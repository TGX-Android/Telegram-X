/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni_utils.h>
#include <log.h>
#include <libyuv.h>
#include <android/bitmap.h>
#include <atomic>
#include <cstdint>
#include <limits>
#include <string>
#include <utility>
#include <vector>
#include <tlottie.h>
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/cpu.h>
#include <libavutil/eval.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
#include <libavcodec/avcodec.h>

}

#include <lz4.h>
#include <malloc.h>
#include <unistd.h>
#include <pthread.h>

#include "bridge.h"

#define MAX_GIF_SIZE 920
// The limit of Telegram for Android
#define MAX_ANIMATION_SIDE 3840
// So that differences of timestamps don't overflow
#define MAX_TIMESTAMP_MS ((double) std::numeric_limits<int32_t>::max())
#define MAX_FRAME_RATE 1000.0
#define MAX_PROBED_PACKETS 64
// Each thread keeps frames of its own
#define MAX_DECODER_THREADS 6
// Valid files give a frame within a few packets of video, packets that an edit list drops count
// only in the second limit
#define MAX_VIDEO_PACKETS_WITHOUT_FRAME 64
#define MAX_PACKETS_WITHOUT_FRAME 1024
#define ANIMATION_CODECS "h264,mpeg4,gif,libvpx-vp9"
// Telegram makes them only as WebM of VP9
#define VIDEO_STICKER_CODECS "libvpx-vp9"
#define VIDEO_STICKER_FORMATS "matroska"
// As many as files of cameras and editors have
#define MAX_ANIMATION_STREAMS 16
// Indexes of ~500K samples of MP4
#define MAX_DECODER_MEMORY ((int64_t) 32 * 1024 * 1024)
// Smaller blocks are never refused: FFmpeg doesn't check them all
#define MIN_REFUSED_BLOCK (64 * 1024)
#define BITMAP_TARGET_FORMAT AV_PIX_FMT_RGBA
// Changed with each update that affects cache contents
// Updates: tlottie, frame-0-fix
#define LOTTIE_CACHE_MAGIC 0xf0ebaef5
#define LOTTIE_CACHE_MAGIC_REDUCED 0xf0ebaef6

// Blocks of the decoder's thread: all outside codec calls, and those of av_realloc in them
struct DecoderMemory {
  std::atomic<int64_t> used{0};
  // The decoder and its blocks not freed yet
  std::atomic<int> references{1};

  bool take (int64_t bytes, int64_t blockSize) {
    int64_t current = used.load();
    do {
      if (blockSize >= MIN_REFUSED_BLOCK && bytes > MAX_DECODER_MEMORY - current) {
        return false;
      }
    } while (!used.compare_exchange_weak(current, current + bytes));
    return true;
  }

  void release () {
    if (references.fetch_sub(1) == 1) {
      delete this;
    }
  }
};

struct BlockHeaderSecrets {
  uintptr_t memory;
  uint64_t check;
};

static const BlockHeaderSecrets &blockHeaderSecrets () {
  static const BlockHeaderSecrets secrets = [] {
    BlockHeaderSecrets made {};
    arc4random_buf(&made, sizeof(made));
    return made;
  }();
  return secrets;
}

static uint64_t mixBits (uint64_t x) {
  x = (x ^ (x >> 33)) * 0xff51afd7ed558ccdULL;
  x = (x ^ (x >> 33)) * 0xc4ceb9fe1a85ec53ULL;
  return x ^ (x >> 33);
}

static thread_local int decodedMaxSide = 0;
static thread_local DecoderMemory *decodedMemory = nullptr;
// Checked as headers are: variables of threads are on the heap before Android 10
static thread_local uint32_t decodedMemoryCheck = 0;
// Codec calls, also those of probing inside FFmpeg, as they are wrapped at link time
static thread_local int codecCalls = 0;

static uint32_t decoderMemoryCheck (DecoderMemory *memory) {
  return (uint32_t) mixBits(~blockHeaderSecrets().check ^ reinterpret_cast<uintptr_t>(memory));
}

static DecoderMemory *checkedDecodedMemory () {
  DecoderMemory *memory = decodedMemory;
  if (memory != nullptr && decodedMemoryCheck != decoderMemoryCheck(memory)) {
    abort();
  }
  return memory;
}

class DecodedBy {
 public:
  DecodedBy (int maxSide, DecoderMemory *memory) :
    previousMaxSide(decodedMaxSide), previousMemory(checkedDecodedMemory()) {
    decodedMaxSide = maxSide;
    decodedMemory = memory;
    decodedMemoryCheck = decoderMemoryCheck(memory);
  }
  ~DecodedBy () {
    decodedMaxSide = previousMaxSide;
    decodedMemory = previousMemory;
    decodedMemoryCheck = decoderMemoryCheck(previousMemory);
  }
  DecodedBy (const DecodedBy &) = delete;
  DecodedBy &operator= (const DecodedBy &) = delete;
 private:
  const int previousMaxSide;
  DecoderMemory *const previousMemory;
};

// The alignment of av_malloc, which the header takes
#if defined(__i386__) || defined(__x86_64__)
#define FFMPEG_ALIGNMENT 64
#else
#define FFMPEG_ALIGNMENT 16
#endif

struct BlockHeader {
  DecoderMemory *memory;
  int64_t size;
};

// The decoder hidden by a secret, and a keyed check of the header and its place, as scudo's
struct StoredBlockHeader {
  uintptr_t memory;
  uint32_t size;
  uint32_t check;
};
static_assert(sizeof(StoredBlockHeader) <= FFMPEG_ALIGNMENT, "the header within the alignment");

static uint32_t blockHeaderCheck (const void *base, uintptr_t memory, uint32_t size) {
  uint64_t x = mixBits(blockHeaderSecrets().check ^ (uint64_t) memory);
  x = mixBits(x ^ (uint64_t) reinterpret_cast<uintptr_t>(base));
  x = mixBits(x ^ size);
  return (uint32_t) (x ^ (x >> 32));
}

static BlockHeader readBlockHeader (void *block) {
  uint8_t *place = static_cast<uint8_t *>(block) - sizeof(StoredBlockHeader);
  StoredBlockHeader stored {};
  memcpy(&stored, place, sizeof(stored));
  if (stored.check != blockHeaderCheck(place, stored.memory, stored.size)) {
    abort();
  }
  return BlockHeader {
    reinterpret_cast<DecoderMemory *>(stored.memory ^ blockHeaderSecrets().memory),
    (int64_t) stored.size
  };
}

static void *writeBlockHeader (void *base, DecoderMemory *memory, int64_t size) {
  StoredBlockHeader stored {};
  stored.memory = reinterpret_cast<uintptr_t>(memory) ^ blockHeaderSecrets().memory;
  stored.size = (uint32_t) size;
  uint8_t *block = static_cast<uint8_t *>(base) + FFMPEG_ALIGNMENT;
  // Right before the block, where any write before it lands first
  uint8_t *place = block - sizeof(StoredBlockHeader);
  stored.check = blockHeaderCheck(place, stored.memory, stored.size);
  memcpy(place, &stored, sizeof(stored));
  return block;
}

extern "C" void *tgx_ff_malloc (size_t size) {
  // All blocks of FFmpeg, built with --malloc-prefix=tgx_ff_
  if (size > std::numeric_limits<uint32_t>::max() - FFMPEG_ALIGNMENT) {
    return nullptr;
  }
  const size_t blockSize = size + FFMPEG_ALIGNMENT;
  DecoderMemory *memory = codecCalls == 0 ? checkedDecodedMemory() : nullptr;
  if (memory != nullptr && !memory->take((int64_t) blockSize, (int64_t) blockSize)) {
    return nullptr;
  }
#if __ANDROID_API__ >= 17
  void *base = nullptr;
  if (posix_memalign(&base, FFMPEG_ALIGNMENT, blockSize) != 0) {
    base = nullptr;
  }
#else
  void *base = memalign(FFMPEG_ALIGNMENT, blockSize);
#endif
  if (base == nullptr) {
    if (memory != nullptr) {
      memory->used -= (int64_t) blockSize;
    }
    return nullptr;
  }
  if (memory != nullptr) {
    memory->references++;
  }
  return writeBlockHeader(base, memory, (int64_t) blockSize);
}

extern "C" void *tgx_ff_realloc (void *block, size_t size) {
  if (size > std::numeric_limits<uint32_t>::max() - FFMPEG_ALIGNMENT) {
    return nullptr;
  }
  const BlockHeader header = block != nullptr ? readBlockHeader(block) : BlockHeader {};
  DecoderMemory *memory = header.memory != nullptr ? header.memory : checkedDecodedMemory();
  const int64_t blockSize = (int64_t) (size + FFMPEG_ALIGNMENT);
  const int64_t more = blockSize - (header.memory != nullptr ? header.size : 0);
  if (memory != nullptr && more > 0 && !memory->take(more, blockSize)) {
    return nullptr;
  }
  uint8_t *previous = block != nullptr ? static_cast<uint8_t *>(block) - FFMPEG_ALIGNMENT : nullptr;
  void *base = realloc(previous, (size_t) blockSize);
  if (base == nullptr) {
    if (memory != nullptr && more > 0) {
      memory->used -= more;
    }
    return nullptr;
  }
  if (memory != nullptr) {
    if (more < 0) {
      memory->used += more;
    }
    if (header.memory == nullptr) {
      memory->references++;
    }
  }
  return writeBlockHeader(base, memory, blockSize);
}

extern "C" void tgx_ff_free (void *block) {
  if (block == nullptr) {
    return;
  }
  const BlockHeader header = readBlockHeader(block);
  free(static_cast<uint8_t *>(block) - FFMPEG_ALIGNMENT);
  if (header.memory != nullptr) {
    header.memory->used -= header.size;
    header.memory->release();
  }
}

extern "C" int __real_vp9_alloc_context_buffers (void *common, int width, int height);

extern "C" int __wrap_vp9_alloc_context_buffers (void *common, int width, int height) {
  // libvpx allocates for each frame, hidden ones too, before FFmpeg checks max_pixels
  if (decodedMaxSide > 0 && std::max(width, height) > decodedMaxSide) {
    return 1;
  }
  return __real_vp9_alloc_context_buffers(common, width, height);
}

extern "C" int __real_avcodec_open2 (AVCodecContext *context, const AVCodec *codec,
                                     AVDictionary **options);
extern "C" int __real_avcodec_send_packet (AVCodecContext *context, const AVPacket *packet);
extern "C" int __real_avcodec_receive_frame (AVCodecContext *context, AVFrame *frame);
extern "C" void __real_avcodec_flush_buffers (AVCodecContext *context);

class InCodecCall {
 public:
  InCodecCall () {
    codecCalls++;
  }
  ~InCodecCall () {
    codecCalls--;
  }
  InCodecCall (const InCodecCall &) = delete;
  InCodecCall &operator= (const InCodecCall &) = delete;
};

extern "C" int __wrap_avcodec_open2 (AVCodecContext *context, const AVCodec *codec,
                                     AVDictionary **options) {
  InCodecCall call;
  return __real_avcodec_open2(context, codec, options);
}

extern "C" int __wrap_avcodec_send_packet (AVCodecContext *context, const AVPacket *packet) {
  InCodecCall call;
  return __real_avcodec_send_packet(context, packet);
}

extern "C" int __wrap_avcodec_receive_frame (AVCodecContext *context, AVFrame *frame) {
  InCodecCall call;
  return __real_avcodec_receive_frame(context, frame);
}

extern "C" void __wrap_avcodec_flush_buffers (AVCodecContext *context) {
  InCodecCall call;
  __real_avcodec_flush_buffers(context);
}

static const std::string av_make_error_str (int errnum) {
  char errbuf[AV_ERROR_MAX_STRING_SIZE];
  av_strerror(errnum, errbuf, AV_ERROR_MAX_STRING_SIZE);
  return std::string(errbuf);
}

#undef av_err2str
#define av_err2str(errnum) av_make_error_str(errnum).c_str()

struct LottieInfo {
  const std::string path;
  TLottieInstance *animation = nullptr;
  // Pixels of a frame for a bitmap of another aspect ratio or stride than the animation
  uint32_t *frameBuffer = nullptr;
  size_t frameBufferSize = 0;
  FILE *cacheFile = nullptr;
  uint8_t *cacheBuffer = nullptr;
  size_t cacheBufferSize = 0;
  uint32_t maxCompressedFrameSize = 0;
  bool hadCacheFileErrors = false;

  volatile bool canceled = false;

  uint32_t nextFrameNo = 0;
  size_t headerSize = 0;

  LottieInfo (std::string path) : path(std::move(path)) {

  }

  uint8_t *getBuffer (size_t size) {
    if (cacheBuffer == nullptr) {
      cacheBuffer = (uint8_t *) malloc(cacheBufferSize = size);
    } else if (cacheBufferSize < size) {
      cacheBuffer = (uint8_t *) realloc(cacheBuffer, cacheBufferSize = size);
    }
    return cacheBuffer;
  }

  ~LottieInfo () {
    if (cacheFile != nullptr) {
      fclose(cacheFile);
    }
    if (cacheBuffer != nullptr) {
      free(cacheBuffer);
    }
    free(frameBuffer);
    tlottie_drop(animation);
  }
};

static bool renderLottieFrame (TLottieInstance *animation, uint32_t frameNo, void *pixels,
                               const AndroidBitmapInfo &bitmapInfo, uint32_t **buffer,
                               size_t *bufferSize) {
  // Draws a frame of the animation into the pixels of a bitmap: scaled to fit it with the aspect
  // ratio of the animation, centered on transparent pixels, as rlottie drew. tlottie draws into
  // pixels of the size it's given without a stride, so other bitmaps take the frame from buffer
  const uint32_t width = bitmapInfo.width;
  const uint32_t height = bitmapInfo.height;
  const uint32_t animationWidth = tlottie_width(animation);
  const uint32_t animationHeight = tlottie_height(animation);
  uint32_t fitWidth = width, fitHeight = height;
  if (animationWidth != 0 && animationHeight != 0 &&
      (uint64_t) width * animationHeight != (uint64_t) height * animationWidth) {
    const float scale = std::min((float) width / (float) animationWidth,
                                 (float) height / (float) animationHeight);
    fitWidth = std::max(1u, (uint32_t) lroundf((float) animationWidth * scale));
    fitHeight = std::max(1u, (uint32_t) lroundf((float) animationHeight * scale));
  }
  if (fitWidth == width && fitHeight == height && bitmapInfo.stride == width * 4) {
    return tlottie_render(animation, (float) frameNo, width, height, (uint32_t *) pixels,
                          (size_t) width * height, 1) == TLOTTIE_OK;
  }
  const size_t size = (size_t) fitWidth * fitHeight;
  if (*bufferSize < size) {
    auto *newBuffer = (uint32_t *) realloc(*buffer, size * sizeof(uint32_t));
    if (newBuffer == nullptr) {
      return false;
    }
    *buffer = newBuffer;
    *bufferSize = size;
  }
  if (tlottie_render(animation, (float) frameNo, fitWidth, fitHeight, *buffer, size, 1) !=
      TLOTTIE_OK) {
    return false;
  }
  const uint32_t left = (width - fitWidth) / 2;
  const uint32_t top = (height - fitHeight) / 2;
  for (uint32_t y = 0; y < height; y++) {
    auto *row = (uint32_t *) ((uint8_t *) pixels + (size_t) y * bitmapInfo.stride);
    memset(row, 0, (size_t) width * sizeof(uint32_t));
    if (y >= top && y < top + fitHeight) {
      memcpy(row + left, *buffer + (size_t) (y - top) * fitWidth,
             (size_t) fitWidth * sizeof(uint32_t));
    }
  }
  return true;
}

struct VideoInfo {

  const std::string path;

  VideoInfo (const std::string &path) : path(path) { }

  ~VideoInfo () {
    if (video_dec_ctx) {
#if LIBAVFORMAT_VERSION_MAJOR < 59
      avcodec_close(video_dec_ctx);
#endif
      avcodec_free_context(&video_dec_ctx);
      video_dec_ctx = nullptr;
    }
    if (fmt_ctx) {
      avformat_close_input(&fmt_ctx);
      fmt_ctx = nullptr;
    }
    if (frame) {
      av_frame_free(&frame);
      frame = nullptr;
    }
    if (scale_ctx) {
      sws_freeContext(scale_ctx);
      scale_ctx = nullptr;
    }
    sws_freeContext(frame_scale_ctx);
    if (packet != nullptr) {
      av_packet_free(&packet);
      packet = nullptr;
    }
    video_stream_idx = -1;
    video_stream = nullptr;
    memory->release();
  }

  DecoderMemory *const memory = new DecoderMemory();
  AVFormatContext *fmt_ctx = nullptr;
  int video_stream_idx = -1;
  AVStream *video_stream = nullptr;
  AVCodecContext *video_dec_ctx = nullptr;
  SwsContext *scale_ctx = nullptr;
  SwsContext *frame_scale_ctx = nullptr;
  int frameScaleWidth = -1, frameScaleHeight = -1;
  AVPixelFormat frameScaleFormat = AV_PIX_FMT_NONE;
  AVFrame *frame = nullptr;
  int srcWidth = -1, srcHeight = -1;
  int maxSide = MAX_ANIMATION_SIDE;
  AVPixelFormat srcFormat = AV_PIX_FMT_NONE;
  int dstWidth = -1, dstHeight = -1;
  int frameRate = -1;
  bool has_decoded_frames = false;
  bool is_broken = false;
  bool had_invalid_frames = false;
  bool eof_reached = false;
  bool draining = false;
  bool has_pending_frame = false;
  bool pending_frame_looped = false;
  size_t loop_count = 0;
  size_t drained_count = 0;

  AVPacket *packet = nullptr;
};

extern "C" int __real_av_image_check_size (unsigned int width, unsigned int height,
                                           int logOffset, void *logContext);

extern "C" int __wrap_av_image_check_size (unsigned int width, unsigned int height,
                                           int logOffset, void *logContext) {
  // Decoders check headers by it before they allocate, but it takes no max_pixels
  if (logContext != nullptr &&
      *static_cast<const AVClass *const *>(logContext) == avcodec_get_class()) {
    return av_image_check_size2(width, height,
                                static_cast<const AVCodecContext *>(logContext)->max_pixels,
                                AV_PIX_FMT_NONE, logOffset, logContext);
  }
  return __real_av_image_check_size(width, height, logOffset, logContext);
}

extern "C" int __real_av_image_check_size2 (unsigned int width, unsigned int height,
                                            int64_t maxPixels, AVPixelFormat format,
                                            int logOffset, void *logContext);

extern "C" int __wrap_av_image_check_size2 (unsigned int width, unsigned int height,
                                            int64_t maxPixels, AVPixelFormat format,
                                            int logOffset, void *logContext) {
  // Frames are allocated at the coded size, also in probing, and a pixel of H.264 takes up to 6
  // bytes, so bytes are bounded, at 2 a pixel as of 4:2:2 of 8 bits
  if (logContext != nullptr &&
      *static_cast<const AVClass *const *>(logContext) == avcodec_get_class()) {
    const auto *context = static_cast<const AVCodecContext *>(logContext);
    if ((context->codec_id == AV_CODEC_ID_H264 || context->codec_id == AV_CODEC_ID_MPEG4) &&
        av_image_get_buffer_size(context->pix_fmt, std::max((int) width, context->coded_width),
                                 std::max((int) height, context->coded_height), 1) >
        maxPixels * 2) {
      return AVERROR(EINVAL);
    }
  }
  return __real_av_image_check_size2(width, height, maxPixels, format, logOffset, logContext);
}

int open_codec_context (int *stream_idx, AVCodecContext **dec_ctx, AVFormatContext *fmt_ctx,
                        enum AVMediaType type, int maxSide, int64_t maxPixels,
                        const char *codecs) {
  int ret;
  AVStream *st;
  AVDictionary *opts = nullptr;

  ret = av_find_best_stream(fmt_ctx, type, -1, -1, nullptr, 0);
  if (ret < 0) {
    loge(TAG_GIF_LOADER, "Can't find %s stream in input file", av_get_media_type_string(type));
    return ret;
  } else {
    *stream_idx = ret;
    st = fmt_ctx->streams[*stream_idx];

    const int width = st->codecpar->width;
    const int height = st->codecpar->height;
    if (width <= 0 || height <= 0 || std::max(width, height) > maxSide) {
      loge(TAG_GIF_LOADER, "can't play %dx%d", width, height);
      return -1;
    }

    const AVCodec *dec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!dec) {
      loge(TAG_GIF_LOADER, "failed to find %s decoder for %s", av_get_media_type_string(type), avcodec_get_name(st->codecpar->codec_id));
      return -1;
    }

    *dec_ctx = avcodec_alloc_context3(dec);
    if (!*dec_ctx) {
      loge(TAG_GIF_LOADER, "Failed to allocate the %s codec context for %s", av_get_media_type_string(type), avcodec_get_name(st->codecpar->codec_id));
      return AVERROR(ENOMEM);
    }

    if ((ret = avcodec_parameters_to_context(*dec_ctx, st->codecpar)) < 0) {
      loge(TAG_GIF_LOADER, "Failed to copy %s codec parameters to decoder context for %s", av_get_media_type_string(type), avcodec_get_name(st->codecpar->codec_id));
      return ret;
    }

    auto context = *dec_ctx;
    context->max_pixels = maxPixels;
    context->thread_count = std::max(1, std::min(av_cpu_count(), MAX_DECODER_THREADS));
    if (dec->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
      context->thread_type = FF_THREAD_FRAME;
    } else if (dec->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
      context->thread_type = FF_THREAD_SLICE;
    } else {
      context->thread_count = 1;
    }

    av_dict_set(&opts, "refcounted_frames", "1", 0);
    av_dict_set(&opts, "codec_whitelist", codecs, 0);
    av_dict_set(&opts, "export_side_data", "film_grain", 0);
    ret = avcodec_open2(*dec_ctx, dec, &opts);
    av_dict_free(&opts);
    if (ret < 0) {
      loge(TAG_GIF_LOADER, "failed to open %s decoder for %s", av_get_media_type_string(type), avcodec_get_name(st->codecpar->codec_id));
      return -1;
    }
  }

  return 0;
}

static SwsContext *getScaleContext (int srcWidth, int srcHeight, AVPixelFormat srcFormat,
                                    int dstWidth, int dstHeight) {
  const int flags =
    srcWidth == dstWidth && srcHeight == dstHeight ? SWS_FAST_BILINEAR : SWS_BILINEAR;
  return sws_getContext(srcWidth, srcHeight, srcFormat, dstWidth, dstHeight,
                        BITMAP_TARGET_FORMAT, flags, nullptr, nullptr, nullptr);
}

JNI_FUNC(jlong, createDecoder, jstring src, jlongArray data, jdouble startMediaTimestamp,
         jint maxSourceSize) {
  const int maxSide = maxSourceSize > 0 ? std::min((int) maxSourceSize, MAX_ANIMATION_SIDE) :
    MAX_ANIMATION_SIDE;
  // Decoders align frames to blocks of up to 64 pixels
  const int64_t maxPixels = (int64_t) FFALIGN(maxSide, 64) * FFALIGN(maxSide, 64);
  const char *codecs = maxSourceSize > 0 ? VIDEO_STICKER_CODECS : ANIMATION_CODECS;

  VideoInfo *info = new VideoInfo(jni::from_jstring(env, src));
  info->maxSide = maxSide;
  DecodedBy decoded(maxSide, info->memory);

  int ret;
  AVDictionary *options = nullptr;
  av_dict_set(&options, "codec_whitelist", codecs, 0);
  av_dict_set_int(&options, "max_streams", MAX_ANIMATION_STREAMS, 0);
  av_dict_set_int(&options, "max_probe_packets", MAX_PROBED_PACKETS, 0);
  // A few bytes of an index give millions of packets
  av_dict_set(&options, "fflags", "+nobuffer", 0);
  if (maxSourceSize > 0) {
    av_dict_set(&options, "format_whitelist", VIDEO_STICKER_FORMATS, 0);
  }
  ret = avformat_open_input(&info->fmt_ctx, info->path.c_str(), nullptr, &options);
  av_dict_free(&options);
  if (ret < 0) {
    loge(TAG_GIF_LOADER, "can't open source file %s, %s", info->path.c_str(), av_err2str(ret));
    delete info;
    return 0;
  }

  // Probing decodes first frames
  std::vector<AVDictionary *> streamOptions(info->fmt_ctx->nb_streams, nullptr);
  for (AVDictionary *&streamOption : streamOptions) {
    av_dict_set_int(&streamOption, "max_pixels", maxPixels, 0);
    // Otherwise H.264 makes a second frame of each picture with the grain
    av_dict_set(&streamOption, "export_side_data", "film_grain", 0);
  }
  ret = avformat_find_stream_info(info->fmt_ctx, streamOptions.data());
  for (AVDictionary *&streamOption : streamOptions) {
    av_dict_free(&streamOption);
  }
  if (ret < 0) {
    loge(TAG_GIF_LOADER, "can't find stream information %s, %s", info->path.c_str(),
         av_err2str(ret));
    delete info;
    return 0;
  }

  if (open_codec_context(&info->video_stream_idx, &info->video_dec_ctx, info->fmt_ctx,
                         AVMEDIA_TYPE_VIDEO, maxSide, maxPixels, codecs) >= 0) {
    info->video_stream = info->fmt_ctx->streams[info->video_stream_idx];
  }

  if (info->video_stream == nullptr) {
    loge(TAG_GIF_LOADER, "can't find video stream in the input, aborting %s", info->path.c_str());
    delete info;
    return 0;
  }

  info->frame = av_frame_alloc();
  if (info->frame == nullptr) {
    loge(TAG_GIF_LOADER, "can't allocate frame %s", info->path.c_str());
    delete info;
    return 0;
  }

  info->packet = av_packet_alloc();
  if (info->packet == nullptr) {
    loge(TAG_GIF_LOADER, "can't allocate packet %s", info->path.c_str());
    delete info;
    return 0;
  }

  const int srcWidth = info->srcWidth = info->video_dec_ctx->width;
  const int srcHeight = info->srcHeight = info->video_dec_ctx->height;
  const AVPixelFormat srcFormat = info->srcFormat = info->video_dec_ctx->pix_fmt;
  if (srcWidth <= 0 || srcHeight <= 0 || std::max(srcWidth, srcHeight) > maxSide ||
      srcFormat == AV_PIX_FMT_NONE) {
    loge(TAG_GIF_LOADER, "can't play %dx%d, format: %d, %s", srcWidth, srcHeight, srcFormat,
         info->path.c_str());
    delete info;
    return 0;
  }

  int dstWidth = srcWidth;
  int dstHeight = srcHeight;
  if (std::max(srcWidth, srcHeight) > MAX_GIF_SIZE) {
    const float scale = std::min((float) MAX_GIF_SIZE / (float) srcWidth,
                                 (float) MAX_GIF_SIZE / (float) srcHeight);
    dstWidth = (int) ((float) srcWidth * scale);
    dstHeight = (int) ((float) srcHeight * scale);
    dstWidth = std::max(1, dstWidth - dstWidth % 2);
    dstHeight = std::max(1, dstHeight - dstHeight % 2);
  }

  // Frames with alpha are scaled by scaleChangedFrame
  if (srcFormat != AV_PIX_FMT_YUVA420P &&
      (srcFormat != BITMAP_TARGET_FORMAT || dstWidth != srcWidth || dstHeight != srcHeight)) {
    info->scale_ctx = getScaleContext(srcWidth, srcHeight, srcFormat, dstWidth, dstHeight);
    if (info->scale_ctx == nullptr) {
      loge(TAG_GIF_LOADER, "can't scale %dx%d to %dx%d, format: %d, %s", srcWidth, srcHeight,
           dstWidth, dstHeight, srcFormat, info->path.c_str());
      delete info;
      return 0;
    }
  }

  const double framesPerSecond = av_q2d(info->video_stream->avg_frame_rate);
  const long frameRate = std::isfinite(framesPerSecond) && framesPerSecond > 0 ?
    (long) (1000.0 * std::min(framesPerSecond, MAX_FRAME_RATE)) : 0;

  info->dstWidth = dstWidth;
  info->dstHeight = dstHeight;
  info->frameRate = frameRate;

  const int64_t ts = (int64_t) (startMediaTimestamp * (double) AV_TIME_BASE);
  ret = avformat_seek_file(
    info->fmt_ctx, -1,
    std::numeric_limits<int64_t>::min(), ts,
    std::numeric_limits<int64_t>::max(), 0);
  if (ret < 0) {
    loge(TAG_GIF_LOADER, "can't seek to startMediaTimestamp %s, %s", info->path.c_str(),
         av_err2str(ret));
  }

  jlong *dataArr = env->GetLongArrayElements(data, 0);
  if (dataArr != nullptr) {
    dataArr[0] = dstWidth;
    dataArr[1] = dstHeight;
    dataArr[2] = frameRate;
    /*AVDictionaryEntry *rotate_tag = av_dict_get(info->video_stream->metadata, "rotate", nullptr, 0);
    if (rotate_tag && *rotate_tag->value && strcmp(rotate_tag->value, "0") != 0) {
      char *tail;
      dataArr[2] = (int) av_strtod(rotate_tag->value, &tail);
      if (*tail) {
        dataArr[2] = 0;
      }
    } else {
      dataArr[2] = 0;
    }*/
    env->ReleaseLongArrayElements(data, dataArr, 0);
  }

  //LOGD("successfully opened file %s", info->src);

  return jni::ptr_to_jlong(info);
}

JNI_FUNC(void, destroyDecoder, jlong ptr) {
  if (ptr != 0) {
    VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);
    delete info;
  }
}

JNI_FUNC(jboolean, destroyLottieDecoder, jlong ptr) {
  if (ptr != 0) {
    LottieInfo *info = jni::jlong_to_ptr<LottieInfo *>(ptr);
    bool needDeleteCacheFile = info->hadCacheFileErrors;
    delete info;
    return needDeleteCacheFile ? JNI_TRUE : JNI_FALSE;
  }
  return JNI_FALSE;
}

static void drop_pending_frame (VideoInfo *info) {
  if (info->has_pending_frame) {
    info->has_pending_frame = false;
    av_frame_unref(info->frame);
  }
}

JNI_FUNC(jboolean, seekVideoToStart, jlong ptr) {
  if (ptr == 0) {
    return JNI_FALSE;
  }

  VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);
  drop_pending_frame(info);
  DecodedBy decoded(info->maxSide, info->memory);

  int ret = 0;
  ret = avformat_seek_file(
    info->fmt_ctx, -1,
    std::numeric_limits<int64_t>::min(), 0,
    std::numeric_limits<int64_t>::max(), 0);
  if (ret < 0) {
    loge(TAG_GIF_LOADER, "can't forcely seek to beginning of file %s, %s", info->path.c_str(),
         av_err2str(ret));
    return JNI_FALSE;
  }
  avcodec_flush_buffers(info->video_dec_ctx);
  return JNI_TRUE;
}

JNI_FUNC(jboolean, isVideoBroken, jlong ptr) {
  if (ptr == 0) {
    return JNI_FALSE;
  }
  VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);
  if (info->is_broken) {
    return JNI_TRUE;
  } else {
    return JNI_FALSE;
  }
}

static bool scaleFrame (SwsContext *context, const AVFrame *frame, void *pixels,
                        const AndroidBitmapInfo &bitmapInfo) {
  uint8_t *const dst[] = {(uint8_t *) pixels};
  const int dstStride[] = {(int) bitmapInfo.stride};
  // Errors are negative: the NEON converters of swscale for arm32 give 0 for a whole frame
  return sws_scale(context, frame->data, frame->linesize, 0, frame->height, dst, dstStride) >= 0;
}

static bool scaleChangedFrame (VideoInfo *info, const AVFrame *frame, void *pixels,
                               const AndroidBitmapInfo &bitmapInfo) {
  const auto format = (AVPixelFormat) frame->format;
  // sws_getCachedContext would make it again for each frame of yuvj formats, which swscale keeps
  // as their yuv ones
  if (frame->width != info->frameScaleWidth || frame->height != info->frameScaleHeight ||
      format != info->frameScaleFormat) {
    sws_freeContext(info->frame_scale_ctx);
    info->frame_scale_ctx = getScaleContext(frame->width, frame->height, format, info->dstWidth,
                                            info->dstHeight);
    info->frameScaleWidth = frame->width;
    info->frameScaleHeight = frame->height;
    info->frameScaleFormat = format;
  }
  if (info->frame_scale_ctx == nullptr ||
      !scaleFrame(info->frame_scale_ctx, frame, pixels, bitmapInfo)) {
    return false;
  }
  const AVPixFmtDescriptor *descriptor = av_pix_fmt_desc_get(format);
  if (descriptor != nullptr && (descriptor->flags & AV_PIX_FMT_FLAG_ALPHA) != 0) {
    // Bitmaps keep alpha premultiplied, as I420AlphaToARGBMatrix gives it, and swscale doesn't
    libyuv::ARGBAttenuate((uint8_t *) pixels, (int) bitmapInfo.stride, (uint8_t *) pixels,
                          (int) bitmapInfo.stride, info->dstWidth, info->dstHeight);
  }
  return true;
}

static bool copyFrame (const AVFrame *frame, void *pixels, const AndroidBitmapInfo &bitmapInfo) {
  const int width = (int) bitmapInfo.width;
  const int height = (int) bitmapInfo.height;
  const int stride = (int) bitmapInfo.stride;
  if (frame->width != width || frame->height != height) {
    return false;
  }
  auto *dst = (uint8_t *) pixels;
  switch ((AVPixelFormat) frame->format) {
    case BITMAP_TARGET_FORMAT:
      av_image_copy_plane(dst, stride, frame->data[0], frame->linesize[0], width * 4, height);
      return true;
    case AV_PIX_FMT_YUV420P:
    case AV_PIX_FMT_YUVJ420P:
      if (frame->colorspace == AVColorSpace::AVCOL_SPC_BT709) {
        return libyuv::H420ToARGB(frame->data[0], frame->linesize[0], frame->data[2],
                                  frame->linesize[2], frame->data[1], frame->linesize[1],
                                  dst, stride, width, height) == 0;
      }
      return libyuv::I420ToARGB(frame->data[0], frame->linesize[0], frame->data[2],
                                frame->linesize[2], frame->data[1], frame->linesize[1],
                                dst, stride, width, height) == 0;
    case AV_PIX_FMT_YUVA420P:
      return libyuv::I420AlphaToARGBMatrix(frame->data[0], frame->linesize[0], frame->data[2],
                                           frame->linesize[2], frame->data[1], frame->linesize[1],
                                           frame->data[3], frame->linesize[3], dst, stride,
                                           &libyuv::kYvuI601Constants, width, height, 50) == 0;
    case AV_PIX_FMT_BGRA:
      return libyuv::ABGRToARGB(frame->data[0], frame->linesize[0], dst, stride, width,
                                height) == 0;
    default:
      return false;
  }
}

static void update_timestamps (JNIEnv *env, VideoInfo *info, jlongArray data) {
  jlong *dataArr = env->GetLongArrayElements(data, 0);
  if (dataArr != nullptr) {
    const double ms =
      1000.0 * (double) info->frame->pts * av_q2d(info->video_stream->time_base);
    const jlong timestamp = std::isfinite(ms) ?
      (jlong) std::clamp(ms, -MAX_TIMESTAMP_MS, MAX_TIMESTAMP_MS) : dataArr[3];
    if (timestamp > dataArr[3]) {
        dataArr[4] = timestamp - dataArr[3];
    }
    dataArr[3] = timestamp;
    env->ReleaseLongArrayElements(data, dataArr, 0);
  }
}

static bool to_android_bitmap (JNIEnv *env, VideoInfo *info, jobject bitmap) {
  AndroidBitmapInfo bitmapInfo;
  bool drawn = false;

  if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) == ANDROID_BITMAP_RESULT_SUCCESS &&
      bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888 &&
      bitmapInfo.width == info->dstWidth && bitmapInfo.height == info->dstHeight) {
    AVFrame *frame = info->frame;

    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
      if (info->scale_ctx != nullptr && frame->width == info->srcWidth &&
          frame->height == info->srcHeight && frame->format == info->srcFormat) {
        // TODO: find out why sws_scale doesn't support transparency (AV_PIX_FMT_YUVA420P) properly
        // note: for now, updated libyuv + kYvuI601Constants in I420AlphaToARGBMatrix fixes AV_PIX_FMT_YUVA420P issue - but still needs to be researched
        drawn = scaleFrame(info->scale_ctx, frame, pixels, bitmapInfo);
      } else if (info->scale_ctx == nullptr && copyFrame(frame, pixels, bitmapInfo)) {
        drawn = true;
      } else {
        drawn = scaleChangedFrame(info, frame, pixels, bitmapInfo);
      }
      AndroidBitmap_unlockPixels(env, bitmap);
    }
  }
  return drawn;
}

static jint draw_frame (JNIEnv *env, VideoInfo *info, jobject bitmap, bool hasLooped) {
  const bool drawn = to_android_bitmap(env, info, bitmap);
  if (!drawn) {
    loge(TAG_GIF_LOADER, "can't draw frame %dx%d, format: %d, %s", info->frame->width,
         info->frame->height, info->frame->format, info->path.c_str());
    info->is_broken = true;
  }
  av_frame_unref(info->frame);
  return drawn ? (hasLooped ? 2 : 1) : 0;
}

// Without a bitmap, the frame is only decoded, and the next call draws it or skips it
JNI_FUNC(jint, getVideoFrame, jlong ptr, jobject bitmap, jlongArray data) {
  if (ptr == 0) {
    return 0;
  }
  VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);
  if (info->is_broken) {
    return 0;
  }
  if (info->has_pending_frame && bitmap != nullptr) {
    info->has_pending_frame = false;
    return draw_frame(env, info, bitmap, info->pending_frame_looped);
  }
  drop_pending_frame(info);
  DecodedBy decoded(info->maxSide, info->memory);

  bool gotFrame = false;
  bool hasLooped = false;
  bool fatalError = false;
  size_t errorCount = 0;
  size_t maxErrorCount = round(std::min(120.0, (double) info->frameRate / 1000.0) * 2.5);
  size_t packetCount = 0, videoPacketCount = 0;

  do {
    int ret;

    if (info->draining) {
      ret = avcodec_receive_frame(info->video_dec_ctx, info->frame);
      if (ret == 0) {
        if (info->loop_count == 0) { // First loop
          info->drained_count++;
        }
        gotFrame = true;
        break;
      }
      info->draining = false;
      if (!info->has_decoded_frames) {
        loge(TAG_GIF_LOADER, "avcodec_receive_frame fatal error: no valid frames for %s: %s", info->path.c_str(), av_err2str(ret));
        fatalError = true;
        break;
      }
    }

    if (info->eof_reached) {
      if (hasLooped) {
        loge(TAG_GIF_LOADER, "no frames in a whole loop of %s", info->path.c_str());
        fatalError = true;
        break;
      }
      ret = avformat_seek_file(
        info->fmt_ctx, -1,
        std::numeric_limits<int64_t>::min(), 0,
        std::numeric_limits<int64_t>::max(), 0
      );
      if (ret != 0) {
        loge(TAG_GIF_LOADER, "can't seek %s to start, %s, drained_count: %zu", info->path.c_str(),
             av_err2str(ret),
             info->drained_count);
        fatalError = true;
        break;
      }
      if (info->loop_count == SIZE_T_MAX) {
        info->loop_count = 1;
      } else {
        info->loop_count++;
      }
      info->eof_reached = false;
      hasLooped = true;
      avcodec_flush_buffers(info->video_dec_ctx);
    }
    ret = av_read_frame(info->fmt_ctx, info->packet);
    if (ret != 0) {
      if (ret == AVERROR_EOF) {
        info->eof_reached = true;
        ret = avcodec_send_packet(info->video_dec_ctx, NULL);
        if (ret == 0) {
          info->draining = true;
        } else if (info->loop_count == 0) {
          logv(TAG_GIF_LOADER, "avcodec_send_packet can't enter drain mode for %s: %s", info->path.c_str(),
               av_err2str(ret));
        }
        continue;
      }
      loge(TAG_GIF_LOADER, "av_read_frame fatal error for %s: %s", info->path.c_str(), av_err2str(ret));
      fatalError = true;
      break;
    }
    const bool isVideo = info->packet->stream_index == info->video_stream_idx;
    const bool isDropped = (info->packet->flags & AV_PKT_FLAG_DISCARD) != 0;
    if (++packetCount > MAX_PACKETS_WITHOUT_FRAME ||
        (isVideo && !isDropped && ++videoPacketCount > MAX_VIDEO_PACKETS_WITHOUT_FRAME)) {
      loge(TAG_GIF_LOADER, "no frame in %zu packets of %s", packetCount, info->path.c_str());
      av_packet_unref(info->packet);
      fatalError = true;
      break;
    }
    if (!isVideo) {
      av_packet_unref(info->packet);
      continue;
    }
    ret = avcodec_send_packet(info->video_dec_ctx, info->packet);
    if (ret != 0) {
      if (ret == AVERROR_INVALIDDATA && (info->has_decoded_frames || info->loop_count == 0) && errorCount < maxErrorCount) {
        errorCount++;
        if (errorCount == 1 && !info->had_invalid_frames) {
          logv(TAG_GIF_LOADER, "avcodec_send_packet error #%zu for %s: %s maxErrorCount: %zu size: %d pts: %lld flags: %d",
               errorCount,
               info->path.c_str(),
               av_err2str(ret),
               maxErrorCount,
               info->packet->size,
               info->packet->pts,
               info->packet->flags);
        }
      } else if (ret != AVERROR(EAGAIN)) {
        loge(TAG_GIF_LOADER, "avcodec_send_packet fatal error for %s: %s", info->path.c_str(),
             av_err2str(ret));
        fatalError = true;
      }
    } else {
      ret = avcodec_receive_frame(info->video_dec_ctx, info->frame);
      if (ret != 0) {
        if (ret != AVERROR(EAGAIN)) {
          loge(TAG_GIF_LOADER, "avcodec_receive_frame fatal error for %s: %s",
               info->path.c_str(),
               av_err2str(ret));
          fatalError = true;
        }
      } else {
        if (errorCount > 0 && !info->had_invalid_frames) {
          info->had_invalid_frames = true;
          logv(TAG_GIF_LOADER, "avcodec_send_packet got #%zu errors for file %s maxErrorCount: %zu size: %d pts: %lld flags: %d",
               errorCount,
               info->path.c_str(),
               maxErrorCount,
               info->packet->size,
               info->packet->pts,
               info->packet->flags);
        }
        // Done.
        gotFrame = true;
      }
    }
    av_packet_unref(info->packet);
  } while (!info->is_broken && !fatalError && !gotFrame);

  if (fatalError) {
    info->is_broken = true;
  }

  if (gotFrame && std::max(info->frame->width, info->frame->height) > info->maxSide) {
    loge(TAG_GIF_LOADER, "frame of %dx%d above %d, %s", info->frame->width, info->frame->height,
         info->maxSide, info->path.c_str());
    av_frame_unref(info->frame);
    gotFrame = false;
    info->is_broken = true;
  }

  if (gotFrame) {
    update_timestamps(env, info, data);
    info->has_decoded_frames = true;
    if (bitmap == nullptr) {
      info->has_pending_frame = true;
      info->pending_frame_looped = hasLooped;
      return hasLooped ? 2 : 1;
    }
    return draw_frame(env, info, bitmap, hasLooped);
  }

  return 0;
}

JNI_FUNC(void, cancelLottieDecoder, jlong ptr) {
  LottieInfo *info = jni::jlong_to_ptr<LottieInfo *>(ptr);
  if (info != nullptr) {
    info->canceled = true;
  }
}

JNI_FUNC(jboolean, decodeLottieFirstFrame, jstring jPath, jstring jsonData, jobject bitmap) {
  std::string json = jni::from_jstring(env, jsonData);
  std::string path = jni::from_jstring(env, jPath);
  TLottieInstance *animation = tlottie_new_with_options(
    (const uint8_t *) json.data(), json.size(), TLOTTIE_FITZ_NONE, nullptr, 0, nullptr, 0,
    TLOTTIE_CHANNEL_RGBA
  );
  if (animation == nullptr || tlottie_frame_count(animation) == 0) {
    tlottie_drop(animation);
    return JNI_FALSE;
  }
  AndroidBitmapInfo bitmapInfo;
  AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
  void *pixels;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    tlottie_drop(animation);
    return JNI_FALSE;
  }
  uint32_t *buffer = nullptr;
  size_t bufferSize = 0;
  const bool rendered = renderLottieFrame(animation, 0, pixels, bitmapInfo, &buffer, &bufferSize);
  free(buffer);
  tlottie_drop(animation);
  AndroidBitmap_unlockPixels(env, bitmap);
  return rendered ? JNI_TRUE : JNI_FALSE;
}

JNI_FUNC(jlong, createLottieDecoder, jstring jPath, jstring jsonData, jdoubleArray data, jint fitzpatrickType) {
  std::string json = jni::from_jstring(env, jsonData);
  std::string path = jni::from_jstring(env, jPath);

  /*int color = 0;
  std::map<int32_t, int32_t> *colorReplacement = nullptr;
  if (jColorReplacement != nullptr) {
    jsize colorReplacementLength = env->GetArrayLength(jColorReplacement);
    if (colorReplacementLength > 0) {
      jint *elements = jni::array_get<jint>(env, jColorReplacement);
      colorReplacement = new std::map<int32_t, int32_t>();
      for (int32_t a = 0; a < colorReplacementLength / 2; a++) {
        (*colorReplacement)[elements[a * 2]] = elements[a * 2 + 1];
        if (color == 0) {
          color = elements[a * 2 + 1];
        }
      }
      if (elements != nullptr) {
        jni::array_release<jint,jintArray>(env, jColorReplacement, elements);
      }
    }
  }*/

  uint32_t modifier = TLOTTIE_FITZ_NONE;
  switch (fitzpatrickType) {
    case 1:
    case 2:
    case 12:
      modifier = TLOTTIE_FITZ_TYPE_12;
      break;
    case 3:
      modifier = TLOTTIE_FITZ_TYPE_3;
      break;
    case 4:
      modifier = TLOTTIE_FITZ_TYPE_4;
      break;
    case 5:
      modifier = TLOTTIE_FITZ_TYPE_5;
      break;
    case 6:
      modifier = TLOTTIE_FITZ_TYPE_6;
      break;
  }

  LottieInfo *info = new LottieInfo(path);
  info->animation = tlottie_new_with_options(
    (const uint8_t *) json.data(), json.size(), modifier, nullptr, 0, nullptr, 0,
    TLOTTIE_CHANNEL_RGBA
  );

  if (info->animation == nullptr) {
    delete info;
    return 0;
  }

  size_t totalFrame = tlottie_frame_count(info->animation);
  double frameRate = tlottie_frame_rate(info->animation);
  // From the first frame to the last one, as rlottie gave it
  double duration = frameRate > 0 && totalFrame > 0 ? (double) (totalFrame - 1) / frameRate : 0;

  if (totalFrame == 0) {
    delete info;
    return 0;
  }

  if (data != nullptr) {
    jdouble *dataArr = env->GetDoubleArrayElements(data, 0);

    dataArr[0] = totalFrame;
    dataArr[1] = frameRate;
    dataArr[2] = duration;

    env->ReleaseDoubleArrayElements(data, dataArr, 0);
  }

  return jni::ptr_to_jlong(info);
}

JNI_FUNC(void, getLottieSize, jlong ptr, jintArray data) {
  LottieInfo *info = jni::jlong_to_ptr<LottieInfo *>(ptr);
  jint *dataArr = env->GetIntArrayElements(data, 0);

  size_t width = tlottie_width(info->animation);
  size_t height = tlottie_height(info->animation);
  dataArr[0] = (jint) width;
  dataArr[1] = (jint) height;
  env->ReleaseIntArrayElements(data, dataArr, 0);
}

#define MAX_COMPRESSED_BUFFER_SIZE (1024 * 1024 * 15 /*15 MiB*/)
// Holds stickers uncompressed: 180 frames of 384x384
#define MAX_LOTTIE_CACHE_SIZE (1024 * 1024 * 128)
// 10 seconds at 60 frames a second; stickers and effects last 3
#define MAX_LOTTIE_CACHE_FRAMES 600

JNI_FUNC(jint, createLottieCache, jlong ptr, jstring jCachePath, jobject firstFrame, jobject bitmap, jboolean allowCreate, jboolean limitFps) {
  if (jCachePath == nullptr) {
    return 2;
  }

  std::string cachePath = jni::from_jstring(env, jCachePath);
  LottieInfo *info = jni::jlong_to_ptr<LottieInfo *>(ptr);

  if (info == nullptr || info->animation == nullptr) {
    return 2;
  }

  AndroidBitmapInfo bitmapInfo;
  AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

  size_t uncompressedSize = bitmapInfo.height * bitmapInfo.stride;

  double frameRate = tlottie_frame_rate(info->animation);
  bool skipOdd = frameRate == 60.0 && limitFps == JNI_TRUE;
  uint32_t frameCount = tlottie_frame_count(info->animation);
  uint32_t maxCompressedFrameSize = 0;
  uint32_t firstFrameSize = 0;

  if (frameCount > MAX_LOTTIE_CACHE_FRAMES ||
      (uint64_t) frameCount * (uint64_t) LZ4_compressBound((int) uncompressedSize) >
      MAX_LOTTIE_CACHE_SIZE) {
    return 2;
  }

  const uint32_t magic = skipOdd ? LOTTIE_CACHE_MAGIC_REDUCED : LOTTIE_CACHE_MAGIC;
  info->headerSize = sizeof(magic) + sizeof(frameCount) + sizeof(maxCompressedFrameSize); // index table?

  // logi(TAG_GIF_LOADER, "checking lottie cache: %s", cachePath.c_str());

  bool cacheExists = false;
  FILE *cacheFile = fopen(cachePath.c_str(), "r+");
  if (cacheFile != nullptr) {
    // logi(TAG_GIF_LOADER, "lottie cache found");
    uint32_t magicCheck = 0;
    if (fread(&magicCheck, sizeof(magicCheck), 1, cacheFile) == 1 && magicCheck == magic) {
      // logi(TAG_GIF_LOADER, "magic ok");
      uint32_t frameCountCheck = 0;
      if (fread(&frameCountCheck, sizeof(frameCountCheck), 1, cacheFile) == 1 && frameCountCheck == frameCount) {
        // logi(TAG_GIF_LOADER, "frameCount ok: %d", frameCountCheck);
        if (fread(&maxCompressedFrameSize, sizeof(maxCompressedFrameSize), 1, cacheFile) == 1 && maxCompressedFrameSize > 0 && maxCompressedFrameSize <= MAX_COMPRESSED_BUFFER_SIZE) {
          // logi(TAG_GIF_LOADER, "maxCompressedFrameSize ok: %d", maxCompressedFrameSize);
          uint32_t readFrameCount = 0;
          do {
            if (info->canceled) {
              fclose(cacheFile);
              return 3;
            }
            uint32_t frameSize = 0;
            if (fread(&frameSize, sizeof(frameSize), 1, cacheFile) != 1 || (frameSize != 0 && fseek(cacheFile, frameSize, SEEK_CUR) != 0))
              break;
            if (readFrameCount == 0)
              firstFrameSize = frameSize;
            readFrameCount++;
            if (readFrameCount > frameCount)
              break;
          } while (true);
          // logi(TAG_GIF_LOADER, "frameCount check: %d vs %d", readFrameCount, frameCountCheck);
          if (readFrameCount == frameCount) {
            cacheExists = true;
          }
        }
      }
    }
  }

  if (info->canceled) {
    if (cacheFile != nullptr)
      fclose(cacheFile);
    return 3;
  }

  if (!cacheExists && allowCreate == JNI_TRUE) {
    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
      if (cacheFile != nullptr)
        fclose(cacheFile);
      return 2;
    }

    if (cacheFile != nullptr) {
      fclose(cacheFile);
    }
    cacheFile = fopen(cachePath.c_str(), "w+");
    if (cacheFile == nullptr) {
      return 2;
    }

    // logi(TAG_GIF_LOADER, "creating lottie cache: %s", cachePath.c_str());

    const uint32_t compressBound = (uint32_t) LZ4_compressBound((int) uncompressedSize);
    uint8_t *compressBuffer = (uint8_t *) malloc(compressBound);

    fwrite(&magic, sizeof(magic), 1, cacheFile);
    fwrite(&frameCount, sizeof(frameCount), 1, cacheFile);
    fwrite(&maxCompressedFrameSize, sizeof(maxCompressedFrameSize), 1, cacheFile);

    bool aborted = false;

    for (uint32_t frameNo = 0; frameNo < frameCount; frameNo++) {
      bool skipFrame = skipOdd && frameNo % 2 == 1;
      uint32_t compressedSize = 0;
      if (frameNo == 0) {
        void *firstFramePixels;
        if (firstFrame != nullptr && AndroidBitmap_lockPixels(env, firstFrame, &firstFramePixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
          compressedSize = (uint32_t) LZ4_compress_default((const char *) firstFramePixels, (char *) compressBuffer, (int) uncompressedSize, (int) compressBound);
          AndroidBitmap_unlockPixels(env, firstFrame);
        }
      }
      if (compressedSize == 0 && !skipFrame) {
        renderLottieFrame(info->animation, frameNo, pixels, bitmapInfo, &info->frameBuffer,
                          &info->frameBufferSize);
        //libyuv::ABGRToARGB((uint8_t *) pixels, bitmapInfo.stride, (uint8_t *) pixels, bitmapInfo.stride, bitmapInfo.width, bitmapInfo.height);
        compressedSize = (uint32_t) LZ4_compress_default((const char *) pixels, (char *) compressBuffer, (int) uncompressedSize, (int) compressBound);
      }

      if (frameNo == 0)
        firstFrameSize = compressedSize;

      fwrite(&compressedSize, sizeof(compressedSize), 1, cacheFile);
      if (compressedSize > 0) {
        fwrite(compressBuffer, sizeof(uint8_t), compressedSize, cacheFile);
      }

      // logi(TAG_GIF_LOADER, "wrote %d bytes frame no %d", compressedSize, frameNo);
      maxCompressedFrameSize = maxCompressedFrameSize > compressedSize ? maxCompressedFrameSize : compressedSize;

      if (info->canceled) {
        aborted = true;
        break;
      }
    }

    if (aborted || info->canceled) {
      fclose(cacheFile);
      unlink(cachePath.c_str());
      AndroidBitmap_unlockPixels(env, bitmap);
      return 3;
    }

    info->maxCompressedFrameSize = maxCompressedFrameSize;

    fseek(cacheFile, sizeof(magic) + sizeof(frameCount), SEEK_SET);
    fwrite(&maxCompressedFrameSize, sizeof(maxCompressedFrameSize), 1, cacheFile);
    // logi(TAG_GIF_LOADER, "wrote maxCompressedFrameSize:%d", maxCompressedFrameSize);

    fflush(cacheFile);
    fclose(cacheFile);

    info->cacheFile = fopen(cachePath.c_str(), "r+");
    if (info->cacheFile == nullptr) {
      return 2;
    }

    info->cacheBuffer = (uint8_t *) realloc(compressBuffer, maxCompressedFrameSize);
    info->cacheBufferSize = maxCompressedFrameSize;

    fseek(info->cacheFile, info->headerSize + sizeof(uint32_t) + firstFrameSize, SEEK_SET);
    info->nextFrameNo = 1;

    AndroidBitmap_unlockPixels(env, bitmap);
  } else if (cacheExists) {
    info->maxCompressedFrameSize = maxCompressedFrameSize;
    info->getBuffer(maxCompressedFrameSize);
    info->cacheFile = cacheFile;

    fseek(info->cacheFile, info->headerSize + sizeof(uint32_t) + firstFrameSize, SEEK_SET);
    info->nextFrameNo = 1;
  } else {
    if (cacheFile != nullptr) {
      fclose(cacheFile);
    }
    return 1;
  }
  return 0;
}

JNI_FUNC(jboolean, getLottieFrame, jlong ptr, jobject bitmap, jlong jFrameNo) {
  if (ptr == 0 || bitmap == nullptr) {
    return JNI_FALSE;
  }
  auto *info = jni::jlong_to_ptr<LottieInfo *>(ptr);
  uint32_t frameNo = (uint32_t) jFrameNo;

  if (info == nullptr || info->animation == nullptr)
    return JNI_FALSE;

  AndroidBitmapInfo bitmapInfo;
  AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

  void *pixels;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    return JNI_FALSE;

  bool success = false;

  if (info->cacheFile != nullptr) {
    if (info->nextFrameNo >= tlottie_frame_count(info->animation) || frameNo < info->nextFrameNo) {
      fseek(info->cacheFile, info->headerSize, SEEK_SET);
      info->nextFrameNo = 0;
    }
    bool fileError = false;
    while (frameNo > info->nextFrameNo) {
      uint32_t compressedSize;
      if (fread(&compressedSize, sizeof(compressedSize), 1, info->cacheFile) == 1 &&
          (compressedSize == 0 || fseek(info->cacheFile, compressedSize, SEEK_CUR) == 0)) {
        info->nextFrameNo++;
      } else {
        fileError = true;
        break;
      }
    }
    if (info->nextFrameNo == frameNo && !fileError) {
      uint32_t compressedSize;
      if (fread(&compressedSize, sizeof(compressedSize), 1, info->cacheFile) == 1) {
        // limit compressed frame size to be <= than maxCompressedFrameSize. when it's unset, limit to MAX_COMPRESSED_BUFFER_SIZE
        if (compressedSize <= info->maxCompressedFrameSize || (info->maxCompressedFrameSize == 0 && compressedSize <= MAX_COMPRESSED_BUFFER_SIZE)) {
          if (compressedSize == 0) {
            info->nextFrameNo++;
          } else {
            uint8_t *buffer = info->getBuffer(compressedSize);
            if (buffer != nullptr &&
                fread(buffer, sizeof(uint8_t), compressedSize, info->cacheFile) == compressedSize) {
              info->nextFrameNo++;
              LZ4_decompress_safe((const char *) info->cacheBuffer, (char *) pixels,
                                  (int) compressedSize,
                                  (int) (bitmapInfo.height * bitmapInfo.stride));
              success = true;
            } else {
              // Failed to allocate buffer or read desired amount of bytes / EOF
              fileError = true;
            }
          }
        } else {
          loge(TAG_GIF_LOADER, "sticker cache file size verification failed: %d, max: %d", compressedSize, info->maxCompressedFrameSize);
          fileError = true;
        }
      } else {
        loge(TAG_GIF_LOADER, "sticker cache file corrupted");
        fileError = true;
      }
    }
    if (fileError) {
      info->hadCacheFileErrors = true;
      if (fseek(info->cacheFile, info->headerSize, SEEK_SET) == 0) {
        // loge(TAG_GIF_LOADER, "file error, moving to the first frame, frameNo:%d, nextFrameNo:%d", frameNo, info->nextFrameNo);
        info->nextFrameNo = 0;
      } else {
        loge(TAG_GIF_LOADER, "cache file error, switching to direct mode");
        fclose(info->cacheFile);
        info->cacheFile = nullptr;
      }
    }
  }

  if (!success) {
    renderLottieFrame(info->animation, (uint32_t) frameNo, pixels, bitmapInfo, &info->frameBuffer,
                      &info->frameBufferSize);
    if (info->cacheFile != nullptr) {
      logi(TAG_GIF_LOADER, "read frame directly: %d, nextFrameNo:%d, totalFrame:%d", frameNo,
           info->nextFrameNo, tlottie_frame_count(info->animation));
    }
    // libyuv::ABGRToARGB((uint8_t *) pixels, bitmapInfo.stride, (uint8_t *) pixels, bitmapInfo.stride, bitmapInfo.width, bitmapInfo.height);
  }

  AndroidBitmap_unlockPixels(env, bitmap);

  return JNI_TRUE;
}
