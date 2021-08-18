#include <jni.h>
#include <math.h>
#include <stdio.h>
#include "utils.h"

JNIEXPORT float Java_org_thunderdog_challegram_N_iimg (JNIEnv *env, jclass class, jfloat input) {
  return 1.0f - powf(1.0f - input, 1.56f);
}