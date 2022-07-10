package org.thunderdog.challegram.loader;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
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
  private final Map<String, GifReceiver> centerAnimationReceivers = new HashMap<>();
  private final Map<String, GifReceiver> aroundAnimationReceivers = new HashMap<>();

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

  @Nullable
  public GifReceiver getCenterAnimationReceiver (@Nullable String reaction) {
    if (reaction == null) {
      return null;
    }
    return centerAnimationReceivers.get(reaction);
  }

  @Nullable
  public GifReceiver getAroundAnimationReceiver (@Nullable String reaction) {
    if (reaction == null) {
      return null;
    }
    return aroundAnimationReceivers.get(reaction);
  }

  public void attach () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::attach);
  }

  public void detach () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::detach);

    doOnEach(centerAnimationReceivers.values(), GifReceiver::destroy);
    centerAnimationReceivers.clear();

    doOnEach(aroundAnimationReceivers.values(), GifReceiver::destroy);
    aroundAnimationReceivers.clear();
  }

  @Override
  public void performDestroy () {
    doOnEach(staticIconReceivers.values(), ImageReceiver::destroy);
    staticIconReceivers.clear();

    doOnEach(centerAnimationReceivers.values(), GifReceiver::destroy);
    centerAnimationReceivers.clear();

    doOnEach(aroundAnimationReceivers.values(), GifReceiver::destroy);
    aroundAnimationReceivers.clear();
  }

  private static <T> void doOnEach (@NonNull Iterable<T> collection, @NonNull RunnableData<T> runnableData) {
    for (T entry : collection) {
      runnableData.runWithData(entry);
    }
  }

  public void playAnimation (@NonNull Tdlib tdlib, @Nullable String reaction) {
    playAnimation(tdlib, tdlib.getReaction(reaction));
  }

  public void playAnimation (@NonNull Tdlib tdlib, @Nullable TdApi.Reaction reaction) {
    if (reaction == null) {
      return;
    }
    String key = reaction.reaction;
    GifReceiver centerAnimationReceiver = getCenterAnimationReceiver(key);
    GifReceiver aroundAnimationReceiver = getAroundAnimationReceiver(key);
    if (isAnimationPlaying(centerAnimationReceiver) || isAnimationPlaying(aroundAnimationReceiver)) {
      return;
    }
    TdApi.Sticker centerAnimation = reaction.centerAnimation;
    if (canPlayAnimation(centerAnimation)) {
      GifFile centerAnimationFile = new GifFile(tdlib, centerAnimation);
      centerAnimationFile.setUnique(true);
      centerAnimationFile.setPlayOnce(true);
      centerAnimationFile.setOptimize(true);
      centerAnimationFile.setScaleType(GifFile.CENTER_CROP);
      centerAnimationFile.addLoopListener(() -> {
        GifReceiver receiver = centerAnimationReceivers.remove(key);
        if (receiver != null) {
          receiver.destroy();
        }
      });

      if (centerAnimationReceiver == null) {
        centerAnimationReceiver = new GifReceiver(view);
        centerAnimationReceivers.put(key, centerAnimationReceiver);
      }
      centerAnimationReceiver.requestFile(centerAnimationFile);
      resetAnimation(centerAnimationReceiver);
    }

    TdApi.Sticker aroundAnimation = reaction.aroundAnimation;
    if (canPlayAnimation(aroundAnimation)) {
      GifFile aroundAnimationFile = new GifFile(tdlib, aroundAnimation);
      aroundAnimationFile.setUnique(true);
      aroundAnimationFile.setPlayOnce(true);
      aroundAnimationFile.setOptimize(true);
      aroundAnimationFile.setScaleType(GifFile.CENTER_CROP);
      aroundAnimationFile.addLoopListener(() -> {
        GifReceiver receiver = aroundAnimationReceivers.remove(key);
        if (receiver != null) {
          receiver.destroy();
        }
      });

      if (aroundAnimationReceiver == null) {
        aroundAnimationReceiver = new GifReceiver(view);
        aroundAnimationReceivers.put(reaction.reaction, aroundAnimationReceiver);
      }
      aroundAnimationReceiver.requestFile(aroundAnimationFile);
      resetAnimation(aroundAnimationReceiver);
    }
  }

  public void requestFiles (@NonNull Tdlib tdlib, @Nullable TdApi.MessageReaction[] messageReactions) {
    if (messageReactions == null || messageReactions.length == 0) {
      doOnEach(staticIconReceivers.values(), ImageReceiver::clear);
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
      preloadAnimations(tdlib, reaction);
    }
    if (!hasChosenReaction) {
      requestStaticIcon(tdlib, getStaticIconReceiver(), null);
    }
  }

  private static void preloadAnimations (@NonNull Tdlib tdlib, @Nullable TdApi.Reaction reaction) {
    if (reaction == null) {
      return;
    }
    preloadAnimation(tdlib, reaction.centerAnimation);
    preloadAnimation(tdlib, reaction.aroundAnimation);
  }

  private static void preloadAnimation (@NonNull Tdlib tdlib, @Nullable TdApi.Sticker animation) {
    TdApi.File file = animation != null ? animation.sticker : null;
    if (file == null || TD.isFileLoaded(file)) {
      return;
    }
    tdlib.files().downloadFile(file);
  }

  private static boolean isAnimationPlaying (@Nullable GifReceiver gifReceiver) {
    if (gifReceiver == null) {
      return false;
    }
    GifFile gifFile = gifReceiver.getCurrentFile();
    return gifFile != null && !gifFile.hasLooped();
  }

  private static boolean canPlayAnimation (@Nullable TdApi.Sticker animation) {
    return animation != null && Td.isAnimated(animation.type) && TD.isFileLoadedAndExists(animation.sticker);
  }

  private static void resetAnimation (@NonNull GifReceiver gifReceiver) {
    GifFile currentFile = gifReceiver.getCurrentFile();
    if (currentFile != null && currentFile.hasLooped()) {
      if (currentFile.setLooped(false)) {
        gifReceiver.getTargetView().invalidate();
      }
    }
  }

  private static void requestStaticIcon (@NonNull Tdlib tdlib, @NonNull ImageReceiver receiver, @Nullable TdApi.Reaction reaction) {
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