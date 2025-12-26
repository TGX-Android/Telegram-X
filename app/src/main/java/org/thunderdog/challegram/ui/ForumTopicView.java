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
 * File created for forum topics support
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class ForumTopicView extends BaseView implements TdlibEmojiManager.Watcher {
  private static TextPaint titlePaint;
  private static TextPaint previewPaint;
  private static TextPaint timePaint;
  private static Paint iconPaint;

  private Tdlib tdlib;
  private TdApi.ForumTopic topic;

  private String titleText;
  private String previewText;
  private String timeText;
  private Counter unreadCounter;
  private String highlightQuery;

  // Icon loading
  private long customEmojiId;
  private TdlibEmojiManager.Entry customEmoji;
  private ImageFile thumbnail;
  private ImageFile imageFile;
  private GifFile gifFile;
  private final ComplexReceiver iconReceiver;

  private static final int PADDING_LEFT = 72;
  private static final int PADDING_RIGHT = 16;
  private static final int ICON_SIZE = 44;
  private static final int ICON_LEFT = 14;

  public ForumTopicView (Context context) {
    super(context, null);
    setWillNotDraw(false);
    RippleSupport.setTransparentSelector(this);
    initPaints();
    iconReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
  }

  private static void initPaints () {
    if (titlePaint == null) {
      titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      titlePaint.setTextSize(Screen.dp(16f));
      titlePaint.setTypeface(Fonts.getRobotoMedium());
      titlePaint.setColor(Theme.textAccentColor());
      ThemeManager.addThemeListener(titlePaint, ColorId.text);

      previewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      previewPaint.setTextSize(Screen.dp(14f));
      previewPaint.setTypeface(Fonts.getRobotoRegular());
      previewPaint.setColor(Theme.textDecentColor());
      ThemeManager.addThemeListener(previewPaint, ColorId.textLight);

      timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      timePaint.setTextSize(Screen.dp(12f));
      timePaint.setTypeface(Fonts.getRobotoRegular());
      timePaint.setColor(Theme.textDecentColor());
      ThemeManager.addThemeListener(timePaint, ColorId.textLight);

      iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }
  }

  public void attach () {
    iconReceiver.attach();
  }

  public void detach () {
    iconReceiver.detach();
  }

  public void destroy () {
    iconReceiver.performDestroy();
    if (customEmojiId != 0 && customEmoji == null && tdlib != null) {
      tdlib.emoji().forgetWatcher(customEmojiId, this);
    }
  }

  public void setTopic (Tdlib tdlib, TdApi.ForumTopic topic) {
    setTopic(tdlib, topic, null);
  }

  public void setTopic (Tdlib tdlib, TdApi.ForumTopic topic, @Nullable String highlightQuery) {
    this.tdlib = tdlib;
    this.topic = topic;
    this.highlightQuery = highlightQuery;

    // Build title
    this.titleText = topic.info.name;
    if (topic.info.isClosed) {
      this.titleText = "\uD83D\uDD12 " + titleText; // Lock emoji
    }
    if (topic.isPinned) {
      this.titleText = "\uD83D\uDCCC " + titleText; // Pin emoji
    }
    // Check if muted
    boolean isMuted = topic.notificationSettings != null && topic.notificationSettings.muteFor > 0;
    if (isMuted) {
      this.titleText = "\uD83D\uDD07 " + titleText; // Muted speaker emoji
    }

    // Build preview text from last message
    if (topic.lastMessage != null) {
      ContentPreview preview = ContentPreview.getChatListPreview(tdlib, topic.info.chatId, topic.lastMessage, true);
      if (preview != null) {
        this.previewText = preview.buildText(false);
      } else {
        this.previewText = "";
      }
      this.timeText = Lang.timeOrDateShort(topic.lastMessage.date, java.util.concurrent.TimeUnit.SECONDS);
    } else {
      this.previewText = "";
      this.timeText = "";
    }

    // Unread counter
    if (topic.unreadCount > 0) {
      if (unreadCounter == null) {
        unreadCounter = new Counter.Builder().build();
      }
      unreadCounter.setCount(topic.unreadCount, topic.unreadMentionCount > 0, false);
    } else {
      unreadCounter = null;
    }

    // Load topic icon
    loadTopicIcon();

    invalidate();
  }

  private void loadTopicIcon () {
    TdApi.ForumTopicIcon icon = topic.info.icon;
    if (icon == null) {
      return;
    }

    // Clear previous files
    imageFile = null;
    gifFile = null;
    thumbnail = null;
    iconReceiver.clear();

    if (icon.customEmojiId != 0) {
      // Custom emoji icon - request loading
      this.customEmojiId = icon.customEmojiId;
      this.customEmoji = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
      if (customEmoji != null && !customEmoji.isNotFound()) {
        buildCustomEmojiIcon(customEmoji);
      } else {
        // Trigger loading of postponed emoji requests
        tdlib.emoji().performPostponedRequests();
      }
    } else {
      this.customEmojiId = 0;
      this.customEmoji = null;
    }

    requestIconFiles();
  }

  private void buildCustomEmojiIcon (TdlibEmojiManager.Entry entry) {
    TdApi.Sticker sticker = entry.value;
    if (sticker == null) return;

    int size = Screen.dp(ICON_SIZE);

    // Thumbnail
    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(size);
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }

    // Main image/animation
    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        this.gifFile.setRequestedSize(size);
        break;
      }
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        this.imageFile = new ImageFile(tdlib, sticker.sticker);
        this.imageFile.setSize(size);
        this.imageFile.setScaleType(ImageFile.FIT_CENTER);
        this.imageFile.setNoBlur();
        break;
      }
    }
  }

  private void requestIconFiles () {
    DoubleImageReceiver preview = iconReceiver.getPreviewReceiver(0);
    preview.requestFile(null, thumbnail);
    if (imageFile != null) {
      iconReceiver.getImageReceiver(0).requestFile(imageFile);
    } else if (gifFile != null) {
      iconReceiver.getGifReceiver(0).requestFile(gifFile);
    }
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    this.customEmoji = entry;
    if (!entry.isNotFound()) {
      buildCustomEmojiIcon(entry);
    }
    // Request files on UI thread
    if (tdlib != null) {
      tdlib.ui().post(() -> {
        requestIconFiles();
        invalidate();
      });
    }
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (topic == null) return;

    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    // Draw topic icon
    int iconLeft = Screen.dp(ICON_LEFT);
    int iconSize = Screen.dp(ICON_SIZE);
    int iconCenterX = iconLeft + iconSize / 2;
    int iconCenterY = height / 2;
    int iconRadius = iconSize / 2;

    TdApi.ForumTopicIcon icon = topic.info.icon;
    if (icon != null) {
      boolean hasLoadedEmoji = customEmojiId != 0 && customEmoji != null && !customEmoji.isNotFound();

      // Draw colored circle background only if no custom emoji loaded
      if (!hasLoadedEmoji) {
        iconPaint.setColor(getTopicColor(icon.color));
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, iconPaint);
      }

      // Draw custom emoji icon if available
      if (hasLoadedEmoji) {
        drawCustomEmojiIcon(canvas, iconLeft, iconCenterY - iconRadius, iconLeft + iconSize, iconCenterY + iconRadius);
      } else if (customEmojiId == 0) {
        // No custom emoji - draw first letter
        drawLetterIcon(canvas, iconCenterX, iconCenterY);
      }
      // If customEmojiId != 0 but not loaded yet, just show colored circle
    }

    // Calculate text bounds
    int textLeft = Screen.dp(PADDING_LEFT);
    int textRight = width - Screen.dp(PADDING_RIGHT);

    // Draw time on the right
    float timeWidth = 0;
    if (!StringUtils.isEmpty(timeText)) {
      timeWidth = timePaint.measureText(timeText);
      canvas.drawText(timeText, textRight - timeWidth, Screen.dp(28f), timePaint);
    }

    // Draw title with optional highlighting
    int titleRight = (int) (textRight - timeWidth - Screen.dp(8f));
    if (!StringUtils.isEmpty(titleText)) {
      String ellipsizedTitle = TextUtils.ellipsize(titleText, titlePaint, titleRight - textLeft, TextUtils.TruncateAt.END).toString();
      float titleY = Screen.dp(28f);

      if (!StringUtils.isEmpty(highlightQuery)) {
        // Draw title with highlight
        drawHighlightedText(canvas, ellipsizedTitle, textLeft, titleY, titlePaint, highlightQuery);
      } else {
        canvas.drawText(ellipsizedTitle, textLeft, titleY, titlePaint);
      }
    }

    // Draw preview text
    int previewRight = textRight;
    if (unreadCounter != null) {
      float counterWidth = unreadCounter.getWidth();
      previewRight -= (int) (counterWidth + Screen.dp(8f));
      unreadCounter.draw(canvas, textRight - counterWidth / 2, height / 2 + Screen.dp(8f), Gravity.CENTER, 1f);
    }

    if (!StringUtils.isEmpty(previewText)) {
      String ellipsizedPreview = TextUtils.ellipsize(previewText, previewPaint, previewRight - textLeft, TextUtils.TruncateAt.END).toString();
      canvas.drawText(ellipsizedPreview, textLeft, Screen.dp(50f), previewPaint);
    }

    // Draw separator line at bottom
    canvas.drawLine(textLeft, height - 1, width, height - 1, Paints.strokeSeparatorPaint(ColorId.separator));
  }

  private void drawCustomEmojiIcon (Canvas canvas, int left, int top, int right, int bottom) {
    // Check if we need to apply color filter for themed stickers
    boolean needRepainting = customEmoji != null && TD.needThemedColorFilter(customEmoji.value);

    Receiver content;
    if (imageFile != null) {
      ImageReceiver image = iconReceiver.getImageReceiver(0);
      image.setBounds(left, top, right, bottom);
      content = image;
    } else if (gifFile != null) {
      GifReceiver gif = iconReceiver.getGifReceiver(0);
      gif.setBounds(left, top, right, bottom);
      content = gif;
    } else {
      content = null;
    }

    DoubleImageReceiver preview = content == null || content.needPlaceholder() ? iconReceiver.getPreviewReceiver(0) : null;
    if (preview != null) {
      if (needRepainting) {
        preview.setThemedPorterDuffColorId(ColorId.icon);
      } else {
        preview.disablePorterDuffColorFilter();
      }
      preview.setBounds(left, top, right, bottom);
      preview.draw(canvas);
    }
    if (content != null) {
      if (needRepainting) {
        content.setThemedPorterDuffColorId(ColorId.icon);
      } else {
        content.disablePorterDuffColorFilter();
      }
      content.draw(canvas);
    }
  }

  private void drawLetterIcon (Canvas canvas, int centerX, int centerY) {
    if (!StringUtils.isEmpty(topic.info.name)) {
      String firstLetter = topic.info.name.substring(0, 1).toUpperCase();
      TextPaint letterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      letterPaint.setColor(0xFFFFFFFF);
      letterPaint.setTextSize(Screen.dp(20f));
      letterPaint.setTypeface(Fonts.getRobotoMedium());
      letterPaint.setTextAlign(Paint.Align.CENTER);
      Paint.FontMetrics fm = letterPaint.getFontMetrics();
      float textY = centerY - (fm.ascent + fm.descent) / 2;
      canvas.drawText(firstLetter, centerX, textY, letterPaint);
    }
  }

  private int getTopicColor (int colorValue) {
    // Telegram topic colors are passed as actual color values like 0x6FB9F0
    // If color is 0 or very small, it's likely a color index (old format)
    if (colorValue > 0x00FFFFFF) {
      // It's already an actual color value with alpha
      return colorValue;
    } else if (colorValue >= 0x100000) {
      // It's a color without alpha - add full opacity
      return 0xFF000000 | colorValue;
    }

    // Fallback: treat as color index for backwards compatibility
    int[] colors = {
      0xFF6FB9F0, // Blue
      0xFFFFD67E, // Yellow
      0xFFCB86DB, // Purple
      0xFF8EEE98, // Green
      0xFFFF93B2, // Pink
      0xFFFB6F5F  // Red
    };
    if (colorValue >= 0 && colorValue < colors.length) {
      return colors[colorValue];
    }
    return colors[0];
  }

  private void drawHighlightedText (Canvas canvas, String text, float x, float y, TextPaint paint, String query) {
    if (StringUtils.isEmpty(query)) {
      canvas.drawText(text, x, y, paint);
      return;
    }

    String lowerText = text.toLowerCase();
    String lowerQuery = query.toLowerCase();
    int matchStart = lowerText.indexOf(lowerQuery);

    if (matchStart < 0) {
      // No match found - draw normal text
      canvas.drawText(text, x, y, paint);
      return;
    }

    int matchEnd = matchStart + query.length();

    // Draw text before highlight
    if (matchStart > 0) {
      String beforeMatch = text.substring(0, matchStart);
      canvas.drawText(beforeMatch, x, y, paint);
      x += paint.measureText(beforeMatch);
    }

    // Draw highlighted part with background
    String matchedText = text.substring(matchStart, matchEnd);
    float matchWidth = paint.measureText(matchedText);

    // Draw highlight background
    Paint.FontMetrics fm = paint.getFontMetrics();
    RectF highlightRect = new RectF(
      x - Screen.dp(1f),
      y + fm.ascent - Screen.dp(1f),
      x + matchWidth + Screen.dp(1f),
      y + fm.descent + Screen.dp(1f)
    );
    Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    highlightPaint.setColor(Theme.getColor(ColorId.textSearchQueryHighlight));
    canvas.drawRoundRect(highlightRect, Screen.dp(2f), Screen.dp(2f), highlightPaint);

    // Draw highlighted text
    canvas.drawText(matchedText, x, y, paint);
    x += matchWidth;

    // Draw text after highlight
    if (matchEnd < text.length()) {
      String afterMatch = text.substring(matchEnd);
      canvas.drawText(afterMatch, x, y, paint);
    }
  }
}
