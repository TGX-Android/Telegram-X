package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.TripleAvatarAnimator;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.MessageId;

public class MessageCommentButton implements FactorAnimator.Target, ClickHelper.Delegate {
  public static final int COMMENT_MODE_NONE = 0;
  public static final int COMMENT_MODE_BUTTON = 1;
  public static final int COMMENT_MODE_DETACHED_BUTTON = 2;

  private final TGMessage parent;
  private ViewProvider viewProvider;
  private final Tdlib tdlib;
  private final ProgressComponent progress;

  private final BoolAnimator hasUnreadComments;
  private final BoolAnimator commentsLoading;
  private final BoolAnimator commentButtonVisible;

  private final TripleAvatarAnimator commentsTripleAvatar;
  private final Counter commentButtonText;
  private final ClickHelper forceTouchHelper;

  public MessageCommentButton (@NonNull TGMessage parent, ViewProvider viewProvider) {
    this.parent = parent;
    this.path = new Path();
    this.dirtyRect = new Rect();
    this.viewProvider = viewProvider;
    this.tdlib = parent.tdlib();

    this.hasUnreadComments = new BoolAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    this.commentsLoading = new BoolAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    this.commentButtonVisible = new BoolAnimator(0, (a, b, c, d) -> {
      parent.onChangeCommentButtonVisibleFactor();
      invalidate();
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);

    this.commentButtonText = new Counter.Builder()
      .noBackground()
      .allBold(true)
      .callback(parent)
      .textSize(inDetachedMode() ? 13f : 15f)
      .colorSet(this::getCommentButtonTextColor)
      .build();
    this.commentsTripleAvatar = new TripleAvatarAnimator(parent.tdlib(), viewProvider);
    this.forceTouchHelper = new ClickHelper(this);
    this.forceTouchHelper.setNoSound(true);
    this.progress = new ProgressComponent(UI.getContext(parent.context()), Screen.dp(8f), parent.getCurrentViews());
    this.progress.forceColor(Theme.getColor(getCommentButtonTextColorId()));
    this.progress.setUseLargerPaint(Screen.dp(2));
  }

  public void update (@Nullable TdApi.MessageReplyInfo replyInfo, boolean isVisible, int commentsCount, String buttonText, boolean hasUnreadComments, boolean animated) {
    this.commentButtonText.setCount(commentsCount, buttonText, animated);
    this.hasUnreadComments.setValue(hasUnreadComments, animated);
    this.commentsTripleAvatar.setReplyInfo(replyInfo, animated);
    this.commentButtonVisible.setValue(isVisible && !getMessageThreadError, animated);
    this.parent.invalidateCommentAvatarReceiver();
  }

  public float getVisibility () {
    return commentButtonVisible.getFloatValue();
  }

  public boolean isVisible () {
    return commentButtonVisible.getValue();
  }

  private void invalidate () {
    if (viewProvider != null) {
      viewProvider.invalidate(dirtyRect);
    }
  }

  public int getDefaultMode () {
    if (!parent.useBubbles()) {
      return COMMENT_MODE_BUTTON;
    }
    return !parent.useBubble() || parent.useStickerBubbleReactions() ? COMMENT_MODE_DETACHED_BUTTON : COMMENT_MODE_BUTTON;
  }

  public boolean inDetachedMode () {
    return getDefaultMode() == COMMENT_MODE_DETACHED_BUTTON;
  }

  public boolean needDrawSepLineOnCommentButton () {
    return parent.useBubbles() && !inDetachedMode() && !parent.useMediaBubbleReactions();
  }

  public int getAnimatedWidth () {
    final float hasUnreadComments = this.hasUnreadComments.getFloatValue();
    final boolean isDetached = inDetachedMode();
    final int textOffset = Screen.dp(isDetached ? 39: 47);
    final int rightOffset = Screen.dp(isDetached ? 6: 16);
    return (int) (textOffset + commentButtonText.getWidth() + (isDetached ? 0: Screen.dp(10)) + Math.max(commentsTripleAvatar.getWidth(), Screen.dp(20)) + Screen.dp(12) * hasUnreadComments + rightOffset);
  }

  public void setLoading (boolean loading, boolean animated) {
    commentsLoading.setValue(loading, animated);
    if (loading) {
      tdlib.ui().postDelayed(() -> {    // todo ???
        if (!parent.isDestroyed() && !parent.messagesController().isDestroyed()) {
          setLoading(false, false);
        }
      }, 5000);
    }
  }

  public boolean isLoading () {
    return commentsLoading.getValue();
  }

  private int getStaticHeight () {
    return Screen.dp(inDetachedMode() ? 32: 42);
  }

  public void requestCommentAvatars (ComplexReceiver complexReceiver) {
    commentsTripleAvatar.requestCommentAvatars(complexReceiver);
  }

  public int getCommentButtonTextColorId () {
    return inDetachedMode() ? R.id.theme_color_fileContent: R.id.theme_color_messageAuthor;
  }

  public int getCommentButtonTextColor () {
    return Theme.getColor(getCommentButtonTextColorId());
  }

  private int lastStartY;
  private int lastStartX;
  private int lastEndX;

  private final Path path;
  private final Rect dirtyRect;
  private int oldTopRadius;
  private int oldBottomLeftRadius;
  private int oldBottomRightRadius;

  private void rebuildPathIfNeeded (int x1, int y1, int x2, int y2, int topRadius, int bottomLeftRadius, int bottomRightRadius) {
    if (dirtyRect.left != x1 || dirtyRect.right != x2 || dirtyRect.top != y1 || dirtyRect.bottom != y2 || oldTopRadius != topRadius || oldBottomLeftRadius != bottomLeftRadius || oldBottomRightRadius != bottomRightRadius) {
      dirtyRect.left = x1; dirtyRect.right = x2;
      dirtyRect.top = y1; dirtyRect.bottom = y2;
      oldBottomLeftRadius = bottomLeftRadius;
      oldBottomRightRadius = bottomRightRadius;
      oldTopRadius = topRadius;
      path.reset();

      DrawAlgorithms.buildPath(path, new RectF(dirtyRect.left, dirtyRect.top, dirtyRect.right, dirtyRect.bottom), topRadius, topRadius, bottomRightRadius, bottomLeftRadius);
    }
  }

  public void draw (MessageView view, Canvas c, int startX, int endX, int y, float alpha) {
    this.lastStartY = y;
    this.lastStartX = startX;
    this.lastEndX = endX;

    final int width = endX - startX;
    final int height = getStaticHeight();

    final boolean useBubbles = parent.useBubbles();
    final boolean isDetached = inDetachedMode();
    final boolean needDrawSepLine = needDrawSepLineOnCommentButton();
    final int cy = y + height / 2;

    final float defaultAvatarsWidth = commentsTripleAvatar.getWidth();
    final int minFullWidth = getAnimatedWidth();
    final int actualWidth = endX - startX;
    final int overWidth = minFullWidth - actualWidth;

    final boolean needShrink = useBubbles && !isDetached && (overWidth > 0);
    final boolean needHideIcon = needShrink && (overWidth <= Screen.dp(30) || overWidth > defaultAvatarsWidth);
    final boolean needHideAvatars = needShrink && (overWidth > Screen.dp(30));

    final int iconOffset = Screen.dp(isDetached ? 10: (useBubbles ? 16: 21));
    final int textOffset = useBubbles ? Screen.dp(isDetached ? 34: (46 - (needHideIcon ? 30 : 0))): TGMessage.getContentLeft();
    final int rightOffset = Screen.dp(useBubbles ? (isDetached ? 6: 16): 38);
    final int iconColorId = getCommentButtonTextColorId();
    final int iconColor = Theme.getColor(iconColorId);
    final int loadingOffset = textOffset / 2 - Screen.dp(8);
    final float avatarsWidth = needHideAvatars ? 0: defaultAvatarsWidth;

    final int directionColor = ColorUtils.alphaColor(alpha, isDetached ? iconColor: parent.getDecentColor());
    final int directionOffset = Screen.dp(useBubbles ? (isDetached ? 16: 22): 20);
    final int directionWidth = Screen.dp(isDetached ? 1.25f: 1.5f);
    final int directionHeight = Screen.dp(isDetached ? 5.25f: 6.5f);

    final int topRadius = isDetached ? (height / 2): 0;
    final int bottomLeftRadius = isDetached ? (height / 2): (int) parent.getBubbleBottomLeftRadius();
    final int bottomRightRadius = isDetached ? (height / 2): (int) parent.getBubbleBottomRightRadius();

    final float loadingAlpha = commentsLoading.getFloatValue();

    //

    final RectF rounder = new RectF();
    final int strokePadding = Math.round(Paints.getInlineButtonOuterPaint().getStrokeWidth() * .5f);
    final int bottom = y + height;
    final int radius = isDetached ? (height / 2): (useBubbles ? Screen.dp(Theme.getBubbleMergeRadius()): 0);

    rounder.left = startX + strokePadding; rounder.right = endX - strokePadding;
    rounder.top = y + strokePadding; rounder.bottom = bottom - strokePadding;

    rebuildPathIfNeeded (startX, y, endX, bottom, topRadius, bottomLeftRadius, bottomRightRadius);

    int selectionColor = Theme.getColor(R.id.theme_color_bubble_button);
    if (fadeFactor != 0f) {
      selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
    }

    if (selectionFactor != 0f) {
      int anchorX = Math.max(Math.min(this.anchorX, width), 0);
      int anchorY = Math.max(Math.min(this.anchorY, height), 0);
      float selectionRadius = (float) Math.sqrt(width * width + height * height) * .5f * selectionFactor;
      float centerX = width / 2f;
      float centerY = height / 2f;
      float diffX = centerX - anchorX;
      float diffY = centerY - anchorY;
      float selectionX = startX + anchorX + diffX * selectionFactor;
      float selectionY = y + anchorY + diffY * selectionFactor;

      final int saveCount;
      if ((saveCount = ViewSupport.clipPath(c, path)) != Integer.MIN_VALUE) {
        c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
      } else {
        c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(selectionColor));
      }
      ViewSupport.restoreClipPath(c, saveCount);
    }

    // Draw button

    if (needDrawSepLine) {
      c.drawLine(startX + Screen.dp(16), y - Screen.dp(1), endX - Screen.dp(16), y, Paints.strokeSeparatorPaint(ColorUtils.alphaColor(alpha * 0.15f, Theme.getColor(parent.getDecentColorId(R.id.theme_color_textLight)))));
    }

    if (isDetached) {
      c.drawRoundRect(new RectF(startX, y, endX, y + height), height / 2f, height / 2f, Paints.fillingPaint(parent.getBubbleTimeColor()));
    }

    if (!needHideIcon) {
      final Drawable drawable = view.getSparseDrawable(isDetached ? R.drawable.baseline_forum_16 : R.drawable.baseline_forum_18, iconColorId);
      Drawables.draw(c, drawable, startX + iconOffset, cy - drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(iconColorId, Math.min(alpha, 1f - loadingAlpha)));
    }

    commentButtonText.draw(c, startX + textOffset, cy, Gravity.LEFT, alpha);

    DrawAlgorithms.drawDirection(c, endX - directionOffset, cy, directionColor, Gravity.RIGHT, directionWidth, directionHeight, useBubbles ? 1f - commentsTripleAvatar.getVisibility(): 1f);

    if (!needHideAvatars) {
      commentsTripleAvatar.draw(c, view.getCommentAvatarsReceiver(), endX - rightOffset - Screen.dp(10), cy);
    }

    if (hasUnreadComments.getFloatValue() > 0) {
      c.drawCircle(endX - rightOffset - Screen.dp(11) - avatarsWidth, cy, Screen.dp(3) * hasUnreadComments.getFloatValue(), Paints.fillingPaint(Theme.getColor(R.id.theme_color_messageAuthor)));
    }

    if (loadingAlpha != 0f && !needHideIcon) {
      progress.setBounds(startX + loadingOffset, cy - Screen.dp(8), startX + loadingOffset + Screen.dp(16), cy + Screen.dp(8));
      progress.setAlpha(commentsLoading.getFloatValue());
      progress.draw(c);
    }

    /*
    int debugColor = ColorUtils.alphaColor(0.25f, Color.RED);
    c.drawRect(startX, y, endX, y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
    if (!useBubbles) {
      c.drawRect(startX + Screen.dp(60), y, endX, y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
      c.drawRect(startX, y, endX - Screen.dp(18), y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
      c.drawRect(startX, y, endX - Screen.dp(38), y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
    } else {
      c.drawRect(startX + Screen.dp(46), y, endX, y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
      c.drawRect(startX, y, endX - Screen.dp(16), y + Screen.dp(42), Paints.strokeSmallPaint(debugColor));
    }
    */
  }








  // View

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    final boolean isBlocked = viewProvider == null;
    if (fadeAnimator != null) {
      fadeAnimator.setIsBlocked(isBlocked);
    }
    if (selectionAnimator != null) {
      selectionAnimator.setIsBlocked(isBlocked);
    }
  }

  // Touch events

  private static final int FLAG_CAUGHT = 0x02;
  private static final int FLAG_BLOCKED = 0x04;

  private int flags;
  private int anchorX, anchorY;

  private boolean isCaught () {
    return (flags & FLAG_CAUGHT) != 0;
  }

  private boolean isBlocked () {
    return (flags & FLAG_BLOCKED) != 0;
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    int x = Math.round(e.getX() - lastStartX);
    int y = Math.round(e.getY() - lastStartY);

    Log.i("ClickHelper", String.format("TOUCH %b %d %d", forceTouchHelper.inLongPress(), x, y));

    if (!forceTouchHelper.inLongPress() && !isCaught() && (x < 0 || y < 0 || x >= (lastEndX - lastStartX) || y > getStaticHeight())) {
      return false;
    }


    boolean res = false;
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        flags |= FLAG_CAUGHT;
        anchorX = x; anchorY = y;
        if (!isBlocked()) {
          animateSelectionFactor(1f);
        }
        res = true;
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        anchorX = x; anchorY = y;
        res = true;
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isCaught()) {
          flags &= ~FLAG_CAUGHT;
          if (!isBlocked()) {
            cancelSelection();
          }
        }
        res = true;
        break;
      }
      case MotionEvent.ACTION_UP: {
        anchorX = x; anchorY = y;
        res = isCaught();
        if (res) {
          flags &= ~FLAG_CAUGHT;
          ViewUtils.onClick(view);
          performClick(view);
        }
        break;
      }
    }

    return forceTouchHelper.onTouchEvent(view, e) || res;
  }

  private void performClick (View view) {
    if (!isBlocked()) {
      cancelSelection();
    }
  }

  private void cancelSelection () {
    animateFadeFactor(1f);
  }

  private void forceResetSelection () {
    if (fadeAnimator != null) {
      fadeAnimator.forceFactor(this.fadeFactor = 0f);
      flags &= ~FLAG_BLOCKED;
    }
    if (selectionAnimator != null) {
      selectionAnimator.forceFactor(this.selectionFactor = 0f);
    }
  }

  public boolean needCaughtLongPress (View v, float x, float y) {
    return (flags & FLAG_CAUGHT) != 0;
  }

  public boolean performLongPress (View v) {
    if ((flags & FLAG_CAUGHT) != 0) {
      flags &= ~FLAG_CAUGHT;
      // cancelSelection();
      return true;
    }
    return false;
  }

  // Animations

  private static final long ANIMATION_DURATION = 250L;
  private static final int SELECTION_ANIMATOR = 0;
  private static final int FADE_ANIMATOR = 2;

  private float selectionFactor;
  private FactorAnimator selectionAnimator;

  private void animateSelectionFactor (float toFactor) {
    if (selectionAnimator == null) {
      selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
      selectionAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
    }
    selectionAnimator.animateTo(toFactor);
  }

  private float fadeFactor;
  private FactorAnimator fadeAnimator;

  private void animateFadeFactor (float toFactor) {
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
      fadeAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
    }
    flags |= FLAG_BLOCKED;
    fadeAnimator.animateTo(toFactor);
  }

  // Animation

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case SELECTION_ANIMATOR: {
        this.selectionFactor = factor;
        break;
      }
      case FADE_ANIMATOR: {
        this.fadeFactor = factor;
        break;
      }
    }
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == FADE_ANIMATOR) {
      if (finalFactor == 1f) {
        forceResetSelection();
      }
    }
  }

  // preview

  private void closePreview () {
    if (currentOpenPreview != null) {
      UI.getContext(parent.context()).closeForceTouch();
      currentOpenPreview = null;
    }
  }

  public void dropPress (float x, float y) {
    setInLongPress(false, x, y);
  }

  private ViewParent requestedDisallowOnParent;
  private void setInLongPress (boolean inLongPress, float x, float y) {
    View view = parent.findCurrentView();
    if (inLongPress) {
      /*setPressed(false);
      dispatchingEvents = false;*/
      if (view != null) {
        requestedDisallowOnParent = view.getParent();
      }
      // awaitingSlideOff = true;
      // slideOffStartY = y;
    }
    if (requestedDisallowOnParent != null) {
      requestedDisallowOnParent.requestDisallowInterceptTouchEvent(inLongPress);
    }
  }




  //

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (!isButton(x, y)) return false;
    startOpenPreview(view, x, y);
    return false;
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    /*if (awaitingSlideOff) {
      boolean ok = false;
      if (customControllerProvider != null) {
        ok = customControllerProvider.onSlideOff(this, x, y, currentOpenPreview);
      } else if (slideOffListener != null) {
        ok = slideOffListener.onSlideOff(this, x, y, currentOpenPreview);
      }
      if (ok) {
        awaitingSlideOff = false;
        closePreview();
      }
    }*/
    if (currentOpenPreview != null) {
      UI.getContext(parent.context()).processForceTouchMoveEvent(x, y, startX, startY);
    }
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    cancelScheduledPreviewOpening();
    closePreview();
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    cancelScheduledPreviewOpening();
    setInLongPress(false, x, y);
    closePreview();
  }

  //

  @Override
  public long getLongPressDuration () {
    return ViewConfiguration.getLongPressTimeout();
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return isButton(x, y);
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return isButton(x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    startOpenThread();
  }

  public void onClickTouchDown (View view, float x, float y) {
    if (isButton(x, y)) {
      getMessageThread(parent.getChatId(), parent.getSmallestId());
    }
  }


  private boolean isButton (float rx, float ry) {
    float x = rx - lastStartX;
    float y = ry - lastStartY;
    return !(x < 0 || y < 0 || x >= (lastEndX - lastStartX) || y > getStaticHeight());
  }



  private int contextId = 0;
  private ThreadInfo currentThreadInfo;

  private boolean needOpenPreviewOnThreadLoading;
  private boolean needOpenThreadOnThreadLoading;

  private View openPreviewOnThreadLoadingView;
  private float openPreviewOnThreadLoadingX;
  private float openPreviewOnThreadLoadingY;

  private void startOpenThread () {
    if (needOpenPreviewOnThreadLoading || needOpenThreadOnThreadLoading) return;

    if (currentThreadInfo != null) {
      openThread(currentThreadInfo);
      return;
    }

    scheduleThreadOpening();
  }

  private void startOpenPreview (View view, float x, float y) {
    if (needOpenPreviewOnThreadLoading || needOpenThreadOnThreadLoading) return;

    if (currentThreadInfo != null) {
      openPreview(currentThreadInfo, view, x, y);
      cancelSelection();
      return;
    }

    schedulePreviewOpening(view, x, y);
  }

  private void scheduleThreadOpening () {
    this.needOpenThreadOnThreadLoading = true;
    setLoading(true, parent.needAnimateChanges());
  }

  private void schedulePreviewOpening (View view, float x, float y) {
    this.needOpenPreviewOnThreadLoading = true;
    this.openPreviewOnThreadLoadingView = view;
    this.openPreviewOnThreadLoadingX = x;
    this.openPreviewOnThreadLoadingY = y;
    setLoading(true, parent.needAnimateChanges());
  }

  private void cancelScheduledPreviewOpening () {
    this.needOpenPreviewOnThreadLoading = false;
    cancelSelection();
  }

  private ViewController<?> currentOpenPreview;

  private void openThread (ThreadInfo threadInfo) {
    setLoading(false, parent.needAnimateChanges());
    parent.openMessageThread(threadInfo, null);
  }

  private void openPreview (ThreadInfo threadInfo, View view, float x, float y) {
    setLoading(false, parent.needAnimateChanges());

    MessagesController controller = new MessagesController(parent.context(), tdlib);
    controller.setArguments(new MessagesController.Arguments(null, tdlib.chat(threadInfo.getChatId()), threadInfo, /*new MessageId(threadInfo.getChatId(), MessageId.MIN_VALID_ID)*/ null, 0, null));
    controller.setInForceTouchMode(true);
    openPreview(controller, x, y);
  }

  private void openPreview (MessagesController controller, float x, float y) {
    View view = parent.findCurrentView();
    if (view == null) return;

    ViewController<?> ancestor = ViewController.findAncestor(view);
    if ((ancestor != null && tdlib != null && ancestor.tdlib() != null && ancestor.tdlib().id() != tdlib.id())) {
      return;
    }

    ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, view, controller.get(), controller);
    context.setStateListener(controller);

    ((ForceTouchView.PreviewDelegate) controller).onPrepareForceTouchContext(context);

    context.setMaximizeListener((target, animateToWhenReady, arg) -> MessagesController.maximizeFrom(tdlib, parent.context(), target, animateToWhenReady, arg));

    IntList ids = new IntList(5);
    IntList icons = new IntList(5);
    StringList strings = new StringList(5);
    ForceTouchView.ActionListener listener = null;

    boolean canRead = true; //parent.hasUnreadComments();
    ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
    strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
    icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);

    listener = new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        if (actionId == R.id.btn_markChatAsRead) {
          parent.tdlib().client().send(new TdApi.GetMessageThread(parent.getChatId(), parent.getSmallestId()), result -> parent.tdlib().ui().post(() -> {
            switch (result.getConstructor()) {
              case TdApi.MessageThreadInfo.CONSTRUCTOR: {
                TdApi.MessageThreadInfo messageThread = (TdApi.MessageThreadInfo) result;
                parent.tdlib().markChatAsRead(messageThread.chatId, messageThread.messageThreadId, null);
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(result);
                break;
              }
            }
          }));
        }
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {}
    };
    context.setButtons(listener, controller, ids.get(), icons.get(), strings.get());
    context.setExpandFactor(view, y);
    context.setForceSubtitle(controller.getThreadSubtitle());

    if (UI.getContext(parent.context()).openForceTouch(context)) {
      currentOpenPreview = controller;
      forceTouchHelper.onLongPress(view, x, y);
      setInLongPress(true, x, y);
    } else {
      controller.destroy();
    }
  }

  private boolean getMessageThreadError;

  private void getMessageThread (long chatId, long messageId) {
    this.contextId += 1;
    final int finalContextId = this.contextId;
    currentThreadInfo = null;
    tdlib.client().send(new TdApi.GetMessageThread(chatId, messageId), result -> tdlib.ui().post(() -> {
      if (finalContextId != contextId) return;
      switch (result.getConstructor()) {
        case TdApi.MessageThreadInfo.CONSTRUCTOR: {
          TdApi.MessageThreadInfo messageThread = (TdApi.MessageThreadInfo) result;
          onThreadInfoLoaded(new ThreadInfo(parent.getAllMessages(), parent.getChat(), messageThread, parent.isRepliesChat()));
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          commentButtonVisible.setValue(false, parent.needAnimateChanges());
          getMessageThreadError = true;
          currentThreadInfo = null;
          UI.showError(result);
          break;
        }
      }
    }));
  }

  private void onThreadInfoLoaded (ThreadInfo threadInfo) {
    if (parent.isDestroyed() || parent.messagesController().isDestroyed()) return;

    this.currentThreadInfo = threadInfo;

    if (needOpenPreviewOnThreadLoading) {
      openPreview(threadInfo, openPreviewOnThreadLoadingView, openPreviewOnThreadLoadingX, openPreviewOnThreadLoadingY);
      needOpenPreviewOnThreadLoading = false;
      cancelSelection();
    }

    if (needOpenThreadOnThreadLoading) {
      openThread(threadInfo);
      needOpenThreadOnThreadLoading = false;
      cancelSelection();
    }
  }
}
