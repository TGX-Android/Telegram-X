#ifndef image_h
#define image_h

#include <jni.h>

#ifndef NO_WEBP
jint imageOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env);
#endif

#endif
