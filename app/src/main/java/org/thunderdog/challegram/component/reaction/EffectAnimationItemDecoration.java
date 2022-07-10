package org.thunderdog.challegram.component.reaction;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class EffectAnimationItemDecoration extends RecyclerView.ItemDecoration implements Destroyable {

  private final Map<String, GifReceiver> receivers = new HashMap<>();

  @Override
  public void onDrawOver (@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    LayoutManager layoutManager = parent.getLayoutManager();
    if (!(layoutManager instanceof LinearLayoutManager)) {
      return;
    }
    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
    int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
    int lastVisibleItemPosition = linearLayoutManager.findLastVisibleItemPosition();
    for (int position = firstVisibleItemPosition; position <= lastVisibleItemPosition; position++) {
      View view = linearLayoutManager.findViewByPosition(position);
      if (!(view instanceof SelectableReactionView)) {
        continue;
      }
      SelectableReactionView selectableReactionView = (SelectableReactionView) view;
      ReactionView reactionView = selectableReactionView.getReactionView();
      TdApi.Reaction reaction = reactionView.getReaction();
      GifReceiver receiver = reaction != null ? receivers.get(reaction.reaction) : null;
      if (receiver == null) {
        continue;
      }
      GifFile gifFile = receiver.getCurrentFile();
      if (gifFile == null || gifFile.hasLooped()) {
        continue;
      }
      int inset = -Screen.dp(12f);
      receiver.setBounds(view.getLeft() + inset, view.getTop() + inset, view.getRight() - inset, view.getTop() + reactionView.getTop() + reactionView.getBottom() - inset);
      receiver.setAlpha(reactionView.getAlpha());
      receiver.draw(canvas);
    }
  }

  public void prepareAnimation (@NonNull RecyclerView parent, @NonNull Tdlib tdlib, @Nullable TdApi.Reaction reaction) {
    if (reaction == null) {
      return;
    }
    String key = reaction.reaction;
    if (receivers.containsKey(key)) {
      return;
    }
    TdApi.Sticker effectAnimation = reaction.effectAnimation;
    if (effectAnimation != null && Td.isAnimated(effectAnimation.type) && TD.isFileLoadedAndExists(effectAnimation.sticker)) {
      GifFile effectAnimationFile = new GifFile(tdlib, effectAnimation);
      effectAnimationFile.setScaleType(GifFile.FIT_CENTER);
      effectAnimationFile.setUnique(true);
      effectAnimationFile.setPlayOnce(true);
      effectAnimationFile.setOptimize(true);

      GifReceiver receiver = new GifReceiver(parent);
      receiver.requestFile(effectAnimationFile);

      effectAnimationFile.addLoopListener(() -> {
        receivers.values().remove(receiver);
        receiver.destroy();
      });

      receivers.put(key, receiver);
    }
  }

  @Override
  public void performDestroy () {
    for (GifReceiver receiver : receivers.values()) {
      receiver.destroy();
    }
    receivers.clear();
  }
}
