package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawModifier;

import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 8/18/17
 * Author: default
 */

public class SmallChatView extends BaseView implements AttachDelegate, TooltipOverlayView.LocationProvider, Destroyable {
  private final ImageReceiver receiver;

  private DoubleTextWrapper chat;
  private DrawModifier drawModifier;
  private ImageReceiver emojiReceiver;

  public SmallChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);

    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    this.receiver = new ImageReceiver(this, radius);
    layoutReceiver();
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
  }

  private void layoutReceiver () {
    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    int left = Screen.dp(11f);
    int right = Screen.dp(11f) + radius * 2;
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      this.receiver.setBounds(viewWidth - right, viewHeight / 2 - radius, viewWidth - left, viewHeight / 2 + radius);
    } else {
      this.receiver.setBounds(left, viewHeight / 2 - radius, right, viewHeight / 2 + radius);
    }
  }

  @Override
  public void attach () {
    receiver.attach();
    if (emojiReceiver != null) emojiReceiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
    if (emojiReceiver != null) emojiReceiver.detach();
  }

  @Override
  public void performDestroy () {
    //receiver.destroy(); TODO: we need this?
    if (emojiReceiver != null) emojiReceiver.destroy();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    if (width > 0 && chat != null) {
      chat.layout(width);
    }
    layoutReceiver();
  }

  public void setChat (DoubleTextWrapper chat) {
    if (this.chat == chat) {
      return;
    }

    if (this.chat != null) {
      this.chat.getViewProvider().detachFromView(this);
    }

    this.chat = chat;
    if (chat != null) {
      setPreviewChatId(null, chat.getChatId(), null);
    } else {
      clearPreviewChat();
    }

    if (chat != null) {
      final int currentWidth = getMeasuredWidth();
      if (currentWidth != 0) {
        chat.layout(currentWidth);
      }
      chat.getViewProvider().attachToView(this);
    }

    requestFile();
    invalidate();
  }

  private void requestFile () {
    receiver.requestFile(chat != null ? chat.getAvatarFile() : null);
  }

  public void invalidateContent (DoubleTextWrapper chat) {
    if (this.chat == chat) {
      requestFile();
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (chat != null) {
      chat.getTargetBounds(targetView, outRect);
    }
  }

  public void setDrawModifier (DrawModifier drawModifier) {
    this.drawModifier = drawModifier;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }

    layoutReceiver();

    if (drawModifier != null) drawModifier.beforeDraw(this, c);
    if (chat.getAvatarFile() != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, receiver.getRadius());
      }
      receiver.draw(c);
    } else if (chat.getAvatarPlaceholder() != null) {
      chat.getAvatarPlaceholder().draw(c, receiver.centerX(), receiver.centerY());
    }

    chat.draw(this, receiver, c);
    if (drawModifier != null) drawModifier.afterDraw(this, c);
  }

  public ImageReceiver getReceiver () {
    if (emojiReceiver == null) {
      emojiReceiver = new ImageReceiver(this, 0);
    }

    return emojiReceiver;
  }
}
