package org.pytgcalls.ntgcallsx;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.tool.Screen;

public class VoIPFloatingLayout extends FrameLayout {
  public final static int STATE_GONE = 0;
  public final static int STATE_FULLSCREEN = 1;
  public final static int STATE_FLOATING = 2;

  private final float FLOATING_MODE_SCALE = 0.23f;
  float starX;
  float starY;
  float startMovingFromX;
  float startMovingFromY;
  boolean moving;

  int lastH;
  int lastW;
  float touchSlop;

  final Path path = new Path();
  final RectF rectF = new RectF();

  public float relativePositionToSetX = -1f;
  float relativePositionToSetY = -1f;

  private float leftPadding;
  private float rightPadding;
  private float topPadding;
  private float bottomPadding;

  float toFloatingModeProgress = 0;

  private boolean floatingMode;
  private boolean setedFloatingMode;
  private boolean switchingToFloatingMode;
  public boolean measuredAsFloatingMode;

  private boolean active = true;
  public boolean isAppearing;
  public boolean alwaysFloating;
  public float savedRelativePositionX;
  public float savedRelativePositionY;
  public float updatePositionFromX;
  public float updatePositionFromY;
  public boolean switchingToPip;

  OnClickListener tapListener;

  private VoIPFloatingLayoutDelegate delegate;

  public interface VoIPFloatingLayoutDelegate {
    void onChange(float progress, boolean value);
  }

  ValueAnimator switchToFloatingModeAnimator;
  private final ValueAnimator.AnimatorUpdateListener progressUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
      toFloatingModeProgress = (float) valueAnimator.getAnimatedValue();
      if (delegate != null) {
        delegate.onChange(toFloatingModeProgress, measuredAsFloatingMode);
      }
      invalidate();
    }
  };

  public VoIPFloatingLayout (@NonNull Context context) {
    super(context);
    touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new ViewOutlineProvider() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline(View view, Outline outline) {
          if (!floatingMode) {
            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
          } else {
            outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), floatingMode ? Screen.dp(4) : 0);
          }
        }
      });
      setClipToOutline(true);
    }
  }

  @Override
  protected void dispatchDraw(@NonNull Canvas canvas) {
    boolean animated = false;
    if (updatePositionFromX >= 0) {
      if(!isAppearing) {
        animate().setListener(null).cancel();
      }
      setTranslationX(updatePositionFromX);
      setTranslationY(updatePositionFromY);
      if(!isAppearing) {
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
      }
      updatePositionFromX = -1f;
      updatePositionFromY = -1f;
    }

    if (relativePositionToSetX >= 0 && floatingMode && getMeasuredWidth() > 0) {
      setRelativePositionInternal(relativePositionToSetX, relativePositionToSetY, getMeasuredWidth(), getMeasuredHeight(), animated);
      relativePositionToSetX = -1f;
      relativePositionToSetY = -1f;
    }
    super.dispatchDraw(canvas);

    if (!switchingToFloatingMode && floatingMode != setedFloatingMode) {
      setFloatingMode(setedFloatingMode, true);
    }
    if (switchingToFloatingMode) {
      invalidate();
    }
  }

  private void setRelativePositionInternal(float xRelative, float yRelative, int width, int height, boolean animated) {
    ViewParent parent = getParent();
    if (parent == null || !floatingMode || switchingToFloatingMode || !active) {
      return;
    }

    float xPoint = leftPadding + (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - width) * xRelative;
    float yPoint = topPadding + (((View) parent).getMeasuredHeight() - bottomPadding - topPadding - height) * yRelative;

    if (animated) {
      animate().setListener(null).cancel();
      animate().scaleX(1f).scaleY(1f)
        .translationX(xPoint)
        .translationY(yPoint)
        .alpha(1f)
        .setStartDelay(0)
        .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    } else {
      if (!alwaysFloating) {
        animate().setListener(null).cancel();
        setScaleX(1f);
        setScaleY(1f);
        animate().alpha(1f).setDuration(150).start();
      }
      setTranslationX(xPoint);
      setTranslationY(yPoint);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    measuredAsFloatingMode = false;
    if (floatingMode) {
      width = (int) (((float) width) * FLOATING_MODE_SCALE);
      height = (int) (((float) height) * FLOATING_MODE_SCALE);
      measuredAsFloatingMode = true;
    } else {
      if (!switchingToPip) {
        setTranslationX(0);
        setTranslationY(0);
      }
    }
    if (delegate != null) {
      delegate.onChange(toFloatingModeProgress, measuredAsFloatingMode);
    }

    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

    if (getMeasuredHeight() != lastH && getMeasuredWidth() != lastW) {
      path.reset();
      rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
      path.addRoundRect(rectF, Screen.dp(4), Screen.dp(4), Path.Direction.CW);
      path.toggleInverseFillType();
    }
    lastH = getMeasuredHeight();
    lastW = getMeasuredWidth();

    updatePadding();
  }

  private void updatePadding() {
    leftPadding = Screen.dp(16);
    rightPadding = Screen.dp(16);
    topPadding = Screen.dp(166);
    bottomPadding = Screen.dp(166);
  }

  @SuppressLint("Recycle")
  public void setFloatingMode(boolean show, boolean animated) {
    if (getMeasuredWidth() <= 0 || getVisibility() != View.VISIBLE) {
      animated = false;
    }
    if (!animated) {
      if (floatingMode != show) {
        floatingMode = show;
        setedFloatingMode = show;
        toFloatingModeProgress = floatingMode ? 1f : 0f;
        requestLayout();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          invalidateOutline();
        }
      }
      return;
    }
    if (switchingToFloatingMode) {
      setedFloatingMode = show;
      return;
    }
    if (show && !floatingMode) {
      floatingMode = true;
      setedFloatingMode = true;
      updatePadding();
      if (relativePositionToSetX >= 0) {
        setRelativePositionInternal(relativePositionToSetX, relativePositionToSetY,
          (int) (getMeasuredWidth() * FLOATING_MODE_SCALE), (int) (getMeasuredHeight() * FLOATING_MODE_SCALE), false);
      }
      floatingMode = false;
      switchingToFloatingMode = true;
      float toX = getTranslationX();
      float toY = getTranslationY();
      setTranslationX(0);
      setTranslationY(0);
      invalidate();
      if (switchToFloatingModeAnimator != null) {
        switchToFloatingModeAnimator.cancel();
      }
      switchToFloatingModeAnimator = ValueAnimator.ofFloat(toFloatingModeProgress, 1f);
      switchToFloatingModeAnimator.addUpdateListener(progressUpdateListener);
      switchToFloatingModeAnimator.setDuration(300);
      switchToFloatingModeAnimator.start();
      animate().setListener(null).cancel();
      animate().scaleX(FLOATING_MODE_SCALE).scaleY(FLOATING_MODE_SCALE)
        .translationX(toX - (getMeasuredWidth() - getMeasuredWidth() * FLOATING_MODE_SCALE) / 2f)
        .translationY(toY - (getMeasuredHeight() - getMeasuredHeight() * FLOATING_MODE_SCALE) / 2f)
        .alpha(1f)
        .setStartDelay(0)
        .setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            switchingToFloatingMode = false;
            floatingMode = true;
            updatePositionFromX = toX;
            updatePositionFromY = toY;
            requestLayout();

          }
        }).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    } else if (!show && floatingMode) {
      setedFloatingMode = false;
      float fromX = getTranslationX();
      float fromY = getTranslationY();
      updatePadding();
      floatingMode = false;
      switchingToFloatingMode = true;
      requestLayout();
      animate().setListener(null).cancel();
      getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
          if (!measuredAsFloatingMode) {
            if (switchToFloatingModeAnimator != null) {
              switchToFloatingModeAnimator.cancel();
            }
            switchToFloatingModeAnimator = ValueAnimator.ofFloat(toFloatingModeProgress, 0f);
            switchToFloatingModeAnimator.addUpdateListener(progressUpdateListener);
            switchToFloatingModeAnimator.setDuration(300);
            switchToFloatingModeAnimator.start();

            float fromXFinal = fromX - (getMeasuredWidth() - getMeasuredWidth() * FLOATING_MODE_SCALE) / 2f;
            float fromYFinal = fromY - (getMeasuredHeight() - getMeasuredHeight() * FLOATING_MODE_SCALE) / 2f;
            getViewTreeObserver().removeOnPreDrawListener(this);
            setTranslationX(fromXFinal);
            setTranslationY(fromYFinal);
            setScaleX(FLOATING_MODE_SCALE);
            setScaleY(FLOATING_MODE_SCALE);
            animate().setListener(null).cancel();
            animate().setListener(new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                switchingToFloatingMode = false;
                requestLayout();
              }
            }).scaleX(1f).scaleY(1f).translationX(0).translationY(0).alpha(1f).setDuration(300).setStartDelay(0).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
          } else {
            floatingMode = false;
            requestLayout();
          }
          return false;
        }
      });
    } else {
      toFloatingModeProgress = floatingMode ? 1f : 0f;
      setedFloatingMode = floatingMode;
      requestLayout();
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return true;
  }

  long startTime;

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    ViewParent parent = getParent();
    if (!floatingMode || switchingToFloatingMode || !active) {
      return false;
    }
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (floatingMode && !switchingToFloatingMode) {
          startTime = System.currentTimeMillis();
          starX = event.getX() + getX();
          starY = event.getY() + getY();
          animate().setListener(null).cancel();
          animate().scaleY(1.05f).scaleX(1.05f).alpha(1f).setStartDelay(0).start();
        }
        break;
      case MotionEvent.ACTION_MOVE:
        float dx = event.getX() + getX() - starX;
        float dy = event.getY() + getY() - starY;
        if (!moving && (dx * dx + dy * dy) > touchSlop * touchSlop) {
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          moving = true;
          starX = event.getX() + getX();
          starY = event.getY() + getY();
          startMovingFromX = getTranslationX();
          startMovingFromY = getTranslationY();
          dx = 0;
          dy = 0;
        }
        if (moving) {
          setTranslationX(startMovingFromX + dx);
          setTranslationY(startMovingFromY + dy);
        }
        break;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        if (parent != null && floatingMode && !switchingToFloatingMode) {
          parent.requestDisallowInterceptTouchEvent(false);
          animate().setListener(null).cancel();
          ViewPropertyAnimator animator = animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0);

          if (tapListener != null && !moving && System.currentTimeMillis() - startTime < 200) {
            tapListener.onClick(this);
          }

          int parentWidth = ((View) getParent()).getMeasuredWidth();
          int parentHeight = ((View) getParent()).getMeasuredHeight();

          if (getX() < leftPadding) {
            animator.translationX(leftPadding);
          } else if (getX() + getMeasuredWidth() > parentWidth - rightPadding) {
            animator.translationX(parentWidth - getMeasuredWidth() - rightPadding);
          }

          if (getY() < topPadding) {
            animator.translationY(topPadding);
          } else if (getY() + getMeasuredHeight() > parentHeight - bottomPadding) {
            animator.translationY(parentHeight - getMeasuredHeight() - bottomPadding);
          }
          animator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        moving = false;
        break;
    }
    return true;
  }

  public void restoreRelativePosition() {
    updatePadding();
    if (savedRelativePositionX >= 0 && !switchingToFloatingMode) {
      setRelativePositionInternal(savedRelativePositionX, savedRelativePositionY, getMeasuredWidth(), getMeasuredHeight(), true);
      savedRelativePositionX = -1f;
      savedRelativePositionY = -1f;
    }
  }

  public void saveRelativePosition() {
    if (getMeasuredWidth() > 0 && relativePositionToSetX < 0) {
      ViewParent parent = getParent();
      if (parent == null) {
        return;
      }
      savedRelativePositionX = (getTranslationX() - leftPadding) / (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - getMeasuredWidth());
      savedRelativePositionY = (getTranslationY() - topPadding) / (((View) parent).getMeasuredHeight() - bottomPadding - topPadding - getMeasuredHeight());
      savedRelativePositionX = Math.max(0, Math.min(1, savedRelativePositionX));
      savedRelativePositionY = Math.max(0, Math.min(1, savedRelativePositionY));
    } else {
      savedRelativePositionX = -1f;
      savedRelativePositionY = -1f;
    }
  }

  public void setRelativePosition(float x, float y) {
    ViewParent parent = getParent();
    if (!floatingMode || parent == null || ((View) parent).getMeasuredWidth() > 0 || getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
      relativePositionToSetX = x;
      relativePositionToSetY = y;
    } else {
      setRelativePositionInternal(x, y, getMeasuredWidth(), getMeasuredHeight(), true);
    }
  }

  public void setRelativePosition(VoIPFloatingLayout fromLayout) {
    ViewParent parent = getParent();
    if (parent == null) {
      return;
    }
    updatePadding();

    float xRelative = (fromLayout.getTranslationX() - leftPadding) / (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - fromLayout.getMeasuredWidth());
    float yRelative = (fromLayout.getTranslationY() - topPadding) / (((View) parent).getMeasuredHeight() - bottomPadding - topPadding - fromLayout.getMeasuredHeight());

    xRelative = Math.min(1f, Math.max(0, xRelative));
    yRelative = Math.min(1f, Math.max(0, yRelative));

    setRelativePosition(xRelative, yRelative);
  }

  public void setOnTapListener(OnClickListener tapListener) {
    this.tapListener = tapListener;
  }

  public void setIsActive(boolean value) {
    active = value;
  }

  public void setDelegate(VoIPFloatingLayoutDelegate delegate) {
    this.delegate = delegate;
  }
}
