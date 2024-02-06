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
 * File created on 06/01/2023
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.ChatFolderStyle;
import org.thunderdog.challegram.telegram.ChatFoldersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AdapterSubListUpdateCallback;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.util.EndIconModifier;
import org.thunderdog.challegram.util.ListItemDiffUtilCallback;
import org.thunderdog.challegram.util.PremiumLockModifier;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.NonMaterialButton;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class SettingsFoldersController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener, ChatFoldersListener, TdlibSettingsManager.ChatListPositionListener {
  private static final long MAIN_CHAT_FOLDER_ID = Long.MIN_VALUE;
  private static final long ARCHIVE_CHAT_FOLDER_ID = Long.MIN_VALUE + 1;

  private static final int TYPE_CHAT_FOLDER = 0;
  private static final int TYPE_RECOMMENDED_CHAT_FOLDER = 1;

  private final @IdRes int chatFoldersPreviousItemId = ViewCompat.generateViewId();
  private final @IdRes int recommendedChatFoldersPreviousItemId = ViewCompat.generateViewId();

  private final PremiumLockModifier premiumLockModifier = new PremiumLockModifier();
  private final DrawModifier hasInviteLinksModifier = new EndIconModifier(R.drawable.baseline_link_24, ColorId.icon);
  private final DrawModifier disabledFolderModifier = new EndIconModifier(R.drawable.baseline_eye_off_24, ColorId.icon);

  private int chatFolderGroupItemCount, recommendedChatFolderGroupItemCount;
  private boolean recommendedChatFoldersInitialized;

  private @Nullable TdApi.RecommendedChatFolder[] recommendedChatFolders;

  private SettingsAdapter adapter;
  private ItemTouchHelper itemTouchHelper;

  public SettingsFoldersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_chatFolders;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !recommendedChatFoldersInitialized;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChatFolders);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
    if (itemAnimator instanceof SimpleItemAnimator) {
      ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
    }
    itemTouchHelper = RemoveHelper.attach(recyclerView, new ItemTouchHelperCallback());

    TdApi.ChatFolderInfo[] chatFolders = tdlib.chatFolders();
    int mainChatListPosition = tdlib.mainChatListPosition();
    int archiveChatListPosition = tdlib.settings().archiveChatListPosition();
    List<ListItem> chatFolderItemList = buildChatFolderItemList(chatFolders, mainChatListPosition, archiveChatListPosition);
    chatFolderGroupItemCount = chatFolderItemList.size();

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.ChatFoldersSettings));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatFolderStyle, 0, R.string.ChatFoldersAppearance));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_appBadge, 0, R.string.BadgeCounter));
    if (Config.CHAT_FOLDERS_REDESIGN) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_archiveAsFolder, 0, R.string.ArchiveAsFolder));
    }
    // items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_countMutedChats, 0, R.string.CountMutedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatFolders));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, chatFoldersPreviousItemId));
    items.addAll(chatFolderItemList);
    int newFolderIconRes = Config.CHAT_FOLDERS_REDESIGN ? R.drawable.baseline_add_24 : R.drawable.baseline_create_new_folder_24;
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createNewFolder, newFolderIconRes, R.string.CreateNewFolder).setTextColorId(ColorId.inlineText));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    int infoRes = Config.CHAT_FOLDERS_REDESIGN ? R.string.ChatFoldersReorderTip : R.string.ChatFoldersInfo;
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, recommendedChatFoldersPreviousItemId, 0, infoRes));

    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));

    adapter = new SettingsAdapter(this) {
      @SuppressLint("ClickableViewAccessibility")
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        switch (customViewType) {
          case TYPE_CHAT_FOLDER: {
            SettingView settingView = new SettingView(parent.getContext(), tdlib);
            settingView.setType(SettingView.TYPE_INFO_SUPERCOMPACT);
            settingView.setSwapDataAndName();
            settingView.setOnClickListener(SettingsFoldersController.this);
            settingView.setOnLongClickListener(SettingsFoldersController.this);
            if (!Config.CHAT_FOLDERS_REDESIGN) {
              settingView.addToggler();
              settingView.forcePadding(0, Screen.dp(66f));
              settingView.setOnTouchListener(new ChatFolderOnTouchListener());
              settingView.getToggler().setOnClickListener(v -> {
                ListItem item = (ListItem) settingView.getTag();
                if (Config.RESTRICT_HIDING_MAIN_LIST && isMainChatFolder(item) && settingView.getToggler().isEnabled()) {
                  return;
                }
                UI.forceVibrate(v, false);
                boolean enabled = settingView.getToggler().toggle(true);
                settingView.setVisuallyEnabled(enabled, true);
                settingView.setIconColorId(enabled ? ColorId.icon : ColorId.iconLight);
                TdApi.ChatList chatList = getChatList(item);
                tdlib.settings().setChatListEnabled(chatList, enabled);
              });
            }
            addThemeInvalidateListener(settingView);
            return new SettingHolder(settingView);
          }
          case TYPE_RECOMMENDED_CHAT_FOLDER:
            SettingView settingView = new SettingView(parent.getContext(), tdlib);
            settingView.setType(SettingView.TYPE_INFO_COMPACT);
            settingView.setSwapDataAndName();
            settingView.setOnClickListener(SettingsFoldersController.this);
            addThemeInvalidateListener(settingView);

            FrameLayout.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(29f), Screen.dp(28f), Lang.reverseGravity(Gravity.CENTER_VERTICAL));
            params.leftMargin = params.rightMargin = Screen.dp(17f);
            NonMaterialButton button = new NonMaterialButton(parent.getContext()) {
              @Override
              protected void onSizeChanged (int width, int height, int oldWidth, int oldHeight) {
                settingView.forcePadding(0, Math.max(0, width + params.leftMargin + params.rightMargin - Screen.dp(17f)));
              }
            };
            button.setId(R.id.btn_double);
            button.setLayoutParams(params);
            button.setText(R.string.PlusSign);
            button.setOnClickListener(SettingsFoldersController.this);
            settingView.addView(button);

            return new SettingHolder(settingView);
        }
        throw new IllegalArgumentException("customViewType=" + customViewType);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        if (customViewType == TYPE_CHAT_FOLDER) {
          SettingView settingView = (SettingView) holder.itemView;
          settingView.setIcon(item.getIconResource());
          settingView.setName(item.getString());
          settingView.setData(item.getStringValue());
          settingView.setTextColorId(item.getTextColorId(ColorId.NONE));
          settingView.setIgnoreEnabled(true);
          settingView.setEnabled(true);

          boolean isEnabled;
          if (isMainChatFolder(item) || isArchiveChatFolder(item)) {
            isEnabled = tdlib.settings().isChatListEnabled(getChatList(item));
            settingView.setClickable(Config.CHAT_FOLDERS_REDESIGN);
            settingView.setLongClickable(Config.CHAT_FOLDERS_REDESIGN);
            DrawModifier drawModifier = item.getDrawModifier();
            settingView.setDrawModifier(drawModifier);
            if (drawModifier instanceof PremiumLockModifier) {
              settingView.setTooltipLocationProvider((PremiumLockModifier) drawModifier);
            } else {
              settingView.setTooltipLocationProvider(null);
            }
          } else if (isChatFolder(item)) {
            isEnabled = tdlib.settings().isChatFolderEnabled(item.getIntValue());
            settingView.setClickable(true);
            settingView.setLongClickable(true);
            DrawModifier drawModifier;
            TdApi.ChatFolderInfo info = (TdApi.ChatFolderInfo) item.getData();
            if (!isEnabled) {
              drawModifier = disabledFolderModifier;
            } else if (info.hasMyInviteLinks) {
              drawModifier = hasInviteLinksModifier;
            } else {
              drawModifier = item.getDrawModifier();
            }
            settingView.setDrawModifier(drawModifier);
          } else {
            throw new IllegalArgumentException();
          }
          settingView.setVisuallyEnabled(isEnabled, false);
          settingView.setIconColorId(isEnabled ? ColorId.icon : ColorId.iconLight);
          if (!Config.CHAT_FOLDERS_REDESIGN) {
            settingView.getToggler().setRadioEnabled(isEnabled, false);
            if (Config.RESTRICT_HIDING_MAIN_LIST) {
              settingView.getToggler().setVisibility(isMainChatFolder(item) ? View.GONE : View.VISIBLE);
            }
          }
        } else if (customViewType == TYPE_RECOMMENDED_CHAT_FOLDER) {
          SettingView settingView = (SettingView) holder.itemView;
          settingView.setIcon(item.getIconResource());
          settingView.setName(item.getString());
          settingView.setData(item.getStringValue());
          settingView.setTextColorId(item.getTextColorId(ColorId.NONE));
          settingView.setEnabled(true);
          View button = settingView.findViewById(R.id.btn_double);
          button.setEnabled(true);
          button.setTag(item.getData());
        } else {
          throw new IllegalArgumentException("customViewType=" + customViewType);
        }
      }

      @SuppressLint("ClickableViewAccessibility")
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_createNewFolder) {
          boolean canCreateChatFolder = tdlib.canCreateChatFolder();
          view.setIgnoreEnabled(true);
          view.setVisuallyEnabled(canCreateChatFolder, isUpdate);
          view.setIconColorId(canCreateChatFolder ? ColorId.inlineIcon : ColorId.iconLight);
          PremiumLockModifier modifier = (canCreateChatFolder || tdlib.hasPremium()) ? null : premiumLockModifier;
          view.setDrawModifier(modifier);
          view.setTooltipLocationProvider(modifier);
        } else {
          view.setIgnoreEnabled(false);
          view.setEnabledAnimated(true, isUpdate);
          view.setIconColorId(ColorId.NONE);
          view.setDrawModifier(item.getDrawModifier());
          view.setTooltipLocationProvider(null);
        }
        if (item.getId() == R.id.btn_chatFolderStyle) {
          int positionRes;
          if (tdlib.settings().displayFoldersAtTop()) {
            positionRes = R.string.ChatFoldersPositionTop;
          } else {
            positionRes = R.string.ChatFoldersPositionBottom;
          }
          int styleRes;
          switch (tdlib.settings().chatFolderStyle()) {
            case ChatFolderStyle.LABEL_AND_ICON:
              styleRes = R.string.LabelAndIcon;
              break;
            case ChatFolderStyle.ICON_ONLY:
              styleRes = R.string.IconOnly;
              break;
            case ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE:
              styleRes = R.string.IconWithLabelOnActiveFolder;
              break;
            default:
            case ChatFolderStyle.LABEL_ONLY:
              styleRes = R.string.LabelOnly;
              break;
          }
          view.setData(Lang.getString(R.string.format_chatFoldersPositionAndStyle, Lang.getString(positionRes), Lang.getString(styleRes)));
        } else if (item.getId() == R.id.btn_countMutedChats) {
          boolean isEnabled = BitwiseUtils.hasFlag(tdlib.settings().getChatFolderBadgeFlags(), Settings.BADGE_FLAG_MUTED);
          view.getToggler().setRadioEnabled(isEnabled, isUpdate);
        } else if (item.getId() == R.id.btn_archiveAsFolder) {
          boolean showArchiveAsFolder = tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE);
          view.getToggler().setRadioEnabled(showArchiveAsFolder, isUpdate);
        }
      }
    };
    adapter.setItems(items, false);
    recyclerView.addItemDecoration(new ItemDecoration());
    recyclerView.setAdapter(adapter);

    tdlib.listeners().subscribeToChatFoldersUpdates(this);
    tdlib.settings().addChatListPositionListener(this);
    updateRecommendedChatFolders();
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromChatFoldersUpdates(this);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    return true;
  }

  private boolean shouldUpdateRecommendedChatFolders = false;

  @Override
  protected void onFocusStateChanged () {
    if (isFocused()) {
      if (shouldUpdateRecommendedChatFolders) {
        shouldUpdateRecommendedChatFolders = false;
        updateRecommendedChatFolders();
      }
    } else {
      shouldUpdateRecommendedChatFolders = true;
    }
  }

  @Override
  public void onChatFoldersChanged (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition) {
    runOnUiThreadOptional(() -> {
      adapter.updateValuedSettingById(R.id.btn_createNewFolder);
      updateChatFolders(chatFolders, mainChatListPosition, tdlib.settings().archiveChatListPosition());
      if (isFocused()) {
        tdlib.ui().postDelayed(() -> {
          if (!isDestroyed() && isFocused()) {
            updateRecommendedChatFolders();
          }
        }, /* ¯\_(ツ)_/¯ */ 500L);
      }
    });
  }

  @Override
  public void onChatListStateChanged (Tdlib tdlib, TdApi.ChatList chatList, boolean isEnabled) {
    runOnUiThreadOptional(() -> {
      int startIndex = indexOfFirstChatFolder();
      if (startIndex == RecyclerView.NO_POSITION) return;
      int index = adapter.indexOfView((item) -> isChatFolder(item) && Td.equalsTo(chatList, getChatList(item)), startIndex, false);
      if (index != RecyclerView.NO_POSITION) {
        adapter.notifyItemChanged(index);
      }
    });
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_createNewFolder) {
      if (tdlib.canCreateChatFolder()) {
        navigateTo(EditChatFolderController.newFolder(context, tdlib));
      } else {
        showChatFolderLimitReached(v);
      }
    } else if (v.getId() == R.id.chatFolder) {
      ListItem item = (ListItem) v.getTag();
      if (isMainChatFolder(item) || isArchiveChatFolder(item)) {
        if (Config.CHAT_FOLDERS_REDESIGN) {
          showTooltip(v, Lang.getString(R.string.HoldAndDragToReorder), View::getDrawingRect);
        }
        return;
      }
      editChatFolder((TdApi.ChatFolderInfo) item.getData());
    } else if (v.getId() == R.id.recommendedChatFolder) {
      if (tdlib.canCreateChatFolder()) {
        ListItem item = (ListItem) v.getTag();
        TdApi.ChatFolder chatFolder = (TdApi.ChatFolder) item.getData();
        chatFolder.icon = tdlib.chatFolderIcon(chatFolder);
        navigateTo(EditChatFolderController.newFolder(context, tdlib, chatFolder));
      } else {
        showChatFolderLimitReached(v);
      }
    } else if (v.getId() == R.id.btn_double) {
      Object tag = v.getTag();
      if (tag instanceof TdApi.ChatFolder) {
        if (tdlib.canCreateChatFolder()) {
          v.setEnabled(false);
          TdApi.ChatFolder chatFolder = (TdApi.ChatFolder) tag;
          WeakReference<View> viewRef = new WeakReference<>(v);
          createChatFolder(chatFolder, (ok) -> {
            if (ok) {
              removeRecommendedChatFolder(chatFolder);
            } else {
              View view = viewRef.get();
              if (view != null && view.getTag() == tag) {
                view.setEnabled(true);
              }
            }
          });
        } else {
          showChatFolderLimitReached(v);
        }
      }
    } else if (v.getId() == R.id.btn_chatFolderStyle) {
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      ListItem[] items = new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_displayFoldersAtTop, 0, R.string.DisplayFoldersAtTheTop, tdlib.settings().displayFoldersAtTop()),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM).setTextColorId(ColorId.background),
        new ListItem(ListItem.TYPE_SHADOW_TOP).setTextColorId(ColorId.background),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_labelOnly, 0, R.string.LabelOnly, R.id.btn_chatFolderStyle, chatFolderStyle == ChatFolderStyle.LABEL_ONLY),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_labelAndIcon, 0, R.string.LabelAndIcon, R.id.btn_chatFolderStyle, chatFolderStyle == ChatFolderStyle.LABEL_AND_ICON),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_iconOnly, 0, R.string.IconOnly, R.id.btn_chatFolderStyle, chatFolderStyle == ChatFolderStyle.ICON_ONLY),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_iconWithLabelOnActiveFolder, 0, R.string.IconWithLabelOnActiveFolder, R.id.btn_chatFolderStyle, chatFolderStyle == ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE)
      };
      SettingsWrapBuilder settings = new SettingsWrapBuilder(R.id.btn_chatFolderStyle)
        .setRawItems(items)
        .setNeedSeparators(false)
        .setIntDelegate((id, result) -> {
          int selection = result.get(R.id.btn_chatFolderStyle);
          int style;
          if (selection == R.id.btn_iconOnly) {
            style = ChatFolderStyle.ICON_ONLY;
          } else if (selection == R.id.btn_labelAndIcon) {
            style = ChatFolderStyle.LABEL_AND_ICON;
          } else if (selection == R.id.btn_iconWithLabelOnActiveFolder) {
            style = ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE;
          } else {
            style = ChatFolderStyle.LABEL_ONLY;
          }
          boolean displayFoldersAtTop = result.get(R.id.btn_displayFoldersAtTop) != 0;
          tdlib.settings().setChatFolderStyle(style);
          tdlib.settings().setDisplayFoldersAtTop(displayFoldersAtTop);
          adapter.updateValuedSettingById(R.id.btn_chatFolderStyle);
        });
      showSettings(settings);
    } else if (v.getId() == R.id.btn_countMutedChats) {
      boolean countMuted = adapter.toggleView(v);
      int badgeFlags = BitwiseUtils.setFlag(tdlib.settings().getChatFolderBadgeFlags(), Settings.BADGE_FLAG_MUTED, countMuted);
      tdlib.settings().setChatFolderBadgeFlags(badgeFlags);
    } else if (v.getId() == R.id.btn_appBadge) {
      SettingsNotificationController c = new SettingsNotificationController(context, tdlib);
      c.setArguments(new SettingsNotificationController.Args(SettingsNotificationController.Section.APP_BADGE, 0));
      navigateTo(c);
    } else if (v.getId() == R.id.btn_archiveAsFolder) {
      boolean showArchiveAsFolder = adapter.toggleView(v);
      tdlib.settings().setChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE, showArchiveAsFolder);
      updateChatFolders();
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.chatFolder) {
      if (Config.CHAT_FOLDERS_REDESIGN) {
        startDrag(getRecyclerView().getChildViewHolder(v));
        return true;
      }
      ListItem item = (ListItem) v.getTag();
      if (isMainChatFolder(item) || isArchiveChatFolder(item)) {
        return false;
      }
      showChatFolderOptions((TdApi.ChatFolderInfo) item.getData());
      return true;
    }
    return false;
  }

  private void startDrag (RecyclerView.ViewHolder viewHolder) {
    if (viewHolder == null)
      return;
    ListItem listItem = (ListItem) viewHolder.itemView.getTag();
    if (isMainChatFolder(listItem) && !tdlib.hasPremium()) {
      UI.forceVibrateError(viewHolder.itemView);
      CharSequence markdown = Lang.getString(R.string.PremiumRequiredMoveFolder, Lang.getString(R.string.CategoryMain));
      context()
        .tooltipManager()
        .builder(viewHolder.itemView)
        .icon(R.drawable.dotvhs_baseline_folders_reorder_24)
        .controller(this)
        .show(tdlib, Strings.buildMarkdown(this, markdown))
        .hideDelayed();
      return;
    }
    itemTouchHelper.startDrag(viewHolder);
  }

  private void showChatFolderLimitReached (View view) {
    UI.forceVibrateError(view);
    if (tdlib.hasPremium()) {
      showTooltip(view, Lang.getMarkdownString(this, R.string.ChatFolderLimitReached, tdlib.chatFolderCountMax()));
    } else {
      tdlib.ui().showPremiumLimitInfo(this, view, TdlibUi.PremiumLimit.CHAT_FOLDER_COUNT);
    }
  }

  private void showTooltip (View view, CharSequence text) {
    showTooltip(view, text, null);
  }

  private void showTooltip (View view, CharSequence text, @Nullable TooltipOverlayView.LocationProvider locationProvider) {
    context()
      .tooltipManager()
      .builder(view)
      .controller(this)
      .locate(locationProvider)
      .show(tdlib, text)
      .hideDelayed(3500, TimeUnit.MILLISECONDS);
  }

  private void showChatFolderOptions (TdApi.ChatFolderInfo chatFolderInfo) {
    Options options = new Options.Builder()
      .info(chatFolderInfo.title)
      .item(new OptionItem(R.id.btn_edit, Lang.getString(R.string.EditFolder), OptionColor.NORMAL, R.drawable.baseline_edit_24))
      .item(new OptionItem(R.id.btn_delete, Lang.getString(R.string.RemoveFolder), OptionColor.RED, R.drawable.baseline_delete_24))
      .build();
    showOptions(options, (optionItemView, id) -> {
      if (id == R.id.btn_edit) {
        editChatFolder(chatFolderInfo);
      } else if (id == R.id.btn_delete) {
        showRemoveFolderConfirm(chatFolderInfo);
      }
      return true;
    });
  }

  private void showRemoveFolderConfirm (TdApi.ChatFolderInfo info) {
    tdlib.ui().showDeleteChatFolderConfirm(this, info.hasMyInviteLinks, () -> {
      // TODO(nikita-toropov) leave chats suggestion
      deleteChatFolder(info.id);
    });
  }

  private void editChatFolder (TdApi.ChatFolderInfo chatFolderInfo) {
    tdlib.send(new TdApi.GetChatFolder(chatFolderInfo.id), (chatFolder, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        EditChatFolderController controller = new EditChatFolderController(context, tdlib);
        controller.setArguments(new EditChatFolderController.Arguments(chatFolderInfo.id, chatFolder));
        navigateTo(controller);
      }
    }));
  }

  private void createChatFolder (TdApi.ChatFolder chatFolder, RunnableBool after) {
    tdlib.send(new TdApi.CreateChatFolder(chatFolder), (chatFolderInfo, error) -> runOnUiThreadOptional(() -> {
      after.runWithBool(error == null);
      if (error != null) {
        UI.showError(error);
      }
    }));
  }

  private void deleteChatFolder (int chatFolderId) {
    int position = -1;
    TdApi.ChatFolderInfo[] chatFolders = tdlib.chatFolders();
    for (int index = 0; index < chatFolders.length; index++) {
      TdApi.ChatFolderInfo chatFolder = chatFolders[index];
      if (chatFolder.id == chatFolderId) {
        position = index;
        break;
      }
    }
    if (position != -1) {
      int archiveChatListPosition = tdlib.settings().archiveChatListPosition();
      if (position >= tdlib.mainChatListPosition()) position++;
      if (position >= archiveChatListPosition) position++;
      boolean affectsArchiveChatListPosition = position < archiveChatListPosition && archiveChatListPosition < chatFolders.length + 2;
      tdlib.send(new TdApi.DeleteChatFolder(chatFolderId, null), tdlib.typedOkHandler(() -> {
        if (affectsArchiveChatListPosition && archiveChatListPosition == tdlib.settings().archiveChatListPosition()) {
          tdlib.settings().setArchiveChatListPosition(archiveChatListPosition - 1);
          if (!isDestroyed()) {
            updateChatFolders();
          }
        }
      }));
    }
  }

  private void reorderChatFolders () {
    int firstIndex = indexOfFirstChatFolder();
    int lastIndex = indexOfLastChatFolder();
    if (firstIndex == RecyclerView.NO_POSITION || lastIndex == RecyclerView.NO_POSITION)
      return;
    int mainChatListPosition = tdlib.mainChatListPosition();
    int archiveChatListPosition = tdlib.settings().archiveChatListPosition();
    IntList chatFoldersIds = new IntList(tdlib.chatFolderCount());
    int folderPosition = 0;
    for (int index = firstIndex; index <= lastIndex; index++) {
      ListItem item = adapter.getItem(index);
      if (item == null) {
        updateChatFolders();
        return;
      }
      if (isChatFolder(item)) {
        if (isMainChatFolder(item)) {
          mainChatListPosition = folderPosition;
        } else if (isArchiveChatFolder(item)) {
          archiveChatListPosition = folderPosition;
        } else {
          chatFoldersIds.append(item.getIntValue());
        }
        folderPosition++;
      }
    }
    if (mainChatListPosition > archiveChatListPosition) {
      mainChatListPosition--;
    }
    if (archiveChatListPosition > chatFoldersIds.size()) {
      archiveChatListPosition = Integer.MAX_VALUE;
    }
    if (mainChatListPosition != 0 && !tdlib.hasPremium()) {
      updateChatFolders();
      return;
    }
    tdlib.settings().setArchiveChatListPosition(archiveChatListPosition);
    if (chatFoldersIds.isEmpty()) {
      return;
    }
    tdlib.send(new TdApi.ReorderChatFolders(chatFoldersIds.get(), mainChatListPosition), (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        updateChatFolders();
      }
    }));
  }

  private boolean isChatFolder (ListItem item) {
    return item.getId() == R.id.chatFolder;
  }

  private TdApi.ChatList getChatList (ListItem item) {
    if (!isChatFolder(item))
      throw new IllegalArgumentException();
    if (isMainChatFolder(item)) {
      return ChatPosition.CHAT_LIST_MAIN;
    } else if (isArchiveChatFolder(item)) {
      return ChatPosition.CHAT_LIST_ARCHIVE;
    } else {
      return new TdApi.ChatListFolder(item.getIntValue());
    }
  }

  private boolean isMainChatFolder (ListItem item) {
    return isChatFolder(item) && item.getLongId() == MAIN_CHAT_FOLDER_ID;
  }

  private boolean isArchiveChatFolder (ListItem item) {
    return isChatFolder(item) && item.getLongId() == ARCHIVE_CHAT_FOLDER_ID;
  }

  private boolean canMoveChatFolder (ListItem item) {
    return isChatFolder(item) && (tdlib.hasPremium() || !isMainChatFolder(item));
  }

  private int indexOfFirstChatFolder () {
    return indexOfChatFolderGroup();
  }

  private int indexOfLastChatFolder () {
    int index = indexOfChatFolderGroup();
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + chatFolderGroupItemCount - 1;
  }

  private int indexOfChatFolderGroup () {
    int index = adapter.indexOfViewById(chatFoldersPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private int indexOfRecommendedChatFolderGroup () {
    int index = adapter.indexOfViewById(recommendedChatFoldersPreviousItemId);
    return index == RecyclerView.NO_POSITION ? RecyclerView.NO_POSITION : index + 1;
  }

  private List<ListItem> buildChatFolderItemList (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition, int archiveChatListPosition) {
    boolean showArchiveAsFolder = !Config.CHAT_FOLDERS_REDESIGN || tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE);
    int chatFolderCount = chatFolders.length + (showArchiveAsFolder ? 2 : 1); /* All Chats, Archived */
    int chatFolderIndex = 0;
    mainChatListPosition = MathUtils.clamp(mainChatListPosition, 0, chatFolders.length);
    if (showArchiveAsFolder) {
      archiveChatListPosition = MathUtils.clamp(archiveChatListPosition, 0, chatFolderCount - 1);
      if (mainChatListPosition >= archiveChatListPosition) {
        mainChatListPosition++;
      }
    } else {
      archiveChatListPosition = -1;
    }
    List<ListItem> itemList = new ArrayList<>();
    for (int position = 0; position < chatFolderCount; position++) {
      if (position == mainChatListPosition) {
        itemList.add(mainChatFolderItem());
      } else if (position == archiveChatListPosition) {
        itemList.add(archiveChatFolderItem());
      } else if (chatFolderIndex < chatFolders.length) {
        TdApi.ChatFolderInfo chatFolder = chatFolders[chatFolderIndex++];
        itemList.add(chatFolderItem(chatFolder));
      } else {
        throw new IllegalStateException(String.format("position=%d mainChatListPosition=%d archiveChatListPosition=%d chatFolderIndex=%d chatFolderCount=%d", position, mainChatListPosition, archiveChatListPosition, chatFolderIndex, chatFolderCount));
      }
    }
    return itemList;
  }

  private static final int RECOMMENDED_FOLDERS_HEADER_ID = ViewCompat.generateViewId();
  private static final int RECOMMENDED_FOLDERS_SHADOW_TOP_ID = ViewCompat.generateViewId();
  private static final int RECOMMENDED_FOLDERS_SHADOW_BOTTOM_ID = ViewCompat.generateViewId();

  private List<ListItem> buildRecommendedChatFolderItemList (TdApi.RecommendedChatFolder[] recommendedChatFolders) {
    if (recommendedChatFolders.length == 0) {
      return Collections.emptyList();
    }
    List<ListItem> itemList = new ArrayList<>(recommendedChatFolders.length * 2 - 1 + 3);
    itemList.add(new ListItem(ListItem.TYPE_HEADER, RECOMMENDED_FOLDERS_HEADER_ID, 0, R.string.RecommendedFolders));
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_TOP, RECOMMENDED_FOLDERS_SHADOW_TOP_ID));
    for (int index = 0; index < recommendedChatFolders.length; index++) {
      TdApi.RecommendedChatFolder recommendedChatFolder = recommendedChatFolders[index];
      if (index > 0) {
        itemList.add(new ListItem(ListItem.TYPE_SEPARATOR).setStringValue(recommendedChatFolder.folder.title));
      }
      itemList.add(recommendedChatFolderItem(recommendedChatFolder));
    }
    itemList.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, RECOMMENDED_FOLDERS_SHADOW_BOTTOM_ID));
    return itemList;
  }

  private ListItem mainChatFolderItem () {
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM - TYPE_CHAT_FOLDER, R.id.chatFolder);
    item.setString(R.string.CategoryMain);
    item.setLongId(MAIN_CHAT_FOLDER_ID);
    if (Config.CHAT_FOLDERS_REDESIGN) {
      item.setIconRes(R.drawable.baseline_forum_24);
      item.setDrawModifier(tdlib.hasPremium() ? null : premiumLockModifier);
    } else {
      item.setIconRes(tdlib.hasPremium() ? R.drawable.baseline_drag_handle_24 : R.drawable.deproko_baseline_lock_24);
      item.setDrawModifier(new FolderBadge(Lang.getString(R.string.MainListBadge)));
    }
    return item;
  }

  private ListItem archiveChatFolderItem () {
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM - TYPE_CHAT_FOLDER, R.id.chatFolder);
    item.setString(R.string.CategoryArchive);
    item.setLongId(ARCHIVE_CHAT_FOLDER_ID);
    if (Config.CHAT_FOLDERS_REDESIGN) {
      item.setIconRes(R.drawable.baseline_archive_24);
      item.setStringValue(Lang.getString(R.string.LocalFolder));
    } else {
      item.setIconRes(R.drawable.baseline_drag_handle_24);
      item.setDrawModifier(new FolderBadge(Lang.getString(R.string.LocalFolderBadge)));
    }
    return item;
  }

  private ListItem chatFolderItem (TdApi.ChatFolderInfo chatFolderInfo) {
    int iconRes;
    if (Config.CHAT_FOLDERS_REDESIGN) {
      iconRes = TD.findFolderIcon(chatFolderInfo.icon, R.drawable.baseline_folder_24);
    } else {
      iconRes = R.drawable.baseline_drag_handle_24;
    }
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM - TYPE_CHAT_FOLDER, R.id.chatFolder, iconRes, Emoji.instance().replaceEmoji(chatFolderInfo.title));
    item.setIntValue(chatFolderInfo.id);
    item.setLongId(chatFolderInfo.id);
    item.setData(chatFolderInfo);
    return item;
  }

  private ListItem recommendedChatFolderItem (TdApi.RecommendedChatFolder recommendedChatFolder) {
    ListItem item = new ListItem(ListItem.TYPE_CUSTOM - TYPE_RECOMMENDED_CHAT_FOLDER, R.id.recommendedChatFolder);
    item.setData(recommendedChatFolder.folder);
    item.setString(recommendedChatFolder.folder.title);
    item.setStringValue(recommendedChatFolder.description);
    item.setIconRes(tdlib.chatFolderIconDrawable(recommendedChatFolder.folder, R.drawable.baseline_folder_24));
    return item;
  }

  private void updateChatFolders () {
    updateChatFolders(tdlib.chatFolders(), tdlib.mainChatListPosition(), tdlib.settings().archiveChatListPosition());
  }

  private void updateChatFolders (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition, int archiveChatListPosition) {
    int fromIndex = indexOfChatFolderGroup();
    if (fromIndex == RecyclerView.NO_POSITION)
      return;
    List<ListItem> subList = adapter.getItems().subList(fromIndex, fromIndex + chatFolderGroupItemCount);
    List<ListItem> newList = buildChatFolderItemList(chatFolders, mainChatListPosition, archiveChatListPosition);
    chatFolderGroupItemCount = newList.size();
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(chatFoldersDiff(subList, newList));
    subList.clear();
    subList.addAll(newList);
    diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, fromIndex));
  }

  private void updateRecommendedChatFolders () {
    tdlib.send(new TdApi.GetRecommendedChatFolders(), (recommendedChatFolders, error) -> {
      runOnUiThreadOptional(() -> {
        if (recommendedChatFolders != null) {
          updateRecommendedChatFolders(recommendedChatFolders.chatFolders);
        }
        if (!recommendedChatFoldersInitialized) {
          recommendedChatFoldersInitialized = true;
          executeScheduledAnimation();
        }
      });
    });
  }

  private void updateRecommendedChatFolders (TdApi.RecommendedChatFolder[] chatFolders) {
    int fromIndex = indexOfRecommendedChatFolderGroup();
    if (fromIndex == RecyclerView.NO_POSITION)
      return;
    List<ListItem> subList = adapter.getItems().subList(fromIndex, fromIndex + recommendedChatFolderGroupItemCount);
    List<ListItem> newList = buildRecommendedChatFolderItemList(chatFolders);
    if (subList.isEmpty() && newList.isEmpty()) {
      return;
    }
    recommendedChatFolders = chatFolders;
    recommendedChatFolderGroupItemCount = newList.size();
    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(recommendedChatFoldersDiff(subList, newList));
    subList.clear();
    subList.addAll(newList);
    diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, fromIndex));
  }

  private void removeRecommendedChatFolder (TdApi.ChatFolder chatFolder) {
    if (recommendedChatFolders == null || recommendedChatFolders.length == 0)
      return;
    int indexToRemove = -1;
    for (int i = 0; i < recommendedChatFolders.length; i++) {
      if (chatFolder == recommendedChatFolders[i].folder) {
        indexToRemove = i;
        break;
      }
    }
    if (indexToRemove != -1) {
      TdApi.RecommendedChatFolder[] chatFolders = new TdApi.RecommendedChatFolder[recommendedChatFolders.length - 1];
      if (chatFolders.length > 0) {
        ArrayUtils.removeElement(recommendedChatFolders, indexToRemove, chatFolders);
      }
      updateRecommendedChatFolders(chatFolders);
    }
  }

  private static DiffUtil.Callback chatFoldersDiff (List<ListItem> oldList, List<ListItem> newList) {
    return new ListItemDiffUtilCallback(oldList, newList) {
      @Override
      public boolean areItemsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getViewType() != newItem.getViewType() || oldItem.getId() != newItem.getId()) {
          return false;
        }
        if (oldItem.getId() == R.id.chatFolder) {
          return oldItem.getLongId() == newItem.getLongId();
        }
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean areContentsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getId() == R.id.chatFolder) {
          CharSequence a = oldItem.getString();
          CharSequence b = newItem.getString();
          return ObjectUtils.equals(a, b) && oldItem.getIconResource() == newItem.getIconResource();
        }
        throw new UnsupportedOperationException();
      }
    };
  }

  private static DiffUtil.Callback recommendedChatFoldersDiff (List<ListItem> oldList, List<ListItem> newList) {
    return new ListItemDiffUtilCallback(oldList, newList) {
      @Override
      public boolean areItemsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getViewType() != newItem.getViewType() || oldItem.getId() != newItem.getId()) {
          return false;
        }
        if (oldItem.getId() == R.id.recommendedChatFolder) {
          CharSequence a = oldItem.getString();
          CharSequence b = newItem.getString();
          return ObjectUtils.equals(a, b);
        }
        if (oldItem.getViewType() == ListItem.TYPE_SEPARATOR) {
          return ObjectUtils.equals(oldItem.getStringValue(), newItem.getStringValue());
        }
        return true;
      }

      @Override
      public boolean areContentsTheSame (ListItem oldItem, ListItem newItem) {
        if (oldItem.getId() == R.id.recommendedChatFolder) {
          CharSequence a1 = oldItem.getString();
          CharSequence b1 = newItem.getString();
          if (oldItem.getIconResource() != newItem.getIconResource() ||
            !ObjectUtils.equals(a1, b1)) return false;
          String a = oldItem.getStringValue();
          String b = newItem.getStringValue();
          return ObjectUtils.equals(a, b);
        }
        if (oldItem.getViewType() == ListItem.TYPE_SEPARATOR) {
          return true;
        }
        if (oldItem.getViewType() == ListItem.TYPE_HEADER) {
          CharSequence a = oldItem.getString();
          CharSequence b = newItem.getString();
          return ObjectUtils.equals(a, b);
        }
        return true;
      }
    };
  }

  private class ItemTouchHelperCallback implements RemoveHelper.ExtendedCallback {
    @Override
    public boolean isLongPressDragEnabled () {
      return false;
    }

    @Override
    public int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      return isChatFolder(item) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
    }

    @Override
    public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      return isChatFolder(item) && !isMainChatFolder(item) && !isArchiveChatFolder(item);
    }

    @Override
    public void onRemove (RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      showRemoveFolderConfirm((TdApi.ChatFolderInfo) item.getData());
    }

    @Override
    public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
      int sourcePosition = source.getAbsoluteAdapterPosition();
      int targetPosition = target.getAbsoluteAdapterPosition();
      if (sourcePosition == RecyclerView.NO_POSITION || targetPosition == RecyclerView.NO_POSITION) {
        return false;
      }
      int firstChatFolderIndex = indexOfFirstChatFolder();
      int lastChatFolderIndex = indexOfLastChatFolder();
      if (firstChatFolderIndex == RecyclerView.NO_POSITION || lastChatFolderIndex == RecyclerView.NO_POSITION) {
        return false;
      }
      if (targetPosition < firstChatFolderIndex || targetPosition > lastChatFolderIndex) {
        return false;
      }
      adapter.moveItem(sourcePosition, targetPosition, /* notify */ true);
      return true;
    }

    @Override
    public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
      ListItem sourceItem = (ListItem) source.itemView.getTag();
      ListItem targetItem = (ListItem) target.itemView.getTag();
      return isChatFolder(sourceItem) && isChatFolder(targetItem) && canMoveChatFolder(targetItem);
    }

    @Override
    public void onCompleteMovement (int fromPosition, int toPosition) {
      reorderChatFolders();
    }
  }

  private class ChatFolderOnTouchListener implements View.OnTouchListener {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch (View view, MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
        float paddingStart = ((SettingView) view).getMeasuredNameStart();
        boolean shouldStartDrag = Lang.rtl() ? event.getX() > view.getWidth() - paddingStart : event.getX() < paddingStart;
        if (shouldStartDrag) {
          startDrag(getRecyclerView().getChildViewHolder(view));
        }
      }
      return false;
    }
  }

  private static class ItemDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      if (!Config.CHAT_FOLDERS_REDESIGN) return;
      boolean isRtl = Lang.rtl();
      int separatorColor = Theme.separatorColor();
      int separatorHeight = Math.max(1, Screen.dp(.5f));
      Paint sepratorPaint = Paints.fillingPaint(separatorColor);
      int startOffset = Screen.dp(72f);
      int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (!isChatFolder(child) || isDragging(child)) {
          continue;
        }
        int layoutPosition = parent.getChildLayoutPosition(child);
        if (layoutPosition == RecyclerView.NO_POSITION) {
          continue;
        }
        RecyclerView.ViewHolder next = parent.findViewHolderForLayoutPosition(layoutPosition + 1);
        if (next == null || isChatFolder(next.itemView) && isDragging(next.itemView)) {
          continue;
        }
        int bottom = child.getBottom();
        int top = bottom - separatorHeight;
        if (isRtl) {
          c.drawRect(0, top, child.getWidth() - startOffset, bottom, sepratorPaint);
        } else {
          c.drawRect(startOffset, top, child.getWidth(), bottom, sepratorPaint);
        }
      }
    }

    private boolean isChatFolder (View view) {
      return view.getId() == R.id.chatFolder;
    }

    private boolean isDragging (View view) {
      return view.getTranslationY() != 0f;
    }
  }

  private static class FolderBadge implements DrawModifier {
    private final Text text;

    public FolderBadge (String badgeText) {
      text = new Text.Builder(badgeText, Integer.MAX_VALUE, Paints.robotoStyleProvider(12f), Theme::textDecentColor)
        .allBold()
        .singleLine()
        .noClickable()
        .ignoreNewLines()
        .ignoreContinuousNewLines()
        .noSpacing()
        .build();
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      SettingView settingView = (SettingView) view;
      float centerY = view.getHeight() / 2 + Screen.dp(.8f);
      int startX = (int) (settingView.getMeasuredNameStart() + settingView.getMeasuredNameWidth()) + Screen.dp(8f) + Screen.dp(6f);
      int startY = Math.round(centerY) - text.getLineCenterY();
      float alpha = 0.7f + settingView.getVisuallyEnabledFactor() * 0.3f;
      text.draw(c, startX, startY, null, alpha);

      int strokeColor = ColorUtils.alphaColor(alpha, Theme.textDecentColor());
      Paint.FontMetricsInt fontMetrics = Paints.getFontMetricsInt(Paints.getTextPaint16());
      float height = fontMetrics.descent - fontMetrics.ascent - Screen.dp(2f);

      RectF rect = Paints.getRectF(startX - Screen.dp(6f), centerY - height / 2f, startX + text.getWidth() + Screen.dp(6f), centerY + height / 2f);
      float radius = Screen.dp(4f);
      c.drawRoundRect(rect, radius, radius, Paints.strokeSmallPaint(strokeColor));
    }

    @Override
    public int getWidth () {
      return Screen.dp(8f) + text.getWidth() + Screen.dp(6f) * 2;
    }
  }
}