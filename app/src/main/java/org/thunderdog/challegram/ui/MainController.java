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

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
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
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.sync.SyncAdapter;
import org.thunderdog.challegram.telegram.ChatFilter;
import org.thunderdog.challegram.telegram.ChatFolderStyle;
import org.thunderdog.challegram.telegram.ChatFoldersListener;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
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
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.IconSpan;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.BubbleLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SnackBar;
import org.thunderdog.challegram.widget.ViewPager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class MainController extends ViewPagerController<Void> implements Menu, MoreDelegate, OverlayButtonWrap.Callback, TdlibOptionListener, AppUpdater.Listener, ChatFoldersListener, GlobalCountersListener, Settings.ChatFolderSettingsListener {
  private static final long MAIN_PAGER_ITEM_ID = Long.MIN_VALUE;
  private static final long ARCHIVE_PAGER_ITEM_ID = Long.MIN_VALUE + 1;
  private static final long INVALID_PAGER_ITEM_ID = Long.MAX_VALUE;

  public MainController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_main;
  }

  private FrameLayoutFix mainWrap;
  private FrameLayoutFix pagerWrap;
  private OverlayButtonWrap composeWrap;

  @Override
  protected View onCreateView (Context context) {
    initPagerSections();
    return super.onCreateView(context);
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    if (BuildConfig.DEBUG) {
      Test.execute();
    }

    pagerWrap = new FrameLayoutFix(context);
    pagerWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    pagerWrap.addView(pager);

    mainWrap = new FrameLayoutFix(context);
    mainWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    mainWrap.addView(pagerWrap);
    generateChatSearchView(mainWrap);

    contentView.addView(mainWrap);

    UI.setSoftInputMode(UI.getContext(context), Config.DEFAULT_WINDOW_PARAMS);

    composeWrap = new OverlayButtonWrap(context);
    composeWrap.initWithList(this,
      ColorId.circleButtonActive,
      ColorId.circleButtonActiveIcon,
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
        ColorId.circleButtonRegular,
        ColorId.circleButtonNewSecret,
        ColorId.circleButtonNewChannel,
        ColorId.circleButtonNewGroup,
        ColorId.circleButtonNewChat
      }, new int[] {
        ColorId.circleButtonRegularIcon,
        ColorId.circleButtonNewSecretIcon,
        ColorId.circleButtonNewChannelIcon,
        ColorId.circleButtonNewGroupIcon,
        ColorId.circleButtonNewChatIcon
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
    tdlib.listeners().subscribeToChatFoldersUpdates(this);
    tdlib.context().global().addCountersListener(this);
    Settings.instance().addChatFolderSettingsListener(this);
    if (Settings.instance().chatFoldersEnabled()) {
      if (this.chatFolderInfos != tdlib.chatFolders()) {
        updatePagerSections(true);
      }
      tdlib.settings().addChatListPositionListener(chatListPositionListener = new ChatListPositionListener());
    }

    prepareControllerForPosition(0, this::executeScheduledAnimation);

    if (headerCell != null) {
      headerCell.getTopView().setOnSlideOffListener(new ViewPagerTopView.OnSlideOffListener() {

        private long selectedPagerItemId = INVALID_PAGER_ITEM_ID;

        @Override
        public boolean onSlideOffPrepare (View view, MotionEvent event, int index) {
          return hasFolders() || getPagerItemId(index) == MAIN_PAGER_ITEM_ID;
        }

        @Override
        public void onSlideOffStart (View view, MotionEvent event, int index) {
          selectedPagerItemId = getPagerItemId(index);
          setMenuVisible(view, selectedPagerItemId, true);
          if (composeWrap != null && displayTabsAtBottom()) {
            composeWrap.hide();
          }
        }

        @Override
        public void onSlideOffFinish (View view, MotionEvent event, int index, boolean apply) {
          float offsetX = getOffsetX(view);
          float offsetY = getOffsetX(view);
          if (apply && selectedSection != -1) {
            requestChatsSection(selectedSection, selectedPagerItemId);
            setSelectedSection(-1, event, offsetX, offsetY, true);
          } else {
            setSelectedSection(-1, event, offsetX, offsetY, false);
          }
          setMenuVisible(view, selectedPagerItemId, false);
          selectedPagerItemId = INVALID_PAGER_ITEM_ID;
        }

        @Override
        public void onSlideOffMovement (View view, MotionEvent event, int index) {
          float x = event.getX();
          float y = event.getY();
          float offsetX = getOffsetX(view);
          float offsetY = getOffsetY(view);
          x -= offsetX;
          y -= offsetY + mainWrap.getTranslationY() + pagerWrap.getTranslationY();
          int selectedSection = -1;
          if (x >= 0 && x < menu.getMeasuredWidth()) {
            int firstItemEnd = menu.getChildAt(0).getBottom();
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
          setSelectedSection(selectedSection, event, offsetX, offsetY, false);
        }

        private float getOffsetX (View view) {
          return menu.getX() - getX(view, headerCell.getView());
        }

        private float getOffsetY (View view) {
          FrameLayoutFix.LayoutParams lp = (FrameLayoutFix.LayoutParams) menu.getLayoutParams();
          int verticalGravity = Gravity.VERTICAL_GRAVITY_MASK & lp.gravity;
          return verticalGravity == Gravity.BOTTOM ? -menu.getHeight() : view.getHeight();
        }
      });
      if (Settings.instance().chatFoldersEnabled()) {
        headerCell.getTopView().setCounterAlphaProvider(new ViewPagerTopView.CounterAlphaProvider() {
          @Override
          public float getTextAlpha (Counter counter, float alphaFactor) {
            return MathUtils.fromTo(1f, .5f + .5f * alphaFactor, counter.getMuteFactor());
          }
        });
      }
    }

    // tdlib.awaitConnection(this::unlockTabs);

    checkTabs();
    checkMargins();

    context().appUpdater().addListener(this);
    if (context().appUpdater().state() == AppUpdater.State.READY_TO_INSTALL) {
      onAppUpdateAvailable(context().appUpdater().flowType() == AppUpdater.FlowType.TELEGRAM_CHANNEL, true);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Chats);
  }

  @Override
  public View getCustomHeaderCell () {
    if (displayTabsAtBottom()) {
      return null;
    }
    if (headerCell != null) {
      View headerCellView = headerCell.getView();
      if (bottomBar != null && headerCellView.getParent() == bottomBar) {
        return null;
      }
      return headerCellView;
    }
    return null;
  }

  // Menu

  private int selectedSection = -1;
  private long downTime;

  private void setSelectedSection (int section, MotionEvent event, float xOffset, float yOffset, boolean apply) {
    float eventX = event.getX() - xOffset;
    float eventY = event.getY() - yOffset;
    if (this.selectedSection != section) {
      if (this.selectedSection != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), apply ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, eventX, eventY - view.getTop(), event.getMetaState()));
      }
      this.selectedSection = section;
      if (section != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime = SystemClock.uptimeMillis(), event.getEventTime(), MotionEvent.ACTION_DOWN, eventX, eventY - view.getTop(), event.getMetaState()));
      }
    } else if (section != -1) {
      View view = menu.getChildAt(this.selectedSection);
      view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), MotionEvent.ACTION_MOVE, eventX, eventY - view.getTop(), event.getMetaState()));
    }
  }

  private @Nullable BubbleLayout menu;
  private boolean menuNeedArchive;
  private View menuAnchor;

  private int pendingSection = -1;
  private long pendingPagerItemId = INVALID_PAGER_ITEM_ID;
  private ChatsController pendingChatsController;

  private void cancelPendingSection () {
    if (pendingChatsController != null) {
      pendingChatsController.destroy();
      pendingChatsController = null;
    }
    this.pendingSection = -1;
    this.pendingPagerItemId = INVALID_PAGER_ITEM_ID;
  }

  private int applySection (int section, long pagerItemId, ChatsController chatsController) {
    if (pagerItemId == INVALID_PAGER_ITEM_ID || headerCell == null)
      return NO_POSITION;

    setSelectedFilter(pagerItemId, section);

    int pagerItemPosition = getPagerItemPosition(pagerItemId);
    if (pagerItemPosition == NO_POSITION)
      return NO_POSITION;

    ViewPagerTopView.Item item;
    if (hasFolders()) {
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        item = buildMainSectionItem(pagerItemPosition, chatFolderStyle);
      } else if (pagerItemId == ARCHIVE_PAGER_ITEM_ID) {
        item = buildArchiveSectionItem(pagerItemPosition, chatFolderStyle);
      } else {
        TdApi.ChatList chatList = pagerChatLists.get(pagerItemPosition);
        if (TD.isChatListFolder(chatList)) {
          TdApi.ChatListFolder chatListFolder = (TdApi.ChatListFolder) chatList;
          TdApi.ChatFolderInfo chatFolderInfo = tdlib.chatFolderInfo(chatListFolder.chatFolderId);
          if (chatFolderInfo != null) {
            item = buildSectionItem(pagerItemId, pagerItemPosition, chatList, chatFolderInfo, chatFolderStyle);
          } else {
            chatsController.destroy();
            return NO_POSITION;
          }
        } else {
          throw new UnsupportedOperationException("chatList = " + chatList);
        }
      }
    } else {
      int filter = getSelectedFilter(pagerItemId);
      if (filter == FILTER_NONE && !menuNeedArchive) {
        item = getDefaultMainItem();
      } else {
        CharSequence sectionName = getMenuSectionName(pagerItemId, pagerItemPosition, /* hasFolders */ false, ChatFolderStyle.LABEL_ONLY, /* upperCase */ true);
        item = new ViewPagerTopView.Item(sectionName);
      }
      getDefaultSectionItems().set(0, item);
    }
    headerCell.getTopView().setItemAt(pagerItemPosition, item);

    replaceController(pagerItemId, chatsController);
    if (getCurrentPagerItemPosition() == pagerItemPosition) {
      showComposeWrap(null);
    }
    return pagerItemPosition;
  }

  private void setNeedArchive (boolean needArchive) {
    if (this.menuNeedArchive != needArchive) {
      this.menuNeedArchive = needArchive;
    }
  }

  private void requestChatsSection (int requestedSection, long pagerItemId) {
    if (pagerItemId == INVALID_PAGER_ITEM_ID)
      return;
    int menuSection = getSelectedFilter(pagerItemId);
    if ((menuSection == requestedSection && (requestedSection != FILTER_NONE || (pagerItemId == MAIN_PAGER_ITEM_ID && !menuNeedArchive))) ||
        (pagerItemId == MAIN_PAGER_ITEM_ID && menuNeedArchive && menuSection == FILTER_NONE && requestedSection == FILTER_ARCHIVE)) {
      cancelPendingSection();
      int pagerItemPosition = getPagerItemPosition(pagerItemId);
      if (pagerItemPosition != NO_POSITION) {
        setCurrentPagerPosition(pagerItemPosition, true);
      }
      return;
    }
    if (this.pendingSection == requestedSection && this.pendingPagerItemId == pagerItemId) {
      return; // Still waiting to switch, do nothing
    }
    cancelPendingSection();

    int position = getPagerItemPosition(pagerItemId);
    if (position == NO_POSITION)
      return;
    int section;
    if (requestedSection == FILTER_NONE) {
      section = FILTER_NONE;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        setNeedArchive(false);
      }
    } else if (requestedSection == FILTER_ARCHIVE) {
      section = FILTER_NONE;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        setNeedArchive(menuSection != FILTER_NONE || !menuNeedArchive);
      }
    } else {
      section = requestedSection;
    }
    TdApi.ChatList chatList;
    if (pagerItemId == MAIN_PAGER_ITEM_ID) {
      chatList = menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
    } else {
      chatList = pagerChatLists.get(position);
    }
    ChatsController c = newChatsController(chatList, section);
    this.pendingChatsController = c;
    this.pendingSection = section;
    this.pendingPagerItemId = pagerItemId;
    c.postOnAnimationExecute(() -> {
      if (this.pendingSection == section && this.pendingChatsController == c && this.pendingPagerItemId == pagerItemId) {
        this.pendingSection = -1;
        this.pendingPagerItemId = INVALID_PAGER_ITEM_ID;
        this.pendingChatsController = null;
        int pagerItemPosition = applySection(section, pagerItemId, c);
        if (pagerItemPosition != NO_POSITION) {
          setCurrentPagerPosition(pagerItemPosition, true);
        }
      }
    });
    modifyNewPagerItemController(c);
  }

  private void layoutMenu (@NonNull View view) {
    if (menu == null || headerCell == null)
      return;
    menuAnchor = view;
    int menuWidth = menu.getMeasuredWidth();
    if (menuWidth == 0)
      return;
    float x = getX(view, headerCell.getView());
    boolean displayTabsAtBottom = displayTabsAtBottom();
    float translationX = x - menuWidth / 2 + view.getMeasuredWidth() / 2;
    int dx = displayTabsAtBottom ? 0 : Screen.dp(14f);
    menu.setTranslationX(MathUtils.clamp(translationX, -dx, Screen.currentWidth() - menuWidth + dx));
    if (displayTabsAtBottom) {
      int cornerCenterX = menuWidth / 2 + Math.round(translationX - menu.getTranslationX());
      menu.setCornerCenterX(MathUtils.clamp(cornerCenterX, Screen.dp(18f), menuWidth - Screen.dp(18f)));
    }
  }

  private static float getX (View view, View parent) {
    float x = view.getX();
    View currentView = view;
    do {
      currentView = (View) currentView.getParent();
      if (currentView == null)
        break;
      x += currentView.getX();
    } while (currentView != parent);
    return x;
  }

  private void setMenuVisible (View anchorView, long pagerItemId, boolean visible) {
    if (!visible && menu == null)
      return;
    View parent = null;
    if (menu == null) {
      boolean top = !displayTabsAtBottom();
      menu = new BubbleLayout(context, this, top) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (menuAnchor != null) {
            layoutMenu(menuAnchor);
          }
        }
      };
      int gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.LEFT;
      int marginBottom = top ? 0 : getHeaderHeight();
      menu.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity, 0, 0, 0, marginBottom));
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
        sectionView.setGravity(Gravity.CENTER);
        sectionView.setPadding(Screen.dp(18f), Screen.dp(13f), Screen.dp(18f), Screen.dp(14f));
        sectionView.setTypeface(Fonts.getRobotoMedium());
        sectionView.setTag(index);
        sectionView.setOnClickListener(v -> { });
        Views.setMediumText(sectionView, Lang.getString(bindLocaleChanger(section, sectionView, false, true)));
        RippleSupport.setTransparentBlackSelector(sectionView);
        menu.addView(sectionView);
        index++;
      }
      pagerWrap.addView(menu, 1);
      parent = pagerWrap;
    }
    if (visible) {
      int selectedFilter = getSelectedFilter(pagerItemId);

      boolean needArchive = pagerItemId == MAIN_PAGER_ITEM_ID && (menuNeedArchive || (!tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE) && tdlib.hasArchivedChats()));
      menu.getChildAt(FILTER_ARCHIVE).setVisibility(needArchive ? View.VISIBLE : View.GONE);

      TdApi.ChatList chatList;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        chatList = selectedFilter == FILTER_ARCHIVE || menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
      } else {
        int pagerItemPosition = getPagerItemPosition(pagerItemId);
        chatList = hasFolders() && pagerItemPosition < pagerChatLists.size() ? pagerChatLists.get(pagerItemPosition) : null;;
      }

      boolean hasUnreadChats = chatList != null && tdlib.hasUnreadChats(chatList);
      boolean needUnread = selectedFilter == FILTER_UNREAD || hasUnreadChats;
      menu.getChildAt(FILTER_UNREAD).setVisibility(needUnread ? View.VISIBLE : View.GONE);

      for (int index = 0; index < menu.getChildCount(); index++) {
        TextView sectionView = (TextView) menu.getChildAt(index);
        if (sectionView.getVisibility() != View.VISIBLE)
          continue;
        boolean isSelected;
        if (index == FILTER_NONE && pagerItemId == MAIN_PAGER_ITEM_ID) {
          isSelected = selectedFilter == FILTER_NONE && !menuNeedArchive;
        } else if (index == FILTER_ARCHIVE && pagerItemId == MAIN_PAGER_ITEM_ID) {
          isSelected = menuNeedArchive;
        } else {
          isSelected = selectedFilter == index;
        }
        int textColorId = isSelected ? ColorId.textNeutral : ColorId.textLight;
        sectionView.setTextColor(Theme.getColor(textColorId));
        addThemeTextColorListener(sectionView, textColorId);
      }
      layoutMenu(anchorView);
    } else {
      for (int index = 0; index < menu.getChildCount(); index++) {
        removeThemeListenerByTarget(menu.getChildAt(index));
      }
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
    return pagerWrap;
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
    ChatsController c = findChatsControllerForSearchMessages();
    if (c != null && TD.isChatListArchive(c.chatList())) {
      return ChatPosition.CHAT_LIST_ARCHIVE;
    }
    return null;
  }

  @Nullable
  public final ChatsController findMainChatsController () {
    return (ChatsController) getCachedControllerForItemId(MAIN_PAGER_ITEM_ID);
  }

  @Nullable
  public final ChatsController getCurrentChatsController () {
    ViewController<?> current = getCurrentPagerItem();
    return current instanceof ChatsController ? (ChatsController) current : null;
  }

  @Nullable
  public final ChatsController findChatsControllerForSearchMessages () {
    return hasFolders() ? getCurrentChatsController() : findMainChatsController();
  }

  @Override
  protected boolean filterChatMessageSearchResult (TdApi.Chat chat) {
    ChatsController c = findChatsControllerForSearchMessages();
    if (c != null) {
      TdApi.ChatList chatList = c.chatList();
      if (TD.isChatListFolder(chatList) && Config.SEARCH_MESSAGES_ONLY_IN_SELECTED_FOLDER) {
        if (ChatPosition.findPosition(chat, chatList) == null)
          return false;
      }
      ChatFilter filter = c.getFilter();
      if (filter != null && filter.canFilterMessages()) {
        return filter.accept(chat);
      }
    }
    return super.filterChatMessageSearchResult(chat);
  }

  @Override
  protected int getChatMessagesSearchTitle () {
    ChatsController c = findChatsControllerForSearchMessages();
    ChatFilter filter = c != null ? c.getFilter() : null;
    boolean isArchive = c != null && TD.isChatListArchive(c.chatList());
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
    if (id == R.id.user) {
      TdApi.User user = ((TdApi.User) view.getTag());
      if (user != null) {
        tdlib.ui().openPrivateChat(this, user.id, null);
      }
    } else if (id == R.id.btn_float_addContact) {
      navigateTo(new PhoneController(context, tdlib).setMode(PhoneController.MODE_ADD_CONTACT));
    } else if (id == R.id.btn_float_call) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_CALL);
      navigateTo(c);
    } else if (id == R.id.btn_float_compose) {
      if (navigationController != null && headerView != null && !navigationController.isAnimating() && !headerView.isAnimating() && composeWrap != null) {
        composeWrap.toggle();
      }
      return false;
    } else if (id == R.id.btn_float_newChannel) {
      navigateTo(new CreateChannelController(context, tdlib));
    } else if (id == R.id.btn_float_newGroup) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_GROUP);
      navigateTo(c);
    } else if (id == R.id.btn_float_newSecretChat) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_SECRET_CHAT);
      navigateTo(c);
    } else if (id == R.id.btn_float_newChat) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_CHAT);
      navigateTo(c);
    }
    return true;
  }

  @Override
  public void onPageScrolled (int position, int actualPosition, float actualPositionOffset, int actualPositionOffsetPixels) {
    super.onPageScrolled(position, actualPosition, actualPositionOffset, actualPositionOffsetPixels);
    if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL && displayTabsAtBottom()) {
      showBottomBar();
    }
  }

  @Override
  public void onPageSelected (int position, int actualPosition) {
    if (!inSearchMode()) {
      composeWrap.show();
    }
    if (hasFolders()) {
      if (composeWrap.getMainButtonId() != R.id.btn_float_compose) {
        composeWrap.replaceMainButton(R.id.btn_float_compose, R.drawable.baseline_create_24);
      }
      return;
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
    if (needUpdatePagerSections) {
      needUpdatePagerSections = false;
      updatePagerSections(true);
      if (headerCell != null) {
        ViewUtils.runJustBeforeBeingDrawn(headerCell.getView(), () ->
          headerCell.getTopView().updateAnchorPosition(false)
        );
      }
    }
    if (headerView != null) {
      headerView.updateLockButton(getMenuId());
    }
    checkTabs();
    checkMenu();
    checkMargins();
  }

  private void checkMargins () {
    checkPagerMargins();
    checkHeaderMargins();
    checkComposeWrapPaddings();
  }

  private void checkComposeWrapPaddings () {
    if (composeWrap != null) {
      int paddingBottom = displayTabsAtBottom() ? getHeaderHeight() : 0;
      composeWrap.setPadding(composeWrap.getPaddingLeft(), composeWrap.getPaddingTop(), composeWrap.getPaddingRight(), paddingBottom);
      composeWrap.setClipToPadding(paddingBottom == 0);
    }
  }

  private void checkPagerMargins () {
    if (!Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL && displayTabsAtBottom()) {
      Views.setBottomMargin(getViewPager(), getHeaderHeight());
      Views.setBottomMargin(pagerWrap, updateSnackBar != null ? Math.round(updateSnackBar.getHeight() * updateSnackBar.getVisibilityFactor()) : 0); // FIXME
    } else {
      Views.setBottomMargin(getViewPager(), 0);
      Views.setBottomMargin(pagerWrap, 0);
    }
  }

  private void checkHeaderMargins () {
    if (headerCell == null)
      return;
    ViewPagerHeaderViewCompact headerCellView = ((ViewPagerHeaderViewCompact) headerCell.getView());
    RecyclerView recyclerView = headerCellView.getRecyclerView();
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) recyclerView.getLayoutParams();
    if (params.rightMargin != 0 || params.leftMargin != 0) {
      params.leftMargin = 0;
      params.rightMargin = 0;
      recyclerView.setLayoutParams(params);
    }
    int paddingLeft, paddingRight;
    if (displayTabsAtBottom()) {
      paddingLeft = paddingRight = 0;
      recyclerView.setPadding(0, 0, 0, 0);
    } else {
      recyclerView.setClipToPadding(false);
      recyclerView.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
      int menuWidth = Screen.dp(44f);
      if (Passcode.instance().isEnabled()) {
        menuWidth += Screen.dp(48f);
      }
      if (Lang.rtl()) {
        paddingLeft = menuWidth;
        paddingRight = Screen.dp(44f);
      } else {
        paddingLeft = Screen.dp(44f);
        paddingRight = menuWidth;
      }
    }
    if (headerCellView.getPaddingLeft() != paddingLeft || headerCellView.getPaddingRight() != paddingRight) {
      headerCellView.setPadding(paddingLeft, headerCellView.getPaddingTop(), paddingRight, headerCellView.getPaddingBottom());
    }
  }

  private SnackBar updateSnackBar;

  private void onAppUpdateAvailable (boolean isApk, boolean immediate) {
    if (updateSnackBar == null) {
      updateSnackBar = new SnackBar(context);
      updateSnackBar.setCallback(new SnackBar.Callback() {
        @Override
        public void onSnackBarTransition (SnackBar v, float factor) {
          float offsetBySnackBar = v.getMeasuredHeight() * factor;
          composeWrap.setTranslationY(-offsetBySnackBar);
          if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
            bottomBarOffsetBySnackBar = -offsetBySnackBar;
            invalidateBottomBarOffset();
          } else if (displayTabsAtBottom()) {
            Views.setBottomMargin(pagerWrap, Math.round(offsetBySnackBar)); // FIXME
          }
        }

        @Override
        public void onDestroySnackBar (SnackBar v) {
          if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
            bottomBarOffsetBySnackBar = 0;
            invalidateBottomBarOffset();
          } else {
            Views.setBottomMargin(pagerWrap, 0); // FIXME
          }
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
    tdlib.context().global().removeCountersListener(this);
    Settings.instance().removeChatFolderSettingsListener(this);
    tdlib.listeners().unsubscribeFromChatFoldersUpdates(this);
    if (chatListUnreadCountListener != null) {
      tdlib.listeners().removeTotalChatCounterListener(chatListUnreadCountListener);
      chatListUnreadCountListener = null;
    }
    if (defaultCounterListener != null) {
      tdlib.listeners().removeTotalChatCounterListener(defaultCounterListener);
      defaultCounterListener = null;
      defaultMainItem = null;
    }
    if (chatListPositionListener != null) {
      tdlib.settings().removeChatListPositionListener(chatListPositionListener);
      chatListPositionListener = null;
    }
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
          colors.append(OptionColor.BLUE);
          icons.append(R.drawable.baseline_check_24);

          ids.append(R.id.btn_cancel);
          icons.append(R.drawable.baseline_cancel_24);
          colors.append(OptionColor.NORMAL);
          strings.append(R.string.Cancel);

          ids.append(R.id.btn_languageSettings);
          strings.append(R.string.MoreLanguages);
          colors.append(OptionColor.NORMAL);
          icons.append(R.drawable.baseline_language_24);

          CharSequence text = Strings.buildMarkdown(this, TD.findOrdinary(cloudStrings, Lang.getResourceEntryName(R.string.language_continueInLanguagePopupText), () -> Lang.getString(R.string.language_continueInLanguagePopupText)), null);

          PopupLayout popupLayout = showOptions(text, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
            if (id == R.id.btn_done) {
              tdlib.applyLanguage(languagePack, applySuccess -> {
                if (applySuccess) {
                  UI.showToast(R.string.language_appliedLanguage, Toast.LENGTH_SHORT);
                  Settings.instance().setRecommendedLanguagePackId(languagePackId);
                }
              }, true);
            } else if (id == R.id.btn_cancel) {
              Settings.instance().setRecommendedLanguagePackId(languagePackId);
            } else if (id == R.id.btn_languageSettings) {
              Settings.instance().setRecommendedLanguagePackId(languagePackId);
              navigateTo(new SettingsLanguageController(context, tdlib));
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
      showOptions(Lang.getStringBold(R.string.EmojiSetUpdated, emojiPack.displayName), new int[] {R.id.btn_downloadFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.EmojiSetUpdate), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, new int[] {R.drawable.baseline_sync_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        if (id == R.id.btn_downloadFile) {
          SettingsCloudEmojiController c = new SettingsCloudEmojiController(context, tdlib);
          c.setArguments(new SettingsCloudController.Args<>(emojiPack));
          navigateTo(c);
        } else if (id == R.id.btn_cancel) {
          Settings.instance().revokeOutdatedEmojiPack();
        }
        return true;
      });
    }, 100);
    return true;
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
    tdlib.checkDeadlocks(() -> runOnUiThreadOptional(() ->
      context().permissions().requestPostNotifications(granted -> {
        if (granted) {
          tdlib.notifications().onNotificationPermissionGranted();
        }
      })
    ));
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
      int top, bottom;
      if (displayTabsAtBottom()) {
        top = Views.getLocationInWindow(headerCell.getView())[1];
        bottom = top + headerCell.getView().getHeight();
      } else {
        top = HeaderView.getTopOffset();
        bottom = HeaderView.getSize(true);
      }
      if (y < bottom && y >= top && x < headerCell.getView().getMeasuredWidth()) {
        return !((ViewPagerHeaderViewCompact) headerCell.getView()).canScrollLeft();
      }
    }
    return true;
  }

  @Override
  protected int getMenuButtonsWidth () {
    return 0; // disable header margins
  }

  public boolean showComposeWrap (ViewController<?> controller) {
    if (!inSearchMode() && (controller == null || getCurrentPagerItem() == controller)) {
      composeWrap.show();
      return true;
    }
    return false;
  }

  private boolean showHideCompose (ViewController<?> controller, boolean show) {
    if (getCurrentPagerItem() == controller) {
      if (show) {
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          showBottomBar();
        }
        return showComposeWrap(null);
      } else {
        if (getCurrentPagerItemId() == MAIN_PAGER_ITEM_ID) {
          ChatsController c = findMainChatsController();
          if (c != null && c.isPullingArchive()) {
            return false;
          }
        }
        composeWrap.hide();
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          hideBottomBar();
        }
        return true;
      }
    }
    return false;
  }

  // Controllers

  private static final int POSITION_CHATS = 0;
  private static final int POSITION_CALLS = 1;
  private static final int POSITION_PEOPLE = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FILTER_NONE, FILTER_ARCHIVE, FILTER_PRIVATE, FILTER_GROUPS, FILTER_CHANNELS, FILTER_BOTS, FILTER_UNREAD})
  private @interface Filter { }

  private static final int FILTER_NONE = 0;
  private static final int FILTER_ARCHIVE = 1;
  private static final int FILTER_PRIVATE = 2;
  private static final int FILTER_GROUPS = 3;
  private static final int FILTER_CHANNELS = 4;
  private static final int FILTER_BOTS = 5;
  private static final int FILTER_UNREAD = 6;

  private @Filter int getSelectedFilter (long pagerItemId) {
    return pagerChatListFilters.get(pagerItemId, FILTER_NONE);
  }

  private void setSelectedFilter (long pagerItemId, @Filter int filter) {
    pagerChatListFilters.put(pagerItemId, filter);
  }

  @Override
  protected int getPagerItemCount () {
    return hasFolders() ? pagerChatLists.size() : 2;
  }

  protected long getPagerItemId (int position) {
    if (hasFolders()) {
      return getPagerItemId(pagerChatLists.get(position));
    }
    if (position == POSITION_CHATS) {
      return MAIN_PAGER_ITEM_ID;
    }
    return position;
  }

  private long getPagerItemId (TdApi.ChatList chatList) {
    switch (chatList.getConstructor()) {
      case TdApi.ChatListMain.CONSTRUCTOR:
        return MAIN_PAGER_ITEM_ID;
      case TdApi.ChatListArchive.CONSTRUCTOR:
        return ARCHIVE_PAGER_ITEM_ID;
      case TdApi.ChatListFolder.CONSTRUCTOR:
        return ((TdApi.ChatListFolder) chatList).chatFolderId;
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public boolean onPagerItemLongClick (int index) {
    if (hasFolders()) {
      TdApi.ChatList chatList = pagerChatLists.get(index);
      showChatListOptions(chatList);
      return true;
    }
    return false;
  }

  private ChatsController newChatsController (TdApi.ChatList chatList, @Filter int filter) {
    ChatsController chats = new ChatsController(this.context, tdlib).setParent(this);
    ChatFilter chatFilter;
    switch (filter) {
      case FILTER_NONE:
        chatFilter = null;
        break;
      case FILTER_UNREAD:
        chatFilter = ChatFilter.unreadFilter(tdlib);
        break;
      case FILTER_PRIVATE:
        chatFilter = ChatFilter.privateFilter(tdlib);
        break;
      case FILTER_GROUPS:
        chatFilter = ChatFilter.groupsFilter(tdlib);
        break;
      case FILTER_CHANNELS:
        chatFilter = ChatFilter.channelsFilter(tdlib);
        break;
      case FILTER_BOTS:
        chatFilter = ChatFilter.botsFilter(tdlib);
        break;
      case FILTER_ARCHIVE:
      default:
        throw new IllegalArgumentException();
    }
    if (chatFilter != null) {
      chats.setArguments(new ChatsController.Arguments(chatFilter).setChatList(chatList).setIsBase(true));
    } else if (chatList != null) {
      chats.setArguments(new ChatsController.Arguments(chatList).setIsBase(true));
    }
    return chats;
  }

  private CharSequence getMenuSectionName (long pagerItemId, int pagerItemPosition, boolean hasFolders, @ChatFolderStyle int chatFolderStyle, boolean upperCase) {
    int selectedFilter = getSelectedFilter(pagerItemId);
    boolean isMain = pagerItemId == MAIN_PAGER_ITEM_ID;
    boolean isArchive = pagerItemId == ARCHIVE_PAGER_ITEM_ID;
    if (!isMain && !isArchive) {
      throw new UnsupportedOperationException();
    }
    if (hasFolders && Config.CHAT_FOLDERS_REDESIGN && selectedFilter != FILTER_NONE) {
      String source;
      if (chatFolderStyle == ChatFolderStyle.ICON_ONLY) {
        source = "∇";
      } else {
        int sourceRes = isMain ? getMainSectionNameRes(FILTER_NONE, /* hasFolders */ true) : getArchiveSectionNameRes(FILTER_NONE, chatFolderStyle);
        source = (upperCase ? Lang.getString(sourceRes).toUpperCase() : Lang.getString(sourceRes)) + " ∇";
      }
      SpannableString string = new SpannableString(source);
      // TODO(nikita-toropov) icon color
      string.setSpan(new IconSpan(R.drawable.baseline_filter_variant, ColorId.iconActive), string.length() - 1, string.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return string;
    }
    String sectionName;
    //noinspection ConstantConditions
    if (isArchive || (isMain && menuNeedArchive && (!hasFolders || pagerItemPosition > 0))) {
      sectionName = Lang.getString(getArchiveSectionNameRes(selectedFilter, chatFolderStyle));
    } else {
      sectionName = Lang.getString(getMainSectionNameRes(selectedFilter, hasFolders));
    }
    return upperCase ? sectionName.toUpperCase() : sectionName;
  }

  @StringRes
  private int getMainSectionNameRes (int selectedFilter, boolean hasFolders) {
    if (selectedFilter != FILTER_NONE) {
      return getFilterName(selectedFilter);
    }
    return hasFolders ? R.string.CategoryMain : R.string.Chats;
  }

  @StringRes
  private int getArchiveSectionNameRes (int selectedFilter, @ChatFolderStyle int chatFolderStyle) {
    if (chatFolderStyle == ChatFolderStyle.LABEL_ONLY) {
      switch (selectedFilter) {
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
        case FILTER_NONE:
        case FILTER_ARCHIVE:
          return R.string.CategoryArchive;
        default:
          throw new UnsupportedOperationException("selectedFilter=" + selectedFilter);
      }
    }
    return selectedFilter == FILTER_NONE ? R.string.CategoryArchive : getFilterName(selectedFilter);
  }

  private CharSequence getFolderSectionName (long pagerItemId, String folderName, @ChatFolderStyle int chatFolderStyle) {
    int selectedFilter = getSelectedFilter(pagerItemId);
    CharSequence sectionName;
    if (selectedFilter != FILTER_NONE) {
      if (Config.CHAT_FOLDERS_REDESIGN) {
        String source = chatFolderStyle == ChatFolderStyle.ICON_ONLY ? "∇" : folderName + " ∇";
        SpannableString string = new SpannableString(source);
        // TODO(nikita-toropov) icon color
        string.setSpan(new IconSpan(R.drawable.baseline_filter_variant, ColorId.iconActive), string.length() - 1, string.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sectionName = string;
      } else {
        String filterName = Lang.getString(getFilterName(selectedFilter));
        if (chatFolderStyle == ChatFolderStyle.LABEL_ONLY) {
          sectionName = Lang.getString(R.string.format_folderAndFilter, folderName, filterName);
        } else {
          sectionName = filterName;
        }
      }
    } else {
      sectionName = folderName;
    }
    return Emoji.instance().replaceEmoji(sectionName);
  }

  private @StringRes int getFilterName (@Filter int filter) {
    switch (filter) {
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
      case FILTER_ARCHIVE:
        return R.string.CategoryArchive;
      case FILTER_NONE:
        return 0;
      default:
        throw new UnsupportedOperationException("filter=" + filter);
    }
  }

  private void modifyNewPagerItemController (final ViewController<?> c) {
    if (c instanceof RecyclerViewProvider) {
      c.getValue();
      ((RecyclerViewProvider) c).provideRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
        private float lastY;
        private float lastShowY;

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          lastY += dy;
          if (dy < 0 && lastShowY - lastY >= Screen.getTouchSlop()) {
            showHideCompose(c, true);
            lastShowY = lastY;
          } else if (lastY - lastShowY > Screen.getTouchSlopBig()) {
            showHideCompose(c, false);
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
    final ViewController<?> c;
    long pagerItemId = getPagerItemId(position);
    if (pagerItemId == MAIN_PAGER_ITEM_ID) {
      TdApi.ChatList chatList = this.menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
      c = newChatsController(chatList, getSelectedFilter(MAIN_PAGER_ITEM_ID));
    } else if (hasFolders()) {
      TdApi.ChatList chatList = pagerChatLists.get(position);
      c = newChatsController(chatList, getSelectedFilter(pagerItemId));
    } else if (position == POSITION_CALLS) {
      c = new CallListController(this.context, tdlib);
    } else if (position == POSITION_PEOPLE) {
      c = new PeopleController(this.context, tdlib);
    } else {
      throw new IllegalArgumentException("position == " + position);
    }
    modifyNewPagerItemController(c);
    return c;
  }

  private @Nullable TdApi.ChatFolderInfo[] chatFolderInfos;
  private List<ViewPagerTopView.Item> pagerSections = Collections.emptyList();
  private List<TdApi.ChatList> pagerChatLists = Collections.emptyList();
  private final LongSparseIntArray pagerChatListFilters = new LongSparseIntArray();

  private boolean hasFolders () {
    return Settings.instance().chatFoldersEnabled() && !pagerChatLists.isEmpty();
  }

  @Override
  protected CharSequence[] getPagerSections () {
    if (hasFolders()) {
      throw new UnsupportedOperationException();
    }
    return new CharSequence[] {
      getMenuSectionName(MAIN_PAGER_ITEM_ID, /* pagerItemPosition */ 0, /* hasFolders */ false, ChatFolderStyle.LABEL_ONLY, /* upperCase */ true),
      Lang.getString(R.string.Calls).toUpperCase()/*, UI.getString(R.string.Contacts).toUpperCase()*/
    };
  }

  private List<ViewPagerTopView.Item> defaultSectionItems;
  private ViewPagerTopView.Item defaultMainItem;
  private CounterChangeListener defaultCounterListener;

  private ViewPagerTopView.Item getDefaultMainItem () {
    if (defaultMainItem == null) {
      CharSequence mainItem = getMenuSectionName(MAIN_PAGER_ITEM_ID, 0, false, ChatFolderStyle.LABEL_ONLY,  /* upperCase */ true);
      UnreadCounterColorSet unreadCounterColorSet = new UnreadCounterColorSet();
      Counter unreadCounter = new Counter.Builder()
        .textSize(12f)
        .backgroundPadding(4f)
        .outlineAffectsBackgroundSize(false)
        .colorSet(unreadCounterColorSet)
        .callback(unreadCounterCallback(MAIN_PAGER_ITEM_ID))
        .build();
      unreadCounterColorSet.setCounter(unreadCounter);
      updateCounter(ChatPosition.CHAT_LIST_MAIN, unreadCounter, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN), false);
      defaultCounterListener = new CounterChangeListener() {
        @Override
        public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
          handleCounterChange(chatList, counter, false);
        }

        @Override
        public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
          handleCounterChange(chatList, counter, true);
        }

        private void handleCounterChange (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean areMessages) {
          int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
          if (areMessages != BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES)) {
            return;
          }
          if (chatList.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
            executeOnUiThreadOptional(() -> {
              updateCounter(chatList, unreadCounter, counter, isFocused());
            });
          } else if (chatList.getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR && BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED)) {
            executeOnUiThreadOptional(() -> {
              updateCounter(ChatPosition.CHAT_LIST_MAIN, unreadCounter, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN), isFocused());
            });
          }
        }
      };
      defaultMainItem = new ViewPagerTopView.Item(mainItem, unreadCounter);
      tdlib.listeners().addTotalChatCounterListener(defaultCounterListener);
    }
    return defaultMainItem;
  }

  private List<ViewPagerTopView.Item> getDefaultSectionItems () {
    if (defaultSectionItems == null) {
      String callsItem = Lang.getString(R.string.Calls).toUpperCase();
      defaultSectionItems = Arrays.asList(
        getDefaultMainItem(),
        new ViewPagerTopView.Item(callsItem)
      );
    }
    return defaultSectionItems;
  }

  @Override
  protected List<ViewPagerTopView.Item> getPagerSectionItems () {
    return hasFolders() ? pagerSections : getDefaultSectionItems();
  }

  // Menu

  @Override
  protected int getMenuId () {
    return R.id.menu_main;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_main) {
      header.addLockButton(menu);
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, getSearchHeaderIconColorId(), getSearchBackButtonResource());
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      tdlib.checkDeadlocks(() -> runOnUiThreadOptional(() -> {
        if (isFocused()) {
          openSearchMode();
        }
      }));
      /*case R.id.menu_btn_more: {
        showMore(new int[] {
          R.id.more_btn_settings,
          R.id.more_btn_help
        }, new String[] {Lang.getString(R.string.Settings), Lang.getString(R.string.Help)}, 0);
        break;
      }*/
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else {
      super.onMenuItemPressed(id, view);
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
        out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), null, false), tdlib.maxMessageTextLength()));
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
      out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), null, false), tdlib.maxMessageTextLength()));
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
        out.add(new TdApi.InputMessagePhoto(inputFile, null, null, width, height, messageCaption, null, false));
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

          out.add(new TdApi.InputMessageVideo(inputVideo, null, null, duration, width, height, U.canStreamVideo(inputVideo), messageCaption, null, false));
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

  // Chat folders

  private void showChatListOptions (TdApi.ChatList chatList) {
    boolean isMain = TD.isChatListMain(chatList);
    boolean isFolder = TD.isChatListFolder(chatList);
    boolean isArchive = TD.isChatListArchive(chatList);
    int chatFolderId = isFolder ? ((TdApi.ChatListFolder) chatList).chatFolderId : 0;
    TdApi.ChatFolderInfo chatFolderInfo;
    String title;
    if (isMain) {
      title = Lang.getString(R.string.CategoryMain);
      chatFolderInfo = null;
    } else if (isArchive) {
      title = Lang.getString(R.string.CategoryArchive);
      chatFolderInfo = null;
    } else if (isFolder) {
      chatFolderInfo = tdlib.chatFolderInfo(chatFolderId);
      title = chatFolderInfo != null ? chatFolderInfo.title : null;
    } else {
      title = null;
      chatFolderInfo = null;
    }
    Options.Builder options = new Options.Builder();
    if (!StringUtils.isEmptyOrBlank(title)) {
      options.info(title);
    }
    if (tdlib.hasUnreadChats(chatList)) {
      options.item(new OptionItem(R.id.btn_markFolderAsRead, Lang.getString(R.string.MarkFolderAsRead), OptionColor.NORMAL, R.drawable.baseline_done_all_24));
    }
    if (isFolder) {
      options.item(new OptionItem(R.id.btn_editFolder, Lang.getString(R.string.EditFolder), OptionColor.NORMAL, R.drawable.baseline_edit_24));
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      if (chatFolderStyle == ChatFolderStyle.ICON_ONLY || chatFolderStyle == ChatFolderStyle.LABEL_AND_ICON || chatFolderStyle == ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE) {
        options.item(new OptionItem(R.id.btn_changeFolderIcon, Lang.getString(R.string.ChatFolderChangeIcon), OptionColor.NORMAL, R.drawable.baseline_image_24));
      }
      options.item(new OptionItem(R.id.btn_folderIncludeChats, Lang.getString(R.string.ChatFolderAddChats), OptionColor.NORMAL, R.drawable.baseline_add_24));
      if (chatFolderInfo != null) {
        options.item(new OptionItem(R.id.btn_shareFolder, Lang.getString(R.string.ShareFolder), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
      }
    }
    if (isMain) {
      if (!Config.RESTRICT_HIDING_MAIN_LIST) {
        options.item(new OptionItem(R.id.btn_hideFolder, Lang.getString(R.string.HideAllChats), OptionColor.NORMAL, R.drawable.baseline_eye_off_24));
      }
    } else if (isFolder || isArchive) {
      options.item(new OptionItem(R.id.btn_hideFolder, Lang.getString(R.string.HideFolder), OptionColor.NORMAL, R.drawable.baseline_eye_off_24));
    }
    if (isFolder) {
      options.item(new OptionItem(R.id.btn_removeFolder, Lang.getString(R.string.RemoveFolder), OptionColor.RED, R.drawable.baseline_delete_24));
    }
    if (options.itemCount() > 0) {
      options.item(OptionItem.SEPARATOR);
    }
    options.item(new OptionItem(R.id.btn_chatFolders, Lang.getString(R.string.EditFolders), OptionColor.NORMAL, R.drawable.baseline_edit_folders_24));
    showOptions(options.build(), (v, id) -> {
      if (id == R.id.btn_editFolder) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            EditChatFolderController controller = new EditChatFolderController(context, tdlib);
            controller.setArguments(new EditChatFolderController.Arguments(chatFolderId, chatFolder));
            navigateTo(controller);
          }
        }));
      } else if (id == R.id.btn_hideFolder) {
        tdlib.settings().setChatListEnabled(chatList, false);
        if (headerCell != null && !StringUtils.isEmptyOrBlank(title)) {
          context()
            .tooltipManager()
            .builder(headerCell.getTopView())
            .locate((targetView, outRect) -> outRect.left = outRect.right = Screen.dp(56f) - (int) targetView.getX())
            .controller(this)
            .show(tdlib, Lang.getString(R.string.HideFolderInfo, title, Lang.getString(R.string.Settings), Lang.getString(R.string.ChatFolders)))
            .hideDelayed(3500, TimeUnit.MILLISECONDS);
        }
      } else if (id == R.id.btn_folderIncludeChats) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            boolean showChatTypes = !chatFolder.isShareable;
            SelectChatsController controller = new SelectChatsController(context, tdlib);
            controller.setArguments(SelectChatsController.Arguments.includedChats(chatFolderId, chatFolder, showChatTypes));
            navigateTo(controller);
          }
        }));
      } else if (id == R.id.btn_changeFolderIcon) {
        ChatFolderIconSelector.show(this, TD.getIconName(chatFolderInfo), selectedIcon -> {
          tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, getError) -> {
            if (getError != null) {
              UI.showError(getError);
            } else {
              if (!Td.equalsTo(chatFolder.icon, selectedIcon)) {
                chatFolder.icon = selectedIcon;
                tdlib.send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (info, editError) -> {
                  if (editError != null) {
                    UI.showError(editError);
                  }
                });
              }
            }
          });
        });
      } else if (id == R.id.btn_shareFolder) {
        TdApi.ChatFolderInfo info = ObjectsCompat.requireNonNull(chatFolderInfo);
        if (info.hasMyInviteLinks) {
          tdlib.send(new TdApi.GetChatFolderInviteLinks(chatFolderId), (result, error) -> {
            if (error != null) {
              UI.showError(error);
            } else {
              runOnUiThreadOptional(() -> showChatFolderInviteLinks(chatFolderId, title, result.inviteLinks));
            }
          });
        } else {
          return onCreateChatFolderInviteLinkClick(v, chatFolderId, info.isShareable, /* chatFolderInviteLinkCount */ 0L);
        }
      } else if (id == R.id.btn_removeFolder) {
        TdApi.ChatFolderInfo info = ObjectsCompat.requireNonNull(chatFolderInfo);
        if (info.isShareable) {
          tdlib.send(new TdApi.GetChatFolderChatsToLeave(chatFolderId), (result, error) -> runOnUiThreadOptional(() -> {
            if (error != null) {
              UI.showError(error);
            } else if (result.totalCount > 0) {
              ChatFolderInviteLinkController controller = new ChatFolderInviteLinkController(context, tdlib);
              controller.setArguments(ChatFolderInviteLinkController.Arguments.deleteFolder(info, result.chatIds));
              controller.show();
            } else {
              showDeleteChatFolderConfirm(chatFolderId, info.hasMyInviteLinks);
            }
          }));
        } else {
          showDeleteChatFolderConfirm(chatFolderId, info.hasMyInviteLinks);
        }
      } else if (id == R.id.btn_chatFolders) {
        navigateTo(new SettingsFoldersController(context, tdlib));
      } else if (id == R.id.btn_markFolderAsRead) {
        tdlib.readAllChats(chatList, /* after */ null);
      }
      return true;
    });
  }

  private void showDeleteChatFolderConfirm (int chatFolderId, boolean hasMyInviteLinks) {
    tdlib.ui().showDeleteChatFolderConfirm(this, hasMyInviteLinks, () -> {
      tdlib.send(new TdApi.DeleteChatFolder(chatFolderId, null), tdlib.typedOkHandler());
    });
  }

  private void showChatFolderInviteLinks (int chatFolderId, @Nullable CharSequence title, TdApi.ChatFolderInviteLink[] inviteLinks) {
    Options.Builder options = new Options.Builder();
    if (!StringUtils.isEmptyOrBlank(title)) {
      options.info(title);
    }
    long chatFolderInviteLinkCount = inviteLinks.length;
    long chatFolderInviteLinkCountMax = tdlib.chatFolderInviteLinkCountMax();
    boolean canCreateInviteLink = chatFolderInviteLinkCount < chatFolderInviteLinkCountMax;
    int createNewLinkOptionColor = canCreateInviteLink ? OptionColor.BLUE : OptionColor.INACTIVE;
    SparseArrayCompat<TdApi.ChatFolderInviteLink> inviteLinkById = new SparseArrayCompat<>(inviteLinks.length);
    for (TdApi.ChatFolderInviteLink inviteLink : inviteLinks) {
      int id = ViewCompat.generateViewId();
      inviteLinkById.append(id, inviteLink);
      String name = StringUtils.isEmptyOrInvisible(inviteLink.name) ? StringUtils.urlWithoutProtocol(inviteLink.inviteLink) : inviteLink.name;
      options.item(new OptionItem(id, name, OptionColor.NORMAL, R.drawable.baseline_link_24));
    }
    options.item(new OptionItem(R.id.btn_createInviteLink, Lang.getString(R.string.CreateANewLink), createNewLinkOptionColor, R.drawable.baseline_add_link_24));
    PopupLayout popupLayout = showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_createInviteLink) {
        return onCreateChatFolderInviteLinkClick(view, chatFolderId, true, chatFolderInviteLinkCount);
      }
      TdApi.ChatFolderInviteLink inviteLink = ObjectsCompat.requireNonNull(inviteLinkById.get(id));
      showChatFolderInviteLinkOptions(chatFolderId, inviteLink);
      return true;
    });
    if (!canCreateInviteLink) {
      View button = popupLayout.getBoundView().findViewById(R.id.btn_createInviteLink);
      if (button instanceof TextView) {
        TextView textView = (TextView) button;
        Views.setMediumText(textView, textView.getText());
        Drawable[] drawables = textView.getCompoundDrawables();
        Drawable lock = Drawables.get(R.drawable.baseline_lock_16);
        lock.setColorFilter(Paints.getColorFilter(Theme.textAccentColor()));
        addThemeFilterListener(lock, ColorId.text);
        button.setTag(R.id.tag_tooltip_location_provider, (TooltipOverlayView.LocationProvider) (targetView, outRect) -> {
          outRect.set(lock.getBounds());
          int top = (targetView.getHeight() - outRect.height()) / 2;
          int left = Lang.rtl() ? targetView.getPaddingLeft() : targetView.getWidth() - targetView.getPaddingRight() - outRect.width();
          outRect.offsetTo(left, top);
          outRect.inset(0, -Screen.dp(10f));
        });
        if (Lang.rtl()) {
          textView.setCompoundDrawablesWithIntrinsicBounds(lock, null, drawables[2], null);
        } else {
          textView.setCompoundDrawablesWithIntrinsicBounds(drawables[0], null, lock, null);
        }
      }
    }
  }

  private boolean onCreateChatFolderInviteLinkClick (View view, int chatFolderId, boolean isChatFolderShareable, long chatFolderInviteLinkCount) {
    if (chatFolderInviteLinkCount == 0 && !isChatFolderShareable && !tdlib.canAddShareableFolder()) {
      showShareableFoldersLimitReached(view);
      return false;
    }
    if (chatFolderInviteLinkCount >= tdlib.chatFolderInviteLinkCountMax()) {
      showChatFolderInviteLinksLimitReached(view);
      return false;
    }
    createChatFolderInviteLink(chatFolderId);
    return true;
  }

  private void showShareableFoldersLimitReached (View view) {
    UI.forceVibrateError(view);
    PopupLayout popupLayout = PopupLayout.parentOf(view);
    if (tdlib.hasPremium()) {
      CharSequence message = Lang.getMarkdownString(this, R.string.ShareableFoldersLimitReached, tdlib.addedShareableChatFolderCountMax());
      popupLayout.tooltipManager().builder(view).show(tdlib, message).hideDelayed();
    } else {
      tdlib.ui().showPremiumLimitInfo(this, popupLayout.tooltipManager(), view, TdlibUi.PremiumLimit.SHAREABLE_FOLDER_COUNT);
    }
  }

  private void showChatFolderInviteLinksLimitReached (View view) {
    UI.forceVibrateError(view);
    PopupLayout popupLayout = PopupLayout.parentOf(view);
    if (tdlib.hasPremium()) {
      CharSequence message = Lang.getMarkdownString(this, R.string.ChatFolderInviteLinksLimitReached, tdlib.chatFolderInviteLinkCountMax());
      popupLayout.tooltipManager().builder(view).show(tdlib, message).hideDelayed();
    } else {
      tdlib.ui().showPremiumLimitInfo(this, popupLayout.tooltipManager(), view, TdlibUi.PremiumLimit.CHAT_FOLDER_INVITE_LINK_COUNT);
    }
  }

  private void showChatFolderInviteLinkOptions (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    String title = StringUtils.isEmptyOrInvisible(inviteLink.name) ? StringUtils.urlWithoutProtocol(inviteLink.inviteLink) : inviteLink.name;
    Options.Builder options = new Options.Builder();
    options.info(title);
    options.item(new OptionItem(R.id.btn_shareLink, Lang.getString(R.string.ShareLink), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
    options.item(new OptionItem(R.id.btn_copyLink, Lang.getString(R.string.InviteLinkCopy), OptionColor.NORMAL, R.drawable.baseline_content_copy_24));
    options.item(new OptionItem(R.id.btn_edit, Lang.getString(R.string.InviteLinkEdit), OptionColor.NORMAL, R.drawable.baseline_edit_24));
    showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_shareLink) {
        tdlib.ui().shareUrl(this, inviteLink.inviteLink);
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(inviteLink.inviteLink, R.string.CopiedLink);
      } else if (id == R.id.btn_edit) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) ->  runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
            controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, chatFolder, inviteLink));
            navigateTo(controller);
          }
        }));
      } else {
        throw new UnsupportedOperationException();
      }
      return true;
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId) {
    tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        createChatFolderInviteLink(chatFolderId, chatFolder);
      }
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId, TdApi.ChatFolder chatFolder) {
    if (TD.countIncludedChatTypes(chatFolder) > 0 || TD.countExcludedChatTypes(chatFolder) > 0) {
      runOnUiThreadOptional(() -> {
        CharSequence message = Lang.getMarkdownString(this, R.string.ChatFolderInviteLinkChatTypesNotSupported);
        if (showFolderTooltip(chatFolderId, message)) {
          View topView = headerCell != null ? headerCell.getTopView() : null;
          if (topView != null) {
            UI.forceVibrateError(topView);
          }
        } else {
          UI.showCustomToast(message, Toast.LENGTH_LONG, 0);
        }
      });
      return;
    }
    tdlib.send(new TdApi.GetChatsForChatFolderInviteLink(chatFolderId), (chats, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        createChatFolderInviteLink(chatFolderId, chatFolder, chats.chatIds);
      }
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId, TdApi.ChatFolder chatFolder, long[] shareableChatIds) {
    if (shareableChatIds.length == 0) {
      UI.showCustomToast(R.string.ChatFolderInviteLinkNoChatsToShare, Toast.LENGTH_SHORT, 0);
      return;
    }
    tdlib.createChatFolderInviteLink(chatFolderId, /* name */ "", shareableChatIds, (inviteLink, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
        controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, chatFolder, shareableChatIds, inviteLink));
        navigateTo(controller);
      }
    }));
  }

  private @Nullable ViewGroup bottomBar;
  private @Px float bottomBarOffsetByPlayer;
  private @Px float bottomBarOffsetByScroll;
  private @Px float bottomBarOffsetBySnackBar;
  private final BoolAnimator isBottomBarVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    int headerHeight = getHeaderHeight();
    int shadowHeight = Screen.dp(2f);
    bottomBarOffsetByScroll = (headerHeight + shadowHeight) * (1f - factor);
    invalidateBottomBarOffset();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, true);

  private void showBottomBar () {
    if (bottomBar != null) {
      isBottomBarVisible.setValue(true, isFocused());
    }
  }

  private void hideBottomBar () {
    if (bottomBar != null) {
      isBottomBarVisible.setValue(false, isFocused());
    }
  }

  private void invalidateBottomBarOffset () {
    if (bottomBar != null) {
      bottomBar.setTranslationY(bottomBarOffsetByPlayer + bottomBarOffsetByScroll + bottomBarOffsetBySnackBar);
    }
  }

  @Override
  protected boolean applyPlayerOffset (float factor, float top) {
    boolean result = super.applyPlayerOffset(factor, top);
    if (result) {
      bottomBarOffsetByPlayer = factor < 1f ? -top : 0;
      invalidateBottomBarOffset();
    }
    return result;
  }

  private boolean showFolderTooltip (int chatFolderId, CharSequence message) {
    return showFolderTooltip(new TdApi.ChatListFolder(chatFolderId), message);
  }

  private boolean showFolderTooltip (TdApi.ChatList chatList, CharSequence message) {
    if (headerCell == null) {
      return false;
    }
    int index = indexOfChatList(chatList);
    if (index == -1) {
      return false;
    }
    ViewPagerTopView topView = headerCell.getTopView();
    Rect itemRect = new Rect();
    if (topView == null || !topView.getItemRect(index, itemRect)) {
      return false;
    }
    int topViewX = Math.round(topView.getX());
    int topViewParentWidth = ((View) topView.getParent()).getWidth();
    int horizontalInset = Screen.dp(16f);
    itemRect.left = Math.max(itemRect.left, horizontalInset - topViewX);
    itemRect.right = Math.min(itemRect.right, topViewParentWidth - topViewX - horizontalInset);
    if (itemRect.isEmpty()) {
      return false;
    }
    context()
      .tooltipManager()
      .builder(topView)
      .locate((targetView, outRect) -> outRect.set(itemRect))
      .controller(this)
      .show(tdlib, message)
      .hideDelayed(3500, TimeUnit.MILLISECONDS);
    return true;
  }

  private int indexOfChatList (TdApi.ChatList chatList) {
    for (int i = 0; i < pagerChatLists.size(); i++) {
      if (Td.equalsTo(chatList, pagerChatLists.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private void checkTabs () {
    if (headerCell == null)
      return;
    ViewPagerHeaderViewCompact headerCellView = (ViewPagerHeaderViewCompact) headerCell.getView();
    boolean hasFolders = hasFolders();
    boolean displayTabsAtBottom = displayTabsAtBottom();
    headerCell.getTopView().setShowLabelOnActiveOnly(hasFolders && tdlib.settings().chatFolderStyle() == ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE);
    headerCell.getTopView().setUseDarkBackground(displayTabsAtBottom);
    headerCell.getTopView().setDrawSelectionAtTop(displayTabsAtBottom);
    headerCell.getTopView().setSlideOffDirection(displayTabsAtBottom ? ViewPagerTopView.SLIDE_OFF_DIRECTION_TOP : ViewPagerTopView.SLIDE_OFF_DIRECTION_BOTTOM);
    headerCell.getTopView().setItemPadding(Screen.dp(hasFolders ? ViewPagerTopView.COMPACT_ITEM_PADDING : ViewPagerTopView.DEFAULT_ITEM_PADDING));
    headerCell.getTopView().setItemSpacing(Screen.dp(hasFolders ? ViewPagerTopView.COMPACT_ITEM_SPACING : ViewPagerTopView.DEFAULT_ITEM_SPACING));
    headerCellView.setFadingEdgeLength(displayTabsAtBottom ? 0f : 16f);

    if (displayTabsAtBottom) {
      if (bottomBar == null) {
        ViewGroup parent = (ViewGroup) headerCellView.getParent();
        if (parent != null) {
          if (headerView != null && isFocused() && !inTransformMode()) {
            headerView.setTitle(this);
            View titleView = headerView.findViewById(getId());
            if (titleView != null) {
              titleView.setAlpha(1f);
              titleView.setTranslationX(0);
              titleView.setTranslationY(0);
              titleView.setVisibility(View.VISIBLE);
            }
          } else {
            parent.removeView(headerCellView);
          }
        }
        int shadowHeight = Screen.dp(3f);
        ShadowView shadowView = new ShadowView(context);
        shadowView.setVerticalShadow(new int[]{0x00000000, 0x40000000}, null, shadowHeight);
        shadowView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, shadowHeight));
        Views.setTopMargin(headerCellView, shadowHeight - Screen.dp(1f));

        ViewSupport.setThemedBackground(headerCellView, ColorId.headerLightBackground, this);
        headerCell.getTopView().setSelectionColorId(ColorId.headerLightText, .9f);
        headerCell.getTopView().setTextFromToColorId(ColorId.headerLightText, ColorId.headerLightText, PropertyId.SUBTITLE_ALPHA);

        int headerHeight = getHeaderHeight();
        bottomBar = new FrameLayoutFix(context);
        bottomBar.addView(shadowView);
        bottomBar.addView(headerCellView);
        pagerWrap.addView(bottomBar, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight + Views.getTopMargin(headerCellView), Gravity.BOTTOM));
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          isBottomBarVisible.setValue(true, false);
          invalidateBottomBarOffset();
        }
      }
      headerCellView.setAlpha(1f);
      headerCellView.setTranslationX(0f);
      headerCellView.setTranslationY(0f);
      headerCellView.setVisibility(View.VISIBLE);
      headerCellView.setScaleFactor(0f, 0f, 0f, false);
    } else {
      if (bottomBar != null) {
        pagerWrap.removeView(bottomBar);
        bottomBar.removeView(headerCellView);
        bottomBar = null;
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          isBottomBarVisible.setValue(false, false);
        }
        headerCell.getTopView().setSelectionColorId(ColorId.headerTabActive);
        headerCell.getTopView().setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
        ViewUtils.setBackground(headerCellView, null);
        Views.setTopMargin(headerCellView, 0);
        if (headerView != null && isFocused() && !inTransformMode()) {
          headerView.setTitle(this);
        }
      }
    }
  }

  private void checkMenu () {
    if (menu != null) {
      FrameLayoutFix.LayoutParams layoutParams = (FrameLayoutFix.LayoutParams) menu.getLayoutParams();
      boolean displayTabsAtBottom = displayTabsAtBottom();
      int gravity = (displayTabsAtBottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.LEFT;
      int bottomMargin = displayTabsAtBottom ? getHeaderHeight() : 0;
      if (menu.setTop(!displayTabsAtBottom)) {
        menu.setDefaultPadding();
        menu.setPadding(menu.getPaddingLeft(), menu.getPaddingTop() + Screen.dp(14f), menu.getPaddingRight(), menu.getPaddingBottom() + Screen.dp(13f));
      }
      if (gravity != layoutParams.gravity || bottomMargin != layoutParams.bottomMargin) {
        layoutParams.gravity = gravity;
        layoutParams.bottomMargin = bottomMargin;
      }
    }
  }

  private boolean displayTabsAtBottom () {
    return hasFolders() && !tdlib.settings().displayFoldersAtTop();
  }

  private void initPagerSections () {
    if (Settings.instance().chatFoldersEnabled()) {
      updatePagerSections(true);
    }
  }

  private boolean needUpdatePagerSections;

  private void updatePagerSections (boolean force) {
    if (force || isAttachedToNavigationController()) {
      updatePagerSections(tdlib.chatFolders(), tdlib.mainChatListPosition(), tdlib.settings().archiveChatListPosition());
    } else {
      needUpdatePagerSections = true;
    }
  }

  private void updatePagerSections (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition, int archiveChatListPosition) {
    boolean oldHasFolders = hasFolders();
    List<TdApi.ChatList> chatLists;
    List<ViewPagerTopView.Item> sections;
    if (chatFolders.length > 0 || tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE)) {
      int chatListCount = chatFolders.length;
      if (tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_MAIN)) {
        mainChatListPosition = MathUtils.clamp(mainChatListPosition, 0, chatListCount);
        chatListCount++;
      } else {
        mainChatListPosition = NO_POSITION;
      }
      if (tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE)) {
        archiveChatListPosition = MathUtils.clamp(archiveChatListPosition, 0, chatListCount);
        chatListCount++;
        if (mainChatListPosition >= archiveChatListPosition) {
          mainChatListPosition++;
        }
      } else {
        archiveChatListPosition = NO_POSITION;
      }
      @ChatFolderStyle int chatFolderStyle = tdlib.settings().chatFolderStyle();
      chatLists = new ArrayList<>(chatListCount);
      sections = new ArrayList<>(chatListCount);
      int chatFolderIndex = 0;
      for (int position = 0; position < chatListCount; position++) {
        TdApi.ChatList chatList;
        ViewPagerTopView.Item sectionItem;
        if (position == mainChatListPosition) {
          chatList = ChatPosition.CHAT_LIST_MAIN;
          sectionItem = buildMainSectionItem(position, chatFolderStyle);
        } else if (position == archiveChatListPosition) {
          chatList = ChatPosition.CHAT_LIST_ARCHIVE;
          sectionItem = buildArchiveSectionItem(position, chatFolderStyle);
        } else {
          TdApi.ChatFolderInfo chatFolder = chatFolders[chatFolderIndex++];
          if (!tdlib.settings().isChatFolderEnabled(chatFolder.id))
            continue;
          chatList = new TdApi.ChatListFolder(chatFolder.id);
          sectionItem = buildSectionItem(getPagerItemId(chatList), position, chatList, chatFolder, chatFolderStyle);
        }
        chatLists.add(chatList);
        sections.add(sectionItem);
      }
    } else {
      sections = Collections.emptyList();
      chatLists = Collections.emptyList();
    }
    if (chatLists.size() > 1 || (!chatLists.isEmpty() && !TD.isChatListMain(chatLists.get(0)))) {
      this.pagerSections = sections;
      this.pagerChatLists = chatLists;
      this.chatFolderInfos = chatFolders;
      if (chatListUnreadCountListener == null) {
        chatListUnreadCountListener = new ChatListUnreadCountListener();
        tdlib.listeners().addTotalChatCounterListener(chatListUnreadCountListener);
      }
    } else {
      this.pagerSections = Collections.emptyList();
      this.pagerChatLists = Collections.emptyList();
      this.chatFolderInfos = null;
      if (chatListUnreadCountListener != null) {
        tdlib.listeners().removeTotalChatCounterListener(chatListUnreadCountListener);
        chatListUnreadCountListener = null;
      }
    }
    notifyPagerItemPositionsChanged();
    ViewPager pager = getViewPager();
    if (pager != null) {
      onPageSelected(getCurrentPagerItemPosition(), pager.getCurrentItem());
    }
    if (oldHasFolders != hasFolders() && pagerWrap != null) {
      checkTabs();
      checkMenu();
      checkMargins();
    }
  }

  private ViewPagerTopView.Item buildMainSectionItem (int pagerItemPosition, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getMenuSectionName(MAIN_PAGER_ITEM_ID, pagerItemPosition, /* hasFolders */ true, chatFolderStyle, /* upperCase */ false);
    int iconResource = menuNeedArchive ? R.drawable.baseline_archive_24 : R.drawable.baseline_forum_24;
    return buildSectionItem(MAIN_PAGER_ITEM_ID, pagerItemPosition, ChatPosition.CHAT_LIST_MAIN, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildArchiveSectionItem (int pagerItemPosition, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getMenuSectionName(ARCHIVE_PAGER_ITEM_ID, pagerItemPosition, /* hasFolders */ true, chatFolderStyle, /* upperCase */ false);
    int iconResource = R.drawable.baseline_archive_24;
    return buildSectionItem(ARCHIVE_PAGER_ITEM_ID, pagerItemPosition, ChatPosition.CHAT_LIST_ARCHIVE, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildSectionItem (long pagerItemId, int pagerItemPosition, TdApi.ChatList chatList, TdApi.ChatFolderInfo chatFolderInfo, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getFolderSectionName(pagerItemId, chatFolderInfo.title, chatFolderStyle);
    int iconResource = TD.findFolderIcon(chatFolderInfo.icon, R.drawable.baseline_folder_24);
    return buildSectionItem(pagerItemId, pagerItemPosition, chatList, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildSectionItem (long pagerItemId, int pagerItemPosition, TdApi.ChatList chatList, CharSequence sectionName, @DrawableRes int iconResource, @ChatFolderStyle int chatFolderStyle) {
    int selectedFilter = getSelectedFilter(pagerItemId);
    Counter unreadCounter = buildUnreadCounter(pagerItemId, selectedFilter);
    if (unreadCounter != null) {
      TdlibCounter counter = tdlib.getCounter(chatList);
      updateCounter(chatList, unreadCounter, counter, /* animated */ false);
    }
    ViewPagerTopView.Item item;
    if (pagerItemId == MAIN_PAGER_ITEM_ID && pagerItemPosition == 0) {
      if (selectedFilter != FILTER_NONE && selectedFilter != FILTER_ARCHIVE) {
        item = new ViewPagerTopView.Item(sectionName, iconResource, unreadCounter);
      } else {
        item = new ViewPagerTopView.Item(iconResource, unreadCounter);
      }
    } else if (chatFolderStyle == ChatFolderStyle.LABEL_AND_ICON || chatFolderStyle == ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE ||
      (chatFolderStyle == ChatFolderStyle.ICON_ONLY && selectedFilter != FILTER_NONE)) {
      item = new ViewPagerTopView.Item(sectionName, iconResource, unreadCounter);
    } else if (chatFolderStyle == ChatFolderStyle.ICON_ONLY) {
      item = new ViewPagerTopView.Item(iconResource, unreadCounter);
    } else {
      item = new ViewPagerTopView.Item(sectionName, unreadCounter);
    }
    item.setMinWidth(Screen.dp(56f - ViewPagerTopView.COMPACT_ITEM_PADDING * 2));
    return item;
  }

  private @Nullable Counter buildUnreadCounter (long pagerItemId, int selectedFilter) {
    if (selectedFilter != FILTER_NONE || (pagerItemId == MAIN_PAGER_ITEM_ID && menuNeedArchive)) {
      return null;
    }
    UnreadCounterColorSet unreadCounterColorSet = new UnreadCounterColorSet();
    Counter unreadCounter = new Counter.Builder()
      .textSize(12f)
      .backgroundPadding(4f)
      .outlineAffectsBackgroundSize(false)
      .colorSet(unreadCounterColorSet)
      .callback(unreadCounterCallback(pagerItemId))
      .build();
    unreadCounterColorSet.setCounter(unreadCounter);
    return unreadCounter;
  }

  private Counter.Callback unreadCounterCallback (long pagerItemId) {
    return (counter, sizeChanged) -> {
      if (headerCell != null) {
        int position = getPagerItemPosition(pagerItemId);
        if (position != NO_POSITION) {
          ViewPagerTopView topView = headerCell.getTopView();
          if (sizeChanged) {
            topView.requestItemLayoutAt(position);
          }
          topView.invalidate();
        }
      }
    };
  }

  private void updateCounter (TdApi.ChatList chatList, Counter target, TdlibCounter counter, boolean animated) {
    int mutedCount, unmutedCount;
    int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
    boolean countMessages = BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES);
    if (countMessages) {
      mutedCount = Math.max(0, counter.messageCount);
      unmutedCount = Math.max(0, counter.messageUnmutedCount);
    } else {
      mutedCount = Math.max(0, counter.chatCount);
      unmutedCount = Math.max(0, counter.chatUnmutedCount);
    }
    if (BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED) && chatList.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
      TdlibCounter archiveCounter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      if (countMessages) {
        mutedCount += Math.max(0, archiveCounter.messageCount);
        unmutedCount += Math.max(0, archiveCounter.messageUnmutedCount);
      } else {
        mutedCount += Math.max(0, archiveCounter.chatCount);
        unmutedCount += Math.max(0, archiveCounter.chatUnmutedCount);
      }
    }
    if (BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MUTED)) {
      boolean muted = mutedCount > 0 ? unmutedCount == 0 : target.isMuted();
      target.setCount(mutedCount, muted, animated);
    } else {
      target.setCount(unmutedCount, false, animated);
    }
  }

  private class UnreadCounterColorSet implements TextColorSet {
    private @Nullable Counter counter;

    public void setCounter (@Nullable Counter counter) {
      this.counter = counter;
    }

    @Override
    public int defaultTextColor () {
      return counter != null ? ColorUtils.fromToArgb(foregroundColor(), backgroundColor(), counter.getMuteFactor()) : foregroundColor();
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      return counter != null ? ColorUtils.alphaColor(1f - counter.getMuteFactor(), backgroundColor()) : backgroundColor();
    }

    @Override
    public int outlineColor (boolean isPressed) {
      return counter != null ? backgroundColor() : Color.TRANSPARENT;
    }

    private int foregroundColor () {
      return Theme.getColor(displayTabsAtBottom() ? ColorId.headerLightBackground : ColorId.headerBackground);
    }

    private int backgroundColor() {
      return Theme.getColor(displayTabsAtBottom() ? ColorId.headerLightText : ColorId.headerText);
    }
  };

  private @Nullable ChatListPositionListener chatListPositionListener;
  private @Nullable ChatListUnreadCountListener chatListUnreadCountListener;

  private class ChatListPositionListener implements TdlibSettingsManager.ChatListPositionListener {
    @Override
    public void onArchiveChatListPositionChanged (Tdlib tdlib, int archiveChatListPosition) {
      if (tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE)) {
        updatePagerSections(false);
      }
    }

    @Override
    public void onChatListStateChanged (Tdlib tdlib, TdApi.ChatList chatList, boolean isEnabled) {
      updatePagerSections(false);
    }

    @Override
    public void onBadgeFlagsChanged (Tdlib tdlib, int newFlags) {
      if (hasFolders()) {
        updatePagerCounters();
      }
    }

    @Override
    public void onChatFolderStyleChanged (Tdlib tdlib, int newStyle) {
      onFoldersAppearanceChanged();
    }

    @Override
    public void onChatFolderOptionsChanged (Tdlib tdlib, int newOptions) {
      onFoldersAppearanceChanged();
    }
  }

  @Override
  public void onBadgeSettingsChanged () {
    if (hasFolders()) {
      updatePagerCounters();
    }
  }

  private void onFoldersAppearanceChanged () {
    if (hasFolders()) {
      updatePagerSections(false);
    }
  }

  @Override
  public void onChatFolderOptionsChanged (int newOptions) {
    onFoldersAppearanceChanged();
  }

  @Override
  public void onChatFolderStyleChanged (int newStyle) {
    onFoldersAppearanceChanged();
  }

  private class ChatListUnreadCountListener implements CounterChangeListener {
    @Override
    public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
      dispatchCounterChange(chatList, counter, false);
      /*TODO update visibility?
      if (chatList instanceof TdApi.ChatListArchive && availabilityChanged) {
        runOnUiThreadOptional(() -> {
          if (menu != null) {
            menu.getChildAt(FILTER_ARCHIVE).setVisibility(tdlib.hasArchivedChats() ? View.VISIBLE : View.GONE);
          }
        });
      }*/
    }

    @Override
    public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
      dispatchCounterChange(chatList, counter, true);
    }

    private void dispatchCounterChange (TdApi.ChatList chatList, TdlibCounter counter, boolean areMessages) {
      runOnUiThreadOptional(() -> {
        int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
        if (areMessages != BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES)) {
          return;
        }
        dispatchCounter(chatList, counter);
        if (chatList.getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR && BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED)) {
          dispatchCounter(ChatPosition.CHAT_LIST_MAIN, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN));
        }
      });
    }

    private void dispatchCounter (TdApi.ChatList chatList, TdlibCounter counter) {
      long itemId = getPagerItemId(chatList);
      int positionToUpdate = getPagerItemPosition(itemId);
      if (positionToUpdate != NO_POSITION) {
        ViewPagerTopView.Item pagerSection = pagerSections.get(positionToUpdate);
        if (pagerSection.counter != null) {
          updateCounter(chatList, pagerSection.counter, counter, isFocused());
        }
      }
    }
  }

  private void updatePagerCounters () {
    for (int i = 0; i < pagerSections.size(); i++) {
      TdApi.ChatList chatList = pagerChatLists.get(i);
      ViewPagerTopView.Item section = pagerSections.get(i);
      if (section.counter != null) {
        TdlibCounter counter = tdlib.getCounter(chatList);
        updateCounter(chatList, section.counter, counter, isFocused());
      }
    }
  }

  @Override
  public void onChatFoldersChanged (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition) {
    if (Settings.instance().chatFoldersEnabled()) {
      runOnUiThreadOptional(() ->
        updatePagerSections(false)
      );
    }
  }
}
