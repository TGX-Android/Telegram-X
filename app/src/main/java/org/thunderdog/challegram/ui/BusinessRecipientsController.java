/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import me.vkryl.core.lambda.RunnableData;

final class BusinessRecipientsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final boolean forBot;
  private final RunnableData<TdApi.BusinessRecipients> done;
  private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
  private final LinkedHashSet<Long> excluded = new LinkedHashSet<>();
  private final long account;
  private boolean existing, newChats, contacts, nonContacts, exceptSelected, completed;
  private SettingsAdapter adapter;

  BusinessRecipientsController (Context context, Tdlib tdlib, TdApi.BusinessRecipients source, boolean forBot,
                                RunnableData<TdApi.BusinessRecipients> done) {
    super(context, tdlib);
    this.account = tdlib.myUserId();
    this.forBot = forBot;
    this.done = done;
    existing = source.selectExistingChats;
    newChats = source.selectNewChats;
    contacts = source.selectContacts;
    nonContacts = source.selectNonContacts;
    exceptSelected = source.excludeSelected;
    if (source.chatIds != null) {
      for (long id : source.chatIds) {
        selected.add(id);
      }
    }
    if (forBot && source.excludedChatIds != null) {
      for (long id : source.excludedChatIds) {
        excluded.add(id);
        selected.remove(id);
      }
    }
  }

  @Override public int getId () { return R.id.controller_businessRecipients; }
  @Override public CharSequence getName () { return Lang.getString(R.string.TgxBusinessCustomRecipients); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getViewType() == ListItem.TYPE_RADIO_SETTING) {
          view.getToggler().setRadioEnabled(item.isSelected(), isUpdate);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    render();
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    addToggle(items, R.id.btn_businessRecipientsExcludeMode, R.string.TgxBusinessExcludeSelected, exceptSelected);
    addToggle(items, R.id.btn_businessRecipientsExisting, R.string.TgxBusinessExistingChats, existing);
    addToggle(items, R.id.btn_businessRecipientsNew, R.string.TgxBusinessNewChats, newChats);
    addToggle(items, R.id.btn_businessRecipientsContacts, R.string.TgxBusinessContacts, contacts);
    addToggle(items, R.id.btn_businessRecipientsNonContacts, R.string.TgxBusinessNonContacts, nonContacts);
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0,
      exceptSelected ? R.string.TgxBusinessExcludedSelection : R.string.TgxBusinessIncludedSelection));
    addChats(items, selected, false);
    if (forBot) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessAlwaysExcluded));
      addChats(items, excluded, true);
    }
    items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_businessRecipientsDone, 0, R.string.Continue));
    adapter.setItems(items, false);
  }

  private static void addToggle (ArrayList<ListItem> items, int id, int label, boolean checked) {
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, id, 0, label).setSelected(checked));
  }

  private void addChats (ArrayList<ListItem> items, LinkedHashSet<Long> chats, boolean alwaysExclude) {
    for (long id : chats) {
      String name = tdlib.chat(id) != null ? tdlib.chatTitle(id) : Long.toString(id);
      items.add(new ListItem(ListItem.TYPE_SETTING, alwaysExclude ? R.id.btn_businessRecipientsRemoveExcluded :
        R.id.btn_businessRecipientsRemoveSelected, 0, name).setLongId(id));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, alwaysExclude ? R.id.btn_businessRecipientsAddExcluded :
      R.id.btn_businessRecipientsAddSelected, 0, R.string.TgxBusinessAddRecipient));
  }

  private void pickChat (boolean alwaysExclude) {
    ContactsController picker = new ContactsController(context(), tdlib);
    picker.setArguments(new ContactsController.Args(new SenderPickerDelegate() {
      @Override public boolean allowGlobalSearch () { return true; }
      @Override public boolean onSenderPick (ContactsController origin, View view, TdApi.MessageSender sender) {
        TdApi.User user = sender instanceof TdApi.MessageSenderUser target ? tdlib.cache().user(target.userId) : null;
        if (user == null || !(user.type instanceof TdApi.UserTypeRegular) || user.id == account) {
          UI.showToast(R.string.TgxBusinessRecipientInvalid, Toast.LENGTH_SHORT);
          return false;
        }
        tdlib.send(new TdApi.CreatePrivateChat(user.id, false), (chat, error) -> origin.runOnUiThreadOptional(() -> {
          if (!origin.isFocused() || completed || account != tdlib.myUserId()) {
            return;
          }
          if (error != null) {
            UI.showError(error);
            return;
          }
          LinkedHashSet<Long> target = alwaysExclude ? excluded : selected;
          LinkedHashSet<Long> other = alwaysExclude ? selected : excluded;
          other.remove(chat.id);
          target.add(chat.id);
          render();
          origin.navigateBack();
        }));
        return false;
      }
    }));
    navigateTo(picker);
  }

  @Override
  public void onClick (View view) {
    int id = view.getId();
    if (id == R.id.btn_businessRecipientsAddExcluded || id == R.id.btn_businessRecipientsAddSelected) {
      pickChat(id == R.id.btn_businessRecipientsAddExcluded);
      return;
    }
    if (id == R.id.btn_businessRecipientsDone && !completed && account == tdlib.myUserId()) {
      TdApi.BusinessRecipients result = new TdApi.BusinessRecipients(toIds(selected), toIds(excluded),
        existing, newChats, contacts, nonContacts, exceptSelected);
      if (navigationController() == null || navigationController().getPreviousStackItem() == null) {
        return;
      }
      var origin = navigationController().getPreviousStackItem();
      completed = true;
      origin.addOneShotFocusListener(() -> {
        if (account == tdlib.myUserId()) {
          done.runWithData(result);
        }
      });
      navigateBack();
      return;
    }
    if (id == R.id.btn_businessRecipientsExcludeMode) {
      exceptSelected = !exceptSelected;
    } else if (id == R.id.btn_businessRecipientsExisting) {
      existing = !existing;
    } else if (id == R.id.btn_businessRecipientsNew) {
      newChats = !newChats;
    } else if (id == R.id.btn_businessRecipientsContacts) {
      contacts = !contacts;
    } else if (id == R.id.btn_businessRecipientsNonContacts) {
      nonContacts = !nonContacts;
    } else if (view.getTag() instanceof ListItem item) {
      if (id == R.id.btn_businessRecipientsRemoveSelected) {
        selected.remove(item.getLongId());
      } else if (id == R.id.btn_businessRecipientsRemoveExcluded) {
        excluded.remove(item.getLongId());
      }
    }
    render();
  }

  private static long[] toIds (LinkedHashSet<Long> set) {
    long[] ids = new long[set.size()];
    int i = 0;
    for (long id : set) {
      ids[i++] = id;
    }
    return ids;
  }
}
