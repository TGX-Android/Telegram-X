package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.ReactionsListAnimator;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;

public class TGReactions {
  private final Tdlib tdlib;
  private TdApi.MessageReaction[] reactions;
  private ComplexReceiver complexReceiver;

  private final TGMessage parent;

  private final HashMap<String, TdApi.MessageReaction> tdReactionsMap;
  private final HashMap<String, TGReactions.MessageReactionEntry> reactionsMapEntry;
  private final ArrayList<TGReactions.MessageReactionEntry> reactionsListEntry;

  private final MessageReactionsDelegate delegate;
  private int totalCount;
  private boolean hasReaction;
  private String chosenReaction;

  private final ReactionsListAnimator reactionsAnimator;
  private int width = 0;
  private int height = 0;
  private int lastLineWidth = 0;

  TGReactions (TGMessage parent, Tdlib tdlib, TdApi.MessageReaction[] reactions, MessageReactionsDelegate delegate) {
    this.parent = parent;
    this.delegate = delegate;

    this.reactionsListEntry = new ArrayList<>();
    this.reactionsMapEntry = new HashMap<>();
    this.tdReactionsMap = new HashMap<>();

    this.totalCount = 0;
    this.hasReaction = false;
    this.chosenReaction = "";
    this.tdlib = tdlib;
    this.reactionsAnimator = new ReactionsListAnimator((a) -> parent.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    setReactions(reactions);
    updateCounterAnimators(false);
    resetReactionsAnimator(false);
  }

  public void setReceiversPool (ComplexReceiver complexReceiver) {
    this.complexReceiver = complexReceiver;
    for (Map.Entry<String, MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      MessageReactionEntry entry = pair.getValue();
      entry.setComplexReceiver(complexReceiver);
    }
  }

  public void setReactions (TdApi.MessageReaction[] reactions) {
    this.reactionsListEntry.clear();
    this.tdReactionsMap.clear();
    this.reactions = reactions;
    this.hasReaction = false;
    this.chosenReaction = "";
    this.totalCount = 0;

    if (reactions == null) {
      return;
    }

    for (TdApi.MessageReaction reaction : reactions) {
      tdReactionsMap.put(reaction.reaction, reaction);
      totalCount += reaction.totalCount;
      hasReaction |= reaction.isChosen;
      if (reaction.isChosen) {
        chosenReaction = reaction.reaction;
      }

      TGReaction reactionObj = tdlib.getReaction(reaction.reaction);
      if (reactionObj == null) {
        continue;
      }
      MessageReactionEntry entry = getMessageReactionEntry(reactionObj);
      entry.setMessageReaction(reaction);
      reactionsListEntry.add(entry);
    }
  }

  public void setReactions (ArrayList<TdApi.Message> combinedMessages) {
    this.reactionsListEntry.clear();
    this.hasReaction = false;
    this.chosenReaction = "";
    this.totalCount = 0;

    HashMap<String, TdApi.MessageReaction> reactionsHashMap = new HashMap<>();

    for (TdApi.Message message : combinedMessages) {
      if (message.interactionInfo == null) {
        continue;
      }
      if (message.interactionInfo.reactions == null) {
        continue;
      }

      for (TdApi.MessageReaction reaction : message.interactionInfo.reactions) {
        final TdApi.MessageReaction fakeReaction;
        if (reactionsHashMap.containsKey(reaction.reaction)) {
          fakeReaction = reactionsHashMap.get(reaction.reaction);
        } else {
          fakeReaction = new TdApi.MessageReaction(reaction.reaction, 0, false, new TdApi.MessageSender[0]);
          reactionsHashMap.put(reaction.reaction, fakeReaction);
        }
        fakeReaction.totalCount += reaction.totalCount;
        totalCount += reaction.totalCount;
        hasReaction |= reaction.isChosen;
        if (reaction.isChosen) {
          chosenReaction = reaction.reaction;
        }
      }
    }

    if (hasReaction) {
      reactionsHashMap.get(chosenReaction).isChosen = true;
    }

    TdApi.MessageReaction[] combinedReactionsArray = new TdApi.MessageReaction[reactionsHashMap.size()];
    int i = 0;
    for (Map.Entry<String, TdApi.MessageReaction> pair : reactionsHashMap.entrySet()) {
      combinedReactionsArray[i++] = pair.getValue();
    }

    Arrays.sort(combinedReactionsArray, (a, b) -> b.totalCount - a.totalCount);
    setReactions(combinedReactionsArray);
  }

  @Nullable
  public TdApi.MessageReaction[] getReactions () {
    return reactions;
  }

  public String getChosen () {
    return chosenReaction;
  }

  private MessageReactionEntry getMessageReactionEntry (TGReaction reactionObj) {
    final MessageReactionEntry entry;
    if (!reactionsMapEntry.containsKey(reactionObj.reaction.reaction)) {
      Counter.Builder counterBuilder = new Counter.Builder()
        .allBold(false)
        .callback(parent)
        .textSize(TGMessage.reactionsTextStyleProvider().getTextSizeInDp())
        .noBackground()
        .textColor(R.id.theme_color_badgeText, R.id.theme_color_badgeText, R.id.theme_color_badgeText);
      entry = new MessageReactionEntry(tdlib, delegate, parent, reactionObj, counterBuilder);
      if (complexReceiver != null) {
        entry.setComplexReceiver(complexReceiver);
      }

      reactionsMapEntry.put(reactionObj.reaction.reaction, entry);
    } else {
      entry = reactionsMapEntry.get(reactionObj.reaction.reaction);
    }
    return entry;
  }

  public void onUpdateTextSize () {
    reactionsMapEntry.clear();
    setReactions(getReactions());
    updateCounterAnimators(false);
  }

  public void updateCounterAnimators (boolean animated) {
    if (reactions == null) {
      return;
    }
    for (TdApi.MessageReaction reaction : reactions) {
      TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction.reaction);
      if (entry != null) {
        entry.setCount(reaction.totalCount, reaction.isChosen, animated);
      }
    }
  }

  public void resetReactionsAnimator (boolean animated) {
    if (reactions == null) {
      reactionsAnimator.clear(animated);
      return;
    }
    reactionsAnimator.reset(reactionsListEntry, animated);
  }

  public void measureReactionBubbles (int maxWidth) {
    measureReactionBubbles(maxWidth, 0);
  }

  public void measureReactionBubbles (int maxWidth, int timeWidth) {
    if (maxWidth == 0) {
      maxWidth = 1;
    }
    int bubbleHeight = getReactionBubbleHeight();
    int padding = Screen.dp(6);
    int x = 0;
    int y = 0;

    width = 0;
    height = 0;
    lastLineWidth = 0;

    for (TGReactions.MessageReactionEntry entry : reactionsListEntry) {
      Counter counter = entry.counter;

      int bubbleWidth = entry.getBubbleTargetWidth();
      int nextLastLineWidth = lastLineWidth + bubbleWidth + (lastLineWidth > 0 ? padding : 0);
      if (nextLastLineWidth > maxWidth) {
        nextLastLineWidth = bubbleWidth;
        y += bubbleHeight + padding;
        x = 0;
      } else {
        x = nextLastLineWidth - bubbleWidth;
      }

      entry.setPosition(x, y);

      lastLineWidth = nextLastLineWidth;
      width = Math.max(width, lastLineWidth);
      height = y + bubbleHeight;
    }
    reactionsAnimator.setLayoutParams(maxWidth, timeWidth, parent.getForceTimeExpandHeightByReactions());
  }

  public static int getButtonsPadding () {
    return Screen.dp(6);
  }

  public int getBubblesCount () {
    return reactionsListEntry.size();
  }

  public static int getReactionBubbleHeight () {
    return Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1) * 1.25f + 11);
  }

  public static int getReactionImageSize () {
    return Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1) * 1.25f + 17);
  }


  // target values

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public int getLastLineWidth () {
    return lastLineWidth;
  }



  // animated values

  public float getAnimatedWidth () {
    return reactionsAnimator.getMetadata().getTotalWidth();
  }

  public float getAnimatedHeight () {
    return reactionsAnimator.getMetadata().getTotalHeight();
  }

  public float getAnimatedLastLineWidth () {
    return reactionsAnimator.getMetadata().getLastLineWidth();
  }

  public float getVisibility () {
    return reactionsAnimator.getMetadata().getVisibility();
  }

  public float getTimeHeightExpand () {
    return reactionsAnimator.getMetadata().getTimeHeightExpand();
  }

  public boolean hasChosen () {
    return hasReaction;
  }

  public int getTotalCount () {
    return totalCount;
  }

  public ReactionsListAnimator getReactionsAnimator () {
    return reactionsAnimator;
  }

  private int lastDrawX, lastDrawY;

  public void drawReactionBubbles (Canvas c, MessageView view, int x, int y) {
    lastDrawX = x;
    lastDrawY = y;
/*
    c.drawRect(x - Screen.dp(10), y, x + reactionsAnimator.getReactionsMaxWidth() + Screen.dp(10), y + getHeight(), Paints.strokeSmallPaint(Color.MAGENTA));
    c.drawRect(x - Screen.dp(10), y - Screen.dp(9), x + getWidth() + Screen.dp(10), y + getHeight() + Screen.dp(10), Paints.strokeSmallPaint(Color.RED));
    c.drawRect(x, y, x + getAnimatedWidth(), y + getAnimatedHeight(), Paints.strokeSmallPaint(Color.GREEN));
    c.drawRect(x, y - Screen.dp(20), x, y + getAnimatedHeight() + Screen.dp(20), Paints.strokeSmallPaint(Color.GREEN));
    c.drawRect(x, y, x + reactionsAnimator.getReactionsMaxWidth(), y + getHeight(), Paints.strokeSmallPaint(Color.MAGENTA));
*/
    float oldChosenPosition = -1f;
    float newChosenPosition = -2f;
    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry item = reactionsAnimator.getEntry(a);
      TdApi.MessageReaction messageReaction = item.item.getMessageReaction();
      if (messageReaction.isChosen) {
        if (!item.isAffectingList()) {
          oldChosenPosition = item.getPosition();
        } else {
          newChosenPosition = item.getPosition();
        }
      }
    }


    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry item = reactionsAnimator.getEntry(a);
      TdApi.MessageReaction messageReaction = item.item.getMessageReaction();
      int appearFlags = MessageReactionEntry.TYPE_APPEAR_NONE_FLAG;

      if (oldChosenPosition == newChosenPosition && messageReaction.isChosen && item.getPosition() == oldChosenPosition) {
        appearFlags |= MessageReactionEntry.TYPE_APPEAR_OPACITY_FLAG;
      } else {
        appearFlags |= MessageReactionEntry.TYPE_APPEAR_SCALE_FLAG;
      }

      item.item.drawReactionInBubble(view, c, x + item.getRectF().left, y + item.getRectF().top, item.getVisibility(), appearFlags);
    }
  }

  public int getReactionBubbleX (String reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction);
    if (entry == null) {
      return 0;
    }
    return entry.getX();
  }

  public int getReactionBubbleY (String reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction);
    if (entry == null) {
      return 0;
    }
    return entry.getY();
  }

  public float getReactionPositionInList (String reaction) {
    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry item = reactionsAnimator.getEntry(a);
      if (item.item.reaction.equals(reaction)) {
        return item.getPosition();
      }
    }
    return 0;
  }

  @Nullable
  public MessageReactionEntry findBubbleByPosition (int x, int y) {
    for (int a = 0; a < reactionsListEntry.size(); a++) {
      MessageReactionEntry entry = reactionsListEntry.get(a);
      if (entry.checkTouch(x, y)) {
        return entry;
      }
    }

    return null;
  }

  private MessageReactionEntry activeEntry = null;

  public boolean onTouchEvent (View view, MotionEvent e) {
    if (reactionsListEntry.isEmpty()) {
      return false;
    }

    int x = Math.round(e.getX() - lastDrawX);
    int y = Math.round(e.getY() - lastDrawY);

    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      activeEntry = findBubbleByPosition(x, y);
    }

    boolean result = activeEntry != null && activeEntry.onTouchEvent(view, e, Math.round(x - activeEntry.getX()), Math.round(y - activeEntry.getY()));
    if (activeEntry != null && (e.getAction() == MotionEvent.ACTION_CANCEL || e.getAction() == MotionEvent.ACTION_UP)) {
      activeEntry = null;
    }
    return result;
  }

  public boolean performLongPress (View view) {
    if (activeEntry != null) {
      return activeEntry.performLongPress(view);
    }

    return false;
  }

  public void startAnimation (String emoji) {
    MessageReactionEntry entry = reactionsMapEntry.get(emoji);
    if (entry != null) {
      entry.startAnimation();
      entry.setHidden(false);
    }
  }

  public void prepareAnimation (String emoji) {
    MessageReactionEntry entry = reactionsMapEntry.get(emoji);
    if (entry != null) {
      entry.prepareAnimation();
    }
  }

  public void setHidden (String emoji, boolean hidden) {
    MessageReactionEntry entry = reactionsMapEntry.get(emoji);
    if (entry != null) {
      entry.setHidden(hidden);
    }
  }

  @Nullable
  public MessageReactionEntry getMessageReactionEntry (String emoji) {
    MessageReactionEntry entry = reactionsMapEntry.get(emoji);
    return entry;
  }

  public TdApi.MessageReaction getTdMessageReaction (String emoji) {
    TdApi.MessageReaction reaction = tdReactionsMap.get(emoji);
    if (reaction != null) {
      return reaction;
    }
    return new TdApi.MessageReaction(emoji, 0, false, new TdApi.MessageSender[0]);
  }

  public boolean sendReaction (String reaction, boolean isBig, Client.ResultHandler handler) {
    TdApi.Message message = parent.getFirstMessageInCombined();
    boolean needUnset = reaction.equals(chosenReaction) && !isBig;
    tdlib.client().send(new TdApi.SetMessageReaction(parent.getChatId(), message.id, needUnset ? "" : reaction, isBig), handler);
    return !needUnset;
  }

  public static class MessageReactionEntry implements TextColorSet, FactorAnimator.Target {
    public static final int TYPE_APPEAR_NONE_FLAG = 0;
    public static final int TYPE_APPEAR_SCALE_FLAG = 1;
    public static final int TYPE_APPEAR_OPACITY_FLAG = 2;

    private final Counter counter;
    private final String reaction;
    private final TGReaction reactionObj;
    private final TGMessage message;

    @Nullable private GifReceiver staticCenterAnimationReceiver;
    @Nullable private GifReceiver centerAnimationReceiver;
    @Nullable private final GifFile animation;
    private final GifFile staticAnimationFile;

    private final MessageReactionsDelegate delegate;

    private final Path path;
    private final RectF rect;

    private int x;
    private int y;

    public MessageReactionEntry (Tdlib tdlib, MessageReactionsDelegate delegate, TGMessage message, TGReaction reaction, Counter.Builder counter) {
      this.reactionObj = reaction;
      this.reaction = reaction.reaction.reaction;
      this.message = message;
      this.delegate = delegate;

      this.path = new Path();
      this.rect = new RectF();

      this.counter = counter.colorSet(this).build();

      animation = reactionObj.newCenterAnimationSicker().getFullAnimation();
      staticAnimationFile = reactionObj.staticCenterAnimationSicker().getPreviewAnimation();
      if (animation != null) {
        animation.setPlayOnce(true);
        animation.setLooped(true);
      }
    }

    // Receivers

    public void setComplexReceiver (ComplexReceiver complexReceiver) {
      if (complexReceiver == null) {
        this.centerAnimationReceiver = null;
        this.staticCenterAnimationReceiver = null;
        return;
      }

      centerAnimationReceiver = complexReceiver.getGifReceiver(reactionObj.getId());
      staticCenterAnimationReceiver = complexReceiver.getGifReceiver(((long) reactionObj.getId()) << 32);

      if (staticCenterAnimationReceiver != null) {
        staticCenterAnimationReceiver.requestFile(staticAnimationFile);
      }
      if (centerAnimationReceiver != null && inAnimation) {
        centerAnimationReceiver.requestFile(animation);
      }
      invalidate();
    }

    public void startAnimation () {
      if (animation != null) {
        inAnimation = true;
        animation.setLooped(false);
        if (centerAnimationReceiver != null) {
          centerAnimationReceiver.requestFile(animation);
        }
      }
      invalidate();
    }

    public void prepareAnimation () {
      if (animation != null) {
        if (centerAnimationReceiver != null) {
          centerAnimationReceiver.requestFile(animation);
        }
      }
    }

    // Touch

    private int flags;
    private int anchorX, anchorY;

    private static final int FLAG_ACTIVE = 0x01;
    private static final int FLAG_CAUGHT = 0x02;
    private static final int FLAG_BLOCKED = 0x04;

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

    public boolean performLongPress (View view) {
      if ((flags & FLAG_CAUGHT) != 0) {
        flags &= ~FLAG_CAUGHT;
        if (!isActive()) {
          cancelSelection();
          delegate.onLongClick(view,this);
        }
      }
      return true;
    }

    private void cancelSelection () {
      animateFadeFactor(1f);
    }

    public void performClick (View view) {
      if (!isBlocked()) {
        performAction(view);
        if (!isActive()) {
          cancelSelection();
        }
      }
    }

    private void performAction (View view) {
      delegate.onClick(view, this);
    }

    public boolean checkTouch (int x, int y) {
      int buttonX = getX();
      int buttonY = getY();
      int buttonWidth = getBubbleWidth();
      int buttonHeight = getBubbleHeight();

      if (buttonX < x && x < buttonX + buttonWidth && buttonY < y && y < buttonY + buttonHeight) {
        return true;
      }

      return false;
    }

    // Animations

    private static final long ANIMATION_DURATION = 180L;
    private static final int SELECTION_ANIMATOR = 0;
    private static final int ACTIVE_ANIMATOR = 1;
    private static final int FADE_ANIMATOR = 2;
    private static final int PROGRESS_ANIMATOR = 3;

    private float selectionFactor;
    private FactorAnimator selectionAnimator;

    private void animateSelectionFactor (float toFactor) {
      if (selectionAnimator == null) {
        selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
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
      }
      flags |= FLAG_BLOCKED;
      fadeAnimator.animateTo(toFactor);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case SELECTION_ANIMATOR: {
          this.selectionFactor = factor;
          break;
        }
        case FADE_ANIMATOR: {
          this.fadeFactor = factor;
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

    private void forceResetSelection () {
      if (fadeAnimator != null) {
        fadeAnimator.forceFactor(this.fadeFactor = 0f);
        flags &= ~FLAG_BLOCKED;
      }
      if (selectionAnimator != null) {
        selectionAnimator.forceFactor(this.selectionFactor = 0f);
      }
    }

    private void invalidate () {
      message.invalidate();
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (obj instanceof MessageReactionEntry) {
        return ((MessageReactionEntry) obj).reactionObj.getId() == this.reactionObj.getId();
      }

      return false;
    }

    @Override
    public int hashCode () {
      return reactionObj.getId();
    }

    public String getReaction () {
      return reaction;
    }

    public TGReaction getTGReaction () {
      return reactionObj;
    }

    public void setCount (int count, boolean chosen, boolean animated) {
      counter.setCount(count, !chosen, animated);
    }

    // Render

    public void drawReactionNonBubble (Canvas c, float x, float cy, float radDp, final float alpha) {
      int radius = Screen.dp(radDp);
      if (!isHidden) {
        drawReceiver(c, (int) x - radius, (int) cy - radius, (int) x + radius, (int) cy + radius, alpha);
      }
    }

    private TdApi.MessageReaction messageReaction;
    private boolean isHidden;
    private boolean inAnimation;

    private void drawReceiver (Canvas c, int l, int t, int r, int b, float alpha) {
      GifReceiver receiver = inAnimation ? centerAnimationReceiver : staticCenterAnimationReceiver;
      if (receiver != null) {
        receiver.setBounds(l, t, r, b);
        receiver.setAlpha(alpha);
        receiver.draw(c);
      }
    }

    public void drawReactionInBubble (MessageView view, Canvas c, float x, float y, float visibility, int appearTypeFlags) {
      final boolean hasScaleSaved = visibility != 1f && (BitwiseUtils.getFlag(appearTypeFlags, TYPE_APPEAR_SCALE_FLAG));
      final float alpha = BitwiseUtils.getFlag(appearTypeFlags, TYPE_APPEAR_OPACITY_FLAG) ? visibility : 1f;

      c.save();
      c.translate(x, y);

      if (hasScaleSaved) {
        c.save();
        c.scale(visibility, visibility, 0, 0);
      }

      int width = getBubbleWidth();
      int height = getBubbleHeight();
      int imageSize = getReactionImageSize();
      int imgY = (height - imageSize) / 2;
      int textX = height + Screen.dp(1);
      int radius = height / 2;
      int backgroundColor = backgroundColor(false);

      rect.set(0, 0, width, height);
      path.reset();
      path.addRoundRect(rect, radius, radius, Path.Direction.CCW);

      if (visibility > 0f) {
        c.drawRoundRect(rect, radius, radius, Paints.fillingPaint( ColorUtils.alphaColor(alpha, backgroundColor)));
        counter.draw(c, textX, getReactionBubbleHeight() / 2f, Gravity.LEFT, alpha, view, R.id.theme_color_badgeFailedText);
        if (!isHidden) {
          drawReceiver(c, Screen.dp(-1), imgY, Screen.dp(-1) + imageSize, imgY + imageSize, alpha);
        }
      }

      int selectionColor = message.useBubbles() ? message.getBubbleButtonRippleColor() : ColorUtils.alphaColor(0.25f, Theme.getColor(R.id.theme_color_bubbleIn_time));
      if (fadeFactor != 0f) {
        selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
      }

      if (selectionFactor != 0f) {
        //if (selectionFactor == 1f || path == null) {
        //  c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(selectionColor));
        //} else {
        int anchorX = Math.max(Math.min(this.anchorX, width), 0);
        int anchorY = Math.max(Math.min(this.anchorY, height), 0);
        float selectionRadius = (float) Math.sqrt(width * width + height * height) * .5f * selectionFactor;
        float centerX = width / 2f;
        float centerY = height / 2f;
        float diffX = centerX - anchorX;
        float diffY = centerY - anchorY;
        float selectionX = /*x + */anchorX + diffX * selectionFactor;
        float selectionY = /*y + */anchorY + diffY * selectionFactor;

        final int saveCount;
        if ((saveCount = ViewSupport.clipPath(c, path)) != Integer.MIN_VALUE) {
          c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
        }
        ViewSupport.restoreClipPath(c, saveCount);
        //}
      }

      if (hasScaleSaved) {
        c.restore();
      }

      c.restore();
    }

    public void setHidden (boolean isHidden) {
      this.isHidden = isHidden;
      invalidate();
    }

    public void setMessageReaction (TdApi.MessageReaction messageReaction) {
      this.messageReaction = messageReaction;
    }

    public TdApi.MessageReaction getMessageReaction () {
      return messageReaction;
    }

    // Measurable

    public void setPosition (int x, int y) {
      this.x = x;
      this.y = y;
    }

    public int getX () {
      return x;
    }

    public int getY () {
      return y;
    }

    public int getBubbleWidth () {
      int addW = Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1f) / 3f);
      return (int) (counter.getWidth() + getReactionImageSize() + addW);
    }

    public int getBubbleTargetWidth () {
      int addW = Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1f) / 3f);
      return (int) (counter.getTargetWidth() + getReactionImageSize() + addW);
    }

    public int getBubbleHeight () {
      return getReactionBubbleHeight();
    }

    // ColorSet

    @Override
    public int defaultTextColor () {
      if (!message.useBubbles()) {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_fillingPositiveContent, R.id.theme_color_fillingActiveContent);
      } else if (message.useStickerBubbleReactions() || message.useMediaBubbleReactions()) {
        return ColorUtils.fromToArgb(
          Theme.getColor(message.isOutgoing() ? R.id.theme_color_bubbleOut_fillingPositiveContent_overlay : R.id.theme_color_bubbleIn_fillingPositiveContent_overlay),
          message.getBubbleDateTextColor(),
          counter.getMuteFactor()
        );
      } else if (message.isOutgoing()) {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_bubbleOut_fillingPositiveContent, R.id.theme_color_bubbleOut_fillingActiveContent);
      } else {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_bubbleIn_fillingPositiveContent, R.id.theme_color_bubbleIn_fillingActiveContent);
      }
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      if (!message.useBubbles()) {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_fillingPositive, R.id.theme_color_fillingActive);
      } else if (message.useStickerBubbleReactions() || message.useMediaBubbleReactions()) {
        return ColorUtils.fromToArgb(
          Theme.getColor(message.isOutgoing() ? R.id.theme_color_bubbleOut_fillingPositive_overlay : R.id.theme_color_bubbleIn_fillingPositive_overlay),
          message.getBubbleDateBackgroundColor(),
          counter.getMuteFactor()
        );
      } else if (message.isOutgoing()) {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_bubbleOut_fillingPositive, R.id.theme_color_bubbleOut_fillingActive);
      } else {
        return counter.getColor(counter.getMuteFactor(), R.id.theme_color_bubbleIn_fillingPositive, R.id.theme_color_bubbleIn_fillingActive);
      }
    }
  }

  public interface MessageReactionsDelegate {
    default void onClick (View v, MessageReactionEntry entry) {}
    default void onLongClick (View v, MessageReactionEntry entry) {}
  }
}
