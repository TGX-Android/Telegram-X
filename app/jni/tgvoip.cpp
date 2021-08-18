//
// Created by default on 6/16/17.
//

#include <libtgvoip/VoIPController.h>
#include <tdjni/telegram_crypto.h>
#include <libtgvoip/client/android/tg_voip_jni.h>

extern "C" {
int voipOnJNILoad(JavaVM *vm, JNIEnv *env) {
  tgvoip::VoIPController::crypto.sha1 = &telegram_sha1;
  tgvoip::VoIPController::crypto.sha256 = &telegram_sha256;
  tgvoip::VoIPController::crypto.rand_bytes = &telegram_rand_bytes;
  tgvoip::VoIPController::crypto.aes_ige_encrypt = &telegram_aes_ige_encrypt;
  tgvoip::VoIPController::crypto.aes_ige_decrypt = &telegram_aes_ige_decrypt;
  tgvoip::VoIPController::crypto.aes_ctr_encrypt = &telegram_aes_ctr_encrypt;
  tgvoipRegisterNatives(env);
  return 0;
}
}