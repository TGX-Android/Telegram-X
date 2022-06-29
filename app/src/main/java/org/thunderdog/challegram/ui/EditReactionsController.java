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
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.SelectableReaction;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.td.TdConstants;

public class EditReactionsController extends EditBaseController<EditReactionsController.Args> implements TdlibCache.BasicGroupDataChangeListener {
  private static final String[] reactions = new String[]{
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
  private static final int[] reactionsTitles = new int[]{
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

  private SettingsAdapter settingsAdapter;
  private RecyclerView reactionsGridView;

  public static class Args {
    public long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public EditReactionsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
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

  private void buildSettingsItems () {
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
    };
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.theme_chat_classic, 0, R.string.ReactionsDisabled, R.id.btn_reactionsDisabled, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AllowMembersToReact));
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.AvailableReactions));
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    settingsAdapter.setItems(items, false);
  }

  private void buildReactionGridView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    List<ReactionItem> reactionItems = new ArrayList<>();
    for (int i = 0; i < reactions.length; i++) {
      reactionItems.add(new ReactionItem(reactions[i], reactionsTitles[i], false));
    }
    ReactionAdapter reactionAdapter = new ReactionAdapter(context, reactionItems);

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

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Reactions);
  }

  public static class ReactionAdapter extends RecyclerView.Adapter<ReactionItemHolder> {
    public final List<ReactionItem> items;
    private final Context context;

    public ReactionAdapter (Context context, List<ReactionItem> items) {
      this.context = context;
      this.items = items;
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

      return new ReactionItemHolder(mainLayout, selectableReaction, reactionTitle);
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionItemHolder holder, int position) {
      View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick (View view) {
          view.setSelected(!view.isSelected());
          invalidateViewAlpha(view);
        }
      };

      ReactionItem item = items.get(position);
      holder.itemView.setId(R.id.emoji);
      holder.reactionTitle.setText(Lang.getString(item.reactionTitle));
      holder.selectableReaction.setReaction(item.reactionCode);
      holder.selectableReaction.setSelected(item.isSelected);
      holder.selectableReaction.setOnClickListener(onClickListener);
      invalidateViewAlpha(holder.selectableReaction);
    }

    private void invalidateViewAlpha (View view) {
      float alpha = view.isSelected() ? 1f : .5f;
      view.setAlpha(alpha);
      ((SelectableReaction) view).holder.reactionTitle.setAlpha(alpha);
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

    public ReactionItemHolder (@NonNull View itemView, SelectableReaction selectableReaction, TextView reactionTitle) {
      super(itemView);
      this.itemView = itemView;
      this.selectableReaction = selectableReaction;
      this.reactionTitle = reactionTitle;
      selectableReaction.setHolder(this);
    }
  }
}
