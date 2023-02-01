/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/02/2019
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.PollResultsController;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class TGMessagePoll extends TGMessage implements ClickHelper.Delegate, ComplexReceiver.KeyFilter, TooltipOverlayView.VisibilityListener {
  private static int ftoi (float f) { // Utility method to change conversion in all places, if needed
    return (int) f;
  }

  private static int fromTo (int from, int to, float factor) {
    return from + ftoi((float) (to - from) * factor);
  }

  private static float fromTo (float from, float to, float factor) {
    return from + (to - from) * factor;
  }

  private static class PollState {
    private final TdApi.Poll poll;
    private final int maxVoterCount;
    private final PollOption[] options;
    private final float resultsVisibility, timerVisibility, hintVisibility;
    private final boolean resultsVisible, timerVisible, hintVisible;

    public PollState (Tdlib tdlib, TdApi.Poll poll) {
      this.poll = poll;
      this.resultsVisible = TD.needShowResults(poll);
      this.resultsVisibility = resultsVisible ? 1f : 0f;
      this.timerVisible = !poll.isClosed && poll.openPeriod != 0;
      this.timerVisibility = timerVisible ? 1f : 0f;
      this.hintVisible = poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR && !Td.isEmpty(((TdApi.PollTypeQuiz) poll.type).explanation);
      this.hintVisibility = hintVisible ? 1f : 0f;
      this.maxVoterCount = TD.getMaxVoterCount(poll);
      this.options = new PollOption[poll.options.length];
      for (int i = 0; i < poll.options.length; i++) {
        this.options[i] = new PollOption(poll.options[i], voteRatio(i), poll.options[i].isBeingChosen ? 1f : 0f);
      }
    }

    public PollState (Tdlib tdlib, PollState fromState, PollState toState, float factor) {
      if (fromState.options.length != toState.options.length)
        throw new AssertionError(fromState.options.length + " != " + toState.options.length);
      this.resultsVisibility = fromTo(fromState.resultsVisibility, toState.resultsVisibility, factor);
      this.resultsVisible = resultsVisibility > 0f;
      this.timerVisibility = fromTo(fromState.timerVisibility, toState.timerVisibility, factor);
      this.timerVisible = timerVisibility > 0f;
      this.hintVisibility = fromTo(fromState.hintVisibility, toState.hintVisibility, factor);
      this.hintVisible = hintVisibility > 0f;
      this.maxVoterCount = fromTo(fromState.maxVoterCount, toState.maxVoterCount, factor);
      this.options = new PollOption[toState.options.length];
      TdApi.PollOption[] options = new TdApi.PollOption[toState.options.length];
      for (int i = 0; i < options.length; i++) {
        TdApi.PollOption fromOption = fromState.poll.options[i];
        TdApi.PollOption toOption = toState.poll.options[i];
        int voterCount = fromTo(fromOption.voterCount, toOption.voterCount, factor);
        int votePercentage = fromTo(fromOption.votePercentage, toOption.votePercentage, factor);
        TdApi.PollOption option = new TdApi.PollOption(toOption.text, voterCount, votePercentage, toOption.isChosen, toOption.isBeingChosen);
        options[i] = option;
        this.options[i] = new PollOption(
          option,
          fromTo(fromState.options[i].ratio, toState.options[i].ratio, factor),
          fromTo(fromState.options[i].progress, toState.options[i].progress, factor)
        );
      }
      this.poll = new TdApi.Poll(toState.poll.id, toState.poll.question, options, toState.poll.totalVoterCount, toState.poll.recentVoterUserIds, toState.poll.isAnonymous, toState.poll.type, toState.poll.openPeriod, toState.poll.closeDate, toState.poll.isClosed);
    }

    public int size () {
      return poll.options.length;
    }

    public float voteRatio (int optionId) {
      return maxVoterCount != 0 ? (float) poll.options[optionId].voterCount / (float) maxVoterCount : 0;
    }

    public int votePercentage (int optionId) {
      return poll.options[optionId].votePercentage;
    }

    public int voterCount (int optionId) {
      return poll.options[optionId].voterCount;
    }
  }

  private static class PollOption {
    public TdApi.PollOption option;
    public final float ratio;
    public final float progress;

    public PollOption (TdApi.PollOption option, float ratio, float progress) {
      this.option = option;
      this.ratio = ratio;
      this.progress = progress;
    }
  }

  // State

  @NonNull
  private PollState state;
  @Nullable
  private PollState futureState;

  // Texts

  public static class OptionEntry implements Destroyable {
    private int percentage = -1;
    private String percentageStr;
    private int percentageStrWidth;
    private float selectionFactor;
    private SimplestCheckBox checkBox;
    private TextWrapper text;
    private ProgressComponent progress;
    private BoolAnimator isSelected;

    public float getSelectionFactor () {
      return Math.max(selectionFactor, isSelected != null ? isSelected.getFloatValue() : 0f);
    }

    public boolean isSelected () {
      return isSelected != null && isSelected.getValue();
    }

    public float getMoveFactor () {
      if (isSelected != null) {
        return 1f - this.selectionFactor;
      } else {
        return 0f;
      }
    }

    @Override
    public void performDestroy () {
      if (progress != null) {
        progress.performDestroy();
        progress = null;
      }
      if (checkBox != null) {
        checkBox.destroy();
        checkBox = null;
      }
    }
  }

  @Override
  protected void onMessageContainerDestroyed() {
    if (options != null) {
      for (OptionEntry entry : options) {
        entry.performDestroy();
      }
    }
  }

  private OptionEntry[] options;
  private ProgressComponent commonProgress;
  private ProgressComponent timerProgress;

  private static final int POLL_STATUS_ANONYMOUS = 1;
  private static final int POLL_STATUS_CLOSED = 2;
  private int pollStatus;
  private Text pollStatusText;

  private int totalVoterCount = -1;
  private String totalVoterCountStr;
  private int totalVoterCountStrWidth;
  private final BoolAnimator isButtonActive;
  private final ReplaceAnimator<Button> button;

  private TextWrapper questionText;

  // Animation

  private static final int ANIMATOR_CHANGE = 0;
  private static final int ANIMATOR_SELECT = 1;
  private static final int ANIMATOR_BUTTON = 2;

  private FactorAnimator animator;
  private float changeFactor;

  // Touch

  private final ClickHelper clickHelper;

  private static final float VOTER_RADIUS = 9f;
  private static final float VOTER_OUTLINE = 1f;
  private static final float VOTER_SPACING = 4f;

  private static class UserEntry {
    private final long userId;

    public UserEntry (Tdlib tdlib, long userId) {
      this.userId = userId;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof UserEntry && ((UserEntry) obj).userId == this.userId;
    }

    @Override
    public int hashCode() {
      return (int) (userId ^ (userId >>> 32));
    }

    public void draw (Canvas c, TGMessage context, ComplexReceiver complexReceiver, float cx, float cy, final float alpha) {
      if (alpha == 0f)
        return;

      int replaceColor = context.getContentReplaceColor();
      int radius = Screen.dp(VOTER_RADIUS);

      AvatarReceiver receiver = complexReceiver.getAvatarReceiver(userId);
      if (alpha != 1f)
        receiver.setPaintAlpha(receiver.getPaintAlpha() * alpha);
      receiver.setBounds((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius));

      boolean needRestore = alpha != 1f;
      int restoreToCount;
      if (needRestore) {
        float scale = .5f + .5f * alpha;
        restoreToCount = Views.save(c);
        c.scale(scale, scale, cx, cy);
      } else {
        restoreToCount = -1;
      }

      float displayRadius = receiver.getDisplayRadius();
      receiver.drawPlaceholderRounded(c, displayRadius, Screen.dp(VOTER_OUTLINE) * alpha * .5f, Paints.getProgressPaint(replaceColor, Screen.dp(VOTER_OUTLINE) * alpha));
      if (receiver.needPlaceholder())
        receiver.drawPlaceholder(c);
      receiver.draw(c);
      if (alpha != 1f)
        receiver.restorePaintAlpha();

      if (needRestore) {
        Views.restore(c, restoreToCount);
      }
    }
  }

  private ListAnimator<UserEntry> recentVoters;

  // Impl

  private void prepareOptions (TdApi.PollOption[] options) {
    int count = options.length;
    if (this.options == null) {
      this.options = new OptionEntry[count];
      for (int i = 0; i < count; i++) {
        this.options[i] = new OptionEntry();
        if (options[i].isBeingChosen && isMultiChoicePoll()) {
          this.options[i].isSelected = new BoolAnimator(ANIMATOR_SELECT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l, true);
        }
      }
    } else if (count != this.options.length) {
      int oldLength = this.options.length;
      this.options = ArrayUtils.resize(this.options, count);
      for (int i = oldLength; i < count; i++) {
        this.options[i] = new OptionEntry();
        if (options[i].isBeingChosen && isMultiChoicePoll()) {
          this.options[i].isSelected = new BoolAnimator(ANIMATOR_SELECT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l, true);
        }
      }
    }
  }

  public TGMessagePoll (MessagesManager manager, TdApi.Message msg, TdApi.Poll poll) {
    super(manager, msg);

    /*if (BuildConfig.DEBUG) {
      msg.date = (int) (tdlib().currentTimeMillis() / 1000l);
      poll.closeDate = msg.date + 15;
      poll.openPeriod = 30;
      poll.isClosed = false;
      if (poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR) {
        ((TdApi.PollTypeQuiz) poll.type).explanation = null;
      }
    }*/

    this.clickHelper = new ClickHelper(this);
    this.state = new PollState(tdlib, poll);
    if (!poll.isAnonymous || isMultiChoicePoll()) {
      this.isButtonActive = new BoolAnimator(ANIMATOR_BUTTON, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
      this.button = new ReplaceAnimator<>(animator -> this.invalidate());
    } else {
      this.isButtonActive = null;
      this.button = null;
    }
  }

  private void setQuestion (String question) {
    if (this.questionText == null || !StringUtils.equalsOrBothEmpty(this.questionText.getText(), question)) {
      this.questionText = new TextWrapper(state.poll.question, getBiggerTextStyleProvider(), getTextColorSet())
        .setEntities(new TextEntity[] {TextEntity.valueOf(tdlib, state.poll.question, new TdApi.TextEntity(0, state.poll.question.length(), new TdApi.TextEntityTypeBold()), null)}, null)
        .setViewProvider(currentViews);
    }
  }

  private void setOptions (TdApi.PollOption[] options) {
    prepareOptions(options);
    int optionId = 0;
    for (TdApi.PollOption option : options) {
      if (this.options[optionId].text == null || !StringUtils.equalsOrBothEmpty(this.options[optionId].text.getText(), option.text)) {
        this.options[optionId].text = new TextWrapper(option.text, getTextStyleProvider(), getTextColorSet())
          .setViewProvider(currentViews);
      }
      optionId++;
    }
  }

  private void prepareProgress (TdApi.PollOption[] options) {
    prepareOptions(options);
    if (isMultiChoicePoll()) {
      if (commonProgress != null)
        return;
      for (TdApi.PollOption option : options) {
        if (option.isBeingChosen) {
          getCommonProgressView();
          break;
        }
      }
    } else {
      int optionId = 0;
      for (TdApi.PollOption option : options) {
        if (option.isBeingChosen && this.options[optionId].progress == null) {
          getResultProgressView(optionId);
        }
        optionId++;
      }
    }
  }

  @Override
  protected void buildContent (int maxWidth) {
    if (questionText == null) {
      setRecentVoters(state.poll.recentVoterUserIds, false);
      setQuestion(state.poll.question);
      setOptions(state.poll.options);
      prepareProgress(state.poll.options);
      setTexts();
      setButton(false);
    }
    questionText.prepare(maxWidth);
    int optionWidth = maxWidth - Screen.dp(34f);
    for (OptionEntry option : this.options) {
      option.text.prepare(optionWidth);
    }
  }

  @Override
  protected int getContentHeight () {
    int height = (questionText != null ? questionText.getHeight() : 0) + Screen.dp(5f);
    height += Screen.dp(18f); // poll status
    if (options != null) {
      for (OptionEntry option : options) {
        height += getOptionHeight(option.text);
      }
    } else {
      height += (Screen.dp(46f) + Screen.separatorSize()) * getPoll().options.length;
    }
    height += Screen.dp(10f) + Screen.dp(14f);
    height += Screen.dp(12f);
    if (useBubbles()) {
      height += Screen.dp(8f);
    }
    return height;
  }

  @Override
  public boolean filterKey (int receiverType, Receiver receiver, long key) {
    if (recentVoters != null) {
      for (ListAnimator.Entry<UserEntry> recentVoter : recentVoters) {
        if (recentVoter.item.userId == key) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean needComplexReceiver () {
    return !getPoll().isAnonymous;
  }

  @Override
  public void requestMediaContent (ComplexReceiver complexReceiver, boolean invalidate, int invalidateArg) {
    if (recentVoters != null) {
      for (ListAnimator.Entry<UserEntry> entry : recentVoters) {
        AvatarReceiver receiver = complexReceiver.getAvatarReceiver(entry.item.userId);
        receiver.requestUser(tdlib, entry.item.userId, AvatarReceiver.Options.NONE);
      }
    }
    complexReceiver.clearReceivers(this);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    drawContent(view, c, startX, startY, maxWidth);

    // First, draw question
    startY += questionText.getHeight() + Screen.dp(5f);
    // Second, draw status
    startY += Screen.dp(18f);

    startY -= Screen.dp(10f);

    if (recentVoters != null) {
      int cx = startX + pollStatusText.getWidth() + Screen.dp(VOTER_RADIUS) + Screen.dp(6f);
      int spacing = Screen.dp(VOTER_RADIUS) * 2 - Screen.dp(VOTER_SPACING);
      for (int index = recentVoters.size() - 1; index >= 0; index--) {
        ListAnimator.Entry<UserEntry> item = recentVoters.getEntry(index);
        int x = cx + item.getIndex() * spacing;
        if (x + Screen.dp(VOTER_RADIUS) + Screen.dp(2f) <= startX + maxWidth) {
          item.item.draw(c, this, receiver, cx + item.getPosition() * spacing, startY, item.getVisibility());
        }
      }
    }
  }

  public boolean isAnonymous () {
    return state.poll.isAnonymous;
  }

  private int getConfettiCenterX (int optionId) {
    return getContentX() + Screen.dp(12f);
  }

  private int getConfettiCenterY (int optionId) {
    int startY = getContentY();
    startY += questionText.getHeight() + Screen.dp(5f);
    startY += Screen.dp(18f);
    int currentOptionId = 0;
    for (OptionEntry option : options) {
      if (currentOptionId == optionId) {
        return startY + Screen.dp(22f);
      }
      int optionHeight = getOptionHeight(option.text);
      startY += optionHeight;
      currentOptionId++;
    }
    return getContentY() + getContentHeight() / 2;
  }

  private Drawable explanationDrawable, explanationDrawableActive;
  private float explanationActive;

  private void setExplanationActive (float factor) {
    if (this.explanationActive != factor) {
      this.explanationActive = factor;
      if (getHintVisibility() > 0f) {
        invalidate();
      }
    }
  }

  private Drawable getExplanationDrawable (boolean isActive) {
    if (isActive) {
      if (explanationDrawableActive == null) {
        explanationDrawableActive = Drawables.get(R.drawable.deproko_baseline_lamp_filled_22);
      }
      return explanationDrawableActive;
    } else {
      if (explanationDrawable == null) {
        explanationDrawable = Drawables.get(R.drawable.deproko_baseline_lamp_22);
      }
      return explanationDrawable;
    }
  }

  private int timerTime = -1;
  private String timerStr;
  private float timerWidth;

  @Override
  protected void drawContent (MessageView view, Canvas c, final int startX, int startY, int maxWidth) {
    int textColor = getTextColor();
    int decentColor = getDecentColor();
    int textOffset = Screen.dp(12f);

    // First, draw question
    questionText.draw(c, startX, startX + maxWidth, 0, startY, null, 1f);
    startY += questionText.getHeight() + Screen.dp(5f);

    // Second, draw status
    pollStatusText.draw(c, startX, startY);

    float hintVisibility = getHintVisibility();
    if (hintVisibility > 0f) {
      Drawable explanationDrawable = getExplanationDrawable(false);
      float cx = startX + maxWidth - explanationDrawable.getMinimumWidth() / 2f - Screen.dp(2f);
      float cy = startY + pollStatusText.getHeight() / 2f;
      if (explanationActive < 1f) {
        Drawables.draw(c, explanationDrawable, cx - explanationDrawable.getMinimumWidth() / 2f, cy - explanationDrawable.getMinimumHeight() / 2f, hintVisibility == 1f ? getDecentIconPaint() : Paints.getPorterDuffPaint(ColorUtils.alphaColor(hintVisibility, decentColor)));
      }
      if (explanationActive > 0f) {
        Drawable explanationDrawableActive = getExplanationDrawable(true);
        int progressColor = getProgressColor();
        int size = Screen.dp(2f);
        Drawables.draw(c, explanationDrawableActive, cx - explanationDrawable.getMinimumWidth() / 2f, cy - explanationDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(ColorUtils.alphaColor(hintVisibility * explanationActive, progressColor)));

        cy -= Screen.dp(2.5f);

        int lineColor = ColorUtils.alphaColor(hintVisibility * explanationActive, progressColor);
        int lineLength = Screen.dp(2.5f);
        float dy1 = Screen.dp(6f) + Screen.dp(3f);
        float dy2 = lineLength * explanationActive;

        for (float degrees = 0; degrees < 360f; degrees += 45f) {
          if (degrees == 180 || degrees == 180 - 45 || degrees == 180 + 45) {
            continue;
          }
          double rad = Math.toRadians(degrees);
          float sin = (float) Math.sin(rad);
          float cos = (float) Math.cos(rad);
          c.drawLine(cx - dy1 * sin, cy - dy1 * cos, cx - (dy1 + dy2) * sin, cy - (dy1 + dy2) * cos, Paints.getProgressPaint(lineColor, size));
        }
      }
    }

    float timerVisibility = getTimerVisibility();
    if (timerVisibility > 0f || timerProgress != null) {
      if (timerProgress == null) {
        timerProgress = new ProgressComponent(context(), Screen.dp(5f));
        timerProgress.setViewProvider(currentViews);
        timerProgress.setTimer(tdlib.toSystemTimeMillis(msg.date, TimeUnit.SECONDS), tdlib.toSystemTimeMillis(getPoll().closeDate, TimeUnit.SECONDS));
        timerProgress.setUseLargerPaint(Screen.dp(1.5f));
      }
      int cx = startX + maxWidth - Screen.dp(12f) - Screen.dp(1f);
      int cy = startY + pollStatusText.getHeight() / 2;
      int size = Screen.dp(12f);

      long remainingTime = timerProgress.getTimerRemainingTimeMillis();
      int remainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remainingTime);
      int color = remainingTime >= 10000 ? getDecentColor() : remainingTime <= 5000 ? getNegativeLineColor() : ColorUtils.fromToArgb(getNegativeLineColor(), getDecentColor(), (remainingTime - 5000) / (float) (10000 - 5000));

      timerProgress.forceColor(color);
      timerProgress.setAlpha(timerVisibility);
      timerProgress.setBounds(cx - size, cy - size, cx + size, cy + size);
      timerProgress.draw(c);

      TextPaint paint = Paints.getRegularTextPaint(12f, ColorUtils.alphaColor(timerVisibility, color));
      if (this.timerTime != remainingSeconds || this.timerStr == null) {
        this.timerStr = Strings.buildDuration(remainingSeconds);
        this.timerTime = remainingSeconds;
        this.timerWidth = U.measureText(timerStr, paint);
      }

      cx -= Screen.dp(5f) + Screen.dp(6f) + timerWidth;
      c.drawText(timerStr, cx, cy + Screen.dp(4f), paint);
    }

    startY += Screen.dp(18f);

    // Then, draw options

    boolean isQuiz = state.poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
    int correctOptionId;
    if (isQuiz) {
      correctOptionId = ((TdApi.PollTypeQuiz) state.poll.type).correctOptionId;
      if (correctOptionId == -1 && futureState != null && futureState.poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR) {
        correctOptionId = ((TdApi.PollTypeQuiz) futureState.poll.type).correctOptionId;
      }
    } else {
      correctOptionId = -1;
    }

    float visibility = getResultsVisibility();
    int optionId = 0;
    for (OptionEntry option : options) {
      int optionHeight = getOptionHeight(option.text);
      int rightX = startX + maxWidth + (useBubbles() ? getBubblePaddingRight() : 0);

      if (visibility < 1f) {
        // separator
        int lineY = startY + optionHeight - Screen.separatorSize();
        c.drawLine(startX + Screen.dp(34f), lineY, rightX, lineY, Paints.getProgressPaint(ColorUtils.alphaColor(1f - visibility, getSeparatorColor()),  Screen.separatorSize()));
      }

      if (highlightOptionId == optionId) {
        c.drawRect(startX - (useBubbles() ? getBubbleContentPadding() : 0), startY, rightX, startY + optionHeight, Paints.fillingPaint(Theme.getColor(getPressColorId())));
      }

      int optionTextY = startY + Math.max(Screen.dp(8f), Screen.dp(46f) / 2 - option.text.getLineHeight() / 2);
      option.text.draw(c, startX + Screen.dp(34f), startX + maxWidth, 0, optionTextY, null, 1f);

      float progress = getResultProgress(optionId);
      float stateVisibility = visibility >= .5f ? 0f : 1f - visibility / .5f;
      int progressRadius = Screen.dp(9f);
      int progressCx = startX + Screen.dp(12f);
      int progressCy = startY + Screen.dp(22f);
      if (stateVisibility > 0f) {
        int circleColor = ColorUtils.alphaColor(stateVisibility * (isMultiChoicePoll() ? 1f - option.getSelectionFactor() : 1f - progress), decentColor);
        if (isMultiChoicePoll()) {
          RectF rectF = Paints.getRectF();
          progressRadius -= Screen.dp(1f);
          rectF.set(progressCx - progressRadius, progressCy - progressRadius, progressCx + progressRadius, progressCy + progressRadius);
          int squareRadius = Screen.dp(3f);
          c.drawRoundRect(rectF, squareRadius, squareRadius, Paints.getProgressPaint(circleColor, Screen.dp(1f)));
        } else {
          c.drawCircle(progressCx, progressCy, progressRadius, Paints.getProgressPaint(circleColor, Screen.dp(1f)));
        }
        if (progress > 0f && option.progress != null && !isMultiChoicePoll()) {
          ProgressComponent progressComponent = getResultProgressView(optionId);
          progressComponent.forceColor(ColorUtils.alphaColor(stateVisibility * progress, Theme.getColor(getProgressColorId())));
          progressComponent.setBounds(progressCx - progressRadius, progressCy - progressRadius, progressCx + progressRadius, progressCy + progressRadius);
          progressComponent.draw(c);
        }
      }

      int lineColor = getVerticalLineColor();
      int contentColor = getVerticalLineContentColor();
      float selectionFactor = option.getSelectionFactor();

      if (correctOptionId != -1) {
        boolean isChosen = getPoll().options[correctOptionId].isChosen;
        int secondaryColor = optionId == correctOptionId ? getCorrectLineColor(isChosen) : getNegativeLineColor();
        int secondaryContentColor = optionId == correctOptionId ? getCorrectLineContentColor(isChosen) : getNegativeLineContentColor();
        lineColor = ColorUtils.fromToArgb(lineColor, secondaryColor, selectionFactor);
        contentColor = ColorUtils.fromToArgb(contentColor, secondaryContentColor, selectionFactor);
      }

      int lineY = startY + optionHeight - Screen.separatorSize() - Screen.dp(2.5f);
      int fromX = startX + Screen.dp(34f);
      int toX = startX + maxWidth;
      if (visibility > 0f) {
        c.drawLine(fromX, lineY, fromX + (float) (toX - fromX) * getResultRatio(optionId), lineY, Paints.getProgressPaint(ColorUtils.alphaColor(visibility, lineColor),
                Screen.dp(3f)));
        c.drawText(option.percentageStr, fromX - option.percentageStrWidth - Screen.dp(8f), optionTextY + textOffset, Paints.getMediumTextPaint(13f, ColorUtils.alphaColor(visibility, textColor), false));
      }

      if (selectionFactor > 0f) {
        float moveFactor = option.getMoveFactor();
        float squareFactor = (state.poll.type.getConstructor() == TdApi.PollTypeRegular.CONSTRUCTOR && ((TdApi.PollTypeRegular) state.poll.type).allowMultipleAnswers ? 1f : 0f);
        if (option.checkBox == null) {
          option.checkBox = SimplestCheckBox.newInstance(selectionFactor, null, lineColor, contentColor, isQuiz && optionId != correctOptionId, moveFactor);
        }
        float scale = .75f;
        int cx = fromX - (int) (SimplestCheckBox.size() * scale) / 2 - Screen.dp(8f) + (int) (Screen.dp(2f) * scale);
        int cy = lineY - (int) (Screen.dp(2f) * scale);
        if (moveFactor > 0f) {
          cx = (int) (cx + (progressCx - cx) * moveFactor);
          cy = (int) (cy + (progressCy - cy) * moveFactor);
          scale = scale + (1f - scale) * moveFactor;
        }
        if (scale != 1f) {
          c.save();
          c.scale(scale, scale, cx, lineY);
        }
        SimplestCheckBox.draw(c, cx, cy, selectionFactor, null, option.checkBox, lineColor, contentColor, isQuiz && optionId != correctOptionId, squareFactor);
        if (scale != 1f) {
          c.restore();
        }
      }

      startY += optionHeight;
      optionId++;
    }

    if (highlightOptionId == HIGHLIGHT_BUTTON) {
      if (useBubble() && !useForward()) {
        c.save();
        c.clipRect(getActualLeftContentEdge(), startY, getActualRightContentEdge(), getBottomContentEdge());
        c.drawPath(getBubblePath(), Paints.fillingPaint(Theme.getColor(getPressColorId())));
        c.restore();
      } else {
        int rightX = startX + maxWidth + (useBubbles() ? getBubblePaddingRight() : 0);
        c.drawRect(startX - (useBubbles() ? getBubbleContentPadding() : 0), startY, rightX, startY + Screen.dp(46f), Paints.fillingPaint(Theme.getColor(getPressColorId())));
      }
    }

    startY += Screen.dp(10f);
    float maxVisibility = 0f;
    if (button != null) {
      for (ListAnimator.Entry<Button> entry : button) {
        maxVisibility = Math.max(entry.getVisibility(), maxVisibility);
        int x = startX + maxWidth / 2 - entry.item.text.getWidth() / 2;
        int y = startY + Screen.dp(useBubbles() ? 6f : 4f);
        /*float textVisibility = entry.getVisibility();
        if (textVisibility != 1f) {
          float scale = .9f + .1f * textVisibility;
          c.save();
          c.scale(scale, scale, startX + maxWidth / 2, y + entry.item.text.getHeight() / 2);
        }*/
        // int dy = (int) (!isMultiChoicePoll() && !isAnonymous() ? Screen.dp(7f) * getResultsVisibility() : 0);
        entry.item.text.draw(c, x, y, null, (1f - .4f * (1f - isButtonActive.getFloatValue())) * entry.getVisibility());
        if (commonProgress != null && entry.item.id == R.id.btn_vote) {
          final float stateVisibility = getResultsVisibility() >= .5f ? 0f : 1f - getResultsVisibility() / .5f;
          commonProgress.forceColor(ColorUtils.alphaColor(stateVisibility * getCommonProgress() * entry.getVisibility(), Theme.getColor(getProgressColorId())));
          int radius = Screen.dp(3);
          x += entry.item.text.getWidth() + radius + Screen.dp(7f);
          y += entry.item.text.getHeight() / 2;
          commonProgress.setBounds(x - radius, y - radius, x + radius, y + radius);
          commonProgress.draw(c);
        }
        /*if (textVisibility != 1f) {
          c.restore();
        }*/
      }
    }
    startY += Screen.dp(12f);

    /*if (button != null && !isAnonymous() && !isMultiChoicePoll()) {
      decentColor = U.alphaColor(1f - maxVisibility, decentColor);
    }*/
    int textCx = startX + maxWidth / 2 - totalVoterCountStrWidth / 2;
    int textCy = startY + textOffset - Screen.dp(useBubbles() ? 5f : 7f);
    if (!isAnonymous() || isMultiChoicePoll()) {
      decentColor = ColorUtils.alphaColor(1f - maxVisibility, decentColor);
      c.drawText(totalVoterCountStr, textCx, textCy, Paints.getRegularTextPaint(12f, decentColor));
    } else {
      // c.drawText(totalVoterCountStr, startX, startY + textOffset, Paints.getRegularTextPaint(12f, decentColor));
      c.drawText(totalVoterCountStr, textCx, textCy, Paints.getRegularTextPaint(12f, decentColor));
    }
  }

  @Override
  protected int getBottomLineContentWidth () {
    return totalVoterCountStrWidth;
  }

  private float getResultsVisibility () {
    if (futureState == null || changeFactor == 0f)
      return state.resultsVisibility;
    if (changeFactor == 1f)
      return futureState.resultsVisibility;
    return fromTo(state.resultsVisibility, futureState.resultsVisibility, changeFactor);
  }

  private float getHintVisibility () {
    if (futureState == null || changeFactor == 0f)
      return state.hintVisibility;
    if (changeFactor == 1f)
      return futureState.hintVisibility;
    return fromTo(state.hintVisibility, futureState.hintVisibility, changeFactor);
  }

  private float getTimerVisibility () {
    if (futureState == null || changeFactor == 0f)
      return state.timerVisibility;
    if (changeFactor == 1f)
      return futureState.timerVisibility;
    return fromTo(state.timerVisibility, futureState.timerVisibility, changeFactor);
  }

  private float getResultRatio (int optionId) {
    if (futureState == null || changeFactor == 0f)
      return state.resultsVisible ? state.options[optionId].ratio : 0f;
    if (changeFactor == 1f)
      return futureState.options[optionId].ratio;
    return fromTo(state.options[optionId].ratio, futureState.options[optionId].ratio, changeFactor);
  }

  private float getResultProgress (int optionId) {
    if (futureState == null || changeFactor == 0f)
      return state.options[optionId].progress;
    if (changeFactor == 1f)
      return futureState.options[optionId].progress;
    return fromTo(state.options[optionId].progress, futureState.options[optionId].progress, changeFactor);
  }

  private float getCommonProgress () {
    float maxProgress = 0f;
    for (int optionId = 0; optionId < state.options.length; optionId++) {
      maxProgress = Math.max(maxProgress, getResultProgress(optionId));
    }
    return maxProgress;
  }

  private TdApi.Poll getPoll () {
    return futureState != null ? futureState.poll : state.poll;
  }

  private boolean isMultiChoicePoll () {
    return TD.isMultiChoice(getPoll());
  }

  private boolean isQuiz () {
    return getPoll().type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
  }

  private boolean hasAnswer () {
    return TD.hasAnswer(getPoll());
  }

  private ProgressComponent newProgress (boolean isCommon) {
    ProgressComponent progress = new ProgressComponent(context(), Screen.dp(isCommon ? 3f : 9f));
    progress.setNoStartDelay(true);
    progress.setViewProvider(currentViews);
    progress.forceColor(0);
    return progress;
  }

  private ProgressComponent getCommonProgressView () {
    if (!isMultiChoicePoll())
      return null;
    if (commonProgress == null)
      commonProgress = newProgress(true);
    return commonProgress;
  }

  private ProgressComponent getResultProgressView (int optionId) {
    if (isMultiChoicePoll())
      return null;
    ProgressComponent progress = options[optionId].progress;
    if (progress == null) {
      progress = newProgress(false);
      options[optionId].progress = progress;
    }
    return progress;
  }

  private boolean canVote (boolean checkSelected) {
    return TD.canVote(getPoll()) && (!checkSelected || (!isMultiChoicePoll() || !(!hasAnswer() && TD.hasSelectedOption(getPoll()))));
  }

  @Override
  protected boolean onLocaleChange () {
    totalVoterCount = -1;
    pollStatus = 0;
    setTexts();
    return true;
  }

  private void setRecentVoters (long[] recentVoterUserIds, boolean animated) {
    if (recentVoterUserIds != null && recentVoterUserIds.length > 0) {
      List<UserEntry> entries = new ArrayList<>(recentVoterUserIds.length);
      for (long userId : recentVoterUserIds) {
        entries.add(new UserEntry(tdlib, userId));
      }
      if (this.recentVoters == null)
        this.recentVoters = new ListAnimator<>(currentViews);
      recentVoters.reset(entries, animated);
    } else if (recentVoters != null) {
      recentVoters.clear(animated);
    }
  }

  @Override
  protected boolean isSupportedMessageContent(TdApi.Message message, TdApi.MessageContent messageContent) {
    if (super.isSupportedMessageContent(message, messageContent)) {
      TdApi.Poll oldPoll = getPoll();
      TdApi.Poll updatedPoll = ((TdApi.MessagePoll) messageContent).poll;
      return oldPoll.options.length == updatedPoll.options.length &&
        oldPoll.type.getConstructor() == updatedPoll.type.getConstructor() &&
        TD.isMultiChoice(oldPoll) == TD.isMultiChoice(updatedPoll);
    }
    return false;
  }

  private void applyPoll (TdApi.Poll updatedPoll) {
    TdApi.Poll oldPoll = getPoll();
    boolean changed = !TD.compareContents(oldPoll, updatedPoll) || questionText == null;
    boolean animated = !changed && needAnimateChanges();
    if (animated) {
      resetPollAnimation(true);
      futureState = new PollState(tdlib, updatedPoll);
      setRecentVoters(updatedPoll.recentVoterUserIds, true);
      setButton(true);
      if (recentVoters != null) {
        invalidateContentReceiver();
      }
      if (isQuiz() || Config.TEST_CONFETTI) {
        int optionId = 0;
        int beingChosenOptionId = -1;
        for (TdApi.PollOption option : oldPoll.options) {
          if (option.isBeingChosen) {
            beingChosenOptionId = optionId;
            break;
          }
          optionId++;
        }
        optionId = 0;
        int chosenOptionId = -1;
        for (TdApi.PollOption option : updatedPoll.options) {
          if (option.isChosen) {
            chosenOptionId = optionId;
            break;
          }
          optionId++;
        }
        int correctOptionId;
        if (updatedPoll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR) {
          correctOptionId = ((TdApi.PollTypeQuiz) updatedPoll.type).correctOptionId;
        } else {
          correctOptionId = 0;
        }
        if (correctOptionId != -1 && beingChosenOptionId != -1 && updatedPoll.options[beingChosenOptionId].isChosen) {
          if (beingChosenOptionId == correctOptionId) {
            performConfettiAnimation(getConfettiCenterX(beingChosenOptionId), getConfettiCenterY(beingChosenOptionId));
            performShakeAnimation(true);
          } else {
            performShakeAnimation(false);
            if (updatedPoll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR && !Td.isEmpty(((TdApi.PollTypeQuiz) updatedPoll.type).explanation)) {
              showExplanation(null);
            }
          }
        } else if (correctOptionId != -1 && chosenOptionId != -1 && correctOptionId != chosenOptionId &&
          updatedPoll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR && oldPoll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR &&
          Td.isEmpty(((TdApi.PollTypeQuiz) oldPoll.type).explanation) && !Td.isEmpty(((TdApi.PollTypeQuiz) updatedPoll.type).explanation)
        ) {
          showExplanation(null);
        } else if (chosenOptionId == -1 && !oldPoll.isClosed && updatedPoll.isClosed && oldPoll.openPeriod > 0 && oldPoll.closeDate != 0 && tdlib.currentTimeMillis() / 1000l + 5 >= oldPoll.closeDate) {
          performShakeAnimation(false);
        }
      }
      if (isMultiChoicePoll() && TD.hasAnswer(updatedPoll)) {
        int optionId = 0;
        for (OptionEntry entry : options) {
          if (entry.isSelected != null && !(updatedPoll.options[optionId].isChosen || updatedPoll.options[optionId].isBeingChosen)) {
            entry.isSelected.setValue(false, false);
            entry.isSelected = null;
          }
          optionId++;
        }
      }
      if (animator == null) {
        animator = new FactorAnimator(ANIMATOR_CHANGE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280l);
      }
      animator.animateTo(1f);
    } else {
      resetPollAnimation(false);
      this.state = new PollState(tdlib, updatedPoll);
      setRecentVoters(updatedPoll.recentVoterUserIds, false);
      if (recentVoters != null) {
        invalidateContentReceiver();
      }
      setButton(false);
      setTexts();
      if (changed) {
        setQuestion(updatedPoll.question);
        setOptions(updatedPoll.options);
        prepareProgress(updatedPoll.options);
        rebuildAndUpdateContent();
      } else {
        invalidate();
      }
    }
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (newContent.getConstructor() == TdApi.MessagePoll.CONSTRUCTOR) {
      TdApi.Poll updatedPoll = ((TdApi.MessagePoll) newContent).poll;
      applyPoll(updatedPoll);
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.Poll updatedPoll = ((TdApi.MessagePoll) newContent).poll;
    applyPoll(updatedPoll);
    return true;
  }

  // Texts

  private CharSequence getCounter (TdApi.Poll poll, int count) {
    switch (getPoll().type.getConstructor()) {
      case TdApi.PollTypeRegular.CONSTRUCTOR:
        return count > 0 ? Lang.pluralBold(R.string.xVotes, count) : Lang.getString(poll.isClosed ? R.string.NoVotesResult : R.string.NoVotes);
      case TdApi.PollTypeQuiz.CONSTRUCTOR:
        return count > 0 ? Lang.pluralBold(R.string.xAnswers, count) : Lang.getString(poll.isClosed ? R.string.NoAnswersResult : R.string.NoAnswers);
      default:
        throw new IllegalArgumentException(getPoll().type.toString());
    }
  }

  private void setTotalVoterCount (TdApi.Poll poll) {
    int count = poll.totalVoterCount;
    if (!poll.isAnonymous) {
      if (TD.hasAnswer(poll))
        count--;
    } else if (isMultiChoicePoll() && canVote(false)) {
      if (!TD.hasAnswer(poll))
        count++;
    }
    if (this.totalVoterCount != count) {
      this.totalVoterCount = count;
      this.totalVoterCountStr = getCounter(poll, count).toString();
      this.totalVoterCountStrWidth = (int) U.measureText(totalVoterCountStr, Paints.getRegularTextPaint(12f));
    }
  }

  private void setPollStatus (int status) {
    if (this.pollStatus != status) {
      this.pollStatus = status;
      int stringRes;
      switch (getPoll().type.getConstructor()) {
        case TdApi.PollTypeRegular.CONSTRUCTOR:
          stringRes = status == POLL_STATUS_CLOSED ? R.string.PollResults : getPoll().isAnonymous ? R.string.PollAnonymous : R.string.PollPublic;
          break;
        case TdApi.PollTypeQuiz.CONSTRUCTOR:
          stringRes = status == POLL_STATUS_CLOSED ? R.string.QuizResults : getPoll().isAnonymous ? R.string.QuizAnonymous : R.string.QuizPublic;
          break;
        default:
          throw new IllegalArgumentException(getPoll().type.toString());
      }
      this.pollStatusText = new Text.Builder(Lang.getString(stringRes), getContentMaxWidth(), Paints.robotoStyleProvider(12f), getDecentColorSet()).singleLine().build();
    }
  }

  private static String makeVotePercentageStr (int votePercentage) {
    return votePercentage + "%";
  }

  private void setPercentage (int optionId, int percentage) {
    if (options != null && options[optionId].percentage != percentage) {
      options[optionId].percentage = percentage;
      options[optionId].percentageStr = makeVotePercentageStr(percentage);
      options[optionId].percentageStrWidth = (int) U.measureText(options[optionId].percentageStr, Paints.getMediumTextPaint(13f, false));
    }
  }

  private void setPercentages (boolean visible, TdApi.PollOption[] options) {
    prepareOptions(options);
    for (int i = 0; i < options.length; i++) {
      setPercentage(i, visible ? options[i].votePercentage : 0);
    }
  }

  private void setTexts () {
    if (futureState == null) {
      setTotalVoterCount(state.poll);
      setPollStatus(state.poll.isClosed ? POLL_STATUS_CLOSED : POLL_STATUS_ANONYMOUS);
      setPercentages(TD.needShowResults(state.poll), state.poll.options);
      int correctOptionId = state.poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? ((TdApi.PollTypeQuiz) state.poll.type).correctOptionId : -1;
      for (int optionId = 0; optionId < state.poll.options.length; optionId++) {
        options[optionId].selectionFactor = optionId == correctOptionId || state.poll.options[optionId].isChosen ? 1f : 0f;
      }
    } else {
      setTotalVoterCount(futureState.poll);
      if (state.poll.isClosed != futureState.poll.isClosed) {
        setPollStatus(futureState.poll.isClosed ? POLL_STATUS_CLOSED : POLL_STATUS_ANONYMOUS);
      }
      int fromCorrectOptionId = state.poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? ((TdApi.PollTypeQuiz) state.poll.type).correctOptionId : -1;
      int toCorrectOptionId = futureState.poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR ? ((TdApi.PollTypeQuiz) futureState.poll.type).correctOptionId : -1;
      for (int optionId = 0; optionId < state.poll.options.length; optionId++) {
        int fromPercentage = state.resultsVisible ? state.votePercentage(optionId) : 0;
        int toPercentage = futureState.resultsVisible ? futureState.votePercentage(optionId) : 0;
        if (fromPercentage != toPercentage) {
          setPercentage(optionId, fromTo(fromPercentage, toPercentage, changeFactor));
        }
        options[optionId].selectionFactor = fromTo(optionId == fromCorrectOptionId || state.poll.options[optionId].isChosen ? 1f : 0f, optionId == toCorrectOptionId || futureState.poll.options[optionId].isChosen ? 1f : 0f, changeFactor);
      }
    }
  }

  private void updateProgress () {
    if (options == null)
      return;
    final float visibility = getResultsVisibility();
    final float stateVisibility = visibility >= .5f ? 0f : 1f - visibility / .5f;
    final int progressColor = Theme.getColor(getProgressColorId());
    if (isMultiChoicePoll()) {
      float maxProgress = 0f;
      int optionId = 0;
      for (OptionEntry ignored : options) {
        maxProgress = Math.max(maxProgress, getResultProgress(optionId));
        optionId++;
      }
      if (commonProgress != null || maxProgress > 0f) {
        getCommonProgressView().forceColor(ColorUtils.alphaColor(stateVisibility * maxProgress, progressColor));
      }
    } else {
      int optionId = 0;
      for (OptionEntry option : options) {
        float progress = getResultProgress(optionId);
        if (option.progress != null || (progress > 0f && stateVisibility > 0f)) {
          ProgressComponent progressComponent = getResultProgressView(optionId);
          if (progressComponent != null) {
            progressComponent.forceColor(ColorUtils.alphaColor(stateVisibility * progress, progressColor));
          }
        }
        optionId++;
      }
    }
  }

  private static class Button {
    private final int id;
    private final Text text;
    private final boolean isInactive;

    public Button (int id, Text text, boolean isInactive) {
      this.id = id;
      this.text = text;
      this.isInactive = isInactive;
    }

    public boolean isInactive () {
      return isInactive;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof Button && ((Button) obj).id == this.id && ((Button) obj).text.getText().equals(this.text.getText());
    }
  }

  private void setButton (boolean animated) {
    if (this.button == null)
      return;
    int id;
    String str;
    boolean isInactive = false;
    if (isMultiChoicePoll() && !hasAnswer() && canVote(false)) {
      id = R.id.btn_vote;
      str = Lang.getString(R.string.Vote);
    } else if (!isAnonymous() && (hasAnswer() || !canVote(false))) {
      id = R.id.btn_viewResults;
      int voterCount = getPoll().totalVoterCount;
      if (canVote(false) && !TD.hasAnswer(getPoll())) {
        voterCount++;
      }
      if (voterCount == 0 && getPoll().isClosed) {
        str = Lang.getString(isQuiz() ? R.string.NoAnswersResult : R.string.NoVotesResult);
        isInactive = true;
      } else if (voterCount > 1) {
        str = Lang.plural(isQuiz() ? R.string.ViewXQuizResults : R.string.ViewXPollResults, voterCount);
      } else {
        str = Lang.getString(isQuiz() ? R.string.ViewQuizResults : R.string.ViewPollResults);
      }
    } else {
      id = 0; str = null;
    }
    if (str != null) {
      Text text = new Text.Builder(str, getContentMaxWidth(), isInactive ? Paints.robotoStyleProvider(12f) : getNameStyleProvider(), isInactive ? getDecentColorSet() : getLinkColorSet()).singleLine().allBold(!isInactive).build();
      this.button.replace(new Button(id, text, isInactive), animated);
    } else {
      this.button.clear(animated);
    }
    updateButtonState(animated);
  }

  private void updateButtonState (boolean animated) {
    if (isButtonActive == null || button == null)
      return;

    boolean isActive = false;
    if (isMultiChoicePoll() && !hasAnswer() && canVote(false)) {
      if (options != null) {
        for (OptionEntry entry : options) {
          if (entry.isSelected()) {
            isActive = true;
            break;
          }
        }
      }
    } else if (!isAnonymous() && (hasAnswer() || !canVote(false))) {
      isActive = true;
    } else {
      isActive = false;
    }

    isButtonActive.setValue(isActive, animated);
  }

  // Animation

  private void resetPollAnimation (boolean applyFuture) {
    if (animator != null) {
      animator.cancel();
      animator.forceFactor(0f);
    }
    if (futureState != null) {
      if (applyFuture) {
        state = new PollState(tdlib, state, futureState, changeFactor);
      }
      futureState = null;
    }
    if (recentVoters != null) {
      recentVoters.stopAnimation(applyFuture);
    }
    if (button != null) {
      button.stopAnimation(applyFuture);
    }
    changeFactor = 0f;
  }

  private void setChangeFactor (float factor) {
    if (this.changeFactor != factor) {
      this.changeFactor = factor;
      // Looks better when synchronized with everything else
      // float animationFactor = factor > (180f / 280f) ? 1f : factor / (180f / 280f);
      if (recentVoters != null) {
        recentVoters.applyAnimation(factor);
      }
      if (button != null) {
        button.applyAnimation(factor);
      }
      setTexts();
      updateProgress();
      if (timerProgress != null) {
        timerProgress.setAlpha(getTimerVisibility());
      }
      invalidate();
    }
  }

  @Override
  protected void onChildFactorChanged (int id, float factor, float fraction) {
    switch (id) {
      case ANIMATOR_CHANGE: {
        setChangeFactor(factor);
        break;
      }
      case ANIMATOR_SELECT:
      case ANIMATOR_BUTTON: {
        invalidate();
        break;
      }
    }
  }

  @Override
  protected void onChildFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CHANGE: {
        if (finalFactor == 1f && this.futureState != null) {
          setChangeFactor(1f);
          if (isMultiChoicePoll() && hasAnswer()) {
            for (OptionEntry entry : options) {
              if (entry.isSelected != null) {
                entry.isSelected.setValue(false, false);
                entry.isSelected = null;
              }
            }
          }
          this.state = futureState;
          this.futureState = null;
          this.animator.forceFactor(0f);
          this.changeFactor = 0f;
        }
        break;
      }
    }
  }

  // Touch

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    boolean res = super.onTouchEvent(view, e);
    return (!isEventLog() && clickHelper.onTouchEvent(view, e)) || res;
  }

  private int clickOptionId = -1;

  @Override
  public boolean performLongPress (View view, float x, float y) {
    clickHelper.cancel(view, x, y);
    return super.performLongPress(view, x, y);
  }

  @Override
  public boolean needClickAt (View view, final float originalX, final float originalY) {
    final float x = originalX - getContentX();
    final float y = originalY - getContentY();
    final int maxWidth = getContentMaxWidth();

    if (x < 0 || y < 0 || x > maxWidth || isNotSent()) {
      return false;
    }

    int startY = questionText.getHeight() + Screen.dp(5f);
    if (explanationDrawable != null && getHintVisibility() > 0f) {
      float cx = maxWidth - explanationDrawable.getMinimumWidth() / 2f - Screen.dp(2f);
      float cy = startY + pollStatusText.getHeight() / 2f;

      float padding = Screen.dp(6f);
      float boundX = explanationDrawable.getMinimumWidth() / 2f + padding;
      float boundY = explanationDrawable.getMinimumHeight() / 2f + padding;

      if (x >= cx - boundX && x <= cx + boundX && y >= cy - boundY && y <= cy + boundY) {
        clickOptionId = HIGHLIGHT_EXPLANATION;
        return true;
      }
    }
    startY += Screen.dp(18f);

    int optionId = 0;

    for (OptionEntry option : options) {
      int optionHeight = getOptionHeight(option.text);
      if (y >= startY && y < startY + optionHeight) {
        clickOptionId = optionId;
        return true;
      }
      startY += optionHeight;
      optionId++;
    }

    if (isButtonActive != null && isButtonActive.getValue() && button.singleton() != null && !button.singleton().item.isInactive && y >= startY && y < getContentHeight() + (useBubbles() ? getBubbleContentPadding() : 0)) {
      clickOptionId = HIGHLIGHT_BUTTON;
      return true;
    }

    return false;
  }

  @Override
  public void onClickTouchDown (View view, float x, float y) {
    if (clickOptionId <= HIGHLIGHT_NONE || canVote(true)) {
      setHighlightOption(clickOptionId, view, x, y);
    }
  }

  @Override
  public void onClickTouchUp (View view, float x, float y) {
    setHighlightOption(HIGHLIGHT_NONE, view, x, y);
  }

  private TooltipOverlayView.TooltipInfo explanationPopup;

  @Override
  public void onVisibilityChanged (TooltipOverlayView.TooltipInfo tooltipInfo, float visibilityFactor) {
    if (explanationPopup == tooltipInfo) {
      setExplanationActive(MathUtils.clamp(visibilityFactor));
    }
  }

  @Override
  public void onVisibilityChangeFinished (TooltipOverlayView.TooltipInfo tooltipInfo, boolean isVisible) {
    if (explanationPopup == tooltipInfo && !isVisible) {
      explanationPopup = null;
    }
  }

  private void showExplanation (View view) {
    TdApi.FormattedText formattedText = TD.getExplanation(getPoll());
    if (!Td.isEmpty(formattedText)) {
      if (explanationPopup != null) {
        explanationPopup.removeListener(this);
      }
      explanationPopup = buildContentHint(view, (targetView, outRect) -> {
        outRect.set(0, 0, questionText.getWidth(), questionText.getHeight());
      }).icon(R.drawable.baseline_info_24).needBlink(true).chatTextSize(-2f).interceptTouchEvents(true).handleBackPress(true).show(tdlib, formattedText).addListener(this);
    }
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (clickOptionId != HIGHLIGHT_NONE) {
      if (clickOptionId == HIGHLIGHT_EXPLANATION) {
        showExplanation(view);
      } else if (clickOptionId == HIGHLIGHT_BUTTON) {
        if (button.singleton() != null) {
          if (isScheduled()) {
            showContentHint(view, (targetView, outRect) -> {
              int startY = questionText.getHeight() + Screen.dp(5f);
              startY += Screen.dp(18f);
              for (OptionEntry options : options) {
                startY += Math.max(Screen.dp(46f), options.text.getHeight()) + Screen.separatorSize();
              }
              outRect.set(0, startY, getContentWidth(), getContentHeight());
            }, R.string.ErrorScheduled);
          } else {
            switch (button.singleton().item.id) {
              case R.id.btn_vote: {
                IntList selectedOptions = new IntList(this.options.length);
                IntList currentOptions = new IntList(selectedOptions.size());
                int optionId = 0;
                for (OptionEntry entry : options) {
                  if (entry.isSelected()) {
                    selectedOptions.append(optionId);
                  }
                  if (getPoll().options[optionId].isBeingChosen) {
                    currentOptions.append(optionId);
                  }
                  optionId++;
                }
                int[] selectedOptionIds = selectedOptions.get();
                int[] currentOptionIds = currentOptions.get();
                if (isAnonymous() || messagesController().callNonAnonymousProtection(msg.id + R.id.btn_vote, this, makeVoteButtonLocationProvider())) {
                  if (Arrays.equals(selectedOptionIds, currentOptionIds)) {
                    tdlib.client().send(new TdApi.SetPollAnswer(msg.chatId, msg.id, null), tdlib.okHandler());
                  } else {
                    tdlib.client().send(new TdApi.SetPollAnswer(msg.chatId, msg.id, selectedOptionIds), tdlib.okHandler());
                  }
                }
                break;
              }
              case R.id.btn_viewResults: {
                PollResultsController c = new PollResultsController(context(), tdlib());
                c.setArguments(new PollResultsController.Args(getPoll(), msg.chatId, msg.id));
                navigateTo(c);
                break;
              }
            }
          }
        }
      } else if (isScheduled()) {
        final int selectedOptionId = clickOptionId;
        showContentHint(view, (targetView, outRect) -> {
          int startY = questionText.getHeight() + Screen.dp(5f);
          startY += Screen.dp(18f);
          int optionId = 0;
          for (OptionEntry option : options) {
            int optionHeight = getOptionHeight(option.text);
            if (selectedOptionId == optionId) {
              int progressCx = Screen.dp(12f);
              int progressCy = startY + Screen.dp(22f);
              int progressRadius = Screen.dp(9f);
              outRect.set(progressCx - progressRadius, progressCy - progressRadius, progressCx + progressRadius, progressCy + progressRadius);
              return;
            }
            startY += optionHeight;
            optionId++;
          }
          outRect.set(0, 0, 0, 0);
        }, R.string.ErrorScheduled);
      } else if (!canVote(true)) {
        final int selectedOptionId = clickOptionId;
        showContentHint(view, (targetView, outRect) -> {
          int startY = questionText.getHeight() + Screen.dp(5f);
          startY += Screen.dp(18f);
          int optionId = 0;
          for (OptionEntry option : options) {
            int optionHeight = getOptionHeight(option.text);
            if (selectedOptionId == optionId) {
              startY += Screen.dp(15f);
              outRect.set(Screen.dp(34f), startY, Screen.dp(34f) + option.text.getLineWidth(0), startY + option.text.getLineHeight());
              return;
            }
            startY += optionHeight;
            optionId++;
          }
          outRect.set(0, 0, 0, 0);
        }, TD.toFormattedText(getCounter(getPoll(), getPoll().options[selectedOptionId].voterCount), false));
      } else if (isMultiChoicePoll()) {
        selectUnselect(clickOptionId);
      } else {
        chooseOption(clickOptionId);
      }
      clickOptionId = HIGHLIGHT_NONE;
    }
  }

  private int getOptionHeight (TextWrapper text) {
    return Math.max(Screen.dp(46f), Math.max(Screen.dp(8f), (Screen.dp(46f) / 2 - text.getLineHeight() / 2)) + text.getHeight() + Screen.dp(12f)) + Screen.separatorSize();
  }

  private void chooseOption (final int optionId) {
    if (isAnonymous() || messagesController().callNonAnonymousProtection(msg.id + optionId, this, makeButtonLocationProvider(optionId))) {
      if (getPoll().options[optionId].isBeingChosen) {
        tdlib.client().send(new TdApi.SetPollAnswer(msg.chatId, msg.id, null), tdlib.okHandler());
      } else {
        tdlib.client().send(new TdApi.SetPollAnswer(msg.chatId, msg.id, new int[] {optionId}), tdlib.okHandler());
      }
    }
  }

  private void selectUnselect (final int optionId) {
    if (options[optionId].isSelected == null) {
      options[optionId].isSelected = new BoolAnimator(ANIMATOR_SELECT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
    }
    options[optionId].isSelected.toggleValue(needAnimateChanges());
    updateButtonState(needAnimateChanges());
  }

  private static final int HIGHLIGHT_NONE = -1;
  private static final int HIGHLIGHT_BUTTON = -2;
  private static final int HIGHLIGHT_EXPLANATION = -3;
  private int highlightOptionId = HIGHLIGHT_NONE;

  private void setHighlightOption (int optionId, View view, float x, float y) {
    if (highlightOptionId != optionId) {
      this.highlightOptionId = optionId;
      invalidate();
    }
  }

  private TooltipOverlayView.LocationProvider makeVoteButtonLocationProvider () {
    return (targetView, outRect) -> {
      int startY = questionText.getHeight() + Screen.dp(28f);
      for (OptionEntry option : options) {
        int optionHeight = getOptionHeight(option.text);
        startY += optionHeight;
      }
      outRect.set(0, startY, getContentMaxWidth(), startY + Screen.dp(50));
      outRect.offset(getContentX(), getContentY());
    };
  }

  private TooltipOverlayView.LocationProvider makeButtonLocationProvider (int selectedOptionId) {
    return (targetView, outRect) -> {
      int startY = questionText.getHeight() + Screen.dp(5f);
      int optionId = 0;
      for (OptionEntry option : options) {
        int optionHeight = getOptionHeight(option.text);
        if (selectedOptionId == optionId) {
          startY += Screen.dp(15f + 12f);
          outRect.set(Screen.dp(0f), startY, Screen.dp(24f), startY + option.text.getLineHeight());
          outRect.offset(getContentX(), getContentY());
          return;
        }
        startY += optionHeight;
        optionId++;
      }
      outRect.set(0, 0, 0, 0);
    };
  }
}
