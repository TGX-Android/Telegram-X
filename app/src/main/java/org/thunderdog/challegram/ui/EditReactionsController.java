package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.CheckBoxTriStates;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.SelectableReaction;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.TdConstants;

public class EditReactionsController extends EditBaseController<EditReactionsController.Args> implements View.OnClickListener, TdlibCache.BasicGroupDataChangeListener {
  public static final int MODE_REACTIONS = 1;
  public static final int MODE_QUICK_REACTIONS = 2;

  private SettingsAdapter settingsAdapter;
  private ReactionAdapter reactionAdapter;
  private RecyclerView reactionsGridView;
  public List<ReactionItem> reactionItems;

  public static class Args {
    public static final String[] reactions = new String[]{
      "\uD83D\uDC4D",
      "\uD83D\uDC4E",
      "\u2764",
      "\uD83D\uDD25",
      "\uD83E\uDD70",
      "\uD83D\uDC4F",
      "\uD83D\uDE04",
      "\uD83E\uDD14",
      "\uD83E\uDD2F",
      "\uD83D\uDE31",
      "\uD83E\uDD2C",
      "\uD83D\uDE22",
      "\uD83C\uDF89",
      "\uD83E\uDD29",
      "\uD83E\uDD2E",
      "\uD83D\uDCA9"
    };
    public static final int[] reactionsTitles = new int[]{
      R.string.Reaction_ThumbsUp,
      R.string.Reaction_ThumbsDown,
      R.string.Reaction_RedHeart,
      R.string.Reaction_Fire,
      R.string.Reaction_SmilingFaceWithHearts,
      R.string.Reaction_ClappingHands,
      R.string.Reaction_BeamingFace,
      R.string.Reaction_ThinkingFace,
      R.string.Reaction_ExplodingHead,
      R.string.Reaction_ScreamingFace,
      R.string.Reaction_FaceWithSymbolsOnMouth,
      R.string.Reaction_CryingFace,
      R.string.Reaction_PartyPopper,
      R.string.Reaction_StarStruck,
      R.string.Reaction_FaceVomiting,
      R.string.Reaction_PileOfPoo
    };

    public long chatId;
    public final int mode;
    public List<String> selectedReactions;
    public boolean quickReactionEnabled;
    public boolean reactionsEnabled;

    public Args (long chatId, boolean reactionsEnabled, List<String>  selectedReactions) {
      this.chatId = chatId;
      this.mode = MODE_REACTIONS;
      this.reactionsEnabled = reactionsEnabled;
      this.selectedReactions = new ArrayList<>(selectedReactions);
    }

    public Args (boolean quickReactionEnabled, String selectedReaction) {
      this.mode = MODE_QUICK_REACTIONS;
      this.selectedReactions = List.of(selectedReaction);
      this.quickReactionEnabled = quickReactionEnabled;
    }

    public boolean isSelected(String reaction) {
      return selectedReactions.contains(reaction);
    }
  }

  public EditReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.reactionItems = new ArrayList<>();
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
  }

  @Override
  public int getId () {
    return R.id.controller_allowedReactions;
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    buildSettingsItems();
    recyclerView.setAdapter(settingsAdapter);

    buildReactionGridView(context, contentView, recyclerView);
    contentView.addView(reactionsGridView);
  }

  @Override
  @SuppressWarnings("WrongConstant")
  public void onClick (View view) {
    EditReactionsController.Args args = getArgumentsStrict();
    ListItem item = (ListItem) view.getTag();

    switch (item.getId()) {
      case R.id.btn_enableQuickReaction:
        args.quickReactionEnabled = !args.quickReactionEnabled;
        if (!args.quickReactionEnabled) {
          clearSelection();
        }
        ((SettingView) view).getToggler().setRadioEnabled(args.quickReactionEnabled, true);
        settingsAdapter.updateValuedSettingById(R.id.btn_enableQuickReaction);
        break;
      case R.id.btn_enableReactions: {
        args.reactionsEnabled = !args.reactionsEnabled;
        if (!args.reactionsEnabled) {
          clearSelection();
        }
        ((SettingView) view).findCheckBox().setChecked(args.reactionsEnabled, true);
        settingsAdapter.updateValuedSettingById(R.id.btn_enableReactions);
        break;
      }
    }
  }

  private void buildSettingsItems () {
    EditReactionsController.Args args = getArgumentsStrict();
    settingsAdapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(item.getStringValue());
        editText.setMaxLength(TdConstants.MAX_CUSTOM_TITLE_LENGTH);
        if (parent.getBackground() == null) {
          ViewSupport.setThemedBackground(parent, R.id.theme_color_filling, EditReactionsController.this);
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        v.setDrawModifier(item.getDrawModifier());
        switch (item.getId()) {
          case R.id.btn_enableQuickReaction: {
            v.getToggler().setRadioEnabled(args.quickReactionEnabled, isUpdate);
            break;
          }
          case R.id.btn_enableReactions: {
            v.findCheckBox().setChecked(args.reactionsEnabled, isUpdate);
            break;
          }
        }
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    switch (args.mode) {
        case MODE_REACTIONS: {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_enableReactions, 0, R.string.EnableReactions).setBoolValue(args.quickReactionEnabled));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AllowMembersToReact));
          items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.AvailableReactions));
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          break;
        }
        case MODE_QUICK_REACTIONS:
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_enableQuickReaction, 0, R.string.EnableQuickReaction).setBoolValue(args.quickReactionEnabled));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AvailabilityOfSpecificReactions));
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_NO_HEAD));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          break;
      }
    settingsAdapter.setItems(items, false);
  }

  private void buildReactionGridView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    EditReactionsController.Args args = getArgumentsStrict();
    for (int i = 0; i < Args.reactions.length; i++) {
      reactionItems.add(new ReactionItem(Args.reactions[i], Args.reactionsTitles[i], args.isSelected(Args.reactions[i])));
    }

    View.OnClickListener onClickListener = view -> {
      boolean isSelectionAllowed = (args.reactionsEnabled && args.mode == MODE_REACTIONS) || (args.quickReactionEnabled && args.mode == MODE_QUICK_REACTIONS);
      if (!isSelectionAllowed) {
        return;
      }
      if (args.mode == MODE_REACTIONS) {
        onReactionItemPressedMultiSelect(args, view);
      } else {
        onReactionItemClickSingleSelect(args, view);
      }
    };
    reactionAdapter = new ReactionAdapter(context, reactionItems, onClickListener);

    reactionsGridView = (CustomRecyclerView) Views.inflate(context, R.layout.recycler_custom, contentView);
    reactionsGridView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    reactionsGridView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS : RecyclerView.OVER_SCROLL_NEVER);
    reactionsGridView.setVerticalScrollBarEnabled(false);
    reactionsGridView.setLayoutManager(new GridLayoutManager(context, 4));
    reactionsGridView.setAdapter(reactionAdapter);
    ViewSupport.setThemedBackground(reactionsGridView, R.id.theme_color_filling, EditReactionsController.this);

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    recyclerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    params.topMargin = recyclerView.getMeasuredHeight();
    reactionsGridView.setLayoutParams(params);
    reactionsGridView.setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
  }

  private void onReactionItemClickSingleSelect (Args args, View view) {
    SelectableReaction reaction = ((SelectableReaction) view);
    args.selectedReactions = new ArrayList<>();
    args.selectedReactions.add(((SelectableReaction) view).getReaction());
    for (ReactionItem item : reactionItems) {
      item.isSelected = item.reactionCode.equals(((SelectableReaction) view).getReaction());
    }
    view.setSelected(!view.isSelected());
    reactionAdapter.notifyDataSetChanged();
    reaction.holder.invalidateViewAlpha(view);
  }

  private void onReactionItemPressedMultiSelect (Args args, View view) {
    SelectableReaction reaction = ((SelectableReaction) view);
    for (ReactionItem item : reactionItems) {
      if (item.reactionCode.equals(reaction.getReaction())) {
        item.isSelected = !view.isSelected();
      }
    }
    reactionAdapter.notifyDataSetChanged();
    reaction.holder.invalidateViewAlpha(view);
  }

  private void clearSelection() {
    for (ReactionItem item : reactionItems) {
        item.isSelected = false;
    }
    reactionAdapter.notifyDataSetChanged();
  }

  @Override
  public CharSequence getName () {
    EditReactionsController.Args args = getArgumentsStrict();
    switch (args.mode) {
      case MODE_REACTIONS:
        return Lang.getString(R.string.Reactions);
      case MODE_QUICK_REACTIONS:
        return Lang.getString(R.string.QuickReaction);
    }
    throw new AssertionError();

  }

  public static class ReactionAdapter extends RecyclerView.Adapter<ReactionItemHolder> {
    public List<ReactionItem> getItems () {
      return items;
    }

    public List<ReactionItem> items;
    private final Context context;
    public View.OnClickListener onClickListener;

    public ReactionAdapter (Context context, List<ReactionItem> items, View.OnClickListener onClickListener) {
      this.context = context;
      this.items = items;
      this.onClickListener = onClickListener;
    }

    @NonNull
    @Override
    public ReactionItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      SelectableReaction selectableReaction = new SelectableReaction(context);
      RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(Screen.dp(54f), Screen.dp(54f));
      selectableReaction.setPadding(Screen.dp(60f), Screen.dp(60f), Screen.dp(60f), Screen.dp(60f));
      selectableReaction.setLayoutParams(params);
      Views.setClickable(selectableReaction);

      TextView reactionTitle = new TextView(context);
      RelativeLayout.LayoutParams reactionTitleParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      reactionTitleParams.topMargin = Screen.dp(4f);
      reactionTitle.setLayoutParams(reactionTitleParams);
      reactionTitle.setMaxLines(2);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        reactionTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
      }
      reactionTitle.setEllipsize(TextUtils.TruncateAt.END);
      reactionTitle.setId(R.id.text_reactionTitle);

      RecyclerView.LayoutParams linearLayoutParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      linearLayoutParams.setMargins(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(8f));
      LinearLayout mainLayout = new LinearLayout(context);
      mainLayout.setLayoutParams(linearLayoutParams);
      mainLayout.setOrientation(LinearLayout.VERTICAL);
      mainLayout.setGravity(Gravity.CENTER);
      mainLayout.addView(selectableReaction);
      mainLayout.addView(reactionTitle);

      return new ReactionItemHolder(mainLayout, selectableReaction, reactionTitle, onClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionItemHolder holder, int position) {
      ReactionItem item = items.get(position);
      holder.itemView.setId(R.id.emoji);
      holder.reactionTitle.setText(Lang.getString(item.reactionTitle));
      holder.selectableReaction.setReaction(item.reactionCode);
      holder.selectableReaction.setSelected(item.isSelected);
      holder.selectableReaction.setOnClickListener(holder.onClickListener);
      holder.invalidateViewAlpha(holder.selectableReaction);
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }

  public static class ReactionItem {
    public final String reactionCode;
    public final int reactionTitle;
    public boolean isSelected;

    public ReactionItem (String reactionCode, int reactionTitle, boolean isSelected) {
      this.reactionCode = reactionCode;
      this.reactionTitle = reactionTitle;
      this.isSelected = isSelected;
    }
  }

  public static class ReactionItemHolder extends RecyclerView.ViewHolder {
    public View itemView;
    public SelectableReaction selectableReaction;
    public TextView reactionTitle;
    public View.OnClickListener onClickListener;

    public ReactionItemHolder (@NonNull View itemView, SelectableReaction selectableReaction, TextView reactionTitle, View.OnClickListener onClickListener) {
      super(itemView);
      this.itemView = itemView;
      this.selectableReaction = selectableReaction;
      this.reactionTitle = reactionTitle;
      this.onClickListener = onClickListener;
      selectableReaction.setHolder(this);
    }

    private void invalidateViewAlpha (View view) {
      float alpha = view.isSelected() ? 1f : .5f;
      view.setAlpha(alpha);
      ((SelectableReaction) view).holder.reactionTitle.setAlpha(alpha);
    }
  }
}
