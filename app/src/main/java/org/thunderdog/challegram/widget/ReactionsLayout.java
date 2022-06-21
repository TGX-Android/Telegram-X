package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;

public class ReactionsLayout extends HorizontalScrollView {

    private final ArrayList<TdApi.Reaction> mReactions = new ArrayList<>();
    private String[] availableReactions;
    private Tdlib tdlib;
    private LinearLayout mLinearLayout;
    private TGMessage selectedMessage;
    private ReactionCallback reactionCallback = null;

    public ReactionsLayout(Context context, Tdlib tdlib, TGMessage selectedMessage) {
        super(context);
        this.tdlib = tdlib;
        this.selectedMessage = selectedMessage;
        tdlib.getAvailableReactions(selectedMessage.getMessage().chatId, selectedMessage.getMessage().id, arg -> {
            availableReactions = arg.reactions;
            sortReaction(tdlib.getSupportedReactions(), availableReactions);
            initReactionList();
        });
        mLinearLayout = new LinearLayout(context);
        mLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        mLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(54f)));
        Views.setClickable(this);
        setBackgroundColor(Theme.getColor(R.id.theme_color_background));
        addView(mLinearLayout);
        setHorizontalScrollBarEnabled(false);
    }

    public ReactionsLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ReactionsLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ReactionsLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setReactionCallback(ReactionCallback reactionCallback) {
        this.reactionCallback = reactionCallback;
    }

    private void sortReaction(TdApi.Reaction[] reactions, String[] availableReactions) {
        mReactions.clear();
        for (String reaction : availableReactions) {
            for (TdApi.Reaction reactionCurrent : reactions) {
                if (reactionCurrent.isActive && reactionCurrent.reaction.equals(reaction)) {
                    mReactions.add(reactionCurrent);
                    break;
                }
            }
        }
    }

    private void initReactionList() {
        if (mReactions == null || mReactions.isEmpty()) {
            setVisibility(View.GONE);
            return;
        }
        ArrayList<StickerSmallView> stickerSmallViewList = new ArrayList<>();
        for (TdApi.Reaction reaction : mReactions) {
            StickerSmallView stickerSmallView = new StickerSmallView(getContext());
            stickerSmallView.setSticker(new TGStickerObj(tdlib, reaction.activateAnimation, "", reaction.activateAnimation.type));
            stickerSmallView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(54f), Screen.dp(54f)));
            stickerSmallViewList.add(stickerSmallView);
            stickerSmallView.setPreviewEnabled(false);
            stickerSmallView.setIsReaction(true);
            stickerSmallView.setStickerMovementCallback(new StickerSmallView.StickerMovementCallback() {
                @Override
                public boolean onStickerClick(StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
                    TdApi.SetMessageReaction messageReaction = new TdApi.SetMessageReaction();
                    messageReaction.reaction = reaction.reaction;
                    messageReaction.messageId = selectedMessage.getMessage().id;
                    messageReaction.chatId = selectedMessage.getMessage().chatId;
                    messageReaction.isBig = false;
                    tdlib.client().send(messageReaction, object -> {

                    });
                    if (reactionCallback != null) {
                        reactionCallback.onReactionSelected();
                    }
                    return true;
                }

                @Override
                public long getStickerOutputChatId() {
                    return 0;
                }

                @Override
                public void setStickerPressed(StickerSmallView view, TGStickerObj sticker, boolean isPressed) {

                }

                @Override
                public boolean canFindChildViewUnder(StickerSmallView view, int recyclerX, int recyclerY) {
                    return false;
                }

                @Override
                public boolean needsLongDelay(StickerSmallView view) {
                    return false;
                }

                @Override
                public int getStickersListTop() {
                    return 0;
                }

                @Override
                public int getViewportHeight() {
                    return 0;
                }
            });
            mLinearLayout.addView(stickerSmallView);
        }
        Background.instance().post(() -> {
            for (StickerSmallView view : stickerSmallViewList) {
                view.setAnimation(false);
            }
        }, 2500);
    }

    public interface ReactionCallback {
        void onReactionSelected();
    }
}