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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.AdapterSubListUpdateCallback;
import org.thunderdog.challegram.util.ListItemDiffUtilCallback;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class EditChatFolderController extends RecyclerViewController<EditChatFolderController.Arguments> implements View.OnClickListener, SettingsAdapter.TextChangeListener, SelectChatsController.Delegate {

  private static final int NO_CHAT_FOLDER_ID = 0;
  private static final int COLLAPSED_CHAT_COUNT = 3;
  private static final int MAX_CHAT_FOLDER_TITLE_LENGTH = 12;
  private static final TdApi.ChatFolder EMPTY_CHAT_FOLDER = TD.newChatFolder();
  private static final ArrayList<ListItem> TEMP_ITEM_LIST = new ArrayList<>(0);

  public static class Arguments {
    private final int chatFolderId;
    private final String chatFolderName;
    private final @Nullable TdApi.ChatFolder chatFolder;

    public static Arguments newFolder () {
      return new Arguments(NO_CHAT_FOLDER_ID, (TdApi.ChatFolder) null);
    }

    public static Arguments newFolder (@Nullable TdApi.ChatFolder chatFolder) {
      return new Arguments(NO_CHAT_FOLDER_ID, chatFolder);
    }

    public Arguments (TdApi.ChatFolderInfo chatFolderInfo) {
      this(chatFolderInfo.id, chatFolderInfo.title);
    }

    public Arguments (int chatFolderId, @Nullable TdApi.ChatFolder chatFolder) {
      this(chatFolderId, chatFolder != null ? chatFolder.title : "", chatFolder);
    }

    public Arguments (int chatFolderId, String chatFolderName) {
      this(chatFolderId, chatFolderName, null);
    }

    private Arguments (int chatFolderId, String chatFolderName, @Nullable TdApi.ChatFolder chatFolder) {
      this.chatFolder = chatFolder;
      this.chatFolderId = chatFolderId;
      this.chatFolderName = chatFolderName;
    }
  }

  public static EditChatFolderController newFolder (Context context, Tdlib tdlib) {
    EditChatFolderController controller = new EditChatFolderController(context, tdlib);
    controller.setArguments(Arguments.newFolder());
    return controller;
  }

  public static EditChatFolderController newFolder (Context context, Tdlib tdlib, TdApi.ChatFolder chatFolder) {
    EditChatFolderController controller = new EditChatFolderController(context, tdlib);
    controller.setArguments(Arguments.newFolder(chatFolder));
    return controller;
  }

  @SuppressWarnings("FieldCanBeLocal")
  private final @IdRes int includedChatsPreviousItemId = R.id.btn_folderIncludeChats;
  @SuppressWarnings("FieldCanBeLocal")
  private final @IdRes int excludedChatsPreviousItemId = R.id.btn_folderExcludeChats;
  private final @IdRes int includedChatsNextItemId = ViewCompat.generateViewId();
  private final @IdRes int excludedChatsNextItemId = ViewCompat.generateViewId();

  private boolean showAllIncludedChats;
  private boolean showAllExcludedChats;

  private SettingsAdapter adapter;
  private ListItem input;

  private int chatFolderId;
  private TdApi.ChatFolder originChatFolder;
  private TdApi.ChatFolder editedChatFolder;

  public EditChatFolderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return originChatFolder == null && chatFolderId != NO_CHAT_FOLDER_ID;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 500l;
  }

  @Override
  public int getId () {
    return R.id.controller_editChatFolders;
  }

  @Override
  public CharSequence getName () {
    Arguments arguments = getArgumentsStrict();
    return chatFolderId != NO_CHAT_FOLDER_ID ? arguments.chatFolderName : Lang.getString(R.string.NewFolder);
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatFolderId = args.chatFolderId;
    this.originChatFolder = args.chatFolder;
    this.editedChatFolder = args.chatFolder != null ? TD.copyOf(args.chatFolder) : TD.newChatFolder(args.chatFolderName);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderName));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(input = new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.input).setStringValue(editedChatFolder.title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderIncludedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderIncludeChats, R.drawable.baseline_add_24, R.string.FolderActionIncludeChats).setTextColorId(ColorId.inlineText));
    fillIncludedChats(editedChatFolder, items);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, includedChatsNextItemId));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.FolderIncludedChatsInfo)));

    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderExcludedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderExcludeChats, R.drawable.baseline_add_24, R.string.FolderActionExcludeChats).setTextColorId(ColorId.inlineText));
    fillExcludedChats(editedChatFolder, items);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, excludedChatsNextItemId));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.FolderExcludedChatsInfo)));

    if (chatFolderId != NO_CHAT_FOLDER_ID) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_removeFolder, R.drawable.baseline_delete_forever_24, R.string.RemoveFolder).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));
    }

    adapter = new Adapter(this);
    adapter.setLockFocusOn(this, /* showAlways */ StringUtils.isEmpty(editedChatFolder.title));
    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);
    CustomItemAnimator itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    itemAnimator.setSupportsChangeAnimations(false);
    recyclerView.setItemAnimator(itemAnimator);
    recyclerView.setAdapter(adapter);
    RemoveHelper.attach(recyclerView, new RemoveHelperCallback());

    if (originChatFolder == null && chatFolderId != NO_CHAT_FOLDER_ID) {
      loadChatFolder();
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    Arguments arguments = getArgumentsStrict();
    outState.putInt(keyPrefix + "_chatFolderId", arguments.chatFolderId);
    outState.putString(keyPrefix + "_chatFolderName", arguments.chatFolderName);
    TD.saveChatFolder(outState, keyPrefix + "_originChatFolder", originChatFolder);
    TD.saveChatFolder(outState, keyPrefix + "_editedChatFolder", editedChatFolder);
    outState.putBoolean(keyPrefix + "_showAllIncludedChats", showAllIncludedChats);
    outState.putBoolean(keyPrefix + "_showAllExcludedChats", showAllExcludedChats);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    int chatFolderId = in.getInt(keyPrefix + "_chatFolderId", NO_CHAT_FOLDER_ID);
    String chatFolderName = in.getString(keyPrefix + "_chatFolderName");
    TdApi.ChatFolder originChatFolder = TD.restoreChatFolder(in, keyPrefix + "_originChatFolder");
    TdApi.ChatFolder editedChatFolder = TD.restoreChatFolder(in, keyPrefix + "_editedChatFolder");
    if (chatFolderName != null && editedChatFolder != null) {
      super.setArguments(new Arguments(chatFolderId, chatFolderName, originChatFolder));
      this.chatFolderId = chatFolderId;
      this.originChatFolder = originChatFolder;
      this.editedChatFolder = editedChatFolder;
      this.showAllIncludedChats = in.getBoolean(keyPrefix + "_showAllIncludedChats");
      this.showAllExcludedChats = in.getBoolean(keyPrefix + "_showAllExcludedChats");
      return true;
    }
    return false;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_done) {
      header.addDoneButton(menu, this).setVisibility(canSaveChanges() ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) {
      saveChanges();
    }
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_folderIncludeChats) {
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.includedChats(this, chatFolderId, editedChatFolder));
      navigateTo(selectChats);
    } else if (id == R.id.btn_folderExcludeChats) {
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.excludedChats(this, chatFolderId, editedChatFolder));
      navigateTo(selectChats);
    } else if (id == R.id.btn_showAdvanced) {
      ListItem item = (ListItem) v.getTag();
      if (item.getBoolValue()) {
        if (!showAllIncludedChats) {
          showAllIncludedChats = true;
          updateIncludedChats();
        }
      } else {
        if (!showAllExcludedChats) {
          showAllExcludedChats = true;
          updateExcludedChats();
        }
      }
    } else if (id == R.id.btn_removeFolder) {
      showRemoveFolderConfirm();
    } else if (id == R.id.chat || ArrayUtils.contains(TD.CHAT_TYPES, id)) {
      int position = getRecyclerView().getChildAdapterPosition(v);
      ListItem item = (ListItem) v.getTag();
      showRemoveConditionConfirm(position, item);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasChanges()) {
      showUnsavedChangesPromptBeforeLeaving(/* onConfirm */ null);
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    adapter.setLockFocusOn(this, false);
    setLockFocusView(getLockFocusView(), false);
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    updateMenuButton();
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    editedChatFolder.title = text;
    updateMenuButton();
  }

  private void fillIncludedChats (TdApi.ChatFolder chatFolder, List<ListItem> outList) {
    int chatTypeCount = TD.countIncludedChatTypes(chatFolder);
    int chatCount = chatFolder.pinnedChatIds.length + chatFolder.includedChatIds.length;
    int visibleChatCount = showAllIncludedChats || (chatCount <= COLLAPSED_CHAT_COUNT + 1) ? chatCount : COLLAPSED_CHAT_COUNT;
    int moreCount = chatCount - visibleChatCount;
    int itemCount = (chatTypeCount + visibleChatCount) * 2 + (moreCount > 0 ? 2 : 0);
    if (itemCount == 0)
      return;
    ArrayUtils.ensureCapacity(outList, itemCount);
    for (int includedChatType : TD.includedChatTypes(chatFolder)) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setIntValue(includedChatType));
      outList.add(chatTypeItem(includedChatType));
    }
    int count = 0;
    for (long pinnedChatId : chatFolder.pinnedChatIds) {
      if (count++ >= visibleChatCount)
        break;
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setLongValue(pinnedChatId));
      outList.add(chatItem(pinnedChatId).setBoolValue(true /* included chat */));
    }
    for (long includedChatId : chatFolder.includedChatIds) {
      if (count++ >= visibleChatCount)
        break;
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setLongValue(includedChatId));
      outList.add(chatItem(includedChatId).setBoolValue(true /* included chat */));
    }
    if (moreCount > 0) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setIntValue(R.id.btn_showAdvanced));
      outList.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.ChatsXShowMore, moreCount)).setBoolValue(true /* included chats */));
    }
  }

  private void fillExcludedChats (TdApi.ChatFolder chatFolder, List<ListItem> outList) {
    int chatTypeCount = TD.countExcludedChatTypes(chatFolder);
    int chatCount = chatFolder.excludedChatIds.length;
    int visibleChatCount = showAllExcludedChats || (chatCount <= COLLAPSED_CHAT_COUNT + 1) ? chatCount : COLLAPSED_CHAT_COUNT;
    int moreCount = chatCount - visibleChatCount;
    int itemCount = (chatTypeCount + visibleChatCount) * 2 + (moreCount > 0 ? 2 : 0);
    if (itemCount == 0)
      return;
    ArrayUtils.ensureCapacity(outList, itemCount);
    for (int excludedChatType : TD.excludedChatTypes(chatFolder)) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setIntValue(excludedChatType));
      outList.add(chatTypeItem(excludedChatType));
    }
    for (int index = 0; index < visibleChatCount; index++) {
      long excludedChatId = chatFolder.excludedChatIds[index];
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setLongValue(excludedChatId));
      outList.add(chatItem(excludedChatId).setBoolValue(false /* excluded chat */));
    }
    if (moreCount > 0) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setIntValue(R.id.btn_showAdvanced));
      outList.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.ChatsXShowMore, moreCount)).setBoolValue(false /* excluded chats */));
    }
  }

  private ListItem chatItem (long chatId) {
    TGFoundChat foundChat = new TGFoundChat(tdlib, null, chatId, true);
    foundChat.setNoUnread();
    return new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat).setData(foundChat).setLongId(chatId);
  }

  private ListItem chatTypeItem (@IdRes int id) {
    TdlibAccentColor accentColor = tdlib.accentColor(TD.chatTypeAccentColorId(id));
    return new ListItem(ListItem.TYPE_CHAT_BETTER, id, TD.chatTypeIcon24(id), TD.chatTypeName(id))
      .setAccentColor(accentColor);
  }

  private void loadChatFolder () {
    tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        updateChatFolder(chatFolder);
      }
    }));
  }

  private void updateChatFolder (TdApi.ChatFolder chatFolder) {
    this.editedChatFolder = chatFolder;
    updateMenuButton();
    updateIncludedChats();
    updateExcludedChats();
  }

  private void updateIncludedChats () {
    int previousItemIndex = adapter.indexOfViewById(includedChatsPreviousItemId);
    int nextItemIndex = adapter.indexOfViewById(includedChatsNextItemId);
    if (previousItemIndex == -1 || nextItemIndex == -1)
      return;
    int firstItemIndex = previousItemIndex + 1;
    TEMP_ITEM_LIST.clear();
    fillIncludedChats(editedChatFolder, TEMP_ITEM_LIST);
    if (firstItemIndex < nextItemIndex) {
      List<ListItem> oldList = adapter.getItems().subList(firstItemIndex, nextItemIndex);
      DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtilCallback(oldList, TEMP_ITEM_LIST));
      oldList.clear();
      oldList.addAll(TEMP_ITEM_LIST);
      diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, firstItemIndex));
    } else if (TEMP_ITEM_LIST.size() > 0) {
      adapter.addItems(firstItemIndex, TEMP_ITEM_LIST.toArray(new ListItem[0]));
    }
    TEMP_ITEM_LIST.clear();
  }

  private void updateExcludedChats () {
    int previousItemIndex = adapter.indexOfViewById(excludedChatsPreviousItemId);
    int nextItemIndex = adapter.indexOfViewById(excludedChatsNextItemId);
    if (previousItemIndex == -1 || nextItemIndex == -1)
      return;
    int firstItemIndex = previousItemIndex + 1;
    TEMP_ITEM_LIST.clear();
    fillExcludedChats(editedChatFolder, TEMP_ITEM_LIST);
    if (firstItemIndex < nextItemIndex) {
      List<ListItem> oldList = adapter.getItems().subList(firstItemIndex, nextItemIndex);
      DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtilCallback(oldList, TEMP_ITEM_LIST));
      oldList.clear();
      oldList.addAll(TEMP_ITEM_LIST);
      diffResult.dispatchUpdatesTo(new AdapterSubListUpdateCallback(adapter, firstItemIndex));
    } else if (TEMP_ITEM_LIST.size() > 0) {
      adapter.addItems(firstItemIndex, TEMP_ITEM_LIST.toArray(new ListItem[0]));
    }
    TEMP_ITEM_LIST.clear();
  }

  private void updateFolderName () {
    if (StringUtils.isEmpty(editedChatFolder.title) && editedChatFolder.pinnedChatIds.length == 0 && editedChatFolder.includedChatIds.length == 0) {
      int[] includedChatTypes = TD.includedChatTypes(editedChatFolder);
      if (includedChatTypes.length == 1) {
        int includedChatType = includedChatTypes[0];
        String chatTypeName = Lang.getString(TD.chatTypeName(includedChatType));
        boolean hasChanges = false;
        if (input.setStringValueIfChanged(chatTypeName)) {
          editedChatFolder.title = chatTypeName;
          hasChanges = true;
        }
        if (editedChatFolder.icon == null) {
          TdApi.ChatFolderIcon chatTypeIcon = TD.chatTypeIcon(includedChatType);
          if (chatTypeIcon != null) {
            editedChatFolder.icon = chatTypeIcon;
            hasChanges = true;
          }
        }
        if (hasChanges) {
          adapter.updateSimpleItemById(input.getId());
        }
      }
    }
  }

  @Override
  public void onSelectedChatsChanged (int mode, Set<Long> chatIds, Set<Integer> chatTypes) {
    if (mode == SelectChatsController.MODE_FOLDER_INCLUDE_CHATS) {
      TD.updateIncludedChats(editedChatFolder, originChatFolder, chatIds);
      TD.updateIncludedChatTypes(editedChatFolder, chatTypes);
    } else if (mode == SelectChatsController.MODE_FOLDER_EXCLUDE_CHATS) {
      TD.updateExcludedChats(editedChatFolder, chatIds);
      TD.updateExcludedChatTypes(editedChatFolder, chatTypes);
    } else {
      throw new UnsupportedOperationException();
    }
    updateFolderName();
    updateChatFolder(editedChatFolder);
  }

  private void showRemoveConditionConfirm (int position, ListItem item) {
    boolean inclusion = item.getBoolValue();
    CharSequence title;
    if (item.getId() == R.id.chat) {
      title = ((TGFoundChat) item.getData()).getFullTitle();
    } else {
      title = item.getString();
    }
    CharSequence info = Lang.getStringBold(inclusion ? R.string.FolderRemoveInclusionConfirm : R.string.FolderRemoveExclusionConfirm, title);
    showConfirm(info, Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OPTION_COLOR_RED, () -> {
      int index = adapter.getItem(position) == item ? position : adapter.indexOfView(item);
      if (index != RecyclerView.NO_POSITION) {
        adapter.removeRange(index - 1, 2); /* separator, condition */
      }
      if (item.getId() == R.id.chat) {
        long chatId = item.getLongId();
        if (inclusion) {
          editedChatFolder.pinnedChatIds = ArrayUtils.removeElement(editedChatFolder.pinnedChatIds, ArrayUtils.indexOf(editedChatFolder.pinnedChatIds, chatId));
          editedChatFolder.includedChatIds = ArrayUtils.removeElement(editedChatFolder.includedChatIds, ArrayUtils.indexOf(editedChatFolder.includedChatIds, chatId));
        } else {
          editedChatFolder.excludedChatIds = ArrayUtils.removeElement(editedChatFolder.excludedChatIds, ArrayUtils.indexOf(editedChatFolder.excludedChatIds, chatId));
        }
      } else if (item.getId() == R.id.chatType_contact) {
        editedChatFolder.includeContacts = false;
      } else if (item.getId() == R.id.chatType_nonContact) {
        editedChatFolder.includeNonContacts = false;
      } else if (item.getId() == R.id.chatType_group) {
        editedChatFolder.includeGroups = false;
      } else if (item.getId() == R.id.chatType_channel) {
        editedChatFolder.includeChannels = false;
      } else if (item.getId() == R.id.chatType_bot) {
        editedChatFolder.includeBots = false;
      } else if (item.getId() == R.id.chatType_muted) {
        editedChatFolder.excludeMuted = false;
      } else if (item.getId() == R.id.chatType_read) {
        editedChatFolder.excludeRead = false;
      } else if (item.getId() == R.id.chatType_archived) {
        editedChatFolder.excludeArchived = false;
      }
      updateFolderName();
      updateMenuButton();
    });
  }

  private void showRemoveFolderConfirm () {
    showConfirm(Lang.getString(R.string.RemoveFolderConfirm), Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OPTION_COLOR_RED, () -> {
      deleteChatFolder(chatFolderId);
    });
  }

  private boolean hasChanges () {
    TdApi.ChatFolder originChatFolder = this.originChatFolder != null ? this.originChatFolder : EMPTY_CHAT_FOLDER;
    TdApi.ChatFolder editedChatFolder = this.editedChatFolder != null ? this.editedChatFolder : EMPTY_CHAT_FOLDER;
    return !TD.contentEquals(originChatFolder, editedChatFolder);
  }

  private boolean canSaveChanges () {
    String title = editedChatFolder.title.trim();
    if (StringUtils.isEmpty(title)) {
      return false;
    }
    int codePointCount = Character.codePointCount(title, 0, title.length());
    if (codePointCount > MAX_CHAT_FOLDER_TITLE_LENGTH) {
      return false;
    }
    return (editedChatFolder.includeContacts || editedChatFolder.includeNonContacts || editedChatFolder.includeGroups || editedChatFolder.includeChannels || editedChatFolder.includeBots || editedChatFolder.pinnedChatIds.length > 0 || editedChatFolder.includedChatIds.length > 0) &&
      (chatFolderId == NO_CHAT_FOLDER_ID || hasChanges());
  }

  private void saveChanges () {
    if (chatFolderId != NO_CHAT_FOLDER_ID) {
      editChatFolder(chatFolderId, TD.copyOf(editedChatFolder));
    } else {
      createChatFolder(TD.copyOf(editedChatFolder));
    }
  }

  private void createChatFolder (TdApi.ChatFolder chatFolder) {
    tdlib.send(new TdApi.CreateChatFolder(chatFolder), tdlib.successHandler(this::closeSelf));
  }

  private void editChatFolder (int chatFolderId, TdApi.ChatFolder chatFolder) {
    tdlib.send(new TdApi.EditChatFolder(chatFolderId, chatFolder), tdlib.successHandler(this::closeSelf));
  }

  private void deleteChatFolder (int chatFolderId) {
    tdlib.send(new TdApi.DeleteChatFolder(chatFolderId, null), tdlib.typedOkHandler(this::closeSelf));
  }

  private void closeSelf () {
    if (!isDestroyed()) {
      navigateBack();
    }
  }

  private void updateMenuButton () {
    if (headerView != null) {
      headerView.updateButton(getMenuId(), R.id.menu_btn_done, canSaveChanges() ? View.VISIBLE : View.GONE, 0);
    }
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }
    @Override
    protected void setChatData (ListItem item, int position, BetterChatView chatView) {
      if (item.getId() == R.id.chat) {
        chatView.setNoSubtitle(false);
        chatView.setChat((TGFoundChat) item.getData());
        chatView.setAllowMaximizePreview(false);
      } else {
        chatView.setTitle(item.getString());
        chatView.setSubtitle(null);
        chatView.setNoSubtitle(true);
        chatView.setAvatar(null, new AvatarPlaceholder.Metadata(item.getAccentColor(), item.getIconResource()));
        chatView.clearPreviewChat();
      }
    }

    @Override
    protected SettingHolder initCustom (ViewGroup parent) {
      FrameLayoutFix frameLayout = new FrameLayoutFix(parent.getContext());
      frameLayout.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, Screen.dp(57f)));
      ViewSupport.setThemedBackground(frameLayout, ColorId.filling, EditChatFolderController.this);

      MaterialEditTextGroup editText = new MaterialEditTextGroup(parent.getContext(), false);
      editText.setId(android.R.id.input);
      editText.applyRtl(Lang.rtl());
      editText.addThemeListeners(EditChatFolderController.this);
      editText.setTextListener(this);
      editText.setFocusListener(this);
      editText.addLengthCounter(true);
      editText.setMaxLength(MAX_CHAT_FOLDER_TITLE_LENGTH);
      editText.getEditText().setLineDisabled(true);
      editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

      FrameLayout.LayoutParams editTextParams = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
      editTextParams.leftMargin = Screen.dp(16f);
      editTextParams.rightMargin = Screen.dp(57f);
      editTextParams.bottomMargin = Screen.dp(8f);
      frameLayout.addView(editText, editTextParams);

      ImageView imageView = new ImageView(parent.getContext());
      imageView.setId(android.R.id.icon);
      imageView.setScaleType(ImageView.ScaleType.CENTER);
      imageView.setColorFilter(Theme.getColor(ColorId.icon));
      addThemeFilterListener(imageView, ColorId.icon);
      RippleSupport.setTransparentSelector(imageView);
      Views.setClickable(imageView);
      imageView.setOnClickListener(v -> showIconSelector());

      FrameLayout.LayoutParams imageViewParams = new FrameLayout.LayoutParams(Screen.dp(57f), Screen.dp(57f), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
      frameLayout.addView(imageView, imageViewParams);

      setLockFocusView(editText.getEditText());

      SettingHolder holder = new SettingHolder(frameLayout);
      holder.setIsRecyclable(false);
      return holder;
    }

    @Override
    protected void setCustom (ListItem item, SettingHolder holder, int position) {
      MaterialEditTextGroup editText = holder.itemView.findViewById(android.R.id.input);
      editText.applyRtl(Lang.rtl());
      editText.setEmptyHint(R.string.FolderNameHint);
      editText.setText(item.getStringValue());

      ImageView imageView = holder.itemView.findViewById(android.R.id.icon);
      int iconResource = TD.findFolderIcon(editedChatFolder.icon, R.drawable.baseline_folder_24);
      imageView.setImageDrawable(Drawables.get(imageView.getResources(), iconResource));
    }

    @Override
    protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
      if (item.getId() == R.id.btn_folderIncludeChats || item.getId() == R.id.btn_folderExcludeChats) {
        view.setIconColorId(ColorId.inlineIcon);
      } else if (item.getId() == R.id.btn_removeFolder) {
        view.setIconColorId(ColorId.iconNegative);
      } else {
        view.setIconColorId(ColorId.NONE /* theme_color_icon */);
      }
    }
  }

  private static class DiffUtilCallback extends ListItemDiffUtilCallback {
    public DiffUtilCallback (List<ListItem> oldList, List<ListItem> newList) {
      super(oldList, newList);
    }

    @Override
    public boolean areItemsTheSame (ListItem oldItem, ListItem newItem) {
      if (oldItem.getViewType() != newItem.getViewType() || oldItem.getId() != newItem.getId())
        return false;
      if (oldItem.getId() == R.id.chat) {
        return oldItem.getLongId() == newItem.getLongId();
      }
      if (oldItem.getViewType() == ListItem.TYPE_SEPARATOR)
        return oldItem.getIntValue() == newItem.getIntValue() && oldItem.getLongValue() == newItem.getLongValue();
      return true;
    }

    @Override
    public boolean areContentsTheSame (ListItem oldItem, ListItem newItem) {
      return false;
    }
  }

  private class RemoveHelperCallback implements RemoveHelper.Callback {
    @Override
    public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
      return viewHolder.getItemViewType() == ListItem.TYPE_CHAT_BETTER;
    }

    @Override
    public void onRemove (RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      int position = viewHolder.getAbsoluteAdapterPosition();
      showRemoveConditionConfirm(position, item);
    }
  }

  private void showIconSelector () {
    ChatFolderIconSelector.show(this, icon -> {
      if (!Td.equalsTo(editedChatFolder.icon, icon)) {
        editedChatFolder.icon = icon;
        adapter.updateSimpleItemById(input.getId());
        updateMenuButton();
      }
    });
  }
}
