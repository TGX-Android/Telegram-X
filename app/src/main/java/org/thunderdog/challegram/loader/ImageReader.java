/**
 * File created on 06/05/15 at 18:45
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.filegen.TdlibFileGenerationManager;
import org.thunderdog.challegram.support.Mp3Support;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import me.vkryl.core.StringUtils;

public class ImageReader {
  private static ImageReader instance;

  public static ImageReader instance () {
    if (instance == null) {
      instance = new ImageReader();
    }
    return instance;
  }

  private static byte[] bytesThumb;

  private final ImageReaderThread imageThread, videoThread, memThread;

  private ImageReader () {
    imageThread = new ImageReaderThread();
    videoThread = new ImageReaderThread();
    memThread = new ImageReaderThread();
  }

  public void post (Runnable r) {
    imageThread.post(r, 0);
  }

  public void postVideo (Runnable r) {
    videoThread.post(r, 0);
  }

  @SuppressWarnings (value={"SpellCheckingInspection", "deprecation"})
  public void readImage (final ImageActor actor, final ImageFile file, final String path, final Listener listener) {
    ImageReaderThread thread = file.getBytes() != null ? memThread : file instanceof ImageVideoThumbFile || file instanceof ImageMp3File || (file instanceof ImageGalleryFile && ((ImageGalleryFile) file).isVideo()) ? videoThread : imageThread;

    if (Thread.currentThread() != thread) {
      thread.readImage(actor, file, path, listener);
      return;
    }

    if (actor.isCancelled()) {
      return;
    }

    byte[] bytes = file.getBytes();
    if (bytes != null) {
      readBytes(file, bytes, listener);
      return;
    }

    if (file instanceof ImageApicFile) {
      readBytes(file, ((ImageApicFile) file).getApic().pictureData, listener);
      return;
    }

    if (file instanceof ImageMp3File) {
      readMp3AlbumCover((ImageMp3File) file, listener);
      return;
    }

    if (file instanceof ImageVideoThumbFile) {
      readVideoThumb((ImageVideoThumbFile) file, listener);
      return;
    }

    if (file instanceof ImageGalleryFile) {
      ImageGalleryFile galleryFile = (ImageGalleryFile) file;
      if (galleryFile.needThumb()) {
        readThumbImage(actor, (ImageGalleryFile) file, listener);
        return;
      }
    }

    Bitmap bitmap = readImage(file, path);
    listener.onImageLoaded(bitmap != null, bitmap);
  }

  private static Bitmap readImage (ImageFile file, String path) {
    boolean needSquare = file.needDecodeSquare();

    ImageFile exifFile;
    if (file instanceof ImageFilteredFile) {
      exifFile = ((ImageFilteredFile) file).getSourceFile();
    } else {
      exifFile = file;
    }
    int rotation = 0;
    String exifPath = exifFile.getFilePath();
    if (exifFile.isProbablyRotated() && !exifFile.isWebp()) {
      exifFile.setRotation(rotation = U.getRotation(exifPath));
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Config.MODERN_IMAGE_DECODER_ENABLED && rotation == 0 && !needSquare && !(Config.useBundledWebp() && file.isWebp())) {
      try {
        final boolean forceSw = file.isSwOnly();
        android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(new File(path));
        int[] outSize = new int[3];
        Bitmap bitmap = android.graphics.ImageDecoder.decodeBitmap(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
          @Override
          public void onHeaderDecoded (@NonNull android.graphics.ImageDecoder decoder, @NonNull android.graphics.ImageDecoder.ImageInfo info, @NonNull android.graphics.ImageDecoder.Source source) {
            if (Config.FORCE_SOFTWARE_IMAGE_DECODER) {
              decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
            }
            if (forceSw) {
              decoder.setMutableRequired(true);
            }
            int limitSize = file.isPrivate() ? 36 : file.getSize();
            int width = outSize[0] = info.getSize().getWidth();
            int height = outSize[1] = info.getSize().getHeight();
            if (limitSize != 0) {
              float ratio = Math.min((float) limitSize / (float) width, (float) limitSize / (float) height);
              if (ratio < 1f) {

                decoder.setTargetSize(width = (int) ((float) width * ratio), height = (int) ((float) height * ratio));
              }
            }
            if (needSquare && width != height) {
              if (width > height) {
                decoder.setCrop(new Rect(width / 2 - height / 2, 0, width / 2 + height / 2 + height % 2, height));
              } else {
                decoder.setCrop(new Rect(0, height / 2 - width / 2, width, height / 2 + width / 2 + width % 2));
              }
            }
            boolean needBlur = file.isPrivate() || (!file.isWebp() && file.shouldUseBlur() && (file.needBlur() || (outSize[0] < 100 && outSize[1] < 100)));
            if (needBlur) {
              outSize[2] = 1;
              decoder.setMutableRequired(true);
            }
          }
        });
        if (outSize[2] == 1) { // Can be blurred
          int res = N.blurBitmap(bitmap, file.isPrivate() ? 7 : file.getBlurRadius(), 0, file.isPrivate() ? 1 : 0);
          if (res != 0) {
            Log.i(Log.TAG_IMAGE_LOADER, "#%s: Couldn't blur bitmap, resultCode: %d", file.toString(), res);
          }
        }
        return bitmap;
      } catch (Throwable t) {
        Log.e("#%s: Can't decode bitmap using modern API, getting to fallback", t, file.toString());
      }
    }

    long ms = SystemClock.elapsedRealtime();

    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;

    if (!file.isWebp() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.useBundledWebp())) {
      opts.inJustDecodeBounds = true;
      decodeFile(path, opts);
      int limitSize = file.getSize(); // != 0 ? file.getSize() : ;
      if (limitSize != 0) {
        opts.inSampleSize = calculateInSampleSize(opts, limitSize, limitSize);
      }
    }

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      Log.v(Log.TAG_IMAGE_LOADER, "#%s: preparing to read image, sampleSize: %d width: %d height: %d", file.toString(), opts.inSampleSize, opts.outWidth, opts.outHeight);
    }

    opts.inJustDecodeBounds = false;
    opts.inDither = false;
    if (file.needHiRes()) {
      opts.inPreferQualityOverSpeed = true;
    }

    if (file.forceArgb8888() || file.needHiRes()|| file.isPrivate() || (file.shouldBeCached() && (file.needBlur() || (Math.max(opts.outWidth, opts.outHeight) < 100)))) {
      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
    } else {
      opts.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    if (file.shouldBeCached() && !file.isWebp() && !file.needBlur() && Config.PIN_BITMAP_ENABLED) {
      opts.inPurgeable = true;
    }

    File cacheFile = new File(path);

    Bitmap bitmap;

    try {
      if (file.isWebp() && Config.useBundledWebp()) {
        RandomAccessFile f = new RandomAccessFile(cacheFile, "r");
        ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFile.length());

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        N.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
        bitmap = Bitmap.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);
        N.loadWebpImage(bitmap, buffer, buffer.limit(), null, !opts.inPurgeable);

        f.close();
      } else {
        if (opts.inPurgeable) {
          RandomAccessFile f = null;
          for (int attempt = 0; attempt < 2; attempt++) { // fixme stupid fix for EACCESS on early applaunch requests
            try {
              f = new RandomAccessFile(cacheFile, "r");
              break;
            } catch (Throwable t) {
              if (attempt + 1 == 2) {
                Log.e(Log.TAG_IMAGE_LOADER, "#%s: Cannot access image file", t, file.toString());
              }
              try { Thread.sleep(100); } catch (Throwable ignored) { }
            }
          }
          if (f == null) {
            bitmap = null;
          } else {
            int len = (int) f.length();
            byte[] data = bytesThumb != null && bytesThumb.length >= len ? bytesThumb : null;
            if (data == null) {
              bytesThumb = data = new byte[len];
            }
            f.readFully(data, 0, len);
            bitmap = BitmapFactory.decodeByteArray(data, 0, len, opts);
            f.close();
          }
        } else {
          bitmap = null;

          if (needSquare && opts.outWidth != opts.outHeight && opts.outWidth > 0) {
            Rect rect = new Rect();
            if (opts.outWidth > opts.outHeight) {
              rect.top = 0; rect.bottom = opts.outHeight;
              rect.left = opts.outWidth / 2 - opts.outHeight / 2;
              rect.right = rect.left + opts.outHeight;
            } else {
              rect.left = 0; rect.right = opts.outWidth;
              rect.top = opts.outHeight / 2 - opts.outWidth / 2;
              rect.bottom = rect.top + opts.outWidth;
            }
            int oldSampleSize = opts.inSampleSize;
            try {
              if (opts.inSampleSize != 1 && file.getSize() != 0) {
                int sizeLimit = file.getSize();
                int minSide = Math.min(opts.outWidth, opts.outHeight);
                opts.inSampleSize = calculateInSampleSize(minSide, minSide, sizeLimit, sizeLimit);
              }
              bitmap = U.tryDecodeRegion(cacheFile.getPath(), rect, opts);
              /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rotation != 0) {
                bitmap = TdlibFileGenerationManager.rotateBitmap(bitmap, -rotation);
              }*/
            } catch (Throwable t) {
              Log.w(Log.TAG_IMAGE_LOADER, "Cannot decode region", t);
            }
            if (bitmap == null) {
              opts.inSampleSize = oldSampleSize;
            }
          }

          if (bitmap == null) {
            try (FileInputStream is = new FileInputStream(cacheFile)) {
              bitmap = BitmapFactory.decodeStream(is, null, opts);
            }
          }
        }
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_IMAGE_LOADER, "#%s: Cannot load bitmap, config: %s", t, file.toString(), opts.inPreferredConfig.toString());
      bitmap = null;
    }

    if (bitmap != null) {
      if (file.isPrivate()) {
        bitmap = resizeBitmap(bitmap, 36, 36, true);
        /*bitmap = resizeBitmap(bitmap, 48, 48, true);*/
      } else if (!file.needHiRes() && (file.needFitSize() || file.forceArgb8888()) && Math.max(bitmap.getWidth(), bitmap.getHeight()) > file.getSize() && file.getSize() != 0) {
        bitmap = resizeBitmap(bitmap, file.getSize(), file.getSize(), true);
      }
    }

    if (bitmap != null) {
      if (needSquare && bitmap.getWidth() != bitmap.getHeight()) {
        bitmap = TdlibFileGenerationManager.cropSquare(bitmap);
      }
      if (file.isPrivate() || (!file.isWebp() && file.shouldUseBlur() && (file.needBlur() || (bitmap.getWidth() < 100 && bitmap.getHeight() < 100)))) {
        int res = N.blurBitmap(bitmap, file.isPrivate() ? 7 : file.getBlurRadius(), opts.inPurgeable ? 0 : 1, file.isPrivate() ? 1 : 0);
        if (res != 0) {
          Log.i(Log.TAG_IMAGE_LOADER, "#%s: Couldn't blur bitmap, resultCode: %d", file.toString(), res);
        }
      } else if (opts.inPurgeable) {
        N.pinBitmapIfNeeded(bitmap);
      }
    }

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      long elapsed = SystemClock.elapsedRealtime() - ms;
      if (elapsed > 250) {
        Log.w(Log.TAG_IMAGE_LOADER, "#%s: Image load took %dms, width: %d height: %d", file.toString(), elapsed, bitmap != null ? bitmap.getWidth() : 0, bitmap != null ? bitmap.getHeight() : 0);
      }
    }

    return bitmap;
  }

  private void readMp3AlbumCover (ImageMp3File file, Listener listener) {
    String mp3Path = file.getPath();

    byte[] data = Mp3Support.readCover(mp3Path);

    if (data == null || data.length == 0) {
      listener.onImageLoaded(false, null);
      return;
    }

    readBytes(file, data, listener);
  }

  private void readBytes (ImageFile file, byte[] data, Listener listener) {
    Bitmap bitmap = readBytes(data, file.getSize(), !file.isWebp() && file.shouldUseBlur(), true);
    if (bitmap != null && file.needDecodeSquare() && bitmap.getWidth() != bitmap.getHeight()) {
      bitmap = TdlibFileGenerationManager.cropSquare(bitmap);
    }
    listener.onImageLoaded(bitmap != null, bitmap);
  }

  public static @Nullable Bitmap readBytes (byte[] data, int maxSize, boolean allowBlur, boolean scaleToFit) {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    if (maxSize != 0) {
      opts.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(data, 0, data.length, opts);
      opts.inJustDecodeBounds = false;
      opts.inSampleSize = opts.outWidth != 0 && opts.outHeight != 0 ? calculateInSampleSize(opts, maxSize, maxSize) : 1;
    } else {
      opts.inSampleSize = 1;
    }
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);

    if (bitmap != null) {
      if (maxSize > 0 && Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSize && scaleToFit) {
        bitmap = resizeBitmap(bitmap, maxSize, maxSize, false);
      }
      if (allowBlur && (bitmap.getWidth() < 100 || bitmap.getHeight() < 100)) {
        U.blurBitmap(bitmap, 3, 1);
      } else if (Config.PIN_BITMAP_ENABLED) {
        N.pinBitmapIfNeeded(bitmap);
      }
    }

    return bitmap;
  }

  private static Bitmap scaleToFit (Bitmap bitmap, int maxWidth, int maxHeight) {
    if (!U.isValidBitmap(bitmap) || maxWidth <= 0 || maxHeight <= 0)
      return bitmap;
    if (bitmap.getWidth() > maxWidth || bitmap.getHeight() > maxHeight) {
      float ratio = Math.min((float) maxWidth / (float) bitmap.getWidth(), (float) maxHeight / (float) bitmap.getHeight());
      int targetWidth = (int) ((float) bitmap.getWidth() * ratio);
      int targetHeight = (int) ((float) bitmap.getHeight() * ratio);

      Bitmap scaledBitmap = null;
      try {
        scaledBitmap = resizeBitmap(bitmap, targetWidth, targetHeight, false);
      } catch (Throwable ignored) { }

      if (scaledBitmap != null && bitmap != scaledBitmap) {
        bitmap.recycle();
        return scaledBitmap;
      }
    }
    return bitmap;
  }

  private static Bitmap cropSquare (Bitmap bitmap) {
    if (U.isValidBitmap(bitmap) && bitmap.getWidth() != bitmap.getHeight()) {
      return TdlibFileGenerationManager.cropSquare(bitmap);
    }
    return bitmap;
  }

  @SuppressWarnings ("deprecation")
  private void readVideoThumb (ImageVideoThumbFile file, Listener listener) {
    int maxWidth = file.getMaxWidth();
    int maxHeight = file.getMaxHeight();

    int[] rotation = new int[1];

    Bitmap bitmap = U.tryDecodeVideoThumb(file.getFilePath(), file.getFrameTimeUs(), maxWidth, maxHeight, rotation);

    if (rotation[0] != 0) {
      file.setVideoRotation(rotation[0]);
    }

    bitmap = scaleToFit(bitmap, maxWidth, maxHeight);
    if (file.needDecodeSquare()) {
      bitmap = cropSquare(bitmap);
    }

    listener.onImageLoaded(bitmap != null, bitmap);
  }

  @SuppressWarnings ("deprecation")
  private void readThumbImage (ImageActor actor, ImageGalleryFile file, Listener listener) {
    long imageId = file.getGalleryId();

    BitmapFactory.Options opts = new BitmapFactory.Options();

    int size = file.getSize();
    opts.inSampleSize = size == 0 ? 1 : Math.max(1, Math.round(521f / (float) size)); // Math.min(521 / size, 384 / size)
    opts.inDither = false;
    opts.inPurgeable = !file.needBlur() && Config.PIN_BITMAP_ENABLED;
    opts.inPreferredConfig = !file.needBlur() ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;

    Bitmap bitmap;

    if (file.isVideo()) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        MediaMetadataRetriever retriever = null;
        try {
          retriever = U.openRetriever(file.getFilePath());
          String metadata = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
          if (metadata != null && !metadata.isEmpty() && StringUtils.isNumeric(metadata)) {
            file.setRotation(StringUtils.parseInt(metadata));
          }
        } catch (Throwable ignored) { }
        U.closeRetriever(retriever);
      }

      bitmap = null;
      if (file.getStartTimeUs() > 0) {
        int[] rotation = new int[1];
        bitmap = U.tryDecodeVideoThumb(file.getFilePath(), file.getStartTimeUs(), size, size, rotation);
        bitmap = scaleToFit(bitmap, size, size);
        if (file.needDecodeSquare()) {
          bitmap = cropSquare(bitmap);
        }
      }
      if (!U.isValidBitmap(bitmap)) {
        bitmap = MediaStore.Video.Thumbnails.getThumbnail(UI.getAppContext().getContentResolver(), imageId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
      try {
        bitmap = UI.getAppContext().getContentResolver().loadThumbnail(uri, new Size(512, 512), actor.getCancellationSignal());
      } catch (OperationCanceledException | IOException e) {
        bitmap = null;
      }
      if (bitmap == null)
        bitmap = MediaStore.Images.Thumbnails.getThumbnail(UI.getAppContext().getContentResolver(), imageId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
      if (bitmap == null && Config.MODERN_IMAGE_DECODER_ENABLED) {
        try {
          android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(new File(file.getFilePath()));
          bitmap = android.graphics.ImageDecoder.decodeBitmap(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded (@NonNull android.graphics.ImageDecoder decoder, @NonNull android.graphics.ImageDecoder.ImageInfo info, @NonNull android.graphics.ImageDecoder.Source source) {
              if (Config.FORCE_SOFTWARE_IMAGE_DECODER) {
                decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
              }
              if (file.isSwOnly()) {
                decoder.setMutableRequired(true);
              }
              int limitSize = file.isPrivate() ? 36 : file.getSize();
              int width = info.getSize().getWidth();
              int height = info.getSize().getHeight();
              if (limitSize != 0) {
                float ratio = Math.min((float) limitSize / (float) width, (float) limitSize / (float) height);
                if (ratio < 1f) {
                  decoder.setTargetSize(width = (int) ((float) width * ratio), height = (int) ((float) height * ratio));
                }
              }
              if (file.needDecodeSquare() && width != height) {
                if (width > height) {
                  decoder.setCrop(new Rect(width / 2 - height / 2, 0, width / 2 + height / 2 + height % 2, height));
                } else {
                  decoder.setCrop(new Rect(0, height / 2 - width / 2, width, height / 2 + width / 2 + width % 2));
                }
              }
            }
          });
        } catch (IOException t) {
          Log.i(Log.TAG_IMAGE_LOADER, "#%s: Couldn't load file: %d", t, file.toString());
        }
      }
    } else {
      bitmap = MediaStore.Images.Thumbnails.getThumbnail(UI.getAppContext().getContentResolver(), imageId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
    }
    if (bitmap == null)
      bitmap = decodeFile(file.getFilePath(), opts);

    if (bitmap != null) {
      if (!file.isWebp() && file.shouldUseBlur() && file.needBlur()) {
        U.blurBitmap(bitmap, file.getBlurRadius(), opts.inPurgeable ? 0 : 1);
      } else if (opts.inPurgeable) {
        N.pinBitmapIfNeeded(bitmap);
      }
    }

    listener.onImageLoaded(bitmap != null, bitmap);
  }

  public static int calculateInSampleSize (int width, int height, int reqWidth, int reqHeight) {
    return (Math.max(width, height) <= reqHeight) ? 1 : width > height ? width / reqWidth : height / reqHeight;
  }

  public static int calculateInSampleSize (BitmapFactory.Options options, int reqWidth, int reqHeight) {
    return calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
  }

  public static int ceilInSampleSize (int width, int height, int reqWidth, int reqHeight) {
    return (height <= reqHeight && width <= reqHeight) ? 1 : Math.max(1, Math.max(width / reqWidth, height / reqHeight) - 1);
  }

  public static void getImageSize (String path, BitmapFactory.Options opts) {
    opts.inSampleSize = 1;
    opts.inJustDecodeBounds = true;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && path.startsWith("content://")) {
      boolean done = false;
      // Trying to resolve additional information
      try (Cursor c = UI.getContext().getContentResolver().query(Uri.parse(path), new String[] {
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT
      }, null, null, null)) {
        if (c != null && c.moveToFirst()) {
          long width = c.getLong(0);
          long height = c.getLong(0);

          if (width > 0 && height > 0) {
            opts.outWidth = (int) width;
            opts.outHeight = (int) height;
            done = true;
          }
        }
      } catch (Throwable t) {
        Log.w("Cannot resolve display name/size/mime", t);
      }
      if (done) {
        return;
      }
    }

    decodeFile(path, opts);
  }

  public static Bitmap decodeFile (String path, BitmapFactory.Options opts) {
    try (InputStream is = U.openInputStream(path)) {
      return BitmapFactory.decodeStream(is, null, opts);
    } catch (Throwable t) {
      Log.e("Error decoding file", t);
    }
    return null;
  }

  public static Bitmap decodeLottieFrame (String path, int maxSize) {
    String json = U.gzipFileToString(path);
    if (StringUtils.isEmpty(json))
      return null;
    long ptr = N.createLottieDecoder(path, json, null, null);
    if (ptr == 0)
      return null;
    int[] size = new int[2];
    N.getLottieSize(ptr, size);
    if (size[0] <= 0 || size[1] <= 0) {
      N.destroyLottieDecoder(ptr);
      return null;
    }
    int width = size[0];
    int height = size[1];
    if (maxSize != 0 && Math.max(width, height) > maxSize) {
      float ratio = Math.min((float) maxSize / (float) width, (float) maxSize / (float) height);
      width *= ratio;
      height *= ratio;
    }
    Bitmap result = null;
    try {
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      if (N.getLottieFrame(ptr, bitmap, 0)) {
        result = bitmap;
      } else {
        bitmap.recycle();
      }
    } catch (Throwable t) {
      Log.e("Cannot create bitmap", t);
    }
    N.destroyLottieDecoder(ptr);
    return result;
  }

  public static Bitmap decodeLottieFrame (String path, int width, int height, int maxSize) {
    String json = U.gzipFileToString(path);
    if (StringUtils.isEmpty(json))
      return null;
    if (maxSize != 0 && Math.max(width, height) > maxSize) {
      float ratio = Math.min((float) maxSize / (float) width, (float) maxSize / (float) height);
      width *= ratio;
      height *= ratio;
    }
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    if (N.decodeLottieFirstFrame(path, json, result)) {
      return result;
    }
    result.recycle();
    return null;
  }

  public static BitmapFactory.Options getImageSize (String path) {
    if (path == null) {
      return null;
    }

    BitmapFactory.Options opts = new BitmapFactory.Options();
    getImageSize(path, opts);

    return opts;
  }

  public static BitmapFactory.Options getImageWebpSize (String path) {
    if (path == null) {
      return null;
    }

    if (!Config.useBundledWebp()) {
      return getImageSize(path);
    }

    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;
    opts.inJustDecodeBounds = true;

    File file = new File(path);
    try (RandomAccessFile f = new RandomAccessFile(new File(path), "r")) {
      ByteBuffer buffer = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
      N.loadWebpImage(null, buffer, buffer.limit(), opts, true);
    } catch (Throwable t) {
      Log.w(Log.TAG_IMAGE_LOADER, "Cannot get WebP bounds, file: %s", t, path);
      return null;
    }

    return opts;
  }

  /*@Deprecated
  public static void clearUpload () {
    Background.instance().post(new Runnable() {
      @Override
      public void run () {
        try {
          int removedCount = 0;
          int errorCount = 0;
          int missingCount = 0;

          Map<String, ?> map = Prefs.instance().getAll("upload");

          for (String str : map.keySet()) {
            if (!str.startsWith("upload_")) {
              continue;
            }
            try {
              File file = new File((String) map.get(str));
              if (file.exists()) {
                if (file.delete()) {
                  removedCount++;
                } else {
                  errorCount++;
                }
              } else {
                missingCount++;
              }
            } catch (Throwable t) {
              Log.w(Log.TAG_IMAGE_LOADER, "Cannot remove file", t);
              errorCount++;
            }
          }

          Prefs.instance().clear("upload");

          Log.i(Log.TAG_IMAGE_LOADER, "Cleared upload cache. Removed: %d, Errors: %d, Missing: %d", removedCount, missingCount, errorCount);
        } catch (Throwable t) {
          Log.w(Log.TAG_IMAGE_LOADER, "Cannot clear upload cache", t);
        }
      }
    });
  }*/

  public static Bitmap resizeBitmap (Bitmap bitmap, int maxWidth, int maxHeight, boolean pin) {
    return resizeBitmap(bitmap, maxWidth, maxHeight, pin, false, true);
  }

  public static Bitmap resizeBitmap (Bitmap bitmap, int maxWidth, int maxHeight, boolean pin, boolean returnOriginal, boolean allowRecycle) {
    if (bitmap == null || bitmap.isRecycled()) {
      return null;
    }

    int width = bitmap.getWidth();
    int height = bitmap.getHeight();

    if (width < maxWidth && height < maxHeight) {
      return bitmap;
    }

    float ratio = Math.min((float) maxWidth / (float) width, (float) maxHeight / (float) height);

    Bitmap resized = null;

    try {
      resized = Bitmap.createScaledBitmap(bitmap, (int) (width * ratio), (int) (height * ratio), true);
      if (resized != null) {
        if (allowRecycle && !bitmap.isRecycled()) {
          bitmap.recycle();
        }
        if (pin) {
          N.pinBitmapIfNeeded(resized);
        }
      }
    } catch (Throwable t) {
      Log.w("Cannot resize bitmap", t);
      if (!returnOriginal) {
        if (resized != null) { try { resized.recycle();} catch (Throwable ignored) { } }
        throw t;
      }
    }

    if (resized == null) {
      if (returnOriginal) {
        return bitmap;
      } else {
        throw new NullPointerException();
      }
    }

    return resized;
  }

  public interface Listener {
    void onImageLoaded (boolean success, Bitmap result);
  }
}
