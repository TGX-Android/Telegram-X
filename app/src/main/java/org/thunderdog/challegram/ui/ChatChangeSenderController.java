package org.thunderdog.challegram.ui;

import static org.thunderdog.challegram.widget.EmptySmartView.MODE_EMPTY_CHAT_SENDERS;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ChatSenderView;

import java.util.ArrayList;
import java.util.Objects;

public class ChatChangeSenderController extends MediaBottomBaseController<Void> implements Menu {

  private final long chatId;
  private SettingsAdapter settingsAdapter;

  private long currentSelectedSenderId = 0;
  private final ArrayList<TdlibSender> senders;

  public ChatChangeSenderController (MediaLayout context, long chatId, ArrayList<TdlibSender> availableSenders) {
    super(context, R.string.SendAs);
    this.chatId = chatId;
    this.senders = availableSenders;
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);

    recyclerView.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(recyclerView, R.id.theme_color_background);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    for (int i = 0; i < senders.size(); i++) {
      TdlibSender tdlibSender = senders.get(i);
      if (tdlibSender.isSelectedSender()) {
        this.currentSelectedSenderId = tdlibSender.getSenderId();
        break;
      }
    }
    settingsAdapter = new SettingsAdapter(this, v -> {
      if (v.getId() == R.id.btn_sender_enabled) {
        ChatSenderView chatSenderView = (ChatSenderView) v;
        onChatSenderSelected(chatSenderView.sender, v);
      }
    }, this) {
      @Override
      protected void setChatSender (ListItem item, int position, ChatSenderView userView, boolean isUpdate) {
        final long senderId = item.getLongId();
        final TdlibSender sender = getSender(senderId);
        if (sender != null) {
          userView.setSender(sender);
          userView.setChecked(isSenderSelected(senderId), isUpdate);
        }
      }
    };

    setSenders();
    initMetrics();
    setAdapter(settingsAdapter);
    return contentView;
  }

  private void setSenders () {
    ArrayList<ListItem> items = new ArrayList<>();
    boolean first = true;
    for (TdlibSender sender : senders) {
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_CHAT_SENDER_CHECKBOX, R.id.btn_sender_enabled, 0, 0, sender.getSenderId() == currentSelectedSenderId).setLongId(sender.getSenderId()));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    settingsAdapter.setItems(items, true);
  }

  private void setSenders (String query) {
    ArrayList<ListItem> items = new ArrayList<>();
    boolean first = true;
    for (TdlibSender sender : senders) {
      if ((sender.getName() != null && sender.getName().contains(query)) || sender.getUsername().contains(query)) {
        if (first) {
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(new ListItem(ListItem.TYPE_CHAT_SENDER_CHECKBOX, R.id.btn_sender_enabled, 0, 0, sender.getSenderId() == currentSelectedSenderId).setLongId(sender.getSenderId()));
      }
    }
    if (items.isEmpty()) {
      settingsAdapter.setItems(new ListItem[] {
        new ListItem(ListItem.TYPE_SMART_EMPTY).setIntValue(MODE_EMPTY_CHAT_SENDERS)
      }, false);
    } else {
      settingsAdapter.replaceItems(items);
    }
  }

  private TdlibSender getSender (long id) {
    for (TdlibSender sender : senders) {
      if (sender.getSenderId() == id) {
        return sender;
      }
    }
    return null;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (mediaLayout.getHeaderView().inSearchMode()) {
      mediaLayout.getHeaderView().closeSearchMode(true, null);
      headerView = mediaLayout.getHeaderView();
      setSenders();
      return true;
    }
    mediaLayout.hide(false);
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_changeSender;
  }

  private void onChatSenderSelected (TdlibSender sender, View v) {
    NavigationController navigation = UI.getContext(context).navigation();
    if (sender.isNeedsPremium() && navigation != null && tdlib.ui().showPremiumAlert(navigation.getCurrentStackItem(), v, TdlibUi.PremiumFeature.SEND_AS_CHANNEL)) {
      return;
    }

    long newSelectedSenderId = sender.getSenderId();
    if (currentSelectedSenderId == newSelectedSenderId) {
      return;
    }
    tdlib.client().send(new TdApi.SetChatMessageSender(chatId, sender.getSender()), tdlib.okHandler(() -> {
      this.currentSelectedSenderId = newSelectedSenderId;
      settingsAdapter.updateAllValuedSettingsById(R.id.btn_sender_enabled);
      mediaLayout.hide(false);
    }));
  }

  private boolean isSenderSelected (long senderId) {
    return senderId == currentSelectedSenderId;
  }

  @Override
  protected int getInitialContentHeight () {
    if (senders != null) {
      int initialContentHeight = Screen.dp(63) * senders.size();
      return Math.min(super.getInitialContentHeight(), initialContentHeight);
    }
    return super.getInitialContentHeight();
  }

  @Override
  protected boolean canExpandHeight () {
    return true;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  // Search

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search: {
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        mediaLayout.getHeaderView().openSearchMode();
        expandFully();
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  private String lastQuery;

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    if (!Objects.equals(lastQuery, query)) {
      if (senders != null && senders.size() > 0) {
        if (query.isEmpty()) {
          setSenders();
        } else {
          setSenders(query.trim());
        }
      }
      this.lastQuery = query;
    }
  }
}
