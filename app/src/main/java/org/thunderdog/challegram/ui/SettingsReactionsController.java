package org.thunderdog.challegram.ui;

import static org.thunderdog.challegram.ui.SettingsReactionsController.Arguments;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.reaction.EffectAnimationItemDecoration;
import org.thunderdog.challegram.component.reaction.SelectableReactionView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsReactionsController extends RecyclerViewController<Arguments> implements View.OnClickListener {

  private static final int MODE_QUICK_REACTION = 1;
  private static final int MODE_CHAT_REACTIONS = 2;

  private static final int SPAN_COUNT = 4;

  public static class Arguments {
    public final long chatId;

    public Arguments (long chatId) {
      this.chatId = chatId;
    }
  }

  private SettingsAdapter adapter;
  private EffectAnimationItemDecoration effectAnimationItemDecoration;

  private int mode;

  public SettingsReactionsController (@NonNull Context context, @NonNull Tdlib tdlib) {
    super(context, tdlib);
  }

  private final Set<String> chatAvailableReactions = new HashSet<>();

  private int reactionStart, reactionCount;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    long chatId = getChatId();
    mode = chatId != 0L ? MODE_CHAT_REACTIONS : MODE_QUICK_REACTION;

    if (mode == MODE_CHAT_REACTIONS) {
      String[] availableReactions = tdlib().chatAvailableReactions(chatId);
      if (availableReactions != null) {
        Collections.addAll(chatAvailableReactions, availableReactions);
      }
    }

    adapter = new SettingsAdapter(this, this, this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        SelectableReactionView itemView = new SelectableReactionView(context, tdlib, SettingsReactionsController.this);
        itemView.setOnClickListener(SettingsReactionsController.this);
        itemView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(128f)));
        return new SettingHolder(itemView);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        SelectableReactionView itemView = (SelectableReactionView) holder.itemView;
        TdApi.Reaction reaction = (TdApi.Reaction) item.getData();
        itemView.setClickable(reaction != null);
        itemView.setReaction(reaction);
        boolean isChecked;
        if (reaction != null) {
          isChecked = isChecked(reaction.reaction);
        } else {
          isChecked = false;
        }
        itemView.setChecked(isChecked, false);
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_quickReaction) {
          String quickReaction = tdlib().settings().quickReaction();
          boolean isEnabled = !quickReaction.equals(TdlibSettingsManager.QUICK_REACTION_DISABLED);
          view.getToggler().setRadioEnabled(isEnabled, isUpdate);
        } else if (item.getId() == R.id.btn_chatReactions) {
          int enabledReactionCount = chatAvailableReactions.size();
          boolean isChecked = enabledReactionCount > 0;
          boolean isIntermediate = enabledReactionCount < reactionCount;
          CheckBoxView checkBox = view.findCheckBox();
          if (isChecked) {
            view.setName(Lang.pluralBold(R.string.xReactionsEnabled, enabledReactionCount));
          } else {
            view.setName(R.string.ReactionsDisabled);
          }
          item.setSelected(isChecked);
          checkBox.setIntermediate(isIntermediate, isUpdate);
          checkBox.setChecked(isChecked, isUpdate);
        }
      }
    };
    GridLayoutManager gridLayoutManager = new GridLayoutManager(context, SPAN_COUNT);
    gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int itemViewType = adapter.getItemViewType(position);
        if (itemViewType <= ListItem.TYPE_CUSTOM) {
          return 1;
        }
        return SPAN_COUNT;
      }
    });
    effectAnimationItemDecoration = new EffectAnimationItemDecoration();
    recyclerView.addItemDecoration(effectAnimationItemDecoration);

    recyclerView.setLayoutManager(gridLayoutManager);
    recyclerView.setAdapter(adapter);

    List<ListItem> items = new ArrayList<>();
    if (mode == MODE_CHAT_REACTIONS) {
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_chatReactions, 0, R.string.ReactionsDisabled, false));
    } else {
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_quickReaction, 0, R.string.EnableQuickReaction));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    CharSequence info;
    if (mode == MODE_CHAT_REACTIONS) {
      info = tdlib.isChannelFast(chatId) ? Lang.getString(R.string.ChannelReactionsInfo) : Lang.getString(R.string.GroupReactionsInfo);
    } else {
      info = Lang.getString(R.string.QuickReactionInfo);
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, info, false));

    TdApi.Reaction[] reactions = tdlib().getSupportedReactions();
    if (reactions != null && reactions.length > 0) {
      ListItem header;
      if (mode == MODE_CHAT_REACTIONS) {
        header = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AvailableReactions);
        items.add(header);
      } else {
        header = null;
      }
      ListItem shadowTop = new ListItem(ListItem.TYPE_SHADOW_TOP);
      items.add(shadowTop);
      reactionStart = items.size();
      reactionCount = 0;
      for (int i = 0; i < reactions.length; i++) {
        TdApi.Reaction reaction = reactions[i];
        if (!reaction.isActive) {
          continue;
        }
        ListItem item = new ListItem(ListItem.TYPE_CUSTOM - i);
        item.setData(reaction);
        item.setStringKey(reaction.reaction);
        items.add(item);
        reactionCount++;
      }

      if (reactionCount > 0) {
        int count = SPAN_COUNT - (reactionCount % SPAN_COUNT);
        for (int i = 0; i < count; i++) {
          items.add(new ListItem(ListItem.TYPE_CUSTOM - reactionCount));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      } else {
        items.remove(shadowTop);
        if (header != null) {
          items.remove(header);
        }
      }
    }
    adapter.setItems(items, false);
  }

  @Override
  public void destroy () {
    super.destroy();
    if (effectAnimationItemDecoration != null) {
      effectAnimationItemDecoration.performDestroy();
    }
  }

  @Override
  public int getId () {
    if (mode == MODE_CHAT_REACTIONS) {
      return R.id.controller_chatReactions;
    } else {
      return R.id.controller_quickReaction;
    }
  }

  @Override
  public CharSequence getName () {
    if (mode == MODE_CHAT_REACTIONS) {
      return Lang.getString(R.string.Reactions);
    } else {
      return Lang.getString(R.string.QuickReaction);
    }
  }

  @Override
  public void onClick (@NonNull View view) {
    final ListItem item = (ListItem) view.getTag();
    final int itemId = item.getId();
    if (itemId == R.id.btn_quickReaction) {
      UI.hapticVibrate(view, false);
      boolean isChecked = adapter.toggleView(view);
      if (isChecked) {
        tdlib().settings().setQuickReaction(TdlibSettingsManager.QUICK_REACTION_DEFAULT);
      } else {
        tdlib().settings().setQuickReaction(TdlibSettingsManager.QUICK_REACTION_DISABLED);
      }
      updateCheckedState();
    } else if (itemId == R.id.btn_chatReactions) {
      UI.hapticVibrate(view, false);
      chatAvailableReactions.clear();
      if (adapter.toggleView(view)) {
        TdApi.Reaction[] supportedReactions = tdlib().getSupportedReactions();
        if (supportedReactions != null) {
          for (TdApi.Reaction supportedReaction : supportedReactions) {
            if (supportedReaction.isActive) {
              chatAvailableReactions.add(supportedReaction.reaction);
            }
          }
        }
      }
      adapter.updateValuedSettingById(R.id.btn_chatReactions);
      updateCheckedState();
    } else if (view instanceof SelectableReactionView) {
      TdApi.Reaction reaction = (TdApi.Reaction) item.getData();
      SelectableReactionView itemView = (SelectableReactionView) view;
      if (reaction != null) {
        UI.hapticVibrate(view, false);
        boolean isChecked = itemView.toggle();
        if (isChecked) {
          itemView.playActivateAnimation();
          if (effectAnimationItemDecoration != null) {
            effectAnimationItemDecoration.prepareAnimation(getRecyclerView(), tdlib(), reaction);
          }
        }
        onCheckedChanged(reaction, isChecked);
      }
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (mode == MODE_CHAT_REACTIONS) {
      String[] availableReactions = chatAvailableReactions.toArray(new String[0]);
      tdlib().send(new TdApi.SetChatAvailableReactions(getChatId(), availableReactions), tdlib().okHandler());
    }
    return super.onBackPressed(fromTop);
  }

  private void onCheckedChanged (@NonNull TdApi.Reaction reaction, boolean isChecked) {
    if (mode == MODE_QUICK_REACTION) {
      if (isChecked) {
        tdlib().settings().setQuickReaction(reaction.reaction);
      } else {
        tdlib().settings().setQuickReaction(TdlibSettingsManager.QUICK_REACTION_DISABLED);
      }
      adapter.updateValuedSettingById(R.id.btn_quickReaction);
      updateCheckedState();
    } else {
      if (isChecked) {
        chatAvailableReactions.add(reaction.reaction);
      } else {
        chatAvailableReactions.remove(reaction.reaction);
      }
      adapter.updateValuedSettingById(R.id.btn_chatReactions);
    }
  }

  private void updateCheckedState () {
    if (reactionCount == 0) {
      return;
    }
    RecyclerView.LayoutManager layoutManager = getRecyclerView().getLayoutManager();
    if (layoutManager == null) {
      adapter.notifyItemRangeChanged(reactionStart, reactionCount);
      return;
    }
    for (int i = 0; i < reactionCount; i++) {
      int position = reactionStart + i;
      View view = layoutManager.findViewByPosition(position);
      if (view instanceof SelectableReactionView) {
        SelectableReactionView itemView = (SelectableReactionView) view;
        TdApi.Reaction reaction = itemView.getReaction();
        if (reaction != null) {
          itemView.setChecked(isChecked(reaction.reaction), true);
          continue;
        }
      }
      adapter.notifyItemChanged(position);
    }
  }

  private boolean isChecked (@NonNull String reaction) {
    if (mode == MODE_QUICK_REACTION) {
      String quickReaction = tdlib().settings().quickReaction();
      return quickReaction.equals(reaction);
    } else {
      return chatAvailableReactions.contains(reaction);
    }
  }

  @Override
  public long getChatId () {
    Arguments arguments = getArguments();
    return arguments != null ? arguments.chatId : 0L;
  }
}
