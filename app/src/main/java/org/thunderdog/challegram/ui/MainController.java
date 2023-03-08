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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.filegen.VideoGenerationInfo;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.OverlayButtonWrap;
import org.thunderdog.challegram.navigation.RecyclerViewProvider;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.sync.SyncAdapter;
import org.thunderdog.challegram.telegram.ChatFilter;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.BubbleLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SnackBar;
import org.thunderdog.challegram.widget.ViewPager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.TdConstants;

public class MainController extends ViewPagerController<Void> implements Menu, MoreDelegate, OverlayButtonWrap.Callback, TdlibOptionListener, AppUpdater.Listener, CounterChangeListener {
  public MainController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_main;
  }

  private FrameLayoutFix mainWrap;
  private OverlayButtonWrap composeWrap;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    if (BuildConfig.DEBUG) {
      Test.execute();
    }

    mainWrap = new FrameLayoutFix(context);
    mainWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    mainWrap.addView(pager);
    generateChatSearchView(mainWrap);

    contentView.addView(mainWrap);

    UI.setSoftInputMode(UI.getContext(context), Config.DEFAULT_WINDOW_PARAMS);

    composeWrap = new OverlayButtonWrap(context);
    composeWrap.initWithList(this,
      R.id.theme_color_circleButtonActive,
      R.id.theme_color_circleButtonActiveIcon,
      new int[]{
        R.id.btn_float_compose,
        R.id.btn_float_newSecretChat,
        R.id.btn_float_newChannel,
        R.id.btn_float_newGroup,
        R.id.btn_float_newChat
      }, new int[]{
        R.drawable.baseline_create_24,
        R.drawable.baseline_lock_24,
        R.drawable.baseline_bullhorn_24,
        R.drawable.baseline_group_24,
        R.drawable.baseline_person_24
      }, new int[]{
        R.id.theme_color_circleButtonRegular,
        R.id.theme_color_circleButtonNewSecret,
        R.id.theme_color_circleButtonNewChannel,
        R.id.theme_color_circleButtonNewGroup,
        R.id.theme_color_circleButtonNewChat
      }, new int[] {
        R.id.theme_color_circleButtonRegularIcon,
        R.id.theme_color_circleButtonNewSecretIcon,
        R.id.theme_color_circleButtonNewChannelIcon,
        R.id.theme_color_circleButtonNewGroupIcon,
        R.id.theme_color_circleButtonNewChatIcon
      }, new int[]{
        R.string.NewSecretChat,
        R.string.NewChannel,
        R.string.NewGroup,
        R.string.NewChat
      }, false);
    composeWrap.setCallback(this);
    contentView.addView(composeWrap);

    makeStartupChecks();

    if (shareIntent != null) {
      shareIntentImpl();
    }

    tdlib.wallpaper().ensureWallpaperAvailability();
    tdlib.listeners().addOptionsListener(this);

    prepareControllerForPosition(0, this::executeScheduledAnimation);

    if (headerCell != null) {
      headerCell.getTopView().setOnSlideOffListener(new ViewPagerTopView.OnSlideOffListener() {
        @Override
        public boolean onSlideOffPrepare (View view, MotionEvent event, int index) {
          return index == 0 && unlockTabs();
        }

        @Override
        public void onSlideOffStart (View view, MotionEvent event, int index) {
          setMenuVisible(view, true);
        }

        @Override
        public void onSlideOffFinish (View view, MotionEvent event, int index, boolean apply) {
          if (apply && selectedSection != -1) {
            requestChatsSection(selectedSection);
            setSelectedSection(-1, event, view.getMeasuredHeight(),true);
          } else {
            setSelectedSection(-1, event, view.getMeasuredHeight(),false);
          }
          setMenuVisible(view, false);
        }

        @Override
        public void onSlideOffMovement (View view, MotionEvent event, int index) {
          float x = event.getX();
          float y = event.getY();
          x += (menu.getMeasuredWidth() - view.getMeasuredWidth()) / 2;
          y -= view.getMeasuredHeight() + mainWrap.getTranslationY();
          int selectedSection = -1;
          if (x >= 0 && x < menu.getMeasuredWidth()) {
            int firstItemEnd = menu.getTop() + menu.getChildAt(0).getBottom();
            if (y <= firstItemEnd) {
              selectedSection = 0;
            } else if (y < menu.getHeight()) {
              for (int section = 1; section < menu.getChildCount(); section++) {
                View child = menu.getChildAt(section);
                if (y >= child.getTop() && y < child.getBottom() && child.getVisibility() != View.GONE) {
                  selectedSection = section;
                  break;
                }
              }
            }
          }
          setSelectedSection(selectedSection, event, view.getMeasuredHeight(), false);
        }
      });
    }

    // tdlib.awaitConnection(this::unlockTabs);

    context().appUpdater().addListener(this);
    if (context().appUpdater().state() == AppUpdater.State.READY_TO_INSTALL) {
      onAppUpdateAvailable(context().appUpdater().flowType() == AppUpdater.FlowType.TELEGRAM_CHANNEL, true);
    }
  }

  private boolean unlockTabs () {
    return true;
    /*if (this.menuSection != 0)
      return true;
    if (tdlib.account().isDebug())
      return true;
    if (needTabsForLevel(testerLevel))
      return true;
    Calendar c = Dates.getNowCalendar();
    int month = c.get(Calendar.MONTH);
    int day = c.get(Calendar.DAY_OF_MONTH);
    if (month == Calendar.APRIL) {
      if (day <= 14)
        return true;
    }
    checkTesterLevel();
    return needTabsForLevel(testerLevel);*/
  }

  private int testerLevel = Tdlib.TESTER_LEVEL_NONE;
  private long lastCheckTime;

  private boolean needTabsForLevel (int level) {
    return level == Tdlib.TESTER_LEVEL_READER || level >= Tdlib.TESTER_LEVEL_ADMIN;
  }

  private void checkTesterLevel () {
    long now = SystemClock.uptimeMillis();
    if (lastCheckTime != 0 && now - lastCheckTime < TimeUnit.HOURS.toMillis(1))
      return;
    lastCheckTime = now;
    tdlib.getTesterLevel(level -> this.testerLevel = level);
  }

  // Menu

  private int selectedSection = -1;
  private long downTime;

  private void setSelectedSection (int section, MotionEvent event, float yOffset, boolean apply) {
    if (this.selectedSection != section) {
      if (this.selectedSection != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), apply ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, event.getX(), event.getY() - yOffset - view.getTop(), event.getMetaState()));
      }
      this.selectedSection = section;
      if (section != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime = SystemClock.uptimeMillis(), event.getEventTime(), MotionEvent.ACTION_DOWN, event.getX(), event.getY() - yOffset - view.getTop(), event.getMetaState()));
      }
    } else if (section != -1) {
      View view = menu.getChildAt(this.selectedSection);
      view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), MotionEvent.ACTION_MOVE, event.getX(), event.getY() - yOffset - view.getTop(), event.getMetaState()));
    }
  }

  private BubbleLayout menu;
  private int menuSection;
  private boolean menuNeedArchive;
  private View menuAnchor;

  private int pendingSection = -1;
  private ChatsController pendingChatsController;

  private void cancelPendingSection () {
    if (pendingChatsController != null) {
      pendingChatsController.destroy();
      pendingChatsController = null;
    }
    this.pendingSection = -1;
  }

  private void applySection (int section) {
    if (this.pendingSection != section)
      return;
    this.pendingSection = -1;
    ChatsController chatsController = this.pendingChatsController;
    this.pendingChatsController = null;

    ((TextView) menu.getChildAt(FILTER_ARCHIVE)).setTextColor(menuNeedArchive ? Theme.getColor(R.id.theme_color_textNeutral) : Theme.textDecentColor());

    TextView textView = (TextView) menu.getChildAt(menuSection);
    textView.setTextColor(Theme.textDecentColor());
    removeThemeListenerByTarget(textView);
    addThemeTextDecentColorListener(textView);

    this.menuSection = section;

    textView = (TextView) menu.getChildAt(menuSection);
    textView.setTextColor(menuSection != FILTER_NONE || !menuNeedArchive ? Theme.getColor(R.id.theme_color_textNeutral) : Theme.textDecentColor());
    removeThemeListenerByTarget(textView);
    addThemeTextColorListener(textView, R.id.theme_color_textNeutral);

    headerCell.getTopView().setItemAt(POSITION_CHATS, Lang.getString(getMenuSectionName()).toUpperCase());

    replaceController(POSITION_CHATS, chatsController);
    if (getCurrentPagerItemPosition() == POSITION_CHATS) {
      showComposeWrap(null);
    }
  }

  private void setNeedArchive (boolean needArchive) {
    if (this.menuNeedArchive != needArchive) {
      this.menuNeedArchive = needArchive;
      // ((TextView) menu.getChildAt(FILTER_ARCHIVE)).setCompoundDrawables(needArchive ? Drawables.get(R.drawable.baseline_check_24) : null, null, null, null);
    }
  }

  private void requestChatsSection (int requestedSection) {
    if ((this.menuSection == requestedSection && (requestedSection != FILTER_NONE || !menuNeedArchive)) || (menuNeedArchive && this.menuSection == FILTER_NONE && requestedSection == FILTER_ARCHIVE)) {
      cancelPendingSection();
      setCurrentPagerPosition(POSITION_CHATS, true);
      return;
    }
    if (this.pendingSection == requestedSection) {
      return; // Still waiting to switch, do nothing
    }
    cancelPendingSection();

    int section;
    if (requestedSection == FILTER_NONE) {
      section = FILTER_NONE;
      setNeedArchive(false);
    } else if (requestedSection == FILTER_ARCHIVE) {
      if (this.menuSection != FILTER_NONE) {
        section = FILTER_NONE;
        setNeedArchive(true);
      } else {
        setNeedArchive(!menuNeedArchive);
        section = this.menuSection;
      }
    } else {
      section = requestedSection;
    }

    ChatsController c = newChatsController(section, menuNeedArchive);
    this.pendingChatsController = c;
    this.pendingSection = section;
    c.postOnAnimationExecute(() -> {
      if (this.pendingSection == section && this.pendingChatsController == c) {
        applySection(section);
        setCurrentPagerPosition(POSITION_CHATS, true);
      }
    });
    modifyNewPagerItemController(c, POSITION_CHATS);
  }

  private void layoutMenu (@NonNull View view) {
    if (menu == null)
      return;
    menuAnchor = view;
    int left = view.getLeft();
    View currentView = view;
    do {
      currentView = (View) currentView.getParent();
      if (currentView == null)
        break;
      left += currentView.getLeft();
    } while (currentView != headerCell);
    menu.setTranslationX(Math.max(-Screen.dp(14f), left - menu.getMeasuredWidth()  / 2 + view.getMeasuredWidth() / 2));
  }

  private void setMenuVisible (View anchorView, boolean visible) {
    if (!visible && menu == null)
      return;
    View parent = null;
    if (menu == null) {
      menu = new BubbleLayout(context, this, true) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (menuAnchor != null) {
            layoutMenu(menuAnchor);
          }
        }
      };
      menu.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      int[] sections = new int[] {
        R.string.CategoryMain,
        R.string.CategoryArchive,
        R.string.CategoryPrivate,
        R.string.CategoryGroup,
        R.string.CategoryChannels,
        R.string.CategoryBots,
        R.string.CategoryUnread
      };

      menu.setPadding(menu.getPaddingLeft(), menu.getPaddingTop() + Screen.dp(14f), menu.getPaddingRight(), menu.getPaddingBottom() + Screen.dp(13f));
      menu.setMinimumWidth(Screen.dp(112f));
      int index = 0;
      for (int section : sections) {
        TextView sectionView = new NoScrollTextView(context);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;

        sectionView.setId(R.id.btn_send);
        sectionView.setLayoutParams(params);
        sectionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        int textColorId = menuSection == index ? R.id.theme_color_textNeutral : R.id.theme_color_textLight;
        sectionView.setTextColor(Theme.getColor(textColorId));
        sectionView.setGravity(Gravity.CENTER);
        sectionView.setPadding(Screen.dp(18f), Screen.dp(13f), Screen.dp(18f), Screen.dp(14f));
        addThemeTextColorListener(sectionView, textColorId);
        sectionView.setTypeface(Fonts.getRobotoMedium());
        sectionView.setTag(index);
        sectionView.setOnClickListener(v -> { });
        Views.setMediumText(sectionView, Lang.getString(bindLocaleChanger(section, sectionView, false, true)));
        RippleSupport.setTransparentBlackSelector(sectionView);
        menu.addView(sectionView);
        index++;
      }
      mainWrap.addView(menu, 1);
      parent = mainWrap;
      tdlib.listeners().addTotalChatCounterListener(this);
    }
    if (visible) {
      menu.getChildAt(FILTER_ARCHIVE).setVisibility(tdlib.hasArchivedChats() ? View.VISIBLE : View.GONE);

      boolean needUnread = tdlib.getMainCounter().chatCount > 0 || menuSection == FILTER_UNREAD;
      menu.getChildAt(FILTER_UNREAD).setVisibility(needUnread ? View.VISIBLE : View.GONE);
      layoutMenu(anchorView);
    }
    menu.setBubbleVisible(visible, parent);
  }

  // Language

  @Override
  public void onSuggestedLanguagePackChanged (String languagePackId, TdApi.LanguagePackInfo languagePackInfo) {
    if (isFocused()) {
      showSuggestions();
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (composeWrap != null)
      composeWrap.updateRtl();
    checkHeaderMargins();
  }

  public void setComposeAlpha (float alpha) {
    if (composeWrap != null) {
      composeWrap.close();
      composeWrap.setAlpha(alpha);
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    UI.startNotificationService();
    checkSyncAlert();
  }

  private void makeStartupChecks () {
    tdlib.context().checkDeviceToken();
    tdlib.contacts().startSyncIfNeeded(context(), false, null);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    ViewController<?> c = getCachedControllerForPosition(0);
    return c == null || c.needAsynchronousAnimation();
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (composeWrap != null) {
      composeWrap.close();
    }
  }

  @Override
  protected boolean useCenteredTitle () {
    return true;
  }

  // Search

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    composeWrap.forceHide();
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    composeWrap.showIfWasHidden();
  }

  /*@Override
  protected void onEnterSelectMode () {
    super.onEnterSelectMode();
    composeWrap.forceHide();
  }

  @Override
  protected void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    composeWrap.showIfWasHidden();
  }*/

  @Override
  protected View getSearchAntagonistView () {
    return getViewPager();
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_NEED_MESSAGES | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }

  @Override
  protected TdApi.ChatList getChatMessagesSearchChatList () {
    if (menuNeedArchive) {
      return ChatPosition.CHAT_LIST_ARCHIVE;
    }
    return null;
  }

  @Nullable
  public final ChatsController findChatsController () {
    return (ChatsController) getCachedControllerForPosition(0);
  }

  @Override
  protected boolean filterChatMessageSearchResult (TdApi.Chat chat) {
    ChatsController c = findChatsController();
    ChatFilter filter = c != null ? c.getFilter() : null;
    return filter != null && filter.canFilterMessages() ? filter.accept(chat) : super.filterChatMessageSearchResult(chat);
  }

  @Override
  protected int getChatMessagesSearchTitle () {
    ChatsController c = findChatsController();
    ChatFilter filter = c != null ? c.getFilter() : null;
    boolean isArchive = c != null && c.chatList().getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR;
    if (filter != null && filter.canFilterMessages())
      return filter.getMessagesStringRes(isArchive);
    if (isArchive)
      return R.string.MessagesArchive;
    return super.getChatMessagesSearchTitle();
  }

  // Main

  @Override
  protected boolean overridePagerParent () {
    return true;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return mainWrap;
  }

  // Overlay wrap

  @Override
  public boolean isIntercepted () {
    return composeWrap != null && composeWrap.isShowing();
  }

  @Override
  public void dismissIntercept () {
    if (composeWrap != null) {
      composeWrap.close();
    }
  }

  @Override
  public boolean onOverlayButtonClick (int id, View view) {
    // TODO check if viewPager is scrolling
    switch (id) {
      case R.id.user: {
        TdApi.User user = ((TdApi.User) view.getTag());
        if (user != null) {
          tdlib.ui().openPrivateChat(this, user.id, null);
        }
        break;
      }
      case R.id.btn_float_addContact: {
        navigateTo(new PhoneController(context, tdlib).setMode(PhoneController.MODE_ADD_CONTACT));
        break;
      }
      case R.id.btn_float_call: {
        ContactsController c = new ContactsController(context, tdlib);
        c.initWithMode(ContactsController.MODE_CALL);
        navigateTo(c);
        break;
      }
      case R.id.btn_float_compose: {
        if (navigationController != null && headerView != null && !navigationController.isAnimating() && !headerView.isAnimating() && composeWrap != null) {
          composeWrap.toggle();
        }
        return false;
      }
      case R.id.btn_float_newChannel: {
        navigateTo(new CreateChannelController(context, tdlib));
        break;
      }
      case R.id.btn_float_newGroup: {
        ContactsController c = new ContactsController(context, tdlib);
        c.initWithMode(ContactsController.MODE_NEW_GROUP);
        navigateTo(c);
        break;
      }
      case R.id.btn_float_newSecretChat: {
        ContactsController c = new ContactsController(context, tdlib);
        c.initWithMode(ContactsController.MODE_NEW_SECRET_CHAT);
        navigateTo(c);
        break;
      }
      case R.id.btn_float_newChat: {
        ContactsController c = new ContactsController(context, tdlib);
        c.initWithMode(ContactsController.MODE_NEW_CHAT);
        navigateTo(c);
        break;
      }
    }
    return true;
  }

  @Override
  public void onPageSelected (int position, int actualPosition) {
    if (!inSearchMode()) {
      composeWrap.show();
    }
    switch (position) {
      case POSITION_CHATS: {
        composeWrap.replaceMainButton(R.id.btn_float_compose, R.drawable.baseline_create_24);
        break;
      }
      case POSITION_CALLS: {
        composeWrap.replaceMainButton(R.id.btn_float_call, R.drawable.baseline_phone_24);
        break;
      }
      case POSITION_PEOPLE: {
        composeWrap.replaceMainButton(R.id.btn_float_addContact, R.drawable.baseline_person_add_24);
        break;
      }
    }
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (headerView != null) {
      headerView.updateLockButton(getMenuId());
    }
    checkHeaderMargins();
  }

  private void checkHeaderMargins () {
    if (headerCell != null) {
      RecyclerView recyclerView = ((ViewPagerHeaderViewCompact) headerCell).getRecyclerView();
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams();
      int menuWidth = getMenuButtonsWidth();
      int marginLeft, marginRight;
      if (Lang.rtl()) {
        marginLeft = menuWidth;
        marginRight = Screen.dp(56f);
      } else {
        marginLeft = Screen.dp(56f);
        marginRight = menuWidth;
      }
      if (params.rightMargin != marginRight || params.leftMargin != marginLeft) {
        params.leftMargin = marginLeft;
        params.rightMargin = marginRight;
        recyclerView.setLayoutParams(params);
      }
    }
  }

  private SnackBar updateSnackBar;

  private void onAppUpdateAvailable (boolean isApk, boolean immediate) {
    if (updateSnackBar == null) {
      updateSnackBar = new SnackBar(context);
      updateSnackBar.setCallback(new SnackBar.Callback() {
        @Override
        public void onSnackBarTransition (SnackBar v, float factor) {
          composeWrap.setTranslationY(-v.getMeasuredHeight() * factor);
        }

        @Override
        public void onDestroySnackBar (SnackBar v) {
          if (updateSnackBar == v) {
            mainWrap.removeView(updateSnackBar);
            updateSnackBar.removeThemeListeners(MainController.this);
            updateSnackBar = null;
          }
        }
      });
      updateSnackBar.addThemeListeners(this);
      updateSnackBar.setText(Lang.getString(R.string.AppUpdateReady));
      mainWrap.addView(updateSnackBar);
    }
    updateSnackBar.setAction(Lang.getString(isApk ? R.string.AppUpdateInstall : R.string.AppUpdateRestart), context().appUpdater()::installUpdate, !isApk);
    updateSnackBar.showSnackBar(!immediate && isFocused());
  }

  @Override
  public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) {
    if (state == AppUpdater.State.READY_TO_INSTALL) {
      onAppUpdateAvailable(isApk, false);
    } else if (updateSnackBar != null) {
      updateSnackBar.dismissSnackBar(isFocused());
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().removeOptionListener(this);
    context().appUpdater().removeListener(this);
    tdlib.listeners().removeTotalChatCounterListener(this);
  }

  private void showSuggestions () {
    if (showLanguageSuggestion()) {
      return;
    }
    if (showEmojiUpdateSuggestion()) {
      return;
    }
  }

  private TdApi.LanguagePackInfo loadingLanguagePack;

  private boolean showLanguageSuggestion () {
    String languagePackId = tdlib.suggestedLanguagePackId();
    TdApi.LanguagePackInfo languagePack = Settings.instance().suggestedLanguagePackInfo(languagePackId, tdlib);
    if (languagePack == null) {
      loadingLanguagePack = null;
      return false;
    }
    if (loadingLanguagePack != null && loadingLanguagePack.id.equals(languagePack.id))
      return true;
    loadingLanguagePack = languagePack;
    String[] keys = Lang.getKeys(new int[] {
      R.string.language_continueInLanguage,
      R.string.language_continueInLanguagePopupText,
      R.string.language_appliedLanguage
    });
    tdlib.getStrings(languagePack, keys, cloudStrings -> {
      boolean isBadLanguage = cloudStrings == null || cloudStrings.size() < keys.length;
      if (isBadLanguage) {
        Log.w("Suggested language is bad, ignoring. languagePackId:%s", languagePack.id);
        if (loadingLanguagePack != null && languagePack.id.equals(loadingLanguagePack.id))
          loadingLanguagePack = null;
        return;
      }
      tdlib.ui().post(() -> {
        if (!isDestroyed() && loadingLanguagePack != null && languagePack.id.equals(loadingLanguagePack.id)) {
          IntList ids = new IntList(3);
          StringList strings = new StringList(3);
          IntList colors = new IntList(3);
          IntList icons = new IntList(3);

          ids.append(R.id.btn_done);
          strings.append(TD.findOrdinary(cloudStrings, Lang.getResourceEntryName(R.string.language_continueInLanguage), () -> Lang.getString(R.string.language_continueInLanguage)));
          colors.append(OPTION_COLOR_BLUE);
          icons.append(R.drawable.baseline_check_24);

          ids.append(R.id.btn_cancel);
          icons.append(R.drawable.baseline_cancel_24);
          colors.append(OPTION_COLOR_NORMAL);
          strings.append(R.string.Cancel);

          ids.append(R.id.btn_languageSettings);
          strings.append(R.string.MoreLanguages);
          colors.append(OPTION_COLOR_NORMAL);
          icons.append(R.drawable.baseline_language_24);

          CharSequence text = Strings.buildMarkdown(this, TD.findOrdinary(cloudStrings, Lang.getResourceEntryName(R.string.language_continueInLanguagePopupText), () -> Lang.getString(R.string.language_continueInLanguagePopupText)), null);

          PopupLayout popupLayout = showOptions(text, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
            switch (id) {
              case R.id.btn_done: {
                tdlib.applyLanguage(languagePack, applySuccess -> {
                  if (applySuccess) {
                    UI.showToast(R.string.language_appliedLanguage, Toast.LENGTH_SHORT);
                    Settings.instance().setRecommendedLanguagePackId(languagePackId);
                  }
                }, true);
                break;
              }
              case R.id.btn_cancel: {
                Settings.instance().setRecommendedLanguagePackId(languagePackId);
                break;
              }
              case R.id.btn_languageSettings: {
                Settings.instance().setRecommendedLanguagePackId(languagePackId);
                navigateTo(new SettingsLanguageController(context, tdlib));
                break;
              }
            }
            return true;
          });
          if (popupLayout != null) {
            popupLayout.setDisableCancelOnTouchDown(true);
          }
          loadingLanguagePack = null;
        }
      });
    });
    return true;
  }

  private boolean showEmojiUpdateSuggestion () {
    Settings.EmojiPack emojiPack = Settings.instance().getOutdatedEmojiPack();
    if (emojiPack == null)
      return false;
    tdlib.ui().postDelayed(() -> {
      if (!isFocused() || isDestroyed() || context.isPasscodeShowing())
        return;
      showOptions(Lang.getStringBold(R.string.EmojiSetUpdated, emojiPack.displayName), new int[] {R.id.btn_downloadFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.EmojiSetUpdate), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_BLUE, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_sync_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        switch (id) {
          case R.id.btn_downloadFile: {
            SettingsCloudEmojiController c = new SettingsCloudEmojiController(context, tdlib);
            c.setArguments(new SettingsCloudController.Args<>(emojiPack));
            navigateTo(c);
            break;
          }
          case R.id.btn_cancel: {
            Settings.instance().revokeOutdatedEmojiPack();
            break;
          }
        }
        return true;
      });
    }, 100);
    return true;
  }

  @Override
  public void onChatCounterChanged(@NonNull TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    if (chatList instanceof TdApi.ChatListArchive && availabilityChanged) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && menu != null) {
          menu.getChildAt(FILTER_ARCHIVE).setVisibility(tdlib.hasArchivedChats() ? View.VISIBLE : View.GONE);
        }
      });
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    // FIXME check tdlib.isUnauthorized()
    tdlib.context().changePreferredAccountId(tdlib.id(), TdlibManager.SWITCH_REASON_NAVIGATION);
    if (UI.TEST_MODE == UI.TEST_MODE_USER) {
      UI.TEST_MODE = UI.TEST_MODE_NONE;
    }
    showSuggestions();
    checkSyncAlert();
    tdlib.checkDeadlocks();
    context().permissions().requestPostNotifications(granted -> {
      if (granted) {
        tdlib.notifications().onNotificationPermissionGranted();
      }
    });
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_MENU;
  }

  @Override
  protected boolean useDrawer () {
    return true;
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (!super.canSlideBackFrom(navigationController, x, y)) {
      return false;
    }
    if (headerCell != null) {
      if (y < HeaderView.getSize(true) && y >= HeaderView.getTopOffset() && x < ((View) headerCell).getMeasuredWidth()) {
        return !((ViewPagerHeaderViewCompact) headerCell).canScrollLeft();
      }
    }
    return true;
  }

  @Override
  protected int getMenuButtonsWidth () {
    int width = Screen.dp(56f);
    if (Passcode.instance().isEnabled()) {
      width += Screen.dp(28f);
    }
    return width;
  }

  public boolean showComposeWrap (ViewController<?> controller) {
    if (!inSearchMode() && (controller == null || getCurrentPagerItem() == controller)) {
      composeWrap.show();
      return true;
    }
    return false;
  }

  private boolean showHideCompose (int position, boolean show) {
    if (getCurrentPagerItemPosition() == position) {
      if (show) {
        return showComposeWrap(null);
      } else {
        if (getCurrentPagerItemPosition() == POSITION_CHATS) {
          ChatsController c = findChatsController();
          if (c != null && c.isPullingArchive()) {
            return false;
          }
        }
        composeWrap.hide();
        return true;
      }
    }
    return false;
  }

  // Controllers

  private static final int POSITION_CHATS = 0;
  private static final int POSITION_CALLS = 1;
  private static final int POSITION_PEOPLE = 2;

  private static final int FILTER_NONE = 0;
  private static final int FILTER_ARCHIVE = 1;
  private static final int FILTER_PRIVATE = 2;
  private static final int FILTER_GROUPS = 3;
  private static final int FILTER_CHANNELS = 4;
  private static final int FILTER_BOTS = 5;
  private static final int FILTER_UNREAD = 6;

  @Override
  protected int getPagerItemCount () {
    return 2;
  }

  private ChatsController newChatsController (int section, boolean needArchive) {
    ChatsController chats = new ChatsController(this.context, tdlib).setParent(this);
    ChatFilter filter = null;
    switch (section) {
      case FILTER_UNREAD:
        filter = ChatFilter.unreadFilter(tdlib);
        break;
      case FILTER_PRIVATE:
        filter = ChatFilter.privateFilter(tdlib);
        break;
      case FILTER_GROUPS:
        filter = ChatFilter.groupsFilter(tdlib);
        break;
      case FILTER_CHANNELS:
        filter = ChatFilter.channelsFilter(tdlib);
        break;
      case FILTER_BOTS:
        filter = ChatFilter.botsFilter(tdlib);
        break;
    }
    if (filter != null) {
      chats.setArguments(new ChatsController.Arguments(filter).setChatList(needArchive ? ChatPosition.CHAT_LIST_ARCHIVE : null).setIsBase(true));
    } else if (needArchive) {
      chats.setArguments(new ChatsController.Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setIsBase(true));
    }
    return chats;
  }

  private int getMenuSectionName () {
    if (menuNeedArchive) {
      switch (menuSection) {
        case FILTER_UNREAD:
          return R.string.CategoryArchiveUnread;
        case FILTER_PRIVATE:
          return R.string.CategoryArchivePrivate;
        case FILTER_GROUPS:
          return R.string.CategoryArchiveGroup;
        case FILTER_CHANNELS:
          return R.string.CategoryArchiveChannels;
        case FILTER_BOTS:
          return R.string.CategoryArchiveBots;
      }
      return R.string.CategoryArchive;
    } else {
      switch (menuSection) {
        case FILTER_UNREAD:
          return R.string.CategoryUnread;
        case FILTER_PRIVATE:
          return R.string.CategoryPrivate;
        case FILTER_GROUPS:
          return R.string.CategoryGroup;
        case FILTER_CHANNELS:
          return R.string.CategoryChannels;
        case FILTER_BOTS:
          return R.string.CategoryBots;
      }
      return R.string.Chats;
    }
  }

  private void modifyNewPagerItemController (final ViewController<?> c, final int position) {
    if (c instanceof RecyclerViewProvider) {
      c.get();
      ((RecyclerViewProvider) c).provideRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
        private float lastY;
        private float lastShowY;

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          lastY += dy;
          if (dy < 0 && lastShowY - lastY >= Screen.getTouchSlop()) {
            showHideCompose(position, true);
            lastShowY = lastY;
          } else if (lastY - lastShowY > Screen.getTouchSlopBig()) {
            showHideCompose(position, false);
            lastShowY = lastY;
          }
          if (Math.abs(lastY - lastShowY) > Screen.getTouchSlopBig()) {
            lastY = 0;
            lastShowY = 0;
          }
        }
      });
    }
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, final int position) {
    ViewController<?> c;
    switch (position) {
      case POSITION_CHATS: {
        c = newChatsController(this.menuSection, this.menuNeedArchive);
        break;
      }
      case POSITION_CALLS:
        c = new CallListController(this.context, tdlib);
        break;
      case POSITION_PEOPLE:
        c = new PeopleController(this.context, tdlib);
        break;
      default:
        throw new IllegalArgumentException("position == " + position);
    }
    modifyNewPagerItemController(c, position);
    return c;
  }

  @Override
  protected String[] getPagerSections () {
    return new String[] {Lang.getString(getMenuSectionName()).toUpperCase(), Lang.getString(R.string.Calls).toUpperCase()/*, UI.getString(R.string.Contacts).toUpperCase()*/};
  }

  // Menu

  @Override
  protected int getMenuId () {
    return R.id.menu_main;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_main: {
        header.addLockButton(menu);
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, getSearchHeaderIconColorId(), getSearchBackButtonResource());
        break;
      }
      default: {
        super.fillMenuItems(id, header, menu);
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        tdlib.checkDeadlocks();
        openSearchMode();
        break;
      }
      /*case R.id.menu_btn_more: {
        showMore(new int[] {
          R.id.more_btn_settings,
          R.id.more_btn_help
        }, new String[] {Lang.getString(R.string.Settings), Lang.getString(R.string.Help)}, 0);
        break;
      }*/
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
      default: {
        super.onMenuItemPressed(id, view);
        break;
      }
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (composeWrap != null && composeWrap.isShowing()) {
      composeWrap.close();
      return true;
    }
    return scrollToFirstPosition();
  }

  // Intent process

  private Tdlib shareTdlib;
  private String shareIntentAction;
  private Intent shareIntent;

  public void shareIntent (final Tdlib tdlib, final String intentAction, Intent intent) {
    this.shareTdlib = tdlib;
    this.shareIntentAction = intentAction;
    this.shareIntent = intent;
    shareIntentImpl();
  }

  private void shareIntentImpl () {
    final Tdlib tdlib = shareTdlib;
    final Intent intent = shareIntent;
    final String intentAction = shareIntentAction;
    if (intent == null) {
      return;
    }

    BaseActivity activity = context();
    if (activity.isPasscodeShowing()) {
      activity.addPasscodeListener(new BaseActivity.PasscodeListener() {
        @Override
        public void onPasscodeShowing (BaseActivity context, boolean isShowing) {
          if (isShowing) {
            activity.removePasscodeListener(this);
            shareIntentImpl();
          }
        }
      });
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      if (context().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        context().requestCustomPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, (code, permissions, grantResults, grantCount) -> {
          if (grantCount == permissions.length) {
            shareIntentImpl();
          } else {
            UI.showToast(R.string.NoStorageAccess, Toast.LENGTH_SHORT);
          }
        });
        return;
      }
    }

    shareTdlib = null;
    shareIntent = null;
    shareIntentAction = null;
    new Thread(() -> {
      CancellableRunnable runnable = new CancellableRunnable() {
        @Override
        public void act () {
          UI.showToast(R.string.ProcessingFileWait, Toast.LENGTH_SHORT);
        }
      };
      runnable.removeOnCancel(UI.getAppHandler());
      UI.post(runnable, 1000l);
      try {
        switch (intentAction) {
          case Intent.ACTION_SEND: {
            shareIntentImplSingle(tdlib, intent);
            break;
          }
          case Intent.ACTION_SEND_MULTIPLE: {
            shareIntentImplMultiple(tdlib, intent);
            break;
          }
        }
      } catch (Throwable t) {
        Log.e(t);
        UI.showToast(R.string.ShareContentUnsupported, Toast.LENGTH_SHORT);
      } finally {
        runnable.cancel();
      }
    }).start();
  }

  private void shareIntentImplSingle (Tdlib tdlib, final Intent intent) throws Throwable {
    String type = intent.getType();
    final ArrayList<TdApi.InputMessageContent> out = new ArrayList<>();

    if (!StringUtils.isEmpty(type) && ContactsContract.Contacts.CONTENT_VCARD_TYPE.equals(type)) {
      Uri uri = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
      if (uri == null) {
        throw new IllegalArgumentException("uri == null");
      }
      ContentResolver cr = UI.getContext().getContentResolver();
      try (InputStream stream = cr.openInputStream(uri)) {
        if (stream == null) {
          throw new IllegalArgumentException("stream == null (vcard)");
        }

        boolean vCardStarted = false;
        String currentName = null;

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StringUtils.UTF_8))) {
          String line;
          while ((line = bufferedReader.readLine()) != null) {
            String[] args = line.split(":");
            if (args.length != 2) {
              continue;
            }
            if (args[0].equals("BEGIN") && args[1].equals("VCARD")) {
              vCardStarted = true;
              currentName = null;
            } else if (args[0].equals("END") && args[1].equals("VCARD")) {
              vCardStarted = false;
            }
            if (!vCardStarted) {
              continue;
            }
            if (args[0].startsWith("FN") || (args[0].startsWith("ORG") && StringUtils.isEmpty(currentName))) {
              String nameEncoding = null;
              String nameCharset = "UTF-8";
              String[] params = args[0].split(";");
              for (String param : params) {
                String[] args2 = param.split("=");
                if (args2.length != 2) {
                  continue;
                }
                if (args2[0].equals("CHARSET")) {
                  nameCharset = args2[1];
                } else if (args2[0].equals("ENCODING")) {
                  nameEncoding = args2[1];
                }
              }
              currentName = args[1];
              if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                while (currentName.endsWith("=")) {
                  currentName = currentName.substring(0, currentName.length() - 1);
                  line = bufferedReader.readLine();
                  if (line == null) {
                    break;
                  }
                  currentName += line;
                }
                byte[] bytes = U.decodeQuotedPrintable(currentName.getBytes());
                if (bytes != null && bytes.length != 0) {
                  currentName = new String(bytes, nameCharset);
                }
              }
            } else if (args[0].startsWith("TEL")) {
              String phone = Strings.getNumber(args[1]);
              if (!phone.isEmpty()) {
                boolean found = false;
                if (!out.isEmpty()) {
                  for (TdApi.InputMessageContent content : out) {
                    if (content instanceof TdApi.InputMessageContact && StringUtils.equalsOrBothEmpty(((TdApi.InputMessageContact) content).contact.phoneNumber, phone)) {
                      found = true;
                      break;
                    }
                  }
                }
                if (!found) {
                  out.add(new TdApi.InputMessageContact(new TdApi.Contact(phone, currentName, null, null, 0))); // TODO VCARD sharing
                }
              }
            }
          }
        }
        if (out.isEmpty()) {
          UI.showToast("No phone number available to share", Toast.LENGTH_SHORT);
          return;
        }
      }
    } else {
      // First, obtain text
      String sendingText = obtainText(intent);

      // Second, obtain media
      Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      Uri uri = U.getUri(parcelable);
      if (uri != null) {
        if (U.isInternalUri(uri)) {
          throw new IllegalArgumentException("Tried to share internal file: " + uri.toString());
        }
        if (addShareUri(tdlib, out, type, uri, sendingText)) {
          sendingText = null;
        }
      }

      // If sendingText still unused, then add it to the beginning of send queue
      if (!StringUtils.isEmpty(sendingText)) {
        out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), false, false), tdlib.maxMessageTextLength()));
      }
    }
    shareContents(tdlib, type, out, false);
  }

  private static String obtainText (Intent intent) {
    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
    if (text == null) {
      CharSequence textSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
      if (textSequence != null) {
        text = textSequence.toString();
      }
    }
    String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
    String result = getShareText(subject, text);
    return result != null ? result.trim() : null;
  }

  private void shareIntentImplMultiple (Tdlib tdlib, Intent intent) {
    ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    ArrayList<TdApi.InputMessageContent> out = new ArrayList<>(uris.size());
    String type = intent.getType();

    // First, obtain text
    String sendingText = obtainText(intent);

    for (Parcelable parcelable : uris) {
      Uri uri = U.getUri(parcelable);
      if (uri == null) {
        throw new IllegalArgumentException("Unknown parcelable type: " + parcelable);
      }
      if (addShareUri(tdlib, out, type, uri, sendingText)) {
        sendingText = null;
      }
    }
    // If sendingText still unused, then add it to the beginning of send queue
    if (!StringUtils.isEmpty(sendingText)) {
      out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), false, false), tdlib.maxMessageTextLength()));
    }

    shareContents(tdlib, type, out, true);
  }

  private static String getShareText (String subject, String text) {
    if (StringUtils.isEmpty(text) && StringUtils.isEmpty(subject)) {
      return null;
    }
    StringBuilder b = new StringBuilder(StringUtils.length(text) + 1 + StringUtils.length(subject));
    if (!StringUtils.isEmpty(subject) && (StringUtils.isEmpty(text) || text.startsWith("https://") || text.startsWith("http://"))) {
      b.append(subject);
    }
    if (!StringUtils.isEmpty(text)) {
      if (b.length() > 0) {
        b.append('\n');
      }
      b.append(text);
    }
    String res = b.toString().trim();
    return res.isEmpty() ? null : res;
  }

  /**
   *
   * @return true if provided caption parameter has been used as caption
   */
  private static boolean addShareUri (Tdlib tdlib, ArrayList<TdApi.InputMessageContent> out, String mimeType, Uri uri, final @Nullable String rawCaption) {
    if (uri == null) {
      return false;
    }

    String filePath = U.tryResolveFilePath(uri);

    if (StringUtils.isEmpty(filePath)) {
      throw new IllegalArgumentException("filePath cannot be resolved for type " + mimeType + ", uri: " + uri);
    }

    if (mimeType.isEmpty()) {
      String extension = U.getExtension(filePath);
      if (extension != null) {
        mimeType = TGMimeType.mimeTypeForExtension(extension);
      }
    }

    if (!U.canReadFile(filePath)) {
      filePath = uri.toString();

      if (!U.canReadContentUri(uri)) {
        return false;
      }
    }

    final int captionCodePointCount = rawCaption != null ? rawCaption.codePointCount(0, rawCaption.length()) : 0;
    final TdApi.FormattedText messageCaption = captionCodePointCount > 0 && captionCodePointCount <= tdlib.maxCaptionLength() ? new TdApi.FormattedText(rawCaption, null) : null;

    if (!StringUtils.isEmpty(mimeType)) {
      if (mimeType.equals("image/webp")) {
        BitmapFactory.Options options = ImageReader.getImageWebpSize(filePath);
        out.add(new TdApi.InputMessageSticker(TD.createInputFile(filePath), null, options.outWidth, options.outHeight, null));
        return false;
      }

      if (mimeType.equals("image/gif")) {
        BitmapFactory.Options options = ImageReader.getImageSize(filePath);
        out.add(new TdApi.InputMessageAnimation(TD.createInputFile(filePath), null, null, 0, options.outWidth, options.outHeight, messageCaption, false));
        return messageCaption != null;
      }

      if (mimeType.startsWith("image/")) {
        BitmapFactory.Options opts = ImageReader.getImageSize(filePath);

        int rotation = U.getRotation(filePath);
        int inSampleSize = ImageReader.calculateInSampleSize(opts, 1280, 1280);
        int width = rotation == 90 || rotation == 270 ? opts.outHeight / inSampleSize : opts.outWidth / inSampleSize;
        int height = rotation == 90 || rotation == 270 ? opts.outWidth / inSampleSize : opts.outHeight / inSampleSize;

        TdApi.InputFileGenerated inputFile = PhotoGenerationInfo.newFile(filePath, rotation);
        out.add(new TdApi.InputMessagePhoto(inputFile, null, null, width, height, messageCaption, 0, false));
        return messageCaption != null;
      }

      if (mimeType.startsWith("video/")) {
        MediaMetadataRetriever media = null;
        try {
          media = U.openRetriever(filePath);
        } catch (Throwable ignored) { }
        if (media != null) {

          String rawDuration = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
          int duration = StringUtils.isNumeric(rawDuration) ? (int) (Long.parseLong(rawDuration) / 1000l) : 0;
          String rawWidth = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
          int width = StringUtils.isNumeric(rawWidth) ? StringUtils.parseInt(rawWidth) : 0;
          String rawHeight = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
          int height = StringUtils.isNumeric(rawHeight) ? StringUtils.parseInt(rawHeight) : 0;

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            String rawRotation = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int rotation = StringUtils.isNumeric(rawRotation) ? StringUtils.parseInt(rawRotation) : 0;
            if (rotation == 90 || rotation == 270) {
              int temp = height;
              height = width;
              width = temp;
            }
          }


          TdApi.InputFile inputVideo;
          if (Config.USE_VIDEO_COMPRESSION) {
            inputVideo = VideoGenerationInfo.newFile(filePath, null, false);
          } else {
            inputVideo = TD.createInputFile(filePath);
          }

          U.closeRetriever(media);

          out.add(new TdApi.InputMessageVideo(inputVideo, null, null, duration, width, height, U.canStreamVideo(inputVideo), messageCaption, 0, false));
          return messageCaption != null;
        }
      }
    }

    TD.FileInfo info = new TD.FileInfo();
    TdApi.InputFile file = TD.createInputFile(filePath, mimeType, info);

    out.add(TD.toInputMessageContent(filePath, file, info, messageCaption, false));
    return messageCaption != null;
  }

  private void shareContents (final Tdlib tdlib, final String type, final ArrayList<TdApi.InputMessageContent> contents, boolean mergeAlbum) {
    if (contents.isEmpty()) {
      throw new IllegalArgumentException("Unsupported content type: " + type);
    }
    tdlib.ui().post(() -> tdlib.awaitInitialization(() -> tdlib.ui().post(() -> {
      ShareController c = new ShareController(context, tdlib);

      ShareController.ShareProviderDelegate shareDelegate = new ShareController.ShareProviderDelegate() {
        @Override
        public void generateFunctionsForChat (long chatId, TdApi.Chat chat, TdApi.MessageSendOptions sendOptions, ArrayList<TdApi.Function<?>> functions) {
          List<TdApi.InputMessageContent> album = null;
          for (TdApi.InputMessageContent content : contents) {
            content = tdlib.filegen().createThumbnail(content, ChatId.isSecret(chatId));
            if (mergeAlbum) {
              int combineMode = TD.getCombineMode(content);
              if (combineMode == TD.COMBINE_MODE_MEDIA) {
                if (album == null)
                  album = new ArrayList<>();
                album.add(content);
                if (album.size() == TdConstants.MAX_MESSAGE_GROUP_SIZE)
                  TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
              } else {
                TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
                TD.processSingle(tdlib, chatId, sendOptions, functions, content);
              }
            } else {
              TD.processSingle(tdlib, chatId, sendOptions, functions, content);
            }
          }
          if (mergeAlbum) {
            TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
          }
        }

        @Override
        public CharSequence generateErrorMessageForChat (long chatId) {
          for (TdApi.InputMessageContent content : contents) {
            CharSequence errorMessage = tdlib.getRestrictionText(tdlib.chatStrict(chatId), content);
            if (errorMessage != null)
              return errorMessage;
          }
          return null;
        }
      };

      c.setArguments(new ShareController.Args(shareDelegate).setNeedOpenChat(true));
      c.show();
    })));
  }

  // Sync alert

  private SettingsWrap syncAlertWrap;
  private boolean syncShown;

  private void showSyncAlert () {
    if (syncShown || (syncAlertWrap != null && syncAlertWrap.window != null && !syncAlertWrap.window.isWindowHidden()) || !Settings.instance().needTutorial(Settings.TUTORIAL_SYNC_SETTINGS))
      return;
    syncAlertWrap = showSettings(new SettingsWrapBuilder(R.id.btn_notificationSettings).setIntDelegate((id, result) -> {
      SyncAdapter.turnOnSync(context, tdlib, true);
    }).setDismissListener(popup -> {
      syncAlertWrap = null;
    }).setOnActionButtonClick((wrap, view, isCancel) -> {
      if (isCancel) {
        int i = wrap.adapter.indexOfViewById(R.id.btn_neverAllow);
        if (i != -1 && wrap.adapter.getItems().get(i).isSelected()) {
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_SYNC_SETTINGS);
        }
      }
      return false;
    }).setCancelStr(R.string.NotificationSyncDecline).setSaveStr(R.string.NotificationSyncAccept).setAllowResize(false).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_neverAllow, 0, R.string.NeverShowAgain, false)
    }).addHeaderItem(Lang.getMarkdownString(this, R.string.NotificationSyncOffWarn)));
    if (syncAlertWrap != null)
      syncShown = true;
  }

  private void hideSyncAlert () {
    if (syncAlertWrap != null) {
      if (syncAlertWrap.window != null)
        syncAlertWrap.window.hideWindow(true);
      syncAlertWrap = null;
      syncShown = false;
    }
  }

  private void checkSyncAlert () {
    if (tdlib.notifications().needSyncAlert()) {
      showSyncAlert();
    } else {
      hideSyncAlert();
    }
  }
}
