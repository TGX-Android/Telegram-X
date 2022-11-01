package org.thunderdog.challegram.component.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class IdentityLayout extends RelativeLayout
  implements PopupLayout.DismissListener, PopupLayout.TouchDownInterceptor,
  PopupLayout.AnimatedPopupProvider, BaseActivity.PopupAnimatorOverride {
  public static final long REVEAL_DURATION = 220l;

  private final Tdlib tdlib;
  private final MessagesController target;
  private final TdApi.Chat chat;


  private IdentitiesController controller;
  private PopupLayout popupLayout;
  private PopupLayout openingPopup;
  private PopupLayout pendingPopup;

  private int contentHeight = 0;

  public IdentityLayout (@NonNull Context context, Tdlib tdlib, MessagesController target, TdApi.Chat chat) {
    super(context);
    this.tdlib = tdlib;
    this.target = target;
    this.chat = chat;
  }

  public void init (List<Identity> identities, Identity selectedIdentity) {
    controller = new IdentitiesController(getContext(), tdlib);
    Runnable close = () -> {
      launchHideAnimation(popupLayout, null);
    };
    Runnable goToSearchMode = () -> {
      createGoToSearchModeAnimator().start();
    };
    Runnable quitSearchMode = () -> {
      createQuitSearchModeAnimator().start();
    };
    Consumer<Identity> onIdentityClick = identity -> {
      tdlib.switchIdentity(chat.id, identity, () -> {}, error -> {} );
    };
    controller.setArguments(new IdentitiesController.Arguments(
      identities,
      selectedIdentity,
      close,
      goToSearchMode,
      quitSearchMode,
      onIdentityClick
    ));
    addView(controller.get());
  }

  public void show () {
    popupLayout = new PopupLayout(getContext());
    popupLayout.setTouchDownInterceptor(this);
    popupLayout.setHideKeyboard();
    popupLayout.setDismissListener(this);
    popupLayout.setNeedRootInsets();
    popupLayout.init(true);
    popupLayout.showAnimatedPopupView(this, this);
  }

  @Override
  public void onPopupDismiss (PopupLayout popup) {
    target.setBroadcastAction(TdApi.ChatActionCancel.CONSTRUCTOR);
    removeAllViews();
  }

  @Override
  public boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e) {
    return false;
  }

  @Override
  public void prepareShowAnimation () {

  }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    this.openingPopup = popup;
    createCustomShowAnimator().start();
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    this.pendingPopup = popup;
    createCustomHideAnimator().start();
    return true;
  }

  @Override
  public Animator createCustomShowAnimator () {
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addUpdateListener(animation -> setRevealFactor(AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (openingPopup != null) {
          openingPopup.onCustomShowComplete();
        }
        if (controller != null) {
          contentHeight = controller.get().getMeasuredHeight();
        }
      }
    });
    setRevealFactor(0f);
    return animator;
  }

  public Animator createGoToSearchModeAnimator () {
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addUpdateListener(animation -> setSearchModeFactor(AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (openingPopup != null) {
          openingPopup.onCustomShowComplete();
        }
      }
    });
    setSearchModeFactor(0f);
    return animator;
  }

  public Animator createQuitSearchModeAnimator () {
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      setSearchModeFactor(1f - factor);
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (openingPopup != null) {
          openingPopup.onCustomShowComplete();
        }
      }
    });
    setSearchModeFactor(1f);
    return animator;
  }

  @Override
  public void modifyBaseShowAnimator (ValueAnimator animator) {

  }

  @Override
  public boolean shouldOverrideHideAnimation () {
    return true;
  }

  @Override
  public Animator createCustomHideAnimator () {
    Animator.AnimatorListener endListener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (pendingPopup != null) {
          pendingPopup.onCustomHideAnimationComplete();
        }
      }
    };
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setDuration(REVEAL_DURATION);
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      setRevealFactor(1f - factor);
    });
    animator.addListener(endListener);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    return animator;
  }

  @Override
  public void modifyBaseHideAnimator (ValueAnimator animator) {

  }

  @Override
  public boolean shouldOverrideShowAnimation () {
    return false;
  }


  private void setRevealFactor (float factor) {
    if (popupLayout != null) {
      popupLayout.setRevealFactor(factor);
    }
    setContentFactor(factor);
  }

  private void setContentFactor (float factor) {
    if (controller == null) return;
    View controllerView = controller.get();
    int height = controllerView != null ? controllerView.getMeasuredHeight() : 0;
    int y = height - (int) ((float) height * factor);
    controllerView.setTranslationY(y);
  }

  private void setSearchModeFactor (float factor) {
    if (controller == null) return;
    View controllerView = controller.get();
    int screenHeight = Screen.getDisplayHeight();
    int heightDiff = screenHeight - contentHeight;
    ViewGroup.LayoutParams params = controllerView.getLayoutParams();
    params.height = (contentHeight + (int) (heightDiff * factor));
    controllerView.setLayoutParams(params);
  }
}
