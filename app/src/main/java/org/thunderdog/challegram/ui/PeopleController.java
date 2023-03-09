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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibContactManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.ArrayUtils;

public class PeopleController extends RecyclerViewController<PeopleController.Args> implements View.OnClickListener, Client.ResultHandler, TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, org.thunderdog.challegram.navigation.Menu, TdlibContactManager.Listener {
  public static final int MODE_CONTACTS = 0;
  public static final int MODE_DISCUSSION_GROUPS = 1;

  public static class Args {
    public int mode;
    public GroupSelectListener groupSelectListener;

    public Args (int mode) {
      this.mode = mode;
    }

    public Args setGroupSelectListener (GroupSelectListener listener) {
      this.groupSelectListener = listener;
      return this;
    }
  }

  private int mode = MODE_CONTACTS;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args != null ? args.mode : MODE_CONTACTS;
    this.groupListener = args != null ? args.groupSelectListener : null;
  }

  public PeopleController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setNeedSearch();
  }

  @Override
  public int getId () {
    return R.id.controller_people;
  }

  @Override
  public CharSequence getName () {
    switch (mode) {
      case MODE_DISCUSSION_GROUPS:
        return Lang.getString(R.string.LinkGroupTitle);
      case MODE_CONTACTS:
      default:
        return Lang.getString(R.string.Contacts);
    }
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.contact: {
            TdlibContactManager.UnregisteredContact contact = (TdlibContactManager.UnregisteredContact) item.getData();
            userView.setContact(contact);
            break;
          }
          case R.id.user: {
            TGUser user = (TGUser) item.getData();
            userView.setUser(user);
            break;
          }
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }
    };
    buildCells();

    recyclerView.setAdapter(adapter);

    switch (mode) {
      case MODE_CONTACTS:
        tdlib.cache().addGlobalUsersListener(this);
        tdlib.searchContacts(null, ContactsController.DISPLAY_LIMIT, this);
        tdlib.contacts().addListener(this);
        break;
      case MODE_DISCUSSION_GROUPS:
        tdlib.client().send(new TdApi.GetSuitableDiscussionChats(), this);
        break;
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeGlobalUsersListener(this);
    tdlib.contacts().removeListener(this);
  }

  @Override
  protected int getMenuId () {
    switch (mode) {
      case MODE_DISCUSSION_GROUPS:
        return R.id.menu_search;
      case MODE_CONTACTS:
      default:
        return R.id.menu_people;
    }
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_people: {
        header.addButton(menu, R.id.menu_btn_addContact, R.drawable.baseline_person_add_24, getHeaderIconColorId(), this, Screen.dp(49f));
        header.addSearchButton(menu, this);
        break;
      }
      default: {
        super.fillMenuItems(id, header, menu);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_addContact: {
        PhoneController c = new PhoneController(context, tdlib);
        c.setMode(PhoneController.MODE_ADD_CONTACT);
        navigateTo(c);
        break;
      }
      default: {
        super.onMenuItemPressed(id, view);
        break;
      }
    }
  }

  /*@Override
  protected boolean needChatSearchManagerPreparation () {
    return mode != MODE_DISCUSSION_GROUPS;
  }*/

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (TD.isContact(user)) {
        int i = indexOfUser(user.id);
        if (i != -1) {
          adapter.updateUserViewByLongId(user.id, false);
        } else {
          addUser(user);
        }
      } else {
        removeUser(user.id);
      }
    });
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) { }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @Override
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    int oldIndex = indexOfUser(userId);
    if (oldIndex == -1) {
      return;
    }
    int adapterPosition = adapter.updateUserViewByLongId(userId, true);
    if (uiOnly || users.size() == 1) {
      return;
    }

    boolean fromEnd = oldIndex == users.size() - 1;

    TGUser parsedUser = users.remove(oldIndex);
    int newIndex = Collections.binarySearch(users, parsedUser, tdlib.userProviderComparator());
    if (newIndex >= 0) { // Error
      users.add(oldIndex, parsedUser);
      return;
    }

    newIndex = newIndex * -1 - 1;

    if (newIndex == oldIndex) {
      users.add(oldIndex, parsedUser);
      return;
    }

    ListItem userItem = adapter.getItems().remove(adapterPosition);
    ListItem separator;

    if (fromEnd) {
      separator = adapter.getItems().remove(adapterPosition - 1); // separator at the top of the user view
      adapter.notifyItemRangeRemoved(adapterPosition - 1, 2);
    } else {
      separator = adapter.getItems().remove(adapterPosition); // separator at the bottom of the user view
      adapter.notifyItemRangeRemoved(adapterPosition, 2);
    }

    addUser(newIndex, parsedUser, userItem, separator);
  }

  private void removeUser (long userId) {
    int i = indexOfUser(userId);
    if (i != -1) {
      removeUserByPosition(i);
    }
  }

  private void removeUserByPosition (int position) {
    users.remove(position);
    if (users.isEmpty()) {
      if (unregisteredCount > 0) {
        adapter.removeRange(1, 4); // header, top shadow, user, bottom shadow
      } else {
        buildContactCells();
      }
      return;
    }
    int index = adapter.indexOfViewById(R.id.btn_contactsRegistered);
    if (index == -1) {
      throw new IllegalStateException();
    }
    index += 2;
    if (position == users.size()) {
      adapter.removeRange(index + position * 2 - 1, 2);
    } else {
      adapter.removeRange(index + position * 2, 2);
    }
  }

  private void addUser (TdApi.User user) {
    if (users == null) {
      return;
    }

    TGUser parsedUser = new TGUser(tdlib, user);
    if (users.isEmpty()) {
      users.add(parsedUser);
      if (unregisteredCount > 0) {
        List<ListItem> out = adapter.getItems();
        ArrayUtils.ensureCapacity(out, out.size() + 4);
        out.add(1, new ListItem(ListItem.TYPE_HEADER, R.id.btn_contactsRegistered, 0, makeContactCounter(), false));
        out.add(2, new ListItem(ListItem.TYPE_SHADOW_TOP));
        out.add(3, new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(parsedUser.getUserId()).setData(parsedUser));
        out.add(4, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.notifyItemRangeInserted(1, 4);
      } else {
        buildContactCells();
      }
      return;
    }

    int newIndex = Collections.binarySearch(users, parsedUser, tdlib.userProviderComparator());
    if (newIndex >= 0) {
      return;
    }
    newIndex = newIndex * -1 - 1;
    addUser(newIndex, parsedUser, null, null);
  }

  private void addUser (int newIndex, TGUser user, ListItem userItem, ListItem separator) {
    users.add(newIndex, user);

    if (userItem == null) {
      userItem = new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(user.getUserId()).setData(user);
    }
    if (separator == null) {
      separator = new ListItem(ListItem.TYPE_SEPARATOR);
    }

    boolean toEnd = newIndex == users.size() - 1;
    int startIndex = adapter.indexOfViewById(R.id.btn_contactsRegistered);
    if (startIndex == -1) {
      throw new IllegalStateException();
    }
    startIndex += 2;

    if (toEnd) {
      adapter.getItems().add(startIndex + newIndex * 2 - 1, userItem);
      adapter.getItems().add(startIndex + newIndex * 2 - 1, separator);
      adapter.notifyItemRangeInserted(startIndex + newIndex * 2, 2);
    } else {
      adapter.getItems().add(startIndex + newIndex * 2, separator);
      adapter.getItems().add(startIndex + newIndex * 2, userItem);
      adapter.notifyItemRangeInserted(startIndex + newIndex * 2, 2);
    }
  }

  private int indexOfUser (long userId) {
    if (users != null) {
      int i = 0;
      for (TGUser user : users) {
        if (user.getUserId() == userId) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  public interface GroupSelectListener {
    boolean onGroupSelected (PeopleController context, TGFoundChat chat);
  }

  private GroupSelectListener groupListener;

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    switch (mode) {
      case MODE_CONTACTS:
        break;
      case MODE_DISCUSSION_GROUPS:
        return groupListener != null && groupListener.onGroupSelected(this, chat);
    }
    return super.onFoundChatClick(view, chat);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.user: {
        TGUser user = (TGUser) ((ListItem) v.getTag()).getData();
        tdlib.ui().openPrivateChat(this, user.getUserId(), null);
        break;
      }
      case R.id.chat: {
        TGFoundChat chat = (TGFoundChat) ((ListItem) v.getTag()).getData();
        if (groupListener == null || !groupListener.onGroupSelected(this, chat)) {
          tdlib.ui().openChat(this, chat.getChatId(), null);
        }
        break;
      }
      case R.id.contact: {
        final TdlibContactManager.UnregisteredContact contact = (TdlibContactManager.UnregisteredContact) ((ListItem) v.getTag()).getData();
        if (contact.importerCount == 1000) {
          Intents.sendSms(contact.contact.phoneNumber, Lang.getString(R.string.InviteTextCommonOverThousand, BuildConfig.DOWNLOAD_URL));
        } else if (contact.importerCount > 1) {
          Intents.sendSms(contact.contact.phoneNumber, Lang.plural(R.string.InviteTextCommonMany, contact.importerCount, BuildConfig.DOWNLOAD_URL));
        } else {
          tdlib.cache().getInviteText(result -> {
            if (!isDestroyed()) {
              Intents.sendSms(contact.contact.phoneNumber, result.text);
            }
          });
        }
        break;
      }
    }
  }

  private void buildCells () {
    switch (mode) {
      case MODE_CONTACTS:
        buildContactCells();
        break;
      case MODE_DISCUSSION_GROUPS:
        buildChatCells();
        break;
    }
  }


  private String makeContactCounter () {
    return Lang.plural(R.string.xContacts, tdlib.contacts().getRegisteredCount());
  }

  private int unregisteredCount;

  private void buildContactCells () {
    unregisteredCount = 0;

    if (users == null) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_PROGRESS)
      }, false);
      return;
    }

    ArrayList<TdlibContactManager.UnregisteredContact> unregisteredContacts = tdlib.contacts().getUnregisteredContacts();
    final int unregisteredCount = unregisteredContacts != null ? unregisteredContacts.size() : 0;
    final int registeredCount = users.size();

    final int totalContactCount = registeredCount + unregisteredCount;

    if (totalContactCount == 0) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NoContacts)
      }, false);
      executeScheduledAnimation();
      return;
    }

    int cellCount;
    if (registeredCount == 0 || unregisteredCount == 0) {
      cellCount = totalContactCount * 2 + 3;
    } else {
      cellCount = registeredCount * 2 + 3 + unregisteredCount * 2 + 2;
    }

    ArrayList<ListItem> items = new ArrayList<>(cellCount);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    ListItem separator = new ListItem(ListItem.TYPE_SEPARATOR);

    if (registeredCount > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, R.id.btn_contactsRegistered, 0, makeContactCounter(), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TGUser user : users) {
        if (first) {
          first = false;
        } else {
          items.add(separator);
        }
        items.add(new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(user.getUserId()).setData(user));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (unregisteredCount > 0) {
      this.unregisteredCount = unregisteredCount;
      fillUnregisteredCells(items, separator, unregisteredContacts);
    }

    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  private static int fillUnregisteredCells (List<ListItem> items, ListItem separator, ArrayList<TdlibContactManager.UnregisteredContact> unregisteredContacts) {
    int startSize = items.size();
    items.add(new ListItem(ListItem.TYPE_HEADER, R.id.btn_contactsUnregistered, 0, R.string.InviteFriends));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (TdlibContactManager.UnregisteredContact contact : unregisteredContacts) {
      if (first) {
        first = false;
      } else {
        items.add(separator);
      }
      items.add(new ListItem(ListItem.TYPE_USER, R.id.contact).setData(contact));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items.size() - startSize;
  }

  private List<TGUser> users;

  private void setUsers (ArrayList<TGUser> users) {
    this.users = users;
    buildContactCells();
    removeItemAnimatorDelayed();
  }

  private void buildChatCells () {
    if (chats == null) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_PROGRESS)
      }, false);
      return;
    }

    if (chats.isEmpty()) {
      adapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NoCommentChats)
      }, false);
      executeScheduledAnimation();
      return;
    }

    ArrayList<ListItem> items = new ArrayList<>(3 + chats.size() * 2);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.plural(R.string.xChats, chats.size()), false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (TGFoundChat chat : chats) {
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(searchValueOf(R.id.chat, chat, false));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  private List<TGFoundChat> chats;

  private void setChats (List<TGFoundChat> chats) {
    this.chats = chats;
    buildChatCells();
    removeItemAnimatorDelayed();
    invalidateChatSearchResults();
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Users.CONSTRUCTOR: {
        TdApi.Users result = (TdApi.Users) object;
        long[] userIds = result.userIds;
        ArrayList<TdApi.User> rawUsers = tdlib.cache().users(userIds);
        final ArrayList<TGUser> users = new ArrayList<>(userIds.length);
        long myUserId = tdlib.myUserId();
        for (TdApi.User user : rawUsers) {
          if (user.id == myUserId) {
            continue;
          }
          TdApi.User cachedUser = tdlib.cache().user(user.id);
          if (cachedUser != null) {
            TGUser parsedUser = new TGUser(tdlib, cachedUser);
            int i = Collections.binarySearch(users, parsedUser, tdlib.userProviderComparator());
            if (i < 0) {
              users.add(i * -1 - 1, parsedUser);
            }
          }
        }
        tdlib.ui().post(() -> setUsers(users));
        break;
      }
      case TdApi.Chats.CONSTRUCTOR: {
        List<TdApi.Chat> chats = tdlib.chats(((TdApi.Chats) object).chatIds);
        List<TGFoundChat> foundChats = new ArrayList<>(chats.size());
        for (TdApi.Chat chat : chats) {
          foundChats.add(new TGFoundChat(tdlib, null, chat, false, null).setForceUsername());
        }
        tdlib.ui().post(() -> setChats(foundChats));
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        break;
      }
    }
  }

  @Override
  protected void modifyFoundChat (TGFoundChat chat) {
    switch (mode) {
      case MODE_DISCUSSION_GROUPS:
        chat.setForceUsername();
        break;
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    // TODO backward
    return true;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 200l;
  }

  @Override
  protected int getChatSearchFlags () {
    switch (mode) {
      case MODE_DISCUSSION_GROUPS:
        return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_TOP_SEARCH_CATEGORY_GROUPS | SearchManager.FLAG_NEED_GLOBAL_SEARCH | SearchManager.FLAG_CUSTOM_FILTER;
      case MODE_CONTACTS:
      default:
        return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_USERS | SearchManager.FLAG_ONLY_CONTACTS | SearchManager.FLAG_NO_BOTS | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
    }
  }

  @Override
  protected boolean filterChatSearchResult (TdApi.Chat chat) {
    List<TGFoundChat> discussionChats = this.chats;
    if (discussionChats != null) {
      for (TGFoundChat discussionChat : discussionChats) {
        if (discussionChat.getChatId() == chat.id) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected int getSearchHint () {
    switch (mode) {
      case MODE_DISCUSSION_GROUPS:
        return R.string.Search;
      case MODE_CONTACTS:
      default:
        return R.string.SearchPeople;
    }
  }

  // Contacts updates

  @Override
  public void onRegisteredContactsChanged (long[] userIds, int totalCount, boolean newArrival) {
    if (adapter != null) {
      int i = adapter.indexOfViewById(R.id.btn_contactsRegistered);
      if (i != -1) {
        adapter.getItems().get(i).setString(makeContactCounter());
        adapter.updateValuedSettingByPosition(i);
      }
    }
  }

  @Override
  public void onUnregisteredContactsChanged (int oldTotalCount, ArrayList<TdlibContactManager.UnregisteredContact> contacts, int totalCount) {
    if (adapter != null) {
      int headerIndex = adapter.indexOfViewById(R.id.btn_contactsUnregistered);
      boolean hadUnregistered = headerIndex != -1;
      boolean hasUnregistered = totalCount > 0;
      if (hadUnregistered != hasUnregistered) {
        if (users != null && !users.isEmpty()) {
          if (hasUnregistered) {
            int itemCount = 2 + totalCount * 2;
            List<ListItem> out = adapter.getItems();
            ArrayUtils.ensureCapacity(out, out.size() + itemCount);
            int startIndex = out.size();
            int count = fillUnregisteredCells(out, new ListItem(ListItem.TYPE_SEPARATOR), contacts);
            adapter.notifyItemRangeInserted(startIndex, count);
          } else {
            adapter.removeRange(headerIndex, 2 + unregisteredCount * 2);
          }
        } else {
          buildContactCells();
        }
      } else if (hasUnregistered) {
        headerIndex += 2; // Skip header and top shadow
        if (totalCount == unregisteredCount) {
          int shift = 0;
          for (TdlibContactManager.UnregisteredContact contact : contacts) {
            adapter.getItems().get(headerIndex + shift).setData(contact);
            shift += 2;
          }
          adapter.notifyItemRangeChanged(headerIndex, totalCount * 2 - 1);
        } else {
          int shift = 0;
          for (int i = 0; i < Math.min(unregisteredCount, totalCount); i++) {
            adapter.getItems().get(headerIndex + shift).setData(contacts.get(i));
            shift += 2;
          }
          adapter.notifyItemRangeChanged(headerIndex, shift - 1);
          headerIndex += shift - 1;
          if (totalCount < unregisteredCount) {
            adapter.removeRange(headerIndex, (unregisteredCount - totalCount) * 2 - 1);
          } else {
            ListItem separator = new ListItem(ListItem.TYPE_SEPARATOR);
            List<ListItem> out = adapter.getItems();
            ArrayUtils.ensureCapacity(out, out.size() + (totalCount - unregisteredCount) * 2);
            int index = headerIndex;
            for (int i = unregisteredCount; i < totalCount; i++) {
              TdlibContactManager.UnregisteredContact contact = contacts.get(i);
              out.add(index++, separator);
              out.add(index++, new ListItem(ListItem.TYPE_USER, R.id.contact).setData(contact));
            }
            adapter.notifyItemRangeInserted(headerIndex, (totalCount - unregisteredCount) * 2);
          }
        }
      }
      this.unregisteredCount = totalCount;
    }
  }
}
