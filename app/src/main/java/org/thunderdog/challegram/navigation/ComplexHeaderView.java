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
 * File created on 09/08/2015 at 22:00
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.SimpleMediaViewController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class ComplexHeaderView extends BaseView implements RtlCheckListener, StretchyHeaderView, TextChangeDelegate, Destroyable, ColorSwitchPreparator, MediaCollectorDelegate, BaseView.CustomControllerProvider, TdlibStatusManager.HelperTarget, TGLegacyManager.EmojiLoadListener, HeaderView.OffsetChangeListener {
  private static final int FLAG_SHOW_LOCK = 1;
  private static final int FLAG_SHOW_MUTE = 1 << 1;
  private static final int FLAG_SHOW_VERIFY = 1 << 2;
  private static final int FLAG_PHOTO_OPEN_DISABLED = 1 << 4;
  private static final int FLAG_HAD_FULL_EXPAND = 1 << 7;
  private static final int FLAG_IGNORE_CUSTOM_HEIGHT = 1 << 10;
  private static final int FLAG_ALLOW_EMPTY_CLICK = 1 << 11;
  private static final int FLAG_CAUGHT = 1 << 12;
  private static final int FLAG_NEED_ARROW = 1 << 14;
  private static final int FLAG_NO_STATUS = 1 << 15;
  private static final int FLAG_IGNORE_MUTE = 1 << 16;
  private static final int FLAG_RED_HIGHLIGHT = 1 << 17;
  private static final int FLAG_NO_EXPAND = 1 << 18;
  private static final int FLAG_SHOW_SCAM = 1 << 19;
  private static final int FLAG_SHOW_FAKE = 1 << 20;

  protected float scaleFactor;

  private @NonNull final AvatarReceiver receiver;

  private String title, subtitle, expandedSubtitle;
  private TextEntity[] subtitleEntities;
  private @Nullable Text trimmedTitle, trimmedTitleExpanded, trimmedSubtitle, trimmedSubtitleExpanded;

  private float avatarAllowanceFactor, avatarCollapseFactor;
  private BoolAnimator avatarCollapseAnimator;

  private int flags;

  // private ViewController parent;

  private Drawable arrowDrawable;
  private Drawable topShadow, bottomShadow;

  public ComplexHeaderView (Context context, @NonNull Tdlib tdlib, @Nullable ViewController<?> parent) {
    super(context, tdlib);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    // this.parent = parent;
    this.status = new TdlibStatusManager.Helper(UI.getContext(context), tdlib, this, parent);
    setUseDefaultClickListener(false);
    this.receiver = new AvatarReceiver(this);
    this.receiver.setDisplayFullSizeOnlyInFullScreen(true);
    setCustomControllerProvider(this);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  protected final boolean hasSubtitle () {
    return this.subtitle != null;
  }

  public void setNoExpand (boolean noExpand) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_NO_EXPAND, noExpand))) {
      if (noExpand) {
        setAvatarAllowanceFactor(0f);
      }
    }
  }

  public void setUseRedHighlight (boolean useRedHighlight) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_RED_HIGHLIGHT, useRedHighlight))) {
      layoutTitle();
      invalidate();
    }
  }

  private void updateTopShadow () {
    if (topShadow != null) {
      topShadow.setBounds(0, 0, getMeasuredWidth(), currentHeaderOffset + (int) ((float) HeaderView.getSize(false)) + Size.getHeaderPlayerSize());
    }
  }

  private int getBottomShadowSize () {
    return (int) ((float) ((Screen.dp(28f) + Screen.dp(5f) + getTitleHeight() + Screen.dp(8f)) + Screen.dp(14f)) * (1f / .9f));
    // return (int) ((float) Screen.dp(78f) + getMultiLineAddition());
  }

  private void updateBottomShadow () {
    if (bottomShadow != null) {
      bottomShadow.setBounds(0, 0, getMeasuredWidth(), getBottomShadowSize());
    }
  }

  private Drawable getTopShadow () {
    if (topShadow == null) {
      topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x77000000, 2, Gravity.TOP, false);
      updateTopShadow();
    }
    return topShadow;
  }

  private Drawable getBottomShadow () {
    if (bottomShadow == null) {
      bottomShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x66000000, 2, Gravity.BOTTOM, false);
      updateBottomShadow();
    }
    return bottomShadow;
  }

  private void setCollapseAvatar (boolean collapse, boolean animated) {
    boolean curCollapse = avatarCollapseAnimator != null && avatarCollapseAnimator.getValue();
    if (curCollapse != collapse) {
      if (avatarCollapseAnimator == null) {
        avatarCollapseAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
          if (avatarCollapseFactor != factor) {
            int oldTextMaxWidth = getCheckTextMaxWidth();
            avatarCollapseFactor = factor;
            if (avatarExpandListener != null) {
              avatarExpandListener.onAvatarExpandFactorChanged(this, getAvatarExpandFactor(), true, avatarAllowanceFactor, avatarCollapseFactor);
            }
            layoutTextsIfNeeded(oldTextMaxWidth);
            invalidate();
          }
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l, false);
      }
      avatarCollapseAnimator.setValue(collapse, animated && avatarAllowanceFactor != 0f);
    }
  }

  private int currentHeaderOffset;

  @Override
  public void onHeaderOffsetChanged (HeaderView headerView, int newOffset) {
    if (this.currentHeaderOffset != newOffset) {
      this.currentHeaderOffset = newOffset;
      updateTopShadow();
      layoutReceiver();
      invalidate();
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  @Override
  public void checkRtl () {
    invalidate();
  }

  public void initWithController (ViewController<?> themeProvider, boolean addListener) {
    int colorId = themeProvider.getHeaderTextColorId();
    setTextColor(Theme.getColor(colorId));
    if (addListener)
      themeProvider.addThemeTextColorListener(this, colorId);
  }

  public void setNoStatus (boolean noStatus) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NO_STATUS, noStatus);
  }

  public void setNeedArrow (boolean needArrow) {
    boolean prevNeedArrow = (flags & FLAG_NEED_ARROW) != 0;
    if (prevNeedArrow != needArrow) {
      if (needArrow && arrowDrawable == null) {
        arrowDrawable = Drawables.get(getResources(), R.drawable.round_keyboard_arrow_right_24);
      }
      this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_ARROW, needArrow);
      layoutTexts();
      invalidate();
    }
  }

  public void setPhotoOpenDisabled (boolean isDisabled) {
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_PHOTO_OPEN_DISABLED, isDisabled);
  }

  @Override
  public void performDestroy () {
    pause();
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public void pause () {
    if (status != null) {
      status.detachFromAnyChat();
    }
    if (receiver != null) {
      receiver.clear();
    }
  }

  // Public utils

  private boolean hasTrimmedText () {
    return (trimmedTitle != null && trimmedTitle.isEllipsized()) || (trimmedSubtitle != null && trimmedSubtitle.isEllipsized());
  }

  private void setHadFullExpand () {
    int oldTextMaxWidth = getCheckTextMaxWidth();
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_HAD_FULL_EXPAND, true)) && layoutTextsIfNeeded(oldTextMaxWidth)) {
      invalidate();
    }
  }

  private boolean wouldCollapse (float scaleFactor) {
    int headerHeight = calculateHeaderHeight(scaleFactor);
    return
      (headerHeight <= currentHeaderOffset + Size.getHeaderPortraitSize() + Size.getHeaderDrawerSize()) ||
      (headerHeight - Screen.dp(58f) - (int) getMultiLineAddition()) < currentHeaderOffset + HeaderView.getSize(false) * .7f;
  }

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    if (scaleFactor == 1f) {
      setHadFullExpand();
    }
    if (this.scaleFactor != scaleFactor) {
      int oldTextMaxWidth = getCheckTextMaxWidth();
      this.scaleFactor = scaleFactor;
      if (!BitwiseUtils.getFlag(flags, FLAG_NO_EXPAND)) {
        if (!byScroll) {
          if (wouldCollapse(fromFactor) == wouldCollapse(toScaleFactor)) {
            setAvatarAllowanceFactor(1f);
            setCollapseAvatar(wouldCollapse(scaleFactor), false);
          } else if (scaleFactor == toScaleFactor || fromFactor == toScaleFactor) {
            setAvatarAllowanceFactor(1f);
            setCollapseAvatar(wouldCollapse(scaleFactor), false);
          } else if (fromFactor < toScaleFactor) {
            float allowance = (scaleFactor - fromFactor) / (toScaleFactor - fromFactor);
            setAvatarAllowanceFactor(allowance);
            setCollapseAvatar(wouldCollapse(toScaleFactor), false);
          } else {
            float allowance = (scaleFactor - toScaleFactor) / (fromFactor - toScaleFactor);
            setAvatarAllowanceFactor(allowance);
            setCollapseAvatar(wouldCollapse(fromFactor), false);
          }
        } else {
          setCollapseAvatar(wouldCollapse(scaleFactor), true);
        }
      }
      layoutTextsIfNeeded(oldTextMaxWidth);
      invalidate();
    }
  }

  public interface AvatarFactorChangeListener {
    void onAvatarExpandFactorChanged (ComplexHeaderView headerView, float expandFactor, boolean byCollapse, float allowanceFactor, float collapseFactor);
  }

  private AvatarFactorChangeListener avatarExpandListener;

  public void setAvatarExpandListener (AvatarFactorChangeListener avatarExpandListener) {
    this.avatarExpandListener = avatarExpandListener;
  }

  public boolean isCollapsed () {
    return avatarCollapseAnimator != null && avatarCollapseAnimator.getValue();
  }

  private void setAvatarAllowanceFactor (float factor) {
    if (this.avatarAllowanceFactor != factor) {
      this.avatarAllowanceFactor = factor;
      if (avatarExpandListener != null) {
        avatarExpandListener.onAvatarExpandFactorChanged(this, getAvatarExpandFactor(), false, avatarAllowanceFactor, avatarCollapseFactor);
      }
      invalidate();
    }
  }

  public float getAvatarExpandFactor () {
    return avatarAllowanceFactor * (1f - avatarCollapseFactor);
  }

  private boolean setFlags (int flags) {
    if (this.flags != flags) {
      this.flags = flags;
      return true;
    }
    return false;
  }

  public void setShowMute (boolean showMute) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_SHOW_MUTE, showMute))) {
      layoutTitle();
      invalidate();
    }
  }

  public void setShowVerify (boolean showVerify) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_SHOW_VERIFY, showVerify))) {
      layoutTitle();
      invalidate();
    }
  }

  public void setShowScam (boolean showScam) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_SHOW_SCAM, showScam))) {
      layoutTitle();
      invalidate();
    }
  }

  public void setShowFake (boolean showFake) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_SHOW_FAKE, showFake))) {
      layoutTitle();
      invalidate();
    }
  }

  public void setShowLock (boolean showLock) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_SHOW_LOCK, showLock))) {
      layoutTitle();
      invalidate();
    }
  }

  public boolean getShowMute () {
    return (flags & FLAG_SHOW_MUTE) != 0;
  }

  public void setText (@StringRes int titleResId, @StringRes int subtitleResId) {
    setText(Lang.getString(titleResId), Lang.getString(subtitleResId));
  }

  public void setText (String text, CharSequence subtext) {
    this.title = text;
    this.subtitle = StringUtils.isEmpty(subtext) ? null : subtext.toString();
    TdApi.TextEntity[] entities = this.subtitle != null ? TD.toEntities(subtext, false) : null;
    this.subtitleEntities = entities != null ? TextEntity.valueOf(tdlib, this.subtitle, entities, null) : null;
    buildLayout();
    invalidate();
  }

  public void setExpandedSubtitle (CharSequence expandedSubtitleCs) {
    String expandedSubtitle = expandedSubtitleCs != null ? expandedSubtitleCs.toString() : null;
    if ((this.expandedSubtitle == null) != (expandedSubtitle == null) || (expandedSubtitle != null && !expandedSubtitle.equals(this.expandedSubtitle))) {
      this.expandedSubtitle = expandedSubtitle;
      layoutSubtext();
      invalidate();
    }
  }

  public void setTitle (String title) {
    setText(title, subtitle);
  }

  public void setSubtitle (CharSequence subtitle) {
    setText(title, subtitle);
  }

  private int innerLeftMargin, innerRightMargin, innerRightMarginStart = -1;

  private int getInnerRightMargin () {
    return !BitwiseUtils.getFlag(flags, FLAG_HAD_FULL_EXPAND) && innerRightMarginStart != -1 ? innerRightMarginStart : innerRightMargin;
  }

  public void setInnerRightMarginStart (int rightMarginStart) {
    if (this.innerRightMarginStart != rightMarginStart) {
      int oldTextMaxWidth = getCheckTextMaxWidth();
      this.innerRightMarginStart = rightMarginStart;
      if (layoutTextsIfNeeded(oldTextMaxWidth)) {
        invalidate();
      }
    }
  }

  public void setInnerRightMargin (int rightMargin) {
    setInnerMargins(this.innerLeftMargin, rightMargin);
  }

  public void setInnerMargins (int leftMargin, int rightMargin) {
    if (this.innerLeftMargin != leftMargin) {
      this.innerLeftMargin = leftMargin;
      this.innerRightMargin = rightMargin;
      buildLayout();
    } else if (this.innerRightMargin != rightMargin) {
      int oldTextWidth = getCheckTextMaxWidth();
      this.innerRightMargin = rightMargin;
      if (layoutTextsIfNeeded(oldTextWidth)) {
        invalidate();
      }
    }
  }

  // Position calculation

  // Receiver utils

  private int calculateHeaderHeight () {
    return calculateHeaderHeight(scaleFactor);
  }

  private int calculateHeaderHeight (float scaleFactor) {
    return currentHeaderOffset + HeaderView.getSize(false) + (int) ((HeaderView.getBigSize(false) - HeaderView.getSize(false)) * scaleFactor);
  }

  private float calculateRealScaleFactor () {
    final int startY = Screen.dp(144f) - HeaderView.getSize(false);
    final int currentHeight = calculateHeaderHeight() - currentHeaderOffset - HeaderView.getSize(false);
    if (startY < currentHeight) {
      return 1f;
    }
    return (float) currentHeight / (float) startY;
  }

  private void layoutReceiver () {
    float baseRadius = Screen.dp(getBaseAvatarRadiusDp());
    float baseCenterX = innerLeftMargin + Screen.dp(4f) + baseRadius;
    float baseCenterY = currentHeaderOffset + HeaderView.getSize(false) / 2;

    float scaleFactor = calculateRealScaleFactor();

    if (scaleFactor != 0f) {
      baseCenterX += -Screen.dp(33f) * scaleFactor;
      baseCenterY += Screen.dp(64f) * scaleFactor;
      baseRadius += Screen.dp(10f) * scaleFactor;
    }

    float expandFactor = getAvatarExpandFactor();
    int viewCenterX = getMeasuredWidth() / 2;
    int viewCenterY = currentHeaderOffset + (calculateHeaderHeight() - currentHeaderOffset) / 2;
    // avatarReceiver.setRadius(Math.round(baseRadius * (1f - expandFactor)));
    receiver.forceFullScreen(expandFactor != 0f, expandFactor);

    baseRadius += (viewCenterX - baseRadius) * expandFactor;
    baseCenterX += (viewCenterX - baseCenterX) * expandFactor;
    baseCenterY += (viewCenterY - baseCenterY) * expandFactor;
    receiver.setBounds(Math.round(baseCenterX - baseRadius), Math.round(baseCenterY - baseRadius), Math.round(baseCenterX + baseRadius), Math.round(baseCenterY + baseRadius));
  }

  // Private utils

  public void setIgnoreCustomHeight () {
    this.flags |= FLAG_IGNORE_CUSTOM_HEIGHT;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildLayout();
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      buildLayout();
    }
  }

  // Layout utils

  private static int getMutePadding () {
    return Screen.dp(1f);
  }

  private void buildLayout () {
    updateTopShadow();
    updateBottomShadow();
    layoutReceiver();
    layoutTexts();
    invalidate();
  }

  private int getCheckTextMaxWidth () {
    return getCurrentScaledTextMaxWidth();
  }

  private boolean layoutTextsIfNeeded (int oldTextMaxWidth) {
    int newTextMaxWidth = getCheckTextMaxWidth();
    if (newTextMaxWidth < oldTextMaxWidth || (newTextMaxWidth > oldTextMaxWidth && hasTrimmedText())) {
      layoutTexts();
      return true;
    }
    return false;
  }

  private void layoutTexts () {
    layoutTitle();
    layoutSubtext();
    layoutChatAction();
  }

  private int getBaseTextMaxWidth () {
    return Math.max(0, getMeasuredWidth() - (innerLeftMargin + getInnerRightMargin()) - Screen.dp(4f) - Screen.dp(getBaseAvatarRadiusDp()) * 2 - Screen.dp(9f));
  }

  private int getScaledTextMaxWidth () {
    return (int) ((float) (getBaseTextMaxWidth() + Screen.dp(11f) + getInnerRightMargin()) / 1.1f);
  }

  private int getCurrentScaledTextMaxWidth () {
    // return getBaseTextMaxWidth() + (int) Math.ceil((float) (getScaledTextMaxWidth() - getBaseTextMaxWidth()) * scaleFactor);
    return Math.min(getBaseTextMaxWidth(), getScaledTextMaxWidth());
  }

  private int getExpandedMaxTextWidth () {
    return (int) ((float) (getMeasuredWidth() - Screen.dp(11f) * 2) / avatarTextScale) - (BitwiseUtils.getFlag(flags, FLAG_SHOW_VERIFY) ? Screen.dp(20f) : 0);
  }

  /*private int calculateTextMaxWidth (boolean allowScale) {
    float avatarExpandFactor = getAvatarExpandFactor();
    int baseMaxTextWidth = getMeasuredWidth() - (innerLeftMargin + innerRightMargin) - Screen.dp(4f) - Screen.dp(getBaseAvatarRadiusDp()) * 2 - Screen.dp(9f);
    if (scaleFactor != 0f) {
      baseMaxTextWidth += (Screen.dp(11f) + innerRightMargin) * scaleFactor;
    }
    if (avatarExpandFactor != 0f) {
      baseMaxTextWidth += (getExpandedMaxTextWidth() - baseMaxTextWidth) * avatarExpandFactor;
    }
    if (allowScale) {
      float textScaleFactor = U.fromTo(1f + scaleFactor * .1f, avatarTextScale, avatarExpandFactor);
      if (textScaleFactor != 1f) {
        baseMaxTextWidth = (int) Math.ceil((float) baseMaxTextWidth / textScaleFactor);
      }
    }
    return Math.max(0, baseMaxTextWidth);
  }*/

  private static final float DEFAULT_AVATAR_TEXT_SCALE = 1.3f;
  private float avatarTextScale = DEFAULT_AVATAR_TEXT_SCALE;
  private int additionalTextEndPadding;

  private void layoutTitle () {
    if (StringUtils.isEmpty(title)) {
      trimmedTitle = trimmedTitleExpanded = null;
    } else {
      final boolean showScam = (flags & FLAG_SHOW_SCAM) != 0;
      final boolean showFake = (flags & FLAG_SHOW_FAKE) != 0;

      if (showFake || showScam) {
        additionalTextEndPadding = getOutlinedWidth(Lang.getString(showFake ? R.string.FakeMark : R.string.ScamMark));
      } else {
        additionalTextEndPadding = 0;
      }

      avatarTextScale = DEFAULT_AVATAR_TEXT_SCALE;
      trimmedTitle = new Text.Builder(title, getCurrentScaledTextMaxWidth() - additionalTextEndPadding, Paints.robotoStyleProvider(18), getTitleColorSet())
        .lineWidthProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> defaultMaxWidth - getTextOffsetLeft() - getTextOffsetRight())
        .lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? getTextOffsetLeft() : 0)
        .singleLine()
        .clipTextArea()
        .allBold()
        .build();
      trimmedTitleExpanded = null;
      if (trimmedTitle.isEllipsized()) {
        int maxLineCount = 2;
        do {
          trimmedTitleExpanded = new Text.Builder(title, getExpandedMaxTextWidth() - additionalTextEndPadding, Paints.robotoStyleProvider(18), getTitleColorSet())
            .lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? getTextOffsetLeft() : 0)
            .maxLineCount(maxLineCount)
            .clipTextArea()
            .allBold()
            .build();
          if (!trimmedTitleExpanded.isEllipsized()) {
            break;
          }
          if (maxLineCount == 2) {
            maxLineCount++;
          }
          avatarTextScale -= .05f;
        } while (true);
      }
      updateBottomShadow();
    }
  }

  private void layoutSubtext () {
    if (StringUtils.isEmpty(subtitle)) {
      trimmedSubtitle = trimmedSubtitleExpanded = null;
    } else {
      TextColorSet colorSet = this::getSubtitleColor;
      trimmedSubtitle = new Text.Builder(subtitle, getCurrentScaledTextMaxWidth(), Paints.robotoStyleProvider(14), colorSet)
        .singleLine()
        .entities(subtitleEntities, null)
        .build();
      if (trimmedSubtitle.isEllipsized() || (expandedSubtitle != null && !expandedSubtitle.equals(subtitle))) {
        trimmedSubtitleExpanded = new Text.Builder(expandedSubtitle != null ? expandedSubtitle : subtitle, getExpandedMaxTextWidth(), Paints.robotoStyleProvider(14), colorSet)
          .singleLine()
          .entities(expandedSubtitle != null ? null /*TODO*/ : subtitleEntities, null)
          .build();
      } else {
        trimmedSubtitleExpanded = null;
      }
    }
  }

  private TextColorSet getTitleColorSet () {
    return this::getTitleColor;
  }

  private int getTypingColor () {
    float darkness = Theme.getDarkFactor();
    if (darkness == 0f) {
      if (this instanceof ChatHeaderView) {
        return Theme.headerTextColor();
      } else {
        return ColorUtils.color(0xff, (subtitleColor & 0x00ffffff));
      }
    } else if (darkness == 1f) {
      return Theme.chatListActionColor();
    } else {
      return ColorUtils.fromToArgb(this instanceof ChatHeaderView ? Theme.headerTextColor() : ColorUtils.color(0xff, (subtitleColor & 0x00ffffff)), Theme.chatListActionColor(), darkness);
    }
  }

  @Override
  public final void prepareColorChangers (int fromColor, int toColor) { }

  public static int subtitleColor (int headerColor) {
    return ColorUtils.alphaColor(Theme.getSubtitleAlpha(), headerColor);
  }

  private int titleColor = Color.WHITE, subtitleColor = ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Color.WHITE);

  @Override
  public final void setTextColor (int textColor) {
    titleColor = textColor;
    subtitleColor = subtitleColor(textColor);
    invalidate();
  }

  public void setTextColors (int textColor, int subtitleColor) {
    this.titleColor = textColor;
    this.subtitleColor = subtitleColor;
    invalidate();
  }

  private float muteFadeFactor;

  public void setMuteFadeFactor (float factor) {
    if (this.muteFadeFactor != factor) {
      this.muteFadeFactor = factor;
      if (BitwiseUtils.getFlag(flags, FLAG_SHOW_MUTE) && !BitwiseUtils.getFlag(flags, FLAG_IGNORE_MUTE)) {
        invalidate();
      }
    }
  }

  // Drawing

  private static final int LOCK_ICON = R.drawable.deproko_baseline_lock_24;

  private int getTextOffsetLeft () {
    return (flags & FLAG_SHOW_LOCK) != 0 ? Screen.dp(16f) : 0;
  }

  public static float getBaseAvatarRadiusDp () {
    return 20.5f;
  }

  public void setIgnoreMute (boolean ignoreMute) {
    if (setFlags(BitwiseUtils.setFlag(flags, FLAG_IGNORE_MUTE, ignoreMute)) && BitwiseUtils.getFlag(flags, FLAG_SHOW_MUTE)) {
      layoutTitle();
      invalidate();
    }
  }

  private int getTextOffsetRight () {
    int right = 0;
    if ((flags & FLAG_SHOW_VERIFY) != 0) {
      right += Screen.dp(20f);
    }
    if ((flags & FLAG_SHOW_MUTE) != 0 && (flags & FLAG_IGNORE_MUTE) == 0) {
      right += getMutePadding() + Icons.getChatMuteDrawableWidth();
    }
    return right;
  }

  public MediaViewThumbLocation getThumbLocation () {
    MediaViewThumbLocation location = new MediaViewThumbLocation();
    location.set(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
    location.setClip(0, Math.max(-receiver.getTop(), 0), 0, Math.max(0, receiver.getBottom() - calculateHeaderHeight()));
    float radius = receiver.getDisplayRadius();
    location.setColorId(R.id.theme_color_headerBackground);
    location.setRoundings(radius, radius, radius, radius);
    return location;
  }

  private static final float VERIFY_ALPHA = .7f;

  private static float invert (float x, int viewWidth, float itemWidth) {
    return Lang.rtl() ? viewWidth - x - itemWidth : x;
  }

  private float getMultiLineAddition () {
    return (trimmedTitleExpanded != null && trimmedTitle != null ? (trimmedTitleExpanded.getHeight() - trimmedTitle.getHeight()) * avatarTextScale : 0);
  }

  private int getTitleHeight () {
    return (int) ((float) (trimmedTitleExpanded != null ? trimmedTitleExpanded.getHeight() : trimmedTitle != null ? trimmedTitle.getHeight() : 0) * avatarTextScale);
  }

  private int getTitleColor () {
    return ColorUtils.fromToArgb(this.titleColor, Color.WHITE, getAvatarExpandFactor());
  }

  private int getSubtitleColor () {
    return ColorUtils.fromToArgb(this.subtitleColor, ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Color.WHITE), getAvatarExpandFactor());
  }

  @Override
  protected void onDraw (Canvas c) {
    TdlibStatusManager.ChatState state = (flags & FLAG_NO_STATUS) != 0 ? null : status.drawingState();
    float statusVisibility = state != null ? state.visibility() : 0f;
    float textAlpha = 1f - statusVisibility;
    int dy = (int) (Screen.dp(12f) * statusVisibility);
    int viewWidth = getMeasuredWidth();
    float avatarExpandFactor = getAvatarExpandFactor();

    final int saveCount = Views.save(c);
    try {
      c.clipRect(0, 0, getMeasuredWidth(), calculateHeaderHeight());

      final boolean showLock = (flags & FLAG_SHOW_LOCK) != 0;
      final boolean showMute = (flags & FLAG_SHOW_MUTE) != 0 && (flags & FLAG_IGNORE_MUTE) == 0;
      final boolean showVerify = (flags & FLAG_SHOW_VERIFY) != 0;
      final boolean showArrow = (flags & FLAG_NEED_ARROW) != 0;
      final boolean showScam = (flags & FLAG_SHOW_SCAM) != 0;
      final boolean showFake = (flags & FLAG_SHOW_FAKE) != 0;

      final int titleColor = getTitleColor();
      final int subtitleColor = getSubtitleColor();
      final float scaleFactor = calculateRealScaleFactor();
      final float textScaleFactor = MathUtils.fromTo(1f + scaleFactor * .1f, avatarTextScale, avatarExpandFactor);

      layoutReceiver();
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, receiver.getDisplayRadius(), Theme.headerPlaceholderColor());
      }
      receiver.draw(c);
      if (avatarExpandFactor > 0f && receiver.getRequestedPlaceholder() == null) {
        getTopShadow().setAlpha((int) (255f * .8f * avatarExpandFactor));
        getTopShadow().draw(c);

        c.save();
        int headerHeight = calculateHeaderHeight();
        int shadowTop = headerHeight - getBottomShadowSize();
        // c.clipRect(0, Math.max(shadowTop, getTopShadow().getBounds().bottom), getMeasuredWidth(), headerHeight);
        c.translate(0, Math.max(shadowTop, getTopShadow().getBounds().bottom - Screen.dp(28f)));
        getBottomShadow().setAlpha((int) (255f * .8f * avatarExpandFactor));
        getBottomShadow().draw(c);
        c.restore();
      }

      float baseTextLeft = innerLeftMargin + Screen.dp(4f) + Screen.dp(getBaseAvatarRadiusDp()) * 2 + Screen.dp(9f);
      float baseTitleTop = currentHeaderOffset + Screen.dp(7f);
      float baseSubtitleTop = currentHeaderOffset + Screen.dp(30f);
      if (scaleFactor != 0f) {
        baseTextLeft += -Screen.dp(11f) * scaleFactor;
        baseTitleTop += Screen.dp(62f) * scaleFactor;
        baseSubtitleTop += Screen.dp(68f) * scaleFactor;
      }
      if (avatarExpandFactor != 0f) {
        baseTextLeft += (Screen.dp(11f) - baseTextLeft) * avatarExpandFactor;


        // baseTitleTop += ((calculateHeaderHeight() - Screen.dp(28f) - (trimmedTitleExpanded != null ? trimmedTitleExpanded.getHeight() + trimmedTitleExpanded.getLineHeight(false) : trimmedTitle != null ? trimmedTitle.getHeight() + trimmedTitle.getLineHeight(false) : 0) * avatarTextScale) + (trimmedSubtitleExpanded != null ? trimmedSubtitleExpanded.getHeight() : trimmedTitle != null ? trimmedTitle.getHeight() : 0) - baseTitleTop) * avatarExpandFactor;
        baseTitleTop += ((calculateHeaderHeight() - Screen.dp(28f) - Screen.dp(5f) - getTitleHeight()) - baseTitleTop) * avatarExpandFactor;
        baseSubtitleTop += ((calculateHeaderHeight() - Screen.dp(28f)) - baseSubtitleTop) * avatarExpandFactor;
      }

      if (trimmedTitle != null) {
        c.save();
        c.translate(baseTextLeft, baseTitleTop);
        c.scale(textScaleFactor, textScaleFactor);

        // c.drawRect(0, 0, calculateTextMaxWidth(true), trimmedTitle.getHeight(), Paints.fillingPaint(0xffff0000));

        if (showLock) {
          Drawable drawable = getSparseDrawable(LOCK_ICON, 0);
          Drawables.draw(c, drawable, -Screen.dp(5f), trimmedTitle.getHeight() / 2 - drawable.getMinimumHeight() / 2 + Screen.dp(1f), titleColor == Theme.headerTextColor() ? Paints.getHeaderPorterDuffPaint() : Paints.getPorterDuffPaint(titleColor));
        }

        float iconAlpha = 1f - avatarExpandFactor;
        if (trimmedTitleExpanded != null && avatarExpandFactor > 0f) {
          if (avatarExpandFactor < 1f) {
            trimmedTitle.draw(c, 0, 0, null, 1f - avatarExpandFactor);
          }
          trimmedTitleExpanded.draw(c, 0, 0, null, avatarExpandFactor);
        } else {
          trimmedTitle.draw(c, 0, 0, null, 1f);
        }

        float baseIconLeft = trimmedTitle.getWidth() + (showLock ? Screen.dp(16f) : 0);
        float toIconLeft = trimmedTitleExpanded != null ? trimmedTitleExpanded.getLastLineWidth() : baseIconLeft;
        float iconLeft = baseIconLeft + (toIconLeft - baseIconLeft) * avatarExpandFactor;
        float iconTop = trimmedTitleExpanded != null ? (trimmedTitleExpanded.getHeight() - trimmedTitle.getHeight()) * avatarExpandFactor : 0;
        float iconsAdded = 0;

        if (showVerify) {
          Paint paint = Paints.getPorterDuffPaint(titleColor);
          int alpha = paint.getAlpha();
          paint.setAlpha((int) ((float) alpha * (VERIFY_ALPHA + (1f - VERIFY_ALPHA) * avatarExpandFactor)));
          Drawable drawable = getSparseDrawable(R.drawable.deproko_baseline_verify_24, 0);
          Drawables.draw(c, drawable, iconLeft, iconTop + trimmedTitle.getHeight() / 2f - drawable.getMinimumHeight() / 2f, paint);
          paint.setAlpha(alpha);
          iconsAdded += drawable.getMinimumWidth();
        }

        if (showFake || showScam) {
          int baseX = (int) (iconLeft + iconsAdded + Screen.dp(10f));
          int baseY = (int) (iconTop - Screen.dp(1.5f));
          drawOutlinedText(c, Lang.getString(showFake ? R.string.FakeMark : R.string.ScamMark), baseX, baseY, trimmedTitleExpanded != null ? MathUtils.fromTo(trimmedTitle.getLineHeight(), trimmedTitleExpanded.getLineHeight(), avatarExpandFactor) : trimmedTitle.getLineHeight());
          iconsAdded += additionalTextEndPadding;
        }

        if (showMute) {
          float muteAlpha = (1f - this.muteFadeFactor) * iconAlpha;
          Drawable drawable = getSparseDrawable(R.drawable.deproko_baseline_notifications_off_24, 0);
          Drawables.draw(c, drawable, baseIconLeft + iconsAdded, trimmedTitle.getHeight() / 2f - drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(R.id.theme_color_headerText, muteAlpha * .4f));
          iconLeft += drawable.getMinimumWidth();
        }

        c.restore();
      }

      if (trimmedSubtitle != null) {
        if (trimmedSubtitleExpanded != null && avatarExpandFactor > 0f) {
          if (avatarExpandFactor < 1f) {
            trimmedSubtitle.draw(c, (int) baseTextLeft, (int) baseSubtitleTop + dy, null, textAlpha * (1f - avatarExpandFactor));
          }
          trimmedSubtitleExpanded.draw(c, (int) baseTextLeft, (int) baseSubtitleTop + dy, null, textAlpha * avatarExpandFactor);
        } else {
          trimmedSubtitle.draw(c, (int) baseTextLeft, (int) baseSubtitleTop + dy, null, textAlpha);
        }
      }

      if (showArrow) {
        Drawables.draw(c, arrowDrawable, baseTextLeft + Math.max(trimmedTitle != null ? trimmedTitle.getWidth() : 0, trimmedSubtitle != null ? trimmedSubtitle.getWidth() : 0) + Screen.dp(4f), Size.getHeaderPortraitSize() / 2 - arrowDrawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(ColorUtils.alphaColor(.25f, titleColor)));
      }

      if (statusVisibility > 0f) {
        Text text = status.drawingText();
        if (text != null) {
          float top = baseSubtitleTop - Screen.dp(13f) * textAlpha;
          int statusTextColor;
          float darkness = Theme.getDarkFactor();
          int knownThemeColorId = 0;
          if (darkness == 0f) {
            if (this instanceof ChatHeaderView) {
              statusTextColor = Theme.headerTextColor();
              knownThemeColorId = R.id.theme_color_headerText;
            } else {
              statusTextColor = ColorUtils.color(0xff, (subtitleColor & 0x00ffffff));
            }
          } else if (darkness == 1f) {
            statusTextColor = Theme.chatListActionColor();
            knownThemeColorId = R.id.theme_color_chatListAction;
          } else {
            statusTextColor = ColorUtils.fromToArgb(this instanceof ChatHeaderView ? Theme.headerTextColor() : ColorUtils.color(0xff, (subtitleColor & 0x00ffffff)), Theme.chatListActionColor(), darkness);
            knownThemeColorId = R.id.theme_color_chatListAction;
          }
          DrawAlgorithms.drawStatus(c, state, baseTextLeft, top + text.getLineHeight() / 2f, ColorUtils.alphaColor(statusVisibility, statusTextColor), this, statusVisibility == 1f ? knownThemeColorId : 0);
          text.draw(c, (int) baseTextLeft, (int) top, null, statusVisibility);
        }
      }
    } finally {
      Views.restore(c, saveCount);
    }
  }

  private void drawOutlinedText (Canvas c, String text, int cx, int cy, int height) {
    int additionalPaddingVert = Screen.dp(2f);
    int additionalPadding = Screen.dp(4f);

    Paint textPaint = Paints.getMediumTextPaint(12, getSubtitleColor(), false);
    float textWidth = U.measureText(text, textPaint);

    RectF rct = Paints.getRectF();
    rct.set(cx - additionalPadding, cy + additionalPaddingVert, cx + textWidth + additionalPadding, cy + height - additionalPaddingVert);
    c.drawRoundRect(rct, Screen.dp(2f), Screen.dp(2f), Paints.getProgressPaint(getSubtitleColor(), Screen.dp(1.5f)));

    c.drawText(text, cx, cy + Screen.dp(16f), textPaint);
  }

  private int getOutlinedWidth (String text) {
    return (int) ((Screen.dp(6f) + (Screen.dp(4f) * 2) + U.measureText(text, Paints.getMediumTextPaint(12, getSubtitleColor(), false))));
  }

  private Callback callback;

  public void setPhotoOpenCallback (Callback callback) {
    this.callback = callback;
  }

  private void openComplexPhoto () {
    if (!usesDefaultClickListener()) {
      ViewUtils.onClick(this);
    }
    if (callback != null) {
      callback.performComplexPhotoOpen();
    }
  }

  public void setAllowEmptyClick () {
    flags |= FLAG_ALLOW_EMPTY_CLICK;
  }

  private boolean checkCaught (float x, float y, boolean set) {
    if ((receiver.getRequestedPlaceholder() != null && (flags & FLAG_ALLOW_EMPTY_CLICK) == 0) || callback == null) {
      if (set) {
        flags &= ~FLAG_CAUGHT;
      }
      return false;
    }

    boolean caught = y < calculateHeaderHeight() && receiver.isInsideReceiver(x, y);
    if (set) {
      this.flags = BitwiseUtils.setFlag(this.flags, FLAG_CAUGHT, caught);
    }
    return caught;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return (super.needClickAt(view, x, y) && y < calculateHeaderHeight()) || checkCaught(x, y, false);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if ((flags & FLAG_PHOTO_OPEN_DISABLED) == 0) {
      checkCaught(x, y, true);
      if ((flags & FLAG_CAUGHT) != 0) {
        openComplexPhoto();
        flags &= ~FLAG_CAUGHT;
        return;
      }
    }
    super.onClickAt(view, x, y);
  }

  public interface Callback {
    void performComplexPhotoOpen ();
  }

  @Override
  public MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    return null;
  }

  @Override
  public void modifyMediaArguments (Object cause, MediaViewController.Args args) {
    args.delegate = new MediaViewDelegate() {
      @Override
      public MediaViewThumbLocation getTargetLocation (int index, MediaItem item) {
        if (index == 0) {
          return getThumbLocation();
        }
        return null;
      }

      @Override
      public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) {

      }
    };
  }

  // Preview

  @Override
  public boolean needsForceTouch (BaseView v, float x, float y) {
    int bound = Screen.dp(6f);
    return (receiver.getRequestedChatPhoto() != null || receiver.getRequestedProfilePhoto() != null || receiver.getRequestedChatPhotoInfo() != null) && x >= receiver.getLeft() - bound && x < receiver.getRight() + bound && y >= receiver.getTop() - bound && y < receiver.getBottom() + bound;
  }

  public AvatarReceiver getAvatarReceiver () {
    return receiver;
  }

  @Override
  public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
    return false;
  }

  @Override
  public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
    SimpleMediaViewController.Args args = null;

    TdApi.ProfilePhoto profilePhoto = receiver.getRequestedProfilePhoto();
    TdApi.ChatPhotoInfo chatPhotoInfo = receiver.getRequestedChatPhotoInfo();
    if (profilePhoto != null) {
      args = new SimpleMediaViewController.Args(profilePhoto, receiver.getRequestedUserId());
    } else if (chatPhotoInfo != null) {
      args = new SimpleMediaViewController.Args(chatPhotoInfo, receiver.getRequestedChatId());
    }

    if (args != null) {
      SimpleMediaViewController m = new SimpleMediaViewController(getContext(), tdlib);
      m.setArguments(args);
      return m;
    }
    return null;
  }

  // Typings

  private final TdlibStatusManager.Helper status;

  public void attachChatStatus (long chatId, long messageThreadId) {
    status.attachToChat(chatId, messageThreadId);
  }

  public void removeChatStatus () {
    status.detachFromAnyChat();
  }

  @Override
  public void layoutChatAction () {
    String chatActionText = status.fullText();
    Text trimmedChatAction;
    if (StringUtils.isEmpty(chatActionText)) {
      trimmedChatAction = null;
    } else {
      int avail = getCurrentScaledTextMaxWidth();
      if (avail > 0) {
        int width = status.actionIconWidth();
        trimmedChatAction = new Text.Builder(chatActionText, avail, Paints.robotoStyleProvider(14), this::getTypingColor).lineMarginProvider(width > 0 ? (lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? width : 0 : null).singleLine().build();
      } else {
        trimmedChatAction = null;
      }
    }
    status.setDrawingText(trimmedChatAction);
  }

  @Override
  public void invalidateTypingPart (boolean onlyIcon) {
    // TODO invalidate only specific part
    invalidate();
  }

  @Override
  public boolean canLoop () {
    return true;
  }

  @Override
  public boolean canAnimate () {
    return true;
  }
}
