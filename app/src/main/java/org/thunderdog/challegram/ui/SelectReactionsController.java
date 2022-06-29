package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.SelectableReactionView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoneButton;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.Td;

public class SelectReactionsController extends ViewController<SelectReactionsController.Arguments> {
  private CustomRecyclerView recyclerView;
  private LinearLayoutManager manager;
  private SelectReactionsController.ReactionAdapter adapter;
  private EmojiToneHelper toneHelper;
  private final List<SelectReactionsController.Item> items;
  private DoneButton doneButton;
  private boolean doneVisible = false;

  public SelectReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    items = new ArrayList<>();
  }

  @Override
  public int getId () {
    return R.id.controller_chatReactions;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }
    return false;
  }
  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.requestLayout();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix contentView = new FrameLayoutFix(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    Arguments arguments = getArgumentsStrict();
    String[] reactions = arguments.getAllReactions();
    for (int i = 0; i < reactions.length; i++) {
      String reaction = reactions[i];
      int reactionColorState = EmojiData.instance().getEmojiColorState(reaction);
      items.add(new SelectReactionsController.Item(reaction, reactionColorState, arguments.isReactionSelected(reaction)));
      if (adapter != null) {
        adapter.notifyItemInserted(i);
      }
    }

    manager = new GridLayoutManager(context, 4);
    toneHelper = new EmojiToneHelper(context, null, this);
    adapter = new SelectReactionsController.ReactionAdapter(context, items, this, String -> {
      doneVisible = hasAnyChanges();
      doneButton.setIsVisible(doneVisible, false);
    });

    recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, contentView);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
    recyclerView.setAdapter(adapter);
    contentView.addView(recyclerView);

    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
    params.rightMargin = params.leftMargin = params.bottomMargin = Screen.dp(16f) - padding;
    doneButton = new DoneButton(context);
    doneButton.setId(R.id.btn_done);
    addThemeInvalidateListener(doneButton);
    doneButton.setOnClickListener(v -> {
      if (doneVisible) {
        arguments.saveSelectedChatReaction(items);
        doneVisible = false;
        doneButton.setIsVisible(doneVisible, false);
        navigateBack();
      }
    });
    doneButton.setLayoutParams(params);
    doneButton.setMaximumAlpha(1f);
    doneButton.setIsVisible(false, false);
    contentView.addView(doneButton);

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);
    return wrapper;
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
  }
  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChatReactions);
  }


  private boolean hasAnyChanges () {
    SelectReactionsController.Arguments arguments = getArgumentsStrict();
    for (Item item: items) {
      if (item.selected != arguments.isReactionSelected(item.reaction)) {
        return true;
      }
    }
    return false;
  }

  private static class Item {
    public static final String DEFAULT_CAPTION = "This reaction is not supported";

    public final String reaction;
    public final int reactionColorState;
    public final String caption;

    public boolean selected = false;

    public Item (String reaction, int reactionColorState, boolean selected) {
      this.reaction = reaction;
      this.reactionColorState = reactionColorState;
      this.selected = selected;
      switch (reaction) {
        case "👍": {
          caption = Lang.getString(R.string.ThumbsUp);
          break;
        }
        case "👎": {
          caption = Lang.getString(R.string.ThumbsDown);
          break;
        }
        case "❤": {
          caption = Lang.getString(R.string.RedHeart);
          break;
        }
        case "🎉": {
          caption = Lang.getString(R.string.PartyProper);
          break;
        }
        case "💩": {
          caption = Lang.getString(R.string.PileOfPoo);
          break;
        }
        case "🔥": {
          caption = Lang.getString(R.string.Fire);
          break;
        }
        case "🤮": {
          caption = Lang.getString(R.string.FaceVomiting);
          break;
        }
        case "😂": {
          caption = Lang.getString(R.string.BeamingFace);
          break;
        }
        case "😭": {
          caption = Lang.getString(R.string.AngryFace);
          break;
        }
        case "😱": {
          caption = Lang.getString(R.string.ScreamingFace);
          break;
        }
        case "🤩": {
          caption = Lang.getString(R.string.StarStruck);
          break;
        }
        default: {
          caption = DEFAULT_CAPTION;
          break;
        }
      }
    }
  }

  private static class ItemHolder extends RecyclerView.ViewHolder {
    public TextView captionView;
    public SelectableReactionView reactionView;

    public ItemHolder (View itemView, TextView captionView, SelectableReactionView reactionView) {
      super(itemView);
      this.captionView = captionView;
      this.reactionView = reactionView;
    }
  }

  private static class ReactionAdapter extends RecyclerView.Adapter<SelectReactionsController.ItemHolder> {

    private static final int reactionSize = Screen.dp(60f);
    private static final int reactionPadding = Screen.dp(10f);

    private final Context context;
    private final SelectReactionsController parent;
    private final List<SelectReactionsController.Item> items;
    private final Consumer<String> onReactionClick;

    public ReactionAdapter (Context context, List<SelectReactionsController.Item> items, SelectReactionsController parent, Consumer<String> onReactionClick) {
      this.context = context;
      this.parent = parent;
      this.items = items;
      this.onReactionClick = onReactionClick;
    }
    @NonNull
    @Override
    public SelectReactionsController.ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      LinearLayout wrapper = new LinearLayout(context);
      wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      RippleSupport.setTransparentSelector(wrapper);

      SelectableReactionView reactionView = new SelectableReactionView(context, this.parent.toneHelper);
      reactionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      wrapper.addView(reactionView);

      TextView captionView = new TextView(context);
      captionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      captionView.setId(R.id.text_reactionCaption);
      wrapper.addView(captionView);

      return new SelectReactionsController.ItemHolder(wrapper, captionView, reactionView);
    }

    @Override
    public void onBindViewHolder (@NonNull SelectReactionsController.ItemHolder holder, int position) {
      SelectReactionsController.Item item = items.get(position);
      holder.itemView.setId(R.id.btn_selectReaction);
      holder.itemView.setOnClickListener(view -> {
        item.selected = !item.selected;
        holder.reactionView.setSelected(item.selected);
        onReactionClick.accept(item.reaction);
      });
      holder.captionView.setText(item.caption);
      holder.reactionView.setReaction(item.reaction, item.reactionColorState);
      holder.reactionView.setSelected(item.selected);
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  public static class Arguments {
    private final boolean useDarkMode;
    private String[] selectedReactions;
    private final Consumer<String[]> saveSelectedChatReaction;
    private static final String[] allReactions = new String[] {
        "👍",
        "👎",
        "❤",
        "🎉",
        "💩",
        "🔥",
        "🤮",
        "😂",
        "😭",
        "😱",
        "🤩"
    };

    public Arguments (boolean useDarkMode, String[] selectedReactions, Consumer<String[]> saveSelectedChatReaction) {
      this.useDarkMode = useDarkMode;
      this.selectedReactions = selectedReactions;
      this.saveSelectedChatReaction = saveSelectedChatReaction;
    }

    public boolean useDarkMode () {
      return useDarkMode;
    }

    public String[] getAllReactions () {
      return allReactions;
    }

    public String[] getSelectedReactions () {
      return selectedReactions;
    }

    public boolean isReactionSelected (String reaction) {
      for (int i = 0; i < selectedReactions.length; i++) {
        if (selectedReactions[i].equals(reaction)) {
          return true;
        }
      }
      return false;
    }

    public void saveSelectedChatReaction (List<Item> items) {
      int size = 0;
      for (Item item: items) {
        if (item.selected) {
          size++;
        }
      }
      selectedReactions = new String[size];
      int i = 0;
      for (Item item: items) {
        if (item.selected) {
          selectedReactions[i] = item.reaction;
          i++;
        }
      }
      saveSelectedChatReaction.accept(selectedReactions);
    }
  }
}
