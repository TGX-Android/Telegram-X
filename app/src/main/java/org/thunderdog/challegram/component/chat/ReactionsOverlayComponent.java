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

public class ReactionsOverlayComponent extends View {
  private static final int RC_HIDE_AFTER = 1000;

  private final Handler rcHandler = new Handler(Looper.getMainLooper());

  private final ArrayList<GifReceiver> runningOverlays = new ArrayList<>();
  private float[] runningOverlaysDbg = new float[] {0, 0};

  public ReactionsOverlayComponent (Context context) {
    super(context);
    setWillNotDraw(false);
  }

  public void addReactionToOverlay (Tdlib tdlib, float centerX, float centerY, TdApi.Reaction reaction) {
    int size = Screen.dp(96f);
    int halfSize = size / 2;

    GifReceiver gr = new GifReceiver(this);
    gr.setBounds((int) centerX - halfSize, (int) centerY - halfSize, (int) centerX + halfSize, (int) centerY + halfSize);

    GifFile animatedFile = new GifFile(tdlib, reaction.aroundAnimation != null ? reaction.aroundAnimation : reaction.effectAnimation); // activateAnimation, centerAnimation
    animatedFile.setScaleType(GifFile.CENTER_CROP);
    animatedFile.setSize(size);
    animatedFile.setPlayOnce(true);
    animatedFile.addLoopListener(() -> runningOverlays.remove(gr));

    gr.requestFile(animatedFile);

    //runningOverlaysDbg = new float[] { centerX, centerY };
    runningOverlays.add(gr);
  }

  public void onScrolled (int dy) {
    for (int i = 0; i < runningOverlays.size(); i++) {
      GifReceiver ov = runningOverlays.get(i);
      ov.setBounds(ov.getLeft(), ov.getTop() - dy, ov.getRight(), ov.getBottom() - dy);
      //runningOverlaysDbg[1] -= dy;
    }

    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    for (int i = 0; i < runningOverlays.size(); i++) {
      runningOverlays.get(i).draw(canvas);
      //if (BuildConfig.DEBUG) canvas.drawCircle(runningOverlaysDbg[0], runningOverlaysDbg[1], 20, Paints.getGreenPorterDuffPaint());
    }
  }
}
