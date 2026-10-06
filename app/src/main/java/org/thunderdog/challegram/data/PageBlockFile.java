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
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;

public class PageBlockFile extends PageBlock implements me.vkryl.core.lambda.Destroyable {
  private final InlineResultCommon result;
  private final TGPlayerController.PlayListBuilder playListBuilder;
  private FileComponent articleFile;

  public void setArticleMessage (TGMessageArticle message) {
    result.setMessage(message.getMessage());
    if (block instanceof TdApi.PageBlockDocument) {
      articleFile = new FileComponent(message, message.getMessage(), ((TdApi.PageBlockDocument) block).document);
      articleFile.setViewProvider(currentViews);
    }
  }

  public PageBlockFile (ViewController<?> context, TdApi.PageBlock pageBlock, int quoteLevel, String url, TGPlayerController.PlayListBuilder builder) {
    super(context, pageBlock, quoteLevel);
    this.result = (InlineResultCommon) InlineResult.valueOf(context.context(), context.tdlib(), pageBlock, builder);
    this.playListBuilder = builder;
    if (result == null)
      throw new UnsupportedOperationException(pageBlock.toString());
    if (pageBlock.getConstructor() == TdApi.PageBlockAudio.CONSTRUCTOR) {
      result.setIsTrack(false);
    }
  }

  @Override
  public boolean isClickable () {
    return true;
  }

  @Override
  public boolean onClick (View view, boolean isLongPress) {
    if (!isLongPress && block.getConstructor() != TdApi.PageBlockDocument.CONSTRUCTOR) {
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
    return articleFile != null ? ListItem.TYPE_PAGE_BLOCK_MEDIA : ListItem.TYPE_CUSTOM_INLINE;
  }

  @Override
  protected int computeHeight (View view, int width) {
    if (articleFile != null) {
      articleFile.buildLayout(width);
      return articleFile.getHeight();
    }
    result.layout(width, null);
    return result.getHeight();
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return articleFile != null && articleFile.onTouchEvent(view, e);
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
    if (articleFile != null) articleFile.draw(view, c, 0, 0, preview, receiver, 0, 0, 1f, 0f);
  }

  @Override public void requestPreview (DoubleImageReceiver receiver) { if (articleFile != null) articleFile.requestPreview(receiver); }
  @Override public void requestImage (ImageReceiver receiver) { if (articleFile != null) articleFile.requestContent(receiver); }
  @Override public void performDestroy () { if (articleFile != null) { articleFile.performDestroy(); articleFile = null; } }
}
