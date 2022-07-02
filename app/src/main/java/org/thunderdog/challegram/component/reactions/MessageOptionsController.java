package org.thunderdog.challegram.component.reactions;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.OptionDelegate;

import androidx.annotation.NonNull;
import me.vkryl.android.animator.FactorAnimator;

class MessageOptionsController extends ViewController<MessageOptionsController.Args> implements FactorAnimator.Target {
    public static final int REVEAL_ANIMATOR = 1;
    public static final int HIDE_ANIMATOR = 2;

    public static class Args {
        private final Options options;
        private final long messageId;
        OptionDelegate optionsDelegate;

        public Args(Options options, long messageId) {
            this.options = options;
            this.messageId = messageId;
        }

        public void setOptionsDelegate(OptionDelegate optionsDelegate) {
            this.optionsDelegate = optionsDelegate;
        }
    }

    private final int optionsItemHeight;
    private OptionDelegate optionDelegate;

    public MessageOptionsController(@NonNull Context context, Tdlib tdlib, ReactionsManager reactionsManager) {
        super(context, tdlib);
        optionsItemHeight = Screen.dp(54);
    }

    public int getOptionsItemHeight() {
        return optionsItemHeight;
    }

    @Override
    protected View onCreateView(Context context) {
        optionDelegate = getArguments() != null ? getArguments().optionsDelegate : null;
        LinearLayout container = new LinearLayout(context);
        container.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(R.id.theme_color_filling));
        OptionsLayout optionsLayout = createOptionsLayout(context);
        container.addView(optionsLayout, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return container;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {

    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        FactorAnimator.Target.super.onFactorChangeFinished(id, finalFactor, callee);
    }

    private OptionsLayout createOptionsLayout(Context context) {
        if (getArguments() == null) return null;
        Options options = getArguments().options;
        OptionsLayout optionsWrap = new OptionsLayout(context, this, null);
        optionsWrap.setInfo(this, this.tdlib(), options.info, false);
        optionsWrap.setLayoutParams(new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        optionsWrap.setBackgroundColor(Theme.getColor(R.id.theme_color_filling));

        int index = 0;
        for (ViewController.OptionItem item : options.items) {
            TextView text = OptionsLayout.genOptionView(context, item.id, item.name, item.color, item.icon, this::handleOptionsClicked, getThemeListeners(), null);
            RippleSupport.setTransparentSelector(text);
            text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, optionsItemHeight));
            if (optionDelegate != null) text.setTag(optionDelegate.getTagForItem(index));
            optionsWrap.addView(text);
            index++;
        }
        return optionsWrap;
    }

    private void handleOptionsClicked(View view) {
        if (optionDelegate != null) {
            optionDelegate.onOptionItemPressed(view, view.getId());
        }
    }

    @Override
    public int getId() {
        return R.id.controller_message_options;
    }
}
