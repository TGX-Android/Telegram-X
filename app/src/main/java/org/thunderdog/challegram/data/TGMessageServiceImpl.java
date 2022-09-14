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
 * File created on 03/09/2022, 18:42.
 */

package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
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
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  }

  private ServiceMessageCreator textCreator;

  protected void setTextCreator (ServiceMessageCreator textCreator) {
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

  public void setDisplayMessage (long chatId, long messageId, Filter<TdApi.Message> callback) {
    tdlib.client().send(new TdApi.GetMessage(chatId, messageId), result -> {
      if (result.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) result;
        runOnUiThreadOptional(() -> {
          if (callback.accept(message)) {
            // subscribe to further updates
            boolean hadTextMedia = hasTextMedia();
            rebuildAndUpdateContent();
            if (hadTextMedia || hasTextMedia()) {
              invalidateTextMediaReceiver();
            }
          }
        });
      }
    });
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
    location.setColorId(manager().useBubbles() ? R.id.theme_color_placeholder : R.id.theme_color_chatBackground);
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
            tdlib.ui().openPrivateProfile(controller(), sender.getUserId(), openParameters());
          } else if (sender.isChat()) {
            tdlib.ui().openChatProfile(controller(), sender.getChatId(), null, openParameters());
          }
        }
      });
      int nameColorId = needColoredNames() ?
        sender.getNameColorId() :
        R.id.theme_color_messageAuthor;
      if (useBubbles()) {
        custom.setCustomColorSet(new TextColorSetOverride(defaultTextColorSet()) {
          @Override
          public int defaultTextColor () {
            return ColorUtils.fromToArgb(
              getBubbleDateTextColor(),
              Theme.getColor(nameColorId),
              messagesController().wallpaper().getBackgroundTransparency()
            );
          }

          @Override
          public int backgroundColorId (boolean isPressed) {
            float transparency = messagesController().wallpaper().getBackgroundTransparency();
            return isPressed && transparency == 1f ?
              nameColorId :
              0;
          }

          @Override
          public int backgroundColor (boolean isPressed) {
            int colorId = backgroundColorId(isPressed);
            return colorId != 0 ?
              ColorUtils.alphaColor(.2f, Theme.getColor(colorId)) :
              0;
          }
        });
      } else {
        custom.setCustomColorSet(new TextColorSetOverride(defaultTextColorSet()) {
          @Override
          public int defaultTextColor () {
            return Theme.getColor(nameColorId);
          }

          @Override
          public int backgroundColorId (boolean isPressed) {
            return isPressed ? nameColorId : 0;
          }

          @Override
          public int backgroundColor (boolean isPressed) {
            int colorId = backgroundColorId(isPressed);
            return colorId != 0 ?
              ColorUtils.alphaColor(.2f, Theme.getColor(colorId)) :
              0;
          }
        });
      }
      return new FormattedText(text, new TextEntity[] {custom});
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
    FormattedText[] formatArgs = new FormattedText[args.length];
    for (int i = 0; i < args.length; i++) {
      formatArgs[i] = args[i].buildArgument();
    }
    return formatArgs;
  }

  protected final FormattedText getText (@StringRes int resId, FormattedArgument... args) {
    if (args == null || args.length == 0) {
      return new FormattedText(Lang.getString(resId));
    }
    FormattedText[] formatArgs = parseFormatArgs(args);
    CharSequence text = Lang.getString(resId,
      (target, argStart, argEnd, argIndex, needFakeBold) -> formatArgs[argIndex],
      (Object[]) formatArgs
    );
    return toFormattedText(text);
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
    return toFormattedText(text);
  }

  protected final FormattedText getPlural (@StringRes int resId, long num, FormattedArgument... args) {
    FormattedText[] formatArgs = parseFormatArgs(args);
    CharSequence text = Lang.plural(resId, num,
      (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ?
        Lang.boldCreator().onCreateSpan(target, argStart, argEnd, argIndex, needFakeBold) :
        formatArgs[argIndex - 1],
      (Object[]) formatArgs
    );
    return toFormattedText(text);
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

  private FormattedText toFormattedText (CharSequence text) {
    final String string = text.toString();
    if (!(text instanceof Spanned)) {
      return new FormattedText(string);
    }
    List<TextEntity> mixedEntities = null;
    Spanned spanned = (Spanned) text;
    Object[] spans = spanned.getSpans(
      0,
      spanned.length(),
      Object.class
    );
    for (Object span : spans) {
      final int spanStart = spanned.getSpanStart(span);
      final int spanEnd = spanned.getSpanEnd(span);
      if (spanStart == -1 || spanEnd == -1) {
        continue;
      }
      if (span instanceof FormattedText) {
        FormattedText formattedText = (FormattedText) span;
        if (formattedText.entities != null) {
          for (TextEntity entity : formattedText.entities) {
            entity.offset(spanStart);
            if (mixedEntities == null) {
              mixedEntities = new ArrayList<>();
            }
            mixedEntities.add(entity);
          }
        }
      } else if (span instanceof CharacterStyle) {
        TdApi.TextEntityType[] entityType = TD.toEntityType((CharacterStyle) span);
        if (entityType != null && entityType.length > 0) {
          TdApi.TextEntity[] telegramEntities = new TdApi.TextEntity[entityType.length];
          for (int i = 0; i < entityType.length; i++) {
            telegramEntities[i] = new TdApi.TextEntity(
              spanStart,
              spanEnd,
              entityType[i]
            );
          }
          TextEntity[] entities = TextEntity.valueOf(tdlib, string, telegramEntities, openParameters());
          if (entities != null && entities.length > 0) {
            if (mixedEntities == null) {
              mixedEntities = new ArrayList<>();
            }
            Collections.addAll(mixedEntities, entities);
          }
        }
      }
    }
    return new FormattedText(
      string,
      mixedEntities != null && !mixedEntities.isEmpty() ? mixedEntities.toArray(new TextEntity[0]) : null
    );
  }
}
