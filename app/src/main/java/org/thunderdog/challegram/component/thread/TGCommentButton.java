package org.thunderdog.challegram.component.thread;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.helper.ForceTouchPreviewDelegate;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;

public class TGCommentButton extends ForceTouchPreviewDelegate implements
  CounterAnimator.Callback<Text>, TextColorSet, FactorAnimator.Target,
  ForceTouchPreviewDelegate.CustomControllerProvider,
  ForceTouchPreviewDelegate.ActionListProvider {

  public static final int DEFAULT_PADDING = Screen.dp(16);
  public static final int DEFAULT_HEIGHT = Screen.dp(40);
  private static final int SELECTION_ANIMATOR = 0;
  private static final int FADE_ANIMATOR = 1;
  private static final long ANIMATION_DURATION = 180L;
  private static final int sTextSizeDp = 14;
  private static final int sSmallTextSizeDp = 12;
  private static final int sUnreadSize = Screen.dp(6);
  private static final boolean DEBUG = false;

  private final CounterAnimator<Text> mCounterAnimator;
  private final UserAvatarStack mUserAvatarStack;
  private final TGMessage mParent;
  private final RectF mBounds;
  private final Region mBackgroundClipRegion;
  private final Region mBottomBubbleRegion;
  private final ClickHelper mClickHelper;
  private Drawable mStartIconDrawable;
  private Drawable mEndIconDrawable;
  private int width, height;
  private Info mInfo;
  private String mText;
  private boolean mAnimated = false;
  private int paddingStart = DEFAULT_PADDING, paddingEnd = DEFAULT_PADDING;
  private int startIconPadding = Screen.dp(12);
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
  private boolean mPendingFade = false;
  private boolean isEnabled = false;

  public static class Info {
    private final int count;
    private final TdApi.MessageSender[] senders;
    private boolean hasUnread;

    public Info (int count, TdApi.MessageSender[] senders, boolean hasUnread) {
      this.count = count;
      this.senders = senders;
      this.hasUnread = hasUnread;
    }

    @Override
    public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Info info = (Info) o;
      return count == info.count && hasUnread == info.hasUnread && Arrays.equals(senders, info.senders);
    }

    @Override
    public int hashCode () {
      int result = Objects.hash(count, hasUnread);
      result = 31 * result + Arrays.hashCode(senders);
      return result;
    }
  }

  public TGCommentButton (TGMessage parent) {
    super(parent.tdlib());
    mParent = parent;
    mClickHelper = new ClickHelper(this);
    mCounterAnimator = new CounterAnimator<>(this);
    mStartIconDrawable = Drawables.get(R.drawable.baseline_forum_18);
    mEndIconDrawable = Drawables.get(R.drawable.round_keyboard_arrow_right_18);
    mUserAvatarStack = new UserAvatarStack(this);
    mBounds = new RectF();
    mBackgroundPath = new Path();
    mBackgroundClipRegion = new Region();
    mBottomBubbleRegion = new Region();
    this.height = DEFAULT_HEIGHT;
    setNeedBackground(false);
    this.setCustomControllerProvider(this);
    this.setPreviewActionListProvider(this);
  }

  public void setupForceTouch(View view, Tdlib tdlib) {
    this.setMaximizeListener((target, animateToWhenReady, arg) ->
      MessagesController.maximizeFrom(tdlib, view.getContext(), target, animateToWhenReady, arg)
    );
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

  public void updateWidth(int width) {
    resetBounds(mX, mY, width, this.height);
  }

  public void setBounds(int width, int startX, int startY) {
    resetBounds(startX, startY, width, this.height);
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
      mTextSize = sSmallTextSizeDp;
    } else {
      mStartIconDrawable = Drawables.get(R.drawable.baseline_forum_18);
      mTextSize = sTextSizeDp;
    }
  }

  public void setEnabled(boolean enabled) {
    this.isEnabled = enabled;
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public void update (Info info, boolean isEnabled, boolean animated) {
    if (mInfo != null && mInfo.equals(info)) return;
    this.mInfo = info;
    mAnimated = animated;
    displayEndIcon = info.count == 0;
    mUserAvatarStack.update(getAvatars(info.senders), animated);
    if (mInfo.count == 0) {
      mText = Lang.getString(R.string.LeaveComment);
    } else {
      mText = Lang.plural(R.string.xComments, mInfo.count);
    }
    setupCounter(width < getPreferredMinWidth());
    this.isEnabled = isEnabled;
    if (animated) {
      invalidate();
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
    if (id == SELECTION_ANIMATOR && finalFactor == 1f && mPendingFade) {
      cancelSelection();
    } else if (id == FADE_ANIMATOR && finalFactor == 1f) {
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
      .allBold(false)
      .build();
  }

  @Override
  public int defaultTextColor () {
    return Theme.getColor(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_inlineText);
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

  public boolean onTouchEvent (View view, @NonNull MotionEvent e) {
    if (!mClickHelper.inLongPress() && !handleTouch((int) e.getX(), (int) e.getY())) {
      mClickHelper.cancel(view, e.getX(), e.getY());
      return false;
    }
    return mClickHelper.onTouchEvent(view, e);
  }

  @Override
  public boolean needsForceTouch (View v, float x, float y) {
    return mInfo != null && mInfo.count > 0 && mBounds.contains(x, y);
  }

  @Override
  public boolean onSlideOff (View v, float x, float y, @Nullable ViewController<?> openPreview) {
    return false;
  }

  @Override
  public ViewController<?> createForceTouchPreview (View v, float x, float y) {
    return mParent.getMessageThreadPreviewController();
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return mBounds.contains(x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    mParent.openMessageThread(null);
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return mBounds.contains(x, y);
  }

  @Override
  public void onClickTouchDown (View view, float x, float y) {
    super.onClickTouchDown(view, x, y);
    this.touchX = (int) x;
    this.touchY = (int) y;
    animateSelectionFactor(1f);
  }

  @Override
  public void onClickTouchUp (View view, float x, float y) {
    super.onClickTouchUp(view, x, y);
    cancelSelection();
  }

  @Override
  public void onLongPress (View view, float x, float y) {
    performLongPress(view, x, y);
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
   if (mInfo.count == 0) {
      onLongPress(view, x, y);
      return false;
    }else if (mParent.getLastThreadInfo() != null) {
      return super.onLongPressRequestedAt(view, x, y);
    } else {
      mParent.getMessageThread(null, messageThreadInfo -> {
        super.onLongPressRequestedAt(view, x, y);
      });
      return false;
    }
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    super.onLongPressCancelled(view, x, y);
    cancelSelection();
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    super.onLongPressFinish(view, x, y);
    cancelSelection();
  }


  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    TdApi.MessageThreadInfo threadInfo = mParent.getLastThreadInfo();
    boolean canRead = false;
    if (threadInfo != null) {
      TdApi.Chat chat = mParent.tdlib().chat(threadInfo.chatId);
      canRead = mParent.tdlib().canMarkAsRead(chat);
    }
    ids.append(canRead ? R.id.btn_markChatAsRead : R.id.btn_markChatAsUnread);
    strings.append(canRead ? R.string.MarkAsRead : R.string.MarkAsUnread);
    icons.append(canRead ? Config.ICON_MARK_AS_READ : Config.ICON_MARK_AS_UNREAD);
    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        if (threadInfo == null) return;
        if (actionId == R.id.btn_markChatAsRead) {
          mParent.tdlib().markChatAsRead(threadInfo.chatId, threadInfo.messageThreadId, () -> {
            mInfo.hasUnread = false;
            invalidate();
          });
        } else if ( actionId == R.id.btn_markChatAsUnread) {
          mParent.tdlib().markChatAsUnread(mParent.tdlib().chat(threadInfo.chatId), () -> {
            mInfo.hasUnread = true;
            invalidate();
          });
        }
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

      }
    };
  }

  public boolean performLongPress (View view, float x, float y) {
    cancelSelection();
    mClickHelper.onLongPress(view, x, y);
    return true;
  }

  public boolean handleTouch (int touchX, int touchY) {
    return isEnabled && touchX >= mBounds.left
      && touchX < mBounds.right
      && touchY >= mBounds.top
      && touchY < mBounds.bottom;
  }

  public void invalidate () {
    if (mParent.isDestroyed()) return;
    mParent.invalidate(
      (int) mBounds.left,
      (int) mBounds.top,
      (int) mBounds.right,
      (int) mBounds.bottom
    );
  }

  public void draw (Canvas canvas) {
    drawDebug(canvas);
    drawBackground(canvas);
    drawStartIcon(canvas);
    drawCounter(canvas);
    drawUnread(canvas);
    drawAvatarStack(canvas);
    drawEndIcon(canvas);
    drawSelection(canvas);
  }

  private void drawDebug (Canvas canvas) {
    if (!DEBUG) return;
    canvas.drawRect(mBounds, Paints.fillingPaint(Color.LTGRAY));
  }

  private void drawStartIcon (Canvas canvas) {
    int dX = mX + paddingStart;
    int dY = (int) (mY + (height - mStartIconDrawable.getIntrinsicHeight()) / 2f);
    Drawables.draw(
      canvas,
      mStartIconDrawable,
      dX,
      dY,
      PorterDuffPaint.get(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_inlineIcon, getEnabledAlpha())
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
      getEnabledAlpha(),
      getEnabledAlpha(),
      1f
    );
  }

  private void drawAvatarStack (Canvas canvas) {
    mUserAvatarStack.draw(canvas);
  }

  private void drawEndIcon (Canvas canvas) {
    if (!shouldDisplayEndIcon()) return;
    int dX = mX + width - mEndIconDrawable.getIntrinsicWidth() - paddingEnd;
    int dY = (int) (mY + (height - mEndIconDrawable.getIntrinsicHeight()) / 2f);
    Drawables.draw(
      canvas,
      mEndIconDrawable,
      dX,
      dY,
      PorterDuffPaint.get(mNeedBackground ? R.id.theme_color_white : R.id.theme_color_iconLight, getEnabledAlpha())
    );
  }

  private void drawBackground (Canvas canvas) {
    if (mNeedBackground) {
      Paint paint = Paints.fillingPaint(
        ColorUtils.alphaColor(
          getEnabledAlpha(),
          mParent.getBubbleButtonBackgroundColor()
        )
      );
      Path path = getBackgroundPath();
      canvas.drawPath(path, paint);
    }
  }

  private void drawUnread(Canvas canvas) {
    if (mInfo == null || !mInfo.hasUnread) return;
    int endIconOffset = shouldDisplayEndIcon()
      ? mEndIconDrawable.getIntrinsicWidth() + endIconPadding
      : 0;
    float radius = sUnreadSize / 2f;
    canvas.drawCircle(
      mX + width - endIconOffset - paddingEnd - mUserAvatarStack.getCurrentWidth() - endIconPadding - radius,
      mBounds.centerY(),
      radius,
      Paints.fillingPaint(
        ColorUtils.alphaColor(
          getEnabledAlpha(),
          defaultTextColor()
        )
      )
    );
  }

  private void drawSelection (Canvas canvas) {
    int selectionColor = mParent.useBubbles()
      ? Theme.getColor(mParent.getPressColorId())
      : ColorUtils.alphaColor(0.25f, Theme.getColor(R.id.theme_color_bubbleIn_time));
    if (fadeFactor != 0f) {
      selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
    }
    Paint paint = Paints.fillingPaint(selectionColor);
    if (selectionFactor == 0f) return;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * selectionFactor), selectionColor);
      canvas.drawPath(getBackgroundPath(), Paints.fillingPaint(selectionColor));
      return;
    }
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

  private Path getBackgroundPath () {
    return mBackgroundPath;
  }

  private void resetBackgroundPath () {
    mBackgroundPath.reset();
    if (mNeedBackground) {
      mBackgroundPath.addRoundRect(mBounds, height / 2f, height / 2f, Path.Direction.CW);
    } else if (mParent.useBubbles() && mParent.getBubblePath() != null) {
      mBackgroundClipRegion.set(
        (int) mBounds.left,
        (int) mBounds.top,
        (int) mBounds.right,
        (int) mBounds.bottom
      );
      mBottomBubbleRegion.setPath(mParent.getBubblePath(), mBackgroundClipRegion);
      mBottomBubbleRegion.getBoundaryPath(mBackgroundPath);
      mBackgroundPath.close();
    } else {
      mBackgroundPath.addRect(mBounds, Path.Direction.CW);
    }
  }

  private void resetBounds (int x, int y, int width, int height) {
    boolean positionChanged = mX != x || mY != y;
    boolean sizeChanged = this.width != width || this.height != height;
    if (positionChanged || sizeChanged) {
      mX = x;
      mY = y;
      this.width = width;
      this.height = height;
      mBounds.set(x, y, x + width, y + height);
      int endIconOffset = shouldDisplayEndIcon()
        ? mEndIconDrawable.getIntrinsicWidth() + endIconPadding
        : 0;
      mUserAvatarStack.setEndEdge(
        mX + width - endIconOffset - paddingEnd,
        mY + (height - mUserAvatarStack.getCurrentHeight()) / 2
      );
      resetBackgroundPath();
      if (sizeChanged) {
        setupCounter(width < getPreferredMinWidth());
      }
    }
  }

  private boolean shouldDisplayEndIcon () {
    return forceDisplayEndIcon || displayEndIcon;
  }

  private void setupCounter (boolean ellipsize) {
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
      mInfo != null && mInfo.count > 0 ? mInfo.count : -1,
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
      (mInfo != null && mInfo.hasUnread ? sUnreadSize : 0) +
      (shouldDisplayEndIcon() ? mEndIconDrawable.getIntrinsicWidth() : 0));
  }

  private void animateSelectionFactor (float toFactor) {
    if (selectionAnimator == null) {
      selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
    }
    selectionAnimator.animateTo(toFactor);
  }

  private void animateFadeFactor (float toFactor) {
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
      fadeAnimator.setStartDelay(ANIMATION_DURATION);
    }
    fadeAnimator.animateTo(toFactor);
  }

  private void cancelSelection () {
    mPendingFade = selectionAnimator.isAnimating();
    if (!mPendingFade) {
      animateFadeFactor(1f);
    }
  }

  private void forceResetSelection () {
    if (fadeAnimator != null) {
      fadeAnimator.forceFactor(this.fadeFactor = 0f);
    }
    if (selectionAnimator != null) {
      selectionAnimator.forceFactor(this.selectionFactor = 0f);
    }
  }

  private float getEnabledAlpha() {
    return isEnabled ? 1f : 0.7f;
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
