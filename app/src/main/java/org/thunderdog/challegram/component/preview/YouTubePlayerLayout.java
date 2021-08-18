/**
 * File created on 25/02/16 at 19:24
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.preview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.youtube.player.YouTubePlayer;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class YouTubePlayerLayout extends FrameLayoutFix implements YouTubeFragmentHelper.Callback {
  private int currentWidth, currentHeight;
  private YouTubeFragmentHelper helper;
  private YouTubeFragment fragment;
  private YouTubePlayerControls controls;

  private int errorWidth;
  private String errorString;

  private ImageReceiver receiver;
  private ImageFile preview;

  // resources

  private int errorOffset;

  private Paint blackPaint;
  private Paint textPaint;

  public YouTubePlayerLayout (Context context) {
    super(context);

    setId(R.id.youtube_container);
    setClipChildren(false);

    // Thumbnail receiver

    receiver = new ImageReceiver(this, 0);

    // Sizes

    errorOffset = Screen.dp(10f);

    // Paints

    blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    blackPaint.setStyle(Paint.Style.FILL);
    blackPaint.setColor(0xff000000);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    textPaint.setColor(0xffffffff);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(15f));

    setClipChildren(false);
    setWillNotDraw(false);
  }

  private View firstChild;
  private boolean youtubeReady;

  @Override
  public void addView (View child, int index, ViewGroup.LayoutParams params) {
    super.addView(child, 0, params);
    if (!youtubeReady && firstChild == null && getChildCount() == 2) {
      child.setAlpha(0f);
      controls.setAlpha(0f);
      firstChild = child;
    }
  }

  public int getCurrentWidth () {
    return currentWidth;
  }

  public YouTubePlayer getPlayer () {
    return fragment == null ? null : fragment.getPlayer();
  }

  public void setPreview (ImageFile file) {
    this.preview = file;
    receiver.requestFile(currentReceiverFile = file);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    receiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    receiver.detach();
  }

  public void loadVideo (String videoId) {
    if (helper == null) {
      helper = new YouTubeFragmentHelper(videoId, controls, this);
    }
    this.fragment = YouTubeFragment.newInstance(helper);
    ((BaseActivity) getContext()).getFragmentManager().beginTransaction().add(R.id.youtube_container, fragment).commit();
  }

  @Override
  public void onYouTubeReady () {
    youtubeReady = true;
    if (firstChild != null) {
      if (YouTubePreviewLayout.ALLOW_FULLSCREEN) {
        UI.setFullscreenIfNeeded(firstChild);
      }
      YouTube.patchYouTubePlayer(firstChild);
      controls.setAlpha(0f);
      firstChild.setAlpha(0f);
      Views.animateAlpha(controls, 1f, 200l, AnimatorUtils.DECELERATE_INTERPOLATOR, null);
      Views.animateAlpha(firstChild, 1f, 200l, AnimatorUtils.DECELERATE_INTERPOLATOR, new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          receiver.requestFile(currentReceiverFile = null);
        }
      });
    } else {
      receiver.requestFile(currentReceiverFile = null);
    }
  }

  public boolean isReady () {
    return youtubeReady;
  }

  @Override
  public void onYouTubeError (String error) {
    setError(error);
  }

  private void setError (String error) {
    if (fragment != null) {
      ((BaseActivity) getContext()).getFragmentManager().beginTransaction().remove(fragment).commit();
      fragment = null;
    }
    this.errorWidth = error == null ? 0 : (int) U.measureText(error, textPaint);
    this.errorString = error;
    postInvalidate();
  }

  public void setCurrentSize (int width, int height) {
    this.currentWidth = width;
    this.currentHeight = height;
  }

  public void setControls (YouTubePlayerControls controls) {
    this.controls = controls;
  }

  private YouTubePreviewLayout parentLayout;

  public void setParentLayout (YouTubePreviewLayout layout) {
    this.parentLayout = layout;
  }

  @Override
  public void onYouTubeFatalError () {
    parentLayout.forceClose(true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(currentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(currentHeight, MeasureSpec.EXACTLY));
    receiver.setBounds(0, 0, currentWidth, currentHeight);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (currentReceiverFile != null || fragment == null || fragment.isStopped() || (firstChild != null && firstChild.getAlpha() != 1f) || (controls != null && controls.getAlpha() != 1f)) {
      c.drawRect(0, 0, currentWidth, currentHeight, blackPaint);
      receiver.draw(c);
    }
    if (errorString != null) {
      blackPaint.setAlpha(0x99);
      c.drawRect(0, 0, currentWidth, currentHeight, blackPaint);
      blackPaint.setAlpha(0xff);
      c.drawText(errorString, (int) ((float) currentWidth * .5f - (float) errorWidth * .5f), (int) ((float) currentHeight * .5f + errorOffset), textPaint);
    }
  }

  private ImageFile currentReceiverFile;

  public void showThumb () {
    if (firstChild != null) {
      firstChild.setVisibility(View.INVISIBLE);
      receiver.requestFile(currentReceiverFile = preview);
    }
  }

  public void hideThumb () {
    receiver.requestFile(currentReceiverFile = null);
    if (firstChild != null) {
      firstChild.setVisibility(View.VISIBLE);
    }
  }

  // touching

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return true;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    super.onTouchEvent(e);
    return controls != null && controls.processTouchEvent(e);
  }

  // Public interrupts

  public void seekToMillis (int millis) {
    if (fragment != null && fragment.getPlayer() != null) {
      fragment.getPlayer().seekToMillis(millis);
    }
  }

  // Cleaning up resources

  public void onPrepareDestroy () {
    if (fragment != null) {
      fragment.destroy();
      fragment = null;
    }
  }

  public void onDestroy () {
    receiver.requestFile(currentReceiverFile = null);
  }
}
