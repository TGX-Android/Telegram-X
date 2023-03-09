/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/08/2017
 */
package org.thunderdog.challegram.component.chat;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.CircleFrameLayout;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MessageViewGroup extends ViewGroup implements Destroyable, AttachDelegate, MessagesManager.MessageProvider {
  private static class MessageBackground extends Drawable {
    private final MessageView context;

    public MessageBackground (MessageView context) {
      this.context = context;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      TGMessage msg = context.getMessage();
      if (msg != null) {
        msg.drawBackground(context, c);
      }
    }

    @Override
    public void setAlpha (@IntRange(from = 0, to = 255) int alpha) {

    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }

  private MessageView messageView;
  private MessagesManager manager;
  private VideoPlayerView videoPlayerView;
  private MessageOverlayView overlayView;

  public MessageViewGroup (Context context) {
    super(context);
    setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
  }

  public MessageOverlayView getOverlayView () {
    return overlayView;
  }

  public void initWithView (MessageView messageView, MessagesManager manager, @Nullable ViewController<?> themeProvider) {
    this.messageView = messageView;
    messageView.setCustomMeasureDisabled(true);
    messageView.setParentMessageViewGroup(this);
    messageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(messageView);

    this.manager = manager;

    ViewUtils.setBackground(this, new MessageBackground(messageView));

    videoPlayerView = new VideoPlayerView(getContext());
    ViewSupport.setHigherElevation(videoPlayerView, messageView, true);
    addView(videoPlayerView);

    overlayView = new MessageOverlayView(getContext()).setBoundView(messageView);
    overlayView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    ViewSupport.setHigherElevation(overlayView, videoPlayerView, true);
    addView(overlayView);

    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(overlayView);
    }
  }

  private float swipeTranslation;

  public void setSwipeTranslation (float dx) {
    if (this.swipeTranslation != dx) {
      this.swipeTranslation = dx;
      updateTranslation();
    }
  }

  private float selectableTranslation;

  public final void setSelectableTranslation (float dx) {
    if (this.selectableTranslation != dx && manager.useBubbles()) {
      this.selectableTranslation = dx;
      updateTranslation();
    }
  }

  private void updateTranslation () {
    float dx = swipeTranslation + selectableTranslation;
    videoPlayerView.setTranslationX(dx);
    TGMessage msg = messageView.getMessage();
    UI.getContext(getContext()).getRoundVideoController().onMessageTranslate(msg.getChatId(), msg.getId());
  }

  public ViewGroup getVideoPlayerView () {
    return videoPlayerView;
  }

  @Override
  public TGMessage getMessage () {
    return messageView.getMessage();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    if (width == 0) {
      measureChildren(widthMeasureSpec, heightMeasureSpec);
      return;
    }

    final TGMessage msg = messageView.getMessage();
    if (msg != null) {
      msg.buildLayout(width);
    }
    heightMeasureSpec = MeasureSpec.makeMeasureSpec(messageView.getCurrentHeight(), MeasureSpec.EXACTLY);
    setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view == null) {
        continue;
      }
      LayoutParams params = view.getLayoutParams();
      final int futureWidth, futureHeight;
      switch (params.width) {
        case LayoutParams.MATCH_PARENT:
          futureWidth = getMeasuredWidth();
          break;
        case LayoutParams.WRAP_CONTENT:
          futureWidth = msg != null ? msg.getChildrenWidth() : 0;
          break;
        default:
          futureWidth = params.width;
          break;
      }
      switch (params.height) {
        case LayoutParams.MATCH_PARENT:
          futureHeight = getMeasuredHeight();
          break;
        case LayoutParams.WRAP_CONTENT:
          futureHeight = msg != null ? msg.getChildrenHeight() : 0;
          break;
        default:
          futureHeight = params.height;
          break;
      }
      final int viewWidth = view.getMeasuredWidth();
      final int viewHeight = view.getMeasuredHeight();
      view.measure(MeasureSpec.makeMeasureSpec(futureWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(futureHeight, MeasureSpec.EXACTLY));
      /*if (true || viewWidth != futureWidth || viewHeight != futureHeight || (futureWidth == 0 && futureHeight == 0)) {
      }*/
    }
  }

  private final Rect childrenBounds = new Rect();

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int childCount = getChildCount();
    final TGMessage msg = messageView.getMessage();
    final int childrenLeft, childrenTop, childrenWidth, childrenHeight;

    if (msg != null) {
      childrenLeft = msg.getChildrenLeft();
      childrenTop = msg.getChildrenTop();
      childrenWidth = msg.getChildrenWidth();
      childrenHeight = msg.getChildrenHeight();
    } else {
      childrenLeft = childrenTop = childrenWidth = childrenHeight = 0;
    }

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view == null) {
        continue;
      }
      LayoutParams params = view.getLayoutParams();
      int left = 0, top = 0;
      if (params.width == LayoutParams.WRAP_CONTENT) {
        left = childrenLeft;
      }
      if (params.height == LayoutParams.WRAP_CONTENT) {
        top = childrenTop;
      }

      final int viewWidth = view.getMeasuredWidth();
      int right = left + viewWidth;
      int bottom = top + view.getMeasuredHeight();

      view.layout(left, top, right, bottom);
      /*if (true || left != view.getLeft() || view.getTop() != top || view.getRight() != right || view.getBottom() != bottom) {
      }*/
    }

    if (childrenBounds.left != childrenLeft || childrenBounds.top != childrenTop || childrenBounds.right != childrenWidth || childrenBounds.bottom != childrenHeight) {
      childrenBounds.set(childrenLeft, childrenTop, childrenWidth, childrenHeight);
    }
  }

  public MessageView getMessageView () {
    return messageView;
  }

  @Override
  public void performDestroy () {
    messageView.performDestroy();
    videoPlayerView.performDestroy();
  }

  @Override
  public void attach () {
    messageView.onAttachedToRecyclerView();
    videoPlayerView.attach();
  }

  @Override
  public void detach () {
    messageView.onDetachedFromRecyclerView();
    videoPlayerView.detach();
  }

  public void setMessage (TGMessage message) {
    messageView.setMessage(message);
    overlayView.setMessage(message);
    requestVideo(message);

    if (getMeasuredHeight() != messageView.getCurrentHeight()) {
      requestLayout();
    }
  }

  private void requestVideo (TGMessage message) {
    videoPlayerView.requestFiles(message);
  }

  public void invalidateContent (TGMessage message) {
    requestVideo(message);
  }
}

// Video

class VideoPlayerView extends CircleFrameLayout implements Destroyable, AttachDelegate {
  private final MutedVideoView mutedVideoView;

  public VideoPlayerView (@NonNull Context context) {
    super(context);

    this.mutedVideoView = new MutedVideoView(context);
    this.mutedVideoView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(mutedVideoView);

    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public MutedVideoView getMutedVideoView () {
    return mutedVideoView;
  }

  public void requestFiles (@Nullable TGMessage message) {
    if (message == null) {
      mutedVideoView.getReceiver().requestFile(null);
    } else {
      message.requestGif(mutedVideoView.getReceiver());
    }
  }

  @Override
  public void attach () {
    mutedVideoView.attach();
  }

  @Override
  public void detach () {
    mutedVideoView.detach();
  }

  @Override
  public void performDestroy () {
    mutedVideoView.performDestroy();
  }
}
class MutedVideoView extends View implements AttachDelegate, Destroyable {
  private final GifReceiver receiver;

  public MutedVideoView (Context context) {
    super(context);
    this.receiver = new GifReceiver(this);
  }

  public GifReceiver getReceiver () {
    return receiver;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    receiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  @Override
  protected void onDraw (Canvas c) {
    receiver.draw(c);
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
  }
}