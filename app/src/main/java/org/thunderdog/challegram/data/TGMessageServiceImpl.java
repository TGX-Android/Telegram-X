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
 * File created on 03/09/2022, 18:42.
 */

package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextEntityMessage;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Filter;
import me.vkryl.td.MessageId;

abstract class TGMessageServiceImpl extends TGMessage {
  protected TGMessageServiceImpl (MessagesManager manager, TdApi.Message msg) {
    super(manager, msg);
  }

  @NonNull
  protected abstract TextColorSet defaultTextColorSet ();

  protected interface ServiceMessageCreator {
    FormattedText createText ();

    default boolean ignoreNewLines () {
      return false;
    }
  }

  private ServiceMessageCreator textCreator;

  protected void setTextCreator (@NonNull ServiceMessageCreator textCreator) {
    this.textCreator = textCreator;
  }

  protected interface OnClickListener {
    void onClick ();
  }

  private OnClickListener onClickListener;

  protected void setOnClickListener (OnClickListener onClickListener) {
    this.onClickListener = onClickListener;
  }

  private MediaPreview chatPhoto;

  protected void setDisplayChatPhoto (@Nullable TdApi.ChatPhoto chatPhoto) {
    if (chatPhoto != null) {
      this.chatPhoto = MediaPreview.valueOf(tdlib, chatPhoto, ChatView.getDefaultAvatarCacheSize(), Screen.dp(28f));
    } else {
      this.chatPhoto = null;
    }
  }

  private ServiceMessageCreator originalMessageCreator;
  private Filter<TdApi.Message> previewCallback;
  private TdApi.Message previewMessage;

  @Override
  protected boolean handleMessagePreviewChange (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (previewMessage != null && previewMessage.chatId == chatId && previewMessage.id == messageId) {
      previewMessage.content = newContent;
      if (previewCallback.accept(previewMessage)) {
        updateServiceMessage();
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean handleMessagePreviewDelete (long chatId, long messageId) {
    if (originalMessageCreator != null && previewMessage != null && previewMessage.chatId == chatId && previewMessage.id == messageId) {
      previewMessage = null;
      previewCallback = null;
      setTextCreator(originalMessageCreator);
      updateServiceMessage();
      return true;
    }
    return false;
  }

  public void setDisplayMessage (long chatId, long messageId, Filter<TdApi.Message> callback) {
    originalMessageCreator = textCreator;
    tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) result;
        runOnUiThreadOptional(() -> {
          if (callback.accept(message)) {
            this.previewMessage = message;
            this.previewCallback = callback;
            updateServiceMessage();
          }
        });
      }
    });
  }

  private void updateServiceMessage () {
    boolean hadTextMedia = hasTextMedia();
    rebuildAndUpdateContent();
    if (hadTextMedia || hasTextMedia()) {
      invalidateTextMediaReceiver();
    }
  }

  private boolean hasTextMedia () {
    return this.displayText != null && this.displayText.hasMedia();
  }

  // Content

  @Override
  protected boolean headerDisabled () {
    return true;
  }

  @Override
  protected boolean disableBubble () {
    return true;
  }

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  public boolean canBeSelected () {
    return false;
  }

  @Override
  public boolean canBePinned () {
    return false;
  }

  @Override
  public boolean canBeReacted () {
    return false;
  }

  @Override
  public boolean canSwipe () {
    return false;
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    if (chatPhoto != null) {
      chatPhoto.requestFiles(receiver, invalidate);
    } else {
      receiver.clear();
    }
  }

  private FormattedText currentText;
  private int lastAvailWidth;
  private Text displayText;

  private final ClickHelper helper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      return isWithinPhotoCoordinates(x, y);
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      if (isWithinPhotoCoordinates(x, y)) {
        MediaItem item = MediaItem.valueOf(context(), tdlib(), getMessage());
        if (item != null) {
          MediaViewController.openFromMessage(TGMessageServiceImpl.this, item);
        }
      }
    }
  });

  @Override
  protected void buildContent (int maxWidth) {
    int availWidth = Math.max(0, this.width - Screen.dp(12f) * 2);

    FormattedText formattedText = textCreator.createText();
    if (this.lastAvailWidth == availWidth && this.currentText != null && this.currentText.equals(formattedText)) {
      return;
    }

    boolean hadMedia = this.displayText != null && this.displayText.hasMedia();

    if (this.displayText != null) {
      this.displayText.performDestroy();
      this.displayText = null;
    }

    this.currentText = formattedText;
    this.lastAvailWidth = availWidth;
    if (availWidth > 0) {
      Text.Builder b = new Text.Builder(
        formattedText, availWidth,
        getServiceTextStyleProvider(useBubbles()),
        defaultTextColorSet(),
        (text, specificMedia) -> {
          if (this.displayText == text) {
            invalidateTextMediaReceiver(text, specificMedia);
          }
        }
      ).viewProvider(currentViews)
       .textFlags(Text.FLAG_ALIGN_CENTER)
       .allBold();
      if (textCreator.ignoreNewLines()) {
        b.ignoreNewLines()
         .ignoreContinuousNewLines();
      }
      if (this.onClickListener != null) {
        b.onClick((v, text, part, openParameters) -> {
          if (onClickListener != null) {
            onClickListener.onClick();
            return true;
          }
          return false;
        });
      }
      this.displayText = b.build();
    }

    if (hadMedia || (this.displayText != null && this.displayText.hasMedia())) {
      invalidateTextMediaReceiver();
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (displayText != null) {
      displayText.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private int getTextY () {
    if (useBubbles()) {
      return getContentY();
    } else {
      return getContentY() + Screen.dp(3f);
    }
  }

  @Override
  protected int getContentHeight () {
    return
      (displayText != null ? displayText.getHeight() : 0) +
      (useBubbles() ? 0 : Screen.dp(3f) + Screen.dp(1.5f)) +
      (chatPhoto != null ? Screen.dp(28f) * 2 + Screen.dp(8f) : 0);
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    res = (displayText != null && displayText.performLongPress(view)) || res;
    helper.cancel(view, x, y);
    return res;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    boolean res = displayText != null && displayText.onTouchEvent(view, e);
    return helper.onTouchEvent(view, e) || res;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    if (displayText == null) {
      return;
    }

    int textY = getTextY();

    // Background
    if (useBubbles()) {
      int backgroundRadius = Screen.dp(Theme.getBubbleDateRadius());
      RectF rectF = Paints.getRectF();
      int left = (int) (this.width / 2f - displayText.getWidth() / 2f);
      int top = textY;
      int right = left + displayText.getWidth();
      int bottom = top + displayText.getHeight();
      int backgroundPaddingHorizontal = Screen.dp(8f);
      int backgroundPaddingVertical = Screen.dp(4f);
      rectF.set(
        left - backgroundPaddingHorizontal,
        top - Screen.dp(5f),
        right + backgroundPaddingHorizontal,
        bottom + Screen.dp(4f)
      );

      int backgroundColor = getBubbleDateBackgroundColor();
      c.drawRoundRect(rectF, backgroundRadius, backgroundRadius, Paints.fillingPaint(backgroundColor));
    }

    // Text
    int paddingHorizontal = Screen.dp(12f);
    displayText.draw(c,
      paddingHorizontal, this.width - paddingHorizontal, 0,
      textY,
      null, 1f,
      view.getTextMediaReceiver()
    );

    // Chat photo
    if (chatPhoto != null /*&& !shouldHideMedia()*/) { // TODO: fix animation that seems to be broken since a long time ago
      int avatarRadius = Screen.dp(28f);
      int avatarTop = textY + displayText.getHeight() + Screen.dp(useBubbles() ? 10f : 8);
      float avatarLeft = this.width / 2f - avatarRadius;
      chatPhoto.draw(view, c, receiver, avatarLeft, avatarTop, 1f);
    }
  }

  private boolean isWithinPhotoCoordinates (float x, float y) {
    if (displayText == null || chatPhoto == null) {
      return false;
    }
    int textY = getTextY();
    int avatarRadius = Screen.dp(28f);
    int avatarTop = textY + displayText.getHeight() + Screen.dp(useBubbles() ? 10f : 8);
    float avatarLeft = this.width / 2f - avatarRadius;
    return x >= avatarLeft && x < avatarLeft + avatarRadius * 2 && y >= avatarTop && y < avatarTop + avatarRadius * 2;
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    int avatarRadius = Screen.dp(28f);
    int textY = getTextY();
    int avatarTop = textY + displayText.getHeight() + Screen.dp(useBubbles() ? 10f : 8);
    int avatarLeft = (int) (this.width / 2f - avatarRadius);

    MediaViewThumbLocation location = new MediaViewThumbLocation();
    location.setNoBounce();
    location.setRoundings(avatarRadius);

    location.set(avatarLeft, top + avatarTop, avatarLeft + avatarRadius * 2, top + avatarTop + avatarRadius * 2);
    location.setColorId(manager().useBubbles() ? ColorId.placeholder : ColorId.chatBackground);
    return location;
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (displayText != null) {
      displayText.performDestroy();
      displayText = null;
    }
  }

  // == FormattedText Utilities ==

  protected interface FormattedArgument {
    FormattedText buildArgument ();
  }

  protected final class AccentColorArgument implements FormattedArgument {
    private final @Nullable TdlibAccentColor accentColor;
    private final long customEmojiId;

    public AccentColorArgument (@NonNull TdlibAccentColor accentColor) {
      this(accentColor, 0);
    }

    public AccentColorArgument (@Nullable TdlibAccentColor accentColor, long customEmojiId) {
      this.accentColor = accentColor;
      this.customEmojiId = customEmojiId;
    }

    @Override
    public FormattedText buildArgument () {
      final String text = accentColor != null ? accentColor.getTextRepresentation() : " ";
      TextEntity entity;
      if (customEmojiId != 0) {
        entity = new TextEntityMessage(
          tdlib,
          text, new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeCustomEmoji(customEmojiId)),
          openParameters()
        );
      } else {
        entity = new TextEntityCustom(
          controller(),
          tdlib,
          text,
          0, text.length(),
          0,
          openParameters()
        );
      }
      if (accentColor != null) {
        customizeColor(entity, accentColor);
      }
      return new FormattedText(text, new TextEntity[] {entity});
    }
  }

  protected final class CustomEmojiArgument implements FormattedArgument {
    private final Tdlib tdlib;
    private final long customEmojiId;
    private final String text;

    public CustomEmojiArgument (Tdlib tdlib, long customEmojiId, @Nullable TdlibAccentColor repaintAccentColor) {
      this.tdlib = tdlib;
      this.customEmojiId = customEmojiId;
      TdlibEmojiManager.Entry emoji = tdlib.emoji().find(customEmojiId);
      if (emoji != null && !emoji.isNotFound()) {
        this.text = emoji.value.emoji;
      } else {
        this.text = ContentPreview.EMOJI_INFO.textRepresentation;
      }
    }

    @Override
    public FormattedText buildArgument () {
      String text = this.text;
      TextEntityMessage custom = new TextEntityMessage(
        tdlib,
        text,
        0, text.length(),
        new TdApi.TextEntity(0, text.length(), new TdApi.TextEntityTypeCustomEmoji(customEmojiId)),
        null,
        openParameters()
      );
      return new FormattedText(text, new TextEntity[] {custom});
    }
  }

  protected final class SenderArgument implements FormattedArgument {
    private final TdlibSender sender;
    private final boolean onlyFirstName;

    public SenderArgument (TdlibSender sender, boolean onlyFirstName) {
      this.sender = sender;
      this.onlyFirstName = onlyFirstName;
    }

    public SenderArgument (TdlibSender sender) {
      this(sender, false);
    }

    @Override
    public FormattedText buildArgument () {
      final String text = onlyFirstName ?
        sender.getNameShort() :
        sender.getName();
      if (text.isEmpty()) {
        return new FormattedText(text);
      }
      TextEntityCustom custom = new TextEntityCustom(
        controller(),
        tdlib,
        text,
        0, text.length(),
        0,
        openParameters()
      );
      custom.setOnClickListener(new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          if (sender.isUser()) {
            if (isEventLog() && sender.getUserId() == tdlib.telegramAntiSpamUserId()) {
              // FIXME tooltip instead of the toast
              TooltipOverlayView.TooltipBuilder b = openParameters().tooltip;
              if (b != null) {
                b.icon(R.drawable.baseline_info_24).show(tdlib, R.string.AggressiveAntiSpamBot).hideDelayed();
              } else {
                UI.showToast(R.string.AggressiveAntiSpamBot, Toast.LENGTH_SHORT);
              }
            } else {
              tdlib.ui().openPrivateProfile(controller(), sender.getUserId(), openParameters());
            }
          } else if (sender.isChat()) {
            tdlib.ui().openChatProfile(controller(), sender.getChatId(), null, openParameters());
          }
        }
      });
      TdlibAccentColor accentColor = needColoredNames() ? sender.getAccentColor() : tdlib.accentColor(TdlibAccentColor.InternalId.REGULAR);
      customizeColor(custom, accentColor);
      return new FormattedText(text, new TextEntity[] {custom});
    }
  }

  protected void customizeColor (TextEntity entity, TdlibAccentColor accentColor) {
    if (useBubbles()) {
      entity.setCustomColorSet(new TextColorSetOverride(defaultTextColorSet()) {
        @Override
        public int defaultTextColor () {
          return ColorUtils.fromToArgb(
            getBubbleDateTextColor(),
            accentColor.getNameColor(),
            messagesController().wallpaper().getBackgroundTransparency()
          );
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return defaultTextColor();
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          float transparency = messagesController().wallpaper().getBackgroundTransparency();
          long complexColor = accentColor.getNameComplexColor();
          return isPressed && transparency == 1f ? Theme.extractColorValue(complexColor) : 0;
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          float transparency = messagesController().wallpaper().getBackgroundTransparency();
          return isPressed && transparency == 1f ? ColorUtils.alphaColor(.2f, accentColor.getNameColor()) : 0;
        }
      });
    } else {
      entity.setCustomColorSet(new TextColorSetOverride(defaultTextColorSet()) {
        @Override
        public int defaultTextColor () {
          return accentColor.getNameColor();
        }

        @Override
        public int clickableTextColor (boolean isPressed) {
          return defaultTextColor();
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          return isPressed ? Theme.extractColorValue(accentColor.getNameComplexColor()) : 0;
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          return isPressed ? ColorUtils.alphaColor(.2f, accentColor.getNameColor()) : 0;
        }
      });
    }
  }

  protected final class SenderListArgument implements FormattedArgument {
    private final TdlibSender[] senders;
    private final boolean onlyFirstNames;

    public SenderListArgument (TdlibSender[] senders, boolean onlyFirstNames) {
      this.senders = senders;
      this.onlyFirstNames = onlyFirstNames;
    }

    public SenderListArgument (TdlibSender[] senders) {
      this(senders, false);
    }

    @Override
    public FormattedText buildArgument () {
      if (senders.length == 0) {
        return getPlural(R.string.xUsers, 0);
      }
      if (senders.length == 1) {
        return new SenderArgument(senders[0], onlyFirstNames).buildArgument();
      }
      FormattedText[] formattedTexts = new FormattedText[senders.length];
      for (int i = 0; i < senders.length; i++) {
        formattedTexts[i] = new SenderArgument(senders[i], onlyFirstNames).buildArgument();
      }
      return FormattedText.concat(
        Lang.getConcatSeparator(),
        Lang.getConcatSeparatorLast(true),
        formattedTexts
      );
    }
  }

  protected abstract class FormattedTextArgument implements FormattedArgument {
    protected abstract TdApi.FormattedText getFormattedText ();

    @Override
    public final FormattedText buildArgument () {
      TdApi.FormattedText formattedText = getFormattedText();
      return FormattedText.valueOf(controller(), formattedText, openParameters());
    }
  }

  protected class TextEntityArgument implements FormattedArgument {
    private final String text;
    private final TdApi.TextEntityType entityType;

    public TextEntityArgument (String text, TdApi.TextEntityType entityType) {
      this.text = text;
      this.entityType = entityType;
    }

    @Override
    public FormattedText buildArgument () {
      final TdApi.FormattedText formattedText;
      if (text.length() > 0) {
        formattedText = new TdApi.FormattedText(text, new TdApi.TextEntity[] {
          new TdApi.TextEntity(0, text.length(), entityType)
        });
      } else {
        formattedText = new TdApi.FormattedText("", new TdApi.TextEntity[0]);
      }
      return FormattedText.valueOf(controller(), formattedText, openParameters());
    }
  }

  protected final class BoldArgument extends TextEntityArgument {
    public BoldArgument (String text) {
      super(text, new TdApi.TextEntityTypeBold());
    }
  }

  protected class MessageArgument implements FormattedArgument {
    private final TdApi.Message message;
    private final TdApi.FormattedText preview;

    public MessageArgument (TdApi.Message message, TdApi.FormattedText preview) {
      this.message = message;
      this.preview = preview;
    }

    @Override
    public final FormattedText buildArgument () {
      FormattedText formattedText = FormattedText.valueOf(controller(), preview, openParameters());
      return formattedText.allClickable(controller(), new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          highlightOtherMessage(new MessageId(message.chatId, message.id));
        }
      }, true, openParameters());
    }
  }

  protected final class InvoiceArgument extends MessageArgument {
    public InvoiceArgument (TdApi.Message message) {
      super(message, new TdApi.FormattedText(
        ((TdApi.MessageInvoice) message.content).title,
        null
      ));
    }
  }

  protected final class GameArgument extends MessageArgument {
    public GameArgument (TdApi.Message message) {
      super(message, new TdApi.FormattedText(
        TD.getGameName(((TdApi.MessageGame) message.content).game, false),
        null
      ));
    }
  }

  protected final class InviteLinkArgument implements FormattedArgument {
    private final TdApi.ChatInviteLink inviteLink;
    private final String text;
    private final boolean requiresUpdateBeforeOpening;

    public InviteLinkArgument (TdApi.ChatInviteLink inviteLink, String text, boolean requiresUpdateBeforeOpening) {
      this.inviteLink = inviteLink;
      this.text = text;
      this.requiresUpdateBeforeOpening = requiresUpdateBeforeOpening;
    }

    public InviteLinkArgument (TdApi.ChatInviteLink inviteLink, boolean requiresUpdateBeforeOpening) {
      this(inviteLink, inviteLink.inviteLink, requiresUpdateBeforeOpening);
    }

    @Override
    public FormattedText buildArgument () {
      TextEntityCustom custom = new TextEntityCustom(
        controller(),
        tdlib,
        text,
        0, text.length(),
        0,
        openParameters()
      );
      custom.setOnClickListener(new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          if (requiresUpdateBeforeOpening) {
            tdlib.ui().showInviteLinkOptionsPreload(controller(), inviteLink, msg.chatId, true, null, null);
          } else {
            tdlib.ui().showInviteLinkOptions(controller(), inviteLink, msg.chatId, true, true, null, null);
          }
        }
      });
      return new FormattedText(text, new TextEntity[] {custom});
    }
  }

  private static FormattedText[] parseFormatArgs (FormattedArgument... args) {
    if (args == null) {
      return new FormattedText[0];
    }

    FormattedText[] formatArgs = new FormattedText[args.length];
    for (int i = 0; i < args.length; i++) {
      formatArgs[i] = args[i].buildArgument();
    }
    return formatArgs;
  }

  protected final FormattedText getText (@StringRes int resId, FormattedArgument... args) {
    return FormattedText.valueOf(tdlib, openParameters(), resId, parseFormatArgs(args));
  }

  protected final FormattedText formatText (@NonNull String format, FormattedArgument... args) {
    if (args == null || args.length == 0) {
      return new FormattedText(format);
    }
    FormattedText[] formatArgs = parseFormatArgs(args);
    CharSequence text = Lang.formatString(format,
      (target, argStart, argEnd, argIndex, needFakeBold) -> formatArgs[argIndex],
      (Object[]) formatArgs
    );
    return FormattedText.valueOf(text, tdlib, openParameters());
  }

  protected final FormattedText getPlural (@StringRes int resId, long num, FormattedArgument... args) {
    return FormattedText.getPlural(tdlib, openParameters(), resId, num, parseFormatArgs(args));
  }

  protected final FormattedText getDuration (
    @StringRes int secondsRes,
    @StringRes int minutesRes,
    @StringRes int hoursRes,
    @StringRes int daysRes,
    @StringRes int weeksRes,
    @StringRes int monthsRes,
    final long duration,
    final TimeUnit durationUnit,
    FormattedArgument... args) {
    final long days = durationUnit.toDays(duration);
    final long months = days / 30;
    final long weeks = days / 7;
    if (monthsRes != 0 && months > 0) {
      return getPlural(monthsRes, months, args);
    }
    if (weeksRes != 0 && weeks > 0) {
      return getPlural(weeksRes, weeks, args);
    }
    if (daysRes != 0 && days > 0) {
      return getPlural(daysRes, days, args);
    }
    final long hours = durationUnit.toHours(duration);
    if (hoursRes != 0 && hours > 0) {
      return getPlural(hoursRes, hours, args);
    }
    final long minutes = durationUnit.toMinutes(duration);
    if (minutesRes != 0 && minutes > 0) {
      return getPlural(minutesRes, minutes, args);
    }
    final long seconds = durationUnit.toSeconds(duration);
    if (secondsRes != 0) {
      return getPlural(secondsRes, seconds, args);
    }
    throw new IllegalArgumentException("duration == " + durationUnit.toMillis(duration));
  }
}
