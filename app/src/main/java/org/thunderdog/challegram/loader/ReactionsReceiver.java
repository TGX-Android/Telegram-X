package org.thunderdog.challegram.loader;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.HashMap;
import java.util.Map;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class ReactionsReceiver implements Destroyable {

  @NonNull
  private final View view;
  private final Map<String, ImageReceiver> staticIconReceivers = new HashMap<>();

  public ReactionsReceiver (@NonNull View view) {
    this.view = view;
  }

  @NonNull
  public ImageReceiver getStaticIconReceiver () {
    return getStaticIconReceiver(null);
  }

  @NonNull
  public ImageReceiver getStaticIconReceiver (@Nullable String reaction) {
    ImageReceiver staticIconReceiver = staticIconReceivers.get(reaction);
    if (staticIconReceiver == null) {
      staticIconReceiver = new ImageReceiver(view, 0);
      staticIconReceivers.put(reaction, staticIconReceiver);
    }
    return staticIconReceiver;
  }

  public void attach () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::attach);
  }

  public void detach () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::detach);
  }

  @Override
  public void performDestroy () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::destroy);
    staticIconReceivers.clear();
  }

  private static <T> void doOnEach (@NonNull Iterable<T> collection, @NonNull RunnableData<T> runnableData) {
    for (T entry : collection) {
      runnableData.runWithData(entry);
    }
  }

  public void requestFiles (@NonNull Tdlib tdlib, @Nullable TdApi.MessageReaction[] messageReactions) {
    if (messageReactions == null || messageReactions.length == 0) {
      return;
    }
    boolean hasChosenReaction = false;
    for (TdApi.MessageReaction messageReaction : messageReactions) {
      ImageReceiver staticIconReceiver = getStaticIconReceiver(messageReaction.reaction);
      TdApi.Reaction reaction = tdlib.getReaction(messageReaction.reaction);
      requestStaticIcon(tdlib, staticIconReceiver, reaction);
      if (messageReaction.isChosen && messageReaction.totalCount > 1) {
        hasChosenReaction = true;
        requestStaticIcon(tdlib, getStaticIconReceiver(), reaction);
      }
    }
    if (!hasChosenReaction) {
      requestStaticIcon(tdlib, getStaticIconReceiver(), null);
    }
  }

  private void requestStaticIcon (@NonNull Tdlib tdlib, @NonNull ImageReceiver receiver, @Nullable TdApi.Reaction reaction) {
    if (reaction != null && !Td.isAnimated(reaction.staticIcon.type)) {
      ImageFile imageFile = new ImageFile(tdlib, reaction.staticIcon.sticker);
      imageFile.setScaleType(ImageFile.FIT_CENTER);
      imageFile.setNoBlur();
      imageFile.setWebp();
      receiver.requestFile(imageFile);
    } else {
      receiver.clear();
    }
  }
}