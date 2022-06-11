/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <math.h>
#include <stdio.h>
#include "utils.h"

JNIEXPORT float Java_org_thunderdog_challegram_N_iimg (JNIEnv *env, jclass class, jfloat input) {
  return 1.0f - powf(1.0f - input, 1.56f);
}