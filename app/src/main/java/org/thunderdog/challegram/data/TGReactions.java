package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ReactionLoadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ReactionsListAnimator;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class TGReactions implements Destroyable, ReactionLoadListener {
  private final Tdlib tdlib;
  private TdApi.MessageReaction[] reactions;

  private final TGMessage parent;

  private final HashMap<String, TdApi.MessageReaction> tdReactionsMap;
  private final HashMap<String, TGReactions.MessageReactionEntry> reactionsMapEntry;
  private final ArrayList<TGReactions.MessageReactionEntry> reactionsListEntry;

  private final MessageReactionsDelegate delegate;
  private int totalCount;
  private final Set<String> chosenReactions;
  private Set<String> awaitingReactions;

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
    this.chosenReactions = new LinkedHashSet<>();
    this.tdlib = tdlib;
    this.reactionsAnimator = new ReactionsListAnimator((a) -> parent.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    setReactions(reactions);
    updateCounterAnimators(false);
    resetReactionsAnimator(false);
  }

  public void requestReactionFiles (ComplexReceiver complexReceiver) {
    for (Map.Entry<String, MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      MessageReactionEntry entry = pair.getValue();
      entry.requestReactionFiles(complexReceiver);
    }
  }

  public void setReactions (TdApi.MessageReaction[] reactions) {
    this.reactionsListEntry.clear();
    this.tdReactionsMap.clear();
    this.reactions = reactions;
    this.chosenReactions.clear();
    this.totalCount = 0;

    if (reactions == null || isDestroyed) {
      return;
    }

    for (TdApi.MessageReaction reaction : reactions) {
      String reactionKey = TD.makeReactionKey(reaction.type);
      tdReactionsMap.put(reactionKey, reaction);
      totalCount += reaction.totalCount;
      if (reaction.isChosen) {
        chosenReactions.add(reactionKey);
      }

      TGReaction reactionObj = tdlib.getReaction(reaction.type);
      if (reactionObj == null) {
        if (awaitingReactions == null) {
          awaitingReactions = new LinkedHashSet<>();
        }
        if (awaitingReactions.add(reactionKey)) {
          tdlib.listeners().addReactionLoadListener(reactionKey, this);
        }
        continue;
      }
      MessageReactionEntry entry = getMessageReactionEntry(reactionObj);
      entry.setMessageReaction(reaction);
      reactionsListEntry.add(entry);
      if (awaitingReactions != null && awaitingReactions.remove(reactionKey)) {
        tdlib.listeners().removeReactionLoadListener(reactionKey, this);
      }
    }
  }

  public void setReactions (ArrayList<TdApi.Message> combinedMessages) {
    this.reactionsListEntry.clear();
    this.chosenReactions.clear();
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
        final String reactionKey = TD.makeReactionKey(reaction.type);
        TdApi.MessageReaction fakeReaction = reactionsHashMap.get(reactionKey);
        if (fakeReaction == null) {
          fakeReaction = new TdApi.MessageReaction(reaction.type, 0, false, null, new TdApi.MessageSender[0]);
          reactionsHashMap.put(reactionKey, fakeReaction);
        }
        fakeReaction.totalCount += reaction.totalCount;
        if (reaction.recentSenderIds != null && reaction.recentSenderIds.length > 0) {
          fakeReaction.recentSenderIds = reaction.recentSenderIds;  // todo conact arrays ?
        }
        fakeReaction.isChosen = reaction.isChosen;
        totalCount += reaction.totalCount;
        if (reaction.isChosen) {
          chosenReactions.add(reactionKey);
        }
      }
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

  public Set<String> getChosen () {
    return chosenReactions;
  }

  public boolean isChosen (TdApi.ReactionType reactionType) {
    return chosenReactions.contains(TD.makeReactionKey(reactionType));
  }

  private MessageReactionEntry getMessageReactionEntry (TGReaction reactionObj) {
    final MessageReactionEntry entry;
    if (!reactionsMapEntry.containsKey(reactionObj.key)) {
      Counter.Builder counterBuilder = new Counter.Builder()
        .allBold(false)
        .callback(parent)
        .textSize(TGMessage.reactionsTextStyleProvider().getTextSizeInDp())
        .noBackground()
        .textColor(ColorId.badgeText, ColorId.badgeText, ColorId.badgeText);
      entry = new MessageReactionEntry(tdlib, delegate, parent, reactionObj, counterBuilder);
      delegate.onInvalidateReceiversRequested();

      reactionsMapEntry.put(reactionObj.key, entry);
    } else {
      entry = reactionsMapEntry.get(reactionObj.key);
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
    final int mode = Settings.instance().getReactionAvatarsMode();

    for (TdApi.MessageReaction reaction : reactions) {
      String reactionKey = TD.makeReactionKey(reaction.type);
      TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reactionKey);
      if (entry != null) {
        TdApi.MessageSender[] recentSenderIds = getRecentSenderIds(reaction, mode);
        recentSenderIds = limitSenders(recentSenderIds, reaction.totalCount > 3 ? 2 : 3);
        entry.setCount(recentSenderIds, reaction.totalCount, reaction.isChosen, animated);
      }
    }
  }

  private TdApi.MessageSender[] limitSenders (TdApi.MessageSender[] senders, int maxCount) {
    return senders != null && senders.length > maxCount ? Arrays.copyOfRange(senders, 0, maxCount) : senders;
  }

  private TdApi.MessageSender[] getRecentSenderIds (TdApi.MessageReaction reaction, int mode) {
    if (reaction.recentSenderIds == null || reaction.recentSenderIds.length == 0)
      return reaction.recentSenderIds;
    if (mode == Settings.REACTION_AVATARS_MODE_NEVER)
      return null;

    // Filter out current user/reaction.usedSenderId, unless reaction.isChosen == true
    List<TdApi.MessageSender> sendersPreFiltered = ArrayUtils.filter(ArrayUtils.asList(reaction.recentSenderIds),
      sender -> !(tdlib.isSelfSender(sender) || Td.equalsTo(reaction.usedSenderId, sender)) || reaction.isChosen
    );

    if (mode == Settings.REACTION_AVATARS_MODE_ALWAYS) {
      return sendersPreFiltered.toArray(new TdApi.MessageSender[0]);
    }

    return ArrayUtils.filter(sendersPreFiltered, (item) -> parent.matchesReactionSenderAvatarFilter(reaction, item)).toArray(new TdApi.MessageSender[0]);
  }

  public void requestAvatarFiles (ComplexReceiver complexReceiver, boolean isUpdate) {
    if (reactions == null) {
      return;
    }
    if (!isUpdate) {
      complexReceiver.clear();
    }
    for (TdApi.MessageReaction reaction : reactions) {
      String reactionKey = TD.makeReactionKey(reaction.type);
      TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reactionKey);
      if (entry != null) {
        entry.requestAvatars(complexReceiver, isUpdate);
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

  public static int getReactionAvatarRadiusDp () {
    return (int) ((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1) * 0.625f + 2.5f);
  }

  public static int getReactionAvatarOutlineDp () {
    return (int) ((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1) / 6f);
  }

  public static int getReactionAvatarSpacingDp () {
    return (int) -((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1) / 3f);
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
    return !chosenReactions.isEmpty();
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

  public int getReactionBubbleX (TGReaction reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction.key);
    if (entry == null) {
      return 0;
    }
    return entry.getX();
  }

  public int getReactionBubbleY (TGReaction reaction) {
    TGReactions.MessageReactionEntry entry = reactionsMapEntry.get(reaction.key);
    if (entry == null) {
      return 0;
    }
    return entry.getY();
  }

  public float getReactionPositionInList (TdApi.ReactionType reactionType) {
    for (int a = 0; a < reactionsAnimator.size(); a++) {
      ReactionsListAnimator.Entry item = reactionsAnimator.getEntry(a);
      if (Td.equalsTo(item.item.reactionType, reactionType)) {
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

  public void startAnimation (String reactionKey) {
    MessageReactionEntry entry = reactionsMapEntry.get(reactionKey);
    if (entry != null) {
      entry.startAnimation();
      entry.setHidden(false);
    }
  }

  public void prepareAnimation (String reactionKey) {
    MessageReactionEntry entry = reactionsMapEntry.get(reactionKey);
    if (entry != null) {
      entry.prepareAnimation();
    }
  }

  public void setHidden (String reactionKey, boolean hidden) {
    MessageReactionEntry entry = reactionsMapEntry.get(reactionKey);
    if (entry != null) {
      entry.setHidden(hidden);
    }
  }

  @Nullable
  public MessageReactionEntry getMessageReactionEntry (String reactionKey) {
    return reactionsMapEntry.get(reactionKey);
  }

  public TdApi.MessageReaction getTdMessageReaction (TdApi.ReactionType reactionType) {
    String reactionKey = TD.makeReactionKey(reactionType);
    TdApi.MessageReaction reaction = tdReactionsMap.get(reactionKey);
    if (reaction != null) {
      return reaction;
    }
    return new TdApi.MessageReaction(reactionType, 0, false, null, new TdApi.MessageSender[0]);
  }

  public boolean hasReaction (TdApi.ReactionType reactionType) {
    String reactionKey = TD.makeReactionKey(reactionType);
    return chosenReactions.contains(reactionKey);
  }

  public boolean toggleReaction (TdApi.ReactionType reactionType, boolean isBig, boolean updateRecentReactions, Client.ResultHandler handler) {
    TdApi.Message message = parent.getOldestMessage();
    boolean hasReaction = !hasReaction(reactionType);
    if (hasReaction) {
      tdlib.client().send(new TdApi.AddMessageReaction(parent.getChatId(), message.id, reactionType, isBig, updateRecentReactions), handler);
    } else {
      tdlib.client().send(new TdApi.RemoveMessageReaction(parent.getChatId(), message.id, reactionType), handler);
    }
    return hasReaction;
  }

  public static class MessageReactionEntry implements TextColorSet, FactorAnimator.Target {
    public static final int TYPE_APPEAR_NONE_FLAG = 0;
    public static final int TYPE_APPEAR_SCALE_FLAG = 1;
    public static final int TYPE_APPEAR_OPACITY_FLAG = 2;

    private final Counter counter;
    private final TGAvatars avatars;
    private final TdApi.ReactionType reactionType;
    private final TGReaction reactionObj;
    private final TGMessage message;

    @Nullable private Receiver staticCenterAnimationReceiver;   // FIXME: single TGMessage may be displayed in multiple MessageView at once.
    @Nullable private GifReceiver centerAnimationReceiver;      // This class wrongly relies that it cannot.
    @Nullable private final GifFile animation;
    private final float animationScale;
    private final GifFile staticAnimationFile;
    private final float staticAnimationFileScale;
    private final ImageFile staticImageFile;
    private final float staticImageFileScale;

    private final MessageReactionsDelegate delegate;

    private final Path path;
    private final RectF rect;

    private int x;
    private int y;

    public MessageReactionEntry (Tdlib tdlib, MessageReactionsDelegate delegate, TGMessage message, TGReaction reaction, Counter.Builder counter) {
      this.reactionObj = reaction;
      this.reactionType = reaction.type;
      this.message = message;
      this.delegate = delegate;

      this.path = new Path();
      this.rect = new RectF();

      this.counter = counter != null ? counter.colorSet(this).build() : null;
      if (message != null) {
        this.avatars = new TGAvatars(tdlib, message, message.currentViews);
        this.avatars.setDimensions(getReactionAvatarRadiusDp(), getReactionAvatarOutlineDp(), getReactionAvatarSpacingDp());
      } else {
        this.avatars = null;
      }

      TGStickerObj stickerObj = reactionObj.newCenterAnimationSicker();
      animation = stickerObj.getFullAnimation();
      animationScale = stickerObj.getDisplayScale();
      if (animation != null && !stickerObj.isCustomReaction()) {
        animation.setPlayOnce(true);
        animation.setLooped(true);
      }

      TGStickerObj staticFile = reactionObj.staticCenterAnimationSicker();
      staticAnimationFile = staticFile.getPreviewAnimation();
      staticAnimationFileScale = staticFile.getDisplayScale();

      if (staticAnimationFile == null) {
        staticFile = reactionObj.staticCenterAnimationSicker();
        staticImageFile = staticFile.getImage();
        staticImageFileScale = staticFile.getDisplayScale();
      } else {
        staticImageFile = null;
        staticImageFileScale = 0f;
      }
    }

    // Receivers

    public void requestReactionFiles (ComplexReceiver complexReceiver) {
      if (complexReceiver == null) {
        this.centerAnimationReceiver = null;
        this.staticCenterAnimationReceiver = null;
        return;
      }

      centerAnimationReceiver = complexReceiver.getGifReceiver(reactionObj.getId());
      long staticId = ((long) reactionObj.getId()) << 32;
      if (staticAnimationFile != null) {
        GifReceiver receiver = complexReceiver.getGifReceiver(staticId);
        receiver.requestFile(staticAnimationFile);
        staticCenterAnimationReceiver = receiver;
      } else if (staticImageFile != null) {
        ImageReceiver receiver = complexReceiver.getImageReceiver(staticId);
        receiver.requestFile(staticImageFile);
        staticCenterAnimationReceiver = receiver;
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

    public void invalidate () {
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

    public TdApi.ReactionType getReactionType () {
      return reactionType;
    }

    public TGReaction getTGReaction () {
      return reactionObj;
    }

    public void setCount (TdApi.MessageSender[] senders, int count, boolean chosen, boolean animated) {
      boolean hasSenders = senders != null && senders.length > 0;
      int countToDisplay = count - (hasSenders ? senders.length: 0);
      int value = countToDisplay > 0 ? BitwiseUtils.setFlag(countToDisplay, 1 << 30, hasSenders): 0;
      String text = hasSenders ? "+" + Strings.buildCounter(countToDisplay): Strings.buildCounter(countToDisplay);

      counter.setCount(value, !chosen, text, animated);
      avatars.setSenders(senders, animated);
    }

    public void requestAvatars (ComplexReceiver complexReceiver, boolean isUpdate) {
      avatars.requestFiles(complexReceiver, isUpdate, true);
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
      Receiver receiver = inAnimation ? centerAnimationReceiver : staticCenterAnimationReceiver;
      float scale = inAnimation ? animationScale : staticAnimationFile != null ? staticAnimationFileScale : staticImageFileScale;
      if (receiver != null) {
        // TODO contour placeholder
        if (reactionObj.needThemedColorFilter()) {
          receiver.setThemedPorterDuffColorId(ColorId.text);
        } else {
          receiver.disablePorterDuffColorFilter();
        }
        receiver.setBounds(l, t, r, b);
        receiver.setAlpha(alpha);
        receiver.drawScaled(c, scale);
      }
    }

    public void drawReactionInBubble (MessageView view, Canvas c, float x, float y, float visibility, int appearTypeFlags) {
      final boolean hasScaleSaved = visibility != 1f && (BitwiseUtils.hasFlag(appearTypeFlags, TYPE_APPEAR_SCALE_FLAG));
      final float alpha = BitwiseUtils.hasFlag(appearTypeFlags, TYPE_APPEAR_OPACITY_FLAG) ? visibility : 1f;

      c.save();
      c.translate(x, y);

      if (hasScaleSaved) {
        c.save();
        c.scale(visibility, visibility, 0, 0);
      }

      int avatarsWidth = (int) avatars.getAnimatedWidth();
      int avatarsOffset = (Screen.dp(2f * avatars.getAvatarsVisibility()));
      int width = getBubbleWidth();
      int height = getBubbleHeight();
      int imageSize = getReactionImageSize();
      int imgY = (height - imageSize) / 2;
      int avatarsX = height + Screen.dp(1);
      int textX = avatarsX + avatarsOffset + avatarsWidth;
      int radius = height / 2;
      int backgroundColor = backgroundColor(false);

      rect.set(0, 0, width, height);
      path.reset();
      path.addRoundRect(rect, radius, radius, Path.Direction.CCW);

      if (visibility > 0f) {
        c.drawRoundRect(rect, radius, radius, Paints.fillingPaint( ColorUtils.alphaColor(alpha, backgroundColor)));
        avatars.draw(view, c, view.getReactionAvatarsReceiver(), avatarsX, getReactionBubbleHeight() / 2, Gravity.LEFT, alpha);
        counter.draw(c, textX, getReactionBubbleHeight() / 2f, Gravity.LEFT, alpha, view, ColorId.badgeFailedText);
        if (!isHidden) {
          drawReceiver(c, Screen.dp(-1), imgY, Screen.dp(-1) + imageSize, imgY + imageSize, alpha);
        }
      }

      int selectionColor = message.useBubbles() ? message.getBubbleButtonRippleColor() : ColorUtils.alphaColor(0.25f, Theme.getColor(ColorId.bubbleIn_time));
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
      float avatarsWidth = avatars.getAnimatedWidth();
      float avatarsOffset = Screen.dp(2f * avatars.getAvatarsVisibility() * counter.getVisibility());
      int addW = Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1f) / 3f);
      int subW = Screen.dp(6f - counter.getVisibility() * 6f);
      return (int) (counter.getWidth() + getReactionImageSize() + addW - subW + avatarsWidth + avatarsOffset);
    }

    public int getBubbleTargetWidth () {
      float avatarsWidth = avatars.getTargetWidth(Screen.dp(counter.getVisibilityTarget() ? 2: 0));
      int addW = Screen.dp((TGMessage.reactionsTextStyleProvider().getTextSizeInDp() + 1f) / 3f);
      int subW = Screen.dp(counter.getVisibilityTarget() ? 0: 6);
      return (int) (counter.getTargetWidth() + getReactionImageSize() + addW - subW + avatarsWidth);
    }

    public int getBubbleHeight () {
      return getReactionBubbleHeight();
    }

    // ColorSet

    @Override
    public int defaultTextColor () {
      if (!message.useBubbles()) {
        return counter.getColor(counter.getMuteFactor(), ColorId.fillingPositiveContent, ColorId.fillingActiveContent);
      } else if (message.useStickerBubbleReactions() || message.useMediaBubbleReactions()) {
        return ColorUtils.fromToArgb(
          Theme.getColor(message.isOutgoing() ? ColorId.bubbleOut_fillingPositiveContent_overlay : ColorId.bubbleIn_fillingPositiveContent_overlay),
          message.getBubbleDateTextColor(),
          counter.getMuteFactor()
        );
      } else if (message.isOutgoing()) {
        return counter.getColor(counter.getMuteFactor(), ColorId.bubbleOut_fillingPositiveContent, ColorId.bubbleOut_fillingActiveContent);
      } else {
        return counter.getColor(counter.getMuteFactor(), ColorId.bubbleIn_fillingPositiveContent, ColorId.bubbleIn_fillingActiveContent);
      }
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      if (!message.useBubbles()) {
        return counter.getColor(counter.getMuteFactor(), ColorId.fillingPositive, ColorId.fillingActive);
      } else if (message.useStickerBubbleReactions() || message.useMediaBubbleReactions()) {
        return ColorUtils.fromToArgb(
          Theme.getColor(message.isOutgoing() ? ColorId.bubbleOut_fillingPositive_overlay : ColorId.bubbleIn_fillingPositive_overlay),
          message.getBubbleDateBackgroundColor(),
          counter.getMuteFactor()
        );
      } else if (message.isOutgoing()) {
        return counter.getColor(counter.getMuteFactor(), ColorId.bubbleOut_fillingPositive, ColorId.bubbleOut_fillingActive);
      } else {
        return counter.getColor(counter.getMuteFactor(), ColorId.bubbleIn_fillingPositive, ColorId.bubbleIn_fillingActive);
      }
    }
  }

  public interface MessageReactionsDelegate {
    default void onClick (View v, MessageReactionEntry entry) {}
    default void onLongClick (View v, MessageReactionEntry entry) {}
    default void onInvalidateReceiversRequested () {}
    default void onRebuildRequested () {}
  }

  private boolean isDestroyed;

  @Override
  public void performDestroy () {
    this.isDestroyed = true;
    if (awaitingReactions != null) {
      for (String reactionKey : awaitingReactions) {
        tdlib.listeners().removeReactionLoadListener(reactionKey, this);
      }
      awaitingReactions.clear();
    }
  }

  @Override
  public void onReactionLoaded (String reactionKey) {
    if (awaitingReactions != null && awaitingReactions.remove(reactionKey)) {
      delegate.onRebuildRequested();
    }
  }
}