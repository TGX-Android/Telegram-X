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
 *
 * File created on 09/07/2017
 */

#ifndef CHALLEGRAM_LOG_H
#define CHALLEGRAM_LOG_H

#include <stdlib.h>
#include <stdarg.h>

#define LOG_TAG "tgx"

#define LOG_LEVEL_ASSERT 0
#define LOG_LEVEL_ERROR 1
#define LOG_LEVEL_WARNING 2
#define LOG_LEVEL_INFO 3
#define LOG_LEVEL_DEBUG 4
#define LOG_LEVEL_VERBOSE 5

// Must be the same as in Log.java
#define TAG_NETWORK_STATE (1)
#define TAG_VOIP (1 << 1)
#define TAG_FCM (1 << 2)
#define TAG_MESSAGES_LOADER (1 << 3)
#define TAG_INTRO (1 << 4)
#define TAG_IMAGE_LOADER (1 << 5)
#define TAG_TEXT_SPEED (1 << 6)
#define TAG_YOUTUBE (1 << 7)
#define TAG_CRASH (1 << 8)
#define TAG_GIF_LOADER (1 << 9)
#define TAG_CAMERA (1 << 10)
#define TAG_VOICE (1 << 11)
#define TAG_EMOJI (1 << 12)
#define TAG_LUX (1 << 13)
#define TAG_VIDEO (1 << 14)
#define TAG_ROUND (1 << 15)
#define TAG_COMPRESS (1 << 16)
#define TAG_CONTACT (1 << 17)
#define TAG_PAINT (1 << 18)
#define TAG_PLAYER (1 << 19)
#define TAG_NDK (1 << 20)
#define TAG_ACCOUNTS (1 << 21)

#define TAG_TDLIB_FILES (1 << 29)
#define TAG_TDLIB_OPTIONS (1 << 30)

#ifdef __cplusplus
extern "C" {
#endif
// TODO __attribute__((format(printf, format_pos, arg_pos)))
void _logf (int level, int tag, const char *fmt, ...);
void _vlogf (int level, int tag, const char *fmt, va_list args);
int check_log_permission (int tag, int level);
#ifdef __cplusplus
}
#endif

#define LOG_LEVEL LOG_LEVEL_VERBOSE

#if LOG_LEVEL >= LOG_LEVEL_ASSERT
#define loga(...) _logf(LOG_LEVEL_ASSERT, __VA_ARGS__)
#else
#define loga(...) ((void) 0)
#endif

#if LOG_LEVEL >= LOG_LEVEL_ERROR
#define loge(...) _logf(LOG_LEVEL_ERROR, __VA_ARGS__)
#else
#define loge(...) ((void) 0)
#endif

#if LOG_LEVEL >= LOG_LEVEL_WARNING
#define logw(...) _logf(LOG_LEVEL_WARNING, __VA_ARGS__)
#else
#define logw(...) ((void) 0)
#endif

#if LOG_LEVEL >= LOG_LEVEL_INFO
#define logi(...) _logf(LOG_LEVEL_INFO, __VA_ARGS__)
#else
#define logi(...) ((void) 0)
#endif

#if LOG_LEVEL >= LOG_LEVEL_DEBUG
#define logd(...) _logf(LOG_LEVEL_DEBUG, __VA_ARGS__)
#else
#define logd(...) ((void) 0)
#endif

#if LOG_LEVEL >= LOG_LEVEL_VERBOSE
#define logv(...) _logf(LOG_LEVEL_VERBOSE, __VA_ARGS__)
#else
#define logv(...) ((void) 0)
#endif

#endif //CHALLEGRAM_LOG_H
