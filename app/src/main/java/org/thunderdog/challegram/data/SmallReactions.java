package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.Gravity;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.util.ObjectsCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.ReactionsReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;

public final class SmallReactions {
  @Dimension(unit = Dimension.DP)
  private static final float ITEM_SIZE = 14f;
  @Dimension(unit = Dimension.DP)
  private static final float ITEM_PADDING = 1f;
  @Dimension(unit = Dimension.DP)
  private static final float ITEM_LIST_MARGIN_LEFT = 0f;
  @Dimension(unit = Dimension.DP)
  private static final float ITEM_LIST_MARGIN_RIGHT = 4f;
  @Dimension(unit = Dimension.DP)
  private static final float COUNTER_MARGIN_LEFT = -1f; // yep, it's negative
  @Dimension(unit = Dimension.DP)
  private static final float COUNTER_MARGIN_RIGHT = 6f;

  private static final int MAX_ITEM_COUNT = 3;

  @Px
  private final int itemSize, itemPadding, itemListMarginLeft, itemListMarginRight, counterMarginLeft, counterMarginRight;

  @Nullable
  private final Counter counter;
  @NonNull
  private final ListAnimator<Item> itemListAnimator;
  private final boolean isUserChat;

  public SmallReactions (@Nullable Counter counter, boolean isUserChat, @NonNull Runnable callback) {
    this.counter = counter;
    this.isUserChat = isUserChat;

    this.itemSize = Screen.dp(ITEM_SIZE);
    this.itemPadding = Screen.dp(ITEM_PADDING);

    this.itemListAnimator = new ListAnimator<>(animator -> callback.run(), AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    this.itemListMarginLeft = Screen.dp(ITEM_LIST_MARGIN_LEFT);
    this.itemListMarginRight = Screen.dp(ITEM_LIST_MARGIN_RIGHT);

    this.counterMarginLeft = Screen.dp(COUNTER_MARGIN_LEFT);
    this.counterMarginRight = Screen.dp(COUNTER_MARGIN_RIGHT);
  }

  public void setMessageReactions (@Nullable TdApi.MessageReaction[] messageReactions, boolean animated) {
    if (counter != null) {
      int totalCount = TD.getTotalReactionCount(messageReactions);
      if (totalCount > 1) { // because 1 > 0
        boolean muted = TD.hasChosenReaction(messageReactions);
        counter.setCount(totalCount, muted, animated);
      } else {
        counter.setCount(0, false, animated);
      }
    }

    if (messageReactions != null && messageReactions.length > 0) {
      int maxItemCount = messageReactions.length + (isUserChat ? 1 : 0);
      List<Item> itemList = new ArrayList<>(Math.min(maxItemCount, MAX_ITEM_COUNT));
      for (TdApi.MessageReaction messageReaction : messageReactions) {
        if (itemList.size() == MAX_ITEM_COUNT) {
          break;
        }
        itemList.add(new Item(itemSize, messageReaction.reaction));
        if (isUserChat && messageReaction.isChosen && messageReaction.totalCount > 1 && itemList.size() < MAX_ITEM_COUNT) {
          itemList.add(new Item(itemSize, null));
        }
      }
      int maxWidth = itemList.size() * itemSize;
      itemListAnimator.reset(itemList, animated, null, maxWidth);
    } else {
      itemListAnimator.reset(null, animated);
    }
  }

  @Px
  public int getWidth () {
    float counterWidth;
    if (counter != null) {
      counterWidth = counter.getScaledWidth(counterMarginLeft + counterMarginRight);
    } else {
      counterWidth = 0f;
    }
    float itemListWidth = itemListAnimator.getMetadata().getTotalWidth();
    if (itemListWidth > 0f) {
      itemListWidth += itemListAnimator.getMetadata().getVisibility() * (itemListMarginLeft + itemListMarginRight);
    }
    return Math.round(counterWidth + itemListWidth);
  }

  public void draw (@NonNull Canvas canvas, @Px int x, @Px int y, ReactionsReceiver reactionsReceiver) {
    float halfItemSize = itemSize / 2f;
    x += itemListMarginLeft * itemListAnimator.getMetadata().getVisibility();
    for (ListAnimator.Entry<Item> entry : itemListAnimator) {
      Item item = entry.item;
      RectF bounds = entry.getRectF();
      bounds.offset(x, y - halfItemSize);
      bounds.inset(itemPadding, itemPadding);
      ImageReceiver staticIconReceiver = reactionsReceiver.getStaticIconReceiver(item.reaction);
      setBounds(staticIconReceiver, bounds);
      staticIconReceiver.setAlpha(entry.getVisibility());
      staticIconReceiver.draw(canvas);
    }
    if (counter != null) {
      x += itemListAnimator.getMetadata().getTotalWidth();
      x += itemListMarginRight * itemListAnimator.getMetadata().getVisibility();
      x += counterMarginLeft * counter.getVisibility();
      counter.draw(canvas, x, y, Gravity.LEFT, 1f);
    }
  }

  private static final class Item implements ListAnimator.Measurable {
    @Px
    private final int size;
    @Nullable
    private final String reaction;

    private Item (@Px int size, @Nullable String reaction) {
      this.size = size;
      this.reaction = reaction;
    }

    @Override
    public int getWidth () {
      return size;
    }

    @Override
    public int getHeight () {
      return size;
    }

    @Override
    public boolean equals (Object o) {
      return o instanceof Item && ObjectsCompat.equals(((Item) o).reaction, reaction);
    }

    @Override
    public int hashCode () {
      return ObjectsCompat.hashCode(reaction);
    }
  }

  private static void setBounds (@NonNull Receiver receiver, @NonNull RectF bounds) {
    receiver.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
  }
}
