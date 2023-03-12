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
 * File created on 23/03/2018
 */
package org.thunderdog.challegram.helper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.LiveLocationManager;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class LiveLocationHelper implements LiveLocationManager.Listener, FactorAnimator.Target, BaseView.ActionListProvider, ForceTouchView.ActionListener, MessageListener, Handler.Callback, ClickHelper.Delegate {
  private static final int ANIMATOR_SUBTEXT = 0;
  private static final int ANIMATOR_VISIBILITY = 1;

  private final BoolAnimator visibilityAnimator = new BoolAnimator(ANIMATOR_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator subtextAnimator = new BoolAnimator(ANIMATOR_SUBTEXT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private final BaseActivity context;
  private final Tdlib tdlib;
  private final long chatId, messageThreadId;
  private final @Nullable View targetView;
  private final boolean onBackground;

  private ArrayList<TdApi.Message> locationMessages;
  private @Nullable TdApi.Location location;
  private int heading;

  private final Handler handler;
  private final ClickHelper clickHelper;

  private final Drawable icon;

  public interface Callback {
    boolean onBeforeVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate);
    void onAfterVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate);
    void onApplyVisibility (LiveLocationHelper helper, boolean isVisible, float visibilityFactor, boolean finished);
  }

  private @Nullable final Callback callback;

  public LiveLocationHelper (BaseActivity context, Tdlib tdlib, long chatId, long messageThreadId, @Nullable View targetView, boolean onBackground, @Nullable Callback callback) {
    this.context = context;
    this.tdlib = tdlib;
    this.chatId = chatId;
    this.messageThreadId = messageThreadId;
    this.targetView = targetView;
    this.onBackground = onBackground;
    this.callback = callback;
    this.icon = Drawables.get(context.getResources(), R.drawable.baseline_location_on_18);
    this.handler = chatId != 0 ? new Handler(this) : null;
    this.clickHelper = chatId != 0 ? new ClickHelper(this) : null;
  }

  public LiveLocationHelper init () {
    if (chatId != 0) {
      tdlib.client().send(new TdApi.SearchChatRecentLocationMessages(chatId, 100), object -> {
        if (isDestroyed) {
          return;
        }
        ArrayList<TdApi.Message> messages = new ArrayList<>();
        if (object.getConstructor() == TdApi.Messages.CONSTRUCTOR) {
          TdApi.Message[] msgs = ((TdApi.Messages) object).messages;
          messages.ensureCapacity(msgs.length);
          for (TdApi.Message message : msgs) {
            if (message.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR || ((TdApi.MessageLocation) message.content).expiresIn == 0) {
              continue;
            }
            messages.add(message);
          }
        }
        UI.post(() -> {
          if (!isDestroyed) {
            setChatMessages(messages);
          }
        });
      });
    } else {
      tdlib.context().liveLocation().addListener(this);
    }
    return this;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY: {
        if (callback != null) {
          callback.onApplyVisibility(this, visibilityAnimator.getValue(), factor, false);
        }
        if (targetView != null) {
          targetView.invalidate();
        }
        break;
      }
      case ANIMATOR_SUBTEXT: {
        if (targetView != null) {
          targetView.invalidate();
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY: {
        if (callback != null) {
          callback.onApplyVisibility(this, visibilityAnimator.getValue(), finalFactor, true);
        }
        break;
      }
    }
  }

  @Override
  public void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading) {
    if (chatId == 0) {
      return;
    }
    this.location = location;
    this.heading = heading;
    if (isBroadcasting) {
      updateText(true);
    }
  }

  @Override
  public void onLiveLocationErrorState (boolean inErrorState) {
    if (chatId == 0 && inErrorState) {
      // TODO ?
    }
  }

  private boolean isBroadcasting;

  private void setIsBroadcasting (boolean isBroadcasting, boolean animated) {
    if (this.isBroadcasting != isBroadcasting) {
      this.isBroadcasting = isBroadcasting;
      updateText(animated);
    }
  }

  private Letters title1;
  private String subtitle1;
  private String trimmedTitle1, trimmedSubtitle1;
  private float titleWidth1, subtitleWidth1;

  private float maxWidth;

  private void trimText (boolean isSub) {
    if (targetView == null) {
      return;
    }
    maxWidth = targetView.getMeasuredWidth();
    float availWidth = maxWidth - Screen.dp(104f);
    Letters title;
    String subtitle;
    if (isSub) {
      title = title2; subtitle = subtitle2;
    } else {
      title = title1;
      subtitle = subtitle1;
    }
    String trimmedTitle, trimmedSubtitle;
    float titleWidth = 0, subtitleWidth= 0;
    if (availWidth > 0f && title != null && subtitle != null) {
      TextPaint titlePaint = Paints.getTitlePaint(title.needFakeBold);
      trimmedTitle = TextUtils.ellipsize(title.text, titlePaint, availWidth, TextUtils.TruncateAt.END).toString();
      titleWidth = U.measureText(trimmedTitle, titlePaint);
      availWidth -= titleWidth;
      if (availWidth > 0) {
        trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getTextPaint15(), availWidth, TextUtils.TruncateAt.END).toString();
        subtitleWidth = U.measureText(trimmedSubtitle, Paints.getTextPaint15());
      } else {
        trimmedSubtitle = null;
      }
    } else {
      trimmedTitle = null;
      trimmedSubtitle = null;
    }

    if (isSub) {
      this.trimmedTitle2 = trimmedTitle;
      this.trimmedSubtitle2 = trimmedSubtitle;
      this.titleWidth2 = titleWidth;
      this.subtitleWidth2 = subtitleWidth;
    } else {
      this.trimmedTitle1 = trimmedTitle;
      this.trimmedSubtitle1 = trimmedSubtitle;
      this.titleWidth1 = titleWidth;
      this.subtitleWidth1 = subtitleWidth;
    }
  }

  private boolean setText (String title, String subtitle) {
    boolean changed = false;
    if (!StringUtils.equalsOrBothEmpty(title1 != null ? title1.text : null, title)) {
      this.title1 = new Letters(title);
      changed = true;
    }
    if (!StringUtils.equalsOrBothEmpty(subtitle1, subtitle)) {
      this.subtitle1 = subtitle;
      changed = true;
    }
    if (changed) {
      trimText(false);
    }
    return changed;
  }

  private Letters title2;
  private String subtitle2;
  private String trimmedTitle2, trimmedSubtitle2;
  private float titleWidth2, subtitleWidth2;
  private boolean setSubtext (String title, String subtitle) {
    boolean changed = false;
    if (!StringUtils.equalsOrBothEmpty(title2 != null ? title2.text : null, title)) {
      this.title2 = new Letters(title);
      changed = true;
    }
    if (!StringUtils.equalsOrBothEmpty(subtitle2, subtitle)) {
      this.subtitle2 = subtitle;
      changed = true;
    }
    if (changed) {
      trimText(true);
    }
    return changed;
  }

  private void updateText (boolean animated) {
    if (targetView == null) {
      return;
    }
    String title = null;
    String subtitle = TdlibManager.instance().liveLocation().buildSubtext(tdlib, locationMessages, chatId, true, location);
    if (!StringUtils.isEmpty(subtitle)) {
      title = Lang.getString(R.string.AttachLiveLocation);
      subtitle = " " + subtitle;
    }

    String subtextTitle = null, subtextSubtitle = null;

    if (chatId != 0) {
      subtextSubtitle = TdlibManager.instance().liveLocation().buildSubtext(tdlib, locationMessages, chatId, false, location);
      if (subtextSubtitle != null) {
        subtextTitle = locationMessages.size() == 2 ?
          tdlib.senderName(locationMessages.get(tdlib.isSelfSender(locationMessages.get(0)) ? 1 : 0).senderId) :
          Lang.getString(R.string.AttachLiveLocation);
        subtextSubtitle = " " + subtextSubtitle;
      }
    }
    boolean changedText = false;
    if (!StringUtils.isEmpty(title)) {
      if (setText(title, subtitle)) {
        changedText = true;
      }
    }
    if (!StringUtils.isEmpty(subtextTitle)) {
      if (setSubtext(subtextTitle, subtextSubtitle)) {
        changedText = true;
      }
    }
    subtextAnimator.setValue(!StringUtils.isEmpty(subtextTitle), animated && visibilityAnimator.getFloatValue() > 0f);
    setIsVisible(!StringUtils.isEmpty(title) || !StringUtils.isEmpty(subtitle), animated);
    if (changedText && visibilityAnimator.getValue()) {
      targetView.invalidate();
    }
  }

  private void setIsVisible (boolean isVisible, boolean animated) {
    boolean nowVisible = this.visibilityAnimator.getValue();
    if (nowVisible != isVisible) {
      if (callback != null && !callback.onBeforeVisibilityStateChanged(this, isVisible, animated)) {
        animated = false;
      }
    }
    visibilityAnimator.setValue(isVisible, animated);
    if (nowVisible != isVisible && callback != null) {
      callback.onAfterVisibilityStateChanged(this, isVisible, animated);
    }
  }

  @Override
  public void onLiveLocationsLoaded (ArrayList<Tdlib> tdlibs, ArrayList<ArrayList<TdApi.Message>> messages) {
    int i = tdlibs.indexOf(tdlib);
    if (chatId == 0) {
      if (i != -1) {
        this.locationMessages = messages.get(i);
        updateText(false);
      }
    } else {
      boolean isBroadcasting = i != -1;
      if (isBroadcasting) {
        ArrayList<TdApi.Message> msgs = messages.get(i);
        isBroadcasting = false;
        for (TdApi.Message message : msgs) {
          if (message.chatId == this.chatId) {
            isBroadcasting = true;
            break;
          }
        }
      }
      setIsBroadcasting(isBroadcasting, false);
    }

  }

  @Override
  public void onLiveLocationsListChanged (Tdlib tdlib, @Nullable ArrayList<TdApi.Message> messages) {
    if (this.tdlib.id() == tdlib.id()) {
      if (chatId == 0) {
        this.locationMessages = messages;
        updateText(true);
      } else {
        boolean isBroadcasting = messages != null && !messages.isEmpty();
        if (isBroadcasting) {
          isBroadcasting = false;
          for (TdApi.Message message : messages) {
            if (message.chatId == this.chatId) {
              isBroadcasting = true;
              break;
            }
          }
        }
        setIsBroadcasting(isBroadcasting, true);
      }
    }
  }

  private int indexOfMessage (long chatId, long messageId) {
    if (locationMessages == null || locationMessages.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (TdApi.Message message : locationMessages) {
      if (message.chatId == chatId && message.id == messageId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  @Override
  public void onLiveLocationMessageEdited (Tdlib tdlib, TdApi.Message message) {
    if (this.tdlib.id() == tdlib.id()) {
      int i = indexOfMessage(message.chatId, message.id);
      if (i != -1) {
        //T ODO
      }
    }
  }

  // Draw

  public static int height () {
    return Screen.dp(36f);
  }

  public boolean isVisible () {
    return visibilityAnimator.getValue();
  }

  public float getVisibilityFactor () {
    return visibilityAnimator.getFloatValue();
  }

  private long nextScheduleTime;

  public void draw (Canvas c, int startY) {
    if (targetView == null) {
      return;
    }
    int viewWidth = targetView.getMeasuredWidth();
    if (maxWidth != viewWidth) {
      maxWidth = viewWidth;
      trimText(false);
      trimText(true);
    }

    float alphaFactor = visibilityAnimator.getFloatValue();
    int alpha = (int) (255f * alphaFactor);
    if (alpha == 0) {
      return;
    }

    int cx = Screen.dp(56) / 2;
    int cy = startY + height() / 2;

    final Paint iconPaint = onBackground ? Paints.getBackgroundIconPorterDuffPaint() : Paints.getIconGrayPorterDuffPaint();
    iconPaint.setAlpha(alpha);
    Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, iconPaint);
    iconPaint.setAlpha(255);

    int iconColor = onBackground ? Theme.backgroundIconColor() : Theme.iconColor();
    long delay = DrawAlgorithms.drawWaves(c, cx, cy, ColorUtils.color(alpha, iconColor), false, nextScheduleTime);
    if (delay != -1) {
      nextScheduleTime = SystemClock.uptimeMillis() + delay;
      int horizontalRadius = Screen.dp(15f);
      int verticalRadius = Screen.dp(24f);
      targetView.postInvalidateDelayed(delay, cx - horizontalRadius, cy - verticalRadius, cx + horizontalRadius, cy + verticalRadius);
    }

    cx = viewWidth - Screen.dp(50f) / 2;
    DrawAlgorithms.drawMark(c, cx, cy, 1f, Screen.dp(9f), Paints.getProgressPaint(ColorUtils.alphaColor(alphaFactor, iconColor),  Screen.dp(2f)));

    int titleColor, subtitleColor;
    if (onBackground) {
      titleColor = Theme.textDecentColor();
      subtitleColor = Theme.textDecent2Color();
    } else {
      titleColor = Theme.textAccentColor();
      subtitleColor = Theme.textDecentColor();
    }

    float subtextFactor = subtextAnimator.getFloatValue();
    if (subtextFactor != 1f && title1 != null) {
      drawText(c, startY, title1, trimmedTitle1, titleWidth1, trimmedSubtitle1, subtitleWidth1, titleColor, subtitleColor, alphaFactor, -subtextFactor);
    }
    if (subtextFactor != 0f && title2 != null) {
      drawText(c, startY, title2, trimmedTitle2, titleWidth2, trimmedSubtitle2, subtitleWidth2, titleColor, subtitleColor, alphaFactor, 1f - subtextFactor);

    }
  }

  private void drawText (Canvas c, int startY, Letters title, String trimmedTitle, float titleWidth, String trimmedSubtitle, float subtitleWidth, int titleColor, int subtitleColor, float alpha, float dismissFactor) {
    alpha = alpha * (1f - Math.abs(dismissFactor));
    final boolean saved = dismissFactor != 0;
    if (saved) {
      c.save();
      c.translate(0, height() * dismissFactor);
    }
    int textTop = startY + Screen.dp(23f);
    int textLeft = targetView.getMeasuredWidth() / 2 - (int) (titleWidth + subtitleWidth) / 2; // Screen.dp(72f);
    if (trimmedTitle != null) {
      Paint titlePaint = Paints.getTitlePaint(title.needFakeBold);
      final int sourceColor = titlePaint.getColor();
      titlePaint.setColor(ColorUtils.alphaColor(alpha, titleColor));
      c.drawText(trimmedTitle, textLeft, textTop, titlePaint);
      titlePaint.setColor(sourceColor);
    }
    if (trimmedSubtitle != null) {
      c.drawText(trimmedSubtitle, textLeft + titleWidth, textTop, Paints.getTextPaint15(ColorUtils.alphaColor(alpha, subtitleColor)));
    }
    if (saved) {
      c.restore();
    }
  }

  // Actions

  public boolean onTouchEvent (MotionEvent e) {
    return clickHelper != null && clickHelper.onTouchEvent(targetView, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    onClickAt(x, y);
  }

  public void onClickAt (float x, float y) {
    if (targetView != null) {
      if (x >= targetView.getMeasuredWidth() - Screen.dp(50f)) {
        stopLiveLocations(chatId, null);
      } else {
        openLiveLocationList(false);
      }
    }
  }

  public void stopLiveLocations (final long chatId, final Runnable after) {
    NavigationController navigationController = context.navigation();
    if (navigationController == null || navigationController.isEmpty()) {
      return;
    }
    ViewController<?> c = navigationController.getCurrentStackItem();
    if (c == null) {
      return;
    }
    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    IntList icons = new IntList(2);

    ids.append(R.id.btn_stopAllLiveLocations);
    strings.append(R.string.StopLiveLocation);
    icons.append(R.drawable.baseline_remove_circle_24);

    ids.append(R.id.btn_cancel);
    strings.append(R.string.Cancel);
    icons.append(R.drawable.baseline_cancel_24);

    CharSequence info;
    if (chatId != 0) {
      info = Lang.getStringBold(R.string.StopLiveLocationInfoX, tdlib.chatTitle(chatId));
    } else {
      info = Lang.getString(R.string.StopLiveLocationInfo);
    }

    c.showOptions(info, ids.get(), strings.get(), new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_stopAllLiveLocations: {
          tdlib.cache().stopLiveLocations(chatId);
          if (after != null) {
            after.run();
          }
          break;
        }
      }
      return true;
    });
  }

  private SettingsWrap lastPopup;

  public LiveLocationHelper openLiveLocationList (boolean forceList) {
    if (locationMessages == null || locationMessages.isEmpty()) {
      return this;
    }
    NavigationController navigationController = context.navigation();
    if (navigationController == null) {
      return this;
    }
    ViewController<?> c = context.navigation().getCurrentStackItem();
    if (c == null) {
      return this;
    }

    long chatId = this.chatId != 0 ? this.chatId : locationMessages.size() == 1 ? locationMessages.get(0).chatId : 0;
    long messageThreadId = this.chatId != 0 || this.messageThreadId != 0 ? this.messageThreadId : locationMessages.size() == 1 ? locationMessages.get(0).messageThreadId : 0;

    if (chatId != 0 && !forceList) {
      TdlibContext context = new TdlibContext(this.context, tdlib);

      TdApi.Message sourceMessage = locationMessages.get(0);
      TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) sourceMessage.content;

      MapController.Args args = new MapController.Args(messageLocation.location.latitude, messageLocation.location.longitude, sourceMessage).setChatId(chatId, messageThreadId).setNavigateBackOnStop(true);

      tdlib.ui().openMap(context, args);

      return this;
    }

    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.liveLocation);
    final SettingsWrap[] wrap = new SettingsWrap[1];

    b.setSaveStr(R.string.StopAllLocationSharings);
    b.setSaveColorId(R.id.theme_color_textNegative);
    b.addHeaderItem(Lang.plural(R.string.SharingLiveLocationToChats, locationMessages.size()));
    ListItem[] items = new ListItem[locationMessages.size() + 2];
    items[0] = items[items.length - 1] = new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)).setBoolValue(true);
    int i = 1;
    for (TdApi.Message message : locationMessages) {
      items[i] = new ListItem(ListItem.TYPE_LIVE_LOCATION_TARGET, i + 1).setData(message);
      i++;
    }
    b.setNeedSeparators(false);
    b.setRawItems(items);
    b.setDrawerProcessor((item, view, timerView, isUpdate) -> {
      TdApi.Message message = (TdApi.Message) item.getData();
      TdApi.Chat chat = tdlib.chat(message.chatId);
      view.setAvatar(tdlib, message.chatId);
      view.setText(tdlib.chatTitle(chat));
      view.setPreviewChatId(null, message.chatId, null, new MessageId(message.chatId, message.id), null);
      view.setPreviewActionListProvider(LiveLocationHelper.this);
      int livePeriod = ((TdApi.MessageLocation) message.content).livePeriod;
      long expiresInMs = Math.max((long) (message.date + livePeriod) * 1000l - System.currentTimeMillis(), 0l);
      timerView.setLivePeriod(livePeriod, expiresInMs > 0 ? SystemClock.uptimeMillis() + expiresInMs : 0);
    });
    b.setOnActionButtonClick((wrap1, view, isCancel) -> {
      if (!isCancel) {
        stopLiveLocations(0, () -> wrap1.window.hideWindow(true));
        return true;
      }
      return false;
    });
    b.setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
      TdApi.Message message = (TdApi.Message) item.getData();
      TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) message.content;
      MapController.Args args = new MapController.Args(messageLocation.location.latitude, messageLocation.location.longitude, message).setChatId(message.chatId, message.messageThreadId).setNavigateBackOnStop(true);
      tdlib.ui().openMap(new TdlibContext(context, tdlib), args);
      wrap[0].window.hideWindow(true);
    });
    b.setTdlibDelegate(new TdlibContext(context, tdlib));
    wrap[0] = lastPopup = c.showSettings(b);
    return this;
  }

  private boolean isDestroyed;

  public LiveLocationHelper destroy () {
    this.isDestroyed = true;
    tdlib.context().liveLocation().removeListener(this);
    if (chatId != 0) {
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
      handler.removeCallbacksAndMessages(null);
      visibilityAnimator.setValue(false, false);
      if (callback != null) {
        callback.onApplyVisibility(this, false, 0f, true);
      }
    }
    return this;
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    context.setAllowFullscreen(true);
    final ForceTouchView.MaximizeListener maximizeListener = context.getMaximizeListener();
    context.setMaximizeListener((target1, animateToWhenReady, arg) -> {
      if (lastPopup != null) {
        lastPopup.window.hideWindow(true);
        lastPopup = null;
      }
      return maximizeListener.onPerformMaximize(target1, animateToWhenReady, arg);
    });
    ids.append(R.id.btn_messageLiveStop);
    strings.append(R.string.StopLiveLocationShort);
    icons.append(R.drawable.baseline_remove_circle_24);
    return this;
  }

  @Override
  public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }

  @Override
  public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
    switch (actionId) {
      case R.id.btn_messageLiveStop: {
        stopLiveLocations(((MessagesController) arg).getChatId(), () -> {
          if (lastPopup != null) {
            lastPopup.window.hideWindow(true);
            lastPopup = null;
          }
        });
        break;
      }
    }
  }

  @Override
  public boolean handleMessage (Message msg) {
    removeChatMessage((TdApi.Message) msg.obj);
    return true;
  }

  // Chat stuff

  private void setChatMessages (ArrayList<TdApi.Message> messages) {
    if (isDestroyed) {
      return;
    }
    this.locationMessages = messages;
    if (messages != null && !messages.isEmpty()) {
      for (TdApi.Message message : messages) {
        scheduleRemoval(message, true);
      }
    }
    updateText(true);
    tdlib.context().liveLocation().addListener(this);
    tdlib.listeners().subscribeToMessageUpdates(chatId, this);
  }

  private void addChatMessage (TdApi.Message message) {
    if (isDestroyed) {
      return;
    }
    if (locationMessages == null) {
      locationMessages = new ArrayList<>();
    } else {
      int i = 0;
      for (TdApi.Message msg : locationMessages) {
        if (Td.equalsTo(msg.senderId, message.senderId)) {
          locationMessages.remove(i);
          scheduleRemoval(msg, false);
          break;
        }
        i++;
      }
    }
    locationMessages.add(message);
    scheduleRemoval(message, true);
    updateText(true);
  }

  private void editChatMessage (long messageId, TdApi.MessageLocation newContent) {
    if (isDestroyed) {
      return;
    }
    if (locationMessages == null || locationMessages.isEmpty()) {
      return;
    }
    if (newContent.expiresIn == 0) {
      removeChatMessages(new long[] {messageId});
    } else {
      for (TdApi.Message message : locationMessages) {
        if (message.id == messageId) {
          message.content = newContent;
          scheduleRemoval(message, false);
          scheduleRemoval(message, true);
          if (ChatId.isUserChat(chatId) && locationMessages.size() == 2) {
            updateText(true);
          }
          break;
        }
      }
    }
  }

  private void scheduleRemoval (TdApi.Message message, boolean schedule) {
    if (schedule) {
      handler.sendMessageDelayed(Message.obtain(handler, 0, message), (long) ((TdApi.MessageLocation) message.content).expiresIn * 1000l);
    } else {
      handler.removeMessages(0, message);
    }
  }

  private void removeChatMessage (TdApi.Message message) {
    if (isDestroyed) {
      return;
    }
    if (locationMessages == null || locationMessages.isEmpty()) {
      return;
    }
    int i = locationMessages.indexOf(message);
    if (i == -1) {
      return;
    }
    locationMessages.remove(i);
    updateText(true);
  }

  private void removeChatMessages (long[] messageIds) {
    if (isDestroyed) {
      return;
    }
    if (locationMessages == null || locationMessages.isEmpty()) {
      return;
    }

    int removedCount = 0;
    final int size = locationMessages.size();
    for (int i = size - 1; i >= 0; i--) {
      TdApi.Message msg = locationMessages.get(i);
      if (ArrayUtils.indexOf(messageIds, msg.id) != -1) {
        locationMessages.remove(i);
        scheduleRemoval(msg, false);
        if (++removedCount == messageIds.length) {
          break;
        }
      }
    }
    if (removedCount > 0) {
      updateText(true);
    }
  }

  @Override
  public void onNewMessage (TdApi.Message message) {
    if (!message.isOutgoing && message.sendingState == null && message.schedulingState == null && message.content.getConstructor() == TdApi.MessageLocation.CONSTRUCTOR && ((TdApi.MessageLocation) message.content).livePeriod > 0 && ((TdApi.MessageLocation) message.content).expiresIn > 0) {
      UI.post(() -> addChatMessage(message));
    }
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    if (message.content.getConstructor() == TdApi.MessageLocation.CONSTRUCTOR && message.schedulingState == null && ((TdApi.MessageLocation) message.content).livePeriod > 0 && ((TdApi.MessageLocation) message.content).expiresIn > 0) {
      UI.post(() -> addChatMessage(message));
    }
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (newContent.getConstructor() == TdApi.MessageLocation.CONSTRUCTOR && ((TdApi.MessageLocation) newContent).livePeriod > 0) {
      UI.post(() -> editChatMessage(messageId, (TdApi.MessageLocation) newContent));
    }
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    UI.post(() -> removeChatMessages(messageIds));
  }
}
