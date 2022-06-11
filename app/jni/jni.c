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
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <inttypes.h>
#include <time.h>

#include "utils.h"

#ifndef NO_WEBP
#include "image.h"
#endif

int jni_init(JavaVM *vm, JNIEnv *env);
int voipOnJNILoad(JavaVM *vm, JNIEnv *env);

jint JNI_OnLoad (JavaVM *vm, void *reserved) {
  JNIEnv *env = 0;
  srand(time(NULL));

  if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }

  if (jni_init(vm, env) == -1) {
    return -1;
  }

#ifndef NO_WEBP
  if (imageOnJNILoad(vm, reserved, env) == -1) {
    return -1;
  }
#endif

  /*if (gifvideoOnJNILoad(vm, env) == -1) {
      return -1;
  }*/

  if (voipOnJNILoad(vm, env) == -1) {
      return -1;
  }

  return JNI_VERSION_1_6;
}

void JNI_OnUnload (JavaVM *vm, void *reserved) {

}