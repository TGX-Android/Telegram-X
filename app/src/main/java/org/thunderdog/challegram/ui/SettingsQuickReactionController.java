package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;

public class SettingsQuickReactionController extends ReactionListBaseController<SettingsQuickReactionController.Args> implements View.OnClickListener {
  private static final int MAX_REACTIONS = 4;

  public SettingsQuickReactionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    selectedReactions.addAll(Settings.instance().getQuickReactions());
    useCounter = true;
    allowPremiumReactionsAnyway = false;
  }

  @Override
  public int getId () {
    return R.id.controller_quickReaction;
  }

  @Override
  protected void onPopulateTopItems (List<ListItem> outItems) {
    outItems.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_menu_enable, 0, R.string.EnableQuickReaction, Settings.instance().areQuickReactionsEnabled()));
    outItems.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    outItems.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuickReactionExplanation));
    outItems.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
  }

  @Override
  protected boolean onReactionSelected (String reaction) {
    if (selectedReactions.size() == MAX_REACTIONS) {
      Toast.makeText(context, Lang.pluralBold(R.string.QuickReactionsLimit, MAX_REACTIONS), Toast.LENGTH_SHORT).show();
      return false;
    }
    return true;
  }

  @Override
  protected void onSelectedReactionsChanged () {
    ListItem sw = topAdapter.findItemById(R.id.btn_menu_enable);
    boolean shouldBeSelected = !selectedReactions.isEmpty();
    if (sw.isSelected() != shouldBeSelected) {
      sw.setSelected(shouldBeSelected);
      topAdapter.updateValuedSettingByPosition(0);
    }
    saveSettings();
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.QuickReaction);
  }

  @Override
  protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
    super.setValuedSetting(item, view, isUpdate);
    if (item.getId() == R.id.btn_menu_enable) {
      view.getToggler().setRadioEnabled(item.isSelected(), isUpdate);
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_menu_enable) {
      ListItem sw = topAdapter.findItemById(R.id.btn_menu_enable);
      sw.setSelected(!sw.isSelected());
      topAdapter.updateValuedSettingByPosition(0);
      if (sw.isSelected() && selectedReactions.isEmpty()) {
        selectedReactions.add(regularReactions.get(0).reaction);
        RecyclerView rv = getRecyclerView();
        for (int i = 0; i < rv.getChildCount(); i++) {
          RecyclerView.ViewHolder holder = rv.getChildViewHolder(rv.getChildAt(i));
          if (holder instanceof ReactionListBaseController.ReactionCellViewHolder) {
            ((ReactionCellViewHolder) holder).updateState(true);
          }
        }
      }
      saveSettings();
    }
  }

  private void saveSettings () {
    ListItem sw = topAdapter.findItemById(R.id.btn_menu_enable);
    Settings.instance().setQuickReactionsEnabled(sw.isSelected());
    Settings.instance().setQuickReactions(selectedReactions);
    getArguments().parentController.updateQuickReactions();
  }

  public static class Args {
    public SettingsThemeController parentController;

    public Args (SettingsThemeController parentController) {
      this.parentController = parentController;
    }
  }
}
