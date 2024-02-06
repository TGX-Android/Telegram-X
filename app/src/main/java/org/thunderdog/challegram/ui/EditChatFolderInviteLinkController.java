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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.MaterialEditText;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongSet;

public class EditChatFolderInviteLinkController extends EditBaseController<EditChatFolderInviteLinkController.Arguments> implements View.OnClickListener, View.OnLongClickListener, SettingsAdapter.TextChangeListener {

  public static class Arguments {
    private final int chatFolderId;
    private final long[] chatIds;
    private final long[] shareableChatIds;
    private final String chatFolderName;
    private final @Nullable TdApi.ChatFolderInviteLink inviteLink;

    public Arguments (int chatFolderId, TdApi.ChatFolder chatFolder) {
      this(chatFolderId, chatFolder, ArrayUtils.EMPTY_LONGS, /* inviteLink */ null);
    }

    public Arguments (int chatFolderId, TdApi.ChatFolder chatFolder, TdApi.ChatFolderInviteLink inviteLink) {
      this(chatFolderId, chatFolder, inviteLink.chatIds, inviteLink);
    }

    public Arguments (int chatFolderId, TdApi.ChatFolder chatFolder, long[] shareableChatIds, @Nullable TdApi.ChatFolderInviteLink inviteLink) {
      this.chatFolderId = chatFolderId;
      this.chatFolderName = chatFolder.title;
      this.chatIds = U.concat(chatFolder.pinnedChatIds, chatFolder.includedChatIds);
      this.shareableChatIds = shareableChatIds;
      this.inviteLink = inviteLink;
    }
  }

  private final ChatIdSet selectedChatIds = new ChatIdSet();
  private final ChatIdSet includedChatIds = new ChatIdSet();
  private final ChatIdSet shareableChatIds = new ChatIdSet();
  private final Adapter adapter = new Adapter(this);

  private int chatFolderId;
  private String chatFolderName;
  private long[] chatIds;
  private TdApi.ChatFolderInviteLink inviteLink;
  private String inviteLinkName;

  private final int headerId = ViewCompat.generateViewId();

  public EditChatFolderInviteLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_editChatFolderInviteLink;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ShareFolder);
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.chatFolderId = args.chatFolderId;
    this.chatFolderName = args.chatFolderName;
    if (args.shareableChatIds.length > 0) {
      this.chatIds = U.concat(args.shareableChatIds, ArrayUtils.removeAll(args.chatIds, args.shareableChatIds));
    } else {
      this.chatIds = args.chatIds;
    }
    updateInviteLink(args.inviteLink);
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    ArrayList<ListItem> items = new ArrayList<>();

    if (isNoChatsToShare()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, 0, 0, R.string.ChatFolderInviteLinkNoChatsToShare));
    } else {
      items.add(new ListItem(ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION, R.id.btn_inviteLinkName, R.drawable.baseline_info_24, R.string.InviteLinkAdminName));
      //noinspection ConstantValue
      if (inviteLink != null) {
        String link = StringUtils.urlWithoutProtocol(inviteLink.inviteLink);
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, R.drawable.baseline_link_24, link));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.edit_description));
    }

    // chats
    if (chatIds != null && chatIds.length > 0) {
      if (isNoChatsToShare()) {
        items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.ChatFolderInviteLinkNoChatsToShareHeader));
      } else {
        items.add(new ListItem(ListItem.TYPE_HEADER_WITH_CHECKBOX, headerId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean addSeparator = false;
      for (long chatId : chatIds) {
        if (addSeparator) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        } else {
          addSeparator = true;
        }
        items.add(chatItem(chatId));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      // info
      if (isNoChatsToShare()) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ChatFolderInviteLinkNoChatsToShareInfo));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ChatFolderInviteLinkSelectGroupsAndChannelsInfo).setTextPaddingRight(Screen.dp(56f)));
      }
    }

    adapter.setLockFocusOn(this, false);
    adapter.setTextChangeListener(this);
    adapter.setItems(items, false);
    recyclerView.setLayoutManager(new LinearLayoutManager(context));
    recyclerView.setAdapter(adapter);
    updateDoneButton();

    if (!isNoChatsToShare()) {
      updateShareableChats();
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.chat) {
      ListItem item = (ListItem) v.getTag();
      long chatId = item.getLongId();
      if (shareableChatIds.has(chatId)) {
        boolean isChecked = selectedChatIds.add(chatId) || !selectedChatIds.remove(chatId);
        DoubleTextWrapper chat = (DoubleTextWrapper) item.getData();
        chat.setIsChecked(isChecked, /* animated */ true);
        updateItemsWithCounter();
        updateDoneButton();
      } else {
        context.tooltipManager()
          .builder(v)
          .controller(this)
          .locate((targetView, outRect) -> outRect.set(Screen.dp(48f), Screen.dp(5f), Screen.dp(58f), targetView.getHeight() - Screen.dp(5f)))
          .show(tdlib, R.string.ThisChatCantBeShared)
          .hideDelayed();
      }
    } else if (v.getId() == R.id.btn_inviteLink) {
      showInviteLinkOptions();
    } else if (v.getId() == headerId) {
      if (selectedChatIds.size() < shareableChatIds.size()) {
        selectedChatIds.addAll(shareableChatIds);
      } else {
        selectedChatIds.clear();
      }
      onSelectedChatsChanged();
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_inviteLink) {
      copyInviteLink();
      return true;
    }
    return false;
  }

  @Override
  protected boolean onDoneClick () {
    if (isInProgress()) {
      return true;
    }
    if (hasUnsavedChanges()) {
      saveChanges();
    } else {
      showInviteLinkOptions();
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

  private void updateInviteLink (@Nullable TdApi.ChatFolderInviteLink inviteLink) {
    this.inviteLink = inviteLink;
    if (inviteLinkName == null && inviteLink != null) {
      inviteLinkName = inviteLink.name;
    }
    selectedChatIds.clear();
    includedChatIds.clear();
    if (inviteLink != null) {
      selectedChatIds.addAll(inviteLink.chatIds);
      includedChatIds.addAll(inviteLink.chatIds);
    }
    onSelectedChatsChanged();
  }

  private void updateShareableChats () {
    tdlib.send(new TdApi.GetChatsForChatFolderInviteLink(chatFolderId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        updateShareableChats(result);
      }
    }));
  }

  private void updateShareableChats (TdApi.Chats chats) {
    shareableChatIds.clear();
    shareableChatIds.addAll(chats.chatIds);

    Arguments arguments = getArgumentsStrict();
    if (arguments.inviteLink != null) {
      shareableChatIds.addAll(arguments.inviteLink.chatIds); // TODO(nikita-toropov) ???
    }

    updateItemsWithCounter();
    updateChatItems();
  }

  private void onSelectedChatsChanged () {
    if (getWrapUnchecked() == null) {
      return;
    }
    updateItemsWithCounter();
    updateChatItems();
    updateDoneButton();
  }

  private void updateItemsWithCounter () {
    adapter.updateValuedSettingById(R.id.edit_description);
    adapter.updateValuedSettingById(headerId);
  }

  private void updateChatItems () {
    adapter.updateAllValuedSettingsById(R.id.chat);
  }

  private boolean hasSelectedChats () {
    return !selectedChatIds.isEmpty();
  }

  private boolean hasUnsavedChanges () {
    if (inviteLink == null) {
      return false;
    }
    return !ObjectsCompat.equals(inviteLink.name, inviteLinkName) || !includedChatIds.equals(selectedChatIds);
  }

  private void saveChanges () {
    if (!hasUnsavedChanges()) return;
    TdApi.ChatFolderInviteLink inviteLink = ObjectsCompat.requireNonNull(this.inviteLink);
    long[] chatIds = selectedChatIds.toArray();
    setInProgress(true);
    tdlib.editChatFolderInviteLink(chatFolderId, inviteLink.inviteLink, inviteLinkName, chatIds, (result, error) -> runOnUiThreadOptional(() -> {
      setInProgress(false);
      CharSequence message;
      if (error != null) {
        message = TD.toErrorString(error);
        updateDoneButton();
      } else {
        message = Lang.getString(R.string.Saved);
        updateInviteLink(result);
      }
      context.tooltipManager()
        .builder(getDoneButton())
        .controller(this)
        .show(tdlib, message)
        .hideDelayed();
    }));
  }

  private void showInviteLinkOptions () {
    Options.Builder builder = new Options.Builder();
    builder.item(new OptionItem(R.id.btn_copyLink, Lang.getString(R.string.InviteLinkCopy), OptionColor.NORMAL, R.drawable.baseline_content_copy_24));
    builder.item(new OptionItem(R.id.btn_shareLink, Lang.getString(R.string.ShareLink), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
    builder.item(new OptionItem(R.id.btn_deleteLink, Lang.getString(R.string.InviteLinkDelete), OptionColor.RED, R.drawable.baseline_delete_24));
    showOptions(builder.build(), (view, id) -> {
      if (id == R.id.btn_copyLink) {
        copyInviteLink();
      } else if (id == R.id.btn_shareLink) {
        shareInviteLink();
      } else if (id == R.id.btn_deleteLink) {
        showDeleteInviteLinkConfirm();
      }
      return true;
    });
  }

  private void copyInviteLink () {
    UI.copyText(inviteLink.inviteLink, R.string.CopiedLink);
  }

  private void shareInviteLink () {
    tdlib.ui().shareUrl(this, inviteLink.inviteLink);
  }

  private void showDeleteInviteLinkConfirm () {
    showConfirm(Lang.getString(R.string.AreYouSureDeleteInviteLink), Lang.getString(R.string.InviteLinkDelete), R.drawable.baseline_delete_24, OptionColor.RED, this::deleteInviteLink);
  }

  private void deleteInviteLink () {
    tdlib.deleteChatFolderInviteLink(chatFolderId, inviteLink.inviteLink, (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        navigateBack();
      }
    }));
  }

  private ListItem chatItem (long chatId) {
    TdApi.Chat chat = tdlib.chatStrict(chatId);
    boolean isPublic = tdlib.chatPublic(chatId);
    CharSequence subtitle;
    if (tdlib.isChannel(chatId)) {
      subtitle = Lang.getString(isPublic ? R.string.ChannelPublic : R.string.ChannelPrivate);
    } else if (tdlib.isMultiChat(chat)) {
      subtitle = Lang.getString(isPublic ? R.string.GroupPublic : R.string.GroupPrivate);
    } else if (tdlib.isBotChat(chat)) {
      subtitle = Lang.getString(R.string.Bot);
    } else if (tdlib.isUserChat(chat)) {
      subtitle = Lang.getString(R.string.PrivateChat);
    } else {
      subtitle = "";
    }
    DoubleTextWrapper data = new DoubleTextWrapper(tdlib, chat);
    data.setAdminSignVisible(false, false);
    data.setForcedSubtitle(subtitle);
    return new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.chat).setLongId(chatId).setData(data);
  }

  private @DrawableRes int doneIconRes = ResourcesCompat.ID_NULL;

  private void updateDoneButton () {
    if (inviteLink == null) {
      return;
    }
    int iconRes = hasUnsavedChanges() ? R.drawable.baseline_check_24 : R.drawable.baseline_share_arrow_24;
    if (doneIconRes != iconRes) {
      doneIconRes = iconRes;
      setDoneIcon(iconRes);
    }
    boolean isVisible = isInProgress() || hasSelectedChats();
    if (isFocused()) {
      setDoneVisible(isVisible);
    } else {
      setInstantDoneVisible(isVisible);
    }
  }

  private class Adapter extends SettingsAdapter {
    public Adapter (ViewController<?> context) {
      super(context);
    }

    @NonNull
    @Override
    public SettingHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      SettingHolder holder = super.onCreateViewHolder(parent, viewType);
      if (viewType == ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION) {
        int verticalPadding = Screen.dp(13f);
        FrameLayoutFix frameLayout = (FrameLayoutFix) holder.itemView;
        frameLayout.setPadding(frameLayout.getPaddingLeft(), verticalPadding, frameLayout.getPaddingRight(), verticalPadding);
        MaterialEditTextGroup editTextGroup = (MaterialEditTextGroup) frameLayout.getChildAt(0);
        MaterialEditText editText = editTextGroup.getEditText();
        editText.setLineDisabled(false);
        editText.setPadding(0, editText.getPaddingTop(), 0, editText.getPaddingBottom());

        int startMargin = Screen.dp(58f);
        if (Lang.rtl()) {
          Views.setRightMargin(editTextGroup, startMargin);
        } else {
          Views.setLeftMargin(editTextGroup, startMargin);
        }
        ImageView iconView = new ImageView(frameLayout.getContext());
        iconView.setColorFilter(Theme.iconColor());
        addThemeFilterListener(iconView, ColorId.icon);
        iconView.setId(android.R.id.icon);
        iconView.setPadding(Screen.dp(2f), 0, Screen.dp(2f), 0);
        frameLayout.addView(iconView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Lang.gravity(Gravity.CENTER_VERTICAL)));
      }
      return holder;
    }

    @Override
    protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
      if (item.getId() == R.id.btn_inviteLinkName) {
        editText.setEmptyHint(R.string.ChatFolderInviteLinkNameHint);
        editText.setText(inviteLinkName);
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        ImageView iconView = parent.findViewById(android.R.id.icon);
        iconView.setImageResource(item.getIconResource());
      }
    }

    @Override
    protected void modifyDescription (ListItem item, TextView textView) {
      if (item.getId() == R.id.edit_description) {
        int selectedChatCount = selectedChatIds.size();
        if (selectedChatCount > 0) {
          textView.setText(Lang.pluralBold(R.string.ChatFolderInviteLinkInfo, selectedChatCount, chatFolderName));
        } else {
          textView.setText(Lang.getStringBold(R.string.ChatFolderInviteLinkInfoNoCounter, chatFolderName));
        }
      }
    }

    @Override
    protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
      if (item.getId() == R.id.btn_inviteLink) {
        view.setCenterIcon(true);
        view.setData(Lang.getString(R.string.FolderLink));
      } else {
        view.setCenterIcon(false);
      }
    }

    @Override
    protected void setHeaderText (ListItem item, TextView view, boolean isUpdate) {
      if (item.getId() == headerId) {
        if (chatIds != null && chatIds.length > 0) {
          Views.setMediumText(view, Lang.getString(R.string.xOfYChatsSelected, selectedChatIds.size(), chatIds.length));
        } else {
          view.setText(null);
        }
      } else {
        super.setHeaderText(item, view, isUpdate);
      }
    }

    @Override
    protected void setHeaderCheckBoxState (ListItem item, CheckBoxView checkBox, boolean isUpdate) {
      if (item.getId() == headerId) {
        checkBox.setChecked(!selectedChatIds.isEmpty(), isUpdate);
        checkBox.setDisabled(shareableChatIds.isEmpty(), isUpdate);
        checkBox.setPartially(selectedChatIds.size() < shareableChatIds.size(), isUpdate);
      } else {
        super.setHeaderCheckBoxState(item, checkBox, isUpdate);
      }
    }

    @Override
    protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
      if (item.getId() == R.id.chat) {
        long chatId = item.getLongId();
        DoubleTextWrapper chat = (DoubleTextWrapper) item.getData();
        chat.setDrawCrossIcon(!shareableChatIds.has(chatId));
        chat.setIsChecked(selectedChatIds.has(chatId), isUpdate);
      }
    }
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    if (id == R.id.btn_inviteLinkName) {
      inviteLinkName = v.getText().toString();
      updateDoneButton();
    }
  }

  private boolean isNoChatsToShare () {
    return inviteLink == null;
  }

  private static class ChatIdSet extends LongSet {
    @Override
    public boolean equals (@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj instanceof ChatIdSet) {
        return set.equals(((ChatIdSet) obj).set);
      }
      if (obj instanceof Set) {
        return set.equals(obj);
      }
      return false;
    }
  }
}
