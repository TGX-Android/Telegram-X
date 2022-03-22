package org.thunderdog.challegram.loader.gcomb;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.td.Td;

// A wrapper for DoubleImageReceiver for TdApi.Reaction support
public class ReactionReceiver {
  private final DoubleImageReceiver wrapped;
  private Path outline;

  private boolean isGifPlaying;

  public ReactionReceiver (View view) {
    wrapped = new DoubleImageReceiver(view, 0, true);
  }

  public void setReaction (Tdlib tdlib, TdApi.Reaction reaction, int size) {
    outline = Td.buildOutline(reaction.staticIcon.outline, (float) size / reaction.staticIcon.height);
    wrapped.getPreview().requestFile(createPreview(tdlib, reaction, size));
    wrapped.getGifReceiver().requestFile(createFile(tdlib, reaction));
  }

  public Receiver getMainReceiver () {
    return wrapped.getPreview();
  }

  public void playGif () {
    isGifPlaying = true;
    wrapped.getGifReceiver().getCurrentFile().setLooped(false);
  }

  private ImageFile createPreview (Tdlib tdlib, TdApi.Reaction reaction, int size) {
    ImageFile staticIconFile = new ImageFile(tdlib, reaction.staticIcon.sticker);
    staticIconFile.setSize(size * 2);
    staticIconFile.setNoBlur();
    return staticIconFile;
  }

  private GifFile createFile (Tdlib tdlib, TdApi.Reaction reaction) {
    GifFile dynamicIconFile = new GifFile(tdlib, reaction.centerAnimation != null ? reaction.centerAnimation : reaction.activateAnimation);
    dynamicIconFile.setPlayOnce(true);
    return dynamicIconFile;
  }

  public void setBounds (int cx, int cy, int size) {
    int halfSize = size / 2;
    wrapped.getPreview().setBounds(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
    wrapped.getGifReceiver().setBounds(cx - size, cy - size, cx + size, cy + size);
  }

  public void draw (Canvas c, float alpha) {
    wrapped.setPaintAlpha(alpha);
    draw(c);
    wrapped.restorePaintAlpha();
  }

  public void draw (Canvas c) {
    if (wrapped.needPlaceholder()) {
      wrapped.getPreview().drawPlaceholderContour(c, outline);
    }

    if (isGifPlaying) {
      wrapped.draw(c, 1f);
    } else {
      wrapped.draw(c, 0f);
    }
  }

  public void attach () {
    wrapped.attach();
  }

  public void detach () {
    wrapped.detach();
  }

  public void destroy () {
    wrapped.destroy();
  }
}
