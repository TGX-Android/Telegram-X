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