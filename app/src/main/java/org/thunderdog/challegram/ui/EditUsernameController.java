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
 * File created on 21/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.net.Uri;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import tgx.td.ChatId;
import tgx.td.Td;
import tgx.td.TdConstants;

public class EditUsernameController extends EditBaseController<EditUsernameController.Args> implements SettingsAdapter.TextChangeListener, View.OnClickListener {
  public static class Args {
    public long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public EditUsernameController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long chatId;
  private boolean isBotEdit;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chatId = args.chatId;
    this.isBotEdit = tdlib.canEditBotChat(chatId);
  }

  @Override
  public int getId () {
    return R.id.controller_editUsername;
  }

  @Override
  public CharSequence getName () {
    if (isBotEdit) {
      return Lang.getString(R.string.BotUsername);
    } else {
      return Lang.getString(chatId != 0 ? (tdlib.isChannel(chatId) ? R.string.ChannelLink : R.string.GroupLink) : R.string.Username);
    }
  }

  private SettingsAdapter adapter;
  private TdApi.Usernames sourceUsernames, currentUsernames;
  private Set<String> activeUsernames;
  private ListItem checkedItem, checkingItem, description;
  private boolean hasSecondaryUsernames;

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }

  private static ListItem valueOfUsername (String username, boolean isEditable) {
    return new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_username, 0, username, true)
      .setBoolValue(isEditable)
      .setStringValue(username);
  }

  private FillingDecoration decoration;
  private int[] activeUsernamesDecoration;
  private ListItem reorderHintItem;
  private boolean hasReorderHint;

  private static final boolean NEED_ACTIVE_USERNAMES_FILLING = false;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    TdApi.Usernames usernames;
    if (chatId != 0) {
      usernames = tdlib.chatUsernames(chatId);
    } else {
      usernames = tdlib.myUserUsernames();
    }
    sourceUsernames = usernames != null ? usernames : new TdApi.Usernames(new String[0], new String[0], "", new String[0]);
    activeUsernames = new LinkedHashSet<>();
    Collections.addAll(activeUsernames, sourceUsernames.activeUsernames);
    if (TEST_MULTI_USERNAMES_UI) {
      // toggleUsernameIsActive, toggleBotUsernameIsActive, or toggleSupergroupUsernameIsActive
      // reorderActiveUsernames, reorderBotActiveUsernames or reorderSupergroupActiveUsernames
      if (sourceUsernames.disabledUsernames.length == 0) {
        sourceUsernames.disabledUsernames = new String[] {
          "tgxasdf",
          /*"abgasdf",
          "asasdfdf",
          "vasfdaasdf",
          "test"*/
        };
      }
    }
    currentUsernames = Td.copyOf(sourceUsernames);
    hasSecondaryUsernames = Td.secondaryUsernamesCount(currentUsernames) > 0;

    checkingItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, chatId != 0 ? R.string.LinkChecking : R.string.UsernameChecking).setTextColorId(ColorId.textLight);
    checkedItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.state, 0, 0);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
      }

      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        if (isBotEdit) {
          editText.setInputEnabled(false);
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_username) {
          String username = item.getStringValue();
          boolean isEditable = item.getBoolValue();
          boolean isActive = item.isSelected();
          boolean isLoading = togglingUsernames.contains(username);
          view.setIgnoreEnabled(true);
          view.setVisuallyEnabled(isActive && !isEditable && !isLoading, isUpdate);
          view.findCheckBox().setDisabled(isEditable, isUpdate);
          view.findCheckBox().setChecked(isActive, isUpdate);
        }
      }
    };
    adapter.setTextChangeListener(this);
    adapter.setLockFocusOn(this, !hasSecondaryUsernames && !isBotEdit);

    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EDITTEXT, R.id.input, 0, chatId != 0 ? tdlib.tMeHost() : Lang.getString(R.string.Username), false).setStringValue(currentUsernames.editableUsername)
      .setInputFilters(new InputFilter[] {
        new CodePointCountFilter(TdConstants.MAX_USERNAME_LENGTH),
        new TD.UsernameFilter()
      }).setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this)));
    items.add((description = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, genDescription(), false)
      .setTextColorId(ColorId.textLight)));

    decoration = new FillingDecoration(recyclerView, this);
    decoration.addRange(0, items.size());
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, R.id.shadowBottom));
    recyclerView.addItemDecoration(decoration);

    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          Keyboard.hide(getLockFocusView());
        }
      }
    });

    if (hasSecondaryUsernames) {
      final ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
        private boolean isMovable (ListItem item) {
          return item.getId() == R.id.btn_username && item.isSelected();
        }

        @Override
        public int getMovementFlags (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
          ListItem item = (ListItem) viewHolder.itemView.getTag();
          if (activeUsernames.size() > 1 && isMovable(item)) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
          } else {
            return 0;
          }
        }

        @Override
        public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
          ListItem fromItem = (ListItem) viewHolder.itemView.getTag();
          ListItem toItem = (ListItem) target.itemView.getTag();

          if (isMovable(fromItem) && isMovable(toItem)) {
            int fromAdapterIndex = viewHolder.getBindingAdapterPosition();
            int fromUsernameIndex = toUsernameIndex(fromAdapterIndex);

            int toAdapterIndex = target.getBindingAdapterPosition();
            int toUsernameIndex = toUsernameIndex(toAdapterIndex);

            moveUsername(fromUsernameIndex, toUsernameIndex);
            saveUsernamesOrder(false);

            return true;
          }
          return false;
        }

        @Override
        public void onSwiped (@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
      });
      helper.attachToRecyclerView(recyclerView);
      @StringRes int titleRes, hintRes;
      titleRes = selectResource(
        R.string.UsernamesMy,
        R.string.UsernamesChat,
        R.string.UsernamesChannel,
        R.string.UsernamesBot
      );
      hintRes = selectResource(
        R.string.UsernamesMyHint,
        R.string.UsernamesChatHint,
        R.string.UsernamesChannelHint,
        R.string.UsernamesBotHint
      );
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, titleRes));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      int startIndex = items.size();
      boolean first = true;
      for (String activeUsername : activeUsernames) {
        if (first) {
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        final boolean isEditable = activeUsername.equals(currentUsernames.editableUsername);
        items.add(valueOfUsername(activeUsername, isEditable));
      }
      for (String disabledUsername : currentUsernames.disabledUsernames) {
        if (first) {
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_username, 0, disabledUsername, false)
          .setStringValue(disabledUsername)
        );
      }
      if (NEED_ACTIVE_USERNAMES_FILLING) {
        decoration.addRange(startIndex, items.size());
        activeUsernamesDecoration = decoration.lastRange();
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      reorderHintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, hintRes);
      if (activeUsernames.size() > 1) {
        hasReorderHint = true;
        items.add(reorderHintItem);
      }
    }

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    /*if (!isBotEdit) {
      setDoneVisible(true);
    }*/
  }

  @Override
  public void onTextChanged (int id, ListItem ite, MaterialEditTextGroup v) {
    String username = v.getText().toString();
    if (currentUsernames.editableUsername.equals(username)) {
      return;
    }
    doUsernameCheck(username);
  }

  private void setEditableUsername (String newUsername) {
    final String oldUsername = this.currentUsernames.editableUsername;
    this.currentUsernames.editableUsername = newUsername;

    boolean hadUsername = !StringUtils.isEmpty(oldUsername);
    boolean hasUsername = !StringUtils.isEmpty(newUsername);

    if (hadUsername) {
      activeUsernames.remove(oldUsername);
    }
    if (hasUsername) {
      activeUsernames.add(newUsername);
    }
    checkReorderHint();

    int index = hadUsername ? adapter.indexOfView(item -> item.getId() == R.id.btn_username && item.getBoolValue()) : -1;

    if (hadUsername != hasUsername) {
      if (hasUsername) {
        index = adapter.indexOfViewById(R.id.btn_username);
        if (index != -1) {
          adapter.getItems().addAll(index, Arrays.asList(
            valueOfUsername(newUsername, true),
            new ListItem(ListItem.TYPE_SEPARATOR_FULL)
          ));
          adapter.notifyItemRangeInserted(index, 2);
          if (NEED_ACTIVE_USERNAMES_FILLING) {
            activeUsernamesDecoration[1] += 2;
          }
        }
      } else if (index != -1) {
        boolean wasTop = index - 2 >= 0 && adapter.getItems().get(index - 2).getId() != R.id.btn_username;
        adapter.removeRange(wasTop ? index : index - 1, 2);
        if (NEED_ACTIVE_USERNAMES_FILLING) {
          activeUsernamesDecoration[1] -= 2;
        }
      }
    } else if (hasUsername && index != -1) {
      ListItem item = adapter.getItems().get(index);
      item.setStringValue(newUsername);
      item.setString(newUsername);
      adapter.updateValuedSettingByPosition(index);
    }
  }

  private void doUsernameCheck (String username) {
    if (!isDoneVisible()) {
      setDoneVisible(true);
    }
    setEditableUsername(username);
    cancelCheckRequest();

    if (description.setStringIfChanged(genDescription())) {
      adapter.updateSimpleItemById(R.id.description);
    }

    if (sourceUsernames.editableUsername.equals(username) && !username.isEmpty()) {
      checkedItem.setTextColorId(ColorId.textSecure);
      checkedItem.setString(getResultString(ResultType.AVAILABLE));
      adapter.updateEditTextById(R.id.input, true, false);
      setState(STATE_CHECKED);
    } else {
      adapter.updateEditTextById(R.id.input, false, false);
      setState(username.length() < MIN_USERNAME_LENGTH || !TD.matchUsername(username) || username.length() > TdConstants.MAX_USERNAME_LENGTH ? STATE_NONE : STATE_CHECKING);
    }
    if (state == STATE_CHECKING) {
      checkUsername();
    }
  }

  private int selectResource (int myRes, int otherRes) {
    return selectResource(myRes, otherRes, otherRes, otherRes);
  }

  private int selectResource (int myRes, int chatRes, int channelRes, int botRes) {
    if (isBotEdit) {
      return botRes;
    } else if (chatId != 0) {
      if (tdlib.isChannel(chatId)) {
        return channelRes;
      } else {
        return chatRes;
      }
    } else {
      return myRes;
    }
  }

  private void checkReorderHint () {
    boolean canReorder = activeUsernames.size() > 1;
    if (this.hasReorderHint != canReorder) {
      this.hasReorderHint = canReorder;
      if (canReorder) {
        adapter.addItem(adapter.getItemCount(), reorderHintItem);
      } else {
        adapter.removeItem(reorderHintItem);
      }
    }
  }

  private CancellableRunnable checkRunnable;

  private void checkUsername () {
    checkRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        checkUsernameInternal(false, this);
      }
    };
    checkRunnable.removeOnCancel(UI.getAppHandler());
    UI.post(checkRunnable, 350l);
  }

  private final Set<String> togglingUsernames = new LinkedHashSet<>();

  private TooltipOverlayView.TooltipInfo activatingHint;

  private void hideActivatingHint () {
    if (activatingHint != null) {
      activatingHint.hide(true);
      activatingHint = null;
    }
  }

  private void showActivatingHint (View view, boolean isActivating) {
    hideActivatingHint();
    @StringRes int stringRes = isActivating ?
      selectResource(R.string.ActivatingUsername, R.string.ActivatingLink) :
      selectResource(R.string.DeactivatingUsername, R.string.DeactivatingLink);
    activatingHint = context.tooltipManager()
      .builder(view)
      .show(this, tdlib, R.drawable.baseline_info_24, Lang.getMarkdownStringSecure(this, stringRes));
  }

  private int toUsernameIndex (int adapterIndex) {
    final int firstUsernameIndex = adapter.indexOfViewById(R.id.btn_username);
    return (adapterIndex - firstUsernameIndex) / 2;
  }

  private void moveUsername (int fromIndex, int toIndex) {
    if (fromIndex == toIndex) {
      return;
    }
    final int firstUsernameIndex = adapter.indexOfViewById(R.id.btn_username);
    int displayFromPosition = firstUsernameIndex + fromIndex * 2;
    int displayToPosition = firstUsernameIndex + toIndex * 2;
    adapter.moveItem(displayFromPosition, displayToPosition);
    if (toIndex >= fromIndex) {
      adapter.removeItem(displayFromPosition);
      displayToPosition--;
      adapter.addItem(displayToPosition, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    } else {
      adapter.removeItem(displayFromPosition);
      adapter.addItem(displayToPosition + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
  }

  private static final boolean TEST_MULTI_USERNAMES_UI = false;

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    ListItem item = (ListItem) v.getTag();
    if (item == null) {
      return;
    }
    if (viewId == R.id.btn_username) {
      final SettingView settingView = (SettingView) v;

      final String username = item.getStringValue();
      final boolean isEditable = item.getBoolValue();
      if (isEditable) {
        context.tooltipManager()
          .builder(settingView.findCheckBox())
          .show(this, tdlib, R.drawable.baseline_error_24, Lang.getMarkdownStringSecure(this, selectResource(R.string.PrimaryUsernameHint, R.string.PrimaryLinkHint, R.string.PrimaryLinkHint, R.string.PrimaryLinkBotHint)));
        return;
      }

      final boolean isLoading = togglingUsernames.contains(username);
      if (isLoading) {
        showActivatingHint(settingView.findCheckBox(), !item.isSelected());
        return;
      }

      boolean isActive = !item.isSelected();
      togglingUsernames.add(username);
      settingView.setVisuallyEnabled(false, true);

      TdApi.Function<TdApi.Ok> function;
      if (isBotEdit) {
        function = new TdApi.ToggleBotUsernameIsActive(tdlib.chatUserId(chatId), username, isActive);
      } else if (chatId != 0) {
        function = new TdApi.ToggleSupergroupUsernameIsActive(ChatId.toSupergroupId(chatId), username, isActive);
      } else {
        function = new TdApi.ToggleUsernameIsActive(username, isActive);
      }
      tdlib.send(function, (ok, error) -> runOnUiThreadOptional(() -> {
        hideActivatingHint();
        togglingUsernames.remove(username);
        int index = adapter.indexOfView(item);
        boolean isError = error != null && !TEST_MULTI_USERNAMES_UI;
        if (isError) {
          if (index != -1) {
            View view = recyclerView.getLayoutManager().findViewByPosition(index);
            if (view instanceof SettingView) {
              context.tooltipManager()
                .builder(((SettingView) view).findCheckBox())
                .show(this, tdlib, R.drawable.baseline_error_24, TD.toErrorString(error));
            }
          }
        } else {
          if (isActive) {
            activeUsernames.add(username);
          } else {
            activeUsernames.remove(username);
          }
          item.setSelected(isActive);
        }
        adapter.updateValuedSetting(item);

        if (!isError) {
          // Move item
          int currentIndex = 0;
          while (index - currentIndex * 2 - 2 >= 0 && adapter.getItem(index - currentIndex * 2 - 2).getId() == R.id.btn_username) {
            currentIndex++;
          }
          int newIndex = isActive ? activeUsernames.size() - 1 : activeUsernames.size();
          moveUsername(currentIndex, newIndex);
          checkReorderHint();
        }
      }, null, TEST_MULTI_USERNAMES_UI ? 1000L : 0));
    } else if (viewId == R.id.chat) {
      TGFoundChat chat = (TGFoundChat) item.getData();
      if (chat == null) {
        return;
      }
      String publicLink = tdlib.tMeHost() + tdlib.chatUsername(chat.getChatId());
      showOptions(publicLink, new int[] {R.id.btn_delete, R.id.btn_openChat}, new String[] {Lang.getString(R.string.ChatLinkRemove), Lang.getString(R.string.ChatLinkView)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_visibility_24}, (itemView, id) -> {
        if (id == R.id.btn_openChat) {
          tdlib.ui().openChat(this, chat.getChatId(), new TdlibUi.ChatOpenParameters().keepStack());
        } else if (id == R.id.btn_delete) {
          showOptions(Lang.getStringBold(R.string.ChatLinkRemoveAlert, tdlib.chatTitle(chat.getChatId()), publicLink), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ChatLinkRemove), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (resultItemView, confirmId) -> {
            if (confirmId == R.id.btn_delete && !isInProgress()) {
              setInProgress(true);
              tdlib.send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chat.getChatId()), null), (ok, error) -> runOnUiThreadOptional(() -> {
                setInProgress(false);
                if (error != null) {
                  context.tooltipManager().builder(getDoneButton())
                      .show(this, tdlib, R.drawable.baseline_error_24, TD.toErrorString(error));
                } else {
                  doUsernameCheck(currentUsernames.editableUsername);
                  if (needAlwaysShowKeyboardOnFocusView()) {
                    Keyboard.show(getLockFocusView());
                  }
                }
              }));
            }
            return true;
          });
        }
        return true;
      });
    }
  }

  private void cancelCheckRequest () {
    if (checkRunnable != null) {
      checkRunnable.cancel();
      checkRunnable = null;
    }
    setPublicChats(null);
  }

  private boolean hasUnsavedChanges () {
    return !StringUtils.equalsOrBothEmpty(sourceUsernames.editableUsername, currentUsernames.editableUsername) ||
      !Arrays.equals(sourceUsernames.activeUsernames, currentUsernames.activeUsernames);
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return !hasUnsavedChanges();
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (hasUnsavedChanges()) {
      if (commit) {
        showUnsavedChangesPromptBeforeLeaving(null);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private void checkUsernameInternal (boolean forceIgnoreChatId, final CancellableRunnable runnable) {
    final String username = currentUsernames.editableUsername;
    final long requestChatId = forceIgnoreChatId || ChatId.isBasicGroup(chatId) ? 0 : chatId != 0 ? chatId : tdlib.selfChatId();
    tdlib.send(new TdApi.CheckChatUsername(requestChatId, username), (checkChatUsernameResult, checkChatUsernameError) -> runOnUiThreadOptional(() -> {
      if (!currentUsernames.editableUsername.equals(username))
        return;
      if (!forceIgnoreChatId && checkChatUsernameError != null && checkChatUsernameError.code == 400 && "Chat not found".equals(checkChatUsernameError.message)) {
        // FIXME[TDLib]: rudimentary chat existence check for current user id
        checkUsernameInternal(true, runnable);
        return;
      }

      boolean isAvailable = false;
      CharSequence result;
      boolean needOccupiedList = false;

      if (checkChatUsernameError != null) {
        result = TD.toErrorString(checkChatUsernameError);
      } else {
        switch (checkChatUsernameResult.getConstructor()) {
          case TdApi.CheckChatUsernameResultOk.CONSTRUCTOR:
            isAvailable = true;
            result = getResultString(ResultType.AVAILABLE);
            break;
          case TdApi.CheckChatUsernameResultUsernameOccupied.CONSTRUCTOR:
            result = getResultString(ResultType.OCCUPIED);
            break;
          case TdApi.CheckChatUsernameResultUsernamePurchasable.CONSTRUCTOR:
            result = getResultString(ResultType.PURCHASABLE);
            break;
          case TdApi.CheckChatUsernameResultPublicChatsTooMany.CONSTRUCTOR:
            result = Lang.getString(R.string.TooManyPublicLinks);
            needOccupiedList = true;
            break;
          case TdApi.CheckChatUsernameResultUsernameInvalid.CONSTRUCTOR:
            result = Lang.getString(chatId != 0 ? R.string.LinkInvalid : R.string.UsernameInvalid);
            break;
          case TdApi.CheckChatUsernameResultPublicGroupsUnavailable.CONSTRUCTOR:
            result = Lang.getString(R.string.PublicGroupsUnavailable);
            break;
          default:
            Td.assertCheckChatUsernameResult_936bb8da();
            throw Td.unsupported(checkChatUsernameResult);
        }
      }
      checkedItem.setString(result);
      checkedItem.setTextColorId(isAvailable ? ColorId.textSecure : ColorId.textNegative);
      setState(STATE_CHECKED);
      adapter.updateEditTextById(R.id.input, isAvailable, !isAvailable);
      if (needOccupiedList && chatId != 0) {
        tdlib.send(new TdApi.GetCreatedPublicChats(), (publicChats, error) -> {
          if (publicChats == null) {
            return;
          }
          List<TdApi.Chat> chats = tdlib.chats(publicChats.chatIds);
          if (chats.isEmpty()) {
            return;
          }
          final List<TGFoundChat> foundChats = new ArrayList<>(chats.size());
          for (TdApi.Chat chat : chats) {
            TGFoundChat foundChat = new TGFoundChat(tdlib, null, chat, true, null);
            foundChat.setNoUnread();
            foundChat.setUseTme();
            foundChats.add(foundChat);
          }
          runOnUiThreadOptional(() -> {
            if (checkRunnable == runnable && runnable.isPending()) {
              setPublicChats(foundChats);
            }
          });
        });
      }
    }));
  }

  private List<TGFoundChat> occupiedChats;

  private void setPublicChats (List<TGFoundChat> chats) {
    if (occupiedChats == null && chats == null) {
      return;
    }
    if (this.occupiedChats != null) {
      int i = adapter.indexOfViewById(R.id.occupiedChats);
      if (i != -1) {
        adapter.removeRange(i, 1 + occupiedChats.size() * 2);
        decoration.removeLastRange();
      }
    }
    this.occupiedChats = chats;
    if (chats != null) {
      List<ListItem> items = adapter.getItems();
      int i = items.size();
      boolean first = true;
      for (TGFoundChat chat : chats) {
        if (first) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, R.id.occupiedChats));
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_CHAT_BETTER, R.id.chat).setLongId(chat.getId()).setData(chat));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      decoration.addRange(i + 1, items.size() - 1);
      adapter.notifyItemRangeInserted(i, items.size() - i);
    }
    recyclerView.invalidateItemDecorations();
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentUsernames.editableUsername : null);
  }

  private static final int MIN_USERNAME_LENGTH = 1;

  @Override
  protected boolean onDoneClick () {
    String currentUsername = currentUsernames.editableUsername;
    if (currentUsername.isEmpty()) {
      setUsername("");
    } else if (currentUsername.length() < MIN_USERNAME_LENGTH) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidShort : R.string.UsernameInvalidShort));
    } else if (currentUsername.length() > TdConstants.MAX_USERNAME_LENGTH) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidLong : R.string.UsernameInvalidLong));
    } else if (StringUtils.isNumeric(currentUsername.charAt(0))) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalidStartNumber : R.string.UsernameInvalidStartNumber));
    } else if (!TD.matchUsername(currentUsername)) {
      showError(Lang.getString(chatId != 0 ? R.string.LinkInvalid : R.string.UsernameInvalid));
    } else {
      setUsername(currentUsername);
    }
    return true;
  }

  private void setUsername (final String username) {
    setUsername(username, true);
  }

  private String[] obtainActiveUsernames () {
    Set<String> usernames = new LinkedHashSet<>();
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_username && item.isSelected()) {
        usernames.add(item.getStringValue());
      }
    }
    return usernames.toArray(new String[0]);
  }

  private void saveUsernamesOrder (boolean needExit) {
    String[] activeUsernames = obtainActiveUsernames();
    TdApi.Function<TdApi.Ok> function;
    if (isBotEdit) {
      function = new TdApi.ReorderBotActiveUsernames(tdlib.chatUserId(chatId), activeUsernames);
    } else if (chatId != 0) {
      function = new TdApi.ReorderSupergroupActiveUsernames(ChatId.toSupergroupId(chatId), activeUsernames);
    } else {
      function = new TdApi.ReorderActiveUsernames(activeUsernames);
    }
    tdlib.send(function, (ok, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        if (!isDoneVisible()) {
          setDoneVisible(true, false);
        }
        currentUsernames.activeUsernames = activeUsernames;
        context.tooltipManager().builder(getDoneButton())
          .show(this, tdlib, R.drawable.baseline_error_24, TD.toErrorString(error));
      } else {
        if (needExit) {
          onSaveCompleted();
        }
      }
    }));
  }

  private void setUsername (final String username, boolean needPrompt) {
    if (!isInProgress()) {
      if (ChatId.isBasicGroup(chatId) && needPrompt) {
        if (StringUtils.isEmpty(username)) {
          onSaveCompleted();
        } else {
          showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), () -> setUsername(username, false));
        }
        return;
      }
      setInProgress(true);

      Tdlib.ResultHandler<TdApi.Ok> handler = (ok, error) -> runOnUiThreadOptional(() -> {
        setInProgress(false);
        if (error == null) {
          if (hasSecondaryUsernames) {
            saveUsernamesOrder(true);
          } else {
            onSaveCompleted();
          }
        } else {
          showError(TD.toErrorString(error));
        }
      });

      if (chatId != 0) {
        if (ChatId.isBasicGroup(chatId)) {
          tdlib.upgradeToSupergroup(chatId, (oldChatId, newChatId, error) -> {
            if (newChatId != 0) {
              setArguments(new Args(newChatId));
              tdlib.send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chatId), username), handler);
            } else {
              handler.onResult(null, error != null ? error : new TdApi.Error(-1, "Failed to upgrade to supergroup"));
            }
          });
        } else {
          tdlib.send(new TdApi.SetSupergroupUsername(ChatId.toSupergroupId(chatId), username), handler);
        }
      } else {
        tdlib.send(new TdApi.SetUsername(username), handler);
      }
    }
  }

  private void showError (String error) {
    checkedItem.setString(error);
    checkedItem.setTextColorId(ColorId.textNegative);
    adapter.updateEditTextById(R.id.input, false, true);
    setState(STATE_CHECKED);
  }

  private static final int STATE_NONE = 0;
  private static final int STATE_CHECKING = 1;
  private static final int STATE_CHECKED = 2;

  private int state;

  private void setState (int state) {
    if (this.state != state) {
      int prevState = this.state;
      this.state = state;

      if (prevState != STATE_NONE && state != STATE_NONE) {
        adapter.setItem(1, state == STATE_CHECKED ? checkedItem : checkingItem);
      } else if (prevState == STATE_NONE) {
        adapter.addItem(1, state == STATE_CHECKED ? checkedItem : checkingItem);
        decoration.firstRange()[1]++;
        if (NEED_ACTIVE_USERNAMES_FILLING && hasSecondaryUsernames) {
          activeUsernamesDecoration[0]++;
        }
      } else {
        adapter.removeItem(1);
        decoration.firstRange()[1]--;
        if (NEED_ACTIVE_USERNAMES_FILLING && hasSecondaryUsernames) {
          activeUsernamesDecoration[0]--;
        }
      }
    } else if (state == STATE_CHECKED) {
      adapter.updateSimpleItemById(checkedItem.getId());
    }
  }

  private CharSequence helpSequence;

  private CharSequence genDescription () {
    if (helpSequence == null) {
      if (isBotEdit) {
        String fragmentUsername = new Uri.Builder()
          .scheme("https")
          .authority(TdConstants.FRAGMENT_HOST)
          .path("username")
          .build()
          .toString();
        helpSequence = Lang.getMarkdownStringSecure(this, R.string.EditBotUsernameHint, fragmentUsername);
      } else {
        @StringRes int res;
        if (chatId != 0) {
          if (tdlib.isChannel(chatId)) {
            res = R.string.LinkChannelHelp;
          } else {
            res = R.string.LinkGroupHelp;
          }
        } else {
          res = R.string.UsernameHelp;
        }
        helpSequence = Strings.replaceBoldTokens(Lang.getString(res), ColorId.textLight);
      }
    }
    int usernameLength = currentUsernames.editableUsername.length();
    if (usernameLength >= MIN_USERNAME_LENGTH && usernameLength <= TdConstants.MAX_USERNAME_LENGTH && chatId == 0) {
      SpannableStringBuilder b = new SpannableStringBuilder(helpSequence);
      b.append("\n\n");
      b.append(Lang.getString(currentUsernames.editableUsername.equals(sourceUsernames.editableUsername) ? R.string.ThisLinkOpens : R.string.ThisLinkWillOpen));
      b.append(" ");
      String tMeUrl = tdlib.tMeUrl(currentUsernames.editableUsername);
      b.append(tMeUrl);
      b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoRegular(), ColorId.textLink), b.length() - tMeUrl.length(), b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return b;
    }
    return helpSequence;
  }

  @IntDef({
    ResultType.AVAILABLE,
    ResultType.OCCUPIED,
    ResultType.PURCHASABLE
  })
  private @interface ResultType {
    int AVAILABLE = 0, OCCUPIED = 1, PURCHASABLE = 2;
  }

  private CharSequence getResultString (@ResultType int resultType) {
    switch (resultType) {
      case ResultType.AVAILABLE: {
        SpannableStringBuilder b = new SpannableStringBuilder(Lang.getString(sourceUsernames.editableUsername.equals(currentUsernames.editableUsername) ? (chatId != 0 ? R.string.LinkCurrent : R.string.UsernameCurrent) : R.string.UsernameAvailable, currentUsernames.editableUsername));
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), ColorId.textSecure), 0, currentUsernames.editableUsername.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
      }
      case ResultType.OCCUPIED: {
        return Lang.getString(chatId != 0 ? R.string.LinkInUse : R.string.UsernameInUse);
      }
      case ResultType.PURCHASABLE: {
        String learnMoreUrl = new Uri.Builder()
          .scheme("https")
          .authority(TdConstants.FRAGMENT_HOST)
          .path("username/" + currentUsernames.editableUsername)
          .build()
          .toString();
        return Lang.getMarkdownStringSecure(this, chatId != 0 ? R.string.LinkPurchasable : R.string.UsernamePurchasable, learnMoreUrl);
      }
      default: {
        throw new IllegalArgumentException(Integer.toString(resultType));
      }
    }
  }
}
