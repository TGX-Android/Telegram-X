package org.thunderdog.challegram.reactions;

import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;

public class CompactReactionsRenderer implements Counter.Callback {
  private Counter counter;
  private TGMessage parent;
  private int totalReactionsCount;
  private ArrayList<TdApi.Reaction> iconizedReactions = new ArrayList<>(3);
  private boolean selfReacted;
  private boolean iconOnlyMode;

  public CompactReactionsRenderer (TGMessage parent) {
    this.parent = parent;
    iconOnlyMode = parent.isChannel();
    Counter.Builder b = new Counter.Builder()
      .noBackground()
      .textSize(parent.useBubbles() ? 11f : 12f)
      .allBold(false)
      .callback(this)
      .colorSet(this::getCounterColor);

    if (iconOnlyMode) {
      b.drawable(R.drawable.baseline_favorite_14, 14, 0, Gravity.LEFT);
    }

    counter = b.build();
  }

  public void update (boolean animated) {
    TdApi.MessageInteractionInfo info = parent.getMessageForReactions().interactionInfo;
    iconizedReactions.clear();
    totalReactionsCount = 0;
    selfReacted = false;
    if (info == null || info.reactions == null) {
      counter.setCount(0, animated);
      return;
    }
    if (iconOnlyMode) {
      for (TdApi.MessageReaction reaction : info.reactions) {
        totalReactionsCount += reaction.totalCount;
        selfReacted = selfReacted || reaction.isChosen;
      }
      counter.setCount(totalReactionsCount > 0 ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, !selfReacted, animated);
    } else {
      for (TdApi.MessageReaction reaction : info.reactions) {
        totalReactionsCount += reaction.totalCount;
        selfReacted = selfReacted || reaction.isChosen;
        if (iconizedReactions.size() < 3) {
          iconizedReactions.add(parent.tdlib().getReaction(reaction.reaction));
        }
      }
      counter.setCount(totalReactionsCount > 1 ? totalReactionsCount : 0, !selfReacted, animated);
    }
  }

  public int getWidth () {
    if (iconOnlyMode)
      return Math.round(counter.getScaledWidth(Screen.dp(3)));
    if (iconizedReactions.isEmpty())
      return 0;
    return iconizedReactions.size() * Screen.dp(16) + Math.round(counter.getScaledWidth(Screen.dp(6))) + (parent.useBubbles() ? Screen.dp(3) : 0);
  }

  // Canvas must be translated as needed
  public void draw (Canvas c, View view, ComplexReceiver receiver) {
    int iconOffset = Screen.dp(16);
    int iconSize = Screen.dp(12);
    for (int i = 0; i < iconizedReactions.size(); i++) {
      ImageReceiver icon = receiver.getImageReceiver(i);
      icon.setBounds(iconOffset * i + iconOffset / 2 - iconSize / 2, -iconSize / 2, iconOffset * i + iconOffset / 2 + iconSize / 2, iconSize / 2);
      icon.draw(c);
    }
    if (iconOnlyMode)
      counter.draw(c, 0, 0, Gravity.LEFT, 1f, (DrawableProvider) view, getCounterColorID());
    else
      counter.draw(c, iconOffset * iconizedReactions.size() + Screen.dp(3), 0, Gravity.LEFT, 1f, (DrawableProvider) view, getCounterColorID());
  }

  public Counter getCounter () {
    return counter;
  }

  public void loadIconsIntoReceiver (ComplexReceiver receiver) {
    int i = 0;
    for (TdApi.Reaction r : iconizedReactions) {
      ImageReceiver icon = receiver.getImageReceiver(i);
      icon.requestFile(TD.toImageFile(parent.tdlib(), r.staticIcon.thumbnail));
      i++;
    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    parent.onCounterAppearanceChanged(counter, sizeChanged);
  }

  @Override
  public boolean needAnimateChanges (Counter counter) {
    return parent.needAnimateChanges(counter);
  }

  private int getCounterColorID () {
    if (selfReacted) {
      if (parent.useBubbles()) {
        return parent.isOutgoing() ? R.id.theme_color_bubbleOut_inlineText : R.id.theme_color_inlineText;
      }
      return R.id.theme_color_inlineText;
    }
    return parent.getTimePartIconColorId();
  }

  private int getCounterColor () {
    if (selfReacted) {
      if (parent.useBubbles()) {
        return Theme.getColor(parent.isOutgoing() ? R.id.theme_color_bubbleOut_inlineText : R.id.theme_color_inlineText);
      }
      return Theme.getColor(R.id.theme_color_inlineText);
    }
    return parent.getTimePartTextColor();
  }
}
