package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.CheckBoxView;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import androidx.recyclerview.widget.RecyclerView;

public class EditReactionsController extends ReactionListBaseController<TdApi.Chat> implements View.OnClickListener {
  private boolean isChannel;

  public EditReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (TdApi.Chat args) {
    super.setArguments(args);
    isChannel = args.type instanceof TdApi.ChatTypeSupergroup && ((TdApi.ChatTypeSupergroup) args.type).isChannel;
    if (args.availableReactions != null) {
      selectedReactions.addAll(Arrays.asList(args.availableReactions));
    }
    allowPremiumReactionsAnyway = true;
  }

  @Override
  protected void onPopulateTopItems (List<ListItem> outItems) {
    outItems.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_menu_enable));
    outItems.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    outItems.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, isChannel ? R.string.ChannelReactionsExplanation : R.string.ChatReactionsExplanation));
    outItems.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
  }

  @Override
  protected boolean onReactionSelected (String reaction) {
    return true;
  }

  @Override
  protected void onSelectedReactionsChanged () {
    topAdapter.updateValuedSettingByPosition(0);
  }

  @Override
  public int getId () {
    return R.id.controller_chatReactions;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChatReactions);
  }

  @Override
  protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
    if (item.getId() == R.id.btn_menu_enable) {
      if (selectedReactions.isEmpty())
        view.setName(R.string.ReactionsDisabled);
      else
        view.setName(Lang.pluralBold(R.string.xReactionsEnabled, selectedReactions.size()));

      item.setSelected(!selectedReactions.isEmpty());
      CheckBoxView cb = (CheckBoxView) view.getChildAt(0);
      if (selectedReactions.isEmpty()) {
        cb.setChecked(false, isUpdate);
      } else if (selectedReactions.size() == regularReactions.size() + premiumReactions.size()) {
        cb.setChecked(true, isUpdate);
        cb.setIndeterminate(false, isUpdate);
      } else {
        cb.setChecked(true, isUpdate);
        cb.setIndeterminate(true, isUpdate);
      }
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_menu_enable) {
      if (!selectedReactions.isEmpty()) {
        selectedReactions.clear();
      } else {
        selectedReactions.addAll(regularReactions.stream().map(r -> r.reaction).collect(Collectors.toList()));
        selectedReactions.addAll(premiumReactions.stream().map(r -> r.reaction).collect(Collectors.toList()));
      }
      RecyclerView rv = getRecyclerView();
      int lastUpdatedPos = -1;
      for (int i = 0; i < rv.getChildCount(); i++) {
        RecyclerView.ViewHolder holder = rv.getChildViewHolder(rv.getChildAt(i));
        if (holder instanceof ReactionListBaseController.ReactionCellViewHolder) {
          ((ReactionCellViewHolder) holder).updateState(true);
          lastUpdatedPos = Math.max(lastUpdatedPos, holder.getAbsoluteAdapterPosition());
        }
      }
      if (lastUpdatedPos < actualAdapter.getItemCount() - 1) {
        actualAdapter.notifyItemRangeChanged(lastUpdatedPos + 1, actualAdapter.getItemCount() - lastUpdatedPos);
      }
      topAdapter.updateValuedSettingByPosition(0);
    }
  }

  @Override
  public void destroy () {
    tdlib.send(new TdApi.SetChatAvailableReactions(getArguments().id, selectedReactions.toArray(new String[0])), tdlib.okHandler());
    super.destroy();
  }
}
