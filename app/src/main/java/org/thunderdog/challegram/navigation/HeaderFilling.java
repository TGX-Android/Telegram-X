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
 * File created on 17/08/2015 at 19:33
 */
package org.thunderdog.challegram.navigation;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.CallManager;
import org.thunderdog.challegram.telegram.TGLegacyAudioManager;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.ui.PlaybackController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.BitwiseUtils;

public class HeaderFilling extends Drawable implements TGLegacyAudioManager.PlayListener, FactorAnimator.Target, CallManager.CurrentCallListener, TdlibCache.CallStateChangeListener, Runnable, TGPlayerController.TrackChangeListener, TGPlayerController.TrackListener, ClickHelper.Delegate, Destroyable, TGLegacyManager.EmojiLoadListener {
  private HeaderView headerView; // Header that holds the filling
  private @Nullable NavigationController navigationController;

  // Filling

  private int color;

  private int width, height;
  private float fillingBottom;
  private float shadowTop;

  // private Paint bgPaint;

  private boolean isOutlineBig;
  private boolean outlineBigForced;

  // Player

  private static final int SECTION_AUDIO = 1 << 1;
  private static final int SECTION_CALL = 1 << 2;

  private Tdlib playingMessageTdlib;
  private TdApi.Message playingMessage;

  private float fillFactor;

  private float hideFactor;
  private float showFactor;
  private boolean showOngoingBar;
  private int ongoingSections;

  private Drawable hangIcon, micIcon, forwardIcon;

  private int lastBarType;
  private int textLeft;
  private int playerTop, playerBottom;

  private boolean needOffsets;

  public void setNeedOffsets () {
    needOffsets = true;
  }

  private int getTopOffset () {
    return needOffsets ? HeaderView.getTopOffset() : 0;
  }

  private final ClickHelper helper;

  public HeaderFilling (HeaderView parent, @Nullable NavigationController navigationController) {
    this.headerView = parent;
    this.navigationController = navigationController;

    if (headerView != null) {
      helper = new ClickHelper(this);
      helper.setNoSound(true);
    } else {
      helper = null;
    }

    this.fillFactor = 1f;

    this.micIcon = Drawables.get(parent.getResources(), R.drawable.baseline_mic_24);
    this.hangIcon = Drawables.get(parent.getResources(), R.drawable.baseline_call_end_24);
    this.forwardIcon = Drawables.get(parent.getResources(), R.drawable.baseline_fast_forward_24);

    this.textLeft = Screen.dp(72f);

    if (navigationController != null) {
      TdlibManager.instance().player().addTrackChangeListener(this);
      initCallListeners();
    }
    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidateOngoingBar();
  }

  @Override
  public void performDestroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  // Component stuff

  public int getExtraHeight () { // Called to get the extra height of the header
    return ShadowView.simpleBottomShadowHeight();
  }

  public void forceBigOutline (boolean isBig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (outlineBigForced != isBig) {
        outlineBigForced = isBig;
        headerView.invalidateOutline();
      }
    }
  }

  public int getOutlineBottom () {
    return outlineBigForced ? Size.getHeaderPortraitSize() + Size.getMaximumHeaderSizeDifference() : isOutlineBig ? Math.min((int) fillingBottom, Size.getHeaderPortraitSize() + Size.getMaximumHeaderSizeDifference()) : Size.getHeaderPortraitSize();
  }

  public int getBottom () {
    return height + (int) (HeaderView.getPlayerSize() * showFactor);
  }

  public int getPlayerOffset () {
    return (int) (HeaderView.getPlayerSize() * showFactor);
  }

  public void setCollapsed (boolean collapsed) {
    if (collapsed) {
      if (showFactor != 0f) {
        hideFactor = 1f;
      }
      fillFactor = 0f;
    } else {
      hideFactor = 0f;
      fillFactor = 1f;
    }
    invalidate();
  }

  public void collapseFilling (float px) {
    boolean updated = false;
    if (showFactor != 0f || true) {
      if (px > playerBottom) {
        if (fillFactor != 1f || hideFactor != 0f) {
          fillFactor = 1f;
          hideFactor = 0f;
          updated = true;
        }
      } else {
        float x = px - playerTop;
        hideFactor = x <= 0 ? 1f : x >= HeaderView.getPlayerSize() ? 0f : 1f - (x / (float) HeaderView.getPlayerSize());

        if (px > fillingBottom) {
          fillFactor = 1f;
        } else {
          fillFactor = px / fillingBottom;
        }
        updated = true;
      }
    } else {
      if (px > fillingBottom) {
        if (fillFactor != 1f) {
          fillFactor = 1f;
          updated = true;
        }
      } else {
        fillFactor = px / fillingBottom;
        updated = true;
      }
    }
    if (updated) {
      invalidate();
    }
  }

  public void layout (int width, int height, float factor) { // Called during the layout
    this.width = width;
    trimText(lastTextPadding);
    layout(height, factor);
  }

  public void layout (int height, float factor) { // Called during the navigation animation
    height += getTopOffset();

    this.height = height;

    if (showFactor != 0f) {
      layoutOngoingBar();
    } else {
      fillingBottom = shadowTop = playerBottom = height;
      playerTop = playerBottom - Size.getHeaderPlayerSize();
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (isOutlineBig) {
        if (factor <= .5f) {
          isOutlineBig = false;
          headerView.invalidateOutline();
        } else if (factor == 1f) {
          headerView.invalidateOutline();
        }
      } else {
        if (factor > .5f) {
          isOutlineBig = true;
          headerView.invalidateOutline();
        } else if (factor == 0f) {
          headerView.invalidateOutline();
        }
      }
    }
  }

  public void layout () {
    layoutOngoingBar();
    if (navigationController != null) {
      navigationController.applyPlayerOffset(showFactor, (float) HeaderView.getPlayerSize() * showFactor);
    }
  }

  private float playerAllowance = 1f;

  public void setPlayerAllowance (float allowance) {
    if (this.playerAllowance != allowance) {
      this.playerAllowance = allowance;
      layout();
      invalidate();
      UI.getContext(headerView.getContext()).getRoundVideoController().checkLayout();
    }
  }

  private void layoutOngoingBar () {
    playerBottom = height + (int) ((float) HeaderView.getPlayerSize() * (showFactor - MathUtils.clamp((float) (height - HeaderView.getSize(true)) / (float) Size.getHeaderSizeDifference(false))) * playerAllowance);
    playerTop = playerBottom - HeaderView.getPlayerSize();

    fillingBottom = height;
    shadowTop = Math.max(fillingBottom, playerBottom);
  }

  private float dropShadowAlpha = 1f;

  public void setShadowAlpha (float alpha) {
    if (this.dropShadowAlpha != alpha) {
      this.dropShadowAlpha = alpha;
      invalidate();
    }
  }

  public void setColor (int color) {
    this.color = color;
  }

  public int getColor () {
    return color;
  }

  private float radiusFactor;
  private int radiusColor;

  public void setRadiusFactor (float factor, int radiusColor) {
    this.radiusFactor = factor;
    this.radiusColor = radiusColor;
  }

  public void resetRadius () {
    this.radiusFactor = 0f;
    this.radiusColor = 0;
  }

  // Restoring the lost rect

  private boolean restoreRect;
  private float restorePixels;
  private int restoreColor;

  public void setRestorePixels (boolean restore, float pixels, int color) {
    this.restoreRect = restore;
    if (restore) {
      this.restorePixels = pixels;
      this.restoreColor = color;
      invalidate();
    }
  }

  public void restorePixels (float pixels) {
    if (this.restoreRect && this.restorePixels != pixels) {
      this.restorePixels = pixels;
      invalidate();
    }
  }

  // Drawing

  private boolean hasVisibleOngoingBar () {
    return showFactor != 0f && hideFactor != 1f && dropShadowAlpha != 0f;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    if (restoreRect && restorePixels > 0) {
      if (Lang.rtl()) {
        c.drawRect(0, 0, restorePixels, fillingBottom, Paints.fillingPaint(restoreColor));
      } else {
        c.drawRect(width - restorePixels, 0, width, fillingBottom, Paints.fillingPaint(restoreColor));
      }
    }
    if (hasVisibleOngoingBar()) {
      drawOngoingBar(c);
    }
    if (fillFactor == 1f && hideFactor == 0f) {
      c.drawRect(0f, 0f, width, fillingBottom, Paints.fillingPaint(color));
      if (radiusFactor != 0f && radiusColor != 0) {
        c.save();
        c.clipRect(0, 0, width, fillingBottom);
        float radius = (float) Math.sqrt(width * width + fillingBottom * fillingBottom) * .5f;
        float startX = Lang.rtl() ? Screen.dp(49f) / 2 - Screen.dp(3f) - Screen.dp(5f) : width - Screen.dp(49f) / 2 + Screen.dp(3f) + Screen.dp(5f);
        float endX = width / 2;
        float x = startX + (endX - startX) * radiusFactor;
        float y = HeaderView.getTopOffset() + HeaderView.getSize(false) / 2 + Screen.dp(2f);
        c.drawCircle(x, y, radius * radiusFactor, Paints.fillingPaint(ColorUtils.alphaColor(.35f + .65f * radiusFactor, radiusColor)));
        // c.drawCircle(startX, y, Screen.dp(2f), Paints.fillingPaint(0xaaff0000));
        c.restore();
      }
      ShadowView.drawDropShadow(c, 0, width, (int) shadowTop, dropShadowAlpha);
    } else {
      if (fillFactor == 1f) {
        c.drawRect(0f, 0f, width, fillingBottom, Paints.fillingPaint(color));
      } else {
        c.drawRect(0f, 0f, width, fillingBottom * fillFactor, Paints.fillingPaint(color));
      }
    }
    if (navigationController != null) {
      ViewController<?> current = navigationController.getCurrentStackItem();
      if (current != null) {
        current.drawTransform(c, width, (int) fillingBottom);
      }
    }
    /*if (needStatusBar) {
      final int offset = getTopOffset();
      if (navigationController == null && offset > 0) {
        c.drawRect(0, 0, width, offset, Paints.fillingPaint(Theme.getColor(R.id.theme_color_statusBar)));
      }
    }*/
  }

  private void invalidate () {
    // invalidateSelf();
    headerView.invalidate();
  }

  private void invalidateOngoingBar () {
    // invalidateSelf();
    headerView.invalidate(0, playerTop, width, playerBottom);
  }

  private boolean isCurrentSection (int section) {
    return (ongoingSections & section) != 0 || (!showOngoingBar && lastBarType == section);
  }

  private boolean canBuildSection (int section) {
    int flags = this.ongoingSections & ~section;
    return section > flags;
  }

  private void drawOngoingBar (Canvas c) {
    int hide = (int) ((float) HeaderView.getPlayerSize() * hideFactor);
    int playerTop = this.playerTop - hide;
    int playerBottom = this.playerBottom - hide;
    float rectWidth;

    if (restoreRect && restorePixels > 0) {
      rectWidth = width - restorePixels;
    } else {
      rectWidth = width;
    }

    if (isCurrentSection(SECTION_CALL)) {
      drawOngoingCall(c, playerTop, rectWidth, playerBottom);
    } else if (isCurrentSection(SECTION_AUDIO)) {
      drawOngoingAudio(c, playerTop, rectWidth, playerBottom);
    }
  }

  // Audio (new)

  private Tdlib currentTrackTdlib;
  private TdApi.Message currentTrack;

  @Override
  public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    boolean changedTrack = false;
    if (currentTrackTdlib != tdlib || currentTrack != newTrack) {
      if (currentTrack != null) {
        TdlibManager.instance().player().removeTrackListener(currentTrackTdlib, currentTrack, this);
      }
      currentTrack = newTrack;
      currentTrackTdlib = tdlib;
      changedTrack = true;
    }
    boolean isPlaying = state == TGPlayerController.STATE_PLAYING;
    TdApi.Message newMessage = newTrack != null && state != TGPlayerController.STATE_NONE ? newTrack : null;
    boolean updatePlay;
    if (this.playingMessageTdlib != tdlib || this.playingMessage != newMessage) {
      this.playingMessageTdlib = tdlib;
      this.playingMessage = newMessage;
      boolean show = newMessage != null;
      setShowOngoingSection(SECTION_AUDIO, show, true, false);
      updatePlay = show;
    } else {
      updatePlay = newTrack != null && TGPlayerController.compareTracks(newTrack, playingMessage);
    }
    if (updatePlay) {
      setIsPlaying(isPlaying, hasVisibleOngoingBar());
      if (changedTrack) {
        TdlibManager.instance().player().addTrackListener(tdlib, newTrack, this);
      }
    }
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) { }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (currentTrack != null && currentTrack.content.getConstructor() != TdApi.MessageVideoNote.CONSTRUCTOR) {
      setSeekFactor(progress);
    }
  }

  // Audio

  public boolean playPause () {
    if (playingMessage != null) {
      TdlibManager.instance().player().playPauseMessage(playingMessageTdlib, playingMessage, null);
      return true;
    }
    return false;
  }

  public void openPlayer () {
    if (navigationController == null || playingMessageTdlib == null || playingMessage == null) {
      return;
    }
    if (playingMessage.content.getConstructor() != TdApi.MessageAudio.CONSTRUCTOR) {
      if (playingMessage.chatId == 0 || playingMessage.id == 0) {
        return;
      }

      final BaseActivity context = UI.getContext(navigationController.getContext());

      if (TD.isScheduled(playingMessage)) {
        playingMessageTdlib.ui().openScheduledMessage(new TdlibContext(context, playingMessageTdlib), playingMessage);
      } else {
        playingMessageTdlib.ui().openMessage(new TdlibContext(context, playingMessageTdlib), playingMessage, null);
      }

      return;
    }
    PlaybackController c = new PlaybackController(navigationController.getContext(), playingMessageTdlib);
    if (c.prepare() != -1) {
      navigationController.navigateTo(c);
    }
  }

  @Override
  public void onPlayPause (int fileId, boolean isPlaying, boolean isUpdate) {
    setIsPlaying(isPlaying, true);
  }

  @Override
  public boolean needPlayProgress (int fileId) {
    return true;
  }

  @Override
  public void onPlayProgress (int fileId, float progress, boolean isUpdate) {
    setSeekFactor(progress);
  }

  private float seekFactor;

  public void setSeekFactor (float seekFactor) { // 0f -> 1f length of the player seek
    if (this.seekFactor != seekFactor) {
      final int oldWidth = (int) ((float) width * this.seekFactor);
      this.seekFactor = seekFactor;
      final int newWidth = (int) ((float) width * seekFactor);
      if (oldWidth != newWidth) {
        invalidateOngoingBar();
      }
    }
  }

  private boolean isAudioPlaying;

  private static String buildPrivateTitle (Tdlib tdlib, TdApi.Message message) {
    String title;
    if (tdlib.isSelfSender(message)) {
      title = Lang.getString(R.string.FromYou);
    } else {
      title = tdlib.messageAuthor(message);
      if (StringUtils.isEmpty(title)) {
        title = Lang.getString(message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR ? R.string.AttachRound : R.string.AttachAudio);
      }
    }
    return title;
  }

  private void buildAudio () {
    if (playPausePath == null) {
      playPausePath = new Path();
    }

    this.seekFactor = 0f;
    String title, subtitle;
    String fileName = null;
    boolean isVoice = false;

    if (playingMessage.content.getConstructor() != TdApi.MessageAudio.CONSTRUCTOR) {
      title = buildPrivateTitle(playingMessageTdlib, playingMessage);
      subtitle = TD.isScheduled(playingMessage) ? Lang.getString(R.string.ScheduledMessage) : playingMessage.date != 0 ? Lang.getRelativeTimestamp(playingMessage.date, TimeUnit.SECONDS, true, 5) : null; // TODO update
      isVoice = playingMessage.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
    } else {
      TdApi.Audio audio = ((TdApi.MessageAudio) playingMessage.content).audio;
      title = TD.getTitle(audio);
      subtitle = TD.getSubtitle(audio);
      fileName = audio.fileName;
    }

    if (title != null) {
      title = title.trim();
    }
    if (subtitle != null) {
      subtitle = subtitle.trim();
    }
    if (title != null && subtitle != null && title.length() > 0 && subtitle.length() > 0) {
      subtitle = isVoice && !TD.isScheduled(playingMessage) ? " " + subtitle : " — " + subtitle;
    }

    if (StringUtils.isEmpty(subtitle) && StringUtils.isEmpty(title) && fileName != null) {
      subtitle = fileName.trim();
    }

    setText(StringUtils.isEmpty(title) ? null : title, StringUtils.isEmpty(subtitle) ? null : subtitle, Screen.dp(56f) * 2 + Screen.dp(24f) + Screen.dp(6f));
  }

  private void drawOngoingAudio (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    if (restoreRect && restorePixels > 0) {
      c.drawRect(rectWidth, playerTop, width, playerBottom, Paints.fillingPaint(Theme.fillingColor()));
    }

    final int playerFillingColor = ColorUtils.alphaColor(dropShadowAlpha, Theme.fillingColor());

    c.drawRect(0, playerTop, rectWidth, playerBottom, Paints.fillingPaint(playerFillingColor));
    if (seekFactor != 0f) {
      c.drawRect(0, playerBottom - (Screen.dp(1f) + 1), (int) (width * seekFactor), playerBottom, Paints.fillingPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.getColor(R.id.theme_color_headerBarCallActive))));
    }

    TdApi.File file = TD.getFile(playingMessage);
    DrawAlgorithms.drawPlayPause(c, width - Screen.dp(28f), playerBottom - Size.getHeaderPlayerSize() / 2, Screen.dp(12f), playPausePath, playPauseDrawFactor, playPauseDrawFactor = playPauseFactor, file != null ? TD.getFileProgress(file) : 1f, ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor()));

    Drawables.draw(c, forwardIcon, width - Screen.dp(52f) - forwardIcon.getMinimumWidth(), playerBottom - Size.getHeaderPlayerSize() / 2 - forwardIcon.getMinimumHeight() / 2, dropShadowAlpha == 1f ? Paints.getIconGrayPorterDuffPaint() : Paints.getPorterDuffPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor())));
    drawCloseIcon(c, playerTop, width, playerBottom, false);

    drawOngoingText(c, playerTop, rectWidth, playerBottom, Screen.dp(67f), null, 1f, 1f);
  }

  private void drawCloseIcon (Canvas c, int playerTop, float rectWidth, int playerBottom, boolean alignRight) {
    float cx = alignRight ? rectWidth - Screen.dp(50f) / 2 : Screen.dp(28f);
    DrawAlgorithms.drawMark(c, cx, playerTop + (playerBottom - playerTop) / 2, 1f, Screen.dp(9f), Paints.getProgressPaint(ColorUtils.alphaColor(dropShadowAlpha, Theme.iconColor()),  Screen.dp(2f)));
  }

  // Ongoing bar text

  private String title, subtitle;
  private Text trimmedTitle, trimmedSubtitle;

  private void setText (String title, String text, int textPadding) {
    boolean changed = lastTextPadding != 0 && lastTextPadding != textPadding;
    if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
      this.title = title;
      changed = true;
    }
    if (!StringUtils.equalsOrBothEmpty(this.subtitle, text)) {
      this.subtitle = text;
      changed = true;
    }
    if (changed) {
      trimText(textPadding);
    }
  }

  private int lastTextPadding;

  private void trimText (int textPadding) {
    this.lastTextPadding = textPadding;
    trimTitle(textPadding);
    trimSubtitle(textPadding);
  }

  private void trimTitle (int padding) {
    if (width != 0) {
      final int availWidth = width - padding;
      this.trimmedTitle = !StringUtils.isEmpty(title) && availWidth > 0 ? new Text.Builder(title, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.NORMAL).singleLine().allBold().build() : null;
    }
  }

  private int getTitleWidth () {
    return (trimmedTitle != null ? trimmedTitle.getWidth() : 0);
  }

  private void trimSubtitle (int textPadding) {
    if (width != 0) {
      final int availWidth = width - textPadding - getTitleWidth();
      this.trimmedSubtitle = !StringUtils.isEmpty(subtitle) && availWidth > 0 ? new Text.Builder(subtitle, availWidth, Paints.getTitleStyleProvider(), TextColorSets.Regular.LIGHT).singleLine().build() : null;
    }
  }

  private void drawOngoingText (Canvas c, int playerTop, float rectWidth, int playerBottom, int textLeft, TextColorSet theme, float titleAlpha, float textAlpha) {
    int textTop = Screen.dp(9f);
    if (trimmedTitle != null) {
      trimmedTitle.draw(c, textLeft, playerTop + textTop, theme, titleAlpha * dropShadowAlpha);
    }
    if (trimmedSubtitle != null) {
      trimmedSubtitle.draw(c, textLeft + getTitleWidth(), playerTop + textTop, theme, textAlpha * dropShadowAlpha);
    }
  }

  // Audio play state

  private static final int ANIMATOR_PLAY_ID = 1;
  private FactorAnimator playAnimator;
  private Path playPausePath;
  private float playPauseFactor, playPauseDrawFactor = -1;

  public void setIsPlaying (boolean isPlaying, final boolean animated) {
    if (this.isAudioPlaying != isPlaying) {
      this.isAudioPlaying = isPlaying;
      final float toFactor = isPlaying ? 1f : 0f;
      if (animated) {
        if (playAnimator == null) {
          playAnimator = new FactorAnimator(ANIMATOR_PLAY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 160l, this.playPauseFactor);
        }
        playAnimator.animateTo(toFactor);
      } else {
        if (playAnimator != null) {
          playAnimator.forceFactor(toFactor);
        }
        setPlayPauseFactor(toFactor);
      }
    }
  }

  private void setPlayPauseFactor (float playPauseFactor) {
    if (this.playPauseFactor != playPauseFactor) {
      this.playPauseFactor = playPauseFactor;
      invalidateOngoingBar();
    }
  }

  public void setSeekAnimated (float seekTo, int duration) {
    setSeekFactor(seekTo);
  }

  // Calls

  private Tdlib callTdlib;
  private TdApi.Call call;

  private int getCallId () {
    return call != null ? call.id : 0;
  }

  private void initCallListeners () {
    TdlibManager.instance().calls().addCurrentCallListener(this);
    setCall(TdlibManager.instance().calls().getCurrentCallTdlib(), TdlibManager.instance().calls().getCurrentCall(), true);
  }

  @Override
  public void onCurrentCallChanged (final Tdlib tdlib, @Nullable final TdApi.Call call) {
    UI.post(() -> setCall(tdlib, call, false));
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (getCallId() == call.id) {
      updateCall(call);
    }
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (getCallId() == callId && newState == CallState.ESTABLISHED) {
      buildCall();
      invalidateOngoingBar();
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) { }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (getCallId() == callId) {
      setCallMuted(settings.isMicMuted(), isOngoingBarVisible());
    }
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    this.previousCallState = this.call != null ? this.call.state : null;
    this.call = call;
    buildCall();
    invalidateOngoingBar();
  }

  private void buildCall () {
    if (call == null) {
      return;
    }
    TdApi.User user = callTdlib.cache().user(call.userId);
    String callState;
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      callState = TD.getCallState2(call, previousCallState, TdlibManager.instance().calls().getCallDuration(callTdlib, call.id), true);
    } else {
      callState = TD.getCallState(call, TdlibManager.instance().calls().getCallDuration(callTdlib, call.id), true);
    }
    setText(TD.getUserName(user), "  " + callState, textLeft * 2);
    if (!TD.isFinished(call)) {
      CallSettings settings = callTdlib.cache().getCallSettings(call.id);
      setCallMuted(settings != null && settings.isMicMuted(), isOngoingBarVisible());
    }
    setCallActive(!TD.isFinished(call), true, call != null ? call.state : null);
    setCallIncoming(!TD.isFinished(call) && !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR, isOngoingBarVisible());
    setCallFlashing(TD.getCallNeedsFlashing(call));
    updateCallLooping();
  }

  private void setCall (@Nullable Tdlib tdlib, @Nullable TdApi.Call call, final boolean force) {
    if (this.call != null) {
      this.callTdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    }
    final Tdlib oldTdlib = this.callTdlib;
    final TdApi.Call oldCall = this.call;
    if (call != null) {
      this.call = call;
      this.callTdlib = tdlib;
      tdlib.cache().subscribeToCallUpdates(call.id, this);
    }
    if (call != null || oldCall == null || force) {
      this.call = call;
      setShowOngoingSection(SECTION_CALL, call != null, false, force);
    } else {
      UI.post(() -> {
        if (HeaderFilling.this.callTdlib != null && HeaderFilling.this.callTdlib.id() == oldTdlib.id() &&
          HeaderFilling.this.call != null && oldCall.id == HeaderFilling.this.call.id) {
          HeaderFilling.this.call = null;
          HeaderFilling.this.callTdlib = null;
          setShowOngoingSection(SECTION_CALL, false, false, false);
        }
      }, isOngoingBarVisible() ? 2000 : 0);
    }
  }

  private void updateCallLooping () {
    setCallIsLooping(call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isCallLooping;

  private void setCallIsLooping (boolean isLooping) {
    if (this.isCallLooping != isLooping) {
      this.isCallLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (call != null) {
      buildCall();
      invalidateOngoingBar();
      if (isCallLooping) {
        UI.post(this, getTimeTillNextUpdate());
      }
    }
  }

  private long getTimeTillNextUpdate () {
    return TdlibManager.instance().calls().getTimeTillNextCallDurationUpdate(callTdlib, call.id);
  }

  private boolean isCallActive;
  private static final int ANIMATOR_CALL_ACTIVE_ID = 4;
  private FactorAnimator callActiveAnimator;
  private float callActiveFactor;

  private void setCallActive (boolean isActive, boolean animated, TdApi.CallState state) {
    if (this.isCallActive != isActive) {
      this.isCallActive = isActive;
      final float toFactor = isActive ? 1f : 0f;
      if (animated) {
        if (callActiveAnimator == null) {
          callActiveAnimator = new FactorAnimator(ANIMATOR_CALL_ACTIVE_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callActiveFactor);
        }
        callActiveAnimator.animateTo(toFactor);
      } else {
        if (callActiveAnimator != null) {
          callActiveAnimator.forceFactor(toFactor);
        }
        setCallActiveFactor(callActiveFactor);
      }
    }
  }

  private void setCallActiveFactor (float factor) {
    if (this.callActiveFactor != factor) {
      this.callActiveFactor = factor;
      invalidateOngoingBar();
    }
  }

  private boolean isCallPendingIncoming;
  private static final int ANIMATOR_CALL_INCOMING_ID = 5;
  private FactorAnimator callIncomingAnimator;
  private float callIncomingFactor;

  private void setCallIncoming (boolean isIncoming, boolean animated) {
    if (this.isCallPendingIncoming != isIncoming) {
      this.isCallPendingIncoming = isIncoming;
      final float toFactor = isIncoming ? 1f : 0f;
      if (animated) {
        if (callIncomingAnimator == null) {
          callIncomingAnimator = new FactorAnimator(ANIMATOR_CALL_INCOMING_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callIncomingFactor);
        }
        callIncomingAnimator.animateTo(toFactor);
      } else {
        if (callIncomingAnimator != null) {
          callIncomingAnimator.forceFactor(toFactor);
        }
        setCallIncomingFactor(toFactor);
      }
    }
  }

  private void setCallIncomingFactor (float factor) {
    if (this.callIncomingFactor != factor) {
      this.callIncomingFactor = factor;
      invalidateOngoingBar();
    }
  }

  private boolean isCallFlashing;
  private static final int ANIMATOR_CALL_FLASH_ID = 6;
  private FactorAnimator callFlashAnimator;
  private float callFlashFactor;

  private void setCallFlashing (boolean isCallFlashing) {
    if (this.isCallFlashing != isCallFlashing) {
      this.isCallFlashing = isCallFlashing;
      if (isCallFlashing) {
        if (callFlashAnimator == null) {
          callFlashAnimator = new FactorAnimator(ANIMATOR_CALL_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CallController.CALL_FLASH_DURATION);
          callFlashAnimator.setStartDelay(CallController.CALL_FLASH_DELAY);
        }
        if (!callFlashAnimator.isAnimating()) {
          callFlashAnimator.forceFactor(0f);
          callFlashAnimator.animateTo(1f);
        }
      } else {
        if (callFlashAnimator != null && callFlashAnimator.getFactor() == 0f) {
          callFlashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private void setCallFlashFactor (float flashFactor) {
    if (this.callFlashFactor != flashFactor) {
      this.callFlashFactor = flashFactor;
      invalidateOngoingBar();
    }
  }

  private static final int ANIMATOR_CALL_MUTE_ID = 2;

  private boolean isCallMuted;
  private FactorAnimator callMuteAnimator;
  private float callMuteFactor;

  private void setCallMuted (boolean isCallMuted, boolean animated) {
    if (this.isCallMuted != isCallMuted) {
      this.isCallMuted = isCallMuted;
      final float toFactor = isCallMuted ? 1f : 0f;
      if (animated) {
        if (callMuteAnimator == null) {
          callMuteAnimator = new FactorAnimator(ANIMATOR_CALL_MUTE_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.callMuteFactor);
        }
        callMuteAnimator.animateTo(toFactor);
      } else {
        if (callMuteAnimator != null) {
          callMuteAnimator.forceFactor(toFactor);
        }
        setCallMuteFactor(toFactor);
      }
    }
  }

  private void setCallMuteFactor (float factor) {
    if (this.callMuteFactor != factor) {
      this.callMuteFactor = factor;
      invalidateOngoingBar();
    }
  }

  private void toggleCallMute () {
    if (call != null) {
      CallSettings settings = callTdlib.cache().getCallSettings(call.id);
      if (settings == null) {
        settings = new CallSettings(callTdlib, call.id);
      }
      setCallMuted(!settings.isMicMuted(), true);
      settings.setMicMuted(isCallMuted);
    }
  }

  private void openCall () {
    TdlibManager.instance().calls().openCurrentCall();
  }

  private void drawOngoingCall (Canvas c, int playerTop, float rectWidth, int playerBottom) {
    final int backgroundColor = ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_headerBarCallMuted), ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_headerBarCallActive), Theme.getColor(R.id.theme_color_headerBarCallIncoming), callIncomingFactor), (1f - callMuteFactor) * callActiveFactor);
    if (restoreRect && restorePixels > 0) {
      c.drawRect(rectWidth, playerTop, width, playerBottom, Paints.fillingPaint(backgroundColor));
    }
    final int playerFillingColor = ColorUtils.alphaColor(dropShadowAlpha, backgroundColor);

    c.drawRect(0, playerTop, rectWidth, playerBottom, Paints.fillingPaint(playerFillingColor));

    Paint iconPaint = Paints.getPorterDuffPaint(0xffffffff);

    // Mute button
    iconPaint.setAlpha((int) (255f * dropShadowAlpha * callActiveFactor * (1f - callIncomingFactor)));
    int cx = width - Screen.dp(12f) - micIcon.getMinimumWidth();
    int cy = playerTop + Screen.dp(6f);
    Drawables.draw(c, micIcon, cx, cy, iconPaint);
    DrawAlgorithms.drawCross(c, cx + micIcon.getMinimumWidth() / 2, cy + micIcon.getMinimumHeight() / 2, callMuteFactor, ColorUtils.color(iconPaint.getAlpha(), 0xffffff), playerFillingColor);

    // Call button
    iconPaint.setAlpha((int) (255f * dropShadowAlpha));
    cx = Screen.dp(18f);
    cy = playerTop + Screen.dp(6f);

    float callRotationFactor = callIncomingFactor * callActiveFactor;
    if (callRotationFactor != 0f) {
      c.save();
      c.rotate(225f * callRotationFactor, cx + hangIcon.getMinimumWidth() / 2, cy + hangIcon.getMinimumHeight() / 2);
    }
    Drawables.draw(c, hangIcon, cx, cy, iconPaint);
    if (callActiveFactor != 1f) {
      DrawAlgorithms.drawCross(c, cx + hangIcon.getMinimumWidth() / 2, cy + hangIcon.getMinimumHeight() / 2 - Screen.dp(2f), 1f - callActiveFactor, ColorUtils.color((int) (255f * dropShadowAlpha), 0xffffff), backgroundColor);
    }
    if (callRotationFactor != 0f) {
      c.restore();
    }
    iconPaint.setAlpha(0xff);

    float textAlpha = ((float) 0xe0 / (float) 0xff) * (callFlashFactor <= .5f ? 1f - (callFlashFactor / .5f) : (callFlashFactor - .5f) / .5f);
    drawOngoingText(c, playerTop, rectWidth, playerBottom, textLeft, TextColorSets.WHITE, 1f, textAlpha);
  }

  // Show/hide ongoing bar

  private boolean isOngoingBarVisible () {
    return showFactor > 0f && shadowTop > fillingBottom && hideFactor < 1f;
  }

  private static final int ANIMATOR_SHOW_ID = 0;
  private FactorAnimator barShowAnimator;

  private void setShowOngoingSection (int section, boolean isShowing, boolean needRebuild, boolean force) {
    if (BitwiseUtils.hasFlag(ongoingSections, section) != isShowing) {
      this.ongoingSections = BitwiseUtils.setFlag(ongoingSections, section, isShowing);
      if (isShowing) {
        if (canBuildSection(section)) {
          lastBarType = section;
          buildSection(section);
        }
      }/* else if ((ongoingSections & SECTION_LOCATION) != 0) {
        buildLocation();
      }*/ else if ((ongoingSections & SECTION_CALL) != 0) {
        buildCall();
      } else if ((ongoingSections & SECTION_AUDIO) != 0) {
        buildAudio();
      }
      if (isShowing || ongoingSections != 0) {
        invalidateOngoingBar();
      }
      showHideAnimated(ongoingSections != 0, force);
    } else {
      if (needRebuild) {
        buildSection(section);
      }
      invalidateOngoingBar();
    }
  }

  private void buildSection (int section) {
    switch (section) {
      /*case SECTION_LOCATION:
        buildLocation();
        break;*/
      case SECTION_CALL:
        buildCall();
        break;
      case SECTION_AUDIO:
        buildAudio();
        break;
    }
  }

  private void showHideAnimated (boolean show, boolean force) {
    if (this.showOngoingBar != show) {
      this.showOngoingBar = show;

      if (force) {
        setShowFactor(show ? 1f : 0f);
        return;
      }

      ViewController<?> c = navigationController != null ? navigationController.getCurrentStackItem() : null;
      boolean allowAnimation = c != null && !c.usePopupMode();
      final float toFactor = show ? 1f : 0f;
      if (allowAnimation) {
        if (barShowAnimator == null) {
          barShowAnimator = new FactorAnimator(ANIMATOR_SHOW_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, this.showFactor);
        }
        barShowAnimator.animateTo(toFactor);
      } else {
        if (barShowAnimator != null) {
          barShowAnimator.forceFactor(toFactor);
        }
        setShowFactor(toFactor);
      }
    } else if (showOngoingBar) {
      invalidate();
    }
  }

  private void setShowFactor (float playerFactor) {
    if (this.showFactor != playerFactor) {
      this.showFactor = playerFactor;
      layout();
      invalidate();

      UI.getContext(headerView.getContext()).getRoundVideoController().checkLayout();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_SHOW_ID: {
        setShowFactor(factor);
        break;
      }
      case ANIMATOR_PLAY_ID: {
        setPlayPauseFactor(factor);
        break;
      }
      case ANIMATOR_CALL_MUTE_ID: {
        setCallMuteFactor(factor);
        break;
      }
      case ANIMATOR_CALL_ACTIVE_ID: {
        setCallActiveFactor(factor);
        break;
      }
      case ANIMATOR_CALL_INCOMING_ID: {
        setCallIncomingFactor(factor);
        break;
      }
      case ANIMATOR_CALL_FLASH_ID: {
        setCallFlashFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CALL_FLASH_ID: {
        callFlashAnimator.forceFactor(0f);
        if (isCallFlashing) {
          callFlashAnimator.animateTo(1f);
        }
        break;
      }
    }
  }

  // Touch processing

  private void performSoundFeedback () {
    if (headerView != null) {
      headerView.playSoundEffect(SoundEffectConstants.CLICK);
    }
  }

  public boolean onTouchEvent (MotionEvent e) {
    return helper != null && helper.onTouchEvent(headerView, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return (navigationController == null || !navigationController.isAnimating()) && (showOngoingBar || showFactor != 0f) && dropShadowAlpha != 0f && hideFactor != 1f && !(y < playerTop) && !(y > playerBottom) && !(y <= fillingBottom);
  }

  @Override
  public boolean needLongPress (float x, float y) {
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      return false;
    }
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      int endX = width - Screen.dp(52f);
      return x <= endX && x >= endX - Screen.dp(24f);
    }
    return false;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (needLongPress(x, y)) {
      TdlibManager.instance().player().setPlaybackSpeed(TGPlayerController.PLAY_SPEED_2X);
      return true;
    }
    return false;
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    TdlibManager.instance().player().setPlaybackSpeed(TGPlayerController.PLAY_SPEED_NORMAL);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    int buttonSize = Screen.dp(52f);
    if (x <= width && x >= width - buttonSize - Screen.dp(24f)) {
      int index = x >= width - buttonSize ? 0 : (int) (width - x - buttonSize) / Screen.dp(24f) + 1;
      onRightClick(index);
    } else if (x >= 0 && x <= buttonSize) {
      onLeftClick();
    } else {
      onBarClick();
    }
  }

  private boolean onRightClick (int index) {
    /*if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      stopAllLiveLocations();
    } else*/
    switch (index) {
      case 0: { // play/pause or mute/unute
        performSoundFeedback();
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
          if (isCallActive && !isCallPendingIncoming) {
            toggleCallMute();
          }
        } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
          if (!playPause()) {
            openPlayer();
          }
        }
        return true;
      }
      case 1: {
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
          return false;
        }
        if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
          TdlibManager.instance().player().playNextMessageInQueue();
          return true;
        }
        break;
      }
    }
    return false;
  }

  private void onLeftClick () {
   /* if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      // Do nothing
    } else*/
   performSoundFeedback();
   if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      if (isCallActive) {
        if (call != null && headerView != null) {
          if (isCallPendingIncoming) {
            TdlibManager.instance().calls().acceptCall(headerView.getContext(), callTdlib, call.id);
          } else {
            TdlibManager.instance().calls().hangUp(callTdlib, call.id);
          }
        }
      }
    } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      TdlibManager.instance().player().stopPlayback(true);
    }
  }

  private void onBarClick () {
    /*if (U.getFlag(ongoingSections, SECTION_LOCATION)) {
      openLiveLocationList();
    } else*/
    performSoundFeedback();
    if (BitwiseUtils.hasFlag(ongoingSections, SECTION_CALL)) {
      openCall();
    } else if (BitwiseUtils.hasFlag(ongoingSections, SECTION_AUDIO)) {
      openPlayer();
    }
  }

  // Drawable utils

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (ColorFilter colorFilter) { }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }
}
