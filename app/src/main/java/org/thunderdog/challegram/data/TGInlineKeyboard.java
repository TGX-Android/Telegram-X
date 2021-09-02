package org.thunderdog.challegram.data;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.EmojiString;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.CheckBox;
import org.thunderdog.challegram.widget.ProgressComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

/**
 * Date: 08/11/2016
 * Author: default
 */

public class TGInlineKeyboard {
  private static final float CURRENCY_TEXT_SIZE_DP = 10f;
  private static final float BUTTON_TEXT_SIZE_DP = 14f;

  private final @NonNull TGMessage context;
  private final @NonNull TGMessage parent;
  private final RectF rounder;

  private ViewProvider viewProvider;
  private int maxWidth;
  private @Nullable TdApi.ReplyMarkupInlineKeyboard keyboard;
  private long messageId;

  private final ArrayList<Button> buttons;

  public interface ClickListener {
    void onClick (View view, TGInlineKeyboard keyboard, Button button);
  }

  public TGInlineKeyboard (@NonNull TGMessage parent, boolean owned) {
    this.context = parent;
    this.parent = parent; //  owned ? parent : null;
    this.buttons = new ArrayList<>();
    this.rounder = new RectF();
  }

  public void clear () {
    this.keyboard = null;
    this.messageId = 0;
  }

  public void updateMessageId (long oldMessageId, long newMessageId) {
    if (this.messageId == oldMessageId) {
      this.messageId = newMessageId;
    }
  }

  public void set (long replyMarkupMessageId, @NonNull TdApi.ReplyMarkupInlineKeyboard keyboard, int contentWidth, int contentMaxWidth) {
    this.keyboard = keyboard;
    this.messageId = replyMarkupMessageId;
    int realMaxWidth = Math.max(contentWidth, findMaxColumnCount(keyboard.rows) * getSmallestDesiredWidth());
    this.maxWidth = Math.min(contentMaxWidth, Math.max(context.useBubbles() ? Screen.dp(40f) : Screen.dp(200f), realMaxWidth));
    buildLayout(maxWidth, contentMaxWidth);
  }

  private boolean isCustom;

  public void setCustom (int iconRes, String text, int maxWidth, ClickListener listener) {
    this.maxWidth = maxWidth;
    this.isCustom = true;

    Button button = new Button(this, parent, text.toUpperCase(), iconRes, maxWidth - getButtonPadding() * 2);
    button.setClickListener(listener);
    button.setViewProvider(viewProvider);

    this.buttons.clear();
    this.buttons.add(button);
  }

  public boolean clickFirstButton (View view) {
    if (buttons.size() > 0) {
      buttons.get(0).performClick(view);
      return true;
    }
    return false;
  }

  public Button firstButton () {
    return buttons.get(0);
  }

  public void openGame (View view) {
    if (buttons.size() > 0) {
      TdApi.InlineKeyboardButtonType type = buttons.get(0).type;
      if (type != null && type.getConstructor() == TdApi.InlineKeyboardButtonTypeCallbackGame.CONSTRUCTOR) {
        buttons.get(0).performClick(view);
      }
    }
  }

  public void setViewProvider (ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    for (Button button : buttons) {
      button.setViewProvider(viewProvider);
    }
  }

  private static int findMaxColumnCount (TdApi.InlineKeyboardButton[][] rows) {
    int max = -1;
    for (TdApi.InlineKeyboardButton[] row : rows) {
      if (row.length > max) {
        max = row.length;
      }
    }
    return max;
  }

  public static int getButtonHeight () {
    return Screen.dp(39f);
  }

  public static int getButtonSpacing () {
    return Screen.dp(4f);
  }

  private static int getButtonPadding () {
    return Screen.dp(4f);
  }

  private static int getStrokePadding () {
    return Math.round(Paints.getInlineButtonOuterPaint().getStrokeWidth() * .5f);
  }

  private int getSmallestDesiredWidth () {
    return getButtonPadding() * 4;
  }

  public int getWidth () {
    return maxWidth;
  }

  public int getHeight () {
    return keyboard != null ? keyboard.rows.length * getButtonHeight() + (keyboard.rows.length - 1) * getButtonSpacing() : 0;
  }

  public boolean isEmpty () {
    return keyboard == null;
  }

  private void buildLayout (int maxWidth, int retryWidth) {
    if (keyboard == null) {
      return;
    }

    this.maxWidth = maxWidth;

    final int buttonSpacing = getButtonSpacing();
    final int buttonPadding = getButtonPadding();
    float preferredMinWidth = 0f;

    int buttonCount = 0;
    final int preparedButtonCount = buttons.size();
    int buttonTextPadding = Screen.dp(12f);
    for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
      final int buttonWidth = (maxWidth - buttonSpacing * (row.length - 1)) / row.length;
      final int textWidth = Math.max(0, buttonWidth - buttonPadding * 2);
      for (TdApi.InlineKeyboardButton rawButton : row) {
        Button button;
        if (buttonCount >= preparedButtonCount) {
          button = new Button(this, parent, rawButton, textWidth);
          button.setViewProvider(viewProvider);
          buttons.add(button);
        } else {
          button = buttons.get(buttonCount);
          button.set(rawButton, textWidth);
        }
        float minWidth = button.getPreferredMinWidth();
        if (minWidth != 0) {
          preferredMinWidth = Math.max(preferredMinWidth, (minWidth + buttonPadding * 2) * row.length + buttonSpacing * (row.length - 1));
        }
        int minButtonWidth = button.wrapper.getMaxLineWidth() + buttonTextPadding * 2;
        if (buttonWidth < minButtonWidth) {
          preferredMinWidth = Math.max(preferredMinWidth, minButtonWidth * row.length + buttonSpacing * (row.length - 1));
        }
        buttonCount++;
      }
    }
    if (buttonCount < buttons.size() - 1) {
      for (int i = buttonCount; i < buttons.size(); i++) {
        buttons.remove(i);
      }
    }

    if (retryWidth != 0 && retryWidth > maxWidth && preferredMinWidth > maxWidth) {
      buildLayout((int) Math.min(preferredMinWidth, retryWidth), 0);
    }
  }

  private int lastStartX, lastStartY;

  public void draw (MessageView view, Canvas c, int startX, int startY) {
    this.lastStartX = startX;
    this.lastStartY = startY;

    final int buttonHeight = getButtonHeight();
    final int buttonSpacing = getButtonSpacing();
    final int strokePadding = getStrokePadding();

    if (isCustom) {
      buttons.get(0).draw(view, c, startX, startY, maxWidth, buttonHeight, strokePadding, rounder, 0, 0);
      return;
    }

    if (keyboard == null) {
      return;
    }

    int buttonRow = 0;
    int buttonIndex = 0;
    int cy = startY;
    for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
      int cx = startX;
      int buttonWidth = (maxWidth - buttonSpacing * (row.length - 1)) / row.length;
      int buttonColumn = 0;
      for (TdApi.InlineKeyboardButton ignored : row) {
        final Button button = buttons.get(buttonIndex);
        button.draw(view, c, cx, cy, buttonWidth, buttonHeight, strokePadding, rounder, buttonRow, buttonColumn);
        cx += buttonWidth + buttonSpacing;
        buttonIndex++;
        buttonColumn++;
      }
      cy += buttonSpacing + buttonHeight;
      buttonRow++;
    }
  }

  // Touch events

  private int activeIndex = -1;
  private int activeMaxWidth, activeX, activeY;

  public boolean onTouchEvent (View view, MotionEvent e) {
    if (buttons.isEmpty()) {
      return false;
    }

    int x = Math.round(e.getX() - lastStartX);
    int y = Math.round(e.getY() - lastStartY);

    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      activeIndex = findButtonByPosition(x, y);
      activeMaxWidth = maxWidth;
    }

    if (activeIndex != -1 && activeMaxWidth != maxWidth) {
      findXYForButton(activeIndex);
    }

    boolean result = activeIndex != -1 && activeIndex >= 0 && activeIndex < buttons.size() && buttons.get(activeIndex).onTouchEvent(view, e, Math.round(x - activeX), Math.round(y - activeY));
    if (activeIndex != -1 && (e.getAction() == MotionEvent.ACTION_CANCEL || e.getAction() == MotionEvent.ACTION_UP)) {
      activeIndex = -1;
    }
    return result;
  }

  public boolean performLongPress (View view) {
    boolean result = false;
    for (Button button : buttons) {
      if (button.performLongPress(view)) {
        result = true;
      }
    }
    return result;
  }

  private void findXYForButton (int index) {
    if (keyboard == null || index < 0 || index >= buttons.size()) {
      return;
    }

    final int buttonSpacing = getButtonSpacing();
    final int buttonHeight = getButtonHeight();
    int cy = 0;

    int i = 0;
    for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
      final int buttonWidth = (maxWidth - buttonSpacing * (row.length - 1)) / row.length;
      int cx = 0;
      for (TdApi.InlineKeyboardButton ignored : row) {
        if (i++ == index) {
          activeX = cx;
          activeY = cy;
          break;
        }
        cx += buttonWidth + buttonSpacing;
      }
      cy += buttonHeight + buttonSpacing;
    }

    activeX = -1;
    activeY = -1;
  }

  private int findButtonByPosition (int x, int y) {
    final int buttonSpacing = getButtonSpacing();
    final int buttonHeight = getButtonHeight();

    if (isCustom) {
      if (!buttons.isEmpty() && x >= 0 && x <= maxWidth && y >= 0 && y <= buttonHeight) {
        return 0;
      }
      return -1;
    }

    if (keyboard == null || x < 0 || y < 0) {
      return -1;
    }

    int cy = 0;
    int index = 0;

    for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
      if (y < cy) {
        return -1;
      }
      if (y > cy + buttonHeight) {
        cy += buttonHeight + buttonSpacing;
        index += row.length;
        continue;
      }

      final int buttonWidth = (maxWidth - buttonSpacing * (row.length - 1)) / row.length;
      int cx = 0;
      for (TdApi.InlineKeyboardButton ignored : row) {
        if (x < cx) {
          return -1;
        }
        if (x > cx + buttonWidth) {
          cx += buttonWidth + buttonSpacing;
          index++;
          continue;
        }
        activeX = cx;
        activeY = cy;
        return index;
      }

      return -1;
    }

    return -1;
  }

  // Button

  private static String cleanButtonText (String in) {
    int i = in.indexOf('\n');
    if (i != -1) {
      i = in.indexOf('\n', i + 1);
      if (i != -1) {
        return in.substring(0, i) + ' ' + in.substring(i + 1).replace('\n', ' ');
      }
    }
    return in;
  }

  public static class Button implements FactorAnimator.Target, DrawableProvider {
    private final @NonNull TGMessage parent;
    private final Path path;
    private final Rect dirtyRect;
    private EmojiString wrapper;
    private @Nullable TdApi.InlineKeyboardButtonType type;
    private TGInlineKeyboard context;
    private boolean needFakeBold;

    private @DrawableRes int customIconRes;
    private boolean isCustom;
    private String currencyChar;
    private float currencyCharWidth;

    public Button (TGInlineKeyboard context, @NonNull TGMessage parent, TdApi.InlineKeyboardButton button, int maxWidth) {
      this.context = context;
      this.parent = parent;
      this.path = new Path();
      this.dirtyRect = new Rect();
      String text = uppercase(cleanButtonText(button.text));
      this.needFakeBold = Text.needFakeBold(text);
      TextPaint textPaint = Paints.getBoldPaint14(needFakeBold);
      this.wrapper = new EmojiString(text, maxWidth, textPaint);
      this.type = button.type;
      if (type.getConstructor() == TdApi.InlineKeyboardButtonTypeBuy.CONSTRUCTOR) {
        currencyChar = CurrencyUtils.getCurrencyChar(((TdApi.MessageInvoice) parent.getMessage().content).currency);
        currencyCharWidth = U.measureText(currencyChar, Paints.getBoldTextPaint(CURRENCY_TEXT_SIZE_DP));
      }
    }

    public float getPreferredMinWidth () {
      return wrapper.getPreferredMinWidth();
    }

    public Button (TGInlineKeyboard context, @NonNull TGMessage parent, String text, @DrawableRes int iconRes, int maxWidth) {
      this.context = context;
      this.parent = parent;
      this.path = new Path();
      this.dirtyRect = new Rect();
      this.needFakeBold = Text.needFakeBold(text);
      TextPaint textPaint = Paints.getBoldPaint14(needFakeBold);
      this.wrapper = new EmojiString(text, maxWidth - (iconRes != 0 ? Screen.dp(24f) / 2 + Screen.dp(CUSTOM_ICON_PADDING) : 0), textPaint);
      this.customIconRes = iconRes;
      this.isCustom = true;
    }

    private String uppercase (String text) {
      return useWhiteMode() ? text : text.toUpperCase();
    }

    public void set (TdApi.InlineKeyboardButton button, int maxWidth) {
      this.type = button.type;
      String text = uppercase(cleanButtonText(button.text));
      final boolean reset = !wrapper.getText().equals(text);
      if (reset || wrapper.getMaxWidth() != maxWidth) {
        this.needFakeBold = Text.needFakeBold(text);
        TextPaint textPaint = Paints.getBoldPaint14(needFakeBold);
        this.wrapper = new EmojiString(uppercase(text), maxWidth, textPaint);
      }
      if (reset || !Td.equalsTo(type, button.type)) {
        if (contextId == Integer.MAX_VALUE) {
          contextId = 0;
        } else {
          contextId++;
        }
        if (currentTooltip != null) {
          currentTooltip.hideNow();
          currentTooltip = null;
        }
      }
      if (reset) {
        if (isActive()) {
          forceResetSelection();
        }
        forceHideProgress();
      }
    }

    private int row = -1, column = -1;

    private boolean useWhiteMode () {
      return context.context.useBubbles() && !isCustom;
    }

    private static final float CUSTOM_ICON_PADDING = 2f;

    public void draw (MessageView view, Canvas c, int cx, int cy, int buttonWidth, int buttonHeight, int strokePadding, RectF rounder, int row, int column) {
      final int right = cx + buttonWidth;
      final int bottom = cy + buttonHeight;
      final int radius = Screen.dp(context.context.useBubbles() && isCustom ? Theme.getBubbleMergeRadius() : 6f);

      rounder.left = cx + strokePadding; rounder.right = right - strokePadding;
      rounder.top = cy + strokePadding; rounder.bottom = bottom - strokePadding;

      boolean isOutBubble = context.context != null && context.context.isOutgoingBubble();

      if (this.row != -1 || this.column != -1) {
        forceResetSelection();
        forceHideProgress();
        this.row = row;
        this.column = column;
      }

      if (dirtyRect.left != cx || dirtyRect.right != right || dirtyRect.top != cy || dirtyRect.bottom != bottom) {
        dirtyRect.left = cx; dirtyRect.right = right;
        dirtyRect.top = cy; dirtyRect.bottom = bottom;
        path.reset();
        path.addRoundRect(rounder, radius, radius, Path.Direction.CCW);
        if (progress != null) {
          setProgressBounds();
        }
      }

      final boolean useBubbleMode = useWhiteMode();
      // float darkFactor = Theme.getDarkFactor();
      int inlineOutlineColor = Theme.inlineOutlineColor(isOutBubble);
      int fillingColor = 0;

      if (useBubbleMode) {
        c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(fillingColor = context.context.getBubbleButtonBackgroundColor()));
      } else {
        Paint paint = Paints.getInlineButtonOuterPaint();
        paint.setColor(inlineOutlineColor);
        c.drawRoundRect(rounder, radius, radius, paint);
      }

      //noinspection ConstantConditions
      float selectionColorFactor = ALLOW_INVERSE ? (ALLOW_ALWAYS_ACTIVE && isAlwaysActive() ? selectionFactor : activeFactor) : (ALLOW_ALWAYS_ACTIVE ? selectionFactor : 0f); // : Utils.color((int) (255f * (1f - fadeFactor)), selectionChanger.getColor(inverseFactor));
      int selectionColor = useBubbleMode ? context.context.getBubbleButtonRippleColor() : ColorUtils.fromToArgb(ColorUtils.color(0x1a, inlineOutlineColor), inlineOutlineColor, selectionColorFactor);
      if (fadeFactor != 0f) {
        selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
      }

      if (selectionFactor != 0f) {
        if (selectionFactor == 1f || path == null) {
          c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(selectionColor));
        } else {
          int anchorX = Math.max(Math.min(this.anchorX, buttonWidth), 0);
          int anchorY = Math.max(Math.min(this.anchorY, buttonHeight), 0);
          float selectionRadius = (float) Math.sqrt(buttonWidth * buttonWidth + buttonHeight * buttonHeight) * .5f * selectionFactor;
          float centerX = buttonWidth / 2;
          float centerY = buttonHeight / 2;
          float diffX = centerX - anchorX;
          float diffY = centerY - anchorY;
          float selectionX = cx + anchorX + diffX * selectionFactor;
          float selectionY = cy + anchorY + diffY * selectionFactor;

          final int saveCount;
          if ((saveCount = ViewSupport.clipPath(c, path)) != Integer.MIN_VALUE) {
            c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
          } else {
            c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(selectionColor));
          }
          ViewSupport.restoreClipPath(c, saveCount);
        }
      }

      //noinspection ConstantConditions
      final float textColorFactor = ALLOW_INVERSE ? (selectionFactor * activeFactor * (1f - fadeFactor)) : ALLOW_ALWAYS_ACTIVE ? selectionFactor * (1f - fadeFactor) : 0f;
      final int textColor = useBubbleMode ? context.context.getBubbleButtonTextColor() : ColorUtils.fromToArgb(Theme.inlineTextColor(isOutBubble), Theme.inlineTextActiveColor(), textColorFactor);

      int textX = cx + getButtonPadding();
      if (customIconRes != 0) {
        Drawable drawable = view.getSparseDrawable(customIconRes, ThemeColorId.NONE);
        int iconWidth = drawable.getMinimumWidth();
        int contentWidth = wrapper.getTextWidth();
        int totalWidth = iconWidth + contentWidth;
        int offset = Screen.dp(4f);
        int iconX;
        if (Lang.rtl()) {
          iconX = cx + buttonWidth / 2 + totalWidth / 2 + offset - iconWidth;
          textX -= iconWidth / 4 * 3 + offset - iconWidth;
        } else {
          iconX = cx + buttonWidth / 2 - totalWidth / 2 - offset;
          textX += iconWidth / 4 * 3 - offset;
        }

        Drawables.draw(c, drawable, iconX, cy + buttonHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(textColor));
      }
      Paints.getBoldPaint14(needFakeBold, Theme.inlineTextColor(isOutBubble));
      wrapper.draw(c, textX, cy + Screen.dp(12f), textColor, true);

      if (type != null) {
        int iconColor = Theme.inlineIconColor(isOutBubble);
        switch (type.getConstructor()) {
          case TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR:
          case TdApi.InlineKeyboardButtonTypeCallbackWithPassword.CONSTRUCTOR: {
            boolean isSwitchInline = type.getConstructor() == TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR;
            Drawable icon = getSparseDrawable(isSwitchInline ?
              R.drawable.baseline_alternate_email_12 :
              R.drawable.deproko_baseline_lock_16,
              ThemeColorId.NONE
            );
            int padding = Screen.dp(isSwitchInline ? 4f : 1f);
            Drawables.draw(c, icon, dirtyRect.right - icon.getMinimumWidth() - padding, dirtyRect.top + padding, useBubbleMode ?
              (progressFactor == 0f ? Paints.getInlineBubbleIconPaint(textColor) : Paints.getPorterDuffPaint(ColorUtils.alphaColor(1f - progressFactor, textColor))) :
              textColorFactor == 0f && progressFactor == 0f ? Paints.getInlineIconPorterDuffPaint(isOutBubble) : Paints.getPorterDuffPaint(ColorUtils.alphaColor(1f - progressFactor, ColorUtils.fromToArgb(iconColor, Theme.inlineTextActiveColor(), textColorFactor))));
            drawProgress(c, useBubbleMode, textColorFactor);
            break;
          }
          case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR: {
            Drawable icon = getSparseDrawable(R.drawable.deproko_baseline_link_arrow_20, ThemeColorId.NONE);
            Drawables.draw(c, icon, dirtyRect.right - icon.getMinimumWidth(), dirtyRect.top, useBubbleMode ? Paints.getInlineBubbleIconPaint(textColor) : textColorFactor == 0f ? Paints.getInlineIconPorterDuffPaint(isOutBubble) : Paints.getPorterDuffPaint(ColorUtils.fromToArgb(iconColor, Theme.inlineTextActiveColor(), textColorFactor)));
            break;
          }
          case TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR: {
            Drawable icon = getSparseDrawable(R.drawable.deproko_baseline_link_arrow_20, ThemeColorId.NONE);
            Drawables.draw(c, icon, dirtyRect.right - icon.getMinimumWidth(), dirtyRect.top, useBubbleMode ? Paints.getInlineBubbleIconPaint(ColorUtils.alphaColor(1f - progressFactor, textColor)) : textColorFactor == 0f && progressFactor == 1f ? Paints.getInlineIconPorterDuffPaint(isOutBubble) : Paints.getPorterDuffPaint(ColorUtils.alphaColor(1f - progressFactor, ColorUtils.fromToArgb(iconColor, Theme.inlineTextActiveColor(), textColorFactor))));
            drawProgress(c, useBubbleMode, textColorFactor);
            break;
          }
          case TdApi.InlineKeyboardButtonTypeBuy.CONSTRUCTOR: {
            if (!StringUtils.isEmpty(currencyChar)) {
              int color = ColorUtils.alphaColor(1f - progressFactor, useBubbleMode ? textColor : ColorUtils.fromToArgb(iconColor, Theme.inlineTextActiveColor(), textColorFactor));
              c.drawText(currencyChar, dirtyRect.right - Screen.dp(6f) - currencyCharWidth, dirtyRect.top + getStrokePadding() + Screen.dp(12f), Paints.getBoldTextPaint(CURRENCY_TEXT_SIZE_DP, color));
            }
            drawProgress(c, useBubbleMode, textColorFactor);
            break;
          }
          case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR:
          case TdApi.InlineKeyboardButtonTypeCallbackGame.CONSTRUCTOR: {
            drawProgress(c, useBubbleMode, textColorFactor);
            break;
          }
        }
      } else {
        // TODO
      }
    }

    private void drawProgress (Canvas c, boolean useBubbleMode, float textColorFactor) {
      if (progress != null) {
        final int color = useBubbleMode ?  context.context.getBubbleButtonTextColor() : ColorUtils.fromToArgb(Theme.inlineIconColor(context.context != null && context.context.isOutgoingBubble()), Theme.inlineTextActiveColor(), textColorFactor);
        final int progressColor = ColorUtils.color((int) ((float) Color.alpha(color) * progressFactor), color);
        progress.forceColor(progressColor);
        progress.draw(c);
      }
    }

    // View

    private @Nullable
    ViewProvider viewProvider;

    public void setViewProvider (@Nullable ViewProvider viewProvider) {
      this.viewProvider = viewProvider;
      final boolean isBlocked = viewProvider == null;
      if (fadeAnimator != null) {
        fadeAnimator.setIsBlocked(isBlocked);
      }
      if (activeAnimator != null) {
        activeAnimator.setIsBlocked(isBlocked);
      }
      if (selectionAnimator != null) {
        selectionAnimator.setIsBlocked(isBlocked);
      }
      if (progressAnimator != null) {
        progressAnimator.setIsBlocked(isBlocked);
      }
      if (progress != null) {
        progress.setViewProvider(viewProvider);
      }
    }

    private void invalidate () {
      if (viewProvider != null) {
        viewProvider.invalidate(dirtyRect);
      }
    }

    // Touch events

    private static final int FLAG_ACTIVE = 0x01;
    private static final int FLAG_CAUGHT = 0x02;
    private static final int FLAG_BLOCKED = 0x04;

    private int flags;
    private int anchorX, anchorY;
    private int contextId;

    private boolean isActive () {
      return (flags & FLAG_ACTIVE) != 0;
    }

    private boolean isCaught () {
      return (flags & FLAG_CAUGHT) != 0;
    }

    private boolean isBlocked () {
      return (flags & FLAG_BLOCKED) != 0;
    }

    public boolean onTouchEvent (View view, MotionEvent e, int x, int y) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          flags |= FLAG_CAUGHT;
          anchorX = x; anchorY = y;
          if (!isActive() && !isBlocked()) {
            animateSelectionFactor(1f);
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          anchorX = x; anchorY = y;
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (isCaught()) {
            flags &= ~FLAG_CAUGHT;
            if (!isActive() && !isBlocked()) {
              cancelSelection();
            }
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          anchorX = x; anchorY = y;
          if (isCaught()) {
            flags &= ~FLAG_CAUGHT;
            ViewUtils.onClick(view);
            performClick(view);
            return true;
          }
          return false;
        }
      }

      return true;
    }

    public void performClick (View view) {
      if (!isBlocked()) {
        performAction(view, true);
        if (!ALLOW_INVERSE || !isActive()) {
          cancelSelection();
        }
      }
    }

    private void cancelSelection () {
      animateFadeFactor(1f);
    }

    private void forceResetSelection () {
      if (fadeAnimator != null) {
        fadeAnimator.forceFactor(this.fadeFactor = 0f);
        flags &= ~FLAG_BLOCKED;
      }
      if (selectionAnimator != null) {
        selectionAnimator.forceFactor(this.selectionFactor = 0f);
      }
      if (activeAnimator != null) {
        activeAnimator.forceFactor(this.activeFactor = 0f);
        flags &= ~FLAG_ACTIVE;
      }
    }

    private TooltipOverlayView.TooltipInfo currentTooltip;

    private TooltipOverlayView.TooltipBuilder tooltip (View view) {
      return parent.context().tooltipManager().builder(view, parent.currentViews).onBuild(tooltip -> currentTooltip = tooltip).locate((targetView, outRect) -> outRect.set(dirtyRect));
    }

    public boolean performLongPress (View view) {
      if ((flags & FLAG_CAUGHT) != 0) {
        flags &= ~FLAG_CAUGHT;
        if (!isActive()) {
          cancelSelection();
          if (type != null) {
            switch (type.getConstructor()) {
              case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR: {
                ViewController<?> c = parent.context().navigation().getCurrentStackItem();
                if (c != null) {
                  c.showCopyUrlOptions(((TdApi.InlineKeyboardButtonTypeUrl) type).url, openParameters(contextId, view), null);
                  return true;
                }
                break;
              }
              case TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR:
                ViewController<?> c = parent.context().navigation().getCurrentStackItem();
                if (c != null) {
                  c.showCopyUrlOptions(((TdApi.InlineKeyboardButtonTypeLoginUrl) type).url, openParameters(contextId, view), () -> {
                    performAction(view, false);
                    return true;
                  });
                  return true;
                }
                break;
            }
          }
        }
      }
      return false;
    }

    // Animations

    private static final long ANIMATION_DURATION = 180l;

    private static final int SELECTION_ANIMATOR = 0;
    private static final int ACTIVE_ANIMATOR = 1;
    private static final int FADE_ANIMATOR = 2;
    private static final int PROGRESS_ANIMATOR = 3;

    private float activeFactor;
    private FactorAnimator activeAnimator;

    private void animateActiveFactor (float toFactor) {
      if (toFactor == 0f) {
        flags &= ~FLAG_ACTIVE;
      } else if (toFactor == 1f) {
        flags |= FLAG_ACTIVE;
      }
      if (activeAnimator == null) {
        activeAnimator = new FactorAnimator(ACTIVE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        activeAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      activeAnimator.animateTo(toFactor);
    }

    private float selectionFactor;
    private FactorAnimator selectionAnimator;

    private void animateSelectionFactor (float toFactor) {
      if (selectionAnimator == null) {
        selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        selectionAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      selectionAnimator.animateTo(toFactor);
    }

    private float fadeFactor;
    private FactorAnimator fadeAnimator;

    private void animateFadeFactor (float toFactor) {
      if (toFactor == 1f) {
        flags &= ~FLAG_ACTIVE;
      }
      if (fadeAnimator == null) {
        fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        fadeAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      flags |= FLAG_BLOCKED;
      fadeAnimator.animateTo(toFactor);
    }

    private float progressFactor;
    private @Nullable ProgressComponent progress;
    private FactorAnimator progressAnimator;

    private void setProgressBounds () {
      if (progress != null) {
        progress.setBounds(dirtyRect.right - Screen.dp(16f), dirtyRect.top, dirtyRect.right, dirtyRect.top + Screen.dp(16f));
      }
    }

    private void animateProgressFactor (float toFactor) {
      if (progress == null) {
        progress = new ProgressComponent(context.context.context(), Screen.dp(3.5f));
        progress.setViewProvider(viewProvider);
        setProgressBounds();
      }
      if (progressAnimator == null) {
        progressAnimator = new FactorAnimator(PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        progressAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      progressAnimator.animateTo(toFactor);
    }

    private CancellableRunnable progressDelayed;

    public void showProgressDelayed () {
      cancelDelayedProgress();
      if (viewProvider != null && viewProvider.hasAnyTargetToInvalidate()) {
        progressDelayed = new CancellableRunnable() {
          @Override
          public void act () {
            animateProgressFactor(1f);
          }
        };
        UI.getProgressHandler().postDelayed(progressDelayed, 250l);
      } else {
        animateProgressFactor(1f);
      }
    }

    private void cancelDelayedProgress () {
      if (progressDelayed != null) {
        progressDelayed.cancel();
        progressDelayed = null;
      }
    }

    public void hideProgress () {
      cancelDelayedProgress();
      animateProgressFactor(0f);
    }

    private void forceHideProgress () {
      cancelDelayedProgress();
      if (progressAnimator != null) {
        progressAnimator.forceFactor(this.progressFactor = 0f);
      }
    }

    // Animation

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case SELECTION_ANIMATOR: {
          this.selectionFactor = factor;
          break;
        }
        case ACTIVE_ANIMATOR: {
          this.activeFactor = factor;
          break;
        }
        case FADE_ANIMATOR: {
          this.fadeFactor = factor;
          break;
        }
        case PROGRESS_ANIMATOR: {
          this.progressFactor = factor;
          break;
        }
      }
      invalidate();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == FADE_ANIMATOR) {
        if (finalFactor == 1f) {
          forceResetSelection();
        }
      }
    }

    // Handlers

    private static final boolean ALLOW_ALWAYS_ACTIVE = true;
    private static final boolean ALLOW_INVERSE = false;

    private boolean isAlwaysActive () {
      return type != null && (type.getConstructor() == TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR || type.getConstructor() == TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR || type.getConstructor() == TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR);
    }

    public void makeActive () {
      if (ALLOW_INVERSE) {
        animateActiveFactor(1f);
      } else {
        flags |= FLAG_ACTIVE;
      }
    }

    public void makeInactive () {
      cancelSelection();
      hideProgress();
    }

    public int getContextId () {
      return contextId;
    }

    private ClickListener clickListener;

    public void setClickListener (ClickListener clickListener) {
      this.clickListener = clickListener;
    }

    public TdlibUi.UrlOpenParameters openParameters (View view) {
      return openParameters(this.contextId, view);
    }

    private TdlibUi.UrlOpenParameters openParameters (int contextId, View view) {
      return parent.openParameters().tooltip(this.contextId == contextId ? tooltip(view) : null);
    }

    private void openUrl (int contextId, View view, String url, boolean needVerify) {
      ViewController<?> c = parent.context().navigation().getCurrentStackItem();
      if (c != null) {
        if (needVerify) {
          c.openLinkAlert(url, openParameters(contextId, view));
        } else {
          context.context.tdlib().ui().openUrl(context.context.controller(), url, openParameters(contextId, view));
        }
      }
    }

    public void showTooltip (View view, int stringRes) {
      showTooltip(view, Lang.getString(stringRes));
    }

    public TooltipOverlayView.TooltipBuilder tooltipBuilder (View view) {
      return parent.context().tooltipManager().builder(view, viewProvider).controller(parent.controller()).locate((targetView, rect) -> rect.set(dirtyRect));
    }

    public void showTooltip (View view, CharSequence text) {
      tooltipBuilder(view).show(parent.tdlib(), text).hideDelayed();
    }

    private void performAction (View view, boolean needVerify) {
      if (isActive()) {
        return;
      }

      if (isCustom) {
        if (clickListener != null) {
          clickListener.onClick(view, context, this);
        }
        return;
      }

      if (type == null) {
        return;
      }

      if (parent.isScheduled() && type.getConstructor() != TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR) {
        showTooltip(view, R.string.ErrorScheduled);
        return;
      }

      final int currentContextId = this.contextId;

      switch (type.getConstructor()) {
        case TdApi.InlineKeyboardButtonTypeBuy.CONSTRUCTOR: {
          // TODO
          break;
        }

        case TdApi.InlineKeyboardButtonTypeCallbackWithPassword.CONSTRUCTOR: {
          final TdApi.InlineKeyboardButtonTypeCallbackWithPassword callbackWithPassword = (TdApi.InlineKeyboardButtonTypeCallbackWithPassword) type;
          final byte[] data = callbackWithPassword.data;
          final boolean isBotTransfer = Td.isBotOwnershipTransfer(callbackWithPassword) && context.context.tdlib().isBotFatherChat(parent.getChatId());

          makeActive();
          showProgressDelayed();

          RunnableData<CharSequence> act = alertText -> {
            context.context.tdlib.ui().requestTransferOwnership(context.context.messagesController(), alertText, new TdlibUi.OwnershipTransferListener() {
              @Override
              public void onOwnershipTransferAbilityChecked (TdApi.Object result) {
                if (currentContextId == contextId) {
                  makeInactive();
                }
              }

              @Override
              public void onOwnershipTransferConfirmed (String password) {
                if (currentContextId == contextId) {
                  makeActive();
                  cancelDelayedProgress();
                  animateProgressFactor(1f);
                }
                context.context.tdlib.client().send(new TdApi.GetCallbackQueryAnswer(parent.getChatId(), context.messageId, new TdApi.CallbackQueryPayloadDataWithPassword(password, data)), getAnswerCallback(currentContextId, view,false));
              }
            });
          };

          if (isBotTransfer) {
            TD.BotTransferInfo transferInfo = TD.parseBotTransferInfo(callbackWithPassword);
            if (transferInfo != null) {
              AtomicInteger remaining = new AtomicInteger(2);
              Client.ResultHandler handler = ignored -> {
                if (remaining.decrementAndGet() == 0) {
                  TdApi.User botUser = context.context.tdlib.cache().user(transferInfo.botUserId);
                  TdApi.User targetUser = context.context.tdlib.cache().user(transferInfo.targetOwnerUserId);

                  context.context.tdlib.ui().post(() -> {
                    if (currentContextId == contextId) {
                      CharSequence alertText;
                      if (botUser != null && targetUser != null) {
                        alertText = Lang.getMarkdownString(context.context.messagesController(),
                          R.string.TransferOwnershipAlertBotName,
                          (target, argStart, argEnd, argIndex, needFakeBold) -> Lang.newUserSpan(context.context.controller(), argIndex == 0 ? transferInfo.botUserId : transferInfo.targetOwnerUserId),
                          context.context.tdlib.cache().userName(transferInfo.botUserId),
                          context.context.tdlib.cache().userName(transferInfo.targetOwnerUserId)
                        );
                      } else {
                        alertText = Lang.getMarkdownString(context.context.messagesController(),
                          R.string.TransferOwnershipAlertBot
                        );
                      }
                      act.runWithData(alertText);
                    }
                  });
                }
              };
              context.context.tdlib.client().send(new TdApi.GetUser(transferInfo.botUserId), handler);
              context.context.tdlib.client().send(new TdApi.GetUser(transferInfo.targetOwnerUserId), handler);
            } else {
              act.runWithData(Lang.getMarkdownString(context.context.messagesController(),
                R.string.TransferOwnershipAlertBot
              ));
            }
          } else {
            act.runWithData(Lang.getMarkdownString(context.context.messagesController(),
              R.string.TransferOwnershipAlertUnknown
            ));
          }

          break;
        }
        case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR: {
          makeActive();
          showProgressDelayed();

          final byte[] data = ((TdApi.InlineKeyboardButtonTypeCallback) type).data;
          context.context.tdlib().client().send(new TdApi.GetCallbackQueryAnswer(parent.getChatId(), context.messageId, new TdApi.CallbackQueryPayloadData(data)), getAnswerCallback(currentContextId, view,false));

          break;
        }
        case TdApi.InlineKeyboardButtonTypeCallbackGame.CONSTRUCTOR: {
          if (parent.getMessage().content.getConstructor() != TdApi.MessageGame.CONSTRUCTOR) {
            break;
          }

          makeActive();
          showProgressDelayed();

          final String data = ((TdApi.MessageGame) parent.getMessage().content).game.shortName;
          context.context.tdlib().client().send(new TdApi.GetCallbackQueryAnswer(parent.getChatId(), context.messageId, new TdApi.CallbackQueryPayloadGame(data)), getAnswerCallback(currentContextId, view, true));
          break;
        }
        case TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR: {
          final TdApi.InlineKeyboardButtonTypeSwitchInline switchInline = (TdApi.InlineKeyboardButtonTypeSwitchInline) type;

          flags |= FLAG_BLOCKED;
          UI.post(() -> {
            flags &= ~FLAG_BLOCKED;
            ViewController<?> c = parent.context().navigation().getCurrentStackItem();
            if (c instanceof MessagesController) {
              TdApi.Message msg = parent.getMessage();
              ((MessagesController) c).switchInline(msg.viaBotUserId != 0 ? msg.viaBotUserId : Td.getSenderUserId(msg), switchInline);
            }
          }, ANIMATION_DURATION / 2);
          break;
        }
        case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR: {
          final String url = ((TdApi.InlineKeyboardButtonTypeUrl) type).url;
          flags |= FLAG_BLOCKED;
          UI.post(() -> {
            flags &= ~FLAG_BLOCKED;
            openUrl(currentContextId, view, url, needVerify);
          }, ANIMATION_DURATION / 2);
          break;
        }
        case TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR: {
          makeActive();
          showProgressDelayed();

          TdApi.InlineKeyboardButtonTypeLoginUrl button = (TdApi.InlineKeyboardButtonTypeLoginUrl) type;
          context.context.tdlib().client().send(new TdApi.GetLoginUrlInfo(context.context.getChatId(), context.messageId, button.id), getLoginCallback(currentContextId, view, button, needVerify));
          break;
        }
      }
    }

    private Client.ResultHandler getLoginCallback (final int currentContextId, final View view, final TdApi.InlineKeyboardButtonTypeLoginUrl button, final boolean needVerify) {
      return object -> UI.post(() -> {
        if (currentContextId == contextId) {
          makeInactive();
        }

        if (parent.isDestroyed()) {
          return;
        }

        ViewController<?> c = parent.context().navigation().getCurrentStackItem();
        if (!(c instanceof MessagesController) || c.getChatId() != parent.getChatId()) {
          return;
        }

        switch (object.getConstructor()) {
          case TdApi.LoginUrlInfoOpen.CONSTRUCTOR: {
            TdApi.LoginUrlInfoOpen open = (TdApi.LoginUrlInfoOpen) object;
            if (open.skipConfirm) {
              context.context.tdlib().ui().openUrl(context.context.controller(), open.url, openParameters(currentContextId, view).disableInstantView());
            } else {
              context.context.controller().openLinkAlert(open.url, openParameters(currentContextId, view).disableInstantView());
            }
            break;
          }
          case TdApi.LoginUrlInfoRequestConfirmation.CONSTRUCTOR:
            TdApi.LoginUrlInfoRequestConfirmation confirm = (TdApi.LoginUrlInfoRequestConfirmation) object;
            List<ListItem> items = new ArrayList<>();
            items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE,
              R.id.btn_signIn, 0,
              Lang.getString(R.string.LogInAsOn,
                (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 1 ?
                  new CustomTypefaceSpan(null, R.id.theme_color_textLink) :
                  Lang.newBoldSpan(needFakeBold),
                context.context.tdlib().accountName(),
                confirm.domain),
              true
            ));
            if (confirm.requestWriteAccess) {
              items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_MULTILINE,
                R.id.btn_allowWriteAccess,
                0,
                Lang.getString(R.string.AllowWriteAccess, Lang.boldCreator(), context.context.tdlib().cache().userName(confirm.botUserId)),
                true
              ));
            }
            context.context.controller().showSettings(
              new SettingsWrapBuilder(R.id.btn_open)
              .addHeaderItem(Lang.getString(R.string.OpenLinkConfirm, (target, argStart, argEnd, spanIndex, needFakeBold) -> new CustomTypefaceSpan(null, R.id.theme_color_textLink), confirm.url))
              .setRawItems(items)
              .setIntDelegate((id, result) -> {
                boolean needSignIn = items.get(0).isSelected();
                boolean needWriteAccess = items.size() > 1 && items.get(1).isSelected();
                if (needSignIn) {
                  makeActive();
                  showProgressDelayed();
                  context.context.tdlib().client().send(new TdApi.GetLoginUrl(parent.getChatId(), context.messageId, button.id, needWriteAccess), getLoginUrlCallback(currentContextId, view, button, needVerify));
                } else {
                  openUrl(currentContextId, view, button.url, false);
                }
              })
                .setSettingProcessor((item, itemView, isUpdate) -> {
                  switch (item.getViewType()) {
                    case ListItem.TYPE_CHECKBOX_OPTION:
                    case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
                    case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
                      ((CheckBox) itemView.getChildAt(0)).setChecked(item.isSelected(), isUpdate);
                      break;
                  }
                })
              .setOnSettingItemClick(confirm.requestWriteAccess ? (ViewController.OnSettingItemClick) (itemView, settingsId, item, doneButton, settingsAdapter) -> {
                switch (item.getId()) {
                  case R.id.btn_signIn: {
                    boolean needSignIn = settingsAdapter.getCheckIntResults().get(R.id.btn_signIn) == R.id.btn_signIn;
                    if (!needSignIn) {
                      // settingsAdapter.setToggledById(R.id.btn_allowWriteAccess, false);
                      items.get(1).setSelected(false);
                      settingsAdapter.updateValuedSettingById(R.id.btn_allowWriteAccess);
                    }
                    break;
                  }
                  case R.id.btn_allowWriteAccess: {
                    boolean needWriteAccess = settingsAdapter.getCheckIntResults().get(R.id.btn_allowWriteAccess) == R.id.btn_allowWriteAccess;
                    if (needWriteAccess) {
                      // settingsAdapter.setToggledById(R.id.btn_signIn, true);
                      items.get(0).setSelected(true);
                      settingsAdapter.updateValuedSettingById(R.id.btn_signIn);
                    }
                    break;
                  }
                }
              } : null)
              .setSaveStr(R.string.Open)
              .setRawItems(items)
            );
            break;
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            openUrl(currentContextId, view, button.url, needVerify);
            break;
          }
        }
      });
    }

    private Client.ResultHandler getLoginUrlCallback (final int currentContextId, final View view, final TdApi.InlineKeyboardButtonTypeLoginUrl button, final boolean needVerify) {
      return object -> UI.post(() -> {
        // TODO unify this piece of code into the one
        if (currentContextId == contextId) {
          makeInactive();
        }

        if (parent.isDestroyed()) {
          return;
        }

        ViewController<?> c = parent.context().navigation().getCurrentStackItem();
        if (!(c instanceof MessagesController) || c.getChatId() != parent.getChatId()) {
          return;
        }

        switch (object.getConstructor()) {
          case TdApi.HttpUrl.CONSTRUCTOR: {
            String url = ((TdApi.HttpUrl) object).url;
            context.context.tdlib().ui().openUrl(context.context.controller(), url, openParameters(currentContextId, view).disableInstantView());
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            openUrl(currentContextId, view, button.url, needVerify);
            break;
          }
        }
      });
    }

    private Client.ResultHandler getAnswerCallback (final int currentContextId, final View view, final boolean isGame) {
      return object -> {
        switch (object.getConstructor()) {
          case TdApi.CallbackQueryAnswer.CONSTRUCTOR: {
            final TdApi.CallbackQueryAnswer answer = (TdApi.CallbackQueryAnswer) object;

            final CharSequence answerText;
            if (answer.text.isEmpty()) {
              answerText = null;
            } else {
              answerText = Emoji.instance().replaceEmoji(answer.text);
            }

            final boolean showAlert = answer.showAlert;
            final String url = answer.url;

            context.context.tdlib().ui().post(() -> {
              if (currentContextId == contextId) {
                makeInactive();
              }

              if (parent.isDestroyed()) {
                return;
              }

              ViewController<?> c = parent.context().navigation().getCurrentStackItem();
              if (!(c instanceof MessagesController) || c.getChatId() != parent.getChatId()) {
                return;
              }

              if (!StringUtils.isEmpty(url)) {
                if (isGame) {
                  TdApi.Message msg = parent.getMessage();
                  ((MessagesController) c).openGame(msg.viaBotUserId != 0 ? msg.viaBotUserId : Td.getSenderUserId(msg), ((TdApi.MessageGame) msg.content).game, url, msg);
                } else {
                  c.openLinkAlert(url, openParameters(currentContextId, view));
                }
              }
              if (answerText != null) {
                if (showAlert) {
                  c.openOkAlert(context.context.tdlib().messageUsername(parent.getMessage()), answerText);
                } else {
                  ((MessagesController) c).showCallbackToast(answerText);
                }
              }
            });

            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if (error.code == 502) {
              UI.showBotDown(context.context.tdlib().messageUsername(parent.getMessage()));
              return;
            }
            UI.showError(object);
            context.context.tdlib().ui().post(() -> {
              if (currentContextId == contextId) {
                makeInactive();
              }
            });
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.GetCallbackQueryAnswer.class, TdApi.CallbackQueryAnswer.class);
            context.context.tdlib().ui().post(() -> {
              if (currentContextId == contextId) {
                makeInactive();
              }
            });
            break;
          }
        }
      };
    }

    // Sparse drawable function
    private SparseArrayCompat<Drawable> sparseDrawables;
    @Override
    public final SparseArrayCompat<Drawable> getSparseDrawableHolder () { return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>())); }
    @Override
    public final Resources getSparseDrawableResources () { return UI.getResources(); }
  }
}
