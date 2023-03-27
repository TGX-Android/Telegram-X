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

#include <libtgvoip/VoIPController.h>
#include <libtgvoip/client/android/tg_voip_jni.h>

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

extern "C" {
int voipOnJNILoad(JavaVM *vm, JNIEnv *env) {
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
  tgvoipRegisterNatives(env);
  return 0;
}
}