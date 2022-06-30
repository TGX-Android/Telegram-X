package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.reaction.ReactionView;
import org.thunderdog.challegram.component.reaction.SelectableReactionView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.DoneButton;
import org.thunderdog.challegram.widget.ThreeStateCheckBoxView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class SelectReactionsController extends ViewController<SelectReactionsController.Arguments> {
  private CustomRecyclerView recyclerView;
  private LinearLayoutManager manager;
  private SelectReactionsController.ReactionAdapter adapter;
  private final List<SelectReactionsController.Item> items;
  private DoneButton doneButton;
  private boolean doneVisible = false;
  private CustomTextView checkBoxText;
  private ThreeStateCheckBoxView checkBoxView;

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
    Arguments arguments = getArgumentsStrict();

    String[] reactions = arguments.getAllReactions();
    for (int i = 0; i < reactions.length; i++) {
      String reaction = reactions[i];
      int reactionColorState = EmojiData.instance().getEmojiColorState(reaction);
      items.add(new SelectReactionsController.Item(reaction, reactionColorState, arguments.isReactionSelected(reaction)));
    }

    LinearLayout contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    contentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout checkBoxLayout = new LinearLayout(context);
    checkBoxLayout.setOrientation(LinearLayout.HORIZONTAL);
    checkBoxLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Screen.dp(60f)));
    checkBoxLayout.setGravity(Gravity.CENTER_VERTICAL);
    checkBoxText = new CustomTextView(context, tdlib);
    checkBoxText.setLayoutParams(new LinearLayout.LayoutParams(Screen.currentWidth() - Screen.dp(40f), LinearLayout.LayoutParams.WRAP_CONTENT));
    checkBoxText.setPadding(Screen.dp(10f), 0, 0, 0);
    setTextForCheckBox(arguments.selectedReactions.length);
    checkBoxText.setTextSize(18f);
    checkBoxLayout.addView(checkBoxText);
    checkBoxView = new ThreeStateCheckBoxView(context);
    checkBoxView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(20f), Screen.dp(20f)));
    Views.setClickable(checkBoxView);
    checkBoxView.setOnClickListener(view -> {
      checkBoxView.toggle();
      boolean reactionsState = false;
      if (checkBoxView.getState() == ThreeStateCheckBoxView.State.TRUE) {
        reactionsState = true;
      }
      for (Item item: items) {
        item.selected = reactionsState;
      }
      doneVisible = hasAnyChanges();
      synchronized (adapter) {
        adapter.notifyDataSetChanged();
      }
      doneButton.setIsVisible(doneVisible, false);
      int numOfSelected = reactionsState ? items.size() : 0;
      setTextForCheckBox(numOfSelected);
    });
    updateCheckBox();
    checkBoxLayout.addView(checkBoxView);
    contentView.addView(checkBoxLayout);

    manager = new GridLayoutManager(context, 4);
    Consumer<String> onReactionClick = (String) -> {
      if (items.isEmpty()) return;
      doneVisible = hasAnyChanges();
      doneButton.setIsVisible(doneVisible, false);
      updateCheckBox();
      setTextForCheckBox(getNumberOfSelected());
    };
    BiConsumer<SelectableReactionView, String> loadSticker = (reactionView, reaction) -> {
      tdlib.client().send(new TdApi.GetAnimatedEmoji(reaction), result -> {
        if (result.getConstructor() == TdApi.AnimatedEmoji.CONSTRUCTOR) {
          TdApi.AnimatedEmoji emoji = (TdApi.AnimatedEmoji) result;
          TGStickerObj tgStickerObj = new TGStickerObj(tdlib, emoji.sticker, reaction, new TdApi.StickerTypeStatic());
          reactionView.setSticker(tgStickerObj);
        }
      });
    };
    adapter = new SelectReactionsController.ReactionAdapter(context, items, onReactionClick, loadSticker);

    recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, contentView);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
    recyclerView.setAdapter(adapter);
    contentView.addView(recyclerView);

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);

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
    wrapper.addView(doneButton);
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

  private boolean differentReactionStates () {
    for (int i = 1; i < items.size(); i ++) {
      if (items.get(i).selected != items.get(i - 1).selected) {
        return true;
      }
    }
    return false;
  }

  private int getNumberOfSelected () {
    int count = 0;
    for (int i = 0; i < items.size(); i ++) {
      if (items.get(i).selected) {
        count++;
      }
    }
    return count;
  }

  private void setTextForCheckBox (int num) {
    String text = Lang.getString(R.string.ReactionsDisabled);
    if (num > 0) {
      text = num + " " + Lang.getString(R.string.ReactionsEnabled);
    }
    checkBoxText.setText(text, null, true);
  }

  private void updateCheckBox() {
    if (differentReactionStates()) {
      checkBoxView.setState(ThreeStateCheckBoxView.State.INTERIM);
    } else if (items.get(0).selected == true) {
      checkBoxView.setState(ThreeStateCheckBoxView.State.TRUE);
    } else {
      checkBoxView.setState(ThreeStateCheckBoxView.State.FALSE);
    }
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
          caption = Lang.getString(R.string.CryingFace);
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
        case "\uD83D\uDC4F": {
          caption = Lang.getString(R.string.ClappingHands);
          break;
        }
        case "\uD83E\uDD14": {
          caption = Lang.getString(R.string.ThinkingFace);
          break;
        }
        case "\uD83E\uDD2F": {
          caption = Lang.getString(R.string.ExplodingHead);
          break;
        }
        case "\uD83D\uDE22": {
          caption = Lang.getString(R.string.AngryFace);
          break;
        }
        case "\uD83E\uDD70": {
          caption = Lang.getString(R.string.SmilingFaceWithHearts);
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
    private static final int REACTION_SIZE = Screen.dp(80f);
    private static final int REACTION_PADDING = Screen.dp(40f);

    private final Context context;
    private final List<SelectReactionsController.Item> items;
    private final Consumer<String> onReactionClick;
    private final BiConsumer<SelectableReactionView, String> loadSticker;

    public ReactionAdapter (
        Context context,
        List<SelectReactionsController.Item> items,
        Consumer<String> onReactionClick,
        BiConsumer<SelectableReactionView, String> loadSticker
    ) {
      this.context = context;
      this.items = items;
      this.onReactionClick = onReactionClick;
      this.loadSticker = loadSticker;
    }

    @NonNull
    @Override
    public SelectReactionsController.ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      LinearLayout wrapper = new LinearLayout(context);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
      wrapper.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      RippleSupport.setTransparentSelector(wrapper);

      SelectableReactionView reactionView = new SelectableReactionView(context, REACTION_PADDING);
      reactionView.setLayoutParams(new LinearLayout.LayoutParams(REACTION_SIZE, REACTION_SIZE));
      wrapper.addView(reactionView);

      TextView captionView = new TextView(context);
      captionView.setGravity(Gravity.CENTER_HORIZONTAL);
      captionView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
      loadSticker.accept(holder.reactionView, item.reaction);
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
