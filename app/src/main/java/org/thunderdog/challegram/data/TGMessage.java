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
 * File created on 03/05/2015 at 10:39
 */
package org.thunderdog.challegram.data;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
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
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageQuickActionSwipeHelper;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.ReplyComponent;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ReactionsOverlayView;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.FeatureToggles;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.TranslationControllerV2;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.LanguageDetector;
import org.thunderdog.challegram.util.ReactionsCounterDrawable;
import org.thunderdog.challegram.util.TranslationCounterDrawable;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.util.text.TextPart;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.v.MessagesRecyclerView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.SdkVersion;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public abstract class TGMessage implements InvalidateContentProvider, TdlibDelegate, FactorAnimator.Target, Comparable<TGMessage>, Counter.Callback, TGAvatars.Callback, TranslationsManager.Translatable {
  private static final int MAXIMUM_CHANNEL_MERGE_TIME_DIFF = 150;
  private static final int MAXIMUM_COMMON_MERGE_TIME_DIFF = 900;

  private static final int MAXIMUM_CHANNEL_MERGE_COUNT = 19;
  private static final int MAXIMUM_COMMON_MERGE_COUNT = 14;

  private static final int MESSAGE_FLAG_READ = 1;
  private static final int MESSAGE_FLAG_IS_BOTTOM = 1 << 1;
  private static final int MESSAGE_FLAG_HAS_OLDER_MESSAGE = 1 << 2;
  private static final int MESSAGE_FLAG_IS_THREAD_HEADER = 1 << 3;
  private static final int MESSAGE_FLAG_BELOW_HEADER = 1 << 4;
  private static final int MESSAGE_FLAG_FORCE_AVATAR = 1 << 5; // FIXME conflicts with FLAG_LAYOUT_BUILT
  private static final int MESSAGE_FLAG_FIRST_UNREAD = 1 << 14;

  private static final int FLAG_LAYOUT_BUILT = 1 << 5;
  private static final int FLAG_MERGE_FORWARD = 1 << 6;
  private static final int FLAG_MERGE_BOTTOM = 1 << 7;
  private static final int FLAG_HEADER_ENABLED = 1 << 8;
  private static final int FLAG_SHOW_TICKS = 1 << 9;
  private static final int FLAG_SHOW_BADGE = 1 << 10;
  private static final int FLAG_SHOW_DATE = 1 << 11;
  private static final int FLAG_EXTRA_PADDING = 1 << 12;
  private static final int FLAG_IGNORE_REACTIONS_VIEW = 1 << 13;
  private static final int FLAG_HIDE_MEDIA = 1 << 17;
  private static final int FLAG_VIEWED = 1 << 18;
  private static final int FLAG_DATE_FAKE_BOLD = 1 << 19;
  private static final int FLAG_NO_UNREAD = 1 << 20;
  private static final int FLAG_ATTACHED = 1 << 21;
  private static final int FLAG_EVENT_LOG = 1 << 22;
  // private static final int FLAG_IS_ADMIN = 1 << 24;
  private static final int FLAG_SELF_CHAT = 1 << 25;
  private static final int FLAG_IGNORE_SWIPE = 1 << 26;
  private static final int FLAG_READY_QUICK_LEFT = 1 << 27;
  private static final int FLAG_READY_QUICK_RIGHT = 1 << 28;
  private static final int FLAG_UNSUPPORTED = 1 << 29;
  private static final int FLAG_ERROR = 1 << 30;
  private static final int FLAG_BEING_ADDED = 1 << 31;

  protected TdApi.Message msg;
  protected final TdApi.SponsoredMessage sponsoredMessage;
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
  protected final TGReactions messageReactions;
  protected final TGCommentButton commentButton;
  protected MessageQuickActionSwipeHelper swipeHelper;

  // header values

  private String date;
  private @Nullable Text hAuthorNameT, hPsaTextT, hAuthorChatMark;
  private @Nullable Text hAdminNameT;
  private @Nullable Letters uBadge;
  private EmojiStatusHelper.EmojiStatusDrawable hAuthorEmojiStatus;

  // counters

  private final Counter viewCounter, replyCounter, shareCounter, isPinned;
  private Counter shrinkedReactionsCounter, reactionsCounter;
  private final ReactionsCounterDrawable reactionsCounterDrawable;
  private final Counter isChannelHeaderCounter;
  private float isChannelHeaderCounterX, isChannelHeaderCounterY;

  private boolean translatedCounterForceShow;
  private final Counter isTranslatedCounter;
  private final TranslationCounterDrawable isTranslatedCounterDrawable;
  private float isTranslatedCounterX, isTranslatedCounterY;

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
  private int timeAddedHeight;
  private final FactorAnimator timeExpandValue = new FactorAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      if (BitwiseUtils.hasFlag(flags, FLAG_LAYOUT_BUILT)) {
        if (useBubbles() && !useReactionBubbles()) {
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

  private int pTimeLeft, pTimeWidth;
  private int pClockLeft, pClockTop;
  private int pTicksLeft, pTicksTop;
  private int pDateWidth;
  private int lastDrawReactionsX, lastDrawReactionsY;

  private final Path bubblePath, bubbleClipPath;
  private float topRightRadius, topLeftRadius, bottomLeftRadius, bottomRightRadius;
  private final RectF bubblePathRect, bubbleClipPathRect;

  private boolean needSponsorSmallPadding;

  protected final MessagesManager manager;
  protected final Tdlib tdlib;
  protected final MultipleViewProvider currentViews;
  protected final MultipleViewProvider overlayViews;

  private TdApi.AvailableReactions messageAvailableReactions;

  // Reaction draw mode

  public static final int REACTIONS_DRAW_MODE_BUBBLE = 0;
  public static final int REACTIONS_DRAW_MODE_FLAT = 1;
  public static final int REACTIONS_DRAW_MODE_ONLY_ICON = 2;

  private final TranslationsManager mTranslationsManager;

  protected TGMessage (MessagesManager manager, TdApi.Message msg) {
    this(manager, msg, null);
  }

  protected TGMessage (MessagesManager manager, long inChatId, TdApi.SponsoredMessage sponsoredMessage) {
    this(manager, SponsoredMessageUtils.sponsoredToTd(inChatId, sponsoredMessage, manager.controller().tdlib()), sponsoredMessage);
  }

  private TGMessage (MessagesManager manager, TdApi.Message msg, @Nullable TdApi.SponsoredMessage sponsoredMessage) {
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

    this.mTranslationsManager = new TranslationsManager(tdlib, this, this::setTranslatedStatus, this::setTranslationResult, this::showTranslateErrorMessageBubbleMode);

    this.bubblePath = new Path();
    this.bubblePathRect = new RectF();
    this.bubbleClipPath = new Path();
    this.bubbleClipPathRect = new RectF();

    this.swipeHelper = new MessageQuickActionSwipeHelper(this);
    this.currentViews = new MultipleViewProvider();
    this.currentViews.setContentProvider(this);
    this.msg = msg;
    this.sponsoredMessage = sponsoredMessage;
    this.messageReactions = new TGReactions(this, tdlib, msg.interactionInfo != null ? msg.interactionInfo.reactions : null, new TGReactions.MessageReactionsDelegate() {
      @Override
      public void onClick (View v, TGReactions.MessageReactionEntry entry) {
        boolean hasReaction = messageReactions.hasReaction(entry.getReactionType());
        if (!Config.PROTECT_ANONYMOUS_REACTIONS || hasReaction || messagesController().callNonAnonymousProtection(getId() + entry.hashCode(), TGMessage.this, getReactionBubbleLocationProvider(entry))) {
          boolean needAnimation = messageReactions.toggleReaction(entry.getReactionType(), false, false, handler(v, entry, () -> {
          }));
          if (needAnimation) {
            scheduleSetReactionAnimation(new NextReactionAnimation(entry.getTGReaction(), NextReactionAnimation.TYPE_CLICK));
          }
        }
      }

      @Override
      public void onLongClick (View v, TGReactions.MessageReactionEntry entry) {
        checkAvailableReactions(() -> {
          checkMessageFlags(() -> {
            if (canGetAddedReactions()) {
              MessagesController m = messagesController();
              m.showMessageAddedReactions(TGMessage.this, entry.getReactionType());
            } else {
              showReactionBubbleTooltip(v, entry, Lang.getString(R.string.ChannelReactionsAnonymous));
            }
          });
        });
      }

      @Override
      public void onRebuildRequested () {
        runOnUiThreadOptional(() -> {
          if (isLayoutBuilt()) {
            updateInteractionInfo(true);
          }
        });
      }
    });
    this.commentButton = new TGCommentButton(this);

    if (isSponsoredMessage()) {
      this.sender = new TdlibSender(tdlib, msg.chatId, sponsoredMessage.sponsor);
    } else {
      TdApi.MessageSender sender = msg.senderId;
      if (sender == null) {
        throw new IllegalArgumentException();
      }
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
    }

    this.isPinned = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .drawable(R.drawable.deproko_baseline_pin_14, 14f, 0f, Gravity.CENTER_HORIZONTAL)
      .build();
    this.isChannelHeaderCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .drawable(R.drawable.baseline_bullhorn_16, 16f, 0, Gravity.CENTER_HORIZONTAL)
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
      .drawable(R.drawable.baseline_reply_14, 14f, 3f, Gravity.LEFT)
      .build();
    this.shareCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .textSize(useBubbles() ? 11f : 12f)
      .callback(this)
      .colorSet(this::getTimePartTextColor)
      .drawable(R.drawable.baseline_share_arrow_14, 14f, 3f, Gravity.LEFT)
      .build();
    this.reactionsCounterDrawable = new ReactionsCounterDrawable(messageReactions.getReactionsAnimator());
    this.reactionsCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .textSize(useBubbles() ? 11f : 12f)
      .colorSet(() -> messageReactions.hasChosen() ? Theme.getColor(ColorId.badge) : this.getTimePartTextColor())
      .build();
    this.shrinkedReactionsCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .colorSet(() -> messageReactions.hasChosen() ? Theme.getColor(ColorId.badge) : Theme.getColor(ColorId.iconLight))
      .drawable(R.drawable.baseline_favorite_14, 14f, 0f, Gravity.CENTER_HORIZONTAL)
      .build();

    this.isTranslatedCounterDrawable = new TranslationCounterDrawable(Drawables.get(R.drawable.baseline_translate_14));
    this.isTranslatedCounterDrawable.setColors(
      msg.isOutgoing ? ColorId.bubbleOut_time : ColorId.bubbleIn_time,
      msg.isOutgoing ? ColorId.bubbleOut_time : ColorId.bubbleIn_time,
      msg.isOutgoing ? ColorId.bubbleOut_textLink : ColorId.bubbleIn_textLink
    );
    this.isTranslatedCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .drawable(isTranslatedCounterDrawable, 3f, Gravity.LEFT)
      .build();

    updateInteractionInfo(false);

    this.time = genTime();

    if (msg.viaBotUserId != 0) {
      TdApi.User viaBot = tdlib.cache().user(msg.viaBotUserId);
      if (viaBot != null) {
        this.viaBotUsername = "@" + Td.primaryUsername(viaBot);
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

    ThreadInfo messageThread = messagesController().getMessageThread();
    if (msg.replyTo != null && (messageThread == null || !messageThread.isRootMessage(msg.replyTo))) {
      if (msg.replyTo.getConstructor() == TdApi.MessageReplyToMessage.CONSTRUCTOR) { // TODO: support replies to stories
        loadReply();
      }
    }

    if (isHot() && needHotTimer() && isHotOpened()) {
      startHotTimer(false);
    }

    computeQuickButtons();
    checkHighlightedText();
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
    } else if (isSponsoredMessage()) {
      return Lang.getString(sponsoredMessage.isRecommended ? R.string.RecommendedSign : R.string.SponsoredSign);
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

  public int getForwardTimeStamp () {
    if (msg.forwardInfo == null) {
      return -1;
    }

    return msg.forwardInfo.date;
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
    return Lang.getDate(getComparingDate(), TimeUnit.SECONDS);
  }

  public final void forceAvatarWhenMerging (boolean value) {
    flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_FORCE_AVATAR, value);
  }

  public final boolean mergeWith (@Nullable TGMessage top, boolean isBottom) {
    if (top != null) {
      top.setNeedExtraPadding(false);
      top.setNeedExtraPresponsoredPadding(isSponsoredMessage());
      flags |= MESSAGE_FLAG_HAS_OLDER_MESSAGE;
    } else {
      flags &= ~MESSAGE_FLAG_HAS_OLDER_MESSAGE;
    }
    if (isBottom) {
      setNeedExtraPadding(true);
    }

    boolean isBelowHeader = top != null && top.isThreadHeader();
    flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_BELOW_HEADER, isBelowHeader);
    updateShowBadge();
    updateBadgeText();

    setIsBottom(true);

    if (top == null || top.isThreadHeader() != isThreadHeader() || !(isEventLog() ? needHideEventDate() || (DateUtils.isSameHour(top.getComparingDate(), getComparingDate()) /*|| !(msg.content instanceof TdApiExt.MessageChatEvent)*/) : DateUtils.isSameDay(top.getComparingDate(), getComparingDate()))) {
      if (top != null) {
        top.setIsBottom(true);
      }
      setHeaderEnabled(!headerDisabled());
      if ((top != null || getDate() != 0 || isScheduled()) && !isSponsoredMessage() && (!isBelowHeader || messagesController().areScheduledOnly())) {
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
    return hAuthorNameT != null ?
      hAuthorNameT.getWidth() + (hAuthorEmojiStatus != null ? hAuthorEmojiStatus.getWidth(Screen.dp(3)) : 0) + (hAuthorChatMark != null ? hAuthorChatMark.getWidth() + Screen.dp(16f) : 0) :
      needName(true) ? -Screen.dp(3f) : 0;
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
        if (needDrawChannelIconInHeader() && hAuthorNameT != null) {
          nameWidth += isChannelHeaderCounter.getScaledWidth(Screen.dp(5));
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
        float forwardWidth = Math.max((isPsa && fPsaTextT != null ? fAuthorNameT.getWidth() : fAuthorNameT != null ? fAuthorNameT.getWidth() : 0)
          + fTimeWidth + Screen.dp(6f)
          + (getViewCountMode() == VIEW_COUNT_FORWARD ? viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN)) + shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN)) : 0),
          isPsa && fPsaTextT != null && fAuthorNameT != null ? fAuthorNameT.getWidth() : 0)
          + (replyData != null ? xTextPadding : 0);
        width = Math.max(width, Math.max(contentWidth + Screen.dp(11f), (int) (forwardWidth + Screen.dp(11f))));
      } else {
        width = Math.max(width, contentWidth + Screen.dp(11f));
      }
    }

    if (commentButton.isVisible() && commentButton.isInline()) {
      if (allowMessageHorizontalExtend()) {
        float commentButtonWidth = commentButton.getAnimatedWidth(0, 1f);
        if (commentButtonWidth > width) {
          width = Math.round(MathUtils.fromTo(width, commentButtonWidth, commentButton.getVisibility()));
        }
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
      if (useBubbles() || BitwiseUtils.hasFlag(flags, FLAG_HEADER_ENABLED)) {
        return VIEW_COUNT_MAIN;
      }
    }
    return VIEW_COUNT_HIDDEN;
  }

  private final BoolAnimator openingComments = new BoolAnimator(0, new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

  protected final int getCommentButtonViewMode () {
    if (!needCommentButton()) {
      return TGCommentButton.VIEW_MODE_HIDDEN;
    }
    if (useBubbles() && (!useBubble() || useCircleBubble())) {
      return TGCommentButton.VIEW_MODE_BUBBLE;
    }
    return TGCommentButton.VIEW_MODE_INLINE;
  }

  public final @Nullable TdApi.Message findMessageWithThread () {
    TdApi.Message oldestMessage = getOldestMessage();
    return oldestMessage.canGetMessageThread ? oldestMessage : null;
  }

  protected final boolean needCommentButton () {
    if (isScheduled() || isSponsoredMessage() || !allowInteraction()) {
      return false;
    }
    if (isChannel()) {
      TdApi.Message messageWithThread = findMessageWithThread();
      return messageWithThread != null && TD.getReplyInfo(messageWithThread.interactionInfo) != null;
    }
    if (isRepliesChat()) {
      return FeatureToggles.SHOW_VIEW_IN_CHAT_BUTTON_IN_REPLIES && msg.forwardInfo != null && msg.forwardInfo.fromChatId != 0 && msg.forwardInfo.fromMessageId != 0 && msg.forwardInfo.fromChatId != msg.chatId;
    }
    return false;
  }

  protected boolean needCommentButtonSeparator () {
    return !drawBubbleTimeOverContent() || useForward();
  }

  public final void openMessageThread () {
    TdApi.Message messageWithThread = findMessageWithThread();
    if (messageWithThread == null)
      return;
    MessageId highlightMessageId;
    if (isChannel() || isChannelAutoForward()) {
      // View X Comments
      highlightMessageId = null;
    } else if (isMessageThreadRoot()) {
      // View X Replies
      highlightMessageId = new MessageId(messageWithThread.chatId, MessageId.MIN_VALID_ID);
    } else {
      // View Thread
      highlightMessageId = toMessageId();
    }
    openMessageThread(new TdApi.GetMessageThread(messageWithThread.chatId, messageWithThread.id), highlightMessageId);
  }

  public final void openMessageThread (@NonNull MessageId highlightMessageId) {
    openMessageThread(highlightMessageId, null);
  }

  public final void openMessageThread (@NonNull MessageId highlightMessageId, @Nullable MessageId fallbackHighlightMessageId) {
    TdApi.GetMessageThread query = new TdApi.GetMessageThread(highlightMessageId.getChatId(), highlightMessageId.getMessageId());
    TdApi.GetMessageThread fallbackQuery;
    if (fallbackHighlightMessageId != null) {
      fallbackQuery = new TdApi.GetMessageThread(fallbackHighlightMessageId.getChatId(), fallbackHighlightMessageId.getMessageId());
    } else {
      fallbackQuery = null;
    }
    openMessageThread(query, highlightMessageId, fallbackQuery, fallbackHighlightMessageId);
  }

  public final void openMessageThread (@NonNull TdApi.GetMessageThread query) {
    openMessageThread(query, null);
  }

  public final void openMessageThread (@NonNull TdApi.GetMessageThread query, @Nullable MessageId highlightMessageId) {
    openMessageThread(query, highlightMessageId, null, null);
  }

  public final void openMessageThread (@NonNull TdApi.GetMessageThread query, @Nullable MessageId highlightMessageId, @Nullable TdApi.GetMessageThread fallbackQuery, @Nullable MessageId fallbackHighlightMessageId) {
    if (openingComments.getValue())
      return;
    openingComments.setValue(true, needAnimateChanges());
    tdlib.client().send(query, result -> runOnUiThreadOptional(() -> {
      switch (result.getConstructor()) {
        case TdApi.MessageThreadInfo.CONSTRUCTOR: {
          TdApi.MessageThreadInfo messageThread = (TdApi.MessageThreadInfo) result;
          ThreadInfo threadInfo = ThreadInfo.openedFromChat(tdlib, messageThread, getChatId());
          if (Config.SHOW_CHANNEL_POST_REPLY_INFO_IN_COMMENTS && isChannel() &&
            msg.replyTo != null &&
            msg.chatId == query.chatId && isDescendantOrSelf(query.messageId)) {
            TdApi.Message message = threadInfo.getOldestMessage();
            if (message != null && message.replyTo == null && tdlib.isChannelAutoForward(message)) {
              message.replyTo = msg.replyTo;
            }
          }
          TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack().messageThread(threadInfo).after(chatId -> {
            openingComments.setValue(false, needAnimateChanges());
          });
          if (highlightMessageId != null) {
            MessageId finalHighlightMessageId;
            if (highlightMessageId.getChatId() != messageThread.chatId) {
              finalHighlightMessageId = new MessageId(messageThread.chatId, highlightMessageId.getMessageId(), highlightMessageId.getOtherMessageIds());
            } else {
              finalHighlightMessageId = highlightMessageId;
            }
            if (finalHighlightMessageId.isHistoryStart()) {
              params.highlightMessage(MessagesManager.HIGHLIGHT_MODE_UNREAD, finalHighlightMessageId);
            } else {
              params.highlightMessage(finalHighlightMessageId);
              params.ensureHighlightAvailable();
            }
          }
          tdlib.ui().openChat(this, messageThread.chatId, params);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if ("MSG_ID_INVALID".equals(TD.errorText(result))) {
            boolean needAnimateChanges = needAnimateChanges();
            openingComments.setValue(false, needAnimateChanges);
            if (isChannel()) {
              UI.showToast(R.string.ChannelPostDeleted, Toast.LENGTH_SHORT);
            } else {
              UI.showError(result);
            }
            break;
          }
          if (fallbackQuery != null) {
            openingComments.setValue(false, false);
            openMessageThread(fallbackQuery, fallbackHighlightMessageId);
            break;
          }
          openingComments.setValue(false, needAnimateChanges());
          UI.showError(result);
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
    if (!useMediaBubbleReactions() && !useStickerBubbleReactions() && useReactionBubbles()) {
      height += messageReactions.getAnimatedHeight() + (xReactionBubblePaddingBottom - Screen.dp(2)) * messageReactions.getVisibility();
    }
    if (commentButton.isVisible() && commentButton.isInline()) {
      height += commentButton.getAnimatedHeight(0, commentButton.getVisibility());
    }

    return height;
  }

  private static int getBubbleForwardOffset () {
    return getBubbleNameHeight();
  }

  private int getBubbleReplyOffset () {
    return ReplyComponent.height() + Screen.dp(useBubble() ? 3f : 6f) - (useForward() ? Screen.dp(9f) : 0);
  }

  public void rebuildLayout () {
    final int width = this.width;
    if (width != 0) {
      this.width = 0;
      buildLayout(width);
    }
  }

  public void onUpdateTextSize () {
    messageReactions.onUpdateTextSize();
  }

  public void prepareLayout () {
    if (this.width != 0) {
      rebuildLayout();
    } else {
      buildLayout(manager.getRecyclerWidth());
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
    buildReactions(false);
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
      return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_background : ColorId.bubbleIn_background);
    } else {
      int color = Theme.getColor(ColorId.chatBackground);
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
      if (oldHeight != height) {
        manager.onMessageHeightChanged(getChatId(), getId(), oldHeight, height);
      }
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
      if (useReactionBubbles()) {
        if (useMediaBubbleReactions()) {
          height += messageReactions.getAnimatedHeight() + xReactionBubblePaddingTop * messageReactions.getVisibility();
        } else if (useStickerBubbleReactions()) {
          height += messageReactions.getAnimatedHeight() + xReactionBubblePaddingTop * messageReactions.getVisibility();
        }
      }
      if (commentButton.isBubble()) {
        height += commentButton.getAnimatedHeight(Screen.dp(5f), commentButton.getVisibility());
      }
      return height;
    } else {
      int height = pContentY + getContentHeight() + getPaddingBottom() + getExtraPadding();
      if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
        height += inlineKeyboard.getHeight() + xPaddingBottom;
      }
      boolean useReactionBubbles = useReactionBubbles();
      if (useReactionBubbles) {
        height += messageReactions.getAnimatedHeight() + xReactionBubblePaddingTop * messageReactions.getVisibility();
      }
      if (hasFooter()) {
        height += getFooterHeight() + getFooterPaddingTop() + getFooterPaddingBottom();
      }
      if (commentButton.isVisible() && commentButton.isInline()) {
        height += commentButton.getAnimatedHeight(useReactionBubbles ? -Screen.dp(2f) : 0, commentButton.getVisibility());
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
    if (isSponsoredMessage()) {
      switch (sponsoredMessage.sponsor.type.getConstructor()) {
        case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR:
        case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR:
          return false;
        case TdApi.MessageSponsorTypeBot.CONSTRUCTOR:
        case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR:
          return true;
        default:
          Td.assertMessageSponsorType_ce9e3245();
          throw Td.unsupported(sponsoredMessage.sponsor.type);
      }
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
    if (isSponsoredMessage() && useBubbles())
      return true;
    if (isPsa() && forceForwardedInfo())
      return true;
    if (isOutgoing() && (sender.isAnonymousGroupAdmin() || sender.isChannel()))
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
    final float moveFactor = swipeHelper.getMoveFactor();

    if (moveFactor != 0f && !useBubbles()) {
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

  public final int getBubbleDateBackgroundColor () {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_date, ColorId.bubble_date_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_DATE);
  }

  public final int getBubbleDateTextColor () {
    return manager.getColor(ColorId.NONE, ColorId.bubble_dateText, ColorId.bubble_dateText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_DATE);
  }

  protected final int getUnreadSeparatorBackgroundColor () {
    return manager.getOverlayColor(ColorId.unread, ColorId.bubble_unread, ColorId.bubble_unread_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_UNREAD);
  }

  protected final int getUnreadSeparatorContentColor () {
    return manager.getColor(ColorId.unreadText, ColorId.bubble_unreadText, ColorId.bubble_unreadText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_UNREAD);
  }

  public final int getBubbleMediaReplyBackgroundColor () {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_mediaReply, ColorId.bubble_mediaReply_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_MEDIA_REPLY);
  }

  public final int getBubbleMediaReplyTextColor () {
    return manager.getColor(ColorId.NONE, ColorId.bubble_mediaReplyText, ColorId.bubble_mediaReplyText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_MEDIA_REPLY);
  }

  protected final int getBubbleTimeColor () {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_mediaTime, ColorId.bubble_mediaTime_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_TIME);
  }

  protected final int getBubbleTimeTextColor () {
    return manager.getColor(ColorId.NONE, ColorId.bubble_mediaTimeText, ColorId.bubble_mediaTimeText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_TIME);
  }

  public final int getBubbleButtonBackgroundColor () {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_button, ColorId.bubble_button_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_BUTTON);
  }

  public final int getBubbleButtonRippleColor () {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_buttonRipple, ColorId.bubble_buttonRipple_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_BUTTON);
  }

  public final int getBubbleButtonTextColor () {
    return manager.getColor(ColorId.NONE, ColorId.bubble_buttonText, ColorId.bubble_buttonText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_BUTTON);
  }

  public static int getBubbleTransparentColor (MessagesManager manager) {
    return manager.getOverlayColor(ColorId.NONE, ColorId.bubble_overlay, ColorId.bubble_overlay_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_OVERLAY);
  }

  public static int getBubbleTransparentTextColor (MessagesManager manager) {
    return manager.getColor(ColorId.NONE, ColorId.bubble_overlayText, ColorId.bubble_overlayText_noWallpaper, PropertyId.WALLPAPER_OVERRIDE_OVERLAY);
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
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha * detachFactor, Theme.getColor(ColorId.chatBackground))));
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

  public final void draw (MessageView view, Canvas c, @NonNull AvatarReceiver avatarReceiver, Receiver replyReceiver, ComplexReceiver replyTextMediaReceiver, DoubleImageReceiver previewReceiver, ImageReceiver contentReceiver, GifReceiver gifReceiver, ComplexReceiver complexReceiver) {
    final int viewWidth = view.getMeasuredWidth();
    final int viewHeight = view.getMeasuredHeight();

    final boolean useBubbles = useBubbles();
    final boolean useReactionBubbles = useReactionBubbles();
    final int reactionsDrawMode = getReactionsDrawMode();

    final float selectableFactor = manager.getSelectableFactor();

    checkEdges();

    // "Unread messages" / "Discussion started" badge
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
      if (isFirstUnread()) {
        int iconTop = Screen.dp(4.5f);
        Drawables.draw(c, iBadge, pBadgeIconX, iconTop + top, Paints.getUnreadSeparationPaint(color));
      }
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
      final int bubbleColor = Theme.getColor(isOutgoingBubble() && !useCircleBubble() ? ColorId.bubbleOut_background : ColorId.bubbleIn_background);
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

    if (hasBubble) {
      if (needBubbleCornerFix()) {
        int padding = getBubbleContentPadding();
        drawCornerFixes(c, this, 1f,
          bubblePathRect.left + padding, bubblePathRect.top + padding, bubblePathRect.right - padding, bubblePathRect.bottom - padding,
          topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
      }
      if (commentButton.isVisible() && commentButton.isInline()) {
        int left = leftContentEdge;
        int right = rightContentEdge;
        int bottom = bottomContentEdge;
        int top = bottom - commentButton.getAnimatedHeight(0, commentButton.getVisibility());
        if (needCommentButtonSeparator()) {
          int separatorColor = ColorUtils.alphaColor(0.15f * commentButton.getVisibility(), getDecentColor());
          Paint separatorPaint = Config.COMMENTS_INLINE_BUTTON_SEPARATOR_1PX ? Paints.strokeSeparatorPaint(separatorColor) : Paints.strokeSmallPaint(separatorColor);
          c.drawLine(left + Screen.dp(7f), top, right - Screen.dp(7f), top, separatorPaint);
        }
        commentButton.draw(view, c, view, left, top, right, bottom);
      }
    }

    if (hasFooter()) {
      drawFooter(view, c);
    }

    // Header
    if ((flags & FLAG_HEADER_ENABLED) != 0) {
      // Avatar
      if (needAvatar()) {
        float cx = avatarReceiver.centerX();
        float cy = avatarReceiver.centerY();
        if (useFullWidth()) {
          c.drawCircle(cx, cy, xAvatarRadius + Screen.dp(2.5f), Paints.fillingPaint(Theme.getColor(ColorId.chatBackground)));
        }
        if (avatarReceiver.needPlaceholder())
          avatarReceiver.drawPlaceholder(c);
        avatarReceiver.draw(c);
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
          if (hAuthorEmojiStatus != null) {
            hAuthorEmojiStatus.draw(c, left + hAuthorNameT.getWidth() + Screen.dp(3), newTop, 1f, view.getEmojiStatusReceiver());
          }
          if (sender.hasChatMark() && hAuthorChatMark != null) {
            int cmLeft = left + hAuthorNameT.getWidth() + Screen.dp(6f);
            RectF rct = Paints.getRectF();
            rct.set(cmLeft, newTop, cmLeft + hAuthorChatMark.getWidth() + Screen.dp(8f), newTop + hAuthorNameT.getLineHeight(false));
            c.drawRoundRect(rct, Screen.dp(2f), Screen.dp(2f), Paints.getProgressPaint(Theme.getColor(ColorId.textNegative), Screen.dp(1.5f)));
            cmLeft += Screen.dp(4f);
            hAuthorChatMark.draw(c, cmLeft, cmLeft + hAuthorChatMark.getWidth(), 0, newTop + ((hAuthorNameT.getLineHeight(false) - hAuthorChatMark.getLineHeight(false)) / 2));
          }
        }
        int right = getActualRightContentEdge() - xBubblePadding - xBubblePaddingSmall;
        if (useBubbles && needAdminSign() && hAdminNameT != null) {
          right -= hAdminNameT.getWidth();
          int y = top - Screen.dp(1.5f);
          hAdminNameT.draw(c, right, top - Screen.dp(12f));
        }
        if (useBubbles && needDrawChannelIconInHeader() && hAuthorNameT != null) {
          isChannelHeaderCounter.draw(c, isChannelHeaderCounterX = (right - Screen.dp(6)), isChannelHeaderCounterY = (top - Screen.dp(5)), Gravity.RIGHT | Gravity.BOTTOM, 1f, view, isOutgoing() ? ColorId.bubbleOut_time : ColorId.bubbleIn_time);
        }
      }
    }

    if (!useBubbles) {
      // Plain mode time part

      boolean needMetadata = BitwiseUtils.hasFlag(flags, FLAG_HEADER_ENABLED);
      int top = getHeaderPadding() + xViewsOffset + Screen.dp(7f);

      // Time
      if (needMetadata) {
        c.drawText(time, pTimeLeft, xTimeTop + getHeaderPadding(), mTime(true));
      }

      int clockX = pClockLeft - Icons.getClockIconWidth() - Screen.dp(Icons.CLOCK_SHIFT_X);
      int viewsX = pTicksLeft - Icons.getSingleTickWidth() + ((flags & FLAG_HEADER_ENABLED) != 0 ? 0 : Screen.dp(1f)) - Screen.dp(Icons.TICKS_SHIFT_X);

      if (needDrawChannelIconInHeader() && hAuthorNameT != null) {
        isChannelHeaderCounter.draw(c, isChannelHeaderCounterX = ((isSending() ? clockX : viewsX) + Screen.dp(7)), isChannelHeaderCounterY = (pTicksTop + Screen.dp(5)), Gravity.LEFT, 1f, view, ColorId.iconLight);
        clockX -= isChannelHeaderCounter.getScaledWidth(Screen.dp(1));
        viewsX -= isChannelHeaderCounter.getScaledWidth(Screen.dp(1));
      }

      // Clock, tick and views
      if (isSending()) {
        Drawables.draw(c, Icons.getClockIcon(ColorId.iconLight), clockX, pClockTop - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint());
      } else if (isFailed()) {
        // TODO failure icon
      } else if (shouldShowTicks() && getViewCountMode() != VIEW_COUNT_MAIN) {
        boolean unread = isUnread() && !noUnread();
        Drawables.draw(c, unread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticksRead), viewsX, pTicksTop - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint());
      }

      int right = pTicksLeft - (shouldShowTicks() ? Icons.getSingleTickWidth() + Screen.dp(2.5f) : 0); //needMetadata ? pTimeLeft - Screen.dp(4f) : pTicksLeft;
      if (needDrawChannelIconInHeader() && hAuthorNameT != null) {
        right -= isChannelHeaderCounter.getScaledWidth(Screen.dp(1));
      }

      // Edited
      if (shouldShowEdited()) {
        // right -= Icons.getEditedIconWidth();
        right -= Icons.getEditedIconWidth();
        if (isBeingEdited()) {
          Drawables.draw(c, Icons.getClockIcon(ColorId.iconLight), pTicksLeft - (shouldShowTicks() ? Icons.getSingleTickWidth() + Screen.dp(2.5f) : 0) - Icons.getEditedIconWidth() - Screen.dp(6f), pTicksTop - Screen.dp(5f), Paints.getIconLightPorterDuffPaint());
        } else {
          Drawables.draw(c, view.getSparseDrawable(R.drawable.baseline_edit_12, ColorId.NONE), right, pTicksTop, Paints.getIconLightPorterDuffPaint());
        }
        right -= Screen.dp(COUNTER_ADD_MARGIN);
      }

      isPinned.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
      right -= isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN));
      if (needMetadata) {
        right -= Screen.dp(COUNTER_ADD_MARGIN);
        if (replyCounter.getVisibility() > 0f) {
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

      if (translationStyleMode() == Settings.TRANSLATE_MODE_INLINE) {
        isTranslatedCounter.draw(c, right, isTranslatedCounterY = top, Gravity.RIGHT, 1f);
        isTranslatedCounterX = right - Screen.dp(10);
        right -= isTranslatedCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      }
      if (reactionsDrawMode == REACTIONS_DRAW_MODE_FLAT) {
        reactionsCounter.draw(c, right, top, Gravity.RIGHT, 1f, view, getTimePartIconColorId());
        right -= reactionsCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN));
        right -= reactionsCounterDrawable.getMinimumWidth();
        drawReactionsWithoutBubbles(c, right, top);
        right -= Screen.dp(5) * reactionsCounter.getVisibility();
      }
      if (reactionsDrawMode == REACTIONS_DRAW_MODE_ONLY_ICON) {
        shrinkedReactionsCounter.draw(c, right, top, Gravity.RIGHT, 1f, view, ColorId.NONE);
        setLastDrawReactionsPosition(right, top);
        right -= shrinkedReactionsCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN)) + Screen.dp(COUNTER_ADD_MARGIN);
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
        c.drawCircle(centerX, centerY, Screen.dp(9f) + (int) (Screen.dp(1f) * (1f - transparency)), Paints.strokeBigPaint(ColorUtils.alphaColor(selectableFactor, ColorUtils.fromToArgb(Theme.getColor(ColorId.bubble_messageCheckOutline), Theme.getColor(ColorId.bubble_messageCheckOutlineNoWallpaper), transparency))));
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
          final int color = Theme.getColor(ColorId.chatBackground);
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

    // Reaction bubbles
    if (useReactionBubbles) {
      int top = (int) (findBottomEdge() - messageReactions.getAnimatedHeight());
      if (!useBubbles) {
        if (commentButton.isVisible() && commentButton.isInline()) {
          top -= commentButton.getAnimatedHeight(-Screen.dp(2), commentButton.getVisibility());
        }
        drawReactionsWithBubbles(c, view, xContentLeft, top - Screen.dp(9));
      } else {
        if (useMediaBubbleReactions()) {
          drawReactionsWithBubbles(c, view, (int) bubblePathRect.left, top - Screen.dp(6));
        } else if (useStickerBubbleReactions()) {
          int left = isOutgoingBubble() ? (useBubble() ? getContentX() : getActualRightContentEdge() - getContentWidth()) : (useBubble() && useCircleBubble() ? (int) bubblePathRect.left : getContentX());
          if (isOutgoingBubble() && messageReactions.getAnimatedWidth() > getContentWidth()) {
            left = (int) (getActualRightContentEdge() - messageReactions.getAnimatedWidth());
          }
          if (commentButton.isVisible() && commentButton.isBubble()) {
            top -= commentButton.getAnimatedHeight(Screen.dp(5), commentButton.getVisibility());
          }
          drawReactionsWithBubbles(c, view, left, top - Screen.dp(6));
        } else {
          int x = (int) bubblePathRect.left + xReactionBubblePadding;
          int y = bottomContentEdge - (int) messageReactions.getAnimatedHeight() - timeAddedHeight - xReactionBubblePaddingBottom;
          if (commentButton.isVisible() && commentButton.isInline()) {
            y -= commentButton.getAnimatedHeight(0, commentButton.getVisibility());
          }
          drawReactionsWithBubbles(c, view, x, y);
        }
      }
    }

    if (commentButton.isVisible()) {
      if (useBubbles) {
        if (commentButton.isBubble()) {
          int left;
          if (useBubble()) {
            left = (int) bubblePathRect.left;
          } else {
            left = getContentX();
          }
          int bottom = findBottomEdge() - Math.round((Screen.dp(5f) * commentButton.getVisibility()));
          int right = left + commentButton.getAnimatedWidth(0, 1f);
          int top = bottom - commentButton.getAnimatedHeight(0, commentButton.getVisibility());
          int inset = Math.round((right - left) * 0.2f * (1f - commentButton.getVisibility()));
          commentButton.draw(view, c, view, left + inset, top, right - inset, bottom);
        }
      } else {
        if (commentButton.isInline()) {
          float scale = commentButton.getVisibility();
          int bottom = findBottomEdge() - (useReactionBubbles ? Math.round(Screen.dp(2f) * scale) : 0);
          int top = bottom - commentButton.getAnimatedHeight(0, scale);
          commentButton.draw(view, c, view, 0, top, width, bottom);
        }
      }
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
        lineTop = replyData != null ? (topContentEdge + (useBubble() ? xBubblePadding + xBubblePaddingSmall : Screen.dp(8f)) + (useBubbleName() ? getBubbleNameHeight() : 0)) : forwardY;
        lineBottom = bottomContentEdge - xBubblePadding - xBubblePaddingSmall - (useBubbleTime() ? getBubbleTimePartHeight() : 0);
        if (useReactionBubbles) {
          lineBottom = (int) (lineBottom - (messageReactions.getAnimatedHeight() + xReactionBubblePaddingTop * messageReactions.getVisibility()) + (getBubbleTimePartHeight() * (1f - messageReactions.getTimeHeightExpand()) * messageReactions.getVisibility())); // - xReactionBubblePaddingBottom * messageReactions.getVisibility());
        }
        if (commentButton.isVisible() && commentButton.isInline()) {
          lineBottom -= commentButton.getAnimatedHeight(0, commentButton.getVisibility());
        }
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
          lineBottom = forwardY + getForwardHeight() + (replyData != null ? ReplyComponent.height() : 0);
          mergeBottom = false;
        }
      }

      int lineWidth = Screen.dp(3f);
      int lineLeft = getForwardLeft();
      int lineRight = lineLeft + lineWidth;

      RectF rectF = Paints.getRectF();
      rectF.set(lineLeft, lineTop, lineRight, lineBottom);
      final int lineColor = getVerticalLineColor();
      c.drawRoundRect(rectF, lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(lineColor));

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

      if (useBubbles) {
        top = topContentEdge + (useBubble() ? xBubblePadding + xBubblePaddingSmall : Screen.dp(8f)) + (useBubbleName() ? getBubbleNameHeight() : 0);
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
      } else {
        top = pContentY - ReplyComponent.height();
        startX = useFullWidth() ? pRealContentX : pContentX;
        endX = viewWidth - startX;
      }

      if (useBubbles && isForward() && !forceForwardedInfo()) {
        startX += xTextPadding;
      }

      replyData.draw(c, startX, top, endX, width, replyReceiver, replyTextMediaReceiver, Lang.rtl());
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

    startSetReactionAnimationIfReady();
    highlightUnreadReactionsIfNeeded();
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
    final boolean savedContentTranslation = contentOffset != 0;
    int globalRestoreToCount = -1;
    if (savedContentTranslation) {
      globalRestoreToCount = Views.save(c);
      c.translate(contentOffset, 0);
    }
    final boolean savedTranslation = translation != 0f;
    int restoreToCount = -1;
    if (savedTranslation) {
      restoreToCount = Views.save(c);
      c.translate(translation, 0);
    }
    drawOverlay(view, c, pContentX, pContentY, pContentMaxWidth);
    if (savedTranslation) {
      Views.restore(c, restoreToCount);
      drawTranslate(view, c);
    } else if (dismissFactor != 0f) {
      drawTranslate(view, c);
    }
    if (useBubbles()) {
      if (useBubbleTime()) {
        drawBubbleTimePart(c, view);
      }
    }
    if (savedContentTranslation) {
      Views.restore(c, globalRestoreToCount);
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
      if (isAttached) {
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
    if (hAuthorEmojiStatus != null) {
      hAuthorEmojiStatus.onAppear();
    }

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
    return hasAnyTargetToInvalidate() && controller().getParentOrSelf().isAttachedToNavigationController() && BitwiseUtils.hasFlag(flags, FLAG_LAYOUT_BUILT) && UI.inUiThread();
  }

  public final boolean isLayoutBuilt () {
    return BitwiseUtils.hasFlag(flags, FLAG_LAYOUT_BUILT);
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
    for (View view : currentViews) {
      act.runWithData((MessageView) view);
    }
  }

  public final @Nullable View findCurrentView () {
    return currentViews.findAnyTarget();
  }

  @Override
  public final boolean invalidateContent (Object cause) {
    if (cause == replyData) {
      invalidateReplyReceiver();
    } else {
      invalidateContentReceiver();
    }
    return true;
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

  public final void invalidateTextMediaReceiver () {
    performWithViews(view -> requestTextMedia(view.getTextMediaReceiver()));
  }

  public final void invalidateEmojiStatusReceiver () {
    performWithViews(view -> requestAuthorTextMedia(view.getEmojiStatusReceiver()));
  }

  public final void invalidateAvatarsReceiver () {
    performWithViews(view -> requestCommentsResources(view.getAvatarsReceiver(), true));
  }

  public final void invalidateTextMediaReceiver (@NonNull Text text, @Nullable TextMedia textMedia) {
    performWithViews(view -> view.invalidateTextMediaReceiver(this, text, textMedia));
  }

  public final void invalidateReplyTextMediaReceiver (@NonNull Text text, @Nullable TextMedia textMedia) {
    performWithViews(view -> view.invalidateReplyTextMediaReceiver(this, text, textMedia));
  }

  // Touch

  public boolean allowLongPress (float x, float y) {
    if (messagesController().inSelectMode()) {
      return true;
    }
    if (commentButton.isVisible() && commentButton.contains(x, y)) {
      // long press is handled in commentButton
      return false;
    }
    return true;
  }

  @CallSuper
  public boolean performLongPress (View view, float x, float y) {
    if (isSponsoredMessage()) {
      return false;
    }
    boolean result = false;
    if (inlineKeyboard != null) {
      result = inlineKeyboard.performLongPress(view);
    }
    if (messageReactions.getTotalCount() > 0 && useReactionBubbles()) {
      result = messageReactions.performLongPress(view) || result;
    }
    if (hasFooter()) {
      result = footerText.performLongPress(view) || result;
    }
    clickHelper.cancel(view, x, y);
    return result;
  }

  public boolean shouldIgnoreTap (MotionEvent e) {
    return e.getY() < findTopEdge();
  }

  private int getClickType (MessageView view, float x, float y) {
    if (isTranslated()) {
      if (MathUtils.distance(isTranslatedCounterX, isTranslatedCounterY, x, y) < Screen.dp(8)) {
        return CLICK_TYPE_TRANSLATE_MESSAGE_ICON;
      }
    }

    if (needDrawChannelIconInHeader() && hAuthorNameT != null) {
      if (MathUtils.distance(isChannelHeaderCounterX, isChannelHeaderCounterY, x, y) < Screen.dp(8)) {
        return CLICK_TYPE_CHANNEL_MESSAGE_ICON;
      }
    }

    if (replyData != null && replyData.isInside(x, y, useBubbles() && !useBubble())) {
      return CLICK_TYPE_REPLY;
    }
    if (hasHeader() && needAvatar() && view.getAvatarReceiver().isInsideReceiver(x, y)) {
      return CLICK_TYPE_AVATAR;
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
        case CLICK_TYPE_TRANSLATE_MESSAGE_ICON: {
          openLanguageSelectorInlineMode();
          break;
        }
        case CLICK_TYPE_CHANNEL_MESSAGE_ICON: {
          openMessageFromChannel();
          break;
        }
        case CLICK_TYPE_REPLY: {
          if (replyData != null && replyData.hasValidMessage()) {
            TdApi.MessageReplyToMessage replyToMessage = (TdApi.MessageReplyToMessage) msg.replyTo;
            if (replyToMessage.chatId != msg.chatId) {
              if (isMessageThread() && isThreadHeader()) {
                tdlib.ui().openMessage(controller(), replyToMessage.chatId, new MessageId(replyToMessage), openParameters());
              } else {
                openMessageThread(new MessageId(replyToMessage));
              }
            } else if (isScheduled()) {
              tdlib.ui().openMessage(controller(), replyToMessage.chatId, new MessageId(replyToMessage), openParameters());
            } else {
              highlightOtherMessage(replyToMessage.messageId);
            }
          }
          break;
        }
        case CLICK_TYPE_AVATAR: {
          onAvatarClick(view);
          break;
        }
      }
      clickType = CLICK_TYPE_NONE;
    }
  });

  protected final void highlightOtherMessage (MessageId messageId) {
    manager.controller().highlightMessage(messageId, toMessageId());
  }

  protected final void highlightOtherMessage (long otherMessageId) {
    highlightOtherMessage(new MessageId(msg.chatId, otherMessageId));
  }

  private float mInitialTouchX;
  private float mInitialTouchY;

  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    /*if ((flags & FLAG_HEADER_ENABLED) == 0 && (msg.forwardInfo == null || forwardInfo == null) && replyData == null && (inlineKeyboard == null || inlineKeyboard.isEmpty())) {
      return false;
    }*/
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      mInitialTouchX = e.getX();
      mInitialTouchY = e.getY();
    }

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
    if (useReactionBubbles()) {
      if (messageReactions.onTouchEvent(view, e)) {
        return true;
      }
    }
    if (commentButton.onTouchEvent(view, e)) {
      return true;
    }
    return clickHelper.onTouchEvent(view, e);
  }

  private static final int CLICK_TYPE_NONE = 0;
  private static final int CLICK_TYPE_REPLY = 1;
  private static final int CLICK_TYPE_AVATAR = 2;
  private static final int CLICK_TYPE_CHANNEL_MESSAGE_ICON = 3;
  private static final int CLICK_TYPE_TRANSLATE_MESSAGE_ICON = 4;

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

      if (isFirstUnread()) {
        // badge has icon
        pBadgeX = (int) (center - (badgeWidth + Screen.dp(7f) + iBadge.getMinimumWidth()) / 2f);
      } else {
        // badge has no icon
        pBadgeX = (int) (center - badgeWidth / 2f);
      }
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

  protected void buildReactions (boolean animated) {
    if (useReactionBubbles()) {
      if (useBubbles()) {
        if (useMediaBubbleReactions()) {
          messageReactions.measureReactionBubbles(computeBubbleWidth() + getBubblePaddingLeft() + getBubblePaddingRight());
        } else if (useStickerBubbleReactions()) {
          messageReactions.measureReactionBubbles(Math.max(getContentWidth(), (int) (getEstimatedContentMaxWidth() * 0.85f)));
        } else {
          messageReactions.measureReactionBubbles((computeBubbleWidth() + getBubblePaddingLeft() + getBubblePaddingRight() - xReactionBubblePadding * 2), computeBubbleTimePartWidth(true, true));
        }
      } else {
        messageReactions.measureReactionBubbles(getEstimatedContentMaxWidth(), 0);
      }
    }
    messageReactions.resetReactionsAnimator(animated);
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
      BitwiseUtils.hasFlag(flags, FLAG_SELF_CHAT) ||
      (isChannelAutoForward() && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginChannel.CONSTRUCTOR &&
        msg.forwardInfo.fromChatId == ((TdApi.MessageForwardOriginChannel) msg.forwardInfo.origin).chatId) ||
      msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR ||
      (isPsa() && !sender.isUser() && useBubbles()) ||
      isRepliesChat());
  }

  public final boolean isChannelAutoForward () {
    return tdlib.isChannelAutoForward(msg);
  }

  public final boolean isRepliesChat () {
    return tdlib.isRepliesChat(msg.chatId);
  }

  public final boolean isMessageThread () {
    return messagesController().getMessageThread() != null;
  }

  private boolean hasAvatar;

  private void layoutAvatar () {
    if (useBubbles() && !needAvatar()) {
      hasAvatar = false;
      return;
    }
    if (this.chat == null) {
      this.chat = tdlib.chat(msg.chatId);
    }
    hasAvatar = true;
    // FIXME: better logic behind this method
  }

  protected static final float LETTERS_SIZE = 16f;
  protected static final float LETTERS_SIZE_SMALL = 15f;

  private boolean onAvatarClick (View view) {
    return openProfile(view, null, null, null, ((MessageView) view).getAvatarReceiver());
  }

  private boolean onNameClick (View view, Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (part.getEntity() != null && part.getEntity().getTag() instanceof Long) {
      manager.controller().setInputInlineBot(msg.viaBotUserId, viaBotUsername);
      return true;
    } else if (needDrawChannelIconInHeader()) {
      return openMessageFromChannel();
    } else {
      return openProfile(view, text, part, openParameters, null);
    }
  }

  private boolean openProfile (View view, @Nullable Text text, TextPart part, @Nullable TdlibUi.UrlOpenParameters openParameters, @Nullable Receiver receiver) {
    if (forceForwardedInfo()) {
      forwardInfo.open(view, text, part, openParameters, receiver);
    } else if (isSponsoredMessage()) {
      openSponsoredMessage();
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

  private Text makeChatMark (int maxWidth) {
    return new Text.Builder(Lang.getString(sender.isFake() ? R.string.FakeMark : R.string.ScamMark), maxWidth, Paints.robotoStyleProvider(10f), TextColorSets.Regular.NEGATIVE)
      .singleLine()
      .allBold()
      .clipTextArea()
      .build();
  }

  private Text makeName (String authorName, int nameColorId, boolean available, boolean isPsa, boolean hideName, long viaBotUserId, int maxWidth, boolean isForward) {
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
    } else if (!isForward && needColoredNames() && nameColorId != 0) {
      colorTheme = new TextColorSetOverride(getChatAuthorColorSet()) {
        @Override
        public int clickableTextColor (boolean isPressed) {
          return Theme.getColor(nameColorId);
        }

        @Override
        public int backgroundColor (boolean isPressed) {
          return isPressed ? ColorUtils.alphaColor(.2f, Theme.getColor(nameColorId)) : 0;
        }

        @Override
        public int backgroundColorId (boolean isPressed) {
          return isPressed ? nameColorId : 0;
        }
      };
    } else {
      colorTheme = getChatAuthorColorSet();
    }

    colorTheme = new TextColorSetOverride(colorTheme) {
      @Override
      public int emojiStatusColor () {
        return clickableTextColor(false);
      }
    };

    if (!(tdlib.isSelfChat(chat) && forwardInfo != null) && !hasBot && !isForward && sender.isUser()) {
      hAuthorEmojiStatus = EmojiStatusHelper.makeDrawable(null, tdlib, tdlib.cache().user(sender.getUserId()), colorTheme, (text1, specificMedia) -> invalidateEmojiStatusReceiver());
      hAuthorEmojiStatus.invalidateTextMedia();
      maxWidth -= hAuthorEmojiStatus.getWidth(Screen.dp(3));
    }

    return new Text.Builder(tdlib, text, openParameters(), maxWidth, getNameStyleProvider(), colorTheme, null)
      .singleLine()
      .clipTextArea()
      .allBold(allBold)
      .allClickable(allActive)
      .viewProvider(currentViews)
      .onClick(isForward ? this::onForwardClick : this::onNameClick)
      .build();
  }

  private void layoutInfo () {
    final int reactionsDrawMode = getReactionsDrawMode();
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

      final String authorName = getDisplayAuthor();
      if (needName(true) && maxWidth > 0) {
        if (!forceForwardedInfo() && sender.hasChatMark()) {
          hAuthorChatMark = makeChatMark(maxWidth);
          maxWidth -= hAuthorChatMark.getWidth();
        }
        isChannelHeaderCounter.showHide(needDrawChannelIconInHeader(), false);
        if (needDrawChannelIconInHeader()) {
          maxWidth -= isChannelHeaderCounter.getScaledWidth(Screen.dp(5));
        }
        hAuthorNameT = makeName(authorName, forceForwardedInfo() ? forwardInfo.getAuthorNameColorId() : sender.getNameColorId(), !(forceForwardedInfo() && forwardInfo instanceof TGSourceHidden), isPsa, !needName(false), msg.forwardInfo == null || forceForwardedInfo() ? msg.viaBotUserId : 0, maxWidth, false);
      } else {
        hAuthorNameT = null;
        hAuthorChatMark = null;
        isChannelHeaderCounter.showHide(false, false);
      }
      if (isPsa) {
        CharSequence text = Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType);
        hPsaTextT = new Text.Builder(tdlib, text, openParameters(), maxWidth, getNameStyleProvider(), getChatAuthorPsaColorSet(), null)
          .allClickable()
          .singleLine()
          .viewProvider(currentViews)
          .onClick(this::onNameClick)
          .build();
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
    if (replyCounter.getVisibility() > 0f) {
      max -= replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (getViewCountMode() == VIEW_COUNT_MAIN) {
      max -= shareCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
      max -= viewCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (reactionsDrawMode == REACTIONS_DRAW_MODE_FLAT) {
      max -= reactionsCounterDrawable.getMinimumWidth();
      max -= reactionsCounter.getScaledWidth(Screen.dp(8));
    }
    if (reactionsDrawMode == REACTIONS_DRAW_MODE_ONLY_ICON) {
      max -= shrinkedReactionsCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    if (translationStyleMode() == Settings.TRANSLATE_MODE_INLINE) {
      max -= isTranslatedCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    int nameMaxWidth;
    if (isPsa) {
      nameMaxWidth = totalMaxWidth;
      CharSequence text = Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType);
      hPsaTextT = max <= 0 ? null :
        new Text.Builder(tdlib, text, openParameters(), max, getNameStyleProvider(), getChatAuthorPsaColorSet(), null)
        .allClickable()
        .singleLine()
        .viewProvider(currentViews)
        .onClick(this::onNameClick)
        .build();
    } else {
      hPsaTextT = null;
      nameMaxWidth = max;
    }
    if (nameMaxWidth > 0) {
      if (!forceForwardedInfo() && sender.hasChatMark()) {
        hAuthorChatMark = makeChatMark(totalMaxWidth);
        nameMaxWidth -= hAuthorChatMark.getWidth() + Screen.dp(8f);
      }
      isChannelHeaderCounter.showHide(needDrawChannelIconInHeader(), false);
      if (needDrawChannelIconInHeader()) {
        nameMaxWidth -= isChannelHeaderCounter.getScaledWidth(Screen.dp(1));
      }
      hAuthorNameT = makeName(authorName, forceForwardedInfo() ? forwardInfo.getAuthorNameColorId() : sender.getNameColorId(), !(forceForwardedInfo() && forwardInfo instanceof TGSourceHidden), isPsa, !needName(false), msg.forwardInfo == null || forceForwardedInfo() ? msg.viaBotUserId : 0, nameMaxWidth, false);
    } else {
      hAuthorNameT = null;
      hAuthorChatMark = null;
      isChannelHeaderCounter.showHide(false, false);
    }
  }

  private String getDisplayAuthor () {
    if (forceForwardedInfo()) {
      return forwardInfo.getAuthorName();
    } else {
      return sender.getName();
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
    fAuthorNameT = makeName(forwardInfo.getAuthorName(), 0, !(forwardInfo instanceof TGSourceHidden), isPsa, false, msg.viaBotUserId, (int) (isPsa ? totalMax : max), true);
    if (isPsa) {
      CharSequence text = Lang.getPsaNotificationType(controller(), msg.forwardInfo.publicServiceAnnouncementType);
      fPsaTextT = new Text.Builder(tdlib, text, openParameters(), (int) max, getNameStyleProvider(), getChatAuthorPsaColorSet(), null)
        .allClickable()
        .singleLine()
        .onClick(this::onForwardClick)
        .build();
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

  public final void replaceReplyContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (Td.equalsTo(msg.replyTo, chatId, messageId) && replyData != null) {
      replyData.replaceMessageContent(messageId, newContent);
    }
  }

  public final void replaceReplyTranslation (long chatId, long messageId, @Nullable TdApi.FormattedText translation) {
    if (Td.equalsTo(msg.replyTo, chatId, messageId) && replyData != null) {
      replyData.replaceMessageTranslation(messageId, translation);
    }
  }

  public final void removeReply (long chatId, long messageId) {
    if (Td.equalsTo(msg.replyTo, chatId, messageId) && replyData != null) {
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

    if (!useBubbles() || useMediaBubbleReactions() || useStickerBubbleReactions()) {
      notifyBubbleChanged();  // не костыль ?
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

  private int bubbleTimePartWidth;

  protected final int getBubbleTimePartWidth () {
    return useBubbles() && useBubbleTime() ? bubbleTimePartWidth : 0;
  }

  protected final boolean moveBubbleTimePartToLeft () {
    return Lang.rtl();
  }

  protected final boolean needExpandBubble (int bottomLineContentWidth, int bubbleTimePartWidth, int maxLineWidth) {
    switch (bottomLineContentWidth) {
      case BOTTOM_LINE_KEEP_WIDTH:
        return false;
      case BOTTOM_LINE_EXPAND_HEIGHT:
        return true;
      case BOTTOM_LINE_DEFINE_BY_FACTOR:
        throw new UnsupportedOperationException();
    }
    return bottomLineContentWidth > 0 && (bottomLineContentWidth + bubbleTimePartWidth > maxLineWidth);
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

      final int defaultBubbleWidth = computeBubbleWidth();
      final int defaultBubbleHeight = computeBubbleHeight();
      int bubbleWidth = defaultBubbleWidth;
      int bubbleHeight = defaultBubbleHeight;
      boolean needAnimateTimeExpand = true;
      timeAddedHeight = 0;

      if (!headerDisabled() && !(drawBubbleTimeOverContent() && !useForward()) && useBubbleTime()) {
        final int bubbleTimePartWidthWithoutPadding = computeBubbleTimePartWidth(false);
        final int bubbleTimePartWidth = computeBubbleTimePartWidth(true);
        final int bottomLineContentWidth = useForward() ? BOTTOM_LINE_EXPAND_HEIGHT : hasFooter() ? footerText.getLastLineWidth() + Screen.dp(10f) : getBottomLineContentWidth();
        final boolean allowHorizontalExtend = allowBubbleHorizontalExtend();

        final int expandedBubbleWidth = allowHorizontalExtend ? Math.max(bubbleWidth, bubbleTimePartWidthWithoutPadding) : bubbleWidth;
        final int expandedBubbleHeight = bubbleHeight + getBubbleTimePartHeight();

        switch (bottomLineContentWidth) {
          case BOTTOM_LINE_KEEP_WIDTH:
            break;
          case BOTTOM_LINE_DEFINE_BY_FACTOR: {
            final int extendedWidth = getAnimatedBottomLineWidth() + bubbleTimePartWidth;
            final int fitBubbleWidth = Math.max(bubbleWidth, extendedWidth);

            float factor = getBubbleExpandFactor();
            if (factor > 0f) {
              bubbleWidth = MathUtils.fromTo(fitBubbleWidth, expandedBubbleWidth, factor);
              int newBubbleHeight = MathUtils.fromTo(bubbleHeight, expandedBubbleHeight, factor);
              timeAddedHeight = newBubbleHeight - bubbleHeight;
              bubbleHeight = newBubbleHeight;
            } else {
              bubbleWidth = fitBubbleWidth;
            }
            needAnimateTimeExpand = false;
            break;
          }
          default: {
            final int maxLineWidth = allowHorizontalExtend ? pRealContentMaxWidth : Math.min(pRealContentMaxWidth, bubbleWidth);
            if (needExpandBubble(bottomLineContentWidth, bubbleTimePartWidth, maxLineWidth)) {
              bubbleWidth = expandedBubbleWidth;
              timeAddedHeight = expandedBubbleHeight - bubbleHeight;
              bubbleHeight = expandedBubbleHeight;
            } else {
              final int extendedWidth = bottomLineContentWidth + bubbleTimePartWidth;
              //noinspection UnnecessaryLocalVariable
              final int fitBubbleWidth = Math.max(bubbleWidth, extendedWidth);
              bubbleWidth = fitBubbleWidth;
            }
            break;
          }
        }

        if (messageReactions.getVisibility() > 0f && useReactionBubbles()) {
          final int reactionsWidth = (int) messageReactions.getAnimatedWidth() + xReactionBubblePadding * 2 - getBubblePaddingLeft() - getBubblePaddingRight();
          final int reactionsFitBubbleWidth = Math.max(Math.max(defaultBubbleWidth, reactionsWidth), bubbleTimePartWidthWithoutPadding);

          final int reactionsFinalCorrectedWidth;
          final int reactionsFinalCorrectedHeight;

          reactionsFinalCorrectedWidth = MathUtils.fromTo(reactionsFitBubbleWidth,
            Math.max(expandedBubbleWidth, reactionsWidth),
            messageReactions.getTimeHeightExpand());
          reactionsFinalCorrectedHeight = MathUtils.fromTo(defaultBubbleHeight, expandedBubbleHeight, messageReactions.getTimeHeightExpand());
          timeAddedHeight = (int) MathUtils.fromTo(timeAddedHeight, getBubbleTimePartHeight() * messageReactions.getTimeHeightExpand(), messageReactions.getVisibility());
          needAnimateTimeExpand = false;

          bubbleWidth = MathUtils.fromTo(bubbleWidth, reactionsFinalCorrectedWidth, messageReactions.getVisibility());
          bubbleHeight = MathUtils.fromTo(bubbleHeight, reactionsFinalCorrectedHeight, messageReactions.getVisibility());
        }

        this.bubbleTimePartWidth = bubbleTimePartWidth;
      } else {
        this.bubbleTimePartWidth = 0;
      }

      if (timeExpandValue.getToFactor() != timeAddedHeight) {
        if (needAnimateChanges() && needAnimateTimeExpand) {
          timeExpandValue.animateTo(timeAddedHeight);
        } else {
          timeExpandValue.forceFactor(timeAddedHeight);
        }
      }

      bubbleHeight = (int) (bubbleHeight - timeAddedHeight + timeExpandValue.getFactor());

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

  public boolean getForceTimeExpandHeightByReactions () {
    return false;
  }

  private void notifyBubbleChanged () {
    int oldHeight = height;
    height = computeHeight();
    onBubbleHasChanged();
    if (height != oldHeight) {
      // FIXME?
      if (hasAnyTargetToInvalidate()) {
        manager.onMessageHeightChanged(getChatId(), getId(), oldHeight, height);
      }
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
      return Theme.getColor(ColorId.bubble_mediaOverlayText);
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
      return ColorId.bubble_mediaTime;
    } else {
      return ColorId.bubble_mediaOverlayText;
    }
  }

  private static final float COUNTER_ICON_MARGIN = 3f;
  private static final float COUNTER_ADD_MARGIN = 3f;

  protected void drawBubbleTimePart (Canvas c, MessageView view) {
    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());
    final int reactionsDrawMode = getReactionsDrawMode();

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
      iconColorId = ColorId.bubble_mediaTimeText;
      backgroundColor = getBubbleTimeColor();
      iconPaint = ticksPaint = ticksReadPaint = Paints.getBubbleTimePaint(textColor);
    } else { // Media
      iconColorId = ColorId.bubble_mediaOverlayText;
      textColor = Theme.getColor(ColorId.bubble_mediaOverlayText);
      backgroundColor = Theme.getColor(ColorId.bubble_mediaOverlay);
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
    int startY = bottomContentEdge - getBubbleTimePartHeight() - getBubbleTimePartOffsetY();
    if (commentButton.isVisible() && commentButton.isInline()) {
      startY -= commentButton.getAnimatedHeight(0, commentButton.getVisibility());
    }

    if (backgroundColor != 0) {
      startY -= Screen.dp(4f);
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(6f);
      rectF.set(startX - padding, startY, startX + innerWidth + padding, startY + Screen.dp(21f));
      c.drawRoundRect(rectF, Screen.dp(12f), Screen.dp(12f), Paints.fillingPaint(backgroundColor));
      startY -= Screen.dp(1f);
    }

    int counterY = startY + Screen.dp(11.5f);

    if (reactionsDrawMode == REACTIONS_DRAW_MODE_FLAT) {
      drawReactionsWithoutBubbles(c, startX, counterY);
      startX += reactionsCounterDrawable.getMinimumWidth() + Screen.dp(COUNTER_ADD_MARGIN) * messageReactions.getVisibility();
      reactionsCounter.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
      startX += reactionsCounter.getScaledWidth(Screen.dp(5));
    }

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
    if (replyCounter.getVisibility() > 0f) {
      replyCounter.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
      startX += replyCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
    }
    isPinned.draw(c, startX, counterY, Gravity.LEFT, 1f, view, iconColorId);
    startX += isPinned.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN));

    if (shouldShowEdited()) {
      if (isBeingEdited()) {
        Drawables.draw(c, Icons.getClockIcon(iconColorId), startX - Screen.dp(6f), startY + Screen.dp(4.5f) - Screen.dp(5f), iconPaint);
      } else {
        Drawables.draw(c, view.getSparseDrawable(R.drawable.baseline_edit_12, ColorId.NONE), startX, startY + Screen.dp(4.5f), iconPaint);
      }
      startX += Icons.getEditedIconWidth() + Screen.dp(2f);
    }

    if (translationStyleMode() == Settings.TRANSLATE_MODE_INLINE) {
      isTranslatedCounter.draw(c, startX, isTranslatedCounterY = counterY, Gravity.LEFT, 1f);
      isTranslatedCounterX = startX + Screen.dp(7);
      startX += isTranslatedCounter.getScaledWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN));
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
    return computeBubbleTimePartWidth(includePadding, false);
  }

  protected final int computeBubbleTimePartWidth (boolean includePadding, boolean isTarget) {
    final int reactionsDrawMode = getReactionsDrawMode();
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
    if (translationStyleMode() == Settings.TRANSLATE_MODE_INLINE) {
      width += isTranslatedCounter.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN), isTarget);
    }
    boolean isSending = isSending();
    if (getViewCountMode() == VIEW_COUNT_MAIN) {
      final float viewsWidth = viewCounter.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN), isTarget);
      final int clockWidth = Icons.getClockIconWidth() + Screen.dp(3f);
      if (isSending && (drawBubbleTimeOverContent() || !useBubble())) {
        width += clockWidth;
      } else {
        width += viewsWidth;
      }
      width += shareCounter.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN), isTarget);
    }
    if (replyCounter.getVisibility() > 0f) {
      width += replyCounter.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN), isTarget);
    }
    width += isPinned.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN), isTarget);
    if (reactionsDrawMode == REACTIONS_DRAW_MODE_FLAT) {
      width += reactionsCounterDrawable.getMinimumWidth() + messageReactions.getVisibility() * Screen.dp(3);
      width += reactionsCounter.getScaledOrTargetWidth(Screen.dp(COUNTER_ICON_MARGIN + COUNTER_ADD_MARGIN), isTarget);
    }
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
    if (useForward() || hasFooter()) {
      return true;
    }
    if (drawBubbleTimeOverContent()) {
      return !commentButton.isVisible() || !commentButton.isInline();
    }
    return false;
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

  public final void layoutAvatar (MessageView view, AvatarReceiver receiver) {
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

  public final void requestReply (DoubleImageReceiver receiver, ComplexReceiver textMediaReceiver) {
    if (replyData != null) {
      replyData.requestPreview(receiver, textMediaReceiver);
    } else {
      receiver.clear();
    }
  }

  public final void requestAvatar (AvatarReceiver receiver) {
    requestAvatar(receiver, false);
  }

  public final void requestAvatar (AvatarReceiver receiver, boolean force) {
    if (hasAvatar || force) {
      if (isSponsoredMessage()) {
        if (sponsoredMessage.sponsor.photo != null) {
          receiver.requestSpecific(tdlib, sponsoredMessage.sponsor.photo, AvatarReceiver.Options.NONE);
        } else {
          TdApi.MessageSponsor sponsor = sponsoredMessage.sponsor;
          switch (sponsor.type.getConstructor()) {
            case TdApi.MessageSponsorTypeBot.CONSTRUCTOR: {
              TdApi.MessageSponsorTypeBot bot = (TdApi.MessageSponsorTypeBot) sponsor.type;
              receiver.requestUser(tdlib, bot.botUserId, AvatarReceiver.Options.NONE);
              break;
            }
            case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR: {
              TdApi.MessageSponsorTypePublicChannel publicChannel = (TdApi.MessageSponsorTypePublicChannel) sponsor.type;
              receiver.requestChat(tdlib, publicChannel.chatId, AvatarReceiver.Options.NONE);
              break;
            }
            case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR:
            case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR: {
              receiver.requestPlaceholder(tdlib, sender.getPlaceholderMetadata(), AvatarReceiver.Options.NONE);
              break;
            }
            default:
              Td.assertMessageSponsorType_ce9e3245();
              throw Td.unsupported(sponsor.type);
          }
        }
      } else if (forceForwardedInfo()) {
        forwardInfo.requestAvatar(receiver);
      } else if (sender.isDemo()) {
        receiver.requestPlaceholder(tdlib, sender.getPlaceholderMetadata(), AvatarReceiver.Options.NONE);
      } else {
        receiver.requestMessageSender(tdlib, sender.toSender(), AvatarReceiver.Options.NONE);
      }
    } else {
      receiver.clear();
    }
  }

  ComplexReceiver currentComplexReceiver;

  public final void requestReactions (ComplexReceiver complexReceiver) {
    currentComplexReceiver = complexReceiver;
    messageReactions.setReceiversPool(complexReceiver);
    computeQuickButtons();
  }

  public final void requestCommentsResources (ComplexReceiver complexReceiver, boolean isUpdate) {
    if (commentButton != null) {
      commentButton.requestResources(complexReceiver, isUpdate);
    }
  }

  public final void requestReactionsResources (ComplexReceiver complexReceiver, boolean isUpdate) {
    if (messageReactions != null) {
//      messageReactions.requestAvatarFiles(complexReceiver, isUpdate);
    }
  }


  public final void requestAllTextMedia (MessageView view) {
    requestTextMedia(view.getTextMediaReceiver());
    requestAuthorTextMedia(view.getEmojiStatusReceiver());

    if (footerText != null) {
      footerText.requestMedia(view.getFooterTextMediaReceiver(true));
    } else {
      ComplexReceiver receiver = view.getFooterTextMediaReceiver(false);
      if (receiver != null) {
        receiver.clear();
      }
    }
  }

  public final void requestAuthorTextMedia (ComplexReceiver textMediaReceiver) {
    if (hAuthorEmojiStatus != null) {
      hAuthorEmojiStatus.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    // override in children
    textMediaReceiver.clear();
  }

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

  @Nullable
  public TdApi.ChatEvent getEvent () {
    return event != null ? event.event : null;
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
    return TD.getViewCount(msg.interactionInfo);
  }

  public final int getReplyCount () {
    return TD.getReplyCount(getOldestMessage().interactionInfo);
  }

  public final @Nullable TdApi.MessageReplyInfo getReplyInfo () {
    return TD.getReplyInfo(getOldestMessage().interactionInfo);
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

  public boolean isFakeMessage () {
    //noinspection WrongConstant
    if (msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR) {
      return true;
    }
    return isDemoChat();
  }

  public final void getIds (@NonNull LongSet ids, long afterMessageId, long beforeMessageId) {
    if (isFakeMessage()) {
      return;
    }
    synchronized (this) {
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        ids.ensureCapacity(ids.size() + combinedMessages.size());
        for (TdApi.Message msg : combinedMessages) {
          if (msg.id != 0 && ((afterMessageId == 0 && beforeMessageId == 0) || (msg.id > afterMessageId && msg.id < beforeMessageId))) {
            ids.add(msg.id);
          }
        }
        return;
      }
    }
    if (msg.id != 0 && ((afterMessageId == 0 && beforeMessageId == 0) || (msg.id > afterMessageId && msg.id < beforeMessageId))) {
      ids.add(msg.id);
    }
  }

  public final void getIds (@NonNull LongSet ids) {
    getIds(ids, 0, 0);
  }

  public final long[] getIds () {
    LongSet ids = new LongSet(getMessageCount());
    getIds(ids, 0, 0);
    long[] result = ids.toArray();
    Arrays.sort(result);
    return result;
  }

  public final boolean isMessageThreadRoot () {
    return canGetMessageThread() && (isChannel() || (isMessageThread() && isThreadHeader()) || (msg.messageThreadId != 0 && msg.replyTo == null));
  }

  public final long getMessageThreadId () {
    return getOldestMessage().messageThreadId;
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

  public final boolean containsUnreadReactions () {
    synchronized (this) {
      if (combinedMessages != null) {
        for (int i = combinedMessages.size() - 1; i >= 0; i--) {
          if (combinedMessages.get(i).unreadReactions != null && combinedMessages.get(i).unreadReactions.length > 0) {
            return true;
          }
        }
      }
    }
    return msg.unreadReactions != null && msg.unreadReactions.length > 0;
  }

  public final void readReactions () {
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message message : combinedMessages) {
          message.unreadReactions = new TdApi.UnreadReaction[0];
        }
      }
      msg.unreadReactions = new TdApi.UnreadReaction[0];
    }
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
    if (msg.mediaAlbumId == 0 || msg.mediaAlbumId != message.mediaAlbumId || !Td.equalsTo(msg.selfDestructType, message.selfDestructType) || isHot() || isEventLog() || isSponsoredMessage()) {
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
    computeQuickButtons();
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
          computeQuickButtons();
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
    if (isSponsoredMessage()) {
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
        result = null; //Lang.getString(R.string.message_channelSign);
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
    return (event != null && event.event.date != 0) ? event.event.date : msg.schedulingState != null ? (msg.schedulingState.getConstructor() == TdApi.MessageSchedulingStateSendAtDate.CONSTRUCTOR ? ((TdApi.MessageSchedulingStateSendAtDate) msg.schedulingState).sendDate : 0) : msg.date;
  }

  public final boolean isSending () {
    return msg.sendingState != null && msg.sendingState.getConstructor() == TdApi.MessageSendingStatePending.CONSTRUCTOR && !tdlib.qack().isMessageAcknowledged(msg.chatId, msg.id);
  }

  public final boolean isPsa () {
    return msg.forwardInfo != null && !StringUtils.isEmpty(msg.forwardInfo.publicServiceAnnouncementType) && !sender.isUser();
  }

  public final boolean useMediaBubbleReactions () {
    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());

    return (isWhite && !isTransparent);
  }

  public final boolean useStickerBubbleReactions () {
    boolean isTransparent = !useBubble() || useCircleBubble();
    boolean isWhite = isTransparent || (drawBubbleTimeOverContent() && !useForward());
    return (isWhite && isTransparent);
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
            errors.add(TD.toErrorString(failed.error));
          }
        }
      } else {
        if (msg.sendingState instanceof TdApi.MessageSendingStateFailed) {
          TdApi.MessageSendingStateFailed failed = (TdApi.MessageSendingStateFailed) msg.sendingState;
          errors.add(TD.toErrorString(failed.error));
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

  public final boolean canGetMessageThread () {
    return getOldestMessage().canGetMessageThread;
  }

  public final boolean canGetAddedReactions () {
    synchronized (this) {
      if (combinedMessages != null) {
        for (TdApi.Message message : combinedMessages) {
          if (message.canGetAddedReactions) {
            return true;
          }
        }
      }
    }

    return msg.canGetAddedReactions;

    //return !isChannel() && messageReactions.getTotalCount() > 0 && (msg.forwardInfo == null || msg.forwardInfo.origin.getConstructor() != TdApi.MessageForwardOriginChannel.CONSTRUCTOR);
  }

  public final boolean canBeDeletedOnlyForSelf () {
    return msg.canBeDeletedOnlyForSelf;
  }

  public final boolean canBeDeletedForEveryone () {
    return msg.canBeDeletedForAllUsers;
  }

  public boolean canBeSelected () {
    return (!isNotSent() || canResend()) && (flags & FLAG_UNSUPPORTED) == 0 && allowInteraction() && !isSponsoredMessage() && !messagesController().inSearchMode();
  }

  public boolean canBePinned () {
    return !isNotSent() && allowInteraction() && !isSponsoredMessage();
  }

  public boolean canEditText () {
    return msg.canBeEdited && TD.canEditText(msg.content) && allowInteraction() && messagesController().canWriteMessages();
  }

  public boolean canBeForwarded () {
    return msg.canBeForwarded && (msg.content.getConstructor() != TdApi.MessageLocation.CONSTRUCTOR || ((TdApi.MessageLocation) msg.content).expiresIn == 0) && !isEventLog();
  }

  public boolean canBeReacted () {
    return !isSponsoredMessage() && !isEventLog() && !(msg.content instanceof TdApi.MessageCall) && !Td.isEmpty(messageAvailableReactions);
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
    ThreadInfo messageThread = messagesController().getMessageThread();
    if (chat == null) {
      chat = tdlib.chat(msg.chatId);
      setChatData(chat, messageThread);
    }
    long lastReadMessageId;
    if (messageThread != null) {
      lastReadMessageId = msg.isOutgoing ? messageThread.getLastReadOutboxMessageId() : messageThread.getLastReadInboxMessageId();
    } else if (chat != null) {
      lastReadMessageId = msg.isOutgoing ? chat.lastReadOutboxMessageId : chat.lastReadInboxMessageId;
    } else {
      return false;
    }
    return lastReadMessageId < getBiggestId();
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
    return BitwiseUtils.hasFlag(flags, FLAG_BEING_ADDED);
  }

  public boolean canMarkAsViewed () {
    return msg.id != 0 && msg.chatId != 0 && (flags & FLAG_VIEWED) == 0;
  }

  public boolean markAsViewed () {
    boolean result = false;

    if (canMarkAsViewed()) {
      flags |= FLAG_VIEWED;
      if (msg.containsUnreadMention) {
        highlight(true);
      }
      result = true;
    }
    if (containsUnreadReactions()) {
      if (!BitwiseUtils.hasFlag(flags, FLAG_IGNORE_REACTIONS_VIEW)) {
        highlightUnreadReactions();
        highlight(true);
        tdlib.ui().postDelayed(() -> {
          flags = BitwiseUtils.setFlag(flags, FLAG_IGNORE_REACTIONS_VIEW, false);
        }, 500L);
        tdlib().ui().post(this::readReactions);
      }
      flags |= FLAG_IGNORE_REACTIONS_VIEW;
      result = true;
    }
    return result;
  }

  public boolean needRefreshViewCount () {
    return !isSponsoredMessage() && viewCounter != null && !isSending();
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

  public boolean isUserChat () {
    return ChatId.isUserChat(getChatId());
  }

  public long getChannelId () {
    return ChatId.toSupergroupId(msg.chatId);
  }

  private void setChatData (TdApi.Chat chat, @Nullable ThreadInfo messageThread) {
    this.chat = chat;
    int flags = this.flags;
    flags = BitwiseUtils.setFlag(flags, FLAG_NO_UNREAD, needNoUnread());
    flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, isSelfChat());
    this.flags = flags;

    if (isOutgoing() && !isSending()) {
      setUnread(messageThread != null ? messageThread.getLastReadOutboxMessageId() : chat.lastReadOutboxMessageId);
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
    return ((flags & FLAG_SELF_CHAT) != 0 || isChannelAutoForward() || isRepliesChat()) && msg.forwardInfo != null && msg.forwardInfo.fromChatId != 0 && msg.forwardInfo.fromMessageId != 0 && msg.forwardInfo.fromChatId != msg.chatId;
  }

  public final void openSourceMessage () {
    if (msg.forwardInfo != null) {
      if (isRepliesChat()) {
        MessageId replyMessageId = new MessageId(msg.forwardInfo.fromChatId, msg.forwardInfo.fromMessageId);
        MessageId replyToMessageId = Td.toMessageId(msg.replyTo);
        openMessageThread(replyMessageId, replyToMessageId);
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
    return BitwiseUtils.hasFlag(flags, FLAG_HIDE_MEDIA);
  }

  public final void setMediaVisible (MediaItem media, boolean isVisible) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_HIDE_MEDIA, !isVisible);
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
    return isOutgoing() && isHotOpened();
  }

  public boolean isHotOpened () {
    if (msg.selfDestructType != null && msg.selfDestructType.getConstructor() == TdApi.MessageSelfDestructTypeTimer.CONSTRUCTOR) {
      TdApi.MessageSelfDestructTypeTimer timer = (TdApi.MessageSelfDestructTypeTimer) msg.selfDestructType;
      return msg.selfDestructIn < timer.selfDestructTime;
    }
    return false;
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
    return Td.isListenedOrViewed(msg.content);
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
    double prevTtl = msg.selfDestructIn;
    hotTimerStart = now;
    msg.selfDestructIn = Math.max(0, prevTtl - (double) elapsed / 1000.0d);
    boolean secondsChanged = Math.round(prevTtl) != Math.round(msg.selfDestructIn);
    onHotInvalidate(secondsChanged);
    if (hotListener != null) {
      hotListener.onHotInvalidate(secondsChanged);
    }
    if (needHotTimer() && hotTimerStart != 0 && msg.selfDestructIn > 0) {
      HotHandler hotHandler = getHotHandler();
      hotHandler.sendMessageDelayed(Message.obtain(hotHandler, HotHandler.MSG_HOT_CHECK, this), HOT_CHECK_DELAY);
    }
  }

  public float getHotExpiresFactor () {
    if (msg.selfDestructType != null && msg.selfDestructType.getConstructor() == TdApi.MessageSelfDestructTypeTimer.CONSTRUCTOR) {
      TdApi.MessageSelfDestructTypeTimer timer = (TdApi.MessageSelfDestructTypeTimer) msg.selfDestructType;
      return (float) (msg.selfDestructIn / timer.selfDestructTime);
    }
    return 0f;
  }

  public String getHotTimerText () {
    return TdlibUi.getDuration((int) Math.round(msg.selfDestructIn), TimeUnit.SECONDS, false);
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

    computeQuickButtons();
    return replaceRequired ? MESSAGE_REPLACE_REQUIRED : getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED;
  }

  private void updateInteractionInfo (boolean allowAnimation) {
    TdApi.MessageInteractionInfo interactionInfo = msg.interactionInfo;
    boolean animated = allowAnimation && needAnimateChanges();
    if (viewCounter != null) {
      viewCounter.setCount(TD.getViewCount(interactionInfo), animated && getViewCountMode() != VIEW_COUNT_HIDDEN);
    }
    int commentButtonViewMode = getCommentButtonViewMode();
    commentButton.setViewMode(commentButtonViewMode, animated);
    if (commentButtonViewMode != TGCommentButton.VIEW_MODE_HIDDEN) {
      if (isRepliesChat()) {
        commentButton.showAsViewInChat(animated);
      } else {
        commentButton.setReplyInfo(getReplyInfo(), animated);
      }
      replyCounter.hide(animated);
    } else {
      replyCounter.setCount(isThreadHeader() || isChannel() ? 0 : getReplyCount(), animated);
    }
    shareCounter.setCount(interactionInfo != null ? interactionInfo.forwardCount : 0, animated);
    isPinned.showHide(isPinned(), animated);

    if (combinedMessages != null) {
      messageReactions.setReactions(combinedMessages);
    } else {
      messageReactions.setReactions(interactionInfo != null ? interactionInfo.reactions : null);
    }
    messageReactions.updateCounterAnimators(animated);
    if (allowAnimation) {
      buildReactions(animated);
    }
    if (reactionsCounter != null) {
      int count = messageReactions.getTotalCount();
      if (tdlib.isUserChat(msg.chatId) && messageReactions.getReactions() != null && (count == 1 || messageReactions.getReactions().length > 1)) {
        count = 0;
      }
      reactionsCounter.setCount(count, !messageReactions.hasChosen(), animated);
    }

    if (shrinkedReactionsCounter != null) {
      shrinkedReactionsCounter.showHide(messageReactions.getTotalCount() > 0, animated);
      shrinkedReactionsCounter.setMuted(!messageReactions.hasChosen(), animated);
    }

    if (animated) {
      startReactionAnimationIfNeeded();
    }
    if (allowAnimation) {
      notifyBubbleChanged();
      layoutInfo();
    }
  }

  public TGReactions getMessageReactions () {
    return messageReactions;
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
    dst.canGetAddedReactions = src.canGetAddedReactions;
    dst.canGetStatistics = src.canGetStatistics;
    dst.canGetViewers = src.canGetViewers;
    dst.canReportReactions = src.canReportReactions;
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

  public boolean setMessageUnreadReactions (long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions) {
    boolean changed;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        combinedMessages.get(i).unreadReactions = unreadReactions;
        changed = true; // i == combinedMessages.size() - 1 || i == 0;
      } else if (i == MESSAGE_INDEX_SELF) {
        msg.unreadReactions = unreadReactions;
        changed = true;
      } else {
        return false;
      }
    }
    return changed;
  }

  public boolean setMessageInteractionInfo (long messageId, TdApi.MessageInteractionInfo interactionInfo) {
    boolean changed;
    synchronized (this) {
      int i = indexOfMessageInternal(messageId);
      if (i >= 0) {
        combinedMessages.get(i).interactionInfo = interactionInfo;
        changed = true; //i == combinedMessages.size() - 1;
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
      this.buildReactions(needAnimateChanges());
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

  @NonNull
  private String getBadgeText () {
    if (isBelowHeader()) {
      return Lang.getString(R.string.DiscussionStart);
    }
    if (isFirstUnread()) {
      return Lang.getString(R.string.NewMessages);
    }
    return "";
  }

  private void updateShowBadge () {
    boolean showBadge = isFirstUnread() || isBelowHeader() && !messagesController().areScheduledOnly();
    flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_BADGE, showBadge);
  }

  private boolean updateBadgeText () {
    String badgeText = getBadgeText();
    if (uBadge == null || !uBadge.text.equals(badgeText)) {
      uBadge = new Letters(badgeText);
      return true;
    }
    return false;
  }

  public void setShowUnreadBadge (boolean show) {
    flags = BitwiseUtils.setFlag(flags, MESSAGE_FLAG_FIRST_UNREAD, show);
    updateShowBadge();
    updateBadgeText();
    if (BitwiseUtils.hasFlag(flags, FLAG_LAYOUT_BUILT)) {
      rebuildLayout();
      requestLayout();
      invalidate();
    }
  }

  public boolean hasBadge () {
    return BitwiseUtils.hasFlag(flags, FLAG_SHOW_BADGE);
  }

  public boolean hasUnreadBadge () {
    return isFirstUnread() && hasBadge();
  }

  public boolean isFirstUnread () {
    return BitwiseUtils.hasFlag(flags, MESSAGE_FLAG_FIRST_UNREAD);
  }

  public boolean isBelowHeader () {
    return BitwiseUtils.hasFlag(flags, MESSAGE_FLAG_BELOW_HEADER);
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
    if (useForward()) {
      return xBubblePadding + xBubblePaddingSmall;
    }
    if (drawBubbleTimeOverContent() && commentButton.isVisible() && commentButton.isInline()) {
      return Math.round(getBubbleContentPadding() * (1f - commentButton.getVisibility()));
    }
    return getBubbleContentPadding();
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
    messageReactions.performDestroy();
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
  private float selectionFactor;
  private SelectionInfo selection;

  public int getSelectionColor (float factor) {
    final boolean useBubbles = useBubbles();
    if (useBubbles) {
      return ColorUtils.alphaColor(factor, ColorUtils.fromToArgb(Theme.getColor(ColorId.bubble_messageSelection), Theme.getColor(ColorId.bubble_messageSelectionNoWallpaper), manager.controller().wallpaper().getBackgroundTransparency()));
    } else {
      // manager.controller().wallpaper().getOverlayColor(ColorId.chatTransparentColor)
      final int color = Theme.chatSelectionColor();
      return ColorUtils.alphaColor(factor, ColorUtils.compositeColor(Theme.getColor(ColorId.chatBackground), color));
    }
    /*final int color = useBubbles ? Theme.getColor(ColorId.messageBubbleSelection) : Theme.chatSelectionColor();
    return U.alphaColor(factor, useBubbles ? color : U.compositeColor(Theme.getColor(ColorId.chatPlainBackground), color));*/
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
    if ((sizeChanged || (counter == reactionsCounter && !useBubbles())) && BitwiseUtils.hasFlag(flags, FLAG_LAYOUT_BUILT)) {
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
      } else if (counter == replyCounter || counter == shareCounter || counter == shrinkedReactionsCounter || counter == isPinned || counter == isTranslatedCounter) {
        if (useBubbles() || (flags & FLAG_HEADER_ENABLED) != 0) {
          layoutInfo();
        }
      } else if (counter == reactionsCounter) {
        layoutInfo();
      }
    }
    if (counter == isTranslatedCounter && isTranslatedCounter.getVisibility() == 1f) {
      boolean force = !useBubbles() && Settings.instance().needTutorial(Settings.TUTORIAL_SELECT_LANGUAGE_INLINE_MODE);
      if (force) {
        Settings.instance().markTutorialAsShown(Settings.TUTORIAL_SELECT_LANGUAGE_INLINE_MODE);
      }
      checkSelectLanguageWarning(force);
    }
    if (UI.inUiThread()) { // FIXME remove this after reworking combineWith method
      invalidate();
    } else {
      postInvalidate();
    }
  }

  @Override
  public void onSizeChanged () {
    if (UI.inUiThread()) { // FIXME remove this after reworking combineWith method
      invalidate();
    } else {
      postInvalidate();
    }
  }

  @Override
  public void onInvalidateMedia (TGAvatars avatars) {
    performWithViews(view -> requestReactionsResources(view.getReactionAvatarsReceiver(), true));
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
        case ANIMATOR_READY_LEFT: {
          if (this.quickLeftReadyFactor != factor) {
            this.quickLeftReadyFactor = factor;
            invalidate();
          }
          break;
        }
        case ANIMATOR_READY_RIGHT: {
          if (this.quickRightReadyFactor != factor) {
            this.quickRightReadyFactor = factor;
            invalidate();
          }
          break;
        }
        case ANIMATOR_QUICK_VERTICAL_LEFT: {
          if (this.leftActionVerticalFactor != factor) {
            this.leftActionVerticalFactor = factor;
            invalidate();
          }
          break;
        }
        case ANIMATOR_QUICK_VERTICAL_RIGHT: {
          if (this.rightActionVerticalFactor != factor) {
            this.rightActionVerticalFactor = factor;
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

  private @Nullable String highlightedText = null;
  private final Highlight.Pool searchResultsHighlightPool = new Highlight.Pool();

  private void setHighlightedText (@Nullable String text) {
    if (!StringUtils.equalsOrBothEmpty(text, highlightedText)) {
      searchResultsHighlightPool.clear();
      this.highlightedText = text;
      onUpdateHighlightedText();
      invalidate();
    }
  }

  protected final @Nullable Highlight getHighlightedText (int key, String in) {
    Highlight highlight = Highlight.valueOf(in, highlightedText);
    if (highlight == null) return null;

    highlight.setCustomColorSet(getSearchHighlightColorSet());
    int mostRelevantKey = searchResultsHighlightPool.getMostRelevantHighlightKey();
    searchResultsHighlightPool.add(key, highlight);
    if (mostRelevantKey != Highlight.Pool.KEY_NONE && mostRelevantKey != searchResultsHighlightPool.getMostRelevantHighlightKey()) {
      UI.post(this::onUpdateHighlightedText);
      android.util.Log.i("HIGHLIGHT", "UPDATE");
    }

    return searchResultsHighlightPool.isMostRelevant(key) ? highlight : null;
  }

  public void checkHighlightedText () {
    if (messagesController().isMessageFound(getMessage(getSmallestId()))) {
      setHighlightedText(manager.controller().getLastMessageSearchQuery());
    } else {
      setHighlightedText(null);
    }
  }

  // Override
  protected void onUpdateHighlightedText () {

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

  private float translationOption;
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

    if (after != null && ((flags & FLAG_READY_QUICK_RIGHT) == 0) && (flags & FLAG_READY_QUICK_LEFT) == 0) {
      vibrate();
    }

    if (!needDelay) {
      U.run(after);
    }
    if (quickLeftReadyAnimator != null) {
      quickLeftReadyAnimator.cancel();
    }
    if (quickRightReadyAnimator != null) {
      quickRightReadyAnimator.cancel();
    }
    if (leftActionVerticalAnimator != null) {
      leftActionVerticalAnimator.cancel();
    }
    if (rightActionVerticalAnimator != null) {
      rightActionVerticalAnimator.cancel();
    }

    final boolean[] status = needDelay && after != null ? new boolean[1] : null;
    final float fromTranslation = translation;
    final float fromTranslationOption = translationOption;
    final float fromReplyReadyFactor = quickRightReadyFactor;
    final float fromShareReadyFactor = quickLeftReadyFactor;
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
        translate(fromTranslation * (1f - factor), fromTranslationOption, false);
        if (view instanceof MessageViewGroup) {
          ((MessageViewGroup) view).setSwipeTranslation(translation);
        }
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        setQuickActionVerticalFactor(false, 0f, false, true);
        setQuickActionVerticalFactor(true, 0f, false, true);
        translate(0f, 0f, false);
        flags &= ~FLAG_IGNORE_SWIPE;
        /*if (needDelay) {
          U.run(after);
        }*/
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    animator.animateTo(1f);
  }

  private static final float BUBBLE_MOVE_MAX = 64f;
  public static final float BUBBLE_MOVE_THRESHOLD = 42f;
  public static final float QUICK_ACTION_VERTICAL_SWIPE_SIZE = 80f;

  private static final int ANIMATOR_READY_LEFT = -4;
  private static final int ANIMATOR_READY_RIGHT = -5;
  private static final int ANIMATOR_QUICK_VERTICAL_LEFT = -6;
  private static final int ANIMATOR_QUICK_VERTICAL_RIGHT = -7;

  public void vibrate () {
    UI.hapticVibrate(findCurrentView(), true);
  }

  private float quickRightReadyFactor, quickLeftReadyFactor;
  private FactorAnimator quickRightReadyAnimator, quickLeftReadyAnimator;

  private float leftActionVerticalFactor, rightActionVerticalFactor;
  private FactorAnimator leftActionVerticalAnimator, rightActionVerticalAnimator;

  private void setQuickActionVerticalFactor (boolean isLeft, float factor, boolean animated, boolean invalidate) {
    FactorAnimator verticalAnimator = isLeft ? leftActionVerticalAnimator : rightActionVerticalAnimator;
    float verticalFactor = isLeft ? leftActionVerticalFactor : rightActionVerticalFactor;
    if (animated) {
      if (verticalAnimator == null) {
        if (verticalFactor == factor) {
          return;
        }
        verticalAnimator = new FactorAnimator(isLeft ? ANIMATOR_QUICK_VERTICAL_LEFT : ANIMATOR_QUICK_VERTICAL_RIGHT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, useBubbles() ? 110L : 220L, verticalFactor);
        if (isLeft) {
          leftActionVerticalAnimator = verticalAnimator;
        } else {
          rightActionVerticalAnimator = verticalAnimator;
        }
      }
      if (verticalAnimator.getToFactor() == factor) {
        return;
      }
      verticalAnimator.animateTo(factor);
      vibrate();
    } else {
      if (verticalAnimator != null) {
        verticalAnimator.forceFactor(factor);
      }
      if (verticalFactor != factor) {
        if (isLeft) {
          leftActionVerticalFactor = factor;
        } else {
          rightActionVerticalFactor = factor;
        }
        if (invalidate) {
          invalidate();
        }
      }
    }
  }

  private void setReadyFactor (boolean isLeft, float factor, boolean animated, boolean invalidate) {
    FactorAnimator readyAnimator = isLeft ? quickLeftReadyAnimator : quickRightReadyAnimator;
    float readyFactor = isLeft ? quickLeftReadyFactor : quickRightReadyFactor;
    if (animated) {
      if (readyAnimator == null) {
        if (readyFactor == factor) {
          return;
        }
        readyAnimator = new FactorAnimator(isLeft ? ANIMATOR_READY_LEFT : ANIMATOR_READY_RIGHT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 110l, readyFactor);
        if (isLeft) {
          quickLeftReadyAnimator = readyAnimator;
        } else {
          quickRightReadyAnimator = readyAnimator;
        }
      }
      readyAnimator.animateTo(factor);
    } else {
      if (readyAnimator != null) {
        readyAnimator.forceFactor(factor);
      }
      if (readyFactor != factor) {
        if (isLeft) {
          this.quickLeftReadyFactor = factor;
        } else {
          this.quickRightReadyFactor = factor;
        }
        if (invalidate) {
          invalidate();
        }
      }
    }
  }

  public void translate (float dx, float verticalPosition, boolean bySwipe) {
    if (bySwipe && ((flags & FLAG_IGNORE_SWIPE) != 0)) {
      return;
    }

    boolean useBubbles = useBubbles();

    if (useBubbles) {
      int bound = Screen.dp(BUBBLE_MOVE_MAX);
      dx = Math.max(-bound, Math.min(bound, dx));
    }

    if (translation == dx && translationOption == verticalPosition) {
      return;
    }

    translation = dx;
    context().reactionsOverlayManager().invalidate();
    translationOption = verticalPosition;
    if (translation == 0f) {
      translationOption = 0f;
      setQuickActionVerticalFactor(true, 0, false, true);
      setQuickActionVerticalFactor(false, 0, false, true);
    } else {
      setQuickActionVerticalFactor(dx >= 0, Math.round(translationOption), true, true);
    }

    if (useBubbles) {
      if (dx >= 0) {
        flags &= ~FLAG_READY_QUICK_RIGHT;
        setReadyFactor(false, 0f, false, false);
      }
      if (dx <= 0) {
        flags &= ~FLAG_READY_QUICK_LEFT;
        setReadyFactor(true, 0f, false, false);
      }
      int threshold = Screen.dp(BUBBLE_MOVE_THRESHOLD);
      if (dx >= threshold && (flags & FLAG_READY_QUICK_LEFT) == 0) {
        flags |= FLAG_READY_QUICK_LEFT;
        vibrate();
        setReadyFactor(true, 1f, true, true);
      } else if (dx <= -threshold && (flags & FLAG_READY_QUICK_RIGHT) == 0) {
        flags |= FLAG_READY_QUICK_RIGHT;
        vibrate();
        setReadyFactor(false, 1f, true, true);
      }
    }

    invalidate(true);
  }

  public float getQuickActionVerticalFactor (boolean isLeft) {
    return (isLeft ? leftActionVerticalFactor : rightActionVerticalFactor) + getQuickDefaultPosition(isLeft);
  }

  public void drawTranslate (View view, Canvas c) {
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

    boolean isLeft = x > 0;
    ArrayList<SwipeQuickAction> actions = isLeft ? getLeftQuickReactions() : getRightQuickReactions();
    float verticalFactor = getQuickActionVerticalFactor(isLeft);
    float readyFactor = isLeft ? quickLeftReadyFactor : quickRightReadyFactor;

    if (useBubbles()) {
      if (translation == 0f) {
        return;
      }

      int height = bottomContentEdge - topContentEdge;
      float shrinkFactor = 0.5f; // 1f - MathUtils.clamp( ((float)(height - Screen.dp(40))) / Screen.dp(75));
      int shrinkSize = (int) (Screen.dp(8) * shrinkFactor);
      int offset = Screen.dp(32) - shrinkSize;
      int positionOffset = -(int) (verticalFactor * offset);

      int startY = topContentEdge + (bottomContentEdge - topContentEdge) / 2 + positionOffset;
      float cx = translation > 0f ? translation / 2 : view.getMeasuredWidth() + translation / 2;
      for (int a = 0; a < actions.size(); a++) {
        SwipeQuickAction action = actions.get(a);
        float positionFactor = getTranslatePositionFactor(isLeft, a);
        float cy = startY + offset * a;
        float closenessToBorder = 1f - MathUtils.clamp(Math.min(cy - topContentEdge, bottomContentEdge - cy) / Screen.dp(12));
        float maxAlpha = Math.max(positionFactor, 1f - closenessToBorder);
        float ncx = cx + shrinkSize * (1f - positionFactor);
        Drawable icon = (!action.isQuickReaction || nextSetReactionAnimation == null) ? action.icon : null;
        drawTranslateRound(c, ncx, cy, readyFactor, maxAlpha, positionFactor, icon);
      }
      return;
    }

    int startY, endY;
    if (useBubbles()) {
      startY = topContentEdge;
      endY = bottomContentEdge;
    } else {
      startY = getHeaderPadding() - xHeaderPadding;
      endY = findBottomEdge();
    }
    final int height = endY - startY;
    boolean rtl = Lang.rtl();
    float textWidth;
    if (rtl != (x > 0)) {
      textWidth = xQuickShareWidth;
    } else {
      textWidth = xQuickReplyWidth;
    }

    int positionOffset = -(int) (verticalFactor * height);
    c.save();
    c.clipRect(0, startY, view.getMeasuredWidth(), endY);
    for (int a = 0; a < actions.size(); a++) {
      SwipeQuickAction action = actions.get(a);
      float positionFactor = getTranslatePositionFactor(x > 0, a);
      Drawable icon = (!action.isQuickReaction || nextSetReactionAnimation == null) ? action.icon : null;
      drawTranslateRect(c,
        x, startY + positionOffset + height * a,
        view.getMeasuredWidth(), height, positionFactor,
        quickColor, icon, action.text, (int) textWidth,
        height > Screen.dp(256) ? (int) (mInitialTouchY - getHeaderPadding() + xHeaderPadding) : (height / 2)
      );
    }
    c.restore();
  }

  private float getTranslatePositionFactor (boolean isLeft, int position) {
    return (1f - (MathUtils.clamp(Math.abs(getQuickActionVerticalFactor(isLeft) - position))));
  }

  private void drawTranslateRound (Canvas c, float cx, float cy, float readyFactor, float maxAlpha, float positionFactor, Drawable icon) {
    float threshold = Screen.dp(BUBBLE_MOVE_THRESHOLD);

    float alpha = Math.min(MathUtils.clamp(Math.min(Math.abs(translation / threshold), .6f + 4f * positionFactor)) * readyFactor, maxAlpha);
    float scale = (.6f + .4f * Math.min(readyFactor, positionFactor));
    if (alpha == 0f) {
      return;
    }

    float darkFactor = Theme.getDarkFactor() * (1f - manager.controller().wallpaper().getBackgroundTransparency());
    float radius = Screen.dp(16f) * scale;
    float radius2 = Screen.dp(15.5f) * scale;

    if (darkFactor > 0f) {
      c.drawCircle(cx, cy, radius, Paints.getProgressPaint(ColorUtils.alphaColor(alpha * darkFactor, Theme.headerColor()), Screen.dp(1f)));
    }
    c.drawCircle(cx, cy, radius, Paints.strokeSmallPaint(ColorUtils.alphaColor(alpha * 0.05f, getBubbleButtonTextColor())));
    c.drawCircle(cx, cy, radius2, Paints.fillingPaint(ColorUtils.alphaColor(alpha, getBubbleButtonBackgroundColor())));

    if (icon != null) {
      c.save();
      c.scale((Lang.rtl() ? -.8f : .8f) * scale, .8f * scale, cx, cy);
      icon.setAlpha((int) (alpha * 255));
      Paint paint = Paints.getInlineBubbleIconPaint(ColorUtils.alphaColor(alpha, getBubbleButtonTextColor()));
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, paint);
      c.restore();
    }
  }

  private void drawTranslateRect (Canvas c, float x, float y, int width, int height, float positionFactor, int quickColor, Drawable icon, String text, int textWidth, int textY) {
    final Paint iconPaint = Paints.getInlineBubbleIconPaint(
      ColorUtils.alphaColor((float) mQuickText.getAlpha() / 255f,
      Theme.getColor(ColorId.messageSwipeContent)));

    final int iconWidth = icon != null ? icon.getMinimumWidth() : 0;
    final int iconHeight = icon != null ? icon.getMinimumHeight() : 0;

    final int check = Screen.dp(12f) + iconHeight;
    final float iconX, textX;
    final int cy = (int) (y + (int) ((float) height * .5f));
    final boolean rtl = Lang.rtl();

    if (x > 0) {
      c.drawRect(0, y, x, y + height, Paints.fillingPaint(ColorUtils.alphaColor(positionFactor, quickColor)));
      iconX = x - xQuickPadding - iconWidth;
      textX = iconX - xQuickTextPadding - textWidth;
    } else {
      int endX = width;
      c.drawRect(endX + x, y, endX, y + height, Paints.fillingPaint(ColorUtils.alphaColor(positionFactor, quickColor)));
      iconX = endX + x + xQuickPadding;
      textX = iconX + iconWidth + textWidth;
    }
    if (check > height) {
      c.save();
      float scale = (float) height / (float) check;
      c.scale(scale, scale, iconX, cy);
    }
    if (rtl) {
      c.save();
      c.scale(-1f, 1f, iconX + iconWidth / 2f, cy);
    }
    if (icon != null) {
      icon.setAlpha(255);
      Drawables.draw(c, icon, iconX, y + textY - (int) ((float) iconHeight * .5f) + Screen.dp((x > 0) ? 0f : 0.5f), iconPaint);
    }
    if (rtl) {
      c.restore();
    }
    c.drawText(text, textX, y + textY + xQuickTextOffset, mQuickText);
    if (check > height) {
      c.restore();
    }
  }

  public void setDismiss (float dismiss) {
    if (this.dismissFactor != dismiss) {
      this.dismissFactor = dismiss;
      if (dismiss == 0f) {
        translationOption = 0f;
        setQuickActionVerticalFactor(true, 0f, false, true);
        setQuickActionVerticalFactor(false, 0f, false, true);
      }
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
      dismissAnimator = new FactorAnimator(ANIMATOR_DISMISS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 60L, this.dismissFactor);
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
    swipeHelper.reset();

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

  private TdApiExt.MessageChatEvent event;
  private long eventMessageId;

  protected final TGMessage setIsEventLog (TdApiExt.MessageChatEvent event, long messageId) {
    flags |= FLAG_EVENT_LOG;
    this.event = event;
    this.time = genTime();
    setDate(genDate());
    return this;
  }

  private boolean needHideEventDate () {
    //noinspection WrongConstant
    return (event != null & event.hideDate) || (msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR && ((TdApiExt.MessageChatEvent) msg.content).hideDate);
  }

  public final boolean isEventLog () {
    //noinspection WrongConstant
    return (flags & FLAG_EVENT_LOG) != 0 || msg.content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR;
  }

  public final boolean isThreadHeader () {
    return BitwiseUtils.hasFlag(flags, MESSAGE_FLAG_IS_THREAD_HEADER);
  }

  protected String footerTitle;
  protected TextWrapper footerText;

  protected boolean hasFooter () {
    return footerTitle != null && footerText != null;
  }

  public void setFooter (String title, String text, TdApi.TextEntity[] entities) {
    this.footerTitle = title;

    final TextWrapper footerWrapper = new TextWrapper(text, getSmallerTextStyleProvider(), getTextColorSet())
      .setEntities(TextEntity.valueOf(tdlib, text, entities, openParameters()), (wrapper, text1, specificMedia) -> {
        if (footerText == wrapper) {
          performWithViews(view -> {
            view.invalidateFooterTextMediaReceiver(this, text1, specificMedia);
          });
        }
      })
      .setClickCallback(clickCallback());
    if (useBubbles()) {
      footerWrapper.addTextFlags(Text.FLAG_ADJUST_TO_CURRENT_WIDTH);
    }
    if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
      footerWrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
    }
    footerWrapper.setViewProvider(currentViews);

    this.footerText = footerWrapper;
  }

  protected final int getFooterTop () {
    return pContentY + getContentHeight() + getFooterPaddingTop();
  }

  protected final int getFooterHeight () {
    return Screen.dp(22f) + footerText.getHeight() + Screen.dp(2f);
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

  public final TextColorSet getSearchHighlightColorSet () {
    return pick(TextColorSets.Regular.MESSAGE_SEARCH_HIGHLIGHT, TextColorSets.BubbleOut.MESSAGE_SEARCH_HIGHLIGHT, TextColorSets.BubbleIn.MESSAGE_SEARCH_HIGHLIGHT);
  }

  // Colors

  public final @ColorInt int getContentBackgroundColor () {
    if (useBubbles()) {
      return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_background : ColorId.bubbleIn_background);
    } else {
      return ColorUtils.compositeColor(Theme.getColor(ColorId.chatBackground), getSelectionColor(selectionFactor));
    }
  }

  public final @ColorId int getDecentColorId (@ColorId int defaultColorId) {
    return useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_time : ColorId.bubbleIn_time) : defaultColorId;
  }

  public final @ColorId int getProgressColorId () {
    return useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_progress : ColorId.bubbleIn_progress) : ColorId.progress;
  }

  public final int getProgressColor () {
    return Theme.getColor(getProgressColorId());
  }

  public final @ColorId int getDecentColorId () {
    return getDecentColorId(ColorId.textLight);
  }

  public final @ColorId int getSeparatorColorId () {
    return useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_separator : ColorId.bubbleIn_separator) : ColorId.separator;
  }

  public final @ColorId int getPressColorId () {
    return useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_pressed : ColorId.bubbleIn_pressed) : ColorId.messageSelection;
  }

  public final @ColorId int getDecentIconColorId () {
    return getDecentColorId(ColorId.iconLight);
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
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_text : ColorId.bubbleIn_text) : ColorId.text);
  }

  public final int getOutlineColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_outline : ColorId.bubbleIn_outline) : ColorId.separator);
  }

  public final int getTextLinkColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_textLink : ColorId.bubbleIn_textLink) : ColorId.textLink);
  }

  public final int getTextLinkHighlightColor () {
    return Theme.getColor(useBubbles() ? (isOutgoingBubble() ? ColorId.bubbleOut_textLinkPressHighlight : ColorId.bubbleIn_textLinkPressHighlight) : ColorId.textLinkPressHighlight);
  }

  protected final int getTextTopOffset () {
    return (!needHeader() || !needName()) && (!useForward() || ((flags & FLAG_SELF_CHAT) != 0 && !isOutgoing())) && replyData == null ? 0 : -Screen.dp(useBubbles() ? 4f : 2f);
  }

  protected final int getVerticalLineColor () {
    return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_chatVerticalLine : ColorId.messageVerticalLine);
  }

  protected final int getVerticalLineContentColor () {
    return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_chatNeutralFillingContent : ColorId.messageNeutralFillingContent);
  }

  protected final int getCorrectLineColor (boolean isPersonal) {
    return Theme.getColor(
            isPersonal ?
            isOutgoingBubble() ? ColorId.bubbleOut_chatCorrectChosenFilling : ColorId.messageCorrectChosenFilling :
            isOutgoingBubble() ? ColorId.bubbleOut_chatCorrectFilling : ColorId.messageCorrectFilling
    );
  }

  protected final int getCorrectLineContentColor (boolean isPersonal) {
    return Theme.getColor(
            isPersonal ?
            isOutgoingBubble() ? ColorId.bubbleOut_chatCorrectChosenFillingContent : ColorId.messageCorrectChosenFillingContent :
            isOutgoingBubble() ? ColorId.bubbleOut_chatCorrectFillingContent : ColorId.messageCorrectFillingContent
    );
  }

  protected final int getNegativeLineColor () {
    return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_chatNegativeFilling : ColorId.messageNegativeLine);
  }

  protected final int getNegativeLineContentColor () {
    return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_chatNegativeFillingContent : ColorId.messageNegativeLineContent);
  }

  protected final int getChatAuthorColor () {
    return Theme.getColor(getChatAuthorColorId());
  }

  protected final int getChatAuthorColorId () {
    return isOutgoingBubble() ? ColorId.bubbleOut_messageAuthor : ColorId.messageAuthor;
  }

  protected final int getChatAuthorPsaColor () {
    return Theme.getColor(isOutgoingBubble() ? ColorId.bubbleOut_messageAuthorPsa : ColorId.messageAuthorPsa);
  }

  private void drawFooter (MessageView view, Canvas c) {
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
    paint.setColor(Theme.getColor(ColorId.textNeutral));
    c.drawText(trimmedFooterTitle != null ? trimmedFooterTitle : footerTitle, contentX, contentY + Screen.dp(15f), paint);

    footerText.draw(c, contentX, contentY + Screen.dp(22f), null, 1f, view.getFooterTextMediaReceiver(true));
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
    if (hasBadge() && updateBadgeText()) {
      updated = true;
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

  public final Text.ClickCallback clickCallback () {
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
          messagesController().sendCommand(command, user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR ? Td.primaryUsername(user) : null);
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

      @Override
      public boolean onUsernameClick (String username) {
        if (isSponsoredMessage()) {
          TdApi.Usernames usernames = sender.getUsernames();
          if (usernames != null && Td.findUsername(usernames, username.substring(1), true)) {
            openSponsoredMessage();
            return true;
          }
        }
        trackSponsoredMessageClicked();
        return false;
      }

      @Override
      public boolean onUserClick (long userId) {
        if (isSponsoredMessage() && userId != 0 && sender.getUserId() == userId) {
          openSponsoredMessage();
          return true;
        }
        trackSponsoredMessageClicked();
        return false;
      }

      @Override
      public boolean onEmailClick (String email) {
        trackSponsoredMessageClicked();
        return false;
      }

      @Override
      public boolean onPhoneNumberClick (String phoneNumber) {
        trackSponsoredMessageClicked();
        return false;
      }

      @Override
      public boolean onUrlClick (View view, String link, boolean promptUser, @NonNull TdlibUi.UrlOpenParameters openParameters) {
        trackSponsoredMessageClicked();
        return false;
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
      paint.setColor(Theme.getColor(ColorId.textLight));;
    return paint;
  }

  private static void initPaints () {
    if (mQuickText == null) {
      mQuickText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      mQuickText.setColor(Theme.chatQuickActionTextColor());
      ThemeManager.addThemeListener(mQuickText, ColorId.messageSwipeContent);
      mQuickText.setTypeface(Fonts.getRobotoRegular());
      mQuickText.setTextSize(Screen.dp(16f));
    }
  }

  private static TextStyleProvider styleProvider, simpleStyleProvider, biggerStyleProvider, smallerStyleProvider, nameProvider, timeProvider, reactionBubbleProvider, bubbleServiceProvider;

  public static TextStyleProvider reactionsTextStyleProvider () {
    if (reactionBubbleProvider == null) {
      reactionBubbleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSizeDiff(-4f).setTextSize(Settings.instance().getChatFontSize()).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(reactionBubbleProvider);
    }
    return reactionBubbleProvider;
  }

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

  public static TextStyleProvider getServiceTextStyleProvider (boolean bubbleMode) {
    if (bubbleMode) {
      return bubbleServiceTextStyleProvider();
    } else {
      return getTextStyleProvider();
    }
  }

  public static TextStyleProvider bubbleServiceTextStyleProvider () {
    if (bubbleServiceProvider == null) {
      bubbleServiceProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSizeDiff(-2f).setTextSize(Settings.instance().getChatFontSize()).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(bubbleServiceProvider);
    }
    return bubbleServiceProvider;
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
  protected static int xBubblePadding, xReactionBubblePadding, xReactionBubblePaddingTop, xReactionBubblePaddingBottom, xBubblePaddingSmall, xBubbleAvatarRadius, xBubbleAvatarLeft;

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

  protected static int xQuickPadding, xQuickTextPadding, xQuickTextOffset, xQuickShareWidth, xQuickReplyWidth, xQuickTranslateWidth, xQuickTranslateStopWidth;

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
    xReactionBubblePadding = Screen.dp(10f);
    xReactionBubblePaddingTop = Screen.dp(9f);
    xReactionBubblePaddingBottom = Screen.dp(10f);
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

  private static Drawable iQuickTranslate, iQuickStopTranslate, iQuickReply, iQuickShare, iBadge;
  private static String shareText, replyText, translateText, translateStopText;
  private static boolean initialized;

  private static void initResources () {
    Resources res = UI.getResources();
    iBadge = Drawables.get(res, R.drawable.baseline_keyboard_arrow_down_20);
    iQuickReply = Drawables.get(res, R.drawable.baseline_reply_24);
    iQuickShare = Drawables.get(res, R.drawable.baseline_forward_24);
    iQuickTranslate = Drawables.get(res, R.drawable.baseline_translate_24);
    iQuickStopTranslate = Drawables.get(res, R.drawable.baseline_translate_off_24);
    initBubbleResources();
  }

  private static void initTexts () {
    if (mQuickText != null) {
      shareText = Lang.getString(R.string.SwipeShare);
      replyText = Lang.getString(R.string.SwipeReply);
      translateText = Lang.getString(R.string.Translate);
      translateStopText = Lang.getString(R.string.TranslateOff);
      xQuickReplyWidth = (int) U.measureText(replyText, mQuickText);
      xQuickShareWidth = (int) U.measureText(shareText, mQuickText);
      xQuickTranslateWidth = (int) U.measureText(translateText, mQuickText);
      xQuickTranslateStopWidth = (int) U.measureText(translateStopText, mQuickText);
    }
  }

  private static boolean isStaticText (int res) {
    return res == R.string.SwipeShare || res == R.string.SwipeReply;
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

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.Chat chat, @Nullable ThreadInfo messageThread, @Nullable LongSparseArray<TdApi.ChatAdministrator> chatAdmins) {
    return valueOf(context, msg, chat, messageThread, msg.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR && chatAdmins != null ? chatAdmins.get(((TdApi.MessageSenderUser) msg.senderId).userId) : null);
  }

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.Chat chat, @Nullable ThreadInfo messageThread, @Nullable TdApi.ChatAdministrator admin) {
    TGMessage parsedMessage = valueOf(context, msg);
    if (chat != null) {
      parsedMessage.setChatData(chat, messageThread);
    }
    if (admin != null) {
      parsedMessage.setAdministratorSign(admin);
    }
    parsedMessage.initSelectedMessages();
    return parsedMessage;
  }

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg) {
    return valueOf(context, msg, msg.content);
  }

  public static TGMessage valueOf (MessagesManager context, TdApi.Message msg, TdApi.MessageContent content) {
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

      //noinspection WrongConstant
      if (content.getConstructor() == TdApiExt.MessageChatEvent.CONSTRUCTOR) {
        return ChatEventUtil.newMessage(context, msg, (TdApiExt.MessageChatEvent) content);
      }

      int unsupportedStringRes = R.string.UnsupportedMessage;

      switch (content.getConstructor()) {
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
            return new TGMessageSticker(context, msg, null, animatedEmoji);
          }
          return new TGMessageText(context, msg, nonNull((TdApi.MessageText) content), (TdApi.MessageText) pendingContent);
        }
        case TdApi.MessageCall.CONSTRUCTOR: {
          return new TGMessageCall(context, msg, nonNull(((TdApi.MessageCall) content)));
        }
        case TdApi.MessagePhoto.CONSTRUCTOR: {
          TdApi.MessagePhoto messagePhoto = (TdApi.MessagePhoto) content;
          return new TGMessageMedia(context, msg, messagePhoto, messagePhoto.caption);
        }
        case TdApi.MessageVideo.CONSTRUCTOR: {
          TdApi.MessageVideo messageVideo = (TdApi.MessageVideo) content;
          return new TGMessageMedia(context, msg, messageVideo, messageVideo.caption);
        }
        case TdApi.MessageAnimation.CONSTRUCTOR: {
          TdApi.MessageAnimation messageAnimation = (TdApi.MessageAnimation) content;
          return new TGMessageMedia(context, msg, messageAnimation, messageAnimation.caption);
        }
        case TdApi.MessageVideoNote.CONSTRUCTOR: {
          return new TGMessageVideo(context, msg, nonNull(((TdApi.MessageVideoNote) content).videoNote), ((TdApi.MessageVideoNote) content).isViewed);
        }
        case TdApi.MessageDice.CONSTRUCTOR: {
          return new TGMessageSticker(context, msg, nonNull(((TdApi.MessageDice) content)));
        }
        case TdApi.MessageSticker.CONSTRUCTOR: {
          return new TGMessageSticker(context, msg, nonNull(((TdApi.MessageSticker) content).sticker), false, 0);
        }
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
        case TdApi.MessageAudio.CONSTRUCTOR:
        case TdApi.MessageDocument.CONSTRUCTOR: {
          return new TGMessageFile(context, msg);
        }
        case TdApi.MessagePoll.CONSTRUCTOR: {
          return new TGMessagePoll(context, msg, nonNull((TdApi.MessagePoll) content).poll);
        }
        case TdApi.MessageLocation.CONSTRUCTOR: {
          TdApi.MessageLocation location = (TdApi.MessageLocation) content;
          return new TGMessageLocation(context, msg, nonNull(location.location), location.livePeriod, location.expiresIn);
        }
        case TdApi.MessageVenue.CONSTRUCTOR: {
          return new TGMessageLocation(context, msg, ((TdApi.MessageVenue) content).venue);
        }
        case TdApi.MessageContact.CONSTRUCTOR: {
          return new TGMessageContact(context, msg, (TdApi.MessageContact) content);
        }
        case TdApi.MessageGame.CONSTRUCTOR: {
          return new TGMessageGame(context, msg, ((TdApi.MessageGame) content).game);
        }
        case TdApi.MessageExpiredPhoto.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageExpiredPhoto) content);
        }
        case TdApi.MessageExpiredVideo.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageExpiredVideo) content);
        }
        case TdApi.MessagePinMessage.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessagePinMessage) content);
        }
        case TdApi.MessageScreenshotTaken.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageScreenshotTaken) content);
        }
        case TdApi.MessageGiftedPremium.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageGiftedPremium) content);
        }
        case TdApi.MessageChatSetTheme.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatSetTheme) content);
        }
        case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatSetMessageAutoDeleteTime) content);
        }
        case TdApi.MessageGameScore.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageGameScore) content);
        }
        case TdApi.MessageContactRegistered.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageContactRegistered) content);
        }
        case TdApi.MessageChatChangePhoto.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatChangePhoto) content);
        }
        case TdApi.MessageCustomServiceAction.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageCustomServiceAction) content);
        }
        case TdApi.MessagePaymentSuccessful.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessagePaymentSuccessful) content);
        }
        case TdApi.MessageWebAppDataSent.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageWebAppDataSent) content);
        }
        case TdApi.MessageChatDeletePhoto.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatDeletePhoto) content);
        }
        case TdApi.MessageChatAddMembers.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatAddMembers) content);
        }
        case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageBasicGroupChatCreate) content);
        }
        case TdApi.MessageChatChangeTitle.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatChangeTitle) content);
        }
        case TdApi.MessageChatDeleteMember.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatDeleteMember) content);
        }
        case TdApi.MessageChatJoinByLink.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatJoinByLink) content);
        }
        case TdApi.MessageChatJoinByRequest.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatJoinByRequest) content);
        }
        case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageProximityAlertTriggered) content);
        }
        case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageInviteVideoChatParticipants) content);
        }
        case TdApi.MessageVideoChatStarted.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageVideoChatStarted) content);
        }
        case TdApi.MessageVideoChatScheduled.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageVideoChatScheduled) content);
        }
        case TdApi.MessageVideoChatEnded.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageVideoChatEnded) content);
        }
        case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageSupergroupChatCreate) content);
        }
        case TdApi.MessageWebsiteConnected.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageWebsiteConnected) content);
        }
        case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageBotWriteAccessAllowed) content);
        }
        case TdApi.MessageChatUpgradeTo.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatUpgradeTo) content);
        }
        case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageChatUpgradeFrom) content);
        }
        case TdApi.MessageForumTopicCreated.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageForumTopicCreated) content);
        }
        case TdApi.MessageForumTopicEdited.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageForumTopicEdited) content);
        }
        case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageForumTopicIsClosedToggled) content);
        }
        case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR: {
          return new TGMessageService(context, msg, (TdApi.MessageForumTopicIsHiddenToggled) content);
        }
        // unsupported
        case TdApi.MessageInvoice.CONSTRUCTOR:
        case TdApi.MessagePassportDataSent.CONSTRUCTOR:
        case TdApi.MessageStory.CONSTRUCTOR:
        case TdApi.MessageChatSetBackground.CONSTRUCTOR:
        case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
        case TdApi.MessageUserShared.CONSTRUCTOR:
        case TdApi.MessageChatShared.CONSTRUCTOR:
          break;
        case TdApi.MessageUnsupported.CONSTRUCTOR:
          unsupportedStringRes = R.string.UnsupportedMessageType;
          break;
        // bots only
        case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
        case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
        case TdApi.MessageWebAppDataReceived.CONSTRUCTOR: {
          Log.e("Received bot message for a regular user:\n%s", msg);
          break;
        }
        default: {
          Td.assertMessageContent_6479f6fc();
          throw Td.unsupported(msg.content);
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
        if (supergroup != null && Td.hasUsername(supergroup)) {
          b.append("Public post: t.me/").append(Td.primaryUsername(supergroup)).append('/').append(msg.id >> 20).append("\n");
        }
      }
    } else {
      b.append("FROM UNKNOWN CHAT #").append(msg.chatId).append("\n");
    }
    b.append("\n");

    Log.toStringBuilder(error, 2, b);

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

  public final boolean useReactionBubbles () {
    return manager().useReactionBubbles();
  }

  //

  public final void checkAvailableReactions (Runnable after) {
    tdlib().client().send(new TdApi.GetMessageAvailableReactions(msg.chatId, getSmallestId(), 25), result -> {
      switch (result.getConstructor()) {
        case TdApi.AvailableReactions.CONSTRUCTOR: {
          TdApi.AvailableReactions availableReactions = (TdApi.AvailableReactions) result;
          tdlib.ensureReactionsAvailable(availableReactions, reactionsUpdated -> {
            messageAvailableReactions = availableReactions;
            computeQuickButtons();
            runOnUiThreadOptional(after);
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          runOnUiThreadOptional(after);
          break;
        }
      }
    });
  }

  public final void checkMessageFlags (Runnable r) {
    TdApi.Message msg = getMessage(getSmallestId());
    if (msg == null || isFakeMessage() || isSponsoredMessage()) {
      r.run();
      return;
    }

    tdlib().client().send(new TdApi.GetMessageLocally(msg.chatId, msg.id), (TdApi.Object object) -> {
      if (object.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        TdApi.Message message = (TdApi.Message) object;
        copyFlags(message, msg);
        tdlib().ui().post(r);
      }
    });
  }

  public final boolean isCustomEmojiReactionsAvailable () {
    if (messageAvailableReactions == null)
      return false;

    return messageAvailableReactions.allowCustomEmoji;
  }

  public final TdApi.AvailableReaction[] getMessageAvailableReactions () {
    if (messageAvailableReactions == null)
      return null;
    boolean hasPremium = tdlib.hasPremium();
    Set<String> addedReactions = new HashSet<>();
    List<TdApi.AvailableReaction> reactions = new ArrayList<>();
    for (TdApi.AvailableReaction reaction : messageAvailableReactions.popularReactions) {
      if ((!reaction.needsPremium || hasPremium) && addedReactions.add(TD.makeReactionKey(reaction.type))) {
        reactions.add(reaction);
      }
    }
    for (TdApi.AvailableReaction reaction : messageAvailableReactions.topReactions) {
      if ((!reaction.needsPremium || hasPremium) && addedReactions.add(TD.makeReactionKey(reaction.type))) {
        reactions.add(reaction);
      }
    }
    for (TdApi.AvailableReaction reaction : messageAvailableReactions.recentReactions) {
      if ((!reaction.needsPremium || hasPremium) && addedReactions.add(TD.makeReactionKey(reaction.type))) {
        reactions.add(reaction);
      }
    }
    if (reactions.isEmpty()) {
      return null;
    }
    String[] activeEmojiReactions = tdlib.getActiveEmojiReactions();
    if (activeEmojiReactions != null && activeEmojiReactions.length > 0) {
      Collections.sort(reactions, (a, b) -> {
        boolean aIsEmoji = a.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR;
        boolean bIsEmoji = b.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR;
        if (aIsEmoji != bIsEmoji) {
          return aIsEmoji ? -1 : 1;
        }
        if (!aIsEmoji) {
          return 0;
        }
        String aEmoji = ((TdApi.ReactionTypeEmoji) a.type).emoji;
        String bEmoji = ((TdApi.ReactionTypeEmoji) b.type).emoji;
        int aIndex = ArrayUtils.indexOf(activeEmojiReactions, aEmoji);
        int bIndex = ArrayUtils.indexOf(activeEmojiReactions, bEmoji);
        boolean aAvailable = aIndex != -1;
        boolean bAvailable = bIndex != -1;
        if (aAvailable != bAvailable) {
          return aAvailable ? -1 : 1;
        }
        if (aIndex != bIndex) {
          return aIndex < bIndex ? -1 : 1;
        }
        return 0;
      });
    }
    return reactions.toArray(new TdApi.AvailableReaction[0]);
  }

  // Utils

  public void runOnUiThread (Runnable act) {
    tdlib.ui().post(act);
  }

  public void runOnUiThread (Runnable act, long delayMillis) {
    tdlib.ui().postDelayed(act, delayMillis);
  }

  public void runOnUiThreadOptional (Runnable act) {
    runOnUiThread(() -> {
      if (!isDestroyed()) {
        act.run();
      }
    });
  }

  public void executeOnUiThreadOptional (Runnable act) {
    if (UI.inUiThread()) {
      if (!isDestroyed()) {
        act.run();
      }
    } else {
      runOnUiThreadOptional(act);
    }
  }

  // quick action

  private final ArrayList<SwipeQuickAction> leftActions = new ArrayList<>();
  private final ArrayList<SwipeQuickAction> rightActions = new ArrayList<>();
  private int leftQuickDefaultPosition = 0;
  private int rightQuickDefaultPosition = 0;

  public static class SwipeQuickAction {
    public String text;
    public Drawable icon;
    public Runnable handler;
    public boolean needDelay;
    public boolean isQuickReaction;

    SwipeQuickAction (String text, Drawable icon, Runnable handler, boolean needDelay, boolean isQuickReaction) {
      this.text = text;
      this.icon = icon;
      this.handler = handler;
      this.needDelay = needDelay;
      this.isQuickReaction = isQuickReaction;
    }

    public void onSwipe () {
      handler.run();
    }
  }

  private boolean canSendReaction (TdApi.ReactionType reactionType) {
    return canBeReacted() && !tdlib.isSelfChat(msg.chatId) && Td.isAvailable(messageAvailableReactions, reactionType);
  }

  private void computeQuickButtons () {
    if (!UI.inUiThread()) {
      tdlib.ui().post(this::computeQuickButtons);
      return;
    }

    final boolean canReply = Settings.instance().needChatQuickReply() && messagesController().canWriteMessages() && !messagesController().needTabs() && canReplyTo();
    final boolean canShare = Settings.instance().needChatQuickShare() && !messagesController().isSecretChat() && canBeForwarded();

    leftActions.clear();
    rightActions.clear();
    rightQuickDefaultPosition = 0;

    SwipeQuickAction replyButton = null;
    if (canReply) {
      replyButton = new SwipeQuickAction(replyText, iQuickReply, () -> {
        messagesController().showReply(getNewestMessage(), true, true);
      }, true, false);
      rightActions.add(replyButton);
    }

    if (Settings.instance().needUseQuickTranslation()) {
      if (isTranslated()) {
        rightActions.add(new SwipeQuickAction(translateStopText, iQuickStopTranslate, () -> {
          stopTranslated();
        }, true, false));
      } else if (isTranslatable() && translationStyleMode() != Settings.TRANSLATE_MODE_NONE) {
        rightActions.add(new SwipeQuickAction(translateText, iQuickTranslate, () -> {
          messagesController().startTranslateMessages(this);
        }, true, false));
      }
    }

    final String[] quickReactions = Settings.instance().getQuickReactions(tdlib);
    for (int a = 0; a < quickReactions.length; a++) {
      final String reactionString = quickReactions[a];
      TdApi.ReactionType reactionType = TD.toReactionType(reactionString);
      final boolean canReact = canSendReaction(reactionType);
      final TGReaction reactionObj = tdlib.getReaction(reactionType);
      if (reactionObj != null && canReact) {
        final TGReaction.ReactionDrawable reactionDrawable = new TGReaction.ReactionDrawable(reactionObj, Screen.dp(48), Screen.dp(48));
        reactionDrawable.setComplexReceiver(currentComplexReceiver);

        final boolean isOdd = a % 2 == 1;
        final SwipeQuickAction quickReaction = new SwipeQuickAction(reactionObj.getTitle(), reactionDrawable, () -> {
          boolean hasReaction = messageReactions.hasReaction(reactionType);
          if (!Config.PROTECT_ANONYMOUS_REACTIONS || hasReaction || !canGetAddedReactions() || messagesController().callNonAnonymousProtection(getId() + reactionObj.hashCode(), null)) {
            if (messageReactions.toggleReaction(reactionType, false, false, handler(findCurrentView(), null, () -> {}))) {
              scheduleSetReactionAnimation(new NextReactionAnimation(reactionObj, NextReactionAnimation.TYPE_QUICK));
            }
          }
        }, false, true);

        if (isOdd) {
          rightQuickDefaultPosition += 1;
          rightActions.add(0, quickReaction);
        } else {
          rightActions.add(quickReaction);
        }
      }
    }



    if (canShare) {
      leftActions.add(new SwipeQuickAction(shareText, iQuickShare, () -> {
        messagesController().shareMessages(getChatId(), getAllMessages());
      }, true, false));
    }
  }

  public ArrayList<SwipeQuickAction> getLeftQuickReactions () {
    return leftActions;
  }

  public ArrayList<SwipeQuickAction> getRightQuickReactions () {
    return rightActions;
  }

  @Nullable public SwipeQuickAction getQuickAction (boolean isLeft, int index) {
    List<SwipeQuickAction> swipeQuickActions = (isLeft ? leftActions : rightActions);
    return index >= 0 && index < swipeQuickActions.size() ? swipeQuickActions.get(index) : null;
  }

  public int getQuickDefaultPosition (boolean isLeft) {
    return isLeft ? leftQuickDefaultPosition : rightQuickDefaultPosition;
  }

  public int getQuickActionsCount (boolean isLeft) {
    return isLeft ? getLeftQuickReactions().size() : getRightQuickReactions().size();
  }

  public MessageQuickActionSwipeHelper getSwipeHelper () {
    return swipeHelper;
  }



  // Reaction positions and draw

  private void drawReactionsWithBubbles (Canvas c, MessageView v, int x, int y) {
    messageReactions.drawReactionBubbles(c, v, x, y);
    setLastDrawReactionsPosition(x, y);
  }

  private void drawReactionsWithoutBubbles (Canvas c, int x, int y) {
    setLastDrawReactionsPosition(x, y);
    reactionsCounterDrawable.draw(c, x, y);
  }

  private void setLastDrawReactionsPosition (int lastDrawReactionsX, int lastDrawReactionsY) {
    this.lastDrawReactionsX = lastDrawReactionsX;
    this.lastDrawReactionsY = lastDrawReactionsY;
  }

  private Point getReactionPosition (TGReaction reaction) {
    final int reactionsDrawMode = getReactionsDrawMode();

    if (reactionsDrawMode == REACTIONS_DRAW_MODE_BUBBLE) {
      int x = lastDrawReactionsX + messageReactions.getReactionBubbleX(reaction) + TGReactions.getReactionImageSize() / 2 - Screen.dp(1);
      int y = lastDrawReactionsY + messageReactions.getReactionBubbleY(reaction) + TGReactions.getReactionBubbleHeight() / 2;
      return new Point(x, y);
    } else if (reactionsDrawMode == REACTIONS_DRAW_MODE_ONLY_ICON) {
      int x = lastDrawReactionsX - Screen.dp(7);
      int y = lastDrawReactionsY;
      return new Point(x, y);
    } else if (reactionsDrawMode == REACTIONS_DRAW_MODE_FLAT) {
      int x = (int) (lastDrawReactionsX + Screen.dp(6) + Screen.dp(15) * MathUtils.clamp(messageReactions.getReactionPositionInList(reaction.type), 0, 2));
      int y = lastDrawReactionsY;
      return new Point(x, y);
    }

    return new Point(Screen.currentWidth(), 0);
  }

  private int getReactionsDrawMode () {
    if (useReactionBubbles()) {
      return REACTIONS_DRAW_MODE_BUBBLE;
    }

    boolean headerEnabled = BitwiseUtils.hasFlag(flags, FLAG_HEADER_ENABLED);
    boolean isUserChat = tdlib.isUserChat(getChatId());

    if (!useBubbles() && (isChannel() || (!headerEnabled && !isUserChat))) {
      return REACTIONS_DRAW_MODE_ONLY_ICON;
    }

    return REACTIONS_DRAW_MODE_FLAT;
  }

  private Counter getReactionsOnlyIconCounter () {
    if (shrinkedReactionsCounter != null) {
      return shrinkedReactionsCounter;
    }

    shrinkedReactionsCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .colorSet(() -> messageReactions.hasChosen() ? Theme.getColor(ColorId.badge) : Theme.getColor(ColorId.iconLight))
      .drawable(R.drawable.baseline_favorite_14, 14f, 0f, Gravity.CENTER_HORIZONTAL)
      .build();

    shrinkedReactionsCounter.showHide(messageReactions.getTotalCount() > 0, false);
    shrinkedReactionsCounter.setMuted(!messageReactions.hasChosen(), false);

    return shrinkedReactionsCounter;
  }

  private Counter getReactionsCounter () {
    if (reactionsCounter != null) {
      return reactionsCounter;
    }

    reactionsCounter = new Counter.Builder()
      .noBackground()
      .allBold(false)
      .callback(this)
      .textSize(useBubbles() ? 11f : 12f)
      .colorSet(() -> messageReactions.hasChosen() ? Theme.getColor(ColorId.badge) : this.getTimePartTextColor())
      .build();

    int count = messageReactions.getTotalCount();
    if (tdlib.isUserChat(msg.chatId) && messageReactions.getReactions() != null && (count == 1 || messageReactions.getReactions().length > 1)) {
      count = 0;
    }
    reactionsCounter.setCount(count, !messageReactions.hasChosen(), false);

    return reactionsCounter;
  }

  // Set Reaction Animations

  @Nullable private NextReactionAnimation nextSetReactionAnimation;
  @Nullable private TdApi.UnreadReaction[] savedUnreadReactions;
  private boolean setReactionAnimationReadyToPlay = false;
  private boolean setReactionAnimationNowPlaying = false;
  private boolean needHighlightUnreadReactions = false;

  public void scheduleSetReactionAnimationFromBottomSheet (TGReaction reaction, Point startPos) {
    NextReactionAnimation animation = new NextReactionAnimation(reaction, NextReactionAnimation.TYPE_BOTTOM_SHEET);
    animation.setStartPosition(startPos);
    scheduleSetReactionAnimation(animation);
  }

  public void scheduleSetReactionAnimationFullscreenFromBottomSheet (TGReaction reaction, Point startPos) {
    NextReactionAnimation animation = new NextReactionAnimation(reaction, NextReactionAnimation.TYPE_BOTTOM_SHEET_FULLSCREEN);
    animation.setStartPosition(startPos);
    scheduleSetReactionAnimation(animation);
  }

  private void scheduleSetReactionAnimation (NextReactionAnimation animation) {
    if (setReactionAnimationNowPlaying) {
      return;
    }

    nextSetReactionAnimation = animation;
    invalidate(true);
  }

  public void cancelScheduledSetReactionAnimation () {
    clearSetReactionAnimation();
  }

  private void clearSetReactionAnimation () {
    setReactionAnimationNowPlaying = false;
    setReactionAnimationReadyToPlay = false;
    nextSetReactionAnimation = null;
  }

  private void startReactionAnimationIfNeeded () {
    if (nextSetReactionAnimation == null || setReactionAnimationNowPlaying) {
      return;
    }

    setReactionAnimationReadyToPlay = true;
    invalidate();
  }

  private void startSetReactionAnimationIfReady () {
    if (!setReactionAnimationReadyToPlay || setReactionAnimationNowPlaying) {
      return;
    }

    if (nextSetReactionAnimation == null) {
      clearSetReactionAnimation();
      return;
    }

    View view = findCurrentView();
    if (view == null) {
      clearSetReactionAnimation();
      return;
    }

    setReactionAnimationNowPlaying = true;

    TGReaction reaction = nextSetReactionAnimation.reaction;
    Point reactionPosition = getReactionPosition(reaction);

    int[] positionCords = new int[2];
    view.getLocationOnScreen(positionCords);

    int finishX = positionCords[0] + reactionPosition.x;
    int finishY = positionCords[1] + reactionPosition.y;

    TGReactions.MessageReactionEntry entry = messageReactions.getMessageReactionEntry(reaction.key);
    if (entry != null) {
      TdApi.MessageReaction messageReaction = entry.getMessageReaction();
      if (messageReaction.totalCount == 1 && messageReaction.isChosen) {
        entry.setHidden(true);
        entry.prepareAnimation();
      }
    }

    if (nextSetReactionAnimation.type == NextReactionAnimation.TYPE_CLICK) {
      onQuickReactionAnimationFinish();
    } else if (nextSetReactionAnimation.type == NextReactionAnimation.TYPE_QUICK) {
      int startX = positionCords[0];
      int startY = positionCords[1] + view.getMeasuredHeight() / 2;
      if (useBubbles()) {
        final int top = topContentEdge;
        final int bottom = bottomContentEdge;
        final int height = bottom - top;
        if (!Lang.rtl()) {
          startX += view.getMeasuredWidth() - Screen.dp(BUBBLE_MOVE_MAX) / 2;
        } else {
          startX += Screen.dp(BUBBLE_MOVE_MAX) / 2;
        }
        startX -= translation;
        startY = positionCords[1] + top + height / 2;
      } else {
        if (!Lang.rtl()) {
          startX += Screen.dp(BUBBLE_MOVE_MAX) / 2;
        } else {
          startX += view.getMeasuredWidth() - Screen.dp(BUBBLE_MOVE_MAX) / 2;
        }

        // FIXME: rely on dp, not on px
        if (height > 256) {
          startY = (int) (positionCords[1] + (mInitialTouchY - getHeaderPadding() + xHeaderPadding));
        }
      }

      context().reactionsOverlayManager().addOverlay(
        new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
          .setSticker(nextSetReactionAnimation.reaction.staticCenterAnimationSicker(), false)
          .setAnimationEndListener(this::onQuickReactionAnimationFinish)
          .setAnimatedPosition(
            new Point(startX, startY),
            new Point(finishX, finishY),
            Screen.dp(40),
            Screen.dp(useReactionBubbles() ? 32 : 24),
            new QuickReactionAnimatedPositionProvider(),
            MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 210
          )
          .setAnimatedPositionOffsetProvider(new QuickReactionAnimatedPositionOffsetProvider())
      );
    } else if (nextSetReactionAnimation.type == NextReactionAnimation.TYPE_BOTTOM_SHEET && nextSetReactionAnimation.startPosition != null) {
      int startX = nextSetReactionAnimation.startPosition.x;
      int startY = nextSetReactionAnimation.startPosition.y;
      context().reactionsOverlayManager().addOverlay(
        new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
          .setSticker(nextSetReactionAnimation.reaction.staticCenterAnimationSicker(), false)
          .setAnimationEndListener(this::onQuickReactionAnimationFinish)
          .setAnimatedPosition(
            new Point(startX, startY),
            new Point(finishX, finishY),
            Screen.dp(40),
            Screen.dp(useReactionBubbles() ? 32 : 24),
            new QuickReactionAnimatedPositionProvider(Screen.dp(80)),
            MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 210
          )
          .setAnimatedPositionOffsetProvider(new QuickReactionAnimatedPositionOffsetProvider())
      );
    } else if (nextSetReactionAnimation.type == NextReactionAnimation.TYPE_BOTTOM_SHEET_FULLSCREEN && nextSetReactionAnimation.startPosition != null) {
      int startX = nextSetReactionAnimation.startPosition.x;
      int startY = nextSetReactionAnimation.startPosition.y;

      reaction.withEffectAnimation(effectAnimation -> {
        final QuickReactionAnimatedPositionOffsetProvider offsetProvider = new QuickReactionAnimatedPositionOffsetProvider();
        final Runnable finishRunnable = () -> {
          Point p = new Point();
          offsetProvider.getOffset(p);
          context().replaceReactionPreviewCords(finishX + p.x, finishY + p.y);
          context().closeStickerPreview();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            onQuickReactionAnimationFinish(view.isAttachedToWindow());
          } else {
            onQuickReactionAnimationFinish();
          }
        };
        final CancellableRunnable finishAnimation = new CancellableRunnable() {
          @Override
          public void act () {
            finishRunnable.run();
          }
        };

        if (effectAnimation != null && effectAnimation.getFullAnimation() != null) {
          effectAnimation.getFullAnimation().setPlayOnce(true);
          effectAnimation.getFullAnimation().setLooped(false);
          effectAnimation.getFullAnimation().addLoopListener(() -> {
            if (nextSetReactionAnimation != null) {
              nextSetReactionAnimation.fullscreenEffectFinished = true;
              if (nextSetReactionAnimation.fullscreenEmojiFinished) {
                finishAnimation.cancel();
                tdlib().ui().postDelayed(finishRunnable, 180l);
              }
            }
          });
        } else {
          nextSetReactionAnimation.fullscreenEffectFinished = true;
        }

        TGStickerObj activateAnimation = nextSetReactionAnimation.reaction.activateAnimationSicker();
        if (activateAnimation.getFullAnimation() != null) {
          if (!activateAnimation.isCustomReaction()) {
            activateAnimation.getFullAnimation().setPlayOnce(true);
            activateAnimation.getFullAnimation().setLooped(false);
          }
          activateAnimation.getFullAnimation().addLoopListener(() -> {
            if (nextSetReactionAnimation != null) {
              nextSetReactionAnimation.fullscreenEmojiFinished = true;
              if (nextSetReactionAnimation.fullscreenEffectFinished) {
                finishAnimation.cancel();
                tdlib().ui().postDelayed(finishRunnable, 180l);
              }
            }
          });
        } else {
          nextSetReactionAnimation.fullscreenEmojiFinished = true;
        }

        context().openReactionPreview(tdlib, null, reaction, effectAnimation, startX, startY, Screen.dp(30), -1, true);
        tdlib().ui().postDelayed(finishAnimation, 7500l);
      });
    }
  }

  public void onQuickReactionAnimationFinish () {
    onQuickReactionAnimationFinish(true);
  }

  public void onQuickReactionAnimationFinish (boolean needPlayEffectAnimation) {
    if (nextSetReactionAnimation == null) {
      clearSetReactionAnimation();
      return;
    }

    if (needPlayEffectAnimation) {
      vibrate();
      startReactionBubbleAnimation(nextSetReactionAnimation.reaction.type);
    }
    clearSetReactionAnimation();
  }

  public void startReactionBubbleAnimation (TdApi.ReactionType reactionType) {
    View view = findCurrentView();
    TGReaction tgReaction = tdlib.getReaction(reactionType);
    if (tgReaction == null || view == null) {
      return;
    }

    Point reactionPosition = getReactionPosition(tgReaction);

    int[] positionCords = new int[2];
    view.getLocationOnScreen(positionCords);
    int bubbleX = positionCords[0] + reactionPosition.x;
    int bubbleY = positionCords[1] + reactionPosition.y;

    messageReactions.startAnimation(tgReaction.key);
    RunnableData<TGStickerObj> act = overlaySticker -> {
      context().reactionsOverlayManager().addOverlay(
        new ReactionsOverlayView.ReactionInfo(context().reactionsOverlayManager())
          .setSticker(overlaySticker, true)
          .setUseDefaultSprayAnimation(tgReaction.isCustom())
          .setEmojiStatusEffect(tgReaction.isCustom() ? tgReaction.newCenterAnimationSicker() : null)
          .setPosition(new Point(bubbleX, bubbleY), Screen.dp(90))
          .setAnimatedPositionOffsetProvider(new QuickReactionAnimatedPositionOffsetProvider())
      );
    };
    TGStickerObj overlaySticker = tgReaction.newAroundAnimationSicker();
    if (overlaySticker != null && !Config.TEST_GENERIC_REACTION_EFFECTS) {
      act.runWithData(overlaySticker);
    } else {
      tdlib.pickRandomGenericOverlaySticker(sticker -> {
        if (sticker != null) {
          TGStickerObj genericOverlaySticker = new TGStickerObj(tdlib, sticker, null, sticker.fullType)
            .setReactionType(tgReaction.type);
          executeOnUiThreadOptional(() ->
            act.runWithData(genericOverlaySticker)
          );
        }
      });
    }
  }


  private boolean readyHighlightUnreadReactions = false;
  private void highlightUnreadReactionsIfNeeded () {
    if (readyHighlightUnreadReactions) {
      highlightUnreadReactionsImpl();
      readyHighlightUnreadReactions = false;
    } else if (needHighlightUnreadReactions) {
      readyHighlightUnreadReactions = true;
      invalidate();
    }
  }

  private void highlightUnreadReactions () {
    needHighlightUnreadReactions = true;
    if (combinedMessages != null) {
      for (TdApi.Message message : combinedMessages) {
        if (message.unreadReactions != null && message.unreadReactions.length > 0) {
          savedUnreadReactions = message.unreadReactions;
          break;
        }
      }
    } else {
      savedUnreadReactions = msg.unreadReactions;
    }
    invalidate();
  }

  private void highlightUnreadReactionsImpl () {
    if (savedUnreadReactions == null || savedUnreadReactions.length == 0) {
      return;
    }

    Set<String> reactionKeys = new HashSet<>();
    ArrayList<TdApi.UnreadReaction> reactions = new ArrayList<>();
    for (TdApi.UnreadReaction unreadReaction : savedUnreadReactions) {
      if (reactionKeys.add(TD.makeReactionKey(unreadReaction.type))) {
        reactions.add(unreadReaction);
      }
    }

    for (TdApi.UnreadReaction reaction : reactions) {
      // TODO support reaction.isBig
      startReactionBubbleAnimation(reaction.type);
    }

    needHighlightUnreadReactions = false;
    savedUnreadReactions = null;
  }

  private static class NextReactionAnimation {
    public static final int TYPE_QUICK = 0;
    public static final int TYPE_CLICK = 3;
    public static final int TYPE_BOTTOM_SHEET = 1;
    public static final int TYPE_BOTTOM_SHEET_FULLSCREEN = 2;
    public final int type;
    public final TGReaction reaction;

    @Nullable public Point startPosition;
    public boolean fullscreenEffectFinished = false;
    public boolean fullscreenEmojiFinished = false;

    public NextReactionAnimation (TGReaction reaction, int type) {
      this.reaction = reaction;
      this.type = type;
    }

    public NextReactionAnimation setStartPosition (Point startPosition) {
      this.startPosition = startPosition;
      return this;
    }
  }

  public static class QuickReactionAnimatedPositionProvider implements ReactionsOverlayView.AnimatedPositionProvider {
    private final int jumpHeight;

    public QuickReactionAnimatedPositionProvider () {
      this.jumpHeight = Screen.dp(20);
    }

    public QuickReactionAnimatedPositionProvider (int jumpHeight) {
      this.jumpHeight = jumpHeight;
    }

    @Override
    public void getPosition (ReactionsOverlayView.ReactionInfo reactionInfo, float factor, Rect outPosition) {
      Rect startPosition = reactionInfo.getStartPosition();
      Rect finishPosition = reactionInfo.getFinishPosition();
      if (startPosition == null || finishPosition == null) {
        return;
      }

      float positionFactor = factor; // MathUtils.clamp(factor / 0.9f);
      float scaleFactor = 1f; // - MathUtils.clamp((factor - .9f) * 10f);

      float jumpFactor = -4f * (positionFactor - 0.5f) * (positionFactor - 0.5f) + 1f;
      int yAdd = (int) (-jumpHeight * jumpFactor);

      int width = (int) ((startPosition.width() + (finishPosition.width() - startPosition.width()) * positionFactor) * scaleFactor);
      int height = (int) ((startPosition.height() + (finishPosition.height() - startPosition.height()) * positionFactor) * scaleFactor);
      int x = (int) (startPosition.centerX() + (finishPosition.centerX() - startPosition.centerX()) * positionFactor);
      int y = (int) (startPosition.centerY() + (finishPosition.centerY() - startPosition.centerY()) * positionFactor) + yAdd;
      outPosition.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
    }
  }

  private class QuickReactionAnimatedPositionOffsetProvider implements ReactionsOverlayView.AnimatedPositionOffsetProvider {
    private final int startX;
    private final int startY;
    private final int startH;

    public QuickReactionAnimatedPositionOffsetProvider () {
      this.startX = lastDrawReactionsX;
      this.startY = lastDrawReactionsY;
      this.startH = height;
    }

    @Override
    public void getOffset (Point p) {
      p.x = lastDrawReactionsX - startX + (int) translation;
      p.y = lastDrawReactionsY - startY + (startH - height);
    }
  }

  // Set reaction handlers

  private Client.ResultHandler handler (@Nullable View v, @Nullable TGReactions.MessageReactionEntry entry, Runnable onSuccess) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib.ui().post(onSuccess);
          break;
        case TdApi.Error.CONSTRUCTOR:
          tdlib.ui().post(() -> onSendError(v, entry, (TdApi.Error) object));
          cancelScheduledSetReactionAnimation();
          break;
      }
    };
  }

  private void onSendError (@Nullable View v, @Nullable TGReactions.MessageReactionEntry entry, TdApi.Error error) {
    showReactionBubbleTooltip(v, entry, TD.toErrorString(error));
  }

  private void showReactionBubbleTooltip (View v, TGReactions.MessageReactionEntry entry, String text) {
    context().tooltipManager().builder(v)
      .locate(getReactionBubbleLocationProvider(entry))
      .show(tdlib, text).hideDelayed(3500, TimeUnit.MILLISECONDS);
  }

  private TooltipOverlayView.LocationProvider getReactionBubbleLocationProvider (TGReactions.MessageReactionEntry entry) {
    return (targetView, outRect) -> {
      if (entry == null) {
        return;
      }
      outRect.set(entry.getX(), entry.getY(), entry.getX() + entry.getBubbleWidth(), entry.getY() + entry.getBubbleHeight());
      outRect.offset(lastDrawReactionsX, lastDrawReactionsY);
    };
  }

  private boolean openMessageFromChannel () {
    TooltipOverlayView.TooltipBuilder tooltipBuilder = context().tooltipManager().builder(findCurrentView()).locate((targetView, outRect) -> {
      outRect.left = (int) (isChannelHeaderCounterX - Screen.dp(7));
      outRect.top = (int) (isChannelHeaderCounterY - Screen.dp(7));
      outRect.right = (int) (isChannelHeaderCounterX + Screen.dp(7));
      outRect.bottom = (int) (isChannelHeaderCounterY + Screen.dp(7));
    });

    tdlib().ui().openChat(this, sender.getChatId(), new TdlibUi.ChatOpenParameters()
      .urlOpenParameters(new TdlibUi.UrlOpenParameters().tooltip(tooltipBuilder)).keepStack()
    );
    return true;
  }

  private boolean needDrawChannelIconInHeader () {
    return sender.isChannel() && !messagesController().isChannel();
  }

  private TooltipOverlayView.TooltipInfo languageSelectorTooltip;

  private void openLanguageSelectorInlineMode () {
    TooltipOverlayView.TooltipBuilder tooltipBuilder = context().tooltipManager().builder(findCurrentView()).locate((targetView, outRect) -> {
      outRect.left = (int) (isTranslatedCounterX - Screen.dp(7));
      outRect.top = (int) (isTranslatedCounterY - Screen.dp(7));
      outRect.right = (int) (isTranslatedCounterX + Screen.dp(7));
      outRect.bottom = (int) (isTranslatedCounterY + Screen.dp(7));
    });

    languageSelectorTooltip = tooltipBuilder.show(this, this::showLanguageSelectorInlineMode);//.hideDelayed(3500, TimeUnit.MILLISECONDS);
  }

  private void showTranslateErrorMessageBubbleMode (String message) {
    TooltipOverlayView.TooltipBuilder tooltipBuilder = context().tooltipManager().builder(findCurrentView()).locate((targetView, outRect) -> {
      outRect.left = (int) (isTranslatedCounterX - Screen.dp(7));
      outRect.top = (int) (isTranslatedCounterY - Screen.dp(7));
      outRect.right = (int) (isTranslatedCounterX + Screen.dp(7));
      outRect.bottom = (int) (isTranslatedCounterY + Screen.dp(7));
    });
    languageSelectorTooltip = tooltipBuilder.show(tdlib, message).hideDelayed(3500, TimeUnit.MILLISECONDS);
  }

  private void showLanguageSelectorInlineMode (View v) {
    if (languageSelectorTooltip == null) return;

    float x = Math.max(languageSelectorTooltip.getContentRight() - Screen.dp(178 + 16), 0);
    float y;
    float pivotY;
    if (useBubbles()) {
      y = languageSelectorTooltip.getContentBottom() - Screen.dp(280 + 16);
      pivotY = Screen.dp(288);
      if (y < HeaderView.getTopOffset()) {
        pivotY = Screen.dp(288) - (HeaderView.getTopOffset() - y);
        y = HeaderView.getTopOffset();
      }
    } else {
      pivotY = Screen.dp(8);
      y = languageSelectorTooltip.getContentTop();
      if (y > Screen.currentHeight() - Screen.dp(280 + 16)) {
        pivotY = Screen.dp(24) + y - (Screen.currentHeight() - Screen.dp(280 + 16));
        y = Screen.currentHeight() - Screen.dp(280 + 16);
      }
    }

    TranslationControllerV2.LanguageSelectorPopup languagePopupLayout = new TranslationControllerV2.LanguageSelectorPopup(v.getContext(), null, this::onLanguageChanged, mTranslationsManager.getCurrentTranslatedLanguage(), getOriginalMessageLanguage());
    // languagePopupLayout.languageRecyclerWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_RIGHT);
    languagePopupLayout.languageRecyclerWrap.setTranslationX(x);
    languagePopupLayout.languageRecyclerWrap.setTranslationY(y);

    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) languagePopupLayout.languageRecyclerWrap.getLayoutParams();
    params.gravity = Gravity.TOP | Gravity.LEFT;

    languagePopupLayout.show();
    languagePopupLayout.languageRecyclerWrap.setPivotX(Screen.dp(178 + 8));
    languagePopupLayout.languageRecyclerWrap.setPivotY(pivotY);
  }

  private void onLanguageChanged (String language) {
    if (languageSelectorTooltip != null) {
      languageSelectorTooltip.hide(true);
    }
    mTranslationsManager.requestTranslation(language);
  }

  protected float getTranslationLoadingAlphaValue () {
    return isTranslatedCounterDrawable.getLoadingTextAlpha();
  }

  public void startTranslated () {
    translatedCounterForceShow = true;
    mTranslationsManager.requestTranslation(Lang.getDefaultLanguageToTranslateV2(textToTranslateOriginalLanguage));
  }

  public void stopTranslated () {
    translatedCounterForceShow = false;
    mTranslationsManager.stopTranslation();
  }

  public int translationStyleMode () {
    return manager().getUsedTranslateStyleMode();
  }

  private @Nullable TdApi.FormattedText textToTranslate;
  private @Nullable String textToTranslateOriginalLanguage;

  public @Nullable String getCurrentTranslatedLanguage () {
    return mTranslationsManager.getCurrentTranslatedLanguage();
  }

  public @Nullable TdApi.FormattedText getTranslatedText () {
    if (textToTranslate == null) return null;
    return mTranslationsManager.getCachedTextTranslation(textToTranslate.text, getCurrentTranslatedLanguage());
  }

  @Override
  public @Nullable String getOriginalMessageLanguage () {
    return textToTranslateOriginalLanguage;
  }

  public boolean isTranslated () {
    return mTranslationsManager.getCurrentTranslatedLanguage() != null || translatedCounterForceShow;
  }

  public boolean isTranslatable () {
    return !Td.isEmpty(textToTranslate) && (flags & FLAG_UNSUPPORTED) == 0 && !Settings.instance().isNotTranslatableLanguage(textToTranslateOriginalLanguage);
  }

  @Override
  public @Nullable TdApi.FormattedText getTextToTranslate () {
    return textToTranslate;
  }

  public void checkTranslatableText (Runnable after) {
    final TdApi.FormattedText textToTranslate = getTextToTranslateImpl();
    this.textToTranslate = textToTranslate;
    textToTranslateOriginalLanguage = textToTranslate != null ? mTranslationsManager.getCachedTextLanguage(textToTranslate.text) : null;
    if (textToTranslate != null && textToTranslateOriginalLanguage == null && translationStyleMode() != Settings.TRANSLATE_MODE_NONE) {
      LanguageDetector.detectLanguage(context(), textToTranslate.text, lang -> {
        mTranslationsManager.saveCachedTextLanguage(textToTranslate.text, textToTranslateOriginalLanguage = lang);
        after.run();
      }, err -> {
        textToTranslateOriginalLanguage = null;
        after.run();
      });
    } else {
      after.run();
    }
  }

  protected @Nullable TdApi.FormattedText getTextToTranslateImpl () {
    return null; // override
  }

  private void setTranslatedStatus (int status, boolean animated) {
    boolean show = status != TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT || translatedCounterForceShow;
    isTranslatedCounterDrawable.setInvalidateCallback(show ? this::invalidate : null);
    isTranslatedCounterDrawable.setStatus(status, animated);
    if (show) {
      isTranslatedCounter.show(animated);
    } else {
      isTranslatedCounter.hide(animated);
    }
    this.buildReactions(animated);
  }

  private void checkSelectLanguageWarning (boolean force) {
    String current = mTranslationsManager.getCurrentTranslatedLanguage();
    if (current == null || StringUtils.equalsOrBothEmpty(current, getOriginalMessageLanguage()) || force) {
      context().tooltipManager().builder(findCurrentView()).locate((targetView, outRect) -> {
        outRect.left = (int) (isTranslatedCounterX - Screen.dp(7));
        outRect.top = (int) (isTranslatedCounterY - Screen.dp(7));
        outRect.right = (int) (isTranslatedCounterX + Screen.dp(7));
        outRect.bottom = (int) (isTranslatedCounterY + Screen.dp(7));
      }).show(tdlib, Lang.getString(R.string.TapToSelectLanguage)).hideDelayed(3500, TimeUnit.MILLISECONDS);;
    }
  }

  protected void setTranslationResult (@Nullable TdApi.FormattedText text) {
    manager.updateMessageTranslation(getChatId(), getSmallestId(), text);
  };

  /*  */

  public long getFirstEmojiId () {
    TdApi.FormattedText text = getTextToTranslate();
    if (text == null || text.text == null || text.entities == null || text.entities.length == 0) return -1;

    for (TdApi.TextEntity entity : text.entities) {
      if (Td.isCustomEmoji(entity.type)) {
        return ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId;
      }
    }

    return -1;
  }

  public long[] getUniqueEmojiPackIdList () {
    long[] emojiIds = TD.getUniqueEmojiIdList(getTextToTranslate());

    LongSet emojiSets = new LongSet();
    for (long emojiId : emojiIds) {
      TdlibEmojiManager.Entry entry = tdlib().emoji().find(emojiId);
      if (entry == null || entry.value == null) continue;
      emojiSets.add(entry.value.setId);
    }

    return emojiSets.toArray();
  }

  // Sponsored-related tools

  public final boolean isSponsoredMessage () {
    return sponsoredMessage != null;
  }

  public void trackSponsoredMessageClicked () {
    if (isSponsoredMessage()) {
      tdlib.client().send(new TdApi.ClickChatSponsoredMessage(msg.chatId, sponsoredMessage.messageId), tdlib.silentHandler());
    }
  }

  public void openSponsoredMessage () {
    if (!isSponsoredMessage()) {
      return;
    }
    final RunnableBool after = ok -> {
      if (ok) {
        trackSponsoredMessageClicked();
      }
    };
    TdlibUi.UrlOpenParameters openParameters = openParameters()
      .requireOpenPrompt();
    TdApi.MessageSponsor sponsor = sponsoredMessage.sponsor;
    switch (sponsor.type.getConstructor()) {
      case TdApi.MessageSponsorTypeBot.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeBot bot = (TdApi.MessageSponsorTypeBot) sponsor.type;
        tdlib.ui().openInternalLinkType(this, null, bot.link, openParameters, after);
        break;
      }
      case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePublicChannel publicChannel = (TdApi.MessageSponsorTypePublicChannel) sponsor.type;
        if (publicChannel.link != null) {
          tdlib.ui().openInternalLinkType(this, null, publicChannel.link, openParameters, after);
        } else {
          tdlib.ui().openChat(this, publicChannel.chatId, new TdlibUi.ChatOpenParameters().urlOpenParameters(openParameters).keepStack().after(chatId -> {
            after.runWithBool(true);
          }));
        }
        break;
      }
      case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePrivateChannel privateChannel = (TdApi.MessageSponsorTypePrivateChannel) sponsor.type;
        tdlib.ui().openUrl(this, privateChannel.inviteLink, openParameters, after);
        break;
      }
      case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeWebsite website = (TdApi.MessageSponsorTypeWebsite) sponsor.type;
        tdlib.ui().openUrl(this, website.url, openParameters, after);
        break;
      }
      default:
        Td.assertMessageSponsorType_ce9e3245();
        throw Td.unsupported(sponsor.type);
    }
  }

  public @StringRes int getSponsoredMessageButtonResId () {
    if (!isSponsoredMessage()) {
      return 0;
    }
    TdApi.MessageSponsor sponsor = sponsoredMessage.sponsor;
    switch (sponsor.type.getConstructor()) {
      case TdApi.MessageSponsorTypeBot.CONSTRUCTOR:
        return R.string.AdOpenBot;
      case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR:
        return R.string.AdOpenChannel;
      case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePublicChannel publicChannel = (TdApi.MessageSponsorTypePublicChannel) sponsor.type;
        if (publicChannel.link != null && publicChannel.link.getConstructor() == TdApi.InternalLinkTypeMessage.CONSTRUCTOR) {
          return R.string.AdOpenPost;
        }
        return R.string.AdOpenChannel;
      }
      case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR: {
        return R.string.AdOpenWebsite;
      }
      default:
        Td.assertMessageSponsorType_ce9e3245();
        throw Td.unsupported(sponsor.type);
    }
  }

  public TdApi.SponsoredMessage getSponsoredMessage () {
    return sponsoredMessage;
  }

  public String getSponsoredMessageUrl () {
    if (!isSponsoredMessage() || !Config.ALLOW_SPONSORED_MESSAGE_LINK_COPY) {
      return null;
    }

    TdApi.MessageSponsor sponsor = sponsoredMessage.sponsor;
    switch (sponsor.type.getConstructor()) {
      case TdApi.MessageSponsorTypeBot.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeBot bot = (TdApi.MessageSponsorTypeBot) sponsor.type;
        if (bot.link.getConstructor() == TdApi.InternalLinkTypeBotStart.CONSTRUCTOR) {
          TdApi.InternalLinkTypeBotStart botStart = (TdApi.InternalLinkTypeBotStart) bot.link;
          return tdlib.tMeStartUrl(botStart.botUsername, botStart.startParameter, false);
        }
        // Ignoring other types
        break;
      }
      case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePrivateChannel privateChannel = (TdApi.MessageSponsorTypePrivateChannel) sponsor.type;
        return privateChannel.inviteLink;
      }
      case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePublicChannel publicChannel = (TdApi.MessageSponsorTypePublicChannel) sponsor.type;
        if (publicChannel.link != null) {
          if (publicChannel.link.getConstructor() == TdApi.InternalLinkTypeMessage.CONSTRUCTOR) {
            return ((TdApi.InternalLinkTypeMessage) publicChannel.link).url;
          }
        } else {
          return tdlib.tMeChatUrl(publicChannel.chatId);
        }
      }
      case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeWebsite website = (TdApi.MessageSponsorTypeWebsite) sponsor.type;
        return website.url;
      }
      default:
        Td.assertMessageSponsorType_ce9e3245();
        throw Td.unsupported(sponsor.type);
    }

    return null;
  }
}
