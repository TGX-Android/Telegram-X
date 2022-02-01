/**
 * File created on 17/03/16 at 01:17
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.sticker;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.OverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.ShareController;
import org.thunderdog.challegram.ui.StickersListController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class StickerSetWrap extends FrameLayoutFix implements StickersListController.StickerSetProvider, StickersListController.OffsetProvider, View.OnClickListener, FactorAnimator.Target, PopupLayout.PopupHeightProvider {
  private HeaderView headerView;
  private StickersListController stickersController;
  private FrameLayoutFix bottomWrap;
  private TextView textButton;
  private ShadowView topShadow;
  private RelativeLayout button;
  private ProgressComponentView progressView;

  private boolean isOneShot = true;

  private LickView topLick;

  private static class LickView extends View {
    public LickView (Context context) {
      super(context);
      setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (factor > 0f) {
        int bottom = getMeasuredHeight();
        int top = bottom - (int) ((float) bottom * factor);
        c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
      }
    }
  }

  private final ThemeListenerList themeListener = new ThemeListenerList();

  private final Tdlib tdlib;

  public StickerSetWrap (Context context, Tdlib tdlib) {
    super(context);

    this.tdlib = tdlib;

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));

    bottomWrap = new FrameLayoutFix(context);
    bottomWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f) + Screen.dp(7f), Gravity.BOTTOM));
    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    bottomWrap.addView(shadowView);
    themeListener.addThemeInvalidateListener(shadowView);

    FrameLayoutFix buttonWrap = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(buttonWrap, R.id.theme_color_filling);
    themeListener.addThemeInvalidateListener(buttonWrap);
    buttonWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM));

    button = new RelativeLayout(context);
    button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    button.setBackgroundResource(R.drawable.bg_btn_header);
    button.setOnClickListener(this);
    Views.setClickable(button);

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.CENTER_IN_PARENT);

    textButton = new NoScrollTextView(context);
    textButton.setId(R.id.btn_addStickerSet);
    textButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    textButton.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    textButton.setGravity(Gravity.CENTER);
    textButton.setTypeface(Fonts.getRobotoMedium());
    textButton.setSingleLine(true);
    textButton.setEllipsize(TextUtils.TruncateAt.END);
    textButton.setLayoutParams(params);
    button.addView(textButton);

    params = new RelativeLayout.LayoutParams(Screen.dp(11f), Screen.dp(11f));
    params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
    params.addRule(RelativeLayout.CENTER_VERTICAL);

    progressView = new ProgressComponentView(context);
    progressView.initCustom(4.5f, 0f, 10f);
    progressView.setVisibility(View.VISIBLE);
    progressView.setLayoutParams(params);
    themeListener.addThemeInvalidateListener(progressView);

    button.addView(progressView);
    buttonWrap.addView(button);

    bottomWrap.addView(buttonWrap);

    this.headerView = new HeaderView(context);

    stickersController = new StickersListController(context, tdlib);
    stickersController.attachToThemeListeners(themeListener);
    stickersController.setArguments(this);
    stickersController.setOffsetProvider(this);
    stickersController.attachHeaderViewWithoutNavigation(headerView);

    topShadow = new ShadowView(context);
    topShadow.setSimpleTopShadow(true);
    themeListener.addThemeInvalidateListener(topShadow);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION) {
      topLick = new LickView(context);
      themeListener.addThemeInvalidateListener(topLick);
    }

    updateHeader();
  }

  @Override
  public void invalidate () {
    headerView.resetColors(stickersController, null);
  }

  @Override
  public boolean onStickerClick (View view, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    NavigationController navigation = UI.getContext(getContext()).navigation();
    if (navigation != null) {
      ViewController<?> c = navigation.getCurrentStackItem();
      if (c instanceof MessagesController && ((MessagesController) c).canWriteMessages()) {
        if (((MessagesController) c).onSendSticker(view, sticker, forceDisableNotification, schedulingState)) {
          popupLayout.hideWindow(true);
          return true;
        }
      } else {
        ShareController s = new ShareController(getContext(), tdlib);
        s.setArguments(new ShareController.Args(sticker.getSticker()));
        s.show();
        return true;
      }
    }
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    NavigationController navigation = UI.getContext(getContext()).navigation();
    if (navigation != null) {
      ViewController<?> c = navigation.getCurrentStackItem();
      if (c instanceof MessagesController && ((MessagesController) c).canWriteMessages()) {
        return c.getChatId();
      }
    }
    return 0;
  }

  @Override
  public boolean canArchiveStickerSet () {
    return info.isInstalled && !info.isArchived;
  }

  @Override
  public boolean canRemoveStickerSet () {
    return !info.isInstalled && info.isArchived;
  }

  @Override
  public void removeStickerSet () {
    makeRequest(STATE_UNINSTALLED);
  }

  @Override
  public boolean canViewPack () {
    return false;
  }

  @Override
  public void archiveStickerSet () {
    archive();
  }

  private float statusBarFactor;

  private void setStatusBarFactor (float factor) {
    if (this.statusBarFactor != factor) {
      this.statusBarFactor = factor;
      OverlayView view = ((BaseActivity) getContext()).getLayeredOverlayView();
      int toColor = HeaderView.whiteStatusColor();
      int fromColor = view != null ? view.getCurrentStatusBarColor() : toColor;
      UI.setStatusBarColor(ColorUtils.fromToArgb(fromColor, toColor, factor));
    }
  }

  public void setIsOneShot () {
    this.isOneShot = true;
  }

  private int getStatusBarLimit () {
    return Size.getHeaderPortraitSize() / 2;
  }

  private void updateHeader () {
    int topOffset = HeaderView.getTopOffset();
    int top = Math.max(topOffset, getHeaderTop());
    this.headerView.setTranslationY(top);
    if (topLick != null) {
      topLick.setTranslationY(top - HeaderView.getTopOffset());
    }
    this.topShadow.setTranslationY(top - Screen.dp(6f));
    top -= topOffset;
    float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (Config.USE_FULLSCREEN_NAVIGATION) {
        if (topLick != null) {
          topLick.setFactor(factor);
        }
      } else {
        int limit = getStatusBarLimit();
        setStatusBarFactor(top > limit ? 0f : 1f - ((float) top / (float) limit));
      }
    }

    if (headerView != null && headerView.getFilling() != null) {
      headerView.getFilling().setShadowAlpha(factor);
    }
  }

  @Override
  public int getCurrentPopupHeight () {
    int top = Math.max(0, getHeaderTop());

    return getMeasuredHeight() - top;
  }

  private FactorAnimator animator;
  private String pendingText;
  private float pendingProgressFactor;
  private int pendingTextColorId;

  private static final float MIN_SCALE = .8f;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (factor >= .5f && pendingText != null) {
      Views.setMediumText(textButton, pendingText);
      themeListener.removeThemeListenerByTarget(textButton);
      textButton.setTextColor(Theme.getColor(pendingTextColorId));
      themeListener.addThemeColorListener(textButton, pendingTextColorId);
      pendingText = null;
    }

    progressView.forceFactor(factor >= .5f ? 0f : pendingProgressFactor * (1f - (factor / .5f)));
    progressView.invalidate();

    float alpha = factor <= .5f ? 1f - (factor / .5f) : (factor - .5f) / .5f;
    final float scale = MIN_SCALE + (1f - MIN_SCALE) * alpha;
    //noinspection Range
    textButton.setAlpha(alpha);
    textButton.setScaleX(scale);
    textButton.setScaleY(scale);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private boolean inProgress;

  private CancellableRunnable scheduledProgress;

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      button.setEnabled(!inProgress);
      if (scheduledProgress != null) {
        scheduledProgress.cancel();
        button.removeCallbacks(scheduledProgress);
        scheduledProgress = null;
      }
      if (inProgress) {
        scheduledProgress = new CancellableRunnable() {
          @Override
          public void act () {
            progressView.animateFactor(1f);
          }
        };
        button.postDelayed(scheduledProgress, 180l);
      }
    }
  }

  private void archive () {
    if (!inProgress) {
      makeRequest(STATE_ARCHIVED);
    }
  }

  private static final int STATE_UNINSTALLED = 0;
  private static final int STATE_ARCHIVED = 1;
  private static final int STATE_INSTALLED = 2;

  private void makeRequest (final int state) {
    final boolean isInstalled, isArchived;
    switch (state) {
      case STATE_ARCHIVED:
        isArchived = true;
        isInstalled = false;
        break;
      case STATE_INSTALLED:
        isInstalled = true;
        isArchived = false;
        break;
      case STATE_UNINSTALLED:
      default:
        isInstalled = isArchived = false;
        break;
    }
    if (!inProgress) {
      setInProgress(true);
      tdlib.client().send(new TdApi.ChangeStickerSet(info.id, isInstalled, isArchived), object -> {
        final boolean ok = object.getConstructor() == TdApi.Ok.CONSTRUCTOR;
        tdlib.ui().post(() -> {
          setInProgress(false);
          if (ok) {
            info.isInstalled = isInstalled;
            info.isArchived = isArchived;
            switch (state) {
              case STATE_ARCHIVED:
                tdlib.listeners().notifyStickerSetArchived(info);
                break;
              case STATE_INSTALLED:
                tdlib.listeners().notifyStickerSetInstalled(info);
                break;
              case STATE_UNINSTALLED:
                tdlib.listeners().notifyStickerSetRemoved(info);
                break;
            }
            if (isOneShot) {
              popupLayout.hideWindow(true);
            } else {
              updateButton(true);
            }
          } else if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
            UI.showError(object);
            updateButton(true);
          }
        });
      });
    }
  }

  private void updateButton (boolean animated) {
    if (info.stickerType.getConstructor() == TdApi.StickerTypeMask.CONSTRUCTOR) {
      updateButton(Lang.plural(info.isInstalled && !info.isArchived ? R.string.RemoveXMasks : R.string.AddXMasks, info.size), !info.isInstalled || info.isArchived, animated);
    } else {
      updateButton(Lang.plural(info.isInstalled && !info.isArchived ? R.string.RemoveXStickers : R.string.AddXStickers, info.size), !info.isInstalled || info.isArchived, animated);
    }
  }

  private void updateButton (String str, boolean positive, boolean animated) {
    str = str.toUpperCase();
    int colorId = positive ? R.id.theme_color_textNeutral : R.id.theme_color_textNegative;
    if (!textButton.getText().toString().equals(str) || textButton.getCurrentTextColor() != Theme.getColor(colorId)) {
      if (animated) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        } else {
          animator.forceFactor(0f);
        }
        pendingText = str;
        pendingTextColorId = colorId;
        pendingProgressFactor = progressView.cancelPendingAnimation();
        animator.animateTo(1f);
      } else {
        Views.setMediumText(textButton, str);
        themeListener.removeThemeListenerByTarget(textButton);
        textButton.setTextColor(Theme.getColor(colorId));
        themeListener.addThemeColorListener(textButton, colorId);
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateHeader();
  }

  private TdApi.StickerSetInfo info;

  public void initWithInfo (TdApi.StickerSetInfo info) {
    this.info = info;
    updateButton(false);
    stickersController.setStickerSetInfo(info);
    addViews();
  }

  public void initWithSet (TdApi.StickerSet set) {
    this.info = new TdApi.StickerSetInfo(set.id, set.title, set.name, set.thumbnail, set.thumbnailOutline, set.isInstalled, set.isArchived, set.isOfficial, set.stickerType, false, set.stickers.length, null);
    updateButton(false);
    stickersController.setStickerSetInfo(info);
    stickersController.setStickers(set.stickers, info.stickerType, set.emojis);
    addViews();
  }

  private void addViews () {
    headerView.initWithSingleController(stickersController, false);
    addView(stickersController.get());
    addView(topShadow);
    if (topLick != null) {
      addView(topLick);
    }
    addView(headerView);
    addView(bottomWrap);
  }

  private int getHeaderTop () {
    return provideOffset() - stickersController.getOffsetScroll();
  }

  @Override
  public void onScrollFinished () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

      if (Config.USE_FULLSCREEN_NAVIGATION) {
        if (topLick != null) {
          if (topLick.factor >= .4f) {
            stickersController.scrollBy((int) ((float) HeaderView.getTopOffset() * (1f - topLick.factor)));
          } else {
            stickersController.scrollBy(-(int) ((float) HeaderView.getTopOffset() * topLick.factor));
          }
        }

      } else {
        if (statusBarFactor != 0f && statusBarFactor != 1f) {
          if (statusBarFactor >= .4f) {
            stickersController.scrollBy(getHeaderTop());
          } else {
            stickersController.scrollBy(-(getStatusBarLimit() - getHeaderTop()));
          }
        }
      }
    }
  }

  private int calculateTotalHeight () {
    return Math.min(Math.max(Screen.currentActualHeight() / 2, Screen.smallestSide()), Screen.dp(350f));
  }

  @Override
  public int provideOffset () {
    return Math.max(0, Screen.currentActualHeight() - calculateTotalHeight());
  }

  // private float shadowFactor;

  @Override
  public void onContentScroll (float shadowFactor) {
    updateHeader();
    /*if (this.shadowFactor != shadowFactor) {
      this.shadowFactor = shadowFactor;
      headerView.getFilling().setShadowAlpha(shadowFactor);
      headerView.invalidate();
    }*/
  }

  @Override
  public int provideReverseOffset () {
    return Screen.currentActualHeight() - provideOffset() - Screen.dp(56f) - Size.getHeaderPortraitSize();
  }

  // Click logic

  @Override
  public void onClick (View v) {
    if (info == null || inProgress) {
      return;
    }

    if (info.isArchived || info.isOfficial) {
      if (info.isArchived) {
        makeRequest(STATE_INSTALLED);
      } else {
        archive();
      }
    } else {
      makeRequest(info.isInstalled ? STATE_UNINSTALLED : STATE_INSTALLED);
    }
  }

  // Show

  private PopupLayout popupLayout;

  public void showStickerSet () {
    popupLayout = new PopupLayout(getContext());
    popupLayout.setDismissListener(popup -> {
      stickersController.destroy();
      progressView.performDestroy();
    });
    popupLayout.setShowListener(popup -> {
      stickersController.setItemAnimator();
    });
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.showSimplePopupView(this, calculateTotalHeight());
  }

  public static StickerSetWrap showStickerSet (TdlibDelegate context, TdApi.StickerSetInfo info) {
    StickerSetWrap wrap = new StickerSetWrap(context.context(), context.tdlib());
    wrap.initWithInfo(info);
    wrap.showStickerSet();
    return wrap;
  }

  public static StickerSetWrap showStickerSet (TdlibDelegate context, TdApi.StickerSet set) {
    StickerSetWrap wrap = new StickerSetWrap(context.context(), context.tdlib());
    wrap.initWithSet(set);
    wrap.showStickerSet();
    return wrap;
  }
}
