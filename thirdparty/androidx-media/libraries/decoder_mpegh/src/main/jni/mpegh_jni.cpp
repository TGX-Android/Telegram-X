/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/log.h>
#include <jni.h>

#include <cmath>
#include <cstdio>
#include <cstring>

#include "../include/mpeghdecoder.h"

#define LOG_TAG "mpeghdec_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGW(...) \
  ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define DECODER_FUNC(RETURN_TYPE, NAME, ...)                     \
  extern "C" {                                                   \
  JNIEXPORT RETURN_TYPE                                          \
      Java_androidx_media3_decoder_mpegh_MpeghDecoderJni_##NAME( \
          JNIEnv *env, jobject obj, ##__VA_ARGS__);              \
  }                                                              \
  JNIEXPORT RETURN_TYPE                                          \
      Java_androidx_media3_decoder_mpegh_MpeghDecoderJni_##NAME( \
          JNIEnv *env, jobject obj, ##__VA_ARGS__)

#define EXCEPTION_PATH "androidx/media3/decoder/mpegh/MpeghDecoderException"

#define MAX_NUM_FRAMES (6)
#define MAX_FRAME_LENGTH (3072)
#define MAX_NUM_CHANNELS (24)
#define BYTES_PER_SAMPLE (2)
#define MAX_OUTBUF_SIZE_SAMPLES \
  (MAX_NUM_FRAMES * MAX_FRAME_LENGTH * MAX_NUM_CHANNELS)

typedef struct DECODER_CONTEXT {
  int32_t outSampleRate;
  int32_t outNumChannels;
  int64_t outPts;

  HANDLE_MPEGH_DECODER_CONTEXT handle;
  int32_t samples[MAX_OUTBUF_SIZE_SAMPLES];
} DECODER_CONTEXT;

jfieldID getHandleFieldID(JNIEnv *env, jobject obj) {
  jclass cls = env->GetObjectClass(obj);
  return env->GetFieldID(cls, "decoderHandle", "J");
}

void setContext(JNIEnv *env, jobject obj, DECODER_CONTEXT *ctx) {
  jfieldID decoderHandle_fid = getHandleFieldID(env, obj);
  env->SetLongField(obj, decoderHandle_fid, (jlong)ctx);
}

DECODER_CONTEXT *getContext(JNIEnv *env, jobject obj) {
  jfieldID decoderHandle_fid = getHandleFieldID(env, obj);
  return (DECODER_CONTEXT *)env->GetLongField(obj, decoderHandle_fid);
}

/*
 * Method: init
 * will be used to initialize the JNI MPEG-H decoder wrapper.
 */
DECODER_FUNC(void, init, jint cicpIndex, jbyteArray mhaConfig,
             jint mhaConfigLength) {
  // create JNI decoder wrapper context
  auto *ctx = (DECODER_CONTEXT *)calloc(1, sizeof(DECODER_CONTEXT));
  if (ctx == nullptr) {
    LOGE("Unable to allocate memory for DECODER_CONTEXT!");
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "cannot create DECODER_CONTEXT");
    return;
  }

  // create MPEG-H decoder
  ctx->handle = mpeghdecoder_init(cicpIndex);
  if (ctx->handle == nullptr) {
    LOGE("Cannot create mpeghdecoder with CICP = %d!", cicpIndex);
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "Cannot create mpeghdecoder");
    return;
  }

  if (mhaConfigLength > 0) {
    auto *cData = (jbyte *)calloc(mhaConfigLength, sizeof(jbyte));
    env->GetByteArrayRegion(mhaConfig, 0, mhaConfigLength, cData);

    MPEGH_DECODER_ERROR result = mpeghdecoder_setMhaConfig(
        ctx->handle, (unsigned char *)cData, (uint32_t)mhaConfigLength);
    free(cData);
    if (result != MPEGH_DEC_OK) {
      LOGE("Cannot set MHA config!");
      jclass atscExCls = env->FindClass(EXCEPTION_PATH);
      env->ThrowNew(atscExCls, "Cannot set MHA config");
      return;
    }
  }

  // store the wrapper context in JNI env
  setContext(env, obj, ctx);
}

/*
 * Method: destroy
 * will be called to destroy the JNI MPEG-H decoder wrapper.
 */
DECODER_FUNC(void, destroy) {
  DECODER_CONTEXT *ctx = getContext(env, obj);

  mpeghdecoder_destroy(ctx->handle);
  free(ctx);
}

/*
 * Method: process
 * will be called to pass the received MHAS frame to the decoder.
 */
DECODER_FUNC(void, process, jobject inputBuffer, jint inputLength,
             jlong timestampUs) {
  DECODER_CONTEXT *ctx = getContext(env, obj);

  // get memory pointer to the buffer of the corresponding JAVA input parameter
  auto *inData = (const uint8_t *)env->GetDirectBufferAddress(inputBuffer);
  auto inDataLen = (uint32_t)inputLength;
  auto ptsIn = (uint64_t)timestampUs;

  MPEGH_DECODER_ERROR result =
      mpeghdecoder_process(ctx->handle, inData, inDataLen, ptsIn * 1000);
  if (result != MPEGH_DEC_OK) {
    LOGW("Unable to feed new data with return value = %d", result);
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "Unable to feed new data!");
  }
}

/*
 * Method: getSamples
 * will be called to receive the decoded PCM.
 */
DECODER_FUNC(jint, getSamples, jobject buffer, jint writePos) {
  DECODER_CONTEXT *ctx = getContext(env, obj);

  // get memory pointer to the buffer of the corresponding JAVA input parameter
  auto *outData = (uint8_t *)env->GetDirectBufferAddress(buffer);
  if (outData == nullptr) {
    LOGE("not possible to get direct byte buffer!");
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "not possible to get direct byte buffer!");
    return 0;
  }

  unsigned int outNumSamples = 0;
  MPEGH_DECODER_OUTPUT_INFO outInfo;

  MPEGH_DECODER_ERROR result = mpeghdecoder_getSamples(
      ctx->handle, ctx->samples, MAX_OUTBUF_SIZE_SAMPLES, &outInfo);

  if (result == MPEGH_DEC_OK) {
    outNumSamples = outInfo.numSamplesPerChannel;
    if (outNumSamples > 0) {
      for (int i = 0; i < outNumSamples * outInfo.numChannels; i++) {
        ctx->samples[i] = ctx->samples[i] >> 16;
        outData[writePos + i * 2 + 0] = (ctx->samples[i] >> 8) & 0xFF;
        outData[writePos + i * 2 + 1] = (ctx->samples[i]) & 0xFF;
      }
    }
    ctx->outSampleRate = outInfo.sampleRate;
    ctx->outNumChannels = outInfo.numChannels;
    ctx->outPts = outInfo.pts / 1000;
  } else {
    ctx->outSampleRate = -1;
    ctx->outNumChannels = -1;
    ctx->outPts = -1;
  }

  return outNumSamples * ctx->outNumChannels * BYTES_PER_SAMPLE;
}

/*
 * Method: flushAndGet
 * will be called to force the decoder to flush the internal PCM buffer and
 * write available output samples into a sample queue.
 */
DECODER_FUNC(void, flushAndGet) {
  DECODER_CONTEXT *ctx = getContext(env, obj);

  MPEGH_DECODER_ERROR result = mpeghdecoder_flushAndGet(ctx->handle);
  if (result != MPEGH_DEC_OK) {
    LOGE("Unable to flush data with return value = %d", result);
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "Unable to flush data!");
  }
}

/*
 * Method: getNumChannels
 * will be called to receive the number of output channels.
 */
DECODER_FUNC(jint, getNumChannels) {
  DECODER_CONTEXT *ctx = getContext(env, obj);
  return ctx->outNumChannels;
}

/*
 * Method: getSamplerate
 * will be called to receive the output sample rate.
 */
DECODER_FUNC(jint, getSamplerate) {
  // get wrapper context from JNI env
  DECODER_CONTEXT *ctx = getContext(env, obj);
  return ctx->outSampleRate;
}

/*
 * Method: getPts
 * will be called to receive the output PTS.
 */
DECODER_FUNC(jlong, getPts) {
  DECODER_CONTEXT *ctx = getContext(env, obj);
  return ctx->outPts;
}

/*
 * Method: flush
 * will be called to force the decoder to flush the internal PCM buffer.
 */
DECODER_FUNC(void, flush) {
  DECODER_CONTEXT *ctx = getContext(env, obj);

  MPEGH_DECODER_ERROR result = mpeghdecoder_flush(ctx->handle);
  if (result != MPEGH_DEC_OK) {
    LOGE("Unable to flush data with return value = %d", result);
    jclass atscExCls = env->FindClass(EXCEPTION_PATH);
    env->ThrowNew(atscExCls, "Unable to flush data!");
  }
}
