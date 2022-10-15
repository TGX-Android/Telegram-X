package org.thunderdog.challegram.component.thread;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;


@SuppressLint("ViewConstructor")
public class TGCommentButton implements CounterAnimator.Callback<Text>, TextColorSet, FactorAnimator.Target {

  public static final int DEFAULT_PADDING = Screen.dp(8);
  private static final int SELECTION_ANIMATOR = 0;
  private static final int FADE_ANIMATOR = 1;
  private static final long ANIMATION_DURATION = 250L;
  private static final int sTextSizeDp = 14;
  private static final int sSmallTextSizeDp = 12;
  private static final boolean DEBUG = false;
  private static final int FLAG_ACTIVE = 0x01;
  private static final int FLAG_CAUGHT = 0x02;
  private static final int FLAG_BLOCKED = 0x04;

  private final CounterAnimator<Text> mCounterAnimator;
  private final UserAvatarStack mUserAvatarStack;
  private final TGMessage mParent;
  private final RectF mBounds;
  private final CommentButtonDelegate mDelegate;
  private final Region mBackgroundClipRegion;
  private final Region mBottomBubbleRegion;
  private Drawable mStartIconDrawable;
  private Drawable mEndIconDrawable;
  private int width, height;
  private Info mInfo;
  private String mText;
  private boolean mAnimated = false;
  private int paddingStart = DEFAULT_PADDING, paddingEnd = DEFAULT_PADDING;
  private int startIconPadding = DEFAULT_PADDING;
  private int endIconPadding = DEFAULT_PADDING;
  private int mX, mY;
  private boolean mNeedBackground = false;
  private boolean forceDisplayEndIcon = false, displayEndIcon = true;
  private float selectionFactor;
  private float fadeFactor;
  private FactorAnimator selectionAnimator;
  private FactorAnimator fadeAnimator;
  private int touchX, touchY;
  private int flags;
  private int mTextSize;
  private Path mBackgroundPath;

  public static class Info {
    private final int count;
    private final TdApi.MessageSender[] senders;

    public Info (int count, TdApi.MessageSender[] senders) {
      this.count = count;
      this.senders = senders;
    }
  }

  public interface CommentButtonDelegate {
    default void onClick (View view) {}
    default void onLongClick (View view) {}
  }

  public TGCommentButton (TGMessage parent, CommentButtonDelegate delegate) {
    mParent = parent;
    mDelegate = delegate;
    mCounterAnimator = new CounterAnimator<>(this);
    mStartIconDrawable = Drawables.get(R.drawable.baseline_forum_24);
    mEndIconDrawable = Drawables.get(R.drawable.round_keyboard_arrow_right_24);
    mUserAvatarStack = new UserAvatarStack(this);
    mBounds = new RectF();
    mBackgroundPath = new Path();
    mBackgroundClipRegion = new Region();
    mBottomBubbleRegion = new Region();
    setNeedBackground(false);
  }

  public void setReceiversPool (ComplexReceiver complexReceiver) {
    this.mUserAvatarStack.setReceiversPool(complexReceiver);
  }

  public void setForceDisplayEndIcon (boolean forceDisplayEndIcon) {
    this.forceDisplayEndIcon = forceDisplayEndIcon;
  }

  public void setHorizontalPaddings (int paddingStart, int paddingEnd) {
    this.paddingStart = paddingStart;
    this.paddingEnd = paddingEnd;
  }

  public void setStartDrawablePadding (int padding) {
    this.startIconPadding = padding;
  }

  public void setEndDrawablePadding (int padding) {
    this.endIconPadding = padding;
  }

  public void setNeedBackground (boolean needBackground) {
    this.mNeedBackground = needBackground;
    if (mNeedBackground) {
      mStartIconDrawable = Drawables.get(R.drawable.baseline_forum_16);
      mEndIconDrawable = Drawables.get(R.drawable.round_keyboard_arrow_right_18);
      mUserAvatarStack.setStrokeColor(Theme.getColor(R.id.theme_color_bubble_button));
      mTextSize = sSmallTextSizeDp;
    } else {
      mStartIconDrawable = Drawables.get(R.drawable.baseline_forum_24);
      mEndIconDrawable = Drawables.get(R.drawable.round_keyboard_arrow_right_24);
      mUserAvatarStack.setStrokeColor(Theme.getColor(R.id.theme_color_bubbleIn_background));
      mTextSize = sTextSizeDp;
    }
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public void update (Info info, boolean animated) {
    this.mInfo = info;
    mAnimated = animated;
    displayEndIcon = info.count == 0;
    mUserAvatarStack.update(getAvatars(info.senders), animated);
    if (mInfo.count == 0) {
      mText = Lang.getString(R.string.LeaveComment);
    } else {
      mText = Lang.plural(R.string.xComments, mInfo.count);
    }
    if (animated) {
      setupCounter(width < getPreferredMinWidth());
    }
  }

  public int getPreferredMinWidth () {
    float measuredText = mText != null
      ? Paints.getBoldTextPaint(mTextSize).measureText(mText)
      : 0f;
    return (int) (measuredText + getWidthWithoutText());
  }

  public Drawable getStartIconDrawable () {
    return mStartIconDrawable;
  }

  public Drawable getEndIconDrawable () {
    return mEndIconDrawable;
  }

  public RectF getBounds () {
    mBounds.set(mX, mY, mX + width, mY + height);
    return mBounds;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == SELECTION_ANIMATOR) {
      selectionFactor = factor;
    } else if (id == FADE_ANIMATOR) {
      fadeFactor = factor;
    }
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == FADE_ANIMATOR && finalFactor == 1f) {
      forceResetSelection();
    }
  }

  @Override
  public void onItemsChanged (CounterAnimator<?> animator) {}

  @Override
  public Text onCreateTextDrawable (String text) {
    return new Text.Builder(
      text,
      Integer.MAX_VALUE,
      Paints.robotoStyleProvider(mTextSize),
      this
    )
      .noSpacing()
      .allBold(true)
      .build();
  }

  @Override
  public int defaultTextColor () {
    return Theme.getColor(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_textLink);
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return 0;
  }

  @Override
  public int outlineColor (boolean isPressed) {
    return 0;
  }

  @Override
  public int outlineColorId (boolean isPressed) {
    return 0;
  }

  @Override
  public int backgroundColorId (boolean isPressed) {
    return 0;
  }

  public void onMeasure (int width, int height) {
    this.width = width;
    this.height = height;
    setupCounter(width < getPreferredMinWidth());
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    if (!handleTouch((int) e.getX(), (int) e.getY())) return false;
    int x = (int) e.getX();
    int y = (int) e.getY();
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        flags |= FLAG_CAUGHT;
        touchX = x;
        touchY = y;
        if (!isActive() && !isBlocked()) {
          animateSelectionFactor(1f);
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        touchX = x;
        touchY = y;
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isCaught()) {
          flags &= ~FLAG_CAUGHT;
          if (!isActive() && !isBlocked()) {
            cancelSelection();
          }
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        touchX = x;
        touchY = y;
        if (isCaught()) {
          flags &= ~FLAG_CAUGHT;
          ViewUtils.onClick(view);
          performClick(view);
          return true;
        }
        return false;
      }
    }
    return true;
  }

  public boolean performLongPress (View view) {
    if ((flags & FLAG_CAUGHT) != 0) {
      flags &= ~FLAG_CAUGHT;
      if (!isActive()) {
        cancelSelection();
        view.post(() -> mDelegate.onLongClick(view));
      }
    }
    return true;
  }

  public void performClick (View view) {
    if (!isBlocked()) {
      view.post(() -> mDelegate.onClick(view));
      if (!isActive()) {
        cancelSelection();
      }
    }
  }

  public boolean handleTouch (int touchX, int touchY) {
    return touchX >= mBounds.left
      && touchX < mBounds.right
      && touchY >= mBounds.top
      && touchY < mBounds.bottom;
  }

  public void invalidate () {
    mParent.invalidate(
      (int) mBounds.left,
      (int) mBounds.top,
      (int) mBounds.right,
      (int) mBounds.bottom
    );
  }

  public void draw (Canvas canvas, int x, int y) {
    Log.e("CommentButton", "start draw message id at x = " + x + " y = " + y);
    resetBounds(x, y);
    drawDebug(canvas);
    drawBackground(canvas);
    drawStartIcon(canvas);
    drawCounter(canvas);
    drawAvatarStack(canvas);
    drawEndIcon(canvas);
    drawSelection(canvas);
    Log.e("CommentButton", "end draw message at x = " + x + " y = " + y);
  }

  private void drawDebug (Canvas canvas) {
    if (!DEBUG) return;
    canvas.drawRect(mBounds, Paints.fillingPaint(Color.LTGRAY));
  }

  private void drawStartIcon (Canvas canvas) {
    int dX = mX + paddingStart;
    int dY = (int) (mY + (height - mStartIconDrawable.getIntrinsicHeight()) / 2f);
    Log.e("CommentButton", "draw start icon at x = " + dX + "; y = " + dY);
    Drawables.draw(
      canvas,
      mStartIconDrawable,
      dX,
      dY,
      PorterDuffPaint.get(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_iconActive)
    );
  }

  private void drawCounter (Canvas canvas) {
    DrawAlgorithms.drawCounter(
      canvas,
      mX + paddingStart + startIconPadding + mStartIconDrawable.getIntrinsicWidth(),
      mY + this.height / 2.0f,
      Gravity.LEFT,
      mCounterAnimator,
      Screen.dp(mTextSize),
      false,
      this,
      null,
      0,
      0,
      0,
      1f,
      0f,
      1f
    );
  }

  private void drawAvatarStack (Canvas canvas) {
    int endIconOffset = shouldDisplayEndIcon()
      ? mEndIconDrawable.getIntrinsicWidth() + endIconPadding
      : 0;
    mUserAvatarStack.draw(
      canvas,
      mX + width - endIconOffset - paddingEnd,
      mY + (height - mUserAvatarStack.getCurrentHeight()) / 2
    );
  }

  private void drawEndIcon (Canvas canvas) {
    if (!shouldDisplayEndIcon()) return;
    int dX = mX + width - mEndIconDrawable.getIntrinsicWidth() - paddingEnd;
    int dY = (int) (mY + (height - mEndIconDrawable.getIntrinsicHeight()) / 2f);
    Log.e("CommentButton", "draw end icon at x = " + dX + "; y = " + dY);
    Drawables.draw(
      canvas,
      mEndIconDrawable,
      dX,
      dY,
      PorterDuffPaint.get(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_iconLight)
    );
  }

  private void drawBackground (Canvas canvas) {
    if (mNeedBackground) {
      Paint paint = Paints.fillingPaint(Theme.getColor(R.id.theme_color_bubble_button));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Path path = getBackgroundPath();
        path.addRoundRect(mBounds, height / 2f, height / 2f, Path.Direction.CW);
        canvas.drawPath(path, paint);
      } else {
        canvas.drawRect(mBounds, paint);
      }
    }
  }

  private void drawSelection (Canvas canvas) {
    int selectionColor = mParent.useBubbles()
      ? Theme.getColor(mParent.getPressColorId())
      : ColorUtils.alphaColor(0.25f, Theme.getColor(R.id.theme_color_bubbleIn_time));
    if (fadeFactor != 0f) {
      selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
    }
    Paint paint = Paints.fillingPaint(selectionColor);
    if (selectionFactor != 0f) {
      int anchorX = Math.max(Math.min(this.touchX - mX, width), 0);
      int anchorY = Math.max(Math.min(this.touchY - mY, height), 0);
      float selectionRadius = (float) Math.sqrt(width * width + height * height) * .5f * selectionFactor;
      float centerX = mBounds.centerX();
      float centerY = mBounds.centerY();
      float diffX = centerX - anchorX;
      float diffY = centerY - anchorY;
      float selectionX = anchorX + diffX * selectionFactor;
      float selectionY = anchorY + diffY * selectionFactor;

      final int saveCount;
      if ((saveCount = ViewSupport.clipPath(canvas, getBackgroundPath())) != Integer.MIN_VALUE) {
        canvas.drawCircle(selectionX, selectionY, selectionRadius, paint);
      }
      ViewSupport.restoreClipPath(canvas, saveCount);
    }
  }

  private Path getBackgroundPath () {
    return mBackgroundPath;
  }

  private void resetBackgroundPath () {
    mBackgroundPath.reset();
    if (mNeedBackground) {
      mBackgroundPath.addRoundRect(mBounds, height / 2f, height / 2f, Path.Direction.CW);
    } else if (mParent.useBubbles()) {
      mBackgroundClipRegion.set(
        (int) mBounds.left,
        (int) mBounds.top,
        (int) mBounds.right,
        (int) mBounds.bottom
      );
      mBottomBubbleRegion.setPath(mParent.getBubblePath(), mBackgroundClipRegion);
      mBackgroundPath = mBottomBubbleRegion.getBoundaryPath();
    } else {
      mBackgroundPath.addRect(mBounds, Path.Direction.CW);
    }
  }

  private void resetBounds (int x, int y) {
    if (mX != x || mY != y) {
      mX = x;
      mY = y;
      mBounds.set(x, y, x + width, y + height);
      resetBackgroundPath();
    }
  }

  private boolean shouldDisplayEndIcon () {
    return forceDisplayEndIcon || displayEndIcon;
  }

  private void setupCounter (boolean ellipsize) {
    if (mInfo == null) return;
    String counterText = mText;
    if (ellipsize) {
      counterText = TextUtils.ellipsize(
        mText,
        Paints.getBoldTextPaint(mTextSize, defaultTextColor()),
        getAvailableTextWidth(),
        TextUtils.TruncateAt.END
      ).toString();
    }
    mCounterAnimator.setCounter(
      mInfo.count > 0 ? mInfo.count : -1,
      counterText,
      mAnimated
    );
  }

  private int getAvailableTextWidth () {
    return width - getWidthWithoutText();
  }

  private int getWidthWithoutText () {
    return (int) (paddingStart +
      mStartIconDrawable.getIntrinsicWidth() +
      startIconPadding +
      endIconPadding +
      paddingEnd +
      mUserAvatarStack.getCurrentWidth() +
      (shouldDisplayEndIcon() ? mEndIconDrawable.getIntrinsicWidth() : 0));
  }

  private void animateSelectionFactor (float toFactor) {
    if (selectionAnimator == null) {
      selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
    }
    selectionAnimator.animateTo(toFactor);
  }

  private void animateFadeFactor (float toFactor) {
    if (toFactor == 1f) {
      flags &= ~FLAG_ACTIVE;
    }
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
    }
    flags |= FLAG_BLOCKED;
    fadeAnimator.animateTo(toFactor);
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

  private boolean isActive () {
    return (flags & FLAG_ACTIVE) != 0;
  }

  private boolean isCaught () {
    return (flags & FLAG_CAUGHT) != 0;
  }

  private boolean isBlocked () {
    return (flags & FLAG_BLOCKED) != 0;
  }

  private Map<Long, UserAvatarStack.AvatarInfo> getAvatars (TdApi.MessageSender[] senders) {
    Map<Long, UserAvatarStack.AvatarInfo> avatarInfo = new HashMap<>();
    if (senders != null) {
      float avatarRadius = UserAvatarStack.getDefaultAvatarSize() / 2f;
      for (TdApi.MessageSender sender : senders) {
        ImageFile imageFile = null;
        long memberId = -1;
        if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
          long userId = ((TdApi.MessageSenderUser) sender).userId;
          imageFile = mParent.tdlib().cache().userAvatar(userId);
          memberId = userId;
        } else if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
          long chatId = ((TdApi.MessageSenderChat) sender).chatId;
          imageFile = TD.getAvatar(mParent.tdlib(), mParent.tdlib().chat(chatId));
          memberId = chatId;
        }
        if (memberId != -1) {
          AvatarPlaceholder avatarPlaceholder = mParent.tdlib().cache()
            .userPlaceholder(memberId, false, avatarRadius, null);
          UserAvatarStack.AvatarInfo info = new UserAvatarStack.AvatarInfo(
            memberId,
            imageFile,
            avatarPlaceholder
          );
          avatarInfo.put(memberId, info);
        }
      }
    }
    return avatarInfo;
  }
}
