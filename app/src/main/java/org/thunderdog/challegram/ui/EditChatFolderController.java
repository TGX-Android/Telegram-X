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

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewKt;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.EditHeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatFolderListener;
import org.thunderdog.challegram.telegram.ChatFoldersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.AdapterSubListUpdateCallback;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.ListItemDiffUtilCallback;
import org.thunderdog.challegram.util.PremiumLockModifier;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import kotlin.Unit;
import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class EditChatFolderController extends EditBaseController<EditChatFolderController.Arguments> implements View.OnClickListener, View.OnLongClickListener, SettingsAdapter.TextChangeListener, SelectChatsController.Delegate, ChatFoldersListener, ChatFolderListener {

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
  private final @IdRes int inviteLinksPreviousItemId = R.id.btn_createInviteLink;
  private final @IdRes int inviteLinksNextItemId = ViewCompat.generateViewId();

  private boolean showAllIncludedChats;
  private boolean showAllExcludedChats;

  private SettingsAdapter adapter;
  private @Nullable ListItem input;
  private @Nullable EditHeaderView headerCell;

  private volatile int chatFolderId;
  private TdApi.ChatFolder originChatFolder;
  private TdApi.ChatFolder editedChatFolder;

  private @Nullable TdApi.ChatFolderInviteLink[] inviteLinks;

  public EditChatFolderController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return chatFolderId != NO_CHAT_FOLDER_ID && (originChatFolder == null || originChatFolder.isShareable);
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
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  @SuppressWarnings("deprecation")
  protected int getHeaderHeight () {
    return headerCell != null ? Size.getHeaderBigPortraitSize(false) : super.getHeaderHeight();
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatFolderId = args.chatFolderId;
    this.originChatFolder = args.chatFolder;
    this.editedChatFolder = args.chatFolder != null ? TD.copyOf(args.chatFolder) : TD.newChatFolder(args.chatFolderName);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    if (Config.CHAT_FOLDERS_REDESIGN) {
      headerCell = new EditHeaderView(context, this);
      headerCell.setInput(editedChatFolder.title);
      headerCell.setInputOptions(R.string.FolderNameHint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
      headerCell.setOnPhotoClickListener(this::showIconSelector);
      headerCell.setImeOptions(EditorInfo.IME_ACTION_DONE);
      headerCell.getInputView().setFilters(new InputFilter[] {
        new CodePointCountFilter(MAX_CHAT_FOLDER_TITLE_LENGTH),
        new CharacterStyleFilter()
      });
      TextViewKt.doAfterTextChanged(headerCell.getInputView(), (editable) -> {
        onTitleChanged(editable != null ? editable.toString() : "");
        return Unit.INSTANCE;
      });
      setLockFocusView(headerCell.getInputView(), /* showAlways */ StringUtils.isEmpty(editedChatFolder.title));
      Views.setTopMargin(recyclerView, Size.getHeaderSizeDifference(false));
      updateFolderIcon();
    }

    ArrayList<ListItem> items = new ArrayList<>();

    if (Config.CHAT_FOLDERS_REDESIGN) {
      if (chatFolderId != NO_CHAT_FOLDER_ID) {
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_visible, 0, R.string.FolderVisible));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderName));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(input = new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.input).setStringValue(editedChatFolder.title));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.FolderIncludedChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderIncludeChats, R.drawable.baseline_add_24, R.string.FolderActionIncludeChats).setTextColorId(ColorId.inlineText));
    fillIncludedChats(editedChatFolder, items);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, includedChatsNextItemId));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.FolderIncludedChatsInfo)));

    if (!isShareable()) {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.FolderExcludedChats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_folderExcludeChats, R.drawable.baseline_add_24, R.string.FolderActionExcludeChats).setTextColorId(ColorId.inlineText));
      fillExcludedChats(editedChatFolder, items);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, excludedChatsNextItemId));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.FolderExcludedChatsInfo)));
    }

    if (chatFolderId != NO_CHAT_FOLDER_ID) {
      items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.InviteLinks));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, 0));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createInviteLink, R.drawable.baseline_add_link_24, R.string.CreateANewLink)
        .setTextColorId(ColorId.inlineText)
        .setDrawModifier(new PremiumLockModifier()));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, inviteLinksNextItemId));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ChatFolderInviteLinksInfo));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_removeFolder, R.drawable.baseline_delete_24, R.string.RemoveFolder).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));
    }

    items.add(new ListItem(ListItem.TYPE_ZERO_VIEW));

    adapter = new Adapter(this);
    if (input != null) {
      adapter.setLockFocusOn(this, /* showAlways */ StringUtils.isEmpty(editedChatFolder.title));
      adapter.setTextChangeListener(this);
    }
    adapter.setItems(items, false);
    CustomItemAnimator itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    itemAnimator.setSupportsChangeAnimations(false);
    recyclerView.setItemAnimator(itemAnimator);
    recyclerView.addItemDecoration(new ItemDecoration());
    recyclerView.setAdapter(adapter);
    RemoveHelper.attach(recyclerView, new RemoveHelperCallback());

    if (chatFolderId != NO_CHAT_FOLDER_ID) {
      if (originChatFolder == null) {
        loadChatFolder();
      }
      updateInviteLinks();
      tdlib.listeners().subscribeToChatFoldersUpdates(this);
      tdlib.listeners().addChatFolderListener(chatFolderId, this);
    }
  }

  @Override
  protected void onDoneVisibleChanged (boolean isVisible) {
    if (recyclerView != null) {
      recyclerView.invalidateItemDecorations();
      adapter.notifyLastItemChanged();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromChatFoldersUpdates(this);
    tdlib.listeners().removeChatFolderListener(chatFolderId, this);
  }

  private boolean firstFocus;

  @Override
  public void onFocus () {
    super.onFocus();
    if (firstFocus) {
      firstFocus = false;
    } else {
      updateInviteLinks();
      if (chatFolderId != NO_CHAT_FOLDER_ID) {
        checkFolderDeleted();
      }
    }
  }

  @Override
  public void onChatFolderInviteLinkDeleted (int chatFolderId, String inviteLink) {
    if (this.chatFolderId == chatFolderId) {
      updateInviteLinks();
    }
  }

  @Override
  public void onChatFolderInviteLinkCreated (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    if (this.chatFolderId == chatFolderId) {
      updateInviteLinks();
    }
  }

  @Override
  public void onChatFolderInviteLinkChanged (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    if (this.chatFolderId == chatFolderId) {
      updateInviteLinks();
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
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_folderIncludeChats) {
      boolean showChatTypes = !isShareable();
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.includedChats(this, chatFolderId, editedChatFolder, showChatTypes));
      navigateTo(selectChats);
    } else if (id == R.id.btn_folderExcludeChats) {
      boolean showChatTypes = !isShareable();
      SelectChatsController selectChats = new SelectChatsController(context, tdlib);
      selectChats.setArguments(SelectChatsController.Arguments.excludedChats(this, chatFolderId, editedChatFolder, showChatTypes));
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
    } else if (id == R.id.btn_visible) {
      UI.forceVibrate(v, false);
      boolean isEnabled = adapter.toggleView(v);
      tdlib.settings().setChatFolderEnabled(chatFolderId, isEnabled);
    } else if (id == R.id.btn_removeFolder) {
      if (originChatFolder.isShareable) {
        tdlib.send(new TdApi.GetChatFolderChatsToLeave(chatFolderId), (result, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else if (result.totalCount > 0) {
            ChatFolderInviteLinkController controller = new ChatFolderInviteLinkController(context, tdlib);
            controller.setArguments(ChatFolderInviteLinkController.Arguments.deleteFolder(chatFolderId, originChatFolder.title, result.chatIds));
            controller.show();
          } else {
            showRemoveFolderConfirm();
          }
        }));
      } else {
        showRemoveFolderConfirm();
      }
    } else if (id == R.id.btn_createInviteLink) {
      onCreateInviteLinkClick(v);
    } else if (id == R.id.btn_inviteLink) {
      ListItem item = (ListItem) v.getTag();
      TdApi.ChatFolderInviteLink inviteLink = (TdApi.ChatFolderInviteLink) item.getData();
      EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
      controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, editedChatFolder, inviteLink));
      navigateTo(controller);
    } else if (id == R.id.chat || ArrayUtils.contains(TD.CHAT_TYPES, id)) {
      int position = getRecyclerView().getChildAdapterPosition(v);
      ListItem item = (ListItem) v.getTag();
      showRemoveConditionConfirm(position, item);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_inviteLink) {
      ListItem item = (ListItem) v.getTag();
      TdApi.ChatFolderInviteLink inviteLink = (TdApi.ChatFolderInviteLink) item.getData();
      showInviteLinkOptions(inviteLink);
      return true;
    }
    return false;
  }

  @Override
  protected boolean onDoneClick () {
    if (!isInProgress()) {
      saveChanges(this::closeSelf);
    }
    return true;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (hasUnsavedChanges()) {
      showUnsavedChangesPromptBeforeLeaving(/* onConfirm */ null);
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return !hasUnsavedChanges();
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
    updateDoneButton();
  }

  @Override
  public void onChatFoldersChanged (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition) {
    if (chatFolderId != NO_CHAT_FOLDER_ID && isFocused()) {
      checkFolderDeleted();
    }
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    if (item == input) {
      onTitleChanged(text);
    }
  }

  private void onTitleChanged (String text) {
    editedChatFolder.title = text;
    updateDoneButton();
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
        boolean isFirstLoad = this.originChatFolder == null;
        updateChatFolder(chatFolder);
        if (isFirstLoad && (!chatFolder.isShareable || inviteLinks != null)) {
          executeScheduledAnimation();
        }
      }
    }));
  }

  private void updateChatFolder (TdApi.ChatFolder chatFolder) {
    if (this.originChatFolder == null) {
      this.originChatFolder = TD.copyOf(chatFolder);
    }
    this.editedChatFolder = chatFolder;
    updateDoneButton();
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
    if (!StringUtils.isEmpty(editedChatFolder.title)) {
      return;
    }
    if (editedChatFolder.pinnedChatIds.length > 0 || editedChatFolder.includedChatIds.length > 0) {
      return;
    }
    int[] includedChatTypes = TD.includedChatTypes(editedChatFolder);
    if (includedChatTypes.length != 1) {
      return;
    }
    int includedChatType = includedChatTypes[0];
    String chatTypeName = Lang.getString(TD.chatTypeName(includedChatType));
    boolean isNameChanged = false;
    boolean isIconChanged = false;
    if (!StringUtils.equalsOrBothEmpty(editedChatFolder.title, chatTypeName)) {
      editedChatFolder.title = chatTypeName;
      isNameChanged = true;
      if (input != null) {
        input.setStringValue(chatTypeName);
      }
      if (headerCell != null) {
        headerCell.setInput(chatTypeName);
      }
    }
    if (editedChatFolder.icon == null) {
      TdApi.ChatFolderIcon chatTypeIcon = TD.chatTypeIcon(includedChatType);
      if (chatTypeIcon != null) {
        editedChatFolder.icon = chatTypeIcon;
        isIconChanged = true;
      }
    }
    if (input != null && (isNameChanged || isIconChanged)) {
      adapter.updateSimpleItemById(input.getId());
    }
    if (headerCell != null && isIconChanged) {
      updateFolderIcon();
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
    @StringRes int stringRes;
    if (item.getId() == R.id.chat) {
      long chatId = item.getLongId();
      if (tdlib.isUserChat(chatId)) {
        stringRes = inclusion ? R.string.FolderRemoveInclusionConfirmUser : R.string.FolderRemoveExclusionConfirmUser;
      } else {
        stringRes = inclusion ? R.string.FolderRemoveInclusionConfirmChat : R.string.FolderRemoveExclusionConfirmChat;
      }
    } else {
      stringRes = inclusion ? R.string.FolderRemoveInclusionConfirmType : R.string.FolderRemoveExclusionConfirmType;
    }
    CharSequence info = Lang.getStringBold(stringRes, title);
    showConfirm(info, Lang.getString(R.string.Remove), R.drawable.baseline_delete_24, OptionColor.RED, () -> {
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
      updateDoneButton();
    });
  }

  private void showRemoveFolderConfirm () {
    tdlib.ui().showDeleteChatFolderConfirm(this, hasInviteLinks(), () -> {
      deleteChatFolder(chatFolderId);
    });
  }

  private boolean hasUnsavedChanges () {
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
      (chatFolderId == NO_CHAT_FOLDER_ID || hasUnsavedChanges());
  }

  private void saveChanges (Runnable after) {
    if (chatFolderId != NO_CHAT_FOLDER_ID) {
      editChatFolder(chatFolderId, TD.copyOf(editedChatFolder), after);
    } else {
      createChatFolder(TD.copyOf(editedChatFolder), after);
    }
  }

  private void createChatFolder (TdApi.ChatFolder chatFolder, Runnable after) {
    executeWithProgress(new TdApi.CreateChatFolder(chatFolder), after);
  }

  private void editChatFolder (int chatFolderId, TdApi.ChatFolder chatFolder, Runnable after) {
    executeWithProgress(new TdApi.EditChatFolder(chatFolderId, chatFolder), after);
  }

  private void deleteChatFolder (int chatFolderId) {
    executeWithProgress(new TdApi.DeleteChatFolder(chatFolderId, null), this::closeSelf);
  }

  private void executeWithProgress (TdApi.Function<?> request, Runnable onResult) {
    setInProgress(true);
    tdlib.send(request, (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      updateDoneButton();
      if (error != null) {
        UI.showError(error);
      } else {
        onResult.run();
      }
    }));
  }

  private void closeSelf () {
    if (!isDestroyed()) {
      navigateBack();
    }
  }

  private void updateFolderIcon () {
    if (input != null) {
      adapter.updateSimpleItemById(input.getId());
    }
    if (headerCell != null) {
      int iconResource = TD.findFolderIcon(editedChatFolder.icon, R.drawable.baseline_folder_24);
      headerCell.setIcon(iconResource, ColorId.white);
    }
  }

  private void updateDoneButton () {
    boolean isDoneVisible = isInProgress() || canSaveChanges();
    setDoneVisible(isDoneVisible);
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
      frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(57f)));
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

      FrameLayout.LayoutParams editTextParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
      view.setDrawModifier(item.getDrawModifier());

      if (item.getId() == R.id.btn_createInviteLink) {
        view.setName(hasInviteLinks() ? R.string.CreateANewLink : R.string.CreateAnInviteLink);
        PremiumLockModifier modifier = (PremiumLockModifier) ObjectsCompat.requireNonNull(item.getDrawModifier());
        boolean showLockIcon = !canCreateInviteLink() && !tdlib.hasPremium() && chatTypeCount() == 0;
        modifier.setVisible(showLockIcon);
        view.setTooltipLocationProvider(showLockIcon ? modifier : null);
      } else if (item.getId() == R.id.btn_inviteLink) {
        TdApi.ChatFolderInviteLink inviteLink = (TdApi.ChatFolderInviteLink) item.getData();
        view.setData(Lang.plural(R.string.xChats, inviteLink.chatIds.length));
      } else if (item.getId() == R.id.btn_visible) {
        boolean isEnabled = tdlib.settings().isChatFolderEnabled(chatFolderId);
        view.getToggler().setRadioEnabled(isEnabled, isUpdate);
      }

      if (item.getId() == R.id.btn_createInviteLink) {
        view.setIgnoreEnabled(true);
        view.setVisuallyEnabled(canCreateInviteLink(), isUpdate);
      } else {
        view.setIgnoreEnabled(false);
      }

      if (item.getId() == R.id.btn_folderIncludeChats || item.getId() == R.id.btn_folderExcludeChats) {
        view.setIconColorId(ColorId.inlineIcon);
      } else if (item.getId() == R.id.btn_createInviteLink) {
        view.setIconColorId(canCreateInviteLink() ? ColorId.inlineIcon : ColorId.textLight);
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
      if (oldItem.getId() == R.id.btn_inviteLink) {
        TdApi.ChatFolderInviteLink oldData = (TdApi.ChatFolderInviteLink) oldItem.getData();
        TdApi.ChatFolderInviteLink newData = (TdApi.ChatFolderInviteLink) newItem.getData();
        return ObjectsCompat.equals(oldData.inviteLink, newData.inviteLink);
      }
      if (oldItem.getViewType() == ListItem.TYPE_SEPARATOR) {
        return oldItem.getIntValue() == newItem.getIntValue() &&
          oldItem.getLongValue() == newItem.getLongValue() &&
          ObjectsCompat.equals(oldItem.getStringValue(), newItem.getStringValue());
      }
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
      Object tag = viewHolder.itemView.getTag();
      if (tag instanceof ListItem) {
        ListItem item = (ListItem) tag;
        if (item.getId() == R.id.btn_inviteLink) {
          return true;
        }
      }
      return viewHolder.getItemViewType() == ListItem.TYPE_CHAT_BETTER;
    }

    @Override
    public void onRemove (RecyclerView.ViewHolder viewHolder) {
      ListItem item = (ListItem) viewHolder.itemView.getTag();
      if (item.getViewType() == ListItem.TYPE_CHAT_BETTER) {
        int position = viewHolder.getAbsoluteAdapterPosition();
        showRemoveConditionConfirm(position, item);
      } else if (item.getId() == R.id.btn_inviteLink) {
        TdApi.ChatFolderInviteLink data = (TdApi.ChatFolderInviteLink) item.getData();
        showDeleteInviteLinkConfirm(data.inviteLink);
      }
    }
  }

  private void showIconSelector () {
    ChatFolderIconSelector.show(this, TD.getIconName(editedChatFolder), selectedIcon -> {
      if (!Td.equalsTo(editedChatFolder.icon, selectedIcon)) {
        editedChatFolder.icon = selectedIcon;
        updateFolderIcon();
        updateDoneButton();
      }
    });
  }

  @AnyThread
  private void updateInviteLinks () {
    if (chatFolderId == NO_CHAT_FOLDER_ID) {
      return;
    }
    tdlib.send(new TdApi.GetChatFolderInviteLinks(chatFolderId), (result, error) -> {
      TdApi.ChatFolderInviteLink[] inviteLinks = result != null ? result.inviteLinks : new TdApi.ChatFolderInviteLink[0];
      runOnUiThreadOptional(() -> {
        boolean isFirstLoad = this.inviteLinks == null;
        updateInviteLinks(inviteLinks);
        if (isFirstLoad && originChatFolder != null) {
          executeScheduledAnimation();
        }
      });
    });
  }

  private void updateInviteLinks (TdApi.ChatFolderInviteLink[] inviteLinks) {
    this.inviteLinks = inviteLinks;
    int previousItemIndex = adapter.indexOfViewById(inviteLinksPreviousItemId);
    if (previousItemIndex == -1) {
      return;
    }
    int nextItemIndex = adapter.indexOfViewById(inviteLinksNextItemId);
    int firstItemIndex = previousItemIndex + 1;
    TEMP_ITEM_LIST.clear();
    fillInviteLinks(inviteLinks, TEMP_ITEM_LIST);
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
    adapter.updateValuedSettingById(R.id.btn_createInviteLink);
  }

  /**
   * @noinspection SameParameterValue
   */
  private void fillInviteLinks (TdApi.ChatFolderInviteLink[] inviteLinks, List<ListItem> outList) {
    for (TdApi.ChatFolderInviteLink inviteLink : inviteLinks) {
      outList.add(new ListItem(ListItem.TYPE_SEPARATOR).setStringValue(inviteLink.inviteLink));
      outList.add(inviteLink(inviteLink));
    }
  }

  private ListItem inviteLink (TdApi.ChatFolderInviteLink inviteLink) {
    String name = getName(inviteLink);
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_inviteLink, R.drawable.baseline_link_24, name).setData(inviteLink);
  }

  private void onCreateInviteLinkClick (View view) {
    if (chatTypeCount() > 0) {
      UI.forceVibrateError(view);
      showTooltip(view, R.string.ChatFolderInviteLinkChatTypesNotSupported);
      return;
    }
    if (!isShareable() && !tdlib.canAddShareableFolder()) {
      showShareableFoldersLimitReached(view);
      return;
    }
    if (inviteLinkCount() >= tdlib.chatFolderInviteLinkCountMax()) {
      showInviteLinksLimitReached(view);
      return;
    }
    if (hasUnsavedChanges()) {
      showConfirm(Lang.getString(R.string.ChatFolderSaveChangesBeforeCreatingANewInviteLink), Lang.getString(R.string.LocalizationEditConfirmSave), () -> {
        saveChanges(this::createInviteLink);
      });
    } else {
      createInviteLink();
    }
  }

  private void showShareableFoldersLimitReached (View view) {
    UI.forceVibrateError(view);
    if (tdlib.hasPremium()) {
      showTooltip(view, R.string.ShareableFoldersLimitReached, tdlib.addedShareableChatFolderCountMax());
    } else {
      tdlib.ui().showPremiumLimitInfo(this, view, TdlibUi.PremiumLimit.SHAREABLE_FOLDER_COUNT);
    }
  }

  private void showInviteLinksLimitReached (View view) {
    UI.forceVibrateError(view);
    if (tdlib.hasPremium()) {
      showTooltip(view, R.string.ChatFolderInviteLinksLimitReached, tdlib.chatFolderInviteLinkCountMax());
    } else {
      tdlib.ui().showPremiumLimitInfo(this, view, TdlibUi.PremiumLimit.CHAT_FOLDER_INVITE_LINK_COUNT);
    }
  }

  private void showTooltip (View view, @StringRes int markdownStringRes, Object... formatArgs) {
    context.tooltipManager().builder(view).controller(this).show(tdlib, Lang.getMarkdownString(this, markdownStringRes, formatArgs)).hideDelayed();
  }

  private void showInviteLinkOptions (TdApi.ChatFolderInviteLink inviteLink) {
    Options.Builder builder = new Options.Builder();
    builder.info(getName(inviteLink));
    builder.item(new OptionItem(R.id.btn_copyLink, Lang.getString(R.string.InviteLinkCopy), OptionColor.NORMAL, R.drawable.baseline_content_copy_24));
    builder.item(new OptionItem(R.id.btn_shareLink, Lang.getString(R.string.ShareLink), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
    builder.item(new OptionItem(R.id.btn_deleteLink, Lang.getString(R.string.InviteLinkDelete), OptionColor.RED, R.drawable.baseline_delete_24));
    showOptions(builder.build(), (view, id) -> {
      if (id == R.id.btn_copyLink) {
        copyInviteLink(inviteLink);
      } else if (id == R.id.btn_shareLink) {
        shareInviteLink(inviteLink);
      } else if (id == R.id.btn_deleteLink) {
        showDeleteInviteLinkConfirm(inviteLink.inviteLink);
      }
      return true;
    });
  }

  private void copyInviteLink (TdApi.ChatFolderInviteLink inviteLink) {
    UI.copyText(inviteLink.inviteLink, R.string.CopiedLink);
  }

  private void shareInviteLink (TdApi.ChatFolderInviteLink inviteLink) {
    tdlib.ui().shareUrl(this, inviteLink.inviteLink);
  }

  private void showDeleteInviteLinkConfirm (String inviteLink) {
    showConfirm(Lang.getString(R.string.AreYouSureDeleteInviteLink), Lang.getString(R.string.InviteLinkDelete), R.drawable.baseline_delete_24, OptionColor.RED, () -> {
      deleteInviteLink(inviteLink);
    });
  }

  private void createInviteLink () {
    tdlib.send(new TdApi.GetChatsForChatFolderInviteLink(chatFolderId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else if (result.totalCount == 0) {
        EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
        controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, editedChatFolder));
        navigateTo(controller);
      } else {
        createInviteLink(result.chatIds);
      }
    }));
  }

  private void createInviteLink (long[] shareableChatIds) {
    tdlib.createChatFolderInviteLink(chatFolderId, /* name */ "", shareableChatIds, (inviteLink, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        View targetView = getRecyclerView().findViewById(R.id.btn_createInviteLink);
        if (TD.ERROR_CHATLISTS_TOO_MUCH.equals(error.message) && targetView != null) {
          showShareableFoldersLimitReached(targetView);
        } else {
          UI.showError(error);
        }
      } else {
        EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
        controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, editedChatFolder, shareableChatIds, inviteLink));
        navigateTo(controller);
      }
    }));
  }

  private void deleteInviteLink (String inviteLink) {
    tdlib.deleteChatFolderInviteLink(chatFolderId, inviteLink, tdlib.typedOkHandler());
  }

  private String getName (TdApi.ChatFolderInviteLink inviteLink) {
    if (StringUtils.isEmptyOrInvisible(inviteLink.name)) {
      return StringUtils.urlWithoutProtocol(inviteLink.inviteLink);
    }
    return inviteLink.name;
  }

  private int inviteLinkCount () {
    return inviteLinks != null ? inviteLinks.length : 0;
  }

  private int chatTypeCount () {
    return TD.countIncludedChatTypes(editedChatFolder) + TD.countExcludedChatTypes(editedChatFolder);
  }

  private boolean hasInviteLinks () {
    return inviteLinkCount() > 0;
  }

  private boolean canCreateInviteLink () {
    int chatTypeCount = chatTypeCount();
    if (chatTypeCount > 0) {
      return false;
    }
    int inviteLinkCount = inviteLinkCount();
    if (inviteLinkCount >= tdlib.chatFolderInviteLinkCountMax()) {
      return false;
    }
    return isShareable() || tdlib.canAddShareableFolder();
  }

  private boolean isShareable () {
    return editedChatFolder.isShareable || hasInviteLinks();
  }

  private void checkFolderDeleted () {
    if (chatFolderId == NO_CHAT_FOLDER_ID) return;
    boolean isFolderDeleted = tdlib.chatFolderInfo(chatFolderId) == null;
    if (isFolderDeleted) {
      closeSelf();
    }
  }

  private class ItemDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.setEmpty();
      RecyclerView.ViewHolder viewHolder = parent.getChildViewHolder(view);
      if (viewHolder.getItemViewType() == ListItem.TYPE_ZERO_VIEW && isDoneVisible()) {
        outRect.bottom = Screen.dp(76);
      }
    }
  }
}
