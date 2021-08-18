package me.vkryl.android;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;

public final class ViewUtils {
  public static void onClick (View v) {
    if (v != null) {
      v.playSoundEffect(SoundEffectConstants.CLICK);
    }
  }

  public static void setBackground (View view, Drawable drawable) {
    if (view != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        view.setBackground(drawable);
      } else {
        //noinspection deprecation
        view.setBackgroundDrawable(drawable);
      }
    }
  }

  public static String getActionName (MotionEvent e) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return MotionEvent.actionToString(e.getAction());
    } else {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: return "ACTION_DOWN";
        case MotionEvent.ACTION_UP: return "ACTION_UP";
        case MotionEvent.ACTION_MOVE: return "ACTION_MOVE";
        case MotionEvent.ACTION_CANCEL: return "ACTION_CANCEL";
      }
      return "ACTION:" + e.getAction();
    }
  }

  public static void runJustBeforeBeingDrawn (final View view, final Runnable runnable) {
    final ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw() {
        view.getViewTreeObserver().removeOnPreDrawListener(this);
        runnable.run();
        return true;
      }
    };
    view.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
  }

  public static void hapticVibrate (View view, boolean isForce, boolean ignoreSetting) {
    if (view != null) {
      view.performHapticFeedback(isForce ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP, ignoreSetting ? HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING : 0);
    }
  }

  public static void fixViewPager (View pager) {
    // Whenever ViewPager gets re-attached to window (e.g. inside RecyclerView while scrolling),
    // first touch event gets processed improperly
    try {
      Method method = pager.getClass().getDeclaredMethod("resetTouch");
      method.setAccessible(true);
      method.invoke(pager);
    } catch (Throwable ignored) { }
    // pager.requestLayout();
  }
}
