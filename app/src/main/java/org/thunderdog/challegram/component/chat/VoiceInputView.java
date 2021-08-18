/**
 * File created on 04/03/16 at 13:50
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGRecord;
import org.thunderdog.challegram.helper.Recorder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyAudioManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;

public class VoiceInputView extends FrameLayoutFix implements View.OnClickListener, TGLegacyAudioManager.PlayListener, ClickHelper.Delegate, FactorAnimator.Target {
  private static final long VOICE_START_DELAY = 500l;
  private static final long ANIMATION_START_DELAY = 80l;

  public interface Callback {
    void onDiscardVoiceRecord ();
  }

  private Paint textPaint;
  private int textOffset, textRight;
  private int waveLeft;

  private Callback callback;
  private Waveform waveform;
  private ImageView iconView;

  private ClickHelper clickHelper;

  public VoiceInputView (Context context) {
    super(context);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(15f));
    textOffset = Screen.dp(5f);
    textRight = Screen.dp(39f);
    waveLeft = Screen.dp(66f);

    this.clickHelper = new ClickHelper(this);

    this.waveform = new Waveform(null, Waveform.MODE_RECT, false);

    iconView = new ImageView(context);
    iconView.setId(R.id.btn_discard_record);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setImageResource(R.drawable.baseline_delete_24);
    iconView.setColorFilter(Theme.iconColor());
    iconView.setOnClickListener(this);
    iconView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(58f), ViewGroup.LayoutParams.MATCH_PARENT, Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    Views.setClickable(iconView);
    RippleSupport.setTransparentSelector(iconView);

    addView(iconView);

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    if (Lang.rtl()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
      params.leftMargin = Screen.dp(55f);
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
      params.rightMargin = Screen.dp(55f);
    }

    setWillNotDraw(false);
    setLayoutParams(params);
  }

  public void addThemeListeners (ViewController c) {
    c.addThemeFilterListener(iconView, R.id.theme_color_icon);
    c.addThemeInvalidateListener(this);
    ViewSupport.setThemedBackground(this, R.id.theme_color_filling);
  }

  private int calculateWaveformWidth () {
    return getMeasuredWidth() - waveLeft - Screen.dp(110f) + Screen.dp(55f);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (getMeasuredWidth() != 0) {
      waveform.layout(calculateWaveformWidth());
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_discard_record: {
        if (callback != null) {
          callback.onDiscardVoiceRecord();
        }
        break;
      }
    }
  }

  @Override
  public void onPlayPause (int fileId, boolean isPlaying, boolean isUpdate) {
    if (record != null && record.getFileId() == fileId && !isPlaying && !isCaught && !ignoreStop) {
      progress = 1f;
      updateProgress();
      invalidate();
    }
  }

  @Override
  public boolean needPlayProgress (int fileId) {
    return true;
  }

  @Override
  public void onPlayProgress (int fileId, float progress, boolean isUpdate) {
    if (record != null && record.getFileId() == fileId && progress > 0f) {
      this.progress = progress;
      if (!updateProgress()) {
        invalidate();
      }
    }
  }

  // Waveform and record stuff

  private boolean ignoreStop;

  public void ignoreStop () {
    ignoreStop = true;
  }

  public void clearData () {
    discardRecord();
    ignoreStop = false;
    progress = 0f;
    hasStartedPlaying = false;
    waveform.setData(null);
    invalidate();
  }

  private TGRecord record;

  private void setRecord (final TGRecord record) {
    if (this.record != record) {
      if (this.record != null) {
        TGLegacyAudioManager.instance().unsubscribe(this.record.getAudio().getId(), this);
      }
      this.record = record;
      if (record != null) {
        TGLegacyAudioManager.instance().subscribe(record.getAudio().getId(), this);
      }
    }
  }

  public void processRecord (final TGRecord record) {
    setRecord(record);
    setDuration(record.getDuration());
    Background.instance().post(() -> {
      final byte[] waveform = record.getWaveform() != null ? record.getWaveform() : N.getWaveform(record.getPath());
      if (waveform != null) {
        UI.post(() -> setWaveform(record, waveform));
      }
    });
  }

  public TGRecord getRecord () {
    TGRecord record = this.record;
    setRecord(null);
    return record;
  }

  // Waveform animation

  private boolean hasStartedPlaying;

  private final FactorAnimator waveformAnimator = new FactorAnimator(0, this, overshoot, OPEN_DURATION);

  private void setWaveform (final TGRecord record, byte[] waveform) {
    if (this.record == null || !this.record.equals(record)) {
      return;
    }
    record.setWaveform(waveform);
    this.waveform.setData(waveform);
    waveformAnimator.forceFactor(0f);
    waveformAnimator.setStartDelay(ANIMATION_START_DELAY);
    waveformAnimator.animateTo(1f);
    hasStartedPlaying = false;
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setExpand(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private void playPause () {
    if (!hasStartedPlaying) {
      hasStartedPlaying = true;
      updateProgress();
      invalidate();
      playVoice(record);
    } else {
      /*if (Config.USE_NEW_PLAYER) {
        // TODO
      } else {
        Player.instance().playPause(record.getAudio(), true);
      }*/
    }
  }

  private void playVoice (TGRecord record) {
    if (this.record == null || !this.record.equals(record)) {
      return;
    }

    /*if (Config.USE_NEW_PLAYER) {
      // TODO
    } else {
      Player.instance().destroy();
      Player.instance().playPause(record.getAudio(), true);
    }*/
  }

  public float getExpand () {
    return waveform.getExpand();
  }

  public void setExpand (float expand) {
    waveform.setExpand(expand);
    invalidateWave();
  }

  private void invalidateWave () {
    invalidate(waveLeft, 0, waveLeft + waveform.getWidth(), getMeasuredHeight());
  }

  // Duration shit

  private float progress;
  private int duration = -1;
  private int seek = -1;
  private String seekStr;

  public void setDuration (int duration) {
    if (this.duration != duration) {
      this.duration = duration;
      updateProgress();
    }
  }

  private boolean updateProgress () {
    int seek = (int) ((float) duration * (hasStartedPlaying ? progress : 1f));
    if (this.seek != seek) {
      this.seek = seek;
      seekStr = Strings.buildDuration(seek);
      invalidate();
      return true;
    }
    return false;
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int centerY = (int) ((float) height * .5f);

    if (seekStr != null) {
      textPaint.setColor(Theme.textAccentColor());
      c.drawText(seekStr, width - textRight, centerY + textOffset, textPaint);
    }

    waveform.draw(c, !hasStartedPlaying ? 1f : progress, waveLeft, centerY);
  }

  // Record utils

  public void discardRecord () {
    if (record != null) {
      Recorder.instance().delete(record);
      setRecord(null);
    }
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return waveform != null && record != null && x >= waveLeft && x < waveLeft + waveform.getWidth();
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (waveform != null && record != null && x >= waveLeft && x < waveLeft + waveform.getWidth()) {
      playPause();
    }
  }

  // Touch events

  private boolean isCaught;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        isCaught = false;

        if (Lang.rtl()) {
          if (e.getX() > getMeasuredWidth()) {
            return false;
          }
        } else {
          if (e.getX() < ((RelativeLayout.LayoutParams) getLayoutParams()).leftMargin) {
            return false;
          }
        }
      }
    }

    return clickHelper.onTouchEvent(this, e);
  }

  // Close Animation

  private static final AnticipateOvershootInterpolator overshoot = new AnticipateOvershootInterpolator(3.0f);

  private float collapse;

  public float getCollapse () {
    return collapse;
  }

  public void setCollapse (float collapse) {
    if (this.collapse != collapse) {
      this.collapse = collapse;
      if (waveform != null) {
        waveform.setExpand(1f - overshoot.getInterpolation(collapse));
        invalidateWave();
      }
    }
  }

  private ValueAnimator cancel;
  private static final long CLOSE_DURATION = 350l;
  private static final long FADE_DURATION = 150l;
  private static final long OPEN_DURATION = 350l;

  public void animateClose () {
    collapse = 0f;

    final long startDelay;

    if (waveform != null && waveform.getMaxSample() != 0) {
      final float startFactor = getCollapse();
      final float diffFactor = 1f - startFactor;
      cancel = AnimatorUtils.simpleValueAnimator();
      cancel.addUpdateListener(animation -> setCollapse(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
      cancel.setDuration(CLOSE_DURATION);
      cancel.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
      cancel.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          cancel = null;
        }
      });
      startDelay = CLOSE_DURATION - FADE_DURATION;
    } else {
      cancel = null;
      startDelay = 0l;
    }
    Views.animateAlpha(this, 0f, FADE_DURATION, startDelay, AnimatorUtils.DECELERATE_INTERPOLATOR, new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        setVisibility(View.GONE);
      }
    });
    if (cancel != null) {
      cancel.start();
    }
  }

  public void cancelCloseAnimation () {
    if (cancel != null) {
      cancel.cancel();
      cancel = null;
    }
    Views.clearAnimations(this);
  }
}
