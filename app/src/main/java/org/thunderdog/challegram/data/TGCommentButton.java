package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.PorterDuffThemeColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.FeatureToggles;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.FormattedCounterAnimator;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public final class TGCommentButton implements FactorAnimator.Target, TextColorSet, FormattedCounterAnimator.Callback<Text>, ClickHelper.Delegate {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({VIEW_MODE_HIDDEN, VIEW_MODE_INLINE, VIEW_MODE_BUBBLE})
  public @interface ViewMode {
  }

  public static final int VIEW_MODE_HIDDEN = 0;
  public static final int VIEW_MODE_INLINE = 1;
  public static final int VIEW_MODE_BUBBLE = 2;

  private static final int VISIBILITY_ANIMATOR = 1;
  private static final int SELECTION_ANIMATOR = 2;
  private static final int FADE_ANIMATOR = 3;
  private static final int ARROW_VISIBILITY_ANIMATOR = 4;
  private static final int BADGE_VISIBILITY_ANIMATOR = 5;

  private static final long VISIBILITY_ANIMATION_DURATION = 180l;
  private static final long ARROW_VISIBILITY_ANIMATION_DURATION = 280l;
  private static final long BADGE_VISIBILITY_ANIMATION_DURATION = 280l;
  private static final long COUNTER_ANIMATION_DURATION = 280l;
  private static final long SELECTION_ANIMATION_DURATION = 180l;
  private static final long FADE_ANIMATION_DURATION = 180l;

  private static final @Dimension(unit = Dimension.DP) float INLINE_TEXT_SIZE = 15f;
  private static final @Dimension(unit = Dimension.DP) float BUBBLE_TEXT_SIZE = 13f;
  private static final @Dimension(unit = Dimension.DP) float BADGE_RADIUS = 3f;

  private static final float[] TEMP_RADII = new float[8];

  private final Rect rect = new Rect();
  private final Path path = new Path();
  private final ClickHelper clickHelper = new ClickHelper(this);
  private final TGMessage context;
  private final TGAvatars avatars;

  private final BoolAnimator visibilityAnimator = new BoolAnimator(VISIBILITY_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, VISIBILITY_ANIMATION_DURATION);
  private final BoolAnimator arrowVisibilityAnimator = new BoolAnimator(ARROW_VISIBILITY_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ARROW_VISIBILITY_ANIMATION_DURATION);
  private final BoolAnimator badgeVisibilityAnimator = new BoolAnimator(BADGE_VISIBILITY_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, BADGE_VISIBILITY_ANIMATION_DURATION);
  private final FormattedCounterAnimator<Text> counterAnimator = new FormattedCounterAnimator<>(this, COUNTER_ANIMATION_DURATION);

  private @Nullable BoolAnimator selectionAnimator, fadeAnimator;
  private @ViewMode int viewMode = VIEW_MODE_HIDDEN;

  public TGCommentButton (@NonNull TGMessage context) {
    this.context = context;
    this.avatars = new TGAvatars(context.tdlib(), new TGAvatars.Callback() {
      @Override
      public void onSizeChanged () {
        if (context.useBubbles()) {
          TGCommentButton.this.onSizeChanged();
        }
      }

      @Override
      public void onInvalidateMedia (TGAvatars avatars) {
        context.invalidateAvatarsReceiver();
      }
    }, context.currentViews);
  }

  @Override public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == VISIBILITY_ANIMATOR || context.useBubbles() && (id == ARROW_VISIBILITY_ANIMATOR || id == BADGE_VISIBILITY_ANIMATOR)) {
      onSizeChanged();
    }
    context.invalidate();
  }

  @Override public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == FADE_ANIMATOR && finalFactor == 1f) {
      forceResetSelection();
    }
  }

  public void requestResources (@Nullable ComplexReceiver complexReceiver, boolean isUpdate) {
    this.avatars.requestFiles(complexReceiver, isUpdate);
  }

  public void setViewMode (@ViewMode int viewMode, boolean animated) {
    if (viewMode != VIEW_MODE_HIDDEN) {
      this.viewMode = viewMode;
    }
    this.visibilityAnimator.setValue(viewMode != VIEW_MODE_HIDDEN, animated);
  }

  public void showAsViewInChat (boolean animated) {
    counterAnimator.setCounter(0, Lang.getString(R.string.ViewInChat), animated);
    avatars.setSenders(null, animated);
    arrowVisibilityAnimator.setValue(true, animated);
    badgeVisibilityAnimator.setValue(false, animated);
  }

  public void setReplyInfo (@Nullable TdApi.MessageReplyInfo replyInfo, boolean animated) {
    int replyCount = replyInfo != null ? replyInfo.replyCount : 0;
    setReplyCount(replyCount, animated);

    TdApi.MessageSender[] recentReplierIds = replyInfo != null ? replyInfo.recentReplierIds : null;
    setRecentReplierIds(recentReplierIds, animated);

    boolean hasRecentReplierIds = recentReplierIds != null && recentReplierIds.length > 0;
    boolean showArrow = !context.useBubbles() || !hasRecentReplierIds;
    arrowVisibilityAnimator.setValue(showArrow, animated);

    long lastReadInboxMessageId = replyInfo != null ? replyInfo.lastReadInboxMessageId : 0;
    boolean showUnread = replyCount > 0 && hasRecentReplierIds && Td.hasUnread(replyInfo) && lastReadInboxMessageId != 0;
    badgeVisibilityAnimator.setValue(showUnread, animated);
  }

  private void setReplyCount (int replyCount, boolean animated) {
    if (replyCount > 0) {
      counterAnimator.setCounter(R.string.xComments, replyCount, animated);
    } else {
      counterAnimator.setCounter(0, Lang.getString(R.string.LeaveComment), animated);
    }
  }

  private void setRecentReplierIds (TdApi.MessageSender[] recentReplierIds, boolean animated) {
    avatars.setSenders(recentReplierIds, animated);
  }

  public float getVisibility () {
    return visibilityAnimator.getFloatValue();
  }

  public boolean isVisible () {
    return visibilityAnimator.getFloatValue() > 0f;
  }

  public boolean isInline () {
    return viewMode == VIEW_MODE_INLINE;
  }

  public boolean isBubble () {
    return viewMode == VIEW_MODE_BUBBLE;
  }

  public @Px int getHeight () {
    if (isInline()) {
      return Screen.dp(42f);
    }
    if (isBubble()) {
      return Screen.dp(32f);
    }
    return 0;
  }

  public @Px int getAnimatedWidth (@Px int extraWidth, float scale) {
    float badgeVisibility = badgeVisibilityAnimator.getFloatValue();
    if (isInline()) {
      // context.useBubbles() == true
      float width = Screen.dp(46f) + counterAnimator.getWidth() + Screen.dp(13f);
      if (badgeVisibility > 0f) {
        width += (Screen.dp(BADGE_RADIUS) * 2 + Screen.dp(8f)) * badgeVisibility;
      }
      width += MathUtils.fromTo(avatars.getAnimatedWidth() + Screen.dp(16f), Screen.dp(52f), arrowVisibilityAnimator.getFloatValue());
      return Math.round((width + extraWidth) * scale);
    }
    if (isBubble()) {
      float width = Screen.dp(34f) + counterAnimator.getWidth() + Screen.dp(13f);
      if (badgeVisibility > 0f) {
        width += (Screen.dp(BADGE_RADIUS) * 2 + Screen.dp(8f)) * badgeVisibility;
      }
      width += MathUtils.fromTo(avatars.getAnimatedWidth() + Screen.dp(16f), Screen.dp(19f), arrowVisibilityAnimator.getFloatValue());
      if (Config.COMMENTS_BUBBLE_BUTTON_MIN_WIDTH > 0 && FeatureToggles.COMMENTS_BUBBLE_BUTTON_HAS_MIN_WIDTH) {
        width = Math.max(width, Screen.dp(Config.COMMENTS_BUBBLE_BUTTON_MIN_WIDTH));
      }
      return Math.round((width + extraWidth) * scale);
    }
    return 0;
  }

  public @Px int getAnimatedHeight (@Px int extraHeight, float scale) {
    return Math.round((getHeight() + extraHeight) * scale);
  }

  public boolean contains (float x, float y) {
    return rect.contains(Math.round(x), Math.round(y));
  }

  private static final int FLAG_CAUGHT = 0x01;
  private static final int FLAG_BLOCKED = 0x02;

  private int flags;
  private int anchorX, anchorY;

  private boolean isCaught () {
    return BitwiseUtils.hasFlag(flags, FLAG_CAUGHT);
  }

  private boolean isBlocked () {
    return BitwiseUtils.hasFlag(flags, FLAG_BLOCKED);
  }

  public boolean onTouchEvent (View view, MotionEvent event) {
    return clickHelper.onTouchEvent(view, event);
  }

  @Override public boolean needClickAt (View view, float x, float y) {
    return isVisible() && contains(x, y);
  }

  @Override public boolean needLongPress (float x, float y) {
    return Config.FORCE_TOUCH_ENABLED && Settings.instance().needPreviewChatOnHold();
  }

  @Override public boolean ignoreHapticFeedbackSettings (float x, float y) {
    return true;
  }

  @Override public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  @Override public void onClickAt (View view, float x, float y) {
    if (context.isRepliesChat()) {
      TdApi.MessageForwardInfo forwardInfo = context.msg.forwardInfo;
      MessageId replyToMessageId = new MessageId(context.msg.replyInChatId, context.msg.replyToMessageId);
      if (forwardInfo != null && forwardInfo.fromChatId != 0 && forwardInfo.fromMessageId != 0) {
        MessageId replyMessageId = new MessageId(forwardInfo.fromChatId, forwardInfo.fromMessageId);
        context.openMessageThread(replyMessageId, replyToMessageId);
      } else {
        context.openMessageThread(replyToMessageId);
      }
    } else {
      context.openMessageThread();
    }
  }

  @Override public void onClickTouchDown (View view, float x, float y) {
    flags = BitwiseUtils.setFlag(flags, FLAG_CAUGHT, true);
    anchorX = Math.round(x);
    anchorY = Math.round(y);
    if (!isBlocked()) {
      animateSelection();
    }
  }

  @Override public void onClickTouchMove (View view, float x, float y) {
    if (!isCaught()) {
      return;
    }
    anchorX = Math.round(x);
    anchorY = Math.round(y);
  }

  @Override public void onClickTouchUp (View view, float x, float y) {
    if (!isCaught()) {
      return;
    }
    flags = BitwiseUtils.setFlag(flags, FLAG_CAUGHT, false);
    if (!isBlocked()) {
      cancelSelection();
    }
  }

  @Override public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (isCaught()) {
      openCommentsPreviewAsync(Math.round(x), Math.round(y));
    }
    return false;
  }

  @Override public void onLongPressCancelled (View view, float x, float y) {
    cancelPreview(view);
  }

  @Override public void onLongPressFinish (View view, float x, float y) {
    cancelPreview(view);
  }

  @Override public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    if (currentPreviewController != null) {
      context.context().processForceTouchMoveEvent(x, y, startX, startY);
    }
  }

  private void cancelPreview (View view) {
    if (view instanceof MessageView) {
      ((MessageView) view).setLongPressed(false);
    }
    cancelAsyncPreview();
    closePreview();
  }

  private void animateSelection () {
    if (selectionAnimator == null) {
      selectionAnimator = new BoolAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, SELECTION_ANIMATION_DURATION);
    }
    selectionAnimator.setValue(true, true);
  }

  private void cancelSelection () {
    if (fadeAnimator == null) {
      fadeAnimator = new BoolAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, FADE_ANIMATION_DURATION);
    }
    flags = BitwiseUtils.setFlag(flags, FLAG_BLOCKED, true);
    fadeAnimator.setValue(true, true);
  }

  private void forceResetSelection () {
    if (fadeAnimator != null) {
      fadeAnimator.forceValue(false, 0f);
      flags = BitwiseUtils.setFlag(flags, FLAG_BLOCKED, false);
    }
    if (selectionAnimator != null) {
      selectionAnimator.forceValue(false, 0f);
    }
  }

  public void draw (@NonNull MessageView view, @NonNull Canvas c, @NonNull DrawableProvider drawableProvider, int left, int top, int right, int bottom) {
    if (isBubble()) {
      drawBubble(view, c, drawableProvider, left, top, right, bottom);
    } else if (isInline()) {
      drawInline(view, c, drawableProvider, left, top, right, bottom);
    }
  }

  private void drawInline (@NonNull MessageView view, @NonNull Canvas c, @NonNull DrawableProvider drawableProvider, int left, int top, int right, int bottom) {
    boolean useBubbles = context.useBubbles();

    if (rect.left != left || rect.top != top || rect.right != right || rect.bottom != bottom) {
      rect.set(left, top, right, bottom);
      path.reset();
      if (useBubbles) {
        TEMP_RADII[0] = TEMP_RADII[1] = 0f;
        TEMP_RADII[2] = TEMP_RADII[3] = 0f;
        TEMP_RADII[4] = TEMP_RADII[5] = context.getBubbleBottomRightRadius();
        TEMP_RADII[6] = TEMP_RADII[7] = context.getBubbleBottomLeftRadius();
        RectF tempRect = Paints.getRectF();
        tempRect.set(left, top, right, bottom);
        path.addRoundRect(tempRect, TEMP_RADII, Path.Direction.CCW);
      } else {
        path.addRect(left, top, right, bottom, Path.Direction.CCW);
      }
    }
    float alpha = visibilityAnimator.getFloatValue();
    float selectionFactor = selectionAnimator != null ? selectionAnimator.getFloatValue() : 0f;

    int saveCount;
    if (alpha != 1f || selectionFactor != 0f) {
      saveCount = ViewSupport.clipPath(c, path);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    if (selectionFactor != 0f && saveCount != Integer.MIN_VALUE) {
      int selectionColor = Theme.getColor(context.getPressColorId());
      drawSelection(c, selectionFactor, selectionColor);
    }

    int iconColorId = R.id.theme_color_inlineIcon;
    Drawable icon = drawableProvider.getSparseDrawable(R.drawable.baseline_forum_18, iconColorId);
    float iconX = useBubbles ? left + Screen.dp(16f) : (TGMessage.getContentLeft() - icon.getMinimumWidth()) / 2f;
    float iconY = rect.centerY() - icon.getMinimumHeight() / 2f;
    Drawables.draw(c, icon, iconX, iconY, PorterDuffPaint.get(iconColorId, alpha));

    float textX = useBubbles ? left + Screen.dp(46f) : TGMessage.getContentLeft();
    float textY = rect.centerY();
    drawText(c, textX, textY, alpha);

    float arrowVisibility = arrowVisibilityAnimator.getFloatValue();
    if (arrowVisibility > 0f) {
      int arrowColor = ColorUtils.alphaColor(alpha * arrowVisibility, Theme.getColor(R.id.theme_color_iconLight));
      int arrowX = right - (useBubbles ? Screen.dp(22f) : Screen.dp(18f));
      int arrowY = rect.centerY();

      float arrowScale = 0.75f;
      c.save();
      c.scale(arrowScale, arrowScale, arrowX, arrowY);
      DrawAlgorithms.drawDirection(c, arrowX, arrowY, arrowColor, Gravity.RIGHT);
      c.restore();
    }

    int avatarsX = right - (useBubbles ? Screen.dp(16f) : Screen.dp(38f));
    int avatarsY = rect.centerY();
    avatars.draw(view, c, avatarsX, avatarsY, Gravity.RIGHT, alpha);

    int badgeX = avatarsX - Math.round(avatars.getAnimatedWidth()) - Screen.dp(8f) - Screen.dp(BADGE_RADIUS);
    int badgeY = rect.centerY();
    drawBadge(c, badgeX, badgeY, alpha);

    ViewSupport.restoreClipPath(c, saveCount);
  }

  private void drawBubble (@NonNull MessageView view, @NonNull Canvas c, @NonNull DrawableProvider drawableProvider, int left, int top, int right, int bottom) {
    float radius = 0.75f * (bottom - top);
    if (rect.left != left || rect.top != top || rect.right != right || rect.bottom != bottom) {
      rect.set(left, top, right, bottom);
      path.reset();
      RectF tempRect = Paints.getRectF();
      tempRect.set(left, top, right, bottom);
      path.addRoundRect(tempRect, radius, radius, Path.Direction.CCW);
    }
    float alpha = visibilityAnimator.getFloatValue();
    float selectionFactor = selectionAnimator != null ? selectionAnimator.getFloatValue() : 0f;

    int saveCount;
    if (alpha != 1f || selectionFactor != 0f) {
      saveCount = ViewSupport.clipPath(c, path);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    RectF tempRect = Paints.getRectF();
    tempRect.set(rect);
    c.drawRoundRect(tempRect, radius, radius, Paints.fillingPaint(getBubbleBackgroundColor()));

    if (selectionFactor != 0f && saveCount != Integer.MIN_VALUE) {
      drawSelection(c, selectionFactor, getBubbleSelectionColor());
    }

    int iconColorId = getBubbleIconColorId();
    Drawable icon = drawableProvider.getSparseDrawable(R.drawable.baseline_forum_16, iconColorId);
    float iconX = left + Screen.dp(18f);
    float iconY = rect.centerY();
    Drawables.drawCentered(c, icon, iconX, iconY, PorterDuffPaint.get(iconColorId, alpha));

    float textX = left + Screen.dp(34f);
    float textY = rect.centerY();
    drawText(c, textX, textY, alpha);

    float arrowVisibility = arrowVisibilityAnimator.getFloatValue();
    if (arrowVisibility > 0f) {
      int arrowColor = ColorUtils.alphaColor(alpha * arrowVisibility, getBubbleArrowColor());
      int arrowX = right - Screen.dp(12f);
      int arrowY = rect.centerY();

      float arrowScale = 0.75f;
      c.save();
      c.scale(arrowScale, arrowScale, arrowX, arrowY);
      DrawAlgorithms.drawDirection(c, arrowX, arrowY, arrowColor, Gravity.RIGHT);
      c.restore();
    }

    int avatarsX = right - Screen.dp(6f);
    int avatarsY = rect.centerY();
    avatars.draw(view, c, avatarsX, avatarsY, Gravity.RIGHT, alpha);

    float badgeX = avatarsX - avatars.getAnimatedWidth() - Screen.dp(8f) - Screen.dp(BADGE_RADIUS);
    float badgeY = rect.centerY();
    drawBadge(c, badgeX, badgeY, alpha);

    ViewSupport.restoreClipPath(c, saveCount);
  }

  private void drawText (@NonNull Canvas c, float x, float cy, float alpha) {
    DrawAlgorithms.drawCounter(c, x, cy, Gravity.LEFT, counterAnimator, getTextSize(), false, this, null, Gravity.LEFT, 0, 0, alpha, 0, 1f);
  }

  private void drawSelection (@NonNull Canvas c, float selectionFactor, int selectionColor) {
    if (selectionFactor == 0f) {
      return;
    }
    float fadeFactor = fadeAnimator != null ? fadeAnimator.getFloatValue() : 0f;
    if (fadeFactor != 0f) {
      selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
    }
    int anchorX = Math.max(Math.min(this.anchorX, rect.right), rect.left);
    int anchorY = Math.max(Math.min(this.anchorY, rect.bottom), rect.top);
    float buttonWidth = rect.width();
    float buttonHeight = rect.height();
    float selectionRadius = (float) Math.sqrt(buttonWidth * buttonWidth + buttonHeight * buttonHeight) * .5f * selectionFactor;
    float diffX = rect.centerX() - anchorX;
    float diffY = rect.centerY() - anchorY;
    float selectionX = anchorX + diffX * selectionFactor;
    float selectionY = anchorY + diffY * selectionFactor;
    c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
  }

  private void drawBadge (@NonNull Canvas c, float cx, float cy, float alpha) {
    float visibility = badgeVisibilityAnimator.getFloatValue();
    if (visibility == 0f || alpha == 0f) {
      return;
    }
    float radius = Screen.dp(BADGE_RADIUS) * visibility;
    int color = ColorUtils.alphaColor(alpha, Theme.badgeColor());
    c.drawCircle(cx, cy, radius, Paints.fillingPaint(color));
  }

  @Override public int defaultTextColor () {
    if (isInline()) {
      return Theme.inlineTextColor(false);
    }
    if (isBubble()) {
      return getBubbleTextColor();
    }
    return 0;
  }

  @Override public void onItemsChanged (CounterAnimator<?> animator) {
    if (isInline() && context.useBubbles()) {
      onSizeChanged();
    } else {
      context.invalidate();
    }
  }

  private void onSizeChanged () {
    if (!context.isLayoutBuilt()) {
      return;
    }
    if (context.useBubble() && !context.useCircleBubble()) {
      int oldHeight = context.getHeight();
      if (context.allowBubbleHorizontalExtend()) {
        context.buildBubble(false);
      } else {
        context.rebuildContent();
      }
      int newHeight = context.getHeight();
      if (newHeight != oldHeight) {
        context.onBubbleHasChanged();
        context.manager().onMessageHeightChanged(context.getChatId(), context.getId(), oldHeight, newHeight);
        context.requestLayout();
      }
    } else {
      int oldHeight = context.getHeight();
      int newHeight = context.computeHeight();
      if (newHeight != oldHeight) {
        context.height = newHeight;
        context.manager().onMessageHeightChanged(context.getChatId(), context.getId(), oldHeight, newHeight);
        context.requestLayout();
      }
    }
    context.invalidate();
  }

  @Override public Text onCreateTextDrawable (String text) {
    return new Text.Builder(text, Integer.MAX_VALUE, Paints.robotoStyleProvider(getTextSize()), this).noSpacing().allBold().build();
  }

  @Override public boolean shouldAnimatePartVerticalPosition (@NonNull FormattedCounterAnimator.Part<Text> part, long oldCount, long newCount) {
    return oldCount == 0 || newCount == 0 || part.isCounter();
  }

  private @Dimension(unit = Dimension.DP) float getTextSize () {
    return isInline() ? INLINE_TEXT_SIZE : BUBBLE_TEXT_SIZE;
  }

  private boolean useDarkTheme () {
    return FeatureToggles.COMMENTS_BUBBLE_BUTTON_ALWAYS_DARK;
  }

  private @ColorInt int getBubbleTextColor () {
    return useDarkTheme() ? 0xFFFFFFFF : context.getBubbleButtonTextColor();
  }

  private @ColorInt int getBubbleBackgroundColor () {
    return useDarkTheme() ? 0x66000000 : context.getBubbleButtonBackgroundColor();
  }

  private @ColorInt int getBubbleSelectionColor () {
    return useDarkTheme() ? 0x33FFFFFF : context.getBubbleButtonRippleColor();
  }

  private @ColorInt int getBubbleArrowColor () {
    return useDarkTheme() ? 0x80FFFFFF : ColorUtils.alphaColor(0.5f, Theme.getColor(getBubbleIconColorId()));
  }

  private @PorterDuffThemeColorId int getBubbleIconColorId () {
    return useDarkTheme() ? R.id.theme_color_white : R.id.theme_color_bubble_mediaTimeText;
  }

  private void openCommentsPreviewAsync (int x, int y) {
    MessageId messageId, fallbackMessageId;
    if (context.isRepliesChat()) {
      if (context.msg.forwardInfo != null) {
        messageId = new MessageId(context.msg.forwardInfo.fromChatId, context.msg.forwardInfo.fromMessageId);
        fallbackMessageId = new MessageId(context.msg.replyInChatId, context.msg.replyToMessageId);
      } else {
        messageId = new MessageId(context.msg.replyInChatId, context.msg.replyToMessageId);
        fallbackMessageId = null;
      }
    } else {
      TdApi.Message messageWithThread = context.findMessageWithThread();
      if (messageWithThread != null) {
        messageId = new MessageId(messageWithThread.chatId, messageWithThread.id);
        fallbackMessageId = null;
      } else {
        return;
      }
    }
    openCommentsPreviewAsync(messageId, fallbackMessageId, x, y);
  }

  private void openCommentsPreviewAsync (@NonNull MessageId messageId, @Nullable MessageId fallbackMessageId, int x, int y) {
    cancelAsyncPreview();
    TdApi.GetMessageThread messageThreadQuery = new TdApi.GetMessageThread(messageId.getChatId(), messageId.getMessageId());
    currentMessageThreadQuery = messageThreadQuery;
    context.tdlib().send(messageThreadQuery, (result) -> context.runOnUiThreadOptional(() -> {
      if (messageThreadQuery != currentMessageThreadQuery) {
        return;
      }
      currentMessageThreadQuery = null;
      switch (result.getConstructor()) {
        case TdApi.MessageThreadInfo.CONSTRUCTOR:
          openCommentsPreviewAsync((TdApi.MessageThreadInfo) result, x, y);
          break;
        case TdApi.Error.CONSTRUCTOR:
          if ("MSG_ID_INVALID".equals(TD.errorText(result))) {
            if (context.isChannel()) {
              UI.showToast(R.string.ChannelPostDeleted, Toast.LENGTH_SHORT);
            } else {
              UI.showError(result);
            }
            break;
          }
          if (fallbackMessageId != null) {
            openCommentsPreviewAsync(fallbackMessageId, null, x, y);
            break;
          }
          UI.showError(result);
          break;
      }
    }));
  }

  private @Nullable ViewController<?> currentPreviewController;
  private @Nullable ViewController<?> pendingPreviewController;
  private @Nullable CancellableRunnable pendingPreviewTask;
  private @Nullable TdApi.GetMessageThread currentMessageThreadQuery;

  private void openCommentsPreviewAsync (@NonNull TdApi.MessageThreadInfo messageThreadInfo, int x, int y) {
    if (currentPreviewController != null || pendingPreviewController != null) {
      return;
    }
    cancelAsyncPreview();
    ThreadInfo messageThread = ThreadInfo.openedFromChat(context.tdlib(), messageThreadInfo, context.getChatId());
    TdApi.Chat chat = context.tdlib().chat(messageThreadInfo.chatId);
    if (chat == null) {
      return;
    }
    MessagesController controller = new MessagesController(context.context(), context.tdlib()) {
      @Override // Костыли начинаются здесь
      public int makeGuessAboutForcePreviewHeight () {
        boolean hasHeader = true;
        boolean hasFooter = shouldShowMarkAsReadAction(this);
        return MessagesController.getForcePreviewHeight(hasHeader, hasFooter);
      }
    };
    controller.setArguments(new MessagesController.Arguments(context.tdlib(), null, chat, messageThread, null));
    openPreviewAsync(controller, x, y);
  }

  private void openPreviewAsync (@NonNull MessagesController controller, int x, int y) {
    controller.setInForceTouchMode(true);
    if (controller.wouldHideKeyboardInForceTouchMode()) {
      UI.getContext(context.context()).hideSoftwareKeyboard();
    }
    pendingPreviewController = controller;
    pendingPreviewTask = new CancellableRunnable() {
      @Override public void act () {
        if (pendingPreviewController == controller) {
          pendingPreviewController = null;
          pendingPreviewTask = null;
          openPreview(controller, x, y);
        }
      }
    };
    pendingPreviewTask.removeOnCancel(UI.getAppHandler());
    controller.scheduleAnimation(pendingPreviewTask, 600l);
    controller.get();
  }

  private void cancelAsyncPreview () {
    currentMessageThreadQuery = null;
    if (pendingPreviewController != null) {
      pendingPreviewController.destroy();
      pendingPreviewController = null;
    }
    if (pendingPreviewTask != null) {
      pendingPreviewTask.cancel();
      pendingPreviewTask = null;
    }
  }

  private void openPreview (MessagesController controller, int x, int y) {
    Tdlib tdlib = context.tdlib();
    View sourceView = context.findCurrentView();
    if (sourceView == null) {
      return;
    }
    ViewController<?> ancestor = ViewController.findAncestor(sourceView);
    if ((ancestor != null && tdlib != null && ancestor.tdlib() != null && ancestor.tdlib().id() != tdlib.id())) {
      return;
    }
    int[] location = Views.getLocationOnScreen(sourceView);
    int sourceX = location[0] + x;
    int sourceY = location[1] + y;
    ForceTouchView.ForceTouchContext forceTouchContext = new ForceTouchView.ForceTouchContext(tdlib, sourceView, controller.get(), controller);
    forceTouchContext.setAnimationSourcePoint(sourceX, sourceY);
    forceTouchContext.setStateListener(controller);
    forceTouchContext.setStateListenerArgument(controller);
    forceTouchContext.setMaximizeListener((target, animateToWhenReady, arg) -> MessagesController.maximizeFrom(tdlib, context.context(), target, animateToWhenReady, arg));

    controller.onPrepareForceTouchContext(forceTouchContext);

    if (shouldShowMarkAsReadAction(controller)) {
      int[] ids = {R.id.btn_markChatAsRead};
      int[] icons = new int[] {R.drawable.baseline_done_all_24};
      String[] hints = new String[] {Lang.getString(R.string.ActionRead)};
      ForceTouchView.ActionListener actionListener = (context, actionId, arg) -> {
        if (actionId == R.id.btn_markChatAsRead) {
          if (!(arg instanceof MessagesController)) {
            return;
          }
          MessagesController c = (MessagesController) arg;
          ThreadInfo messageThread = ((MessagesController) arg).getMessageThread();
          if (messageThread == null) {
            return;
          }
          long[] messageIds = {messageThread.getLastMessageId()};
          c.tdlib().send(new TdApi.ViewMessages(
            messageThread.getChatId(), messageIds,
            new TdApi.MessageSourceMessageThreadHistory(),
            true
          ), c.tdlib().okHandler());
        }
      };
      forceTouchContext.setButtons(actionListener, controller, ids, icons, hints);
    }

    if (context.context().openForceTouch(forceTouchContext)) {
      currentPreviewController = controller;
      clickHelper.onLongPress(sourceView, x, y);
      if (isCaught() && !isBlocked()) {
        cancelSelection();
      }
      if (sourceView instanceof MessageView) {
        ((MessageView) sourceView).setLongPressed(true);
      }
    } else {
      controller.destroy();
    }
  }

  private static boolean shouldShowMarkAsReadAction (@NonNull MessagesController controller) {
    if (BuildConfig.EXPERIMENTAL && FeatureToggles.ALWAYS_SHOW_MARK_AS_READ_ACTION_IN_THREAD_PREVIEW) {
      return true;
    }
    return controller.getMessageThread() != null &&
      controller.getMessageThread().hasUnreadMessages() &&
      controller.getMessageThread().getLastReadInboxMessageId() != 0;
  }

  private void closePreview () {
    if (currentPreviewController != null) {
      currentPreviewController = null;
      context.context().closeForceTouch();
    }
  }
}
