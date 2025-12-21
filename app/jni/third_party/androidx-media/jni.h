#ifndef TGX_JNI_H
#define TGX_JNI_H

#include <jni.h>

jint ffmpeg_jni_OnLoad(JavaVM* vm, void* reserved);
jint opus_jni_OnLoad(JavaVM* vm, void* reserved);
jint vpx_jni_OnLoad(JavaVM* vm, void* reserved);


#endif //TGX_JNI_H
