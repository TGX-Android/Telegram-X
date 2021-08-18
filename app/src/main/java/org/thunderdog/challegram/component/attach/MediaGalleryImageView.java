package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 21/10/2016
 * Author: default
 */

public class MediaGalleryImageView extends View implements Destroyable, FactorAnimator.Target, ClickHelper.Delegate, ImageFile.ChangeListener {
  private static final float SCALE = .24f;

  public interface ClickListener {
    void onClick (View view, boolean isSelect);
  }

  // Children
  private final ImageReceiver receiver;

  // Data
  private ImageFile image;
  private int width, height;

  private final ClickHelper helper;
  private ClickListener listener;

  public MediaGalleryImageView (Context context) {
    super(context);

    helper = new ClickHelper(this);

    receiver = new ImageReceiver(this, 0);
    receiver.prepareToBeCropped();
    receiver.setAnimationDisabled(true);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void setClickListener (ClickListener listener) {
    this.listener = listener;
  }

  private boolean isInvisible;

  public void setInvisible (boolean isInvisible, boolean needInvalidate) {
    if (this.isInvisible != isInvisible) {
      this.isInvisible = isInvisible;
      invalidate();
    }
  }

  public void setAnimationsDisabled (boolean disabled) {
    receiver.setAnimationDisabled(disabled);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return listener != null;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (listener != null) {
      int size = Screen.dp(24f) * 2;
      listener.onClick(view, y <= size && x >= getMeasuredWidth() - size);
    }
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    return onLongClickListener != null && onLongClickListener.onLongClick(view);
  }

  private OnLongClickListener onLongClickListener;

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    this.onLongClickListener = l;
  }

  // Attach/Detach

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return helper.onTouchEvent(this, event);
  }

  // Image

  private String duration;
  private int durationWidth;

  private int selectionIndex = -1;
  private String selectionIndexStr;

  @Override
  public void onImageChanged (ImageFile file) {
    if (this.image == file) {
      this.receiver.clear();
      this.receiver.requestFile(image);
    }
  }

  public void setImage (ImageFile image, int selectionIndex, boolean isCheckable) {
    if (this.image == image) {
      return;
    }
    if (this.image != null) {
      this.image.removeChangeListener(this);
    }
    this.image = image;
    if (image != null) {
      image.addChangeListener(this);
    }
    receiver.requestFile(image);
    if (image instanceof ImageGalleryFile && ((ImageGalleryFile) image).isVideo()) {
      ImageGalleryFile videoFile = (ImageGalleryFile) image;
      int trimmedDuration = videoFile.getVideoDuration(false);
      int totalDuration = videoFile.getVideoDuration(false);
      if (trimmedDuration < totalDuration) {
        duration = Lang.getString(R.string.format_trimmedDuration, Strings.buildDuration(trimmedDuration), Strings.buildDuration(totalDuration));
      } else {
        duration = Strings.buildDuration(totalDuration);
      }
      durationWidth = (int) U.measureText(duration, Paints.whiteMediumPaint(12f, false, true));
    } else {
      duration = null;
    }
    setChecked(selectionIndex, false);
    setInvisible(!isCheckable, false);
    setSelectionIndex(selectionIndex);
    invalidate();
  }

  public void setSelectionIndex (int index) {
    if (this.selectionIndex != index && index >= 0) {
      this.selectionIndex = index;
      this.selectionIndexStr = String.valueOf(index + 1);
      invalidate();
    }
  }

  public ImageFile getImage () {
    return image;
  }

  // CheckView

  public void setChecked (int selectionIndex, boolean animated) {
    if (selectionIndex >= 0) {
      setSelectionIndex(selectionIndex);
      setChecked(true, animated);
    } else {
      setChecked(false, animated);
    }
  }

  private boolean isChecked;

  private void setChecked (boolean isChecked, boolean animated) {
    if (this.isChecked != isChecked) {
      this.isChecked = isChecked;
      if (animated) {
        animateFactor(isChecked ? 1f : 0f);
      } else {
        forceFactor(isChecked ? 1f : 0f);
      }
    }
  }

  private FactorAnimator animator;

  private void animateFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
    }
    animator.animateTo(toFactor);
  }

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private float factor;
  private SimplestCheckBox checkBox;

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factor > 0f && checkBox == null) {
        checkBox = SimplestCheckBox.newInstance(factor, selectionIndexStr);
      }
      invalidate();
    }
  }

  // Metrics

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    if (this.width != width || this.height != height) {
      this.width = width;
      this.height = height;
      setReceiverBounds();
    }
  }

  private void setReceiverBounds () {
    receiver.setBounds(0, 0, width, height);
  }

  public int getReceiverOffset () {
    return factor != 0f ? (receiver.getWidth() - (int) ((float) receiver.getWidth() * (1f - factor * SCALE))) / 2 : 0;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (factor != 0f || receiver.needPlaceholder()) {
      c.drawRect(0, 0, width, height, Paints.fillingPaint(Theme.chatSelectionColor()));
    }

    final boolean saved = factor != 0f;
    if (saved) {
      final float scale = 1f - (factor * SCALE);
      c.save();
      c.scale(scale, scale, receiver.centerX(), receiver.centerY());
    }

    receiver.draw(c);
    if (duration != null && !duration.isEmpty()) {
      int textLeft = receiver.getLeft() + Screen.dp(7f);
      int textTop = receiver.getTop() + Screen.dp(5f);
      RectF rectF = Paints.getRectF();
      rectF.set(textLeft - Screen.dp(3f), textTop - Screen.dp(2f), textLeft + durationWidth + Screen.dp(3f), textTop + Screen.dp(15f));
      c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(0x4c000000));
      c.drawText(duration, textLeft, textTop + Screen.dp(11f), Paints.whiteMediumPaint(12f, false, false));
    }

    if (saved) {
      c.restore();
    }

    if (!isInvisible) {
      final int centerX = receiver.centerX() + (int) ((float) receiver.getWidth() * (1f - SCALE)) / 2;
      final int centerY = receiver.centerY() - (int) ((float) receiver.getHeight() * (1f - SCALE)) / 2;
      final int radius = Screen.dp(9f + 2f * factor);

      final int toColor = ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor());
      c.drawCircle(centerX, centerY, radius, Paints.getOuterCheckPaint(ColorUtils.alphaColor(1f, ColorUtils.fromToArgb(0xffffffff, toColor, factor))));

      if (factor != 0f && checkBox != null) {
        SimplestCheckBox.draw(c, centerX, centerY, factor, selectionIndexStr, checkBox);
      }
    }
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
    if (checkBox != null) {
      checkBox.destroy();
      checkBox = null;
    }
    factor = 0f;
  }
}
