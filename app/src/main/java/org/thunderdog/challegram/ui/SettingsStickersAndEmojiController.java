/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiModifier;
import org.thunderdog.challegram.util.ReactionModifier;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SettingsStickersAndEmojiController extends RecyclerViewController<SettingsController> implements View.OnClickListener, StickersListener, SettingsController.StickerSetLoadListener, TGLegacyManager.EmojiLoadListener {
  private SettingsAdapter adapter;
  private int stickerSetsCount = -1;
  private int emojiPacksCount = -1;

  public SettingsStickersAndEmojiController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        v.setDrawModifier(item.getDrawModifier());

        final int itemId = item.getId();
        if (itemId == R.id.btn_quick_reaction) {
          final String[] reactions = Settings.instance().getQuickReactions(tdlib);
          tdlib.ensureReactionsAvailable(reactions, reactionsUpdated -> {
            if (reactionsUpdated) {
              runOnUiThreadOptional(() -> updateQuickReaction());
            }
          });
          StringBuilder stringBuilder = new StringBuilder();
          if (reactions.length > 0) {
            final List<TGReaction> tgReactions = new ArrayList<>(reactions.length);
            for (String reactionKey : reactions) {
              TdApi.ReactionType reactionType = TD.toReactionType(reactionKey);
              final TGReaction tgReaction = tdlib.getReaction(reactionType, false);
              if (tgReaction != null) {
                tgReactions.add(tgReaction);
                if (stringBuilder.length() > 0) {
                  stringBuilder.append(Lang.getConcatSeparator());
                }
                stringBuilder.append(tgReaction.getTitle());
              }
            }
            v.setDrawModifier(new ReactionModifier(tgReactions.toArray(new TGReaction[0])).requestFiles(v.getComplexReceiver()));
            v.setData(stringBuilder.toString());
          } else {
            v.setDrawModifier(null);
            v.setData(R.string.QuickReactionDisabled);
          }
        } else if (itemId == R.id.btn_big_reactions) {
          StringBuilder b = new StringBuilder();
          if (Settings.instance().getBigReactionsInChats()) {
            b.append(Lang.getString(R.string.BigReactionsChats));
          }
          if (Settings.instance().getBigReactionsInChannels()) {
            if (b.length() > 0) {
              b.append(Lang.getConcatSeparator());
            }
            b.append(Lang.getString(R.string.BigReactionsChannels));
          }
          if (b.length() == 0) {
            b.append(Lang.getString(R.string.BigReactionsNone));
          }
          v.setData(b.toString());
        } else if (itemId == R.id.btn_emoji) {
          Settings.EmojiPack emojiPack = Settings.instance().getEmojiPack();
          if (emojiPack.identifier.equals(BuildConfig.EMOJI_BUILTIN_ID)) {
            v.setData(R.string.EmojiBuiltIn);
          } else {
            v.setData(emojiPack.displayName);
          }
        } else if (itemId == R.id.btn_useBigEmoji) {
          v.getToggler().setRadioEnabled(Settings.instance().useBigEmoji(), isUpdate);
        } else if (itemId == R.id.btn_toggleNewSetting) {
          boolean value = Settings.instance().getNewSetting(item.getLongId());
          if (item.getBoolValue())
            value = !value;
          v.getToggler().setRadioEnabled(value, isUpdate);
        } else if (itemId == R.id.btn_animatedEmojiSettings) {
          if (emojiPacksCount != -1) {
            v.setData(Lang.plural(R.string.xEmojiSetsInstalled, emojiPacksCount));
          } else {
            v.setData(Lang.getString(R.string.xEmojiSetsInstalledUnknown));
          }
        } else if (itemId == R.id.btn_stickerSettings) {
          if (stickerSetsCount != -1) {
            v.setData(Lang.plural(R.string.xStickerSetsInstalled, stickerSetsCount));
          } else {
            v.setData(Lang.getString(R.string.xStickerSetsInstalledUnknown));
          }
        } else if (itemId == R.id.btn_stickerSuggestions) {
          switch (Settings.instance().getStickerMode()) {
            case Settings.STICKER_MODE_ALL:
              v.setData(R.string.SuggestStickersAll);
              break;
            case Settings.STICKER_MODE_ONLY_INSTALLED:
              v.setData(R.string.SuggestStickersInstalled);
              break;
            case Settings.STICKER_MODE_NONE:
              v.setData(R.string.SuggestStickersNone);
              break;
          }
        } else if (itemId == R.id.btn_avatarsInReactions) {
          switch (Settings.instance().getReactionAvatarsMode()) {
            case Settings.REACTION_AVATARS_MODE_ALWAYS:
              v.setData(R.string.AvatarsInReactionsAlways);
              break;
            case Settings.REACTION_AVATARS_MODE_SMART_FILTER:
              v.setData(R.string.AvatarsInReactionsSmartFilter);
              break;
            case Settings.REACTION_AVATARS_MODE_NEVER:
              v.setData(R.string.AvatarsInReactionsNever);
              break;
          }
        } else if (itemId == R.id.btn_emojiSuggestions) {
          switch (Settings.instance().getEmojiMode()) {
            case Settings.STICKER_MODE_ALL:
              v.setData(R.string.SuggestStickersAll);
              break;
            case Settings.STICKER_MODE_ONLY_INSTALLED:
              v.setData(R.string.SuggestStickersInstalled);
              break;
            case Settings.STICKER_MODE_NONE:
              v.setData(R.string.SuggestStickersNone);
              break;
          }
        }
      }
    };
    recyclerView.setAdapter(adapter);

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Reactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_quick_reaction, 0, R.string.QuickReaction));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_big_reactions, 0, R.string.BigReactions));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_avatarsInReactions, 0, R.string.AvatarsInReactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.EmojiHeader));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_animatedEmojiSettings, R.drawable.baseline_emoticon_outline_24, R.string.EmojiPacks));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_emoji, 0, R.string.Emoji).setDrawModifier(new EmojiModifier(Lang.getString(R.string.EmojiPreview), Paints.emojiPaint())));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_emojiSuggestions, 0, R.string.SuggestAnimatedEmoji));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.AnimatedEmoji).setLongId(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI).setBoolValue(true));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useBigEmoji, 0, R.string.BigEmoji));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Stickers));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_stickerSettings, R.drawable.deproko_baseline_insert_sticker_24, R.string.StickerPacks));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_stickerSuggestions, 0, R.string.SuggestStickers));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.LoopAnimatedStickers).setLongId(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP).setBoolValue(true));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    TGLegacyManager.instance().addEmojiListener(this);

    adapter.setItems(items, true);
    tdlib.listeners().subscribeToStickerUpdates(this);
  }

  public void updateSelectedEmoji () {
    if (adapter != null)
      adapter.updateValuedSettingById(R.id.btn_emoji);
  }

  public void updateQuickReaction () {
    if (adapter != null)
      adapter.updateValuedSettingById(R.id.btn_quick_reaction);
  }

  private void invalidateById (int id) {
    if (adapter != null) {
      int index = adapter.indexOfViewById(id);
      View view = getRecyclerView().getLayoutManager().findViewByPosition(index);
      if (view != null)
        view.invalidate();
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (isPackSwitch) {
      updateSelectedEmoji();
    } else {
      invalidateById(R.id.btn_emoji);
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_quick_reaction) {
      EditEnabledReactionsController c = new EditEnabledReactionsController(context, tdlib);
      c.setArguments(new EditEnabledReactionsController.Args(null, EditEnabledReactionsController.TYPE_QUICK_REACTION));
      navigateTo(c);
    } else if (viewId == R.id.btn_big_reactions) {
      showSettings(R.id.btn_big_reactions, new ListItem[] {
        new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.BigReactionsInfo),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_bigReactionsChats, 0, R.string.BigReactionsChats, R.id.btn_bigReactionsChats, Settings.instance().getBigReactionsInChats()),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_bigReactionsChannels, 0, R.string.BigReactionsChannels, R.id.btn_bigReactionsChannels, Settings.instance().getBigReactionsInChannels())
      }, (id, result) -> {
        Settings.instance().setBigReactionsInChannels(result.get(R.id.btn_bigReactionsChannels) == R.id.btn_bigReactionsChannels);
        Settings.instance().setBigReactionsInChats(result.get(R.id.btn_bigReactionsChats) == R.id.btn_bigReactionsChats);
        adapter.updateValuedSettingById(R.id.btn_big_reactions);
      });
    } else if (viewId == R.id.btn_emoji) {
      SettingsCloudEmojiController c = new SettingsCloudEmojiController(context, tdlib);
      c.setArguments(new SettingsCloudController.Args<>(this));
      navigateTo(c);
    } else if (viewId == R.id.btn_useBigEmoji) {
      Settings.instance().setUseBigEmoji(adapter.toggleView(v));
    } else if (viewId == R.id.btn_toggleNewSetting) {
      ListItem item = (ListItem) v.getTag();
      boolean value = adapter.toggleView(v);
      if (item.getBoolValue())
        value = !value;
      Settings.instance().setNewSetting(item.getLongId(), value);
      if (value && item.getLongId() == Settings.SETTING_FLAG_DOWNLOAD_BETAS) {
        context().appUpdater().checkForUpdates();
      }
    } else if (viewId == R.id.btn_stickerSettings) {
      SettingsStickersController c = new SettingsStickersController(context, tdlib, SettingsStickersController.TYPE_STICKER);
      c.setArguments(getArguments());
      navigateTo(c);
    } else if (viewId == R.id.btn_animatedEmojiSettings) {
      SettingsStickersController c = new SettingsStickersController(context, tdlib, SettingsStickersController.TYPE_EMOJI);
      c.setArguments(getArguments());
      navigateTo(c);
    } else if (viewId == R.id.btn_stickerSuggestions) {
      showStickerOptions(false);
    } else if (viewId == R.id.btn_emojiSuggestions) {
      showStickerOptions(true);
    } else if (viewId == R.id.btn_avatarsInReactions) {
      showReactionAvatarsOptions();
    }
  }

  private void showReactionAvatarsOptions () {
    final int reactionAvatarsMode = Settings.instance().getReactionAvatarsMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_avatarsInReactions).setRawItems(new ListItem[]{
      new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.ReactionAvatarsInfo),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_avatarsInReactionsAlways, 0, R.string.AvatarsInReactionsAlways, R.id.btn_avatarsInReactions, reactionAvatarsMode == Settings.REACTION_AVATARS_MODE_ALWAYS),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_avatarsInReactionsSmartFilter, 0, R.string.AvatarsInReactionsSmartFilter, R.id.btn_avatarsInReactions, reactionAvatarsMode == Settings.REACTION_AVATARS_MODE_SMART_FILTER),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_avatarsInReactionsNever, 0, R.string.AvatarsInReactionsNever, R.id.btn_avatarsInReactions, reactionAvatarsMode == Settings.REACTION_AVATARS_MODE_NEVER),
    }).setIntDelegate((id, result) -> {
      int newReactionAvatarsMode = Settings.instance().getReactionAvatarsMode();
      int stickerResultId = result.get(R.id.btn_avatarsInReactions);
      if (stickerResultId == R.id.btn_avatarsInReactionsAlways) {
        newReactionAvatarsMode = Settings.REACTION_AVATARS_MODE_ALWAYS;
      } else if (stickerResultId == R.id.btn_avatarsInReactionsSmartFilter) {
        newReactionAvatarsMode = Settings.REACTION_AVATARS_MODE_SMART_FILTER;
      } else if (stickerResultId == R.id.btn_avatarsInReactionsNever) {
        newReactionAvatarsMode = Settings.REACTION_AVATARS_MODE_NEVER;
      }

      Settings.instance().setReactionAvatarsMode(newReactionAvatarsMode);
      adapter.updateValuedSettingById(R.id.btn_avatarsInReactions);
    }).setAllowResize(false));
  }

  private void showStickerOptions (boolean isEmoji) {
    @IdRes int btnId = isEmoji ? R.id.btn_emojiSuggestions : R.id.btn_stickerSuggestions;
    final int stickerOption = isEmoji ?
      Settings.instance().getEmojiMode():
      Settings.instance().getStickerMode();

    showSettings(new SettingsWrapBuilder(btnId).setRawItems(new ListItem[]{
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerOrEmojiSuggestionsAll, 0, R.string.SuggestStickersAll, btnId, stickerOption == Settings.STICKER_MODE_ALL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerOrEmojiSuggestionsInstalled, 0, R.string.SuggestStickersInstalled, btnId, stickerOption == Settings.STICKER_MODE_ONLY_INSTALLED),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerOrEmojiSuggestionsNone, 0, R.string.SuggestStickersNone, btnId, stickerOption == Settings.STICKER_MODE_NONE),
    }).setIntDelegate((id, result) -> {
      int newStickerMode = Settings.instance().getStickerMode();
      int stickerResultId = result.get(btnId);
      if (stickerResultId == R.id.btn_stickerOrEmojiSuggestionsAll) {
        newStickerMode = Settings.STICKER_MODE_ALL;
      } else if (stickerResultId == R.id.btn_stickerOrEmojiSuggestionsInstalled) {
        newStickerMode = Settings.STICKER_MODE_ONLY_INSTALLED;
      } else if (stickerResultId == R.id.btn_stickerOrEmojiSuggestionsNone) {
        newStickerMode = Settings.STICKER_MODE_NONE;
      }
      if (isEmoji) {
        Settings.instance().setEmojiMode(newStickerMode);
      } else {
        Settings.instance().setStickerMode(newStickerMode);
      }
      adapter.updateValuedSettingById(btnId);
    }).setAllowResize(false)); //.setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, 0, 0, UI.getString(R.string.MarkdownHint), false))
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    updateQuickReaction();
  }

  @Override
  public int getId () {
    return R.id.controller_stickersAndEmoji;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StickersAndEmoji);
  }

  @Override
  public void setArguments (SettingsController args) {
    super.setArguments(args);

    stickerSetsCount = args.getStickerSetsCount(false);
    if (stickerSetsCount == -1) {
      args.addStickerSetListener(false, this);
    }

    emojiPacksCount = args.getStickerSetsCount(true);
    if (emojiPacksCount == -1) {
      args.addStickerSetListener(true, this);
    }
  }

  @Override
  public void onStickerSetsLoaded (ArrayList<TGStickerSetInfo> stickerSets, TdApi.StickerType type) {
    boolean isEmoji = type.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR;
    boolean isStickers = type.getConstructor() == TdApi.StickerTypeRegular.CONSTRUCTOR;
    if (isEmoji) {
      updateStickerSetsCount(true, stickerSets.size());
    } else if (isStickers) {
      updateStickerSetsCount(false, stickerSets.size());
    }
  }

  @Override
  public void onInstalledStickerSetsUpdated (long[] stickerSetIds, TdApi.StickerType stickerType) {
    boolean isEmoji = stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR;
    boolean isStickers = stickerType.getConstructor() == TdApi.StickerTypeRegular.CONSTRUCTOR;
    if (isEmoji) {
      updateStickerSetsCount(true, stickerSetIds.length);
    } else if (isStickers) {
      updateStickerSetsCount(false, stickerSetIds.length);
    }
  }

  private void updateStickerSetsCount (boolean isEmoji, int size) {
    if (getArguments() != null) {
      getArguments().removeStickerSetListener(isEmoji, this);
    }
    if (isEmoji) {
      emojiPacksCount = size;
    } else {
      stickerSetsCount = size;
    }

    if (adapter != null) {
      adapter.updateValuedSettingById(isEmoji ? R.id.btn_animatedEmojiSettings : R.id.btn_stickerSettings);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
    if (getArguments() != null) {
      getArguments().removeStickerSetListener(true, this);
      getArguments().removeStickerSetListener(false, this);
    }
  }
}
