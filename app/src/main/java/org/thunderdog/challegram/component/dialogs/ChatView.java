/**
 * File created on 28/04/15 at 18:58
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.dialogs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
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
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.BaseView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.ChatPosition;

public class ChatView extends BaseView implements TdlibSettingsManager.PreferenceChangeListener {
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
    ThemeManager.addThemeListener(titlePaint, R.id.theme_color_text);

    titlePaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaintFake.setColor(Theme.textAccentColor());
    titlePaintFake.setTextSize(Screen.dp(17f));
    titlePaintFake.setTypeface(Fonts.getRobotoRegular());
    titlePaintFake.setFakeBoldText(true);
    ThemeManager.addThemeListener(titlePaintFake, R.id.theme_color_text);

    timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    timePaint.setColor(Theme.textDecentColor());
    timePaint.setTextSize(Screen.dp(12f));
    timePaint.setTypeface(Fonts.getRobotoRegular());
    ThemeManager.addThemeListener(timePaint, R.id.theme_color_textLight);
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
    return imageReceiver;
  }

  private TGChat chat;
  private final ImageReceiver imageReceiver;

  private final BounceAnimator onlineAnimator;
  private final BoolAnimator isSelected = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public ChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    if (titlePaint == null) {
      initPaints();
    }
    this.onlineAnimator = new BounceAnimator(new SingleViewProvider(this));
    setId(R.id.chat);
    RippleSupport.setTransparentSelector(this);
    int chatListMode = getChatListMode();
    imageReceiver = new ImageReceiver(this, getAvatarRadius(chatListMode));
    imageReceiver.setBounds(getAvatarLeft(chatListMode), getAvatarTop(chatListMode), getAvatarLeft(chatListMode) + getAvatarSize(chatListMode), getAvatarTop(chatListMode) + getAvatarSize(chatListMode));
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void setIsSelected (boolean isSelected, boolean animated) {
    this.isSelected.setValue(isSelected, animated);
  }

  public void setAnimationsDisabled (boolean disabled) {
    imageReceiver.setAnimationDisabled(disabled);
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
    imageReceiver.attach();
  }

  public void detach () {
    imageReceiver.detach();
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
        chat.syncCounter();
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
      onlineAnimator.setValue(chat != null && chat.isOnline(), false);
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
    }
    imageReceiver.requestFile(chat != null ? chat.getAvatar() : null);
  }

  public boolean needAnimateChanges () {
    ViewController<?> c = ViewController.findAncestor(ChatView.this);
    return c == null || c.isAttachedToNavigationController();
  }

  public void updateOnline () {
    onlineAnimator.setValue(chat != null && chat.isOnline(), needAnimateChanges());
  }

  public void invalidateContentReceiver () {
    imageReceiver.requestFile(chat != null ? chat.getAvatar() : null);
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
    if (chat != null) {
      chat.checkLayout(getMeasuredWidth());
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
    imageReceiver.setBounds(left, getAvatarTop(chatListMode), left + getAvatarSize(chatListMode), getAvatarTop(chatListMode) + getAvatarSize(chatListMode));
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

    if (chat.showVerify()) {
      Drawables.drawRtl(c, Icons.getChatVerifyDrawable(), chat.getVerifyLeft(), getMuteTop(chatListMode), Paints.getVerifyPaint(), viewWidth, rtl);
    }

    if (chat.showMute()) {
      Drawables.drawRtl(c, Icons.getChatMuteDrawable(R.id.theme_color_chatListMute), chat.getMuteLeft(), getMuteTop(chatListMode), Paints.getChatsMutePaint(), viewWidth, rtl);
    }

    if (chat.isSending()) {
      int x = chat.getChecksRight() - Screen.dp(10f) - Screen.dp(Icons.CLOCK_SHIFT_X);
      Drawables.drawRtl(c, Icons.getClockIcon(R.id.theme_color_iconLight), x, getClockTop(chatListMode) - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint(), viewWidth, rtl);
    } else if (chat.isOutgoing() && !chat.isSelfChat()) {
      int x = chat.getChecksRight();
      int y = getClockTop(chatListMode);
      if (chat.showViews()) {
        y -= Screen.dp(.5f);
      } else if (chat.isUnread()) {
        x += Screen.dp(4f);
      }
      if (chat.showViews()) {
        chat.getViewCounter().draw(c, x + Screen.dp(3f), y + Screen.dp(14f) / 2f, Gravity.RIGHT, 1f, this, R.id.theme_color_ticksRead);
      } else {
        int iconX = x - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f);
        boolean unread = chat.isUnread();
        Drawables.drawRtl(c, unread ? Icons.getSingleTick(R.id.theme_color_ticks) : Icons.getDoubleTick(R.id.theme_color_ticks), iconX, y - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint(), viewWidth, rtl);
      }
    }

    Counter counter = chat.getCounter();
    float counterRight = viewWidth - getRightPadding();
    float counterRadius = getCounterRadius();
    float counterCenterY = getCounterTop(chatListMode);
    counter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f);
    counterRight -= counter.getScaledWidth(getTimePaddingLeft());

    Counter mentionCounter = chat.getMentionCounter();
    mentionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, R.id.theme_color_badgeText);
    counterRight -= mentionCounter.getScaledWidth(getTimePaddingLeft());

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
            Drawable d = getSparseDrawable(iconId, 0);
            int y = textTop + text.getLineHeight(false) / 2 - d.getMinimumHeight() / 2;
            if (iconId == R.drawable.baseline_camera_alt_16) {
              y += Screen.dp(.5f);
            }
            Drawables.drawRtl(c, d, x, y, paint, viewWidth, rtl);
            x += Screen.dp(18f); //  + d.getMinimumWidth();

          }
        }

        text.draw(c, chat.getTextLeft(), textTop, null, textAlpha);
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
        DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - getLeftPadding(chatListMode) : getLeftPadding(chatListMode), top + text.getLineHeight() / 2f, ColorUtils.alphaColor(statusVisibility, text.getTextColor()), this, statusVisibility == 1f ? R.id.theme_color_textLight : 0);
        int x = getLeftPadding(chatListMode);
        text.draw(c, x, (int) top, null, statusVisibility);
      }
    }

    layoutReceiver();

    if (chat.hasAvatar()) {
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderRounded(c, getAvatarRadius(chatListMode));
      }
      imageReceiver.draw(c);
    } else {
      AvatarPlaceholder p = chat.getAvatarPlaceholder();
      if (p != null) {
        int cx = imageReceiver.centerX();
        int cy = imageReceiver.centerY();
        if (chat.isArchive()) {
          c.drawCircle(cx, cy, p.getRadius(), Paints.fillingPaint(ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_avatarArchivePinned), Theme.getColor(R.id.theme_color_avatarArchive), isPinnedArchive.getFloatValue())));
          p.draw(c, cx, cy, 1f, p.getRadius(), false);
        } else {
          p.draw(c, cx, cy);
        }
      }
    }

    DrawAlgorithms.drawIcon(c, imageReceiver, 315f, chat.getScheduleAnimator().getFloatValue(), Theme.fillingColor(), getSparseDrawable(R.drawable.baseline_watch_later_10, R.id.theme_color_badgeMuted), PorterDuffPaint.get(R.id.theme_color_badgeMuted, chat.getScheduleAnimator().getFloatValue()));

    onlineAnimator.setValue(chat.isOnline(), true);
    DrawAlgorithms.drawOnline(c, imageReceiver, (1f - isSelected.getFloatValue()) * onlineAnimator.getFloatValue());
    DrawAlgorithms.drawSimplestCheckBox(c, imageReceiver, isSelected.getFloatValue());
  }
}