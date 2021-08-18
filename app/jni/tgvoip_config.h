//
// Created by default on 7/18/18.
//

#ifndef CHALLEGRAM_TGVOIP_CONFIG_H
#define CHALLEGRAM_TGVOIP_CONFIG_H

#define TGVOIP_FUNC(RETURN_TYPE, NAME, ...)          \
  extern "C" {                                       \
  JNIEXPORT RETURN_TYPE                              \
      Java_org_thunderdog_challegram_voip_##NAME(       \
          JNIEnv *env, ##__VA_ARGS__);               \
  }                                                  \
  JNIEXPORT RETURN_TYPE                              \
      Java_org_thunderdog_challegram_voip_##NAME(       \
          JNIEnv *env, ##__VA_ARGS__)

#endif //CHALLEGRAM_TGVOIP_CONFIG_H
