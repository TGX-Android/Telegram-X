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
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;

public class PageBlockFile extends PageBlock {
  private final InlineResultCommon result;
  private final TGPlayerController.PlayListBuilder playListBuilder;

  public PageBlockFile (ViewController<?> context, TdApi.PageBlock pageBlock, String url, TGPlayerController.PlayListBuilder builder) {
    super(context, pageBlock);
    this.result = (InlineResultCommon) InlineResult.valueOf(context.context(), context.tdlib(), pageBlock, builder);
    this.playListBuilder = builder;
    if (result == null)
      throw new UnsupportedOperationException(pageBlock.toString());
    if (pageBlock.getConstructor() == TdApi.PageBlockAudio.CONSTRUCTOR) {
      ((InlineResultCommon) result).setIsTrack(false);
    }
  }

  @Override
  public boolean isClickable () {
    return true;
  }

  @Override
  public boolean onClick (View view, boolean isLongPress) {
    if (!isLongPress) {
      context.tdlib().context().player().playPauseMessage(context.tdlib(), result.getPlayPauseMessage(), playListBuilder);
      return true;
    }
    return false;
  }

  public InlineResultCommon getFile () {
    return result;
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_CUSTOM_INLINE;
  }

  @Override
  protected int computeHeight (View view, int width) {
    return 0;
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected int getContentTop () {
    return 0;
  }

  @Override
  protected int getContentHeight () {
    return 0;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {

  }
}
