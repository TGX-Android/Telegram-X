package org.thunderdog.challegram.component.reactions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import me.vkryl.core.lambda.Destroyable;

import static org.thunderdog.challegram.component.reactions.Reaction.DisplayMode.MINI;

public class MessageReactionsContainer implements Destroyable {

    private static final int DEFAULT_H_SPACE = 8;
    private static final int DEFAULT_V_SPACE = 8;

    private final Tdlib tdlib;
    private final ReactionsManager reactionsManager;
    private Map<String, TGReaction> reactionsMap;
    private Map<String, Reaction> reactionsToDraw;
    private int maxWidth = 0, maxHeight = 0;
    private int horizontalSpace;
    private int verticalSpace;
    private int currentHeight, currentWidth = 0;
    private Reaction.DisplayMode displayMode;
    private MessageView view;
    private int currentLeft;
    private int currentTop;
    private Reaction pressedReaction;
    private long messageId;
    private long chatId;
    private int lastRowWidth;

    public MessageReactionsContainer(Tdlib tdlib, ReactionsManager reactionsManager, long chatId, long messageId) {
        this.tdlib = tdlib;
        this.messageId = messageId;
        this.chatId = chatId;
        this.reactionsManager = reactionsManager;
        setHorizontalSpace(DEFAULT_H_SPACE);
        setVerticalSpace(DEFAULT_V_SPACE);
    }

    public void setReactions(TdApi.MessageReaction[] reactions, Reaction.DisplayMode displayMode, boolean animated) {
        this.displayMode = displayMode;
        if (reactionsMap == null) {
            reactionsMap = new TreeMap<>();
        }
        if (reactionsToDraw == null) {
            reactionsToDraw = new HashMap<>();
        }

        Set<String> newReactionsSet = new HashSet<>(reactions.length);
        for (TdApi.MessageReaction msgReaction : reactions) {
            if (!reactionsManager.isReactionSupported(msgReaction.reaction)) continue;
            newReactionsSet.add(msgReaction.reaction);
            TGReaction tgReaction;
            Reaction reaction;
            if (reactionsToDraw.containsKey(msgReaction.reaction)) {
                // update
                tgReaction = createTGReaction(msgReaction, false);
                reaction = reactionsToDraw.get(msgReaction.reaction);
                if (reaction != null) {
                    reaction.updateReaction(tgReaction, animated);
                }
            } else {
                // add
                tgReaction = createTGReaction(msgReaction, animated);
                reaction = createReaction(tgReaction);
                if (view != null) reaction.attachToView(view);
                reactionsToDraw.put(msgReaction.reaction, reaction);
            }
            reactionsMap.put(msgReaction.reaction, tgReaction);
        }
        removeOldReactions(reactionsMap, newReactionsSet);
        removeOldReactions(reactionsToDraw, newReactionsSet);
        if (view != null) measure();
    }

    private static void removeOldReactions(Map<String, ?> map, Set<String> reactions) {
        Set<String> keys = new HashSet<>(map.keySet());
        keys.removeAll(reactions);
        for (String toRemove : keys) {
            map.remove(toRemove);
        }
    }

    public void removeReactions() {
        clearReactions();
    }

    public TGReaction[] getReactions() {
        if (reactionsMap == null || reactionsMap.isEmpty()) return null;
        TGReaction[] tgReactions = new TGReaction[reactionsMap.size()];
        int index = 0;
        for (Map.Entry<String, TGReaction> entry : reactionsMap.entrySet()) {
            tgReactions[index] = entry.getValue();
            index++;
        }
        return tgReactions;
    }

    public int getReactionsCount() {
        return reactionsToDraw != null ? reactionsToDraw.size() : 0;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int width) {
        this.maxWidth = width;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public int getCurrentWidth() {
        return currentWidth;
    }

    public int getLastRowWidth() {
        return lastRowWidth;
    }

    public int getHorizontalSpace() {
        return horizontalSpace;
    }

    public void setHorizontalSpace(int horizontalSpaceDp) {
        this.horizontalSpace = Screen.dp(horizontalSpaceDp);
    }

    public int getVerticalSpace() {
        return verticalSpace;
    }

    public void setVerticalSpace(int verticalSpaceDp) {
        this.verticalSpace = Screen.dp(verticalSpaceDp);
    }

    public void attachToView(MessageView view) {
        this.view = view;
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;
        for (Reaction reaction : reactionsToDraw.values()) {
            reaction.attachToView(view);
        }
    }

    public void detachFromView() {
        this.view = null;
        detachReactions();
    }

    public void measure() {
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;
        int currentRowWidth = 0;
        currentHeight = 0;
        currentWidth = 0;
        for (Map.Entry<String, Reaction> entry : reactionsToDraw.entrySet()) {
            Reaction reaction = entry.getValue();
            reaction.measure();
            int rowWidth = reaction.getWidth();
            int rowHeight = reaction.getHeight();
            int newRowWidth = currentRowWidth + rowWidth;
            if (currentHeight == 0) currentHeight = rowHeight;
            if (newRowWidth > maxWidth) {
                currentHeight += rowHeight + getVerticalSpace();
                currentWidth = Math.max(currentRowWidth, currentWidth);
                newRowWidth = rowWidth;
            }
            currentRowWidth = newRowWidth + getHorizontalSpace();
        }
        lastRowWidth = currentRowWidth;
        currentWidth = Math.min(maxWidth, Math.max(currentRowWidth, currentWidth));
    }

    public void draw(Canvas canvas, int left, int top) {
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;
        currentTop = top;
        currentLeft = left;
        int drawX = left;
        int drawY = top;
        int offset = 0;
        for (Map.Entry<String, Reaction> entry : reactionsToDraw.entrySet()) {
            Reaction reaction = entry.getValue();
            if (reaction == null) continue;

            drawX += offset;
            if (drawX + reaction.getWidth() > left + maxWidth) {
                drawY += reaction.getHeight() + getVerticalSpace();
                drawX = left;
            }
            offset = reaction.getWidth() + getHorizontalSpace();
            reaction.draw(canvas, drawX, drawY);
        }
    }

    @Override
    public void performDestroy() {
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;

        for (Map.Entry<String, Reaction> entry : reactionsToDraw.entrySet()) {
            entry.getValue().performDestroy();
        }
        reactionsToDraw.clear();

    }

    public boolean onTouchEvent(View view, MotionEvent motionEvent) {
        if (!shouldHandleTouch(motionEvent.getX(), motionEvent.getY())) return false;

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (String key : reactionsToDraw.keySet()) {
                    Reaction reaction = reactionsToDraw.get(key);
                    if (reaction != null && reaction.handleTouch(motionEvent.getX(), motionEvent.getY())) {
                        pressedReaction = reaction;
                        reaction.performTouchDown();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pressedReaction != null) {
                    pressedReaction.performTouchUp();
                    onReactionClick(pressedReaction.getTGReaction());
                    pressedReaction = null;
                }
                break;
        }
        return shouldHandleTouch(motionEvent.getX(), motionEvent.getY());
    }

    public boolean performLongPress(View view, float x, float y) {
        if (shouldHandleTouch(x, y)) {
            TGReaction longPressed = null;
            for (String key : reactionsToDraw.keySet()) {
                Reaction reaction = reactionsToDraw.get(key);
                if (reaction != null && reaction.performLongPress(x, y)) {
                    longPressed = reactionsMap.get(key);
                }
            }
            if (longPressed != null) {
                return true;
            }
            return false;
        }
        return false;
    }

    private boolean shouldHandleTouch(float x, float y) {
        return displayMode == Reaction.DisplayMode.BIG &&
                x > currentLeft && x < currentLeft + currentWidth
                && y > currentTop && y < currentTop + currentHeight;
    }

    private void onReactionClick(TGReaction reaction) {
        if (reaction != null) {
            reactionsManager.sendMessageReaction(chatId, messageId, reaction.getReactionString());
        }
    }

    private void onReactionLongPress(TGReaction reaction) {
        //todo handle reaction longpress
    }

    private Reaction createReaction(TGReaction tgReaction) {
        Reaction reaction = new Reaction(tgReaction);
        DisplayModeParams params = getDisplayParams();
        if (params != null) {
            reaction.setHeightDp(params.getHeight());
            reaction.setTextSize(params.getTextSize());
            reaction.setDisplayMode(displayMode);
            reaction.setHorizontalPadding(params.getHorizontalPadding());
            reaction.setVerticalPadding(params.getVerticalPadding());
            reaction.setTextPaddingStart(params.getTextPadding());
        }
        reaction.setHasBackground(displayMode != MINI);
        return reaction;
    }

    private TGReaction createTGReaction(TdApi.MessageReaction messageReaction, boolean animated) {
        TGReaction tgReaction = new TGReaction(tdlib, reactionsManager);
        tgReaction.setMessageReaction(messageReaction);
        tgReaction.setState(animated ? TGReaction.State.ACTIVATING : TGReaction.State.IDDLE);
        return tgReaction;
    }


    private DisplayModeParams getDisplayParams() {
        switch (displayMode) {
            case MINI:
                return new DisplayModeParams()
                        .setHeight(16)
                        .setVerticalPadding(2)
                        .setHorizontalPadding(2)
                        .setTextSize(12);
            case BIG:
                return new DisplayModeParams()
                        .setHeight(30)
                        .setVerticalPadding(8)
                        .setHorizontalPadding(8)
                        .setTextPadding(8)
                        .setTextSize(12);
            default:
                return null;
        }
    }

    private void detachReactions() {
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;
        for (Reaction reaction : reactionsToDraw.values()) {
            reaction.detachFromView();
        }
    }

    private void clearReactions() {
        if (reactionsToDraw == null || reactionsToDraw.isEmpty()) return;
        for (Reaction reaction : reactionsToDraw.values()) {
            reaction.detachFromView();
            reaction.performDestroy();
        }
        reactionsToDraw.clear();
    }

    private static class DisplayModeParams {
        private int textSize = 0;
        private int height = 0;
        private int verticalPadding = 0, horizontalPadding = 0, textPadding = 0;

        public int getTextSize() {
            return textSize;
        }

        public DisplayModeParams setTextSize(int textSize) {
            this.textSize = textSize;
            return this;
        }

        public int getHeight() {
            return height;
        }

        public DisplayModeParams setHeight(int height) {
            this.height = height;
            return this;
        }

        public int getVerticalPadding() {
            return verticalPadding;
        }

        public DisplayModeParams setVerticalPadding(int verticalPadding) {
            this.verticalPadding = verticalPadding;
            return this;
        }

        public int getHorizontalPadding() {
            return horizontalPadding;
        }

        public DisplayModeParams setHorizontalPadding(int horizontalPadding) {
            this.horizontalPadding = horizontalPadding;
            return this;
        }

        public DisplayModeParams setTextPadding(int textPadding) {
            this.textPadding = textPadding;
            return this;
        }

        public int getTextPadding() {
            return textPadding;
        }
    }
}
