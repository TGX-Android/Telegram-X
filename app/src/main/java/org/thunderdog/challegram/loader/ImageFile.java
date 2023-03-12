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
 * File created on 06/05/2015 at 20:48
 */
package org.thunderdog.challegram.loader;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.palette.graphics.Palette;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibProvider;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.td.Td;

public class ImageFile {
  public static final int HTTP_START_ID = -1000000;
  public static final int GALLERY_START_ID = -2000000;
  public static final int LOCAL_START_ID = -3000000;
  public static final int MP3_START_ID = -4000000;

  public static final int FIT_CENTER = 1;
  public static final int CENTER_CROP = 2;
  public static final int CENTER_REPEAT = 3;

  public static final byte TYPE_BASIC = (byte) 1;
  public static final byte TYPE_GALLERY = (byte) 2;
  public static final byte TYPE_LOCAL = (byte) 3;
  public static final byte TYPE_PERSISTENT = (byte) 4;
  public static final byte TYPE_MP3 = (byte) 5;
  public static final byte TYPE_VIDEO_THUMB = (byte) 6;

  private static final int FLAG_WEBP = 1;
  private static final int FLAG_NO_CACHE = 1 << 1;
  private static final int FLAG_NO_BLUR = 1 << 3;
  private static final int FLAG_FORCE_ARGB_8888 = 1 << 4;
  private static final int FLAG_PROBABLY_ROTATED = 1 << 5;
  private static final int FLAG_PRIVATE = 1 << 6;
  private static final int FLAG_NEED_HIGH_RESOLUTION = 1 << 7;
  private static final int FLAG_NEED_FIT_SIZE = 1 << 8;
  private static final int FLAG_NO_REFERENCE = 1 << 9;
  private static final int FLAG_DECODE_SQUARE = 1 << 10;
  private static final int FLAG_CACHE_ONLY = 1 << 11;
  private static final int FLAG_CANCEL = 1 << 12;
  private static final int FLAG_CANCEL_WEAK = 1 << 13;
  private static final int FLAG_SUPPRESS_EMPTY_BUNDLE = 1 << 14;
  private static final int FLAG_FORCE_RGB_565 = 1 << 15;
  private static final int FLAG_FORCE_SW_RENDER = 1 << 16;
  private static final int FLAG_NEED_PALETTE = 1 << 17;
  private static final int FLAG_IS_VECTOR = 1 << 18;
  private static final int FLAG_IS_CONTENT_URI = 1 << 19;

  protected TdApi.File file;

  private int size, blurRadius, scaleType;
  private int flags;
  protected String key;

  protected final byte[] bytes;

  protected int rotation;

  protected final TdlibProvider tdlib;
  private Palette.Swatch palette;

  public ImageFile (TdlibProvider tdlib, TdApi.File file, byte[] bytes) {
    this.tdlib = tdlib;
    this.file = file;
    this.bytes = bytes != null && bytes.length > 0 ? bytes : null;
  }

  public ImageFile (TdlibProvider tdlib, TdApi.File file) {
    this(tdlib, file, null);
  }

  public void setPalette (Palette.Swatch palette) {
    this.palette = palette;
  }

  public Palette.Swatch getPaletteSwatch () {
    return palette;
  }

  public Tdlib tdlib () {
    return tdlib != null ? tdlib.tdlib() : null;
  }

  public boolean isRemote () {
    return tdlib() != null && getId() > 0;
  }

  public byte[] getBytes () {
    return bytes;
  }

  public final int accountId () {
    return tdlib != null ? tdlib.accountId() : TdlibAccount.NO_ID;
  }

  public void setSuppressEmptyBundle (boolean suppress) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SUPPRESS_EMPTY_BUNDLE, suppress);
  }

  public void setSwOnly (boolean swOnly) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_FORCE_SW_RENDER, swOnly);
  }

  public boolean needPalette () {
    return BitwiseUtils.getFlag(flags, FLAG_NEED_PALETTE);
  }

  public void setNeedPalette (boolean needPalette) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_PALETTE, needPalette);
    if (needPalette) {
      setSwOnly(true);
    }
  }

  public boolean suppressEmptyBundle () {
    boolean suppress = (flags & FLAG_SUPPRESS_EMPTY_BUNDLE) != 0;
    if (suppress) {
      flags &= ~FLAG_SUPPRESS_EMPTY_BUNDLE;
    }
    return suppress;
  }

  public void setWebp () {
    flags |= FLAG_WEBP;
  }

  public void setNoCache () {
    flags |= FLAG_NO_CACHE;
  }

  public void setNoBlur () {
    flags |= FLAG_NO_BLUR;
  }

  public void setDecodeSquare (boolean enabled) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_DECODE_SQUARE, enabled);
  }

  public void setNeedCancellation (boolean onlyPending) {
    flags |= FLAG_CANCEL;
    if (onlyPending) {
      flags |= FLAG_CANCEL_WEAK;
    }
  }

  public void setProbablyRotated () {
    flags |= FLAG_PROBABLY_ROTATED;
  }

  public void setScaleType (int scaleType) {
    this.scaleType = scaleType;
  }

  public void setNeedBlur () {
    blurRadius = 3;
  }

  public void setCacheOnly () {
    flags |= FLAG_CACHE_ONLY;
  }

  public void setIsPrivate () {
    flags |= FLAG_PRIVATE;
  }

  public void setIsVector () {
    flags |= FLAG_IS_VECTOR;
  }

  public void setIsContentUri () {
    flags |= FLAG_IS_CONTENT_URI;
  }

  public void setNoReference () {
    flags |= FLAG_NO_REFERENCE;
  }

  public boolean needReferences () {
    return (flags & FLAG_NO_REFERENCE) == 0;
  }

  public boolean isSwOnly () {
    return (flags & FLAG_FORCE_SW_RENDER) != 0;
  }

  public boolean isCacheOnly () {
    return (flags & FLAG_CACHE_ONLY) != 0;
  }

  public boolean isPrivate () {
    return (flags & FLAG_PRIVATE) != 0;
  }

  public boolean isVector () {
    return (flags & FLAG_IS_VECTOR) != 0;
  }

  public boolean isContentUri () {
    return (flags & FLAG_IS_CONTENT_URI) != 0;
  }

  public void setBlur (int radius) {
    blurRadius = radius;
  }

  public void setSize (int size) {
    this.size = size;
  }

  public interface RotationListener {
    void onRotationChanged (ImageFile imageFile, int newDegrees, boolean byUserRequest);
  }

  private RotationListener rotationMetadataListener;

  public void setRotationMetadataListener (RotationListener listener) {
    this.rotationMetadataListener = listener;
  }

  public void setRotation (int degrees) {
    if (this.rotation != degrees && rotationMetadataListener != null) {
      this.rotation = degrees;
      this.rotationMetadataListener.onRotationChanged(this, degrees, false);
    } else {
      this.rotation = degrees;
    }
  }

  public void setRotation (int degrees, boolean byUserRequest) {
    if (this.rotation != degrees && rotationMetadataListener != null) {
      this.rotation = degrees;
      this.rotationMetadataListener.onRotationChanged(this, degrees, byUserRequest);
    } else {
      this.rotation = degrees;
    }
  }

  public void setForceRgb565 () {
    this.flags |= FLAG_FORCE_RGB_565;
  }

  public boolean forceRgb565 () {
    return (flags & FLAG_FORCE_RGB_565) != 0;
  }

  public void setForceArgb8888 () {
    this.flags |= FLAG_FORCE_ARGB_8888;
  }

  public boolean forceArgb8888 () {
    return (flags & FLAG_FORCE_ARGB_8888) != 0;
  }

  public int getSize () {
    return size;
  }

  public int getRotation () {
    return rotation;
  }

  public int getVisualRotation () {
    return rotation;
  }

  public TdApi.File getFile () {
    return file;
  }

  public String getFilePath () {
    return file.local != null ? file.local.path : null;
  }

  public String getTargetPath () {
    return filtersState != null && !filtersState.isEmpty() ? ImageFilteredFile.getPath(filtersState) : file.local.path;
  }

  public String getRemoteId () {
    return file.remote.id;
  }

  public int getId () {
    return file.id;
  }

  public boolean isWebp () {
    return (flags & FLAG_WEBP) != 0;
  }

  public boolean needDecodeSquare () {
    return (flags & FLAG_DECODE_SQUARE) != 0;
  }

  public boolean needCancellation () {
    return (flags & FLAG_CANCEL) != 0;
  }

  public boolean isCancellationOnlyPending () {
    return (flags & FLAG_CANCEL_WEAK) != 0;
  }

  public boolean isProbablyRotated () {
    return (flags & FLAG_PROBABLY_ROTATED) != 0;
  }

  public boolean shouldBeCached () {
    return (flags & FLAG_NO_CACHE) == 0;
  }

  public boolean shouldUseBlur () {
    return (flags & FLAG_NO_BLUR) == 0;
  }

  public void setNeedHiRes () {
    this.flags |= FLAG_NEED_HIGH_RESOLUTION;
  }

  public boolean needHiRes () {
    return (flags & FLAG_NEED_HIGH_RESOLUTION) != 0;
  }

  public void setNeedFitSize () {
    flags |= FLAG_NEED_FIT_SIZE;
  }

  public boolean needFitSize () {
    return (flags & FLAG_NEED_FIT_SIZE) != 0;
  }

  public int getScaleType () {
    return scaleType;
  }

  public int getBlurRadius () {
    return blurRadius == 0 ? 3 : blurRadius;
  }

  public boolean needBlur () {
    return blurRadius != 0;
  }

  /*public boolean needOverlayCalcs () {
    return (flags & FLAG_OVERLAY_CALCULATIONS) != 0;
  }*/

  /*public void copyOverlayCalcs (int[] calcs) {
    this.overlayCalcs = calcs;
  }*/

  public void updateFile (TdApi.File src) {
    Td.copyTo(src, file);
    this.file = src;
  }

  public float getProgressFactor () {
    return TD.getFileProgress(file);
  }

  @Override
  public final int hashCode () {
    return toString().hashCode();
  }

  @Override
  public boolean equals (Object object) {
    return object instanceof ImageFile && toString().equals(object.toString());
  }

  protected final StringBuilder buildStandardKey (StringBuilder b) {
    b.append("account");
    b.append(accountId());
    b.append('_');
    b.append(Td.getId(file));
    b.append('_');
    b.append(size);
    if ((flags & FLAG_DECODE_SQUARE) != 0) {
      b.append("_square");
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (flags & FLAG_FORCE_SW_RENDER) != 0) {
      b.append("_sw");
    }
    return b;
  }

  protected String buildImageKey () {
    return buildStandardKey(new StringBuilder()).toString();
  }

  @Override
  @NonNull
  public final String toString () {
    return (key != null ? key : (key = buildImageKey()));
  }

  public byte getType () {
    return TYPE_BASIC;
  }

  static String getFileLoadKey (int accountId, int fileId) {
    return accountId + "_" + fileId;
  }

  static String getFileLoadKey (Tdlib tdlib, int fileId) {
    return getFileLoadKey(tdlib != null ? tdlib.id() : TdlibAccount.NO_ID, fileId);
  }

  static String getFileLoadKey (int accountId, String remoteFileId) {
    return accountId + "_" + remoteFileId;
  }

  static String getFileLoadKey (Tdlib tdlib, String remoteFileId) {
    return getFileLoadKey(tdlib != null ? tdlib.id() : TdlibAccount.NO_ID, remoteFileId);
  }

  public String getFileLoadKey () {
    return getFileLoadKey(accountId(), file.id);
  }

  // Any changes

  public boolean hasAnyChanges () {
    return (filtersState != null && !filtersState.isEmpty()) /*|| getCropState() != null*/ || getPaintState() != null;
  }

  // Filters

  private FiltersState filtersState;

  public FiltersState getFiltersState () {
    return filtersState;
  }

  public void setFiltersState (FiltersState state) {
    if (state == null || state.isEmpty()) {
      this.filtersState = null;
    } else {
      this.filtersState = state;
    }
  }

  // Crop

  private CropState cropState;

  public CropState getCropState () {
    return cropState != null && !cropState.isEmpty() ? cropState : null;
  }

  public void setCropState (CropState state) {
    if (state == null || state.isEmpty()) {
      this.cropState = null;
    } else {
      this.cropState = state;
    }
    if (cropStateListeners != null) {
      final int size = cropStateListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        CropStateChangeListener listener = cropStateListeners.get(i).get();
        if (listener != null) {
          listener.onCropStateChanged(this, state);
        } else {
          cropStateListeners.remove(i);
        }
      }
    }
  }

  public interface CropStateChangeListener {
    void onCropStateChanged (ImageFile file, CropState newCropState);
  }

  private List<Reference<CropStateChangeListener>> cropStateListeners;

  public void addCropStateListener (CropStateChangeListener listener) {
    if (cropStateListeners == null) {
      cropStateListeners = new ArrayList<>();
    }
    ReferenceUtils.addReference(cropStateListeners, listener);
  }

  public void removeCropStateListener (CropStateChangeListener listener) {
    if (cropStateListeners != null) {
      ReferenceUtils.removeReference(cropStateListeners, listener);
    }
  }

  // Changes

  public interface ChangeListener {
    void onImageChanged (ImageFile file);
  }

  private ReferenceList<ChangeListener> changeListeners;

  public void addChangeListener (ChangeListener listener) {
    if (changeListeners == null)
      changeListeners = new ReferenceList<>();
    changeListeners.add(listener);
  }

  public void removeChangeListener (ChangeListener listener) {
    if (changeListeners != null)
      changeListeners.remove(listener);
  }

  public void notifyChanged () {
    this.key = null;
    if (changeListeners != null) {
      for (ChangeListener listener : changeListeners) {
        listener.onImageChanged(this);
      }
    }
  }

  // Paint

  private PaintState paintState;

  public PaintState getPaintState () {
    return paintState != null && !paintState.isEmpty() ? paintState : null;
  }

  public boolean setPaintState (PaintState state) {
    if (state == null || state.isEmpty()) {
      if (this.paintState == null) {
        return false;
      }
      this.paintState = null;
      return true;
    } else if (this.paintState == null || !this.paintState.compare(state)) {
      this.paintState = state;
      return true;
    } else {
      this.paintState = state;
      return false;
    }
  }

  // TTL

  private int ttl;

  public int getTTL () {
    return ttl;
  }

  public void setTTL (int ttl) {
    this.ttl = ttl;
  }

  public static ImageFile copyOf (ImageFile imageFile) {
    if (imageFile instanceof ImageFileLocal) {
      return new ImageFileLocal((ImageFileLocal) imageFile);
    } else if (imageFile instanceof ImageFileRemote) {
      return new ImageFileRemote(imageFile.tdlib, imageFile.getRemoteId(), ((ImageFileRemote) imageFile).getFileType());
    } else {
      return new ImageFile(imageFile.tdlib, imageFile.getFile());
    }
  }

  // Overlay calculations

  /*public static final int LEFT_TOP_INDEX = 0;
  public static final int RIGHT_TOP_INDEX = 1;
  public static final int RIGHT_MIDDLE_INDEX = 2;
  public static final int LEFT_BOTTOM_INDEX = 3;
  public static final int RIGHT_BOTTOM_INDEX = 4;

  private int[] overlayCalcs; // array of 5 average colors: left-top, right-top (xx), right-top (x), left-bottom, right-bottom

  public void makeOverlayCalcs (Bitmap bitmap) {
    if (overlayCalcs != null) {
      return;
    }

    int startX, startY;
    int size;

    int bitmapWidth = bitmap.getWidth(), bitmapHeight = bitmap.getHeight();

    if (bitmapWidth > bitmapHeight) {
      size = bitmapHeight;
      startY = 0;
      startX = (int) ((float) bitmapWidth * .5f - size * .5f);
    } else if (bitmapHeight > bitmapWidth) {
      size = bitmapWidth;
      startX = 0;
      startY = (int) ((float) bitmapHeight * .5f - size * .5f);
    } else {
      size = bitmapWidth;
      startX = 0;
      startY = 0;
    }

    overlayCalcs = new int[5];

    float scale = (float) size / (float) Screen.smallestSide();
    int buttonWidth = (int) (scale * (float) Screen.dp(56f));

    overlayCalcs[LEFT_TOP_INDEX] = makeOverlayCalc(bitmap, startX, startY, startX + buttonWidth, startY + buttonWidth);
    overlayCalcs[RIGHT_TOP_INDEX] = makeOverlayCalc(bitmap, startX + size - buttonWidth, startY, startX + size, startY + buttonWidth);
    overlayCalcs[RIGHT_MIDDLE_INDEX] = makeOverlayCalc(bitmap, startX + size - buttonWidth * 2, startY, startX + size - buttonWidth, startY + buttonWidth);

    int padding = (int) (scale * (float) Screen.dp(10f));

    int startY2 = startY + size - padding - buttonWidth;
    int endY2 = startY + size - padding;

    overlayCalcs[LEFT_BOTTOM_INDEX] = makeOverlayCalc(bitmap, startX + padding, startY2, startX + padding + buttonWidth, endY2);
    overlayCalcs[RIGHT_BOTTOM_INDEX] = makeOverlayCalc(bitmap, startX + size - padding - buttonWidth, startY2, startX + size - padding, endY2);

    ImageCache.instance().putOverlayCalcs(this);
  }

  private int makeOverlayCalc (Bitmap bitmap, int startX, int startY, int endX, int endY) {
    int count = 0;
    int red = 0;
    int green = 0;
    int blue = 0;

    for (int x = startX; x < endX; x++) {
      for (int y = startY; y < endY; y++) {
        if (bitmap.isRecycled()) {
          return 0xff000000;
        }
        int color = bitmap.getPixel(x, y);
        red += Color.red(color);
        green += Color.green(color);
        blue += Color.blue(color);
        count++;
      }
    }

    return count == 0 ? 0xff000000 : Color.rgb(red / count, green / count, blue / count);
  }

  public int[] getOverlayCalcs () {
    if (overlayCalcs == null) {
      ImageCache.instance().copyOverlayCalcs(this);
    }
    return overlayCalcs;
  }*/
}
