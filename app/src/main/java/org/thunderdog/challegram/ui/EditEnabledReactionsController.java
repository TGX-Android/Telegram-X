package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.ReactionCheckboxSettingsView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditEnabledReactionsController extends EditBaseController<EditEnabledReactionsController.Args> implements View.OnClickListener, StickerSmallView.StickerMovementCallback, ChatListener {

  public static final int TYPE_ENABLED_REACTIONS = 0;
  public static final int TYPE_QUICK_REACTION = 1;

  public static class Args {
    public TdApi.Chat chat;
    public int type;

    public Args (TdApi.Chat chat, int type) {
      this.chat = chat;
      this.type = type;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.Chat chat;
  private int type;

  private final HashMap<String, TdApi.Reaction> enabledReactions = new HashMap<>();
  private final ArrayList<String> quickReactionsList = new ArrayList<>();

  public EditEnabledReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (EditEnabledReactionsController.Args args) {
    super.setArguments(args);
    this.chat = args.chat;
    this.type = args.type;
  }

  @Override
  public int getId () {
    return R.id.controller_enabledReactions;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    enabledReactions.clear();
    quickReactionsList.clear();
    if (chat != null && type == TYPE_ENABLED_REACTIONS) {
      for (int a = 0; a < chat.availableReactions.length; a++) {
        TGReaction reaction = tdlib.getReaction(chat.availableReactions[a]);
        if (reaction != null) {
          enabledReactions.put(reaction.reaction.reaction, reaction.reaction);
        }
      }
    }

    if (type == TYPE_QUICK_REACTION) {
      quickReactionsList.addAll(Arrays.asList(Settings.instance().getQuickReactions()));
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        v.setDrawModifier(item.getDrawModifier());
        int viewId = v.getId();
        if (viewId == R.id.reactions_enabled) {
          CheckBoxView view = v.findCheckBox();
          if (view != null) {
            if (enabledReactions.isEmpty()) {
              v.setName(R.string.ReactionsDisabled);
              view.setChecked(false, isUpdate);
            } else {
              v.setName(Lang.plural(R.string.ReactionsEnabled, enabledReactions.size()));
              view.setPartially(enabledReactions.size() != tdlib.getTotalActiveReactionsCount(), isUpdate);
              view.setChecked(true, isUpdate);
            }
          }
        }
        if (viewId == R.id.btn_quick_reaction_enabled) {
          TogglerView view = v.getToggler();
          if (view != null) {
            view.setRadioEnabled(quickReactionsList.size() > 0, isUpdate);
          }
        }
      }

      @Override
      protected void setReaction (ListItem item, int position, ReactionCheckboxSettingsView userView, boolean isUpdate) {
        final String reaction = String.valueOf(item.getString());
        final TGReaction reactionsObj = tdlib.getReaction(reaction);
        userView.getStickerSmallView().setStickerMovementCallback(EditEnabledReactionsController.this);
        userView.getStickerSmallView().setTag(reactionsObj);
        if (reactionsObj != null) {
          userView.setReaction(reactionsObj);
          if (type == TYPE_ENABLED_REACTIONS) {
            userView.setChecked(enabledReactions.containsKey(reactionsObj.getReaction().reaction), isUpdate);
          } else if (type == TYPE_QUICK_REACTION) {
            int index = quickReactionsList.indexOf(reaction);
            userView.setNumber(index >= 0 ? index + 1: index, isUpdate);
          }
        }
      }

      @Override
      public void onBindViewHolder (SettingHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        if (holder.getItemViewType() == ListItem.TYPE_HEADER_WITH_ACTION) {
          ImageView imageView = (ImageView) ((FrameLayoutFix) holder.itemView).getChildAt(1);
          imageView.setColorFilter(Theme.getColor(R.id.theme_color_text));
          holder.itemView.setOnClickListener((v) -> {
            imageView.performClick();
          });
        }
      }
    };

    GridLayoutManager manager = new GridLayoutManager(context, 4);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int type = adapter.getItemViewType(position);
        return (type == ListItem.TYPE_REACTION_CHECKBOX) ? 1 : 4;
      }
    });

    ArrayList<ListItem> items = new ArrayList<>();
    if (type == TYPE_ENABLED_REACTIONS) {
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.reactions_enabled, 0, R.string.ReactionsDisabled, R.id.reactions_enabled, !enabledReactions.isEmpty()));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.ReactionsDisabledDesc), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    } else if (type == TYPE_QUICK_REACTION) {
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_quick_reaction_enabled, 0, R.string.QuickReactionEnable));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.QuickReactionEnableDesc), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }

    for (TGReaction reaction : tdlib.getNotPremiumReactions()) {
      items.add(new ListItem(ListItem.TYPE_REACTION_CHECKBOX, R.id.btn_enabledReactionsCheckboxGroup, 0, reaction.reaction.reaction, false));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (!needPremiumRestriction()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, R.id.reactions_premium_locked, 0, R.string.PremiumReactions));
    } else {
      items.add(new ListItem(ListItem.TYPE_HEADER_WITH_ACTION, R.id.reactions_premium_locked, R.drawable.baseline_lock_16, R.string.PremiumReactions));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (TGReaction reaction : tdlib.getOnlyPremiumReactions()) {
      items.add(new ListItem(ListItem.TYPE_REACTION_CHECKBOX, R.id.btn_enabledReactionsCheckboxGroup, 0, reaction.reaction.reaction, false));
    }

    adapter.setItems(items, true);

    GridSpacingItemDecoration decoration = new GridSpacingItemDecoration(4, Screen.dp(3f), true, true, true);
    decoration.setNeedDraw(true, ListItem.TYPE_REACTION_CHECKBOX);
    decoration.setDrawColorId(R.id.theme_color_filling);
    decoration.setSpanSizeLookup(manager.getSpanSizeLookup());
    recyclerView.addItemDecoration(decoration);
    recyclerView.setItemAnimator(null);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    addThemeInvalidateListener(recyclerView);
    recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        context().reactionsOverlayManager().addOffset(0, -dy);
      }
    });

    context().reactionsOverlayManager();
  }

  private boolean needPremiumRestriction () {
    return type == TYPE_QUICK_REACTION && !tdlib.hasPremium();
  }

  private void showPremiumLockTooltip (View v) {
    context().tooltipManager().builder(v).show(tdlib, Lang.getMarkdownString(this, R.string.PremiumReactionsLocked));
  }

  private void showQuickReactionsLimit (View v) {
    context().tooltipManager().builder(v).show(tdlib, Lang.getString(R.string.QuickReactionsLimit));
  }

  @Override
  protected void onTranslationChanged (float newTranslationX) {
    context().reactionsOverlayManager().setControllerTranslationX((int) newTranslationX);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();

    if (viewId == R.id.reactions_premium_locked && needPremiumRestriction()) {
      showPremiumLockTooltip(v);
    }

    if (viewId == R.id.reactions_enabled) {
      if (enabledReactions.isEmpty()) {
        for (TGReaction tgReaction : tdlib.getNotPremiumReactions()) {
          enabledReactions.put(tgReaction.reaction.reaction, tgReaction.reaction);
        }
        for (TGReaction tgReaction : tdlib.getOnlyPremiumReactions()) {
          enabledReactions.put(tgReaction.reaction.reaction, tgReaction.reaction);
        }
      } else {
        enabledReactions.clear();
      }

      adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
      adapter.updateValuedSettingById(R.id.reactions_enabled);
    }

    if (viewId == R.id.btn_quick_reaction_enabled) {
      if (quickReactionsList.size() == 0) {
        quickReactionsList.add("\uD83D\uDC4D");
      } else {
        quickReactionsList.clear();
      }
      updateQuickReactionsSettings();
      adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
      adapter.updateValuedSettingById(R.id.btn_quick_reaction_enabled);
    }

    if (v instanceof ReactionCheckboxSettingsView) {
      ReactionCheckboxSettingsView reactionView = (ReactionCheckboxSettingsView) v;
      TGStickerObj sticker = reactionView.getSticker();
      if (sticker != null) {
        String emoji = sticker.getFoundByEmoji();
        TGReaction tgReaction = tdlib.getReaction(emoji);
        if (tgReaction != null) {
          boolean checked = false;
          if (type == TYPE_ENABLED_REACTIONS) {
            if (enabledReactions.containsKey(emoji)) {
              enabledReactions.remove(emoji);
            } else {
              checked = true;
              enabledReactions.put(emoji, tgReaction.getReaction());
            }

            adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
            adapter.updateValuedSettingById(R.id.reactions_enabled);
          }
          if (type == TYPE_QUICK_REACTION) {
            if (quickReactionsList.contains(emoji)) {
              quickReactionsList.remove(emoji);
            } else {
              if (!needPremiumRestriction()|| !tgReaction.reaction.isPremium) {
                if (quickReactionsList.size() < 4) {
                  checked = true;
                  quickReactionsList.add(emoji);
                } else {
                  showQuickReactionsLimit(v);
                }
              } else {
                showPremiumLockTooltip(v);
              }
            }
            updateQuickReactionsSettings();
            adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
            adapter.updateValuedSettingById(R.id.btn_quick_reaction_enabled);
          }

          if (checked) {
            TGStickerObj stickerObj = tgReaction.newAroundAnimationSicker();

            int[] positionCords = new int[2];
            v.getLocationOnScreen(positionCords);
            positionCords[0] += v.getMeasuredWidth() / 2;
            positionCords[1] += Screen.dp(40);

            context().reactionsOverlayManager().addOverlay(stickerObj, new Rect(
              positionCords[0] - Screen.dp(50),
              positionCords[1] - Screen.dp(50),
              positionCords[0] + Screen.dp(50),
              positionCords[1] + Screen.dp(50)
            ));
          }
        }
      }
    }
  }

  private void updateEnabledReactions () {
    tdlib.client().send(new TdApi.SetChatAvailableReactions(chat.id, enabledReactions.keySet().toArray(new String[0])), tdlib.okHandler());
  }

  private void checkEnabledReactions (String[] availableReactions) {
    enabledReactions.clear();
    if (availableReactions != null) {
      for (int a = 0; a < availableReactions.length; a++) {
        TGReaction reaction = tdlib.getReaction(availableReactions[a]);
        if (reaction != null) {
          enabledReactions.put(reaction.reaction.reaction, reaction.reaction);
        }
      }
    }
    adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
    adapter.updateValuedSettingById(R.id.reactions_enabled);
  }

  @Override
  public CharSequence getName () {
    if (type == TYPE_ENABLED_REACTIONS)
      return Lang.getString(R.string.Reactions);
    else
      return Lang.getString(R.string.QuickReaction);
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  public void onChatAvailableReactionsUpdated (long chatId, String[] availableReactions) {
    tdlib.ui().post(() -> {
      if (chat.id == chatId) {
        checkEnabledReactions(availableReactions);
      }
    });
  }


  @Override
  public void onFocus () {
    super.onFocus();
    if (chat != null) {
      subscribeToUpdates(chat.id);
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (chat != null && type == TYPE_ENABLED_REACTIONS) {
      updateEnabledReactions();
      unsubscribeFromUpdates(chat.id);
    }
  }

  public void subscribeToUpdates (long chatId) {
    tdlib.listeners().subscribeToChatUpdates(chatId, this);
  }

  public void unsubscribeFromUpdates (long chatId) {
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
  }

  private void updateQuickReactionsSettings () {
    Settings.instance().setQuickReactions(quickReactionsList.toArray(new String[0]));
  }

  // StickerView Callback


  @Override
  public TGReaction getReactionForPreview (StickerSmallView v) {
    Object tag = v.getTag();
    if (tag instanceof TGReaction) {
      return (TGReaction) tag;
    }
    return null;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    onClick(((View) view.getParent()));
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    return 0;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return false;
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }


  @Override
  public boolean needShowButtons () {
    return false;
  }

  @Override
  public StickerSmallView getStickerViewUnder (StickerSmallView v, int x, int y) {
    View view = recyclerView.findChildViewUnder(x, y);
    if (view instanceof ReactionCheckboxSettingsView) {
      return ((ReactionCheckboxSettingsView) view).getStickerSmallView();
    }
    return null;
  }

  @Override
  public int getStickerViewLeft (StickerSmallView v) {
    ViewParent parent = v.getParent();
    if (parent instanceof View) {
      return ((View) parent).getLeft();
    }
    return -1;
  }

  @Override
  public int getStickerViewTop (StickerSmallView v) {
    ViewParent parent = v.getParent();
    if (parent instanceof View) {
      return ((View) parent).getTop();
    }
    return -1;
  }

  @Override
  public int getStickersListTop () {
    return Views.getLocationInWindow(recyclerView)[1];
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }
}
