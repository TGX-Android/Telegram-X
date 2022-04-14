package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageLocation;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TGMessageSticker;
import org.thunderdog.challegram.data.TGMessageVideo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.HashMap;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

public class ReactionsComponent implements FactorAnimator.Target {
  // Sizes
  private static final int REACTION_HEIGHT = Screen.dp(28f);
  private static final int REACTION_ITEM_SEPARATOR = Screen.dp(6f);
  private static final int REACTION_ITEM_SEPARATOR_SMALL = Screen.dp(2f);
  private static final int REACTION_ITEM_HALF_SEPARATOR = Screen.dp(4f);
  private static final int REACTION_RADIUS = Screen.dp(32f);
  private static final int REACTION_ICON_SIZE = Screen.dp(16f);
  private static final int REACTION_ICON_SIZE_SMALL = Screen.dp(12f);
  private static final int REACTION_BASE_WIDTH = REACTION_ICON_SIZE + Screen.dp(12f);

  // Animator Settings
  public static final Interpolator RC_INTERPOLATOR = new CubicBezierInterpolator(0.2, 0.01, 0.28, 0.91);
  public static final long RC_DURATION = 250L;

  // Animator IDs
  private static final int ANIMATOR_VISIBLE = 0;
  private static final int ANIMATOR_CONTAINER_PARAMS_H = 1;
  private static final int ANIMATOR_CONTAINER_PARAMS_W = 2;

  // Animators
  private final BoolAnimator componentVisibleAnimator = new BoolAnimator(ANIMATOR_VISIBLE, this, RC_INTERPOLATOR, RC_DURATION);
  private final FactorAnimator rcWidthAnimator = new FactorAnimator(ANIMATOR_CONTAINER_PARAMS_W, this, RC_INTERPOLATOR, RC_DURATION);
  private final FactorAnimator rcHeightAnimator = new FactorAnimator(ANIMATOR_CONTAINER_PARAMS_H, this, RC_INTERPOLATOR, RC_DURATION);
  private int containerWidth, containerHeight;

  private final ArrayList<Reaction> clientReactions = new ArrayList<>();
  private TdApi.MessageReaction[] clientReactionsOrder;

  private final TGMessage source;

  private final RectF rcRect = new RectF();
  private RectF[] rcClickListeners;
  private int realMaxWidth;
  private int lastWidth, lastHeight;
  private boolean measured;

  public ReactionsComponent (TGMessage source) {
    this.source = source;
    update(source.getOldestMessage().interactionInfo != null ? source.getOldestMessage().interactionInfo.reactions : new TdApi.MessageReaction[0], false, !source.useBubbles());
  }

  private HashMap<String, Reaction> asMap () {
    HashMap<String, Reaction> existingHash = new HashMap<>();

    for (int i = 0; i < clientReactions.size(); i++) {
      Reaction existingReaction = clientReactions.get(i);
      existingHash.put(existingReaction.reaction.reaction, existingReaction);
    }

    return existingHash;
  }

  // TODO: analyze why it sometimes takes 15-20ms to complete
  public void update (TdApi.MessageReaction[] messageReactions, boolean animated, boolean measure) {
    if (clientReactions.isEmpty() && messageReactions.length == 0) return;

    ArrayList<String> emojiList = new ArrayList<>();
    HashMap<String, Reaction> existingHash = asMap();

    for (int i = 0; i < messageReactions.length; i++) {
      TdApi.MessageReaction reaction = messageReactions[i];
      emojiList.add(reaction.reaction);

      if (existingHash.containsKey(reaction.reaction)) {
        // reaction exists, update it
        Reaction existingReaction = existingHash.get(reaction.reaction);
        if (existingReaction == null) continue;
        existingReaction.update(messageReactions[i], i, animated, shouldRenderSmall());
      } else {
        // reaction does not exist, add it
        // note that previous index is already added, so we can be assured in list changes
        Reaction newReaction = new Reaction(source.tdlib(), source, reaction, source.isOutgoing() && !source.isChannel(), shouldRenderSmall(), animated);
        clientReactions.add(i, newReaction);
        newReaction.update(messageReactions[i], i, false, shouldRenderSmall());
        newReaction.show(animated);
      }
    }

    for (Reaction existingReaction : existingHash.values()) {
      if (!emojiList.contains(existingReaction.reaction.reaction)) {
        // reaction is not existing anymore, remove it
        existingReaction.hide(animated, () -> clientReactions.remove(existingReaction));
      }
    }

    rcClickListeners = new RectF[clientReactions.size()];
    initializeClickArray();

    clientReactionsOrder = messageReactions;
    if (measure) {
      measure(messageReactions, -1, animated);
      componentVisibleAnimator.setValue(messageReactions.length > 0, animated);
    }
  }

  private void initializeClickArray () {
    for (int i = 0; i < rcClickListeners.length; i++) {
      rcClickListeners[i] = new RectF();
    }
  }

  public boolean needExtraYPadding () {
    return source.useBubbles() && !((source instanceof TGMessageMedia || source instanceof TGMessageVideo || source instanceof TGMessageSticker) && !source.isForward());
  }

  public boolean needExtraMissingPadding () {
    return !source.isForward() && source.useBubbles() && (source instanceof TGMessageMedia || source instanceof TGMessageVideo) && !shouldRenderUnderBubble();
  }

  public boolean needExtraMissingOffset () {
    return !source.isForward() && source.useBubbles() && (source instanceof TGMessageMedia || source instanceof TGMessageVideo || source instanceof TGMessageSticker) && shouldRenderUnderBubble();
  }

  private void measure (TdApi.MessageReaction[] order, int leftEdge, boolean animated) {
    if (order == null || order.length == 0) {
      if (animated) {
        rcWidthAnimator.animateTo(0);
        rcHeightAnimator.animateTo(0);
      } else {
        if (!rcWidthAnimator.isAnimating()) rcWidthAnimator.forceFactor(0);
        if (!rcHeightAnimator.isAnimating()) rcHeightAnimator.forceFactor(0);
      }

      measured = true;
      return;
    }

    HashMap<String, Reaction> existingHash = asMap();

    float leftBubbleEdge = leftEdge != -1 ? leftEdge : (source.useBubbles() ? source.getActualLeftContentEdge() : 0);
    float maxWidth = (!source.allowMessageHorizontalExtend()) ? source.getContentWidth() : TGMessage.getEstimatedContentMaxWidth();

    int width = 0;
    int currentX = 0;
    int currentY = (!shouldRenderSmall() && (!source.useBubbles() || needExtraYPadding())) ? REACTION_ITEM_HALF_SEPARATOR : 0;
    boolean needExtraHeight = false;

    for (int i = 0; i < order.length; i++) {
      Reaction reaction = existingHash.get(order[i].reaction);
      if (reaction == null) continue;

      boolean isLast = i == (order.length - 1);
      float predictWidth = leftBubbleEdge + currentX + reaction.getStaticWidth() + Screen.dp(10f);

      if ((predictWidth - leftBubbleEdge) >= maxWidth || predictWidth >= Screen.currentWidth()) {
        // too much space taken, move to next row
        width = Math.max(width, currentX - (shouldRenderSmall() ? REACTION_ITEM_SEPARATOR_SMALL : REACTION_ITEM_SEPARATOR));
        currentY += REACTION_HEIGHT + REACTION_ITEM_SEPARATOR;
        currentX = 0;
      }

      // needExtraHeight = source.useBubbles() && (source.getRealContentX() + currentX + reaction.getStaticWidth() - leftBubbleEdge) >= maxWidth - source.getBubbleTimePartWidth();
      reaction.setCoordinates(currentX, currentY, animated);
      currentX += (shouldRenderSmall() ? reaction.getSmallWidth() : reaction.getStaticWidth()) + (!isLast ? (shouldRenderSmall() ? REACTION_ITEM_SEPARATOR_SMALL : REACTION_ITEM_SEPARATOR) : 0);

      if (!shouldRenderUnderBubble() && source.useBubbles() && isLast) {
        // comparing if reactions are inside the time
        int rowWidth = currentX - (shouldRenderSmall() ? REACTION_ITEM_SEPARATOR_SMALL : REACTION_ITEM_SEPARATOR);
        int startTimePoint = source.getBubbleInnerWidth() - source.getBubbleTimePartWidth();
        needExtraHeight = startTimePoint <= rowWidth;
      }
    }

    int reactionsWidth = (width == 0 ? currentX : width);
    int reactionsHeight = (currentY == 0 ? REACTION_HEIGHT : currentY + REACTION_HEIGHT) + (needExtraHeight ? Screen.dp(12f) + (source.isSmallBubbleContentPadding() ? Screen.dp(8f) : 0) : 0) + (((needExtraMissingPadding() || source instanceof TGMessageSticker) && !needExtraHeight) ? Screen.dp(source instanceof TGMessageSticker ? 2f : 8f) : 0) + (shouldRenderUnderBubble() ? Screen.dp(needExtraMissingOffset() ? 4f : 2f) : 0);

    if (animated) {
      rcWidthAnimator.animateTo(reactionsWidth);
      rcHeightAnimator.animateTo(reactionsHeight);
    } else {
      if (!rcWidthAnimator.isAnimating()) rcWidthAnimator.forceFactor(reactionsWidth);
      if (!rcHeightAnimator.isAnimating()) rcHeightAnimator.forceFactor(reactionsHeight);
    }

    measured = true;
  }

  public int getHeight () {
    return containerHeight;
  }

  public int getWidth () {
    return containerWidth;
  }

  public int getCompensatedHeight (boolean compensate) {
    if (!compensate) return getHeight();
    return (int) (Math.max(0, rcHeightAnimator.getFactor() - Screen.dp(16f)) * getVisibility());
  }

  private float getVisibility () {
    return shouldRenderSmall() ? 1f : componentVisibleAnimator.getFloatValue();
  }

  public boolean shouldRenderUnderBubble () {
    if (shouldRenderSmall() || (source.isForward() && source.useBubbles())) return false;
    if (!source.useBubbles()) return true;

    if (source instanceof TGMessageMedia) {
      return ((TGMessageMedia) source).isEmptyCaption();
    } else {
      return source instanceof TGMessageSticker || source instanceof TGMessageVideo || source instanceof TGMessageLocation;
    }
  }

  public boolean shouldRenderSmall () {
    return source.messagesController().isUserChat();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CONTAINER_PARAMS_H:
        containerHeight = (int) (rcHeightAnimator.getFactor() * getVisibility());
        break;
      case ANIMATOR_CONTAINER_PARAMS_W:
        containerWidth = (int) (rcWidthAnimator.getFactor() * getVisibility());
        break;
      case ANIMATOR_VISIBLE:
        containerHeight = (int) (rcHeightAnimator.getFactor() * getVisibility());
        containerWidth = (int) (rcWidthAnimator.getFactor() * getVisibility());
        break;
    }

    checkLayoutParams((id == ANIMATOR_VISIBLE || id == ANIMATOR_CONTAINER_PARAMS_H) && shouldRenderUnderBubble());
  }

  private void checkLayoutParams (boolean shouldNotifyHeightChange) {
    int newWidth = getWidth();
    int newHeight = getHeight();

    if (newWidth != lastWidth || newHeight != lastHeight) {
      lastWidth = newWidth;
      lastHeight = newHeight;

      if (shouldNotifyHeightChange) {
        source.notifyMessageHeightChanged();
      } else {
        if (source.needViewGroup()) {
          source.invalidateParentOrSelf(false);
        } else {
          source.requestLayout();
        }
      }
    }
  }

  public void draw (MessageView view, Canvas c, int startX, int startY) {
    if (shouldRenderUnderBubble()) {
      startY += Screen.dp(needExtraMissingOffset() ? 4f : 2f);
    }

    rcRect.set(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    c.save();
    c.clipRect(rcRect);

    View cv = source.findCurrentView();
    int[] loc = cv != null ? Views.getLocationInWindow(source.findCurrentView()) : null;

    for (int i = 0; i < clientReactions.size(); i++) {
      Reaction reaction = clientReactions.get(i);

      float sx = startX + reaction.getStartXRelative();
      float sy = startY + reaction.getStartYRelative();
      rcClickListeners[i].set(sx, sy, sx + reaction.getStaticWidth(), sy + reaction.getHeight());

      reaction.draw(c, view.getReactionsReceiver(), startX, startY, loc != null ? loc[1] + (shouldRenderSmall() ? 0 : rcClickListeners[i].centerY()) : 0, clientReactions.size() == 1, shouldRenderSmall());
    }

    c.restore();
  }

  public boolean handleClick (float x, float y) {
    if (shouldRenderSmall() || rcClickListeners == null || rcClickListeners.length == 0) return false; // should be not handled

    for (int i = 0; i < rcClickListeners.length; i++) {
      if (rcClickListeners[i].contains(x, y) && clientReactions.size() > i) {
        source.messagesController().setMessageReaction(source.getOldestMessage(), clientReactions.get(i).reaction.reaction);
        UI.hapticVibrate(source.findCurrentView(), false);
        return true;
      }
    }

    return false;
  }

  public void measureLayout (int pRealContentMaxWidth) {
    if (realMaxWidth == pRealContentMaxWidth || clientReactionsOrder == null) return;
    realMaxWidth = pRealContentMaxWidth;
    measure(clientReactionsOrder, -1, false);
    componentVisibleAnimator.setValue(clientReactionsOrder.length > 0, false);
  }

  public void measureIfNot (int leftEdge) {
    if (measured) return;
    if (clientReactionsOrder == null) return;
    measure(clientReactionsOrder, leftEdge, false);
    componentVisibleAnimator.setValue(clientReactionsOrder.length > 0, false);
  }

  public void animateUnread (TdApi.UnreadReaction[] unreadReactions) {
    HashMap<String, Reaction> map = asMap();

    for (TdApi.UnreadReaction unread : unreadReactions) {
      Reaction mapped = map.get(unread.reaction);
      if (mapped != null) {
        mapped.animate();
      }
    }
  }

  private static class Reaction implements FactorAnimator.Target {
    private final TdApi.Reaction reactionObj;
    private final boolean isOutgoing;
    private final Counter textCounter;
    private final TGMessage source;

    private final ImageFile staticIconFile;
    private GifFile dynamicIconFile;

    private final Path staticIconContour = new Path();

    private static final int ANIMATOR_VISIBLE = 3;
    private static final int ANIMATOR_CHOOSE = 4;
    private static final int ANIMATOR_COORDINATE = 5;
    private static final int ANIMATOR_TEXT_VISIBLE = 6;
    private final BoolAnimator appearAnimator = new BoolAnimator(ANIMATOR_VISIBLE, this, RC_INTERPOLATOR, RC_DURATION);
    private final BoolAnimator chooseAnimator = new BoolAnimator(ANIMATOR_CHOOSE, this, RC_INTERPOLATOR, RC_DURATION);
    private final FactorAnimator xCoordinate = new FactorAnimator(ANIMATOR_COORDINATE, this, RC_INTERPOLATOR, RC_DURATION);
    private final FactorAnimator yCoordinate = new FactorAnimator(ANIMATOR_COORDINATE, this, RC_INTERPOLATOR, RC_DURATION);
    private final BoolAnimator counterAppearAnimator = new BoolAnimator(ANIMATOR_TEXT_VISIBLE, this, RC_INTERPOLATOR, RC_DURATION);

    private final RectF bubbleRect = new RectF();
    private final Path bubblePath = new Path();

    private TdApi.MessageReaction reaction;
    private Runnable onHideAnimationEnd;
    private float bubblePathWidth;

    private boolean drawAnimated;
    private boolean isPlayingNow;

    public Reaction (Tdlib tdlib, TGMessage source, TdApi.MessageReaction reaction, boolean isOutgoing, boolean small, boolean animate) {
      this.reaction = reaction;
      this.reactionObj = tdlib.getReaction(reaction.reaction);
      this.isOutgoing = isOutgoing;
      this.source = source;

      if (reactionObj != null) {
        Td.buildOutline(reactionObj.staticIcon.outline, (float) (small ? REACTION_ICON_SIZE_SMALL : REACTION_ICON_SIZE) / reactionObj.staticIcon.height, staticIconContour);
      }

      staticIconFile = new ImageFile(tdlib, reactionObj != null ? reactionObj.staticIcon.sticker : null);
      staticIconFile.setSize((small ? REACTION_ICON_SIZE_SMALL : REACTION_ICON_SIZE) * 4);
      staticIconFile.setNoBlur();

      if (reactionObj != null && reactionObj.centerAnimation != null) {
        dynamicIconFile = new GifFile(tdlib, reactionObj.centerAnimation);
        dynamicIconFile.setUnique(true);
        dynamicIconFile.setPlayOnce(true);
        dynamicIconFile.setFrameChangeListener((file, frameNo, frameDelta) -> {
          isPlayingNow = frameNo > 4;
          if (source.needViewGroup()) source.invalidateParentOrSelf(false);
        });
      }

      chooseAnimator.setValue(reaction.isChosen, false);

      textCounter = new Counter.Builder()
        .noBackground()
        .allBold(false)
        .textSize(small ? 10f : 12f)
        .callback((counter, sizeChanged) -> source.invalidateParentOrSelf(false))
        .colorSet(this::getTextColor)
        .build();

      textCounter.setCount(reaction.totalCount, false);
      if (animate && (small || reaction.isChosen)) {
        animate();
      }
    }

    public void setCoordinates (float x, float y, boolean animated) {
      UI.post(() -> {
        if (animated && !appearAnimator.isAnimating()) {
          xCoordinate.animateTo(x);
          yCoordinate.animateTo(y);
        } else {
          xCoordinate.forceFactor(x);
          yCoordinate.forceFactor(y);
        }
      });
    }

    public void show (boolean animated) {
      UI.post(() -> appearAnimator.setValue(true, animated));
    }

    public void hide (boolean animated, @Nullable Runnable onEnd) {
      UI.post(() -> {
        onHideAnimationEnd = onEnd;
        appearAnimator.setValue(false, animated);
        source.messagesController().updateReactionOverlayAlpha(createKey(), false);
      });
    }

    public int getBackgroundColor () {
      return isOutgoing ? ColorUtils.fromToArgb(
        ColorUtils.alphaColor(0.15f, Theme.getColor(R.id.theme_color_bubbleOut_time)),
        Theme.getColor(R.id.theme_color_bubbleOut_file),
        chooseAnimator.getFloatValue()
      ) : ColorUtils.fromToArgb(
        ColorUtils.alphaColor(0.15f, Theme.getColor(R.id.theme_color_bubbleIn_time)),
        Theme.getColor(R.id.theme_color_file),
        chooseAnimator.getFloatValue()
      );
    }

    public int getTextColor () {
      return isOutgoing ? ColorUtils.fromToArgb(
        Theme.getColor(R.id.theme_color_bubbleOut_text),
        Color.WHITE,
        chooseAnimator.getFloatValue()
      ) : ColorUtils.fromToArgb(
        Theme.getColor(R.id.theme_color_bubbleIn_text),
        Color.WHITE,
        chooseAnimator.getFloatValue()
      );
    }

    public int getChosenColor () {
      return Theme.getColor(isOutgoing ? R.id.theme_color_bubbleOut_file : R.id.theme_color_file);
    }

    public float getHeight () {
      return REACTION_HEIGHT;
    }

    public float getWidth () {
      return REACTION_BASE_WIDTH + textCounter.getScaledWidth(0) + Screen.dp(12f);
    }

    public float getStaticWidth () {
      return REACTION_BASE_WIDTH + Paints.getRegularTextPaint(12f).measureText(String.valueOf(reaction.totalCount)) + Screen.dp(12f);
    }

    public float getSmallWidth () {
      return REACTION_ICON_SIZE_SMALL + REACTION_ITEM_HALF_SEPARATOR + (reaction.totalCount == 1 ? 0 : Screen.dp(4f) + Paints.getRegularTextPaint(10f).measureText(String.valueOf(reaction.totalCount)));
    }

    public float getStartXRelative () {
      return xCoordinate.getToFactor();
    }

    public float getStartYRelative () {
      return yCoordinate.getToFactor();
    }

    public boolean isChosen () {
      return reaction.isChosen;
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      source.invalidateParentOrSelf(false);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == ANIMATOR_VISIBLE && finalFactor == 0f && onHideAnimationEnd != null) {
        onHideAnimationEnd.run();
        onHideAnimationEnd = null;
      }
    }

    public void update (TdApi.MessageReaction reaction, int index, boolean animated, boolean small) {
      if (((reaction.isChosen && !this.reaction.isChosen) || small) && animated) {
        animate();
      }

      if (small) {
        counterAppearAnimator.setValue(reaction.totalCount > 1, animated);
        if (reaction.totalCount > 1) {
          textCounter.setCount(reaction.totalCount, animated);
        } else {
          textCounter.hide(animated);
        }
      } else if (this.reaction.totalCount != reaction.totalCount) {
        textCounter.setCount(reaction.totalCount, animated);
      }

      this.reaction = reaction;
      chooseAnimator.setValue(reaction.isChosen, animated);

      if (animated) {
        source.messagesController().updateReactionOverlayAlpha(createKey(), reaction.isChosen);
      }

      setCoordinates((REACTION_BASE_WIDTH * 4) * index, 0, animated);
    }

    private String createKey () {
      return source.getChatId() + "_" + source.getId() + "_" + reaction.reaction;
    }

    private void animate () {
      if (dynamicIconFile != null) {
        source.performWithReactionsReceiver(reactionsReceiver -> reactionsReceiver.getGifReceiver(getKey()).setOnGifLoadedListener(() -> createOverlay(() -> {
          drawAnimated = true;
          reactionsReceiver.getGifReceiver(getKey()).requestFile(dynamicIconFile);
          dynamicIconFile.setLooped(false);
          source.invalidate();
        })));
      }
    }

    private int getKey () {
      return createKey().hashCode();
    }

    private void createOverlay (@Nullable Runnable onFirstFrameListener) {
      source.messagesController().addReactionToOverlay(createKey(), reactionObj, onFirstFrameListener);
    }

    public void draw (Canvas c, ComplexReceiver reactionsReceiver, int sx, int sy, float vy, boolean isSingle, boolean isSmall) {
      if (isSmall) {
        drawSmall(c, reactionsReceiver, sx, sy, vy);
      } else {
        drawLarge(c, reactionsReceiver, sx, sy, isSingle, vy);
      }
    }

    public void drawSmall (Canvas c, ComplexReceiver reactionsReceiver, int sx, int sy, float vy) {
      final float alpha = appearAnimator.getFloatValue();
      final float width = getSmallWidth();
      final float height = Screen.dp(16f);
      final float animationScale = MathUtils.fromTo(0.5f, 1f, alpha);

      final int startX = (int) (sx + xCoordinate.getFactor());
      final int startY = (int) (sy + yCoordinate.getFactor());
      final int vertPad = (int) (height - REACTION_ICON_SIZE_SMALL) / 2;
      final int tcWidth = (int) textCounter.getScaledWidth(Screen.dp(4f));

      c.save();
      c.translate(startX, startY);
      c.scale(animationScale, animationScale, width / 2, height / 2);

      GifReceiver gr = reactionsReceiver.getGifReceiver(getKey());
      ImageReceiver r = reactionsReceiver.getImageReceiver(getKey());

      r.setBounds(tcWidth, vertPad, REACTION_ICON_SIZE_SMALL + tcWidth, (int) (height - vertPad));
      if (r.isEmpty()) r.requestFile(staticIconFile);
      if (r.needPlaceholder()) r.drawPlaceholderContour(c, staticIconContour, alpha);
      if (gr.isEmpty() || !isPlayingNow) {
        r.setPaintAlpha(alpha);
        r.draw(c);
        r.restorePaintAlpha();
      }

      if (dynamicIconFile != null) {
        int grCx = r.centerX();
        int grCy = r.centerY();
        int grPad = Screen.dp(12f);
        if (gr.isEmpty()) gr.requestFile(dynamicIconFile);
        if (drawAnimated) {
          gr.setBounds(grCx - grPad, grCy - grPad, grCx + grPad, grCy + grPad);
          gr.setPaintAlpha(alpha);
          gr.draw(c);
          gr.restorePaintAlpha();
        }
      }

      textCounter.draw(c, 0, r.centerY(), Gravity.LEFT, 1f);
      c.restore();

      if (vy > 0) {
        source.messagesController().updateReactionOverlayLocation(createKey(), startX + r.centerX() + source.getTranslation(), vy + startY + r.centerY() - (Screen.getStatusBarHeight() + HeaderView.getHeaderHeight(null)), true);
      }
    }

    public boolean hasAnimationEnded () {
      return dynamicIconFile != null && dynamicIconFile.isPlayOnce() && dynamicIconFile.hasLooped() && !dynamicIconFile.needDecodeLastFrame();
    }

    public void drawLarge (Canvas c, ComplexReceiver reactionsReceiver, int sx, int sy, boolean isSingle, float vy) {
      final boolean clipped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

      final float width = getWidth();
      final float height = getHeight();
      final float alpha = appearAnimator.getFloatValue();
      final float animationDelta = (Screen.dp(50f) * (1f - alpha));
      final float animationScale = MathUtils.fromTo(0.5f, 1f, alpha);

      final int startX = (int) (sx + xCoordinate.getFactor());
      final int startY = (int) (sy + yCoordinate.getFactor() + (isSingle ? animationDelta : 0));

      c.save();
      c.translate(startX, startY);
      c.scale(animationScale, animationScale, width / 2, height / 2);

      if (bubblePathWidth != width) {
        bubblePathWidth = width;
        bubbleRect.set(0, 0, width, height);
        bubblePath.reset();
        DrawAlgorithms.buildPath(bubblePath, bubbleRect, REACTION_RADIUS, REACTION_RADIUS, REACTION_RADIUS, REACTION_RADIUS);
      }

      int saveCount;
      if (clipped) {
        saveCount = ViewSupport.clipPath(c, bubblePath);
      } else {
        c.save();
        c.clipRect(bubbleRect);
        saveCount = Integer.MIN_VALUE;
      }

      int bgColor = getBackgroundColor();
      Paint rectPaint = Paints.fillingPaint(bgColor);
      rectPaint.setAlpha((int) (Color.alpha(bgColor) * alpha));
      c.drawRoundRect(bubbleRect, REACTION_RADIUS, REACTION_RADIUS, rectPaint);

      if (clipped && chooseAnimator.isAnimating())
        c.drawCircle(Screen.dp(16f), (height / 2f), width * chooseAnimator.getFloatValue(), Paints.fillingPaint(getChosenColor()));

      int izPad = (REACTION_HEIGHT - REACTION_ICON_SIZE) / 2;

      // Static
      GifReceiver gr = reactionsReceiver.getGifReceiver(getKey());
      ImageReceiver r = reactionsReceiver.getImageReceiver(getKey());
      r.setBounds(Screen.dp(8f), izPad, Screen.dp(8f) + REACTION_ICON_SIZE, REACTION_HEIGHT - izPad);
      if (r.isEmpty()) r.requestFile(staticIconFile);
      if (r.needPlaceholder()) r.drawPlaceholderContour(c, staticIconContour, alpha);
      if (gr.isEmpty() || !isPlayingNow) {
        r.setPaintAlpha(alpha);
        r.draw(c);
        r.restorePaintAlpha();
      }

      // Dynamic
      if (dynamicIconFile != null) {
        int grCx = r.centerX();
        int grCy = r.centerY();
        int grPad = Screen.dp(16f);
        if (gr.isEmpty()) gr.requestFile(dynamicIconFile);
        if (drawAnimated) {
          gr.setBounds(grCx - grPad, grCy - grPad, grCx + grPad, grCy + grPad);
          gr.setPaintAlpha(alpha);
          gr.draw(c);
          gr.restorePaintAlpha();
        }
      }

      textCounter.draw(c, Screen.dp(8f) + REACTION_ICON_SIZE + Screen.dp(6f), r.centerY(), Gravity.LEFT, alpha);

      if (clipped) {
        ViewSupport.restoreClipPath(c, saveCount);
      } else {
        c.restore();
      }

      c.restore();

      if (vy > 0) {
        source.messagesController().updateReactionOverlayLocation(createKey(), startX + Screen.dp(16) + source.getTranslation(), vy - (Screen.getStatusBarHeight() + HeaderView.getHeaderHeight(null)), false);
      }
    }
  }
}
