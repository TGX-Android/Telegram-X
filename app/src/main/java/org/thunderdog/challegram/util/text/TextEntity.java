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
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.util.text;

import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

public abstract class TextEntity {
  public static final int TYPE_MESSAGE_ENTITY = 0;
  public static final int TYPE_CUSTOM = 1;

  protected final @Nullable Tdlib tdlib;
  protected final @Nullable TdlibUi.UrlOpenParameters openParameters;
  protected final boolean needFakeBold;
  protected final int start, end;

  private Object tag;

  public TextEntity (@Nullable Tdlib tdlib, int start, int end, boolean needFakeBold, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    this.tdlib = tdlib;
    this.openParameters = openParameters;
    this.needFakeBold = needFakeBold;
    this.start = start;
    this.end = end;
  }

  public Object getTag () {
    return tag;
  }

  public TextEntity setTag (Object tag) {
    this.tag = tag;
    return this;
  }

  @NonNull
  public final TdlibUi.UrlOpenParameters openParameters (View view, Text text, TextPart part) {
    if (this.openParameters != null && this.openParameters.tooltip != null)
      return this.openParameters;
    TooltipOverlayView.TooltipBuilder b = part.newTooltipBuilder(view);
    // TODO highlight the text part & modify color, if needed
    return new TdlibUi.UrlOpenParameters(this.openParameters).tooltip(b);
  }

  protected TdlibUi.UrlOpenParameters modifyUrlOpenParameters (TdlibUi.UrlOpenParameters parameters, Text.ClickCallback callback, String url) {
    if (callback == null)
      return parameters;
    parameters = new TdlibUi.UrlOpenParameters(parameters);
    if (callback.forceInstantView(url)) {
      parameters.forceInstantView();
    }
    TdApi.WebPage webPage = callback.findWebPage(url);
    if (webPage != null) {
      parameters.sourceWebView(webPage);
    }
    return parameters;
  }

  public abstract int getType ();

  public abstract boolean hasMedia ();
  @Nullable
  public abstract String getMediaKeyId ();
  public abstract void requestMedia (ComplexReceiver receiver, int keyOffset, int maxMediaCount);

  public final int getStart () {
    return start;
  }
  public final int getEnd () {
    return end;
  }
  public abstract boolean isClickable ();
  public abstract TdApi.TextEntity getSpoiler ();
  public abstract boolean isBold ();
  public abstract boolean isIcon ();
  public TextIcon getIcon () { return null; }
  public abstract boolean isItalic ();
  public abstract boolean isUnderline ();
  public abstract boolean isStrikethrough ();
  public abstract boolean hasAnchor (String anchor);
  public abstract boolean isFullWidth ();
  public abstract boolean isCustomEmoji ();

  final TextPaint getTextPaint (TextStyleProvider textStyleProvider, boolean forceBold) {
    // different typefaces
    boolean isBold = forceBold || isBold();
    boolean isItalic = isItalic();
    boolean isFixed = isMonospace();

    // different storages
    boolean isUnderline = isUnderline();
    boolean isStrikeThrough = isStrikethrough();

    Fonts.TextPaintStorage storage = textStyleProvider.getTextPaintStorage();

    if (isFixed) {
      storage = storage.getMonospaceStorage();
    }
    if (isUnderline) {
      storage = storage.getUnderlineStorage();
    }
    if (isStrikeThrough) {
      storage = storage.getStrikeThroughStorage();
    }

    TextPaint textPaint;
    if (isBold && isItalic) {
      textPaint = storage.getBoldItalicPaint(); // todo fake bolding?
    } else if (isItalic) {
      textPaint = storage.getItalicPaint();
    } else if (isBold) {
      textPaint = needFakeBold ? storage.getFakeBoldPaint() : storage.getBoldPaint();
    } else {
      textPaint = storage.getRegularPaint();
    }

    textStyleProvider.preparePaint(textPaint);

    if (isSmall()) {
      textPaint.setTextSize(textPaint.getTextSize() * .75f);
    }

    return textPaint;
  }
  public abstract boolean isMonospace ();
  public abstract boolean isSmall ();
  public abstract void performClick (View view, Text text, TextPart textPart, @Nullable Text.ClickCallback callback);
  public abstract boolean performLongPress (View view, Text text, TextPart textPart, boolean allowShare, @Nullable Text.ClickCallback callback);
  protected abstract boolean equals (TextEntity b, int compareMode, String originalText);
  public abstract boolean isEssential ();
  public abstract TextColorSet getSpecialColorSet (TextColorSet defaultColorSet);

  @Nullable
  protected final ViewController<?> findRoot (View view) {
    if (view == null)
      return null;
    ViewController<?> c = ViewController.findRoot(view);
    if (c != null)
      return c;
    return UI.getContext(view.getContext()).navigation().getCurrentStackItem();
  }

  public float getBaselineShift () {
    return 0f;
  }

  public static TextEntity[] valueOf (Tdlib tdlib, TdApi.FormattedText text, TdlibUi.UrlOpenParameters openParameters) {
    return valueOf(tdlib, text.text, text.entities, openParameters);
  }

  public static TextEntity[] valueOf (ViewController<?> context, Tdlib tdlib, CharSequence text, TdlibUi.UrlOpenParameters openParameters) {
    if (StringUtils.isEmpty(text)) {
      return null;
    }
    if (!(text instanceof Spanned)) {
      return null;
    }
    ClickableSpan[] spans = ((Spanned) text).getSpans(0, text.length(), ClickableSpan.class);
    if (spans == null || spans.length == 0) {
      return null;
    }
    TextEntity[] entities = new TextEntity[spans.length];
    String str = text.toString();
    int i = 0;
    for (ClickableSpan span : spans) {
      int startIndex = ((Spanned) text).getSpanStart(span);
      int endIndex = ((Spanned) text).getSpanEnd(span);
      entities[i] = new TextEntityCustom(context, tdlib, str, startIndex, endIndex, TextEntityCustom.FLAG_CLICKABLE, openParameters).setOnClickListener(span);
      i++;
    }
    return entities;
  }

  private static int valueOf (Tdlib tdlib, String in, TdApi.TextEntity[] entities, TdlibUi.UrlOpenParameters openParameters, List<TdApi.TextEntity> parents, final int rootIndex, List<TextEntity> out) {
    final TdApi.TextEntity rootEntity = entities[rootIndex];
    int offset = rootEntity.offset;
    int index = rootIndex;

    TdApi.TextEntity nextEntity;
    while (index + 1 < entities.length && (nextEntity = entities[index + 1]).offset < rootEntity.offset + rootEntity.length) {
      if (offset < nextEntity.offset) {
        out.add(new TextEntityMessage(tdlib, in, offset, nextEntity.offset, rootEntity, parents, openParameters));
      }

      if (parents == null)
        parents = new ArrayList<>();
      parents.add(rootEntity);
      index += valueOf(tdlib, in, entities, openParameters, parents, index + 1, out);
      parents.remove(parents.size() - 1);

      offset = out.get(out.size() - 1).end;
    }
    if (offset < rootEntity.offset + rootEntity.length) {
      out.add(new TextEntityMessage(tdlib, in, offset, rootEntity.offset + rootEntity.length, rootEntity, parents, openParameters));
    }
    return (index - rootIndex) + 1;
  }

  public static TextEntity[] valueOf (Tdlib tdlib, String in, @Nullable TdApi.TextEntity[] entities, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (entities == null || entities.length == 0)
      return null;
    List<TextEntity> result = new ArrayList<>();
    for (int i = 0; i < entities.length; ) {
      i += valueOf(tdlib, in, entities, openParameters, null, i, result);
    }
    for (int i = 1; i < result.size(); i++) {
      TextEntity entity = result.get(i);
      TextEntity prev = result.get(i - 1);
      if (prev.end > entity.start) {
        Log.e("Error processing entities, textLength: %d, entities: %s", in.length(), entities);
        if (BuildConfig.DEBUG)
          throw new RuntimeException();
        return null;
      }
    }
    return result.toArray(new TextEntity[0]);
  }

  public static TextEntity valueOf (Tdlib tdlib, String in, TdApi.TextEntity entity, TdlibUi.UrlOpenParameters openParameters) {
    return new TextEntityMessage(tdlib, in, entity, openParameters);
  }

  public static final int COMPARE_MODE_NORMAL = 0;
  public static final int COMPARE_MODE_CLICK_HIGHLIGHT = 1;
  public static final int COMPARE_MODE_SPOILER = 2;

  public static boolean equals (TextEntity a, TextEntity b, int compareMode, String originalText) {
    return (a == null && b == null) || (!(a == null || b == null) && a.getType() == b.getType() && a.equals(b, compareMode, originalText));
  }
}
