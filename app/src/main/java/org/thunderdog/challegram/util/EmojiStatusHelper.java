package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextMedia;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class EmojiStatusHelper implements Destroyable {
  private final @Nullable Tdlib tdlib;
  public final ComplexReceiver emojiStatusReceiver;
  private final View parentView;

  private @Nullable EmojiStatusDrawable emojiStatusDrawable;
  private @Nullable Text.ClickListener clickListenerToSet;

  public EmojiStatusHelper (@Nullable Tdlib tdlib, View v) {
    this.tdlib = tdlib;
    this.parentView = v;
    emojiStatusReceiver = new ComplexReceiver(v, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
  }

  public void setClickListener (@Nullable Text.ClickListener clickListenerToSet) {
    this.clickListenerToSet = clickListenerToSet;
  }

  public void invalidateEmojiStatusReceiver (@Nullable TextMedia specificMedia) {
    invalidateEmojiStatusReceiver(emojiStatusDrawable != null ? emojiStatusDrawable.emojiStatus: null, specificMedia);
  }

  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    if (text != null) {
      text.requestMedia(emojiStatusReceiver, 0, 1);
    }
    if (parentView != null) {
      parentView.invalidate();
    }
  }

  public void updateEmoji (@Nullable TdApi.User user, TextColorSet textColorSet) {
    updateEmoji(tdlib, user, textColorSet);
  }

  public void updateEmoji (Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet) {
    updateEmoji(tdlib, user, textColorSet, R.drawable.baseline_premium_star_16, 15);
  }

  public boolean onTouchEvent (View v, MotionEvent ev) {
    return emojiStatusDrawable != null && emojiStatusDrawable.onTouchEvent(v, ev);
  }

  public void updateEmoji (Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet, int defaultStarIconId, int textSize) {
    emojiStatusDrawable = new EmojiStatusDrawable(tdlib, user, clickListenerToSet, textColorSet, this::invalidateEmojiStatusReceiver, defaultStarIconId, textSize);
    emojiStatusDrawable.ignoreDraw = ignoreDraw;
    invalidateEmojiStatusReceiver(emojiStatusDrawable.emojiStatus, null);
  }

  public int getLastDrawX () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.lastDrawX: 0;
  }

  public int getLastDrawY () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.lastDrawY: 0;
  }

  public void attach () {
    emojiStatusReceiver.attach();
  }

  public void detach () {
    emojiStatusReceiver.detach();
  }

  public void destroy () {
    emojiStatusReceiver.performDestroy();
  }

  @Override
  public void performDestroy () {
    emojiStatusReceiver.performDestroy();
  }

  public boolean needDrawEmojiStatus () {
    return emojiStatusDrawable != null && emojiStatusDrawable.needDrawEmojiStatus;
  }

  public int getWidth () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.getWidth(): 0;
  }

  public int getWidth (int offset) {
    return emojiStatusDrawable != null ? emojiStatusDrawable.getWidth(offset): 0;
  }

  public void draw (Canvas c, int startX, int startY) {
    draw(c, startX, startY, 1f);
  }

  public void draw (Canvas c, int startX, int startY, float alpha) {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.draw(c, startX, startY, alpha, emojiStatusReceiver);
    }
  }

  public void draw (Canvas c, int startX, int startY, float alpha, float scale) {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.draw(c, startX, startY, alpha, scale, emojiStatusReceiver);
    }
  }

  public interface EmojiStatusReceiverInvalidateDelegate {
    void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia);
  }

  public static int emojiSizeToTextSize (int emojiSize) {
    return emojiSize - 3;
  }

  private boolean ignoreDraw;

  public void setIgnoreDraw (boolean ignoreDraw) {
    this.ignoreDraw = ignoreDraw;
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.ignoreDraw = ignoreDraw;
    }
  }

  public void onAppear () {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.onAppear();
    }
  }


  public static EmojiStatusDrawable makeDrawable (Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet, Text.TextMediaListener textMediaListener) {
    return new EmojiStatusDrawable(tdlib, user, null, textColorSet, textMediaListener, R.drawable.baseline_premium_star_16, 15);
  }

  public static @NonNull EmojiStatusDrawable makeDrawable (Tdlib tdlib, @Nullable TdApi.User user, @Nullable Text.ClickListener clickListener, TextColorSet textColorSet, Text.TextMediaListener textMediaListener, int defaultStarIconId, int textSize) {
    return new EmojiStatusDrawable(tdlib, user, clickListener, textColorSet, textMediaListener, defaultStarIconId, textSize);
  }

  public static class EmojiStatusDrawable {
    private final @Nullable Text emojiStatus;
    private final @Nullable Drawable starDrawable;
    private final @Nullable TextColorSet textColorSet;
    private final @Nullable Text.ClickListener clickListener;
    private final boolean needDrawEmojiStatus;
    private final Text.TextMediaListener textMediaListener;
    private final int textSize;
    private int lastDrawX, lastDrawY;
    private float lastDrawScale = 1f;
    private boolean ignoreDraw;

    private EmojiStatusDrawable (Tdlib tdlib, @Nullable TdApi.User user, @Nullable Text.ClickListener clickListener, @Nullable TextColorSet textColorSet, Text.TextMediaListener textMediaListener, int defaultStarIconId, int textSize) {
      this.emojiStatus = makeText(tdlib, user, clickListener, textColorSet, textMediaListener, textSize);
      this.needDrawEmojiStatus = user != null && user.isPremium;
      this.textSize = textSize;
      this.textColorSet = textColorSet;
      this.textMediaListener = textMediaListener;
      this.clickListener = clickListener;
      this.starDrawable = emojiStatus == null && needDrawEmojiStatus ? Drawables.get(defaultStarIconId): null;
    }

    public void setIgnoreDraw (boolean ignoreDraw) {
      this.ignoreDraw = ignoreDraw;
    }

    public void invalidateTextMedia () {
      if (emojiStatus != null) {
        textMediaListener.onInvalidateTextMedia(emojiStatus, null);
      }
    }

    public int getWidth () {
      if (emojiStatus != null) {
        return emojiStatus.getWidth();
      } else if (needDrawEmojiStatus) {
        return Screen.dp(18);
      }
      return 0;
    }

    public int getWidth (int offset) {
      if (emojiStatus != null) {
        return emojiStatus.getWidth() + offset;
      } else if (needDrawEmojiStatus) {
        return Screen.dp(18) + offset;
      }
      return 0;
    }

    public void requestMedia (ComplexReceiver complexReceiver) {
      if (emojiStatus != null) {
        emojiStatus.requestMedia(complexReceiver, 0, 1);
      }
    }

    boolean isCapture;

    public boolean onTouchEvent (View v, MotionEvent e) {
      if (emojiStatus != null) {
        return emojiStatus.onTouchEvent(v, e);
      }
      if (clickListener == null || starDrawable == null) return false;

      int touchX = (int) e.getX();
      int touchY = (int) e.getY();

      boolean isInside = (lastDrawX <= touchX) && (lastDrawY <= touchY) &&
        (touchX <= lastDrawX + starDrawable.getMinimumWidth()) &&
        (touchY <= lastDrawY + starDrawable.getMinimumHeight());

      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          return isCapture = isInside;
        }
        case MotionEvent.ACTION_CANCEL: {
          boolean r = isCapture;
          isCapture = false;
          return r;
        }
        case MotionEvent.ACTION_MOVE: {
          if (isCapture) {
            if (Math.max(Math.abs(touchX - e.getX()), Math.abs(touchY - e.getY())) > Screen.getTouchSlop()) {
              isCapture = false;
            }
            return true;
          }
          return false;
        }
        case MotionEvent.ACTION_UP: {
          if (isCapture) {
            clickListener.onClick(null, null, null, null);
            isCapture = false;
            return true;
          }
          break;
        }
      }
      return isCapture;
    }

    public void draw (Canvas c, int startX, int startY, float alpha, ComplexReceiver emojiStatusReceiver) {
      draw(c, startX, startY, alpha, 1f, emojiStatusReceiver);
    }

    public void draw (Canvas c, int startX, int startY, float alpha, float scale, ComplexReceiver emojiStatusReceiver) {
      if (ignoreDraw) return;

      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, startX, startY);
      }
      lastDrawX = startX;
      lastDrawY = startY;
      lastDrawScale = scale;
      if (emojiStatus != null) {
        emojiStatus.draw(c, startX, startY, null, alpha, emojiStatusReceiver);
      } else if (starDrawable != null && textColorSet != null) {
        Paint p = Paints.getPorterDuffPaint(ColorUtils.alphaColor(alpha, textColorSet.emojiStatusColor()));
        Drawables.draw(c, starDrawable, startX, startY + (Screen.dp(textSize + 2) - starDrawable.getMinimumHeight()) / 2f, p);
      }
      if (scale != 1f) {
        c.restore();
      }
    }

    public void onAppear () {
      if (emojiStatus != null) {
        TextMedia part = emojiStatus.getTextMediaFromLastPart();
        if (part != null) {
          part.rebuild();
        }
      }
    }
  }



  private static @Nullable Text makeText (Tdlib tdlib, @Nullable TdApi.User user, Text.ClickListener clickListener, TextColorSet textColorSet, Text.TextMediaListener textMediaListener, int textSize) {
    TdApi.FormattedText text = makeEmojiText(user);
    if (text == null) return null;

    Text result = new Text.Builder(tdlib, text, null, Screen.dp(1000), Paints.robotoStyleProvider(textSize), textColorSet, textMediaListener)
      .singleLine()
      .onClick(clickListener)
      .build();

    TextMedia part = result.getTextMediaFromLastPart();
    if (part != null) {
      part.setIsEmojiStatus();
    }

    return result;
  }

  private static @Nullable TdApi.FormattedText makeEmojiText (@Nullable TdApi.User user) {
    if (user == null || user.emojiStatus == null) return null;

    long emojiStatusId = user.emojiStatus.customEmojiId;
    TdApi.TextEntity emoji = new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiStatusId));
    return new TdApi.FormattedText("*", new TdApi.TextEntity[] {emoji});
  }
}
