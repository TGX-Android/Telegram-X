package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.AvailableEmojiView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditReactionsController extends ViewController<EditReactionsController.Args> implements View.OnClickListener, TGLegacyManager.EmojiLoadListener {
  public static class Args {
    public long chatId;
    public List<String> enabledReactions;

    public Args (long chatId, String[] enabledReactions) {
      this.chatId = chatId;
      this.enabledReactions = new ArrayList<>(Arrays.asList(enabledReactions));
    }
  }

  public EditReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_enabledReactions;
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
      emojis.add(new Item(emojiCode, getArgumentsStrict().enabledReactions.contains(emojiCode.reaction)));
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
    };

    ArrayList<ListItem> items = new ArrayList<>();
    ListItem checkboxItem;
    if (getArgumentsStrict().enabledReactions.isEmpty()) {
      checkboxItem = new ListItem(ListItem.TYPE_CHECKBOX_OPTION_INDETERMINATE, R.id.btn_toggleAllReactions, 0, R.string.ReactionsDisabled, R.id.theme_chat, false);
      items.add(checkboxItem);
    } else {
      checkboxItem = new ListItem(ListItem.TYPE_CHECKBOX_OPTION_INDETERMINATE, R.id.btn_toggleAllReactions, 0, Lang.plural(R.string.ReactionsEnabled, getArgumentsStrict().enabledReactions.size()), R.id.theme_chat, true);
      checkboxItem.setBoolValue(getArgumentsStrict().enabledReactions.size() != tdlib.getReactions().length);
      items.add(checkboxItem);
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AllowToReact));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AvailableReactions));
    ListItem emojiGridItem = new ListItem(ListItem.TYPE_GRID, R.id.grid_active_reactions);
    items.add(emojiGridItem);

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
    ListItem checkboxItem = baseAdapter.findItemById(R.id.btn_toggleAllReactions);
    switch (v.getId()) {
      case R.id.btn_toggleAllReactions: {
        if (getArgumentsStrict().enabledReactions.size() == tdlib.getReactions().length) {
          getArgumentsStrict().enabledReactions.clear();
        } else {
          getArgumentsStrict().enabledReactions.clear();
          for (TdApi.Reaction reaction: tdlib.getReactions()) {
            getArgumentsStrict().enabledReactions.add(reaction.reaction);
          }
        }

        if (checkboxItem != null) {
          if (getArgumentsStrict().enabledReactions.isEmpty()) {
            checkboxItem.setSelected(false);
            checkboxItem.setBoolValue(false);
            checkboxItem.setString(R.string.ReactionsDisabled);
          } else {
            checkboxItem.setSelected(true);
            checkboxItem.setBoolValue(getArgumentsStrict().enabledReactions.size() != tdlib.getReactions().length);
            checkboxItem.setString(Lang.plural(R.string.ReactionsEnabled, getArgumentsStrict().enabledReactions.size()));
          }
          baseAdapter.notifyItemChanged(0);
        }

        for (int i = 0; i < emojiAdapter.items.size(); i++) {
          Item item = emojiAdapter.items.get(i);
          emojiAdapter.items.set(i, new Item(item.emoji, getArgumentsStrict().enabledReactions.size() == tdlib.getReactions().length));
        }
        emojiAdapter.notifyDataSetChanged();
      }
      default: {
        if (v instanceof AvailableEmojiView) {
          AvailableEmojiView emojiView = (AvailableEmojiView) v;
          String reaction = emojiView.getRawEmoji().reaction;
          if (emojiView.isSelected()) {
            getArgumentsStrict().enabledReactions.remove(reaction);
          } else {
            getArgumentsStrict().enabledReactions.add(reaction);
          }

          if (checkboxItem != null) {
            if (getArgumentsStrict().enabledReactions.isEmpty()) {
              checkboxItem.setSelected(false);
              checkboxItem.setBoolValue(false);
              checkboxItem.setString(R.string.ReactionsDisabled);
            } else {
              checkboxItem.setSelected(true);
              checkboxItem.setBoolValue(getArgumentsStrict().enabledReactions.size() != tdlib.getReactions().length);
              checkboxItem.setString(Lang.plural(R.string.ReactionsEnabled, getArgumentsStrict().enabledReactions.size()));
            }
            baseAdapter.notifyItemChanged(0);
          }

          for (int i = 0; i < emojiAdapter.items.size(); i++) {
            Item item = emojiAdapter.items.get(i);
            if (item.emoji.reaction.equals(reaction)) {
              emojiAdapter.items.set(i, new Item(item.emoji, !item.isSelected));
              emojiAdapter.notifyItemChanged(i);
              break;
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
    return Lang.getString(R.string.Reactions);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    tdlib.setChatAvailableReactions(getArgumentsStrict().chatId, getArgumentsStrict().enabledReactions.toArray(new String[0]));
    return false;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }
}
