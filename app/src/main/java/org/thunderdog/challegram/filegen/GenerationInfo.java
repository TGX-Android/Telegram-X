package org.thunderdog.challegram.filegen;

import android.os.SystemClock;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;

/**
 * Date: 08/12/2016
 * Author: default
 */
public abstract class GenerationInfo {
  static final String TYPE_PHOTO = "photo";
  static final String TYPE_PHOTO_THUMB = "pthumb";
  static final String TYPE_VIDEO_THUMB = "vthumb";
  static final String TYPE_MUSIC_THUMB = "mthumb";
  static final String TYPE_VIDEO = "video";
  static final String TYPE_AVATAR = "avatar";
  public static final String TYPE_STICKER_PREVIEW = "asthumb";

  protected final long generationId;
  protected final String originalPath;
  protected final String conversion;
  protected final String destinationPath;

  public GenerationInfo (long generationId, String originalPath, String destinationPath, String conversion, boolean hasThumb) {
    this.generationId = generationId;
    this.conversion = conversion;
    this.originalPath = originalPath;
    this.destinationPath = destinationPath;
  }

  public String getKey () {
    StringBuilder b = new StringBuilder(originalPath);
    if (conversion != null) {
      b.append('?');
      b.append(conversion);
    }
    return b.toString();
  }

  public long getGenerationId () {
    return generationId;
  }

  public String getOriginalPath () {
    return originalPath;
  }

  public String getDestinationPath () {
    return destinationPath;
  }

  @Override
  public String toString () {
    return getKey();
  }

  private Runnable onCancel;

  public void setOnCancel (Runnable onCancel) {
    synchronized (this) {
      this.onCancel = onCancel;
    }
  }

  public void cancel () {
    synchronized (this) {
      if (onCancel != null) {
        onCancel.run();
        onCancel = null;
      }
    }
  }

  public static String randomStamp () {
    return SystemClock.uptimeMillis() + "_" + System.currentTimeMillis() + "_" + Math.random();
  }

  public static long lastModified (String path) {
    return Config.DISABLE_SENDING_MEDIA_CACHE ? System.currentTimeMillis() : Config.WORKAROUND_NEED_MODIFY ? U.getLastModifiedTime(path) : 0;
  }
}
