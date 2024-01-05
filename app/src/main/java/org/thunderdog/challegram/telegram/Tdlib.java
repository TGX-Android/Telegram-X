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
 * File created on 14/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;
import androidx.core.os.CancellationSignal;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.TdlibSingleUnreadReactionsManager;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.filegen.TdlibFileGenerationManager;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.gif.GifBridge;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.sync.SyncHelper;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.EditRightsController;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppInstallationUtil;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.UserProvider;
import org.thunderdog.challegram.util.WrapperProvider;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.voip.annotation.CallState;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.collection.LongSparseLongArray;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.util.ConditionalExecutor;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.JSON;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class Tdlib implements TdlibProvider, Settings.SettingsChangeListener, DateChangeListener {
  @Override
  public final int accountId () {
    return id();
  }

  @NonNull
  @Override
  public final Tdlib tdlib () {
    return this;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Mode.NORMAL,
    Mode.DEBUG,
    Mode.SERVICE
  })
  public @interface Mode {
    int
      NORMAL = 0,
      DEBUG = 1,
      SERVICE = 2;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Status.UNKNOWN,
    Status.UNAUTHORIZED,
    Status.READY
  })
  public @interface Status {
    int
      UNKNOWN = 0,
      UNAUTHORIZED = 1,
      READY = 2;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    GiftPremiumOption.FROM_INPUT_FIELD,
    GiftPremiumOption.FROM_ATTACHMENT_MENU
  }, flag = true)
  public @interface GiftPremiumOption {
    int FROM_INPUT_FIELD = 1, FROM_ATTACHMENT_MENU = 1 << 1;
  }

  public static final int CHAT_ACCESS_TEMPORARY = 1;
  public static final int CHAT_ACCESS_OK = 0;
  public static final int CHAT_ACCESS_FAIL = -1;
  public static final int CHAT_ACCESS_BANNED = -2; // Access banned
  public static final int CHAT_ACCESS_PRIVATE = -3; // Needs an invitation

  private final Object handlerLock = new Object();
  private TdlibUi _handler;

  private final TdApi.SetTdlibParameters parameters;
  private final Client.ResultHandler configHandler = object -> {
    if (object instanceof TdApi.JsonValue) {
      TdApi.JsonValue json = (TdApi.JsonValue) object;
      setApplicationConfig(json, JSON.stringify(json));
    } else {
      Log.i("getApplicationConfig failed: %s", TD.toErrorString(object));
    }
  };
  private final Client.ResultHandler messageHandler = object -> {
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR:
        break;
      case TdApi.Message.CONSTRUCTOR:
        updateNewMessage(new TdApi.UpdateNewMessage((TdApi.Message) object), false);
        break;
      case TdApi.Messages.CONSTRUCTOR:
        // FIXME send as a single update
        for (TdApi.Message message : ((TdApi.Messages) object).messages) {
          if (message != null) {
            updateNewMessage(new TdApi.UpdateNewMessage(message), false);
          }
        }
        break;
      case TdApi.Error.CONSTRUCTOR:
        UI.showError(object);
        break;
    }
  };
  private final Client.ResultHandler doneHandler = object -> {
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR:
        UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
        break;
      case TdApi.Error.CONSTRUCTOR:
        UI.showError(object);
        break;
    }
  };
  private final Client.ResultHandler silentHandler = object -> {
    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      Log.e("TDLib Error (silenced): %s", TD.toErrorString(object));
    }
  };
  private final ResultHandler<TdApi.File> imageLoadHandler = (file, error) -> {
    if (error != null) {
      Log.e(Log.TAG_IMAGE_LOADER, "DownloadFile failed: %s", TD.toErrorString(error));
    } else {
      if (file.local.isDownloadingCompleted) {
        ImageLoader.instance().onLoad(Tdlib.this, file);
      } else if (!file.local.isDownloadingActive) {
        Log.e(Log.TAG_IMAGE_LOADER, "WARNING: Image load not started");
      }
    }
  };
  private final Client.ResultHandler profilePhotoHandler = object -> {
    if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      Log.e("setProfilePhoto failed: %s", TD.toErrorString(object));
      UI.showToast(Lang.getString(R.string.SetProfilePhotoError, TD.toErrorString(object)), Toast.LENGTH_SHORT);
    }
  };

  private static final class ClientHolder implements Client.ResultHandler, Client.ExceptionHandler {
    private final Tdlib tdlib;
    private final Client client;

    private final TdlibResourceManager resources, updates;
    private boolean running = true;

    private long initializationTime;
    private long timeWasted;

    private boolean logged;

    public boolean hasLogged () {
      boolean logged = this.logged;
      this.logged = true;
      return logged;
    }

    public final CountDownLatch closeLatch = new CountDownLatch(1);
    public int closeState;

    private static final AtomicInteger runningClients = new AtomicInteger();

    public ClientHolder (Tdlib tdlib) {
      Log.i(Log.TAG_ACCOUNTS, "Creating client #%d", runningClients.incrementAndGet());
      this.tdlib = tdlib;
      this.client = Client.create(this, this, this);
      tdlib.updateParameters(client);
      if (Config.NEED_ONLINE) {
        if (tdlib.isOnline) {
          client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(true)), tdlib.okHandler());
        }
      }
      tdlib.context.modifyClient(tdlib, client);
      this.resources = new TdlibResourceManager(tdlib, BuildConfig.TELEGRAM_RESOURCES_CHANNEL);
      this.updates = new TdlibResourceManager(tdlib, BuildConfig.TELEGRAM_UPDATES_CHANNEL);
      if (!tdlib.inRecoveryMode()) {
        init();
      }
    }

    public void runOnTdlibThread (Runnable after) {
      runOnTdlibThread(after, 0);
    }

    public void runOnTdlibThread (Runnable after, double timeoutSeconds) {
      runOnTdlibThread(after, timeoutSeconds, null);
    }

    public void runOnTdlibThread (Runnable after, double timeout, @Nullable CancellationSignal cancellationSignal) {
      client.send(new TdApi.SetAlarm(timeout), ignored -> {
        if (cancellationSignal == null || !cancellationSignal.isCanceled()) {
          after.run();
        }
      });
    }

    public void init () {
      if (initializationTime != 0)
        return;
      initializationTime = SystemClock.uptimeMillis();
      long time = SystemClock.uptimeMillis();
      StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
      final CancellationSignal openTimeoutSignal = new CancellationSignal();
      client.send(tdlib.parameters, result -> {
        long elapsed = SystemClock.uptimeMillis() - time;
        TDLib.Tag.td_init("SetTdlibParameters response in %dms, accountId:%d, ok:%b", elapsed, tdlib.accountId, result.getConstructor() == TdApi.Ok.CONSTRUCTOR);
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          Tracer.onTdlibFatalError(tdlib, TdApi.SetTdlibParameters.class, (TdApi.Error) result, stackTrace);
        }
        openTimeoutSignal.cancel();
        tdlib.setIsOptimizing(false);
      });
      runOnTdlibThread(() ->
        tdlib.setIsOptimizing(true)
      , 1.0, openTimeoutSignal);
      final TdApi.Function<?> startup;
      if (tdlib.accountId == 0 && Settings.instance().needProxyLegacyMigrateCheck()) {
        startup = new TdApi.GetProxies();
      } else {
        startup = new TdApi.SetAlarm(0);
      }
      client.send(startup, (result) -> {
        if (result.getConstructor() == TdApi.Proxies.CONSTRUCTOR) {
          TdApi.Proxy[] proxies = ((TdApi.Proxies) result).proxies;
          boolean foundEnabledProxy = false;
          for (TdApi.Proxy proxy : proxies) {
            TdApi.InternalLinkTypeProxy proxyDetails = new TdApi.InternalLinkTypeProxy(
              proxy.server,
              proxy.port,
              proxy.type
            );
            int proxyId = Settings.instance().addOrUpdateProxy(proxyDetails, null, proxy.isEnabled);
            if (proxy.isEnabled) {
              tdlib.setEffectiveProxyId(proxyId);
              tdlib.setProxy(proxyId, proxyDetails);
              foundEnabledProxy = true;
            }
          }
          if (!foundEnabledProxy) {
            tdlib.setEffectiveProxyId(Settings.PROXY_ID_NONE);
          }
        } else {
          int proxyId = Settings.instance().getEffectiveProxyId();
          Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
          if (proxy != null) {
            tdlib.setProxy(proxyId, proxy.proxy);
          } else {
            tdlib.disableProxy();
          }
        }
      });
      client.send(new TdApi.GetApplicationConfig(), tdlib.configHandler);
    }

    public void sendFakeUpdate (TdApi.Update update, boolean forceUi) {
      if (forceUi) {
        tdlib.processUpdate(this, update);
      } else {
        runOnTdlibThread(() -> tdlib.processUpdate(this, update));
      }
    }

    @Override
    public void onException (Throwable e) {
      Tracer.onTdlibHandlerError(e);
    }

    public void stop () {
      running = false;
    }

    @Override
    public void onResult (TdApi.Object object) {
      if (running) {
        long ms = SystemClock.uptimeMillis();
        if (object instanceof TdApi.Update) {
          tdlib.processUpdate(this, (TdApi.Update) object);
        } else {
          Log.e("Invalid update type: %s", object);
        }
        if (Log.needMeasureLaunchSpeed()) {
          ms = SystemClock.uptimeMillis() - ms;
          if (ms > 100) {
            Log.e("%s took %dms", object.toString(), ms);
          }
          timeWasted += ms;
        }
      } else {
        Log.w("Ignored update: %s", object);
      }
    }

    public void sendClose () {
      client.send(new TdApi.Close(), tdlib.okHandler());
    }

    public void close () {
      Log.i(Log.TAG_ACCOUNTS, "Calling client.close(), accountId:%d", tdlib.accountId);
      long ms = SystemClock.uptimeMillis();
      // Nothing to do anymore?
      // client.close();
      Log.i(Log.TAG_ACCOUNTS, "client.close() done in %dms, accountId:%d, accountsNum:%d", SystemClock.uptimeMillis() - ms, tdlib.accountId, runningClients.decrementAndGet());
    }

    public long timeSinceInitializationMs () {
      return initializationTime != 0 ? SystemClock.uptimeMillis() - initializationTime : -1;
    }

    public long timeWasted () {
      return timeWasted;
    }
  }

  private final Comparator<TdApi.User> userComparator = new UserComparator(this);
  private final Comparator<UserProvider> userProviderComparator = new UserProviderComparator(userComparator);

  // Context

  private final TdlibManager context;
  private final int accountId;

  private ClientHolder client;
  private @ConnectionState int connectionState = ConnectionState.UNKNOWN;

  private final Object clientLock = new Object();
  private final Object dataLock = new Object();
  private final HashMap<Long, TdApi.Chat> chats = new HashMap<>();
  private final HashMap<Long, TdApi.ChatActiveStories> activeStories = new HashMap<>();
  private final SparseIntArray storyListChatCount = new SparseIntArray();
  private final SparseArrayCompat<StoryList> storyLists = new SparseArrayCompat<>();
  private final HashMap<String, TdApi.ForumTopicInfo> forumTopicInfos = new HashMap<>();
  private final HashMap<String, TdlibChatList> chatLists = new HashMap<>();
  private final StickerSet
    animatedTgxEmoji = new StickerSet(AnimatedEmojiListener.TYPE_TGX, "AnimatedTgxEmojies", false),
    animatedDiceExplicit = new StickerSet(AnimatedEmojiListener.TYPE_DICE, "BetterDice", true);
  private final HashSet<Long> knownChatIds = new HashSet<>();
  private final HashMap<Long, Integer> chatOnlineMemberCount = new HashMap<>();
  private final TdlibCache cache;
  private final TdlibEmojiManager emoji;
  private final TdlibEmojiReactionsManager reactions;
  private final TdlibSingleton<TdApi.Stickers> genericReactionEffects;
  private final TdlibListeners listeners;
  private final TdlibFilesManager filesManager;
  private final TdlibStatusManager statusManager;
  private final TdlibContactManager contactManager;
  private final TdlibQuickAckManager quickAckManager;
  private final TdlibSettingsManager settingsManager;
  private final TdlibWallpaperManager wallpaperManager;
  private final TdlibNotificationManager notificationManager;
  private final TdlibFileGenerationManager fileGenerationManager;
  private final TdlibSingleUnreadReactionsManager unreadReactionsManager;
  private final TdlibMessageViewer messageViewer;

  private final HashSet<Long> channels = new HashSet<>();
  private final LongSparseLongArray accessibleChatTimers = new LongSparseLongArray();

  private long authorizationDate = 0;
  private int supergroupMaxSize = 100000;
  private int maxBioLength = 70;
  private int chatFolderMaxCount = 10, folderChosenChatMaxCount = 100;
  private int addedShareableChatFolderMaxCount = 2, chatFolderInviteLinkMaxCount = 3;
  private long chatFolderUpdatePeriod = 300; // Seconds
  private int activeStoryCountMax = 100, weeklySentStoryCountMax = 700,monthlySentStoryCountMax = 3000;
  private boolean canUseTextEntitiesInStoryCaptions;
  private int storyCaptionLengthMax = 2048;
  private int storySuggestedReactionAreaCountMax = 5;
  private int storyViewersExpirationDelay = 86400;
  private int storyStealhModeCooldownPeriod = 3600, storyStealthModeFuturePeriod = 1500, storyStealthModePastPeriod = 300;
  private boolean isPremium, isPremiumAvailable;
  private @GiftPremiumOption int giftPremiumOptions;
  private boolean suggestOnlyApiStickers;
  private int maxGroupCallParticipantCount = 10000;
  private long roundVideoBitrate = 1000, roundAudioBitrate = 64, roundVideoMaxSize = 12582912, roundVideoDiameter = 384;
  private int forwardMaxCount = 100;
  private int groupMaxSize = 200;
  private int pinnedChatsMaxCount = 5, pinnedArchivedChatsMaxCount = 100, pinnedForumTopicMaxCount = 5;
  private int favoriteStickersMaxCount = 5;
  private double emojiesAnimatedZoom = .75f;
  private boolean youtubePipDisabled, qrLoginCamera, dialogFiltersTooltip, dialogFiltersEnabled, forceUrgentInAppUpdate;
  private String qrLoginCode;
  private String[] diceEmoji, activeEmojiReactions;
  private TdApi.ReactionType defaultReactionType;
  private final Map<String, TGReaction> cachedReactions = new HashMap<>();
  private boolean callsEnabled = true, expectBlocking, isLocationVisible;
  private boolean canIgnoreSensitiveContentRestrictions, ignoreSensitiveContentRestrictions;
  private boolean canArchiveAndMuteNewChatsFromUnknownUsers;
  private RtcServer[] rtcServers;

  private long unixTime;
  private long unixTimeReceived;
  private long utcTimeOffset;

  private int storyStealthModeActiveUntilDate;
  private int storyStealthModeCooldownUntilDate;

  private Boolean disableTopChats;
  private boolean disableSentScheduledMessageNotifications;
  private long antiSpamBotUserId;
  private long channelBotUserId = TdConstants.TELEGRAM_CHANNEL_BOT_ACCOUNT_ID;
  private long groupAnonymousBotUserId;
  private long repliesBotUserId = TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID;
  private long repliesBotChatId = ChatId.fromUserId(TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID);
  private long telegramServiceNotificationsChatId = TdConstants.TELEGRAM_ACCOUNT_ID;
  private String animationSearchBotUsername = "gif";
  private String venueSearchBotUsername = "foursquare";
  private String photoSearchBotUsername = "pic";
  // private String animatedEmojiStickerSetName = "animatedemojies", animatedDiceStickerSetName;

  private String languagePackId;
  private String suggestedLanguagePackId;
  private TdApi.LanguagePackInfo suggestedLanguagePackInfo;

  private long connectionLossTime = SystemClock.uptimeMillis();

  private String tMeUrl;

  private long callConnectTimeoutMs = 30000;
  private long callPacketTimeoutMs = 10000;

  private final Map<String, TdlibCounter> counters = new HashMap<>();
  private final TdlibBadgeCounter tempCounter = new TdlibBadgeCounter();
  private final TdlibBadgeCounter unreadCounter = new TdlibBadgeCounter();

  @NonNull
  public TdlibCounter getCounter (@NonNull TdApi.ChatList chatList) {
    final String key = TD.makeChatListKey(chatList);
    TdlibCounter counter = counters.get(key);
    if (counter == null) {
      counter = new TdlibCounter(-1, -1, -1, -1, -1, -1, -1);
      counters.put(key, counter);
    }
    return counter;
  }

  private int maxMessageCaptionLength = 1024;
  private int maxMessageTextLength = 4000;

  private int installedStickerSetLimit = 200;

  private boolean disableContactRegisteredNotifications = false;

  private int[] favoriteStickerIds;
  private int unreadTrendingStickerSetsCount;

  private @Mode int instanceMode;
  private boolean instancePaused;
  private final AtomicInteger referenceCount = new AtomicInteger(0);

  /*package*/ Tdlib (TdlibAccount account, @Mode int mode) {
    this.context = account.context;
    this.accountId = account.id;
    this.instanceMode = mode;
    this.hasUnprocessedPushes = account.hasUnprocessedPushes();
    this.isLoggingOut = account.isLoggingOut();

    if (mode == Mode.SERVICE) {
      this.parameters = new TdApi.SetTdlibParameters(
        false,
        null, null, null, // updateParameters
        false,
        false,
        false,
        false,
        BuildConfig.TELEGRAM_API_ID,
        BuildConfig.TELEGRAM_API_HASH,
        null, null, null, // updateParameters
        BuildConfig.VERSION_NAME,
        false,
        false
      );
    } else {
      this.parameters = new TdApi.SetTdlibParameters(
        false,
        null, null, null, // updateParameters
        true,
        true,
        true,
        true,
        BuildConfig.TELEGRAM_API_ID,
        BuildConfig.TELEGRAM_API_HASH,
        null, null, null, // updateParameters
        BuildConfig.VERSION_NAME,
        false,
        false
      );
    }

    boolean needMeasure = Log.needMeasureLaunchSpeed();
    long ms = needMeasure ? SystemClock.uptimeMillis() : 0;
    this.listeners = new TdlibListeners(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.listeners -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.cache = new TdlibCache(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.cache -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.emoji = new TdlibEmojiManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.emoji -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.reactions = new TdlibEmojiReactionsManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.reaction -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.genericReactionEffects = new TdlibSingleton<>(this, () -> new TdApi.GetCustomEmojiReactionAnimations());
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.genericReactionEffects -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.filesManager = new TdlibFilesManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.filesManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.statusManager = new TdlibStatusManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.statusManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.contactManager = new TdlibContactManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.contactManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.quickAckManager = new TdlibQuickAckManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.quickAckManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.settingsManager = new TdlibSettingsManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.settingsManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.wallpaperManager = new TdlibWallpaperManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.wallpaperManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.notificationManager = new TdlibNotificationManager(this, context.notificationQueue());
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.notificationManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.fileGenerationManager = new TdlibFileGenerationManager(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.fileGenerationManager -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.messageViewer = new TdlibMessageViewer(this);
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.messageViewer -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }
    this.unreadReactionsManager = new TdlibSingleUnreadReactionsManager(this);
    this.applicationConfigJson = settings().getApplicationConfig();
    if (!StringUtils.isEmpty(applicationConfigJson)) {
      TdApi.JsonValue value = JSON.parse(applicationConfigJson);
      if (value != null) {
        processApplicationConfig(value);
      }
    }
    if (needMeasure) {
      Log.v("INITIALIZATION: Tdlib.applicationConfig -> %dms", SystemClock.uptimeMillis() - ms);
      ms = SystemClock.uptimeMillis();
    }

    synchronized (clientLock) {
      if (client == null)
        client = newClient();
    }
    if (needMeasure) {
      Log.i("INITIALIZATION: Tdlib.newClient() -> %dms", SystemClock.uptimeMillis() - ms);
    }

    Settings.instance().addNewSettingsListener(this);
  }

  @Override
  public void onSettingsChanged (long newSettings, long oldSettings) {
    if (BitwiseUtils.hasFlag(newSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) != BitwiseUtils.hasFlag(oldSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) && !isDebugInstance()) {
      if (BitwiseUtils.hasFlag(newSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) && !animatedDiceExplicit.isLoaded()) {
        animatedDiceExplicit.load(this);
      } else {
        listeners().notifyAnimatedEmojiListeners(AnimatedEmojiListener.TYPE_DICE);
      }
    }
  }

  // Device token

  void checkDeviceToken () {
    runOnTdlibThread(() -> checkDeviceTokenImpl(null));
  }

  synchronized void checkDeviceTokenImpl (@Nullable Runnable onDone) {
    final TdApi.DeviceToken deviceToken = context.getToken();
    if (deviceToken == null || authorizationStatus() != Status.READY)
      return;
    long myUserId = myUserId();
    if (myUserId == 0)
      return;
    long[] availableUserIds = context.availableUserIds(instanceMode);
    long[] otherUserIds = ArrayUtils.removeElement(availableUserIds, Arrays.binarySearch(availableUserIds, myUserId));
    if (TdlibSettingsManager.checkRegisteredDeviceToken(id(), myUserId, deviceToken, otherUserIds, false)) {
      Log.i(Log.TAG_FCM, "Device token already registered. accountId:%d", accountId);
      context.setDeviceRegistered(accountId, true);
      U.run(onDone);
      return;
    }
    Log.i(Log.TAG_FCM, "Registering device token... accountId:%d", accountId);
    context.setDeviceRegistered(accountId, false);
    incrementReferenceCount(REFERENCE_TYPE_JOB);
    client().send(new TdApi.RegisterDevice(deviceToken, otherUserIds), result -> {
      switch (result.getConstructor()) {
        case TdApi.PushReceiverId.CONSTRUCTOR:
          Log.i(Log.TAG_FCM, "Successfully registered device token:%s, accountId:%d, otherUserIdsCount:%d", deviceToken, accountId, otherUserIds.length);
          Settings.instance().putNotificationReceiverId(((TdApi.PushReceiverId) result).id, accountId);
          TdlibSettingsManager.setRegisteredDevice(accountId, myUserId, deviceToken, otherUserIds);
          context().setDeviceRegistered(accountId, true);
          context().unregisterDevices(instanceMode, accountId, availableUserIds);
          U.run(onDone);
          break;
        case TdApi.Error.CONSTRUCTOR: {
          TdApi.Error error = (TdApi.Error) result;
          int seconds = Math.max(5, TD.getFloodErrorSeconds(error.code, error.message, 5));
          if (seconds > 60 && isDebugInstance()) {
            Log.e("Unable to register device token, flood is %d seconds, ignoring: %s, accountId:%d", seconds, TD.toErrorString(result), accountId);
            context.setDeviceRegistered(accountId, true);
            U.run(onDone);
          } else {
            Log.e("Unable to register device token, retrying in %d seconds: %s, accountId:%d", seconds, TD.toErrorString(result), accountId);
            client().send(new TdApi.SetAlarm(seconds), ignored -> checkDeviceTokenImpl(onDone));
          }
          break;
        }
      }
      decrementReferenceCount(REFERENCE_TYPE_JOB);
    });
  }

  // Use count

  private static final int REFERENCE_TYPE_UI = 0;
  private static final int REFERENCE_TYPE_JOB = 1;
  private static final int REFERENCE_TYPE_SYNC = 3;
  private static final int REFERENCE_TYPE_NOTIFICATION = 4;
  private static final int REFERENCE_TYPE_LOCATION = 5;
  private static final int REFERENCE_TYPE_CALL = 6;
  private static final int REFERENCE_TYPE_REQUEST_EXECUTION = 7;
  private static final int REFERENCE_TYPE_MESSAGE = 8;
  private static final int REFERENCE_TYPE_TASK = 9;
  private static final int REFERENCE_TYPE_TASK_EXECUTION = 10;

  public void changeLocationReferenceCount (int deltaCount) {
    if (deltaCount > 0) {
      do {
        incrementReferenceCount(REFERENCE_TYPE_LOCATION);
      } while (--deltaCount > 0);
    } else if (deltaCount < 0) {
      do {
        decrementReferenceCount(REFERENCE_TYPE_LOCATION);
      } while (++deltaCount < 0);
    }
  }

  public void incrementCallReferenceCount () {
    incrementReferenceCount(REFERENCE_TYPE_CALL);
  }

  public void decrementCallReferenceCount () {
    decrementReferenceCount(REFERENCE_TYPE_CALL);
  }

  public void incrementUiReferenceCount () {
    incrementReferenceCount(REFERENCE_TYPE_UI);
  }

  public void decrementUiReferenceCount () {
    decrementReferenceCount(REFERENCE_TYPE_UI);
  }

  void incrementNotificationReferenceCount () {
    incrementReferenceCount(REFERENCE_TYPE_NOTIFICATION);
  }

  void decrementNotificationReferenceCount () {
    decrementReferenceCount(REFERENCE_TYPE_NOTIFICATION);
  }

  void incrementJobReferenceCount () {
    incrementReferenceCount(REFERENCE_TYPE_JOB);
  }

  void decrementJobReferenceCount () {
    decrementReferenceCount(REFERENCE_TYPE_JOB);
  }

  private void incrementReferenceCount (int type) {
    boolean wakeup;
    int referenceCount;
    synchronized (clientLock) {
      referenceCount = this.referenceCount.incrementAndGet();
      wakeup = referenceCount == 1;
      Log.v(Log.TAG_ACCOUNTS, "accountId:%d, referenceCount:%d, type:%d", accountId, referenceCount, type);
      if (type == REFERENCE_TYPE_UI)
        account().markAsUsed();
      schedulePause();
    }
    if (wakeup) {
      wakeUp();
      if (type == REFERENCE_TYPE_UI)
        context.checkPauseTimeouts(account());
    }
    if (wakeup) {
      noReferenceListeners.notifyConditionChanged();
    }
  }

  private void decrementReferenceCount (int type) {
    int referenceCount;
    synchronized (clientLock) {
      referenceCount = this.referenceCount.decrementAndGet();
      if (referenceCount < 0) {
        RuntimeException e = new IllegalStateException("type == " + type);
        Tracer.onOtherError(e);
        throw e;
      }
      Log.v(Log.TAG_ACCOUNTS, "accountId:%d, referenceCount:%d, type:%d", accountId, referenceCount, type);
      schedulePause();
    }
    if (referenceCount == 0) {
      noReferenceListeners.notifyConditionChanged();
    }
  }

  private long restartTimeout, restartScheduledTime;

  // keep_alive

  private boolean keepAlive, hasUnprocessedPushes, isLoggingOut, ignoreNotificationUpdates;

  private void setHasUnprocessedPushes (boolean hasUnprocessedPushes) {
    if (this.hasUnprocessedPushes != hasUnprocessedPushes) {
      if (hasUnprocessedPushes && isConnected()) {
        return;
      }
      this.hasUnprocessedPushes = hasUnprocessedPushes;
      context().setHasUnprocessedPushes(accountId, hasUnprocessedPushes);
    }
  }

  private boolean checkKeepAlive () {
    boolean keepAlive = havePendingNotifications || hasUnprocessedPushes;
    if (this.keepAlive != keepAlive) {
      this.keepAlive = keepAlive;
      context().setKeepAlive(accountId, keepAlive);
    }
    return keepAlive;
  }

  // logging_out, logged_out

  @TdlibThread
  private void setLoggingOut (boolean isLoggingOut) {
    if (this.isLoggingOut != isLoggingOut) {
      this.isLoggingOut = isLoggingOut;
      if (isLoggingOut) {
        ignoreNotificationUpdates = true;
      }
      context().setLoggingOut(accountId, isLoggingOut);
      checkPauseTimeout();
      if (isLoggingOut) {
        notifications().onDropNotificationData(true);
      }
    }
  }

  public void deleteAllFiles (@Nullable RunnableBool after) {
    Log.i("Clearing data... accountId:%d", accountId);
    long ms = SystemClock.uptimeMillis();
    client().send(new TdApi.OptimizeStorage(0, 0, 0, 0, new TdApi.FileType[]{
      // new TdApi.FileTypeNone(),
      new TdApi.FileTypeAnimation(),
      new TdApi.FileTypeAudio(),
      new TdApi.FileTypeDocument(),
      new TdApi.FileTypePhoto(),
      new TdApi.FileTypeProfilePhoto(),
      new TdApi.FileTypeSecret(),
      new TdApi.FileTypeSecretThumbnail(),
      new TdApi.FileTypeSecure(),
      new TdApi.FileTypeSticker(),
      new TdApi.FileTypeThumbnail(),
      new TdApi.FileTypeUnknown(),
      new TdApi.FileTypeVideo(),
      new TdApi.FileTypeVideoNote(),
      new TdApi.FileTypeVoiceNote(),
      new TdApi.FileTypeWallpaper()
    }, null, null, false, 0), result -> {
      switch (result.getConstructor()) {
        case TdApi.StorageStatistics.CONSTRUCTOR:
          Log.i("Cleared files in %dms, accountId:%d", SystemClock.uptimeMillis() - ms, accountId);
          break;
        case TdApi.Error.CONSTRUCTOR:
          Log.e("Unable to delete files data:%s, accountId:%d", TD.toErrorString(result), accountId);
          break;
      }
      if (after != null) {
        after.runWithBool(result.getConstructor() == TdApi.StorageStatistics.CONSTRUCTOR);
      }
    });
  }

  public void closeAllSecretChats (@Nullable Runnable after) {
    AtomicInteger remaining = new AtomicInteger(2);
    AtomicInteger closingCount = new AtomicInteger(0);
    RunnableData<TdApi.Chat> perChatCallback = chat -> {
      TdApi.SecretChat secretChat = chatToSecretChat(chat.id);
      if (secretChat != null && secretChat.state.getConstructor() != TdApi.SecretChatStateClosed.CONSTRUCTOR) {
        closingCount.incrementAndGet();
        client().send(new TdApi.CloseSecretChat(secretChat.id), closeResult -> {
          if (closingCount.decrementAndGet() == 0 && remaining.get() == 0) {
            U.run(after);
          }
        });
      }
    };
    RunnableBool onEndCallback = isFinal -> {
      if (isFinal && remaining.decrementAndGet() == 0) {
        if (closingCount.get() == 0) {
          U.run(after);
        }
      }
    };
    chatList(ChatPosition.CHAT_LIST_MAIN).loadAll(perChatCallback, onEndCallback);
    chatList(ChatPosition.CHAT_LIST_ARCHIVE).loadAll(perChatCallback, onEndCallback);
  }

  void cleanupUnauthorizedData (@Nullable Runnable after) {
    awaitInitialization(() -> {
      if (!account().isUnauthorized() || account().isLoggingOut()) {
        if (after != null)
          after.run();
      } else {
        incrementReferenceCount(REFERENCE_TYPE_JOB);
        deleteAllFiles(success -> {
          if (success) {
            context().markNoPrivateData(accountId);
          }
          decrementReferenceCount(REFERENCE_TYPE_JOB);
        });
      }
    });
  }

  // restart

  public void pause (Runnable onPause) {
    if (!shouldPause()) {
      U.run(onPause);
      return;
    }
    runOnUiThread(() -> {
      if (isPaused()) {
        U.run(onPause);
      } else {
        awaitClose(onPause, true);
        doPause();
      }
    });
  }

  private boolean shouldPause () {
    if (instancePaused)
      return false; // Instance already paused
    if (authorizationStatus() == Status.UNKNOWN)
      return false; // TDLib takes too long to launch. Give it a chance to initialize
    if ((authorizationState != null && authorizationState.getConstructor() != TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR))
      return false; // User has started authorization process. Give them a chance to complete it.
    /*if (context().hasUi()) {
        // TODO limit couple most recent used accounts?
      }*/
    return !checkKeepAlive() && !account().keepAlive() && account().isDeviceRegistered() && getReferenceCount() == 0;
  }

  void checkPauseTimeout () {
    synchronized (clientLock) {
      schedulePause();
    }
  }

  private long getPauseTimeout () {
    if (Settings.instance().forceTdlibRestart())
      return TimeUnit.SECONDS.toMillis(1);
    if (!context().hasUi())
      return TimeUnit.SECONDS.toMillis(5); // No UI (running in the background), no limits
    int num = context.getActiveAccountsNum();
    if (num == 1)
      return TimeUnit.MINUTES.toMillis(15); // User has only one account
    int index = context.getAccountUsageIndex(accountId, TimeUnit.MINUTES.toMillis(15));
    if (index == -1)
      return TimeUnit.SECONDS.toMillis(5); // Instance was not used within last 15 minutes
    if (index < 3)
      return TimeUnit.MINUTES.toMillis(15); // Account is within 3 last used accounts (including current)
    if (index < 5)
      return TimeUnit.MINUTES.toMillis(7); // Account is within 5 last used accounts
    if (index < 10)
      return TimeUnit.MINUTES.toMillis(2); // Account is within 10 last used accounts
    // User has used more than 10 accounts, killing the oldest ones within 5 seconds
    return TimeUnit.SECONDS.toMillis(5); //  seconds
  }

  private void schedulePause () {
    if (shouldPause()) {
      long timeout = getPauseTimeout();
      if (restartScheduledTime == 0) {
        Log.i(Log.TAG_ACCOUNTS, "Scheduling TDLib restart, accountId:%d, timeout:%d", accountId, timeout);
        restartScheduledTime = SystemClock.uptimeMillis();
        restartTimeout = timeout;
      } else if (restartTimeout != timeout) {
        long oldTimeout = restartTimeout;
        restartTimeout = timeout;
        long newTimeout = timeout + restartScheduledTime - SystemClock.uptimeMillis();
        Log.i(Log.TAG_ACCOUNTS, "Rescheduling TDLib restart, accountId:%d, timeout:%d (%d->%d)", accountId, newTimeout, oldTimeout, timeout);
        ui().removeMessages(MSG_ACTION_PAUSE);
        timeout = newTimeout;
      } else {
        return;
      }
      if (timeout > 0) {
        ui().sendMessageDelayed(ui().obtainMessage(MSG_ACTION_PAUSE), timeout);
      } else {
        doPauseImpl();
      }
    } else if (restartScheduledTime != 0) {
      restartScheduledTime = 0;
      Log.i(Log.TAG_ACCOUNTS, "Canceling TDLib restart, accountId:%d, referenceCount:%d, keepAlive:%b", accountId, getReferenceCount(), account().keepAlive());
      ui().removeMessages(MSG_ACTION_PAUSE);
    }
  }

  private void doPauseImpl () {
    if (restartScheduledTime == 0)
      return;
    if (client == null || instancePaused) {
      Log.e(Log.TAG_ACCOUNTS, "Cannot pause TDLib instance, because it is already paused. accountId:%d", accountId);
      return;
    }
    restartScheduledTime = 0;
    if (!shouldPause()) {
      Log.i(Log.TAG_ACCOUNTS, "Cannot restart TDLib, because it is in use. referenceCount:%d, accountId:%d", getReferenceCount(), accountId);
      return;
    }
    Log.i(Log.TAG_ACCOUNTS, "Pausing TDLib instance, because it is unused, accountId:%d", accountId);
    instancePaused = true;
    client.sendClose();
  }

  private void doPause () {
    synchronized (clientLock) {
      doPauseImpl();
    }
  }

  // Stress-test

  private int stressTest;

  public void stressTest (int count) {
    if (count <= 0)
      return;
    this.stressTest += count;
    clientHolder().sendClose();
  }

  // Database erasing

  private RunnableBool pendingEraseActor;

  public void eraseTdlibDatabase (@NonNull RunnableBool onDelete) { // FIXME TDLib: Proper database optimization
    boolean success;
    synchronized (clientLock) {
      if (pendingEraseActor != null)
        return;
      if (client != null && !instancePaused && !inRecoveryMode()) {
        pendingEraseActor = onDelete;
        client.sendClose();
        return;
      } else {
        pendingEraseActor = null;
        success = eraseTdlibDatabaseImpl();
      }
    }
    onDelete.runWithBool(success);
  }

  private boolean eraseTdlibFilesImpl () {
    boolean success = true;
    for (String directory : TdlibManager.getAllTdlibDirectories(false)) {
      File file = new File(parameters.filesDirectory, directory);
      if (file.exists()) {
        success = FileUtils.delete(file, true) && success;
      }
    }
    for (String directory : TdlibManager.getAllTdlibDirectories(true)) {
      File file = new File(parameters.databaseDirectory, directory);
      if (file.exists()) {
        success = FileUtils.delete(file, true) && success;
      }
    }
    return success;
  }

  private boolean needDropNotificationIdentifiers;

  private boolean eraseTdlibDatabaseImpl () {
    File dbFile;

    boolean success = true;
    dbFile = new File(parameters.databaseDirectory, "db.sqlite-wal");
    success = (!dbFile.exists() || dbFile.delete()) && success;
    dbFile = new File(parameters.databaseDirectory, "db.sqlite-shm");
    success = (!dbFile.exists() || dbFile.delete()) && success;
    dbFile = new File(parameters.databaseDirectory, "db.sqlite");
    success = (!dbFile.exists() || dbFile.delete()) && success;

    if (success) {
      needDropNotificationIdentifiers = true;
      // notifications().onDropNotificationData(true);
    }

    success = eraseTdlibFilesImpl() && success;

    if (success) {
      Log.i("Successfully deleted TDLib database, accountId:%d", accountId);
    } else {
      Log.e("Failed to delete TDLib database, accountId:%d", accountId);
    }

    return success;
  }

  // Base

  private TdApi.AuthorizationState authorizationState;

  public TdApi.AuthorizationState authorizationState () {
    return authorizationState;
  }

  public boolean switchToNextAuthorizedAccount () {
    if (context().preferredAccountId() == accountId) {
      int nextAccountId = context().findNextAccountId(accountId);
      if (nextAccountId != TdlibAccount.NO_ID) {
        context().changePreferredAccountId(nextAccountId, TdlibManager.SWITCH_REASON_UNAUTHORIZED);
        return true;
      }
    }
    return false;
  }

  public void signOut () {
    switchToNextAuthorizedAccount();
    boolean isMulti = context().isMultiUser();
    String name = isMulti ? TD.getUserName(account().getFirstName(), account().getLastName()) : null;
    incrementReferenceCount(REFERENCE_TYPE_JOB);
    /*deleteAllFiles(ignored -> */client().send(new TdApi.LogOut(), result -> {
      if (isMulti) {
        UI.showToast(Lang.getString(R.string.SignedOutAs, name), Toast.LENGTH_SHORT);
      }
      decrementReferenceCount(REFERENCE_TYPE_JOB);
    })/*)*/;
  }

  public void destroy () {
    client().send(new TdApi.Destroy(), okHandler());
  }

  public boolean isCurrent () {
    return context().preferredAccountId() == accountId;
  }

  public String tdlibCommitHash () {
    return context().tdlibCommitHash();
  }

  public String tdlibCommitHashFull () {
    return context().tdlibCommitHashFull();
  }

  public String tdlibVersion () {
    return context().tdlibVersion();
  }

  public boolean isAuthorized () {
    return authorizationState != null && authorizationState.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR;
  }

  public boolean isUnauthorized () {
    if (authorizationState != null) {
      switch (authorizationState.getConstructor()) {
        case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR:
        case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
          return true;
        case TdApi.AuthorizationStateReady.CONSTRUCTOR:
          return false;
        case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
        case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
          break; // because we cannot know for sure
        default:
          throw new AssertionError(authorizationState);
      }
    }
    return false;
  }

  public @Status int authorizationStatus () {
    synchronized (dataLock) {
      return getStatus(authorizationState);
    }
  }

  @TdlibThread
  private void updateAuthState (ClientHolder context, TdApi.AuthorizationState newAuthState) {
    final @Status int prevStatus = authorizationStatus();
    synchronized (dataLock) {
      this.authorizationState = newAuthState;
    }
    //noinspection SwitchIntDef
    switch (newAuthState.getConstructor()) {
      case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
      case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
        this.connectionState = ConnectionState.UNKNOWN;
        break;
    }
    final @Status int newStatus = authorizationStatus();

    closeListeners.notifyConditionChanged(true);
    readyOrWaitingForDataListeners.notifyConditionChanged(false);

    if (prevStatus == Status.UNKNOWN && newStatus != Status.UNKNOWN) {
      synchronized (dataLock) {
        resetChatsData();
      }
    }

    switch (newStatus) {
      case Status.UNKNOWN: {
        if (newAuthState.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
          RunnableBool eraseActor;
          boolean eraseSuccess;

          boolean forceErase = false;
          if (isLoggingOut) { // FIXME TDLib: AuthorizationStateLoggedOut
            setLoggingOut(false);
            forceErase = true;

            activeCalls.clear();
            setHaveActiveCalls(false);
          }

          synchronized (clientLock) {
            client.closeState++; // 1
            client.stop();
            client.closeState++; // 2
            client.close();
            client.closeState++; // 3
            resetState();
            synchronized (dataLock) {
              resetContextualData();
            }
            client.closeState++; // 4
            listeners.performRestart();
            client.closeState++; // 5
            ImageLoader.instance().clear(accountId, true);
            client.closeState++; // 6

            if (pendingEraseActor != null || forceErase) {
              eraseActor = pendingEraseActor;
              pendingEraseActor = null;
              eraseSuccess = eraseTdlibDatabaseImpl();
            } else {
              eraseActor = null;
              eraseSuccess = false;
            }

            client.closeState++; // 7

            CountDownLatch prevLatch = client.closeLatch;
            if (stressTest == 0 && (instancePaused || shouldPause())) {
              client = null;
            } else {
              if (stressTest > 0)
                stressTest--;
              client = newClient();
              schedulePause();
            }
            prevLatch.countDown();
          }

          if (eraseActor != null) {
            eraseActor.runWithBool(eraseSuccess);
          }
        }
        break;
      }
      case Status.UNAUTHORIZED: {
        if (newAuthState.getConstructor() == TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR) {
          setLoggingOut(true);
        } else {
          synchronized (dataLock) {
            resetChatsData();
          }
        }
        break;
      }
      case Status.READY: {
        setLoggingOut(false);
        break;
      }
      default:
        throw new IllegalStateException(Integer.toString(newStatus));
    }
    if (newStatus != Status.UNKNOWN && Log.needMeasureLaunchSpeed() && !context.hasLogged()) {
      long timeSinceInitialization = context.timeSinceInitializationMs();
      long timeWasted = context.timeWasted();
      Log.v("INITIALIZATION: TDLIB FINISHED INITIALIZATION & SENT VALID AUTH STATE IN %dMS, WASTED: %dMS", timeSinceInitialization - timeWasted, timeWasted);
    }
    Log.i(Log.TAG_ACCOUNTS, "updateAuthState accountId:%d %s", accountId, newAuthState.getClass().getSimpleName());
    if (newStatus == Status.READY) {
      setNeedPeriodicSync(true);
    } else if (newStatus == Status.UNAUTHORIZED) {
      setNeedPeriodicSync(false);
    }

    final long myUserId = myUserId();
    context().onAuthStateChanged(this, newAuthState, newStatus, myUserId);
    listeners().updateAuthorizationState(newAuthState);
    if (newStatus != Status.READY) {
      startupPerformed = false;
    }
    setNeedTimeZoneListener(newStatus != Status.UNKNOWN);
    if (prevStatus == Status.UNKNOWN && newStatus != prevStatus) {
      onInitialized();
    }
    if (prevStatus != Status.READY && newStatus == Status.READY) {
      runStartupChecks();
    }
    if (prevStatus != Status.UNAUTHORIZED && newStatus == Status.UNAUTHORIZED) {
      Log.i("Performing account cleanup for accountId:%d", accountId);
      listeners().performCleanup();
    } else if (newStatus == Status.READY) {
      onPerformStartup();
      TdApi.User myUser = cache().myUser();
      if (myUser != null) {
        context().onUpdateAccountProfile(id(), myUser, true);
      }
    }
    if (newStatus == Status.UNAUTHORIZED)
      checkChangeLogs(false, false);

    if (newStatus == Status.READY && stressTest > 0) {
      clientHolder().sendClose();
    }
  }

  private boolean needTimeZoneListener;

  private void setNeedTimeZoneListener (boolean needTimeZoneListener) {
    if (this.needTimeZoneListener != needTimeZoneListener) {
      this.needTimeZoneListener = needTimeZoneListener;
      if (needTimeZoneListener) {
        context.dateManager().addListener(this);
      } else {
        context.dateManager().removeListener(this);
      }
    }
  }

  @Override
  public void onTimeChanged () {
    updateUtcTimeOffset();
  }

  @Override
  public void onTimeZoneChanged () {
    updateUtcTimeOffset();
  }

  private static TdApi.FormattedText makeUpdateText (String version, String changeLog) {
    String text = Lang.getStringSecure(R.string.ChangeLogText, version, changeLog);
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text, null);
    //noinspection UnsafeOptInUsageError
    Td.parseMarkdown(formattedText);
    return formattedText;
  }

  private static void makeUpdateText (int major, int agesSinceBirthdate, int monthsSinceLastBirthday, int buildNo, String changeLogUrl, List<TdApi.Function<?>> functions, List<TdApi.InputMessageContent> messages, boolean isLast) {
    // TODO (?) replace agesSinceBirthdate & monthsSinceLastBirthday with the commit date
    /*if (isLast) {
      version = BuildConfig.OVERRIDEN_VERSION_NAME;
      int i = version.indexOf('-');
      if (i != -1) {
        version = version.substring(0, i);
      }
    }*/
    TdApi.FormattedText text = makeUpdateText(String.format(Locale.US, "%d.%d.%d.%d", major, agesSinceBirthdate, monthsSinceLastBirthday, buildNo), changeLogUrl);
    functions.add(new TdApi.GetWebPagePreview(text, new TdApi.LinkPreviewOptions(false, changeLogUrl, false, false, false)));
    functions.add(new TdApi.GetWebPageInstantView(changeLogUrl, false));
    messages.add(new TdApi.InputMessageText(text, null, false));
  }

  private boolean isOptimizing;

  public boolean isOptimizing () {
    synchronized (dataLock) {
      return isOptimizing;
    }
  }

  @TdlibThread
  private void setIsOptimizing (boolean isOptimizing) {
    synchronized (dataLock) {
      if (this.isOptimizing == isOptimizing)
        return;
      this.isOptimizing = isOptimizing;
    }
    context().global().notifyOptimizing(this, isOptimizing);
  }

  private static boolean checkVersion (int version, int checkVersion, boolean isTest) {
    return version < checkVersion && (checkVersion <= BuildConfig.ORIGINAL_VERSION_CODE || isTest || BuildConfig.DEBUG) && checkVersion < Integer.MAX_VALUE;
  }

  private static @Status int getStatus (TdApi.AuthorizationState state) {
    if (state == null)
      return Status.UNKNOWN;
    switch (state.getConstructor()) {
      case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
      case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
      case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
        return Status.UNKNOWN;
      case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
      case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR:
        return Status.UNAUTHORIZED;
      case TdApi.AuthorizationStateReady.CONSTRUCTOR:
        return Status.READY;
      default:
        Td.assertAuthorizationState_6e5056de();
        throw Td.unsupported(state);
    }
  }

  public boolean checkChangeLogs (boolean alreadySent, boolean test) {
    final int status = authorizationStatus();
    if (status != Status.READY && status != Status.UNAUTHORIZED) {
      return false;
    }
    final String key = accountId + "_app_version";
    if (status == Status.UNAUTHORIZED || alreadySent) {
      Settings.instance().putInt(key, BuildConfig.ORIGINAL_VERSION_CODE);
      return alreadySent;
    }
    int prevVersion = test || Config.TEST_CHANGELOG ? 0 : Settings.instance().getInt(key, 0);
    if (prevVersion != BuildConfig.ORIGINAL_VERSION_CODE) {
      List<TdApi.InputMessageContent> updates = new ArrayList<>();
      List<TdApi.Function<?>> functions = new ArrayList<>();
      // TODO refactor to make it prettier & ready to grow
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_MARCH, test)) {
        makeUpdateText(0, 20, 6, APP_RELEASE_VERSION_2018_MARCH, "http://telegra.ph/Telegram-X-03-26", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_JULY, test)) {
        makeUpdateText(0, 20, 10, APP_RELEASE_VERSION_2018_JULY, "http://telegra.ph/Telegram-X-07-27", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_OCTOBER, test)) {
        makeUpdateText(0, 21, 1, APP_RELEASE_VERSION_2018_OCTOBER,  "https://telegra.ph/Telegram-X-10-14", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2018_OCTOBER_2, test)) {
        // makeUpdateText("0.21.1." + APP_RELEASE_VERSION_OCTOBER_2,  "https://t.me/tgx_android/129", functions, updates);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2019_APRIL, test)) {
        makeUpdateText(0, 21, 7, APP_RELEASE_VERSION_2019_APRIL, "https://telegra.ph/Telegram-X-04-25", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_JANUARY, test)) {
        makeUpdateText(0, 22, 4, APP_RELEASE_VERSION_2020_JANUARY, "https://telegra.ph/Telegram-X-01-23-2", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_FEBRUARY, test)) {
        makeUpdateText(0, 22, 5, APP_RELEASE_VERSION_2020_FEBRUARY, "https://telegra.ph/Telegram-X-02-29", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_SPRING, test)) {
        makeUpdateText(0, 22, 8, APP_RELEASE_VERSION_2020_SPRING, "https://telegra.ph/Telegram-X-04-23", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2021_NOVEMBER, test)) {
        makeUpdateText(0, 24, 2, APP_RELEASE_VERSION_2021_NOVEMBER, "https://telegra.ph/Telegram-X-11-08", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2022_JUNE, test)) {
        makeUpdateText(0, 24, 9, APP_RELEASE_VERSION_2022_JUNE, "https://telegra.ph/Telegram-X-06-11", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2022_OCTOBER, test)) {
        makeUpdateText(0, 25, 1, APP_RELEASE_VERSION_2022_OCTOBER, "https://telegra.ph/Telegram-X-10-06", functions, updates, false);
      }
      boolean haveMarch2023ChangeLog = false;
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_MARCH, test)) {
        makeUpdateText(0, 25, 6, APP_RELEASE_VERSION_2023_MARCH, "https://telegra.ph/Telegram-X-03-08", functions, updates, false);
        haveMarch2023ChangeLog = true;
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_MARCH_2, test) && !haveMarch2023ChangeLog) {
        makeUpdateText(0, 25, 6, APP_RELEASE_VERSION_2023_MARCH_2, "https://t.me/tgx_android/305", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_APRIL, test)) {
        makeUpdateText(0, 25, 6, APP_RELEASE_VERSION_2023_APRIL, "https://telegra.ph/Telegram-X-04-02", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_AUGUST, test)) {
        makeUpdateText(0, 25, 10, APP_RELEASE_VERSION_2023_AUGUST, "https://telegra.ph/Telegram-X-08-02", functions, updates, false);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2023_DECEMBER, test)) {
        makeUpdateText(0, 26, 3, APP_RELEASE_VERSION_2023_DECEMBER, "https://telegra.ph/Telegram-X-2023-12-31", functions, updates, true);
      }
      if (!updates.isEmpty()) {
        incrementReferenceCount(REFERENCE_TYPE_JOB); // starting task
        functions.add(new TdApi.CreatePrivateChat(TdConstants.TELEGRAM_ACCOUNT_ID, false));
        if (telegramServiceNotificationsChatId != 0 && telegramServiceNotificationsChatId != TdConstants.TELEGRAM_ACCOUNT_ID) {
          functions.add(new TdApi.GetChat(telegramServiceNotificationsChatId));
        }
        AtomicInteger remainingFunctions = new AtomicInteger(functions.size());
        Client.ResultHandler handler = object -> {
          if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
            Log.e("Received error while posting change log: %s", TD.toErrorString(object));
          }
          if (remainingFunctions.decrementAndGet() == 0) {
            AtomicInteger remainingUpdates = new AtomicInteger(updates.size());
            long chatId = serviceNotificationsChatId();
            Client.ResultHandler localMessageHandler = message -> {
              if (message.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                Log.e("Received error while sending change log: %s", TD.toErrorString(object));
              }
              if (remainingUpdates.decrementAndGet() == 0) {
                decrementReferenceCount(REFERENCE_TYPE_JOB); // ending task
              }
            };
            for (TdApi.InputMessageContent content : updates) {
              client().send(new TdApi.AddLocalMessage(chatId, new TdApi.MessageSenderUser(TdConstants.TELEGRAM_ACCOUNT_ID) /*TODO: @tgx_android?*/, null, true, content), localMessageHandler);
            }
          }
        };
        for (TdApi.Function<?> function : functions) {
          client().send(function, handler);
        }
      }
      Settings.instance().putInt(key, BuildConfig.ORIGINAL_VERSION_CODE);
    }
    return true;
  }

  private static final int APP_RELEASE_VERSION_2018_MARCH = 906; // 31st March, 2018: http://telegra.ph/Telegram-X-03-26
  private static final int APP_RELEASE_VERSION_2018_JULY = 967; // 27th July, 2018: http://telegra.ph/Telegram-X-07-27
  private static final int APP_RELEASE_VERSION_2018_OCTOBER = 1005; // 15th October, 2018: https://telegra.ph/Telegram-X-10-14
  private static final int APP_RELEASE_VERSION_2018_OCTOBER_2 = 1010; // 21th October, 2018: https://t.me/tgx_android/129

  private static final int APP_RELEASE_VERSION_2019_APRIL = 1149; // 26th April, 2019: https://telegra.ph/Telegram-X-04-25
  private static final int APP_RELEASE_VERSION_2019_SEPTEMBER = 1205; // 21st September, 2019: https://telegra.ph/Telegram-X-09-21

  private static final int APP_RELEASE_VERSION_2020_JANUARY = 1270; // 23 January, 2020: https://telegra.ph/Telegram-X-01-23-2
  private static final int APP_RELEASE_VERSION_2020_FEBRUARY = 1302; // 3 March, 2020: https://telegra.ph/Telegram-X-02-29 // 6th, Actually. Production version is 1305
  private static final int APP_RELEASE_VERSION_2020_SPRING = 1361; // 15 May, 2020: https://telegra.ph/Telegram-X-04-23

  private static final int APP_RELEASE_VERSION_2021_NOVEMBER = 1470; // 12 November, 2021: https://telegra.ph/Telegram-X-11-08

  private static final int APP_RELEASE_VERSION_2022_JUNE = 1530; // Going open source. 16 June, 2022: https://telegra.ph/Telegram-X-06-11

  private static final int APP_RELEASE_VERSION_2022_OCTOBER = 1560; // Reactions. 7 October, 2022: https://telegra.ph/Telegram-X-10-06

  private static final int APP_RELEASE_VERSION_2023_MARCH = 1605; // Dozens of stuff. 8 March, 2023: https://telegra.ph/Telegram-X-03-08
  private static final int APP_RELEASE_VERSION_2023_MARCH_2 = 1615; // Bugfixes to the previous release. 15 March, 2023: https://t.me/tgx_android/305
  private static final int APP_RELEASE_VERSION_2023_APRIL = 1624; // Emoji 15.0, more recent stickers & more + critical TDLIb upgrade. 2 April, 2023: https://telegra.ph/Telegram-X-04-02
  private static final int APP_RELEASE_VERSION_2023_AUGUST = 1646; // Translation, Advanced Text Formatting, Emoji Status, tgcalls, reproducible TDLib & more. 3 August, 2023: https://telegra.ph/Telegram-X-08-02
  private static final int APP_RELEASE_VERSION_2023_DECEMBER = 1674; // Custom emoji, select link preview, archive settings, in-app avatar picker, group chat tools, & more. 31st December, 2023 (full roll-out in January 2024): https://telegra.ph/Telegram-X-2023-12-31

  // Startup

  private boolean startupPerformed;

  boolean isStartupPerformed () {
    return startupPerformed;
  }

  private void onPerformStartup () {
    Log.i("Performing account startup for accountId:%d, isAfterRestart:%b", accountId, startupPerformed);
    listeners().performStartup(startupPerformed);
    startupPerformed = true;
  }

  // Phone number

  private String authPhoneCode, authPhoneNumber;

  public void setAuthPhoneNumber (String code, String number) {
    this.authPhoneCode = code;
    this.authPhoneNumber = number;
  }

  public boolean hasAuthPhoneNumber () {
    return !StringUtils.isEmpty(authPhoneNumber) || !StringUtils.isEmpty(authPhoneCode);
  }

  public String authPhoneCode () {
    return authPhoneCode;
  }

  public String authPhoneNumber () {
    return authPhoneNumber;
  }

  public String authPhoneNumberFormatted () {
    return hasAuthPhoneNumber() ? Strings.formatPhone("+" + authPhoneCode + authPhoneNumber, false, true) : "";
  }

  public String robotLoginCode () {
    String number = robotPhoneNumber();
    if (StringUtils.isEmpty(number)) {
      return "";
    } else {
      StringBuilder b = new StringBuilder(5);
      for (int i = 0; i < 5; i++) {
        b.append(number, 5, 6);
      }
      return b.toString();
    }
  }

  public int robotId () {
    String number = robotPhoneNumber();
    if (StringUtils.isEmpty(number)) {
      return 0;
    } else {
      return StringUtils.parseInt(number.substring("99966173".length())) - Config.ROBOT_ID_PREFIX;
    }
  }

  public String robotPhoneNumber () {
    TdApi.User user = myUser();
    String phoneNumber = user != null ? user.phoneNumber : null;
    if (StringUtils.isEmpty(phoneNumber)) {
      phoneNumber = authPhoneCode + authPhoneNumber;
    }
    if (StringUtils.isEmpty(phoneNumber) || !phoneNumber.startsWith("999661") || phoneNumber.length() != "999661".length() + 4) {
      return null;
    }
    return phoneNumber;
  }

  // Getters

  public int id () {
    return accountId;
  }

  public boolean isProduction () {
    return instanceMode == Mode.NORMAL;
  }

  private boolean isDebugInstance () {
    return instanceMode == Mode.DEBUG;
  }

  private boolean isServiceInstance () {
    return instanceMode == Mode.SERVICE;
  }

  public boolean inRecoveryMode () {
    return !isServiceInstance() && context.inRecoveryMode();
  }

  void setInstanceMode (@Mode int mode) {
    synchronized (clientLock) {
      setInstanceModeImpl(mode);
    }
  }

  private int getReferenceCount () {
    return this.referenceCount.get();
  }

  public boolean hasActiveReferences () {
    return getReferenceCount() > 0;
  }

  private void setInstanceModeImpl (@Mode int mode) {
    if (this.instanceMode != mode) {
      this.instanceMode = mode;
      if (instancePaused)
        return;
      client.sendClose();
    }
  }

  public TdlibAccount account () {
    return context.account(accountId);
  }

  public void wakeUp () {
    synchronized (clientLock) {
      if (client == null || !instancePaused || !inTdlibThread()) {
        clientHolderUnsafe();
        return;
      }
    }
    //FIXME?
    new Thread(this::clientHolder).start();
  }

  public boolean ownsClient (Client client) {
    synchronized (clientLock) {
      return this.client != null && this.client.client == client;
    }
  }

  private ClientHolder clientHolder () {
    CountDownLatch latch;
    synchronized (clientLock) {
      ClientHolder holder = clientHolderUnsafe();
      if (holder != null)
        return holder;
      latch = client.closeLatch;
    }
    if (Settings.instance().forceTdlibRestart()) {
      if (!U.awaitLatch(latch, 10, TimeUnit.SECONDS)) {
        RuntimeException e = new RuntimeException("Long close detected. authState: " + authorizationState + ", closeState: " + (client != null ? client.closeState : -1));
        Tracer.onOtherError(e);
        throw e;
      }
    } else {
      U.awaitLatch(latch);
    }
    return clientHolder();
  }

  public void checkDeadlocks (@Nullable Runnable after) {
    if (!Config.PROFILE_DEADLOCKS) {
      if (after != null) {
        after.run();
      }
      return;
    }
    CancellationSignal crashSignal = new CancellationSignal();
    Runnable forceAnr = () -> {
      if (!crashSignal.isCanceled()) {
        // Force ANR to cause system report, because TDLib thread is unavailable at least for 7 seconds
        clientExecute(new TdApi.SetAlarm(0), 0, false);
      }
    };
    crashSignal.setOnCancelListener(() ->
      ui().removeCallbacks(forceAnr)
    );
    client().send(new TdApi.SetAlarm(0), ignored -> {
      crashSignal.cancel();
      if (after != null) {
        after.run();
      }
    });
    ui().postDelayed(forceAnr, 7500);
  }

  public Client client () { // TODO migrate all tdlib.client().send(..) to tdlib.send(..)
    return clientHolder().client;
  }

  public interface ResultHandler<T extends TdApi.Object> {
    void onResult (T result, @Nullable TdApi.Error error);

    static <T extends TdApi.Object> Client.ResultHandler toTdlibHandler (ResultHandler<T> handler) {
      return result -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          handler.onResult(null, (TdApi.Error) result);
        } else {
          //noinspection unchecked
          handler.onResult((T) result, null);
        }
      };
    }
  }

  public <T extends TdApi.Object> void send (TdApi.Function<T> function, ResultHandler<T> handler) {
    send(client(), function, handler);
  }

  public <T extends TdApi.Object> void sendAll (TdApi.Function<T>[] functions, @NonNull ResultHandler<T> handler, @Nullable Runnable after) {
    sendAll(functions, ResultHandler.toTdlibHandler(handler), after);
  }

  public static <T extends TdApi.Object> void send (Client client, TdApi.Function<T> function, ResultHandler<T> handler) {
    send(client, function, ResultHandler.toTdlibHandler(handler));
  }

  private <T extends TdApi.Object> void send (TdApi.Function<T> function, Client.ResultHandler handler) {
    send(client(), function, handler);
  }

  private static <T extends TdApi.Object> void send (Client client, TdApi.Function<T> function, Client.ResultHandler handler) {
    client.send(function, handler);
  }

  public <T extends TdApi.Object> void sendAll (TdApi.Function<T>[] functions, @NonNull Client.ResultHandler handler, @Nullable Runnable after) {
    if (functions.length == 0) {
      if (after != null) {
        after.run();
      }
      return;
    }
    if (functions.length == 1) {
      send(functions[0], after != null ? result -> {
        handler.onResult(result);
        after.run();
      } : handler);
      return;
    }
    AtomicInteger remaining;
    Client.ResultHandler actualHandler;
    if (after != null) {
      remaining = new AtomicInteger(functions.length);
      actualHandler = result -> {
        handler.onResult(result);
        if (remaining.decrementAndGet() == 0) {
          after.run();
        }
      };
    } else {
      remaining = null;
      actualHandler = handler;
    }
    for (TdApi.Function<T> function : functions) {
      send(function, actualHandler);
    }
  }

  private ClientHolder clientHolderUnsafe () {
    if (client == null) {
      client = newClient();
      ui().postDelayed(this::checkPauseTimeout, 350);
    }
    if (!instancePaused)
      return client;
    if (inTdlibThread())
      throw new IllegalStateException();
    return null;
  }

  private void performOptional (@NonNull RunnableData<Client> runnable, @Nullable Runnable onFailure) {
    Client client;
    synchronized (clientLock) {
      if (instancePaused)
        return;
      ClientHolder holder = clientHolderUnsafe();
      client = holder != null ? holder.client : null;
    }
    if (client != null) {
      runnable.runWithData(client);
    } else if (onFailure != null) {
      onFailure.run();
    }
  }

  public void runOnUiThread (@NonNull Runnable runnable) {
    runOnUiThread(runnable, 0);
  }

  public void runOnUiThread (@NonNull Runnable runnable, long timeoutMs) {
    incrementUiReferenceCount();
    Runnable act = () -> {
      runnable.run();
      decrementUiReferenceCount();
    };
    if (timeoutMs > 0) {
      ui().postDelayed(act, timeoutMs);
    } else {
      ui().post(act);
    }
  }

  public void runOnTdlibThread (@NonNull Runnable runnable) {
    runOnTdlibThread(runnable, 0, true);
  }

  public void runOnTdlibThread (@NonNull Runnable runnable, double timeoutSeconds, boolean acquireReference) {
    if (acquireReference) {
      incrementReferenceCount(REFERENCE_TYPE_JOB);
    }
    clientHolder().runOnTdlibThread(() -> {
      runnable.run();
      if (acquireReference) {
        decrementReferenceCount(REFERENCE_TYPE_JOB);
      }
    }, timeoutSeconds);
  }

  public void searchContacts (@Nullable String searchQuery, int limit, Client.ResultHandler handler) {
    Log.ensureReturnType(TdApi.SearchContacts.class, TdApi.Users.class);
    client().send(new TdApi.SearchContacts(searchQuery, limit), handler);
  }

  public void loadMoreChats (@NonNull TdApi.ChatList chatList, int limit, Client.ResultHandler handler) {
    Log.ensureReturnType(TdApi.LoadChats.class, TdApi.Ok.class);
    client().send(new TdApi.LoadChats(chatList, limit), handler);
  }

  public void readAllChats (@NonNull TdApi.ChatList chatList, @Nullable RunnableInt after) {
    AtomicInteger readChatsCount = new AtomicInteger(0);
    getAllChats(chatList, chat -> {
      boolean read = false;
      if (chat.unreadCount != 0 && chat.lastMessage != null) {
        readMessages(chat.id, new long[] {chat.lastMessage.id}, new TdApi.MessageSourceChatList());
        read = true;
      }
      if (chat.isMarkedAsUnread) {
        client().send(new TdApi.ToggleChatIsMarkedAsUnread(chat.id, false), okHandler());
        read = true;
      }
      if (chat.unreadMentionCount > 0) {
        client().send(new TdApi.ReadAllChatMentions(chat.id), okHandler());
        read = true;
      }
      if (read) {
        readChatsCount.incrementAndGet();
      }
    }, isFinal -> {
      if (isFinal && after != null) {
        after.runWithInt(readChatsCount.get());
      }
    }, false);
  }

  public void getAllChats (@NonNull TdApi.ChatList chatList, @NonNull RunnableData<TdApi.Chat> perChatCallback, @Nullable RunnableBool after, boolean callMiddle) {
    chatList(chatList).loadAll(perChatCallback, after);
  }

  public static class Generation {
    private CountDownLatch latch;

    public TdApi.File file;
    public String destinationPath;

    private long generationId;
    private boolean isPending;

    public Runnable onCancel;
  }

  private final HashMap<String, Generation> awaitingGenerations = new HashMap<>();
  private final HashMap<Long, Generation> pendingGenerations = new HashMap<>();

  public @Nullable Generation generateFile (String id, TdApi.FileType fileType, boolean isSecret, int priority, long timeoutMs) {
    final CountDownLatch latch = new CountDownLatch(2);
    final Generation generation = new Generation();
    generation.latch = latch;

    synchronized (awaitingGenerations) {
      awaitingGenerations.put(id, generation);
    }

    client().send(new TdApi.PreliminaryUploadFile(new TdApi.InputFileGenerated(null, id, 0), isSecret ? new TdApi.FileTypeSecret() : fileType, priority), object -> {
      switch (object.getConstructor()) {
        case TdApi.File.CONSTRUCTOR:
          generation.file = (TdApi.File) object;
          break;
        case TdApi.Error.CONSTRUCTOR:
          Log.w("Error starting file generation: %s", TD.toErrorString(object));
          latch.countDown(); // since file generation may have not started
          break;
      }
      latch.countDown();
    });

    try {
      if (timeoutMs > 0) {
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      } else {
        latch.await();
      }
    } catch (InterruptedException e) {
      Log.i(e);
    }
    synchronized (awaitingGenerations) {
      if (generation.isPending) {
        pendingGenerations.remove(generation.generationId);
      } else {
        awaitingGenerations.remove(id);
      }
    }
    if (generation.file == null || generation.destinationPath == null) {
      return null;
    }
    return generation;
  }

  public void finishGeneration (Generation generation, @Nullable TdApi.Error error) {
    synchronized (awaitingGenerations) {
      pendingGenerations.remove(generation.generationId);
    }
    client().send(new TdApi.FinishFileGeneration(generation.generationId, error), silentHandler());
  }

  public void getMessage (long chatId, long messageId, @Nullable RunnableData<TdApi.Message> callback) {
    client().send(new TdApi.GetMessageLocally(chatId, messageId), localResult -> {
      if (localResult instanceof TdApi.Message) {
        if (callback != null)
          callback.runWithData((TdApi.Message) localResult);
      } else {
        client().send(new TdApi.GetMessage(chatId, messageId), serverResult -> {
          if (serverResult instanceof TdApi.Message) {
            if (callback != null)
              callback.runWithData((TdApi.Message) serverResult);
          } else {
            if (callback != null)
              callback.runWithData(null);
            Log.i("Could not get message from server: %s, chatId:%s, messageId:%s", TD.toErrorString(serverResult), chatId, messageId);
          }
        });
      }
    });
  }

  @Nullable
  public TdApi.Message getMessageLocally (long chatId, long messageId) {
    return getMessageLocally(chatId, messageId, 0);
  }
  @Nullable
  public TdApi.Message getMessageLocally (long chatId, long messageId, long timeoutMs) {
    if (inTdlibThread()) {
      return null;
    }
    TdApi.Object result = clientExecute(new TdApi.GetMessageLocally(chatId, messageId), timeoutMs);
    if (result instanceof TdApi.Message)
      return (TdApi.Message) result;
    if (result instanceof TdApi.Error)
      Log.i("Could not get message: %s, chatId:%s, messageId:%s", TD.toErrorString(result), chatId, messageId);
    return null;
  }

  public static class Album {
    public final List<TdApi.Message> messages;
    public final boolean mayHaveNewerItems, mayHaveOlderItems;

    public Album (List<TdApi.Message> messages, boolean mayHaveNewerItems, boolean mayHaveOlderItems) {
      this.messages = messages;
      this.mayHaveNewerItems = mayHaveNewerItems;
      this.mayHaveOlderItems = mayHaveOlderItems;
    }

    public Album (List<TdApi.Message> messages) {
      this(messages, false, false);
    }

    public boolean mayHaveMoreItems () {
      return mayHaveNewerItems || mayHaveOlderItems;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (this == obj)
        return true;
      if (obj == null || !(obj instanceof Album))
        return false;
      List<TdApi.Message> cmp = ((Album) obj).messages;
      if (cmp.size() != messages.size()) {
        return false;
      }
      for (int i = 0; i < cmp.size(); i++) {
        TdApi.Message a = cmp.get(i);
        TdApi.Message b = messages.get(i);
        if (a.chatId != b.chatId || a.id != b.id || a.mediaAlbumId != b.mediaAlbumId) {
          return false;
        }
      }
      return true;
    }
  }

  public void getAlbum (TdApi.Message message, boolean onlyLocal, @Nullable Album prevAlbum, @Nullable RunnableData<Album> callback) {
    if (message.mediaAlbumId == 0) {
      if (callback != null) {
        callback.runWithData(null);
      }
      return;
    }
    if (prevAlbum != null) {
      getAlbum(prevAlbum.messages, onlyLocal, prevAlbum, callback);
    } else {
      List<TdApi.Message> album = new ArrayList<>(TdConstants.MAX_MESSAGE_GROUP_SIZE);
      album.add(message);
      getAlbum(album, onlyLocal, prevAlbum, callback);
    }
  }

  public void getAlbum (List<TdApi.Message> album, boolean onlyLocal, @Nullable Album prevAlbum, @Nullable RunnableData<Album> callback) {
    TdApi.Message newestMessage = album.get(0);
    TdApi.Message oldestMessage = album.get(album.size() - 1);

    List<TdApi.Message> olderMessages = new ArrayList<>();
    List<TdApi.Message> newerMessages = new ArrayList<>();

    AtomicBoolean endFound = new AtomicBoolean(prevAlbum != null && !prevAlbum.mayHaveOlderItems); // oldest message found
    AtomicBoolean startFound = new AtomicBoolean(prevAlbum != null && !prevAlbum.mayHaveNewerItems); // newest message found

    int reqCount = 0;
    if (!endFound.get())
      reqCount++;
    if (!startFound.get())
      reqCount++;

    if (reqCount == 0) {
      if (callback != null) {
        callback.runWithData(prevAlbum);
      }
      return;
    }

    AtomicInteger requestCount = new AtomicInteger(reqCount);

    Runnable after = () -> {
      List<TdApi.Message> result;
      int addedCount = olderMessages.size() + newerMessages.size();
      if (addedCount > 0) {
        result = new ArrayList<>(newerMessages.size() + album.size() + olderMessages.size());
        result.addAll(newerMessages);
        result.addAll(album);
        result.addAll(olderMessages);
      } else {
        result = album;
      }
      boolean mayHaveNewerItems = result.size() < TdConstants.MAX_MESSAGE_GROUP_SIZE && !startFound.get();
      boolean mayHaveOlderItems = result.size() < TdConstants.MAX_MESSAGE_GROUP_SIZE && !endFound.get();
      if (callback != null) {
        callback.runWithData(new Album(result, mayHaveNewerItems, mayHaveOlderItems));
      }
    };

    int count = TdConstants.MAX_MESSAGE_GROUP_SIZE - album.size() + 1;
    if (!endFound.get()) {
      client().send(new TdApi.GetChatHistory(oldestMessage.chatId, oldestMessage.id, 0, count, onlyLocal), result -> {
        switch (result.getConstructor()) {
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Message[] messages = ((TdApi.Messages) result).messages;
            for (TdApi.Message message : messages) {
              if (message.id >= oldestMessage.id)
                continue;
              if (message.mediaAlbumId != oldestMessage.mediaAlbumId) {
                endFound.set(true);
                break;
              }
              olderMessages.add(message);
            }
            if (olderMessages.isEmpty() && !onlyLocal) {
              endFound.set(true);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.i("Failed to fetch part of an album: %s", TD.toErrorString(result));
            break;
          }
        }
        if (requestCount.decrementAndGet() == 0) {
          after.run();
        }
      });
    }
    if (!startFound.get()) {
      client().send(new TdApi.GetChatHistory(newestMessage.chatId, newestMessage.id, -count, count, onlyLocal), result -> {
        switch (result.getConstructor()) {
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Message[] messages = ((TdApi.Messages) result).messages;
            for (int i = messages.length - 1; i >= 0; i--) {
              TdApi.Message message = messages[i];
              if (message.id <= newestMessage.id) {
                continue;
              }
              if (message.mediaAlbumId != newestMessage.mediaAlbumId) {
                startFound.set(true);
                break;
              }
              newerMessages.add(message);
            }
            Collections.reverse(newerMessages);
            if (newerMessages.isEmpty() && !onlyLocal) {
              startFound.set(true);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.i("Failed to fetch part of an album: %s", TD.toErrorString(result));
            break;
          }
        }
        if (requestCount.decrementAndGet() == 0) {
          after.run();
        }
      });
    }
  }

  private void updateTdlibThread () {
    tdlibThread = Thread.currentThread();
  }

  public boolean inTdlibThread () {
    if (tdlibThread != null) {
      // FIXME[tdlib]: it is safe as long as there's just one thread for all apps
      return Thread.currentThread() == tdlibThread;
    } else {
      // FIXME[tdlib]: more reliable way
      final String tdlibThreadName = "TDLib thread";
      return tdlibThreadName.equals(Thread.currentThread().getName());
    }
  }

  public TdApi.Object clientExecute (TdApi.Function<?> function, long timeoutMs) {
    return clientExecute(function, timeoutMs, true);
  }

  public <T extends TdApi.Object> T clientExecuteT (TdApi.Function<T> function, boolean throwErrors) throws TdlibException {
    return clientExecuteT(function, 0, throwErrors);
  }

  public <T extends TdApi.Object> T clientExecuteT (TdApi.Function<T> function, long timeoutMs, boolean throwErrors) throws TdlibException {
    TdApi.Object result = clientExecute(function, timeoutMs);
    if (result instanceof TdApi.Error) {
      if (throwErrors) {
        throw new TdlibException((TdApi.Error) result);
      }
      Log.i("clientExecute %s failed: %s", function.getClass().getName(), TD.toErrorString(result));
      return null;
    }
    //noinspection unchecked
    return (T) result;
  }

  @Nullable
  private TdApi.Object clientExecute (TdApi.Function<?> function, long timeoutMs, boolean requiresTdlibInitialization) {
    if (inTdlibThread())
      throw new IllegalStateException("Cannot call from TDLib thread: " + function);
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();
    Runnable act = () -> {
      if (requiresTdlibInitialization) {
        incrementReferenceCount(REFERENCE_TYPE_REQUEST_EXECUTION);
      }
      client().send(function, object -> {
        synchronized (response) {
          response.set(object);
          latch.countDown();
        }
        if (requiresTdlibInitialization) {
          decrementReferenceCount(REFERENCE_TYPE_REQUEST_EXECUTION);
        }
      });
    };
    //noinspection SwitchIntDef
    switch (function.getConstructor()) {
      // Methods that can be called before initialization
      case TdApi.SetAlarm.CONSTRUCTOR:
      case TdApi.Close.CONSTRUCTOR:
      case TdApi.GetAuthorizationState.CONSTRUCTOR:
      case TdApi.GetCurrentState.CONSTRUCTOR:
        act.run();
        break;
      // Require TDLib initialization in case of all other methods
      default:
        if (requiresTdlibInitialization) {
          awaitInitialization(act);
        } else {
          act.run();
        }
        break;
    }
    try {
      if (timeoutMs > 0) {
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      } else {
        latch.await();
      }
    } catch (InterruptedException e) {
      Log.i(e);
    }
    synchronized (response) {
      return response.get();
    }
  }

  public @Nullable TdApi.File getRemoteFile (String remoteId, TdApi.FileType fileType, long timeoutMs) {
    TdApi.Object result = clientExecute(new TdApi.GetRemoteFile(remoteId, fileType), timeoutMs);
    return result instanceof TdApi.File ? (TdApi.File) result : null;
  }

  public TdApi.SetTdlibParameters clientParameters () {
    return parameters;
  }

  public TdlibCache cache () {
    return cache;
  }

  public TdlibEmojiManager emoji () {
    return emoji;
  }

  public TdlibEmojiReactionsManager reactions () {
    return reactions;
  }

  public TdlibSingleton<TdApi.Stickers> genericAnimationEffects () {
    return genericReactionEffects;
  }

  public TdlibListeners listeners () {
    return listeners;
  }

  public TdlibStatusManager status () {
    return statusManager;
  }

  public TdlibFileGenerationManager filegen () {
    return fileGenerationManager;
  }

  public TdlibMessageViewer messageViewer () {
    return messageViewer;
  }

  public TdlibQuickAckManager qack () {
    return quickAckManager;
  }

  public TdlibFilesManager files () {
    return filesManager;
  }

  public TdlibSettingsManager settings () {
    return settingsManager;
  }

  public TdlibNotificationManager notifications () {
    return notificationManager;
  }

  public TdlibContactManager contacts () {
    return contactManager;
  }

  public TdlibWallpaperManager wallpaper () {
    return wallpaperManager;
  }

  public TdlibManager context () {
    return context;
  }

  public TdlibUi ui () {
    if (_handler == null) {
      synchronized (handlerLock) {
        if (_handler == null) {
          long ms = SystemClock.uptimeMillis();
          _handler = new TdlibUi(this);
          Log.i(Log.TAG_ACCOUNTS, "Created UI handler in %dms", SystemClock.uptimeMillis() - ms);
        }
      }
    }
    return _handler;
  }

  public void uiExecute (Runnable act) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      act.run();
    } else {
      ui().post(act);
    }
  }

  public Client.ResultHandler okHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    };
  }

  public ResultHandler<TdApi.Ok> typedOkHandler () {
    return (ok, error) -> {
      if (error != null) {
        UI.showError(error);
      }
    };
  }

  public Client.ResultHandler okHandler (@Nullable Runnable after) {
    return after != null ? object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib().ui().post(after);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    } : okHandler();
  }

  public ResultHandler<TdApi.Ok> typedOkHandler (@Nullable Runnable after) {
    return after != null ? (ok, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        tdlib().ui().post(after);
      }
    } : typedOkHandler();
  }

  public <T extends TdApi.Object> ResultHandler<T> successHandler (@Nullable Runnable after) {
    return (data, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        tdlib().ui().post(after);
      }
    };
  }

  public Client.ResultHandler doneHandler () {
    return doneHandler;
  }

  public Client.ResultHandler profilePhotoHandler () {
    return profilePhotoHandler;
  }

  public Client.ResultHandler silentHandler () {
    return silentHandler;
  }

  public Client.ResultHandler silentHandler (@Nullable Runnable after) {
    return after != null ? object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib().ui().post(after);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    } : silentHandler();
  }

  public Client.ResultHandler messageHandler () {
    return messageHandler;
  }

  public ResultHandler<TdApi.File> imageLoadHandler () {
    return imageLoadHandler;
  }

  public Comparator<TdApi.User> userComparator () {
    return userComparator;
  }

  public Comparator<UserProvider> userProviderComparator () {
    return userProviderComparator;
  }

  // Self User

  public long myUserId (boolean allowCached) {
    long myUserId = myUserId();
    if (myUserId != 0 || !allowCached)
      return myUserId;
    return account().getKnownUserId();
  }

  public long myUserId () {
    // TODO move myUserId management to TdlibContext
    return cache().myUserId();
  }

  public @Nullable TdApi.User myUser () {
    // TODO move myUser management to TdlibContext
    return cache().myUser();
  }

  public boolean hasPremium () {
    TdApi.User user = cache().myUser();
    if (user != null) {
      return user.isPremium;
    }
    return isPremium;
  }

  public TdApi.MessageSender mySender () {
    long userId = myUserId();
    return new TdApi.MessageSenderUser(userId);
  }

  public @Nullable TdApi.Usernames myUserUsernames () {
    TdApi.User user = myUser();
    return user != null ? user.usernames : null;
  }

  public @Nullable String myUserUsername () {
    TdApi.User user = myUser();
    return Td.primaryUsername(user);
  }

  public @Nullable TdApi.UserFullInfo myUserFull () {
    long myUserId = myUserId();
    return myUserId != 0 ? cache().userFull(myUserId) : null;
  }

  // Chats

  public void loadChats (long[] chatIds, @Nullable Runnable after) {
    if (chatIds == null || chatIds.length == 0)
      return;
    if (after != null) {
      int[] counter = new int[] {chatIds.length};
      for (long chatId : chatIds) {
        client().send(new TdApi.GetChat(chatId), result -> {
          silentHandler.onResult(result);
          if (--counter[0] == 0) {
            after.run();
          }
        });
      }
    } else {
      for (long chatId : chatIds) {
        client().send(new TdApi.GetChat(chatId), silentHandler);
      }
    }
  }

  public boolean chatOnline (long chatId) {
    if (chatId == 0 || isSelfChat(chatId))
      return false;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        long userId = chatUserId(chatId);
        return userId != 0 && cache().isOnline(userId);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        break;
      default:
        Td.assertChatType_e562ec7d();
        throw new UnsupportedOperationException(Long.toString(chatId));
    }
    return false;
  }

  public int chatOnlineMemberCount (long chatId) {
    if (chatId == 0)
      return 0;
    final Integer onlineMemberCount;
    synchronized (dataLock) {
      onlineMemberCount = chatOnlineMemberCount.get(chatId);
    }
    return onlineMemberCount != null && onlineMemberCount > 1 ? onlineMemberCount : 0;
  }

  public int chatMemberCount (long chatId) {
    if (chatId == 0)
      return 0;
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        TdApi.BasicGroup basicGroup = cache().basicGroup(ChatId.toBasicGroupId(chatId));
        return basicGroup != null ? basicGroup.memberCount : 0;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.SupergroupFullInfo supergroupFullInfo = cache().supergroupFull(supergroupId, false);
        int memberCount = supergroupFullInfo != null ? supergroupFullInfo.memberCount : 0;
        if (memberCount > 0)
          return memberCount;
        TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
        return supergroup != null ? supergroup.memberCount : 0;
      }
    }
    return 0;
  }

  public void privateChat (long userId, RunnableData<TdApi.Chat> callback) {
    TdApi.Chat chat = chat(ChatId.fromUserId(userId));
    if (chat != null) {
      if (callback != null) {
        callback.runWithData(chat);
      }
      return;
    }
    client().send(new TdApi.CreatePrivateChat(userId, false), result -> {
      if (callback != null) {
        ui().post(() -> callback.runWithData(chat(ChatId.fromUserId(userId))));
      }
    });
  }

  public @Nullable TdApi.ForumTopicInfo forumTopicInfo (long chatId, long messageThreadId) {
    String cacheKey = chatId + "_" + messageThreadId;
    synchronized (dataLock) {
      return forumTopicInfos.get(cacheKey);
    }
  }

  public @Nullable TdApi.Chat chat (long chatId) {
    if (chatId == 0) {
      return null;
    }
    final TdApi.Chat chat;
    synchronized (dataLock) {
      chat = chats.get(chatId);
    }
    return chat;
  }

  public @NonNull TdApi.Chat chatStrict (long chatId) {
    final TdApi.Chat chat;
    synchronized (dataLock) {
      chat = chats.get(chatId);
      if (chat == null) {
        throw new IllegalStateException("updateChat not received for id:" + chatId);
      }
    }
    return chat;
  }

  public void chat (long chatId, @NonNull RunnableData<TdApi.Chat> callback) {
    runOnTdlibThread(() -> {
      TdApi.Chat chat = chat(chatId);
      if (chat != null) {
        callback.runWithData(chat);
      } else {
        client().send(new TdApi.GetChat(chatId), result -> callback.runWithData(result.getConstructor() == TdApi.Chat.CONSTRUCTOR ? chat(chatId) : null));
      }
    });
  }

  public void chat (long chatId, Future<TdApi.Function<?>> createFunction, @NonNull RunnableData<TdApi.Chat> callback) {
    if (createFunction == null) {
      chat(chatId, callback);
    } else {
      chat(chatId, chat -> {
        if (chat != null) {
          callback.runWithData(chat);
        } else {
          client().send(createFunction.getValue(), ignored -> {
            TdApi.Chat createdChat = chat(chatId);
            callback.runWithData(createdChat);
          });
        }
      });
    }
  }

  public @Nullable TdApi.Chat syncChat (@NonNull TdApi.Chat chat) {
    return knownChatIds.contains(chat.id) ? chat : chatSync(chat.id);
  }

  public @Nullable TdApi.Chat chatSync (long chatId) {
    return chatSync(chatId, TimeUnit.SECONDS.toMillis(5));
  }

  public @Nullable TdApi.Chat chatSync (long chatId, long timeoutMs) {
    if (chatId == 0)
      return null;
    TdApi.Chat chat = knownChatIds.contains(chatId) ? chat(chatId) : null;
    if (chat != null)
      return chat;
    TdApi.Object result = clientExecute(new TdApi.GetChat(chatId), timeoutMs);
    if (result != null) {
      switch (result.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR:
          return knownChatIds.contains(chatId) ? chat(chatId) : null;
        case TdApi.Error.CONSTRUCTOR:
          Log.e("chatSync failed: %s, chatId:%d", TD.toErrorString(result), chatId);
          return null;
      }
    }
    return null;
  }

  public @NonNull TdlibChatList chatList (@NonNull TdApi.ChatList chatList) {
    synchronized (dataLock) {
      return chatListImpl(chatList);
    }
  }

  private @NonNull TdlibChatList chatListImpl (@NonNull TdApi.ChatList chatList) {
    final String key = TD.makeChatListKey(chatList);
    TdlibChatList list = chatLists.get(key);
    if (list == null) {
      list = new TdlibChatList(this, chatList);
      chatLists.put(key, list);
    }
    return list;
  }

  private @Nullable TdlibChatList[] chatListsImpl (@Nullable TdApi.ChatPosition[] positions) {
    if (positions == null || positions.length == 0) {
      return null;
    }
    final TdlibChatList[] chatLists = new TdlibChatList[positions.length];
    for (int i = 0; i < positions.length; i++) {
      chatLists[i] = chatListImpl(positions[i].list);
    }
    return chatLists;
  }

  public @NonNull List<TdApi.Chat> chats (long[] chatIds) {
    final ArrayList<TdApi.Chat> result = new ArrayList<>(chatIds.length);
    synchronized (dataLock) {
      for (long chatId : chatIds) {
        TdApi.Chat chat = chats.get(chatId);
        if (TdlibUtils.assertChat(chatId, chat))
          continue;
        result.add(chat);
      }
    }
    return result;
  }

  public @NonNull List<TdApi.User> chatUsers (long[] chatIds) {
    final ArrayList<TdApi.User> result = new ArrayList<>(chatIds.length);
    synchronized (dataLock) {
      for (long chatId : chatIds) {
        TdApi.User user = chatUser(chatId);
        if (user != null)
          result.add(user);
      }
    }
    return result;
  }

  public void stickerSet (String name, RunnableData<TdApi.StickerSet> callback) {
    if (StringUtils.isEmpty(name)) {
      callback.runWithData(null);
    } else {
      client().send(new TdApi.SearchStickerSet(name), result -> {
        switch (result.getConstructor()) {
          case TdApi.StickerSet.CONSTRUCTOR:
            callback.runWithData((TdApi.StickerSet) result);
            break;
          case TdApi.Error.CONSTRUCTOR:
            Log.e("Failed to find animated emoji sticker set: %s, %s, isDebugInstance: %b", name, TD.toErrorString(result), isDebugInstance());
            callback.runWithData(null);
            break;
        }
      });
    }
  }

  public boolean isAnonymousAdmin (long chatId) {
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    return status != null && Td.isAnonymous(status);
  }

  public boolean isAnonymousAdminNonCreator (long chatId) {
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    return status != null && Td.isAnonymous(status) && !TD.isCreator(status);
  }

  public @Nullable TdApi.ChatMemberStatus chatStatus (long chatId) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return null;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
        return supergroup != null ? supergroup.status : null;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        long basicGroupId = ChatId.toBasicGroupId(chatId);
        TdApi.BasicGroup basicGroup = cache().basicGroup(basicGroupId);
        return basicGroup != null ? basicGroup.status : null;
      }
    }
    throw new RuntimeException();
  }

  public @Nullable TdApi.ChatType chatType (long chatId) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
        return new TdApi.ChatTypePrivate(ChatId.toUserId(chatId));
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return new TdApi.ChatTypeBasicGroup(ChatId.toBasicGroupId(chatId));
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int secretChatId = ChatId.toSecretChatId(chatId);
        TdApi.SecretChat secretChat = cache().secretChat(secretChatId);
        return new TdApi.ChatTypeSecret(secretChatId, secretChat != null ? secretChat.userId : 0);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
        return new TdApi.ChatTypeSupergroup(supergroupId, supergroup != null && supergroup.isChannel);
      }
    }
    throw new IllegalArgumentException("chatId == " + chatId);
  }

  public @Nullable TdApi.ChatNotificationSettings chatSettings (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null ? chat.notificationSettings : null;
  }

  public @Nullable TdApi.ChatPermissions chatPermissions (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null ? chat.permissions : null;
  }

  // Data Utils

  public TdApi.Chat objectToChat (TdApi.Object object) {
    return chat(((TdApi.Chat) object).id);
  }

  public boolean isSelfChat (long chatId) {
    return isSelfUserId(ChatId.toUserId(chatId));
  }

  public boolean isSelfChat (TdApi.Chat chat) {
    return chat != null && isSelfChat(chat.id);
  }

  public boolean isSelfUserId (long userId) {
    return userId != 0 && userId == myUserId(true);
  }

  public long selfChatId () {
    return ChatId.fromUserId(myUserId());
  }

  public TdApi.Chat selfChat () {
    return chat(selfChatId());
  }

  public boolean canClearHistory (long chatId) {
    return chatId != 0 && canClearHistory(chat(chatId));
  }

  public boolean canClearHistory (TdApi.Chat chat) {
    return chat != null && chat.lastMessage != null && (chat.canBeDeletedOnlyForSelf || chat.canBeDeletedForAllUsers);
  }

  public boolean canClearHistoryForAllUsers (long chatId) {
    return chatId != 0 && canClearHistoryForAllUsers(chat(chatId));
  }

  public boolean canClearHistoryForAllUsers (TdApi.Chat chat) {
    return chat != null && chat.lastMessage != null && chat.canBeDeletedForAllUsers;
  }

  public boolean canClearHistoryOnlyForSelf (long chatId) {
    return chatId != 0 && canClearHistoryOnlyForSelf(chat(chatId));
  }

  public boolean canClearHistoryOnlyForSelf (TdApi.Chat chat) {
    return chat != null && chat.lastMessage != null && chat.canBeDeletedOnlyForSelf;
  }

  public boolean canAddToOtherChat (TdApi.Chat chat) {
    TdApi.User user = chatUser(chat);
    if (user == null) {
      return false;
    }
    switch (user.type.getConstructor()) {
      case TdApi.UserTypeRegular.CONSTRUCTOR:
        return true;
      case TdApi.UserTypeBot.CONSTRUCTOR:
        return ((TdApi.UserTypeBot) user.type).canJoinGroups;
    }
    return false;
  }

  public @Nullable TdApi.SecretChat chatToSecretChat (long chatId) {
    int secretChatId = ChatId.toSecretChatId(chatId);
    return secretChatId != 0 ? cache().secretChat(secretChatId) : null;
  }

  public @Nullable TdApi.BasicGroup chatToBasicGroup (long chatId) {
    long basicGroupId = ChatId.toBasicGroupId(chatId);
    return basicGroupId != 0 ? cache().basicGroup(basicGroupId) : null;
  }

  public @Nullable TdApi.Supergroup chatToSupergroup (long chatId) {
    long supergroupId = ChatId.toSupergroupId(chatId);
    return supergroupId != 0 ? cache().supergroup(supergroupId) : null;
  }

  public @Nullable ImageFile chatAvatar (long chatId) {
    return chatAvatar(chatId, ChatView.getDefaultAvatarCacheSize());
  }

  public @Nullable ImageFile chatAvatar (long chatId, @Px int size) {
    if (chatId == 0)
      return null;
    TdApi.Chat chat = chat(chatId);
    TdApi.ChatPhotoInfo photo = chat != null ? chat.photo : null;
    if (photo == null)
      return null;
    ImageFile avatarFile = new ImageFile(this, photo.small);
    avatarFile.setSize(size);
    return avatarFile;
  }

  public AvatarPlaceholder chatPlaceholder (TdApi.Chat chat, boolean allowSavedMessages, float radius, @Nullable DrawableProvider provider) {
    return new AvatarPlaceholder(radius, chatPlaceholderMetadata(chat, allowSavedMessages), provider);
  }

  public AvatarPlaceholder chatPlaceholder (long chatId, @Nullable TdApi.Chat chat, boolean allowSavedMessages, float radius, @Nullable DrawableProvider provider) {
    return new AvatarPlaceholder(radius, chatPlaceholderMetadata(chatId, chat, allowSavedMessages), provider);
  }

  public AvatarPlaceholder.Metadata chatPlaceholderMetadata (long chatId, boolean allowSavedMessages) {
    return chatPlaceholderMetadata(chatId, chat(chatId), allowSavedMessages);
  }

  public AvatarPlaceholder.Metadata chatPlaceholderMetadata (long chatId, @Nullable TdApi.Chat chat, boolean allowSavedMessages) {
    if (chat != null || chatId == 0) {
      return chatPlaceholderMetadata(chat, allowSavedMessages);
    } else if (allowSavedMessages && isSelfChat(chatId)) {
      return new AvatarPlaceholder.Metadata(accentColor(TdlibAccentColor.InternalId.SAVED_MESSAGES));
    } else if (isRepliesChat(chatId)) {
      return new AvatarPlaceholder.Metadata(accentColor(TdlibAccentColor.InternalId.REPLIES));
    } else if (isDeletedAccountChat(chatId)) {
      return new AvatarPlaceholder.Metadata(accentColor(TdlibAccentColor.InternalId.INACTIVE));
    } else {
      return new AvatarPlaceholder.Metadata(chatAccentColor(chatId));
    }
  }

  public AvatarPlaceholder.Metadata chatPlaceholderMetadata (@Nullable TdApi.Chat chat, boolean allowSavedMessages) {
    if (chat == null) {
      return null;
    }
    TdlibAccentColor accentColor;
    Letters avatarLetters;
    int desiredDrawableRes = 0;
    int extraDrawableRes = 0;
    if (isUserChat(chat)) {
      long userId = chatUserId(chat);
      return cache().userPlaceholderMetadata(userId, cache().user(userId), allowSavedMessages);
    } else {
      accentColor = chatAccentColor(chat);
      avatarLetters = chatLetters(chat);
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          extraDrawableRes = canChangeInfo(chat) ? R.drawable.baseline_add_a_photo_56 : R.drawable.baseline_group_56;
          break;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          extraDrawableRes = canChangeInfo(chat) ? R.drawable.baseline_add_a_photo_56 : isChannelChat(chat) ? R.drawable.baseline_bullhorn_56 : R.drawable.baseline_group_56;
          break;
      }
    }
    return new AvatarPlaceholder.Metadata(accentColor, avatarLetters, desiredDrawableRes, extraDrawableRes);
  }

  public Letters chatLetters (TdApi.Chat chat) {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
        case TdApi.ChatTypeSecret.CONSTRUCTOR: {
          return TD.getLetters(chatUser(chat));
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          return TD.getLetters(chatTitle(chat));
        }
      }
    }
    return TD.getLetters();
  }

  public Letters chatLetters (long chatId) {
    if (chatId != 0) {
      switch (ChatId.getType(chatId)) {
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
        case TdApi.ChatTypeSecret.CONSTRUCTOR: {
          return TD.getLetters(chatUser(chatUserId(chatId)));
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          return TD.getLetters(chatTitle(chatId));
        }
      }
    }
    return TD.getLetters();
  }

  public int chatAccentColorId (long chatId) {
    if (chatId == 0) {
      return TdlibAccentColor.InternalId.INACTIVE;
    }
    if (isRepliesChat(chatId)) {
      return TdlibAccentColor.InternalId.REPLIES;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        TdApi.Chat chat = chat(chatId);
        if (chat != null) {
          return chat.accentColorId;
        } else {
          return TdlibAccentColor.InternalId.INACTIVE;
        }
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        long userId = chatUserId(chatId);
        return cache().userAccentColorId(userId);
      }
      default: {
        Td.assertChatType_e562ec7d();
        throw new UnsupportedOperationException(Long.toString(chatId));
      }
    }
  }

  public int chatAccentColorId (@Nullable TdApi.Chat chat) {
    if (chat == null) {
      return TdlibAccentColor.InternalId.INACTIVE;
    }
    if (isRepliesChat(chat.id)) {
      return TdlibAccentColor.InternalId.REPLIES;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        return chat.accentColorId;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        long userId = chatUserId(chat);
        return cache().userAccentColorId(userId);
      }
      default: {
        Td.assertChatType_e562ec7d();
        throw Td.unsupported(chat.type);
      }
    }
  }

  public TdlibAccentColor messageAccentColor (@NonNull TdApi.Message message) {
    if (message.forwardInfo != null) {
      switch (message.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageOriginChat.CONSTRUCTOR:
          return chatAccentColor(((TdApi.MessageOriginChat) message.forwardInfo.origin).senderChatId);
        case TdApi.MessageOriginUser.CONSTRUCTOR:
          return cache().userAccentColor(((TdApi.MessageOriginUser) message.forwardInfo.origin).senderUserId);
        case TdApi.MessageOriginChannel.CONSTRUCTOR:
          return chatAccentColor(((TdApi.MessageOriginChannel) message.forwardInfo.origin).chatId);
        case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
          return null;
        default:
          Td.assertMessageOrigin_f2224a59();
          throw Td.unsupported(message.forwardInfo.origin);
      }
    }
    return senderAccentColor(message.senderId);
  }

  public TdlibAccentColor chatAccentColor (long chatId) {
    int accentColorId = chatAccentColorId(chatId);
    return accentColor(accentColorId);
  }

  public TdlibAccentColor chatAccentColor (@Nullable TdApi.Chat chat) {
    int accentColorId = chatAccentColorId(chat);
    return accentColor(accentColorId);
  }

  public TdlibAccentColor senderAccentColor (TdApi.MessageSender sender) {
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        TdApi.MessageSenderUser user = (TdApi.MessageSenderUser) sender;
        return cache().userAccentColor(user.userId);
      }
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        TdApi.MessageSenderChat chat = (TdApi.MessageSenderChat) sender;
        return chatAccentColor(chat.chatId);
      }
      default: {
        Td.assertMessageSender_439d4c9c();
        throw Td.unsupported(sender);
      }
    }
  }

  public String messageAuthor (TdApi.Message message) {
    return messageAuthor(message, true, false);
  }

  public String messageAuthorUsername (TdApi.Message message) {
    long chatId = Td.getMessageAuthorId(message);
    if (chatId != 0) {
      if (ChatId.isPrivate(chatId)) {
        return cache().userUsername(ChatId.toUserId(chatId));
      } else {
        return chatUsername(chatId);
      }
    }
    return null;
  }

  public String messageAuthor (TdApi.Message message, boolean allowSignature, boolean shorten) {
    if (message == null)
      return null;
    if (message.forwardInfo != null) {
      switch (message.forwardInfo.origin.getConstructor()) {
        case TdApi.MessageOriginUser.CONSTRUCTOR: {
          long userId = ((TdApi.MessageOriginUser) message.forwardInfo.origin).senderUserId;
          return shorten ? cache().userFirstName(userId) : cache().userName(userId);
        }
        case TdApi.MessageOriginChannel.CONSTRUCTOR: {
          TdApi.MessageOriginChannel info = (TdApi.MessageOriginChannel) message.forwardInfo.origin;
          if (allowSignature && !StringUtils.isEmpty(info.authorSignature))
            return info.authorSignature;
          TdApi.Chat chat = chat(info.chatId);
          if (chat != null)
            return chat.title;
          break;
        }
        case TdApi.MessageOriginChat.CONSTRUCTOR: {
          TdApi.MessageOriginChat info = (TdApi.MessageOriginChat) message.forwardInfo.origin;
          if (allowSignature && !StringUtils.isEmpty(info.authorSignature))
            return info.authorSignature;
          TdApi.Chat chat = chat(info.senderChatId);
          if (chat != null)
            return chat.title;
          break;
        }
        case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
          break;
        default: {
          Td.assertMessageOrigin_f2224a59();
          throw Td.unsupported(message.forwardInfo.origin);
        }
      }
    }
    if (message.senderId == null)
      return null;
    return senderName(message.senderId, shorten);
  }

  public String chatTitle (long chatId) {
    return chatTitle(chatId, true, false);
  }

  public String chatTitleShort (long chatId) {
    return chatTitle(chatId, true, true);
  }

  public String chatTitle (long chatId, boolean allowSavedMessages, boolean shorten) {
    TdApi.Chat chat = chat(chatId);
    if (chat != null) {
      return chatTitle(chat, allowSavedMessages, shorten);
    }
    if (chatId == CLOUD_RESOURCES_CHAT_ID && ".".equals(chat.title)) {
      return Lang.getString(R.string.EmojiSets);
    }
    final long userId = chatUserId(chatId);
    return userId != 0 ? cache().userDisplayName(userId, allowSavedMessages, shorten) : null;
  }

  public boolean canReportMessage (TdApi.Message message) {
    return message != null && !isSelfChat(message.chatId) && !message.isOutgoing && message.sendingState == null && canReportChatSpam(message.chatId);
  }

  public boolean canReportChatSpam (long chatId) {
    return canReportChatSpam(chat(chatId));
  }

  public boolean canReportChatSpam (TdApi.Chat chat) {
    return chat != null && chat.canBeReported;
  }

  public String chatTitle (TdApi.Chat chat) {
    return chatTitle(chat, true, false);
  }

  public String chatTitle (TdApi.Chat chat, boolean allowSavedMessages) {
    return chatTitle(chat, allowSavedMessages, false);
  }

  public String chatTitle (TdApi.Chat chat, boolean allowSavedMessages, boolean shorten) {
    if (chat.id == CLOUD_RESOURCES_CHAT_ID && ".".equals(chat.title)) {
      return Lang.getString(R.string.EmojiSets);
    }
    final long userId = chatUserId(chat.id);
    return userId != 0 ? cache().userDisplayName(userId, allowSavedMessages, shorten) : chat.title;
  }

  public long chatUserId (TdApi.Chat chat) {
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
        return ((TdApi.ChatTypePrivate) chat.type).userId;
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return ((TdApi.ChatTypeSecret) chat.type).userId;
    }
    return 0;
  }

  public @Nullable TdApi.User chatUser (TdApi.Chat chat) {
    final long userId = chatUserId(chat);
    return userId != 0 ? cache().user(userId) : null;
  }

  public long chatUserId (long chatId) {
    if (ChatId.isPrivate(chatId)) {
      return ChatId.toUserId(chatId);
    } else if (ChatId.isSecret(chatId)) {
      int secretChatId = ChatId.toSecretChatId(chatId);
      TdApi.SecretChat secretChat = cache().secretChat(secretChatId);
      if (secretChat != null) {
        return secretChat.userId;
      }
    }
    return 0;
  }

  @Nullable
  public TdApi.ChatPhoto chatPhoto (long chatId) {
    return chatPhoto(chatId, true);
  }

  @Nullable
  public TdApi.ChatPhoto chatPhoto (long chatId, boolean allowRequest) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        long userId = chatUserId(chatId);
        TdApi.UserFullInfo userFullInfo = userId != 0 ? cache().userFull(userId, allowRequest) : null;
        return userFullInfo != null ? userFullInfo.photo : null;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.SupergroupFullInfo supergroupFullInfo = supergroupId != 0 ? cache().supergroupFull(supergroupId, allowRequest) : null;
        return supergroupFullInfo != null ? supergroupFullInfo.photo : null;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        long basicGroupId = ChatId.toBasicGroupId(chatId);
        TdApi.BasicGroupFullInfo basicGroupFullInfo = basicGroupId != 0 ? cache().basicGroupFull(basicGroupId, allowRequest) : null;
        return basicGroupFullInfo != null ? basicGroupFullInfo.photo : null;
      }
      default: {
        Td.assertChatType_e562ec7d();
        throw new UnsupportedOperationException(Long.toString(chatId));
      }
    }
  }

  public TdApi.MessageSender sender (long chatId) {
    long userId = chatUserId(chatId);
    return userId != 0 ? new TdApi.MessageSenderUser(userId) : new TdApi.MessageSenderChat(chatId);
  }

  public boolean isSelfSender (TdApi.MessageSender sender) {
    return sender != null && isSelfUserId(Td.getSenderUserId(sender));
  }

  public boolean isSelfSender (TdApi.Message message) {
    return message != null && (message.isOutgoing || isSelfSender(message.senderId));
  }

  public boolean senderContactOrCloseFirend (TdApi.MessageSender sender) {
    long userId = Td.getSenderUserId(sender);
    TdApi.User user = userId != 0 ? cache().user(userId) : null;
    return user != null && (user.isContact || user.isCloseFriend);
  }

  public @Nullable TdApi.User chatUser (long chatId) {
    long userId = chatUserId(chatId);
    if (userId != 0) {
      return cache().user(userId);
    } else {
      return null;
    }
  }

  public boolean chatUserDeleted (TdApi.Chat chat) {
    TdApi.User user = chatUser(chat);
    return user != null && user.type.getConstructor() == TdApi.UserTypeDeleted.CONSTRUCTOR;
  }

  public boolean isForum (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && supergroup.isForum;
  }

  public @Nullable TdApi.BlockList chatBlockList (TdApi.Chat chat) {
    return chat != null ? chatBlockList(chat.id) : null;
  }

  public @Nullable TdApi.BlockList chatBlockList (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null ? chat.blockList : null;
  }

  public boolean chatFullyBlocked (long chatId) {
    TdApi.BlockList blockList = chatBlockList(chatId);
    return blockList != null && blockList.getConstructor() == TdApi.BlockListMain.CONSTRUCTOR;
  }
  @Nullable
  public String chatUsername (TdApi.Chat chat) {
    TdApi.Usernames usernames = chatUsernames(chat);
    return Td.primaryUsername(usernames);
  }

  @Nullable
  public TdApi.Usernames chatUsernames (TdApi.Chat chat) {
    if (chat == null) {
      return null;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return null;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        long userId = chatUserId(chat);
        return cache().userUsernames(userId);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
        return cache().supergroupUsernames(supergroupId);
      }
      default: {
        Td.assertChatType_e562ec7d();
        throw Td.unsupported(chat.type);
      }
    }
  }

  @Nullable
  public TdApi.Usernames chatUsernames (long chatId) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return null;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        long userId = ChatId.toUserId(chatId);
        return cache().userUsernames(userId);
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int secretChatId = ChatId.toSecretChatId(chatId);
        TdApi.SecretChat secretChat = cache().secretChat(secretChatId);
        if (secretChat != null) {
          return cache().userUsernames(secretChat.userId);
        }
        return null;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        long supergroupId = ChatId.toSupergroupId(chatId);
        return cache().supergroupUsernames(supergroupId);
      }
      default: {
        Td.assertChatType_e562ec7d();
        throw new UnsupportedOperationException(Long.toString(chatId));
      }
    }
  }

  @Nullable
  public String chatUsername (long chatId) {
    TdApi.Usernames usernames = chatUsernames(chatId);
    return Td.primaryUsername(usernames);
  }

  public TdApi.ChatLocation chatLocation (long chatId) {
    if (chatId == 0) {
      return null;
    }
    if (ChatId.getType(chatId) == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
      TdApi.SupergroupFullInfo fullInfo = cache().supergroupFull(ChatId.toSupergroupId(chatId));
      return fullInfo != null ? fullInfo.location : null;
    }
    return null;
  }

  public boolean chatBasicGroupActive (long chatId) {
    long basicGroupId = ChatId.toBasicGroupId(chatId);
    return basicGroupId != 0 && cache().basicGroupActive(basicGroupId);
  }

  public void withChannelBotUserId (RunnableLong runnable) {
    client().send(new TdApi.SearchPublicChat("Channel_Bot"), result -> ui().post(() -> {
      long userId = result.getConstructor() == TdApi.Chat.CONSTRUCTOR ? chatUserId((TdApi.Chat) result) : 0;
      runnable.runWithLong(userId != 0 ? userId : telegramChannelBotUserId());
    }));
  }

  public boolean canCopyPublicMessageLinks (long chatId) {
    if (chatId == 0) {
      return false;
    }
    long supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0) {
      return false;
    }
    TdApi.Usernames usernames = cache().supergroupUsernames(supergroupId);
    return Td.hasUsername(usernames);
  }

  public boolean chatPublic (long chatId) {
    if (chatId == 0) {
      return false;
    }
    long supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0) {
      return false;
    }
    TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
    return supergroup != null && (Td.hasUsername(supergroup) || supergroup.hasLocation);
  }

  public TdApi.ChatSource chatSource (TdApi.ChatList chatList, long chatId) {
    if (chatId == 0)
      return null;
    TdApi.Chat chat = chat(chatId);
    TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
    return position != null ? position.source : null;
  }

  public boolean chatPinned (TdApi.ChatList chatList, long chatId) {
    if (chatId == 0) {
      return false;
    }
    TdApi.Chat chat = chat(chatId);
    return ChatPosition.isPinned(chat, chatList);
  }

  public @NonNull List<Long> getPinnedChats (final @Nullable TdApi.ChatList chatList) {
    synchronized (dataLock) {
      List<TdApi.Chat> pinnedChats = null;
      for (TdApi.Chat chat : chats.values()) {
        TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
        if (position != null && position.isPinned) {
          if (pinnedChats == null)
            pinnedChats = new ArrayList<>();
          pinnedChats.add(chat);
        }
      }
      if (pinnedChats != null) {
        Td.sort(pinnedChats, chatList);
        List<Long> pinnedChatIds = new ArrayList<>(pinnedChats.size());
        for (TdApi.Chat chat : pinnedChats) {
          pinnedChatIds.add(chat.id);
        }
        return pinnedChatIds;
      }
      return new ArrayList<>();
    }
  }

  public int chatFoldersCount () {
    synchronized (dataLock) {
      return chatFolders.length;
    }
  }

  public TdApi.ChatFolderInfo[] chatFolders () {
    synchronized (dataLock) {
      return chatFolders;
    }
  }

  public int mainChatListPosition () {
    synchronized (dataLock) {
      return mainChatListPosition;
    }
  }

  public TdApi.ChatFolderInfo chatFolderInfo (int chatFolderId) {
    synchronized (dataLock) {
      if (chatFolders != null) {
        for (TdApi.ChatFolderInfo filter : chatFolders) {
          if (filter.id == chatFolderId)
            return filter;
        }
      }
    }
    return null;
  }

  public boolean hasFolders () {
    synchronized (dataLock) {
      return chatFolders != null && chatFolders.length > 0;
    }
  }

  public boolean canArchiveChat (TdApi.ChatList chatList, TdApi.Chat chat) {
    if (chat == null)
      return false;
    if (chatList != null) {
      switch (chatList.getConstructor()) {
        case TdApi.ChatListMain.CONSTRUCTOR:
        case TdApi.ChatListArchive.CONSTRUCTOR:
          break;
        case TdApi.ChatListFolder.CONSTRUCTOR:
          return false;
      }
    }
    TdApi.ChatPosition[] positions = chat.positions;
    if (positions != null) {
      for (TdApi.ChatPosition position : positions) {
        switch (position.list.getConstructor()) {
          case TdApi.ChatListMain.CONSTRUCTOR:
            return !isSelfChat(chat.id) && !isServiceNotificationsChat(chat.id);
          case TdApi.ChatListArchive.CONSTRUCTOR:
            return true; // Already archived
          case TdApi.ChatListFolder.CONSTRUCTOR:
            break;
        }
      }
    }
    return false;
  }

  public boolean chatArchived (long chatId) {
    if (chatId == 0)
      return false;
    TdApi.Chat chat = chat(chatId);
    return chat != null && ChatPosition.findPosition(chat, ChatPosition.CHAT_LIST_ARCHIVE) != null;
  }

  public boolean chatNeedsMuteIcon (long chatId) {
    return chatNeedsMuteIcon(chat(chatId));
  }

  public boolean chatNeedsMuteIcon (TdApi.Chat chat) {
    return chat != null && TD.needMuteIcon(chat.notificationSettings, scopeNotificationSettings(chat.id));
  }

  public boolean chatNotificationsEnabled (long chatId) {
    return chatNotificationsEnabled(chat(chatId));
  }

  public boolean chatNotificationsEnabled (TdApi.Chat chat) {
    if (chat == null)
      return true;
    if (!chat.notificationSettings.useDefaultMuteFor) {
      return chat.notificationSettings.muteFor == 0;
    } else {
      return scopeMuteFor(notificationManager.scope(chat)) == 0;
    }
  }

  public int scopeMuteFor (TdApi.NotificationSettingsScope scope) {
    TdApi.ScopeNotificationSettings settings = notificationManager.getScopeNotificationSettings(scope);
    return settings != null ? settings.muteFor : 0;
  }

  public boolean chatDefaultDisableNotifications (long chatId) {
    if (Config.NEED_SILENT_BROADCAST) {
      TdApi.Chat chat = chat(chatId);
      return chat != null && chat.defaultDisableNotification;
    }
    return false;
  }

  public int chatMuteFor (long chatId) {
    return chatMuteFor(chat(chatId));
  }

  public int chatMuteFor (TdApi.Chat chat) {
    if (chat == null)
      return 0;
    if (chat.notificationSettings.useDefaultMuteFor) {
      TdApi.ScopeNotificationSettings notificationSettings = notificationManager.getScopeNotificationSettings(chat);
      return notificationSettings != null ? notificationSettings.muteFor : 0;
    }
    return chat.notificationSettings.muteFor;
  }

  public int chatTTL (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null ? chat.messageAutoDeleteTime : 0;
  }

  public boolean chatSupportsRoundVideos (long chatId) {
    if (chatId == 0) {
      return false;
    }
    if (ChatId.isSecret(chatId)) {
      TdApi.SecretChat secretChat = chatToSecretChat(chatId);
      return secretChat != null && secretChat.layer >= 66;
    }
    return true;
  }

  public boolean messageSending (TdApi.Message msg) {
    return msg != null && msg.sendingState != null && !qack().isMessageAcknowledged(msg.chatId, msg.id);
  }

  public boolean messageBeingEdited (TdApi.Message msg) {
    return msg != null && getPendingFormattedText(msg.chatId, msg.id) != null;
  }

  public boolean albumBeingEdited (Album album) {
    if (album != null) {
      for (TdApi.Message message : album.messages) {
        if (messageBeingEdited(message)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean canCopyPostLink (TdApi.Message msg) {
    return msg != null && msg.sendingState == null && ChatId.toSupergroupId(msg.chatId) != 0 && !TD.isScheduled(msg);
  }

  @Nullable
  public TdApi.Usernames messageUsernames (TdApi.Message msg) {
    if (msg != null) {
      final long userId = msg.viaBotUserId != 0 ? msg.viaBotUserId : Td.getSenderUserId(msg);
      if (userId != 0) {
        TdApi.User user = cache().user(userId);
        return user != null ? user.usernames : null;
      }
    }
    return null;
  }

  @Nullable
  public String messageUsername (TdApi.Message msg) {
    TdApi.Usernames usernames = messageUsernames(msg);
    return Td.primaryUsername(usernames);
  }

  public boolean isSameSender (TdApi.Message a, TdApi.Message b) {
    String psa1 = a.forwardInfo != null ? a.forwardInfo.publicServiceAnnouncementType : null;
    String psa2 = b.forwardInfo != null ? b.forwardInfo.publicServiceAnnouncementType : null;
    return Td.equalsTo(a.senderId, b.senderId) && StringUtils.equalsOrBothEmpty(psa1, psa2);
  }

  public long senderUserId (TdApi.Message msg) {
    if (msg == null) {
      return 0;
    }
    if (isSelfChat(msg.chatId)) {
      if (msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageOriginUser.CONSTRUCTOR)
        return ((TdApi.MessageOriginUser) msg.forwardInfo.origin).senderUserId;
    }
    return Td.getSenderUserId(msg);
  }

  public String sponsorName (TdApi.MessageSponsor sponsor) {
    switch (sponsor.type.getConstructor()) {
      case TdApi.MessageSponsorTypeBot.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeBot bot = (TdApi.MessageSponsorTypeBot) sponsor.type;
        return cache().userName(bot.botUserId);
      }
      case TdApi.MessageSponsorTypeWebApp.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeWebApp webApp = (TdApi.MessageSponsorTypeWebApp) sponsor.type;
        return webApp.webAppTitle;
      }
      case TdApi.MessageSponsorTypePublicChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePublicChannel publicChannel = (TdApi.MessageSponsorTypePublicChannel) sponsor.type;
        return chatTitle(publicChannel.chatId);
      }
      case TdApi.MessageSponsorTypePrivateChannel.CONSTRUCTOR: {
        TdApi.MessageSponsorTypePrivateChannel privateChannel = (TdApi.MessageSponsorTypePrivateChannel) sponsor.type;
        return privateChannel.title;
      }
      case TdApi.MessageSponsorTypeWebsite.CONSTRUCTOR: {
        TdApi.MessageSponsorTypeWebsite website = (TdApi.MessageSponsorTypeWebsite) sponsor.type;
        return website.name;
      }
      default:
        Td.assertMessageSponsorType_cdabde01();
        throw Td.unsupported(sponsor.type);
    }
  }

  public String senderName (TdApi.Message msg, boolean allowForward, boolean shorten) {
    long authorId = Td.getMessageAuthorId(msg, allowForward);
    if (authorId == 0 && allowForward) {
      if (msg.forwardInfo != null) {
        if (msg.forwardInfo.origin.getConstructor() == TdApi.MessageOriginHiddenUser.CONSTRUCTOR) {
          return ((TdApi.MessageOriginHiddenUser) msg.forwardInfo.origin).senderName;
        } else {
          authorId = Td.getMessageAuthorId(msg, false);
        }
      }
      if (msg.importInfo != null && authorId == 0) {
        return msg.importInfo.senderName;
      }
    }
    if (ChatId.isUserChat(authorId)) {
      long userId = chatUserId(authorId);
      return shorten ? cache().userFirstName(userId) : cache().userName(userId);
    } else if (authorId == msg.chatId && !StringUtils.isEmpty(msg.authorSignature)) {
      return shorten ? msg.authorSignature : Lang.getString(isChannel(msg.chatId) ? R.string.format_channelAndSignature : R.string.format_chatAndSignature, chatTitle(authorId), msg.authorSignature);
    } else {
      return chatTitle(authorId);
    }
  }

  public String senderName (TdApi.MessageSender sender) {
    return senderName(sender, false);
  }

  public String senderName (TdApi.MessageSender sender, boolean shorten) {
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        long chatId = ((TdApi.MessageSenderChat) sender).chatId;
        return chatTitle(chatId);
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        long userId = ((TdApi.MessageSenderUser) sender).userId;
        return shorten ? cache().userFirstName(userId) : cache().userName(userId);
      }
    }
    throw new RuntimeException(sender.toString());
  }

  public boolean needUserAvatarPreviewAnimation (long userId) {
    if (userId != 0) {
      TdApi.User user = cache().user(userId);
      return user != null && user.isPremium;
    }
    return false;
  }

  public boolean needAvatarPreviewAnimation (long chatId) {
    if (chatId != 0) {
      long userId = chatUserId(chatId);
      return needUserAvatarPreviewAnimation(chatUserId(chatId));
    }
    return false;
  }

  public boolean needAvatarPreviewAnimation (TdApi.MessageSender sender) {
    return senderPremium(sender);
  }

  public boolean senderPremium (TdApi.MessageSender sender) {
    if (sender == null) {
      return false;
    }
    long userId;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR:
        userId = chatUserId(((TdApi.MessageSenderChat) sender).chatId);
        break;
      case TdApi.MessageSenderUser.CONSTRUCTOR:
        userId = ((TdApi.MessageSenderUser) sender).userId;
        break;
      default:
        Td.assertMessageSender_439d4c9c();
        throw Td.unsupported(sender);
    }
    TdApi.User user = cache().user(userId);
    return user != null && user.isPremium;
  }

  public String senderUsername (TdApi.MessageSender sender) {
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        return chatUsername(((TdApi.MessageSenderChat) sender).chatId);
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        return cache().userUsername(((TdApi.MessageSenderUser) sender).userId);
      }
    }
    throw new IllegalArgumentException(sender.toString());
  }

  public boolean isFromAnonymousGroupAdmin (TdApi.Message message) {
    return message != null && message.chatId == Td.getSenderId(message) && isMultiChat(message.chatId);
  }

  public boolean isContactChat (TdApi.Chat chat) {
    if (chat != null) {
      TdApi.User user = chatUser(chat);
      return TD.isContact(user) && !isSelfUserId(user.id);
    }
    return false;
  }

  public void markChatAsRead (long chatId, TdApi.MessageSource source, boolean allowRemoveMarkedAsUnrad, @Nullable Runnable after) {
    TdApi.Chat chat = chat(chatId);
    if (chat != null) {
      if (chat.isMarkedAsUnread && allowRemoveMarkedAsUnrad) {
        switch (source.getConstructor()) {
          case TdApi.MessageSourceChatHistory.CONSTRUCTOR:
          case TdApi.MessageSourceHistoryPreview.CONSTRUCTOR:
          case TdApi.MessageSourceChatList.CONSTRUCTOR:
          case TdApi.MessageSourceSearch.CONSTRUCTOR:
          case TdApi.MessageSourceChatEventLog.CONSTRUCTOR:
          case TdApi.MessageSourceNotification.CONSTRUCTOR:
          case TdApi.MessageSourceOther.CONSTRUCTOR:
            client().send(new TdApi.ToggleChatIsMarkedAsUnread(chat.id, false), okHandler(after));
            break;
          case TdApi.MessageSourceMessageThreadHistory.CONSTRUCTOR:
          case TdApi.MessageSourceForumTopicHistory.CONSTRUCTOR:
            // No need to remove marked as unread mark
            break;
        }
      }
      if (!hasPasscode(chat) && chat.lastMessage != null) {
        client().send(new TdApi.ViewMessages(chatId, new long[] {chat.lastMessage.id}, source, true), okHandler(after));
      }
    }
  }

  public void markChatAsUnread (@Nullable TdApi.Chat chat, @Nullable Runnable after) {
    if (chat != null && chat.unreadCount == 0) {
      if (!chat.isMarkedAsUnread) {
        client().send(new TdApi.ToggleChatIsMarkedAsUnread(chat.id, true), okHandler(after));
      }
    }
  }

  public static class MessageLink {
    public final String url;
    public final boolean isPublic;

    public MessageLink (@NonNull String url, boolean isPublic) {
      this.url = url;
      this.isPublic = isPublic;
    }

    @NonNull
    @Override
    public String toString () {
      return url;
    }
  }

  public void getMessageLink (TdApi.Message message, boolean forAlbum, boolean forComment, RunnableData<MessageLink> after) {
    AtomicBoolean signal = new AtomicBoolean(false);
    CancellableRunnable fallback;
    if (message.sendingState == null && !ChatId.isUserChat(message.chatId) && MessageId.toServerMessageId(message.id) != 0 && !forComment) {
      fallback = new CancellableRunnable() {
        @Override
        public void act () {
          synchronized (signal) {
            if (signal.get())
              return;
            String fallbackUrl; boolean fallbackPrivate;
            String username = chatUsername(message.chatId);
            if (!StringUtils.isEmpty(username)) {
              fallbackPrivate = false;
              fallbackUrl = tMeMessageUrl(username, MessageId.toServerMessageId(message.id));
              if (!forAlbum && message.mediaAlbumId != 0)
                fallbackUrl += "?single";
            } else {
              fallbackPrivate = true;
              long supergroupId = ChatId.toSupergroupId(message.chatId);
              if (supergroupId != 0)
                fallbackUrl = tMePrivateMessageUrl(supergroupId, MessageId.toServerMessageId(message.id));
              else
                fallbackUrl = null;
            }
            if (!StringUtils.isEmpty(fallbackUrl) && !signal.getAndSet(true)) {
              after.runWithData(new MessageLink(fallbackUrl, fallbackPrivate));
            }
          }
        }
      };
    } else {
      fallback = null;
    }
    client().send(new TdApi.GetMessageLink(message.chatId, message.id, 0, forAlbum, forComment), object -> {
      switch (object.getConstructor()) {
        case TdApi.MessageLink.CONSTRUCTOR: {
          TdApi.MessageLink link = (TdApi.MessageLink) object;
          ui().post(() -> {
            synchronized (signal) {
              if (!signal.getAndSet(true)) {
                if (fallback != null)
                  fallback.cancel();
                after.runWithData(new MessageLink(link.link, link.isPublic));
              }
            }
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e("Could not fetch message link: %s", TD.toErrorString(object));
          if (fallback != null) {
            ui().post(fallback);
          }
          break;
        }
      }
    });
    if (fallback != null) {
      ui().postDelayed(fallback, 500l);
    }
  }

  public boolean canMarkAsRead (@Nullable TdApi.Chat chat) {
    return chat != null && (chat.unreadCount > 0 || chat.isMarkedAsUnread);
  }

  public boolean isMultiChat (long chatId) {
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return true;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        return isSupergroup(chatId);
    }
    return false;
  }


  public boolean isMultiChat (TdApi.Chat chat) {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          return true;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          return !((TdApi.ChatTypeSupergroup) chat.type).isChannel;
      }
    }
    return false;
  }

  public boolean isChannelFast (long chatId) {
    long supergroupId = ChatId.toSupergroupId(chatId);
    return supergroupId != 0 && channels.contains(supergroupId);
  }

  public boolean isChannel (TdApi.MessageSender senderId) {
    long chatId = Td.getSenderId(senderId);
    return chatId != 0 && isChannel(chatId);
  }

  public boolean isUser (TdApi.MessageSender senderId) {
    long chatId = Td.getSenderId(senderId);
    return chatUserId(chatId) != 0;
  }

  public boolean isChannel (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && supergroup.isChannel;
  }

  public boolean isSupergroup (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && !supergroup.isChannel;
  }

  public boolean isChannelAutoForward (@Nullable TdApi.Message message) {
    return message != null && message.chatId != 0 && message.forwardInfo != null &&
      message.forwardInfo.fromChatId != 0 && message.forwardInfo.fromMessageId != 0 &&
      message.forwardInfo.fromChatId != message.chatId &&
      message.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR &&
      ((TdApi.MessageSenderChat) message.senderId).chatId == message.forwardInfo.fromChatId &&
      isSupergroup(message.chatId) && isChannel(message.forwardInfo.fromChatId);
  }

  public boolean canDisablePinnedMessageNotifications (long chatId) {
    return !ChatId.isUserChat(chatId);
  }

  public boolean canDisableMentions (long chatId) {
    return isMultiChat(chatId);
  }

  public boolean isSupergroupChat (TdApi.Chat chat) {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && !((TdApi.ChatTypeSupergroup) chat.type).isChannel;
  }

  public boolean isChannelChat (TdApi.Chat chat) {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR && ((TdApi.ChatTypeSupergroup) chat.type).isChannel;
  }

  public boolean isUserChat (TdApi.Chat chat) {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
        case TdApi.ChatTypePrivate.CONSTRUCTOR:
          return true;
      }
    }
    return false;
  }

  public boolean isUserChat (long chatId) {
    return ChatId.isUserChat(chatId);
  }

  public boolean isRepliesChat (long chatId) {
    return (repliesBotChatId != 0 && repliesBotChatId == chatId) || (chatId == ChatId.fromUserId(repliesBotUserId));
  }

  public boolean isServiceNotificationsChat (long chatId) {
    return (telegramServiceNotificationsChatId != 0 && telegramServiceNotificationsChatId == chatId) || (chatId == ChatId.fromUserId(TdConstants.TELEGRAM_ACCOUNT_ID));
  }

  public long serviceNotificationsChatId () {
    return telegramServiceNotificationsChatId != 0 ? telegramServiceNotificationsChatId : ChatId.fromUserId(TdConstants.TELEGRAM_ACCOUNT_ID);
  }

  public boolean isBotFatherChat (long chatId) {
    return chatId == ChatId.fromUserId(TdConstants.TELEGRAM_BOT_FATHER_ACCOUNT_ID) || TdConstants.TELEGRAM_BOT_FATHER_USERNAME.equals(chatUsername(chatId));
  }

  public boolean suggestStopBot (long chatId) {
    return suggestStopBot(chat(chatId));
  }

  public boolean suggestStopBot (TdApi.Chat chat) {
    if (chat != null && isBotChat(chat)) {
      TdApi.User user = chatUser(chat);
      return user == null || !user.isSupport;
    }
    return false;
  }

  public boolean isBotChat (long chatId) {
    long userId = chatUserId(chatId);
    return cache().userBot(userId);
  }

  public boolean isBotChat (TdApi.Chat chat) {
    long userId = chatUserId(chat);
    return cache().userBot(userId);
  }

  public boolean isSupportChat (TdApi.Chat chat) {
    TdApi.User user = chatUser(chat);
    return user != null && user.isSupport;
  }

  public boolean isDeletedAccountChat (long chatId) {
    TdApi.User user = chatUser(chatId);
    return TD.isUserDeleted(user);
  }

  public boolean chatVerified (TdApi.Chat chat) {
    if (chat == null) {
      return false;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        TdApi.User user = chatUser(chat);
        return user != null && user.isVerified;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        TdApi.Supergroup supergroup = cache().supergroup(ChatId.toSupergroupId(chat.id));
        return supergroup != null && supergroup.isVerified;
    }
    return false;
  }

  public boolean chatScam (TdApi.Chat chat) {
    if (chat == null) {
      return false;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        TdApi.User user = chatUser(chat);
        return user != null && user.isScam;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        TdApi.Supergroup supergroup = cache().supergroup(ChatId.toSupergroupId(chat.id));
        return supergroup != null && supergroup.isScam;
    }
    return false;
  }

  public boolean chatFake (TdApi.Chat chat) {
    if (chat == null) {
      return false;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        TdApi.User user = chatUser(chat);
        return user != null && user.isFake;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        TdApi.Supergroup supergroup = cache().supergroup(ChatId.toSupergroupId(chat.id));
        return supergroup != null && supergroup.isFake;
    }
    return false;
  }

  public boolean chatRestricted (long chatId) {
    return !StringUtils.isEmpty(chatRestrictionReason(chatId));
  }

  public boolean chatRestricted (TdApi.Chat chat) {
    return !StringUtils.isEmpty(chatRestrictionReason(chat));
  }

  public String chatRestrictionReason (long chatId) {
    return chatRestrictionReason(chatStrict(chatId));
  }

  public String chatRestrictionReason (TdApi.Chat chat) {
    if (chat == null || !Settings.instance().needRestrictContent()) {
      return null;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        TdApi.User user = chatUser(chat);
        return user != null && !StringUtils.isEmpty(user.restrictionReason) ? user.restrictionReason : null;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        TdApi.Supergroup supergroup = cache().supergroup(ChatId.toSupergroupId(chat.id));
        return supergroup != null && !StringUtils.isEmpty(supergroup.restrictionReason) ? supergroup.restrictionReason : null;
    }
    return null;
  }

  public boolean chatAvailable (TdApi.Chat chat) {
    if (chat == null)
      return false;
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    boolean isMember = TD.isMember(status, false);
    if (isMember || chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR)
      return isMember;
    if (chat.lastMessage != null)
      return true;
    TdApi.ChatPosition[] positions = chat.positions;
    if (positions != null) {
      for (TdApi.ChatPosition position : positions) {
        if (position.order != 0)
          return true;
      }
    }
    return false;
  }

  public boolean chatReactionsEnabled (long chatId) {
    TdApi.Chat chat = chat(chatId);
    if (chat == null)
      return false;
    TdApi.ChatAvailableReactions availableReactions = chat.availableReactions;
    if (availableReactions == null)
      return false;
    if (availableReactions.getConstructor() == TdApi.ChatAvailableReactionsSome.CONSTRUCTOR)
      return ((TdApi.ChatAvailableReactionsSome) availableReactions).reactions.length > 0;
    return true;
  }

  public boolean chatHasScheduled (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null && chat.hasScheduledMessages;
  }

  public long calleeUserId (TdApi.Message message) {
    return message.isOutgoing ? chatUserId(message.chatId) : senderUserId(message);
  }

  @Nullable
  public String getDiceEmoji (TdApi.FormattedText text) {
    //noinspection UnsafeOptInUsageError
    if (!Td.isEmpty(text) && (text.entities == null || text.entities.length == 0)) {
      String trimmed = text.text.trim();
      if (isDiceEmoji(trimmed)) {
        return trimmed;
      }
    }
    return null;
  }

  /*public ArrayList<TGReaction> getNotPremiumReactions () {
    synchronized (dataLock) {
      return notPremiumReactions;
    }
  }

  public ArrayList<TGReaction> getOnlyPremiumReactions () {
    synchronized (dataLock) {
      return onlyPremiumReactions;
    }
  }

  public int getTotalActiveReactionsCount () {
    synchronized (dataLock) {
      return notPremiumReactions.size() + onlyPremiumReactions.size();
    }
  }*/

  public boolean isActiveEmojiReaction (String emoji) {
    if (StringUtils.isEmpty(emoji)) {
      return false;
    }
    synchronized (dataLock) {
      if (activeEmojiReactions != null) {
        return ArrayUtils.contains(activeEmojiReactions, emoji);
      }
    }
    return false;
  }

  public String[] getActiveEmojiReactions () {
    synchronized (dataLock) {
      return activeEmojiReactions;
    }
  }

  @NonNull
  public String defaultEmojiReaction () {
    synchronized (dataLock) {
      if (defaultReactionType != null && defaultReactionType.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
        return ((TdApi.ReactionTypeEmoji) defaultReactionType).emoji;
      }
    }
    return "\uD83D\uDC4D"; // Thumbs up
  }

  public void ensureEmojiReactionsAvailable (@Nullable RunnableBool after) {
    ensureReactionsAvailable(new TdApi.ChatAvailableReactionsAll(), after);
  }

  public void ensureReactionsAvailable (String[] reactionKeys, @Nullable RunnableBool after) {
    Set<String> uniqueReactionKeys = new HashSet<>(reactionKeys.length);
    Collections.addAll(uniqueReactionKeys, reactionKeys);
    TdApi.ReactionType[] reactionTypes = new TdApi.ReactionType[uniqueReactionKeys.size()];
    int index = 0;
    for (String reactionKey : uniqueReactionKeys) {
      reactionTypes[index] = TD.toReactionType(reactionKey);
      index++;
    }
    ensureReactionsAvailable(new TdApi.ChatAvailableReactionsSome(reactionTypes), after);
  }

  public void ensureReactionsAvailable (@NonNull TdApi.AvailableReactions reactions, @Nullable RunnableBool after) {
    List<TdApi.ReactionType> reactionTypes = new ArrayList<>(
      reactions.recentReactions.length +
      reactions.topReactions.length +
      reactions.popularReactions.length
    );
    for (TdApi.AvailableReaction availableReaction : reactions.recentReactions) {
      reactionTypes.add(availableReaction.type);
    }
    for (TdApi.AvailableReaction availableReaction : reactions.topReactions) {
      reactionTypes.add(availableReaction.type);
    }
    for (TdApi.AvailableReaction availableReaction : reactions.popularReactions) {
      reactionTypes.add(availableReaction.type);
    }
    ensureReactionsAvailable(new TdApi.ChatAvailableReactionsSome(reactionTypes.toArray(new TdApi.ReactionType[0])), after);
  }

  public void ensureReactionsAvailable (@NonNull TdApi.ChatAvailableReactions reactions, @Nullable RunnableBool after) {
    final AtomicInteger remaining = new AtomicInteger();
    TdlibEmojiReactionsManager.Watcher emojiReactionWatcher = (context, entry) -> {
      /*if (entry.value != null) {
        synchronized (dataLock) {
          cachedReactions.put(entry.key, new TGReaction(this, entry.value));
        }
      }*/
      if (remaining.decrementAndGet() == 0) {
        if (after != null) {
          after.runWithBool(true);
        }
      }
    };
    TdlibEmojiManager.Watcher customReactionWatcher = (context, entry) -> {
      if (remaining.decrementAndGet() == 0) {
        if (after != null) {
          after.runWithBool(true);
        }
      }
    };
    switch (reactions.getConstructor()) {
      case TdApi.ChatAvailableReactionsAll.CONSTRUCTOR: {
        String[] activeEmojiReactions = getActiveEmojiReactions();
        if (activeEmojiReactions == null || activeEmojiReactions.length == 0) {
          if (after != null) {
            after.runWithBool(false);
          }
          return;
        }
        int requestedCount = 0;
        remaining.set(activeEmojiReactions.length);
        for (String activeEmojiReaction : activeEmojiReactions) {
          TdlibEmojiReactionsManager.Entry entry = reactions().findOrPostponeRequest(activeEmojiReaction, emojiReactionWatcher, true);
          if (entry != null) {
            remaining.decrementAndGet();
          } else {
            requestedCount++;
          }
        }
        if (requestedCount == 0) {
          if (after != null) {
            after.runWithBool(false);
          }
        } else {
          reactions().performPostponedRequests();
        }
        break;
      }
      case TdApi.ChatAvailableReactionsSome.CONSTRUCTOR: {
        TdApi.ChatAvailableReactionsSome some = (TdApi.ChatAvailableReactionsSome) reactions;
        if (some.reactions.length == 0) {
          if (after != null) {
            after.runWithBool(false);
          }
          return;
        }
        remaining.set(some.reactions.length);
        int requestedEmojiReactionCount = 0;
        int requestedCustomReactionCount = 0;
        for (TdApi.ReactionType reactionType : some.reactions) {
          switch (reactionType.getConstructor()) {
            case TdApi.ReactionTypeEmoji.CONSTRUCTOR: {
              TdApi.ReactionTypeEmoji emoji = (TdApi.ReactionTypeEmoji) reactionType;
              TdlibEmojiReactionsManager.Entry entry = reactions().findOrPostponeRequest(emoji.emoji, emojiReactionWatcher, true);
              if (entry != null) {
                remaining.decrementAndGet();
              } else {
                requestedEmojiReactionCount++;
              }
              break;
            }
            case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR: {
              TdApi.ReactionTypeCustomEmoji customEmoji = (TdApi.ReactionTypeCustomEmoji) reactionType;
              TdlibEmojiManager.Entry entry = emoji().findOrPostponeRequest(customEmoji.customEmojiId, customReactionWatcher, true);
              if (entry != null) {
                remaining.decrementAndGet();
              } else {
                requestedCustomReactionCount++;
              }
              break;
            }
          }
        }
        if (requestedEmojiReactionCount == 0 && requestedCustomReactionCount == 0) {
          if (after != null) {
            after.runWithBool(false);
          }
        } else {
          if (requestedEmojiReactionCount > 0) {
            reactions().performPostponedRequests();
          }
          if (requestedCustomReactionCount > 0) {
            emoji().performPostponedRequests();
          }
        }
        break;
      }
    }
  }

  public void pickRandomGenericOverlaySticker (RunnableData<TdApi.Sticker> after) {
    genericReactionEffects.get(stickers -> {
      if (stickers != null && stickers.stickers.length > 0) {
        TdApi.Sticker sticker = stickers.stickers[MathUtils.random(0, stickers.stickers.length - 1)];
        after.runWithData(sticker);
      } else {
        after.runWithData(null);
      }
    });
  }

  public TGReaction getReaction (@Nullable TdApi.ReactionType reactionType) {
    return getReaction(reactionType, true);
  }

  @Nullable
  public TGReaction getReaction (@Nullable TdApi.ReactionType reactionType, boolean allowRequest) {
    if (reactionType == null) {
      return null;
    }
    String key = TD.makeReactionKey(reactionType);
    synchronized (dataLock) {
      TGReaction reaction = cachedReactions.get(key);
      if (reaction != null) {
        return reaction;
      }
    }
    switch (reactionType.getConstructor()) {
      case TdApi.ReactionTypeEmoji.CONSTRUCTOR: {
        TdApi.ReactionTypeEmoji emoji = (TdApi.ReactionTypeEmoji) reactionType;
        RunnableData<TdlibEmojiReactionsManager.Entry> emojiReactionWatcher = (newEntry) -> {
          if (newEntry.value != null) {
            TGReaction reaction = new TGReaction(this, newEntry.value);
            synchronized (dataLock) {
              cachedReactions.put(key, reaction);
            }
            listeners().notifyReactionLoaded(key);
          }
        };
        TdlibEmojiReactionsManager.Entry entry;
        if (allowRequest) {
          entry = reactions().findOrRequest(emoji.emoji, emojiReactionWatcher);
        } else {
          entry = reactions().find(emoji.emoji);
        }
        if (entry != null) {
          if (entry.value == null) {
            return null;
          }
          TGReaction reaction = new TGReaction(this, entry.value);
          synchronized (dataLock) {
            cachedReactions.put(key, reaction);
          }
          return reaction;
        }
        break;
      }
      case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR: {
        TdApi.ReactionTypeCustomEmoji customEmoji = (TdApi.ReactionTypeCustomEmoji) reactionType;
        RunnableData<TdlibEmojiManager.Entry> customReactionWatcher = (newEntry) -> {
          if (newEntry.value != null) {
            TGReaction reaction = new TGReaction(this, newEntry.value);
            synchronized (dataLock) {
              cachedReactions.put(key, reaction);
            }
            listeners().notifyReactionLoaded(key);
          }
        };
        TdlibEmojiManager.Entry entry;
        if (allowRequest) {
          entry = emoji().findOrRequest(customEmoji.customEmojiId, customReactionWatcher);
        } else {
          entry = emoji().find(customEmoji.customEmojiId);
        }
        if (entry != null) {
          if (entry.value == null) {
            return null;
          }
          TGReaction reaction = new TGReaction(this, entry.value);
          synchronized (dataLock) {
            cachedReactions.put(key, reaction);
          }
          return reaction;
        }
        break;
      }
    }
    return null;
  }

  public boolean shouldSendAsDice (TdApi.FormattedText text) {
    return getDiceEmoji(text) != null;
  }

  public boolean isDiceEmoji (String text) {
    if (StringUtils.isEmpty(text))
      return false;
    synchronized (dataLock) {
      if (diceEmoji != null) {
        return ArrayUtils.contains(diceEmoji, text);
      }
    }
    return false;
  }

  // Chat open/close

  private final LongSparseArray<ArrayList<ViewController<?>>> openedChats = new LongSparseArray<>(8);
  private final LongSparseIntArray openedChatsTimes = new LongSparseIntArray();
  private final Object chatOpenMutex = new Object();

  public void openChat (long chatId, @Nullable ViewController<?> controller) {
    openChat(chatId, controller, null);
  }

  public void openChat (long chatId, @Nullable ViewController<?> controller, @Nullable Runnable after) {
    Client.ResultHandler okHandler = okHandler();
    synchronized (chatOpenMutex) {
      ArrayList<ViewController<?>> controllers = openedChats.get(chatId);
      if (controllers == null) {
        controllers = new ArrayList<>();
        controllers.add(controller);
        openedChats.put(chatId, controllers);
      } else {
        controllers.add(controller);
      }
      if (controllers.size() == 1) {
        openedChatsTimes.put(chatId, (int) (System.currentTimeMillis() / 1000l));
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.v(Log.TAG_MESSAGES_LOADER, "openChat, chatId=%d", chatId);
        }
        client().send(new TdApi.OpenChat(chatId), after != null ? result -> {
          okHandler.onResult(result);
          after.run();
        } : okHandler);
      } else if (after != null) {
        client().send(new TdApi.SetAlarm(0), result ->
          after.run()
        );
      }
    }
    notifications().onChatOpened(chatId);
  }

  public boolean isChatOpen (final long chatId) {
    synchronized (chatOpenMutex) {
      return openedChatsTimes.get(chatId) != 0;
    }
  }

  public void closeChat (final long chatId, final ViewController<?> controller, boolean needDelay) {
    if (needDelay) {
      ui().postDelayed(() -> closeChatImpl(chatId, controller), 1000);
    } else {
      closeChatImpl(chatId, controller);
    }
  }

  private void closeChatImpl (long chatId, ViewController<?> controller) {
    synchronized (chatOpenMutex) {
      ArrayList<ViewController<?>> controllers = openedChats.get(chatId);
      if (controllers != null && controllers.remove(controller) && controllers.isEmpty()) {
        openedChatsTimes.delete(chatId);
        openedChats.remove(chatId);
        if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
          Log.v(Log.TAG_MESSAGES_LOADER, "closeChat, chatId=%d", chatId);
        }
        client().send(new TdApi.CloseChat(chatId), okHandler());
      }
    }
  }

  public void onScreenshotTaken (int timeSeconds) {
   /* synchronized (chatOpenMutex) {
      final int size = openedChatsTimes.size();
      for (int i = 0; i < size; i++) {
        final int openTime = openedChatsTimes.valueAt(i);
        if (timeSeconds >= openTime) {
          final long chatId = openedChatsTimes.keyAt(i);
          TdApi.Chat chat = chat(chatId);
          if ((ChatId.isSecret(chatId) || ui().shouldSendScreenshotHint(chat))) {
            sendScreenshotMessage(chatId, null);
          }
        }
      }
    }*/
    ui().execute(() ->
      messageViewer.onScreenshotTaken(timeSeconds)
    );
  }

  public boolean hasPotentiallyVisibleMessages () {
    return (openedChats != null && openedChats.size() > 0) || messageViewer.hasPotentiallyVisibleMessages();
  }

  // Metadata

  public String emailMetadata () {
    return Lang.getString(R.string.email_metadata,
      BuildConfig.VERSION_NAME,
      languagePackId,
      TdlibManager.getSystemLanguageCode(),
      TdlibManager.getSystemVersion());
  }

  // Notificaitons

  public int accountColor (long chatId) {
    return getColor(ChatId.isSecret(chatId) ? ColorId.notificationSecure : ColorId.notification);
  }

  public int accountColor () {
    return getColor(ColorId.notification);
  }

  public int accountPlayerColor () {
    return getColor(ColorId.notificationPlayer);
  }

  public int getColor (@ColorId int colorId) {
    int colorTheme = settings().globalTheme();
    return Theme.getColor(colorId, colorTheme);
  }

  public String accountShortName () {
    return context.account(accountId).getShortName();
  }

  public String accountShortName (int category) {
    String name = accountShortName();
    return category != TdlibNotificationGroup.CATEGORY_DEFAULT ? Lang.getString(R.string.format_accountAndCategory, name, Lang.getNotificationCategory(category)) : name;
  }

  public String accountLongName () {
    return context.account(accountId).getLongName();
  }

  public String accountName () {
    return context.account(accountId).getName();
  }

  // Actions

  public void sendScreenshotMessage (long chatId, long[] messageIds) {
    client().send(new TdApi.ViewMessages(chatId, messageIds, new TdApi.MessageSourceScreenshot(), false), messageHandler());
  }

  public void sendMessage (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.Animation animation) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageAnimation(new TdApi.InputFileId(animation.animation.id), null, null, animation.duration, animation.width, animation.height, null, false);
    sendMessage(chatId, messageThreadId, replyTo, options, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.Audio audio) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageAudio(new TdApi.InputFileId(audio.audio.id), null, audio.duration, audio.title, audio.performer, null);
    sendMessage(chatId, messageThreadId, replyTo, options, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.Sticker sticker, @Nullable String emoji) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageSticker(new TdApi.InputFileId(sticker.sticker.id), null, 0, 0, emoji);
    sendMessage(chatId, messageThreadId, replyTo, options, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.InputMessageContent inputMessageContent) {
    sendMessage(chatId, messageThreadId, replyTo, options, inputMessageContent, null);
  }

  public void sendMessage (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, TdApi.InputMessageContent inputMessageContent, @Nullable RunnableData<TdApi.Message> after) {
    client().send(new TdApi.SendMessage(chatId, messageThreadId, replyTo, options, null, inputMessageContent), after != null ? result -> {
      messageHandler.onResult(result);
      after.runWithData(result instanceof TdApi.Message ? (TdApi.Message) result : null);
    } : messageHandler());
  }

  public void resendMessages (long chatId, long[] messageIds) {
    client().send(new TdApi.ResendMessages(chatId, messageIds, null), messageHandler());
  }

  private final HashMap<String, TdApi.MessageContent> pendingMessageTexts = new HashMap<>();
  private final HashMap<String, TdApi.FormattedText> pendingMessageCaptions = new HashMap<>();

  public void editMessageText (long chatId, long messageId, TdApi.InputMessageText content, @Nullable TdApi.WebPage webPage) {
    if (content.linkPreviewOptions != null && content.linkPreviewOptions.isDisabled) {
      webPage = null;
    }
    TD.parseEntities(content.text);
    TdApi.MessageText messageText = new TdApi.MessageText(content.text, webPage, content.linkPreviewOptions);
    if (!Emoji.instance().isSingleEmoji(content.text)) {
      performEdit(chatId, messageId, messageText, new TdApi.EditMessageText(chatId, messageId, null, content), pendingMessageTexts);
      return;
    }
    long customEmojiId = 0;
    if (content.text.entities != null) {
      for (TdApi.TextEntity entity : content.text.entities) {
        if (Td.isCustomEmoji(entity.type)) {
          customEmojiId = ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId;
          break;
        }
      }
    }
    Runnable animatedEmojiFallback = () -> {
      client().send(new TdApi.GetAnimatedEmoji(content.text.text), result -> {
        if (result.getConstructor() == TdApi.AnimatedEmoji.CONSTRUCTOR) {
          TdApi.MessageAnimatedEmoji animatedEmoji = new TdApi.MessageAnimatedEmoji((TdApi.AnimatedEmoji) result, content.text.text);
          performEdit(chatId, messageId, animatedEmoji, new TdApi.EditMessageText(chatId, messageId, null, content), pendingMessageTexts);
        } else {
          performEdit(chatId, messageId, messageText, new TdApi.EditMessageText(chatId, messageId, null, content), pendingMessageTexts);
        }
      });
    };
    if (customEmojiId != 0) {
      emoji().findOrRequest(customEmojiId, entry -> {
        if (entry != null && !entry.isNotFound()) {
          TdApi.Sticker customEmojiSticker = entry.value;
          TdApi.MessageAnimatedEmoji animatedEmoji = new TdApi.MessageAnimatedEmoji(new TdApi.AnimatedEmoji(customEmojiSticker, customEmojiSticker.width, customEmojiSticker.height, 0, null), content.text.text);
          performEdit(chatId, messageId, animatedEmoji, new TdApi.EditMessageText(chatId, messageId, null, content), pendingMessageTexts);
        } else {
          animatedEmojiFallback.run();
        }
      });
    } else {
      animatedEmojiFallback.run();
    }
  }

  public void editMessageCaption (long chatId, long messageId, TdApi.FormattedText caption) {
    TD.parseEntities(caption);
    performEdit(chatId, messageId, caption, new TdApi.EditMessageCaption(chatId, messageId, null, caption), pendingMessageCaptions);
  }

  public TdApi.FormattedText getFormattedText (TdApi.Message message) {
    if (message == null)
      return null;
    TdApi.FormattedText pendingText = getPendingFormattedText(message.chatId, message.id);
    if (pendingText != null)
      return pendingText;
    return Td.textOrCaption(message.content);
  }

  public TdApi.FormattedText getPendingFormattedText (long chatId, long messageId) {
    TdApi.MessageContent messageText = getPendingMessageText(chatId, messageId);
    if (messageText != null) {
      //noinspection SwitchIntDef
      switch (messageText.getConstructor()) {
        case TdApi.MessageText.CONSTRUCTOR:
          return ((TdApi.MessageText) messageText).text;
        case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
          return Td.textOrCaption(messageText);
      }
      Td.assertMessageContent_d40af239();
      throw Td.unsupported(messageText);
    }
    return getPendingMessageCaption(chatId, messageId);
  }

  public TdApi.MessageContent getPendingMessageText (long chatId, long messageId) {
    synchronized (pendingMessageTexts) {
      return pendingMessageTexts.get(chatId + "_" + messageId);
    }
  }

  public TdApi.FormattedText getPendingMessageCaption (long chatId, long messageId) {
    synchronized (pendingMessageCaptions) {
      return pendingMessageCaptions.get(chatId + "_" + messageId);
    }
  }

  private <T extends TdApi.Object> void performEdit (long chatId, long messageId, T pendingData, TdApi.Function<?> function, Map<String, T> map) {
    final String key = chatId + "_" + messageId;
    synchronized (map) {
      map.put(key, pendingData);
    }
    listeners.updateMessagePendingContentChanged(chatId, messageId);
    client().send(function, result -> {
      if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.showError(result);
      }
      tdlib().ui().post(() -> {
        T currentData = map.get(key);
        if (currentData == pendingData) {
          synchronized (map) {
            map.remove(key);
          }
          listeners.updateMessagePendingContentChanged(chatId, messageId);
        }
      });
    });
  }

  private Map<String, Queue<Runnable>> messageCallbacks;

  public void awaitMessageSent (TdApi.Message message, Runnable callback) {
    if (message.sendingState == null || message.sendingState.getConstructor() != TdApi.MessageSendingStatePending.CONSTRUCTOR || qack().isMessageAcknowledged(message.chatId, message.id)) {
      callback.run();
    } else {
      synchronized (dataLock) {
        if (messageCallbacks == null)
          messageCallbacks = new HashMap<>();
        String key = message.chatId + "_" + message.id;
        Queue<Runnable> callbacks = messageCallbacks.get(key);
        if (callbacks == null) {
          callbacks = new ArrayDeque<>();
          messageCallbacks.put(key, callbacks);
        }
        callbacks.add(callback);
      }
    }
  }

  private void notifyMessageSendCallbacks (long chatId, long messageId) {
    Queue<Runnable> callbacks;
    synchronized (dataLock) {
      if (messageCallbacks == null)
        return;
      callbacks = messageCallbacks.remove(chatId + "_" + messageId);
    }
    if (callbacks != null) {
      for (Runnable callback : callbacks) {
        callback.run();
      }
    }
  }

  public static final int TESTER_LEVEL_NONE = 0;
  public static final int TESTER_LEVEL_READER = 1;
  public static final int TESTER_LEVEL_TESTER = 2;
  public static final int TESTER_LEVEL_ADMIN = 3;
  public static final int TESTER_LEVEL_DEVELOPER = 4;
  public static final int TESTER_LEVEL_CREATOR = 5;

  public static final int TGX_CREATOR_USER_ID = 163957826;
  public static final int TDLIB_CREATOR_USER_ID = 7736885;

  public static final long ADMIN_CHAT_ID = ChatId.fromSupergroupId(1112283549); // TGX Alpha and Admins
  public static final long TESTER_CHAT_ID = ChatId.fromSupergroupId(1336679475); // Telegram X Android: t.me/tgandroidtests
  public static final long READER_CHAT_ID = ChatId.fromSupergroupId(1136101327); // Telegram X: t.me/tgx_android
  public static final long CLOUD_RESOURCES_CHAT_ID = ChatId.fromSupergroupId(1247387696); // Telegram X: Resources
  public static final long TRENDING_STICKERS_CHAT_ID = ChatId.fromSupergroupId(1140222267); // Trending Stickers: t.me/TrendingStickers

  public boolean isRedTeam (long chatId) {
    if (isDebugInstance()) {
      return false;
    }
    long supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0)
      return false;
    return supergroupId == 1084287520 || supergroupId == 1266791237 ||
      supergroupId == 1492016544 || supergroupId == 1227585106 || supergroupId == 1116030833;
  }

  public void getTesterLevel (@NonNull RunnableInt callback) {
    getTesterLevel(callback, false);
  }

  public void getTesterLevel (@NonNull RunnableInt callback, boolean onlyLocal) {
    if (inRecoveryMode() || isDebugInstance()) {
      callback.runWithInt(TESTER_LEVEL_TESTER);
      return;
    }
    long myUserId = myUserId();
    if (myUserId == TGX_CREATOR_USER_ID) {
      callback.runWithInt(TESTER_LEVEL_CREATOR);
    } else if (myUserId == TDLIB_CREATOR_USER_ID) {
      callback.runWithInt(TESTER_LEVEL_DEVELOPER);
    } else if (onlyLocal) {
      TdApi.Chat tgxAdminChat = chat(ADMIN_CHAT_ID);
      if (tgxAdminChat != null && TD.isMember(chatStatus(ADMIN_CHAT_ID))) {
        callback.runWithInt(TESTER_LEVEL_ADMIN);
        return;
      }
      TdApi.Chat tgxTestersChat = chat(TESTER_CHAT_ID);
      if (tgxTestersChat != null && TD.isMember(chatStatus(TESTER_CHAT_ID))) {
        callback.runWithInt(TESTER_LEVEL_TESTER);
        return;
      }
      TdApi.Chat tgxReadersChat = chat(READER_CHAT_ID);
      if (tgxReadersChat != null && TD.isMember(chatStatus(READER_CHAT_ID))) {
        callback.runWithInt(TESTER_LEVEL_READER);
        return;
      }
      callback.runWithInt(TESTER_LEVEL_NONE);
    } else {
      chat(ADMIN_CHAT_ID, tgxAdminChat -> {
        if (tgxAdminChat != null && TD.isMember(chatStatus(ADMIN_CHAT_ID))) {
          callback.runWithInt(TESTER_LEVEL_ADMIN);
        } else {
          chat(TESTER_CHAT_ID, tgxTestersChat -> {
            if (tgxTestersChat != null && TD.isMember(chatStatus(TESTER_CHAT_ID))) {
              callback.runWithInt(TESTER_LEVEL_TESTER);
            } else {
              chat(READER_CHAT_ID, tgxReadersChat -> {
                if (tgxReadersChat != null && TD.isMember(chatStatus(READER_CHAT_ID))) {
                  callback.runWithInt(TESTER_LEVEL_READER);
                } else {
                  callback.runWithInt(TESTER_LEVEL_NONE);
                }
              });
            }
          });
        }
      });
    }
  }

  public void forwardMessage (long chatId, long messageThreadId, long fromChatId, long messageId, TdApi.MessageSendOptions options) {
    client().send(new TdApi.ForwardMessages(chatId, messageThreadId, fromChatId, new long[] {messageId}, options, false, false), messageHandler());
  }

  public void sendInlineQueryResult (long chatId, long messageThreadId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions options, long queryId, String resultId) {
    client().send(new TdApi.SendInlineQueryResultMessage(chatId, messageThreadId, replyTo, options, queryId, resultId, false), messageHandler());
  }

  public void sendBotStartMessage (long botUserId, long chatId, String parameter) {
    client().send(new TdApi.SendBotStartMessage(botUserId, chatId, parameter), messageHandler());
  }

  public void setChatMessageAutoDeleteTime (long chatId, int ttl) {
    client().send(new TdApi.SetChatMessageAutoDeleteTime(chatId, ttl), okHandler());
  }

  public void getPrimaryChatInviteLink (long chatId, Tdlib.ResultHandler<TdApi.ChatInviteLink> handler) {
    Client.ResultHandler linkHandler = new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object result) {
        TdApi.ChatInviteLink inviteLink;
        switch (result.getConstructor()) {
          case TdApi.BasicGroupFullInfo.CONSTRUCTOR: {
            inviteLink = ((TdApi.BasicGroupFullInfo) result).inviteLink;
            break;
          }
          case TdApi.SupergroupFullInfo.CONSTRUCTOR: {
            inviteLink = ((TdApi.SupergroupFullInfo) result).inviteLink;
            break;
          }
          case TdApi.ChatInviteLink.CONSTRUCTOR:
            handler.onResult((TdApi.ChatInviteLink) result, null);
            return;
          case TdApi.Error.CONSTRUCTOR:
            handler.onResult(null, (TdApi.Error) result);
            return;
          default:
            throw new UnsupportedOperationException(result.toString());
        }
        if (inviteLink != null) {
          handler.onResult(inviteLink, null);
        } else {
          client().send(new TdApi.ReplacePrimaryChatInviteLink(chatId), this);
        }
      }
    };
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        client().send(new TdApi.GetBasicGroupFullInfo(ChatId.toBasicGroupId(chatId)), linkHandler);
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        client().send(new TdApi.GetSupergroupFullInfo(ChatId.toSupergroupId(chatId)), linkHandler);
        break;
      }
      default: {
        handler.onResult(null, new TdApi.Error(-1, "Invalid chat type"));
        break;
      }
    }
  }

  @TdlibThread
  public void traceInviteLink (TdApi.ChatInviteLinkInfo inviteLinkInfo) {
    if (inviteLinkInfo.chatId == 0)
      return;
    synchronized (dataLock) {
      if (inviteLinkInfo.isPublic || inviteLinkInfo.accessibleFor == 0) {
        accessibleChatTimers.delete(inviteLinkInfo.chatId);
      } else {
        accessibleChatTimers.put(inviteLinkInfo.chatId, SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(inviteLinkInfo.accessibleFor));
      }
    }
  }

  public boolean isPublicChat (long chatId) {
    if (chatId == 0)
      return false;
    TdApi.Chat chat = chat(chatId);
    if (chat == null)
      return false;
    if (!StringUtils.isEmpty(chatUsername(chat)))
      return true;
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && (supergroup.hasLinkedChat || supergroup.hasLocation);
  }

  public boolean isTemporaryAccessible (long chatId) {
    synchronized (dataLock) {
      long openUntil = accessibleChatTimers.get(chatId);
      if (openUntil > SystemClock.elapsedRealtime())
        return true;
      if (openUntil != 0)
        accessibleChatTimers.delete(chatId);
      return false;
    }
  }

  public int chatAccessState (long chatId) {
    return chatId != 0 ? chatAccessState(chat(chatId)) : CHAT_ACCESS_FAIL;
  }

  public int chatAccessState (TdApi.Chat chat) {
    if (chat == null) {
      return CHAT_ACCESS_FAIL;
    }
    if (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
      TdApi.Supergroup supergroup = chatToSupergroup(chat.id);
      if (supergroup == null) {
        return CHAT_ACCESS_FAIL;
      }
      boolean isPublic = Td.hasUsername(supergroup) || supergroup.hasLinkedChat || supergroup.hasLocation;
      boolean isTemporary = isTemporaryAccessible(chat.id);
      switch (supergroup.status.getConstructor()) {
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
          return isPublic ? CHAT_ACCESS_OK : isTemporary ? CHAT_ACCESS_TEMPORARY : CHAT_ACCESS_PRIVATE;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          return (isPublic || ((TdApi.ChatMemberStatusRestricted) supergroup.status).isMember) ? CHAT_ACCESS_OK : isTemporary ? CHAT_ACCESS_TEMPORARY : CHAT_ACCESS_PRIVATE;
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          return CHAT_ACCESS_BANNED;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          return CHAT_ACCESS_OK;
      }
    }
    return CHAT_ACCESS_OK;
  }

  public void blockSender (TdApi.MessageSender sender, @Nullable TdApi.BlockList blockList, Client.ResultHandler handler) {
    client().send(new TdApi.SetMessageSenderBlockList(sender, blockList), handler);
  }

  public void unblockSender (TdApi.MessageSender sender, Client.ResultHandler handler) {
    blockSender(sender, null, handler);
  }

  public void setScopeNotificationSettings (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    client().send(new TdApi.SetScopeNotificationSettings(scope, settings), okHandler());
  }

  public TdApi.ScopeNotificationSettings scopeNotificationSettings (long chatId) {
    return notificationManager.getScopeNotificationSettings(chatId);
  }

  public TdApi.ScopeNotificationSettings scopeNotificationSettings (TdApi.Chat chat) {
    return notificationManager.getScopeNotificationSettings(chat);
  }

  public TdApi.ScopeNotificationSettings scopeNotificationSettings (TdApi.NotificationSettingsScope scope) {
    return notificationManager.getScopeNotificationSettings(scope);
  }

  public void setChatNotificationSettings (long chatId, TdApi.ChatNotificationSettings settings) {
    client().send(new TdApi.SetChatNotificationSettings(chatId, settings), okHandler());
  }

  public void setMuteForSync (long chatId, int muteFor) {
    TdApi.Chat chat = chatSync(chatId);
    if (chat == null)
      throw new NullPointerException();
    TdApi.NotificationSettingsScope scope = notifications().scope(chat);
    TdApi.ScopeNotificationSettings settings = scopeNotificationSettings(scope);
    if (settings == null) {
      TdApi.Object result = clientExecute(new TdApi.GetScopeNotificationSettings(scope), 0);
      if (result instanceof TdApi.ScopeNotificationSettings)
        settings = (TdApi.ScopeNotificationSettings) result;
    }
    if (settings == null)
      throw new NullPointerException();
    TdApi.ChatNotificationSettings chatSettings = chat.notificationSettings;
    chatSettings.muteFor = muteFor;
    chatSettings.useDefaultMuteFor = (muteFor == 0 && settings.muteFor == 0); // || (TD.isMutedForever(muteFor) && TD.isMutedForever(settings.muteFor));
    setChatNotificationSettings(chatId, chatSettings);
  }

  public void setMuteFor (long chatId, int muteFor) {
    TdApi.ScopeNotificationSettings settings = scopeNotificationSettings(chatId);
    TdApi.ChatNotificationSettings chatSettings = chatSettings(chatId);
    if (settings == null)
      throw new NullPointerException();
    if (chatSettings == null)
      throw new NullPointerException();
    chatSettings.muteFor = muteFor;
    chatSettings.useDefaultMuteFor = (muteFor == 0 && settings.muteFor == 0); // || (TD.isMutedForever(muteFor) && TD.isMutedForever(settings.muteFor));
    setChatNotificationSettings(chatId, chatSettings);
  }

  public void setScopeMuteFor (TdApi.NotificationSettingsScope scope, int muteFor) {
    TdApi.ScopeNotificationSettings settings = scopeNotificationSettings(scope);
    if (settings != null) {
      settings.muteFor = muteFor;
      setScopeNotificationSettings(scope, settings);
    } else {
      client().send(new TdApi.GetScopeNotificationSettings(scope), result -> {
        if (result.getConstructor() == TdApi.ScopeNotificationSettings.CONSTRUCTOR) {
          TdApi.ScopeNotificationSettings scopeSettings = (TdApi.ScopeNotificationSettings) result;
          scopeSettings.muteFor = muteFor;
          setScopeNotificationSettings(scope, scopeSettings);
        }
      });
    }
  }

  public void setChatPermissions (long chatId, TdApi.ChatPermissions permissions, RunnableBool callback) {
    client().send(new TdApi.SetChatPermissions(chatId, permissions), result -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          callback.runWithBool(true);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          callback.runWithBool(false);
          break;
      }
    });
  }

  public boolean canRevokeChat (long chatId) {
    return ChatId.isPrivate(chatId) && !isSelfChat(chatId) && !isBotChat(chatId);
  }

  public void deleteChat (long chatId, boolean revoke, @Nullable Runnable after) {
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        client().send(new TdApi.DeleteChatHistory(chatId, true, revoke), okHandler(after));
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        TdApi.SecretChat secretChat = chatToSecretChat(chatId);
        if (secretChat == null || secretChat.state.getConstructor() == TdApi.SecretChatStateClosed.CONSTRUCTOR) {
          client().send(new TdApi.DeleteChatHistory(chatId, true, revoke), silentHandler(after));
        } else {
          client().send(new TdApi.CloseSecretChat(ChatId.toSecretChatId(chatId)), result -> {
            if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
              Log.e("Cannot close secret chat, secretChatId:%d, error: %s", ChatId.toSecretChatId(chatId), TD.toErrorString(result));
            }
            client().send(new TdApi.DeleteChatHistory(chatId, true, revoke), silentHandler(after));
          });
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        TdApi.ChatMemberStatus status = chatStatus(chatId);
        if (!TD.isMember(status, false)) {
          client().send(new TdApi.DeleteChatHistory(chatId, true, revoke), okHandler(after));
        } else {
          client().send(new TdApi.SetChatMemberStatus(chatId, mySender(), new TdApi.ChatMemberStatusLeft()), result -> {
            if (ChatId.isBasicGroup(chatId)) {
              client().send(new TdApi.DeleteChatHistory(chatId, true, revoke), okHandler(after));
            }
          });
        }
        break;
      }
    }
  }

  private void setChatMemberStatusImpl (final long chatId, final TdApi.MessageSender sender, final TdApi.ChatMemberStatus newStatus, final int forwardLimit, final @Nullable TdApi.ChatMemberStatus currentStatus, @Nullable final ChatMemberStatusChangeCallback callback) {
    final boolean needForward = ChatId.isBasicGroup(chatId) && forwardLimit > 0 && !TD.isMember(currentStatus, false) && TD.isMember(newStatus, false) && sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR;
    final AtomicBoolean oneShot = (needForward && TD.isAdmin(newStatus)) ? new AtomicBoolean(false) : null;

    TdApi.Function<?> function;
    if (needForward) {
      function = new TdApi.AddChatMember(chatId, ((TdApi.MessageSenderUser) sender).userId, forwardLimit);
    } else {
      function = new TdApi.SetChatMemberStatus(chatId, sender, newStatus);
    }

    final AtomicReference<TdApi.Error> error = new AtomicReference<>();
    final AtomicInteger retryCount = new AtomicInteger(0);
    client().send(function, new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            if (oneShot != null && !oneShot.getAndSet(true)) {
              client().send(new TdApi.SetChatMemberStatus(chatId, sender, newStatus), this);
            } else {
              client().send(new TdApi.GetChatMember(chatId, sender), this);
            }
            return;
          case TdApi.ChatMember.CONSTRUCTOR: {
            final TdApi.ChatMember newMember = (TdApi.ChatMember) object;
            if (error.get() == null && !Td.equalsTo(newStatus, newMember.status) && retryCount.incrementAndGet() <= 3) {
              client().send(new TdApi.SetAlarm(.5 + .5 * retryCount.get()), this);
            } else {
              cache().onChatMemberStatusChanged(chatId, newMember);
              if (callback != null) {
                TdApi.Error result = error.get();
                callback.onMemberStatusUpdated(result == null, result);
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR:
            final TdApi.Error originalError = error.getAndSet((TdApi.Error) object);
            if (originalError == null) {
              client().send(new TdApi.GetChatMember(chatId, sender), this);
            } else if (callback != null) {
              callback.onMemberStatusUpdated(false, originalError);
            } else {
              UI.showError(originalError);
            }
            break;
        }
      }
    });
  }

  public void terminateSession (TdApi.Session session, RunnableData<TdApi.Error> after) {
    client().send(new TdApi.TerminateSession(session.id), result -> {
      after.runWithData(result.getConstructor() == TdApi.Error.CONSTRUCTOR ? (TdApi.Error) result : null);
      if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        this.sessionsInfo = null;
        listeners.notifySessionTerminated(session);
      }
    });
  }

  public void terminateAllOtherSessions (TdApi.Session currentSession, RunnableData<TdApi.Error> after) {
    client().send(new TdApi.TerminateAllOtherSessions(), result -> {
      if (after != null) {
        after.runWithData(result.getConstructor() == TdApi.Error.CONSTRUCTOR ? (TdApi.Error) result : null);
      }
      if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        this.sessionsInfo = null;
        listeners.notifyAllSessionsTerminated(currentSession);
      }
    });
  }

  public void setInactiveSessionTtl (int ttlDays, @Nullable RunnableData<TdApi.Error> after) {
    client().send(new TdApi.SetInactiveSessionTtl(ttlDays), result -> {
      if (after != null) {
        after.runWithData(result.getConstructor() == TdApi.Error.CONSTRUCTOR ? (TdApi.Error) result : null);
      }
      if (result.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        this.sessionsInfo = null;
        listeners.notifyInactiveSessionTtlChanged(ttlDays);
      }
    });
  }

  public static class SessionsInfo {
    public final TdApi.Session[] allSessions, otherActiveSessions, incompleteLoginAttempts;
    public final TdApi.Session currentSession;
    public final boolean onlyCurrent;

    public final int otherDevicesCount;
    public final int sessionCountOnCurrentDevice;
    public final int activeSessionCount;

    public final int inactiveSessionTtlDays;

    public SessionsInfo (TdApi.Sessions sessions) {
      this.inactiveSessionTtlDays = sessions.inactiveSessionTtlDays;

      Td.sort(sessions.sessions);

      TdApi.Session currentSession = null;
      List<TdApi.Session> pendingPasswords = null;
      List<TdApi.Session> otherSessions = null;
      Map<String, AtomicInteger> devicesMap = new HashMap<>();
      int activeSessionCount = 0;
      int totalSessionCount = 0;
      for (TdApi.Session session : sessions.sessions) {
        totalSessionCount++;
        if (session.isCurrent) {
          currentSession = session;
        } else if (session.isPasswordPending) {
          if (pendingPasswords == null) {
            pendingPasswords = new ArrayList<>();
          }
          pendingPasswords.add(session);
          continue;
        } else {
          if (otherSessions == null) {
            otherSessions = new ArrayList<>();
          }
          otherSessions.add(session);
        }

        final String signature = getSignature(session);
        final AtomicInteger counter = devicesMap.get(signature);
        if (counter != null) {
          counter.incrementAndGet();
        } else {
          devicesMap.put(signature, new AtomicInteger(1));
        }
        activeSessionCount++;
      }

      AtomicInteger currentDeviceCounter = currentSession != null ? devicesMap.remove(getSignature(currentSession)) : null;

      this.allSessions = sessions.sessions;
      this.currentSession = currentSession;
      this.sessionCountOnCurrentDevice = currentDeviceCounter != null ? currentDeviceCounter.get() : 1;
      this.otherDevicesCount = devicesMap.size();
      this.activeSessionCount = activeSessionCount;
      this.onlyCurrent = totalSessionCount == 1;
      this.incompleteLoginAttempts = pendingPasswords != null && !pendingPasswords.isEmpty() ? pendingPasswords.toArray(new TdApi.Session[0]) : new TdApi.Session[0];
      this.otherActiveSessions = otherSessions != null && !otherSessions.isEmpty() ? otherSessions.toArray(new TdApi.Session[0]) : new TdApi.Session[0];
    }

    private static String getSignature (TdApi.Session session) {
      return session.deviceModel + " " + session.platform + " " + session.systemVersion;
    }
  }

  private SessionsInfo sessionsInfo;

  @Nullable
  public TdApi.Session currentSession () {
    synchronized (dataLock) {
      return sessionsInfo.currentSession;
    }
  }

  public void getSessions (boolean allowCached, RunnableData<SessionsInfo> callback) {
    if (allowCached) {
      runOnTdlibThread(() -> {
        if (sessionsInfo != null) {
          if (callback != null) {
            callback.runWithData(sessionsInfo);
          }
        } else {
          getSessions(false, callback);
        }
      });
    } else {
      client().send(new TdApi.GetActiveSessions(), result -> {
        switch (result.getConstructor()) {
          case TdApi.Sessions.CONSTRUCTOR: {
            TdApi.Sessions sessions = (TdApi.Sessions) result;
            synchronized (dataLock) {
              this.sessionsInfo = new SessionsInfo(sessions);
            }
            if (callback != null) {
              callback.runWithData(this.sessionsInfo);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            Log.e("Unable to fetch sessions", TD.toErrorString(result));
            if (callback != null) {
              callback.runWithData(null);
            }
            break;
          }
        }
      });
    }
  }

  public void confirmQrCodeAuthentication (String qrLoginUri, RunnableData<TdApi.Session> onDone, RunnableData<TdApi.Error> onError) {
    client().send(new TdApi.ConfirmQrCodeAuthentication(qrLoginUri), result -> {
      switch (result.getConstructor()) {
        case TdApi.Session.CONSTRUCTOR: {
          TdApi.Session session = (TdApi.Session) result;
          if (onDone != null) {
            onDone.runWithData(session);
          }
          listeners.notifySessionCreatedViaQrCode(session);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (onError != null) {
            onError.runWithData((TdApi.Error) result);
          }
          break;
        }
      }
    });
  }

  public interface SupergroupUpgradeCallback {
    void onSupergroupUpgraded (final long fromChatId, final long toChatId, @Nullable TdApi.Error error);
  }

  public void upgradeToSupergroup (final long chatId, final SupergroupUpgradeCallback callback) {
    client().send(new TdApi.UpgradeBasicGroupChatToSupergroupChat(chatId), result -> {
      switch (result.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR: {
          long newChatId = ((TdApi.Chat) result).id;
          client().send(new TdApi.GetSupergroupFullInfo(ChatId.toSupergroupId(newChatId)), ignored -> {
            if (callback != null)
              callback.onSupergroupUpgraded(chatId, newChatId, ignored.getConstructor() == TdApi.Error.CONSTRUCTOR ? (TdApi.Error) ignored : null);
            else if (ignored.getConstructor() == TdApi.Error.CONSTRUCTOR)
              UI.showError(ignored);
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (callback != null)
            callback.onSupergroupUpgraded(chatId, chatId, (TdApi.Error) result);
          else
            UI.showError(result);
          break;
        }
      }
    });
  }

  public interface ChatMemberStatusChangeCallback {
    void onMemberStatusUpdated (boolean success, @Nullable TdApi.Error error);
  }

  private void refreshChatMemberStatus (final long chatId, final TdApi.MessageSender sender, final @TdApi.ChatMemberStatus.Constructors int expectedType, ChatMemberStatusChangeCallback callback) {
    final AtomicInteger retryCount = new AtomicInteger();
    final AtomicReference<TdApi.Error> error = new AtomicReference<>();
    client().send(new TdApi.GetChatMember(chatId, sender), new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            client().send(new TdApi.GetChatMember(chatId, sender), this);
            break;
          }
          case TdApi.ChatMember.CONSTRUCTOR: {
            TdApi.ChatMember member = (TdApi.ChatMember) object;
            if (member.status.getConstructor() != expectedType && retryCount.incrementAndGet() <= 3) {
              client().send(new TdApi.SetAlarm(.5 + .5 * retryCount.get()), this);
            } else {
              cache().onChatMemberStatusChanged(chatId, member);
              if (callback != null) {
                callback.onMemberStatusUpdated(member.status.getConstructor() == expectedType, error.get());
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            final TdApi.Error originalError = error.getAndSet((TdApi.Error) object);
            if (originalError == null) {
              client().send(new TdApi.GetChatMember(chatId, sender), this);
            } else if (callback != null) {
              callback.onMemberStatusUpdated(false, originalError);
            } else {
              UI.showError(originalError);
            }
            break;
          }
        }
      }
    });
  }

  public void transferOwnership (final long chatId, final long toUserId, final String password, ChatMemberStatusChangeCallback callback) {
    client().send(new TdApi.TransferChatOwnership(chatId, toUserId, password), result -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          ChatMemberStatusChangeCallback statusChangeCallback;
          if (callback != null) {
            final AtomicInteger remaining = new AtomicInteger(2);
            final AtomicBoolean hasFailures = new AtomicBoolean(false);
            final AtomicReference<TdApi.Error> anyError = new AtomicReference<>();
            statusChangeCallback = (success, error) -> {
              if (error != null) {
                anyError.set(error);
              }
              if (!success) {
                hasFailures.set(true);
              }
              if (remaining.decrementAndGet() == 0) {
                if (hasFailures.get()) {
                  callback.onMemberStatusUpdated(false, anyError.get());
                } else {
                  callback.onMemberStatusUpdated(true, null);
                }
              }
            };
          } else {
            statusChangeCallback = null;
          }
          refreshChatMemberStatus(chatId, new TdApi.MessageSenderUser(toUserId), TdApi.ChatMemberStatusCreator.CONSTRUCTOR, statusChangeCallback);
          refreshChatMemberStatus(chatId, new TdApi.MessageSenderUser(myUserId()), TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR, statusChangeCallback);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          if (callback != null) {
            callback.onMemberStatusUpdated(false, (TdApi.Error) result);
          } else {
            UI.showError(result);
          }
          break;
        }
      }
    });
  }

  public void setChatMemberStatus (final long chatId, final TdApi.MessageSender sender, final TdApi.ChatMemberStatus newStatus, final @Nullable TdApi.ChatMemberStatus currentStatus, @Nullable final ChatMemberStatusChangeCallback callback) {
    setChatMemberStatus(chatId, sender, newStatus, 0, currentStatus, callback);
  }

  public void setChatMemberStatus (final long chatId, final TdApi.MessageSender sender, final TdApi.ChatMemberStatus newStatus, final int forwardLimit, final @Nullable TdApi.ChatMemberStatus currentStatus, @Nullable final ChatMemberStatusChangeCallback callback) {
    if (ChatId.isBasicGroup(chatId) && TD.needUpgradeToSupergroup(newStatus)) {
      Runnable act = () -> upgradeToSupergroup(chatId, (oldChatId, newChatId, error) -> {
        if (newChatId != 0)
          setChatMemberStatusImpl(newChatId, sender, newStatus, 0, currentStatus, callback);
        else if (callback != null)
          callback.onMemberStatusUpdated(false, error);
      });
      if (forwardLimit > 0 && sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR &&
        TD.isMember(newStatus, false) && !TD.isMember(currentStatus, false)) {
        client().send(new TdApi.AddChatMember(chatId, ((TdApi.MessageSenderUser) sender).userId, forwardLimit), object -> {
          if (TD.isOk(object)) {
            act.run();
          } else if (callback != null) {
            ui().post(() ->
              callback.onMemberStatusUpdated(false, (TdApi.Error) object)
            );
          }
        });
      } else {
        act.run();
      }
    } else {
      setChatMemberStatusImpl(chatId, sender, newStatus, forwardLimit, currentStatus, callback);
    }
  }

  public void deleteMessages (long chatId, long[] messageIds, boolean revoke) {
    client().send(new TdApi.DeleteMessages(chatId, messageIds, revoke), okHandler());
  }

  public void deleteMessagesIfOk (final long chatId, final long[] messageIds, boolean revoke) {
    client().send(new TdApi.DeleteMessages(chatId, messageIds, revoke), okHandler());
  }

  public void readMessages (long chatId, long[] messageIds, TdApi.MessageSource source) {
    if (Log.isEnabled(Log.TAG_FCM)) {
      Log.i(Log.TAG_FCM, "Reading messages chatId:%d messageIds:%s", Log.generateSingleLineException(2), chatId, Arrays.toString(messageIds));
    }
    client().send(new TdApi.ViewMessages(chatId, messageIds, source, true), okHandler());
  }

  // TDLib config

  /*public boolean updateCustomLanguageCode (String languageCode) {
    if (this.languageCode.equals(languageCode)) {
      updateLanguageCode();
      return true;
    }
    return false;
  }*/

  public boolean setLanguage (@NonNull TdApi.LanguagePackInfo languagePackInfo) {
    return setLanguagePackIdImpl(languagePackInfo.id, true);
  }

  private boolean setLanguagePackIdImpl (@NonNull String languagePackId, boolean dispatch) {
    if (!StringUtils.equalsOrBothEmpty(languagePackId, languagePackId)) {
      this.languagePackId = languagePackId;
      if (dispatch) {
        updateLanguageParameters(client(), false);
      }
      return true;
    }
    return false;
  }

  public @Nullable String suggestedLanguagePackId () {
    return suggestedLanguagePackId;
  }

  public @Nullable TdApi.LanguagePackInfo suggestedLanguagePackInfo () {
    return suggestedLanguagePackInfo;
  }

  private void setSuggestedLanguagePackInfo (String languagePackId, @Nullable TdApi.LanguagePackInfo languagePackInfo) {
    if (StringUtils.equalsOrBothEmpty(this.suggestedLanguagePackId, languagePackId) && this.suggestedLanguagePackInfo == null) {
      this.suggestedLanguagePackInfo = languagePackInfo;
      listeners().updateSuggestedLanguageChanged(languagePackId, languagePackInfo);
    }
  }

  public void syncLanguage (@NonNull TdApi.LanguagePackInfo info, @Nullable RunnableBool callback) {
    if (StringUtils.isEmpty(info.baseLanguagePackId)) {
      syncLanguage(info.id, callback);
    } else {
      syncLanguage(info.id, success -> {
        if (success) {
          syncLanguage(info.baseLanguagePackId, callback);
        } else if (callback != null) {
          callback.runWithBool(false);
        }
      });
    }
  }

  private void syncLanguage (@NonNull String languagePackId, @Nullable RunnableBool callback) {
    send(new TdApi.SynchronizeLanguagePack(languagePackId), (ok, error) -> {
      boolean success;
      if (error != null) {
        Log.e("Unable to synchronize languagePackId %s: %s", languagePackId, TD.toErrorString(error));
        success = languagePackId.equals(Lang.getBuiltinLanguagePackId());
        if (!success) {
          success = Config.NEED_LANGUAGE_WORKAROUND;
          UI.showError(error);
          /*if (success = Config.NEED_LANGUAGE_WORKAROUND) {
            UI.showToast("Warning: language not synced. It's temporary issue of current beta version. " + TD.makeErrorString(result), Toast.LENGTH_LONG);
          } else {
            UI.showError(result);
          }*/
        }
      } else {
        Log.v("%s language is successfully synchronized", languagePackId);
        success = true;
      }
      if (callback != null) {
        callback.runWithBool(success);
      }
    });
  }

  private void getStrings (@NonNull String languagePackId, @NonNull String[] keys, @Nullable RunnableData<Map<String, TdApi.LanguagePackString>> callback) {
    client().send(new TdApi.GetLanguagePackStrings(languagePackId, keys), result -> {
      switch (result.getConstructor()) {
        case TdApi.LanguagePackStrings.CONSTRUCTOR: {
          if (callback != null) {
            TdApi.LanguagePackString[] strings = ((TdApi.LanguagePackStrings) result).strings;
            Map<String, TdApi.LanguagePackString> map = new HashMap<>(strings.length);
            for (TdApi.LanguagePackString string : strings) {
              if (string.value.getConstructor() != TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR)
                map.put(string.key, string);
            }
            callback.runWithData(map);
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR:
          Log.e("Failed to fetch %d strings: %s, languagePackId: %s", keys.length, TD.toErrorString(result), languagePackId);
          if (callback != null) {
            callback.runWithData(null);
          }
          break;
      }
    });
  }

  public void getStrings (@NonNull TdApi.LanguagePackInfo info, @NonNull String[] keys, @Nullable RunnableData<Map<String, TdApi.LanguagePackString>> callback) {
    if (StringUtils.isEmpty(info.baseLanguagePackId)) {
      getStrings(info.id, keys, callback);
    } else {
      getStrings(info.id, keys, result -> {
        Set<String> keySet = new HashSet<>(keys.length);
        Collections.addAll(keySet, keys);
        if (result != null)
          keySet.removeAll(result.keySet());
        if (!keySet.isEmpty()) {
          String[] missingKeys = new String[keySet.size()];
          keySet.toArray(missingKeys);
          getStrings(info.baseLanguagePackId, missingKeys, missingResult -> {
            if (callback != null) {
              if (result == null && missingResult == null) {
                callback.runWithData(null);
              } else if (result == null) {
                callback.runWithData(missingResult);
              } else {
                if (missingResult != null)
                  result.putAll(missingResult);
                callback.runWithData(result);
              }
            }
          });
        }
      });
    }
  }

  private void updateLanguageParameters (Client client, boolean isInitialization) {
    if (isInitialization) {
      this.languagePackId = Settings.instance().getLanguagePackInfo().id;
      client.send(new TdApi.SetOption("language_pack_database_path", new TdApi.OptionValueString(context.languageDatabasePath())), okHandler());
      client.send(new TdApi.SetOption("localization_target", new TdApi.OptionValueString(BuildConfig.LANGUAGE_PACK)), okHandler());
    }
    client.send(new TdApi.SetOption("language_pack_id", new TdApi.OptionValueString(languagePackId)), okHandler());
  }

  public void applyLanguage (TdApi.LanguagePackInfo languagePack, RunnableBool callback, boolean needSync) {
    Runnable act = () -> client().send(new TdApi.SetOption("language_pack_id", new TdApi.OptionValueString(languagePack.id)), result -> ui().post(() -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          Lang.changeLanguage(languagePack);
          if (callback != null)
            callback.runWithBool(true);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          if (callback != null)
            callback.runWithBool(false);
          break;
      }
    }));
    if (needSync && !TD.isLocalLanguagePackId(languagePack.id)) {
      syncLanguage(languagePack, success -> {
        if (success) {
          act.run();
        } else {
          if (callback != null) {
            ui().post(() -> callback.runWithBool(false));
          }
        }
      });
    } else {
      act.run();
    }
  }

  private void updateNotificationParameters (Client client) {
    final int notificationGroupCountMax, notificationGroupSizeMax;

    if (Config.FORCE_DISABLE_NOTIFICATIONS || isServiceInstance()) {
      // Disable Notifications API if we are running experimental build
      notificationGroupCountMax = 0;
      notificationGroupSizeMax = 1;
    } else {
      notificationGroupCountMax = 25;
      notificationGroupSizeMax = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? 7 : 10;
    }

    Client.ResultHandler okHandler = okHandler();
    client.send(new TdApi.SetOption("notification_group_count_max", new TdApi.OptionValueInteger(notificationGroupCountMax)), okHandler);
    client.send(new TdApi.SetOption("notification_group_size_max", new TdApi.OptionValueInteger(notificationGroupSizeMax)), okHandler);
  }

  private String lastReportedConnectionParams;

  public void checkConnectionParams () {
    checkConnectionParams(client(), false);
  }

  private TdApi.DeviceToken getRegisteredDeviceToken () {
    TdApi.DeviceToken deviceToken = context.getToken();
    return deviceToken == null ? Settings.instance().getDeviceToken() : deviceToken;
  }

  public String safetyNetApiKey () {
    // TODO: server config
    return BuildConfig.SAFETYNET_API_KEY;
  }

  public TdApi.PhoneNumberAuthenticationSettings phoneNumberAuthenticationSettings (Context context) {
    TdApi.FirebaseAuthenticationSettings firebaseAuthenticationSettings = null;
    String safetyNetApiKey = safetyNetApiKey();
    if (StringUtils.isEmpty(safetyNetApiKey)) {
      TDLib.Tag.safetyNet("Ignoring Firebase authentication, because SafetyNet API_KEY is unset");
    } else if (Config.REQUIRE_FIREBASE_SERVICES_FOR_SAFETYNET && !U.isGooglePlayServicesAvailable(context)) {
      TDLib.Tag.safetyNet("Ignoring Firebase authentication, because Firebase services are unavailable");
    } else {
      TDLib.Tag.safetyNet("Enabling Firebase authentication for the next request");
      firebaseAuthenticationSettings = new TdApi.FirebaseAuthenticationSettingsAndroid();
    }
    return new TdApi.PhoneNumberAuthenticationSettings(
      false, // TODO transparently request permission & enter flash call
      true,
      false, // TODO check if passed phone number is inserted in the current phone
      false, // TODO for faster login when SMS method is chosen
      firebaseAuthenticationSettings,
      Settings.instance().getAuthenticationTokens()
    );
  }

  private Map<String, Object> newConnectionParams () {
    Map<String, Object> params = new LinkedHashMap<>();
    if (isServiceInstance()) {
      params.put("device_token", "HIDDEN");
    } else {
      int state = context().getTokenState();
      final TdApi.DeviceToken deviceToken = getRegisteredDeviceToken();
      if (deviceToken != null && (state == TdlibManager.TokenState.NONE || state == TdlibManager.TokenState.INITIALIZING)) {
        state = TdlibManager.TokenState.OK;
      }
      String tokenProvider = TdlibNotificationUtils.getTokenRetriever().getName();
      String error = context().getTokenError();
      switch (state) {
        case TdlibManager.TokenState.ERROR: {
          params.put("device_token", tokenProvider.toUpperCase() + "_ERROR");
          if (!StringUtils.isEmpty(error)) {
            params.put(tokenProvider + "_error", error);
          }
          break;
        }
        case TdlibManager.TokenState.INITIALIZING: {
          params.put("device_token", tokenProvider.toUpperCase() + "_INITIALIZING");
          break;
        }
        case TdlibManager.TokenState.OK: {
          String tokenOrEndpoint;
          switch (ObjectUtils.requireNonNull(deviceToken).getConstructor()) {
            case TdApi.DeviceTokenFirebaseCloudMessaging.CONSTRUCTOR:
              tokenOrEndpoint = ((TdApi.DeviceTokenFirebaseCloudMessaging) deviceToken).token;
              break;
            case TdApi.DeviceTokenHuaweiPush.CONSTRUCTOR: {
              tokenOrEndpoint = ((TdApi.DeviceTokenHuaweiPush) deviceToken).token;
              final String huaweiTokenPrefix = "huawei://";
              if (tokenOrEndpoint.startsWith(huaweiTokenPrefix)) {
                tokenOrEndpoint = huaweiTokenPrefix + tokenOrEndpoint;
              }
              break;
            }
            case TdApi.DeviceTokenSimplePush.CONSTRUCTOR:
              tokenOrEndpoint = ((TdApi.DeviceTokenSimplePush) deviceToken).endpoint;
              break;
            default: {
              Td.assertDeviceToken_de4a4f61();
              throw Td.unsupported(deviceToken);
            }
          }
          params.put("device_token", tokenOrEndpoint);
          break;
        }
        case TdlibManager.TokenState.NONE:
          break;
        default:
          throw new IllegalStateException(Integer.toString(state));
      }
    }
    long timeZoneOffset = timeZoneOffset();
    params.put("package_id", UI.getAppContext().getPackageName());
    String installerName = AppInstallationUtil.getInstallerPackageName();
    if (!StringUtils.isEmpty(installerName)) {
      params.put("installer", installerName);
    }
    String initiatorName = AppInstallationUtil.getInitiatorPackageName();
    if (!StringUtils.isEmpty(initiatorName) && !initiatorName.equals(installerName)) {
      params.put("initiator", initiatorName);
    }
    if (BuildConfig.DEBUG) {
      params.put("debug", true);
    }
    String fingerprint = U.getApkFingerprint("SHA1", false);
    if (!StringUtils.isEmpty(fingerprint)) {
      params.put("data", fingerprint);
    }
    params.put("tz_offset", timeZoneOffset);

    Map<String, Object> git = new LinkedHashMap<>();
    git.put("remote", BuildConfig.REMOTE_URL.replaceAll("^(https?://)?github\\.com/", ""));
    git.put("commit", BuildConfig.COMMIT);
    git.put("tdlib", tdlibCommitHash());
    git.put("date", BuildConfig.COMMIT_DATE);
    List<Map<String, Object>> pullRequests = null;
    //noinspection ConstantConditions
    for (int i = 0; i < BuildConfig.PULL_REQUEST_ID.length; i++) {
      Map<String, Object> pr = new LinkedHashMap<>();
      pr.put("id", BuildConfig.PULL_REQUEST_ID[i]);
      pr.put("commit", BuildConfig.PULL_REQUEST_COMMIT[i]);
      pr.put("date", BuildConfig.PULL_REQUEST_COMMIT_DATE[i]);
      //noinspection ConstantConditions
      if (pullRequests == null) {
        pullRequests = new ArrayList<>();
      }
      pullRequests.add(pr);
    }
    //noinspection ConstantConditions
    if (pullRequests != null) {
      git.put("prs", pullRequests);
    }
    params.put("git", git);

    return params;
  }

  private void updateUtcTimeOffset () {
    performOptional(client -> {
      long timeZoneOffset = timeZoneOffset();
      if (this.utcTimeOffset != timeZoneOffset) {
        client.send(new TdApi.SetOption("utc_time_offset", new TdApi.OptionValueInteger(timeZoneOffset)), silentHandler());
      }
    }, null);
  }

  public static long timeZoneOffset () {
    return TimeUnit.MILLISECONDS.toSeconds(
      TimeZone.getDefault().getRawOffset() +
        TimeZone.getDefault().getDSTSavings()
    );
  }

  private void checkConnectionParams (Client client, boolean force) {
    Map<String, Object> params = newConnectionParams();
    String connectionParams = JSON.stringify(JSON.toObject(params));
    if (connectionParams != null && (force || !StringUtils.equalsOrBothEmpty(lastReportedConnectionParams, connectionParams))) {
      this.lastReportedConnectionParams = connectionParams;
      client.send(new TdApi.SetOption("connection_parameters", new TdApi.OptionValueString(connectionParams)), okHandler());
    }
  }

  private Thread tdlibThread;

  private void updateParameters (Client client) {
    Client.ResultHandler okHandler = object -> {
      updateTdlibThread();
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    };
    final boolean isService = isServiceInstance();
    client.send(new TdApi.SetOption("use_quick_ack", new TdApi.OptionValueBoolean(true)), okHandler);
    client.send(new TdApi.SetOption("use_pfs", new TdApi.OptionValueBoolean(true)), okHandler);
    client.send(new TdApi.SetOption("is_emulator", new TdApi.OptionValueBoolean(isEmulator = Settings.instance().isEmulator())), okHandler);
    if (isService) {
      updateNotificationParameters(client);
    } else {
      updateLanguageParameters(client, true);
      updateNotificationParameters(client);
      client.send(new TdApi.SetOption("storage_max_files_size", new TdApi.OptionValueInteger(Integer.MAX_VALUE)), okHandler);
      client.send(new TdApi.SetOption("ignore_default_disable_notification", new TdApi.OptionValueBoolean(true)), okHandler);
      client.send(new TdApi.SetOption("ignore_platform_restrictions", new TdApi.OptionValueBoolean(AppInstallationUtil.isAppSideLoaded())), okHandler);
    }
    checkConnectionParams(client, true);

    if (needDropNotificationIdentifiers) {
      client.send(new TdApi.SetOption("drop_notification_ids", new TdApi.OptionValueBoolean(true)), result -> {
        notifications().onDropNotificationData(true);
      });
      needDropNotificationIdentifiers = false;
    }

    parameters.useTestDc = isDebugInstance();
    parameters.databaseDirectory = TdlibManager.getTdlibDirectory(accountId, false);
    parameters.filesDirectory = TdlibManager.getTdlibDirectory(accountId, !isService);
    parameters.systemLanguageCode = TdlibManager.getSystemLanguageCode();
    parameters.deviceModel = TdlibManager.getDeviceModel();
    parameters.systemVersion = TdlibManager.getSystemVersion();
  }

  private String applicationConfigJson;

  private void setApplicationConfig (TdApi.JsonValue config, String json) {
    if (applicationConfigJson == null || !applicationConfigJson.equals(json)) {
      applicationConfigJson = json;
      settings().setApplicationConfig(json);
      if (config != null) {
        processApplicationConfig(config);
      }
    }
  }

  public boolean hasUrgentInAppUpdate () {
    return forceUrgentInAppUpdate;
  }

  private void processApplicationConfig (TdApi.JsonValue config) {
    if (!(config instanceof TdApi.JsonValueObject))
      return;
    TdApi.JsonValueObject object = (TdApi.JsonValueObject) config;
    for (TdApi.JsonObjectMember member : object.members) {
      if (StringUtils.isEmpty(member.key))
        continue;
      switch (member.key) {
        case "test":
          // Nothing to do?
          break;
        case "force_inapp_update":
          this.forceUrgentInAppUpdate = member.value instanceof TdApi.JsonValueBoolean && ((TdApi.JsonValueBoolean) member.value).value;
          break;
        case "ios_disable_parallel_channel_reset":
        case "small_queue_max_active_operations_count": // Number
        case "large_queue_max_active_operations_count": // Number
          break;
        case "youtube_pip":
          this.youtubePipDisabled = member.value instanceof TdApi.JsonValueString && "disabled".equals(((TdApi.JsonValueString) member.value).value);
          break;
        case "premium_playmarket_direct_currency_list":
          if (member.value instanceof TdApi.JsonValueArray) {
            TdApi.JsonValueArray array = (TdApi.JsonValueArray) member.value;
            for (TdApi.JsonValue value : array.values) {
              // ...
            }
          }
          break;
        case "qr_login_camera":
          if (member.value instanceof TdApi.JsonValueBoolean)
            this.qrLoginCamera = ((TdApi.JsonValueBoolean) member.value).value;
          break;
        case "qr_login_code":
          if (member.value instanceof TdApi.JsonValueString)
            this.qrLoginCode = ((TdApi.JsonValueString) member.value).value;
          break;
        case "dialog_filters_enabled":
          if (member.value instanceof TdApi.JsonValueBoolean)
            this.dialogFiltersEnabled = ((TdApi.JsonValueBoolean) member.value).value;
          break;
        case "dialog_filters_tooltip":
          if (member.value instanceof TdApi.JsonValueBoolean)
            this.dialogFiltersTooltip = ((TdApi.JsonValueBoolean) member.value).value;
          break;
        case "emojies_animated_zoom":
          if (member.value instanceof TdApi.JsonValueNumber)
            emojiesAnimatedZoom = Math.max(.75f, MathUtils.clamp(((TdApi.JsonValueNumber) member.value).value));
          break;
        case "rtc_servers":
          if (member.value instanceof TdApi.JsonValueArray) {
            TdApi.JsonValue[] array = ((TdApi.JsonValueArray) member.value).values;
            List<RtcServer> servers = new ArrayList<>(array.length);
            for (TdApi.JsonValue item : array) {
              if (item instanceof TdApi.JsonValueObject) {
                RtcServer server;
                try {
                  server = new RtcServer((TdApi.JsonValueObject) item);
                } catch (IllegalArgumentException ignored) {
                  continue;
                }
                servers.add(server);
              }
            }
            this.rtcServers = servers.toArray(new RtcServer[0]);
          }
          break;
        case "emojies_sounds":
          // TODO tdlib
          break;
        case "stickers_emoji_cache_time":
          break;
        case "stickers_emoji_suggest_only_api":
          if (member.value instanceof TdApi.JsonValueBoolean) {
            this.suggestOnlyApiStickers = ((TdApi.JsonValueBoolean) member.value).value;
          }
          break;
        case "groupcall_video_participants_max":
          if (member.value instanceof TdApi.JsonValueNumber) {
            this.maxGroupCallParticipantCount = (int) ((TdApi.JsonValueNumber) member.value).value;
          }
          break;
        case "round_video_encoding":
          if (member.value instanceof TdApi.JsonValueObject) {
            for (TdApi.JsonObjectMember property : ((TdApi.JsonValueObject) member.value).members) {
              if (!(property.value instanceof TdApi.JsonValueNumber))
                continue;
              long value = (long) ((TdApi.JsonValueNumber) property.value).value;
              switch (property.key) {
                case "diameter":
                  this.roundVideoDiameter = value;
                  break;
                case "video_bitrate":
                  this.roundVideoBitrate = value;
                  break;
                case "audio_bitrate":
                  this.roundAudioBitrate = value;
                  break;
                case "max_size":
                  this.roundVideoMaxSize = value;
                  break;
              }
            }
          }
          break;
        default:
          if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
            Log.i(Log.TAG_TDLIB_OPTIONS, "appConfig: %s -> %s", member.key, member.value);
          }
          break;
      }
    }
  }

  public String language () {
    return parameters.systemLanguageCode;
  }

  public long timeElapsedSinceDate (long unixTime, TimeUnit unit) {
    long now = System.currentTimeMillis();
    long unixTimeMs = unit.toMillis(unixTime);
    return Math.max(now - unixTimeMs, 0);
  }

  public long currentTimeMillis () {
    synchronized (dataLock) {
      if (unixTime != 0) {
        long elapsedMillis = SystemClock.elapsedRealtime() - unixTimeReceived;
        return TimeUnit.SECONDS.toMillis(unixTime) + elapsedMillis;
      }
      return System.currentTimeMillis();
    }
  }

  public long currentTime (TimeUnit unit) {
    return unit.convert(currentTimeMillis(), TimeUnit.MILLISECONDS);
  }

  public long toTdlibTimeMillis (long systemTime, TimeUnit unit) {
    return unit.toMillis(systemTime) + (currentTimeMillis() - System.currentTimeMillis());
  }

  public long toSystemTimeMillis (long tdlibTime, TimeUnit unit) {
    return unit.toMillis(tdlibTime) + (System.currentTimeMillis() - currentTimeMillis());
  }

  private int effectiveProxyId = Settings.PROXY_ID_UNKNOWN;

  @TdlibThread
  private void setEffectiveProxyId (int proxyId) {
    if (this.effectiveProxyId != proxyId) {
      boolean hadProxy = this.effectiveProxyId > Settings.PROXY_ID_NONE;
      boolean hasProxy = proxyId > Settings.PROXY_ID_NONE;
      this.effectiveProxyId = proxyId;
      if (hadProxy != hasProxy && connectionState == ConnectionState.CONNECTING) {
        notifyConnectionDisplayStatusChanged();
      }
    }
  }

  public void disableProxy () {
    setProxy(Settings.PROXY_ID_NONE, null);
  }

  public void setProxy (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy) {
    final TdApi.Function<?> function;
    if (proxy != null) {
      function = new TdApi.AddProxy(proxy.server, proxy.port, true, proxy.type);
    } else {
      function = new TdApi.DisableProxy();
    }
    client().send(function, (result) -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          setEffectiveProxyId(Settings.PROXY_ID_NONE);
          break;
        case TdApi.Proxy.CONSTRUCTOR:
          setEffectiveProxyId(proxyId);
          break;
      }
    });
  }
  public void cleanupProxies () {
    client().send(new TdApi.GetProxies(), result -> {
      switch (result.getConstructor()) {
        case TdApi.Proxies.CONSTRUCTOR: {
          TdApi.Proxies proxies = (TdApi.Proxies) result;
          for (TdApi.Proxy proxy : proxies.proxies) {
            if (!proxy.isEnabled) {
              client().send(new TdApi.RemoveProxy(proxy.id), okHandler());
            }
          }
          break;
        }
      }
    });
  }
  public void removeProxies (int excludeProxyId) {
    client().send(new TdApi.GetProxies(), (result) -> {
      switch (result.getConstructor()) {
        case TdApi.Proxies.CONSTRUCTOR: {
          TdApi.Proxy[] proxies = ((TdApi.Proxies) result).proxies;
          for (TdApi.Proxy proxy : proxies) {
            if (proxy.id != excludeProxyId) {
              client().send(new TdApi.RemoveProxy(proxy.id), okHandler());
            }
          }
          break;
        }
      }
    });
  }
  public void getProxyLink (@NonNull Settings.Proxy proxy, RunnableData<String> callback) {
    if (proxy.proxy == null)
      throw new IllegalArgumentException();
    send(new TdApi.AddProxy(proxy.proxy.server, proxy.proxy.port, false, proxy.proxy.type), (tdlibProxy, error) -> {
      if (error != null) {
        UI.showError(error);
        ui().post(() -> callback.runWithData(null));
      } else {
        int tdlibProxyId = tdlibProxy.id;
        send(new TdApi.GetProxyLink(tdlibProxyId), (httpUrl, error1) -> {
          String url;
          if (error1 != null) {
            Log.e("Proxy link unavailable: %s", TD.toErrorString(error1));
            url = null;
          } else {
            url = httpUrl.url;
          }
          ui().post(() -> callback.runWithData(url));
        });
      }
    });
  }

  @AnyThread
  private void notifyPingValueChanged (@NonNull Settings.Proxy proxy) {
    long pingMs = proxy.pingMs;
    ui().post(() ->
      context().global().notifyProxyPingChanged(proxy, pingMs)
    );
  }
  public void pingProxy (@NonNull Settings.Proxy proxy, @Nullable RunnableLong after) {
    int proxyId = proxy.id;
    int pingId = ++proxy.pingCount;
    proxy.pingMs = Settings.PROXY_TIME_LOADING;
    proxy.pingErrorCount = 0;
    notifyPingValueChanged(proxy);
    TdApi.Function<?> function = proxyId != Settings.PROXY_ID_NONE ?
      new TdApi.AddProxy(proxy.proxy.server, proxy.proxy.port, false, proxy.proxy.type) :
      new TdApi.PingProxy(0);
    AtomicLong uptimeMillis = new AtomicLong(SystemClock.uptimeMillis());
    client().send(function, new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object result) {
        if (pingId != proxy.pingCount) {
          return;
        }
        long now = SystemClock.uptimeMillis();
        long elapsed = now - uptimeMillis.getAndSet(now);
        long pingMs;
        switch (result.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            client().send(function, this);
            return;
          }
          case TdApi.Proxy.CONSTRUCTOR: {
            int tdlibProxyId = ((TdApi.Proxy) result).id;
            client().send(new TdApi.PingProxy(tdlibProxyId), this);
            return;
          }
          case TdApi.Seconds.CONSTRUCTOR: {
            long timestampMs = currentTimeMillis();
            pingMs = Math.round(((TdApi.Seconds) result).seconds * 1000.0);
            proxy.pingErrorCount = 0;
            Settings.instance().trackSuccessfulConnection(proxyId, timestampMs, pingMs, true);
            if (routeSelector != null) {
              routeSelector.markAsSuccessful(proxyId);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            if (++proxy.pingErrorCount < 3 && elapsed <= 1000) {
              client().send(new TdApi.SetAlarm(0.35 * proxy.pingErrorCount), this);
              return;
            }
            pingMs = Settings.PROXY_TIME_EMPTY;
            proxy.pingError = (TdApi.Error) result;
            break;
          }
          default:
            throw new UnsupportedOperationException(result.toString());
        }
        proxy.pingMs = pingMs;
        notifyPingValueChanged(proxy);
        if (after != null) {
          after.runWithLong(pingMs);
        }
      }
    });
    // return pingId;
  }

  private ClientHolder newClient () {
    Log.i("Creating TDLib client, hasInstance:%b, accountId:%d, debug:%b, wasPaused:%b", client != null, accountId, isDebugInstance(), instancePaused);
    instancePaused = false;
    return new ClientHolder(this);
  }

  public boolean isPaused () {
    synchronized (clientLock) {
      return client == null || instancePaused;
    }
  }

  void initializeIfWaiting () {
    clientHolder().init();
  }

  private TdApi.NetworkType networkType;

  public void setNetworkType (TdApi.NetworkType networkType) {
    this.networkType = networkType;
    performOptional(client ->
      client.send(new TdApi.SetNetworkType(networkType), okHandler()), null);
    listeners().updateConnectionType(networkType);
  }

  public boolean isWaitingForNetwork () {
    return (networkType != null && networkType.getConstructor() == TdApi.NetworkTypeNone.CONSTRUCTOR) || context.watchDog().isWaitingForNetwork();
  }

  public void resendNetworkTypeIfNeeded (TdApi.NetworkType networkType) {
    if (connectionState != ConnectionState.CONNECTED) {
      setNetworkType(networkType);
    }
  }

  // Options

  public boolean isStickerFavorite (int stickerId) {
    synchronized (dataLock) {
      return favoriteStickerIds != null && ArrayUtils.indexOf(favoriteStickerIds, stickerId) != -1;
    }
  }

  public int getInstalledStickerSetLimit () {
    synchronized (dataLock) {
      return installedStickerSetLimit;
    }
  }

  public boolean canSetPasscode (TdApi.Chat chat) {
    return chat != null && chat.type.getConstructor() == TdApi.ChatTypeSecret.CONSTRUCTOR;
  }

  private static final int CLIENT_DATA_VERSION = 0;

  public static class ChatPasscode {
    public static final int FLAG_INVISIBLE = 1;

    public int mode;
    private int flags;
    public String hash;
    public @Nullable String fingerHash;

    public ChatPasscode (int mode, int flags, String hash, @Nullable String fingerHash) {
      this.mode = mode;
      this.flags = flags;
      this.hash = hash;
      this.fingerHash = fingerHash;
    }

    public boolean isVisible () {
      return (flags & FLAG_INVISIBLE) == 0;
    }

    public void setIsVisible (boolean isVisible) {
      flags = BitwiseUtils.setFlag(flags, FLAG_INVISIBLE, !isVisible);
    }

    public boolean unlock (int mode, String hash) {
      return this.mode == mode && this.hash.equals(hash);
    }

    public boolean unlockWithFingerprint (String fingerHash) {
      return !StringUtils.isEmpty(this.fingerHash) && this.fingerHash.equals(fingerHash);
    }

    @Override
    public final String toString () {
      StringBuilder b = new StringBuilder()
        .append(CLIENT_DATA_VERSION)
        .append('_')
        .append(mode)
        .append('_')
        .append(flags)
        .append('_')
        .append(hash.length())
        .append('_')
        .append(hash);
      if (!StringUtils.isEmpty(fingerHash)) {
        b.append('_').append(fingerHash.length()).append('_').append(fingerHash);
      }
      return b.toString();
    }

    public static int makeFlags (boolean isVisible) {
      int flags = 0;
      if (!isVisible) {
        flags |= FLAG_INVISIBLE;
      }
      return flags;
    }
  }

  public boolean hasPasscode (long chatId) {
    return hasPasscode(chat(chatId));
  }

  public boolean hasPasscode (TdApi.Chat chat) {
    return chat != null && !StringUtils.isEmpty(chat.clientData);
  }

  public void setPasscode (TdApi.Chat chat, @Nullable ChatPasscode passcode) {
    if (chat != null) {
      if (passcode != null && Passcode.isValidMode(passcode.mode)) {
        setChatClientData(chat, passcode.toString());
      } else {
        setChatClientData(chat, null);
      }
    }
  }

  public @Nullable ChatPasscode chatPasscode (TdApi.Chat chat) {
    if (chat == null) {
      return null;
    }
    String clientData = chat.clientData;
    if (StringUtils.isEmpty(clientData)) {
      return null;
    }
    String[] data = clientData.split("_", 2);
    if (data.length != 2) {
      return null;
    }
    int version = StringUtils.parseInt(data[0]);
    if (version < 0 || version > CLIENT_DATA_VERSION) {
      return null;
    }
    if (version < CLIENT_DATA_VERSION) {
      for (; version <= CLIENT_DATA_VERSION; version++) {
        clientData = upgradeChatClientData(chat, version);
      }
      setChatClientData(chat, clientData);
      if (StringUtils.isEmpty(clientData)) {
        return null;
      }
      data = clientData.split("_", 2);
      if (data.length != 2 || StringUtils.parseInt(data[0]) != CLIENT_DATA_VERSION) {
        return null;
      }
    }
    try {
      String arg = data[1];
      int i = arg.indexOf('_');
      int mode = StringUtils.parseInt(arg.substring(0, i));
      if (!Passcode.isValidMode(mode)) {
        return null;
      }
      int startIndex = i + 1;
      i = arg.indexOf('_', startIndex);
      int flags = StringUtils.parseInt(arg.substring(startIndex, i));
      startIndex = i + 1;
      i = arg.indexOf('_', startIndex);
      int passcodeHashLength = StringUtils.parseInt(arg.substring(startIndex, i));
      if (passcodeHashLength < 0) {
        return null;
      }
      startIndex = i + 1;
      String passcodeHash = arg.substring(startIndex, startIndex + passcodeHashLength);
      String fingerHash = null;
      if (arg.length() > startIndex + passcodeHashLength) {
        startIndex = startIndex + passcodeHashLength + 1;
        i = arg.indexOf('_', startIndex);
        int fingerHashLength = StringUtils.parseInt(arg.substring(startIndex, i));
        if (fingerHashLength > 0) {
          fingerHash = arg.substring(i + 1, i + 1 + fingerHashLength);
        }
      }
      return new ChatPasscode(mode, flags, passcodeHash, fingerHash);
    } catch (Throwable t) {
      Log.w("Unable to parse clientData", t);
    }
    return null;
  }

  private String upgradeChatClientData (TdApi.Chat chat, int version) {
    switch (version) {
      // TODO case VERSION_1: ...; setChatClientData(...);
    }
    throw new RuntimeException("version: " + version + ", clientData: " + chat.clientData);
  }

  private void setChatClientData (TdApi.Chat chat, String data) {
    if (chat == null) {
      return;
    }
    synchronized (dataLock) {
      if (StringUtils.equalsOrBothEmpty(chat.clientData, data)) {
        return;
      }
      chat.clientData = data;
    }
    long chatId = chat.id;
    client().send(new TdApi.SetChatClientData(chatId, data), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          listeners.updateChatClientDataChanged(chatId, data);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e("Cannot set clientData: %s, chatId:%d, clientData:%s", TD.toErrorString(object), chatId, data);
          UI.showError(object);
          break;
        }
      }
    });
  }

  private boolean isOnline;

  public boolean isOnline () {
    return isOnline;
  }

  public void setOnline (boolean isOnline) {
    if (this.isOnline != isOnline) {
      this.isOnline = isOnline;
      Log.i("SetOnline accountId:%d -> %b", accountId, isOnline);
      if (Config.NEED_ONLINE) {
        performOptional(client -> client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(isOnline)), okHandler()), null);
      }
      // cache().setPauseStatusRefreshers(!isOnline);
    }
  }

  private boolean isEmulator;

  public void setIsEmulator (boolean isEmulator) {
    if (this.isEmulator != isEmulator) {
      this.isEmulator = isEmulator;
      performOptional(client -> client.send(new TdApi.SetOption("is_emulator", new TdApi.OptionValueBoolean(isEmulator)), okHandler()), null);
    }
  }

  public boolean isEmulator () {
    return isEmulator;
  }

  public void sync (long pushId, @Nullable Runnable after, boolean needNotifications, boolean needNetworkRequest) {
    TDLib.Tag.notifications(pushId, accountId, "Performing sync needNotification: %b, needNetworkRequest: %b, hasAfter: %b. Awaiting connection. Connection state: %d, status: %d", needNotifications, needNetworkRequest, after != null, connectionState, authorizationStatus());
    incrementReferenceCount(REFERENCE_TYPE_SYNC);
    Runnable onDone = () -> {
      if (after != null) {
        if (needNotifications) {
          TDLib.Tag.notifications(pushId, accountId, "Making sure havePendingNotifications is false.");
          awaitNotifications(() -> {
            TDLib.Tag.notifications(pushId, accountId, "Sync task finished.");
            after.run();
          });
        } else {
          TDLib.Tag.notifications(pushId, accountId, "Sync task finished.");
          after.run();
        }
      } else {
        TDLib.Tag.notifications(pushId, accountId, "Sync task finished, but there's no callback.");
      }
      decrementReferenceCount(REFERENCE_TYPE_SYNC);
    };
    if (Config.NEED_NETWORK_SYNC_REQUEST || needNetworkRequest) {
      awaitConnection(() -> {
        TDLib.Tag.notifications(pushId, accountId, "Connection available. Performing network request to make sure it's still active.");
        performOptional(client -> {
          client.send(new TdApi.GetCountryCode(), ignored -> onDone.run());
        }, onDone);
      });
    } else {
      awaitConnection(onDone);
    }
  }

  public boolean notifyPushProcessingTakesTooLong (long pushId) { // Called from Firebase thread
    TdApi.AuthorizationState authorizationState = this.authorizationState;
    if (authorizationState != null) {
      switch (authorizationState.getConstructor()) {
        case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
        case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
          // Make sure action finishes even if it causes ANR.
          return false;
      }
    }
    notifications().notifyPushProcessingTakesTooLong();
    return true;
  }

  void processPushOrSync (long pushId, String payload, @Nullable Runnable after) {
    TDLib.Tag.notifications(pushId, accountId, "Started processing push notification, hasAfter:%b", after != null);
    incrementNotificationReferenceCount();
    client().send(new TdApi.ProcessPushNotification(payload), result -> {
      Runnable notificationChecker = () -> {
        TDLib.Tag.notifications(pushId, accountId, "Making sure all notifications displayed");
        incrementNotificationReferenceCount();
        if (after != null) {
          notifications().releaseTdlibReference(() -> {
            TDLib.Tag.notifications(pushId, accountId, "Making sure we're not in AuthorizationStateLoggingOut");
            awaitClose(() -> {
              TDLib.Tag.notifications(pushId, accountId, "Finished processing push. Invoking after()");
              after.run();
            }, false);
          });
        } else {
          notifications().releaseTdlibReference(() -> {
            TDLib.Tag.notifications(pushId, accountId, "All notifications displayed. But there's no after() callback.");
          });
        }
      };

      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          TDLib.Tag.notifications(pushId, accountId, "Ensuring updateActiveNotifications was sent. ignoreNotificationUpdates:%b, receivedActiveNotificationsTime:%d, receivedActiveNotificationsIgnored: %b", ignoreNotificationUpdates, receivedActiveNotificationsTime, receivedActiveNotificationsIgnored);
          awaitNotificationInitialization(notificationChecker);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          TdApi.Error error = (TdApi.Error) result;
          if (error.code == 401) {
            TDLib.Tag.notifications(pushId, accountId, "TDLib tells to expect AuthorizationStateLoggingOut: %s, waiting.", error);
            awaitClose(() -> {
              if (after != null) {
                TDLib.Tag.notifications(pushId, accountId, "Finished processing push. Invoking after()");
                after.run();
              } else {
                TDLib.Tag.notifications(pushId, accountId, "All notifications displayed. But there's no after() callback.");
              }
            }, true);
          } else {
            TDLib.Tag.notifications(pushId, accountId, "Failed to process push: %s, performing full sync.", TD.toErrorString(result));
            setHasUnprocessedPushes(true);
            sync(pushId, () -> {
              setHasUnprocessedPushes(false);
              notificationChecker.run();
            }, true, false);
          }
          break;
        }
      }
      decrementNotificationReferenceCount();
    });
  }

  public boolean disableContactRegisteredNotifications (boolean allowRequest) {
    if (allowRequest) {
      client().send(new TdApi.GetOption("disable_contact_registered_notifications"), result -> {
        if (result.getConstructor() == TdApi.OptionValueBoolean.CONSTRUCTOR) {
          setDisableContactRegisteredNotificationsImpl(((TdApi.OptionValueBoolean) result).value);
        }
      });
    }
    return disableContactRegisteredNotifications;
  }

  public void setDisableContactRegisteredNotifications (boolean disable) {
    if (this.disableContactRegisteredNotifications != disable) {
      this.disableContactRegisteredNotifications = disable;
      client().send(new TdApi.SetOption("disable_contact_registered_notifications", new TdApi.OptionValueBoolean(disable)), okHandler());
      listeners().updateContactRegisteredNotificationsDisabled(disable);
    }
  }

  private void setDisableContactRegisteredNotificationsImpl (boolean disable) {
    if (this.disableContactRegisteredNotifications != disable) {
      this.disableContactRegisteredNotifications = disable;
      listeners().updateContactRegisteredNotificationsDisabled(disable);
    }
  }

  private void setDisableTopChatsImpl (boolean disableTopChats) {
    if (this.disableTopChats == null) {
      this.disableTopChats = disableTopChats;
    } else if (this.disableTopChats != disableTopChats) {
      this.disableTopChats = disableTopChats;
      listeners().updateTopChatsDisabled(disableTopChats);
    }
  }

  private void setDisableSentScheduledMessageNotificationsImpl (boolean disableSentScheduledMessageNotifications) {
    if (this.disableSentScheduledMessageNotifications != disableSentScheduledMessageNotifications) {
      this.disableSentScheduledMessageNotifications = disableSentScheduledMessageNotifications;
      listeners().updatedSentScheduledMessageNotificationsDisabled(disableSentScheduledMessageNotifications);
    }
  }

  /*public boolean disablePinnedMessageNotifications () {
    return disablePinnedMessageNotifications;
  }

  public void setDisablePinnedMessageNotifications (boolean disable) {
    if (this.disablePinnedMessageNotifications != disable) {
      this.disablePinnedMessageNotifications = disable;
      client().send(new TdApi.SetOption("disable_pinned_message_notifications", new TdApi.OptionValueBoolean(disable)), okHandler);
    }
  }*/

  public long authorizationDate () {
    return authorizationDate;
  }

  public boolean callsEnabled () {
    return callsEnabled;
  }

  public boolean expectBlocking () {
    return expectBlocking;
  }

  public boolean isLocationVisible () {
    return isLocationVisible;
  }

  public void setLocationVisible (boolean isLocationVisible) {
    this.isLocationVisible = isLocationVisible;
    client().send(new TdApi.SetOption("is_location_visible", new TdApi.OptionValueBoolean(isLocationVisible)), okHandler());
  }

  public boolean canIgnoreSensitiveContentRestriction () {
    return canIgnoreSensitiveContentRestrictions;
  }

  public boolean ignoreSensitiveContentRestrictions () {
    return ignoreSensitiveContentRestrictions;
  }

  public void setIgnoreSensitiveContentRestrictions (boolean ignoreSensitiveContentRestrictions) {
    if (this.ignoreSensitiveContentRestrictions != ignoreSensitiveContentRestrictions) {
      this.ignoreSensitiveContentRestrictions = ignoreSensitiveContentRestrictions;
      client().send(new TdApi.SetOption("ignore_sensitive_content_restrictions", new TdApi.OptionValueBoolean(ignoreSensitiveContentRestrictions)), okHandler());
    }
  }

  public String uniqueSuffix () {
    return accountId + "." + authorizationDate;
  }

  public String uniqueSuffix (long id) {
    return accountId + "." + authorizationDate + "." + id;
  }

  public int supergroupMaxSize () {
    return supergroupMaxSize;
  }

  public int maxBioLength () {
    return maxBioLength;
  }

  public int forwardMaxCount () {
    return forwardMaxCount;
  }

  public int basicGroupMaxSize () {
    return groupMaxSize;
  }

  public int pinnedChatsMaxCount () {
    return pinnedChatsMaxCount;
  }

  public int pinnedArchivedChatsMaxCount () {
    return pinnedArchivedChatsMaxCount;
  }

  public int favoriteStickersMaxCount () {
    return favoriteStickersMaxCount;
  }

  public double emojiesAnimatedZoom () {
    return emojiesAnimatedZoom;
  }

  public boolean youtubePipEnabled () {
    return !youtubePipDisabled || AppInstallationUtil.isAppSideLoaded();
  }

  public RtcServer[] rtcServers () {
    return rtcServers;
  }

  public boolean autoArchiveAvailable () {
    return canArchiveAndMuteNewChatsFromUnknownUsers;
  }

  public String tMeUrl () {
    return StringUtils.isEmpty(tMeUrl) ? "https://" + TdConstants.TME_HOSTS[0] + "/" : tMeUrl;
  }

  public String tMeMessageUrl (String username, long messageId) {
    return tMeUrl(username + "/" + messageId);
  }

  public String tMePrivateMessageUrl (long supergroupId, long messageId) {
    return tMeUrl("c/" + supergroupId + "/" + messageId);
  }

  public Uri.Builder tMeUrlBuilder () {
    return new Uri.Builder()
      .scheme("https")
      .authority(tMeAuthority());
  }

  public String tMeUrl (@NonNull TdApi.Usernames usernames) {
    return tMeUrl(usernames, false);
  }

  public String tMeUrl (@NonNull TdApi.Usernames usernames, boolean excludeProtocol) {
    return tMeUrl(Td.primaryUsername(usernames), excludeProtocol);
  }

  public String tMeUrl (String path) {
    return tMeUrl(path, false);
  }

  public String tMeUrl (String path, boolean excludeProtocol) {
    String result = tMeUrlBuilder()
      .path(path)
      .build()
      .toString();
    if (excludeProtocol) {
      int i = result.indexOf("://");
      if (i != -1) {
        return result.substring(i + "://".length());
      }
    }
    return result;
  }

  public String tMeStartUrl (String botUsername, String parameter, boolean inGroup) {
    return tMeUrlBuilder()
      .path(botUsername)
      .appendQueryParameter(inGroup ? "startgroup" : "start", parameter)
      .build()
      .toString();
  }

  public String tMeChatUrl (long chatId) {
    String username = chatUsername(chatId);
    if (!StringUtils.isEmpty(username)) {
      return tMeUrl(username);
    }
    if (ChatId.isSupergroup(chatId)) {
      return tMeUrl("c/" + ChatId.toSupergroupId(chatId));
    }
    return null;
  }

  public String tMeBackgroundUrl (String backgroundId) {
    return tMeUrl("bg/" + backgroundId);
  }

  public String tMeStickerSetUrl (String stickerSetName) {
    return tMeUrl("addstickers/" + stickerSetName);
  }

  public String tMeLanguageUrl (String languagePackId) {
    return tMeUrl("setlanguage/" + languagePackId);
  }

  public String tMeStickerSetUrl (@NonNull TdApi.StickerSetInfo stickerSetInfo) {
    switch (stickerSetInfo.stickerType.getConstructor()) {
      case TdApi.StickerTypeCustomEmoji.CONSTRUCTOR:
        return tMeUrl("addemoji/" + stickerSetInfo.name);
      case TdApi.StickerTypeMask.CONSTRUCTOR:
      case TdApi.StickerTypeRegular.CONSTRUCTOR:
        return tMeUrl("addstickers/" + stickerSetInfo.name);
      default: {
        Td.assertStickerType_cc811bb7();
        throw Td.unsupported(stickerSetInfo.stickerType);
      }
    }
  }

  public String tMeHost () {
    return StringUtils.urlWithoutProtocol(tMeUrl());
  }

  public String tMeAuthority () {
    return Uri.parse(tMeUrl).getHost();
  }

  public boolean areTopChatsDisabled () {
    return disableTopChats != null ? disableTopChats : false;
  }

  public void setDisableTopChats (boolean disableTopChats) {
    this.disableTopChats = disableTopChats;
    client().send(new TdApi.SetOption("disable_top_chats", new TdApi.OptionValueBoolean(disableTopChats)), okHandler());
  }

  public boolean areSentScheduledMessageNotificationsDisabled () {
    return disableSentScheduledMessageNotifications;
  }

  public void setDisableSentScheduledMessageNotifications (boolean disable) {
    this.disableSentScheduledMessageNotifications = disable;
    client().send(new TdApi.SetOption("disable_sent_scheduled_message_notifications", new TdApi.OptionValueBoolean(disable)), okHandler());
  }

  public String getAnimationSearchBotUsername () {
    return animationSearchBotUsername;
  }

  public String getVenueSearchBotUsername () {
    return venueSearchBotUsername;
  }

  public String getPhotoSearchBotUsername () {
    return photoSearchBotUsername;
  }

  public boolean isTmeUrl (String url) {
    if (StringUtils.isEmpty(url))
      return false;
    if (url.startsWith("tg://"))
      return true;
    try {
      if (!url.startsWith("http://") && !url.startsWith("https://"))
        url = "http://" + url;
      return isKnownHost(Uri.parse(url).getHost(), false);
    } catch (Throwable ignored) {
      return false;
    }
  }

  public String getWallpaperData (String url) {
    if (StringUtils.isEmpty(url))
      return null;
    try {
      Uri uri = Uri.parse(url);
      if ("tg".equals(uri.getScheme())) {
        if (!"bg".equals(uri.getHost()))
          return null;
        String data = uri.getQueryParameter("slug");
        if (!StringUtils.isEmpty(data)) {
          String bgColor = uri.getQueryParameter("bg_color");
          String intensity = uri.getQueryParameter("intensity");
          if (!StringUtils.isEmpty(bgColor) || !StringUtils.isEmpty(intensity)) {
            StringBuilder b = new StringBuilder(data).append("?");
            if (!StringUtils.isEmpty(bgColor))
              b.append("bg_color=").append(bgColor);
            if (!StringUtils.isEmpty(intensity)) {
              if (!StringUtils.isEmpty(bgColor))
                b.append("&");
              b.append("intensity=").append(intensity);
            }
            data = b.toString();
          }
        } else {
          data = uri.getQueryParameter("color");
        }
        return data;
      }
      if (StringUtils.isEmpty(uri.getScheme())) {
        uri = Uri.parse("http://" + url);
      }
      if (!isKnownHost(uri.getHost(), false))
        return null;
      List<String> segments = uri.getPathSegments();
      if (segments != null && segments.size() == 2 && "bg".equalsIgnoreCase(segments.get(0))) {
        String query = uri.getQuery();
        return StringUtils.isEmpty(query) ? segments.get(1) : segments.get(1) + "?" + query;
      }
      return null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public boolean isTrustedHost (String host, boolean allowSubdomains) {
    // No prompt when pressing links from these hosts.
    if (StringUtils.isEmpty(host)) {
      return false;
    }
    Uri uri = Strings.wrapHttps(host);
    if (uri == null) {
      return false;
    }
    host = uri.getHost().toLowerCase();
    for (String knownHost : TdConstants.TELEGRAM_HOSTS) {
      if (StringUtils.equalsOrBothEmpty(host, knownHost) || (allowSubdomains && host.endsWith("." + knownHost))) {
        return true;
      }
    }
    return false;
  }

  public boolean isKnownHost (String host, boolean allowTelegraph) {
    if (StringUtils.isEmpty(host)) {
      return false;
    }
    Uri uri = Strings.wrapHttps(host);
    if (uri == null) {
      return false;
    }
    host = uri.getHost().toLowerCase();
    if (!StringUtils.isEmpty(tMeUrl)) {
      String tMeHost = StringUtils.urlWithoutProtocol(tMeUrl);
      if (StringUtils.equalsOrBothEmpty(host, tMeHost) || host.endsWith("." + tMeHost)) {
        return true;
      }
    }
    for (String knownHost : TdConstants.TME_HOSTS) {
      if (StringUtils.equalsOrBothEmpty(host, knownHost) || host.endsWith("." + knownHost)) {
        return true;
      }
    }
    if (allowTelegraph) {
      for (String knownHost : TdConstants.TELEGRAM_HOSTS) {
        if (StringUtils.equalsOrBothEmpty(host, knownHost) || host.endsWith("." + knownHost)) {
          return true;
        }
      }
      for (String knownHost : TdConstants.TELEGRAPH_HOSTS) {
        if (StringUtils.equalsOrBothEmpty(host, knownHost) || host.endsWith("." + knownHost)) {
          return true;
        }
      }
    }
    return false;
  }

  public long callConnectTimeoutMs () {
    return callConnectTimeoutMs;
  }

  public boolean allowQrLoginCamera () {
    return (qrLoginCamera && Config.QR_AVAILABLE) || BuildConfig.DEBUG;
  }

  public long callPacketTimeoutMs () {
    return callPacketTimeoutMs;
  }

  public int maxCaptionLength () {
    return maxMessageCaptionLength;
  }

  public boolean suggestOnlyApiStickers () {
    return suggestOnlyApiStickers;
  }

  public int maxMessageTextLength () {
    return maxMessageTextLength;
  }
  
  public long chatFolderCountMax () {
    return chatFolderMaxCount;
  }

  public long chatFolderChosenChatCountMax () {
    return folderChosenChatMaxCount;
  }

  public long telegramAntiSpamUserId () {
    return antiSpamBotUserId;
  }

  public long telegramChannelBotUserId () {
    return channelBotUserId;
  }

  public @ConnectionState int connectionState () {
    return connectionState;
  }

  public String connectionStateText () {
    switch (connectionState) {
      case ConnectionState.UNKNOWN:
        return Lang.getString(R.string.Initializing);
      case ConnectionState.CONNECTED:
        return Lang.getString(R.string.Connected);
      case ConnectionState.WAITING_FOR_NETWORK:
        return Lang.getString(R.string.network_WaitingForNetwork);
      case ConnectionState.CONNECTING: {
        if (routeSelector != null) {
          if (routeSelector.isLookingUpForRoute()) {
            return Lang.getString(R.string.network_Lookup);
          } else if (routeSelector.isEmpty()) {
            return Lang.getString(effectiveProxyId > Settings.PROXY_ID_NONE ?
              R.string.network_LookupFailedProxy :
              R.string.network_LookupFailed
            );
          } else if (routeSelector.isPending(effectiveProxyId)) {
            return Lang.getString(effectiveProxyId > Settings.PROXY_ID_NONE ?
              R.string.network_LookupAttemptProxy :
              R.string.network_LookupAttempt
            );
          }
        }
        return Lang.getString(effectiveProxyId > Settings.PROXY_ID_NONE ?
          R.string.ConnectingWithProxy :
          R.string.network_Connecting
        );
      }
      case ConnectionState.CONNECTING_TO_PROXY:
        return Lang.getString(R.string.ConnectingToProxy);
      case ConnectionState.UPDATING:
        return Lang.getString(R.string.network_Updating);
    }
    throw new UnsupportedOperationException(Integer.toString(connectionState));
  }

  public @Nullable TdApi.NetworkType networkType () {
    return networkType;
  }

  public boolean isConnected () {
    return connectionState == ConnectionState.CONNECTED;
  }

  public boolean isConnectingOrUpdating () {
    switch (connectionState) {
      case ConnectionState.CONNECTING:
      case ConnectionState.CONNECTING_TO_PROXY:
      case ConnectionState.UPDATING:
        return true;
      case ConnectionState.CONNECTED:
      case ConnectionState.WAITING_FOR_NETWORK:
      case ConnectionState.UNKNOWN:
        break;
    }
    return false;
  }

  public void scheduleUiMessageAction (TGMessage msg, int action, int arg1, int arg2, long delay) {
    int what = MSG_ACTION_MESSAGE_ACTION_PREFIX + action;
    if (delay > 0) {
      ui().sendMessageDelayed(ui().obtainMessage(what, arg1, arg2, msg), delay);
    } else {
      ui().sendMessage(ui().obtainMessage(what, arg1, arg2, msg));
    }
  }

  public void cancelUiMessageActions (TGMessage msg, int action) {
    ui().removeMessages(MSG_ACTION_MESSAGE_ACTION_PREFIX + action, msg);
  }

  // UI handler

  private static final int MSG_ACTION_UPDATE_CHAT_ACTION = 1;
  private static final int MSG_ACTION_UPDATE_CALL = 2;
  private static final int MSG_ACTION_DISPATCH_UNREAD_COUNTER = 3;
  private static final int MSG_ACTION_REMOVE_LOCATION_MESSAGE = 4;
  private static final int MSG_ACTION_CALL_STATE = 5;
  private static final int MSG_ACTION_CALL_BARS = 6;
  private static final int MSG_ACTION_PAUSE = 7;
  private static final int MSG_ACTION_USER_STATUS = 8;
  private static final int MSG_ACTION_DISPATCH_TERMS_OF_SERVICE = 9;
  private static final int MSG_ACTION_UPDATE_LANG_PACK = 11;
  private static final int MSG_ACTION_MESSAGE_ACTION_PREFIX = 100000;

  void handleUiMessage (Message msg) {
    switch (msg.what) {
      case MSG_ACTION_UPDATE_CHAT_ACTION:
        statusManager.onUpdateChatUserAction((TdApi.UpdateChatAction) msg.obj);
        break;
      case MSG_ACTION_UPDATE_CALL:
        cache.onUpdateCall((TdApi.UpdateCall) msg.obj);
        break;
      case MSG_ACTION_DISPATCH_UNREAD_COUNTER:
        dispatchUnreadCounters((TdApi.ChatList) msg.obj, msg.arg1, msg.arg2 == 1);
        break;
      case MSG_ACTION_REMOVE_LOCATION_MESSAGE:
        cache().onScheduledRemove((TdApi.Message) msg.obj);
        break;
      case MSG_ACTION_CALL_STATE:
        cache().onCallStateChanged(msg.arg1, msg.arg2);
        break;
      case MSG_ACTION_CALL_BARS:
        cache().onCallSignalBarsChanged(msg.arg1, msg.arg2);
        break;
      case MSG_ACTION_PAUSE:
        doPause();
        break;
      case MSG_ACTION_USER_STATUS:
        cache().onUpdateUserStatusInternal((TdApi.UpdateUserStatus) msg.obj, msg.arg1 == 1);
        break;
      case MSG_ACTION_DISPATCH_TERMS_OF_SERVICE:
        ui().handleTermsOfService((TdApi.UpdateTermsOfService) msg.obj);
        break;
      case MSG_ACTION_UPDATE_LANG_PACK:
        Lang.updateLanguagePack((TdApi.UpdateLanguagePackStrings) msg.obj);
        break;
      default:
        if (msg.what >= MSG_ACTION_MESSAGE_ACTION_PREFIX) {
          ((TGMessage) msg.obj).handleUiMessage(msg.what - MSG_ACTION_MESSAGE_ACTION_PREFIX, msg.arg1, msg.arg2);
        }
        break;
    }
  }

  // UI

  @AnyThread
  public void dispatchUserStatus (TdApi.UpdateUserStatus update, boolean uiOnly) {
    ui().sendMessage(ui().obtainMessage(MSG_ACTION_USER_STATUS, uiOnly ? 1 : 0, 0, update));
  }

  @AnyThread
  public void dispatchCallStateChanged (final int callId, final @CallState int newState) {
    ui().sendMessage(ui().obtainMessage(MSG_ACTION_CALL_STATE, callId, newState));
  }

  @AnyThread
  public void dispatchCallBarsCount (final int callId, final int barsCount) {
    ui().sendMessage(ui().obtainMessage(MSG_ACTION_CALL_BARS, callId, barsCount));
  }

  void scheduleLocationRemoval (TdApi.Message message) {
    ui().sendMessageDelayed(ui().obtainMessage(MSG_ACTION_REMOVE_LOCATION_MESSAGE, message), (long) ((TdApi.MessageLocation) message.content).expiresIn * 1000l);
  }

  void cancelLocationRemoval (TdApi.Message message) {
    ui().removeMessages(MSG_ACTION_REMOVE_LOCATION_MESSAGE, message);
  }

  // Updates: NOTIFICATIONS

  private boolean havePendingNotifications, haveInitializedNotifications;

  private void resetState () {
    haveInitializedNotifications = false;
    ignoreNotificationUpdates = false;
  }

  private void resetChatsData () {
    knownChatIds.clear();
    chats.clear();
    chatLists.clear();
    forumTopicInfos.clear();
  }

  private void resetContextualData () {
    // chats.clear();
    resetChatsData();
    activeCalls.clear();
    activeStories.clear();
    storyLists.clear();
    storyStealthModeActiveUntilDate = storyStealthModeCooldownUntilDate = 0;
    accessibleChatTimers.clear();
    chatOnlineMemberCount.clear();
    myProfilePhoto = null;
    myEmojiStatusId = 0;
    pendingMessageTexts.clear();
    pendingMessageCaptions.clear();
    animatedDiceExplicit.clear();
    suggestedActions.clear();
    telegramServiceNotificationsChatId = TdConstants.TELEGRAM_ACCOUNT_ID;
    repliesBotChatId = TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID;
    sessionsInfo = null;
    animatedTgxEmoji.clear();
    cachedReactions.clear();
    activeEmojiReactions = null;
  }

  public static class RtcServer {
    public final String host;
    public final int port;
    public final String username, password;

    public RtcServer (TdApi.JsonValueObject object) {
      Map<String, TdApi.JsonValue> map = JSON.asMap(object);
      this.host = JSON.asString(map.get("host"));
      this.port = JSON.asInt(map.get("port"));
      this.username = JSON.asString(map.get("username"));
      this.password = JSON.asString(map.get("password"));
    }
  }

  private static class StickerSet {
    private final ConcurrentHashMap<String, TdApi.Sticker> stickers = new ConcurrentHashMap<>();
    private TdApi.StickerSet stickerSet;

    private final int type;
    private boolean isLoading;
    private String currentSetName;
    private boolean onlySingle;

    public StickerSet (int type, String setName, boolean onlySingle) {
      this.type = type;
      this.currentSetName = setName;
      this.onlySingle = onlySingle;
    }

    public TdApi.Sticker find (String emoji) {
      return stickers.get(Emoji.instance().cleanEmojiCode(emoji));
    }

    public TdApi.Sticker find (int value) {
      return stickers.get(Emoji.toEmoji(value));
    }

    public void clear () {
      isLoading = false;
      stickers.clear();
      stickerSet = null;
    }

    public void reload (Tdlib tdlib, String newStickerSetName) {
      if (this.currentSetName == null || !this.currentSetName.equalsIgnoreCase(newStickerSetName)) {
        this.currentSetName = newStickerSetName;
        this.isLoading = false;
        load(tdlib);
      }
    }

    public boolean isLoaded () {
      return !stickers.isEmpty();
    }

    public void load (Tdlib tdlib) {
      if (!isLoading && !StringUtils.isEmpty(currentSetName)) {
        isLoading = true;
        tdlib.stickerSet(currentSetName, stickerSet -> reset(tdlib, stickerSet));
      }
    }

    public void update (Tdlib tdlib, TdApi.StickerSet stickerSet) {
      if (this.stickerSet != null && this.stickerSet.id == stickerSet.id) {
        reset(tdlib, stickerSet);
      }
    }

    public void reset (Tdlib tdlib, TdApi.StickerSet stickerSet) {
      clear();
      this.stickerSet = stickerSet;
      if (stickerSet != null) {
        int index = 0;
        for (TdApi.Sticker sticker : stickerSet.stickers) {
          TdApi.Emojis emojis = stickerSet.emojis[index];
          if (onlySingle && emojis.emojis.length > 1)
            continue;
          for (String emoji : emojis.emojis) {
            emoji = Emoji.instance().cleanEmojiCode(emoji);
            if (!stickers.containsKey(emoji)) {
              stickers.put(emoji, sticker);
            }
          }
          index++;
        }
      }
      tdlib.listeners().notifyAnimatedEmojiListeners(type);
      if (type == AnimatedEmojiListener.TYPE_DICE) {
        TdApi.Sticker sticker = find(0);
        if (sticker != null && !TD.isFileLoaded(sticker.sticker)) {
          tdlib.files().downloadFile(sticker.sticker);
        }
      }
    }
  }

  private TdApi.Sticker findExplicitDiceEmoji (int value) {
    if (!Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE))
      return null;
    TdApi.StickerSet stickerSet = animatedDiceExplicit.stickerSet;
    if (stickerSet != null) {
      String languageEmoji = Lang.getLanguageEmoji();
      String builtinLanguageEmoji = Lang.getBuiltinLanguageEmoji();
      String numberEmoji = Emoji.toEmoji(value);
      int index = 0;
      TdApi.Sticker bestStickerGuess = null;
      for (TdApi.Sticker sticker : stickerSet.stickers) {
        TdApi.Emojis emojis = stickerSet.emojis[index];
        int stickerValue = -1;
        int matchedLanguageLevel = 0;
        for (String diceEmoji : emojis.emojis) {
          if (diceEmoji.equals(languageEmoji)) {
            matchedLanguageLevel = 2;
          } else if (diceEmoji.equals(builtinLanguageEmoji)) {
            matchedLanguageLevel = 1;
          } else if (diceEmoji.equals(ContentPreview.EMOJI_DICE.textRepresentation)) {
            stickerValue = 1;
          } else if (diceEmoji.equals(numberEmoji)) {
            stickerValue = value;
          } else {
            continue;
          }
          if (stickerValue == value) {
            if (matchedLanguageLevel == 2)
              return sticker;
            if (matchedLanguageLevel == 1)
              bestStickerGuess = sticker;
          }
        }
        index++;
      }
      if (bestStickerGuess != null)
        return bestStickerGuess;
    }
    TdApi.Sticker explicitDice = animatedDiceExplicit.find(value);
    if (explicitDice != null)
      return explicitDice;
    if (value != 1)
      return null;
    explicitDice = animatedDiceExplicit.find(Lang.getLanguageEmoji());
    if (explicitDice != null)
      return explicitDice;
    explicitDice = animatedDiceExplicit.find(Lang.getBuiltinLanguageEmoji());
    if (explicitDice != null)
      return explicitDice;
    explicitDice = animatedDiceExplicit.find(ContentPreview.EMOJI_DICE.textRepresentation);
    return explicitDice;
  }

  @Nullable
  public TdApi.DiceStickers findDiceEmoji (String emoji, int value, TdApi.DiceStickers defaultValue) {
    if (ContentPreview.EMOJI_DICE.textRepresentation.equals(emoji)) {
      TdApi.Sticker explicitDice = findExplicitDiceEmoji(value);
      if (explicitDice != null)
        return new TdApi.DiceStickersRegular(explicitDice);
    }
    return defaultValue;
  }

  @Nullable
  public TdApi.Sticker findTgxEmoji (String emoji) {
    return animatedTgxEmoji.find(emoji);
  }

  @TdlibThread
  private void onUpdateHavePendingNotifications (TdApi.UpdateHavePendingNotifications update) {
    boolean havePendingNotifications = update.haveDelayedNotifications || update.haveUnreceivedNotifications;
    if (this.havePendingNotifications != havePendingNotifications) {
      this.havePendingNotifications = havePendingNotifications;
      checkKeepAlive();
    }
    if (!update.haveUnreceivedNotifications) {
      this.notificationConsistencyListeners.notifyConditionChanged(true);
    }
    incrementNotificationReferenceCount();
    notificationManager.releaseTdlibReference(() ->
      notificationListeners.notifyConditionChanged(!havePendingNotifications)
    );
  }

  private long receivedActiveNotificationsTime;
  private boolean receivedActiveNotificationsIgnored;

  @TdlibThread
  private void onUpdateActiveNotifications (TdApi.UpdateActiveNotifications update) {
    TDLib.Tag.notifications(0, accountId, "Received updateActiveNotifications, ignore: %b", ignoreNotificationUpdates);
    if (ignoreNotificationUpdates && update.groups.length > 0) {
      update = new TdApi.UpdateActiveNotifications(new TdApi.NotificationGroup[0]);
      receivedActiveNotificationsIgnored = true;
    } else {
      receivedActiveNotificationsIgnored = false;
    }
    receivedActiveNotificationsTime = SystemClock.uptimeMillis();
    notificationManager.onUpdateActiveNotifications(update, this::dispatchNotificationsInitialized);
  }

  @TdlibThread
  private void onUpdateNotificationGroup (TdApi.UpdateNotificationGroup update) {
    TDLib.Tag.notifications(0, accountId, "Received updateNotificationGroup, groupId: %d, elapsed: %d, ignore: %b", update.notificationGroupId, SystemClock.uptimeMillis() - receivedActiveNotificationsTime, ignoreNotificationUpdates);
    if (!ignoreNotificationUpdates) {
      notificationManager.onUpdateNotificationGroup(update);
    }
  }

  @TdlibThread
  private void onUpdateNotification (TdApi.UpdateNotification update) {
    if (!ignoreNotificationUpdates) {
      notificationManager.onUpdateNotification(update);
    }
  }

  // Updates: MESSAGES

  private final Set<String> sendingMessages = new HashSet<>();

  private void addRemoveSendingMessage (long chatId, long messageId, boolean add) {
    int delta;
    synchronized (sendingMessages) {
      int prevSize = sendingMessages.size();
      String key = chatId + "_" + messageId;
      if (add) {
        sendingMessages.add(key);
      } else {
        sendingMessages.remove(key);
      }
      delta = sendingMessages.size() - prevSize;
    }
    if (delta > 0) {
      for (int i = 0; i < delta; i++) {
        incrementReferenceCount(REFERENCE_TYPE_MESSAGE);
      }
    } else if (delta < 0) {
      for (int i = 0; i < -delta; i++) {
        decrementReferenceCount(REFERENCE_TYPE_MESSAGE);
      }
    }
  }

  private void updateNewMessage (TdApi.UpdateNewMessage update, boolean isUpdate) {
    if (update.message.sendingState instanceof TdApi.MessageSendingStatePending && update.message.content.getConstructor() != TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR) {
      addRemoveSendingMessage(update.message.chatId, update.message.id, true);
      if (isUpdate)
        return;
    }

    listeners.updateNewMessage(update);

    notificationManager.onUpdateNewMessage(update);

    context.global().notifyUpdateNewMessage(this, update);

    if (!isDebugInstance() && update.message.chatId == ChatId.fromUserId(TdConstants.TELEGRAM_ACCOUNT_ID)) {
      listeners.notifySessionListPossiblyChanged(true);
    }
  }

  private void updateMessageSendSucceeded (TdApi.UpdateMessageSendSucceeded update) {
    synchronized (dataLock) {
      Settings.instance().updateScrollMessageId(accountId, update.message.chatId, update.oldMessageId, update.message.id);
    }

    notifyMessageSendCallbacks(update.message.chatId, update.oldMessageId);

    listeners.updateMessageSendSucceeded(update);

    notificationManager.onUpdateMessageSendSucceeded(update);
    quickAckManager.onMessageSendSucceeded(update.message.chatId, update.oldMessageId);

    context.global().notifyUpdateMessageSendSucceeded(this, update);

    cache.addOutputLocationMessage(update.message);

    addRemoveSendingMessage(update.message.chatId, update.oldMessageId, false);
  }

  private void updateMessageSendFailed (TdApi.UpdateMessageSendFailed update) {
    UI.showError(update.error);
    synchronized (dataLock) {
      Settings.instance().updateScrollMessageId(accountId, update.message.chatId, update.oldMessageId, update.message.id);
    }

    listeners.updateMessageSendFailed(update);
    quickAckManager.onMessageSendFailed(update.message.chatId, update.oldMessageId);

    context.global().notifyUpdateMessageSendFailed(this, update);

    addRemoveSendingMessage(update.message.chatId, update.oldMessageId, false);
  }

  void onMessageSendAcknowledged (TdApi.UpdateMessageSendAcknowledged update) {
    notifyMessageSendCallbacks(update.chatId, update.messageId);
    listeners.updateMessageSendAcknowledged(update);
  }

  @TdlibThread
  private void updateMessageContent (TdApi.UpdateMessageContent update) {
    final TdApi.Chat chat;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
    }

    listeners.updateMessageContent(update);

    switch (update.newContent.getConstructor()) {
      case TdApi.MessageLocation.CONSTRUCTOR: {
        cache().updateLiveLocation(update.chatId, update.messageId, (TdApi.MessageLocation) update.newContent);
        break;
      }
      case TdApi.MessagePoll.CONSTRUCTOR: {
        TdApi.Poll poll = ((TdApi.MessagePoll) update.newContent).poll;
        listeners().updatePoll(poll);
        break;
      }
    }
  }

  @TdlibThread
  private void updateMessageEdited (TdApi.UpdateMessageEdited update) {
    listeners.updateMessageEdited(update);
    // TODO notifications per-edit?
  }

  @TdlibThread
  private void updateMessageContentOpened (TdApi.UpdateMessageContentOpened update) {
    listeners.updateMessageContentOpened(update);
  }

  @TdlibThread
  private void updateAnimatedEmojiMessageClicked (TdApi.UpdateAnimatedEmojiMessageClicked update) {
    listeners.updateAnimatedEmojiMessageClicked(update);
  }

  @TdlibThread
  private void updateMessageIsPinned (TdApi.UpdateMessageIsPinned update) {
    listeners.updateMessageIsPinned(update);
  }

  @TdlibThread
  private void updateLiveLocationViewed (TdApi.UpdateMessageLiveLocationViewed update) {
    listeners.updateMessageLiveLocationViewed(update);
    context.liveLocation().requestUpdate();
  }

  @TdlibThread
  private void updateMessageMentionRead (TdApi.UpdateMessageMentionRead update) {
    final boolean counterChanged, availabilityChanged;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      availabilityChanged = (chat.unreadMentionCount > 0) != (update.unreadMentionCount > 0);
      counterChanged = chat.unreadMentionCount != update.unreadMentionCount;
      chat.unreadMentionCount = update.unreadMentionCount;
    }

    listeners.updateMessageMentionRead(update, counterChanged, availabilityChanged);
  }

  @TdlibThread
  private void updateMessageInteractionInfo (TdApi.UpdateMessageInteractionInfo update) {
    listeners.updateMessageInteractionInfo(update);
  }

  @TdlibThread
  private void updateMessageUnreadReactions (TdApi.UpdateMessageUnreadReactions update) {
    final boolean counterChanged, availabilityChanged;
    final TdApi.Chat chat;
    final TdlibChatList[] chatLists;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      availabilityChanged = (chat.unreadReactionCount > 0) != (update.unreadReactionCount > 0);
      counterChanged = chat.unreadReactionCount != update.unreadReactionCount;
      chat.unreadReactionCount = update.unreadReactionCount;
      chatLists = counterChanged || availabilityChanged ? chatListsImpl(chat.positions) : null;
    }


    listeners.updateMessageUnreadReactions(update, counterChanged, availabilityChanged, chat, chatLists);
  }

  @TdlibThread
  private void updateMessagesDeleted (TdApi.UpdateDeleteMessages update) {
    if (update.fromCache) {
      return;
    }

    Arrays.sort(update.messageIds);

    listeners.updateMessagesDeleted(update);

    context.global().notifyUpdateMessagesDeleted(this, update);

    cache.deleteOutputMessages(update.chatId, update.messageIds);
  }

  // Updates: CHATS

  @TdlibThread
  private void updateNewChat (TdApi.UpdateNewChat update) {
    List<TdlibChatList> chatLists;
    synchronized (dataLock) {
      if (Config.TEST_CHAT_COUNTERS) {
        update.chat.unreadCount = MathUtils.random(1, 250000);
      }
      chats.put(update.chat.id, update.chat);
      knownChatIds.add(update.chat.id);
      if (update.chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
        long supergroupId = ((TdApi.ChatTypeSupergroup) update.chat.type).supergroupId;
        if (((TdApi.ChatTypeSupergroup) update.chat.type).isChannel)
          channels.add(supergroupId);
        else
          channels.remove(supergroupId);
      }
      if (update.chat.positions != null && update.chat.positions.length > 0) {
        chatLists = new ArrayList<>(update.chat.positions.length);
        for (TdApi.ChatPosition position : update.chat.positions) {
          if (position.order != 0) {
            chatLists.add(chatListImpl(position.list));
          }
        }
      } else {
        chatLists = null;
      }
    }
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        chatList.onUpdateNewChat(update.chat);
      }
    }
  }

  public void refreshChatState (long chatId) {
    client().send(new TdApi.GetChat(chatId), result -> {
      switch (result.getConstructor()) {
        case TdApi.Chat.CONSTRUCTOR: {
          updateChatState((TdApi.Chat) result);
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.v("Unable to refresh chat state: %s", TD.toErrorString(result));
          break;
        }
      }
    });
  }

  @TdlibThread
  private void updateChatState (TdApi.Chat chat) {
    boolean notificationSettingsChanged;
    synchronized (dataLock) {
      TdApi.Chat existingChat = chats.get(chat.id);
      if (TdlibUtils.assertChat(chat.id, chat)) {
        return;
      }
      existingChat.canBeDeletedForAllUsers = chat.canBeDeletedForAllUsers;
      existingChat.canBeDeletedOnlyForSelf = chat.canBeDeletedOnlyForSelf;
      existingChat.canBeReported = chat.canBeReported;
      notificationSettingsChanged = !existingChat.notificationSettings.useDefaultMuteFor && !chat.notificationSettings.useDefaultMuteFor && existingChat.notificationSettings.muteFor != chat.notificationSettings.muteFor;
    }
    if (notificationSettingsChanged) {
      listeners.updateNotificationSettings(new TdApi.UpdateChatNotificationSettings(chat.id, chat.notificationSettings));
    }
  }

  @TdlibThread
  private void updateChatDefaultDisableNotifications (TdApi.UpdateChatDefaultDisableNotification update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.defaultDisableNotification = update.defaultDisableNotification;
    }
    listeners.updateChatDefaultDisableNotifications(update);
  }

  @TdlibThread
  private void updateChatDefaultMessageSenderId (TdApi.UpdateChatMessageSender update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.messageSenderId = update.messageSenderId;
    }
    listeners.updateChatDefaultMessageSenderId(update);
  }

  @TdlibThread
  private void updateChatUnreadMentionCount (TdApi.UpdateChatUnreadMentionCount update) {
    final boolean availabilityChanged;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      availabilityChanged = (chat.unreadMentionCount > 0) != (update.unreadMentionCount > 0);
      chat.unreadMentionCount = update.unreadMentionCount;
    }
    listeners.updateChatUnreadMentionCount(update, availabilityChanged);
  }

  @TdlibThread
  private void updateChatUnreadReactionCount (TdApi.UpdateChatUnreadReactionCount update) {
    final boolean availabilityChanged;
    final TdApi.Chat chat;
    final TdlibChatList[] chatLists;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      availabilityChanged = (chat.unreadReactionCount > 0) != (update.unreadReactionCount > 0);
      chat.unreadReactionCount = update.unreadReactionCount;
      chatLists = chatListsImpl(chat.positions);
    }
    listeners.updateChatUnreadReactionCount(update, availabilityChanged, chat, chatLists);
  }

  @TdlibThread
  private void updateChatLastMessage (TdApi.UpdateChatLastMessage update) {
    if (Log.isEnabled(Log.TAG_MESSAGES_LOADER)) {
      Log.i(Log.TAG_MESSAGES_LOADER, "updateChatTopMessage chatId=%d messageId=%d", update.chatId, update.lastMessage != null ? update.lastMessage.id : 0);
    }
    List<ChatListChange> listChanges;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.lastMessage = update.lastMessage;
      listChanges = setChatPositions(chat, update.positions);
    }
    listeners.updateChatLastMessage(update, listChanges);
  }

  public static int CHAT_MARKED_AS_UNREAD = -1;
  public static int CHAT_FAILED = -2;
  public static int CHAT_LOADING = -3;

  static class ChatListChange {
    public final TdlibChatList list;
    public final TdApi.Chat chat;
    public final ChatChange change;

    public ChatListChange (TdlibChatList list, TdApi.Chat chat, ChatChange change) {
      this.list = list;
      this.chat = chat;
      this.change = change;
    }
  }

  public static class ChatChange {
    public static final int ORDER = 1;
    public static final int SOURCE = 1 << 1;
    public static final int PIN_STATE = 1 << 2;
    public static final int ALL = ORDER | SOURCE | PIN_STATE;

    public final TdApi.ChatPosition position;
    public final int flags;

    public ChatChange (TdApi.ChatPosition position, int flags) {
      this.position = position;
      this.flags = flags;
    }

    public boolean orderChanged () {
      return BitwiseUtils.hasFlag(flags, ORDER);
    }

    public boolean metadataChanged () {
      return BitwiseUtils.setFlag(flags, ORDER, false) != 0;
    }

    public boolean sourceChanged () {
      return BitwiseUtils.hasFlag(flags, SOURCE);
    }

    public boolean pinStateChanged () {
      return BitwiseUtils.hasFlag(flags, PIN_STATE);
    }
  }

  public static boolean updateChatPosition (TdApi.Chat chat, TdApi.ChatPosition position) {
    return setChatPosition(chat, position) != 0;
  }

  private static int removeChatPosition (TdApi.Chat chat, int existingPosition) {
    int flags = 0;
    TdApi.ChatPosition position = chat.positions[existingPosition];
    if (position.order != 0) {
      flags |= ChatChange.ORDER;
    }
    chat.positions = chat.positions.length == 1 ? new TdApi.ChatPosition[0] : ArrayUtils.removeElement(chat.positions, existingPosition, new TdApi.ChatPosition[chat.positions.length - 1]);
    return flags;
  }

  private static int addChatPosition (TdApi.Chat chat, TdApi.ChatPosition position) {
    if (position.order == 0)
      return 0;
    if (chat.positions != null && chat.positions.length > 0) {
      TdApi.ChatPosition[] positions = new TdApi.ChatPosition[chat.positions.length + 1];
      System.arraycopy(chat.positions, 0, positions, 0, chat.positions.length);
      positions[chat.positions.length] = position;
      chat.positions = positions;
    } else {
      chat.positions = new TdApi.ChatPosition[] {position};
    }
    return ChatChange.ORDER | ChatChange.SOURCE;
  }

  private static int updateOrRemoveChatPosition (TdApi.Chat chat, int existingPosition, TdApi.ChatPosition position) {
    int flags = 0;
    TdApi.ChatPosition prevPosition = chat.positions[existingPosition];
    if (prevPosition.order != position.order)
      flags |= ChatChange.ORDER;
    if (position.order == 0) {
      chat.positions = ArrayUtils.removeElement(chat.positions, existingPosition, new TdApi.ChatPosition[chat.positions.length - 1]);
    } else {
      if (!Td.equalsTo(prevPosition.source, position.source)) {
        flags |= ChatChange.SOURCE;
      }
      if (prevPosition.isPinned != position.isPinned) {
        flags |= ChatChange.PIN_STATE;
      }
      if (flags == 0) {
        return 0;
      }
      chat.positions[existingPosition] = position;
    }
    return flags;
  }

  private static int setChatPosition (TdApi.Chat chat, TdApi.ChatPosition position) {
    int existingPosition = ChatPosition.indexOf(chat.positions, position.list);
    if (existingPosition != -1) {
      return updateOrRemoveChatPosition(chat, existingPosition, position);
    } else {
      return addChatPosition(chat, position);
    }
  }

  private List<ChatListChange> setChatPositions (TdApi.Chat chat, @NonNull TdApi.ChatPosition[] newPositions) {
    List<ChatListChange> changes = null;
    TdApi.ChatPosition[] oldPositions = chat.positions;
    int extraPositionCount = oldPositions.length;
    for (int newPositionIndex = newPositions.length - 1; newPositionIndex >= 0; newPositionIndex--) {
      int flags = 0;
      TdApi.ChatPosition newPosition = newPositions[newPositionIndex];
      int existingIndex = ChatPosition.indexOf(oldPositions, newPosition.list, newPositionIndex);
      if (existingIndex != -1) { // Updated position
        extraPositionCount--;
        TdApi.ChatPosition oldPosition = oldPositions[existingIndex];
        if (oldPosition.order != newPosition.order)
          flags |= ChatChange.ORDER;
        if (!Td.equalsTo(oldPosition.source, newPosition.source))
          flags |= ChatChange.SOURCE;
        if (oldPosition.isPinned != newPosition.isPinned)
          flags |= ChatChange.PIN_STATE;
      } else if (newPosition.order != 0) { // Added position
        flags |= ChatChange.ORDER;
        if (newPosition.source != null)
          flags |= ChatChange.SOURCE;
        if (newPosition.isPinned)
          flags |= ChatChange.PIN_STATE;
      }
      if (flags != 0) {
        if (changes == null)
          changes = new ArrayList<>(newPositionIndex + 1);
        ChatChange positionChange = new ChatChange(newPosition, flags);
        ChatListChange chatListChange = new ChatListChange(
          chatListImpl(newPosition.list),
          chat,
          positionChange
        );
        changes.add(chatListChange);
      }
    }
    if (extraPositionCount > 0) {
      for (int oldPositionIndex = oldPositions.length - 1; oldPositionIndex >= 0; oldPositionIndex--) {
        TdApi.ChatPosition oldPosition = oldPositions[oldPositionIndex];
        int newPositionIndex = ChatPosition.indexOf(newPositions, oldPosition.list);
        if (newPositionIndex == -1) { // Removed position
          if (oldPosition.order != 0) {
            int flags = ChatChange.ORDER;
            if (oldPosition.source != null)
              flags |= ChatChange.SOURCE;
            if (oldPosition.isPinned)
              flags |= ChatChange.PIN_STATE;
            if (changes == null)
              changes = new ArrayList<>(extraPositionCount);
            ChatChange positionChange = new ChatChange(new TdApi.ChatPosition(oldPosition.list, 0, false, null), flags);
            changes.add(new ChatListChange(
              chatListImpl(oldPosition.list),
              chat,
              positionChange
            ));
          }
          if (--extraPositionCount == 0)
            break;
        }
      }
    }
    chat.positions = newPositions;
    return changes;
  }

  @TdlibThread
  private void updateChatPosition (TdApi.UpdateChatPosition update) {
    final ChatListChange chatListChange;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      int changeFlags = setChatPosition(chat, update.position);
      if (changeFlags != 0) {
        TdlibChatList chatList = chatListImpl(update.position.list);
        chatListChange = new ChatListChange(
          chatList,
          chat,
          new ChatChange(update.position, changeFlags)
        );
      } else {
        chatListChange = null;
      }
    }
    if (chatListChange != null) {
      listeners.updateChatPosition(update, chatListChange);
    }
  }

  @TdlibThread
  private void updateChatAvailableReactions (TdApi.UpdateChatAvailableReactions update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.availableReactions = update.availableReactions;
    }
    listeners.updateChatAvailableReactions(update);
  }

  private int mainChatListPosition;
  private TdApi.ChatFolderInfo[] chatFolders = new TdApi.ChatFolderInfo[0];
  private final SparseArrayCompat<TdApi.ChatFolderInfo> chatFoldersById = new SparseArrayCompat<>();

  @TdlibThread
  private void updateChatFolders (TdApi.UpdateChatFolders update) {
    synchronized (dataLock) {
      TdApi.ChatFolderInfo[] chatFolders = update.chatFolders;
      this.chatFolders = chatFolders;
      this.chatFoldersById.clear();
      if (chatFolders != null) {
        for (TdApi.ChatFolderInfo chatFolder : chatFolders) {
          this.chatFoldersById.put(chatFolder.id, chatFolder);
        }
      }
      this.mainChatListPosition = update.mainChatListPosition;
    }
    listeners.updateChatFolders(update);
  }

  @TdlibThread
  private void updateChatPermissions (TdApi.UpdateChatPermissions update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.permissions = update.permissions;
    }

    listeners.updateChatPermissions(update);
  }

  @TdlibThread
  private void updateChatTitle (TdApi.UpdateChatTitle update) {
    final TdApi.Chat chat;
    final TdlibChatList[] chatLists;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.title = update.title;
      chatLists = chatListsImpl(chat.positions);
    }

    listeners.updateChatTitle(update, chat, chatLists);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      long myUserId = myUserId();
      if (myUserId != 0) {
        try {
          TdlibNotificationChannelGroup.updateChat(this, myUserId, chat);
        } catch (TdlibNotificationChannelGroup.ChannelCreationFailureException e) {
          TDLib.Tag.notifications("Unable to update notification channel title for chat %d:\n%s",
            update.chatId,
            Log.toString(e)
          );
          settings().trackNotificationChannelProblem(e, chat.id);
        }
      }
    }
  }

  @TdlibThread
  private void updateChatTheme (TdApi.UpdateChatTheme update) {
    final TdApi.Chat chat;
    final TdlibChatList[] chatLists;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.themeName = update.themeName;
      chatLists = chatListsImpl(chat.positions);
    }
    listeners.updateChatTheme(update, chat, chatLists);
  }

  @TdlibThread
  private void updateChatActionBar (TdApi.UpdateChatActionBar update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.actionBar = update.actionBar;
    }

    listeners.updateChatActionBar(update);
  }

  @TdlibThread
  private void updateChatHasScheduledMessages (TdApi.UpdateChatHasScheduledMessages update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.hasScheduledMessages = update.hasScheduledMessages;
    }

    listeners.updateChatHasScheduledMessages(update);
  }

  @TdlibThread
  private void updateChatHasProtectedContent (TdApi.UpdateChatHasProtectedContent update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.hasProtectedContent = update.hasProtectedContent;
    }

    listeners.updateChatHasProtectedContent(update);
  }

  @TdlibThread
  private void updateChatPhoto (TdApi.UpdateChatPhoto update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.photo = update.photo;
    }

    listeners.updateChatPhoto(update);
  }

  @TdlibThread
  private void updateChatReadInbox (TdApi.UpdateChatReadInbox update) {
    final TdApi.Chat chat;
    final boolean availabilityChanged;
    final TdlibChatList[] chatLists;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.lastReadInboxMessageId = update.lastReadInboxMessageId;
      if (Config.TEST_CHAT_COUNTERS) {
        update.unreadCount = MathUtils.random(1, 250000);
      }
      availabilityChanged = (chat.unreadCount > 0) != (update.unreadCount > 0);
      chat.unreadCount = update.unreadCount;
      chatLists = chatListsImpl(chat.positions);
    }
    listeners.updateChatReadInbox(update, availabilityChanged, chat, chatLists);
  }

  @TdlibThread
  private void updateChatReadOutbox (TdApi.UpdateChatReadOutbox update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.lastReadOutboxMessageId = update.lastReadOutboxMessageId;
    }

    listeners.updateChatReadOutbox(update);
  }

  @TdlibThread
  private void updateChatReplyMarkup (TdApi.UpdateChatReplyMarkup update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.replyMarkupMessageId = update.replyMarkupMessageId;
    }

    listeners.updateChatReplyMarkup(update);
  }

  @TdlibThread
  private void updateChatDraftMessage (TdApi.UpdateChatDraftMessage update) {
    final List<ChatListChange> listChanges;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.draftMessage = update.draftMessage;
      listChanges = setChatPositions(chat, update.positions);
    }
    listeners.updateChatDraftMessage(update, listChanges);
  }

  @TdlibThread
  private void updateUsersNearby (TdApi.UpdateUsersNearby update) {
    listeners.updateUsersNearby(update);
  }

  @TdlibThread
  private void updateChatOnlineMemberCount (TdApi.UpdateChatOnlineMemberCount update) {
    synchronized (dataLock) {
      Integer onlineCountObj = chatOnlineMemberCount.get(update.chatId);
      int count = onlineCountObj != null ? onlineCountObj : 0;
      if (update.onlineMemberCount == count)
        return;
      if (update.onlineMemberCount != 0)
        chatOnlineMemberCount.put(update.chatId, update.onlineMemberCount);
      else
        chatOnlineMemberCount.remove(update.chatId);
    }
    listeners.updateChatOnlineMemberCount(update);
  }

  @TdlibThread
  private void updateChatMessageAutoDeleteTime (TdApi.UpdateChatMessageAutoDeleteTime update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.messageAutoDeleteTime = update.messageAutoDeleteTime;
    }
    listeners.updateChatMessageAutoDeleteTime(update);
  }

  @TdlibThread
  private void updateChatVideoChat (TdApi.UpdateChatVideoChat update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.videoChat = update.videoChat;
    }
    listeners.updateChatVideoChat(update);
  }

  @TdlibThread
  private void updateForumTopicInfo (TdApi.UpdateForumTopicInfo update) {
    String cacheKey = update.chatId + "_" + update.info.messageThreadId;
    synchronized (dataLock) {
      forumTopicInfos.put(cacheKey, update.info);
    }
    listeners.updateForumTopicInfo(update);
  }

  @TdlibThread
  private void updateChatViewAsTopics (TdApi.UpdateChatViewAsTopics update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.viewAsTopics = update.viewAsTopics;
    }
    listeners.updateChatViewAsTopics(update);
  }

  @TdlibThread
  private void updateChatPendingJoinRequests (TdApi.UpdateChatPendingJoinRequests update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.pendingJoinRequests = update.pendingJoinRequests;
    }
    listeners.updateChatPendingJoinRequests(update);
  }

  @TdlibThread
  private void updateChatIsMarkedAsUnread (TdApi.UpdateChatIsMarkedAsUnread update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.isMarkedAsUnread = update.isMarkedAsUnread;
    }

    listeners.updateChatIsMarkedAsUnread(update);
  }

  @TdlibThread
  private void updateChatIsTranslatable (TdApi.UpdateChatIsTranslatable update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.isTranslatable = update.isTranslatable;
    }

    listeners.updateChatIsTranslatable(update);
  }

  @TdlibThread
  private void updateChatIsBlocked (TdApi.UpdateChatBlockList update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.blockList = update.blockList;
    }

    listeners.updateChatBlockList(update);
  }

  // Updates: STORIES

  private final Comparator<TdApi.ChatActiveStories> storiesComparator = (o1, o2) -> {
    if (o1.order != o2.order) {
      return o1.order > o2.order ? -1 : 1;
    }
    if (o1.chatId != o2.chatId) {
      return o1.chatId > o2.chatId ? -1 : 1;
    }
    return 0;
  };

  public Comparator<TdApi.ChatActiveStories> storiesComparator () {
    return storiesComparator;
  }

  @NonNull
  public StoryList getStoryList (@NonNull TdApi.StoryList list) {
    synchronized (dataLock) {
      StoryList storyList = storyLists.get(list.getConstructor());
      if (storyList == null) {
        storyList = new StoryList(this, list);
        storyLists.put(list.getConstructor(), storyList);
      }
      return storyList;
    }
  }

  @Nullable
  public TdApi.ChatActiveStories getActiveStories (long chatId, boolean allowRequest, @Nullable RunnableData<TdApi.ChatActiveStories> onLoaded) {
    synchronized (dataLock) {
      TdApi.ChatActiveStories stories = this.activeStories.get(chatId);
      if (stories != null) {
        return stories;
      }
    }
    if (allowRequest) {
      client().send(new TdApi.GetChatActiveStories(chatId), result -> {
        switch (result.getConstructor()) {
          case TdApi.ChatActiveStories.CONSTRUCTOR: {
            TdApi.ChatActiveStories stories = getActiveStories(chatId, false, null);
            if (stories == null) {
              throw new IllegalStateException();
            }
            if (onLoaded != null) {
              onLoaded.runWithData(stories);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(result);
            break;
          }
        }
      });
    }
    return null;
  }
  
  @TdlibThread
  private void updateStoryListChatCount (TdApi.UpdateStoryListChatCount update) {
    synchronized (dataLock) {
      storyListChatCount.put(update.storyList.getConstructor(), update.chatCount);
    }
    StoryList storyList = getStoryList(update.storyList);
    storyList.notifyApproximateTotalItemCountChanged();
  }

  public int getStoryListChatCount (@NonNull TdApi.StoryList list) {
    synchronized (dataLock) {
      return storyListChatCount.get(list.getConstructor());
    }
  }

  @TdlibThread
  private void updateChatActiveStories (TdApi.UpdateChatActiveStories update) {
    final TdApi.ChatActiveStories prevActiveStories;
    synchronized (dataLock) {
      final long chatId = update.activeStories.chatId;
      prevActiveStories = activeStories.remove(chatId);
      activeStories.put(chatId, update.activeStories);
    }
    listeners.updateChatActiveStories(update);
    boolean wasPresent = prevActiveStories != null && prevActiveStories.stories.length > 0 && prevActiveStories.list != null;
    boolean nowPresent = update.activeStories.stories.length > 0 && update.activeStories.list != null;
    boolean sameList = wasPresent && nowPresent && Td.equalsTo(prevActiveStories.list, update.activeStories.list);
    if (sameList) {
      // Moved or just updated within the same list
      StoryList storyList = getStoryList(update.activeStories.list);
      storyList.moveItem(update.activeStories, prevActiveStories);
    } else {
      if (wasPresent) {
        // Removed from prevActiveStories.list
        StoryList storyList = getStoryList(prevActiveStories.list);
        storyList.removeItem(prevActiveStories);
      }
      if (nowPresent) {
        // Added to update.activeStories.list
        StoryList storyList = getStoryList(update.activeStories.list);
        storyList.addItem(update.activeStories);
      }
    }
  }

  @TdlibThread
  private void updateStory (TdApi.UpdateStory update) {
    listeners.updateStory(update);
  }

  @TdlibThread
  private void updateStoryDeleted (TdApi.UpdateStoryDeleted update) {
    listeners.updateStoryDeleted(update);
  }

  @TdlibThread
  private void updateStorySendSucceeded (TdApi.UpdateStorySendSucceeded update) {
    listeners.updateStorySendSucceeded(update);
  }

  @TdlibThread
  private void updateStorySendFailed (TdApi.UpdateStorySendFailed update) {
    listeners.updateStorySendFailed(update);
  }

  @TdlibThread
  private void updateStoryStealthMode (TdApi.UpdateStoryStealthMode update) {
    synchronized (dataLock) {
      this.storyStealthModeActiveUntilDate = update.activeUntilDate;
      this.storyStealthModeCooldownUntilDate = update.cooldownUntilDate;
    }
    listeners.updateStoryStealthMode(update);
  }

  // Updates: CHAT STATUS

  @TdlibThread
  private void updateChatUserAction (final TdApi.UpdateChatAction update) {
    if (update.chatId != myUserId()) {
      ui().sendMessage(ui().obtainMessage(MSG_ACTION_UPDATE_CHAT_ACTION, 0, 0, update));
    }
  }

  // Updates: CALLS

  private final SparseArrayCompat<TdApi.Call> activeCalls = new SparseArrayCompat<>();
  private boolean haveActiveCalls;

  public boolean haveActiveCalls () {
    return haveActiveCalls;
  }

  private void setHaveActiveCalls (boolean haveActiveCalls) {
    if (this.haveActiveCalls != haveActiveCalls) {
      this.haveActiveCalls = haveActiveCalls;
      if (haveActiveCalls) {
        incrementCallReferenceCount();
      } else {
        decrementCallReferenceCount();
      }
    }
  }

  @TdlibThread
  private void updateCall (TdApi.UpdateCall update) {
    if (TD.isActive(update.call)) {
      activeCalls.put(update.call.id, update.call);
    } else {
      activeCalls.remove(update.call.id);
    }
    setHaveActiveCalls(!activeCalls.isEmpty());

    ui().sendMessage(ui().obtainMessage(MSG_ACTION_UPDATE_CALL, update));
    listeners.updateCall(update);
  }

  @TdlibThread
  private void updateCallSignalingData (TdApi.UpdateNewCallSignalingData update) {
    listeners.updateNewCallSignalingData(update);
  }

  @TdlibThread
  private void updateGroupCall (TdApi.UpdateGroupCall update) {
    listeners.updateGroupCall(update);
  }

  @TdlibThread
  private void updateGroupCallParticipant (TdApi.UpdateGroupCallParticipant update) {
    listeners.updateGroupCallParticipant(update);
  }

  // Updates: LANG PACK

  @TdlibThread
  private void updateLanguagePack (TdApi.UpdateLanguagePackStrings update) {
    if (BuildConfig.LANGUAGE_PACK.equals(update.localizationTarget)) {
      ui().sendMessage(ui().obtainMessage(MSG_ACTION_UPDATE_LANG_PACK, update));
    }
  }

  // Updates: ATTACH MENU BOTS

  @TdlibThread
  private void updateAttachmentMenuBots (TdApi.UpdateAttachmentMenuBots update) {
    // TODO
  }

  @TdlibThread
  private void updateWebAppMessageSent (TdApi.UpdateWebAppMessageSent update) {
    // TODO
  }

  // Updates: NOTIFICATIONS

  @TdlibThread
  private void updateNotificationSettings (TdApi.UpdateChatNotificationSettings update) {
    final long chatId = update.chatId;
    final TdApi.ChatNotificationSettings oldNotificationSettings;
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(chatId);
      if (TdlibUtils.assertChat(chatId, chat, update)) {
        return;
      }
      oldNotificationSettings = chat.notificationSettings;
      chat.notificationSettings = update.notificationSettings;
    }

    listeners.updateNotificationSettings(update);
    notificationManager.onUpdateNotificationSettings(update, chatId, oldNotificationSettings);
  }

  @TdlibThread
  private void updateNotificationSettings (TdApi.UpdateScopeNotificationSettings update) {
    listeners.updateNotificationSettings(update);
    notificationManager.onUpdateNotificationSettings(update);
  }

  @TdlibThread
  private void onUpdateSavedNotificationSounds (TdApi.UpdateSavedNotificationSounds update) {
    // TODO
  }

  // Updates: PRIVACY

  @TdlibThread
  private void updatePrivacySettingRules (TdApi.UpdateUserPrivacySettingRules update) {
    listeners.updatePrivacySettingRules(update.setting, update.rules);
  }

  // Updates: ADD MEMBERS PRIVACY

  @TdlibThread
  private void updateAddChatMembersPrivacyForbidden (TdApi.UpdateAddChatMembersPrivacyForbidden update) {
    // TODO show alert
  }

  // Updates: CHAT ACTION

  @TdlibThread
  private void updateSupergroup (TdApi.UpdateSupergroup update) {
    TdApi.Chat chat;
    synchronized (dataLock) {
      long chatId = ChatId.fromSupergroupId(update.supergroup.id);
      chat = chats.get(chatId);
      if (chat != null) {
        boolean prevIsChannel = ((TdApi.ChatTypeSupergroup) chat.type).isChannel;
        boolean nowIsChannel = update.supergroup.isChannel;
        if (prevIsChannel != nowIsChannel) {
          ((TdApi.ChatTypeSupergroup) chat.type).isChannel = nowIsChannel;
          long supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
          if (nowIsChannel) {
            channels.add(supergroupId);
          } else {
            channels.remove(supergroupId);
          }
        }
      }
    }
    cache.onUpdateSupergroup(update, chat);
  }

  // Updates: SECURITY

  private void updateServiceNotification (TdApi.UpdateServiceNotification update) {
    final TdApi.FormattedText text = Td.textOrCaption(update.content);
    CharSequence msg = TD.toDisplayCharSequence(text);
    if (msg == null)
      return;
    ui().post(() -> {
      ViewController<?> c = UI.getCurrentStackItem();
      if (c != null) {
        if (!StringUtils.isEmpty(update.type) && (update.type.startsWith("AUTH_KEY_DROP") || update.type.startsWith("AUTHKEYDROP"))) {
          c.openAlert(R.string.AppName, msg, Lang.getString(R.string.LogOut), (dialog, which) -> destroy(), ViewController.ALERT_NO_CANCELABLE);
        } else {
          c.openAlert(R.string.AppName, msg);
        }
      }
    });
  }

  @TdlibThread
  private void updateUnconfirmedSession (TdApi.UpdateUnconfirmedSession update) {

  }

  // Updates: FILES

  private void updateFile (TdApi.UpdateFile update) {
    listeners.updateFile(update);

    // TODO

    context.player().onUpdateFile(this, update);

    files().onFileUpdate(update);

    if (update.file.local.isDownloadingActive || update.file.remote.isUploadingActive) {
      files().onFileProgress(update);

      int fileId = update.file.id;
      if (!ImageLoader.instance().onProgress(this, update.file)) {
        if (!GifBridge.instance().onProgress(this, fileId, TD.getFileProgress(update.file))) {
          // Nothing?
        }
      }

      return;
    }

    if (TD.isFileLoaded(update.file)) {
      files().onFileLoaded(update);
      if (!ImageLoader.instance().onLoad(this, update.file)) {
        if (!GifBridge.instance().onLoad(this, update.file)) {
          // Nothing?
        }
      }
    } else {
      files().onFileUpdated(update);
    }
  }

  @TdlibThread
  private void updateFileAddedToDownloads (TdApi.UpdateFileAddedToDownloads update) {
    listeners.updateFileAddedToDownloads(update);
  }

  @TdlibThread
  private void updateFileDownload (TdApi.UpdateFileDownload update) {
    listeners.updateFileDownload(update);
  }

  @TdlibThread
  private void updateFileDownloads (TdApi.UpdateFileDownloads update) {
    listeners.updateFileDownloads(update);
  }

  @TdlibThread
  private void updateFileRemovedFromDownloads (TdApi.UpdateFileRemovedFromDownloads update) {
    listeners.updateFileRemovedFromDownloads(update);
  }

  // Updates: CONFIG

  @TdlibThread
  private void updateConnectionState (TdApi.UpdateConnectionState update) {
    final int state;
    switch (update.state.getConstructor()) {
      case TdApi.ConnectionStateWaitingForNetwork.CONSTRUCTOR:
        state = ConnectionState.WAITING_FOR_NETWORK;
        break;
      case TdApi.ConnectionStateConnectingToProxy.CONSTRUCTOR:
        state = ConnectionState.CONNECTING_TO_PROXY;
        break;
      case TdApi.ConnectionStateConnecting.CONSTRUCTOR:
        state = ConnectionState.CONNECTING;
        break;
      case TdApi.ConnectionStateUpdating.CONSTRUCTOR:
        state = ConnectionState.UPDATING;
        break;
      case TdApi.ConnectionStateReady.CONSTRUCTOR:
        state = ConnectionState.CONNECTED;
        break;
      default:
        Td.assertConnectionState_963d6b5f();
        throw Td.unsupported(update.state);
    }

    if (this.connectionState != state) {
      int prevState = this.connectionState;
      this.connectionState = state;
      if (state == ConnectionState.CONNECTED || state == ConnectionState.WAITING_FOR_NETWORK) {
        final long connectionLossTime = this.connectionLossTime;
        this.connectionLossTime = 0;
        final int proxyId = effectiveProxyId;
        cancelConnectionResolver();
        if (state == ConnectionState.CONNECTED && connectionLossTime != 0 && proxyId != Settings.PROXY_ID_UNKNOWN) {
          long connectedWithinMs = SystemClock.uptimeMillis() - connectionLossTime;
          Settings.instance().trackSuccessfulConnection(
            proxyId,
            currentTimeMillis(),
            connectedWithinMs,
            false
          );
        }
      } else {
        if (connectionLossTime == 0 || connectionResolver == null) {
          scheduleConnectionResolver();
        }
        if (connectionLossTime == 0) {
          connectionLossTime = SystemClock.uptimeMillis();
        }
      }
      listeners.updateConnectionState(state, prevState);
      context.onConnectionStateChanged(this, state);
      notifyConnectionDisplayStatusChanged();
      if (state == ConnectionState.CONNECTED) {
        onConnected();
      } else if (prevState == ConnectionState.CONNECTED) {
        context().watchDog().helpDogeIfInBackground();
      }
    }
  }
  private void notifyConnectionDisplayStatusChanged () {
    listeners.updateConnectionDisplayStatusChanged();
    context.onConnectionDisplayStatusChanged(this);
  }
  private CancellableRunnable connectionResolver;

  private static final double CONNECTION_TIMEOUT_PROXY = 5.0;
  private static final double CONNECTION_TIMEOUT_DIRECT = 5.0;
  private static final double CONNECTION_TIMEOUT_DIRECT_CENSORED = 9.0;

  @TdlibThread
  private void scheduleConnectionResolver () {
    cancelConnectionResolver();
    connectionResolver = new CancellableRunnable() {
      @Override
      public void act () {
        resolveConnectionIssues(false);
      }
    };
    double timeoutSeconds;
    if (effectiveProxyId == Settings.PROXY_ID_NONE) {
      timeoutSeconds = expectBlocking ? CONNECTION_TIMEOUT_DIRECT_CENSORED : CONNECTION_TIMEOUT_DIRECT;
    } else {
      timeoutSeconds = CONNECTION_TIMEOUT_PROXY;
    }
    runOnTdlibThread(connectionResolver, timeoutSeconds, false);
  }

  @TdlibThread
  private void cancelConnectionResolver () {
    if (routeSelector != null) {
      routeSelector.cancel();
      routeSelector = null;
    }
    if (connectionResolver != null) {
      connectionResolver.cancel();
      connectionResolver = null;
    }
  }

  public void resolveConnectionIssues () {
    runOnTdlibThread(() -> resolveConnectionIssues(true));
  }

  private TdlibRouteSelector routeSelector;

  @TdlibThread
  private void resolveConnectionIssues (boolean byUserRequest) {
    Settings settings = Settings.instance();
    if ((!byUserRequest && !settings.checkProxySetting(Settings.PROXY_FLAG_SWITCH_AUTOMATICALLY)) || !settings.hasProxyConfiguration()) {
      return;
    }
    if (routeSelector != null && !byUserRequest) {
      routeSelector.markAsFailed(effectiveProxyId);
    }
    TdlibRouteSelector routeSelector;
    if (this.routeSelector == null || this.routeSelector.isEmpty()) {
      this.routeSelector = routeSelector = new TdlibRouteSelector(this,
        settings.checkProxySetting(Settings.PROXY_FLAG_SWITCH_ALLOW_DIRECT),
        !byUserRequest ? effectiveProxyId : Settings.PROXY_ID_UNKNOWN
      );
    } else {
      routeSelector = this.routeSelector;
    }
    if (routeSelector.isEmpty()) {
      notifyConnectionDisplayStatusChanged();
      return;
    }
    routeSelector.findBestRoute(suggestedProxy -> {
      if (!isConnectingOrUpdating()) {
        return;
      }
      if (suggestedProxy == null) {
        // dispatch "No better route, connecting…"
        notifyConnectionDisplayStatusChanged();
        return;
      }
      ui().post(() -> {
        if (!routeSelector.isCanceled() && isConnectingOrUpdating()) {
          routeSelector.markAsPending(suggestedProxy.id);
          if (suggestedProxy.isDirect()) {
            Settings.instance().disableProxy();
          } else {
            Settings.instance().addOrUpdateProxy(suggestedProxy.proxy, suggestedProxy.description, true, suggestedProxy.id);
          }
          // dispatch "Trying better route…"
          runOnTdlibThread(this::notifyConnectionDisplayStatusChanged);
        }
      });
    });
    // dispatch "Finding better route…"
    notifyConnectionDisplayStatusChanged();
  }

  public long calculateConnectionTimeoutMs (long timeoutMs) {
    long time = timeSinceFirstConnectionAttemptMs();
    return time != 0 ? Math.max(0, timeoutMs - time) : timeoutMs;
  }

  /**
   * @return Zero if connection is established or network is unavailable and amount of milliseconds otherwise.
   */
  public long timeSinceFirstConnectionAttemptMs () {
    long time = connectionLossTime;
    return time != 0 ? SystemClock.uptimeMillis() - time : 0;
  }

  @TdlibThread
  private void onUpdateMyUserId (long myUserId) {
    context.onKnownUserIdChanged(accountId, myUserId);
    cache().onUpdateMyUserId(myUserId);
    notificationManager.onUpdateMyUserId(myUserId);
  }

  @TdlibThread
  private void updateUnreadMessageCount (TdApi.UpdateUnreadMessageCount update) {
    if (setUnreadCounters(update.chatList, update.unreadCount, update.unreadUnmutedCount) && tempCounter.reset(this)) {
      ui().sendMessage(ui().obtainMessage(MSG_ACTION_DISPATCH_UNREAD_COUNTER, tempCounter.getCount(), tempCounter.isMuted() ? 1 : 0, update.chatList));
    }
  }

  @TdlibThread
  private void updateUnreadChatCount (TdApi.UpdateUnreadChatCount update) {
    if (setUnreadChatCounters(update.chatList, update.totalCount, update.unreadCount, update.unreadUnmutedCount, update.markedAsUnreadCount, update.markedAsUnreadUnmutedCount) && tempCounter.reset(this)) {
      ui().sendMessage(ui().obtainMessage(MSG_ACTION_DISPATCH_UNREAD_COUNTER, tempCounter.getCount(), tempCounter.isMuted() ? 1 : 0, update.chatList));
    }
  }

  @TdlibThread
  private void updateTermsOfService (TdApi.UpdateTermsOfService update) {
    ui().sendMessage(ui().obtainMessage(MSG_ACTION_DISPATCH_TERMS_OF_SERVICE, update));
  }

  @TdlibThread
  private void updateAutosaveSettings (TdApi.UpdateAutosaveSettings update) {
    // TODO?
  }

  private final List<TdApi.SuggestedAction> suggestedActions = new ArrayList<>();

  @TdlibThread
  private void updateSuggestedActions (TdApi.UpdateSuggestedActions update) {
    synchronized (dataLock) {
      for (TdApi.SuggestedAction removedAction : update.removedActions) {
        for (int i = suggestedActions.size() - 1; i >= 0; i--) {
          if (suggestedActions.get(i).getConstructor() == removedAction.getConstructor()) {
            suggestedActions.remove(i);
          }
        }
      }
      Collections.addAll(suggestedActions, update.addedActions);
    }
    listeners().updateSuggestedActions(update);
    context().global().notifyResolvableProblemAvailabilityMightHaveChanged();
  }

  @AnyThread
  public TdApi.SuggestedAction[] getSuggestedActions () {
    synchronized (dataLock) {
      return suggestedActions.toArray(new TdApi.SuggestedAction[0]);
    }
  }

  private final Map<String, TdApi.ChatTheme> chatThemes = new HashMap<>();

  @TdlibThread
  private void updateChatThemes (TdApi.UpdateChatThemes update) {
    synchronized (dataLock) {
      chatThemes.clear();
      for (TdApi.ChatTheme theme : update.chatThemes) {
        chatThemes.put(theme.name, theme);
      }
    }
  }

  @TdlibThread
  private void updateChatBackground (TdApi.UpdateChatBackground update) {
    final TdApi.Chat chat;
    synchronized (dataLock) {
      chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.background = update.background;
    }

    listeners.updateChatBackground(update);
  }

  private <T extends TdApi.Update> void updateChat (T update, long chatId, RunnableData<TdApi.Chat> chatModifier, RunnableData<T> updateDispatcher) {
    final TdApi.Chat chat;
    synchronized (dataLock) {
      chat = chats.get(chatId);
      if (TdlibUtils.assertChat(chatId, chat, update)) {
        return;
      }
      if (chatModifier != null) {
        chatModifier.runWithData(chat);
      }
    }
    if (updateDispatcher != null) {
      updateDispatcher.runWithData(update);
    }
  }

  @TdlibThread
  private void updateChatAccentColors (TdApi.UpdateChatAccentColors update) {
    updateChat(update, update.chatId, chat -> {
        chat.accentColorId = update.accentColorId;
        chat.backgroundCustomEmojiId = update.backgroundCustomEmojiId;
        chat.profileAccentColorId = update.profileAccentColorId;
        chat.profileBackgroundCustomEmojiId = update.profileBackgroundCustomEmojiId;
      },
      listeners::updateChatAccentColors
    );
  }

  @TdlibThread
  private void updateChatEmojiStatus (TdApi.UpdateChatEmojiStatus update) {
    updateChat(update, update.chatId, chat ->
      chat.emojiStatus = update.emojiStatus,
      listeners::updateChatEmojiStatus
    );
  }

  @AnyThread
  public @Nullable TdApi.ChatTheme chatTheme (String themeName) {
    synchronized (dataLock) {
      return chatThemes.get(themeName);
    }
  }

  @TdlibThread
  private boolean setUnreadCounters (@NonNull TdApi.ChatList chatList, int unreadMessageCount, int unreadUnmutedCount) {
    TdlibCounter counter = getCounter(chatList);
    if (counter.messageCount == unreadMessageCount && counter.messageUnmutedCount == unreadUnmutedCount) {
      return false;
    }

    int oldUnreadCount = Math.max(counter.messageCount, 0);
    int oldUnreadUnmutedCount = Math.max(counter.messageUnmutedCount, 0);

    counter.messageCount = unreadMessageCount;
    counter.messageUnmutedCount = unreadUnmutedCount;

    account().storeCounter(chatList, counter, false);
    context.incrementBadgeCounters(chatList, unreadMessageCount - oldUnreadCount, unreadUnmutedCount - oldUnreadUnmutedCount, false);
    listeners().notifyMessageCountersChanged(chatList, counter, unreadMessageCount, unreadUnmutedCount);

    return true;
  }

  @UiThread
  private void dispatchUnreadCounters (@NonNull TdApi.ChatList chatList, int count, boolean isMuted) {
    context.global().notifyCountersChanged(this, chatList, count, isMuted);
  }

  @TdlibThread
  private boolean setUnreadChatCounters (@NonNull TdApi.ChatList chatList, int totalCount, int unreadChatCount, int unreadUnmutedCount, int markedAsUnreadCount, int markedAsUnreadUnmutedCount) {
    TdlibCounter counter = getCounter(chatList);

    int oldUnreadCount = Math.max(counter.chatCount, 0);
    int oldUnreadUnmutedCount = Math.max(counter.chatUnmutedCount, 0);
    int oldTotalChatCount = counter.totalChatCount;

    if (counter.setChatCounters(totalCount, unreadChatCount, unreadUnmutedCount, markedAsUnreadCount, markedAsUnreadUnmutedCount)) {
      account().storeCounter(chatList, counter, true);
      context.incrementBadgeCounters(chatList, unreadChatCount - oldUnreadCount, unreadUnmutedCount - oldUnreadUnmutedCount, true);
      listeners().notifyChatCountersChanged(chatList, counter, (totalCount > 0) != (oldTotalChatCount > 0), totalCount, unreadChatCount, unreadUnmutedCount);
      return true;
    }

    return false;
  }

  @AnyThread
  TdlibBadgeCounter getUnreadBadge () {
    unreadCounter.reset(this);
    return unreadCounter;
  }

  @AnyThread
  public TdlibCounter getMainCounter () {
    return getCounter(ChatPosition.CHAT_LIST_MAIN);
  }

  @AnyThread
  public boolean hasArchivedChats () {
    return getTotalChatsCount(ChatPosition.CHAT_LIST_ARCHIVE) > 0;
  }

  @AnyThread
  public boolean hasUnreadChats (@NonNull TdApi.ChatList chatList) {
    return getCounter(chatList).chatCount > 0;
  }

  @AnyThread
  public int getTotalChatsCount (@NonNull TdApi.ChatList chatList) {
    synchronized (dataLock) {
      TdlibCounter counter = getCounter(chatList);
      return counter.totalChatCount;
    }
  }

  @AnyThread
  public int getUnreadBadgeCount () {
    return getUnreadBadge().getCount();
  }

  @TdlibThread
  private void updateSpeechRecognitionTrial (TdApi.UpdateSpeechRecognitionTrial update) {
    // TODO
  }

  @TdlibThread
  private void updateDefaultBackground (TdApi.UpdateDefaultBackground update) {
    // TODO ?
  }

  @TdlibThread
  private void updateOption (ClientHolder context, TdApi.UpdateOption update) {
    final String name = update.name;

    if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
      switch (update.value.getConstructor()) {
        case TdApi.OptionValueEmpty.CONSTRUCTOR: {
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionEmpty %s", name);
          break;
        }
        case TdApi.OptionValueInteger.CONSTRUCTOR: {
          long value = Td.longValue(update.value);
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionInteger %s -> %d", name, value);
          break;
        }
        case TdApi.OptionValueString.CONSTRUCTOR: {
          String value = Td.stringValue(update.value);
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionString %s -> %s", name, value);
          break;
        }
        case TdApi.OptionValueBoolean.CONSTRUCTOR: {
          boolean value = Td.boolValue(update.value);
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionString %s -> %s", name, value);
          break;
        }
      }
    }

    if (!name.isEmpty() && name.charAt(0) == 'x') {
      // TGSettingsManager.instance().onUpdateOption(name, update.value);
      return;
    }

    switch (name) {
      // Service

      case "version":
        context().setTdlibVersion(Td.stringValue(update.value));
        break;
      case "commit_hash":
        context().setTdlibCommitHash(Td.stringValue(update.value));
        break;
      case "unix_time": {
        final long receivedTime = SystemClock.elapsedRealtime();
        final long value =  Td.longValue(update.value);
        synchronized (dataLock) {
          this.unixTime = value;
          this.unixTimeReceived = receivedTime;
        }
        break;
      }
      case "utc_time_offset":
        this.utcTimeOffset = Td.longValue(update.value);
        break;
      case "test_mode":
        break;

      // Auth

      case "my_id":
        onUpdateMyUserId(Td.longValue(update.value));
        break;
      case "authorization_date":
        this.authorizationDate = Td.longValue(update.value);
        break;
      case "authentication_token": {
        final String token = Td.stringValue(update.value);
        if (!StringUtils.isEmpty(token)) {
          Settings.instance().trackAuthenticationToken(token);
        }
        break;
      }

      // Configuration

      case "call_connect_timeout_ms":
        this.callConnectTimeoutMs = Td.longValue(update.value);
        break;
      case "call_packet_timeout_ms":
        this.callPacketTimeoutMs = Td.longValue(update.value);
        break;
      case "suggested_video_note_video_bitrate":
        this.roundVideoBitrate = Td.longValue(update.value);
        break;
      case "suggested_video_note_audio_bitrate":
        this.roundAudioBitrate = Td.longValue(update.value);
        break;
      case "suggested_video_note_length":
        this.roundVideoDiameter = Td.longValue(update.value);
        break;

      // Telegram Premium

      case "is_premium":
        this.isPremium = Td.boolValue(update.value);
        break;
      case "is_premium_available":
        this.isPremiumAvailable = Td.boolValue(update.value);
        break;
      case "gift_premium_from_attachment_menu":
        this.giftPremiumOptions = BitwiseUtils.setFlag(this.giftPremiumOptions, GiftPremiumOption.FROM_ATTACHMENT_MENU, Td.boolValue(update.value));
        break;
      case "gift_premium_from_input_field":
        this.giftPremiumOptions = BitwiseUtils.setFlag(this.giftPremiumOptions, GiftPremiumOption.FROM_INPUT_FIELD, Td.boolValue(update.value));
        break;

      // Constants

      case "t_me_url":
        this.tMeUrl = Td.stringValue(update.value);
        break;
      case "animation_search_bot_username":
        this.animationSearchBotUsername = Td.stringValue(update.value);
        break;
      case "venue_search_bot_username":
        this.venueSearchBotUsername = Td.stringValue(update.value);
        break;
      case "photo_search_bot_username":
        this.photoSearchBotUsername = Td.stringValue(update.value);
        break;

      // Limits

      case "basic_group_size_max":
        this.groupMaxSize = Td.intValue(update.value);
        break;
      case "supergroup_size_max":
        this.supergroupMaxSize = Td.intValue(update.value);
        break;

      case "pinned_chat_count_max":
        this.pinnedChatsMaxCount = Td.intValue(update.value);
        break;
      case "pinned_archived_chat_count_max":
        this.pinnedArchivedChatsMaxCount = Td.intValue(update.value);
        break;
      case "pinned_forum_topic_count_max":
        this.pinnedForumTopicMaxCount = Td.intValue(update.value);
        break;

      case "message_text_length_max":
        this.maxMessageTextLength = Td.intValue(update.value);
        break;
      case "message_caption_length_max":
        this.maxMessageCaptionLength = Td.intValue(update.value);
        break;

      case "forwarded_message_count_max":
        this.forwardMaxCount = Td.intValue(update.value);
        break;

      case "chat_folder_count_max":
        this.chatFolderMaxCount = Td.intValue(update.value);
        break;
      case "chat_folder_chosen_chat_count_max":
        this.folderChosenChatMaxCount = Td.intValue(update.value);
        break;
      case "added_shareable_chat_folder_count_max":
        this.addedShareableChatFolderMaxCount = Td.intValue(update.value);
        break;
      case "chat_folder_invite_link_count_max":
        this.chatFolderInviteLinkMaxCount = Td.intValue(update.value);
        break;
      case "chat_folder_new_chats_update_period":
        this.chatFolderUpdatePeriod = Td.longValue(update.value);
        break;

      case "favorite_stickers_limit":
        this.favoriteStickersMaxCount = Td.intValue(update.value);
        break;
      case "bio_length_max":
        this.maxBioLength = Td.intValue(update.value);
        break;

      case "active_story_count_max":
        this.activeStoryCountMax = Td.intValue(update.value);
        break;
      case "weekly_sent_story_count_max":
        this.weeklySentStoryCountMax = Td.intValue(update.value);
        break;
      case "monthly_sent_story_count_max":
        this.monthlySentStoryCountMax = Td.intValue(update.value);
        break;
      case "can_use_text_entities_in_story_caption":
        this.canUseTextEntitiesInStoryCaptions = Td.boolValue(update.value);
        break;
      case "story_caption_length_max":
        this.storyCaptionLengthMax = Td.intValue(update.value);
        break;
      case "story_suggested_reaction_area_count_max":
        this.storySuggestedReactionAreaCountMax = Td.intValue(update.value);
        break;
      case "story_viewers_expiration_delay":
        this.storyViewersExpirationDelay = Td.intValue(update.value);
        break;
      case "story_stealth_mode_cooldown_period":
        this.storyStealhModeCooldownPeriod = Td.intValue(update.value);
        break;
      case "story_stealth_mode_future_period":
        this.storyStealthModeFuturePeriod = Td.intValue(update.value);
        break;
      case "story_stealth_mode_past_period":
        this.storyStealthModePastPeriod = Td.intValue(update.value);
        break;

      // Service accounts and chats

      case "anti_spam_bot_user_id":
        this.antiSpamBotUserId = Td.longValue(update.value);
        break;
      case "channel_bot_user_id":
        this.channelBotUserId = Td.longValue(update.value);
        break;
      case "group_anonymous_bot_user_id":
        this.groupAnonymousBotUserId = Td.longValue(update.value);
        break;
      case "replies_bot_user_id":
        this.repliesBotUserId = Td.longValue(update.value);
        break;
      case "replies_bot_chat_id":
        this.repliesBotChatId = Td.longValue(update.value);
        break;
      case "telegram_service_notifications_chat_id":
        this.telegramServiceNotificationsChatId = Td.longValue(update.value);
        break;

      // Settings

      case "expect_blocking":
        this.expectBlocking = Td.boolValue(update.value);
        break;
      case "calls_enabled":
        this.callsEnabled = Td.boolValue(update.value);
        break;
      case "is_location_visible":
        this.isLocationVisible = Td.boolValue(update.value);
        break;
      case "can_ignore_sensitive_content_restrictions":
        this.canIgnoreSensitiveContentRestrictions = Td.boolValue(update.value);
        break;
      case "ignore_sensitive_content_restrictions":
        this.ignoreSensitiveContentRestrictions = Td.boolValue(update.value);
        break;
      case "can_archive_and_mute_new_chats_from_unknown_users":
        this.canArchiveAndMuteNewChatsFromUnknownUsers = Td.boolValue(update.value);
        break;
      case "disable_top_chats":
        setDisableTopChatsImpl(Td.boolValue(update.value));
        break;
      case "disable_contact_registered_notifications":
        setDisableContactRegisteredNotificationsImpl(Td.boolValue(update.value));
        break;
      case "disable_sent_scheduled_message_notifications":
        setDisableSentScheduledMessageNotificationsImpl(Td.boolValue(update.value));
        break;

      // Language

      case "language_pack_id":
        setLanguagePackIdImpl(Td.stringValue(update.value), false);
        break;
      case "suggested_language_pack_id": {
        final String languagePackId = Td.stringValue(update.value);
        if (this.suggestedLanguagePackId == null || !this.suggestedLanguagePackId.equals(languagePackId)) {
          this.suggestedLanguagePackId = languagePackId;
          this.suggestedLanguagePackInfo = null;
          listeners().updateSuggestedLanguageChanged(languagePackId, null);
          send(context.client, new TdApi.GetLanguagePackInfo(languagePackId), (languagePackInfo, error) -> {
            if (error != null) {
              Log.e("Failed to fetch suggested language, code: %s %s", languagePackId, TD.toErrorString(error));
              setSuggestedLanguagePackInfo(languagePackId, null);
            } else {
              setSuggestedLanguagePackInfo(languagePackId, languagePackInfo);
            }
          });
        }
        break;
      }

      // Ignored & Unknown

      case "notification_sound_count_max":
      case "notification_sound_duration_max":
      case "notification_sound_size_max":
      case "localization_target":
      case "language_pack_database_path":
      case "ignore_file_names":
      case "ignore_platform_restrictions":
      case "is_emulator":
      case "use_storage_optimizer":
      case "storage_max_files_size":
      case "use_pfs":
      case "use_quick_ack":
      case "connection_parameters":
      case "notification_group_count_max":
      case "notification_group_size_max":
      case "ignore_default_disable_notification":
        break;

      default: {
        if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
          Log.w(Log.TAG_TDLIB_OPTIONS, "Unknown TDLib option: %s %s", name, update.value);
        }
        break;
      }
    }
  }

  // Updates: Accent colors

  private int[] availableAccentColorIds;
  private final SparseArrayCompat<TdlibAccentColor> accentColors = new SparseArrayCompat<>();

  @TdlibThread
  private void updateAccentColors (TdApi.UpdateAccentColors update) {
    boolean listChanged;
    int updatedColorsCount = 0;
    synchronized (accentColors) {
      listChanged = !Arrays.equals(this.availableAccentColorIds, update.availableAccentColorIds);
      this.availableAccentColorIds = update.availableAccentColorIds;
      for (TdApi.AccentColor newColor : update.colors) {
        TdlibAccentColor oldColor = accentColors.get(newColor.id);
        if (oldColor == null) {
          accentColors.put(newColor.id, new TdlibAccentColor(newColor));
        }
        if (oldColor == null || oldColor.updateColor(newColor)) {
          updatedColorsCount++;
        }
      }
    }
    if (listChanged || updatedColorsCount > 0) {
      listeners.updateAccentColors(update);
    }
  }

  @NonNull
  public TdlibAccentColor accentColor (int accentColorId) {
    synchronized (accentColors) {
      TdlibAccentColor color = accentColors.get(accentColorId);
      if (color == null) {
        color = new TdlibAccentColor(accentColorId);
        accentColors.put(accentColorId, color);
      }
      return color;
    }
  }

  @NonNull
  public TdlibAccentColor accentColorForString (String any) {
    return accentColor(MathUtils.pickNumber(TdlibAccentColor.BUILT_IN_COLOR_COUNT, any));
  }

  private int[] availableProfileAccentColorIds;
  private final SparseArrayCompat<TdApi.ProfileAccentColor> profileAccentColors = new SparseArrayCompat<>();

  @TdlibThread
  private void updateProfileAccentColors (TdApi.UpdateProfileAccentColors update) {
    boolean listChanged;
    synchronized (profileAccentColors) {
      listChanged = Arrays.equals(this.availableProfileAccentColorIds, update.availableAccentColorIds);
      this.availableProfileAccentColorIds = update.availableAccentColorIds;
      for (TdApi.ProfileAccentColor profileAccentColor : update.colors) {
        profileAccentColors.put(profileAccentColor.id, profileAccentColor);
      }
    }
    listeners.updateProfileAccentColors(update, listChanged);
  }

  // Updates: MEDIA

  private void updateAnimationSearchParameters (TdApi.UpdateAnimationSearchParameters update) {
    // TODO
  }

  private void updateInstalledStickerSets (TdApi.UpdateInstalledStickerSets update) {
    listeners.updateInstalledStickerSets(update);
  }

  private void updateFavoriteStickers (TdApi.UpdateFavoriteStickers update) {
    synchronized (dataLock) {
      this.favoriteStickerIds = update.stickerIds;
    }
    listeners.updateFavoriteStickers(update);
  }

  private void updateRecentStickers (TdApi.UpdateRecentStickers update) {
    listeners.updateRecentStickers(update);
  }

  private void updateTrendingStickerSets (TdApi.UpdateTrendingStickerSets update) {
    final int unreadCount;
    synchronized (dataLock) {
      unreadCount = this.unreadTrendingStickerSetsCount = TD.calculateUnreadStickerSetCount(update.stickerSets);
    }
    listeners.updateTrendingStickerSets(update, unreadCount);
  }

  private void updateSavedAnimations (TdApi.UpdateSavedAnimations update) {
    listeners.updateSavedAnimations(update);
  }

  private void updateDiceEmoji (String[] emoji) {
    synchronized (dataLock) {
      this.diceEmoji = emoji;
    }
  }

  @TdlibThread
  private void updateActiveEmojiReactions (TdApi.UpdateActiveEmojiReactions update) {
    synchronized (dataLock) {
      this.activeEmojiReactions = update.emojis;
    }
  }

  @TdlibThread
  private void updateDefaultReactionType (TdApi.UpdateDefaultReactionType update) {
    synchronized (dataLock) {
      this.defaultReactionType = update.reactionType;
    }
  }

  /*private void updateReactions (TdApi.UpdateReactions update) {
    synchronized (dataLock) {
      HashMap<String, TGReaction> supportedTGReactionsMap = new HashMap<>();
      ArrayList<TGReaction> notPremiumReactions = new ArrayList<>();
      ArrayList<TGReaction> onlyPremiumReactions = new ArrayList<>();

      for (int a = 0; a < update.reactions.length; a++) {
        TdApi.Reaction reaction = update.reactions[a];
        TGReaction tgReaction = new TGReaction(this, reaction);
        supportedTGReactionsMap.put(reaction.reaction, tgReaction);
        if (reaction.isActive) {
          if (reaction.isPremium) {
            onlyPremiumReactions.add(tgReaction);
          } else {
            notPremiumReactions.add(tgReaction);
          }
        }
      }

      this.supportedTGReactionsMap = supportedTGReactionsMap;
      this.notPremiumReactions = notPremiumReactions;
      this.onlyPremiumReactions = onlyPremiumReactions;
    }
  }*/

  private void updateStickerSet (TdApi.StickerSet stickerSet) {
    animatedTgxEmoji.update(this, stickerSet);
    animatedDiceExplicit.update(this, stickerSet);
    listeners.updateStickerSet(stickerSet);
  }

  // Filegen

  private void updateFileGenerationStart (TdApi.UpdateFileGenerationStart update) {
    synchronized (awaitingGenerations) {
      Generation generation = awaitingGenerations.remove(update.conversion);
      if (generation != null) {
        generation.isPending = true;
        generation.generationId = update.generationId;
        generation.destinationPath = update.destinationPath;
        pendingGenerations.put(update.generationId, generation);
        generation.latch.countDown();
        return;
      }
    }
    filegen().updateFileGenerationStart(update);
  }

  private void updateFileGenerationStop (TdApi.UpdateFileGenerationStop update) {
    synchronized (awaitingGenerations) {
      Generation generation = pendingGenerations.remove(update.generationId);
      if (generation != null) {
        if (generation.onCancel != null) {
          generation.onCancel.run();
        }
        return;
      }
    }
    filegen().updateFileGenerationStop(update);
  }

  // Updates: ENTRY

  /*public void doFake () {
    // Captain Raptor =
    // Lucky = 472280754

    TdApi.Chat captainRaptor = chat(73083932);
    TdApi.Chat lucky = chat(472280754);
    TdApi.Chat kepler = chat(636750);
    TdApi.Chat sajil = chat(47518346);
    TdApi.Chat nepho = chat(213963390);


    if (captainRaptor == null || lucky == null || kepler == null || sajil == null || nepho == null) {
      return;
    }

    sendFakeUpdate(new TdApi.UpdateUserChatAction(captainRaptor.id, (int) captainRaptor.id, new TdApi.ChatActionTyping()), false);
    sendFakeUpdate(new TdApi.UpdateUserChatAction(lucky.id, (int) lucky.id, new TdApi.ChatActionRecordingVoiceNote()), false);
    sendFakeUpdate(new TdApi.UpdateUserChatAction(kepler.id, (int) kepler.id, new TdApi.ChatActionRecordingVideoNote()), false);
    sendFakeUpdate(new TdApi.UpdateUserChatAction(nepho.id, (int) nepho.id, new TdApi.ChatActionStartPlayingGame()), false);
    int stepsCount = 8;
    int factorPerStep = 100 / stepsCount;
    long delay = 300;
    for (int i = 0; i <= stepsCount; i++) {
      final int progress = i * factorPerStep;
      if (i == 0) {
        sendFakeUpdate(new TdApi.UpdateUserChatAction(sajil.id, (int) sajil.id, new TdApi.ChatActionUploadingPhoto()), false);
      } else {
        UI.post(new Runnable() {
          @Override
          public void run () {
            sendFakeUpdate(new TdApi.UpdateUserChatAction(sajil.id, (int) sajil.id, new TdApi.ChatActionUploadingPhoto(progress)), false);
          }
        }, delay * i);
        if (i == stepsCount) {
          UI.post(new Runnable() {
            @Override
            public void run () {
              sendFakeMessage(captainRaptor, new TdApi.MessageText(new TdApi.FormattedText("Hello!", null), null));
              sendFakeMessage(lucky, new TdApi.MessageVoiceNote(null, new TdApi.FormattedText(), false));
              sendFakeMessage(kepler, new TdApi.MessageVideoNote(null, false, false));
              sendFakeMessage(sajil, new TdApi.MessagePhoto(null, new TdApi.FormattedText(), false));
              sendFakeMessage(nepho, new TdApi.MessageGameScore(0, 0, 9138));

              sendFakeUpdate(new TdApi.UpdateUserChatAction(captainRaptor.id, (int) captainRaptor.id, new TdApi.ChatActionCancel()), false);
              sendFakeUpdate(new TdApi.UpdateUserChatAction(lucky.id, (int) lucky.id, new TdApi.ChatActionCancel()), false);
              sendFakeUpdate(new TdApi.UpdateUserChatAction(kepler.id, (int) kepler.id, new TdApi.ChatActionCancel()), false);
              sendFakeUpdate(new TdApi.UpdateUserChatAction(sajil.id, (int) sajil.id, new TdApi.ChatActionCancel()), false);
              sendFakeUpdate(new TdApi.UpdateUserChatAction(nepho.id, (int) nepho.id, new TdApi.ChatActionCancel()), false);
            }
          }, delay * (i + 1));
        }
      }
    }
  }

  private void sendFakeMessage (TdApi.Chat chat, TdApi.MessageContent content) {
    TdApi.Message message = TD.newFakeMessage(content);
    message.chatId = chat.id;
    message.senderUserId = (int) chat.id;
    message.date = (int) (System.currentTimeMillis() / 1000l);
    sendFakeUpdate(new TdApi.UpdateChatReadInbox(chat.id, chat.lastReadInboxMessageId, chat.unreadCount + 1), false);
    // sendFakeUpdate(new TdApi.UpdateNewMessage(message, false, false), false);
    sendFakeUpdate(new TdApi.UpdateChatLastMessage(chat.id, message, chat.order), false);
  }*/

  public void sendFakeUpdate (TdApi.Update update, boolean forceUi) {
    clientHolder().sendFakeUpdate(update, forceUi);
  }

  public void __sendFakeAction (TdApi.ChatAction action) {
    ArrayList<TdApi.Chat> chats;
    synchronized (dataLock) {
      chats = new ArrayList<>(this.chats.size());
      for (Map.Entry<Long, TdApi.Chat> chat : this.chats.entrySet()) {
        chats.add(chat.getValue());
      }
    }
    for (TdApi.Chat chat : chats) {
      if (chat.lastMessage != null) {
        sendFakeUpdate(new TdApi.UpdateChatAction(chat.id, 0, chat.lastMessage.senderId, action), false);
      }
    }
  }

  private void processUpdate (ClientHolder context, TdApi.Update update) {
    switch (update.getConstructor()) {
      // Notifications
      case TdApi.UpdateHavePendingNotifications.CONSTRUCTOR:
        onUpdateHavePendingNotifications((TdApi.UpdateHavePendingNotifications) update);
        break;
      case TdApi.UpdateActiveNotifications.CONSTRUCTOR:
        onUpdateActiveNotifications((TdApi.UpdateActiveNotifications) update);
        break;
      case TdApi.UpdateNotificationGroup.CONSTRUCTOR:
        onUpdateNotificationGroup((TdApi.UpdateNotificationGroup) update);
        break;
      case TdApi.UpdateNotification.CONSTRUCTOR:
        onUpdateNotification((TdApi.UpdateNotification) update);
        break;
      case TdApi.UpdateSavedNotificationSounds.CONSTRUCTOR:
        onUpdateSavedNotificationSounds((TdApi.UpdateSavedNotificationSounds) update);
        break;

      // Messages
      case TdApi.UpdateNewMessage.CONSTRUCTOR: {
        updateNewMessage((TdApi.UpdateNewMessage) update, true);
        break;
      }
      case TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR: {
        updateMessageSendSucceeded((TdApi.UpdateMessageSendSucceeded) update);
        break;
      }
      case TdApi.UpdateMessageSendFailed.CONSTRUCTOR: {
        updateMessageSendFailed((TdApi.UpdateMessageSendFailed) update);
        break;
      }
      case TdApi.UpdateMessageSendAcknowledged.CONSTRUCTOR: {
        quickAckManager.onMessageSendAcknowledged((TdApi.UpdateMessageSendAcknowledged) update);
        break;
      }
      case TdApi.UpdateMessageContent.CONSTRUCTOR: {
        updateMessageContent((TdApi.UpdateMessageContent) update);
        break;
      }
      case TdApi.UpdateMessageEdited.CONSTRUCTOR: {
        updateMessageEdited((TdApi.UpdateMessageEdited) update);
        break;
      }
      case TdApi.UpdateMessageContentOpened.CONSTRUCTOR: {
        updateMessageContentOpened((TdApi.UpdateMessageContentOpened) update);
        break;
      }
      case TdApi.UpdateAnimatedEmojiMessageClicked.CONSTRUCTOR: {
        updateAnimatedEmojiMessageClicked((TdApi.UpdateAnimatedEmojiMessageClicked) update);
        break;
      }
      case TdApi.UpdateMessageIsPinned.CONSTRUCTOR: {
        updateMessageIsPinned((TdApi.UpdateMessageIsPinned) update);
        break;
      }
      case TdApi.UpdateMessageLiveLocationViewed.CONSTRUCTOR: {
        updateLiveLocationViewed((TdApi.UpdateMessageLiveLocationViewed) update);
        break;
      }
      case TdApi.UpdateMessageMentionRead.CONSTRUCTOR: {
        updateMessageMentionRead((TdApi.UpdateMessageMentionRead) update);
        break;
      }
      case TdApi.UpdateMessageInteractionInfo.CONSTRUCTOR: {
        updateMessageInteractionInfo((TdApi.UpdateMessageInteractionInfo) update);
        break;
      }
      case TdApi.UpdateDeleteMessages.CONSTRUCTOR: {
        updateMessagesDeleted((TdApi.UpdateDeleteMessages) update);
        break;
      }

      // Stories
      case TdApi.UpdateStoryListChatCount.CONSTRUCTOR: {
        updateStoryListChatCount((TdApi.UpdateStoryListChatCount) update);
        break;
      }
      case TdApi.UpdateChatActiveStories.CONSTRUCTOR: {
        updateChatActiveStories((TdApi.UpdateChatActiveStories) update);
        break;
      }
      case TdApi.UpdateStory.CONSTRUCTOR: {
        updateStory((TdApi.UpdateStory) update);
        break;
      }
      case TdApi.UpdateStoryDeleted.CONSTRUCTOR: {
        updateStoryDeleted((TdApi.UpdateStoryDeleted) update);
        break;
      }
      case TdApi.UpdateStorySendSucceeded.CONSTRUCTOR: {
        updateStorySendSucceeded((TdApi.UpdateStorySendSucceeded) update);
        break;
      }
      case TdApi.UpdateStorySendFailed.CONSTRUCTOR: {
        updateStorySendFailed((TdApi.UpdateStorySendFailed) update);
        break;
      }
      case TdApi.UpdateStoryStealthMode.CONSTRUCTOR: {
        updateStoryStealthMode((TdApi.UpdateStoryStealthMode) update);
        break;
      }

      // Voice chats
      case TdApi.UpdateChatVideoChat.CONSTRUCTOR: {
        updateChatVideoChat((TdApi.UpdateChatVideoChat) update);
        break;
      }

      // Forum
      case TdApi.UpdateForumTopicInfo.CONSTRUCTOR: {
        updateForumTopicInfo((TdApi.UpdateForumTopicInfo) update);
        break;
      }
      case TdApi.UpdateChatViewAsTopics.CONSTRUCTOR: {
        updateChatViewAsTopics((TdApi.UpdateChatViewAsTopics) update);
        break;
      }

      // Join requests
      case TdApi.UpdateChatPendingJoinRequests.CONSTRUCTOR: {
        updateChatPendingJoinRequests((TdApi.UpdateChatPendingJoinRequests) update);
        break;
      }

      // Reactions
      case TdApi.UpdateActiveEmojiReactions.CONSTRUCTOR: {
        updateActiveEmojiReactions((TdApi.UpdateActiveEmojiReactions) update);
        break;
      }
      case TdApi.UpdateDefaultReactionType.CONSTRUCTOR: {
        updateDefaultReactionType((TdApi.UpdateDefaultReactionType) update);
        break;
      }
      case TdApi.UpdateMessageUnreadReactions.CONSTRUCTOR: {
        updateMessageUnreadReactions((TdApi.UpdateMessageUnreadReactions) update);
        break;
      }
      case TdApi.UpdateChatUnreadReactionCount.CONSTRUCTOR: {
        updateChatUnreadReactionCount((TdApi.UpdateChatUnreadReactionCount) update);
        break;
      }
      case TdApi.UpdateChatAvailableReactions.CONSTRUCTOR: {
        updateChatAvailableReactions((TdApi.UpdateChatAvailableReactions) update);
        break;
      }

      // Chats
      case TdApi.UpdateNewChat.CONSTRUCTOR: {
        updateNewChat((TdApi.UpdateNewChat) update);
        break;
      }
      case TdApi.UpdateChatPermissions.CONSTRUCTOR: {
        updateChatPermissions((TdApi.UpdateChatPermissions) update);
        break;
      }
      case TdApi.UpdateChatOnlineMemberCount.CONSTRUCTOR: {
        updateChatOnlineMemberCount((TdApi.UpdateChatOnlineMemberCount) update);
        break;
      }
      case TdApi.UpdateChatMessageAutoDeleteTime.CONSTRUCTOR: {
        updateChatMessageAutoDeleteTime((TdApi.UpdateChatMessageAutoDeleteTime) update);
        break;
      }
      case TdApi.UpdateChatFolders.CONSTRUCTOR: {
        updateChatFolders((TdApi.UpdateChatFolders) update);
        break;
      }
      case TdApi.UpdateChatPosition.CONSTRUCTOR: {
        updateChatPosition((TdApi.UpdateChatPosition) update);
        break;
      }
      case TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR: {
        updateChatIsMarkedAsUnread((TdApi.UpdateChatIsMarkedAsUnread) update);
        break;
      }
      case TdApi.UpdateChatIsTranslatable.CONSTRUCTOR: {
        updateChatIsTranslatable((TdApi.UpdateChatIsTranslatable) update);
        break;
      }
      case TdApi.UpdateChatBlockList.CONSTRUCTOR: {
        updateChatIsBlocked((TdApi.UpdateChatBlockList) update);
        break;
      }
      case TdApi.UpdateChatDefaultDisableNotification.CONSTRUCTOR: {
        updateChatDefaultDisableNotifications((TdApi.UpdateChatDefaultDisableNotification) update);
        break;
      }
      case TdApi.UpdateChatMessageSender.CONSTRUCTOR: {
        updateChatDefaultMessageSenderId((TdApi.UpdateChatMessageSender) update);
        break;
      }
      case TdApi.UpdateChatUnreadMentionCount.CONSTRUCTOR: {
        updateChatUnreadMentionCount((TdApi.UpdateChatUnreadMentionCount) update);
        break;
      }
      case TdApi.UpdateChatLastMessage.CONSTRUCTOR: {
        updateChatLastMessage((TdApi.UpdateChatLastMessage) update);
        break;
      }
      case TdApi.UpdateChatTitle.CONSTRUCTOR: {
        updateChatTitle((TdApi.UpdateChatTitle) update);
        break;
      }
      case TdApi.UpdateChatTheme.CONSTRUCTOR: {
        updateChatTheme((TdApi.UpdateChatTheme) update);
        break;
      }
      case TdApi.UpdateChatActionBar.CONSTRUCTOR: {
        updateChatActionBar((TdApi.UpdateChatActionBar) update);
        break;
      }
      case TdApi.UpdateChatHasScheduledMessages.CONSTRUCTOR: {
        updateChatHasScheduledMessages((TdApi.UpdateChatHasScheduledMessages) update);
        break;
      }
      case TdApi.UpdateChatHasProtectedContent.CONSTRUCTOR: {
        updateChatHasProtectedContent((TdApi.UpdateChatHasProtectedContent) update);
        break;
      }
      case TdApi.UpdateUsersNearby.CONSTRUCTOR: {
        updateUsersNearby((TdApi.UpdateUsersNearby) update);
        break;
      }
      case TdApi.UpdateChatPhoto.CONSTRUCTOR: {
        updateChatPhoto((TdApi.UpdateChatPhoto) update);
        break;
      }
      case TdApi.UpdateChatReadInbox.CONSTRUCTOR: {
        updateChatReadInbox((TdApi.UpdateChatReadInbox) update);
        break;
      }
      case TdApi.UpdateChatReadOutbox.CONSTRUCTOR: {
        updateChatReadOutbox((TdApi.UpdateChatReadOutbox) update);
        break;
      }
      case TdApi.UpdateChatReplyMarkup.CONSTRUCTOR: {
        updateChatReplyMarkup((TdApi.UpdateChatReplyMarkup) update);
        break;
      }
      case TdApi.UpdateChatDraftMessage.CONSTRUCTOR: {
        updateChatDraftMessage((TdApi.UpdateChatDraftMessage) update);
        break;
      }
      case TdApi.UpdateChatAction.CONSTRUCTOR: {
        updateChatUserAction((TdApi.UpdateChatAction) update);
        break;
      }

      // Calls
      case TdApi.UpdateCall.CONSTRUCTOR: {
        updateCall((TdApi.UpdateCall) update);
        break;
      }
      case TdApi.UpdateGroupCall.CONSTRUCTOR: {
        updateGroupCall((TdApi.UpdateGroupCall) update);
        break;
      }
      case TdApi.UpdateGroupCallParticipant.CONSTRUCTOR: {
        updateGroupCallParticipant((TdApi.UpdateGroupCallParticipant) update);
        break;
      }
      case TdApi.UpdateNewCallSignalingData.CONSTRUCTOR: {
        updateCallSignalingData((TdApi.UpdateNewCallSignalingData) update);
        break;
      }

      // Lang Pack
      case TdApi.UpdateLanguagePackStrings.CONSTRUCTOR: {
        updateLanguagePack((TdApi.UpdateLanguagePackStrings) update);
        break;
      }

      // Attachment Bots
      case TdApi.UpdateAttachmentMenuBots.CONSTRUCTOR: {
        updateAttachmentMenuBots((TdApi.UpdateAttachmentMenuBots) update);
        break;
      }
      case TdApi.UpdateWebAppMessageSent.CONSTRUCTOR: {
        updateWebAppMessageSent((TdApi.UpdateWebAppMessageSent) update);
        break;
      }

      // Notifications
      case TdApi.UpdateChatNotificationSettings.CONSTRUCTOR: {
        updateNotificationSettings((TdApi.UpdateChatNotificationSettings) update);
        break;
      }
      case TdApi.UpdateScopeNotificationSettings.CONSTRUCTOR: {
        updateNotificationSettings((TdApi.UpdateScopeNotificationSettings) update);
        break;
      }
      case TdApi.UpdateUserPrivacySettingRules.CONSTRUCTOR: {
        updatePrivacySettingRules((TdApi.UpdateUserPrivacySettingRules) update);
        break;
      }
      case TdApi.UpdateAddChatMembersPrivacyForbidden.CONSTRUCTOR: {
        updateAddChatMembersPrivacyForbidden((TdApi.UpdateAddChatMembersPrivacyForbidden) update);
        break;
      }

      // Users
      case TdApi.UpdateUser.CONSTRUCTOR: {
        cache.onUpdateUser((TdApi.UpdateUser) update);
        break;
      }
      case TdApi.UpdateUserFullInfo.CONSTRUCTOR: {
        cache.onUpdateUserFull((TdApi.UpdateUserFullInfo) update);
        break;
      }
      case TdApi.UpdateUserStatus.CONSTRUCTOR: {
        cache.onUpdateUserStatus((TdApi.UpdateUserStatus) update);
        break;
      }

      // Groups
      case TdApi.UpdateBasicGroup.CONSTRUCTOR: {
        cache.onUpdateBasicGroup((TdApi.UpdateBasicGroup) update);
        break;
      }
      case TdApi.UpdateBasicGroupFullInfo.CONSTRUCTOR: {
        TdApi.UpdateBasicGroupFullInfo updateBasicGroupFullInfo = (TdApi.UpdateBasicGroupFullInfo) update;
        cache.onUpdateBasicGroupFull(updateBasicGroupFullInfo);
        refreshChatState(ChatId.fromBasicGroupId(updateBasicGroupFullInfo.basicGroupId));
        break;
      }

      // Channels
      case TdApi.UpdateSupergroup.CONSTRUCTOR: {
        updateSupergroup((TdApi.UpdateSupergroup) update);
        break;
      }
      case TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR: {
        TdApi.UpdateSupergroupFullInfo updateSupergroupFullInfo = (TdApi.UpdateSupergroupFullInfo) update;
        cache.onUpdateSupergroupFull(updateSupergroupFullInfo);
        refreshChatState(ChatId.fromSupergroupId(updateSupergroupFullInfo.supergroupId));
        break;
      }

      // Secret chat
      case TdApi.UpdateSecretChat.CONSTRUCTOR: {
        cache.onUpdateSecretChat((TdApi.UpdateSecretChat) update);
        break;
      }

      // Security
      case TdApi.UpdateServiceNotification.CONSTRUCTOR: {
        updateServiceNotification((TdApi.UpdateServiceNotification) update);
        break;
      }
      case TdApi.UpdateUnconfirmedSession.CONSTRUCTOR: {
        updateUnconfirmedSession((TdApi.UpdateUnconfirmedSession) update);
        break;
      }

      // Files
      case TdApi.UpdateFile.CONSTRUCTOR: {
        TdApi.UpdateFile updateFile = (TdApi.UpdateFile) update;
        if (Log.isEnabled(Log.TAG_TDLIB_FILES)) {
          Log.i(Log.TAG_TDLIB_FILES, "updateFile id=%d size=%d expectedSize=%d remote=%s local=%s", updateFile.file.id, updateFile.file.size, updateFile.file.expectedSize, updateFile.file.remote.toString(), updateFile.file.local.toString());
        }
        updateFile(updateFile);
        break;
      }
      case TdApi.UpdateFileAddedToDownloads.CONSTRUCTOR: {
        updateFileAddedToDownloads((TdApi.UpdateFileAddedToDownloads) update);
        break;
      }
      case TdApi.UpdateFileDownload.CONSTRUCTOR: {
        updateFileDownload((TdApi.UpdateFileDownload) update);
        break;
      }
      case TdApi.UpdateFileDownloads.CONSTRUCTOR: {
        updateFileDownloads((TdApi.UpdateFileDownloads) update);
        break;
      }
      case TdApi.UpdateFileRemovedFromDownloads.CONSTRUCTOR: {
        updateFileRemovedFromDownloads((TdApi.UpdateFileRemovedFromDownloads) update);
        break;
      }

      // AuthState
      case TdApi.UpdateAuthorizationState.CONSTRUCTOR: {
        updateAuthState(context, ((TdApi.UpdateAuthorizationState) update).authorizationState);
        break;
      }
      case TdApi.UpdateConnectionState.CONSTRUCTOR: {
        updateConnectionState((TdApi.UpdateConnectionState) update);
        break;
      }
      case TdApi.UpdateOption.CONSTRUCTOR: {
        updateOption(context, (TdApi.UpdateOption) update);
        break;
      }
      case TdApi.UpdateSpeechRecognitionTrial.CONSTRUCTOR: {
        updateSpeechRecognitionTrial((TdApi.UpdateSpeechRecognitionTrial) update);
        break;
      }
      case TdApi.UpdateDefaultBackground.CONSTRUCTOR: {
        updateDefaultBackground((TdApi.UpdateDefaultBackground) update);
        break;
      }


      // Accent colors
      case TdApi.UpdateAccentColors.CONSTRUCTOR: {
        updateAccentColors((TdApi.UpdateAccentColors) update);
        break;
      }
      case TdApi.UpdateProfileAccentColors.CONSTRUCTOR: {
        updateProfileAccentColors((TdApi.UpdateProfileAccentColors) update);
        break;
      }

      // Media
      case TdApi.UpdateAnimationSearchParameters.CONSTRUCTOR: {
        updateAnimationSearchParameters((TdApi.UpdateAnimationSearchParameters) update);
        break;
      }
      case TdApi.UpdateInstalledStickerSets.CONSTRUCTOR: {
        updateInstalledStickerSets((TdApi.UpdateInstalledStickerSets) update);
        break;
      }
      case TdApi.UpdateFavoriteStickers.CONSTRUCTOR: {
        updateFavoriteStickers((TdApi.UpdateFavoriteStickers) update);
        break;
      }
      case TdApi.UpdateRecentStickers.CONSTRUCTOR: {
        updateRecentStickers((TdApi.UpdateRecentStickers) update);
        break;
      }
      case TdApi.UpdateDiceEmojis.CONSTRUCTOR: {
        updateDiceEmoji(((TdApi.UpdateDiceEmojis) update).emojis);
        break;
      }
      case TdApi.UpdateStickerSet.CONSTRUCTOR: {
        updateStickerSet(((TdApi.UpdateStickerSet) update).stickerSet);
        break;
      }
      case TdApi.UpdateTrendingStickerSets.CONSTRUCTOR: {
        updateTrendingStickerSets((TdApi.UpdateTrendingStickerSets) update);
        break;
      }
      case TdApi.UpdateSavedAnimations.CONSTRUCTOR: {
        updateSavedAnimations((TdApi.UpdateSavedAnimations) update);
        break;
      }
      case TdApi.UpdateUnreadMessageCount.CONSTRUCTOR: {
        updateUnreadMessageCount((TdApi.UpdateUnreadMessageCount) update);
        break;
      }
      case TdApi.UpdateUnreadChatCount.CONSTRUCTOR: {
        updateUnreadChatCount((TdApi.UpdateUnreadChatCount) update);
        break;
      }
      case TdApi.UpdateTermsOfService.CONSTRUCTOR: {
        updateTermsOfService((TdApi.UpdateTermsOfService) update);
        break;
      }
      case TdApi.UpdateAutosaveSettings.CONSTRUCTOR: {
        updateAutosaveSettings((TdApi.UpdateAutosaveSettings) update);
        break;
      }
      case TdApi.UpdateSuggestedActions.CONSTRUCTOR: {
        updateSuggestedActions((TdApi.UpdateSuggestedActions) update);
        break;
      }
      case TdApi.UpdateChatThemes.CONSTRUCTOR: {
        updateChatThemes((TdApi.UpdateChatThemes) update);
        break;
      }
      case TdApi.UpdateChatBackground.CONSTRUCTOR: {
        updateChatBackground((TdApi.UpdateChatBackground) update);
        break;
      }
      case TdApi.UpdateChatAccentColors.CONSTRUCTOR: {
        updateChatAccentColors((TdApi.UpdateChatAccentColors) update);
        break;
      }
      case TdApi.UpdateChatEmojiStatus.CONSTRUCTOR: {
        updateChatEmojiStatus((TdApi.UpdateChatEmojiStatus) update);
        break;
      }

      // File generation
      case TdApi.UpdateFileGenerationStart.CONSTRUCTOR: {
        updateFileGenerationStart((TdApi.UpdateFileGenerationStart) update);
        break;
      }
      case TdApi.UpdateFileGenerationStop.CONSTRUCTOR: {
        updateFileGenerationStop((TdApi.UpdateFileGenerationStop) update);
        break;
      }

      // for bots only.
      case TdApi.UpdateNewChatJoinRequest.CONSTRUCTOR:
      case TdApi.UpdateNewCustomEvent.CONSTRUCTOR:
      case TdApi.UpdateNewCustomQuery.CONSTRUCTOR:
      case TdApi.UpdateNewInlineQuery.CONSTRUCTOR:
      case TdApi.UpdateNewChosenInlineResult.CONSTRUCTOR:
      case TdApi.UpdateNewCallbackQuery.CONSTRUCTOR:
      case TdApi.UpdateNewInlineCallbackQuery.CONSTRUCTOR:
      case TdApi.UpdateNewPreCheckoutQuery.CONSTRUCTOR:
      case TdApi.UpdateNewShippingQuery.CONSTRUCTOR:
      case TdApi.UpdatePoll.CONSTRUCTOR:
      case TdApi.UpdatePollAnswer.CONSTRUCTOR:
      case TdApi.UpdateChatMember.CONSTRUCTOR:
      case TdApi.UpdateChatBoost.CONSTRUCTOR:
      case TdApi.UpdateMessageReaction.CONSTRUCTOR:
      case TdApi.UpdateMessageReactions.CONSTRUCTOR: {
        // Must never come from TDLib. If it does, there's a bug on TDLib side.
        throw Td.unsupported(update);
      }
      default: {
        Td.assertUpdate_618db8c7();
        throw Td.unsupported(update);
      }
    }
  }

  // Loading user data

  @TdlibThread
  void downloadMyUser (@Nullable TdApi.User user) {
    TdApi.EmojiStatus emojiStatus = user != null && user.isPremium ? user.emojiStatus : null;
    long newEmojiStatusId = emojiStatus != null ? emojiStatus.customEmojiId : 0;
    TdlibEmojiManager.Entry emojiEntry = newEmojiStatusId != 0 ? emoji().find(newEmojiStatusId) : null;
    TdlibAccentColor accentColor = cache().userAccentColor(user);
    TdApi.Sticker emojiStatusSticker = emojiEntry != null && !emojiEntry.isNotFound() ? emojiEntry.value : null;

    account().storeUserInformation(user, accentColor != null ? accentColor.getRemoteAccentColor() : null, emojiStatusSticker);
    downloadMyProfilePhoto(user);
    downloadMyUserEmojiStatus(user);
  }

  // Loading user data: profile photo

  private TdApi.ProfilePhoto myProfilePhoto;

  @TdlibThread
  private void downloadMyProfilePhoto (TdApi.User user) {
    TdApi.ProfilePhoto newProfilePhoto = user != null ? user.profilePhoto : null;
    if (newProfilePhoto == null && myProfilePhoto == null)
      return;
    if (newProfilePhoto != null && myProfilePhoto != null && (newProfilePhoto.id == myProfilePhoto.id && newProfilePhoto.small.id == myProfilePhoto.small.id && newProfilePhoto.big.id == myProfilePhoto.big.id))
      return;
    myProfilePhoto = newProfilePhoto;
    if (newProfilePhoto != null) {
      client().send(new TdApi.DownloadFile(newProfilePhoto.small.id, TdlibFilesManager.CLOUD_PRIORITY + 2, 0, 0, true), profilePhotoHandler(false));
      client().send(new TdApi.DownloadFile(newProfilePhoto.big.id, TdlibFilesManager.CLOUD_PRIORITY, 0, 0, true), profilePhotoHandler(true));
    }
  }

  private Client.ResultHandler profilePhotoHandler (boolean isBig) {
    return result -> {
      switch (result.getConstructor()) {
        case TdApi.File.CONSTRUCTOR: {
          TdApi.File downloadedFile = (TdApi.File) result;
          if (TD.isFileLoaded(downloadedFile)) {
            TdApi.File currentFile = myProfilePhoto != null ? (isBig ? myProfilePhoto.big : myProfilePhoto.small) : null;
            if (currentFile != null && currentFile.id == downloadedFile.id) {
              Td.copyTo(downloadedFile, currentFile);
              account().storeUserProfilePhotoPath(isBig, downloadedFile.local.path);
              context.onUpdateAccountProfilePhoto(accountId, isBig);
            }
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e("Failed to load avatar, accountId:%d, big:%b", accountId, isBig);
          break;
        }
      }
    };
  }

  // Loading user data: emoji status

  private long myEmojiStatusId;

  @TdlibThread
  private void downloadMyUserEmojiStatus (@Nullable TdApi.User user) {
    TdApi.EmojiStatus emojiStatus = user != null && user.isPremium ? user.emojiStatus : null;
    long newEmojiStatusId = emojiStatus != null ? emojiStatus.customEmojiId : 0;
    if (newEmojiStatusId == myEmojiStatusId)
      return;
    myEmojiStatusId = newEmojiStatusId;
    if (newEmojiStatusId != 0) {
      emoji().findOrRequest(newEmojiStatusId, entry -> {
        if (!entry.isNotFound() && newEmojiStatusId == myEmojiStatusId) {
          account().storeUserEmojiStatusMetadata(newEmojiStatusId, entry.value);
          client().send(new TdApi.DownloadFile(entry.value.sticker.id, TdlibFilesManager.CLOUD_PRIORITY + 1, 0, 0, true), emojiStatusHandler(entry, false));
          if (entry.value.thumbnail != null) {
            client().send(new TdApi.DownloadFile(entry.value.thumbnail.file.id, TdlibFilesManager.CLOUD_PRIORITY, 0, 0, true), emojiStatusHandler(entry, true));
          }
        }
      });
    }
  }

  private Client.ResultHandler emojiStatusHandler (@NonNull TdlibEmojiManager.Entry entry, boolean isThumbnail) {
    return result -> {
      switch (result.getConstructor()) {
        case TdApi.File.CONSTRUCTOR: {
          TdApi.File downloadedFile = (TdApi.File) result;
          TdApi.File targetFile = isThumbnail ? entry.value.thumbnail.file : entry.value.sticker;
          if (TD.isFileLoaded(downloadedFile) && myEmojiStatusId == entry.key && targetFile.id == downloadedFile.id) {
            Td.copyTo(downloadedFile, targetFile);
            account().storeUserEmojiStatusPath(entry.key, entry.value, isThumbnail, downloadedFile.local.path);
            context.onUpdateEmojiStatus(accountId, isThumbnail);
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.e("Failed to load emoji status, accountId:%d, isThumbnail:%b", accountId, isThumbnail);
          break;
        }
      }
    };
  }

  // Periodic sync

  private boolean needPeriodicSync, hasPeriodicSync;

  private void setNeedPeriodicSync (boolean need) {
    if (this.needPeriodicSync != need) {
      this.needPeriodicSync = need;
      if (need) {
        schedulePeriodicSync();
      } else {
        cancelPeriodicSync();
      }
    }
  }

  private void schedulePeriodicSync () {
    if (!hasPeriodicSync) {
      SyncHelper.register(UI.getContext(), accountId);
      this.hasPeriodicSync = true;
    }
  }

  private void cancelPeriodicSync () {
    if (hasPeriodicSync) {
      SyncHelper.cancel(UI.getContext(), accountId);
      this.hasPeriodicSync = false;
    }
  }

  // Events

  private void onJobAdded (boolean isAboutToExecute) {
    incrementReferenceCount(isAboutToExecute ? REFERENCE_TYPE_TASK_EXECUTION : REFERENCE_TYPE_TASK);
  }

  private void onJobRemoved (boolean justFinishedExecution) {
    decrementReferenceCount(justFinishedExecution ? REFERENCE_TYPE_TASK_EXECUTION : REFERENCE_TYPE_TASK);
  }

  private final ConditionalExecutor
    readyOrWaitingForDataListeners = new ConditionalExecutor(() -> {
      TdApi.AuthorizationState state = authorizationState;
      if (state != null) {
        switch (state.getConstructor()) {
          case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
          case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
          case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
          case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
            return false;
          case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitEmailAddress.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
          case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
          case TdApi.AuthorizationStateReady.CONSTRUCTOR:
            return true;
          default:
            Td.assertAuthorizationState_6e5056de();
            throw Td.unsupported(state);
        }
      }
      return false;
    }).onAddRemove(this::onJobAdded, this::onJobRemoved),
    initializationListeners = new ConditionalExecutor(() -> authorizationStatus() != Status.UNKNOWN).onAddRemove(this::onJobAdded, this::onJobRemoved), // Executed once received authorization state
    myUserOrUnauthorizedListeners = new ConditionalExecutor(() -> {
      int status = authorizationStatus();
      return status != Status.UNKNOWN && (status == Status.UNAUTHORIZED || myUser() != null);
    }).onAddRemove(this::onJobAdded, this::onJobRemoved),
    connectionListeners = new ConditionalExecutor(() -> authorizationStatus() != Status.UNKNOWN && connectionState == ConnectionState.CONNECTED).onAddRemove(this::onJobAdded, this::onJobRemoved), // Executed once connected
    notificationInitListeners = new ConditionalExecutor(() -> {
      final int status = authorizationStatus();
      return status == Status.UNAUTHORIZED || (status == Status.READY && haveInitializedNotifications);
    }).onAddRemove(this::onJobAdded, this::onJobRemoved),
    notificationListeners = new ConditionalExecutor(() -> !havePendingNotifications).onAddRemove(this::onJobAdded, this::onJobRemoved),
    notificationConsistencyListeners = new ConditionalExecutor(() -> false),
    closeListeners = new ConditionalExecutor(() -> authorizationState != null && authorizationState.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR), // Executed once no pending notifications remaining
    noReferenceListeners = new ConditionalExecutor(() -> !hasActiveReferences());

  @AnyThread
  public void awaitInitialization (@NonNull Runnable after) {
    initializationListeners.executeOrPostponeTask(after);
  }

  @AnyThread
  public void awaitMyUserOrUnauthorizedState (@NonNull Runnable after) {
    myUserOrUnauthorizedListeners.executeOrPostponeTask(after);
  }

  @AnyThread
  public void awaitAllReferencesReleased (@NonNull Runnable after) {
    noReferenceListeners.executeOrPostponeTask(after);
  }

  @AnyThread
  public void awaitNotificationInitialization (@NonNull Runnable after) {
    notificationInitListeners.executeOrPostponeTask(after);
  }

  public void awaitReadyOrWaitingForData (@NonNull Runnable after) {
    readyOrWaitingForDataListeners.executeOrPostponeTask(after);
  }

  public void awaitClose (@NonNull Runnable after, boolean force) {
    if (force || (authorizationState != null && authorizationState.getConstructor() == TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR)) {
      closeListeners.executeOrPostponeTask(after);
    } else {
      after.run();
    }
  }

  public void awaitNotifications (@NonNull Runnable after) {
    notificationListeners.executeOrPostponeTask(after);
  }

  public void awaitConnection (@NonNull Runnable after) {
    connectionListeners.executeOrPostponeTask(after);
  }

  // Events

  @TdlibThread
  private void onInitialized () {
    initializationListeners.notifyConditionChanged(true);
    notificationInitListeners.notifyConditionChanged();
    connectionListeners.notifyConditionChanged();
  }

  @TdlibThread
  private void runStartupChecks () {
    checkDeviceToken();
    animatedTgxEmoji.load(this);
    if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE) && !isDebugInstance()) {
      animatedDiceExplicit.load(this);
    }
  }

  private void onConnected () {
    setHasUnprocessedPushes(false);
    connectionListeners.notifyConditionChanged();
  }

  private void onNotificationsInitialized () {
    notificationInitListeners.notifyConditionChanged(true);
  }

  public void dispatchNotificationsInitialized () {
    runOnTdlibThread(() -> {
      if (!haveInitializedNotifications) {
        haveInitializedNotifications = true;
        onNotificationsInitialized();
      }
    });
  }

  // Emoji

  void fetchAllMessages (long chatId, @Nullable String query, @Nullable TdApi.SearchMessagesFilter filter, @NonNull RunnableData<List<TdApi.Message>> callback) {
    List<TdApi.Message> messages = new ArrayList<>();
    boolean needFilter = !StringUtils.isEmpty(query) || filter != null;
    TdApi.Function<?> function;
    if (needFilter) {
      function = new TdApi.SearchChatMessages(chatId, query, null, 0, 0, 100, filter, 0);
    } else {
      function = new TdApi.GetChatHistory(chatId, 0, 0, 0, false);
    }
    client().send(function, new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object result) {
        switch (result.getConstructor()) {
          case TdApi.FoundChatMessages.CONSTRUCTOR: {
            TdApi.FoundChatMessages fetchedMessages = (TdApi.FoundChatMessages) result;
            Collections.addAll(messages, fetchedMessages.messages);
            if (fetchedMessages.nextFromMessageId == 0) {
              callback.runWithData(messages);
            } else {
              ((TdApi.SearchChatMessages) function).fromMessageId = fetchedMessages.nextFromMessageId;
              client().send(function, this);
            }
            break;
          }
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Messages fetchedMessages = (TdApi.Messages) result;
            if (fetchedMessages.messages.length == 0) {
              callback.runWithData(messages);
            } else {
              Collections.addAll(messages, fetchedMessages.messages);
              long fromMessageId = messages.get(messages.size() - 1).id;
              ((TdApi.GetChatHistory) function).fromMessageId = fromMessageId;
              client().send(function, this);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            if (messages.isEmpty()) {
              UI.showError(result);
            }
            callback.runWithData(messages);
            break;
          }
        }
      }
    });
  }

  public static final class UpdateFileInfo {
    public final TdApi.Document document;
    public final int buildNo;
    public final String version, commit;

    public UpdateFileInfo (TdApi.Document document, int buildNo, String version, String commit) {
      this.document = document;
      this.buildNo = buildNo;
      this.version = version;
      this.commit = commit;
    }
  }

  public void findUpdateFile (@NonNull RunnableData<UpdateFileInfo> onDone) {
    final String abi;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      abi = Build.SUPPORTED_ABIS[0];
    } else {
      abi = Build.CPU_ABI;
    }
    final String hashtag;
    switch (abi) {
      case "armeabi-v7a": hashtag = "arm32"; break;
      case "arm64-v8a": hashtag = "arm64"; break;
      case "x86": hashtag = "x86"; break;
      case "x86_64": case "x64": hashtag = "x64"; break;
      default: {
        onDone.runWithData(null);
        return;
      }
    }
    clientHolder().updates.findResource(message -> {
      if (message != null && Td.isDocument(message.content)) {
        TdApi.Document document = ((TdApi.MessageDocument) message.content).document;
        TdApi.FormattedText caption = ((TdApi.MessageDocument) message.content).caption;
        boolean ok = false;
        int buildNo = 0;
        String version = null;
        String commit = null;
        final String prefix = "Telegram-X-";
        if (!StringUtils.isEmpty(document.fileName) && document.fileName.startsWith(prefix)) {
          int i = document.fileName.indexOf('-', prefix.length());
          version = document.fileName.substring(prefix.length(), i == -1 ? document.fileName.length() : i);
          if (version.matches("^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$")) {
            buildNo = StringUtils.parseInt(version.substring(version.lastIndexOf('.') + 1));
            if (buildNo > BuildConfig.ORIGINAL_VERSION_CODE || BuildConfig.DEBUG) {
              ok = true;
            }
          }
        }
        //noinspection UnsafeOptInUsageError
        if (!Td.isEmpty(caption)) {
          Pattern pattern = Pattern.compile("(?<=Commit:)\\s*([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
          Matcher matcher = pattern.matcher(caption.text);
          if (matcher.find()) {
            commit = matcher.group(1);
            if (StringUtils.isEmpty(commit)) {
              commit = null;
            }
          }
        }
        onDone.runWithData(ok ? new UpdateFileInfo(document, buildNo, version, commit) : null);
      }
    }, "#apk " + (
      Settings.instance().getNewSetting(Settings.SETTING_FLAG_DOWNLOAD_BETAS) ? "" : "#stable "
    ) + "#" + hashtag, BuildConfig.COMMIT_DATE);
  }

  public <T extends Settings.CloudSetting> void fetchCloudSettings (@NonNull RunnableData<List<T>> callback, String requiredHashtag, @NonNull Future<T> currentSettingProvider, @NonNull Future<T> builtinItemProvider, @NonNull WrapperProvider<T, TdApi.Message> instanceProvider) {
    clientHolder().resources.fetchResources(messages -> {
      List<T> settings = new ArrayList<>();
      Map<String, TdApi.File> pendingPreviews = new HashMap<>();

        boolean foundBuiltIn = false;
        T currentSetting = currentSettingProvider.getValue();
        boolean foundCurrent = currentSetting.isBuiltIn();

        for (TdApi.Message message : messages) {
          TdApi.Document doc = ((TdApi.MessageDocument) message.content).document;
          TdApi.FormattedText caption = ((TdApi.MessageDocument) message.content).caption;
          if (TD.hasHashtag(caption, "#preview")) {
            pendingPreviews.put(U.getSecureFileName(doc.fileName), doc.document);
          } else {
            try {
              T setting = instanceProvider.getWrap(message);
              if (!foundBuiltIn && setting.isBuiltIn())
                foundBuiltIn = true;
              else if (!foundCurrent && setting.identifier.equals(currentSetting.identifier))
                foundCurrent = true;
              settings.add(setting);
            } catch (Throwable ignored) {}
          }
        }

        if (!foundBuiltIn)
          settings.add(0, builtinItemProvider.getValue());
        if (!foundCurrent)
          settings.add(currentSetting);

        Collections.sort(settings);
        for (T setting : settings) {
          setting.setPreviewFile(this, pendingPreviews.remove(setting.identifier));
        }
        callback.runWithData(settings);
    }, message ->
      message.content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR &&
      TD.hasHashtag(((TdApi.MessageDocument) message.content).caption, requiredHashtag)
    );
  }

  public void getEmojiPacks (@NonNull RunnableData<List<Settings.EmojiPack>> callback) {
    fetchCloudSettings(callback, "#emoji", () -> Settings.instance().getEmojiPack(), Settings.EmojiPack::new, Settings.EmojiPack::new);
  }

  public void getIconPacks (@NonNull RunnableData<List<Settings.IconPack>> callback) {
    fetchCloudSettings(callback, "#icons", () -> Settings.instance().getIconPack(), Settings.IconPack::new, Settings.IconPack::new);
  }

  // Chat Permissions

  public boolean canInviteUsers (TdApi.Chat chat) {
    return canInviteUsers(chat, true);
  }

  public boolean canInviteUsers (TdApi.Chat chat, boolean allowDefault) {
    if (chat == null || chat.id == 0) {
      return false;
    }
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          if (((TdApi.ChatMemberStatusAdministrator) status).rights.canInviteUsers) {
            return true;
          }
          break;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          if (!((TdApi.ChatMemberStatusRestricted) status).permissions.canInviteUsers) {
            return false;
          }
          break;

        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
      }
    }
    return allowDefault && chat.permissions.canInviteUsers;
  }

  public boolean canManageInviteLinks (TdApi.Chat chat) {
    if (chat == null || chat.id == 0) {
      return false;
    }
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          return (((TdApi.ChatMemberStatusAdministrator) status).rights.canInviteUsers);
        default:
          return false;
      }
    } else {
      return false;
    }
  }

  public boolean canCreateInviteLink (TdApi.Chat chat) {
    return canInviteUsers(chat, false);
  }

  public boolean canSendPolls (long chatId) {
    if (chatId == 0)
      return false;
    if (ChatId.isUserChat(chatId)) {
      return /*isSelfChat(chatId) ||*/ isBotChat(chatId);
    }
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          if (!((TdApi.ChatMemberStatusRestricted) status).permissions.canSendPolls) {
            return false;
          }
          break;

        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
      }
    }
    TdApi.Chat chat = chat(chatId);
    return chat != null && chat.permissions.canSendPolls;
  }

  public boolean canChangeInfo (TdApi.Chat chat) {
    return canChangeInfo(chat, true);
  }

  public boolean canChangeInfo (TdApi.Chat chat, boolean allowDefault) {
    if (chat == null || chat.id == 0 || isUserChat(chat)) {
      return false;
    }
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          if (((TdApi.ChatMemberStatusAdministrator) status).rights.canChangeInfo) {
            return true;
          }
          break;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          if (!((TdApi.ChatMemberStatusRestricted) status).permissions.canChangeInfo) {
            return false;
          }
          break;

        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
      }
    }
    return allowDefault && chat.permissions.canChangeInfo;
  }

  public boolean canRestrictMembers (long chatId) {
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          if (((TdApi.ChatMemberStatusAdministrator) status).rights.canRestrictMembers) {
            return true;
          }
          break;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
      }
    }
    return false;
  }

  public boolean inSlowMode (long chatId) {
    return cache.getSlowModeDelayExpiresIn(ChatId.toSupergroupId(chatId), TimeUnit.SECONDS) > 0;
  }

  public boolean canEditSlowMode (long chatId) {
    if (canRestrictMembers(chatId)) {
      TdApi.Supergroup supergroup = chatToSupergroup(chatId);
      if (supergroup != null) {
        return !supergroup.isChannel && !supergroup.isBroadcastGroup;
      }
      return ChatId.isBasicGroup(chatId) && canUpgradeChat(chatId);
    }
    return false;
  }

  public boolean isBroadcastGroup (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && supergroup.isBroadcastGroup;
  }

  public boolean canDeleteMessages (long chatId) {
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          if (((TdApi.ChatMemberStatusAdministrator) status).rights.canDeleteMessages) {
            return true;
          }
          break;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
      }
    }
    return false;
  }

  public boolean canToggleSignMessages (TdApi.Chat chat) {
    return isChannelChat(chat) && canChangeInfo(chat, false);
  }

  public boolean haveCreatorRights (long chatId) {
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    return status != null && status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR;
  }

  public boolean canToggleJoinByRequest (TdApi.Chat chat) {
    if (isMultiChat(chat)) {
      String username = chatUsername(chat);
      if (StringUtils.isEmpty(username)) {
        return false;
      }
      if (isSupergroupChat(chat)) {
        return canRestrictMembers(chat.id);
      } else if (chat.type.getConstructor() == TdApi.ChatTypeBasicGroup.CONSTRUCTOR) {
        return haveCreatorRights(chat.id);
      }
    }
    return false;
  }

  public boolean canToggleContentProtection (long chatId) {
    return haveCreatorRights(chatId);
  }

  public boolean canToggleAllHistory (TdApi.Chat chat) {
    return ((isSupergroupChat(chat) && canChangeInfo(chat, false)) || canUpgradeChat(chat.id)) && StringUtils.isEmpty(chatUsername(chat.id));
  }

  public boolean canUpgradeChat (long chatId) {
    return ChatId.isBasicGroup(chatId) && TD.isCreator(chatStatus(chatId));
  }

  public boolean canChangeMessageAutoDeleteTime (long chatId) {
    // Changes the message auto-delete or self-destruct (for secret chats) time in a chat.
    // Requires changeInfo administrator right in basic groups, supergroups and channels
    // Message auto-delete time can't be changed in a chat with the current user (Saved Messages) and the chat 777000 (Telegram).
    if (chatId == 0 || isSelfChat(chatId) || chatUserId(chatId) == TdConstants.TELEGRAM_ACCOUNT_ID) {
      return false;
    }
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    TdApi.Chat chat = chat(chatId);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          return ((TdApi.ChatMemberStatusAdministrator) status).rights.canChangeInfo;
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          break;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          if (!((TdApi.ChatMemberStatusRestricted) status).isMember || !((TdApi.ChatMemberStatusRestricted) status).permissions.canChangeInfo) {
            return false;
          }
          break;
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
          return false;
      }
      return chat != null && chat.permissions.canChangeInfo;
    }
    return chat != null && chat.permissions.canSendBasicMessages;
  }

  public boolean canPinMessages (TdApi.Chat chat) {
    if (chat == null || chat.id == 0 || ChatId.isSecret(chat.id))
      return false;
    if (isUserChat(chat.id))
      return true;
    boolean isChannel = isChannelChat(chat);
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    if (isChannel) {
      if (status != null) {
        switch (status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            return ((TdApi.ChatMemberStatusAdministrator) status).rights.canEditMessages;
        }
      }
      return false;
    } else {
      if (status != null) {
        switch (status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            if (((TdApi.ChatMemberStatusAdministrator) status).rights.canPinMessages)
              return true;
            break;
          case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
            if (!((TdApi.ChatMemberStatusRestricted) status).permissions.canPinMessages)
              return false;
          case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
          case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
            break;
        }
      }
      return chat.permissions.canPinMessages;
    }
  }

  public boolean canKickMember (long chatId, TdApi.ChatMember member) {
    if (chatId == 0)
      return false;
    TdApi.ChatMemberStatus status = chatStatus(chatId);
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
          return true;
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          return !TD.isAdmin(member.status) || (member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR && ((TdApi.ChatMemberStatusAdministrator) member.status).canBeEdited);
      }
    }
    return member.inviterUserId == myUserId();
  }
  public static final int RESTRICTION_STATUS_EVERYONE = 0;
  public static final int RESTRICTION_STATUS_RESTRICTED = 1;
  public static final int RESTRICTION_STATUS_BANNED = 2;
  public static final int RESTRICTION_STATUS_UNAVAILABLE = 3;

  public static class RestrictionStatus {
    public final long chatId;
    public final int status;
    public final int untilDate;

    public RestrictionStatus (long chatId, int status, int untilDate) {
      this.chatId = chatId;
      this.status = status;
      this.untilDate = untilDate;
    }

    public boolean isGlobal () { // Affects all users, not just me
      return status != RESTRICTION_STATUS_RESTRICTED && status != RESTRICTION_STATUS_BANNED;
    }

    public boolean isUserChat () {
      return ChatId.isUserChat(chatId);
    }
  }

  public boolean hasRestriction (long chatId, @RightId int rightId) {
    return getRestrictionStatus(chat(chatId), rightId) != null;
  }

  @Nullable
  public RestrictionStatus getRestrictionStatus (TdApi.Chat chat, @RightId int rightId) {
    if (chat == null || chat.id == 0 || !TD.isValidRight(rightId))
      return null;
    TdApi.ChatMemberStatus status = chatStatus(chat.id);
    boolean isNotSpecificallyRestricted = status != null && (
      status.getConstructor() == TdApi.ChatMemberStatusMember.CONSTRUCTOR || (
        status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR &&
        TD.checkRight(((TdApi.ChatMemberStatusRestricted) status).permissions, rightId)
      )
    );
    if (status != null) {
      switch (status.getConstructor()) {
        case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
        case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
          return null;
        case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR:
          return
            !TD.checkRight(chat.permissions, rightId) ? new RestrictionStatus(chat.id, RESTRICTION_STATUS_EVERYONE, 0) :
            !TD.checkRight(((TdApi.ChatMemberStatusRestricted) status).permissions, rightId) ? new RestrictionStatus(chat.id, RESTRICTION_STATUS_RESTRICTED, ((TdApi.ChatMemberStatusRestricted) status).restrictedUntilDate) :
            null;
        case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
          return new RestrictionStatus(chat.id, RESTRICTION_STATUS_BANNED, ((TdApi.ChatMemberStatusBanned) status).bannedUntilDate);
        case TdApi.ChatMemberStatusLeft.CONSTRUCTOR:
          break;
        case TdApi.ChatMemberStatusMember.CONSTRUCTOR:
          if (isChannelChat(chat))
            return new RestrictionStatus(chat.id, RESTRICTION_STATUS_UNAVAILABLE, 0);
          break;
      }
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        long userId = ChatId.toUserId(chat.id);
        TdApi.User user = cache().user(userId);
        boolean isUnavailable = user == null;
        if (!isUnavailable) {
          switch (user.type.getConstructor()) {
            case TdApi.UserTypeDeleted.CONSTRUCTOR:
            case TdApi.UserTypeUnknown.CONSTRUCTOR:
              isUnavailable = true;
              break;
          }
        }
        if (isUnavailable)
          return new RestrictionStatus(chat.id, RESTRICTION_STATUS_UNAVAILABLE, 0);
        if (rightId == RightId.SEND_VOICE_NOTES || rightId == RightId.SEND_VIDEO_NOTES) {
          TdApi.UserFullInfo userFullInfo = cache().userFull(userId);
          if (userFullInfo != null && userFullInfo.hasRestrictedVoiceAndVideoNoteMessages) {
            return new RestrictionStatus(chat.id, RESTRICTION_STATUS_RESTRICTED, 0);
          }
        }
        if (!TD.checkRight(chat.permissions, rightId)) {
          return new RestrictionStatus(chat.id, RESTRICTION_STATUS_RESTRICTED, 0);
        }
        return null;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int secretChatId = ChatId.toSecretChatId(chat.id);
        TdApi.SecretChat secretChat = cache().secretChat(secretChatId);
        if (secretChat != null && secretChat.state.getConstructor() == TdApi.SecretChatStateReady.CONSTRUCTOR) {
          TdApi.User user = cache().user(secretChat.userId);
          if (user != null) {
            switch (user.type.getConstructor()) {
              case TdApi.UserTypeDeleted.CONSTRUCTOR:
              case TdApi.UserTypeUnknown.CONSTRUCTOR:
                return new RestrictionStatus(chat.id, RESTRICTION_STATUS_UNAVAILABLE, 0);
            }
            return null;
          }
          if (rightId == RightId.SEND_VOICE_NOTES || rightId == RightId.SEND_VIDEO_NOTES) {
            TdApi.UserFullInfo userFullInfo = cache().userFull(secretChat.userId);
            if (userFullInfo != null && userFullInfo.hasRestrictedVoiceAndVideoNoteMessages) {
              return new RestrictionStatus(chat.id, RESTRICTION_STATUS_RESTRICTED, 0);
            }
          }
          if (!TD.checkRight(chat.permissions, rightId)) {
            return new RestrictionStatus(chat.id, RESTRICTION_STATUS_RESTRICTED, 0);
          }
        }
        return new RestrictionStatus(chat.id, RESTRICTION_STATUS_UNAVAILABLE, 0);
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        break;
    }
    if (!TD.checkRight(chat.permissions, rightId))
      return new RestrictionStatus(chat.id, isNotSpecificallyRestricted ? RESTRICTION_STATUS_EVERYONE : RESTRICTION_STATUS_RESTRICTED, 0);
    return null;
  }

  public CharSequence getSlowModeRestrictionText (long chatId) {
    return getSlowModeRestrictionText(chatId, null);
  }

  public CharSequence getSlowModeRestrictionText (long chatId, @Nullable TdApi.MessageSchedulingState schedulingState) {
    if (schedulingState != null) {
      return null;
    }

    final int timeToSend = (int) cache().getSlowModeDelayExpiresIn(ChatId.toSupergroupId(chatId), TimeUnit.SECONDS);
    if (timeToSend == 0) {
      return null;
    }

    final int minutes = timeToSend / 60;
    final int seconds = timeToSend % 60;

    return (minutes > 0) ?
      Lang.pluralBold(R.string.xSlowModeRestrictionMinutes, minutes):
      Lang.pluralBold(R.string.xSlowModeRestrictionSeconds, seconds);
  }

  public CharSequence getRestrictionText (TdApi.Chat chat, TdApi.Message message) {
    if (message != null) {
      switch (message.content.getConstructor()) {
        case TdApi.MessageAnimation.CONSTRUCTOR:
          return getGifRestrictionText(chat);
        case TdApi.MessageSticker.CONSTRUCTOR:
        case TdApi.MessageDice.CONSTRUCTOR:
          return getStickerRestrictionText(chat);
        case TdApi.MessagePoll.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_POLLS);
        case TdApi.MessageAudio.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_AUDIO);
        case TdApi.MessageDocument.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_DOCS);
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageExpiredPhoto.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_PHOTOS);
        case TdApi.MessageVideo.CONSTRUCTOR:
        case TdApi.MessageExpiredVideo.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VIDEOS);
        case TdApi.MessageStory.CONSTRUCTOR:
          return getStoryRestrictionText(chat);
        case TdApi.MessageVideoNote.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VIDEO_NOTES);
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VOICE_NOTES);
        // RightId.SEND_BASIC_MESSAGES
        case TdApi.MessageText.CONSTRUCTOR:
        case TdApi.MessageAnimatedEmoji.CONSTRUCTOR:
        case TdApi.MessageVenue.CONSTRUCTOR:
        case TdApi.MessageLocation.CONSTRUCTOR:
        case TdApi.MessageProximityAlertTriggered.CONSTRUCTOR:
        case TdApi.MessageContact.CONSTRUCTOR:
        case TdApi.MessageInvoice.CONSTRUCTOR:
        case TdApi.MessagePaymentSuccessful.CONSTRUCTOR:
        case TdApi.MessagePaymentSuccessfulBot.CONSTRUCTOR:
          return getBasicMessageRestrictionText(chat);

        case TdApi.MessageGame.CONSTRUCTOR:
        case TdApi.MessageGameScore.CONSTRUCTOR:
          return getGameRestrictionText(chat);

        // None of these
        case TdApi.MessageCall.CONSTRUCTOR:
        case TdApi.MessageBasicGroupChatCreate.CONSTRUCTOR:
        case TdApi.MessageBotWriteAccessAllowed.CONSTRUCTOR:
        case TdApi.MessageChatAddMembers.CONSTRUCTOR:
        case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        case TdApi.MessageChatChangeTitle.CONSTRUCTOR:
        case TdApi.MessageChatDeleteMember.CONSTRUCTOR:
        case TdApi.MessageChatDeletePhoto.CONSTRUCTOR:
        case TdApi.MessageChatJoinByLink.CONSTRUCTOR:
        case TdApi.MessageChatJoinByRequest.CONSTRUCTOR:
        case TdApi.MessageChatSetMessageAutoDeleteTime.CONSTRUCTOR:
        case TdApi.MessageChatSetTheme.CONSTRUCTOR:
        case TdApi.MessageChatSetBackground.CONSTRUCTOR:
        case TdApi.MessageChatShared.CONSTRUCTOR:
        case TdApi.MessageChatUpgradeFrom.CONSTRUCTOR:
        case TdApi.MessageChatUpgradeTo.CONSTRUCTOR:
        case TdApi.MessageContactRegistered.CONSTRUCTOR:
        case TdApi.MessageCustomServiceAction.CONSTRUCTOR:
        case TdApi.MessageForumTopicCreated.CONSTRUCTOR:
        case TdApi.MessageForumTopicEdited.CONSTRUCTOR:
        case TdApi.MessageForumTopicIsClosedToggled.CONSTRUCTOR:
        case TdApi.MessageForumTopicIsHiddenToggled.CONSTRUCTOR:
        case TdApi.MessageGiftedPremium.CONSTRUCTOR:
        case TdApi.MessagePremiumGiftCode.CONSTRUCTOR:
        case TdApi.MessagePremiumGiveawayCreated.CONSTRUCTOR:
        case TdApi.MessagePremiumGiveawayCompleted.CONSTRUCTOR:
        case TdApi.MessagePremiumGiveawayWinners.CONSTRUCTOR:
        case TdApi.MessagePremiumGiveaway.CONSTRUCTOR:
        case TdApi.MessageInviteVideoChatParticipants.CONSTRUCTOR:
        case TdApi.MessagePassportDataReceived.CONSTRUCTOR:
        case TdApi.MessagePassportDataSent.CONSTRUCTOR:
        case TdApi.MessagePinMessage.CONSTRUCTOR:
        case TdApi.MessageScreenshotTaken.CONSTRUCTOR:
        case TdApi.MessageSuggestProfilePhoto.CONSTRUCTOR:
        case TdApi.MessageSupergroupChatCreate.CONSTRUCTOR:
        case TdApi.MessageUnsupported.CONSTRUCTOR:
        case TdApi.MessageUsersShared.CONSTRUCTOR:
        case TdApi.MessageVideoChatEnded.CONSTRUCTOR:
        case TdApi.MessageVideoChatScheduled.CONSTRUCTOR:
        case TdApi.MessageVideoChatStarted.CONSTRUCTOR:
        case TdApi.MessageWebAppDataReceived.CONSTRUCTOR:
        case TdApi.MessageWebAppDataSent.CONSTRUCTOR:
          // None of these messages ever passed to this method,
          // assuming we want to check RightId.SEND_BASIC_MESSAGES
          return getBasicMessageRestrictionText(chat);
        default:
          Td.assertMessageContent_d40af239();
          throw Td.unsupported(message.content);
      }
    }
    // Assuming if null is passed, we want to check if we can write text messages
    return getBasicMessageRestrictionText(chat);
  }

  public CharSequence getRestrictionText (TdApi.Chat chat, TdApi.InputMessageContent content) {
    if (content != null) {
      switch (content.getConstructor()) {
        case TdApi.InputMessageAudio.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_AUDIO);
        case TdApi.InputMessageDocument.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_DOCS);
        case TdApi.InputMessagePhoto.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_PHOTOS);
        case TdApi.InputMessageVideo.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VIDEOS);
        case TdApi.InputMessageVideoNote.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VIDEO_NOTES);
        case TdApi.InputMessageVoiceNote.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_VOICE_NOTES);
        case TdApi.InputMessagePoll.CONSTRUCTOR:
          return getDefaultRestrictionText(chat, RightId.SEND_POLLS);
        // RightId.SEND_OTHER_MESSAGES
        case TdApi.InputMessageAnimation.CONSTRUCTOR:
          return getGifRestrictionText(chat);
        case TdApi.InputMessageSticker.CONSTRUCTOR:
          return getStickerRestrictionText(chat);
        case TdApi.InputMessageDice.CONSTRUCTOR:
          return getDiceRestrictionText(chat, ((TdApi.InputMessageDice) content).emoji);
        case TdApi.InputMessageGame.CONSTRUCTOR:
          return getGameRestrictionText(chat);

        // RightId.SEND_BASIC_MESSAGES
        case TdApi.InputMessageForwarded.CONSTRUCTOR: // TODO tdlib.getMessageLocally?
        case TdApi.InputMessageInvoice.CONSTRUCTOR:
        case TdApi.InputMessageLocation.CONSTRUCTOR:
        case TdApi.InputMessageText.CONSTRUCTOR:
        case TdApi.InputMessageVenue.CONSTRUCTOR:
        case TdApi.InputMessageContact.CONSTRUCTOR:
        case TdApi.InputMessageStory.CONSTRUCTOR:
          return getBasicMessageRestrictionText(chat);
        default:
          Td.assertInputMessageContent_4e99a3f();
          throw Td.unsupported(content);
      }
    }
    // Assuming if null is passed, we want to check if we can write text messages
    return getBasicMessageRestrictionText(chat);
  }

  public CharSequence getBasicMessageRestrictionText (TdApi.Chat chat) {
    return getDefaultRestrictionText(chat, RightId.SEND_BASIC_MESSAGES);
  }

  public CharSequence getDefaultRestrictionText (TdApi.Chat chat, @RightId int rightId) {
    final @StringRes int disabledMediaRes, restrictedMediaRes, restrictedMediaUntilRes;
    @StringRes int specificRes = R.string.UserDisabledMessages, specificUserRes = 0;
    //noinspection SwitchIntDef
    switch (rightId) {
      case RightId.SEND_BASIC_MESSAGES:
        disabledMediaRes = R.string.ChatDisabledMessages;
        restrictedMediaRes = R.string.ChatRestrictedMessages;
        restrictedMediaUntilRes = R.string.ChatRestrictedMessagesUntil;
        break;
      case RightId.SEND_AUDIO:
        disabledMediaRes = R.string.ChatDisabledAudio;
        restrictedMediaRes = R.string.ChatRestrictedAudio;
        restrictedMediaUntilRes = R.string.ChatRestrictedAudioUntil;
        break;
      case RightId.SEND_DOCS:
        disabledMediaRes = R.string.ChatDisabledDocs;
        restrictedMediaRes = R.string.ChatRestrictedDocs;
        restrictedMediaUntilRes = R.string.ChatRestrictedDocsUntil;
        break;
      case RightId.SEND_PHOTOS:
        disabledMediaRes = R.string.ChatDisabledPhoto;
        restrictedMediaRes = R.string.ChatRestrictedPhoto;
        restrictedMediaUntilRes = R.string.ChatRestrictedPhotoUntil;
        break;
      case RightId.SEND_VIDEOS:
        disabledMediaRes = R.string.ChatDisabledVideo;
        restrictedMediaRes = R.string.ChatRestrictedVideo;
        restrictedMediaUntilRes = R.string.ChatRestrictedVideoUntil;
        break;
      case RightId.SEND_VOICE_NOTES:
        disabledMediaRes = R.string.ChatDisabledVoice;
        restrictedMediaRes = R.string.ChatRestrictedVoice;
        restrictedMediaUntilRes = R.string.ChatRestrictedVoiceUntil;
        specificUserRes = R.string.XRestrictedVoiceMessages;
        break;
      case RightId.SEND_VIDEO_NOTES:
        disabledMediaRes = R.string.ChatDisabledVideoNotes;
        restrictedMediaRes = R.string.ChatRestrictedVideoNotes;
        restrictedMediaUntilRes = R.string.ChatRestrictedVideoNotesUntil;
        specificUserRes = R.string.XRestrictedVideoMessages;
        break;
      case RightId.SEND_OTHER_MESSAGES:
        disabledMediaRes = R.string.ChatDisabledOther;
        restrictedMediaRes = R.string.ChatRestrictedOther;
        restrictedMediaUntilRes = R.string.ChatRestrictedOtherUntil;
        break;
      case RightId.SEND_POLLS:
        disabledMediaRes = R.string.ChatDisabledPolls;
        restrictedMediaRes = R.string.ChatRestrictedPolls;
        restrictedMediaUntilRes = R.string.ChatRestrictedPollsUntil;
        break;
      default:
        throw new IllegalArgumentException(Lang.getResourceEntryName(rightId));
    }
    return buildRestrictionText(chat,
      rightId,
      disabledMediaRes, restrictedMediaRes, restrictedMediaUntilRes,
      specificRes, specificUserRes
    );
  }

  public CharSequence getVoiceVideoRestrictionText (TdApi.Chat chat, boolean needVideo) {
    return getDefaultRestrictionText(chat, needVideo ? RightId.SEND_VIDEO_NOTES : RightId.SEND_VOICE_NOTES);
  }

  public CharSequence getGifRestrictionText (TdApi.Chat chat) {
    return buildRestrictionText(chat, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledGifs, R.string.ChatRestrictedGifs, R.string.ChatRestrictedGifsUntil);
  }

  public CharSequence getStickerRestrictionText (TdApi.Chat chat) {
    return buildRestrictionText(chat, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledStickers, R.string.ChatRestrictedStickers, R.string.ChatRestrictedStickersUntil);
  }

  public CharSequence getGameRestrictionText (TdApi.Chat chat) {
    return buildRestrictionText(chat, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledGames, R.string.ChatRestrictedGames, R.string.ChatRestrictedGamesUntil);
  }

  public CharSequence getStoryRestrictionText (TdApi.Chat chat) {
    Tdlib.RestrictionStatus photoStatus = getRestrictionStatus(chat, RightId.SEND_PHOTOS);
    Tdlib.RestrictionStatus videoStatus = getRestrictionStatus(chat, RightId.SEND_VIDEOS);
    Tdlib.RestrictionStatus status;
    @RightId int rightId;
    if (photoStatus == null || videoStatus == null) {
      if (videoStatus != null) {
        rightId = RightId.SEND_VIDEOS;
        status = videoStatus;
      } else {
        rightId = RightId.SEND_PHOTOS;
        status = photoStatus;
      }
    } else if (photoStatus.isGlobal() != videoStatus.isGlobal()) {
      if (photoStatus.isGlobal()) {
        rightId = RightId.SEND_VIDEOS;
        status = videoStatus;
      } else {
        rightId = RightId.SEND_PHOTOS;
        status = photoStatus;
      }
    } else {
      status = videoStatus;
      rightId = RightId.SEND_VIDEOS;
    }
    return buildRestrictionText(status,
      chat, rightId,
      R.string.ChatDisabledStory, R.string.ChatRestrictedStory, R.string.ChatRestrictedStoryUntil
    );
  }

  public CharSequence getInlineRestrictionText (TdApi.Chat chat) {
    return buildRestrictionText(chat, RightId.SEND_OTHER_MESSAGES, R.string.ChatDisabledBots, R.string.ChatRestrictedBots, R.string.ChatRestrictedBotsUntil);
  }

  public CharSequence getPollRestrictionText (TdApi.Chat chat) {
    return getDefaultRestrictionText(chat, RightId.SEND_POLLS);
  }

  public CharSequence getDiceRestrictionText (TdApi.Chat chat, String emoji) {
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
    return buildRestrictionText(chat, RightId.SEND_OTHER_MESSAGES, disabledRes, restrictedRes, restrictedUntilRes);
  }

  public CharSequence buildRestrictionText (TdApi.Chat chat, @RightId int rightId, @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes) {
    return buildRestrictionText(chat, rightId, defaultRes, specificRes, specificUntilRes, R.string.UserDisabledMessages, 0);
  }

  public CharSequence buildRestrictionText (TdApi.Chat chat, @RightId int rightId,
                                            @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes,
                                            @StringRes int defaultUserRes, @StringRes int specificUserRes) {
    RestrictionStatus status = getRestrictionStatus(chat, rightId);
    return buildRestrictionText(status,
      chat, rightId,
      defaultRes, specificRes, specificUntilRes,
      defaultUserRes, specificUserRes
    );
  }

  public CharSequence buildRestrictionText (@Nullable RestrictionStatus status, TdApi.Chat chat, @RightId int rightId, @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes) {
    return buildRestrictionText(status, chat, rightId, defaultRes, specificRes, specificUntilRes, R.string.UserDisabledMessages, 0);
  }

  public CharSequence buildRestrictionText (@Nullable RestrictionStatus status,
                                            TdApi.Chat chat, @RightId int rightId,
                                            @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes,
                                            @StringRes int defaultUserRes, @StringRes int specificUserRes) {
    if (status != null) {
      switch (rightId) {
        case RightId.SEND_BASIC_MESSAGES:
        case RightId.SEND_AUDIO:
        case RightId.SEND_DOCS:
        case RightId.SEND_PHOTOS:
        case RightId.SEND_VIDEOS:
        case RightId.SEND_VOICE_NOTES:
        case RightId.SEND_VIDEO_NOTES:
        case RightId.SEND_OTHER_MESSAGES:
        case RightId.SEND_POLLS: {
          break;
        }
        case RightId.EMBED_LINKS: {
          // check if there is any restriction text for RightId.SEND_BASIC_MESSAGES
          CharSequence restriction = getBasicMessageRestrictionText(chat);
          if (restriction != null)
            return restriction;
          break;
        }
        // admin rights
        case RightId.ADD_NEW_ADMINS:
        case RightId.BAN_USERS:
        case RightId.CHANGE_CHAT_INFO:
        case RightId.DELETE_MESSAGES:
        case RightId.EDIT_MESSAGES:
        case RightId.INVITE_USERS:
        case RightId.MANAGE_VIDEO_CHATS:
        case RightId.MANAGE_TOPICS:
        case RightId.POST_STORIES:
        case RightId.EDIT_STORIES:
        case RightId.DELETE_STORIES:
        case RightId.PIN_MESSAGES:
        case RightId.READ_MESSAGES:
        case RightId.REMAIN_ANONYMOUS:
          break;
      }
      if (status.isUserChat()) {
        switch (status.status) {
          case RESTRICTION_STATUS_RESTRICTED:
            if (specificUserRes != 0) {
              return Lang.getStringBold(specificUserRes, cache().userFirstName(chatUserId(chat)));
            }
            break;
          case RESTRICTION_STATUS_EVERYONE:
            return Lang.getString(defaultUserRes);
        }
      }
      switch (status.status) {
        case RESTRICTION_STATUS_BANNED:
          return status.untilDate != 0 ? Lang.getString(R.string.ChatBannedUntil, Lang.getUntilDate(status.untilDate, TimeUnit.SECONDS)) : Lang.getString(R.string.ChatBanned);
        case RESTRICTION_STATUS_RESTRICTED:
          return status.untilDate != 0 ? Lang.getString(specificUntilRes, Lang.getUntilDate(status.untilDate, TimeUnit.SECONDS)) : Lang.getString(specificRes);
        case RESTRICTION_STATUS_UNAVAILABLE:
        case RESTRICTION_STATUS_EVERYONE:
          return Lang.getString(defaultRes);
      }

      throw new UnsupportedOperationException();
    }
    return null;
  }

  public boolean showRestriction (TdApi.Chat chat, @RightId int rightId, @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes) {
    CharSequence res = buildRestrictionText(chat, rightId, defaultRes, specificRes, specificUntilRes);
    if (res != null) {
      UI.showToast(res, Toast.LENGTH_SHORT);
      return true;
    }
    return false;
  }

  public boolean canAddWebPagePreviews (TdApi.Chat chat) {
    return getRestrictionStatus(chat, RightId.EMBED_LINKS) == null;
  }

  public boolean canSendBasicMessage (long chatId) {
    return chatId != 0 && canSendBasicMessage(chat(chatId));
  }
  public boolean canSendBasicMessage (TdApi.Chat chat) {
    return canSendMessage(chat, RightId.SEND_BASIC_MESSAGES);
  }

  public boolean canSendSendSomeMedia (TdApi.Chat chat) {
    return canSendSendSomeMedia(chat, false);
  }

  public boolean canSendSendSomeMedia (TdApi.Chat chat, boolean checkGlobal) {
    for (int rightId : EditRightsController.SEND_MEDIA_RIGHT_IDS) {
      Tdlib.RestrictionStatus restrictionStatus = getRestrictionStatus(chat, rightId);
      if (restrictionStatus == null || checkGlobal && !restrictionStatus.isGlobal()) {
        return true;
      }
    }

    return false;
  }

  public boolean canSendMessage (TdApi.Chat chat, @RightId int kindResId) {
    switch (kindResId) {
      case RightId.SEND_BASIC_MESSAGES:
      case RightId.SEND_AUDIO:
      case RightId.SEND_DOCS:
      case RightId.SEND_PHOTOS:
      case RightId.SEND_VIDEOS:
      case RightId.SEND_VOICE_NOTES:
      case RightId.SEND_VIDEO_NOTES:
      case RightId.SEND_OTHER_MESSAGES:
      case RightId.SEND_POLLS:
        break;
      case RightId.EMBED_LINKS:
      case RightId.ADD_NEW_ADMINS:
      case RightId.BAN_USERS:
      case RightId.CHANGE_CHAT_INFO:
      case RightId.DELETE_MESSAGES:
      case RightId.EDIT_MESSAGES:
      case RightId.INVITE_USERS:
      case RightId.MANAGE_VIDEO_CHATS:
      case RightId.MANAGE_TOPICS:
      case RightId.POST_STORIES:
      case RightId.EDIT_STORIES:
      case RightId.DELETE_STORIES:
      case RightId.PIN_MESSAGES:
      case RightId.READ_MESSAGES:
      case RightId.REMAIN_ANONYMOUS:
      default:
        throw new IllegalArgumentException(Lang.getResourceEntryName(kindResId));
    }
    return getRestrictionStatus(chat, kindResId) == null;
  }

  public boolean isSettingSuggestion (TdApi.SuggestedAction action) {
    return action.getConstructor() == TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR || action.getConstructor() == TdApi.SuggestedActionCheckPassword.CONSTRUCTOR;
  }

  public int getSettingSuggestionCount () {
    synchronized (dataLock) {
      int count = 0;
      for (TdApi.SuggestedAction action : suggestedActions) {
        if (isSettingSuggestion(action)) {
          count++;
        }
      }
      return count;
    }
  }

  public TdlibSingleUnreadReactionsManager singleUnreadReactionsManager () {
    return unreadReactionsManager;
  }

  @Nullable
  public TdApi.UnreadReaction getSingleUnreadReaction (long chatId) {
    // If chat has one unread reaction, returns it. May be null
    return unreadReactionsManager.getSingleUnreadReaction(chatId);
  }

  public boolean haveAnySettingsSuggestions () {
    synchronized (dataLock) {
      for (TdApi.SuggestedAction action : suggestedActions) {
        if (isSettingSuggestion(action))
          return true;
      }
      return false;
    }
  }

  public TdApi.SuggestedAction singleSettingsSuggestion () {
    synchronized (dataLock) {
      TdApi.SuggestedAction suggestedAction = null;
      for (TdApi.SuggestedAction action : suggestedActions) {
        if (isSettingSuggestion(action)) {
          if (suggestedAction == null) {
            suggestedAction = action;
          } else {
            return null;
          }
        }
      }
      return suggestedAction;
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ResolvableProblem.NONE,
    ResolvableProblem.MIXED,
    ResolvableProblem.NOTIFICATIONS,
    ResolvableProblem.CHECK_PASSWORD,
    ResolvableProblem.CHECK_PHONE_NUMBER
  })
  public @interface ResolvableProblem {
    int
      NONE = 0,
      MIXED = 1,
      NOTIFICATIONS = 2,
      CHECK_PASSWORD = 3,
      CHECK_PHONE_NUMBER = 4;
  }

  @ResolvableProblem
  public int findResolvableProblem () {
    final boolean haveNotificationsProblem = notifications().hasLocalNotificationProblem();
    final TdApi.SuggestedAction singleAction = singleSettingsSuggestion();
    final boolean haveSuggestions = singleAction != null || haveAnySettingsSuggestions();
    final int totalCount = (singleAction != null ? 1 : haveSuggestions ? 2 : 0) + (haveNotificationsProblem ? 1 : 0);
    if (totalCount > 1) {
      return ResolvableProblem.MIXED;
    } else if (haveNotificationsProblem) {
      return ResolvableProblem.NOTIFICATIONS;
    } else if (singleAction != null) {
      switch (singleAction.getConstructor()) {
        case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR:
          return ResolvableProblem.CHECK_PASSWORD;
        case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR:
          return ResolvableProblem.CHECK_PHONE_NUMBER;
        default:
          throw new UnsupportedOperationException(singleAction.toString());
      }
    }
    return ResolvableProblem.NONE;
  }

  public String getMessageSenderTitle (TdApi.MessageSender sender) {
    if (isSelfSender(sender)) {
      return Lang.getString(R.string.YourAccount);
    } else if (!isChannel(sender)) {
      return Lang.getString(R.string.AnonymousAdmin);
    } else {
      return chatTitle(Td.getSenderId(sender));
    }
  }

  @Nullable
  public TdApi.ChatFolderIcon chatFolderIcon (TdApi.ChatFolder chatFolder) {
    if (chatFolder.icon != null && !StringUtils.isEmpty(chatFolder.icon.name)) {
      return chatFolder.icon;
    }
    TdApi.ChatFolderIcon result = clientExecuteT(new TdApi.GetChatFolderDefaultIconName(chatFolder), false);
    if (result != null && !StringUtils.isEmpty(result.name)) {
      return result;
    }
    return null;
  }

  public String chatFolderIconName (TdApi.ChatFolder chatFolder) {
    TdApi.ChatFolderIcon icon = chatFolderIcon(chatFolder);
    return icon != null ? icon.name : "";
  }

  public @DrawableRes int chatFolderIconDrawable (TdApi.ChatFolder chatFolder, @DrawableRes int defaultIcon) {
    return TD.findFolderIcon(chatFolderIcon(chatFolder), defaultIcon);
  }

  public void addChatsToChatFolder (TdlibDelegate delegate, int chatFolderId, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(chatFolder);
      } else {
        addChatsToChatFolder(delegate, chatFolderId, chatFolder, chatIds);
      }
    });
  }

  public void addChatsToChatFolder (TdlibDelegate delegate, int chatFolderId, TdApi.ChatFolder chatFolder, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    LongSet pinnedChatIds = new LongSet(chatFolder.pinnedChatIds);
    LongSet includedChatIds = new LongSet(chatFolder.includedChatIds);
    for (long chatId : chatIds) {
      if (pinnedChatIds.has(chatId) || includedChatIds.has(chatId)) {
        continue;
      }
      includedChatIds.add(chatId);
    }
    if (includedChatIds.size() == chatFolder.includedChatIds.length) {
      return;
    }
    int chatCount = pinnedChatIds.size() + includedChatIds.size();
    int secretChatCount = 0;
    for (long pinnedChatId : pinnedChatIds) {
      if (ChatId.isSecret(pinnedChatId)) secretChatCount++;
    }
    for (long includedChatId : includedChatIds) {
      if (ChatId.isSecret(includedChatId)) secretChatCount++;
    }
    int nonSecretChatCount = chatCount - secretChatCount;
    long chosenChatCountMax = tdlib().chatFolderChosenChatCountMax();
    if (secretChatCount > chosenChatCountMax || nonSecretChatCount > chosenChatCountMax) {
      if (hasPremium()) {
        CharSequence text = Lang.getMarkdownString(delegate, R.string.ChatsInFolderLimitReached, chosenChatCountMax);
        UI.showCustomToast(text, Toast.LENGTH_LONG, 0);
      } else {
        send(new TdApi.GetPremiumLimit(new TdApi.PremiumLimitTypeChatFolderChosenChatCount()), (premiumLimit, error) -> {
          CharSequence text;
          if (error != null) {
            text = Lang.getMarkdownString(delegate, R.string.ChatsInFolderLimitReached, chosenChatCountMax);
          } else {
            text = Lang.getMarkdownString(delegate, R.string.PremiumRequiredChatsInFolder, premiumLimit.defaultValue, premiumLimit.premiumValue);
          }
          UI.showCustomToast(text, Toast.LENGTH_LONG, 0);
        });
      }
      return;
    }
    chatFolder.includedChatIds = includedChatIds.toArray();
    chatFolder.excludedChatIds = ArrayUtils.removeAll(chatFolder.excludedChatIds, chatIds);
    send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (chatFolderInfo, error) -> {
      if (error != null) {
        UI.showError(error);
      }
    });
  }

  public void removeChatFromChatFolder (int chatFolderId, long chatId) {
    removeChatsFromChatFolder(chatFolderId, new long[] {chatId});
  }

  public void removeChatsFromChatFolder (int chatFolderId, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        removeChatsFromChatFolder(chatFolderId, chatFolder, chatIds);
      }
    });
  }

  public void removeChatsFromChatFolder (int chatFolderId, TdApi.ChatFolder chatFolder, long[] chatIds) {
    if (chatIds.length == 0) {
      return;
    }
    LongList pinnedChatIds = new LongList(chatFolder.pinnedChatIds);
    LongSet includedChatIds = new LongSet(chatFolder.includedChatIds);
    LongSet excludedChatIds = new LongSet(chatFolder.excludedChatIds);
    for (long chatId : chatIds) {
       boolean removed = pinnedChatIds.remove(chatId) | includedChatIds.remove(chatId);
       if (removed && Config.CHAT_FOLDERS_SMART_CHAT_DELETION_ENABLED) {
         TdApi.Chat chat = chat(chatId);
         boolean isBotChat = isBotChat(chat);
         boolean isUserChat = isUserChat(chat) && !isBotChat;
         boolean isContactChat = isUserChat && TD.isContact(chatUser(chat));
         if (!chatFolder.includeContacts && isUserChat && isContactChat) continue;
         if (!chatFolder.includeNonContacts && isUserChat && !isContactChat) continue;
         if (!chatFolder.includeGroups && TD.isMultiChat(chat)) continue;
         if (!chatFolder.includeChannels && isChannelChat(chat)) continue;
         if (!chatFolder.includeBots && isBotChat) continue;
       }
       excludedChatIds.add(chatId);
    }
    chatFolder.pinnedChatIds = pinnedChatIds.get();
    chatFolder.includedChatIds = includedChatIds.toArray();
    chatFolder.excludedChatIds = excludedChatIds.toArray();
    send(new TdApi.EditChatFolder(chatFolderId, chatFolder), (chatFolderInfo, error) -> {
      if (error != null) {
        UI.showError(error);
      }
    });
  }
}
