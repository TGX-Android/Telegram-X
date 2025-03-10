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
 * File created on 27/04/2015 at 15:32
 */
package org.thunderdog.challegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.MediaCollectorDelegate;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.attach.MediaBottomFilesController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.component.attach.SponsoredMessagesInfoController;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.AttachLinearLayout;
import org.thunderdog.challegram.component.chat.AudioFile;
import org.thunderdog.challegram.component.chat.ChatBottomBarView;
import org.thunderdog.challegram.component.chat.ChatHeaderView;
import org.thunderdog.challegram.component.chat.ChatSearchMembersView;
import org.thunderdog.challegram.component.chat.CircleCounterBadgeView;
import org.thunderdog.challegram.component.chat.CommandKeyboardLayout;
import org.thunderdog.challegram.component.chat.CounterBadgeView;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.InlineResultsWrap;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.chat.InvisibleImageView;
import org.thunderdog.challegram.component.chat.JoinRequestsView;
import org.thunderdog.challegram.component.chat.MessageSenderButton;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesHolder;
import org.thunderdog.challegram.component.chat.MessagesLayout;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.MessagesSearchManagerMiddleware;
import org.thunderdog.challegram.component.chat.PinnedMessagesBar;
import org.thunderdog.challegram.component.chat.RaiseHelper;
import org.thunderdog.challegram.component.chat.ReplyBarView;
import org.thunderdog.challegram.component.chat.SilentButton;
import org.thunderdog.challegram.component.chat.StickerSuggestionAdapter;
import org.thunderdog.challegram.component.chat.TdlibSingleUnreadReactionsManager;
import org.thunderdog.challegram.component.chat.TopBarView;
import org.thunderdog.challegram.component.chat.VoiceVideoButtonView;
import org.thunderdog.challegram.component.chat.WallpaperAdapter;
import org.thunderdog.challegram.component.chat.WallpaperRecyclerView;
import org.thunderdog.challegram.component.chat.WallpaperView;
import org.thunderdog.challegram.component.popups.MessageSeenController;
import org.thunderdog.challegram.component.popups.ModernActionedLayout;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultButton;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.InlineResultSticker;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.data.TGBotStart;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.TGMessageLocation;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.data.TGMessageSticker;
import org.thunderdog.challegram.data.TGSwitchInline;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.filegen.VideoGenerationInfo;
import org.thunderdog.challegram.helper.BotHelper;
import org.thunderdog.challegram.helper.FoundUrls;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.helper.LiveLocationHelper;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageStrictCache;
import org.thunderdog.challegram.mediaview.MediaSelectDelegate;
import org.thunderdog.challegram.mediaview.MediaSpoilerSendDelegate;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.SliderView;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.SelectDelegate;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.StopwatchHeaderButton;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.player.RecordAudioVideoController;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.MessageListManager;
import org.thunderdog.challegram.telegram.MessageThreadListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.camera.CameraAccessImageView;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.v.MessagesLayoutManager;
import org.thunderdog.challegram.v.MessagesRecyclerView;
import org.thunderdog.challegram.voip.VoIPLogs;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.CollapseListView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.EmojiPacksInfoView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.KeyboardFrameLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.RippleRevealView;
import org.thunderdog.challegram.widget.SendButton;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.TextFormattingLayout;
import org.thunderdog.challegram.widget.TripleAvatarView;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.WallpaperParametersView;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.ChatId;
import tgx.td.MessageId;
import tgx.td.Td;
import tgx.td.TdConstants;
import tgx.td.data.MessageWithProperties;
import tgx.td.ui.TdUi;

public class MessagesController extends ViewController<MessagesController.Arguments> implements
  Menu, Unlockable, View.OnClickListener,
  ActivityResultHandler, MoreDelegate, CommandKeyboardLayout.Callback, MediaCollectorDelegate, SelectDelegate,
  ReplyBarView.Callback, RaiseHelper.Listener,
  TGLegacyManager.EmojiLoadListener, ChatHeaderView.Callback,
  ChatListener, NotificationSettingsListener, EmojiLayout.Listener,
  MessageThreadListener, TdlibSingleUnreadReactionsManager.UnreadSingleReactionListener,
  TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, TdlibCache.SecretChatDataChangeListener,
  TdlibCache.UserDataChangeListener,
  TdlibCache.UserStatusChangeListener,
  FactorAnimator.Target,
  StickerSuggestionAdapter.Callback,
  MediaViewDelegate,
  ForceTouchView.PreviewDelegate,
  Settings.VideoModePreferenceListener,
  RecordAudioVideoController.RecordStateListeners,
  ViewPager.OnPageChangeListener, ViewPagerTopView.OnItemClickListener,
  TGMessage.SelectableDelegate, GlobalAccountListener, EmojiToneHelper.Delegate, ComplexHeaderView.Callback, LiveLocationHelper.Callback, CreatePollController.Callback,
  HapticMenuHelper.Provider, HapticMenuHelper.OnItemClickListener, TdlibSettingsManager.DismissRequestsListener, InputView.SelectionChangeListener {

  private boolean reuseEnabled;
  private boolean destroyInstance;

  public void setReuseEnabled (boolean enabled) {
    this.reuseEnabled = enabled;
  }

  public void setDestroyInstance () {
    destroyInstance = true;
  }

  private int flags;

  private @Nullable TdApi.Chat chat;
  private @Nullable TdApi.ChatList openedFromChatList;

  private ChatHeaderView headerCell;
  private DoubleHeaderView headerDoubleCell;

  private MessagesLayout contentView;
  private LinearLayout bottomWrap;
  private MessagesRecyclerView messagesView;

  private final MessagesManager manager;
  private BotHelper botHelper;

  private @Nullable InputView inputView;
  private final ClickHelper inputViewDisabledClickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      return !hasSendBasicMessagePermission();
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      context().tooltipManager().builder(view).show(tdlib, R.string.MessageInputTextDisabledHint).hideDelayed();
    }
  });
  private SeparatorView bottomShadowView;
  private boolean enableOnResume;

  private KeyboardFrameLayout emojiKeyboardFrameLayout;

  private EmojiLayout emojiLayout;
  private TextFormattingLayout textFormattingLayout;
  private AttachLinearLayout attachButtons;
  private ImageView emojiButton;
  private VoiceVideoButtonView recordButton;
  private SendButton sendButton;
  private HapticMenuHelper sendMenu;
  private CameraAccessImageView mediaButton;
  private InvisibleImageView cameraButton, scheduleButton;
  private InvisibleImageView commandButton;
  private @Nullable SilentButton silentButton;

  private HapticMenuHelper sendAsMenu;
  private MessageSenderButton messageSenderButton;

  private WallpaperView wallpaperViewBlurPreview;
  private WallpaperParametersView backgroundParamsView;

  private CircleCounterBadgeView goToNextFoundMessageButtonBadge, goToPrevFoundMessageButtonBadge;
  private FrameLayoutFix scrollToBottomButtonWrap, mentionButtonWrap, reactionsButtonWrap;
  private CircleButton scrollToBottomButton, mentionButton, reactionsButton;
  private CounterBadgeView unreadCountView, mentionCountView, reactionsCountView;

  public boolean sponsoredMessageLoaded = false;

  public MessagesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.manager = new MessagesManager(this);
  }

  @Override
  public List<HapticMenuHelper.MenuItem> onCreateHapticMenu (View view) {
    TdApi.FormattedText currentText = null;
    boolean canSendWithoutMarkdown = inputView != null && !Td.equalsTo(inputView.getOutputText(true), currentText = inputView.getOutputText(false), true);
    List<HapticMenuHelper.MenuItem> items = tdlib.ui().fillDefaultHapticMenu(getChatId(), isEditingMessage(), canSendWithoutMarkdown, true);
    if (!canSendWithoutMarkdown && tdlib.shouldSendAsDice(currentText) && !isEditingMessage()) {
      if (items == null)
        items = new ArrayList<>();
      if (ContentPreview.EMOJI_DART.textRepresentation.equals(currentText.text)) {
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoMarkdown, Lang.getString(R.string.SendDiceAsEmoji), R.drawable.baseline_gps_fixed_24));
      } else if (ContentPreview.EMOJI_DICE.textRepresentation.equals(currentText.text)) {
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoMarkdown, Lang.getString(R.string.SendDiceAsEmoji), R.drawable.baseline_casino_24));
      } else {
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoMarkdown, Lang.getString(R.string.SendDiceAsEmoji), Drawables.emojiDrawable(currentText.text)));
      }
    }
    if (BuildConfig.DEBUG) {
      items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendToast, "Show toast", R.drawable.baseline_warning_24));
      items.add(new HapticMenuHelper.MenuItem(R.id.btn_debugLtrEmoji, "Send LTR emoji", R.drawable.baseline_warning_24));
    }
    if (canSelectSender()) {
      items.add(0, createHapticSenderItem(R.id.btn_openSendersMenu, chat.messageSenderId, false, false));
    }
    hideCursorsForInputView();
    return items;
  }

  @Override
  public boolean onHapticMenuItemClick (View view, View parentView, HapticMenuHelper.MenuItem item) {
    final int viewId = view.getId();
    if (viewId == R.id.btn_setMsgSender) {
      if (item.isLocked) {
        context().tooltipManager().builder(messageSenderButton.getButtonView())
          .show(tdlib, Lang.getString(R.string.error_PREMIUM_ACCOUNT_REQUIRED))
          .hideDelayed(2000, TimeUnit.MILLISECONDS);
        return true;
      }
      TdApi.MessageSender sender = item.messageSenderId;
      if (sender != null) {
        setNewMessageSender(sender);
      } else {
        openSetSenderPopup();
      }
    } else if (viewId == R.id.btn_openSendersMenu) {
      openSetSenderPopup();
    } else if (viewId == R.id.btn_sendOnceOnline) {
      TdApi.MessageSendOptions sendOptions = Td.newSendOptions(new TdApi.MessageSchedulingStateSendWhenOnline());
      send(sendOptions, true);
    } else if (viewId == R.id.btn_sendScheduled) {
      tdlib.ui().pickSchedulingState(this, sendOptions -> {
        send(sendOptions, true);
      }, getChatId(), false, false, null, null);
    } else if (viewId == R.id.btn_sendNoMarkdown) {
      if (isEditingMessage()) {
        saveMessage(false);
      } else {
        pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) -> send(sendOptions, false));
      }
    } else if (viewId == R.id.btn_sendToast) {
      TdApi.FormattedText newText = inputView.getOutputText(true);
      CharSequence text = TD.toCharSequence(newText);
      showCallbackToast(text);
    } else if (viewId == R.id.btn_sendNoSound) {
      pickDateOrProceed(Td.newSendOptions(true), (modifiedSendOptions, disableMarkdown) -> {
        send(modifiedSendOptions, true);
      });
    } else if (viewId == R.id.btn_debugLtrEmoji) {
      pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) -> send(new TdApi.InputMessageText(new TdApi.FormattedText(Text.bidiGenerateTestMessage(), new TdApi.TextEntity[0]), null, false), false, sendOptions, null));
    }
    return true;
  }

  public void pickDateOrProceed (@NonNull TdApi.MessageSendOptions initialSendOptions, TdlibUi.SimpleSendCallback sendCallback) {
    if (initialSendOptions.schedulingState == null && areScheduledOnly()) {
      tdlib().ui().showScheduleOptions(this, getChatId(), false, (modifiedSendOptions, disableMarkdown) -> {
        if (!isDestroyed()) {
          sendCallback.onSendRequested(modifiedSendOptions, disableMarkdown);
        }
      }, null, null);
    } else {
      sendCallback.onSendRequested(initialSendOptions, false);
    }
  }

  @Override
  public boolean allowThemeChanges () {
    return !inWallpaperMode();
  }

  @Override
  public View getViewForApplyingOffsets () {
    return /*pagerContentView != null ? null : */topBar;
  }

  @Override
  protected boolean shouldApplyPlayerMargin () {
    return false; // pagerContentView != null;
  }

  @Override
  protected boolean applyPlayerOffset (float factor, float top) {
    if (super.applyPlayerOffset(factor, top)) {
      if (messagesView != null) {
        messagesView.invalidateDate();
      }
      return true;
    }
    return false;
  }

  private WallpaperView wallpaperView;
  private RecyclerView wallpapersList;

  public WallpaperView wallpaper () {
    return wallpaperView;
  }

  @Override
  public void performComplexPhotoOpen () {
    long headerChatId = getHeaderChatId();
    TdApi.Chat chat = headerChatId == this.chat.id ? this.chat : tdlib.chat(headerChatId);
    if (chat != null && chat.photo != null && !TD.isFileEmpty(chat.photo.small)) {
      MediaViewController.openFromChat(this, chat, headerCell);
    }
  }

  public final boolean needTabs () {
    return isSelfChat() && !isInForceTouchMode() && !inPreviewMode() && !areScheduledOnly();
  }

  public float getPagerScrollOffsetInPixels () {
    return getValue().getMeasuredWidth() * pagerScrollOffset;
  }

  public float getPagerScrollOffset () {
    return pagerScrollOffset;
  }

  public void onKnownMessageCountChanged (long chatId, int knownMessageCount) {
    if (getChatId() == chatId) {
      if (previewMode == PREVIEW_MODE_SEARCH && (previewSearchSender != null || previewSearchFilter != null)) {
        updateSearchSubtitle();
      }
      // TODO other cases?
    }
  }

  private void updateSearchSubtitle () {
    int totalCount = manager != null ? manager.getKnownTotalMessageCount() : -1;

    if (previewSearchFilter != null) {
      if (Td.isPinnedFilter(previewSearchFilter)) {
        if (totalCount > 0) {
          headerCell.setForcedSubtitle(Lang.pluralBold(R.string.XPinnedMessages, totalCount));
        } else {
          headerCell.setForcedSubtitle(Lang.getString(R.string.PinnedMessages));
        }
      }
      return;
    }

    if (totalCount > 0) {
      if (tdlib.isSelfSender(previewSearchSender)) {
        headerCell.setForcedSubtitle(Lang.pluralBold(R.string.XFoundMessagesFromSelf, totalCount));
      } else {
        headerCell.setForcedSubtitle(Lang.pluralBold(previewSearchSender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.XFoundMessagesFromUser : R.string.XFoundMessagesFromChat, totalCount, tdlib.senderName(previewSearchSender, true)));
      }
    } else {
      if (tdlib.isSelfSender(previewSearchSender)) {
        headerCell.setForcedSubtitle(Lang.getString(R.string.FoundMessagesFromSelf));
      } else {
        headerCell.setForcedSubtitle(Lang.getStringBold(previewSearchSender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.FoundMessagesFromUser : R.string.FoundMessagesFromChat, tdlib.senderName(previewSearchSender, true)));
      }
    }
  }

  private void updateMessageThreadSubtitle () {
    if (messageThread == null)
      return;
    if (areScheduled) {
      headerCell.setForcedSubtitle(Lang.lowercase(Lang.getString(R.string.ScheduledMessages)));
    } else {
      headerCell.setForcedSubtitle(messageThread.chatHeaderSubtitle());
    }
  }

  private void updateForcedSubtitle () {
    if (messageThread != null) {
      updateMessageThreadSubtitle();
    } else if (areScheduled) {
      headerCell.setForcedSubtitle(Lang.lowercase(Lang.getString(isSelfChat() ? R.string.Reminders : R.string.ScheduledMessages)));
    } else {
      headerCell.setForcedSubtitle(null);
    }
  }

  @Override
  protected View onCreateView (final Context context) {
    if (!isInForceTouchMode()) {
      UI.setSoftInputMode(UI.getContext(context), Config.DEFAULT_WINDOW_PARAMS);
    }

    contentView = new MessagesLayout(context);
    contentView.setController(this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    RelativeLayout.LayoutParams params;

    headerCell = new ChatHeaderView(context, tdlib, this);
    headerCell.setPhotoOpenCallback(this);
    switch (previewMode) {
      case PREVIEW_MODE_EVENT_LOG: {
        headerCell.setForcedSubtitle(Lang.lowercase(Lang.getString(R.string.EventLogAllEvents)));
        break;
      }
      case PREVIEW_MODE_SEARCH: {
        if (previewSearchSender != null || previewSearchFilter != null) {
          updateSearchSubtitle();
        } else {
          headerCell.setForcedSubtitle(Lang.getString(R.string.FoundMessagesQuery, previewSearchQuery));
          headerCell.setCallback(this);
        }
        break;
      }
      case PREVIEW_MODE_WALLPAPER_OBJECT: {
        headerDoubleCell = new DoubleHeaderView(context);
        headerDoubleCell.setThemedTextColor(this);
        headerDoubleCell.initWithMargin(Screen.dp(12f), true);
        headerDoubleCell.setTitle(getName());
        if (getArguments().wallpaperObject.document != null) {
          headerDoubleCell.setSubtitle(Strings.buildSize(getArguments().wallpaperObject.document.document.size));
        } else if (getArguments().wallpaperObject.type.getConstructor() == TdApi.BackgroundTypePattern.CONSTRUCTOR) {
          headerDoubleCell.setSubtitle(Lang.getString(R.string.ChatBackgroundTypePattern));
        } else if (getArguments().wallpaperObject.type.getConstructor() == TdApi.BackgroundTypeFill.CONSTRUCTOR) {
          TdApi.BackgroundTypeFill filledWp = (TdApi.BackgroundTypeFill) getArguments().wallpaperObject.type;

          switch (filledWp.fill.getConstructor()) {
            case TdApi.BackgroundFillGradient.CONSTRUCTOR:
              headerDoubleCell.setSubtitle(Lang.getString(R.string.ChatBackgroundTypeGradient));
              break;
            case TdApi.BackgroundFillFreeformGradient.CONSTRUCTOR:
              headerDoubleCell.setSubtitle(Lang.getString(R.string.ChatBackgroundTypeMulticolor));
              break;
            case TdApi.BackgroundFillSolid.CONSTRUCTOR:
              headerDoubleCell.setSubtitle(Lang.getString(R.string.ChatBackgroundTypeSolid));
              break;
            default:
              throw new UnsupportedOperationException(filledWp.fill.toString());
          }
        }
      }
      default: {
        break;
      }
    }
    headerCell.initWithController(this, true);

    wallpaperView = new WallpaperView(context, manager, tdlib);
    if (previewMode == PREVIEW_MODE_WALLPAPER_OBJECT) {
      wallpaperView.initWithCustomWallpaper(new TGBackground(tdlib, getArguments().wallpaperObject));
    } else {
      wallpaperView.initWithSetupMode(previewMode == PREVIEW_MODE_WALLPAPER);
    }
    wallpaperView.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addThemeInvalidateListener(wallpaperView);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);

    MessagesLayoutManager messagesManager;

    messagesManager = new MessagesLayoutManager(context, RecyclerView.VERTICAL, true);
    messagesManager.setManager(manager);

    messagesView = (MessagesRecyclerView) Views.inflate(context(), R.layout.recycler_messages, contentView);
    if (isInForceTouchMode()) {
      messagesView.setVerticalScrollBarEnabled(false);
    }
    addThemeInvalidateListener(messagesView);
    messagesView.setId(R.id.msg_list);
    messagesView.setManager(manager);
    messagesView.setController(this);
    messagesView.setHasFixedSize(true);
    messagesView.setLayoutManager(messagesManager);
    messagesView.setLayoutParams(params);
    Views.setScrollBarPosition(messagesView);
    if (Config.HARDWARE_MESSAGES_LIST) {
      Views.setLayerType(messagesView, View.LAYER_TYPE_HARDWARE);
    }

    manager.modifyRecycler(context, messagesView, messagesManager);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

    bottomWrap = new LinearLayout(context) {
      int lastHeight;

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateButtonsY();
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateButtonsY();
      }
    };
    bottomWrap.setId(R.id.msg_bottom);
    bottomWrap.setOrientation(LinearLayout.VERTICAL);
    bottomWrap.setMinimumHeight(Screen.dp(49f));
    bottomWrap.setLayoutParams(params);

    if (previewMode == PREVIEW_MODE_NONE && !isInForceTouchMode()) {
      inputView = new InputView(context, tdlib, this) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          updateButtonsY();
        }

        @Override
        protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
          super.onLayout(changed, left, top, right, bottom);
          updateButtonsY();
        }

        @Override
        public boolean onTouchEvent (MotionEvent event) {
          boolean r = super.onTouchEvent(event);
          inputViewDisabledClickHelper.onTouchEvent(this, event);
          if (emojiKeyboardFrameLayout != null) {
            emojiKeyboardFrameLayout.contentView.textFormattingLayout.onInputViewTouchEvent(event);
          }
          return r;
        }
      };
      inputView.setNoPersonalizedLearning(Settings.instance().needsIncognitoMode(chat));
      inputView.setId(R.id.msg_input);
      inputView.setTextColor(Theme.textAccentColor());
      addThemePaintColorListener(inputView.getPlaceholderPaint(), ColorId.textPlaceholder);
      addThemeTextColorListener(inputView, ColorId.text);
      inputView.setHintTextColor(Theme.textPlaceholderColor());
      addThemeHintTextColorListener(inputView, ColorId.textPlaceholder);
      inputView.setLinkTextColor(Theme.textLinkColor());
      addThemeLinkTextColorListener(inputView, ColorId.textLink);
      ViewSupport.setThemedBackground(inputView, ColorId.filling, this);
      inputView.setHighlightColor(Theme.fillingTextSelectionColor());
      addThemeHighlightColorListener(inputView, ColorId.textSelectionHighlight);
      bindLocaleChanger(inputView.setController(this));
      if (inPreviewMode) {
        inputView.setEnabled(false);
        inputView.setInputPlaceholder(R.string.Message);
      }
      inputView.setSelectionChangeListener(this);
      inputView.setSpanChangeListener(this::onInputSpansChanged);
    }

    if (!inPreviewMode) {
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      params.addRule(RelativeLayout.ALIGN_TOP, R.id.msg_bottom);

      replyBarView = new ReplyBarView(context(), tdlib);
      ViewSupport.setThemedBackground(replyBarView, ColorId.filling, this);
      replyBarView.setId(R.id.msg_bottomReply);
      replyBarView.setAnimationsDisabled(true);
      replyBarView.initWithCallback(this, this);
      replyBarView.setOnClickListener(this);
      replyBarView.setLayoutParams(params);
    }

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    params.addRule(RelativeLayout.ALIGN_TOP, R.id.msg_bottom);

    topBar = new CollapseListView(context);
    topBar.setLayoutParams(params);
    topBar.setTotalHeightChangeListener(listView -> onMessagesFrameChanged());

    int liveLocationHeight = Screen.dp(36f);
    liveLocationView = new View(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return liveLocation != null && liveLocation.onTouchEvent(e);
      }

      @Override
      protected void onDraw (Canvas c) {
        c.drawRect(0, 0, getMeasuredWidth(), Screen.dp(36f), Paints.fillingPaint(Theme.fillingColor()));
        if (liveLocation != null) {
          liveLocation.draw(c, 0);
        }
      }
    };
    liveLocationView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, liveLocationHeight));
    addThemeInvalidateListener(liveLocationView);

    int actionBarHeight = Screen.dp(46f);
    actionView = new TopBarView(context);
    actionView.setDismissListener(barView ->
      dismissActionBar()
    );
    actionView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, actionBarHeight));
    actionView.addThemeListeners(this);

    int requestsViewHeight = Screen.dp(48f);
    requestsView = new JoinRequestsView(context, tdlib);
    requestsView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, requestsViewHeight));
    requestsView.setOnClickListener(v -> ModernActionedLayout.showJoinRequests(this, chat.id, requestsView.getInfo()));
    requestsView.setOnDismissRunnable(() -> tdlib.settings().dismissRequests(chat.id, requestsView.getInfo()));
    tdlib.settings().addJoinRequestsDismissListener(this);
    RippleSupport.setSimpleWhiteBackground(requestsView, this);
    Views.setClickable(requestsView);

    toastAlertView = new CustomTextView(context, tdlib);
    toastAlertItem = new CollapseListView.Item() {
      @Override
      public int getVisualHeight () {
        return toastAlertView.getCurrentHeight(toastAlertView.getMeasuredWidth());
      }

      @Override
      public boolean allowCollapse () {
        return false;
      }

      @Override
      public View getValue () {
        return toastAlertView;
      }
    };
    toastAlertView.setTextSize(15f);
    addThemeTextAccentColorListener(toastAlertView);
    ViewSupport.setThemedBackground(toastAlertView, ColorId.filling, this);
    toastAlertView.setTextColorId(ColorId.text);
    toastAlertView.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
    toastAlertView.setHeightChangeListener((v, newHeight) -> topBar.notifyItemHeightChanged(toastAlertItem));

    pinnedMessagesBar = new PinnedMessagesBar(context, true) {
      @Override
      protected void onViewportChanged () {
        super.onViewportChanged();
        topBar.notifyItemHeightChanged(pinnedMessagesItem);
      }
    };
    pinnedMessagesBar.setAnimationsDisabled(true);
    pinnedMessagesBar.initialize(this);
    pinnedMessagesBar.setMessageListener(new PinnedMessagesBar.MessageListener() {
      @Override
      public void onMessageClick (PinnedMessagesBar view, TdApi.Message message, TdApi.InputTextQuote quote) {
        highlightMessage(new MessageId(message.chatId, message.id));
      }

      @Override
      public void onDismissRequest (PinnedMessagesBar view) {
        dismissPinnedMessage();
      }

      @Override
      public void onShowAllRequest (PinnedMessagesBar view) {
        MessagesController c = new MessagesController(context, tdlib);
        c.setArguments(new Arguments(null, chat, null, null, new TdApi.SearchMessagesFilterPinned()));
        navigateTo(c);
      }
    });
    pinnedMessagesItem = new CollapseListView.Item() {
      @Override
      public int getVisualHeight () {
        return pinnedMessagesBar.getTotalHeight();
      }

      @Override
      public View getValue () {
        return pinnedMessagesBar;
      }

      @Override
      public void onCompletelyHidden () {
        pinnedMessagesBar.collapse(false);
      }
    };

    topBar.initWithList(new CollapseListView.Item[] {
      // TODO voice chat bar
      pinnedMessagesItem,
      requestsItem = new CollapseListView.ViewItem(requestsView, requestsViewHeight),
      liveLocationItem = new CollapseListView.ViewItem(liveLocationView, liveLocationHeight),
      actionItem = new CollapseListView.ViewItem(actionView, ViewGroup.LayoutParams.WRAP_CONTENT),
      toastAlertItem
    }, this);

    // Mention button

    params = new RelativeLayout.LayoutParams(Screen.dp(118f), Screen.dp(74f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);

    mentionButtonWrap = new FrameLayoutFix(context);
    mentionButtonWrap.setLayoutParams(params);
    setMentionButtonFactor(0f);

    final int padding = Screen.dp(4);
    FrameLayoutFix.LayoutParams fparams = FrameLayoutFix.newParams(Screen.dp(24f) * 2 + padding * 2, Screen.dp(24f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    mentionButton = new CircleButton(context);
    mentionButton.setId(R.id.btn_mention);
    mentionButton.setOnClickListener(this);
    mentionButton.setOnLongClickListener(v -> {
      long chatId = getChatId();
      if (chatId != 0 && !isDestroyed()) {
        tdlib.client().send(new TdApi.ReadAllChatMentions(chatId), tdlib.okHandler());
        return true;
      }
      return false;
    });
    addThemeInvalidateListener(mentionButton);
    mentionButton.init(R.drawable.baseline_alternate_email_24, 48f, 4f, ColorId.circleButtonChat, ColorId.circleButtonChatIcon);
    mentionButton.setLayoutParams(fparams);
    mentionButtonWrap.addView(mentionButton);

    int buttonPadding = Screen.dp(24f);
    fparams = FrameLayoutFix.newParams(buttonPadding + fparams.width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
    fparams.bottomMargin = Screen.dp(24f) * 2 - Screen.dp(28f) / 2;

    mentionCountView = new CounterBadgeView(context);
    mentionCountView.setLayoutParams(fparams);
    mentionCountView.setPadding(buttonPadding, 0, 0, 0);
    addThemeInvalidateListener(mentionCountView);
    mentionButtonWrap.addView(mentionCountView);
    mentionButton.setTag(mentionCountView);

    // Scroll button

    params = new RelativeLayout.LayoutParams(Screen.dp(118f), Screen.dp(74f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);

    scrollToBottomButtonWrap = new FrameLayoutFix(context);
    scrollToBottomButtonWrap.setLayoutParams(params);
    scrollToBottomButtonWrap.setAlpha(0f);
    if (isInForceTouchMode()) {
      scrollToBottomButtonWrap.setTranslationY(-Screen.dp(16f) + Screen.dp(4f));
    }

    fparams = FrameLayoutFix.newParams(Screen.dp(24f) * 2 + padding * 2, Screen.dp(24f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    scrollToBottomButton = new CircleButton(context());
    scrollToBottomButton.setId(R.id.btn_scroll);
    scrollToBottomButton.setOnClickListener(this);
    scrollToBottomButton.setOnLongClickListener(v -> {
      manager.scrollToStart(true);
      return true;
    });
    addThemeInvalidateListener(scrollToBottomButton);
    scrollToBottomButton.init(R.drawable.baseline_arrow_downward_24, 48f, 4f, ColorId.circleButtonChat, ColorId.circleButtonChatIcon);
    scrollToBottomButton.setLayoutParams(fparams);
    scrollToBottomButtonWrap.addView(scrollToBottomButton);

    fparams = FrameLayoutFix.newParams(buttonPadding + fparams.width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
    fparams.bottomMargin = Screen.dp(24f) * 2 - Screen.dp(28f) / 2;

    unreadCountView = new CounterBadgeView(context);
    unreadCountView.setPadding(buttonPadding, 0, 0, 0);
    unreadCountView.setLayoutParams(fparams);
    addThemeInvalidateListener(unreadCountView);
    scrollToBottomButtonWrap.addView(unreadCountView);
    scrollToBottomButton.setTag(unreadCountView);

    // Reaction button

    params = new RelativeLayout.LayoutParams(Screen.dp(118f), Screen.dp(74f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);

    reactionsButtonWrap = new FrameLayoutFix(context);
    reactionsButtonWrap.setLayoutParams(params);

    fparams = FrameLayoutFix.newParams(Screen.dp(24f) * 2 + padding * 2, Screen.dp(24f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    reactionsButton = new CircleButton(context(), tdlib);
    reactionsButton.setId(R.id.btn_reaction);
    reactionsButton.setOnClickListener(this);
    addThemeInvalidateListener(reactionsButton);
    reactionsButton.init(R.drawable.baseline_favorite_20, 48f, 4f, ColorId.circleButtonChat, ColorId.circleButtonChatIcon);
    reactionsButton.setLayoutParams(fparams);
    reactionsButton.setOnClickListener(this);
    reactionsButton.setOnLongClickListener(v -> {
      long chatId = getChatId();
      if (chatId != 0 && !isDestroyed()) {
        tdlib.client().send(new TdApi.ReadAllChatReactions(chatId), tdlib.okHandler());
        return true;
      }
      return false;
    });

    fparams = FrameLayoutFix.newParams(buttonPadding + fparams.width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
    fparams.bottomMargin = Screen.dp(24f) * 2 - Screen.dp(28f) / 2;

    reactionsCountView = new CounterBadgeView(context);
    reactionsCountView.setLayoutParams(fparams);
    reactionsCountView.setPadding(buttonPadding, 0, 0, 0);
    addThemeInvalidateListener(reactionsCountView);
    reactionsButton.setTag(reactionsCountView);
    reactionsButtonWrap.addView(reactionsButton);
    reactionsButtonWrap.addView(reactionsCountView);

    setReactionButtonFactor(0f);

    goToNextFoundMessageButtonBadge = new CircleCounterBadgeView(this, R.id.btn_search_next, this, null);
    goToNextFoundMessageButtonBadge.init(R.drawable.baseline_keyboard_arrow_up_24, 48f, 4f, ColorId.circleButtonChat, ColorId.circleButtonChatIcon);
    goToNextFoundMessageButtonBadge.setTranslationX(CircleCounterBadgeView.BUTTON_WRAPPER_WIDTH);
    goToNextFoundMessageButtonBadge.setTranslationY(Screen.dp(16 - 74));
    goToNextFoundMessageButtonBadge.setEnabled(false, false);

    goToPrevFoundMessageButtonBadge = new CircleCounterBadgeView(this, R.id.btn_search_prev, this, null);
    goToPrevFoundMessageButtonBadge.init(R.drawable.baseline_keyboard_arrow_down_24, 48f, 4f, ColorId.circleButtonChat, ColorId.circleButtonChatIcon);
    goToPrevFoundMessageButtonBadge.setTranslationX(CircleCounterBadgeView.BUTTON_WRAPPER_WIDTH);
    goToPrevFoundMessageButtonBadge.setEnabled(false, false);
    searchNavigationButtonVisibleAnimator.setValue(false, false);

    searchByUserViewWrapper = new ChatSearchMembersView(context, this);
    searchByUserViewWrapper.setVisibility(View.GONE);
    searchByUserViewWrapper.setDelegate(new ChatSearchMembersView.Delegate() {
      @Override
      public void onSetMessageSender (@Nullable TdApi.MessageSender sender) {
        onSetSearchMessagesSenderId(sender);
        showSearchByUserView(false, true);
        searchChatMessages(getLastMessageSearchQuery());
      }

      @Override
      public void onClose () {
        showSearchByUserView(false, true);
      }
    });

    // Shadow & bottom controls

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f));
    params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
    bottomShadowView = SeparatorView.simpleSeparator(context, params, false);
    bottomShadowView.setAlignBottom();
    addThemeInvalidateListener(bottomShadowView);
    bottomShadowView.setId(R.id.msg_bottomShadow);

    params = new RelativeLayout.LayoutParams(Screen.dp(55f), Screen.dp(49f));
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    if (Lang.rtl()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    }

    emojiButton = new ImageView(context);
    emojiButton.setId(R.id.msg_emoji);
    emojiButton.setScaleType(ImageView.ScaleType.CENTER);
    emojiButton.setImageResource(EmojiLayout.getTargetIcon(true));
    emojiButton.setColorFilter(Theme.iconColor());
    addThemeFilterListener(emojiButton, ColorId.icon);
    emojiButton.setOnClickListener(this);
    emojiButton.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(49f));
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    if (Lang.rtl()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    }

    attachButtons = new AttachLinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (inputView != null && getMeasuredWidth() > 0) {
          inputView.checkPlaceholderWidth();
        }
        if (messageSenderButton != null) {
          messageSenderButton.checkPosition();
        }
      }
    };
    attachButtons.setOrientation(LinearLayout.HORIZONTAL);
    attachButtons.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(49f));
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);

    messageSenderButton = new MessageSenderButton(context, this);
    messageSenderButton.setLayoutParams(params);
    messageSenderButton.setDelegate(new MessageSenderButton.Delegate() {
      @Override
      public void onClick () {
        openSetSenderPopup();
      }
    });
    messageSenderButton.setOnLongClickListener(v -> {
      if (canSelectSender()) {
        getChatAvailableMessagesSenders(() -> sendAsMenu.openMenu(messageSenderButton.getButtonView()));
      }
      return true;
    });

    final float ATTACH_BUTTONS_WIDTH = 47f;

    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));

    mediaButton = new CameraAccessImageView(context, this);
    mediaButton.setId(R.id.msg_attach);
    mediaButton.setScaleType(ImageView.ScaleType.CENTER);
    mediaButton.setImageResource(R.drawable.deproko_baseline_attach_26);
    mediaButton.setColorFilter(Theme.iconColor());
    addThemeFilterListener(mediaButton, ColorId.icon);
    mediaButton.setOnClickListener(this);
    mediaButton.setLayoutParams(lp);


    lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));
    cameraButton = new CameraAccessImageView(context, this);
    cameraButton.setId(R.id.btn_camera);
    cameraButton.setScaleType(ImageView.ScaleType.CENTER);
    cameraButton.setImageResource(R.drawable.deproko_baseline_camera_26);
    cameraButton.setColorFilter(Theme.iconColor());
    addThemeFilterListener(cameraButton, ColorId.icon);
    cameraButton.setOnClickListener(this);
    cameraButton.setLayoutParams(lp);

    if (!areScheduled /*&& isSelfChat()*/) {
      lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));
      scheduleButton = new CameraAccessImageView(context, this);
      scheduleButton.setId(R.id.btn_viewScheduled);
      scheduleButton.setScaleType(ImageView.ScaleType.CENTER);
      scheduleButton.setImageResource(R.drawable.baseline_date_range_24);
      scheduleButton.setColorFilter(Theme.iconColor());
      addThemeFilterListener(scheduleButton, ColorId.icon);
      scheduleButton.setOnClickListener(this);
      scheduleButton.setLayoutParams(lp);
    }

    lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));

    commandButton = new InvisibleImageView(context);
    commandButton.setId(R.id.msg_command);
    commandButton.setColorFilter(Theme.iconColor());
    addThemeFilterListener(commandButton, ColorId.icon);
    commandButton.setScaleType(ImageView.ScaleType.CENTER);
    commandButton.setOnClickListener(this);
    commandButton.setVisibility(View.INVISIBLE);
    commandButton.setLayoutParams(lp);
    Views.setClickable(commandButton);
    updateCommandButton(0);

    if (Config.NEED_SILENT_BROADCAST) {
      lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));

      silentButton = new SilentButton(context);
      silentButton.setId(R.id.btn_silent);
      silentButton.setOnClickListener(this);
      silentButton.setLayoutParams(lp);
      addThemeInvalidateListener(silentButton);
      updateSilentButton(false);
    }

    lp = new LinearLayout.LayoutParams(Screen.dp(ATTACH_BUTTONS_WIDTH), Screen.dp(49f));
    lp.rightMargin = Screen.dp(2f);

    recordButton = new VoiceVideoButtonView(context);
    recordButton.setPadding(0, 0, Screen.dp(2f), 0);
    recordButton.setHasTouchControls(true);
    addThemeInvalidateListener(recordButton);
    recordButton.setLayoutParams(lp);

    attachButtons.addView(commandButton);
    if (silentButton != null) {
      attachButtons.addView(silentButton);
    }
    if (scheduleButton != null) {
      attachButtons.addView(scheduleButton);
    }
    if (cameraButton != null) {
      attachButtons.addView(cameraButton);
    }
    attachButtons.addView(mediaButton);
    attachButtons.addView(recordButton);
    attachButtons.updatePivot();

    params = new RelativeLayout.LayoutParams(Screen.dp(55f), Screen.dp(49f));
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    if (Lang.rtl()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    }

    sendButton = new SendButton(context, areScheduled ? R.drawable.dotvhs_baseline_send_schedule_24 : R.drawable.deproko_baseline_send_24);
    sendButton.setIgnoreDrawMessageSender();
    sendButton.setOnClickListener(this);
    addThemeInvalidateListener(sendButton);
    sendButton.setId(R.id.msg_send);
    sendButton.setVisibility(View.INVISIBLE);
    sendButton.setAlpha(0f);
    sendButton.setLayoutParams(params);

    sendMenu = new HapticMenuHelper(this, this, getThemeListeners(), null).attachToView(sendButton);
    sendAsMenu = new HapticMenuHelper(hapticSendAsMenuProvider, this, getThemeListeners(), null).selectableMode(messageSenderButton).hapticListener(messageSenderButton);
    messageSenderButton.setHapticMenuHelper(sendAsMenu);

    if (inPreviewMode) {
      switch (previewMode) {
        case PREVIEW_MODE_WALLPAPER: {
          wallpapersList = new WallpaperRecyclerView(context);
          wallpapersList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, Lang.rtl()));
          wallpapersList.setAdapter(new WallpaperAdapter(this, ThemeManager.instance().currentTheme(false).getId()));
          wallpapersList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
              final int spacing = Screen.dp(3f);
              outRect.top = outRect.bottom = spacing;
              RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
              int position = holder != null ? holder.getBindingAdapterPosition() : RecyclerView.NO_POSITION;
              if (holder == null || holder.getItemViewType() != 0 || position == RecyclerView.NO_POSITION) {
                outRect.left = outRect.right = 0;
              } else {
                if (Lang.rtl()) {
                  outRect.right = spacing; // position == 0 ? spacing : spacing / 2 + spacing % 2
                  outRect.left = position == parent.getAdapter().getItemCount() - 1 ? spacing : 0;
                } else {
                  outRect.left = spacing;
                  outRect.right = position == parent.getAdapter().getItemCount() - 1 ? spacing : 0;
                }
              }
            }
          });
          wallpapersList.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(105f) + Screen.dp(3f) * 2));
          ViewSupport.setThemedBackground(wallpapersList, ColorId.filling, this);
          bottomWrap.addView(wallpapersList);
          break;
        }
        case PREVIEW_MODE_WALLPAPER_OBJECT: {
          ViewSupport.setThemedBackground(wallpapersList, ColorId.filling, this);
          break;
        }
        case PREVIEW_MODE_FONT_SIZE: {
          FrameLayoutFix textWrap = new FrameLayoutFix(context);
          textWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(49f)));
          ViewSupport.setThemedBackground(textWrap, ColorId.filling, this);

          TextView tv1 = new NoScrollTextView(context);
          tv1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
          tv1.setText("T");
          tv1.setTypeface(Fonts.getRobotoBold());
          tv1.setTextColor(Theme.textDecentColor());
          addThemeTextDecentColorListener(tv1);
          tv1.setGravity(Gravity.CENTER);
          tv1.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(46f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
          textWrap.addView(tv1);

          TextView tv2 = new NoScrollTextView(context);
          tv2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
          tv2.setText("T");
          tv2.setGravity(Gravity.CENTER);
          tv2.setPadding(0, 0, 0, Screen.dp(1f));
          tv2.setTypeface(Fonts.getRobotoBold());
          tv2.setTextColor(Theme.textDecentColor());
          addThemeTextDecentColorListener(tv2);
          tv2.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(46f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
          textWrap.addView(tv2);

          fontSliderView = new SliderView(context);
          addThemeInvalidateListener(fontSliderView);
          fontSliderView.setSlideEnabled(true, false);
          fontSliderView.setValueCount(Settings.CHAT_FONT_SIZES.length);
          updateFontSliderValue(fontSliderView);
          fontSliderView.setListener(new SliderView.Listener() {
            @Override
            public void onSetStateChanged (SliderView view, boolean isSetting) {
              if (!isSetting) {
                int index = Math.round(view.getValue() * (float) (Settings.CHAT_FONT_SIZES.length - 1));
                view.animateValue((float) index / (float) (Settings.CHAT_FONT_SIZES.length - 1));
              }
            }

            @Override
            public void onValueChanged (SliderView view, float factor) {
              int index = Math.round(factor * (float) (Settings.CHAT_FONT_SIZES.length - 1));
              if (Settings.instance().setChatFontSize(Settings.CHAT_FONT_SIZES[index])) {
                manager.onUpdateTextSize();
                manager.rebuildLayouts();
              }
            }

            @Override
            public boolean allowSliderChanges (SliderView view) {
              return true;
            }
          });
          fontSliderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          fontSliderView.setForceBackgroundColorId(ColorId.sliderInactive);
          fontSliderView.setPadding(tv2.getLayoutParams().width, 0, tv2.getLayoutParams().width, 0);
          fontSliderView.setColorId(ColorId.sliderActive, false);
          textWrap.addView(fontSliderView);

          bottomWrap.addView(textWrap);
          break;
        }
        case PREVIEW_MODE_NONE: {
          bottomWrap.addView(inputView);
          break;
        }
      }
    } else if (inputView != null) {
      bottomWrap.addView(inputView);
    }

    if (inWallpaperMode()) {
      TdApi.Background currentBackgroundObj = getArgumentsStrict().wallpaperObject;
      TGBackground currentBackground = tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier());
      boolean shouldUseParams = !inWallpaperPreviewMode() || currentBackgroundObj != null && currentBackgroundObj.type.getConstructor() == TdApi.BackgroundTypeWallpaper.CONSTRUCTOR;

      if (shouldUseParams) {
        int height = Screen.dp(49f);
        backgroundParamsView = new WallpaperParametersView(context);
        params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height);
        params.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
        backgroundParamsView.setLayoutParams(params);

        wallpaperViewBlurPreview = new WallpaperView(context, manager, tdlib);
        wallpaperViewBlurPreview.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallpaperViewBlurPreview.setAlpha(0f);

        if (inWallpaperPreviewMode() && currentBackgroundObj != null) {
          backgroundParamsView.initWith(currentBackgroundObj, new WallpaperParametersView.WallpaperParametersListener() {
            @Override
            public void onBlurValueAnimated (float factor) {
              wallpaperViewBlurPreview.setAlpha(MathUtils.clamp(factor));
            }
          });

          wallpaperViewBlurPreview.initWithCustomWallpaper(new TGBackground(tdlib, currentBackgroundObj, !backgroundParamsView.isBlurred()));
          messagesView.setTranslationY(-height);
        } else {
          backgroundParamsView.initWith(currentBackground, new WallpaperParametersView.WallpaperParametersListener() {
            @Override
            public void onParametersViewScaleChanged (float factor) {
              float clamped = MathUtils.clamp(factor);
              backgroundParamsView.setTranslationY(height * (1f - clamped));
              messagesView.setTranslationY(-(height * clamped));
            }

            @Override
            public void onBlurValueAnimated (float factor) {
              wallpaperViewBlurPreview.setAlpha(MathUtils.clamp(factor));
            }
          });

          boolean previewBlurValue = !backgroundParamsView.isBlurred();
          wallpaperViewBlurPreview.initWithCustomWallpaper(TGBackground.newBlurredWallpaper(tdlib, currentBackground, previewBlurValue));
          wallpaperViewBlurPreview.setSelfBlur(previewBlurValue);
          wallpaperView.setSelfBlur(!previewBlurValue);
          backgroundParamsView.setParametersAvailability(currentBackground != null && currentBackground.isWallpaper(), false);
          if (currentBackground != null && currentBackground.isWallpaper()) {
            messagesView.setTranslationY(-height);
          }
        }
      }
    }

    // Bottom bar

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

    bottomBar = new ChatBottomBarView(context, tdlib) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateBottomBarStyle();
      }
    };
    bottomBar.setOnClickListener(this);
    bottomBar.setLayoutParams(params);
    addThemeInvalidateListener(bottomBar);
    updateBottomBarStyle();

    if (previewMode == PREVIEW_MODE_WALLPAPER_OBJECT) {
      showBottomButton(BOTTOM_ACTION_APPLY_WALLPAPER, 0, false);
      bottomShadowView.setVisibility(View.GONE);
    }

    // Setup

    contentView.addView(wallpaperView);
    if (wallpaperViewBlurPreview != null) {
      contentView.addView(wallpaperViewBlurPreview);
    }
    if (!inPreviewMode) {
      contentView.addView(replyBarView);
    }
    contentView.addView(bottomWrap);
    contentView.addView(messagesView);
    contentView.addView(bottomShadowView);

    contentView.addView(topBar);
    contentView.addView(bottomBar);
    contentView.addView(reactionsButtonWrap);
    contentView.addView(mentionButtonWrap);
    contentView.addView(scrollToBottomButtonWrap);
    contentView.addView(goToNextFoundMessageButtonBadge);
    contentView.addView(goToPrevFoundMessageButtonBadge);

    if (previewMode == PREVIEW_MODE_NONE) {
      contentView.addView(emojiButton);
      contentView.addView(attachButtons);
      contentView.addView(sendButton);
      contentView.addView(messageSenderButton);

      initSearchControls();
      contentView.addView(searchControlsLayout);

      addStaticListeners();
    }

    if (backgroundParamsView != null) {
      contentView.addView(backgroundParamsView, 2);
    }

    contentView.addView(searchByUserViewWrapper);

    updateView();

    TGLegacyManager.instance().addEmojiListener(this);

    if (needTabs()) {
      pagerHeaderView = new ViewPagerHeaderViewCompact(context);
      pagerHeaderView.getTopView().setShowLabelOnActiveOnly(!Settings.instance().needReduceMotion());
      addThemeInvalidateListener(pagerHeaderView.getTopView());
      fparams = (FrameLayoutFix.LayoutParams) pagerHeaderView.getRecyclerView().getLayoutParams();
      fparams.leftMargin = Screen.dp(56f);
      fparams.rightMargin = Screen.dp(56f);
      pagerHeaderView.getTopView().setOnItemClickListener(this);
      addThemeInvalidateListener(pagerHeaderView.getTopView());

      List<SharedBaseController<?>> mediaControllers = new ArrayList<>(8);
      ProfileController.fillMediaControllers(mediaControllers, context(), tdlib());
      List<ViewPagerTopView.Item> items = new ArrayList<ViewPagerTopView.Item>(mediaControllers.size() + 1);
      items.add(new ViewPagerTopView.Item(
        Lang.getString(R.string.TabMessages).toUpperCase(),
        R.drawable.baseline_chat_bubble_24,
        null
      ));
      for (SharedBaseController<?> c : mediaControllers) {
        items.add(new ViewPagerTopView.Item(
          c.getName().toString().toUpperCase(),
          c.getIcon(),
          null
        ));
      }
      pagerHeaderView.getTopView().setItems(items);

      pagerContentAdapter = new MediaTabsAdapter(this, mediaControllers);

      pagerContentView = new RtlViewPager(context) {
        boolean blocked;

        @Override
        public boolean onInterceptTouchEvent (MotionEvent ev) {
          if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            blocked = false;
          }
          if (!blocked) {
            blocked = getCurrentItem() == 0 && (UI.getContext(MessagesController.this.context()).getRecordAudioVideoController().isOpen() || (inputView != null && inputView.getInlineSearchContext().isVisibleOrActive()));
          }

          return !blocked && super.onInterceptTouchEvent(ev);
        }
      };
      pagerContentView.setOffscreenPageLimit(1);
      pagerContentView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
      pagerContentView.addOnPageChangeListener(this);
      pagerContentView.setAdapter(pagerContentAdapter);
      pagerContentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      FrameLayoutFix contentView = new FrameLayoutFix(context);
      contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      contentView.addView(pagerContentView);

      return contentView;

    }

    return contentView;
  }

  private SliderView fontSliderView;
  private SliderView blurSliderView;

  private static void updateFontSliderValue (SliderView sliderView) {
    boolean found = false;
    int index = 0;
    for (float dp : Settings.CHAT_FONT_SIZES) {
      if (dp == Settings.instance().getChatFontSize()) {
        found = true;
        break;
      }
      index++;
    }
    if (found) {
      sliderView.setValue((float) index / (float) (Settings.CHAT_FONT_SIZES.length - 1));
    }
  }

  private static void updateWpBlurSliderValue (SliderView sliderView) {
    boolean found = false;
    int index = 0;
    for (float dp : Settings.CHAT_FONT_SIZES) {
      if (dp == Settings.instance().getChatFontSize()) {
        found = true;
        break;
      }
      index++;
    }
    if (found) {
      sliderView.setValue((float) index / (float) (Settings.CHAT_FONT_SIZES.length - 1));
    }
  }

  // SELF-CHAT

  private static class MediaTabsAdapter extends PagerAdapter {
    private final MessagesController context;

    boolean isLocked;
    private final List<SharedBaseController<?>> mediaControllers;

    public MediaTabsAdapter (MessagesController context, List<SharedBaseController<?>> mediaControllers) {
      this.context = context;
      this.mediaControllers = mediaControllers;
    }

    @Override
    public void destroyItem (ViewGroup container, int position, Object object) {
      if (object instanceof MessagesController) {
        container.removeView(((MessagesController) object).contentView);
      } else {
        container.removeView(((ViewController<?>) object).getValue());
      }
    }

    @Override
    public int getCount () {
      return isLocked ? 1 : 1 + mediaControllers.size();
    }

    private static final int POSITION_MESSAGES = 0;
    private static final int POSITION_MEDIA = 1;

    private final SparseArrayCompat<SharedBaseController<?>> cachedItems = new SparseArrayCompat<>();

    public void destroy () {
      final int size = cachedItems.size();
      for (int i = 0; i < size; i++) {
        if (!cachedItems.valueAt(i).isDestroyed())
          cachedItems.valueAt(i).destroy();
      }
      cachedItems.clear();
    }

    @Override
    public Object instantiateItem (ViewGroup container, int position) {
      if (position == 0) {
        container.addView(context.contentView);
        return context;
      }
      SharedBaseController<?> c = cachedItems.get(position);
      if (c == null) {
        c = mediaControllers.get(position - 1);
        c.setArguments(new SharedBaseController.Args(context.getChatId(), context.getMessageThreadId()));
        c.setParent(context);
        cachedItems.put(position, c);
        c.bindThemeListeners(context);
        String input = context.lastMediaSearchQuery;
        if (!StringUtils.isEmpty(input)) {
          c.getValue();
          c.search(input);
        }
      }
      container.addView(c.getValue());
      return c;
    }

    @Override
    public boolean isViewFromObject (View view, Object o) {
      if (o instanceof MessagesController) {
        return ((MessagesController) o).contentView == view;
      } else {
        return o instanceof ViewController && ((ViewController<?>) o).getValue() == view;
      }
    }
  }

  private MediaTabsAdapter pagerContentAdapter;
  private RtlViewPager pagerContentView;
  private ViewPagerHeaderViewCompact pagerHeaderView;

  private float pagerScrollOffset;
  private int pagerScrollPosition;

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    positionOffset = ViewPager.clampPositionOffset(positionOffset);
    float offset = (float) position + positionOffset;
    pagerHeaderView.getTopView().setSelectionFactor(offset);
    if (this.pagerScrollOffset != offset) {
      this.pagerScrollOffset = offset;
      if (hideKeyboardOnPageScroll) {
        hideKeyboardOnPageScroll = false;
        hideSoftwareKeyboard();
      }
      checkPagerInputBlocked();
      checkRoundVideo();
      checkInlineResults();
    }
  }

  @Override
  public void onPageSelected (int position) {
    if (this.pagerScrollPosition != position) {
      this.pagerScrollPosition = position;
      checkPagerInputBlocked();
    }
  }

  private boolean pagerInputBlocked;

  private void checkPagerInputBlocked () {
    boolean blocked = pagerScrollOffset > 0f || pagerScrollPosition != 0;
    if (pagerInputBlocked != blocked) {
      pagerInputBlocked = blocked;
      if (blocked) {
        Keyboard.hide(inputView);
      }
      setInputBlockFlag(FLAG_INPUT_OFFSCREEN, blocked);
    }
  }

  private int pagerScrollState = ViewPager.SCROLL_STATE_IDLE;
  private boolean hideKeyboardOnPageScroll;

  @Override
  public void onPageScrollStateChanged (int scrollState) {
    if (pagerScrollState != scrollState) {
      boolean wasScrolling = pagerScrollState != ViewPager.SCROLL_STATE_IDLE;
      boolean nowScrolling = scrollState != ViewPager.SCROLL_STATE_IDLE;
      this.pagerScrollState = scrollState;
      if (wasScrolling != nowScrolling && nowScrolling) {
        hideKeyboardOnPageScroll = pagerScrollOffset % 1f == 0f;
        if (!hideKeyboardOnPageScroll) {
          hideSoftwareKeyboard();
        }
      }
      if (pagerHeaderView != null && scrollState != ViewPager.SCROLL_STATE_SETTLING) {
        pagerHeaderView.getTopView().resetFromTo();
      }
    }
  }

  @Override
  public void onPagerItemClick (int position) {
    if (pagerContentView.getCurrentItem() == 0 && UI.getContext(MessagesController.this.context()).getRecordAudioVideoController().isOpen()) {
      return;
    }
    if (pagerContentView.getCurrentItem() != position) {
      pagerHeaderView.getTopView().setFromTo(pagerContentView.getCurrentItem(), position);
      pagerContentView.setCurrentItem(position, true);
    }
  }

  private void showMessagesListIfNeeded () {
    if (pagerScrollPosition != 0 && pagerContentView != null) {
      pagerContentView.setCurrentItem(0, true);
    }
  }

  @Override
  protected void onEnterSelectMode () {
    if (pagerContentView != null) {
      pagerContentView.setPagingEnabled(false);
    }
  }

  @Override
  public void onLeaveSelectMode () {
    if (pagerContentView != null) {
      pagerContentView.setPagingEnabled(true);
    }
    if (exitOnTransformFinish)
      tdlib.ui().post(this::navigateBack);
  }

  // SELF-CHAT END

  private void addStaticListeners () {
    Settings.instance().addVideoPreferenceChangeListener(this);
    context().getRecordAudioVideoController().addRecordStateListener(this);
  }

  public void removeStaticListeners () {
    Settings.instance().removeVideoPreferenceChangeListener(this);
    context().getRecordAudioVideoController().removeRecordStateListener(this);
  }

  @Override
  public void onRecordStateChanged (boolean isRecording) {
    setInputBlockFlag(FLAG_INPUT_RECORDING, isRecording);
  }

  public MessagesManager getManager () {
    return manager;
  }

  public TdApi.Chat getChat () {
    return chat;
  }

  public long getActiveChatId () {
    return isFocused() && !isPaused() && manager.isFocused() ? getChatId() : 0l;
  }

  @Override
  public long getChatId () {
    return chat != null ? chat.id : 0l;
  }

  public final long getHeaderChatId () {
    return messageThread != null ? messageThread.getContextChatId() : getChatId();
  }

  public long getMessageThreadId () {
    return messageThread != null ? messageThread.getMessageThreadId() : 0l;
  }

  @Nullable
  public ThreadInfo getMessageThread () {
    return messageThread;
  }

  @Nullable
  public TdApi.DraftMessage getDraftMessage () {
    return messageThread != null ? messageThread.getDraft() : chat != null ? chat.draftMessage : null;
  }

  public long getChatUserId () {
    return TD.getUserId(chat);
  }

  public boolean compareChat (long chatId, @Nullable ThreadInfo threadInfo) {
    return getChatId() == chatId && ((this.messageThread == null && threadInfo == null) || (this.messageThread != null && this.messageThread.equals(threadInfo)));
  }

  public boolean compareChat (long chatId, long messageThreadId) {
    return getChatId() == chatId && ((messageThreadId != 0 && messageThread != null && messageThread.getMessageThreadId() == messageThreadId) || (messageThreadId == 0 && messageThread == null));
  }

  public boolean compareChat (long chatId) {
    return getChatId() == chatId;
  }

  public boolean compareChat (long chatId, @Nullable ThreadInfo threadInfo, boolean areScheduledOnly) {
    return getChatId() == chatId && ((this.messageThread == null && threadInfo == null) || (this.messageThread != null && this.messageThread.equals(threadInfo))) && areScheduledOnly == areScheduledOnly();
  }

  public boolean isChannel () {
    return tdlib.isChannel(getChatId());
  }

  public boolean comparePrivateUserId (long userId) {
    return userId != 0 && ChatId.toUserId(getChatId()) == userId;
  }

  public MessagesRecyclerView getMessagesView () {
    return messagesView;
  }

  private boolean isInputLess () {
    return tdlib.isChannel(getChatId()) && !tdlib.canSendBasicMessage(chat);
  }

  @Override
  public void unlock () {
    if (enableOnResume) {
      enableOnResume = false;
    }
    if (inputView != null && !inputView.isEnabled() && !isInputLess()) {
      inputView.setEnabled(true);
      inputView.requestFocus();
    }
  }

  public @Nullable InlineResultsWrap.PickListener getInlineResultListener () {
    return canWriteMessages() ? inputView : null;
  }

  public void removeInlineBot (long userId) {
    if (inputView != null) {
      inputView.getInlineSearchContext().removeInlineBot(userId);
    }
  }

  @Override
  public void onChatHeaderClick () {
    if (chat != null) {
      if (Test.NEED_CLICK) {
        if (Test.onChatClick(tdlib, chat)) {
          return;
        }
      }
      if (inPreviewSearchMode()) {
        ViewController<?> parent = getParentOrSelf();
        if (parent instanceof ViewPagerController<?>) {
          int index = ((ViewPagerController<?>) parent).getCurrentPagerItemPosition() == 1 ? 0 : 1;
          ((ViewPagerController<?>) parent).onPagerItemClick(index);
        }
      } else {
        if (messageThread != null && messageThread.getChatId() != messageThread.getContextChatId()) {
          TdApi.Chat contextChat = tdlib.chatSync(messageThread.getContextChatId());
          if (contextChat != null) {
            openChatProfile(contextChat);
          } else {
            openChatProfile(messageThread.getContextChatId());
          }
        } else {
          openChatProfile(chat);
        }
      }
    }
  }

  private void openChatProfile (TdApi.Chat chat) {
    ProfileController controller = new ProfileController(context, tdlib);
    controller.setShareCustomHeaderView(true);
    controller.setArguments(new ProfileController.Args(chat, /* threadInfo */ null, false));
    navigateTo(controller);
  }

  private void openChatProfile (long chatId) {
    tdlib.ui().openChatProfile(this, chatId, /* messageThread */ null, null);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return manager != null && !manager.isTotallyEmpty() && manager.getAdapter().getMessageCount() == 0;
  }

  private void openLinkedChat () {
    if (messageThread != null && messageThread.getChatId() != 0 && messageThread.getChatId() != messageThread.getContextChatId()) {
      tdlib.ui().openChat(this, messageThread.getChatId(), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
    } else {
      tdlib.ui().openLinkedChat(this, ChatId.toSupergroupId(getChatId()), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates());
    }
  }

  private void manageGroup () {
    ProfileController controller = new ProfileController(context, tdlib);
    controller.setArguments(new ProfileController.Args(chat, /*messageThread*/ null, true));
    navigateTo(controller);
  }

  private void deleteThread () {
    if (messageThread == null)
      return;
    TdApi.Message message = messageThread.getOldestMessage();
    tdlib.getMessageProperties(message, properties -> {
      if (properties.canGetMessageThread && properties.canBeDeletedForAllUsers) {
        tdlib.deleteMessages(messageThread.getChatId(), new long[]{message.id}, true);
      }
    });
  }

  private void joinChat () {
    if (referrer != null) {
      tdlib.client().send(new TdApi.JoinChatByInviteLink(referrer.inviteLink), result -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          tdlib.client().send(new TdApi.AddChatMember(chat.id, tdlib.myUserId(), 0), tdlib.okHandler());
        }
      });
    } else {
      tdlib.client().send(new TdApi.AddChatMember(chat.id, tdlib.myUserId(), 0), tdlib.okHandler());
    }
  }

  private boolean preventHideKeyboard;

  public void viewScheduledMessages (boolean force) {
    MessagesController c = new MessagesController(context, tdlib);
    boolean keyboardVisible = getKeyboardState();
    c.setArguments(new Arguments(openedFromChatList, chat, /* messageThread */ null, null, MessagesManager.HIGHLIGHT_MODE_NONE, null).setScheduled(true).setOpenKeyboard(keyboardVisible));
    if (force) {
      c.forceFastAnimationOnce();
    }
    if (keyboardVisible) {
      preventHideKeyboardOnBlur();
      preventHideKeyboard = true;
    }
    navigateTo(c);
  }

  public void viewMessagesFromSender (TdApi.MessageSender sender, boolean animated) {
    if (headerView != null) {
      headerView.openSearchMode(animated, false);
      onSetSearchMessagesSenderId(sender);
      onSetSearchFilteredShowMode(true);
      searchChatMessages(getLastMessageSearchQuery());
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (inPreviewMode) {
      if (viewId == R.id.btn_help) {
        context()
          .tooltipManager()
          .builder(v)
          .icon(R.drawable.baseline_info_24)
          .controller(this)
          .needBlink(true)
          .chatTextSize()
          .show(tdlib, tdlib.isChannel(chat.id) ? R.string.EventLogInfoDetailChannel : R.string.EventLogInfoDetail);
      } else if (viewId == R.id.btn_chatAction) {
        if (previewMode == PREVIEW_MODE_EVENT_LOG) {
          processChatAction();
        }
      } else if (viewId == R.id.btn_scroll) {
        scrollToUnreadOrStartMessage();
      }
    }
    if (viewId == R.id.btn_camera) {
      if (!showPhotoVideoRestriction(v)) {
        openInAppCamera(new CameraOpenOptions().anchor(v).noTrace(isSecretChat()));
      }
    } else if (viewId == R.id.btn_viewScheduled) {
      viewScheduledMessages(false);
    } else if (viewId == R.id.msg_bottomReply) {
      if (reply != null) {
        highlightMessage(reply.toMessageId());
      }
    } else if (viewId == R.id.btn_mute) {
      if (chat != null) {
        tdlib.ui().toggleMute(this, chat.id, false, null);
      }
    } else if (viewId == R.id.btn_openLinkedChat) {
      openLinkedChat();
    } else if (viewId == R.id.btn_test) {
      bottomBar.setAction(R.id.btn_test_crash1, "try again", R.drawable.baseline_remove_circle_24, true);
    } else if (viewId == R.id.btn_test_crash1) {
      bottomBar.setAction(R.id.btn_test, "test", R.drawable.baseline_warning_24, true);
    } else if (viewId == R.id.btn_follow) {
      joinChat();
    } else if (viewId == R.id.btn_unpinAll) {
      dismissPinnedMessage();
    } else if (viewId == R.id.btn_applyWallpaper) {
      TdApi.BackgroundType newBackgroundType = getArgumentsStrict().wallpaperObject.type;

      if (newBackgroundType.getConstructor() == TdApi.BackgroundTypeWallpaper.CONSTRUCTOR && backgroundParamsView != null) {
        ((TdApi.BackgroundTypeWallpaper) newBackgroundType).isBlurred = backgroundParamsView.isBlurred();
      }

      tdlib().send(new TdApi.SetDefaultBackground(
        new TdApi.InputBackgroundRemote(getArgumentsStrict().wallpaperObject.id),
        newBackgroundType,
        Theme.isDark()
      ), (result, error) -> {
        if (result != null) {
          runOnUiThread(() -> {
            TGBackground bg = new TGBackground(tdlib(), result);
            tdlib.wallpaper().addBackground(bg, Theme.isDark());
            tdlib.settings().setWallpaper(bg, true, Theme.getWallpaperIdentifier());
            navigateBack();
          });
        }
      });
    } else if (viewId == R.id.btn_silent) {
      if (tdlib.isChannel(chat.id)) {
        boolean silent = silentButton.toggle();
        tdlib.client().send(new TdApi.ToggleChatDefaultDisableNotification(chat.id, silent), tdlib.okHandler());
        int[] pos = new int[2];
        Views.getPosition(bottomWrap, pos);
        UI.showCustomToast(silentButton.getIsSilent() ? R.string.ChannelNotifyMembersInfoOff : R.string.ChannelNotifyMembersInfoOn, Toast.LENGTH_SHORT, pos[1] <= Screen.currentHeight() / 2 + Screen.dp(60f) ? -(contentView.getMeasuredHeight() - bottomWrap.getTop() + Screen.dp(14f)) : 0);
        if (inputView != null) {
          updateInputHint();
        }
      }
    } else if (viewId == R.id.btn_chatAction) {
      processChatAction();
    } else if (viewId == R.id.msg_emoji) {
      toggleEmojiKeyboard();
    } else if (viewId == R.id.msg_attach) {
      hideBottomHint();
      openMediaView(false, false);
    } else if (viewId == R.id.msg_send) {
      if (!leaveInlineMode()) {
        if (inputView != null && !isSelfChat() && !tdlib.hasPremium() && inputView.hasOnlyPremiumFeatures()) {
          showBottomHint(Strings.buildMarkdown(this, Lang.getString(R.string.MessageContainsPremiumFeatures), null), false);
        } else if (isEditingMessage()) {
          saveMessage(true);
        } else if (areScheduled) {
          tdlib.ui().showScheduleOptions(this, getChatId(), false, (modifiedSendOptions, disableMarkdown) -> send(modifiedSendOptions), null, null);
        } else {
          send(null);
        }
      }
    } else if (viewId == R.id.btn_scroll) {
      scrollToUnreadOrStartMessage();
    } else if (viewId == R.id.btn_mention) {
      manager.scrollToNextMention();
    } else if (viewId == R.id.btn_reaction) {
      manager.scrollToNextUnreadReaction();
    } else if (viewId == R.id.msg_command) {
      // FIXME: rely on some state, not on icon id
      if (lastCmdResource == R.drawable.deproko_baseline_bots_command_26) {
        onCommandClick();
      } else if (lastCmdResource == R.drawable.deproko_baseline_bots_keyboard_26 ||
        lastCmdResource == R.drawable.baseline_direction_arrow_down_24 ||
        lastCmdResource == R.drawable.baseline_keyboard_24) {
        toggleCommandsKeyboard();
      }
    } else if (viewId == R.id.btn_search_prev) {
      manager.moveToNextResult(false);
    } else if (viewId == R.id.btn_search_next) {
      manager.moveToNextResult(true);
    }
  }

  private boolean leaveInlineMode () {
    if (sendButton.inInlineMode()) {
      CharSequence currentUsername = inputView.getInlineSearchContext().getInlineUsername();
      if (currentUsername != null) {
        currentUsername = "@" + currentUsername + " ";
        if (inputView.getText().toString().equals(currentUsername.toString())) {
          inputView.setInput("", false, true);
        } else {
          inputView.setInput(currentUsername, true, true);
        }
      }
      return true;
    }
    return false;
  }

  private void send (TdApi.MessageSendOptions sendOptions) {
    send(sendOptions, true);
  }

  private void send (TdApi.MessageSendOptions sendOptions, boolean applyMarkdown) {
    if (isEditingMessage()) {
      saveMessage(applyMarkdown);
    } else if (hasAttachedFiles()) {
      if (isSendingText) {
        return;
      }

      final TdApi.FormattedText caption = inputView != null ? inputView.getOutputText(applyMarkdown) : null;
      final ArrayList<InlineResult<?>> selectedItems = attachedFiles.getCurrentItems();

      final ArrayList<MediaBottomFilesController.MusicEntry> musicEntries = new ArrayList<>();
      final ArrayList<String> files = new ArrayList<>();

      for (InlineResult<?> result : selectedItems) {
        switch (result.getType()) {
          case InlineResult.TYPE_AUDIO: {
            musicEntries.add((MediaBottomFilesController.MusicEntry) ((InlineResultCommon) result).getTag());
            break;
          }
          case InlineResult.TYPE_DOCUMENT: {
            files.add(result.getId());
            break;
          }
        }
      }

      if (musicEntries.isEmpty() && files.isEmpty()) {
        return;
      }

      final List<TdApi.Message> sentMessages = new ArrayList<>(selectedItems.size());
      final TdApi.InputMessageReplyTo replyTo = getCurrentReplyId();
      final ArrayList<TdApi.Function<?>> functions = new ArrayList<>();
      final boolean[] isTimeout = new boolean[1];
      final long chatId = getChatId();

      setIsSendingText(true);
      manager.setSentMessages(sentMessages);
      Runnable clearInputRunnable = () -> {
        if (getChatId() == chatId) {
          clearInputAfterSend(true, true, replyTo, true);
          UI.showToast(Lang.getString(R.string.SlowFileAccess), Toast.LENGTH_LONG);
          isTimeout[0] = true;
        }
      };

      UI.post(clearInputRunnable, MathUtils.clamp(50 * selectedItems.size(), 200, 500));

      final List<TdApi.Function<?>> musicFunctions = getSendMusicFunctions(sendButton, musicEntries, true, !musicEntries.isEmpty(), caption, sendOptions);
      sendFiles(sendButton, files, true, true, !musicEntries.isEmpty() && !files.isEmpty() ? null : caption, sendOptions, filesFunctions -> {
        if (filesFunctions != null) {
          functions.addAll(filesFunctions);
        }
        if (musicFunctions != null) {
          functions.addAll(musicFunctions);
        }
        executeSendMessageFunctions(functions, sentMessages, sendOptions != null && sendOptions.schedulingState != null, success -> UI.post(() -> {
          if (!isTimeout[0] && getChatId() == chatId) {
            clearInputAfterSend(true, true, replyTo, true);
          }
          UI.cancel(clearInputRunnable);
        }));
      });
    } else {
      sendText(applyMarkdown, sendOptions);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (tdlib.ui().processLeaveButton(this, null, getChatId(), id, null)) {
      return;
    }
    if (id == R.id.btn_copyLink || id == R.id.btn_share) {
      tdlib.client().send(new TdApi.GetBackgroundUrl(getArgumentsStrict().wallpaperObject.name, TGBackground.makeBlurredBackgroundType(getArgumentsStrict().wallpaperObject.type, backgroundParamsView != null && backgroundParamsView.isBlurred())), result -> {
        if (result.getConstructor() == TdApi.HttpUrl.CONSTRUCTOR) {
          TdApi.HttpUrl url = (TdApi.HttpUrl) result;
          runOnUiThreadOptional(() -> {
            if (id == R.id.btn_copyLink) {
              UI.copyText(url.url, R.string.CopiedLink);
            } else {
              ShareController c = new ShareController(context(), tdlib());
              c.setArguments(new ShareController.Args(url.url));
              c.show();
              hideCursorsForInputView();
            }
          });
        }
      });
    } else if (id == R.id.btn_openLinkedChat) {
      openLinkedChat();
    } else if (id == R.id.btn_manageGroup) {
      manageGroup();
    } else if (id == R.id.btn_deleteThread) {
      deleteThread();
    } else if (id == R.id.btn_viewScheduled) {
      viewScheduledMessages(false);
    } else if (id == R.id.btn_sendScreenshotNotification) {
      UI.showToast("Sent screenshot notification", Toast.LENGTH_SHORT);
      tdlib.onScreenshotTaken((int) (System.currentTimeMillis() / 1000l));
    } else if (id == R.id.btn_debugShowHideBottomBar) {
      if (bottomButtonAction == BOTTOM_ACTION_NONE) {
        showBottomButton(BOTTOM_ACTION_TEST, 0, true);
      } else {
        hideBottomBar(true);
      }
    } else if (id == R.id.btn_chatFontSizeScale) {
      Settings.instance().toggleChatFontSizeScaling();
      manager.rebuildLayouts();
    } else if (id == R.id.btn_chatFontSizeReset) {
      Settings.instance().resetChatFontSize();
      updateFontSliderValue(fontSliderView);
      manager.rebuildLayouts();
    } else if (id == R.id.btn_botHelp || id == R.id.btn_botSettings) {
      if (chat != null) {
        if (tdlib.chatFullyBlocked(chat.id)) {
          tdlib.unblockSender(tdlib.sender(chat.id), tdlib.okHandler());
        }
        if (actionMode == ACTION_BOT_START) {
          hideActionButton();
        }
        sendText(id == R.id.btn_botHelp ? "/help" : "/settings", false, false, false, Td.newSendOptions());
      }
    } else if (id == R.id.btn_showPinnedMessage) {
      manager.restorePinnedMessage();
    } else if (id == R.id.btn_shareMyContact) {
      TdApi.User user = tdlib.myUser();
      if (user == null) {
        return;
      }
      showOptions(TD.getUserName(user) + ", " + Strings.formatPhone(user.phoneNumber), new int[] {R.id.btn_shareMyContact, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ShareMyContactInfo), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, new int[] {R.drawable.baseline_contact_phone_24, R.drawable.baseline_cancel_24}, (itemView, id1) -> {
        if (id1 == R.id.btn_shareMyContact) {
          sendContact(tdlib.myUser(), true, Td.newSendOptions());
        }
        return true;
      });
    } else if (id == R.id.btn_reportChat) {
      reportChat(null, null);
    } else if (id == R.id.btn_search) {
      if (manager.isReadyToSearch()) {
        openSearchMode();
      }
    } else if (id == R.id.btn_mute) {
      if (chat != null) {
        tdlib.ui().toggleMute(this, chat.id, false, null);
      }
    }
  }

  private void reportChat (@Nullable MessageWithProperties[] messages, final @Nullable Runnable after) {
    TdApi.Message[] array;
    if (messages != null) {
      array = new TdApi.Message[messages.length];
      for (int i = 0; i < array.length; i++) {
        array[i] = messages[i].message;
      }
    } else {
      array = null;
    }
    TdlibUi.reportChat(this, getChatId(), array, null, after, true);
  }

  private void reportSponsoredMessage (TdApi.SponsoredMessage sponsoredMessage, byte[] optionId) {
    tdlib.send(new TdApi.ReportChatSponsoredMessage(getChatId(), sponsoredMessage.messageId, optionId), (result, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      switch (result.getConstructor()) {
        case TdApi.ReportChatSponsoredMessageResultOk.CONSTRUCTOR:
          break;
        case TdApi.ReportChatSponsoredMessageResultFailed.CONSTRUCTOR:
        case TdApi.ReportChatSponsoredMessageResultOptionRequired.CONSTRUCTOR:
        case TdApi.ReportChatSponsoredMessageResultAdsHidden.CONSTRUCTOR:
        case TdApi.ReportChatSponsoredMessageResultPremiumRequired.CONSTRUCTOR:
      }
    }));
  }

  public static final int PREVIEW_MODE_NONE = 0;
  public static final int PREVIEW_MODE_WALLPAPER = 1;
  public static final int PREVIEW_MODE_FONT_SIZE = 2;
  public static final int PREVIEW_MODE_EVENT_LOG = 3;
  public static final int PREVIEW_MODE_SEARCH = 4;
  public static final int PREVIEW_MODE_WALLPAPER_OBJECT = 5;

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    Arguments args = getArguments();
    if (args != null) {
      super.saveInstanceState(outState, keyPrefix);
      outState.putInt(keyPrefix + "type", args.constructor);
      outState.putLong(keyPrefix + "chat_id", args.chat != null ? args.chat.id : 0);
      outState.putString(keyPrefix + "chat_list", args.chatList != null ? TD.makeChatListKey(args.chatList) : "");
      if (args.messageThread != null) {
        args.messageThread.saveTo(outState, keyPrefix + "thread");
      }
      Td.put(outState, keyPrefix + "filter_", args.searchFilter);
      if (args.constructor == 1 || args.constructor == 4) {
        outState.putInt(keyPrefix + "mode", args.highlightMode);
        if (args.highlightMessageId != null) {
          outState.putLong(keyPrefix + "message_id", args.highlightMessageId.getMessageId());
          outState.putLong(keyPrefix + "message_chat_id", args.highlightMessageId.getChatId());
        }
      }
      if (args.constructor == 2) {
        outState.putInt(keyPrefix + "mode", args.previewMode);
      }
      if (args.constructor == 3 || args.constructor == 4) {
        outState.putString(keyPrefix + "query", args.searchQuery);
        Td.put(outState, keyPrefix + "sender_", args.searchSender);
      }
      outState.putBoolean(keyPrefix + "scheduled", args.areScheduled);
      return true;
    }
    return false;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    int constructor = in.getInt(keyPrefix + "type", 0);
    long chatId = in.getLong(keyPrefix + "chat_id", 0);
    TdApi.Chat chat = tdlib.chatSync(chatId);
    if (chatId != 0 && chat == null)
      return false;
    TdApi.ChatList chatList = TD.chatListFromKey(in.getString(keyPrefix + "chat_list", null));
    ThreadInfo messageThread = ThreadInfo.restoreFrom(tdlib, in, keyPrefix + "thread");
    if (messageThread == ThreadInfo.INVALID)
      return false;
    TdApi.SearchMessagesFilter filter = Td.restoreSearchMessagesFilter(in, keyPrefix + "filter_");
    Arguments args = null;
    switch (constructor) {
      case 0: {
        args = new Arguments(tdlib, chatList, chat, messageThread, filter);
        break;
      }
      case 1: {
        int highlightMode = in.getInt(keyPrefix + "mode", 0);
        long highlightMessageId = in.getLong(keyPrefix + "message_id", 0);
        long highlightMessageChatId = in.getLong(keyPrefix + "message_chat_id", 0);
        args = new Arguments(chatList, chat, messageThread, highlightMessageId != 0 ? new MessageId(highlightMessageChatId, highlightMessageId) : null, highlightMode, filter);
        break;
      }
      case 2: {
        int previewMode = in.getInt(keyPrefix + "mode", 0);
        args = new Arguments(previewMode, chatList, chat);
        break;
      }
      case 3: {
        String query = in.getString(keyPrefix + "query", null);
        TdApi.MessageSender sender = Td.restoreMessageSender(in, keyPrefix + "sender_");
        args = new Arguments(chatList, chat, query, sender, filter);
        break;
      }
      case 4: {
        String query = in.getString(keyPrefix + "query", null);
        TdApi.MessageSender sender = Td.restoreMessageSender(in, keyPrefix + "sender_");
        int highlightMode = in.getInt(keyPrefix + "mode", 0);
        long highlightMessageId = in.getLong(keyPrefix + "message_id", 0);
        long highlightMessageChatId = in.getLong(keyPrefix + "message_chat_id", 0);
        args = new Arguments(chatList, chat, query, sender, filter, highlightMessageId != 0 ? new MessageId(highlightMessageChatId, highlightMessageId) : null, highlightMode);
        break;
      }
    }
    if (args != null) {
      super.restoreInstanceState(in, keyPrefix);
      args.areScheduled = in.getBoolean(keyPrefix + "scheduled", false);
      setArguments(args);
      return true;
    }
    return false;
  }

  public static class Arguments {
    private final int constructor;

    public final TdApi.Chat chat;
    public final TdApi.ChatList chatList;
    public final MessageId highlightMessageId;
    public final int highlightMode;

    public final boolean inPreviewMode;
    public final int previewMode;

    public MessageId foundMessageId;
    public @Nullable String searchQuery;
    public TdApi.MessageSender searchSender;
    public TdApi.SearchMessagesFilter searchFilter;

    public boolean areScheduled, openKeyboard;

    public final @Nullable ThreadInfo messageThread;

    public Referrer referrer;
    public TdApi.InternalLinkTypeVideoChat videoChatOrLiveStreamInvitation;
    public TdApi.FormattedText fillDraft;

    public long eventLogUserId;

    public @Nullable TdApi.Background wallpaperObject;

    public Arguments (Tdlib tdlib, TdApi.ChatList chatList, TdApi.Chat chat, @Nullable ThreadInfo messageThread, TdApi.SearchMessagesFilter filter) {
      this.constructor = 0;
      this.chatList = chatList;
      this.chat = chat;
      this.messageThread = messageThread;
      this.highlightMode = MessagesManager.getAnchorHighlightMode(tdlib.id(), chat, messageThread);
      this.highlightMessageId = MessagesManager.getAnchorMessageId(tdlib.id(), chat, messageThread, highlightMode);
      this.searchFilter = filter;

      this.inPreviewMode = false;
      this.previewMode = 0;
    }

    public Arguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable ThreadInfo messageThread, MessageId highlightMessageId, int highlightMode, TdApi.SearchMessagesFilter filter) {
      this.constructor = 1;
      this.chatList = chatList;
      this.chat = chat;
      this.messageThread = messageThread;
      this.highlightMessageId = highlightMessageId;
      this.highlightMode = highlightMode;
      this.searchFilter = filter;

      this.inPreviewMode = false;
      this.previewMode = 0;
    }

    public Arguments (int previewMode, TdApi.ChatList chatList, TdApi.Chat chat) {
      this.constructor = 2;
      this.previewMode = previewMode;
      this.chat = chat;
      this.chatList = chatList;
      this.messageThread = null;

      this.inPreviewMode = true;
      this.highlightMode = MessagesManager.HIGHLIGHT_MODE_NONE;
      this.highlightMessageId = null;
    }

    public Arguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable String query, @Nullable TdApi.MessageSender sender, @Nullable TdApi.SearchMessagesFilter filter) {
      this.constructor = 3;
      this.chat = chat;
      this.chatList = chatList;
      this.messageThread = null;
      this.searchQuery = query;
      this.searchSender = sender;
      this.searchFilter = filter;

      this.inPreviewMode = true;
      this.previewMode = PREVIEW_MODE_SEARCH;
      this.highlightMode = 0;
      this.highlightMessageId = null;
    }

    public Arguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable String query, @Nullable TdApi.MessageSender sender, @Nullable TdApi.SearchMessagesFilter filter,  MessageId highlightMessageId, int highlightMode) {
      this.constructor = 4;
      this.chat = chat;
      this.chatList = chatList;
      this.messageThread = null;
      this.searchQuery = query;
      this.searchSender = sender;
      this.searchFilter = filter;

      this.inPreviewMode = true;
      this.previewMode = PREVIEW_MODE_SEARCH;
      this.highlightMessageId = highlightMessageId;
      this.highlightMode = highlightMode;
    }

    public Arguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable ThreadInfo messageThread, MessageId highlightMessageId, int highlightMode, TdApi.SearchMessagesFilter filter, MessageId foundMessageId, String globalSearchQuery) {
      this.constructor = 5;
      this.chatList = chatList;
      this.chat = chat;
      this.messageThread = messageThread;
      this.highlightMessageId = highlightMessageId;
      this.highlightMode = highlightMode;
      this.searchFilter = filter;

      this.foundMessageId = foundMessageId;
      this.searchQuery = globalSearchQuery;

      this.inPreviewMode = false;
      this.previewMode = 0;
    }

    public Arguments (TdApi.ChatList chatList, TdApi.Chat chat, @Nullable TdApi.MessageSender sender) {
      this.constructor = 6;
      this.chatList = chatList;
      this.chat = chat;
      this.highlightMessageId = null;
      this.highlightMode = 0;
      this.searchSender = sender;
      this.inPreviewMode = false;
      this.previewMode = 0;
      this.messageThread = null;
    }

    public Arguments referrer (Referrer referrer) {
      this.referrer = referrer;
      return this;
    }

    public Arguments setScheduled (boolean areScheduled) {
      if (areScheduled && messageThread != null) {
        throw new IllegalArgumentException();
      }
      this.areScheduled = areScheduled;
      return this;
    }

    public Arguments fillDraft (@Nullable TdApi.FormattedText fillDraft) {
      this.fillDraft = !Td.isEmpty(fillDraft) ? fillDraft : null;
      return this;
    }

    public Arguments setOpenKeyboard (boolean openKeyboard) {
      this.openKeyboard = openKeyboard;
      return this;
    }

    public Arguments setWallpaperObject (TdApi.Background wallpaperObject) {
      this.wallpaperObject = wallpaperObject;
      return this;
    }

    public Arguments voiceChatInvitation (TdApi.InternalLinkTypeVideoChat voiceChatInvitation) {
      this.videoChatOrLiveStreamInvitation = voiceChatInvitation;
      return this;
    }

    public Arguments eventLogUserId (long eventLogUserId) {
      this.eventLogUserId = eventLogUserId;
      return this;
    }
  }

  public static class Referrer {
    public final String inviteLink;

    public Referrer (String inviteLink) {
      this.inviteLink = inviteLink;
    }
  }

  public boolean areScheduledOnly () {
    return areScheduled;
  }

  private boolean inPreviewMode;
  private int previewMode;
  private @Nullable MessageId foundMessageId;
  private @Nullable MessageId searchFromUserMessageId;
  private @Nullable String previewSearchQuery;
  private TdApi.MessageSender previewSearchSender;
  private TdApi.SearchMessagesFilter previewSearchFilter;
  private ThreadInfo messageThread;
  private boolean areScheduled;
  private Referrer referrer;
  private TdApi.InternalLinkTypeVideoChat voiceChatInvitation;
  private TdApi.FormattedText fillDraft;
  private boolean openKeyboard;

  public boolean inWallpaperMode () {
    return inPreviewMode && (previewMode == PREVIEW_MODE_WALLPAPER || previewMode == PREVIEW_MODE_WALLPAPER_OBJECT);
  }

  public boolean inWallpaperPreviewMode () {
    return inPreviewMode && previewMode == PREVIEW_MODE_WALLPAPER_OBJECT;
  }

  public boolean inPreviewMode () {
    return inPreviewMode;
  }

  public boolean inTextSizeMode () {
    return inPreviewMode && previewMode == PREVIEW_MODE_FONT_SIZE;
  }

  private long linkedChatId;

  public void setArguments (Arguments args) {
    super.setArguments(args);

    this.chat = args.chat;
    this.customBotPlaceholder = null;
    this.customCaptionPlaceholder = null;
    this.messageThread = args.messageThread;
    this.openedFromChatList = args.chatList;
    this.linkedChatId = 0;
    this.areScheduled = args.areScheduled;
    this.referrer = args.referrer;
    this.voiceChatInvitation = args.videoChatOrLiveStreamInvitation;
    this.previewSearchQuery = args.searchQuery;
    this.previewSearchSender = args.searchSender;
    this.previewSearchFilter = args.searchFilter;
    this.manager.setHighlightMessageId(args.highlightMessageId, args.highlightMode);
    this.inPreviewMode = args.inPreviewMode;
    this.previewMode = args.previewMode;
    this.openKeyboard = args.openKeyboard;
    this.foundMessageId = args.foundMessageId;
    this.fillDraft = args.fillDraft;

    if (contentView != null) {
      updateView();
    }
  }

  private boolean isEventLog () {
    return inPreviewMode && previewMode == PREVIEW_MODE_EVENT_LOG;
  }

  private boolean ignoreDraftLoad;
  private Object itemToShare;

  public void setShareItem (Object shareItem) {
    this.itemToShare = shareItem;
    this.ignoreDraftLoad = shareItem != null && shouldIgnoreDraftLoad(shareItem);
  }

  private static boolean shouldIgnoreDraftLoad (Object object) {
    return object instanceof TGSwitchInline;
  }

  private void shareItem () {
    if (itemToShare != null) {
      shareItem(itemToShare);
      itemToShare = null;
    }
  }

  public void shareItem (Object item) {
    if (item instanceof InlineResultButton) {
      if (!hasSendBasicMessagePermission()) {
        return;
      }
      processSwitchPm((InlineResultButton) item);
      return;
    }

    if (item instanceof TGSwitchInline) {
      if (!hasSendBasicMessagePermission()) {
        return;
      }
      if (inputView != null) {
        inputView.setInput(item.toString(), true, false);
      }
      return;
    }

    if (item instanceof String) {
      sendText((String) item, false, false, false, Td.newSendOptions());
      return;
    }

    if (item instanceof TdApi.User) {
      sendContact((TdApi.User) item, true, Td.newSendOptions());
      return;
    }

    if (item instanceof TGBotStart) {
      TGBotStart start = (TGBotStart) item;

      if (start.isGame()) {
        tdlib.sendMessage(chat.id, getMessageThreadId(), null, Td.newSendOptions(obtainSilentMode()), new TdApi.InputMessageGame(start.getUserId(), start.getArgument()));
      } else if (start.useDeepLinking()) {
        if (!ChatId.isUserChat(chat.id) || start.ignoreExplicitUserInteraction()) {
          tdlib.sendBotStartMessage(start.getUserId(), chat.id, start.getArgument());
        } else {
          showActionBotButton(start.getArgument());
        }
        return;
      } else if (ChatId.isPrivate(chat.id)) {
        return;
      }

      tdlib.client().send(new TdApi.AddChatMember(chat.id, ((TGBotStart) item).getUserId(), 0), object -> {
        if (object.getConstructor() != TdApi.Ok.CONSTRUCTOR) {
          UI.showToast("Bot is already in chat", Toast.LENGTH_SHORT);
        }
      });

      return;
    }

    if (item instanceof TdApi.Message) {
      forwardMessage((TdApi.Message) item);
      return;
    }

    if (item instanceof TdApi.Audio) {
      sendAudio((TdApi.Audio) item, false);
      return;
    }
  }

  public void reloadData () {
    manager.loadFromStart();
  }

  private CancellableResultHandler adminsHandler;

  private void updateView () {
    if (selectedMessageIds != null) {
      clearSelectedMessageIds();
    }

    if (sendButton != null) {
      sendButton.getSlowModeCounterController(tdlib).setCurrentChat(getChatId());
      sendButton.getSlowModeCounterController(tdlib).setSlowModeCounterUpdateListener(this::onSlowModeCounterUpdate);
    }
    if (messageSenderButton != null) {
      messageSenderButton.setInSlowMode(tdlib.inSlowMode(getChatId()));
    }
    clearSwitchPmButton();
    clearReply();
    canSendMessageToUser = null;

    resetEditState();
    forceHideToast();
    topBar.hideAll(false);

    resetSearchControls();
    updateSelectMessageSenderInterface(false);
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.dismiss();
    }

    tdlib.ui().updateTTLButton(R.id.menu_secretChat, headerView, chat, true);
    messagesView.setMessageAnimatorEnabled(false);
    // messagesView.showDateForcely();

    if (botHelper != null) {
      botHelper.destroy();
      botHelper = null;
    }
    if (liveLocation != null) {
      liveLocation.destroy();
      liveLocation = null;
    }

    if (previewMode == PREVIEW_MODE_NONE) {
      updateForcedSubtitle(); // must be called before calling headerCell.setChat
      headerCell.setCallback(areScheduled ? null : this);
    }
    TdApi.Chat headerChat = messageThread != null ? tdlib.chatSync(messageThread.getContextChatId()) : null;
    headerCell.setChat(tdlib, headerChat != null ? headerChat : chat, messageThread);

    if (inPreviewMode) {
      switch (previewMode) {
        case PREVIEW_MODE_EVENT_LOG:
          showActionButton(R.string.Settings, ACTION_EVENT_LOG_SETTINGS);
          manager.openEventLog(chat);
          messagesView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 120l));
          if (getArgumentsStrict().eventLogUserId != 0 && headerCell != null) {
            manager.applyEventLogFilters(new TdApi.ChatEventLogFilters(true, true, true, true, true, true, true, true, true, true, true, true, true, true), new long[] { getArgumentsStrict().eventLogUserId });
          }
          break;
        case PREVIEW_MODE_SEARCH:
          manager.openSearch(chat, previewSearchQuery, previewSearchSender, previewSearchFilter);
          updateBottomBar(false);
          break;
        default:
          manager.loadPreview();
          break;
      }
      return;
    }

    setLockFocusView(chat != null && tdlib.canSendBasicMessage(chat) ? inputView : null, false);

    if (chat == null) {
      return;
    }
    if (chat.messageSenderId != null) {
      getChatAvailableMessagesSenders(null);
    }
    if (inputView != null) {
      // inputView.setIgnoreAnyChanges(true);
      boolean enabled = !isInputLess();
      if (inputView.isEnabled() != enabled) {
        inputView.setEnabled(enabled);
      }
      if (enabled) {
        inputView.setNoPersonalizedLearning(Settings.instance().needsIncognitoMode(chat));
      }
    }

    TdApi.DraftMessage draftMessage = null;
    if (tdlib.canSendBasicMessage(chat)) {
      draftMessage = getDraftMessage();
      if (!Td.isEmpty(fillDraft)) {
        if (Td.isEmpty(draftMessage) || Td.isEmpty(((TdApi.InputMessageText) draftMessage.inputMessageText).text) /*allow dropping replyTo*/) {
          draftMessage = new TdApi.DraftMessage(null, 0, new TdApi.InputMessageText(fillDraft, null, false), 0);
        } else if (!Td.equalsTo(((TdApi.InputMessageText) draftMessage.inputMessageText).text, fillDraft)) {
          promptDraftPrefillOnFocus = true;
        }
      }
      TdApi.InputMessageReplyTo replyTo = draftMessage != null ? draftMessage.replyTo : null;
      if (replyTo != null && replyTo.getConstructor() != TdApi.InputMessageReplyToStory.CONSTRUCTOR) {
        if (!ignoreDraftLoad) {
          forceDraftReply(replyTo);
        }
      }
      updateSilentButton(tdlib.isChannel(chat.id));
    }
    if (!inPreviewMode && !isInForceTouchMode()) {
      checkActionBar();
      checkJoinRequests(chat.pendingJoinRequests);
      checkCanSendMessagesToUser(true);
    }
    if (inputView != null) {
      inputView.setChat(chat, messageThread, draftMessage != null ? draftMessage.inputMessageText : null, getCustomInputPlaceholder(), silentButton != null && silentButton.getIsSilent());
    }
    ignoreDraftLoad = false;
    discardAttachedFiles(false);
    updateBottomBar(false);

    closeCommandsKeyboard(false);

    if (previewSearchSender == null) {
      manager.openChat(chat, messageThread, previewSearchFilter, this, areScheduled, !inPreviewMode && !isInForceTouchMode());
    }

    updateShadowColor();
    if (scheduleButton != null && scheduleButton.setVisible(messageThread == null && tdlib.chatHasScheduled(chat.id))) {
      commandButton.setTranslationX(scheduleButton.isVisible() ? 0 : scheduleButton.getLayoutParams().width);
      attachButtons.updatePivot();
    }

    // Preloading data so profile will not jump when opening
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        tdlib.cache().userFull(TD.getUserId(chat));
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        tdlib.cache().basicGroupFull(ChatId.toBasicGroupId(chat.id));
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        tdlib.cache().supergroupFull(ChatId.toSupergroupId(chat.id));
        break;
      }
    }

    // Loading bot information after the messages to display them faster

    boolean isMultiChat = tdlib.isMultiChat(chat.id);

    if (isMultiChat || tdlib.isBotChat(chat)/* || (TGUtils.isChannel(chat) && TGUtils.hasWritePermission(chat))*/) {
      updateCommandButton(R.drawable.deproko_baseline_bots_command_26);
      updateCommandButton(false);
      botHelper = new BotHelper(this, chat);
    } else {
      updateCommandButton(0);
      botHelper = null;
    }

    liveLocation = new LiveLocationHelper(context, tdlib, chat.id, getMessageThreadId(), liveLocationView, false, this);
    liveLocation.init();

    if (inputView != null) {
      inputView.setCommandListProvider(botHelper);
      // inputView.setIgnoreAnyChanges(false);
    }

    final long chatId = getChatId();
    tdlib.client().send(new TdApi.GetChatAdministrators(chatId), adminsHandler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        if (object.getConstructor() == TdApi.ChatAdministrators.CONSTRUCTOR) {
          tdlib.ui().post(() -> {
            if (!isCancelled()) {
              if (getChatId() == chatId) {
                manager.setChatAdmins((TdApi.ChatAdministrators) object);
              }
            }
          });
        }
      }
    });

    if (emojiLayout != null) {
      emojiLayout.setAllowPremiumFeatures(isSelfChat());
    }

    updateCounters(false);
    checkRestriction();
    checkLinkedChat();
  }

  public void updateShadowColor () {
    if (bottomShadowView != null) {
      bottomShadowView.setColorId(manager.useBubbles() ? ColorId.bubble_chatSeparator : ColorId.chatSeparator);
    }
  }

  private void cancelAdminsRequest () {
    if (adminsHandler != null) {
      adminsHandler.cancel();
      adminsHandler = null;
    }
  }

  private void updateCounters (boolean animated) {
    if (chat != null) {
      if (messageThread != null) {
        int unreadCount;
        if (messageThread.hasUnreadMessages(chat) && !areScheduledOnly()) {
          unreadCount = messageThread.getUnreadMessageCount() != ThreadInfo.UNKNOWN_UNREAD_MESSAGE_COUNT ? messageThread.getUnreadMessageCount() : Tdlib.CHAT_MARKED_AS_UNREAD;
        } else {
          unreadCount = 0;
        }
        setUnreadCountBadge(unreadCount, animated);
        setMentionCountBadge(0);
        setReactionCountBadge(0);
      } else {
        setUnreadCountBadge(chat.unreadCount, true);
        setMentionCountBadge(chat.unreadMentionCount);
        setReactionCountBadge(chat.unreadReactionCount);
      }
      if (bottomButtonAction == BOTTOM_ACTION_TOGGLE_MUTE) {
        showBottomButton(bottomButtonAction, 0, animated);
      }
    }
  }

  private void scrollToUnreadOrStartMessage () {
    int anchorMode = MessagesManager.getAnchorHighlightMode(tdlib.id(), chat, messageThread);
    if (!manager.hasReturnMessage()) {
      if (!inPreviewMode && !isInForceTouchMode() && anchorMode == MessagesManager.HIGHLIGHT_MODE_UNREAD) {
        MessageId messageId = MessagesManager.getAnchorMessageId(tdlib.id(), chat, messageThread, anchorMode);
        manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_UNREAD_NEXT, null, true);
        return;
      }
      if (chat != null && MessagesManager.canGoUnread(chat, messageThread)) {
        MessageId messageId = MessagesManager.getAnchorMessageId(tdlib.id(), chat, messageThread, MessagesManager.HIGHLIGHT_MODE_UNREAD);
        int firstUnreadIndex = manager.indexOfFirstUnreadMessage();
        TGMessage bottom = manager.findBottomMessage();
        MessageId bottomMessageId = bottom != null ? bottom.toMessageId() : null;
        int messageIndex = manager.getAdapter().indexOfMessageContainer(messageId);
        if (bottomMessageId == null || bottomMessageId.getChatId() != messageId.getChatId()) {
          if (messageIndex != -1) {
            manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_UNREAD_NEXT, null, true);
          } else {
            manager.resetByMessage(messageId, MessagesManager.HIGHLIGHT_MODE_UNREAD);
          }
          return;
        } else if (bottomMessageId.getMessageId() < messageId.getMessageId()) {
          int bottomMessageIndex = manager.getAdapter().indexOfMessageContainer(bottomMessageId);
          if (firstUnreadIndex == -1 || bottomMessageIndex > firstUnreadIndex) {
            if (messageIndex != -1 && (bottomMessageIndex - messageIndex > 1 || (firstUnreadIndex != -1 && bottomMessageIndex > firstUnreadIndex))) {
              manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_UNREAD_NEXT, null, true);
              return;
            }
            if (messageIndex == -1) {
              manager.resetByMessage(messageId, MessagesManager.HIGHLIGHT_MODE_UNREAD);
              return;
            }
          }
        }
      }
    }
    manager.scrollToStart(false);
  }

  public boolean centerMessage (long chatId, long messageId, boolean delayed, boolean centered) {
    if (getChatId() == chatId && chatId != 0) {
      return manager.centerMessage(chatId, messageId, delayed, centered);
    }
    return false;
  }

  public boolean calculateScrollDyForCenterVideoMessage (final long chatId, final long messageId, MessagesManager.VideoScrollParameters out) {
    if (getChatId() == chatId && chatId != 0) {
      return manager.calculateScrollDyForCenterVideoMessage(chatId, messageId, out);
    }
    return false;
  }

  @Override
  protected void onFocusStateChanged () {
    checkRoundVideo();
    checkInlineResults();
    checkBroadcastingSomeAction();
    if (pinnedMessagesBar != null) {
      pinnedMessagesBar.setAnimationsDisabled(!isFocused());
    }
  }

  private void checkInlineResults () {
    context().setInlineResultsHidden(this, !isFocused() || pagerScrollOffset >= 1f);
    context().setEmojiSuggestionsVisible(isFocused() && !(pagerScrollOffset >= 1f) && canShowEmojiSuggestions);
  }

  public void checkRoundVideo () {
    checkRoundVideo(-1, -1, false);
  }

  public void checkRoundVideo (int first, int last, boolean onScroll) {
    if (isInForceTouchMode()) {
      /*NavigationController navigation = UI.getContext(getContext()).getNavigation();
      if (navigation != null) {
        ViewController c = navigation.getCurrentStackItem();
        if (c instanceof MessagesController && ((MessagesController) c).getChatId() == getChatId()) {
          return;
        }
        c = navigation.getPreviousStackItem();
        if (c instanceof MessagesController && ((MessagesController) c).getChatId() == getChatId()) {
          return;
        }
      }*/
      return;
    }

    if (messagesView.isComputingLayout()) {
      UI.post(() -> {
        if (!isDestroyed()) {
          checkRoundVideo(-1, -1, onScroll);
        }
      });
      return;
    }

    final RoundVideoController roundVideoController = context().getRoundVideoController();
    MessageViewGroup targetView = null;
    boolean targetChat = false;
    targetChat = getChatId() == roundVideoController.getPlayingChatId();
    if (targetChat) {
      long playingMessageId = roundVideoController.getPlayingMessageId();
      LinearLayoutManager manager = this.manager.getLayoutManager();
      if (first == -1) {
        first = manager.findFirstVisibleItemPosition();
      }
      if (last == -1) {
        last = manager.findLastVisibleItemPosition();
      }
      if (first != -1 && last != -1 && !messagesView.isComputingLayout()) {
        MessagesAdapter adapter = this.manager.getAdapter();
        final int messageCount = adapter.getMessageCount();
        if (messageCount > 0) {
          for (int i = first; i <= last; i++) {
            if (i >= 0 && i < messageCount && MessagesHolder.isMessageType(adapter.getItemViewType(i)) && adapter.getItem(i).getId() == playingMessageId) {
              View view = manager.findViewByPosition(i);
              if (view instanceof MessageViewGroup) {
                TGMessage msg = ((MessageViewGroup) view).getMessageView().getMessage();
                if (msg != null && msg.getId() == playingMessageId) {
                  targetView = ((MessageViewGroup) view);
                  break;
                }
              }
            }
          }
        }
      }
    }
    roundVideoController.setAttachedToView(targetView, isInForceTouchMode() ? this : null);
    if (targetChat && onScroll) {
      roundVideoController.onMessagesScroll();
    }
  }

  @Override
  public void onChatDefaultMessageSenderIdChanged (long chatId, TdApi.MessageSender senderId) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId) {
        updateInputHint();
        updateSelectMessageSenderInterface(true);
      }
    });
  }

  public void updateInputHint () {
    if (inputView != null) {
      inputView.updateMessageHint(chat, messageThread, getCustomInputPlaceholder(), Config.NEED_SILENT_BROADCAST && silentButton != null ? silentButton.getIsSilent() : tdlib.chatDefaultDisableNotifications(getChatId()));
    }
  }

  private void updateBottomBar (boolean isUpdate) {
    setInputBlockFlag(FLAG_INPUT_TEXT_DISABLED, !tdlib.canSendBasicMessage(chat));
    if (sendButton != null) {
      sendButton.getSlowModeCounterController(tdlib).updateSlowModeTimer(isUpdate);
    }
    if (messageSenderButton != null) {
      messageSenderButton.setInSlowMode(tdlib.inSlowMode(getChatId()));
    }
    if (isUpdate) {
      updateInputHint();
    }
    if (isInForceTouchMode()) {
      setInputVisible(false, false);
      return;
    }
    if (inPreviewSearchMode()) {
      setInputVisible(false, false);
      if (arePinnedMessages()) {
        showBottomButton(BOTTOM_ACTION_UNPIN_ALL, 0, isUpdate);
      }
      return;
    }
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
    if (tdlib.isChannel(chat.id) && status != null && !TD.isAdmin(status)) {
      setInputVisible(false, false);
      if (TD.isLeft(status)) {
        showBottomButton(BOTTOM_ACTION_FOLLOW, 0, isUpdate);
      } else {
        TdApi.SupergroupFullInfo info = tdlib.cache().supergroupFull(ChatId.toSupergroupId(chat.id));
        if (info != null && info.linkedChatId != 0) {
          showBottomButton(BOTTOM_ACTION_DISCUSS, info.linkedChatId, isUpdate);
        } else {
          showBottomButton(BOTTOM_ACTION_TOGGLE_MUTE, 0, isUpdate);
        }
      }
    } else if (tdlib.isRepliesChat(chat.id)) {
      setInputVisible(false, false);
      showBottomButton(BOTTOM_ACTION_TOGGLE_MUTE, 0, isUpdate);
    } else {
      hideBottomBar(isUpdate);

      TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chat.id);
      TdApi.Supergroup supergroup = tdlib.chatToSupergroup(chat.id);
      boolean joinSupergroupToSendMessages = messageThread == null || supergroup != null && supergroup.joinToSendMessages;
      if (canSendMessageToUser != null && canSendMessageToUser.getConstructor() != TdApi.CanSendMessageToUserResultOk.CONSTRUCTOR) {
        switch (canSendMessageToUser.getConstructor()) {
          case TdApi.CanSendMessageToUserResultOk.CONSTRUCTOR:
            throw new IllegalStateException(); // unreachable
          case TdApi.CanSendMessageToUserResultUserIsDeleted.CONSTRUCTOR:
            showActionDeleteChatButton();
            break;
          case TdApi.CanSendMessageToUserResultUserRestrictsNewChats.CONSTRUCTOR: {
            showActionButton(Lang.getMarkdownString(this, R.string.UserNewChatRetricted, Lang.boldCreator(), tdlib.chatTitleShort(chat.id)), ACTION_EMPTY, false);
            break;
          }
          default:
            Td.assertCanSendMessageToUserResult_3ce8a048();
            throw Td.unsupported(canSendMessageToUser);
        }
      } else if (secretChat != null && !TD.isSecretChatReady(secretChat)) {
        showSecretChatAction(secretChat);
      } else if (tdlib.chatFullyBlocked(chat.id) && tdlib.isUserChat(chat)) {
        showActionUnblockButton();
      } else if (tdlib.chatUserDeleted(chat) || (ChatId.isBasicGroup(chat.id) && (!tdlib.chatBasicGroupActive(chat.id) || TD.isNotInChat(status))) || (tdlib.isSupergroupChat(chat) && TD.isNotInChat(status) && joinSupergroupToSendMessages)) {
        if (tdlib.isSupergroupChat(chat) && status != null && TD.canReturnToChat(status)) {
          showActionJoinChatButton();
        } else if (messageThread != null) {
          CharSequence restrictionStatus = tdlib.getBasicMessageRestrictionText(chat);
          if (restrictionStatus != null && !hasSendSomeMediaPermission()) {
            showActionButton(restrictionStatus, ACTION_EMPTY, false);
          } else {
            hideActionButton();
          }
        } else {
          showActionDeleteChatButton();
        }
      } else if (tdlib.isBotChat(chat) && chat.lastMessage == null) {
        showActionBotButton();
      } else {
        CharSequence restrictionStatus = tdlib.getBasicMessageRestrictionText(chat);
        if (restrictionStatus != null && !hasSendSomeMediaPermission()) {
          showActionButton(restrictionStatus, ACTION_EMPTY, false);
        } else {
          hideActionButton();
        }
      }
    }
  }

  // video/audio btn

  private TooltipOverlayView.TooltipInfo tooltipInfo;

  private void showBottomHint (@StringRes int res) {
    showBottomHint(Lang.getString(res), false);
  }

  public void hideBottomHint () {
    if (tooltipInfo != null) {
      tooltipInfo.hide(true);
    }
  }

  private void showBottomHint (CharSequence text, boolean isError) {
    if (!isAttachedToNavigationController())
      return;
    if (tooltipInfo == null) {
      tooltipInfo = context().tooltipManager().builder(sendButton).locate((targetView, rect) -> sendButton.getTargetBounds(targetView, rect))
        .icon(isError ? R.drawable.baseline_warning_24 : 0)
        .ignoreViewScale(true)
        .controller(this)
        .show(tdlib, text);
      tooltipInfo.addOnCloseListener(this::onTooltipInfoClose);
    } else {
      tooltipInfo.reset(context().tooltipManager().newContent(tdlib, text, 0), isError ? R.drawable.baseline_warning_24 : 0);
      tooltipInfo.show();
    }
    isSlowModeRestrictionHintVisible = false;
    tooltipInfo.hideDelayed(false);
  }

  private boolean isSlowModeRestrictionHintVisible;

  private void onTooltipInfoClose (long duration) {
    isSlowModeRestrictionHintVisible = false;
  }

  private void onSlowModeCounterUpdate (int duration) {
    if (sendButton != null && tooltipInfo != null && tooltipInfo.isVisible() && isSlowModeRestrictionHintVisible) {
      CharSequence restriction = tdlib().getSlowModeRestrictionText(getChatId(), null);
      if (restriction != null) {
        tooltipInfo.reset(context().tooltipManager().newContent(tdlib, restriction, 0), R.drawable.baseline_warning_24);
      } else {
        tooltipInfo.hideNow();
      }
    }
  }

  @Override
  public void onPreferVideoModeChanged (boolean preferVideoMode) {
    if (!sendShown.getValue()) {
      showBottomHint(preferVideoMode ? R.string.HoldToVideo : R.string.HoldToAudio);
    }
  }

  @Override
  public void onRecordAudioVideoError (boolean preferVideoMode) {
    if (!sendShown.getValue()) {
      showBottomHint(preferVideoMode ? R.string.HoldToVideo : R.string.HoldToAudio);
    }
  }

  public void setInputVisible (boolean visible, boolean notEmpty) {
    RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams) scrollToBottomButtonWrap.getLayoutParams();
    RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams) mentionButtonWrap.getLayoutParams();
    RelativeLayout.LayoutParams params3 = (RelativeLayout.LayoutParams) reactionsButtonWrap.getLayoutParams();
    RelativeLayout.LayoutParams params4 = (RelativeLayout.LayoutParams) goToNextFoundMessageButtonBadge.getLayoutParams();
    RelativeLayout.LayoutParams params5 = (RelativeLayout.LayoutParams) goToPrevFoundMessageButtonBadge.getLayoutParams();
    if (visible) {
      params1.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
      params1.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      params2.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
      params2.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      params3.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
      params3.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      params4.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
      params4.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      params5.addRule(RelativeLayout.ABOVE, R.id.msg_bottom);
      params5.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
    } else {
      params1.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      params1.addRule(RelativeLayout.ABOVE, 0);
      params2.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      params2.addRule(RelativeLayout.ABOVE, 0);
      params3.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      params3.addRule(RelativeLayout.ABOVE, 0);
      params4.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      params4.addRule(RelativeLayout.ABOVE, 0);
      params5.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      params5.addRule(RelativeLayout.ABOVE, 0);
    }
    if (visible) {
      bottomWrap.setVisibility(View.VISIBLE);
      bottomShadowView.setVisibility(View.VISIBLE);
      if (replyBarView != null) {
        replyBarView.setVisibility(View.VISIBLE);
      }
      emojiButton.setVisibility(View.VISIBLE);
      if (notEmpty) {
        attachButtons.setVisibility(View.INVISIBLE);
        sendButton.setVisibility(View.VISIBLE);
        messageSenderButton.setVisibility(View.INVISIBLE);
      } else {
        attachButtons.setVisibility(View.VISIBLE);
        sendButton.setVisibility(View.INVISIBLE);
        if (canSelectSender()) {
          messageSenderButton.setVisibility(View.VISIBLE);
        }
      }
    } else {
      hideActionButton();
      bottomWrap.setVisibility(View.GONE);
      if (replyBarView != null) {
        replyBarView.setVisibility(View.GONE);
      }
      bottomShadowView.setVisibility(View.GONE);
      emojiButton.setVisibility(View.GONE);
      attachButtons.setVisibility(View.GONE);
      sendButton.setVisibility(View.GONE);
      messageSenderButton.setVisibility(View.GONE);
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidateEmojiViews(false);
  }

  private boolean emojiScheduled;

  public void invalidateEmojiViews (boolean force) {
    if (isNavigationAnimating() && !force) {
      emojiScheduled = true;
      return;
    }
    if (replyBarView != null) {
      replyBarView.invalidate();
    }
    if (messagesView != null) {
      LinearLayoutManager manager = (LinearLayoutManager) messagesView.getLayoutManager();
      for (int i = 0; i < manager.getChildCount(); i++) {
        View v = manager.getChildAt(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
    if (emojiLayout != null) {
      emojiLayout.invalidateAll();
    }
    if (keyboardLayout != null) {
      for (int i = 0; i < keyboardLayout.getChildCount(); i++) {
        View v = keyboardLayout.getChildAt(i);
        if (v != null && v.getVisibility() == View.VISIBLE) {
          v.invalidate();
        }
      }
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public int getId () {
    if (inPreviewMode) {
      switch (previewMode) {
        case PREVIEW_MODE_FONT_SIZE:
          return R.id.controller_fontSize;
        case PREVIEW_MODE_WALLPAPER:
          return R.id.controller_wallpaper;
        case PREVIEW_MODE_WALLPAPER_OBJECT:
          return R.id.controller_wallpaper_preview;
        case PREVIEW_MODE_EVENT_LOG:
          return R.id.controller_eventLog;
        case PREVIEW_MODE_SEARCH:
          return R.id.controller_searchPreview;
      }
      return 0;
    }
    return R.id.controller_messages;
  }

  public static int getSlideBackBound () {
    return TGMessage.getContentLeft();
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (context().getRecordAudioVideoController().isOpen()) {
      return false;
    }

    if (pagerScrollOffset > 0f) {
      return false;
    }

    if (hasAttachedFiles()) {
      return false;
    }

    if (hasEditedChanges()) {
      return false;
    }

    if (getChatId() == 0 || inPreviewMode) {
      /*switch (previewMode) {
          case PREVIEW_MODE_WALLPAPER:
            View view = wallpapersList.getLayoutManager().findViewByPosition(0);
            return view != null && view.getLeft() >= 0;
        }*/
      return previewMode == PREVIEW_MODE_NONE || !(y > contentView.getMeasuredHeight() - bottomWrap.getMeasuredHeight() + HeaderView.getSize(true));
    }

    int baseY = Views.getLocationInWindow(navigationController.getValue())[1];

    /*if (areInlineResultsVisible()) {
      return false;
    }*/

    int bound = (getSlideBackBound());

    if (emojiLayout != null && emojiLayout.getVisibility() == View.VISIBLE && y >= Views.getLocationInWindow(emojiLayout)[1] - baseY) {
      return emojiLayout.canSlideBack() && x <= bound;
    }

    if (inputView != null && inputView.getVisibility() == View.VISIBLE) {
      int inputY = Views.getLocationInWindow(inputView)[1] - baseY;
      if (y >= inputY && y < inputY + inputView.getMeasuredHeight()) {
        return x < emojiButton.getMeasuredWidth() || inputView.isEmpty();
      }
    }

    if (needTabs() && y < HeaderView.getSize(true))
      return false;

    if (Settings.instance().needChatQuickShare()) {
      if (Lang.rtl()) {
        int width = contentView.getMeasuredWidth();
        return width != 0 && x >= width - bound;
      } else {
        return x <= bound;
      }
    }

    return true;
  }

  @Override
  protected int getMenuId () {
    if (areScheduled)
      return 0;
    switch (previewMode) {
      case PREVIEW_MODE_EVENT_LOG:
        return R.id.menu_search;
      case PREVIEW_MODE_SEARCH:
        return 0;
      case PREVIEW_MODE_WALLPAPER_OBJECT:
      case PREVIEW_MODE_FONT_SIZE:
        return R.id.menu_more;
      case PREVIEW_MODE_WALLPAPER:
        return R.id.menu_gallery;
    }
    if (getChatId() != 0) {
      if (isSelfChat()) {
        return R.id.menu_search;
      }
      if (isSecretChat()) {
        return R.id.menu_secretChat;
      }
      return R.id.menu_more;
    }
    return 0;
  }

  @Override
  protected int getSelectMenuId () {
    return R.id.menu_messageActions;
  }

  @Override
  public CharSequence getName () {
    switch (previewMode) {
      case PREVIEW_MODE_WALLPAPER:
        return Lang.getString(R.string.Wallpaper);
      case PREVIEW_MODE_WALLPAPER_OBJECT:
        return Lang.getString(R.string.ChatBackgroundPreview);
      case PREVIEW_MODE_FONT_SIZE:
        return Lang.getString(R.string.TextSize);
      default:
        return Lang.getString(isSelfChat() ? R.string.SavedMessages : R.string.ChatPreview);
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return inWallpaperPreviewMode() ? headerDoubleCell : needTabs() ? pagerHeaderView : getChatId() != 0 ? headerCell : null;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    manager.rebuildLayouts();

    if (emojiKeyboardFrameLayout != null) {
      emojiState = false;
      if (emojiShown) {
        closeEmojiKeyboard();
      }
      emojiKeyboardFrameLayout.rebuildLayout();
    }

    if (keyboardLayout != null) {
      commandsState = false;
      if (commandsShown) {
        closeCommandsKeyboard(false);
      }
    }
  }

  public void checkEventLogSubtitle (boolean inSearch) {
    int res = inSearch ? R.string.EventLogSelectedEvents : R.string.EventLogAllEvents;
    String str = Lang.lowercase(Lang.getString(res));
    headerCell.setForcedSubtitle(str);
    headerCell.setSubtitle(str);
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_gallery) {
      header.addButton(menu, R.id.menu_btn_gallery, R.drawable.baseline_image_24, getHeaderIconColorId(), this, Screen.dp(52f));
    } else if (id == R.id.menu_share) {
      header.addButton(menu, R.id.menu_btn_share, R.drawable.baseline_share_arrow_24, getHeaderIconColorId(), this, Screen.dp(52f));
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this).setColorId(ColorId.headerLightIcon);
    } else if (id == R.id.menu_search) {
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_chat) {
      HeaderButton btn = header.addButton(menu, R.id.menu_btn_viewScheduled, R.drawable.baseline_date_range_24, getHeaderIconColorId(), this, Screen.dp(52f));
      btn.setVisibility(tdlib.chatHasScheduled(getChatId()) ? View.VISIBLE : View.GONE);
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_secretChat) {
      StopwatchHeaderButton headerButton = header.addStopwatchButton(menu, this);
      headerButton.forceValue(tdlib.ui().getTTLShort(getChatId()), isSecretChat() && tdlib.canChangeMessageAutoDeleteTime(chat.id));
      header.addMoreButton(menu, this);
    } else if (id == R.id.menu_messageActions) {
      int iconColorId = getSelectHeaderIconColorId();
      HeaderButton selectInBetweenBtn = header.addButton(menu, R.id.menu_btn_selectInBetween, R.drawable.baseline_toc_24, iconColorId, this, Screen.dp(49f));
      selectInBetweenBtn.setThemeColorId(getSelectHeaderIconColorId());
      selectInBetweenBtn.setTag(Lang.getString(R.string.SelectMessagesInBetween));
      selectInBetweenBtn.setVisibility(View.GONE);
      int totalButtonsCount = 0;
      boolean value;
      header.addButton(menu, R.id.menu_btn_send, R.drawable.baseline_send_24, iconColorId, this, Screen.dp(52f))
        .setVisibility((value = canSendSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addViewButton(menu, this, iconColorId)
        .setVisibility((value = canViewSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addReplyButton(menu, this, iconColorId)
        .setVisibility((value = canReplyToSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addEditButton(menu, this, iconColorId)
        .setVisibility((value = canEditSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addButton(menu, R.id.menu_btn_clearCache, R.drawable.templarian_baseline_broom_24, iconColorId, this, Screen.dp(52f))
        .setVisibility((value = canClearCacheSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addButton(menu, R.id.menu_btn_unpinAll, R.drawable.deproko_baseline_pin_undo_24, iconColorId, this, Screen.dp(52f))
        .setVisibility((value = canUnpinSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addRetryButton(menu, this, iconColorId)
        .setVisibility((value = canResendSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addDeleteButton(menu, this, iconColorId)
        .setVisibility((value = canDeleteSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;

      HeaderButton reportButton = header.addButton(menu, R.id.menu_btn_report, R.drawable.baseline_report_24, iconColorId, this, Screen.dp(52f));

      header.addCopyButton(menu, this, iconColorId)
        .setVisibility((value = canCopySelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;
      header.addForwardButton(menu, this, iconColorId)
        .setVisibility((value = canShareSelectedMessages()) ? View.VISIBLE : View.GONE);
      if (value) totalButtonsCount++;

      reportButton.setVisibility(canReportSelectedMessages(totalButtonsCount) ? View.VISIBLE : View.GONE);
    }
  }

  private MessageWithProperties getSingleSelectedMessage () {
    if (selectedMessageIds != null && selectedMessageIds.size() == 1) {
      long messageId = selectedMessageIds.keyAt(0);
      TGMessage m = selectedMessageIds.valueAt(0);
      TdApi.Message message = m.getMessage(messageId);
      TdApi.MessageProperties properties = m.lastMessageProperties(messageId);
      return new MessageWithProperties(message, properties);
    }
    return null;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      if (inPreviewMode) {
        if (previewMode == PREVIEW_MODE_FONT_SIZE) {
          IntList ids = new IntList(2);
          StringList strings = new StringList(2);
          ids.append(R.id.btn_chatFontSizeScale);
          strings.append(Settings.instance().needChatFontSizeScaling() ? R.string.TextSizeScaleDisable : R.string.TextSizeScaleEnable);
          if (Settings.instance().canResetChatFontSize()) {
            ids.append(R.id.btn_chatFontSizeReset);
            strings.append(R.string.TextSizeReset);
          }
          showMore(ids.get(), strings.get(), 0);
        } else if (previewMode == PREVIEW_MODE_WALLPAPER_OBJECT) {
          IntList ids = new IntList(2);
          StringList strings = new StringList(2);
          ids.append(R.id.btn_share);
          strings.append(R.string.Share);
          ids.append(R.id.btn_copyLink);
          strings.append(R.string.CopyLink);
          showMore(ids.get(), strings.get(), 0);
        }
      } else {
        if (inputView != null && inputView.canFormatText()) {
          int count = 6;
          IntList ids = new IntList(count);
          StringList strings = new StringList(count);
          IntList icons = new IntList(count);

          if (inputView.canClearTextFormat()) {
            ids.append(R.id.btn_plain);
            strings.append(R.string.TextFormatClear);
            icons.append(R.drawable.baseline_format_clear_24);
          }

          ids.append(R.id.btn_bold);
          strings.append(R.string.TextFormatBold);
          icons.append(R.drawable.baseline_format_bold_24);

          ids.append(R.id.btn_italic);
          strings.append(R.string.TextFormatItalic);
          icons.append(R.drawable.baseline_format_italic_24);

          ids.append(R.id.btn_underline);
          strings.append(R.string.TextFormatUnderline);
          icons.append(R.drawable.baseline_format_underlined_24);

          ids.append(R.id.btn_strikethrough);
          strings.append(R.string.TextFormatStrikethrough);
          icons.append(R.drawable.baseline_strikethrough_s_24);

          ids.append(R.id.btn_monospace);
          strings.append(R.string.TextFormatMonospace);
          icons.append(R.drawable.baseline_code_24);

          ids.append(R.id.btn_spoiler);
          strings.append(R.string.TextFormatSpoiler);
          icons.append(R.drawable.baseline_eye_off_24);

          ids.append(R.id.btn_link);
          strings.append(R.string.TextFormatLink);
          icons.append(R.drawable.baseline_link_24);

          ids.append(R.id.btn_quote);
          strings.append(R.string.TextFormatQuote);
          icons.append(R.drawable.baseline_format_quote_close_24);

          showOptions(null, ids.get(), strings.get(), null, icons.get(), (itemView, id1) -> inputView.setSpan(id1));
        } else {
          showMore();
        }
      }
    } else if (id == R.id.menu_btn_gallery) {
      Intents.openGallery(context, false);
    } else if (id == R.id.menu_btn_clear) {
      if (isEventLog()) {
        clearSearchInput();
      } else {
        clearChatSearchInput();
      }
    } else if (id == R.id.menu_btn_search) {
      if (manager.isReadyToSearch()) {
        hideBottomHint();
        openSearchMode();
      }
    } else if (id == R.id.menu_btn_selectInBetween) {
      selectMessagesInBetween();
    } else if (id == R.id.menu_btn_stopwatch) {
      if (isSecretChat()) {
        tdlib.ui().showTTLPicker(context(), chat);
      }
    } else if (id == R.id.menu_btn_viewScheduled) {
      viewScheduledMessages(false);
    } else if (id == R.id.menu_btn_copy) {
      if (selectedMessageIds != null) {
        copySelectedMessages();
        finishSelectMode(-1);
      }
    } else if (id == R.id.menu_btn_report) {
      if (selectedMessageIds != null) {
        MessageWithProperties[] messages = selectedMessagesToArray();
        if (messages != null) {
          reportChat(messages, () -> finishSelectMode(-1));
        }
      }
    } else if (id == R.id.menu_btn_forward) {
      if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
        SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
        if (c != null) {
          c.shareMessages();
        }
        return;
      }
      if (selectedMessageIds != null) {
        int size = selectedMessageIds.size();
        if (size > 0) {
          TdApi.Message[] messages = new TdApi.Message[size];
          for (int i = 0; i < size; i++) {
            long messageId = selectedMessageIds.keyAt(i);
            TdApi.Message message = selectedMessageIds.valueAt(i).getMessage(messageId);
            messages[i] = message;
          }
          shareMessages(messages, true);
        }
      }
    } else if (id == R.id.menu_btn_reply) {
      MessageWithProperties m = getSingleSelectedMessage();
      if (m != null) {
        showReply(m, null, true, true);
        finishSelectMode(-1);
        if (inputView != null && inputView.isEmpty()) {
          Keyboard.show(inputView);
        }
      }
    } else if (id == R.id.menu_btn_edit) {
      MessageWithProperties m = getSingleSelectedMessage();
      if (m != null) {
        editMessage(m);
      }
    } else if (id == R.id.menu_btn_view) {
      if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
        SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
        if (c != null) {
          MessageId messageId = c.getSingularMessageId();
          if (messageId != null) {
            highlightMessage(messageId);
            c.setInMediaSelectMode(false);
          }
        }
      }
    } else if (id == R.id.menu_btn_send) {
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        showOptions(Lang.pluralBold(R.string.SendXMessagesNow, selectedMessageIds.size()), new int[] {R.id.btn_send, R.id.btn_cancel}, new String[] {Lang.getString(R.string.SendNow), Lang.getString(R.string.Cancel)}, null, new int[] {R.drawable.baseline_send_24, R.drawable.baseline_cancel_24}, (v, optionId) -> {
          if (optionId == R.id.btn_send && selectedMessageIds != null) {
            for (int i = selectedMessageIds.size() - 1; i >= 0; i--) {
              long messageId = selectedMessageIds.keyAt(i);
              tdlib.client().send(new TdApi.EditMessageSchedulingState(selectedMessageIds.valueAt(i).getChatId(), messageId, null), tdlib.okHandler());
            }
            finishSelectMode(-1);
          }
          return true;
        });
      }
    } else if (id == R.id.menu_btn_clearCache) {
      if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
        SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
        if (c != null) {
          c.clearMessages();
        }
        return;
      }
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        final int size = selectedMessageIds.size();
        final SparseArrayCompat<TdApi.File> files = new SparseArrayCompat<>(size);
        for (int i = 0; i < size; i++) {
          TdApi.Message message = selectedMessageIds.valueAt(i).getMessage(selectedMessageIds.keyAt(i));
          TdApi.File file = TD.getFile(message);
          if (TD.canDeleteFile(message, file)) {
            files.put(file.id, file);
          }
        }
        TD.deleteFiles(this, ArrayUtils.asArray(files, new TdApi.File[files.size()]), () -> finishSelectMode(-1));
      }
    } else if (id == R.id.menu_btn_unpinAll) {
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        showOptions(Lang.pluralBold(R.string.UnpinXMessages, selectedMessageIds.size()), new int[] {R.id.btn_unpinAll, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Unpin), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.deproko_baseline_pin_undo_24, R.drawable.baseline_cancel_24}, (itemView, viewId) -> {
          if (viewId == R.id.btn_unpinAll) {
            final int size = selectedMessageIds.size();
            for (int i = 0; i < size; i++) {
              tdlib.client().send(new TdApi.UnpinChatMessage(chat.id, selectedMessageIds.keyAt(i)), tdlib.okHandler());
            }
            exitOnTransformFinish = true;
            finishSelectMode(-1);
          }
          return true;
        });
      }
    } else if (id == R.id.menu_btn_delete) {
      if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
        SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
        if (c != null) {
          c.deleteMessages();
        }
        return;
      }
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        final int size = selectedMessageIds.size();
        final MessageWithProperties[] messages = new MessageWithProperties[size];
        for (int i = 0; i < size; i++) {
          long messageId = selectedMessageIds.keyAt(i);
          TGMessage m = selectedMessageIds.valueAt(i);
          TdApi.Message message = m.getMessage(messageId);
          TdApi.MessageProperties properties = m.lastMessageProperties(messageId);
          messages[i] = new MessageWithProperties(message, properties);
        }
        tdlib.ui().showDeleteOptions(this, messages, () -> finishSelectMode(-1));
      }
    } else if (id == R.id.menu_btn_retry) {
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        int count = 0;
        long lastMediaGroupId = 0;
        long lastChatId = 0;
        for (int i = 0; i < selectedMessageIds.size(); i++) {
          long messageId = selectedMessageIds.keyAt(i);
          TdApi.Message message = selectedMessageIds.valueAt(i).getMessage(messageId);
          if (lastChatId != message.chatId || lastMediaGroupId != message.mediaAlbumId || lastMediaGroupId == 0) {
            lastChatId = message.chatId;
            lastMediaGroupId = message.mediaAlbumId;
            count++;
          }
        }
        if (count > 0) {
          showOptions(new int[] {R.id.btn_messageResend, R.id.btn_cancel}, new String[] {Lang.plural(R.string.ResendXMessages, count), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, (v, optionId) -> {
            if (optionId == R.id.btn_messageResend) {
              resendSelectedMessages();
              finishSelectMode(-1);
            }
            return true;
          });
        }
      }
    } else if (id == R.id.menu_btn_up) {
      moveSearchSelection(true);
    } else if (id == R.id.menu_btn_down) {
      moveSearchSelection(false);
    }
  }

  private void resendSelectedMessages () {
    if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
      long[] messageIds = new long[selectedMessageIds.size()];
      for (int i = 0; i < messageIds.length; i++) {
        messageIds[i] = selectedMessageIds.keyAt(i);
      }
      tdlib.resendMessages(getChatId(), messageIds);
    }
  }

  public boolean isChatFocused () {
    return manager.isFocused();
  }

  private boolean resetOnFocus;

  public void setResetOnFocus () {
    if (!this.resetOnFocus) {
      this.resetOnFocus = true;
      tdlib.context().global().addAccountListener(this);
    }
  }

  private void clearResetOnFocus () {
    if (this.resetOnFocus) {
      this.resetOnFocus = false;
      tdlib.context().global().removeAccountListener(this);
    }
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    if (tdlib.id() != newAccount.id) {
      clearResetOnFocus();
    }
  }

  private static HashSet<String> shownTutorials;

  private void showMessageMenuTutorial () {
    if (sendShown.getValue() && !areScheduledOnly() && !isInputLess() && canWriteMessages() && hasSendBasicMessagePermission() && !isEditingMessage() && !isSecretChat() && isFocused() && !sendButton.inInlineMode()) {
      long tutorialFlag;
      if (isSelfChat()) {
        tutorialFlag = Settings.TUTORIAL_SET_REMINDER;
      } else {
        tutorialFlag = Settings.TUTORIAL_SCHEDULE;
      }
      if (Settings.instance().needTutorial(tutorialFlag)) {
        long userId = tdlib.chatUserId(getChatId());
        boolean canSendOnceOnline = !isSelfChat() && tdlib.cache().userLastSeenAvailable(userId);
        String tutorialKey = tutorialFlag + (isSelfChat() ? "_self" : canSendOnceOnline ? "_online" : isChannel() ? "_channel" : "");
        if (shownTutorials == null || !shownTutorials.contains(tutorialKey)) {
          if (shownTutorials == null)
            shownTutorials = new HashSet<>();
          shownTutorials.add(tutorialKey);
          if (isSelfChat()) {
            showBottomHint(R.string.HoldToRemind);
          } else if (canSendOnceOnline) {
            showBottomHint(Lang.getStringBold(R.string.HoldToSchedule2, tdlib.cache().userFirstName(userId)), false);
          } else if (isChannel()) {
            showBottomHint(R.string.HoldToSilentBroadcast);
          } else {
            showBottomHint(R.string.HoldToSchedule);
          }
        }
      }
    }
  }

  public void openVoiceChatInvitation (TdApi.InternalLinkTypeVideoChat invitation) {
    tdlib.ui().openVoiceChatInvitation(this, invitation);
  }

  private boolean promptDraftPrefillOnFocus;

  public boolean fillDraft (TdApi.FormattedText fillDraft, boolean checkCurrentDraft) {
    if (Td.isEmpty(fillDraft)) {
      return false;
    }
    Runnable act = () -> {
      if (inputView != null) {
        inputView.setDraft(new TdApi.InputMessageText(fillDraft, null, false));
      }
    };
    TdApi.DraftMessage currentDraft = getDraftMessage();
    if (checkCurrentDraft && !Td.isEmpty(currentDraft)) {
      TdApi.FormattedText currentText = ((TdApi.InputMessageText) currentDraft.inputMessageText).text;
      if (!Td.isEmpty(currentText) && !Td.equalsTo(currentText, fillDraft)) {
        showWarning(Lang.getMarkdownString(this, R.string.DraftPreFillWarning), isConfirmed -> {
          if (isConfirmed) {
            act.run();
          }
        });
        return true;
      }
    }
    act.run();
    return true;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (promptDraftPrefillOnFocus) {
      promptDraftPrefillOnFocus = false;
      fillDraft(this.fillDraft, true);
    }
    if (chat != null && !isInForceTouchMode()) {
      TdApi.ChatSource source = tdlib.chatSource(openedFromChatList, chat.id);
      if (source != null && Settings.instance().needTutorial(source)) {
        context().tooltipManager().builder(headerCell)
          .controller(this)
          .preventHideOnTouch(true)
          .fillWidth(true)
          .noPivot(true)
          .needBlink(true)
          .icon(R.drawable.baseline_info_24)
          .chatTextSize()
          .show(tdlib, Lang.getTutorial(this, source))
          .addOnCloseListener(duration -> {
            if (duration >= TimeUnit.SECONDS.toMillis(5)) {
              Settings.instance().markTutorialAsComplete(source);
            }
          })
          .hideDelayed(10, TimeUnit.SECONDS);
      }
      if (voiceChatInvitation != null) {
        openVoiceChatInvitation(voiceChatInvitation);
        voiceChatInvitation = null;
      }
    }
    showMessageMenuTutorial();
    if (!resetOnFocus && !inPreviewMode && !isInForceTouchMode()) {
      TdlibManager.instance().changePreferredAccountId(tdlib.id(), TdlibManager.SWITCH_REASON_CHAT_FOCUS, success -> {
        if (success && isFocused()) {
          resetOnFocus = true;
          resetOnFocus();
        }
      });
    }
    UI.setSoftInputMode(context, Config.DEFAULT_WINDOW_PARAMS);
    if (!inPreviewMode() && tdlib != null) {
      ((MainActivity) context).destroyMessageControllers(tdlib.id());
    }
    if (pagerContentAdapter != null && pagerContentAdapter.isLocked) {
      pagerContentAdapter.isLocked = false;
      pagerContentAdapter.notifyDataSetChanged();
    }
    messagesView.setMessageAnimatorEnabled(true);
    registerRaiseListener();
    manager.setParentFocused(true);
    if (scheduledKeyboardMessageId != 0) {
      openCommandsKeyboard(scheduledKeyboardMessageId, scheduledKeyboard, false, false);
      scheduledKeyboardMessageId = 0;
      scheduledKeyboard = null;
    }
    if (InputView.USE_ANDROID_SELECTION_FIX) {
      if (bottomWrap.getVisibility() == View.VISIBLE && inputView != null && inputView.isEnabled() && !isInputLess()) {
        inputView.setEnabled(false);
        inputView.setEnabled(true);
      }
    }
    if (inputView != null && inputView.isEnabled()) {
      inputView.requestFocus();
    }
    if (emojiScheduled) {
      invalidateEmojiViews(true);
      emojiScheduled = false;
    }
    updateCounters(true);
    if (!inPreviewMode) {
      int stackSize = stackSize();
      if (stackSize == 4 && stackItemAt(2) instanceof CreateGroupController) {
        destroyStackItemAt(2);
        destroyStackItemAt(1);
      } else if (stackSize == 3 && stackItemAt(1) instanceof CreateGroupController) {
        destroyStackItemAt(1);
      }
      destroyStackItemById(R.id.controller_call);
      ViewController<?> c = previousStackItem();
      if (c instanceof ChatsController && ((ChatsController) c).isPicker()) {
        destroyStackItemAt(stackSize() - 2);
      }
    }
    resetOnFocus();
    if (openKeyboard) {
      openKeyboard = false;
      Keyboard.show(inputView);
    }
    // showEmojiSuggestionsIfTemporarilyHidden();
    // tdlib.context().changePreferredAccountId(tdlib.id(), TdlibManager.SWITCH_REASON_CHAT_FOCUS);
  }

  private void resetOnFocus () {
    if (resetOnFocus && navigationController() != null) {
      clearResetOnFocus();
      NavigationStack stack = navigationController().getStack();
      stack.destroyAllButSaveLast(1);
      MainController c = new MainController(context, tdlib);
      c.getValue();
      stack.insert(c, 0);
    }
  }

  @Override
  public final boolean shouldDisallowScreenshots () {
    return (chat != null && (isSecretChat() || chat.hasProtectedContent || manager.hasVisibleProtectedContent())) || super.shouldDisallowScreenshots();
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    triggerOneShot = false;
    if (headerCell != null) { // Fix for new profiles
      headerCell.setTranslationX(0f);
    }
    tdlib.ui().updateTTLButton(R.id.menu_secretChat, headerView, chat, true);
    shareItem();
    if (!StringUtils.isEmpty(previewSearchQuery) && headerView != null) {
      headerView.openSearchMode(false, false);
      setSearchInput(previewSearchQuery);
      lastMessageSearchQuery = previewSearchQuery;
      previewSearchQuery = null;
      return;
    }
    if (previewSearchSender != null && headerView != null) {
      viewMessagesFromSender(previewSearchSender, false);
    }
  }

  public boolean canSaveDraft () {
    return canWriteMessages() && getChatId() != 0 && !inPreviewMode() && !isInForceTouchMode();
  }

  private void saveDraft () {
    if (canSaveDraft()) {
      if (isEditingMessage()) {
        // TODO save local draft
      } else if (inputView != null && inputView.textChangedSinceChatOpened() && isFocused()) {
        final TdApi.FormattedText outputText = inputView.getOutputText(false);
        final @Nullable TdApi.InputMessageReplyTo replyTo = getCurrentReplyId();
        final long date = tdlib.currentTime(TimeUnit.SECONDS);
        final TdApi.InputMessageText inputMessageText = new TdApi.InputMessageText(
          outputText,
          findTargetContext().linkPreviewOptions,
          false
        );
        final TdApi.DraftMessage draftMessage = new TdApi.DraftMessage(replyTo, (int) date, inputMessageText, 0);
        final long outputChatId = messageThread != null ? messageThread.getChatId() : getChatId();
        final long messageThreadId = messageThread != null ? messageThread.getMessageThreadId() : 0;
        if (messageThread != null) {
          messageThread.setDraft(draftMessage);
        }
        //noinspection UnsafeOptInUsageError
        tdlib.client().send(new TdApi.SetChatDraftMessage(
          outputChatId,
          messageThreadId,
          !Td.isEmpty(draftMessage) ? draftMessage : null
        ), tdlib.okHandler());
        if (hasAttachedFiles()) {
          // TODO save local draft ?
        }
      }
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    collapsePinnedMessagesBar(false);
  }

  public void collapsePinnedMessagesBar (boolean animated) {
    if (pinnedMessagesBar != null) {
      pinnedMessagesBar.collapse(animated);
    }
  }

  @Override
  public void onBlur () {
    saveDraft();

    super.onBlur();

    messagesView.stopScroll();

    if (preventHideKeyboard) {
      preventHideKeyboard = false;
    } else {
      hideSoftwareKeyboard();
    }
    unregisterRaiseListener();
    manager.setParentFocused(false);

    if (translationPopup != null) {
      translationPopup.hidePopupWindow(true);
    }
    cancelSheduledKeyboardOpeningAndHideAllKeyboards();
    // hideEmojiSuggestionsTemporarily();
    // closeEmojiKeyboard();
    // Media.instance().stopVoice();
  }

  private boolean messagesHidden;

  private void setHideMessages (boolean hide) {
    if (this.messagesHidden != hide) {
      this.messagesHidden = hide;
      manager.setParentHidden(hide);
      // messagesView.setVisibility(hide ? View.GONE : View.VISIBLE);
      // TODO checkPinnedMessage();
      if (hide) {
        setScrollToBottomVisible(false, isFocused());
      }
    }
  }

  private void checkLinkedChat () {
    long supergroupId = ChatId.toSupergroupId(getChatId());
    TdApi.SupergroupFullInfo info = supergroupId != 0 ? tdlib.cache().supergroupFull(supergroupId) : null;
    long linkedChatId = info != null ? info.linkedChatId : 0;
    if (this.linkedChatId != linkedChatId) {
      this.linkedChatId = linkedChatId;
      updateBottomBar(true);
    }
  }

  private void checkRestriction () {
    if (!inPreviewMode() && !isEventLog()) {
      setHideMessages(tdlib.chatRestricted(chat));
    }
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    checkBroadcastingSomeAction();
    unregisterRaiseListener();
    manager.setParentPaused(true);
    saveDraft();
    if (!getKeyboardState() && !isInputLess()) {
      enableOnResume = true;
      if (inputView != null) {
        inputView.setEnabled(false);
      }
    }
    /* FIXME I hope it works fine

    emojiState = false;
    closeEmojiKeyboard();*/
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    checkBroadcastingSomeAction();
    registerRaiseListener();
    manager.setParentPaused(false);
    if (enableOnResume) {
      UI.unlock(this, 200l);
    }
  }

  @Override
  public void destroy () {
    resetSelectableControl();

    discardAttachedFiles(false);
    setScrollToBottomVisible(false, false);
    applyPlayerOffset(0f, 0f);
    hideActionButton();
    setBroadcastAction(TdApi.ChatActionCancel.CONSTRUCTOR);

    cancelJumpToDate();
    cancelAdminsRequest();
    resetSearchControls();
    triggerOneShot = true;

    if (searchByAvatarView != null) {
      searchByAvatarView.performDestroy();
    }
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.dismiss();
    }
    canSendMessageToUser = null;

    if (manager != null) {
      manager.destroy(this);
    }

    if (tooltipInfo != null) {
      tooltipInfo.destroy();
      tooltipInfo = null;
    }

    if (emojiLayout != null) {
      emojiLayout.reset();
    }

    if (wallpaperViewBlurPreview != null) {
      wallpaperViewBlurPreview.performDestroy();
    }

    if (backgroundParamsView != null) {
      backgroundParamsView.performDestroy();

      if (inWallpaperMode() && !inWallpaperPreviewMode()) {
        TGBackground background = tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier());
        if (background != null && background.isWallpaper()) {
          tdlib.settings().setWallpaper(TGBackground.newBlurredWallpaper(tdlib, background, backgroundParamsView.isBlurred()), true, Theme.getWallpaperIdentifier());
        }
      }
    }

    botStartArgument = null;
    sponsoredMessageLoaded = false;

    // switch pm state
    clearSwitchPmButton();

    // edit message state

    resetEditState();

    setInputBlockFlags(0);

    if (inputView != null) {
      String inputText = inputView.getInput();
      if (inputText.isEmpty()) {
        updateSendButton("", false);
      } else {
        inputView.setText("");
      }
    }

    if (pagerContentAdapter != null) {
      pagerContentAdapter.destroy();
    }

    // toast shit

    forceHideToast();

    pinnedMessagesBar.performDestroy();

    if (requestsView != null) {
      requestsView.performDestroy();
    }

    if (reactionsButton != null) {
      reactionsButton.performDestroy();
    }

    if (sendButton != null) {
      sendButton.destroySlowModeCounterController();
    }

    // messagesView.clear();

    closeEmojiKeyboard();
    clearScheduledKeyboard();

    setSearchTransformFactor(0f, false);

    if (headerCell != null) {
      headerCell.pause();
    }

    Views.destroyRecyclerView(messagesView);

    // TODO chat = null;

    if (destroyInstance || !reuseEnabled) {
      super.destroy();
      if (liveLocation != null) {
        liveLocation.destroy();
        liveLocation = null;
      }
      if (pinnedMessagesBar != null)
        pinnedMessagesBar.completeDestroy();
      if (replyBarView != null)
        replyBarView.completeDestroy();
      if (topBar != null)
        topBar.performDestroy();
      if (replyBarView != null)
        replyBarView.performDestroy();
      recordButton.performDestroy();
      if (inputView != null)
        inputView.performDestroy();
      if (wallpaperView != null)
        wallpaperView.performDestroy();
      if (googleClient != null) {
        closeGoogleClient();
      }
      if (botHelper != null) {
        botHelper.destroy();
      }
      removeStaticListeners();
      Views.destroyRecyclerView(wallpapersList);
      if (wallpapersList != null) {
        ((WallpaperAdapter) wallpapersList.getAdapter()).destroy();
      }
      tdlib.settings().removeJoinRequestsDismissListener(this);
      TGLegacyManager.instance().removeEmojiListener(this);
      if (emojiLayout != null) {
        emojiLayout.destroy();
      }
      manager.release();
    }

    checkBroadcastingSomeAction();

    U.gc();
  }

  // Show more

  public void showMore () {
    if (chat == null) {
      return;
    }

    IntList ids = new IntList(4);
    StringList strings = new StringList(4);

    TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);

    if (messageThread != null && messageThread.getMessageThreadId() != 0) {
      if (!manager.isTotallyEmpty() && !messagesHidden) {
        ids.append(R.id.btn_search);
        strings.append(R.string.Search);
      }
      if (status != null && TD.isAdmin(status) || tdlib.canChangeInfo(chat)) {
        ids.append(R.id.btn_manageGroup);
        strings.append(R.string.ManageGroup);
      }
      if (tdlib.canDeleteMessages(messageThread.getChatId())) {
        TdApi.Message message = messageThread.getOldestMessage();
        TdApi.MessageProperties properties = tdlib.getMessagePropertiesSync(message);
        if (properties.canGetMessageThread && properties.canBeDeletedForAllUsers) {
          ids.append(R.id.btn_deleteThread);
          strings.append(R.string.DeleteThread);
        }
      }
      if (messageThread.getChatId() != 0 && messageThread.getChatId() != messageThread.getContextChatId()) {
        ids.append(R.id.btn_openLinkedChat);
        strings.append(R.string.LinkedGroup);
      }
      if (tdlib.chatHasScheduled(messageThread.getChatId())) {
        ids.append(R.id.btn_viewScheduled);
        strings.append(R.string.ScheduledMessages);
      }
      showMore(ids.get(), strings.get());
      return;
    }

    if (!manager.isTotallyEmpty() && (Config.USE_SECRET_SEARCH || !isSecretChat()) && !messagesHidden) {
      ids.append(R.id.btn_search);
      strings.append(R.string.Search);
    }

    if ((!tdlib.isChannel(chat.id) || (status != null && !TD.isLeft(status))) && !tdlib.isSelfChat(chat.id)) {
      ids.append(R.id.btn_mute);
      strings.append(tdlib.chatNotificationsEnabled(chat.id) ? R.string.Mute : R.string.Unmute);
    }

    if (tdlib.canReportChatSpam(chat.id)) {
      ids.append(R.id.btn_reportChat);
      strings.append(R.string.Report);
    }

    if (tdlib.canSetPasscode(chat)) {
      ids.append(R.id.btn_setPasscode);
      strings.append(R.string.PasscodeTitle);
    }
    tdlib.ui().addDeleteChatOptions(getChatId(), ids, strings, !tdlib.isChannel(chat.id), false);

    if (!messagesHidden) {
      if (ChatId.isUserChat(chat.id)) {
        TdApi.User user = tdlib.chatUser(chat);
        if (TD.suggestSharingContact(user)) {
          ids.append(R.id.btn_shareMyContact);
          strings.append(R.string.ShareMyContactInfo);
        }
      }
      if (manager.canRestorePinnedMessage()) {
        ids.append(R.id.btn_showPinnedMessage);
        strings.append(R.string.PinnedMessage);
      }

      if (tdlib.isBotChat(chat) && botHelper != null) {
        if (botHelper.findHelpCommand() != null) {
          ids.append(R.id.btn_botHelp);
          strings.append(R.string.BotHelp);
        }
        if (botHelper.findSettingsCommand() != null) {
          ids.append(R.id.btn_botSettings);
          strings.append(R.string.BotSettings);
        }
      }
    }

    if (linkedChatId != 0 && bottomButtonAction != BOTTOM_ACTION_DISCUSS) {
      ids.append(R.id.btn_openLinkedChat);
      strings.append(tdlib.isChannel(getChatId()) ? R.string.LinkedGroup : R.string.LinkedChannel);
    }

    if (BuildConfig.DEBUG) {
      if (TD.isSecretChat(chat.type)) {
        ids.append(R.id.btn_sendScreenshotNotification);
        strings.append("Send screenshot notification");
      }
      if (!hasSendBasicMessagePermission()) {
        ids.append(R.id.btn_debugShowHideBottomBar);
        strings.append("Show/hide bottom bar");
      }
    }

    showMore(ids.get(), strings.get(), 0);
  }

  // Clear history

  public void deleteAndLeave () {
    if (!isFocused() || chat == null) {
      return;
    }
    tdlib.deleteChat(getChatId(), false, null);
    tdlib.ui().exitToChatScreen(this, getChatId());
  }

  // Message options

  public static class MessageContext {
    public final TGMessage message;
    public final Object tag;
    public final TdApi.ChatMember messageSender;
    public final boolean disableMetadata;

    public MessageContext (TGMessage message) {
      this(message, null, null, false);
    }

    public MessageContext (TGMessage message, Object tag, TdApi.ChatMember messageSender, boolean disableMetadata) {
      this.message = message;
      this.tag = tag;
      this.messageSender = messageSender;
      this.disableMetadata = disableMetadata;
    }
  }

  public void showMessageOptions (TGMessage msg, int[] ids, String[] options, int[] icons, Object selectedMessageTag, TdApi.ChatMember selectedMessageSender, boolean disableMessageMetadata) {
    showMessageOptions(new MessageContext(msg, selectedMessageTag, selectedMessageSender, disableMessageMetadata), ids, options, icons);
  }

  private static OptionItem readItem (Tdlib tdlib, int readDate) {
    return new OptionItem.Builder()
      .name(Lang.getRelativeDate(readDate, TimeUnit.SECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, false, 0, R.string.ReadDate, false))
      .icon(R.drawable.deproko_baseline_check_double_24)
      .color(OptionColor.LIGHT, OptionColor.BLUE)
      .build();
  }

  public void showMessageOptions (MessageContext messageContext, int[] ids, String[] options, int[] icons) {
    final TGMessage msg = messageContext.message;
    SpannableStringBuilder b = new SpannableStringBuilder();
    if (chat != null) {
      final boolean isChannel = tdlib.isChannel(chat.id);
      if (!isChannel && msg.getMessage().content != null) {
        switch (msg.getMessage().content.getConstructor()) {
          case TdApi.MessageSticker.CONSTRUCTOR: {
            String from = tdlib.messageAuthor(msg.getMessage(), true, true);
            if (!StringUtils.isEmpty(from)) {
              b.append(from);
              b.append(": ");
            }
            ContentPreview contentPreview = ContentPreview.getChatListPreview(tdlib, msg.getChatId(), msg.getMessage(), true);
            b.append(contentPreview.buildText(false));
            break;
          }
          case TdApi.MessageDice.CONSTRUCTOR: {
            String emoji = ((TdApi.MessageDice) msg.getMessage().content).emoji;
            b.append(Lang.getString(ContentPreview.EMOJI_DART.textRepresentation.equals(emoji) ? R.string.SendDartHint : ContentPreview.EMOJI_DICE.textRepresentation.equals(emoji) ? R.string.SendDiceHint : R.string.SendUnknownDiceHint, emoji));
            break;
          }
        }
      }
      if (isChannel && !msg.isScheduled()) {
        if (msg.getViewCount() > 0) {
          if (b.length() > 0) {
            b.append(", ");
          }
          b.append(Lang.pluralBold(R.string.xViews, msg.getViewCount()));
        }
        if (msg.getForwardCount() > 0) {
          if (b.length() > 0) {
            b.append(", ");
          }
          b.append(Lang.pluralBold(R.string.StatsXShared, msg.getForwardCount()));
        }
        if (msg.getMessageReactions().getTotalCount() > 0) {
          if (b.length() > 0) {
            b.append(", ");
          }
          b.append(Lang.pluralBold(R.string.xReacted, msg.getMessageReactions().getTotalCount()));
        }
      }
    }
    if (msg.isFailed()) {
      String[] errors = msg.getFailureMessages();
      if (errors != null) {
        if (b.length() > 0) {
          // b.append("\n");
          b.append(". ");
        }
        b.append(Lang.getString(R.string.SendFailureInfo, Strings.join(", ", (Object[]) errors)));
      }
    }
    if (msg.isSponsoredMessage()) {
      String additionalInfo = msg.getSponsoredMessage().additionalInfo;
      if (!StringUtils.isEmpty(additionalInfo)) {
        if (b.length() > 0) {
          b.append('\n');
        }
        b.append(additionalInfo);
      }
    }
    if (!msg.canBeSaved()) {
      if (b.length() > 0) {
        // b.append("\n\n");
        b.append(". ");
      }
      TdApi.MessageSender senderId = msg.getMessage().senderId;
      int resId;
      if (tdlib.cache().senderBot(senderId)) {
        resId = R.string.RestrictSavingBotInfo;
      } else if (tdlib.isChannel(senderId)) {
        resId = R.string.RestrictSavingChannelInfo;
      } else if (tdlib.isUser(senderId)) {
        resId = R.string.RestrictSavingUserInfo;
      } else {
        resId = R.string.RestrictSavingGroupInfo;
      }
      b.append(Lang.getString(resId));
    }

    CharSequence text = StringUtils.trim(b);

    msg.loadAllMessageProperties(() -> {
      msg.loadAvailableReactions(() -> {
        AtomicBoolean shown = new AtomicBoolean(false);
        AtomicReference<RunnableData<TdApi.MessageReadDate>> inject = new AtomicReference<>();
        msg.checkReadDate(expectAgain -> {
          TdApi.MessageReadDate readDate = msg.getReadDate();

          if (shown.getAndSet(true)) {
            if (!expectAgain && readDate != null && readDate.getConstructor() == TdApi.MessageReadDateRead.CONSTRUCTOR) {
              RunnableData<TdApi.MessageReadDate> act = inject.get();
              if (act != null) {
                act.runWithData(readDate);
              }
            }
            return;
          }

          OptionDelegate messageHandler = newMessageOptionDelegate(messageContext);
          Options.Builder messageOptionsBuilder = new Options.Builder()
            .info(StringUtils.isEmpty(text) ? null : text)
            .items(ids, options, null, icons);
          if (readDate != null) {
            switch (readDate.getConstructor()) {
              case TdApi.MessageReadDateRead.CONSTRUCTOR: {
                int date = ((TdApi.MessageReadDateRead) readDate).readDate;
                messageOptionsBuilder.subtitle(readItem(tdlib, date));
                break;
              }
              // Nothing to show
              case TdApi.MessageReadDateUnread.CONSTRUCTOR:
              case TdApi.MessageReadDateTooOld.CONSTRUCTOR:
              case TdApi.MessageReadDateUserPrivacyRestricted.CONSTRUCTOR:
              // TODO?: "View read date by allowing X to see yours or by subscribing to Telegram Premium."
              case TdApi.MessageReadDateMyPrivacyRestricted.CONSTRUCTOR: {
                break;
              }
              default: {
                Td.assertMessageReadDate_5a6e5fbf();
                throw Td.unsupported(readDate);
              }
            }
          }
          Options messageOptions = messageOptionsBuilder.build();
          if (!messageContext.disableMetadata && msg.canBeReacted()) {
            PopupLayout popupLayout = showMessageOptions(messageOptions, messageContext.message, null, messageHandler);
            if (expectAgain && popupLayout != null) {
              inject.set((loadedReadDate) -> {
                if (!popupLayout.isDestroyed() && loadedReadDate.getConstructor() == TdApi.MessageReadDateRead.CONSTRUCTOR) {
                  OptionItem item = readItem(tdlib, ((TdApi.MessageReadDateRead) loadedReadDate).readDate);
                  messageOptionsBuilder.subtitle(item);
                  MessageOptionsController c = ((MessageOptionsPagerController) popupLayout.getBoundController()).findOptionsController();
                  if (c != null) {
                    c.updateSubtitle(messageOptionsBuilder.build());
                  }
                }
              });
            }
          } else {
            PopupLayout popupLayout = showOptions(messageOptions, messageHandler);
            patchReadReceiptsOptions(popupLayout, messageContext);
            patchUsedEmojiPacks(popupLayout, messageContext);
            if (expectAgain && popupLayout != null) {
              inject.set((loadedReadDate) -> {
                if (!popupLayout.isDestroyed() && loadedReadDate.getConstructor() == TdApi.MessageReadDateRead.CONSTRUCTOR) {
                  OptionItem item = readItem(tdlib, ((TdApi.MessageReadDateRead) loadedReadDate).readDate);
                  ((OptionsLayout) popupLayout.getBoundView()).setSubtitle(item);
                }
              });
            }
          }
        }, 200L);
      });
    });
  }

  public void showMessageAddedReactions (TGMessage message, TdApi.ReactionType reactionType) {
    showMessageOptions(null, message, reactionType, newMessageOptionDelegate(message, null, null));
  }

  private boolean isMessageOptionsVisible;

  private PopupLayout showMessageOptions (Options options, TGMessage message, @Nullable TdApi.ReactionType reactionType, OptionDelegate optionsDelegate) {
    if (isMessageOptionsVisible) {
      return null;
    }
    isMessageOptionsVisible = true;

    MessageOptionsPagerController r = new MessageOptionsPagerController(context, tdlib, options, message, reactionType, optionsDelegate) {
      @Override
      protected void onCustomShowComplete () {
        super.onCustomShowComplete();
        optimizeEmojiLayoutForOptionsWindow(true);
      }
    };
    PopupLayout result = r.show();
    r.setDismissListener(new PopupLayout.DismissListener() {
      @Override
      public void onPopupDismiss (PopupLayout popup) {
        optimizeEmojiLayoutForOptionsWindow(false);
        isMessageOptionsVisible = false;
      }

      @Override
      public void onPopupDismissPrepare (PopupLayout popup) {
        onHideMessageOptions();
      }
    });
    prepareToShowMessageOptions();
    hideCursorsForInputView();
    return result;
  }

  private boolean needShowKeyboardAfterHideMessageOptions;
  private boolean needShowEmojiKeyboardAfterHideMessageOptions;

  private void prepareToShowMessageOptions () {
    needShowKeyboardAfterHideMessageOptions = getKeyboardState();
    needShowEmojiKeyboardAfterHideMessageOptions = emojiShown;
    if (needShowKeyboardAfterHideMessageOptions) {    // показываем emoji-клавиатуру, чтобы скрыть системную
      openEmojiKeyboard();                            // делаем emojiLayout невидимым для оптимизации
      emojiLayout.optimizeForDisplayMessageOptionsWindow(true);
    }                                                 // todo: если меню сообщения ниже EmojiLayout, то не скрывать?
  }

  private void optimizeEmojiLayoutForOptionsWindow (boolean needOptimize) {
    if (needShowKeyboardAfterHideMessageOptions || needShowEmojiKeyboardAfterHideMessageOptions) {
      emojiLayout.optimizeForDisplayMessageOptionsWindow(needOptimize);
    }
  }

  private void onHideMessageOptions () {
    if (needShowEmojiKeyboardAfterHideMessageOptions) {
      openEmojiKeyboard();
      emojiLayout.optimizeForDisplayMessageOptionsWindow(false);
    } else if (needShowKeyboardAfterHideMessageOptions) {
      showKeyboard();
    }
  }


  private void patchUsedEmojiPacks (PopupLayout layout, MessageContext messageContext) {
    TGMessage message = messageContext.message;
    long[] emojiPackIds = message.getUniqueEmojiPackIdList();
    if (emojiPackIds.length == 0) {
      return;
    }

    OptionsLayout optionsLayout = (OptionsLayout) layout.getChildAt(1);
    EmojiPacksInfoView emojiPacksInfoView = new EmojiPacksInfoView(layout.getContext(), this, tdlib);
    emojiPacksInfoView.update(message.getFirstEmojiId(), emojiPackIds, new ClickableSpan() {
      @Override
      public void onClick (@NonNull View widget) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        tdlib.ui().showStickerSets(MessagesController.this, emojiPackIds, true, null);
        layout.hideWindow(true);
      }
    }, false);
    optionsLayout.addView(emojiPacksInfoView, 1);

  }

  private void patchReadReceiptsOptions (PopupLayout layout, MessageContext messageContext) {
    TGMessage message = messageContext.message;
    if (!message.canGetViewers() || messageContext.disableMetadata || (message.isUnread() && !message.noUnread()) || !(layout.getChildAt(1) instanceof OptionsLayout)) {
      return;
    }

    OptionsLayout optionsLayout = (OptionsLayout) layout.getChildAt(1);

    LinearLayout receiptWrap = new LinearLayout(layout.getContext());
    receiptWrap.setOrientation(LinearLayout.HORIZONTAL);

    FrameLayout frameLayout = new FrameLayoutFix(layout.getContext());
    frameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, Screen.dp(54f), 1f));
    TextView receiptText = OptionsLayout.genOptionView(layout.getContext(), R.id.more_btn_openReadReceipts, Lang.getString(R.string.LoadingMessageSeen), OptionColor.NORMAL, 0, OptionColor.NORMAL, null, getThemeListeners(), null);

    TripleAvatarView tav = new TripleAvatarView(layout.getContext());

    receiptText.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.CENTER_VERTICAL,
      Screen.dp(18f) + Screen.dp(24f), 0, 0, 0
    ));
    ImageView iconView = new ImageView(context);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.CENTER_VERTICAL,
      Screen.dp(17f), 0, 0, 0
    ));
    iconView.setImageResource(R.drawable.baseline_visibility_24);
    iconView.setColorFilter(Theme.iconColor());
    addThemeFilterListener(iconView, ColorId.icon);
    frameLayout.addView(iconView);
    receiptText.setClickable(false);
    frameLayout.addView(receiptText);
    tav.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(TripleAvatarView.AVATAR_SIZE * 3 + Screen.dp(6)), Screen.dp(54f)));
    receiptWrap.addView(frameLayout);
    receiptWrap.addView(tav);

    Views.setClickable(receiptWrap);
    RippleSupport.setSimpleWhiteBackground(receiptWrap);

    optionsLayout.addView(receiptWrap, 2);

    TextView subtitleView = OptionsLayout.genOptionView(layout.getContext(), 0, null, OptionColor.NORMAL, 0, OptionColor.NORMAL, null, getThemeListeners(), null);

    BoolAnimator isSubtitleVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
      receiptText.setTranslationY(-Screen.dp(10f) * factor);
      subtitleView.setTranslationY(Screen.dp(10f) + Screen.dp(10f) * (1f - factor));
      subtitleView.setAlpha(factor);
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    RunnableData<CharSequence> viewSubtitle = subtitleText -> {
      subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      subtitleView.setTextColor(Theme.textDecentColor());
      subtitleView.setLayoutParams(FrameLayoutFix.newParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER_VERTICAL,
        Screen.dp(18f) + Screen.dp(24f), 0, 0, 0
      ));
      subtitleView.setClickable(false);
      subtitleView.setText(subtitleText);
      subtitleView.setAlpha(0f);
      frameLayout.addView(subtitleView);
      isSubtitleVisible.setValue(true, true);
    };

    tdlib.client().send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), (obj) -> {
      if (obj.getConstructor() != TdApi.MessageViewers.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        TdApi.MessageViewers viewers = (TdApi.MessageViewers) obj;

        if (viewers.viewers.length > 1) {
          receiptText.setText(MessageSeenController.getViewString(message, viewers.viewers.length).toString());
        } else if (viewers.viewers.length == 1) {
          TdApi.MessageViewer viewer = viewers.viewers[0];
          receiptText.setText(tdlib.senderName(new TdApi.MessageSenderUser(viewer.userId)));
          if (viewer.viewDate != 0) {
            String viewedText = TGUser.getActionDateStatus(tdlib, viewer.viewDate, message.getMessage());
            viewSubtitle.runWithData(viewedText);
          }
        } else {
          receiptText.setText(MessageSeenController.getNobodyString(message));
        }

        tav.setUsers(tdlib, viewers);
        receiptWrap.setOnClickListener((v) -> {
          layout.hideWindow(true);
          if (viewers.viewers.length > 1) {
            ModernActionedLayout.showMessageSeen(this, message, viewers);
          } else if (viewers.viewers.length == 1) {
            tdlib.ui().openPrivateProfile(this, viewers.viewers[0].userId, new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
          }
        });
      });
    });
  }

  public boolean onCommandLongPressed (InlineResultCommand command) {
    if (!canWriteMessages() || actionShowing) {
      return false;
    }

    String str;
    if (ChatId.isPrivate(getChatId())) {
      str = command.getCommand() + " ";
    } else {
      str = command.getCommand() + "@" + command.getUsername() + " ";
    }
    inputView.setInput(str, true, true);
    Keyboard.show(inputView);

    return true;
  }

  public boolean onCommandLongPressed (TGMessage msg, String command) {
    if (!canWriteMessages()) {
      return false;
    }
    String str;
    if (tdlib.isMultiChat(getChatId()) && msg.getSender().isBot()) {
      str = command + '@' + msg.getSender().getUsername() + ' ';
    } else {
      str = command + ' ';
    }
    inputView.setInput(str, true, true);
    Keyboard.show(inputView);
    return true;
  }

  public void setInputInlineBot (long userId, String username) {
    if (canWriteMessages()) {
      // pressed via @NephoBot message
      inputView.setInput(username + " ", true, true);
      Keyboard.show(inputView);
    } else {
      tdlib.ui().openPrivateChat(this, userId, new TdlibUi.ChatOpenParameters().keepStack());
    }
  }

  // Message selection

  private @Nullable LongSparseArray<TGMessage> selectedMessageIds;

  private @Nullable MessageWithProperties[] selectedMessagesToArray () {
    if (selectedMessageIds == null) {
      return null;
    }
    final int size = selectedMessageIds.size();
    if (size == 0) {
      return null;
    }
    MessageWithProperties[] messages = new MessageWithProperties[size];
    for (int i = 0; i < size; i++) {
      long messageId = selectedMessageIds.keyAt(i);
      TGMessage msg = selectedMessageIds.valueAt(i);
      TdApi.Message message = msg.getMessage(messageId);
      TdApi.MessageProperties properties = msg.lastMessageProperties(messageId);
      messages[i] = new MessageWithProperties(message, properties);
    }
    return messages;
  }

  private boolean canSelectInBetween () {
    if (selectedMessageIds == null || selectedMessageIds.size() != 2) {
      return false;
    }

    long firstMessageId = selectedMessageIds.keyAt(0);
    long secondMessageId = selectedMessageIds.keyAt(1);

    int firstContainerIndex = manager.getAdapter().indexOfMessageContainer(firstMessageId);
    int secondContainerIndex = manager.getAdapter().indexOfMessageContainer(secondMessageId);
    if (firstContainerIndex == -1 || secondContainerIndex == -1) {
      return false;
    }

    if (firstContainerIndex - secondContainerIndex > 1) {
      return true;
    }

    TGMessage firstContainer = selectedMessageIds.valueAt(0);
    TGMessage secondContainer = selectedMessageIds.valueAt(1);

    return firstContainer.getMessageCountBetween(firstMessageId, secondMessageId) + secondContainer.getMessageCountBetween(firstMessageId, secondMessageId) > 0;
  }

  private void selectMessagesInBetween () {
    if (selectedMessageIds == null || selectedMessageIds.size() != 2) {
      return;
    }

    long firstMessageId = selectedMessageIds.keyAt(0);
    long secondMessageId = selectedMessageIds.keyAt(1);

    int firstContainerIndex = manager.getAdapter().indexOfMessageContainer(firstMessageId);
    int secondContainerIndex = manager.getAdapter().indexOfMessageContainer(secondMessageId);
    if (firstContainerIndex == -1 || secondContainerIndex == -1) {
      return;
    }

    int selectedCount = 0;
    LongSet ids = new LongSet(TdConstants.MAX_MESSAGE_GROUP_SIZE);
    for (int i = secondContainerIndex; i <= firstContainerIndex; i++) {
      TGMessage container = manager.getAdapter().getMessage(i);
      if (!container.canBeSelected()) {
        continue;
      }
      container.getIds(ids, firstMessageId, secondMessageId);
      for (long id : ids) {
        container.setSelected(id, true, true, -1, -1, null);
        putSelectedMessageId(id, container);
      }
      selectedCount += ids.size();
      ids.clear();
    }

    if (selectedCount > 0) {
      updateSelectButtons();
      setSelectedCount(selectedMessageIds.size());
    }
  }

  public final void updateSelectButtons () {
    if (headerView != null) {
      int totalButtonsCount = 0;
      boolean value;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_selectInBetween, (value = canSelectInBetween()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_reply, (value = canReplyToSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_forward, (value = canShareSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_edit, (value = canEditSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_copy, (value = canCopySelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_retry, (value = canResendSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_delete, (value = canDeleteSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_view, (value = canViewSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_send, (value = canSendSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_clearCache, (value = canClearCacheSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.btn_unpinAll, (value = canUnpinSelectedMessages()) ? View.VISIBLE : View.GONE, 0);
      if (value) totalButtonsCount++;
      headerView.updateButton(R.id.menu_messageActions, R.id.menu_btn_report, canReportSelectedMessages(totalButtonsCount) ? View.VISIBLE : View.GONE, 0);
    }
  }

  private void copySelectedMessages () {
    if (selectedMessageIds == null || selectedMessageIds.size() == 0) {
      return;
    }
    if (selectedMessageIds.size() == 1) {
      MessageWithProperties message = getSingleSelectedMessage();
      TdApi.FormattedText formattedText = message != null ? Td.textOrCaption(message.message.content) : null;
      if (formattedText != null) {
        UI.copyText(TD.toCharSequence(formattedText), R.string.CopiedText);
      }
      return;
    }

    SpannableStringBuilder b = new SpannableStringBuilder();
    boolean first = true;
    final int size = selectedMessageIds.size();
    for (int i = 0; i < size; i++) {
      long messageId = selectedMessageIds.keyAt(i);
      TGMessage m = selectedMessageIds.valueAt(i);
      TdApi.Message msg = m.getMessage(messageId);

      if (msg == null) {
        continue;
      }

      if (first) {
        first = false;
      } else {
        b.append("\n\n");
      }

      String author = tdlib.messageAuthor(msg);
      if (msg.viaBotUserId != 0) {
        b.append(Lang.getString(R.string.message_nameViaBot, author, "@" + tdlib.cache().userUsername(msg.viaBotUserId)));
      } else {
        b.append(author);
      }
      b.append(", [");
      b.append(Lang.getTimestamp(msg.date, TimeUnit.SECONDS));
      b.append("]");
      if (msg.isChannelPost) {
        if (!StringUtils.isEmpty(msg.authorSignature)) {
          b.append("\n[");
          b.append(Lang.getString(R.string.PostedBy, msg.authorSignature));
          b.append("]");
        }
      }
      if (msg.replyTo != null) {
        String inReply = m.getInReplyTo();
        if (!StringUtils.isEmpty(inReply)) {
          b.append("\n[");
          b.append(Lang.getString(R.string.InReplyToX, inReply));
          b.append("]");
        }
      }
      if (msg.forwardInfo != null) {
        b.append("\n[ ");
        b.append(Lang.getString(R.string.ForwardedFromX, m.getSourceName()));
        b.append(" ]");
      }
      TdApi.FormattedText text = Td.textOrCaption(msg.content);
      if (msg.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR && msg.content.getConstructor() != TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
        b.append("\n[");
        ContentPreview preview = ContentPreview.getChatListPreview(tdlib, msg.chatId, msg, true);
        b.append(preview.buildText(false));
        b.append("]");
      }
      //noinspection UnsafeOptInUsageError
      if (!Td.isEmpty(text)) {
        b.append('\n');
        b.append(TD.toCharSequence(text));
      }
    }

    UI.copyText(SpannableString.valueOf(b), R.string.CopiedMessages);
  }

  private boolean canCopySelectedMessages () {
    if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
      SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
      return c != null && c.canCopyMessages();
    }
    if (selectedMessageIds == null || selectedMessageIds.size() == 0) {
      return false;
    }
    if (selectedMessageIds.size() == 1) {
      MessageWithProperties m = getSingleSelectedMessage();
      return m.message.canBeSaved && TD.canCopyText(m.message);
    }
    for (int i = 0; i < selectedMessageIds.size(); i++) {
      TdApi.Message message = selectedMessageIds.valueAt(i).getMessage(selectedMessageIds.keyAt(i));
      if (!message.canBeSaved) {
        return false;
      }
    }
    return !isSecretChat();
  }

  private boolean canDeleteSelectedMessages () {
    if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
      SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
      return c != null && c.canDeleteMessages();
    }
    if (selectedMessageIds != null) {
      final int size = selectedMessageIds.size();
      for (int i = 0; i < size; i++) {
        long messageId = selectedMessageIds.keyAt(i);
        TGMessage m = selectedMessageIds.valueAt(i);
        TdApi.Message msg = m.getMessage(messageId);
        TdApi.MessageProperties properties = m.lastMessageProperties(messageId);
        if (msg == null || (!properties.canBeDeletedForAllUsers && !properties.canBeDeletedOnlyForSelf)) {
          return false;
        }
      }
      return size > 0;
    }
    return false;
  }

  private boolean canResendSelectedMessages () {
    if (chat != null && chat.canBeReported && selectedMessageIds != null && !isEventLog()) {
      int size = selectedMessageIds.size();
      if (size > 1) {
        for (int i = 0; i < size; i++) {
          if (!selectedMessageIds.valueAt(i).canResend()) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  private boolean canReportSelectedMessages (int otherButtonsCount) {
    if (chat != null && chat.canBeReported && selectedMessageIds != null && otherButtonsCount <= 3 && !isEventLog()) {
      int size = selectedMessageIds.size();
      if (size > 1) {
        for (int i = 0; i < size; i++) {
          if (!selectedMessageIds.valueAt(i).canBeReported()) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  private boolean canViewSelectedMessages () {
    if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
      SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
      return c != null && c.getSelectedMediaCount() == 1;
    }
    return false;
  }

  private boolean canSendSelectedMessages () {
    if (pagerScrollPosition == 0 && selectedMessageIds != null && selectedMessageIds.size() > 0) {
      for (int i = 0; i < selectedMessageIds.size(); i++) {
        TGMessage msg = selectedMessageIds.valueAt(i);
        TdApi.Message message = msg.getMessage(selectedMessageIds.keyAt(i));
        if (message == null || message.schedulingState == null)
          return false;
      }
      return true;
    }
    return false;
  }

  private boolean canClearCacheSelectedMessages () {
    if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
      SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
      return c != null && c.canClearMessages();
    }
    if (selectedMessageIds != null) {
      boolean hasMergedMessages = false;
      int canClearCache = 0;
      for (int i = 0; i < selectedMessageIds.size(); i++) {
        TGMessage msg = selectedMessageIds.valueAt(i);
        TdApi.Message message = msg.getMessage(selectedMessageIds.keyAt(i));
        if (TD.canDeleteFile(message)) {
          canClearCache++;
          hasMergedMessages = hasMergedMessages || msg.getCombinedMessageCount() > 0;
        }
      }
      return canClearCache > 1 || hasMergedMessages;
    }
    return false;
  }

  private boolean canUnpinSelectedMessages () {
    return arePinnedMessages() && canPinAnyMessage(false);
  }

  private boolean canReplyToSelectedMessages () {
    MessageWithProperties msg = getSingleSelectedMessage();
    return pagerScrollPosition == 0 && msg != null && TD.canReplyTo(msg.message) && canWriteMessages();
  }

  private boolean canEditSelectedMessages () {
    MessageWithProperties msg = getSingleSelectedMessage();
    return !arePinnedMessages() && pagerScrollPosition == 0 && msg != null && msg.properties.canBeEdited && TD.canEditText(msg.message.content);
  }

  private boolean canShareSelectedMessages () {
    if (pagerScrollPosition != 0 && pagerContentAdapter != null) {
      SharedBaseController<?> c = pagerContentAdapter.cachedItems.get(pagerScrollPosition);
      return c != null && c.canShareMessages();
    }
    if (selectedMessageIds != null) {
      final int size = selectedMessageIds.size();
      for (int i = 0; i < size; i++) {
        long messageId = selectedMessageIds.keyAt(i);
        TGMessage m = selectedMessageIds.valueAt(i);
        TdApi.Message msg = m.getMessage(messageId);
        if (msg == null || !m.lastMessageProperties(messageId).canBeForwarded) {
          return false;
        }
      }
      return size > 0;
    }
    return false;
  }

  public boolean unselectMessage (long messageId, TGMessage msg) {
    if (selectedMessageIds != null && msg != null && selectedMessageIds.get(messageId) != null) {
      selectMessage(messageId, msg, -1, -1, false, 0);
      updateSelectButtons();
      return true;
    }
    return false;
  }

  public void selectAllMessages (TGMessage msg, float pivotX, float pivotY) {
    LongSet ids = new LongSet(msg.getMessageCount());
    msg.getIds(ids);
    final int size = ids.size();
    final boolean unselect = msg.isCompletelySelected();
    boolean counterSet = false;
    for (long messageId : ids) {
      boolean ok = false;
      if (unselect) {
        ok = selectedMessageIds != null && selectedMessageIds.get(messageId) != null;
      } else {
        ok = selectedMessageIds == null || selectedMessageIds.get(messageId) == null;
      }
      if (ok && selectMessage(messageId, msg, pivotX, pivotY, false, size)) {
        counterSet = true;
      }
    }
    if (!counterSet) {
      int messageCount = getSelectedMessageCount();
      if (messageCount == 0) {
        closeSelectMode();
        return;
      }
      setSelectedCount(messageCount);
    }
    updateSelectButtons();
  }

  public void selectMessage (final long messageId, TGMessage msg, float pivotX, float pivotY) {
    selectMessage(messageId, msg, pivotX, pivotY, true, -1);
  }

  public int getSelectedMessageCount () {
    return selectedMessageIds != null ? selectedMessageIds.size() : 0;
  }

  private boolean selectMessage (final long messageId, final TGMessage msg, final float pivotX, final float pivotY, final boolean needCounter, final int openCount) {
    if (inTransformMode() && !inSelectMode()) {
      if (inSearchMode()) {
        closeSearchMode(() -> selectMessage(messageId, msg, pivotX, pivotY, needCounter, openCount));
      }
      return false;
    }
    synchronized (this) {
      if (selectedMessageIds == null) {
        selectedMessageIds = new LongSparseArray<>(50);
      }
    }
    boolean counterSet = false;
    if (!inSelectMode()) {
      msg.setSelected(messageId, true, true, pivotX, pivotY, this);
      putSelectedMessageId(messageId, msg);
      openSelectMode(needCounter ? 1 : openCount);
      counterSet = true;
    } else {
      TGMessage selected = selectedMessageIds.get(messageId);
      if (selected != null) {
        deleteSelectedMessageId(messageId);
        boolean finished = selectedMessageIds.size() == 0;
        msg.setSelected(messageId, false, true, pivotX, pivotY, finished ? this : null);
        if (finished) {
          closeSelectMode();
          return false;
        }
      } else {
        if (selectedMessageIds.size() < tdlib.forwardMaxCount()) {
          msg.setSelected(messageId, true, true, pivotX, pivotY, null);
          putSelectedMessageId(messageId, msg);
        } else {
          UI.showToast(Lang.plural(R.string.YouCantForwardMoreMessages, tdlib.forwardMaxCount()), Toast.LENGTH_SHORT);
        }
      }
      if (needCounter) {
        setSelectedCount(selectedMessageIds.size());
        counterSet = true;
      }
    }
    if (needCounter) {
      updateSelectButtons();
    }
    return counterSet;
  }

  @Override
  public void finishSelectMode (int position) {
    if ((position == -2 || position == -1)) {
      if (selectedMessageIds != null) {
        int size = selectedMessageIds.size();
        for (int i = 0; i < size; i++) {
          long messageId = selectedMessageIds.keyAt(i);
          selectedMessageIds.valueAt(i).setSelected(messageId, false, position == -1, -1, -1, i == size - 1 ? this : null);
        }
        clearSelectedMessageIds();
      }
      if (pagerContentAdapter != null) {
        final int size = pagerContentAdapter.cachedItems.size();
        for (int i = 0; i < size; i++) {
          SharedBaseController<?> c = pagerContentAdapter.cachedItems.valueAt(i);
          if (c != null) {
            c.setInMediaSelectMode(false);
          }
        }
      }
      if (position == -1) {
        closeSelectMode();
      }
    }
    if (position == -2) {
      leaveTransformMode();
    }
  }

  private FactorAnimator selectableAnimator;
  private float selectableFactor;

  private void resetSelectableControl () {
    selectableAnimator = null;
    selectableFactor = 0f;
  }

  @Override
  public void onSelectableModeChanged (boolean isSelectable, FactorAnimator animator) {
    selectableAnimator = animator;
  }

  private void putSelectedMessageId (long messageId, TGMessage container) {
    synchronized (this) {
      if (selectedMessageIds == null) {
        selectedMessageIds = new LongSparseArray<>();
      }
      selectedMessageIds.put(messageId, container);
    }
  }

  private void deleteSelectedMessageId (long messageId) {
    synchronized (this) {
      if (selectedMessageIds != null) {
        selectedMessageIds.remove(messageId);
      }
    }
  }

  private void clearSelectedMessageIds () {
    synchronized (this) {
      if (selectedMessageIds != null) {
        selectedMessageIds.clear();
      }
    }
  }

  public boolean isMessageSelected (long chatId, long messageId, TGMessage container) {
    synchronized (this) {
      if (selectedMessageIds != null && selectedMessageIds.size() > 0) {
        int i = selectedMessageIds.indexOfKey(messageId);
        if (i >= 0) {
          TGMessage msg = selectedMessageIds.valueAt(i);
          if (msg != null && msg.getChatId() == chatId) {
            selectedMessageIds.setValueAt(i, container);
            return true;
          }
        }
      }
    }
    return false;
  }

  public float getSelectableFactor () {
    return selectableFactor;
  }

  @Override
  public void onSelectableFactorChanged (float factor, FactorAnimator callee) {
    if (selectableAnimator == callee && selectableFactor != factor) {
      selectableFactor = factor;
      MessagesAdapter adapter = manager.getAdapter();
      final int messageCount = adapter.getMessageCount();
      for (int i = 0; i < messageCount; i++) {
        TGMessage msg = adapter.getMessage(i);
        if (msg != null) {
          msg.onSelectableFactorChanged();
        }
      }
    }
  }

  public boolean canWriteMessages () {
    return inputView != null && bottomWrap.getVisibility() == View.VISIBLE && inputView.getVisibility() == View.VISIBLE;
  }

  public boolean canPinAnyMessage (boolean checkUi) {
    return (checkUi ? canWriteMessages() : hasWritePermission()) && chat != null && tdlib.canPinMessages(chat) && !areScheduled;
  }

  public boolean isSecretChat () {
    return chat != null && ChatId.isSecret(chat.id);
  }

  public boolean inPreviewSearchMode () {
    return inPreviewMode && previewMode == PREVIEW_MODE_SEARCH;
  }

  public boolean isSelfChat () {
    return chat != null && tdlib.isSelfChat(chat.id);
  }

  @Deprecated
  private boolean hasWritePermission () {
    // FIXME: this check is outdated and no longer correct
    return chat != null && tdlib.canSendBasicMessage(chat) && !isEventLog();
  }

  public boolean canSendPhotosAndVideos () { // FIXME separate photos and videos
    return
      tdlib.canSendMessage(chat, RightId.SEND_PHOTOS) &&
      tdlib.canSendMessage(chat, RightId.SEND_VIDEOS);
  }

  public boolean hasSendMessagePermission (@RightId int rightId) {
    return chat != null && tdlib.canSendMessage(chat, rightId) && !isEventLog();
  }

  public boolean hasSendBasicMessagePermission () {
    return chat != null && tdlib.canSendBasicMessage(chat) && !isEventLog();
  }

  public boolean hasSendSomeMediaPermission () {
    return chat != null && tdlib.canSendSendSomeMedia(chat) && !isEventLog();
  }

  // test

  private OptionDelegate newMessageOptionDelegate (final MessageContext context) {
    return newMessageOptionDelegate(context.message, context.messageSender, context.tag);
  }

  private void cancelSheduledKeyboardOpeningAndHideAllKeyboards () {
    if (needShowKeyboardAfterHideMessageOptions || needShowEmojiKeyboardAfterHideMessageOptions) {
      needShowEmojiKeyboardAfterHideMessageOptions = false;
      needShowKeyboardAfterHideMessageOptions = false;
      hideAllKeyboards();
    }
  }

  private OptionDelegate newMessageOptionDelegate (final TGMessage selectedMessage, final TdApi.ChatMember selectedMessageSender, final Object selectedMessageTag) {
    return (itemView, id) -> {
      if (id == R.id.btn_cancel) {
        return true;
      }
      if (selectedMessage == null) {
        return false;
      }
      if (id == R.id.btn_emojiPackInfoButton) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        tdlib.ui().showStickerSets(MessagesController.this, ((EmojiPacksInfoView) itemView).getEmojiPacksIds(), true, null);
        return true;
      } else if (id == R.id.btn_messageApplyLocalization) {
        if (selectedMessage.getMessage().content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR) {
          TdApi.Document document = ((TdApi.MessageDocument) selectedMessage.getMessage().content).document;
          tdlib.ui().readCustomLanguage(this, document, langPack -> tdlib.ui().showLanguageInstallPrompt(this, langPack, selectedMessage.getMessage()), null);
        }
        return true;
      } else if (id == R.id.btn_messageInstallTheme) {
        if (selectedMessage.getMessage().content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR) {
          TdApi.Document document = ((TdApi.MessageDocument) selectedMessage.getMessage().content).document;
          tdlib.ui().readCustomTheme(this, document, null, null);
        }
        return true;
      } else if (id == R.id.btn_messageSponsorInfo) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        ModernActionedLayout mal = new ModernActionedLayout(this);
        mal.setController(new SponsoredMessagesInfoController(mal, R.string.SponsoredInfoMenu));
        mal.initCustom();
        mal.show();
        return true;
      } else if (id == R.id.btn_messageCopyLink) {
        tdlib.getMessageLink(selectedMessage.getNewestMessage(), selectedMessage.getMessageCount() > 1, messageThread != null, link -> UI.copyText(link.url, link.isPublic ? R.string.CopiedLink : R.string.CopiedLinkPrivate));
        return true;
      } else if (id == R.id.btn_messageRetractVote) {
        TdApi.Message message = selectedMessage.getMessage();
        tdlib.client().send(new TdApi.SetPollAnswer(message.chatId, message.id, null), tdlib.okHandler());
        return true;
      } else if (id == R.id.btn_messagePollStop) {
        TdApi.Message message = selectedMessage.getMessage();
        TdApi.Poll poll = ((TdApi.MessagePoll) message.content).poll;
        boolean isQuiz = poll.type.getConstructor() == TdApi.PollTypeQuiz.CONSTRUCTOR;
        showOptions(Lang.getStringBold(isQuiz ? R.string.StopQuizWarn : R.string.StopPollWarn, poll.question), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(isQuiz ? R.string.StopQuiz : R.string.StopPoll), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_poll_24, R.drawable.baseline_cancel_24}, (optionItemView, optionId) -> {
          if (optionId == R.id.btn_done) {
            tdlib.client().send(new TdApi.StopPoll(message.chatId, message.id, message.replyMarkup), tdlib.okHandler());
          }
          return true;
        });
        return true;
      } else if (id == R.id.btn_messageLiveStop) {
        ((TGMessageLocation) selectedMessage).stopLiveLocation();
        return true;
      } else if (id == R.id.btn_messageAddContact) {
        TdApi.Contact contact = ((TdApi.MessageContact) selectedMessage.getMessage().content).contact;
        tdlib.ui().addContact(this, contact);
        return true;
      } else if (id == R.id.btn_messageCallContact) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        TdApi.Contact contact = ((TdApi.MessageContact) selectedMessage.getMessage().content).contact;
        showCallOptions(contact.phoneNumber, contact.userId);
        return true;
      } else if (id == R.id.btn_messageResend) {
        tdlib.resendMessages(selectedMessage.getChatId(), selectedMessage.getIds());
        return true;
      } else if (id == R.id.btn_messageSendNow) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        if (!showRestriction(null, tdlib.getSlowModeRestrictionText(getChatId()))) {
          tdlib.client().send(new TdApi.EditMessageSchedulingState(getChatId(), selectedMessage.getId(), null), tdlib.okHandler());
        }
        return true;
      } else if (id == R.id.btn_messageReschedule) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        tdlib.ui().showScheduleOptions(this, getChatId(), false, (sendOptions, disableMarkdown) -> {
          if (sendOptions.schedulingState != null) {
            tdlib.client().send(new TdApi.EditMessageSchedulingState(getChatId(), selectedMessage.getId(), sendOptions.schedulingState), tdlib.okHandler());
          }
        }, null, null);
        return true;
      } else if (id == R.id.btn_messageShowSource) {
        selectedMessage.openSourceMessage();
        return true;
      } else if (id == R.id.btn_messageShowInChat) {
        long chatId = selectedMessage.getChatId();
        long messageId = selectedMessage.getSmallestId();
        long[] otherMessageIds = selectedMessage.getOtherMessageIds(messageId);
        tdlib.ui().openMessage(this, chatId, new MessageId(chatId, messageId, otherMessageIds), selectedMessage.openParameters());
        return true;
      } else if (id == R.id.btn_messageShowInChatSearch) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        if (inOnlyFoundMode()) {
          long chatId = selectedMessage.getChatId();
          long messageId = selectedMessage.getSmallestId();
          long[] otherMessageIds = selectedMessage.getOtherMessageIds(messageId);
          manager.setHighlightMessageId(new MessageId(chatId, messageId), MessagesManager.HIGHLIGHT_MODE_NORMAL);
          onSetSearchFilteredShowMode(false);
        }
        return true;
      } else if (id == R.id.btn_messageDirections) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        TdApi.MessageContent content = selectedMessage.getMessage().content;
        switch (content.getConstructor()) {
          case TdApi.MessageVenue.CONSTRUCTOR: {
            TdApi.Venue venue = ((TdApi.MessageVenue) content).venue;
            Intents.openDirections(venue.location.latitude, venue.location.longitude, venue.title, venue.address);
            break;
          }
          case TdApi.MessageLocation.CONSTRUCTOR: {
            TdApi.Location location = ((TdApi.MessageLocation) content).location;
            Intents.openDirections(location.latitude, location.longitude, null, null);
            break;
          }
        }
        return true;
      } else if (id == R.id.btn_messageFoursquare) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        String venueId = ((TdApi.MessageVenue) selectedMessage.getMessage().content).venue.id;
        tdlib.ui().openUrl(this, "https://foursquare.com/v/" + venueId, selectedMessage.openParameters());
        return true;
      } else if (id == R.id.btn_messageCall) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        tdlib.context().calls().makeCall(this, tdlib.calleeUserId(selectedMessage.getMessage()), null);
        return true;
      } else if (id == R.id.btn_messageShareCallLogs) {
        VoIPLogs.Pair logFiles = (VoIPLogs.Pair) selectedMessageTag;
        tdlib.ui().shareCallLogs(this, logFiles, true);
      } else if (id == R.id.btn_messageDelete) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        tdlib.ui().showDeleteOptions(this, selectedMessage.getAllMessagesAndProperties(), null);
        return true;
      } else if (id == R.id.btn_messageReport) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        if (selectedMessage.isSponsoredMessage()) {
          TdUi.reportChatSponsoredMessage(this, tdlib, getChatId(), selectedMessage.getSponsoredMessage());
        } else {
          reportChat(selectedMessage.getAllMessagesAndProperties(), null);
        }
        return true;
      } else if (id == R.id.btn_messageSelect) {
        selectAllMessages(selectedMessage, -1, -1);
        return true;
      } else if (id == R.id.btn_messageViewList) {//FIXME?
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        foundMessageId = new MessageId(selectedMessage.getMessage().chatId, selectedMessage.getMessage().id);
        searchFromUserMessageId = foundMessageId;
        manager.setHighlightMessageId(foundMessageId, MessagesManager.HIGHLIGHT_MODE_NORMAL);
        viewMessagesFromSender(selectedMessage.getMessage().senderId, true);
        return true;
      } else if (id == R.id.btn_messageRestrictMember) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        EditRightsController c = new EditRightsController(context, tdlib);
        c.setArguments(new EditRightsController.Args(selectedMessage.getChatId(), selectedMessage.getMessage().senderId, true, tdlib.chatStatus(selectedMessage.getChatId()), selectedMessageSender));
        navigateTo(c);
        return true;
      } else if (id == R.id.btn_messageBlockUser) {
        if (selectedMessageSender != null) {
          tdlib.ui().kickMember(this, selectedMessage.getChatId(), selectedMessage.getMessage().senderId, selectedMessageSender.status);
        }
      } else if (id == R.id.btn_messageUnblockMember) {
        if (selectedMessageSender != null) {
          tdlib.ui().unblockMember(this, selectedMessage.getChatId(), selectedMessage.getMessage().senderId, selectedMessageSender.status);
        }
        return true;
      } else if (id == R.id.btn_messageMore) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        IntList ids = new IntList(3);
        IntList icons = new IntList(3);
        StringList strings = new StringList(3);
        final long chatId = selectedMessage.getChatId();
        if (ChatId.isMultiChat(chatId) && !tdlib.isChannel(chatId) && TD.isAdmin(tdlib.chatStatus(chatId)) && Td.getSenderId(selectedMessage.getMessage().senderId) != chatId) {
          TdApi.MessageSender senderId = selectedMessage.getMessage().senderId;
          tdlib.send(new TdApi.GetChatMember(chatId, senderId), (otherMember, error) -> {
            runOnUiThreadOptional(() -> {
              if (!selectedMessage.isDestroyed()) {
                Object tag = MessageView.fillMessageOptions(this, selectedMessage, otherMember, ids, icons, strings, true);
                if (!ids.isEmpty()) {
                  showMessageOptions(selectedMessage, ids.get(), strings.get(), icons.get(), tag, otherMember, true);
                }
              }
            });
          });
        } else {
          Object tag = MessageView.fillMessageOptions(this, selectedMessage, selectedMessageSender, ids, icons, strings, true);
          if (!ids.isEmpty()) {
            showMessageOptions(selectedMessage, ids.get(), strings.get(), icons.get(), tag, selectedMessageSender, true);
          }
        }
        return true;
      } else if (id == R.id.btn_messageStickerSet) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        ((TGMessageSticker) selectedMessage).openStickerSet();
      } else if (id == R.id.btn_messageFavoriteContent || id == R.id.btn_messageUnfavoriteContent) {
        boolean isFavorite = id == R.id.btn_messageFavoriteContent;
        int fileId = ((TdApi.MessageSticker) selectedMessage.getMessage().content).sticker.sticker.id;
        TdApi.InputFile inputFile = new TdApi.InputFileId(fileId);
        tdlib.client().send(isFavorite ? new TdApi.AddFavoriteSticker(inputFile) : new TdApi.RemoveFavoriteSticker(inputFile), tdlib.okHandler());
      } else if (id == R.id.btn_messageUnpin || id == R.id.btn_messagePin) {
        pinUnpinMessage(selectedMessage, id == R.id.btn_messagePin);
        return true;
      } else if (id == R.id.btn_messageReply) {
        TdApi.Message message = selectedMessage.getNewestMessage();
        selectedMessage.getMessageProperties(message.id, properties -> runOnUiThreadOptional(() -> {
          if (properties != null) {
            showReply(new MessageWithProperties(message, properties), null, true, true);
            if (inputView.isEmpty()) {
              Keyboard.show(inputView);
            }
          }
        }));
        return true;
      } else if (id == R.id.btn_messageReplies) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        selectedMessage.openMessageThread();
        return true;
      } else if (id == R.id.btn_messageReplyWithDice) {
        sendDice(itemView, ((TdApi.MessageDice) selectedMessage.getMessage().content).emoji);
        return true;
      } else if (id == R.id.btn_copyTranslation || id == R.id.btn_messageCopy) {
        if (!selectedMessage.canBeSaved()) {
          context().tooltipManager().builder(itemView).show(tdlib, R.string.ChannelNoCopy).hideDelayed();
          return false;
        }
        TdApi.Message message = null;
        if (selectedMessage instanceof TGMessageMedia) {
          long messageId = ((TGMessageMedia) selectedMessage).getCaptionMessageId();
          message = selectedMessage.getMessage(messageId);
        }
        if (message == null) {
          message = selectedMessage.getNewestMessage();
        }
        TdApi.FormattedText text = id == R.id.btn_copyTranslation ? selectedMessage.getTranslatedText() : Td.textOrCaption(message.content);
        if (text != null)
          UI.copyText(TD.toCopyText(text), R.string.CopiedText);
        return true;
      } else if (id == R.id.btn_messageEdit) {
        TdApi.Message message = null;
        if (selectedMessage instanceof TGMessageMedia) {
          long messageId = ((TGMessageMedia) selectedMessage).getCaptionMessageId();
          message = selectedMessage.getMessage(messageId);
        }
        if (message == null) {
          message = selectedMessage.getNewestMessage();
        }
        TdApi.Message editingMessage = message;
        TdApi.MessageProperties properties = selectedMessage.lastMessageProperties(editingMessage.id);
        editMessage(new MessageWithProperties(editingMessage, properties));
        return true;
      } else if (id == R.id.btn_messageShare) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        if (selectedMessage.canBeForwarded()) {
          shareMessages(selectedMessage.getAllMessages(), false);
        }
        return true;
      } else if (id == R.id.btn_chatTranslate) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        startTranslateMessages(selectedMessage);
        return true;
      } else if (id == R.id.btn_chatTranslateOff) {
        stopTranslateMessages(selectedMessage);
        return true;
      } else if (id == R.id.btn_saveGif) {
        if (selectedMessageTag != null) {
          if (!selectedMessage.canBeSaved()) {
            context().tooltipManager().builder(itemView).show(tdlib, R.string.ChannelNoSave).hideDelayed();
            return false;
          }
          //noinspection unchecked
          tdlib.ui().saveGifs(((List<TD.DownloadedFile>) selectedMessageTag));
        }
        return true;
      } else if (id == R.id.btn_saveFile) {
        if (selectedMessageTag != null) {
          if (!selectedMessage.canBeSaved()) {
            context().tooltipManager().builder(itemView).show(tdlib, R.string.ChannelNoSave).hideDelayed();
            return false;
          }
          //noinspection unchecked
          TD.saveFiles(context, (List<TD.DownloadedFile>) selectedMessageTag);
        }
        return true;
      } else if (id == R.id.btn_openIn) {
        if (selectedMessageTag != null) {
          TdApi.Document document = ((TdApi.MessageDocument) selectedMessage.getMessage().content).document;
          U.openFile(this, U.getFileName(document.document.local.path), new File(document.document.local.path), document.mimeType, 0);
        }
        return true;
      } else if (id == R.id.btn_addToPlaylist) {
        TdlibManager.instance().player().addToPlayList(selectedMessage.getMessage());
        return true;
      } else if (id == R.id.btn_downloadFile) {
        if (!selectedMessage.canBeSaved()) {
          context().tooltipManager().builder(itemView).show(tdlib, R.string.ChannelNoSave).hideDelayed();
          return false;
        }
        TdApi.File file = TD.getFile(selectedMessage);
        if (file != null && !file.local.isDownloadingActive && !file.local.isDownloadingCompleted) {
          tdlib.files().downloadFile(file);
        }
        return true;
      } else if (id == R.id.btn_pauseFile) {
        TdApi.File file = TD.getFile(selectedMessage);
        if (file != null && file.local.isDownloadingActive && !tdlib.context().player().isPlayingFileId(file.id)) {
          tdlib.files().cancelDownloadOrUploadFile(file.id, false, true);
        }
        return true;
      } else if (id == R.id.btn_viewStatistics) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        TdApi.Message[] messages = selectedMessage.getAllMessages();
        MessageStatisticsController statsController = new MessageStatisticsController(context, tdlib);
        if (messages.length == 1) {
          statsController.setArguments(new MessageStatisticsController.Args(messages[0].chatId, messages[0]));
        } else {
          statsController.setArguments(new MessageStatisticsController.Args(messages[0].chatId, Arrays.asList(messages)));
        }
        navigateTo(statsController);

        return true;
      } else if (id == R.id.btn_deleteFile) {
        if (selectedMessageTag != null) {
          //noinspection unchecked
          TD.deleteFiles(this, (List<TD.DownloadedFile>) selectedMessageTag, null);
        } else {
          TdApi.Message[] messages = selectedMessage.getAllMessages();
          final SparseArrayCompat<TdApi.File> files = new SparseArrayCompat<>(messages.length);
          for (TdApi.Message message : messages) {
            TdApi.File file = TD.getFile(message);
            if (TD.canDeleteFile(message, file)) {
              files.put(file.id, file);
            }
          }
          if (files.size() > 0) {
            TD.deleteFiles(this, ArrayUtils.asArray(files, new TdApi.File[files.size()]), null);
          }
        }
        return true;
      } else if (id == R.id.btn_stickerSetInfo) {
        cancelSheduledKeyboardOpeningAndHideAllKeyboards();
        TdApi.MessageContent content = selectedMessage.getMessage().content;
        if (content.getConstructor() == TdApi.MessageSticker.CONSTRUCTOR) {
          TdApi.MessageSticker sticker = (TdApi.MessageSticker) content;
          tdlib.ui().showStickerSet(this, sticker.sticker.setId, null);
        }
        return true;
      }
      return true;
    };
  }

  // Action button

  private ChatBottomBarView bottomBar;

  private static final int BOTTOM_ACTION_NONE = 0;
  private static final int BOTTOM_ACTION_FOLLOW = 1;
  private static final int BOTTOM_ACTION_TOGGLE_MUTE = 2;
  private static final int BOTTOM_ACTION_UNPIN_ALL = 3;
  private static final int BOTTOM_ACTION_DISCUSS = 4;
  private static final int BOTTOM_ACTION_APPLY_WALLPAPER = 5;
  private static final int BOTTOM_ACTION_TEST = 100;

  private int bottomButtonAction;
  private boolean needBigPadding;

  private void checkExtraPadding () {
    boolean needBigPadding = bottomBarVisible.getValue(); //  && !scrollToBottomVisible;
    if (this.needBigPadding != needBigPadding) {
      this.needBigPadding = needBigPadding;
      manager.rebuildLastItem();
    }
  }

  public boolean needExtraBigPadding () {
    return needBigPadding && !inWallpaperPreviewMode();
  }

  private void showBottomButton (int bottomButtonAction, long bottomButtonData, boolean animated) {
    this.bottomButtonAction = bottomButtonAction;
    boolean animateButtonContent = animated && bottomBarVisible.getFloatValue() > 0f;
    switch (bottomButtonAction) {
      case BOTTOM_ACTION_FOLLOW:
        bottomBar.setAction(R.id.btn_follow, Lang.getString(R.string.Follow),  R.drawable.baseline_group_add_24, animateButtonContent);
        bottomBar.clearPreviewChat();
        break;
      case BOTTOM_ACTION_DISCUSS:
        bottomBar.setAction(R.id.btn_openLinkedChat, Lang.getString(R.string.Discuss), R.drawable.baseline_chat_bubble_24, animateButtonContent);
        bottomBar.setPreviewChatId(null, bottomButtonData, null);
        break;
      case BOTTOM_ACTION_TOGGLE_MUTE: {
        boolean notificationsEnabled = tdlib.chatNotificationsEnabled(getChatId());
        bottomBar.setAction(R.id.btn_mute, Lang.getString(notificationsEnabled ? R.string.Mute : R.string.Unmute), notificationsEnabled ? R.drawable.baseline_notifications_off_24 : R.drawable.baseline_notifications_active_24, animateButtonContent);
        bottomBar.clearPreviewChat();
        break;
      }
      case BOTTOM_ACTION_UNPIN_ALL: {
        bottomBar.setAction(R.id.btn_unpinAll, Lang.getString(canPinAnyMessage(false) ? R.string.UnpinAll : R.string.DismissAllPinned), R.drawable.deproko_baseline_pin_undo_24, animated);
        bottomBar.clearPreviewChat();
        break;
      }
      case BOTTOM_ACTION_TEST: {
        bottomBar.setAction(R.id.btn_test, "test", R.drawable.baseline_warning_24, animateButtonContent);
        bottomBar.clearPreviewChat();
        break;
      }
      case BOTTOM_ACTION_APPLY_WALLPAPER: {
        bottomBar.setAction(R.id.btn_applyWallpaper, Lang.getString(R.string.ChatBackgroundApply), R.drawable.baseline_warning_24, animateButtonContent);
        bottomBar.clearPreviewChat();
        break;
      }
    }
    bottomBarVisible.setValue(true, animated);
    checkExtraPadding();
  }

  private void hideBottomBar (boolean animated) {
    bottomBarVisible.setValue(false, animated);
    checkExtraPadding();
  }

  private void updateBottomBarStyle () {
    if (bottomBar == null)
      return;
    float bottomButtonFactor = bottomBarVisible.getFloatValue();
    float detachFactor = scrollToBottomVisible.getFloatValue();
    int barHeight = Screen.dp(48f);
    int baseY = needSearchControlsTranslate() ? (int) ((float) barHeight * MathUtils.clamp(searchControlsFactor)) : 0;
    float fromY = bottomButtonFactor == 1f ? baseY : baseY + (int) ((float) barHeight * (1f - bottomButtonFactor));
    float alpha = (1f - 1f * detachFactor * (1f - bottomButtonFactor)) * (1f - searchControlsFactor);
    int moveBy = Screen.dp(74f) - Screen.dp(16f);
    float toY = -getButtonsOffset() - Screen.dp(16f) - moveBy - moveBy * mentionButtonFactor; //  -getReplyOffset() - (Screen.dp(74f) - Screen.dp(48f)) / 2f;
    bottomBar.setCollapseFactor(detachFactor);
    bottomBar.setAlpha(alpha);
    int dx = (int) ((bottomBar.getMeasuredWidth() / 2f - Screen.dp(16f) - barHeight / 2) * detachFactor);
    bottomBar.setTranslationY(bottomButtonFactor == 1f && detachFactor == 0f ? fromY : fromY + (toY - fromY) * detachFactor);
    bottomBar.setTranslationX(dx);
    int desiredVisibility = (bottomButtonFactor > 0f && searchControlsFactor != 1f) ? View.VISIBLE : View.GONE;
    if (bottomBar.getVisibility() != desiredVisibility) {
      bottomBar.setVisibility(desiredVisibility);
    }
  }

  private static final int ANIMATOR_BOTTOM_BUTTON = 12;
  private final BoolAnimator bottomBarVisible = new BoolAnimator(ANIMATOR_BOTTOM_BUTTON, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public int getBottomOffset () {
    return (int) (getReplyOffset() + getAttachedFilesOffset());
  }

  private static final int ACTION_DELETE_CHAT = 1;
  private static final int ACTION_BOT_START = 2;
  private static final int ACTION_JOIN_CHAT = 3;
  private static final int ACTION_OPEN_SUPERGROUP = 4;
  private static final int ACTION_UNBAN_USER = 5;
  private static final int ACTION_EMPTY = 6;
  private static final int ACTION_EVENT_LOG_SETTINGS = 7;

  private FrameLayoutFix actionButtonWrap;
  private TextView actionButton;
  private boolean actionShowing;
  private int actionMode;

  public void showActionButton (int resource, int mode) {
    showActionButton(Lang.getString(resource).toUpperCase(), mode, true);
  }

  public void showActionButton (String string, int mode) {
    showActionButton(string, mode, true);
  }

  private String botStartArgument;

  public boolean showActionBotButton () {
    return showActionBotButton(botStartArgument);
  }

  public boolean showActionBotButton (String argument) {
    if (tdlib.isBotChat(chat)) {
      this.botStartArgument = argument;
      showActionButton(R.string.BotStart, ACTION_BOT_START);
      return true;
    }
    return false;
  }

  public void showActionDeleteChatButton () {
    if (ChatId.isBasicGroup(getChatId()) && chat.lastMessage != null && chat.lastMessage.content.getConstructor() == TdApi.MessageChatUpgradeTo.CONSTRUCTOR) {
      showActionButton(Lang.getString(R.string.OpenSupergroup).toUpperCase(), ACTION_OPEN_SUPERGROUP);
    } else {
      showActionButton(R.string.DeleteChat, ACTION_DELETE_CHAT);
    }
  }

  public void showActionJoinChatButton () {
    TdApi.Supergroup supergroup = tdlib.chatToSupergroup(getChatId());
    if (supergroup != null && supergroup.joinByRequest && !TD.isAdmin(supergroup.status)) {
      showActionButton(supergroup.isChannel ? R.string.RequestJoinChannel : R.string.RequestJoinGroup, ACTION_JOIN_CHAT);
    } else {
      showActionButton(R.string.JoinChat, ACTION_JOIN_CHAT);
    }
  }

  public void showActionUnblockButton () {
    long userId = tdlib.chatUserId(chat);
    if (!tdlib.isRepliesChat(chat.id) && tdlib.isBotChat(chat)) {
      showActionButton(R.string.RestartBot, ACTION_BOT_START);
    } else {
      showActionButton(R.string.Unblock, ACTION_UNBAN_USER);
    }
  }

  public void showSecretChatAction (TdApi.SecretChat secretChat) {
    switch (secretChat.state.getConstructor()) {
      case TdApi.SecretChatStatePending.CONSTRUCTOR: {
        showActionButton(Lang.getString(R.string.AwaitingEncryption, tdlib.cache().userFirstName(secretChat.userId)), ACTION_EMPTY, false);
        break;
      }
      case TdApi.SecretChatStateClosed.CONSTRUCTOR: {
        showActionButton(Lang.getString(R.string.SecretChatCancelled), ACTION_EMPTY, false);
        break;
      }
    }
  }

  private void updateActionButton (CharSequence text, int mode, boolean isActive) {
    actionMode = mode;
    actionButton.setEnabled(isActive);
    actionButton.setTextColor(isActive ? Theme.textLinkColor() : Theme.textAccentColor());
    removeThemeListenerByTarget(actionButton);
    addThemeTextColorListener(actionButton, isActive ? ColorId.textLink : ColorId.text);
    Views.setMediumText(actionButton, text);
  }

  public void showActionButton (CharSequence text, int mode, boolean isActive) {
    if (actionShowing) {
      hideSoftwareKeyboard();
      updateActionButton(text, mode, isActive);
      return;
    }

    boolean initialized;

    if (actionButtonWrap == null) {
      actionButtonWrap = new FrameLayoutFix(context());
      actionButtonWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(49f)));

      actionButton = new NoScrollTextView(context());
      actionButton.setId(R.id.btn_chatAction);
      actionButton.setOnClickListener(this);
      actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      actionButton.setTypeface(Fonts.getRobotoMedium());
      actionButton.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      // actionButton.setSingleLine(false);
      RippleSupport.setSimpleWhiteBackground(actionButton, this);
      actionButton.setEllipsize(TextUtils.TruncateAt.END);
      actionButton.setGravity(Gravity.CENTER);
      actionButton.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      actionButtonWrap.addView(actionButton);

      if (mode == ACTION_EVENT_LOG_SETTINGS) {
        ImageView imageView = new ImageView(context());
        imageView.setOnClickListener(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.baseline_help_outline_24);
        imageView.setColorFilter(Theme.getColor(ColorId.textNeutral));
        addThemeFilterListener(imageView, ColorId.textNeutral);
        imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(49f), Screen.dp(49f), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        imageView.setId(R.id.btn_help);
        actionButtonWrap.addView(imageView);
      }

      initialized = true;
    } else {
      initialized = false;
    }

    actionShowing = true;

    updateActionButton(text, mode, isActive);

    closeCommandsKeyboard(false);
    closeEmojiKeyboard();

    if (inputView != null) {
      inputView.setVisibility(View.GONE);
      inputView.setInput("", false, false);
    }

    hideAttachButtons();
    hideSendButton();
    hideEmojiButton();

    if (initialized) {
      bottomWrap.addView(actionButtonWrap, Math.min(bottomWrap.getChildCount(), 1));
    } else {
      actionButtonWrap.setVisibility(View.VISIBLE);
    }

    hideSoftwareKeyboard();
  }

  public void hideActionButton () {
    if (!actionShowing) {
      return;
    }

    actionButtonWrap.setVisibility(View.GONE);
    if (inputView != null) {
      inputView.setVisibility(View.VISIBLE);
      checkSendButton(false);
    }

    displayEmojiButton();

    actionShowing = false;
  }

  private void processChatAction () {
    switch (actionMode) {
      case ACTION_OPEN_SUPERGROUP: {
        if (chat != null && chat.lastMessage != null && chat.lastMessage.content.getConstructor() == TdApi.MessageChatUpgradeTo.CONSTRUCTOR) {
          TdApi.MessageChatUpgradeTo to = (TdApi.MessageChatUpgradeTo) chat.lastMessage.content;
          tdlib.ui().openSupergroupChat(this, to.supergroupId, null);
        }
        break;
      }
      case ACTION_DELETE_CHAT: {
        hideSoftwareKeyboard();
        tdlib.ui().showDeleteChatConfirm(this, getChatId());
        break;
      }
      case ACTION_BOT_START: {
        long chatId = getChatId();
        Runnable after = () -> {
          if (!isDestroyed() && getChatId() == chatId) {
            tdlib.sendBotStartMessage(tdlib.chatUserId(chatId), chat.id, botStartArgument);
            hideActionButton();
          }
        };
        if (tdlib.chatFullyBlocked(chat.id)) {
          tdlib.unblockSender(tdlib.sender(chat.id), result -> {
            if (TD.isOk(result)) {
              tdlib.ui().postDelayed(after, 200);
            } else {
              tdlib.okHandler().onResult(result);
            }
          });
        } else {
          after.run();
        }
        break;
      }
      case ACTION_JOIN_CHAT: {
        tdlib.client().send(new TdApi.AddChatMember(chat.id, tdlib.myUserId(), 0), result -> {
          if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
            runOnUiThreadOptional(() -> {
              if (isFocused()) {
                context
                  .tooltipManager()
                  .builder(actionButton)
                  .show(this, tdlib, R.drawable.baseline_error_24, TD.toErrorString(result));
              }
            });
          }
        });
        break;
      }
      case ACTION_UNBAN_USER: {
        tdlib.unblockSender(tdlib.sender(chat.id), tdlib.okHandler());
        break;
      }
      case ACTION_EMPTY: {
        // Nothing to do
        break;
      }
      case ACTION_EVENT_LOG_SETTINGS: {
        openEventLogSettings();
        break;
      }
    }
  }

  // Scroll button

  public void setScrollToBottomVisible (boolean isVisible, boolean isReverse) {
    setScrollToBottomVisible(isVisible, isReverse, isFocused() || getParentOrSelf().isFocused());
  }

  private static final int ANIMATOR_SCROLL_TO_BOTTOM = 6;
  private final BoolAnimator scrollToBottomVisible = new BoolAnimator(ANIMATOR_SCROLL_TO_BOTTOM, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator scrollToBottomReverse = new BoolAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      scrollToBottomButton.setRotation(180f * factor);
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);

  public void setScrollToBottomVisible (boolean isVisible, boolean isReverse, boolean animated) {
    if (messagesHidden) {
      isVisible = false;
    }
    scrollToBottomVisible.setValue(isVisible, animated);
    scrollToBottomReverse.setValue(isReverse, animated && scrollToBottomVisible.getFloatValue() > 0f);
    checkExtraPadding();
  }

  public void onFirstChatScroll () {

  }

  private void setUnreadCountBadge (int unreadCount, boolean isUpdate) {
    if (unreadCount != 0 && (inPreviewMode || isInForceTouchMode())) {
      return;
    }
    boolean animated = isUpdate && scrollToBottomVisible.getFloatValue() > 0f && isFocused();
    boolean isMuted = messageThread == null && !tdlib.chatNotificationsEnabled(getChatId());
    unreadCountView.setCounter(unreadCount, isMuted, animated);
  }

  private static final int ANIMATOR_MENTION_BUTTON = 7;
  private FactorAnimator mentionButtonAnimator;
  private boolean mentionButtonVisible;
  private float mentionButtonFactor = 1f;
  private int mentionCountBadge;

  private void setMentionButtonVisible (boolean visible, boolean animated) {
    if (this.mentionButtonVisible != visible) {
      this.mentionButtonVisible = visible;
      final float toFactor = visible ? 1f : 0f;
      if (animated) {
        if (mentionButtonAnimator == null) {
          mentionButtonAnimator = new FactorAnimator(ANIMATOR_MENTION_BUTTON, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.mentionButtonFactor);
        }
        mentionButtonAnimator.animateTo(toFactor);
      } else {
        if (mentionButtonAnimator != null) {
          mentionButtonAnimator.forceFactor(toFactor);
        }
        setMentionButtonFactor(toFactor);
      }
    }
  }

  private void setMentionCountBadge (int mentionCount) {
    if (mentionCount > 0 && (inPreviewMode || isInForceTouchMode() || areScheduledOnly())) {
      return;
    }
    if (mentionCountBadge != mentionCount) {
      mentionCountBadge = mentionCount;
      boolean visible = mentionCount > 0;
      boolean animate = isFocused();
      mentionCountView.setCounter(mentionCount, false, animate && mentionButtonFactor > 0f);
      setMentionButtonVisible(visible, animate);
    }
  }

  private void setMentionButtonFactor (float factor) {
    if (this.mentionButtonFactor != factor) {
      this.mentionButtonFactor = factor;
      float range = MathUtils.clamp(factor);
      mentionButtonWrap.setAlpha(range);
      updateBottomBarStyle();
    }
  }



  private static final int ANIMATOR_REACTION_BUTTON = 15;
  private FactorAnimator reactionButtonAnimator;
  private boolean reactionButtonVisible;
  private float reactionButtonFactor = 1f;
  private int reactionCountBadge;

  private void setReactionButtonVisible (boolean visible, boolean animated) {
    if (this.reactionButtonVisible != visible) {
      this.reactionButtonVisible = visible;
      final float toFactor = visible ? 1f : 0f;
      if (animated) {
        if (reactionButtonAnimator == null) {
          reactionButtonAnimator = new FactorAnimator(ANIMATOR_REACTION_BUTTON, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.reactionButtonFactor);
        }
        reactionButtonAnimator.animateTo(toFactor);
      } else {
        if (reactionButtonAnimator != null) {
          reactionButtonAnimator.forceFactor(toFactor);
        }
        setReactionButtonFactor(toFactor);
      }
    }
  }

  private void setReactionCountBadge (int reactionCount) {
    if (reactionCount > 0 && (inPreviewMode || isInForceTouchMode() || areScheduledOnly())) {
      return;
    }
    if (reactionCountBadge != reactionCount) {
      reactionCountBadge = reactionCount;
      boolean visible = reactionCount > 0;
      boolean animate = isFocused();
      reactionsCountView.setCounter(reactionCount, true, animate && reactionButtonFactor > 0f);
      setReactionButtonVisible(visible, animate);
    }
    if (reactionCount > 0) {
      reactionsButton.setUnreadReaction(tdlib.getSingleUnreadReaction(getChatId()));
    }
  }

  private void setReactionButtonFactor (float factor) {
    if (this.reactionButtonFactor != factor) {
      this.reactionButtonFactor = factor;
      float range = MathUtils.clamp(factor);
      reactionsButtonWrap.setAlpha(range);
      updateBottomBarStyle();
    }
  }



  // Etc

  public void startSwipe (View view) {
    messagesView.startSwipe(view);
  }

  // Reply utils

  public boolean highlightMessage (MessageId messageId, boolean isScheduled) {
    if (areScheduledOnly() == isScheduled) {
      highlightMessage(messageId);
      return true;
    }
    return false;
  }

  public void highlightMessage (MessageId messageId, @Nullable TdlibUi.UrlOpenParameters parameters) {
    if (parameters != null && parameters.messageId != null && parameters.messageId.getChatId() == messageId.getChatId()) {
      highlightMessage(messageId, parameters.messageId);
    } else {
      highlightMessage(messageId);
    }
  }

  public void highlightMessage (MessageId messageId) {
    if (inPreviewSearchMode()) {
      tdlib.ui().openMessage(this, getChatId(), messageId, null);
      return;
    }
    manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_NORMAL, null, pagerScrollPosition == 0);
    showMessagesListIfNeeded();
  }

  public void highlightMessage (MessageId messageId, MessageId fromMessageId) {
    if (inOnlyFoundMode()) {
      manager.setHighlightMessageId(messageId, MessagesManager.HIGHLIGHT_MODE_NORMAL);
      onSetSearchFilteredShowMode(false);
      return;
    }
    long[] returnToMessageIds = manager.extendReturnToMessageIdStack(fromMessageId);
    highlightMessage(messageId, returnToMessageIds);
  }

  public void highlightMessage (MessageId messageId, long[] returnToMessageIds) {
    if (inPreviewSearchMode()) {
      tdlib.ui().openMessage(this, getChatId(), messageId, null);
      return;
    }
    manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_NORMAL, returnToMessageIds, pagerScrollPosition == 0);
    showMessagesListIfNeeded();
  }

  private static class ReplyInfo {
    public final Tdlib tdlib;
    public final MessageWithProperties message;
    public final @Nullable TdApi.InputTextQuote quote;

    public ReplyInfo (Tdlib tdlib, MessageWithProperties message, @Nullable TdApi.InputTextQuote quote) {
      this.tdlib = tdlib;
      this.message = message;
      this.quote = quote;
    }

    public MessageId toMessageId () {
      return new MessageId(message.message);
    }

    public TdApi.InputMessageReplyTo toInputMessageReply (long inChatId, long inMessageThreadId) {
      if (inChatId != message.message.chatId || (message.message.isTopicMessage && inMessageThreadId != 0 && message.message.messageThreadId != inMessageThreadId)) {
        return new TdApi.InputMessageReplyToExternalMessage(message.message.chatId, message.message.id, quote);
      } else {
        return new TdApi.InputMessageReplyToMessage(message.message.id, quote);
      }
    }
  }

  private ReplyInfo reply;
  private @Nullable ReplyBarView replyBarView;

  private CollapseListView topBar;
  private TopBarView actionView;
  private CollapseListView.Item actionItem;

  private CustomTextView toastAlertView;
  private CollapseListView.Item toastAlertItem;

  private PinnedMessagesBar pinnedMessagesBar;
  private CollapseListView.Item pinnedMessagesItem;

  private JoinRequestsView requestsView;
  private CollapseListView.Item requestsItem;

  public @Nullable TdApi.InputMessageReplyTo getCurrentReplyId () {
    if (reply != null) {
      return reply.toInputMessageReply(messageThread != null ? messageThread.getChatId() : getChatId(), getMessageThreadId());
    }
    return null;
  }

  public @Nullable TdApi.InputMessageReplyTo obtainReplyTo () {
    if (reply != null) {
      TdApi.InputMessageReplyTo replyTo = getCurrentReplyId();
      closeReply(true, false);
      return replyTo;
    }
    return null;
  }

  public void removeReply (long chatId, long[] messageIds) {
    if (reply != null) {
      for (long msgId : messageIds) {
        if (reply.message.message.chatId == chatId && msgId == reply.message.message.id) {
          reply = null;
          updateReplyBarVisibility(true);
          break;
        }
      }
    }
  }

  public void showReply (MessageWithProperties msg, @Nullable TdApi.InputTextQuote quote, boolean byUser, boolean showKeyboard) {
    if (inPreviewMode || isInForceTouchMode()) {
      return;
    }
    if (msg == null || msg.message.id == 0) {
      this.reply = null;
      updateReplyBarVisibility(true);
      return;
    }
    if (inSearchMode()) {
      // TODO prevent keyboard close
      closeSearchMode(null);
    } else if (inSelectMode()) {
      finishSelectMode(-1);
    }
    collapsePinnedMessagesBar(true);
    // TODO show keyboard properly
    if (reply == null || reply.message.message.id != msg.message.id || reply.message.message.chatId != msg.message.chatId || !Td.equalsTo(reply.quote, quote)) {
      this.reply = new ReplyInfo(tdlib, msg, quote);
      updateReplyBarVisibility(true);

      if (byUser) {
        inputView.setTextChangedSinceChatOpened(true);
        saveDraft();
      }
    }
    if (showKeyboard) {
      Keyboard.show(inputView);
    }
  }

  private void updateReplyBarVisibility (boolean animated) {
    if (replyBarView == null) {
      return;
    }
    boolean shouldBeVisible = true;
    if (showingLinkPreview()) {
      replyBarView.showWebPage(findTargetContext(), findTargetContext().findSelectedUrlIndex());
    } else if (isEditingMessage()) {
      replyBarView.setEditingMessage(new MessageWithProperties(editContext.message, editContext.messageProperties), editContext.localPickedFile);
    } else if (reply != null) {
      replyBarView.setReplyTo(reply.message, reply.quote);
    } else {
      shouldBeVisible = false;
    }
    final float toFactor = shouldBeVisible ? 1f : 0f;
    if (animated && replyBarVisible.getFloatValue() != toFactor) {
      setForceHw(true); // Resets back in onFactorChangeFinished
    }
    replyBarVisible.setValue(shouldBeVisible, animated);
  }

  @Override
  public void onDismissReplyBar (ReplyBarView view) {
    if (showingLinkPreview()) {
      closeLinkPreview();
    } else if (isEditingMessage()) {
      closeEdit(false);
    } else {
      closeReply(true, true);
    }
  }

  private MediaToReplacePickerManager mediaPickerManager;

  @Override
  public void onMessageMediaReplaceRequested (ReplyBarView view, TdApi.Message message) {
    if (mediaPickerManager == null) {
      mediaPickerManager = new MediaToReplacePickerManager(this);
    }
    mediaPickerManager.openMediaView(this::setMessageMediaEdited, getChatId(), null, false, this, message);
  }

  @Override
  public void onMessageMediaEditRequested (ReplyBarView view, TdApi.Message message) {
    if (editContext != null && editContext.localPickedFile != null && editContext.localPickedFile.imageGalleryFile != null) {
      onMessageMediaEditRequestedImpl(editContext.localPickedFile.imageGalleryFile);
    } else {
      U.toGalleryFile(message, this::onMessageMediaEditRequestedImpl);
    }
  }

  private void onMessageMediaEditRequestedImpl (ImageGalleryFile imageGalleryFile) {
    if (imageGalleryFile == null || isDestroyed()) {
      return;
    }
    if (inputView != null) {
      imageGalleryFile.setCaption(inputView.getOutputText(false));
    }

    final MediaStack stack = new MediaStack(context, tdlib);
    stack.set(new MediaItem(context, tdlib, imageGalleryFile));

    MediaViewController controller = new MediaViewController(context, tdlib);
    controller.setArguments(
      MediaViewController.Args.fromGallery(this, null, new MediaSelectDelegate() {
            @Override
            public boolean isMediaItemSelected (int index, MediaItem item) {
              return false;
            }

            @Override
            public void setMediaItemSelected (int index, MediaItem item, boolean isSelected) {

            }

            @Override
            public int getSelectedMediaCount () {
              return 0;
            }

            @Override
            public long getOutputChatId () {
              return MessagesController.this.getOutputChatId();
            }

            @Override
            public boolean canDisableMarkdown () {
              return false;
            }

            @Override
            public ArrayList<ImageFile> getSelectedMediaItems (boolean copy) {
              return null;
            }
          },
          new MediaSpoilerSendDelegate() {
            @Override
            public boolean sendSelectedItems (View view, ArrayList<ImageFile> images, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles, boolean showCationAboveMedia, boolean hasSpoiler) {
              if (isDestroyed()) {
                return false;
              }
              // FIXME: hasSpoiler, showCationAboveMedia
              setMessageMediaEdited(new MediaToReplacePickerManager.LocalPickedFile(imageGalleryFile));
              return true;
            }
          },
          stack, areScheduledOnly())
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_MULTI_SELECTION_MEDIA)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SET_DESTRUCTION_TIMER)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SEND_BUTTON_HAPTIC_MENU)
        .setSendButtonIcon(R.drawable.baseline_check_circle_24)
        .setReceiverChatId(getChatId()));
    controller.open();
  }

  private void setMessageMediaEdited (MediaToReplacePickerManager.LocalPickedFile file) {
    editContext.localPickedFile = file;
    if (inputView != null && file.imageGalleryFile != null) {
      TdApi.FormattedText formattedText = file.imageGalleryFile.getCaption(true, false);
      if (!Td.isEmpty(formattedText)) {
        CharSequence text = TD.toCharSequence(formattedText);
        inputView.setText(text);
        inputView.setSelection(text.length());
      }
    }

    updateReplyBarVisibility(true);
  }

  private TooltipOverlayView.TooltipInfo anotherChatHint;

  @Override
  public void onMessageHighlightRequested (ReplyBarView view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote) {
    if (message.chatId == getChatId()) {
      highlightMessage(new MessageId(message.chatId, message.id));
    } else {
      if (anotherChatHint != null && anotherChatHint.isVisible()) {
        tdlib.ui().openMessage(this, message.chatId, new MessageId(message.chatId, message.id), new TdlibUi.UrlOpenParameters().controller(this));
        return;
      }
      anotherChatHint = context()
        .tooltipManager()
        .builder(view)
        .show(this, tdlib, R.drawable.baseline_info_24, Lang.getString(R.string.AnotherChatReplyHint))
        .hideDelayed();
    }
  }

  private void forceDraftReply (final TdApi.InputMessageReplyTo replyTo) {
    final long currentChatId = chat.id;
    final long replyToChatId, replyToMessageId;
    final TdApi.InputTextQuote replyToQuote;
    switch (replyTo.getConstructor()) {
      case TdApi.InputMessageReplyToMessage.CONSTRUCTOR: {
        TdApi.InputMessageReplyToMessage replyToMessage = (TdApi.InputMessageReplyToMessage) replyTo;
        replyToChatId = currentChatId;
        replyToMessageId = replyToMessage.messageId;
        replyToQuote = replyToMessage.quote;
        break;
      }
      case TdApi.InputMessageReplyToExternalMessage.CONSTRUCTOR: {
        TdApi.InputMessageReplyToExternalMessage replyToExternalMessage = (TdApi.InputMessageReplyToExternalMessage) replyTo;
        replyToChatId = replyToExternalMessage.chatId;
        replyToMessageId = replyToExternalMessage.messageId;
        replyToQuote = replyToExternalMessage.quote;
        break;
      }
      case TdApi.InputMessageReplyToStory.CONSTRUCTOR: // Unreachable.
        return;
      default:
        Td.assertInputMessageReplyTo_acef6f3a();
        throw Td.unsupported(replyTo);
    }
    if (replyToChatId == currentChatId) {
      TGMessage foundMessage = manager.getAdapter().findMessageById(replyToMessageId);
      if (foundMessage != null) {
        foundMessage.getMessageWithProperties(msg -> runOnUiThreadOptional(() -> {
          forceReply(msg, replyToQuote);
        }));
        return;
      }
    }
    tdlib.send(new TdApi.GetMessage(replyToChatId, replyToMessageId), (foundReplyMessage, error) -> {
      if (foundReplyMessage != null) {
        runOnUiThreadOptional(() -> {
          if (chat != null && chat.id == currentChatId) {
            TdApi.DraftMessage draftMessage = getDraftMessage();
            TdApi.InputMessageReplyTo currentReplyTo = draftMessage != null ? draftMessage.replyTo : null;
            if (Td.equalsTo(replyTo, currentReplyTo)) {
              tdlib.getMessageProperties(foundReplyMessage, properties -> runOnUiThreadOptional(() -> {
                if (properties != null) {
                  forceReply(new MessageWithProperties(foundReplyMessage, properties), replyToQuote);
                }
              }));
            }
          }
        });
      }
    });
  }

  public void forceReply (MessageWithProperties message, @Nullable TdApi.InputTextQuote quote) {
    if (message == null || chat == null || inPreviewMode || isInForceTouchMode()) {
      clearReply();
      return;
    }

    reply = new ReplyInfo(tdlib, message, quote);
    updateReplyBarVisibility(false);
  }

  private void clearReply () {
    reply = null;
    draftContext.reset();
    updateReplyBarVisibility(false);
  }

  public void closeReply (final boolean byUser, boolean animated) {
    tdlib.uiExecute(() -> {
      if (reply != null) {
        reply = null;
        updateReplyBarVisibility(animated);
        if (byUser) {
          inputView.setTextChangedSinceChatOpened(true);
          saveDraft();
        }
      }
    });
  }

  private boolean forceHw;
  private int originalLayerType1, originalLayerType2, originalLayerType3;

  private void setForceHw (boolean forceHw) {
    if (!Views.HARDWARE_LAYER_ENABLED)
      return;
    if (this.forceHw != forceHw) {
      this.forceHw = forceHw;
      if (forceHw) {
        originalLayerType1 = messagesView.getLayerType();
        if (originalLayerType1 != View.LAYER_TYPE_HARDWARE) {
          Views.setLayerType(messagesView, View.LAYER_TYPE_HARDWARE);
        }
        if (replyBarView != null) {
          originalLayerType2 = replyBarView.getLayerType();
          if (originalLayerType2 != View.LAYER_TYPE_HARDWARE) {
            Views.setLayerType(replyBarView, View.LAYER_TYPE_HARDWARE);
          }
        }
        originalLayerType3 = bottomShadowView.getLayerType();
        if (originalLayerType3 != View.LAYER_TYPE_HARDWARE) {
          Views.setLayerType(bottomShadowView, View.LAYER_TYPE_HARDWARE);
        }
      } else {
        if (originalLayerType1 != View.LAYER_TYPE_HARDWARE) {
          Views.setLayerType(messagesView, originalLayerType1);
        }
        if (originalLayerType2 != View.LAYER_TYPE_HARDWARE) {
          Views.setLayerType(replyBarView, originalLayerType2);
        }
        if (originalLayerType3 != View.LAYER_TYPE_HARDWARE) {
          Views.setLayerType(bottomShadowView, originalLayerType3);
        }
      }
    }
  }

  private static final boolean ANIMATE_REPLY_BAR = false;

  private final BoolAnimator replyBarVisible = new BoolAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      if (ANIMATE_REPLY_BAR && replyBarView != null) {
        replyBarView.setAnimationsDisabled(factor == 0f);
      }
      updateReplyView();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (finalFactor == 1f || finalFactor == 0f) {
        setForceHw(false);
      }
      if (finalFactor == 0f) {
        replyBarView.reset();
      }
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);

  private float getReplyOffset () {
    if (replyBarView == null) {
      return 0;
    }
    return replyBarVisible.getFloatValue() * (1f - getSearchTransformFactor()) * (float) (replyBarView.getLayoutParams().height);
  }

  private float getButtonsOffset () {
    return getReplyOffset() + getAttachedFilesOffset() + getSearchControlsOffset() + getKeyboardOffset();
  }

  private float getMentionButtonY () {
    int moveY = Screen.dp(74f) - Screen.dp(16f);
    float y = -getButtonsOffset() - moveY * scrollToBottomVisible.getFloatValue();
    if (isInForceTouchMode()) {
      y += -Screen.dp(16f) + Screen.dp(4f);
    }
    return y;
  }

  private float getReactionButtonY () {
    int scrollToBottomOffset = Screen.dp(74f) - Screen.dp(16f);
    int mentionButtonOffset = Screen.dp(74f) - Screen.dp(16f);

    float y = -getButtonsOffset();
    y -= scrollToBottomOffset * scrollToBottomVisible.getFloatValue();
    y -= mentionButtonOffset * mentionButtonFactor;

    if (isInForceTouchMode()) {
      y += -Screen.dp(16f) + Screen.dp(4f);
    }

    return y;
  }

  private void checkScrollButtonOffsets () {
    if (isInForceTouchMode()) {
      return;
    }
    float offsetY = -getButtonsOffset();
    if (scrollToBottomButtonWrap != null) {
      scrollToBottomButtonWrap.setTranslationY(offsetY);
    }
    if (mentionButtonWrap != null) {
      mentionButtonWrap.setTranslationY(getMentionButtonY());
    }

    if (reactionsButtonWrap != null) {
      reactionsButtonWrap.setTranslationY(getReactionButtonY());
    }
    if (goToPrevFoundMessageButtonBadge != null) {
      goToPrevFoundMessageButtonBadge.setTranslationY(offsetY);
    }
    if (goToNextFoundMessageButtonBadge != null) {
      goToNextFoundMessageButtonBadge.setTranslationY(offsetY - Screen.dp(74 - 16));
    }
    updateBottomBarStyle();
  }

  private void updateReplyView () {
    float y = -getReplyOffset();
    float offset = -getAttachedFilesOffset();
    final float keyboardOffset = -getKeyboardOffset();
    messagesView.setTranslationY(y + offset + keyboardOffset);
    bottomShadowView.setTranslationY(y + offset + keyboardOffset);
    if (replyBarView != null) {
      replyBarView.setTranslationY(y + keyboardOffset);
    }
    checkScrollButtonOffsets();
    onMessagesFrameChanged();
  }

  // Edit utils

  public boolean isEditingMessage () {
    return editContext != null;
  }

  public boolean hasEditedChanges () {
    if (isEditingMessage()) {
      TdApi.FormattedText newText = inputView != null ? inputView.getOutputText(true) : null;
      TdApi.LinkPreviewOptions newOptions = inputView != null ? editContext.takeOutputLinkPreviewOptions(false) : null;

      //noinspection SwitchIntDef
      switch (editContext.message.content.getConstructor()) {
        case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
          TdApi.FormattedText oldText = Td.textOrCaption(editContext.message.content);
          if (!Td.equalsTo(oldText, newText) || !Td.equalsTo(null, newOptions)) {
            return true;
          }
          break;
        }
        case TdApi.MessageText.CONSTRUCTOR: {
          TdApi.MessageText oldMessageText = (TdApi.MessageText) editContext.message.content;
          TdApi.LinkPreviewOptions oldLinkPreviewOptions = oldMessageText.linkPreviewOptions;
          if (!Td.equalsTo(oldMessageText.text, newText) || !Td.equalsTo(oldLinkPreviewOptions, newOptions)) {
            return true;
          }
          break;
        }
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageVideo.CONSTRUCTOR:
        case TdApi.MessageAudio.CONSTRUCTOR:
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
        case TdApi.MessageDocument.CONSTRUCTOR:
        case TdApi.MessageAnimation.CONSTRUCTOR: {
          TdApi.FormattedText oldText = Td.textOrCaption(editContext.message.content);
          return !Td.equalsTo(oldText, newText);
        }
        default: {
          Td.assertMessageContent_640c68ad();
          break;
        }
      }
    }

    return false;
  }

  public boolean isEditingCaption () {
    return editContext != null && editContext.isMediaCaption();
  }

  public static class MessageInputContext {
    private final MessagesController context;
    private final Tdlib tdlib;
    private final TdApi.Message message;
    private final TdApi.MessageProperties messageProperties;
    private @NonNull FoundUrls foundUrls;
    private MediaToReplacePickerManager.LocalPickedFile localPickedFile;

    private FoundUrls dismissedFoundUrls;

    private final TdApi.LinkPreviewOptions linkPreviewOptions;

    MessageInputContext (MessagesController context, Tdlib tdlib, @Nullable TdApi.Message existingMessage, @Nullable TdApi.MessageProperties existingMessageProperties) {
      this.context = context;
      this.tdlib = tdlib;
      this.foundUrls = FoundUrls.emptyResult();
      this.message = existingMessage;
      this.messageProperties = existingMessageProperties;
      if (existingMessage != null && Td.isText(existingMessage.content)) {
        TdApi.MessageText messageText = (TdApi.MessageText) existingMessage.content;
        linkPreviewOptions = Td.copyOf(messageText.linkPreviewOptions);
        if (linkPreviewOptions.isDisabled) {
          dismissedFoundUrls = foundUrls = new FoundUrls(messageText);
        }
      } else {
        linkPreviewOptions = new TdApi.LinkPreviewOptions();
      }
    }

    public TdApi.Message getExistingMessage () {
      return message;
    }

    public boolean isMediaCaption () {
      return message != null && !Td.isText(message.content);
    }

    public @NonNull FoundUrls getFoundUrls () {
      return foundUrls;
    }

    public boolean setLinkPreviewUrl (@NonNull String url) {
      if (url.equals(this.linkPreviewOptions.url)) {
        return false;
      }
      if (StringUtils.isEmpty(this.linkPreviewOptions.url) && !foundUrls.isEmpty() && url.equals(foundUrls.urls[0])) {
        return false;
      }
      this.linkPreviewOptions.url = url;
      return true;
    }

    private final Map<String, LinkPreview> linkPreviews = new HashMap<>();

    public @Nullable LinkPreview getSelectedLinkPreview () {
      if (linkPreviewOptions.isDisabled) {
        return null;
      }
      int index = findSelectedUrlIndex();
      if (index == -1)
        return null;
      String url = foundUrls.urls[index];
      return getLinkPreview(url);
    }

    public @NonNull LinkPreview getLinkPreview (String url) {
      LinkPreview linkPreview = linkPreviews.get(url);
      if (linkPreview == null) {
        linkPreview = new LinkPreview(tdlib, url, message);
        linkPreview.addLoadCallback(loadedLinkPreview -> {
          if (loadedLinkPreview.isNotFound()) {
            context.updateReplyBarVisibility(true);
          }
        });
        linkPreviews.put(url, linkPreview);
      }
      return linkPreview;
    }

    public int findSelectedUrlIndex () {
      if (foundUrls.isEmpty()) {
        return -1;
      } else if (StringUtils.isEmpty(linkPreviewOptions.url)) {
        return 0;
      } else {
        int index = foundUrls.indexOfUrl(linkPreviewOptions.url);
        if (index != -1) {
          return index;
        }
        return 0;
      }
    }

    public TdApi.LinkPreviewOptions takeOutputLinkPreviewOptions (boolean copy) {
      LinkPreview linkPreview = getSelectedLinkPreview();
      if (linkPreview != null) {
        boolean forceLargeMedia = linkPreview.forceLargeMedia();
        boolean forceSmallMedia = linkPreview.forceSmallMedia();
        if (linkPreviewOptions.forceLargeMedia != forceLargeMedia || linkPreviewOptions.forceSmallMedia != forceSmallMedia) {
          linkPreviewOptions.forceLargeMedia = forceLargeMedia;
          linkPreviewOptions.forceSmallMedia = forceSmallMedia;
        }
        if ((forceLargeMedia || forceSmallMedia) && StringUtils.isEmpty(linkPreviewOptions.url)) {
          linkPreviewOptions.url = linkPreview.url;
        }
      }
      return copy ? Td.copyOf(linkPreviewOptions) : linkPreviewOptions;
    }

    public boolean takeOutputShowCaptionAboveMedia () {
      // TODO
      return Td.showCaptionAboveMedia(message.content);
    }

    public TdApi.LinkPreview takePreloadedOutputLinkPreview () {
      LinkPreview preview = getSelectedLinkPreview();
      TdApi.LinkPreview linkPreview = preview != null ? preview.linkPreview : null;
      if (linkPreview != null && linkPreview.hasLargeMedia) {
        boolean showLargeMedia = preview.getOutputShowLargeMedia();
        if (linkPreview.showLargeMedia != showLargeMedia) {
          linkPreview = Td.copyOf(linkPreview);
          linkPreview.showLargeMedia = showLargeMedia;
        }
      }
      return linkPreview;
    }

    public boolean checkMessage (long chatId, long messageId) {
      return message != null && message.chatId == chatId && message.id == messageId;
    }

    boolean setFoundUrls (@Nullable FoundUrls foundUrls) {
      if (foundUrls == null) {
        foundUrls = FoundUrls.emptyResult();
      }

      FoundUrls previouslyFoundUrls = this.foundUrls;
      this.foundUrls = foundUrls;

      boolean wasEmpty = previouslyFoundUrls.isEmpty();
      boolean nowEmpty = foundUrls.isEmpty();

      boolean hasChanges = wasEmpty != nowEmpty || (!nowEmpty && !previouslyFoundUrls.equals(foundUrls));

      if (dismissedFoundUrls != null && !foundUrls.equals(dismissedFoundUrls)) {
        linkPreviewOptions.isDisabled = false;
        dismissedFoundUrls = null;
        hasChanges = true;
      }
      if (!StringUtils.isEmpty(linkPreviewOptions.url) && !foundUrls.hasUrl(linkPreviewOptions.url)) {
        linkPreviewOptions.url = null;
        hasChanges = true;
      }
      if (hasChanges && !foundUrls.isEmpty()) {
        linkPreviewOptions.isDisabled = false;
      }
      if (hasChanges && foundUrls.size() > 1 && Settings.instance().needTutorial(Settings.TUTORIAL_MULTIPLE_LINK_PREVIEWS)) {
        Settings.instance().markTutorialAsShown(Settings.TUTORIAL_MULTIPLE_LINK_PREVIEWS);
        context.context()
          .tooltipManager()
          .builder(context.replyBarView)
          .icon(R.drawable.baseline_info_24)
          .show(tdlib, R.string.SwipeToSwapLinkPreview);
      }

      return hasChanges;
    }

    void dismiss () {
      dismissedFoundUrls = foundUrls;
      Td.reset(linkPreviewOptions);
      linkPreviewOptions.isDisabled = true;
    }

    void reset () {
      dismissedFoundUrls = null;
      foundUrls = FoundUrls.emptyResult();
      Td.reset(linkPreviewOptions);
    }

    public boolean isVisible () {
      if (!linkPreviewOptions.isDisabled && !foundUrls.isEmpty()) {
        for (String url : foundUrls.urls) {
          LinkPreview linkPreview = linkPreviews.get(url);
          if (linkPreview == null || !linkPreview.isNotFound()) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private void setInEditMode (boolean inEditMode, String futureText) {
    sendButton.setInEditMode(inEditMode);
    messageSenderButton.setAnimateVisible(!inEditMode);
    if (inputView != null) {
      inputView.setIsInEditMessageMode(inEditMode, futureText);
    }
  }

  public boolean inSimpleSendMode () {
    return sendButton.inSimpleSendMode();
  }

  private static final int FLAG_INPUT_EDITING = 1;
  private static final int FLAG_INPUT_OFFSCREEN = 1 << 1;
  private static final int FLAG_INPUT_RECORDING = 1 << 2;
  private static final int FLAG_INPUT_TEXT_DISABLED = 1 << 3;

  private int inputBlockFlags;

  private boolean setInputBlockFlags (int flags) {
    if (this.inputBlockFlags != flags) {
      boolean prevIsBlocked = this.inputBlockFlags != 0;
      this.inputBlockFlags = flags;
      boolean nowIsBlocked = flags != 0;
      if (prevIsBlocked != nowIsBlocked && inputView != null) {
        inputView.setInputBlocked(nowIsBlocked);
      }
      return true;
    }
    return false;
  }

  private void setInputBlockFlag (int flag, boolean active) {
    if (setInputBlockFlags(BitwiseUtils.setFlag(inputBlockFlags, flag, active))) {
      if ((flag == FLAG_INPUT_OFFSCREEN || flag == FLAG_INPUT_TEXT_DISABLED) && inputView != null) {
        inputView.setEnabled(
          !BitwiseUtils.hasFlag(inputBlockFlags, FLAG_INPUT_OFFSCREEN) &&
          !BitwiseUtils.hasFlag(inputBlockFlags, FLAG_INPUT_TEXT_DISABLED)
        );
      }
    }
  }

  public boolean arePinnedMessages () {
    return previewSearchFilter != null && Td.isPinnedFilter(previewSearchFilter);
  }

  public void openPreviewMessage (TGMessage msg) {
    if (arePinnedMessages()) {
      ViewController<?> c = previousStackItem();
      if (c instanceof MessagesController && c.getChatId() == getChatId()) {
        ((MessagesController) c).highlightMessage(msg.toMessageId());
        navigateBack();
        return;
      }
    }
    tdlib().ui().openMessage(this, msg.getChatId(), msg.toMessageId(), null);
  }

  private void resetEditState () {
    editContext = null;
    if (inputView != null) {
      inputView.resetState();
      setInputBlockFlag(FLAG_INPUT_EDITING, false);
    }
    sendButton.forceState(false, false);
  }

  private void editMessage (@NonNull MessageWithProperties m) {
    if (isEditingMessage()) {
      return;
    }

    TdApi.Message msg = m.message;
    TdApi.MessageProperties properties = m.properties;

    needShowEmojiKeyboardAfterHideMessageOptions = false;
    saveDraft();

    if (inSelectMode()) {
      finishSelectMode(-1);
    } else if (inSearchMode()) {
      closeSearchMode(null);
    }

    this.editContext = new MessageInputContext(this, tdlib, msg, properties);
    TdApi.FormattedText text = Td.textOrCaption(msg.content);
    setInEditMode(true, text.text);
    checkAttachedFiles(true);
    sendButton.setIsActive(!StringUtils.isEmpty(text.text) || isEditingCaption());
    updateReplyBarVisibility(true);
    if (inputView != null) {
      TdApi.FormattedText pendingText = tdlib.getPendingFormattedText(msg.chatId, msg.id);
      if (pendingText != null) {
        text = pendingText;
      }
      inputView.setInput(Settings.instance().getNewSetting(Settings.SETTING_FLAG_EDIT_MARKDOWN) ? TD.toMarkdown(text) : TD.toCharSequence(text), true, false);
    }
    Keyboard.show(inputView);
  }

  private void closeEdit (boolean isSaved) {
    if (!isEditingMessage()) {
      return;
    }

    if (inputView != null) {
      TdApi.DraftMessage draftMessage = getDraftMessage();
      inputView.setDraft(draftMessage != null ? draftMessage.inputMessageText : null);
      setInputBlockFlag(FLAG_INPUT_EDITING, false);
    }
    setInEditMode(false, "");
    editContext = null;
    checkAttachedFiles(true);
    if (inputView != null) {
      updateSendButton(inputView.getInput(), true);
    }

    // Intentionally doesn't match the send logic (where there's no animation after send).
    // For the exact match, !isSaved could be passed here instead.
    updateReplyBarVisibility(true);
  }

  private void saveMessage (boolean applyMarkdown) {
    if (!isEditingMessage() || inputView == null) {
      return;
    }

    TdApi.FormattedText newText = inputView.getOutputText(applyMarkdown);
    TdApi.LinkPreviewOptions newOptions = editContext.takeOutputLinkPreviewOptions(true);
    boolean newShowCaptionAboveMedia = editContext.takeOutputShowCaptionAboveMedia();

    switch (editContext.message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
      case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
        if (Td.isEmpty(newText)) {
          return;
        }

        TdApi.MessageText oldMessageText;
        if (editContext.message.content.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
          oldMessageText = new TdApi.MessageText(Td.textOrCaption(editContext.message.content), null, null);
        } else {
          oldMessageText = (TdApi.MessageText) editContext.message.content;
        }
        TdApi.LinkPreviewOptions oldLinkPreviewOptions = oldMessageText.linkPreviewOptions;

        TdApi.InputMessageText newInputMessageText = new TdApi.InputMessageText(newText, newOptions, false);
        if (!Td.equalsTo(newInputMessageText.text, oldMessageText.text) || !Td.equalsTo(newInputMessageText.linkPreviewOptions, oldLinkPreviewOptions)) {
          final int maxLength = tdlib.maxMessageTextLength();
          final int newTextLength = newText != null ? newText.text.codePointCount(0, newText.text.length()) : 0;
          if (newTextLength > maxLength) {
            showBottomHint(Lang.pluralBold(R.string.EditMessageTextTooLong, newTextLength - maxLength), true);
            return;
          }
          TdApi.LinkPreview linkPreview = editContext.takePreloadedOutputLinkPreview();
          tdlib.editMessageText(editContext.message.chatId, editContext.message.id, newInputMessageText, linkPreview);
        }
        break;
      }
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        final MessageInputContext editContext = this.editContext;
        final TdApi.FormattedText oldText = Td.textOrCaption(editContext.message.content);
        final boolean oldShowCaptionAboveMedia = Td.showCaptionAboveMedia(editContext.message.content);
        if (!Td.equalsTo(oldText, newText) || oldShowCaptionAboveMedia != newShowCaptionAboveMedia || editContext.localPickedFile != null) {
          String newString = newText.text.trim();
          final int maxLength = tdlib.maxCaptionLength();
          final int newCaptionLength = newString.codePointCount(0, newString.length());
          if (newCaptionLength > maxLength) {
            showBottomHint(Lang.pluralBold(R.string.EditMessageCaptionTooLong, newCaptionLength - maxLength), true);
            return;
          }
          if (editContext.localPickedFile != null && editContext.localPickedFile.inlineResult != null) {
            final boolean allowAudio = !tdlib.hasReplaceMediaRestriction(editContext.message, RightId.SEND_AUDIO) && tdlib.getRestrictionStatus(chat, RightId.SEND_AUDIO) == null;
            final boolean allowDocs = !tdlib.hasReplaceMediaRestriction(editContext.message, RightId.SEND_DOCS) && tdlib.getRestrictionStatus(chat, RightId.SEND_DOCS) == null;
            final boolean allowVideos = !tdlib.hasReplaceMediaRestriction(editContext.message, RightId.SEND_VIDEOS) && tdlib.getRestrictionStatus(chat, RightId.SEND_VIDEOS) == null;
            final boolean allowGifs = !tdlib.hasReplaceMediaRestriction(editContext.message, RightId.SEND_OTHER_MESSAGES) && tdlib.getRestrictionStatus(chat, RightId.SEND_OTHER_MESSAGES) == null;

            Media.instance().post(() -> {
              // FIXME: hasSpoiler
              TdApi.InputMessageContent content = TD.toInputMessageContent(newText, editContext.localPickedFile.inlineResult, newShowCaptionAboveMedia, false, allowDocs, allowAudio, allowVideos, allowGifs);
              if (content != null) {
                content = tdlib.filegen().createThumbnail(content, isSecretChat());
                TdApi.InputMessageContent finalContent = content;
                UI.post(() -> tdlib.editMessageMedia(editContext.message.chatId, editContext.message.id, finalContent, editContext.localPickedFile));
              } else {
                UI.post(() -> showRestriction(sendButton, Lang.getString(R.string.EditMediaRestricted)));
              }
            });
          } else if (editContext.localPickedFile != null && editContext.localPickedFile.imageGalleryFile != null) {
            editContext.localPickedFile.imageGalleryFile.setCaption(newText);
            Media.instance().post(() -> {
              // FIXME: hasSpoiler
              TdApi.InputMessageContent content = TD.toContent(tdlib, editContext.localPickedFile.imageGalleryFile, false, false, newShowCaptionAboveMedia, false, isSecretChat());
              UI.post(() -> tdlib.editMessageMedia(editContext.message.chatId, editContext.message.id, content, editContext.localPickedFile));
            });
          } else {
            tdlib.editMessageCaption(editContext.message.chatId, editContext.message.id, newText, newShowCaptionAboveMedia);
          }
        }
        break;
      }
      default: {
        Td.assertMessageContent_640c68ad();
        throw Td.unsupported(editContext.message.content);
      }
    }

    closeEdit(true);
  }

  // Share utils

  public void shareMessages (TdApi.Message[] messages, boolean isExplicitSelection) {
    if (messages == null || messages.length == 0) {
      return;
    }
    hideAllKeyboards();
    final ShareController c = new ShareController(context, tdlib);
    c.setArguments(new ShareController.Args(messages).setDisallowReply(isExplicitSelection).setAfter(() -> finishSelectMode(-1)));
    c.show();
    hideCursorsForInputView();
  }

  // Markup utils

  private int lastCmdResource;

  private boolean setCameraVisible (boolean isVisible) {
    int visibility = isVisible ? View.VISIBLE : View.INVISIBLE;
    // TODO
    return false;
  }

  public void updateCommandButton (boolean isVisible) {
    boolean ok = setCameraVisible(!isVisible || !ChatId.isUserChat(getChatId()));
    if (commandButton.setVisible(isVisible) || ok) {
      attachButtons.updatePivot();
    }
  }

  private void updateCommandButton (int resource) {
    if (resource == 0) {
      if (commandButton.setVisible(false)) {
        attachButtons.updatePivot();
      }
      return;
    }
    if (commandButton.setVisible(true)) {
      attachButtons.updatePivot();
    }
    if (lastCmdResource != resource) {
      lastCmdResource = resource;
      commandButton.setImageResource(resource);
    }
  }

  private void onCommandClick () {
    inputView.setInput("/", true, true);
    Keyboard.show(inputView);
  }

  public void showKeyboard () {
    if (isFocused()) {
      Keyboard.show(inputView);
    }
  }

  public void hideAllKeyboards () {
    hideSoftwareKeyboard();
    closeCommandsKeyboard(false);
    closeEmojiKeyboard(true);
  }

  public void hideKeyboard (boolean personal) {
    if (personal && commandsShown) {
      commandsState = false;
    }
    closeCommandsKeyboardImpl(true, false);
  }

  private boolean commandsShown;
  private boolean commandsState;
  private long commandsMessageId;
  private TdApi.ReplyMarkupShowKeyboard commandsKeyboard;
  private ScrollView keyboardWrapper;
  private CommandKeyboardLayout keyboardLayout;

  private void toggleCommandsKeyboard () {
    if (commandsShown) {
      closeCommandsKeyboard(true);
    } else {
      openCommandsKeyboard(true);
    }
  }

  private void closeCommandsKeyboard (boolean byUserEvent) {
    closeCommandsKeyboardImpl(false, byUserEvent);
  }

  private void closeCommandsKeyboardImpl (boolean destroy, boolean byUserEvent) {
    if (commandsShown) {
      if (keyboardWrapper != null) {
        keyboardWrapper.setVisibility(View.GONE);
      }

      // updateButtonsY();

      if (commandsState && isFocused()) {
        keyboardLayout.showKeyboard(inputView);
      }

      commandsShown = false;
      if (destroy) {
        updateCommandButton(R.drawable.deproko_baseline_bots_command_26);
      } else {
        updateCommandButton(R.drawable.deproko_baseline_bots_keyboard_26);
        if (byUserEvent) {
          Settings.instance().onRequestKeyboardClose(tdlib.id(), chat.id, chat.replyMarkupMessageId, true);
        }
      }
    } else if (destroy) {
      updateCommandButton(R.drawable.deproko_baseline_bots_command_26);
    }
  }

  private void forceCloseCommandsKeyboard () {
    if (commandsShown) {
      if (keyboardWrapper != null) {
        keyboardWrapper.setVisibility(View.GONE);
      }
      commandsShown = false;
      updateCommandButton(R.drawable.deproko_baseline_bots_keyboard_26);
    }
  }

  private void openCommandsKeyboard (boolean byUserEvent) {
    openCommandsKeyboard(commandsMessageId, commandsKeyboard, true, byUserEvent);
  }

  private void openCommandsKeyboard (long messageId, TdApi.ReplyMarkupShowKeyboard keyboard, boolean force, boolean byUserEvent) {
    if (keyboardLayout == null) {
      keyboardWrapper = new ScrollView(context());
      ViewSupport.setThemedBackground(keyboardWrapper, ColorId.chatKeyboard, this);

      keyboardLayout = new CommandKeyboardLayout(context());
      keyboardLayout.setThemeProvider(this);
      keyboardLayout.setCallback(this);

      keyboardWrapper.addView(keyboardLayout);
      keyboardWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardLayout.getSize()));

      bottomWrap.addView(keyboardWrapper);
      contentView.getViewTreeObserver().addOnPreDrawListener(keyboardLayout);
      keyboardWrapper.setVisibility(View.GONE);
    }

    if (commandsMessageId != messageId) {
      commandsMessageId = messageId;
      commandsKeyboard = keyboard;
      keyboardLayout.setKeyboard(keyboard);
    }

    if (byUserEvent) {
      Settings.instance().onRequestKeyboardClose(tdlib.id(), getChatId(), messageId, false);
    }

    if (!force && (!keyboard.isPersonal || Settings.instance().shouldKeepKeyboardClosed(tdlib.id(), getChatId(), messageId))) {
      keyboardWrapper.setVisibility(View.GONE);
      updateCommandButton(R.drawable.deproko_baseline_bots_keyboard_26);
      // updateButtonsY();
      return;
    }

    if (emojiShown) {
      commandsState = emojiState;
      forceCloseEmojiKeyboard();
    } else {
      commandsState = getKeyboardState();
    }

    keyboardWrapper.setVisibility(View.VISIBLE);
    // updateButtonsY();
    commandsShown = true;

    if (commandsState) {
      updateCommandButton(R.drawable.baseline_keyboard_24);
      keyboardLayout.hideKeyboard(inputView);
    } else {
      updateCommandButton(R.drawable.baseline_direction_arrow_down_24);
    }
  }

  private float prevButtonsY;

  private void updateButtonsY () {
    float y = bottomWrap.getTop() + (inputView != null ? inputView.getBottom() : Screen.dp(49f)) - Screen.dp(49f) - getKeyboardOffset();

    sendButton.setTranslationY(y);
    emojiButton.setTranslationY(y);
    attachButtons.setTranslationY(y);
    messageSenderButton.setTranslationY(y);

    if (prevButtonsY != y) {
      prevButtonsY = y;
      onMessagesFrameChanged();
      updateRecordLayout();
    }
  }

  private long scheduledKeyboardMessageId;
  private TdApi.ReplyMarkupShowKeyboard scheduledKeyboard;

  private void clearScheduledKeyboard () {
    scheduledKeyboardMessageId = 0;
    scheduledKeyboard = null;
  }

  public void showKeyboard (long messageId, TdApi.ReplyMarkupShowKeyboard markup) {
    if (false && !isFocused() && commandsMessageId != messageId) { // Смотрится как говно
      scheduledKeyboardMessageId = messageId;
      scheduledKeyboard = markup;
    } else {
      openCommandsKeyboard(messageId, markup, false, false);
    }
  }

  @Override
  public void onCommandPressed (String command) {
    pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) ->
      sendText(command, false, true, false, sendOptions)
    );
  }

  @Override
  public void onRequestPoll (boolean oneTime, boolean forceQuiz, boolean forceRegular) {
    if (chat != null && tdlib.canSendPolls(chat.id)) {
      CreatePollController c = new CreatePollController(context, tdlib);
      c.setArguments(new CreatePollController.Args(chat.id, messageThread, this, forceQuiz, forceRegular));
      navigateTo(c);
    }
  }

  @Override
  public boolean onSendPoll (CreatePollController context, long chatId, long messageThreadId, TdApi.InputMessagePoll poll, TdApi.MessageSendOptions sendOptions, RunnableData<TdApi.Message> after) {
    if (getChatId() == chatId && getMessageThreadId() == messageThreadId) {
      send(poll, true, sendOptions, after);
      return true;
    }
    return false;
  }

  @Override
  public boolean areScheduledOnly (CreatePollController context) {
    return areScheduledOnly();
  }

  @Override
  public TdApi.ChatList provideChatList (CreatePollController context) {
    return openedFromChatList;
  }

  @Nullable
  public TdApi.ChatList chatList () {
    return openedFromChatList;
  }

  @Override
  public void onRequestContact (final boolean oneTime) {
    if (chat != null && ChatId.isPrivate(getChatId())) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme());
      builder.setTitle(Lang.getString(R.string.ShareYourPhoneNumberTitle));
      builder.setMessage(Lang.getString(R.string.ShareYourPhoneNumberDesc, tdlib.chatTitle(chat)));
      builder.setPositiveButton(Lang.getOK(), (dialog, which) -> {
        shareMyContact(true);
        if (oneTime) {
          onDestroyCommandKeyboard();
        }
      });
      builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
      showAlert(builder);
    }
  }

  @Override
  public void onRequestLocation (final boolean oneTime) {
    if (chat != null && ChatId.isPrivate(chat.id)) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme());
      builder.setTitle(Lang.getString(R.string.ShareYourLocation));
      builder.setMessage(Lang.getString(R.string.ShareYouLocationInfo));
      builder.setPositiveButton(Lang.getOK(), (dialog, which) -> shareCurrentLocation(oneTime));
      builder.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
      showAlert(builder);
    }
  }

  private GoogleApiClient googleClient;

  private void closeGoogleClient () {
    if (googleClient != null) {
      try {
        googleClient.disconnect();
      } catch (Throwable t) {
        Log.w("GoogleApiClient throws", t);
      }
      googleClient = null;
    }
  }

  private boolean currentShareLocationDestroyKeyboard;
  private long currentShareLocationChatId;

  @SuppressWarnings("deprecation")
  private void shareCurrentLocation (final boolean destroyKeyboard) {
    currentShareLocationDestroyKeyboard = destroyKeyboard;
    currentShareLocationChatId = getChatId();

    if (context().checkLocationPermissions(false) != PackageManager.PERMISSION_GRANTED) {
      context().requestLocationPermission(false, false, null);
      return;
    }

    try {
      if (googleClient == null) {
        GoogleApiClient.Builder b = new GoogleApiClient.Builder(context());
        b.addApi(LocationServices.API);
        googleClient = b.build();
        googleClient.connect();
      }

      final LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
        .addLocationRequest(LocationRequest.create())
        .setAlwaysShow(true);
      final LocationSettingsRequest request = builder.build();
      final PendingResult<LocationSettingsResult> result =
        LocationServices.SettingsApi.checkLocationSettings(googleClient, request);

      result.setResultCallback(result1 -> {
        final Status status = result1.getStatus();
        //final LocationSettingsStates state = result.getLocationSettingsStates();
        switch (status.getStatusCode()) {
          case LocationSettingsStatusCodes.RESOLUTION_REQUIRED: {
            try {
              status.startResolutionForResult(context(), Intents.ACTIVITY_RESULT_RESOLUTION);
            } catch (Throwable t) {
              getCustomCurrentLocation(destroyKeyboard, true, false);
            }
            break;
          }
          case LocationSettingsStatusCodes.SUCCESS: {
            getCurrentLocation(destroyKeyboard, googleClient);
            break;
          }
          case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
          default: {
            getCurrentLocation(destroyKeyboard, googleClient);
            break;
          }
        }
      });
    } catch (Throwable t) {
      shareCurrentLocation(destroyKeyboard, null);
    }
  }

  public void sendPickedLocation (TdApi.Location location, int heading, final TdApi.MessageSendOptions sendOptions) {
    if (getChatId() == currentShareLocationChatId) {
      send(new TdApi.InputMessageLocation(location, 0, heading, 0), true, sendOptions, null);
      if (currentShareLocationDestroyKeyboard) {
        onDestroyCommandKeyboard();
      }
    }
  }

  private void shareCurrentLocation (final boolean destroyKeyboard, Location location) {
    if (getChatId() == currentShareLocationChatId) {
      send(new TdApi.InputMessageLocation(new TdApi.Location(location.getLatitude(), location.getLongitude(), location.getAccuracy()), 0, U.getHeading(location), 0), true, Td.newSendOptions(), null);
      if (destroyKeyboard) {
        onDestroyCommandKeyboard();
      }
    }
  }

  private void getCustomCurrentLocation (final boolean destroyKeyboard, boolean byError, boolean byTimeout) {
    if (getChatId() == currentShareLocationChatId) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context(), Theme.dialogTheme());
      builder.setTitle(Lang.getString(R.string.AppName));
      builder.setMessage(Lang.getString(R.string.DetectLocationError));
      builder.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
      builder.setNegativeButton(Lang.getString(R.string.ShareYouLocationUnableManually), (dialog, which) -> pickCustomCurrentLocation(destroyKeyboard));
      showAlert(builder);
    }
  }

  private void pickCustomCurrentLocation (final boolean destroyKeyboard) {
    MediaLayout mediaLayout = new MediaLayout(this);
    mediaLayout.init(MediaLayout.MODE_LOCATION, this);
    mediaLayout.show();
    // TODO run MediaLayout with only map available
  }

  private void getCurrentLocation (final boolean destroyKeyboard, @Nullable GoogleApiClient client) {
    if (client != null && USE_GOOGLE_LOCATION) {
      getCurrentLocationViaGoogleApiClient(destroyKeyboard, client);
    } else {
      shareCurrentLocationViaManager(destroyKeyboard);
    }
  }

  private static final long LOCATION_MAX_WAIT_TIME = 3000L;
  private static final boolean USE_GOOGLE_LOCATION = true;
  private static final boolean USE_LAST_KNOWN_LOCATION = false;

  @SuppressWarnings("deprecation")
  private void getCurrentLocationViaGoogleApiClient (final boolean destroyKeyboard, final @NonNull GoogleApiClient client) {
    final CancellableRunnable[] timeout = new CancellableRunnable[1];
    final boolean[] sent = new boolean[1];
    final LocationListener listener = location -> {
      timeout[0].cancel();
      if (!sent[0]) {
        sent[0] = true;
        shareCurrentLocation(destroyKeyboard, location);
      }
    };
    timeout[0] = new CancellableRunnable() {
      @Override
      public void act () {
        if (!sent[0]) {
          sent[0] = true;
          try {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleClient, listener);
          } catch (Throwable t) {
            Log.w("Error removeLocationUpdates", t);
          }
          Location location = null;
          try {
            location = LocationServices.FusedLocationApi.getLastLocation(client);
          } catch (SecurityException ignored) { }
            catch (Throwable t) {
            Log.w("getLastLocation error", t);
          }
          if (location == null && USE_LAST_KNOWN_LOCATION) {
            location = U.getLastKnownLocation(context(), false);
          }
          if (location != null) {
            shareCurrentLocation(destroyKeyboard, location);
          } else {
            getCustomCurrentLocation(destroyKeyboard, false, true);
          }
        }
      }
    };
    UI.post(timeout[0], LOCATION_MAX_WAIT_TIME);
    try {
      LocationRequest request = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setExpirationDuration(LOCATION_MAX_WAIT_TIME).setNumUpdates(1).setMaxWaitTime(5000L);
      LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, request, listener);
    } catch (SecurityException ignored) {
      sent[0] = true;
      shareCurrentLocationViaManager(destroyKeyboard);
    } catch (Throwable t) {
      Log.w("requestLocationUpdates error", t);
      sent[0] = true;
      shareCurrentLocationViaManager(destroyKeyboard);
    }
  }

  private void shareCurrentLocationViaManager (final boolean destroyKeyboard) {
    try {
      final LocationManager manager = (LocationManager) context().getSystemService(Context.LOCATION_SERVICE);

      final CancellableRunnable[] timeout = new CancellableRunnable[1];
      final boolean[] sent = new boolean[1];
      final android.location.LocationListener listener = new android.location.LocationListener() {
        @Override
        public void onLocationChanged (Location location) {
          timeout[0].cancel();
          try {
            manager.removeUpdates(this);
          } catch (SecurityException ignored) { }
            catch (Throwable t) {
            Log.w("removeUpdates failed. Probable resource leak", t);
          }
          if (!sent[0]) {
            sent[0] = true;
            shareCurrentLocation(destroyKeyboard, location);
          }
        }

        @Override
        public void onStatusChanged (String provider, int status, Bundle extras) { }

        @Override
        public void onProviderEnabled (String provider) { }

        @Override
        public void onProviderDisabled (String provider) { }
      };
      timeout[0] = new CancellableRunnable() {
        @Override
        public void act () {
          if (!sent[0]) {
            sent[0] = true;
            Location location = USE_LAST_KNOWN_LOCATION ? U.getLastKnownLocation(context(), true) : null;
            if (location != null) {
              shareCurrentLocation(destroyKeyboard, location);
            } else {
              getCustomCurrentLocation(destroyKeyboard, false, true);
            }
          }
        }
      };
      UI.post(timeout[0], LOCATION_MAX_WAIT_TIME);
      manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, listener);
      manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, listener);
    } catch (SecurityException ignored) {
      getCustomCurrentLocation(destroyKeyboard, true, false);
    } catch (Throwable t) {
      Log.w("Error occured", t);
      getCustomCurrentLocation(destroyKeyboard, true, false);
    }
  }

  @Override
  public void onDestroyCommandKeyboard () {
    clearScheduledKeyboard();
    closeCommandsKeyboardImpl(true, false);
    updateCommandButton(R.drawable.deproko_baseline_bots_command_26);
    if (commandsMessageId != 0) {
      botHelper.onDestroyKeyboard(commandsMessageId);
    }
    setCustomBotPlaceholder(null);
  }

  private String customBotPlaceholder;

  public void setCustomBotPlaceholder (@Nullable String customPlaceholder) {
    if (!StringUtils.equalsOrBothEmpty(this.customBotPlaceholder, customPlaceholder)) {
      this.customBotPlaceholder = customPlaceholder;
      updateInputHint();
    }
  }

  private String customCaptionPlaceholder;

  public void setCustomCaptionPlaceholder (@Nullable String customPlaceholder) {
    if (!StringUtils.equalsOrBothEmpty(this.customCaptionPlaceholder, customPlaceholder)) {
      this.customCaptionPlaceholder = customPlaceholder;
      updateInputHint();
    }
  }

  private String getCustomInputPlaceholder () {
    if (!StringUtils.isEmpty(customBotPlaceholder)) {
      return customBotPlaceholder;
    } else if (!StringUtils.isEmpty(customCaptionPlaceholder)) {
      return customCaptionPlaceholder;
    }
    return null;
  }

  @Override
  protected void onTranslationChanged (float newTranslationX) {
    context().reactionsOverlayManager().setControllerTranslationX((int) newTranslationX);
  }

  @Override
  public void onResizeCommandKeyboard (int size) {
    // updateButtonsY();
  }

  // Silent mode

  public int getHorizontalInputPadding () {
    return attachButtons.getVisibleChildrenWidth() + (canSelectSender() ? Screen.dp(47) : 0);
  }

  private void updateSilentButton (boolean visible) {
    if (Config.NEED_SILENT_BROADCAST) {
      commandButton.setTranslationX(visible ? 0 : silentButton.getLayoutParams().width);
      if (silentButton.setVisible(visible)) {
        attachButtons.updatePivot();
      }
      if (visible) {
        silentButton.forceState(tdlib.chatDefaultDisableNotifications(getChatId()));
      }
    }
  }

  public boolean obtainSilentMode () {
    return tdlib.chatDefaultDisableNotifications(getChatId());
  }

  // pinned messages

  private void pinUnpinMessage (TGMessage m, boolean pin) {
    long chatId = m.getMessage().chatId;
    if (chatId == 0) {
      return;
    }
    if (!pin) {
      m.iterate(message -> {
        if (message.isPinned != pin) {
          TdApi.Function<?> function = pin ?
            new TdApi.PinChatMessage(getChatId(), message.id, false, false) :
            new TdApi.UnpinChatMessage(getChatId(), message.id);
          tdlib.client().send(function, tdlib.okHandler());
        }
      }, false);
      return;
    }
    if (tdlib.isSelfChat(getChatId())) {
      tdlib.client().send(new TdApi.PinChatMessage(chatId, m.getSmallestId(), false, false), tdlib.okHandler());
      return;
    }
    int hintRes;
    ListItem item;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        hintRes = R.string.PinMessageInChat;
        item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_notifyMembers, 0, Lang.getStringBold(R.string.PinMessageOther, tdlib.chatTitle(chatId, false, true)), true);
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        boolean isChannel = tdlib.isChannel(getChatId());
        hintRes = isChannel ? R.string.PinMessageInThisChannel : R.string.PinMessageInThisGroup;
        item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_notifyMembers, 0, isChannel ? R.string.PinNotify2 : R.string.PinNotify, true);
        break;
      }
      default:
        throw new RuntimeException();
    }
    showSettings(new SettingsWrapBuilder(R.id.btn_messagePin)
      .addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, hintRes)
      ).setIntDelegate((id, result) -> {
        boolean checkbox = result.get(R.id.btn_notifyMembers) != 0;
        boolean isUserChat = ChatId.isUserChat(chatId);
        boolean disableNotification = !isUserChat && !checkbox;
        boolean onlyForSelf = isUserChat && !checkbox;
        tdlib.client().send(new TdApi.PinChatMessage(chatId, m.getSmallestId(), disableNotification, onlyForSelf), tdlib.okHandler());
      })
    .setRawItems(new ListItem[] {item}).setSaveStr(R.string.Pin));
  }

  public void showHidePinnedMessage (boolean show, @Nullable TdApi.Message message) {
    if (show) {
      pinnedMessagesBar.setCollapseButtonVisible(false);
      pinnedMessagesBar.setContextChatId(getChatId() != getHeaderChatId() ? getHeaderChatId() : 0);
      pinnedMessagesBar.setMessage(tdlib, message);
    }
    topBar.setItemVisible(pinnedMessagesItem, show, isFocused());
  }

  public void showHidePinnedMessages (boolean show, MessageListManager messageList) {
    if (arePinnedMessages()) {
      if (!show)
        navigateBack();
      return;
    }
    if (show) {
      pinnedMessagesBar.setCollapseButtonVisible(true);
      pinnedMessagesBar.setContextChatId(getChatId() != getHeaderChatId() ? getHeaderChatId() : 0);
      pinnedMessagesBar.setMessageList(messageList);
    }
    topBar.setItemVisible(pinnedMessagesItem, show, isFocused());
  }

  private void dismissPinnedMessage () {
    if (!canPinAnyMessage(false)) { // Just hide locally.
      manager.dismissPinnedMessage();
      return;
    }
    int pinnedCount = manager.getPinnedMessageCount();
    if (pinnedCount == ListManager.COUNT_UNKNOWN) {
      return;
    }
    showOptions(new Options.Builder()
      .info(pinnedCount > 1 ? Lang.pluralBold(R.string.UnpinXMessages, pinnedCount) : null)
      .item(canPinAnyMessage(false) ? new OptionItem.Builder()
        .id(R.id.btn_unpinMessage)
        .name(Lang.getString(pinnedCount == 1 ? R.string.UnpinMessage : R.string.UnpinMessagesConfirm))
        .color(OptionColor.RED)
        .icon(R.drawable.deproko_baseline_pin_undo_24)
        .build() : null
      )
      .item(!isSelfChat() ? new OptionItem.Builder()
        .id(R.id.btn_dismissForSelf)
        .name(R.string.HideForYourself)
        .icon(R.drawable.baseline_close_24)
        .build() : null)
      .cancelItem()
      .build(),
      (itemView, id) -> {
        if (id == R.id.btn_unpinMessage) {
          tdlib.client().send(new TdApi.UnpinAllChatMessages(getChatId()), tdlib.okHandler());
        } else if (id == R.id.btn_dismissForSelf) {
          manager.dismissPinnedMessage();
        }
        return true;
      }
    );
  }

  public void onMessageChanged (long chatId, long messageId, TdApi.MessageContent content) {
    if (isEditingMessage() && editContext.checkMessage(chatId, messageId)) {
      if (!editContext.messageProperties.canBeEdited) {
        closeEdit(false);
      } else {
        TdApi.MessageContent oldContent = editContext.message.content;
        editContext.message.content = content;
        updateReplyBarVisibility(true);
        // This is required because we work with a reference from TGMessage, not a copy.
        editContext.message.content = oldContent;
      }
    }
  }

  public void onInlineTranslationChanged (long chatId, long messageId, TdApi.FormattedText text) {

  }

  public void onMessagesDeleted (long chatId, long[] messageIds) {
    if (isEditingMessage() && editContext.getExistingMessage().chatId == chatId && ArrayUtils.indexOf(messageIds, editContext.getExistingMessage().id) != -1) {
      closeEdit(false);
    }
  }

  @Override
  public void onMessageThreadReplyCountChanged (long chatId, long messageThreadId, int replyCount) {
    if (messageThread != null && messageThread.belongsTo(chatId, messageThreadId)) {
      updateMessageThreadSubtitle();
    }
  }

  @Override
  public void onMessageThreadReadInbox (long chatId, long messageThreadId, long lastReadInboxMessageId, int remainingUnreadCount) {
    if (messageThread != null && messageThread.belongsTo(chatId, messageThreadId)) {
      updateCounters(true);
    }
  }

  @Override
  public void onMessageThreadDeleted (long chatId, long messageThreadId) {
    if (messageThread != null && messageThread.belongsTo(chatId, messageThreadId)) {
      forceFastAnimationOnce();
      navigateBack();
    }
  }

  // Live location

  private View liveLocationView;
  private CollapseListView.Item liveLocationItem;
  private LiveLocationHelper liveLocation;

  @Override
  public boolean onBeforeVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate) {
    return isFocused();
  }

  @Override
  public void onAfterVisibilityStateChanged (LiveLocationHelper helper, boolean isVisible, boolean willAnimate) {

  }

  @Override
  public void onApplyVisibility (LiveLocationHelper helper, boolean isVisible, float visibilityFactor, boolean finished) {
    topBar.forceItemVisibility(liveLocationItem, isVisible, visibilityFactor);
  }

  // Action bar

  private void dismissActionBar () {
    tdlib.client().send(new TdApi.RemoveChatActionBar(getChatId()), tdlib.okHandler());
  }

  private boolean needActionBar () {
    return !(inPreviewMode || isInForceTouchMode() || areScheduledOnly() || messageThread != null);
  }

  private TopBarView.Item newAddContactItem (long chatId) {
    return new TopBarView.Item(R.id.btn_addContact, R.string.AddContact, true, R.drawable.baseline_person_add_24, v -> {
      tdlib.ui().addContact(this, tdlib.chatUser(chatId));
    });
  }

  private TopBarView.Item newUnarchiveItem (long chatId) {
    return new TopBarView.Item(R.id.btn_unarchiveChat, R.string.UnarchiveUnmute, true, R.drawable.baseline_unarchive_24, v -> {
      tdlib.client().send(new TdApi.AddChatToList(chatId, new TdApi.ChatListMain()), tdlib.okHandler());
      TdApi.ChatNotificationSettings settings = tdlib.chatSettings(chatId);
      if (settings != null) {
        TdApi.ChatNotificationSettings newSettings = new TdApi.ChatNotificationSettings(
          true, 0,
          settings.useDefaultSound, settings.soundId,
          settings.useDefaultShowPreview, settings.showPreview,
          settings.useDefaultMuteStories, settings.muteStories,
          settings.useDefaultStorySound, settings.storySoundId,
          settings.useDefaultShowStorySender, settings.showStorySender,
          settings.useDefaultDisablePinnedMessageNotifications, settings.disablePinnedMessageNotifications,
          settings.useDefaultDisableMentionNotifications, settings.disableMentionNotifications
        );
        tdlib.client().send(new TdApi.SetChatNotificationSettings(chatId, newSettings), tdlib.okHandler());
      }
    });
  }

  private TopBarView.Item newReportItem (long chatId, boolean isBlock) {
    return new TopBarView.Item(R.id.btn_reportChat, isBlock ? R.string.BlockContact : R.string.ReportSpam, true, isBlock ? R.drawable.baseline_block_24 : R.drawable.baseline_report_24, v -> {
      showSettings(new SettingsWrapBuilder(R.id.btn_reportSpam)
        .setHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, Lang.getStringBold(R.string.ReportChatSpam, chat.title), false))
        .setRawItems(getChatUserId() != 0 ? new ListItem[] {
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_reportSpam, 0, R.string.ReportSpam, true),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_removeChatFromList, 0, R.string.DeleteChat, true),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_blockSender, 0, R.string.BlockUser, true),
        } : new ListItem[] {
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_reportSpam, 0, R.string.ReportSpam, true),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_removeChatFromList, 0, R.string.DeleteChat, true)
        })
        .setIntDelegate((id, result) -> {
          if (id != R.id.btn_reportSpam || chatId != getChatId() || !isFocused()) {
            return;
          }

          boolean reportSpam = result.get(R.id.btn_reportSpam) != 0;
          boolean deleteChat = result.get(R.id.btn_removeChatFromList) != 0;
          boolean blockSender = result.get(R.id.btn_blockSender) != 0;

          if (!reportSpam && !deleteChat && !blockSender) {
            return;
          }

          if (blockSender) {
            tdlib.blockSender(tdlib.sender(chat.id), new TdApi.BlockListMain(), tdlib.okHandler());
          }

          if (reportSpam) {
            tdlib.client().send(new TdApi.ReportChat(getChatId(), null, null, null), tdlib.okHandler());
          }
          if (deleteChat) {
            deleteAndLeave();
          }
        })
        .setSaveStr(R.string.Done)
        .setSaveColorId(ColorId.textNegative));
    }).setIsNegative();
  }

  private void checkJoinRequests (TdApi.ChatJoinRequestsInfo info) {
    if (!needActionBar())
      return;

    if (info == null || info.totalCount == 0 || (chat != null && tdlib.settings().isRequestsDismissed(chat.id, info))) {
      topBar.setItemVisible(requestsItem, false, isFocused());
    } else {
      if (info.totalCount > 0) {
        tdlib.settings().restoreRequests(chat.id, true);
      }

      requestsView.setInfo(info, isFocused());
      topBar.setItemVisible(requestsItem, true, isFocused());
    }
  }

  private void checkActionBar () {
    if (!needActionBar())
      return;

    List<TopBarView.Item> items = new ArrayList<>();

    final long chatId = getChatId();
    TdApi.Chat chat = tdlib.chat(chatId);
    TdApi.ChatActionBar actionBar = chat != null ? chat.actionBar : null;
    if (actionBar != null) {
      switch (actionBar.getConstructor()) {
        case TdApi.ChatActionBarAddContact.CONSTRUCTOR: {
          items.add(newAddContactItem(chatId));
          break;
        }

        case TdApi.ChatActionBarReportSpam.CONSTRUCTOR: {
          TdApi.ChatActionBarReportSpam reportSpam = (TdApi.ChatActionBarReportSpam) actionBar;
          /*
           TODO: Add anti-scam notice for custom emojis
           var customEmoji = tdlib().cache().user(tdlib.chatUserId(getChatId()));
           android.util.Log.e("customEmoji", String.format("customEmoji: %s", customEmoji));
           if (customEmoji != null && customEmoji.emojiStatus != null) {
             items.add(new TopBarView.Item("Have a custom emoji", true));
           }
          */
          items.add(newReportItem(chatId, false));
          if (reportSpam.canUnarchive) {
            items.add(newUnarchiveItem(chatId));
          }
          break;
        }

        case TdApi.ChatActionBarReportAddBlock.CONSTRUCTOR: {
          TdApi.ChatActionBarReportAddBlock reportAddBlock = (TdApi.ChatActionBarReportAddBlock) actionBar;
          /*
           TODO: Add anti-scam notice for custom emojis
           var customEmoji = tdlib().cache().user(tdlib.chatUserId(getChatId()));
           android.util.Log.e("customEmoji", String.format("customEmoji: %s", customEmoji));
           if (customEmoji != null && customEmoji.emojiStatus != null) {
             items.add(new TopBarView.Item("Have a custom emoji", true));
           }
          */
          items.add(newReportItem(chatId, true));
          items.add(newAddContactItem(chatId));
          if (reportAddBlock.canUnarchive) {
            items.add(newUnarchiveItem(chatId));
          }
          break;
        }

        case TdApi.ChatActionBarInviteMembers.CONSTRUCTOR: {
          items.add(new TopBarView.Item(R.id.btn_invite, R.string.AddMember, v -> {
            ContactsController c = new ContactsController(context, tdlib);
            c.initWithMode(ContactsController.MODE_ADD_MEMBER);
            c.setAllowBots(true);
            c.setArguments(new ContactsController.Args(new SenderPickerDelegate() {
              @Override
              public boolean onSenderPick (ContactsController context, View view, TdApi.MessageSender senderId) {
                if (tdlib.isSelfSender(senderId)) {
                  return false;
                }

                tdlib.setChatMemberStatus(chat.id, senderId, new TdApi.ChatMemberStatusMember(), null, (ok, error) -> {
                  runOnUiThreadOptional(() -> {
                    if (!ok && error != null) {
                      context.context()
                        .tooltipManager()
                        .builder(view)
                        .show(context, tdlib, R.drawable.baseline_error_24, TD.toErrorString(error));
                    } else {
                      context.navigateBack();
                    }
                  });
                });

                return true;
              }
            }));
            c.setChatTitle(R.string.AddMember, chat.title);
            navigateTo(c);
          }));
          break;
        }

        case TdApi.ChatActionBarSharePhoneNumber.CONSTRUCTOR: {
          items.add(new TopBarView.Item(R.id.btn_shareMyContact, R.string.SharePhoneNumber, v -> {
            TdApi.User user = tdlib.myUser();
            if (user != null) {
              showOptions(TD.getUserName(user) + ", " + Strings.formatPhone(user.phoneNumber), new int[] {R.id.btn_shareMyContact, R.id.btn_cancel}, new String[] {Lang.getString(R.string.SharePhoneNumberAction), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, new int[] {R.drawable.baseline_contact_phone_24, R.drawable.baseline_cancel_24}, (itemView, id1) -> {
                if (id1 == R.id.btn_shareMyContact) {
                  tdlib.client().send(new TdApi.SharePhoneNumber(tdlib.chatUserId(chatId)), tdlib.okHandler());
                }
                return true;
              });
            }
          }));
          break;
        }
        case TdApi.ChatActionBarJoinRequest.CONSTRUCTOR: {
          TdApi.ChatActionBarJoinRequest joinRequest = (TdApi.ChatActionBarJoinRequest) actionBar;
          int noticeResId = joinRequest.isChannel ? R.string.JoinRequestChannelAdminNotice : R.string.JoinRequestGroupAdminNotice;
          items.add(new TopBarView.Item(Strings.replaceBoldTokens(Lang.getString(noticeResId, tdlib.cache().userFirstName(tdlib.chatUserId(getChatId())), joinRequest.title)), true));
          break;
        }
        default: {
          Td.assertChatActionBar_eedc82ed();
          throw Td.unsupported(actionBar);
        }
      }
    }
    if (ChatId.isSecret(chatId)) {
      TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chatId);
      if (secretChat != null && secretChat.state.getConstructor() == TdApi.SecretChatStateClosed.CONSTRUCTOR) {
        items.add(new TopBarView.Item(R.id.btn_delete, R.string.DeleteAndLeave, v -> {
          if (manager.isTotallyEmpty()) {
            deleteAndLeave();
          } else {
            tdlib.ui().showDeleteChatConfirm(this, getChatId());
          }
        }).setNoDismiss().setIsNegative());
      }
    }
    if (!items.isEmpty()) {
      actionView.setItems(items.toArray(new TopBarView.Item[0]));
    }
    topBar.setItemVisible(actionItem, !items.isEmpty(), isFocused());
  }

  // Callback shit

  private InlineResultButton currentSwitchPmButton;

  private void processSwitchPm (InlineResultButton button) {
    currentSwitchPmButton = button;
    tdlib.sendBotStartMessage(button.getUserId(), getChatId(), button.botStartParameter());
  }

  public void switchBackToSourcePmIfNeeded (TdApi.InlineKeyboardButtonTypeSwitchInline button) {
    if (currentSwitchPmButton != null) {
      TdApi.Chat chat = tdlib.chat(currentSwitchPmButton.getSourceChatId());
      TdApi.User user = tdlib.cache().user(currentSwitchPmButton.getUserId());
      if (chat != null && user != null) {
        tdlib.ui().openChat(this, chat, new TdlibUi.ChatOpenParameters().shareItem(new TGSwitchInline(Td.primaryUsername(user), button.query)));
      }
    }
  }

  private void clearSwitchPmButton () {
    currentSwitchPmButton = null;
  }

  public void checkSwitchPm (TdApi.Message msg) {
    if (currentSwitchPmButton != null && msg.replyMarkup != null && msg.replyMarkup.getConstructor() == TdApi.ReplyMarkupInlineKeyboard.CONSTRUCTOR) {
      TdApi.ReplyMarkupInlineKeyboard keyboard = (TdApi.ReplyMarkupInlineKeyboard) msg.replyMarkup;
      for (TdApi.InlineKeyboardButton[] row : keyboard.rows) {
        for (TdApi.InlineKeyboardButton button : row) {
          if (button.type.getConstructor() == TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR) {
            switchBackToSourcePmIfNeeded((TdApi.InlineKeyboardButtonTypeSwitchInline) button.type);
            break;
          }
        }
      }
    }
  }

  public void onSwitchPm (InlineResultButton button) {
    tdlib.sendBotStartMessage(button.getUserId(), getChatId(), button.botStartParameter());
    inputView.setText("");
  }

  public void openGame (long ownerUserId, TdApi.Game game, final String url, TdApi.Message message) {
    TdApi.User user = tdlib.cache().user(ownerUserId);
    GameController controller = new GameController(context, tdlib);
    controller.setArguments(new GameController.Args(user != null ? user.id : 0, game, user != null ? "@" + Td.primaryUsername(user) : "Game", url, message, this));
    /*PopupLayout popupLayout = new PopupLayout(getContext());
    popupLayout.init(true);
    popupLayout.showSimplePopupView(controller.getWrap(), Screen.currentHeight());*/
    navigateTo(controller);
  }

  public void switchInline (long viaBotUserId, final TdApi.InlineKeyboardButtonTypeSwitchInline switchInline) {
    if (chat == null) {
      return;
    }
    final TdApi.User user = viaBotUserId != 0 ? tdlib.cache().user(viaBotUserId) : tdlib.chatUser(chat);
    if (user == null || !Td.hasUsername(user)) {
      return;
    }

    final String username = Td.primaryUsername(user);

    if (switchInline.targetChat.getConstructor() == TdApi.TargetChatCurrent.CONSTRUCTOR && canWriteMessages() && hasSendMessagePermission(RightId.SEND_OTHER_MESSAGES)) {
      if (inputView != null) {
        inputView.setInput("@" + username + " " + switchInline.query, true, true);
      }
      return;
    }

    // TODO support TargetChatInternalLink

    tdlib.ui().switchInline(this, username, switchInline.query, false);
  }

  // Toast, Report Spam, Share my contact info

  public void showCallbackToast (CharSequence text) {
    toastAlertView.setText(text, null, topBar.isVisible(toastAlertItem));
    cancelScheduledToastHide();
    scheduledToastHide = new CancellableRunnable() {
      @Override
      public void act () {
        topBar.setItemVisible(toastAlertItem, false, true);
      }
    };
    topBar.setItemVisible(toastAlertItem, true, true);
    runOnUiThread(scheduledToastHide, 3000L);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_SCROLL_TO_BOTTOM: {
        scrollToBottomButtonWrap.setAlpha(MathUtils.clamp(factor));
        checkScrollButtonOffsets();
        break;
      }
      case ANIMATOR_MENTION_BUTTON: {
        setMentionButtonFactor(factor);
        checkScrollButtonOffsets();
        break;
      }
      /*case ANIMATOR_BOTTOM_HINT: {
        setBottomHintFactor(factor);
        break;
      }*/
      case ANIMATOR_SEND: {
        setSendFactor(factor);
        break;
      }
      case ANIMATOR_SEARCH_PROGRESS: {
        setSearchInProgress(factor);
        break;
      }
      case ANIMATOR_BOTTOM_BUTTON: {
        updateBottomBarStyle();
        break;
      }
      case ANIMATOR_REACTION_BUTTON: {
        setReactionButtonFactor(factor);
        checkScrollButtonOffsets();
        break;
      }
      case ANIMATOR_SEARCH_BY_USER: {
        setSearchByUserTransformFactor(factor);
        applySearchByUserTransformFactor(factor);
        break;
      }
      case ANIMATOR_SEARCH_NAVIGATION: {
        setBadgesButtonsTranslationX(searchControlsFactor, factor);
        break;
      }
    }
  }

  private CancellableRunnable scheduledToastHide;

  private void cancelScheduledToastHide () {
    if (scheduledToastHide != null) {
      scheduledToastHide.cancel();
      removeCallbacks(scheduledToastHide);
      scheduledToastHide = null;
    }
  }

  private void forceHideToast () {
    cancelScheduledToastHide();
    topBar.setItemVisible(toastAlertItem, false, false);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BOTTOM_BUTTON: {
        if (finalFactor == 0f) {
          bottomButtonAction = 0;
        }
        break;
      }
    }
  }

  // Emoji tones

  @Override
  public int[] displayBaseViewWithAnchor (EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset) {
    return EmojiToneHelper.defaultDisplay(context, anchorView, viewToDisplay, viewWidth, viewHeight, horizontalMargin, horizontalOffset, verticalOffset, contentView, bottomWrap, emojiKeyboardFrameLayout);
  }

  @Override
  public void removeView (EmojiToneHelper context, View displayedView) {
    contentView.removeView(displayedView);
  }

  public void onUsernamePick (String username) {
    inputView.setInput("@" + username + " ", true, true);
  }

  // Link preview

  private void closeLinkPreview () {
    findTargetContext().dismiss();
    updateReplyBarVisibility(!isEditingMessage());
    inputView.setTextChangedSinceChatOpened(true);
  }

  @Override
  public void onSelectLinkPreviewUrl (ReplyBarView view, MessageInputContext messageContext, String url) {
    if (messageContext.setLinkPreviewUrl(url)) {
      Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_MULTIPLE_LINK_PREVIEWS);
      inputView.setTextChangedSinceChatOpened(true);
    }
  }

  @Override
  public boolean onRequestToggleLargeMedia (ReplyBarView view, View buttonView, MessageInputContext messageContext, LinkPreview linkPreview) {
    TdApi.LinkPreviewOptions options = messageContext.takeOutputLinkPreviewOptions(false);
    if (options.isDisabled) {
      return false;
    }
    if (linkPreview.toggleLargeMedia()) {
      options.forceSmallMedia = linkPreview.forceSmallMedia();
      options.forceLargeMedia = linkPreview.forceLargeMedia();
      if (StringUtils.isEmpty(options.url)) {
        options.url = linkPreview.url;
      }
      inputView.setTextChangedSinceChatOpened(true);
      showLinkPreviewHint(Lang.getString(linkPreview.getOutputShowLargeMedia() ? R.string.LinkPreviewEnlarged : R.string.LinkPreviewMinimized));
      return true;
    }
    return false;
  }

  @Override
  public boolean onRequestToggleShowAbove (ReplyBarView view, View buttonView, MessageInputContext messageContext) {
    TdApi.LinkPreviewOptions options = messageContext.takeOutputLinkPreviewOptions(false);
    if (!options.isDisabled) {
      options.showAboveText = !options.showAboveText;
      showLinkPreviewHint(Lang.getString(options.showAboveText ? R.string.LinkPreviewShowAbove : R.string.LinkPreviewShowBelow));
      return true;
    }
    return false;
  }

  private TooltipOverlayView.TooltipInfo linkPreviewHint;

  private void showLinkPreviewHint (CharSequence text) {
    if (linkPreviewHint == null) {
      linkPreviewHint = context().tooltipManager().builder(replyBarView.getLinkPreviewToggleView()).locate((targetView, rect) -> replyBarView.getLinkPreviewToggleView().getTargetBounds(targetView, rect))
        .icon(R.drawable.baseline_info_24)
        .ignoreViewScale(true)
        .controller(this)
        .show(tdlib, text);
    } else {
      linkPreviewHint.reset(context().tooltipManager().newContent(tdlib, text, 0), R.drawable.baseline_info_24);
      linkPreviewHint.show();
    }
    linkPreviewHint.hideDelayed(false);
  }

  private @NonNull TdApi.LinkPreviewOptions obtainLinkPreviewOptions (boolean close) {
    TdApi.LinkPreviewOptions linkPreviewOptions = findTargetContext().takeOutputLinkPreviewOptions(true);
    if (close) {
      findTargetContext().reset();
      updateReplyBarVisibility(false);
    }
    return linkPreviewOptions;
  }

  private final MessageInputContext draftContext = new MessageInputContext(this, tdlib, null, null);
  private MessageInputContext editContext;

  private MessageInputContext findTargetContext () {
    return isEditingMessage() ? editContext : draftContext;
  }

  public void showLinkPreview (@Nullable FoundUrls foundUrls) {
    if (inPreviewMode || isInForceTouchMode()) {
      return;
    }
    MessageInputContext targetContext = findTargetContext();
    if (targetContext.setFoundUrls(foundUrls)) {
      updateReplyBarVisibility(true);
    }
  }

  private boolean showingLinkPreview () {
    return findTargetContext().isVisible();
  }

  // Guess about the future RecyclerView height

  public int makeGuessAboutWidth () {
    if (isInForceTouchMode()) {
      return Screen.currentWidth() - ForceTouchView.getMatchParentHorizontalMargin() * 2;
    } else {
      return Screen.currentWidth();
    }
  }

  public static int getForcePreviewHeight (boolean hasHeader, boolean hasFooter) {
    int forcePreviewHeight = Screen.currentHeight()
      - ForceTouchView.getMatchParentTopMargin()
      - ForceTouchView.getMatchParentBottomMargin();
    if (hasHeader) {
      forcePreviewHeight -= Screen.dp(ForceTouchView.HEADER_HEIGHT);
    }
    if (hasFooter) {
      forcePreviewHeight -= Screen.dp(ForceTouchView.FOOTER_HEIGHT);
    }
    return forcePreviewHeight;
  }

  public int makeGuessAboutHeight () {
    if (isInForceTouchMode()) {
      return makeGuessAboutForcePreviewHeight();
    } else {
      int height = Screen.currentHeight() - HeaderView.getSize(true);

      if (canWriteMessages() || actionShowing) {
        height -= Screen.dp(49f);
      }
      /*if (bottomWrap == null || bottomWrap.getVisibility() == View.VISIBLE) {
        height -= Screen.dp(49f);
      }*/
      return height;
    }
  }

  protected int makeGuessAboutForcePreviewHeight () {
    return getForcePreviewHeight(/* hasHeader */ true, /* hasFooter */ true);
  }

  // Commands

  public boolean getCommandsState () {
    return commandsState;
  }

  public CommandKeyboardLayout getKeyboardLayout () {
    return keyboardLayout;
  }

  // Emoji

  public boolean getEmojiState () {
    return emojiState;
  }

  public @Nullable
  KeyboardFrameLayout getEmojiKeyboardLayout () {
    return emojiKeyboardFrameLayout;
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hide(inputView);
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    if (isEventLog()) {
      bottomWrap.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
      bottomShadowView.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
      RelativeLayout.LayoutParams params = ((RelativeLayout.LayoutParams) messagesView.getLayoutParams());
      params.addRule(RelativeLayout.ABOVE, visible ? 0 : R.id.msg_bottom);
      messagesView.setLayoutParams(params);

      params = ((RelativeLayout.LayoutParams) scrollToBottomButtonWrap.getLayoutParams());
      params.addRule(RelativeLayout.ABOVE, visible ? 0 : R.id.msg_bottom);
      params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, visible ? RelativeLayout.TRUE : 0);
      scrollToBottomButtonWrap.setLayoutParams(params);
    }

    if (isFocused()) {
      //boolean emojiShown = this.emojiShown;
      // boolean commandsShown = this.commandsShown;
      if (visible && !getKeyboardState()) {
        if (emojiShown) {
          closeEmojiKeyboard(true);
        }
        if (commandsShown) {
          closeCommandsKeyboard(true);
        }
      }
      boolean result = super.onKeyboardStateChanged(visible);
      if (emojiShown && emojiKeyboardFrameLayout != null) {
        emojiKeyboardFrameLayout.onKeyboardStateChanged(visible);
      }
      if (commandsShown && keyboardLayout != null) {
        keyboardLayout.onKeyboardStateChanged(visible);
      }
      return result;
    } else {
      return super.onKeyboardStateChanged(visible);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    BaseActivity context = context();
    if (context.getRecordAudioVideoController().isOpen()) {
      context.getRecordAudioVideoController().finishRecording(true);
      return true;
    }
    if (hasEditedChanges()) {
      if (isEditingCaption()) {
        showUnsavedChangesPromptBeforeLeaving(Lang.getString(R.string.DiscardEditCaptionHint), Lang.getString(R.string.DiscardEditCaption), null);
      } else {
        showUnsavedChangesPromptBeforeLeaving(Lang.getString(R.string.DiscardEditMsgHint), Lang.getString(R.string.DiscardEditMsg), null);
      }
      return true;
    }

    if (hasAttachedFiles()) {
      showUnsavedChangesPromptBeforeLeaving(Lang.getString(R.string.DiscardCaptionHint), Lang.getString(R.string.DiscardEditCaption), null);
      return true;
    }

    if (fromTop) {
      return false;
    }
    if (emojiShown) {
      emojiState = false;
      closeEmojiKeyboard();
      return true;
    }
    if (commandsShown) {
      commandsState = false;
      closeCommandsKeyboard(true);
      return true;
    }
    return false;
  }

  private boolean emojiShown, emojiState;

  private void toggleEmojiKeyboard () {
    if (emojiShown) {
      closeEmojiKeyboard();
    } else {
      openEmojiKeyboard();
    }
  }

  private float getKeyboardOffset () {
    return emojiKeyboardFrameLayout != null ? emojiKeyboardFrameLayout.getLayoutTranslationOffset() : 0f;
  }

  private void onKeyboardLayoutTranslation (float translationY) {
    updateButtonsY();
    updateReplyView();
    if (bottomWrap != null) {
      bottomWrap.setTranslationY(translationY);
    }
  }

  private void openEmojiKeyboard () {
    if (!emojiShown) {
      if (emojiKeyboardFrameLayout == null) {
        emojiKeyboardFrameLayout = new KeyboardFrameLayout(context());
        emojiKeyboardFrameLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emojiKeyboardFrameLayout.setParentView(bottomWrap, contentView, contentView);
        emojiKeyboardFrameLayout.setUpdateTranslationListener(this::onKeyboardLayoutTranslation);

        textFormattingLayout = emojiKeyboardFrameLayout.contentView.textFormattingLayout;
        textFormattingLayout.init(this, inputView, new TextFormattingLayout.Delegate() {
          @Override
          public void onWantsCloseTextFormattingKeyboard () {
            closeTextFormattingKeyboard();
          }

          @Override
          public void onWantsOpenTextFormattingKeyboard () {
            openEmojiKeyboard();
          }
        });

        emojiLayout = emojiKeyboardFrameLayout.contentView.emojiLayout;
        emojiLayout.initWithMediasEnabled(this, true, this, this, false);
        emojiLayout.setAllowPremiumFeatures(isSelfChat());
        emojiLayout.setAllowMedia(!hasAttachedFiles());
        emojiLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bottomWrap.addView(emojiKeyboardFrameLayout);
      }
      emojiKeyboardFrameLayout.setVisible(true);

      // updateButtonsY();

      if (commandsShown) {
        emojiState = commandsState;
        forceCloseCommandsKeyboard();
      } else {
        emojiState = getKeyboardState();
      }

      setEmojiShown(true, true);
      if (emojiState) {
        emojiButton.setImageResource(R.drawable.baseline_keyboard_24);
        emojiKeyboardFrameLayout.hideKeyboard(inputView);
      } else {
        emojiButton.setImageResource(R.drawable.baseline_direction_arrow_down_24);
      }
    }
  }

  @Override
  public void onSectionSwitched (EmojiLayout layout, int section, int prevSection) {
    if (emojiShown) {
      if (section != prevSection) {
        notifyChoosingEmoji(prevSection, false);
      }
      updateEmojiStatus();
    }
  }

  private void updateEmojiStatus () {
    @EmojiMediaType int type = emojiLayout != null ? emojiLayout.getCurrentEmojiSection() : EmojiLayout.getTargetSection();
    notifyChoosingEmoji(type, emojiShown);
  }

  private void setEmojiShown (boolean emojiShown, boolean animated) {
    if (this.emojiShown != emojiShown) {
      this.emojiShown = emojiShown;
      if (emojiShown) {
        hideSoftwareKeyboard();
      }
      updateEmojiStatus();
      setTextFormattingLayoutVisible(textInputHasSelection);

      if (inputView != null) {
        inputView.setActionModeVisibility(!textInputHasSelection || !emojiShown);
      }
    }
  }

  private void forceCloseEmojiKeyboard () {
    if (emojiShown) {
      if (emojiKeyboardFrameLayout != null) {
        emojiKeyboardFrameLayout.setVisible(false);
      }
      setEmojiShown(false, false);
      emojiButton.setImageResource(getTargetIcon(true));
    }
  }

  private void closeEmojiKeyboard () {
    closeEmojiKeyboard(false);
  }

  private void closeEmojiKeyboard (boolean byKeyboardOpen) {
    if (emojiShown) {
      if (emojiKeyboardFrameLayout != null) {
        emojiKeyboardFrameLayout.setVisible(false);
      }
      if (emojiState && isFocused() && !byKeyboardOpen) {
        emojiKeyboardFrameLayout.showKeyboard(inputView);
      }
      setEmojiShown(false, true);
      emojiButton.setImageResource(getTargetIcon(true));
    }
  }

  private void updateRecordLayout () {
    context().getRecordAudioVideoController().updatePositions();
  }

  /*@Override
  public boolean onBackspace () {
    if (inputView.length() > 0) {
      inputView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
      return true;
    }
    return false;
  }

  @Override
  public void onEmojiSelected (String emoji) {
    inputView.onEmojiSelected(emoji);
  }*/

  @Override
  public void onEnterEmoji (String emoji) {
    inputView.onEmojiSelected(emoji);
  }

  @Override
  public void onEnterCustomEmoji (TGStickerObj sticker) {
    inputView.onCustomEmojiSelected(sticker);
  }

  @Override
  public long getStickerSuggestionsChatId () {
    return getChatId();
  }

  @Override
  public long getOutputChatId () {
    return getChatId();
  }

  @Override
  public boolean onSendSticker (View view, TGStickerObj sticker, TdApi.MessageSendOptions sendOptions) {
    if (lastJunkTime == 0l || SystemClock.uptimeMillis() - lastJunkTime >= JUNK_MINIMUM_DELAY) {
      if (sendSticker(view, sticker.getSticker(), sticker.getFoundByEmoji(), true, sendOptions)) {
        lastJunkTime = SystemClock.uptimeMillis();
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onSendGIF (View view, TdApi.Animation animation) {
    if (lastJunkTime == 0l || SystemClock.uptimeMillis() - lastJunkTime >= JUNK_MINIMUM_DELAY) {
      if (sendAnimation(view, animation, true)) {
        lastJunkTime = SystemClock.uptimeMillis();
        return true;
      }
    }
    return false;
  }

  @Override
  public void onDeleteEmoji () {
    if (inputView.length() > 0) {
      inputView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
    }
  }

  @Override
  public void onSearchRequested (EmojiLayout layout, boolean areStickers) {
    setInputInlineBot(0, "@" + tdlib.getAnimationSearchBotUsername());
  }

  public void onInputTextChange (CharSequence charSequence, boolean byUserAction) {
    if (sendMenu != null) {
      sendMenu.hideMenu();
    }
    if (emojiLayout != null) {
      emojiLayout.onTextChanged(charSequence);
    }
    updateSendButton(charSequence, true);
    if (byUserAction) {
      setTyping(charSequence.length() > 0);
      inputView.setTextChangedSinceChatOpened(true);
    }
  }

  @Override
  public boolean isEmojiInputEmpty () {
    return inputView == null || inputView.getText().length() == 0;
  }

  public int getTopOffset () {
    int total = topBar.getTotalVisualHeight();
    total *= (1f - getSearchTransformFactor());
    return total;
  }

  public final void onMessagesFrameChanged () {
    context().updateHackyOverlaysPositions();
    manager.onViewportMeasure();
    if (attachedFiles != null && attachedFiles.getParent() != null) {
      attachedFiles.updatePosition(true);
    }
    if (messagesView != null) {
      messagesView.invalidate();
    }
  }

  private final int[] cursorCoordinates = new int[2], symbolUnderCursorPosition = new int[2];

  public int[] getInputCursorOffset () {
    if (inputView == null) {
      cursorCoordinates[0] = cursorCoordinates[1] = 0;
      return cursorCoordinates;
    }
    inputView.getSymbolUnderCursorPosition(symbolUnderCursorPosition);
    cursorCoordinates[0] = symbolUnderCursorPosition[0] + inputView.getLeft() + inputView.getPaddingLeft();
    cursorCoordinates[1] = symbolUnderCursorPosition[1] - inputView.getLineHeight() + Screen.currentHeight() - getInputOffset(true) - Screen.dp(40);
    return cursorCoordinates;
  }

  public int getInputOffset (boolean excludeTranslation) {
    if (bottomWrap.getVisibility() == View.GONE) {
      return 0;
    }
    int bottom = bottomWrap.getMeasuredHeight();
    if (!excludeTranslation) {
      bottom += getReplyOffset();
    }
    bottom += getKeyboardOffset();
    return bottom;
  }

  // Stickers

  private static final long JUNK_MINIMUM_DELAY = 200l;
  private long lastJunkTime;

  // Record

  public LinearLayout getBottomWrap () {
    return bottomWrap;
  }

  // Send sticker

  private boolean sendContent (View view, int rightId, int defaultRes, int specificRes, int specificUntilRes, boolean canReply, TdApi.MessageSendOptions sendOptions, Future<TdApi.InputMessageContent> content) {
    return sendContent(view, rightId, defaultRes, specificRes, specificUntilRes, canReply ? this::obtainReplyTo : null, sendOptions, content);
  }

  private boolean showGifRestriction (View view) {
    return showSlowModeRestriction(view, null) || showRestriction(view, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledStickers, R.string.ChatRestrictedStickers, R.string.ChatRestrictedStickersUntil);
  }

  public boolean showPhotoVideoRestriction (View view) { // TODO separate photos & videos
    return showPhotoVideoRestriction(view, true, true);
  }

  public boolean showPhotoVideoRestriction (View view, boolean checkPhotos, boolean checkVideos) { // TODO separate photos & videos
    Tdlib.RestrictionStatus photosStatus = checkPhotos ? tdlib.getRestrictionStatus(chat, RightId.SEND_PHOTOS) : null;
    Tdlib.RestrictionStatus videosStatus = checkVideos ? tdlib.getRestrictionStatus(chat, RightId.SEND_VIDEOS) : null;
    if (photosStatus == null && videosStatus == null) {
      return false;
    }

    if (showSlowModeRestriction(view, null)) {
      return true;
    }

    if (videosStatus == null || (videosStatus.isGlobal() && photosStatus != null && !photosStatus.isGlobal())) {
      // photo
      return showRestriction(view, RightId.SEND_PHOTOS, R.string.ChatDisabledPhoto, R.string.ChatRestrictedPhoto, R.string.ChatRestrictedPhotoUntil);
    }
    if (photosStatus == null || (photosStatus.isGlobal() && videosStatus != null && !videosStatus.isGlobal())) {
      // video
      return showRestriction(view, RightId.SEND_PHOTOS, R.string.ChatDisabledVideo, R.string.ChatRestrictedVideo, R.string.ChatRestrictedVideoUntil);
    }
    return showRestriction(view, RightId.SEND_PHOTOS, R.string.ChatDisabledMedia, R.string.ChatRestrictedMedia, R.string.ChatRestrictedMediaUntil);
  }

  public boolean showRestriction (View view, @RightId int rightId) {
    CharSequence text = tdlib.getDefaultRestrictionText(chat, rightId);
    return showRestriction(view, text);
  }

  public boolean showSlowModeRestriction (View v, @Nullable TdApi.MessageSendOptions sendOptions) {
    CharSequence restriction = tdlib().getSlowModeRestrictionText(getChatId(), sendOptions != null ? sendOptions.schedulingState : null);
    if (restriction != null) {
      if (v == sendButton || v == recordButton) {
        showBottomHint(restriction, true);
        isSlowModeRestrictionHintVisible = true;
        return true;
      }
      showRestriction(v, restriction);
      return true;
    }

    return false;
  }

  public boolean showRestriction (View view, CharSequence restrictionText) {
    if (restrictionText != null) {
      if (view == sendButton || view == recordButton) {
        showBottomHint(restrictionText, true);
      } else if (view == null) {
        UI.showToast(restrictionText, Toast.LENGTH_SHORT);
      } else {
        context().tooltipManager().builder(view).icon(R.drawable.baseline_warning_24).controller(this).show(tdlib, restrictionText).hideDelayed();
      }
      return true;
    }
    return false;
  }

  public boolean showRestriction (View view, @RightId int rightId, int defaultRes, int specificRes, int specificUntilRes) {
    CharSequence restrictionText = tdlib.buildRestrictionText(chat, rightId, defaultRes, specificRes, specificUntilRes);
    return showRestriction(view, restrictionText);
  }

  private boolean sendContent (View view, @RightId int rightId, int defaultRes, int specificRes, int specificUntilRes, Future<TdApi.InputMessageReplyTo> replyTo, TdApi.MessageSendOptions initialSendOptions, Future<TdApi.InputMessageContent> content) {
    if (showSlowModeRestriction(view, initialSendOptions) || showRestriction(view, rightId, defaultRes, specificRes, specificUntilRes))
      return false;
    pickDateOrProceed(initialSendOptions, (modifiedSendOptions, disableMarkdown) -> {
      tdlib.sendMessage(chat.id, getMessageThreadId(), replyTo != null ? replyTo.getValue() : null, Td.newSendOptions(modifiedSendOptions, obtainSilentMode()), content.getValue(), null);
    });
    return true;
  }

  private boolean sendSticker (View view, TdApi.Sticker sticker, String emoji, boolean allowReply, TdApi.MessageSendOptions initialSendOptions) {
    if (sticker == null) {
      return false;
    }
    if (Td.isPremium(sticker) && tdlib.ui().showPremiumAlert(this, view, TdlibUi.PremiumFeature.STICKER)) {
      return false;
    }
    if (Td.customEmojiId(sticker) != 0 && canWriteMessages() && inputView != null) {
      inputView.onCustomEmojiSelected(sticker);
      return false;
    }
    if (Td.customEmojiId(sticker) != 0 && canWriteMessages() && inputView != null) {
      inputView.onCustomEmojiSelected(sticker);
      return false;
    }
    return sendContent(view, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledStickers, R.string.ChatRestrictedStickers, R.string.ChatRestrictedStickersUntil, allowReply, initialSendOptions, () -> new TdApi.InputMessageSticker(new TdApi.InputFileId(sticker.sticker.id), null, 0, 0, emoji));
  }

  private void sendSticker (String path, boolean allowReply, TdApi.MessageSendOptions initialSendOptions) {
    sendContent(null, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledStickers, R.string.ChatRestrictedStickers, R.string.ChatRestrictedStickersUntil, allowReply, initialSendOptions, () -> new TdApi.InputMessageSticker(TD.createInputFile(path), null, 0, 0, null));
  }

  private boolean sendAnimation (View view, TdApi.Animation animation, boolean allowReply) {
    return sendContent(view, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledGifs, R.string.ChatRestrictedGifs, R.string.ChatRestrictedGifsUntil, allowReply, Td.newSendOptions(), () -> TD.toInputMessageContent(animation));
  }

  private void sendDice (View view, String emoji) {
    int disabledRes, restrictedRes, restrictedUntilRes;
    if (ContentPreview.EMOJI_DART.textRepresentation.equals(emoji)) {
      disabledRes = R.string.ChatDisabledDart;
      restrictedRes = R.string.ChatRestrictedDart;
      restrictedUntilRes = R.string.ChatRestrictedDartUntil;
    } else if (ContentPreview.EMOJI_DICE.textRepresentation.equals(emoji)) {
      disabledRes = R.string.ChatDisabledDice;
      restrictedRes = R.string.ChatRestrictedDice;
      restrictedUntilRes = R.string.ChatRestrictedDiceUntil;
    } else {
      disabledRes = R.string.ChatDisabledStickers;
      restrictedRes = R.string.ChatRestrictedStickers;
      restrictedUntilRes = R.string.ChatRestrictedStickersUntil;
    }
    sendContent(view, RightId.SEND_OTHER_MESSAGES, disabledRes, restrictedRes, restrictedUntilRes, null, Td.newSendOptions(), () -> new TdApi.InputMessageDice(emoji, false));
  }

  // Event log

  private TdApi.ChatAdministrators chatAdmins;
  private RunnableData<TdApi.ChatAdministrators> pendingChatAdminsCallback;

  private void loadChannelAdmins (RunnableData<TdApi.ChatAdministrators> after) {
    if (chatAdmins != null) {
      after.runWithData(chatAdmins);
      return;
    }
    if (pendingChatAdminsCallback != null) {
      pendingChatAdminsCallback = after;
      return;
    }
    pendingChatAdminsCallback = after;

    // TdApi.Function function = new TdApi.GetSupergroupMembers(TD.getChatSupergroupId(chat), new TdApi.ChannelMembersFilterAdministrators(), 0, 200);

    long chatId = chat.id;
    Client.ResultHandler handler = object -> {
      switch (object.getConstructor()) {
        case TdApi.ChatAdministrators.CONSTRUCTOR: {
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              TdApi.ChatAdministrators members = (TdApi.ChatAdministrators) object;
              chatAdmins = members;
              pendingChatAdminsCallback.runWithData(members);
            }
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
    if (tdlib.isSupergroupChat(chat) && tdlib.telegramAntiSpamUserId() != 0) {
      tdlib.client().send(new TdApi.GetUser(tdlib.telegramAntiSpamUserId()), ignored -> {
        tdlib.client().send(new TdApi.GetChatAdministrators(chatId), handler);
      });
    } else {
      tdlib.client().send(new TdApi.GetChatAdministrators(chatId), handler);
    }
  }

  private static boolean checkFilter (@IdRes int filterId, TdApi.ChatEventLogFilters filters) {
    if (filters == null) {
      return true;
    }
    if (filterId == R.id.btn_filterAll) {
      return TD.isAll(filters);
    } else if (filterId == R.id.btn_filterRestrictions) {
      return filters.memberRestrictions;
    } else if (filterId == R.id.btn_filterAdmins) {
      return filters.memberPromotions;
    } else if (filterId == R.id.btn_filterMembers) {
      return filters.memberJoins || filters.memberInvites;
    } else if (filterId == R.id.btn_filterInviteLinks) {
      return filters.inviteLinkChanges;
    } else if (filterId == R.id.btn_filterInfo) {
      return filters.infoChanges;
    } else if (filterId == R.id.btn_filterSettings) {
      return filters.settingChanges;
    } else if (filterId == R.id.btn_filterDeletedMessages) {
      return filters.messageDeletions;
    } else if (filterId == R.id.btn_filterEditedMessages) {
      return filters.messageEdits;
    } else if (filterId == R.id.btn_filterPinnedMessages) {
      return filters.messagePins;
    } else if (filterId == R.id.btn_filterLeavingMembers) {
      return filters.memberLeaves;
    } else if (filterId == R.id.btn_filterVideoChats) {
      return filters.videoChatChanges;
    }
    return false;
  }

  private void openEventLogSettings () {
    loadChannelAdmins(result -> {
        if (!isFocused()) {
          return;
        }

        TdApi.ChatEventLogFilters filters = manager.getEventLogFilters();
        long[] userIds = manager.getEventLogUserIds();

        ArrayList<ListItem> items = new ArrayList<>();

        final int[] ids;
        final String[] strings;

        final boolean isChannel = tdlib.isChannel(chat.id);

        if (isChannel) {
          ids = new int[] {
            R.id.btn_filterAll,
            R.id.btn_filterAdmins,
            R.id.btn_filterMembers,
            R.id.btn_filterInviteLinks,
            R.id.btn_filterInfo,
            R.id.btn_filterSettings,
            R.id.btn_filterDeletedMessages,
            R.id.btn_filterEditedMessages,
            R.id.btn_filterPinnedMessages,
            R.id.btn_filterLeavingMembers,
            R.id.btn_filterVideoChats
          };
          strings = new String[] {
            Lang.getString(R.string.EventLogFilterAll),
            Lang.getString(R.string.EventLogFilterNewAdmins),
            Lang.getString(R.string.EventLogFilterNewMembers),
            Lang.getString(R.string.EventLogFilterInviteLinks),
            Lang.getString(R.string.EventLogFilterChannelInfo),
            Lang.getString(R.string.EventLogFilterChannelSettings),
            Lang.getString(R.string.EventLogFilterDeletedMessages),
            Lang.getString(R.string.EventLogFilterEditedMessages),
            Lang.getString(R.string.EventLogFilterPinnedMessages),
            Lang.getString(R.string.EventLogFilterLeavingMembers),
            Lang.getString(R.string.EventLogFilterLiveStreams)
          };
        } else {
          ids = new int[] {
            R.id.btn_filterAll,
            R.id.btn_filterRestrictions,
            R.id.btn_filterAdmins,
            R.id.btn_filterMembers,
            R.id.btn_filterInviteLinks,
            R.id.btn_filterInfo,
            R.id.btn_filterSettings,
            R.id.btn_filterDeletedMessages,
            R.id.btn_filterEditedMessages,
            R.id.btn_filterPinnedMessages,
            R.id.btn_filterLeavingMembers,
            R.id.btn_filterVideoChats
          };
          strings = new String[] {
            Lang.getString(R.string.EventLogFilterAll),
            Lang.getString(R.string.EventLogFilterNewRestrictions),
            Lang.getString(R.string.EventLogFilterNewAdmins),
            Lang.getString(R.string.EventLogFilterNewMembers),
            Lang.getString(R.string.EventLogFilterInviteLinks),
            Lang.getString(R.string.EventLogFilterGroupInfo),
            Lang.getString(R.string.EventLogFilterGroupSettings),
            Lang.getString(R.string.EventLogFilterDeletedMessages),
            Lang.getString(R.string.EventLogFilterEditedMessages),
            Lang.getString(R.string.EventLogFilterPinnedMessages),
            Lang.getString(R.string.EventLogFilterLeavingMembers),
            Lang.getString(R.string.EventLogFilterVoiceChats)
          };
        }

        boolean isFirst = true;
        int i = 0;

        for (int id : ids) {
          if (isFirst) {
            isFirst = false;
          } else {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          }
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, id == R.id.btn_filterAll ? id : R.id.btn_filter, 0, strings[i], id, checkFilter(id, filters)).setData(filters));
          i++;
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM).setTextColorId(ColorId.background));

        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP).setTextColorId(ColorId.background));

        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_members, 0, R.string.EventLogAllAdmins, userIds == null));

        if (tdlib.isSupergroupChat(chat)) {
          TdApi.SupergroupFullInfo fullInfo = tdlib.cache().supergroupFull(ChatId.toSupergroupId(chat.id));
          if (fullInfo != null && fullInfo.hasAggressiveAntiSpamEnabled) {
            long userId = tdlib.telegramAntiSpamUserId();
            if (userId != 0) {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
              items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR, R.id.user, 0, tdlib.cache().userName(userId), userIds == null || ArrayUtils.indexOf(userIds, userId) != -1).setLongId(userId).setLongValue(userId));
            }
          }
        }

        for (TdApi.ChatAdministrator admin : chatAdmins.administrators) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR, R.id.user, 0, tdlib.cache().userName(admin.userId), userIds == null || ArrayUtils.indexOf(userIds, admin.userId) != -1).setLongId(admin.userId).setLongValue(admin.userId));
        }


        ListItem[] array = new ListItem[items.size()];
        items.toArray(array);

        final SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_filter);
        showSettings(b
          .setNeedSeparators(false)
          .setNeedRootInsets(true)
          .setRawItems(array)
          .setSaveStr(R.string.Apply)
          .setAllowResize(true)
          .setDisableToggles(true)
          .setOnActionButtonClick((wrap, view, isCancel) -> {
            if (isCancel) {
              return false;
            }

            final TdApi.ChatEventLogFilters filter = new TdApi.ChatEventLogFilters(
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true,
              true
            );
            final LongList userIds1;


            int i12 = wrap.adapter.indexOfViewById(R.id.btn_members);
            if (i12 != -1 && wrap.adapter.getItems().get(i12).isSelected()) {
              userIds1 = null;
            } else {
              userIds1 = new LongList(chatAdmins != null ? chatAdmins.administrators.length : 10);
            }

            int filterCount = 0;

            final List<ListItem> listItems = wrap.adapter.getItems();
            final int totalCount = listItems.size();
            for (i12 = 0; i12 < totalCount; i12++) {
              ListItem item = listItems.get(i12);
              final int itemId = item.getId();

              if (itemId == R.id.btn_filter) {
                boolean isSelected = item.isSelected();
                if (isSelected) {
                  filterCount++;
                }
                final int checkId = item.getCheckId();
                if (checkId == R.id.btn_filterRestrictions) {
                  filter.memberRestrictions = isSelected;
                } else if (checkId == R.id.btn_filterAdmins) {
                  filter.memberPromotions = isSelected;
                } else if (checkId == R.id.btn_filterMembers) {
                  filter.memberJoins = filter.memberInvites = isSelected;
                } else if (checkId == R.id.btn_filterInviteLinks) {
                  filter.inviteLinkChanges = isSelected;
                } else if (checkId == R.id.btn_filterInfo) {
                  filter.infoChanges = isSelected;
                } else if (checkId == R.id.btn_filterDeletedMessages) {
                  filter.messageDeletions = isSelected;
                } else if (checkId == R.id.btn_filterSettings) {
                  filter.settingChanges = isSelected;
                } else if (checkId == R.id.btn_filterEditedMessages) {
                  filter.messageEdits = isSelected;
                } else if (checkId == R.id.btn_filterPinnedMessages) {
                  filter.messagePins = isSelected;
                } else if (checkId == R.id.btn_filterLeavingMembers) {
                  filter.memberLeaves = isSelected;
                } else if (checkId == R.id.btn_filterVideoChats) {
                  filter.videoChatChanges = isSelected;
                }
              } else if (itemId == R.id.user) {
                if (item.isSelected() && userIds1 != null) {
                  userIds1.append(item.getLongValue());
                }
              }
            }

            if (filterCount == 0 || (userIds1 != null && userIds1.size() == 0)) {
              context.tooltipManager().builder(view).show(null, tdlib, R.drawable.baseline_warning_24, Lang.getString(R.string.EventLogEmptyFilter));
              return true;
            }

            manager.applyEventLogFilters(filter, userIds1 != null ? userIds1.get() : null);

            return false;
          })
          .setSettingProcessor((item, view, isUpdate) -> {
            switch (item.getViewType()) {
              case ListItem.TYPE_CHECKBOX_OPTION:
              case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
                view.setEmojiStatus(tdlib.cache().user(item.getLongValue()));
                ((CheckBoxView) view.getChildAt(0)).setChecked(item.isSelected(), isUpdate);
                break;
            }
          })
          .setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter, window) -> {
            switch (item.getViewType()) {
              case ListItem.TYPE_CHECKBOX_OPTION:
              case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
                break;
              default:
                return;
            }
            final boolean isSelect = ((CheckBoxView) ((SettingView) view).getChildAt(0)).toggle();
            item.setSelected(isSelect);

            final List<ListItem> allItems = settingsAdapter.getItems();
            final int size = allItems.size();

            final int itemId = item.getId();
            if (itemId == R.id.btn_members) {
              // Select/unselect all admins
              for (int i1 = 0; i1 < size; i1++) {
                ListItem userItem = allItems.get(i1);
                if (userItem.getId() == R.id.user && userItem.isSelected() != isSelect) {
                  userItem.setSelected(isSelect);
                  settingsAdapter.updateValuedSettingByPosition(i1);
                }
              }
            } else if (itemId == R.id.user) {
              // Select/unselect user, unselect "All admins"
              int i1 = settingsAdapter.indexOfViewById(R.id.btn_members);
              if (i1 != -1) {
                ListItem allItem = allItems.get(i1);
                if (allItem.isSelected()) {
                  allItem.setSelected(false);
                  settingsAdapter.updateValuedSettingByPosition(i1);
                }
              }
            } else if (itemId == R.id.btn_filterAll) {
              // Select/Unselect all filters
              for (int i1 = 0; i1 < size; i1++) {
                ListItem filterItem = allItems.get(i1);
                if (filterItem.getId() == R.id.btn_filter && filterItem.isSelected() != isSelect) {
                  filterItem.setSelected(isSelect);
                  settingsAdapter.updateValuedSettingByPosition(i1);
                }
              }
            } else if (itemId == R.id.btn_filter) {
              int selectedFilters = 0;
              for (int i1 = 0; i1 < size; i1++) {
                ListItem filterItem = allItems.get(i1);
                if (filterItem.getId() == R.id.btn_filter && filterItem.isSelected()) {
                  selectedFilters++;
                }
              }

              boolean allSelected = selectedFilters == ids.length - 1;

              int i1 = settingsAdapter.indexOfViewById(R.id.btn_filterAll);
              if (i1 != -1) {
                ListItem allItem = allItems.get(i1);
                if (allItem.isSelected() != allSelected) {
                  allItem.setSelected(allSelected);
                  settingsAdapter.updateValuedSettingByPosition(i1);
                }
              }
            }
          })
        );
    });
  }

  // Send text

  public void updateSendButton (CharSequence message, boolean animated) {
    sendButton.setIsActive(message.length() > 0 || isEditingCaption());
    checkSendButton(animated);
  }

  private void checkSendButton (boolean animated) {
    setSendVisible(inputView.getText().length() > 0 || isEditingMessage() || hasAttachedFiles(), animated && getParentOrSelf().isAttachedToNavigationController());
  }

  private void displaySendButton () {
    sendButton.setVisibility(View.VISIBLE);
  }

  private void hideSendButton () {
    sendButton.setVisibility(View.INVISIBLE);
  }

  private void displayAttachButtons () {
    attachButtons.setVisibility(View.VISIBLE);
    if (canSelectSender()) {
      messageSenderButton.setVisibility(View.VISIBLE);
    }
  }

  private void hideAttachButtons () {
    attachButtons.setVisibility(View.INVISIBLE);
    messageSenderButton.setVisibility(View.INVISIBLE);
  }

  private void displayEmojiButton () {
    emojiButton.setVisibility(View.VISIBLE);
    emojiButton.setOnClickListener(this);
  }

  private void hideEmojiButton () {
    emojiButton.setVisibility(View.INVISIBLE);
    emojiButton.setOnClickListener(null);
  }

  public SendButton getSendButton () {
    return sendButton;
  }

  public View getAttachButton () {
    return mediaButton;
  }

  private float sendFactor;

  private void setSendFactor (float factor) {
    if (this.sendFactor != factor) {
      this.sendFactor = factor;

      float scale = .5f * factor;

      float scale1 = .5f + scale;
      sendButton.setAlpha(factor);
      sendButton.setScaleX(scale1);
      sendButton.setScaleY(scale1);

      float scale2 = 1f - scale;
      attachButtons.setAlpha(1f - factor);
      attachButtons.setScaleX(scale2);
      attachButtons.setScaleY(scale2);
      messageSenderButton.setSendFactor(sendFactor);

      if (tooltipInfo != null && tooltipInfo.isVisible()) {
        tooltipInfo.reposition();
      }
    }
  }

  private static final int ANIMATOR_SEND = 9;

  private final BoolAnimator sendShown = new BoolAnimator(ANIMATOR_SEND, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 150l);

  private void setSendVisible (boolean isVisible, boolean animated) {
    if (isVisible && sendButton != null && sendButton.getVisibility() != View.VISIBLE) { // fix
      displaySendButton();
    }
    if (this.sendShown.getValue() != isVisible || !animated) {
      hideBottomHint();
      if (!sendShown.isAnimating()) {
        if (isVisible) {
          displaySendButton();
        } else {
          displayAttachButtons();
        }
      }
      sendShown.setValue(isVisible, animated && isFocused());
      showMessageMenuTutorial();
    }
  }

  public void sendText (boolean applyMarkdown, TdApi.MessageSendOptions sendOptions) {
    if (inputView != null) {
      sendText(inputView.getOutputText(applyMarkdown), true, applyMarkdown, true, true, sendOptions);
    }
  }

  public void sendCommand (InlineResultCommand command) {
    pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) -> {
      if (ChatId.isUserChat(getChatId()) || command.getUsername() == null) {
        sendText(command.getCommand(), true, true, false, sendOptions);
      } else {
        sendText(command.getCommand() + '@' + command.getUsername(), true, true, false, sendOptions);
      }
    });
  }

  public void sendCommand (String command, String username) {
    pickDateOrProceed(Td.newSendOptions(), (sendOptions, disableMarkdown) -> {
      if (ChatId.isPrivate(getChatId()) || username == null || command.contains("@")) {
        if (actionShowing && actionMode == ACTION_BOT_START) {
          hideActionButton();
        }
        sendText(command, false, false, false, sendOptions);
      } else {
        sendText(command + '@' + username, false, false, false, sendOptions);
      }
    });
  }

  private void sendText (String msg, boolean clearInput, boolean allowReply, boolean allowLinkPreview, TdApi.MessageSendOptions sendOptions) {
    sendText(new TdApi.FormattedText(msg, null), clearInput, true, allowReply, allowLinkPreview, sendOptions);
  }

  private boolean isSendingText;

  private void setIsSendingText (boolean isSendingText) {
    if (this.isSendingText != isSendingText) {
      this.isSendingText = isSendingText;
      setStackLocked(isSendingText);
      inputView.setInputBlocked(isSendingText);

    }
  }

  private void sendText (TdApi.FormattedText msg, boolean clearInput, boolean allowDice, boolean allowReply, boolean allowLinkPreview, TdApi.MessageSendOptions initialSendOptions) {
    if ((Td.isEmpty(msg) && !(clearInput && inputView != null && inputView.getText().length() > 0)) || (isSendingText && clearInput)) {
      return;
    }
    if (!hasSendBasicMessagePermission()) {
      context().tooltipManager().builder(sendButton != null ? sendButton : inputView).show(tdlib, R.string.MessageInputTextDisabledHint).hideDelayed();
      return;
    }

    long chatId = getChatId();
    long messageThreadId = getMessageThreadId();
    final @Nullable TdApi.InputMessageReplyTo replyTo = allowReply ? (clearInput ? getCurrentReplyId() : obtainReplyTo()) : null;
    final TdApi.LinkPreviewOptions linkPreviewOptions = allowLinkPreview ? obtainLinkPreviewOptions(false) : new TdApi.LinkPreviewOptions(
      true, "", false, false, false
    );

    TdApi.InputMessageContent content;
    if (allowDice && tdlib.shouldSendAsDice(msg)) {
      int disabledRes, restrictedRes, restrictedUntilRes;
      if (ContentPreview.EMOJI_DART.textRepresentation.equals(msg.text)) {
        disabledRes = R.string.ChatDisabledDart;
        restrictedRes = R.string.ChatRestrictedDart;
        restrictedUntilRes = R.string.ChatRestrictedDartUntil;
      } else if (ContentPreview.EMOJI_DICE.textRepresentation.equals(msg.text)) {
        disabledRes = R.string.ChatDisabledDice;
        restrictedRes = R.string.ChatRestrictedDice;
        restrictedUntilRes = R.string.ChatRestrictedDiceUntil;
      } else {
        disabledRes = R.string.ChatDisabledStickers;
        restrictedRes = R.string.ChatRestrictedStickers;
        restrictedUntilRes = R.string.ChatRestrictedStickersUntil;
      }
      if (showRestriction(sendButton, RightId.SEND_OTHER_MESSAGES, disabledRes, restrictedRes, restrictedUntilRes)) {
        return;
      }
      content = new TdApi.InputMessageDice(msg.text.trim(), clearInput);
    } else {
      content = new TdApi.InputMessageText(msg, linkPreviewOptions, clearInput);
    }

    final TdApi.MessageSendOptions finalSendOptions = Td.newSendOptions(initialSendOptions, obtainSilentMode(), Settings.instance().getNewSetting(Settings.SETTING_FLAG_DYNAMIC_ORDER_EMOJI_PACKS));
    List<TdApi.Function<?>> functions = (List<TdApi.Function<?>>) (List<?>) TD.sendMessageText(chatId, messageThreadId, replyTo, finalSendOptions, content, tdlib.maxMessageTextLength());

    if (showSlowModeRestriction(sendButton != null ? sendButton : inputView, finalSendOptions)) {
      return;
    }

    if (clearInput) {
      final List<TdApi.Message> sentMessages = new ArrayList<>(functions.size());
      setIsSendingText(true);
      manager.setSentMessages(sentMessages);
      executeSendMessageFunctions(functions, sentMessages, finalSendOptions.schedulingState != null, success -> {
        clearInputAfterSend(success, allowReply, replyTo, allowLinkPreview);
      });
    } else {
      for (TdApi.Function<?> function : functions) {
        tdlib.client().send(function, tdlib.messageHandler());
      }
    }
  }

  private void clearInputAfterSend (boolean success, boolean allowReply, TdApi.InputMessageReplyTo replyTo, boolean allowLinkPreview) {
    if (!isDestroyed()) {
      manager.setSentMessages(null);
      if (success) {
        if (allowReply && replyTo != null && Td.equalsTo(getCurrentReplyId(), replyTo)) {
          obtainReplyTo();
        }
        if (allowLinkPreview) {
          obtainLinkPreviewOptions(true);
        }
        discardAttachedFiles(true);
        inputView.setInput("", false, true);
      }
      setIsSendingText(false);
      /*if (success) {
        inputView.setInput("", false);
      }*/
    }
  }

  private void executeSendMessageFunctions (List<TdApi.Function<?>> functions, List<TdApi.Message> sentMessages, final boolean isSchedule, RunnableBool onDone) {
    final int expectedCount = functions.size();
    final int[] sentFunctionsCount = new int[1];

    Client.ResultHandler handler = new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object result) {
        boolean done = false;
        switch (result.getConstructor()) {
          case TdApi.Message.CONSTRUCTOR: {
            TdApi.Message message = (TdApi.Message) result;
            sentMessages.add(message);
            sentFunctionsCount[0] += 1;
            int sentCount = sentFunctionsCount[0];
            if (sentCount < expectedCount) {
              tdlib.listeners().subscribeToUpdates(message);
              tdlib.client().send(functions.get(sentCount), this);
            } else {
              done = true;
            }
            tdlib.messageHandler().onResult(result);
            break;
          }
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Messages messages = (TdApi.Messages) result;
            for (TdApi.Message message: messages.messages) {
              if (message == null) continue;
              sentMessages.add(message);
            }
            sentFunctionsCount[0] += 1;
            int sentCount = sentFunctionsCount[0];
            if (sentCount < expectedCount) {
              for (TdApi.Message message: messages.messages) {
                if (message == null) continue;
                tdlib.listeners().subscribeToUpdates(message);
              }
              tdlib.client().send(functions.get(sentCount), this);
            } else {
              done = true;
            }
            tdlib.messageHandler().onResult(result);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            tdlib.ui().post(() -> {
              if (isFocused()) {
                showBottomHint(TD.toErrorString(result), true);
              } else {
                UI.showError(result);
              }
            });
            done = true;
            break;
          }
          default: {
            throw new UnsupportedOperationException(result.toString());
          }
        }
        if (done) {
          int sentMessagesCount = sentMessages.size();
          if (sentMessagesCount > 0) {
            for (int i = sentMessagesCount - 1; i >= 0; i--) {
              tdlib.listeners().unsubscribeFromUpdates(sentMessages.get(i));
            }
            List<TGMessage> parsedMessages = manager.parseMessages(sentMessages);
            tdlib.ui().post(() -> {
              if (isSchedule == areScheduled) {
                manager.addSentMessages(parsedMessages);
              }
              onDone.runWithBool(sentFunctionsCount[0] == expectedCount);
              if (!areScheduled && isSchedule && isFocused()) {
                viewScheduledMessages(true);
              }
            });
          } else {
            tdlib.ui().post(() -> onDone.runWithBool(false));
          }
        }
      }
    };
    tdlib.client().send(functions.get(0), handler);
  }

  public void sendContact (TdApi.User user, boolean allowReply, TdApi.MessageSendOptions initialSendOptions) {
    if (hasSendMessagePermission(RightId.SEND_BASIC_MESSAGES)) {
      pickDateOrProceed(initialSendOptions, (modifiedSendOptions, disableMarkdown) -> {
        tdlib.sendMessage(chat.id,
          getMessageThreadId(),
          allowReply ? obtainReplyTo() : null,
          Td.newSendOptions(modifiedSendOptions, obtainSilentMode()),
          new TdApi.InputMessageContact(new TdApi.Contact(user.phoneNumber, user.firstName, user.lastName, null, user.id)),
          null
        );
      });
    }
  }

  public void shareMyContact (boolean allowReply) {
    shareMyContact(allowReply ? obtainReplyTo() : null);
  }

  public void shareMyContact (@Nullable TdApi.InputMessageReplyTo forceReplyTo) {
    if (hasSendMessagePermission(RightId.SEND_BASIC_MESSAGES)) {
      TdApi.User user = tdlib.myUser();
      if (user != null) {
        pickDateOrProceed(Td.newSendOptions(), (modifiedSendOptions, disableMarkdown) -> {
          tdlib.sendMessage(chat.id, getMessageThreadId(), forceReplyTo, Td.newSendOptions(modifiedSendOptions, obtainSilentMode()), new TdApi.InputMessageContact(new TdApi.Contact(user.phoneNumber, user.firstName, user.lastName, null, user.id)), null);
        });
      }
    }
  }

  public void send (TdApi.InputMessageContent content, boolean allowReply, TdApi.MessageSendOptions initialSendOptions, RunnableData<TdApi.Message> after) {
    if (tdlib().getRestrictionText(chat, content) == null) {
      pickDateOrProceed(initialSendOptions, (modifiedSendOptions, disableMarkdown) -> {
        tdlib.sendMessage(chat.id, getMessageThreadId(), allowReply ? obtainReplyTo() : null, Td.newSendOptions(modifiedSendOptions, obtainSilentMode()), content, after);
      });
    }
  }

  public void sendInlineQueryResult (long inlineQueryId, String id, boolean allowReply, boolean clearInput, TdApi.MessageSendOptions initialSendOptions) {
    if (hasSendMessagePermission(RightId.SEND_OTHER_MESSAGES)) {
      pickDateOrProceed(initialSendOptions, (modifiedSendOptions, disableMarkdown) -> {
        tdlib.sendInlineQueryResult(chat.id, getMessageThreadId(), allowReply ? obtainReplyTo() : null, Td.newSendOptions(modifiedSendOptions, obtainSilentMode()), inlineQueryId, id);
        if (clearInput) {
          inputView.setInput("", false, true);
          inputView.getInlineSearchContext().resetInlineBotsCache();
        }
      });
    }
  }

  public void sendAudio (TdApi.Audio audio, boolean allowReply) {
    if (hasSendMessagePermission(RightId.SEND_AUDIO)) {
      pickDateOrProceed(Td.newSendOptions(), (modifiedSendOptions, disableMarkdown) -> {
        tdlib.sendMessage(chat.id, getMessageThreadId(), allowReply ? obtainReplyTo() : null, Td.newSendOptions(modifiedSendOptions, obtainSilentMode()), TD.toInputMessageContent(audio), null);
      });
    }
  }

  public void sendMusic (View view, List<MediaBottomFilesController.MusicEntry> musicFiles, boolean needGroupMedia, boolean allowReply, TdApi.MessageSendOptions initialSendOptions) {
    sendMusic(view, musicFiles, needGroupMedia, allowReply, null, initialSendOptions);
  }

  public void sendMusic (View view, List<MediaBottomFilesController.MusicEntry> musicFiles, boolean needGroupMedia, boolean allowReply, @Nullable TdApi.FormattedText lastFileCaption, TdApi.MessageSendOptions initialSendOptions) {
    List<TdApi.Function<?>> functions = getSendMusicFunctions(view, musicFiles, needGroupMedia, allowReply, lastFileCaption, initialSendOptions);
    if (functions == null) {
      return;
    }
    for (TdApi.Function<?> function : functions) {
      tdlib.client().send(function, tdlib.messageHandler());
    }
  }

  private List<TdApi.Function<?>> getSendMusicFunctions (View view, List<MediaBottomFilesController.MusicEntry> musicFiles, boolean needGroupMedia, boolean allowReply, @Nullable TdApi.FormattedText lastFileCaption, TdApi.MessageSendOptions initialSendOptions) {
    if (!showSlowModeRestriction(view, initialSendOptions) && !showRestriction(view, RightId.SEND_AUDIO)) {
      TdApi.InputMessageContent[] content = new TdApi.InputMessageContent[musicFiles.size()];
      for (int i = 0; i < content.length; i++) {
        final TdApi.FormattedText caption = i == content.length - 1 ? lastFileCaption : null;
        MediaBottomFilesController.MusicEntry musicFile = musicFiles.get(i);
        content[i] = tdlib.filegen().createThumbnail(new TdApi.InputMessageAudio(TD.createInputFile(musicFile.getPath(), musicFile.getMimeType()), null, (int) (musicFile.getDuration() / 1000l), musicFile.getTitle(), musicFile.getArtist(), caption), isSecretChat());
      }
      TdApi.MessageSendOptions finalSendOptions = Td.newSendOptions(initialSendOptions, obtainSilentMode());
      return TD.toFunctions(chat.id, getMessageThreadId(), allowReply ? obtainReplyTo() : null, finalSendOptions, content, needGroupMedia);
    }
    return null;
  }

  public void forwardMessage (TdApi.Message message) { // TODO remove all related to Forward stuff to replace with ShareLayout
    if (tdlib.getRestrictionText(chat, message) == null) {
      tdlib.forwardMessage(chat.id, getMessageThreadId(), message.chatId, message.id, Td.newSendOptions(obtainSilentMode()));
    }
  }

  // Attach

  private boolean openingMediaLayout;

  private void openMediaView (boolean ignorePermissionRequest, boolean noMedia) {
    if (openingMediaLayout) {
      return;
    }

    if (!ignorePermissionRequest && context().permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES_AND_VIDEOS, grantType -> {
      openMediaView(true, grantType == Permissions.GrantResult.NONE);
    })) {
      return;
    }

    final MediaLayout mediaLayout;

    mediaLayout = new MediaLayout(this);
    mediaLayout.initDefault(this);
    if (noMedia) {
      mediaLayout.setNoMediaAccess();
    }

    openingMediaLayout = true;
    mediaLayout.preload(() -> {
      if (isFocused() && !isDestroyed()) {
        mediaLayout.show();
      }
      openingMediaLayout = false;
    }, 300l);

    // mediaLayout.show();
  }

  /*@Override
  public void onSendPaths (String[] paths, boolean areFiles) {
    if (areFiles) {
      for (String path : paths) {
        sendFile(path, true);
      }
    } else {
      for (String path : paths) {
        sendPhotoCompressed(path, 0, true);
      }
    }
  }*/

  @Override
  public void onRequestPermissionResult (int requestCode, boolean success) {
    switch (requestCode) {
      case BaseActivity.REQUEST_FINE_LOCATION: {
        if (success) {
          // TODO
        } else {
          openMissingLocationPermissionAlert(false);
        }
        break;
      }
    }
  }

  /*@Override
  public void onActivityResultCancel (int requestCode, int resultCode) {
    switch (requestCode) {
      case Intents.ACTIVITY_RESULT_RESOLUTION: {
        // getCustomCurrentLocation(currentShareLocationDestroyKeyboard, false, false);
        break;
      }
    }
  }*/

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode != Activity.RESULT_OK) {
      return;
    }
    switch (requestCode) {
      case Intents.ACTIVITY_RESULT_RESOLUTION: {
        getCurrentLocation(currentShareLocationDestroyKeyboard, googleClient);
        break;
      }
      case Intents.ACTIVITY_RESULT_IMAGE_CAPTURE:
      case Intents.ACTIVITY_RESULT_VIDEO_CAPTURE: {
        File file = Intents.takeLastOutputMedia();
        boolean isVideo = requestCode == Intents.ACTIVITY_RESULT_VIDEO_CAPTURE;
        if (showSlowModeRestriction(mediaButton, null) || showRestriction(mediaButton, isVideo ? RightId.SEND_VIDEOS : RightId.SEND_PHOTOS)) {
          return;
        }
        if (file != null) {
          if (!isSecretChat()) {
            U.addToGallery(file);
          }
          U.toGalleryFile(file, isVideo, galleryFile -> {
            if (isDestroyed()) {
              ImageStrictCache.instance().forget(galleryFile);
              return;
            }
            if (galleryFile == null) {
              UI.showToast(requestCode == Intents.ACTIVITY_RESULT_VIDEO_CAPTURE ? R.string.TakeVideoError : R.string.TakePhotoError, Toast.LENGTH_SHORT);
              return;
            }
            MediaStack stack = new MediaStack(context, tdlib);
            stack.set(new MediaItem(context, tdlib, galleryFile));
            MediaViewController controller = new MediaViewController(context, tdlib);
            AtomicBoolean hideMedia = new AtomicBoolean(false);
            controller.setArguments(
              MediaViewController.Args.fromGallery(this, null, null,
                new MediaSpoilerSendDelegate() {
                  @Override
                  public boolean sendSelectedItems (View view, ArrayList<ImageFile> images, TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles, boolean showCaptionAboveMedia, boolean hasSpoiler) {
                    sendPhotosAndVideosCompressed(new ImageGalleryFile[] {galleryFile}, false, options, disableMarkdown, asFiles, showCaptionAboveMedia, hasSpoiler);
                    return true;
                  }
                },
                stack, areScheduledOnly()
              ).setReceiverChatId(getChatId())
               .setFlag(MediaViewController.Args.FLAG_DELETE_FILE_ON_EXIT, isSecretChat() || !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_KEEP_DISCARDED_MEDIA)));
            controller.open();
          });
        }

        break;
      }
      case Intents.ACTIVITY_RESULT_GALLERY:
      case Intents.ACTIVITY_RESULT_GALLERY_FILE: {
        final Uri path = data != null ? data.getData() : null;
        if (path == null) break;
        String imagePath = U.tryResolveFilePath(path);

        if (inPreviewMode) {
          if (previewMode == PREVIEW_MODE_WALLPAPER) {
            tdlib.settings().setWallpaper(new TGBackground(tdlib, imagePath), true, Theme.getWallpaperIdentifier());
            return;
          }
          return;
        }

        if (imagePath != null && imagePath.endsWith(".webp") && tdlib.getRestrictionStatus(chat, RightId.SEND_OTHER_MESSAGES) == null) {
          sendSticker(imagePath, true, Td.newSendOptions());
        } else if (requestCode == Intents.ACTIVITY_RESULT_GALLERY_FILE) {
          sendFiles(mediaButton, Collections.singletonList(imagePath), false, true, Td.newSendOptions());
        } else {
          sendPhotoCompressed(imagePath, null, true);
        }

        break;
      }
      case Intents.ACTIVITY_RESULT_AUDIO: {
        final Uri path = data.getData();
        if (path == null) break;
        if (showSlowModeRestriction(mediaButton, null) || showRestriction(mediaButton, RightId.SEND_AUDIO)) {
          return;
        }
        final String audioPath = U.tryResolveFilePath(path);
        if (audioPath != null) {
          final long chatId = chat.id;
          final boolean disableNotification = obtainSilentMode();
          final TdApi.InputMessageReplyTo replyTo = obtainReplyTo();
          Background.instance().post(() -> {
            AudioFile file;

            file = new AudioFile(audioPath);
            file.loadId3Tags();

            tdlib.sendMessage(chatId, getMessageThreadId(), replyTo, Td.newSendOptions(disableNotification), new TdApi.InputMessageAudio(TD.createInputFile(audioPath), null, file.getDuration(), file.getTitle(), file.getPerformer(), null));
          });
        }
        break;
      }
    }
  }

  public void sendFiles (View view, final List<String> paths, boolean needGroupMedia, boolean allowReply, TdApi.MessageSendOptions initialSendOptions) {
    sendFiles(view, paths, needGroupMedia, allowReply, null, initialSendOptions);
  }

  public void sendFiles (View view, final List<String> paths, boolean needGroupMedia, boolean allowReply, @Nullable TdApi.FormattedText lastFileCaption, TdApi.MessageSendOptions initialSendOptions) {
    sendFiles(view, paths, needGroupMedia, allowReply, lastFileCaption, initialSendOptions, functions -> {
      if (functions == null) {
        return;
      }
      for (TdApi.Function<?> function : functions) {
        tdlib.client().send(function, tdlib.messageHandler());
      }
    });
  }

  private void sendFiles (View view, final List<String> paths, boolean needGroupMedia, boolean allowReply, @Nullable TdApi.FormattedText lastFileCaption, TdApi.MessageSendOptions initialSendOptions, RunnableData<List<TdApi.Function<?>>> onReadyToSend) {
    if (showSlowModeRestriction(view, initialSendOptions)) {
      onReadyToSend.runWithData(null);
      return;
    }

    final long chatId = chat.id;
    final boolean isSecretChat = isSecretChat();
    final TdApi.MessageSendOptions finalSendOptions = Td.newSendOptions(initialSendOptions, obtainSilentMode());
    final TdApi.InputMessageReplyTo replyTo = allowReply ? obtainReplyTo() : null;
    boolean allowAudio = tdlib.getRestrictionStatus(chat, RightId.SEND_AUDIO) == null;
    boolean allowDocs = tdlib.getRestrictionStatus(chat, RightId.SEND_DOCS) == null;
    boolean allowVideos = tdlib.getRestrictionStatus(chat, RightId.SEND_VIDEOS) == null;
    boolean allowGifs = tdlib.getRestrictionStatus(chat, RightId.SEND_OTHER_MESSAGES) == null;
    Media.instance().post(() -> {
      boolean restrictionFailed = false;
      List<TdApi.InputMessageContent> content = new ArrayList<>();
      for (int a = 0; a < paths.size(); a++) {
        final String path = paths.get(a);
        final boolean isLast = a == paths.size() - 1;
        final TdApi.FormattedText caption = isLast ? lastFileCaption : null;
        final boolean showCaptionAboveMedia = false; // FIXME: showCaptionAboveMedia
        TD.FileInfo info = new TD.FileInfo();
        TdApi.InputFile inputFile = TD.createInputFile(path, null, info);
        TdApi.InputMessageContent inputMessageContent = TD.toInputMessageContent(path, inputFile, info, caption, showCaptionAboveMedia, allowAudio, allowGifs, allowVideos, allowDocs, false);
        if (inputMessageContent == null) {
          restrictionFailed = true;
          break;
        }
        content.add(inputMessageContent);
      }
      if (restrictionFailed) {
        runOnUiThreadOptional(() -> {
          showRestriction(view, RightId.SEND_DOCS);
          onReadyToSend.runWithData(null);
        });
        return;
      }
      for (int i = 0; i < content.size(); i++) {
        TdApi.InputMessageContent inputMessageContent = content.get(i);
        content.set(i, tdlib.filegen().createThumbnail(inputMessageContent, isSecretChat));
      }

      List<TdApi.Function<?>> functions = TD.toFunctions(chatId, getMessageThreadId(), replyTo, finalSendOptions, content.toArray(new TdApi.InputMessageContent[0]), needGroupMedia);
      UI.post(() -> onReadyToSend.runWithData(functions));
    });
  }

  public void sendPhotoCompressed (final String path, final @Nullable TdApi.MessageSelfDestructType selfDestructType, final boolean allowReply) {
    if (showSlowModeRestriction(mediaButton, null) || showRestriction(mediaButton, RightId.SEND_PHOTOS)) {
      return;
    }
    if (StringUtils.isEmpty(path)) {
      return;
    }
    if (path != null) {
      final long chatId = chat.id;
      final TdApi.InputMessageReplyTo replyTo = allowReply ? obtainReplyTo() : null;
      final boolean silent = obtainSilentMode();
      final boolean isSecret = isSecretChat();

      Media.instance().post(() -> {
        BitmapFactory.Options opts = ImageReader.getImageSize(path);
        int orientation = U.getExifOrientation(path);
        int inSampleSize = ImageReader.calculateInSampleSize(opts, 1280, 1280);
        int sampledWidth = opts.outWidth / inSampleSize;
        int sampledHeight = opts.outHeight / inSampleSize;
        int width, height;
        if (U.isExifRotated(orientation)) {
          width = sampledHeight;
          height = sampledWidth;
        } else {
          width = sampledWidth;
          height = sampledHeight;
        }
        TdApi.InputFileGenerated inputFile = PhotoGenerationInfo.newFile(path, U.getRotationForExifOrientation(orientation));
        TdApi.InputMessagePhoto photo = tdlib.filegen().createThumbnail(new TdApi.InputMessagePhoto(inputFile, null, null, width, height, null, false, selfDestructType, false), isSecret);
        tdlib.sendMessage(chatId, getMessageThreadId(), replyTo, Td.newSendOptions(silent), photo);
      });
    }
  }

  public boolean sendPhotosAndVideosCompressed (final ImageGalleryFile[] files, final boolean needGroupMedia, final TdApi.MessageSendOptions options, boolean disableMarkdown, boolean asFiles, boolean showCaptionAboveMedia, boolean hasSpoiler) {
    if (files == null || files.length == 0) {
      return false;
    }

    // TODO check RightId.SEND_PHOTOS / RightId.SEND_VIDEOS

    final long chatId = chat.id;
    final TdApi.InputMessageReplyTo replyTo = obtainReplyTo();
    final boolean isSecretChat = isSecretChat();

    Media.instance().post(() -> {
      final TdApi.InputMessageContent[] inputContent = new TdApi.InputMessageContent[files.length];
      int i = 0;
      for (ImageGalleryFile file : files) {
        if (file.getSelfDestructType() != null && asFiles)
          throw new IllegalArgumentException();
        TdApi.InputMessageContent content;
        if (file.isVideo()) {
          boolean sendAsAnimation = file.shouldMuteVideo();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            MediaMetadataRetriever retriever = null;
            try {
              retriever = U.openRetriever(file.getFilePath());
              if (!sendAsAnimation) {
                String hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
                if (StringUtils.isEmpty(hasAudioStr) || !StringUtils.equalsOrBothEmpty(hasAudioStr.toLowerCase(), "yes")) {
                  sendAsAnimation = true;
                }
              }
              String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
              if (StringUtils.isNumeric(rotation)) {
                file.setRotation(StringUtils.parseInt(rotation));
              }
            } catch (Throwable ignored) {
              // Doing nothing
            }
            U.closeRetriever(retriever);
          }

          int[] size = new int[2];
          file.getOutputSize(size);

          final int width = size[0];
          final int height = size[1];

          final TD.FileInfo fileInfo = new TD.FileInfo();
          boolean forceVideo = Config.USE_VIDEO_COMPRESSION && !(asFiles && VideoGenerationInfo.isEmpty(file));
          final TdApi.InputFile inputVideo = forceVideo ? VideoGenerationInfo.newFile(file.getFilePath(), file, asFiles) : TD.createInputFile(file.getFilePath(), null, fileInfo);
          TdApi.FormattedText caption = file.getCaption(true, !disableMarkdown);
          if (asFiles && !forceVideo) {
            content = tdlib.filegen().createThumbnail(TD.toInputMessageContent(file.getFilePath(), inputVideo, fileInfo, caption, showCaptionAboveMedia, hasSpoiler), isSecretChat);
          } else if (sendAsAnimation && file.getSelfDestructType() == null && (files.length == 1 || !needGroupMedia)) {
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageAnimation(inputVideo, null, null, file.getVideoDuration(true), width, height, caption, showCaptionAboveMedia, hasSpoiler), isSecretChat);
          } else {
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageVideo(inputVideo, null, null, 0, null, file.getVideoDuration(true), width, height, U.canStreamVideo(inputVideo), caption, showCaptionAboveMedia, file.getSelfDestructType(), hasSpoiler), isSecretChat);
          }
        } else {
          int[] size = new int[2];
          file.getOutputSize(size);

          final int width = size[0];
          final int height = size[1];

          final TdApi.InputFile inputFile;
          if (asFiles && PhotoGenerationInfo.isEmpty(file)) {
            inputFile = TD.createInputFile(file.getFilePath());
          } else {
            inputFile = PhotoGenerationInfo.newFile(file);
          }

          TdApi.FormattedText caption = file.getCaption(true, !disableMarkdown);

          if (asFiles) {
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessageDocument(inputFile, null, false, caption), isSecretChat);
          } else {
            content = tdlib.filegen().createThumbnail(new TdApi.InputMessagePhoto(inputFile, null, null, width, height, caption, showCaptionAboveMedia, file.getSelfDestructType(), hasSpoiler), isSecretChat);
          }
        }
        inputContent[i] = content;
        i++;
      }
      List<TdApi.Function<?>> functions = TD.toFunctions(chatId, getMessageThreadId(), replyTo, options, inputContent, needGroupMedia);
      for (TdApi.Function<?> function : functions) {
        tdlib.client().send(function, tdlib.messageHandler());
      }
    });

    return true;
  }

  /*private void uploadFile (boolean isSecretChat, TdApi.InputMessageContent content) {
    if (true) {
      // FIXME TDLib uploadFile
      return;
    }
    TdApi.FileType fileType;
    TdApi.InputFile inputFile;
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        fileType = new TdApi.FileTypePhoto();
        inputFile = isSecretChat ? null : ((TdApi.InputMessagePhoto) content).photo;
        break;
      case TdApi.InputMessageAnimation.CONSTRUCTOR:
        fileType = new TdApi.FileTypeAnimation();
        inputFile = isSecretChat ? null : ((TdApi.InputMessageAnimation) content).animation;
        break;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        fileType = new TdApi.FileTypeVideo();
        inputFile = isSecretChat ? null : ((TdApi.InputMessageVideo) content).video;
        break;
      default:
        return;
    }
    if (isSecretChat) {
      fileType = new TdApi.FileTypeSecret();
    }
    tdlib.client().send(new TdApi.UploadFile(inputFile, fileType, 2), tdlib.silentHandler());
  }*/

  // Typing utils

  private boolean broadcastingSomeAction;
  private CancellableRunnable broadcastActor;

  private void checkBroadcastingSomeAction () {
    boolean needBroadcast = broadcastingAction != 0 && context.getActivityState() == UI.State.RESUMED && !isDestroyed();
    if (this.broadcastingSomeAction != needBroadcast) {
      if (needBroadcast) {
        broadcastActor = new CancellableRunnable() {
          @Override
          public void act () {
            if (broadcastActor == this && broadcastingAction != 0) {
              setChatAction(broadcastingAction, true, false);
              tdlib.ui().postDelayed(this, 4500l);
            }
          }
        };
        tdlib.ui().postDelayed(broadcastActor, 4500l);
      } else {
        broadcastActor.cancel();
        broadcastActor = null;
      }
      this.broadcastingSomeAction = needBroadcast;
    }
  }

  private int broadcastingAction;

  @SuppressWarnings("WrongConstant")
  public void setBroadcastAction (@TdApi.ChatAction.Constructors int action) {
    if (action == TdApi.ChatActionCancel.CONSTRUCTOR) {
      action = 0;
    }
    boolean hadAction = this.broadcastingAction != 0;
    boolean hasAction = action != 0;
    if (hadAction != hasAction) {
      if (hasAction) {
        setChatAction(action, true, true);
      } else {
        setChatAction(broadcastingAction, false, false);
      }
      this.broadcastingAction = action;
      checkBroadcastingSomeAction();
    } else if (this.broadcastingAction != action) {
      if (actions != null) {
        actions.delete(broadcastingAction);
      }
      setChatAction(action, true, true);
      this.broadcastingAction = action;
    }
  }

  private SparseIntArray actions;
  private boolean lastActionCancelled;

  public void setChatAction (@TdApi.ChatAction.Constructors int action, boolean set, boolean force) {
    if (chat == null) {
      return;
    }
    if (actions == null) {
      actions = new SparseIntArray(5);
    }
    Tdlib.ResultHandler<TdApi.Ok> handler = (ok, error) -> {
      if (error != null && (error.code != 400 || !"Have no rights to send a message".equals(error.message))) {
        tdlib.okHandler().onResult(error);
      }
    };
    long messageThreadId;
    if (isEditingMessage()) {
      messageThreadId = editContext.getExistingMessage().messageThreadId;
    } else {
      messageThreadId = getMessageThreadId();
      if (messageThreadId == 0 && reply != null) {
        messageThreadId = reply.message.message.messageThreadId != 0 ? reply.message.message.messageThreadId : reply.message.message.id;
      }
    }
    if (set) {
      int time = (int) (SystemClock.uptimeMillis() / 1000l);
      if (time - actions.get(action) >= 4 || force || lastActionCancelled) {
        actions.put(action, time);
        tdlib.send(new TdApi.SendChatAction(chat.id, messageThreadId, null, Td.constructChatAction(action)), handler);
        lastActionCancelled = false;
      }
    } else {
      if (actions.get(action, 0) != 0) {
        actions.delete(action);
        tdlib.send(new TdApi.SendChatAction(chat.id, messageThreadId, null, new TdApi.ChatActionCancel()), handler);
        lastActionCancelled = true;
      }
    }
  }

  public void setTyping (boolean isTyping) {
    if (chat != null) {
      setChatAction(TdApi.ChatActionTyping.CONSTRUCTOR, isTyping, false);
    }
  }

  @Override
  public void onSectionInteracted (EmojiLayout layout, int emojiType, boolean interactionFinished) {
    notifyChoosingEmoji(emojiType, !interactionFinished);
  }

  public void notifyChoosingEmoji (int emojiType, boolean isChoosingEmoji) {
    if (chat != null) {
      @TdApi.ChatAction.Constructors int action;
      switch (emojiType) {
        case EmojiMediaType.STICKER:
          action = TdApi.ChatActionChoosingSticker.CONSTRUCTOR;
          if (suggestionYDiff > Screen.dp(50) || stickerPreviewIsVisible) {
            hideEmojiSuggestionsTemporarily();
          } else if (suggestionYDiff <= 0) {
            showEmojiSuggestionsIfTemporarilyHidden();
          }
          break;
        case EmojiMediaType.EMOJI:
          if (suggestionXDiff > Screen.dp(50)) {
            hideStickersSuggestionsTemporarily();
          } else if (suggestionXDiff <= 0) {
            showStickersSuggestionsIfTemporarilyHidden();
          }
          return;
        case EmojiMediaType.GIF:
        default:
          return;
      }
      setChatAction(action, isChoosingEmoji, false);
    }
  }

  // Audio utils

  @Override
  public MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter) {
    if (!needTabs() || pagerScrollPosition == MediaTabsAdapter.POSITION_MESSAGES) {
      return manager.collectMedias(fromMessageId, filter);
    } else {
      SharedBaseController<?> c = pagerContentAdapter != null ? pagerContentAdapter.cachedItems.get(pagerScrollPosition) : null;
      if (c instanceof MediaCollectorDelegate) {
        return ((MediaCollectorDelegate) c).collectMedias(fromMessageId, filter);
      }
    }
    return null;
  }

  @Override
  public void modifyMediaArguments (Object cause, MediaViewController.Args args) {
    if (!needTabs() || pagerScrollPosition == MediaTabsAdapter.POSITION_MESSAGES) {
      args.delegate = this;
    } else {
      SharedBaseController<?> c = pagerContentAdapter != null ? pagerContentAdapter.cachedItems.get(pagerScrollPosition) : null;
      if (c instanceof MediaCollectorDelegate) {
        ((MediaCollectorDelegate) c).modifyMediaArguments(cause, args);
      }
    }
  }

  @Override
  public MediaViewThumbLocation getTargetLocation (int indexInStack, MediaItem item) {
    if (!needTabs() || pagerScrollPosition == MediaTabsAdapter.POSITION_MESSAGES) {
      int i = manager.getAdapter().findMessageByMediaItem(item);
      if (i != -1) {
        View view = manager.getLayoutManager().findViewByPosition(i);
        if (view != null) {
          RecyclerView.ViewHolder holder = messagesView.getChildViewHolder(view);
          if (holder instanceof MessagesHolder && ((MessagesHolder.isMessageType(holder.getItemViewType())))) {
            TGMessage msg = manager.getAdapter().getMessage(i);
            // FIXME state with FOLLOW
            int offset = (int) messagesView.getTranslationY();
            return msg.getMediaThumbLocation(item.getSourceMessageId(), view, view.getTop() - getTopOffset(), messagesView.getBottom() - view.getBottom(), view.getTop() + HeaderView.getSize(true) + offset);
          }
        }
      }
    } else {
      SharedBaseController<?> c = pagerContentAdapter != null ? pagerContentAdapter.cachedItems.get(pagerScrollPosition) : null;
      if (c instanceof MediaViewDelegate) {
        return ((MediaViewDelegate) c).getTargetLocation(indexInStack, item);
      }
    }
    return null;
  }

  @Override
  public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) {
    if (!needTabs() || pagerScrollPosition == MediaTabsAdapter.POSITION_MESSAGES) {
      int i = manager.getAdapter().findMessageByMediaItem(item);
      if (i != -1) {
        TGMessage msg = manager.getAdapter().getMessage(i);
        msg.setMediaVisible(item, isVisible);
      }
    }
  }

  // Updates (new)

  public void subscribeToUpdates (long chatId) {
    tdlib.listeners().subscribeToChatUpdates(chatId, this);
    tdlib.singleUnreadReactionsManager().subscribeToUnreadSingleReactionUpdates(chatId, this);
    if (chatId != getHeaderChatId()) {
      tdlib.listeners().subscribeToChatUpdates(getHeaderChatId(), this);
    }
    tdlib.listeners().subscribeToSettingsUpdates(this);
    if (messageThread != null) {
      messageThread.addListener(this);
    }
    if (getChatId() == chatId) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          tdlib.cache().subscribeToUserUpdates(TD.getUserId(chat), this);
          break;
        }
        case TdApi.ChatTypeSecret.CONSTRUCTOR: {
          tdlib.cache().subscribeToUserUpdates(TD.getUserId(chat), this);
          tdlib.cache().subscribeToSecretChatUpdates(TD.getSecretChatId(chat), this);
          // tdlib.listeners().subscribeToChatUpdates(TD.userIdToChatId(TD.getUserId(chat)), privateChatListener);
          break;
        }
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          tdlib.cache().subscribeToSupergroupUpdates(((TdApi.ChatTypeSupergroup) chat.type).supergroupId, this);
          break;
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          tdlib.cache().subscribeToGroupUpdates(((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId, this);
          break;
        }
      }
    }
  }

  public void unsubscribeFromUpdates (long chatId) {
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
    tdlib.singleUnreadReactionsManager().unsubscribeFromUnreadSingleReactionUpdates(chatId, this);
    if (chatId != getHeaderChatId()) {
      tdlib.listeners().unsubscribeFromChatUpdates(getHeaderChatId(), this);
    }
    tdlib.listeners().unsubscribeFromSettingsUpdates(this);
    // tdlib.status().unsubscribeFromChatUpdates(chatId, this);
    if (messageThread != null) {
      messageThread.removeListener(this);
    }
    if (getChatId() == chatId) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          tdlib.cache().unsubscribeFromUserUpdates(TD.getUserId(chat), this);
          break;
        }
        case TdApi.ChatTypeSecret.CONSTRUCTOR: {
          tdlib.cache().unsubscribeFromUserUpdates(TD.getUserId(chat), this);
          tdlib.cache().unsubscribeFromSecretChatUpdates(TD.getSecretChatId(chat), this);
          // tdlib.listeners().unsubscribeFromChatUpdates(TD.userIdToChatId(TD.getUserId(chat)), privateChatListener);
          break;
        }
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          tdlib.cache().unsubscribeFromSupergroupUpdates(((TdApi.ChatTypeSupergroup) chat.type).supergroupId, this);
          break;
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          tdlib.cache().unsubscribeFromGroupUpdates(((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId, this);
          break;
        }
      }
    }
  }

  @Override
  public void onJoinRequestsDismissed (long chatId) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId && chat != null) {
        checkJoinRequests(chat.pendingJoinRequests);
      }
    });
  }

  @Override
  public void onJoinRequestsRestore (long chatId) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId && chat != null) {
        checkJoinRequests(chat.pendingJoinRequests);
      }
    });
  }

  @Override
  public void onChatPendingJoinRequestsChanged (long chatId, TdApi.ChatJoinRequestsInfo pendingJoinRequests) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId) {
        checkJoinRequests(pendingJoinRequests);
      }
    });
  }

  @Override
  public void onChatActionBarChanged (long chatId, TdApi.ChatActionBar actionBar) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId) {
        checkActionBar();
      }
    });
  }

  @Override
  public void onChatTopMessageChanged (final long chatId, @Nullable final TdApi.Message topMessage) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId) {
        if (topMessage == null) {
          manager.onMissedMessagesHintReceived();
        }
      }
    });
  }

  @Override
  public void onChatTitleChanged (final long chatId, final String title) {
    runOnUiThreadOptional(() -> {
      if (getHeaderChatId() == chatId) {
        headerCell.setTitle(title);
      }
    });
  }

  private boolean exitOnTransformFinish;

  @Override
  public void onChatHasScheduledMessagesChanged (long chatId, boolean hasScheduledMessages) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        if (scheduleButton != null && scheduleButton.setVisible(messageThread == null && tdlib.chatHasScheduled(chatId))) {
          commandButton.setTranslationX(scheduleButton.isVisible() ? 0 : scheduleButton.getLayoutParams().width);
          attachButtons.updatePivot();
        }
        if (areScheduled && !hasScheduledMessages) {
          forceFastAnimationOnce();
          if (inTransformMode()) {
            exitOnTransformFinish = true;
          } else {
            navigateBack();
          }
        }
      }
    });
  }

  @Override
  public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        updateBottomBar(true);
      }
    });
  }

  @Override
  public void onChatReadInbox(final long chatId, final long lastReadInboxMessageId, final int unreadCount, boolean availabilityChanged) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        updateCounters(true);
      }
    });
  }

  @Override
  public void onChatUnreadMentionCount(final long chatId, final int unreadMentionCount, boolean availabilityChanged) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        updateCounters(true);
      }
    });
  }

  @Override
  public void onChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId) {
        updateCounters(true);
      }
    });
  }

  @Override
  public void onUnreadSingleReactionUpdate (long chatId, @Nullable TdApi.UnreadReaction unreadReaction) {
    UI.execute(() -> {
      if (getChatId() == chatId) {
        updateCounters(true);
      }
    });
  }

  @Override
  public void onChatReadOutbox (final long chatId, final long lastReadOutboxMessageId) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId && messageThread == null) {
        manager.updateChatReadOutbox(lastReadOutboxMessageId);
      }
    });
  }

  @Override
  public void onChatReplyMarkupChanged (final long chatId, final long replyMarkupMessageId) {
    tdlib.ui().post(() -> {
      if (getChatId() == chatId && botHelper != null) {
        botHelper.updateReplyMarkup(chatId, replyMarkupMessageId);
      }
    });
  }

  @Override
  public void onChatDefaultDisableNotifications (long chatId, boolean defaultDisableNotifications) {
    tdlib.ui().post(() -> {
      if (Config.NEED_SILENT_BROADCAST && silentButton != null && getChatId() == chatId) {
        silentButton.setValue(defaultDisableNotifications);
        updateInputHint();
      }
    });
  }

  @Override
  public void onChatDraftMessageChanged (final long chatId, final @Nullable TdApi.DraftMessage draftMessage) {
    runOnUiThreadOptional(() -> {
      if (getChatId() == chatId && inputView != null && !inputView.textChangedSinceChatOpened() && !isSecretChat() && messageThread == null) {
        // Applying server chat draft changes only if text wasn't changed while chat was open
        updateDraftMessage(chatId, draftMessage);
      }
    });
  }

  private void updateDraftMessage (long chatId, @Nullable TdApi.DraftMessage draftMessage) {
    if (isEditingMessage()) {
      return;
    }
    TdApi.InputMessageReplyTo replyTo = draftMessage != null ? draftMessage.replyTo : null;
    if (replyTo == null || replyTo.getConstructor() == TdApi.InputMessageReplyToStory.CONSTRUCTOR) {
      closeReply(false, true);
    } else {
      long replyChatId, replyMessageId;
      TdApi.InputTextQuote replyQuote;
      switch (replyTo.getConstructor()) {
        case TdApi.InputMessageReplyToMessage.CONSTRUCTOR: {
          TdApi.InputMessageReplyToMessage replyToMessage = (TdApi.InputMessageReplyToMessage) replyTo;
          replyChatId = getChatId();
          replyMessageId = replyToMessage.messageId;
          replyQuote = replyToMessage.quote;
          break;
        }
        case TdApi.InputMessageReplyToExternalMessage.CONSTRUCTOR: {
          TdApi.InputMessageReplyToExternalMessage replyToExternalMessage = (TdApi.InputMessageReplyToExternalMessage) replyTo;
          replyChatId = replyToExternalMessage.chatId;
          replyMessageId = replyToExternalMessage.messageId;
          replyQuote = replyToExternalMessage.quote;
          break;
        }
        case TdApi.InputMessageReplyToStory.CONSTRUCTOR: // Unreachable
        default:
          Td.assertInputMessageReplyTo_acef6f3a();
          throw Td.unsupported(replyTo);
      }
      tdlib.send(new TdApi.GetMessage(replyChatId, replyMessageId), (remoteMessage, error) -> tdlib.send(new TdApi.GetMessageProperties(replyChatId, replyMessageId), (properties, error1) -> {
        runOnUiThreadOptional(() -> {
          if (getChatId() == chatId && Td.equalsTo(getDraftMessage(), draftMessage) && remoteMessage != null && properties != null) {
            showReply(new MessageWithProperties(remoteMessage, properties), replyQuote, false, false);
          } else {
            closeReply(false, true);
          }
        });
      }));
    }
    inputView.setDraft(draftMessage != null ? draftMessage.inputMessageText : null);
  }

  private TdApi.CanSendMessageToUserResult canSendMessageToUser;

  @UiThread
  private void checkCanSendMessagesToUser (boolean allowRemote) {
    if (chat != null && TD.isPrivateChat(chat.type) && !inPreviewMode && !isInForceTouchMode()) {
      TdApi.User user = tdlib.chatUser(chat);
      if (user != null && user.restrictsNewChats) {
        long userId = user.id;
        tdlib.send(new TdApi.CanSendMessageToUser(userId, true), (localCanSendMessageToUser, error) -> {
          if (localCanSendMessageToUser != null) {
            processCanSendMessagesToUser(userId, localCanSendMessageToUser);
          }
          if (allowRemote || error != null) {
            tdlib.send(new TdApi.CanSendMessageToUser(userId, false), (remoteCanSendMessageToUser, error1) -> {
              if (remoteCanSendMessageToUser != null) {
                processCanSendMessagesToUser(userId, remoteCanSendMessageToUser);
              }
            });
          }
        });
        return;
      }
    }
    processCanSendMessagesToUser(tdlib.chatUserId(chat), null);
  }

  private void processCanSendMessagesToUser (long userId, TdApi.CanSendMessageToUserResult result) {
    executeOnUiThreadOptional(() -> {
      if (getChatId() == ChatId.fromUserId(userId) && !Td.equalsTo(this.canSendMessageToUser, result)) {
        this.canSendMessageToUser = result;
        updateBottomBar(true);
      }
    });
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    if (chat != null && user != null && headerCell != null && TD.getUserId(chat) == user.id) {
      runOnUiThreadOptional(() -> {
        headerCell.setEmojiStatus(user);
        checkCanSendMessagesToUser(false);
      });
    }
  }

  @Override
  public void onUserFullUpdated (final long userId, final TdApi.UserFullInfo userFull) {
    tdlib.ui().post(() -> {
      if (chat != null && TD.getUserId(chat) == userId) {
        updateBottomBar(true);
      }
    });
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @UiThread
  @Override
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (chat != null && headerCell != null && TD.getUserId(chat) == userId) {
      headerCell.updateUserStatus(chat);
    }
  }

  /*@UiThread
  @Override
  public void onChatActionsChanged (long chatId, @Nullable ArrayList<int[]> actions) {
    if (chat != null && chat.id == chatId && headerCell != null) {
      headerCell.updateChatAction(chat, actions);
    }
  }*/

  @Override
  public void onSupergroupUpdated (final TdApi.Supergroup supergroup) {
    tdlib.ui().post(() -> {
      if (ChatId.toSupergroupId(getChatId()) == supergroup.id) {
        updateBottomBar(true);
      }
    });
  }

  @Override
  public void onSupergroupFullUpdated (final long supergroupId, final TdApi.SupergroupFullInfo newSupergroupFull) {
    tdlib.ui().post(() -> {
      if (ChatId.toSupergroupId(getHeaderChatId()) == supergroupId) {
        headerCell.updateUserStatus(chat);
      }
      if (ChatId.toSupergroupId(getChatId()) == supergroupId) {
        checkLinkedChat();
        if (messageSenderButton != null) {
          messageSenderButton.setInSlowMode(tdlib.inSlowMode(getChatId()));
        }
      }
    });
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    tdlib.ui().post(() -> {
      if (ChatId.toBasicGroupId(getChatId()) == basicGroup.id) {
        if (migratedToSupergroup) {
          long newChatId = ChatId.fromSupergroupId(basicGroup.upgradedToSupergroupId);
          tdlib.chat(newChatId, ignored -> tdlib.uiExecute(() -> {
            if (manager != null) {
              manager.destroy(this);
            }
            setArguments(new Arguments(tdlib, openedFromChatList, tdlib.chatStrict(newChatId), null, null));
          }));
        } else {
          updateBottomBar(true);
        }
      }
    });
  }

  @Override
  public void onBasicGroupFullUpdated (final long basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    tdlib.ui().post(() -> {
      if (chat != null && ChatId.toBasicGroupId(getHeaderChatId()) == basicGroupId) {
        headerCell.updateUserStatus(chat);
      }
    });
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    tdlib.ui().post(() -> {
      if (chat != null && getHeaderChatId() == chatId) {
        headerCell.updateUserStatus(chat);
      }
    });
  }

  @Override
  public void onChatMessageTtlSettingChanged (long chatId, int messageTtlSetting) {
    tdlib.ui().post(() -> {
      if (chat != null && chat.id == chatId) {
        tdlib.ui().updateTTLButton(R.id.menu_secretChat, headerView, chat, false);
      }
    });
  }

  @Override
  public void onSecretChatUpdated (final TdApi.SecretChat secretChat) {
    tdlib.ui().post(() -> {
      if (chat != null && TD.getSecretChatId(chat) == secretChat.id) {
        updateBottomBar(true);
        if (secretChat.state.getConstructor() == TdApi.SecretChatStateClosed.CONSTRUCTOR) {
          checkActionBar();
        }
      }
    });
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    tdlib.ui().post(() -> {
      if (Td.matchesScope(tdlib.chatType(getChatId()), scope)) {
        updateCounters(true);
      }
    });
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    tdlib.ui().post(() -> {
      if (getHeaderChatId() == chatId) {
        headerCell.setShowMute(TD.needMuteIcon(settings, tdlib.scopeNotificationSettings(chatId)));
      }
      if (getChatId() == chatId) {
        updateCounters(true);
      }
    });
  }

  // Raise to speak / listen utils

  private void registerRaiseListener () {
    if (canWriteMessages()) {
      // FIXME RaiseHelper.instance().register(this);
    }
  }

  private void unregisterRaiseListener () {
    // FIXME RaiseHelper.instance().unregister(this);
  }

  @Override
  public boolean enterRaiseMode () {
    final BaseActivity context = context();
    if (context.isPasscodeShowing()) {
      return false;
    }
    /*if (Player.instance().currentIsVoice()) {
      Player.instance().playIfRequested();
      return true;
    }*/
    ArrayList<TGAudio> audio = null; // collectAudios(true, true);
    if (audio != null && audio.size() != 0) {
      // Player.instance().playPause(audio.get(0), true);
      return true;
    }
    return context.getRecordAudioVideoController().enterRaiseRecordMode();
  }

  @Override
  public boolean leaveRaiseMode () {
    /*if (Player.instance().currentIsVoice()) {
      Player.instance().pauseIfPlaying();
    }*/
    context().getRecordAudioVideoController().leaveRaiseRecordMode();
    return true;
  }

  // Messages search

  private FrameLayoutFix searchControlsLayout;
  private RippleRevealView searchControlsReveal;
  private ImageView searchByButton;
  private ImageView searchJumpToDateButton;
  private TextView searchCounterView;
  private ProgressComponentView searchProgressView;
  private ImageView searchSetTypeFilterButton;
  private ImageView searchShowOnlyFoundButton;
  private AvatarView searchByAvatarView;

  private boolean canSetSearchFilteredMode () {
    return !isEventLog() && chat != null && (searchMessagesSender != null || searchMessagesFilterIndex != 0 || !StringUtils.isEmpty(getLastMessageSearchQuery()));
  }

  private boolean canSearchByUserId () {
    return !isEventLog() && chat != null && (tdlib.isMultiChat(chat.id) || tdlib.isUserChat(chat.id));
  }

  private void checkSearchByVisible () {
    setSearchByVisible(canSearchByUserId());
  }

  private void checkSearchFilteredModeButton (boolean animated) {
    boolean isVisible = canSetSearchFilteredMode();
    if (searchShowOnlyFoundButton != null && searchCounterView != null && searchProgressView != null) {
      searchShowOnlyFoundButton.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      searchCounterView.setTranslationX(isVisible ? 0 : Screen.dp(42.5f));
      searchProgressView.setTranslationX(isVisible ? 0 : Screen.dp(42.5f));
    }
    if (!isVisible) {
      onSetSearchFilteredShowMode(false);
    }
  }

  private void setSearchByVisible (boolean isVisible) {
    searchByButton.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    searchSetTypeFilterButton.setTranslationX(isVisible ? 0 : Screen.dp(-42.5f));
  }

  private TdApi.Function<?> jumpToDateRequest;

  private void cancelJumpToDate () {
    jumpToDateRequest = null;
  }

  private void jumpToDate () {
    TGMessage msg = manager.findBottomMessage();

    long currentTime = msg != null ? msg.getDate() * 1000l : System.currentTimeMillis();
    Calendar c = Calendar.getInstance();

    c.set(Calendar.YEAR, 2013);
    c.set(Calendar.MONTH, Calendar.AUGUST);
    c.set(Calendar.DAY_OF_MONTH, 14);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    long sourceMinDate = c.getTimeInMillis();

    long minDate = manager.getMinimumDate();
    long maxDate = System.currentTimeMillis(); // manager.getMaximumDate();

    if (minDate == -1) {
      minDate = sourceMinDate;
    } else {
      minDate *= 1000l;
    }

    /*if (maxDate == -1) {
      maxDate = System.currentTimeMillis();
    } else {
      maxDate *= 1000l;
    }*/


    c.setTimeInMillis(Math.max(minDate, Math.min(maxDate, currentTime)));

    int year = c.get(Calendar.YEAR);
    int monthOfYear = c.get(Calendar.MONTH);
    int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
    final DatePickerDialog datePickerDialog = new DatePickerDialog(context(), Theme.dialogTheme(), (view, year1, month, dayOfMonth1) -> {
      Calendar c1 = Calendar.getInstance();
      c1.set(Calendar.YEAR, year1);
      c1.set(Calendar.MONTH, month);
      c1.set(Calendar.DAY_OF_MONTH, dayOfMonth1);
      c1.set(Calendar.HOUR_OF_DAY, 0);
      c1.set(Calendar.MINUTE, 0);
      c1.set(Calendar.SECOND, 0);
      c1.set(Calendar.MILLISECOND, 0);
      jumpToDate((int) (c1.getTimeInMillis() / 1000l));
    }, year, monthOfYear, dayOfMonth);
    datePickerDialog.setButton(DialogInterface.BUTTON_NEUTRAL, Lang.getString(R.string.Beginning), (dialog, which) -> jumpToDate(0));

    datePickerDialog.getDatePicker().setMaxDate(maxDate);
    try {
      datePickerDialog.getDatePicker().setMinDate(minDate);
    } catch (Throwable ignored) {
      if (minDate != sourceMinDate) {
        try { datePickerDialog.getDatePicker().setMinDate(sourceMinDate); } catch (Throwable ignored1) { }
      }
    }

    ViewSupport.showDatePicker(datePickerDialog);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (needTabs()) {
      if (pagerContentView != null) {
        pagerContentView.checkRtl();
      }
    }
    if (inputView != null) {
      inputView.checkRtl();
    }
    Views.setScrollBarPosition(messagesView);
    if (wallpapersList != null) {
      ((LinearLayoutManager) wallpapersList.getLayoutManager()).setReverseLayout(Lang.rtl());
      wallpapersList.invalidateItemDecorations();
      ((WallpaperAdapter) wallpapersList.getAdapter()).centerWallpapers(false);
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    if (emojiLayout != null) {
      emojiLayout.onLanguagePackEvent(event, arg1);
    }
    if (isFocused() && !inPreviewMode()) {
      manager.rebuildLayouts();
      if (headerCell != null) {
        headerCell.setChat(tdlib, chat, messageThread);
        if (messageThread != null) {
          updateMessageThreadSubtitle();
        }
      }
    }
  }

  public void jumpToBeginningOfTheDay (int date) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis((long) date * 1000l);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    jumpToDate((int) (c.getTimeInMillis() / 1000l));
  }

  public void jumpToDate (int date) {
    final boolean inSearchMode = canSetSearchFilteredMode();
    if (date == 0) {
      MessageId messageId = new MessageId(getChatId(), MessageId.MIN_VALID_ID);
      jumpToDateRequest = null;
      setSearchInProgress(false, true);
      if (inSearchMode) {
        manager.searchMoveToMessage(messageId);
        return;
      }
      manager.highlightMessage(messageId, MessagesManager.HIGHLIGHT_MODE_NORMAL, null, true);
      return;
    }
    final TdApi.GetChatMessageByDate request = new TdApi.GetChatMessageByDate(getChatId(), 0);
    jumpToDateRequest = request;
    request.date = date;
    setSearchInProgress(true, true);
    tdlib.client().send(request, object -> {
      final MessageId messageId;
      final TdApi.Message message;
      if (object.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        message = (TdApi.Message) object;
        messageId = new MessageId(message.chatId, message.id);
      } else {
        message = null;
        messageId = new MessageId(getChatId(), MessageId.MIN_VALID_ID);
      }
      tdlib.ui().post(() -> {
        if (jumpToDateRequest == request) {
          jumpToDateRequest = null;
          setSearchInProgress(false, true);
          if (inSearchMode) {
            manager.searchMoveToMessage(messageId);
            return;
          }
          manager.highlightMessage(messageId, messageId.isHistoryStart() ? MessagesManager.HIGHLIGHT_MODE_NORMAL : MessagesManager.HIGHLIGHT_MODE_NORMAL_NEXT, null, true);
        }
      });
    });
  }

  private void initSearchControls () {
    if (searchControlsLayout != null) {
      return;
    }

    final View.OnClickListener onClickListener = v -> {
      final int viewId = v.getId();
      if (viewId == R.id.btn_search_setTypeFilter) {
        showSearchTypeOptions();
      } else if (viewId == R.id.btn_search_by) {
        showSearchByUserView(true, true);
      } else if (viewId == R.id.btn_search_counter) {
        // TODO open search by text
      } else if (viewId == R.id.btn_search_jump) {
        jumpToDate();
      } else if (viewId == R.id.btn_search_onlyResult) {
        toggleSearchFilteredShowMode();
      }
    };

    Context context = context();

    RelativeLayout.LayoutParams rp;
    FrameLayoutFix.LayoutParams fp;

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    rp.addRule(RelativeLayout.ALIGN_TOP, R.id.msg_bottom);
    rp.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.msg_bottom);

    searchControlsLayout = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        return getSearchTransformFactor() == 0f || super.onInterceptTouchEvent(ev);
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return getSearchTransformFactor() > 0f;
      }

      /*@Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
      }*/
    };
    searchControlsLayout.setMinimumHeight(Screen.dp(49f));
    searchControlsLayout.setLayoutParams(rp);

    searchControlsReveal = new RippleRevealView(context);
    searchControlsReveal.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    searchControlsLayout.addView(searchControlsReveal);
    addThemeInvalidateListener(searchControlsReveal);

    fp = FrameLayoutFix.newParams(Screen.dp(52f), Screen.dp(49f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
    fp.leftMargin = Screen.dp(42.5f);
    searchByButton = Views.newImageButton(context, R.drawable.baseline_person_24, ColorId.icon, this);
    searchByButton.setId(R.id.btn_search_by);
    searchByButton.setOnClickListener(onClickListener);
    searchByButton.setLayoutParams(fp);
    searchControlsLayout.addView(searchByButton);

    searchByAvatarView = new AvatarView(context);
    searchByAvatarView.setId(R.id.btn_search_by);
    searchByAvatarView.setOnClickListener(onClickListener);
    searchByAvatarView.setLayoutParams(fp);
    searchByAvatarView.setPadding(Screen.dp(16), Screen.dp(14.5f), Screen.dp(16), Screen.dp(14.5f));
    searchControlsLayout.addView(searchByAvatarView);

    fp = FrameLayoutFix.newParams(Screen.dp(52f), Screen.dp(49f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
    searchJumpToDateButton = Views.newImageButton(context, R.drawable.baseline_date_range_24, ColorId.icon, this);
    searchJumpToDateButton.setId(R.id.btn_search_jump);
    searchJumpToDateButton.setOnClickListener(onClickListener);
    searchJumpToDateButton.setLayoutParams(fp);
    searchControlsLayout.addView(searchJumpToDateButton);

    fp = FrameLayoutFix.newParams(Screen.dp(52f), Screen.dp(49f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
    fp.leftMargin = Screen.dp(42.5f * 2);
    searchSetTypeFilterButton = Views.newImageButton(context, R.drawable.baseline_filter_variant_remove_24, ColorId.icon, this);
    searchSetTypeFilterButton.setId(R.id.btn_search_setTypeFilter);
    searchSetTypeFilterButton.setOnClickListener(onClickListener);
    searchSetTypeFilterButton.setLayoutParams(fp);
    searchControlsLayout.addView(searchSetTypeFilterButton);

    fp = FrameLayoutFix.newParams(Screen.dp(52f), Screen.dp(49f), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    searchShowOnlyFoundButton = Views.newImageButton(context, R.drawable.baseline_text_search_variant_24, ColorId.icon, this);
    searchShowOnlyFoundButton.setId(R.id.btn_search_onlyResult);
    searchShowOnlyFoundButton.setOnClickListener(onClickListener);
    searchShowOnlyFoundButton.setPadding(0, 0, Screen.dp(12f), 0);
    searchShowOnlyFoundButton.setLayoutParams(fp);
    searchControlsLayout.addView(searchShowOnlyFoundButton);

    final int padding = Screen.dp(22f);
    fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    fp.rightMargin = Screen.dp(60);
    fp.leftMargin = Screen.dp(5f) + padding;
    searchCounterView = new NoScrollTextView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setPivotX(getMeasuredWidth() - padding);
        setPivotY(getMeasuredHeight() / 2);
      }
    };
    searchCounterView.setId(R.id.btn_search_counter);
    // TODO searchCounterView.setOnClickListener(onClickListener);
    searchCounterView.setSingleLine(true);
    searchCounterView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    searchCounterView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    searchCounterView.setTypeface(Fonts.getRobotoMedium());
    searchCounterView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(searchCounterView);
    searchCounterView.setLayoutParams(fp);
    searchControlsLayout.addView(searchCounterView);

    fp = FrameLayoutFix.newParams(Screen.dp(49f), Screen.dp(49f), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    fp.rightMargin = Screen.dp(60);
    searchProgressView = new ProgressComponentView(context);
    searchProgressView.initCustom(4.5f, 0f, 10f);
    searchProgressView.setLayoutParams(fp);
    searchProgressView.forceFactor(1f);
    searchControlsLayout.addView(searchProgressView);
    addThemeInvalidateListener(searchProgressView);

    setSearchInProgress(0f);
    setSearchControlsFactor(0f);
  }

  private boolean searchControlsForChannel;

  private void resetSearchControls () {
    resetSearchControls(false);
  }

  private void resetSearchControls (boolean animated) {
    lastMessageSearchQuery = "";
    lastMembersSearchQuery = "";
    onSetSearchTypeFilter(0);
    onSetSearchMessagesSenderId(null);
    onSetSearchFilteredShowMode(false);
    showSearchByUserView(false, animated);
    if (searchControlsLayout != null) {
      searchCounterView.setText("");
      setSearchInProgress(false, animated);
      checkSearchByVisible();
      checkSearchFilteredModeButton(animated);
      setSearchControlsFactor(0f);
      updateSearchNavigation();

      boolean isChannel = tdlib.isChannelChat(chat);
      if (searchControlsForChannel != isChannel) {
        searchControlsForChannel = isChannel;

        RelativeLayout.LayoutParams rp = (RelativeLayout.LayoutParams) searchControlsLayout.getLayoutParams();
        if (isChannel) {
          rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
          rp.addRule(RelativeLayout.ALIGN_BOTTOM, 0);
          rp.addRule(RelativeLayout.ALIGN_TOP, 0);
          rp.height = Screen.dp(48f);
        } else {
          rp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
          rp.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.msg_bottom);
          rp.addRule(RelativeLayout.ALIGN_TOP, R.id.msg_bottom);
          rp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        searchControlsLayout.setLayoutParams(rp);
      }
    }
    if (goToPrevFoundMessageButtonBadge != null && goToNextFoundMessageButtonBadge != null) {
      goToNextFoundMessageButtonBadge.setEnabled(false, animated);
      goToPrevFoundMessageButtonBadge.setEnabled(false, animated);
      goToNextFoundMessageButtonBadge.setInProgress(false);
      goToPrevFoundMessageButtonBadge.setInProgress(false);
    }
    searchNavigationButtonVisibleAnimator.setValue(false, animated);
  }

  private boolean searchInProgress;
  private FactorAnimator searchProgressAnimator;
  private static final int ANIMATOR_SEARCH_PROGRESS = 11;

  private boolean setSearchInProgress (boolean inProgress, boolean animated) {
    if (this.searchInProgress != inProgress || !animated) {
      boolean oldValue = this.searchInProgress;
      this.searchInProgress = inProgress;
      final float toFactor = inProgress ? 1f : 0f;
      if (animated) {
        if (searchProgressAnimator == null) {
          searchProgressAnimator = new FactorAnimator(ANIMATOR_SEARCH_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 90l, this.searchInProgressFactor);
        }
        searchProgressAnimator.animateTo(toFactor);
      } else {
        if (searchProgressAnimator != null) {
          searchProgressAnimator.forceFactor(toFactor);
        }
        setSearchInProgress(toFactor);
      }
      return oldValue != inProgress;
    }
    return false;
  }

  private float searchInProgressFactor = -1f;

  private void setSearchInProgress (float factor) {
    if (this.searchInProgressFactor != factor) {
      this.searchInProgressFactor = factor;
      updateSearchNavigation();
    }
  }

  private void updateSearchNavigation () {
    if (searchControlsLayout != null) {
      float maxAlpha = searchControlsFactor;

      float counterAlpha = maxAlpha * (1f - searchInProgressFactor);
      float counterScale = .6f + .4f * (1f - searchInProgressFactor);
      float progressAlpha = maxAlpha * searchInProgressFactor;
      float progressScale = .6f + .4f * searchInProgressFactor;

      searchCounterView.setAlpha(counterAlpha);
      searchCounterView.setScaleX(counterScale);
      searchCounterView.setScaleY(counterScale);

      searchProgressView.setAlpha(progressAlpha);
      searchProgressView.setScaleX(progressScale);
      searchProgressView.setScaleY(progressScale);
    }
  }

  private float searchControlsFactor = -1f;

  private void setSearchControlsFactor (float factor) {
    setBadgesButtonsTranslationX(factor, searchNavigationButtonVisibleAnimator.getFloatValue());
    updateBottomBarStyle();
    if (this.searchControlsLayout != null) {
      if (searchControlsFactor != factor) {
        searchControlsFactor = factor;
        searchJumpToDateButton.setAlpha(factor);
        searchByButton.setAlpha(factor);
        searchShowOnlyFoundButton.setAlpha(factor);
        searchByAvatarView.setAlpha(factor);
        searchSetTypeFilterButton.setAlpha(factor);
        updateSearchNavigation();
      }
      boolean translate = needSearchControlsTranslate();
      searchControlsReveal.setRevealFactor(translate ? 1f : factor);
      searchControlsLayout.setTranslationY(translate ? Screen.dp(49f) * (1f - factor) : 0f);
      if (translate) {
        checkScrollButtonOffsets();
      }
    }
  }

  private boolean needSearchControlsTranslate () {
    return tdlib.isChannelChat(chat) && !canWriteMessages();
  }

  private float getSearchControlsOffset () {
    return needSearchControlsTranslate() && searchControlsFactor != -1f ? Screen.dp(49f) * searchControlsFactor : 0f;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  private static final float DISABLED_BUTTON_ALPHA = .6f;

  @Override
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    super.applySearchTransformFactor(factor, isOpening);
    topBar.setGlobalVisibility(1f - factor, false);
    updateReplyView();
    setSearchControlsFactor(factor);
    // checkBottomOffsetFactor();
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    checkAttachedFiles(true);
    manager.onPrepareToSearch();
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.prepare();
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    if (searchFromUserMessageId != null) {
      manager.setHighlightMessageId(searchFromUserMessageId, MessagesManager.HIGHLIGHT_MODE_WITHOUT_HIGHLIGHT);
    }
    onSetSearchFilteredShowMode(false);
    manager.onDestroySearch();
    manager.getAdapter().checkAllMessages();
    searchMedia(null);
  }

  @Override
  protected int getHeaderColorId () {
    if (previewSearchSender != null) {
      return ColorId.filling;
    }
    return super.getHeaderColorId();
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    super.onAfterLeaveSearchMode();
    checkAttachedFiles(true);
    resetSearchControls(true);
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.dismiss();
    }
  }

  private void moveSearchSelection (boolean next) {
    manager.moveToNextResult(next);
  }

  // Search counter

  private boolean showingSearchIndex;
  private int lastSearchIndex = -1, lastSearchTotalCount = -1;

  public void onChatSearchStarted () {
    if (setSearchInProgress(true, true)) {
      lastSearchIndex = lastSearchTotalCount = -1;
    }
  }

  public void onChatSearchAwaitNext (boolean next) {
    goToNextFoundMessageButtonBadge.setInProgress(next);
    goToPrevFoundMessageButtonBadge.setInProgress(!next);
  }

  public void onChatSearchFinished (String counter, int index, int totalCount) {
    lastSearchIndex = index;
    lastSearchTotalCount = totalCount;
    searchCounterView.setText(counter);
    setSearchInProgress(false, true);
    updateSearchNavigation();
    goToNextFoundMessageButtonBadge.setEnabled(manager.canSearchNext(), true);
    goToPrevFoundMessageButtonBadge.setEnabled(manager.canSearchPrev(), true);
    goToNextFoundMessageButtonBadge.setInProgress(false);
    goToPrevFoundMessageButtonBadge.setInProgress(false);
    searchNavigationButtonVisibleAnimator.setValue((index < totalCount) || (index > 0), true);
    manager.getAdapter().checkAllMessages();
  }

  private String lastMediaSearchQuery;

  private void searchMedia (String query) {
    if (pagerContentAdapter != null && !StringUtils.equalsOrBothEmpty(lastMediaSearchQuery, query)) {
      lastMediaSearchQuery = query;
      final int size = pagerContentAdapter.cachedItems.size();
      for (int i = 0; i < size; i++) {
        SharedBaseController<?> c = pagerContentAdapter.cachedItems.valueAt(i);
        if (c != null) {
          c.search(query);
        }
      }
    }
  }

  private void clearChatSearchInput () {
    clearSearchInput();
    // TODO clear input on first tap, clear "From: " on second tap
  }

  private boolean triggerOneShot;

  @Override
  protected boolean allowLeavingSearchMode () {
    return triggerOneShot;
  }

  @Override
  protected void modifySearchHeaderView (HeaderEditText headerEditText) {
    headerEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    int padding = Screen.dp(112f);
    if (Lang.rtl()) {
      headerEditText.setPadding(padding, headerEditText.getPaddingTop(), headerEditText.getPaddingRight(), headerEditText.getPaddingBottom());
    } else {
      headerEditText.setPadding(headerEditText.getPaddingLeft(), headerEditText.getPaddingTop(), padding, headerEditText.getPaddingBottom());
    }
  }

  // Locale change


  /*TODO LANG
  @Override
  public void onLocaleChange () {
    super.onLocaleChange();
    if (chat != null) {
      if (headerCell != null) {
        headerCell.setChat(tdlib, chat);
      }
    }
    if (manager != null) {
      MessagesAdapter adapter = manager.getAdapter();
      if (adapter != null && adapter.updateLocale()) {
        messagesView.invalidateAll();
      }
    }
  }*/

  // Force preview

  @Override
  public void onPrepareForceTouchContext (ForceTouchView.ForceTouchContext context) {
    ThreadInfo messageThread = getMessageThread();
    long boundChatId = messageThread != null ? messageThread.getContextChatId() : getChatId();
    context.setIsMatchParent(true);
    context.setBoundChatId(boundChatId, messageThread);
    context.setAllowFullscreen(true);
    context.setAnimationType(ForceTouchView.ForceTouchContext.ANIMATION_TYPE_EXPAND_VERTICALLY);
  }

  public static boolean maximizeFrom (final Tdlib tdlib, final Context context, final FactorAnimator target, final float animateToWhenReady, final MessagesController controller, final RunnableData<MessagesController> modifier) {
    MessagesController c = new MessagesController(context, tdlib);
    c.setArguments(controller.getArgumentsStrict());
    c.forceFastAnimationOnce();
    if (modifier != null) {
      modifier.runWithData(c);
    }
    c.postOnAnimationReady(() -> target.animateTo(animateToWhenReady));
    UI.getContext(context).navigation().navigateTo(c);
    return true;
  }




  public @Nullable TdApi.MessageSender getChatMessageSender () {
    return chat.messageSenderId;
  }

  public boolean canSelectSender () {
    return chat != null && chat.messageSenderId != null;
  }

  public boolean isCameraButtonVisibleOnAttachPanel () {
    return !canSelectSender();
  }

  public HapticMenuHelper.MenuItem createHapticSenderItem (int id, TdApi.MessageSender sender, boolean useUsername, boolean isLocked) {
    String title = useUsername ? tdlib.senderName(sender) : Lang.getString(R.string.SendAs);
    if (tdlib.isSelfSender(sender)) {
      return new HapticMenuHelper.MenuItem(id, title, Lang.getString(R.string.YourAccount), R.drawable.dot_baseline_acc_personal_24, tdlib, sender, false);
    } else if (!tdlib.isChannel(sender)) {
      return new HapticMenuHelper.MenuItem(id, title, Lang.getString(R.string.AnonymousAdmin), R.drawable.dot_baseline_acc_anon_24, tdlib, sender, false);
    } else {
      String username = tdlib.chatUsername(Td.getSenderId(sender));
      String subtitle = useUsername && !StringUtils.isEmpty(username) ? ("@" + username) : tdlib.getMessageSenderTitle(sender);
      return new HapticMenuHelper.MenuItem(id, title, subtitle, 0, tdlib, sender, isLocked);
    }
  }

  // Call methods

  public static void getChatAvailableMessagesSenders (Tdlib tdlib, long chatId, @NonNull RunnableData<TdApi.ChatMessageSenders> callback) {
    tdlib.send(new TdApi.GetChatAvailableMessageSenders(chatId), (result, error) -> UI.post(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        callback.runWithData(result);
      }
    }));
  }

  public static void setNewMessageSender (Tdlib tdlib, long chatId, TdApi.ChatMessageSender sender, @Nullable Runnable after) {
    tdlib.send(new TdApi.SetChatMessageSender(chatId, sender.sender), tdlib.typedOkHandler(after));
  }

  private void setNewMessageSender (TdApi.ChatMessageSender sender) {
    tdlib.send(new TdApi.SetChatMessageSender(getChatId(), sender.sender), tdlib.typedOkHandler());
  }

  private void setNewMessageSender (TdApi.MessageSender sender) {
    tdlib.send(new TdApi.SetChatMessageSender(getChatId(), sender), tdlib.typedOkHandler());
  }

  private void getChatAvailableMessagesSenders (Runnable after) {
    getChatAvailableMessagesSenders(tdlib, getChatId(), result -> {
      onUpdateChatAvailableMessagesSenders(result.senders);
      if (after != null) {
        tdlib.ui().post(after);
      }
    });
  }


  // Interface

  private void updateSelectMessageSenderInterface (boolean animated) {
    boolean cameraVisible = isCameraButtonVisibleOnAttachPanel();
    boolean canSetSender = canSelectSender();
    if (cameraButton != null) {
      cameraButton.setVisibility(cameraVisible ? View.VISIBLE : View.GONE);  //.setVisible(cameraVisible);
    }

    messageSenderButton.setVisibility((canSetSender && attachButtons.getVisibility() == View.VISIBLE) ? View.VISIBLE : View.GONE);
    if (canSetSender) {
      messageSenderButton.update(getChatMessageSender(), animated);
    }
  }


  private CancellableRunnable clearCurrentNonAnonymousActionHash;
  private long currentNonAnonymousActionHash;


  public boolean callNonAnonymousProtection (long hash, TGMessage message, @NonNull TooltipOverlayView.LocationProvider locationProvider) {
    return callNonAnonymousProtection(hash, message.findCurrentView(), locationProvider);
  }

  public boolean callNonAnonymousProtection (long hash, View view, @NonNull TooltipOverlayView.LocationProvider locationProvider) {
    return callNonAnonymousProtection(hash, context.tooltipManager().builder(view).locate(locationProvider));
  }

  public boolean callNonAnonymousProtection (long hash, @Nullable TooltipOverlayView.TooltipBuilder tooltipBuilder) {
    if (chat == null || tdlib.isSelfSender(chat.messageSenderId) || (chat.messageSenderId == null && !tdlib.isAnonymousAdmin(chat.id))) {
      return true;
    }

    if (currentNonAnonymousActionHash == hash) {
      currentNonAnonymousActionHash = 0;
      if (clearCurrentNonAnonymousActionHash != null) {
        clearCurrentNonAnonymousActionHash.cancel();
        clearCurrentNonAnonymousActionHash = null;
      }
      return true;
    }

    if (clearCurrentNonAnonymousActionHash != null) {
      clearCurrentNonAnonymousActionHash.cancel();
      clearCurrentNonAnonymousActionHash = null;
    }

    currentNonAnonymousActionHash = hash;
    clearCurrentNonAnonymousActionHash = new CancellableRunnable() {
      @Override
      public void act () {
        currentNonAnonymousActionHash = 0;
        clearCurrentNonAnonymousActionHash = null;
      }
    };

    tdlib.ui().postDelayed(clearCurrentNonAnonymousActionHash, 10000);

    if (tooltipBuilder != null) {
      tooltipBuilder
        .show(tdlib, Lang.getString(R.string.AnonWarning))
        .hideDelayed(2000, TimeUnit.MILLISECONDS);
    } else {
      UI.showToast(Lang.getString(R.string.AnonWarning), Toast.LENGTH_LONG);
    }

    return false;
  }





  // Updates

  private TdApi.ChatMessageSender[] chatAvailableSenders;
  private void onUpdateChatAvailableMessagesSenders (TdApi.ChatMessageSender[] senders) {
    tdlib.ui().post(() -> {
      this.chatAvailableSenders = senders;
    });
  }



  //

  private void openSetSenderPopup () {
    getChatAvailableMessagesSenders(() -> {
      final SetSenderController c = new SetSenderController(context, tdlib);
      c.setArguments(new SetSenderController.Args(chat, chatAvailableSenders, chat.messageSenderId));
      c.setDelegate(this::setNewMessageSender);
      c.show();
      hideCursorsForInputView();
    });
  }



  //

  private final HapticMenuHelper.Provider hapticSendAsMenuProvider = new HapticMenuHelper.Provider() {
    @Override
    public List<HapticMenuHelper.MenuItem> onCreateHapticMenu (View view) {
      if (chatAvailableSenders == null || chatAvailableSenders.length == 0) return new ArrayList<>();

      final int maxCount = 5;
      final boolean needMoreButton = chatAvailableSenders.length > maxCount;
      final int senderButtonsCount = needMoreButton ? maxCount - 1 : chatAvailableSenders.length;
      List<HapticMenuHelper.MenuItem> items = new ArrayList<>(Math.min(chatAvailableSenders.length, maxCount));

      for (int i = 0; i < senderButtonsCount; i++) {
        items.add(0, createHapticSenderItem(R.id.btn_setMsgSender, chatAvailableSenders[i].sender, true, chatAvailableSenders[i].needsPremium && !tdlib.hasPremium()));
      }

      if (needMoreButton) {
        items.add(0, new HapticMenuHelper.MenuItem(R.id.btn_openSendersMenu, Lang.getString(R.string.MoreMessageSenders), R.drawable.baseline_more_horiz_24));
      }

      hideCursorsForInputView();
      return items;
    }

    @Override
    public int getAnchorMode (View view) {
      return MenuMoreWrap.ANCHOR_MODE_CENTER;
    }
  };

  public boolean isMessageFound (TdApi.Message message) {
    if (foundMessageId != null && foundMessageId.getMessageId() == message.id) {
      return true;
    }
    return manager.isMessageFound(message);
  }

  // Search Input Callbacks

  private String lastMessageSearchQuery = "";
  private String lastMembersSearchQuery = "";

  public String getLastMessageSearchQuery () {
    return !StringUtils.isEmpty(previewSearchQuery) ? previewSearchQuery : lastMessageSearchQuery;
  }

  public String getLastMembersSearchQuery () {
    return lastMembersSearchQuery;
  }

  @Override
  protected void onSearchInputChanged (String query) {
    if (searchMode == SEARCH_MODE_MESSAGES) {
      if (!query.equals(lastMessageSearchQuery)) {
        searchChatMessages(query);
      }
      lastMessageSearchQuery = query;
      checkSearchFilteredModeButton(false);
    } else if (searchMode == SEARCH_MODE_USERS) {
      if (!query.equals(lastMembersSearchQuery)) {
        searchChatMembers(query);
      }
      lastMembersSearchQuery = query;
    }
  }

  private void searchChatMessages (final String query) {
    if (chat == null) return;
    if (searchMessagesFilterMode) {
      applyQueryForManagerInFilteredShowMode(query);
    }

    manager.search(chat.id, messageThread, searchMessagesSender, searchFiltersTdApi[searchMessagesFilterIndex], chat.type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR, query, foundMessageId);
    foundMessageId = null;
    searchMedia(query);
    manager.getAdapter().checkAllMessages();
  }

  private void searchChatMembers (final String query) {
    searchByUserViewWrapper.search(query);
  }



  private static final int SEARCH_MODE_MESSAGES = 0;
  private static final int SEARCH_MODE_USERS = 1;
  private int searchMode = 0;

  private ChatSearchMembersView searchByUserViewWrapper;
  private boolean isSearchByUserContentVisible;
  private float searchByUserTransformFactor;
  private static final int ANIMATOR_SEARCH_BY_USER = 21;
  private static final int ANIMATOR_SEARCH_NAVIGATION = 22;
  private final BoolAnimator searchByUserTransformAnimator = new BoolAnimator(ANIMATOR_SEARCH_BY_USER, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);
  private final BoolAnimator searchNavigationButtonVisibleAnimator = new BoolAnimator(ANIMATOR_SEARCH_NAVIGATION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

  private void showSearchByUserView (boolean show, boolean animated) {
    searchByUserTransformAnimator.setValue(show, animated);
    searchMode = show ? SEARCH_MODE_USERS : SEARCH_MODE_MESSAGES;
    if (show) {
      setSearchInput(getLastMembersSearchQuery());
    } else {
      setSearchInput(getLastMessageSearchQuery());
    }
  }

  private final void setSearchByUserTransformFactor (float factor) {
    if (this.searchByUserTransformFactor != factor) {
      this.searchByUserTransformFactor = factor;
      applySearchByUserTransformFactor(factor);
    }
  }

  private void applySearchByUserTransformFactor (float factor) {
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.setAlpha(factor);
      setSearchContentVisible(factor != 0f);
    }
  }

  private void setSearchContentVisible (boolean isVisible) {
    if (this.isSearchByUserContentVisible != isVisible) {
      this.isSearchByUserContentVisible = isVisible;
      if (searchByUserViewWrapper != null) {
        searchByUserViewWrapper.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      }
    }
  }

  private void showSearchTypeOptions () {
    showOptions(null, searchFilterIds, searchFilterTexts, null, searchFilterIcons, (View v, int id) -> {
      int index = ArrayUtils.indexOf(searchFilterIds, id);
      if (index >= 0) {
        onSetSearchTypeFilter(index);
        searchChatMessages(getLastMessageSearchQuery());
      }
      return true;
    });
  }

  private void setBadgesButtonsTranslationX (float searchFactor, float navVisible) {
    final float translationX = CircleCounterBadgeView.BUTTON_WRAPPER_WIDTH * searchFactor;
    final float translationX2 = CircleCounterBadgeView.BUTTON_WRAPPER_WIDTH * (1f - Math.min(searchFactor, navVisible));

    if (scrollToBottomButtonWrap != null) {
      scrollToBottomButtonWrap.setTranslationX(translationX);
    }
    if (mentionButtonWrap != null) {
      mentionButtonWrap.setTranslationX(translationX);
    }
    if (reactionsButtonWrap != null) {
      reactionsButtonWrap.setTranslationX(translationX);
    }
    if (goToNextFoundMessageButtonBadge != null) {
      goToNextFoundMessageButtonBadge.setTranslationX(translationX2);
    }
    if (goToPrevFoundMessageButtonBadge != null) {
      goToPrevFoundMessageButtonBadge.setTranslationX(translationX2);
    }
  }



  // Search Callbacks

  private TdApi.MessageSender searchMessagesSender = null;
  private int searchMessagesFilterIndex = 0;
  private boolean searchMessagesFilterMode = false;

  private void onSetSearchMessagesSenderId (TdApi.MessageSender sender) {
    searchMessagesSender = sender;
    if (searchByUserViewWrapper != null) {
      searchByUserViewWrapper.setMessageSender(sender);
    }
    if (searchByAvatarView != null && searchByButton != null) {
      searchByAvatarView.setMessageSender(tdlib, sender);
      searchByAvatarView.setVisibility((sender != null && canSearchByUserId()) ? View.VISIBLE : View.GONE);
      searchByButton.setVisibility((sender == null && canSearchByUserId()) ? View.VISIBLE : View.GONE);
    };
    checkSearchFilteredModeButton(false);
  }

  private void onSetSearchTypeFilter (int index) {
    searchMessagesFilterIndex = index;

    if (searchSetTypeFilterButton != null && index < searchFilterIcons.length && index >= 0) {
      searchSetTypeFilterButton.setImageResource(searchFilterIcons[index]);
      searchSetTypeFilterButton.setColorFilter(Theme.getColor(index == 0 ? ColorId.icon : ColorId.iconActive));
    }
    checkSearchFilteredModeButton(false);
  }

  private void toggleSearchFilteredShowMode () {
    onSetSearchFilteredShowMode(!searchMessagesFilterMode);
  }

  public boolean inOnlyFoundMode () {
    return searchMessagesFilterMode;
  }

  private void onSetSearchFilteredShowMode (boolean inSearchMode) {
    // Reopen chat if needed
    if (!inSearchMode && searchMessagesFilterMode) {
      manager.openChat(chat, messageThread, previewSearchFilter, this, areScheduled, !inPreviewMode && !isInForceTouchMode());
    } else if (inSearchMode && !searchMessagesFilterMode) {
      applyQueryForManagerInFilteredShowMode(getLastMessageSearchQuery());
    }

    searchMessagesFilterMode = inSearchMode;
    if (searchShowOnlyFoundButton != null) {
      searchShowOnlyFoundButton.setColorFilter(Theme.getColor(inSearchMode ? ColorId.iconActive : ColorId.icon));
    }
  }

  public boolean inSearchFilteredShowMode () {
    return searchMessagesFilterMode;
  }

  private void applyQueryForManagerInFilteredShowMode (String query) {
    manager.openSearch(chat, query, searchMessagesSender, searchFiltersTdApi[searchMessagesFilterIndex]);
  }



  // Controller Callbacks

  @Override
  public boolean onBeforeLeaveSearchMode () {
    if (searchMode == SEARCH_MODE_USERS) {
      showSearchByUserView(false, true);
      return false;
    }
    if (previewSearchSender != null) {
      navigateBack();
      return false;
    }
    return true;
  }

  @Override
  protected boolean needHideKeyboardOnTouchBackButton () {
    return searchMode != SEARCH_MODE_USERS;
  }



  // Filter Type Utils

  public static final int MESSAGE_TYPE_UNKNOWN = -1;
  public static final int MESSAGE_TYPE_TEXT = MessagesSearchManagerMiddleware.SearchMessagesFilterTextPolyfill.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_PHOTO = TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_VIDEO = TdApi.SearchMessagesFilterVideo.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_VOICE = TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_ROUND = TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_FILE = TdApi.SearchMessagesFilterDocument.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_MUSIC = TdApi.SearchMessagesFilterAudio.CONSTRUCTOR;
  public static final int MESSAGE_TYPE_GIF = TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR;

  public static int getMessageType (Tdlib tdlib, TdApi.Message msg, TdApi.MessageContent content) {
    try {
      if (content == null) {
        return MESSAGE_TYPE_TEXT;
      }
      if (!StringUtils.isEmpty(msg.restrictionReason) && Settings.instance().needRestrictContent()) {
        return MESSAGE_TYPE_TEXT;
      }
      switch (content.getConstructor()) {
        case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
          TdApi.MessageAnimatedEmoji emoji = (TdApi.MessageAnimatedEmoji) content;
          TdApi.MessageContent pendingContent = tdlib.getPendingMessageText(msg.chatId, msg.id);
          if (pendingContent != null) {
            if (pendingContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
              return MESSAGE_TYPE_UNKNOWN;
            } else {
              return MESSAGE_TYPE_TEXT;
            }
          }
          if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
            return MESSAGE_TYPE_TEXT;
          } else {
            return MESSAGE_TYPE_UNKNOWN;
          }
        }

        case TdApi.MessageText.CONSTRUCTOR: {
          TdApi.MessageContent pendingContent = tdlib.getPendingMessageText(msg.chatId, msg.id);
          if (pendingContent != null && pendingContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
            return MESSAGE_TYPE_UNKNOWN;
          }
          return MESSAGE_TYPE_TEXT;
        }
        case TdApi.MessagePhoto.CONSTRUCTOR: {
          return MESSAGE_TYPE_PHOTO;
        }
        case TdApi.MessageVideo.CONSTRUCTOR: {
          return MESSAGE_TYPE_VIDEO;
        }
        case TdApi.MessageAnimation.CONSTRUCTOR: {
          return MESSAGE_TYPE_GIF;
        }
        case TdApi.MessageVideoNote.CONSTRUCTOR: {
          return MESSAGE_TYPE_ROUND;
        }
        case TdApi.MessageVoiceNote.CONSTRUCTOR: {
          return MESSAGE_TYPE_VOICE;
        }
        case TdApi.MessageAudio.CONSTRUCTOR: {
          return MESSAGE_TYPE_MUSIC;
        }
        case TdApi.MessageDocument.CONSTRUCTOR: {
          return MESSAGE_TYPE_FILE;
        }
        default: {
          return MESSAGE_TYPE_UNKNOWN;
        }
      }
    } catch (Throwable t) {
      return MESSAGE_TYPE_UNKNOWN;
    }
  }

  private static int[] searchFilterIds = new int[]{
    R.id.btn_messageSearchFilterAll,
    R.id.btn_messageSearchFilterText,
    R.id.btn_messageSearchFilterPhoto,
    R.id.btn_messageSearchFilterVideo,
    R.id.btn_messageSearchFilterVoice,
    R.id.btn_messageSearchFilterRound,
    R.id.btn_messageSearchFilterFiles,
    R.id.btn_messageSearchFilterMusic,
    R.id.btn_messageSearchFilterGif
  };

  private static final int[] searchFilterIcons = new int[] {
    R.drawable.baseline_filter_variant_remove_24,
    R.drawable.baseline_format_text_24,
    R.drawable.baseline_image_24,
    R.drawable.baseline_videocam_24,
    R.drawable.baseline_mic_24,
    R.drawable.deproko_baseline_msg_video_24,
    R.drawable.baseline_insert_drive_file_24,
    R.drawable.baseline_music_note_24,
    R.drawable.deproko_baseline_gif_filled_24
  };

  private static final String[] searchFilterTexts = new String[] {
    Lang.getString(R.string.MessageSearchFilterAll),
    Lang.getString(R.string.MessageSearchFilterText),
    Lang.getString(R.string.MessageSearchFilterPhoto),
    Lang.getString(R.string.MessageSearchFilterVideo),
    Lang.getString(R.string.MessageSearchFilterVoice),
    Lang.getString(R.string.MessageSearchFilterRound),
    Lang.getString(R.string.MessageSearchFilterFiles),
    Lang.getString(R.string.MessageSearchFilterMusic),
    Lang.getString(R.string.MessageSearchFilterGif)
  };

  private static final TdApi.SearchMessagesFilter[] searchFiltersTdApi = new TdApi.SearchMessagesFilter[] {
    null,
    new MessagesSearchManagerMiddleware.SearchMessagesFilterTextPolyfill(),
    new TdApi.SearchMessagesFilterPhoto(),
    new TdApi.SearchMessagesFilterVideo(),
    new TdApi.SearchMessagesFilterVoiceNote(),
    new TdApi.SearchMessagesFilterVideoNote(),
    new TdApi.SearchMessagesFilterDocument(),
    new TdApi.SearchMessagesFilterAudio(),
    new TdApi.SearchMessagesFilterAnimation()
  };

  // Translate

  TranslationControllerV2.Wrapper translationPopup;

  public void startTranslateMessages (TGMessage message) {
    startTranslateMessages(message, false);
  }

  public void startTranslateMessages (TGMessage message, boolean forcePopup) {
    if (message.translationStyleMode() == Settings.TRANSLATE_MODE_INLINE && !(message instanceof TGMessageBotInfo) && !forcePopup) {
      message.startTranslated();
    } else {
      translationPopup = new TranslationControllerV2.Wrapper(context, tdlib, this);
      translationPopup.setArguments(new TranslationControllerV2.Args(message));
      translationPopup.setClickCallback(message.clickCallback());
      translationPopup.setTextColorSet(TextColorSets.Regular.NORMAL);
      translationPopup.show();
      translationPopup.setDismissListener(popup -> translationPopup = null);
      hideCursorsForInputView();
    }
  }

  public void stopTranslateMessages (TGMessage message) {
    message.stopTranslated();
  }

  /**/

  private boolean textInputHasSelection;
  private boolean textFormattingVisible;

  @Override
  public void onInputSelectionChanged (InputView v, int start, int end) {
    if (textFormattingLayout != null) {
      textFormattingLayout.onInputViewSelectionChanged(start, end);
    }
  }

  @Override
  public void onInputSelectionExistChanged (InputView v, boolean hasSelection) {
    textInputHasSelection = hasSelection;
    if (!emojiShown) {
      emojiButton.setImageResource(getTargetIcon(true));
    }
  }

  public void onInputSpansChanged (InputView view) {
    if (textFormattingLayout != null) {
      textFormattingLayout.onInputViewSpansChanged();
    }
  }

  private void setTextFormattingLayoutVisible (boolean visible) {
    textFormattingVisible = emojiKeyboardFrameLayout != null && emojiKeyboardFrameLayout.contentView.setTextFormattingLayoutVisible(visible);
  }

  private void closeTextFormattingKeyboard () {
    if (textFormattingVisible && emojiShown) {
      closeEmojiKeyboard();
    }
  }

  public @DrawableRes int getTargetIcon (boolean isMessage) {
    return (textInputHasSelection || (textFormattingVisible && emojiShown)) ? R.drawable.baseline_format_text_24 : EmojiLayout.getTargetIcon(isMessage);
  }

  @Override
  protected void onCreatePopupLayout (PopupLayout popupLayout) {
    hideCursorsForInputView();
  }

  public void hideCursorsForInputView () {
    if (inputView != null) {
      inputView.hideSelectionCursors();
    }
  }

  /* * */


  private ArrayList<InlineResult<?>> stickerSuggestionItems;
  private boolean canShowEmojiSuggestions;
  private boolean isStickerSuggestionsTemporarilyHidden;

  public void showStickerSuggestions (@Nullable ArrayList<TGStickerObj> stickers, boolean isMore) {
    ArrayList<InlineResult<?>> items;
    if (stickers != null) {
      items = new ArrayList<>(stickers.size());
      for (TGStickerObj sticker : stickers) {
        items.add(new InlineResultSticker(context, tdlib, "x", new TdApi.InlineQueryResultSticker("x", sticker.getSticker())));
      }
    } else {
      items = null;
    }

    if (!isMore) {
      stickerSuggestionItems = items;
      context.showInlineResults(this, tdlib, items, true, null, getInlineResultsStickerScrollListener(), getInlineResultsStickerMovementsCallback());
    } else {
      if (items != null && stickerSuggestionItems != null) {
        stickerSuggestionItems.addAll(items);
      }
      context.addInlineResults(this, items, null, getInlineResultsStickerScrollListener(), getInlineResultsStickerMovementsCallback());
    }
  }

  private String lastFoundByEmoji;
  private boolean needSkipMoreEmojiSuggestions;

  public void showEmojiSuggestions (@Nullable ArrayList<TGStickerObj> stickers, String foundByEmoji,  boolean isMore) {
    if (StringUtils.equalsOrBothEmpty(lastFoundByEmoji, foundByEmoji)) {
      if (!isMore || needSkipMoreEmojiSuggestions) {
        if (context.hasEmojiSuggestions()) {
          if (isFocused() && pagerScrollOffset < 1f) {
            context.setEmojiSuggestionsVisible(true);
          }
          canShowEmojiSuggestions = true;
        }
        return;
      }
      needSkipMoreEmojiSuggestions = true;
    } else {
      lastFoundByEmoji = foundByEmoji;
      needSkipMoreEmojiSuggestions = false;
    }

    if (stickers == null || stickers.isEmpty()) {
      if (!isMore) {
        context.setEmojiSuggestions(this, null, getInlineEmojiStickerScrollListener(), this::notifyChoosingEmoji);
        context().setEmojiSuggestionsVisible(false);
        canShowEmojiSuggestions = false;
      }
      return;
    }

    if (isMore && context.hasEmojiSuggestions() && context.isEmojiSuggestionsVisible()) {
      context.addEmojiSuggestions(this, stickers);
    } else {
      context.setEmojiSuggestions(this, stickers, getInlineEmojiStickerScrollListener(), this::notifyChoosingEmoji);
    }
    if (isFocused() && pagerScrollOffset < 1f) {
      context.setEmojiSuggestionsVisible(true);
    }

    canShowEmojiSuggestions = true;
  }

  private void showStickersSuggestionsIfTemporarilyHidden () {
    if (isStickerSuggestionsTemporarilyHidden && stickerSuggestionItems != null) {
      isStickerSuggestionsTemporarilyHidden = false;
      context.showInlineResults(this, tdlib, stickerSuggestionItems, true, null, getInlineResultsStickerScrollListener(), getInlineResultsStickerMovementsCallback());
    }
  }

  private void hideStickersSuggestionsTemporarily () {
    isStickerSuggestionsTemporarilyHidden = true;
    context.showInlineResults(this, tdlib, null, true, null);
  }

  private void showEmojiSuggestionsIfTemporarilyHidden () {
    if (canShowEmojiSuggestions) {
      context().setEmojiSuggestionsVisible(true);
    }
  }

  private void hideEmojiSuggestionsTemporarily () {
    context().setEmojiSuggestionsVisible(false);
  }

  private void hideEmojiAndStickerSuggestionsFinally () {
    onHideEmojiAndStickerSuggestionsFinally();
    context.showInlineResults(this, tdlib, null, false, null);
  }

  public void onHideEmojiAndStickerSuggestionsFinally () {
    canShowEmojiSuggestions = false;
    stickerSuggestionItems = null;
    hideEmojiSuggestionsTemporarily();
  }

  private StickerSmallView.StickerMovementCallback getInlineResultsStickerMovementsCallback () {
    return new StickerSmallView.StickerMovementCallback() {
      @Override
      public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
        if (onSendStickerSuggestion(clickView, sticker, sendOptions)) {
          hideEmojiAndStickerSuggestionsFinally();
          return true;
        }

        return false;
      }

      @Override
      public long getStickerOutputChatId () {
        return getOutputChatId();
      }

      @Override
      public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
        InlineResultsWrap inlineResultsWrap = context.getInlineResultsView();
        if (inlineResultsWrap != null) {
          inlineResultsWrap.setStickerPressed(view, sticker, isPressed);
        }
      }

      @Override
      public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
        return true;
      }

      @Override
      public boolean needsLongDelay (StickerSmallView view) {
        return true;
      }

      @Override
      public int getStickersListTop () {
        return -getInputOffset(false);
      }

      @Override
      public int getViewportHeight () {
        return getStickerSuggestionPreviewViewportHeight();
      }

      @Override
      public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {
        notifyChoosingEmoji(EmojiMediaType.STICKER, stickerPreviewIsVisible = true);
      }

      @Override
      public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {
        notifyChoosingEmoji(EmojiMediaType.STICKER, stickerPreviewIsVisible = true);
      }

      @Override
      public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {
        notifyChoosingEmoji(EmojiMediaType.STICKER, stickerPreviewIsVisible = false);
      }
    };
  }

  private int suggestionXDiff = 0;
  private int suggestionYDiff = 0;
  private boolean stickerPreviewIsVisible;

  private RecyclerView.OnScrollListener getInlineResultsStickerScrollListener () {
    suggestionYDiff = 0;
    return new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy == 0) return;
        suggestionYDiff = recyclerView.computeVerticalScrollOffset();
        notifyChoosingEmoji(EmojiMediaType.STICKER, true);
      }
    };
  }

  private RecyclerView.OnScrollListener getInlineEmojiStickerScrollListener () {
    suggestionYDiff = 0;
    return new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dx == 0) return;
        suggestionXDiff = recyclerView.computeHorizontalScrollOffset();
        notifyChoosingEmoji(EmojiMediaType.EMOJI, true);
      }
    };
  }

  @Override
  public boolean onSendStickerSuggestion (View view, TGStickerObj sticker, TdApi.MessageSendOptions initialSendOptions) {
    if (lastJunkTime == 0l || SystemClock.uptimeMillis() - lastJunkTime >= JUNK_MINIMUM_DELAY) {
      if (sticker.isCustomEmoji()) {
        inputView.onCustomEmojiSelected(sticker, true);
        return true;
      }
      if (showGifRestriction(view))
        return false;
      pickDateOrProceed(initialSendOptions, (modifiedSendOptions, disableMarkdown) -> {
        if (sendSticker(view, sticker.getSticker(), sticker.getFoundByEmoji(), true, modifiedSendOptions)) {
          lastJunkTime = SystemClock.uptimeMillis();
          inputView.setInput("", false, true);
        }
      });
      return true;
    }
    return false;
  }

  @Override
  public int getStickerSuggestionsTop (boolean isEmoji) {
    View v = context().getEmojiSuggestionsView();
    return v != null ? Views.getLocationInWindow(v)[1] : 0;
  }

  @Override
  public int getStickerSuggestionPreviewViewportHeight () {
    return HeaderView.getSize(true) + messagesView.getMeasuredHeight();
  }

  @Override
  public long getCurrentChatId () {
    return getChatId();
  }

  private void checkAttachedFiles (boolean animated) {
    final boolean hasAttachedFiles = hasAttachedFiles();

    if (attachedFiles != null) {
      attachedFiles.setHidden(!hasAttachedFiles, animated);
    }

    setCustomCaptionPlaceholder(hasAttachedFiles ? Lang.getString(R.string.Caption) : null);
    if (inputView != null) {
      checkSendButton(animated);
    }
    if (emojiLayout != null) {
      emojiLayout.setAllowMedia(!hasAttachedFiles);
    }
  }

  private float getAttachedFilesOffset () {
    return attachedFilesLastHeight * (1f - getSearchTransformFactor()) * (attachedFiles != null ? attachedFiles.getVisibleFactor() : 0f);
  }

  public boolean hasAttachedFiles () {
    return !needHideAttachedFiles() && attachedFiles != null && attachedFiles.isDisplayingItems();
  }

  private void discardAttachedFiles (boolean animated) {
    if (attachedFiles != null) {
      attachedFiles.showItems(this, null, false, null, null, null, !isFocused());
      checkAttachedFiles(animated);
    }
  }

  private boolean needHideAttachedFiles () {
    return inSearchMode() || isEditingMessage();
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (attachedFiles != null) {
      attachedFiles.getThemeProvider().onThemeColorsChanged(areTemp);
    }
  }

  private InlineResultsWrap attachedFiles;
  private CustomItemAnimator attachedFilesAnimator;
  private ClickHelper.Delegate attachedFilesClickHelperDelegate;
  private int attachedFilesLastHeight;

  private ClickHelper.Delegate getAttachedFilesClickHelperDelegate () {
    if (attachedFilesClickHelperDelegate == null) {
      attachedFilesClickHelperDelegate = new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt (View view, float x, float y) {
          return true;
        }

        @Override
        public void onClickAt (View view, float x, float y) {
          if (x < view.getMeasuredWidth() - Screen.dp(36)) {
            return;
          }

          Object tag = view.getTag();
          if (tag instanceof InlineResult<?>) {
            if (attachedFiles.getRecyclerView().getItemAnimator() == null) {
              attachedFiles.getRecyclerView().setItemAnimator(attachedFilesAnimator);
            }

            attachedFiles.removeItem((InlineResult<?>) tag);
            scheduleRemoveAttachedFilesAnimator();
            checkAttachedFiles(true);
          }
        }
      };
    }
    return attachedFilesClickHelperDelegate;
  }

  private Runnable attachedFilesAnimatorRemoveRunnable;

  private void scheduleRemoveAttachedFilesAnimator () {
    if (attachedFilesAnimatorRemoveRunnable != null) {
      UI.cancel(attachedFilesAnimatorRemoveRunnable);
    }
    attachedFilesAnimatorRemoveRunnable = this::removeAttachedFilesAnimator;
    UI.post(attachedFilesAnimatorRemoveRunnable, 500);
  }

  private void removeAttachedFilesAnimator () {
    attachedFilesAnimatorRemoveRunnable = null;
    final RecyclerView recyclerView = attachedFiles.getRecyclerView();
    if (recyclerView.getItemAnimator() != null && recyclerView.getItemAnimator().isRunning()) {
      scheduleRemoveAttachedFilesAnimator();
      return;
    }
    recyclerView.setItemAnimator(null);
  }

  public void setFilesToAttach (ArrayList<InlineResult<?>> results, boolean needShowKeyboard) {
    if (results == null || results.isEmpty()) {
      discardAttachedFiles(true);
      return;
    }

    for (InlineResult<?> result : results) {
      if (result instanceof InlineResultCommon) {
        ((InlineResultCommon) result).setNeedCloseButton(true);
        ((InlineResultCommon) result).setClickHelper(new ClickHelper(getAttachedFilesClickHelperDelegate()));
        ((InlineResultCommon) result).rebuildLayout();
      }
    }

    if (attachedFiles == null) {
      attachedFiles = new InlineResultsWrap(context) {
        private int checkTopEdge (int top) {
          int height = Math.min(getHeightLimit(), Math.max(getMinItemsHeight(), getRecyclerView().getMeasuredHeight() - top));
          if (attachedFilesLastHeight != height) {
            attachedFilesLastHeight = height;
            UI.post(MessagesController.this::updateReplyView);
          }
          return top;
        }

        @Override
        public int detectRecyclerTopEdge () {
          final RecyclerView recyclerView = getRecyclerView();
          final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          int i = manager.findFirstVisibleItemPosition();
          if (i != 0) {
            return checkTopEdge(0);
          }

          int topA = 0;
          if (i == 0) {
            View view = manager.findViewByPosition(0);
            if (view != null) {
              topA = view.getMeasuredHeight();
              topA += view.getTop();
            }
          }

          int top = recyclerView.getMeasuredHeight();
          for (int a = 0; a < recyclerView.getChildCount(); a++) {
            View view = recyclerView.getChildAt(a);
            if (view instanceof ShadowView) {
              continue;
            }
            top = Math.min(top, (int) (view.getTop() + view.getTranslationY() + (view.getMeasuredHeight() * (1f - view.getAlpha()))));
          }
          return checkTopEdge(Math.min(top, topA));

        }

        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          super.onFactorChanged(id, factor, fraction, callee);
          updateReplyView();
        }
      };
      attachedFilesAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 150L);
      attachedFiles.getRecyclerView().setItemAnimator(null);
      attachedFiles.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      attachedFiles.setOffsetProvider(new InlineResultsWrap.OffsetProvider() {
        @Override
        public int provideOffset (InlineResultsWrap v) {
          return getInputOffset(false);
        }

        @Override
        public int provideParentHeight (InlineResultsWrap v) {
          return contentView.getMeasuredHeight();
        }
      });
      attachedFiles.setListener(new InlineResultsWrap.PickListener() {});
    }

    if (attachedFiles.getParent() == null) {
      contentView.addView(attachedFiles);
    }

    attachedFiles.showItems(this, results, false, null, null, null, needHideAttachedFiles());
    checkAttachedFiles(true);
    if (inputView != null && needShowKeyboard) {
      showKeyboard();
    }
  }
}
