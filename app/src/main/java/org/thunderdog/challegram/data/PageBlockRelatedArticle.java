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
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class PageBlockRelatedArticle extends PageBlock {
  private final TdApi.PageBlockRelatedArticle article;

  private TextWrapper title, description, info;
  private ImageFile miniThumbnail, preview, photo;
  @Nullable
  private final TdlibUi.UrlOpenParameters openParameters;

  public PageBlockRelatedArticle (ViewController<?> context, TdApi.PageBlockRelatedArticles articles, TdApi.PageBlockRelatedArticle article, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, articles);
    this.article = article;
    this.openParameters = openParameters;

    if (!StringUtils.isEmpty(article.title)) {
      title = new TextWrapper(article.title, PageBlockRichText.getParagraphProvider(), TextColorSets.InstantView.NORMAL)
        .setMaxLines(3)
        .addTextFlags(Text.FLAG_ALL_BOLD | Text.FLAG_ARTICLE);
    }
    if (!StringUtils.isEmpty(article.description)) {
      description = new TextWrapper(article.description, PageBlockRichText.getCaptionProvider(), TextColorSets.InstantView.NORMAL)
        .setMaxLines(3)
        .addTextFlags(Text.FLAG_ARTICLE);
    }
    String info;
    if (article.publishDate != 0 && !StringUtils.isEmptyOrBlank(article.author)) {
      info = Lang.getString(R.string.format_ivRelatedInfo, PageBlockRichText.buildAgo(context.tdlib(), article.publishDate), article.author);
    } else if (article.publishDate != 0) {
      info = PageBlockRichText.buildAgo(context.tdlib(), article.publishDate);
    } else if (!StringUtils.isEmptyOrBlank(article.author)) {
      info = article.author;
    } else {
      info = null;
    }
    if (!StringUtils.isEmpty(info)) {
      this.info = new TextWrapper(info, PageBlockRichText.getCaptionProvider(), TextColorSets.InstantView.CAPTION);
    }
    if (article.photo != null) {
      if (article.photo.minithumbnail != null) {
        miniThumbnail = new ImageFileLocal(article.photo.minithumbnail);
        miniThumbnail.setScaleType(ImageFile.CENTER_CROP);
        miniThumbnail.setDecodeSquare(true);
      }
      TdApi.PhotoSize size = Td.findSmallest(article.photo.sizes);
      if (size != null) {
        preview = new ImageFile(context.tdlib(), size.photo);
        preview.setScaleType(ImageFile.CENTER_CROP);
        preview.setDecodeSquare(true);
        preview.setSize(Screen.dp(50f));
        if (Math.max(size.width, size.height) <= 320) {
          photo = new ImageFile(context.tdlib(), size.photo);
          photo.setScaleType(ImageFile.CENTER_CROP);
          photo.setDecodeSquare(true);
          photo.setSize(Screen.dp(50f));
        }
      }
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.requestFile(miniThumbnail, preview);
  }

  @Override
  public int getImageContentRadius () {
    return Screen.dp(3f);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(photo);
  }

  @Override
  public int getRelatedViewType () {
    return article.photo != null ? ListItem.TYPE_PAGE_BLOCK_MEDIA : ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int computeHeight (View view, int width) {
    int availLineCount = 3;
    int maxWidth = width - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) * 2;
    if (article.photo != null) {
      maxWidth -= Screen.dp(50f) + Screen.dp(12f);
    }
    if (title != null) {
      title.get(maxWidth);
      availLineCount -= title.getLineCount();
    }
    if (description != null) {
      description.setMaxLines(availLineCount);
      if (availLineCount > 0)
        description.get(maxWidth);
    }
    if (info != null) {
      info.get(maxWidth);
    }
    return Math.max(article.photo != null ? Screen.dp(SPACING_START) * 2 + Screen.dp(50f) : 0, getContentHeight() + getContentTop());
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return (title != null && title.onTouchEvent(view, e)) || (description != null && description.onTouchEvent(view, e)) || (info != null && info.onTouchEvent(view, e));
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(SPACING_START);
  }

  @Override
  public boolean isClickable () {
    return true;
  }

  @Override
  public boolean onClick (View view, boolean isLongPress) {
    if (isLongPress) {
      context.tdlib().ui().openUrlOptions(context, article.url, new TdlibUi.UrlOpenParameters(openParameters).forceInstantView());
    } else {
      context.tdlib().ui().openUrl(context, article.url, new TdlibUi.UrlOpenParameters(openParameters).forceInstantView());
    }
    return true;
  }

  private static final float SPACING = 8f, SPACING_START = 12f;

  @Override
  protected int getContentHeight () {
    int totalHeight = 0;
    if (title != null) {
      totalHeight += title.getHeight() + Screen.dp(SPACING);
    }
    if (description != null && description.getMaxLines() > 0) {
      totalHeight += description.getHeight() + Screen.dp(SPACING);
    }
    if (info != null) {
      totalHeight += info.getHeight() + Screen.dp(SPACING);
    }
    if (totalHeight > 0) {
      totalHeight -= Screen.dp(SPACING);
    }
    totalHeight += Screen.dp(SPACING_START);
    return totalHeight;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) {
    int textX = Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
    int textY = Screen.dp(SPACING_START);
    if (title != null) {
      title.draw(c, textX, textY);
      textY += title.getHeight() + Screen.dp(SPACING);
    }
    if (description != null && description.getMaxLines() > 0) {
      description.draw(c, textX, textY);
      textY += description.getHeight() + Screen.dp(SPACING);
    }
    if (info != null) {
      info.draw(c, textX, textY);
    }

    if (preview != null) {
      int viewWidth = view.getMeasuredWidth();
      int left = viewWidth - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) - Screen.dp(50f);
      int top = getContentTop();
      int right = viewWidth - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
      int bottom = getContentTop() + Screen.dp(50f);
      if (receiver.needPlaceholder()) {
        preview.setBounds(left, top, right, bottom);
        if (preview.needPlaceholder()) {
          preview.drawPlaceholderRounded(c, getImageContentRadius());
        }
        preview.draw(c);
      }
      receiver.setBounds(left, top, right, bottom);
      receiver.draw(c);
    }
  }
}
