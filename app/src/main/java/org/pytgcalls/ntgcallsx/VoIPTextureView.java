package org.pytgcalls.ntgcallsx;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.animation.ValueAnimator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;
import org.webrtc.VideoFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class VoIPTextureView extends FrameLayout {
  boolean isCamera;
  final boolean applyRotation;

  float roundRadius;

  private boolean screencast;
  boolean isFirstFrameRendered, runAnimation;

  public final TextureViewRenderer renderer;
  public final ImageView imageView;
  public View backgroundView;
  private FrameLayout screencastView;
  private ImageView screencastImage;
  private TextView screencastText;

  public Bitmap cameraLastBitmap;
  public float stubVisibleProgress = 1f;

  boolean animateOnNextLayout;
  long animateNextDuration;
  ArrayList<Animator> animateOnNextLayoutAnimations = new ArrayList<>();
  int animateFromHeight;
  int animateFromWidth;

  float animateFromY;
  float animateFromX;

  float clipVertical;
  float clipHorizontal;
  float currentClipVertical;
  float currentClipHorizontal;

  float animateFromScale = 1f;
  float animateFromRendererW;

  public float scaleTextureToFill;

  public static int SCALE_TYPE_NONE = 3;
  public static int SCALE_TYPE_FILL = 0;
  public static int SCALE_TYPE_FIT = 1;
  public static int SCALE_TYPE_ADAPTIVE = 2;

  public int scaleType;

  ValueAnimator currentAnimation;

  boolean clipToTexture;
  public float animationProgress;

  public static final int TRANSITION_DURATION = 350;

  public VoIPTextureView(@NonNull Context context, boolean isCamera, boolean applyRotation) {
    this(context, isCamera, applyRotation, true);
  }

  @SuppressLint("SetTextI18n")
  public VoIPTextureView(@NonNull Context context, boolean isCamera, boolean applyRotation, boolean applyRoundRadius) {
    super(context);
    this.isCamera = isCamera;
    this.applyRotation = applyRotation;
    imageView = new ImageView(context);
    imageView.setVisibility(View.GONE);
    renderer = new TextureViewRenderer(context) {
      @Override
      protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
      }
    };
    renderer.setFpsReduction(30);
    renderer.setEnableHardwareScaler(true);
    renderer.setMirror(isCamera);
    if (!isCamera && applyRotation) {
      backgroundView = new View(context);
      backgroundView.setBackgroundColor(0xff1b1f23);
      addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

      renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
      addView(renderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    } else if (!isCamera) {
      addView(renderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    } else {
      addView(renderer);
    }

    addView(imageView);

    screencastView = new FrameLayout(getContext());
    screencastView.setBackground(getLayerDrawable());
    //screencastView.setBackgroundColor(0xff1b1f23);
    addView(screencastView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    screencastView.setVisibility(GONE);

    screencastImage = new ImageView(getContext());
    screencastImage.setScaleType(ImageView.ScaleType.CENTER);
    screencastImage.setImageResource(R.drawable.screencast_big);
    screencastView.addView(screencastImage, LayoutHelper.createFrame(82, 82, Gravity.CENTER, 0, 0, 0, 60));

    screencastText = new TextView(getContext());
    screencastText.setText("Sharing your screen");
    screencastText.setGravity(Gravity.CENTER);
    screencastText.setLineSpacing(Screen.dp(2), 1.0f);
    screencastText.setTextColor(0xffffffff);
    screencastText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    screencastText.setTypeface(Fonts.getRobotoMedium());
    screencastView.addView(screencastText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 21, 28, 21, 0));

    if (applyRoundRadius) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setOutlineProvider(new ViewOutlineProvider() {
          @TargetApi(Build.VERSION_CODES.LOLLIPOP)
          @Override
          public void getOutline(View view, Outline outline) {
            if (roundRadius < 1) {
              outline.setRect((int) currentClipHorizontal, (int) currentClipVertical, (int) (view.getMeasuredWidth() - currentClipHorizontal), (int) (view.getMeasuredHeight() - currentClipVertical));
            } else {
              outline.setRoundRect((int) currentClipHorizontal, (int) currentClipVertical, (int) (view.getMeasuredWidth() - currentClipHorizontal), (int) (view.getMeasuredHeight() - currentClipVertical), roundRadius);
            }
          }
        });
        setClipToOutline(true);
      }
    }

    if (isCamera) {
      if (cameraLastBitmap == null) {
        try {
          File file = new File(TdlibManager.getTgvoipDirectory(), "voip_icthumb.jpg");
          cameraLastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {
        }
      }
      imageView.setVisibility(View.VISIBLE);
      showLastFrame();
    }

    if (!applyRotation) {
      Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
      renderer.setRotation(display.getRotation());
    }

  }

  private static GradientDrawable getLayerDrawable () {
    GradientDrawable shape = new GradientDrawable();
    shape.setShape(GradientDrawable.RECTANGLE);
    int[] colors = {0xff212E3A, 0xff2B5B4D, 0xff245863, 0xff274558};
    shape.setGradientType(GradientDrawable.LINEAR_GRADIENT);
    shape.setOrientation(GradientDrawable.Orientation.BL_TR);
    shape.setColors(colors);
    shape.setGradientCenter(0.5f, 0.5f);
    shape.setUseLevel(true);
    shape.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
    return shape;
  }

  public void setIsCamera(boolean isCamera) {
    renderer.setMirror(isCamera);
    renderer.setIsCamera(isCamera);
    this.isCamera = isCamera;
    imageView.setVisibility(isCamera ? View.VISIBLE : View.GONE);
  }

  public void showWaitFrame() {
    if (isCamera) {
      saveCameraLastBitmap();
      showLastFrame();
      isFirstFrameRendered = false;
      runAnimation = true;
      stubVisibleProgress = 0f;
      imageView.invalidate();
      imageView.setVisibility(View.VISIBLE);
    }
  }

  private void showLastFrame() {
    if (cameraLastBitmap == null) {
      imageView.setImageResource(R.drawable.icplaceholder);
    } else {
      imageView.setImageBitmap(cameraLastBitmap);
    }
    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
  }

  public void saveCameraLastBitmap() {
    if (isCamera) {
      cameraLastBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888);
      cameraLastBitmap = renderer.getBitmap(cameraLastBitmap);
      U.blurBitmap(cameraLastBitmap, 3, 1);
      try {
        File file = new File(TdlibManager.getTgvoipDirectory(), "voip_icthumb.jpg");
        FileOutputStream stream = new FileOutputStream(file);
        cameraLastBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        stream.close();
      } catch (Throwable ignored) {}
    }
  }

  @Override
  protected void dispatchDraw(@NonNull Canvas canvas) {
    super.dispatchDraw(canvas);
    if (runAnimation && isCamera) {
      if (isFirstFrameRendered) {
        stubVisibleProgress -= 16f / 150f;
        if (stubVisibleProgress <= 0) {
          stubVisibleProgress = 0;
          imageView.setVisibility(View.GONE);
          runAnimation = false;
        } else {
          invalidate();
          imageView.setAlpha(stubVisibleProgress);
        }
      } else {
        if (stubVisibleProgress == 0) {
          imageView.setVisibility(View.VISIBLE);
        }
        stubVisibleProgress += 16f / 150f;
        if (stubVisibleProgress >= 1) {
          stubVisibleProgress = 1;
          imageView.setAlpha(1f);
          runAnimation = false;
        } else {
          invalidate();
          imageView.setAlpha(stubVisibleProgress);
        }
      }
    }
  }

  boolean ignoreLayout;
  @Override
  public void requestLayout() {
    if (ignoreLayout) {
      return;
    }
    super.requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (!applyRotation) {
      ignoreLayout = true;
      Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
      renderer.setRotation(display.getRotation());
      ignoreLayout = false;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);

    if (scaleType == SCALE_TYPE_NONE) {
      return;
    }

    if (renderer.getMeasuredHeight() == 0 || renderer.getMeasuredWidth() == 0 || getMeasuredHeight() == 0 || getMeasuredWidth() == 0) {
      scaleTextureToFill = 1f;
      if (currentAnimation == null && !animateOnNextLayout) {
        currentClipHorizontal = 0;
        currentClipVertical = 0;
      }
    } else if (scaleType == SCALE_TYPE_FILL) {
      scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
    } else if (scaleType == SCALE_TYPE_ADAPTIVE) {
      if (Math.abs(getMeasuredHeight() / (float) getMeasuredWidth() - 1f) < 0.02f) {
        scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
      } else {
        if (getMeasuredWidth() > getMeasuredHeight() && renderer.getMeasuredHeight() > renderer.getMeasuredWidth()) {
          scaleTextureToFill = Math.max(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), (getMeasuredWidth() / 2f ) / (float) renderer.getMeasuredWidth());
        } else {
          scaleTextureToFill = Math.min(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
        }
      }
    } else if (scaleType == SCALE_TYPE_FIT) {
      scaleTextureToFill = Math.min(getMeasuredHeight() / (float) renderer.getMeasuredHeight(), getMeasuredWidth() / (float) renderer.getMeasuredWidth());
      if (clipToTexture && !animateWithParent && currentAnimation == null && !animateOnNextLayout) {
        currentClipHorizontal = (getMeasuredWidth() - renderer.getMeasuredWidth()) / 2f;
        currentClipVertical = (getMeasuredHeight() - renderer.getMeasuredHeight()) / 2f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          invalidateOutline();
        }
      }
    }

    if (animateOnNextLayout) {
      animateFromScale /= renderer.getMeasuredWidth() / animateFromRendererW;
      animateOnNextLayout = false;
      float translationY, translationX;
      if (animateWithParent && getParent() != null) {
        View parent = (View) getParent();
        translationY = animateFromY - parent.getTop();
        translationX = animateFromX - parent.getLeft();
      } else {
        translationY = animateFromY - getTop();
        translationX = animateFromX - getLeft();
      }
      clipVertical = 0;
      clipHorizontal = 0;
      if (animateFromHeight != getMeasuredHeight()) {
        clipVertical = (getMeasuredHeight() - animateFromHeight) / 2f;
        translationY -= clipVertical;
      }
      if (animateFromWidth != getMeasuredWidth()) {
        clipHorizontal = (getMeasuredWidth() - animateFromWidth) / 2f;
        translationX -= clipHorizontal;
      }
      setTranslationY(translationY);
      setTranslationX(translationX);

      if (currentAnimation != null) {
        currentAnimation.removeAllListeners();
        currentAnimation.cancel();
      }
      renderer.setScaleX(animateFromScale);
      renderer.setScaleY(animateFromScale);

      currentClipVertical = clipVertical;
      currentClipHorizontal = clipHorizontal;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
      invalidate();
      float fromScaleFinal = animateFromScale;

      currentAnimation = ValueAnimator.ofFloat(1f, 0);
      float finalTranslationX = translationX;
      float finalTranslationY = translationY;
      currentAnimation.addUpdateListener(animator -> {
        float v = (float) animator.getAnimatedValue();
        animationProgress = (1f - v);
        currentClipVertical = v * clipVertical;
        currentClipHorizontal = v * clipHorizontal;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          invalidateOutline();
        }
        invalidate();

        float s = fromScaleFinal * v + scaleTextureToFill * (1f - v);
        renderer.setScaleX(s);
        renderer.setScaleY(s);

        setTranslationX(finalTranslationX * v);
        setTranslationY(finalTranslationY * v);
      });
      if (animateNextDuration != 0) {
        currentAnimation.setDuration(animateNextDuration);
      } else {
        currentAnimation.setDuration(TRANSITION_DURATION);
      }
      currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
      currentAnimation.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          currentClipVertical = 0;
          currentClipHorizontal = 0;

          renderer.setScaleX(scaleTextureToFill);
          renderer.setScaleY(scaleTextureToFill);

          setTranslationY(0);
          setTranslationX(0);

          currentAnimation = null;
        }
      });
      currentAnimation.start();
      if (!animateOnNextLayoutAnimations.isEmpty()) {
        for (int i = 0; i < animateOnNextLayoutAnimations.size(); i++) {
          animateOnNextLayoutAnimations.get(i).start();
        }
      }
      animateOnNextLayoutAnimations.clear();
      animateNextDuration = 0;
    } else {
      if (currentAnimation == null) {
        renderer.setScaleX(scaleTextureToFill);
        renderer.setScaleY(scaleTextureToFill);
      }
    }
  }

  boolean animateWithParent;

  public void setAnimateWithParent(boolean b) {
    animateWithParent = b;
  }

  public void stopCapturing() {
    if (renderer != null) {
      saveCameraLastBitmap();
      showLastFrame();
      isFirstFrameRendered = false;
      runAnimation = true;
      renderer.release();
      imageView.setAlpha(1f);
      if (isCamera) {
        imageView.setVisibility(View.VISIBLE);
      }
      stubVisibleProgress = 1f;
      animateFromScale = 1f;
    }
  }

  public void onFrame(VideoFrame frame) {
    if (!isFirstFrameRendered && !runAnimation) {
      isFirstFrameRendered = true;
      runAnimation = true;
      renderer.onFirstFrameRendered();
    }
    if (renderer != null) {
      renderer.onFrame(frame);
    }
  }

  public void setIsScreencast(boolean value) {
    screencast = value;
    screencastView.setVisibility(screencast ? VISIBLE : GONE);
    if (screencast) {
      renderer.setVisibility(GONE);
      imageView.setVisibility(GONE);
    } else {
      renderer.setVisibility(VISIBLE);
    }
  }

  public void setScreenShareMiniProgress(float progress, boolean value) {
    if (!screencast) {
      return;
    }
    float scale = ((View) getParent()).getScaleX();
    screencastText.setAlpha(1.0f - progress);
    float sc;
    if (!value) {
      sc = 1.0f / scale - 0.4f / scale * progress;
    } else {
      sc = 1.0f - 0.4f * progress;
    }
    screencastImage.setScaleX(sc);
    screencastImage.setScaleY(sc);
    screencastImage.setTranslationY(Screen.dp(60) * progress);
  }
}
