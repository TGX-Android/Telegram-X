package org.thunderdog.challegram.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.ChatId;

/**
 * Date: 01/03/2017
 * Author: default
 */

public class ForceTouchView extends FrameLayoutFix implements
  PopupLayout.AnimatedPopupProvider, FactorAnimator.Target,
  ChatListener, NotificationSettingsListener, TdlibCache.UserDataChangeListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, ThemeChangeListener, TdlibCache.UserStatusChangeListener {
  private ForceTouchContext forceTouchContext;
  private RelativeLayout contentWrap;
  private View backgroundView;

  public interface StateListener {
    void onPrepareToExitForceTouch (ForceTouchContext context);
    void onPrepareToEnterForceTouch (ForceTouchContext context);
    void onCompletelyShownForceTouch (ForceTouchContext context);
    void onDestroyForceTouch (ForceTouchContext context);
  }

  public interface ActionListener {
    void onForceTouchAction (ForceTouchContext context, int actionId, Object arg);
    void onAfterForceTouchAction (ForceTouchContext context, int actionId, Object arg);
  }

  public interface MaximizeListener {
    boolean onPerformMaximize (FactorAnimator target, float animateToWhenReady, Object arg);
  }

  public interface PreviewDelegate {
    void onPrepareForceTouchContext (ForceTouchContext context);
  }

  public static final float FOOTER_HEIGHT = 48f; // 56f
  public static final float HEADER_HEIGHT = 56f;

  // private static TextPaint hintTextPaint;

  private final ThemeListenerList themeListenerList;

  public ForceTouchView (Context context) {
    super(context);

    this.themeListenerList = new ThemeListenerList();

    // Base

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      params.topMargin = Screen.getStatusBarHeight();
    }*/
    setLayoutParams(params);

    // Background

    backgroundView = new View(context);
    addView(backgroundView);

    // Content

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

    final RectF rectF = new RectF();

    contentWrap = new RelativeLayout(context) {
      private final @Nullable Path path = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? new Path() : null;

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkPath();
      }

      private int lastWidth, lastHeight;

      private void checkPath () {
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();

        if (lastWidth != viewWidth || lastHeight != viewHeight) {
          lastWidth = viewWidth;
          lastHeight = viewHeight;

          rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());

          if (path != null) {
            path.reset();
            path.addRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Path.Direction.CW);
          }
        }
      }

      @Override
      public void draw (Canvas c) {
        final boolean needClip = !forceTouchContext.needHeader;
        final int saveCount = needClip ? ViewSupport.clipPath(c, path) : Integer.MIN_VALUE;
        super.draw(c);
        if (needClip) {
          ViewSupport.restoreClipPath(c, saveCount);
        }
        if (path == null) {
          c.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Paints.strokeBigPaint(ColorUtils.alphaColor(.2f, Theme.textAccentColor())));
        }
      }
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      contentWrap.setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), Screen.dp(4f));
        }
      });
      contentWrap.setElevation(Screen.dp(1f));
      contentWrap.setTranslationZ(Screen.dp(1f));
    }
    ViewUtils.setBackground(contentWrap, new Drawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(Theme.fillingColor()));
      }

      @Override
      public void setAlpha (int alpha) {

      }

      @Override
      public void setColorFilter (ColorFilter colorFilter) {

      }

      @Override
      public int getOpacity () {
        return PixelFormat.UNKNOWN;
      }
    });
    contentWrap.setLayoutParams(params);
    addView(contentWrap);

    themeListenerList.addThemeInvalidateListener(contentWrap);
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    themeListenerList.onThemeColorsChanged(areTemp);
  }

  private @Nullable StateListener listener;
  private ComplexHeaderView headerView;

  private LinearLayout buttonsList;
  private Tdlib tdlib;

  public void initWithContext (ForceTouchContext context) {
    this.tdlib = context.tdlib;
    this.forceTouchContext = context;
    this.listener = context.stateListener;

    if (context.backgroundColor != 0) {
      backgroundView.setBackgroundColor(context.backgroundColor);
    } else {
      ViewSupport.setThemedBackground(backgroundView, R.id.theme_color_previewBackground);
      themeListenerList.addThemeInvalidateListener(backgroundView);
    }

    FrameLayoutFix.LayoutParams baseParams = (FrameLayoutFix.LayoutParams) contentWrap.getLayoutParams();
    if (context.isMatchParent) {
      if (Device.NEED_LESS_PREVIEW_MARGINS) {
        baseParams.leftMargin = baseParams.rightMargin = Screen.dp(16f);
        baseParams.topMargin = HeaderView.getTopOffset() + Screen.dp(20f);
        baseParams.bottomMargin = HeaderView.getTopOffset() + Screen.dp(16f);
      } else {
        baseParams.leftMargin = baseParams.rightMargin = Screen.dp(26f);
        baseParams.topMargin = baseParams.bottomMargin = HeaderView.getTopOffset() + Screen.dp(20f);
      }
    } else {
      baseParams.leftMargin = baseParams.rightMargin = Screen.dp(16f); // = params.topMargin = params.rightMargin = params.bottomMargin = Screen.dp(8f);
      baseParams.topMargin = baseParams.bottomMargin = HeaderView.getTopOffset() + Screen.dp(12f);
    }

    RelativeLayout.LayoutParams contentParams;
    contentParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    contentParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
    // contentParams.addRule(RelativeLayout.CENTER_IN_PARENT);

    context.contentView.setId(R.id.forceTouch_content);
    context.contentView.setLayoutParams(contentParams);
    contentWrap.addView(context.contentView);

    if (context.hasHeader()) {
      RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(HEADER_HEIGHT));
      params.addRule(RelativeLayout.ALIGN_LEFT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.forceTouch_content);
      contentParams.addRule(RelativeLayout.BELOW, R.id.forceTouch_header);

      View targetHeaderView;
      if (context.needHeaderAvatar) {
        headerView = new ComplexHeaderView(getContext(), tdlib, null);
        headerView.setId(R.id.forceTouch_header);
        headerView.setIgnoreCustomHeight();
        headerView.setInnerMargins(Screen.dp(8f), Screen.dp(8f));
        headerView.setTextColors(Theme.textAccentColor(), Theme.textDecentColor());
        if (context.boundDataType == TYPE_CHAT && context.boundDataId != 0) {
          setupChat(context.boundDataId, context.boundArg1, headerView);
        } else if (context.boundDataType == TYPE_USER && context.boundDataId != 0) {
          setupUser((int) context.boundDataId, headerView);
        } else {
          if (context.avatarFile != null) {
            headerView.setAvatar(context.avatarFile);
          } else {
            headerView.setAvatarPlaceholder(context.avatarPlaceholder);
          }
          headerView.setText(context.title, context.subtitle);
        }
        headerView.setLayoutParams(params);
        contentWrap.addView(targetHeaderView = headerView);
      } else {
        DoubleHeaderView headerView = new DoubleHeaderView(getContext());
        headerView.setId(R.id.forceTouch_header);
        headerView.setTitle(context.title);
        headerView.setSubtitle(context.subtitle);
        headerView.setTextColors(Theme.textAccentColor(), Theme.textDecentColor());
        headerView.setLayoutParams(params);
        contentWrap.addView(targetHeaderView = headerView);
      }
      themeListenerList.addThemeDoubleTextColorListener(targetHeaderView, R.id.theme_color_text, R.id.theme_color_textLight);

      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(7f));
      params.addRule(RelativeLayout.ALIGN_LEFT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_TOP, R.id.forceTouch_content);

      ShadowView shadowView = new ShadowView(getContext());
      shadowView.setSimpleBottomTransparentShadow(true);
      shadowView.setLayoutParams(params);
      contentWrap.addView(shadowView);
      themeListenerList.addThemeInvalidateListener(shadowView);
    }

    if (context.hasFooter()) {
      RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(0, /* contentParams.bottomMargin =*/ Screen.dp(FOOTER_HEIGHT));
      params.addRule(RelativeLayout.ALIGN_LEFT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.BELOW, R.id.forceTouch_content);
      params.addRule(RelativeLayout.CENTER_HORIZONTAL);
      if (context.isMatchParent) {
        contentParams.bottomMargin = params.height;
        params.topMargin = -contentParams.bottomMargin;
      }

      buttonsList = new LinearLayout(getContext());
      buttonsList.setId(R.id.forceTouch_footer);
      buttonsList.setOrientation(LinearLayout.HORIZONTAL);
      buttonsList.setGravity(Gravity.CENTER_HORIZONTAL);
      buttonsList.setLayoutParams(params);

      View offsetView;

      if (context.buttonIds.length > 1) {
        offsetView = new View(getContext());
        offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        buttonsList.addView(offsetView);
      }

      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(FOOTER_HEIGHT));
      params.addRule(RelativeLayout.ABOVE, R.id.forceTouch_footer);
      params.addRule(RelativeLayout.ALIGN_LEFT, R.id.forceTouch_footer);
      params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.forceTouch_footer);

      if (context.isMatchParent) {
        params.bottomMargin = contentParams.bottomMargin;
      }

      PopupWrapView popupWrapView = new PopupWrapView(getContext());
      popupWrapView.setLayoutParams(params);
      // popupWrapView.setBackgroundColor(0x1cff0000);

      PopupContext[] popupContexts = new PopupContext[context.buttonIds.length];
      boolean rtl = Lang.rtl();
      int remaining = context.buttonIds.length;
      while (remaining > 0) {
        int i = rtl ? remaining - 1 : context.buttonIds.length - remaining;
        int buttonId = context.buttonIds[i];
        ImageView view;
        if (Drawables.needMirror(context.buttonIcons[i])) {
          view = new ImageView(getContext()) {
            @Override
            protected void onDraw (Canvas c) {
              c.save();
              c.scale(-1f, 1f, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
              super.onDraw(c);
              c.restore();
            }
          };
        } else {
          view = new ImageView(getContext());
        }
        view.setId(buttonId);
        PopupContext popupContext = new PopupContext(popupWrapView, view, context.buttonHints[i]);
        view.setTag(popupContexts[i] = popupContext);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setColorFilter(Theme.iconColor());
        themeListenerList.addThemeFilterListener(view, R.id.theme_color_icon);
        view.setImageResource(context.buttonIcons[i]);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f));

        buttonsList.addView(view);
        remaining--;
      }
      popupWrapView.setItems(popupContexts);

      if (context.buttonHints.length > 1) {
        offsetView = new View(getContext());
        offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        buttonsList.addView(offsetView);
      }

      contentWrap.addView(buttonsList);
      contentWrap.addView(popupWrapView);

      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(6f));
      params.addRule(RelativeLayout.ALIGN_LEFT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_RIGHT, R.id.forceTouch_content);
      params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.forceTouch_content);

      ShadowView shadowView = new ShadowView(getContext());
      shadowView.setSimpleTopShadow(true);
      shadowView.setLayoutParams(params);
      contentWrap.addView(shadowView);
      themeListenerList.addThemeInvalidateListener(shadowView);
    }

    revealFactor = 1f;
    setRevealFactor(0f);

    if (context.boundController != null) {
      context.boundController.setBoundForceTouchView(this);
    }

    ThemeManager.instance().addThemeListener(this);
  }

  // Move events (for actions)

  public void processMoveEvent (float x, float y, float startX, float startY) {
    if (forceTouchContext == null) {
      return;
    }

    if (forceTouchContext.maximizeListener != null && forceTouchContext.boundController != null) {
      forceTouchContext.boundController.maximizeFromPreviewIfNeeded(y, startY);
    }

    if (!forceTouchContext.hasFooter()) {
      return;
    }

    float innerX = x + forceTouchContext.getAddX(this);
    float innerY = y + forceTouchContext.getAddY(this);

    setPointerXY((int) innerX, (int) innerY);
  }

  private int lastX = -1, lastY = -1;
  private boolean hadExperience;

  public int getDistanceToButtonsList (float y) {
    if (forceTouchContext == null || !forceTouchContext.hasFooter() || buttonsList == null) {
      return 0;
    }
    float innerY = y + forceTouchContext.getAddY(this);
    final int contentTop = contentWrap.getBottom() - Screen.dp(FOOTER_HEIGHT) - Screen.dp(48f) /*padding*/;

    return innerY > contentTop ? (int) (innerY - contentTop) : 0;
  }

  private void setPointerXY (int x, int y) {
    if (lastX == -1 && lastY == -1) {
      lastX = x;
      lastY = y;
      return;
    }
    if (!hadExperience && Math.max(Math.abs(x - lastX), Math.abs(y - lastY)) < Screen.getTouchSlop()) {
      return;
    }

    hadExperience = true;

    this.lastX = x;
    this.lastY = y;

    ImageView foundView = findActionViewByPosition(x, y);
    setActionView(foundView);
  }

  private ImageView lastActionView;
  private int lastActionId;

  private void setActionView (ImageView imageView) {
    if (lastActionView == imageView) {
      return;
    }

    if (lastActionView != null) {
      ((PopupContext) lastActionView.getTag()).setIsVisible(false);
    }

    lastActionView = imageView;
    lastActionId = imageView != null ? imageView.getId() : 0;

    if (imageView != null) {
      ((PopupContext) imageView.getTag()).setIsVisible(true);
    }
  }

  private ImageView findActionViewByPosition (int x, int y) {
    final int contentBottom = contentWrap.getBottom();

    if (y > contentBottom || y < contentBottom - Screen.dp(FOOTER_HEIGHT) || buttonsList == null) {
      return null;
    }

    x -= contentWrap.getLeft();
    if (forceTouchContext.hasFooter()) {
      x -= buttonsList.getLeft();
    }

    final int childCount = buttonsList.getChildCount();
    final int offsetCount = forceTouchContext.buttonIds.length == 1 ? 0 : 1;

    for (int i = offsetCount; i < childCount - offsetCount; i++) {
      View view = buttonsList.getChildAt(i);
      if (view != null && x >= view.getLeft() && x <= view.getRight()) {
        return (ImageView) view;
      }
    }

    return null;
  }

  // Reveal animation

  private static final float REVEAL_FACTOR = .7f;
  private float revealFactor;

  public void setBeforeMaximizeFactor (float factor) {
    if (revealFactor >= 1f) {
      setRevealFactor(1f + .1f * factor);
      if (revealAnimator != null) {
        revealAnimator.forceFactor(revealFactor);
      }
    }
  }

  private void setRevealFactor (float factor) {
    if (this.revealFactor != factor) {
      this.revealFactor = factor;
      final float scale = REVEAL_FACTOR + (1f - REVEAL_FACTOR) * factor;
      contentWrap.setScaleX(scale);
      contentWrap.setScaleY(scale);


      if (isMaximizing) {
        float progressFactor = MathUtils.clamp((factor - maximizingFromFactor) / (MAXIMIZE_FACTOR - maximizingFromFactor));
        float alpha = MathUtils.clamp(maximizingFromFactor * (1f - progressFactor));
        contentWrap.setAlpha(alpha);
        backgroundView.setAlpha(alpha);
      } else {
        float alpha = MathUtils.clamp(factor);
        contentWrap.setAlpha(alpha);
        backgroundView.setAlpha(alpha);
      }

    }
  }

  // Animator

  private static final int REVEAL_ANIMATOR = 0;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        setRevealFactor(factor);
        break;
      }
    }
  }

  private boolean isDestroyed;

  private void destroy () {
    isDestroyed = true;
    if (forceTouchContext.contentView instanceof Destroyable) {
      ((Destroyable) forceTouchContext.contentView).performDestroy();
    }
    if (headerView != null) {
      headerView.performDestroy();
    }
    if (listener != null) {
      listener.onDestroyForceTouch(forceTouchContext);
    }
    removeListeners();
    ThemeManager.instance().removeThemeListener(this);
  }

  private void performAction (boolean after) {
    if (lastActionId != 0 && forceTouchContext.actionListener != null) {
      if (after) {
        forceTouchContext.actionListener.onAfterForceTouchAction(forceTouchContext, lastActionId, forceTouchContext.listenerArg);
      } else {
        forceTouchContext.actionListener.onForceTouchAction(forceTouchContext, lastActionId, forceTouchContext.listenerArg);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        if (finalFactor == 0f || finalFactor == MAXIMIZE_FACTOR) {
          destroy();
          if (pendingCustomPopup != null) {
            pendingCustomPopup.onCustomHideAnimationComplete();
          }
          performAction(true);
          // UI.getContext(getContext()).setLowProfile(false, false);
        } else if (finalFactor == 1f) {
          if (listener != null) {
            listener.onCompletelyShownForceTouch(forceTouchContext);
          }
          if (pendingPopup != null) {
            pendingPopup.onCustomShowComplete();
          }
        }
        break;
      }
    }
  }

  // Popups

  private static class PopupContext implements FactorAnimator.Target {
    private final PopupWrapView parent;
    private final View target;

    private final String text;
    private final boolean fakeBold;
    private final int width;

    private float factor;

    public PopupContext (PopupWrapView parent, View target, String text) {
      this.parent = parent;
      this.target = target;
      this.text = text;
      this.fakeBold = Text.needFakeBold(text);
      this.width = (int) U.measureText(text, Paints.getMediumTextPaint(12f, fakeBold));
    }

    public void draw (Canvas c) {
      if (factor != 0f) {
        int centerX = (target.getLeft() + target.getRight()) >> 1;

        int startY = parent.getMeasuredHeight();

        final int paddingHorizontal = Screen.dp(8f);
        final int marginBottom = Screen.dp(8f);

        final int rectHeight = Screen.dp(28f);
        final int diffY = rectHeight + marginBottom;

        final int rectTop = startY - (int) ((float) diffY * factor);

        RectF rectF = Paints.getRectF();
        rectF.set(centerX - width / 2 - paddingHorizontal, rectTop, centerX + width / 2 + paddingHorizontal, rectTop + rectHeight);

        float alpha = MathUtils.clamp(factor);
        c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x7c000000)));
        int color = ColorUtils.alphaColor(alpha, 0xffffffff);
        c.drawText(text, centerX - width / 2, rectF.top + Screen.dp(18f), Paints.getMediumTextPaint(12f, color, fakeBold));
      }
    }

    // FactorAnimator

    private boolean isVisible;

    public void setIsVisible (boolean isVisible) {
      if (this.isVisible != isVisible) {
        this.isVisible = isVisible;
        animateFactor(isVisible ? 1f : 0f);
        if (isVisible) {
          UI.forceVibrate(target, false);
          // target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING | HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
      }
    }

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        this.parent.invalidate();
      }
    }

    private FactorAnimator animator;

    private static final Interpolator interpolator = new OvershootInterpolator(1.28f);

    private void animateFactor (float factor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, interpolator, 230l, this.factor);
      }
      animator.animateTo(factor);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
  }

  private static class PopupWrapView extends View {
    private PopupContext[] items;

    public PopupWrapView (Context context) {
      super(context);
    }

    public void setItems (PopupContext[] items) {
      this.items = items;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (items != null) {
        for (PopupContext item : items) {
          item.draw(c);
        }
      }
    }
  }

  // Animation

  private static final OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.24f); // 1.78f
  private FactorAnimator revealAnimator;

  @Override
  public void prepareShowAnimation () {
    if (Device.NEED_REDUCE_BOUNCE || Settings.instance().needReduceMotion()) {
      revealAnimator = new FactorAnimator(REVEAL_ANIMATOR, this, new DecelerateInterpolator(1.46f), 140l);
    } else {
      revealAnimator = new FactorAnimator(REVEAL_ANIMATOR, this, overshootInterpolator, 260l);
    }
  }

  public boolean isAnimatingReveal () {
    return revealAnimator == null || revealAnimator.isAnimating();
  }

  private PopupLayout pendingPopup;

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    this.pendingPopup = popup;
    if (listener != null) {
      listener.onPrepareToEnterForceTouch(forceTouchContext);
    }
    // UI.getContext(getContext()).setLowProfile(true, false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && forceTouchContext.isMatchParent) {
      revealAnimator.setStartDelay(68l);
    }
    revealAnimator.animateTo(1f);
  }

  private PopupLayout pendingCustomPopup;
  private boolean isMaximizing;
  private float maximizingFromFactor;

  private boolean forceMaximize;

  public boolean maximize () {
    if (!isMaximizing && !forceMaximize) {
      forceMaximize = true;
    }
    return true;
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    if (forceTouchContext != null && forceTouchContext.boundController != null && forceTouchContext.boundController.wouldMaximizeFromPreview()) {
      forceMaximize = true;
    }
    if (listener != null) {
      listener.onPrepareToExitForceTouch(forceTouchContext);
    }
    pendingCustomPopup = popup;
    if (forceMaximize || (lastActionId == R.id.maximize && revealFactor >= .8f)) {
      // revealAnimator.setStartDelay(62l);
      revealAnimator.setStartDelay(40l);
      revealAnimator.setDuration(140l);
      maximizingFromFactor = revealFactor;
      isMaximizing = true;
      // revealAnimator.animateTo(MAXIMIZE_FACTOR);
      revealAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);

      if (forceTouchContext.maximizeListener != null) {
        if (forceTouchContext.maximizeListener.onPerformMaximize(revealAnimator, MAXIMIZE_FACTOR, forceTouchContext.listenerArg)) {
          return true;
        }
      }

      revealAnimator.animateTo(MAXIMIZE_FACTOR);
    } else {
      revealAnimator.setStartDelay(0);
      revealAnimator.animateTo(0f);
      performAction(false);
    }
    return true;
  }

  private static final float MAXIMIZE_FACTOR = 1.3f;

  private static final int TYPE_NONE = 0;
  private static final int TYPE_CHAT = 1;
  private static final int TYPE_USER = 2;

  // Context

  public static class ForceTouchContext {
    private final View sourceView;
    private final View contentView;

    private boolean allowFullscreen;
    private boolean needHeader, needHeaderAvatar;
    private boolean isMatchParent;

    private int backgroundColor;

    // Header

    private AvatarPlaceholder avatarPlaceholder;
    private ImageFile avatarFile;

    private String title, subtitle;

    private int boundDataType;
    private long boundDataId, boundArg1;

    private StateListener stateListener;

    // Footer

    private Tdlib tdlib;
    private ActionListener actionListener;
    private MaximizeListener maximizeListener;
    private Object listenerArg;
    private int[] buttonIds;
    private int[] buttonIcons;
    private String[] buttonHints;
    private boolean excludeHeader;

    public View getSourceView () {
      return sourceView;
    }

    private ViewController<?> boundController;

    public ForceTouchContext (Tdlib tdlib, View sourceView, View contentView, @Nullable ViewController<?> controller) {
      this.tdlib = tdlib;
      this.sourceView = sourceView;
      this.contentView = contentView;
      this.stateListener = contentView instanceof StateListener ? (StateListener) contentView : null;
      this.boundController = controller;
    }

    public void setTdlib (Tdlib tdlib) {
      this.tdlib = tdlib;
    }

    public ForceTouchContext setBackgroundColor (int backgroundColor) {
      this.backgroundColor = backgroundColor;
      return this;
    }

    public ForceTouchContext setExcludeHeader (boolean excludeHeader) {
      this.excludeHeader = excludeHeader;
      return this;
    }

    public float getAddX (ForceTouchView target) {
      int[] location = Views.getLocationOnScreen(sourceView);
      int sourceX = location[0];
      location = Views.getLocationOnScreen(target);
      return sourceX - location[0];
    }

    public float getAddY (ForceTouchView target) {
      int[] location = Views.getLocationOnScreen(sourceView);
      int sourceY = location[1];
      location = Views.getLocationOnScreen(target);
      return sourceY - location[1];
      /*int y = 0;
      if (offsetView != null) {
        y += offsetView.getTop();
        y += (int) offsetView.getTranslationY();
      }
      if (sourceView != null) {
        y += sourceView.getTop();
      }
      for (View view : additionalOffsetViews) {
        y += view.getTop();
      }
      if (!excludeHeader) {
        y += HeaderView.getSize(true);
      }
      return y;*/
    }

    public boolean hasFooter () {
      return buttonIds != null && buttonIds.length > 0;
    }

    public boolean hasHeader () {
      return needHeader;
    }

    public int getMinimumWidth () {
      return hasFooter() ? (buttonIds.length > 1 ? buttonIds.length + 1 : buttonIds.length) * Screen.dp(48f) : 0;
    }

    public void setButtons (ActionListener listener, Object listenerArg, int[] ids, int[] icons, String[] hints) {
      this.actionListener = listener;
      if (this.listenerArg == null) { // FIXME code design
        this.listenerArg = listenerArg;
      }
      this.buttonIds = ids;
      this.buttonIcons = icons;
      this.buttonHints = hints;
    }

    public MaximizeListener getMaximizeListener () {
      return maximizeListener;
    }

    public ForceTouchContext setMaximizeListener (MaximizeListener maximizeListener) {
      this.maximizeListener = maximizeListener;
      return this;
    }

    public void setHeader (String title, String subtitle) {
      this.needHeader = true;
      this.title = title;
      this.subtitle = subtitle;
    }

    public void setBoundChatId (long chatId, long messageThreadId) {
      this.needHeader = true;
      this.needHeaderAvatar = true;
      this.boundDataType = TYPE_CHAT;
      this.boundDataId = chatId;
      this.boundArg1 = messageThreadId;
    }

    public void setBoundUserId (int userId) {
      this.needHeader = userId != 0;
      this.needHeaderAvatar = true;
      this.boundDataType = TYPE_USER;
      this.boundDataId = userId;
      this.boundArg1 = 0;
    }

    public void setHeaderAvatar (ImageFile avatarFile, AvatarPlaceholder avatarPlaceholder) {
      this.needHeaderAvatar = true;
      this.avatarFile = avatarFile;
      this.avatarPlaceholder = avatarPlaceholder;
    }

    public ForceTouchContext setStateListener (StateListener stateListener) {
      this.stateListener = stateListener;
      return this;
    }

    public ForceTouchContext setStateListenerArgument (Object arg) {
      this.listenerArg = arg;
      return this;
    }

    public ForceTouchContext setAllowFullscreen (boolean allowFullscreen) {
      this.allowFullscreen = (!Device.DISABLE_FULLSCREEN_PREVIEW || (boundController != null && boundController.context().isKeyboardVisible())) && allowFullscreen;
      return this;
    }

    public ForceTouchContext setIsMatchParent (boolean isMatchParent) {
      this.isMatchParent = isMatchParent;
      return this;
    }

    public boolean allowFullscreen () {
      return allowFullscreen;
    }

    public boolean needHideKeyboard () {
      return !allowFullscreen && (boundController == null || boundController.wouldHideKeyboardInForceTouchMode());
    }
  }

  // Header

  private int boundDataType;
  private TdApi.User boundUser;
  private TdApi.Chat boundChat;
  private long boundMessageThreadId;

  private void setupUser (int userId, ComplexHeaderView headerView) {
    TdApi.User user = tdlib.cache().user(userId);
    if (user == null) {
      throw new NullPointerException();
    }

    this.boundDataType = TYPE_USER;
    this.boundUser = user;
    addUserListeners(user, true);

    setHeaderUser(user);
  }

  private void setHeaderUser (TdApi.User user) {
    headerView.setShowVerify(user.isVerified);
    headerView.setText(TD.getUserName(user), tdlib.status().getPrivateChatSubtitle(user.id, user, false));
    setChatAvatar();
  }

  private void setupChat (long chatId, long messageThreadId, ComplexHeaderView headerView) {
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      throw new NullPointerException();
    }

    this.boundDataType = TYPE_CHAT;
    this.boundChat = chat;
    this.boundMessageThreadId = messageThreadId;
    addChatListeners(chat, messageThreadId, true);

    headerView.setShowLock(ChatId.isSecret(chatId));
    headerView.setShowVerify(tdlib.chatVerified(chat));
    headerView.setShowMute(tdlib.chatNeedsMuteIcon(chat.id));
    headerView.setText(tdlib.chatTitle(chat), tdlib.status().chatStatus(chat));
    setChatAvatar();
  }

  private void setChatAvatar () {
    if (!isDestroyed) {
      switch (boundDataType) {
        case TYPE_CHAT: {
          if (boundChat != null) {
            boolean isSelfChat = tdlib.isSelfChat(boundChat.id);
            if (!isSelfChat && boundChat.photo != null) {
              headerView.setAvatar(TD.getAvatar(tdlib, boundChat));
            } else {
              headerView.setAvatarPlaceholder(tdlib.chatPlaceholder(boundChat, true, ComplexHeaderView.getBaseAvatarRadiusDp(), null));
            }
          }
          break;
        }
        case TYPE_USER: {
          if (boundUser != null) {
            if (boundUser.profilePhoto != null) {
              headerView.setAvatar(TD.getAvatar(tdlib, boundUser));
            } else {
              headerView.setAvatarPlaceholder(tdlib.cache().userPlaceholder(boundUser, false, ComplexHeaderView.getBaseAvatarRadiusDp(), null));
            }
          }
          break;
        }
      }
    }
  }

  private void setChatTitle () {
    if (!isDestroyed && boundChat != null) {
      headerView.setTitle(tdlib.chatTitle(boundChat));
    }
  }

  private void setChatSubtitle () {
    if (!isDestroyed) {
      if (boundChat != null) {
        headerView.setSubtitle(tdlib.status().chatStatus(boundChat));
      }
      if (boundUser != null) {
        headerView.setSubtitle(tdlib.status().getPrivateChatSubtitle(boundUser.id, boundUser, false));
      }
    }
  }

  private void setChatMute () {
    if (!UI.inUiThread()) {
      tdlib.ui().post(this::setChatMute);
      return;
    }
    if (!isDestroyed && boundChat != null) {
      headerView.setShowMute(tdlib.chatNeedsMuteIcon(boundChat.id));
    }
  }

  private void removeListeners () {
    if (boundChat != null) {
      addChatListeners(boundChat, boundMessageThreadId, false);
      boundChat = null;
    }
    if (boundUser != null) {
      addUserListeners(boundUser, false);
      boundUser = null;
    }
  }

  private void addUserListeners (TdApi.User user, boolean add) {
    if (add) {
      tdlib.cache().subscribeToUserUpdates(user.id, this);
    } else {
      tdlib.cache().unsubscribeFromUserUpdates(user.id, this);
    }
  }

  private void addChatListeners (TdApi.Chat chat, long messageThreadId, boolean add) {
    if (add) {
      tdlib.listeners().subscribeToChatUpdates(chat.id, this);
      tdlib.listeners().subscribeToSettingsUpdates(chat.id, this);
      headerView.attachChatStatus(chat.id, messageThreadId);
      // tdlib.status().subscribeForChatUpdates(chat.id, this);
    } else {
      tdlib.listeners().unsubscribeFromChatUpdates(chat.id, this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(chat.id, this);
      headerView.removeChatStatus();
      // tdlib.status().unsubscribeFromChatUpdates(chat.id, this);
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToUserUpdates(TD.getUserId(chat.type), this);
        } else {
          tdlib.cache().unsubscribeFromUserUpdates(TD.getUserId(chat.type), this);
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        }
        break;
      }
    }
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    switch (boundDataType) {
      case TYPE_CHAT:
        break;
      case TYPE_USER:
        setHeaderUser(user);
        break;
    }
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @UiThread
  @Override
  public void onUserStatusChanged (int userId, TdApi.UserStatus status, boolean uiOnly) {
    switch (boundDataType) {
      case TYPE_CHAT:
      case TYPE_USER:
        setChatSubtitle();
        break;
    }
  }

  @Override
  public void onBasicGroupUpdated (TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    tdlib.ui().post(this::setChatSubtitle);
  }

  @Override
  public void onBasicGroupFullUpdated (int basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    tdlib.ui().post(this::setChatSubtitle);
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    tdlib.ui().post(this::setChatSubtitle);
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    tdlib.ui().post(this::setChatSubtitle);
  }

  @Override
  public void onSupergroupFullUpdated (int supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    tdlib.ui().post(this::setChatSubtitle);
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    tdlib.ui().post(this::setChatTitle);
  }

  @Override
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) { }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    tdlib.ui().post(this::setChatAvatar);
  }

  @Override
  public void onChatDefaultDisableNotifications (long chatId, boolean defaultDisableNotifications) { }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    setChatMute();
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    setChatMute();
  }
}
