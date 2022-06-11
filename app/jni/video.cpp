/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include "videocontext.h"

// JNI wrappers
// void Java_org_thunderdog_challegram_N_nativeInit

extern "C" {
JNIEXPORT void Java_org_thunderdog_challegram_N_nativeVideoInit (JNIEnv *env, jclass clazz) {
  initVideoContextResources();
}

JNIEXPORT jlong Java_org_thunderdog_challegram_N_createVideoContext (JNIEnv *env, jclass clazz, jstring path, jint needAudio, jlong start, jlong end, jstring destinationPath) {
  const char *pathStr = env->GetStringUTFChars(path, 0);
  const char *destinationPathStr = env->GetStringUTFChars(destinationPath, 0);
  if (pathStr != NULL) {
    void *context = NULL;
    if (destinationPathStr != NULL) {
      context = createVideoContext(pathStr, needAudio, start, end, destinationPathStr);
      env->ReleaseStringUTFChars(destinationPath, destinationPathStr);
    }
    env->ReleaseStringUTFChars(path, pathStr);
    return (jlong) context;
  }
  return NULL;
}

JNIEXPORT jlong Java_org_thunderdog_challegram_N_prepareVideoConversion (JNIEnv *env, jclass clazz, jlong ptr) {
  if (ptr != NULL) {
    return prepareVideoContext((void *) ptr);
  }
  return -1;
}

JNIEXPORT jlong Java_org_thunderdog_challegram_N_processNextVideoPart (JNIEnv *env, jclass clazz, jlong ptr) {
  if (ptr != NULL) {
    return processNextPart((void *) ptr);
  }
  return -1;
}

JNIEXPORT void Java_org_thunderdog_challegram_N_destroyVideoContext (JNIEnv *env, jclass clazz, jlong ptr) {
  if (ptr != NULL) {
    destroyVideoContext((void *) ptr);
  }
}

}