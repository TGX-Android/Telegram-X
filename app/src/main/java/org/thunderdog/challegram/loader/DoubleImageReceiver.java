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
 * File created on 01/04/2017
 */
package org.thunderdog.challegram.loader;

import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.loader.gif.GifReceiver;

public class DoubleImageReceiver implements Receiver {
  private final boolean isAnimated;
  private final ImageReceiver preview;
  private final Receiver receiver;

  public DoubleImageReceiver (View view, int radius) {
    this(view, radius, false);
  }

  public DoubleImageReceiver (View view, int radius, boolean animated) {
    this.isAnimated = animated;
    this.preview = new ImageReceiver(view, radius);
    if (animated) {
      this.receiver = new GifReceiver(view);
    } else {
      this.receiver = new ImageReceiver(view, radius);
    }
  }

  /** @noinspection unchecked*/
  @Override
  public final DoubleImageReceiver setUpdateListener (ReceiverUpdateListener listener) {
    preview.setUpdateListener(listener);
    receiver.setUpdateListener(listener);
    return this;
  }

  public void setAnimationDisabled (boolean disabled) {
    this.preview.setAnimationDisabled(disabled);
    this.receiver.setAnimationDisabled(disabled);
  }

  @Override
  public void setRadius (float radius) {
    if (isAnimated)
      throw new UnsupportedOperationException();
    this.preview.setRadius(radius);
    getImageReceiver().setRadius(radius);
  }

  public Receiver getReceiver () {
    return receiver;
  }

  public ImageReceiver getImageReceiver () {
    if (isAnimated)
      throw new IllegalStateException();
    return (ImageReceiver) receiver;
  }

  public GifReceiver getGifReceiver () {
    if (!isAnimated)
      throw new IllegalStateException();
    return (GifReceiver) receiver;
  }

  public ImageReceiver getPreview () {
    return preview;
  }

  @Override
  public boolean isEmpty () {
    return preview.isEmpty() && receiver.isEmpty();
  }

  @Override
  public void attach () {
    preview.attach();
    receiver.attach();
  }

  @Override
  public void detach () {
    preview.detach();
    receiver.detach();
  }

  @Override
  public void destroy () {
    preview.destroy();
    receiver.destroy();
  }

  @Override
  public void clear () {
    preview.clear();
    receiver.clear();
  }

  @Override
  public float getPaintAlpha () {
    return receiver.getPaintAlpha();
  }

  @Override
  public void setPaintAlpha (float alpha) {
    preview.setPaintAlpha(alpha * preview.getPaintAlpha());
    receiver.setPaintAlpha(alpha * receiver.getPaintAlpha());
  }

  @Override
  public void setColorFilter (int colorFilter) {
    preview.setColorFilter(colorFilter);
    receiver.setColorFilter(colorFilter);
  }

  @Override
  public void disableColorFilter () {
    preview.disableColorFilter();
    receiver.disableColorFilter();
  }

  @Override
  public void restorePaintAlpha () {
    preview.restorePaintAlpha();
    receiver.restorePaintAlpha();
  }

  public float getFullLoadFactor () {
    return !isAnimated && receiver.needPlaceholder() ? getImageReceiver().getDisplayAlpha() : 1f;
  }

  @Override
  public boolean needPlaceholder () {
    return preview.needPlaceholder() && receiver.needPlaceholder();
  }

  @Override
  public void drawPlaceholder (Canvas c) {
    preview.drawPlaceholder(c);
  }

  @Override
  public boolean setBounds (int left, int top, int right, int bottom) {
    preview.setBounds(left, top, right, bottom);
    return receiver.setBounds(left, top, right, bottom);
  }

  public void requestFile (ImageFile preview, ImageFile image) {
    this.preview.requestFile(preview);
    getImageReceiver().requestFile(image);
  }

  public void draw (Canvas c, float fullAllowance) {
    if (fullAllowance == 0f) {
      preview.draw(c);
    } else if (fullAllowance == 1f) {
      draw(c);
    } else {
      preview.draw(c);
      receiver.setPaintAlpha(receiver.getPaintAlpha() * fullAllowance);
      receiver.draw(c);
      receiver.restorePaintAlpha();
    }
  }

  public void draw (Canvas c) {
    if (receiver.needPlaceholder()) {
      preview.draw(c);
    }
    receiver.draw(c);
  }

  @Override
  public View getTargetView () {
    return receiver.getTargetView();
  }

  @Override
  public int getTargetWidth () {
    return receiver.getTargetWidth();
  }

  @Override
  public int getTargetHeight () {
    return receiver.getTargetHeight();
  }

  @Override
  public int getLeft () {
    return receiver.getLeft();
  }

  public float getRadius () {
    return isAnimated ? 0 : getImageReceiver().getRadius();
  }

  @Override
  public int getTop () {
    return receiver.getTop();
  }

  @Override
  public int getRight () {
    return receiver.getRight();
  }

  @Override
  public int getBottom () {
    return receiver.getBottom();
  }

  @Override
  public int centerX () {
    return receiver.centerX();
  }

  @Override
  public int centerY () {
    return receiver.centerY();
  }

  @Override
  public void setTag (Object tag) {
    receiver.setTag(tag);
  }

  @Override
  public Object getTag () {
    return receiver.getTag();
  }

  @Override
  public int getWidth () {
    return receiver.getWidth();
  }

  @Override
  public int getHeight () {
    return receiver.getHeight();
  }

  @Override
  public void setAlpha (float alpha) {
    preview.setAlpha(alpha);
    receiver.setAlpha(alpha);
  }

  @Override
  public float getAlpha () {
    return receiver.getAlpha();
  }

  @Override
  public void forceBoundsLayout () {
    preview.forceBoundsLayout();
    receiver.forceBoundsLayout();
  }

  @Override
  public boolean isInsideContent (float x, float y, int emptyWidth, int emptyHeight) {
    return receiver.isInsideContent(x, y, emptyWidth, emptyHeight);
  }

  @Override
  public void invalidate () {
    receiver.invalidate();
  }
}
