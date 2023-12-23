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
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BaseView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagePreviewView extends BaseView implements AttachDelegate, Destroyable, ChatListener, MessageListener, TdlibCache.UserDataChangeListener, TGLegacyManager.EmojiLoadListener, TdlibUi.MessageProvider, RunnableData<LinkPreview> {
  private static class TextEntry extends ListAnimator.MeasurableEntry<Text> implements Destroyable {
    public final Drawable drawable;
    public ComplexReceiver receiver;
    public TdlibAccentColor accentColor;

    public TextEntry (Text text, Drawable drawable, ComplexReceiver receiver, TdlibAccentColor accentColor) {
      super(text);
      this.drawable = drawable;
      this.receiver = receiver;
      this.accentColor = accentColor;
    }

    public @PorterDuffColorId int getNameColorId (@PorterDuffColorId int fallbackColorId) {
      if (accentColor != null) {
        long complexColor = accentColor.getNameComplexColor();
        if (Theme.isColorId(complexColor)) {
          return Theme.extractColorValue(complexColor);
        }
      }
      return fallbackColorId;
    }

    @Override
    public void performDestroy () {
      super.performDestroy();
      if (receiver != null) {
        receiver.performDestroy();
      }
    }
  }

  private static class MediaEntry extends ListAnimator.MeasurableEntry<MediaPreview> implements Destroyable {
    final ComplexReceiver receiver;

    public MediaEntry (MediaPreview preview, ComplexReceiver receiver) {
      super(preview);
      this.receiver = receiver;
    }

    @Override
    public void performDestroy () {
      super.performDestroy();
      receiver.performDestroy();
    }
  }

  private final RefreshRateLimiter emojiUpdateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);

  public MessagePreviewView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW)));
    RippleSupport.setTransparentSelector(this);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  private final ViewProvider viewProvider = new SingleViewProvider(this);

  private static class DisplayData implements Destroyable {
    public final Tdlib tdlib;
    public final TdApi.Message message;
    public final @Nullable TdApi.InputTextQuote quote;
    public final @Options int options;
    public @Nullable TdApi.SearchMessagesFilter filter;
    public @Nullable String forcedTitle;
    public boolean messageDeleted;
    public @Nullable LinkPreview linkPreview;
    public @Nullable View.OnClickListener onMediaClickListener;

    public DisplayData (Tdlib tdlib, TdApi.Message message, @Nullable TdApi.InputTextQuote quote, int options) {
      this.tdlib = tdlib;
      this.message = message;
      this.quote = quote;
      this.options = options;
    }

    public boolean setForcedTitle (String forcedTitle) {
      if (!StringUtils.equalsOrBothEmpty(this.forcedTitle, forcedTitle)) {
        this.forcedTitle = forcedTitle;
        return true;
      }
      return false;
    }

    public void setOnMediaClickListener (View.OnClickListener onClickListener) {
      this.onMediaClickListener = onClickListener;
    }

    public void setPreviewFilter (@Nullable TdApi.SearchMessagesFilter filter) {
      this.filter = filter;
    }

    public void setLinkPreview (@Nullable LinkPreview linkPreview) {
      this.linkPreview = linkPreview;
    }

    public boolean isLinkPreviewShowSmallMedia () {
       return linkPreview != null && linkPreview.hasMedia() && !linkPreview.getOutputShowLargeMedia();
    }

    public boolean equalsTo (TdApi.Message message, @Nullable TdApi.InputTextQuote quote, @Options int options, @Nullable LinkPreview linkPreview) {
      return this.message == message && Td.equalsTo(this.quote, quote) && this.options == options && this.linkPreview == linkPreview;
    }

    public TdlibAccentColor accentColor () {
      if (StringUtils.isEmpty(forcedTitle)) {
        // TODO handle fake messages
        return tdlib.messageAccentColor(message);
      }
      return null;
    }

    public boolean needBoldTitle () {
      if (linkPreview != null && linkPreview.isNotFound()) {
        return false;
      }
      return !StringUtils.isEmpty(forcedTitle) || Td.getMessageAuthorId(message, true) != 0;
    }

    public boolean relatedToChat (long chatId) {
      return this.message.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR &&
        ((TdApi.MessageSenderChat) this.message.senderId).chatId == chatId;
    }

    public boolean relatedToUser (long userId) {
      return this.message.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR &&
        ((TdApi.MessageSenderUser) this.message.senderId).userId == userId;
    }

    public boolean updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
      if (this.message.chatId == chatId && this.message.id == messageId) {
        this.message.content = newContent;
        return true;
      }
      return false;
    }

    @Override
    public void performDestroy () {
      setLinkPreview(null);
    }
  }

  private @Nullable DisplayData data;
  private boolean useAvatarFallback;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Options.NONE,
    Options.IGNORE_ALBUM_REFRESHERS,
    Options.DISABLE_MESSAGE_PREVIEW,
    Options.NO_UPDATES,
    Options.HANDLE_MEDIA_CLICKS
  }, flag = true)
  public @interface Options {
    int
      NONE = 0,
      IGNORE_ALBUM_REFRESHERS = 1,
      DISABLE_MESSAGE_PREVIEW = 1 << 1,
      NO_UPDATES = 1 << 2,
      HANDLE_MEDIA_CLICKS = 1 << 3;
  }

  private ContentPreview contentPreview;

  public void setMessage (@Nullable TdApi.Message message, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String forcedTitle, @Options int options) {
    setMessage(message, null, filter, forcedTitle, options);
  }

  public void setMessage (@Nullable TdApi.Message message, @Nullable TdApi.InputTextQuote quote, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String forcedTitle, @Options int options) {
    if (message != null) {
      DisplayData displayData = new DisplayData(tdlib, message, quote, options);
      displayData.setPreviewFilter(filter);
      displayData.setForcedTitle(forcedTitle);
      setDisplayData(displayData);
    } else {
      setDisplayData(null);
    }
  }

  public interface LinkPreviewMediaClickListener {
    void onClick (MessagePreviewView view, @NonNull LinkPreview linkPreview);
  }

  public void setLinkPreview (@Nullable LinkPreview linkPreview, @Nullable LinkPreviewMediaClickListener onMediaClick) {
    if (linkPreview != null) {
      DisplayData displayData = new DisplayData(tdlib, linkPreview.getFakeMessage(), null, Options.DISABLE_MESSAGE_PREVIEW | Options.NO_UPDATES | (onMediaClick != null ? Options.HANDLE_MEDIA_CLICKS : 0));
      displayData.setForcedTitle(linkPreview.getForcedTitle());
      if (onMediaClick != null) {
        displayData.setOnMediaClickListener(v -> {
          onMediaClick.onClick(this, linkPreview);
        });
      }
      displayData.setLinkPreview(linkPreview);
      setDisplayData(displayData);
    } else {
      setDisplayData(null);
    }
  }

  @Override
  public void runWithData (LinkPreview arg) {
    if (this.data != null && this.data.linkPreview == arg) {
      data.setForcedTitle(arg.getForcedTitle());
      updateTitleText();
      buildPreview();
    }
  }

  private void setDisplayData (DisplayData data) {
    if (this.data == null && data == null) {
      return;
    }
    if (this.data != null && data != null && this.data.equalsTo(data.message, data.quote, data.options, data.linkPreview)) {
      if (data.setForcedTitle(data.forcedTitle)) {
        updateTitleText();
      }
      return;
    }
    if (this.data != null) {
      if (!BitwiseUtils.hasFlag(this.data.options, Options.NO_UPDATES)) {
        unsubscribeFromUpdates(this.data.message);
      }
      if (this.data.linkPreview != null) {
        this.data.linkPreview.removeReference(this);
      }
      this.data.performDestroy();
    }
    this.data = data;
    if (data != null) {
      if (!BitwiseUtils.hasFlag(this.data.options, Options.NO_UPDATES)) {
        subscribeToUpdates(data.message);
      }
      if (data.linkPreview != null && data.linkPreview.isLoading()) {
        data.linkPreview.addReference(this);
      }
      buildPreview();
      if (!BitwiseUtils.hasFlag(data.options, Options.DISABLE_MESSAGE_PREVIEW)) {
        setPreviewChatId(null, data.message.chatId, null, new MessageId(data.message), data.filter);
      } else {
        clearPreviewChat();
      }
    } else {
      this.contentPreview = null;
      this.mediaPreview.replace(null, false);
      this.mediaPreview.measure(false);
      clearPreviewChat();
    }
    invalidate();
  }

  public void setUseAvatarFallback (boolean useAvatarFallback) {
    this.useAvatarFallback = useAvatarFallback;
  }

  private final ReplaceAnimator<TextEntry> titleText = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final ReplaceAnimator<TextEntry> contentText = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final ReplaceAnimator<MediaEntry> mediaPreview = new ReplaceAnimator<>(ignored -> {
    buildText(true);
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator showSmallMedia = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private static final float LINE_PADDING = 6f;
  private static final float PADDING_SIZE = 8f;
  private static final float TEXT_SIZE = 14f;
  private static final float IMAGE_HEIGHT = 40f;

  private float customLinePadding = -1;

  private float getLinePadding () {
    return customLinePadding != -1 ? customLinePadding : LINE_PADDING;
  }

  public void setLinePadding (float customLinePadding) {
    this.customLinePadding = customLinePadding;
    buildText(true);
    buildMediaPreview(false);
    invalidate();
  }

  private void buildPreview () {
    if (data == null) {
      throw new IllegalStateException();
    }
    if (!Td.isEmpty(data.quote)) {
      this.contentPreview = new ContentPreview(data.quote.text, false);
    } else {
      this.contentPreview = ContentPreview.getChatListPreview(tdlib, data.message.chatId, data.messageDeleted ? null : data.message, true);
    }
    if (contentPreview.hasRefresher() && !(BitwiseUtils.hasFlag(data.options, Options.IGNORE_ALBUM_REFRESHERS) && contentPreview.isMediaGroup())) {
      contentPreview.refreshContent((chatId, messageId, newPreview, oldPreview) -> {
        tdlib.runOnUiThread(() -> {
          if (this.contentPreview == oldPreview) {
            this.contentPreview = newPreview;
            updateContentText();
            buildMediaPreview(true);
          }
        });
      });
    }
    buildText(false);
    buildMediaPreview(false);
    invalidate();
  }

  private int lastTextWidth;

  private int getTextHorizontalOffset () {
    return getMediaWidth() + Screen.dp(getLinePadding());
  }

  private int getMediaWidth () {
    return (int) (mediaPreview.getMetadata().getTotalWidth() + mediaPreview.getMetadata().getTotalVisibility() * Screen.dp(PADDING_SIZE));
  }

  private int contentInset;

  public void setContentInset (int contentInset) {
    if (this.contentInset != contentInset) {
      this.contentInset = contentInset;
      buildText(true);
    }
  }

  private int calculateTextWidth () {
    return Math.max(0, getMeasuredWidth() - Screen.dp(PADDING_SIZE) * 2 - getTextHorizontalOffset() - Screen.dp(getLinePadding()) - contentInset);
  }

  private ComplexReceiver newComplexReceiver (boolean forTextMedia) {
    ComplexReceiver receiver = new ComplexReceiver(forTextMedia ? null : this);
    if (forTextMedia) {
      receiver.setUpdateListener(emojiUpdateLimiter);
    }
    if (isAttached) {
      receiver.attach();
    } else {
      receiver.detach();
    }
    return receiver;
  }

  private void buildText (boolean isLayout) {
    int textWidth = calculateTextWidth();
    if (this.lastTextWidth != textWidth || !isLayout) {
      this.lastTextWidth = textWidth;
      if (textWidth > 0) {
        if (isLayout && !titleText.isEmpty()) {
          for (ListAnimator.Entry<TextEntry> entry : titleText) {
            entry.item.content.changeMaxWidth(textWidth);
          }
        } else {
          buildTitleText(textWidth, false);
        }
        if (isLayout && !contentText.isEmpty()) {
          for (ListAnimator.Entry<TextEntry> entry : contentText) {
            entry.item.content.changeMaxWidth(textWidth);
            if (entry.item.receiver != null || entry.item.content.hasMedia()) {
              if (entry.item.receiver == null) {
                entry.item.receiver = newComplexReceiver(true);
              }
              entry.item.content.requestMedia(entry.item.receiver);
            }
          }
        } else {
          buildContentText(textWidth, false);
        }
      } else {
        this.titleText.replace(null, false);
        this.contentText.replace(null, false);
      }
      invalidate();
    }
  }

  private void buildMediaPreview (boolean animated) {
    MediaPreview preview;
    boolean showSmallMedia = false;

    if (data != null) {
      preview = MediaPreview.valueOf(tdlib, data.message, contentPreview, Screen.dp(IMAGE_HEIGHT), Screen.dp(3f));
      if (preview == null && useAvatarFallback) {
        TdApi.Chat chat = tdlib.chat(data.message.chatId);
        if (chat != null && chat.photo != null) {
          preview = MediaPreview.valueOf(tdlib, chat.photo, Screen.dp(IMAGE_HEIGHT), Screen.dp(3f));
        }
      }
      showSmallMedia = data.isLinkPreviewShowSmallMedia();
    } else {
      preview = null;
    }

    if (preview == null) {
      this.mediaPreview.replace(null, animated);
    } else {
      ComplexReceiver receiver = newComplexReceiver(false);
      preview.requestFiles(receiver, false);
      this.mediaPreview.replace(new MediaEntry(preview, receiver), animated);
      this.showSmallMedia.setValue(showSmallMedia, animated);
    }
  }

  public void updateShowSmallMedia (boolean animated) {
    boolean showSmallMedia = data != null && data.isLinkPreviewShowSmallMedia();
    this.showSmallMedia.setValue(showSmallMedia, animated);
  }

  private void updateTitleText () {
    if (lastTextWidth > 0) {
      buildTitleText(lastTextWidth, true);
      invalidate();
    }
  }

  @Nullable
  private String getTitle () {
    if (this.data != null) {
      if (!StringUtils.isEmpty(this.data.forcedTitle)) {
        return this.data.forcedTitle;
      }
      return tdlib.senderName(this.data.message, true, false);
    }
    return null;
  }

  private static final float CORNER_PADDING = 3f;

  private void buildTitleText (int availWidth, boolean animated) {
    if (data == null) {
      throw new IllegalStateException();
    }
    String title = getTitle();
    TdlibAccentColor accentColor = data.accentColor();

    Drawable drawable;
    if (!Td.isEmpty(data.quote)) {
      @ColorId int colorId = ColorId.messageAuthor;
      if (accentColor != null) {
        long complexColor = accentColor.getNameComplexColor();
        if (Theme.isColorId(complexColor)) {
          colorId = Theme.extractColorValue(complexColor);
        }
      }
      drawable = getSparseDrawable(R.drawable.baseline_format_quote_close_16, colorId);
      availWidth -= drawable.getMinimumWidth() + Screen.dp(CORNER_PADDING);
    } else {
      drawable = null;
    }

    Text newText;
    if (!StringUtils.isEmpty(title)) {
      newText = new Text.Builder(tdlib,
        title,
        null,
        availWidth,
        Paints.robotoStyleProvider(TEXT_SIZE),
        accentColor != null ?
          accentColor::getNameColor :
          TextColorSets.Regular.MESSAGE_AUTHOR,
        null
      ).viewProvider(viewProvider)
       .singleLine()
       .allClickable()
       .allBold(data.needBoldTitle())
       .build();
    } else {
      newText = null;
    }
    this.titleText.replace(newText != null || drawable != null ? new TextEntry(newText, drawable, null, accentColor) : null, animated);
  }

  private void updateContentText () {
    if (lastTextWidth > 0) {
      buildContentText(lastTextWidth, true);
    }
  }

  private TdApi.FormattedText getContentText () {
    return contentPreview != null ? contentPreview.buildFormattedText(true) : null;
  }

  private void buildContentText (int availWidth, boolean animated) {
    TdApi.FormattedText text = getContentText();
    int iconRes = contentPreview != null && contentPreview.emoji != null ? contentPreview.emoji.iconRepresentation : 0;
    Text newText;
    ComplexReceiver receiver;
    if (!Td.isEmpty(text)) {
      newText = new Text.Builder(tdlib,
        text, null, availWidth, Paints.robotoStyleProvider(TEXT_SIZE), TextColorSets.Regular.NORMAL, (text1, specificMedia) -> {
          for (ListAnimator.Entry<TextEntry> entry : contentText) {
            if (entry.item.content == text1) {
              if (!text1.invalidateMediaContent(entry.item.receiver, specificMedia)) {
                text1.requestMedia(entry.item.receiver);
              }
            }
          }
        }
      ).viewProvider(viewProvider)
       .lineMarginProvider(iconRes != 0 ? (lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? Screen.dp(2f) + Screen.dp(18f) : 0 : null)
       .singleLine()
       .ignoreNewLines()
       .ignoreContinuousNewLines()
       .addFlags(Text.FLAG_CUSTOM_LONG_PRESS)
       .build();
      if (newText.hasMedia()) {
        receiver = newComplexReceiver(true);
        newText.requestMedia(receiver);
      } else {
        receiver = null;
      }
    } else {
      newText = null;
      receiver = null;
    }
    this.contentText.replace(newText != null || iconRes != 0 ? new TextEntry(newText, getSparseDrawable(iconRes, ColorId.icon), receiver, null) : null, animated);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildText(true);
  }

  // Drawing

  public boolean getMediaPosition (RectF rectF) {
    float reverseMediaFactor = showSmallMedia.getFloatValue();
    int textX = Screen.dp(getLinePadding()) + Screen.dp(PADDING_SIZE) + getTextHorizontalOffset() - (int) (getMediaWidth() * reverseMediaFactor);
    ListAnimator.Entry<MediaEntry> entry = mediaPreview.singleton();
    if (entry == null) {
      return false;
    }
    float scale = 1f, cx, cy;
    if (reverseMediaFactor <= .5f) {
      cx = textX - entry.item.content.getWidth() / 2f - Screen.dp(PADDING_SIZE);
      cy = (SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW) - Screen.dp(IMAGE_HEIGHT)) / 2f + entry.item.content.getHeight() / 2f;
    } else {
      scale = MINIMIZED_MEDIA_SCALE;
      float x = getMeasuredWidth() - contentInset - getPaddingRight() - entry.item.content.getWidth() + (int) ((1f - reverseMediaFactor) * getMediaWidth());
      float y = (SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW) - Screen.dp(IMAGE_HEIGHT)) / 2f;
      cx = x + entry.item.content.getWidth() / 2f;
      cy = y + entry.item.content.getHeight() / 2f;
    }

    rectF.left = cx - entry.item.content.getWidth() * scale / 2f;
    rectF.right = cx + entry.item.content.getWidth() * scale / 2f;
    rectF.top = cy - entry.item.content.getHeight() * scale / 2f;
    rectF.bottom = cy + entry.item.content.getHeight() * scale / 2f;
    return true;
  }

  private boolean isInsideMedia (float touchX, float touchY) {
    RectF rectF = Paints.getRectF();
    if (getMediaPosition(rectF)) {
      return (touchX >= rectF.left && touchX <= rectF.right && touchY >= rectF.top && touchY <= rectF.bottom);
    } else {
      return false;
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (this.data == null)
      return;
    float reverseMediaFactor = showSmallMedia.getFloatValue();
    int textX = Screen.dp(getLinePadding()) + Screen.dp(PADDING_SIZE) + getTextHorizontalOffset() - (int) (getMediaWidth() * reverseMediaFactor);
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      if (reverseMediaFactor <= .5f) {
        float alpha = 1f - reverseMediaFactor / .5f;
        entry.item.content.draw(this, c, entry.item.receiver, textX - entry.item.content.getWidth() - Screen.dp(PADDING_SIZE), (SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW) - Screen.dp(IMAGE_HEIGHT)) / 2f, entry.getVisibility() * alpha);
      } else {
        float alpha = (reverseMediaFactor - .5f) / .5f;
        int restoreToCount = Views.save(c);
        float x = getMeasuredWidth() - contentInset - getPaddingRight() - entry.item.content.getWidth() + (int) ((1f - reverseMediaFactor) * getMediaWidth());
        float y = (SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW) - Screen.dp(IMAGE_HEIGHT)) / 2f;
        float cx = x + entry.item.content.getWidth() / 2f;
        float cy = y + entry.item.content.getHeight() / 2f;
        c.scale(MINIMIZED_MEDIA_SCALE, MINIMIZED_MEDIA_SCALE, cx, cy);
        entry.item.content.draw(this, c, entry.item.receiver, x, y, entry.getVisibility() * alpha);
        Views.restore(c, restoreToCount);
      }
    }
    int contextTextY = Screen.dp(7f) + Screen.dp(TEXT_SIZE) + Screen.dp(5f);
    for (ListAnimator.Entry<TextEntry> entry : titleText) {
      int titleY = Screen.dp(7f);
      if (entry.item.content != null) {
        entry.item.content.draw(c, textX, titleY, null, entry.getVisibility());
      }
      if (entry.item.drawable != null) {
        float textCenterY = titleY + (entry.item.content != null ? entry.item.content.getLineHeight(false) : Screen.dp(TEXT_SIZE)) / 2f;
        float iconX = textX + Math.max(
          (entry.item.content != null ? entry.item.content.getWidth() + Screen.dp(CORNER_PADDING) : 0),
          contentText.getMetadata().getTotalWidth() - entry.item.drawable.getMinimumWidth()
        );
        Drawables.draw(c, entry.item.drawable, iconX, textCenterY - entry.item.drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(entry.item.getNameColorId(ColorId.messageAuthor), entry.getVisibility()));
      }
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.drawable != null) {
        Drawables.draw(c, entry.item.drawable, textX, contextTextY + (entry.item.content != null ? entry.item.content.getLineHeight(false) : Screen.dp(TEXT_SIZE)) / 2f - entry.item.drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(ColorId.icon, entry.getVisibility()));
      }
      if (entry.item.content != null) {
        entry.item.content.draw(c, textX, contextTextY, null, entry.getVisibility(), entry.item.receiver);
      }
    }
  }

  private static final float MINIMIZED_MEDIA_SCALE = .8f;

  // Touch events

  private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      if (data != null && BitwiseUtils.hasFlag(data.options, Options.HANDLE_MEDIA_CLICKS)) {
        return isInsideMedia(x, y);
      }
      return false;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      if (data != null && BitwiseUtils.hasFlag(data.options, Options.HANDLE_MEDIA_CLICKS) && isInsideMedia(x, y)) {
        if (data.onMediaClickListener != null) {
          data.onMediaClickListener.onClick(MessagePreviewView.this);
        }
      }
    }
  });

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.content != null && entry.item.content.onTouchEvent(this, e)) {
        return true;
      }
    }
    return clickHelper.onTouchEvent(this, e) || super.onTouchEvent(e);
  }

  // Listeners

  public void subscribeToUpdates (TdApi.Message message) {
    switch (message.senderId.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        tdlib.listeners().subscribeToChatUpdates(((TdApi.MessageSenderChat) message.senderId).chatId, this);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        tdlib.cache().addUserDataListener(((TdApi.MessageSenderUser) message.senderId).userId, this);
        break;
      }
    }
    tdlib.listeners().subscribeToMessageUpdates(message.chatId, this);
  }

  public void unsubscribeFromUpdates (TdApi.Message message) {
    switch (message.senderId.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        tdlib.listeners().unsubscribeFromChatUpdates(((TdApi.MessageSenderChat) message.senderId).chatId, this);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        tdlib.cache().removeUserDataListener(((TdApi.MessageSenderUser) message.senderId).userId, this);
        break;
      }
    }
    tdlib.listeners().unsubscribeFromMessageUpdates(message.chatId, this);
  }

  private void runOnUiThreadOptional (RunnableData<DisplayData> runnable) {
    tdlib.runOnUiThread(() -> {
      if (this.data != null) {
        runnable.runWithData(this.data);
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    runOnUiThreadOptional(data -> {
      if (data.relatedToChat(chatId)) {
        updateTitleText();
      }
    });
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    runOnUiThreadOptional(data -> {
      if (data.relatedToUser(user.id)) {
        updateTitleText();
      }
    });
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    runOnUiThreadOptional(data -> {
      if (data.updateMessageContent(chatId, messageId, newContent)) {
        buildPreview();
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    runOnUiThreadOptional(data -> {
      if (data.message.chatId == chatId) {
        boolean needUpdate = false;
        if (ArrayUtils.contains(messageIds, data.message.id)) {
          data.messageDeleted = true;
          needUpdate = true;
        }
        if (this.contentPreview != null) {
          Tdlib.Album album = this.contentPreview.getAlbum();
          if (album != null) {
            for (TdApi.Message albumMessage : album.messages) {
              if (ArrayUtils.contains(messageIds, albumMessage.id)) {
                needUpdate = true; // one of album's message was deleted, force reload album.
                break;
              }
            }
          }
        }
        if (needUpdate) {
          buildPreview();
        }
      }
    });
  }

  // Interface

  private boolean isAttached;

  @Override
  public void attach () {
    this.isAttached = true;
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      entry.item.receiver.attach();
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.receiver != null) {
        entry.item.receiver.attach();
      }
    }
  }

  @Override
  public void detach () {
    this.isAttached = false;
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      entry.item.receiver.attach();
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.receiver != null) {
        entry.item.receiver.detach();
      }
    }
  }

  public void clear () {
    setDisplayData(null);
  }

  @Override
  public void performDestroy () {
    clear();
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  @Override
  public boolean isMediaGroup () {
    Tdlib.Album album = contentPreview != null ? contentPreview.getAlbum() : null;
    return album != null;
  }

  @Override
  public List<TdApi.Message> getVisibleMediaGroup () {
    Tdlib.Album album = contentPreview != null ? contentPreview.getAlbum() : null;
    return album != null ? album.messages : null;
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    if (data != null && data.message.chatId != 0) {
      return data.message;
    }
    return null;
  }

  @Override
  public int getVisibleMessageFlags () {
    return TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION;
  }
}
