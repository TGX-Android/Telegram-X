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
 * File created on 16/06/2017
 */

#include <jni_utils.h>
#include "bridge.h"

#include <libtgvoip/VoIPController.h>
#include <libtgvoip/client/android/tg_voip_jni.h>

#ifndef DISABLE_TGCALLS
#include <tgcalls/legacy/InstanceImplLegacy.h>
#include <tgcalls/InstanceImpl.h>
//#include <tgcalls/VideoCaptureInterface.h>
#include <tgcalls/v2/InstanceV2Impl.h>
#include <tgcalls/v2_4_0_0/InstanceV2_4_0_0Impl.h>
#include <tgcalls/v2/InstanceV2ReferenceImpl.h>

#else

#ifdef HAVE_TDLIB_CRYPTO
#include <tdjni/telegram_crypto.h>
#else

extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/modes.h>
#endif
#include <openssl/rand.h>
}

void telegram_aes_ige_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void telegram_aes_ige_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

void telegram_rand_bytes(uint8_t* buffer, size_t len){
  RAND_bytes(buffer, len);
}

void telegram_sha1(uint8_t* msg, size_t len, uint8_t* output){
  SHA1(msg, len, output);
}

void telegram_sha256(uint8_t* msg, size_t len, uint8_t* output){
  SHA256(msg, len, output);
}

void telegram_aes_ctr_encrypt(uint8_t* inout, size_t length, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
#ifdef OPENSSL_IS_BORINGSSL
  AES_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num);
#else
  CRYPTO_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num, (block128_f) AES_encrypt);
#endif
}

void telegram_aes_cbc_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void telegram_aes_cbc_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}
#endif
#endif

namespace tgcalls {
  bool isInitialized = false;

  jclass javaTgCallsController = nullptr;
  jclass javaNetworkStats = nullptr;
  jclass javaCallConfiguration = nullptr;
  jclass javaCallOptions = nullptr;

  bool initialize (JNIEnv *env) {
    if (isInitialized) {
      return true;
    }

    // find Java classes
#define INIT(var_name, path) var_name = jni_class::get(env, path);\
    if ((var_name) == nullptr) {\
      jni::throw_new(env, #var_name" not found by path: " path, jni_class::IllegalStateException(env)); \
      return false;\
    }
    INIT(javaTgCallsController, "org/thunderdog/challegram/voip/TgCallsController")
    INIT(javaNetworkStats, "org/thunderdog/challegram/voip/NetworkStats")
    INIT(javaCallConfiguration, "org/thunderdog/challegram/voip/CallConfiguration")
    INIT(javaCallOptions, "org/thunderdog/challegram/voip/CallOptions")
#undef INIT

#ifndef DISABLE_TGCALLS
    // register tgcalls implementations
#define REGISTER(impl) if (!Register<impl>()) { \
      jni::throw_new(env, #impl" could not be registered", jni_class::IllegalStateException(env)); \
    }
    // "2.4.4"
    REGISTER(InstanceImplLegacy)
    // "2.7.7", "5.0.0"
    REGISTER(InstanceImpl)
    // "6.0.0"
    REGISTER(InstanceV2_4_0_0Impl)
    // "7.0.0", "8.0.0", "9.0.0"
    REGISTER(InstanceV2Impl)
    // "10.0.0", "11.0.0"
    REGISTER(InstanceV2ReferenceImpl)
#undef REGISTER
#endif

    isInitialized = true;
    return true;
  }
}

JNI_FUNC(jlong, newTgCallsInstance,
         jstring jVersion,
         jobject jConfiguration,
         jobject jOptions) {

  if (!tgcalls::initialize(env)) {
    return 0;
  }

  std::string version = jni::from_jstring(env, jVersion);
  jni::Object configuration (env, jConfiguration, tgcalls::javaCallConfiguration);
  jni::Object options (env, jOptions, tgcalls::javaCallOptions);

  // TODO create tgcalls instance

  return 0;
}

JNI_FUNC(jobjectArray, getTgCallsVersions) {
#ifndef DISABLE_TGCALLS
  tgcalls::initialize(env);
  std::vector<std::string> versions (tgcalls::Meta::Versions());

  jobjectArray jArray = env->NewObjectArray(
    (jsize) versions.size(),
    jni_class::String(env),
    nullptr
  );

  jsize index = 0;
  for (const auto &version : versions) {
    jstring jVersion = jni::to_jstring(env, version);
    env->SetObjectArrayElement(jArray, index, jVersion);
    env->DeleteLocalRef(jVersion);
    index++;
  }

  return jArray;
#else
  return env->NewObjectArray(0, jni_class::String(env), nullptr);
#endif
}

// JNI initialization

extern "C" {
int voipOnJNILoad(JavaVM *vm, JNIEnv *env) {
#ifdef DISABLE_TGCALLS
  tgvoip::VoIPController::crypto.sha1 = &telegram_sha1;
  tgvoip::VoIPController::crypto.sha256 = &telegram_sha256;
  tgvoip::VoIPController::crypto.rand_bytes = &telegram_rand_bytes;
  tgvoip::VoIPController::crypto.aes_ige_encrypt = &telegram_aes_ige_encrypt;
  tgvoip::VoIPController::crypto.aes_ige_decrypt = &telegram_aes_ige_decrypt;
  tgvoip::VoIPController::crypto.aes_ctr_encrypt = &telegram_aes_ctr_encrypt;
#ifndef HAVE_TDLIB_CRYPTO
  tgvoip::VoIPController::crypto.aes_cbc_decrypt = &telegram_aes_cbc_decrypt;
  tgvoip::VoIPController::crypto.aes_cbc_encrypt = &telegram_aes_cbc_encrypt;
#endif
#endif
  tgvoipRegisterNatives(env);
  return 0;
}
}