#include <jni.h>
#include <ogg/ogg.h>
#include <stdio.h>
#include <opus.h>
#include <stdlib.h>
#include <time.h>
#include <opusfile.h>

#define NO_AUDIO_LOG

#ifndef NO_AUDIO_LOG
#include "utils.h"
#else
#define logi(...) ((void) 0)
#define loge(...) ((void) 0)
#define logw(...) ((void) 0)
#endif

#ifndef max
#define max(x, y) ((x) > (y)) ? (x) : (y)
#endif
#ifndef min
#define min(x, y) ((x) < (y)) ? (x) : (y)
#endif

typedef struct {
    int version;
    int channels; /* Number of channels: 1..255 */
    int preskip;
    ogg_uint32_t input_sample_rate;
    int gain; /* in dB S7.8 should be zero whenever possible */
    int channel_mapping;
    /* The rest is only used if channel_mapping != 0 */
    int nb_streams;
    int nb_coupled;
    unsigned char stream_map[255];
} OpusHeader;

typedef struct {
    unsigned char *data;
    int maxlen;
    int pos;
} Packet;

typedef struct {
    const unsigned char *data;
    int maxlen;
    int pos;
} ROPacket;

typedef struct {
    void *readdata;
    opus_int64 total_samples_per_channel;
    int rawmode;
    int channels;
    long rate;
    int gain;
    int samplesize;
    int endianness;
    char *infilename;
    int ignorelength;
    int skip;
    int extraout;
    char *comments;
    int comments_length;
    int copy_comments;
} oe_enc_opt;

static int write_uint32(Packet *p, ogg_uint32_t val) {
    if (p->pos > p->maxlen - 4) {
        return 0;
    }
    p->data[p->pos  ] = (uint8_t) ((val    ) & 0xFF);
    p->data[p->pos+1] = (uint8_t) ((val>> 8) & 0xFF);
    p->data[p->pos+2] = (uint8_t) ((val>>16) & 0xFF);
    p->data[p->pos+3] = (uint8_t) ((val>>24) & 0xFF);
    p->pos += 4;
    return 1;
}

static int write_uint16(Packet *p, ogg_uint16_t val) {
    if (p->pos > p->maxlen-2) {
        return 0;
    }
    p->data[p->pos  ] = (uint8_t) ((val    ) & 0xFF);
    p->data[p->pos+1] = (uint8_t) ((val>> 8) & 0xFF);
    p->pos += 2;
    return 1;
}

static int write_chars(Packet *p, const unsigned char *str, int nb_chars)
{
    int i;
    if (p->pos>p->maxlen-nb_chars)
        return 0;
    for (i=0;i<nb_chars;i++)
        p->data[p->pos++] = str[i];
    return 1;
}

static int read_uint32(ROPacket *p, ogg_uint32_t *val)
{
    if (p->pos>p->maxlen-4)
        return 0;
    *val =  (ogg_uint32_t)p->data[p->pos  ];
    *val |= (ogg_uint32_t)p->data[p->pos+1]<< 8;
    *val |= (ogg_uint32_t)p->data[p->pos+2]<<16;
    *val |= (ogg_uint32_t)p->data[p->pos+3]<<24;
    p->pos += 4;
    return 1;
}

static int read_uint16(ROPacket *p, ogg_uint16_t *val)
{
    if (p->pos>p->maxlen-2)
        return 0;
    *val =  (ogg_uint16_t)p->data[p->pos  ];
    *val |= (ogg_uint16_t)p->data[p->pos+1]<<8;
    p->pos += 2;
    return 1;
}

static int read_chars(ROPacket *p, unsigned char *str, int nb_chars)
{
    int i;
    if (p->pos>p->maxlen-nb_chars)
        return 0;
    for (i=0;i<nb_chars;i++)
        str[i] = p->data[p->pos++];
    return 1;
}

int opus_header_to_packet(const OpusHeader *h, unsigned char *packet, int len) {
    int i;
    Packet p;
    unsigned char ch;

    p.data = packet;
    p.maxlen = len;
    p.pos = 0;
    if (len < 19) {
        return 0;
    }
    if (!write_chars(&p, (const unsigned char *)"OpusHead", 8)) {
        return 0;
    }

    ch = 1;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }

    ch = h->channels;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }

    if (!write_uint16(&p, h->preskip)) {
        return 0;
    }

    if (!write_uint32(&p, h->input_sample_rate)) {
        return 0;
    }

    if (!write_uint16(&p, h->gain)) {
        return 0;
    }

    ch = h->channel_mapping;
    if (!write_chars(&p, &ch, 1)) {
        return 0;
    }

    if (h->channel_mapping != 0) {
        ch = h->nb_streams;
        if (!write_chars(&p, &ch, 1)) {
            return 0;
        }

        ch = h->nb_coupled;
        if (!write_chars(&p, &ch, 1)) {
            return 0;
        }

        /* Multi-stream support */
        for (i = 0; i < h->channels; i++) {
            if (!write_chars(&p, &h->stream_map[i], 1)) {
                return 0;
            }
        }
    }

    return p.pos;
}

#define writeint(buf, base, val) do { buf[base + 3] = ((val) >> 24) & 0xff; \
buf[base + 2]=((val) >> 16) & 0xff; \
buf[base + 1]=((val) >> 8) & 0xff; \
buf[base] = (val) & 0xff; \
} while(0)

static void comment_init(char **comments, int *length, const char *vendor_string) {
    // The 'vendor' field should be the actual encoding library used
    int vendor_length = strlen(vendor_string);
    int user_comment_list_length = 0;
    int len = 8 + 4 + vendor_length + 4;
    char *p = (char *)malloc(len);
    memcpy(p, "OpusTags", 8);
    writeint(p, 8, vendor_length);
    memcpy(p + 12, vendor_string, vendor_length);
    writeint(p, 12 + vendor_length, user_comment_list_length);
    *length = len;
    *comments = p;
}

static void comment_pad(char **comments, int* length, int amount) {
    if (amount > 0) {
        char *p = *comments;
        // Make sure there is at least amount worth of padding free, and round up to the maximum that fits in the current ogg segments
        int newlen = (*length + amount + 255) / 255 * 255 - 1;
        p = realloc(p, newlen);
        for (int i = *length; i < newlen; i++) {
            p[i] = 0;
        }
        *comments = p;
        *length = newlen;
    }
}

static int writeOggPage(ogg_page *page, FILE *os) {
    int written = fwrite(page->header, sizeof(unsigned char), page->header_len, os);
    written += fwrite(page->body, sizeof(unsigned char), page->body_len, os);
    return written;
}

const opus_int32 bitrate = 32000;
const opus_int32 rate = 48000;
const opus_int32 frame_size = 960;
const int with_cvbr = 1;
const int max_ogg_delay = 0;
const int comment_padding = 512;

opus_int32 coding_rate = 48000;
ogg_int32_t _packetId;
OpusEncoder *_encoder = 0;
uint8_t *_packet = 0;
ogg_stream_state os;
FILE *_fileOs = 0;
oe_enc_opt inopt;
OpusHeader header;
opus_int32 min_bytes;
int max_frame_bytes;
ogg_packet op;
ogg_page og;
opus_int64 bytes_written;
opus_int64 pages_out;
opus_int64 total_samples;
ogg_int64_t enc_granulepos;
ogg_int64_t last_granulepos;
int size_segments;
int last_segments;

void cleanupRecorder() {

    ogg_stream_flush(&os, &og);

    if (_encoder) {
        opus_encoder_destroy(_encoder);
        _encoder = 0;
    }

    ogg_stream_clear(&os);

    if (_packet) {
        free(_packet);
        _packet = 0;
    }

    if (_fileOs) {
        fclose(_fileOs);
        _fileOs = 0;
    }

    _packetId = -1;
    bytes_written = 0;
    pages_out = 0;
    total_samples = 0;
    enc_granulepos = 0;
    size_segments = 0;
    last_segments = 0;
    last_granulepos = 0;
    memset(&os, 0, sizeof(ogg_stream_state));
    memset(&inopt, 0, sizeof(oe_enc_opt));
    memset(&header, 0, sizeof(OpusHeader));
    memset(&op, 0, sizeof(ogg_packet));
    memset(&og, 0, sizeof(ogg_page));
}

int initRecorder(const char *path) {
    cleanupRecorder();

    if (!path) {
        return 0;
    }

    _fileOs = fopen(path, "wb");
    if (!_fileOs) {
        return 0;
    }

    inopt.rate = rate;
    inopt.gain = 0;
    inopt.endianness = 0;
    inopt.copy_comments = 0;
    inopt.rawmode = 1;
    inopt.ignorelength = 1;
    inopt.samplesize = 16;
    inopt.channels = 1;
    inopt.skip = 0;

    comment_init(&inopt.comments, &inopt.comments_length, opus_get_version_string());

    if (rate > 24000) {
        coding_rate = 48000;
    } else if (rate > 16000) {
        coding_rate = 24000;
    } else if (rate > 12000) {
        coding_rate = 16000;
    } else if (rate > 8000) {
        coding_rate = 12000;
    } else {
        coding_rate = 8000;
    }

    if (rate != coding_rate) {
        loge(TAG_VOICE, "Invalid rate");
        return 0;
    }

    header.channels = 1;
    header.channel_mapping = 0;
    header.input_sample_rate = rate;
    header.gain = inopt.gain;
    header.nb_streams = 1;

    int result = OPUS_OK;
    _encoder = opus_encoder_create(coding_rate, 1, OPUS_APPLICATION_VOIP, &result);
    if (result != OPUS_OK) {
        loge(TAG_VOICE, "Error cannot create encoder: %s", opus_strerror(result));
        return 0;
    }

    min_bytes = max_frame_bytes = (1275 * 3 + 7) * header.nb_streams;
    _packet = malloc(max_frame_bytes);

    result = opus_encoder_ctl(_encoder, OPUS_SET_BITRATE(bitrate));
    if (result != OPUS_OK) {
        loge(TAG_VOICE, "Error OPUS_SET_BITRATE returned: %s", opus_strerror(result));
        return 0;
    }

#ifdef OPUS_SET_LSB_DEPTH
    result = opus_encoder_ctl(_encoder, OPUS_SET_LSB_DEPTH(max(8, min(24, inopt.samplesize))));
    if (result != OPUS_OK) {
        loge(TAG_VOICE, "Warning OPUS_SET_LSB_DEPTH returned: %s", opus_strerror(result));
    }
#endif

    opus_int32 lookahead;
    result = opus_encoder_ctl(_encoder, OPUS_GET_LOOKAHEAD(&lookahead));
    if (result != OPUS_OK) {
        loge(TAG_VOICE, "Error OPUS_GET_LOOKAHEAD returned: %s", opus_strerror(result));
        return 0;
    }

    inopt.skip += lookahead;
    header.preskip = (int)(inopt.skip * (48000.0 / coding_rate));
    inopt.extraout = (int)(header.preskip * (rate / 48000.0));

    if (ogg_stream_init(&os, rand()) == -1) {
        loge(TAG_VOICE, "Error: stream init failed");
        return 0;
    }

    unsigned char header_data[100];
    int packet_size = opus_header_to_packet(&header, header_data, 100);
    op.packet = header_data;
    op.bytes = packet_size;
    op.b_o_s = 1;
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 0;
    ogg_stream_packetin(&os, &op);

    while ((result = ogg_stream_flush(&os, &og))) {
        if (!result) {
            break;
        }

        int pageBytesWritten = writeOggPage(&og, _fileOs);
        if (pageBytesWritten != og.header_len + og.body_len) {
            loge(TAG_VOICE, "Error: failed writing header to output stream");
            return 0;
        }
        bytes_written += pageBytesWritten;
        pages_out++;
    }

    comment_pad(&inopt.comments, &inopt.comments_length, comment_padding);
    op.packet = (unsigned char *)inopt.comments;
    op.bytes = inopt.comments_length;
    op.b_o_s = 0;
    op.e_o_s = 0;
    op.granulepos = 0;
    op.packetno = 1;
    ogg_stream_packetin(&os, &op);

    while ((result = ogg_stream_flush(&os, &og))) {
        if (result == 0) {
            break;
        }

        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            loge(TAG_VOICE, "Error: failed writing header to output stream");
            return 0;
        }

        bytes_written += writtenPageBytes;
        pages_out++;
    }

    free(inopt.comments);

    return 1;
}

int writeFrame(uint8_t *framePcmBytes, unsigned int frameByteCount) {
    int cur_frame_size = frame_size;
    _packetId++;

    opus_int32 nb_samples = frameByteCount / 2;
    total_samples += nb_samples;
    if (nb_samples < frame_size) {
        op.e_o_s = 1;
    } else {
        op.e_o_s = 0;
    }

    int nbBytes = 0;

    if (nb_samples != 0) {
        uint8_t *paddedFrameBytes = framePcmBytes;
        int freePaddedFrameBytes = 0;

        if (nb_samples < cur_frame_size) {
            paddedFrameBytes = malloc(cur_frame_size * 2);
            freePaddedFrameBytes = 1;
            memcpy(paddedFrameBytes, framePcmBytes, frameByteCount);
            memset(paddedFrameBytes + nb_samples * 2, 0, cur_frame_size * 2 - nb_samples * 2);
        }

        nbBytes = opus_encode(_encoder, (opus_int16 *)paddedFrameBytes, cur_frame_size, _packet, max_frame_bytes / 10);
        if (freePaddedFrameBytes) {
            free(paddedFrameBytes);
            paddedFrameBytes = NULL;
        }

        if (nbBytes < 0) {
          loge(TAG_VOICE, "Encoding failed: %s. Aborting.", opus_strerror(nbBytes));
          return 0;
        }

        enc_granulepos += cur_frame_size * 48000 / coding_rate;
        size_segments = (nbBytes + 255) / 255;
        min_bytes = nbBytes < min_bytes ? nbBytes : min_bytes;
    }

    while ((((size_segments <= 255) && (last_segments + size_segments > 255)) || (enc_granulepos - last_granulepos > max_ogg_delay)) && ogg_stream_flush_fill(&os, &og, 255 * 255)) {
        if (ogg_page_packets(&og) != 0) {
            last_granulepos = ogg_page_granulepos(&og);
        }

        last_segments -= og.header[26];
        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            loge(TAG_VOICE, "Error: failed writing data to output stream");

            return 0;
        }
        bytes_written += writtenPageBytes;
        pages_out++;
    }

    op.packet = (unsigned char *)_packet;
    op.bytes = nbBytes;
    op.b_o_s = 0;
    op.granulepos = enc_granulepos;
    if (op.e_o_s) {
        op.granulepos = ((total_samples * 48000 + rate - 1) / rate) + header.preskip;
    }
    op.packetno = 2 + _packetId;
    ogg_stream_packetin(&os, &op);
    last_segments += size_segments;

    while ((op.e_o_s || (enc_granulepos + (frame_size * 48000 / coding_rate) - last_granulepos > max_ogg_delay) || (last_segments >= 255)) ? ogg_stream_flush_fill(&os, &og, 255 * 255) : ogg_stream_pageout_fill(&os, &og, 255 * 255)) {
        if (ogg_page_packets(&og) != 0) {
            last_granulepos = ogg_page_granulepos(&og);
        }
        last_segments -= og.header[26];
        int writtenPageBytes = writeOggPage(&og, _fileOs);
        if (writtenPageBytes != og.header_len + og.body_len) {
            loge(TAG_VOICE, "Error: failed writing data to output stream");
            return 0;
        }
        bytes_written += writtenPageBytes;
        pages_out++;
    }

    return 1;
}

JNIEXPORT int Java_org_thunderdog_challegram_N_startRecord(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);

    int result = initRecorder(pathStr);

    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }

    return result;
}

JNIEXPORT int Java_org_thunderdog_challegram_N_writeFrame(JNIEnv *env, jclass class, jobject frame, jint len) {
    jbyte *frameBytes = (*env)->GetDirectBufferAddress(env, frame);
    return writeFrame((uint8_t *) frameBytes, (size_t) len);
}

JNIEXPORT void Java_org_thunderdog_challegram_N_stopRecord(JNIEnv *env, jclass class) {
    cleanupRecorder();
}

//player
OggOpusFile *_opusFile;
int _isSeekable = 0;
ogg_int64_t _totalPcmDuration = 0;
ogg_int64_t _currentPcmOffset = 0;
int _finished = 0;
static const int playerBuffersCount = 3;
static const int playerSampleRate = 48000;

void cleanupPlayer() {
    if (_opusFile) {
        op_free(_opusFile);
        _opusFile = 0;
    }
    _isSeekable = 0;
    _totalPcmDuration = 0;
    _currentPcmOffset = 0;
    _finished = 0;
}

int seekPlayer(float position) {
    if (!_opusFile || !_isSeekable || position < 0) {
        return 0;
    }
    int result = op_pcm_seek(_opusFile, (ogg_int64_t)(position * _totalPcmDuration));
    if (result != OPUS_OK) {
        loge(TAG_VOICE, "op_pcm_seek failed: %d", result);
    }
    ogg_int64_t pcmPosition = op_pcm_tell(_opusFile);
    _currentPcmOffset = pcmPosition;
    return result == OPUS_OK;
}

int initPlayer(const char *path) {
    cleanupPlayer();

    int openError = OPUS_OK;
    _opusFile = op_open_file(path, &openError);
    if (!_opusFile || openError != OPUS_OK) {
        loge(TAG_VOICE, "op_open_file failed: %d", openError);
        cleanupPlayer();
        return 0;
    }

    _isSeekable = op_seekable(_opusFile);
    _totalPcmDuration = op_pcm_total(_opusFile, -1);

    logi(TAG_VOICE, "initPlayer seekable:%d totalPcmDuration:%ld", _isSeekable ? 1 : 0, _totalPcmDuration);

    return 1;
}

void fillBuffer(uint8_t *buffer, int capacity, int32_t *args) {
    if (_opusFile) {
      ogg_int64_t tell = op_pcm_tell(_opusFile);
        args[1] = tell < 0 ? 0 : tell;

        if (_finished) {
            args[0] = 0;
            args[1] = 0;
            args[2] = 1;
            return;
        } else {
            int writtenOutputBytes = 0;
            int endOfFileReached = 0;

            while (writtenOutputBytes < capacity) {
                int readSamples = op_read(_opusFile, (opus_int16 *)(buffer + writtenOutputBytes), (capacity - writtenOutputBytes) / 2, NULL);

                if (readSamples > 0) {
                    writtenOutputBytes += readSamples * 2;
                } else {
                    if (readSamples < 0) {
                        loge(TAG_VOICE, "op_read failed: %d", readSamples);
                    }
                    endOfFileReached = 1;
                    break;
                }
            }

            args[0] = writtenOutputBytes;

            if (endOfFileReached || args[1] + args[0] == _totalPcmDuration) {
                _finished = 1;
                args[2] = 1;
            } else {
                args[2] = 0;
            }
        }
    } else {
        memset(buffer, 0, capacity);
        args[0] = capacity;
        args[1] = _totalPcmDuration;
    }
}

JNIEXPORT jlong Java_org_thunderdog_challegram_N_getTotalPcmDuration(JNIEnv *env, jclass class) {
    return _totalPcmDuration;
}

JNIEXPORT void Java_org_thunderdog_challegram_N_readOpusFile(JNIEnv *env, jclass class, jobject buffer, jint capacity, jintArray args) {
    jint *argsArr = (*env)->GetIntArrayElements(env, args, 0);
    jbyte *bufferBytes = (*env)->GetDirectBufferAddress(env, buffer);
    fillBuffer((uint8_t *) bufferBytes, capacity, argsArr);
    (*env)->ReleaseIntArrayElements(env, args, argsArr, 0);
}

JNIEXPORT int Java_org_thunderdog_challegram_N_seekOpusFile(JNIEnv *env, jclass class, jfloat position) {
    return seekPlayer(position);
}

JNIEXPORT int Java_org_thunderdog_challegram_N_openOpusFile(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);

    int result = initPlayer(pathStr);

    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }

    return result;
}

JNIEXPORT int Java_org_thunderdog_challegram_N_isOpusFile(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);

    int result = 0;

    int error = OPUS_OK;
    OggOpusFile *file = op_test_file(pathStr, &error);
    if (file != NULL) {
        int error = op_test_open(file);
        op_free(file);

        result = error == OPUS_OK;
    }

    logi(TAG_VOICE, "op_test_file: %d, error: %d", file != NULL ? 1 : 0, error);

    if (pathStr != 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathStr);
    }

    return result;
}

int16_t *sampleBuffer = NULL;

static inline void set_bits(uint8_t *bytes, int32_t bitOffset, int32_t value) {
    bytes += bitOffset / 8;
    bitOffset %= 8;
    *((int32_t *) bytes) |= (value << bitOffset);
}

JNIEXPORT jbyteArray Java_org_thunderdog_challegram_N_getWaveform(JNIEnv *env, jclass class, jstring path) {
    const char *pathStr = (*env)->GetStringUTFChars(env, path, 0);
    if (pathStr == NULL) {
      logw(TAG_VOICE, "pathStr == NULL");
      return NULL;
    }

    jbyteArray result = 0;

    int error = OPUS_OK;
    OggOpusFile *opusFile = op_open_file(pathStr, &error);
    if (opusFile == NULL || error != OPUS_OK) {
      logw(TAG_VOICE, "getWaveform failed, file:%d, error: %d", opusFile != NULL ? 1 : 0, error);
      (*env)->ReleaseStringUTFChars(env, path, pathStr);
      return result;
    }
    int64_t totalSamples = op_pcm_total(opusFile, -1);
    int32_t resultSamples = 100;
    int32_t sampleRate = (int32_t) max(1, totalSamples / resultSamples);

    if (totalSamples <= 0) {
      logw(TAG_VOICE, "op_pcm_total: %d, path: %d", totalSamples, strlen(pathStr));
      (*env)->ReleaseStringUTFChars(env, path, pathStr);
      return result;
    }

    uint16_t *samples = malloc(100 * 2);

    size_t bufferSize = 1024 * 128;
    if (sampleBuffer == NULL) {
        sampleBuffer = malloc(bufferSize);
    }
    uint64_t sampleIndex = 0;
    uint16_t peakSample = 0;

    int index = 0;

    while (1) {
        int readSamples = op_read(opusFile, sampleBuffer, bufferSize / 2, NULL);
        for (int i = 0; i < readSamples; i++) {
            uint16_t sample = (uint16_t) abs(sampleBuffer[i]);
            if (sample > peakSample) {
                peakSample = sample;
            }
            if (sampleIndex++ % sampleRate == 0) {
                if (index < resultSamples) {
                    samples[index++] = peakSample;
                }
                peakSample = 0;
            }
        }
        if (readSamples == 0) {
            break;
        }
    }

    int64_t sumSamples = 0;
    for (int i = 0; i < resultSamples; i++) {
        sumSamples += samples[i];
    }
    uint16_t peak = (uint16_t) (sumSamples * 1.8f / resultSamples);
    if (peak < 2500) {
        peak = 2500;
    }

    for (int i = 0; i < resultSamples; i++) {
        uint16_t sample = (uint16_t) ((int64_t) samples[i]);
        if (sample > peak) {
            samples[i] = peak;
        }
    }

    //free(sampleBuffer);
    op_free(opusFile);

    int bitstreamLength = (resultSamples * 5) / 8 + (((resultSamples * 5) % 8) == 0 ? 0 : 1);
    result = (*env)->NewByteArray(env, bitstreamLength);
    jbyte *bytes = (*env)->GetByteArrayElements(env, result, NULL);

    for (int i = 0; i < resultSamples; i++) {
        int32_t value = min(31, abs((int32_t) samples[i]) * 31 / peak);
        set_bits(bytes, i * 5, value & 31);
    }

    (*env)->ReleaseByteArrayElements(env, result, bytes, JNI_COMMIT);
    free(samples);

    (*env)->ReleaseStringUTFChars(env, path, pathStr);

    return result;
}

JNIEXPORT jbyteArray Java_org_thunderdog_challegram_N_getWaveform2(JNIEnv *env, jclass class, jshortArray array, jint length) {

    jshort *sampleBuffer = (*env)->GetShortArrayElements(env, array, 0);

    const int32_t resultSamples = 100;
    uint16_t *samples = malloc(100 * 2);
    uint64_t sampleIndex = 0;
    uint16_t peakSample = 0;
    int32_t sampleRate = (int32_t) max(1, length / resultSamples);
    int32_t index = 0;

    for (int32_t i = 0; i < length; i++) {
        uint16_t sample = (uint16_t) abs(sampleBuffer[i]);
        if (sample > peakSample) {
            peakSample = sample;
        }
        if (sampleIndex++ % sampleRate == 0) {
            if (index < resultSamples) {
                samples[index++] = peakSample;
            }
            peakSample = 0;
        }
    }

    int64_t sumSamples = 0;
    for (int32_t i = 0; i < resultSamples; i++) {
        sumSamples += samples[i];
    }
    uint16_t peak = (uint16_t) (sumSamples * 1.8f / resultSamples);
    if (peak < 2500) {
        peak = 2500;
    }

    for (int32_t i = 0; i < resultSamples; i++) {
        uint16_t sample = (uint16_t) ((int64_t) samples[i]);
        if (sample > peak) {
            samples[i] = peak;
        }
    }

    (*env)->ReleaseShortArrayElements(env, array, sampleBuffer, 0);

    uint32_t bitstreamLength = resultSamples * 5 / 8 + 1;
    jbyteArray *result = (*env)->NewByteArray(env, bitstreamLength);
    if (result) {
        uint8_t *bytes = malloc(bitstreamLength + 4);
        memset(bytes, 0, bitstreamLength + 4);
        for (int32_t i = 0; i < resultSamples; i++) {
            int32_t value = min(31, abs((int32_t) samples[i]) * 31 / peak);
            set_bits(bytes, i * 5, value & 31);
        }
        (*env)->SetByteArrayRegion(env, result, 0, bitstreamLength, (jbyte *) bytes);
    }
    free(samples);

    return result;
}