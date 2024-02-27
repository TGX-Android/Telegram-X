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
 * File created on 12/05/2015 at 12:27
 */
package org.thunderdog.challegram.loader;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.filegen.VideoGenerationInfo;
import org.thunderdog.challegram.mediaview.crop.CropState;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

public class ImageGalleryFile extends ImageFile implements Comparable<ImageGalleryFile> {
  private static int CURRENT_ID = ImageFile.GALLERY_START_ID;

  private static final int FLAG_NEED_THUMB = 0x01;
  private static final int FLAG_IS_VIDEO = 0x02;
  private static final int FLAG_MUTE_VIDEO = 0x04;
  private static final int FLAG_FROM_CAMERA = 0x08;
  private static final int FLAG_UNREADY = 0x10;

  private final long id;
  private final Long idObj;
  private final long dateTaken;

  private final int width, height;
  private final long bucketId;

  private int flags;

  private int postRotate;

  private long startTimeUs = -1, endTimeUs = -1, totalDurationUs = -1;
  private double videoWidth, videoHeight;
  private long videoBitrate;
  private int videoFrameRate;

  private boolean isFavorite;

  public ImageGalleryFile (long imageId, String path, long dateTaken, int width, int height, long bucketId, boolean needThumb) {
    super(null, TD.newFile(CURRENT_ID--, Integer.toString(CURRENT_ID), path, 1));
    this.id = imageId;
    this.idObj = imageId;
    this.dateTaken = dateTaken;
    this.width = width;
    this.height = height;
    this.bucketId = bucketId;
    this.flags = needThumb ? FLAG_NEED_THUMB : 0;
  }

  public ImageGalleryFile (ImageGalleryFile source) {
    super(null, source.file);
    this.id = source.id;
    this.idObj = source.idObj;
    this.dateTaken = source.dateTaken;
    this.width = source.width;
    this.height = source.height;
    this.bucketId = source.bucketId;
    this.flags = source.flags;
    this.rotation = source.rotation;
    this.postRotate = source.postRotate;
    this.startTimeUs = source.startTimeUs;
    this.endTimeUs = source.endTimeUs;
    this.totalDurationUs = source.totalDurationUs;
    this.videoWidth = source.videoWidth;
    this.videoHeight = source.videoHeight;
    this.videoBitrate = source.videoBitrate;
    this.videoFrameRate = source.videoFrameRate;
    this.duration = source.duration;
    this.mimeType = source.mimeType;
    // this.caption = source.caption;
    this.isFavorite = source.isFavorite;
    setSize(source.getSize());
  }

  public void setFavorite (boolean favorite) {
    isFavorite = favorite;
  }

  public boolean isFavorite () {
    return isFavorite;
  }

  @Override
  public int compareTo (ImageGalleryFile o) {
    long t1 = getDateTaken();
    long t2 = o.getDateTaken();
    if (t1 != t2)
      return Long.compare(t2, t1);
    return Long.compare(o.getGalleryId(), getGalleryId());
  }

  public int rotateBy90Degrees () {
    this.postRotate = MathUtils.modulo(postRotate - 90, 360);
    return postRotate;
  }

  public void setPostRotate (int rotate) {
    this.postRotate = rotate;
  }

  public int getPostRotate () {
    return postRotate;
  }

  public long getStartTimeUs () {
    return startTimeUs;
  }

  public long getEndTimeUs () {
    return endTimeUs;
  }

  public long getTotalDurationUs () {
    return totalDurationUs;
  }

  public boolean hasTrim () {
    return startTimeUs != -1 && totalDurationUs != -1;
  }

  public boolean setVideoInformation (long totalDurationUs, double width, double height, int frameRate, long bitrate) {
    if (this.totalDurationUs != totalDurationUs || this.videoWidth != width || this.videoHeight != height || this.videoFrameRate != frameRate || this.videoBitrate != bitrate) {
      this.totalDurationUs = totalDurationUs;
      this.videoWidth = width;
      this.videoHeight = height;
      this.videoFrameRate = frameRate;
      this.videoBitrate = bitrate;
      return true;
    }
    return false;
  }

  public double getVideoWidth () {
    return videoWidth;
  }

  public double getVideoHeight () {
    return videoHeight;
  }

  public long getVideoBitrate () {
    return videoBitrate;
  }

  public int getVideoFrameRate () {
    return videoFrameRate;
  }

  public boolean setTrim (long startTimeUs, long endTimeUs, long totalDurationUs) {
    if (this.startTimeUs != startTimeUs || this.endTimeUs != endTimeUs || this.totalDurationUs != totalDurationUs) {
      this.startTimeUs = startTimeUs;
      this.endTimeUs = endTimeUs;
      this.totalDurationUs = totalDurationUs;
      notifyChanged();
      return true;
    }
    return false;
  }

  public boolean toggleMuteVideo () {
    flags ^= FLAG_MUTE_VIDEO;
    return shouldMuteVideo();
  }

  public void setFromCamera () {
    flags |= FLAG_FROM_CAMERA;
  }

  public boolean isFromCamera () {
    return (flags & FLAG_FROM_CAMERA) != 0;
  }

  public void setReady () {
    flags &= ~FLAG_UNREADY;
  }

  public boolean isReady () {
    return (flags & FLAG_UNREADY) == 0;
  }

  public boolean shouldMuteVideo () {
    return (flags & FLAG_MUTE_VIDEO) != 0;
  }

  @Override
  public int getId () {
    return getFilePath().hashCode();
  }

  public boolean needThumb () {
    return (flags & FLAG_NEED_THUMB) != 0;
  }

  public void setNeedThumb (boolean needThumb) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_NEED_THUMB, needThumb);
  }

  public long getDateTaken () {
    return dateTaken;
  }

  public boolean canSendAsFile () {
    if (getSelfDestructType() != null)
      return false;
    if (isVideo()) {
      return VideoGenerationInfo.canSendInOriginalQuality(this);
    } else {
      return PhotoGenerationInfo.isEmpty(this);
    }
  }

  @Override
  public boolean isProbablyRotated () {
    return true;
  }

  @Override
  public int getVisualRotation () {
    return MathUtils.modulo(rotation + postRotate, 360);
  }

  public boolean isVideo () {
    return (flags & FLAG_IS_VIDEO) != 0;
  }

  private long duration;
  private String mimeType;

  public void setIsVideo (long duration, String mimeType) {
    this.flags |= FLAG_IS_VIDEO;
    this.duration = duration;
    this.mimeType = mimeType;
  }

  private TdApi.FormattedText caption;

  public void setCaption (TdApi.FormattedText text) {
    if (Td.isEmpty(text)) {
      this.caption = null;
    } else {
      this.caption = text;
    }
  }

  public boolean canDisableMarkdown () {
    TdApi.FormattedText markdown = getCaption(true, true);
    TdApi.FormattedText noMarkdown = getCaption(true, false);
    return !Td.equalsTo(markdown, noMarkdown, true);
  }

  public TdApi.FormattedText getCaption (boolean obtain, boolean parseMarkdown) {
    if (obtain) {
      if (Td.isEmpty(caption)) {
        return null;
      }
      TdApi.FormattedText result = new TdApi.FormattedText(caption.text, caption.entities);
      if (parseMarkdown)
        Td.parseMarkdown(result);
      return result;
    }
    return caption;
  }

  public String getVideoMimeType () {
    return mimeType;
  }

  public int getVideoDuration (boolean trimmed) {
    return (int) getVideoDuration(trimmed, TimeUnit.SECONDS);
  }

  public long getVideoDuration (boolean trimmed, TimeUnit unit) {
    if (trimmed && hasTrim()) {
      if (endTimeUs == -1) {
        return unit.convert(
          TimeUnit.MILLISECONDS.toMicros(duration) - startTimeUs,
          TimeUnit.MICROSECONDS
        );
      } else {
        return unit.convert(endTimeUs - startTimeUs, TimeUnit.MICROSECONDS);
      }
    }
    return unit.convert(duration, TimeUnit.MILLISECONDS);
  }

  public long getGalleryId () {
    return id;
  }

  public int getWidth () {
    // 0, 90, 180, 270
    return U.isRotated(getVisualRotation()) ? height : width;
  }

  public int getWidthCropRotated () {
    return U.isRotated(getVisualRotationWithCropRotation()) ? height : width;
  }

  public int getHeightCropRotated () {
    return U.isRotated(getVisualRotationWithCropRotation()) ? width : height;
  }

  public int getHeight () {
    return U.isRotated(getVisualRotation()) ? width : height;
  }

  public void getOutputSize (int[] out) {
    int outWidth, outHeight;
    CropState cropState = getCropState();
    if (cropState == null || cropState.isEmpty()) {
      outWidth = getWidth();
      outHeight = getHeight();
    } else {
      if (U.isRotated(getVisualRotation() + cropState.getRotateBy())) {
        outWidth = height;
        outHeight = width;
      } else {
        outWidth = width;
        outHeight = height;
      }
      if (!cropState.isRegionEmpty()) {
        double width = cropState.getRegionWidth();
        double height = cropState.getRegionHeight();

        outWidth *= width;
        outHeight *= height;
      }
    }

    float scale = Math.min((float) PhotoGenerationInfo.SIZE_LIMIT / outWidth, (float) PhotoGenerationInfo.SIZE_LIMIT / outHeight);
    if (scale < 1.0f) {
      outWidth *= scale;
      outHeight *= scale;
    }

    out[0] = outWidth;
    out[1] = outHeight;
  }

  public boolean isScreenshot () {
    String check = getFile().local.path.toLowerCase();
    return /*check.contains("screenshot") || */check.contains("screen");
  }

  @Override
  protected String buildImageKey () {
    return file.local.path + "?" + (startTimeUs > 0 ? startTimeUs : "") + (needThumb() ? "thumb" + id : "");
  }

  public int getVisualRotationWithCropRotation () {
    CropState cropState = getCropState();
    if (cropState != null) {
      return MathUtils.modulo(getVisualRotation() + cropState.getRotateBy(), 360);
    } else {
      return getVisualRotation();
    }
  }

  @Override
  public byte getType () {
    return TYPE_GALLERY;
  }

  private List<File> copies;

  public void trackCopy (File copy) {
    if (copies == null)
      copies = new ArrayList<>();
    copies.add(copy);
  }

  public List<File> copies () {
    return copies;
  }
}
