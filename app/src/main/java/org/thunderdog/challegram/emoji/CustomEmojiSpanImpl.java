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
 * File created on 05/09/2022, 16:39.
 */

package org.thunderdog.challegram.emoji;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.Layout;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.data.ComplexMediaItem;
import org.thunderdog.challegram.data.ComplexMediaItemCustomEmoji;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextMedia;

import me.vkryl.core.lambda.Destroyable;

class CustomEmojiSpanImpl extends EmojiSpanImpl implements TdlibEmojiManager.Watcher, Destroyable {
  private final CustomEmojiSurfaceProvider surfaceProvider;
  private final Tdlib tdlib;
  private final long customEmojiId;
  @Nullable
  private TdlibEmojiManager.Entry customEmoji;
  private boolean emojiRequested;
  private boolean isDestroyed;

  private final Rect drawRect = new Rect();

  public CustomEmojiSpanImpl (@Nullable EmojiInfo info, CustomEmojiSurfaceProvider surfaceProvider, Tdlib tdlib, long customEmojiId) {
    super(info);
    this.surfaceProvider = surfaceProvider;
    this.tdlib = tdlib;
    this.customEmojiId = customEmojiId;
    if (customEmojiId != 0) {
      setCustomEmoji(tdlib.emoji().findOrPostponeRequest(customEmojiId, this));
    }
  }

  @Override
  public boolean belongsToSurface (CustomEmojiSurfaceProvider customEmojiSurfaceProvider) {
    return this.surfaceProvider == customEmojiSurfaceProvider && !isDestroyed;
  }

  @Override
  public boolean isCustomEmoji () {
    return customEmoji == null || !customEmoji.isNotFound();
  }

  @Override
  public long getCustomEmojiId () {
    return customEmojiId;
  }

  @Override
  public void performDestroy () {
    if (isDestroyed)
      return;
    isDestroyed = true;
    tdlib.emoji().forgetWatcher(customEmojiId, this);
    prepareCustomEmoji(0);
  }

  @UiThread
  private void setCustomEmoji (@Nullable TdlibEmojiManager.Entry customEmoji) {
    if (this.customEmoji == customEmoji)
      return;
    this.customEmoji = customEmoji;
    if (mSize != -1) {
      prepareCustomEmoji(mSize);
      surfaceProvider.onInvalidateSpan(this, customEmoji != null && customEmoji.isNotFound());
    }
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    tdlib.ui().post(() -> {
      if (!isDestroyed) {
        setCustomEmoji(entry);
      }
    });
  }

  @Override
  protected void drawEmoji (Canvas c, float centerX, float centerY, int emojiSize) {
    int left = (int) (centerX - emojiSize / 2f);
    int top = (int) (centerY - emojiSize / 2f);
    int right = left + emojiSize;
    int bottom = top + emojiSize;
    drawRect.set(left, top, right, bottom);
    prepareCustomEmoji(emojiSize);
    if (customEmoji != null && customEmoji.isNotFound()) {
      super.drawEmoji(c, drawRect.centerX(), drawRect.centerY(), mSize);
    }
  }

  private void drawCustomEmoji (Canvas c) {
    if (customEmoji == null || customEmoji.isNotFound()) {
      return;
    }

    //noinspection ConstantConditions
    float scale = TextMedia.getScale(customEmoji.value, customEmojiSize);
    boolean needScale = scale != 1f;

    int restoreToCount;
    if (needScale) {
      restoreToCount = Views.save(c);
      c.scale(scale, scale, drawRect.centerX(), drawRect.centerY());
    } else {
      restoreToCount = -1;
    }
    ComplexReceiver receiver = surfaceProvider.provideComplexReceiverForSpan(this);
    boolean haveDuplicateMedia = surfaceProvider.getDuplicateMediaItemCount(this, mediaItem) > 1;
    mediaItem.draw(c,
      drawRect,
      receiver,
      attachedToMediaKey,
      haveDuplicateMedia
    );
    if (needScale) {
      Views.restore(c, restoreToCount);
    }
  }

  @Override
  public void onOverlayDraw (Canvas c, View view, Layout layout) {
    if (customEmoji == null || customEmoji.isNotFound() || isDestroyed) {
      return;
    }
    if (drawRect.left == drawRect.right || drawRect.top == drawRect.bottom) {
      return; // force invalidate()?
    }
    if (customEmojiSize != mSize && mSize > 0) {
      prepareCustomEmoji(mSize);
    }
    int paddingLeft = view.getPaddingLeft();
    int paddingTop = view.getPaddingTop();
    boolean translate = paddingLeft != 0 || paddingTop != 0;
    if (translate) {
      drawRect.offset(paddingLeft, paddingTop);
    }
    drawCustomEmoji(c);
    if (translate) {
      drawRect.offset(-paddingLeft, -paddingTop);
    }
  }

  private int customEmojiSize;
  private long attachedToMediaKey = -1;

  @Override
  public void requestCustomEmoji (ComplexReceiver receiver, int mediaKey) {
    if (isDestroyed)
      throw new IllegalStateException();
    if (this.attachedToMediaKey != mediaKey)
      throw new IllegalArgumentException();
    if (mediaItem != null) {
      mediaItem.requestComplexMedia(receiver, mediaKey);
    } else {
      receiver.clearReceivers(mediaKey);
      requestCustomEmoji();
    }
  }

  private void requestCustomEmoji () {
    if (customEmoji == null && !emojiRequested) {
      emojiRequested = true;
      tdlib.emoji().performPostponedRequestsDelayed();
    }
  }

  private ComplexMediaItem mediaItem;

  private void prepareCustomEmoji (int size) {
    if (customEmojiSize == size) {
      return;
    }
    if (mediaItem != null) {
      surfaceProvider.detachFromReceivers(this, mediaItem, attachedToMediaKey);
      mediaItem = null;
      attachedToMediaKey = -1;
      customEmojiSize = 0;
    }
    if (!isDestroyed && size > 0) {
      if (customEmoji == null) {
        requestCustomEmoji();
      } else if (!customEmoji.isNotFound()) {
        customEmojiSize = size;
        mediaItem = new ComplexMediaItemCustomEmoji(tdlib, customEmoji.value, size);
        attachedToMediaKey = surfaceProvider.attachToReceivers(this, mediaItem);
      }
    }
  }
}
