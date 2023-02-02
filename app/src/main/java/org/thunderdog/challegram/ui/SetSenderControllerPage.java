package org.thunderdog.challegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.EmptySmartView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class SetSenderControllerPage extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<SetSenderController.Args> implements
        BottomSheetViewController.BottomSheetBaseControllerPage, Menu {

  private final Tdlib tdlib;
  private TdApi.Chat chat;
  private TdApi.ChatMessageSender[] chatAvailableSenders;
  private TdApi.MessageSender currentSender;
  private final SetSenderController parent;

  public SetSenderControllerPage(@NonNull Context context, Tdlib tdlib, SetSenderController parent) {
    super(context, tdlib);
    this.tdlib = tdlib;
    this.parent = parent;
  }

  private CustomRecyclerView recyclerView;
  private SettingsAdapter adapter;

  public CustomRecyclerView getRecyclerView () {
    return recyclerView;
  }

  private HeaderView createHeaderView () {
    HeaderView headerView = new HeaderView(context);
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setColor(Theme.fillingColor());
    headerView.getFilling().setShadowAlpha(0);
    headerView.getBackButton().setIsReverse(true);
    headerView.setWillNotDraw(false);
    addThemeInvalidateListener(headerView);
    return headerView;
  };

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.headerView = createHeaderView();
    this.recyclerView = recyclerView;

    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        int bottom = view instanceof EmptySmartView && getKeyboardState() ? -Keyboard.getSize(Keyboard.getSize()): 0;
        outRect.set(0, bottom, 0, 0);
      }
    });

    adapter = new SettingsAdapter(this, this::onItemClickListener, this);
    adapter.setNoEmptyProgress();
    recyclerView.setAdapter(adapter);
    buildCells();
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    boolean result = super.onKeyboardStateChanged(visible);
    recyclerView.invalidateItemDecorations();
    return result;
  }

  private void onItemClickListener (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item == null || !(item.getData() instanceof DoubleTextWrapper)) {
      return;
    }
    DoubleTextWrapper content = (DoubleTextWrapper) item.getData();
    TdApi.ChatMessageSender sender = content.getChatMessageSender();
    if (sender == null) return;

    if (content.isPremiumLocked()) {
      context.tooltipManager().builder(view).locate((v, rect) -> {
        rect.left = view.getMeasuredWidth() - Screen.dp(18 + 16);
        rect.right = view.getMeasuredWidth() - Screen.dp(18);
        rect.top += Screen.dp(20);
        rect.bottom -= Screen.dp(20);
      }).show(tdlib, R.string.error_PREMIUM_ACCOUNT_REQUIRED)
        .hideDelayed(2000, TimeUnit.MILLISECONDS);
      return;
    }

    if (delegate != null) {
      delegate.onClickMessageSender(sender);
      parent.hidePopupWindow(true);
    }
  }

  private Delegate delegate;

  public interface Delegate {
    void onClickMessageSender (TdApi.ChatMessageSender sender);
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public HeaderView getHeaderView () {
    return headerView;
  }

  public void startLeaveSearchMode () {
    closeSearchMode(null);
  }

  private boolean search (TdApi.MessageSender messageSender, String rawQuery) {
    String senderName = tdlib.senderName(messageSender);
    String senderUsername = tdlib.senderUsername(messageSender);
    boolean searchOnlyByUsername = rawQuery.startsWith("@");
    String query = searchOnlyByUsername ? rawQuery.substring(1) : rawQuery;

    if (StringUtils.isEmpty(rawQuery)) {
      return true;
    }

    return (!StringUtils.isEmpty(senderUsername) && Highlight.isExactMatch(Highlight.valueOf(senderUsername, query)))
      || (!searchOnlyByUsername && Highlight.isExactMatch(Highlight.valueOf(senderName, query)))
      || (searchOnlyByUsername && !StringUtils.isEmpty(senderUsername) && StringUtils.isEmpty(query));
  }

  private boolean isEmpty;

  private void buildCells () {
    final String lastQuery = getLastSearchInput();
    final boolean inSearchMode = inSearchMode() && !StringUtils.isEmpty(lastQuery);

    ArrayList<ListItem> items = new ArrayList<>(Math.max(chatAvailableSenders.length * 2 - 1, 0));
    boolean isFirst = true;
    for (int i = 0; i < chatAvailableSenders.length; i++) {
      TdApi.ChatMessageSender sender = chatAvailableSenders[i];
      DoubleTextWrapper item = parseObject(sender);
      if (item != null) {
        if (inSearchMode && !search(sender.sender, lastQuery)) {
          continue;
        }

        ListItem separatorItem = new ListItem(ListItem.TYPE_SEPARATOR);
        ListItem contentItem = new ListItem(ListItem.TYPE_CHAT_SMALL).setData(item);
        if (!isFirst) {
          items.add(separatorItem);
        } else {
          isFirst = false;
        }
        items.add(contentItem);
      }
    }

    isEmpty = items.isEmpty();

    if (items.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_SMART_EMPTY)
        .setIntValue(EmptySmartView.MODE_EMPTY_RESULTS)
        .setHeight(Screen.dp(430))
        .setBoolValue(false));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.replaceItems(items);
  }

  private DoubleTextWrapper parseObject (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.ChatMessageSender.CONSTRUCTOR: {
        TdApi.ChatMessageSender sender = (TdApi.ChatMessageSender) object;
        if (tdlib.isUser(sender.sender)) {
          DoubleTextWrapper d = new DoubleTextWrapper(tdlib, Td.getSenderUserId(sender.sender), true);
          d.setChatMessageSender(sender);
          d.setForcedSubtitle(Lang.getString(R.string.YourAccount));
          d.setDrawFakeCheckbox(Td.getSenderId(currentSender) == Td.getSenderId(sender.sender));
          return d;
        } else {
          TdApi.Chat chat = tdlib.chat(Td.getSenderId(sender.sender));
          if (chat != null) {
            DoubleTextWrapper d = new DoubleTextWrapper(tdlib, chat);
            d.setChatMessageSender(sender);
            d.setDrawFakeCheckbox(Td.getSenderId(currentSender) == Td.getSenderId(sender.sender));
            if (Td.getSenderId(sender.sender) == this.chat.id) {
              d.setForcedSubtitle(Lang.getString(R.string.AnonymousAdmin));
            } else {
              String username = tdlib.chatUsername(chat);
              if (!StringUtils.isEmpty(username)) {
                d.setForcedSubtitle("@" + username);
              }
            }
            return d;
          }
        }
        break;
      }
    }
    return null;
  }

  @Override
  public boolean needTopDecorationOffsets (RecyclerView parent) {
    if (inSearchMode() && this.parent.getLickViewFactor() == 1f) {
      return false;
    }
    return super.needTopDecorationOffsets(parent);
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    if (inSearchMode() && this.parent.getLickViewFactor() == 1f) {
      return false;
    }
    return super.needBottomDecorationOffsets(parent);
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
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
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this, getHeaderIconColorId()).setTouchDownListener((v, e) -> {
        hideSoftwareKeyboard();
      });
    }
    if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }



  private int awaitingChatSearchOpen;

  @Override
  protected void startHeaderTransformAnimator (ValueAnimator animator, int mode, boolean open) {
    super.startHeaderTransformAnimator(animator, mode, open);
    if (awaitingChatSearchOpen > 0) {
      final int scrollBy = awaitingChatSearchOpen;
      awaitingChatSearchOpen = 0;
      runOnUiThread(() -> smoothScrollBy(scrollBy), 50);
      awaitingChatSearchOpen = 0;
    }
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    final int top = parent.getTopEdge();
    if (top > 0) {
      awaitingChatSearchOpen = top;
    } else {
      //setAutoScrollFinished(true);
    }

    buildCells();
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    super.onAfterLeaveSearchMode();
    buildCells();
    recyclerView.scrollBy(0, (parent.getTargetHeight() - HeaderView.getTopOffset()));
  }

  @Override
  protected void onSearchInputChanged (String input) {
    super.onSearchInputChanged(input);
    buildCells();
  }


  private void smoothScrollBy (int y) {
    if (y == 0) {
      recyclerView.stopScroll();
    }
    //setAutoScrollFinished(false);
    /*if (PREVENT_HEADER_ANIMATOR) {
      totalScrollingBy = y;
      autoScrolling = true;
    }*/
    recyclerView.smoothScrollBy(0, y);
  }

  public boolean closeSearchModeByBackPress (boolean fromTop) {
    return true;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSearchMode()) {
      closeSearchMode(null);
      return true;
    }
    return false;
  }

  @Override
  protected View getSearchAntagonistView () {
    return recyclerView;
  }

  @Override
  public void setArguments (SetSenderController.Args args) {
    super.setArguments(args);
    this.chat = args.chat;
    this.currentSender = args.currentSender;
    this.chatAvailableSenders = args.chatAvailableSenders;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SendAs);
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_icon;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_WRITABLE | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public int getId () {
    return R.id.controller_sender;
  }

  @Override
  public int getItemsHeight (RecyclerView parent) {
    if (isEmpty) {
      return 0;
    }

    return adapter.measureHeight(-1);
  }

  public static class Args {
    public final TdApi.Chat chat;
    public final TdApi.ChatMessageSender[] chatAvailableSenders;
    public final TdApi.MessageSender currentSender;

    public Args (TdApi.Chat chat, TdApi.ChatMessageSender[] chatAvailableSenders, TdApi.MessageSender currentSender) {
      this.chatAvailableSenders = chatAvailableSenders;
      this.currentSender = currentSender;
      this.chat = chat;
    }
  }
}
