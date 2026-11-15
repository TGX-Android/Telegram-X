/* This file is a part of Telegram X. Licensed under the GNU GPL, version 3 or later. */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.BotButtonStyle;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.android.ViewUtils;

/** Reply button: the tag and label still contain the bot's original command. */
final class BotKeyboardButton extends EmojiTextView {
  private final BotButtonEmoji emoji;
  private int backgroundColorId;
  private boolean hasIcon, attached;

  BotKeyboardButton (Context context) {
    super(context);
    emoji = new BotButtonEmoji(this);
    updateBackground();
  }

  void bind (TdApi.KeyboardButton button, @Nullable Tdlib tdlib) {
    setTag(button);
    setText(button.text != null ? button.text : "");
    int colorId = BotButtonStyle.backgroundColorId(button.style);
    if (backgroundColorId != colorId) {
      backgroundColorId = colorId;
      updateBackground();
    }
    hasIcon = button.iconCustomEmojiId != 0;
    emoji.set(tdlib, button.iconCustomEmojiId);
    int slot = hasIcon ? Screen.dp(BotButtonEmoji.SLOT_DP) : 0;
    setPadding(Lang.rtl() ? 0 : slot, 0, Lang.rtl() ? slot : 0, 0);
    setMaxLines(hasIcon ? 2 : Integer.MAX_VALUE);
    setEllipsize(hasIcon ? TextUtils.TruncateAt.END : null);
    invalidate();
  }

  void clearIcon () {
    emoji.set(null, 0);
  }

  private void updateBackground () {
    ViewUtils.setBackground(this, backgroundColorId == ColorId.NONE ?
      Theme.rectSelector(4f, 0f, ColorId.chatKeyboardButton) : new Drawable() {
        private final RectF rect = new RectF();
        @Override
        public void draw (@NonNull Canvas canvas) {
          rect.set(getBounds());
          int radius = Screen.dp(4f);
          canvas.drawRoundRect(rect, radius, radius, Paints.fillingPaint(Theme.getColor(backgroundColorId)));
          if (isPressed() || isFocused()) {
            canvas.drawRoundRect(rect, radius, radius, Paints.fillingPaint(Theme.getColor(ColorId.botButtonRipple)));
          }
        }
        @Override public void setAlpha (int alpha) { }
        @Override public void setColorFilter (@Nullable ColorFilter filter) { }
        @Override public int getOpacity () { return PixelFormat.TRANSLUCENT; }
        @Override public boolean isStateful () { return true; }
        @Override protected boolean onStateChange (int[] state) { invalidateSelf(); return true; }
      });
  }

  @Override
  protected void onDraw (Canvas canvas) {
    int foreground = backgroundColorId != ColorId.NONE ? Theme.getColor(ColorId.botButtonText) : Theme.textAccentColor();
    if (getCurrentTextColor() != foreground) setTextColor(foreground);
    super.onDraw(canvas);
    if (hasIcon) {
      Layout layout = getLayout();
      float textWidth = 0;
      if (layout != null) {
        for (int i = 0; i < layout.getLineCount(); i++) textWidth = Math.max(textWidth, layout.getLineWidth(i));
      }
      textWidth = Math.min(textWidth, Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight()));
      int slot = Screen.dp(BotButtonEmoji.SLOT_DP), size = Screen.dp(BotButtonEmoji.SIZE_DP);
      int x = Math.round((getWidth() - textWidth - slot) / 2f);
      if (Lang.rtl()) x = getWidth() - x - size;
      emoji.draw(canvas, x, (getHeight() - size) / 2, foreground);
    }
  }

  private void updateAttachment () {
    if (emoji != null) emoji.setAttached(attached && isShown() && getWindowVisibility() == VISIBLE);
  }

  @Override protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    attached = true;
    updateAttachment();
  }

  @Override protected void onDetachedFromWindow () {
    attached = false;
    updateAttachment();
    super.onDetachedFromWindow();
  }

  @Override protected void onVisibilityChanged (@NonNull View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    updateAttachment();
  }

  @Override protected void onWindowVisibilityChanged (int visibility) {
    super.onWindowVisibilityChanged(visibility);
    updateAttachment();
  }

  @Override public void performDestroy () {
    emoji.performDestroy();
    super.performDestroy();
  }
}
