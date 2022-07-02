package org.thunderdog.challegram.component.reactions;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

public class ReactionSettingsController extends RecyclerViewController<ReactionSettingsController.Args> implements View.OnClickListener, ReactionsManager.QuickReactionListener, ReactionsManager.ChatReactionsListener {

    public enum Mode {
        QUICK_REACTION,
        AVAILABLE_REACTIONS
    }

    public static class Args {
        private final Mode mode;
        private final long chatId;

        public Args() {
            this.mode = Mode.QUICK_REACTION;
            this.chatId = -1;
        }

        public Args(long chatId) {
            this.mode = Mode.AVAILABLE_REACTIONS;
            this.chatId = chatId;
        }
    }

    private final ReactionsManager reactionsManager;
    private SettingsAdapter settingsAdapter;

    public ReactionSettingsController(@NonNull Context context, Tdlib tdlib) {
        super(context, tdlib);
        reactionsManager = ReactionsManager.instance(tdlib);
    }

    @Override
    protected int getBackButton() {
        return BackHeaderButton.TYPE_BACK;
    }

    @Override
    public int getId() {
        return R.id.controller_reactions_settings;
    }

    @Override
    protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
        settingsAdapter = new SettingsAdapter(this) {
            @Override
            protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
                super.setValuedSetting(item, view, isUpdate);
                if (item.getId() == R.id.reactions_quick_toggle) {
                    boolean isQuickEnabled = reactionsManager.isQuickReactionsEnabled();
                    if (view.getToggler().isEnabled() != isQuickEnabled) {
                        view.getToggler().setRadioEnabled(isQuickEnabled, isUpdate);
                    }
                }else if (item.getId() == R.id.reactions_chat_checkbox) {
                    int totalCount = reactionsManager.getChatAvailableReactionsCount();
                    boolean isEnabled = totalCount > 0;
                    if (item.isSelected() != isEnabled) {
                        item.setSelected(isEnabled);
                    }
                    if (isEnabled) {
                        view.setName(Lang.plural(R.string.xChatReactionsCheckBoxEnabled, totalCount));
                    } else {
                        view.setName(Lang.getString(R.string.ChatReactionsCheckBoxDisabled));
                    }
                }
            }
        };
        recyclerView.setAdapter(settingsAdapter);
        recyclerView.setItemAnimator(null);
        reactionsManager.addQuickReactionStateListener(this);
        reactionsManager.addChatReactionsListener(this);
        settingsAdapter.setItems(getSettings(), true);
        setHeaderTitle();
    }

    @Override
    public boolean onBackPressed(boolean fromTop) {
        if (getChatId() != -1 && getMode() == Mode.AVAILABLE_REACTIONS) {
            reactionsManager.commitAvailableReactions(getChatId());
        }
        return super.onBackPressed(fromTop);
    }

    @Override
    public void destroy() {
        super.destroy();
        reactionsManager.removeQuickReactionStateListener(this);
        reactionsManager.removeChatReactionsListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.reactions_quick_toggle) {
            reactionsManager.setQuickReactionEnabled(settingsAdapter.toggleView(view));
        } else if (view.getId() == R.id.reactions_chat_checkbox && settingsAdapter.processToggle(view)) {
            SparseIntArray array = settingsAdapter.getCheckIntResults();
            int result = array.get(R.id.reactions_chat_checkbox_enabled);
            reactionsManager.setChatReactionsEnabled(getChatId(), result == R.id.reactions_chat_checkbox);
        }
    }

    @Override
    public void onQuickReactionStateUpdated() {
        settingsAdapter.updateItemById(R.id.reactions_quick_toggle);
    }

    @Override
    public void onChatReactionStateUpdated() {
        settingsAdapter.updateItemById(R.id.reactions_chat_checkbox);
    }

    private void setHeaderTitle() {
        switch (getMode()) {
            case QUICK_REACTION:
                setName(R.string.QuickReaction);
                break;
            case AVAILABLE_REACTIONS:
                setName(R.string.ChatReactions);
                break;
        }
    }

    private Mode getMode() {
        return getArguments() != null ? getArguments().mode : Mode.QUICK_REACTION;
    }

    @Override
    public long getChatId() {
        return getArguments() != null ? getArguments().chatId : -1;
    }

    private ListItem[] getSettings(){
        Mode mode = getMode();
        List<ListItem> items = new ArrayList<>();
        long chatId = getArguments() != null ? getArguments().chatId : -1;

        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));


        switch (mode) {
            case QUICK_REACTION:
                items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.reactions_quick_toggle, 0, R.string.QuickReactionToggle, R.id.reactions_quick_enabled, false));
                items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
                items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.reactions_quick_toggle_description, 0, Lang.getString(R.string.QuickReactionDescription), false));
                break;
            case AVAILABLE_REACTIONS:
                int availableCount = reactionsManager.getChatAvailableReactionsCount();
                items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.reactions_chat_checkbox, 0, Lang.plural(R.string.xChatReactionsCheckBoxEnabled, availableCount), R.id.reactions_chat_checkbox_enabled, availableCount > 0));
                items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
                items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.reactions_chat_checkbox_descriptions, 0, Lang.getString(R.string.ChatReactionsDescription), false));
                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatReactionsHeader));
                break;
            default:
                break;
        }
        ListItem grid = new ListItem(ListItem.TYPE_REACTIONS_GRID);
        grid.setData(new ManageReactionsView.ManageInfo(mode == Mode.QUICK_REACTION ? ManageReactionsView.SELECTION_MODE_SINGLE : ManageReactionsView.SELECTION_MODE_MULTIPLE));
        items.add(grid);
        return items.toArray(new ListItem[]{});
    }
}
