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
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.CancellationSignal;
import androidx.core.view.ViewCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetThemed;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.BubbleLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.reference.ReferenceList;

public class TooltipOverlayView extends ViewGroup {
  public interface LocationProvider {
    void getTargetBounds (View targetView, Rect outRect);
  }

  private static final float INNER_PADDING_LEFT = 8f;
  private static final float INNER_PADDING_RIGHT = 8f;
  private static final float INNER_PADDING_VERTICAL = 8f;

  private static final float ICON_SIZE = 24f;
  private static final float ICON_HORIZONTAL_MARGIN = 8f;
  private static final float INNER_PADDING_LEFT_WITH_ICON = 8f;
  private static final float INNER_PADDING_RIGHT_WITH_ICON = 10f;
  private static final float INNER_PADDING_VERTICAL_WITH_ICON = 11f;
  private static final float PIVOT_HEIGHT = 5f;
  private static final float PIVOT_WIDTH = 10f;
  private static final float BACKGROUND_RADIUS = 6f;

  public TooltipContentView newContent (Tdlib tdlib, CharSequence text, int textFlags) {
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text.toString(), TD.toEntities(text, false));
    return new TooltipContentViewText(this, tdlib, formattedText, textFlags, null);
  }

  private static abstract class TooltipContentView {
    protected final TooltipOverlayView parentView;

    protected TooltipContentView (TooltipOverlayView parentView) {
      this.parentView = parentView;
    }

    public abstract boolean layout (TooltipInfo info, int parentWidth, int parentHeight, int maxWidth);
    public abstract int getWidth ();
    public abstract int getHeight ();
    public abstract boolean onTouchEvent (TooltipInfo info, View view, MotionEvent e);
    public abstract void requestIcons (ComplexReceiver iconReceiver);
    public abstract void draw (Canvas c, ColorProvider colorProvider, int left, int top, int right, int bottom, float alpha, ComplexReceiver iconReceiver);
  }

  public interface ColorProvider extends TextColorSetThemed {
    default int tooltipColor () {
      return colorTheme().getColor(tooltipColorId());
    }
    default int tooltipColorId () {
      return R.id.theme_color_tooltip;
    }
    default int tooltipOutlineColor () {
      return colorTheme().getColor(tooltipOutlineColorId());
    }
    default int tooltipOutlineColorId () {
      return R.id.theme_color_tooltip_outline;
    }
    @Override
    default int defaultTextColorId () {
      return R.id.theme_color_tooltip_text;
    }

    @Override
    default int clickableTextColorId (boolean isPressed) {
      return R.id.theme_color_tooltip_textLink;
    }

    @Override
    default int pressedBackgroundColorId () {
      return R.id.theme_color_tooltip_textLinkPressHighlight;
    }
  }

  private static class TooltipContentViewTextWrapper extends TooltipContentView {
    private final TextWrapper text;

    public TooltipContentViewTextWrapper (TooltipOverlayView parentView, TextWrapper text) {
      super(parentView);
      this.text = text;
      this.text.setViewProvider(new SingleViewProvider(parentView));
    }

    @Override
    public boolean layout (TooltipInfo info, int parentWidth, int parentHeight, int maxWidth) {
      text.setTextMediaListener((wrapper, text, specificMedia) -> {
        if (!text.invalidateMediaContent(info.iconReceiver, specificMedia)) {
          text.requestMedia(info.iconReceiver);
        }
      });
      text.prepare(maxWidth);
      return true;
    }

    @Override
    public int getWidth () {
      return text.getWidth();
    }

    @Override
    public int getHeight () {
      return text.getHeight();
    }

    @Override
    public boolean onTouchEvent (TooltipInfo info, View view, MotionEvent e) {
      return text.onTouchEvent(view, e, info.clickCallback);
    }

    @Override
    public void requestIcons (ComplexReceiver iconReceiver) {
      text.requestMedia(iconReceiver);
    }

    @Override
    public void draw (Canvas c, ColorProvider colorProvider, int left, int top, int right, int bottom, float alpha, ComplexReceiver iconReceiver) {
      text.draw(c, left, right, 0, top, null, alpha, iconReceiver);
    }
  }

  private static class TooltipContentViewText extends TooltipContentView {
    private final Tdlib tdlib;
    private final TdApi.FormattedText formattedText;
    private final TdlibUi.UrlOpenParameters urlOpenParameters;
    private final int textFlags;
    private Text text;

    private TooltipContentViewText (TooltipOverlayView parentView, Tdlib tdlib, TdApi.FormattedText formattedText, int textFlags, TdlibUi.UrlOpenParameters urlOpenParameters) {
      super(parentView);
      this.tdlib = tdlib;
      this.formattedText = formattedText;
      this.textFlags = textFlags;
      this.urlOpenParameters = urlOpenParameters;
    }

    @Override
    public int getWidth () {
      return text != null ? text.getWidth() : 0;
    }

    @Override
    public int getHeight () {
      return text != null ? text.getHeight() : 0;
    }

    @Override
    public void requestIcons (ComplexReceiver iconReceiver) {
      if (text != null) {
        text.requestMedia(iconReceiver);
      } else {
        iconReceiver.clear();
      }
    }

    @Override
    public boolean layout (TooltipInfo info, int parentWidth, int parentHeight, int maxWidth) {
      if (text == null || text.getMaxWidth() != maxWidth) {
        text = new Text.Builder(tdlib, formattedText, urlOpenParameters, maxWidth, Paints.robotoStyleProvider(info.textSize).setAllowSp(info.allowSp), info.colorProvider, (text, specificMedia) -> {
          if (this.text == text) {
            if (!text.invalidateMediaContent(info.iconReceiver, specificMedia)) {
              text.requestMedia(info.iconReceiver);
            }
          }
        })
          .textFlags(Text.FLAG_CUSTOM_LONG_PRESS | textFlags)
          .viewProvider(new SingleViewProvider(parentView))
          .build();
        return true;
      }
      return false;
    }

    @Override
    public boolean onTouchEvent (TooltipInfo info, View view, MotionEvent e) {
      return text.onTouchEvent(view, e, info.clickCallback);
    }

    @Override
    public void draw (Canvas c, ColorProvider colorProvider, int left, int top, int right, int bottom, float alpha, ComplexReceiver iconReceiver) {
      if (text != null) {
        text.draw(c, left, right, 0, top, null, alpha, iconReceiver);
      }
    }
  }

  public interface VisibilityListener {
    void onVisibilityChanged (TooltipInfo tooltipInfo, float visibilityFactor);
    void onVisibilityChangeFinished (TooltipInfo tooltipInfo, boolean isVisible);
  }

  public static final int FLAG_INTERCEPT_TOUCH_EVENTS = 1;
  public static final int FLAG_PREVENT_HIDE_ON_TOUCH = 1 << 1;
  public static final int FLAG_FILL_WIDTH = 1 << 2;
  public static final int FLAG_NO_PIVOT = 1 << 3;
  public static final int FLAG_NEED_BLINK = 1 << 4;
  public static final int FLAG_HANDLE_BACK_PRESS = 1 << 5;
  public static final int FLAG_IGNORE_VIEW_SCALE = 1 << 6;

  public interface OffsetProvider {
    void onProvideOffset (RectF rect);
  }

  public static class TooltipInfo {
    private final TooltipOverlayView parentView;

    private final View originalView;
    private final ViewProvider viewProvider;

    @Nullable
    private final LocationProvider locationProvider;
    @NonNull
    private final ColorProvider colorProvider;
    @Nullable
    private final OffsetProvider offsetProvider;
    @Nullable
    private final ViewController<?> controller;
    private final float textSize;
    private final boolean allowSp;
    @Nullable
    private final Text.ClickCallback clickCallback;
    @Nullable
    private Drawable icon;
    @Nullable
    private final ImageFile previewFile, imageFile;
    @Nullable
    private final GifFile gifFile;
    private final float maxWidthDp;
    private final int flags;
    private TooltipContentView popupView;

    private final ComplexReceiver iconReceiver;
    @Nullable
    private final DoubleImageReceiver imageReceiver;
    @Nullable
    private final GifReceiver gifReceiver;

    private final int[] position = new int[2];
    private final Rect innerRect = new Rect();

    private ReferenceList<VisibilityListener> visibilityListeners;

    public boolean isVisible () {
      return isAttached && isVisible.getFloatValue() > 0f;
    }

    private final BoolAnimator isVisible = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        if (visibilityListeners != null) {
          for (VisibilityListener listener : visibilityListeners) {
            listener.onVisibilityChanged(TooltipInfo.this, factor);
          }
        }
        if (isAttached) {
          parentView.invalidate();
        }
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        if (visibilityListeners != null) {
          for (VisibilityListener listener : visibilityListeners) {
            listener.onVisibilityChangeFinished(TooltipInfo.this, finalFactor > 0f);
          }
        }
        if (finalFactor == 0f) {
          detach();
        }
      }
    }, AnimatorUtils.OVERSHOOT_INTERPOLATOR, BubbleLayout.REVEAL_DURATION);

    private TooltipInfo (TooltipOverlayView parent,
                         View originalView, ViewProvider viewProvider,
                         @Nullable LocationProvider locationProvider, @Nullable ColorProvider colorProvider, @Nullable OffsetProvider offsetProvider,
                         @Nullable ViewController<?> controller,
                         @Nullable Text.ClickCallback clickCallback, final float textSize, final boolean allowSp,
                         @DrawableRes int iconRes, @Nullable ImageFile previewFile, @Nullable ImageFile imageFile, @Nullable GifFile gifFile,
                         final float maxWidthDp, final int flags,
                         TooltipContentView view) {
      this.parentView = parent;
      this.originalView = originalView;
      this.viewProvider = viewProvider;
      this.locationProvider = locationProvider;
      this.colorProvider = colorProvider != null ? colorProvider : parent.defaultProvider;
      this.offsetProvider = offsetProvider;
      this.controller = controller;
      this.textSize = textSize;
      this.allowSp = allowSp;
      this.clickCallback = clickCallback;
      this.icon = Drawables.get(iconRes);
      this.previewFile = previewFile;
      this.imageFile = imageFile;
      this.gifFile = gifFile;
      this.imageReceiver = previewFile != null || imageFile != null ? new DoubleImageReceiver(parentView, 0) : null;
      this.gifReceiver = gifFile != null ? new GifReceiver(parentView) : null;
      this.iconReceiver = new ComplexReceiver(parentView);
      this.maxWidthDp = maxWidthDp;
      this.flags = flags;
      this.popupView = view;
    }

    @Nullable
    public String getContentText () {
      if (popupView instanceof TooltipContentViewText) {
        return ((TooltipContentViewText) popupView).formattedText.text;
      }
      return null;
    }

    public void reset (TooltipContentView popupView, @DrawableRes int iconRes) {
      this.popupView = popupView;
      this.icon = Drawables.get(iconRes);
      layout(parentView.getMeasuredWidth(), parentView.getMeasuredHeight());
      parentView.invalidate();
    }

    public void reset (Tdlib tdlib, CharSequence text, @DrawableRes int iconRes) {
      reset(parentView.newContent(tdlib, text, 0), iconRes);
    }

    public TooltipInfo addListener (VisibilityListener listener) {
      if (visibilityListeners == null) {
        visibilityListeners = new ReferenceList<>();
      }
      visibilityListeners.add(listener);
      return this;
    }

    public TooltipInfo removeListener (VisibilityListener listener) {
      if (visibilityListeners != null) {
        visibilityListeners.remove(listener);
      }
      return this;
    }

    private View findView () {
      if (viewProvider != null) {
        if (originalView != null && viewProvider.belongsToProvider(originalView))
          return originalView;
        View view = viewProvider.findAnyTarget();
        if (view != null)
          return view;
      }
      return originalView;
    }

    private CancellableRunnable delayedHide;

    public boolean wontHideDelayed () {
      return isVisible.getValue() && (delayedHide == null || !delayedHide.isPending());
    }

    private void cancelDelayedHide () {
      shallBeHidden = false;
      if (delayedHide != null) {
        delayedHide.cancel();
        delayedHide = null;
      }
    }

    private boolean shallBeHidden;

    public boolean isInside (float x, float y) {
      return x >= contentRect.left && x < contentRect.right && y >= contentRect.top && y < contentRect.bottom;
    }

    public void hideNow () {
      hide(true);
    }

    public void hide (boolean force) {
      cancelDelayedShow();
      if (force || delayedHide == null) {
        cancelDelayedHide();
        setIsVisible(false, true);
      } else {
        shallBeHidden = true;
      }
    }

    private CancellationSignal delayedShowCanceled;

    private void cancelDelayedShow () {
      if (delayedShowCanceled != null) {
        delayedShowCanceled.cancel();
        delayedShowCanceled = null;
      }
    }

    public void show () {
      cancelDelayedHide();
      cancelDelayedShow();
      if (controller == null || controller.isFocused() || isVisible.getFloatValue() > 0f) {
        setIsVisible(true, true);
      } else {
        CancellationSignal signal = new CancellationSignal();
        this.delayedShowCanceled = signal;
        controller.addOneShotFocusListener(() -> {
          if (!signal.isCanceled()) {
            setIsVisible(true, true);
            if (delayedShowCanceled == signal)
              delayedShowCanceled = null;
          }
        });
      }
    }

    public TooltipInfo hideDelayed () {
      return hideDelayed(true);
    }

    public TooltipInfo hideDelayed (long duration, TimeUnit unit) {
      return hideDelayed(true, duration, unit);
    }

    public TooltipInfo hideDelayed (boolean shallBeHidden) {
      return hideDelayed(shallBeHidden, 2500, TimeUnit.MILLISECONDS);
    }

    public TooltipInfo hideDelayed (boolean shallBeHidden, long duration, TimeUnit unit) {
      cancelDelayedHide();
      this.shallBeHidden = shallBeHidden;
      delayedHide = new CancellableRunnable() {
        @Override
        public void act () {
          hide(true);
        }
      };
      delayedHide.removeOnCancel(UI.getAppHandler());
      UI.post(delayedHide, unit.toMillis(duration));
      return this;
    }

    private boolean isAttached;

    private void attach () {
      if (!isAttached) {
        isAttached = true;
        parentView.addHint(this);
        popupView.requestIcons(iconReceiver);
        iconReceiver.attach();
        if (imageReceiver != null) {
          imageReceiver.requestFile(previewFile, imageFile);
          imageReceiver.attach();
        }
        if (gifReceiver != null) {
          gifReceiver.requestFile(gifFile);
          gifReceiver.attach();
        }
      }
    }

    public void destroy () {
      cancelDelayedHide();
      detach();
      setIsVisible(false, false);
      iconReceiver.performDestroy();
      if (imageReceiver != null)
        imageReceiver.destroy();
      if (gifReceiver != null)
        gifReceiver.destroy();
    }

    private void detach () {
      if (isAttached) {
        isAttached = false;
        parentView.removeHint(this);
        iconReceiver.detach();
        if (imageReceiver != null)
          imageReceiver.detach();
        if (gifReceiver != null)
          gifReceiver.detach();
      }
    }

    private final Destroyable destroyable = this::hideNow;
    private final ViewController.FocusStateListener focusStateListener = (c, isFocused) -> {
      if (!isFocused)
        hideNow();
    };

    private long lastVisibleTime;

    private List<RunnableLong> closeCallbacks;

    public TooltipInfo addOnCloseListener (RunnableLong listener) {
      if (closeCallbacks == null)
        closeCallbacks = new ArrayList<>();
      closeCallbacks.add(listener);
      return this;
    }

    private void setIsVisible (boolean isVisible, boolean animated) {
      if (this.isVisible.getValue() != isVisible) {
        if (isVisible && this.isVisible.getFloatValue() == 0f && !BitwiseUtils.getFlag(flags, FLAG_NO_PIVOT)) {
          this.isVisible.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          this.isVisible.setDuration(BubbleLayout.REVEAL_DURATION);
        } else {
          this.isVisible.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          this.isVisible.setDuration(BubbleLayout.DISMISS_DURATION);
        }
        if (controller != null) {
          if (isVisible) {
            controller.addDestroyListener(destroyable);
            controller.addFocusListener(focusStateListener);
          } else {
            controller.removeDestroyListener(destroyable);
            controller.removeFocusListener(focusStateListener);
          }
        }
        if (isVisible) {
          lastVisibleTime = SystemClock.uptimeMillis();
        } else {
          long duration = SystemClock.uptimeMillis() - lastVisibleTime;
          if (closeCallbacks != null) {
            for (RunnableLong callback : closeCallbacks) {
              callback.runWithLong(duration);
            }
          }
        }
      }
      if (isVisible) {
        attach();
      }
      this.isVisible.setValue(isVisible, animated);
    }

    public boolean onTouchEvent (View v, MotionEvent e) {
      boolean hasIcon = hasIcon();
      int innerPaddingLeft = Screen.dp(hasIcon ? INNER_PADDING_LEFT_WITH_ICON : INNER_PADDING_LEFT);
      int innerPaddingVertical = Screen.dp(hasIcon ? INNER_PADDING_VERTICAL_WITH_ICON : INNER_PADDING_VERTICAL);
      int iconOffset = hasIcon ? Screen.dp(ICON_SIZE) + Screen.dp(ICON_HORIZONTAL_MARGIN) : 0;
      return popupView.onTouchEvent(this, v, e);
    }

    private boolean alignBottom;
    private int pivotX, pivotY;
    private final RectF contentRect = new RectF();
    private final Path backgroundPath = new Path();

    private void buildPath (Path backgroundPath, RectF contentRect) {
      int pivotWidth = Screen.dp(PIVOT_WIDTH);
      int backgroundRadius = Screen.dp(BACKGROUND_RADIUS);
      int pivotHeight = Screen.dp(PIVOT_HEIGHT);

      backgroundPath.reset();
      backgroundPath.setFillType(Path.FillType.EVEN_ODD);
      RectF rectF = Paints.getRectF();
      if (BitwiseUtils.getFlag(flags, FLAG_NO_PIVOT)) {
        backgroundPath.addRoundRect(contentRect, backgroundRadius, backgroundRadius, Path.Direction.CW);
      } else if (alignBottom) {
        backgroundPath.moveTo(contentRect.left, contentRect.top + backgroundRadius);

        rectF.set(contentRect.left, contentRect.top, contentRect.left + backgroundRadius * 2, contentRect.top + backgroundRadius * 2);
        backgroundPath.arcTo(rectF, -180, 90);

        backgroundPath.lineTo(pivotX - pivotWidth / 2f, contentRect.top);
        backgroundPath.rLineTo(pivotWidth / 2f, -pivotHeight);
        backgroundPath.rLineTo(pivotWidth / 2f, pivotHeight);
        backgroundPath.lineTo(contentRect.right - backgroundRadius, contentRect.top);

        rectF.set(contentRect.right - backgroundRadius * 2, contentRect.top, contentRect.right, contentRect.top + backgroundRadius * 2f);
        backgroundPath.arcTo(rectF, -90, 90);
        backgroundPath.lineTo(contentRect.right, contentRect.bottom - backgroundRadius);

        rectF.set(contentRect.right - backgroundRadius * 2, contentRect.bottom - backgroundRadius * 2, contentRect.right, contentRect.bottom);
        backgroundPath.arcTo(rectF, 0, 90);
        backgroundPath.lineTo(contentRect.left + backgroundRadius * 2, contentRect.bottom);

        rectF.set(contentRect.left, contentRect.bottom - backgroundRadius * 2, contentRect.left + backgroundRadius * 2, contentRect.bottom);
        backgroundPath.arcTo(rectF, 90, 90);
        backgroundPath.lineTo(contentRect.left, contentRect.top + backgroundRadius);
      } else {
        backgroundPath.moveTo(contentRect.left + backgroundRadius, contentRect.bottom);

        // Start from bottom-left corner
        backgroundPath.lineTo(pivotX - pivotWidth / 2f, contentRect.bottom);
        backgroundPath.rLineTo(pivotWidth / 2f, pivotHeight);
        backgroundPath.rLineTo(pivotWidth / 2f, -pivotHeight);
        backgroundPath.lineTo(contentRect.right - backgroundRadius, contentRect.bottom);

        // Move from bottom to right side
        rectF.set(contentRect.right - backgroundRadius * 2, contentRect.bottom - backgroundRadius * 2, contentRect.right, contentRect.bottom);
        backgroundPath.arcTo(rectF, 90, -90);
        backgroundPath.lineTo(contentRect.right, contentRect.top + backgroundRadius);

        // Move from right to top
        rectF.set(contentRect.right - backgroundRadius * 2, contentRect.top, contentRect.right, contentRect.top + backgroundRadius * 2);
        backgroundPath.arcTo(rectF, 0, -90);
        backgroundPath.lineTo(contentRect.left + backgroundRadius, contentRect.top);

        // Move from top to left
        rectF.set(contentRect.left, contentRect.top, contentRect.left + backgroundRadius * 2, contentRect.top + backgroundRadius * 2);
        backgroundPath.arcTo(rectF, -90, -90);
        backgroundPath.lineTo(contentRect.left, contentRect.bottom - backgroundRadius);

        // Move from left to bottom. The end
        rectF.set(contentRect.left, contentRect.bottom - backgroundRadius * 2, contentRect.left + backgroundRadius * 2, contentRect.bottom);
        backgroundPath.arcTo(rectF, -180, -90);
      }
      backgroundPath.close();
    }

    public void reposition () {
      if (layout(parentView.getMeasuredWidth(), parentView.getMeasuredHeight()) && isVisible.getFloatValue() > 0f) {
        parentView.invalidate();
      }
    }

    private View attachedToView;
    private boolean viewAttachedToWindow;

    private final OnLayoutChangeListener onLayoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (v.getVisibility() == View.GONE) {
        hideNow();
      } else {
        reposition();
      }
    };
    private final BaseView.TranslationChangeListener onTranslationChangeListener = (view, x, y) -> reposition();
    private final OnAttachStateChangeListener onAttachStateChangeListener = new OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) {
        onViewAttachDetach(v, true);
      }

      @Override
      public void onViewDetachedFromWindow (View v) {
        onViewAttachDetach(v, false);
      }
    };
    private void onViewAttachDetach (View v, boolean isAttached) {
      if (this.attachedToView == v && this.viewAttachedToWindow != isAttached) {
        this.viewAttachedToWindow = isAttached;
        if (isAttached) {
          hideNow();
        }
      }
    }

    private void attachListeners (View view) {
      if (this.attachedToView != view) {
        if (this.attachedToView != null) {
          this.attachedToView.removeOnLayoutChangeListener(onLayoutChangeListener);
          this.attachedToView.removeOnAttachStateChangeListener(onAttachStateChangeListener);
          if (attachedToView instanceof BaseView) {
            ((BaseView) attachedToView).removeOnTranslationChangeListener(onTranslationChangeListener);
          }
        }
        this.attachedToView = view;
        if (view != null) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            this.viewAttachedToWindow = view.isAttachedToWindow();
          } else {
            this.viewAttachedToWindow = true;
          }
          view.addOnLayoutChangeListener(onLayoutChangeListener);
          view.addOnAttachStateChangeListener(onAttachStateChangeListener);
          if (view instanceof BaseView) {
            ((BaseView) view).addOnTranslationChangeListener(onTranslationChangeListener);
          }
        }
      }
    }

    private boolean hasIcon () {
      return this.icon != null || this.previewFile != null || this.imageFile != null || this.gifFile != null;
    }

    public boolean layout (int parentWidth, int parentHeight) {
      final View view = findView();
      final int prevPivotX = this.pivotX;
      final int prevPivotY = this.pivotY;
      final boolean prevAlignBottom = this.alignBottom;
      if (view != null) {
        view.getLocationOnScreen(position);
        position[0] -= parentView.position[0];
        position[1] -= parentView.position[1];
      } else {
        position[0] = position[1] = 0;
      }
      Rect prevInnerRect = Paints.getRect();
      prevInnerRect.set(innerRect);
      RectF prevContentRect = Paints.getRectF();
      prevContentRect.set(contentRect);
      if (view != null) {
        float scaleX = view.getScaleX();
        float scaleY = view.getScaleY();
        float pivotX = view.getPivotX();
        float pivotY = view.getPivotY();
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        innerRect.set(0, 0, width, height);
        if (scaleX != 1f || scaleY != 1f) {
          int scaledWidth = (int) ((float) width * scaleX);
          int scaledHeight = (int) ((float) height * scaleY);
          int diffX = (int) ((float) (width - scaledWidth) * (pivotX / (float) width));
          int diffY = (int) ((float) (height - scaledHeight) * (pivotY / (float) height));
          if (!BitwiseUtils.getFlag(flags, FLAG_IGNORE_VIEW_SCALE)) {
            innerRect.set(0, 0, scaledWidth, scaledHeight);
            innerRect.offset(diffX, diffY);
          }
          position[0] -= diffX;
          position[1] -= diffY;
        }
      } else {
        innerRect.set(0, 0, 0, 0);
      }
      if (locationProvider != null) {
        locationProvider.getTargetBounds(view, innerRect);
      } else if (view instanceof LocationProvider) {
        ((LocationProvider) view).getTargetBounds(view, innerRect);
      }

      boolean hasIcon = hasIcon();
      int innerPaddingLeft = Screen.dp(hasIcon ? INNER_PADDING_LEFT_WITH_ICON : INNER_PADDING_LEFT);
      int innerPaddingRight = Screen.dp(hasIcon ? INNER_PADDING_RIGHT_WITH_ICON : INNER_PADDING_RIGHT);
      int innerPaddingVertical = Screen.dp(hasIcon ? INNER_PADDING_VERTICAL_WITH_ICON : INNER_PADDING_VERTICAL);
      int verticalOffset = 0;
      int horizontalMargin = Screen.dp(8f);
      int iconOffset = hasIcon ? Screen.dp(ICON_SIZE) + Screen.dp(ICON_HORIZONTAL_MARGIN) : 0;
      boolean fillWidth = BitwiseUtils.getFlag(flags, FLAG_FILL_WIDTH);

      int maxWidth = Math.min(parentWidth - horizontalMargin * 2, parentHeight - horizontalMargin * 2);
      if (!fillWidth && this.maxWidthDp > 0) {
        maxWidth = Math.min(Screen.dp(this.maxWidthDp), maxWidth);
      }
      boolean textChanged = popupView.layout(this, parentWidth, parentHeight, maxWidth - innerPaddingLeft - innerPaddingRight - iconOffset);

      int triangleHeight = Screen.dp(12f);
      int totalHeight = popupView.getHeight() + innerPaddingVertical * 2 + triangleHeight;

      this.alignBottom = position[1] + innerRect.top - totalHeight < HeaderView.getTopOffset() && position[1] + innerRect.bottom + totalHeight < parentHeight;
      this.pivotX = position[0] + innerRect.centerX();
      this.pivotY = alignBottom ? position[1] + innerRect.bottom : position[1] + innerRect.top;

      int pivotHeight = BitwiseUtils.getFlag(flags, FLAG_NO_PIVOT) ? horizontalMargin : Screen.dp(PIVOT_HEIGHT);

      int contentWidth = innerPaddingLeft + innerPaddingRight + popupView.getWidth() + iconOffset;
      int contentHeight = innerPaddingVertical * 2 + popupView.getHeight();

      if (hasIcon) {
        contentWidth = Math.max(contentWidth, innerPaddingLeft * 2 + Screen.dp(ICON_SIZE));
        contentHeight = Math.max(contentHeight, innerPaddingVertical * 2 + Screen.dp(ICON_SIZE));
      }

      if (fillWidth) {
        contentRect.set(pivotX - maxWidth / 2f, 0, pivotX + maxWidth / 2f, contentHeight);
      } else {
        contentRect.set(
          pivotX - contentWidth / 2f,
          0,
          pivotX + contentWidth / 2f,
          contentHeight
        );
      }
      if (alignBottom) {
        contentRect.offset(0, pivotY + pivotHeight + verticalOffset);
      } else {
        contentRect.offset(0, pivotY - pivotHeight - contentHeight - verticalOffset);
      }
      contentRect.offset(Math.max(0, horizontalMargin - contentRect.left), 0);
      contentRect.offset(Math.min(0, (parentWidth - horizontalMargin) - contentRect.right), 0);
      int checkBound = Screen.dp(PIVOT_WIDTH) / 2 + Screen.dp(BACKGROUND_RADIUS);
      if (pivotX - checkBound < contentRect.left) {
        contentRect.offset((pivotX - checkBound) - contentRect.left, 0);
      } else if (pivotX + checkBound > contentRect.right) {
        contentRect.offset((pivotX + checkBound) - contentRect.right, 0);
      }

      if (offsetProvider != null) {
        offsetProvider.onProvideOffset(contentRect);
      }

      attachListeners(view);

      if (textChanged || this.pivotX != prevPivotX || this.pivotY != prevPivotY || this.alignBottom != prevAlignBottom || !this.contentRect.equals(prevContentRect)) {
        buildPath(backgroundPath, contentRect);
        return true;
      }

      return false;
    }

    public void draw (Canvas c) {
      float factor = this.isVisible.getFloatValue();
      float alpha = MathUtils.clamp(factor);
      float scale = .8f + .2f * factor;

      Rect rect = Paints.getRect();
      rect.left = pivotX - popupView.getWidth() / 2;
      rect.right = rect.left + popupView.getWidth();

      final boolean needSave = scale != 1f;
      final int saveCount;
      if (needSave) {
        saveCount = c.save();
        if (BitwiseUtils.getFlag(flags, FLAG_NO_PIVOT)) {
          c.scale(scale, scale, contentRect.centerX(), contentRect.centerY());
        } else {
          c.scale(scale, scale, pivotX, pivotY);
        }
      } else {
        saveCount = -1;
      }
      int outlineColor = ColorUtils.alphaColor(alpha, colorProvider.tooltipOutlineColor());
      if (Color.alpha(outlineColor) > 0) {
        parentView.paint.setStyle(Paint.Style.STROKE);
        parentView.paint.setStrokeWidth(Screen.dp(2f));
        parentView.paint.setColor(outlineColor);
        c.drawPath(backgroundPath, parentView.paint);
        parentView.paint.setStyle(Paint.Style.FILL);
      }
      parentView.paint.setColor(ColorUtils.alphaColor(alpha, colorProvider.tooltipColor()));
      c.drawPath(backgroundPath, parentView.paint);

      boolean hasIcon = hasIcon();

      int innerPaddingLeft, innerPaddingRight, innerPaddingVertical, iconOffset, verticalOffset;

      if (hasIcon) {
        innerPaddingLeft = Screen.dp(INNER_PADDING_LEFT_WITH_ICON);
        innerPaddingRight = Screen.dp(INNER_PADDING_RIGHT_WITH_ICON);
        innerPaddingVertical = Screen.dp(INNER_PADDING_VERTICAL_WITH_ICON);
        iconOffset = Screen.dp(ICON_SIZE) + Screen.dp(ICON_HORIZONTAL_MARGIN);

        verticalOffset = Math.max(0, Screen.dp(ICON_SIZE) / 2 - popupView.getHeight() / 2);

        int iconSize = Screen.dp(ICON_SIZE);
        int iconLeft = (int) (contentRect.left + innerPaddingLeft);
        int iconTop = (int) (contentRect.top + innerPaddingVertical);
        int iconCenterX = iconLeft + iconSize / 2;
        int iconCenterY = iconTop + iconSize / 2;
        int imageSize = iconSize + Math.min(innerPaddingLeft, Screen.dp(ICON_HORIZONTAL_MARGIN)) / 2;

        if (imageReceiver != null && (gifReceiver == null || gifReceiver.needPlaceholder())) {
          imageReceiver.setBounds(iconCenterX - imageSize / 2, iconCenterY - imageSize / 2, iconCenterX + imageSize / 2, iconCenterY + imageSize / 2);
          imageReceiver.setPaintAlpha(alpha);
          imageReceiver.draw(c);
          imageReceiver.restorePaintAlpha();
        }
        if (gifReceiver != null) {
          gifReceiver.setBounds(iconCenterX - imageSize / 2, iconCenterY - imageSize / 2, iconCenterX + imageSize / 2, iconCenterY + imageSize / 2);
          gifReceiver.setAlpha(alpha);
          gifReceiver.draw(c);
        }
        if (icon != null) {
          Drawables.draw(c, icon, iconLeft, iconTop, Paints.getPorterDuffPaint(ColorUtils.alphaColor(alpha, colorProvider.defaultTextColor())));
        }
        if (BitwiseUtils.getFlag(flags, FLAG_NEED_BLINK)) {
          // TODO
        }
      } else {
        innerPaddingLeft = Screen.dp(INNER_PADDING_LEFT);
        innerPaddingRight = Screen.dp(INNER_PADDING_RIGHT);
        innerPaddingVertical = Screen.dp(INNER_PADDING_VERTICAL);
        iconOffset = verticalOffset = 0;
      }
      popupView.draw(c, colorProvider, (int) (contentRect.left + innerPaddingLeft + iconOffset), (int) (contentRect.top + innerPaddingVertical + verticalOffset), (int) (contentRect.right - innerPaddingRight), (int) (contentRect.bottom - innerPaddingVertical), alpha, iconReceiver);
      if (needSave) {
        c.restoreToCount(saveCount);
      }
    }
  }

  public interface AvailabilityListener {
    void onAvailabilityChanged (TooltipOverlayView view, boolean hasChildren);
  }

  private AvailabilityListener availabilityListener;
  private final int[] position = new int[2];
  private final ColorProvider defaultProvider = new ColorProvider() { };

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

  public TooltipOverlayView (Context context) {
    super(context);
    paint.setStyle(Paint.Style.FILL);
    setWillNotDraw(true);
  }

  public ColorProvider overrideColorProvider (@Nullable ThemeDelegate forcedTheme) {
    if (forcedTheme == null)
      return null;
    return new ColorProvider() {
      @Override
      public ThemeDelegate forcedTheme () {
        return forcedTheme;
      }
    };
  }

  public ColorProvider newFillingColorProvider (@Nullable ThemeDelegate forcedTheme) {
    if (forcedTheme == null)
      return null;
    return new ColorProvider() {
      @Override
      public ThemeDelegate forcedTheme () {
        return forcedTheme;
      }

      @Override
      public int tooltipColorId () {
        return R.id.theme_color_filling;
      }

      @Override
      public int tooltipOutlineColorId () {
        return R.id.theme_color_separator;
      }

      @Override
      public int defaultTextColorId () {
        return R.id.theme_color_text;
      }

      @Override
      public int clickableTextColorId (boolean isPressed) {
        return R.id.theme_color_textLink;
      }

      @Override
      public int pressedBackgroundColorId () {
        return R.id.theme_color_textLinkPressHighlight;
      }
    };
  }

  public void setAvailabilityListener (AvailabilityListener availabilityListener) {
    this.availabilityListener = availabilityListener;
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    getLocationOnScreen(position);
  }

  public static class TooltipBuilder implements Cloneable {
    private final TooltipOverlayView parentView;

    private View originalView;
    private ViewProvider viewProvider;
    private LocationProvider locationProvider;
    private ColorProvider colorProvider;
    private OffsetProvider offsetProvider;
    private Text.ClickCallback clickCallback;
    private ViewController<?> controller;
    private float textSize = 13f;
    private boolean allowSp = true;
    private int flags;
    private int iconRes;
    private ImageFile previewFile, imageFile;
    private GifFile gifFile;
    private float maxWidthDp = 320f;
    @Nullable
    private TdlibUi.UrlOpenParameters urlOpenParameters;
    private List<RunnableData<TooltipInfo>> onBuild;

    private TooltipBuilder (TooltipOverlayView parentView) {
      this.parentView = parentView;
    }

    public TooltipBuilder (TooltipBuilder clone) {
      this.parentView = clone.parentView;
      this.originalView = clone.originalView;
      this.viewProvider = clone.viewProvider;
      this.locationProvider = clone.locationProvider;
      this.colorProvider = clone.colorProvider;
      this.offsetProvider = clone.offsetProvider;
      this.clickCallback = clone.clickCallback;
      this.controller = clone.controller;
      this.textSize = clone.textSize;
      this.allowSp = clone.allowSp;
      this.flags = clone.flags;
      this.iconRes = clone.iconRes;
      this.previewFile = clone.previewFile;
      this.imageFile = clone.imageFile;
      this.gifFile = clone.gifFile;
      this.maxWidthDp = clone.maxWidthDp;
      this.urlOpenParameters = clone.urlOpenParameters;
      this.onBuild = clone.onBuild != null ? new ArrayList<>(clone.onBuild) : null;
    }

    public TooltipBuilder anchor (View originalView) {
      this.originalView = originalView;
      return this;
    }

    public TooltipBuilder anchor (ViewProvider provider) {
      this.viewProvider = provider;
      return this;
    }

    public TooltipBuilder anchor (View originalView, ViewProvider viewProvider) {
      this.originalView = originalView;
      this.viewProvider = viewProvider;
      return this;
    }

    public TooltipBuilder onBuild (RunnableData<TooltipInfo> act) {
      if (onBuild == null)
        onBuild = new ArrayList<>();
      onBuild.add(act);
      return this;
    }

    public ColorProvider colorProvider () {
      return colorProvider != null ? colorProvider : new ColorProvider() {};
    }

    public TooltipBuilder locate (LocationProvider locationProvider) {
      this.locationProvider = locationProvider;
      return this;
    }

    public TooltipBuilder maxWidth (float dp) {
      this.maxWidthDp = dp;
      return this;
    }

    public TooltipBuilder noMaxWidth () {
      return maxWidth(0f);
    }

    public TooltipBuilder color (ColorProvider colorProvider) {
      this.colorProvider = colorProvider;
      return this;
    }

    public TooltipBuilder click (Text.ClickCallback clickCallback) {
      this.clickCallback = clickCallback;
      return this;
    }

    public TooltipBuilder controller (ViewController<?> controller) {
      this.controller = controller;
      return this;
    }

    public boolean hasController () {
      return this.controller != null;
    }

    public boolean hasVisibleTarget () {
      if (viewProvider != null) {
        View view = viewProvider.findAnyTarget();
        return view != null && ViewCompat.isAttachedToWindow(view);
      }
      if (controller != null) {
        return controller.isFocused();
      }
      if (originalView != null) {
        return ViewCompat.isAttachedToWindow(originalView);
      }
      return false;
    }

    public TooltipBuilder textSize (float textSize, boolean allowSp) {
      this.textSize = Math.max(13f, textSize);
      this.allowSp = allowSp;
      return this;
    }

    public TooltipBuilder chatTextSize (float diff) {
      return textSize(Settings.instance().getChatFontSize() + diff, Settings.instance().needChatFontSizeScaling());
    }

    public TooltipBuilder chatTextSize () {
      return chatTextSize(-1f);
    }

    public TooltipBuilder icon (@DrawableRes int iconRes) {
      this.iconRes = iconRes;
      return this;
    }

    public TooltipBuilder gif (@Nullable GifFile gif, @Nullable ImageFile preview) {
      this.gifFile = gif;
      this.previewFile = preview;
      return this;
    }

    public TooltipBuilder image (@Nullable ImageFile image, @Nullable ImageFile preview) {
      this.imageFile = image;
      this.previewFile = preview;
      return this;
    }

    public TooltipBuilder offset (OffsetProvider offsetProvider) {
      this.offsetProvider = offsetProvider;
      return this;
    }

    public TooltipBuilder interceptTouchEvents (boolean intercept) {
      return flag(FLAG_INTERCEPT_TOUCH_EVENTS, intercept);
    }

    public TooltipBuilder preventHideOnTouch (boolean preventHideOnTouch) {
      return flag(FLAG_PREVENT_HIDE_ON_TOUCH, preventHideOnTouch);
    }

    public TooltipBuilder fillWidth (boolean fillWidth) {
      return flag(FLAG_FILL_WIDTH, fillWidth);
    }

    public TooltipBuilder noPivot (boolean noPivot) {
      return flag(FLAG_NO_PIVOT, noPivot);
    }

    public TooltipBuilder needBlink (boolean needBlink) {
      return flag(FLAG_NEED_BLINK, needBlink);
    }

    public TooltipBuilder handleBackPress (boolean handleBackPress) {
      return flag(FLAG_HANDLE_BACK_PRESS, handleBackPress);
    }

    public TooltipBuilder ignoreViewScale (boolean ignoreViewScale) {
      return flag(FLAG_IGNORE_VIEW_SCALE, ignoreViewScale);
    }

    public TooltipBuilder flag (int flag, boolean enabled) {
      this.flags = BitwiseUtils.setFlag(flags, flag, enabled);
      return this;
    }

    public TooltipBuilder source (TdlibUi.UrlOpenParameters openParameters) {
      this.urlOpenParameters = openParameters;
      return this;
    }

    public void show (ViewController<?> controller, Tdlib tdlib, int iconRes, CharSequence text) {
      if (originalView == null && viewProvider == null && locationProvider == null) {
        UI.showToast(text, Toast.LENGTH_SHORT);
      } else {
        icon(iconRes).needBlink(iconRes == R.drawable.baseline_info_24 || iconRes == R.drawable.baseline_error_24).controller(controller != null ? controller.getParentOrSelf() : null).show(tdlib, text).hideDelayed(3500, TimeUnit.MILLISECONDS);
      }
    }

    public TooltipInfo show (Tdlib tdlib, TdApi.FormattedText text) {
      return show(new TooltipContentViewText(parentView, tdlib, text, 0, urlOpenParameters));
    }

    public TooltipInfo show (Tdlib tdlib, int stringRes) {
      return show(tdlib, new TdApi.FormattedText(Lang.getString(stringRes), null));
    }

    public TooltipInfo show (Tdlib tdlib, CharSequence text) {
      return show(tdlib, new TdApi.FormattedText(text.toString(), TD.toEntities(text, false)));
    }

    public TooltipInfo show (TextWrapper textWrapper) {
      return show(new TooltipContentViewTextWrapper(parentView, textWrapper));
    }

    public TooltipInfo show (TooltipContentView view) {
      TooltipInfo info = new TooltipInfo(parentView, originalView, viewProvider, locationProvider, colorProvider, offsetProvider, controller, clickCallback, textSize, allowSp, iconRes, previewFile, imageFile, gifFile, maxWidthDp, flags, view);
      if (onBuild != null) {
        for (RunnableData<TooltipInfo> act : onBuild) {
          act.runWithData(info);
        }
      }
      info.attach();
      return info;
    }
  }

  public TooltipBuilder builder (View view) {
    return new TooltipBuilder(this).anchor(view);
  }

  public TooltipBuilder builder (ViewProvider viewProvider) {
    return new TooltipBuilder(this).anchor(viewProvider);
  }

  public TooltipBuilder builder (View view, ViewProvider viewProvider) {
    return new TooltipBuilder(this).anchor(view, viewProvider);
  }

  public void hideAll (boolean force) {
    for (int i = activePopups.size() - 1; i >= 0; i--) {
      activePopups.get(i).hide(force);
    }
  }

  public boolean onBackPressed () {
    for (int i = activePopups.size() - 1; i >= 0; i--) {
      TooltipInfo info = activePopups.get(i);
      boolean handled = info.wontHideDelayed();
      info.hide(true);
      if (handled || BitwiseUtils.getFlag(info.flags, FLAG_HANDLE_BACK_PRESS)) {
        return true;
      }
    }
    return false;
  }

  public void reposition () {
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    if (viewWidth > 0 && viewHeight > 0) {
      boolean invalidate = false;
      for (TooltipInfo popup : activePopups) {
        invalidate = popup.layout(viewWidth, viewHeight) || invalidate;
      }
      if (invalidate) {
        invalidate();
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    reposition();
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    reposition();
  }

  private final List<TooltipInfo> activePopups = new ArrayList<>();

  private void removeHint (TooltipInfo tooltipInfo) {
    if (activePopups.remove(tooltipInfo) && activePopups.isEmpty()) {
      setWillNotDraw(true);
      if (availabilityListener != null) {
        availabilityListener.onAvailabilityChanged(this, false);
      }
    }
  }

  private void addHint (TooltipInfo info) {
    for (int i = activePopups.size() - 1; i >= 0; i--) {
      TooltipInfo tooltipInfo = activePopups.get(i);
      if (tooltipInfo.delayedHide != null /*&& tooltipInfo.shallBeHidden*/) {
        tooltipInfo.hideNow();
      }
    }
    getLocationOnScreen(position);
    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
      info.layout(getMeasuredWidth(), getMeasuredHeight());
    }
    this.activePopups.add(info);
    if (this.activePopups.size() == 1) {
      setWillNotDraw(false);
      addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow (View v) {
          info.show();
          removeOnAttachStateChangeListener(this);
        }

        @Override
        public void onViewDetachedFromWindow (View v) { }
      });
      if (availabilityListener != null) {
        availabilityListener.onAvailabilityChanged(this, true);
      }
    } else {
      info.show();
    }
  }

  private TooltipInfo touchingInfo;
  private boolean waitUp;

  @SuppressWarnings("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchingInfo = null;
        waitUp = false;
        for (int i = activePopups.size() - 1; i >= 0; i--) {
          TooltipInfo info = activePopups.get(i);
          if (touchingInfo == null && info.onTouchEvent(this, e)) {
            touchingInfo = activePopups.get(i);
          } else if (BitwiseUtils.getFlag(info.flags, FLAG_INTERCEPT_TOUCH_EVENTS) && info.isInside(e.getX(), e.getY())) {
            waitUp = true;
          } else if (!BitwiseUtils.getFlag(info.flags, FLAG_PREVENT_HIDE_ON_TOUCH)) {
            info.hide(info.shallBeHidden);
          }
        }
        return touchingInfo != null || waitUp;
      }
      case MotionEvent.ACTION_CANCEL: {
        boolean value = touchingInfo != null && touchingInfo.onTouchEvent(this, e);
        hideAll(true);
        return value;
      }
    }
    return (touchingInfo != null && touchingInfo.onTouchEvent(this, e)) || waitUp;
  }

  @Override
  protected void onDraw (Canvas c) {
    for (TooltipInfo info : activePopups) {
      info.draw(c);
    }
  }
}
