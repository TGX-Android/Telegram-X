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
 */
@file:JvmName("FrameworkFingerprint")

package tgx.flavor

import org.thunderdog.challegram.core.BiometricAuthentication

fun canAuthenticate(status: Int): Int = status

fun authenticator(
  strong: Boolean,
  callback: BiometricAuthentication.Callback,
  onComplete: Runnable
): BiometricAuthentication.Authenticator? =
  null
