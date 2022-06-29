package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class SelectableReactionView extends View {
  private static final int CHECK_MARK_MIN_SIZE = Screen.dp(10f);

  private OnClickListener onClickListener;
  private final EmojiToneHelper toneHelper;
  private String caption;
  private String emoji;
  private int colorState;
  private EmojiInfo info;
  private String emojiColored;
  private String emojiTone;
  private String[] emojiOtherTones;
  private boolean selected = false;
  Drawable checkMarkDrawable;

  public SelectableReactionView (Context context, EmojiToneHelper toneHelper) {
    super(context);
    this.toneHelper = toneHelper;
    checkMarkDrawable = getResources().getDrawable(R.drawable.baseline_check_circle_24);
    int color = Theme.getColor(R.id.theme_color_bubbleOut_file);
    DrawableCompat.setTint(checkMarkDrawable, color);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    //noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    this.onClickListener = l;
  }


  public void setReaction (String emoji, int colorState) {
    if (StringUtils.equalsOrBothEmpty(this.emoji, emoji)) return;
    this.emoji = emoji;
    this.colorState = colorState;
    this.emojiTone = colorState != EmojiData.STATE_NO_COLORS ? Emoji.instance().toneForEmoji(emoji) : null;
    this.emojiOtherTones = colorState == EmojiData.STATE_HAS_TWO_COLORS ? Emoji.instance().otherTonesForEmoji(emoji) : null;
    setEmojiImpl(emoji, emojiTone, emojiOtherTones);
  }

  private void setEmojiImpl (String emoji, String tone, String[] otherTones) {
    String emojiColored = EmojiData.instance().colorize(emoji, tone, otherTones);
    if (StringUtils.equalsOrBothEmpty(this.emojiColored, emojiColored)) return;
    this.emojiColored = emojiColored;
    this.emojiTone = tone;
    this.emojiOtherTones = otherTones;
    setDrawable(Emoji.instance().getEmojiInfo(emojiColored));
  }

  private void setDrawable (EmojiInfo info) {
    if (this.info != info) {
      this.info = info;
      invalidate();
    }
  }

  public void setSelected(boolean value) {
    if (value == selected) return;
    selected = value;
    invalidate();
  }

  public boolean selected() {
    return selected;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (info != null) {
      final int viewWidth = getMeasuredWidth();
      final int viewHeight = getMeasuredHeight();
      int cx = viewWidth / 2;
      int cy = viewHeight / 2;

      // Draw emoji
      int emojiSize = Math.min(viewWidth, viewHeight) - Screen.dp(16f);
      Rect emojiRect = Paints.getRect();
      emojiRect.left = cx - emojiSize / 2;
      emojiRect.top = cy - emojiSize / 2;
      emojiRect.right = emojiRect.left + emojiSize;
      emojiRect.bottom = emojiRect.top + emojiSize;
      Emoji.instance().draw(c, info, emojiRect);


      // Draw check mark
      if (selected) {
        int markSize = Math.max(CHECK_MARK_MIN_SIZE, emojiSize / 4);
        int markRight = viewWidth;
        int markBottom = viewHeight;
        int markLeft = markRight - markSize;
        int markTop = markBottom - markSize;
        checkMarkDrawable.setBounds(markLeft, markTop, markRight, markBottom);
        checkMarkDrawable.draw(c);
      }
    }
  }
}
