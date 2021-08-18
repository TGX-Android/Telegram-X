//
// Created by Vyacheslav Krylov on 18/01/21.
//

#include <jni_utils.h>
#include <log.h>

#include "bridge.h"

void onFatalError (JNIEnv *env, const std::string &message, int cause) {
  loge(TAG_NDK, "[FATAL ERROR] %s, cause:%d", message.c_str(), cause);
  jclass cls = jni_class::get(env, "org/drinkmore/Tracer");
  jmethodID on_fatal_error_method = env->GetStaticMethodID(cls, "onFatalError", "(Ljava/lang/String;I)V");
  if (env && on_fatal_error_method) {
    jstring error_str = jni::to_jstring(env, message.c_str());
    env->CallStaticVoidMethod(cls, on_fatal_error_method, error_str, cause);
    if (error_str) {
      env->DeleteLocalRef(error_str);
    }
  }
}