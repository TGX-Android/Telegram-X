package org.thunderdog.challegram.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

public class ReactionAnimationOverlay {
  private Activity activity;
  private WindowManager wm;
  private FrameLayout windowView;
  private int runningAnimationsCount;
  private ArrayList<CallbackRecord> pendingEndCallbacks = new ArrayList<>();
  private ArrayList<Runnable> pendingEndRunnables = new ArrayList<>();
  private boolean endingAllAnimations;

  public ReactionAnimationOverlay (ViewController<?> chat) {
    activity = chat.context();
    wm = activity.getWindowManager();
  }

  private void createAndShowWindow () {
    if (windowView != null)
      return;
    windowView = new FrameLayout(activity);
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
    lp.width = lp.height = WindowManager.LayoutParams.MATCH_PARENT;
    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
    lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
    lp.format = PixelFormat.TRANSLUCENT;
    lp.token = activity.getWindow().getDecorView().getWindowToken();
    wm.addView(windowView, lp);
  }

  private void removeWindow () {
    if (windowView == null)
      return;
    wm.removeView(windowView);
    windowView = null;
  }

  public void playLottieAnimation (@NotNull ViewBoundsProvider pos, @NotNull LottieAnimation animation, @Nullable Runnable onStarting, @Nullable AnimationEndCallback onDone) {
    if (endingAllAnimations)
      return;
    createAndShowWindow();
    Rect rect = new Rect();
    if (!pos.getBounds(rect) || rect.isEmpty())
      return;
    LottieAnimationDrawable drawable = new LottieAnimationDrawable(animation, 500, 500);
    ImageView img = new ImageView(activity);
    img.setImageDrawable(drawable);
    img.setTranslationX(rect.left);
    img.setTranslationY(rect.top);
    windowView.addView(img, new FrameLayout.LayoutParams(rect.width(), rect.height(), Gravity.TOP | Gravity.LEFT));
    runningAnimationsCount++;
    CallbackRecord cbRecord;
    if (onDone != null)
      pendingEndCallbacks.add(cbRecord = new CallbackRecord(onDone, img));
    else
      cbRecord = null;
    img.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      private boolean started;
      private boolean removed;

      @Override
      public boolean onPreDraw () {
        if (removed) {
          return false;
        }

        if (!started) {
          if (onStarting != null)
            onStarting.run();
          drawable.start();
          started = true;
        }

        boolean boundsValid = true;
        if (!drawable.isRunning() || !(boundsValid = pos.getBounds(rect))) {
          img.getViewTreeObserver().removeOnPreDrawListener(this);
          Runnable remover = () -> {
            if (removed || windowView == null)
              return;
            removed = true;
            windowView.removeView(img);
            runningAnimationsCount--;
            if (runningAnimationsCount == 0) {
              removeWindow();
            }
          };
          if (cbRecord != null)
            pendingEndCallbacks.remove(cbRecord);
          if (onDone != null && boundsValid)
            onDone.onAnimationEnd(img, remover);
          else
            remover.run();
          return false;
        }
        img.setTranslationX(rect.left);
        img.setTranslationY(rect.top);

        return true;
      }
    });
  }

  public void playFlyingReactionAnimation (@NotNull ViewBoundsProvider dstPos, @NotNull Rect srcPos, @NotNull Drawable drawable, @Nullable Runnable onStarting, @Nullable Runnable onDone) {
    createAndShowWindow();
    FrameLayout wrapper = new FrameLayout(activity); // Needed to move the whole thing around in response to scrolling
    ImageView iv = new ImageView(activity);
    iv.setImageDrawable(drawable);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(srcPos.width(), srcPos.height(), Gravity.LEFT | Gravity.TOP);
    lp.topMargin = -srcPos.height() / 2;
    lp.leftMargin = -srcPos.width() / 2;
    wrapper.addView(iv, lp);
    windowView.addView(wrapper);
    runningAnimationsCount++;

    if (onDone != null)
      pendingEndRunnables.add(onDone);

    iv.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      private boolean started;
      private int startX, startY;
      private Rect rect = new Rect();

      @Override
      public boolean onPreDraw () {
        if (!dstPos.getBounds(rect)) {
          iv.getViewTreeObserver().removeOnPreDrawListener(this);
          windowView.removeView(wrapper);
          if (onDone != null) {
            pendingEndRunnables.remove(onDone);
            onDone.run();
          }
          runningAnimationsCount--;
          if (runningAnimationsCount == 0) {
            removeWindow();
          }
          return false;
        }
        if (!started) {
          started = true;
          if (onStarting != null)
            onStarting.run();

          float srcX = srcPos.centerX(), srcY = srcPos.centerY();
          float dstX = rect.centerX(), dstY = rect.centerY();
          float slopeHeight = Math.min(Screen.dp(100), Math.abs(srcY - dstY) * 0.25f);
          startX = rect.centerX();
          startY = rect.centerY();

          AnimatorSet set = new AnimatorSet();
          ArrayList<Animator> anims = new ArrayList<>();
          if (Build.VERSION.SDK_INT >= 21) {
            Path path = new Path();
            path.moveTo(srcX, srcY);
            path.cubicTo(srcX, srcY, interpolate(srcX, dstX, .25f), Math.min(dstY, srcY) - slopeHeight, interpolate(srcX, dstX, .5f), Math.min(dstY, srcY) - slopeHeight);
            path.cubicTo(interpolate(srcX, dstX, .75f), Math.min(dstY, srcY) - slopeHeight, dstX, dstY, dstX, dstY);
            anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, View.TRANSLATION_Y, path));
          } else {
            anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_X, srcX, dstX));
            anims.add(ObjectAnimator.ofFloat(iv, View.TRANSLATION_Y, srcY, dstY));
          }
          float scale = rect.width() / (float) srcPos.width();
          anims.add(ObjectAnimator.ofFloat(iv, View.SCALE_X, scale));
          anims.add(ObjectAnimator.ofFloat(iv, View.SCALE_Y, scale));
          set.playTogether(anims);
          set.setDuration(400);
          set.setInterpolator(new AccelerateInterpolator());
          set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd (Animator animation) {
              if (windowView == null)
                return;
              windowView.removeView(wrapper);
              if (onDone != null) {
                pendingEndRunnables.remove(onDone);
                onDone.run();
              }
              runningAnimationsCount--;
              if (runningAnimationsCount == 0) {
                removeWindow();
              }
            }
          });
          set.start();
        } else {
          wrapper.setTranslationX(rect.centerX() - startX);
          wrapper.setTranslationY(rect.centerY() - startY);
        }

        return true;
      }
    });
  }

  private static float interpolate (float x1, float x2, float k) {
    return x1 * (1f - k) + x2 * k;
  }

  public void endAllAnimations () {
    if (windowView != null) {
      endingAllAnimations = true;
      for (Runnable r : pendingEndRunnables) {
        r.run();
      }
      pendingEndRunnables.clear();
      for (CallbackRecord cb : pendingEndCallbacks) {
        cb.callback.onAnimationEnd(cb.view, () -> {
        });
      }
      pendingEndCallbacks.clear();
      removeWindow();
      runningAnimationsCount = 0;
      endingAllAnimations = false;
    }
  }

  public boolean isEndingAllAnimations () {
    return endingAllAnimations;
  }

  @FunctionalInterface
  public interface ViewBoundsProvider {
    boolean getBounds (Rect outRect);
  }

  @FunctionalInterface
  public interface AnimationEndCallback {
    void onAnimationEnd (View view, Runnable remove);
  }

  private static class CallbackRecord {
    public AnimationEndCallback callback;
    public View view;

    public CallbackRecord (AnimationEndCallback callback, View view) {
      this.callback = callback;
      this.view = view;
    }
  }
}
