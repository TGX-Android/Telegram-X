package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.StickerSuggestionsProvider;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class EmojiToneListView extends FrameLayout {
  public static final float ITEM_SIZE = 36f;
  public static final float ITEM_PADDING = 4f;
  public static final float CORNER_WIDTH = 18f;
  public static final float CORNER_HEIGHT = 8f;
  public static final float VIEW_PADDING_HORIZONTAL = 1f + (1f / 3f);
  public static final float VIEW_PADDING_TOP = 1f + (1f / 3f);
  public static final float VIEW_PADDING_BOTTOM = 4f + (2f / 3f);
  public static final float EMOJI_DRAW_SIZE = 29f;

  private EmojiInfo[] tones;
  private Drawable backgroundDrawable, cornerDrawable;
  private StickerSuggestionsProvider.Result stickers;
  private ArrayList<StickerSmallView> stickerViews;
  private ArrayList<TGStickerObj> stickerObjs;
  private Tdlib tdlib;
  private int emojiColorState;

  public EmojiToneListView (Context context) {
    super(context);
  }

  public void init (ViewController<?> themeProvider, Tdlib tdlib) {
    this.tones = new EmojiInfo[EmojiData.emojiColors.length];
    this.backgroundDrawable = Theme.filteredDrawable(R.drawable.stickers_back_all, ColorId.overlayFilling, themeProvider);
    this.cornerDrawable = Theme.filteredDrawable(R.drawable.stickers_back_arrow, ColorId.overlayFilling, themeProvider);
    this.tdlib = tdlib;
  }

  private View boundView;
  private int offsetLeft;

  public void setAnchorView (View view, int offsetLeft) {
    this.boundView = view;
    this.offsetLeft = offsetLeft;
    setPivotX(view.getMeasuredWidth() / 2 - offsetLeft);
    setPivotY(Screen.dp(ITEM_SIZE + 4) + Screen.dp(3.5f) + Screen.dp(8f) / 2);
  }

  public View getAnchorView () {
    return boundView;
  }

  private int toneIndex = -1;
  private int toneIndexVertical = 0;

  public int getToneIndex () {
    return toneIndex;
  }

  public int getToneIndexVertical () {
    return toneIndexVertical;
  }

  public void setEmoji (String emoji, String currentTone, int emojiColorState) {
    this.emojiColorState = emojiColorState;
    int i = 0;
    for (String tone : EmojiData.emojiColors) {
      if (tone == null && currentTone == null) {
        toneIndex = 0;
      } else if (StringUtils.equalsOrBothEmpty(tone, currentTone)) {
        toneIndex = i;
      }
      tones[i] = Emoji.instance().getEmojiInfo(EmojiData.instance().colorize(emoji, tone));
      i++;
    }
  }

  @Nullable
  public TGStickerObj getSelectedCustomEmoji () {
    boolean hasToneEmoji = hasToneEmoji();
    if (hasToneEmoji && toneIndexVertical == 0) {
      return null;
    }
    int rowStart = hasToneEmoji ? 1: 0;
    int index = toneIndex;
    for (int a = rowStart; a < toneIndexVertical; a++) {
      index += getRowSize(a);
    }
    if (stickers != null && index >= 0 && index < stickerObjs.size()) {
      return stickerObjs.get(index);
    }
    return null;
  }

  public void setCustomEmoji (@Nullable StickerSuggestionsProvider.Result stickers) {
    this.stickers = stickers;
    if (stickers == null || stickers.isEmpty()) {
      return;
    }

    int a = Math.min(stickers.stickersFromLocal.stickers.length, 6);
    int b = Math.min(stickers.stickersFromServer.stickers.length, 6);
    int size = a + b;

    stickerViews = new ArrayList<>(size);
    stickerObjs = new ArrayList<>(size);

    for (int i = 0; i < b; i++) {
      TdApi.Sticker sticker = stickers.stickersFromServer.stickers[i];
      TGStickerObj stickerObj = new TGStickerObj(tdlib, sticker, sticker.emoji, sticker.fullType);
      StickerSmallView v = new StickerSmallView(getContext(), Screen.dp(2));
      v.setSticker(stickerObj);
      v.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(ITEM_SIZE), Screen.dp(ITEM_SIZE)));
      stickerObjs.add(stickerObj);
      stickerViews.add(v);
      addView(v);
    }

    for (int i = 0; i < a; i++) {
      TdApi.Sticker sticker = stickers.stickersFromLocal.stickers[i];
      TGStickerObj stickerObj = new TGStickerObj(tdlib, sticker, sticker.emoji, sticker.fullType);
      StickerSmallView v = new StickerSmallView(getContext(), Screen.dp(2));
      v.setSticker(stickerObj);
      v.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(ITEM_SIZE), Screen.dp(ITEM_SIZE)));
      stickerObjs.add(stickerObj);
      stickerViews.add(v);
      addView(v);
    }
  }



  public boolean changeIndex (float x, float y) {
    final int resV = MathUtils.clamp((int)((y - Screen.dp(VIEW_PADDING_TOP + ITEM_PADDING)) / Screen.dp(ITEM_SIZE)), 0, getRowsCount() - 1);
    final int resH = MathUtils.clamp((int)((x - getRowX(resV)) / Screen.dp(ITEM_SIZE)), 0, getRowSize(resV) - 1);

    if (resH != toneIndex || resV != toneIndexVertical) {
      toneIndex = resH;
      toneIndexVertical = resV;
      invalidate();
      return true;
    }
    return false;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (stickerViews == null) {
      return;
    }

    int row = hasToneEmoji() ? 1: 0;
    int x = getRowX(row);
    int y = getRowY(row);

    int i = 0;
    for (StickerSmallView v: stickerViews) {
      v.setTranslationX(x + Screen.dp(ITEM_SIZE * i));
      v.setTranslationY(y);
      i++;
      if (i == getRowSize(row)) {
        row++;
        i = 0;
        x = getRowX(row);
        y = getRowY(row);
      }
    }
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    dispatchDrawImpl(c);

    final int itemSize = Screen.dp(ITEM_SIZE);

    int x = Screen.dp(VIEW_PADDING_HORIZONTAL + ITEM_PADDING);
    int y = Screen.dp(VIEW_PADDING_TOP + ITEM_PADDING);

    if (hasToneEmoji()) {
      for (EmojiInfo info : tones) {
        int cx = x + itemSize / 2;
        int cy = y + itemSize / 2;
        int drawSize = Screen.dp(EMOJI_DRAW_SIZE);
        Rect rect = Paints.getRect();
        rect.left = cx - drawSize / 2;
        rect.top = cy - drawSize / 2;
        rect.right = rect.left + drawSize;
        rect.bottom = rect.top + drawSize;
        Emoji.instance().draw(c, info, rect);
        x += itemSize;
      }
    }

    super.dispatchDraw(c);
  }

  private void dispatchDrawImpl (Canvas c) {
    int count = getRowsCount();

    int y = Screen.dp(VIEW_PADDING_TOP + ITEM_PADDING);
    for (int a = 0; a < count; a++) {
      int x = getRowX(a);
      int bx = x - Screen.dp(VIEW_PADDING_HORIZONTAL + ITEM_PADDING);
      int by = y - Screen.dp(VIEW_PADDING_TOP + ITEM_PADDING);
      backgroundDrawable.setBounds(bx, by, bx + getRowWidth(a), by + Screen.dp(ITEM_SIZE + ITEM_PADDING * 2 + VIEW_PADDING_TOP + VIEW_PADDING_BOTTOM));
      backgroundDrawable.draw(c);

      if (toneIndexVertical == a) {
        int rectX = x + Screen.dp(ITEM_SIZE) * toneIndex;
        RectF rectF = Paints.getRectF();
        rectF.set(rectX, y, rectX + Screen.dp(ITEM_SIZE), y + Screen.dp(ITEM_SIZE));
        c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(Theme.HALF_RIPPLE_COLOR));
      }

      y += Screen.dp(ITEM_SIZE + ITEM_PADDING * 3);
    }

    int cornerX = getCornerX() - Screen.dp(CORNER_WIDTH / 2);
    int cornerY = calcViewHeight() - Screen.dp(VIEW_PADDING_BOTTOM);
    cornerDrawable.setBounds(cornerX, cornerY, cornerX + Screen.dp(CORNER_WIDTH), cornerY + Screen.dp(CORNER_HEIGHT));
    cornerDrawable.draw(c);
  }

  private int getCornerX () {
    if (boundView != null) {
      return boundView.getMeasuredWidth() / 2 - offsetLeft;
    }
    return 0;
  }

  public int getRowWidth (int rowIndex) {
    return Screen.dp(ITEM_SIZE * getRowSize(rowIndex) + ITEM_PADDING * 2 + VIEW_PADDING_HORIZONTAL * 2);
  }

  public int getRowY (int rowIndex) {
    return Screen.dp(VIEW_PADDING_TOP + ITEM_PADDING + (ITEM_SIZE + ITEM_PADDING * 3) * rowIndex);
  }

  public int getRowX (int rowIndex) {
    int boundWidth = (boundView != null) ? boundView.getMeasuredWidth(): Screen.dp(48);

    int totalWidth = calcViewWidth();
    int freeSpace = totalWidth - getRowWidth(rowIndex);

    float p = MathUtils.clamp((((float) getCornerX() - boundWidth / 2f)) / (totalWidth - boundWidth));
    return Screen.dp(VIEW_PADDING_HORIZONTAL + ITEM_PADDING) + (int) (freeSpace * p);
  }

  public int getRowSize (int rowIndex) {
    boolean hasTones = emojiColorState != EmojiData.STATE_NO_COLORS;
    if (hasTones) {
      if (rowIndex == 0) {
        return tones.length;
      } else {
        rowIndex -= 1;
      }
    }

    boolean isStickersSmall = stickers != null && stickers.size() <= 6 && stickers.size() >= 0;
    if (isStickersSmall) {
      return rowIndex == 0 ? Math.min(6, stickers.size()): 0;
    }

    if (stickers != null) {
      int count = (rowIndex == 0 && stickers.stickersFromServer.stickers.length > 0) ?
          stickers.stickersFromServer.stickers.length:
          stickers.stickersFromLocal.stickers.length;
      return Math.min(6, count);
    }
    return 0;
  }

  public boolean hasToneEmoji () {
    return emojiColorState != EmojiData.STATE_NO_COLORS;
  }

  public int getRowsCount () {
    boolean isStickersSmall = stickers != null && stickers.size() <= 6 && stickers.size() >= 0;

    int count = (emojiColorState != EmojiData.STATE_NO_COLORS ? 1: 0);
    if (isStickersSmall) {
      count += 1;
    } else {
      count += ((stickers != null && stickers.stickersFromServer.stickers.length > 0) ? 1: 0);
      count += ((stickers != null && stickers.stickersFromLocal.stickers.length > 0) ? 1: 0);
    }

    return count;
  }

  public int calcViewWidth () {
    int count = getRowsCount();
    int maxSize = 0;
    for (int a = 0; a < count; a++) {
      maxSize = Math.max(maxSize, getRowWidth(a));
    }

    return maxSize;
  }

  public int calcViewHeight () {
    int count = getRowsCount();
    return Screen.dp(ITEM_SIZE * count + ITEM_PADDING * (3 * count - 1) + VIEW_PADDING_TOP + VIEW_PADDING_BOTTOM);
  }
}
