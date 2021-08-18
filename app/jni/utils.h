#ifndef log_h
#define log_h

#include <log.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

int jni_init (JavaVM *vm, JNIEnv *env);
jclass jni_find_class (JNIEnv *env, const char *class_path, int need_cache);
void hexdump (void *data, int size);

#ifdef __cplusplus
}
#endif

#endif
