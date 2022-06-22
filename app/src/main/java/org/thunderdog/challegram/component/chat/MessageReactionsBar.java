package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

@SuppressLint("ViewConstructor")
public class MessageReactionsBar extends FrameLayoutFix {

    public MessageReactionsBar(@NonNull Context context, ViewController<?> parent, @Nullable ThemeDelegate forcedTheme, String[] reactionIds) {
        super(context);

        List<TdApi.Reaction> availableReactions = mapReactions(parent.tdlib(), reactionIds);

        HorizontalScrollView horizontalScrollView = initScrollView(context);

        initReactionList(parent.tdlib(), horizontalScrollView, availableReactions);

        addView(horizontalScrollView);

        initBackground(forcedTheme);
    }

    @NonNull
    private List<TdApi.Reaction> mapReactions(Tdlib tdlib, String[] reactionIds) {
        List<TdApi.Reaction> availableReactions = new ArrayList<>();
        TdApi.Reaction[] supportReactions = tdlib.getSupportedReactions();
        for (String reaction : reactionIds) {
            for (TdApi.Reaction supportReaction : supportReactions) {
                if (supportReaction.reaction.equals(reaction)) {
                    availableReactions.add(supportReaction);
                    break;
                }
            }
        }
        return availableReactions;
    }

    @NonNull
    private HorizontalScrollView initScrollView(@NonNull Context context) {
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
        horizontalScrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        return horizontalScrollView;
    }


    private void initReactionList(Tdlib tdlib, HorizontalScrollView horizontalScrollView, List<TdApi.Reaction> availableReactions) {
        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        for (TdApi.Reaction reaction : availableReactions) {
            StickerSmallView stickerSmallView = new StickerSmallView(getContext()){
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    return false;
                }
            };
            stickerSmallView.setSticker(new TGStickerObj(tdlib, reaction.activateAnimation, "", reaction.activateAnimation.type));
            stickerSmallView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(48), Screen.dp(48)));
            linearLayout.addView(stickerSmallView);
        }
        horizontalScrollView.addView(linearLayout);
    }

    private void initBackground(@Nullable ThemeDelegate forcedTheme) {
        ViewUtils.setBackground(this, new Drawable() {
            @Override
            public void draw (@NonNull Canvas c) {
                View view = getChildAt(0);
                int height = view != null ? view.getMeasuredHeight() : 0;
                if (height > 0)
                    c.drawRect(0, height, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(forcedTheme != null ? forcedTheme.getColor(R.id.theme_color_filling) : Theme.getColor(R.id.theme_color_filling)));
            }

            @Override
            public void setAlpha (int alpha) { }

            @Override
            public void setColorFilter (@Nullable ColorFilter colorFilter) { }

            @Override
            public int getOpacity () {
                return PixelFormat.UNKNOWN;
            }
        });
    }
}
