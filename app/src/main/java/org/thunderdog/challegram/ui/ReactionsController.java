package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.OnReactionSelected;
import org.thunderdog.challegram.widget.ReactionStickerGridView;

import java.util.ArrayList;
import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;

public class ReactionsController extends RecyclerViewController<ReactionsController.Args> implements View.OnClickListener {
    public static final int MODE_QUICK_REACTION = 0;
    public static final int MODE_ALLOWED_REACTIONS = 1;

    public static class Args {
        public final int mode;
        public final String[] selectedReactions;
        public OnReactionSelected reactionSelected;

        public Args(int mode, String[] selectedReactions, OnReactionSelected reactionSelected) {
            this.mode = mode;
            this.selectedReactions = selectedReactions;
            this.reactionSelected = reactionSelected;
        }
    }

    public ReactionsController(Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public int getId() {
        switch (mode) {
            case MODE_QUICK_REACTION:
                return R.id.controller_quickReactions;
            case MODE_ALLOWED_REACTIONS:
                return R.id.controller_allowedReactions;
        }
        return R.id.controller_allowedReactions;
    }

    @Override
    protected int getBackButton() {
        return BackHeaderButton.TYPE_BACK;
    }

    @Override
    public CharSequence getName() {
        return Lang.getString(mode == MODE_QUICK_REACTION ? R.string.QuickReaction : R.string.Reactions);
    }

    private int mode;
    private TdApi.Reaction[] availableReactions;
    private ArrayList<String> selectedReactions;
    public OnReactionSelected reactionSelected;

    @Override
    public void setArguments(ReactionsController.Args args) {
        super.setArguments(args);
        this.mode = args.mode;
        this.selectedReactions = new ArrayList<>();
        String[] reactions = args.selectedReactions;
        if (reactions != null) {
            selectedReactions.addAll(Arrays.asList(reactions));
        }
        this.reactionSelected = args.reactionSelected;
    }

    private SettingsAdapter adapter;

    @Override
    protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
        availableReactions = tdlib().getSupportedReactions();
        adapter = new SettingsAdapter(this) {
            @Override
            protected void setSelectedReaction(ListItem item, int position, ReactionStickerGridView view, boolean isUpdate) {
                view.setSelectedReaction(selectedReactions.toArray(new String[]{}));
            }

            @Override
            protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
                view.setDrawModifier(item.getDrawModifier());
                switch (item.getId()) {
                    case R.id.btn_enableQuickReaction: {
                        view.getToggler().setRadioEnabled(Settings.instance().isHasQuickReaction(), isUpdate);
                        break;
                    }
                }
            }
        };
        setDisableSettling(true);
        buildCells();
        recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 150l));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        if (v instanceof EmbeddableStickerView) {
            EmbeddableStickerView view = ((EmbeddableStickerView) v);
            String reactionTitle = view.getCaptionText().toString();
            if (view.isChecked()) {
                selectedReactions.add(reactionTitle);
                Settings.instance().setQuickReaction(reactionTitle);
            } else {
                Settings.instance().setQuickReaction("");
                selectedReactions.remove(reactionTitle);
            }
            reactionSelected.onReactionSelected(reactionTitle, availableReactions[0].reaction, selectedReactions.toArray(new String[]{}));
            adapter.updateValuedSettingById(R.id.btn_enableQuickReaction);
            if (mode == 1) {
                tdlib.updateAvailableReactionsForChat(getChatId(), selectedReactions.toArray(new String[]{}));
            }
        } else {
            switch (v.getId()) {
                case R.id.btn_enableQuickReaction: {
                    if (Settings.instance().isHasQuickReaction()) {
                        Settings.instance().setQuickReaction("");
                    } else {
                        String reactionTitle = availableReactions[0].title;
                        Settings.instance().setQuickReaction(reactionTitle);
                    }
                    reactionSelected.onReactionSelected(null, null, null);
                    adapter.updateValuedSettingById(R.id.btn_enableQuickReaction);
                    // TODO tim do this better with listeners or invalidate single position in rv
                    adapter.notifyDataSetChanged();
                    break;
                }
                case R.id.btn_allowedReactions: {
                    if (((SettingView) v).findCheckBox().isChecked()) {
                        selectedReactions.clear();
                    } else {
                        selectedReactions.clear();
                        for (TdApi.Reaction reaction: availableReactions) {
                            selectedReactions.add(reaction.reaction);
                        }
                    }
                    if (selectedReactions.size() > 0) {
                        ((SettingView) v).findCheckBox().setChecked(true, true);
                        ((SettingView) v).setName(Lang.getString(R.string.ReactionsEnabled, selectedReactions.size()));
                    } else {
                        ((SettingView) v).findCheckBox().setChecked(false, true);
                        ((SettingView) v).setName(Lang.getString(R.string.ReactionsDisabled));
                    }
                    adapter.updateValuedSettingById(R.id.btn_allowedReactions);
                    adapter.updateItemById(R.id.reactionGrid);
                }
            }
        }
    }

    private void buildCells() {
        ArrayList<ListItem> items = new ArrayList<>();
        if (mode == MODE_QUICK_REACTION) {
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_enableQuickReaction, 0, R.string.EnableQuickReaction));
            items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
            items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuickReactionSetupDescription));
        } else {
            if (selectedReactions.size() > 0) {
                items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_allowedReactions, 0, Lang.getString(R.string.ReactionsEnabled, selectedReactions.size()), selectedReactions.size() > 0));
            } else {
                items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_allowedReactions, 0, R.string.ReactionsDisabled));
            }
            items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
            items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ReactionsSetupDescription));
            items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AvailableReactions));
        }
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        if (mode == 0) {
            items.add(new ListItem(ListItem.TYPE_QUICK_REACTION_SELECT, R.id.reactionGrid));
        } else {
            items.add(new ListItem(ListItem.TYPE_MULTIPLE_REACTION_SELECT, R.id.reactionGrid));
        }
        adapter.setItems(items, true);
    }
}
