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
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.data.TranslationsManager;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.filegen.SimpleGenerationInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.CounterHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.StopwatchHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.DoneListener;
import org.thunderdog.challegram.util.LanguageDetector;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.DoneButton;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SliderWrapView;
import org.thunderdog.challegram.widget.ViewControllerPagerAdapter;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class ProfileController extends ViewController<ProfileController.Args> implements
  Menu,
  MoreDelegate,
  OptionDelegate,
  ViewControllerPagerAdapter.ControllerProvider,
  ViewPager.OnPageChangeListener,
  View.OnClickListener,
  MessageListener,
  ChatListener,
  NotificationSettingsListener,
  TdlibCache.UserDataChangeListener,
  TdlibCache.UserStatusChangeListener,
  TdlibCache.SecretChatDataChangeListener,
  TdlibCache.BasicGroupDataChangeListener,
  TdlibCache.SupergroupDataChangeListener,
  ComplexHeaderView.Callback,
  InviteLinkController.Callback,
  SenderPickerDelegate,
  ViewPagerTopView.OnItemClickListener,
  MediaCollectorDelegate,
  FactorAnimator.Target, ActivityResultHandler,
  DoneListener {
  // Constants

  public static final int MODE_USER = 0;
  public static final int MODE_SECRET = 1;
  public static final int MODE_GROUP = 2;
  public static final int MODE_SUPERGROUP = 3;
  public static final int MODE_CHANNEL = 4;
  public static final int MODE_EDIT_GROUP = 5;
  public static final int MODE_EDIT_SUPERGROUP = 6;
  public static final int MODE_EDIT_CHANNEL = 7;

  // Arguments

  public static class Args {
    public TdApi.Chat chat;
    public ThreadInfo threadInfo;
    public boolean isEdit;

    public Args (TdApi.Chat chat, ThreadInfo threadInfo, boolean isEdit) {
      this.chat = chat;
      this.threadInfo = threadInfo;
      this.isEdit = isEdit;
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    Args args = getArguments();
    if (args != null) {
      super.saveInstanceState(outState, keyPrefix);
      outState.putLong(keyPrefix + "chat_id", args.chat.id);
      if (args.threadInfo != null) {
        // TODO в ProfileController используется только messageThreadId, нет смысла сохранять весь ThreadInfo
        args.threadInfo.saveTo(outState, keyPrefix + "message_thread");
      }
      outState.putBoolean(keyPrefix + "is_edit", args.isEdit);
      return true;
    }
    return false;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    long chatId = in.getLong(keyPrefix + "chat_id");
    ThreadInfo threadInfo = ThreadInfo.restoreFrom(tdlib, in, keyPrefix);
    if (threadInfo == ThreadInfo.INVALID)
      return false;
    boolean isEdit = in.getBoolean(keyPrefix + "is_edit");
    TdApi.Chat chat = tdlib.chatSync(chatId);
    if (chat != null && !tdlib.hasPasscode(chat)) {
      if (isEdit) {
        switch (chat.type.getConstructor()) {
          case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
            if (tdlib.cache().basicGroupFull(ChatId.toBasicGroupId(chat.id)) == null)
              return false;
            break;
          case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
            if (tdlib.cache().supergroupFull(ChatId.toSupergroupId(chat.id)) == null)
              return false;
            break;
          default:
            return false;
        }
      }
      super.restoreInstanceState(in, keyPrefix);
      setArguments(new Args(chat, threadInfo, isEdit));
      return true;
    }
    return false;
  }

  private int mode;

  private boolean isUserMode () {
    return mode == MODE_USER || mode == MODE_SECRET;
  }

  public boolean isSameProfile (ProfileController controller) {
    return getChatId() == controller.getChatId() && mode == controller.mode;
  }

  private TdApi.Chat chat;
  private ThreadInfo messageThread;

  private TdApi.User user;
  private TdApi.SecretChat secretChat;
  private TdApi.UserFullInfo userFull;

  TdApi.BasicGroup group;
  TdApi.BasicGroupFullInfo groupFull;

  TdApi.Supergroup supergroup;
  TdApi.SupergroupFullInfo supergroupFull;

  private int inviteLinksCount = -1, inviteLinksRevokedCount = -1;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    this.chat = args.chat;
    this.messageThread = args.threadInfo;
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        this.user = tdlib.cache().userStrict(ChatId.toUserId(chat.id));
        this.mode = MODE_USER;
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        this.secretChat = tdlib.cache().secretChatStrict(ChatId.toSecretChatId(chat.id));
        this.user = tdlib.cache().userStrict(this.secretChat.userId);
        this.mode = MODE_SECRET;
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        this.group = tdlib.cache().basicGroupStrict(ChatId.toBasicGroupId(chat.id));
        this.mode = MODE_GROUP;
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        this.supergroup = tdlib.cache().supergroupStrict(ChatId.toSupergroupId(chat.id));
        this.mode = supergroup.isChannel ? MODE_CHANNEL : MODE_SUPERGROUP;
        break;
      }
    }

    if (args.isEdit) {
      switch (mode) {
        case MODE_CHANNEL:
          mode = MODE_EDIT_CHANNEL;
          break;
        case MODE_GROUP:
          mode = MODE_EDIT_GROUP;
          break;
        case MODE_SUPERGROUP:
          mode = MODE_EDIT_SUPERGROUP;
          break;
      }
      switch (mode) {
        case MODE_EDIT_CHANNEL:
        case MODE_EDIT_SUPERGROUP:
          supergroupFull = tdlib.cache().supergroupFull(supergroup.id);
          if (supergroupFull == null)
            throw new IllegalStateException("id:" + supergroup.id);
          break;
        case MODE_EDIT_GROUP:
          groupFull = tdlib.cache().basicGroupFull(group.id);
          if (groupFull == null)
            throw new IllegalStateException("id:" + group.id);
          break;
      }
    }
  }

  private void replaceWithSupergroup (long supergroupId) {
    if (isDestroyed())
      return;
    if (mode != MODE_EDIT_GROUP && mode != MODE_GROUP)
      return;
    if (isEditing()) {
      TdApi.SupergroupFullInfo supergroupFullInfo = tdlib.cache().supergroupFull(supergroupId);
      if (supergroupFullInfo == null) {
        tdlib.client().send(new TdApi.GetSupergroupFullInfo(supergroupId), result -> {
          if (result.getConstructor() == TdApi.SupergroupFullInfo.CONSTRUCTOR) {
            tdlib.ui().post(() -> replaceWithSupergroup(supergroupId));
          }
        });
        return;
      }
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromSupergroupId(supergroupId));
    if (chat == null) {
      tdlib.client().send(new TdApi.CreateSupergroupChat(supergroupId, false), result -> {
        if (result.getConstructor() == TdApi.Chat.CONSTRUCTOR) {
          tdlib.ui().post(() -> replaceWithSupergroup(supergroupId));
        }
      });
      return;
    }
    unsubscribeFromUpdates();
    this.groupFull = null;
    this.group = null;
    setArguments(new Args(chat, messageThread, getArgumentsStrict().isEdit));
    subscribeToUpdates();
    buildCells();
    updateHeader(true);
  }

  // Controller

  public ProfileController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_profile;
  }

  @Override
  protected int getMenuId () {
    switch (mode) {
      case MODE_SECRET:
        return R.id.menu_profile_secret;
      case MODE_USER:
        return TD.isBot(user) ? R.id.menu_profile_bot : user != null && (user.id == 0 || (user.type.getConstructor() != TdApi.UserTypeRegular.CONSTRUCTOR && user.type.getConstructor() != TdApi.UserTypeBot.CONSTRUCTOR)) ? 0 : R.id.menu_profile_private;
      default:
        return isEditing() ? R.id.menu_profile_manage : R.id.menu_profile;
    }
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_profile || id == R.id.menu_profile_private ||
      id == R.id.menu_profile_bot || id == R.id.menu_profile_secret ||
      id == R.id.menu_profile_manage) {
      LinearLayout realMenu = new LinearLayout(context());
      realMenu.setOrientation(LinearLayout.HORIZONTAL);
      realMenu.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));

      HeaderButton callButton = null;

      int buttonColorId = getHeaderIconColorId();

      if (mode == MODE_SECRET || mode == MODE_USER) {
        callButton = header.addButton(realMenu, R.id.menu_btn_call, R.drawable.baseline_phone_24, buttonColorId, this, Screen.dp(48f));
        callButton.setAlpha(userFull != null && (userFull.canBeCalled || userFull.hasPrivateCalls) ? 1f : 0f);
      }

      if (mode == MODE_SECRET) {
        StopwatchHeaderButton headerButton = header.addStopwatchButton(realMenu, this);
        headerButton.forceValue(tdlib.ui().getTTLShort(chat.id), tdlib.canChangeMessageAutoDeleteTime(chat.id));
        if (!headerButton.getIsVisible()) {
          callButton.setTranslationX(Screen.dp(StopwatchHeaderButton.WIDTH));
        }
      }
      if (id == R.id.menu_profile) {
        HeaderButton button = header.addButton(realMenu, R.id.menu_btn_manage, R.drawable.baseline_edit_24, buttonColorId, this, Screen.dp(49f));
        button.setVisibility(canManageChat() ? View.VISIBLE : View.GONE);

        button = header.addButton(realMenu, R.id.menu_btn_addContact, R.drawable.baseline_person_add_24, buttonColorId, this, Screen.dp(49f));
        button.setVisibility(canAddAnyKindOfMembers() ? View.VISIBLE : View.GONE);
      }
      HeaderButton moreButton = header.addMoreButton(realMenu, this, buttonColorId);
      if (id == R.id.menu_profile_manage) {
        moreButton.setVisibility(hasMoreItems() ? View.VISIBLE : View.INVISIBLE);
      }

      LinearLayout transformedMenu = new LinearLayout(context());
      transformedMenu.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
      transformedMenu.setOrientation(LinearLayout.HORIZONTAL);
      transformedMenu.setAlpha(0f);
      transformedMenu.setTranslationY(HeaderView.getSize(false));
      if (Config.USE_SECRET_SEARCH || mode != MODE_SECRET) {
        HeaderButton button = header.addSearchButton(transformedMenu, this, ColorId.icon).setThemeColorId(ColorId.icon);
        List<SharedBaseController<?>> list = getControllers();
        button.setVisibility(!list.isEmpty() && list.get(0).canSearch() ? View.VISIBLE : View.INVISIBLE);
      }

      FrameLayoutFix wrap = new FrameLayoutFix(context());
      wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      wrap.addView(realMenu);
      wrap.addView(transformedMenu);

      menu.addView(wrap);

      if (getTransformFactor() != 0f) {
        applyHeaderMenuTransform(menu, getTransformFactor());
      }
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, ColorId.textLight, getBackButtonResource());
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      showMore();
    } else if (id == R.id.menu_btn_addContact) {
      addMember(view);
    } else if (id == R.id.menu_btn_manage) {
      manageChat();
    } else if (id == R.id.menu_btn_call) {
      if (userFull != null) {
        tdlib.context().calls().makeCall(this, user.id, userFull);
      }
      /*case R.id.menu_btn_edit: {
        if (supergroupFull != null) {
          final EditChannelController channelController = new EditChannelController();
          channelController.setArguments(new EditChannelController.Arguments(chat, supergroup.id, supergroupFull, headerCell.getAvatar()));
          navigateTo(channelController);
        }
        break;
      }*/
    } else if (id == R.id.menu_btn_stopwatch) {
      tdlib.ui().showTTLPicker(context(), chat);
    } else if (id == R.id.menu_btn_search) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null && ((SharedBaseController<?>) c).canSearch()) {
        openSearchMode();
      }
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    }
  }

  @Override
  public final boolean shouldDisallowScreenshots () {
    return ChatId.isSecret(chat.id) || (chat.hasProtectedContent && !isEditing()) || super.shouldDisallowScreenshots();
  }

  @Override
  protected String getSearchStartQuery () {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      return ((SharedBaseController<?>) c).getCurrentQuery();
    }
    return null;
  }

  @Override
  protected void onSearchInputChanged (String query) {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      ((SharedBaseController<?>) c).search(query.trim());
    }
  }

  /*@Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    // TODO disable scroll maybe?
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
  }*/

  private void showMore () {
    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        showPrivateMore();
        break;
      }
      case MODE_GROUP:
      case MODE_SUPERGROUP:
      case MODE_CHANNEL: {
        showCommonMore();
        break;
      }
      case MODE_EDIT_GROUP:
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP: {
        int count = 0;
        if (canClearHistory())
          count++;
        if (canDestroyChat())
          count++;
        if (count > 0) {
          IntList ids = new IntList(count);
          StringList strings = new StringList(count);
          if (canClearHistory()) {
            ids.append(R.id.btn_clearChatHistory);
            strings.append(R.string.ClearHistory);
          }
          if (canDestroyChat()) {
            ids.append(R.id.btn_destroyChat);
            strings.append(isChannel() ? R.string.DestroyChannel : R.string.DestroyGroup);
          }
          showMore(ids.get(), strings.get(), 0);
        }
        break;
      }
    }
  }

  private void showPrivateMore () {
    if (user.id == 0 || (user.type.getConstructor() != TdApi.UserTypeRegular.CONSTRUCTOR && user.type.getConstructor() != TdApi.UserTypeBot.CONSTRUCTOR)) {
      return;
    }

    boolean isBot = user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR;
    final long myUserId = tdlib.myUserId();

    if (!isBot && user.id == myUserId && user.id != 0) {
      showMore(new int[]{R.id.more_btn_edit}, new String[]{Lang.getString(R.string.EditName)}, 0);
      return;
    }

    final long chatId = ChatId.fromUserId(user.id);
    IntList ids = new IntList(4);
    StringList strings = new StringList(4);

    if (isBot && ((TdApi.UserTypeBot) user.type).canJoinGroups) {
      ids.append(R.id.more_btn_addToGroup);
      strings.append(R.string.BotInvite);
    }

    if (mode == MODE_USER && user.id != myUserId && !TD.isBot(user)) {
      ids.append(R.id.btn_newSecretChat);
      strings.append(R.string.StartEncryptedChat);
    }

    if (isBot) {
      if (Td.hasUsername(user)) {
        ids.append(R.id.more_btn_share);
        strings.append(R.string.Share);
      }
    } else if (TD.hasPhoneNumber(user)) {
      ids.append(R.id.more_btn_share);
      strings.append(R.string.ShareContact);
    }

    //if (!isBot || !((TdApi.UserTypeBot) user.type).isInline) {
    boolean isFullyBlocked = tdlib.chatFullyBlocked(chatId);
    if (!user.isSupport || isFullyBlocked) {
      ids.append(R.id.more_btn_block);
      strings.append(isFullyBlocked ? isBot ? R.string.UnblockBot : R.string.Unblock : isBot ? R.string.BlockBot : R.string.BlockContact);
    }
    //}

    if (tdlib.canSetPasscode(chat)) {
      ids.append(R.id.btn_setPasscode);
      strings.append(R.string.PasscodeTitle);
    }

    if (!tdlib.chatFullyBlocked(chatId)) {
      ids.append(R.id.more_btn_privacy);
      strings.append(R.string.EditPrivacy);
    }

    if (TD.isContact(user)) {
      ids.append(R.id.more_btn_edit);
      strings.append(R.string.RenameContact);
      ids.append(R.id.more_btn_delete);
      strings.append(R.string.DeleteContact);
    } else if (TD.canAddContact(user)) {
      ids.append(R.id.more_btn_addToContacts);
      strings.append(R.string.AddContact);
    }

    if (mode == MODE_SECRET) {
      ids.append(R.id.more_btn_cloudChat);
      strings.append(R.string.OpenCloudChat);
    }

    showMore(ids.get(), strings.get(), 0);
  }

  private void showCommonMore () {
    IntList ids = new IntList(4);
    StringList strings = new StringList(4);

    if (BuildConfig.DEBUG) {
      ids.append(R.id.btn_recentActions);
      strings.append(R.string.EventLog);
    }

    if (canAddAnyKindOfMembers()) {
      ids.append(R.id.more_btn_addMember);
      strings.append(Lang.getString(R.string.AddMember));
    }
    if (mode == MODE_CHANNEL || mode == MODE_SUPERGROUP) {
      if (Td.hasUsername(supergroup)) {
        ids.append(R.id.more_btn_share);
        strings.append(R.string.Share);
      }
    }

    if (mode == MODE_SUPERGROUP || mode == MODE_GROUP) {
      if (!canManageChat()) {
        ids.append(R.id.more_btn_viewAdmins);
        strings.append(R.string.ViewAdmins);
      }
      if (!tdlib.chatFullyBlocked(getChatId())) {
        ids.append(R.id.more_btn_privacy);
        strings.append(R.string.EditPrivacy);
      }
    }

    if (supergroupFull != null && supergroupFull.canGetStatistics) {
      ids.append(R.id.more_btn_viewStats);
      strings.append(R.string.Stats);
    }

    tdlib.ui().addDeleteChatOptions(getChatId(), ids, strings, false, true);

    if (ids.size() > 0) {
      showMore(ids.get(), strings.get(), 0);
    }
  }

  private void openPrivacyExceptions () {
    PrivacyExceptionController c = new PrivacyExceptionController(context, tdlib);
    c.setArguments(new PrivacyExceptionController.Args(getChatId()));
    navigateTo(c);
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (tdlib.ui().processLeaveButton(this, null, getChatId(), id, null)) {
      return;
    }
    switch (mode) {
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP:
      case MODE_EDIT_GROUP: {
        if (id == R.id.btn_destroyChat) {
          destroyChat();
        } else if (id == R.id.btn_inviteLink) {
          openInviteLink();
        }
        break;
      }
      case MODE_USER:
      case MODE_SECRET: {
        if (userFull != null && !tdlib.ui().handleProfileMore(this, id, user, userFull)) {
          if (id == R.id.more_btn_cloudChat) {
            tdlib.ui().openPrivateChat(this, user.id, null);
          } else if (id == R.id.btn_newSecretChat) {
            tdlib.ui().startSecretChat(this, user.id, true, null);
          } else if (id == R.id.more_btn_privacy) {
            openPrivacyExceptions();
          } else if (id == R.id.more_btn_block) {
            final boolean needBlock = !tdlib.chatFullyBlocked(chat.id);
            final boolean isBot = tdlib.isBotChat(chat.id);
            if (needBlock) {
              showOptions(Lang.getStringBold(isBot ? R.string.BlockBotConfirm : R.string.BlockUserConfirm, tdlib.chatTitle(chat.id)), new int[] {R.id.btn_blockSender, R.id.btn_cancel}, new String[] {Lang.getString(isBot ? R.string.BlockBot : R.string.BlockContact), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_block_24, R.drawable.baseline_cancel_24}, (itemView, id1) -> {
                if (!isDestroyed() && id1 == R.id.btn_blockSender) {
                  tdlib.blockSender(tdlib.sender(chat.id), new TdApi.BlockListMain(), result -> {
                    if (TD.isOk(result)) {
                      UI.showToast(Lang.getStringBold(isBot ? R.string.BlockedBot : R.string.BlockedUser, tdlib.chatTitle(chat.id)), Toast.LENGTH_SHORT);
                    } else {
                      tdlib.okHandler().onResult(result);
                    }
                  });
                }
                return true;
              });
            } else {
              tdlib.unblockSender(tdlib.sender(chat.id), result -> {
                if (TD.isOk(result)) {
                  UI.showToast(Lang.getStringBold(isBot ? R.string.UnblockedBot : R.string.UnblockedUser, tdlib.chatTitle(chat.id)), Toast.LENGTH_SHORT);
                } else {
                  tdlib.okHandler().onResult(result);
                }
              });
            }
          } else if (id == R.id.more_btn_delete) {
            tdlib.ui().deleteContact(this, user.id);
          }
        }
        break;
      }
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
      case MODE_GROUP: {
        if (id == R.id.more_btn_addMember) {
          addMember(null);
        } else if (id == R.id.btn_recentActions) {
          openRecentActions();
        } else if (id == R.id.btn_enabledReactions) {
          openEnabledReactions();
        } else if (id == R.id.more_btn_privacy) {
          openPrivacyExceptions();
        } else if (id == R.id.more_btn_share) {
          share(true);
          return;
        } else if (id == R.id.more_btn_viewAdmins) {
          manageChat();
        } else if (id == R.id.more_btn_viewStats) {
          openStats();
        } else if (id == R.id.more_btn_editDescription) {
          editDescription();
          return;
        } else if (id == R.id.more_btn_join) {
          joinChannel();
          return;
        }
        break;
      }
    }
  }

  private String getProfileUsername () {
    return tdlib.chatUsername(chat.id);
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    if (id == R.id.btn_copyText) {
      copyDescription();
    } else if (id == R.id.btn_editUsername) {
      editChannelUsername();
    } else if (id == R.id.btn_manageInviteLinks) {
      openInviteLink();
    } else if (id == R.id.btn_manageJoinRequests) {
      openInviteRequestsManage();
    } else if (id == R.id.btn_copyUsername) {
      String username = getProfileUsername();
      if (!StringUtils.isEmpty(username)) {
        UI.copyText("@" + username, R.string.CopiedUsername);
      }
    } else if (id == R.id.btn_copyLink) {
      String username = getProfileUsername();
      if (!StringUtils.isEmpty(username)) {
        UI.copyText(tdlib.tMeUrl(username), R.string.CopiedLink);
      }
    } else if (id == R.id.btn_share) {
      if (!share(false)) {
        tdlib.ui().handleProfileOption(this, id, user);
      }
    } else {
      tdlib.ui().handleProfileOption(this, id, user);
    }
    return true;
  }

  private boolean canEditDescription () {
    switch (mode) {
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
      case MODE_GROUP:
        return canChangeInfo();
    }
    return false;
  }

  private boolean canChangeInfo () {
    return (supergroupFull != null && tdlib.canChangeInfo(chat)) || (groupFull != null && tdlib.canChangeInfo(chat));
  }

  private void editDescription () {
    EditBioController c = new EditBioController(context, tdlib);
    c.setArguments(new EditBioController.Arguments(getDescriptionValue(), chat.id).setBioChangeListener(this));
    navigateTo(c);
  }

  public void updateDescription (long chatId, String newDescription) {
    if (getChatId() == chatId) {
      if (supergroupFull != null)
        supergroupFull.description = newDescription;
      if (groupFull != null)
        groupFull.description = newDescription;
      checkDescription();
    }
  }

  private void copyDescription () {
    if (aboutWrapper != null) {
      UI.copyText(aboutWrapper.getText(), R.string.CopiedText);
    }
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (mode == MODE_SECRET) {
      tdlib.ui().updateTTLButton(R.id.menu_secretChat, headerView, chat, true);
    }
    if (headerView != null) {
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && baseAdapter != null) {
      makeChannelChecks();
    }
  }

  private void makeChannelChecks () {
    baseAdapter.updateValuedSettingById(R.id.btn_notifications);
  }

  private FrameLayoutFix contentView;

  private @Nullable
  ComplexHeaderView headerCell;
  private CustomRecyclerView baseRecyclerView;
  private SettingsAdapter baseAdapter;

  private RtlViewPager pager;
  private ViewControllerPagerAdapter pagerAdapter;

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getFloatingButtonId () {
    return isEditing() ? 0 : R.drawable.baseline_chat_bubble_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    if (mode == MODE_CHANNEL && FeatureToggles.CHANNEL_PROFILE_FLOATING_BUTTON_OPENS_DISCUSSION_GROUP) {
      if (supergroup != null && supergroup.isChannel && supergroup.hasLinkedChat) {
        long linkedChatId = supergroupFull != null ? supergroupFull.linkedChatId : 0;
        if (linkedChatId != 0) {
          tdlib.ui().openLinkedChat(this, supergroup.id, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
          return;
        }
      }
    }
    ViewController<?> c = previousStackItem();
    if (c instanceof MessagesController && c.getChatId() == chat.id) {
      navigateBack();
    } else {
      tdlib.ui().openChat(this, chat, null);
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected boolean useHeaderTranslation () {
    return !shareCustomHeaderView();
  }

  @Override
  protected int getHeaderHeight () {
    if (isEditing()) {
      return super.getHeaderHeight();
    } else {
      return (int) (Size.getHeaderPortraitSize() + Size.getHeaderSizeDifference(true) * ((ComplexRecyclerView) baseRecyclerView).getScrollFactor());
    }
  }

  @Override
  protected int getMaximumHeaderHeight () {
    return isEditing() ? super.getMaximumHeaderHeight() : Size.getHeaderBigPortraitSize(true);
  }

  @Override
  protected int getHeaderTextColorId () {
    if (isExpanded) {
      return ColorId.text;//Theme.textAccentColor();
    } else {
      return super.getHeaderTextColorId();
    }
  }

  public class ContentDecoration extends RecyclerView.ItemDecoration {
    private final SharedBaseController<?> baseController;

    public ContentDecoration (SharedBaseController<?> controller) {
      this.baseController = controller;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, RecyclerView parent, @NonNull RecyclerView.State state) {
      RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
      if (holder != null) {
        final int position = holder.getAdapterPosition();
        final int itemCount = parent.getAdapter().getItemCount();

        int top = 0;
        int bottom = 0;

        boolean isUnknown = position == -1; // || holder.getItemViewType() == SettingItem.TYPE_SMART_EMPTY || holder.getItemViewType() == SettingItem.TYPE_PROGRESS;

        if (position == 0 || isUnknown) { // Full top height
          top = contentTop();
        }

        if (needTabs() && (position == itemCount - 1 || isUnknown) && !(view instanceof ProgressComponentView || view instanceof EmptySmartView)) {
          final int itemsHeight = isUnknown ? view.getMeasuredHeight() : baseController.calculateItemsHeight() - SettingHolder.measureHeightForType(ListItem.TYPE_HEADER);
          final int parentHeight = parent.getMeasuredHeight() - getShadowBottomHeight();
          bottom = Math.max(0, parentHeight - itemsHeight - getHiddenContentHeight() - getPagerTopViewHeight());
        }

        outRect.set(0, top, 0, bottom);
      } else {
        outRect.set(0, contentTop(), 0, 0);
      }
    }
  }

  // base recycler view decorator
  public class BaseItemsDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, RecyclerView parent, @NonNull RecyclerView.State state) {
      RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
      if (holder != null && holder.getAdapterPosition() == baseAdapter.getItems().size() - 1) {

        final int parentHeight = parent.getMeasuredHeight();
        final int originalBottom = parentHeight - getPagerTopViewHeight() - getShadowBottomHeight();

        final int itemsHeight = getContentItemsHeight();

        if (!needTabs()) {
          outRect.bottom = itemsHeight - getPagerTopViewHeight();
        } else {
          int bottom = Math.max(0, (parent.getMeasuredHeight() + getPagerTopViewHeight()) - itemsHeight);
          if (bottom > 0) {
            int remainingShit = Math.max(0, parentHeight - (itemsHeight + bottom));
            outRect.bottom = originalBottom - remainingShit;
          } else {
            outRect.bottom = originalBottom;
          }
        }
      } else {
        outRect.setEmpty();
      }
    }
  }

  public ContentDecoration newContentDecoration (SharedBaseController<?> controller) {
    return new ContentDecoration(controller);
  }

  private ViewPagerHeaderViewCompact topCellView;
  private FrameLayoutFix topWrap;
  private LickView lickView;
  private ShadowView lickShadow;

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
      if (factor != 0f) {
        int height = getMeasuredHeight();
        c.drawRect(0, height - (float) height * factor, getMeasuredWidth(), height, Paints.fillingPaint(Theme.fillingColor()));
      }
    }
  }

  private float actualTopWrapY, currentTopWrapY;

  private boolean isExpanded;

  private void setExpanded (boolean isExpanded) {
    if (this.isExpanded != isExpanded && needTabs()) {
      this.isExpanded = isExpanded;
      animateExpandFactor(isExpanded ? 1f : 0f);
      if (!isExpanded && inSearchMode()) {
        closeSearchMode(null);
      }
    }
  }

  private float expandFactor;
  private FactorAnimator expandAnimator;

  private void animateExpandFactor (float factor) {
    if (expandAnimator == null) {
      expandAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 218l, expandFactor);
    }
    expandAnimator.animateTo(factor);
  }

  private boolean needMediaSubtitle;

  private void setNeedMediaSubtitle (boolean needMediaSubtitle) {
    if (this.needMediaSubtitle != needMediaSubtitle) {
      this.needMediaSubtitle = needMediaSubtitle;
      if (headerCell != null) {
        headerCell.setSubtitle(makeSubtitle(false));
        headerCell.setExpandedSubtitle(makeSubtitle(true));
      }
    }
  }

  private boolean forgetProfileMargin;

  private void setForgetProfileMargin (boolean forgetProfileMargin) {
    if (this.forgetProfileMargin != forgetProfileMargin) {
      this.forgetProfileMargin = forgetProfileMargin;
      updateHeaderMargin();
    }
  }

  private void updateHeaderMargin () {
    if (headerCell != null) {
      headerCell.setIgnoreMute(forgetProfileMargin);
      if (forgetProfileMargin) {
        ViewController<?> c = findCurrentCachedController();
        headerCell.setInnerRightMargin(c instanceof SharedBaseController && ((SharedBaseController<?>) c).canSearch() ? Screen.dp(49f) : 0);
      } else {
        headerCell.setInnerRightMargin(calculateMenuWidth());
      }
    }
  }

  private void setExpandFactor (float factor) {
    this.expandFactor = factor;

    float toY = -getTopViewTopPadding();
    float fromY = actualTopWrapY;

    float y = Math.min(actualTopWrapY, fromY + (toY - fromY) * factor);
    float headerHeight = HeaderView.getSize(true);

    lickView.setFactor(factor);
    lickShadow.setTranslationY(y - (headerHeight * expandFactor));

    float headerSize = HeaderView.getSize(true);
    float top = lickShadow.getTranslationY() + headerSize + getTopViewTopPadding();
    setTransformFactor(top > headerSize ? 0f : 1f - (top / headerSize));

    setForgetProfileMargin(top < (HeaderView.getTopOffset() + HeaderView.getSize(false) / 2f - Screen.dp(8f)));
    setNeedMediaSubtitle(top < HeaderView.getSize(true) - Screen.dp(16f));

    currentTopWrapY = y;
    setTopTranslationY(y);
  }

  @Override
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    super.applySearchTransformFactor(factor, isOpening);
    topCellView.getTopView().setDisabledFactor(factor);
    checkPagerAbilities();
    setBaseScrollDisabled(factor != 0f);
  }

  private boolean baseScrollDisabled;

  private void setBaseScrollDisabled (boolean disabled) {
    if (this.baseScrollDisabled != disabled) {
      this.baseScrollDisabled = disabled;
      // TODO maybe
    }
  }

  @Override
  protected int getHeaderIconColorId () {
    return isTransformed() && isExpanded ? ColorId.icon : headerCell != null && !headerCell.isCollapsed() ? ColorId.white : ColorId.headerIcon;
  }

  /*@Override
  protected int getHeaderTextColor () {
    return isTransformed() ? Theme.textAccentColor() : super.getHeaderTextColor();
  }

  @Override
  protected boolean allowTransformedHeaderSharing () {
    return false;
  }*/

  @Override
  protected int getHeaderColorId () {
    return isTransformed() ? ColorId.filling : super.getHeaderColorId();
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  protected void drawTransform (Canvas c, float transformFactor, int width, int bottom) {
    float height = HeaderView.getSize(true);
    c.drawRect(0, height - height * transformFactor, width, bottom, Paints.fillingPaint(Theme.fillingColor()));
  }

  @Override
  protected void applyTransformChanges () {
    if (headerView != null) {
      headerView.getFilling().setColor(Theme.fillingColor());
    }
  }

  @Override
  protected void clearTransformChanges () {
    if (headerView != null) {
      headerView.getFilling().setColor(Theme.headerColor());
    }
  }

  @Override
  protected boolean useDropShadow () {
    return !isTransformed();
  }

  @Override
  protected boolean useDropPlayer () {
    return !isEditing();
  }

  // private static final ColorChanger changer = new ColorChanger(0xffffffff, 0xff7d858f);


  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerCell != null) {
      headerCell.setTextColor(ColorUtils.fromToArgb(Theme.headerTextColor(), Theme.textAccentColor(), getTransformFactor()));
    }
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f), Theme.getColor(ColorId.icon), getTransformFactor()));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  @Override
  protected void applyStaticTransform (float factor) {
    if (headerView != null) {
      float realFactor = distanceFactor(factor);
      updateButtonsColor();
      headerView.getFilling().setShadowAlpha(realFactor == 0f ? 1f : 0f);
      final int textColor = ColorUtils.fromToArgb(Theme.headerTextColor(), Theme.textAccentColor(), realFactor);
      if (isEditing()) {
        headerView.updateTextTitleColor(getId(), textColor);
      } else {
        if (headerCell != null) {
          headerCell.setTextColor(textColor);
          headerCell.setMuteFadeFactor(factor);
        }
      }
    }
  }

  /*@Override
  protected void updateCustomButtonColorFilter (View view, int menuId, int colorFilter) {
    if (menuId == getMenuId()) {
      FrameLayoutFix wrapper = (FrameLayoutFix) view;
      LinearLayout transformedMenu = (LinearLayout) wrapper.getChildAt(1);

      final int childCount = transformedMenu.getChildCount();
      final int color = Theme.getColor(ColorId.icon);
      for (int i = 0; i < childCount; i++) {
        View chidlView = transformedMenu.getChildAt(i);
        if (chidlView != null && chidlView instanceof HeaderButton) {
          ((HeaderButton) chidlView).setColorFilter(color);
        }
      }
    }
  }*/

  private static float distanceFactor (float factor) {
    float headerSize = HeaderView.getSize(true);
    float realDistance = headerSize * factor;
    float padding = Screen.dp(4f);
    return Math.max(0f, (realDistance - padding)) / (HeaderView.getSize(false) - padding);
  }

  @Override
  protected void applyHeaderMenuTransform (LinearLayout menu, float factor) {
    FrameLayoutFix wrapper = (FrameLayoutFix) menu.getChildAt(0);
    LinearLayout realMenu = (LinearLayout) wrapper.getChildAt(0);
    LinearLayout transformedMenu = (LinearLayout) wrapper.getChildAt(1);

    float headerHeight = HeaderView.getSize(false);

    float accelerateFactor = AnimatorUtils.ACCELERATE_INTERPOLATOR.getInterpolation(factor);

    float realFactor = distanceFactor(factor);
    realMenu.setTranslationY(-headerHeight * realFactor);
    realMenu.setAlpha(1f - accelerateFactor);

    for (int i = 0; i < realMenu.getChildCount(); i++) {
      View childView = realMenu.getChildAt(i);
      if (childView instanceof HeaderButton) {
        ((HeaderButton) childView).setThemeColorId(ColorId.headerIcon, ColorId.white, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f);
      }
    }

    transformedMenu.setTranslationY(headerHeight - headerHeight * factor);
    transformedMenu.setAlpha(accelerateFactor);
  }

  private void setTopTranslationY (float y) {
    if (topWrap.getTranslationY() != y) {
      topWrap.setTranslationY(y);
      float headerHeight = HeaderView.getSize(true);
      lickView.setTranslationY(y - headerHeight + getTopViewTopPadding());
      lickShadow.setTranslationY(y - (headerHeight * expandFactor));
      if (getSearchTransformFactor() != 0f) {
        View editText = getSearchHeaderView(headerView).view();
        editText.setTranslationY(Math.max(0f, lickShadow.getTranslationY() - HeaderView.getSize(false)));
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setExpandFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
  }

  /*protected int getTopSearchOffset () {
    return (int) ((float) -getPagerTopViewHeight() * getSearchTransformFactor());
  }*/

  private void checkTopViewPosition () {
    int totalHeight = getTopItemsHeight();
    int scrollY = calculateBaseScrollY();

    int diff = totalHeight - scrollY;
    int bound = getPagerTopViewHeight() + getTopViewTopPadding();
    int offset = -getPagerTopViewHeight() - getTopViewTopPadding() - Screen.dp(6f);
    int top;

    top = diff + offset;

    float remaining = top + getTopViewTopPadding();
    actualTopWrapY = top;

    setExpanded(remaining <= bound);

    if ((!isExpanded && (expandAnimator == null || !expandAnimator.isAnimating())) || actualTopWrapY < topWrap.getTranslationY()) {
      currentTopWrapY = top;
      setTopTranslationY(top);
    }
  }

  private boolean eventsBelongToSlider;
  private int touchingMode = TOUCH_MODE_NONE;

  private static final int TOUCH_MODE_NONE = -1;
  private static final int TOUCH_MODE_BASE_RECYCLER = 0;
  private static final int TOUCH_MODE_PAGER = 1;
  private static final int TOUCH_MODE_SLIDER = 2;
  private static final int TOUCH_MODE_CONTENT_RECYCLER = 3;

  private static final boolean USE_DOUBLED_EVENTS = true;

  private boolean scrollingMembers;
  private boolean disallowBaseIntercept;

  private static int getTopViewTopPadding () {
    return Screen.dp(6f);
  }

  private int baseScrollState = RecyclerView.SCROLL_STATE_IDLE;

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context) {
      private boolean isIntercepting;

      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        switch (ev.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            isIntercepting = true;
            float x = ev.getX();
            float y = ev.getY();

            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
              View view = getChildAt(i);
              if (view instanceof CircleButton && view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f) {
                float viewLeft = view.getLeft() + view.getTranslationX();
                float viewTop = view.getTop() + view.getTranslationY();

                if (x >= viewLeft && x < viewLeft + view.getMeasuredWidth() && y >= viewTop && y < viewTop + view.getMeasuredHeight()) {
                  isIntercepting = false;
                  return false;
                }
              }
            }

            break;
          }
        }
        return isIntercepting;
      }

      private float startX, startY;

      private int lastHeight;

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int nowHeight = getMeasuredHeight();
        if (lastHeight != 0 && nowHeight != lastHeight) {
          onGlobalHeightChanged();
        }
        lastHeight = nowHeight;
      }

      private float lastY;

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        float y = e.getY();
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            startX = e.getX();
            startY = y;

            float pagerTop = topWrap.getTranslationY() + getTopViewTopPadding();
            eventsBelongToSlider = false;

            /*stopCachedScrolls();
            baseRecyclerView.stopScroll();*/

            if (startY >= pagerTop && startY <= pagerTop + getPagerTopViewHeight() && topWrap != null && topWrap.getParent() != null) {
              touchingMode = TOUCH_MODE_SLIDER;
              eventsBelongToSlider = true;
              setEventsBelongToPager(true);
            } else if (currentPositionOffset == 0f && belongsToBaseRecycler(getMeasuredWidth(), startY) && calculateBaseScrollY() < maxItemsScrollY()) {
              touchingMode = TOUCH_MODE_BASE_RECYCLER;
              setEventsBelongToPager(false);
            } else {
              touchingMode = TOUCH_MODE_PAGER;
              setEventsBelongToPager(true);
            }
            break;
          }
        }
        switch (touchingMode) {
          case TOUCH_MODE_BASE_RECYCLER: {
            baseRecyclerView.dispatchTouchEvent(e);
            if (USE_DOUBLED_EVENTS && !scrollingMembers && !disallowBaseIntercept) {
              dispatchPagerRecyclerEvent(e);
              /*if (isContentFrozen()) {
                checkContentScrollY(findCurrentCachedController());
              }*/
              if (e.getAction() == MotionEvent.ACTION_MOVE && y < lastY && calculateBaseScrollY() == maxItemsScrollY()) {
                touchingMode = TOUCH_MODE_CONTENT_RECYCLER;
                baseRecyclerView.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), e.getMetaState()));
                baseRecyclerView.stopScroll();
                setEventsBelongToPager(true);
              }
            }
            break;
          }
          case TOUCH_MODE_CONTENT_RECYCLER: {
            dispatchPagerRecyclerEvent(e);
            break;
          }
          case TOUCH_MODE_PAGER: {
            pager.dispatchTouchEvent(e);
            break;
          }
          case TOUCH_MODE_SLIDER: {
            if (!scrollingMembers) {
              if (eventsBelongToPager) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                  pager.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), e.getMetaState()));
                } else {
                  pager.dispatchTouchEvent(e);
                }
              } else {
                dispatchPagerRecyclerEvent(e);
              }
            }
            e.offsetLocation(0, -topWrap.getTranslationY());
            if (!eventsBelongToSlider) {
              touchingMode = TOUCH_MODE_PAGER;
              topWrap.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_CANCEL, e.getX(), e.getY(), e.getMetaState()));
            } else {
              topWrap.dispatchTouchEvent(e);
            }
            break;
          }
        }
        switch (e.getAction()) {
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL: {
            touchingMode = TOUCH_MODE_NONE;
            break;
          }
        }
        lastY = y;
        return true;
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.background, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // ViewPager with shared media
    pager = new RtlViewPager(context);
    pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(this);
    pager.setAdapter(pagerAdapter = new ViewControllerPagerAdapter(this));
    pager.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(pager);

    // Base recycler that's on top and not related to shared media

    if (isEditing()) {
      baseRecyclerView = new CustomRecyclerView(context) {
        @Override
        public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
          super.requestDisallowInterceptTouchEvent(disallowIntercept);
          setDisallowBaseIntercept(disallowIntercept);
        }
      };
    } else {
      // Header cell
      headerCell = new ComplexHeaderView(context, tdlib, this);
      headerCell.setAvatarExpandListener((headerView1, expandFactor1, byCollapse, allowanceFactor, collapseFactor) -> updateButtonsColor());
      headerCell.setInnerRightMarginStart(Screen.dp(49f));
      headerCell.setInnerMargins(Screen.dp(56f), calculateMenuWidth());
      headerCell.setAllowEmptyClick();
      headerCell.initWithController(this, false);
      headerCell.setPhotoOpenCallback(this);

      baseRecyclerView = new ComplexRecyclerView(context, this) {
        @Override
        public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
          super.requestDisallowInterceptTouchEvent(disallowIntercept);
          setDisallowBaseIntercept(disallowIntercept);
        }
      };
      ((ComplexRecyclerView) baseRecyclerView).setHeaderView(headerCell, this);
    }
    baseRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      private boolean oneShot;

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (oneShot || (oneShot = (touchingMode == TOUCH_MODE_BASE_RECYCLER && baseScrollState != RecyclerView.SCROLL_STATE_SETTLING))) {
          checkContentScrollY(findCurrentCachedController()); // FIXME actually, need to fix it in a better way
        }
        checkTopViewPosition();
        if (dy != 0) {
          eventsBelongToSlider = false;
        }
        if (isEditing() && touchingMode == TOUCH_MODE_NONE && prevState == RecyclerView.SCROLL_STATE_IDLE && baseScrollState == RecyclerView.SCROLL_STATE_SETTLING) {
          scrollPagerRecyclerBy(dy);
        } else if ((!USE_DOUBLED_EVENTS || scrollingMembers || disallowBaseIntercept) && dy != 0 && (touchingMode == TOUCH_MODE_BASE_RECYCLER)) {
          scrollPagerRecyclerBy(dy);
        }
      }

      private int prevState = RecyclerView.SCROLL_STATE_IDLE;

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          Keyboard.hide(getLockFocusView());
          hideSoftwareKeyboard();
        }
        prevState = baseScrollState;
        baseScrollState = newState;
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          // stopCachedScrolls();
        }
        checkContentFrozen();
      }
    });
    baseRecyclerView.setHasFixedSize(true);
    baseRecyclerView.addItemDecoration(new BaseItemsDecoration());
    baseRecyclerView.setItemAnimator(null);
    baseRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    baseRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    baseRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(baseRecyclerView);

    // Expanded topView
    lickView = new LickView(context);
    addThemeInvalidateListener(lickView);
    lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getSize(true)));

    topWrap = new FrameLayoutFix(context);
    topWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getPagerTopViewHeight() + getTopViewTopPadding() + Screen.dp(6f)));

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getPagerTopViewHeight());
    params.topMargin = getTopViewTopPadding();

    topCellView = new ViewPagerHeaderViewCompact(context);
    ViewSupport.setThemedBackground(topCellView, ColorId.filling, this);
    ViewPagerTopView topView = topCellView.getTopView();
    topView.setUseDarkBackground();
    /*if (!UI.isTablet) {
      topView.setFitsParentWidth(true);
    }*/
    topView.setOnItemClickListener(this);
    topView.setSelectionColorId(ColorId.profileSectionActive);
    topView.setTextFromToColorId(ColorId.textLight, ColorId.profileSectionActiveContent);
    addThemeInvalidateListener(topView);
    if (Config.USE_ICON_TABS) {
      // topView.setItems(getPagerIcons());
    } else {
      topView.setItems(getPagerTitles());
    }
    topCellView.getRecyclerView().setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topCellView.setLayoutParams(params);
    topWrap.addView(topCellView);
    addThemeInvalidateListener(topView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f));
    params.topMargin = getPagerTopViewHeight() + getTopViewTopPadding();

    ShadowView shadowView = new ShadowView(context);
    addThemeInvalidateListener(shadowView);
    shadowView.setSimpleBottomTransparentShadow(false);
    shadowView.setLayoutParams(params);
    topWrap.addView(shadowView);

    /**/

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getTopViewTopPadding());

    lickShadow = new ShadowView(context);
    addThemeInvalidateListener(lickShadow);
    lickShadow.setSimpleTopShadow(true);
    lickShadow.setLayoutParams(params);

    if (needTabs()) {
      contentView.addView(topWrap);
      contentView.addView(lickView);
      contentView.addView(lickShadow);
    }

    // Base adapter
    baseAdapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setEnabled(false);
        chatView.setChat((TGFoundChat) item.getData());
      }

      @Override
      protected void setSliderValues (ListItem item, SliderWrapView view) {
        super.setSliderValues(item, view);
        view.setShowOnlyValue((item.getId() == R.id.btn_slowMode || item.getId() == R.id.btn_chatTtl));
      }

      @Override
      protected void onSliderValueChanged (ListItem item, SliderWrapView view, int value, int oldValue) {
        if (item.getId() == R.id.btn_slowMode) {
          slowModeDescItem.setString(getSlowModeDescription(TdConstants.SLOW_MODE_OPTIONS[value]));
          baseAdapter.updateValuedSetting(slowModeDescItem);
          onItemsHeightProbablyChanged();
          checkDoneButton();
        } else if (item.getId() == R.id.btn_chatTtl) {
          ttlDescItem.setString(getTtlDescription(TdConstants.CHAT_TTL_OPTIONS[value], isChannel()));
          baseAdapter.updateValuedSetting(ttlDescItem);
          onItemsHeightProbablyChanged();
          checkDoneButton();
        }
      }

      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        if (item.getId() == R.id.title) {
          AvatarView view = (AvatarView) parent.getChildAt(1);
          view.setChat(tdlib, chat);
        }
        if (isEditing()) {
          modifyEditTextImpl(item, parent, editText);
        }
      }

      @Override
      protected void setShadowVisibility (ListItem item, ShadowView view) {
        final int itemId = item.getId();
        if (itemId == R.id.shadowTop || itemId == R.id.shadowBottom) {
          view.setAlpha(0f);
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getViewType() == ListItem.TYPE_INFO_MULTILINE) {
          view.setAllowMultiLineName(item.getId() == R.id.btn_username);
        }
        final int itemId = item.getId();
        if (itemId == R.id.btn_useExplicitDice) {
          view.getToggler().setRadioEnabled(Settings.instance().getNewSetting(item.getLongId()), isUpdate);
        } else if (itemId == R.id.btn_username) {
          view.setName(getUsernameName());
          view.setData(getUsernameData());
        } else if (itemId == R.id.btn_phone) {
          if (tdlib.isSelfUserId(user.id) && Settings.instance().needHidePhoneNumber()) {
            view.setData(Strings.replaceNumbers(Strings.formatPhone(user.phoneNumber)));
          } else if (!StringUtils.isEmpty(user.phoneNumber)) {
            view.setData(Strings.formatPhone(user.phoneNumber));
          } else {
            view.setData(R.string.PhoneNumberUnknown);
          }
        } else if (itemId == R.id.btn_notifications) {
          TdApi.Chat cachedChat = tdlib.chat(chat.id);
          view.setIcon(tdlib.ui().getIconForSetting(cachedChat));
          tdlib.ui().setValueForSetting(view, chat.id);
        } else if (itemId == R.id.btn_groupsInCommon) {
          view.setData(Lang.plural(R.string.xGroups, userFull.groupInCommonCount));
        } else if (itemId == R.id.btn_encryptionKey) {
          view.setData(R.string.PictureAndText);
        } else if (itemId == R.id.btn_description) {
          view.setText(aboutWrapper);
          if (canEditDescription() && !hasDescription()) {
            view.setName(R.string.TapToSetup);
          } else {
            view.setName(isUserMode() && !TD.isBot(user) ? R.string.UserBio : R.string.Description);
          }
        } else if (itemId == R.id.btn_manageInviteLinks) {
          if (inviteLinksCount == -1) {
            view.setData(Lang.getString(R.string.LoadingInformation));
          } else if (inviteLinksRevokedCount > 0) {
            view.setData(Lang.getString(R.string.format_activeAndRevokedLinks, Lang.pluralBold(R.string.xActiveLinks, inviteLinksCount), Lang.pluralBold(R.string.xRevokedLinks, inviteLinksRevokedCount)));
          } else {
            view.setData(Lang.pluralBold(R.string.xActiveLinks, inviteLinksCount));
          }
        } else if (itemId == R.id.btn_manageJoinRequests) {
          view.setData(Lang.pluralBold(R.string.xJoinRequests, chat.pendingJoinRequests != null ? chat.pendingJoinRequests.totalCount : 0));
        } else if (itemId == R.id.btn_inviteLink) {
          TdApi.ChatInviteLink inviteLink;
          switch (mode) {
            case MODE_GROUP: {
              inviteLink = groupFull != null ? groupFull.inviteLink : null;
              break;
            }
            case MODE_SUPERGROUP:
            case MODE_CHANNEL: {
              inviteLink = supergroupFull != null ? supergroupFull.inviteLink : null;
              break;
            }
            default: {
              inviteLink = null;
              break;
            }
          }
          if (inviteLink == null) {
            view.setData(R.string.TapToSetup);
          } else {
            view.setData(StringUtils.urlWithoutProtocol(inviteLink.inviteLink));
          }
          /*case R.id.btn_members: {
            switch (mode) {
              case MODE_GROUP: {
                view.setData(Lang.pluralMembers(group.memberCount, tdlib.cache().getGroupMemberOnlineCount(group.id, true), false));
                break;
              }
              case MODE_SUPERGROUP:
              case MODE_CHANNEL: {
                if (supergroupFull != null) {
                  StringBuilder b = new StringBuilder();
                  b.append(Lang.plural(R.string.xMembers, supergroupFull.memberCount));
                  if (supergroupFull.administratorCount > 0) {
                    b.append(", ");
                    b.append(Lang.plural(R.string.xAdmins, supergroupFull.administratorCount));
                  }
                  if (supergroupFull.bannedCount > 0 && isPublicGroup()) {
                    b.append(", ");
                    b.append(Lang.plural(R.string.xBanned, supergroupFull.bannedCount));
                  }
                  view.setData(b.toString());
                } else {
                  view.setData(R.string.LoadingInformation);
                }
                break;
              }
            }
            break;
          }*/

          // EDIT stuff
        } else if (itemId == R.id.btn_channelType) {
          switch (mode) {
            case MODE_EDIT_SUPERGROUP:
            case MODE_EDIT_CHANNEL: {
              if (Td.isEmpty(supergroup.usernames)) {
                // view.setName(supergroup.isChannel ? R.string.ChannelType : R.string.GroupType);
                view.setData(supergroup.isChannel ? R.string.ChannelLinkSet : R.string.GroupLinkSet);
              } else {
                // view.setName(supergroup.isChannel ? R.string.ChannelLink : R.string.GroupLink);
                view.setData(tdlib.tMeUrl(supergroup.usernames, true));
              }
              break;
            }
            case MODE_EDIT_GROUP: {
              // view.setName(R.string.GroupType);
              view.setData(R.string.GroupLinkSet);
              break;
            }
          }
        } else if (itemId == R.id.btn_linkedChat) {
          switch (mode) {
            case MODE_EDIT_SUPERGROUP:
            case MODE_EDIT_CHANNEL: {
              if (supergroup.hasLinkedChat) {
                TdApi.Chat chat = supergroupFull != null && supergroupFull.linkedChatId != 0 ? tdlib.chat(supergroupFull.linkedChatId) : null;
                String username = chat != null ? tdlib.chatUsername(chat.id) : null;
                view.setData(chat != null ? !StringUtils.isEmpty(username) ? tdlib.tMeHost() + username : tdlib.chatTitle(chat) : Lang.getString(R.string.LoadingInformation));
              } else {
                view.setData(supergroup.isChannel ? R.string.LinkGroup : R.string.LinkChannel);
              }
              break;
            }
            case MODE_EDIT_GROUP: {
              view.setData(R.string.LinkChannel);
              break;
            }
          }
        } else if (itemId == R.id.btn_prehistoryMode) {
          switch (mode) {
            case MODE_EDIT_SUPERGROUP:
              view.setData(supergroupFull != null ? (supergroupFull.isAllHistoryAvailable ? R.string.ChatHistoryVisible : R.string.ChatHistoryHidden) : R.string.LoadingInformation);
              break;
            case MODE_EDIT_GROUP:
              view.setData(R.string.ChatHistoryHidden);
              break;
          }
        } else if (itemId == R.id.btn_chatPermissions) {
          view.setData(Lang.plural(R.string.xPermissions, Td.count(chat.permissions), TdConstants.CHAT_PERMISSIONS_COUNT));
        } else if (itemId == R.id.btn_enabledReactions) {
          TdApi.ChatAvailableReactions availableReactions = chat.availableReactions;
          switch (availableReactions.getConstructor()) {
            case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR:
              view.setData(R.string.ReactionsAllowAll);
              break;
            case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
              int count = ((TdApi.ChatAvailableReactionsSome) availableReactions).reactions.length;
              if (count > 0) {
                view.setData(Lang.pluralBold(R.string.ReactionsAllowSome, count));
              } else {
                view.setData(R.string.ReactionsAllowNone);
              }
              break;
            }
          }
        } else if (itemId == R.id.btn_toggleSignatures || itemId == R.id.btn_toggleProtection || itemId == R.id.btn_toggleJoinByRequest || itemId == R.id.btn_toggleAggressiveAntiSpam || itemId == R.id.btn_toggleHideMembers) {
          view.getToggler().setRadioEnabled(item.isSelected(), isUpdate);
        }
        if (item.getViewType() == ListItem.TYPE_RADIO_SETTING) {
          boolean isLocked = item.getId() == R.id.btn_toggleProtection && !tdlib.canToggleContentProtection(chat.id);
          view.setIgnoreEnabled(true);
          view.setVisuallyEnabled(!isLocked, isUpdate);
          view.getToggler().setShowLock(isLocked);
        }
      }
    };
    topCellView.getRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        scrollingMembers = newState == RecyclerView.SCROLL_STATE_DRAGGING && topCellView.canScrollInAnyDirection();
      }
    });
    buildCells();
    baseRecyclerView.setAdapter(baseAdapter);

    // Binding data
    if (headerCell != null) {
      headerCell.setShowLock(mode == MODE_SECRET);
      setHeaderMute(false);
      setHeaderText();
      setHeaderPhoto(false);
      if (mode == MODE_SECRET) {
        tdlib.ui().updateTTLButton(getMenuId(), headerView, chat, true);
      }
    }

    subscribeToUpdates();

    if (!isEditing()) {
      // For app_notification "Unmutes in"
      tdlib.refreshChatState(chat.id);

      switch (mode) {
        case MODE_USER:
        case MODE_SECRET: {
          processUserFull(tdlib.cache().userFull(user.id));
          break;
        }
        case MODE_GROUP: {
          processGroupFull(tdlib.cache().basicGroupFull(group.id));
          break;
        }
        case MODE_SUPERGROUP:
        case MODE_CHANNEL: {
          processChannelFull(tdlib.cache().supergroupFull(supergroup.id));
          break;
        }
      }
    }

    return contentView;
  }

  private CharSequence getUsernameName () {
    TdApi.Usernames usernames = tdlib.chatUsernames(chat);
    if (usernames != null && Td.hasUsername(usernames)) {
      if (usernames.activeUsernames.length > 1) {
        List<TdApi.TextEntity> entities = new ArrayList<>();

        StringBuilder b = new StringBuilder();
        boolean isUserChat = tdlib.isUserChat(chat);
        for (int i = 1; i < usernames.activeUsernames.length; i++) {
          if (b.length() == 0) {
            b.append(Lang.getString(isUserChat ? R.string.MultiUsernamePrefix : R.string.MultiLinkPrefix));
            if (b.length() > 0) {
              b.append(" ");
            }
          } else {
            b.append(Lang.getConcatSeparator());
          }
          String activeUsername = usernames.activeUsernames[i];
          int start = b.length();
          b.append(isUserChat ? "@" : "/").append(activeUsername);
          int end = b.length();
          entities.add(new TdApi.TextEntity(start, end - start, /*TODO isUserChat ? new TdApi.TextEntityTypeMention() :*/ new TdApi.TextEntityTypeTextUrl(tdlib.tMeUrl(activeUsername))));
        }

        TdApi.FormattedText formattedText = new TdApi.FormattedText(b.toString(), entities.toArray(new TdApi.TextEntity[0]));

        return TD.toCharSequence(formattedText);
      } else if (!tdlib.isUserChat(chat) || tdlib.isBotChat(chat)) {
        return tdlib.tMeUrl(usernames, true);
      }
    }
    return Lang.getString(R.string.Username);
  }

  private String getUsernameData () {
    TdApi.Usernames usernames = tdlib.chatUsernames(chat);
    if (usernames != null && Td.hasUsername(usernames)) {
      if (tdlib.isUserChat(chat)) { // Bots + Users: @username
        return "@" + Td.primaryUsername(usernames);
      } else { // Otherwise: /username
        return "/" + Td.primaryUsername(usernames);
      }
    } else {
      return "";
    }
  }

  private int calculateMenuWidth () {
    int totalWidth = 0;
    if (mode == MODE_SECRET || mode == MODE_USER) {
      totalWidth += Screen.dp(48f);
    }
    if (mode == MODE_SECRET && tdlib.canChangeMessageAutoDeleteTime(chat.id)) {
      totalWidth += Screen.dp(StopwatchHeaderButton.WIDTH);
    }
    final int menuId = getMenuId();
    if (menuId == R.id.menu_profile) {
      if (canManageChat())
        totalWidth += Screen.dp(49f);
      if (canAddAnyKindOfMembers())
        totalWidth += Screen.dp(49f);
      totalWidth += Screen.dp(49f);
    } else if (menuId == R.id.menu_profile_manage) {
      if (hasMoreItems()) {
        totalWidth += Screen.dp(49f);
      }
    } else {
      totalWidth += Screen.dp(49f);
    }
    return totalWidth;
  }

  @Override
  protected void attachNavigationController (NavigationController navigationController) {
    super.attachNavigationController(navigationController);
    if (!isEditing()) {
      ((ComplexRecyclerView) baseRecyclerView).setFloatingButton(navigationController.getFloatingButton());
    }
  }

  @Override
  protected void detachNavigationController () {
    super.detachNavigationController();
    if (!isEditing()) {
      ((ComplexRecyclerView) baseRecyclerView).setFloatingButton(null);
    }
  }

  private boolean needTabs () {
    switch (mode) {
      case MODE_EDIT_GROUP:
        return TD.isCreator(group.status);
      case MODE_EDIT_SUPERGROUP:
        return TD.isAdmin(supergroup.status);
    }
    return true;
  }

  @Override
  public void destroy () {
    super.destroy();
    unsubscribeFromUpdates();
    pagerAdapter.performDestroy();
    Views.destroyRecyclerView(baseRecyclerView);
  }

  private void checkPagerAbilities () {
    boolean isEnabled = getSearchTransformFactor() == 0f;
    if (isEnabled) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        isEnabled = !((SharedBaseController<?>) c).isInMediaSelectMode();
      }
    }
    pager.setPagingEnabled(isEnabled);
    topCellView.getTopView().setTouchDisabled(!isEnabled);
  }

  /*@Override
  protected void onLeaveSearchMode () {
    clearSearchInput();
  }*/

  //

  private void setDisallowBaseIntercept (boolean disallowIntercept) {
    if (disallowBaseIntercept != disallowIntercept) {
      disallowBaseIntercept = disallowIntercept;
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).getRecyclerView().requestDisallowInterceptTouchEvent(disallowIntercept);
      }
    }
  }

  private boolean inSelectMode;
  private FrameLayoutFix pseudoHeaderWrap;
  private CounterHeaderView counterView;
  private ImageView counterDismiss;
  private HeaderButton deleteButton, shareButton, copyButton, clearButton, viewButton;

  public void setInMediaSelectMode (boolean inSelectMode, @StringRes int suffixRes) {
    if (this.inSelectMode != inSelectMode) {
      this.inSelectMode = inSelectMode;
      checkPagerAbilities();

      if (counterView == null) {
        FrameLayoutFix.LayoutParams params;

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, getPagerTopViewHeight());
        params.topMargin = getTopViewTopPadding();

        pseudoHeaderWrap = new FrameLayoutFix(context());
        pseudoHeaderWrap.setLayoutParams(params);

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, getPagerTopViewHeight(), Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);


        counterView = new CounterHeaderView(context());
        counterView.initDefault(16f, ColorId.text);
        int digitWidth = counterView.getMaxDigitWidth();
        counterView.setPadding(digitWidth, 0, digitWidth, 0);
        params.rightMargin = params.leftMargin = Screen.dp(48f + 18f) - digitWidth;
        counterView.setLayoutParams(params);
        counterView.setTextTop(Screen.dp(15f) + Screen.dp(15f));
        counterView.setSuffix(Lang.plural(R.string.SelectedSuffix, 1), false);
        addThemeInvalidateListener(counterView);

        params = FrameLayoutFix.newParams(Screen.dp(56f), getPagerTopViewHeight(), Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
        counterDismiss = new ImageView(context());
        counterDismiss.setImageResource(R.drawable.baseline_close_24);
        counterDismiss.setColorFilter(Theme.iconColor());
        addThemeFilterListener(counterDismiss, ColorId.icon);
        counterDismiss.setScaleType(ImageView.ScaleType.CENTER);
        counterDismiss.setLayoutParams(params);
        counterDismiss.setOnClickListener(v -> {
          ViewController<?> c = findCurrentCachedController();
          if (c != null) {
            ((SharedBaseController<?>) c).setInMediaSelectMode(false);
          }
        });
        Views.setClickable(counterDismiss);
        RippleSupport.setTransparentSelector(counterDismiss);

        pseudoHeaderWrap.addView(counterView);
        pseudoHeaderWrap.addView(counterDismiss);

        for (int i = 0; i < 5; i++) {
          HeaderButton button = new HeaderButton(context());
          button.setThemeColorId(ColorId.icon);
          button.setAlpha(0f);
          params = FrameLayoutFix.newParams(Screen.dp(48f), getPagerTopViewHeight(), Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
          if (Lang.rtl()) {
            params.leftMargin = Screen.dp(12f);
          } else {
            params.rightMargin = Screen.dp(12f);
          }
          addThemeFilterListener(button, ColorId.icon);
          button.setLayoutParams(params);
          pseudoHeaderWrap.addView(button);
          button.setOnClickListener(this);
          RippleSupport.setSimpleWhiteBackground(button);
          switch (i) {
            case 0: {
              shareButton = button;
              button.setId(R.id.menu_btn_forward);
              button.setImageResource(R.drawable.baseline_forward_24);
              break;
            }
            case 1: {
              copyButton = button;
              button.setId(R.id.menu_btn_copy);
              button.setImageResource(R.drawable.baseline_content_copy_24);
              break;
            }
            case 2: {
              deleteButton = button;
              button.setId(R.id.menu_btn_delete);
              button.setImageResource(R.drawable.baseline_delete_24);
              break;
            }
            case 3: {
              clearButton = button;
              button.setId(R.id.menu_btn_clear);
              button.setImageResource(R.drawable.templarian_baseline_broom_24);
              break;
            }
            case 4: {
              viewButton = button;
              button.setId(R.id.menu_btn_view);
              button.setImageResource(R.drawable.baseline_visibility_24);
              break;
            }
          }
        }
        __setButtonsTranslation(0f);
        topWrap.addView(pseudoHeaderWrap);
      }
      counterView.setEnabled(inSelectMode);
      if (inSelectMode) {
        counterView.setSuffix(Lang.plural(suffixRes, 1), selectFactor != 0f);
        counterView.initCounter(1, selectFactor != 0f);
      }

      // asdf
      // TODO prepare view
    }
  }

  public void setSelectedMediaCount (int count, @StringRes int pluralRes) {
    if (counterView != null) {
      if (count > 0) {
        counterView.setSuffix(Lang.plural(pluralRes, count), selectFactor > 0f);
      }
      counterView.setCounter(count);

    }
  }

  private float selectFactor;

  // private boolean canCopyItems, canDeleteItems, canShareItems, canViewItems;

  public void updateItemsAbility (boolean canCopyItems, boolean canDeleteItems, boolean canShareItems, boolean canClearItems, boolean canViewItems) {
    int offsetX = 0;
    float factor = Lang.rtl() ? 1f : -1f;
    if (shareButton != null) {
      shareButton.setAlpha((canShareItems ? 1f : 0f));
      offsetX += canShareItems ? Screen.dp(48f) : 0;
    }
    if (copyButton != null) {
      copyButton.setAlpha(canCopyItems ? 1f : 0f);
      copyButton.setTranslationX(offsetX * factor);
      offsetX += canCopyItems ? Screen.dp(48f) : 0;
    }
    if (deleteButton != null) {
      deleteButton.setAlpha(canDeleteItems ? 1f : 0f);
      deleteButton.setTranslationX(offsetX * factor);
      offsetX += canDeleteItems ? Screen.dp(48f) : 0;
    }
    if (clearButton != null) {
      clearButton.setAlpha(canClearItems ? 1f : 0f);
      clearButton.setTranslationX(offsetX * factor);
      offsetX += canClearItems ? Screen.dp(48f) : 0;
    }
    if (viewButton != null) {
      viewButton.setAlpha(canViewItems ? 1f : 0f);
      viewButton.setTranslationX(offsetX * factor);
      offsetX += canViewItems ? Screen.dp(48f) : 0;
    }
  }

  private void __setButtonsTranslation (float factor) {
    float translation = (float) getPagerTopViewHeight() * (1f - factor);
    if (counterView != null) {
      counterView.setTranslationY(translation);
      counterDismiss.setTranslationY(translation);
      copyButton.setTranslationY(translation);
      shareButton.setTranslationY(translation);
      deleteButton.setTranslationY(translation);
      viewButton.setTranslationY(translation);
      clearButton.setTranslationY(translation);
    }
  }

  public void setSelectFactor (float factor) {
    if (this.selectFactor != factor) {
      this.selectFactor = factor;
      topCellView.getTopView().setOverlayFactor(factor);
      __setButtonsTranslation(factor);
    }
  }

  // Base adapter stuff

  private int baseHeaderItemCount;

  private void buildCells () {
    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        buildUserCells();
        break;
      }
      case MODE_SUPERGROUP:
      case MODE_GROUP: {
        buildGroupCells();
        break;
      }
      case MODE_CHANNEL: {
        buildChannelCells();
        break;
      }

      case MODE_EDIT_GROUP:
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP: {
        buildEditCells();
        break;
      }
    }
  }

  // User cells

  private TextWrapper aboutWrapper;
  private TdApi.FormattedText currentAbout;

  private static int getTextWidth (int width) {
    return Math.max(0, width - Screen.dp(73f) - Screen.dp(17f));
  }

  private boolean setDescription (String text) {
    TdApi.TextEntity[] entities = Td.findEntities(text, (e) -> !Td.isBotCommand(e.type));
    return setDescription(new TdApi.FormattedText(text, entities));
  }

  private boolean setDescription (TdApi.FormattedText text) {
    if (Td.isEmpty(text)) {
      if (canEditDescription()) {
        text = TD.toFormattedText(Lang.getString(R.string.Description), false);
      } else {
        text = null;
      }
    }
    if (this.currentAbout == null || !Td.equalsTo(this.currentAbout, text)) {
      currentAbout = text;
      if (text != null) {
        // TODO: custom emoji support
        aboutWrapper = new TextWrapper(tdlib, text, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, new TdlibUi.UrlOpenParameters().sourceChat(getChatId()), null);
        aboutWrapper.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS | (Lang.rtl() ? Text.FLAG_ALIGN_RIGHT : 0));
        aboutWrapper.prepare(getTextWidth(Screen.currentWidth()));
      } else {
        aboutWrapper = null;
      }
      return true;
    }
    return false;
  }

  /*private SettingItem newMembersListItem () {
    return new SettingItem(SettingItem.TYPE_MEMBERS_LIST, R.id.membersList, 0, 0);
  }*/

  private ListItem newInviteLinkItem () {
    return new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, R.drawable.baseline_link_24, R.string.PrimaryInviteLinkMenu);
  }

  private ListItem newNotificationItem () {
    return new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_notifications, R.drawable.baseline_notifications_24, R.string.Notifications);
  }

  private ListItem newUsernameItem () {
    if (isEditing()) {
      return null;
    }
    TdApi.Usernames usernames = tdlib.chatUsernames(chat);
    if (usernames == null || !Td.hasUsername(usernames)) {
      return null;
    }
    if (!tdlib.isUserChat(chat) || tdlib.isBotChat(chat)) {
      String url = tdlib.tMeUrl(usernames, true);
      return new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_username, R.drawable.baseline_alternate_email_24, url, false);
    } else {
      return new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_username, R.drawable.baseline_alternate_email_24, R.string.Username);
    }
  }

  private ListItem newPhoneItem () {
    return new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_phone, R.drawable.baseline_phone_24, R.string.PhoneMobile);
  }

  private ListItem newDescriptionItem () {
    return new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_description, R.drawable.baseline_info_24, isUserMode() && !TD.isBot(user) ? R.string.UserBio : R.string.Description);
  }

  private ListItem newEncryptionKeyItem () {
    return new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_encryptionKey, R.drawable.baseline_vpn_key_24, R.string.EncryptionKey);
  }

  private boolean needPhoneCell () {
    return user.isContact || user.isMutualContact || TD.hasPhoneNumber(user);
  }

  private void buildUserCells () {
    ArrayList<ListItem> items = new ArrayList<>(15);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));

    int addedCount = 0;
    if (Td.hasUsername(user)) {
      final ListItem usernameItem = newUsernameItem();
      if (usernameItem != null) {
        items.add(usernameItem);
        addedCount++;
      }
    }

    if (TD.isBot(user)) {
      if (userFull != null && (!Td.isEmpty(userFull.bio) || (userFull.botInfo != null && !StringUtils.isEmpty(userFull.botInfo.shortDescription)))) {
        items.add(newDescriptionItem());
        addedCount++;
      }
    } else {
      if (needPhoneCell()) {
        if (addedCount > 0) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(newPhoneItem());
        addedCount++;
      }
      if (mode == MODE_SECRET && TD.hasEncryptionKey(secretChat)) {
        if (addedCount > 0) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        items.add(newEncryptionKeyItem());
        addedCount++;
      }
    }

    this.baseHeaderItemCount = addedCount;
    boolean isMe = tdlib.isSelfUserId(user.id);

    if (addedCount != 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (!isMe) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      }
    }

    if (!isMe) {
      items.add(newNotificationItem());
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    addMediaItems(items);

    baseAdapter.setItems(items, false);
  }

  @WorkerThread
  private void prepareFullCells (final TdApi.UserFullInfo userFull) {
    setDescription();
  }

  private void addFullCells (TdApi.UserFullInfo userFull) {
    checkDescription();
    checkGroupsInCommon();

    /*if (userFull.commonChatCount > 0) {
      int index = baseAdapter.indexOfViewById(R.id.btn_notifications);
      if (index != -1) {
        baseAdapter.getItems().add(++index, new SettingItem(SettingItem.TYPE_SEPARATOR));
        baseAdapter.getItems().add(index + 1, newCommonGroupsCell());
        baseAdapter.notifyItemRangeInserted(index, 2);
        onItemsHeightProbablyChanged();
      }
    }*/

    checkUserButtons();
  }

  private void checkUserButtons () {
    if (headerView != null) {
      headerView.updateButtonAlpha(getMenuId(), R.id.menu_btn_call, userFull.canBeCalled || userFull.hasPrivateCalls ? 1f : 0f);
    }
  }

  private SharedChatsController getGroupsController () {
    ArrayList<SharedBaseController<?>> controllers = getControllers();
    final int count = controllers.size();
    for (int i = count - 1; i >= 0; i--) {
      SharedBaseController<?> c = controllers.get(i);
      if (c instanceof SharedChatsController) {
        return (SharedChatsController) c;
      }
    }
    return null;
  }

  private SharedMembersController getMembersController () {
    ArrayList<SharedBaseController<?>> controllers = getControllers();
    final int count = controllers.size();
    for (int i = count - 1; i >= 0; i--) {
      SharedBaseController<?> c = controllers.get(i);
      if (c instanceof SharedMembersController) {
        TdApi.SupergroupMembersFilter filter = ((SharedMembersController) c).getSpecificFilter();
        if (filter != null && filter.getConstructor() == TdApi.SupergroupMembersFilterRecent.CONSTRUCTOR) {
          return (SharedMembersController) c;
        }
      }
    }
    return null;
  }

  private void checkGroupsInCommon () {
    final boolean needGroups = userFull != null && userFull.groupInCommonCount > 0;
    final SharedChatsController existingGroupsController = getGroupsController();
    boolean hasGroups = existingGroupsController != null;
    if (needGroups != hasGroups) {
      if (needGroups) {
        SharedChatsController c = new SharedChatsController(context, tdlib);
        registerController(c);
        controllers.add(c);
        pagerAdapter.notifyItemInserted(controllers.size() - 1);
        if (Config.USE_ICON_TABS) {
          // topCellView.getTopView().addItem(c.getIcon());
        } else {
          topCellView.getTopView().addItem(c.getName().toString().toUpperCase());
        }
      } else {
        int i = controllers.indexOf(existingGroupsController);
        if (i == -1)
          return;
        ViewController<?> c = controllers.remove(i);
        pagerAdapter.notifyItemRemoved(i);
        unregisterController(c);
        topCellView.getTopView().removeItemAt(i);
        c.destroy();
      }
      pagerAdapter.notifyDataSetChanged();
    }
  }

  private void checkPhone () {
    int index = baseAdapter.indexOfViewById(R.id.btn_phone);
    boolean hadPhone = index != -1;
    boolean hasPhone = needPhoneCell();
    if (hadPhone != hasPhone) {
      if (hadPhone) {
        removeTopItem(index);
      } else {
        index = 0;
        if (baseAdapter.indexOfViewById(R.id.btn_username) != -1) {
          index++;
        }
        if (baseAdapter.indexOfViewById(R.id.btn_description) != -1) {
          index++;
        }
        addTopItem(newPhoneItem(), index); // after username, if exists
      }
    } else if (hasPhone) {
      updateValuedItem(R.id.btn_phone);
    }
  }

  // Any

  private void updateValuedItem (int id) {
    if (baseRecyclerView.getItemAnimator() != null && false) {
      baseAdapter.updateItemById(id);
    } else {
      baseAdapter.updateValuedSettingById(id);
    }
    if (id == R.id.btn_description) {
      onItemsHeightProbablyChanged(); // height probably changed
    }
  }

  private void addTopItem (ListItem item, int index) {
    index = Math.min(baseHeaderItemCount, index);
    if (baseHeaderItemCount == 0) {
      baseAdapter.getItems().add(1, item);
      baseAdapter.getItems().add(2, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      baseAdapter.getItems().add(3, new ListItem(ListItem.TYPE_SHADOW_TOP));
      baseAdapter.notifyItemRangeInserted(1, 3);
    } else if (index == 0) {
      baseAdapter.getItems().add(1, item);
      baseAdapter.getItems().add(2, new ListItem(item.getViewType() == ListItem.TYPE_RADIO_SETTING ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SEPARATOR));
      baseAdapter.notifyItemRangeInserted(1, 2);
    } else {
      int targetIndex = 1 + Math.max(0, index * 2 - 1);
      baseAdapter.getItems().add(targetIndex, new ListItem(item.getViewType() == ListItem.TYPE_RADIO_SETTING ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SEPARATOR));
      baseAdapter.getItems().add(targetIndex + 1, item);
      baseAdapter.notifyItemRangeInserted(targetIndex, 2);
    }
    baseHeaderItemCount++;
    onItemsHeightProbablyChanged();
  }

  private void removeTopItem (int index) {
    if (index == -1) {
      return;
    }
    baseHeaderItemCount--;
    ListItem removedItem = baseAdapter.getItems().remove(index); // item itself
    if (baseHeaderItemCount == 0) {
      baseAdapter.getItems().remove(index); // shadow bottom
      baseAdapter.getItems().remove(index); // shadow top
      baseAdapter.notifyItemRangeRemoved(index, 3);
    } else if (index == 1) {
      baseAdapter.getItems().remove(index); // bottom separator
      baseAdapter.notifyItemRangeRemoved(index, 2);
    } else {
      baseAdapter.getItems().remove(index - 1); // top separator
      baseAdapter.notifyItemRangeRemoved(index - 1, 2);

      index--;
      if (index >= 0 && index < baseAdapter.getItems().size()) {
        boolean useFullSeparator = removedItem.getViewType() == ListItem.TYPE_MEMBERS_LIST;
        ListItem nextItem = baseAdapter.getItems().get(index);
        int viewType = nextItem.getViewType();
        int desiredViewType = useFullSeparator ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SEPARATOR;
        if (viewType != desiredViewType && (viewType == ListItem.TYPE_SEPARATOR || viewType == ListItem.TYPE_SEPARATOR_FULL)) {
          nextItem.setViewType(desiredViewType);
          baseAdapter.notifyItemChanged(index);
        }
      }
    }
    onItemsHeightProbablyChanged();
  }

  private void checkChannelMembers () {
    final boolean needMembers = supergroupFull != null && supergroupFull.canGetMembers;
    final SharedMembersController existingMembersController = getMembersController();
    final boolean hasMembers = (existingMembersController != null);
    if (needMembers != hasMembers) {
      if (needMembers) {
        SharedMembersController c = new SharedMembersController(context, tdlib)
          .setSpecificFilter(new TdApi.SupergroupMembersFilterRecent());
        controllers.add(c);
        registerController(c);
        pagerAdapter.notifyItemInserted(controllers.size() - 1);
        if (Config.USE_ICON_TABS) {
          // topCellView.getTopView().addItem(c.getIcon());
        } else {
          topCellView.getTopView().addItem(c.getName().toString().toUpperCase());
        }
      } else {
        int i = controllers.indexOf(existingMembersController);
        if (i == -1) {
          return;
        }
        ViewController<?> c = controllers.remove(i);
        unregisterController(c);
        topCellView.getTopView().removeLastItem();
        pagerAdapter.notifyItemRemoved(i);
        c.destroy();
      }
      pagerAdapter.notifyDataSetChanged();
    }
  }

  private void removeMembersItemIfNeeded () {
    if (mode == MODE_SUPERGROUP) {
      removeTopItem(baseAdapter.indexOfViewById(R.id.membersList));
    }
  }

  private void addInviteLink () {
    int index = baseAdapter.indexOfViewById(R.id.btn_inviteLink);
    if (index == -1) {
      addTopItem(newInviteLinkItem(), baseHeaderItemCount);
    }
  }

  private void removeInviteLink () {
    removeTopItem(baseAdapter.indexOfViewById(R.id.btn_inviteLink));
  }

  private String getDescriptionValue () {
    if (userFull != null) {
      return !Td.isEmpty(userFull.bio) ? userFull.bio.text : userFull.botInfo != null && !StringUtils.isEmpty(userFull.botInfo.shortDescription) ? userFull.botInfo.shortDescription : "";
    }
    if (supergroupFull != null) {
      return !StringUtils.isEmpty(supergroupFull.description) ? supergroupFull.description : "";
    }
    if (groupFull != null) {
      return !StringUtils.isEmpty(groupFull.description) ? groupFull.description : "";
    }
    return null;
  }

  private boolean hasDescription () {
    return !StringUtils.isEmpty(getDescriptionValue());
  }

  private boolean setDescription () {
    if (userFull != null) {
      if (!Td.isEmpty(userFull.bio)) {
        return setDescription(userFull.bio);
      }
      if (userFull.botInfo != null) {
        return setDescription(userFull.botInfo.shortDescription);
      }
      return setDescription("");
    }
    if (supergroupFull != null) {
      return setDescription(supergroupFull.description);
    }
    if (groupFull != null) {
      return setDescription(groupFull.description);
    }
    return false;
  }

  private void checkDescription () {
    if (isEditing())
      return;
    int index = baseAdapter.indexOfViewById(R.id.btn_description);
    boolean hadDescription = index != -1;
    boolean hasDescription = hasDescription() || canEditDescription();
    if (hadDescription != hasDescription) {
      if (hadDescription) {
        removeTopItem(index);
      } else {
        ListItem descriptionItem = newDescriptionItem();
        setDescription();
        addTopItem(descriptionItem, baseAdapter.indexOfViewById(R.id.btn_username) != -1 ? 1 : 0);
      }
    } else if (hasDescription) {
      if (setDescription()) {
        updateValuedItem(R.id.btn_description);
      }
    }
  }

  private ListItem newExplicitDiceItem () {
    return new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useExplicitDice, 0, R.string.UseExplicitDice).setLongId(Settings.SETTING_FLAG_EXPLICIT_DICE);
  }

  private int testerLevel = -1;

  private void checkEasterEggs () {
    if (mode != MODE_CHANNEL)
      return;
    long chatId = getChatId();
    if (chatId == Tdlib.TRENDING_STICKERS_CHAT_ID && Config.EXPLICIT_DICE_AVAILABLE) {
      int index = baseAdapter.indexOfViewById(R.id.btn_useExplicitDice);
      boolean hasEasterEgg = isMember() && testerLevel >= Tdlib.TESTER_LEVEL_READER;
      boolean hadEasterEgg = index != -1;
      if (hadEasterEgg != hasEasterEgg) {
        if (hadEasterEgg) {
          removeTopItem(index);
        } else {
          addTopItem(newExplicitDiceItem(), baseAdapter.indexOfViewById(R.id.btn_username) != -1 ? 1 : 0);
        }
      }
      if (isMember() && testerLevel == -1) {
        testerLevel = Tdlib.TESTER_LEVEL_NONE;
        tdlib.getTesterLevel(newTesterLevel -> {
          if (!isDestroyed()) {
            testerLevel = newTesterLevel;
            checkEasterEggs();
          }
        });
      }
    }
  }

  private void checkUsername () {
    TdApi.Usernames usernames;
    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        usernames = user.usernames;
        break;
      }
      case MODE_CHANNEL:
      case MODE_SUPERGROUP: {
        usernames = supergroup.usernames;
        break;
      }
      default: {
        return;
      }
    }
    int index = baseAdapter.indexOfViewById(R.id.btn_username);
    boolean hadUsername = index != -1;
    boolean hasUsername = Td.hasUsername(usernames);
    if (hadUsername != hasUsername) {
      if (hadUsername) {
        removeTopItem(index);
        switch (mode) {
          case MODE_SUPERGROUP: {
            if (tdlib.canCreateInviteLink(chat)) {
              addInviteLink();
            }
            setHeaderText();
            break;
          }
          case MODE_CHANNEL: {
            if (tdlib.canCreateInviteLink(chat)) {
              addInviteLink();
            }
            break;
          }
        }
      } else {
        ListItem usernameItem = newUsernameItem();
        if (usernameItem != null) {
          addTopItem(usernameItem, 0);

          switch (mode) {
            case MODE_SUPERGROUP: {
              removeMembersItemIfNeeded();
              if (tdlib.canCreateInviteLink(chat)) {
                removeInviteLink();
              }
              setHeaderText();
              break;
            }
            case MODE_CHANNEL: {
              if (tdlib.canCreateInviteLink(chat)) {
                removeInviteLink();
              }
              break;
            }
          }
        }
      }
      checkCanToggleJoinByRequest();
    } else if (hasUsername) {
      updateValuedItem(R.id.btn_username);
    }
  }

  private void checkCanToggleJoinByRequest () {
    int existingPosition = baseAdapter.indexOfViewById(R.id.btn_toggleJoinByRequest);
    boolean couldToggle = existingPosition != -1;
    boolean canToggle = tdlib.canToggleJoinByRequest(chat);
    if (couldToggle != canToggle) {
      if (canToggle) {
        int bestIndex = baseAdapter.indexOfViewById(R.id.btn_channelType);
        if (bestIndex == -1) {
          return;
        }
        ListItem shadowItem = null;
        while (++bestIndex < baseAdapter.getItems().size()) {
          ListItem item = baseAdapter.getItem(bestIndex);
          if (item != null && item.getViewType() == ListItem.TYPE_SHADOW_BOTTOM) {
            shadowItem = item;
            break;
          }
        }
        if (shadowItem == null) {
          return;
        }
        baseAdapter.addItems(bestIndex + 1,
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleJoinByRequest, 0, R.string.ApproveNewMembers, supergroup != null && supergroup.joinByRequest),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
          new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ApproveNewMembersInfo)
        );
      } else {
        baseAdapter.removeRange(existingPosition - 1, 4);
      }
    } else if (canToggle) {
      baseAdapter.updateValuedSettingByPosition(existingPosition);
    }
  }

  private void checkPrehistory () {
    int index = baseAdapter.indexOfViewById(R.id.btn_prehistoryMode);
    boolean hadPrehistory = index != -1;
    boolean hasPrehistory = tdlib.canToggleAllHistory(chat);
    if (hadPrehistory != hasPrehistory) {
      if (hasPrehistory) {
        int recentIndex = baseAdapter.indexOfViewById(R.id.btn_recentActions);
        if (recentIndex == -1) {
          recentIndex = baseAdapter.indexOfViewById(R.id.belowRecentActions);
        }
        if (recentIndex == -1)
          throw new AssertionError();
        baseAdapter.getItems().add(recentIndex, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        baseAdapter.getItems().add(recentIndex, new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_prehistoryMode, 0, R.string.ChatHistory));
        baseAdapter.notifyItemRangeInserted(recentIndex, 2);
      } else {
        baseAdapter.removeRange(index - 1, 2);
      }
      onItemsHeightProbablyChanged();
    } else if (hasPrehistory) {
      updateValuedItem(R.id.btn_prehistoryMode);
    }
  }

  private void checkEncryptionKey () {
    int index = baseAdapter.indexOfViewById(R.id.btn_encryptionKey);
    boolean hadKey = index != -1;
    boolean hasKey = TD.hasEncryptionKey(secretChat);

    if (hadKey != hasKey) {
      if (hasKey) {
        addTopItem(newEncryptionKeyItem(), 3);
      } else {
        removeTopItem(index);
      }
    }
  }

  // Group

  private boolean isPublicGroup () {
    return (mode == MODE_SUPERGROUP || mode == MODE_EDIT_SUPERGROUP) && Td.hasUsername(supergroup);
  }

  private boolean isPublicChannel () {
    return (mode == MODE_CHANNEL || mode == MODE_EDIT_CHANNEL) && Td.hasUsername(supergroup);
  }

  private boolean canManageChat () {
    if (tdlib.canChangeInfo(chat)) {
      return true;
    }
    switch (mode) {
      /*case MODE_GROUP:
        return TD.isAdmin(group.status);*/
      case MODE_CHANNEL:
        return TD.isAdmin(supergroup.status);
      case MODE_SUPERGROUP: // ((TdApi.ChatMember) null).joinedChatDate
        return TD.isAdmin(supergroup.status);
    }
    return false;
  }

  @Override
  public long getChatId () {
    return chat != null ? chat.id : 0;
  }

  @Override
  public int getRootColorId () {
    return ColorId.background;
  }

  private void buildGroupCells () { // MODE_GROUP, MODE_SUPERGROUP
    final boolean isPublic = isPublicGroup();

    ArrayList<ListItem> items = new ArrayList<>(20);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));

    int addedCount = 0;

    if (isPublic) {
      ListItem usernameItem = newUsernameItem();
      if (usernameItem != null) {
        items.add(usernameItem);
        addedCount++;
      }
    }

    if (tdlib.canCreateInviteLink(chat) && !isPublic) {
      if (addedCount > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(newInviteLinkItem());
      addedCount++;
    }

    if (addedCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    baseHeaderItemCount = addedCount;

    if (addedCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }
    items.add(newNotificationItem());
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    addMediaItems(items);

    baseAdapter.setItems(items, false);
  }

  @WorkerThread
  private void prepareFullCells (TdApi.BasicGroupFullInfo groupFull) {
    setDescription();
  }

  private void addFullCells (TdApi.BasicGroupFullInfo groupFull) {
    this.groupFull = groupFull;

    checkDescription();

    if (TD.isCreator(group.status)) {
      updateValuedItem(R.id.btn_inviteLink);
    }
  }

  @WorkerThread
  private void prepareSupergroupCells (TdApi.SupergroupFullInfo groupFull) {
    if (!groupFull.description.isEmpty()) {
      setDescription(groupFull.description);
    }
  }

  private void addFullSupergroupCells (TdApi.SupergroupFullInfo channelFull) {
    this.supergroupFull = channelFull;

    checkDescription();
    checkManage();

    if (TD.isCreator(supergroup.status)) {
      updateValuedItem(R.id.btn_inviteLink);
    }

    updateHeader(false);
  }

  // Channel cells

  private void buildChannelCells () {
    boolean isPublic = isPublicChannel();
    ArrayList<ListItem> items = new ArrayList<>(20);

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));

    int addedCount = 0;

    if (isPublic) {
      ListItem usernameItem = newUsernameItem();
      if (usernameItem != null) {
        items.add(usernameItem);
        addedCount++;
      }
    }

    /*if (canManageChat()) {
      if (addedCount > 0) {
        items.add(new SettingItem(SettingItem.TYPE_SEPARATOR));
      }
      items.add(newManageItem());
      addedCount++;
    }*/

    if (!isPublic && tdlib.canCreateInviteLink(chat)) {
      if (addedCount > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(newInviteLinkItem());
      addedCount++;
    }

    if (addedCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    baseHeaderItemCount = addedCount;

    if (addedCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }
    items.add(newNotificationItem());
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    addMediaItems(items);

    baseAdapter.setItems(items, false);
    checkEasterEggs();
  }

  private void addMediaItems (ArrayList<ListItem> items) {
    if (needTabs()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, R.id.shadowTop));
      items.add(new ListItem(ListItem.TYPE_FAKE_PAGER_TOPVIEW));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, R.id.shadowBottom));
    } else {
      items.add(new ListItem(ListItem.TYPE_FAKE_PAGER_TOPVIEW));
    }
  }

  @WorkerThread
  private void prepareChannelCells (TdApi.SupergroupFullInfo channelFull) {
    if (!channelFull.description.isEmpty()) {
      setDescription(channelFull.description);
    }
  }

  private void checkManage () {
    if (headerView != null) {
      headerView.updateCustomButtons(this, getMenuId());
    }
    /*int index = baseAdapter.indexOfViewById(R.id.btn_manageChat);
    boolean prevCouldManage = index != -1;
    boolean nowCanManage = canManageChat();

    if (prevCouldManage != nowCanManage) {
      if (nowCanManage) {
        addTopItem(newManageItem(), baseHeaderItemCount);
      } else {
        removeTopItem(index);
      }
    } else if (nowCanManage) {
      updateValuedItem(R.id.btn_manageChat);
    }*/
  }

  private void addFullChannelCells (TdApi.SupergroupFullInfo channelFull) {
    this.supergroupFull = channelFull;
    checkDescription();
    checkManage();
    // checkChannelMembers();

    if (tdlib.canCreateInviteLink(chat)) {
      updateValuedItem(R.id.btn_inviteLink);
    }

    updateHeader(false);
  }

  // Edit cells

  private boolean isEditing () {
    switch (mode) {
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_GROUP:
      case MODE_EDIT_SUPERGROUP:
        return true;
    }
    return false;
  }

  private interface SupergroupFeatureRunnable {
    void setSupergroupFeatureValue (long supergroupId, @Nullable TdApi.SupergroupFullInfo fullInfo, boolean newValue);
  }

  private void toggleSupergroupGroupFeature (FutureBool currentValue, SupergroupFeatureRunnable runnable, @IdRes int toggleViewId) {
    boolean newValue = !currentValue.getBoolValue();
    if (supergroupFull != null) {
      runnable.setSupergroupFeatureValue(supergroup.id, supergroupFull, newValue);
      baseAdapter.updateValuedSettingById(toggleViewId);
    } else if (mode == MODE_EDIT_GROUP) {
      showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), () ->
        tdlib.upgradeToSupergroup(chat.id, (oldChatId, newChatId, error) -> {
          if (newChatId != 0) {
            long supergroupId = ChatId.toSupergroupId(newChatId);
            runnable.setSupergroupFeatureValue(supergroupId, supergroupFull, newValue);
            runOnUiThreadOptional(() ->
              baseAdapter.updateValuedSettingById(toggleViewId)
            );
          }
        })
      );
    }
  }

  private void toggleAggressiveAntiSpam (View v) {
    boolean canToggleAggressiveAntiSpam =
      (supergroupFull != null && supergroupFull.canToggleAggressiveAntiSpam) ||
      (groupFull != null && groupFull.canToggleAggressiveAntiSpam);
    if (canToggleAggressiveAntiSpam) {
      boolean newValue = baseAdapter.toggleView(v);
      aggressiveAntiSpamItem.setSelected(newValue);
      checkDoneButton();
    }
  }

  private void toggleHideMembers (View v) {
    boolean canToggleHideMembers =
      (supergroupFull != null && supergroupFull.canHideMembers) ||
      (groupFull != null && groupFull.canHideMembers);
    if (canToggleHideMembers) {
      boolean newValue = baseAdapter.toggleView(v);
      hideMembersItem.setSelected(newValue);
      checkDoneButton();
    }
  }

  private void toggleJoinByRequests (View v) {
    if (tdlib.canToggleJoinByRequest(chat)) {
      boolean newValue = baseAdapter.toggleView(v);
      toggleJoinByRequestItem.setSelected(newValue);
      checkDoneButton();
    }
  }

  private void toggleContentProtection (View v) {
    if (tdlib.canToggleContentProtection(chat.id)) {
      boolean enabled = baseAdapter.toggleView(v);
      toggleHasProtectionItem.setSelected(enabled);
      checkDoneButton();
    } else {
      context().tooltipManager().builder(((SettingView) v).getToggler()).show(tdlib, isChannel() ? R.string.OnlyOwnerChannel : R.string.OnlyOwnerGroup);
    }
  }

  private void toggleChannelSignatures (View v) {
    if (tdlib.canToggleSignMessages(chat)) {
      boolean newValue = baseAdapter.toggleView(v);
      toggleSignMessagesItem.setSelected(newValue);
      checkDoneButton();
    }
  }

  private void togglePrehistoryMode () {
    boolean currentValue;
    if (supergroupFull != null)
      currentValue = supergroupFull.isAllHistoryAvailable;
    else if (groupFull != null)
      currentValue = false;
    else
      return;
    final ListItem headerItem = new ListItem(ListItem.TYPE_INFO, 0, 0, currentValue ? R.string.ChatHistoryVisibleInfo : R.string.ChatHistoryHiddenInfo, false);
    if (!currentValue && supergroupFull != null && supergroupFull.linkedChatId != 0) {
      headerItem.setString(new SpannableStringBuilder(Lang.getString(R.string.ChatHistoryHiddenInfo))
        .append("\n\n")
        .append(Lang.getStringBold(R.string.ChatHistoryWarnLinkedChannel, tdlib.chatTitle(supergroupFull.linkedChatId))));
    }
    if (groupFull != null)
      headerItem.setString(Lang.plural(R.string.ChatHistoryPartiallyHiddenInfo, 100));
    showSettings(
      new SettingsWrapBuilder(R.id.btn_prehistoryMode)
        .setRawItems(new ListItem[]{
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_visible, 0, R.string.ChatHistoryVisible, R.id.btn_prehistoryMode, currentValue),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_hidden, 0, R.string.ChatHistoryHidden, R.id.btn_prehistoryMode, !currentValue)
        })
        .setHeaderItem(headerItem)
        .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
          boolean visible = settingsAdapter.getCheckIntResults().get(R.id.btn_prehistoryMode) == R.id.btn_visible;
          if (groupFull != null && !visible) {
            headerItem.setString(Lang.plural(R.string.ChatHistoryPartiallyHiddenInfo, 100));
          } else if (!visible && supergroupFull != null && supergroupFull.linkedChatId != 0) {
            headerItem.setString(new SpannableStringBuilder(Lang.getString(R.string.ChatHistoryHiddenInfo))
              .append("\n\n")
              .append(Lang.getStringBold(R.string.ChatHistoryWarnLinkedChannel, tdlib.chatTitle(supergroupFull.linkedChatId))));
          } else {
            headerItem.setString(visible ? R.string.ChatHistoryVisibleInfo : R.string.ChatHistoryHiddenInfo);
          }
          settingsAdapter.updateValuedSettingByPosition(settingsAdapter.indexOfView(headerItem));
        })
        .setIntDelegate((id, result) -> {
          boolean visible = result.get(R.id.btn_prehistoryMode) == R.id.btn_visible;
          if (currentValue != visible) {
            if (groupFull != null) {
              showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), () ->
                tdlib.upgradeToSupergroup(chat.id, (oldChatId, newChatId, error) -> {
                  if (newChatId != 0) {
                    tdlib.client().send(new TdApi.ToggleSupergroupIsAllHistoryAvailable(ChatId.toSupergroupId(newChatId), visible), tdlib.okHandler());
                  }
                })
              );
            } else {
              if (supergroupFull != null && supergroupFull.linkedChatId != 0) {
                tdlib.client().send(new TdApi.SetChatDiscussionGroup(0, chat.id), ignored -> tdlib.client().send(new TdApi.ToggleSupergroupIsAllHistoryAvailable(supergroup.id, visible), tdlib.okHandler()));
              } else {
                tdlib.client().send(new TdApi.ToggleSupergroupIsAllHistoryAvailable(supergroup.id, visible), tdlib.okHandler());
              }
              baseAdapter.updateValuedSettingById(R.id.btn_prehistoryMode);
            }
          }
        })
    );
  }

  private void openEnabledReactions () {
    EditEnabledReactionsController c = new EditEnabledReactionsController(context, tdlib);
    c.setArguments(new EditEnabledReactionsController.Args(chat, EditEnabledReactionsController.TYPE_ENABLED_REACTIONS));
    navigateTo(c);
  }

  private void openChatPermissions () {
    EditRightsController c = new EditRightsController(context, tdlib);
    c.setArguments(new EditRightsController.Args(chat.id));
    navigateTo(c);
  }

  private void openRecentActions () {
    MessagesController c = new MessagesController(context, tdlib);
    c.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_EVENT_LOG, null, chat));
    navigateTo(c);
  }

  private void openStats () {
    ChatStatisticsController c = new ChatStatisticsController(context, tdlib);
    c.setArguments(new ChatStatisticsController.Args(chat.id));
    navigateTo(c);
  }

  private void editChannelUsername () {
    EditUsernameController c = new EditUsernameController(context, tdlib);
    c.setArguments(new EditUsernameController.Args(chat.id));
    navigateTo(c);
  }

  private void editLinkedChat () {
    TdApi.Chat linkedChat = supergroupFull != null && supergroupFull.linkedChatId != 0 ? tdlib.chat(supergroupFull.linkedChatId) : null;
    Lang.SpanCreator linkedChatCreator = (target, argStart, argEnd, argIndex, needFakeBold) ->
      new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          tdlib.ui().openChat(ProfileController.this, linkedChat, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
        }
      };
    switch (mode) {
      case MODE_EDIT_CHANNEL: {
        int size = linkedChat != null ? 3 : 2;
        IntList ids = new IntList(size);
        StringList strings = new StringList(size);
        IntList icons = new IntList(size);

        CharSequence info;
        if (linkedChat != null) {
          ids.append(R.id.btn_delete);
          strings.append(R.string.ChannelGroupRemove);
          icons.append(R.drawable.baseline_remove_circle_24);
          info = Lang.getString(R.string.ChannelGroupInfo2, linkedChatCreator,
            tdlib.chatTitle(linkedChat)
          );
        } else {
          info = Lang.getString(R.string.ChannelGroupInfo);
        }

        ids.append(R.id.btn_search);
        icons.append(R.drawable.baseline_search_24);
        strings.append(R.string.ChannelGroupExisting);

        ids.append(R.id.btn_new);
        icons.append(R.drawable.baseline_group_add_24);
        strings.append(R.string.ChannelGroupNew);

        showOptions(info, ids.get(), strings.get(), size == 3 ? new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL} : null, icons.get(), (v, id) -> {
          if (id == R.id.btn_delete) {
            if (linkedChat != null) {
              showConfirm(Lang.getString(R.string.UnlinkGroupConfirm, linkedChatCreator, tdlib.chatTitle(linkedChat)), Lang.getString(R.string.UnlinkGroupDone), R.drawable.baseline_remove_circle_24, OPTION_COLOR_RED, () ->
                tdlib.client().send(new TdApi.SetChatDiscussionGroup(chat.id, 0), tdlib.okHandler())
              );
            }
          } else if (id == R.id.btn_search) {
            PeopleController c = new PeopleController(context, tdlib);
            c.setArguments(new PeopleController.Args(PeopleController.MODE_DISCUSSION_GROUPS).setGroupSelectListener((context, group) -> {
              linkGroup(context, group.getChatId(), true);
              return true;
            }));
            navigateTo(c);
          } else if (id == R.id.btn_new) {
            CreateGroupController.Callback callback = new CreateGroupController.Callback() {
              @Override
              public boolean onGroupCreated (CreateGroupController context, TdApi.Chat chat) {
                linkGroup(context, chat.id, false);
                return true;
              }

              @Override
              public boolean forceSupergroupChat () {
                return true;
              }
            };
              /*ContactsController c = new ContactsController(context, tdlib);
              c.initWithMode(ContactsController.MODE_NEW_GROUP);
              c.setGroupCreationCallback();*/
            ArrayList<TGUser> users = new ArrayList<>();
            users.add(new TGUser(tdlib, tdlib.myUser()));
            CreateGroupController c = new CreateGroupController(context, tdlib);
            c.setGroupCreationCallback(callback);
            c.setMembers(users);
            navigateTo(c);
          }
          return true;
        }, null);
        break;
      }
      case MODE_EDIT_SUPERGROUP: {
        if (linkedChat == null)
          return;
        CharSequence info = Lang.getString(R.string.GroupChannelInfo, linkedChatCreator, tdlib.chatTitle(linkedChat));
        showOptions(info, new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(R.string.GroupChannelUnlink), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (v, id) -> {
          if (id == R.id.btn_delete) {
            showConfirm(Lang.getString(R.string.UnlinkChannelConfirm, linkedChatCreator, tdlib.chatTitle(linkedChat)), Lang.getString(R.string.UnlinkChannelDone), R.drawable.baseline_remove_circle_24, OPTION_COLOR_RED, () ->
              tdlib.client().send(new TdApi.SetChatDiscussionGroup(0, chat.id), tdlib.okHandler())
            );
          }
          return true;
        }, null);
        break;
      }
    }
  }

  private void linkGroup (ViewController<?> context, long selectedChatId, boolean needPrompt) {
    boolean isPublic = tdlib.chatPublic(chat.id);
    boolean isLinkedPublic = tdlib.chatPublic(selectedChatId);

    tdlib.cache().supergroupFull(ChatId.toSupergroupId(selectedChatId), selectedFullInfo -> {
      Runnable doneAct = () -> {
        RunnableLong act = chatId -> {
          tdlib.client().send(new TdApi.ToggleSupergroupIsAllHistoryAvailable(ChatId.toSupergroupId(chatId), true), ignored ->
            tdlib.client().send(new TdApi.SetChatDiscussionGroup(chat.id, chatId), tdlib.okHandler())
          );
          context.navigateBack();
        };
        if (ChatId.isBasicGroup(selectedChatId)) {
          tdlib.client().send(new TdApi.UpgradeBasicGroupChatToSupergroupChat(selectedChatId), result -> {
            switch (result.getConstructor()) {
              case TdApi.Chat.CONSTRUCTOR:
                tdlib.ui().post(() -> act.runWithLong(((TdApi.Chat) result).id));
                break;
              case TdApi.Error.CONSTRUCTOR:
                UI.showError(result);
                break;
            }
          });
        } else if (selectedFullInfo != null) {
          long currentChatId = selectedFullInfo.linkedChatId;
          tdlib.ui().post(() -> {
            if (currentChatId != 0) {
              showConfirm(Lang.getString(R.string.LinkGroupConfirmOverride, (target, argStart, argEnd, argIndex, needFakeBold) -> new ClickableSpan() {
                @Override
                public void onClick (@NonNull View widget) {
                  tdlib.ui().openChat(ProfileController.this, argIndex == 0 ? selectedChatId : currentChatId, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
                }
              }, tdlib.chatTitle(selectedChatId), tdlib.chatTitle(currentChatId)), Lang.getString(R.string.LinkGroupConfirmOverrideDone), R.drawable.baseline_remove_circle_24, OPTION_COLOR_RED, () -> {
                act.runWithLong(selectedChatId);
              });
            } else {
              act.runWithLong(selectedChatId);
            }
          });
        }
      };
      if (!needPrompt) {
        doneAct.run();
        return;
      }
      CharSequence prompt = Lang.getString(R.string.LinkGroupConfirm, (target, argStart, argEnd, argIndex, needFakeBold) -> new ClickableSpan() {
        @Override
        public void onClick (@NonNull View widget) {
          if (argIndex == 0) {
            tdlib.ui().openChat(ProfileController.this, selectedChatId, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
          } else {
            tdlib.ui().openChat(ProfileController.this, chat.id, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
          }
        }
      }, tdlib.chatTitle(selectedChatId), tdlib.chatTitle(chat.id));
      SpannableStringBuilder b = prompt instanceof SpannableStringBuilder ? (SpannableStringBuilder) prompt : new SpannableStringBuilder(prompt);
      if (!isPublic || !isLinkedPublic) {
        if (isPublic) {
          b.append("\n\n").append(Lang.getMarkdownString(this, R.string.LinkGroupConfirmWarnPrivateGroup));
        } else {
          b.append("\n\n").append(Lang.getMarkdownString(this, R.string.LinkGroupConfirmWarnPrivateChannel));
        }
      }
      if (selectedFullInfo != null && !selectedFullInfo.isAllHistoryAvailable) {
        b.append("\n\n").append(Lang.getMarkdownString(this, R.string.LinkGroupConfirmWarnPreHistory));
      }
      showOptions(b, new int[]{R.id.btn_done, R.id.btn_cancel}, new String[]{Lang.getString(R.string.LinkGroupConfirmDone), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_BLUE, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_link_24, R.drawable.baseline_cancel_24}, (optionView, optionId) -> {
        if (optionId == R.id.btn_done) {
          doneAct.run();
        }
        return true;
      });
    });
  }

  private @Nullable
  ListItem chatTitleItem, chatDescriptionItem;

  private boolean hasUnsavedChanges () {
    return
      (chatTitleItem != null && !StringUtils.equalsOrBothEmpty(chat.title, chatTitleItem.getStringValue())) ||
      (chatDescriptionItem != null && !StringUtils.equalsOrBothEmpty(getDescriptionValue(), chatDescriptionItem.getStringValue())) ||
      hasTtlChanges() ||
      hasSlowModeChanges() ||
      hasAggressiveAntiSpamChanges() ||
      hasHideMembersChanges() ||
      hasContentProtectionChanges() ||
      hasJoinByRequestChanges() ||
      hasSignMessagesChanges();
  }

  private boolean hasSlowModeChanges () {
    int originalSlowMode = supergroupFull != null ? supergroupFull.slowModeDelay : 0;
    return slowModeItem != null && originalSlowMode != TdConstants.SLOW_MODE_OPTIONS[slowModeItem.getSliderValue()];
  }

  private boolean hasAggressiveAntiSpamChanges () {
    boolean originalValue = supergroupFull != null && supergroupFull.hasAggressiveAntiSpamEnabled;
    return aggressiveAntiSpamItem != null && originalValue != aggressiveAntiSpamItem.isSelected();
  }

  private boolean hasHideMembersChanges () {
    boolean originalValue = supergroupFull != null && supergroupFull.hasHiddenMembers;
    return hideMembersItem != null && originalValue != hideMembersItem.isSelected();
  }

  private boolean hasContentProtectionChanges () {
    boolean originalValue = chat.hasProtectedContent;
    return toggleHasProtectionItem != null && originalValue != toggleHasProtectionItem.isSelected();
  }

  private boolean hasJoinByRequestChanges () {
    boolean originalValue = supergroup != null && supergroup.joinByRequest;
    return toggleJoinByRequestItem != null && originalValue != toggleJoinByRequestItem.isSelected();
  }

  private boolean hasSignMessagesChanges () {
    boolean originalValue = supergroup != null && supergroup.signMessages;
    return toggleSignMessagesItem != null && originalValue != toggleSignMessagesItem.isSelected();
  }

  private boolean hasTtlChanges () {
    int originalSlowMode = chat != null ? chat.messageAutoDeleteTime : 0;
    return ttlItem != null && originalSlowMode != TdConstants.CHAT_TTL_OPTIONS[ttlItem.getSliderValue()];
  }

  @Override
  public boolean onDoneClick (View v) {
    applyChatChanges();
    return false; // mode == MODE_EDIT_GROUP && !TD.isCreator(group.status);
  }

  private void modifyEditTextImpl (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
    final int itemId = item.getId();
    if (itemId == R.id.title) {
      editText.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
      editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    } else if (itemId == R.id.description) {
      editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
      Views.setSingleLine(editText.getEditText(), false);
      editText.setMaxLength(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH);
    }
  }

  private void checkDoneButton () {
    setDoneVisible(hasUnsavedChanges());
  }

  private void applyChatChanges () {
    applyChatChanges(false);
  }

  private void applyChatChanges (boolean force) {
    if (inProgress) {
      return;
    }

    if (!hasUnsavedChanges()) {
      return;
    }

    boolean hasSlowModeChanges = hasSlowModeChanges();
    boolean hasTtlChanges = hasTtlChanges();
    boolean hasAggressiveAntiSpamChanges = hasAggressiveAntiSpamChanges();
    boolean hasHideMembersChanges = hasHideMembersChanges();
    boolean hasContentProtectionChanges = hasContentProtectionChanges();
    boolean hasJoinByRequestChanges = hasJoinByRequestChanges();
    boolean hasSignMessagesChanges = hasSignMessagesChanges();

    if (!force && (hasSlowModeChanges || hasAggressiveAntiSpamChanges || hasHideMembersChanges || hasJoinByRequestChanges || hasSignMessagesChanges) && ChatId.isBasicGroup(chat.id)) {
      showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), () -> applyChatChanges(true));
      return;
    }

    final ArrayList<TdApi.Function<?>> changes = new ArrayList<>(3);

    final String newTitle = chatTitleItem != null ? chatTitleItem.getStringValue() : null;
    if (chatTitleItem != null) {
      if (!StringUtils.equalsOrBothEmpty(chat.title, newTitle)) {
        if (StringUtils.isEmptyOrBlank(newTitle)) {
          UI.showToast(R.string.ChatTitleEmpty, Toast.LENGTH_SHORT);
          return;
        }
        changes.add(new TdApi.SetChatTitle(chat.id, newTitle));
      }
    }
    final String newDescription = chatDescriptionItem != null ? chatDescriptionItem.getStringValue() : null;
    if (chatDescriptionItem != null) {
      if (!StringUtils.equalsOrBothEmpty(getDescriptionValue(), chatDescriptionItem.getStringValue())) {
        changes.add(new TdApi.SetChatDescription(chat.id, newDescription));
      }
    }

    if (hasSlowModeChanges) {
      changes.add(new TdApi.SetChatSlowModeDelay(chat.id, TdConstants.SLOW_MODE_OPTIONS[slowModeItem.getSliderValue()]));
    }

    if (hasTtlChanges) {
      changes.add(new TdApi.SetChatMessageAutoDeleteTime(chat.id, TdConstants.CHAT_TTL_OPTIONS[ttlItem.getSliderValue()]));
    }

    if (hasContentProtectionChanges) {
      changes.add(new TdApi.ToggleChatHasProtectedContent(chat.id, toggleHasProtectionItem.isSelected()));
    }

    if (hasJoinByRequestChanges) {
      changes.add(new TdApi.ToggleSupergroupJoinByRequest(ChatId.toSupergroupId(chat.id), toggleJoinByRequestItem.isSelected()));
    }

    if (hasAggressiveAntiSpamChanges) {
      changes.add(new TdApi.ToggleSupergroupHasAggressiveAntiSpamEnabled(ChatId.toSupergroupId(chat.id), aggressiveAntiSpamItem.isSelected()));
    }

    if (hasHideMembersChanges) {
      changes.add(new TdApi.ToggleSupergroupHasHiddenMembers(ChatId.toSupergroupId(chat.id), hideMembersItem.isSelected()));
    }

    if (hasSignMessagesChanges) {
      changes.add(new TdApi.ToggleSupergroupSignMessages(ChatId.toSupergroupId(chat.id), toggleSignMessagesItem.isSelected()));
    }

    if (changes.isEmpty()) {
      return;
    }

    setDoneVisible(true);
    setInProgress(true);

    Runnable act = () -> {
      final int[] signal = new int[2];

      final Client.ResultHandler resultHandler = object -> {
        signal[0]++;
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            signal[1]++;
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            runOnUiThreadOptional(() -> {
              showDoneError(TD.toErrorString(object));
            });
            break;
          }
        }
        if (signal[0] == changes.size()) {
          runOnUiThreadOptional(() -> {
            setInProgress(false);
            if (signal[1] == changes.size()) {
              navigateBack();
            }
          });
        }
      };

      for (TdApi.Function<?> function : changes) {
        tdlib.client().send(function, resultHandler);
      }
    };

    if (hasSlowModeChanges && ChatId.isBasicGroup(chat.id)) {
      tdlib.upgradeToSupergroup(chat.id, (fromChatId, toChatId, error) -> {
        if (error != null) {
          runOnUiThreadOptional(() -> {
            showDoneError(TD.toErrorString(error));
            setInProgress(false);
          });
        } else {
          for (TdApi.Function<?> function : changes) {
            switch (function.getConstructor()) {
              case TdApi.SetChatDescription.CONSTRUCTOR:
                ((TdApi.SetChatDescription) function).chatId = toChatId;
                break;
              case TdApi.SetChatTitle.CONSTRUCTOR:
                ((TdApi.SetChatTitle) function).chatId = toChatId;
                break;
              case TdApi.SetChatSlowModeDelay.CONSTRUCTOR:
                ((TdApi.SetChatSlowModeDelay) function).chatId = toChatId;
                break;
              default:
                throw new UnsupportedOperationException(function.toString());
            }
          }
          act.run();
        }
      });
    } else {
      act.run();
    }
  }

  private void showDoneError (CharSequence text) {
    context.tooltipManager().builder(doneButton).show(this, tdlib, R.drawable.baseline_error_24, text);
  }

  private DoneButton doneButton;

  private boolean isDoneVisible;

  private void setDoneVisible (boolean isVisible) {
    if (this.isDoneVisible != isVisible) {
      this.isDoneVisible = isVisible;
      if (doneButton == null) {
        doneButton = new DoneButton(context());
        doneButton.setId(R.id.btn_done);
        doneButton.setOnClickListener(this);
        contentView.addView(doneButton);
      }
      doneButton.setIsVisible(isVisible, true);
    }
  }

  private boolean inProgress;

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      setStackLocked(inProgress);
      doneButton.setInProgress(inProgress);
    }
  }

  private ListItem slowModeItem, slowModeDescItem;
  private ListItem aggressiveAntiSpamItem, hideMembersItem,
    toggleJoinByRequestItem, toggleHasProtectionItem, toggleSignMessagesItem;
  private ListItem ttlItem, ttlDescItem;

  private void buildEditCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (tdlib.canChangeInfo(chat)) {
      chatTitleItem = new ListItem(mode == MODE_EDIT_GROUP ? ListItem.TYPE_EDITTEXT_WITH_PHOTO : ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER, R.id.title, 0, mode == MODE_EDIT_CHANNEL ? R.string.ChannelName : R.string.GroupName)
        .setStringValue(chat.title)
        .setInputFilters(new InputFilter[]{
          new CodePointCountFilter(TdConstants.MAX_CHAT_TITLE_LENGTH),
          new EmojiFilter(),
          new CharacterStyleFilter()
        })
        .setOnEditorActionListener(new EditBaseController.SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this));
      items.add(chatTitleItem);

      chatDescriptionItem = new ListItem(ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION, R.id.description, 0, R.string.Description).setStringValue(getDescriptionValue()).setInputFilters(new InputFilter[]{
        new CodePointCountFilter(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH),
        new EmojiFilter(),
        new CharacterStyleFilter()
      });
      items.add(chatDescriptionItem);

      baseAdapter.setLockFocusOn(this, false);
      baseAdapter.setTextChangeListener((id, item, v, text) -> {
        // item.setStringValue(text);
        if (id == R.id.description) {
          // onItemsHeightProbablyChanged();
        }
        checkDoneButton();
      });
      baseAdapter.setHeightChangeListener((view, newHeight) -> {
        if (newHeight != 0) {
          fakeChannelDesc = view;
          onItemsHeightProbablyChanged();
        }
      });
    } else {
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(new TGFoundChat(tdlib, null, chat.id, false)));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, R.id.shadowMiddle));

    TdApi.ChatMemberStatus myStatus = supergroup != null ? supergroup.status : group.status;

    int itemCount = 0;
    if (canSetUsername()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_channelType, 0, mode == MODE_EDIT_CHANNEL ? R.string.ChannelLink : R.string.GroupLink));
      itemCount++;
    }
    if (canChangeInfo() && supergroupFull != null && (mode == MODE_EDIT_CHANNEL || (mode == MODE_EDIT_SUPERGROUP && supergroupFull.linkedChatId != 0))) {
      items.add(new ListItem(itemCount > 0 ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(newLinkedChatItem());
      itemCount++;
    }
    if (tdlib.canManageInviteLinks(chat)) {
      items.add(new ListItem(itemCount > 0 ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_manageInviteLinks, 0, R.string.InviteLinkManage));
      if (chat.pendingJoinRequests != null && chat.pendingJoinRequests.totalCount > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_manageJoinRequests, 0, R.string.InviteLinkRequests));
      }
      itemCount++;
    }
    if (itemCount > 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (tdlib.canToggleJoinByRequest(chat)) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(toggleJoinByRequestItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleJoinByRequest, 0, R.string.ApproveNewMembers, supergroup != null && supergroup.joinByRequest));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ApproveNewMembersInfo));
    }

    boolean added = false;

    if (tdlib.canToggleSignMessages(chat)) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(toggleSignMessagesItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleSignatures, 0, R.string.ChannelSignMessages, supergroup.signMessages));
      added = true;
    }
    if (tdlib.canToggleContentProtection(chat.id) || TD.isAdmin(supergroup != null ? supergroup.status : group.status)) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(toggleHasProtectionItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleProtection, 0, R.string.RestrictSaving, chat.hasProtectedContent));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, mode == MODE_EDIT_CHANNEL ? R.string.RestrictSavingChannelHint : R.string.RestrictSavingGroupHint));
      added = false;
    }
    if (tdlib.canToggleAllHistory(chat)) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_prehistoryMode, 0, R.string.ChatHistory));
      added = true;
    }
    if (!isChannel()) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_chatPermissions, 0, R.string.ChatPermissions));
      added = true;
    }

    if (tdlib.canChangeInfo(chat)) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_enabledReactions, 0, R.string.EnabledReactions));
      added = true;
    }

    if (supergroupFull != null && supergroupFull.canGetStatistics) {
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_viewStatistics, 0, R.string.ViewStats));
      added = true;
    }
    boolean hasActions = false;
    if (TD.isAdmin(myStatus) && supergroup != null) { // TODO server: recent actions for basic groups?
      items.add(new ListItem(added ? ListItem.TYPE_SEPARATOR_FULL : ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_recentActions, 0, R.string.EventLog));
      added = true;
      hasActions = true;
    }
    if (added)
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, R.id.belowRecentActions));
    if (hasActions) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, mode == MODE_EDIT_CHANNEL ? R.string.RecentActionsChannelHint : R.string.RecentActionsGroupHint));
    }

    if ((supergroupFull != null && supergroupFull.canToggleAggressiveAntiSpam) || (groupFull != null && groupFull.canToggleAggressiveAntiSpam && tdlib.canUpgradeChat(chat.id))) {
      boolean aggressiveAntiSpamEnabled = supergroupFull != null && supergroupFull.hasAggressiveAntiSpamEnabled;
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(aggressiveAntiSpamItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleAggressiveAntiSpam, 0, R.string.AggressiveAntiSpam, aggressiveAntiSpamEnabled));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AggressiveAntiSpamDesc));
    }

    if ((supergroupFull != null && supergroupFull.canHideMembers) || (groupFull != null && groupFull.canHideMembers && tdlib.canUpgradeChat(chat.id))) {
      boolean membersHidden = supergroupFull != null && supergroupFull.hasHiddenMembers;
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(hideMembersItem = new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleHideMembers, 0, R.string.HideMembers, membersHidden));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.HideMembersDesc));
    }

    if (tdlib.canRestrictMembers(chat.id) && (tdlib.isSupergroup(chat.id) || (ChatId.isBasicGroup(chat.id) && tdlib.canUpgradeChat(chat.id)))) {
      int slowModeValue = supergroupFull != null ? supergroupFull.slowModeDelay : 0;
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SlowMode));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      String[] sliderValues = new String[TdConstants.SLOW_MODE_OPTIONS.length];
      int sliderValueIndex = -1;
      for (int i = 0; i < sliderValues.length; i++) {
        int sliderOption = TdConstants.SLOW_MODE_OPTIONS[i];
        if (sliderOption == 0) {
          sliderValues[i] = Lang.getString(R.string.SlowModeOff);
        } else {
          sliderValues[i] = TdlibUi.getDuration(sliderOption, TimeUnit.SECONDS, TdlibUi.DURATION_MODE_SHORT);
        }
        if (sliderOption == slowModeValue) {
          sliderValueIndex = i;
        }
      }
      if (sliderValueIndex == -1) {
        sliderValueIndex = slowModeValue == 0 ? 0 : 1;
      }
      items.add(slowModeItem = new ListItem(ListItem.TYPE_SLIDER, R.id.btn_slowMode).setSliderInfo(sliderValues, sliderValueIndex));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(slowModeDescItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_slowModeDescription, 0, getSlowModeDescription(slowModeValue), false));
    }

    if (tdlib.canDeleteMessages(chat.id) && !ChatId.isSecret(chat.id)) {
      final int ttlValue = chat != null ? chat.messageAutoDeleteTime : 0;
      final boolean isChannel = isChannel();
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, isChannel ? R.string.ChannelTtl : R.string.ChatTtl));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      String[] sliderValues = new String[TdConstants.CHAT_TTL_OPTIONS.length];
      int sliderValueIndex = -1;
      for (int i = 0; i < sliderValues.length; i++) {
        int sliderOption = TdConstants.CHAT_TTL_OPTIONS[i];
        if (sliderOption == 0) {
          sliderValues[i] = Lang.getString(isChannel ? R.string.ChannelTtlOff : R.string.ChatTtlOff);
        } else {
          sliderValues[i] = Lang.getDuration(sliderOption);
        }
        if (sliderOption == ttlValue) {
          sliderValueIndex = i;
        }
      }
      if (sliderValueIndex == -1) {
        sliderValueIndex = ttlValue == 0 ? 0 : 1;
      }
      items.add(ttlItem = new ListItem(ListItem.TYPE_SLIDER, R.id.btn_chatTtl).setSliderInfo(sliderValues, sliderValueIndex));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(ttlDescItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_chatTtlDescription, 0, getTtlDescription(ttlValue, isChannel), false));
    }

    addMediaItems(items);
    baseAdapter.setItems(items, false);

    if (tdlib.canInviteUsers(chat)) {
      requestInviteLinks(tdlib.chatStatus(chat.id).getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR);
    }
  }

  private void requestInviteLinks (boolean owner) {
    Runnable update = () -> baseAdapter.updateValuedSettingById(R.id.btn_manageInviteLinks);

    inviteLinksCount = 0;
    inviteLinksRevokedCount = 0;

    if (owner) {
      tdlib.client().send(new TdApi.GetChatInviteLinkCounts(chat.id), object3 -> {
        if (object3.getConstructor() == TdApi.ChatInviteLinkCounts.CONSTRUCTOR) {
          runOnUiThreadOptional(() -> {
            for (TdApi.ChatInviteLinkCount count : ((TdApi.ChatInviteLinkCounts) object3).inviteLinkCounts) {
              inviteLinksCount += count.inviteLinkCount;
              inviteLinksRevokedCount += count.revokedInviteLinkCount;
            }

            update.run();
          });
        }
      });
    } else {
      tdlib.client().send(new TdApi.GetChatInviteLinks(chat.id, tdlib.myUserId(), false, 0, null, 1), object -> {
        if (object.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
          inviteLinksCount += ((TdApi.ChatInviteLinks) object).totalCount;
        }

        tdlib.client().send(new TdApi.GetChatInviteLinks(chat.id, tdlib.myUserId(), true, 0, null, 1), object2 -> {
          if (object2.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
            inviteLinksRevokedCount += ((TdApi.ChatInviteLinks) object2).totalCount;
          }

          runOnUiThreadOptional(update);
        });
      });
    }
  }

  public void onInviteLinkCountChanged (int totalActive, int totalRevoked) {
    inviteLinksCount = totalActive;
    inviteLinksRevokedCount = totalRevoked;
    baseAdapter.updateValuedSettingById(R.id.btn_manageInviteLinks);
  }

  @Override
  public void onChatPendingJoinRequestsChanged (long chatId, TdApi.ChatJoinRequestsInfo pendingJoinRequests) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        int idxLinks = baseAdapter.indexOfViewById(R.id.btn_manageInviteLinks);
        int idxRequests = baseAdapter.indexOfViewById(R.id.btn_manageJoinRequests);

        if ((pendingJoinRequests == null || pendingJoinRequests.totalCount == 0) && idxRequests != -1) {
          baseAdapter.removeRange(idxRequests - 1, 2);
        } else if (pendingJoinRequests != null && pendingJoinRequests.totalCount > 0 && idxRequests == -1 && idxLinks != -1) {
          baseAdapter.addItems(idxLinks + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL), new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_manageJoinRequests, 0, R.string.InviteLinkRequests));
        } else {
          baseAdapter.updateValuedSettingById(R.id.btn_manageJoinRequests);
        }
      }
    });
  }

  private static CharSequence getSlowModeDescription (int seconds) {
    if (seconds == 0) {
      return Lang.getString(R.string.SlowModeDisabled);
    } else if (TimeUnit.SECONDS.toHours(seconds) > 0) {
      return Lang.pluralBold(R.string.SlowModeHours, TimeUnit.SECONDS.toHours(seconds));
    } else if (TimeUnit.SECONDS.toMinutes(seconds) > 0) {
      return Lang.pluralBold(R.string.SlowModeMinutes, TimeUnit.SECONDS.toMinutes(seconds));
    } else {
      return Lang.pluralBold(R.string.SlowModeSeconds, seconds);
    }
  }

  private static CharSequence getTtlDescription (int seconds, boolean isChannel) {
    if (seconds == 0) {
      return Lang.getString(isChannel ? R.string.ChannelTtlDisabled : R.string.ChatTtlDisabled);
    } else {
      return Lang.getStringBold(isChannel ? R.string.ChannelTtlEnabled : R.string.ChatTtlEnabled, Lang.getDuration(seconds));
    }
  }

  private void processEditContentChanged (TdApi.Supergroup updatedSupergroup) {
    this.supergroup = updatedSupergroup;
    checkCanToggleJoinByRequest();
    baseAdapter.updateValuedSettingById(R.id.btn_toggleProtection);

    switch (mode) {
      case MODE_EDIT_SUPERGROUP: {
        baseAdapter.updateValuedSettingById(R.id.btn_channelType);
        baseAdapter.updateValuedSettingById(R.id.btn_linkedChat);
        checkPrehistory();
        break;
      }
      case MODE_EDIT_CHANNEL: {
        baseAdapter.updateValuedSettingById(R.id.btn_toggleSignatures);
        baseAdapter.updateValuedSettingById(R.id.btn_linkedChat);
        baseAdapter.updateValuedSettingById(R.id.btn_channelType);
        break;
      }
    }
  }

  private ListItem newLinkedChatItem () {
    return new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_linkedChat, 0, mode == MODE_EDIT_CHANNEL ? R.string.LinkedGroup : R.string.LinkedChannel);
  }

  @Override
  public CharSequence getName () {
    switch (mode) {
      case MODE_EDIT_GROUP:
        return Lang.getString(TD.isCreator(group.status) ? R.string.ManageGroup : TD.isAdmin(group.status) || chat.permissions.canChangeInfo ? R.string.EditGroup : R.string.GroupInfo);
      case MODE_EDIT_SUPERGROUP:
        return Lang.getString(TD.isAdmin(supergroup.status) ? R.string.ManageGroup : chat.permissions.canChangeInfo ? R.string.EditGroup : R.string.GroupInfo);
      case MODE_EDIT_CHANNEL:
        return Lang.getString(R.string.ManageChannel);
    }
    return super.getName();
  }

  // Utils

  public boolean isSupergroup () {
    return (mode == MODE_SUPERGROUP || mode == MODE_EDIT_SUPERGROUP);
  }

  public boolean isBasicGroup () {
    return (mode == MODE_GROUP || mode == MODE_EDIT_GROUP);
  }

  public boolean isChannel () {
    return (mode == MODE_CHANNEL || mode == MODE_EDIT_CHANNEL);
  }

  public boolean isCreator () {
    switch (mode) {
      case MODE_GROUP:
      case MODE_EDIT_GROUP: {
        return TD.isCreator(group.status);
      }
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP: {
        return TD.isCreator(supergroup.status);
      }
    }
    return false;
  }

  public boolean isMember () {
    return (mode == MODE_GROUP && !TD.isNotInChat(group.status)) || (mode != MODE_GROUP && !TD.isNotInChat(supergroup.status));
  }

  // UI utils

  public boolean canBanMembers () {
    switch (mode) {
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
        switch (supergroup.status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            return ((TdApi.ChatMemberStatusAdministrator) supergroup.status).rights.canRestrictMembers;
        }
        break;
    }
    return false;
  }

  public boolean canPromoteMembers () {
    switch (mode) {
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
        switch (supergroup.status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            return ((TdApi.ChatMemberStatusAdministrator) supergroup.status).rights.canPromoteMembers;
        }
        break;
      case MODE_GROUP:
        return TD.isCreator(group.status);
    }
    return false;
  }

  public boolean canAddAnyKindOfMembers () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    return (status != null && status.getConstructor() != TdApi.ChatMemberStatusLeft.CONSTRUCTOR) && (tdlib.canInviteUsers(chat) || canBanMembers() || canPromoteMembers()); // FIXME or not?
  }

  @Override
  public boolean onSenderPick (final ContactsController context, final View view, final TdApi.MessageSender senderId) {
    if (tdlib.isSelfSender(senderId)) {
      // Ignoring current user, as it shouldn't be offered by UI.
      return false;
    }
    addMember(context, view, senderId);
    return false;
  }

  @Override
  public void onSenderConfirm (ContactsController context, TdApi.MessageSender senderId, int option) {
    tdlib.client().send(new TdApi.SetChatMemberStatus(chat.id, senderId, new TdApi.ChatMemberStatusMember()), tdlib.okHandler());
  }

  private void addMember (View view) {
    if (canAddAnyKindOfMembers() && !isStackLocked()) {
      if (mode == MODE_SUPERGROUP) {
        if (supergroupFull == null) {
          return;
        }
        if (supergroupFull.memberCount >= tdlib.supergroupMaxSize()) {
          context.tooltipManager().builder(view).show(this, tdlib, R.drawable.baseline_error_24, Lang.pluralBold(R.string.ParticipantXLimitReached, tdlib.supergroupMaxSize()));
          return;
        }
      }

      if (!canPromoteMembers() && !canBanMembers()) {
        addMemberImpl(MODE_MEMBER_REGULAR);
        return;
      }

      IntList ids = new IntList(3);
      IntList icons = new IntList(3);
      StringList strings = new StringList(3);

      ids.append(R.id.btn_addMember);
      icons.append(R.drawable.baseline_person_add_24);
      strings.append(R.string.AddMember);

      if (canPromoteMembers()) {
        ids.append(R.id.btn_addAdmin);
        icons.append(R.drawable.baseline_stars_24);
        strings.append(R.string.ChannelAddAdmin);
      }

      if (canBanMembers()) {
        ids.append(R.id.btn_banMember);
        if (mode == MODE_SUPERGROUP) {
          icons.append(R.drawable.baseline_block_24);
          strings.append(R.string.BanMember);
        } else {
          icons.append(R.drawable.baseline_remove_circle_24);
          strings.append(R.string.BanMember);
        }
      }

      showOptions(null, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
        if (id == R.id.btn_addMember) {
          addMemberImpl(MODE_MEMBER_REGULAR);
        } else if (id == R.id.btn_addAdmin) {
          addMemberImpl(MODE_MEMBER_ADMIN);
        } else if (id == R.id.btn_banMember) {
          addMemberImpl(MODE_MEMBER_BAN);
        }
        return true;
      });
    }
  }

  private static final int MODE_MEMBER_REGULAR = 0;
  private static final int MODE_MEMBER_ADMIN = 1;
  private static final int MODE_MEMBER_BAN = 2;

  private int currentPickMode;

  private void addMemberImpl (int pickMode) {
    this.currentPickMode = pickMode;
    ContactsController c = new ContactsController(context, tdlib);
    c.initWithMode(ContactsController.MODE_ADD_MEMBER);
    c.setAllowBots(!isChannel() || pickMode == MODE_MEMBER_ADMIN);
    if (!isChannel()) {
      c.setAllowChats(pickMode == MODE_MEMBER_BAN, pickMode == MODE_MEMBER_BAN);
    }
    c.setArguments(new ContactsController.Args(this));
    switch (currentPickMode) {
      case MODE_MEMBER_REGULAR:
        c.setChatTitle(R.string.AddMember, chat.title);
        break;
      case MODE_MEMBER_ADMIN:
        c.setChatTitle(R.string.ChannelAddAdmin, chat.title);
        break;
      case MODE_MEMBER_BAN:
        c.setChatTitle(R.string.BanMember, chat.title);
        break;
    }
    navigateTo(c);
  }

  private void addMember (final ContactsController context, final View view, final TdApi.MessageSender senderId) {
    final int mode = currentPickMode;
    final AtomicReference<TdApi.Object> result = new AtomicReference<>();
    final CancellableRunnable act = new CancellableRunnable() {
      @Override
      public void act () {
        context().hideProgress(false);
        if (!context.isDestroyed()) {
          TdApi.Object object = result.get();
          switch (object.getConstructor()) {
            case TdApi.ChatMember.CONSTRUCTOR: {
              addMember(mode, context, view, (TdApi.ChatMember) object, -1, true);
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              context.context()
                .tooltipManager()
                .builder(view)
                .show(context, tdlib, R.drawable.baseline_error_24, TD.toErrorString(object));
              break;
            }
          }
        }
      }
    };
    tdlib.client().send(new TdApi.GetChatMember(chat.id, senderId), object -> {
      result.set(object);
      tdlib.ui().post(act);
    });
  }

  private void addMember (final int mode, final ContactsController context, final View view,
                          final TdApi.ChatMember member, final int forwardLimit,
                          final boolean needConfirm) {
    final Tdlib.ChatMemberStatusChangeCallback callback = (success, error) -> tdlib.ui().post(() -> {
      if (success) {
        context.navigateBack();
      } else {
        context.context()
          .tooltipManager()
          .builder(view)
          .show(context, tdlib, R.drawable.baseline_error_24,
            error != null && TD.ERROR_USER_PRIVACY.equals(error.message) ?
              Lang.getString(R.string.errorPrivacyAddMember) :
              TD.toErrorString(error)
          );
      }
    });
    final String memberName = tdlib.senderName(member.memberId);
    TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(chat.id);
    if (myStatus == null) {
      myStatus = isBasicGroup() ? group.status : supergroup.status;
    }
    if (forwardLimit == -1 && (mode == MODE_MEMBER_REGULAR || mode == MODE_MEMBER_ADMIN) && !TD.isMember(member.status, false) && isBasicGroup()) {
      showSettings(new SettingsWrapBuilder(R.id.btn_addMember)
        .addHeaderItem(Lang.getStringBold(mode == MODE_MEMBER_ADMIN ? R.string.AddAdminToTheGroup : R.string.AddToTheGroup, memberName))
        .setRawItems(new ListItem[]{
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_forwardLast100, 0, Lang.plural(R.string.ForwardLastXMessages, 100), R.id.btn_addMember, false),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_forwardLast50, 0, Lang.plural(R.string.ForwardLastXMessages, 50), R.id.btn_addMember, true),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_forwardLast15, 0, Lang.plural(R.string.ForwardLastXMessages, 15), R.id.btn_addMember, false),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_addToGroup, 0, R.string.justAdd, R.id.btn_addMember, false)
        }).setIntDelegate((id, result) -> {
          if (id != R.id.btn_addMember) {
            return;
          }
          int chosenForwardCount = 0;
          if (result.size() > 0) {
            final int resultId = result.get(R.id.btn_addMember);
            if (resultId == R.id.btn_forwardLast100) {
              chosenForwardCount = 100;
            } else if (resultId == R.id.btn_forwardLast50) {
              chosenForwardCount = 50;
            } else if (resultId == R.id.btn_forwardLast15) {
              chosenForwardCount = 15;
            }
          }
          addMember(mode, context, view, member, chosenForwardCount, false);
        }).setSaveStr(R.string.AddMemberBtn));
      return;
    }
    switch (mode) {
      case MODE_MEMBER_REGULAR: {
        if (TD.isMember(member.status)) {
          context.context()
            .tooltipManager()
            .builder(view)
            .show(context, tdlib, R.drawable.baseline_info_24,
              Lang.getString(R.string.XIsAlreadyInChat, memberName)
            );
          return;
        }

        if (needConfirm) {
          showOptions(new Options.Builder()
              .info(Lang.getStringBold(isChannel() ? R.string.QAddXToChannel : R.string.AddToTheGroup, memberName))
              .item(new OptionItem(R.id.btn_addMember, Lang.getString(R.string.AddMember), OPTION_COLOR_NORMAL, R.drawable.baseline_person_add_24))
              .cancelItem()
              .build(),
            (itemView, id) -> {
              if (id == R.id.btn_addMember) {
                addMember(mode, context, view, member, forwardLimit, false);
              }
              return true;
            }
          );
          return;
        }

        tdlib.setChatMemberStatus(chat.id, member.memberId, new TdApi.ChatMemberStatusMember(), forwardLimit, member.status, callback);
        break;
      }
      case MODE_MEMBER_ADMIN: {
        if (TD.isCreator(member.status) || (TD.isAdmin(member.status) && !((TdApi.ChatMemberStatusAdministrator) member.status).canBeEdited)) {
          context.context()
            .tooltipManager()
            .builder(view)
            .show(context, tdlib, R.drawable.baseline_info_24,
              Lang.getString(R.string.XIsAlreadyAdmin, tdlib.senderName(member.memberId))
            );
          return;
        }

        if (!canBanMembers() && (
          member.status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ||
            member.status.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR
        )) {
          context.context()
            .tooltipManager()
            .builder(view)
            .show(context, tdlib, R.drawable.baseline_error_24,
              Lang.getStringBold(R.string.YouCantPromoteX, memberName)
            );
          return;
        }

        EditRightsController c = new EditRightsController(this.context, this.tdlib);
        c.setArguments(new EditRightsController.Args(chat.id, member.memberId, false, myStatus, member)
          .forwardLimit(forwardLimit)
        );
        context.preventLeavingSearchMode();
        context.navigateTo(c);
        break;
      }
      case MODE_MEMBER_BAN: {
        if (TD.isCreator(member.status) || (TD.isAdmin(member.status) && !((TdApi.ChatMemberStatusAdministrator) member.status).canBeEdited)) {
          context.context()
            .tooltipManager()
            .builder(view)
            .show(context, tdlib, R.drawable.baseline_error_24, Lang.getStringBold(R.string.YouCantBanX, memberName));
          return;
        }
        if (isBasicGroup() || isChannel()) {
          showOptions(Lang.getStringBold(isBasicGroup() ? R.string.MemberCannotJoinGroup : R.string.MemberCannotJoinChannel, memberName), new int[] {R.id.btn_blockSender, R.id.btn_cancel}, new String[]{Lang.getString(R.string.BlockUser), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_blockSender) {
              tdlib.setChatMemberStatus(chat.id, member.memberId, new TdApi.ChatMemberStatusBanned(), member.status, (success, error) -> {
                if (success) {
                  context.navigateBack();
                } else
                  context.context()
                    .tooltipManager()
                    .builder(view)
                    .show(context, tdlib, R.drawable.baseline_error_24, TD.toErrorString(error));
              });
            }
            return true;
          });
        } else {
          EditRightsController c = new EditRightsController(this.context, this.tdlib);
          c.setArguments(new EditRightsController.Args(chat.id, member.memberId, true, myStatus, member));
          context.preventLeavingSearchMode();
          context.navigateTo(c);
        }
        break;
      }
    }
  }

  private boolean share (boolean needCopy) {
    String username = getProfileUsername();
    if (!StringUtils.isEmpty(username)) {
      String link = tdlib.tMeUrl(username);
      String name = tdlib.chatTitle(chat, false);
      String exportText;
      int textRes = R.string.ShareTextLink;
      switch (mode) {
        case MODE_USER:
        case MODE_SECRET:
          exportText = Lang.getString(tdlib.isBotChat(chat) ? R.string.ShareTextBotLink : R.string.ShareTextProfileLink, name, link);
          textRes = R.string.ShareTextProfileLink2;
          break;
        case MODE_GROUP:
        case MODE_EDIT_GROUP:
        case MODE_SUPERGROUP:
        case MODE_EDIT_SUPERGROUP:
          exportText = Lang.getString(R.string.ShareTextChatLink, name, link);
          break;
        case MODE_CHANNEL:
        case MODE_EDIT_CHANNEL:
          exportText = Lang.getString(R.string.ShareTextChannelLink, name, link);
          break;
        default:
          return false;
      }
      String text = Lang.getString(textRes, name, link);
      ShareController c = new ShareController(context, tdlib);
      c.setArguments(new ShareController.Args(text).setShare(exportText, null));
      c.show();
      return true;
    }
    return false;
  }

  private void joinChannel () {
    switch (mode) {
      case MODE_CHANNEL:
      case MODE_SUPERGROUP: {
        tdlib.client().send(new TdApi.AddChatMember(chat.id, tdlib.myUserId(), 0), tdlib.okHandler());
        break;
      }
    }
  }

  // Listeners

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode != Activity.RESULT_OK) {
      return;
    }
    switch (requestCode) {
      case Intents.ACTIVITY_RESULT_IMAGE_CAPTURE: {
        File image = Intents.takeLastOutputMedia();
        if (image != null) {
          // TODO show editor
          U.addToGallery(image);
          UI.showToast(R.string.UploadingPhotoWait, Toast.LENGTH_SHORT);
          tdlib.client().send(new TdApi.SetChatPhoto(chat.id, new TdApi.InputChatPhotoStatic(new TdApi.InputFileGenerated(image.getPath(), SimpleGenerationInfo.makeConversion(image.getPath()), 0))), tdlib.okHandler());
        }

        break;
      }
      case Intents.ACTIVITY_RESULT_GALLERY: {
        Uri image = data.getData();
        if (image == null) break;
        final Uri path = data.getData();
        String imagePath = U.tryResolveFilePath(path);

        if (imagePath == null) break;

        if (imagePath.endsWith(".webp")) {
          UI.showToast("Webp is not supported for profile photos", Toast.LENGTH_LONG);
          break;
        }

        UI.showToast(R.string.UploadingPhotoWait, Toast.LENGTH_SHORT);
        tdlib.client().send(new TdApi.SetChatPhoto(chat.id, new TdApi.InputChatPhotoStatic(new TdApi.InputFileGenerated(imagePath, SimpleGenerationInfo.makeConversion(imagePath), 0))), tdlib.okHandler());

        break;
      }
    }
  }

  private void changeProfilePhoto () {
    IntList ids = new IntList(4);
    StringList strings = new StringList(4);
    IntList colors = new IntList(4);
    IntList icons = new IntList(4);

    if (chat != null && chat.photo != null && !isEditing()) {
      ids.append(R.id.btn_open);
      strings.append(R.string.Open);
      icons.append(R.drawable.baseline_visibility_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    ids.append(R.id.btn_changePhotoCamera);
    strings.append(R.string.ChatCamera);
    icons.append(R.drawable.baseline_camera_alt_24);
    colors.append(OPTION_COLOR_NORMAL);

    ids.append(R.id.btn_changePhotoGallery);
    strings.append(R.string.Gallery);
    icons.append(R.drawable.baseline_image_24);
    colors.append(OPTION_COLOR_NORMAL);

    if (chat != null && chat.photo != null) {
      ids.append(R.id.btn_changePhotoDelete);
      strings.append(R.string.Delete);
      icons.append(R.drawable.baseline_delete_24);
      colors.append(OPTION_COLOR_RED);
    }

    showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_open) {
        openPhoto();
      } else if (id == R.id.btn_changePhotoCamera) {
        UI.openCameraDelayed(context);
      } else if (id == R.id.btn_changePhotoGallery) {
        UI.openGalleryDelayed(context, false);
      } else if (id == R.id.btn_changePhotoDelete) {
        tdlib.client().send(new TdApi.SetChatPhoto(chat.id, null), tdlib.okHandler());
      }
      return true;
    });
  }

  @Override
  public void performComplexPhotoOpen () {
    if (tdlib.canChangeInfo(chat)) {
      changeProfilePhoto();
    } else {
      openPhoto();
    }
  }

  private void openPhoto () {
    if (chat.photo != null && !TD.isFileEmpty(chat.photo.small)) {
      MediaViewController.openFromChat(this, chat, headerCell);
    }
  }

  private boolean hasMoreItems () {
    return (canDestroyChat() || canClearHistory()) && !tdlib.isUserChat(chat);
  }

  private boolean canDestroyChat () {
    return chat != null && chat.canBeDeletedForAllUsers;
  }

  private boolean canClearHistory () {
    return isChannel() && tdlib.canClearHistory(getChatId());
  }

  private void destroyChat () {
    if (!canDestroyChat()) {
      return;
    }
    final boolean isChannel = isChannel();
    int memberCount = supergroup != null ? supergroup.memberCount : group.memberCount;
    if (tdlib.chatAvailable(chat)) {
      memberCount--;
    }
    CharSequence msg = memberCount > 0 ? Lang.plural(R.string.DestroyX, memberCount, Lang.boldCreator(), tdlib.chatTitle(getChatId())) : Lang.getStringBold(R.string.DestroyXNoMembers, tdlib.chatTitle(getChatId()));
    showOptions(msg,
      new int[] {R.id.btn_destroyChat, R.id.btn_cancel},
      new String[] {Lang.getString(isChannel ? R.string.DestroyChannel : R.string.DestroyGroup), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL},
      new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_destroyChat) {
          showOptions(Lang.getString(isChannel ? R.string.DestroyChannelHint : R.string.DestroyGroupHint),
            new int[] {R.id.btn_destroyChat, R.id.btn_cancel},
            new String[] {Lang.getString(isChannel ? R.string.DestroyChannel : R.string.DestroyGroup), Lang.getString(R.string.Cancel)},
            new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL},
            new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (resultItemView, resultId) -> {
            if (resultId == R.id.btn_destroyChat) {
              tdlib.client().send(new TdApi.DeleteChat(getChatId()), tdlib.okHandler());
              tdlib.ui().exitToChatScreen(this, getChatId());
            }
            return true;
          });
        }
        return true;
      }
    );
  }

  private @Nullable String descriptionLanguage;

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.avatar) {
      changeProfilePhoto();
    } else if (viewId == R.id.btn_done) {
      applyChatChanges();
    } else if (viewId == R.id.menu_btn_copy) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).copyMessages();
      }
    } else if (viewId == R.id.menu_btn_delete) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).deleteMessages();
      }
    } else if (viewId == R.id.menu_btn_clear) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).clearMessages();
      }
    } else if (viewId == R.id.menu_btn_view) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).viewMessages();
      }
    } else if (viewId == R.id.menu_btn_forward) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).shareMessages();
      }
    } else if (viewId == R.id.btn_phone) {
      tdlib.ui().handleProfileClick(this, v, v.getId(), user, false);
    } else if (viewId == R.id.btn_useExplicitDice) {
      Settings.instance().setNewSetting(((ListItem) v.getTag()).getLongId(), baseAdapter.toggleView(v));
    } else if (viewId == R.id.btn_username) {
      boolean canSetUsername = canSetUsername();
      boolean canInviteUsers = chat != null && tdlib.canManageInviteLinks(chat);

      int size = 3;
      if (canSetUsername) size++;
      if (canInviteUsers) size++;

      IntList ids = new IntList(size);
      StringList strings = new StringList(size);
      IntList icons = new IntList(size);

      if (canSetUsername) {
        ids.append(R.id.btn_editUsername);
        strings.append(R.string.edit);
        icons.append(R.drawable.baseline_edit_24);
      }

      if (canInviteUsers) {
        ids.append(R.id.btn_manageInviteLinks);
        strings.append(R.string.InviteLinkManage);
        icons.append(R.drawable.baseline_add_link_24);
      }

      if (canInviteUsers && chat.pendingJoinRequests != null && chat.pendingJoinRequests.totalCount > 0) {
        ids.append(R.id.btn_manageJoinRequests);
        strings.append(R.string.InviteLinkRequests);
        icons.append(R.drawable.baseline_pending_24);
      }

      ids.append(R.id.btn_copyUsername);
      strings.append(R.string.Copy);
      icons.append(R.drawable.baseline_content_copy_24);

      ids.append(R.id.btn_copyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);

      ids.append(R.id.btn_share);
      strings.append(R.string.Share);
      icons.append(R.drawable.baseline_forward_24);

      showOptions("@" + tdlib.chatUsername(chat.id), ids.get(), strings.get(), null, icons.get());
    } else if (viewId == R.id.btn_description) {
      if (canEditDescription() && !hasDescription()) {
        editDescription();
        return;
      }
      if (Settings.instance().getChatTranslateMode() != Settings.TRANSLATE_MODE_NONE) {
        LanguageDetector.detectLanguage(context, aboutWrapper.getText(), (lang) -> {
          showDescriptionOptions(true, descriptionLanguage = lang);
        }, (err) -> {
          showDescriptionOptions(true, descriptionLanguage = null);
        });
      } else {
        showDescriptionOptions(false, descriptionLanguage = null);
      }
    } else if (viewId == R.id.btn_notifications) {
      tdlib.ui().showMuteOptions(this, chat.id, true, null);
    } else if (viewId == R.id.btn_encryptionKey) {
      if (secretChat != null && secretChat.keyHash != null && secretChat.keyHash.length > 0) {
        EncryptionKeyController controller = new EncryptionKeyController(context, tdlib);
        controller.setArguments(new EncryptionKeyController.Args(user.id, secretChat.keyHash));
        navigateTo(controller);
      }
    } else if (viewId == R.id.btn_inviteLink) {
      openInviteLinkMenu();
    } else if (viewId == R.id.btn_manageInviteLinks) {
      openInviteLink();
    } else if (viewId == R.id.btn_manageJoinRequests) {
      openInviteRequestsManage();
    } else if (viewId == R.id.user) {
      TdApi.User user = (TdApi.User) v.getTag();
      if (user != null) {
        tdlib.ui().openPrivateProfile(this, user.id, null);
      }

      // EDIT stuff
    } else if (viewId == R.id.btn_prehistoryMode) {
      togglePrehistoryMode();
    } else if (viewId == R.id.btn_chatPermissions) {
      openChatPermissions();
    } else if (viewId == R.id.btn_recentActions) {
      openRecentActions();
    } else if (viewId == R.id.btn_viewStatistics) {
      openStats();
    } else if (viewId == R.id.btn_enabledReactions) {
      openEnabledReactions();
    } else if (viewId == R.id.btn_channelType) {
      editChannelUsername();
    } else if (viewId == R.id.btn_linkedChat) {
      editLinkedChat();
    } else if (viewId == R.id.btn_toggleSignatures) {
      toggleChannelSignatures(v);
    } else if (viewId == R.id.btn_toggleAggressiveAntiSpam) {
      toggleAggressiveAntiSpam(v);
    } else if (viewId == R.id.btn_toggleHideMembers) {
      toggleHideMembers(v);
    } else if (viewId == R.id.btn_toggleProtection) {
      toggleContentProtection(v);
    } else if (viewId == R.id.btn_toggleJoinByRequest) {
      toggleJoinByRequests(v);
    }
  }

  private boolean canSetUsername () {
    return (supergroup != null && TD.isCreator(supergroup.status)) || (group != null && TD.isCreator(group.status));
  }

  private TranslationControllerV2.Wrapper translationPopup;

  private void showDescriptionOptions (boolean showTranslate, String descriptionLang) {
    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    IntList icons = new IntList(2);

    if (canEditDescription()) {
      ids.append(R.id.btn_edit);
      strings.append(R.string.edit);
      icons.append(R.drawable.baseline_edit_24);
    }

    ids.append(R.id.btn_copyText);
    strings.append(R.string.Copy);
    icons.append(R.drawable.baseline_content_copy_24);

    if (showTranslate && !Settings.instance().isNotTranslatableLanguage(descriptionLang)) {
      ids.append(R.id.btn_chatTranslate);
      strings.append(Lang.getString(R.string.Translate));
      icons.append(R.drawable.baseline_translate_24);
    }

    showOptions(null, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyText) {
        copyDescription();
      } else if (id == R.id.btn_edit) {
        editDescription();
      } else if (id == R.id.btn_chatTranslate) {
        translationPopup = new TranslationControllerV2.Wrapper(context, tdlib, this);
        translationPopup.setArguments(new TranslationControllerV2.Args(new TranslationsManager.Translatable() {
          @Nullable
          @Override
          public String getOriginalMessageLanguage () {
            return descriptionLanguage;
          }

          @Override
          public TdApi.FormattedText getTextToTranslate () {
            return currentAbout;
          }
        }));
        // translationPopup.setClickCallback();
        translationPopup.setTextColorSet(TextColorSets.Regular.NORMAL);
        translationPopup.show();
        translationPopup.setDismissListener(popup -> translationPopup = null);
      }
      return true;
    });
  }

  private TdApi.ChatInviteLink getInviteLink () {
    TdApi.ChatInviteLink inviteLink;
    switch (mode) {
      case MODE_GROUP: {
        inviteLink = groupFull != null ? groupFull.inviteLink : null;
        break;
      }
      case MODE_SUPERGROUP:
      case MODE_CHANNEL: {
        inviteLink = supergroupFull != null ? supergroupFull.inviteLink : null;
        break;
      }
      default: {
        inviteLink = null;
        break;
      }
    }
    return inviteLink;
  }

  private void openInviteLink () {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    if (status == null)
      return;

    ChatLinksController c = new ChatLinksController(context, tdlib);
    c.setArguments(new ChatLinksController.Args(chat.id, tdlib.myUserId(), this, this, status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR));
    navigateTo(c);
  }

  private void openInviteRequestsManage () {
    ChatJoinRequestsController cc = new ChatJoinRequestsController(context, tdlib);
    cc.setArguments(new ChatJoinRequestsController.Args(chat.id, "", this));
    navigateTo(cc);
  }

  private void openInviteLinkMenu () {
    TdApi.ChatInviteLink link = getInviteLink();

    if (link == null) {
      openInviteLink();
      return;
    }

    StringList strings = new StringList(5);
    IntList icons = new IntList(5);
    IntList ids = new IntList(5);
    IntList colors = new IntList(5);

    ids.append(R.id.btn_manageInviteLinks);
    strings.append(R.string.InviteLinkManage);
    icons.append(R.drawable.baseline_add_link_24);
    colors.append(OPTION_COLOR_NORMAL);

    if (chat.pendingJoinRequests != null && chat.pendingJoinRequests.totalCount > 0) {
      ids.append(R.id.btn_manageJoinRequests);
      strings.append(R.string.InviteLinkRequests);
      icons.append(R.drawable.baseline_pending_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    ids.append(R.id.btn_copyLink);
    strings.append(R.string.InviteLinkCopy);
    icons.append(R.drawable.baseline_content_copy_24);
    colors.append(OPTION_COLOR_NORMAL);

    ids.append(R.id.btn_shareLink);
    strings.append(R.string.ShareLink);
    icons.append(R.drawable.baseline_forward_24);
    colors.append(OPTION_COLOR_NORMAL);

    icons.append(R.drawable.baseline_link_off_24);
    ids.append(R.id.btn_revokeLink);
    strings.append(R.string.RevokeLink);
    colors.append(OPTION_COLOR_RED);

    CharSequence info = TD.makeClickable(Lang.getString(R.string.CreatedByXOnDate, ((target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newUserSpan(new TdlibContext(context, tdlib), link.creatorUserId) : null), tdlib.cache().userName(link.creatorUserId), Lang.getRelativeTimestamp(link.date, TimeUnit.SECONDS)));
    Lang.SpanCreator firstBoldCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newBoldSpan(needFakeBold) : null;
    showOptions(Lang.getString(R.string.format_nameAndStatus, firstBoldCreator, link.inviteLink, info), ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyLink) {
        UI.copyText(link.inviteLink, R.string.CopiedLink);
      } else if (id == R.id.btn_manageJoinRequests) {
        openInviteRequestsManage();
      } else if (id == R.id.btn_shareLink) {
        String chatName = tdlib.chatTitle(chat.id);
        String exportText = Lang.getString(tdlib.isChannel(chat.id) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, link.inviteLink);
        String text = Lang.getString(R.string.ShareTextLink, chatName, link.inviteLink);
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(text).setShare(exportText, null));
        c.show();
      } else if (id == R.id.btn_revokeLink) {
        showOptions(Lang.getString(tdlib.isChannel(chat.id) ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[] {R.id.btn_revokeLink, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_link_off_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
          if (id2 == R.id.btn_revokeLink) {
            tdlib.client().send(new TdApi.RevokeChatInviteLink(chat.id, link.inviteLink), result -> {
              if (result.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
                runOnUiThreadOptional(() -> {
                  final TdApi.ChatInviteLinks newInviteLink = (TdApi.ChatInviteLinks) result;
                  TdApi.ChatInviteLink newPrimaryLink = null;

                  for (TdApi.ChatInviteLink candidate : newInviteLink.inviteLinks) {
                    if (candidate.isPrimary && !candidate.isRevoked) {
                      newPrimaryLink = candidate;
                      break;
                    }
                  }

                  if (newPrimaryLink != null) {
                    onInviteLinkChanged(newPrimaryLink);
                  }
                });
              }
            });
          }

          return true;
        });
      } else if (id == R.id.btn_manageInviteLinks) {
        openInviteLink();
      }

      return true;
    });
  }

  @Override
  public void onInviteLinkChanged (TdApi.ChatInviteLink newInviteLink) {
    switch (mode) {
      case MODE_GROUP: {
        if (groupFull != null) {
          groupFull.inviteLink = newInviteLink;
          updateValuedItem(R.id.btn_inviteLink);
        }
        break;
      }
      case MODE_CHANNEL:
      case MODE_SUPERGROUP: {
        if (supergroupFull != null) {
          supergroupFull.inviteLink = newInviteLink;
          updateValuedItem(R.id.btn_inviteLink);
        }
        break;
      }
    }
  }

  // Controller event listeners

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      oneShot = true;
      pagerAdapter.notifyDataSetChanged();
    }
    if (!isEditing()) {
      ((ComplexRecyclerView) baseRecyclerView).setFactorLocked(false);
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (translationPopup != null) {
      translationPopup.hidePopupWindow(true);
    }
    if (!isEditing()) {
      ((ComplexRecyclerView) baseRecyclerView).setFactorLocked(true);
    }
  }

  public void clearSelectMode () {
    if (inSelectMode) {
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        ((SharedBaseController<?>) c).setInMediaSelectMode(false);
      }
    }
  }

  @Override
  public boolean closeSearchModeByBackPress (boolean fromTop) {
    clearSearchInput();
    return false;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (isTransforming() || (expandAnimator != null && expandAnimator.isAnimating())) {
      return true;
    }
    if (inSelectMode) {
      clearSelectMode();
      return true;
    }
    if (isEditing() && hasUnsavedChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }

    return false;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (!isEditing() && baseRecyclerView != null) {
      ((ComplexRecyclerView) baseRecyclerView).rebuildTop();
    }
  }

  // Data: header

  private void setHeaderMute (boolean isUpdate) {
    if (headerCell != null) {
      headerCell.setShowMute(tdlib.chatNeedsMuteIcon(chat.id));
      if (!isUpdate) {
        headerCell.setShowVerify(tdlib.chatVerified(chat));
        headerCell.setShowScam(tdlib.chatScam(chat));
        headerCell.setShowFake(tdlib.chatFake(chat));
      }
    }
  }

  private void setHeaderText () {
    if (headerCell != null && chat != null) {
      headerCell.setUseRedHighlight(tdlib.isRedTeam(chat.id));
      headerCell.setText(tdlib.chatTitle(chat, false), makeSubtitle(false));
      headerCell.setEmojiStatus(tdlib.chatUser(chat));
      headerCell.setExpandedSubtitle(makeSubtitle(true));
    }
  }

  private void setHeaderPhoto (boolean update) {
    if (headerCell != null) {
      headerCell.getAvatarReceiver().requestMessageSender(tdlib, tdlib.sender(getChatId()), AvatarReceiver.Options.FULL_SIZE | AvatarReceiver.Options.FORCE_ANIMATION);
    }
  }

  private void updateHeader (boolean updatePhoto) {
    if (headerCell != null) {
      setHeaderText();
      if (updatePhoto) {
        setHeaderPhoto(true);
      }
      headerCell.invalidate();
    }
  }

  private void updateAvailableReactions () {
    updateValuedItem(R.id.btn_enabledReactions);
  }

  // ViewPager

  private int currentMediaPosition;
  private float currentPositionOffset;
  private int checkedPosition = -1, checkedBasePosition = -1;

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    if (this.checkedBasePosition != position) {
      checkedBasePosition = position;
      checkContentScrollY(position);
    }
    if (positionOffset == 0f) {
      checkedPosition = -1;
    } else {
      eventsBelongToSlider = false;
    }
    if (positionOffset != 0f) {
      int checkPosition = position + 1;
      if (this.checkedPosition != checkPosition) {
        this.checkedPosition = checkPosition;
        checkContentScrollY(checkPosition);
      }
    }
    currentMediaPosition = position;
    currentPositionOffset = positionOffset;
    topCellView.getTopView().setSelectionFactor(position + positionOffset);
    setIgnoreAnyPagerScrollEventsBecauseOfMovements(positionOffset != 0f);
  }

  private void invalidateCachedPosition () {
    checkedPosition = -1;
    checkedBasePosition = -1;
  }

  @Override
  public void onPageSelected (int position) {
    if (headerView != null) {
      headerView.updateCustomButtons(this, getMenuId());
    }
    updateMediaSubtitle();
  }

  @Override
  protected void updateCustomMenu (int menuId, LinearLayout menu) {
    ViewController<?> c = findCurrentCachedController();
    ViewGroup wrapper = (ViewGroup) menu.getChildAt(0);
    if (wrapper == null) {
      return;
    }
    LinearLayout realMenu = (LinearLayout) wrapper.getChildAt(0);
    if (realMenu != null) {
      final int currentMenuId = getMenuId();
      if (currentMenuId == R.id.menu_profile) {
        View view = realMenu.findViewById(R.id.menu_btn_manage);
        if (view != null) {
          view.setVisibility(canManageChat() ? View.VISIBLE : View.GONE);
        }
        view = realMenu.findViewById(R.id.menu_btn_addContact);
        if (view != null) {
          view.setVisibility(canAddAnyKindOfMembers() ? View.VISIBLE : View.GONE);
        }
      } else if (currentMenuId == R.id.menu_profile_manage) {
        View view = realMenu.findViewById(R.id.menu_btn_more);
        if (view != null) {
          view.setVisibility(hasMoreItems() ? View.VISIBLE : View.GONE);
        }
      }
    }
    LinearLayout transformedMenu = (LinearLayout) wrapper.getChildAt(1);
    if (transformedMenu != null) {
      View searchButton = transformedMenu.getChildAt(0);
      if (searchButton != null) {
        searchButton.setVisibility(c != null && ((SharedBaseController<?>) c).canSearch() ? View.VISIBLE : View.INVISIBLE);
      }
    }
    updateHeaderMargin();
  }

  @Override
  public void onPageScrollStateChanged (int state) {

  }

  @Override
  public void onPagerItemClick (int index) {
    invalidateCachedPosition();
    if (pager.getCurrentItem() != index) {
      topCellView.getTopView().setFromTo(pager.getCurrentItem(), index);
      checkContentScrollY(checkedBasePosition = index);
      pager.setCurrentItem(index, true);
    }
  }

  // CanSlideBack

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (isTransforming() || (expandAnimator != null && expandAnimator.isAnimating())) {
      return false;
    }
    if (isEditing() && hasUnsavedChanges()) {
      return false;
    }
    y -= HeaderView.getSize(true);
    float pagerTop = topWrap.getTranslationY() + getTopViewTopPadding();
    if (y >= pagerTop && y <= pagerTop + getPagerTopViewHeight() && topWrap != null && topWrap.getParent() != null) {
      return (currentMediaPosition == 0 && currentPositionOffset == 0f && !topCellView.canScrollLeft()) || x <= Screen.dp(12f);
    }
    if (belongsToBaseRecycler(contentView.getMeasuredWidth(), y)) {
      View view = baseRecyclerView.findChildViewUnder(x, y);
      if (view instanceof RecyclerView && ((RecyclerView) view).getLayoutManager() instanceof LinearLayoutManager) {
        LinearLayoutManager manager = (LinearLayoutManager) ((RecyclerView) view).getLayoutManager();
        if (manager.getOrientation() == LinearLayoutManager.HORIZONTAL) {
          int i = manager.findFirstVisibleItemPosition();
          if (i == 0) {
            View childView = manager.findViewByPosition(0);
            return childView != null && manager.getDecoratedLeft(childView) == 0;
          }
          return false;
        }
      }
      return true;
    } else {
      return (currentMediaPosition == 0 && currentPositionOffset == 0f) || x <= Screen.dp(12f);
    }
  }

  // Helpers

  private HashMap<CharSequence, int[]> textCache;
  private View fakeChannelDesc;
  private int lastUsernameWidth, lastUsernameAddHeight;
  private CharSequence lastUsernames;

  private int calculateHeight (int position, int width, ListItem item) {
    final int itemId = item.getId();
    switch (item.getViewType()) {
      case ListItem.TYPE_INFO_MULTILINE: {
        if (itemId == R.id.btn_username) {
          int availWidth = width - Screen.dp(73f) - Screen.dp(17f);
          if (availWidth <= 0) {
            lastUsernameAddHeight = 0;
            lastUsernameWidth = 0;
          } else {
            CharSequence sequence = getUsernameName();
            if (!StringUtils.equalsOrBothEmpty(sequence, lastUsernames) || lastUsernameWidth != availWidth) {
              Text text = new Text.Builder(tdlib, sequence, null, availWidth, Paints.robotoStyleProvider(13f), TextColorSets.Regular.NORMAL, null).build();
              lastUsernameAddHeight = text.getHeight() - text.getLineHeight();
              lastUsernameWidth = availWidth;
              lastUsernames = sequence;
            }
          }
          return lastUsernameAddHeight + Screen.dp(76f);
        } else if (itemId == R.id.btn_description) {
          if (aboutWrapper != null) {
            aboutWrapper.get(getTextWidth(width));
            return Math.max(aboutWrapper.getHeight() + Screen.dp(21f + 13f) - Screen.dp(13f) + Screen.dp(12f) + Screen.dp(25), Screen.dp(76f));
          }
          return Screen.dp(76f);
        }
        throw new UnsupportedOperationException();
      }
      case ListItem.TYPE_DESCRIPTION: {
        if (textCache == null) {
          textCache = new HashMap<>();
        }
        return U.calculateTextHeight(item.getString(), width - Screen.dp(16f) * 2, 15f, textCache) + Screen.dp(6f) + Screen.dp(12f);
      }
      case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION: {
        int viewHeight;
        if (fakeChannelDesc != null) {
          viewHeight = fakeChannelDesc.getMeasuredHeight();
        } else {
          viewHeight = Screen.dp(60f) + Screen.dp(20f);
        }
        return viewHeight;
      }
    }
    return SettingHolder.measureHeightForType(item.getViewType());
  }

  private int cachedItemsWidth, cachedItemsHeight;

  private int calculateTopItemsHeight (int width) {
    if (cachedItemsWidth == width && cachedItemsHeight > 0) {
      return cachedItemsHeight;
    }
    int height = 0;
    List<ListItem> items = baseAdapter.getItems();
    int i = 0;
    for (ListItem item : items) {
      height += calculateHeight(i, width, item);
      i++;
    }
    cachedItemsWidth = width;
    cachedItemsHeight = height;
    return height;
  }

  private static int getPagerTopViewHeight () {
    return SettingHolder.measureHeightForType(ListItem.TYPE_FAKE_PAGER_TOPVIEW);
  }

  private static int getHiddenContentHeight () {
    return SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP) + SettingHolder.measureHeightForType(ListItem.TYPE_HEADER);
  }

  private static int getShadowBottomHeight () {
    return SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_BOTTOM);
  }

  private int getTopItemsHeight () {
    return calculateTopItemsHeight(baseRecyclerView.getMeasuredWidth());
  }

  private ViewController<?> findCurrentCachedController () {
    return pagerAdapter.findCachedControllerByPosition(pager.getCurrentItem());
  }

  private int getContentItemsHeight () {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      return ((SharedBaseController<?>) c).calculateItemsHeight();
    }
    return 0;
  }

  private int calculateCachedContentScrollY () {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      return ((SharedBaseController<?>) c).calculateScrollY();
    }
    return 0;
  }

  private void stopCachedScrolls () {
    for (int i = 0; i < pagerAdapter.getCount(); i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).stopScroll();
      }
    }
  }

  private boolean belongsToBaseRecycler (int width, float y) {
    int recyclerBottom = 0;

    LinearLayoutManager manager = (LinearLayoutManager) baseRecyclerView.getLayoutManager();
    int firstVisible = manager.findFirstVisibleItemPosition();
    List<ListItem> items = baseAdapter.getItems();
    final int ignoreIndex = items.size() - 1;
    int i = 0;

    for (ListItem item : items) {
      if (i >= firstVisible) {
        if (i == firstVisible) {
          View view = manager.findViewByPosition(i);
          if (view != null) {
            recyclerBottom += view.getTop();
          }
        }
        if (i != ignoreIndex) {
          recyclerBottom += calculateHeight(i, width, item);
        }
      }
      i++;
    }

    return y <= recyclerBottom;
  }

  private void onItemsHeightProbablyChanged () {
    cachedItemsWidth = cachedItemsHeight = 0;
    for (int i = 0; i < pagerAdapter.getCount(); i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).onItemsHeightProbablyChanged();
      }
    }
    checkTopViewPosition();
  }

  private void onGlobalHeightChanged () {
    if (baseAdapter.indexOfViewById(R.id.btn_description) != -1 || baseAdapter.indexOfViewById(R.id.description) != -1) {
      onItemsHeightProbablyChanged();
    }
    baseRecyclerView.invalidateItemDecorations();
    for (int i = 0; i < pagerAdapter.getCount(); i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).onGlobalHeightChanged();
      }
    }
  }

  private boolean eventsBelongToPager;

  private void setEventsBelongToPager (boolean belongToPager) {
    this.eventsBelongToPager = belongToPager;
  }

  private boolean ignoreAnyPagerScrollEvents;

  public void setIgnoreAnyPagerScrollEvents (boolean ignore) {
    this.ignoreAnyPagerScrollEvents = ignore;
  }

  private boolean ignoreAnyPagerScrollEventsBecauseOfMovements;

  public void setIgnoreAnyPagerScrollEventsBecauseOfMovements (boolean ignore) {
    this.ignoreAnyPagerScrollEventsBecauseOfMovements = ignore;
  }

  public void addOnScrollListener (RecyclerView recyclerView) {
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (eventsBelongToPager && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          baseRecyclerView.stopScroll();
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy != 0) {
          hideSoftwareKeyboard();
          eventsBelongToSlider = false;
        }
        if (eventsBelongToPager && !ignoreAnyPagerScrollEvents && !ignoreAnyPagerScrollEventsBecauseOfMovements && dy != 0) {
          baseRecyclerView.stopScroll();
          if (currentPositionOffset == 0f) {
            int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            int contentMaxTop = contentTop();
            if (i == 0) {
              View view = recyclerView.getLayoutManager().findViewByPosition(0);
              checkBaseScrollY(view != null ? contentMaxTop - view.getTop() : 0);
            } else {
              checkBaseScrollY(contentMaxTop);
            }
          }
        }
      }
    });
  }

  private void scrollPagerRecyclerBy (int dy) {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      RecyclerView recyclerView = (RecyclerView) c.getValue();
      recyclerView.scrollBy(0, dy);
    }
  }

  private void dispatchPagerRecyclerEvent (MotionEvent e) {
    ViewController<?> c = findCurrentCachedController();
    if (c != null) {
      c.getValue().dispatchTouchEvent(e);
    }
  }

  private int calculateBaseScrollY () {
    int width = baseRecyclerView.getMeasuredWidth();
    int maxPossibleScroll = maxItemsScrollY();

    int firstVisibleItemPosition = ((LinearLayoutManager) baseRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();

    if (firstVisibleItemPosition >= baseAdapter.getItems().size() - 2) { // exclude bottom shadow and pager top view
      return maxPossibleScroll;
    }

    int scrollY = 0;

    View view = baseRecyclerView.getLayoutManager().findViewByPosition(firstVisibleItemPosition);

    if (view != null) {
      scrollY -= view.getTop();
    }
    for (int j = firstVisibleItemPosition - 1; j >= 0; j--) {
      scrollY += calculateHeight(j, width, baseAdapter.getItems().get(j));
    }
    return scrollY;
  }

  private void checkBaseScrollY (int totalScrollY) {
    totalScrollY = Math.min(maxItemsScrollY(), totalScrollY);
    int scrollY = calculateBaseScrollY();
    if (scrollY != totalScrollY) {
      baseRecyclerView.scrollBy(0, totalScrollY - scrollY);
    }
  }


  private void checkContentFrozen () {

    // baseRecyclerView.setLayoutFrozen();
  }

  private boolean isContentFrozen () {
    return touchingMode == TOUCH_MODE_BASE_RECYCLER && baseScrollState == RecyclerView.SCROLL_STATE_IDLE;
  }

  private void checkContentScrollY (int position) {
    checkContentScrollY(pagerAdapter.findCachedControllerByPosition(position));
  }

  public void checkContentScrollY (ViewController<?> c) {
    int maxScrollY = maxItemsScrollYOffset();
    int scrollY = calculateBaseScrollY();
    if (c != null) {
      ((SharedBaseController<?>) c).ensureMaxScrollY(scrollY, maxScrollY);
    }
  }

  private int maxItemsScrollY () {
    return getTopItemsHeight() - getShadowBottomHeight() - getPagerTopViewHeight();
  }

  public int maxItemsScrollYOffset () {
    return maxItemsScrollY() - getPagerTopViewHeight() - getTopViewTopPadding();
  }

  public int getItemsBound () {
    return getPagerTopViewHeight() + getTopViewTopPadding() + Screen.dp(6f);
  }

  private int contentTop () {
    return getTopItemsHeight() - getPagerTopViewHeight() + getTopViewTopPadding() - ShadowView.simpleTopShadowHeight();
  }

  // Shared stuff

  @Override
  public MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    if (currentPositionOffset != 0f) {
      return null;
    }
    ViewController<?> c = pagerAdapter.findCachedControllerByPosition(currentMediaPosition);
    if (c instanceof MediaCollectorDelegate) {
      return ((MediaCollectorDelegate) c).collectMedias(fromMessageId, filter);
    }
    return null;
  }

  @Override
  public void modifyMediaArguments (Object cause, MediaViewController.Args args) {
    if (currentPositionOffset != 0f) {
      return;
    }
    ViewController<?> c = pagerAdapter.findCachedControllerByPosition(currentMediaPosition);
    if (c instanceof MediaCollectorDelegate) {
      ((MediaCollectorDelegate) c).modifyMediaArguments(cause, args);
    }
  }

  @Override
  public int getControllerCount () {
    int itemCount = getControllers().size();
    return Math.min(itemCount, oneShot ? itemCount : 1);
  }

  private final ArrayList<SharedBaseController<?>> controllers = new ArrayList<>(6);

  /*private int[] getPagerIcons () {
    ArrayList<SharedBaseController> controllers = getControllers();
    int[] icons = new int[controllers.size()];
    int i = 0;
    for (SharedBaseController c : controllers) {
      icons[i++] = c.getIcon();
    }
    return icons;
  }*/

  private String[] getPagerTitles () {
    ArrayList<SharedBaseController<?>> controllers = getControllers();
    String[] strings = new String[controllers.size()];
    int i = 0;
    for (SharedBaseController<?> c : controllers) {
      strings[i++] = c.getName().toString().toUpperCase();
    }
    return strings;
  }

  private ArrayList<SharedBaseController<?>> getControllers () {
    if (controllers.isEmpty()) {
      switch (mode) {
        case MODE_EDIT_CHANNEL: {
          controllers.add(new SharedMembersController(context, tdlib)
            .setSpecificFilter(new TdApi.SupergroupMembersFilterAdministrators()));
          controllers.add(new SharedMembersController(context, tdlib)
            .setSpecificFilter(new TdApi.SupergroupMembersFilterBanned()));
          TdApi.SupergroupFullInfo supergroupFull = tdlib.cache().supergroupFull(supergroup.id);
          if (supergroupFull != null && supergroupFull.canGetMembers) {
            controllers.add(new SharedMembersController(context, tdlib)
              .setSpecificFilter(new TdApi.SupergroupMembersFilterRecent()));
          }
          break;
        }
        case MODE_EDIT_SUPERGROUP:
        case MODE_EDIT_GROUP: {
          controllers.add(new SharedMembersController(context, tdlib)
            .setSpecificFilter(new TdApi.SupergroupMembersFilterAdministrators()));
          if (mode == MODE_EDIT_SUPERGROUP ? TD.isAdmin(supergroup.status) : TD.isCreator(group.status)) {
            controllers.add(new SharedMembersController(context, tdlib)
              .setSpecificFilter(new TdApi.SupergroupMembersFilterRestricted()));
            controllers.add(new SharedMembersController(context, tdlib)
              .setSpecificFilter(new TdApi.SupergroupMembersFilterBanned()));
          }
          break;
        }
        default: {
          String restrictionReason = tdlib.chatRestrictionReason(chat);

          if (mode == MODE_GROUP) {
            controllers.add(new SharedMembersController(context, tdlib));
          } else if (mode == MODE_SUPERGROUP) {
            TdApi.SupergroupFullInfo supergroupFull = tdlib.cache().supergroupFull(supergroup.id);
            if (supergroupFull != null && supergroupFull.canGetMembers) {
              SharedMembersController c = new SharedMembersController(context, tdlib);
              if (supergroupFull.hasHiddenMembers) {
                TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
                if (!TD.isAdmin(status)) {
                  c.setForceAdmins(true);
                }
              }
              controllers.add(c);
            }
          }

          if (restrictionReason == null) {
            if (Config.HIDE_EMPTY_TABS) {
              int syncTabCount = SYNC_TAB_COUNT;
              TdApi.SearchMessagesFilter[] filters = getFiltersOrder();
              buildingTabsCount = filters.length - syncTabCount;
              for (TdApi.SearchMessagesFilter filter : filters) {
                if (isSyncTab(filter)) {
                  controllers.add(SharedBaseController.valueOf(context, tdlib, filter));
                  syncTabCount--;
                } else {
                  getMessageCount(filter, true);
                }
              }
              for (TdApi.SearchMessagesFilter filter : filters) {
                if (isSyncTab(filter)) {
                  getMessageCount(filter, true);
                }
              }
            } else {
              fillMediaControllers(controllers, context, tdlib);
            }
          } else {
            controllers.add(new SharedRestrictionController(context, tdlib).setRestrictionReason(restrictionReason));
          }

          switch (mode) {
            case MODE_USER:
            case MODE_SECRET:
              TdApi.UserFullInfo userFull = tdlib.cache().userFull(user.id);
              if (userFull != null && userFull.groupInCommonCount > 0) {
                controllers.add(new SharedChatsController(context, tdlib));
              }
              break;
            case MODE_CHANNEL:
              // Nothing?
              break;
          }

          break;
        }
      }
      for (ViewController<?> controller : controllers) {
        registerController(controller);
      }
    }
    return controllers;
  }

  private void registerController (ViewController<?> controller) {
    controller.setParentWrapper(this);
    controller.bindThemeListeners(this);
  }

  private void unregisterController (ViewController<?> controller) {
    // TODO
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return buildingTabsCount > 0;
  }

  private void getMessageCount (TdApi.SearchMessagesFilter filter, boolean returnLocal) {
    tdlib.send(new TdApi.GetChatMessageCount(getChatId(), filter, returnLocal), (messageCount, error) -> {
      int count;
      if (error != null) {
        Log.e("TDLib error getMessageCount chatId:%d, filter:%s, returnLocal:%b: %s", getChatId(), filter, returnLocal, TD.toErrorString(error));
        count = -1;
      } else {
        count = messageCount.count;
      }
      if (returnLocal) {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setMediaCount(filter, count);
            if (!isSyncTab(filter) && --buildingTabsCount == 0)
              executeScheduledAnimation();
          }
        });
        if (count == -1) {
          getMessageCount(filter, false);
        }
      } else {
        runOnUiThreadOptional(() -> {
          setMediaCount(filter, count);
        });
      }
    });
  }

  private SparseIntArray mediaCounters;

  private int getMediaCount (@TdApi.SearchMessagesFilter.Constructors int filterConstructor) {
    return mediaCounters != null ? mediaCounters.get(filterConstructor, -1) : -1;
  }

  private void increaseMediaCount (@NonNull TdApi.SearchMessagesFilter filter) {
    int mediaCount = getMediaCount(filter.getConstructor());
    if (mediaCount <= -1) {
      getMessageCount(filter, false);
    } else {
      mediaCounters.put(filter.getConstructor(), ++mediaCount);
      if (!isSyncTab(filter) && mediaCount == 1) {
        addMediaTab(filter);
      }
      updateMediaSubtitle();
    }
  }

  private void refreshCounters () {
    if (mediaCounters != null) {
      for (int i = 0; i < mediaCounters.size(); i++) {
        getMessageCount(Td.constructSearchMessagesFilter(mediaCounters.keyAt(i)), true);
      }
    }
  }

  private void setMediaCount (TdApi.SearchMessagesFilter filter, int count) {
    if (count == -1) {
      return;
    }
    int oldMediaCount = getMediaCount(filter.getConstructor());
    if (oldMediaCount == count || oldMediaCount > 0 && count <= 0) {
      return;
    }
    if (mediaCounters == null) {
      mediaCounters = new SparseIntArray(getFiltersOrder().length - SYNC_TAB_COUNT);
    }
    mediaCounters.put(filter.getConstructor(), count);
    if (!isSyncTab(filter) && oldMediaCount <= 0 && count > 0) {
      addMediaTab(filter);
    }
    updateMediaSubtitle();
  }

  private void addMediaTab (TdApi.SearchMessagesFilter filter) {
    TdApi.SearchMessagesFilter[] filters = getFiltersOrder();
    int visualIndex = 0;
    int count = controllers.size();
    while (visualIndex < count && !SharedBaseController.isMediaController(controllers.get(visualIndex))) {
      visualIndex++;
    }
    if (visualIndex < count) {
      for (TdApi.SearchMessagesFilter lookupFilter : filters) {
        if (lookupFilter.getConstructor() == filter.getConstructor()) {
          break;
        }
        if (hasTab(lookupFilter, visualIndex)) {
          visualIndex++;
        }
      }
    }
    boolean append = visualIndex == count;
    if (append) {
      SharedBaseController<?> c = SharedBaseController.valueOf(context, tdlib, filter);
      controllers.add(c);
      registerController(c);
      pagerAdapter.notifyItemInserted(controllers.size() - 1);
      topCellView.getTopView().addItem(c.getName().toString().toUpperCase());
    } else {
      SharedBaseController<?> c = controllers.get(visualIndex);
      if (SharedBaseController.isMediaController(c) && c.provideSearchFilter().getConstructor() == filter.getConstructor()) {
        return;
      }
      c = SharedBaseController.valueOf(context, tdlib, filter);
      controllers.add(visualIndex, c);
      registerController(c);
      pagerAdapter.notifyItemInserted(visualIndex);
      topCellView.getTopView().addItemAtIndex(c.getName().toString().toUpperCase(), visualIndex);
    }
    pagerAdapter.notifyDataSetChanged();
  }

  private boolean hasTab (TdApi.SearchMessagesFilter filter, int indexGuess) {
    if (controllers.isEmpty()) {
      return false;
    }
    if (indexGuess < 0)
      indexGuess = 0;
    int count = controllers.size();
    for (int i = indexGuess; i < count; i++) {
      SharedBaseController<?> c = controllers.get(i);
      if (SharedBaseController.isMediaController(c)) {
        TdApi.SearchMessagesFilter currentFilter = c.provideSearchFilter();
        if (filter.getConstructor() == currentFilter.getConstructor()) {
          return true;
        }
      }
    }
    return indexGuess > 0 && hasTab(filter, 0);
  }

  public static final int SYNC_TAB_COUNT = 3;
  private static TdApi.SearchMessagesFilter[] filtersOrder, filtersOrder2;

  private static boolean isSyncTab (TdApi.SearchMessagesFilter filter) {
    //noinspection SwitchIntDef
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public static TdApi.SearchMessagesFilter[] getFiltersOrder () {
    boolean separate = Settings.instance().needSeparateMediaTab();
    TdApi.SearchMessagesFilter[] filters = separate ? filtersOrder2 : filtersOrder;
    if (filters != null)
      return filters;
    if (separate) {
      return (filtersOrder2 = new TdApi.SearchMessagesFilter[]{
        new TdApi.SearchMessagesFilterPhoto(),
        new TdApi.SearchMessagesFilterVideo(),
        new TdApi.SearchMessagesFilterDocument(),
        new TdApi.SearchMessagesFilterUrl(),
        new TdApi.SearchMessagesFilterAudio(),
        new TdApi.SearchMessagesFilterAnimation(),
        new TdApi.SearchMessagesFilterVoiceNote(),
        new TdApi.SearchMessagesFilterVideoNote()
      });
    } else {
      return (filtersOrder = new TdApi.SearchMessagesFilter[]{
        new TdApi.SearchMessagesFilterPhotoAndVideo(),
        new TdApi.SearchMessagesFilterDocument(),
        new TdApi.SearchMessagesFilterUrl(),
        new TdApi.SearchMessagesFilterAudio(),
        new TdApi.SearchMessagesFilterAnimation(),
        new TdApi.SearchMessagesFilterVoiceNote(),
        new TdApi.SearchMessagesFilterVideoNote()
      });
    }
  }

  private int buildingTabsCount;

  public static void fillMediaControllers (List<SharedBaseController<?>> controllers, BaseActivity context, Tdlib tdlib) {
    TdApi.SearchMessagesFilter[] filters = getFiltersOrder();
    ArrayUtils.ensureCapacity(controllers, controllers.size() + filters.length);
    for (TdApi.SearchMessagesFilter filter : filters) {
      controllers.add(SharedBaseController.valueOf(context, tdlib, filter));
    }
  }

  @Override
  public ViewController<?> createControllerForPosition (int position) {
    SharedBaseController<?> controller = getControllers().get(position);
    if (!controller.isPrepared()) {
      controller.setPrepared();
      controller.setArguments(new SharedBaseController.Args(chat.id, messageThread != null ? messageThread.getMessageThreadId() : 0));
      controller.setParent(this);
    }
    return controller;
  }

  @Override
  public void onPrepareToShow (int position, ViewController<?> controller) {
    super.onPrepareToShow();
    if (baseRecyclerView.getMeasuredWidth() != 0) {
      checkContentScrollY(controller);
    }
  }

  @Override
  public void onAfterHide (int position, ViewController<?> controller) {
  }

  // Manage chat

  private void manageChat () {
    if (supergroupFull != null || groupFull != null) {
      ProfileController controller = new ProfileController(context, tdlib);
      controller.setArguments(new Args(chat, messageThread, true));
      navigateTo(controller);
    }
  }

  // Updates

  private void subscribeToUpdates () {
    if (!isEditing()) {
      tdlib.listeners().subscribeToMessageUpdates(chat.id, this);
      tdlib.listeners().subscribeToSettingsUpdates(this);
    }

    tdlib.listeners().subscribeToChatUpdates(chat.id, this);

    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        tdlib.cache().subscribeToUserUpdates(user.id, this);
        if (mode == MODE_SECRET) {
          tdlib.cache().subscribeToSecretChatUpdates(secretChat.id, this);
        }
        break;
      }
      case MODE_GROUP:
      case MODE_EDIT_GROUP: {
        tdlib.cache().subscribeToGroupUpdates(group.id, this);
        break;
      }
      case MODE_SUPERGROUP:
      case MODE_CHANNEL:
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP: {
        tdlib.cache().subscribeToSupergroupUpdates(supergroup.id, this);
        break;
      }
    }
  }

  private void unsubscribeFromUpdates () {
    if (!isEditing()) {
      tdlib.listeners().unsubscribeFromMessageUpdates(chat.id, this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(this);
    }

    tdlib.listeners().unsubscribeFromChatUpdates(chat.id, this);
    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        tdlib.cache().unsubscribeFromUserUpdates(user.id, this);
        if (mode == MODE_SECRET) {
          tdlib.cache().unsubscribeFromSecretChatUpdates(secretChat.id, this);
        }
        break;
      }
      case MODE_GROUP:
      case MODE_EDIT_GROUP: {
        tdlib.cache().unsubscribeFromGroupUpdates(group.id, this);
        break;
      }
      case MODE_CHANNEL:
      case MODE_SUPERGROUP:
      case MODE_EDIT_CHANNEL:
      case MODE_EDIT_SUPERGROUP: {
        tdlib.cache().unsubscribeFromSupergroupUpdates(supergroup.id, this);
        break;
      }
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    if (isUserMode() && this.user != null && this.user.id == user.id) {
      runOnUiThreadOptional(() -> {
        ProfileController.this.user = user;
        updateHeader(true);
        checkUsername();
        checkPhone();
      });
    }
  }

  private void processUserFull (final TdApi.UserFullInfo userFull) {
    if (userFull == null) {
      return;
    }
    final boolean isUpdate = this.userFull != null;
    this.userFull = userFull;

    if (!isUpdate) {
      prepareFullCells(userFull);
    }

    tdlib.uiExecute(() -> {
      if (isUpdate) {
        checkUserButtons();
        checkGroupsInCommon();
      } else {
        addFullCells(userFull);
      }
    });
  }

  @Override
  public void onUserFullUpdated (final long userId, TdApi.UserFullInfo userFull) {
    processUserFull(userFull);
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @UiThread
  @Override
  public void onUserStatusChanged (final long userId, TdApi.UserStatus status, boolean uiOnly) {
    switch (mode) {
      case MODE_USER:
      case MODE_SECRET: {
        if (headerCell != null) {
          headerCell.setSubtitle(makeSubtitle(false));
          headerCell.setExpandedSubtitle(makeSubtitle(true));
        }
        break;
      }
    }
  }

  private void updateMediaSubtitle () {
    if (headerCell != null && needMediaSubtitle) {
      headerCell.setSubtitle(makeSubtitle(false));
      headerCell.setExpandedSubtitle(makeSubtitle(true));
    }
  }

  private CharSequence makeSubtitle (boolean isExpanded) {
    if (needMediaSubtitle) {
      if (isExpanded) {
        return null;
      }
      ViewController<?> c = findCurrentCachedController();
      if (c != null) {
        if (c instanceof SharedChatsController) {
          if (userFull != null && userFull.groupInCommonCount > 0) {
            return Lang.pluralBold(R.string.xGroups, userFull.groupInCommonCount);
          }
        } else if (c instanceof SharedRestrictionController) {
          return Lang.getString(R.string.MediaRestricted);
        } else if (c instanceof SharedCommonController || c instanceof SharedMediaController) {
          TdApi.SearchMessagesFilter filter = ((SharedBaseController<?>) c).provideSearchFilter();
          if (filter != null) {
            int count = getMediaCount(filter.getConstructor());
            if (count == 0) {
              switch (filter.getConstructor()) {
                case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
                  return Lang.getString(R.string.TabEmptyPhotos);
                case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
                  return Lang.getString(R.string.TabEmptyDocs);
                case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
                  return Lang.getString(R.string.TabEmptyLinks);
                case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
                case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
                case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
                case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
                case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
                case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
                  return Lang.getString(R.string.TabEmptyMedias);
              }
            } else if (count > 0) {
              switch (filter.getConstructor()) {
                case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xGIFs, count);
                case TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xPhotos, count);
                case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xAudios, count);
                case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xFiles, count);
                case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xMedia, count);
                case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xLinks, count);
                case TdApi.SearchMessagesFilterVideo.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xVideos, count);
                case TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xVideoMessages, count);
                case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
                  return Lang.pluralBold(R.string.xVoiceMessages, count);
              }
            }
          }
        }
      }
    }
    if (isExpanded) {
      return tdlib.status().chatStatusExpanded(chat);
    } else {
      return tdlib.status().chatStatus(chat);
    }
  }

  // Secret chats

  @Override
  public void onChatMessageTtlSettingChanged (long chatId, int messageTtlSetting) {
    tdlib.ui().post(() -> {
      if (chat != null && chat.id == chatId) {
        final boolean res = tdlib.ui().updateTTLButton(getMenuId(), headerView, chat, false);
        if (headerView != null) {
          headerView.updateButton(getMenuId(), R.id.menu_btn_call, data -> data.setTranslationX(res ? 0f : Screen.dp(StopwatchHeaderButton.WIDTH)));
        }
      }
    });
  }

  @Override
  public void onSecretChatUpdated (final TdApi.SecretChat newSecretChat) {
    tdlib.ui().post(() -> {
      if (chat != null && secretChat != null && secretChat.id == newSecretChat.id) {
        secretChat = newSecretChat;
        checkEncryptionKey();
      }
    });
  }

  // Groups

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    if (mode == MODE_GROUP || mode == MODE_EDIT_GROUP) {
      runOnUiThreadOptional(() -> {
        if (ProfileController.this.group != null && ProfileController.this.group.id == basicGroup.id) {
          setHeaderText();
          if (migratedToSupergroup) {
            replaceWithSupergroup(basicGroup.upgradedToSupergroupId);
          }
        }
      });
    }
  }

  private void processGroupFull (final TdApi.BasicGroupFullInfo groupFull) {
    if (groupFull == null) {
      return;
    }

    final boolean isUpdate = this.groupFull != null;
    this.groupFull = groupFull;

    if (!isUpdate) {
      prepareFullCells(groupFull);
    }

    tdlib.uiExecute(() -> {
      if (!isDestroyed()) {
        if (isUpdate) {
          setHeaderText();
          checkDescription();
        } else {
          addFullCells(groupFull);
        }
      }
    });
  }

  @Override
  public void onBasicGroupFullUpdated (final long basicGroupId, final TdApi.BasicGroupFullInfo basicGroupFull) {
    processGroupFull(basicGroupFull);
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId) {
        setHeaderText();
      }
    });
  }

  // Channels

  @Override
  public void onSupergroupUpdated (final TdApi.Supergroup supergroup) {
    if (mode == MODE_CHANNEL || mode == MODE_SUPERGROUP) {
      runOnUiThreadOptional(() -> {
        if (ProfileController.this.supergroup != null && ProfileController.this.supergroup.id == supergroup.id) {
          ProfileController.this.supergroup = supergroup;
          checkUsername();
          checkEasterEggs();
          updateHeader(false);
        }
      });
    } else if (mode == MODE_EDIT_CHANNEL || mode == MODE_EDIT_SUPERGROUP) {
      runOnUiThreadOptional(() -> {
        if (ProfileController.this.supergroup != null && ProfileController.this.supergroup.id == supergroup.id) {
          processEditContentChanged(supergroup);
        }
      });
    }
  }

  @SuppressLint("WrongThread")
  @AnyThread
  private void processChannelFull (final TdApi.SupergroupFullInfo supergroupFull) {
    if (supergroupFull == null) {
      return;
    }
    boolean isUpdate = this.supergroupFull != null;
    this.supergroupFull = supergroupFull;

    final Runnable act;

    if (!isUpdate) {
      if (mode == MODE_SUPERGROUP) {
        prepareSupergroupCells(supergroupFull);
      } else {
        prepareChannelCells(supergroupFull);
      }

      act = () -> {
        if (!isDestroyed()) {
          if (mode == MODE_SUPERGROUP) {
            addFullSupergroupCells(supergroupFull);
          } else {
            addFullChannelCells(supergroupFull);
          }
        }
      };

    } else {
      act = () -> {
        if (!isDestroyed()) {
          // updateValuedItem(R.id.btn_members);
          if (tdlib.canCreateInviteLink(chat)) {
            updateValuedItem(R.id.btn_inviteLink);
          }
          if (isEditing()) {
            updateValuedItem(R.id.btn_channelType);
            updateValuedItem(R.id.btn_linkedChat);
            updateValuedItem(R.id.btn_toggleAggressiveAntiSpam);
            updateValuedItem(R.id.btn_toggleHideMembers);
            if (mode == MODE_EDIT_CHANNEL || mode == MODE_EDIT_SUPERGROUP) {
              int i = baseAdapter.indexOfViewById(R.id.btn_linkedChat);
              boolean hasLinkedChat = i != -1;
              boolean needLinkedChat = canChangeInfo() && (mode == MODE_EDIT_CHANNEL || supergroupFull.linkedChatId != 0);
              if (hasLinkedChat != needLinkedChat) {
                boolean changed = false;
                if (needLinkedChat) {
                  i = baseAdapter.indexOfViewById(R.id.btn_channelType);
                  if (i != -1) {
                    baseAdapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                    baseAdapter.getItems().add(i + 2, newLinkedChatItem());
                    baseAdapter.notifyItemRangeInserted(i + 1, 2);
                    changed = true;
                  } else {
                    i = baseAdapter.indexOfViewById(R.id.btn_manageInviteLinks);
                    if (i != -1) {
                      baseAdapter.getItems().add(i, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                      baseAdapter.getItems().add(i, newLinkedChatItem());
                      baseAdapter.notifyItemRangeInserted(i, 2);
                      changed = true;
                    } else {
                      i = baseAdapter.indexOfViewById(R.id.shadowMiddle);
                      if (i != -1) {
                        baseAdapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SHADOW_TOP));
                        baseAdapter.getItems().add(i + 2, newLinkedChatItem());
                        baseAdapter.getItems().add(i + 3, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
                        baseAdapter.notifyItemRangeInserted(i + 1, 3);
                        changed = true;
                      }
                    }
                  }
                } else if (baseAdapter.indexOfViewById(R.id.btn_channelType) != -1) {
                  baseAdapter.removeRange(i - 1, 2);
                  changed = true;
                } else if (baseAdapter.indexOfViewById(R.id.btn_manageInviteLinks) != -1) {
                  baseAdapter.removeRange(i, 2);
                  changed = true;
                } else {
                  baseAdapter.removeRange(i - 1, 3);
                  changed = true;
                }
                if (changed) {
                  onItemsHeightProbablyChanged();
                }
              }
            }
            checkPrehistory();
          }
          checkDescription();
          checkManage();
          if (mode == MODE_EDIT_CHANNEL) {
            checkChannelMembers();
          }

          updateHeader(false);
        }
      };
    }
    tdlib.uiExecute(act);
  }

  @Override
  public void onSupergroupFullUpdated (final long supergroupId, final TdApi.SupergroupFullInfo newSupergroupFull) {
    processChannelFull(newSupergroupFull);
  }


  // Notifications and mute

  private void invalidateChatSettings (long chatId) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        setHeaderMute(true);
        baseAdapter.updateValuedSettingById(R.id.btn_notifications);
      }
    });
  }

  private void invalidateChatSettings (TdApi.NotificationSettingsScope scope) {
    runOnUiThreadOptional(() -> {
      if (scope.getConstructor() == tdlib.notifications().scope(chat.id).getConstructor()) {
        setHeaderMute(true);
        baseAdapter.updateValuedSettingById(R.id.btn_notifications);
      }
    });
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    invalidateChatSettings(scope);
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    invalidateChatSettings(chatId);
  }

  @Override
  public void onNotificationChannelChanged (TdApi.NotificationSettingsScope scope) {
    invalidateChatSettings(scope);
  }

  @Override
  public void onNotificationChannelChanged (long chatId) {
    invalidateChatSettings(chatId);
  }

  // Chat updated

  @Override
  public void onChatAvailableReactionsUpdated (long chatId, TdApi.ChatAvailableReactions availableReactions) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        updateAvailableReactions();
      }
    });
  }

  @Override
  public void onChatTitleChanged (final long chatId, final String title) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        updateHeader(false);
      }
    });
  }

  @Override
  public void onChatPhotoChanged (final long chatId, final @Nullable TdApi.ChatPhotoInfo photo) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        setHeaderPhoto(true);
      }
    });
  }

  @Override
  public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId && baseAdapter != null) {
        updateValuedItem(R.id.btn_chatPermissions);
      }
    });
  }

  // Messages

  @UiThread
  private void addMessage (TdApi.Message message) {
    if (filterMediaMessage(message) && getChatId() == message.chatId) {
      int filterConstructor = TD.makeFilterConstructor(message, false);
      if (filterConstructor != TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR) {
        TdApi.SearchMessagesFilter[] filters = getFiltersOrder();
        for (TdApi.SearchMessagesFilter filter : filters) {
          if (filter.getConstructor() == filterConstructor) {
            increaseMediaCount(filter);
            break;
          }
        }
      }
    }

    final int size = pagerAdapter.getCount();
    for (int i = 0; i < size; i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).addMessage(message);
      }
    }
  }

  @UiThread
  private void removeMessages (long[] messageIds) {
    final int size = pagerAdapter.getCount();
    for (int i = 0; i < size; i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).removeMessages(messageIds);
      }
    }
    refreshCounters();
  }

  @UiThread
  private void editMessage (long messageId, TdApi.MessageContent content) {
    final int size = pagerAdapter.getCount();
    for (int i = 0; i < size; i++) {
      ViewController<?> c = pagerAdapter.findCachedControllerByPosition(i);
      if (c != null) {
        ((SharedBaseController<?>) c).editMessage(messageId, content);
      }
    }
  }

  public static boolean filterMediaMessage (TdApi.Message message) {
    return message.sendingState == null && message.schedulingState == null;
  }

  // Messages Updates

  @Override
  public void onNewMessage (final TdApi.Message message) {
    if (filterMediaMessage(message)) {
      runOnUiThreadOptional(() -> {
        addMessage(message);
      });
    }
  }

  @Override
  public void onMessageSendSucceeded (final TdApi.Message message, long oldMessageId) {
    runOnUiThreadOptional(() -> {
      if (chat.id == message.chatId) {
        addMessage(message);
      }
    });
  }

  @Override
  public void onMessageSendFailed (final TdApi.Message message, final long oldMessageId, TdApi.Error error) {
    runOnUiThreadOptional(() -> {
      if (chat.id == message.chatId) {
        removeMessages(new long[]{oldMessageId});
      }
    });
  }

  @Override
  public void onMessageContentChanged (final long chatId, final long messageId, final TdApi.MessageContent newContent) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        editMessage(messageId, newContent);
      }
    });
  }

  @Override
  public void onMessagesDeleted (final long chatId, final long[] messageIds) {
    runOnUiThreadOptional(() -> {
      if (chat.id == chatId) {
        removeMessages(messageIds);
      }
    });
  }

  // Language

  @Override
  protected void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (baseAdapter != null) {
      baseAdapter.onLanguagePackEvent(event, arg1);
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (aboutWrapper != null) {
      aboutWrapper.setTextFlagEnabled(Text.FLAG_ALIGN_RIGHT, Lang.rtl());
      if (baseAdapter != null)
        updateValuedItem(R.id.btn_description);
    }
    if (topCellView != null) {
      topCellView.checkRtl();
    }
    if (pseudoHeaderWrap != null) {
      int childCount = pseudoHeaderWrap.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = pseudoHeaderWrap.getChildAt(i);
        if (Views.setGravity(view, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)) {
          if (view instanceof HeaderButton) {
            FrameLayout.LayoutParams params = ((FrameLayout.LayoutParams) view.getLayoutParams());
            if (Lang.rtl()) {
              params.leftMargin = Screen.dp(12f);
              params.rightMargin = 0;
            } else {
              params.rightMargin = Screen.dp(12f);
              params.leftMargin = 0;
            }
            view.setTranslationX(Math.abs(view.getTranslationX()) * (Lang.rtl() ? 1f : -1f));
          }
          Views.updateLayoutParams(view);
        }
      }
    }
    if (Views.setGravity(counterView, Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))
      Views.updateLayoutParams(counterView);
    if (Views.setGravity(counterDismiss, Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))
      Views.updateLayoutParams(counterDismiss);

    if (pager != null) {
      pager.checkRtl();
    }
  }
}
