package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.AvailableEmojiView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class QuickReactionController extends ViewController<QuickReactionController.Args> implements View.OnClickListener, TGLegacyManager.EmojiLoadListener {
  public static class Args {
    public String quickReaction;
    public boolean isEnabled;
    public SettingsThemeController parentController;

    public Args (String quickReaction, boolean isEnabled, SettingsThemeController parentController) {
      this.quickReaction = quickReaction;
      this.isEnabled = isEnabled;
      this.parentController = parentController;
    }
  }

  public QuickReactionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_quickReaction;
  }

  private FrameLayoutFix contentView;
  private RecyclerView baseRecyclerView;
  private SettingsAdapter baseAdapter;

  private CustomRecyclerView emojiGridView;
  private EmojiAdapter emojiAdapter;

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (emojiGridView != null)
      emojiGridView.requestLayout();
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    setLockFocusView(contentView, false);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_background, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    baseRecyclerView = (RecyclerView) Views.inflate(context(), R.layout.recycler, contentView);
    baseRecyclerView.setItemAnimator(null);
    baseRecyclerView.setHasFixedSize(true);
    baseRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    baseRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(baseRecyclerView);

    ArrayList<Item> emojis = new ArrayList<>();
    for (TdApi.Reaction emojiCode : tdlib.getReactions()) {
      emojis.add(new Item(emojiCode, emojiCode.reaction.equals(getArgumentsStrict().quickReaction)));
    }
    emojiAdapter = new EmojiAdapter(context, this, emojis);
    baseAdapter = new SettingsAdapter(this) {
      @Override
      protected void initGrid (ListItem item, CustomRecyclerView recyclerView) {
        emojiGridView = recyclerView;
        recyclerView.setAdapter(emojiAdapter);
      }

      @Override
      protected int getSpanCount () {
        return 4;
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.getToggler().setRadioEnabled(getArgumentsStrict().isEnabled, true);
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleQuickReactions, 0, R.string.EnableQuickReaction));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.EnableQuickReactionDescription));
    if (getArgumentsStrict().isEnabled) {
      items.add(new ListItem(ListItem.TYPE_GRID, R.id.grid_active_reactions));
    }

    baseAdapter.setItems(items);
    baseRecyclerView.setAdapter(baseAdapter);

    TGLegacyManager.instance().addEmojiListener(this);

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);
    return wrapper;
  }

  @Override
  public void onEmojiPartLoaded () {
    if (emojiGridView != null) {
      final int childCount = emojiGridView.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = emojiGridView.getChildAt(i);
        if (view != null) {
          view.invalidate();
        }
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
    Views.destroyRecyclerView(emojiGridView);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_toggleQuickReactions: {
        getArgumentsStrict().isEnabled = !getArgumentsStrict().isEnabled;
        if (getArgumentsStrict().isEnabled) {
          baseAdapter.addItem(baseAdapter.getItemCount(), new ListItem(ListItem.TYPE_GRID, R.id.grid_active_reactions));
        } else {
          baseAdapter.removeItemById(R.id.grid_active_reactions);
        }

        baseAdapter.notifyDataSetChanged();
      }
      default: {
        if (v instanceof AvailableEmojiView) {
          AvailableEmojiView emojiView = (AvailableEmojiView) v;
          String reaction = emojiView.getRawEmoji().reaction;
          if (!getArgumentsStrict().quickReaction.equals(reaction)) {
            getArgumentsStrict().quickReaction = reaction;

            for (int i = 0; i < emojiAdapter.items.size(); i++) {
              Item item = emojiAdapter.items.get(i);
              if (item.emoji.reaction.equals(reaction)) {
                emojiAdapter.items.set(i, new Item(item.emoji, true));
                emojiAdapter.notifyItemChanged(i);
              } else if (item.isSelected) {
                emojiAdapter.items.set(i, new Item(item.emoji, false));
                emojiAdapter.notifyItemChanged(i);
              }
            }
          }
        }
      }
    }
  }

  private static class Item {
    public final TdApi.Reaction emoji;
    public final boolean isSelected;

    public Item (TdApi.Reaction emoji, boolean isSelected) {
      this.emoji = emoji;
      this.isSelected = isSelected;
    }
  }

  private static class ItemHolder extends RecyclerView.ViewHolder {
    public ItemHolder (View itemView) {
      super(itemView);
    }
  }

  private class EmojiAdapter extends RecyclerView.Adapter<ItemHolder> {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final ArrayList<Item> items;

    public EmojiAdapter (Context context, View.OnClickListener onClickListener, ArrayList<Item> items) {
      this.context = context;
      this.onClickListener = onClickListener;
      this.items = items;
    }

    @Override
    @NonNull
    public ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      AvailableEmojiView imageView = new AvailableEmojiView(context);
      imageView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      imageView.setOnClickListener(onClickListener);
      Views.setClickable(imageView);
      RippleSupport.setTransparentSelector(imageView);
      return new ItemHolder(imageView);
    }

    @Override
    public void onBindViewHolder (@NonNull ItemHolder holder, int position) {
      Item item = items.get(position);
      holder.itemView.setId(R.id.emoji);
      ((AvailableEmojiView) holder.itemView).setEmoji(item.emoji, item.isSelected);
    }

    @Override
    public int getItemViewType (int position) {
      return 0;
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.QuickReaction);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    Settings setting = Settings.instance();
    setting.setQuickReactionEnabled(getArgumentsStrict().isEnabled);

    for (TdApi.Reaction reaction: tdlib.getReactions()) {
      if (reaction.reaction.equals(getArgumentsStrict().quickReaction)) {
        setting.setQuickReaction(reaction);
        break;
      }
    }

    if (getThemeController() != null) {
      getThemeController().updateQuickEmoji(getArgumentsStrict().quickReaction, getArgumentsStrict().isEnabled);
    }

    return false;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  protected final SettingsThemeController getThemeController () {
    return getArguments() != null ? getArguments().parentController : null;
  }
}
