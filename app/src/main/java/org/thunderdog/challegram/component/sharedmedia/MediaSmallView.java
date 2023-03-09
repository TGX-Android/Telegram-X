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
 * File created on 26/12/2016
 */
package org.thunderdog.challegram.component.sharedmedia;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.SelectableItemDelegate;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.SimplestCheckBox;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class MediaSmallView extends SparseDrawableView implements Destroyable, FactorAnimator.Target, SelectableItemDelegate {
  private final ImageReceiver miniThumbnail;
  private final ImageReceiver preview, imageReceiver;
  private final GifReceiver gifReceiver;

  private FileProgressComponent.SimpleListener listener;
  private final BoolAnimator downloadedAnimator = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 230l);

  public MediaSmallView (Context context) {
    super(context);

    this.miniThumbnail = new ImageReceiver(this, 0);
    this.preview = new ImageReceiver(this, 0);
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
  }

  public void setListener (FileProgressComponent.SimpleListener listener) {
    this.listener = listener;
  }

  @Override
  public void performDestroy () {
    setItem(null);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    //noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    layoutReceivers();
  }

  private void layoutReceivers () {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    miniThumbnail.setBounds(0, 0, width, height);
    preview.setBounds(0, 0, width, height);
    imageReceiver.setBounds(0, 0, width, height);
    gifReceiver.setBounds(0, 0, width, height);
  }

  private MediaItem item;

  public void setItem (@Nullable MediaItem item) {
    if (this.item != null) {
      this.item.detachFromView(this);
      if (this.item.isLoading() && (item == null || this.item.getFileId() != item.getFileId()) && this.item.isPhoto()) {
        this.item.pauseAbandonedDownload();
      }
    }
    this.item = item;
    if (item != null) {
      miniThumbnail.requestFile(item.getMiniThumbnail());
      if (item.hasSpoiler()) {
        imageReceiver.clear();
        gifReceiver.clear();
        preview.requestFile(item.getBlurredPreviewImageFile());
      } else {
        preview.requestFile(item.isLoaded() && item.getTargetGifFile() == null ? null : item.getPreviewImageFile());
        imageReceiver.requestFile(item.isLoaded() ? item.getTargetImageFile(false) : null);
        gifReceiver.requestFile(item.isLoaded() ? item.getTargetGifFile() : null);
      }
      downloadedAnimator.setValue(item.isLoaded(), false);
      item.attachToView(this);
      item.setSimpleListener(listener);
      item.download(false);
      setText(item.isVideo() && !item.isGifType() ? Strings.buildDuration(item.getVideoDuration(false, TimeUnit.SECONDS)) : null);
    } else {
      miniThumbnail.requestFile(null);
      preview.requestFile(null);
      imageReceiver.requestFile(null);
      gifReceiver.requestFile(null);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return helper != null && helper.onTouchEvent(this, e);
  }

  private float selectableFactor;

  public void setSelectionFactor (float selectableFactor, float selectionFactor) {
    if (this.selectableFactor != selectableFactor) {
      this.selectableFactor = selectableFactor;
      forceSelectionFactor(selectionFactor);
      invalidate();
    } else {
      forceSelectionFactor(selectionFactor);
    }
  }

  public void setSelectableFactor (float factor) {
    if (this.selectableFactor != factor) {
      this.selectableFactor = factor;
      invalidate();
    }
  }

  private float selectionFactor;
  private FactorAnimator animator;

  public void forceSelectionFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setSelectionFactor(factor);
  }

  public void animateFactor (float factor) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectionFactor);
    }
    animator.animateTo(factor);
  }

  @Override
  public void setIsItemSelected (boolean isSelected, int selectionIndex) {
    animateFactor(isSelected ? 1f : 0f);
  }

  private void setSelectionFactor (float factor) {
    if (this.selectionFactor != factor) {
      this.selectionFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == 1) {
      invalidate();
    } else {
      setSelectionFactor(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private boolean isAttached = true;

  public void attach () {
    if (!isAttached) {
      isAttached = true;
      gifReceiver.attach();
      imageReceiver.attach();
      miniThumbnail.attach();
      preview.attach();
    }
  }

  public void detach () {
    if (isAttached) {
      isAttached = false;
      gifReceiver.detach();
      imageReceiver.detach();
      miniThumbnail.detach();
      preview.detach();
    }
  }

  public void invalidateContent (MediaItem item) {
    if (this.item == item) {
      this.imageReceiver.requestFile(item != null && !item.hasSpoiler() ? item.getTargetImage() : null);
      this.gifReceiver.requestFile(item != null && !item.hasSpoiler() ? item.getTargetGifFile() : null);
    }
  }

  // Duration width

  private String text;
  private int textWidth;

  private void setText (String text) {
    if (this.text == null && text == null) {
      return;
    }
    if (text == null) {
      this.text = null;
      this.textWidth = 0;
      return;
    }
    if (this.text == null || !this.text.equals(text)) {
      this.text = text;
      this.textWidth = (int) U.measureText(text, Paints.whiteMediumPaint(12f, false, true));
    }
  }

  // Event listener

  private static final float SCALE = .24f;

  @Override
  protected void onDraw (Canvas c) {
    if (item == null) {
      return;
    }

    final float selectionFactor = this.selectionFactor * this.selectableFactor;

    final boolean saved = selectionFactor != 0f;
    if (saved) {
      c.drawRect(preview.getLeft(), preview.getTop(), preview.getRight(), preview.getBottom(), Paints.fillingPaint(Theme.chatSelectionColor()));
      c.save();
      final float scale = 1f - (selectionFactor * SCALE);
      c.scale(scale, scale, preview.centerX(), preview.centerY());
    }

    Receiver receiver = imageReceiver.getCurrentFile() != null ? imageReceiver : gifReceiver;
    final boolean scaled = receiver == gifReceiver && item != null && item.getType() == MediaItem.TYPE_VIDEO_MESSAGE;
    if (scaled) {
      c.save();
      c.clipRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
      float scale = (float) Math.sqrt(2);
      c.scale(scale, scale, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    }
    if (receiver.needPlaceholder()) {
      if (preview.needPlaceholder()) {
        if (miniThumbnail.needPlaceholder()) {
          miniThumbnail.drawPlaceholder(c);
        }
        miniThumbnail.draw(c);
      }
      preview.draw(c);
    }
    receiver.draw(c);
    if (scaled) {
      c.restore();
    }

    if (item.hasSpoiler()) {
      DrawAlgorithms.drawRoundRect(c, 0, preview.getLeft(), preview.getTop(), preview.getRight(), preview.getBottom(), Paints.fillingPaint(Theme.getColor(R.id.theme_color_spoilerMediaOverlay)));
      DrawAlgorithms.drawParticles(c, 0, preview.getLeft(), preview.getTop(), preview.getRight(), preview.getBottom(), 1f);
    }

    boolean isStreamingUI = item.isVideo() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE;

    int textLeft = receiver.getLeft() + Screen.dp(7f);
    int textTop = receiver.getTop() + Screen.dp(5f);

    if (text != null) {
      float dlFactor = 1f - downloadedAnimator.getFloatValue();

      RectF rectF = Paints.getRectF();
      rectF.set(
        textLeft - Screen.dp(3f),
        textTop - Screen.dp(2f),
        textLeft + textWidth + Screen.dp(3f) + (isStreamingUI ? Screen.dp(22f) * dlFactor : 0),
        textTop + (isStreamingUI ? MathUtils.fromTo(Screen.dp(15f), Screen.dp(21f), dlFactor) : Screen.dp(15f))
      );

      c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(0x4c000000));
      c.drawText(text, textLeft + (isStreamingUI ? Screen.dp(22f) * dlFactor : 0), textTop + Screen.dp(11f) + (isStreamingUI ? Screen.dp(3.5f) * dlFactor : 0), Paints.whiteMediumPaint(12f, false, false)); // TODO

      item.getFileProgress().setDownloadedIconRes(FileProgressComponent.PLAY_ICON);
      item.getFileProgress().setPausedIconRes(R.drawable.baseline_cloud_download_16);

      if (isStreamingUI) {
        item.getFileProgress().setVideoStreamingProgressHidden(false);
        item.getFileProgress().setVideoStreamingOptions(false, false, FileProgressComponent.STREAMING_UI_MODE_EXTRA_SMALL, rectF, downloadedAnimator);
        item.getFileProgress().setVideoStreamingClippingRect(rectF);
      }
    }

    if (item.isVideo() || item.isGif() || item.getType() == MediaItem.TYPE_VIDEO_MESSAGE) {
      item.drawComponents(this, c, 0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    if (saved) {
      c.restore();
    }

    if (this.selectableFactor != 0f) {
      final int centerX = receiver.centerX() + (int) ((float) receiver.getWidth() * (1f - SCALE)) / 2;
      final int centerY = receiver.centerY() - (int) ((float) receiver.getHeight() * (1f - SCALE)) / 2;
      final int radius = Screen.dp(9f + 2f * selectionFactor);

      final int toColor = ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor());
      c.drawCircle(centerX, centerY, radius, Paints.getOuterCheckPaint(ColorUtils.alphaColor(selectableFactor, ColorUtils.fromToArgb(0xffffffff, toColor, selectionFactor))));

      if (selectionFactor != 0f) {
        SimplestCheckBox.draw(c, centerX, centerY, selectionFactor, null);
      }
    }
  }

  // Click

  private ClickHelper helper;

  public void initWithClickDelegate (ClickHelper.Delegate delegate) {
    helper = new ClickHelper(delegate);
  }
}
