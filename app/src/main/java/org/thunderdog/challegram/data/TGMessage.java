/**
 * File created on 03/05/15 at 10:39
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.MessagesTouchHelperCallback;
import org.thunderdog.challegram.component.chat.ReplyComponent;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextPart;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.SdkVersion;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public abstract class TGMessage implements MultipleViewProvider.InvalidateContentProvider, TdlibDelegate, FactorAnimator.Target, Comparable<TGMessage>, Counter.Callback {
  private static final int MAXIMUM_CHANNEL_MERGE_TIME_DIFF = 150;
  private static final int MAXIMUM_COMMON_MERGE_TIME_DIFF = 900;

  private static final int MAXIMUM_CHANNEL_MERGE_COUNT = 19;
  private static final int MAXIMUM_COMMON_MERGE_COUNT = 14;

  private static final int MESSAGE_FLAG_READ = 1;
  private static final int MESSAGE_FLAG_IS_BOTTOM = 1 << 1;
  private static final int MESSAGE_FLAG_HAS_OLDER_MESSAGE = 1 << 2;
  private static final int MESSAGE_FLAG_IS_THREAD_HEADER = 1 << 3;
  private static final int MESSAGE_FLAG_BELOW_HEADER = 1 << 4;
  private static final int MESSAGE_FLAG_FORCE_AVATAR = 1 << 5;

  private static final int FLAG_LAYOUT_BUILT = 1 << 5;
  private static final int FLAG_MERGE_FORWARD = 1 << 6;
  private static final int FLAG_MERGE_BOTTOM = 1 << 7;
  private static final int FLAG_HEADER_ENABLED = 1 << 8;
  private static final int FLAG_SHOW_TICKS = 1 << 9;
  private static final int FLAG_SHOW_BADGE = 1 << 10;
  private static final int FLAG_SHOW_DATE = 1 << 11;
  private static final int FLAG_EXTRA_PADDING = 1 << 12;
  private static final int FLAG_HIDE_MEDIA = 1 << 17;
  private static final int FLAG_VIEWED = 1 << 18;
  private static final int FLAG_DATE_FAKE_BOLD = 1 << 19;
  private static final int FLAG_NO_UNREAD = 1 << 20;
  private static final int FLAG_ATTACHED = 1 << 21;
  private static final int FLAG_EVENT_LOG = 1 << 22;
  // private static final int FLAG_IS_ADMIN = 1 << 24;
  private static final int FLAG_SELF_CHAT = 1 << 25;
  private static final int FLAG_IGNORE_SWIPE = 1 << 26;
  private static final int FLAG_READY_SHARE = 1 << 27;
  private static final int FLAG_READY_REPLY = 1 << 28;
  private static final int FLAG_UNSUPPORTED = 1 << 29;
  private static final int FLAG_ERROR = 1 << 30;
  private static final int FLAG_BEING_ADDED = 1 << 31;

  protected TdApi.Message msg;
  private int flags;

  protected int mergeTime, mergeIndex;

  protected int width;
  protected int height;

  protected String time;

  protected @NonNull final TdlibSender sender;

  protected @Nullable final String viaBotUsername;
  protected TGSource forwardInfo;
  protected ReplyComponent replyData;
  protected TGInlineKeyboard inlineKeyboard;

  // header values

  private String date;
  private @Nullable Text hAuthorNameT, hPsaTextT;
  private @Nullable Text hAdminNameT;
  private ImageFile hAvatar;
  private AvatarPlaceholder hAvatarPlaceholder;
  private Letters uBadge;

  // counters

  private final Counter viewCounter, replyCounter, shareCounter, isPinned;

  // forward values

  private String fTime;
  private Text fAuthorNameT, fPsaTextT;
  private float fTimeWidth;

  // positions and sizes

  private int pBadgeX, pBadgeIconX;

  private int pRealContentX, pRealContentMaxWidth;
  private int leftContentEdge, topContentEdge, bottomContentEdge, rightContentEdge;
  private float lastMergeRadius, lastDefaultRadius;
  private int pContentX, pContentY, pContentMaxWidth;

  private int pTimeLeft, pTimeWidth;
  private int pClockLeft, pClockTop;
  private int pTicksLeft, pTicksTop;
  private int pDateWidth;

  private final Path bubblePath, bubbleClipPath;
  private float topRightRadius, topLeftRadius, bottomLeftRadius, bottomRightRadius;
  private final RectF bubblePathRect, bubbleClipPathRect;

  private boolean needSponsorSmallPadding;

  protected final MessagesManager manager;
  protected final Tdlib tdlib;
  protected final MultipleViewProvider currentViews;
  protected final MultipleViewProvider overlayViews;

  protected TGMessage (MessagesManager manager, TdApi.Message msg) {
    if (!initialized) {
      synchronized (TGMessage.class) {
        if (!initialized) {
          init();
        }
      }
    }
    initPaints();

    this.manager = manager;
    this.tdlib = manager.controller().tdlib();

    this.bubblePath = new Path();
    this.bubblePathRect = new RectF();
    this.bubbleClipPath = new Path();
    this.bubbleClipPathRect = new RectF();

    this.currentViews = new MultipleViewProvider();
    this.currentViews.setContentProvider(this);
    this.msg = msg;

    TdApi.MessageSender sender = msg.senderId;
    if (tdlib.isSelfChat(msg.chatId)) {
      flags |= FLAG_SELF_CHAT;
      if (msg.forwardInfo != null) {
        switch (msg.forwardInfo.origin.getConstructor()) {
          case TdApi.MessageForwardOriginUser.CONSTRUCTOR:
            sender = new TdApi.MessageSenderUser(((TdApi.MessageForwardOriginUser) msg.forwardInfo.origin).senderUserId);
            break;
          case TdApi.MessageForwardOriginChat.CONSTRUCTOR:
            sender = new TdApi.MessageSenderChat(((TdApi.MessageForwardOriginChat) msg.forwardInfo.origin).senderChatId);
            break;
          case TdApi.MessageForwardOriginChannel.CONSTRUCTOR:
            TdApi.MessageForwardOriginChannel info = (TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin;
            if ((msg.forwardInfo.fromChatId == 0 && msg.forwardInfo.fromMessageId == 0)) {
              msg.forwardInfo.fromChatId = info.chatId;
              msg.forwardInfo.fromMessageId = info.messageId;
            }
            break;
          case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR:
          case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR:
            break;
        }
      }
    }
    this.sender = new TdlibSender(tdlib, msg.chatId, sender, manager, !msg.isOutgoing && isDemoChat());

    this.isPinned = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .drawable(R.drawable.deproko_baseline_pin_14, 14f, 0f, Gravity.CENTER_HORIZONTAL)
      .build();
    if (msg.isChannelPost || (msg.forwardInfo != null && (
        msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginChannel.CONSTRUCTOR ||
        TD.getViewCount(msg.interactionInfo) > 1 ||
        tdlib.isChannel(msg.forwardInfo.fromChatId) ||
        this.sender.isChannel()
    ))) {
      this.viewCounter = new Counter.Builder()
        .noBackground()
        .allBold(false)
        .textSize(useBubbles() ? 11f : 12f)
        .callback(this)
        .colorSet(this::getTimePartTextColor)
        .drawable(R.drawable.baseline_visibility_14, 14f, 3f, Gravity.LEFT)
        .build();
    } else {
      this.viewCounter = null;
    }
    this.replyCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .textSize(useBubbles() ? 11f : 12f)
      .callback(this)
      .colorSet(this::getTimePartTextColor)
      .drawable(this.sender.isChannel() ? R.drawable.templarian_baseline_comment_12 : R.drawable.baseline_updirectory_arrow_left_14, 12f, 3f, Gravity.LEFT)
      .build();
    this.shareCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .textSize(useBubbles() ? 11f : 12f)
      .callback(this)
      .colorSet(this::getTimePartTextColor)
      .drawable(R.drawable.baseline_share_arrow_14, 14f, 3f, Gravity.LEFT)
      .build();
    updateInteractionInfo(false);

    this.time = genTime();

    if (msg.viaBotUserId != 0) {
      TdApi.User viaBot = tdlib.cache().user(msg.viaBotUserId);
      if (viaBot != null) {
        this.viaBotUsername = "@" + viaBot.username;
      } else {
        this.viaBotUsername = null;
      }
    } else {
      this.viaBotUsername = null;
    }

    if (needViewGroup()) {
      overlayViews = new MultipleViewProvider();
      overlayViews.setContentProvider(this);
    } else {
      overlayViews = null;
    }

    if (useForward() || forceForwardedInfo()) {
      loadForward();
    }

    if (msg.replyToMessageId != 0 && (msg.replyToMessageId != msg.messageThreadId || msg.messageThreadId != messagesController().getMessageThreadId())) {
      loadReply();
    }

    if (isHot() && needHotTimer() && msg.ttlExpiresIn < msg.ttl) {
      startHotTimer(false);
    }
  }

  private static @NonNull <T> T nonNull (@Nullable T value) {
    if (value == null) {
      throw new IllegalArgumentException("TDLib bug");
    }
    return value;
  }

  @Override
  public final BaseActivity context () {
    return manager.controller().context();
  }

  @Override
  public final Tdlib tdlib () {
    return tdlib;
  }

  public final MessagesManager manager () {
    return manager;
  }

  public final MessagesController messagesController () {
    return manager.controller();
  }

  public final ViewController<?> controller () {
    return messagesController().getParentOrSelf();
  }

  public final void navigateTo (ViewController<?> c) {
    if (!controller().navigateTo(c))
      c.destroy();
  }

  // Value Generators

  private String genTime () {
    if (isEventLog()) {
      return Lang.getRelativeTimestampShort(msg.date, TimeUnit.SECONDS);
    } else if (isSponsored()) {
      return Lang.getString(R.string.SponsoredSign);
    }
    StringBuilder b = new StringBuilder();
    String signature;
    if (isChannel() && !StringUtils.isEmpty(msg.authorSignature)) {
      signature = msg.authorSignature;
    } else if (forceForwardedInfo() && msg.forwardInfo != null) {
      switch (msg.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageForwardOriginChannel.CONSTRUCTOR:
          signature = ((TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin).authorSignature;
          break;
        case TdApi.MessageForwardOriginChat.CONSTRUCTOR:
          signature = ((TdApi.MessageForwardOriginChat) msg.forwardInfo.origin).authorSignature;
          break;
        default:
          signature = null;
          break;
      }
    } else {
      signature = null;
    }
    if (!StringUtils.isEmpty(signature)) {
      if (Fonts.isLtrCharSupported()) {
        b.append(Strings.LTR_CHAR);
      }
      b.append(Strings.limit(signature, 18));
      if (Fonts.isLtrCharSupported()) {
        b.append(Strings.LTR_CHAR);
      }
      b.append(", ");
    }
    if (!useBubbles() && needAdminSign()) {
      b.append(getAdministratorSign()).append(" ");
    }
    if (msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR) {
      b.append(Lang.getString(R.string.ImportedSign)).append(" ");
    }
    if (TD.isFailed(msg)) {
      b.append(Lang.getString(R.string.failed));
    } else if (isScheduled()) {
      int date = msg.schedulingState.getConstructor() == TdApi.MessageSchedulingStateSendAtDate.CONSTRUCTOR ? ((TdApi.MessageSchedulingStateSendAtDate) msg.schedulingState).sendDate : 0;
      if (date != 0) {
        b.append(Lang.time(date, TimeUnit.SECONDS));
      }
    } else if ((flags & FLAG_SELF_CHAT) != 0 && !isOutgoing() && msg.forwardInfo != null) {
      int date = replaceTimeWithEditTime() ? msg.editDate : msg.date;
      if (msg.forwardInfo.date != 0)
        date = msg.forwardInfo.date;
      if (date != 0) {
        b.append(Lang.getRelativeTimestampShort(date, TimeUnit.SECONDS));
      }
    } else if (forceForwardedInfo() && msg.forwardInfo.date != 0) {
      b.append(DateUtils.isSameDay(msg.forwardInfo.date, msg.date) ? Lang.time(msg.forwardInfo.date, TimeUnit.SECONDS) : Lang.getRelativeTimestampShort(msg.forwardInfo.date, TimeUnit.SECONDS));
    } else {
      int date = replaceTimeWithEditTime() ? msg.editDate : msg.date;
      if (date != 0) {
        b.append(Lang.time(date, TimeUnit.SECONDS));
      }
    }

    return b.toString();
  }

  private String genForwardTime () {
    if (!useForward()) {
      return null;
    }
    if (msg.forwardInfo == null) {
      return null;
    }
    return Lang.getRelativeTimestampShort(msg.forwardInfo.date, TimeUnit.SECONDS);
  }

  // Other

  private String genDate () {
    if (isDemoChat() && (flags & MESSAGE_FLAG_HAS_OLDER_MESSAGE) == 0) {
      return Lang.getString(R.string.ChatPreview);
    }
    if (isScheduled()) {
      switch (msg.schedulingState.getConstructor()) {
        case TdApi.MessageSchedulingStateSendWhenOnline.CONSTRUCTOR:
          return Lang.getString(R.string.ScheduledUntilOnline);
        case TdApi.MessageSchedulingStateSendAtDate.CONSTRUCTOR: {
          int date = ((TdApi.MessageSchedulingStateSendAtDate) msg.schedulingState).sendDate;
          if (DateUtils.isToday(date, TimeUnit.SECONDS)) {
            return Lang.getString(R.string.ScheduledToday);
          } else if (DateUtils.isTomorrow(date, TimeUnit.SECONDS)) {
            return Lang.getString(R.string.ScheduledTomorrow);
          } else {
            return Lang.getString(R.string.ScheduledDate, Lang.getDate(date, TimeUnit.SECONDS));
          }
        }
      }
    }
    if (isEventLog()) {
      return Lang.getRelativeTimestamp(getComparingDate(), TimeUnit.SECONDS);
    }
    if (BitwiseUtils.getFlag(flags, MESSAGE_FLAG_BELOW_HEADER) && manager.isHeaderVisible()) {
      return Lang.getString(R.string.DiscussionStart);
    }
    return Lang.getDate(getComparingDate(), TimeUnit.SECONDS);
  }

  public final void forceAvatarWhenMerging (boolean value) {
    flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_FORCE_AVATAR, value);
  }

  public final boolean mergeWith (@Nullable TGMessage top, boolean isBottom) {
    if (top != null) {
      top.setNeedExtraPadding(false);
      top.setNeedExtraPresponsoredPadding(isSponsored());
      flags |= MESSAGE_FLAG_HAS_OLDER_MESSAGE;
    } else {
      flags &= ~MESSAGE_FLAG_HAS_OLDER_MESSAGE;
    }
    if (isBottom) {
      setNeedExtraPadding(true);
    }

    flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_BELOW_HEADER, top != null && top.isThreadHeader());

    setIsBottom(true);

    if (top == null || top.isThreadHeader() != isThreadHeader() || !(isEventLog() ? needHideEventDate() || (DateUtils.isSameHour(top.getComparingDate(), getComparingDate()) /*|| !(msg.content instanceof TdApiExt.MessageChatEvent)*/) : DateUtils.isSameDay(top.getComparingDate(), getComparingDate()))) {
      if (top != null) {
        top.setIsBottom(true);
      }
      setHeaderEnabled(!headerDisabled());
      if ((top != null || getDate() != 0 || isScheduled()) && !isSponsored()) {
        flags |= FLAG_SHOW_DATE;
        setDate(genDate());
      } else {
        flags &= ~FLAG_SHOW_DATE;
      }
      return false;
    }

    flags &= ~FLAG_SHOW_DATE;

    boolean useBubbles = useBubbles();
    boolean isChannel = isChannel();

    TdApi.Message topMessage = top.getMessage();
    if (top.headerDisabled() || (flags & FLAG_SHOW_BADGE) != 0 || !tdlib.isSameSender(topMessage, msg) || !TD.isSameSource(topMessage, msg, forceForwardedInfo()) || topMessage.viaBotUserId != msg.viaBotUserId || !StringUtils.equalsOrBothEmpty(topMessage.authorSignature, msg.authorSignature) || mergeDisabled() || (useBubbles ? top.isOutgoingBubble() != isOutgoingBubble() : top.getMessage().mediaAlbumId != msg.mediaAlbumId || msg.mediaAlbumId != 0)) {
      setHeaderEnabled(!headerDisabled());
      top.setIsBottom(true);
      return false;
    }

    int maxTimeDiff, maxIndex;

    if (isEventLog()) {
      maxTimeDiff = MAXIMUM_COMMON_MERGE_TIME_DIFF;
      maxIndex = Integer.MAX_VALUE;
    } else if (useBubbles()) {
      maxTimeDiff = /*isChannel() ? MAXIMUM_CHANNEL_MERGE_TIME_DIFF :*/ MAXIMUM_COMMON_MERGE_TIME_DIFF;
      maxIndex = Integer.MAX_VALUE;
    } else if (isChannel) {
      maxTimeDiff = MAXIMUM_CHANNEL_MERGE_TIME_DIFF;
      maxIndex = MAXIMUM_CHANNEL_MERGE_COUNT;
    } else {
      maxTimeDiff = MAXIMUM_COMMON_MERGE_TIME_DIFF;
      maxIndex = MAXIMUM_COMMON_MERGE_COUNT;
    }

    if (!(useBubbles && isChannel && msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginUser.CONSTRUCTOR) &&
      msg.date - top.getMergeTime() < maxTimeDiff && top.getMergeIndex() < maxIndex) {
      flags &= ~FLAG_HEADER_ENABLED;
      mergeTime = top.getMergeTime();
      mergeIndex = top.getMergeIndex() + 1;
      if (top.isForward() && isForward()) {
        flags |= FLAG_MERGE_FORWARD;
        top.setMergeBottom(true);
      } else {
        flags &= ~FLAG_MERGE_FORWARD;
        top.setMergeBottom(false);
      }
      if (isOutgoing()) {
        if (!headerDisabled() && !isChannel && ((isSending() || isUnread()) != (top.isUnread() || top.isSending()))) {
          flags |= FLAG_SHOW_TICKS;
        } else {
          flags &= ~FLAG_SHOW_TICKS;
        }
      }

      top.setIsBottom(false);
      return true;
    }

    setHeaderEnabled(!headerDisabled());
    top.setIsBottom(true);

    return false;
  }

  private static boolean showUnreadAlways (TGMessage msg) {
    return msg instanceof TGMessageMedia || msg instanceof TGMessageSticker;
  }

  protected boolean headerDisabled () {
    return false;
  }

  protected boolean disableBubble () {
    return false;
  }

  protected final boolean useBubble () {
    return useBubbles() && (!disableBubble() || useForward());
  }

  protected boolean separateReplyFromBubble () {
    return false;
  }

  protected boolean mergeDisabled () {
    return false;
  }

  public boolean hasHeader () {
    return !headerDisabled() && (flags & FLAG_HEADER_ENABLED) != 0;
  }

  protected int getSmallestMaxContentWidth () {
    return Math.min(pRealContentMaxWidth, Screen.smallestSide() - xPaddingRight - pRealContentX);
  }

  protected int getSmallestMaxContentHeight () {
    return (int) ((float) getSmallestMaxContentWidth() * 1.24f);
  }

  protected static int getEstimatedContentMaxWidth () {
    return Screen.smallestSide() - xPaddingRight - xContentLeft;
  }

  // Layout

  private int computeBubbleLeft () {
    final int x;
    if (needAvatar() && !isOutgoing()) {
      x = xBubbleLeft1 + Screen.dp(40f);
    } else {
      x = Device.NEED_BIGGER_BUBBLE_OFFSETS ? xBubbleLeft2 : xBubbleLeft1;
    }
    return x;
  }

  private int computeBubbleTop () {
    return getHeaderPadding() + getBubbleViewPaddingTop();
  }

  private int getAuthorWidth () {
    return hAuthorNameT != null ? hAuthorNameT.getWidth() : needName(true) ? -Screen.dp(3f) : 0;
  }

  private int computeBubbleWidth () {
    final int contentWidth = getContentWidth();
    int width = contentWidth;
    if (allowMessageHorizontalExtend()) {
      if (replyData != null) {
        width = Math.max(width, replyData.width(!useBubble()));
      }
      if (needName(true) && (flags & FLAG_HEADER_ENABLED) != 0) {
        int nameWidth = getAuthorWidth();
        if (needAdminSign() && hAdminNameT != null) {
          nameWidth += hAdminNameT.getWidth();
        }
        width = Math.max(width, nameWidth);
      }
      if (hasFooter()) {
        width = Math.max(width, getFooterWidth());
      }
    }

    if (useForward()) {
      if (allowMessageHorizontalExtend()) {
        boolean isPsa = isPsa() && !forceForwardedInfo();
        float forwardWidth = Math.max((isPsa && fPsaTextT != null ? fAuthorNameT.getWidth() : fAuthorNameT != null ? fAuthorNameT.getWidth() : 0) +
          + fTimeWidth + Screen.dp(6f)
          + (getViewCountMode() == VIEW_COUNT_FORWARD ? viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN)) + shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN)) : 0),
          isPsa && fPsaTextT != null && fAuthorNameT != null ? fAuthorNameT.getWidth() : 0);
        width = Math.max(width, Math.max(contentWidth + Screen.dp(11f), (int) (forwardWidth + Screen.dp(11f))));
      } else {
        width = Math.max(width, contentWidth + Screen.dp(11f));
      }
    }

    return width; //  + getBubblePaddingLeft() + getBubblePaddingRight();
  }

  protected final boolean useForward () {
    // && !((flags & FLAG_SELF_CHAT) == 0 && msg.forwardInfo.origin.getConstructor() != TdApi.MessageForwardOriginChannel.CONSTRUCTOR && msg.content != null && msg.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR)
    return msg.forwardInfo != null && (!useBubbles() || !separateReplyFromBubble()) && !forceForwardedInfo();
  }

  private static final int VIEW_COUNT_HIDDEN = 0;
  private static final int VIEW_COUNT_MAIN = 1;
  private static final int VIEW_COUNT_FORWARD = 2;

  private int getViewCountMode () {
    if (viewCounter != null) {
      if (useForward() && !msg.isChannelPost && msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginChannel.CONSTRUCTOR) {
        return VIEW_COUNT_FORWARD;
      }
      if (useBubbles() || BitwiseUtils.getFlag(flags, FLAG_HEADER_ENABLED)) {
        return VIEW_COUNT_MAIN;
      }
    }
    return VIEW_COUNT_HIDDEN;
  }

  protected static final int COMMENT_MODE_NONE = 0;
  protected static final int COMMENT_MODE_BUTTON = 1;
  protected static final int COMMENT_MODE_DETACHED_BUTTON = 2;

  private final BoolAnimator hasCommentButton = new BoolAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      if (BitwiseUtils.getFlag(flags, FLAG_LAYOUT_BUILT)) {
        if (useBubbles()) {
          int height = getHeight();
          buildBubble(false);
          if (getHeight() != height) {
            requestLayout();
          }
        }
      }
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);
  private final BoolAnimator openingComments = new BoolAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

  protected final int getCommentMode () {
    return needCommentButton() ? (!useBubble() || useCircleBubble() ? COMMENT_MODE_DETACHED_BUTTON : COMMENT_MODE_BUTTON) : COMMENT_MODE_NONE;
  }

  public final TdApi.Message findMessageWithThread () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message message : combinedMessages) {
          if (message.canGetMessageThread && (message.interactionInfo != null && message.interactionInfo.replyInfo != null)) {
            return message;
          }
        }
      }
      return msg.canGetMessageThread ? msg : null;
    }
  }

  protected final boolean needCommentButton () {
    if (!Config.COMMENTS_SUPPORTED || !msg.isChannelPost || isScheduled() || !allowInteraction() || isSponsored()) {
      return false;
    }
    TdApi.Message msg = this.msg;
    boolean isSending = isSending();
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message message : combinedMessages) {
          TdApi.MessageReplyInfo combinedReplyInfo = TD.getReplyInfo(message.interactionInfo);
          if (message.canGetMessageThread || (!msg.canGetMessageThread && combinedReplyInfo != null)) {
            msg = message;
            break;
          }
        }
      }
    }
    return (msg.canGetMessageThread || isSending) && TD.getReplyInfo(msg.interactionInfo) != null;
  }

  public final void openMessageThread (MessageId highlightMessageId) {
    if (!Config.COMMENTS_SUPPORTED) {
      tdlib.ui().openMessage(controller(), highlightMessageId.getChatId(), highlightMessageId, openParameters());
      return;
    }
    if (openingComments.getValue())
      return;
    openingComments.setValue(true, needAnimateChanges());
    tdlib.client().send(highlightMessageId != null ? new TdApi.GetMessageThread(highlightMessageId.getChatId(), highlightMessageId.getMessageId()) : new TdApi.GetMessageThread(msg.chatId, getSmallestId()), result -> tdlib.ui().post(() -> {
      switch (result.getConstructor()) {
        case TdApi.MessageThreadInfo.CONSTRUCTOR: {
          TdApi.MessageThreadInfo messageThread = (TdApi.MessageThreadInfo) result;
          TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack().messageThread(new ThreadInfo(getAllMessages(), messageThread, isRepliesChat())).after(chatId -> {
            openingComments.setValue(false, needAnimateChanges());
          });
          if (highlightMessageId != null) {
            params.highlightMessage(highlightMessageId).ensureHighlightAvailable();
          }
          tdlib.ui().openChat(this, messageThread.chatId, params);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(result);
          openingComments.setValue(false, needAnimateChanges());
          break;
        }
      }
    }));
  }

  private int computeBubbleHeight () {
    int height = getContentHeight();
    if (replyData != null && !alignReplyHorizontally()) {
      height += getBubbleReplyOffset();
    }
    if (needName(true) && (flags & FLAG_HEADER_ENABLED) != 0) {
      height += getBubbleNameHeight();
      if (isPsa()) {
        height += getPsaTitleHeight();
      }
    }
    if (useForward()) {
      height += getBubbleForwardOffset();
    }
    if (hasFooter()) {
      height += getFooterHeight() + getFooterPaddingTop() + getFooterPaddingBottom();
    }
    height += getBubbleReduceHeight();

    return height;
  }

  private static int getBubbleForwardOffset () {
    return getBubbleNameHeight();
  }

  private int getBubbleReplyOffset () {
    return ReplyComponent.height() + Screen.dp(useBubble() ? 3f : 6f);
  }

  public void rebuildLayout () {
    final int width = this.width;
    if (width != 0) {
      this.width = 0;
      buildLayout(width);
    }
  }

  public void prepareLayout () {
    if (this.width != 0) {
      rebuildLayout();
    } else {
      buildLayout(Screen.currentWidth());
    }
  }

  public void buildLayout (int width) {
    if (width == 0 || this.width == width) {
      return;
    }

    this.width = width;

    if (useBubbles()) {
      pRealContentX = computeBubbleLeft();
      pRealContentMaxWidth = width - (Device.NEED_BIGGER_BUBBLE_OFFSETS ? xBubbleLeft2 : xBubbleLeft1) - computeBubbleLeft() - (Screen.dp(isThreadHeader() ? 8f : 56f));

      if (useForward()) {
        pRealContentX += Screen.dp(11f);
        pRealContentMaxWidth -= Screen.dp(11f);
      }

      int bubblePaddingLeft = getBubblePaddingLeft();
      int bubblePaddingRight = getBubblePaddingRight();

      pRealContentX += bubblePaddingLeft;
      pRealContentMaxWidth -= bubblePaddingLeft + bubblePaddingRight;
    } else {
      if (!useForward()) {
        pRealContentX = xContentLeft;
        pContentY = ((flags & FLAG_HEADER_ENABLED) != 0 ? xContentTop : xPaddingTop) + getHeaderPadding();
      } else {
        pRealContentX = xfContentLeft;
        pContentY = getForwardTop() + getForwardHeaderHeight();
      }
      pRealContentMaxWidth = width - xPaddingRight - pRealContentX;
      if (isPsa()) {
        pContentY += getPsaTitleHeight();
      }
    }

    updateContentPositions(true);
    if (allowMessageHorizontalExtend()) {
      if (replyData != null && !alignReplyHorizontally()) {
        pContentY += ReplyComponent.height();
      }
      buildHeader();
      buildForward();
      if (alignReplyHorizontally()) {
        buildContent(pContentMaxWidth);
        buildReply();
      } else {
        buildReply();
        buildContent(pContentMaxWidth);
      }
    } else {
      if (replyData != null) {
        pContentY += ReplyComponent.height();
      }
      buildContent(pContentMaxWidth);
      buildHeader();
      buildForward();
      buildReply();
    }

    buildFooter();
    buildBubble(true);

    if (useBubbles()) {
      pContentY = topContentEdge + getBubblePaddingTop();
      if (replyData != null && !alignReplyHorizontally()) {
        pContentY += getBubbleReplyOffset();
      }
      if (needName(true) && (flags & FLAG_HEADER_ENABLED) != 0) {
        pContentY += getBubbleNameHeight();
        if (isPsa()) {
          pContentY += getPsaTitleHeight();
        }
      }
      if (useForward()) {
        pContentY += getBubbleForwardOffset();
      }
    }

    buildMarkup();

    height = computeHeight();

    flags |= FLAG_LAYOUT_BUILT;
  }

  protected int getContentMaxWidth () {
    return pContentMaxWidth;
  }

  public final @ColorInt int getContentReplaceColor () {
    if (useBubbles()) {
      return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_background : R.id.theme_color_bubbleIn_background);
    } else {
      int color = Theme.getColor(R.id.theme_color_chatBackground);
      if (selectionFactor > 0f) {
        return ColorUtils.compositeColor(color, ColorUtils.alphaColor(selectionFactor, Theme.chatSelectionColor()));
      } else {
        return color;
      }
    }
  }

  public final boolean isOutgoingBubble () {
    return useBubbles() && isOutgoing() && !isChannel() && !isEventLog();
  }

  protected boolean alignBubbleRight () {
    return useBubbles() && (isOutgoingBubble() != Lang.rtl());
  }

  private void updateContentPositions (boolean maxOnly) {
    if (useFullWidth()) {
      pContentX = 0;
      pContentMaxWidth = width;
    } else if (alignBubbleRight()) {
      pContentX = width - rightContentEdge - leftContentEdge + pRealContentX;
      pContentMaxWidth = pRealContentMaxWidth;
    } else {
      pContentX = pRealContentX;
      pContentMaxWidth = pRealContentMaxWidth;
    }
  }

  private int measureKeyboardLeft () {
    return useBubbles() ? alignBubbleRight() ? getActualRightContentEdge() - inlineKeyboard.getWidth() : getActualLeftContentEdge() : pRealContentX;
  }

  private int measureKeyboardTop () {
    return useBubbles() ? bottomContentEdge + TGInlineKeyboard.getButtonSpacing() : pContentY + getContentHeight() + getPaddingBottom() + (hasFooter() ? getFooterHeight() + getFooterPaddingTop() + getFooterPaddingBottom() : 0);
  }

  protected boolean rebuildContentDimensions () {
    if ((flags & FLAG_LAYOUT_BUILT) != 0) {
      updateContentPositions(true);
      buildBubble(false);
      int oldHeight = height;
      height = computeHeight();
      return oldHeight != height;
    } else {
      return rebuildContent();
    }
  }

  protected boolean rebuildContent () {
    if ((flags & FLAG_LAYOUT_BUILT) != 0) {
      updateContentPositions(true);
      buildContent(pContentMaxWidth);
      buildBubble(false);
      int oldHeight = height;
      height = computeHeight();
      return oldHeight != height;
    } else {
      rebuildLayout();
      return false;
    }
  }

  protected void rebuildAndUpdateContent () {
    boolean isBackground = Looper.myLooper() != Looper.getMainLooper();
    if (isBackground) {
      if (rebuildContent()) {
        requestLayout();
      } else {
        postInvalidate(true);
      }
    } else {
      if (rebuildContent()) {
        requestLayout();
      } else {
        invalidate(true);
      }
    }
  }

  private int getBubbleViewPaddingTop () {
    return useBubble() ? ((flags & FLAG_HEADER_ENABLED) != 0 ? xBubbleViewPadding : xBubbleViewPaddingSmall) : xBubbleViewPadding;
  }

  private int getBubbleViewPaddingBottom () {
    return useBubble() ? (isBottomMessage() || (inlineKeyboard != null && !inlineKeyboard.isEmpty()) ? xBubbleViewPadding : xBubbleViewPaddingSmall) : xBubbleViewPadding;
  }

  protected final int getPaddingBottom () {
    return useBubbles() ? getBubbleViewPaddingBottom() : xPaddingBottom;
  }

  protected final int getExtraPadding () {
    if (needSponsorSmallPadding) {
      return Screen.dp(7f);
    }

    return (flags & FLAG_EXTRA_PADDING) != 0 ? Screen.dp(7f) + (messagesController().needExtraBigPadding() ? Screen.dp(48f) : 0) : 0;
  }

  public int computeHeight () {
    if (useBubbles()) {
      int height = bottomContentEdge + getPaddingBottom() + getExtraPadding();
      if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
        height += inlineKeyboard.getHeight() + TGInlineKeyboard.getButtonSpacing();
      }
      return height;
    } else {
      int height = pContentY + getContentHeight() + getPaddingBottom() + getExtraPadding();
      if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
        height += inlineKeyboard.getHeight() + xPaddingBottom;
      }
      if (hasFooter()) {
        height += getFooterHeight() + getFooterPaddingTop() + getFooterPaddingBottom();
      }
      return height;
    }
  }

  protected boolean useLargeHeight () {
    return false;
  }

  protected final boolean useFullWidth () {
    return !useForward() && !useBubbles() && preferFullWidth() && !isEventLog();
  }

  protected boolean preferFullWidth () {
    return false;
  }

  protected void buildContent (int maxWidth) { }

  protected int getContentWidth () {
    return pRealContentMaxWidth;
  }

  protected int getContentHeight () { return 10; }

  protected boolean centerBubble () {
    return false;
  }

  // Drawing

  private boolean needHeader () {
    return (flags & FLAG_HEADER_ENABLED) != 0;
  }

  private boolean shouldShowTicks () {
    return !headerDisabled() && !isChannel() && !TD.isFailed(msg) && ((needHeader() && isOutgoing()) || (flags & FLAG_SHOW_TICKS) != 0) && !(!useBubbles() && noUnread() && !needHeader()) && !isFailed();
  }

  private boolean shouldShowEdited () {
    return !headerDisabled() && (isEdited() || isBeingEdited()) && msg.viaBotUserId == 0 && !sender.isBot() && !sender.isServiceAccount() && (useBubbles() ? useBubbleTime() : (!isOutgoing() || hasHeader() || !shouldShowTicks())) && (getViewCount() > 0 || !isEventLog());
  }

  private boolean needAvatar () {
    if (!useBubbles()) {
      return true;
    }
    if (isThreadHeader() && messagesController().getMessageThread().areComments()) {
      return false;
    }
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          return !isOutgoing();
        }
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          return isEventLog() || (tdlib.isSupergroupChat(chat) && !isOutgoing());
        }
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          return ((flags & FLAG_SELF_CHAT) != 0 && !isOutgoing()) || isRepliesChat();
        }
      }
    }
    return !isOutgoing() && isDemoGroupChat();
  }

  protected final boolean isDemoChat () {
    return msg.chatId == 0;
  }

  protected final boolean isDemoGroupChat () {
    return isDemoChat() && manager.isDemoGroupChat();
  }

  protected final TdApi.User userForId (long userId) {
    if (!msg.isOutgoing && isDemoChat())
      return manager.demoParticipant(userId);
    else
      return tdlib.cache().user(userId);
  }

  protected final boolean needName () {
    return needName(true);
  }

  private boolean needName (boolean allowVia) {
    if (!useBubbles() ||
      (useBubble() && ((msg.viaBotUserId != 0 && !useForward() && allowVia) ||
      ((flags & FLAG_SELF_CHAT) != 0 && !isOutgoing())))) {
      return true;
    }
    if (!useBubble() || separateReplyFromBubble()) {
      return false;
    }
    if (isSponsored() && useBubbles())
      return true;
    if (isPsa() && forceForwardedInfo())
      return true;
    if (isOutgoing() && sender.isAnonymousGroupAdmin())
      return true;
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          return !isOutgoing();
        }
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          return !isOutgoing() && !((TdApi.ChatTypeSupergroup) chat.type).isChannel;
        }
      }
    }
    return !isOutgoing() && isDemoGroupChat();
  }

  private boolean useBubbleTime () {
    return !headerDisabled() && (!useForward() || (isOutgoing() || (flags & FLAG_HEADER_ENABLED) != 0)) && (msg.content.getConstructor() != TdApi.MessageCall.CONSTRUCTOR);
  }

  private static Bitmap cornerTopLeftBig, cornerTopLeftSmall;
  private static Bitmap cornerTopRightBig, cornerTopRightSmall;
  private static Bitmap cornerBottomLeftBig, cornerBottomLeftSmall;
  private static Bitmap cornerBottomRightBig, cornerBottomRightSmall;

  private static Paint topShadowPaint, bottomShadowPaint, leftShadowPaint, rightShadowPaint;
  private static Bitmap leftShadow, topShadow, rightShadow, bottomShadow;
  private static Paint shadowPaint;

  private static void initBubbleResources () {
    Resources res = UI.getResources();
    cornerTopLeftSmall = BitmapFactory.decodeResource(res, R.drawable.corner_small_up_left_w);
    cornerTopLeftBig = BitmapFactory.decodeResource(res, R.drawable.corner_big_up_left_w);
    cornerTopRightSmall = BitmapFactory.decodeResource(res, R.drawable.corner_small_up_right_w);
    cornerTopRightBig = BitmapFactory.decodeResource(res, R.drawable.corner_big_up_right_w);

    cornerBottomLeftSmall = BitmapFactory.decodeResource(res, R.drawable.corner_small_down_left_w);
    cornerBottomLeftBig = BitmapFactory.decodeResource(res, R.drawable.corner_big_down_left_w);
    cornerBottomRightSmall = BitmapFactory.decodeResource(res, R.drawable.corner_small_down_right_w);
    cornerBottomRightBig = BitmapFactory.decodeResource(res, R.drawable.corner_big_down_right_w);

    topShadow = BitmapFactory.decodeResource(res, R.drawable.msg_top_w);
    topShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    topShadowPaint.setShader(new BitmapShader(topShadow, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP));

    bottomShadow = BitmapFactory.decodeResource(res, R.drawable.msg_down_w);
    bottomShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    bottomShadowPaint.setShader(new BitmapShader(bottomShadow, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP));

    leftShadow = BitmapFactory.decodeResource(res, R.drawable.msg_left_w);
    leftShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    leftShadowPaint.setShader(new BitmapShader(leftShadow, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT));

    rightShadow = BitmapFactory.decodeResource(res, R.drawable.msg_right_w);
    rightShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    rightShadowPaint.setShader(new BitmapShader(rightShadow, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT));

    shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  }

  protected boolean needBubbleCornerFix () {
    return false;
  }

  private void drawBubbleShadow (Canvas c, float factor) {
    int alpha = (int) (255f * factor);
    if (alpha <= 0) {
      return;
    }

    Paint paint = shadowPaint;
    paint.setAlpha(alpha);

    if (useCircleBubble()) {
      // c.drawBitmap(videoShadow, bubblePathRect.centerX() - videoShadow.getWidth() / 2, bubblePathRect.centerY() - videoShadow.getHeight() / 2, paint);
      return;
    }

    int offset = Screen.dp(2f);

    float left = bubblePathRect.left - offset;
    float top = bubblePathRect.top - offset;
    float right = bubblePathRect.right + offset;
    float bottom = bubblePathRect.bottom + offset;

    Bitmap topLeft = Theme.isBubbleRadiusBig(topLeftRadius) ? cornerTopLeftBig : cornerTopLeftSmall;
    c.drawBitmap(topLeft, left, top, paint);

    Bitmap bottomLeft = Theme.isBubbleRadiusBig(bottomLeftRadius) ? cornerBottomLeftBig : cornerBottomLeftSmall;
    c.drawBitmap(bottomLeft, left, bottom - bottomLeft.getHeight(), paint);

    Bitmap topRight = Theme.isBubbleRadiusBig(topRightRadius) ? cornerTopRightBig : cornerTopRightSmall;
    c.drawBitmap(topRight, right - topRight.getWidth(), top, paint);

    Bitmap bottomRight = Theme.isBubbleRadiusBig(bottomRightRadius) ? cornerBottomRightBig : cornerBottomRightSmall;
    c.drawBitmap(bottomRight, right - bottomRight.getWidth(), bottom - bottomRight.getHeight(), paint);

    paint.setAlpha(255);

    c.save();

    offset = Screen.dp(18f);
    float shadowLeft, shadowTop, shadowRight, shadowBottom;

    float cx = 0f, cy = 0f;
    float tx, ty;

    shadowLeft = left + topLeft.getWidth();
    shadowRight = right - topRight.getWidth();
    if (shadowRight - shadowLeft > 0) {
      topShadowPaint.setAlpha(alpha);
      tx = shadowLeft;
      ty = bubblePathRect.top - topShadow.getHeight() + offset;
      c.translate(tx - cx, ty - cy);
      c.drawRect(0, 0, shadowRight - shadowLeft, topShadow.getHeight(), topShadowPaint);
      cx = tx; cy = ty;
    }

    shadowLeft = left + bottomLeft.getWidth();
    shadowRight = right - bottomRight.getWidth();
    if (shadowRight - shadowLeft > 0) {
      bottomShadowPaint.setAlpha(alpha);
      tx = shadowLeft;
      ty = bubblePathRect.bottom - offset;
      c.translate(tx - cx, ty - cy);
      c.drawRect(0, 0, shadowRight - shadowLeft, bottomShadow.getHeight(), bottomShadowPaint);
      cx = tx; cy = ty;
    }

    shadowTop = top + topLeft.getHeight();
    shadowBottom = bottom - bottomLeft.getHeight();
    if (shadowBottom - shadowTop > 0) {
      leftShadowPaint.setAlpha(alpha);
      tx = bubblePathRect.left - leftShadow.getWidth() + offset;
      ty = shadowTop;
      c.translate(tx - cx, ty - cy);
      c.drawRect(0, 0, leftShadow.getWidth(), shadowBottom - shadowTop, leftShadowPaint);
      cx = tx; cy = ty;
    }

    shadowTop = top + topRight.getHeight();
    shadowBottom = bottom - bottomRight.getHeight();
    if (shadowBottom - shadowTop > 0) {
      rightShadowPaint.setAlpha(alpha);
      tx = bubblePathRect.right - offset;
      ty = shadowTop;
      c.translate(tx - cx, ty - cy);
      c.drawRect(0, 0, rightShadow.getWidth(), shadowBottom - shadowTop, rightShadowPaint);
      cx = tx; cy = ty;
    }

    c.restore();
  }

  public static void drawCornerFixes (Canvas c, TGMessage source, float factor, float left, float top, float right, float bottom, float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
    if (factor == 0f)
      return;
    Paint paint = Paints.strokeBigPaint(ColorUtils.alphaColor(factor, source.getContentReplaceColor()));
    // Paint paint = Paints.strokeBigPaint(0xffff0000);

    float offset = -paint.getStrokeWidth() / 2f;
    float addRadius = 0; // paint.getStrokeWidth();

    left += offset;
    top += offset;
    right -= offset;
    bottom -= offset;

    RectF rectF = Paints.getRectF();

    if (topLeftRadius > 0) {
      float cx = left + topLeftRadius;
      float cy = top + topLeftRadius;
      float radius = topLeftRadius + addRadius;
      rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
      c.drawArc(rectF, 180f, 90f, false, paint);
    }
    if (topRightRadius > 0) {
      float cx = right - topRightRadius;
      float cy = top + topRightRadius;
      float radius = topRightRadius + addRadius;
      rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
      c.drawArc(rectF, 270f, 90f, false, paint);
    }
    if (bottomLeftRadius > 0) {
      float cx = left + bottomLeftRadius;
      float cy = bottom - bottomLeftRadius;
      float radius = bottomLeftRadius + addRadius;
      rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
      c.drawArc(rectF, 90f, 90f, false, paint);
    }
    if (bottomRightRadius > 0) {
      float cx = right - bottomRightRadius;
      float cy = bottom - bottomRightRadius;
      float radius = bottomRightRadius + addRadius;
      rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
      c.drawArc(rectF, 0f, 90f, false, paint);
    }
  }

  private void drawBubble (Canvas c, Paint paint, boolean stroke, int padding) {
    if (paint.getAlpha() == 0) {
      return;
    }

    final float left = bubblePathRect.left - padding;
    final float top = bubblePathRect.top - padding;
    final float right = bubblePathRect.right + padding;
    final float bottom = bubblePathRect.bottom + padding;

    final RectF rectF = Paints.getRectF();
    if (topLeftRadius != 0) {
      rectF.set(left, top, left + topLeftRadius * 2, top + topLeftRadius * 2);
      c.drawArc(rectF, 180f, 90f, !stroke, paint);
      if (topLeftRadius < topRightRadius && !stroke) {
        c.drawRect(left, top + topLeftRadius, left + topLeftRadius, top + topRightRadius, paint);
      }
    }
    if (topRightRadius != 0) {
      rectF.set(right - topRightRadius * 2, top, right, top + topRightRadius * 2);
      c.drawArc(rectF, 270f, 90f, !stroke, paint);
      if (topRightRadius < topLeftRadius && !stroke) {
        c.drawRect(right - topRightRadius, top + topRightRadius, right, top + topLeftRadius, paint);
      }
    }
    if ((topLeftRadius != 0 || topRightRadius != 0) && !stroke) {
      c.drawRect(left + topLeftRadius, top, right - topRightRadius, top + Math.max(topLeftRadius, topRightRadius), paint);
    }
    if (bottomLeftRadius != 0) {
      rectF.set(left, bottom - bottomLeftRadius * 2, left + bottomLeftRadius * 2, bottom);
      c.drawArc(rectF, 90f, 90f, !stroke, paint);
      if (bottomLeftRadius < bottomRightRadius && !stroke) {
        c.drawRect(left, bottom - bottomRightRadius, left + bottomLeftRadius, bottom - bottomLeftRadius, paint);
      }
    }
    if (bottomRightRadius != 0) {
      rectF.set(right - bottomRightRadius * 2, bottom - bottomRightRadius * 2, right, bottom);
      c.drawArc(rectF, 0f, 90f, !stroke, paint);
      if (bottomRightRadius < bottomLeftRadius && !stroke) {
        c.drawRect(right - bottomRightRadius, bottom - bottomLeftRadius, right, bottom - bottomRightRadius, paint);
      }
    }
    if ((bottomLeftRadius != 0 || bottomRightRadius != 0) && !stroke) {
      c.drawRect(left + bottomLeftRadius, bottom - Math.max(bottomLeftRadius, bottomRightRadius), right - bottomRightRadius, bottom, paint);
    }

    if (stroke) {
      c.drawLine(left + topLeftRadius, top, right - topRightRadius, top, paint);
      c.drawLine(left + bottomLeftRadius, bottom, right - bottomRightRadius, bottom, paint);
      c.drawLine(left, top + topLeftRadius, left, bottom - bottomLeftRadius, paint);
      c.drawLine(right, top + topRightRadius, right, bottom - bottomRightRadius, paint);
    } else {
      float bubbleTop = top + Math.max(topLeftRadius, topRightRadius);
      float bubbleBottom = bottom - Math.max(bottomLeftRadius, bottomRightRadius);
      if (bubbleBottom > bubbleTop)
        c.drawRect(left, bubbleTop, right, bubbleBottom, paint);
    }
  }

  public final void drawBackground (MessageView view, Canvas c) {
    if (moveFactor != 0f) {
      c.drawRect(0, findTopEdge(), view.getMeasuredWidth(), findBottomEdge(), Paints.fillingPaint(getSelectionColor(moveFactor)));
    }
    if (selectionFactor != 0f) {
      drawSelection(view, c);
    }
    if (highlightFactor != 0f) {
      drawHighlight(view, c);
    }
  }

  public static int getDateHeight (boolean useBubbles) {
    return (useBubbles ? xDatePadding - Screen.dp(3f) * 2 : xDatePadding);
  }

  public final boolean hasDate () {
    return (flags & FLAG_SHOW_DATE) != 0;
  }

  private Object tag;

  public final void setTag (Object tag) {
    this.tag = tag;
  }

  public final Object getTag () {
    return tag;
  }

  public int getDateWidth () {
    return pDateWidth;
  }

  public int getDatePadding () {
    return Screen.dp(useBubbles() ? 8f : 10f);
  }

  protected final int getBubbleDateBackgroundColor () {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_date, R.id.theme_color_bubble_date_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_DATE);
  }

  protected final int getBubbleDateTextColor () {
    return manager.getColor(0, R.id.theme_color_bubble_dateText, R.id.theme_color_bubble_dateText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_DATE);
  }

  protected final int getUnreadSeparatorBackgroundColor () {
    return manager.getOverlayColor(R.id.theme_color_unread, R.id.theme_color_bubble_unread, R.id.theme_color_bubble_unread_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_UNREAD);
  }

  protected final int getUnreadSeparatorContentColor () {
    return manager.getColor(R.id.theme_color_unreadText, R.id.theme_color_bubble_unreadText, R.id.theme_color_bubble_unreadText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_UNREAD);
  }

  public final int getBubbleMediaReplyBackgroundColor () {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_mediaReply, R.id.theme_color_bubble_mediaReply_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY);
  }

  public final int getBubbleMediaReplyTextColor () {
    return manager.getColor(0, R.id.theme_color_bubble_mediaReplyText, R.id.theme_color_bubble_mediaReplyText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY);
  }

  protected final int getBubbleTimeColor () {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_mediaTime, R.id.theme_color_bubble_mediaTime_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_TIME);
  }

  protected final int getBubbleTimeTextColor () {
    return manager.getColor(0, R.id.theme_color_bubble_mediaTimeText, R.id.theme_color_bubble_mediaTimeText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_TIME);
  }

  public final int getBubbleButtonBackgroundColor () {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_button, R.id.theme_color_bubble_button_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_BUTTON);
  }

  public final int getBubbleButtonRippleColor () {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_buttonRipple, R.id.theme_color_bubble_buttonRipple_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_BUTTON);
  }

  public final int getBubbleButtonTextColor () {
    return manager.getColor(0, R.id.theme_color_bubble_buttonText, R.id.theme_color_bubble_buttonText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_BUTTON);
  }

  public static int getBubbleTransparentColor (MessagesManager manager) {
    return manager.getOverlayColor(0, R.id.theme_color_bubble_overlay, R.id.theme_color_bubble_overlay_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY);
  }

  public static int getBubbleTransparentTextColor (MessagesManager manager) {
    return manager.getColor(0, R.id.theme_color_bubble_overlayText, R.id.theme_color_bubble_overlayText_noWallpaper, ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY);
  }

  public boolean drawDate (Canvas c, int centerX, int startY, float detachFactor, float alpha) {
    if (!hasDate()) {
      return false;
    }
    if (alpha == 0f) {
      return true;
    }
    RectF rectF = Paints.getRectF();
    int textX, textY;
    TextPaint textPaint;
    if (useBubbles()) {
      int color = getBubbleDateBackgroundColor();
      int textColor = getBubbleDateTextColor();

      int padding = Screen.dp(8f);
      rectF.set(centerX - pDateWidth / 2 - padding, startY + Screen.dp(5f), centerX + pDateWidth / 2 + padding, startY + Screen.dp(5f) + Screen.dp(26f));
      int radius = Screen.dp(Theme.getBubbleDateRadius());
      c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, color)));
      textX = centerX - pDateWidth / 2;
      textY = startY + xDateTop - Screen.dp(3f);
      textPaint = Paints.getBoldPaint13((flags & FLAG_DATE_FAKE_BOLD) != 0, textColor);
    } else {
      if (detachFactor > 0f) {
        int padding = Screen.dp(10f);
        rectF.set(centerX - pDateWidth / 2 - padding, startY + Screen.dp(8f), centerX + pDateWidth / 2 + padding, startY + Screen.dp(8f) + Screen.dp(26f));
        int radius = Screen.dp(Theme.getDateRadius());
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha * detachFactor, Theme.getColor(R.id.theme_color_chatBackground))));
        c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(ColorUtils.alphaColor(alpha * detachFactor, Theme.separatorColor()), Math.max(1, Screen.dp(.5f))));
      }
      textX = centerX - pDateWidth / 2;
      textY = startY + xDateTop;
      if ((flags & FLAG_SHOW_BADGE) != 0) {
        textY += Screen.dp(1f);
      }
      textPaint = Paints.getTitlePaint((flags & FLAG_DATE_FAKE_BOLD) != 0);
    }
    int savedAlpha = alpha != 1f ? textPaint.getAlpha() : 255;
    if (alpha != 1f) {
      textPaint.setAlpha((int) ((float) savedAlpha * alpha));
    }
    c.drawText(date, textX, textY, textPaint);
    if (alpha != 1f) {
      textPaint.setAlpha(savedAlpha);
    }
    return true;
  }

  public String getDrawDateText () {
    return date;
  }

  public final int getDrawDateY () {
    return (flags & FLAG_SHOW_BADGE) != 0 ? xBadgeHeight + (useBubbles() ? Screen.dp(3.5f) : 0) : 0;
  }

  public final boolean separateReplyFromContent () {
    return useBubbles() && (!useBubble() || separateReplyFromBubble());
  }

  public final void draw (MessageView view, Canvas c, @NonNull ImageReceiver avatarReceiver, Receiver replyReceiver, DoubleImageReceiver previewReceiver, ImageReceiver contentReceiver, GifReceiver gifReceiver, ComplexReceiver complexReceiver) {
    final int viewWidth = view.getMeasuredWidth();
    final int viewHeight = view.getMeasuredHeight();

    final boolean useBubbles = useBubbles();

    final float selectableFactor = manager.getSelectableFactor();

    checkEdges();

    // Unread messages badge
    if ((flags & FLAG_SHOW_BADGE) != 0) {
      int top = 0;
      if (useBubbles) {
        top += (flags & FLAG_SHOW_DATE) != 0 ? Screen.dp(3.5f) : Screen.dp(2f);
      }
      int bottom = xBadgeHeight + top;
      float shadow = Theme.getBubbleUnreadShadow();
      if (shadow > 0f) {
        ShadowView.drawTopShadow(c, 0, width, top, shadow);
        ShadowView.drawBottomShadow(c, 0, width, bottom, shadow);
      }
      c.drawRect(0, top, width, bottom, Paints.fillingPaint(getUnreadSeparatorBackgroundColor()));
      TextPaint mBadge = getBadgePaint(uBadge.needFakeBold);
      int color = getUnreadSeparatorContentColor();
      mBadge.setColor(color);
      c.drawText(uBadge.text, pBadgeX, xBadgeTop + top, mBadge);
      int iconTop = Screen.dp(4.5f);
      Drawables.draw(c, iBadge, pBadgeIconX, iconTop + top, Paints.getUnreadSeparationPaint(color));
    }

    if (useBubbles && !needViewGroup()) {
      drawBackground(view, c);
    }

    final boolean savedTranslation = translation != 0f;
    if (savedTranslation) {
      c.save();
      c.translate(translation, 0);
    }

    // selection and highlight background
    if (!useBubbles && !needViewGroup()) {
      drawBackground(view, c);
    }

    final int contentOffset = getSelectableContentOffset(selectableFactor);
    if (contentOffset != 0) {
      c.save();
      c.translate(contentOffset * (Lang.rtl() ? -1 : 1), 0);
    }

    boolean hasBubble = useBubbles && useBubble();
    float lineFactor = 0f;
    if (hasBubble) {
      final int bubbleColor = Theme.getColor(isOutgoingBubble() && !useCircleBubble() ? R.id.theme_color_bubbleOut_background : R.id.theme_color_bubbleIn_background);
      lineFactor = Theme.getBubbleOutlineFactor();
      /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && lineFactor < 1f) {
        c.save();
        int offset = Screen.dp(1.5f);
        c.clipRect(getActualLeftContentEdge(), bottomContentEdge - (xBubblePadding + xBubblePaddingSmall) * 2, getActualRightContentEdge(), bottomContentEdge + offset * 2);
        c.translate(0, offset);
        drawBubble(c, Paints.fillingPaint(Utils.alphaColor((1f - lineFactor) * .2f, 0xff000000)), false);
        c.restore();
      }*/
      if (lineFactor != 1f) {
        drawBubbleShadow(c, 1f - lineFactor);
      }
      if (lineFactor != 0f) {
        int outlineColor = getOutlineColor();
        float outlineWidthDp = Theme.getBubbleOutlineSize();
        if (outlineWidthDp > 0f) {
          int outlineWidth = Math.max(1, Screen.dp(outlineWidthDp));
          drawBubble(c, Paints.getProgressPaint(ColorUtils.alphaColor(lineFactor, outlineColor), outlineWidth), true, 0);
        }
      }
      drawBubble(c, Paints.fillingPaint(bubbleColor), false, 0);

      float commentButton = hasCommentButton.getFloatValue();
      if (commentButton > 0f) {
        int y = bottomContentEdge - getBubbleReduceHeight();
        c.drawLine(leftContentEdge, y, rightContentEdge, y, Paints.strokeSeparatorPaint(ColorUtils.alphaColor(commentButton, getSeparatorColor())));
        if (commentButton != 1f) {
          c.save();
          c.clipRect(leftContentEdge, y, rightContentEdge, bottomContentEdge);
        }
        drawCommentButton(view, c, leftContentEdge, rightContentEdge, y, commentButton);
        if (commentButton != 1f) {
          c.restore();
        }
      }
    }

    // Content universal
    if (needComplexReceiver()) {
      drawContent(view, c, pContentX, pContentY, pContentMaxWidth, complexReceiver);
    } else if (needGifReceiver()) {
      drawContent(view, c, pContentX, pContentY, pContentMaxWidth, previewReceiver, gifReceiver);
    } else if (needImageReceiver()) {
      drawContent(view, c, pContentX, pContentY, pContentMaxWidth, previewReceiver, contentReceiver);
    } else {
      drawContent(view, c, pContentX, pContentY, pContentMaxWidth);
    }

    if (hasBubble && needBubbleCornerFix()) {
      int padding = getBubbleContentPadding();
      drawCornerFixes(c, this, 1f,
        bubblePathRect.left + padding, bubblePathRect.top + padding, bubblePathRect.right - padding, bubblePathRect.bottom - padding,
        topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
    }

    if (hasFooter()) {
      drawFooter(c);
    }

    // Header
    if ((flags & FLAG_HEADER_ENABLED) != 0) {
      // Avatar
      if (needAvatar()) {
        float cx = avatarReceiver.centerX();
        float cy = avatarReceiver.centerY();
        if (useFullWidth()) {
          c.drawCircle(cx, cy, xAvatarRadius + Screen.dp(2.5f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_chatBackground)));
        }
        if (hAvatar != null) {
          if (avatarReceiver.needPlaceholder())
            avatarReceiver.drawPlaceholderRounded(c, useBubbles ? xBubbleAvatarRadius : xAvatarRadius);
          avatarReceiver.draw(c);
        } else if (hAvatarPlaceholder != null) {
          hAvatarPlaceholder.draw(c, (int) cx, (int) cy);
        }
      }

      // Author
      if (needName(true)) {
        int left = useBubbles ? getActualLeftContentEdge() + xBubblePadding + xBubblePaddingSmall : xContentLeft;
        int top = useBubbles ? topContentEdge + xBubbleNameTop : xNameTop + getHeaderPadding();
        boolean isPsa = isPsa() && forceForwardedInfo();
        if (hAuthorNameT != null) {
          int newTop = useBubbles ? topContentEdge + Screen.dp(9f) : getHeaderPadding() + Screen.dp(1f);
          if (isPsa && hPsaTextT != null) {
            hPsaTextT.draw(c, left, left + hPsaTextT.getWidth(), 0, newTop);
            newTop += getPsaTitleHeight();
          }
          hAuthorNameT.draw(c, left, left + hAuthorNameT.getWidth(), 0, newTop);
        }
        if (useBubbles && needAdminSign() && hAdminNameT != null) {
          int x = getActualRightContentEdge() - xBubblePadding - xBubblePaddingSmall - hAdminNameT.getWidth();
          int y = top - Screen.dp(1.5f);
          hAdminNameT.draw(c, x, top - Screen.dp(12f));
        }
      }
    }

    if (!useBubbles) {
      // Plain mode time part

      boolean needMetadata = BitwiseUtils.getFlag(flags, FLAG_HEADER_ENABLED);
      int top = getHeaderPadding() + xViewsOffset + Screen.dp(7f);

      // Time
      if (needMetadata) {
        c.drawText(time, pTimeLeft, xTimeTop + getHeaderPadding(), mTime(true));
      }

      // Clock, tick and views
      if (isSending()) {
        Drawables.draw(c, Icons.getClockIcon(R.id.theme_color_iconLight), pClockLeft - Icons.getClockIconWidth() - Screen.dp(Icons.CLOCK_SHIFT_X), pClockTop - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint());
      } else if (isFailed()) {
        // TODO failure icon
      } else if (shouldShowTicks() && getViewCountMode() != VIEW_COUNT_MAIN) {
        boolean unread = isUnread() && !noUnread();
        Drawables.draw(c, unread ? Icons.getSingleTick(R.id.theme_color_ticks) : Icons.getDoubleTick(R.id.theme_color_ticksRead), pTicksLeft - Icons.getSingleTickWidth() + ((flags & FLAG_HEADER_ENABLED) != 0 ? 0 : Screen.dp(1f)) - Screen.dp(Icons.TICKS_SHIFT_X), pTicksTop - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint());
      }

      int right = pTicksLeft - (shouldShowTicks() ? Icons.getSingleTickWidth() + Screen.dp(2.5f) : 0); //needMetadata ? pTimeLeft - Screen.dp(4f) : pTicksLeft;

      // Edited
      if (shouldShowEdited()) {
        // right -= Icons.getEditedIconWidth();
        right -= Icons.getEditedIconWidth();
        if (isBeingEdited()) {
          Drawables.draw(c, Icons.getClockIcon(R.id.theme_color_iconLight), pTicksLeft - (shouldShowTicks() ? Icons.getSingleTickWidth() + Screen.dp(2.5f) : 0) - Icons.getEditedIconWidth() - Screen.dp(6f), pTicksTop - Screen.dp(5f), Paints.getIconLightPorterDuffPaint());
        } else {
          Drawables.draw(c, view.getSparseDrawable(R.drawable.baseline_edit_12, 0), right, pTicksTop, Paints.getIconLightPorterDuffPaint());
        }
        right -= Screen.dp(COUNTER_ADD_MARGIN);
      }

      isPinned.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
      right -= isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN)) + Screen.dp(COUNTER_ADD_MARGIN);
      if (needMetadata) {
        if (getCommentMode() == COMMENT_MODE_NONE) {
          replyCounter.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
          right -= replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
        }
        if (getViewCountMode() == VIEW_COUNT_MAIN) {
          shareCounter.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
          right -= shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
          viewCounter.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
          right -= viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
        }
      }
    }

    // Check box
    if (useBubbles) {
      if (selectableFactor > 0 && canBeSelected()) {
        int centerY = (findTopEdge() / 2 + findBottomEdge() / 2);
        int centerX = Screen.dp(18f) - contentOffset;
        if (contentOffset != 0) {
          centerX -= Screen.dp(28f) * (1f - selectableFactor);
        }
        if (Lang.rtl()) {
          centerX = viewWidth - centerX;
        }

        // float darkFactor = Theme.getDarkFactor();
        float transparency = manager.controller().wallpaper().getBackgroundTransparency();
        c.drawCircle(centerX, centerY, Screen.dp(9f) + (int) (Screen.dp(1f) * (1f - transparency)), Paints.strokeBigPaint(ColorUtils.alphaColor(selectableFactor, ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_bubble_messageCheckOutline), Theme.getColor(R.id.theme_color_bubble_messageCheckOutlineNoWallpaper), transparency))));
        SimplestCheckBox.draw(c, centerX, centerY, selectionFactor, null);
      }
    } else if (selectionFactor > 0f) {
      double radians = Math.toRadians(45f);

      int x = (int) (xAvatarCenterX + (float) ((double) (xAvatarRadius) * Math.sin(radians)));
      int y;

      if ((flags & FLAG_HEADER_ENABLED) != 0) {
        y = (int) (xAvatarRadius + getHeaderPadding() + (float) ((double) (xAvatarRadius) * Math.cos(radians)));
      } else {
        y = pContentY + Screen.dp(8f);
      }

      RectF rectF = Paints.getRectF();
      int radius = Screen.dp(11f);
      rectF.set(x - radius, y - radius, x + radius, y + radius);

      if ((flags & FLAG_HEADER_ENABLED) != 0) {
        if (useFullWidth()) {
          final int color = Theme.getColor(R.id.theme_color_chatBackground);
          c.drawArc(rectF, 135f, 170f * selectionFactor, false, Paints.getOuterCheckPaint(color));
          c.drawArc(rectF, 305f, 195f * selectionFactor, false, Paints.getOuterCheckPaint(color));
        } else {
          c.drawArc(rectF, 135f, 170f * selectionFactor, false, Paints.getOuterCheckPaint(getSelectionColor(1f)));
        }
      } else if (useFullWidth()) {
        c.drawArc(rectF, 305f, 290f * selectionFactor, false, Paints.getOuterCheckPaint(getSelectionColor(1f)));
      }

      SimplestCheckBox.draw(c, x, y, selectionFactor, null);
    }

    // Inline keyboard
    if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
      inlineKeyboard.draw(view, c, measureKeyboardLeft(), measureKeyboardTop());
    }

    if (useBubbles) {
      if (!needViewGroup() && useBubbleTime()) {
        drawBubbleTimePart(c, view);
      }
    }

    // forward
    if (useForward()) {
      int forwardY = getForwardTop();
      int forwardTextTop = useBubbles ? pContentY - getBubbleForwardOffset() + Screen.dp(15f) : forwardY + Screen.dp(16f);

      // forward author and time
      boolean isPsa = isPsa() && !forceForwardedInfo();
      int nameColor = isPsa ? getChatAuthorPsaColor() : getChatAuthorColor();
      int forwardTextLeft = getForwardAuthorNameLeft();

      int forwardX = forwardTextLeft + (isPsa ? (fPsaTextT != null ? fPsaTextT.getWidth() : 0) : (fAuthorNameT != null ? fAuthorNameT.getWidth() : 0)) + Screen.dp(6f);
      TextPaint mTimePaint = useBubbles ? Paints.colorPaint(mTimeBubble(), getDecentColor()) : mTime(true);
      c.drawText(fTime, forwardX, forwardTextTop, mTimePaint);
      if (getViewCountMode() == VIEW_COUNT_FORWARD) {
        forwardX += Screen.dp(2f) + fTimeWidth + Screen.dp(COUNTER_ADD_MARGIN);
        int iconTop = forwardTextTop - Screen.dp(3f);
        viewCounter.draw(c, forwardX, iconTop, Gravity.LEFT, 1f, view, getDecentIconColorId());
        forwardX += viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
        shareCounter.draw(c, forwardX, iconTop, Gravity.LEFT, 1f, view, getDecentIconColorId());
        forwardX += shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      }
      if (isPsa && fPsaTextT != null) {
        fPsaTextT.draw(c, forwardTextLeft, forwardTextLeft + fPsaTextT.getWidth(), 0, forwardTextTop - Screen.dp(13f));
        forwardTextTop += getPsaTitleHeight();
      }
      if (fAuthorNameT != null) {
        fAuthorNameT.draw(c, forwardTextLeft, forwardTextLeft + fAuthorNameT.getWidth(), 0, forwardTextTop - Screen.dp(13f));
      }

      int lineTop;
      int lineBottom;
      boolean mergeTop, mergeBottom;

      if (useBubbles) {
        lineTop = forwardY;
        lineBottom = bottomContentEdge - xBubblePadding - xBubblePaddingSmall - (useBubbleTime() ? getBubbleTimePartHeight() : 0) - getBubbleReduceHeight();
        mergeBottom = mergeTop = false;
      } else {
        if ((flags & FLAG_MERGE_FORWARD) != 0 && (flags & FLAG_HEADER_ENABLED) == 0) {
          lineTop = 0;
          mergeTop = true;
        } else {
          lineTop = forwardY;
          mergeTop = false;
        }

        if ((flags & FLAG_MERGE_BOTTOM) != 0) {
          lineBottom = height;
          mergeBottom = true;
        } else {
          lineBottom = forwardY + getForwardHeight();
          mergeBottom = false;
        }
      }

      int lineWidth = Screen.dp(3f);
      int lineLeft = getForwardLeft();
      int lineRight = lineLeft + lineWidth;

      RectF rectF = Paints.getRectF();
      rectF.set(lineLeft, lineTop, lineRight, lineBottom);
      final int lineColor = getVerticalLineColor();
      c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(lineColor));

      if (mergeTop) {
        c.drawRect(lineLeft, lineTop, lineRight, lineTop + lineWidth, Paints.fillingPaint(lineColor));
      }
      if (mergeBottom) {
        c.drawRect(lineLeft, lineBottom - lineWidth, lineRight, lineBottom, Paints.fillingPaint(lineColor));
      }
      // c.drawRect(getForwardLeft(), lineTop, getForwardLeft() + Screen.dp(3f), lineBottom, Paints.fillingPaint(LINE_COLOR));
    }

    // reply
    if (replyData != null) {
      int startX, top, endX;

      int width = replyData.width(useBubbles && !useBubble());

      top = useBubbles ? topContentEdge + (useBubble() ? xBubblePadding + xBubblePaddingSmall : Screen.dp(8f)) + (useBubbleName() ? getBubbleNameHeight() : 0) : pContentY - ReplyComponent.height();

      if (useBubbles) {
        if (isOutgoing() && (!useBubble() || separateReplyFromBubble())) {
          startX = getActualRightContentEdge() - width;
          endX = getInternalBubbleEndX();
          if (alignReplyHorizontally()) {
            startX -= getContentWidth();
            top = topContentEdge + (bottomContentEdge - topContentEdge) / 2 - ReplyComponent.height() / 2;
          }
        } else {
          startX = getInternalBubbleStartX();
          endX = getInternalBubbleEndX();
          if (alignReplyHorizontally()) {
            startX += getContentWidth();
            top = topContentEdge + (bottomContentEdge - topContentEdge) / 2 - ReplyComponent.height() / 2;
          }
        }
      } else if (useFullWidth()) {
        startX = pRealContentX;
        endX = viewWidth - startX;
      } else {
        startX = pContentX;
        endX = viewWidth - startX;
      }

      replyData.draw(c, startX, top, endX, width, replyReceiver, Lang.rtl());
    }

    if (contentOffset != 0) {
      c.restore();
    }

    if (savedTranslation) {
      c.restore();
      if (!needViewGroup()) {
        drawTranslate(view, c);
      }
    } else if (dismissFactor != 0f && !needViewGroup()) {
      drawTranslate(view, c);
    }
  }

  protected final boolean needColoredNames () {
    return !msg.isOutgoing && (TD.isMultiChat(chat) || isDemoGroupChat());
  }

  private int getInternalBubbleStartX () {
    return getActualLeftContentEdge() + xBubblePadding + xBubblePaddingSmall;
  }

  private int getInternalBubbleEndX () {
    return getActualRightContentEdge() - xBubblePadding - xBubblePaddingSmall;
  }

  public final int getSelectableContentOffset (float selectableFactor) {
    return useBubbles() && !isOutgoingBubble() && !headerDisabled() ? (int) (Screen.dp(28f) * selectableFactor) : 0;
  }

  public final void drawOverlay (MessageView view, Canvas c) {
    int contentOffset = getSelectableContentOffset(manager.getSelectableFactor());
    MessageViewGroup parentViewGroup = view.getParentMessageViewGroup();
    if (parentViewGroup != null) {
      parentViewGroup.setSelectableTranslation(contentOffset);
    }
    if (contentOffset != 0) {
      c.save();
      c.translate(contentOffset, 0);
    }
    final boolean savedTranslation = translation != 0f;
    if (savedTranslation) {
      c.save();
      c.translate(translation, 0);
    }
    drawOverlay(view, c, pContentX, pContentY, pContentMaxWidth);
    if (savedTranslation) {
      c.restore();
      drawTranslate(view, c);
    } else if (dismissFactor != 0f) {
      drawTranslate(view, c);
    }
    if (useBubbles()) {
      if (useBubbleTime()) {
        drawBubbleTimePart(c, view);
      }
    }
    if (contentOffset != 0) {
      c.restore();
    }
  }

  protected void drawOverlay (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    // Override
  }

  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    // These two dots should never appear in MessagesListView

    c.drawCircle(startX + 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
    c.drawCircle(startX + maxWidth - 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
  }

  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver receiver) {
    // These two dots should never appear in MessagesListView

    c.drawCircle(startX + 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
    c.drawCircle(startX + maxWidth - 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
  }

  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    // These two dots should never appear in MessagesListView

    c.drawCircle(startX + 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
    c.drawCircle(startX + maxWidth - 5f, startY + 5f, 5f, Paints.fillingPaint(Theme.radioFillingColor()));
  }

  public boolean needImageReceiver () {
    return false;
  }

  public boolean needViewGroup () {
    return false;
  }

  public int getChildrenWidth () {
    return getContentWidth();
  }

  public int getChildrenHeight () {
    return getContentHeight();
  }

  public int getChildrenLeft () {
    return pContentX;
  }

  public int getChildrenTop () {
    return pContentY;
  }

  public boolean needGifReceiver () {
    return false;
  }

  public boolean needComplexReceiver () {
    return false;
  }

  // Touch

  private void setViewAttached (boolean isAttached) {
    boolean nowIsAttached = (flags & FLAG_ATTACHED) != 0;
    if (isAttached != nowIsAttached) {
      flags = BitwiseUtils.setFlag(flags, FLAG_ATTACHED, isAttached);
      onMessageAttachStateChange(isAttached);
      if (!Config.READ_MESSAGES_BEFORE_FOCUS && isAttached) {
        manager.viewMessages();
      }
    }
  }

  protected void onMessageAttachStateChange (boolean isAttached) {
    // override
  }

  public void handleUiMessage (int what, int arg1, int arg2) {
    // override
  }

  private boolean hasAttachedToAnything () {
    return currentViews.hasAnyTargetToInvalidate() || (overlayViews != null && overlayViews.hasAnyTargetToInvalidate());
  }

  public final void onAttachedToView (@Nullable MessageView view) {
    setViewAttached(view != null || hasAttachedToAnything());
    if (currentViews.attachToView(view) && view != null) {
      onMessageAttachedToView(view, true);
    }
  }

  public final void onDetachedFromView (@Nullable MessageView view) {
    if (currentViews.detachFromView(view) && view != null) {
      onMessageAttachedToView(view, false);
    }
    setViewAttached(hasAttachedToAnything());
  }

  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) { }

  public final void onAttachedToOverlayView (@Nullable View view) {
    setViewAttached(view != null || hasAttachedToAnything());
    if (view != null && overlayViews != null) {
      if (overlayViews.attachToView(view)) {
        onMessageAttachedToOverlayView(view, true);
      }
    }
  }

  public final void onDetachedFromOverlayView (@Nullable View view) {
    if (overlayViews != null && overlayViews.detachFromView(view) && view != null) {
      onMessageAttachedToOverlayView(view, false);
    }
    setViewAttached(hasAttachedToAnything());
  }

  protected void onMessageAttachedToOverlayView (@NonNull View view, boolean attached) { }

  // Invalidate attached views

  public final void requestLayout () {
    currentViews.requestLayout();
  }

  public final boolean hasAnyTargetToInvalidate () {
    return currentViews.hasAnyTargetToInvalidate();
  }

  /**
   * Synonym to {@link #hasAnyTargetToInvalidate()}
   * @return false, when layout must be updated immediately
   */
  protected final boolean needAnimateChanges () {
    return hasAnyTargetToInvalidate() && controller().getParentOrSelf().isAttachedToNavigationController() && BitwiseUtils.getFlag(flags, FLAG_LAYOUT_BUILT) && UI.inUiThread();
  }

  public final void invalidate () {
    currentViews.invalidate();
  }

  public final void invalidateParentOrSelf (boolean invalidateOverlay) {
    invalidate();
    if (needViewGroup()) {
      invalidateParent();
    }
    if (invalidateOverlay) {
      invalidateOverlay();
    }
  }

  public final void invalidateParentOrSelf (int left, int top, int right, int bottom, boolean invalidateOverlay) {
    invalidate(left, top, right, bottom);
    if (needViewGroup()) {
      invalidateParent(left, top, right, bottom);
    }
    if (invalidateOverlay) {
      invalidateOverlay();
    }
  }

  public final void invalidateParent () {
    currentViews.invalidateParent();
  }

  public final void invalidateParent (int left, int top, int right, int bottom) {
    currentViews.invalidateParent(left, top, right, bottom);
  }

  public final void invalidate (boolean withOverlay) {
    currentViews.invalidate();
    if (withOverlay && overlayViews != null) {
      overlayViews.invalidate();
    }
  }

  public final void invalidateOverlay () {
    if (overlayViews != null) {
      overlayViews.invalidate();
    }
  }

  public final void invalidateOverlay (int left, int top, int right, int bottom) {
    if (overlayViews != null) {
      overlayViews.invalidate(left, top, right, bottom);
    }
  }

  /*public final void invalidateOutline (boolean withInvalidate) {
    currentViews.invalidateOutline(withInvalidate);
  }*/

  public final void invalidate (int left, int top, int right, int bottom) {
    currentViews.invalidate(left, top, right, bottom);
  }

  public final void postInvalidate () {
    currentViews.postInvalidate();
  }

  public final void postInvalidate (boolean withOverlay) {
    currentViews.postInvalidate();
    if (withOverlay && overlayViews != null) {
      overlayViews.postInvalidate();
    }
  }

  public final void performClickSoundFeedback () {
    currentViews.performClickSoundFeedback();
  }

  public final void performShakeAnimation (boolean isPositive) {
    performWithViews(view -> view.shake(isPositive));
  }

  public final void performConfettiAnimation (int pivotX, int pivotY) {
    performWithViews(view -> context().performConfetti(view, pivotX, pivotY));
  }

  private void performWithViews (@NonNull RunnableData<MessageView> act) {
    final ReferenceList<View> attachedToViews = currentViews.getViewsList();
    for (View view : attachedToViews) {
      act.runWithData((MessageView) view);
    }
  }

  public final @Nullable View findCurrentView () {
    return currentViews.findAnyTarget();
  }

  @Override
  public final void invalidateContent () {
    invalidateContentReceiver();
  }

  public final void invalidateContentReceiver (long messageId, int arg) {
    performWithViews(view -> view.invalidateContentReceiver(msg.chatId, messageId, arg));
  }

  public final void invalidatePreviewReceiver () {
    performWithViews(view -> view.invalidatePreviewReceiver(msg.chatId, msg.id));
  }

  public final void invalidateContentReceiver () {
    performWithViews(view -> view.invalidateContentReceiver(msg.chatId, msg.id, -1));
  }

  public final void invalidateContentReceiver (int arg) {
    performWithViews(view -> view.invalidateContentReceiver(msg.chatId, msg.id, arg));
  }

  public final void invalidateReplyReceiver () {
    performWithViews(view -> view.invalidateReplyReceiver(msg.chatId, msg.id));
  }

  // Touch

  public boolean allowLongPress (float x, float y) {
    return true;
  }

  @CallSuper
  public boolean performLongPress (View view, float x, float y) {
    if (isSponsored()) {
      return false;
    }
    boolean result = false;
    if (inlineKeyboard != null) {
      result = inlineKeyboard.performLongPress(view);
    }
    if (hasFooter()) {
      result = footerText.performLongPress(view) || result;
    }
    clickHelper.cancel(view, x, y);
    return result;
  }

  public boolean shouldIgnoreTap (MotionEvent e) {
    // TODO ignore date & unread messages tap
    return false;
  }

  private int getClickType (MessageView view, float x, float y) {
    if (replyData != null && replyData.isInside(x, y, useBubbles() && !useBubble())) {
      return CLICK_TYPE_REPLY;
    }
    if (hasHeader() && needAvatar() && view.getAvatarReceiver().isInsideReceiver(x, y)) {
      return CLICK_TYPE_AVATAR;
    }
    switch (getCommentMode()) {
      case COMMENT_MODE_BUTTON:
        if (useBubbles()) {
          if (x >= leftContentEdge && x < rightContentEdge && y >= bottomContentEdge - getBubbleReduceHeight() && y < bottomContentEdge) {
            return CLICK_TYPE_COMMENTS;
          }
        }
        break;
      case COMMENT_MODE_DETACHED_BUTTON:
        // TODO
        break;
    }
    return CLICK_TYPE_NONE;
  }

  private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      clickType = getClickType((MessageView) view, x, y);
      return clickType != CLICK_TYPE_NONE;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      switch (clickType) {
        case CLICK_TYPE_REPLY: {
          if (replyData != null && replyData.hasValidMessage()) {
            if (msg.replyInChatId != msg.chatId) {
              openMessageThread(new MessageId(msg.replyInChatId, msg.replyToMessageId));
            } else {
              highlightOtherMessage(msg.replyToMessageId);
            }
          }
          break;
        }
        case CLICK_TYPE_AVATAR: {
          openProfile(view, null, null, null, ((MessageView) view).getAvatarReceiver());
          break;
        }
        case CLICK_TYPE_COMMENTS: {
          openMessageThread(null);
          break;
        }
      }
      clickType = CLICK_TYPE_NONE;
    }
  });

  protected final void highlightOtherMessage (long otherMessageId) {
    manager.controller().highlightMessage(new MessageId(msg.chatId, otherMessageId), toMessageId());
  }

  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    /*if ((flags & FLAG_HEADER_ENABLED) == 0 && (msg.forwardInfo == null || forwardInfo == null) && replyData == null && (inlineKeyboard == null || inlineKeyboard.isEmpty())) {
      return false;
    }*/
    if (hasHeader() && needName(true)) {
      if (hAuthorNameT != null && hAuthorNameT.onTouchEvent(view, e))
        return true;
      if (hPsaTextT != null && hPsaTextT.onTouchEvent(view, e))
        return true;
    }
    if (useForward()) {
      if (fPsaTextT != null && fPsaTextT.onTouchEvent(view, e))
        return true;
      if (fAuthorNameT != null && fAuthorNameT.onTouchEvent(view, e))
        return true;
    }
    if (hasFooter()) {
      if (footerText.onTouchEvent(view, e))
        return true;
    }
    if (!isEventLog() && inlineKeyboard != null && !inlineKeyboard.isEmpty() && inlineKeyboard.onTouchEvent(view, e)) {
      return true;
    }
    return clickHelper.onTouchEvent(view, e);
  }

  private static final int CLICK_TYPE_NONE = 0;
  private static final int CLICK_TYPE_REPLY = 1;
  private static final int CLICK_TYPE_AVATAR = 2;
  private static final int CLICK_TYPE_COMMENTS = 3;

  private int clickType = CLICK_TYPE_NONE;

  // Header

  public void updateDate () {
    setDate(genDate());
    if (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0) {
      layoutInfo();
    }
  }

  private void setDate (String date) {
    this.date = date;
    boolean needFakeBold = Text.needFakeBold(date);
    this.flags = BitwiseUtils.setFlag(flags, FLAG_DATE_FAKE_BOLD, needFakeBold);
    // TextPaint paint = useBubbles() ? Paints.getDatePaint() : Paints.getTitlePaint(false);
    TextPaint paint = useBubbles() ? Paints.getBoldPaint13(needFakeBold) : Paints.getTitlePaint(needFakeBold);
    this.pDateWidth = StringUtils.isEmpty(date) ? 0 : (int) U.measureText(date, paint);
  }

  protected final boolean alignReplyHorizontally () {
    return false;
  }

  private void buildHeader () {
    int currentWidth = this.width;

    if ((flags & FLAG_HEADER_ENABLED) != 0 || useBubbles()) {
      layoutAvatar();
      layoutInfo();
    }

    if ((flags & FLAG_SHOW_BADGE) != 0) {
      float center = (float) currentWidth / 2f;
      float badgeWidth = uBadge != null ? U.measureText(uBadge.text, getBadgePaint(uBadge.needFakeBold)) : 0;

      pBadgeX = (int) (center - (badgeWidth + Screen.dp(7f) + iBadge.getMinimumWidth()) / 2f);
      pBadgeIconX = pBadgeX + (int) badgeWidth + Screen.dp(2f);
    }

    pClockTop = getHeaderPadding() + Screen.dp(3.5f);
    pTicksTop = getHeaderPadding() + Screen.dp(3f);

    if ((flags & FLAG_HEADER_ENABLED) != 0) {
      pClockLeft = pTimeLeft - Screen.dp(6f);
      pTicksLeft = pTimeLeft - Screen.dp(3f);
    } else {
      pClockLeft = currentWidth - Screen.dp(17f);
      pTicksLeft = currentWidth - Screen.dp(15f);

      if (replyData != null && !alignReplyHorizontally()) {
        pClockTop += ReplyComponent.height();
        pTicksTop += ReplyComponent.height();
      }
    }
  }

  public TdApi.Message findReplyMarkupMessage () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        TdApi.Message markup = null;
        for (TdApi.Message msg : combinedMessages) {
          if (msg.replyMarkup != null) {
            if (markup != null) {
              return null;
            }
            markup = msg;
          }
        }
        return markup;
      }
    }
    return msg;
  }

  private void buildMarkup () {
    TdApi.Message replyMarkupMessage = findReplyMarkupMessage();
    if (replyMarkupMessage != null && replyMarkupMessage.replyMarkup != null && replyMarkupMessage.replyMarkup.getConstructor() == TdApi.ReplyMarkupInlineKeyboard.CONSTRUCTOR) {
      if (inlineKeyboard == null) {
        inlineKeyboard = new TGInlineKeyboard(this, true);
        inlineKeyboard.setViewProvider(currentViews);
      }
      if (useBubbles() && useBubble()) {
        int width = getActualRightContentEdge() - getActualLeftContentEdge();
        int maxWidth = pRealContentMaxWidth + getBubblePaddingLeft() + getBubblePaddingRight();
        inlineKeyboard.set(replyMarkupMessage.id, (TdApi.ReplyMarkupInlineKeyboard) replyMarkupMessage.replyMarkup, width, maxWidth);
      } else {
        inlineKeyboard.set(replyMarkupMessage.id, (TdApi.ReplyMarkupInlineKeyboard) replyMarkupMessage.replyMarkup, getContentWidth(), pRealContentMaxWidth);
      }
    } else if (inlineKeyboard != null) {
      inlineKeyboard.clear();
    }
  }

  public final boolean forceForwardedInfo () {
    return msg.forwardInfo != null && !isOutgoing() && (
      BitwiseUtils.getFlag(flags, FLAG_SELF_CHAT) || isChannelAutoForward() ||
      msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR ||
      (isPsa() && !sender.isUser() && useBubbles()) ||
      isRepliesChat());
  }

  public final boolean isChannelAutoForward () {
    return (msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginChannel.CONSTRUCTOR &&
      msg.forwardInfo.fromChatId == ((TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin).chatId &&
      msg.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR &&
      ((TdApi.MessageSenderChat) msg.senderId).chatId == msg.forwardInfo.fromChatId
    );
  }

  public final boolean isRepliesChat () {
    return tdlib.isRepliesChat(msg.chatId);
  }

  private void layoutAvatar () {
    if (useBubbles() && !needAvatar()) {
      hAvatar = null;
      return;
    }

    if (this.chat == null) {
      this.chat = tdlib.chat(msg.chatId);
    }

    final float avatarRadiusDp = useBubbles() ? BUBBLE_AVATAR_RADIUS : AVATAR_RADIUS;
    if (forceForwardedInfo()) {
      hAvatar = forwardInfo.getAvatar();
      hAvatarPlaceholder = new AvatarPlaceholder(avatarRadiusDp, forwardInfo.getAvatarPlaceholderMetadata(), null);
    } else {
      hAvatar = sender.getAvatar();
      hAvatarPlaceholder = new AvatarPlaceholder(avatarRadiusDp, sender.getPlaceholderMetadata(), null);
    }
  }

  protected static final float LETTERS_SIZE = 16f;
  protected static final float LETTERS_SIZE_SMALL = 15f;

  private boolean onNameClick (View view, Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (part.getEntity() != null && part.getEntity().getTag() instanceof Long) {
      manager.controller().setInputInlineBot(msg.viaBotUserId, viaBotUsername);
      return true;
    } else {
      return openProfile(view, text, part, openParameters, null);
    }
  }

  private boolean openProfile (View view, @Nullable Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters, @Nullable Receiver receiver) {
    if (forceForwardedInfo()) {
      forwardInfo.open(view, text, part,openParameters, receiver);
    } else if (sender.isUser()) {
      tdlib.ui().openPrivateProfile(controller(), sender.getUserId(), openParameters);
    } else if (sender.isChat()) {
      tdlib.ui().openChatProfile(controller(), sender.getChatId(), null, openParameters);
    } else {
      return false;
    }
    return true;
  }

  private boolean onForwardClick (View view, Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (part.getEntity() == null && text.getEntityCount() == 1)
      return false;
    if (part.getEntity() != null && part.getEntity().getTag() instanceof Long) {
      manager.controller().setInputInlineBot(msg.viaBotUserId, viaBotUsername);
    } else {
      forwardInfo.open(view, text, part, openParameters, null);
    }
    return true;
  }

  public TdlibUi.UrlOpenParameters openParameters () {
    return new TdlibUi.UrlOpenParameters().sourceMessage(this);
  }

  private Text makeName (String authorName, boolean available, boolean isPsa, boolean hideName, long viaBotUserId, int maxWidth, boolean isForward) {
    if (maxWidth <= 0)
      return null;
    boolean hasBot = viaBotUserId != 0;
    int textRes = isPsa ? (hasBot ? R.string.PsaFromXViaBot : R.string.PsaFromX) : (hasBot ? hideName ? R.string.message_viaBot : R.string.message_nameViaBot : 0);
    CharSequence text;
    boolean allActive = textRes == R.string.PsaFromXViaBot || textRes == R.string.PsaFromX, allBold = false;
    if (textRes == 0) {
      if (hideName) {
        return null;
      }
      text = authorName;
      allActive = true;
      allBold = available;
    } else if (textRes == R.string.PsaFromXViaBot || textRes == R.string.message_nameViaBot) { // author via bot
      text = Lang.getString(textRes, (target, argStart, argEnd, argIndex, needFakeBold) -> new TextEntityCustom(controller(), tdlib, target.toString(), argStart, argEnd, TextEntityCustom.FLAG_CLICKABLE | (argIndex == 1 || available ? TextEntityCustom.FLAG_BOLD : 0), openParameters()).setTag(argIndex == 1 ? viaBotUserId : null), authorName, "@" + tdlib.cache().userUsername(viaBotUserId));
    } else if (textRes == R.string.PsaFromX) { // author
      text = Lang.getString(textRes, (target, argStart, argEnd, argIndex, needFakeBold) -> Lang.newBoldSpan(needFakeBold), authorName);
      allActive = true;
    } else { // via bot
      text = Lang.getString(textRes, (target, argStart, argEnd, argIndex, needFakeBold) -> new TextEntityCustom(controller(), tdlib, target.toString(), argStart, argEnd, TextEntityCustom.FLAG_CLICKABLE | TextEntityCustom.FLAG_BOLD, openParameters()).setTag(viaBotUserId), "@" + tdlib.cache().userUsername(viaBotUserId));
    }
    TextColorSet colorTheme;
    if (isPsa) {
      colorTheme = getChatAuthorPsaColorSet();
    } else if (!isForward && needColoredNames() && hAvatarPlaceholder != null) {
      int colorId = TD.getNameColorId(hAvatarPlaceholder.metadata.colorId);
      colorTheme = new TextColorSetOverride(getChatAuthorColorSet()) {
        @Override
        public int clickableTextColor (boolean isPressed) {
          return Theme.getColor(colorId);
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          return isPressed ? ColorUtils.alphaColor(.2f, Theme.getColor(colorId)) : 0;
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          return isPressed ? colorId : 0;
        }
      };
    } else {
      colorTheme = getChatAuthorColorSet();
    }
    return new Text.Builder(tdlib, text, openParameters(), maxWidth, getNameStyleProvider(), colorTheme)
      .singleLine()
      .clipTextArea()
      .allBold(allBold)
      .allClickable(allActive)
      .onClick(isForward ? this::onForwardClick : this::onNameClick)
      .build();
  }

  private void layoutInfo () {
    boolean isPsa = isPsa() && forceForwardedInfo();

    if (useBubbles()) {
      // time part
      pTimeWidth = (int) U.measureText(time, mTimeBubble());

      // header part
      int maxWidth = (allowMessageHorizontalExtend() ? pRealContentMaxWidth : getContentWidth()) + getBubblePaddingRight() + getBubblePaddingRight() - (xBubblePadding + xBubblePaddingSmall) * 2;

      if (needAdminSign()) {
        hAdminNameT = new Text.Builder(getAdministratorSign(), maxWidth, getTimeTextStyleProvider(), getDecentColorSet()).singleLine().build();
        maxWidth -= hAdminNameT.getWidth();
      } else {
        hAdminNameT = null;
      }

      final String authorName;
      if (forceForwardedInfo()) {
        authorName = forwardInfo.getAuthorName();
      } else {
        authorName = sender.getName();
      }
      if (needName(true) && maxWidth > 0) {
        hAuthorNameT = makeName(authorName, !(forceForwardedInfo() && forwardInfo instanceof TGSourceHidden), isPsa, !needName(false), msg.forwardInfo == null || forceForwardedInfo() ? msg.viaBotUserId : 0, maxWidth, false);
      } else {
        hAuthorNameT = null;
      }
      if (isPsa) {
        hPsaTextT = new Text.Builder(tdlib, Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType), openParameters(), maxWidth, getNameStyleProvider(), getChatAuthorPsaColorSet()).allClickable().singleLine().onClick(this::onNameClick).build();
      } else {
        hPsaTextT = null;
      }

      if ((flags & FLAG_LAYOUT_BUILT) != 0) {
        buildBubble(false);
      }

      return;
    }

    int currentWidth = this.width;

    pTimeWidth = (int) U.measureText(time, mTime(false));
    int timePaddingRight = Screen.dp(16f);
    pTimeLeft = currentWidth - pTimeWidth - timePaddingRight;

    int totalMaxWidth = currentWidth - xContentLeft - Screen.dp(4f);
    int max = totalMaxWidth - timePaddingRight - pTimeWidth;

    if (shouldShowTicks()) {
      max -= Screen.dp(3f) + Icons.getSingleTickWidth() + Screen.dp(3f);
    }

    if (shouldShowEdited()) {
      max -= Screen.dp(5f) + Icons.getEditedIconWidth();
    }

    String authorName;
    if (forceForwardedInfo()) {
      authorName = forwardInfo.getAuthorName();
    } else {
      authorName = sender.getName();
    }

    max -= isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN)) + Screen.dp(COUNTER_ADD_MARGIN);
    if (getCommentMode() == COMMENT_MODE_NONE) {
      max -= replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (getViewCountMode() == VIEW_COUNT_MAIN) {
      max -= shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      max -= viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }

    int nameMaxWidth;
    if (isPsa) {
      nameMaxWidth = totalMaxWidth;
      hPsaTextT = max > 0 ? new Text.Builder(tdlib, Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType), openParameters(), max, getNameStyleProvider(), getChatAuthorPsaColorSet()).allClickable().singleLine().onClick(this::onNameClick).build() : null;
    } else {
      hPsaTextT = null;
      nameMaxWidth = max;
    }
    if (nameMaxWidth > 0) {
      hAuthorNameT = makeName(authorName, !(forceForwardedInfo() && forwardInfo instanceof TGSourceHidden), isPsa, !needName(false), msg.forwardInfo == null || forceForwardedInfo() ? msg.viaBotUserId : 0, nameMaxWidth, false);
    } else {
      hAuthorNameT = null;
    }
  }

  private void loadForward () {
    if (msg.forwardInfo == null) {
      return;
    }
    switch (msg.forwardInfo.origin.getConstructor()) {
      case TdApi.MessageForwardOriginUser.CONSTRUCTOR: {
        forwardInfo = new TGSourceUser(this, (TdApi.MessageForwardOriginUser) msg.forwardInfo.origin);
        break;
      }
      case TdApi.MessageForwardOriginChat.CONSTRUCTOR: {
        forwardInfo = new TGSourceChat(this, (TdApi.MessageForwardOriginChat) msg.forwardInfo.origin);
        break;
      }
      case TdApi.MessageForwardOriginChannel.CONSTRUCTOR: {
        forwardInfo = new TGSourceChat(this, (TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin);
        break;
      }
      case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR: {
        forwardInfo = new TGSourceHidden(this, (TdApi.MessageForwardOriginHiddenUser) msg.forwardInfo.origin);
        break;
      }
      case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR: {
        forwardInfo = new TGSourceHidden(this, (TdApi.MessageForwardOriginMessageImport) msg.forwardInfo.origin);
        break;
      }
    }
    buildForwardTime();
    forwardInfo.load();
  }

  void rebuildForward () {
    if ((flags & FLAG_LAYOUT_BUILT) != 0) {
      if (useBubbles() && allowMessageHorizontalExtend()) {
        rebuildLayout();
      } else {
        buildForward();
      }
    }
  }

  private void buildForward () {
    if (!useForward() || forwardInfo == null) {
      return;
    }

    fTimeWidth = U.measureText(fTime, useBubbles() ? mTimeBubble() : mTime(false));

    float totalMax;
    if (useBubbles()) {
      totalMax = allowMessageHorizontalExtend() ? pRealContentMaxWidth : getContentWidth() + (getBubblePaddingRight() + getBubblePaddingLeft() - (xBubblePaddingSmall + xBubblePadding) * 2);
    } else {
      totalMax = this.width - xfContentLeft - xPaddingRight;
    }

    float max = totalMax - fTimeWidth - xTimePadding;
    if (getViewCountMode() == VIEW_COUNT_FORWARD) {
      max -= viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN)) +
        shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }

    boolean isPsa = isPsa() && !forceForwardedInfo();
    fAuthorNameT = makeName(forwardInfo.getAuthorName(), !(forwardInfo instanceof TGSourceHidden), isPsa, false, msg.viaBotUserId, (int) (isPsa ? totalMax : max), true);
    if (isPsa) {
      fPsaTextT = new Text.Builder(tdlib, Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType), openParameters(), (int) max, getNameStyleProvider(), getChatAuthorPsaColorSet()).allClickable().singleLine().onClick(this::onForwardClick).build();
    } else {
      fPsaTextT = null;
    }
  }

  private int getForwardAuthorNameLeft () {
    return useBubbles() ? getInternalBubbleStartX() + Screen.dp(11f) : xfContentLeft;
  }

  private void loadReply () {
    replyData = new ReplyComponent(this);
    replyData.setViewProvider(currentViews);
    replyData.load();
  }

  public final void replaceReplyContent (long messageId, TdApi.MessageContent newContent) {
    if (msg.replyToMessageId == messageId && replyData != null) {
      replyData.replaceMessageContent(messageId, newContent);
    }
  }

  public final void removeReply (long messageId) {
    if (msg.replyToMessageId == messageId && replyData != null) {
      replyData.deleteMessageContent(messageId);
    }
  }

  private void buildReply () {
    if (replyData != null) {
      int maxWidth = allowMessageHorizontalExtend() ? pRealContentMaxWidth : getContentWidth();
      if (alignReplyHorizontally()) {
        maxWidth = this.width - getContentWidth() - Screen.dp(18f) - (Device.NEED_BIGGER_BUBBLE_OFFSETS ? xBubbleLeft2 : xBubbleLeft1) * 2;
      }
      replyData.layout(maxWidth);
    }
  }

  // Bubble stuff

  private void checkEdges () {
    if (useBubbles()) {
      buildBubble(false);
    }
  }

  protected boolean useCircleBubble () {
    return false;
  }

  private boolean allowMessageHorizontalExtend () {
    return /*msg.forwardInfo != null ||*/ !useBubbles() || allowBubbleHorizontalExtend();
  }

  protected boolean allowBubbleHorizontalExtend () {
    return true;
  }

  private int bubbleTimePartWidth, bubbleInnerWidth;

  protected final int getBubbleTimePartWidth () {
    return useBubbles() && useBubbleTime() ? bubbleTimePartWidth : 0;
  }

  protected final boolean moveBubbleTimePartToLeft () {
    return Lang.rtl();
  }

  protected final boolean needExpandBubble (int bottomLineContentWidth) {
    return bottomLineContentWidth + bubbleTimePartWidth > (allowBubbleHorizontalExtend() ? pRealContentMaxWidth : Math.min(pRealContentMaxWidth, bubbleInnerWidth));
  }

  protected float getBubbleExpandFactor () {
    throw new RuntimeException();
  }

  protected int getAnimatedBottomLineWidth () {
    throw new RuntimeException();
  }

  protected final void buildBubble (boolean force) {
    if (useBubbles()) {
      final boolean needBubble = useBubble();

      int bubbleWidth = computeBubbleWidth();
      int bubbleHeight = computeBubbleHeight();

      if (!headerDisabled() && !(drawBubbleTimeOverContent() && !useForward()) && useBubbleTime()) {
        final int bubbleTimePartWidth = computeBubbleTimePartWidth(true);
        final int bottomLineContentWidth = useForward() ? BOTTOM_LINE_EXPAND_HEIGHT : hasFooter() ? footerText.getLastLineWidth() + Screen.dp(10f) : getBottomLineContentWidth();
        final int extendedWidth = bottomLineContentWidth + bubbleTimePartWidth;
        final boolean allowHorizontalExtend = allowBubbleHorizontalExtend();

        final int expandedBubbleWidth = allowHorizontalExtend ? Math.max(bubbleWidth, bubbleTimePartWidth) : bubbleWidth;
        final int expandedBubbleHeight = bubbleHeight + getBubbleTimePartHeight();
        final int maxLineWidth = allowHorizontalExtend ? pRealContentMaxWidth : Math.min(pRealContentMaxWidth, bubbleWidth);
        final int fitBubbleWidth = Math.max(bubbleWidth, extendedWidth);

        switch (bottomLineContentWidth) {
          case BOTTOM_LINE_KEEP_WIDTH:
            break;
          case BOTTOM_LINE_DEFINE_BY_FACTOR: {
            float factor = getBubbleExpandFactor();
            bubbleWidth = MathUtils.fromTo(Math.max(bubbleWidth, getAnimatedBottomLineWidth() + bubbleTimePartWidth), expandedBubbleWidth, factor);
            bubbleHeight = MathUtils.fromTo(bubbleHeight, expandedBubbleHeight, factor);
            break;
          }
          default: {
            if (bottomLineContentWidth == BOTTOM_LINE_EXPAND_HEIGHT || extendedWidth > maxLineWidth) {
              bubbleWidth = expandedBubbleWidth;
              bubbleHeight = expandedBubbleHeight;
            } else {
              bubbleWidth = fitBubbleWidth;
            }
            break;
          }
        }
        this.bubbleInnerWidth = bubbleWidth;
        this.bubbleTimePartWidth = bubbleTimePartWidth;
      } else {
        this.bubbleTimePartWidth = 0;
      }

      final int bubblePaddingLeft = getBubblePaddingLeft();
      final int bubblePaddingTop = getBubblePaddingTop();
      final int bubblePaddingRight = getBubblePaddingRight();
      final int bubblePaddingBottom = getBubblePaddingBottom();

      bubbleWidth += bubblePaddingLeft + bubblePaddingRight;
      bubbleHeight += bubblePaddingTop + bubblePaddingBottom;

      int leftContentEdge = centerBubble() ? width / 2 - bubbleWidth / 2 : computeBubbleLeft();
      int rightContentEdge = leftContentEdge + bubbleWidth;

      int topContentEdge = computeBubbleTop();
      int bottomContentEdge = topContentEdge + bubbleHeight;

      int bubbleDefaultRadius = Screen.dp(Theme.getBubbleDefaultRadius());
      int bubbleMergeRadius = Screen.dp(Theme.getBubbleMergeRadius());

      if (this.leftContentEdge == leftContentEdge && this.topContentEdge == topContentEdge && this.rightContentEdge == rightContentEdge && this.bottomContentEdge == bottomContentEdge && this.lastMergeRadius == bubbleMergeRadius && this.lastDefaultRadius == bubbleDefaultRadius && !force) {
        return;
      }

      this.leftContentEdge = leftContentEdge;
      this.topContentEdge = topContentEdge;
      this.rightContentEdge = rightContentEdge;
      this.bottomContentEdge = bottomContentEdge;

      this.lastMergeRadius = bubbleMergeRadius;
      this.lastDefaultRadius = bubbleDefaultRadius;

      if (needBubble) {
        bubblePath.reset();
        bubbleClipPath.reset();
      }

      if (alignBubbleRight()) {
        int translateBy = width - rightContentEdge - leftContentEdge;
        leftContentEdge += translateBy;
        rightContentEdge += translateBy;
      }

      if (needBubble) {
        final boolean circleBubble = useCircleBubble();
        int dr, mr;
        if (circleBubble) {
          dr = mr = bubbleWidth / 2;
          topContentEdge = bottomContentEdge - bubbleWidth;
        } else {
          dr = bubbleDefaultRadius;
          mr = bubbleMergeRadius;
        }
        final boolean mergeTop = !headerDisabled(); /* && !drawBubbleTimeOverContent()*/ // !hasHeader() && !headerDisabled();
        final boolean mergeBottom = (flags & MESSAGE_FLAG_IS_BOTTOM) == 0 || (msg.content.getConstructor() == TdApi.MessageGame.CONSTRUCTOR);

        synchronized (bubblePath) {
          final boolean alignContentRight = alignBubbleRight();

          if (circleBubble) {
            bottomRightRadius = bottomLeftRadius = topLeftRadius = topRightRadius = dr;
            float centerX = (leftContentEdge + rightContentEdge) * .5f;
            float centerY = bottomContentEdge - dr;
            bubblePath.addCircle(centerX, centerY, dr, Path.Direction.CW);
            bubbleClipPath.addCircle(centerX, centerY, dr, Path.Direction.CW);
            bubblePathRect.set(leftContentEdge, bottomContentEdge - bubbleWidth, rightContentEdge, bottomContentEdge);
            bubbleClipPathRect.set(bubblePathRect);
          } else {
            bubblePathRect.set(leftContentEdge, topContentEdge, rightContentEdge, bottomContentEdge);
            DrawAlgorithms.buildPath(bubblePath, bubblePathRect, topLeftRadius = (mergeTop && !alignContentRight ? mr : dr), topRightRadius = (mergeTop && alignContentRight ? mr : dr), bottomRightRadius = (mergeBottom && alignContentRight ? mr : dr), bottomLeftRadius = (mergeBottom && !alignContentRight ? mr : dr));
            bubbleClipPathRect.set(leftContentEdge + bubblePaddingLeft, topContentEdge + bubblePaddingTop - getBubbleSpecialPaddingTop(), rightContentEdge - bubblePaddingRight, bottomContentEdge - bubblePaddingBottom);
            dr /= 1.5;
            mr /= 1.5;
            DrawAlgorithms.buildPath(bubbleClipPath, bubbleClipPathRect, mergeTop && !alignContentRight ? mr : dr, mergeTop && alignContentRight ? mr : dr, mergeBottom && alignContentRight ? mr : dr, mergeBottom && !alignContentRight ? mr : dr);
          }
        }
      }

      updateContentPositions(false);
      notifyBubbleChanged();
      // invalidateOutline(true);
    }
  }

  private void notifyBubbleChanged () {
    int oldHeight = height;
    height = computeHeight();
    onBubbleHasChanged();
    if (height != oldHeight) {
      // FIXME?
      requestLayout();
    }
  }

  protected void onBubbleHasChanged () {
    // override
  }

  public @Nullable Path getBubblePath () {
    return disableBubble() ? null : bubblePath;
  }

  public Path getBubbleClipPath () {
    return bubbleClipPath;
  }

  protected static int getBubbleTimePartHeight () {
    return Screen.dp(16f);
  }

  protected int getAbsolutelyRealRightContentEdge (View view, int timePartWidth) {
    return getActualRightContentEdge() - timePartWidth;
  }

  protected int getBubbleTimePartOffsetY () {
    return Screen.dp(8f);
  }

  protected int getTimePartTextColor () {
    if (!useBubbles()) {
      return getDecentColor();
    }
    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());
    if (!isWhite) { // Inside bubble
      return getDecentColor();
    } else if (isTransparent) {
      return getBubbleTimeTextColor();
    } else {
      return Theme.getColor(R.id.theme_color_bubble_mediaOverlayText);
    }
  }

  protected int getTimePartIconColorId () {
    if (!useBubbles()) {
      return getDecentIconColorId();
    }

    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());

    if (!isWhite) { // Inside bubble
      return getDecentIconColorId();
    } else if (isTransparent) { // Partially on the content
      return R.id.theme_color_bubble_mediaTime;
    } else {
      return R.id.theme_color_bubble_mediaOverlayText;
    }
  }

  private static final float COUNTER_ICON_MARGIN = 3f;
  private static final float COUNTER_ADD_MARGIN = 3f;

  protected void drawBubbleTimePart (Canvas c, MessageView view) {
    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());

    final int iconColorId, backgroundColor, textColor;
    final Paint iconPaint, ticksPaint, ticksReadPaint;

    if (!isWhite) { // Inside bubble
      textColor = getDecentColor();
      iconColorId = getDecentIconColorId();
      backgroundColor = 0;
      iconPaint = getDecentIconPaint();
      ticksPaint = Paints.getBubbleTicksPaint();
      ticksReadPaint = Paints.getBubbleTicksReadPaint();
    } else if (isTransparent) { // Partially on the content
      textColor = getBubbleTimeTextColor();
      iconColorId = R.id.theme_color_bubble_mediaTimeText;
      backgroundColor = getBubbleTimeColor();
      iconPaint = ticksPaint = ticksReadPaint = Paints.getBubbleTimePaint(textColor);
    } else { // Media
      iconColorId = R.id.theme_color_bubble_mediaOverlayText;
      textColor = Theme.getColor(R.id.theme_color_bubble_mediaOverlayText);
      backgroundColor = Theme.getColor(R.id.theme_color_bubble_mediaOverlay);
      iconPaint = ticksPaint = ticksReadPaint = Paints.getBubbleOverlayTimePaint(textColor);
    }

    int innerWidth = computeBubbleTimePartWidth(false);
    int startX;

    final boolean isSending = isSending();
    final boolean isFailed = isFailed();

    boolean reverseOrder;

    if ((reverseOrder = Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT && moveBubbleTimePartToLeft())) {
      startX = getActualLeftContentEdge() + Screen.dp(10f);
    } else {
      startX = getAbsolutelyRealRightContentEdge(view, innerWidth + Screen.dp(11f));
    }
    int startY = bottomContentEdge - getBubbleTimePartHeight() - getBubbleTimePartOffsetY() - getBubbleReduceHeight();

    if (backgroundColor != 0) {
      startY -= Screen.dp(4f);
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(6f);
      rectF.set(startX - padding, startY, startX + innerWidth + padding, startY + Screen.dp(21f));
      c.drawRoundRect(rectF, Screen.dp(12f), Screen.dp(12f), Paints.fillingPaint(backgroundColor));
      startY -= Screen.dp(1f);
    }

    int counterY = startY + Screen.dp(11.5f);
    if (getViewCountMode() == VIEW_COUNT_MAIN) {
      if (isSending) {
        final float viewsWidth = viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
        final int clockWidth = Icons.getClockIconWidth() + Screen.dp(3f);
        if ((useBubble() && !drawBubbleTimeOverContent()) && viewsWidth > clockWidth) {
          startX += viewsWidth - clockWidth;
        }
        Drawables.draw(c, Icons.getClockIcon(iconColorId), startX - Screen.dp(Icons.CLOCK_SHIFT_X), startY + Screen.dp(5f) - Screen.dp(Icons.CLOCK_SHIFT_Y), iconPaint);
        startX += clockWidth;
      } else {
        viewCounter.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
        startX += viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      }
      shareCounter.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
      startX += shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (getCommentMode() == COMMENT_MODE_NONE) {
      replyCounter.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
      startX += replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    isPinned.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
    startX += isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN));

    if (shouldShowEdited()) {
      if (isBeingEdited()) {
        Drawables.draw(c, Icons.getClockIcon(iconColorId), startX - Screen.dp(6f), startY + Screen.dp(4.5f) - Screen.dp(5f), iconPaint);
      } else {
        Drawables.draw(c, view.getSparseDrawable(R.drawable.baseline_edit_12, 0), startX, startY + Screen.dp(4.5f), iconPaint);
      }
      startX += Icons.getEditedIconWidth() + Screen.dp(2f);
    }

    if (time != null) {
      c.drawText(time, startX, startY + Screen.dp(15.5f), Paints.colorPaint(mTimeBubble(), textColor));
      startX += pTimeWidth;
    }

    if (isOutgoingBubble() || (isSending && getViewCountMode() != VIEW_COUNT_MAIN)) {
      int top;

      startX += Screen.dp(3.5f);

      if (isSending) {
        top = startY + Screen.dp(5f);
        startX += Screen.dp(1f);
      } else {
        top = startY + Screen.dp(4.5f);
      }

      if (isFailed) {
        // TODO failure icon
      } else if (isSending) {
        Drawables.draw(c, Icons.getClockIcon(iconColorId), startX - Screen.dp(Icons.CLOCK_SHIFT_X), top - Screen.dp(Icons.CLOCK_SHIFT_Y), iconPaint);
      } else {
        boolean unread = isUnread() && !noUnread();
        Drawables.draw(c, unread ? Icons.getSingleTick(iconColorId) : Icons.getDoubleTick(iconColorId), startX - Screen.dp(Icons.TICKS_SHIFT_X), top - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? ticksPaint : ticksReadPaint);
      }
      startX += Icons.getSingleTickWidth();
    }
  }

  protected final int computeBubbleTimePartWidth (boolean includePadding) {
    int width = 0;
    /*if (shouldShowTicks()) {
      width += Screen.dp(3f) + Icons.getSingleTick().getWidth() + Screen.dp(3f);
    }
    if (shouldShowEdited()) {
      width += Screen.dp(5f) + Icons.getEditedIcon().getWidth();
    }*/
    width += pTimeWidth;
    if (width == 0 && !StringUtils.isEmpty(time)) { // TODO do it in a proper place
      width = (int) U.measureText(time, mTimeBubble());
    }
    if (shouldShowEdited()) {
      width += Icons.getEditedIconWidth() + Screen.dp(2f);
    }
    boolean isSending = isSending();
    if (getViewCountMode() == VIEW_COUNT_MAIN) {
      final float viewsWidth = viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      final int clockWidth = Icons.getClockIconWidth() + Screen.dp(3f);
      if (isSending && (drawBubbleTimeOverContent() || !useBubble())) {
        width += clockWidth;
      } else {
        width += viewsWidth;
      }
      width += shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (getCommentMode() == COMMENT_MODE_NONE) {
      width += replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    width += isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN));
    if (!isFailed() && (isOutgoingBubble() || (isSending && getViewCountMode() != VIEW_COUNT_MAIN))) {
      width += /*Screen.dp(3.5f) +*/ Icons.getSingleTickWidth() /*- Screen.dp(3.5f)*/; // singleTick bitmap contains padding
    }
    if (includePadding) {
      width += Screen.dp(8f);
    }
    return width;
  }

  protected boolean drawBubbleTimeOverContent () {
    return false;
  }
  protected final boolean needTopContentRounding () {
    return useBubbleName() || useForward() || replyData != null;
  }
  protected final boolean needBottomContentRounding () {
    return useForward() || drawBubbleTimeOverContent() || hasFooter();
  }

  protected static final int BOTTOM_LINE_EXPAND_HEIGHT = -1;
  protected static final int BOTTOM_LINE_KEEP_WIDTH = -2;
  protected static final int BOTTOM_LINE_DEFINE_BY_FACTOR = -3;

  protected int getBottomLineContentWidth () {
    return BOTTOM_LINE_EXPAND_HEIGHT;
  }

  public float getBubbleTopRightRadius () {
    return topRightRadius;
  }

  public float getBubbleTopLeftRadius () {
    return topLeftRadius;
  }

  public float getBubbleBottomLeftRadius () {
    return bottomLeftRadius;
  }

  public float getBubbleBottomRightRadius () {
    return bottomRightRadius;
  }

  // Image receivers

  public final void layoutAvatar (MessageView view, ImageReceiver receiver) {
    int left, top, size;
    if (useBubbles()) {
      left = xBubbleAvatarLeft;
      top = computeBubbleTop();
      size = xBubbleAvatarRadius * 2;
    } else {
      top = getHeaderPadding();
      left = xAvatarLeft;
      size = xAvatarRadius * 2;
    }
    if (Lang.rtl() && useBubbles()) {
      left = view.getMeasuredWidth() - left - size;
    }
    receiver.setBounds(left, top, left + size, top + size);
  }

  public final void requestReply (DoubleImageReceiver receiver) {
    if (replyData != null) {
      replyData.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  public final void requestAvatar (ImageReceiver receiver) {
    if ((flags & FLAG_HEADER_ENABLED) != 0 && hAvatar != null && needAvatar()) {
      receiver.requestFile(hAvatar);
    } else {
      receiver.requestFile(null);
    }
  }



  // public static final float IMAGE_CONTENT_DEFAULT_RADIUS = 3f;
  // public static final float BUBBLE_MERGE_RADIUS = 6f;
  // public static final float BUBBLE_DEFAULT_RADIUS = BUBBLE_BIG_RADIUS_AVAILABLE ? 18f : BUBBLE_MERGE_RADIUS;

  public int getImageContentRadius (boolean isPreview) {
    return 0;
  }

  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(null);
  }

  public void requestGif (GifReceiver receiver) {
    receiver.requestFile(null);
  }

  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.clear();
  }

  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    receiver.clear();
  }

  public void invalidateMediaContent (ComplexReceiver receiver, long messageId) {
    receiver.clear();
  }

  // Getters

  public boolean onMessageClick (MessageView v, MessagesController c) {
    // TODO
    return /* isEventLog() */ false;
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public int getContentX () {
    return pContentX;
  }

  public int getActualRightContentEdge () {
    return alignBubbleRight() ? width - leftContentEdge : rightContentEdge;
  }

  public int getActualLeftContentEdge () {
    return alignBubbleRight() ? width - rightContentEdge : leftContentEdge;
  }

  public int getBottomContentEdge () {
    return bottomContentEdge;
  }

  public int getTopContentEdge () {
    return topContentEdge;
  }

  public final int centerX () {
    return useBubbles() ? getActualLeftContentEdge() + (getActualRightContentEdge() - getActualLeftContentEdge()) / 2 : getContentX() + getContentWidth() / 2;
  }

  public final int centerY () {
    return useBubbles() ? (int) bubblePathRect.centerY() : getContentY() + getContentHeight() / 2;
  }

  public boolean isInsideBubble (float x, float y) {
    return x >= getActualLeftContentEdge() && x < getActualRightContentEdge() && y >= topContentEdge && y < bottomContentEdge;
  }

  public int getRealContentX () {
    return pRealContentX;
  }

  public int getRealContentMaxWidth () {
    return pRealContentMaxWidth;
  }

  public final int getContentY () {
    return pContentY;
  }

  private int getForwardLeft () {
    return useBubbles() ? getInternalBubbleStartX() : xContentLeft;
  }

  private int getForwardTop () {
    return useBubbles() ?
      pContentY - getBubbleForwardOffset() :
      ((flags & FLAG_HEADER_ENABLED) != 0 ? xContentTop - Screen.dp(3f) : xPaddingTop - Screen.dp(3f)) + getHeaderPadding();
  }

  private static int getForwardHeaderHeight () {
    return Screen.dp(26f);
  }

  private int getForwardHeight () {
    return getContentHeight() + getForwardHeaderHeight() + (isPsa() && !forceForwardedInfo() ? getPsaTitleHeight() : 0);
  }

  public int getHeaderPadding () {
    int result;
    if ((flags & FLAG_SHOW_BADGE) != 0) {
      if ((flags & FLAG_SHOW_DATE) != 0) {
        result = xBadgeHeight + xBadgePadding + (useBubbles() ? xDatePadding - Screen.dp(3f) * 2 : xDatePadding);
      } else {
        result = xBadgeHeight + xBadgePadding;
      }
    } else {
      if ((flags & FLAG_SHOW_DATE) != 0) {
        result = (useBubbles() ? xDatePadding - Screen.dp(3f) * 2 : xDatePadding);
      } else {
        result = 0;
      }
    }
    return (flags & FLAG_HEADER_ENABLED) != 0 && !useBubbles() ? xHeaderPadding + result : result;
  }

  // Data getters

  public TdlibSender getSender () {
    return sender;
  }

  public TdApi.Message getMessage () {
    return msg;
  }

  public TdApi.Message getMessage (long messageId) {
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message message : combinedMessages) {
          if (message.id == messageId) {
            return message;
          }
        }
      }
    }
    return msg.id == messageId ? msg : null;
  }

  public int getMessageCountBetween (long afterMessageId, long beforeMessageId) {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        int count = 0;
        for (TdApi.Message msg : combinedMessages) {
          if (msg.id <= afterMessageId) {
            continue;
          }
          if (msg.id >= beforeMessageId) {
            break;
          }
          count++;
        }
        return count;
      }
    }
    return 0;
  }

  public int getMessageCount () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        return combinedMessages.size();
      }
    }
    return 1;
  }

  public static final int MESSAGE_ALBUM_TYPE_NONE = 0;
  public static final int MESSAGE_ALBUM_TYPE_MIXED = 1;
  public static final int MESSAGE_ALBUM_TYPE_MEDIA = 2;
  public static final int MESSAGE_ALBUM_TYPE_PLAYLIST = 3;
  public static final int MESSAGE_ALBUM_TYPE_FILES = 4;

  public int getMessageAlbumType () {
    SparseIntArray counters = new SparseIntArray();
    iterate(message -> {
      int key = MESSAGE_ALBUM_TYPE_MIXED;
      switch (message.content.getConstructor()) {
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageVideo.CONSTRUCTOR:
        case TdApi.MessageAnimation.CONSTRUCTOR:
          key = MESSAGE_ALBUM_TYPE_MEDIA;
          break;
        case TdApi.MessageAudio.CONSTRUCTOR:
          key = MESSAGE_ALBUM_TYPE_PLAYLIST;
          break;
        case TdApi.MessageDocument.CONSTRUCTOR:
          key = MESSAGE_ALBUM_TYPE_FILES;
          break;
      }
      ArrayUtils.increment(counters, key);
    }, false);
    if (counters.size() > 1) {
      return MESSAGE_ALBUM_TYPE_MIXED;
    }
    return counters.valueAt(0) > 1 ? counters.keyAt(0) : MESSAGE_ALBUM_TYPE_NONE;
  }

  public TdApi.Message[] getAllMessages () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        TdApi.Message[] result = new TdApi.Message[combinedMessages.size()];
        combinedMessages.toArray(result);
        return result;
      }
    }
    return new TdApi.Message[] {msg};
  }

  protected final ArrayList<TdApi.Message> getCombinedMessagesUnsafely () {
    return combinedMessages;
  }

  public void iterate (RunnableData<TdApi.Message> callback, boolean reverse) {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        if (reverse) {
          final int size = combinedMessages.size();
          for (int i = size - 1; i >= 0; i--) {
            callback.runWithData(combinedMessages.get(i));
          }
        } else {
          for (TdApi.Message message : combinedMessages) {
            callback.runWithData(message);
          }
        }
      } else {
        callback.runWithData(msg);
      }
    }
  }

  public TGSource getForwardInfo () {
    return forwardInfo;
  }

  public final String getSourceName () {
    if (forwardInfo != null) {
      if (forwardInfo.isReady()) {
        return forwardInfo.getAuthorName();
      }
      if (forwardInfo instanceof TGSourceChat) {
        return null;
      }
      if (forwardInfo instanceof TGSourceUser) {
        final TdApi.User user = tdlib.cache().user(((TGSourceUser) forwardInfo).getSenderUserId());
        return user != null ? user.firstName : null;
      }
      return null;
    }
    return sender.getNameShort();
  }

  public final String getInReplyTo () {
    return replyData != null ? replyData.getAuthor() : null;
  }

  public final int getViewCount () {
    if (isSending() || isFailed()) {
      return 0;
    }
    TdApi.MessageInteractionInfo info = msg.interactionInfo;
    return info != null ? info.viewCount : 0;
  }

  public final int getReplyCount () {
    if (!Config.COMMENTS_SUPPORTED || isThreadHeader()) {
      return 0;
    }
    TdApi.MessageInteractionInfo info = msg.interactionInfo;
    TdApi.MessageReplyInfo replyInfo = info != null ? info.replyInfo : null;
    return replyInfo != null ? replyInfo.replyCount : 0;
  }

  public final int getForwardCount () {
    TdApi.MessageInteractionInfo info = msg.interactionInfo;
    return info != null ? info.forwardCount : 0;
  }

  public final long getId () {
    return msg.id;
  }

  public final MessageId toMessageId () {
    return new MessageId(msg.chatId, msg.id, getOtherMessageIds(msg.id));
  }

  public final void getIds (@NonNull LongList ids, long afterMessageId, long beforeMessageId) {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message msg : combinedMessages) {
          if (msg.id > afterMessageId && msg.id < beforeMessageId) {
            ids.append(msg.id);
          }
        }
        return;
      }
    }
    if (msg.id > afterMessageId && msg.id < beforeMessageId) {
      ids.append(msg.id);
    }
  }

  public final void getIds (@NonNull LongSet ids) {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        ids.ensureCapacity(ids.size() + combinedMessages.size());
        for (TdApi.Message msg : combinedMessages) {
          ids.add(msg.id);
        }
        return;
      }
    }
    ids.add(msg.id);
  }

  public final long[] getIds () {
    synchronized (this) {
      if (combinedMessages != null) {
        long[] ids = new long[combinedMessages.size()];
        int i = 0;
        for (TdApi.Message msg : combinedMessages) {
          ids[i] = msg.id;
          i++;
        }
        Arrays.sort(ids);
        return ids;
      } else {
        return new long[] {msg.id};
      }
    }
  }

  public final long getMessageThreadId () {
    return msg.messageThreadId;
  }

  public final long[] getOtherMessageIds (long exceptMessageId) {
    synchronized (this) {
      if (combinedMessages != null && combinedMessages.size() > 1) {
        long[] result = new long[combinedMessages.size() - 1];
        int i = 0;
        for (TdApi.Message msg : combinedMessages) {
          if (msg.id != exceptMessageId) {
            result[i] = msg.id;
            i++;
          }
        }
        Arrays.sort(result);
        return result;
      }
    }
    return null;
  }

  public final long getSmallestId () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        return combinedMessages.get(0).id;
      }
    }
    return msg.id;
  }

  public final long getBiggestId () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        return combinedMessages.get(combinedMessages.size() - 1).id;
      }
    }
    return msg.id;
  }

  public final TdApi.Message getOldestMessage () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        return combinedMessages.get(0);
      }
    }
    return msg;
  }

  public final TdApi.Message getNewestMessage () {
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        return combinedMessages.get(combinedMessages.size() - 1);
      }
    }
    return msg;
  }

  public final boolean containsUnreadMention () {
    synchronized (this) {
      if (combinedMessages != null) {
        for (int i = combinedMessages.size() - 1; i >= 0; i--) {
          if (combinedMessages.get(i).containsUnreadMention) {
            return true;
          }
        }
      }
    }
    return msg.containsUnreadMention;
  }

  public final void readMention (long messageId) {
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message message : combinedMessages) {
          if (message.id == messageId) {
            message.containsUnreadMention = false;
            return;
          }
        }
      }
    }
    if (msg.id == messageId) {
      msg.containsUnreadMention = false;
    }
  }

  public final long getMediaGroupId () {
    return msg.mediaAlbumId;
  }

  private ArrayList<TdApi.Message> combinedMessages;

  @AnyThread
  protected void onMessageCombinedWithOtherMessage (TdApi.Message otherMessage, boolean atBottom, boolean local) {
    // override
  }

  @UiThread
  protected void onMessageCombinationRemoved (TdApi.Message message, int index) {
    // override
  }

  @AnyThread
  public final boolean wouldCombineWith (TdApi.Message message) {
    if (msg.mediaAlbumId == 0 || msg.mediaAlbumId != message.mediaAlbumId || msg.ttl != message.ttl || isHot() || isEventLog() || isSponsored()) {
      return false;
    }
    int combineMode = TD.getCombineMode(msg);
    return combineMode != TD.COMBINE_MODE_NONE && TD.getCombineMode(message) == combineMode;
  }

  @AnyThread
  public final boolean combineWith (TdApi.Message message, boolean atBottom) {
    if (!wouldCombineWith(message))
      return false;
    boolean local = (flags & FLAG_LAYOUT_BUILT) == 0;
    synchronized (this) {
      if (combinedMessages == null) {
        combinedMessages = new ArrayList<>();
        combinedMessages.add(msg);
      }
      if (atBottom) {
        combinedMessages.add(message);
        msg = message;
      } else {
        combinedMessages.add(0, message);
      }
      onMessageCombinedWithOtherMessage(message, atBottom, local);
      forceCancelGlobalAnimation(true);
    }
    updateInteractionInfo(false);
    if (!local) {
      if (atBottom) {
        layoutInfo();
      }
      rebuildAndUpdateContent();
    }
    return true;
  }

  public final int getCombinedMessageCount () {
    synchronized (this) {
      return combinedMessages != null ? combinedMessages.size() : 0;
    }
  }

  private static final int MESSAGE_INDEX_NOT_FOUND = -1;
  private static final int MESSAGE_INDEX_SELF = -2;

  private int indexOfMessageInternal (long messageId) {
    if (combinedMessages != null) {
      int index = 0;
      for (TdApi.Message msg : combinedMessages) {
        if (msg.id == messageId) {
          return index;
        }
        index++;
      }
    }
    if (msg.id == messageId) {
      return MESSAGE_INDEX_SELF;
    }
    return MESSAGE_INDEX_NOT_FOUND;
  }

  public static final int REMOVE_NOTHING = 0;
  public static final int REMOVE_COMPLETELY = 1;
  public static final int REMOVE_COMBINATION = 2;

  @UiThread
  public final int removeMessage (long messageId) {
    synchronized (this) {
      int index = indexOfMessageInternal(messageId);
      if (index >= 0) {
        TdApi.Message message = combinedMessages.remove(index);
        if (index == combinedMessages.size() && index > 0) {
          msg = combinedMessages.get(index - 1);
        }
        if (combinedMessages.isEmpty()) {
          return REMOVE_COMPLETELY;
        }
        onMessageCombinationRemoved(message, index);
        forceRemoveAnimation(messageId);
        updateInteractionInfo(true);
        return REMOVE_COMBINATION;
      }
      if (index == MESSAGE_INDEX_SELF) {
        return combinedMessages != null && !combinedMessages.isEmpty() ? REMOVE_NOTHING : REMOVE_COMPLETELY;
      }
      return REMOVE_NOTHING;
    }
  }

  public final long getChatId () {
    return msg.chatId;
  }

  private TdApi.ChatAdministrator administrator;

  private String getAdministratorSign () {
    String result = null;
    if (isSponsored()) {
      return null;
    } else if (administrator != null) {
      if (!StringUtils.isEmpty(administrator.customTitle))
        result = administrator.customTitle;
      else if (administrator.isOwner)
        result = Lang.getString(R.string.message_ownerSign);
      else
        result = Lang.getString(R.string.message_adminSignPlain);
    } else if (sender.isAnonymousGroupAdmin()) {
      result = !StringUtils.isEmpty(msg.authorSignature) ? msg.authorSignature : Lang.getString(R.string.message_adminSignPlain);
    } else if (StringUtils.isEmpty(msg.authorSignature) && msg.chatId != 0 && tdlib.isMultiChat(msg.chatId)) {
      long chatId = sender.getChatId();
      if (tdlib.isChannel(chatId)) {
        result = Lang.getString(R.string.message_channelSign);
      } else if (ChatId.isMultiChat(chatId)) {
        result = Lang.getString(R.string.message_groupSign);
      }
    }
    if (result != null) {
      if (useBubbles())
        return "  " + result.trim();
      else
        return result;
    }
    return null;
  }

  public final TdApi.ChatAdministrator getAdministrator () {
    return administrator;
  }

  private boolean needAdminSign () {
    return getAdministratorSign() != null;
  }

  public final void setAdministratorSign (@Nullable TdApi.ChatAdministrator administrator) {
    final boolean isAdmin = (this.administrator != null || sender.isAnonymousGroupAdmin()) && !isOutgoing();
    final boolean nowIsAdmin = administrator != null;
    if (isAdmin != nowIsAdmin || isAdmin) {
      this.administrator = administrator;
      if ((flags & FLAG_LAYOUT_BUILT) != 0) {
        if (useBubbles()) {
          buildHeader();
          buildBubble(false);
        } else {
          buildTime();
          buildHeader();
        }
        invalidate();
      } else {
        buildTime();
      }
    }
  }

  public final int getDate () {
    return msg.date;
  }

  public final int getComparingDate () {
    return eventDate != 0 ? eventDate : msg.schedulingState != null ? (msg.schedulingState.getConstructor() == TdApi.MessageSchedulingStateSendAtDate.CONSTRUCTOR ? ((TdApi.MessageSchedulingStateSendAtDate) msg.schedulingState).sendDate : 0) : msg.date;
  }

  public final boolean isSending () {
    return msg.sendingState != null && msg.sendingState.getConstructor() == TdApi.MessageSendingStatePending.CONSTRUCTOR && !tdlib.qack().isMessageAcknowledged(msg.chatId, msg.id);
  }

  public final boolean isPsa () {
    return msg.forwardInfo != null && !StringUtils.isEmpty(msg.forwardInfo.publicServiceAnnouncementType) && !sender.isUser();
  }

  public boolean isSponsored () {
    return false;
  }

  public final int getPinnedMessageCount () {
    if (isThreadHeader()) {
      return 0;
    }
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        int messageCount = 0;
        for (TdApi.Message message : combinedMessages) {
          if (message.isPinned) {
            messageCount++;
          }
        }
        return messageCount;
      }
    }
    return msg.isPinned ? 1 : 0;
  }

  public final boolean isPinned () {
    if (isThreadHeader())
      return false;
    if (msg.isPinned)
      return true;
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message message : combinedMessages) {
          if (message.isPinned) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public final boolean isOld () {
    return tdlib().timeElapsedSinceDate(getDate(), TimeUnit.SECONDS) >= TimeUnit.MINUTES.toMillis(5);
  }

  public final boolean isChatMember () {
    TdApi.Chat chat = tdlib.chat(getChatId());
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          return true;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          TdApi.ChatMemberStatus status = tdlib.chatStatus(chat.id);
          return status != null && TD.isMember(status, false);
        }
      }
    }
    return false;
  }

  protected boolean isBeingEdited () {
    return false; // override in children
  }

  public final boolean isFailed () {
    return msg.sendingState != null && msg.sendingState.getConstructor() == TdApi.MessageSendingStateFailed.CONSTRUCTOR;
  }

  public final boolean canResend () {
    if (!(msg.sendingState instanceof TdApi.MessageSendingStateFailed) || !((TdApi.MessageSendingStateFailed) msg.sendingState).canRetry) {
      return false;
    }
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message msg : combinedMessages) {
          if (!(msg.sendingState instanceof TdApi.MessageSendingStateFailed) || !((TdApi.MessageSendingStateFailed) msg.sendingState).canRetry) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public final String[] getFailureMessages () {
    Set<String> errors = new HashSet<>();
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message msg : combinedMessages) {
          if (msg.sendingState instanceof TdApi.MessageSendingStateFailed) {
            TdApi.MessageSendingStateFailed failed = (TdApi.MessageSendingStateFailed) msg.sendingState;
            errors.add(TD.toErrorString(new TdApi.Error(failed.errorCode, failed.errorMessage)));
          }
        }
      } else {
        if (msg.sendingState instanceof TdApi.MessageSendingStateFailed) {
          TdApi.MessageSendingStateFailed failed = (TdApi.MessageSendingStateFailed) msg.sendingState;
          errors.add(TD.toErrorString(new TdApi.Error(failed.errorCode, failed.errorMessage)));
        }
      }
    }
    return errors.isEmpty() ? null : errors.toArray(new String[0]);
  }

  public final boolean isNotSent () {
    return msg.sendingState != null;
  }

  public final boolean isAnimating () {
    return false; // FIXME return correct animation state
  }

  public final boolean canBeDeletedForSomebody () {
    return (msg.canBeDeletedOnlyForSelf || msg.canBeDeletedForAllUsers) && allowInteraction();
  }

  public final boolean canBeReported () {
    return !isSelfChat() && msg.sendingState == null && !msg.isOutgoing && tdlib.canReportChatSpam(msg.chatId) && !isEventLog();
  }

  public final boolean canViewStatistics () {
    return msg.canGetStatistics;
  }

  public final boolean canGetViewers () {
    return msg.canGetViewers;
  }

  public final boolean canBeDeletedOnlyForSelf () {
    return msg.canBeDeletedOnlyForSelf;
  }

  public final boolean canBeDeletedForEveryone () {
    return msg.canBeDeletedForAllUsers;
  }

  public final boolean canBeSelected () {
    return (!isNotSent() || canResend()) && (flags & FLAG_UNSUPPORTED) == 0 && !(this instanceof TGMessageChat) && allowInteraction() && !isSponsored();
  }

  public boolean canEditText () {
    return msg.canBeEdited && TD.canEditText(msg.content) && allowInteraction() && messagesController().canWriteMessages();
  }

  public boolean canBeForwarded () {
    return msg.canBeForwarded && (msg.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR || ((TdApi.MessageLocation) msg.content).expiresIn == 0) && !isEventLog();
  }

  public boolean canBeSaved () {
    return msg.canBeSaved;
  }

  public boolean isUnread () {
    return (flags & MESSAGE_FLAG_READ) == 0 || (msg.sendingState != null);
  }

  public boolean checkIsUnread (boolean needMention) {
    if (needMention && msg.containsUnreadMention)
      return true;
    TdApi.Chat chat = getChat();
    if (chat == null) {
      chat = tdlib.chat(msg.chatId);
      setChatData(chat);
    }
    return chat != null && (msg.isOutgoing ? chat.lastReadOutboxMessageId : chat.lastReadInboxMessageId) < getBiggestId();
  }

  public boolean isChannel () {
    return msg.isChannelPost;
  }

  public final boolean isSecretChat () {
    return ChatId.isSecret(msg.chatId);
  }

  public boolean isGame () {
    return msg.content.getConstructor() != TdApi.MessageGame.CONSTRUCTOR;
  }

  public boolean isOutgoing () {
    return msg.isOutgoing && !isEventLog();
  }

  @CallSuper
  public void markAsBeingAdded (boolean isBeingAdded) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_BEING_ADDED, isBeingAdded);
  }

  public final boolean isBeingAdded () {
    return BitwiseUtils.getFlag(flags, FLAG_BEING_ADDED);
  }

  public boolean canMarkAsViewed () {
    return msg.id != 0 && msg.chatId != 0 && (flags & FLAG_VIEWED) == 0;
  }

  public boolean markAsViewed () {
    if (canMarkAsViewed()) {
      flags |= FLAG_VIEWED;
      if (msg.containsUnreadMention)
        highlight(true);
      return true;
    }
    return false;
  }

  public boolean needRefreshViewCount () {
    return viewCounter != null && !isSending();
  }

  public void markAsUnread () {
    flags &= ~FLAG_VIEWED;
  }

  public boolean isEdited () {
    if (msg.editDate > 0) {
      return true;
    }
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message message : combinedMessages) {
          if (message.editDate > 0) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected boolean replaceTimeWithEditTime () {
    return false;
  }

  public int getMergeIndex () {
    if ((flags & FLAG_HEADER_ENABLED) != 0) {
      return 0;
    } else {
      return mergeIndex;
    }
  }

  public int getMergeTime () {
    if ((flags & FLAG_HEADER_ENABLED) != 0 || mergeTime == 0) {
      return msg.date;
    } else {
      return mergeTime;
    }
  }

  public boolean isForward () {
    return msg.forwardInfo != null;
  }

  // Setters

  private @Nullable TdApi.Chat chat;

  public @Nullable TdApi.Chat getChat () {
    return chat;
  }

  public long getChannelId () {
    return ChatId.toSupergroupId(msg.chatId);
  }

  private void setChatData (TdApi.Chat chat) {
    this.chat = chat;
    int flags = this.flags;
    flags = BitwiseUtils.setFlag(flags, FLAG_NO_UNREAD, needNoUnread());
    flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, isSelfChat());
    this.flags = flags;

    if (isOutgoing() && !isSending()) {
      setUnread(chat.lastReadOutboxMessageId);
    }

    /*if (replyData != null && TD.isMultiChat(chat)) {
      replyData.setUseColorize(!useBubbles());
    }*/

    if (tdlib.isChannelChat(chat)) {
      if (replyData != null) {
        replyData.setChannelTitle(chat.title);
      }
      buildTime();
    }
  }

  private boolean needNoUnread () {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          long userId = ((TdApi.ChatTypePrivate) chat.type).userId;
          return tdlib.isSelfUserId(userId) || tdlib.cache().userBot(userId);
        }
      }
    }
    return false;
  }

  private boolean isSelfChat () {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR && tdlib.isSelfUserId(((TdApi.ChatTypePrivate) chat.type).userId);
  }

  public final boolean needMessageButton () {
    return ((flags & FLAG_SELF_CHAT) != 0 || isChannelAutoForward() || isRepliesChat()) && msg.forwardInfo != null && msg.forwardInfo.fromChatId != 0 &&  msg.forwardInfo.fromMessageId != 0 &&  msg.forwardInfo.fromChatId != msg.chatId;
  }

  public final void openSourceMessage () {
    if (msg.forwardInfo != null) {
      if (isRepliesChat()) {
        openMessageThread(new MessageId(msg.forwardInfo.fromChatId, msg.forwardInfo.fromMessageId));
      } else {
        tdlib.ui().openMessage(controller(), msg.forwardInfo.fromChatId, new MessageId(msg.forwardInfo.fromChatId, msg.forwardInfo.fromMessageId), openParameters());
      }
    }
  }

  public boolean noUnread () {
    return (flags & FLAG_NO_UNREAD) != 0 || isDemoChat();
  }

  public TdApi.Message findDescendantOrSelf (long messageId, long[] otherMessageIds) {
    if (msg.id == messageId || (otherMessageIds != null && ArrayUtils.contains(otherMessageIds, messageId))) {
      return msg;
    }
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message msg : combinedMessages) {
          if (msg.id == messageId || (otherMessageIds != null && ArrayUtils.contains(otherMessageIds, msg.id))) {
            return msg;
          }
        }
      }
    }
    return null;
  }

  public boolean isDescendantOrSelf (long messageId, long[] otherMessageIds) {
    return findDescendantOrSelf(messageId, otherMessageIds) != null;
  }

  public boolean isDescendantOrSelf (long messageId) {
    return findDescendantOrSelf(messageId) != null;
  }

  public @Nullable TdApi.Message findDescendantOrSelf (long messageId) {
    return findDescendantOrSelf(messageId, null);
  }

  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    return message.content.getConstructor() == messageContent.getConstructor();
  }

  @MessageChangeType
  public int setMessageContent (long messageId, TdApi.MessageContent newContent) {
    TdApi.Message message;
    boolean isBottomMessage;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        message = combinedMessages.get(i);
        isBottomMessage = i == combinedMessages.size() - 1;
      } else if (i == MESSAGE_INDEX_SELF) {
        message = msg;
        isBottomMessage = true;
      } else {
        return MESSAGE_NOT_CHANGED;
      }
    }
    if ((flags & FLAG_UNSUPPORTED) != 0) {
      if (message.content.getConstructor() == TdApi.MessageUnsupported.CONSTRUCTOR && newContent.getConstructor() != TdApi.MessageUnsupported.CONSTRUCTOR) {
        message.content = newContent;
        return MESSAGE_REPLACE_REQUIRED;
      }
      return MESSAGE_NOT_CHANGED;
    }
    if (isSupportedMessageContent(message, newContent)) {
      int height = getHeight();
      int width = getWidth();
      int contentWidth = getContentWidth();
      updateMessageContent(message, newContent, isBottomMessage);
      message.content = newContent;
      if (width != getWidth() || contentWidth != getContentWidth()) {
        buildMarkup();
      }
      return height == getHeight() ? MESSAGE_INVALIDATED : MESSAGE_CHANGED;
    }
    return MESSAGE_REPLACE_REQUIRED;
  }

  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    return false;
  }

  public void autoDownloadContent (TdApi.ChatType type) {
    // Override in children
  }

  protected boolean shouldHideMedia () {
    return (flags & FLAG_HIDE_MEDIA) != 0;
  }

  public final void setMediaVisible (MediaItem media, boolean isVisible) {
    if (isVisible) {
      flags |= FLAG_HIDE_MEDIA;
    } else {
      flags &= ~FLAG_HIDE_MEDIA;
    }
    invalidate();
  }

  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    // override
    return null;
  }

  public boolean updateUnread (long lastReadOutboxId, @Nullable TGMessage topMessage) {
    if (setUnread(lastReadOutboxId)) {
      if (isOutgoing()) {
        if ((flags & FLAG_HEADER_ENABLED) == 0) {
          if (topMessage == null || ((isUnread() || isSending()) != (topMessage.isUnread() || topMessage.isSending()))) {
            flags |= FLAG_SHOW_TICKS;
          } else {
            flags &= ~FLAG_SHOW_TICKS;
          }
        }
      }
      return true;
    }
    return false;
  }

  public boolean setUnread (long lastReadMessageId) {
    boolean wasRead = (flags & MESSAGE_FLAG_READ) != 0;
    if (msg.id > lastReadMessageId) {
      flags &= ~MESSAGE_FLAG_READ;
      return wasRead && !noUnread();
    } else {
      flags |= MESSAGE_FLAG_READ;
      return !wasRead && !noUnread();
    }
  }

  private void buildForwardTime () {
    fTime = genForwardTime();
    if ((flags & FLAG_LAYOUT_BUILT) != 0) {
      buildForward();
    }
  }

  private void buildTime () {
    this.time = genTime();
    if ((flags & FLAG_LAYOUT_BUILT) != 0 && (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0)) {
      layoutInfo();
    }
  }

  // Hot stuff

  public boolean isHot () {
    return ChatId.isUserChat(msg.chatId) && Td.isSecret(msg.content);
    // return msg.ttl > 0 && ((chat != null && chat.type.getConstructor() == TdApi.ChatTypePrivate.CONSTRUCTOR) || msg.ttl <= 60) && (flags & FLAG_EVENT_LOG) == 0 && !isEventLog();
  }

  public boolean isHotDone () {
    return isOutgoing() && msg.ttlExpiresIn < msg.ttl;
  }

  protected boolean needHotTimer () {
    return false; // override
  }

  protected void onHotInvalidate (boolean secondsChanged) {
    // override
  }

  private static final int HOT_CHECK_DELAY = 18;
  private long hotTimerStart;

  public void readContent () {
    if (!isEventLog()) {
      tdlib.client().send(new TdApi.OpenMessageContent(msg.chatId, msg.id), tdlib.okHandler());
      if (!isOutgoing()) {
        startHotTimer(true);
      }
    }
  }

  public boolean isContentRead () {
    return TD.isMessageOpened(msg);
  }

  private static @Nullable HotHandler __hotHandler;

  private static HotHandler getHotHandler () {
    if (__hotHandler == null) {
      synchronized (HotHandler.class) {
        if (__hotHandler == null) {
          __hotHandler = new HotHandler();
        }
      }
    }
    return __hotHandler;
  }

  private void startHotTimer (boolean byEvent) {
    if (isHot() && needHotTimer() && hotTimerStart == 0) {
      HotHandler hotHandler = getHotHandler();
      hotTimerStart = System.currentTimeMillis();
      hotHandler.sendMessageDelayed(Message.obtain(hotHandler, HotHandler.MSG_HOT_CHECK, this), HOT_CHECK_DELAY);
      onHotTimerStarted(byEvent);
    }
  }

  protected void onHotTimerStarted (boolean byEvent) {
    // override
  }

  protected boolean isHotTimerStarted () {
    return hotTimerStart != 0;
  }

  private void stopHotTimer () {
    if (hotTimerStart != 0) {
      hotTimerStart = 0;
      getHotHandler().removeMessages(HotHandler.MSG_HOT_CHECK, this);
    }
  }

  private void checkHotTimer () {
    long now = System.currentTimeMillis();
    long elapsed = now - hotTimerStart;
    double prevTtl = msg.ttlExpiresIn;
    hotTimerStart = now;
    msg.ttlExpiresIn = Math.max(0, prevTtl - (double) elapsed / 1000.0d);
    boolean secondsChanged = Math.round(prevTtl) != Math.round(msg.ttlExpiresIn);
    onHotInvalidate(secondsChanged);
    if (hotListener != null) {
      hotListener.onHotInvalidate(secondsChanged);
    }
    if (needHotTimer() && hotTimerStart != 0 && msg.ttlExpiresIn > 0) {
      HotHandler hotHandler = getHotHandler();
      hotHandler.sendMessageDelayed(Message.obtain(hotHandler, HotHandler.MSG_HOT_CHECK, this), HOT_CHECK_DELAY);
    }
  }

  public float getHotExpiresFactor () {
    return (float) (msg.ttlExpiresIn / msg.ttl);
  }

  public String getHotTimerText () {
    return TdlibUi.getDuration((int) Math.round(msg.ttlExpiresIn), TimeUnit.SECONDS, false);
  }

  public interface HotListener {
    void onHotInvalidate (boolean secondsChanged);
  }

  private HotListener hotListener;

  public void setHotListener (HotListener listener) {
    this.hotListener = listener;
  }

  // updates

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MESSAGE_NOT_CHANGED, MESSAGE_INVALIDATED, MESSAGE_CHANGED, MESSAGE_REPLACE_REQUIRED})
  public @interface MessageChangeType {}

  public static final int MESSAGE_NOT_CHANGED = 0;
  public static final int MESSAGE_INVALIDATED = 1;
  public static final int MESSAGE_CHANGED = 2;
  public static final int MESSAGE_REPLACE_REQUIRED = 3;

  public interface MessageIdChangeListener {
    void onMessageIdChanged (TGMessage msg, long oldMessageId, long newMessageId, boolean success);
  }

  @Nullable
  private ReferenceList<MessageIdChangeListener> messageIdChangeListeners;

  private void updateMessageId (long oldMessageId, long newMessageId, boolean success) {
    onMessageIdChanged(oldMessageId, newMessageId, success);
    if (messageIdChangeListeners != null) {
      for (MessageIdChangeListener listener : messageIdChangeListeners) {
        listener.onMessageIdChanged(this, oldMessageId, newMessageId, success);
      }
    }
  }

  public ReferenceList<MessageIdChangeListener> getMessageIdChangeListeners () {
    return messageIdChangeListeners != null ? messageIdChangeListeners : (messageIdChangeListeners = new ReferenceList<>());
  }

  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) { }

  public boolean onMessageSendAcknowledged (long messageId) {
    updateMessageId(messageId, messageId, true);
    return msg.id == messageId;
  }

  public boolean allowInteraction () {
    return !isEventLog() && !isThreadHeader();
  }

  public boolean canReplyTo () {
    return TD.canReplyTo(msg) && allowInteraction();
  }

  public boolean isScheduled () {
    return msg.schedulingState != null;
  }

  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    // override
    return false;
  }

  public final int setMessagePendingContentChanged (long chatId, long messageId) {
    int oldHeight = getHeight();
    return onMessagePendingContentChanged(chatId, messageId, oldHeight);
  }

  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    return MESSAGE_NOT_CHANGED;
  }

  public final int setSendSucceeded (TdApi.Message message, long oldMessageId) {
    TdApi.Message msg = null;
    boolean isBottomMessage = false;
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message combinedMessage : combinedMessages) {
          if (combinedMessage.id == oldMessageId) {
            msg = combinedMessage;
            isBottomMessage = combinedMessage.id == combinedMessages.get(combinedMessages.size() - 1).id;
            break;
          }
        }
      }
      if (msg == null && this.msg.id == oldMessageId) {
        msg = this.msg;
        isBottomMessage = true;
      }
    }
    if (msg == null || msg.id != oldMessageId) {
      return MESSAGE_NOT_CHANGED;
    }

    boolean replaceRequired = !isSupportedMessageContent(msg, message.content) ||
      (msg.forwardInfo == null) == (message.forwardInfo != null) ||
      (msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() != message.forwardInfo.origin.getConstructor()) ||
      msg.isOutgoing != message.isOutgoing;

    copyFlags(message, msg);
    if (inlineKeyboard != null) {
      inlineKeyboard.updateMessageId(oldMessageId, message.id);
    }

    if (isBottomMessage) {
      buildTime();
    }
    updateInteractionInfo(true);

    updateMessageId(oldMessageId, message.id, true);

    int oldHeight = getHeight();

    if (!replaceRequired && (flags & FLAG_UNSUPPORTED) == 0) {
      if (onMessageContentChanged(msg, msg.content, message.content, isBottomMessage)) {
        msg.content = message.content;
      }
    }

    if (!Td.equalsTo(msg.replyMarkup, message.replyMarkup)) {
      msg.replyMarkup = message.replyMarkup;
      // if (isBottomMessage) {
        buildMarkup();
        height = computeHeight();
      // }
    }

    return replaceRequired ? MESSAGE_REPLACE_REQUIRED : getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED;
  }

  private void updateInteractionInfo (boolean allowAnimation) {
    TdApi.MessageInteractionInfo interactionInfo = msg.interactionInfo;
    boolean animated = allowAnimation && needAnimateChanges();
    if (viewCounter != null) {
      viewCounter.setCount(interactionInfo != null ? interactionInfo.viewCount : 0, animated && getViewCountMode() != VIEW_COUNT_HIDDEN);
    }
    int commentMode = getCommentMode();
    replyCounter.setCount(getReplyCount(), commentMode == COMMENT_MODE_NONE && animated);
    hasCommentButton.setValue(commentMode == COMMENT_MODE_BUTTON, animated);
    shareCounter.setCount(interactionInfo != null ? interactionInfo.forwardCount : 0, animated);
    isPinned.showHide(isPinned(), animated);
  }

  private static void copyFlags (TdApi.Message src, TdApi.Message dst) {
    dst.id = src.id;
    dst.date = src.date;
    dst.sendingState = src.sendingState;

    dst.canBeDeletedOnlyForSelf = src.canBeDeletedOnlyForSelf;
    dst.canBeDeletedForAllUsers = src.canBeDeletedForAllUsers;
    dst.canGetMessageThread = src.canGetMessageThread;
    dst.canBeForwarded = src.canBeForwarded;
    dst.canBeSaved = src.canBeSaved;
    dst.canBeEdited = src.canBeEdited;
    dst.canGetStatistics = src.canGetStatistics;
    dst.canGetViewers = src.canGetViewers;
    dst.canGetMediaTimestampLinks = src.canGetMediaTimestampLinks;
    dst.hasTimestampedMedia = src.hasTimestampedMedia;

    dst.editDate = src.editDate;
    dst.isChannelPost = src.isChannelPost;
    dst.interactionInfo = src.interactionInfo;
  }

  public boolean setSendFailed (TdApi.Message message, long oldMessageId) {
    TdApi.Message msg = getMessage(oldMessageId);
    if (msg != null && msg.id == oldMessageId) {
      copyFlags(message, msg);
      if (inlineKeyboard != null) {
        inlineKeyboard.updateMessageId(oldMessageId, message.id);
      }
      buildTime();
      updateMessageId(oldMessageId, message.id, false);
      updateInteractionInfo(true);
      return true;
    }
    return false;
  }

  protected boolean onMessageEdited (long messageId, int editDate) {
    return false;
  }

  @MessageChangeType
  public int setMessageEdited (long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
    boolean affectsGroup = false;
    boolean markupChanged = false;
    boolean wasEdited = false;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        affectsGroup = i == combinedMessages.size() - 1;
        TdApi.Message msg = combinedMessages.get(i);
        wasEdited = msg.editDate != 0;
        msg.editDate = editDate;
        markupChanged = !Td.equalsTo(msg.replyMarkup, replyMarkup);
        msg.replyMarkup = replyMarkup;
      } else if (i == MESSAGE_INDEX_SELF) {
        affectsGroup = true;
        wasEdited = msg.editDate != 0;
        msg.editDate = editDate;
        markupChanged = !Td.equalsTo(msg.replyMarkup, replyMarkup);
        msg.replyMarkup = replyMarkup;
      }
    }
    if (affectsGroup || markupChanged) {
      if (affectsGroup && !wasEdited && (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0)) {
        layoutInfo();
      }
      if (markupChanged) {
        buildMarkup();
        int oldHeight = getHeight();
        height = computeHeight();
        return getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED;
      }
      if (onMessageEdited(messageId, editDate) || !wasEdited) {
        return MESSAGE_INVALIDATED;
      }
    } else if (onMessageEdited(messageId, editDate)) {
      return MESSAGE_INVALIDATED;
    }
    return MESSAGE_NOT_CHANGED;
  }

  public void setMessageOpened (long messageId) {
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        TD.setMessageOpened(combinedMessages.get(i));
      } else if (i == MESSAGE_INDEX_SELF) {
        TD.setMessageOpened(msg);
      } else {
        return;
      }
    }
    startHotTimer(true);
    onMessageContentOpened(messageId);
  }

  protected void onMessageContentOpened (long messageId) {
    // override
  }

  public boolean setMessageInteractionInfo (long messageId, TdApi.MessageInteractionInfo interactionInfo) {
    boolean changed;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        combinedMessages.get(i).interactionInfo = interactionInfo;
        changed = i == combinedMessages.size() - 1;
      } else if (i == MESSAGE_INDEX_SELF) {
        msg.interactionInfo = interactionInfo;
        changed = true;
      } else {
        return false;
      }
      if (changed) {
        updateInteractionInfo(true);
      }
    }
    return changed;
  }

  public boolean setIsPinned (long messageId, boolean isPinned) {
    boolean changed;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        if (changed = (combinedMessages.get(i).isPinned != isPinned)) {
          combinedMessages.get(i).isPinned = isPinned;
          changed = true;
        }
        if (changed && !isPinned) {
          for (TdApi.Message message : combinedMessages) {
            if (message.isPinned) {
              changed = false;
              break;
            }
          }
        }
      } else if (i == MESSAGE_INDEX_SELF) {
        changed = msg.isPinned != isPinned;
        msg.isPinned = isPinned;
      } else {
        return false;
      }
    }
    if (changed) {
      this.isPinned.showHide(isPinned, needAnimateChanges());
      return true;
    }
    return false;
  }

  public void setMergeBottom (boolean mergeBottom) {
    if (mergeBottom) {
      flags |= FLAG_MERGE_BOTTOM;
    } else {
      flags &= ~FLAG_MERGE_BOTTOM;
    }
  }

  private static String getBadgeText () {
    return Lang.getString(R.string.NewMessages);
  }

  public void setShowUnreadBadge (boolean show) {
    flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_BADGE, show);
    uBadge = show ? new Letters(getBadgeText()) : null;
    if (BitwiseUtils.getFlag(flags, FLAG_LAYOUT_BUILT)) {
      rebuildLayout();
      requestLayout();
      invalidate();
    }
  }

  public boolean hasUnreadBadge () {
    return (flags & FLAG_SHOW_BADGE) != 0;
  }

  private boolean isBottomMessage () {
    return (flags & MESSAGE_FLAG_IS_BOTTOM) != 0;
  }

  public void setIsBottom (boolean isBottom) {
    if (isBottom != isBottomMessage()) {
      if (isBottom) {
        flags |= MESSAGE_FLAG_IS_BOTTOM;
      } else {
        flags &= ~MESSAGE_FLAG_IS_BOTTOM;
      }
      if ((flags & FLAG_LAYOUT_BUILT) != 0) {
        buildBubble(true);
      }
    }
  }

  public void setIsThreadHeader (boolean isThreadHeader) {
    this.flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_IS_THREAD_HEADER, isThreadHeader);
    updateInteractionInfo(false);
  }

  protected int getBubbleContentPadding () {
    return xBubblePadding;
  }

  private int getBubblePaddingLeft () {
    return useForward() ? xBubblePadding + xBubblePaddingSmall : getBubbleContentPadding();
  }

  private static int getBubbleNameHeight () {
    return Screen.dp(24f);
  }

  private static int getPsaTitleHeight () {
    return Screen.dp(20f);
  }

  public boolean useBubbleName () {
    return (flags & FLAG_HEADER_ENABLED) != 0 && needName(true);
  }

  private int getBubbleSpecialPaddingTop () {
    /*if (disableBubble()) {
      return msg.replyToMessageId != 0 && !alignReplyHorizontally() ? Screen.dp(3f) : 0;
    }*/
    int padding = 0;
    if (hasHeader() && needName(true) && (flags & FLAG_HEADER_ENABLED) != 0) {
      padding = Math.max(xBubblePadding, getBubbleContentPadding());
    } else if (replyData != null && !alignReplyHorizontally()) {
      if (disableBubble()) {
        padding = Screen.dp(3f);
      } else {
        padding = Math.max(xBubblePadding, getBubbleContentPadding());
      }
    }
    if (padding == 0 && useForward()) {
      padding = Math.max(xBubblePadding + xBubblePaddingSmall, getBubbleContentPadding());
    }
    return padding;
  }

  private int getBubblePaddingTop () {
    int specialPadding = getBubbleSpecialPaddingTop();
    return specialPadding != 0 ? specialPadding : getBubbleContentPadding();
  }

  protected final int getBubblePaddingRight () {
    return useForward() ? xBubblePadding + xBubblePaddingSmall : getBubbleContentPadding();
  }

  private int getBubblePaddingBottom () {
    return useForward() ? xBubblePadding + xBubblePaddingSmall : getBubbleContentPadding();
  }

  public boolean setNeedExtraPadding (boolean needPadding) {
    int oldHeight = height;
    if (needPadding) {
      if ((flags & FLAG_EXTRA_PADDING) == 0) {
        flags |= FLAG_EXTRA_PADDING;
        if ((flags & FLAG_LAYOUT_BUILT) != 0) {
          height = computeHeight();
        }
      }
    } else {
      if ((flags & FLAG_EXTRA_PADDING) != 0) {
        flags &= ~FLAG_EXTRA_PADDING;
        if ((flags & FLAG_LAYOUT_BUILT) != 0) {
          height = computeHeight();
        }
      }
    }
    return oldHeight != height;
  }

  public boolean setNeedExtraPresponsoredPadding (boolean needPadding) {
    int oldHeight = height;
    if (needPadding) {
      if (!needSponsorSmallPadding) {
        needSponsorSmallPadding = true;
        if ((flags & FLAG_LAYOUT_BUILT) != 0) {
          height = computeHeight();
        }
      }
    } else {
      if (needSponsorSmallPadding) {
        needSponsorSmallPadding = false;
        if ((flags & FLAG_LAYOUT_BUILT) != 0) {
          height = computeHeight();
        }
      }
    }
    return oldHeight != height;
  }

  private void setHeaderEnabled (boolean enabled) {
    boolean isEnabledNow = (flags & FLAG_HEADER_ENABLED) != 0;
    if (enabled != isEnabledNow) {
      if (enabled) {
        flags |= FLAG_HEADER_ENABLED;
      } else {
        flags &= ~FLAG_HEADER_ENABLED;
      }
      if ((flags & FLAG_LAYOUT_BUILT) != 0) {
        buildBubble(true);
      }
    }
  }

  // Destructor

  private boolean isDestroyed;

  public final void onDestroy () {
    isDestroyed = true;
    stopHotTimer();
    if (forwardInfo != null)
      forwardInfo.destroy();
    if (replyData != null)
      replyData.performDestroy();
    setViewAttached(false);
    onMessageContainerDestroyed();
  }

  public final boolean isDestroyed () {
    return isDestroyed;
  }

  protected void onMessageContainerDestroyed () {
    // implement in children
  }

  // Selection

  private float highlightFactor = 0f;
  private float moveFactor, selectionFactor;
  private SelectionInfo selection;

  public int getSelectionColor (float factor) {
    final boolean useBubbles = useBubbles();
    if (useBubbles) {
      return ColorUtils.alphaColor(factor, ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_bubble_messageSelection), Theme.getColor(R.id.theme_color_bubble_messageSelectionNoWallpaper), manager.controller().wallpaper().getBackgroundTransparency()));
    } else {
      // manager.controller().wallpaper().getOverlayColor(R.id.theme_color_chatTransparentColor)
      final int color = Theme.chatSelectionColor();
      return ColorUtils.alphaColor(factor, ColorUtils.compositeColor(Theme.getColor(R.id.theme_color_chatBackground), color));
    }
    /*final int color = useBubbles ? Theme.getColor(R.id.theme_color_messageBubbleSelection) : Theme.chatSelectionColor();
    return U.alphaColor(factor, useBubbles ? color : U.compositeColor(Theme.getColor(R.id.theme_color_chatPlainBackground), color));*/
  }

  private int findBottomEdge () {
    return height - getExtraPadding();
  }

  private void drawSelection (View view, Canvas c) {
    if (selectionFactor == 1f) {
      c.drawRect(0, findTopEdge(), view.getMeasuredWidth(), findBottomEdge(), Paints.fillingPaint(getSelectionColor(1f)));
      return;
    }
    if (selection != null) {
      c.save();
      c.clipRect(0, selection.startY, view.getMeasuredWidth(), findBottomEdge());
      c.drawCircle(selection.pivotX + selection.diffX * selectionFactor, selection.pivotY + selection.diffY * selectionFactor, selection.expectedRadius * selectionFactor, Paints.fillingPaint(getSelectionColor(1f)));
      c.restore();
    }
  }

  public void onSelectableFactorChanged () {
    invalidate(true);
  }

  private void setSelectionFactor (float factor) {
    if (this.selectionFactor != factor) {
      this.selectionFactor = factor;
      invalidateParentOrSelf(false);
    }
  }

  public int findTopEdge () {
    return getHeaderPadding() - ((flags & FLAG_HEADER_ENABLED) != 0 && !useBubbles() ? xHeaderPadding : 0);
  }

  private LongList selectedMessageIds;
  private LongSparseArray<FactorAnimator> selectionAnimators;

  public final boolean isCompletelySelected () {
    synchronized (this) {
      int selectedCount = selectedMessageIds != null ? selectedMessageIds.size() : 0;
      if (selectedCount == 0) {
        return false;
      }
      int combinedCount = combinedMessages != null ? combinedMessages.size() : 1;
      return combinedCount > 0 && selectedCount >= combinedCount;
    }
  }

  public final boolean hasSelectedMessages () {
    synchronized (this) {
      return selectedMessageIds != null && selectedMessageIds.size() > 0;
    }
  }

  public final boolean shouldApplySelectionFully () {
    synchronized (this) {
      return combinedMessages == null || combinedMessages.size() <= 1;
    }
  }

  protected final FactorAnimator findSelectionAnimator (long messageId) {
    synchronized (this) {
      if (selectionAnimators != null) {
        return selectionAnimators.get(messageId);
      }
    }
    return null;
  }

  protected void onAnimatorAttachedToMessage (long messageId, FactorAnimator animator) {
    // override
  }

  protected void onMessageSelectionChanged (long messageId, float selectionFactor, boolean needInvalidate) { }

  public final float getSelectionFactor (FactorAnimator childAnimator) {
    return childAnimator == null || (childAnimator.getIntValue() & ANIMATION_FLAG_IGNORE_CHILD) != 0f || shouldApplySelectionFully() ? 0f : childAnimator.getFactor();
  }

  public final long findMessageIdUnder (float x, float y) {
    x -= getSelectableContentOffset(manager.getSelectableFactor());
    return findChildMessageIdUnder(x, y);
  }

  protected long findChildMessageIdUnder (float x, float y) {
    return 0;
  }

  private static final int ANIMATION_FLAG_AFFECTS_CONTAINER = 1;
  private static final int ANIMATION_FLAG_IGNORE_CHILD = 1 << 1;
  private static final int ANIMATION_FLAG_AFFECTS_SELECTABLE = 1 << 2;
  private static final int ANIMATION_FLAG_IGNORE_SELF = 1 << 3;

  private void forceCancelGlobalAnimation (boolean reset) {
    if (selectedMessageIds != null) {
      final int size = selectionAnimators.size();
      for (int i = 0; i < size; i++) {
        FactorAnimator animator = selectionAnimators.valueAt(i);
        if (animator != null && animator.getIntFlag(ANIMATION_FLAG_AFFECTS_CONTAINER)) {
          animator.setIntFlag(ANIMATION_FLAG_AFFECTS_CONTAINER, false);
        }
      }
      if (reset) {
        setSelectionFactor(0f);
      }
    }
  }

  private void forceRemoveAnimation (long messageId) {
    if (selectedMessageIds != null) {
      selectedMessageIds.remove(messageId);

      forceCancelGlobalAnimation(false);

      int totalCount = combinedMessages != null ? combinedMessages.size() : 1;
      boolean needGlobalSelection = totalCount == selectedMessageIds.size();
      boolean animate = needGlobalSelection && currentViews.hasAnyTargetToInvalidate();

      int i = selectionAnimators.indexOfKey(messageId);
      if (i >= 0) {
        FactorAnimator animator = selectionAnimators.valueAt(i);
        if (animate) {
          animator.setIntFlag(ANIMATION_FLAG_IGNORE_CHILD, true);
          animator.forceFactor(selectionFactor);
          animator.animateTo(1f);
        } else if (animator.getIntFlag(ANIMATION_FLAG_AFFECTS_SELECTABLE)) {
          selectionAnimators.removeAt(i);
        } else {
          animator.cancel();
          selectionAnimators.removeAt(i);
        }
      }

      if (needGlobalSelection && !animate) {
        setSelectionFactor(1f);
      }
    }
  }

  private boolean prepareSelectionAnimation (float pivotX, float pivotY) {
    final int viewWidth = currentViews.getMeasuredWidth();
    final View view = findCurrentView();

    if (viewWidth == 0 || view == null || !currentViews.hasAnyTargetToInvalidate() || view.getParent() == null) {
      return false;
    }

    final int viewHeight = view.getMeasuredHeight();
    if (viewHeight > ((View) view.getParent()).getMeasuredHeight() * 2.5f) {
      return false;
    }

    int startY = findTopEdge();
    int endX = viewWidth;
    int endY = findBottomEdge();

    float targetX = (int) ((float) endX * .5f);
    float targetY = startY + (int) ((float) (endY - startY) * .5f);

    if (pivotX == -1 && pivotY == -1) {
      pivotX = targetX;
      pivotY = targetY;
    }

    float diffX = targetX - pivotX;
    float diffY = targetY - pivotY;

    float circleHeight = endY - startY;
    float radius = (float) Math.sqrt(endX * endX + circleHeight * circleHeight) * .5f;

    if (selection == null) {
      selection = new SelectionInfo(startY, pivotX, pivotY, diffX, diffY, radius);
    } else {
      selection.startY = startY;
      selection.pivotX = pivotX;
      selection.pivotY = pivotY;
      selection.diffX = diffX;
      selection.diffY = diffY;
      selection.expectedRadius = radius;
    }

    return true;
  }

  private static final int ANIMATOR_SELECT = -3;

  public interface SelectableDelegate {
    void onSelectableModeChanged (boolean isSelectable, FactorAnimator animator);
    void onSelectableFactorChanged (float factor, FactorAnimator callee);
  }

  /*public interface CommonDelegate {
    boolean isMessageSelected (long chatId, long messageId, TGMessage container);
    float getSelectableFactor ();
  }*/

  // private @Nullable CommonDelegate commonDelegate;

  private void initSelectedMessages () {
    LongList ids = null;
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        int skippedCount = 0;
        for (TdApi.Message message : combinedMessages) {
          if (manager.isMessageSelected(message.chatId, message.id, this)) {
            if (ids == null) {
              ids = new LongList(combinedMessages.size() - skippedCount);
            }
            ids.append(message.id);
          } else {
            skippedCount++;
          }
        }
      }
    }
    if (ids != null) {
      final int size = ids.size();
      for (int i = 0; i < size; i++) {
        long messageId = ids.get(i);
        setSelected(messageId, true, false, -1, -1, null);
      }
    } else if (manager.isMessageSelected(msg.chatId, msg.id, this)) {
      setSelected(msg.id, true, false, -1, -1, null);
    }
  }

  public void setSelected (long messageId, boolean isSelected, boolean animated, float pivotX, float pivotY, @Nullable SelectableDelegate selectable) {
    if (isSelected && !canBeSelected()) {
      return;
    }

    boolean affectsSelectable = selectable != null && useBubbles();
    FactorAnimator targetAnimator;
    boolean hasGlobalEffect;

    synchronized (this) {
      int totalCount = combinedMessages != null && !combinedMessages.isEmpty() ? combinedMessages.size() : 1;

      if (isSelected) {
        if (selectedMessageIds == null) {
          selectedMessageIds = new LongList(totalCount);
        } else if (selectedMessageIds.contains(messageId)) {
          return;
        }
        selectedMessageIds.append(messageId);
        hasGlobalEffect = totalCount == selectedMessageIds.size();
      } else {
        if (selectedMessageIds == null || !selectedMessageIds.remove(messageId)) {
          return;
        }
        hasGlobalEffect = totalCount - 1 == selectedMessageIds.size();
      }

      if (selectionAnimators != null) {
        targetAnimator = selectionAnimators.get(messageId);
      } else {
        targetAnimator = null;
        selectionAnimators = new LongSparseArray<>();
      }
      if (targetAnimator == null) {
        targetAnimator = new FactorAnimator(ANIMATOR_SELECT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);
        targetAnimator.setLongValue(messageId);
        selectionAnimators.put(messageId, targetAnimator);
        onAnimatorAttachedToMessage(messageId, targetAnimator);
      }
      targetAnimator.setIntFlag(ANIMATION_FLAG_AFFECTS_CONTAINER, hasGlobalEffect);
      targetAnimator.setIntFlag(ANIMATION_FLAG_AFFECTS_SELECTABLE, affectsSelectable);
      targetAnimator.setObjValue(affectsSelectable ? selectable : null);
      if (affectsSelectable) {
        selectable.onSelectableModeChanged(isSelected, targetAnimator);
      }
    }

    if (hasGlobalEffect) {
      animated = animated && prepareSelectionAnimation(pivotX, pivotY);
    }
    if (animated) {
      animated = currentViews.hasAnyTargetToInvalidate();
    }

    targetAnimator.setIntFlag(ANIMATION_FLAG_IGNORE_SELF, !animated && affectsSelectable);

    final float toFactor = isSelected ? 1f : 0f;

    if (animated || affectsSelectable) {
      targetAnimator.animateTo(toFactor);
    } else {
      targetAnimator.forceFactor(toFactor);
    }
    if (!animated && hasGlobalEffect) {
      setSelectionFactor(toFactor);
    }
  }

  // Animations

  @Override
  public boolean needAnimateChanges (Counter counter) {
    return needAnimateChanges();
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (sizeChanged && BitwiseUtils.getFlag(flags, FLAG_LAYOUT_BUILT)) {
      if (counter == viewCounter) {
        switch (getViewCountMode()) {
          case VIEW_COUNT_FORWARD:
            buildForward();
            break;
          case VIEW_COUNT_MAIN:
            if (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0) {
              layoutInfo();
            }
            break;
        }
      } else if ((counter == replyCounter && getCommentMode() == COMMENT_MODE_NONE) || counter == shareCounter || counter == isPinned) {
        if (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0) {
          layoutInfo();
        }
      }
    }
    if (UI.inUiThread()) { // FIXME remove this after reworking combineWith method
      invalidate();
    } else {
      postInvalidate();
    }
  }

  @Override
  public final void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id >= 0) {
      onChildFactorChanged(id, factor, fraction);
    } else {
      switch (id) {
        case ANIMATOR_REVOKE: {
          setHighlight(1f - factor);
          break;
        }
        case ANIMATOR_DISMISS: {
          setDismiss(factor);
          break;
        }
        case ANIMATOR_READY_SHARE: {
          if (this.shareReadyFactor != factor) {
            this.shareReadyFactor = factor;
            invalidate();
          }
          break;
        }
        case ANIMATOR_READY_REPLY: {
          if (this.replyReadyFactor != factor) {
            this.replyReadyFactor = factor;
            invalidate();
          }
          break;
        }
        case ANIMATOR_SELECT: {
          long messageId = callee.getLongValue();
          int intValue = callee.getIntValue();
          boolean hasGlobalEffect = (intValue & ANIMATION_FLAG_AFFECTS_CONTAINER) != 0;
          boolean ignoreSelf = (intValue & ANIMATION_FLAG_IGNORE_SELF) != 0;
          if (!ignoreSelf) {
            if ((intValue & ANIMATION_FLAG_IGNORE_CHILD) == 0) {
              onMessageSelectionChanged(messageId, factor, !hasGlobalEffect || this.selectionFactor == factor);
            }
            if (hasGlobalEffect) {
              setSelectionFactor(factor);
            }
          }
          if ((intValue & ANIMATION_FLAG_AFFECTS_SELECTABLE) != 0) {
            SelectableDelegate delegate = (SelectableDelegate) callee.getObjValue();
            if (delegate != null) {
              delegate.onSelectableFactorChanged(factor, callee);
            }
          }
          break;
        }
      }
    }
  }

  @Override
  public final void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id >= 0) {
      onChildFactorChangeFinished(id, finalFactor, callee);
    }
  }

  protected void onChildFactorChanged (int id, float factor, float fraction) {
    // override
  }

  protected void onChildFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    // override
  }

  // Highlight

  public void drawHighlight (View view, Canvas c) {
    if (highlightFactor != 0f) {
      c.drawRect(0, findTopEdge(), view.getMeasuredWidth(), findBottomEdge(), Paints.fillingPaint(getSelectionColor(highlightFactor)));
    }
  }

  public void highlight (boolean revoke) {
    cancelHighlightRevoke();
    setHighlight(1f);
    if (revoke) {
      revokeHighlight();
    }
  }

  private void setHighlight (float highlight) {
    if (this.highlightFactor != highlight) {
      this.highlightFactor = highlight;
      invalidateParentOrSelf(false);
    }
  }

  private FactorAnimator revokeAnimator;
  private static final int ANIMATOR_REVOKE = -1;

  private void cancelHighlightRevoke () {
    if (revokeAnimator != null) {
      revokeAnimator.cancel();
      revokeAnimator = null;
    }
  }

  public void revokeHighlight () {
    if (revokeAnimator != null && revokeAnimator.isAnimating()) {
      return;
    }
    if (revokeAnimator == null) {
      revokeAnimator = new FactorAnimator(ANIMATOR_REVOKE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 400l, 0f);
      revokeAnimator.setStartDelay(2000l);
    } else {
      revokeAnimator.forceFactor(0f);
    }
    revokeAnimator.animateTo(1f);
  }

  // Translation

  private float translation;
  private float dismissFactor;

  public void resetTransformState () {
    if (highlightFactor == 1f) {
      revokeHighlight();
    }
  }

  public float getTranslation () {
    return translation;
  }

  public boolean canSwipe () {
    return ((flags & FLAG_IGNORE_SWIPE) == 0);
  }

  public void normalizeTranslation (final View view, @Nullable final Runnable after, final boolean needDelay) {
    if (translation == 0f) {
      return;
    }

    if (after != null && ((flags & FLAG_READY_REPLY) == 0) && (flags & FLAG_READY_SHARE) == 0) {
      vibrate();
    }

    if (!needDelay) {
      U.run(after);
    }
    if (shareReadyAnimator != null) {
      shareReadyAnimator.cancel();
    }
    if (replyReadyAnimator != null) {
      replyReadyAnimator.cancel();
    }

    final boolean[] status = needDelay && after != null ? new boolean[1] : null;
    final float fromTranslation = translation;
    final float fromReplyReadyFactor = replyReadyFactor;
    final float fromShareReadyFactor = shareReadyFactor;
    flags |= FLAG_IGNORE_SWIPE;
    FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        if (needDelay && fraction >= .85f && after != null && !status[0]) {
          status[0] = true;
          U.run(after);
        }
        setReadyFactor(false, fromReplyReadyFactor * (1f - factor), false, true);
        setReadyFactor(true, fromShareReadyFactor * (1f - factor), false, true);
        translate(fromTranslation * (1f - factor), false);
        if (view instanceof MessageViewGroup) {
          ((MessageViewGroup) view).setSwipeTranslation(translation);
        }
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        flags &= ~FLAG_IGNORE_SWIPE;
        /*if (needDelay) {
          U.run(after);
        }*/
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    animator.animateTo(1f);
  }

  private static final float BUBBLE_MOVE_MAX = 64f;
  public static final float BUBBLE_MOVE_THRESHOLD = 42f;

  private static final int ANIMATOR_READY_SHARE = -4;
  private static final int ANIMATOR_READY_REPLY = -5;

  private void vibrate () {
    UI.hapticVibrate(findCurrentView(), true);
  }

  private float replyReadyFactor, shareReadyFactor;
  private FactorAnimator replyReadyAnimator, shareReadyAnimator;

  private void setReadyFactor (boolean isShare, float factor, boolean animated, boolean invalidate) {
    FactorAnimator readyAnimator = isShare ? shareReadyAnimator : replyReadyAnimator;
    float readyFactor = isShare ? shareReadyFactor : replyReadyFactor;
    if (animated) {
      if (readyAnimator == null) {
        if (readyFactor == factor) {
          return;
        }
        readyAnimator = new FactorAnimator(isShare ? ANIMATOR_READY_SHARE : ANIMATOR_READY_REPLY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 110l, readyFactor);
        if (isShare) {
          shareReadyAnimator = readyAnimator;
        } else {
          replyReadyAnimator = readyAnimator;
        }
      }
      readyAnimator.animateTo(factor);
    } else {
      if (readyAnimator != null) {
        readyAnimator.forceFactor(factor);
      }
      if (readyFactor != factor) {
        if (isShare) {
          this.shareReadyFactor = factor;
        } else {
          this.replyReadyFactor = factor;
        }
        if (invalidate) {
          invalidate();
        }
      }
    }
  }

  public void translate (float dx, boolean bySwipe) {
    if (bySwipe && ((flags & FLAG_IGNORE_SWIPE) != 0)) {
      return;
    }

    boolean useBubbles = useBubbles();

    if (useBubbles) {
      int bound = Screen.dp(BUBBLE_MOVE_MAX);
      dx = Math.max(-bound, Math.min(bound, dx));
    }

    if (translation == dx) {
      return;
    }

    if (dx == 0f || useBubbles) {
      moveFactor = 0f;
    } else {
      moveFactor = Math.min(1f, Math.abs(dx) / (float) xQuickWidth);
    }

    translation = dx;

    if (useBubbles) {
      if (dx >= 0) {
        flags &= ~FLAG_READY_REPLY;
        setReadyFactor(false, 0f, false, false);
      }
      if (dx <= 0) {
        flags &= ~FLAG_READY_SHARE;
        setReadyFactor(true, 0f, false, false);
      }
      int threshold = Screen.dp(BUBBLE_MOVE_THRESHOLD);
      if (dx >= threshold && (flags & FLAG_READY_SHARE) == 0) {
        flags |= FLAG_READY_SHARE;
        vibrate();
        setReadyFactor(true, 1f, true, true);
      } else if (dx <= -threshold && (flags & FLAG_READY_REPLY) == 0) {
        flags |= FLAG_READY_REPLY;
        vibrate();
        setReadyFactor(false, 1f, true, true);
      }
    }

    invalidate(true);
  }

  private void drawTranslate (View view, Canvas c) {
    float x;
    int quickColor;
    if (translation == 0f) {
      int alpha = (int) (255f * Math.abs(dismissFactor));
      x = Math.signum(dismissFactor) * view.getMeasuredWidth();
      quickColor = ColorUtils.color(alpha, Theme.chatQuickActionColor());
      mQuickText.setAlpha(alpha);
    } else {
      x = this.translation;
      quickColor = Theme.chatQuickActionColor();
      mQuickText.setAlpha(255);
    }

    if (useBubbles()) {
      if (translation == 0f) {
        return;
      }
      int threshold = Screen.dp(BUBBLE_MOVE_THRESHOLD);
      float scaleFactor = (translation > 0f ? shareReadyFactor : replyReadyFactor);
      float alpha = MathUtils.clamp(Math.abs(translation) / (float) threshold) * scaleFactor;
      float scale = (.6f + .4f * scaleFactor);
      if (alpha == 0f) {
        return;
      }
      int cy = topContentEdge + (bottomContentEdge - topContentEdge) / 2;
      float cx = translation > 0f ? translation / 2 : view.getMeasuredWidth() + translation / 2;
      float darkFactor = Theme.getDarkFactor() * (1f - manager.controller().wallpaper().getBackgroundTransparency());
      float radius = Screen.dp(16f) * scale;
      if (darkFactor > 0f) {
        c.drawCircle(cx, cy, radius, Paints.getProgressPaint(ColorUtils.alphaColor(alpha * darkFactor, Theme.headerColor()), Screen.dp(1f)));
      }
      c.drawCircle(cx, cy, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, getBubbleButtonBackgroundColor())));
      Drawable icon = Lang.rtl() != (translation > 0) ? iQuickShare : iQuickReply;
      Paint paint = Paints.getInlineBubbleIconPaint(ColorUtils.alphaColor(alpha, getBubbleButtonTextColor()));
      c.save();
      c.scale((Lang.rtl() ? -.8f : .8f) * scale, .8f * scale, cx, cy);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, paint);
      c.restore();
      return;
    }

    /*if (false) {
      final int height = bottomContentEdge - topContentEdge;

      final int leftContentEdge = getActualLeftContentEdge();
      final int rightContentEdge = getActualRightContentEdge();

      final int width = rightContentEdge - leftContentEdge;
      final int minSide = Math.min(width, height);

      final float totalWidth = Math.min(Math.abs(x), width);

      final int cy = useCircleBubble() ? bottomContentEdge - width / 2 : topContentEdge + height / 2;

      if (x > 0) {
        c.save();
        c.clipRect(leftContentEdge, topContentEdge, leftContentEdge + totalWidth, bottomContentEdge);
        if (useBubble()) {
          drawBubble(c, Paints.fillingPaint(quickColor), false);
        } else {
          c.drawRect(leftContentEdge, topContentEdge, rightContentEdge, bottomContentEdge, Paints.fillingPaint(quickColor));
        }

        final float iconX = leftContentEdge + totalWidth - xQuickPadding - iQuickShare.getWidth();
        final int check = Screen.dp(12f) + iQuickShare.getHeight();
        if (check > minSide) {
          c.save();
          float scale = (float) minSide / (float) check;
          c.scale(scale, scale, iconX, cy);
        }
        c.drawBitmap(iQuickShare, iconX, cy - (int) ((float) iQuickShare.getHeight() * .5f), mQuickText);

        if (width - xQuickPadding - xQuickTextPadding - xQuickPadding - iQuickShare.getWidth() - xQuickShareWidth > 0) {
          float textX = iconX - xQuickTextPadding - xQuickShareWidth;
          c.drawText(shareText, textX, cy + xQuickTextOffset, mQuickText);
        }

        if (check > minSide) {
          c.restore();
        }

        c.restore();
      } else {
        c.save();
        c.clipRect(rightContentEdge - totalWidth, topContentEdge, rightContentEdge, bottomContentEdge);
        if (useBubble()) {
          drawBubble(c, Paints.fillingPaint(quickColor), false);
        } else {
          c.drawRect(leftContentEdge, topContentEdge, rightContentEdge, bottomContentEdge, Paints.fillingPaint(quickColor));
        }

        float iconX = rightContentEdge - totalWidth + xQuickPadding;

        int check = Screen.dp(12f) + iQuickReply.getHeight();
        if (check > minSide) {
          c.save();
          float scale = (float) minSide / (float) check;
          c.scale(scale, scale, iconX, cy);
        }

        c.drawBitmap(iQuickReply, iconX, cy - (int) ((float) iQuickReply.getHeight() * .5f) + Screen.dp(.5f), mQuickText);

        if (width - xQuickPadding - xQuickTextPadding - xQuickPadding - iQuickReply.getWidth() - xQuickReplyWidth > 0) {
          float textX = iconX + iQuickReply.getWidth() + xQuickTextPadding;
          c.drawText(replyText, textX, cy + xQuickTextOffset, mQuickText);
        }

        if (check > minSide) {
          c.restore();
        }

        c.restore();
      }


      return;
    }*/

    int startY, endY;
    if (useBubbles()) {
      startY = topContentEdge;
      endY = bottomContentEdge;
    } else {
      startY = getHeaderPadding() - xHeaderPadding;
      endY = findBottomEdge();
    }
    final int height = endY - startY;
    int cy = startY + (int) ((float) height * .5f);

    boolean rtl = Lang.rtl();
    Drawable icon;
    String text;
    float textWidth;
    if (rtl != (x > 0)) {
      icon = iQuickShare;
      text = shareText;
      textWidth = xQuickShareWidth;
    } else {
      icon = iQuickReply;
      text = replyText;
      textWidth = xQuickReplyWidth;
    }

    if (x > 0) {
      c.drawRect(0, startY, x, endY, Paints.fillingPaint(quickColor));

      float iconX = x - xQuickPadding - icon.getMinimumWidth();

      final int check = Screen.dp(12f) + icon.getMinimumHeight();
      if (check > height) {
        c.save();
        float scale = (float) height / (float) check;
        c.scale(scale, scale, iconX, cy);
      }

      Paint iconPaint = Paints.getInlineBubbleIconPaint(ColorUtils.alphaColor((float) mQuickText.getAlpha() / 255f, Theme.getColor(R.id.theme_color_messageSwipeContent)));
      if (rtl) {
        c.save();
        c.scale(-1f, 1f, iconX + icon.getMinimumWidth() / 2, cy);
      }
      Drawables.draw(c, icon, iconX, cy - (int) ((float) icon.getMinimumHeight() * .5f), iconPaint);
      if (rtl) {
        c.restore();
      }

      float textX = iconX - xQuickTextPadding - textWidth;
      c.drawText(text, textX, cy + xQuickTextOffset, mQuickText);

      if (check > height) {
        c.restore();
      }
    } else {
      int endX = view.getMeasuredWidth();
      c.drawRect(endX + x, startY, endX, endY, Paints.fillingPaint(quickColor));

      float iconX = endX + x + xQuickPadding;

      int check = Screen.dp(12f) + icon.getMinimumHeight();
      if (check > height) {
        c.save();
        float scale = (float) height / (float) check;
        c.scale(scale, scale, iconX, cy);
      }

      Paint iconPaint = Paints.getInlineBubbleIconPaint(ColorUtils.alphaColor((float) mQuickText.getAlpha() / 255f, Theme.getColor(R.id.theme_color_messageSwipeContent)));
      if (rtl) {
        c.save();
        c.scale(-1f, 1f, iconX + icon.getMinimumWidth() / 2, cy);
      }
      Drawables.draw(c, icon, iconX, cy - (int) ((float) icon.getMinimumHeight() * .5f) + Screen.dp(.5f), iconPaint);
      if (rtl) {
        c.restore();
      }

      int textX = (int) (endX + x + xQuickPadding + icon.getMinimumWidth() + textWidth);
      c.drawText(text, textX, cy + xQuickTextOffset, mQuickText);

      if (check > height) {
        c.restore();
      }
    }
  }

  public void setDismiss (float dismiss) {
    if (this.dismissFactor != dismiss) {
      this.dismissFactor = dismiss;
      invalidateParentOrSelf(true);
    }
  }

  private static final int ANIMATOR_DISMISS = -2;
  private FactorAnimator dismissAnimator;

  private void animateDismiss (float startFactor, float toFactor) {
    if (dismissAnimator != null) {
      dismissAnimator.forceFactor(startFactor);
    }
    setDismiss(startFactor);
    if (dismissAnimator == null) {
      if (dismissFactor == toFactor) {
        return;
      }
      dismissAnimator = new FactorAnimator(ANIMATOR_DISMISS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, this.dismissFactor);
    } else if (!dismissAnimator.isAnimating() && dismissFactor == toFactor) {
      return;
    }
    dismissAnimator.animateTo(toFactor);
  }

  public float getDismiss () {
    return dismissFactor;
  }

  public int currentActualHeight () {
    return height - getHeaderPadding() + (useBubbles() ? 0 : xHeaderPadding);
  }

  public void completeTranslation () {
    float startDismiss = Math.signum(translation);
    translation = 0f;
    moveFactor = 0f;

    animateDismiss(startDismiss, 0f);
  }

  private static class SelectionInfo {
    int startY;
    float pivotX, pivotY;
    float diffX, diffY;
    float expectedRadius;

    public SelectionInfo (int startY, float pivotX, float pivotY, float diffX, float diffY, float radius) {
      this.startY = startY;
      this.pivotX = pivotX;
      this.pivotY = pivotY;
      this.diffX = diffX;
      this.diffY = diffY;
      this.expectedRadius = radius;
    }
  }

  // Chat events

  private int eventDate;
  private boolean hideEventDate;
  private long eventMessageId;

  protected final TGMessage setIsEventLog (TdApiExt.MessageChatEvent event, long messageId) {
    flags |= FLAG_EVENT_LOG;
    this.eventDate = event.event.date;
    this.hideEventDate = event.hideDate;
    this.time = genTime();
    setDate(genDate());
    return this;
  }

  private boolean needHideEventDate () {
    return hideEventDate || (msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR && ((TdApiExt.MessageChatEvent) msg.content).hideDate);
  }

  public final boolean isEventLog () {
    return (flags & FLAG_EVENT_LOG) != 0 || msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR;
  }

  private boolean isThreadHeader () {
    return BitwiseUtils.getFlag(flags, MESSAGE_FLAG_IS_THREAD_HEADER);
  }

  protected String footerTitle;
  protected TextWrapper footerText;

  protected boolean hasFooter () {
    return footerTitle != null && footerText != null;
  }

  public void setFooter (String title, String text, TdApi.TextEntity[] entities) {
    this.footerTitle = title;

    TextWrapper wrapper = new TextWrapper(text, getSmallerTextStyleProvider(), getTextColorSet(), TextEntity.valueOf(tdlib, text, entities, openParameters())).setClickCallback(clickCallback());
    if (useBubbles()) {
      wrapper.addTextFlags(Text.FLAG_ADJUST_TO_CURRENT_WIDTH);
    }
    if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
      wrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
    }
    wrapper.setViewProvider(currentViews);

    this.footerText = wrapper;
  }

  protected final int getFooterTop () {
    return pContentY + getContentHeight() + getFooterPaddingTop();
  }

  protected final int getFooterHeight () {
    return Screen.dp(22f) + footerText.getHeight() + Screen.dp(2f);
  }

  protected final int getCommentButtonHeight () {
    return Screen.dp(40f);
  }

  protected final int getBubbleReduceHeight () {
    return Math.round(getCommentButtonHeight() * hasCommentButton.getFloatValue());
  }

  protected final int getFooterWidth () {
    return Math.max(trimmedFooterTitleWidth, footerText.getWidth()) + Screen.dp(10f);
  }

  protected int getFooterPaddingTop () {
    return Screen.dp(4f);
  }

  protected int getFooterPaddingBottom () {
    return Screen.dp(2f);
  }

  private String trimmedFooterTitle;
  private int trimmedFooterTitleWidth;

  private void buildFooter () {
    if (!hasFooter()) {
      return;
    }

    int paddingLeft = Screen.dp(10f);
    int maxWidth = allowMessageHorizontalExtend() ? pRealContentMaxWidth : getContentWidth();
    maxWidth -= paddingLeft;
    if (useBubbles()) {
      maxWidth -= xBubblePadding + xBubblePaddingSmall;
    } else {
      maxWidth -= Screen.dp(8f);
    }

    footerText.prepare(maxWidth);

    TextPaint textPaint = getSmallerTextStyleProvider().getBoldPaint();
    trimmedFooterTitle = TextUtils.ellipsize(footerTitle, textPaint, maxWidth, TextUtils.TruncateAt.END).toString();
    trimmedFooterTitleWidth = (int) U.measureText(footerTitle, textPaint);
  }

  // Color Sets

  private TextColorSet pick (TextColorSets.Regular regular, TextColorSets.BubbleOut bubbleOut, TextColorSets.BubbleIn bubbleIn) {
    return useBubbles() ? (isOutgoingBubble() ? bubbleOut : bubbleIn) : regular;
  }

  public final TextColorSet getDecentColorSet () {
    return pick(TextColorSets.Regular.LIGHT, TextColorSets.BubbleOut.LIGHT, TextColorSets.BubbleIn.LIGHT);
  }

  public final TextColorSet getLinkColorSet () {
    return pick(TextColorSets.Regular.LINK, TextColorSets.BubbleOut.LINK, TextColorSets.BubbleIn.LINK);
  }

  public final TextColorSet getTextColorSet () {
    return pick(TextColorSets.Regular.NORMAL, TextColorSets.BubbleOut.NORMAL, TextColorSets.BubbleIn.NORMAL);
  }

  public final TextColorSet getChatAuthorColorSet () {
    return pick(TextColorSets.Regular.MESSAGE_AUTHOR, TextColorSets.BubbleOut.MESSAGE_AUTHOR, TextColorSets.BubbleIn.MESSAGE_AUTHOR);
  }

  public final TextColorSet getChatAuthorPsaColorSet () {
    return pick(TextColorSets.Regular.MESSAGE_AUTHOR_PSA, TextColorSets.BubbleOut.MESSAGE_AUTHOR_PSA, TextColorSets.BubbleIn.MESSAGE_AUTHOR_PSA);
  }

  // Colors

  public final @ColorInt int getContentBackgroundColor () {
    if (useBubbles()) {
      return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_background : R.id.theme_color_bubbleIn_background);
    } else {
      return ColorUtils.compositeColor(Theme.getColor(R.id.theme_color_chatBackground), getSelectionColor(selectionFactor));
    }
  }

  public final @ThemeColorId int getDecentColorId (@ThemeColorId int defaultColorId) {
    return useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_time : R.id.theme_color_bubbleIn_time) : defaultColorId;
  }

  public final @ThemeColorId int getProgressColorId () {
    return useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_progress : R.id.theme_color_bubbleIn_progress) : R.id.theme_color_progress;
  }

  public final int getProgressColor () {
    return Theme.getColor(getProgressColorId());
  }

  public final @ThemeColorId int getDecentColorId () {
    return getDecentColorId(R.id.theme_color_textLight);
  }

  public final @ThemeColorId int getSeparatorColorId () {
    return useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_separator : R.id.theme_color_bubbleIn_separator) : R.id.theme_color_separator;
  }

  public final @ThemeColorId int getPressColorId () {
    return useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_pressed : R.id.theme_color_bubbleIn_pressed) : R.id.theme_color_messageSelection;
  }

  public final @ThemeColorId int getDecentIconColorId () {
    return getDecentColorId(R.id.theme_color_iconLight);
  }

  public final @ColorInt int getDecentColor () {
    return Theme.getColor(getDecentColorId());
  }

  public final @ColorInt int getSeparatorColor () {
    return Theme.getColor(getSeparatorColorId());
  }

  public final @ColorInt int getDecentIconColor () {
    return Theme.getColor(getDecentIconColorId());
  }

  public final Paint getDecentIconPaint () {
    return useBubbles() ? (isOutgoingBubble() ? Paints.getBubbleOutTimePaint() : Paints.getBubbleInTimePaint()) : Paints.getIconLightPorterDuffPaint();
  }

  public final int getTextColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_text : R.id.theme_color_bubbleIn_text) : R.id.theme_color_text);
  }

  public final int getOutlineColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_outline : R.id.theme_color_bubbleIn_outline) : R.id.theme_color_separator);
  }

  public final int getTextLinkColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_textLink : R.id.theme_color_bubbleIn_textLink) : R.id.theme_color_textLink);
  }

  public final int getTextLinkHighlightColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? R.id.theme_color_bubbleOut_textLinkPressHighlight : R.id.theme_color_bubbleIn_textLinkPressHighlight) : R.id.theme_color_textLinkPressHighlight);
  }

  protected final int getTextTopOffset () {
    return (!needHeader() || !needName()) && (!useForward() || ((flags & FLAG_SELF_CHAT) != 0 && !isOutgoing())) && replyData == null ? 0 : -Screen.dp(useBubbles() ? 4f : 2f);
  }

  protected final int getVerticalLineColor () {
    return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatVerticalLine : R.id.theme_color_messageVerticalLine);
  }

  protected final int getVerticalLineContentColor () {
    return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatNeutralFillingContent : R.id.theme_color_messageNeutralFillingContent);
  }

  protected final int getCorrectLineColor (boolean isPersonal) {
    return Theme.getColor(
            isPersonal ?
            isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatCorrectChosenFilling : R.id.theme_color_messageCorrectChosenFilling :
            isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatCorrectFilling : R.id.theme_color_messageCorrectFilling
    );
  }

  protected final int getCorrectLineContentColor (boolean isPersonal) {
    return Theme.getColor(
            isPersonal ?
            isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatCorrectChosenFillingContent : R.id.theme_color_messageCorrectChosenFillingContent :
            isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatCorrectFillingContent : R.id.theme_color_messageCorrectFillingContent
    );
  }

  protected final int getNegativeLineColor () {
    return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatNegativeFilling : R.id.theme_color_messageNegativeLine);
  }

  protected final int getNegativeLineContentColor () {
    return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_chatNegativeFillingContent : R.id.theme_color_messageNegativeLineContent);
  }

  protected final int getChatAuthorColor () {
    return Theme.getColor(getChatAuthorColorId());
  }

  protected final int getChatAuthorColorId () {
    return isOutgoingBubble() ? R.id.theme_color_bubbleOut_messageAuthor : R.id.theme_color_messageAuthor;
  }

  protected final int getChatAuthorPsaColor () {
    return Theme.getColor(isOutgoingBubble() ? R.id.theme_color_bubbleOut_messageAuthorPsa : R.id.theme_color_messageAuthorPsa);
  }

  private void drawCommentButton (MessageView view, Canvas c, int startX, int endX, int y, float alpha) {
    int cy = y + getCommentButtonHeight() / 2;
    int iconColorId = getChatAuthorColorId();
    Drawable drawable = view.getSparseDrawable(R.drawable.templarian_outline_comment_22, iconColorId);
    Drawables.draw(c, drawable, startX + Screen.dp(12f), cy - drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(iconColorId, alpha));

    // TODO draw text, avatars, ripple effect

    DrawAlgorithms.drawDirection(c, endX - Screen.dp(12f), cy, ColorUtils.alphaColor(alpha, Theme.getColor(iconColorId)), Gravity.RIGHT);
  }

  private void drawFooter (Canvas c) {
    int contentX, contentY = getFooterTop();
    if (useBubbles()) {
      // int bubblePadding = getBubbleContentPadding();
      contentX = getActualLeftContentEdge() + xBubblePadding + xBubblePaddingSmall;
    } else {
      contentX = pContentX;
    }

    RectF rectF = Paints.getRectF();
    rectF.set(contentX, contentY, contentX + Screen.dp(3f), contentY + getFooterHeight());
    c.drawRoundRect(rectF, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(getVerticalLineColor()));

    contentX += Screen.dp(10f);
    // int textY = contentY; // + Screen.dp(14f); //  + getFooterFontSizeOffset() / 2;
    TextStyleProvider provider = getSmallerTextStyleProvider();
    TextPaint paint = provider.getBoldPaint();
    paint.setColor(Theme.getColor(R.id.theme_color_textNeutral));
    c.drawText(trimmedFooterTitle != null ? trimmedFooterTitle : footerTitle, contentX, contentY + Screen.dp(15f), paint);

    footerText.draw(c, contentX, contentY + Screen.dp(22f), null, 1f);
  }

  // Locale change

  public final boolean updateLocale () {
    boolean updated = false;
    String time = genTime();
    if (this.time == null || !this.time.equals(time)) {
      this.time = time;
      updated = true;
    }
    String fTime = genForwardTime();
    if (this.fTime == null || !this.fTime.equals(fTime)) {
      this.fTime = fTime;
      updated = true;
    }
    if ((flags & FLAG_SHOW_BADGE) != 0) {
      String newBadge = getBadgeText();
      if (uBadge == null || !uBadge.text.equals(newBadge)) {
        uBadge = new Letters(newBadge);
        updated = true;
      }
    }
    if ((flags & FLAG_SHOW_DATE) != 0) {
      String date = genDate();
      if (this.date == null || !this.date.equals(date)) {
        setDate(date);
        updated = true;
      }
    }
    updateInteractionInfo(true);
    if (onLocaleChange()) {
      updated = true;
    }
    if (updated) {
      buildHeader();
      return true;
    }
    return false;
  }

  protected boolean onLocaleChange () {
    return false;
  }

  @Override
  public int compareTo (TGMessage o) {
    return Long.compare(o.getId(), getId());
  }

  public TooltipOverlayView.TooltipInfo showContentHint (View view, TooltipOverlayView.LocationProvider locationProvider, @StringRes int stringRes) {
    return showContentHint(view, locationProvider, new TdApi.FormattedText(Lang.getString(stringRes), null));
  }

  public TooltipOverlayView.TooltipInfo showContentHint (View view, TooltipOverlayView.LocationProvider locationProvider, TdApi.FormattedText text) {
    return buildContentHint(view, locationProvider).show(tdlib, text);
  }

  public TooltipOverlayView.TooltipBuilder buildContentHint (View view, TooltipOverlayView.LocationProvider locationProvider) {
    return context().tooltipManager().builder(view, currentViews)
      .locate((v, outRect) -> {
        locationProvider.getTargetBounds(v, outRect);
        outRect.offset(getContentX(), getContentY());
      })
      .chatTextSize(-2f)
      .click(clickCallback())
      .controller(controller())
      .source(openParameters());
  }

  private Text.ClickCallback clickCallback;

  @Nullable
  protected TdApi.WebPage findLinkPreview (String link) {
    return null;
  }

  protected boolean hasInstantView (String link) {
    return false;
  }

  protected final Text.ClickCallback clickCallback () {
    if (clickCallback != null)
      return clickCallback;
    return clickCallback = new Text.ClickCallback() {
      @Override
      public boolean onCommandClick (View view, Text text, TextPart part, String command, boolean isLongPress) {
        if (isLongPress) {
          messagesController().onCommandLongPressed(TGMessage.this, command);
        } else {
          TdApi.Message m = getMessage();
          TdApi.User user;
          if (m.forwardInfo != null) {
            user = m.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginUser.CONSTRUCTOR ? ((TGSourceUser) getForwardInfo()).getUser() : null;
          } else {
            user = sender.isUser() ? tdlib.cache().user(sender.getUserId()) : null;
          }
          messagesController().sendCommand(command, user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR ? user.username : null);
        }
        return true;
      }

      @Override
      public TdApi.WebPage findWebPage (String link) {
        return findLinkPreview(link);
      }

      @Override
      public boolean forceInstantView (String link) {
        return hasInstantView(link);
      }
    };
  }

  @Nullable
  protected final TooltipOverlayView.ColorProvider newMessageColorProvider () {
    return useBubbles() && !useBubble() ? null : new TooltipOverlayView.ColorProvider() {
      @Override
      public int tooltipColor () {
        return getContentReplaceColor();
      }

      @Override
      public int defaultTextColor () {
        return getTextColor();
      }

      @Override
      public int clickableTextColor (boolean isPressed) {
        return getTextLinkColor();
      }

      @Override
      public int backgroundColor (boolean isPressed) {
        return isPressed ? getTextLinkHighlightColor() : 0;
      }

      @Override
      public int outlineColor (boolean isPressed) {
        return getOutlineColor();
      }
    };
  }

  // Paints

  protected static Paint mQuickText;

  protected static TextPaint mHotPaint () {
    return Paints.getRegularTextPaint(12f);
  }

  private TextPaint getBadgePaint (boolean needFakeBold) {
    return Paints.getMediumTextPaint(14f, needFakeBold);
  }

  protected static TextPaint mTimeBubble () {
    return Paints.getRegularTextPaint(11f);
  }

  protected static TextPaint mTime (boolean willDraw) {
    TextPaint paint = Paints.getRegularTextPaint(12f);
    if (willDraw)
      paint.setColor(Theme.getColor(R.id.theme_color_textLight));;
    return paint;
  }

  private static void initPaints () {
    if (mQuickText == null) {
      mQuickText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      mQuickText.setColor(Theme.chatQuickActionTextColor());
      ThemeManager.addThemeListener(mQuickText, R.id.theme_color_messageSwipeContent);
      mQuickText.setTypeface(Fonts.getRobotoRegular());
      mQuickText.setTextSize(Screen.dp(16f));
    }
  }

  private static TextStyleProvider styleProvider, simpleStyleProvider, biggerStyleProvider, smallerStyleProvider, nameProvider, timeProvider;

  public static TextStyleProvider simpleTextStyleProvider () {
    if (simpleStyleProvider == null) {
      simpleStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(Settings.CHAT_FONT_SIZE_DEFAULT);
    }
    return simpleStyleProvider;
  }

  public static TextStyleProvider getNameStyleProvider () {
    if (nameProvider == null) {
      nameProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f);
    }
    return nameProvider;
  }

  public static TextStyleProvider getTextStyleProvider () {
    if (styleProvider == null) {
      styleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(Settings.instance().getChatFontSize()).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(styleProvider);
    }
    return styleProvider;
  }

  public static TextStyleProvider getSmallerTextStyleProvider () {
    if (smallerStyleProvider == null) {
      smallerStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSizeDiff(-1f).setTextSize(Settings.instance().getChatFontSize()).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(smallerStyleProvider);
    }
    return smallerStyleProvider;
  }

  public static TextStyleProvider getBiggerTextStyleProvider () {
    if (biggerStyleProvider == null) {
      biggerStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSizeDiff(1f).setTextSize(Settings.instance().getChatFontSize()).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(biggerStyleProvider);
    }
    return biggerStyleProvider;
  }

  public static TextStyleProvider getTimeTextStyleProvider () {
    if (timeProvider == null) {
      timeProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(11f);
    }
    return timeProvider;
  }

  // Sizes

  protected static int xTextLine, xTextSize;

  protected static int xBubbleLeft1, xBubbleLeft2, xBubbleViewPadding, xBubbleViewPaddingSmall, xBubbleNameTop;
  protected static int xBubblePadding, xBubblePaddingSmall, xBubbleAvatarRadius, xBubbleAvatarLeft;

  protected static int xContentLeft, xContentTop, xContentOffset;
  protected static int xfContentLeft;
  protected static int xPaddingTop, xPaddingBottom, xPaddingRight, xTextPadding, xHeaderPadding;
  protected static int xDatePadding, xDateTop;
  protected static int xBadgeHeight, xBadgeTop, xBadgePadding;

  protected static int xAvatarRadius;
  protected static int xAvatarLeft, xAvatarCenterX;
  protected static int xLettersTop;

  protected static int xNameTop;
  protected static int xTimeTop, xTimePadding;

  protected static int xViewsPaddingRight, xViewsOffset, xViewsPaddingLeft;

  protected static int xQuickWidth, xQuickPadding, xQuickTextPadding, xQuickTextOffset, xQuickShareWidth, xQuickReplyWidth;

  // protected static int xCaptionTouchOffset, xCaptionAddition;

  public static void reset () {
    initialized = false;
    /*initSizes();
    if (mTimeBubble != null)
      mTimeBubble.setTextSize(Screen.dp(11f));
    if (mTime != null)
      mTime.setTextSize(Screen.dp(12f));
    if (mTextDeprecated != null)
      mTextDeprecated.setTextSize(Screen.dp(15f));
    if (mTextItalicDeprecated != null)
      mTextItalicDeprecated.setTextSize(Screen.dp(15f));
    if (mQuickText != null)
      mQuickText.setTextSize(Screen.dp(16f));
    if (mHotPaint != null)
      mHotPaint.setTextSize(Screen.dp(12f));
    initResources();
    initTexts();*/
  }

  private static final float BUBBLE_AVATAR_RADIUS = 18f;
  private static final float AVATAR_RADIUS = 20.5f;

  private static void initSizes () {
    xTextSize = Screen.dp(15f);
    xHeaderPadding = Screen.dp(4.5f);
    xDatePadding = Screen.dp(40f);
    xDateTop = Screen.dp(26f);

    xBubblePadding = Screen.dp(8f);
    xBubblePaddingSmall = Screen.dp(2f);
    xBubbleAvatarRadius = Screen.dp(BUBBLE_AVATAR_RADIUS);
    xBubbleLeft1 = Screen.dp(8f);
    xBubbleLeft2 = Screen.dp(9f);
    xBubbleAvatarLeft = Screen.dp(6f);

    xBubbleViewPadding = Screen.dp(3f);
    xBubbleViewPaddingSmall = Screen.dp(1.5f);
    xBubbleNameTop = Screen.dp(22f);

    xAvatarLeft = Screen.dp(9f);
    xAvatarRadius = Screen.dp(AVATAR_RADIUS);
    xAvatarCenterX = xAvatarLeft + xAvatarRadius;
    xLettersTop = Screen.dp(27f);

    xNameTop = Screen.dp(14f);
    xTimeTop = Screen.dp(14f);
    xContentTop = Screen.dp(25f);

    xTimePadding = Screen.dp(8f);

    xViewsOffset = Screen.dp(2.5f);
    xViewsPaddingRight = Screen.dp(3f);
    xViewsPaddingLeft = Screen.dp(5f);

    xTextLine = Screen.dp(20f);

    xContentLeft = xAvatarLeft + xAvatarRadius * 2 + Screen.dp(10f);
    xContentOffset = Screen.dp(12f);
    xPaddingTop = Screen.dp(7.5f);
    xPaddingBottom = Screen.dp(7.5f);
    xPaddingRight = Screen.dp(35f);
    xTextPadding = Screen.dp(10f);

    xfContentLeft = xContentLeft + Screen.dp(11f);

    xBadgeTop = Screen.dp(18f);
    // xBadgeIconTop = Screen.dp(11f);
    xBadgeHeight = Screen.dp(26f);
    xBadgePadding = Screen.dp(4f);

    xQuickWidth = Screen.dp(MessagesTouchHelperCallback.SWIPE_THRESHOLD_WIDTH);
    xQuickPadding = Screen.dp(19f);
    xQuickTextPadding = Screen.dp(22f);
    xQuickTextOffset = Screen.dp(5.5f);

    // xCaptionTouchOffset = Screen.dp(22f);
    // xCaptionOffset = Screen.dp(22f);
    // xCaptionAddition = Screen.dp(14f);
  }

  public static int getContentLeft () {
    return xContentLeft;
  }

  // Icons

  private static Drawable iQuickReply, iQuickShare, iBadge;
  private static String shareText, replyText;
  private static boolean initialized;

  private static void initResources () {
    Resources res = UI.getResources();
    iBadge = Drawables.get(res, R.drawable.baseline_keyboard_arrow_down_20);
    iQuickReply = Drawables.get(res, R.drawable.baseline_reply_24);
    iQuickShare = Drawables.get(res, R.drawable.baseline_forward_24);
    initBubbleResources();
  }

  private static void initTexts () {
    if (mQuickText != null) {
      shareText = Lang.getString(R.string.SwipeShare);
      replyText = Lang.getString(R.string.SwipeReply);
      xQuickReplyWidth = (int) U.measureText(replyText, mQuickText);
      xQuickShareWidth = (int) U.measureText(shareText, mQuickText);
    }
  }

  private static boolean isStaticText (int res) {
    switch (res) {
      case R.string.SwipeShare:
      case R.string.SwipeReply:
        return true;
    }
    return false;
  }

  public static void processLanguageEvent (@Lang.EventType int eventType, int arg1) {
    if (eventType == Lang.EVENT_PACK_CHANGED || (eventType == Lang.EVENT_STRING_CHANGED && (arg1 == 0 || isStaticText(arg1)))) {
      initTexts();
    }
  }

  private static void init () {
    try {
      initResources();
      initPaints();
      initSizes();
      initTexts();
      initialized = true;
    } catch (Throwable t) {
      Tracer.onLaunchError(t);
    }
  }

  protected final void addMessageFlags (int flags) {
    this.flags |= flags;
  }

  protected final boolean isErrorMessage () {
    return (flags & FLAG_ERROR) != 0;
  }

  // Other

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.Chat chat, @Nullable LongSparseArray<TdApi.ChatAdministrator> chatAdmins) {
    return valueOf(context, msg, chat, msg.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR && chatAdmins != null ? chatAdmins.get(((TdApi.MessageSenderUser) msg.senderId).userId) : null);
  }

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.Chat chat, @Nullable TdApi.ChatAdministrator admin) {
    TGMessage parsedMessage = valueOf(context, msg);
    if (chat != null) {
      parsedMessage.setChatData(chat);
    }
    if (admin != null) {
      parsedMessage.setAdministratorSign(admin);
    }
    parsedMessage.initSelectedMessages();
    return parsedMessage;
  }

  private static void appendRight (StringBuilder b, int res, boolean oldValue, boolean value, boolean needEqual) {
    if (oldValue != value) {
      b.append('\n');
      b.append(value ? '+' : '-');
      b.append(' ');
      b.append(Lang.getString(res));
    } else if (needEqual && value) {
      b.append('\n');
      b.append(Lang.getString(res));
      if (!value)
        b.append(" (-)");
    }
  }

  private static void appendRight (StringBuilder b, int res, int doubleRes, String oldValue, String newValue, boolean needEqual) {
    if (!StringUtils.equalsOrBothEmpty(oldValue, newValue)) {
      b.append('\n');
      if (StringUtils.isEmpty(oldValue)) {
        b.append("+ ").append(Lang.getString(res, newValue));
      } else if (StringUtils.isEmpty(newValue)) {
        b.append("- ").append(Lang.getString(res, oldValue));
      } else {
        b.append("+ ").append(Lang.getString(doubleRes, oldValue, newValue));
      }
    } else if (needEqual && !StringUtils.isEmpty(newValue)) {
      b.append(Lang.getString(res, newValue));
    }
  }

  private static TGMessage valueOf (MessagesManager context, TdApi.Message msg) {
    TdApi.MessageContent content = msg.content;
    final Tdlib tdlib = context.controller().tdlib();
    try {
      if (content == null) {
        return new TGMessageText(context, msg, new TdApi.FormattedText(Lang.getString(R.string.DeletedMessage), null));
      }
      if (!StringUtils.isEmpty(msg.restrictionReason) && Settings.instance().needRestrictContent()) {
        TGMessageText text = new TGMessageText(context, msg, new TdApi.FormattedText(msg.restrictionReason, null));
        text.addMessageFlags(FLAG_UNSUPPORTED);
        return text;
      }

      if (content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR) {
        TdApiExt.MessageChatEvent event = (TdApiExt.MessageChatEvent) content;
        switch (event.event.action.getConstructor()) {
          case TdApi.ChatEventMemberJoined.CONSTRUCTOR: {
            final long userId = Td.getSenderUserId(event.event.memberId);
            content = new TdApi.MessageChatAddMembers(new long[]{userId});
            break;
          }
          case TdApi.ChatEventMemberLeft.CONSTRUCTOR: {
            final long userId = Td.getSenderUserId(event.event.memberId);
            content = new TdApi.MessageChatDeleteMember(userId);
            break;
          }
          case TdApi.ChatEventMessageTtlChanged.CONSTRUCTOR:
            content = new TdApi.MessageChatSetTtl(((TdApi.ChatEventMessageTtlChanged) event.event.action).newMessageTtl);
            break;
          case TdApi.ChatEventVideoChatCreated.CONSTRUCTOR:
            content = new TdApi.MessageVideoChatStarted(((TdApi.ChatEventVideoChatCreated) event.event.action).groupCallId);
            break;
          case TdApi.ChatEventVideoChatEnded.CONSTRUCTOR:
            content = new TdApi.MessageVideoChatEnded(0); // ((TdApi.ChatEventVideoChatEnded) event.event.action).groupCallId
            break;
          case TdApi.ChatEventTitleChanged.CONSTRUCTOR: {
            TdApi.ChatEventTitleChanged e = (TdApi.ChatEventTitleChanged) event.event.action;
            content = new TdApi.MessageChatChangeTitle(e.newTitle);
            break;
          }
          case TdApi.ChatEventPhotoChanged.CONSTRUCTOR: {
            TdApi.ChatEventPhotoChanged e = (TdApi.ChatEventPhotoChanged) event.event.action;
            if (e.newPhoto != null || e.oldPhoto != null) {
              content = new TdApi.MessageChatChangePhoto(e.newPhoto != null ? e.newPhoto : e.oldPhoto);
            } else {
              content = new TdApi.MessageChatDeletePhoto();
            }
            break;
          }
        }
      }

      int unsupportedStringRes = R.string.UnsupportedMessage;

      switch (content.getConstructor()) {
        case TdApiExt.MessageChatEvent.CONSTRUCTOR:
          TdApiExt.MessageChatEvent event = (TdApiExt.MessageChatEvent) content;
          if (event.isFull) {
            switch (event.event.action.getConstructor()) {
              case TdApi.ChatEventUsernameChanged.CONSTRUCTOR: {
                TdApi.ChatEventUsernameChanged e = (TdApi.ChatEventUsernameChanged) event.event.action;

                TdApi.FormattedText text;
                if (StringUtils.isEmpty(e.newUsername)) {
                  text = new TdApi.FormattedText("", null);
                } else {
                  String link = TD.getLink(e.newUsername);
                  text = new TdApi.FormattedText(link, new TdApi.TextEntity[] {new TdApi.TextEntity(0, link.length(), new TdApi.TextEntityTypeUrl())});
                }

                TGMessageText parsedMessage = new TGMessageText(context, msg, text);

                if (!StringUtils.isEmpty(e.oldUsername)) {
                  String link = TD.getLink(e.oldUsername);
                  parsedMessage.setFooter(Lang.getString(R.string.EventLogPreviousLink), link, new TdApi.TextEntity[]{new TdApi.TextEntity(0, link.length(), new TdApi.TextEntityTypeUrl())});
                }

                return parsedMessage;
              }

              case TdApi.ChatEventDescriptionChanged.CONSTRUCTOR: {
                TdApi.ChatEventDescriptionChanged e = (TdApi.ChatEventDescriptionChanged) event.event.action;

                TdApi.FormattedText text;
                if (StringUtils.isEmpty(e.newDescription)) {
                  text = new TdApi.FormattedText("", null);
                } else {
                  text = new TdApi.FormattedText(e.newDescription, Text.findEntities(e.newDescription, Text.ENTITY_FLAGS_ALL_NO_COMMANDS));
                }

                TGMessageText parsedMessage = new TGMessageText(context, msg, text);

                if (!StringUtils.isEmpty(e.oldDescription)) {
                  parsedMessage.setFooter(Lang.getString(R.string.EventLogPreviousGroupDescription), e.oldDescription, null);
                }

                return parsedMessage;
              }

              case TdApi.ChatEventMemberInvited.CONSTRUCTOR: {
                TdApi.ChatEventMemberInvited e = (TdApi.ChatEventMemberInvited) event.event.action;
                TdApi.User user = tdlib.cache().user(e.userId);
                String userName = TD.getUserName(user);
                StringBuilder b = new StringBuilder(Lang.getString(R.string.group_user_added, "", userName).trim());
                int start = b.lastIndexOf(userName);
                ArrayList<TdApi.TextEntity> entities = new ArrayList<>(3);
                entities.add(new TdApi.TextEntity(0, start - 1, new TdApi.TextEntityTypeItalic()));
                entities.add(new TdApi.TextEntity(start, userName.length(), new TdApi.TextEntityTypeMentionName(e.userId)));
                if (user != null && !StringUtils.isEmpty(user.username)) {
                  b.append(" / ");
                  entities.add(new TdApi.TextEntity(b.length(), user.username.length() + 1, new TdApi.TextEntityTypeMention()));
                  b.append('@');
                  b.append(user.username);
                }

                TdApi.TextEntity[] array = new TdApi.TextEntity[entities.size()];
                entities.toArray(array);
                TdApi.FormattedText text = new TdApi.FormattedText(b.toString(), array);

                return new TGMessageText(context, msg, text);
              }

              case TdApi.ChatEventMessageDeleted.CONSTRUCTOR: {
                TdApi.ChatEventMessageDeleted e = (TdApi.ChatEventMessageDeleted) event.event.action;

                TGMessage m = valueOf(context, e.message);
                m.setIsEventLog(event, 0);

                return m;
              }

              case TdApi.ChatEventMessageEdited.CONSTRUCTOR: {
                TdApi.ChatEventMessageEdited e = (TdApi.ChatEventMessageEdited) event.event.action;


                TGMessage m = valueOf(context, TD.removeWebPage(e.newMessage));
                m.setIsEventLog(event, e.newMessage.id);

                int footerRes;
                TdApi.Message oldMessage = TD.removeWebPage(e.oldMessage);
                TdApi.FormattedText originalText = Td.textOrCaption(oldMessage.content);
                switch (oldMessage.content.getConstructor()) {
                  case TdApi.MessageText.CONSTRUCTOR:
                    footerRes = R.string.EventLogOriginalMessages;
                    break;
                  default:
                    footerRes = R.string.EventLogOriginalCaption;
                    break;
                }
                String text = Td.isEmpty(originalText) ? Lang.getString(R.string.EventLogOriginalCaptionEmpty) : originalText.text;
                m.setFooter(Lang.getString(footerRes), text, originalText != null ? originalText.entities : null);

                return m;
              }
              case TdApi.ChatEventMessagePinned.CONSTRUCTOR: {
                TdApi.ChatEventMessagePinned e = (TdApi.ChatEventMessagePinned) event.event.action;

                TGMessage m = valueOf(context, e.message);
                m.setIsEventLog(event, e.message.id);

                return m;
              }
              case TdApi.ChatEventPollStopped.CONSTRUCTOR: {
                TdApi.ChatEventPollStopped e = (TdApi.ChatEventPollStopped) event.event.action;

                TGMessage m = valueOf(context, e.message);
                m.setIsEventLog(event, e.message.id);

                return m;
              }
              case TdApi.ChatEventInviteLinkEdited.CONSTRUCTOR: {
                TdApi.ChatEventInviteLinkEdited e = (TdApi.ChatEventInviteLinkEdited) event.event.action;

                boolean changedLimit = e.oldInviteLink.memberLimit != e.newInviteLink.memberLimit;
                boolean changedExpires = e.oldInviteLink.expirationDate != e.newInviteLink.expirationDate;

                String link = StringUtils.urlWithoutProtocol(e.newInviteLink.inviteLink);
                String prevLimit = e.oldInviteLink.memberLimit != 0 ? Strings.buildCounter(e.oldInviteLink.memberLimit) : Lang.getString(R.string.EventLogEditedInviteLinkNoLimit);
                String newLimit = e.newInviteLink.memberLimit != 0 ? Strings.buildCounter(e.newInviteLink.memberLimit) : Lang.getString(R.string.EventLogEditedInviteLinkNoLimit);

                String text;
                if (changedLimit && changedExpires) {
                  String expires;
                  if (e.newInviteLink.expirationDate == 0) {
                    expires = Lang.getString(R.string.LinkExpiresNever);
                  } else if (DateUtils.isToday(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
                    expires = Lang.getString(R.string.LinkExpiresTomorrow, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  } else if (DateUtils.isTomorrow(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
                    expires = Lang.getString(R.string.LinkExpiresTomorrow, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  } else {
                    expires = Lang.getString(R.string.LinkExpiresFuture, Lang.getDate(e.newInviteLink.expirationDate, TimeUnit.SECONDS), Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  }
                  text = Lang.getString(R.string.EventLogEditedInviteLink, link, prevLimit, newLimit, expires);
                } else if (changedLimit) {
                  text = Lang.getString(R.string.EventLogEditedInviteLinkLimit, link, prevLimit, newLimit);
                } else {
                  if (e.newInviteLink.expirationDate == 0) {
                    text = Lang.getString(R.string.EventLogEditedInviteLinkExpireNever, link);
                  } else if (DateUtils.isToday(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
                    text = Lang.getString(R.string.EventLogEditedInviteLinkExpireToday, link, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  } else if (DateUtils.isTomorrow(e.newInviteLink.expirationDate, TimeUnit.SECONDS)) {
                    text = Lang.getString(R.string.EventLogEditedInviteLinkExpireTomorrow, link, Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  } else {
                    text = Lang.getString(R.string.EventLogEditedInviteLinkExpireFuture, link, Lang.getDate(e.newInviteLink.expirationDate, TimeUnit.SECONDS), Lang.time(e.newInviteLink.expirationDate, TimeUnit.SECONDS));
                  }
                }

                TdApi.FormattedText formattedText = new TdApi.FormattedText(text, Td.findEntities(text));
                TGMessageText m = new TGMessageText(context, msg, formattedText);
                m.setIsEventLog(event, 0);

                return m;
              }
              case TdApi.ChatEventPermissionsChanged.CONSTRUCTOR: {
                StringBuilder b = new StringBuilder(Lang.getString(R.string.EventLogPermissions));
                int length = b.length();

                b.append("\n");

                TdApi.ChatEventPermissionsChanged permissions = (TdApi.ChatEventPermissionsChanged) event.event.action;

                appendRight(b, R.string.EventLogPermissionSendMessages, permissions.oldPermissions.canSendMessages, permissions.newPermissions.canSendMessages, true);
                appendRight(b, R.string.EventLogPermissionSendMedia, permissions.oldPermissions.canSendMediaMessages, permissions.newPermissions.canSendMediaMessages, true);
                appendRight(b, R.string.EventLogPermissionSendStickers, permissions.oldPermissions.canSendOtherMessages, permissions.newPermissions.canSendOtherMessages, true);
                appendRight(b, R.string.EventLogPermissionSendPolls, permissions.oldPermissions.canSendPolls, permissions.newPermissions.canSendPolls, true);
                appendRight(b, R.string.EventLogPermissionSendEmbed, permissions.oldPermissions.canAddWebPagePreviews, permissions.newPermissions.canAddWebPagePreviews, true);
                appendRight(b, R.string.EventLogPermissionAddUsers, permissions.oldPermissions.canInviteUsers, permissions.newPermissions.canInviteUsers, true);
                appendRight(b, R.string.EventLogPermissionPinMessages, permissions.oldPermissions.canPinMessages, permissions.newPermissions.canPinMessages, true);
                appendRight(b, R.string.EventLogPermissionChangeInfo, permissions.oldPermissions.canChangeInfo, permissions.newPermissions.canChangeInfo, true);

                TdApi.FormattedText formattedText = new TdApi.FormattedText(b.toString().trim(), new TdApi.TextEntity[]{new TdApi.TextEntity(0, length, new TdApi.TextEntityTypeItalic())});
                TGMessageText m = new TGMessageText(context, msg, formattedText);
                m.setIsEventLog(event, 0);

                return m;
              }
              case TdApi.ChatEventMemberPromoted.CONSTRUCTOR:
              case TdApi.ChatEventMemberRestricted.CONSTRUCTOR: {
                final TdApi.MessageSender memberId;

                StringBuilder b = new StringBuilder();
                ArrayList<TdApi.TextEntity> entities = new ArrayList<>();
                final TdApi.ChatMemberStatus oldStatus, newStatus;
                final boolean isPromote, isTransferOwnership;
                boolean isAnonymous = false;

                final int stringRes;
                int restrictedUntil = 0;

                if (event.event.action.getConstructor() == TdApi.ChatEventMemberPromoted.CONSTRUCTOR) {
                  TdApi.ChatEventMemberPromoted e = (TdApi.ChatEventMemberPromoted) event.event.action;
                  memberId = new TdApi.MessageSenderUser(e.userId);

                  isPromote = true;

                  // TYPE_EDIT = 0, TYPE_ASSIGN = 1, TYPE_REMOVE = 2, TYPE_TRANSFER = 3
                  int type = 0;

                  if (e.oldStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
                    isTransferOwnership = true;
                    oldStatus = e.oldStatus;
                    newStatus = new TdApi.ChatMemberStatusCreator();
                    type = 3;
                  } else if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() != TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
                    isTransferOwnership = true;
                    oldStatus = e.oldStatus;
                    newStatus = new TdApi.ChatMemberStatusCreator();
                    type = 4;
                  } else {
                    isTransferOwnership = false;

                    if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
                      type = 5;
                      isAnonymous = ((TdApi.ChatMemberStatusCreator) e.oldStatus).isAnonymous != ((TdApi.ChatMemberStatusCreator) e.newStatus).isAnonymous;
                    }

                    switch (e.oldStatus.getConstructor()) {
                      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                        oldStatus = e.oldStatus;
                        break;
                      default:
                        if (!isAnonymous) {
                          type = 1;
                          oldStatus = new TdApi.ChatMemberStatusAdministrator();
                        } else {
                          oldStatus = e.oldStatus;
                        }
                        break;
                    }

                    switch (e.newStatus.getConstructor()) {
                      case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                        newStatus = e.newStatus;
                        break;
                      default:
                        if (!isAnonymous) {
                          type = 2;
                          newStatus = new TdApi.ChatMemberStatusAdministrator();
                        } else {
                          newStatus = e.newStatus;
                        }
                        break;
                    }
                  }

                  switch (type) {
                    case 1:
                      stringRes = R.string.EventLogPromotedNew;
                      break;
                    case 2:
                      stringRes = R.string.EventLogUnpromoted;
                      break;
                    case 3:
                      stringRes = R.string.EventLogTransferredOwnership;
                      break;
                    case 4:
                      stringRes = R.string.EventLogNoLongerCreator;
                      break;
                    default:
                      stringRes = R.string.EventLogPromoted;
                      break;
                  }

                } else {
                  TdApi.ChatEventMemberRestricted e = (TdApi.ChatEventMemberRestricted) event.event.action;

                  memberId = e.memberId;
                  isPromote = false;
                  isTransferOwnership = false;

                  if (msg.isChannelPost) {
                    oldStatus = null;
                    newStatus = null;
                    if (e.newStatus.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR) {
                      stringRes = R.string.EventLogChannelRestricted;
                    } else {
                      stringRes = R.string.EventLogChannelUnrestricted;
                    }
                  } else {
                    oldStatus = e.oldStatus;
                    newStatus = e.newStatus;
                    // STATUS_NORMAL = 0, STATUS_BANNED = 1, STATUS_RESTRICTED = 2
                    int prevState = 0, newState = 0;

                    switch (e.oldStatus.getConstructor()) {
                      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
                        prevState = 1;
                        break;
                      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
                        prevState = 2;
                        break;
                    }

                    switch (e.newStatus.getConstructor()) {
                      case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
                        newState = 1;
                        break;
                      case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
                        newState = 2;
                        break;
                    }

                    if (e.oldStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && e.newStatus.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
                      isAnonymous = ((TdApi.ChatMemberStatusCreator) e.oldStatus).isAnonymous != ((TdApi.ChatMemberStatusCreator) e.newStatus).isAnonymous;
                      stringRes = R.string.EventLogPromoted;
                    } else if (newState == 1 && prevState == 0) {
                      stringRes = R.string.EventLogGroupBanned;
                      restrictedUntil = ((TdApi.ChatMemberStatusBanned) newStatus).bannedUntilDate;
                    } else if (newState != 0) {
                      stringRes = prevState != 0 ? R.string.EventLogRestrictedUntil : R.string.EventLogRestrictedNew;
                      restrictedUntil = newStatus.getConstructor() == TdApi.ChatMemberStatusBanned.CONSTRUCTOR ? ((TdApi.ChatMemberStatusBanned) newStatus).bannedUntilDate : ((TdApi.ChatMemberStatusRestricted) newStatus).restrictedUntilDate;
                    } else {
                      stringRes = R.string.EventLogRestrictedDeleted;
                    }

                    /* else {
                      throw new IllegalArgumentException("server bug: " + event.event.action);
                    }*/
                  }
                }

                String userName = tdlib.senderName(memberId);
                String username = tdlib.senderUsername(memberId);

                b.append(Lang.getString(stringRes));

                int start = b.indexOf("%1$s");
                if (start != -1) {
                  entities.add(new TdApi.TextEntity(0, start - 1, new TdApi.TextEntityTypeItalic()));

                  b.replace(start, start + 4, "");
                  b.insert(start, userName);
                  if (memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
                    // TODO support for MessageSenderChat
                    entities.add(new TdApi.TextEntity(start, userName.length(), new TdApi.TextEntityTypeMentionName(((TdApi.MessageSenderUser) memberId).userId)));
                  }
                  start += userName.length();
                  if (!StringUtils.isEmpty(username)) {
                    b.insert(start, " / @");
                    start += 3;
                    b.insert(start + 1, username);
                    entities.add(new TdApi.TextEntity(start, username.length() + 1, new TdApi.TextEntityTypeMention()));
                    start += username.length() + 1;
                  }
                }

                int i = b.indexOf("%2$s");
                if (i != -1) {
                  int duration = restrictedUntil - event.event.date;
                  String str;

                  if (restrictedUntil == 0) {
                    str = Lang.getString(R.string.UserRestrictionsUntilForever);
                  } else if (Lang.preferTimeForDuration(duration)) {
                    str = Lang.getString(R.string.UntilX, Lang.getDate(restrictedUntil, TimeUnit.SECONDS));
                  } else {
                    str = Lang.getDuration(duration, 0, 0, false);
                  }

                  b.replace(i, i + 4, str);
                }

                if (start < b.length() - 1) {
                  entities.add(new TdApi.TextEntity(start, b.length() - start, new TdApi.TextEntityTypeItalic()));
                }

                b.append('\n');

                if (isTransferOwnership) {
                  // no need to show anything
                } else if (isAnonymous) {
                  appendRight(b, R.string.EventLogPromotedRemainAnonymous, ((TdApi.ChatMemberStatusCreator) oldStatus).isAnonymous, ((TdApi.ChatMemberStatusCreator) newStatus).isAnonymous, false);
                } else if (isPromote) {
                  final TdApi.ChatMemberStatusAdministrator oldAdmin = (TdApi.ChatMemberStatusAdministrator) oldStatus;
                  final TdApi.ChatMemberStatusAdministrator newAdmin = (TdApi.ChatMemberStatusAdministrator) newStatus;
                  appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedManageChannel : R.string.EventLogPromotedManageGroup, oldAdmin.rights.canManageChat, newAdmin.rights.canManageChat, false);
                  appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedChangeChannelInfo : R.string.EventLogPromotedChangeGroupInfo, oldAdmin.rights.canChangeInfo, newAdmin.rights.canChangeInfo, false);
                  if (msg.isChannelPost) {
                    appendRight(b, R.string.EventLogPromotedPostMessages, oldAdmin.rights.canChangeInfo, newAdmin.rights.canChangeInfo, false);
                    appendRight(b, R.string.EventLogPromotedEditMessages, oldAdmin.rights.canEditMessages, newAdmin.rights.canEditMessages, false);
                  }
                  appendRight(b, R.string.EventLogPromotedDeleteMessages, oldAdmin.rights.canDeleteMessages, newAdmin.rights.canDeleteMessages, false);
                  appendRight(b, R.string.EventLogPromotedBanUsers, oldAdmin.rights.canRestrictMembers, newAdmin.rights.canRestrictMembers, false);
                  appendRight(b, R.string.EventLogPromotedAddUsers, oldAdmin.rights.canInviteUsers, newAdmin.rights.canInviteUsers, false);
                  if (!msg.isChannelPost) {
                    appendRight(b, R.string.EventLogPromotedPinMessages, oldAdmin.rights.canPinMessages, newAdmin.rights.canPinMessages, false);
                  }
                  appendRight(b, msg.isChannelPost ? R.string.EventLogPromotedManageLiveStreams : R.string.EventLogPromotedManageVoiceChats, oldAdmin.rights.canManageVideoChats, newAdmin.rights.canManageVideoChats, false);
                  if (!msg.isChannelPost) {
                    appendRight(b, R.string.EventLogPromotedRemainAnonymous, oldAdmin.rights.isAnonymous, newAdmin.rights.isAnonymous, false);
                  }
                  appendRight(b, R.string.EventLogPromotedAddAdmins, oldAdmin.rights.canPromoteMembers, newAdmin.rights.canPromoteMembers, false);
                  appendRight(b, R.string.EventLogPromotedTitle, R.string.EventLogPromotedTitleChange, oldAdmin.customTitle, newAdmin.customTitle, false);
                } else if (oldStatus != null && newStatus != null) {
                  final boolean oldCanReadMessages = oldStatus.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR;
                  final boolean newCanReadMessages = newStatus.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR;

                  final TdApi.ChatMemberStatusRestricted oldBan = oldStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? (TdApi.ChatMemberStatusRestricted) oldStatus : null;
                  final TdApi.ChatMemberStatusRestricted newBan = newStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? (TdApi.ChatMemberStatusRestricted) newStatus : null;

                  if (memberId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
                    appendRight(b, R.string.EventLogRestrictedReadMessages, oldCanReadMessages, newCanReadMessages, false);
                  }
                  appendRight(b, R.string.EventLogRestrictedSendMessages, oldBan != null ? oldBan.permissions.canSendMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendMessages : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedSendMedia, oldBan != null ? oldBan.permissions.canSendMediaMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendMediaMessages : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedSendStickers, oldBan != null ? oldBan.permissions.canSendOtherMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendOtherMessages : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedSendPolls, oldBan != null ? oldBan.permissions.canSendOtherMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canSendOtherMessages : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedSendEmbed, oldBan != null ? oldBan.permissions.canAddWebPagePreviews : oldCanReadMessages, newBan != null ? newBan.permissions.canAddWebPagePreviews : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedAddUsers, oldBan != null ? oldBan.permissions.canInviteUsers : oldCanReadMessages, newBan != null ? newBan.permissions.canInviteUsers : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedPinMessages, oldBan != null ? oldBan.permissions.canPinMessages : oldCanReadMessages, newBan != null ? newBan.permissions.canPinMessages : newCanReadMessages, false);
                  appendRight(b, R.string.EventLogRestrictedChangeInfo, oldBan != null ? oldBan.permissions.canChangeInfo : oldCanReadMessages, newBan != null ? newBan.permissions.canChangeInfo : newCanReadMessages, false);
                }

                TdApi.FormattedText formattedText = new TdApi.FormattedText(b.toString().trim(), null);
                if (!entities.isEmpty()) {
                  formattedText.entities = new TdApi.TextEntity[entities.size()];
                  entities.toArray(formattedText.entities);
                }

                TGMessageText m = new TGMessageText(context, msg, formattedText);
                m.setIsEventLog(event, 0);

                return m;
              }
            }
            throw new IllegalArgumentException("unsupported: " + event.event.action);
          }
          return new TGMessageChat(context, msg, ((TdApiExt.MessageChatEvent) msg.content).event).setIsEventLog((TdApiExt.MessageChatEvent) msg.content, 0);

        case TdApi.MessageAnimatedEmoji.CONSTRUCTOR: {
          TdApi.MessageAnimatedEmoji emoji = nonNull((TdApi.MessageAnimatedEmoji) content);
          TdApi.MessageContent pendingContent = tdlib.getPendingMessageText(msg.chatId, msg.id);
          if (pendingContent != null) {
            if (pendingContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
              return new TGMessageSticker(context, msg, emoji, (TdApi.MessageAnimatedEmoji) pendingContent);
            } else {
              return new TGMessageText(context, msg, new TdApi.MessageText(Td.textOrCaption(emoji), null), new TdApi.MessageText(Td.textOrCaption(pendingContent), null));
            }
          }
          if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
            return new TGMessageText(context, msg, new TdApi.MessageText(Td.textOrCaption(emoji), null), null);
          } else {
            return new TGMessageSticker(context, msg, emoji, null);
          }
        }

        case TdApi.MessageText.CONSTRUCTOR: {
          TdApi.MessageContent pendingContent = tdlib.getPendingMessageText(msg.chatId, msg.id);
          if (pendingContent != null && pendingContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
            TdApi.MessageAnimatedEmoji animatedEmoji = (TdApi.MessageAnimatedEmoji) pendingContent;
            return new TGMessageSticker(context, msg, null, (TdApi.MessageAnimatedEmoji) animatedEmoji);
          }
          return new TGMessageText(context, msg, nonNull((TdApi.MessageText) content), (TdApi.MessageText) pendingContent);
        }
        case TdApi.MessageCall.CONSTRUCTOR: {
          return new TGMessageCall(context, msg, nonNull(((TdApi.MessageCall) content)));
        }
        case TdApi.MessagePhoto.CONSTRUCTOR: {
          return new TGMessageMedia(context, msg, nonNull(((TdApi.MessagePhoto) content).photo), ((TdApi.MessagePhoto) content).caption);
        }
        case TdApi.MessageExpiredPhoto.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageExpiredPhoto) content);
        }
        case TdApi.MessageVideo.CONSTRUCTOR: {
          return new TGMessageMedia(context, msg, nonNull(((TdApi.MessageVideo) content).video), ((TdApi.MessageVideo) content).caption);
        }
        case TdApi.MessageExpiredVideo.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageExpiredVideo) content);
        }
        case TdApi.MessageAnimation.CONSTRUCTOR: {
          return new TGMessageMedia(context, msg, nonNull(((TdApi.MessageAnimation) content).animation), ((TdApi.MessageAnimation) content).caption);
        }
        case TdApi.MessageVideoNote.CONSTRUCTOR: {
          return new TGMessageVideo(context, msg, nonNull(((TdApi.MessageVideoNote) content).videoNote), ((TdApi.MessageVideoNote) content).isViewed);
        }
        case TdApi.MessageDocument.CONSTRUCTOR: {
          TdApi.MessageDocument document = nonNull((TdApi.MessageDocument) content);
          if (TD.canConvertToVideo(document.document)) {
            return new TGMessageMedia(context, msg, document.document, document.caption);
          } else {
            return new TGMessageFile(context, msg);
          }
        }
        case TdApi.MessageDice.CONSTRUCTOR: {
          return new TGMessageSticker(context, msg, nonNull(((TdApi.MessageDice) content)));
        }
        case TdApi.MessageSticker.CONSTRUCTOR: {
          return new TGMessageSticker(context, msg, nonNull(((TdApi.MessageSticker) content).sticker), false, 0);
        }
        case TdApi.MessageVoiceNote.CONSTRUCTOR: {
          return new TGMessageFile(context, msg);
        }
        case TdApi.MessageAudio.CONSTRUCTOR: {
          return new TGMessageFile(context, msg);
        }
        case TdApi.MessagePoll.CONSTRUCTOR: {
          return new TGMessagePoll(context, msg, nonNull((TdApi.MessagePoll) content).poll);
        }
        case TdApi.MessageLocation.CONSTRUCTOR: {
          TdApi.MessageLocation location = (TdApi.MessageLocation) content;
          return new TGMessageLocation(context, msg, nonNull(location.location), location.livePeriod, location.expiresIn);
        }
        /*case TdApi.MessageInvoice.CONSTRUCTOR: {
          return new TGMessageInvoice(msg, validateContent((TdApi.MessageInvoice) content));
        }*/
        case TdApi.MessageContact.CONSTRUCTOR: {
          return new TGMessageContact(context, msg, (TdApi.MessageContact) content);
        }
        case TdApi.MessagePinMessage.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessagePinMessage) content);
        }
        case TdApi.MessageScreenshotTaken.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageScreenshotTaken) content);
        }
        case TdApi.MessageChatSetTtl.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatSetTtl) content);
        }
        case TdApi.MessageGameScore.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageGameScore) content);
        }
        case TdApi.MessageGame.CONSTRUCTOR: {
          return new TGMessageGame(context, msg, ((TdApi.MessageGame) content).game);
        }
        case TdApi.MessageContactRegistered.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageContactRegistered) content);
        }
        case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatChangePhoto) content);
        }
        case TdApi.MessageCustomServiceAction.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageCustomServiceAction) content);
        }
        case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessagePaymentSuccessful) content);
        }
        case TdApi.MessageChatDeletePhoto.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, TGMessageChat.TYPE_DELETE_PHOTO);
        }
        case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatAddMembers) content);
        }
        case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageBasicGroupChatCreate) content);
        }
        case TdApi.MessageChatChangeTitle.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatChangeTitle) content);
        }
        case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatDeleteMember) content);
        }
        case TdApi.MessageChatJoinByLink.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatJoinByLink) content);
        }
        case TdApi.MessageChatJoinByRequest.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatJoinByRequest) content);
        }
        case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageProximityAlertTriggered) content);
        }
        case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageInviteVideoChatParticipants) content);
        }
        case TdApi.MessageVideoChatStarted.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageVideoChatStarted) content);
        }
        case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageVideoChatEnded) content);
        }
        case TdApi.MessageVenue.CONSTRUCTOR: {
          return new TGMessageLocation(context, msg, ((TdApi.MessageVenue) content).venue);
        }
        case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageSupergroupChatCreate) content);
        }
        case TdApi.MessageWebsiteConnected.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageWebsiteConnected) content);
        }
        case TdApi.MessageChatUpgradeTo.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatUpgradeTo) content);
        }
        case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR: {
          return new TGMessageChat(context, msg, (TdApi.MessageChatUpgradeFrom) content);
        }
        // unsupported
        case TdApi.MessageInvoice.CONSTRUCTOR:
        case TdApi.MessagePassportDataSent.CONSTRUCTOR:
          break;
        case TdApi.MessageUnsupported.CONSTRUCTOR:
          unsupportedStringRes = R.string.UnsupportedMessageType;
          break;
        // bots only
        case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
        case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
          Log.e("Received bot message for a regular user:\n%s", msg);
          break;
        default: {
          Log.i("Weird content type: %s", msg.content.getClass().getName());
          break;
        }
      }
      TGMessageText text = new TGMessageText(context, msg, new TdApi.FormattedText(Lang.getString(unsupportedStringRes), null));
      text.addMessageFlags(FLAG_UNSUPPORTED);
      return text;
    } catch (Throwable t) {
      Log.e("Cannot parse message", t);
      return valueOfError(context, msg, t);
    }
  }

  public static TGMessage valueOfError (MessagesManager context, TdApi.Message msg, Throwable error) {
    String text = Lang.getString(R.string.FailureMessageText);

    TdApi.Object entitiesObject = Client.execute(new TdApi.GetTextEntities(text));
    TdApi.TextEntity[] entities = null;
    if (entitiesObject != null && entitiesObject.getConstructor() == TdApi.TextEntities.CONSTRUCTOR) {
      entities = ((TdApi.TextEntities) entitiesObject).entities;
    }

    TdApi.TextEntity logEntity = new TdApi.TextEntity(-1, -1, new TdApi.TextEntityTypePreCode());

    if (entities != null && entities.length > 0) {
      TdApi.TextEntity[] newEntities = new TdApi.TextEntity[entities.length + 1];
      System.arraycopy(entities, 0, newEntities, 0, entities.length);
      newEntities[entities.length] = logEntity;
      entities = newEntities;
    } else {
      entities = new TdApi.TextEntity[] {logEntity};
    }


    StringBuilder b = new StringBuilder(text);
    b.append("\n\n");

    logEntity.offset = b.length();

    b.append(Lang.getString(R.string.AppName)).append(' ').append(BuildConfig.VERSION_NAME).append("\n");
    b.append("Type: ").append(msg.content != null ? msg.content.getClass().getSimpleName() : "NULL").append("\n");
    b.append("Android: ").append(SdkVersion.getPrettyName()).append(" (").append(Build.VERSION.SDK_INT).append(")\n");
    b.append("Screen: ").append(Screen.widestSide()).append("x").append(Screen.smallestSide()).append("x").append(Screen.density()).append("\n");

    Tdlib tdlib = context.controller().tdlib();
    TdApi.Chat chat = tdlib.chat(msg.chatId);
    if (chat != null) {
      if (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
        TdApi.Supergroup supergroup = tdlib.chatToSupergroup(msg.chatId);
        if (supergroup != null && !StringUtils.isEmpty(supergroup.username)) {
          b.append("Public post: t.me/").append(supergroup.username).append('/').append(msg.id >> 20).append("\n");
        }
      }
    } else {
      b.append("FROM UNKNOWN CHAT #").append(msg.chatId).append("\n");
    }
    b.append("\n");

    Log.toStringBuilder(error, b);

    logEntity.length = b.length() - logEntity.offset;

    TGMessageText result = new TGMessageText(context, msg, new TdApi.FormattedText(b.toString(), entities));
    result.addMessageFlags(FLAG_UNSUPPORTED | FLAG_ERROR);
    return result;
  }

  // Hot stuff

  private static final class HotHandler extends Handler {
    public static final int MSG_HOT_CHECK = 0;

    public HotHandler () {
      super(Looper.getMainLooper());
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case MSG_HOT_CHECK: {
          ((TGMessage) msg.obj).checkHotTimer();
          break;
        }
      }
    }
  }

  // Bubbles

  public final boolean useBubbles () {
    return manager().useBubbles();
  }
  /*public static boolean useBubbles () {
    return ThemeManager.getChatStyle() == ThemeManager.CHAT_STYLE_BUBBLES;
  }*/
}
