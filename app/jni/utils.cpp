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

int jni_init (JavaVM *vm, JNIEnv *env) {
  return jni::validate_environment(env) ? 0 : -1;
}

jclass jni_find_class (JNIEnv *env, const char *class_path, int need_cache) {
  return jni_class::get(env, std::string(class_path));
}

void hexdump (void *data, int size) {
  unsigned char *buf = (unsigned char*) data;
  char output_buf[256];
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