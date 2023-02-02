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
 *
 * File created on 16/08/2015 at 15:57
 */
package org.thunderdog.challegram.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.user.BubbleHeaderView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.util.UserPickerMultiDelegate;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SectionedRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class ContactsController extends TelegramViewController<ContactsController.Args> implements OptionDelegate, BubbleHeaderView.Callback, TextWatcher, Runnable, Menu, Unlockable,
  TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, Comparator<TdApi.User> {
  public static final int MODE_PICK = 1;
  public static final int MODE_CHANNEL_MEMBERS = 2;
  public static final int MODE_NEW_GROUP = 3;
  public static final int MODE_NEW_CHAT = 4;
  public static final int MODE_IMPORT = 5;
  public static final int MODE_NEW_SECRET_CHAT = 6;
  public static final int MODE_MULTI_PICK = 7;
  public static final int MODE_CALL = 8;
  // public static final int MODE_CONTACTS = 4; // TODO
  public static final int MODE_ADD_MEMBER = 10;

  public static final int SOURCE_TYPE_REMOTE = 0;
  public static final int SOURCE_TYPE_LOCAL = 1;
  public static final int SOURCE_TYPE_GMAIL = 2;

  public static class Args {
    SenderPickerDelegate delegate;
    UserPickerMultiDelegate multiDelegate;
    boolean useGlobalSearch;
    int globalSearchFlags;

    public Args (SenderPickerDelegate delegate) {
      this.delegate = delegate;
    }

    public Args (UserPickerMultiDelegate multiDelegate) {
      this.multiDelegate = multiDelegate;
    }

    public Args useGlobalSearch (int flags) {
      this.useGlobalSearch = true;
      this.globalSearchFlags = flags;
      return this;
    }
  }

  public ContactsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private FrameLayoutFix contentView;
  private SectionedRecyclerView recyclerView;
  private View spinnerView;
  private TextView emptyView;

  private SenderPickerDelegate delegate;
  private UserPickerMultiDelegate multiDelegate;
  private TGUser[] users;
  private ContactsAdapter adapter;
  private HeaderEditText searchView;
  private @Nullable BubbleHeaderView headerCell;
  private DoubleHeaderView addMemberHeaderView;

  private TdApi.MessageSender pickedSenderId;
  private List<TGUser> pickedChats;

  private int mode;
  private int sourceType;
  private TdApi.Chat chat;

  public void initWithMode (int mode) {
    this.mode = mode;
    switch (mode) {
      case MODE_PICK:
      case MODE_NEW_CHAT:
      case MODE_CALL:
      case MODE_ADD_MEMBER:
      case MODE_NEW_SECRET_CHAT: {
        break;
      }
      case MODE_MULTI_PICK:
      case MODE_CHANNEL_MEMBERS:
      case MODE_NEW_GROUP:
      case MODE_IMPORT: {
        if (mode == MODE_MULTI_PICK) {
          long[] chatIds = multiDelegate.getAlreadySelectedChatIds();
          pickedChats = new ArrayList<>(chatIds != null ? chatIds.length : 10);
          if (chatIds != null) {
            for (long chatId : chatIds) {
              long userId = ChatId.toUserId(chatId);
              if (userId != 0) {
                TdApi.User user = tdlib.cache().user(userId);
                if (user != null) {
                  pickedChats.add(new TGUser(tdlib, user));
                }
              } else {
                TdApi.Chat chat = tdlib.chat(chatId);
                if (chat != null) {
                  pickedChats.add(new TGUser(tdlib, chat));
                }
              }
            }
          }
        } else {
          pickedChats = new ArrayList<>(10);
        }
        break;
      }
    }
  }

  private CreateGroupController.Callback groupCreationCallback;

  public void setGroupCreationCallback (CreateGroupController.Callback groupCreationCallback) {
    this.groupCreationCallback = groupCreationCallback;
  }

  private boolean allowBots, allowChats, allowChannels;

  public void setAllowBots (boolean allowBots) {
    this.allowBots = allowBots;
  }

  public void setAllowChats (boolean allowChats, boolean allowChannels) {
    this.allowChats = allowChats;
    this.allowChannels = allowChannels;
  }

  public void setChat (TdApi.Chat chat) {
    this.chat = chat;
  }

  public void setSourceType (int sourceType) {
    this.sourceType = sourceType;
  }

  private ImportContactsCallback importCallback;

  public interface ImportContactsCallback {
    void onContactsImported (TdApi.Users contacts);
  }

  public void setImportCallback (ImportContactsCallback callback) {
    this.importCallback = callback;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    int desiredMode = 0;
    if (args.delegate != null) {
      delegate = args.delegate;
      desiredMode = MODE_PICK;
    } else if (args.multiDelegate != null) {
      multiDelegate = args.multiDelegate;
      desiredMode = MODE_MULTI_PICK;
    }
    if (mode == 0 && desiredMode != 0) {
      initWithMode(desiredMode);
    }
  }

  /*@Override
  public void setArguments (UserPickerDelegate args) {
    super.setArguments(args);
    delegate = args;
    initWithMode(MODE_PICK);
  }*/

  @Override
  public int getId () {
    return R.id.controller_contacts;
  }

  private int titleRes;
  private String chatTitle;

  public void setChatTitle (int addStringRes, String chatTitle) {
    this.titleRes = addStringRes;
    this.chatTitle = chatTitle;
  }

  private boolean hasBubbles () {
    return mode == MODE_CHANNEL_MEMBERS || mode == MODE_NEW_GROUP || mode == MODE_MULTI_PICK;
  }

  private boolean canSelectContacts () {
    return mode == MODE_CHANNEL_MEMBERS || mode == MODE_NEW_GROUP || mode == MODE_IMPORT || mode == MODE_MULTI_PICK;
  }

  @Override
  public CharSequence getName () {
    if (mode == MODE_PICK && delegate != null && delegate.allowGlobalSearch()) {
      return delegate.getUserPickTitle();
    }
    return super.getName();
  }

  private boolean canAddContacts () {
    return mode == MODE_NEW_CHAT || mode == MODE_CALL;
  }

  @SuppressWarnings("ResourceType")
  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);

    recyclerView = new SectionedRecyclerView(context);
    recyclerView.setSectionedAdapter(adapter = new ContactsAdapter(recyclerView, this));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          hideSoftwareKeyboard();
        }
      }
    });
    addThemeInvalidateListener(recyclerView);

    contentView.addView(recyclerView);

    if (mode == MODE_ADD_MEMBER) {
      addMemberHeaderView = new DoubleHeaderView(context);
      addMemberHeaderView.setThemedTextColor(this);
      addMemberHeaderView.initWithMargin(Screen.dp(49f), true);
      addMemberHeaderView.setTitle(titleRes);
      addMemberHeaderView.setSubtitle(chatTitle);
    } else if (hasBubbles()) {
      headerCell = new BubbleHeaderView(context);
      headerCell.setHint(bindLocaleChanger(mode == MODE_MULTI_PICK ? multiDelegate.provideMultiUserPickerHint() : R.string.SendMessageTo, headerCell.getInput(), true, false));
      headerCell.setCallback(this);
      if (pickedChats != null && pickedChats.size() > 0) {
        headerCell.forceUsers(pickedChats);
        headerOffset = headerCell.getCurrentWrapHeight();
        recyclerView.setTranslationY(headerOffset);
        ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = headerOffset;
      }
    } else if (mode != MODE_PICK || (delegate != null && !delegate.allowGlobalSearch())) {
      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize());

      if (Lang.rtl()) {
        params.rightMargin = Screen.dp(68f);
        params.leftMargin = canAddContacts() ? Screen.dp(49f) : 0;
      } else {
        params.leftMargin = Screen.dp(68f);
        params.rightMargin = canAddContacts() ? Screen.dp(49f) : 0;
      }

      searchView = HeaderEditText.create(UI.getContext(context).navigation().getHeaderView(), false, this);
      searchView.setPadding(Screen.dp(5f), 0, Screen.dp(5f), 0);
      searchView.setHint(Lang.getString(bindLocaleChanger(mode == MODE_NEW_SECRET_CHAT ? R.string.NewSecretChatWith : R.string.Search, searchView, true, false)));
      searchView.addTextChangedListener(this);
      searchView.setLayoutParams(params);
    }

    if (needChatSearch()) {
      RecyclerView recyclerView = generateChatSearchView(contentView);
      if (pickedChats != null && pickedChats.size() > 0) {
        recyclerView.setTranslationY(headerOffset);
        ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = headerOffset;
      }
    }

    // Now let's talk about the data

    loadContacts();

    return contentView;
  }

  private boolean needChatSearch () {
    return (getArguments() != null && getArguments().useGlobalSearch) || mode == MODE_NEW_GROUP || mode == MODE_CHANNEL_MEMBERS || mode == MODE_ADD_MEMBER || (mode == MODE_PICK && delegate != null && delegate.allowGlobalSearch());
  }

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    searchUser(s.toString());
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

  @Override
  public void afterTextChanged (Editable s) { }

  // View

  private void showProgress () {
    if (spinnerView == null) {
      spinnerView = Views.simpleProgressView(context());
      contentView.addView(spinnerView);
    } else if (spinnerView.getParent() == null) {
      spinnerView.setVisibility(View.VISIBLE);
      contentView.addView(spinnerView);
    }
  }

  private void hideProgress () {
    if (spinnerView != null && spinnerView.getParent() != null) {
      spinnerView.setVisibility(View.GONE);
      contentView.removeView(spinnerView);
    }
  }

  private boolean showingEmptyView;

  private void showEmptyView () {
    this.showingEmptyView = true;
    if (emptyView == null) {
      emptyView = new NoScrollTextView(context());
      emptyView.setText(Lang.getString(R.string.NoContacts));
      emptyView.setTextColor(0xff8a8a8a);
      emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      emptyView.setTypeface(Fonts.getRobotoRegular());
      emptyView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
      if (isChatSearchOpen()) {
        emptyView.setVisibility(View.GONE);
      }
      contentView.addView(emptyView);
    } else if (emptyView.getParent() == null) {
      emptyView.setVisibility(isChatSearchOpen() ? View.GONE : View.VISIBLE);
      contentView.addView(emptyView);
    }
  }

  private void hideEmptyView () {
    this.showingEmptyView = false;
    if (emptyView != null && emptyView.getParent() != null) {
      emptyView.setVisibility(View.GONE);
      contentView.removeView(emptyView);
    }
  }

  // Click listener

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    if (pickedSenderId != null && delegate != null && id != R.id.btn_cancel) {
      delegate.onSenderConfirm(this, pickedSenderId, id);
      navigateBack();
    } else switch (id) {
      case R.id.btn_newContact: {
        createContact();
        break;
      }
      case R.id.btn_localContacts: {
        importContacts(SOURCE_TYPE_LOCAL);
        break;
      }
      case R.id.btn_gmailContacts: {
        importContacts(SOURCE_TYPE_GMAIL);
        break;
      }
    }
    return true;
  }

  public void onClick (TGUser u, View v) {
    switch (mode) {
      case MODE_CHANNEL_MEMBERS:
      case MODE_NEW_GROUP:
      case MODE_IMPORT:
      case MODE_MULTI_PICK: {
        selectUser(u, (UserView) v);
        break;
      }
      case MODE_NEW_SECRET_CHAT: {
        hideSoftwareKeyboard();
        tdlib.ui().startSecretChat(this, u.getUserId(), false, null);
        break;
      }
      case MODE_NEW_CHAT:
      case MODE_CALL:
      case MODE_PICK:
      default: {
        hideSoftwareKeyboard();
        if (delegate != null) {
          if (delegate.onSenderPick(this, v, pickedSenderId = u.getSenderId())) {
            navigateBack();
          }
        } else if (mode == MODE_CALL) {
          tdlib.context().calls().makeCall(this, u.getUserId(), null);
        } else {
          tdlib.ui().openPrivateChat(this, u.getUserId(), null);
        }
        break;
      }
    }
  }

  // Setup

  @Override
  protected int getMenuId () {
    return mode == MODE_ADD_MEMBER ? R.id.menu_search : canAddContacts() ? R.id.menu_contacts : mode == MODE_PICK && delegate != null && delegate.allowGlobalSearch() ? R.id.menu_search : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search:
        header.addSearchButton(menu, this, getHeaderIconColorId());
        break;
      case R.id.menu_contacts:
        header.addButton(menu, R.id.menu_btn_addContact, R.drawable.baseline_person_add_24, getHeaderIconColorId(), this, Screen.dp(49f));
        break;
    }
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hideList(searchView, headerCell == null ? null : headerCell.getInput());
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_addContact: {
        if (users != null) {
          createContact();
          // showOptions(new int[] {R.id.btn_newContact, R.id.btn_localContacts, R.id.btn_gmailContacts}, new String[] {UI.getString(R.string.NewContact), UI.getString(R.string.ImportContacts), UI.getString(R.string.ImportGmailContacts)}, null, new int[] {R.drawable.ic_person_add_gray, R.drawable.ic_contact_gray, R.drawable.ic_mail_gray});
        }
        break;
      }
      case R.id.menu_btn_search: {
        openSearchMode();
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    if (delegate != null) {
      delegate.onSenderPick(this, view, pickedSenderId = chat.getSenderId());
      return true;
    }
    if (canSelectContacts()) {
      long chatId = chat.getAnyId();
      long userId = chat.getUserId();
      TGUser user = userId != 0 ? new TGUser(tdlib, tdlib.cache().userStrict(userId)) : new TGUser(tdlib, tdlib.chatStrict(chatId));
      if (isSelected(user) || selectUser(user, null)) {
        headerCell.getSearchInput().setText("");
        return true;
      }
      return false;
    }
    // TODO if needed
    return super.onFoundChatClick(view, chat);
  }

  @Override
  protected boolean canInteractWithFoundChat (TGFoundChat chat) {
    return false;
  }

  @Override
  protected int getChatSearchFlags () {
    return (getArguments() != null && getArguments().useGlobalSearch) ? getArguments().globalSearchFlags : SearchManager.FILTER_INVITE | (allowBots || mode == MODE_CHANNEL_MEMBERS || mode == MODE_NEW_GROUP ? 0 : SearchManager.FLAG_NO_BOTS) | (!allowChats && !allowChannels ? SearchManager.FLAG_ONLY_USERS : allowChats && allowChannels ? 0 : allowChats ? SearchManager.FLAG_NO_CHANNELS : SearchManager.FLAG_NO_GROUPS);
  }

  private void createContact () {
    PhoneController c = new PhoneController(context, tdlib);
    c.setMode(PhoneController.MODE_ADD_CONTACT);
    navigateTo(c);
  }

  private void importContacts (int sourceType) {
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(MODE_IMPORT);
    c.setSourceType(sourceType);
    c.setImportCallback(createImportCallback());
    navigateTo(c);
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return headerCell == null || !headerCell.areBubblesAnimating();
  }

  @Override
  public View getCustomHeaderCell () {
    return mode == MODE_ADD_MEMBER ? addMemberHeaderView : hasBubbles() ? headerCell : searchView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return hasBubbles() ? null : recyclerView;
  }

  @Override
  protected boolean useDropPlayer () {
    return !hasBubbles();
  }

  @Override
  protected View getSearchAntagonistView () {
    return recyclerView;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getFloatingButtonId () {
    return mode == MODE_ADD_MEMBER || mode == MODE_MULTI_PICK ? 0 : mode == MODE_PICK || mode == MODE_NEW_CHAT || mode == MODE_CALL || mode == MODE_NEW_SECRET_CHAT || pickedChats.size() == 0 ? 0 : mode == MODE_CHANNEL_MEMBERS || mode == MODE_MULTI_PICK ? R.drawable.baseline_check_24 : R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    switch (mode) {
      case MODE_CHANNEL_MEMBERS: {
        createChannel();
        break;
      }
      case MODE_NEW_GROUP: {
        createGroup();
        break;
      }
    }
  }

  private void createChannel () {
    int size = pickedChats.size();

    if (size == 0 || creatingChat) {
      return;
    }

    setStackLocked(true);
    creatingChat = true;

    long[] userIds = new long[size];
    for (int i = 0; i < size; i++) {
      userIds[i] = pickedChats.get(i).getUserId();
    }

    tdlib.client().send(new TdApi.AddChatMembers(chat.id, userIds), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          UI.unlock(ContactsController.this);
          tdlib.ui().openChat(ContactsController.this, chat, null);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          UI.unlock(ContactsController.this);
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(object, TdApi.AddChatMembers.class, TdApi.Ok.class);
          UI.unlock(ContactsController.this);
          break;
        }
      }
    });
  }

  @Override
  public void unlock () {
    creatingChat = false;
    setStackLocked(false);
  }

  private boolean creatingChat;

  private void createGroup () {
    int size = pickedChats.size();

    if (size == 0 || creatingChat) {
      return;
    }

    setStackLocked(true);
    creatingChat = true;

    final ArrayList<TGUser> users = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      users.add(pickedChats.get(i));
    }

    Background.instance().post(() -> {
      Collections.sort(users, (lhs, rhs) -> {
        int x, y;
        // noinspection SpellCheckingInspection
        TdApi.User luser = lhs.getUser(), ruser = rhs.getUser();
        if (luser == null || ruser == null) {
          return 0;
        }
        x = TD.getLastSeen(luser);
        y = TD.getLastSeen(ruser);
        return x == y ? 0 : x > y ? -1 : 1;
      });
      tdlib.ui().post(() -> {
        setStackLocked(false);
        CreateGroupController c = new CreateGroupController(context, tdlib);
        c.setGroupCreationCallback(groupCreationCallback);
        c.setMembers(users);
        navigateTo(c);
        creatingChat = false;
      });
    });
  }

  // Multi-user

  private boolean isSelected (TGUser u) {
    return canSelectContacts() && indexOfSelectedChat(u.getChatId()) >= 0;
  }

  private boolean selectUser (TGUser u, UserView v) {
    if ((headerCell != null && headerCell.areBubblesAnimating()) || creatingChat) {
      return false;
    }
    int viewIndex = -1;
    if (v == null) {
      viewIndex = adapter.indexOfUser(u.getUserId());
      if (viewIndex != -1) {
        v = (UserView) recyclerView.getLayoutManager().findViewByPosition(viewIndex);
        if (v == null)
          viewIndex = -1;
      }
    }
    int selectedIndex = indexOfSelectedChat(u.getChatId());
    if (canSelectContacts() && selectedIndex >= 0) {
      pickedChats.remove(selectedIndex);
      if (v != null)
        v.setChecked(false, true);
      if (hasBubbles()) {
        headerCell.removeUser(u);
      }
      if (pickedChats.size() == 0) {
        if (floatingButton != null) {
          floatingButton.hide();
        }
      }
    } else {
      int nextSize = pickedChats.size() + 1;
      if (mode == MODE_NEW_GROUP) {
        if (nextSize >= tdlib.supergroupMaxSize()) {
          context.tooltipManager().builder(v).show(this, tdlib, R.drawable.baseline_error_24, Lang.pluralBold(R.string.ParticipantXLimitReached, tdlib.supergroupMaxSize()));
          return false;
        }
      }
      pickedChats.add(u);
      if (v != null)
        v.setChecked(true, true);
      if (hasBubbles()) {
        headerCell.addUser(u);
      }
      if (pickedChats.size() == 1) {
        if (floatingButton != null && getFloatingButtonId() != 0) {
          floatingButton.show(this);
        }
      }
    }
    if (adapter.getSearchUserCount() == 1 && headerCell != null) {
      headerCell.clearSearchInput();
      // hideSoftwareKeyboard();
    }
    if (mode == MODE_MULTI_PICK) {
      multiDelegate.onAlreadyPickedChatsChanged(pickedChats);
    }
    if (viewIndex != -1) {
      adapter.notifyItemChanged(viewIndex);
    }
    return true;
  }

  private int headerOffset;

  @Override
  protected int getHeaderHeight () {
    return Size.getHeaderPortraitSize() + headerOffset;
  }

  @Override
  protected int getMaximumHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override
  public void setHeaderOffset (int offset) {
    if (headerOffset != offset) {
      this.headerOffset = offset;
      recyclerView.setTranslationY(offset);
      RecyclerView recyclerView = getChatSearchView();
      if (recyclerView != null) {
        recyclerView.setTranslationY(offset);
      }
      int height = getHeaderHeight();
      if (navigationController != null) {
        navigationController.getHeaderView().setBackgroundHeight(height);
        navigationController.getFloatingButton().updatePosition(height);
      }
    }
  }

  @Override
  public void applyHeaderOffset () {
    ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = (int) recyclerView.getTranslationY();
    recyclerView.requestLayout();
    RecyclerView recyclerView = getChatSearchView();
    if (recyclerView != null) {
      Views.setBottomMargin(recyclerView, (int) recyclerView.getTranslationY());
    }
  }

  @Override
  public void prepareHeaderOffset (int offset) {
    ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = offset;
    recyclerView.requestLayout();
    RecyclerView recyclerView = getChatSearchView();
    if (recyclerView != null) {
      Views.setBottomMargin(recyclerView, offset);
    }
  }

  @Override
  public View getTranslationView () {
    return recyclerView;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    recyclerView.post(this);
  }

  @Override
  public void run () {
    recyclerView.requestLayout();
  }

  private String lastQuery;

  @Override
  public void searchUser (String q) {
    if (lastQuery == null) {
      lastQuery = "";
    }
    if (q.equals(lastQuery))
      return;

    if (needChatSearch()) {
      boolean prevHadSearch = !lastQuery.isEmpty();
      boolean hasSearch = !q.isEmpty();
      lastQuery = q;
      if (prevHadSearch != hasSearch) {
        if (hasSearch) {
          forceOpenChatSearch(q);
          if (showingEmptyView && emptyView != null) {
            emptyView.setVisibility(View.GONE);
          }
        } else {
          forceCloseChatSearch();
          if (showingEmptyView && emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
          }
        }
      } else if (hasSearch) {
        forceSearchChats(q);
      }
    } else if (users != null && users.length > 0) {
      q = Strings.clean(q.trim().toLowerCase());
      if (!q.equals(lastQuery)) {
        searchInternal(q, q.length() > lastQuery.length() && lastQuery.length() > 0 && q.startsWith(lastQuery));
        lastQuery = q;
      }
    }
  }

  /*@Override
  public void onContactImported (final TdApi.User user) {
    Background.instance().post(new Runnable() {
      @Override
      public void run () {
        if (users.length == 0) {
          users = new TGUser[] {new TGUser(user)};
          sortUsers(users, null, false);
        } else {
          TGUser[] newUsers = new TGUser[users.length + 1];
          System.arraycopy(users, 0, newUsers, 0, users.length);
          newUsers[users.length] = new TGUser(user);
          users = newUsers;
          sortUsers(users, null, true);
        }
      }
    });
  }*/

  private static void sortUsers (TGUser[] users) {
    Arrays.sort(users, (l, r) -> l.getSortingKey().compareTo(r.getSortingKey()));
  }

  private void sortUsers (final TGUser[] users, final String q, final boolean resort) {
    if (users == null) {
      return;
    }

    Background.instance().post(() -> {
      if (resort) {
        sortUsers(users);
      }

      ArrayList<TGUser> result = new ArrayList<>();

      int sectionCount = 0;
      int[] sections = new int[Math.min(15, users.length)];
      String[] letters = new String[sections.length];

      int section = 0;
      String prev = null;

      for (TGUser user : users) {
        if (user == null) {
          Log.critical("ContactsController::sortUsers: TGUser is null");
          continue;
        }
        String firstName = Strings.clean(user.getFirstName().trim()).toLowerCase();
        String lastName = Strings.clean(user.getLastName().trim()).toLowerCase();
        TdApi.Usernames usernames = user.getUsernames();
        String check = (firstName + " " + lastName).trim();

        if (q != null) {
          if (!firstName.startsWith(q) && !lastName.startsWith(q) && !check.startsWith(q) && !Td.findUsernameByPrefix(usernames, q)) {
            continue;
          }
        }

        String c;
        if (check.isEmpty()) {
          c = "#";
        } else {
          int codePoint = check.codePointAt(0);
          int charCount = Character.charCount(codePoint);
          if ((charCount == 1 && Character.isDigit(codePoint)) || charCount > check.length()) {
            c = "#";
          } else {
            c = check.substring(0, charCount).toUpperCase();
          }
        }

        result.add(user);

        if (prev == null) {
          prev = c;
        } else if (section > 0 && !c.equals(prev)) {
          sections[sectionCount] = section;
          letters[sectionCount] = prev;
          sectionCount++;
          if (sections.length <= sectionCount) {
            int[] newSections = new int[sections.length + 15];
            System.arraycopy(sections, 0, newSections, 0, sections.length);
            sections = newSections;
            String[] newLetters = new String[sections.length];
            System.arraycopy(letters, 0, newLetters, 0, letters.length);
            letters = newLetters;
          }
          prev = c;
          section = 0;
        }

        section++;
      }

      if (section > 0) {
        sections[sectionCount] = section;
        letters[sectionCount] = prev;
        sectionCount++;
      }

      final int finalSectionCount = sectionCount;
      final int[] finalSections = sections;
      final String[] finalLetters = letters;

      final TGUser[] resultArray = new TGUser[result.size()];
      result.toArray(resultArray);

      UI.post(() -> {
        if (isDestroyed()) return;
        if (q != null) {
          adapter.setSearchData(resultArray, finalSectionCount, finalSections, finalLetters);
        } else {
          hideProgress();
          hideEmptyView();
          adapter.setData(resultArray, finalSectionCount, finalSections, finalLetters);
        }
        recyclerView.postInvalidate();
      });
    });
  }

  private void searchInternal (final String q, boolean optimize) {
    if (q.length() == 0) {
      adapter.clearSearchData();
      return;
    }
    if (optimize) {
      sortUsers(adapter.getSearchUsers(), q, false);
    } else {
      sortUsers(users, q, false);
    }
  }

  private int indexOfSelectedChat (long chatId) {
    for (int index = 0; index < pickedChats.size(); index++) {
      if (pickedChats.get(index).getChatId() == chatId) {
        return index;
      }
    }
    return -1;
  }

  @Override
  public void onBubbleRemoved (long chatId) {
    int index = indexOfSelectedChat(chatId);
    if (index != -1) {
      pickedChats.remove(index);
      if (pickedChats.size() == 0 && floatingButton != null) {
        floatingButton.hide();
      }

      int i = 0;
      for (TGUser user : users) {
        if (user.getChatId() == chatId) {
          View v = recyclerView.getLayoutManager().findViewByPosition(i);

          if (v != null && v instanceof UserView) {
            UserView u = (UserView) v;
            if (u.getUser().getChatId() == chatId) {
              u.setChecked(false, true);
              break;
            }
          }

          adapter.notifyItemChanged(i);
          break;
        }
        i++;
      }

      if (mode == MODE_MULTI_PICK) {
        multiDelegate.onAlreadyPickedChatsChanged(pickedChats);
      }
    }
  }

  // Focus

  @Override
  public void onFocus () {
    super.onFocus();
    if (mode == MODE_CHANNEL_MEMBERS && stackSize() == 3 && stackItemAt(1) instanceof CreateChannelLinkController) {
      destroyStackItemAt(1);
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    Keyboard.hide(mode == MODE_PICK || mode == MODE_NEW_CHAT || mode == MODE_CALL || mode == MODE_NEW_SECRET_CHAT ? searchView : headerCell == null ? null : headerCell.getInput());
  }

  // Data Updates

  private void updateUser (TdApi.User user) {
    if (adapter != null) {
      int i = adapter.indexOfUser(user.id);
      if (i != -1) {
        TGUser parsedUser = adapter.getUserAt(i);
        parsedUser.setUser(user, 0);
        updateUserCellAt(i, false);
      } else if (users != null && TD.isContact(user)) {
        // TODO addContact
        // TGUser parsedUser = new TGUser(user);
      }
    }
  }

  private void updateUserStatus (long userId, TdApi.UserStatus status) {
    if (adapter != null) {
      int i = adapter.indexOfUser(userId);
      if (i != -1) {
        TGUser parsedUser = adapter.getUserAt(i);
        parsedUser.setStatus(status);
        updateUserCellAt(i, true);
      }
    }
  }

  private void updateUserCellAt (int index, boolean onlySubtext) {
    View view = recyclerView.getLayoutManager().findViewByPosition(index);

    UserView userView = null;
    if (view != null && view instanceof UserView) {
      userView = (UserView) view;
    }

    if (userView != null) {
      if (onlySubtext) {
        userView.updateSubtext();
      } else {
        userView.updateAll();
      }
      userView.invalidate();
    } else {
      adapter.notifyItemChanged(index);
    }
  }

  // RecyclerView

  public static class ContactsAdapter extends SectionedRecyclerView.SectionedAdapter implements View.OnClickListener {
    private TGUser[] users;
    private int sectionCount;
    private int[] sections;
    private String[] letters;

    private TGUser[] sUsers;
    private int sSectionCount;
    private int[] sSections;
    private String[] sLetters;

    private ContactsController controller;

    public ContactsAdapter (SectionedRecyclerView parentView, ContactsController controller) {
      super(parentView);
      this.controller = controller;
    }

    public int indexOfUser (long userId) {
      if (users == null || users.length == 0) {
        return -1;
      }
      int i = 0;
      for (TGUser user : users) {
        if (user.getUserId() == userId) {
          return i;
        }
        i++;
      }
      return -1;
    }

    public TGUser getUserAt (int i) {
      return users[i];
    }

    public void updateLocale () {
      if (users != null) {
        LinearLayoutManager manager = (LinearLayoutManager) controller.recyclerView.getLayoutManager();
        for (TGUser user : users) {
          user.updateStatus();
        }
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        for (int i = first; i <= last; i++) {
          View v = manager.findViewByPosition(i);
          if (v != null) {
            final UserView userView = (UserView) v;
            userView.updateSubtext();
            userView.invalidate();
          }
        }
      }
    }

    public void setData (TGUser[] users, int sectionCount, int[] sections, String[] letters) {
      this.users = users;
      this.sectionCount = sectionCount;
      this.sections = sections;
      this.letters = letters;
      notifyDataSetChanged();
    }

    public void setSearchData (TGUser[] users, int sectionCount, int[] sections, String[] letters) {
      this.sUsers = users;
      this.sSectionCount = sectionCount;
      this.sSections = sections;
      this.sLetters = letters;
      notifyDataSetChanged();
    }

    public TGUser[] getSearchUsers () {
      return sUsers;
    }

    public void clearSearchData () {
      sUsers = null;
      sSections = null;
      sLetters = null;
      notifyDataSetChanged();
    }

    @Override
    public int getSectionCount () {
      return sUsers == null ? sectionCount : sSectionCount;
    }

    @Override
    public int getRowsInSection (int section) {
      return sUsers == null ? sections[section] : sSections[section];
    }

    @Override
    public String getSectionName (int section) {
      return sUsers == null ? letters[section] : sLetters[section];
    }

    public int getSearchUserCount () {
      return sUsers != null ? sUsers.length : -1;
    }

    @Override
    public int getItemHeight () {
      return Screen.dp(UserView.DEFAULT_HEIGHT);
    }

    @Override
    public View createView (int viewType) {
      UserView userView;
      userView = new UserView(context, controller.tdlib);
      userView.setOffsetLeft(Screen.dp(72f));

      userView.setOnClickListener(this);
      RippleSupport.setSimpleWhiteBackground(userView, controller);
      Views.setClickable(userView);
      return userView;
    }

    @Override
    public void updateView (SectionedRecyclerView.SectionViewHolder holder, int position) {
      TGUser user = sUsers == null ? users[position] : sUsers[position];
      ((UserView) holder.itemView).setUser(user);
      ((UserView) holder.itemView).setChecked(controller.canSelectContacts() && controller.isSelected(user), false);
    }

    @Override
    public void attachViewToWindow (SectionedRecyclerView.SectionViewHolder holder) {
      ((UserView) holder.itemView).attachReceiver();
    }

    @Override
    public void detachViewFromWindow (SectionedRecyclerView.SectionViewHolder holder) {
      ((UserView) holder.itemView).detachReceiver();
    }

    @Override
    public void onClick (View v) {
      if (v instanceof UserView) {
        TGUser user = ((UserView) v).getUser();
        controller.onClick(user, v);
      }
    }
  }

  // Data

  public static final int DISPLAY_LIMIT = 1024 * 10;

  private void loadContacts () {
    showProgress();
    switch (sourceType) {
      case SOURCE_TYPE_REMOTE: {
        tdlib.searchContacts(null, DISPLAY_LIMIT, object -> {
          switch (object.getConstructor()) {
            case TdApi.Users.CONSTRUCTOR: {
              long[] userIds = ((TdApi.Users) object).userIds;
              ArrayList<TdApi.User> rawUsers = tdlib.cache().users(userIds);
              Collections.sort(rawUsers, this);

              users = new TGUser[userIds.length];
              if (users.length > 0) {
                int i = 0;
                for (TdApi.User user : rawUsers) {
                  users[i++] = new TGUser(tdlib, user);
                }
              }
              processUsers(users);
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
          }
        });
        tdlib.cache().addGlobalUsersListener(this);
        break;
      }
      case SOURCE_TYPE_LOCAL: {
        loadLocalContacts();
        break;
      }
      case SOURCE_TYPE_GMAIL: {
        loadGmailContacts();
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    if (headerCell != null) {
      headerCell.destroy();
    }
    if (sourceType == SOURCE_TYPE_REMOTE) {
      tdlib.cache().removeGlobalUsersListener(this);
    }
  }

  @Override
  public int compare (TdApi.User lhs, TdApi.User rhs) {
    return TGUser.getSortingKey(lhs).compareTo(TGUser.getSortingKey(rhs));
  }

  private void processUsers (TGUser[] users) {
    if (users.length == 0) {
      UI.post(() -> {
        if (isDestroyed()) return;
        hideProgress();
        showEmptyView();
      });
    } else {
      sortUsers(users, null, false);
    }
  }

  // Updates

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      /*TODO
      if (TD.isContact(user)) {
        updateUser(user);
      } else {
        removeUser(user.id);
      }*/
      updateUser(user);
    });
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @UiThread
  @Override
  public void onUserStatusChanged (final long userId, final TdApi.UserStatus status, boolean uiOnly) {
    updateUserStatus(userId, status);
  }

  // Local storage

  private ImportContactsCallback createImportCallback () {
    return contacts -> ContactsController.this.onContactsImported(contacts);
  }

  public void onContactsImported (TdApi.Users contacts) {

  }

  private void loadLocalContacts () {
    Background.instance().post(() -> loadLocalContactsInternal());
  }

  private void loadLocalContactsInternal () {
    ContentResolver resolver = context().getContentResolver();
    Cursor c = null;
    ArrayList<TGUser> contacts = new ArrayList<>();
    try {
      final String[] projection = new String[] {
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.HAS_PHONE_NUMBER,
        ContactsContract.Contacts.DISPLAY_NAME
      };
      c = resolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null);
      if (c != null) {
        final int idColumn = c.getColumnIndex(ContactsContract.Contacts._ID);
        final int hasNumberColumn = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
        final int nameColumn = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

        // Getting contact ids
        final SparseArrayCompat<String[]> nameSet = new SparseArrayCompat<>(c.getCount());
        while (c.moveToNext()) {
          int hasNumber = c.getInt(hasNumberColumn);
          if (hasNumber > 0) {
            String name = c.getString(nameColumn);
            name = name == null ? "" : name.trim();
            nameSet.put(c.getInt(idColumn), new String[] {name, ""});
          }
        }

        c.close();

        if (nameSet.size() > 0) {
          int[] contactIds = new int[nameSet.size()];
          for (int i = 0; i < contactIds.length; i++) {
            contactIds[i] = nameSet.keyAt(i);
          }
          String contactIdsList = StringUtils.join(",", contactIds);

          // Fetching first and last name
          final String[] nameProjection = new String[] {
            ContactsContract.Contacts._ID,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
          };
          final String whereName = ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.Contacts._ID + " IN (" + contactIdsList + ")";
          final String[] whereNameArgs = new String[] {ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};
          Cursor nc = null;
          try {
            nc = resolver.query(ContactsContract.Data.CONTENT_URI, nameProjection, whereName, whereNameArgs, null);
            if (nc != null) {
              final int idNameColumn = nc.getColumnIndex(ContactsContract.Contacts._ID);
              final int givenNameColumn = nc.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
              final int familyNameColumn = nc.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
              while (nc.moveToNext()) {
                int id = nc.getInt(idNameColumn);
                String[] name = nameSet.get(id, null);
                if (name == null) {
                  continue;
                }
                String firstName = nc.getString(givenNameColumn);
                firstName = firstName == null ? "" : firstName.trim();
                if (firstName.length() == 0) {
                  continue;
                }
                String lastName = nc.getString(familyNameColumn);
                lastName = lastName == null ? "" : lastName.trim();
                name[0] = firstName;
                name[1] = lastName;
              }
              nc.close();
            }
          } catch (Throwable t) {
            Log.w("Cannot read contact names", t);
          }
          if (nc != null && !nc.isClosed()) {
            nc.close();
          }

          // Fetching phone numbers
          final String[] phoneProjection = new String[] {
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
          };
          Cursor cc = null;
          try {
            cc = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, phoneProjection, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " IN (" + contactIdsList + ")", null, null);
            if (cc != null) {
              int contactId = 0;
              final int contactIdIndex = cc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
              final int numberIndex = cc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
              while (cc.moveToNext()) {
                int rawContactId = cc.getInt(contactIdIndex);
                String[] name = nameSet.get(rawContactId);
                if (name != null) {
                  contacts.add(new TGUser(tdlib, ++contactId, rawContactId, name[0], name[1], cc.getString(numberIndex)));
                }
              }
              cc.close();
            }
          } catch (Throwable t) {
            Log.w("Cannot read phone numbers", t);
          }
          if (cc != null && !cc.isClosed()) {
            cc.close();
          }
        }
      }
    } catch (Throwable t) {
      Log.w("Cannot read contacts", t);
    }
    if (c != null && !c.isClosed()) {
      c.close();
    }
    TGUser[] users = new TGUser[contacts.size()];
    if (users.length > 0) {
      contacts.toArray(users);
      sortUsers(users);
    }
    processUsers(users);
  }

  private void loadGmailContacts () {

  }

  // Locale changer


  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (adapter != null) {
      adapter.updateLocale();
    }
    if (recyclerView != null) {
      recyclerView.invalidate();
    }
    if (searchView != null) {
      int leftMargin = Screen.dp(68f);
      int rightMargin = canAddContacts() ? Screen.dp(49f) : 0;
      if (Views.setMargins(searchView, Lang.rtl() ? rightMargin : leftMargin, 0, Lang.rtl() ? leftMargin : rightMargin, 0)) {
        Views.updateLayoutParams(searchView);
      }
    }
  }
}
