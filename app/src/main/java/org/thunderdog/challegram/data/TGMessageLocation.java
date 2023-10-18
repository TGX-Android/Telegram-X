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
 * File created on 03/05/2015 at 11:06
 */
package org.thunderdog.challegram.data;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.LiveLocationManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MapController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class TGMessageLocation extends TGMessage implements LiveLocationManager.UserLocationChangeListener {
  private final TdApi.Location point;
  private int livePeriod;
  private int initialExpiresIn;
  private final TdApi.Venue venue;

  private long aliveExpiresAt;

  private @Nullable ImageFile geoFile;
  private ImageFile previewFile;
  private Letters previewLetters;
  private float previewLettersWidth;
  private int previewAvatarColorId;

  private int previewWidth;
  private int previewHeight;

  private String trimmedTitle;
  private boolean needFakeTitle;
  private String trimmedSubtitle;

  public TGMessageLocation (MessagesManager context, TdApi.Message msg, TdApi.Location point, int livePeriod, int expiresInSeconds) {
    super(context, msg);
    this.point = point;
    this.venue = null;
    if (livePeriod != 0) {
      switch (msg.senderId.getConstructor()) {
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          long userId = ((TdApi.MessageSenderUser) msg.senderId).userId;
          updatePreviewUser(userId, tdlib.cache().user(userId));
          break;
        }
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          long chatId = ((TdApi.MessageSenderChat) msg.senderId).chatId;
          updatePreviewChat(chatId, tdlib.chat(chatId));
          break;
        }
        default:
          throw new AssertionError(msg.senderId.toString());
      }
      setLivePeriod(livePeriod, expiresInSeconds, true);
    }
  }

  private void setLivePeriod (int livePeriod, int expiresInSeconds, boolean isInitial) {
    cancelUpdate(SCHEDULE_FLAG_LIVE);
    this.livePeriod = livePeriod;
    this.initialExpiresIn = expiresInSeconds;
    if (expiresInSeconds > 0) {
      aliveExpiresAt = SystemClock.uptimeMillis() + (long) expiresInSeconds * 1000l;
      updateTimer();
    } else if (aliveExpiresAt > 0) {
      aliveExpiresAt = 0;
      updateTimer();
    }
    if (!isInitial) {
      if (Td.isLocation(msg.content)) {
        ((TdApi.MessageLocation) msg.content).expiresIn = expiresInSeconds;
      }
      checkAlive(true);
    }
    if (isUpdating) {
      scheduleAliveCheck();
    }
  }

  private void updatePreviewUser (long userId, TdApi.User user) {
    this.previewAvatarColorId = tdlib.cache().userAvatarColorId(user);
    if (user != null) {
      this.previewFile = TD.getAvatar(tdlib, user);
      this.previewLetters = TD.getLetters(user);
    } else {
      this.previewFile = null;
      this.previewLetters = TD.getLetters((TdApi.User) null);
    }
    this.previewLettersWidth = Paints.measureLetters(previewLetters, 18f);
  }

  private void updatePreviewChat (long chatId, TdApi.Chat chat) {
    this.previewFile = tdlib.chatAvatar(chatId);
    this.previewAvatarColorId = tdlib.chatAvatarColorId(chatId);
    this.previewLetters = tdlib.chatLetters(chatId);
    this.previewLettersWidth = Paints.measureLetters(previewLetters, 18f);
  }

  public TGMessageLocation (MessagesManager context, TdApi.Message msg, TdApi.Venue venue) {
    super(context, msg);
    this.point = venue.location;
    this.venue = venue;
    this.livePeriod = 0;
    if ("foursquare".equals(venue.provider)) {
      TdApi.FileType fileType = isSecretChat() ? new TdApi.FileTypeSecretThumbnail() : new TdApi.FileTypeThumbnail();
      String url = TD.getIconUrl(venue);
      if (url != null) {
        previewFile = new ImageFileRemote(tdlib, url, fileType);
        previewFile.setScaleType(ImageFile.FIT_CENTER);
      }
    }
  }

  @Override
  protected boolean allowBubbleHorizontalExtend () {
    return false;
  }

  @Override
  protected boolean preferFullWidth () {
    return UI.isPortrait() && !UI.isTablet() && isChannel();
  }

  protected boolean drawBubbleTimeOverContent () {
    return venue == null;
  }

  @Override
  protected boolean needBubbleCornerFix () {
    return true;
  }

  @Override
  public boolean isEdited () {
    return false;
  }

  /*@Override
  protected boolean replaceTimeWithEditTime () {
    return msg.editDate > msg.date;
  }*/

  @Override
  protected int getBottomLineContentWidth () {
    return venue == null ? BOTTOM_LINE_EXPAND_HEIGHT : BOTTOM_LINE_KEEP_WIDTH;
  }

  private long nextLiveLocationUpdateTime = -1;

  private static final long MAX_RECENT_TIME = 50000l;

  private String modifySubtitle (String subtitle) {
    if (compareWithLocation == null) {
      return subtitle;
    }
    StringBuilder b = new StringBuilder();
    float distance = U.distanceBetween(point.latitude, point.longitude, compareWithLocation.latitude, compareWithLocation.longitude);
    b.append(Lang.shortDistance(distance));
    b.append(Strings.DOT_SEPARATOR);
    b.append(subtitle);
    return b.toString();
  }

  public static class TimeResult {
    public final String text;
    public final long nextLiveLocationUpdateTime;

    public TimeResult (String text, long nextLiveLocationUpdateTime) {
      this.text = text;
      this.nextLiveLocationUpdateTime = nextLiveLocationUpdateTime;
    }
  }

  private String buildLiveLocationSubtitle () {
    TimeResult result = buildLiveLocationSubtitle(tdlib, Math.max(msg.date, msg.editDate));
    this.nextLiveLocationUpdateTime = result.nextLiveLocationUpdateTime;
    return result.text;
  }

  public static TimeResult buildLiveLocationSubtitle (Tdlib tdlib, int date) {
    long nowMillis = tdlib.currentTimeMillis();
    final long timeTillNextSubtitleUpdate = Lang.getNextRelativeDateUpdateMs(date, TimeUnit.SECONDS, nowMillis, TimeUnit.MILLISECONDS, true, 5);
    final String result = Lang.getRelativeDate(date, TimeUnit.SECONDS, nowMillis, TimeUnit.MILLISECONDS, true, 5, R.string.locationUpdated, false);
    long nextLiveLocationUpdateTime = timeTillNextSubtitleUpdate != -1 ? SystemClock.uptimeMillis() + Math.max(timeTillNextSubtitleUpdate, 0) : -1;
    return new TimeResult(result, nextLiveLocationUpdateTime);
  }

  private boolean isUpdating;
  private boolean locationListenerRegistered;

  private void setUpdating (boolean isUpdating) {
    if (this.isUpdating != isUpdating) {
      this.isUpdating = isUpdating;
      if (isUpdating) {
        if ((isAliveNow && !msg.isOutgoing)) {
          LiveLocationManager.LocationData locationData = tdlib.context().liveLocation().addLocationListener(this);
          if (locationData != null) {
            compareWithLocation = locationData.location;
            compareWithHeading = locationData.heading;
          } else {
            compareWithLocation = null;
            compareWithHeading = 0;
          }
          locationListenerRegistered = true;
        }
        boolean updated = updateLiveSubtitle(false);
        if (!updated) {
          updated = updateModifiedSubtitle();
        }
        updated = updateTimer() || updated;
        checkAlive(true);
        scheduleAliveCheck();
        if (updated) {
          invalidate();
        }
      } else {
        cancelAllUpdates();
        if (locationListenerRegistered) {
          tdlib.context().liveLocation().removeLocationListener(this);
          locationListenerRegistered = false;
        }
      }
    }
  }

  @Override
  public void onLiveLocationBroadcast (@Nullable TdApi.Location location, int heading) {
    this.compareWithLocation = location;
    if (updateModifiedSubtitle()) {
      invalidate();
    }
  }

  private boolean updateModifiedSubtitle () {
    if (originalSubtitle != null) {
      String newSubtitle = modifySubtitle(originalSubtitle);
      if (!StringUtils.equalsOrBothEmpty(newSubtitle, this.subtitle)) {
        this.subtitle = newSubtitle;
        if (maxSubtitleWidth > 0) {
          trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getSubtitlePaint(), maxSubtitleWidth, TextUtils.TruncateAt.END).toString();
        }
        return true;
      }
    }
    return false;
  }

  @Override
  protected void onMessageAttachStateChange (boolean isAttached) {
    setUpdating(isAttached && isAliveNow);
  }

  private String subtitle, originalSubtitle;
  private float maxSubtitleWidth;

  @Override
  protected boolean onMessageEdited (long messageId, int editDate) {
    return updateLiveSubtitle(true);
  }

  private String timer;
  private float timerWidth;
  private long nextTimerUpdateTime = -1;

  private String buildTimer () {
    if (aliveExpiresAt == 0) {
      nextTimerUpdateTime = -1;
      return null;
    }
    long now = SystemClock.uptimeMillis();
    long millisRemaining = aliveExpiresAt - now;
    if (millisRemaining <= 0) {
      aliveExpiresAt = 0;
      nextTimerUpdateTime = -1;
      return null;
    }

    long timeTillNextTimerUpdate;
    String res;
    if (millisRemaining <= 60000) {
      int seconds = (int) (millisRemaining / 1000l);
      timeTillNextTimerUpdate = 1000; // - millisRemaining % 1000;
      res = Integer.toString(seconds);
    } else if (millisRemaining < 60000 * 60) {
      int minutes = (int) ((double) (millisRemaining / 1000l) / 60.0);
      timeTillNextTimerUpdate = 60000 - millisRemaining % 60000;
      res = Integer.toString(minutes);
    } else {
      int hours = (int) Math.ceil((double) (millisRemaining / 1000l / 60l) / 60.0);
      timeTillNextTimerUpdate = (60000 * 60) - millisRemaining % (60000 * 60);
      res = hours + "h";
    }

    nextTimerUpdateTime = now + timeTillNextTimerUpdate;
    return res;
  }

  private static final float TIMER_TEXT_SIZE = 13f;

  private boolean updateTimer () {
    boolean isScheduled = (scheduleFlags & SCHEDULE_FLAG_TIMER) != 0;
    String newTimer = buildTimer();
    boolean updated = false;
    if ((timer == null) != (newTimer == null) || !StringUtils.equalsOrBothEmpty(timer, newTimer)) {
      this.timer = newTimer;
      this.timerWidth = U.measureText(timer, Paints.whiteMediumPaint(TIMER_TEXT_SIZE, false, true));
      updated = true;
    }
    if (!isScheduled && isUpdating) {
      scheduleUpdate(SCHEDULE_FLAG_TIMER, false, nextTimerUpdateTime - SystemClock.uptimeMillis());
    } else if (isScheduled && nextTimerUpdateTime == 0) {
      cancelUpdate(SCHEDULE_FLAG_TIMER);
    }
    return updated;
  }

  private boolean updateLiveSubtitle (boolean force) {
    if (!force && (subtitle == null || nextLiveLocationUpdateTime == -1)) {
      return false;
    }
    boolean isScheduled = (scheduleFlags & SCHEDULE_FLAG_SUBTITLE) != 0;
    if (!force && isScheduled && SystemClock.uptimeMillis() < nextLiveLocationUpdateTime) {
      return false;
    }
    String originalSubtitle;
    String newSubtitle = modifySubtitle(originalSubtitle = buildLiveLocationSubtitle());
    final boolean changed;
    if (changed = !StringUtils.equalsOrBothEmpty(newSubtitle, subtitle)) {
      this.subtitle = newSubtitle;
      this.originalSubtitle = originalSubtitle;
      if (maxSubtitleWidth > 0) {
        trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getSubtitlePaint(), maxSubtitleWidth, TextUtils.TruncateAt.END).toString();
      }
    }
    if (!isScheduled && isUpdating) {
      long updateDelay = nextLiveLocationUpdateTime - SystemClock.uptimeMillis();
      scheduleUpdate(SCHEDULE_FLAG_SUBTITLE, false, updateDelay);
    } else if (!isUpdating) {
      cancelUpdate(SCHEDULE_FLAG_SUBTITLE);
    }
    return changed;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.Location location;
    int newLivePeriod;
    int newExpiresIn;
    switch (newContent.getConstructor()) {
      case TdApi.MessageVenue.CONSTRUCTOR:
        location = ((TdApi.MessageVenue) newContent).venue.location;
        newLivePeriod = livePeriod;
        newExpiresIn = initialExpiresIn;
        break;
      case TdApi.MessageLocation.CONSTRUCTOR:
        TdApi.MessageLocation messageLocation = (TdApi.MessageLocation) newContent;
        location = messageLocation.location;
        newLivePeriod = messageLocation.livePeriod;
        newExpiresIn = messageLocation.expiresIn;
        break;
      default:
        return false;
    }
    boolean changed = false;
    if (this.point.latitude != location.latitude || this.point.longitude != location.longitude) {
      this.point.latitude = location.latitude;
      this.point.longitude = location.longitude;
      if (previewWidth > 0 && previewHeight > 0) {
        buildGeoFile(true);
      }
      changed = true;
    }
    if (newLivePeriod != livePeriod || newExpiresIn != initialExpiresIn) {
      setLivePeriod(newLivePeriod, newExpiresIn, false);
      changed = true;
    }
    return changed;
  }

  /*private boolean isAliveLocal () {
    return ;
  }*/

  public boolean canStopAlive () {
    return msg.canBeEdited && livePeriod > 0 && checkAlive(true);
  }

  public void stopLiveLocation () {
    if (canStopAlive()) {
      tdlib.client().send(new TdApi.EditMessageLiveLocation(msg.chatId, msg.id, msg.replyMarkup, null, 0, 0), tdlib.silentHandler());
    }
  }

  private boolean checkAlive (boolean rebuild) {
    boolean res;
    if (res = (livePeriod > 0 && aliveExpiresAt > 0)) {
      if (aliveExpiresAt <= SystemClock.uptimeMillis()) {
        aliveExpiresAt = 0;
        res = false;
      }
    }
    if (rebuild && res != isAliveNow) {
      rebuildAndUpdateContent();
    }
    return res;
  }

  private void scheduleAliveCheck () {
    if (aliveExpiresAt > 0) {
      scheduleUpdate(SCHEDULE_FLAG_LIVE, true, aliveExpiresAt - SystemClock.uptimeMillis());
    }
  }

  private boolean isAliveNow;
  private TdApi.Location compareWithLocation;
  private int compareWithHeading;

  @Override
  protected void buildContent (int maxWidth) {
    float factor = (float) U.MAP_HEIGHT / (float) U.MAP_WIDTH;

    this.previewWidth = useFullWidth() ? maxWidth : getSmallestMaxContentWidth();
    this.previewHeight = (int) ((float) previewWidth * factor);
    this.isAliveNow = checkAlive(false);

    String title;
    if (venue != null) {
      title = venue.title;
      this.subtitle = modifySubtitle(this.originalSubtitle = venue.address);
      this.timer = null;
    } else if (isAliveNow) {
      title = Lang.getString(R.string.AttachLiveLocation);
      this.subtitle = modifySubtitle(this.originalSubtitle = buildLiveLocationSubtitle());
      this.timer = buildTimer();
    } else {
      title = this.subtitle = this.originalSubtitle = this.timer = null;
    }
    if (title != null && subtitle != null) {
      boolean useBubbles = useBubbles();
      int textPaddingLeft = Screen.dp(11f);
      int circleRadius = Screen.dp(20f);
      int horizontalPadding = (useFullWidth() ? xContentLeft : 0);
      int maxTextWidth = maxWidth - horizontalPadding * 2;
      if (useBubbles) {
        maxTextWidth -= (xBubblePadding - xBubblePaddingSmall) * 2;
      } else if (NEED_VENUE_CIRCLE) {
        maxTextWidth -= textPaddingLeft + circleRadius * 2;
      }

      if (timer != null) {
        maxTextWidth -= Screen.dp(TIMER_RADIUS) + Screen.dp(22);
        if (!useBubbles) {
          maxTextWidth -= Screen.dp(4f);
        }
      }

      needFakeTitle = Text.needFakeBold(title);
      trimmedTitle = TextUtils.ellipsize(title, Paints.getTitlePaint(needFakeTitle), maxTextWidth, TextUtils.TruncateAt.END).toString();
      if (useBubbles && venue != null) {
        maxTextWidth -= computeBubbleTimePartWidth(true);
      }
      trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getSubtitlePaint(), maxSubtitleWidth = maxTextWidth, TextUtils.TruncateAt.END).toString();

      if (useFullWidth()) {
        previewHeight -= circleRadius * 2 - Screen.dp(9f);
      }
    } else {
      this.trimmedTitle = this.trimmedSubtitle = null;
    }

    buildGeoFile(false);
  }

  private void buildGeoFile (boolean invalidate) {
    int mapProviderType = Settings.instance().getMapProviderType(!isSecretChat());

    if (mapProviderType == Settings.MAP_PROVIDER_NONE || mapProviderType == Settings.MAP_PROVIDER_UNSET) {
      if (!isSecretChat()) {
        mapProviderType = Settings.MAP_PROVIDER_DEFAULT_CLOUD;
      }
    }

    switch (mapProviderType) {
      case Settings.MAP_PROVIDER_UNSET:
        tdlib.ui().post(() -> {
          tdlib.ui().showMapProviderSettings(controller(), TdlibUi.MAP_PROVIDER_MODE_SECRET_TUTORIAL, () -> {
            if (!isDestroyed()) {
              buildGeoFile(true);
            }
          });
        });
        break;
      case Settings.MAP_PROVIDER_NONE:
        geoFile = null;
        break;
      case Settings.MAP_PROVIDER_TELEGRAM: {
        int w = previewWidth;
        int h = previewHeight;

        if (w > 1024 || h > 1024) {
          float scale = 1024f / (float) Math.max(w, h);
          w *= scale;
          h *= scale;
        }
        w = Math.max(14, w);
        h = Math.max(14, h);

        int scale = Screen.density() >= 2.0f ? 2 : 1;
        w /= scale;
        h /= scale;

        this.geoFile = new ImageFileRemote(tdlib, new TdApi.GetMapThumbnailFile(point, 16, w, h, scale, getChatId()), "telegram_map_" + point.latitude + "," + point.longitude);
        this.geoFile.setScaleType(ImageFile.CENTER_CROP);
        break;
      }
      case Settings.MAP_PROVIDER_GOOGLE: {
        String url = U.getMapPreview(tdlib, point.latitude, point.longitude, 16, false, previewWidth, previewHeight, null);
        this.geoFile = new ImageFileRemote(tdlib, url, isSecretChat() ? new TdApi.FileTypeSecretThumbnail() : new TdApi.FileTypeThumbnail());
        this.geoFile.setScaleType(ImageFile.CENTER_CROP);
        break;
      }
    }


    if (invalidate) {
      if (geoFile != null)
        this.geoFile.setSuppressEmptyBundle(true);
      invalidatePreviewReceiver();
      if (geoFile != null)
        this.geoFile.setSuppressEmptyBundle(false);
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.requestFile(null, geoFile);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(previewFile);
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePaddingSmall;
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return !isPreview && venue == null && livePeriod > 0 ? Screen.dp(26f) : 0;
  }

  private static final boolean NEED_VENUE_CIRCLE = false;
  private static final float PIN_RADIUS = 26f;

  @Override
  protected void drawBubbleTimePart (Canvas c, MessageView view) {
    if (livePeriod <= 0 || aliveExpiresAt == 0) {
      super.drawBubbleTimePart(c, view);
    }
  }

  private static final float MAX_PULSE_RADIUS = 42f;
  private static final long PULSE_DURATION = 2000l;
  private static final long PULSE_PAUSE_DURATION = 1000l;
  private long nextPulseTimeMs;
  private int pulseCenterX, pulseCenterY;

  private static final float TIMER_RADIUS = 12f;
  private int timerCenterX, timerCenterY;

  private static final int SCHEDULE_FLAG_PULSE = 1;
  private static final int SCHEDULE_FLAG_SUBTITLE = 1 << 1;
  private static final int SCHEDULE_FLAG_TIMER = 1 << 2;
  private static final int SCHEDULE_FLAG_CIRCLE = 1 << 3;
  private static final int SCHEDULE_FLAG_LIVE = 1 << 4;
  private int scheduleFlags;

  @Override
  public void handleUiMessage (int what, int arg1, int arg2) {
    scheduleFlags &= ~what;
    switch (what) {
      case SCHEDULE_FLAG_PULSE: {
        int maxRadius = Screen.dp(MAX_PULSE_RADIUS);
        invalidate(pulseCenterX - maxRadius, pulseCenterY - maxRadius, pulseCenterX + maxRadius, pulseCenterY+ maxRadius);
        break;
      }
      case SCHEDULE_FLAG_SUBTITLE:
        if (updateLiveSubtitle(false)) {
          invalidate();
        }
        break;
      case SCHEDULE_FLAG_TIMER:
        if (updateTimer()) {
          invalidateTimer();
        }
        break;
      case SCHEDULE_FLAG_CIRCLE:
        invalidateTimer();
        break;
      case SCHEDULE_FLAG_LIVE: {
        checkAlive(true);
        scheduleAliveCheck();
        break;
      }
    }
  }

  private void scheduleUpdate (int flag, boolean force, long delay) {
    boolean isScheduled = (scheduleFlags & flag) != 0;
    if (!isScheduled || force) {
      if (isScheduled) {
        tdlib.cancelUiMessageActions(this, flag);
      }
      scheduleFlags |= flag;
      tdlib.scheduleUiMessageAction(this, flag, 0, 0, delay);
    }
  }

  private void cancelUpdate (int flag) {
    if ((scheduleFlags & flag) != 0) {
      tdlib.cancelUiMessageActions(this, flag);
      scheduleFlags &= ~flag;
    }
  }

  private void cancelAllUpdates () {
    cancelUpdate(SCHEDULE_FLAG_PULSE);
    cancelUpdate(SCHEDULE_FLAG_SUBTITLE);
    cancelUpdate(SCHEDULE_FLAG_TIMER);
    cancelUpdate(SCHEDULE_FLAG_SUBTITLE);
    cancelUpdate(SCHEDULE_FLAG_CIRCLE);
    cancelUpdate(SCHEDULE_FLAG_LIVE);
  }

  private void invalidateTimer () {
    if (timer != null && timerCenterX != 0 && timerCenterY != 0) {
      int timerRadius = Screen.dp(TIMER_RADIUS);
      invalidate(timerCenterX - timerRadius, timerCenterY - timerRadius, timerCenterX + timerRadius, timerCenterY + timerRadius);
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    super.onMessageContainerDestroyed();
    cancelAllUpdates();
  }

  private float pulseFactor;

  @Override
  protected void drawContent (MessageView view, Canvas c, final int startX, int startY, int maxWidth, Receiver mapReceiver, Receiver iconReceiver) {
    boolean useBubbles = useBubbles();
    final boolean clipped = useBubbles && !useForward();
    final int saveCount = clipped ? ViewSupport.clipPath(c, getBubbleClipPath()) : Integer.MIN_VALUE;
    boolean useFullWidth = useFullWidth();

    mapReceiver.setBounds(getContentX(), getContentY(), getContentX() + previewWidth, getContentY() + previewHeight);
    if (mapReceiver.needPlaceholder()) {
      c.drawRect(startX, startY, startX + previewWidth, startY + previewHeight, Paints.getPlaceholderPaint());
    }
    mapReceiver.draw(c);

    int mapCenterX = startX + previewWidth / 2;
    int mapCenterY = startY + previewHeight / 2;
    c.save();
    c.scale(.85f, .85f, mapCenterX, mapCenterY);

    Bitmap pinBgIcon = Icons.getLivePin();
    int pinTop = mapCenterY - pinBgIcon.getHeight() + Screen.dp(8f);
    int pinCenterY = pinTop + Screen.dp(31f);
    int pinRadius = Screen.dp(PIN_RADIUS);

    int maxPulseRadius = Screen.dp(MAX_PULSE_RADIUS);
    long pulseUpdateDelay = -1;
    if (livePeriod > 0 && ((pulseFactor > 0f && pulseFactor < 1f) || aliveExpiresAt > 0)) {
      long now = SystemClock.elapsedRealtime();
      long time = now % (PULSE_DURATION + PULSE_PAUSE_DURATION);
      if (time >= PULSE_DURATION) {
        pulseFactor = 1f;
        pulseUpdateDelay = Math.max(0, PULSE_PAUSE_DURATION - (time - PULSE_DURATION));
      } else {
        pulseFactor = time == 0 ? 0f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((float) ((double) time / (double) PULSE_DURATION));
        pulseUpdateDelay = Math.max(U.calculateDelayForDistance(maxPulseRadius, PULSE_DURATION), ValueAnimator.getFrameDelay());
      }
      if ((pulseFactor == 0f || pulseFactor == 1f) && aliveExpiresAt == 0) {
        pulseUpdateDelay = -1;
      }
    }

    if (pulseFactor > 0f && pulseFactor < 1f) {
      float step = .35f;
      float alpha = .45f * (pulseFactor < step ? 1f : 1f - (pulseFactor - step) / (1f - step));
      float radius = (float) maxPulseRadius * pulseFactor;
      int color = ColorUtils.alphaColor(alpha, Theme.getColor(ColorId.file));
      c.drawCircle(mapCenterX, mapCenterY + Screen.dp(1f), radius, Paints.fillingPaint(color));
    }

    if (pulseUpdateDelay != -1 && (scheduleFlags & SCHEDULE_FLAG_PULSE) == 0) {
      this.pulseCenterX = mapCenterX;
      this.pulseCenterY = mapCenterY;
      scheduleUpdate(SCHEDULE_FLAG_PULSE, false, pulseUpdateDelay);
    }

    c.drawBitmap(pinBgIcon, mapCenterX - pinBgIcon.getWidth() / 2, pinTop, Paints.getBitmapPaint());

    int iconSize;
    if (livePeriod > 0) {
      iconSize = pinRadius;
      if (previewFile == null && previewLetters != null) {
        c.drawCircle(mapCenterX, pinCenterY, pinRadius, Paints.fillingPaint(Theme.getColor(previewAvatarColorId)));
        Paints.drawLetters(c, previewLetters, mapCenterX - previewLettersWidth / 2, pinCenterY + Screen.dp(7f), 18f);
      }
    } else {
      iconSize = Screen.dp(16f);
      c.drawCircle(mapCenterX, pinCenterY, pinRadius, Paints.fillingPaint(Theme.getColor(ColorId.file)));

      if (iconReceiver.needPlaceholder()) {
        float iconAlpha = 1f - ((ImageReceiver) iconReceiver).getDisplayAlpha();
        Paint paint = Paints.whitePorterDuffPaint();
        if (iconAlpha != 1f) {
          paint.setAlpha((int) (255f * iconAlpha));
        }
        Drawable pinIcon = view.getSparseDrawable(R.drawable.baseline_location_on_24, 0);
        Drawables.draw(c, pinIcon, mapCenterX - pinIcon.getMinimumWidth() / 2, pinCenterY - pinIcon.getMinimumHeight() / 2, paint);
        if (iconAlpha != 1f) {
          paint.setAlpha(255);
        }
      }
    }
    if (previewFile != null) {
      iconReceiver.setBounds(mapCenterX - iconSize, pinCenterY - iconSize, mapCenterX + iconSize, pinCenterY + iconSize);
      iconReceiver.draw(c);
    }
    if (livePeriod > 0 && !isAliveNow) {
      c.drawCircle(mapCenterX, pinCenterY, pinRadius, Paints.fillingPaint(ColorUtils.alphaColor(.75f, 0xffffffff)));
    }

    c.restore();


    if (!useBubbles && !useFullWidth) {
      RectF rectF = Paints.getRectF();

      int padding = Screen.dp(2f);
      rectF.set(mapReceiver.getLeft() - padding, mapReceiver.getTop() - padding, mapReceiver.getRight() + padding, mapReceiver.getBottom() + padding);
      int radius = Screen.dp(Theme.getImageRadius());
      TGMessage.drawCornerFixes(c, this, 1f,
        mapReceiver.getLeft(), mapReceiver.getTop(), mapReceiver.getRight(), mapReceiver.getBottom(),
        radius, radius, radius, radius
      );
    }

    if (clipped) {
      ViewSupport.restoreClipPath(c, saveCount);
    }

    if (venue != null || trimmedTitle != null || trimmedSubtitle != null) {
      int venueContentX = useFullWidth ? xContentLeft : startX;
      int venueContentY = startY + previewHeight;
      int paddingTop = Screen.dp(4f);
      int textX = venueContentX;
      if (useBubbles) {
        textX += xBubblePadding - xBubblePaddingSmall;
      }/* else if (NEED_VENUE_CIRCLE && venue != null) {
        int circleRadius = Screen.dp(20f);
        int centerY = venueContentY + paddingTop + circleRadius;
        c.drawCircle(venueContentX + circleRadius, centerY, circleRadius, Paints.fillingPaint(Theme.getColor(ColorId.circleButtonRegular)));
        final Bitmap icon = Icons.getLocationIcon();
        c.drawBitmap(icon, venueContentX + circleRadius - icon.getWidth() / 2, centerY - icon.getHeight() / 2, Paints.getBitmapPaint());
        textX += circleRadius * 2 + Screen.dp(11f);
      }*/
      if (trimmedTitle != null) {
        c.drawText(trimmedTitle, textX, venueContentY + paddingTop + Screen.dp(17f), Paints.getTitlePaint(needFakeTitle));
      }
      boolean align = isOutgoingBubble();
      if (trimmedSubtitle != null) {
        c.drawText(trimmedSubtitle, textX, venueContentY + paddingTop + Screen.dp(35f), Paints.getRegularTextPaint(13f, getDecentColor()));
      }

      if (timer != null) {
        timerCenterX = useFullWidth ? view.getMeasuredWidth() - xContentLeft : startX + previewWidth - (useBubbles ? Screen.dp(22) : Screen.dp(16f));
        timerCenterY = venueContentY + paddingTop + Screen.dp(22f);
        int timerRadius = Screen.dp(TIMER_RADIUS);
        int color = Theme.getColor(getProgressColorId());

        long millisRemaining = aliveExpiresAt - SystemClock.uptimeMillis();
        float doneFactor = MathUtils.clamp((float) ((double) millisRemaining / (double) (livePeriod * 1000l)));
        int strokeSize = Screen.dp(1.5f);

        int degrees = (int) (360f * doneFactor);
        if (degrees == 360) {
          c.drawCircle(timerCenterX, timerCenterY, timerRadius, Paints.getProgressPaint(color, strokeSize));
        } else {
          c.drawCircle(timerCenterX, timerCenterY, timerRadius, Paints.getProgressPaint(ColorUtils.alphaColor(.25f, color), strokeSize));
          RectF rectF = Paints.getRectF();
          rectF.set(timerCenterX - timerRadius, timerCenterY - timerRadius, timerCenterX + timerRadius, timerCenterY + timerRadius);
          c.drawArc(rectF, MathUtils.modulo((-90 + (360 - degrees)), 360), degrees, false, Paints.getProgressPaint(color, strokeSize));
        }

        if ((scheduleFlags & SCHEDULE_FLAG_CIRCLE) == 0) {
          long delay = U.calculateDelayForDiameter(timerRadius * 2, (long) livePeriod * 1000l);
          scheduleUpdate(SCHEDULE_FLAG_CIRCLE, false, delay);
        }

        Paint textPaint = Paints.whiteMediumPaint(TIMER_TEXT_SIZE, false, false);
        if (color != 0xfffffff) {
          textPaint.setColor(color);
        }
        c.drawText(timer, timerCenterX - timerWidth / 2, timerCenterY + Screen.dp(4f), textPaint);
        if (color != 0xffffffff) {
          textPaint.setColor(0xffffffff);
        }
      }
    }
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  protected int getContentWidth () {
    return previewWidth;
  }

  @Override
  protected int getContentHeight () {
    return venue != null || isAliveNow ? previewHeight + Screen.dp(useBubbles() ? 9f : 4f) + Screen.dp(20f) * 2 : previewHeight;
  }

  private float currentTouchX, currentTouchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) return true;

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();

        int left = getContentX();
        int top = getContentY();

        if (x >= left && x <= left + previewWidth && y >= top && y <= top + previewHeight) {
          currentTouchX = x;
          currentTouchY = y;
          return true;
        }

        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_UP: {
        if (currentTouchX != 0f && currentTouchY != 0f) {
          if (Math.abs(e.getX() - currentTouchX) < Screen.getTouchSlop() && Math.abs(e.getY() - currentTouchY) < Screen.getTouchSlop()) {
            MapController.Args args;
            if (livePeriod > 0) {
              args = new MapController.Args(point.latitude, point.longitude, msg);
            } else {
              args = new MapController.Args(point.latitude, point.longitude);
            }
            args.setChatId(msg.chatId, messagesController().getMessageThreadId());
            if (venue != null) {
              args.title = venue.title;
              args.address = venue.address;
              args.iconImage = previewFile;
            }
            ViewUtils.onClick(view);
            if (isSecretChat()) {
              int type = Settings.instance().getMapProviderType(false);
              if (type != Settings.MAP_PROVIDER_GOOGLE) {
                double latitude = point.latitude;
                double longitude = point.longitude;
                controller().showOptions(latitudeStr(latitude) + " " + longitudeStr(longitude), new int[] {R.id.btn_open, R.id.btn_copyText, R.id.btn_openIn}, new String[] {Lang.getString(R.string.OpenMap), Lang.getString(R.string.CopyCoordinates), Lang.getString(R.string.OpenInExternalApp)}, null, new int[] {R.drawable.baseline_map_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_open_in_browser_24}, (itemView, id) -> {
                  if (id == R.id.btn_copyText) {
                    UI.copyText(String.format(Locale.US, "%f,%f", latitude, longitude), R.string.CopiedCoordinates);
                  } else if (id == R.id.btn_open) {
                    if (tdlib.ui().openMap(TGMessageLocation.this, args)) {
                      readContent();
                    }
                  } else if (id == R.id.btn_openIn) {
                    if (Intents.openMap(latitude, longitude, args.title, args.address)) {
                      readContent();
                    }
                  }
                  return true;
                });
                return true;
              }
            }
            if (tdlib.ui().openMap(this, args)) {
              readContent();
            }
            return true;
          }
          currentTouchX = 0f;
          currentTouchY = 0f;
        }
        break;
      }
    }
    return false;
  }

  private static String latitudeStr (double latitude) {
    String direction = (latitude > 0) ? "N" : "S";
    String str = Location.convert(Math.abs(latitude), Location.FORMAT_SECONDS);
    str = replaceDelimiters(str, 2);
    str += direction;
    return str;
  }

  private static String longitudeStr (double longitude) {
    String direction = (longitude > 0) ? "W" : "E";
    String str = Location.convert(Math.abs(longitude), Location.FORMAT_SECONDS);
    str = replaceDelimiters(str, 2);
    str += direction;
    return str;
  }

  private static String replaceDelimiters(String str, int decimalPlace) {
    str = str.replaceFirst(":", "°");
    str = str.replaceFirst(":", "'");
    int pointIndex = str.indexOf(".");
    int endIndex = pointIndex + 1 + decimalPlace;
    if (endIndex < str.length()) {
      str = str.substring(0, endIndex);
    }
    str += "\"";
    return str;
  }
}
