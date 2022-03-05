package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;

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
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.td.Td;

public class ReactionsComponent implements FactorAnimator.Target {
  // Sizes
  private static final int REACTION_ROW_HEIGHT = Screen.dp(36f);
  private static final int REACTION_HEIGHT = Screen.dp(28f);
  private static final int REACTION_ITEM_SEPARATOR = Screen.dp(8f);
  private static final int REACTION_RADIUS = Screen.dp(32f);
  private static final int REACTION_CONTAINER_DELTA = Screen.dp(6f);
  private static final int REACTION_ICON_SIZE = Screen.dp(8f);
  private static final int REACTION_BASE_WIDTH = REACTION_ICON_SIZE + Screen.dp(12f);
  // Animation IDs
  private static final int ANIMATOR_VISIBLE = 0;
  private static final int ANIMATOR_CHOOSE = 1;

  // Diff
  private final DiffUtil.ItemCallback<Reaction> reactionDiffer = new DiffUtil.ItemCallback<>() {
    @Override
    public boolean areItemsTheSame (@NonNull Reaction oldItem, @NonNull Reaction newItem) {
      return oldItem.reaction.reaction.equals(newItem.reaction.reaction);
    }

    @Override
    public boolean areContentsTheSame (@NonNull Reaction oldItem, @NonNull Reaction newItem) {
      return Td.equalsTo(oldItem.reaction, newItem.reaction);
    }
  };

  private final TGMessage source;

  private ViewProvider viewProvider;

  private TdApi.MessageReaction[] reactions;
  private ArrayList<Reaction> clientReactions = new ArrayList<>();

  private final BoolAnimator componentVisibleAnimator = new BoolAnimator(ANIMATOR_VISIBLE, this, AnimatorUtils.SLOW_DECELERATE_INTERPOLATOR, 230l);

  public ReactionsComponent (TGMessage source, ViewProvider viewProvider) {
    this.source = source;
    this.viewProvider = viewProvider;
    update(source.getMessage().interactionInfo != null ? source.getMessage().interactionInfo.reactions : new TdApi.MessageReaction[0], false);
  }

  public void update (TdApi.MessageReaction[] messageReactions, boolean animated) {
    this.reactions = messageReactions;

    /**
     * Animations:
     * - 0 -> 1
     * - 1 -> 0
     * - X -> Y
     * - Add/Remove secondary reactions
     * - Sort?
     */

    ArrayList<Reaction> newReactions = new ArrayList<>();
    for (int i = 0; i < messageReactions.length; i++) {
      newReactions.add(i, new Reaction(source.tdlib(), messageReactions[i], viewProvider, source.isOutgoing() && !source.isChannel()));
    }

    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override
      public int getOldListSize () {
        return clientReactions.size();
      }

      @Override
      public int getNewListSize () {
        return newReactions.size();
      }

      @Override
      public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
        return reactionDiffer.areItemsTheSame(clientReactions.get(oldItemPosition), newReactions.get(newItemPosition));
      }

      @Override
      public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
        return reactionDiffer.areContentsTheSame(clientReactions.get(oldItemPosition), newReactions.get(newItemPosition));
      }
    });

    diff.dispatchUpdatesTo(new ListUpdateCallback() {
      @Override
      public void onInserted (int position, int count) {
        // Inserted - show bubble
        Log.e("RC onInserted %s %s", position, count);
        clientReactions.addAll(position, newReactions.subList(position, position + count));
        for (int i = position; i < position + count; i++) {
          clientReactions.get(i).show(animated);
        }
      }

      @Override
      public void onRemoved (int position, int count) {
        // Removed - hide bubble
        Log.e("RC onRemoved %s %s", position, count);
        //clientReactions.addAll(position, newReactions.subList(position, position + count));
        for (int i = position; i < position + count; i++) {
          int finalI = i;
          clientReactions.get(i).hide(animated, () -> clientReactions.remove(finalI));
        }
      }

      @Override
      public void onMoved (int fromPosition, int toPosition) {
        Log.e("RC onMoved %s %s", fromPosition, fromPosition);
      }

      @Override
      public void onChanged (int position, int count, @Nullable Object payload) {
        // Changed - update content
        Log.e("RC onChanged %s %s", position, count);
        for (int i = position; i < position + count; i++) {
          clientReactions.get(i).update(newReactions.get(position).reaction, animated);
        }
      }
    });

    componentVisibleAnimator.setValue(reactions.length > 0, animated);
  }

  public int getHeight () {
    return (int) ((REACTION_ROW_HEIGHT - REACTION_CONTAINER_DELTA) * componentVisibleAnimator.getFloatValue());
  }

  public int getWidth () {
    int totalWidth = 0;

    for (int i = 0; i < clientReactions.size(); i++) {
      totalWidth += clientReactions.get(i).getWidth(i != 0) + REACTION_ITEM_SEPARATOR;
    }

    return (int) (totalWidth * componentVisibleAnimator.getFloatValue());
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    viewProvider.invalidate();
  }

  public void draw (MessageView view, Canvas c, int startX, int startY) {
    for (int i = 0; i < clientReactions.size(); i++) {
      Reaction reaction = clientReactions.get(i);
      reaction.draw(c, view.getReactionsReceiver(), startX, startY);
      startX += reaction.getWidth(true) + REACTION_ITEM_SEPARATOR;
    }
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

    private Runnable onHideAnimationEnd;

    private final RectF bubbleRect = new RectF();
    private final Path bubblePath = new Path();
    private float bubblePathWidth;
    private int bubbleStartX, bubbleStartY;

    public Reaction (Tdlib tdlib, TdApi.MessageReaction reaction, ViewProvider viewProvider, boolean isOutgoing) {
      this.reaction = reaction;
      this.reactionObj = tdlib.getReaction(reaction.reaction);
      this.isOutgoing = isOutgoing;
      this.viewProvider = viewProvider;

      Td.buildOutline(reactionObj.staticIcon.outline, REACTION_ICON_SIZE, staticIconContour);
      staticIconFile = new ImageFile(tdlib, reactionObj.staticIcon.sticker);
      staticIconFile.setScaleType(ImageFile.FIT_CENTER);
      staticIconFile.setSize(REACTION_ICON_SIZE * 8);
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

    public void show (boolean animated) {
      appearAnimator.setValue(true, animated);
    }

    public void hide (boolean animated, @Nullable Runnable onEnd) {
      onHideAnimationEnd = onEnd;
      appearAnimator.setValue(false, animated);
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

    public float getHeight (boolean animated) {
      return REACTION_HEIGHT * appearAnimator.getFloatValue();
    }

    public float getWidth (boolean animated) {
      float bubbleWidth = REACTION_BASE_WIDTH + textCounter.getScaledWidth(0) + Screen.dp(18f);
      return bubbleWidth * (animated ? appearAnimator.getFloatValue() : 1f);
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

    public void update (TdApi.MessageReaction reaction, boolean animated) {
      this.reaction = reaction;
      chooseAnimator.setValue(reaction.isChosen, animated);
      textCounter.setCount(reaction.totalCount, animated);
    }

    public void draw (Canvas c, ComplexReceiver reactionsReceiver, int startX, int startY) {
      final boolean clipped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && chooseAnimator.isAnimating();
      final float width = getWidth(true); // false for indexes starting from 1 TODO
      final float height = getHeight(true); // false for indexes starting from 1 TODO

      ImageReceiver r = reactionsReceiver.getImageReceiver(reaction.reaction.hashCode());

      if (bubblePathWidth != width || bubbleStartX != startX || bubbleStartY != startY) {
        bubbleStartX = startX;
        bubbleStartY = startY;
        bubblePath.reset();
        bubblePathWidth = width;
        bubbleRect.set(startX, startY, startX + width, startY + height);
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

      c.drawRoundRect(bubbleRect, REACTION_RADIUS, REACTION_RADIUS, Paints.fillingPaint(getBackgroundColor()));
      if (clipped) c.drawCircle(startX + Screen.dp(19f), startY + (height / 2f), width * chooseAnimator.getFloatValue(), Paints.fillingPaint(getChosenColor()));

      r.setBounds(startX + Screen.dp(8f), startY, startX + Screen.dp(16f) + REACTION_ICON_SIZE, startY + REACTION_HEIGHT);

      if (r.isEmpty()) {
        r.requestFile(staticIconFile);
      }

      if (r.needPlaceholder()) {
        r.drawPlaceholderContour(c, staticIconContour);
      } else {
        r.draw(c);
      }

      textCounter.draw(c, r.getRight() + Screen.dp(4f), r.centerY(), Gravity.LEFT, 1f);

      if (clipped) {
        ViewSupport.restoreClipPath(c, saveCount);
      } else {
        c.restore();
      }
    }
  }
}
