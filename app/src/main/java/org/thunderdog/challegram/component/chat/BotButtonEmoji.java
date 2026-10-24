/* This file is a part of Telegram X. Licensed under the GNU GPL, version 3 or later. */
package org.thunderdog.challegram.component.chat;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.lambda.Destroyable;

/** A view-owned icon: metadata, downloads and decoders never outlive its binding. */
public final class BotButtonEmoji implements TdlibEmojiManager.Watcher, TGLegacyManager.EmojiLoadListener, Destroyable {
  public static final int SIZE_DP = 20;
  public static final int SLOT_DP = 26;
  private final View view;
  private final ComplexReceiver receiver;
  private final boolean lowMemory;
  private final Rect bounds = new Rect();
  private Tdlib tdlib;
  private long customEmojiId;
  private TdlibEmojiManager.Entry entry;
  private ImageFile image;
  private GifFile animation;
  private EmojiInfo fallback;
  private boolean attached, destroyed, retried;
  private final Runnable retry = () -> {
    if (attached && !destroyed && entry != null) {
      tdlib.emoji().retryTransientError(entry);
      request();
    }
  };

  public BotButtonEmoji (View view) {
    this.view = view;
    ActivityManager memory = (ActivityManager) view.getContext().getSystemService(Context.ACTIVITY_SERVICE);
    lowMemory = memory != null && (memory.getMemoryClass() <= 128 ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && memory.isLowRamDevice()));
    receiver = new ComplexReceiver(view, 30);
    receiver.detach();
  }

  public void set (Tdlib tdlib, long customEmojiId) {
    if (this.tdlib == tdlib && this.customEmojiId == customEmojiId) return;
    forget();
    receiver.clear();
    this.tdlib = tdlib;
    this.customEmojiId = customEmojiId;
    entry = null;
    image = null;
    animation = null;
    fallback = null;
    retried = false;
    if (attached) request();
  }

  public void setAttached (boolean attached) {
    if (destroyed || this.attached == attached) return;
    this.attached = attached;
    if (attached) {
      receiver.attach();
      TGLegacyManager.instance().addEmojiListener(this);
      retried = false;
      request();
    } else {
      TGLegacyManager.instance().removeEmojiListener(this);
      forget();
      receiver.detach();
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    view.invalidate();
  }

  private void forget () {
    view.removeCallbacks(retry);
    if (tdlib != null && customEmojiId != 0) tdlib.emoji().forgetWatcher(customEmojiId, this);
  }

  private void request () {
    if (tdlib == null || customEmojiId == 0 || destroyed) return;
    TdlibEmojiManager.Entry cached = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
    if (cached != null) apply(cached);
    else tdlib.emoji().performPostponedRequestsDelayed();
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry result) {
    view.post(() -> {
      if (!destroyed && attached && tdlib != null && context == tdlib.emoji() && result.customEmojiId == customEmojiId) {
        apply(result);
        view.invalidate();
      }
    });
  }

  private void apply (TdlibEmojiManager.Entry result) {
    if (result.error != null) {
      entry = result;
      if (!retried && result.isTransientError()) {
        retried = true;
        // One bounded retry per attachment, shared/deduplicated by the account's manager.
        view.postDelayed(retry, TdlibEmojiManager.ERROR_RETRY_DELAY_MS);
      }
      return;
    }
    if (entry == result && (animation == null ||
        (animation.isStill() == needsStaticFrame() && animation.isPlayOnce() ==
          Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI_LOOP)))) return;
    view.removeCallbacks(retry);
    entry = result;
    TdApi.Sticker sticker = result.value;
    fallback = Emoji.instance().getEmojiInfo(sticker.emoji);
    int size = Screen.dp(SIZE_DP);
    ImageFile thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(size);
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }
    receiver.getPreviewReceiver(0).requestFile(null, thumbnail);
    image = null;
    animation = null;
    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatWebp.CONSTRUCTOR:
        image = new ImageFile(tdlib, sticker.sticker);
        image.setWebp(); // Use bundled libwebp on old Android, including lossless alpha.
        image.setSize(size);
        image.setScaleType(ImageFile.FIT_CENTER);
        image.setNoBlur();
        break;
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR:
        animation = new GifFile(tdlib, sticker);
        animation.setScaleType(GifFile.FIT_CENTER);
        animation.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        animation.setRequestedSize(size);
        // Bundled rlottie/FFmpeg also work on legacy Android. Limit playback by
        // device resources and user preferences, not by the Android version alone.
        animation.setIsStill(needsStaticFrame());
        animation.setPlayOnce(Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI_LOOP));
        break;
    }
    receiver.getImageReceiver(0).requestFile(image);
    receiver.getGifReceiver(0).requestFile(animation);
  }

  private boolean needsStaticFrame () {
    return lowMemory || Settings.instance().needReduceMotion() ||
      Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI);
  }

  public void draw (Canvas c, int x, int y, int foreground) {
    if (customEmojiId == 0) return;
    int size = Screen.dp(SIZE_DP);
    bounds.set(x, y, x + size, y + size);
    Receiver content = image != null ? receiver.getImageReceiver(0) : animation != null ? receiver.getGifReceiver(0) : null;
    boolean repaint = entry != null && entry.value != null && TD.needThemedColorFilter(entry.value);
    int save = c.save();
    c.clipRect(bounds);
    if (content == null || content.needPlaceholder()) {
      DoubleImageReceiver preview = receiver.getPreviewReceiver(0);
      preview.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
      if (preview.needPlaceholder() && (fallback == null || !Emoji.instance().draw(c, fallback, bounds))) {
        // A neutral drawable fallback cannot turn into a missing system-font glyph.
        float cx = bounds.exactCenterX(), cy = bounds.exactCenterY(), r = size * .3f;
        c.drawCircle(cx, cy, r, Paints.fillingPaint((foreground & 0x00ffffff) | 0x40000000));
        c.drawCircle(cx - r * .35f, cy - r * .15f, size * .045f, Paints.fillingPaint(foreground));
        c.drawCircle(cx + r * .35f, cy - r * .15f, size * .045f, Paints.fillingPaint(foreground));
        c.drawRect(cx - r * .3f, cy + r * .3f, cx + r * .3f, cy + r * .4f, Paints.fillingPaint(foreground));
      }
      tint(preview, repaint, foreground);
      preview.draw(c);
    }
    if (content != null) {
      content.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
      tint(content, repaint, foreground);
      content.draw(c);
    }
    c.restoreToCount(save);
  }

  private static void tint (Receiver receiver, boolean repaint, int foreground) {
    if (repaint) receiver.setPorterDuffColorFilter(foreground);
    else receiver.disablePorterDuffColorFilter();
  }

  @Override
  public void performDestroy () {
    destroyed = true;
    if (attached) TGLegacyManager.instance().removeEmojiListener(this);
    forget();
    receiver.performDestroy();
    tdlib = null;
  }
}
