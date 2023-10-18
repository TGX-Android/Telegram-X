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
 * File created on 28/04/2015 at 18:58
 */
package org.thunderdog.challegram.component.dialogs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.widget.BaseView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.ChatPosition;

public class ChatView extends BaseView implements TdlibSettingsManager.PreferenceChangeListener, InvalidateContentProvider, EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate, TdlibUi.MessageProvider {
  private static Paint timePaint;
  private static TextPaint titlePaint, titlePaintFake; // counterTextPaint

  public static void reset () {
    if (titlePaint != null)
      titlePaint.setTextSize(Screen.dp(17f));
    if (titlePaintFake != null)
      titlePaintFake.setTextSize(Screen.dp(17f));
    if (timePaint != null)
      timePaint.setTextSize(Screen.dp(12f));
  }

  private static void initPaints () {
    titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaint.setColor(Theme.textAccentColor());
    titlePaint.setTextSize(Screen.dp(17f));
    titlePaint.setTypeface(Fonts.getRobotoMedium());
    ThemeManager.addThemeListener(titlePaint, ColorId.text);

    titlePaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaintFake.setColor(Theme.textAccentColor());
    titlePaintFake.setTextSize(Screen.dp(17f));
    titlePaintFake.setTypeface(Fonts.getRobotoRegular());
    titlePaintFake.setFakeBoldText(true);
    ThemeManager.addThemeListener(titlePaintFake, ColorId.text);

    timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    timePaint.setColor(Theme.textDecentColor());
    timePaint.setTextSize(Screen.dp(12f));
    timePaint.setTypeface(Fonts.getRobotoRegular());
    ThemeManager.addThemeListener(timePaint, ColorId.textLight);
  }

  public static TextPaint getTitlePaint (boolean needFake) {
    if (titlePaintFake == null || titlePaint == null) {
      initPaints();
    }
    return needFake ? titlePaintFake : titlePaint;
  }

  public static Paint getTimePaint () {
    if (timePaint == null) {
      synchronized (ChatView.class) {
        if (timePaint == null) {
          initPaints();
        }
      }
    }
    return timePaint;
  }

  private static int getTextOffset () {
    return Screen.dp(12f);
  }

  public static int getAvatarLeft (int chatListMode) {
    return Screen.dp(/*inMultiLineMode ? 6f : */7f);
  }

  public static int getAvatarRadius (int chatListMode) {
    return getAvatarSize(chatListMode) / 2;
  }

  public static int getDefaultAvatarCacheSize () {
    return getAvatarSize(Settings.instance().getChatListMode());
  }

  public static int getCounterRadius () {
    return Screen.dp(11f);
  }

  public static int getLeftPadding (int chatListMode) {
    return chatListMode != Settings.CHAT_MODE_2LINE ? getAvatarLeft(chatListMode) + getAvatarSize(chatListMode) + Screen.dp(11f) : getViewHeight(chatListMode);
  }

  public static int getRightPadding () {
    return getTimePadding();
  }

  public static int getMuteOffset () {
    return Screen.dp(1f);
  }

  public static int getMutePadding () {
    return getMuteOffset();
  }

  public static int getTimePaddingRight () {
    return getTimePadding();
  }

  public static int getTimePaddingLeft () {
    return Screen.dp(7f);
  }

  public Receiver getAvatarReceiver () {
    return avatarReceiver;
  }

  public ComplexReceiver getTextMediaReceiver () {
    return textMediaReceiver;
  }

  private TGChat chat;
  private final AvatarReceiver avatarReceiver;
  private final ComplexReceiver emojiStatusReceiver;
  private final ComplexReceiver textMediaReceiver;
  private final ComplexReceiver reactionsReceiver;

  private final BoolAnimator isSelected = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final RefreshRateLimiter refreshRateLimiter;

  public ChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    if (titlePaint == null) {
      initPaints();
    }
    this.refreshRateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    setId(R.id.chat);
    RippleSupport.setTransparentSelector(this);
    int chatListMode = getChatListMode();
    emojiStatusReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    reactionsReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    avatarReceiver = new AvatarReceiver(this)
      .setUpdateListener(refreshRateLimiter.passThroughUpdateListener());
    avatarReceiver.setAvatarRadiusPropertyIds(PropertyId.AVATAR_RADIUS_CHAT_LIST, PropertyId.AVATAR_RADIUS_CHAT_LIST_FORUM);
    avatarReceiver.setBounds(getAvatarLeft(chatListMode), getAvatarTop(chatListMode), getAvatarLeft(chatListMode) + getAvatarSize(chatListMode), getAvatarTop(chatListMode) + getAvatarSize(chatListMode));
    textMediaReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void setIsSelected (boolean isSelected, boolean animated) {
    this.isSelected.setValue(isSelected, animated);
  }

  public void setAnimationsDisabled (boolean disabled) {
    avatarReceiver.setAnimationDisabled(disabled);
    textMediaReceiver.setAnimationDisabled(disabled);
    emojiStatusReceiver.setAnimationDisabled(disabled);
    reactionsReceiver.setAnimationDisabled(disabled);
  }

  public static int getViewHeight (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(82f);
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(78f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(72f);
    }
  }

  public static float getAvatarSizeDp (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE_BIG:
        return 60f;
      case Settings.CHAT_MODE_3LINE:
        return 58f;
      case Settings.CHAT_MODE_2LINE:
      default:
        return 52f;
    }
  }

  public static int getAvatarSize (int chatListMode) {
    return Screen.dp(getAvatarSizeDp(chatListMode));
  }

  private static int getAvatarTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(11f);
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(10f);
    }
  }

  private static int getTimePadding () {
    return Screen.dp(15f);
  }

  private static int getCounterOffset (int chatsListMode) {
    switch (chatsListMode) {
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(42f);
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(44f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(38f);
    }
  }

  public static int getCounterTop (int chatsListMode) {
    return getCounterOffset(chatsListMode) + getCounterRadius();
  }

  public static int getTextTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(32f);
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(33f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(39.5f);
    }
  }

  private static int getSingleLineOffset (int chatListMode) {
    return Screen.dp(chatListMode == Settings.CHAT_MODE_3LINE ? 2f : 2f);
  }

  public static int getTitleTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return getTextOffset() + Screen.dp(14f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return getTextOffset() + Screen.dp(16f);
    }
  }

  private static int getClockTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(15f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(17f);
    }
  }

  private static int getMuteTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(9f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(11f);
    }
  }

  public static int getTitleTop2 (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(10f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(12f);
    }
  }

  public static int getAvatarLeftFull (int chatListMode) {
    return getAvatarLeft(chatListMode) + getAvatarRadius(chatListMode);
  }

  public static int getAvatarTopFull (int chatListMode) {
    return getAvatarTop(chatListMode) + getAvatarRadius(chatListMode);
  }

  public void attach () {
    avatarReceiver.attach();
    textMediaReceiver.attach();
    emojiStatusReceiver.attach();
    reactionsReceiver.attach();
  }

  public void detach () {
    avatarReceiver.detach();
    textMediaReceiver.detach();
    emojiStatusReceiver.detach();
    reactionsReceiver.detach();
  }

  public void setChat (TGChat chat) {
    if (this.chat != chat) {
      if (this.chat != null) {
        this.chat.detachFromView(this);
        if (this.chat.isArchive()) {
          this.tdlib.settings().removeUserPreferenceChangeListener(this);
        }
      }
      this.chat = chat;
      this.isPinnedArchive.setValue(chat != null && chat.isArchive() && !tdlib.settings().needHideArchive(), false);
      if (chat != null) {
        chat.checkLayout(getMeasuredWidth());
        chat.attachToView(this);
        if (chat.isArchive()) {
          this.tdlib.settings().addUserPreferenceChangeListener(this);
        }
      }
      if (chat != null) {
        setPreviewChatId(chat.getChatList(), chat.getChatId(), null);
      } else {
        setPreviewChatId(null, 0, null);
      }
      if (chat != null && chat.isArchive()) {
        setCustomControllerProvider(new CustomControllerProvider() {
          @Override
          public boolean needsForceTouch (BaseView v, float x, float y) {
            return Settings.instance().needPreviewChatOnHold();
          }

          @Override
          public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
            return false;
          }

          @Override
          public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
            ChatsController c = new ChatsController(getContext(), tdlib);
            c.setArguments(new ChatsController.Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setNeedMessagesSearch(true));
            return c;
          }
        });
      } else {
        setCustomControllerProvider(null);
      }
      if (chat != null) {
        chat.onAttachToView();
      }
    }
    requestContent();
  }

  @Override
  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    requestEmojiStatus();
  }

  private void requestEmojiStatus () {
    EmojiStatusHelper.EmojiStatusDrawable text = chat != null ? chat.getEmojiStatus() : null;
    if (text != null) {
      text.requestMedia(emojiStatusReceiver);
    } else {
      emojiStatusReceiver.clear();
    }
  }

  public ComplexReceiver getReactionsReceiver () {
    return reactionsReceiver;
  }

  public void requestReactionFiles () {
    if (chat != null) {
      chat.requestReactionFiles(reactionsReceiver);
    } else {
      reactionsReceiver.clear();
    }
  }

  private void requestTextContent () {
    Text text = chat != null ? chat.getText() : null;
    if (text != null) {
      text.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private void requestContent () {
    requestTextContent();
    requestEmojiStatus();
    requestReactionFiles();
    if (chat != null) {
      AvatarPlaceholder.Metadata avatarPlaceholder = chat.getAvatarPlaceholder();
      if (avatarPlaceholder != null) {
        avatarReceiver.requestPlaceholder(tdlib, avatarPlaceholder, AvatarReceiver.Options.SHOW_ONLINE);
      } else {
        avatarReceiver.requestChat(tdlib, chat.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
      }
    } else {
      avatarReceiver.clear();
    }
  }

  public boolean needAnimateChanges () {
    ViewController<?> c = ViewController.findAncestor(ChatView.this);
    return c == null || c.getParentOrSelf().getAttachState();
  }

  public void invalidateAvatarReceiver () {
    if (chat != null) {
      avatarReceiver.requestChat(tdlib, chat.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
    } else {
      avatarReceiver.clear();
    }
    invalidate();
  }

  private boolean needBackground;

  public void setNeedBackground (boolean needBackground) {
    if (this.needBackground != needBackground) {
      this.needBackground = needBackground;
      if (needBackground) {
        RippleSupport.setSimpleWhiteBackground(this);
      } else {
        RippleSupport.setTransparentSelector(this);
      }
    }
  }

  private boolean isDragging;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      invalidate();
    }
  }

  public boolean isDragging () {
    return isDragging;
  }

  private int getChatListMode () {
    return chat != null ? chat.getListMode() : Settings.instance().getChatListMode();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getViewHeight(getChatListMode()), MeasureSpec.EXACTLY));
    layoutReceiver();
    if (chat != null && chat.checkLayout(getMeasuredWidth())) {
      requestContent();
    }
  }

  public boolean canStartDrag (float x, float y) {
    int chatListMode = getChatListMode();
    int threshold = ChatView.getAvatarLeft(chatListMode) * 2 + ChatView.getAvatarSize(chatListMode);
    if (Lang.rtl()) {
      return x >= getMeasuredWidth() - threshold;
    } else {
      return x <= threshold;
    }
  }

  private void layoutReceiver () {
    int chatListMode = getChatListMode();
    int left = Lang.rtl() ? getMeasuredWidth() - getAvatarLeft(chatListMode) - getAvatarSize(chatListMode) : getAvatarLeft(chatListMode);
    avatarReceiver.setBounds(left, getAvatarTop(chatListMode), left + getAvatarSize(chatListMode), getAvatarTop(chatListMode) + getAvatarSize(chatListMode));
  }

  public TGChat getChat () {
    return chat;
  }

  public long getChatId () {
    return chat != null ? chat.getChatId() : 0;
  }

  public void invalidateTypingPart (boolean onlyIcon) {
    invalidate(); // TODO invalidate only needed part
  }

  @Override
  public void onPreferenceChanged(Tdlib tdlib, long key, boolean value) {
    if (chat != null && chat.isArchive() && key == TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE) {
      isPinnedArchive.setValue(!value, true);
    }
  }

  private final BoolAnimator isPinnedArchive = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.chat == cause) {
      requestTextContent();
      requestEmojiStatus();
      requestReactionFiles();
      return true;
    }
    return false;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }

    final int chatListMode = getChatListMode();

    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();

    if (isDragging) {
      c.drawColor(ColorUtils.alphaColor(.8f, Theme.fillingColor()));
    }

    c.drawText(chat.getTime(), (rtl ? viewWidth - chat.getTimeLeft() - chat.getTimeWidth() : chat.getTimeLeft()), getTitleTop(chatListMode), timePaint);

    Text title = chat.getTitle();
    if (title != null) {
      int titleX = getLeftPadding(chatListMode);
      int titleTop = getTitleTop2(chatListMode);
      if (chat.isSecretChat()) {
        Drawable d = Icons.getSecureDrawable();
        Drawables.drawRtl(c, d, titleX - Screen.dp(7f), titleTop + title.getHeight() / 2 - d.getMinimumHeight() / 2, Paints.getGreenPorterDuffPaint(), viewWidth, rtl);
        titleX += Screen.dp(14f);
      }

      title.draw(c, titleX, titleTop);
    }

    EmojiStatusHelper.EmojiStatusDrawable emojiStatus = chat.getEmojiStatus();
    if (emojiStatus != null) {
      emojiStatus.draw(c, chat.getEmojiStatusLeft(), getTitleTop2(chatListMode), 1f, emojiStatusReceiver);
    }

    if (chat.showVerify()) {
      Drawables.drawRtl(c, Icons.getChatVerifyDrawable(), chat.getVerifyLeft(), getMuteTop(chatListMode), Paints.getVerifyPaint(), viewWidth, rtl);
    }

    if ((chat.showScam() || chat.showFake()) && chat.getChatMark() != null) {
      int additionalPadding = Screen.dp(4f);

      int chatMarkLeft = chat.getVerifyLeft() + Screen.dp(10f);
      int chatMarkY = getTitleTop2(chatListMode) + Screen.dp(0.5f);

      RectF rct = Paints.getRectF();
      rct.set(chatMarkLeft - additionalPadding, chatMarkY, chatMarkLeft + chat.getChatMark().getWidth() + additionalPadding, chatMarkY + chat.getChatMark().getLineHeight(true));
      c.drawRoundRect(rct, Screen.dp(2f), Screen.dp(2f), Paints.getProgressPaint(Theme.getColor(ColorId.textNegative), Screen.dp(1.5f)));

      chat.getChatMark().draw(c, chatMarkLeft, chatMarkY + Screen.dp(1f));
    }

    if (chat.showMute()) {
      Drawables.drawRtl(c, Icons.getChatMuteDrawable(ColorId.chatListMute), chat.getMuteLeft(), getMuteTop(chatListMode), Paints.getChatsMutePaint(), viewWidth, rtl);
    }

    if (chat.isSending()) {
      int x = chat.getChecksRight() - Screen.dp(10f) - Screen.dp(Icons.CLOCK_SHIFT_X);
      Drawables.drawRtl(c, Icons.getClockIcon(ColorId.iconLight), x, getClockTop(chatListMode) - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint(), viewWidth, rtl);
    } else {
      int x = chat.getChecksRight();
      if (chat.isOutgoing() && !chat.isSelfChat()) {
        int y = getClockTop(chatListMode);
        if (chat.showViews()) {
          y -= Screen.dp(.5f);
        } else if (chat.isUnread()) {
          x += Screen.dp(4f);
        }
        if (chat.showViews()) {
          chat.getViewCounter().draw(c, x + Screen.dp(3f), y + Screen.dp(14f) / 2f, Gravity.RIGHT, 1f, this, ColorId.ticksRead);
          x -= chat.getViewCounter().getScaledWidth(Screen.dp(3));
        } else {
          int iconX = x - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f);
          boolean unread = chat.isUnread();
          Drawables.drawRtl(c, unread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticks), iconX, y - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint(), viewWidth, rtl);
          x -= Screen.dp(24 + 3);
        }
      }
      if (chat.needDrawReactionsPreview()) {
        chat.getReactionsCounterDrawable().draw(c, x - chat.getReactionsWidth(), getClockTop(chatListMode) + Screen.dp(7f));
      }
    }

    Counter counter = chat.getCounter();
    float counterRight = viewWidth - getRightPadding();
    float counterRadius = getCounterRadius();
    float counterCenterY = getCounterTop(chatListMode);
    counter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f);
    counterRight -= counter.getScaledWidth(getTimePaddingLeft());

    Counter mentionCounter = chat.getMentionCounter();
    mentionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, ColorId.badgeText);
    counterRight -= mentionCounter.getScaledWidth(getTimePaddingLeft());

    Counter reactionCounter = chat.getReactionsCounter();
    reactionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, chat.notificationsEnabled() ? ColorId.badgeText : ColorId.badgeMutedText);
    counterRight -= reactionCounter.getScaledWidth(getTimePaddingLeft());

    TdlibStatusManager.Helper status = chat.statusHelper();
    TdlibStatusManager.ChatState state = status != null ? status.drawingState() : null;
    float statusVisibility = state != null ? state.visibility() : 0f;
    float textAlpha = 1f - statusVisibility;
    if (textAlpha > 0f) {
      final int dy = (int) (Screen.dp(14f) * statusVisibility);
      final boolean needRestore = dy != 0;
      final int saveCount;
      if (needRestore) {
        saveCount = Views.save(c);
        c.translate(0, dy);
      } else {
        saveCount = -1;
      }

      int textTop = getTextTop(chatListMode);
      Text prefix = chat.getPrefix();
      if (prefix != null) {
        int titleColor = ColorUtils.alphaColor(textAlpha, chat.showDraft() ? Theme.textRedColor() : Theme.textAccentColor());
        prefix.draw(c, getLeftPadding(chatListMode), textTop, null, textAlpha);
      }
      Text text = chat.getText();
      if (text != null) {
        if (chatListMode != Settings.CHAT_MODE_2LINE) {
          if (prefix != null) {
            textTop += prefix.getNextLineHeight();
          } else if (text.getLineCount() == 1) {
            textTop += getSingleLineOffset(chatListMode);
          }
        }
        IntList prefixIcons = chat.getTextIconIds();
        if (prefixIcons != null) {
          int x = chat.getTextLeft();
          for (int i = 0; i < prefixIcons.size(); i++) {
            Paint paint = PorterDuffPaint.get(chat.getTextIconColorId(), textAlpha);
            int iconId = prefixIcons.get(i);
            Drawable d = getSparseDrawable(iconId, ColorId.NONE);
            int y = textTop + text.getLineHeight(false) / 2 - d.getMinimumHeight() / 2;
            if (iconId == R.drawable.baseline_camera_alt_16) {
              y += Screen.dp(.5f);
            }
            Drawables.drawRtl(c, d, x, y, paint, viewWidth, rtl);
            x += Screen.dp(18f); //  + d.getMinimumWidth();

          }
        }

        text.draw(c, chat.getTextLeft(), textTop, null, textAlpha, textMediaReceiver);
      }

      if (needRestore) {
        Views.restore(c, saveCount);
      }
    }
    if (statusVisibility > 0f) {
      Text text = status.drawingText();
      if (text != null) {
        float top = getTextTop(chatListMode) - Screen.dp(14f) * textAlpha;
        if (chatListMode != Settings.CHAT_MODE_2LINE && text.getLineCount() == 1) {
          top += getSingleLineOffset(chatListMode);
        }
        DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - getLeftPadding(chatListMode) : getLeftPadding(chatListMode), top + text.getLineHeight() / 2f, ColorUtils.alphaColor(statusVisibility, text.getTextColor()), this, statusVisibility == 1f ? ColorId.textLight : ColorId.NONE);
        int x = getLeftPadding(chatListMode);
        text.draw(c, x, (int) top, null, statusVisibility);
      }
    }

    avatarReceiver.forceAllowOnline(!isSelected.getValue(), 1f - isSelected.getFloatValue());
    layoutReceiver();
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);

    DrawAlgorithms.drawIcon(c, avatarReceiver, 315f, chat.getScheduleAnimator().getFloatValue(), Theme.fillingColor(), getSparseDrawable(R.drawable.baseline_watch_later_10, ColorId.badgeMuted), PorterDuffPaint.get(ColorId.badgeMuted, chat.getScheduleAnimator().getFloatValue()));
    DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, isSelected.getFloatValue());
  }

  @Override
  public boolean isMediaGroup () {
    return chat != null && chat.isMediaGroup();
  }

  @Override
  public List<TdApi.Message> getVisibleMediaGroup () {
    return chat != null ? chat.getVisibleMediaGroup() : null;
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    return chat != null ? chat.getVisibleMessage() : null;
  }

  @Override
  public int getVisibleMessageFlags () {
    return TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION | (chat != null && chat.needRefreshInteractionInfo() ? TdlibMessageViewer.Flags.REFRESH_INTERACTION_INFO : 0);
  }
}