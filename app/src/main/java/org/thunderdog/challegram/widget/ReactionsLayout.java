package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ReactionListController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionsLayout extends FrameLayoutFix {
    private boolean useDarkMode;
    private ViewController<?> parentController;
    private String[] reactions;

    public ReactionsLayout (Context context) {
        super(context);
    }

    public void init (ViewController<?> context, boolean useDarkMode, String[] reactions) {
        this.useDarkMode = useDarkMode;
        this.parentController = context;
        this.reactions = reactions;

        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ReactionListController controller = new ReactionListController(context.context(), context.tdlib());
        controller.setArguments(this);
        addView(controller.get());
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public boolean useDarkMode () {
        return useDarkMode;
    }

    public String[] getReactions () {
        return reactions;
    }

    public EmojiToneHelper.Delegate getToneDelegate () {
        return parentController != null && parentController instanceof EmojiToneHelper.Delegate ? (EmojiToneHelper.Delegate) parentController : null;
    }
}
