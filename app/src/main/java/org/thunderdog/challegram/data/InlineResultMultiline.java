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
 * File created on 03/12/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.net.URLDecoder;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class InlineResultMultiline extends InlineResult<TdApi.InlineQueryResult> {
  private String title, description;
  private TdApi.TextEntity[] descriptionEntities;
  private boolean isEmail;
  private String url;

  private final AvatarPlaceholder avatarPlaceholder;

  private static final float AVATAR_PLACEHOLDER_RADIUS = 25f;

  public InlineResultMultiline (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultArticle article) {
    super(context, tdlib, TYPE_ARTICLE, article.id, article);

    this.title = article.title;
    this.description = article.description;
    this.url = article.hideUrl || article.url.isEmpty() ? null : article.url; // ? null : article.url;

    int placeholderColorId = TD.getColorIdForString(article.url.isEmpty() ? article.id : article.url);
    avatarPlaceholder = new AvatarPlaceholder(AVATAR_PLACEHOLDER_RADIUS, new AvatarPlaceholder.Metadata(placeholderColorId, TD.getLetters(title)), null);

    setMediaPreview(MediaPreview.valueOf(tdlib, article.thumbnail, null, Screen.dp(50f), Screen.dp(3f)));
    layoutInternal(Screen.currentWidth());
  }

  public InlineResultMultiline (BaseActivity context, Tdlib tdlib, TdApi.InlineQueryResultGame game) {
    super(context, tdlib, TYPE_GAME, game.id, game);

    this.title = game.game.title;
    this.description = game.game.description;

    int placeholderColorId = TD.getColorIdForString(game.game.shortName);
    avatarPlaceholder = new AvatarPlaceholder(AVATAR_PLACEHOLDER_RADIUS, new AvatarPlaceholder.Metadata(placeholderColorId, TD.getLetters(title)), null);

    setMediaPreview(MediaPreview.valueOf(tdlib, game.game, Screen.dp(50f), Screen.dp(3f)));

    layoutInternal(Screen.currentWidth());
  }

  public InlineResultMultiline (BaseActivity context, Tdlib tdlib, TdApi.Message message) {
    super(context, tdlib, TYPE_ARTICLE, null, null);

    setMessage(message);

    TdApi.FormattedText text = Td.textOrCaption(message.content);
    TdApi.WebPage webPage = message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR ? ((TdApi.MessageText) message.content).webPage : null;

    if (webPage != null) {
      this.title = Strings.any(webPage.title, webPage.document != null ? webPage.document.fileName : null, webPage.audio != null ? webPage.audio.title : null, webPage.siteName);
      this.description = webPage.description.text;
      this.descriptionEntities = webPage.description.entities;
      String urlInText = Td.findUrl(text, webPage.url, true);
      this.url = !StringUtils.isEmpty(urlInText) ? urlInText : webPage.url;
    } else if (text != null) {
      TdApi.TextEntity effectiveEntity = null;
      main: for (TdApi.TextEntity entity : text.entities) {
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
            if (effectiveEntity == null) {
              this.url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
              effectiveEntity = entity;
            }
            break; // Don't break main to find link that is visible in the text
          }
          case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: {
            if (effectiveEntity == null) {
              this.url = Td.substring(text.text, entity);
              this.isEmail = true;
              effectiveEntity = entity;
            }
            break main;
          }
          case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
          case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: {
            // TODO ?
            break;
          }
          case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
            String url = Td.substring(text.text, entity);
            boolean isDomain = StringUtils.isDomain(url);
            if (effectiveEntity == null || !isDomain) {
              this.url = url;
              effectiveEntity = entity;
              if (!isDomain) {
                break main;
              }
            }
            break;
          }
        }
      }
      if (effectiveEntity != null) {
        if (effectiveEntity.type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR) {
          TdApi.FormattedText part1 = effectiveEntity.offset > 0 ? Td.substring(text, 0, effectiveEntity.offset) : null;
          TdApi.FormattedText part2 = effectiveEntity.offset + effectiveEntity.length < text.text.length() ? Td.substring(text, effectiveEntity.offset + effectiveEntity.length) : null;
          TdApi.FormattedText finalText = Td.trim(part1 != null && part2 != null ? Td.concat(part1, new TdApi.FormattedText("…", new TdApi.TextEntity[]{new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeTextUrl(url))}), part2) : part1 != null ? part1 : part2);
          if (finalText != null && !StringUtils.equalsOrBothEmpty(url, finalText.text)) {
            this.description = finalText.text;
            this.descriptionEntities = finalText.entities;
          }
        } else {
          this.description = text.text;
          this.descriptionEntities = text.entities;
        }
      }
    }

    setMediaPreview(MediaPreview.valueOf(tdlib, message, null, Screen.dp(50f), Screen.dp(3f)));

    if (StringUtils.isEmpty(title)) {
      this.title = isEmail ? Lang.getString(R.string.EMail) :
        StringUtils.isEmpty(url) ? Lang.getString(R.string.Link) :
        StringUtils.domainOf(url);
    }
    if (StringUtils.isEmpty(url)) {
      this.url = "";
    }

    int placeholderColorId = TD.getColorIdForString(url);
    avatarPlaceholder = new AvatarPlaceholder(AVATAR_PLACEHOLDER_RADIUS, new AvatarPlaceholder.Metadata(placeholderColorId, TD.getLetters(title)), null);

    layoutInternal(Screen.currentWidth());
  }

  @Override
  protected int getContentHeight () {
    int textHeight = Screen.dp(11f) + Screen.dp(4f);
    boolean hadText = false;
    if (titleWrap != null) {
      textHeight += titleWrap.getHeight();
      hadText = true;
    }
    if (descWrap != null) {
      if (hadText)
        textHeight += Screen.dp(TEXT_PADDING);
      else
        hadText = true;
      textHeight += descWrap.getHeight();
    }
    if (urlWrap != null) {
      if (hadText)
        textHeight += Screen.dp(TEXT_PADDING);
      else
        hadText = true;
      textHeight += urlWrap.getHeight();
    }
    textHeight += Screen.dp(14f);

    return Math.max(Screen.dp(72f), textHeight);
  }

  @Override
  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    if (getMediaPreview() != null) {
      getMediaPreview().requestFiles(receiver, isInvalidate);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (descWrap != null) {
      descWrap.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private TextWrapper titleWrap, descWrap, urlWrap;
  private static final float TEXT_PADDING = 6f;

  @Override
  protected void layoutInternal (int contentWidth) {
    if (titleWrap == null) {
      titleWrap = new TextWrapper(title, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL)
        .setMaxLines(2);
      titleWrap.addTextFlags(Text.FLAG_ALL_BOLD);
      titleWrap.setMaxLines(2);
    }
    if (descWrap == null && !StringUtils.isEmpty(description)) {
      descWrap = new TextWrapper(description, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL)
          .setMaxLines(4);
      if (descriptionEntities != null && descriptionEntities.length > 0) {
        descWrap.setEntities(TextEntity.valueOf(tdlib, new TdApi.FormattedText(description, descriptionEntities), null), (wrapper, text, specificMedia) -> {
          if (descWrap == wrapper) {
            currentViews.performWithViews(view -> {
              if (!text.invalidateMediaContent(((CustomResultView) view).getTextMediaReceiver(), specificMedia)) {
                ((CustomResultView) view).invalidateTextMedia(this);
              }
            });
          }
        });
      }
      descWrap.setViewProvider(currentViews);
      descWrap.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS);
      descWrap.setMaxLines(3);
    }
    if (urlWrap == null && url != null) {
      String displayUrl;
      try {
        displayUrl = URLDecoder.decode(url, "UTF-8");
      } catch (Throwable ignored) {
        displayUrl = url;
      }
      urlWrap = new TextWrapper(displayUrl, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL)
        .setEntities(TextEntity.valueOf(tdlib, displayUrl, new TdApi.TextEntity[] { new TdApi.TextEntity(0, displayUrl.length(), isEmail ? new TdApi.TextEntityTypeEmailAddress() : new TdApi.TextEntityTypeUrl() )}, null), null);
      urlWrap.setMaxLines(2);
      urlWrap.setViewProvider(currentViews);
      urlWrap.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS);
    }

    int availWidth = contentWidth - Screen.dp(11f) * 2 - Screen.dp(50f) - Screen.dp(15f);

    titleWrap.prepare(availWidth);
    if (descWrap != null) {
      descWrap.prepare(availWidth);
    }
    if (urlWrap != null) {
      urlWrap.prepare(availWidth);
    }
  }

  @Override
  public boolean onTouchEvent (View view, MotionEvent e) {
    return (titleWrap != null && titleWrap.onTouchEvent(view, e)) || (descWrap != null && descWrap.onTouchEvent(view, e)) || (urlWrap != null && urlWrap.onTouchEvent(view, e));
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    if (getMediaPreview() != null) {
      getMediaPreview().draw(view, c, receiver, Screen.dp(11f), Screen.dp(11f), 1f);
    } else if (avatarPlaceholder != null) {
      RectF rectF = Paints.getRectF();
      rectF.set(Screen.dp(11f), Screen.dp(11f), Screen.dp(11f) + Screen.dp(50f), Screen.dp(11f) + Screen.dp(50f));
      c.drawRoundRect(rectF, Screen.dp(3f), Screen.dp(3f), Paints.fillingPaint(Theme.getColor(avatarPlaceholder.metadata.colorId)));
      avatarPlaceholder.draw(c, rectF.centerX(), rectF.centerY(), 1f, avatarPlaceholder.getRadius(), false);
    }

    int textX = Screen.dp(11f) + Screen.dp(50f) + Screen.dp(15f);
    int textY = startY + Screen.dp(11f) + Screen.dp(4f);

    boolean hadText = false;
    if (titleWrap != null) {
      titleWrap.draw(c, textX, textY, null, 1f);
      textY += titleWrap.getHeight();
      hadText = true;
    }

    if (descWrap != null) {
      if (hadText)
        textY += Screen.dp(TEXT_PADDING);
      else
        hadText = true;
      descWrap.draw(c, textX, textY, null, 1f, view.getTextMediaReceiver());
      textY += descWrap.getHeight();
    }

    if (urlWrap != null) {
      if (hadText)
        textY += Screen.dp(TEXT_PADDING);
      else
        hadText = true;
      urlWrap.draw(c, textX, textY, null, 1f);
    }
  }

  @Override
  public void onDrawSelectionOver (Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, float anchorTouchX, float anchorTouchY, float selectFactor, String counter, @Nullable SimplestCheckBox checkBox) {
    final int radius = Screen.dp(11f);
    final int x = Screen.dp(11f) + Screen.dp(50f) - radius / 2;
    final int y = Screen.dp(11f) + Screen.dp(50f) - radius / 2;

    SimplestCheckBox.draw(c, x, y, selectFactor, counter, checkBox);

    RectF rectF = Paints.getRectF();

    rectF.set(x - radius, y - radius, x + radius, y + radius);

    c.drawArc(rectF, 135f, 170f * selectFactor, false, Paints.getOuterCheckPaint(ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor())));
  }
}
