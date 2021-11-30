package org.thunderdog.challegram.ui.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageStrictCache;
import org.thunderdog.challegram.mediaview.MediaSelectDelegate;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.camera.legacy.CameraManagerLegacy;
import org.thunderdog.challegram.ui.camera.x.CameraManagerX;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.unit.BitwiseUtils;

/**
 * Date: 8/8/17
 * Author: default
 */

public class CameraController extends ViewController<Void> implements CameraDelegate, SensorEventListener, FactorAnimator.Target, View.OnClickListener, CameraButton.RecordListener, CameraOverlayView.FlashListener, Settings.SettingsChangeListener {
  public static final String[] VIDEO_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? new String[] {
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE
  } : new String[] {
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  public static final String[] VIDEO_ONLY_PERMISSIONS = new String[] {
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE
  };

  private static final boolean ALLOW_EARLY_INITIALIZATION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public interface ReadyListener {
    void onCameraCompletelyReady (CameraController camera);
    void onCameraSwitched (boolean isForward, boolean toFrontFace);
  }

  public interface QrCodeListener {
    void onQrCodeScanned (String qrCode);
  }

  public static final int MODE_MAIN = 0;
  public static final int MODE_ROUND_VIDEO = 1;
  public static final int MODE_QR = 2;

  private boolean forceLegacy;
  private int cameraMode;
  private @Nullable ReadyListener readyListener;
  private @Nullable QrCodeListener qrCodeListener;
  private String savedQrCodeData;
  private boolean qrCodeConfirmed;
  private int qrSubtitleRes;
  private boolean qrModeDebug;

  public void setQrListener (@Nullable QrCodeListener qrCodeListener, @StringRes int subtitleRes, boolean qrModeDebug) {
    this.qrCodeListener = qrCodeListener;
    this.qrSubtitleRes = subtitleRes;
    this.qrModeDebug = qrModeDebug;
    if (this.cameraMode == MODE_QR && rootLayout != null) {
      rootLayout.setQrModeSubtitle(subtitleRes);
      rootLayout.setQrMode(true, qrModeDebug);
    }
  }

  public void setMode (int mode, @Nullable ReadyListener readyListener) {
    this.qrCodeConfirmed = false;
    this.readyListener = readyListener;
    if (this.cameraMode == mode) {
      if (this.cameraMode == MODE_QR) {
        rootLayout.setQrMode(true, false);
      }

      return;
    }
    setForceLegacy(mode == MODE_ROUND_VIDEO || mode == MODE_QR);
    this.cameraMode = mode;
    if (contentView != null) {
      updateContentScale();
      updateQrButtonHide();
      if (cameraOverlayView != null) {
        cameraOverlayView.setGridVisible(cameraMode == MODE_MAIN && Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_SHOW_GRID), isFocused());
      }
    }
  }

  @Override
  public int getId () {
    return R.id.controller_camera;
  }

  // == View ===

  private final CameraUiHandler handler = new CameraUiHandler(this);

  private CameraRootLayout rootLayout;
  private CameraLayout contentView;
  private CameraOverlayView cameraOverlayView;
  private CameraFadeView fadeView;
  private TextView errorView;
  private ShadowView contentShadow;

  private CameraBlurView blurView;
  private CameraButton button;
  private CameraControlButton switchCameraButton, flashButton;
  private FrameLayoutFix switchCameraButtonParent;
  private TextView durationView;

  private CameraManager<?> manager;

  public CameraController (Context context) {
    super(context, null);
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new CameraLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    contentView.setParent(this);

    cameraOverlayView = new CameraOverlayView(context);
    cameraOverlayView.setFlashListener(this);
    cameraOverlayView.setGridVisible(cameraMode == MODE_MAIN && Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_SHOW_GRID), false);
    cameraOverlayView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    fadeView = new CameraFadeView(context);

    blurView = new CameraBlurView(context);
    button = new CameraButton(context);
    button.setBlurView(blurView);
    button.setParent(this);
    button.setRecordListener(this);

    switchCameraButton = new CameraControlButton(context);
    switchCameraButton.setOnClickListener(this);
    switchCameraButton.setNeedParentTranslation(true);
    switchCameraButton.setId(R.id.btn_camera_switch);
    switchCameraButtonParent = new FrameLayoutFix(context);
    switchCameraButtonParent.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f)));
    switchCameraButtonParent.addView(switchCameraButton);

    flashButton = new CameraControlButton(context);
    flashButton.setId(R.id.btn_camera_flash);
    flashButton.setOnClickListener(this);
    flashButton.setIconRes(R.drawable.baseline_flash_off_24);

    durationView = new NoScrollTextView(context);
    durationView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    durationView.setTypeface(Fonts.getRobotoMedium());
    durationView.setTextColor(0xffffffff);
    durationView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
    Views.setSimpleShadow(durationView);
    resetDuration();

    rootLayout = new CameraQrCodeRootLayout(context);
    rootLayout.setController(this);
    rootLayout.setBackgroundColor(0xff000000);
    rootLayout.addView(contentView);

    rootLayout.addView(switchCameraButtonParent);
    rootLayout.addView(flashButton);
    rootLayout.addView(blurView);
    rootLayout.addView(button);
    rootLayout.addView(durationView);

    rootLayout.addView(fadeView);

    contentShadow = new ShadowView(context);
    contentShadow.setSimpleBottomTransparentShadow(true);
    rootLayout.addView(contentShadow);

    if (needLegacy()) {
      manager = new CameraManagerLegacy(context, this);
    } else {
      manager = new CameraManagerX(context, this);
    }

    contentView.addView(manager.getView());
    switchCameraButton.setCameraIconRes(manager.preferFrontFacingCamera());
    contentView.addView(cameraOverlayView);
    
    updateQrButtonHide();
    updateControlMargins();
    updateControlsFactor();

    Settings.instance().addNewSettingsListener(this);

    return rootLayout;
  }

  @Override
  public void onSettingsChanged (long newSettings, long oldSettings) {
    cameraOverlayView.setGridVisible(cameraMode == MODE_MAIN && BitwiseUtils.getFlag(newSettings, Settings.SETTING_FLAG_CAMERA_SHOW_GRID), isFocused());
  }

  public boolean isLegacy () {
    return manager instanceof CameraManagerLegacy;
  }

  private boolean needLegacy () {
    if (forceLegacy || !Config.CAMERA_X_AVAILABLE)
      return true;
    int type = Settings.instance().getCameraType();
    return type == Settings.CAMERA_TYPE_SYSTEM ? Settings.CAMERA_TYPE_DEFAULT == Settings.CAMERA_TYPE_LEGACY : type == Settings.CAMERA_TYPE_LEGACY;
  }

  private void setForceLegacy (boolean forceLegacy) {
    if (this.forceLegacy != forceLegacy) {
      this.forceLegacy = forceLegacy;
      checkLegacyMode();
    }
  }

  public void checkLegacyMode () {
    if (contentView != null && isLegacy() != needLegacy()) {
      if (manager != null) {
        contentView.removeView(this.manager.getView());
        manager.destroy();
        manager = null;
      }
      if (needLegacy()) {
        manager = new CameraManagerLegacy(context, this);
      } else {
        manager = new CameraManagerX(context, this);
      }
      contentView.addView(manager.getView(), contentView.getChildCount() - 1);
    }
  }

  public void takeCameraLayout (ViewGroup toGroup, int index) {
    get();
    Views.moveView(contentView, toGroup, index);
    manager.getView().requestLayout();
  }

  public void releaseCameraLayout () {
    get();
    Views.moveView(contentView, rootLayout, 0);
    manager.getView().requestLayout();
  }

  public boolean isInQrScanMode () {
    return cameraMode == MODE_QR;
  }

  public CameraLayout getCameraLayout () {
    return contentView;
  }

  private boolean useFastInitialization;

  public void setUseFastInitialization (boolean useFastInitialization) {
    if (this.useFastInitialization != useFastInitialization) {
      this.useFastInitialization = useFastInitialization;
      cameraOverlayView.setNeedFastAnimations(useFastInitialization);
    }
  }

  public CameraManager<?> getManager () {
    return manager;
  }

  public CameraManagerLegacy getLegacyManager () {
    return (CameraManagerLegacy) manager;
  }

  @SuppressWarnings("SetTextI18n")
  private void resetDuration () {
    durationView.setText("0:00");
  }

  public void switchCamera () {
    if (!isSwitchingCamera) {
      manager.switchCamera();
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_camera_switch: {
        switchCamera();
        break;
      }
      case R.id.btn_camera_flash: {
        manager.switchFlashMode();
        break;
      }
    }
  }

  private static final float CONTROLS_SCALE = .65f;

  private float controlsFactor = -1f;
  private float videoFactor = 0;

  public void setVideoFactor (float factor) {
    if (this.videoFactor != factor) {
      this.videoFactor = factor;
      updateControlsStyles();
    }
  }

  private void updateControlsFactor () {
    setControlsFactor(appearFactor);
  }

  private void updateControlsStyles () {
    float factor = appearFactor;
    float scale = CONTROLS_SCALE + (1f - CONTROLS_SCALE) * factor;

    blurView.setScaleX(scale);
    blurView.setScaleY(scale);
    blurView.setAlpha(factor);

    button.setScaleX(scale);
    button.setScaleY(scale);
    button.setAlpha(factor);

    float controlsFactor = factor * (1f - videoFactor);

    scale = .7f + .3f * controlsFactor;

    switchCameraButton.setScaleX(scale);
    switchCameraButton.setScaleY(scale);
    switchCameraButtonParent.setAlpha(controlsFactor);

    flashButton.setScaleX(scale);
    flashButton.setScaleY(scale);
    flashButton.setAlpha(controlsFactor);

    float durationFactor = factor * videoFactor;

    scale = .7f + .3f * durationFactor;
    durationView.setScaleX(scale);
    durationView.setScaleY(scale);
    durationView.setAlpha(durationFactor);
  }

  private void setControlsFactor (float factor) {
    if (this.controlsFactor != factor) {
      this.controlsFactor = factor;
      updateControlsStyles();
    }
  }

  /*@Override
  public void onSpin (CameraControlButton v, float rotate, float scale) {
    this.contentView.setRotationY(rotate);
    this.contentScale = scale;
    updateContentScale();
  }*/

  private boolean setMargins (View view, FrameLayoutFix.LayoutParams params, int anchorGravity, int margin, int oppositeMargin) {
    switch (anchorGravity) {
      case Gravity.TOP:
        return Views.setMargins(params, oppositeMargin > 0 ? oppositeMargin : 0, margin - view.getPaddingTop() + marginTop, oppositeMargin < 0 ? -oppositeMargin : 0, 0);
      case Gravity.BOTTOM:
        return Views.setMargins(params, oppositeMargin > 0 ? oppositeMargin : 0, 0, oppositeMargin < 0 ? -oppositeMargin : 0, margin - view.getPaddingBottom() + marginBottom);
      case Gravity.LEFT:
        return Views.setMargins(params, margin - view.getPaddingLeft() + marginLeft, 0, 0, 0);
      case Gravity.RIGHT:
        return Views.setMargins(params, 0, 0, margin - view.getPaddingRight() + marginRight, 0);
    }
    return false;
  }

  private static int makeCenter (int gravity) {
    return (gravity == Gravity.TOP || gravity == Gravity.BOTTOM) ? gravity | Gravity.CENTER_HORIZONTAL : gravity | Gravity.CENTER_VERTICAL;
  }

  private void setLayoutParams (View view, int anchorGravity, int gravity, int margin, int oppositeMargin) {
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) view.getLayoutParams();
    boolean gravitySet = Views.setGravity(params, gravity);
    boolean marginsSet = setMargins(view, params, anchorGravity, margin, oppositeMargin);
    if (gravitySet || marginsSet) {
      view.setLayoutParams(params);
    }
  }

  public void checkDisplayRotation () {
    manager.checkDisplayRotation();
    updateControlMargins();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void updateControlMargins () {
    int bottomGravity;
    int topGravity;
    switch (manager.getDisplayRotation()) {
      case 180:
        bottomGravity = Gravity.TOP;
        topGravity = Gravity.BOTTOM;
        break;
      case 90:
        bottomGravity = Gravity.RIGHT;
        topGravity = Gravity.LEFT;
        break;
      case 270:
        bottomGravity = Gravity.LEFT;
        topGravity = Gravity.RIGHT;
        break;
      case 0:
      default:
        bottomGravity = Gravity.BOTTOM;
        topGravity = Gravity.TOP;
        break;
    }

    int bottomGravityCentered = makeCenter(bottomGravity);
    int topGravityCentered = makeCenter(topGravity);

    setLayoutParams(durationView, topGravity, topGravityCentered, Screen.dp(18f), 0);
    setLayoutParams(button, bottomGravity, bottomGravityCentered, Screen.dp(40f), 0);
    setLayoutParams(blurView, bottomGravity, bottomGravityCentered, Screen.dp(18f), 0);
    setLayoutParams(switchCameraButtonParent, bottomGravity, bottomGravityCentered, Screen.dp(40f), 0);
    setLayoutParams(flashButton, bottomGravity, bottomGravityCentered, Screen.dp(40f), 0);
    if (bottomGravity != Gravity.RIGHT) {
      switchCameraButton.setAlignGravity(Gravity.RIGHT);
      flashButton.setAlignGravity(Gravity.LEFT);
    } else {
      switchCameraButton.setAlignGravity(Gravity.LEFT);
      flashButton.setAlignGravity(Gravity.RIGHT);
    }
  }

  // == Listener ==

  @AnyThread
  @Override
  @SuppressWarnings("WrongThread")
  public void displayFatalErrorMessage (String msg) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_ERROR, msg));
      return;
    }

    if (StringUtils.isEmpty(msg)) {
      if (errorView != null && errorView.getParent() != null) {
        contentView.removeView(errorView);
      }
      return;
    }

    if (errorView == null) {
      errorView = new NoScrollTextView(context());
      errorView.setTextColor(0xffffffff);
      errorView.setTypeface(Fonts.getRobotoRegular());
      errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      errorView.setGravity(Gravity.CENTER);
      errorView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
      Views.setSimpleShadow(errorView);
    }

    if (errorView.getParent() == null) {
      contentView.addView(errorView);
    }
    errorView.setText(msg);
  }

  @AnyThread
  @Override
  public void resolveExpectedError (@CameraError.Code int code) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_RESOLVE_ERROR, code, 0));
      return;
    }

    switch (code) {
      case CameraError.NOT_ENOUGH_SPACE: {
        // TODO
        break;
      }
    }
  }

  private int availableCameraCount = -1;

  @AnyThread
  @Override
  @SuppressWarnings("WrongThread")
  public void onAvailableCamerasCountChanged (int availableCameraCount) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_CHANGE_CAMERA_COUNT, availableCameraCount, 0));
      return;
    }

    this.availableCameraCount = availableCameraCount;
    if (availableCameraCount == -1) {
      return;
    }

    boolean visible = availableCameraCount > 1;
    if (!isInQrScanMode()) switchCameraButton.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  private boolean hasRenderedFrame;

  public boolean hasRenderedFrame () {
    return hasRenderedFrame;
  }

  @Override
  public void onRenderedFirstFrame () {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_RENDER_STATE_CHANGED, 1, 0));
      return;
    }

    hasRenderedFrame = true;
    cameraOverlayView.setOverlayVisible(false, !isPaused(), null);

    if (readyListener != null) {
      readyListener.onCameraCompletelyReady(this);
    }

    executeScheduledAnimation();
  }

  @Override
  public void onDisplayRotationChanged () {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_UPDATE_ROTATIONS));
      return;
    }

    resetFakeRotation();
  }

  @Override
  public void onResetRenderState (boolean needBlur, Runnable after) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_RENDER_STATE_CHANGED, 0, needBlur ? 1 : 0, after));
      return;
    }

    if (hasRenderedFrame) {
      Bitmap blurredBitmap = manager.takeBlurredPreview();
      cameraOverlayView.setPreview(blurredBitmap, context().getWindowRotationDegrees());
    }

    hasRenderedFrame = false;
    cameraOverlayView.setOverlayVisible(true, !isPaused(), after);

    if (isInQrScanMode()) {
      rootLayout.onCameraClosed();
    }

    executeScheduledAnimation();
  }

  private int flashMode = CameraFeatures.FEATURE_FLASH_OFF;

  @Override
  public void onFlashModeChanged (int flashMode) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_FLASH_MODE_CHANGED, flashMode, 0));
      return;
    }

    this.flashMode = flashMode;

    int res;
    switch (flashMode) {
      case CameraFeatures.FEATURE_FLASH_ON:
      case CameraFeatures.FEATURE_FLASH_FAKE:
        res = R.drawable.baseline_flash_on_24;
        break;
      case CameraFeatures.FEATURE_FLASH_AUTO:
        res = R.drawable.baseline_flash_auto_24;
        break;
      case CameraFeatures.FEATURE_FLASH_OFF:
      default:
        res = R.drawable.baseline_flash_off_24;
        break;
    }
    flashButton.changeIcon(res);
  }

  @Override
  public void onZoomChanged (float zoom) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_ZOOM_CHANGED, Float.floatToIntBits(zoom), 0));
      return;
    }
    button.setActualZoom(zoom);
  }

  private boolean isSwitchingCamera;

  @Override
  public void onCameraSourceChange (boolean completed, boolean isForward, boolean toFrontFace) {
    if (!UI.inUiThread()) {
      int flags = 0;
      if (isForward) {
        flags |= 1;
      }
      if (toFrontFace) {
        flags |= 2;
      }
      handler.sendMessage(Message.obtain(handler, ACTION_CAMERA_SOURCE_CHANGED, completed ? 1 : 0, flags));
      return;
    }

    isSwitchingCamera = !completed;

    if (!completed) {
      switchCameraButton.spinAround(isForward, toFrontFace);
      if (readyListener != null) {
        readyListener.onCameraSwitched(isForward, toFrontFace);
      }
    }
  }

  @Override
  public void onPerformSuccessHint (boolean isVideo) {
    if (!UI.inUiThread()) {
      handler.sendMessage(Message.obtain(handler, ACTION_PERFORM_SUCCESS_HINT, isVideo ? 1 : 0, 0));
      return;
    }

    blurView.performSuccessHint();
  }

  // == State ==

  private static final float MINIMIZED_SCALE = .9f;

  private boolean willProbablyFocus;

  /**
   * @param willProbablyFocus Means that even if {@link #isFocused()} returns false, camera may become focused again.
   * */
  public void setWillProbablyFocus (boolean willProbablyFocus) {
    if (this.willProbablyFocus != willProbablyFocus) {
      this.willProbablyFocus = willProbablyFocus;

    }
  }

  private float contentScale = 1f;
  private float appearFactor;

  private void updateContentScale () {
    float scale = cameraMode == MODE_ROUND_VIDEO ? 1f : (MINIMIZED_SCALE + (1f - MINIMIZED_SCALE) * appearFactor) * contentScale;
    contentView.setScaleX(scale);
    contentView.setScaleY(scale);
  }

  private void updateQrButtonHide () {
    if (isInQrScanMode()) {
      switchCameraButton.setVisibility(View.GONE);
      flashButton.setVisibility(View.GONE);
      blurView.setVisibility(View.GONE);
      rootLayout.setQrMode(true, qrModeDebug);
      rootLayout.setQrModeSubtitle(qrSubtitleRes);
    } else {
      switchCameraButton.setVisibility(View.VISIBLE);
      flashButton.setVisibility(View.VISIBLE);
      blurView.setVisibility(View.VISIBLE);
      rootLayout.setQrMode(false, qrModeDebug);
    }
  }

  /**
   * Called during layout animation,
   * where 0 - camera is fully hidden, 1 - camera is fully visible.
   *
   * Probably we can start camera initialization at factor >= .8f,
   * but this is pretty dangerous, since it can cause frame drops.
   *
   * Frame drops might be not a problem when byDrag == false.
   * */
  public void setAppearFactor (float factor, boolean byDrag, boolean isGrowing) {
    this.appearFactor = factor;
    fadeView.setFadeFactor(1f - factor);
    updateContentScale();
    updateControlsFactor();
  }

  /**
   * Called during layout animation or update to layout content shadow under content
   * */
  public void setContentShadowTop (float top) {
    int threshold = Screen.getStatusBarHeight();
    float alpha = top <= threshold ? (threshold == 0 ? 0f : top / (float) threshold) : 1f;
    contentShadow.setAlpha(alpha);
    contentShadow.setTranslationY(top);
  }

  private int marginLeft, marginTop, marginRight, marginBottom;
  /**
   * Called during layout to update control margins
   * */
  public void setControlMargins (int left, int top, int right, int bottom) {
    if (marginLeft != left || marginTop != top || marginRight != right || marginBottom != bottom) {
      marginLeft = left;
      marginTop = top;
      marginRight = right;
      marginBottom = bottom;
      updateControlMargins();
    }
  }

  private boolean inEarlyInitialization;

  public void setInEarlyInitialization () {
    inEarlyInitialization = ALLOW_EARLY_INITIALIZATION || manager.isCameraActive();
  }

  private boolean isPrepared;
  private ViewController<?> outputController;

  public void setOutputController (ViewController<?> c) {
    this.outputController = c;
  }

  /**
   * Called when camera is not yet even attached to view tree,
   * right before adding to root view.
   * */
  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (outputController == null) {
      throw new IllegalStateException();
    }
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onPrepareToShow");
    }
    isPrepared = true;
    resetFakeRotation();
    resetCameraButton();
    checkDisplayRotation();
    checkSensors();

    if (inEarlyInitialization) {
      openCamera();
    }
  }

  private void resetControls () {
    flashButton.setIconRes(R.drawable.baseline_flash_off_24);
    boolean frontFace = manager.preferFrontFacingCamera();
    switchCameraButton.forceSpin(frontFace);
    switchCameraButton.setCameraIconRes(frontFace);
  }

  /**
   * Called when camera is detached from view root.
   *
   * It will not be used again until {@link #onPrepareToShow} is called.
   * */
  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onCleanAfterHide");
    }

    isPrepared = false;

    setWillProbablyFocus(false);

    checkSensors();
    resetFakeRotation();

    displayFatalErrorMessage(null);
    resetControls();
    cameraOverlayView.resetOverlayState();

    if (inEarlyInitialization) {
      closeCamera();
    }

    manager.resetPreferences();
  }

  /**
   * Called when camera is fully visible to user,
   * good point to unlock any camera controls.
   * */
  @Override
  public void onFocus () {
    super.onFocus();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onFocus");
    }
    if (cameraMode == MODE_MAIN && Settings.instance().needTutorial(Settings.TUTORIAL_HOLD_VIDEO)) {
      Settings.instance().markTutorialAsShown(Settings.TUTORIAL_HOLD_VIDEO);
      context().tooltipManager().builder(button).controller(this).show(tdlib, R.string.CameraButtonHint).hideDelayed();
    }

    if (inEarlyInitialization) {
      inEarlyInitialization = false;
    } else {
      openCamera();
    }
  }

  /**
   * Called when camera is about to be hidden,
   * good point to
   * */
  @Override
  public void onBlur () {
    super.onBlur();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onBlur");
    }
    if (inEarlyInitialization) {
      executeScheduledAnimation();
    } else {
      closeCamera();
    }
  }

  // == Game sensor ==

  private SensorManager sensorManager;
  private Sensor accelerometerSensor;

  private void prepareSensors () {
    if (Config.CAMERA_ALLOW_FAKE_ROTATION) {
      if (sensorManager == null) {
        try {
          sensorManager = (SensorManager) context().getSystemService(Context.SENSOR_SERVICE);
          accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
          if (accelerometerSensor == null) {
            Log.i(Log.TAG_CAMERA, "Accelerometer sensor is not available");
          }
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Unable to setup accelerometer sensor", t);
        }
      }
    }
  }

  private void registerSensorsImpl () {
    if (sensorManager != null) {
      sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
  }

  private void unregisterSensorsImpl () {
    if (sensorManager != null) {
      try {
        sensorManager.unregisterListener(this);
      } catch (Throwable ignored) { }
    }
  }

  private boolean accelerometerActive, rotationActive;

  private void checkSensors () {
    boolean register = supportsCustomRotations() && !isPaused() && !isDestroyed() && isPrepared;
    // setAccelerometerActive(register);
    setRotationActive(register);
  }

  private OrientationEventListener orientationListener;

  private static final int DEGREES = 28;

  private static int normalizeRotation (int degrees) {
    if (degrees <= 90 - DEGREES || degrees >= 270 + DEGREES)
      return 0;
    if (degrees > 90 - DEGREES && degrees < 90 + DEGREES)
      return 270;
    if (degrees > 180 + DEGREES && degrees < 270 + DEGREES)
      return 90;
    return 180;
  }

  private void initOrientationListener () {
    if (orientationListener == null) {
      orientationListener = new OrientationEventListener(context(), SensorManager.SENSOR_DELAY_NORMAL) {
        @Override
        public void onOrientationChanged (int orientation) {
          if (orientation != ORIENTATION_UNKNOWN && orientation <= 360 && !isUiBlocked) {
            setRotation(normalizeRotation(orientation), true);
          }
        }
      };
    }
  }

  private void setRotationActive (boolean active) {
    if (this.rotationActive != active) {
      this.rotationActive = active;
      if (orientationListener == null) {
        initOrientationListener();
      }
      if (active) {
        orientationListener.enable();
      } else {
        orientationListener.disable();
      }
    }
  }

  private void setAccelerometerActive (boolean registered) {
    if (this.accelerometerActive == registered) {
      return;
    }

    boolean success = false;

    if (registered) {
      if (Config.CAMERA_ALLOW_FAKE_ROTATION) {
        try {
          registerSensorsImpl();
          success = true;
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Cannot register camera sensors");
          unregisterSensorsImpl();
        }
      }
    } else {
      success = true;
      unregisterSensorsImpl();
    }

    if (success) {
      this.accelerometerActive = registered;
    }
  }

  @Override
  public void onSensorChanged (SensorEvent e) {
    int degrees;
    if (Math.abs(e.values[1]) > Math.abs(e.values[0])) {
      //Mainly portrait
      if (e.values[1] > 1) {
        degrees = 0;
      } else if (e.values[1] < -1) {
        degrees = 180; // Inverse
      } else {
        return;
      }
    } else {
      //Mainly landscape
      if (e.values[0] > 1) {
        degrees = 90; // Right side up
      } else if (e.values[0] < -1) {
        degrees = 270; // Left side up
      } else {
        return;
      }
    }

    setRotation(degrees, true);
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int accuracy) { }

  private int fakeRotation;
  private int actualRotation = -1;

  private void setRotation (int rotation, boolean delayed) {
    if (rotation == 180) {
      return;
    }

    if (this.actualRotation == rotation && delayed) {
      return;
    }

    this.actualRotation = rotation;

    if (delayed) {
      handler.removeMessages(ACTION_CHANGE_ORIENTATION);
      handler.sendMessageDelayed(Message.obtain(handler, ACTION_CHANGE_ORIENTATION, rotation, 0), 800l);
      return;
    }

    if (isUiBlocked) {
      return;
    }

    int fakeRotation;
    switch (manager.getDisplayRotation()) {
      case 90: // left
        switch (rotation) {
          case 0:
            fakeRotation = -90;
            break;
          case 270:
            fakeRotation = -180;
            break;
          case 90:
          default:
            fakeRotation = 0;
            break;
        }
        break;
      case 270: // right
        switch (rotation) {
          case 0:
            fakeRotation = 90;
            break;
          case 90:
            fakeRotation = 180;
            break;
          case 270:
          default:
            fakeRotation = 0;
            break;
        }
        break;

      case 0:
      default:
        switch (rotation) {
          case 90:
            fakeRotation = 90;
            break;
          case 270:
            fakeRotation = -90;
            break;
          case 0:
          default:
            fakeRotation = 0;
        }
    }

    if (this.fakeRotation != fakeRotation) {
      if (Log.isEnabled(Log.TAG_CAMERA)) {
        Log.i(Log.TAG_CAMERA, "Fake rotation: %d -> %d, pictureRotation: %d", this.fakeRotation, fakeRotation, getPictureExifRotation());
      }

      this.fakeRotation = fakeRotation;
      animateFakeRotation(fakeRotation);
    }
  }

  private int getPictureExifRotation () {
    return MathUtils.modulo(fakeRotation, 360);
  }

  private int getPictureRotation () {
    return manager.getDisplayRotation() + fakeRotation;
  }

  private void resetCameraButton () {
    switchCameraButton.setCameraIconRes(manager.preferFrontFacingCamera());
  }

  private void resetFakeRotation () {
    if (rotationAnimator != null) {
      rotationAnimator.forceFactor(0f);
    }
    this.fakeRotation = 0;
    actualRotation = -1;
    updateRotations();
  }

  private FactorAnimator rotationAnimator;
  private static final int ANIMATOR_ROTATION = 0;

  private void animateFakeRotation (int rotation) {
    if (rotationAnimator != null) {
      rotationAnimator.cancel();
    }

    boolean animated = isPrepared && !isPaused() && !isDestroyed();

    if (animated) {
      if (rotationAnimator == null) {
        rotationAnimator = new FactorAnimator(ANIMATOR_ROTATION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 0f);
      }
      rotationAnimator.animateTo(rotation);
    } else {
      if (rotationAnimator != null) {
        rotationAnimator.forceFactor(rotation);
      }
      updateRotations();
    }
  }

  public float getTransformedRotation () {
    return rotationAnimator != null ? rotationAnimator.getFactor() : 0f;
  }

  private void updateRotations () {
    float rotation = getTransformedRotation();
    rootLayout.setComponentRotation(rotation);
    button.setComponentRotation(rotation);
    switchCameraButtonParent.setRotation(rotation);
    flashButton.setComponentRotation(rotation);
    durationView.setRotation(rotation);
  }

  private int getOutputRotation () {
    return getFakeResultOrientation(false);
    // return Utils.modulo(manager.getDisplayRotation() + fakeRotation, 360);
  }

  private int getFakeResultOrientation (boolean forOutput) {
    int currentRotation = manager.getDisplayRotation();

    int resultRotation;
    if (forOutput) {
      switch (currentRotation) {
        case 0:
          resultRotation = fakeRotation == 90f ? 270 : fakeRotation == -90f ? 90 : 0;
          break;
        case 270:
          resultRotation = fakeRotation == 90 ? 270 : fakeRotation == 180 ? 180 : 0;
          break;
        case 90:
          resultRotation = fakeRotation == -90 ? 90 : fakeRotation == -180 ? 180 : 0;
          break;
        default:
          return -1;
      }
    } else {
      switch (currentRotation) {
        case 0:
          resultRotation = fakeRotation == 90f ? 90 : fakeRotation == -90f ? 270 : 0;
          break;
        case 270:
          resultRotation = fakeRotation == 90f ? 0 : fakeRotation == 180f ? 90 : 270;
          break;
        case 90:
          resultRotation = fakeRotation == -90f ? 0 : fakeRotation == -180f ? 270 : 0;
          break;
        default:
          return -1;
      }
    }

    return resultRotation;
  }

  private boolean applyFakeRotation () {
    float rotation = getTransformedRotation();
    if (rotation != 0f) {
      checkDisplayRotation();
      int resultRotation = getFakeResultOrientation(false);
      if (resultRotation == -1) {
        return false;
      }

      int requestedOrientation;
      switch (resultRotation) {
        case 0:
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
          break;
        case 270:
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
          break;
        case 90:
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
          break;
        default:
          return false;
      }

      int prevOrientation = context().getCurrentOrientation();
      context().lockOrientation(requestedOrientation);
      checkDisplayRotation();
      return (prevOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) != (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    return false;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ROTATION:
        updateRotations();
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ROTATION:
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
          applyFakeRotation();
        }
        break;
    }
  }

  // == Camera ==

  private boolean isCameraOpen;

  private void openCamera () {
    if (!isCameraOpen) {
      isCameraOpen = true;
      checkCameraOpen();
    }
  }

  private void closeCamera () {
    if (isCameraOpen) {
      isCameraOpen = false;
      checkCameraOpen();
    }
  }

  @Override
  public void onMultiWindowModeChanged (boolean inMultiWindowMode) {
    super.onMultiWindowModeChanged(inMultiWindowMode);
    checkCameraOpen();
  }

  private boolean __cameraOpen;
  private MediaViewController viewController;
  private MediaItem workingWithItem;
  private Bitmap workingWithBitmap;

  private void checkCameraOpen () {
    boolean isOpen = isCameraOpen && viewController == null; // !inMultiWindowMode()
    if (__cameraOpen != isOpen) {
      __cameraOpen = isOpen;
      if (isOpen) {
        manager.openCamera();
      } else {
        manager.closeCamera();
        cameraOverlayView.setOverlayVisible(true, !isPaused(), null);
      }
    }
  }

  // == Activity ==

  /**
   * Destroy camera in whatever state
   */
  @Override
  public void destroy () {
    super.destroy();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "destroy");
    }
    Settings.instance().removeNewSettingsListener(this);
    if (manager != null) {
      manager.destroy();
    }
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onActivityPause");
    }
    onResetRenderState(true, null);
    manager.pauseCamera();
    checkSensors();
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    checkDisplayRotation();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (Log.isEnabled(Log.TAG_CAMERA)) {
      Log.i(Log.TAG_CAMERA, "onActivityResume");
    }
    manager.resumeCamera();
    checkSensors();
  }

  // == API ==

  /**
   * Returns whether user is recording video right now.
   * */
  public boolean isRecording () {
    return manager.isCapturingVideo();
  }

  /**
   * Returns whether CameraController is capable of detecting orientation changes
   * without need in onConfigurationChanged
   */
  public boolean supportsCustomRotations () {
    if (Config.CAMERA_ALLOW_FAKE_ROTATION) {
      if (orientationListener == null) {
        initOrientationListener();
      }
      return orientationListener.canDetectOrientation();
    }
    return false;
    /*if (Config.CAMERA_ALLOW_FAKE_ROTATION) {
      if (accelerometerSensor == null) {
        prepareSensors();
      }
      return accelerometerSensor != null;
    }
    return false;*/
  }

  @Override
  public boolean usePrivateFolder () {
    return isSecretChat();
  }

  @Override
  public boolean useQrScanner () {
    return isInQrScanMode();
  }

  // Shooting & Recording

  private boolean canTakeSnapshot () {
    return Config.CAMERA_ALLOW_SNAPSHOTS && (flashMode == CameraFeatures.FEATURE_FLASH_OFF || flashMode == CameraFeatures.FEATURE_FLASH_FAKE);
  }

  private Runnable scheduledInstantResult;

  private int trimWidth, trimHeight, resultRotation;

  private void saveSizes () {
    trimWidth = contentView.getMeasuredWidth();
    trimHeight = contentView.getMeasuredHeight();
    resultRotation = getFakeResultOrientation(true);
  }

  private void takeSnapshotImpl (boolean withinFakeFlash) {
    if (!manager.isCameraActive()) {
      return;
    }
    final Bitmap result = manager.takeSnapshot();
    saveSizes();
    Runnable act = () -> {
      if (!manager.onInstantPhotoResult(result, trimWidth, trimHeight, resultRotation)) {
        manager.onSnapshotControlReturned(result);
      } else {
        workingWithBitmap = result;
      }
    };
    if (withinFakeFlash) {
      scheduledInstantResult = act;
      cameraOverlayView.releaseFakeFlash(false);
    } else {
      act.run();
    }
  }

  private MessagesController findOutputController () {
    ViewController<?> c = context.navigation().getCurrentStackItem();
    if (c instanceof MessagesController) {
      return (MessagesController) c;
    }
    return null;
  }

  private boolean isSecretChat () {
    MessagesController m = findOutputController();
    return m != null && m.isSecretChat();
  }

  private void onSendMedia (ImageGalleryFile file, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles) {
    MessagesController m = findOutputController();
    if (m != null) {
      context.forceCloseCamera();
      m.sendCompressed(file, options, disableMarkdown, asFiles);
    }
  }

  @Override
  public void onMediaTaken (final ImageGalleryFile file) {
    boolean awaitLayout = applyFakeRotation();

    Runnable act = () -> {
      Tdlib tdlib = context.currentTdlib();
      MediaViewController c = new MediaViewController(context, tdlib);
      MediaStack stack = new MediaStack(context, tdlib);
      MediaItem item = new MediaItem(context, tdlib, file);
      stack.set(item);
      MessagesController m = findOutputController();
      MediaViewController.Args args = new MediaViewController.Args(CameraController.this, MediaViewController.MODE_GALLERY, new MediaViewDelegate() {
        @Override
        public MediaViewThumbLocation getTargetLocation (int index, MediaItem item) {
          MediaViewThumbLocation location = new MediaViewThumbLocation(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight());
          location.setNoBounce();
          location.setNoPlaceholder();
          return location;
        }

        @Override
        public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) {

        }
      }, new MediaSelectDelegate() {
        @Override
        public boolean isMediaItemSelected (int index, MediaItem item) {
          return false;
        }

        @Override
        public void setMediaItemSelected (int index, MediaItem item, boolean isSelected) {

        }

        @Override
        public int getSelectedMediaCount () {
          return 0;
        }

        @Override
        public boolean canDisableMarkdown () {
          return file.canDisableMarkdown();
        }

        /*@Override
        public boolean canSendAsFile () {
          return file.canSendAsFile();
        }*/

        @Override
        public long getOutputChatId () {
          MessagesController m = findOutputController();
          return m != null ? m.getOutputChatId() : 0;
        }

        @Override
        public ArrayList<ImageFile> getSelectedMediaItems (boolean copy) {
          return null;
        }
      }, (images, options, disableMarkdown, asFiles) -> {
        ImageGalleryFile galleryFile = (ImageGalleryFile) images.get(0);
        onSendMedia(galleryFile, options, disableMarkdown, asFiles);
      }, stack).setOnlyScheduled(m != null && m.areScheduledOnly());
      if (m != null) {
        args.setReceiverChatId(m.getChatId());
      }
      c.setArguments(args);
      c.forceCameraAnimationType(true);
      c.open();
      c.addDestroyListener(() -> setOpenEditor(null, null));
      setOpenEditor(c, item);
    };

    if (false && awaitLayout) {
      scheduleAnimation(act, 100);
    } else {
      act.run();
    }
  }

  private long videoStartTime;

  private void updateVideoDuration () {
    long now = SystemClock.uptimeMillis();
    long diff = videoStartTime != 0 ? now - videoStartTime : 0;
    durationView.setText(Strings.buildDuration(diff / 1000l));
    handler.removeMessages(ACTION_UPDATE_DURATION);
    if (videoStartTime != 0) {
      long timeTillUpdate = 1000 - (diff % 1000);
      handler.sendMessageDelayed(Message.obtain(handler, ACTION_UPDATE_DURATION), timeTillUpdate);
    }
  }

  private void startDurationTimer (long startMs) {
    this.videoStartTime = startMs;
    updateVideoDuration();
  }

  @Override
  public void onVideoCaptureStarted (long startTimeMs) {
    startDurationTimer(startTimeMs);
    button.setInRecordMode(true);
  }

  @Override
  public void onQrCodeFound (String qrCodeData, @Nullable RectF boundingBox, int height, int width, int rotation, boolean isLegacyZxing) {
    if (qrCodeListener != null && !qrCodeData.isEmpty() && (qrCodeData.startsWith("tg://") || qrCodeData.startsWith(context.currentTdlib().tMeUrl())) && !qrCodeConfirmed) {
      savedQrCodeData = qrCodeData;
      rootLayout.setQrCorner(boundingBox, height, width, rotation, isLegacyZxing);
    }
  }

  @Override
  public int getCurrentCameraOrientation () {
    if (manager instanceof CameraManagerLegacy) {
      return ((CameraManagerLegacy) manager).getCurrentCameraRotation();
    } else {
      return 0; // CameraX impl won't call this anyway
    }
  }

  @Override
  public int getCurrentCameraSensorOrientation () {
    if (manager instanceof CameraManagerLegacy) {
      return ((CameraManagerLegacy) manager).getCurrentCameraSensorOrientation();
    } else {
      return 0; // CameraX impl won't call this anyway
    }
  }

  public void onQrCodeFoundAndWaited () {
    if (qrCodeListener != null && savedQrCodeData != null) {
      qrCodeListener.onQrCodeScanned(savedQrCodeData);
      savedQrCodeData = null;
      qrCodeConfirmed = true;
      context.onBackPressed();
    }
  }

  @Override
  public void onQrCodeNotFound () {
    rootLayout.resetQrCorner();
  }

  @Override
  public void onVideoCaptureEnded () {
    button.setInRecordMode(false);
  }

  public void setOpenEditor (MediaViewController c, MediaItem item) {
    if (c == null) {
      final MediaItem oldItem = this.workingWithItem;
      final boolean deleteFile = this.viewController != null && !this.viewController.isMediaSent() && (isSecretChat() || !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_KEEP_DISCARDED_MEDIA) || (oldItem != null && U.isPrivateFile(oldItem.getSourceGalleryFile().getFilePath())));
      if (oldItem != null) {
        ImageStrictCache.instance().remove(oldItem.getSourceGalleryFile());
        ImageReader.instance().post(() -> {
          if (deleteFile) {
            oldItem.deleteFiles();
          } else {
            final String path = oldItem.getSourceGalleryFile().getFilePath();
            if (!U.isPrivateFile(path)) {
              U.addToGallery(path);
            }
          }
        });
      }
      if (deleteFile && workingWithBitmap != null && !workingWithBitmap.isRecycled()) {
        manager.onSnapshotControlReturned(workingWithBitmap);
      } else {
        manager.onSnapshotControlLost();
      }
    }
    this.viewController = c;
    this.workingWithItem = item;
    checkCameraOpen();
    UI.post(() -> context().checkCameraUi(), 200);
  }

  public boolean hasOpenEditor () {
    return viewController != null;
  }

  @Override
  public void onFlashPerformed () {
    if (canTakeSnapshot()) {
      takeSnapshotImpl(true);
    } else {
      manager.setTakingPhoto(false);
      takeRegularPhoto(true);
    }
  }

  private boolean releaseFakeFlash;

  private void takeRegularPhoto (boolean withinFakeFlash) {
    if (isUiBlocked)
      return;
    saveSizes();
    this.releaseFakeFlash = withinFakeFlash;
    manager.takePhoto(trimWidth, trimHeight, resultRotation);
  }

  @Override
  public void onFlashFinished () {
    if (scheduledInstantResult != null) {
      scheduledInstantResult.run();
      scheduledInstantResult = null;
    }
  }

  @Override
  public void onTakePicture (CameraButton v) {
    if (isUiBlocked) {
      return;
    }

    if (isInQrScanMode()) {
      context.onBackPressed();
    } else if (canTakeSnapshot()) {
      manager.setTakingPhoto(true);
      if (flashMode == CameraFeatures.FEATURE_FLASH_OFF) {
        takeSnapshotImpl(false);
      } else {
        cameraOverlayView.performFakeFlash();
      }
    } else if (flashMode == CameraFeatures.FEATURE_FLASH_FAKE) {
      manager.setTakingPhoto(true);
      cameraOverlayView.performFakeFlash();
    } else {
      takeRegularPhoto(false);
    }
  }

  @Override
  public boolean onStartVideoCapture (CameraButton v) {
    if (isInQrScanMode()) return false;
    Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_HOLD_VIDEO);
    return manager.startVideoCapture(getOutputRotation());
  }

  @Override
  public void onFinishVideoCapture (CameraButton v) {
    if (isInQrScanMode()) return;
    manager.finishOrCancelVideoCapture();
  }

  private boolean isUiBlocked;

  @Override
  public void onUiBlocked (boolean isBlocked) {
    isUiBlocked = isBlocked;
    context().setCameraBlocked(isBlocked);
    if (!isBlocked && releaseFakeFlash) {
      releaseFakeFlash = false;
      cameraOverlayView.releaseFakeFlash(false);
    }
  }

  private CancellableRunnable takeVideoActor;

  private boolean cancelVolumeKeyVideoCapture () {
    if (takeVideoActor != null && takeVideoActor.isPending()) {
      takeVideoActor.cancel();
      takeVideoActor = null;
      return true;
    }
    return false;
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    if (!isCameraOpen || viewController != null || isInQrScanMode()) {
      return super.onKeyDown(keyCode, event);
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_VOLUME_DOWN:
      case KeyEvent.KEYCODE_VOLUME_UP: {
        int controlType = cameraMode != MODE_MAIN ? Settings.CAMERA_VOLUME_CONTROL_ZOOM : Settings.instance().getCameraVolumeControl();
        switch (controlType) {
          case Settings.CAMERA_VOLUME_CONTROL_SHOOT:
            if (takeVideoActor != null && takeVideoActor.isPending())
              return true;
            if (button.finishVideoCapture(false))
              return true;
            cancelVolumeKeyVideoCapture();
            takeVideoActor = new CancellableRunnable() {
              @Override
              public void act () {
                if (isCameraOpen && viewController == null) {
                  button.takeVideo();
                }
              }
            };
            runOnUiThread(takeVideoActor, ViewConfiguration.getLongPressTimeout());
            return true;
          case Settings.CAMERA_VOLUME_CONTROL_ZOOM:
            boolean isZoomingIn = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
            float minZoom = manager.getMinZoom();
            float maxZoom = manager.getMaxZoom();
            float currentZoom = manager.getCurrentZoom();
            float step = Math.max(manager.getMinZoomStep(), Math.min(1f, (maxZoom - minZoom) / 20f));
            manager.onRequestZoom(MathUtils.clamp(currentZoom + (isZoomingIn ? step : -step), minZoom, maxZoom));
            return true;
        }
        break;
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp (int keyCode, KeyEvent event) {
    if (!isCameraOpen || viewController != null || isInQrScanMode()) {
      return super.onKeyDown(keyCode, event);
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_VOLUME_DOWN:
      case KeyEvent.KEYCODE_VOLUME_UP: {
        int controlType = Settings.instance().getCameraVolumeControl();
        switch (controlType) {
          case Settings.CAMERA_VOLUME_CONTROL_SHOOT:
            if (cancelVolumeKeyVideoCapture()) {
              button.takePhoto();
            }
            return true;
          case Settings.CAMERA_VOLUME_CONTROL_ZOOM:
            // TODO stop zooming in/out
            return true;
        }
        break;
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  // UI

  private static final int ACTION_DISPATCH_ERROR = 0;
  private static final int ACTION_CHANGE_CAMERA_COUNT = 1;
  private static final int ACTION_RESOLVE_ERROR = 2;
  private static final int ACTION_RENDER_STATE_CHANGED = 3;
  private static final int ACTION_CHANGE_ORIENTATION = 4;
  private static final int ACTION_UPDATE_ROTATIONS = 5;
  private static final int ACTION_FLASH_MODE_CHANGED = 7;
  private static final int ACTION_CAMERA_SOURCE_CHANGED = 8;
  private static final int ACTION_ZOOM_CHANGED = 9;
  private static final int ACTION_PERFORM_SUCCESS_HINT = 10;
  private static final int ACTION_UPDATE_DURATION = 11;

  private static class CameraUiHandler extends Handler {
    private final CameraController context;

    public CameraUiHandler (CameraController context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case ACTION_DISPATCH_ERROR: {
          context.displayFatalErrorMessage((String) msg.obj);
          break;
        }
        case ACTION_CHANGE_CAMERA_COUNT: {
          context.onAvailableCamerasCountChanged(msg.arg1);
          break;
        }
        case ACTION_RESOLVE_ERROR: {
          context.resolveExpectedError(msg.arg1);
          break;
        }
        case ACTION_RENDER_STATE_CHANGED: {
          if (msg.arg1 == 1) {
            context.onRenderedFirstFrame();
          } else {
            context.onResetRenderState(msg.arg2 == 1, (Runnable) msg.obj);
          }
          break;
        }
        case ACTION_CHANGE_ORIENTATION: {
          context.setRotation(msg.arg1, false);
          break;
        }
        case ACTION_FLASH_MODE_CHANGED: {
          context.onFlashModeChanged(msg.arg1);
          break;
        }
        case ACTION_CAMERA_SOURCE_CHANGED: {
          context.onCameraSourceChange(msg.arg1 == 1, (msg.arg2 & 1) != 0, (msg.arg2 & 2) != 0);
          break;
        }
        case ACTION_ZOOM_CHANGED: {
          context.onZoomChanged(Float.intBitsToFloat(msg.arg1));
          break;
        }
        case ACTION_PERFORM_SUCCESS_HINT: {
          context.onPerformSuccessHint(msg.arg1 == 1);
          break;
        }
        case ACTION_UPDATE_DURATION: {
          context.updateVideoDuration();
          break;
        }
      }
    }
  }
}