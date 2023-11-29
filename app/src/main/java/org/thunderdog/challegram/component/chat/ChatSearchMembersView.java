package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.Td;

public class ChatSearchMembersView extends FrameLayout implements TdlibCache.BasicGroupDataChangeListener {
  private final MessagesController controller;
  private final CustomRecyclerView usersRecyclerView;
  private final SettingsAdapter adapter;
  private final Tdlib tdlib;
  private long chatId;
  private Delegate delegate;

  private boolean isLoading;
  private @Nullable String currentQuery;
  private CancellableRunnable searchTask;

  private final ChatMembersSearcher membersSearcher;
  private boolean canLoadMore;
  private boolean useGlobalSearch;
  private ArrayList<DoubleTextWrapper> foundUsers;
  private ArrayList<ListItem> reuse = new ArrayList<>();


  public ChatSearchMembersView (@NonNull Context context, MessagesController controller) {
    super(context);
    this.controller = controller;
    this.chatId = controller.getChatId();
    this.tdlib = controller.tdlib();

    this.membersSearcher = new ChatMembersSearcher(tdlib);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    adapter = new SettingsAdapter(controller, this::onClickUserItem, controller) {
      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
        if (item.getViewType() == ListItem.TYPE_CHAT_SMALL) {
          TdApi.MessageSender sender = chatView.getSenderId();
          chatView.setCheckboxIconVisible(Td.getSenderId(sender) == Td.getSenderId(selectedSender));
        }
      }
    };
    adapter.setNoEmptyProgress();

    CustomItemAnimator itemAnimator = new CustomItemAnimator(null, 180L);
    usersRecyclerView = (CustomRecyclerView) Views.inflate(context, R.layout.recycler_custom, this);
    usersRecyclerView.setBackgroundColor(Theme.backgroundColor());
    usersRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    usersRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP, 0, 0, 0, Screen.dp(56)));
    usersRecyclerView.setAdapter(adapter);
    usersRecyclerView.setItemAnimator(itemAnimator);
    usersRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        loadMoreIfNeeded();
        List<ListItem> items = adapter.getItems();
        if (items.size() == 1 && items.get(0).getViewType() == ListItem.TYPE_SMART_EMPTY) {
          View view = recyclerView.getLayoutManager().findViewByPosition(0);
          if (view != null) {
            view.invalidate();
          }
        }
      }
    });
    controller.addThemeBackgroundColorListener(usersRecyclerView, ColorId.background);
    Views.setScrollBarPosition(usersRecyclerView);
    addView(usersRecyclerView);

    FrameLayoutFix footerView = new FrameLayoutFix(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        return true;
      }
    };
    ViewSupport.setThemedBackground(footerView, ColorId.filling, controller);
    footerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM));
    addView(footerView);

    for (int i = 0; i < 2; i++) {
      TextView button = new NoScrollTextView(context);
      int colorId = ColorId.textNeutral;
      button.setTextColor(Theme.getColor(colorId));
      controller.addThemeTextColorListener(button, colorId);
      button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      button.setOnClickListener(this::onClickControlButtons);
      button.setBackgroundResource(R.drawable.bg_btn_header);
      button.setGravity(Gravity.CENTER);
      button.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
      CharSequence text;
      if (i == 0) {
        button.setId(R.id.btn_cancel);
        button.setText(text = Lang.getString(R.string.Cancel).toUpperCase());
        button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(55f), (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM));
      } else {
        button.setId(R.id.btn_clear);
        button.setText(text = Lang.getString(R.string.Clear).toUpperCase());
        button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(55f), (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM));
      }
      Views.updateMediumTypeface(button, text);
      Views.setClickable(button);
      footerView.addView(button);
    }
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public void setMessageSender (TdApi.MessageSender sender) {
    setMessageSender(sender, true);
  }

  public void search (String query) {
    if (!StringUtils.equalsOrBothEmpty(currentQuery, query)) {
      loadMembers(query, true);
    }
  }

  public void prepare () {
    reset();
    loadMembers("", true);
  }

  public void dismiss () {
    reset();
  }

  private void onResult (ChatMembersSearcher.Result result) {
    final boolean canLoadMore = result.canLoadMore;
    final TdApi.Object object = result.result;
    final int usedFilter = result.type;
    UI.post(() -> {
      this.useGlobalSearch = usedFilter == ChatMembersSearcher.FILTER_TYPE_GLOBAL_LOCAL || usedFilter == ChatMembersSearcher.FILTER_TYPE_GLOBAL_GLOBAL;
      this.canLoadMore = canLoadMore;
      this.isLoading = false;
      processData(object);
    });
  }

  private void loadMembers (final String query, boolean isStart) {
    if (tdlib.isChannel(chatId)) return;

    boolean queryWasChanged = !StringUtils.equalsOrBothEmpty(query, currentQuery);
    if (this.isLoading && !queryWasChanged) {
      return;
    }

    this.currentQuery = query;
    this.isLoading = true;

    if (searchTask != null) {
      searchTask.cancel();
      searchTask = null;
    }

    if (queryWasChanged) {
      this.foundUsers = null;
      this.useGlobalSearch = false;
      this.canLoadMore = false;
      buildCells();
    }

    if (isStart || queryWasChanged) {
      searchTask = new CancellableRunnable() {
        @Override
        public void act () {
          membersSearcher.search(chatId, query, ChatSearchMembersView.this::onResult);
        }
      };
      searchTask.removeOnCancel(UI.getAppHandler());
      UI.post(searchTask, 300L);
    } else {
      membersSearcher.next(this::onResult);
    }
  }

  private void processData (final TdApi.Object object) {
    final ArrayList<DoubleTextWrapper> items;
    switch (object.getConstructor()) {
      case TdApi.Chats.CONSTRUCTOR: {
        long[] chatIds = ((TdApi.Chats) object).chatIds;
        items = new ArrayList<>(chatIds.length);
        for (long chatId : chatIds) {
          DoubleTextWrapper parsedItem;
          if (tdlib.isUserChat(chatId)) {
            parsedItem = parseObject(tdlib.chatUser(chatId));
          } else {
            parsedItem = parseObject(tdlib.chat(chatId));
          }
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        break;
      }
      case TdApi.Users.CONSTRUCTOR: {
        long[] userIds = ((TdApi.Users) object).userIds;
        ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
        items = new ArrayList<>(users.size());
        for (TdApi.User user : users) {
          DoubleTextWrapper parsedItem = parseObject(user);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        break;
      }
      case TdApi.ChatMembers.CONSTRUCTOR: {
        TdApi.ChatMembers members = (TdApi.ChatMembers) object;
        items = new ArrayList<>(members.members.length);
        for (TdApi.ChatMember member : members.members) {
          DoubleTextWrapper parsedItem = parseObject(member);
          if (parsedItem != null) {
            items.add(parsedItem);
          }
        }
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        items = new ArrayList<>(0);
        break;
      }
      default: {
        throw new UnsupportedOperationException(object.toString());
      }
    }

    if (controller.isDestroyed()) return;

    final boolean isFirstResult = foundUsers == null;

    if (foundUsers == null) {
      foundUsers = new ArrayList<>();
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null && tdlib.isSupergroup(chatId) && StringUtils.isEmpty(currentQuery)) {
        items.add(0, new DoubleTextWrapper(tdlib, chat));
      }
    }

    addItems(items, isFirstResult);
    bringMemberToTop(selectedSender);
    loadMoreIfNeeded();
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (foundUsers != null && foundUsers.isEmpty()) {
      if (adapter.getItems().size() == 1 && adapter.getItems().get(0).getViewType() == ListItem.TYPE_SMART_EMPTY) {
        return;
      }
      setOverScrollMode(View.OVER_SCROLL_NEVER);
      items.add(new ListItem(ListItem.TYPE_SMART_EMPTY).setIntValue(EmptySmartView.MODE_EMPTY_MEMBERS).setBoolValue(tdlib.isChannel(chatId)));
    } else if (foundUsers == null) {
      setOverScrollMode(View.OVER_SCROLL_NEVER);
      if (adapter.getItems().size() == 1 && adapter.getItems().get(0).getViewType() == ListItem.TYPE_SMART_PROGRESS) {
        return;
      }
      items.add(new ListItem(ListItem.TYPE_SMART_PROGRESS));
    } else {
      addItems(reuse, foundUsers, 0, items, null);
    }

    adapter.setItems(items, false);
  }

  private void addItems (ArrayList<DoubleTextWrapper> newData, boolean reset) {
    ArrayList<DoubleTextWrapper> target = foundUsers;

    if (target == null || target.isEmpty() || reset) {
      foundUsers = newData;
      buildCells();
    } else if (!newData.isEmpty()) {
      int startIndex = target.size();
      target.addAll(newData);
      addItems(reuse, target, startIndex, adapter.getItems(), adapter);
    }
  }

  private void addItems (List<ListItem> reuse, ArrayList<DoubleTextWrapper> dateItems, int offset, List<ListItem> out, @Nullable SettingsAdapter adapter) {
    if (dateItems.isEmpty()) {
      return;
    }

    final int currentEndIndex = out.size();
    reuse.clear();
    ArrayUtils.ensureCapacity(reuse, dateItems.size());

    final int size = dateItems.size();
    for (int i = offset; i < size; i++) {
      DoubleTextWrapper item = dateItems.get(i);
      if (offset == 0 && i == 0) {
        if (useGlobalSearch) {
          reuse.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, Lang.getString(R.string.Global), false));
          reuse.add(new ListItem(ListItem.TYPE_SHADOW_TOP, R.id.shadowTop));
        }
      } else {
        reuse.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      reuse.add(new ListItem(ListItem.TYPE_CHAT_SMALL).setData(item).setLongId(Td.getSenderId(item.getSenderId())));
    }

    if (offset == 0) {
      // reuse.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      // reuse.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW, R.id.search_counter).setIntValue(-1));
      out.addAll(reuse);
      if (adapter != null) {
        adapter.notifyItemRangeInserted(currentEndIndex, reuse.size());
      }
    } else {
      out.addAll(currentEndIndex /*- 2*/, reuse);
      if (adapter != null) {
        adapter.notifyItemRangeInserted(currentEndIndex /*- 2*/, reuse.size());
      }
    }

  }










  /////////////////////////////////////////////////////////

  private void reset () {
    isLoading = false;
    selectedSender = null;
    currentQuery = null;
    searchTask = null;
    reuse = new ArrayList<>();
    chatId = controller.getChatId();
    adapter.replaceItems(new ArrayList<>());
    foundUsers = null;
    useGlobalSearch = false;
    canLoadMore = false;
    isLoading = false;
    membersSearcher.reset();
  }

  private void loadMoreIfNeeded () {
    if ( canLoadMore && !isLoading ) {
      LinearLayoutManager manager = (LinearLayoutManager) usersRecyclerView.getLayoutManager();
      int lastVisibleItemPosition = manager.findLastVisibleItemPosition();
      if (lastVisibleItemPosition != -1 && lastVisibleItemPosition + 6 >= adapter.getItems().size()) {
        loadMembers(currentQuery, false);
      }
    }
  }



  private static int indexOfMember (ArrayList<DoubleTextWrapper> array, TdApi.MessageSender memberId) {
    if (array == null || array.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (DoubleTextWrapper wrapper : array) {
      if (Td.equalsTo(wrapper.getSenderId(), memberId)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private void bringMemberToTop (TdApi.MessageSender memberId) {
    bringMemberToTop(foundUsers, memberId);
  }

  private void bringMemberToTop (ArrayList<DoubleTextWrapper> array, TdApi.MessageSender memberId) {
    if (array == null || memberId == null)
      return;
    int fromIndex = indexOfMember(array, memberId);
    int newIndex = 0;
    if (!array.isEmpty() && tdlib.isSelfUserId(array.get(0).getUserId())) {
      newIndex++;
    }
    if (fromIndex != -1) {
      if (fromIndex > newIndex) {
        DoubleTextWrapper wrapper = array.remove(fromIndex);
        array.add(newIndex, wrapper);
        final int headerItemCount = getHeaderItemCount();
        ListItem contentItem, separatorItem;
        if (fromIndex == 0) {
          contentItem = adapter.getItems().remove(headerItemCount);
          separatorItem = adapter.getItems().remove(headerItemCount);
          adapter.notifyItemRangeRemoved(headerItemCount, 2);
        } else {
          int startIndex = headerItemCount + fromIndex * 2;
          contentItem = adapter.getItems().remove(startIndex);
          separatorItem = adapter.getItems().remove(startIndex - 1);
          adapter.notifyItemRangeRemoved(startIndex - 1, 2);
        }

        if (newIndex == 0) {
          adapter.getItems().add(headerItemCount, separatorItem);
          adapter.getItems().add(headerItemCount, contentItem);
          adapter.notifyItemRangeInserted(headerItemCount, 2);
        } else {
          int startIndex = headerItemCount + newIndex * 2 - 1;
          adapter.getItems().add(startIndex, contentItem);
          adapter.getItems().add(startIndex, separatorItem);
          adapter.notifyItemRangeInserted(startIndex, 2);
        }
      }
    }
  }



  /////////////////////////////////////////////////////////////

  private int getHeaderItemCount () {
    int i = adapter.indexOfViewById(R.id.shadowTop);
    return i != -1 ? i + 1 : 0;
  }

  private DoubleTextWrapper parseObject (TdApi.Object object) {
    if (object == null) return null;

    switch (object.getConstructor()) {
      case TdApi.Chat.CONSTRUCTOR: {
        return new DoubleTextWrapper(tdlib, (TdApi.Chat) object);
      }
      case TdApi.ChatMember.CONSTRUCTOR: {
        return DoubleTextWrapper.valueOf(tdlib, (TdApi.ChatMember) object, false, true);
      }
      case TdApi.User.CONSTRUCTOR: {
        return new DoubleTextWrapper(tdlib, ((TdApi.User) object).id, true);
      }
    }
    return null;
  }

  private TdApi.MessageSender selectedSender = null;

  private boolean setMessageSender (TdApi.MessageSender sender, boolean force) {
    final TdApi.MessageSender oldSenderId = selectedSender;
    final TdApi.MessageSender newSenderId = (Td.getSenderId(sender) == Td.getSenderId(oldSenderId) && !force) ? null : sender;

    if (Td.getSenderId(oldSenderId) == Td.getSenderId(newSenderId)) return false;

    selectedSender = newSenderId;

    final int oldSenderPosition = oldSenderId != null ? adapter.indexOfViewByLongId(Td.getSenderId(oldSenderId)) : -1;
    final int newSenderPosition = newSenderId != null ? adapter.indexOfViewByLongId(Td.getSenderId(newSenderId)) : -1;
    if (oldSenderPosition != -1) {
      adapter.notifyItemChanged(oldSenderPosition);
    }
    if (newSenderPosition != -1) {
      adapter.notifyItemChanged(newSenderPosition);
    }

    if (newSenderId != null) {
      usersRecyclerView.scrollToPosition(0);
      bringMemberToTop(newSenderId);
    }

    if (delegate != null && !force) {
      delegate.onSetMessageSender(newSenderId);
    }

    return true;
  }

  private void onClickControlButtons (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_cancel) {
      if (delegate != null) {
        delegate.onClose();
      }
    } else if (viewId == R.id.btn_clear) {
      if (!setMessageSender(null, false)) {
        if (delegate != null) {
          delegate.onClose();
        }
      }
    }
  }

  private void onClickUserItem (View v) {
    if (v instanceof SmallChatView) {
      SmallChatView chatView = (SmallChatView) v;
      TdApi.MessageSender sender = chatView.getSenderId();
      setMessageSender(sender, false);
    }
  }

  public interface Delegate {
    void onSetMessageSender (@Nullable TdApi.MessageSender sender);
    void onClose ();
  }
}
