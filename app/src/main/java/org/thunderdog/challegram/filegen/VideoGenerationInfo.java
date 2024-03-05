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
 * File created on 14/12/2016
 */
package org.thunderdog.challegram.filegen;

import android.os.Build;

import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.crop.CropStateParser;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.StringUtils;

public class VideoGenerationInfo extends GenerationInfo implements AbstractVideoGenerationInfo {

  public VideoGenerationInfo (long generationId, String originalPath, String destinationPath, String conversion) {
    super(generationId, originalPath, destinationPath, conversion, true);
    parseConversion(this, conversion.substring(GenerationInfo.TYPE_VIDEO.length()));
  }

  private int sourceFileId;
  private boolean needMute;
  private int rotate;
  private long startTime = -1, endTime = -1;
  private boolean noTranscoding;
  private @Nullable CropState cropState;
  private Settings.VideoLimit videoLimit;

  public static void parseConversion (AbstractVideoGenerationInfo out, String conversion) {
    String[] args = conversion.split(",", -1);
    boolean needMute = StringUtils.parseInt(args[0]) == 1;
    int rotate = 0;
    long startTime = -1, endTime = -1;
    int mostMajor = Settings.DEFAULT_VIDEO_LIMIT, mostMinor = Settings.DEFAULT_VIDEO_LIMIT;
    int sourceId = 0;
    boolean noTranscoding = false;
    long bitrate = DefaultVideoStrategy.BITRATE_UNKNOWN;
    int frameRate = Settings.DEFAULT_FRAME_RATE;
    CropState cropState = null;
    int argIndex = 0;
    for (String arg : args) {
      if (arg.startsWith(PREFIX_ROTATE)) {
        rotate = StringUtils.parseInt(arg.substring(PREFIX_ROTATE.length()));
      } else if (arg.startsWith(PREFIX_QUALITY)) {
        String[] resolution = arg.substring(PREFIX_QUALITY.length()).split("x");
        int a = StringUtils.parseInt(resolution[0]);
        int b = StringUtils.parseInt(resolution[1]);
        mostMajor = Math.max(a, b);
        mostMinor = Math.min(a, b);
      } else if (arg.startsWith(PREFIX_BITRATE)) {
        bitrate = StringUtils.parseLong(arg.substring(PREFIX_BITRATE.length()));
      } else if (arg.startsWith(PREFIX_FRAME_RATE)) {
        frameRate = StringUtils.parseInt(arg.substring(PREFIX_FRAME_RATE.length()));
      } else if (arg.startsWith(PREFIX_START)) {
        startTime = StringUtils.parseLong(arg.substring(PREFIX_START.length()));
      } else if (arg.startsWith(PREFIX_END)) {
        endTime = StringUtils.parseLong(arg.substring(PREFIX_END.length()));
      } else if (arg.startsWith(PREFIX_SOURCE_FILE_ID)) {
        sourceId = StringUtils.parseInt(arg.substring(PREFIX_SOURCE_FILE_ID.length()));
      } else if (arg.startsWith(PREFIX_NO_TRANSCODING)) {
        noTranscoding = StringUtils.parseInt(arg.substring(PREFIX_NO_TRANSCODING.length())) == 1;
      } else if (arg.startsWith(PREFIX_CROP)) {
        String in = arg.substring(PREFIX_CROP.length());
        cropState = CropStateParser.parse(in);
      } else if (argIndex != 0 && !(arg.startsWith(PREFIX_RANDOM) || arg.startsWith(PREFIX_LAST_MODIFIED))) {
        Log.w("Unknown video conversion argument: %s, full: %s", arg, conversion);
      }
      argIndex++;
    }
    out.setVideoGenerationInfo(sourceId,
      needMute,
      new Settings.VideoLimit(new Settings.VideoSize(mostMajor, mostMinor), frameRate, bitrate),
      rotate,
      startTime, endTime,
      noTranscoding,
      cropState
    );
  }

  @Override
  public void setVideoGenerationInfo (int sourceFileId, boolean needMute, Settings.VideoLimit videoLimit, int rotate, long startTime, long endTime, boolean noTranscoding, @Nullable CropState cropState) {
    this.sourceFileId = sourceFileId;
    this.needMute = needMute;
    this.videoLimit = videoLimit;
    this.rotate = rotate;
    this.startTime = startTime;
    this.endTime = endTime;
    this.noTranscoding = noTranscoding;
    this.cropState = cropState;
  }

  public Settings.VideoLimit getVideoLimit () {
    return videoLimit;
  }

  public boolean needMute () {
    return needMute;
  }

  public int getRotate () {
    return rotate;
  }

  public int getSourceFileId () {
    return sourceFileId;
  }

  public long getStartTimeUs () {
    return startTime;
  }

  public long getEndTimeUs () {
    return endTime;
  }

  public boolean needTrim () {
    return startTime != -1;
  }

  public boolean hasCrop () {
    return cropState != null && !cropState.isEmpty();
  }

  @Nullable
  public CropState getCrop () {
    return cropState;
  }

  public boolean disableTranscoding () {
    return noTranscoding;
  }

  public static TdApi.InputFileGenerated newFile (String path, @Nullable ImageGalleryFile file, boolean noTranscoding) {
    return new TdApi.InputFileGenerated(path, makeConversion(path, file, noTranscoding), 0);
  }

  private static String makeConversion (String path, @Nullable ImageGalleryFile file, boolean noTranscoding) {
    if (file != null) {
      return makeConversion(0, file.shouldMuteVideo(), file.getPostRotate(), file.getStartTimeUs(), file.getEndTimeUs(), noTranscoding, file.getCropState(), lastModified(path));
    } else {
      return makeConversion(0, false, 0, -1, -1, noTranscoding, null, lastModified(path));
    }
  }

  public static boolean isEmpty (ImageGalleryFile file) {
    return file != null && !file.shouldMuteVideo() && file.getPostRotate() == 0 && !file.hasTrim() && !file.hasCrop();
  }

  public boolean canTakeSimplePath () {
    return !hasCrop();
  }

  private static boolean isSupportedOutputFormat (ImageGalleryFile file, String mimeType) {
    // TODO: video/x-matroska + api-independent detection
    return !StringUtils.isEmpty(mimeType) && (
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && "video/mp4".equals(mimeType)) ||
        (file.getPostRotate() == 0 && (
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && "video/webm".equals(mimeType)) ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && "video/3gpp".equals(mimeType)) ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && "video/ogg".equals(mimeType))
    )));
  }

  public static boolean canSendInOriginalQuality (ImageGalleryFile file) {
    if (file == null) {
      return false;
    }
    if (Config.MODERN_VIDEO_TRANSCODING_ENABLED) {
      return true;
    }
    if (file.hasCrop()) {
      return false;
    }
    if (!(file.hasTrim() || file.getPostRotate() != 0 || file.shouldMuteVideo())) {
      return true;
    }
    String mimeType = TGMimeType.mimeTypeForExtension(U.getExtension(file.getFilePath()));
    return isSupportedOutputFormat(file, mimeType);
  }

  public static String makeConversion (int sourceFileId, boolean mute, int postRotate, long startTimeUs, long endTimeUs, boolean noTranscoding, @Nullable CropState cropState, long lastModified) {
    Settings.VideoLimit videoLimit = Settings.instance().getPreferredVideoLimit();
    return makeConversion(sourceFileId, mute, videoLimit, postRotate, startTimeUs, endTimeUs, noTranscoding, cropState, lastModified);
  }

  public static String makeConversion (int sourceFileId, boolean mute, Settings.VideoLimit videoLimit, int postRotate, long startTimeUs, long endTimeUs, boolean noTranscoding, @Nullable CropState cropState, long lastModified) {
    StringBuilder b = new StringBuilder(TYPE_VIDEO);
    b.append(mute ? 1 : 0);
    if (postRotate != 0) {
      b.append(',');
      b.append(PREFIX_ROTATE);
      b.append(postRotate);
    }
    if (videoLimit != null && !videoLimit.isDefault()) {
      b.append(',');
      b.append(PREFIX_QUALITY);
      b.append(videoLimit.size.majorSize).append('x').append(videoLimit.size.minorSize);
      if (videoLimit.fps != Settings.DEFAULT_FRAME_RATE) {
        b.append(',');
        b.append(PREFIX_FRAME_RATE);
        b.append(videoLimit.fps);
      }
      if (videoLimit.bitrate != DefaultVideoStrategy.BITRATE_UNKNOWN) {
        b.append(',');
        b.append(PREFIX_BITRATE);
        b.append(videoLimit.bitrate);
      }
    }
    if (startTimeUs != -1) {
      b.append(',');
      b.append(PREFIX_START);
      b.append(startTimeUs);
    }
    if (endTimeUs != -1) {
      b.append(',');
      b.append(PREFIX_END);
      b.append(endTimeUs);
    }
    if (sourceFileId != 0) {
      b.append(',');
      b.append(PREFIX_SOURCE_FILE_ID);
      b.append(sourceFileId);
    }
    if (noTranscoding) {
      b.append(',');
      b.append(PREFIX_NO_TRANSCODING);
      b.append('1');
    }
    if (cropState != null && !cropState.isEmpty()) {
      b.append(',');
      b.append(PREFIX_CROP);
      b.append(CropStateParser.toParsableString(cropState));
    }
    if (lastModified != 0) {
      b.append(',');
      b.append(PREFIX_LAST_MODIFIED);
      b.append(lastModified);
    }
    if (BuildConfig.DEBUG) {
      b.append(',');
      b.append(PREFIX_RANDOM);
      b.append(Math.random());
    }
    return b.toString();
  }
}
