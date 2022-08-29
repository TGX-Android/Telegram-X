/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.lambda.Destroyable;

public class PageBlockView extends BaseView implements Destroyable, InvalidateContentProvider {
  public static final int MODE_IMAGE = 1;
  public static final int MODE_GIF = 2;
  public static final int MODE_COLLAGE = 3;
  public static final int MODE_AVATAR = 4;

  private PageBlock block;

  private int mode;

  private final ComplexReceiver iconReceiver;
  private DoubleImageReceiver preview;
  private ImageReceiver receiver;
  private GifReceiver gifReceiver;
  private ComplexReceiver multipleReceiver;

  public PageBlockView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    Views.setClickable(this);
    RippleSupport.setTransparentSelector(this);
    iconReceiver = new ComplexReceiver(this);
  }

  private View.OnClickListener onClickListener;
  private View.OnLongClickListener onLongClickListener;

  public void setClickListener (View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
    this.onClickListener = onClickListener;
    this.onLongClickListener = onLongClickListener;
  }

  public void initWithMode (int mode) {
    if (this.mode != mode) {
      this.mode = mode;
      switch (mode) {
        case MODE_IMAGE: {
          this.preview = new DoubleImageReceiver(this, 0);
          this.receiver = new ImageReceiver(this, 0);
          break;
        }
        case MODE_GIF: {
          this.preview = new DoubleImageReceiver(this, 0);
          this.gifReceiver = new GifReceiver(this);
          break;
        }
        case MODE_COLLAGE: {
          this.multipleReceiver = new ComplexReceiver(this);
          break;
        }
        case MODE_AVATAR: {
          this.preview = new DoubleImageReceiver(this, Screen.dp(40f) / 2);
          this.receiver = new ImageReceiver(this, Screen.dp(40f) / 2);
          break;
        }
      }
    }
  }

  public ComplexReceiver getMultipleReceiver () {
    return multipleReceiver;
  }

  @Override
  public void performDestroy () {
    iconReceiver.performDestroy();
    switch (mode) {
      case MODE_IMAGE:
      case MODE_AVATAR: {
        preview.destroy();
        receiver.destroy();
        break;
      }
      case MODE_GIF: {
        preview.destroy();
        gifReceiver.destroy();
        break;
      }
      case MODE_COLLAGE: {
        multipleReceiver.performDestroy();
        break;
      }
    }
  }

  public void attach () {
    iconReceiver.attach();
    switch (mode) {
      case MODE_IMAGE:
      case MODE_AVATAR: {
        preview.attach();
        receiver.attach();
        break;
      }
      case MODE_GIF: {
        preview.attach();
        gifReceiver.attach();
        break;
      }
      case MODE_COLLAGE: {
        multipleReceiver.attach();
        break;
      }
    }
  }

  public void detach () {
    iconReceiver.detach();
    switch (mode) {
      case MODE_IMAGE:
      case MODE_AVATAR: {
        preview.detach();
        receiver.detach();
        break;
      }
      case MODE_GIF: {
        preview.detach();
        gifReceiver.detach();
        break;
      }
      case MODE_COLLAGE: {
        multipleReceiver.detach();
        break;
      }
    }
  }

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.block == cause) {
      requestFiles(true);
      return true;
    }
    return false;
  }

  public boolean invalidateIconsContent (PageBlock block) {
    if (this.block == block && block != null) {
      block.requestIcons(iconReceiver);
      return true;
    }
    return false;
  }

  public void requestFiles (boolean invalidate) {
    if (block != null) {
      block.requestIcons(iconReceiver);
      switch (mode) {
        case MODE_IMAGE:
        case MODE_AVATAR: {
          if (mode == MODE_IMAGE) {
            preview.setRadius(block.getImageContentRadius());
            receiver.setRadius(block.getImageContentRadius());
          }
          if (!invalidate) {
            block.requestPreview(preview);
          }
          block.requestImage(receiver);
          break;
        }
        case MODE_GIF: {
          if (!invalidate) {
            block.requestPreview(preview);
          }
          block.requestGif(gifReceiver);
          break;
        }
        case MODE_COLLAGE: {
          block.requestFiles(multipleReceiver, invalidate);
          break;
        }
      }
    } else {
      iconReceiver.clear();
      switch (mode) {
        case MODE_IMAGE:
        case MODE_AVATAR: {
          preview.clear();
          receiver.clear();
          break;
        }
        case MODE_GIF: {
          preview.clear();
          gifReceiver.clear();
          break;
        }
        case MODE_COLLAGE: {
          multipleReceiver.clear();
          break;
        }
      }
    }
  }

  public PageBlock getBlock () {
    return block;
  }

  private boolean needClick;

  private void setNeedClick (boolean needClick) {
    if (this.needClick != needClick) {
      this.needClick = needClick;
      if (needClick) {
        setOnClickListener(onClickListener);
        setOnLongClickListener(onLongClickListener);
      } else {
        setOnClickListener(null);
        setOnLongClickListener(null);
      }
    }
  }

  public void setBlock (PageBlock block) {
    if (this.block == block) {
      requestFiles(false);
      return;
    }

    if (this.block != null) {
      this.block.detachFromView(this);
      this.block = null;
    }

    this.block = block;
    setNeedClick(block != null && block.isClickable());

    final int viewWidth = getMeasuredWidth();
    final int desiredHeight;
    if (block != null) {
      block.autoDownloadContent();
      block.attachToView(this);
      desiredHeight = viewWidth != 0 ? block.getHeight(this, viewWidth) : 0;
    } else {
      desiredHeight = 0;
    }

    requestFiles(false);

    if (viewWidth != 0 && getMeasuredHeight() != desiredHeight) {
      requestLayout();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = needClick && super.onTouchEvent(e);
    return (block != null && block.onTouchEvent(this, e)) || res;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    setMeasuredDimension(width, MeasureSpec.makeMeasureSpec(block != null ? block.getHeight(this, width) : 0, MeasureSpec.EXACTLY));
  }

  public ComplexReceiver getIconReceiver () {
    return iconReceiver;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (block != null) {
      block.draw(this, c, preview, mode == MODE_COLLAGE ? null : mode == MODE_GIF ? gifReceiver : receiver, iconReceiver);
    }
  }
}
