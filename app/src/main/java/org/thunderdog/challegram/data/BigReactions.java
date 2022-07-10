package org.thunderdog.challegram.data;

import static me.vkryl.android.AnimatorUtils.DECELERATE_INTERPOLATOR;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.Gravity;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.animator.ListAnimator;
import org.thunderdog.challegram.loader.ReactionsReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;

public final class BigReactions implements Counter.Callback {
  @Dimension(unit = Dimension.DP)
  private static final float REACTION_SIZE = 16f;
  @Dimension(unit = Dimension.DP)
  private static final float BUTTON_HEIGHT = 28f;
  @Dimension(unit = Dimension.DP)
  private static final float BUTTON_SPACING = 3f;

  @Nullable
  private List<Button> buttonList;
  private final ListAnimator<Button> buttonListAnimator;

  @NonNull
  private final Runnable callback;
  @NonNull
  private final ColorSet colorSet;
  @NonNull
  private final TextColorSet textColorSet;

  @Px
  private final int buttonSpacing, extraVerticalSpacing, extraHorizontalSpacing;

  @Px
  private int lastX, lastY, lastMaxWidth, lastMinItemsInRow;
  private boolean lastAnimated;

  public BigReactions (@NonNull ColorSet colorSet, @NonNull Runnable callback) {
    this.callback = callback;
    this.buttonList = new ArrayList<>();
    this.buttonListAnimator = new ListAnimator<>(animator -> callback.run(), DECELERATE_INTERPOLATOR, 200L);

    this.buttonSpacing = Screen.dp(BUTTON_SPACING);
    this.extraVerticalSpacing = buttonSpacing * 2;
    this.extraHorizontalSpacing = buttonSpacing * 2;

    this.colorSet = colorSet;
    this.textColorSet = toTextColorSet(colorSet);
  }

  public void setMessageReactions (@Nullable TdApi.MessageReaction[] messageReactions, boolean animated) {
    if (messageReactions != null && messageReactions.length > 0) {
      List<Button> newButtonList = new ArrayList<>(messageReactions.length);
      for (TdApi.MessageReaction messageReaction : messageReactions) {
        Button foundButton = null;
        if (buttonList != null) {
          for (Button button : buttonList) {
            if (button.reaction.reaction.equals(messageReaction.reaction)) {
              foundButton = button;
              break;
            }
          }
        }
        Button button = foundButton != null ? foundButton : new Button(this, textColorSet, buttonSpacing);
        button.setReaction(messageReaction, foundButton != null && animated);
        newButtonList.add(button);
      }
      int maxWidth = lastMaxWidth > 0 ? lastMaxWidth + extraHorizontalSpacing : Screen.currentWidth();
      buttonList = newButtonList;
      buttonListAnimator.reset(newButtonList, animated, null, maxWidth, lastMinItemsInRow, Integer.MAX_VALUE);
    } else {
      buttonList = null;
      buttonListAnimator.reset(null, animated);
    }
  }

  @Px
  public int getWidth () {
    int totalWidth = Math.round(buttonListAnimator.getMetadata().getTotalWidth());
    return Math.max(totalWidth - extraHorizontalSpacing, 0);
  }

  @Px
  public int getHeight () {
    int totalHeight = Math.round(buttonListAnimator.getMetadata().getTotalHeight());
    return Math.max(totalHeight - extraVerticalSpacing, 0);
  }

  @Px
  public int getLastLineWidth () {
    int lastLineWidth = Math.round(buttonListAnimator.getMetadata().getLastLineWidth());
    return Math.max(lastLineWidth - extraHorizontalSpacing, 0);
  }

  @Px
  public int getLastLineHeight () {
    int lastLineHeight = Math.round(buttonListAnimator.getMetadata().getLastLineHeight());
    return Math.max(lastLineHeight - extraVerticalSpacing, 0);
  }

  // minItemsInRow is stronger than maxWidth
  public void measure (@Px int maxWidth, int minItemsInRow, boolean force, boolean animated) {
    boolean sizeChanged = lastMaxWidth != maxWidth || lastMinItemsInRow != minItemsInRow;
    lastAnimated = animated;
    lastMaxWidth = maxWidth;
    lastMinItemsInRow = minItemsInRow;
    if (sizeChanged || force) {
      buttonListAnimator.measure(animated, maxWidth + extraHorizontalSpacing, minItemsInRow, Integer.MAX_VALUE);
    }
  }

  @Nullable
  public TdApi.MessageReaction findReactionAt (float x, float y) {
    Button button = findButtonAt(x, y);
    return button != null ? button.reaction : null;
  }

  @Nullable
  private Button findButtonAt (float x, float y) {
    x -= lastX;
    y -= lastY;
    if (x < 0 || x > getWidth() || y < 0 || y > getHeight()) {
      return null;
    }
    for (ListAnimator.Entry<Button> entry : buttonListAnimator) {
      if (entry.isJunk()) {
        continue;
      }
      RectF buttonRect = entry.getRectF();
      buttonRect.offset(-buttonSpacing, -buttonSpacing);
      buttonRect.inset(buttonSpacing, buttonSpacing);
      if (buttonRect.contains(x, y)) {
        return entry.item;
      }
    }
    return null;
  }

  public void draw (@NonNull Canvas canvas, @Px int x, @Px int y, @NonNull ReactionsReceiver reactionsReceiver) {
    lastX = x;
    lastY = y;

    int iconSize = Screen.dp(REACTION_SIZE);

    for (ListAnimator.Entry<Button> entry : buttonListAnimator) {
      Button button = entry.item;
      RectF buttonRect = entry.getRectF();
      buttonRect.offset(x - buttonSpacing, y - buttonSpacing);
      buttonRect.inset(buttonSpacing, buttonSpacing);
      float buttonHeight = buttonRect.height();
      float buttonRadius = buttonHeight / 2f;

      float muteFactor = button.counter.getMuteFactor();
      int backgroundColor = ColorUtils.fromToArgb(colorSet.defaultBackgroundColor(), colorSet.selectedBackgroundColor(), muteFactor);
      int fillingColor = ColorUtils.alphaColor(entry.getVisibility(), backgroundColor);
      canvas.drawRoundRect(buttonRect, buttonRadius, buttonRadius, Paints.fillingPaint(fillingColor));

      button.iconLeft = Math.round(buttonRect.left) + Screen.dp(6f);
      button.iconTop = Math.round(buttonRect.centerY() - iconSize / 2f);
      button.iconRight = button.iconLeft + iconSize;
      button.iconBottom = button.iconTop + iconSize;

      String reaction = button.reaction.reaction;

      boolean isCenterAnimationPlaying = false;
      GifReceiver centerAnimation = reactionsReceiver.getCenterAnimationReceiver(reaction);
      if (centerAnimation != null) {
        GifFile centerAnimationFile = centerAnimation.getCurrentFile();
        if (centerAnimationFile != null && !centerAnimationFile.hasLooped()) {
          isCenterAnimationPlaying = true;
          
          int saveCount = canvas.save();

          canvas.translate(button.iconLeft - iconSize / 2f, button.iconTop - iconSize / 2f);
          centerAnimation.setBounds(0, 0, iconSize * 2, iconSize * 2);
          centerAnimation.setAlpha(entry.getVisibility());
          centerAnimation.draw(canvas);

          canvas.restoreToCount(saveCount);
        }
      }

      if (!isCenterAnimationPlaying) {
        Receiver staticIcon = reactionsReceiver.getStaticIconReceiver(reaction);
        staticIcon.setBounds(button.iconLeft, button.iconTop, button.iconRight, button.iconBottom);
        staticIcon.setAlpha(entry.getVisibility());
        staticIcon.draw(canvas);
      }

      float counterX = button.iconRight + Screen.dp(5);
      float counterY = buttonRect.centerY();
      button.counter.draw(canvas, counterX, counterY, Gravity.LEFT, entry.getVisibility());
    }

    for (ListAnimator.Entry<Button> entry : buttonListAnimator) {
      Button button = entry.item;
      String reaction = button.reaction.reaction;
      GifReceiver aroundAnimation = reactionsReceiver.getAroundAnimationReceiver(reaction);
      if (aroundAnimation != null) {
        GifFile aroundAnimationFile = aroundAnimation.getCurrentFile();
        if (aroundAnimationFile != null && !aroundAnimationFile.hasLooped()) {
          int saveCount = canvas.save();

          canvas.translate(button.iconLeft - iconSize / 2f, button.iconTop - iconSize / 2f);
          aroundAnimation.setBounds(0, 0, iconSize * 2, iconSize * 2);
          aroundAnimation.setAlpha(entry.getVisibility());
          aroundAnimation.draw(canvas);

          canvas.restoreToCount(saveCount);
        }
      }
    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (sizeChanged) {
      measure(lastMaxWidth, lastMinItemsInRow, true, lastAnimated);
    } else {
      callback.run();
    }
  }

  @NonNull
  private static TextColorSet toTextColorSet (@NonNull ColorSet colorSet) {
    return new TextColorSet() {
      @Override
      public int defaultTextColor () {
        return colorSet.defaultTextColor();
      }

      @Override
      public int mutedTextColor () {
        return colorSet.selectedTextColor();
      }
    };
  }

  public interface ColorSet {
    @ColorInt
    int defaultTextColor ();

    @ColorInt
    int selectedTextColor ();

    @ColorInt
    int defaultBackgroundColor ();

    @ColorInt
    int selectedBackgroundColor ();
  }

  private static final class Button implements ListAnimator.Measurable {
    int iconLeft, iconTop, iconRight, iconBottom;

    @NonNull
    final Counter counter;
    @Px
    private int spacing;

    TdApi.MessageReaction reaction;

    private Button (@NonNull Counter.Callback counterCallback,
                    @NonNull TextColorSet textColorSet,
                    @Px int spacing) {
      this.counter = new Counter.Builder()
        .textSize(11f)
        .allBold(true)
        .noBackground()
        .colorSet(textColorSet)
        .callback(counterCallback)
        .build();
      this.spacing = spacing;
    }

    public void setSpacing (@Px int spacing) {
      this.spacing = spacing;
    }

    public void setReaction (@NonNull TdApi.MessageReaction reaction, boolean animated) {
      this.reaction = reaction;
      this.counter.setCount(reaction.totalCount, reaction.isChosen, animated);
    }

    @Override
    public int getWidth () {
      int counterWidth = Math.round(counter.getScaledWidth(0));
      int reactionWidth = Screen.dp(REACTION_SIZE);
      return counterWidth + reactionWidth + Screen.dp(6f) + Screen.dp(5f) + Screen.dp(8f);
    }

    @Override
    public int getHeight () {
      return Screen.dp(BUTTON_HEIGHT);
    }

    @Override
    public int getSpacingStart (boolean isFirst) {
      return spacing;
    }

    @Override
    public int getSpacingEnd (boolean isLast) {
      return spacing;
    }

    @Override
    public boolean equals (Object other) {
      return other instanceof Button && ((Button) other).reaction.reaction.equals(reaction.reaction);
    }

    @Override
    public int hashCode () {
      return reaction.reaction.hashCode();
    }
  }
}
