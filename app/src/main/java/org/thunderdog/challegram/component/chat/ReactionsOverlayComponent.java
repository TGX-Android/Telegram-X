package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.data.TGMessageSticker;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class ReactionsOverlayComponent extends View {
  private final HashMap<String, OverlayItem> runningOverlays = new HashMap<>();

  public ReactionsOverlayComponent (Context context) {
    super(context);
    setWillNotDraw(false);
  }

  public void addReactionToOverlay (Tdlib tdlib, String key, TdApi.Reaction reaction) {
    runningOverlays.put(key, new OverlayItem(tdlib, key, this, reaction));
  }

  public void updateReactionOverlayLocation (String key, float centerX, float centerY, boolean isSmall) {
    if (runningOverlays.containsKey(key)) {
      runningOverlays.get(key).setBounds(centerX, centerY, isSmall);
    }
  }

  public void updateReactionOverlayAlpha (String key, boolean visible) {
    if (runningOverlays.containsKey(key)) {
      runningOverlays.get(key).updateReactionOverlayAlpha(visible);
    }
  }

  public void onScrolled (int dy) {
    for (OverlayItem ov : runningOverlays.values()) {
      ov.onScrolled(dy);
    }

    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    for (OverlayItem ov : runningOverlays.values()) {
      ov.draw(canvas);
    }
  }

  private static class OverlayItem {
    private final BoolAnimator alphaAnimator = new BoolAnimator(0, (FactorAnimator.Target) (id, factor, fraction, callee) -> {

    }, ReactionsComponent.RC_INTERPOLATOR, ReactionsComponent.RC_DURATION);

    private final GifReceiver receiver;

    public OverlayItem (Tdlib tdlib, String key, ReactionsOverlayComponent viewRef, TdApi.Reaction reaction) {
      GifFile animatedFile = new GifFile(tdlib, reaction.aroundAnimation != null ? reaction.aroundAnimation : reaction.effectAnimation);
      animatedFile.setScaleType(GifFile.CENTER_CROP);
      animatedFile.setSize(Screen.dp(96f));
      animatedFile.setPlayOnce(true);
      animatedFile.addLoopListener(() -> viewRef.runningOverlays.remove(key));

      this.receiver = new GifReceiver(viewRef);
      this.receiver.requestFile(animatedFile);
    }

    public void updateReactionOverlayAlpha (boolean visible) {
      alphaAnimator.setValue(visible, true);
    }

    public void onScrolled (int dy) {
      receiver.setBounds(receiver.getLeft(), receiver.getTop() - dy, receiver.getRight(), receiver.getBottom() - dy);
    }

    public void setBounds (float centerX, float centerY, boolean isSmall) {
      int halfSize = Screen.dp(isSmall ? 36f : 48f);
      receiver.setBounds((int) centerX - halfSize, (int) centerY - halfSize, (int) centerX + halfSize, (int) centerY + halfSize);
    }

    public void draw (Canvas canvas) {
      receiver.setAlpha(alphaAnimator.getFloatValue());
      receiver.draw(canvas);
      receiver.setAlpha(1f);
    }
  }
}
