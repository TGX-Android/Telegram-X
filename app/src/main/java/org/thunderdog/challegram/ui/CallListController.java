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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.CallItem;
import org.thunderdog.challegram.data.CallSection;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.DateChangeListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.CenterDecoration;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.Td;

public class CallListController extends RecyclerViewController<Void> implements
  View.OnClickListener,
  Client.ResultHandler,
  MessageListener,
  DateChangeListener,
  View.OnLongClickListener,
  BaseView.ActionListProvider, TdlibOptionListener {
  public CallListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_call_list;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Calls);
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (endReached) {
          infoView.showInfo(Lang.pluralBold(R.string.xCalls, messages.size()));
        } else {
          infoView.showProgress();
        }
      }

      @Override
      protected void setHeaderText (ListItem item, TextView view, boolean isUpdate) {
        if (item.getData() instanceof CallSection) {
          CharSequence text = ((CallSection) item.getData()).getName();
          Views.setMediumText(view, text);
        } else {
          super.setHeaderText(item, view, isUpdate);
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setCallItem((CallItem) item.getData());
      }

      @Override
      protected void setRecyclerViewData (ListItem item, RecyclerView recyclerView, boolean isInitialization) {
        final int itemId = item.getId();
        if (itemId == R.id.search_top) {
          if (recyclerView.getAdapter() != topChatsAdapter) {
            recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
            recyclerView.setAdapter(topChatsAdapter);
            if (recyclerView.getItemDecorationCount() == 0) {
              recyclerView.addItemDecoration(new CenterDecoration() {
                @Override
                public int getItemCount () {
                  return topChats != null ? topChats.size() : 0;
                }
              });
              ((CustomRecyclerView) recyclerView).setMeasureListener((v, oldWidth, oldHeight, newWidth, newHeight) -> {
                if (oldWidth != newWidth && oldWidth != 0) {
                  v.invalidateItemDecorations();
                }
              });
            }
          }
        }
      }
    };
    adapter.setOnLongClickListener(this);
    buildCells();
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      private float lastY;
      private float lastShowY;

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (messages != null && ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() >= adapter.getItems().size() - 5) {
          loadMore();
        }
        if (Settings.instance().chatFoldersEnabled() && getParentOrSelf() == CallListController.this) {
          lastY += dy;
          if (dy < 0 && lastShowY - lastY >= Screen.getTouchSlop()) {
            setDoneVisible(true, true);
            lastShowY = lastY;
          } else if (lastY - lastShowY > Screen.getTouchSlopBig()) {
            setDoneVisible(false, true);
            lastShowY = lastY;
          }
          if (Math.abs(lastY - lastShowY) > Screen.getTouchSlopBig()) {
            lastY = 0;
            lastShowY = 0;
          }
        }
      }
    });

    tdlib.client().send(new TdApi.SearchCallMessages(null, Screen.calculateLoadingItems(Screen.dp(72f), 20), false), this);
    tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryCalls(), 30), this);
    tdlib.listeners().subscribeForAnyUpdates(this);
    tdlib.context().dateManager().addListener(this);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return messages == null;
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (Settings.instance().chatFoldersEnabled() && getParentOrSelf() == this) {
      setDoneIcon(R.drawable.baseline_phone_24);
      setDoneVisible(true, false);
    }
  }

  @Override
  protected void onDoneClick () {
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(ContactsController.MODE_CALL);
    navigateTo(c);
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
      case Lang.EVENT_DIRECTION_CHANGED:
        adapter.notifyAllStringsChanged();
        break;
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        adapter.notifyItemsChanged(item -> item.getViewType() == ListItem.TYPE_CHAT_BETTER);
        break;
      case Lang.EVENT_STRING_CHANGED:
        adapter.notifyStringChanged(arg1);
        break;
    }
  }

  private ArrayList<TGFoundChat> topChats;
  private SettingsAdapter topChatsAdapter;

  private boolean hasTopChats () {
    return topChats != null && !topChats.isEmpty();
  }

  private void setTopChats (ArrayList<TGFoundChat> topChats) {
    if (this.topChats == null && topChats == null) {
      return;
    }
    boolean hasSections = sections != null && !sections.isEmpty();
    boolean hadChats = this.topChats != null && !this.topChats.isEmpty() && hasSections;
    boolean hasChats = topChats != null && !topChats.isEmpty() && hasSections;

    this.topChats = topChats;
    if (topChats != null && topChatsAdapter == null) {
      topChatsAdapter = new SettingsAdapter(this, v -> {
        ListItem item = (ListItem) v.getTag();
        final int itemId = item.getId();
        if (itemId == R.id.search_chat_top) {
          final TGFoundChat chat = (TGFoundChat) item.getData();
          if (chat.getId() != 0) {
            tdlib.context().calls().makeCall(CallListController.this, chat.getUserId(), null);
          }
        }
      }, this) {
        @Override
        protected void setChatData (ListItem item, VerticalChatView chatView) {
          chatView.setPreviewActionListProvider(CallListController.this);
          chatView.setChat((TGFoundChat) item.getData());
        }
      };
      topChatsAdapter.setOnLongClickListener(v -> {
        final ListItem item = (ListItem) v.getTag();
        final int itemId = item.getId();
        if (itemId == R.id.search_chat_top) {
          final TGFoundChat chat = (TGFoundChat) item.getData();
          removeTopChat(chat);
          return true;
        }
        return false;
      });
    }
    if (topChatsAdapter != null && topChats != null) {
      ArrayList<ListItem> chatItems = new ArrayList<>(topChats.size());
      for (TGFoundChat chat : topChats) {
        chat.setNoUnread();
        chatItems.add(new ListItem(ListItem.TYPE_CHAT_VERTICAL, R.id.search_chat_top).setData(chat).setLongId(chat.getId()));
      }
      topChatsAdapter.replaceItems(chatItems);
    }

    if (hasChats != hadChats && hasSections) {
      if (hasChats) {
        List<ListItem> items = adapter.getItems();
        items.add(1, newPeopleItem());
        items.add(2, new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(3, new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL, R.id.search_top));
        items.add(4, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.notifyItemRangeInserted(1, 4);
      } else {
        adapter.removeRange(1, 4);
      }
    }
  }

  private ListItem newPeopleItem () {
    return new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.People);
  }

  private void removeTopChat (final TGFoundChat chat) {
    showOptions(Lang.getStringBold(R.string.ChatHintsDelete, chat.getTitle()), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_sweep_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_delete) {
        tdlib.client().send(new TdApi.RemoveTopChat(new TdApi.TopChatCategoryCalls(), chat.getChatId()), tdlib.okHandler());
        if (hasTopChats()) {
          if (topChats.size() == 1 && topChats.remove(chat)) {
            setTopChats(null);
          } else {
            topChats.remove(chat);
            int i = topChatsAdapter.indexOfViewByData(chat);
            if (i != -1) {
              topChatsAdapter.removeItem(i);
              topChatsAdapter.notifyItemRangeChanged(0, topChats.size());
            }
            if (topChats.size() > 15) {
              tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryCalls(), 30), CallListController.this);
            }
          }
        } else {
          tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryCalls(), 30), CallListController.this);
        }
      }
      return true;
    });
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (sections == null) {
      items.add(new ListItem(ListItem.TYPE_PROGRESS));
    } else {
      boolean firstSection = true;
      if (!sections.isEmpty() && topChats != null && !topChats.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        firstSection = false;
        items.add(newPeopleItem());
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL, R.id.search_top));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
      for (CallSection section : sections) {
        if (firstSection) {
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
          firstSection = false;
        }
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, section.getName(), false).setData(section));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        boolean firstCall = true;
        for (CallItem item : section.getItems()) {
          if (firstCall) {
            firstCall = false;
          } else {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.call).setData(item));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
      if (items.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NoCalls));
      } else {
        items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.btn_calls));
      }
    }
    adapter.setItems(items, false);
  }

  private int indexOfMessage (long chatId, long messageId) {
    if (messages == null || messages.isEmpty()) {
      return -1;
    }
    int index = 0;
    for (TdApi.Message message : messages) {
      if (message.chatId == chatId && message.id == messageId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private void removeMessage (long chatId, long messageId) {
    int index = indexOfMessage(chatId, messageId);
    if (index == -1) {
      return;
    }
    messages.remove(index);
    if (sections == null || sections.isEmpty()) {
      return;
    }
    if (messages.isEmpty()) {
      sections.clear();
      buildCells();
      return;
    }
    loop:
    for (CallSection section : sections) {
      boolean isFirstItem = true;
      for (CallItem item : section.getItems()) {
        if (item.removeMessage(chatId, messageId)) {
          int position = adapter.indexOfViewByData(item);
          if (item.isEmpty()) {
            section.removeItem(item);
            if (section.isEmpty()) {
              sections.remove(section);
              adapter.removeRange(position - 2, 4);
            } else {
              adapter.removeRange(isFirstItem ? position : position - 1, 2);
            }
          } else {
            // Notify call counter changed
            adapter.updateValuedSettingByPosition(position);
          }

          break loop;
        }
        isFirstItem = false;
      }
    }
    adapter.updateValuedSettingById(R.id.btn_calls);
  }

  private void addMessages (TdApi.FoundMessages messages) {
    nextOffset = messages.nextOffset;
    if (StringUtils.isEmpty(nextOffset)) {
      endReached = true;
    }
    if (messages.messages.length == 0) {
      adapter.updateValuedSettingById(R.id.btn_calls);
      return;
    }

    boolean workingWithExistingItem = true;
    boolean workingWithExistingSection = true;
    int changedExistingIndex = -1;
    int insertedItemCount = 0;

    CallSection currentSection = sections.isEmpty() ? null : sections.get(sections.size() - 1);
    boolean needReplace = currentSection == null;
    if (needReplace) {
      adapter.removeRange(0, adapter.getItems().size());
    }
    int startIndex = needReplace ? 0 : adapter.getItems().size() - 2;

    for (TdApi.Message message : messages.messages) {
      this.messages.add(message);
      CallItem item = new CallItem(tdlib, message);
      int state = currentSection != null ? currentSection.appendItem(item) : CallSection.STATE_NONE;
      switch (state) {
        case CallSection.STATE_NONE: {
          workingWithExistingItem = false;

          if (workingWithExistingSection) {
            if (insertedItemCount > 0) {
              adapter.notifyItemRangeInserted(startIndex, insertedItemCount);
            }
            insertedItemCount = 0;
            startIndex = adapter.getItems().size() - 1;
            workingWithExistingSection = false;
          } else {
            if (needReplace) {
              needReplace = false;
              adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
            } else {
              adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            }
            insertedItemCount++;
          }

          currentSection = new CallSection(item);
          sections.add(currentSection);

          adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_HEADER, 0, 0, currentSection.getName(), false).setData(currentSection));
          insertedItemCount++;
          adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_SHADOW_TOP));
          insertedItemCount++;
          adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.call).setData(item));
          insertedItemCount++;

          break;
        }
        case CallSection.STATE_MERGED: {
          // Nothing to do, except that we should notifyItemChanged on already existing cell
          if (workingWithExistingItem) {
            changedExistingIndex = startIndex - 1;
          }
          break;
        }
        case CallSection.STATE_INSERTED: {
          workingWithExistingItem = false;

          if (currentSection.getItems().size() > 1) {
            adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_SEPARATOR));
            insertedItemCount++;
          }

          adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.call).setData(item));
          insertedItemCount++;

          break;
        }
      }
    }

    if (changedExistingIndex != -1) {
      adapter.updateValuedSettingByPosition(changedExistingIndex);
    }
    if (insertedItemCount > 0) {
      if (!workingWithExistingSection) {
        adapter.getItems().add(startIndex + insertedItemCount, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        insertedItemCount++;
      }
      adapter.notifyItemRangeInserted(startIndex, insertedItemCount);
    }
  }

  private void addMessage (TdApi.Message message) {
    if (messages == null || indexOfMessage(message.chatId, message.id) != -1 || (!messages.isEmpty() && messages.get(0).date > message.date)) {
      return;
    }
    if (this.sections == null) {
      return;
    }
    messages.add(0, message);
    if (this.sections.isEmpty()) {
      buildSections();
      return;
    }
    CallItem item = new CallItem(tdlib, message);
    int state = this.sections.get(0).prependItem(item);
    switch (state) {
      case CallSection.STATE_INSERTED: {
        int startIndex = hasTopChats() ? 7 : 3;
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_SEPARATOR));
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.call).setData(item));
        adapter.notifyItemRangeInserted(3, 2);
        break;
      }
      case CallSection.STATE_MERGED: {
        adapter.updateValuedSettingByPosition(3);
        break;
      }
      case CallSection.STATE_NONE: {
        CallSection section = new CallSection(item);
        sections.add(0, section);
        int startIndex = hasTopChats() ? 5 : 1;
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.call).setData(item));
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_SHADOW_TOP));
        adapter.getItems().add(startIndex, new ListItem(ListItem.TYPE_HEADER, 0, 0, section.getName(), false).setData(section.getName()));
        adapter.notifyItemRangeInserted(startIndex, 4);
        break;
      }
    }
    adapter.updateValuedSettingById(R.id.btn_calls);
  }

  private ArrayList<TdApi.Message> messages;
  private String nextOffset;

  private void setMessages (TdApi.FoundMessages messages) {
    this.messages = new ArrayList<>(messages.messages.length);
    Collections.addAll(this.messages, messages.messages);
    this.nextOffset = messages.nextOffset;
    buildSections();
    removeItemAnimatorDelayed();
    if (StringUtils.isEmpty(nextOffset)) {
      endReached = true;
    }
    executeScheduledAnimation();
  }

  private boolean isLoadingMore;
  private boolean endReached;

  private void loadMore () {
    if (!isLoadingMore && messages != null && !messages.isEmpty() && !endReached && sections != null && !sections.isEmpty() && !isDestroyed()) {
      isLoadingMore = true;
      tdlib.client().send(new TdApi.SearchCallMessages(nextOffset, 40, false), new Client.ResultHandler() {
        @Override
        public void onResult (TdApi.Object object) {
          if (object.getConstructor() == TdApi.FoundMessages.CONSTRUCTOR) {
            TdApi.FoundMessages foundMessages = (TdApi.FoundMessages) object;
            if (foundMessages.messages.length == 0 && !StringUtils.isEmpty(foundMessages.nextOffset) && !foundMessages.nextOffset.equals(nextOffset)) {
              nextOffset = foundMessages.nextOffset;
              tdlib.client().send(new TdApi.SearchCallMessages(foundMessages.nextOffset, 40, false), this);
              return;
            }
          }
          runOnUiThreadOptional(() -> {
            isLoadingMore = false;
            if (object.getConstructor() == TdApi.FoundMessages.CONSTRUCTOR) {
              addMessages((TdApi.FoundMessages) object);
            }
          });
        }
      });
    }
  }

  private ArrayList<CallSection> sections;

  private void buildSections () {
    this.sections = new ArrayList<>();
    CallSection section = null;
    for (TdApi.Message message : messages) {
      CallItem item = new CallItem(tdlib, message);
      if (section == null || section.appendItem(item) == CallSection.STATE_NONE) {
        section = new CallSection(item);
        sections.add(section);
      }
    }
    buildCells();
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
    tdlib.context().dateManager().removeListener(this);
  }

  @Override
  public void onClick (View v) {
    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CHAT_BETTER) {
      CallItem call = (CallItem) item.getData();
      tdlib.context().calls().makeCall(CallListController.this, call.getUserId(), null);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CHAT_BETTER) {
      CallItem call = (CallItem) item.getData();
      final long chatId = call.getChatId();
      final long[] messageIdsToDelete = call.getMessageIds();
      if (messageIdsToDelete != null) {
        showOptions(null, new int[]{R.id.btn_deleteAll, R.id.btn_openChat, R.id.btn_cancel}, new String[]{Lang.getString(R.string.DeleteEntry), Lang.getString(R.string.OpenChat), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_sweep_24, R.drawable.baseline_chat_bubble_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_deleteAll) {
            tdlib.deleteMessages(chatId, messageIdsToDelete, false);
          } else if (id == R.id.btn_openChat) {
            tdlib.ui().openChat(CallListController.this, chatId, null);
          }
          return true;
        });
        return true;
      }
    }
    return false;
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    final ListItem item = (ListItem) v.getTag();
    if (item == null) {
      return null;
    }

    final long userId;
    final long chatId;
    final CallItem call;
    final TGFoundChat chat;
    final int itemId = item.getId();
    if (itemId == R.id.search_chat_top) {
      chat = (TGFoundChat) item.getData();
      chatId = chat.getId();
      userId = chat.getUserId();
      call = null;
    } else if (itemId == R.id.call) {
      call = (CallItem) item.getData();
      chat = null;
      userId = call.getUserId();
      chatId = call.getChatId();
    } else {
      return null;
    }

    if (tdlib.cache().userGeneral(userId)) {
      ids.append(R.id.btn_phone_call);
      strings.append(R.string.Call);
      icons.append(R.drawable.baseline_call_24);
    }

    ids.append(R.id.btn_delete);
    strings.append(R.string.RemoveCall);
    icons.append(R.drawable.baseline_delete_sweep_24);

    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        if (actionId == R.id.btn_phone_call) {
          tdlib.context().calls().makeCallDelayed(CallListController.this, userId, null, true);
        } else if (actionId == R.id.btn_delete) {
          if (call != null) {
            String firstName = tdlib.senderName(new TdApi.MessageSenderUser(call.getUserId()), true);
            CharSequence text = Lang.getStringBold(R.string.QDeleteCallFromRecent);
            if (call.canBeDeletedForAllUsers()) {
              showSettings(
                new SettingsWrapBuilder(R.id.btn_delete).setHeaderItem(new ListItem(ListItem.TYPE_INFO, R.id.text_title, 0, text, false)).setRawItems(
                  new ListItem[] {
                    new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_deleteAll, 0, Lang.getStringBold(R.string.DeleteForUser, firstName), false)
                  }).setIntDelegate((id, result) -> {
                  if (id == R.id.btn_delete) {
                    tdlib.deleteMessages(chatId, call.getMessageIds(), result.get(R.id.btn_deleteAll) != 0);
                  }
                }).setSaveStr(R.string.Delete).setSaveColorId(ColorId.textNegative)
              );
            } else {
              showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DeleteEntry), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_sweep_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                if (id == R.id.btn_delete) {
                  tdlib.deleteMessages(chatId, call.getMessageIds(), false);
                }
                return true;
              });
            }
          } else if (chat != null) {
            removeTopChat(chat);
          }
        }
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

      }
    };
  }

  @Override
  public void onTopChatsDisabled (boolean areDisabled) {
    tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryCalls(), 30), this);
  }

  @Override
  public void onResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.FoundMessages.CONSTRUCTOR: {
        runOnUiThreadOptional(() -> {
          if (Log.isEnabled(Log.TAG_MESSAGES_LOADER) && Log.checkLogLevel(Log.LEVEL_VERBOSE)) {
            Log.i(Log.TAG_MESSAGES_LOADER, "Calls list: %s", object);
          }
          setMessages((TdApi.FoundMessages) object);
        });
        break;
      }
      case TdApi.Chats.CONSTRUCTOR: {
        long[] chatIds = ((TdApi.Chats) object).chatIds;
        final ArrayList<TGFoundChat> foundChats;
        if (chatIds.length >= Config.MINIMUM_CALL_CONTACTS_SUGGESTIONS) {
          foundChats = new ArrayList<>(chatIds.length);
          SearchManager.parseResult(tdlib, null, 0, foundChats, null, chatIds, null, false, null);
        } else {
          foundChats = null;
        }
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setTopChats(foundChats);
          }
        });
        break;
      }
    }
  }

  private static boolean filter (TdApi.Message message) {
    return Td.isCall(message.content) && message.sendingState == null && message.schedulingState == null;
  }

  @Override
  public void onNewMessage (final TdApi.Message message) {
    if (filter(message)) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          addMessage(message);
        }
      });
    }
  }

  /*@Override
  public void __onNewMessages (final TdApi.Message[] messages) {
    boolean found = false;
    for (TdApi.Message message : messages) {
      if (filter(message)) {
        found = true;
        break;
      }
    }
    if (found) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          for (TdApi.Message message : messages) {
            if (filter(message)) {
              addMessage(message);
            }
          }
        }
      });
    }
  }*/

  @Override
  public void onDateChanged () {
    buildSections();
  }

  @Override
  public void onMessageSendSucceeded (final TdApi.Message message, long oldMessageId) {
    if (filter(message)) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          addMessage(message);
        }
      });
    }
  }

  @Override
  public void onMessagesDeleted (final long chatId, final long[] messageIds) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        for (long messageId : messageIds) {
          removeMessage(chatId, messageId);
        }
      }
    });
  }
}
