package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.ReactionListController;

import java.util.function.Consumer;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionsLayout extends FrameLayoutFix {
    private boolean useDarkMode;
    private ViewController<?> parentController;
    private String[] reactions;
    private Consumer<String> onReactionClick;

    public ReactionsLayout (Context context) {
        super(context);
    }

    public void init (ViewController<?> context, boolean useDarkMode, String[] reactions, Consumer<String> onReactionClick) {
        this.useDarkMode = useDarkMode;
        this.parentController = context;
        this.reactions = reactions;
        this.onReactionClick = onReactionClick;

        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ReactionListController controller = new ReactionListController(context.context(), context.tdlib());
        controller.setArguments(this);
        addView(controller.get());
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setOnReactionClick () {

    }

    public boolean useDarkMode () {
        return useDarkMode;
    }

    public String[] getReactions () {
        return reactions;
    }

    public Consumer<String> getOnReactionClick () {
        return onReactionClick;
    }

    public EmojiToneHelper.Delegate getToneDelegate () {
        return parentController != null && parentController instanceof EmojiToneHelper.Delegate ? (EmojiToneHelper.Delegate) parentController : null;
    }
}
