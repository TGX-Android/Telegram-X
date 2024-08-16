/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 30/06/2024
 */
package org.thunderdog.challegram.mediaview.disposable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.ExoPlayer;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.CircleFrameLayout;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

class DisposableMediaViewControllerVideo extends DisposableMediaViewController {
  public DisposableMediaViewControllerVideo (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private CircleFrameLayout mainPlayerView;
  private View mainTextureView;

  private DoubleImageReceiver previewReceiver;

  @Override
  protected FrameLayoutFix onCreateContentView (Context context) {
    contentView = new ContentView(context);

    final int textureSize = getVideoSize();
    previewReceiver = new DoubleImageReceiver(contentView, textureSize / 2);
    tgMessage.requestPreview(previewReceiver);

    mainPlayerView = new CircleFrameLayout(context);
    mainPlayerView.setAlpha(getRevealFactor());
    mainPlayerView.setLayoutParams(FrameLayoutFix.newParams(textureSize, textureSize));
    mainPlayerView.setPivotX(0);
    mainPlayerView.setPivotY(0);

    if (RoundVideoController.USE_SURFACE) {
      mainTextureView = new SurfaceView(context);
    } else {
      mainTextureView = new TextureView(context);
    }
    mainTextureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    mainPlayerView.addView(mainTextureView);

    contentView.addView(mainPlayerView);

    return contentView;
  }

  @Override
  protected void onExoPlayerCreated (ExoPlayer exoPlayer) {
    if (mainTextureView instanceof SurfaceView) {
      exoPlayer.setVideoSurfaceView((SurfaceView) mainTextureView);
    } else {
      exoPlayer.setVideoTextureView((TextureView) mainTextureView);
    }
  }

  private RectF revealStartPosition;
  private final RectF revealPosition = new RectF();
  private final RectF progressBounds = new RectF();
  private final Rect revealPositionBounds = new Rect();
  private final RectF revealEndPosition = new RectF();
  private float removeDegrees;

  private static final int[] tmpCords = new int[2];

  @Override
  public void prepareShowAnimation () {
    if (anchorView != null) {
      revealStartPosition = new RectF();
      anchorView.getLocationOnScreen(tmpCords);

      final int left = tmpCords[0] + tgMessage.getContentX();
      final int top = tmpCords[1] + tgMessage.getContentY();
      final int width = tgMessage.getChildrenWidth();
      final int height = tgMessage.getChildrenHeight();

      revealStartPosition.set(left, top, left + width, top + height);
    } else {
      revealStartPosition = null;
    }

    onChangeRevealFactor(getRevealFactor());
    super.prepareShowAnimation();
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    revealStartPosition.set(revealEndPosition);
    return super.launchHideAnimation(popup, originalAnimator);
  }

  @Override
  protected void onChangeRevealFactor (float factor) {
    final int viewWidth = contentView.getMeasuredWidth();
    final int viewHeight = contentView.getMeasuredHeight();
    final int videoSize = getVideoSize();
    if (viewWidth == 0 || viewHeight == 0) {
      return;
    }

    revealEndPosition.set(
      (viewWidth - videoSize) / 2f,
      (viewHeight - videoSize) / 2f,
      (viewWidth + videoSize) / 2f,
      (viewHeight + videoSize) / 2f
    );

    if (revealStartPosition == null) {
      revealStartPosition = new RectF(revealEndPosition);
    }

    revealPosition.set(
      MathUtils.fromTo(revealStartPosition.left, revealEndPosition.left, factor),
      MathUtils.fromTo(revealStartPosition.top, revealEndPosition.top, factor),
      MathUtils.fromTo(revealStartPosition.right, revealEndPosition.right, factor),
      MathUtils.fromTo(revealStartPosition.bottom, revealEndPosition.bottom, factor)
    );
    revealPosition.round(revealPositionBounds);
    previewReceiver.setBounds(revealPositionBounds.left, revealPositionBounds.top, revealPositionBounds.right, revealPositionBounds.bottom);

    progressBounds.set(revealPosition);
    progressBounds.inset(-Screen.dp(10), -Screen.dp(10));

    mainPlayerView.setAlpha(factor);
    mainPlayerView.setTranslationX(revealPosition.left);
    mainPlayerView.setTranslationY(revealPosition.top);
    mainPlayerView.setScaleX(revealPosition.width() / revealEndPosition.width());
    mainPlayerView.setScaleY(revealPosition.height() / revealEndPosition.height());

    final double removeDistance = Paints.videoStrokePaint().getStrokeWidth();
    final double totalDistance = (int) (2.0 * Math.PI * (double) (progressBounds.width() / 2f));
    this.removeDegrees = (float) (removeDistance / totalDistance) * 360f;

    context.setPhotoRevealFactor(factor);
    contentView.setBackgroundColor(ColorUtils.alphaColor(factor * 0.9f, Color.BLACK));
    contentView.invalidate();
  }

  @Override
  protected void onChangeVisualProgress (float progress) {
    contentView.invalidate();
  }

  @Override
  protected long calculateProgressTickDelay (long playDuration) {
    return U.calculateDelayForDiameter(getVideoSize(), playDuration);
  }

  @Override
  public int getId () {
    return 0;
  }

  @Override
  public void destroy () {
    previewReceiver.destroy();
    super.destroy();
  }



  /* Content */

  public class ContentView extends FrameLayoutFix {
    private static final int STROKE_WIDTH = 4;
    private final Paint strokePaint;
    private final Drawable drawable;

    public ContentView (@NonNull Context context) {
      super(context);
      strokePaint = new Paint(Paints.videoStrokePaint());
      drawable = Drawables.get(context.getResources(), R.drawable.baseline_hot_once_24);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      onChangeRevealFactor(getRevealFactor());
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      previewReceiver.attach();
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      previewReceiver.detach();
    }

    @Override
    protected void dispatchDraw (@NonNull Canvas canvas) {
      final float alpha = getRevealFactor();
      final float decorationsAlpha = alpha * alpha;

      Drawables.drawCentered(canvas, drawable, revealPosition.centerX(), revealPosition.top - Screen.dp(38), PorterDuffPaint.get(ColorId.white, decorationsAlpha));

      previewReceiver.setAlpha(alpha);
      if (previewReceiver.needPlaceholder()) {
        previewReceiver.drawPlaceholderRounded(canvas, revealPositionBounds.width() / 2f);
      }
      previewReceiver.draw(canvas);

      strokePaint.setStrokeWidth(Screen.dp(STROKE_WIDTH));
      strokePaint.setColor(ColorUtils.alphaColor(decorationsAlpha, Color.WHITE));

      canvas.drawArc(progressBounds, -90, (360f - removeDegrees) * (getVisualProgress() - 1f), false, strokePaint);

      super.dispatchDraw(canvas);
    }
  }

  private static int getVideoSize () {
    return Math.min(Screen.smallestSide() - Screen.dp(80), Screen.dp(640));
  }
}
