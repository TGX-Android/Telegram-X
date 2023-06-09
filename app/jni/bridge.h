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
 *
 * File created on 18/01/2021
 */

#ifndef TGX_BRIDGE_H
#define TGX_BRIDGE_H

#define JNI_FUNC(RETURN_TYPE, NAME, ...)                              \
  extern "C" {                                                        \
  JNIEXPORT RETURN_TYPE JNICALL                                       \
      Java_org_thunderdog_challegram_N_##NAME(                        \
          JNIEnv *env, jclass clazz, ##__VA_ARGS__);                  \
  }                                                                   \
  JNIEXPORT RETURN_TYPE JNICALL                                       \
      Java_org_thunderdog_challegram_N_##NAME(                        \
          JNIEnv *env, jclass clazz, ##__VA_ARGS__)

#define JNI_OBJECT_FUNC(RETURN_TYPE, CLASS_NAME, NAME, ...)           \
  extern "C" {                                                        \
  JNIEXPORT RETURN_TYPE JNICALL                                       \
      Java_org_thunderdog_challegram_##CLASS_NAME##_##NAME(           \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__);                  \
  }                                                                   \
  JNIEXPORT RETURN_TYPE JNICALL                                       \
      Java_org_thunderdog_challegram_##CLASS_NAME##_##NAME(           \
          JNIEnv *env, jobject thiz, ##__VA_ARGS__)

void onFatalError (JNIEnv *env, const std::string &message, int cause);

#endif //TGX_BRIDGE_H
