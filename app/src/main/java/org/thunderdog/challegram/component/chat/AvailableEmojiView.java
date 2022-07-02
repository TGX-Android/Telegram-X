package org.thunderdog.challegram.component.chat;

import static org.thunderdog.challegram.config.Config.COVER_OVERLAY_QUEUE;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.StringUtils;

public class AvailableEmojiView extends View implements ClickHelper.Delegate {
  private final ClickHelper helper;
  private final int emojiSize = Screen.dp(24f);
  private final int textPadding = Screen.dp(8);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint checkmarkIconPaint = Paints.createPorterDuffPaint(null, Theme.getColor(R.id.theme_color_controlActive));
  private final int checkmarkSize = Screen.dp(16f);
  private final Drawable checkmarkIcon;

  public AvailableEmojiView (Context context) {
    super(context);
    this.helper = new ClickHelper( this);
    this.checkmarkIcon = Drawables.get(R.drawable.baseline_check_circle_24);
    shadowPaint.setColor(COVER_OVERLAY_QUEUE);
    textPaint.setTextSize(Screen.sp(12f));
  }

  private boolean dispatchingEvents;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        dispatchingEvents = super.onTouchEvent(e);
        break;
      }
      default: {
        if (dispatchingEvents) {
          super.onTouchEvent(e);
        }
        break;
      }
    }
    return isEnabled() && helper.onTouchEvent(this, e);
  }

  private OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    this.onClickListener = l;
  }

  private TdApi.Reaction emoji;
  private boolean isSelected;
  private EmojiInfo info;

  public TdApi.Reaction getRawEmoji () {
    return emoji;
  }

  public boolean isSelected () {
    return isSelected;
  }

  public void setEmoji (TdApi.Reaction emoji, boolean isSelected) {
    if (this.emoji == null || !StringUtils.equalsOrBothEmpty(this.emoji.reaction, emoji.reaction)) {
      this.emoji = emoji;
      this.isSelected = isSelected;
      setDrawable(Emoji.instance().getEmojiInfo(emoji.reaction));
    }
  }

  private void setDrawable (EmojiInfo info) {
    if (this.info != info) {
      this.info = info;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    if (info != null) {
      int cx = viewWidth / 2;
      int cy = viewHeight / 2;

      Rect rect = Paints.getRect();
      rect.left = cx - emojiSize / 2;
      rect.top = cy - emojiSize;
      rect.right = rect.left + emojiSize;
      rect.bottom = rect.top + emojiSize;

      if (isSelected) {
        textPaint.setColor(Color.WHITE);
        Emoji.instance().draw(c, info, rect);
      } else {
        textPaint.setColor(Color.GRAY);
        Emoji.instance().draw(c, info, rect, shadowPaint);
      }
      c.save();

      CharSequence ellipsizeText = TextUtils.ellipsize(emoji.title, textPaint, 2 * (viewWidth - 3 * textPadding), TextUtils.TruncateAt.END);
      Layout titleLayout = U.createLayout(ellipsizeText, viewWidth - 2 * textPadding, textPaint, Layout.Alignment.ALIGN_CENTER);
      c.translate(textPadding, cy + textPadding);
      titleLayout.draw(c);

      c.restore();
      if (isSelected) {
        checkmarkIcon.setBounds(0, 0, checkmarkSize, checkmarkSize);
        Drawables.draw(c, checkmarkIcon, cx + checkmarkSize / 4f, cy - checkmarkSize / 2f, checkmarkIconPaint);
      }
    }
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return onClickListener != null;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (onClickListener != null) {
      onClickListener.onClick(view);
    }
  }
}
