//
// Created by Vyacheslav Krylov on 18/01/21.
//

#ifndef TGX_BRIDGE_H
#define TGX_BRIDGE_H

#define JNI_FUNC(RETURN_TYPE, NAME, ...)                              \
  extern "C" {                                                        \
  JNIEXPORT RETURN_TYPE                                               \
      Java_org_thunderdog_challegram_N_##NAME( \
          JNIEnv *env, jclass clazz, ##__VA_ARGS__);                  \
  }                                                                   \
  JNIEXPORT RETURN_TYPE                                               \
      Java_org_thunderdog_challegram_N_##NAME( \
          JNIEnv *env, jclass clazz, ##__VA_ARGS__)

void onFatalError (JNIEnv *env, const std::string &message, int cause);

#endif //TGX_BRIDGE_H
