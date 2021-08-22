package org.thunderdog.challegram.player;

import android.Manifest;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.VoiceVideoButtonView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGRecord;
import org.thunderdog.challegram.filegen.VideoGenerationInfo;
import org.thunderdog.challegram.helper.Recorder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.camera.CameraControlButton;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.CircleFrameLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SimpleVideoPlayer;
import org.thunderdog.challegram.widget.VideoTimelineView;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceUtils;
import me.vkryl.td.ChatId;

/**
 * Date: 10/12/17
 * Author: default
 */

public class RecordAudioVideoController implements
  Settings.VideoModePreferenceListener, FactorAnimator.Target,
  Recorder.Listener,
  CameraController.ReadyListener,
  RoundVideoRecorder.Delegate,
  TdlibFilesManager.SimpleListener,
  BaseActivity.ActivityListener,
  ThemeChangeListener {
  private static final int MAX_HQ_ROUND_RESOLUTION = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ? 360 : 480;
  private static final int MAX_ROUND_RESOLUTION = 280;
  private static final int MAX_ROUND_DURATION_MS = 59500;

  private final BaseActivity context;

  private FrameLayoutFix rootLayout;
  private RecordBackgroundView videoBackgroundView;
  private ShadowView videoTopShadowView, videoBottomShadowView;
  private RecordButton recordBackground;
  private VoiceVideoButtonView voiceVideoButtonView;
  private RecordLockView lockView;
  private CameraControlButton switchCameraButton;
  private FrameLayoutFix switchCameraButtonWrap;
  private RecordDurationView durationView;
  private FrameLayoutFix inputOverlayView;
  private TextView slideHintView;
  private TextView cancelView;
  private View cornerView;
  private CircleFrameLayout videoLayout;
  private View videoPlaceholderView;
  private RoundProgressView progressView;
  private ImageView deleteButton, sendButton;
  private HapticMenuHelper sendHelper;
  private VideoTimelineView videoTimelineView;
  private SimpleVideoPlayer videoPreviewView;
  private ImageView muteIcon;

  private boolean preferVideoMode;

  public RecordAudioVideoController (BaseActivity context) {
    this.context = context;
    context.addActivityListener(this);

    this.preferVideoMode = Settings.instance().preferVideoMode();
    Settings.instance().addVideoPreferenceChangeListener(this);

    Recorder.instance();

    ThemeManager.instance().addThemeListener(this);
  }

  private Tdlib tdlib;

  public void setTdlib (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  // Theme

  @Override
  public boolean needsTempUpdates () {
    return Math.max(editFactor, recordFactor) > 0f;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    updateColors();
  }

  private void updateColors () {
    if (rootLayout == null) {
      return;
    }

    this.inputOverlayView.setBackgroundColor(Theme.fillingColor());
    this.slideHintView.setTextColor(Theme.textDecentColor());
    this.cancelView.setTextColor(Theme.getColor(R.id.theme_color_textNeutral));
    this.videoPlaceholderView.setBackgroundColor(Theme.fillingColor());
    this.deleteButton.setColorFilter(Theme.iconColor());
    this.sendButton.setColorFilter(Theme.chatSendButtonColor());
    this.videoBackgroundView.setBackgroundColor(Theme.getColor(R.id.theme_color_previewBackground));

    this.cornerView.invalidate();
    this.switchCameraButton.invalidate();
    this.switchCameraButtonWrap.invalidate();
    this.videoTimelineView.invalidate();
    this.durationView.invalidate();
    this.lockView.invalidate();
    this.videoBottomShadowView.invalidate();
    this.videoTopShadowView.invalidate();
  }

  // Implementation

  public void onBackPressed () {
    if (recordMode == RECORD_MODE_VIDEO_EDIT) {
      ViewController<?> c = UI.getCurrentStackItem(context);
      if (c != null) {
        c.openAlert(R.string.DiscardVideoMessageTitle, R.string.DiscardVideoMessageDescription, Lang.getString(R.string.Discard), (dialog, which) -> closeVideoEditMode(null));
      }
    } else {
      finishRecording(true);
    }
  }

  @Override
  public void onActivityPause () {
    videoPreviewView.setActivityPaused(true);
    if (isOpen() && !inRaiseMode) {
      finishRecording(true);
    }
  }

  @Override
  public void onActivityResume () {
    videoPreviewView.setActivityPaused(false);
  }

  @Override
  public void onActivityDestroy () {
    videoPreviewView.setActivityPaused(true);
    if (isOpen()) {
      if (recordMode == RECORD_MODE_VIDEO_EDIT) {
        closeVideoEditMode(null);
      } else {
        stopRecording(CLOSE_MODE_CANCEL, false);
      }
    }
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {

  }

  @Override
  public void onPreferVideoModeChanged (boolean preferVideoMode) {
    this.preferVideoMode = preferVideoMode;
  }

  // Layout

  public View prepareViews () {
    if (rootLayout == null) {
      this.rootLayout = new FrameLayoutFix(context) {
        @Override
        public boolean onInterceptTouchEvent (MotionEvent ev) {
          return Math.max(recordFactor, editFactor) == 0f;
        }

        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return Math.max(recordFactor, editFactor) != 0f && super.onTouchEvent(event);
        }
      };
      this.rootLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      this.videoBackgroundView = new RecordBackgroundView(context);
      this.videoBackgroundView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      this.rootLayout.addView(videoBackgroundView);

      this.inputOverlayView = new FrameLayoutFix(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return Views.isValid(this);
        }
      };
      this.inputOverlayView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(49f), Gravity.BOTTOM));
      this.rootLayout.addView(inputOverlayView);

      this.slideHintView = new NoScrollTextView(context) {
        private int prevWidth;
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          int newWidth = getMeasuredWidth();
          if (prevWidth != newWidth) {
            prevWidth = newWidth;
            updateTranslations();
          }
        }
      };
      this.slideHintView.setText(Lang.getString(R.string.slideToCancel));
      this.slideHintView.setTypeface(Fonts.getRobotoRegular());
      this.slideHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      this.slideHintView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
      this.inputOverlayView.addView(slideHintView);

      this.cornerView = new View(context) {
        @Override
        protected void onDraw (Canvas c) {
          DrawAlgorithms.drawDirection(c, 0, getMeasuredHeight() / 2, Theme.textDecentColor(), Gravity.LEFT);
        }
      };
      this.cornerView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(12f), Screen.dp(16f), Gravity.CENTER));
      this.inputOverlayView.addView(cornerView);

      this.cancelView = new NoScrollTextView(context) {
        @Override
        public boolean onTouchEvent (MotionEvent e) {
          return Views.onTouchEvent(this, e) && super.onTouchEvent(e);
        }
      };
      this.cancelView.setGravity(Gravity.CENTER);
      this.cancelView.setText(Lang.getString(R.string.Cancel).toUpperCase());
      this.cancelView.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      this.cancelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      this.cancelView.setTypeface(Fonts.getRobotoMedium());
      this.cancelView.setAlpha(0f);
      this.cancelView.setOnClickListener(v -> {
        if (isReleased) {
          stopRecording(CLOSE_MODE_CANCEL, false);
        }
      });
      this.cancelView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
      this.inputOverlayView.addView(cancelView);

      this.videoTopShadowView = new ShadowView(context);
      this.videoTopShadowView.setSimpleTopShadow(true);
      this.videoTopShadowView.setLayoutParams(Views.newLayoutParams(videoTopShadowView, Gravity.BOTTOM));
      this.rootLayout.addView(videoTopShadowView);

      this.videoBottomShadowView = new ShadowView(context);
      this.videoBottomShadowView.setSimpleBottomTransparentShadow(true);
      this.videoBottomShadowView.setLayoutParams(Views.newLayoutParams(videoBottomShadowView, Gravity.BOTTOM));
      this.rootLayout.addView(videoBottomShadowView);

      // Duration
      this.durationView = new RecordDurationView(context);
      this.durationView.setTimerCallback(() -> {
        float progress = recordingVideo ? getRecordProgress() : 0f;
        progressView.setVisualProgress(MathUtils.clamp(progress));
        if (progress >= 1f) {
          finishRecording(true);
        }
      });
      this.durationView.setLayoutParams(Views.newLayoutParams(durationView, Gravity.LEFT | Gravity.BOTTOM));
      this.rootLayout.addView(durationView);

      this.recordBackground = new RecordButton(context);
      this.recordBackground.setOnClickListener(v -> {
        if (isOpen() && isReleased) {
          finishRecording(false);
        }
      });
      this.rootLayout.addView(recordBackground);

      this.switchCameraButton = new CameraControlButton(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return Views.isValid(this) && super.onTouchEvent(event);
        }
      };
      this.switchCameraButton.setCameraIconRes(!Settings.instance().startRoundWithRear());
      /*this.switchCameraButton.setSpinCallback(new CameraControlButton.SpinCallback() {
        @Override
        public void onSpin (CameraControlButton v, float rotate, float scaleFactor) {
          float scale = 1f + .1f * scaleFactor;
          videoLayout.setRotationY(rotate);
          videoLayout.setScaleX(scale);
          videoLayout.setScaleY(scale);
        }
      });*/
      this.switchCameraButton.setIsSmall();
      this.switchCameraButtonWrap = new FrameLayoutFix(context);
      Views.setClickable(switchCameraButtonWrap);
      RippleSupport.setCircleBackground(switchCameraButtonWrap, 33f, 3f, R.id.theme_color_filling);
      this.switchCameraButtonWrap.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(33f) + Screen.dp(3f) * 2, Screen.dp(33f) + Screen.dp(3f) * 2));
      this.switchCameraButtonWrap.setOnClickListener(v -> {
        if (ownedCamera != null) {
          ownedCamera.switchCamera();
        }
      });
      this.switchCameraButtonWrap.addView(switchCameraButton);
      this.rootLayout.addView(switchCameraButtonWrap);

      this.lockView = new RecordLockView(context);
      Views.setSimpleStateListAnimator(lockView);
      rootLayout.addView(lockView);
      this.lockView.setOnClickListener(v -> {
        if (isReleased) {
          finishRecording(true);
        }
      });

      this.voiceVideoButtonView = new VoiceVideoButtonView(context) {
        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
          super.onLayout(changed, left, top, right, bottom);
          updateTranslations();
          updatePositions();
        }
      };
      this.voiceVideoButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(50f), Screen.dp(49f), Gravity.RIGHT | Gravity.BOTTOM));
      ViewSupport.setHigherElevation(voiceVideoButtonView, recordBackground, true);
      rootLayout.addView(voiceVideoButtonView);

      this.videoLayout = new CircleFrameLayout(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return Views.isValid(this) && super.onTouchEvent(event);
        }
      };
      this.videoLayout.setOnClickListener(v -> {
        if (recordMode == RECORD_MODE_VIDEO_EDIT) {
          videoPreviewView.toggleMuted();
        }
      });
      this.videoLayout.setTransparentOutline(false);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        this.videoLayout.setTranslationZ(Screen.dp(1.5f));
        this.videoLayout.setElevation(Screen.dp(1f));
      }
      this.videoLayout.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(200f), Screen.dp(200f), Gravity.CENTER_HORIZONTAL));
      this.rootLayout.addView(videoLayout);

      this.videoPlaceholderView = new View(context);
      this.videoPlaceholderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      this.videoLayout.addView(videoPlaceholderView);

      this.progressView = new RoundProgressView(context);
      this.progressView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      this.videoLayout.addView(progressView);

      this.deleteButton = new ImageView(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return editFactor > 0f && Views.isValid(this) && super.onTouchEvent(event);
        }
      };
      this.deleteButton.setScaleType(ImageView.ScaleType.CENTER);
      this.deleteButton.setImageResource(R.drawable.baseline_delete_24);
      Views.setClickable(deleteButton);
      this.deleteButton.setOnClickListener(v -> {
        if (recordMode == RECORD_MODE_VIDEO_EDIT) {
          closeVideoEditMode(null);
        }
      });
      this.deleteButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
      this.inputOverlayView.addView(deleteButton);

      this.sendButton = new ImageView(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return editFactor > 0f && Views.isValid(this) && super.onTouchEvent(event);
        }
      };
      this.sendButton.setScaleType(ImageView.ScaleType.CENTER);
      this.sendButton.setImageResource(R.drawable.deproko_baseline_send_24);
      Views.setClickable(sendButton);
      this.sendButton.setOnClickListener(v -> {
        sendVideo(false, null);
      });
      this.sendButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(55f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
      this.inputOverlayView.addView(sendButton);

      this.videoTimelineView = new VideoTimelineView(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          return editFactor > 0f && Views.isValid(this) && super.onTouchEvent(event);
        }
      };
      this.videoTimelineView.setDelegate(new VideoTimelineView.TimelineDelegate() {
        @Override
        public boolean canTrimTimeline (VideoTimelineView v) {
          return videoPreviewView != null && videoPreviewView.canTrimRegion();
        }

        @Override
        public void onTrimStartEnd (VideoTimelineView v, boolean isStarted) {
          videoPreviewView.setPlaying(!isStarted);
        }

        @Override
        public void onTimelineTrimChanged (VideoTimelineView v, double totalDuration, double startTimeSeconds, double endTimeSeconds) {
          videoPreviewView.setTrimRegion(totalDuration, startTimeSeconds, endTimeSeconds);
        }

        @Override
        public void onSeekTo (VideoTimelineView v, float progress) {
          videoPreviewView.seekTo(progress);
        }
      });
      videoTimelineView.setPadding(Screen.dp(14f), Screen.dp(5f), Screen.dp(14f), Screen.dp(5f));
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      params.leftMargin = params.rightMargin = Screen.dp(56f) - Screen.dp(14f);
      this.videoTimelineView.setLayoutParams(params);
      this.inputOverlayView.addView(videoTimelineView);

      this.videoPreviewView = new SimpleVideoPlayer(context);
      this.videoPreviewView.setMuted(true);
      this.videoPreviewView.setPlaying(true);
      this.videoPreviewView.setLooping(true);
      this.videoPreviewView.setDelegate(new SimpleVideoPlayer.Delegate() {
        @Override
        public void onVideoRenderStateChanged (boolean hasFrame) {
          renderAnimator.setValue(hasFrame, hasFrame);
        }

        @Override
        public void onVideoMuteStateChanged (boolean isMuted) {
          muteAnimator.setValue(isMuted, editFactor > 0f);
        }
      });
      this.videoPreviewView.setAlpha(0f);
      this.videoPreviewView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      this.videoLayout.addView(videoPreviewView);

      this.muteIcon = new ImageView(context) {
        @Override
        protected void onDraw (Canvas c) {
          c.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, Screen.dp(12f), Paints.fillingPaint(0x40000000));
          super.onDraw(c);
        }
      };
      this.muteIcon.setScaleType(ImageView.ScaleType.CENTER);
      this.muteIcon.setImageResource(R.drawable.deproko_baseline_sound_muted_24);
      this.muteIcon.setColorFilter(0xffffffff);
      params = FrameLayoutFix.newParams(Screen.dp(12f) * 2 + Screen.dp(2f), Screen.dp(12f) * 2 + Screen.dp(2), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
      params.bottomMargin = Screen.dp(8f);
      this.muteIcon.setLayoutParams(params);
      this.videoLayout.addView(muteIcon);

      updateColors();

      setRecordFactor(0f);
    }
    return rootLayout;
  }

  private float getRecordProgress () {
    return startTime != 0 ? (float) ((double) (SystemClock.uptimeMillis() - startTime) / (double) MAX_ROUND_DURATION_MS) : 0f;
  }

  @Override
  public void onCameraCompletelyReady (CameraController camera) {
    if (recordMode == RECORD_MODE_VIDEO) {
      isCameraReady = true;
      checkActualRecording(CLOSE_MODE_CANCEL);
    }
  }

  // private int[] inputPos = new int[2];

  private static final float SLIDE_DISTANCE = 50f;

  private void updateMiddle () {
    float actualX = Math.max(-Screen.dp(SLIDE_DISTANCE), getActualTranslateX());
    float closeFactor = MathUtils.clamp(-actualX / (float) Screen.dp(SLIDE_DISTANCE));

    float slideAlpha = (1f - releaseFactor) * (1f - closeFactor);
    slideHintView.setAlpha(slideAlpha);
    cornerView.setAlpha(slideAlpha);

    float editFactor = this.editFactor * (1f - recordFactor);

    cancelView.setAlpha(releaseFactor * (1f - editFactor));

    float dy = inputOverlayView.getMeasuredHeight() / 2;
    float addDy = editFactor * dy;
    cancelView.setTranslationY(-dy * (1f - releaseFactor) + addDy);
    slideHintView.setTranslationY(dy * releaseFactor);
    cornerView.setTranslationY(dy * releaseFactor);

    float editDy = -dy + addDy;
    sendButton.setAlpha(this.editFactor);
    deleteButton.setAlpha(editFactor);
    deleteButton.setTranslationY(editDy);
    videoTimelineView.setAlpha(editFactor);
    videoTimelineView.setTranslationY(editDy);
  }

  private void updateTranslations () {
    float actualY = getActualTranslateY();
    int threshold = -Screen.dp(33f) * 2;
    if (!isReleased) {
      float collapseFactor = actualY <= threshold ? 1f : actualY / threshold;
      lockView.setCollapseFactor(collapseFactor);

      if (collapseFactor == 1f) {
        setReleased(true, true);
      }
    }
    float actualX = Math.max(-Screen.dp(SLIDE_DISTANCE), getActualTranslateX());
    float closeFactor = MathUtils.clamp(-actualX / (float) Screen.dp(SLIDE_DISTANCE));

    float cornerX = -slideHintView.getMeasuredWidth() / 2 + actualX - cornerView.getMeasuredWidth();
    slideHintView.setTranslationX(actualX);
    cornerView.setTranslationX(cornerX);
    updateMiddle();
    cornerX += cornerView.getLeft();

    durationView.setTranslationX(Math.min(0, cornerX - durationView.getMeasuredWidth()));

    updateVideoY();
    updateDuration();

    inputOverlayView.setTranslationY(overallTranslation);
    videoTopShadowView.setTranslationY(overallTranslation - inputOverlayView.getMeasuredHeight());
    videoBottomShadowView.setTranslationY(overallTranslation + videoBottomShadowView.getMeasuredHeight());

    float y = overallTranslation + actualY;
    voiceVideoButtonView.setTranslationY(y);

    // Relative to voiceVideoButtonView

    float top = voiceVideoButtonView.getTop() + voiceVideoButtonView.getTranslationY();

    float cx = voiceVideoButtonView.getLeft() + voiceVideoButtonView.getTranslationX() + voiceVideoButtonView.getMeasuredWidth() / 2;
    float cy = top + voiceVideoButtonView.getMeasuredHeight() / 2;

    videoBackgroundView.setPivot((int) cx, (int) cy);

    recordBackground.setTranslationX(cx - recordBackground.getMeasuredWidth() / 2);
    recordBackground.setTranslationY(cy - recordBackground.getMeasuredHeight() / 2);

    lockView.setTranslationX(cx - lockView.getMeasuredWidth() / 2);
    switchCameraButtonWrap.setTranslationX(cx - switchCameraButtonWrap.getMeasuredWidth() / 2);
    updateLockY();

    if (closeFactor * recordFactor == 1f) {
      stopRecording(CLOSE_MODE_CANCEL, false);
    }
  }

  private void updateVideoY () {
    int bottom = inputOverlayView.getTop() + overallTranslation;
    videoLayout.setTranslationY(bottom / 2 - videoLayout.getLayoutParams().height / 2 + (bottom / 3 * (1f - Math.max(recordFactor, editFactor))));
  }

  private void updateLockY () {
    float cy = voiceVideoButtonView.getTop() + voiceVideoButtonView.getTranslationY() + voiceVideoButtonView.getMeasuredHeight() / 2;
    float y = cy - lockView.getMeasuredHeight() - Screen.dp(11f) - Screen.dp(41f) + Screen.dp(24f) * releaseFactor + Screen.dp(24f) * (1f - MathUtils.clamp(recordFactor));
    lockView.setTranslationY(y);
    switchCameraButtonWrap.setTranslationY(y - Screen.dp(16f) - switchCameraButtonWrap.getMeasuredHeight() + Screen.dp(24f) * (1f - MathUtils.clamp(recordFactor)) * (1f - releaseFactor));
  }

  private boolean isReleased;
  private static final int ANIMATOR_RELEASE = 2;
  private BoolAnimator releaseAnimator = new BoolAnimator(ANIMATOR_RELEASE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private float releaseFactor;

  private void setReleased (boolean isReleased, boolean animated) {
    if (this.isReleased != isReleased) {
      this.isReleased = isReleased;
      releaseAnimator.setValue(isReleased, animated);
    }
  }

  private void setReleaseFactor (float factor) {
    if (this.releaseFactor != factor) {
      this.releaseFactor = factor;
      voiceVideoButtonView.setSendFactor(factor);
      lockView.setSendFactor(factor);
      updateTranslations();
    }
  }

  public void updatePositions () {
    ViewController<?> c = UI.getCurrentStackItem(context);
    if (c instanceof MessagesController) {
      View view = ((MessagesController) c).getBottomWrap();
      setOverallTranslation((Views.getLocationInWindow(view)[1] - Views.getLocationInWindow(rootLayout)[1]) - voiceVideoButtonView.getTop());
    }
  }

  private int overallTranslation;

  private void setOverallTranslation (int translation) {
    if (this.overallTranslation != translation) {
      this.overallTranslation = translation;
      updateTranslations();
    }
  }

  private void resetViews () {
    setTranslations(0f, 0f);
    switchCameraButton.setCameraIconRes(!Settings.instance().startRoundWithRear());
    progressView.setVisualProgress(0f);
    durationView.reset();
    lockView.setCollapseFactor(0f);
    recordBackground.setVolume(0f, false);
    editAnimator.setValue(false, false);
    videoPreviewView.performDestroy();
    videoTimelineView.performDestroy();
    videoPreviewView.setMuted(true);
    videoPreviewView.setPlaying(true);
    setReleased(false, false);
    resetState();
  }

  @Override
  public void onCameraSwitched (boolean isForward, boolean toFrontFace) {
    switchCameraButton.spinAround(isForward, toFrontFace);
  }

  private float translateX, translateY;

  private float getActualTranslateX () {
    return translateX * (1f - verticalFactor);
  }

  private float getActualTranslateY () {
    return translateY * verticalFactor * (1f - releaseFactor);
  }

  public boolean setTranslations (float x, float y) {
    float prevX = getActualTranslateX();
    float prevY = getActualTranslateY();

    this.translateX = x;
    this.translateY = y;

    if (prevX != getActualTranslateX() || prevY != getActualTranslateY()) {
      updateTranslations();
    }

    return !isReleased && isOpen();
  }

  private boolean applyVerticalDrag;

  public void setApplyVerticalDrag (boolean applyVerticalDrag) {
    if (this.applyVerticalDrag != applyVerticalDrag) {
      this.applyVerticalDrag = applyVerticalDrag;
      checkAxis();
    }
  }

  private boolean currentAxis;
  private static final int ANIMATOR_AXIS = 1;
  private BoolAnimator axisAnimator = new BoolAnimator(ANIMATOR_AXIS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private void checkAxis () {
    boolean axis = this.applyVerticalDrag;
    if (this.currentAxis != axis) {
      this.currentAxis = axis;
      axisAnimator.setValue(axis, Math.max(Math.abs(getActualTranslateX()), Math.abs(getActualTranslateY())) > 0);
    }
  }

  // Duration

  private long startTime;

  private void startTimers (long ms) {
    startTime = ms;
    durationView.start(startTime);
  }

  private void stopTimers () {
    startTime = 0;
    durationView.stop();
  }

  // State

  public boolean isOpen () {
    return recordMode != RECORD_MODE_NONE || Math.max(recordFactor, editFactor) > 0f;
  }

  // Permissions check

  private static final String[] AUDIO_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ? new String[] {
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE
  } : new String[] {
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
  };

  private boolean needPermissions (boolean video, boolean allowRequest) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      String[] permissions = video ? CameraController.VIDEO_PERMISSIONS : AUDIO_PERMISSIONS;
      if (U.needsPermissionRequest(permissions)) {
        if (allowRequest) {
          U.requestPermissions(permissions, null);
        }
        return true;
      }
    }
    return false;
  }

  // Entry points

  public interface RecordStateListeners {
    void onRecordStateChanged (boolean isRecording);
  }

  private final List<Reference<RecordStateListeners>> recordListeners = new ArrayList<>();

  public void addRecordStateListener (RecordStateListeners listener) {
    ReferenceUtils.addReference(recordListeners, listener);
  }

  public void removeRecordStateListener (RecordStateListeners listener) {
    ReferenceUtils.removeReference(recordListeners, listener);
  }

  private void notifyRecordStateChanged (boolean isRecording) {
    context.navigation().getStack().setIsLocked(isRecording);

    if (isRecording) {
      UI.forceVibrate(voiceVideoButtonView, true, true);
      tdlib.context().player().pauseWithReason(TGPlayerController.PAUSE_REASON_RECORD_AUDIO_VIDEO);
    }

    final int size = recordListeners.size();
    for (int i = size - 1; i >= 0; i--) {
      RecordStateListeners listener = recordListeners.get(i).get();
      if (listener != null) {
        listener.onRecordStateChanged(isRecording);
      } else {
        recordListeners.remove(i);
      }
    }
  }

  private static final int RECORD_MODE_NONE = 0;
  private static final int RECORD_MODE_AUDIO = 1;
  private static final int RECORD_MODE_VIDEO = 2;
  private static final int RECORD_MODE_VIDEO_EDIT = 3;

  private static final long EXPAND_DURATION = 160l;
  private static final long COLLAPSE_DURATION = 140l;

  private static final int ANIMATOR_RECORDING = 0;
  private int recordMode;
  private final BoolAnimator recordAnimator = new BoolAnimator(ANIMATOR_RECORDING, this, AnimatorUtils.DECELERATE_INTERPOLATOR, EXPAND_DURATION);
  private boolean recordingVideo;

  private static final int ANIMATOR_EDIT = 4;
  private float editFactor;
  private final BoolAnimator editAnimator = new BoolAnimator(ANIMATOR_EDIT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private static final int ANIMATOR_RENDER = 5;
  private float renderFactor;
  private final BoolAnimator renderAnimator = new BoolAnimator(ANIMATOR_RENDER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private static final int ANIMATOR_MUTE = 6;
  private float muteFactor = 1f;
  private final BoolAnimator muteAnimator = new BoolAnimator(ANIMATOR_MUTE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, true);

  private void updateMuteAlpha () {
    float factor = editFactor * (1f - recordFactor) * muteFactor;
    muteIcon.setAlpha(factor);
    float scale = .6f + .4f * factor;
    muteIcon.setScaleX(scale);
    muteIcon.setScaleY(scale);
  }

  private void setEditFactor (float factor) {
    if (this.editFactor != factor) {
      this.editFactor = factor;
      updateMainAlphas();
    }
  }

  private void setMuteFactor (float factor) {
    if (this.muteFactor != factor) {
      this.muteFactor = factor;
      updateMuteAlpha();
    }
  }

  private void setRenderFactor (float factor) {
    if (this.renderFactor != factor) {
      this.renderFactor = factor;
      videoPreviewView.setAlpha(factor);
    }
  }

  private static boolean isVideoMode (int mode) {
    return mode == RECORD_MODE_VIDEO || mode == RECORD_MODE_VIDEO_EDIT;
  }

  private static boolean isInRecording (int mode) {
    return mode != RECORD_MODE_NONE && mode != RECORD_MODE_VIDEO_EDIT;
  }

  private void setRecordMode (int mode, boolean animated) {
    boolean wasRecording = isInRecording(this.recordMode);
    boolean isRecording = isInRecording(mode);
    this.recordingVideo = isVideoMode(this.recordMode) || isVideoMode(mode);
    this.recordMode = mode;
    if (targetController != null) {
      targetController.setChatAction(recordingVideo ? TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR : TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR, mode != RECORD_MODE_NONE, false);
      targetController.hideBottomHint();
    }
    recordAnimator.setDuration(isRecording ? EXPAND_DURATION : COLLAPSE_DURATION);
    if (isRecording) {
      updatePositions();
      updateTranslations();
      recordAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    } else {
      recordAnimator.setInterpolator(AnimatorUtils.ACCELERATE_INTERPOLATOR);
    }
    this.recordAnimator.setValue(isRecording, animated);
    if (isRecording) {
      voiceVideoButtonView.setInVideoMode(mode == RECORD_MODE_VIDEO, recordFactor > 0f);
    }
    if (wasRecording != isRecording) {
      notifyRecordStateChanged(isRecording);
      if (!isRecording) {
        onRecordBlur();
      }
    }
  }

  private CameraController ownedCamera;
  private boolean isCameraReady;
  private boolean inRaiseMode;

  private long targetChatId;
  private long targetMessageThreadId;
  private MessagesController targetController;

  public boolean startRecording (View view, boolean inRaiseMode) {
    if (this.recordMode != RECORD_MODE_NONE) {
      return false;
    }

    if (tdlib.context().calls().promptActiveCall()) {
      return false;
    }

    final boolean needVideo = !inRaiseMode && preferVideoMode;
    if (needPermissions(needVideo, !inRaiseMode)) {
      return false;
    }

    this.targetController = findMessagesController();
    if (targetController == null) {
      return false;
    }

    this.targetChatId = targetController.getChatId();
    this.targetMessageThreadId = targetController.getMessageThreadId();
    if (needVideo && !tdlib.chatSupportsRoundVideos(targetChatId)) {
      TdApi.User user = tdlib.chatUser(targetChatId);
      String name = user != null ? user.firstName : tdlib.chatTitle(targetChatId);
      UI.showToast(Lang.getString(R.string.SecretChatFeatureUnsupported, name), Toast.LENGTH_SHORT);
      return false;
    }

    CharSequence restrictionText = tdlib.getRestrictionText(tdlib.chat(targetChatId), R.id.right_sendMedia, needVideo ? R.string.ChatDisabledVideoNotes : R.string.ChatDisabledVoice, needVideo ? R.string.ChatRestrictedVideoNotes : R.string.ChatRestrictedVoice, needVideo ? R.string.ChatRestrictedVideoNotesUntil : R.string.ChatRestrictedVoiceUntil);
    if (restrictionText != null) {
      if (view != null) {
        context.tooltipManager().builder(view).controller(targetController).icon(R.drawable.baseline_warning_24).show(tdlib, restrictionText).hideDelayed();
      } else {
        UI.showToast(restrictionText, Toast.LENGTH_SHORT);
      }
      return false;
    }

    if (awaitingRoundResult()) {
      return false;
    }

    if (recordAnimator != null && recordAnimator.isAnimating()) {
      return false;
    }

    if (UI.getContext(context).isActivityBusyWithSomething()) {
      return false;
    }

    UI.getContext(context).closeAllMedia(true);

    if (needVideo) {
      if (ownedCamera != null) {
        throw new IllegalStateException();
      }
      isCameraReady = false;
      ownedCamera = context.takeCameraOwnership(new ViewController.CameraOpenOptions()
        .mode(CameraController.MODE_ROUND_VIDEO)
        .readyListener(this)
      );
      if (ownedCamera == null) {
        return false;
      }
      prepareVideoRecording();
    }

    if (sendHelper != null)
      sendHelper.detachFromView(sendButton);
    sendHelper = tdlib.ui()
      .createSimpleHapticMenu(targetController, targetChatId, () -> editFactor == 1f, null, null, (forceDisableNotification, schedulingState, disableMarkdown) -> sendVideo(forceDisableNotification, schedulingState), null)
      .attachToView(sendButton);
    if (!inRaiseMode) {
      context.setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_RECORDING, true);
    }
    context.setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_RECORDING, true);

    resetViews();
    this.inRaiseMode = inRaiseMode;
    setReleased(inRaiseMode, false);
    if (inRaiseMode) {
      lockView.setCollapseFactor(1f);
    }
    setRecordMode(needVideo ? RECORD_MODE_VIDEO : RECORD_MODE_AUDIO, !inRaiseMode);

    // checkActualRecording(CLOSE_MODE_CANCEL);

    return true;
  }

  private static final int CLOSE_MODE_CANCEL = 0;
  private static final int CLOSE_MODE_SEND = 1;
  private static final int CLOSE_MODE_PREVIEW = 2;
  private static final int CLOSE_MODE_PREVIEW_SCHEDULE = 3;

  private static final long MINIMUM_VIDEO_RECORDING_DURATION = 1000l;
  private static final long MINIMUM_AUDIO_RECORDING_DURATION = 500l;

  private boolean canSendRecording () {
    return startTime != 0 && (SystemClock.uptimeMillis() - startTime) >= (recordingVideo ? MINIMUM_VIDEO_RECORDING_DURATION : MINIMUM_AUDIO_RECORDING_DURATION);
  }

  public boolean finishRecording (boolean needPreview) {
    return stopRecording(canSendRecording() ? (needPreview ? CLOSE_MODE_PREVIEW : (hasValidOutputTarget() && targetController.areScheduledOnly()) ? CLOSE_MODE_PREVIEW_SCHEDULE : CLOSE_MODE_SEND) : CLOSE_MODE_CANCEL, true);
  }

  private boolean stopRecording (int closeMode, boolean showPrompt) {
    if (recordMode == RECORD_MODE_NONE || recordMode == RECORD_MODE_VIDEO_EDIT) {
      return false;
    }

    if (closeMode == CLOSE_MODE_CANCEL) {
      UI.forceVibrate(voiceVideoButtonView, true, true);
      if (showPrompt) {
        Settings.instance().notifyRecordAudioVideoError();
      }
    }

    int mode = RECORD_MODE_NONE;

    boolean async = (closeMode == CLOSE_MODE_PREVIEW || closeMode == CLOSE_MODE_PREVIEW_SCHEDULE) && recordingVideo;
    if (async) {
      mode = RECORD_MODE_VIDEO_EDIT;
      editAnimator.setValue(true, false);
    }

    if (recordingVideo && (closeMode == CLOSE_MODE_PREVIEW || closeMode == CLOSE_MODE_PREVIEW_SCHEDULE)) {
      if (ownedCamera != null) {
        ownedCamera.getLegacyManager().setNoPreviewBlur(true);
      } else {
        Log.w(Log.TAG_ROUND, "ownedCamera.setNoPreviewBlur() failed: null");
      }
    }
    setRecordMode(mode, true);
    if (recordingVideo) {
      destroyVideoRecording(closeMode == CLOSE_MODE_CANCEL);
    }
    checkActualRecording(closeMode);

    return true;
  }

  // Backend

  private boolean isVideoRecordingReady () {
    return ownedCamera != null && isCameraReady && isRoundVideoFileReady();
  }

  private boolean isAudioRecordingReady () {
    return !Recorder.instance().isRecording();
  }

  private boolean hasValidOutputTarget () {
    return targetController != null && !targetController.isDestroyed() && targetController.compareChat(targetChatId, targetMessageThreadId) && targetChatId != 0 && tdlib != null;
  }

  private void checkActualRecording (int closeMode) {
    boolean isRecording = this.recordMode != RECORD_MODE_NONE && this.recordMode != RECORD_MODE_VIDEO_EDIT && gotFocus;
    boolean actuallyRecording = this.currentRecording != RECORD_MODE_NONE;
    if (!actuallyRecording && isRecording) {
      switch (recordMode) {
        case RECORD_MODE_AUDIO:
          isRecording = isAudioRecordingReady();
          break;
        case RECORD_MODE_VIDEO:
          isRecording = isVideoRecordingReady();
          break;
      }
    }
    if (actuallyRecording != isRecording) {
      if (isRecording) {
        startRecordingImpl(this.recordMode);
      } else {
        stopRecordingImpl(hasValidOutputTarget() ? closeMode : CLOSE_MODE_CANCEL);
      }
    }
  }

  private boolean awaitingRoundResult () {
    return roundCloseMode != CLOSE_MODE_CANCEL;
  }

  private void cleanupVideoRecording () {
    if (recordingVideo && Math.max(recordFactor, editFactor) * (1f - renderFactor) == 0f && ownedCamera != null && !awaitingRoundResult()) {
      ownedCamera.onCleanAfterHide();
      ownedCamera.releaseCameraLayout();

      setupCamera(false);
      context.releaseCameraOwnership();
      ownedCamera = null;

      resetRoundState();
    }
  }

  // Raise to Speak

  public boolean enterRaiseRecordMode () {
    return startRecording(null, true);
  }

  public boolean leaveRaiseRecordMode () {
    return finishRecording(true);
  }

  // Animations

  private float recordFactor = -1f;
  private float verticalFactor = 0f;

  private void setVerticalFactor (float factor) {
    if (this.verticalFactor != factor) {
      this.verticalFactor = factor;
      updateTranslations();
    }
  }

  private void updateDuration () {
    float range = MathUtils.clamp(recordFactor);
    float editDyTotal = inputOverlayView.getMeasuredHeight() / 2;
    float editDy = editFactor * (1f - recordFactor) * editDyTotal;
    durationView.setAlpha(range);
    durationView.setTranslationY(overallTranslation + editDy);
  }

  private void updateMainAlphas () {
    float range = MathUtils.clamp(recordFactor);
    float editRange = Math.max(range, editFactor);
    voiceVideoButtonView.setAlpha(range);
    inputOverlayView.setAlpha(editRange);
    lockView.setAlpha(range);

    float videoRange = recordingVideo ? editRange : 0f;
    videoBackgroundView.setFactor(videoRange);
    videoTopShadowView.setAlpha(videoRange);
    videoBottomShadowView.setAlpha(videoRange);
    videoLayout.setAlpha(videoRange);

    progressView.setAlpha(Math.max(recordFactor, 1f - editFactor));

    float videoScale = .4f + .6f * videoRange;
    videoLayout.setScaleX(videoScale);
    videoLayout.setScaleY(videoScale);
    switchCameraButtonWrap.setAlpha(recordingVideo ? range : 0);

    updateMuteAlpha();

    updateVideoY();
  }

  private void setRecordFactor (float factor) {
    if (this.recordFactor != factor) {
      this.recordFactor = factor;

      updateMainAlphas();

      float scale = .6f + .4f * factor;
      lockView.setScaleX(scale);
      lockView.setScaleY(scale);
      switchCameraButtonWrap.setScaleX(scale);
      switchCameraButtonWrap.setScaleY(scale);

      recordBackground.setExpand(factor);

      updateLockY();
      updateDuration();
      updateMiddle();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_RECORDING: {
        setRecordFactor(factor);
        break;
      }
      case ANIMATOR_AXIS: {
        setVerticalFactor(factor);
        break;
      }
      case ANIMATOR_RELEASE: {
        setReleaseFactor(factor);
        break;
      }
      case ANIMATOR_EDIT: {
        setEditFactor(factor);
        break;
      }
      case ANIMATOR_RENDER: {
        setRenderFactor(factor);
        break;
      }
      case ANIMATOR_MUTE: {
        setMuteFactor(factor);
        break;
      }
    }
  }

  private boolean gotFocus;

  private void onRecordFocus () {
    if (gotFocus) {
      return;
    }
    gotFocus = true;
    if (recordingVideo) {
      if (ownedCamera != null) {
        ownedCamera.onFocus();
      } else {
        Log.w(Log.TAG_ROUND, "ownedCamera.onFocus() failed: null");
      }
    }
    checkActualRecording(CLOSE_MODE_CANCEL);
  }

  private void onRecordRemoved () {
    context.setScreenFlagEnabled(BaseActivity.SCREEN_FLAG_RECORDING, false);
    context.setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_RECORDING, false);
    cleanupVideoRecording();
  }

  private void onRecordBlur () {
    if (!gotFocus) {
      return;
    }
    if (recordingVideo && ownedCamera != null) {
      ownedCamera.onBlur();
    }
    gotFocus = false;
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_RECORDING: {
        if (finalFactor == 1f) {
          onRecordFocus();
        } else if (finalFactor == 0f) {
          onRecordRemoved();
        }
        break;
      }
      case ANIMATOR_RELEASE: {
        if (finalFactor == 1f) {
          videoPreviewView.preparePlayer();
        }
        break;
      }
      case ANIMATOR_EDIT: {
        if (finalFactor == 0f) {
          cleanupVideoRecording();
        } else if (finalFactor == 1f && roundCloseMode == CLOSE_MODE_PREVIEW_SCHEDULE) {
          sendVideo(false, null);
        }
        break;
      }
      case ANIMATOR_RENDER: {
        if (finalFactor == 1f) {
          cleanupVideoRecording();
        }
        break;
      }
    }
  }

  // Record  impl

  private int currentRecording;
  private int roundCloseMode;

  private void startRecordingImpl (int mode) {
    this.currentRecording = mode;
    switch (mode) {
      case RECORD_MODE_AUDIO:
        if (tdlib != null) {
          Recorder.instance().record(tdlib, ChatId.isSecret(targetChatId), this);
        }
        break;
      case RECORD_MODE_VIDEO:
        startVideoRecording();
        break;
    }
  }

  private MessagesController findMessagesController () {
    ViewController<?> c = UI.getCurrentStackItem(context);
    return c instanceof MessagesController ? (MessagesController) c : null;
  }

  private void stopRecordingImpl (int closeMode) {
    final boolean needResult = closeMode != CLOSE_MODE_CANCEL && hasValidOutputTarget();
    switch (currentRecording) {
      case RECORD_MODE_AUDIO:
        if (needResult) {
          if (closeMode == CLOSE_MODE_PREVIEW || closeMode == CLOSE_MODE_PREVIEW_SCHEDULE) {
            targetController.prepareVoicePreview((int) ((SystemClock.uptimeMillis() - startTime) / 1000l));
            // TODO stop playing temporary record
            // Player.instance().destroy();
          }
          Recorder.instance().save();
        } else {
          Recorder.instance().cancel();
        }
        break;
      case RECORD_MODE_VIDEO:
        finishVideoRecording(closeMode);
        break;
    }
    this.currentRecording = RECORD_MODE_NONE;
    stopTimers();
  }


  // Audio record impl

  private void resetState () {
    checkActualRecording(RECORD_MODE_NONE);
  }

  @Override
  public void onAmplitude (float amplitude) {
    if (isOpen() && currentRecording != RECORD_MODE_NONE) {
      if (startTime != 0) {
        recordBackground.setVolume(amplitude, true);
      } else {
        startTimers(SystemClock.uptimeMillis());
      }
    }
  }

  @Override
  public void onFail () {
    if (isOpen()) {
      UI.showToast(R.string.Error, Toast.LENGTH_SHORT);
      stopRecording(CLOSE_MODE_CANCEL, false);
    }
  }

  @Override
  public void onSave (final Tdlib.Generation generation, final int duration, final byte[] waveform) {
    UI.post(() -> {
      if (hasValidOutputTarget()) {
        targetController.shareItem(new TGRecord(tdlib, generation, duration, waveform));
      }
    });
  }

  // Video record impl

  private void setupCamera (boolean isOwned) {
    ownedCamera.getManager().setPreferFrontFacingCamera(!Settings.instance().startRoundWithRear() && isOwned);
    ownedCamera.getManager().setMaxResolution(isOwned ? (Settings.instance().needHqRoundVideos() ? MAX_HQ_ROUND_RESOLUTION : MAX_ROUND_RESOLUTION) : 0);
    ownedCamera.getLegacyManager().setNoPreviewBlur(false);
    ownedCamera.getLegacyManager().setUseRoundRender(isOwned);
    ownedCamera.getLegacyManager().getView().setIgnoreAspectRatio(isOwned);
    ownedCamera.getCameraLayout().setDisallowRatioChanges(isOwned);
    ownedCamera.setUseFastInitialization(isOwned);
  }

  private String roundKey;
  private long roundGenerationId;
  private String roundOutputPath;
  private TdApi.File roundFile;

  private void prepareVideoRecording () {
    if (!StringUtils.isEmpty(roundKey)) {
      throw new IllegalStateException();
    }
    if (ownedCamera == null) {
      throw new IllegalStateException();
    }
    roundKey = "round" + SystemClock.uptimeMillis() + "_" + System.currentTimeMillis() + "_" + Math.random();
    tdlib.client().send(new TdApi.UploadFile(new TdApi.InputFileGenerated(null, roundKey, 0), ChatId.isSecret(targetChatId) ? new TdApi.FileTypeSecret() : new TdApi.FileTypeVideoNote(), 1), object -> {
      if (object.getConstructor() == TdApi.File.CONSTRUCTOR) {
        setRoundGenerationFile((TdApi.File) object);
      } else {
        UI.showError(object);
      }
    });

    setupCamera(true);
    ownedCamera.setInEarlyInitialization();
    ownedCamera.setOutputController(context.navigation().getCurrentStackItem());
    ownedCamera.onPrepareToShow();
    ownedCamera.takeCameraLayout(videoLayout, 1);
  }

  private void setRoundGeneration (long generationId, String outputPath) {
    this.roundGenerationId = generationId;
    this.roundOutputPath = outputPath;
    checkActualRecording(CLOSE_MODE_CANCEL);
  }

  private void setRoundFile (TdApi.File file) {
    this.roundFile = file;
    checkActualRecording(CLOSE_MODE_CANCEL);
  }

  private boolean isRoundVideoFileReady () {
    return !StringUtils.isEmpty(roundKey) && !StringUtils.isEmpty(roundOutputPath) && roundFile != null;
  }

  private void destroyVideoRecording (boolean deleteFile) {
    if (StringUtils.isEmpty(roundKey)) {
      return;
    }

    if (deleteFile) {
      if (!StringUtils.isEmpty(roundOutputPath)) {
        tdlib.client().send(new TdApi.FinishFileGeneration(roundGenerationId, new TdApi.Error()), tdlib.silentHandler());
      }
      if (roundFile != null) {
        tdlib.client().send(new TdApi.CancelUploadFile(roundFile.id), tdlib.silentHandler());
      }
      resetRoundState();
    }
  }

  private void resetRoundState () {
    if (roundFile != null) {
      tdlib.files().unsubscribe(roundFile.id, this);
      roundFile = null;
    }
    roundOutputPath = null;
    roundGenerationId = 0;
    roundKey = null;
    roundGenerationFinished = false;
  }

  @AnyThread
  public void setRoundGeneration (final String key, final long generationId, final String outputPath) {
    UI.post(() -> {
      if (StringUtils.equalsOrBothEmpty(key, roundKey)) {
        setRoundGeneration(generationId, outputPath);
      } else {
        tdlib.client().send(new TdApi.FinishFileGeneration(generationId, new TdApi.Error()), tdlib.silentHandler());
      }
    });
  }

  @AnyThread
  private void setRoundGenerationFile (final TdApi.File file) {
    UI.post(() -> {
      if (!StringUtils.isEmpty(roundKey) && StringUtils.equalsOrBothEmpty(file.local.path, roundOutputPath)) {
        setRoundFile(file);
      } else {
        tdlib.client().send(new TdApi.CancelUploadFile(file.id), tdlib.silentHandler());
      }
    });
  }

  private boolean recordingRoundVideo;
  private int savedRoundDuration;

  private void startVideoRecording () {
    this.recordingRoundVideo = true;
    ownedCamera.getLegacyManager().requestRoundVideoCapture(roundKey, this, roundOutputPath);
  }

  private void finishVideoRecording (int closeMode) {
    this.recordingRoundVideo = false;
    this.roundCloseMode = closeMode;
    final boolean needResult = closeMode != CLOSE_MODE_CANCEL;
    ownedCamera.getLegacyManager().finishOrCancelRoundVideoCapture(roundKey, needResult);
  }

  @Override
  public void onVideoRecordingStarted (String key, long startTimeMs) {
    if (StringUtils.equalsOrBothEmpty(roundKey, key) && recordingRoundVideo) {
      startTimers(startTimeMs);
    }
  }

  @Override
  public void onVideoRecordProgress (String key, int readyBytesCount) {
    if (StringUtils.equalsOrBothEmpty(roundKey, key)) {
      tdlib.client().send(new TdApi.SetFileGenerationProgress(roundGenerationId, 0, readyBytesCount), tdlib.silentHandler());
    }
  }

  private void sendVideoNote (TdApi.InputMessageVideoNote videoNote, TdApi.MessageSendOptions options, TdApi.File helperFile) {
    if (hasValidOutputTarget()) {
      boolean isSecretChat = ChatId.isSecret(targetChatId);
      targetController.pickDateOrProceed(options.disableNotification, options.schedulingState, (forceDisableNotification, schedulingState, disableMarkdown) -> {
        TdApi.InputMessageVideoNote newVideoNote = tdlib.filegen().createThumbnail(videoNote, isSecretChat, helperFile);
        long chatId = targetController.getChatId();
        long messageThreadId = targetController.getMessageThreadId();
        long replyToMessageId = targetController.obtainReplyId();
        TdApi.MessageSendOptions opts = new TdApi.MessageSendOptions(forceDisableNotification, false, schedulingState);
        if (newVideoNote.thumbnail == null && helperFile != null) {
          tdlib.client().send(new TdApi.DownloadFile(helperFile.id, 1, 0, 0, true), result -> {
            tdlib.sendMessage(chatId, messageThreadId, replyToMessageId, opts, result.getConstructor() == TdApi.File.CONSTRUCTOR ? tdlib.filegen().createThumbnail(videoNote, isSecretChat, (TdApi.File) result) : newVideoNote, null);
          });
        } else {
          tdlib.sendMessage(chatId, messageThreadId, replyToMessageId, opts, newVideoNote, null);
        }
      });
    }
    roundCloseMode = CLOSE_MODE_CANCEL;
    cleanupVideoRecording();
  }

  private void finishFileGeneration (int resultFileSize) {
    tdlib.client().send(new TdApi.SetFileGenerationProgress(roundGenerationId, resultFileSize, resultFileSize), tdlib.silentHandler());
    tdlib.client().send(new TdApi.FinishFileGeneration(roundGenerationId, null), tdlib.silentHandler());
  }

  private static final int VIDEO_NOTE_LENGTH = 360;

  @Override
  public void onVideoRecordingFinished (String key, int resultFileSize, int resultFileDurationSeconds) {
    if (StringUtils.equalsOrBothEmpty(roundKey, key)) {
      boolean success = resultFileSize > 0;
      if (awaitingRoundResult()) {
        if (success) {
          this.savedRoundDuration = resultFileDurationSeconds;
          if (roundCloseMode == CLOSE_MODE_PREVIEW || roundCloseMode == CLOSE_MODE_PREVIEW_SCHEDULE) {
            awaitRoundVideo();
            finishFileGeneration(resultFileSize);
          } else {
            finishFileGeneration(resultFileSize);
            sendVideoNote(new TdApi.InputMessageVideoNote(new TdApi.InputFileId(roundFile.id), null, savedRoundDuration, VIDEO_NOTE_LENGTH), TD.defaultSendOptions(), roundFile);
          }
        } else {
          finishFileGeneration(-1);
          cancelAwaitRoundRecord();
        }
      } else if (!success) {
        stopRecording(CLOSE_MODE_CANCEL, false);
      }
    }
  }

  /*@Override
  public void onUpdateFileGenerationFailure (final int fileId) {
    TGDataManager.runOnUiThread(new Runnable() {
      @Override
      public void run () {
        if (roundFile.id == fileId) {
          cancelAwaitRoundRecord();
        }
      }
    });
  }*/

  private void cancelAwaitRoundRecord () {
    if (recordMode == RECORD_MODE_VIDEO_EDIT) {
      roundCloseMode = CLOSE_MODE_CANCEL;
      setRecordMode(RECORD_MODE_NONE, true);
      cleanupVideoRecording();
    }
  }

  // Generation progress

  @Override
  public void onUpdateFile (final TdApi.File file) {
    if (!StringUtils.isEmpty(file.local.path) && file.size != 0 && file.local.isDownloadingCompleted) {
      tdlib.ui().post(() -> {
        if (roundFile != null && roundFile.id == file.id) {
          roundFile = file;
          roundGenerationFinished = true;
          onRoundVideoReady();
        }
      });
    }
  }

  /*@Override
  public void onUpdateFileGenerationFinish (final TdApi.File file) {
    TGDataManager.runOnUiThread(new Runnable() {
      @Override
      public void run () {
        if (roundFile != null && roundFile.id == file.id) {
          roundFile = file;
          roundGenerationFinished = true;
          onRoundVideoReady();
        }
      }
    });
  }*/

  // Video Trim/Preview

  private boolean roundGenerationFinished;

  private void awaitRoundVideo () {
    tdlib.files().subscribe(roundFile.id, this);
  }

  private boolean roundFileReceived () {
    return roundGenerationFinished && TD.isFileLoaded(roundFile);
  }

  private void onRoundVideoReady () {
    if (!roundFileReceived()) {
      return;
    }

    tdlib.files().unsubscribe(roundFile.id, this);
    videoTimelineView.setVideoPath(roundFile.local.path);
    videoPreviewView.setVideo(roundFile.local.path);

    if (scheduledEditClose) {
      scheduledEditClose = false;
      TdApi.MessageSendOptions options = scheduledSendOptions;
      scheduledSendOptions = null;
      closeVideoEditMode(options);
    }
  }

  private boolean scheduledEditClose;
  private TdApi.MessageSendOptions scheduledSendOptions;

  private void sendVideo (boolean forceDisableNotification, TdApi.MessageSchedulingState schedulingState) {
    if (recordMode == RECORD_MODE_VIDEO_EDIT) {
      closeVideoEditMode(new TdApi.MessageSendOptions(forceDisableNotification, false, schedulingState));
    }
  }

  private void closeVideoEditMode (TdApi.MessageSendOptions sendOptions) {
    if (recordMode != RECORD_MODE_VIDEO_EDIT) {
      return;
    }

    if (!roundFileReceived()) {
      scheduledEditClose = true;
      scheduledSendOptions = sendOptions;
      return;
    }

    if (sendOptions != null && sendOptions.schedulingState == null && targetController != null && targetController.areScheduledOnly()) {
      targetController.pickDateOrProceed(sendOptions.disableNotification, sendOptions.schedulingState, (forceDisableNotification, schedulingState, disableMarkdown) -> closeVideoEditMode(new TdApi.MessageSendOptions(forceDisableNotification, false, schedulingState)));
      return;
    }

    this.recordMode = RECORD_MODE_NONE;

    if (sendOptions != null) {
      if (videoPreviewView.hasTrim()) {
        tdlib.client().send(new TdApi.CancelUploadFile(roundFile.id), tdlib.okHandler());
        double startTimeSeconds = videoPreviewView.getStartTime();
        double endTimeSeconds = videoPreviewView.getEndTime();
        String conversion = VideoGenerationInfo.makeConversion(roundFile.id, false, 0, (long) (startTimeSeconds * 1_000_000), (long) (endTimeSeconds * 1_000_000), true, 0);
        TdApi.InputFileGenerated trimmedFile = new TdApi.InputFileGenerated(roundFile.local.path, conversion, 0);
        sendVideoNote(new TdApi.InputMessageVideoNote(trimmedFile, null, (int) Math.round(endTimeSeconds - startTimeSeconds), VIDEO_NOTE_LENGTH), sendOptions, null);
      } else {
        sendVideoNote(new TdApi.InputMessageVideoNote(new TdApi.InputFileId(roundFile.id), null, savedRoundDuration, VIDEO_NOTE_LENGTH), sendOptions, roundFile);
      }
    } else {
      tdlib.client().send(new TdApi.DeleteFile(roundFile.id), tdlib.silentHandler());
      roundCloseMode = CLOSE_MODE_CANCEL;
    }
    videoPreviewView.setPlaying(false);
    editAnimator.setValue(false, true);

  }
}
