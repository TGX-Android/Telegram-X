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
 * File created on 20/07/2017
 */
package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.chat.MessageSendersAdapter;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGMessageSender;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ContactsController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.CustomImageView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.MessageSenderView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class MessageSendersController extends TelegramViewController<MessageSendersController.Args> implements
  FactorAnimator.Target, Runnable, PopupLayout.PopupHeightProvider,
  View.OnClickListener, Menu, PopupLayout.TouchSectionProvider,
  BaseView.ActionListProvider, ChatListListener, Filter<TdApi.Chat>, Client.ResultHandler {

  public static class Args {
    private TdApi.ChatList chatList;
    private TdApi.Message[] messages;
    private long messageThreadId;

    private TdApi.FormattedText text;

    private String shareText, shareButtonText;
    private String exportText;

    private TdApi.Game game;
    private long botUserId;
    private TdApi.Message botMessage;
    private boolean withUserScore;

    private TdApi.InputMessageContent customContent;

    private String filePath, fileMimeType;

    private TdApi.User contactUser;

    private TdApi.Sticker sticker;

    private boolean needOpenChat;

    private boolean allowCopyLink;

    private TdApi.MessageSender selected;

    private TdApi.Chat currentChat;

    private Runnable after;

    public Args setSelected(TdApi.MessageSender selected) {
      this.selected = selected;
      return this;
    }

    public Args setCurrentChat(TdApi.Chat currentChat) {
      this.currentChat = currentChat;
      return this;
    }

    public Args setAfter (Runnable after) {
      this.after = after;
      return this;
    }

  }

  public MessageSendersController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.ChatList chatList;
  private TdlibChatListSlice list;

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
  public void setArguments (Args args) {
    super.setArguments(args);
    this.chatList = args.chatList != null ? args.chatList : ChatPosition.CHAT_LIST_MAIN;
  }

  @Override
  public int getId () {
    return R.id.controller_share;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  private int getMenuItemCount () {
    int count = 1;
    return count;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search: {
        header.addSearchButton(menu, this, getHeaderIconColorId()).setTouchDownListener((v, e) -> {
          resetTopEnsuredState();
          hideSoftwareKeyboard();
        });
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
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
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        if (displayingSenders != null) {
          openSearchMode();
        }
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  private RelativeLayout contentView;
  private CustomRecyclerView chatSearchView;
  private CustomRecyclerView recyclerView;
  private MessageSendersAdapter usersAdapter;

  private SettingsAdapter adapter;
  private LickView lickView;
  private InputView inputView;
  private View fixView;

  private static class LickView extends View {
    public LickView (Context context) {
      super(context);
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (factor > 0f) {
        int bottom = getMeasuredHeight();
        int top = bottom - (int) ((float) bottom * factor);
        c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
      }
    }
  }

  private GridSpacingItemDecoration decoration;

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_WRITABLE | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }

  @Override
  protected View getSearchAntagonistView () {
    return contentView;
  }

  private SendButton sendButton;
  private SeparatorView bottomShadow;
  private HapticMenuHelper sendMenu;

  private LinearLayout bottomWrap;
  private View stubInputView;
  private ImageView okButton;

  private FrameLayoutFix wrapView;

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  public boolean accept (TdApi.Chat chat) {
    if (tdlib.chatAvailable(chat)) {
      Tdlib.RestrictionStatus restrictionStatus = tdlib.getRestrictionStatus(chat, R.id.right_sendMessages);
      return restrictionStatus == null || !restrictionStatus.isGlobal();
    }
    return false;
  }

  @Override
  protected View onCreateView (Context context) {
    list = new TdlibChatListSlice(tdlib, chatList, this, true) {
      @Override
      protected boolean modifySlice (List<Entry> slice, int currentSize) {
        int index = 0;
        for (Entry entry : slice) {
          if (tdlib.isSelfChat(entry.chat)) {
            if (currentSize > 0) {
              slice.remove(index);
              return true;
            } else if (index == 0 || ChatPosition.isPinned(entry.chat, chatList)) {
              return false;
            } else {
              slice.remove(index);
              entry.bringToTop();
              slice.add(0, entry);
              return true;
            }
          }
          index++;
        }
        if (currentSize == 0) {
          TdApi.Chat selfChat = tdlib.selfChat();
          if (selfChat != null && !ChatPosition.isPinned(selfChat, chatList)) {
            Entry entry = new Entry(selfChat, chatList, ChatPosition.findPosition(selfChat, chatList), true);
            entry.bringToTop();
            slice.add(0, entry);
            return true;
          }
          list.bringToTop(tdlib.selfChatId(), () -> new TdApi.CreatePrivateChat(tdlib.myUserId(), false), null);
        }
        return false;
      }
    };

    updateHeader();

    contentView = new RelativeLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // rootLayout.addView(contentView);

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(true);
    recyclerView = new CustomRecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY() - HeaderView.getSize(true)) && super.onTouchEvent(e);
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        post(MessageSendersController.this);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
        if (awaitLayout) {
          awaitLayout = false;
          autoScroll(awaitScrollBy);
        }
        launchExpansionAnimation();
      }

      @Override
      public void draw (Canvas c) {
        int top = detectTopRecyclerEdge();
        if (top == 0) {
          c.drawColor(Theme.fillingColor());
        } else {
          c.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
        }
        super.draw(c);
      }

      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
      }
    };
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          setAutoScrollFinished(true);
        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          resetTopEnsuredState();
          hideSoftwareKeyboard();
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkHeaderPosition();
//        if (list.canLoad() && !inSearchMode()) {
//          GridLayoutManager gridManager = (GridLayoutManager) recyclerView.getLayoutManager();
//          int i = gridManager.findLastVisibleItemPosition();
//          if (i >= adapter.getItemCount() - gridManager.getSpanCount()) {
//            list.loadMore(30, null);
//          }
//        }
      }
    });
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
//    recyclerView.setLayoutManager(new RtlGridLayoutManager(context, 4));
    recyclerView.setLayoutParams(params);

//    final GridLayoutManager.SpanSizeLookup lookup = new GridLayoutManager.SpanSizeLookup() {
//      @Override
//      public int getSpanSize (int position) {
//        return adapter.getItems().get(position).getViewType() == ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH ? 1 : ((GridLayoutManager) recyclerView.getLayoutManager()).getSpanCount();
//      }
//    };
//    decoration = new GridSpacingItemDecoration(calculateSpanCount(), Screen.dp(HORIZONTAL_PADDING_SIZE), true, false, false);
    /*decoration.setNeedDraw(true, SettingItem.TYPE_CHAT_VERTICAL_FULLWIDTH);
    decoration.setDrawColorId(R.id.theme_color_filling);*/
//    decoration.setSpanSizeLookup(lookup);
//    recyclerView.addItemDecoration(decoration);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
//        int position = parent.getChildAdapterPosition(view);
//        if (position != RecyclerView.NO_POSITION && recyclerView.getAdapter() != null
//          && position == recyclerView.getAdapter().getItemCount() - 1 && false /*&& mediaLayout.getCounterFactor() == 1f*/) {
//          outRect.set(0, 0, 0, MediaBottomBar.getBarHeight());
//        } else {
//          outRect.setEmpty();
//        }
        outRect.top = 0;
        outRect.bottom = 0;
        int i = parent.getChildAdapterPosition(view);
        if (i != RecyclerView.NO_POSITION) {
          //int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
          int spanCount = 1;
          if (i < spanCount) {
            int totalSpanCount = 0;
            for (int pos = 0; pos < i && totalSpanCount <= spanCount; pos++) {
              totalSpanCount += 1;//lookup.getSpanSize(pos);
            }
            if (i <= spanCount) {
              outRect.top = getContentOffset();
            }
          }
          final int itemCount = adapter.getItemCount();
          int addSize = itemCount % spanCount;
          if (addSize == 0)
            addSize = spanCount;
          if (i >= itemCount - addSize) {
            int rowCount = (int) Math.ceil((float) itemCount / (float) spanCount);
            int itemsHeight = rowCount * Screen.dp(86f) + Screen.dp(HORIZONTAL_PADDING_SIZE) + Screen.dp(VERTICAL_PADDING_SIZE);
            outRect.bottom = Math.max(0, get().getMeasuredHeight() == 0 ? Screen.currentHeight() : get().getMeasuredHeight() - HeaderView.getSize(true) - itemsHeight - 0);
          }
        }
      }
    });

//    ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanSizeLookup(lookup);

    contentView.addView(recyclerView);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    sendButton = new SendButton(context);
    sendButton.setLayoutParams(params);
    sendButton.setId(R.id.btn_send);
    sendButton.getChildAt(0).setId(R.id.btn_send);
    sendButton.getChildAt(0).setOnClickListener(this);
    addThemeInvalidateListener(sendButton);

    sendMenu = new HapticMenuHelper(view -> {
      if (selectedChats.size() == 0)
        return null;
      List<HapticMenuHelper.MenuItem> items = null;
      if (selectedChats.size() == 1) {
        items = tdlib.ui().fillDefaultHapticMenu(selectedChats.valueAt(0).getChatId(), false, false, false/*forceSendWithoutSound*/, true);
        if (items == null)
          items = new ArrayList<>(1);
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendAndOpen, Lang.getString(R.string.SendAndOpen), R.drawable.baseline_forward_24));
      }
      return items;
    }, (view, parentView) -> {
      switch (view.getId()) {
        case R.id.btn_sendScheduled:
//          tdlib.ui().showScheduleOptions(this, selectedChats.size() == 1 ? selectedChats.valueAt(0).getChatId() : 0, false, (sendOptions, disableMarkdown) -> performSend(needHideKeyboard, sendOptions, false), defaultSendOptions, null);
          break;
      }
    }, getThemeListeners(), null).attachToView(sendButton.getChildAt(0));

    // Bottom wrap
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
    bottomWrap = new LinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkCommentPosition();
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkCommentPosition();
      }
    };
    bottomWrap.setLayoutParams(params);
    bottomWrap.setId(R.id.share_bottom);
    bottomWrap.setOrientation(LinearLayout.VERTICAL);
    contentView.addView(bottomWrap);

    inputView = new InputView(context, tdlib) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkButtonsPosition();
      }
    };
    inputView.setInputListener(new InputView.InputListener() {
      @Override
      public void onInputChanged (InputView v, String input) {
      }

      @Override
      public boolean canSearchInline (InputView v) {
        return false;
      }

      @Override
      public TdApi.Chat provideInlineSearchChat (InputView v) {
        return null;
      }

      @Override
      public long provideInlineSearchChatId (InputView v) {
        return 0;
      }

      @Override
      public long provideInlineSearchChatUserId (InputView v) {
        return 0;
      }

      @Override
      public void showInlineResults (InputView v, ArrayList<InlineResult<?>> items, boolean isContent) {

      }

      @Override
      public void addInlineResults (InputView v, ArrayList<InlineResult<?>> items) {

      }
    });
    inputView.setEnabled(false);
    inputView.setGravity(Gravity.LEFT | Gravity.BOTTOM);
    inputView.setTypeface(Fonts.getRobotoRegular());
    inputView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(inputView);
    inputView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    inputView.setPadding(Screen.dp(60f), Screen.dp(12f), Screen.dp(55f), Screen.dp(12f));
    inputView.setHintTextColor(Theme.textPlaceholderColor());
    addThemeHintTextColorListener(inputView, R.id.theme_color_textPlaceholder);
    ViewSupport.setThemedBackground(inputView, R.id.theme_color_filling, this);
    inputView.setHighlightColor(Theme.fillingTextSelectionColor());
    addThemeHighlightColorListener(inputView, R.id.theme_color_textSelectionHighlight);
    inputView.setMinimumHeight(Screen.dp(48f));
    inputView.setHint(Lang.getString(R.string.AddComment));
    inputView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    inputView.setInputType(inputView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    inputView.setSingleLine(false);
    inputView.setMaxLines(4);
    bottomWrap.addView(inputView);

    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      params.leftMargin = inputView.getPaddingLeft();
      params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
      stubInputView = new View(context);
      stubInputView.setId(R.id.share_comment_stub);
      stubInputView.setOnClickListener(this);
      stubInputView.setLayoutParams(params);
      contentView.addView(stubInputView);
    }

    // Buttons

    params = new RelativeLayout.LayoutParams(Screen.dp(55f), Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ALIGN_TOP, R.id.share_bottom);

    okButton = new CustomImageView(context);
    okButton.setId(R.id.btn_done);
    okButton.setScaleType(ImageView.ScaleType.CENTER);
    okButton.setImageResource(R.drawable.deproko_baseline_send_24);
    okButton.setColorFilter(Theme.chatSendButtonColor());
    addThemeFilterListener(okButton, R.id.theme_color_chatSendButton);
    okButton.setVisibility(View.INVISIBLE);
    okButton.setOnClickListener(this);
    okButton.setLayoutParams(params);
    contentView.addView(okButton);
    sendMenu.attachToView(okButton);

    params = new RelativeLayout.LayoutParams(Screen.dp(60f), Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    params.addRule(RelativeLayout.ALIGN_TOP, R.id.share_bottom);

    // Send

    contentView.addView(sendButton);

    // Shadow

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ABOVE, R.id.share_bottom);
    bottomShadow = SeparatorView.simpleSeparator(context, params, false);
    bottomShadow.setColorId(R.id.theme_color_shareSeparator);
    bottomShadow.setAlignBottom();
    bottomShadow.setAlpha(0f);
    bottomShadow.setLayoutParams(params);
    contentView.addView(bottomShadow);

    adapter = new SettingsAdapter(this) {

      @Override
      protected void setMessageSenderData (ListItem item, int position, MessageSenderView senderView) {
//        TGFoundChat chat = (TGFoundChat) item.getData();
//        senderView.setChat(chat);
        TGMessageSender messageSender = (TGMessageSender) item.getData();
        senderView.setPreviewActionListProvider(MessageSendersController.this);
        senderView.setMessageSender(messageSender);
        senderView.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick (View v) {

            tdlib.send(new TdApi.SetChatMessageSender(getArguments().currentChat.id, messageSender.getMessageSender()), tdlib().okHandler());

            if (popupLayout != null)
              popupLayout.hideWindow(true);
          }
        });
      }
    };
    adapter.setNoEmptyProgress();

    headerView = new HeaderView(context);
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    getSearchHeaderView(headerView);

    // Fix blinking black line between header & content
    FrameLayout.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f));
    fp.topMargin = HeaderView.getSize(false) - Screen.dp(3f);
    fixView = new View(context);
    ViewSupport.setThemedBackground(fixView, R.id.theme_color_filling, this);
    fixView.setLayoutParams(fp);

    wrapView = new FrameLayoutFix(context);
    wrapView.addView(fixView);
    wrapView.addView(contentView);
    chatSearchView = generateChatSearchView(wrapView);
    ((ViewGroup.MarginLayoutParams) chatSearchView.getLayoutParams()).topMargin = HeaderView.getSize(true);
    wrapView.addView(headerView);
    if (HeaderView.getTopOffset() > 0) {
      lickView = new LickView(context);
      addThemeInvalidateListener(lickView);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
      wrapView.addView(lickView);
    }

    checkCommentPosition();

    // Load chats

//    tdlib.client().send(new TdApi.CreatePrivateChat(tdlib.myUserId(), true), tdlib.silentHandler());
    // FIXME replace Math.max with proper fix.
//    int startLoadCount = Math.max(20, Screen.calculateLoadingItems(Screen.dp(95f), 1) * calculateSpanCount());
//    list.initializeList(this, this::processChats, startLoadCount, this::executeScheduledAnimation);

//    tdlib.searchContacts(null, ContactsController.DISPLAY_LIMIT, this);
    if (getArguments() != null) {
      tdlib.send(new TdApi.GetChatAvailableMessageSenders(getArguments().currentChat.id), new Client.ResultHandler() {
        @Override
        public void onResult (TdApi.Object object) {
          if (object.getConstructor() == TdApi.ChatMessageSenders.CONSTRUCTOR) {
            List<TGMessageSender> result = new ArrayList<>();
            TdApi.ChatMessageSenders chatMessageSenders = (TdApi.ChatMessageSenders) object;
            for (TdApi.ChatMessageSender sender : chatMessageSenders.senders) {
              TGMessageSender tgMessageSender = new TGMessageSender(tdlib, sender.sender, sender.needsPremium);
              TdApi.MessageSender messageSenderId = getArguments().currentChat.messageSenderId;
              if (messageSenderId != null && Td.getSenderId(messageSenderId) == tgMessageSender.getAnyId()) {
                tgMessageSender.setSelected(true);
              }
              result.add(tgMessageSender);
            }

            runOnUiThreadOptional(() ->
              displaySenders(result)
            );
          }
        }
      });
    }

    return wrapView;
  }

  @Override
  public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      if (displayingChats != null) {
        TGFoundChat parsedChat = newChat(chat);
        displayingChats.add(atIndex, parsedChat);
//        adapter.addItem(atIndex, valueOfChat(parsedChat));
        recyclerView.invalidateItemDecorations();
      }
    });
  }

  @Override
  public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      if (displayingChats != null) {
        displayingChats.remove(fromIndex);
//        adapter.removeItem(fromIndex);
        recyclerView.invalidateItemDecorations();
      }
    });
  }

  @Override
  public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      if (displayingChats != null) {
        TGFoundChat entry = displayingChats.remove(fromIndex);
        displayingChats.add(toIndex, entry);
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int savedPosition, savedOffset;
        if (manager != null) {
          savedPosition = manager.findFirstVisibleItemPosition();
          View view = manager.findViewByPosition(savedPosition);
          savedOffset = view != null ? manager.getDecoratedTop(view) : 0;
        } else {
          savedPosition = RecyclerView.NO_POSITION;
          savedOffset = 0;
        }

//        adapter.moveItem(fromIndex, toIndex);
        recyclerView.invalidateItemDecorations(); // TODO detect only first-non-first row changes
        if (savedPosition != RecyclerView.NO_POSITION) {
          manager.scrollToPositionWithOffset(savedPosition, savedOffset);
        }
      }
    });
  }

  private TGFoundChat newChat (TdApi.Chat rawChat) {
    TGFoundChat chat = new TGFoundChat(tdlib, chatList, rawChat, false, null);
    chat.setNoUnread();
    chat.setNoSubscription();
    return chat;
  }

  private void processChats (List<TdlibChatListSlice.Entry> entries) {
    final List<TGFoundChat> result = new ArrayList<>(entries.size());
    for (TdlibChatList.Entry entry : entries) {
      result.add(newChat(entry.chat));
    }
    runOnUiThreadOptional(() ->
      displayChats(result)
    );
  }



  @Override
  public ForceTouchView.ActionListener onCreateActions (final View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    final ListItem item = (ListItem) v.getTag();
    final TGFoundChat chat = (TGFoundChat) item.getData();

    context.setExcludeHeader(true);

    ids.append(R.id.btn_selectChat);
    strings.append(isChecked(chat.getAnyId()) ? R.string.Unselect : R.string.Select);
    icons.append(R.drawable.baseline_playlist_add_check_24);

    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        toggleChecked(v, chat, isChecked ->
          ((VerticalChatView) v).setIsChecked(isChecked, true)
        );
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

      }
    };
  }

  @Override
  protected boolean canSelectFoundChat (TGFoundChat chat) {
    return true;
  }

  private int detectTopRecyclerEdge () {
    LinearLayoutManager manager =  (LinearLayoutManager) recyclerView.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int spanCount = 1;//manager.getSpanCount();
    int top = 0;
    if (first != -1 && first < spanCount) {
      View topView = manager.findViewByPosition(first);
      if (topView != null) {
        final int topViewPos = topView.getTop() - Screen.dp(VERTICAL_PADDING_SIZE);
        if (topViewPos > 0) {
          int totalSpanCount = 0;
          for (int pos = 0; pos < first && totalSpanCount <= spanCount; pos++) {
            totalSpanCount += 1; //manager.getSpanSizeLookup().getSpanSize(pos);
          }
          if (totalSpanCount <= spanCount) {
            top = topViewPos;
          }
        }
      }
    }
    return top;
  }

  // Other

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hide(inputView);
  }

  private boolean needPostponeAutoScroll = false;

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    if (chat.getId() == 0) {
      chat = new TGFoundChat(tdlib, chat.getList(), chat.getAnyId(), false);
    }
    final long chatId = chat.getAnyId();

    if (!isChecked(chatId)) {
      if (processSingleTap(chat))
        return true;
      if (!toggleChecked(view, chat, null))
        return true;
    }

    int i = adapter.indexOfViewByLongId(chat.getAnyId());
//    int i = adapter.getSelectedUsers().indexOfKey(chat.getAnyId());
    if (i != -1) {
      View itemView = recyclerView.getLayoutManager().findViewByPosition(i);
      if (itemView != null) {
        ((VerticalChatView) itemView).setIsChecked(true, false);
      } else {
        adapter.notifyItemChanged(i);
      }
    }

    list.bringToTop(chat.getAnyId(), null, () -> runOnUiThreadOptional(() -> {
      needPostponeAutoScroll = true;
      closeSearchMode(null);
      needPostponeAutoScroll = false;
    }));

    return true;
  }

  @Override
  protected boolean canInteractWithFoundChat (TGFoundChat chat) {
    return false;
  }

  private static final float HORIZONTAL_PADDING_SIZE = 8f;
  private static final float VERTICAL_PADDING_SIZE = 4f;
  private boolean autoScrollFinished = true, openKeyboardUponFinishingAutoScroll;

  private View lastFirstView;

  private void checkHeaderPosition () {
    View view = lastFirstView;
    if (view == null || recyclerView.getChildAdapterPosition(view) != 0) {
      view = lastFirstView = recyclerView.getLayoutManager().findViewByPosition(0);
    }
    int top = HeaderView.getTopOffset();
    if (view != null) {
      top = Math.max(view.getTop() - HeaderView.getSize(false) + recyclerView.getTop() - Screen.dp(VERTICAL_PADDING_SIZE), HeaderView.getTopOffset());
    }
    if (!appliedRecyclerMargin && expandFactor != 0f) {
      top = Math.max(HeaderView.getTopOffset(), (int) (top + calculateRecyclerOffset()));
    }
    if (headerView != null) {
      headerView.setTranslationY(top);
      fixView.setTranslationY(top);
      final int topOffset = HeaderView.getTopOffset();
      top -= topOffset;
      checkInputEnabled(top);
      chatSearchView.setTranslationY(top);
      if (lickView != null) {
        lickView.setTranslationY(top);
        float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
        lickView.setFactor(factor);
        headerView.getFilling().setShadowAlpha(factor);
      }
      if (top == 0 && needUpdateSearchMode) {
        super.updateSearchMode(true);
        needUpdateSearchMode = false;
      } else if (autoScrollFinished && top > 0 && inSearchMode() && getSearchTransformFactor() == 1f) {
        recyclerView.scrollBy(0, top);
      }
    }
  }

  private boolean preventAutoScroll;

  private final LongSparseArray<TGFoundChat> selectedChats = new LongSparseArray<>();
  private final LongList selectedChatIds = new LongList(10);

  private boolean isChecked (long chatId) {
    return selectedChats.get(chatId) != null;
  }

  private boolean hasSelectedAnything;

  private boolean processSingleTap (TGFoundChat chat) {
    if (!hasSelectedAnything && chat.isSelfChat() && selectedChats.size() == 0) {
      selectedChats.put(chat.getAnyId(), chat);
      selectedChatIds.append(chat.getAnyId());
      return true;
    }
    return false;
  }

  private CharSequence getErrorMessage (long chatId) {
    Args args = getArgumentsStrict();
    TdApi.Chat chat = tdlib.chatStrict(chatId);
    return "";
  }

  @Nullable
  private View findVisibleChatView (long chatId) {
//    int i = adapter.indexOfViewByLongId(chatId);
//    if (i != -1) {
//      int firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
//      int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
//      if (i >= firstVisiblePosition && i <= lastVisiblePosition) {
//        return recyclerView.getLayoutManager().findViewByPosition(i);
//      }
//    }
    return null;
  }

  private boolean showErrorMessage (View anchorView, long chatId, boolean includeTitle) {
    CharSequence errorMessage = getErrorMessage(chatId);
    if (errorMessage != null) {
      if (anchorView == null) {
        anchorView = findVisibleChatView(chatId);
        includeTitle = includeTitle && anchorView == null;
      }
      if (anchorView == null && isExpanded) {
        anchorView = isSendHidden ? okButton : sendButton;
      }
      if (includeTitle) {
        errorMessage = Lang.getString(R.string.format_chatAndError, (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ? Lang.boldCreator().onCreateSpan(target, argStart, argEnd, argIndex, needFakeBold) : null, tdlib.chatTitle(chatId), errorMessage);
      }
      if (anchorView != null) {
        context().tooltipManager().builder(anchorView).icon(R.drawable.baseline_warning_24).show(tdlib, errorMessage).hideDelayed();
      } else {
        UI.showToast(errorMessage, Toast.LENGTH_SHORT);
      }
      return true;
    }
    return false;
  }

  private boolean toggleChecked (View view, TGFoundChat chat, RunnableBool after)  {
    return toggleCheckedImpl(view, chat, after, true);
  }

  private final LongSet lockedChatIds = new LongSet();

  private boolean toggleCheckedImpl (View view, TGFoundChat chat, @Nullable RunnableBool after, boolean performAsyncChecks)  {
    long chatId = chat.getAnyId();
    if (lockedChatIds.has(chatId)) {
      return false;
    }

    boolean result = !isChecked(chatId);

    if (result) {
      if (performAsyncChecks && ChatId.isUserChat(chatId)) {
        lockedChatIds.add(chatId);
        tdlib.cache().userFull(tdlib.chatUserId(chatId), userFullInfo -> {
          lockedChatIds.remove(chatId);
          // FIXME: view recycling safety
          // By the time `after` is called, initial view could have been already recycled.
          // Current implementation relies on the quick response from GetUserFull,
          // however, there's a chance `view` could have been already taken by some other view.
          // Should be fixed inside `after` contents.
          toggleCheckedImpl(view, chat, after, false);
        });
        return false;
      }
      if (showErrorMessage(view, chatId, false)) {
        result = false;
      }
    }

    if (result) {
      selectedChats.put(chatId, chat);
      selectedChatIds.append(chatId);
      hasSelectedAnything = true;
    } else {
      selectedChats.remove(chatId);
      selectedChatIds.remove(chatId);
    }
    updateHeader();
    if (after != null) {
      after.runWithBool(result);
    }
    return result;
  }

  private void updateHeader () {
    if (selectedChats.size() == 0) {
//      headerCell.setSubtitle(Lang.lowercase(Lang.getString(R.string.SelectChats)));
    } else if (selectedChats.size() == 1) {
//      headerCell.setSubtitle(selectedChats.valueAt(0).getFullTitle());
    } else {
      final int size = selectedChatIds.size();
      int count = 0;
      final int limit = getMenuItemCount() > 1 ? 2 : 3;
      List<String> names = new ArrayList<>();
      int others = 0;
      for (int i = 0; i < size; i++) {
        long chatId = selectedChatIds.get(i);
        TGFoundChat chat = selectedChats.get(chatId);
        if (count == limit - 1 && size > limit) {
          others = size - count;
          break;
        }
        if (chat.isSelfChat()) {
          names.add(Lang.getString(R.string.SavedMessages));
        } else {
          names.add(chat.getSingleLineTitle().toString());
        }
        count++;
      }
//      headerCell.setSubtitle(Lang.pluralChatTitles(names, others));
    }
  }

  private static final boolean OPEN_KEYBOARD_WITH_AUTOSCROLL = false;
  private boolean sendOnKeyboardClose;
  private TdApi.MessageSendOptions sendOnKeyboardCloseSendOptions;
  private boolean sendOnKeyboardCloseGoToChat;

  private void performSend (boolean needHideKeyboard, TdApi.MessageSendOptions finalSendOptions, boolean forceGoToChat) {
    if (needHideKeyboard) {
      if (!sendOnKeyboardClose) {
        sendOnKeyboardClose = true;
        sendOnKeyboardCloseSendOptions = finalSendOptions;
        sendOnKeyboardCloseGoToChat = forceGoToChat;
        hideSoftwareKeyboard();
      }
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_send:{
        if (selectedChats.size() == 0) {
        } else {
          performSend(false, Td.newSendOptions(), false);
        }
        break;
      }
      case R.id.btn_done: {
        if (selectedChats.size() == 0) {
          hideSoftwareKeyboard();
        } else {
          performSend(true, Td.newSendOptions(), false);
        }
        break;
      }
      case R.id.share_comment_stub: {
        if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
          final int top = getTopEdge();
          openKeyboardUponFinishingAutoScroll = true;
          smoothScrollBy(top);
        }
        break;
      }
      default: {
        ListItem item = (ListItem) v.getTag();
        TGFoundChat chat = (TGFoundChat) item.getData();
        switch (item.getId()) {
          case R.id.chat: {
            if (autoScrollFinished) {
              if (!processSingleTap(chat)) {
                toggleChecked(v, chat, isChecked ->
                  ((VerticalChatView) v).setIsChecked(isChecked, true)
                );
              }
            }
            break;
          }
          case R.id.search_chat: {
            if (!processSingleTap(chat)) {
              toggleChecked(v, chat, isChecked ->
                ((VerticalChatView) v).setIsChecked(isChecked, true)
              );
              closeSearchMode(null);
            }
            break;
          }
        }
        break;
      }
    }
  }

  private void setSmoothScrollFactor (float factor) {
    if (!autoScrollFinished && currentScrollBy != 0) {
      int scrollBy = (int) ((float) currentScrollBy * factor);
      int by = scrollBy - lastScrollBy;
      recyclerView.scrollBy(0, by);
      lastScrollBy = scrollBy;
    }
  }

  private int currentScrollBy, lastScrollBy;

  private int totalScrollingBy;
  private boolean autoScrolling;

  private static final boolean PREVENT_HEADER_ANIMATOR = false; // TODO

  @Override
  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return PREVENT_HEADER_ANIMATOR && open && getTopEdge() > 0;
  }

  private void smoothScrollBy (int y) {
    if (y == 0) {
      recyclerView.stopScroll();
    }
    setAutoScrollFinished(false);
    /*FIXME replace with
    currentScrollBy = y;
    lastScrollBy = 0;*/
    if (PREVENT_HEADER_ANIMATOR) {
      totalScrollingBy = y;
      autoScrolling = true;
    }
    recyclerView.smoothScrollBy(0, y);
  }

  @Override
  protected Interpolator getSearchTransformInterpolator () {
    return AnimatorUtils.DECELERATE_INTERPOLATOR;
  }

  @Override
  protected long getSearchTransformDuration () {
    return 220l;
  }

  private int calculateSpanCount () {
    if (UI.isLandscape()) {
      int itemWidth = Screen.smallestSide() / 4;
      return Screen.currentWidth() / itemWidth;
    } else {
      return 4;
    }
  }

  @Override
  public void run () {
//    int spanCount = calculateSpanCount();
//    GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
//    if (manager.getSpanCount() != spanCount) {
//      manager.setSpanCount(spanCount);
//      decoration.setSpanCount(spanCount);
//      recyclerView.invalidateItemDecorations();
//    }
  }

  private int getTopEdge () {
    return Math.max(0, (int) (headerView.getTranslationY() - HeaderView.getTopOffset()));
  }

  private int awaitingChatSearchOpen;

  @Override
  protected void onChatSearchOpenStarted () {
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
    final int top = getTopEdge();
    if (top > 0) {
      awaitingChatSearchOpen = top;
    } else {
      setAutoScrollFinished(true);
    }
  }

  private void setAutoScrollFinished (boolean isFinished) {
    if (this.autoScrollFinished != isFinished) {
      this.autoScrollFinished = isFinished;
      recyclerView.setScrollDisabled(!isFinished);
      if (isFinished) {
        if (scheduledScrollLock) {
          processScrollLock();
        }
        if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
          if (openKeyboardUponFinishingAutoScroll) {
            Keyboard.show(inputView);
            openKeyboardUponFinishingAutoScroll = false;
          }
        }
      }
    }
  }

  private boolean awaitLayout;
  private int awaitScrollBy;

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    if (preventAutoScroll) {
      preventAutoScroll = false;
      setAutoScrollFinished(true);
      return;
    }
    final int top = getTopEdge();
    final int contentOffset = getContentOffset() - Screen.dp(VERTICAL_PADDING_SIZE);
    if (top == 0) {
      //GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      int i = manager.findFirstVisibleItemPosition();
      View view = i == 0 ? manager.findViewByPosition(0) : null;
      if (i != 0 || needPostponeAutoScroll) {
        awaitLayout = true;
        awaitScrollBy = -contentOffset;
        manager.scrollToPositionWithOffset(0, Screen.dp(VERTICAL_PADDING_SIZE));
        return;
      } else if (view != null && view.getTop() < Screen.dp(VERTICAL_PADDING_SIZE)) {
        int viewTop = view.getTop();
        recyclerView.scrollBy(0, viewTop - Screen.dp(VERTICAL_PADDING_SIZE));
      }
    }
    autoScroll(top - contentOffset);
  }

  private void autoScroll (int by) {
    if (awaitLayout) {
      awaitScrollBy = by;
    } else {
      smoothScrollBy(by);
    }
  }

  private boolean needUpdateSearchMode;

  @Override
  protected void updateSearchMode (boolean inSearch) {
    if (!inSearch || getTopEdge() == 0) {
      super.updateSearchMode(inSearch);
      needUpdateSearchMode = false;
    } else {
      needUpdateSearchMode = true;
    }
  }

  @Override
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    super.applySearchTransformFactor(factor, isOpening);
    setSmoothScrollFactor(isOpening ? factor : 1f - factor);
    setScrollLocked(factor == 1f);
    popupLayout.setIgnoreBottom(factor != 0f);
  }

  private boolean isScrollLocked;

  private boolean scheduledScrollLock;
  private void processScrollLock () {
    scheduledScrollLock = false;
    recyclerView.stopScroll();
    if (getTopEdge() == 0) {
      //GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      int i = manager.findFirstVisibleItemPosition();
      View view = i == 0 ? manager.findViewByPosition(0) : null;
      preventAutoScroll = i > 0 || (view != null && view.getTop() < Screen.dp(VERTICAL_PADDING_SIZE));
    } else {
      preventAutoScroll = false;
    }
  }

  private void setScrollLocked (boolean isLocked) {
    if (this.isScrollLocked != isLocked) {
      this.isScrollLocked = isLocked;
      if (isScrollLocked) {
        if (autoScrollFinished) {
          processScrollLock();
        } else {
          scheduledScrollLock = true;
        }
      } else {
        scheduledScrollLock = false;
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_EXPAND: {
        setExpandFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_EXPAND: {
        onExpansionFinished(finalFactor);
        break;
      }
    }
  }

  // PopupLayout


  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerView.getTranslationY() - HeaderView.getSize(true);
  }

  private PopupLayout popupLayout;

  public void show () {
    if (tdlib == null) {
      return;
    }
    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
        if (!isDestroyed()) {
          recyclerView.invalidateItemDecorations();
        }
      }
    };
    popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    // popupLayout.set®(View.LAYER_TYPE_HARDWARE, Views.LAYER_PAINT);
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(false);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    popupLayout.setIgnoreHorizontal();
    get();
    context().addFullScreenView(this, false);
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  private boolean openLaunched;

  private void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(get(), calculateTotalHeight());
    }
  }

  private int getTargetHeight () {
    return Screen.currentHeight(); // UI.getContext(getContext()).getNavigation().getWrap().getMeasuredHeight();
  }

  private int getContentOffset () {
    return getTargetHeight() / 2 - HeaderView.getSize(true) - (isExpanded ? calculateMovementDistance() : 0) + (Screen.dp(56f) / 2);
  }

  private int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset());
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - detectTopRecyclerEdge() - (int) ((float) HeaderView.getTopOffset() * (1f - (lickView != null ? lickView.factor : 0f))));
  }

  // Data loading

  @Override
  public void onResult (TdApi.Object object) {

  }

  private void displaySenders (List<TGMessageSender> senders) {
    boolean areFirst = displayingSenders == null;
    if (areFirst) {
      displayingSenders = senders;
    } else {
      ArrayUtils.ensureCapacity(displayingSenders, displayingSenders.size() + senders.size());
      displayingSenders.addAll(senders);
    }

    // if (!inSearchMode()) {
    final int startIndex = adapter.getItems().size();
    fillCells(senders, adapter.getItems());
    adapter.notifyItemRangeInserted(startIndex, adapter.getItems().size() - startIndex);
    // }

    if (areFirst) {
      recyclerView.setAdapter(adapter);
      launchOpenAnimation();
    } else {
      recyclerView.invalidateItemDecorations();
    }


//    if (users.isEmpty()) {
////      showError(this.users == null ? R.string.NoContacts : R.string.NothingFound, this.users == null);
//      adapter.setUsers(null);
//    } else if (this.users == null) {
////      hideProgress();
////      hideError();
//      this.users = users;
//      adapter.setUsers(users);
////      expandStartHeight(adapter);
//    } else {
//      adapter.setUsers(users);
////      hideError();
//    }
//    adapter.setUsers(users);
    launchOpenAnimation();
  }


  protected final void dispatchError (final String error, final boolean animated) {
    runOnUiThread(() -> {
      UI.showToast(error, Toast.LENGTH_SHORT);
    });
  }

  private List<TGFoundChat> displayingChats;
  private List<TGMessageSender> displayingSenders;

  private void displayChats (List<TGFoundChat> chats) {
    boolean areFirst = displayingChats == null;
    if (areFirst) {
      displayingChats = chats;
    } else {
      ArrayUtils.ensureCapacity(displayingChats, displayingChats.size() + chats.size());
      displayingChats.addAll(chats);
    }

    // if (!inSearchMode()) {
      final int startIndex = adapter.getItems().size();
      addCells(chats, adapter.getItems());
      adapter.notifyItemRangeInserted(startIndex, adapter.getItems().size() - startIndex);
    // }

    if (areFirst) {
      recyclerView.setAdapter(adapter);
      launchOpenAnimation();
    } else {
      recyclerView.invalidateItemDecorations();
    }
  }

  private void addCells (List<TGFoundChat> entries, List<ListItem> out) {
    if (entries.isEmpty()) {
      return;
    }
    ArrayUtils.ensureCapacity(out, out.size() + entries.size());
    for (TGFoundChat entry : entries) {
      out.add(valueOfChat(entry));
    }
  }

  private void fillCells (List<TGMessageSender> entries, List<ListItem> out) {
    if (entries.isEmpty()) {
      return;
    }
    ArrayUtils.ensureCapacity(out, out.size() + entries.size());
    for (TGMessageSender entry : entries) {
      out.add(valueOfSender(entry));
    }
  }

  private static ListItem valueOfChat (TGFoundChat chat) {
    return new ListItem(ListItem.TYPE_MESSAGE_SENDER, R.id.chat).setData(chat).setLongId(chat.getAnyId());
  }

  private static ListItem valueOfSender (TGMessageSender sender) {
    return new ListItem(ListItem.TYPE_MESSAGE_SENDER, R.id.chat).setData(sender).setLongId(sender.getAnyId());
  }

  // Button

  private static class SendButton extends FrameLayoutFix implements FactorAnimator.Target {
    public SendButton (Context context) {
      super(context);
      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          doDraw(c);
        }

        @Override
        public void setAlpha (@IntRange(from = 0, to = 255) int alpha) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
      View view = new View(context);
      RippleSupport.setTransparentSelector(view);
      Views.setClickable(view);
      view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addView(view);
    }

    private boolean isReady;
    private FactorAnimator animator;

    public void setIsReady (boolean isReady, boolean animated) {
      if (this.isReady != isReady) {
        this.isReady = isReady;
        final float toFactor = isReady ? 1f : 0f;
        if (animated) {
          if (animator == null) {
            animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
          }
          animator.animateTo(toFactor);
        } else {
          if (animator != null) {
            animator.forceFactor(toFactor);
          }
          setFactor(toFactor);
        }
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

    }

    private float factor; // 0f -> copy link, 1f -> send

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    private String sendText;
    private boolean sendTextFake;
    private float sendWidth;

    private String copyText;
    private boolean copyTextFake;
    private float copyWidth;

    public void setShareText (@NonNull String text) {
      this.copyText = text.toUpperCase();
      this.copyTextFake = Text.needFakeBold(copyText);
      this.copyWidth = U.measureText(copyText, Paints.getTitleBigPaint(copyTextFake));
    }

    public void setSendText (@NonNull String text) {
      sendText = text.toUpperCase();
      sendTextFake = Text.needFakeBold(sendText);
      sendWidth = U.measureText(sendText, Paints.getTitleBigPaint(sendTextFake));
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      if (sendText == null) {
        setSendText(Lang.getString(R.string.Send));
      }
      if (copyText == null) {
        setShareText(Lang.getString(R.string.ShareBtnLink));
      }
    }

    private void doDraw (Canvas c) {
      final int width = getMeasuredWidth();
      final int height = getMeasuredHeight();
      final int color = factor == 0f ? Theme.fillingColor() : factor == 1f ? Theme.getColor(R.id.theme_color_fillingPositive) : ColorUtils.fromToArgb(Theme.fillingColor(), Theme.getColor(R.id.theme_color_fillingPositive), factor);
      c.drawColor(color);
      if (factor != 0f && factor != 1f) {
        float radius = (float) Math.sqrt(width * width + height * height) * .5f;
        c.drawCircle(width / 2, height / 2, radius * factor, Paints.fillingPaint(ColorUtils.alphaColor(factor, Theme.getColor(R.id.theme_color_fillingPositive))));
      }
      final int textColor = factor == 0f ? Theme.getColor(R.id.theme_color_textNeutral) : factor == 1f ? Theme.getColor(R.id.theme_color_fillingPositiveContent) : ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_textNeutral), Theme.getColor(R.id.theme_color_fillingPositiveContent), factor);
      if (factor <= .5f) {
        TextPaint paint = Paints.getTitleBigPaint(copyTextFake);
        final int sourceTextColor = paint.getColor();
        paint.setColor(textColor);
        c.drawText(copyText, width / 2 - copyWidth / 2, height / 2 + Screen.dp(7f), paint);
        paint.setColor(sourceTextColor);
      }
      if (factor >= .5f) {
        TextPaint paint = Paints.getTitleBigPaint(sendTextFake);
        final int sourceTextColor = paint.getColor();
        paint.setColor(textColor);
        c.drawText(sendText, width / 2 - sendWidth / 2, height / 2 + Screen.dp(7f), paint);
        paint.setColor(sourceTextColor);
      }
    }
  }

  private boolean isExpanded;
  private FactorAnimator expandAnimator;
  private float expandFactor;
  private static final int ANIMATOR_EXPAND = 1;

  private void setExpandFactor (float factor) {
    if (this.expandFactor != factor) {
      this.expandFactor = factor;
      float y = calculateRecyclerOffset();
      recyclerView.setTranslationY(y);
      checkCommentPosition();
      checkHeaderPosition();
      bottomShadow.setAlpha(expandFactor >= .2f ? 1f : expandFactor / .2f);
      if (sendHint != null) {
        sendHint.reposition();
      }
    }
  }

  private void checkCommentPosition () {
    float y = (float) calculateMovementDistance() * (1f - expandFactor);
    bottomWrap.setTranslationY(y);
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      stubInputView.setTranslationY(y);
    }
    bottomShadow.setTranslationY(y);
    sendButton.setTranslationY(y);
    checkButtonsPosition();
  }

  private void checkButtonsPosition () {
    int add = Math.max(0, inputView.getMeasuredHeight() - Screen.dp(48f));
    float y = bottomWrap.getTranslationY() + add;
    okButton.setTranslationY(y);
  }

  private int calculateMovementDistance () {
    return Math.max(bottomWrap.getMeasuredHeight(), Screen.dp(48f)) + Screen.dp(56f);
  }

  private boolean appliedRecyclerMargin;

  private void setAppliedRecyclerMargin (boolean isApplied) {
    if (this.appliedRecyclerMargin != isApplied) {
      this.appliedRecyclerMargin = isApplied;
      recyclerView.setTranslationY(calculateRecyclerOffset());
      ((RelativeLayout.LayoutParams) recyclerView.getLayoutParams()).addRule(RelativeLayout.ABOVE, 0);
      recyclerView.setLayoutParams(recyclerView.getLayoutParams());
      recyclerView.invalidateItemDecorations();
      if (ignoreRecyclerMovement && savedPosition == 0) {
        if (isApplied) {
          recyclerView.scrollBy(0, calculateMovementDistance() * -1);
        } else {
          ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(savedPosition, savedOffset - getContentOffset());
        }
      }
      inputView.setEnabled(isApplied);
    }
  }

  private void checkInputEnabled (int top) {
    final boolean isEnabled = appliedRecyclerMargin /*&& top == 0*/;
    if (inputView.isEnabled() != isEnabled) {
      inputView.setEnabled(isEnabled);
    }
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      stubInputView.setEnabled(!isEnabled);
    }
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      if (isEnabled && openKeyboardUponFinishingAutoScroll) {
        openKeyboardUponFinishingAutoScroll = false;
        Keyboard.show(inputView);
      }
    }
  }

  private boolean topEnsured;
  private int topEnsuredBy;

  private void resetTopEnsuredState () {
    topEnsured = false;
  }

  private void ensureTopPosition () {
    int top = getTopEdge();
    if (top > 0) {
      topEnsured = true;
      topEnsuredBy = top;
      recyclerView.scrollBy(0, top);
    } else {
      topEnsured = false;
    }
  }

  private void restoreEnsuredTopPosition () {
    if (topEnsured) {
      recyclerView.scrollBy(0, -topEnsuredBy);
      topEnsured = false;
    }
  }

  private boolean isSendHidden;

  private float calculateRecyclerOffset () {
    return appliedRecyclerMargin || ignoreRecyclerMovement ? 0f : (float) calculateMovementDistance() * expandFactor * -1f;
  }

  private int savedPosition, savedOffset;

  // Returns whether animation should start after onLayout
  private boolean onPrepareToExpand (final float toFactor) {
    ignoreRecyclerMovement = getTopEdge() <= HeaderView.getTopOffset() * 2;
    if (ignoreRecyclerMovement) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      savedPosition = manager.findFirstVisibleItemPosition();
      /*if (savedPosition == 0) {
        ignoreRecyclerMovement = false;
      }*/
      View view = manager.findViewByPosition(savedPosition);
      savedOffset = view != null ? view.getTop() : 0;
    }
    if (appliedRecyclerMargin && toFactor < 1f) {
      setAppliedRecyclerMargin(false);
      return true;
    }
    return false;
  }

  private void onExpansionFinished (float finalFactor) {
    if (finalFactor == 1f) {
      setAppliedRecyclerMargin(true);
      setLockFocusView(inputView, false);
    } else {
      setLockFocusView(null);
    }
  }

  private boolean expansionAnimationScheduled;
  private float scheduledExpansionFactor;

  private void launchExpansionAnimation () {
    if (expansionAnimationScheduled) {
      expansionAnimationScheduled = false;
      expandAnimator.animateTo(scheduledExpansionFactor);
    }
  }

  private boolean ignoreRecyclerMovement;
  private TooltipOverlayView.TooltipInfo sendHint;

  @Override
  public void destroy () {
    super.destroy();
    list.unsubscribeFromUpdates(this);
    Views.destroyRecyclerView(recyclerView);
    context().removeFullScreenView(this, false);
  }
}
