/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.filegen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.paint.PaintState;

import java.io.InputStream;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class PhotoGenerationInfo extends GenerationInfo {
  public static final int SIZE_LIMIT = 1280;

  private int rotation; // 0, 90, 180 or 270
  private boolean isFiltered;
  private CropState cropState;
  private PaintState paintState;
  private int resolutionLimit;
  private boolean allowTransparency;

  public PhotoGenerationInfo (long generationId, String originalPath, String destinationPath, String conversion) {
    super(generationId, originalPath, destinationPath, conversion, false);
    parseConversion(conversion.substring(GenerationInfo.TYPE_PHOTO.length()));
  }

  public boolean isEdited () {
    return isFiltered || (paintState != null && !paintState.isEmpty()) || (cropState != null && !cropState.isEmpty());
  }

  private void parseConversion (String conversion) {
    String[] args = conversion.split(",", -1);
    rotation = StringUtils.parseInt(args[0]);
    if (args.length > 1) {
      isFiltered = !args[1].isEmpty();
    }
    if (args.length > 2) {
      cropState = CropState.parse(args[2]);
    }
    if (args.length > 3) {
      for (int i = 3; i < args.length; i++) {
        String arg = args[i];
        int j = arg.indexOf(':');
        String type = j == -1 ? arg : arg.substring(0, j);
        String data = j != -1 && arg.length() > 1 ? arg.substring(j + 1) : null;
        switch (type) {
          case "p": {
            paintState = PaintState.parse(data);
            break;
          }
          case "l": {
            resolutionLimit = StringUtils.parseInt(data);
            break;
          }
          case "t": {
            allowTransparency = true;
            break;
          }
        }
      }
    }
  }

  public int getResolutionLimit () {
    return resolutionLimit;
  }

  public boolean getAllowTransparency () {
    return allowTransparency;
  }

  public boolean isFiltered () {
    return isFiltered;
  }

  public CropState getCropState () {
    return cropState != null && !cropState.isEmpty() ? cropState : null;
  }

  public PaintState getPaintState () {
    return paintState != null && !paintState.isEmpty() ? paintState : null;
  }

  public boolean needSpecialProcessing (boolean needRotate) {
    if (paintState != null && !paintState.isEmpty()) {
      return true;
    }
    if (cropState != null) {
      int rotation = (needRotate ? this.rotation : 0) + cropState.getRotateBy();
      return rotation != 0 || cropState.getDegreesAroundCenter() != 0 || regionDecoderState == REGION_ERROR;
    } else {
      return needRotate && rotation != 0;
    }
  }

  private static final int REGION_OK = 1;
  private static final int REGION_ERROR = 2;

  private int regionDecoderState;
  private double left, top, right, bottom;
  private int originalWidth, originalHeight;
  private Rect regionRect;

  public Bitmap readImage (InputStream is, BitmapFactory.Options opts, String filePath) throws Throwable {
    originalWidth = opts.outWidth;
    originalHeight = opts.outHeight;

    if (cropState != null && !cropState.isRegionEmpty()) {
      // TODO move this part of code to somewhere common & reuse in ImageReceiver & CropState
      // First, normalize region area
      left = cropState.getLeft();
      right = cropState.getRight();
      top = cropState.getTop();
      bottom = cropState.getBottom();

      int rotateArea = -MathUtils.modulo(rotation + cropState.getRotateBy(), 360);
      while (rotateArea != 0) {
        if (left != 0.0 || top != 0.0 || right != 1.0 || bottom != 1.0) {
          double prevLeft = left;
          double prevTop = top;
          double prevRight = right;
          double prevBottom = bottom;

          if (rotateArea < 0) {
            bottom = 1.0 - prevLeft;
            right = prevBottom;
            top = 1.0 - prevRight;
            left = prevTop;
            rotateArea += 90;
          } else {
            bottom = prevRight;
            left = 1.0 - prevBottom;
            top = prevLeft;
            right = 1.0 - prevTop;
            rotateArea -= 90;
          }
        } else {
          rotateArea += 90 * -Math.signum(rotateArea);
        }
      }

      Bitmap bitmapRegion = null;
      Rect regionRect = null;
      if (Config.CROP_USE_REGION_READER && opts.inSampleSize > 1 && cropState.getDegreesAroundCenter() == 0) { // TODO BitmapRegionDecoder support for getDegreesAroundCenter() != 0
        BitmapRegionDecoder decoder = null;
        try {
          regionRect = new Rect();

          regionRect.left = (int) Math.ceil(left * (double) originalWidth);
          regionRect.right = (int) Math.floor(right * (double) originalWidth);
          regionRect.top = (int) Math.ceil(top * (double) originalHeight);
          regionRect.bottom = (int) Math.floor(bottom * (double) originalHeight);

          BitmapFactory.Options regionOptions = new BitmapFactory.Options();
          regionOptions.inSampleSize = ImageReader.calculateInSampleSize(regionRect.width(), regionRect.height(), PhotoGenerationInfo.SIZE_LIMIT, PhotoGenerationInfo.SIZE_LIMIT);

          decoder = BitmapRegionDecoder.newInstance(is, false);
          bitmapRegion = decoder.decodeRegion(regionRect, regionOptions);
        } catch (Throwable t) {
          Log.i("BitmapRegionDecoder failed", t);
        }
        if (decoder != null) {
          try {
            decoder.recycle();
          } catch (Throwable ignored) { }
        }
      }
      regionDecoderState = bitmapRegion != null ? REGION_OK : REGION_ERROR;
      if (bitmapRegion != null) {
        this.regionRect = regionRect;
        return bitmapRegion;
      }
    }
    if (regionDecoderState == REGION_ERROR) {
      try (InputStream newIs = U.openInputStream(filePath)) {
        Bitmap result = null;
        try {
          result = BitmapFactory.decodeStream(newIs, null, opts);
        } catch (Throwable t) {
          Log.w("Cannot read bitmap", t);
        }
        return result;
      }
    } else {
      return BitmapFactory.decodeStream(is, null, opts);
    }
  }

  private void drawPaintState (Canvas c, int bitmapWidth, int bitmapHeight) {
    if (paintState != null) {
      if (regionDecoderState == REGION_OK) {
        float scaleX = (float) bitmapWidth / (float) regionRect.width();
        float scaleY = (float) bitmapHeight / (float) regionRect.height();
        int baseWidth = (int) ((float) originalWidth * scaleX);
        int baseHeight = (int) ((float) originalHeight * scaleY);

        int left = (int) ((float) regionRect.left * scaleX);
        int top = (int) ((float) regionRect.top * scaleY);

        paintState.draw(c, -left, -top, baseWidth, baseHeight);
      } else {
        paintState.draw(c, 0,0, bitmapWidth, bitmapHeight);
      }
    }
  }

  public Bitmap process (Bitmap source, boolean needRotate) {
    Matrix matrix = new Matrix();
    boolean matrixEmpty = true;

    int bitmapLeft = 0;
    int bitmapTop = 0;
    int bitmapRight = source.getWidth();
    int bitmapBottom = source.getHeight();
    int rotation = needRotate ? this.rotation : 0;
    boolean drawingComplete = false;

    if (cropState != null) {
      rotation = MathUtils.modulo(rotation + cropState.getRotateBy(), 360);
      if (regionDecoderState == REGION_ERROR) {
        Log.i("Region reader failed, cropping in-memory");
        int sourceWidth, sourceHeight;

        sourceWidth = source.getWidth();
        sourceHeight = source.getHeight();

        bitmapLeft = (int) Math.ceil(left * (double) sourceWidth);
        bitmapRight = (int) Math.floor(right * (double) sourceWidth);
        bitmapTop = (int) Math.ceil(top * (double) sourceHeight);
        bitmapBottom = (int) Math.floor(bottom * (double) sourceHeight);
      }

      final float preRotate = cropState.getDegreesAroundCenter();
      if (preRotate != 0f) {
        float w = source.getWidth();
        float h = source.getHeight();

        double rad = Math.toRadians(preRotate);
        float sin = (float) Math.abs(Math.sin(rad));
        float cos = (float) Math.abs(Math.cos(rad));

        // W = w·|cos φ| + h·|sin φ|
        // H = w·|sin φ| + h·|cos φ|

        float W = w * cos + h * sin;
        float H = w * sin + h * cos;

        Bitmap rotated = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(rotated);
        final float scale = Math.max(W / w, H / h);
        c.rotate(preRotate, w / 2, h / 2);
        if (scale != 1f) {
          c.scale(scale, scale, w / 2, h / 2);
        }
        c.drawBitmap(source, 0, 0, null);
        if (paintState != null) {
          drawPaintState(c, source.getWidth(), source.getHeight());
          drawingComplete = true;
        }
        source.recycle();
        source = rotated;
        U.recycle(c);
      }
    }

    if (rotation != 0) {
      matrixEmpty = false;
      matrix.setRotate(rotation);
    }

    if (matrixEmpty) {
      matrix = null;
    }

    if (paintState != null && !drawingComplete) {
      Bitmap altered = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(altered);
      c.drawBitmap(source, 0, 0, null);
      drawPaintState(c, source.getWidth(), source.getHeight());
      source.recycle();
      source = altered;
      U.recycle(c);
    }

    return Bitmap.createBitmap(source, bitmapLeft, bitmapTop, bitmapRight - bitmapLeft, bitmapBottom - bitmapTop, matrix, false);
  }

  public static TdApi.InputFileGenerated newFile (String path, int rotation) {
    /*String visualPath = path;
    if (!Strings.isEmpty(path)) {
      String extension = U.getExtension(path);
      String mimeType = TGMimeType.mimeTypeForExtension(extension);
      if (Strings.isEmpty(mimeType) || !mimeType.startsWith("image/")) {
        visualPath = path + ".jpg";
      }
    }*/
    return new TdApi.InputFileGenerated(path, makeConversion(rotation, lastModified(path), null), 0);
  }

  public static String makeConversion (int rotation, long lastModified, boolean transparent, int resolutionLimit) {
    String parameters = null;
    if (transparent || resolutionLimit != 0) {
      StringBuilder b = new StringBuilder();
      if (transparent){
        b.append("t");
      }
      if (resolutionLimit != 0) {
        if (b.length() > 0)
          b.append(",");
        b.append("l:");
        b.append(resolutionLimit);
      }
      parameters = b.toString();
    }
    return makeConversion(rotation, lastModified, parameters);
  }

  public static TdApi.InputFileGenerated newFile (String path, int rotation, long lastModified, boolean transparent, int resolutionLimit) {
    return new TdApi.InputFileGenerated(path, makeConversion(rotation, lastModified, transparent, resolutionLimit), 0);
  }

  public static TdApi.InputFileGenerated newFile (ImageGalleryFile file) {
    String path = file.getTargetPath();
    return new TdApi.InputFileGenerated(path, makeConversion(file, lastModified(path)), 0);
  }

  private static String makeConversion (int rotation, long lastModifiedTime, String parameters) {
    StringBuilder b = new StringBuilder(TYPE_PHOTO).append(rotation);
    if (lastModifiedTime != 0 || !StringUtils.isEmpty(parameters)) {
      b.append(",,,").append(lastModifiedTime);
      if (!StringUtils.isNumeric(parameters)) {
        b.append(",").append(parameters);
      }
    }
    return b.toString();
  }

  public static boolean isEmpty (ImageGalleryFile file) {
    return file != null && (file.getFiltersState() == null || file.getFiltersState().isEmpty()) && (file.getCropState() == null || file.getCropState().isEmpty()) && (file.getPaintState() == null || file.getPaintState().isEmpty());
  }

  public static String editResolutionLimit (String conversion, int limit) {
    PhotoGenerationInfo info = new PhotoGenerationInfo(-1, null, null, conversion);
    if (!info.isEdited() && limit == TdlibFileGenerationManager.SMALL_THUMB_RESOLUTION) {
      return ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_PHOTO, null, TdlibFileGenerationManager.SMALL_THUMB_RESOLUTION);
    }
    int remaining = 2;
    int fromIndex = 0;
    while ((fromIndex = conversion.indexOf(',', fromIndex)) != -1 && remaining > 0) {
      fromIndex++;
      remaining--;
    }
    StringBuilder b = new StringBuilder(conversion);
    for (int i = 0; i < remaining; i++) {
      b.append(',');
    }
    b.append(",l:").append(limit);
    return b.toString();
  }

  private static String makeConversion (ImageGalleryFile file, long lastModifiedTime) {
    StringBuilder b = new StringBuilder(TYPE_PHOTO);
    b.append(file.getRotation());

    FiltersState state = file.getFiltersState();
    b.append(',');
    if (state != null && !state.isEmpty()) {
      b.append(state.toString());
    }

    b.append(',');
    CropState cropState = file.getCropState();
    if (cropState != null && !cropState.isEmpty()) {
      b.append(cropState.toString());
    }

    PaintState paintState = file.getPaintState();
    if (paintState != null && !paintState.isEmpty()) {
      b.append(",p:");
      b.append(paintState.toString());
    }

    if (lastModifiedTime != 0) {
      b.append(",").append(lastModifiedTime);
    }

    return b.toString();
  }
}
