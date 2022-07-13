package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
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
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class TGReactions /*implements Destroyable*/ {
  private Tdlib tdlib;
  private TdApi.MessageReaction[] reactions;

  private final TGMessage parent;

  private final HashMap<String, TdApi.MessageReaction> reactionsMap;
  private final HashMap<String, TGReactions.MessageReactionEntry> reactionsMapEntry;
  private final ArrayList<TGReactions.MessageReactionEntry> reactionsListEntry;

  private MessageReactionsDelegate delegate;
  private int totalCount;
  private boolean hasReaction;
  private String chosenReaction;

  private final ReactionsListAnimator<MessageReactionEntry> reactionsAnimator;
  private int width = 0;
  private int height = 0;
  private int lastLineWidth = 0;

  TGReactions (TGMessage parent, Tdlib tdlib, TdApi.MessageReaction[] reactions, MessageReactionsDelegate delegate) {
    this.parent = parent;
    this.reactionsMap = new HashMap<>();
    this.delegate = delegate;

    this.reactionsListEntry = new ArrayList<>();
    this.reactionsMapEntry = new HashMap<>();

    this.totalCount = 0;
    this.hasReaction = false;
    this.chosenReaction = "";
    this.tdlib = tdlib;
    this.reactionsAnimator = new ReactionsListAnimator<>((a) -> parent.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    setReactions(reactions);
    updateCounterAnimators(false);
    resetReactionsAnimator(false);
  }

  public void setReactions (TdApi.MessageReaction[] reactions) {
    android.util.Log.i("ANIMATEREACTION", String.format("SET %d", parent.getMessage().id));

    this.reactionsMap.clear();
    this.reactionsListEntry.clear();
    this.reactions = reactions;
    this.hasReaction = false;
    this.chosenReaction = "";
    this.totalCount = 0;

    if (reactions == null) {
      return;
    }

    for (TdApi.MessageReaction reaction : reactions) {
      reactionsMap.put(reaction.reaction, reaction);
      totalCount += reaction.totalCount;
      hasReaction |= reaction.isChosen;
      if (reaction.isChosen) {
        chosenReaction = reaction.reaction;
      }

      TGReaction reactionObj = tdlib.getReaction(reaction.reaction);
      if (reactionObj == null) {
        continue;
      }
      reactionsListEntry.add(getMessageReactionEntry(reactionObj));
    }
  }

  public void setReactions (ArrayList<TdApi.Message> combinedMessages) {
    this.reactionsMap.clear();
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

  /*private View view;
  private boolean attached = false;
  public void attach (View v) {
    for (Map.Entry<String, MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      pair.getValue().drawable.init(v);
      pair.getValue().drawable.attach();
    }
    view = v;
    attached = true;
  }

  public void detach () {
    for (Map.Entry<String, MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      pair.getValue().drawable.detach();
    }
    attached = false;
  }

  @Override
  public void performDestroy () {
    for (Map.Entry<String, MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      pair.getValue().drawable.performDestroy();
    }
  }*/

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
      TGReaction.ReactionDrawable drawable = new TGReaction.ReactionDrawable(parent.findCurrentView(), reactionObj.staticIconSicker(), Screen.dp(20), Screen.dp(20));
      Counter.Builder counterBuilder = new Counter.Builder()
        .allBold(false)
        .callback(parent)
        .textSize(14f)
        .isReaction()
        .textColor(R.id.theme_color_badgeText, R.id.theme_color_badgeText, R.id.theme_color_badgeText)
        .drawable(drawable, 20f, Gravity.LEFT);
      entry = new MessageReactionEntry(tdlib, delegate, parent, reactionObj.reaction.reaction, counterBuilder, drawable);
      reactionsMapEntry.put(reactionObj.reaction.reaction, entry);
      /*if (attached) {
        drawable.init(view);
        drawable.attach();
      }*/
    } else {
      entry = reactionsMapEntry.get(reactionObj.reaction.reaction);
    }
    return entry;
  }

  public void updateCounterAnimators (boolean animated) {
    if (reactions == null) {
      return;
    }
    for (TdApi.MessageReaction reaction : reactions) {
      reactionsMapEntry.get(reaction.reaction).setCount(reaction.totalCount, reaction.isChosen, animated);
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
    if (maxWidth == 0) {
      maxWidth = 1;
    }
    int bubbleHeight = Screen.dp(28);
    int padding = Screen.dp(6);
    int x = 0;
    int y = 0;

    width = 0;
    height = 0;
    lastLineWidth = 0;

    for (TGReactions.MessageReactionEntry entry : reactionsListEntry) {
      Counter counter = entry.counter;

      int bubbleWidth = (int) counter.getTargetWidth() + Screen.dp(20);   // bubble width
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
  }

  public static int getButtonsPadding () {
    return Screen.dp(6);
  }

  public int getBubblesCount () {
    return reactionsListEntry.size();
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

  public boolean hasChosen () {
    return hasReaction;
  }

  public int getTotalCount () {
    return totalCount;
  }

  public HashMap<String, TdApi.MessageReaction> getReactionsMap () {
    return reactionsMap;
  }

  public ReactionsListAnimator<MessageReactionEntry> getReactionsAnimator () {
    return reactionsAnimator;
  }

  private int lastDrawX, lastDrawY;

  public void drawReactionBubbles (Canvas c, MessageView view, int x, int y) {
    lastDrawX = x;
    lastDrawY = y;
    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry<TGReactions.MessageReactionEntry> item = reactionsAnimator.getEntry(a);
      item.item.drawReactionInBubble(view, c, x + item.getRectF().left, y + item.getRectF().top, item.getVisibility());
    }
  }

  public int getLastDrawX () {
    return lastDrawX;
  }

  public int getLastDrawY () {
    return lastDrawY;
  }

  public int getReactionBubbleX (String reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction);
    if (entry == null) {
      return 0;
    }
    return lastDrawX + entry.getX();
  }

  public int getReactionBubbleY (String reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction);
    if (entry == null) {
      return 0;
    }
    return lastDrawY + entry.getY();
  }

  public float getReactionPositionInList (String reaction) {
    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry<TGReactions.MessageReactionEntry> item = reactionsAnimator.getEntry(a);
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

  public boolean sendReaction (String reaction, boolean isBig) {
    TdApi.Message message = parent.getFirstMessageInCombined();
    boolean needUnset = reaction.equals(chosenReaction) && !isBig;
    tdlib.client().send(new TdApi.SetMessageReaction(parent.getChatId(), message.id, needUnset ? "" : reaction, isBig), tdlib.okHandler());

    return !needUnset;
  }

  public static class MessageReactionEntry implements ReactionsListAnimator.Measurable, TextColorSet, FactorAnimator.Target {
    private final Counter counter;
    private final String reaction;
    private final TGReaction reactionObj;

    private final TGMessage message;
    private TGStickerObj staticIconSticker;
    private ImageReceiver staticIconReceiver;
    private MessageReactionsDelegate delegate;
    public TGReaction.ReactionDrawable drawable;

    private final Path path;
    private final RectF rect;

    private int x;
    private int y;
    private int count;

    public MessageReactionEntry (Tdlib tdlib, MessageReactionsDelegate delegate, TGMessage message, String reaction, Counter.Builder counter, TGReaction.ReactionDrawable drawable) {
      this.reactionObj = tdlib.getReaction(reaction);
      this.reaction = reaction;
      this.message = message;
      this.delegate = delegate;
      this.drawable = drawable;

      this.path = new Path();
      this.rect = new RectF();

      this.counter = counter.colorSet(this).build();

      if (reactionObj == null) return;
      staticIconSticker = reactionObj.staticIconSicker();
      ImageFile staticIconFile = staticIconSticker.getFullImage();

      if (staticIconFile == null) return;
      staticIconReceiver = new ImageReceiver(null, 0);
      staticIconReceiver.requestFile(staticIconFile);
    }

    public void setPosition (int x, int y) {
      this.x = x;
      this.y = y;
    }

    public boolean checkTouch (int x, int y) {
      int buttonX = getX();
      int buttonY = getY();
      int buttonWidth = getWidth();
      int buttonHeight = getHeight();

      if (buttonX < x && x < buttonX + buttonWidth && buttonY < y && y < buttonY + buttonHeight) {
        return true;
      }

      return false;
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
          delegate.onLongClick(this);
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
      delegate.onClick(this);
    }

    //

    private static final long ANIMATION_DURATION = 180l;
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

    //

    private void invalidate () {
      message.invalidate();
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (obj instanceof MessageReactionEntry) {
        if (((MessageReactionEntry) obj).staticIconSticker == null || this.staticIconSticker == null)
          return false;
        return ((MessageReactionEntry) obj).staticIconSticker.getId() == this.staticIconSticker.getId();
      }

      return false;
    }

    @Override
    public int hashCode () {
      if (staticIconSticker == null) return 0;
      return staticIconSticker.getId();
    }

    public void drawReaction (Canvas c, float x, float cy, float radDp, final float alpha) {
      staticIconReceiver.setBounds((int) x, (int) cy - Screen.dp(radDp), (int) x + Screen.dp(radDp * 2), (int) cy + Screen.dp(radDp));
      staticIconReceiver.setAlpha(alpha);
      staticIconReceiver.draw(c);
    }

    public void drawReactionInBubble (MessageView view, Canvas c, float x, float y, float visibility) {
      boolean hasScaleSaved = visibility != 1f;
      c.save();
      c.translate(x, y);

      if (hasScaleSaved) {
        c.save();
        c.scale(visibility, visibility, 0, 0);
      }

      counter.draw(c,
        Screen.dp(14f),
        Screen.dp(14f),
        Gravity.LEFT, visibility, view, R.id.theme_color_badgeFailedText);

      int width = getWidth();
      int height = getHeight();
      int radius = height / 2;

      rect.set(0, 0, width, height);
      path.reset();
      path.addRoundRect(rect, radius, radius, Path.Direction.CCW);

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
          float centerX = width / 2;
          float centerY = height / 2;
          float diffX = centerX - anchorX;
          float diffY = centerY - anchorY;
          float selectionX = /*x + */anchorX + diffX * selectionFactor;
          float selectionY = /*y + */anchorY + diffY * selectionFactor;

          final int saveCount;
          if ((saveCount = ViewSupport.clipPath(c, path)) != Integer.MIN_VALUE) {
            c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
          } else {
            //c.drawRoundRect(rounder, radius, radius, Paints.fillingPaint(selectionColor));
          }
          ViewSupport.restoreClipPath(c, saveCount);
        //}
      }

      if (hasScaleSaved) {
        c.restore();
      }

      c.restore();
    }

    public String getReaction () {
      return reaction;
    }

    public Counter getCounter () {
      return counter;
    }

    public void setCount (int count, boolean chosen, boolean animated) {
      this.count = count;
      counter.setCount(count, !chosen, animated);
    }

    public int getCount () {
      return count;
    }

    @Override
    public int getX () {
      return x;
    }

    @Override
    public int getY () {
      return y;
    }

    @Override
    public int getWidth () {
      return (int) (counter.getWidth() + Screen.dp(20));
    }

    @Override
    public int getHeight () {
      return Screen.dp(28);
    }



    // ColorSet

    @Override
    public int defaultTextColor () {
      return counter.getColor(counter.getMuteFactor(), R.id.theme_color_badgeText, R.id.theme_color_text, R.id.theme_color_badgeText);
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      if (message.isOutgoing()) {
        return ColorUtils.alphaColor(
          Math.max(1f - counter.getMuteFactor(), 0.15f),
          counter.getColor(counter.getMuteFactor(), R.id.theme_color_bubbleOut_file, R.id.theme_color_bubbleOut_time, R.id.theme_color_badgeFailed));
      } else {
        return ColorUtils.alphaColor(
          Math.max(1f - counter.getMuteFactor(), 0.15f),
          counter.getColor(counter.getMuteFactor(), R.id.theme_color_file, R.id.theme_color_bubbleIn_time, R.id.theme_color_badgeFailed));
      }
    }
  }

  public interface MessageReactionsDelegate {
    default void onClick (MessageReactionEntry entry) {}
    default void onLongClick (MessageReactionEntry entry) {}
  }
}
