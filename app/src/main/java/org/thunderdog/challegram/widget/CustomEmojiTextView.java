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
 * File created on 08/05/2024
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.ComplexMediaHolder;
import org.thunderdog.challegram.data.ComplexMediaItem;
import org.thunderdog.challegram.emoji.CustomEmojiSurfaceProvider;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.telegram.Tdlib;

@SuppressWarnings("ViewConstructor")
public class CustomEmojiTextView extends EmojiTextView implements CustomEmojiSurfaceProvider {
  private final @Nullable Tdlib tdlib;
  private final RefreshRateLimiter refreshRateLimiter;
  private final ComplexMediaHolder<EmojiSpan> mediaHolder;

  public CustomEmojiTextView (Context context, @Nullable Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.refreshRateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    this.mediaHolder = new ComplexMediaHolder<>(this);
    this.mediaHolder.setUpdateListener((usages, displayMediaKey) ->
      refreshRateLimiter.invalidate()
    );
  }

  @Override
  public EmojiSpan onCreateNewSpan (CharSequence emojiCode, EmojiInfo info, long customEmojiId) {
    if (tdlib != null) {
      return Emoji.instance().newCustomSpan(emojiCode, info, this, tdlib, customEmojiId);
    } else {
      return null;
    }
  }

  @Override
  public void onInvalidateSpan (EmojiSpan span, boolean requiresLayoutUpdate) {
    if (requiresLayoutUpdate) {
      EmojiUpdater.invalidateEmojiSpan(this, span);
    }
    invalidate();
  }

  @Override
  public ComplexReceiver provideComplexReceiverForSpan (EmojiSpan span) {
    return mediaHolder.receiver;
  }

  @Override
  public int getDuplicateMediaItemCount (EmojiSpan span, ComplexMediaItem mediaItem) {
    return mediaHolder.getMediaUsageCount(mediaItem);
  }

  @Override
  public long attachToReceivers (EmojiSpan span, ComplexMediaItem complexMediaItem) {
    return mediaHolder.attachMediaUsage(complexMediaItem, span);
  }

  @Override
  public void detachFromReceivers (EmojiSpan span, ComplexMediaItem complexMediaItem, long mediaKey) {
    mediaHolder.detachMediaUsage(complexMediaItem, span, mediaKey);
  }

  @Override
  public void performDestroy () {
    super.performDestroy();
    mediaHolder.performDestroy();
  }

  private void drawEmojiOverlay (Canvas c) {
    Layout layout = getLayout();
    for (EmojiSpan span : mediaHolder.defaultLayerUsages()) {
      span.onOverlayDraw(c, this, layout);
    }
    for (EmojiSpan span : mediaHolder.topLayerUsages()) {
      span.onOverlayDraw(c, this, layout);
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
    drawEmojiOverlay(canvas);
  }
}
