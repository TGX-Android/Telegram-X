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
#include <cstdint>
#include <limits>
#include <string>
#include <utility>
#include <rlottie.h>
extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/eval.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
#include <libavcodec/avcodec.h>

}

#include <lz4.h>
#include <unistd.h>
#include <pthread.h>

#include "bridge.h"

#define MAX_GIF_SIZE 920
#define BITMAP_TARGET_FORMAT AV_PIX_FMT_RGBA
#define LOTTIE_CACHE_MAGIC 0xf0ebaef1
#define LOTTIE_CACHE_MAGIC_REDUCED 0xf0ebaef2

static const std::string av_make_error_str (int errnum) {
  char errbuf[AV_ERROR_MAX_STRING_SIZE];
  av_strerror(errnum, errbuf, AV_ERROR_MAX_STRING_SIZE);
  return std::string(errbuf);
}

#undef av_err2str
#define av_err2str(errnum) av_make_error_str(errnum).c_str()

struct LottieInfo {
  const std::string path;
  std::unique_ptr<rlottie::Animation> animation;
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
  }
};

struct VideoInfo {

  const std::string path;

  VideoInfo (const std::string &path) : path(path) { }

  ~VideoInfo () {
    if (video_dec_ctx) {
      avcodec_close(video_dec_ctx);
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
    if (packet != nullptr) {
      av_packet_free(&packet);
      packet = nullptr;
    }
    video_stream_idx = -1;
    video_stream = nullptr;
  }

  AVFormatContext *fmt_ctx = nullptr;
  int video_stream_idx = -1;
  AVStream *video_stream = nullptr;
  AVCodecContext *video_dec_ctx = nullptr;
  SwsContext *scale_ctx = nullptr;
  int32_t dst_linesize[1];
  AVFrame *frame = nullptr;
  int srcWidth = -1, srcHeight = -1;
  int dstWidth = -1, dstHeight = -1;
  int frameRate = -1;
  bool has_decoded_frames = false;
  bool is_broken = false;
  bool had_invalid_frames = false;

  AVPacket *packet = nullptr;
};

int open_codec_context (int *stream_idx, AVCodecContext **dec_ctx, AVFormatContext *fmt_ctx, enum AVMediaType type) {
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
    context->thread_count = 0;
    if (dec->capabilities & AV_CODEC_CAP_FRAME_THREADS) {
      context->thread_type = FF_THREAD_FRAME;
    } else if (dec->capabilities & AV_CODEC_CAP_SLICE_THREADS) {
      context->thread_type = FF_THREAD_SLICE;
    } else {
      context->thread_count = 1;
    }

    av_dict_set(&opts, "refcounted_frames", "1", 0);
    if ((ret = avcodec_open2(*dec_ctx, dec, &opts)) < 0) {
      loge(TAG_GIF_LOADER, "failed to open %s decoder for %s", av_get_media_type_string(type), avcodec_get_name(st->codecpar->codec_id));
      return -1;
    }
  }

  return 0;
}

JNI_FUNC(jlong, createDecoder, jstring src, jintArray data, jdouble startMediaTimestamp) {

  VideoInfo *info = new VideoInfo(jni::from_jstring(env, src));

  int ret;
  if ((ret = avformat_open_input(&info->fmt_ctx, info->path.c_str(), nullptr, nullptr)) < 0) {
    loge(TAG_GIF_LOADER, "can't open source file %s, %s", info->path.c_str(), av_err2str(ret));
    delete info;
    return 0;
  }

  if ((ret = avformat_find_stream_info(info->fmt_ctx, nullptr)) < 0) {
    loge(TAG_GIF_LOADER, "can't find stream information %s, %s", info->path.c_str(),
         av_err2str(ret));
    delete info;
    return 0;
  }

  if (open_codec_context(&info->video_stream_idx, &info->video_dec_ctx, info->fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
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
    return 0;
  }

  const int srcWidth = info->srcWidth = info->video_dec_ctx->width;
  const int srcHeight = info->srcHeight = info->video_dec_ctx->height;

  int dstWidth = srcWidth;
  int dstHeight = srcHeight;

  if (dstWidth > 0 && dstHeight > 0) {
    int newWidth, newHeight;
    if (std::max(dstWidth, dstHeight) > MAX_GIF_SIZE) {
      float scaleW = (float) MAX_GIF_SIZE / (float) dstWidth;
      float scaleH = (float) MAX_GIF_SIZE / (float) dstHeight;
      float scale = scaleW < scaleH ? scaleW : scaleH;
      newWidth = (int) ((float) dstWidth * scale);
      newHeight = (int) ((float) dstHeight * scale);

      newWidth -= newWidth % 2;
      newHeight -= newHeight % 2;
    } else {
      newWidth = dstWidth;
      newHeight = dstHeight;
    }

    if (newWidth > 0 && newHeight > 0) {
      AVPixelFormat fmt = info->video_dec_ctx->pix_fmt;
      if (fmt != AV_PIX_FMT_NONE && fmt != AV_PIX_FMT_YUVA420P && (fmt != BITMAP_TARGET_FORMAT || newWidth != srcWidth || newHeight != srcHeight)) {
        info->scale_ctx = sws_getContext(srcWidth, srcHeight, fmt, newWidth, newHeight,
                                         BITMAP_TARGET_FORMAT, newWidth == srcWidth && newHeight == srcHeight ? SWS_FAST_BILINEAR : SWS_BILINEAR, nullptr, nullptr,
                                         nullptr);
        if (info->scale_ctx != nullptr) {
          dstWidth = newWidth;
          dstHeight = newHeight;
          logi(TAG_GIF_LOADER, "Created scale context %dx%d -> %dx%d, format: %d", srcWidth, srcHeight, dstWidth, dstHeight, BITMAP_TARGET_FORMAT);
        }
      }
    }
  }

  int frameRate = (int) (1000 * av_q2d(info->video_stream->avg_frame_rate));

  info->dstWidth = dstWidth;
  info->dstHeight = dstHeight;
  info->frameRate = frameRate;

  if (startMediaTimestamp != 0) {
    int ret = 0;
    int64_t ts = (int64_t) (startMediaTimestamp * (double) AV_TIME_BASE);
    ret = avformat_seek_file(info->fmt_ctx, -1, std::numeric_limits<int64_t>::min(), ts,
                             std::numeric_limits<int64_t>::max(), 0);
    if (ret < 0) {
      loge(TAG_GIF_LOADER, "can't seek to startMediaTimestamp %s, %s", info->path.c_str(), av_err2str(ret));
    }
  }

  jint *dataArr = env->GetIntArrayElements(data, 0);
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
    env->ReleaseIntArrayElements(data, dataArr, 0);
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

JNI_FUNC(jboolean, seekVideoToStart, jlong ptr) {
  if (ptr == 0) {
    return JNI_FALSE;
  }

  VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);

  int ret = 0;
  ret = avformat_seek_file(info->fmt_ctx, -1, std::numeric_limits<int64_t>::min(), 0,
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

void to_android_bitmap (JNIEnv *env, VideoInfo *info, jobject bitmap, jintArray data) {
  auto fmt = (AVPixelFormat) info->frame->format;

  AndroidBitmapInfo bitmapInfo;
  AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);

  if (bitmapInfo.width == info->dstWidth && bitmapInfo.height == info->dstHeight) {
    jint *dataArr = env->GetIntArrayElements(data, 0);
    if (dataArr != nullptr) {
      dataArr[3] = (int) (1000 * info->frame->pts * av_q2d(info->video_stream->time_base));
      env->ReleaseIntArrayElements(data, dataArr, 0);
    }

    AVFrame *frame = info->frame;
    int frameWidth = frame->width;
    int frameHeight = frame->height;

    void *pixels;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
      if (info->scale_ctx != nullptr) {
        uint8_t *dst_data[1];
        dst_data[0] = (uint8_t *) pixels;
        info->dst_linesize[0] = bitmapInfo.stride;
        // TODO: find out why sws_scale doesn't support transparency (AV_PIX_FMT_YUVA420P) properly
        // note: for now, updated libyuv + kYvuI601Constants in I420AlphaToARGBMatrix fixes AV_PIX_FMT_YUVA420P issue - but still needs to be researched
        /*int res =*/ sws_scale(info->scale_ctx, frame->data, frame->linesize, 0, frame->height, dst_data, info->dst_linesize);
      } else {
        // TODO: find out why libyuv damages the color palette
        switch (fmt) {
          case BITMAP_TARGET_FORMAT: {
            int size = av_image_get_buffer_size(fmt, frameWidth, frameHeight, 1);
            memcpy((uint8_t *) pixels, frame->data[0], size);
            break;
          }
          case AV_PIX_FMT_YUV420P:
          case AV_PIX_FMT_YUVJ420P:
            if (frame->colorspace == AVColorSpace::AVCOL_SPC_BT709) {
              libyuv::H420ToARGB(frame->data[0], frame->linesize[0], frame->data[2],
                                 frame->linesize[2], frame->data[1], frame->linesize[1],
                                 (uint8_t *) pixels, frameWidth * 4, frameWidth, frameHeight);
            } else {
              libyuv::I420ToARGB(frame->data[0], frame->linesize[0], frame->data[2],
                                 frame->linesize[2], frame->data[1], frame->linesize[1],
                                 (uint8_t *) pixels, frameWidth * 4, frameWidth, frameHeight);
            }
            break;
          case AV_PIX_FMT_YUVA420P:
            libyuv::I420AlphaToARGBMatrix(frame->data[0], frame->linesize[0], frame->data[2],
                                          frame->linesize[2], frame->data[1], frame->linesize[1],
                                          frame->data[3], frame->linesize[3],
                                          (uint8_t *) pixels, frameWidth * 4,
                                          &libyuv::kYvuI601Constants, frameWidth, frameHeight,
                                          50);
            break;
          case AV_PIX_FMT_BGRA:
            libyuv::ABGRToARGB(frame->data[0], frame->linesize[0], (uint8_t *) pixels,
                               frameWidth * 4, frameWidth, frameHeight);
            break;
          default:
            // TODO more libyuv cases?
            logw(TAG_GIF_LOADER, "unsupported pixel format: %d", fmt);
            break;
        }
      }
      AndroidBitmap_unlockPixels(env, bitmap);
    }
  }
}

JNI_FUNC(jint, getVideoFrame, jlong ptr, jobject bitmap, jintArray data) {
  if (ptr == 0 || bitmap == nullptr) {
    return 0;
  }
  VideoInfo *info = jni::jlong_to_ptr<VideoInfo *>(ptr);
  if (info->is_broken) {
    return 0;
  }

  bool gotFrame = false;
  bool hasLooped = false;
  bool eofReached = false;
  bool fatalError = false;
  size_t errorCount = 0;
  size_t maxErrorCount = round(std::min(120.0, (double) info->frameRate / 1000.0) * 2.5);

  do {
    int ret;

    if (eofReached) {
      ret = avformat_seek_file(
        info->fmt_ctx, -1,
        std::numeric_limits<int64_t>::min(), 0,
        std::numeric_limits<int64_t>::max(), 0
      );
      if (ret != 0) {
        loge(TAG_GIF_LOADER, "can't seek %s to start, %s",info->path.c_str(), av_err2str(ret));
        fatalError = true;
        break;
      }
      info->has_decoded_frames = false;
      eofReached = false;
      hasLooped = true;
      avcodec_flush_buffers(info->video_dec_ctx);
    }
    ret = av_read_frame(info->fmt_ctx, info->packet);
    if (ret != 0) {
      if (!info->has_decoded_frames) {
        loge(TAG_GIF_LOADER, "av_read_frame fatal error for %s: %s", info->path.c_str(), av_err2str(ret));
        fatalError = true;
        break;
      }
      if (ret == AVERROR_EOF) {
        eofReached = true;
        continue;
      }
      loge(TAG_GIF_LOADER, "av_read_frame fatal error for %s: %s", info->path.c_str(), av_err2str(ret));
      fatalError = true;
      break;
    }
    ret = avcodec_send_packet(info->video_dec_ctx, info->packet);
    if (ret != 0) {
      if (ret == AVERROR_INVALIDDATA && info->has_decoded_frames && errorCount < maxErrorCount) {
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

  if (gotFrame) {
    to_android_bitmap(env, info, bitmap, data);
    info->has_decoded_frames = true;
    av_frame_unref(info->frame);
    return hasLooped ? 2 : 1;
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
  std::unique_ptr<rlottie::Animation> animation = rlottie::Animation::loadFromData(json, path, nullptr);
  if (animation == nullptr || animation->totalFrame() == 0) {
    return JNI_FALSE;
  }
  AndroidBitmapInfo bitmapInfo;
  AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
  void *pixels;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    return JNI_FALSE;
  }
  rlottie::Surface surface((uint32_t *) pixels, bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
  animation->renderSync(0, surface, true);
  AndroidBitmap_unlockPixels(env, bitmap);
  return JNI_TRUE;
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

  rlottie::FitzModifier modifier = rlottie::FitzModifier::None;
  switch (fitzpatrickType) {
    case 1:
    case 2:
    case 12:
      modifier = rlottie::FitzModifier::Type12;
      break;
    case 3:
      modifier = rlottie::FitzModifier::Type3;
      break;
    case 4:
      modifier = rlottie::FitzModifier::Type4;
      break;
    case 5:
      modifier = rlottie::FitzModifier::Type5;
      break;
    case 6:
      modifier = rlottie::FitzModifier::Type6;
      break;
  }

  LottieInfo *info = new LottieInfo(path);
  info->animation = rlottie::Animation::loadFromData(json, path, nullptr, modifier);

  if (info->animation == nullptr) {
    delete info;
    return 0;
  }

  size_t totalFrame = info->animation->totalFrame();
  double frameRate = info->animation->frameRate();
  double duration = info->animation->duration();

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

  size_t width, height;
  info->animation->size(width, height);
  dataArr[0] = (jint) width;
  dataArr[1] = (jint) height;
  env->ReleaseIntArrayElements(data, dataArr, 0);
}

#define MAX_COMPRESSED_BUFFER_SIZE (1024 * 1024 * 15 /*15 MiB*/)

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

  double frameRate = info->animation->frameRate();
  bool skipOdd = frameRate == 60.0 && limitFps == JNI_TRUE;
  uint32_t frameCount = (uint32_t) info->animation->totalFrame();
  uint32_t maxCompressedFrameSize = 0;
  uint32_t firstFrameSize = 0;

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
        rlottie::Surface surface((uint32_t *) pixels, bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
        info->animation->renderSync((size_t) frameNo, surface, true);
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
    if (info->nextFrameNo >= info->animation->totalFrame() || frameNo < info->nextFrameNo) {
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
    rlottie::Surface surface((uint32_t *) pixels, bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
    info->animation->renderSync((size_t) frameNo, surface, true);
    if (info->cacheFile != nullptr) {
      logi(TAG_GIF_LOADER, "read frame directly: %d, nextFrameNo:%d, totalFrame:%d", frameNo, info->nextFrameNo, info->animation->totalFrame());
    }
    // libyuv::ABGRToARGB((uint8_t *) pixels, bitmapInfo.stride, (uint8_t *) pixels, bitmapInfo.stride, bitmapInfo.width, bitmapInfo.height);
  }

  AndroidBitmap_unlockPixels(env, bitmap);

  return JNI_TRUE;
}
