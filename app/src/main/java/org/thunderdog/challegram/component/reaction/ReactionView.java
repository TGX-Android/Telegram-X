package org.thunderdog.challegram.component.reaction;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class ReactionView extends View implements FactorAnimator.Target, Destroyable {
  public static final float PADDING = 8f;
  private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3.2f);

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private final FactorAnimator animator;
  private @Nullable
  TGStickerObj sticker;
  private Path contour;

  public ReactionView (Context context) {
    super(context);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
    this.animator = new FactorAnimator(0, this, OVERSHOOT_INTERPOLATOR, 230l);
  }

  private boolean isAnimation;

  public void setSticker (@Nullable TGStickerObj sticker) {
    this.sticker = sticker;
    resetStickerState();
    ImageFile imageFile = sticker != null && !sticker.isEmpty() ? sticker.getImage() : null;
    GifFile gifFile = sticker != null && !sticker.isEmpty() ? sticker.getPreviewAnimation() : null;
    if ((sticker == null || sticker.isEmpty()) && imageFile != null) {
      throw new RuntimeException("");
    }
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
    imageReceiver.requestFile(imageFile);
    if (gifFile != null) {
      gifFile.setPlayOnce(true);
    }
    gifReceiver.requestFile(gifFile);
  }

  public void startAnimation () {
    this.isAnimation = sticker != null && sticker.isAnimated();
  }

  public void stopAnimation () {
    this.isAnimation = false;
  }

  public void refreshSticker () {
    setSticker(sticker);
  }

  public void attach () {
    imageReceiver.attach();
    gifReceiver.attach();
  }

  public void detach () {
    imageReceiver.detach();
    gifReceiver.detach();
  }

  @Override
  public void performDestroy () {
    imageReceiver.destroy();
    gifReceiver.destroy();
  }

  private float factor;

  private void resetStickerState () {
    animator.forceFactor(0f, true);
    factor = 0f;
  }

  private static final float MIN_SCALE = .82f;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  private boolean isTrending;

  public void setIsTrending () {
    isTrending = true;
  }

  private boolean isSuggestion;

  public void setIsSuggestion () {
    isSuggestion = true;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (isSuggestion) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(72f), MeasureSpec.EXACTLY), heightMeasureSpec);
    } else if (isTrending) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.smallestSide() / 5, MeasureSpec.EXACTLY));
    } else {
      //noinspection SuspiciousNameCombination
      super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
    int padding = Screen.dp(PADDING);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    imageReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    gifReceiver.setBounds(padding, padding + getPaddingTop(), width - padding, height - getPaddingBottom() - padding);
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
  }

  @Override
  protected void onDraw (Canvas c) {
    boolean saved = factor != 0f;
    if (saved) {
      c.save();
      float scale = MIN_SCALE + (1f - MIN_SCALE) * (1f - factor);
      int cx = getMeasuredWidth() / 2;
      int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingBottom()) / 2;
      c.scale(scale, scale, cx, cy);
    }
    if (isAnimation) {
      if (gifReceiver.needPlaceholder()) {
        if (imageReceiver.needPlaceholder()) {
          imageReceiver.drawPlaceholderContour(c, contour);
        }
        imageReceiver.draw(c);
      }
      gifReceiver.draw(c);
    } else {
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderContour(c, contour);
      }
      imageReceiver.draw(c);
    }
    if (saved) {
      c.restore();
    }
  }

  public @Nullable TGStickerObj getSticker () {
    return sticker;
  }
}
