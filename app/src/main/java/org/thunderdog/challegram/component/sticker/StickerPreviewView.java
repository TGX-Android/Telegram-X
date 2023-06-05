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
 * File created on 23/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifActor;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.EmojiString;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class StickerPreviewView extends FrameLayoutFix implements FactorAnimator.Target, PopupLayout.AnimatedPopupProvider, BackListener, Destroyable, ThemeChangeListener {
  private static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(1f);

  private static final int REVEAL_ANIMATOR = 0;
  private static final int REPLACE_ANIMATOR = 1;

  private static final long REVEAL_DURATION = 268l;
  private static final long HIDE_DURATION = 292l;

  private final FactorAnimator animator;

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private final ImageReceiver preview;

  private final ImageReceiver effectImageReceiver;
  private final GifReceiver effectGifReceiver;
  private final ImageReceiver effectPreview;

  private final StickerView stickerView;

  private final TextPaint paint;

  private final ThemeListenerList themeListenerList = new ThemeListenerList();

  public StickerPreviewView (Context context) {
    super(context);
    this.paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    this.paint.setTextSize(Screen.dp(30f));
    this.paint.setTypeface(Fonts.getRobotoMedium());
    this.stickerView = new StickerView(context);
    this.stickerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(stickerView);
    ViewSupport.setThemedBackground(this, ColorId.previewBackground);
    themeListenerList.addThemeInvalidateListener(this);
    setAlpha(0f);
    stickerView.setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    this.animator = new FactorAnimator(REVEAL_ANIMATOR, this, OVERSHOOT_INTERPOLATOR, REVEAL_DURATION);
    this.preview = new ImageReceiver(stickerView, 0);
    this.imageReceiver = new ImageReceiver(stickerView, 0);
    this.gifReceiver = new GifReceiver(stickerView);
    this.effectPreview = new ImageReceiver(stickerView, 0);
    this.effectImageReceiver = new ImageReceiver(stickerView, 0);
    this.effectGifReceiver = new GifReceiver(stickerView);
    ThemeManager.instance().addThemeListener(this);
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    themeListenerList.onThemeColorsChanged(areTemp);
  }

  private StickerSmallView controllerView;

  public void setControllerView (StickerSmallView stickerView) {
    this.controllerView = stickerView;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    closePreviewIfNeeded();
    return true;
  }

  @Override
  public void prepareShowAnimation () { }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    this.pendingPopup = popup;
    animator.animateTo(1f);
  }

  private PopupLayout pendingPopup;

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator ignored) {
    animator.cancel();
    //animator.setInterpolator(Anim.DECELERATE_INTERPOLATOR);
    animator.setDuration(HIDE_DURATION);
    if (animator.getFactor() == 0f) {
      popup.onCustomHideAnimationComplete();
    } else {
      pendingPopup = popup;
      animator.animateTo(0f);
    }
    return true;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        final float alpha = Math.max(0f, Math.min(1f, factor));
        setAlpha(alpha);
        setAppearFactor(factor);
        break;
      }
      case REPLACE_ANIMATOR: {
        setReplaceFactor(factor);
        break;
      }
      case ANIMATOR_MENU: {
        setMenuFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        if (finalFactor == 0f) {
          imageReceiver.clear();
          preview.clear();
          gifReceiver.clear();
          effectImageReceiver.clear();
          effectPreview.clear();
          effectGifReceiver.clear();
          if (pendingPopup != null) {
            pendingPopup.onCustomHideAnimationComplete();
          }
        } else if (finalFactor == 1f) {
          if (pendingPopup != null) {
            pendingPopup.onCustomShowComplete();
          }
        }
        break;
      }
      case ANIMATOR_MENU: {
        if (finalFactor == 0f && menu != null) {
          removeView(menu);
          menu = null;
        }
        break;
      }
    }
  }

  @Override
  public void performDestroy () {
    ThemeManager.instance().removeThemeListener(this);
    imageReceiver.clear();
    gifReceiver.clear();
    preview.clear();
    effectImageReceiver.clear();
    effectPreview.clear();
    effectGifReceiver.clear();
    if (currentSticker != null) {
      if (currentSticker.isAnimated()) {
        GifActor.addFreezeReason(currentSticker.getFullAnimation(), false);
      }
      currentSticker = null;
    }
    if (currentEffectSticker != null) {
      if (currentEffectSticker.isAnimated()) {
        GifActor.addFreezeReason(currentEffectSticker.getFullAnimation(), false);
      }
      currentEffectSticker = null;
    }
  }

  private TGStickerObj currentSticker;
  private int stickerWidth, stickerHeight;

  private TGStickerObj currentEffectSticker;
  private int effectStickerWidth, effectStickerHeight;

  private int getStickerCenterX () {
    return getMeasuredWidth() / 2;
  }

  private int getStickerCenterY () {
    return getDesiredHeight() / 2;
  }

  private int stickerMaxWidth;
  private int effectStickerMaxWidth;

  private void layoutReceivers () {
    if (currentSticker != null) {
      int cx = getStickerCenterX();
      int cy = getStickerCenterY();

      stickerWidth = (int) Math.floor(currentSticker.getWidth() * currentSticker.getDisplayScale());
      stickerHeight = (int) Math.floor(currentSticker.getHeight() * currentSticker.getDisplayScale());

      stickerMaxWidth = Math.min(Screen.dp(190f), Screen.smallestSide() - Screen.dp(86f));
      if (Math.max(stickerWidth, stickerHeight) != stickerMaxWidth) {
        float ratio = Math.min((float) stickerMaxWidth / (float) stickerWidth, (float) stickerMaxWidth / (float) stickerHeight);
        stickerWidth = (int) ((float) stickerWidth * ratio);
        stickerHeight = (int) ((float) stickerHeight * ratio);
      }

      contour = currentSticker.getContour(stickerWidth, stickerHeight);
      preview.setBounds(cx - stickerWidth / 2, cy - stickerHeight / 2, cx + stickerWidth / 2, cy + stickerHeight / 2);
      imageReceiver.setBounds(cx - stickerWidth / 2, cy - stickerHeight / 2, cx + stickerWidth / 2, cy + stickerHeight / 2);
      gifReceiver.setBounds(cx - stickerWidth / 2, cy - stickerHeight / 2, cx + stickerWidth / 2, cy + stickerHeight / 2);
    }

    if (currentEffectSticker != null) {
      int cx = getStickerCenterX();
      int cy = getStickerCenterY();

      effectStickerWidth = currentEffectSticker.getWidth();
      effectStickerHeight = currentEffectSticker.getHeight();

      effectStickerMaxWidth = Math.min(Screen.dp(350f), Screen.smallestSide() - Screen.dp(86f));
      if (Math.max(effectStickerWidth, effectStickerHeight) != effectStickerMaxWidth) {
        float ratio = Math.min((float) effectStickerMaxWidth / (float) effectStickerWidth, (float) effectStickerMaxWidth / (float) effectStickerHeight);
        effectStickerWidth = (int) ((float) effectStickerWidth * ratio);
        effectStickerHeight = (int) ((float) effectStickerHeight * ratio);
      }

      effectPreview.setBounds(cx - effectStickerWidth / 2, cy - effectStickerHeight / 2, cx + effectStickerWidth / 2, cy + effectStickerHeight / 2);
      effectImageReceiver.setBounds(cx - effectStickerWidth / 2, cy - effectStickerHeight / 2, cx + effectStickerWidth / 2, cy + effectStickerHeight / 2);
      effectGifReceiver.setBounds(cx - effectStickerWidth / 2, cy - effectStickerHeight / 2, cx + effectStickerWidth / 2, cy + effectStickerHeight / 2);
    }
  }

  private @Nullable EmojiString emojiString;
  private boolean disableEmojis;
  private Path contour;

  private void setSticker (TGStickerObj sticker, boolean loadPreview) {
    setSticker(sticker, null, loadPreview);
  }

  private void setSticker (TGStickerObj sticker, @Nullable TGStickerObj effectSticker, boolean loadPreview) {
    if (sticker.isAnimated()) {
      GifActor.addFreezeReason(sticker.getFullAnimation(), true);
    }
    if (currentSticker != null && currentSticker.isAnimated()) {
      GifActor.addFreezeReason(currentSticker.getFullAnimation(), false);
    }
    this.currentSticker = sticker;
    if (!sticker.isMasks() && !disableEmojis) {
      this.emojiString = new EmojiString(sticker.getAllEmoji(), -1, paint);
    } else {
      this.emojiString = null;
    }
    if (effectSticker == null) layoutReceivers();
    preview.requestFile(sticker.getImage());
    imageReceiver.requestFile(sticker.getFullImage());
    gifReceiver.requestFile(sticker.getFullAnimation());

    if (currentEffectSticker != null && currentEffectSticker.isAnimated()) {
      GifActor.addFreezeReason(currentEffectSticker.getFullAnimation(), false);
    }
    this.currentEffectSticker = effectSticker;
    if (effectSticker != null) {
      if (effectSticker.isAnimated()) {
        GifActor.addFreezeReason(effectSticker.getFullAnimation(), true);
      }
      layoutReceivers();
      effectPreview.requestFile(effectSticker.getImage());
      effectImageReceiver.requestFile(effectSticker.getFullImage());
      effectGifReceiver.requestFile(effectSticker.getFullAnimation());
    }

    hideMenu();
  }

  private int fromCx, fromCy, fromWidth, viewportHeight;

  private int getDesiredHeight () {
    return viewportHeight != -1 ? Math.min(getMeasuredHeight(), viewportHeight) : getMeasuredHeight();
  }

  private Tdlib tdlib;

  public void setSticker (Tdlib tdlib, TGStickerObj sticker, int cx, int cy, int width, int viewportHeight, boolean disableEmojis) {
    this.tdlib = tdlib;
    this.disableEmojis = disableEmojis;
    this.fromCx = cx;
    this.fromCy = cy;
    this.fromWidth = width;
    this.viewportHeight = viewportHeight;
    setSticker(sticker, false);
  }

  public void setReaction (Tdlib tdlib, TGReaction reaction, @Nullable TGStickerObj effectAnimation, int cx, int cy, int width, int viewportHeight, boolean disableEmojis) {
    this.tdlib = tdlib;
    this.disableEmojis = disableEmojis;
    this.fromCx = cx;
    this.fromCy = cy;
    this.fromWidth = width;
    this.viewportHeight = viewportHeight;
    setSticker(reaction.activateAnimationSicker(), effectAnimation != null ? effectAnimation : reaction.effectAnimationSicker(), false);
  }

  private LinearLayout menu;

  public void hideMenu () {
    if (menu != null) {
      setMenuVisible(false, true);
    }
  }

  private int calculateMaximumMenuItemWidth () {
    if (menu == null) {
      return Integer.MAX_VALUE;
    }
    final int availableWidth = getMeasuredWidth() - menu.getPaddingLeft() - menu.getPaddingRight();
    if (availableWidth > 0) {
      int imageButtonsWidth = 0;
      int textButtonsCount = 0;
      for (int i = 0; i < menu.getChildCount(); i++) {
        View view = menu.getChildAt(i);
        if (view instanceof ImageView) {
          imageButtonsWidth += view.getPaddingLeft() + view.getPaddingRight() + Math.max(0, view.getLayoutParams().width);
        } else if (view instanceof TextView) {
          textButtonsCount++;
        }
      }
      if (textButtonsCount > 0) {
        final int result = Math.max(0, (availableWidth - imageButtonsWidth) / textButtonsCount);
        return result > 0 ? result : Integer.MAX_VALUE;
      }
    }
    return Integer.MAX_VALUE;
  }

  private void applyMaximumMenuItemsWidth () {
    if (menu != null) {
      final int maximumItemWidth = calculateMaximumMenuItemWidth();
      for (int i = 0; i < menu.getChildCount(); i++) {
        View view = menu.getChildAt(i);
        if (view instanceof TextView) {
          TextView textView = (TextView) view;
          if (textView.getMaxWidth() != maximumItemWidth) { // Avoid unnecessary requestLayout
            textView.setMaxWidth(maximumItemWidth);
          }
        }
      }
    }
  }

  public void openMenu (final TGStickerObj sticker) {
    setMenuVisible(false, false);
    menu = new LinearLayout(getContext()) {
      @Override
      protected void onDraw (Canvas c) {
        RectF rectF = Paints.getRectF();
        rectF.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
        Paint paint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? Paints.fillingPaint(Theme.fillingColor()) : Paints.shadowFillingPaint(Theme.fillingColor());
        c.drawRoundRect(rectF, Screen.dp(2f), Screen.dp(2f), paint);
      }
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      menu.setElevation(Screen.dp(1f));
      menu.setTranslationZ(Screen.dp(1f));
      menu.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @TargetApi(value = 21)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), Screen.dp(2f));
        }
      });
    } else {
      Views.setLayerType(menu, LAYER_TYPE_SOFTWARE);
    }
    menu.setWillNotDraw(false);
    menu.setPadding(Screen.dp(4f), Screen.dp(4f), Screen.dp(4f), Screen.dp(4f));
    menu.setOrientation(LinearLayout.HORIZONTAL);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f) + menu.getPaddingTop() + menu.getPaddingBottom(), Gravity.CENTER_HORIZONTAL);
    params.topMargin = getStickerCenterY() + stickerHeight / 2 + Screen.dp(32f);
    menu.setLayoutParams(params);
    View.OnClickListener onClickListener = v -> {
      final int viewId = v.getId();
      if (viewId == R.id.btn_favorite) {
        final int stickerId = sticker.getId();
        if (tdlib.isStickerFavorite(stickerId)) {
          tdlib.client().send(new TdApi.RemoveFavoriteSticker(new TdApi.InputFileId(stickerId)), tdlib.okHandler());
        } else {
          tdlib.client().send(new TdApi.AddFavoriteSticker(new TdApi.InputFileId(stickerId)), tdlib.okHandler());
        }
        closePreviewIfNeeded();
      } else if (viewId == R.id.btn_send) {
        if (controllerView != null && controllerView.onSendSticker(v, sticker, Td.newSendOptions())) {
          closePreviewIfNeeded();
        }
      } else if (viewId == R.id.btn_view) {
        if (controllerView != null) {
          ViewController<?> context = findRoot();
          if (context != null) {
            tdlib.ui().showStickerSet(context, sticker.getStickerSetId(), null);
            closePreviewIfNeeded();
          }
        }
      } else if (viewId == R.id.btn_removeRecent) {
        final int stickerId = sticker.getId();
        tdlib.client().send(new TdApi.RemoveRecentSticker(false, new TdApi.InputFileId(stickerId)), tdlib.okHandler());
        closePreviewIfNeeded();
      } else {
        closePreviewIfNeeded();
      }
    };
    themeListenerList.addThemeInvalidateListener(menu);

    boolean isFavorite = tdlib.isStickerFavorite(sticker.getId());

    ImageView imageView = new ImageView(getContext());
    imageView.setId(R.id.btn_favorite);
    imageView.setScaleType(ImageView.ScaleType.CENTER);
    imageView.setOnClickListener(onClickListener);
    imageView.setImageResource(isFavorite ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
    imageView.setColorFilter(Theme.getColor(ColorId.textNeutral));
    themeListenerList.addThemeFilterListener(imageView, ColorId.textNeutral);
    imageView.setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(48f), ViewGroup.LayoutParams.MATCH_PARENT));
    imageView.setPadding(Lang.rtl() ? 0 : Screen.dp(8f), 0, Lang.rtl() ? Screen.dp(8f) : 0, 0);
    RippleSupport.setTransparentBlackSelector(imageView);
    Views.setClickable(imageView);
    if (Lang.rtl())
      menu.addView(imageView, 0);
    else
      menu.addView(imageView);

    boolean needViewPackButton = sticker.needViewPackButton();

    TextView sendView = new NoScrollTextView(getContext());
    sendView.setId(R.id.btn_send);
    sendView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    sendView.setTypeface(Fonts.getRobotoMedium());
    sendView.setTextColor(Theme.getColor(ColorId.textNeutral));
    themeListenerList.addThemeColorListener(sendView, ColorId.textNeutral);
    Views.setMediumText(sendView, Lang.getString(R.string.SendSticker).toUpperCase());
    sendView.setOnClickListener(onClickListener);
    RippleSupport.setTransparentBlackSelector(sendView);
    int paddingLeft = Screen.dp(12f);
    int paddingRight = Screen.dp(needViewPackButton ? 12f : 16f);
    sendView.setPadding(Lang.rtl() ? paddingRight : paddingLeft, 0, Lang.rtl() ? paddingLeft : paddingRight, 0);
    sendView.setGravity(Gravity.CENTER);
    sendView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (Lang.rtl())
      menu.addView(sendView, 0);
    else
      menu.addView(sendView);
    if (controllerView != null && controllerView.getStickerOutputChatId() != 0) {
      sendView.setOnLongClickListener(v -> {
        ViewController<?> c = findRoot();
        return c != null && tdlib.ui().showScheduleOptions(c, controllerView.getStickerOutputChatId(), true, (sendOptions, disableMarkdown) -> {
          if (controllerView.onSendSticker(v, sticker, sendOptions)) {
            closePreviewIfNeeded();
          }
        }, null, null);
      });
    }

    if (needViewPackButton) {
      TextView viewView = new NoScrollTextView(getContext());
      viewView.setId(R.id.btn_view);
      viewView.setTypeface(Fonts.getRobotoMedium());
      viewView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      viewView.setTextColor(Theme.getColor(ColorId.textNeutral));
      Views.setMediumText(viewView, Lang.getString(R.string.ViewPackPreview).toUpperCase());
      themeListenerList.addThemeColorListener(viewView, ColorId.textNeutral);
      viewView.setOnClickListener(onClickListener);
      RippleSupport.setTransparentBlackSelector(viewView);
      viewView.setPadding(Screen.dp(Lang.rtl() ? 16f : 12f), 0, Screen.dp(Lang.rtl() ? 12f : 16f), 0);
      viewView.setGravity(Gravity.CENTER);
      viewView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      if (Lang.rtl())
        menu.addView(viewView, 0);
      else
        menu.addView(viewView);
    }

    if (sticker.isRecent()) {
      ImageView removeRecentView = new ImageView(getContext());
      removeRecentView.setId(R.id.btn_removeRecent);
      removeRecentView.setScaleType(ImageView.ScaleType.CENTER);
      removeRecentView.setOnClickListener(onClickListener);
      removeRecentView.setImageResource(R.drawable.baseline_auto_delete_24);
      removeRecentView.setColorFilter(Theme.getColor(ColorId.textNegative));
      themeListenerList.addThemeFilterListener(removeRecentView, ColorId.textNegative);
      removeRecentView.setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(48f), ViewGroup.LayoutParams.MATCH_PARENT));
      removeRecentView.setPadding(Lang.rtl() ? Screen.dp(8f) : 0, 0, Lang.rtl() ? 0 : Screen.dp(8f), 0);
      RippleSupport.setTransparentBlackSelector(removeRecentView);
      Views.setClickable(removeRecentView);
      if (Lang.rtl())
        menu.addView(removeRecentView, 0);
      else
        menu.addView(removeRecentView);
    }

    menu.setAlpha(0f);
    addView(menu);

    applyMaximumMenuItemsWidth();

    setMenuVisible(true, true);
  }

  private ViewController<?> findRoot () {
    ViewController<?> context = ViewController.findRoot(controllerView);
    if (context == null) {
      context = UI.getCurrentStackItem(getContext());
    }
    return context;
  }

  private boolean isMenuVisible;
  private FactorAnimator menuAnimator;
  private static final int ANIMATOR_MENU = 3;

  private float menuFactor;

  private void setMenuFactor (float factor) {
    if (this.menuFactor != factor) {
      this.menuFactor = factor;
      menu.setAlpha(MathUtils.clamp(factor));
      float sourceScale = (.6f + .4f * menuFactor);
      menu.setScaleX(sourceScale);
      menu.setScaleY(sourceScale);
    }
  }

  public void setMenuVisible (boolean isMenuVisible, boolean animated) {
    if (this.isMenuVisible != isMenuVisible) {
      this.isMenuVisible = isMenuVisible;
      final float toFactor = isMenuVisible ? 1f : 0f;
      if (animated) {
        if (menuAnimator == null) {
          menuAnimator = new FactorAnimator(ANIMATOR_MENU, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 100l, this.menuFactor);
        }
        if (toFactor == 1f && menuFactor == 0f) {
          menuAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          menuAnimator.setDuration(290l);
        } else {
          menuAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          menuAnimator.setDuration(140l);
        }
        menuAnimator.animateTo(toFactor);
      } else {
        if (menuAnimator != null) {
          menuAnimator.forceFactor(toFactor);
        }
        setMenuFactor(toFactor);
        if (toFactor == 0f && menu != null) {
          removeView(menu);
          menu = null;
        }
      }
    }
  }

  private float replaceFactor;
  private FactorAnimator replaceAnimator;

  private void setReplaceFactor (float replaceFactor) {
    if (this.replaceFactor != replaceFactor) {
      this.replaceFactor = replaceFactor;
      stickerView.invalidate();
    }
  }

  public void replaceSticker (TGStickerObj sticker, int cx, int cy) {
    if (replaceAnimator == null) {
      replaceAnimator = new FactorAnimator(REPLACE_ANIMATOR, this, OVERSHOOT_INTERPOLATOR, 220, 1f);
    } else {
      replaceAnimator.forceFactor(1f);
    }
    replaceFactor = 1f;
    setSticker(sticker, true);
    this.fromCx = cx;
    this.fromCy = cy;
    replaceAnimator.animateTo(0f);
  }

  public void replaceReaction (TGReaction reaction, int cx, int cy) {
    if (replaceAnimator == null) {
      replaceAnimator = new FactorAnimator(REPLACE_ANIMATOR, this, OVERSHOOT_INTERPOLATOR, 220, 1f);
    } else {
      replaceAnimator.forceFactor(1f);
    }
    replaceFactor = 1f;
    setSticker(reaction.activateAnimationSicker(), reaction.effectAnimationSicker(), true);
    this.fromCx = cx;
    this.fromCy = cy;
    replaceAnimator.animateTo(0f);
  }

  public void replaceStartCords (int cx, int cy) {
    this.fromCx = cx;
    this.fromCy = cy;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceivers();
    applyMaximumMenuItemsWidth();
  }

  private float appearFactor;

  public void setAppearFactor (float factor) {
    if (this.appearFactor != factor) {
      this.appearFactor = factor;
      stickerView.invalidate();
    }
  }

  private static final float MIN_SCALE = .72f;

  private class StickerView extends View {
    public StickerView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      final boolean savedAppear = appearFactor != 1f;
      if (savedAppear) {
        final float fromScale = (float) fromWidth / (float) stickerWidth;
        final float scale = fromScale + (1f - fromScale) * appearFactor;
        final float cx = getStickerCenterX();
        final float cy = getStickerCenterY();
        c.save();
        c.translate((cx - fromCx) * (1f - appearFactor) * -1f, (cy - fromCy) * (1f - appearFactor) * -1f);
        c.scale(scale, scale, cx, cy);
      }

      final float scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - replaceFactor);

      boolean animated = currentSticker != null && currentSticker.isAnimated();
      Receiver receiver = animated ? gifReceiver : imageReceiver;

      if (emojiString != null) {
        int textX = receiver.centerX() - emojiString.getWidth() / 2;
        int textY = receiver.centerY() - stickerMaxWidth / 2 - Screen.dp(58f);

        final boolean saved = replaceFactor != 0f;
        if (saved) {
          c.save();
          c.scale(scale, scale, receiver.centerX(), textY + Screen.dp(15f));
        }
        emojiString.draw(c, textX, textY, 0xff000000, false);
        if (saved) {
          c.restore();
        }
      }

      final boolean savedReplace = replaceFactor != 0f;
      if (savedReplace) {
        c.save();
        c.scale(scale, scale, receiver.centerX(), receiver.centerY());
      }
      if (animated) {
        if (receiver.needPlaceholder()) {
          if (preview.needPlaceholder()) {
            preview.drawPlaceholderContour(c, contour);
          }
          preview.draw(c);
        } else {
          receiver.draw(c);
        }
      } else {
        if (receiver.needPlaceholder()) {
          if (preview.needPlaceholder()) {
            preview.drawPlaceholderContour(c, contour);
          }
          preview.draw(c);
        }
        receiver.draw(c);
      }

      if (Config.DEBUG_STICKER_OUTLINES) {
        preview.drawPlaceholderContour(c, contour);
      }

      if (currentEffectSticker != null) {
        if (effectGifReceiver.needPlaceholder()) {
          effectPreview.draw(c);
        } else {
          effectGifReceiver.draw(c);
        }
      }

      if (savedReplace) {
        c.restore();
      }

      if (savedAppear) {
        c.restore();
      }
    }
  }

  private void closePreviewIfNeeded () {
    if (controllerView != null) {
      controllerView.closePreviewIfNeeded();
    }
  }

  private float deltaX, deltaY;

  private @Nullable View findMenuItemByPosition (MotionEvent e, float x, float y) {
    if (menu == null) {
      return null;
    }
    final int left = menu.getLeft();
    final int top = menu.getTop();
    final int right = menu.getRight();
    final int bottom = menu.getBottom();
    deltaX = 0;
    deltaY = 0;

    if (x >= left && x <= right && y >= top && y <= bottom) {
      x -= left;
      y -= top;
      deltaX -= left;
      deltaY -= top;

      final int childCount = menu.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = menu.getChildAt(i);
        final int childLeft = view.getLeft();
        final int childTop = view.getTop();
        final int childRight = view.getRight();
        final int childBottom = view.getBottom();
        if (x >= childLeft && x <= childRight && y >= childTop && y <= childBottom) {
          return view;
        }
      }
    }

    return null;
  }

  private View currentMenuItem;

  private void dispatchMenuItemEvent (MotionEvent e, View item, int action) {
    item.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), action, e.getX() + deltaX - item.getLeft(), e.getY() + deltaY - item.getTop(), e.getMetaState()));
  }

  private void setCurrentMenuItem (MotionEvent e, View item) {
    if (this.currentMenuItem != item) {
      if (this.currentMenuItem != null) {
        dispatchMenuItemEvent(e, currentMenuItem, MotionEvent.ACTION_CANCEL);
      }
      this.currentMenuItem = item;
      if (item != null) {
        dispatchMenuItemEvent(e, item, MotionEvent.ACTION_DOWN);
        UI.forceVibrate(item, false);
      }
    }
  }

  public void dispatchMenuTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        setCurrentMenuItem(e, findMenuItemByPosition(e, e.getX(), e.getY()));
        break;
      }
    }
    if (currentMenuItem != null) {
      e.offsetLocation(deltaX - currentMenuItem.getLeft(), deltaY - currentMenuItem.getTop());
      currentMenuItem.dispatchTouchEvent(e);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      closePreviewIfNeeded();
    }
    return true;
  }
}
