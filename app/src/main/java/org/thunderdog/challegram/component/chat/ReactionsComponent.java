package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.HashMap;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

public class ReactionsComponent implements FactorAnimator.Target {
  // Sizes
  private static final int REACTION_ROW_HEIGHT = Screen.dp(48f);
  private static final int REACTION_HEIGHT = Screen.dp(28f);
  private static final int REACTION_ITEM_SEPARATOR = Screen.dp(8f);
  private static final int REACTION_ITEM_HALF_SEPARATOR = Screen.dp(4f);
  private static final int REACTION_RADIUS = Screen.dp(32f);
  private static final int REACTION_CONTAINER_DELTA = Screen.dp(6f);
  private static final int REACTION_ICON_SIZE = Screen.dp(16f);
  private static final int REACTION_BASE_WIDTH = REACTION_ICON_SIZE + Screen.dp(12f);
  // Animation IDs
  private static final int ANIMATOR_VISIBLE = 0;
  private static final int ANIMATOR_CHOOSE = 1;
  private static final int ANIMATOR_COORDINATE = 2;

  private final TGMessage source;

  private ViewProvider viewProvider;
  private TdApi.MessageReaction[] reactions;
  private ArrayList<Reaction> clientReactions = new ArrayList<>();

  private int reactionsWidth;
  private int reactionsHeight;
  private RectF rcRect = new RectF();

  private final BoolAnimator componentVisibleAnimator = new BoolAnimator(ANIMATOR_VISIBLE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);

  public ReactionsComponent (TGMessage source, ViewProvider viewProvider) {
    this.source = source;
    this.viewProvider = viewProvider;
    update(source.getMessage().interactionInfo != null ? source.getMessage().interactionInfo.reactions : new TdApi.MessageReaction[0], false);
  }

  public void update (TdApi.MessageReaction[] messageReactions, boolean animated) {
    if (clientReactions.isEmpty() && messageReactions.length == 0) return;
    this.reactions = messageReactions;

    ArrayList<String> emojiList = new ArrayList<>();
    HashMap<String, Reaction> existingHash = new HashMap<>();

    for (int i = 0; i < clientReactions.size(); i++) {
      Reaction existingReaction = clientReactions.get(i);
      existingHash.put(existingReaction.reaction.reaction, existingReaction);
    }

    for (int i = 0; i < messageReactions.length; i++) {
      TdApi.MessageReaction reaction = messageReactions[i];
      emojiList.add(reaction.reaction);

      if (existingHash.containsKey(reaction.reaction)) {
        // reaction exists, update it
        existingHash.get(reaction.reaction).update(messageReactions[i], i, animated);
      } else {
        // reaction does not exist, add it
        // note that previous index is already added, so we can be assured in list changes
        Reaction newReaction = new Reaction(source.tdlib(), reaction, viewProvider, source.isOutgoing() && !source.isChannel());
        clientReactions.add(i, newReaction);
        newReaction.update(messageReactions[i], i, false);
        newReaction.show(true);
      }
    }

    for (Reaction existingReaction : existingHash.values()) {
      if (!emojiList.contains(existingReaction.reaction.reaction)) {
        // reaction is not existing anymore, remove it
        existingReaction.hide(animated, () -> clientReactions.remove(existingReaction));
      }
    }

    measure(messageReactions, animated);
    componentVisibleAnimator.setValue(reactions.length > 0, animated);
  }

  private void measure (TdApi.MessageReaction[] order, boolean animated) {
    HashMap<String, Reaction> existingHash = new HashMap<>();

    for (int i = 0; i < clientReactions.size(); i++) {
      Reaction existingReaction = clientReactions.get(i);
      existingHash.put(existingReaction.reaction.reaction, existingReaction);
    }

    int width = 0;
    int currentX = 0;
    int currentY = 0;

    for (int i = 0; i < order.length; i++) {
      Reaction reaction = existingHash.get(order[i].reaction);

      if (currentX + reaction.getStaticWidth() >= TGMessage.getEstimatedContentMaxWidth()) {
        // too much space taken, move to next row
        width = currentX;
        currentY += REACTION_HEIGHT + REACTION_ITEM_SEPARATOR;
        currentX = 0;
      }

      reaction.setCoordinates(currentX, currentY, animated);
      currentX += reaction.getStaticWidth() + ((i != order.length - 1) ? REACTION_ITEM_SEPARATOR : 0);
    }

    Log.e("RC Measure %s / %s / %s / %s", source.getSmallestMaxContentWidth(), TGMessage.getEstimatedContentMaxWidth(), source.getRealContentMaxWidth(), currentX);
    // TODO: Multiline

    reactionsWidth = width == 0 ? currentX : width;
    reactionsHeight = currentY == 0 ? REACTION_HEIGHT : currentY + REACTION_HEIGHT;
  }

  public int getHeight () {
    return (int) (((reactionsHeight - (source.isChannel() ? REACTION_CONTAINER_DELTA : 0)) + Screen.dp(4f)) * componentVisibleAnimator.getFloatValue());
  }

  public int getFlatHeight () {
    return (int) ((reactionsHeight + REACTION_ITEM_HALF_SEPARATOR) * componentVisibleAnimator.getFloatValue());
  }

  public int getWidth () {
    return (int) (reactionsWidth * componentVisibleAnimator.getFloatValue());
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    viewProvider.invalidate();
  }

  public void draw (MessageView view, Canvas c, int startX, int startY) {
    rcRect.set(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight() - Screen.dp(source.needExtraPadding() ? 10f : 3f));
    c.save();
    c.clipRect(rcRect);
    for (int i = 0; i < clientReactions.size(); i++) {
      clientReactions.get(i).draw(c, view.getReactionsReceiver(), startX, startY, clientReactions.size() == 1);
    }
    c.restore();
  }

  private static class Reaction implements FactorAnimator.Target {
    private final TdApi.Reaction reactionObj;
    private final boolean isOutgoing;
    private final ViewProvider viewProvider;
    private final Counter textCounter;

    private final ImageFile staticIconFile;
    private final Path staticIconContour = new Path();

    private TdApi.MessageReaction reaction;

    private final BoolAnimator appearAnimator = new BoolAnimator(ANIMATOR_VISIBLE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);
    private final BoolAnimator chooseAnimator = new BoolAnimator(ANIMATOR_CHOOSE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);
    private final FactorAnimator xCoordinate = new FactorAnimator(ANIMATOR_COORDINATE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);
    private final FactorAnimator yCoordinate = new FactorAnimator(ANIMATOR_COORDINATE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);

    private Runnable onHideAnimationEnd;

    private final RectF bubbleRect = new RectF();
    private final Path bubblePath = new Path();
    private float bubblePathWidth;

    public Reaction (Tdlib tdlib, TdApi.MessageReaction reaction, ViewProvider viewProvider, boolean isOutgoing) {
      this.reaction = reaction;
      this.reactionObj = tdlib.getReaction(reaction.reaction);
      this.isOutgoing = isOutgoing;
      this.viewProvider = viewProvider;

      Td.buildOutline(reactionObj.staticIcon.outline, (float) REACTION_ICON_SIZE / reactionObj.staticIcon.height, staticIconContour);
      staticIconFile = new ImageFile(tdlib, reactionObj.staticIcon.sticker);
      //staticIconFile.setScaleType(ImageFile.FIT_CENTER);
      staticIconFile.setSize(REACTION_ICON_SIZE);
      staticIconFile.setNoBlur();
      chooseAnimator.setValue(reaction.isChosen, false);

      textCounter = new Counter.Builder()
        .noBackground()
        .allBold(false)
        .textSize(12f)
        .callback((counter, sizeChanged) -> viewProvider.invalidate())
        .colorSet(this::getTextColor)
        .build();

      textCounter.setCount(reaction.totalCount, false);
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
      });
    }

    public int getBackgroundColor () {
      if (chooseAnimator.getFloatValue() == 1f) return getChosenColor();
      return ColorUtils.alphaColor(0.15f, getChosenColor());
    }

    public int getTextColor () {
      return isOutgoing ? ColorUtils.fromToArgb(
        Theme.getColor(R.id.theme_color_bubbleOut_text), Color.WHITE, chooseAnimator.getFloatValue()
      ) : ColorUtils.fromToArgb(
        Theme.getColor(R.id.theme_color_fillingPositive), Color.WHITE, chooseAnimator.getFloatValue()
      );
    }

    public int getChosenColor () {
      return Theme.getColor(R.id.theme_color_fillingPositive);
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

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      viewProvider.invalidate();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == ANIMATOR_VISIBLE && finalFactor == 0f && onHideAnimationEnd != null) {
        onHideAnimationEnd.run();
        onHideAnimationEnd = null;
      }
    }

    public void update (TdApi.MessageReaction reaction, int index, boolean animated) {
      if (this.reaction.totalCount != reaction.totalCount) textCounter.setCount(reaction.totalCount, animated);
      this.reaction = reaction;
      chooseAnimator.setValue(reaction.isChosen, animated);
      setCoordinates((REACTION_BASE_WIDTH * 4) * index, 0, animated);
    }

    public void draw (Canvas c, ComplexReceiver reactionsReceiver, int sx, int sy, boolean isSingle) {
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

      ImageReceiver r = reactionsReceiver.getImageReceiver(reaction.reaction.hashCode());

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

      if (clipped && chooseAnimator.isAnimating()) c.drawCircle(Screen.dp(16f), (height / 2f), width * chooseAnimator.getFloatValue(), Paints.fillingPaint(getChosenColor()));

      int izPad = (REACTION_HEIGHT - REACTION_ICON_SIZE) / 2;
      r.setBounds(Screen.dp(8f), izPad, Screen.dp(8f) + REACTION_ICON_SIZE, REACTION_HEIGHT - izPad);

      if (r.isEmpty()) {
        r.requestFile(staticIconFile);
      }

      r.setAlpha(alpha);
      if (r.needPlaceholder()) {
        r.drawPlaceholderContour(c, staticIconContour, alpha);
      } else {
        r.draw(c);
      }
      r.setAlpha(1f);

      textCounter.draw(c, r.getRight() + Screen.dp(6f), r.centerY(), Gravity.LEFT, alpha);

      if (clipped) {
        ViewSupport.restoreClipPath(c, saveCount);
      } else {
        c.restore();
      }

      c.restore();
    }
  }
}
