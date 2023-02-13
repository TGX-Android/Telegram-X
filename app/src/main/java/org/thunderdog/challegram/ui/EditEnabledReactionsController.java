/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.ReactionsOverlayView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.RunnableData;

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

  private TdApi.ChatAvailableReactions availableReactions;
  private final Set<String> enabledReactions = new HashSet<>();
  private final List<String> quickReactions = new ArrayList<>();

  public EditEnabledReactionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean reactionsFetched;

  @Override
  public boolean needAsynchronousAnimation () {
    return !reactionsFetched;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
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

  private void updateAvailableReactions (@NonNull TdApi.ChatAvailableReactions availableReactions) {
    this.availableReactions = availableReactions;
    this.enabledReactions.clear();
    switch (availableReactions.getConstructor()) {
      case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR:
        break;
      case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
        TdApi.ChatAvailableReactionsSome some = (TdApi.ChatAvailableReactionsSome) availableReactions;
        for (TdApi.ReactionType reactionType : some.reactions) {
          this.enabledReactions.add(TD.makeReactionKey(reactionType));
        }
        break;
      }
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    switch (type) {
      case TYPE_ENABLED_REACTIONS: {
        updateAvailableReactions(chat.availableReactions);
        break;
      }
      case TYPE_QUICK_REACTION: {
        String[] quickReactions = Settings.instance().getQuickReactions(tdlib);
        this.quickReactions.addAll(Arrays.asList(quickReactions));
        break;
      }
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        v.setDrawModifier(item.getDrawModifier());
        int viewId = v.getId();
        if (viewId == R.id.reactions_enabled) {
          CheckBoxView view = v.findCheckBox();
          if (view != null) {
            int enabledCount = availableReactions == null ? 0 : availableReactions.getConstructor() == TdApi.ChatAvailableReactionsSome.CONSTRUCTOR ? ((TdApi.ChatAvailableReactionsSome) availableReactions).reactions.length : Integer.MAX_VALUE;
            if (enabledCount == 0) {
              v.setName(R.string.ReactionsDisabled);
              view.setChecked(false, isUpdate);
            } else if (enabledCount == Integer.MAX_VALUE) {
              v.setName(Lang.getMarkdownString(EditEnabledReactionsController.this, R.string.ReactionsEnabledAll));
              view.setChecked(true, isUpdate);
            } else {
              v.setName(Lang.pluralBold(R.string.ReactionsEnabled, enabledCount));
              view.setChecked(true, isUpdate);
            }
            view.setPartially(enabledCount != 0 && enabledCount != Integer.MAX_VALUE, isUpdate);
          }
        } else if (viewId == R.id.btn_quick_reaction_enabled) {
          TogglerView view = v.getToggler();
          if (view != null) {
            view.setRadioEnabled(!quickReactions.isEmpty(), isUpdate);
          }
        }
      }

      @Override
      protected void setReaction (ListItem item, int position, ReactionCheckboxSettingsView userView, boolean isUpdate) {
        final String reactionKey = String.valueOf(item.getString());
        final TdApi.ReactionType reactionType = TD.toReactionType(reactionKey);
        final TGReaction reactionsObj = tdlib.getReaction(reactionType);
        userView.getStickerSmallView().setStickerMovementCallback(EditEnabledReactionsController.this);
        userView.getStickerSmallView().setTag(reactionsObj);
        if (reactionsObj != null) {
          userView.setReaction(reactionsObj);
          if (type == TYPE_ENABLED_REACTIONS) {
            if (availableReactions == null) {
              userView.setChecked(false, isUpdate);
            } else {
              switch (availableReactions.getConstructor()) {
                case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR: {
                  userView.setChecked(true, isUpdate);
                  break;
                }
                case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
                  userView.setChecked(enabledReactions.contains(reactionKey), isUpdate);
                  break;
                }
              }
            }
          } else if (type == TYPE_QUICK_REACTION) {
            int index = quickReactions.indexOf(reactionKey);
            userView.setNumber(index >= 0 ? index + 1 : index, isUpdate);
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

    tdlib.ensureEmojiReactionsAvailable(emojiReactionsFetched -> {
      Runnable after = () -> {
        this.reactionsFetched = true;
        buildCells();
        executeScheduledAnimation();
      };
      if (type == TYPE_ENABLED_REACTIONS) {
        tdlib.ensureReactionsAvailable(chat.availableReactions, customReactionsFetched ->
          executeOnUiThreadOptional(after)
        );
      } else {
        executeOnUiThreadOptional(after);
      }
    });
  }

  private ListItem toggleItem;

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (type == TYPE_ENABLED_REACTIONS) {
      items.add(toggleItem = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.reactions_enabled, 0, R.string.ReactionsDisabled, R.id.reactions_enabled, isToggleSelected()));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.ReactionsDisabledDesc), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    } else if (type == TYPE_QUICK_REACTION) {
      items.add(toggleItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_quick_reaction_enabled, 0, R.string.QuickReactionEnable, isToggleSelected()));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.QuickReactionEnableDesc), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }

    String[] activeEmojiReactions = tdlib.getActiveEmojiReactions();
    if (activeEmojiReactions != null) {
      for (String activeEmojiReaction : activeEmojiReactions) {
        TGReaction reaction = tdlib.getReaction(new TdApi.ReactionTypeEmoji(activeEmojiReaction));
        if (reaction != null) {
          items.add(new ListItem(ListItem.TYPE_REACTION_CHECKBOX, R.id.btn_enabledReactionsCheckboxGroup, 0, reaction.key, false));
        }
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    /* TODO: custom reactions
    List<TGReaction> premiumReactions = tdlib.getOnlyPremiumReactions();
    if (!premiumReactions.isEmpty()) {
      if (!needPremiumRestriction()) {
        items.add(new ListItem(ListItem.TYPE_HEADER, R.id.reactions_premium_locked, 0, R.string.PremiumReactions));
      } else {
        items.add(new ListItem(ListItem.TYPE_HEADER_WITH_ACTION, R.id.reactions_premium_locked, R.drawable.baseline_lock_16, R.string.PremiumReactions));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      for (TGReaction reaction : premiumReactions) {
        items.add(new ListItem(ListItem.TYPE_REACTION_CHECKBOX, R.id.btn_enabledReactionsCheckboxGroup, 0, reaction.reaction.reaction, false));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }*/

    adapter.setItems(items, true);
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

  private TdApi.ChatAvailableReactions buildAvailableReactions () {
    TdApi.ReactionType[] availableReactions = new TdApi.ReactionType[enabledReactions.size()];
    int index = 0;
    for (String enabledReaction : enabledReactions) {
      availableReactions[index] = TD.toReactionType(enabledReaction);
      index++;
    }
    return new TdApi.ChatAvailableReactionsSome(availableReactions);
  }

  private boolean isToggleSelected () {
    switch (type) {
      case TYPE_ENABLED_REACTIONS:
        return !enabledReactions.isEmpty() || availableReactions.getConstructor() == TdApi.ChatAvailableReactionsAll.CONSTRUCTOR;
      case TYPE_QUICK_REACTION:
        return !quickReactions.isEmpty();
    }
    return false;
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();

    if (viewId == R.id.reactions_premium_locked && needPremiumRestriction()) {
      showPremiumLockTooltip(v);
    }

    if (viewId == R.id.reactions_enabled) {
      enabledReactions.clear();
      switch (availableReactions.getConstructor()) {
        case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR: {
          // Disable all reactions
          availableReactions = new TdApi.ChatAvailableReactionsSome(new TdApi.ReactionType[0]);
          break;
        }
        case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
          // Enable all reactions
          availableReactions = new TdApi.ChatAvailableReactionsAll();
          break;
        }
      }

      toggleItem.setSelected(isToggleSelected());
      adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
      adapter.updateValuedSettingById(R.id.reactions_enabled);
    }

    if (viewId == R.id.btn_quick_reaction_enabled) {
      if (quickReactions.isEmpty()) {
        quickReactions.add(tdlib.defaultEmojiReaction());
      } else {
        quickReactions.clear();
      }
      toggleItem.setSelected(isToggleSelected());
      updateQuickReactionsSettings();
      adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
      adapter.updateValuedSettingById(R.id.btn_quick_reaction_enabled);
    }

    if (v instanceof ReactionCheckboxSettingsView) {
      ReactionCheckboxSettingsView reactionView = (ReactionCheckboxSettingsView) v;
      TGStickerObj sticker = reactionView.getSticker();
      TdApi.ReactionType reactionType = sticker != null ? sticker.getReactionType() : null;
      TGReaction tgReaction = tdlib.getReaction(reactionType);
      if (tgReaction != null) {
        boolean checked = false;
        switch (type) {
          case TYPE_ENABLED_REACTIONS: {
            switch (availableReactions.getConstructor()) {
              case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR: {
                enabledReactions.clear();
                for (ListItem item : adapter.getItems()) {
                  if (item.getId() == R.id.btn_enabledReactionsCheckboxGroup) {
                    String reactionKey = item.getString().toString();
                    if (!tgReaction.key.equals(reactionKey)) {
                      enabledReactions.add(reactionKey);
                    }
                  }
                }
                break;
              }
              case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
                if (!enabledReactions.remove(tgReaction.key)) {
                  checked = true;
                  enabledReactions.add(tgReaction.key);
                }
                break;
              }
            }
            availableReactions = buildAvailableReactions();

            toggleItem.setSelected(isToggleSelected());
            adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
            adapter.updateValuedSettingById(R.id.reactions_enabled);
            break;
          }
          case TYPE_QUICK_REACTION: {
            if (!quickReactions.remove(tgReaction.key)) {
              if (!needPremiumRestriction()|| !tgReaction.isPremium()) {
                if (quickReactions.size() < 4) {
                  checked = true;
                  quickReactions.add(tgReaction.key);
                } else {
                  showQuickReactionsLimit(v);
                }
              } else {
                showPremiumLockTooltip(v);
              }
            }
            updateQuickReactionsSettings();

            toggleItem.setSelected(isToggleSelected());

            adapter.updateAllValuedSettingsById(R.id.btn_enabledReactionsCheckboxGroup);
            adapter.updateValuedSettingById(R.id.btn_quick_reaction_enabled);
            break;
          }
        }

        if (checked) {
          RunnableData<TGStickerObj> act = (overlaySticker) -> {
            int[] positionCords = new int[2];
            v.getLocationOnScreen(positionCords);
            positionCords[0] += v.getMeasuredWidth() / 2;
            positionCords[1] += Screen.dp(40);
            context().reactionsOverlayManager().addOverlay(new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
              .setSticker(overlaySticker, true)
              .setUseDefaultSprayAnimation(overlaySticker.isCustomReaction())
              .setPosition(new Rect(
              positionCords[0] - Screen.dp(50),
              positionCords[1] - Screen.dp(50),
              positionCords[0] + Screen.dp(50),
              positionCords[1] + Screen.dp(50)
            )));
          };
          TGStickerObj overlaySticker = tgReaction.newAroundAnimationSicker();
          if (overlaySticker != null && !Config.TEST_GENERIC_REACTION_EFFECTS) {
            act.runWithData(overlaySticker);
          } else {
            tdlib.pickRandomGenericOverlaySticker(genericOverlayEffectSticker -> {
              if (genericOverlayEffectSticker != null) {
                TGStickerObj stickerObj = new TGStickerObj(tdlib, genericOverlayEffectSticker, null, genericOverlayEffectSticker.type)
                  .setReactionType(tgReaction.type);
                executeOnUiThreadOptional(() ->
                  act.runWithData(stickerObj)
                );
              }
            });
          }
        }
      }
    }
  }

  private void updateEnabledReactions () {
    tdlib.client().send(new TdApi.SetChatAvailableReactions(chat.id, availableReactions), tdlib.okHandler());
  }

  private void checkEnabledReactions (TdApi.ChatAvailableReactions availableReactions) {
    updateAvailableReactions(availableReactions);
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
  public void onChatAvailableReactionsUpdated (long chatId, TdApi.ChatAvailableReactions availableReactions) {
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
    Settings.instance().setQuickReactions(quickReactions.toArray(new String[0]));
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
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
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
