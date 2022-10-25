package org.thunderdog.challegram.helper;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.ForceTouchView;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;

public abstract class ForceTouchPreviewDelegate implements ClickHelper.Delegate {

  public abstract void onLongPress(View view, float x, float y);

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    if (awaitingSlideOff) {
      boolean ok = false;
      if (customControllerProvider != null) {
        ok = customControllerProvider.onSlideOff(view, x, y, currentOpenPreview);
      } else if (slideOffListener != null) {
        ok = slideOffListener.onSlideOff(view, x, y, currentOpenPreview);
      }
      if (ok) {
        awaitingSlideOff = false;
        closePreview(view);
      }
    }
    if (currentOpenPreview != null) {
      UI.getContext(view.getContext()).processForceTouchMoveEvent(x, y, startX, startY);
    }
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (needForceTouch(view, x, y) && customControllerProvider != null) {
      ViewController<?> controller = customControllerProvider.createForceTouchPreview(view, x, y);
      if (controller != null) {
        if (controller.needAsynchronousAnimation()) {
          openPreviewAsync(view, controller, x, y);
        } else {
          openPreview(view, controller, x, y);
        }
      }
    }
    return false;
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    cancelAsyncPreview();
    closePreview(view);
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    if (view.getParent() != null) {
      view.getParent().requestDisallowInterceptTouchEvent(false);
    }
    awaitingSlideOff = false;
    closePreview(view);
  }

  @Override
  public long getLongPressDuration () {
    return ViewConfiguration.getLongPressTimeout();
  }

  @Override
  public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  public interface ActionListProvider {
    ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target);
  }

  public interface CustomControllerProvider {
    boolean needsForceTouch (View v, float x, float y);
    boolean onSlideOff (View v, float x, float y, @Nullable ViewController<?> openPreview);
    ViewController<?> createForceTouchPreview (View v, float x, float y);
  }

  public interface SlideOffListener {
    boolean onSlideOff (View v, float x, float y, @Nullable ViewController<?> openPreview);
  }

  private @Nullable SlideOffListener slideOffListener;
  private @Nullable ActionListProvider actionListProvider;
  private @Nullable CustomControllerProvider customControllerProvider;
  private ForceTouchView.MaximizeListener maximizeListener;
  private final Tdlib tdlib;
  private ViewController<?> pendingController;
  private CancellableRunnable pendingTask;
  private ViewController<?> currentOpenPreview;
  private boolean awaitingSlideOff;

  public ForceTouchPreviewDelegate (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void setCustomControllerProvider (@Nullable CustomControllerProvider customControllerProvider) {
    this.customControllerProvider = customControllerProvider;
  }

  public final void setSlideOffListener (@Nullable SlideOffListener slideOffListener) {
    this.slideOffListener = slideOffListener;
  }

  public final void setPreviewActionListProvider (@Nullable ActionListProvider provider) {
    this.actionListProvider = provider;
  }

  public final void setMaximizeListener (ForceTouchView.MaximizeListener maximizeListener) {
    this.maximizeListener = maximizeListener;
  }

  public boolean needForceTouch (View view, final float x, final float y) {
    if (!Config.FORCE_TOUCH_ENABLED) {
      return false;
    }
    return customControllerProvider != null && customControllerProvider.needsForceTouch(view, x, y);
  }

  public void openPreviewAsync (final View view, final ViewController<?> controller, final float x, final float y) {
    cancelAsyncPreview();
    controller.setInForceTouchMode(true);
    if (controller.wouldHideKeyboardInForceTouchMode()) {
      UI.getContext(view.getContext()).hideSoftwareKeyboard();
    }
    pendingController = controller;
    pendingTask = new CancellableRunnable() {
      @Override
      public void act () {
        if (pendingController == controller) {
          pendingController = null;
          pendingTask = null;
          openPreview(view, controller, x, y);
        }
      }
    };
    pendingTask.removeOnCancel(UI.getAppHandler());
    controller.scheduleAnimation(pendingTask, 600l);
    controller.get();
  }

  public void cancelAsyncPreview () {
    if (pendingController != null) {
      pendingController.destroy();
      pendingController = null;
    }
    if (pendingTask != null) {
      pendingTask.cancel();
      pendingTask = null;
    }
  }

  public void openPreview (View view, ViewController<?> controller, float x, float y) {
    ViewController<?> ancestor = ViewController.findAncestor(view);
    if ((ancestor != null && tdlib != null && ancestor.tdlib() != null && ancestor.tdlib().id() != tdlib.id())) {
      return;
    }
    ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, view, controller.get(), controller);
    context.setStateListener(controller);
    context.setTouch(x, y);

    if (controller instanceof ForceTouchView.PreviewDelegate) {
      ((ForceTouchView.PreviewDelegate) controller).onPrepareForceTouchContext(context);
    }

    if (maximizeListener != null) {
      context.setMaximizeListener(maximizeListener);
    }

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    StringList strings = new StringList(5);

    if (actionListProvider != null) {
      ForceTouchView.ActionListener listener =
        actionListProvider.onCreateActions(view, context, ids, icons, strings, controller);
      context.setButtons(listener, controller, ids.get(), icons.get(), strings.get());
    }

    if (UI.getContext(view.getContext()).openForceTouch(context)) {
      currentOpenPreview = controller;
      awaitingSlideOff = true;
      onLongPress(view, x, y);
      if (view.getParent() != null) {
        view.getParent().requestDisallowInterceptTouchEvent(true);
      }
    } else {
      controller.destroy();
    }
  }

  public void closePreview (View view) {
    if (currentOpenPreview != null) {
      UI.getContext(view.getContext()).closeForceTouch();
      currentOpenPreview = null;
    }
  }
}
