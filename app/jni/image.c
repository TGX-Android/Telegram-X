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

#include <jni.h>
#include <stdio.h>
#include <setjmp.h>
#include <android/bitmap.h>
#include <string.h>
#ifndef NO_WEBP
#include <webp/decode.h>
#endif

#include "utils.h"
#include "image.h"

#include <math.h>

#ifndef NO_WEBP
jclass jclass_NullPointerException;
jclass jclass_RuntimeException;

jclass jclass_Options;
jfieldID jclass_Options_inJustDecodeBounds;
jfieldID jclass_Options_outHeight;
jfieldID jclass_Options_outWidth;

jclass jclass_Bitmap;
jmethodID jclass_Bitmap_createBitmap;
jclass jclass_Config;
jfieldID jclass_Config_ARGB_8888;

jint imageOnJNILoad (JavaVM *vm, void *reserved, JNIEnv *env) {
  jclass_NullPointerException = jni_find_class(env, "java/lang/NullPointerException", 1);
  if (jclass_NullPointerException == 0) {
    return -1;
  }
  jclass_RuntimeException = jni_find_class(env, "java/lang/RuntimeException", 1);
  if (jclass_RuntimeException == 0) {
    return -1;
  }
  
  jclass_Options = jni_find_class(env, "android/graphics/BitmapFactory$Options", 0);
  if (jclass_Options == 0) {
    return -1;
  }
  jclass_Options_inJustDecodeBounds = (*env)->GetFieldID(env, jclass_Options, "inJustDecodeBounds", "Z");
  if (jclass_Options_inJustDecodeBounds == 0) {
    return -1;
  }
  jclass_Options_outHeight = (*env)->GetFieldID(env, jclass_Options, "outHeight", "I");
  if (jclass_Options_outHeight == 0) {
    return -1;
  }
  jclass_Options_outWidth = (*env)->GetFieldID(env, jclass_Options, "outWidth", "I");
  if (jclass_Options_outWidth == 0) {
    return -1;
  }
  
  jclass_Bitmap = jni_find_class(env, "android/graphics/Bitmap", 0);
  if (jclass_Bitmap == 0) {
    return -1;
  }
  jclass_Bitmap_createBitmap = (*env)->GetStaticMethodID(env, jclass_Bitmap, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
  if (jclass_Bitmap_createBitmap == 0) {
    return -1;
  }
  
  jclass_Config = jni_find_class(env, "android/graphics/Bitmap$Config", 0);
  if (jclass_Config == 0) {
    return -1;
  }
  jclass_Config_ARGB_8888 = (*env)->GetStaticFieldID(env, jclass_Config, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
  if (jclass_Config_ARGB_8888 == 0) {
    return -1;
  }
  
  return JNI_VERSION_1_6;
}
#endif

static inline uint64_t get_colors (const uint8_t *p) {
    return p[0] + (p[1] << 16) + ((uint64_t)p[2] << 32);
}

#define BLUR_SIZE_LIMIT (160 * 160)

static void fastBlurMore(int imageWidth, int imageHeight, int imageStride, void *pixels, int radius) {
    uint8_t *pix = (uint8_t *)pixels;
    const int w = imageWidth;
    const int h = imageHeight;
    const int stride = imageStride;
    const int r1 = radius + 1;
    const int div = radius * 2 + 1;

    if (radius > 15 || div >= w || div >= h || w * h > BLUR_SIZE_LIMIT || imageStride > imageWidth * 4) {
        return;
    }
    
    uint64_t *rgb = malloc(imageWidth * imageHeight * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }
    
    int x, y, i;
    
    int yw = 0;
    const int we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = get_colors (&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);
        
        for (i = 1; i <= radius; i++) {
            uint64_t cur = get_colors (&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }
        
        x = 0;
        
        #define update(start, middle, end) \
            rgb[y * w + x] = (rgbsum >> 6) & 0x00FF00FF00FF00FF; \
            rgballsum += get_colors (&pix[yw + (start) * 4]) - 2 * get_colors (&pix[yw + (middle) * 4]) + get_colors (&pix[yw + (end) * 4]); \
            rgbsum += rgballsum; \
            x++; \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
    #undef update
        
        yw += stride;
    }
    
    const int he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }
        
        y = 0;
        int yi = x * 4;
        
    #define update(start, middle, end) \
            int64_t res = rgbsum >> 6; \
            pix[yi] = res; \
            pix[yi + 1] = res >> 16; \
            pix[yi + 2] = res >> 32; \
            rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
            rgbsum += rgballsum; \
            y++; \
            yi += stride;
        
        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
    #undef update
    }
}

static void fastBlur(int imageWidth, int imageHeight, int imageStride, void *pixels, int radius) {
    uint8_t *pix = (uint8_t *)pixels;
    if (pix == NULL) {
        return;
    }
    const int w = imageWidth;
    const int h = imageHeight;
    const int stride = imageStride;
    const int r1 = radius + 1;
    const int div = radius * 2 + 1;
    int shift;
    if (radius == 1) {
        shift = 2;
    } else if (radius == 3) {
        shift = 4;
    } else if (radius == 7) {
        shift = 6;
    } else if (radius == 15) {
        shift = 8;
    } else {
        return;
    }

    if (radius > 15 || div >= w || div >= h || w * h > BLUR_SIZE_LIMIT || imageStride > imageWidth * 4) {
        return;
    }
    
    uint64_t *rgb = malloc(imageWidth * imageHeight * sizeof(uint64_t));
    if (rgb == NULL) {
        return;
    }
    
    int x, y, i;
    
    int yw = 0;
    const int we = w - r1;
    for (y = 0; y < h; y++) {
        uint64_t cur = get_colors (&pix[yw]);
        uint64_t rgballsum = -radius * cur;
        uint64_t rgbsum = cur * ((r1 * (r1 + 1)) >> 1);
        
        for (i = 1; i <= radius; i++) {
            uint64_t cur = get_colors (&pix[yw + i * 4]);
            rgbsum += cur * (r1 - i);
            rgballsum += cur;
        }
        
        x = 0;
        
        #define update(start, middle, end)  \
                rgb[y * w + x] = (rgbsum >> shift) & 0x00FF00FF00FF00FFLL; \
                rgballsum += get_colors (&pix[yw + (start) * 4]) - 2 * get_colors (&pix[yw + (middle) * 4]) + get_colors (&pix[yw + (end) * 4]); \
                rgbsum += rgballsum;        \
                x++;                        \

        while (x < r1) {
            update (0, x, x + r1);
        }
        while (x < we) {
            update (x - r1, x, x + r1);
        }
        while (x < w) {
            update (x - r1, x, w - 1);
        }
        
        #undef update
        
        yw += stride;
    }
    
    const int he = h - r1;
    for (x = 0; x < w; x++) {
        uint64_t rgballsum = -radius * rgb[x];
        uint64_t rgbsum = rgb[x] * ((r1 * (r1 + 1)) >> 1);
        for (i = 1; i <= radius; i++) {
            rgbsum += rgb[i * w + x] * (r1 - i);
            rgballsum += rgb[i * w + x];
        }
        
        y = 0;
        int yi = x * 4;
        
        #define update(start, middle, end)  \
                int64_t res = rgbsum >> shift;   \
                pix[yi] = res;              \
                pix[yi + 1] = res >> 16;    \
                pix[yi + 2] = res >> 32;    \
                rgballsum += rgb[x + (start) * w] - 2 * rgb[x + (middle) * w] + rgb[x + (end) * w]; \
                rgbsum += rgballsum;        \
                y++;                        \
                yi += stride;
        
        while (y < r1) {
            update (0, y, y + r1);
        }
        while (y < he) {
            update (y - r1, y, y + r1);
        }
        while (y < h) {
            update (y - r1, y, h - 1);
        }
        #undef update
    }
    
    free(rgb);
}

JNIEXPORT jint Java_org_thunderdog_challegram_N_blurBitmap(JNIEnv *env, jclass class, jobject bitmap, int radius, int unpin, int forceLess) {
  if (!bitmap) {
    return -1;
  }
  
  AndroidBitmapInfo info;
  
  if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
    return -2;
  }

  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
    return -3;
  }
  
  if (!info.width || !info.height) {
    return -4;
  }

  if (!info.stride) {
    return -5;
  }
  
  void *pixels = 0;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
    return -6;
  }
  if (radius <= 3 || forceLess == 1) {
    fastBlur(info.width, info.height, info.stride, pixels, radius);
  } else {
    fastBlurMore(info.width, info.height, info.stride, pixels, radius);
  }
  if (unpin) {
    AndroidBitmap_unlockPixels(env, bitmap);
  }
  return 0;
}

const uint32_t PGPhotoEnhanceHistogramBins = 256;
const uint32_t PGPhotoEnhanceSegments = 4;

JNIEXPORT void Java_org_thunderdog_challegram_N_calcCDT(JNIEnv *env, jclass class, jobject hsvBuffer, int width, int height, jobject buffer) {
    float imageWidth = width;
    float imageHeight = height;
    float _clipLimit = 1.25f;

    uint32_t totalSegments = PGPhotoEnhanceSegments * PGPhotoEnhanceSegments;
    uint32_t tileArea = (uint32_t)(floorf(imageWidth / PGPhotoEnhanceSegments) * floorf(imageHeight / PGPhotoEnhanceSegments));
    uint32_t clipLimit = (uint32_t)fmaxf(1.0f, _clipLimit * tileArea / (float) PGPhotoEnhanceHistogramBins);
    float scale = 255.0f / (float) tileArea;


    unsigned char *bytes = (*env)->GetDirectBufferAddress(env, hsvBuffer);

    uint32_t **hist = calloc(totalSegments, sizeof(uint32_t *));
    uint32_t **cdfs = calloc(totalSegments, sizeof(uint32_t *));
    uint32_t *cdfsMin = calloc(totalSegments, sizeof(uint32_t));
    uint32_t *cdfsMax = calloc(totalSegments, sizeof(uint32_t));
    
    for (uint32_t a = 0; a < totalSegments; a++) {
        hist[a] = calloc(PGPhotoEnhanceHistogramBins, sizeof(uint32_t));
        cdfs[a] = calloc(PGPhotoEnhanceHistogramBins, sizeof(uint32_t));
    }
    
    float xMul = PGPhotoEnhanceSegments / imageWidth;
    float yMul = PGPhotoEnhanceSegments / imageHeight;
    
    for (uint32_t y = 0; y < imageHeight; y++) {
        uint32_t yOffset = y * width * 4;
        for (uint32_t x = 0; x < imageWidth; x++) {
            uint32_t index = x * 4 + yOffset;
            
            uint32_t tx = (uint32_t)(x * xMul);
            uint32_t ty = (uint32_t)(y * yMul);
            uint32_t t = ty * PGPhotoEnhanceSegments + tx;
            
            hist[t][bytes[index + 2]]++;
        }
    }
    
    for (uint32_t i = 0; i < totalSegments; i++) {
        if (clipLimit > 0) {
            uint32_t clipped = 0;
            for (uint32_t j = 0; j < PGPhotoEnhanceHistogramBins; ++j) {
                if (hist[i][j] > clipLimit) {
                    clipped += hist[i][j] - clipLimit;
                    hist[i][j] = clipLimit;
                }
            }
            
            uint32_t redistBatch = clipped / PGPhotoEnhanceHistogramBins;
            uint32_t residual = clipped - redistBatch * PGPhotoEnhanceHistogramBins;
            
            for (uint32_t j = 0; j < PGPhotoEnhanceHistogramBins; ++j) {
                hist[i][j] += redistBatch;
            }
            
            for (uint32_t j = 0; j < residual; ++j) {
                hist[i][j]++;
            }
        }
        memcpy(cdfs[i], hist[i], PGPhotoEnhanceHistogramBins * sizeof(uint32_t));
        
        uint32_t hMin = PGPhotoEnhanceHistogramBins - 1;
        for (uint32_t j = 0; j < hMin; ++j) {
            if (cdfs[j] != 0) {
                hMin = j;
            }
        }
        
        uint32_t cdf = 0;
        for (uint32_t j = hMin; j < PGPhotoEnhanceHistogramBins; ++j) {
            cdf += cdfs[i][j];
            cdfs[i][j] = (uint8_t) fminf(255.0f, (float) cdf * scale);
        }
        
        cdfsMin[i] = cdfs[i][hMin];
        cdfsMax[i] = cdfs[i][PGPhotoEnhanceHistogramBins - 1];
    }
    
    // uint32_t resultSize = 4 * PGPhotoEnhanceHistogramBins * totalSegments;
    uint32_t resultBytesPerRow = 4 * PGPhotoEnhanceHistogramBins;
    
    unsigned char *result = (*env)->GetDirectBufferAddress(env, buffer);
    for (uint32_t tile = 0; tile < totalSegments; tile++) {
        uint32_t yOffset = tile * resultBytesPerRow;
        for (uint32_t i = 0; i < PGPhotoEnhanceHistogramBins; i++) {
            uint32_t index = i * 4 + yOffset;
            result[index] = (uint8_t)cdfs[tile][i];
            result[index + 1] = (uint8_t)cdfsMin[tile];
            result[index + 2] = (uint8_t)cdfsMax[tile];
            result[index + 3] = 255;
        }
    }

    for (uint32_t a = 0; a < totalSegments; a++) {
        free(hist[a]);
        free(cdfs[a]);
    }
    free(hist);
    free(cdfs);
    free(cdfsMax);
    free(cdfsMin);
}

JNIEXPORT int Java_org_thunderdog_challegram_N_pinBitmap (JNIEnv *env, jclass class, jobject bitmap) {
  void *pixels;
  return AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 ? 1 : 0;
}

JNIEXPORT int Java_org_thunderdog_challegram_N_unpinBitmap (JNIEnv *env, jclass class, jobject bitmap) {
  return AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT jboolean Java_org_thunderdog_challegram_N_hasBuiltInWebpSupport (JNIEnv *env, jclass class) {
#ifdef NO_WEBP
  return 0;
#else
  return 1;
#endif
}

#define USE_CUSTOM_WEBP_DECODING

JNIEXPORT jboolean Java_org_thunderdog_challegram_N_loadWebpImage (JNIEnv *env, jclass class, jobject outputBitmap, jobject buffer, jint len, jobject options, jboolean unpin) {
#ifdef NO_WEBP
  return 0;
#else
  if (!buffer) {
    (*env)->ThrowNew(env, jclass_NullPointerException, "Input buffer can not be null");
    return 0;
  }

  jbyte *inputBuffer = (*env)->GetDirectBufferAddress(env, buffer);
  uint8_t *data = (uint8_t *) inputBuffer;
  size_t data_size = (size_t) len;

  int bitmapWidth = 0;
  int bitmapHeight = 0;
  if (!WebPGetInfo(data, data_size, &bitmapWidth, &bitmapHeight)) {
    (*env)->ThrowNew(env, jclass_RuntimeException, "Invalid WebP format");
    return 0;
  }

  if (options && (*env)->GetBooleanField(env, options, jclass_Options_inJustDecodeBounds) == JNI_TRUE) {
    (*env)->SetIntField(env, options, jclass_Options_outWidth, bitmapWidth);
    (*env)->SetIntField(env, options, jclass_Options_outHeight, bitmapHeight);
    return 1;
  }

  if (!outputBitmap) {
    (*env)->ThrowNew(env, jclass_NullPointerException, "output bitmap can not be null");
    return 0;
  }

  AndroidBitmapInfo bitmapInfo;
  if (AndroidBitmap_getInfo(env, outputBitmap, &bitmapInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
    (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to get Bitmap information");
    return 0;
  }

  void *bitmapPixels = NULL;
  if (AndroidBitmap_lockPixels(env, outputBitmap, &bitmapPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
    (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to lock Bitmap pixels");
    return 0;
  }

  uint8_t *rgba = (uint8_t *) bitmapPixels;
  size_t size = (size_t) (bitmapInfo.height * bitmapInfo.stride);
  int stride = bitmapInfo.stride;

#ifdef USE_CUSTOM_WEBP_DECODING
  WebPDecoderConfig config;
  memset(&config, 0, sizeof(config));
  // FIXME stack WebPInitDecoderConfig(&config);

  config.output.colorspace = MODE_rgbA;
  config.output.u.RGBA.rgba = rgba;
  config.output.u.RGBA.stride = stride;
  config.output.u.RGBA.size = size;
  config.output.is_external_memory = 1;

  config.options.no_fancy_upsampling = 1;
  config.options.bypass_filtering = 1;
  // config.options.use_threads = 1;

  if (WebPDecode(data, data_size, &config) != VP8_STATUS_OK) {
    AndroidBitmap_unlockPixels(env, outputBitmap);
    (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to decode webp image");
    return 0;
  }
#else
  if (!WebPDecodeRGBAInto(data, data_size, rgba, size, stride)) {
    AndroidBitmap_unlockPixels(env, outputBitmap);
    (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to decode webp image");
    return 0;
  }
#endif

  if (unpin && AndroidBitmap_unlockPixels(env, outputBitmap) != ANDROID_BITMAP_RESULT_SUCCESS) {
    (*env)->ThrowNew(env, jclass_RuntimeException, "Failed to unlock Bitmap pixels");
    return 0;
  }

  return 1;
#endif
}