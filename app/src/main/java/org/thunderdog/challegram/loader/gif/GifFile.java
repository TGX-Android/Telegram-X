/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 29/02/2016 at 22:45
 */
package org.thunderdog.challegram.loader.gif;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.BitwiseUtils;

public class GifFile {
  public static final int TYPE_GIF = 1;
  public static final int TYPE_MPEG4 = 2;
  public static final int TYPE_WEBM = 2;
  public static final int TYPE_TG_LOTTIE = 3;

  public static final int FIT_CENTER = 1;
  public static final int CENTER_CROP = 2;

  public static final int FLAG_ROUND_VIDEO = 1;
  public static final int FLAG_STILL = 1 << 1;
  public static final int FLAG_OPTIMIZE = 1 << 2;
  public static final int FLAG_PLAY_ONCE = 1 << 3;
  public static final int FLAG_UNIQUE = 1 << 4;
  public static final int FLAG_DECODE_LAST_FRAME = 1 << 5;

  protected final Tdlib tdlib;
  protected final TdApi.File file;
  private final int type;
  private int scaleType;
  private int flags;
  private long chatId, messageId;
  private boolean isLooped, isFrozen;
  private int vibrationPattern = Emoji.VIBRATION_PATTERN_NONE;
  private int fitzpatrickType;

  private final long creationTime;

  public GifFile (Tdlib tdlib, TdApi.Animation gif) {
    this(tdlib, gif.animation, "video/mp4".equals(gif.mimeType) ? GifFile.TYPE_MPEG4 : "image/gif".equals(gif.mimeType) ? GifFile.TYPE_GIF : TD.isAnimatedSticker(gif.mimeType) ? GifFile.TYPE_TG_LOTTIE : 0);
  }

  public GifFile (Tdlib tdlib, TdApi.File file, int type) {
    this.tdlib = tdlib;
    this.file = file;
    this.type = type;
    this.creationTime = SystemClock.uptimeMillis();
  }

  public GifFile (Tdlib tdlib, TdApi.Sticker sticker) {
    this(tdlib, sticker.sticker, sticker.format);
  }

  public GifFile (Tdlib tdlib, TdApi.File file, TdApi.StickerFormat format) {
    this(tdlib, file, toFileType(format));
  }

  public boolean isLottie () {
    return type == TYPE_TG_LOTTIE;
  }

  private static int toFileType (TdApi.StickerFormat format) {
    switch (format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
        return TYPE_TG_LOTTIE;
      case TdApi.StickerFormatWebm.CONSTRUCTOR:
        return TYPE_MPEG4;
      case TdApi.StickerFormatWebp.CONSTRUCTOR:
        break;
    }
    throw new IllegalArgumentException(format.toString());
  }

  public int getFitzpatrickType () {
    return fitzpatrickType;
  }

  public void setFitzpatrickType (int fitzpatrickType) {
    this.fitzpatrickType = fitzpatrickType;
  }

  public interface FrameChangeListener {
    void onFrameChanged (GifFile file, double frameNo, double frameDelta);
  }

  private long totalFrameCount;

  public void setTotalFrameCount (long totalFrameCount) {
    this.totalFrameCount = totalFrameCount;
  }

  public boolean hasFrame (long frameNo) {
    return frameNo >= 0 && frameNo < totalFrameCount;
  }

  private FrameChangeListener frameChangeListener;

  public void setFrameChangeListener (FrameChangeListener listener) {
    this.frameChangeListener = listener;
  }

  public void onFrameChange (double frameNo, double frameDelta) {
    if (frameChangeListener != null) {
      frameChangeListener.onFrameChanged(this, frameNo, frameDelta);
    }
  }

  public boolean setLooped (boolean isLooped) {
    if (this.isLooped != isLooped) {
      this.isLooped = isLooped;
      if (isLooped) {
        this.vibrationPattern = Emoji.VIBRATION_PATTERN_NONE;
        onLoop();
      }
      return true;
    }
    return false;
  }

  public boolean isFrozen () {
    return isFrozen;
  }

  public void setFrozen (boolean isFrozen) {
    this.isFrozen = isFrozen;
  }

  public int getVibrationPattern () {
    return vibrationPattern;
  }

  public boolean setVibrationPattern (int vibrationPattern) {
    if (this.vibrationPattern != vibrationPattern) {
      this.vibrationPattern = vibrationPattern;
      return true;
    }
    return false;
  }

  public void setOptimize (boolean needOptimize) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_OPTIMIZE, needOptimize);
  }

  public boolean needOptimize () {
    return BitwiseUtils.getFlag(flags, FLAG_OPTIMIZE);
  }

  public void setPlayOnce (boolean playOnce) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_PLAY_ONCE, playOnce);
  }

  public void setPlayOnce () {
    setPlayOnce(Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP));
  }

  public boolean isPlayOnce () {
    return BitwiseUtils.getFlag(flags, FLAG_PLAY_ONCE);
  }

  public void setDecodeLastFrame (boolean decodeLastFrame) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_DECODE_LAST_FRAME, decodeLastFrame);
  }

  private List<Runnable> loopListeners;

  public void addLoopListener (Runnable callback) {
    if (hasLooped() && isPlayOnce()) {
      callback.run();
      return;
    }
    if (loopListeners == null) {
      loopListeners = new ArrayList<>();
    }
    loopListeners.add(callback);
  }

  public void onLoop () {
    if (loopListeners != null) {
      tdlib.ui().post(() -> {
        if (loopListeners != null) {
          for (Runnable callback : loopListeners) {
            callback.run();
          }
          loopListeners = null;
        }
      });
    }
  }

  public boolean needDecodeLastFrame () {
    return BitwiseUtils.getFlag(flags, FLAG_DECODE_LAST_FRAME);
  }

  public boolean hasLooped () {
    return isLooped;
  }

  public void setUnique (boolean isUnique) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_UNIQUE, isUnique);
  }

  public boolean isUnique () {
    return BitwiseUtils.getFlag(flags, FLAG_UNIQUE);
  }

  public boolean isStill () {
    return (flags & FLAG_STILL) != 0;
  }

  public void setIsStill (boolean isStill) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_STILL, isStill);
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  public void setScaleType (int scaleType) {
    this.scaleType = scaleType;
  }

  public void setSize (int size) {
    // todo?
  }

  public TdApi.File getFile () {
    return file;
  }

  public String getFilePath () {
    return file.local != null ? file.local.path : null;
  }

  public int getFileId () {
    return file.id;
  }

  /*public String getPersistentId () {
    return file.remote.id;
  }*/

  public int getGifType () {
    return type;
  }

  public int getScaleType () {
    return scaleType;
  }

  private String key;

  @Override
  public int hashCode () {
    return toString().hashCode();
  }

  protected final StringBuilder makeCommonKey (StringBuilder b) {
    b.append(tdlib != null ? tdlib.id() : TdlibAccount.NO_ID);
    b.append('_');
    b.append(getFileId());
    if (flags != 0) {
      b.append(',');
      b.append(flags);
    }
    if (fitzpatrickType != 0) {
      b.append(",f");
      b.append(fitzpatrickType);
    }
    if (isUnique() || isPlayOnce()) {
      b.append(',');
      b.append(creationTime);
    }
    return b;
  }

  protected String makeGifKey () {
    return makeCommonKey(new StringBuilder()).toString();
  }

  @Override
  @NonNull
  public final String toString () {
    return key != null ? key : (key = makeGifKey());
  }

  // Round video

  public void setIsRoundVideo (long chatId, long messageId) {
    this.flags |= FLAG_ROUND_VIDEO;
    this.chatId = chatId;
    this.messageId = messageId;
  }

  public boolean isRoundVideo () {
    return (flags & FLAG_ROUND_VIDEO) != 0;
  }

  public long getChatId () {
    return chatId;
  }

  public long getMessageId () {
    return messageId;
  }
}
