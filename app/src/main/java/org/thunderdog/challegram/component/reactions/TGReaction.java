package org.thunderdog.challegram.component.reactions;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Objects;

import androidx.annotation.Nullable;

public final class TGReaction {
    private final Tdlib tdlib;
    private final ReactionsManager reactionsManager;
    private TdApi.Reaction reaction;
    private TdApi.MessageReaction messageReactionInfo;
    private TGStickerObj staticIcon;
    private TGStickerObj appearAnimation;
    private TGStickerObj selectAnimation;
    private TGStickerObj activateAnimation;
    private TGStickerObj effectAnimation;
    @Nullable private TGStickerObj aroundAnimation;
    @Nullable private TGStickerObj centerAnimation;
    private State state = State.IDDLE;

    public TGReaction(Tdlib tdlib, ReactionsManager reactionsManager) {
        this.tdlib = tdlib;
        this.reactionsManager = reactionsManager;
    }

    public Tdlib getTdlib() {
        return tdlib;
    }

    public void setMessageReaction(TdApi.MessageReaction messageReaction) {
        this.messageReactionInfo = messageReaction;
        this.reaction = reactionsManager.getReaction(messageReaction.reaction);
    }

    public TdApi.MessageReaction getMessageReactionInfo() {
        return messageReactionInfo;
    }

    public TdApi.MessageSender[] getReactionSenders() {
        return messageReactionInfo.recentSenderIds;
    }

    public void setReaction(TdApi.Reaction reaction) {
        this.reaction = reaction;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isChosen() {
        return this.messageReactionInfo != null && this.messageReactionInfo.isChosen;
    }

    public int getTotalCount() {
        return this.messageReactionInfo != null ? this.messageReactionInfo.totalCount : 0;
    }

    public String getReactionTitle() {
        return this.reaction != null ? this.reaction.title : null;
    }

    @Nullable
    public String getReactionString() {
        if (this.messageReactionInfo != null) {
            return this.messageReactionInfo.reaction;
        } else if (this.reaction != null) {
            return this.reaction.reaction;
        }
        return null;
    }

    @Nullable
    public TGStickerObj getStateSticker() {
        TGStickerObj stickerObj = null;
        switch (state) {
            case IDDLE:
                stickerObj = getStaticIcon();
                break;
            case APPEARING:
                stickerObj = getAppearAnimation();
                break;
            case ACTIVATING:
                stickerObj = getActivateAnimation();
                break;
        }
        return stickerObj;
    }

    public State getState() {
        return state;
    }

    @Nullable
    public TGStickerObj getStaticIcon() {
        if (staticIcon == null && reaction != null) {
            TdApi.Sticker sticker = reaction.staticIcon;
            staticIcon = createStickerObj(sticker);
        }
        return staticIcon;
    }

    @Nullable
    public TGStickerObj getAppearAnimation() {
        if (appearAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.appearAnimation;
            appearAnimation = createStickerObj(sticker);
        }
        return appearAnimation;
    }

    @Nullable
    public TGStickerObj getSelectAnimation() {
        if (selectAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.selectAnimation;
            selectAnimation = createStickerObj(sticker);
        }
        return selectAnimation;
    }

    @Nullable
    public TGStickerObj getActivateAnimation() {
        if (activateAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.activateAnimation;
            activateAnimation = createStickerObj(sticker);
        }
        return activateAnimation;
    }

    @Nullable
    public TGStickerObj getEffectAnimation() {
        if (effectAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.effectAnimation;
            effectAnimation = createStickerObj(sticker);
        }
        return effectAnimation;
    }

    @Nullable
    public TGStickerObj getAroundAnimation() {
        if (aroundAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.aroundAnimation;
            if (sticker == null) return null;
            aroundAnimation = createStickerObj(sticker);
        }
        return aroundAnimation;
    }

    @Nullable
    public TGStickerObj getCenterAnimation() {
        if (centerAnimation == null && reaction != null) {
            TdApi.Sticker sticker = reaction.centerAnimation;
            if (sticker == null) return null;
            centerAnimation = createStickerObj(sticker);
        }
        return centerAnimation;
    }

    private TGStickerObj createStickerObj(TdApi.Sticker sticker) {
        return new TGStickerObj(tdlib, sticker, sticker.type, new String[] { reaction.reaction });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TGReaction that = (TGReaction) o;
        return Objects.equals(reaction, that.reaction) && Objects.equals(messageReactionInfo, that.messageReactionInfo) && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reaction, messageReactionInfo, state);
    }

    public enum State {
        IDDLE,
        APPEARING,
        ACTIVATING
    }
}
