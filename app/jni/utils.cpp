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

#include "utils.h"
#include <string>
#include <jni_utils.h>
#include <unistd.h>
#include <bridge.h>
#include <vector>

#if defined(__ARM_FEATURE_PAC_DEFAULT) && defined(__ARM_FEATURE_BTI_DEFAULT)
#include <asm/hwcap.h>
#include <cinttypes>
#include <cstdio>
#include <cstring>
#include <sys/auxv.h>
#endif

int jni_init (JavaVM *vm, JNIEnv *env) {
  return jni::validate_environment(env) ? 0 : -1;
}

jclass jni_find_class (JNIEnv *env, const char *class_path, int need_cache) {
  return jni_class::get(env, std::string(class_path));
}

void hexdump (void *data, int size) {
  unsigned char *buf = (unsigned char*) data;
  int i, j;
  for (i = 0; i < size; i += 16) {
    printf("%06x: ", i);
    for (j=0; j<16; j++) {
      if (i + j < size) {
        printf("%02x ", buf[i + j]);
      } else {
        printf("   ");
      }
    }
    printf(" ");
    for (j = 0; j < 16; j++) {
      if (i + j < size) {
        printf("%c", isprint(buf[i + j]) ? buf[i + j] : '.');
      }
    }
    printf("\n");
  }
}


JNI_FUNC(jstring, readlink, jstring jPath) {
  std::string path = jni::from_jstring(env, jPath);

  std::vector<char> buffer(path.size() > 256 ? path.size() : 256);
  ssize_t len;

  do {
    buffer.resize(buffer.size() + 128);
    len = readlink(path.c_str(), &(buffer[0]), buffer.size());
  } while (buffer.size() == len);

  if (len > 0) {
    buffer[len] = '\0';
    return jni::to_jstring(env, (std::string(&(buffer[0]))));
  }

  return nullptr;
}

#if defined(__ARM_FEATURE_PAC_DEFAULT) && defined(__ARM_FEATURE_BTI_DEFAULT)
static constexpr jint HARDENING_PAC = 1;
static constexpr jint HARDENING_BTI = 1 << 1;

static bool is_code_guarded_by_bti () {
  FILE *smaps = fopen("/proc/self/smaps", "re");
  if (smaps == nullptr) {
    return false;
  }
  auto code = reinterpret_cast<uintptr_t>(&is_code_guarded_by_bti);
  bool is_own_code = false;
  bool is_guarded = false;
  char line[512];
  while (fgets(line, sizeof(line), smaps) != nullptr) {
    uintptr_t start, end;
    if (sscanf(line, "%" SCNxPTR "-%" SCNxPTR " ", &start, &end) == 2) {
      is_own_code = code >= start && code < end;
    } else if (is_own_code && strncmp(line, "VmFlags:", 8) == 0) {
      is_guarded = strstr(line, " bt") != nullptr;
      break;
    }
  }
  fclose(smaps);
  return is_guarded;
}

static jint get_hardware_hardening () {
  jint hardening = 0;
  if ((getauxval(AT_HWCAP) & HWCAP_PACA) != 0) {
    hardening |= HARDENING_PAC;
  }
  if ((getauxval(AT_HWCAP2) & HWCAP2_BTI) != 0 && is_code_guarded_by_bti()) {
    hardening |= HARDENING_BTI;
  }
  return hardening;
}
#else
static constexpr jint HARDENING_NOT_BUILT = -1;

static jint get_hardware_hardening () {
  return HARDENING_NOT_BUILT;
}
#endif

JNI_FUNC(jint, getHardwareHardening) {
  static const jint hardening = get_hardware_hardening();
  return hardening;
}
