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
 * File created on 05/09/2022, 17:31.
 */

package org.thunderdog.challegram.emoji;

import android.graphics.Paint;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;
import android.text.style.CharacterStyle;

public final class CustomEmojiId extends CharacterStyle implements EmojiSpan, Parcelable {
  public final long customEmojiId;
  public final boolean disableAnimations;

  public CustomEmojiId (long customEmojiId, boolean disableAnimations) {
    this.customEmojiId = customEmojiId;
    this.disableAnimations = disableAnimations;
  }

  public CustomEmojiId (Parcel source) {
    this(source.readLong(), (int) source.readByte() == 1);
  }

  // EmojiSpan

  @Override
  public long getCustomEmojiId () {
    return customEmojiId;
  }

  @Override
  public boolean forceDisableAnimations () {
    return disableAnimations;
  }

  @Override
  public boolean isCustomEmoji () {
    return true;
  }

  @Override
  public EmojiSpan toBuiltInEmojiSpan () {
    throw new UnsupportedOperationException();
  }

  private final EmojiSize size = new EmojiSize();

  @Override
  public int getRawSize (Paint paint) {
    size.initialize(paint, null, true);
    return size.getSize();
  }

  // CharacterStyle

  @Override
  public void updateDrawState (TextPaint tp) {
    // Do nothing.
  }

  // Parcelable

  @Override
  public void writeToParcel (Parcel dest, int flags) {
    dest.writeLong(customEmojiId);
  }

  @Override
  public int describeContents () {
    return 0;
  }

  public static final Parcelable.Creator<CustomEmojiId> CREATOR = new Parcelable.Creator<>() {
    @Override
    public CustomEmojiId createFromParcel (Parcel source) {
      return new CustomEmojiId(source);
    }

    @Override
    public CustomEmojiId[] newArray (int size) {
      return new CustomEmojiId[0];
    }
  };
}
