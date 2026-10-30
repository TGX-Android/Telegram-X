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

// Opens samples and draws all of their frames at the sizes at which the app draws them, so that
// tlottie built with instrumentation writes the profile of drawing, see GenerateTlottieProfileTask.
// Usage: profile <folder with emoji, sticker and effect folders of .tgs files>

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <zlib.h>
#include <tlottie.h>

static double now (void) {
  struct timespec time;
  clock_gettime(CLOCK_MONOTONIC, &time);
  return (double) time.tv_sec * 1e3 + (double) time.tv_nsec / 1e6;
}

static char *readGzip (const char *path, size_t *length) {
  gzFile file = gzopen(path, "rb");
  if (file == NULL) {
    return NULL;
  }
  size_t capacity = 1 << 20, size = 0;
  char *data = malloc(capacity);
  int read;
  while ((read = gzread(file, data + size, (unsigned) (capacity - size))) > 0) {
    size += (size_t) read;
    if (size == capacity) {
      capacity *= 2;
      data = realloc(data, capacity);
    }
  }
  gzclose(file);
  *length = size;
  return data;
}

int main (int argc, char **argv) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <samples>\n", argv[0]);
    return 1;
  }
  // Sizes of GifActor on a screen of density 2.625: custom emoji in text and in the panel,
  // stickers in the panel and in messages, full-screen effects
  struct {
    const char *kind;
    uint32_t sizes[2];
    int sizeCount;
  } kinds[] = {
    {"emoji", {53, 105}, 2},
    {"sticker", {160, 384}, 2},
    {"effect", {384, 0}, 1}
  };
  uint32_t *pixels = malloc(384 * 384 * sizeof(uint32_t));
  long animationCount = 0, frameCount = 0;
  double openMs = 0, renderMs = 0;
  for (size_t k = 0; k < sizeof(kinds) / sizeof(kinds[0]); k++) {
    char directory[512];
    snprintf(directory, sizeof(directory), "%s/%s", argv[1], kinds[k].kind);
    DIR *dir = opendir(directory);
    if (dir == NULL) {
      continue;
    }
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
      size_t nameLength = strlen(entry->d_name);
      if (nameLength < 4 || strcmp(entry->d_name + nameLength - 4, ".tgs") != 0) {
        continue;
      }
      char path[1024];
      snprintf(path, sizeof(path), "%s/%s", directory, entry->d_name);
      size_t length;
      char *json = readGzip(path, &length);
      if (json == NULL) {
        continue;
      }
      double openStart = now();
      TLottieInstance *animation = tlottie_new_with_options(
        (const uint8_t *) json, length, TLOTTIE_FITZ_NONE, NULL, 0, NULL, 0, TLOTTIE_CHANNEL_RGBA
      );
      openMs += now() - openStart;
      free(json);
      if (animation == NULL) {
        continue;
      }
      animationCount++;
      uint32_t count = tlottie_frame_count(animation);
      for (int s = 0; s < kinds[k].sizeCount; s++) {
        uint32_t size = kinds[k].sizes[s];
        double renderStart = now();
        for (uint32_t frame = 0; frame < count; frame++) {
          tlottie_render(animation, (float) frame, size, size, pixels, (size_t) size * size, 1);
          frameCount++;
        }
        renderMs += now() - renderStart;
      }
      tlottie_drop(animation);
    }
    closedir(dir);
  }
  free(pixels);
  printf("%ld animations, %ld frames, open %.1f ms, render %.1f ms\n", animationCount, frameCount,
         openMs, renderMs);
  return 0;
}
