package org.thunderdog.challegram.telegram;

import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.filegen.TdlibFileGenerationManager;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.gif.GifBridge;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.sync.SyncHelper;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.UserProvider;
import org.thunderdog.challegram.util.WrapperProvider;
import org.thunderdog.challegram.util.text.Letters;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.collection.LongSparseLongArray;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.core.util.JobList;
import me.vkryl.td.ChatId;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.JSON;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

/**
 * Date: 2/14/18
 * Author: default
 */

public class Tdlib implements TdlibProvider, Settings.SettingsChangeListener {
  @Override
  public final int accountId () {
    return id();
  }

  @NonNull
  @Override
  public final Tdlib tdlib () {
    return this;
  }

  public static final int STATE_UNKNOWN = -1;
  public static final int STATE_CONNECTED = 0;
  public static final int STATE_CONNECTING_TO_PROXY = 1;
  public static final int STATE_CONNECTING = 2;
  public static final int STATE_UPDATING = 3;
  public static final int STATE_WAITING = 4;

  public static final int STATUS_UNKNOWN = 0;
  public static final int STATUS_UNAUTHORIZED = 1;
  public static final int STATUS_READY = 2;

  public static final int CHAT_ACCESS_TEMPORARY = 1;
  public static final int CHAT_ACCESS_OK = 0;
  public static final int CHAT_ACCESS_FAIL = -1;
  public static final int CHAT_ACCESS_BANNED = -2; // Access banned
  public static final int CHAT_ACCESS_PRIVATE = -3; // Needs an invitation

  private final Object handlerLock = new Object();
  private TdlibUi _handler;

  private final TdApi.TdlibParameters parameters;
  private final Client.ResultHandler okHandler = object -> {
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR:
        break;
      case TdApi.Error.CONSTRUCTOR:
        UI.showError(object);
        break;
    }
  };
  private final Client.ResultHandler configHandler = object -> {
    if (object instanceof TdApi.JsonValue) {
      TdApi.JsonValue json = (TdApi.JsonValue) object;
      setApplicationConfig(json, JSON.stringify(json));
    } else {
      Log.e("getApplicationConfig failed: %s", TD.toErrorString(object));
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
  private final Client.ResultHandler imageLoadHandler = object -> {
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR:
        break;
      case TdApi.File.CONSTRUCTOR:
        TdApi.File file = (TdApi.File) object;
        if (file.local.isDownloadingCompleted) {
          ImageLoader.instance().onLoad(Tdlib.this, file);
        } else if (!file.local.isDownloadingActive) {
          Log.e(Log.TAG_IMAGE_LOADER, "WARNING: Image load not started");
        }
        break;
      case TdApi.Error.CONSTRUCTOR:
        Log.e(Log.TAG_IMAGE_LOADER, "DownloadFile failed: %s", TD.toErrorString(object));
        break;
      default:
        Log.unexpectedTdlibResponse(object, TdApi.DownloadFile.class, TdApi.Ok.class, TdApi.Error.class);
        break;
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
      this.client = Client.create(this, this, this, tdlib.debugInstance);
      tdlib.updateParameters(client);
      if (Config.NEED_ONLINE) {
        if (tdlib.isOnline) {
          client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(true)), tdlib.okHandler);
        }
      }
      tdlib.context.modifyClient(tdlib, client);
      this.resources = new TdlibResourceManager(tdlib, BuildConfig.TELEGRAM_RESOURCES_CHANNEL);
      this.updates = new TdlibResourceManager(tdlib, BuildConfig.TELEGRAM_UPDATES_CHANNEL);
      if (!tdlib.context.inRecoveryMode()) {
        init();
      }
    }

    public void runOnTdlibThread (Runnable after) {
      runOnTdlibThread(after, 0);
    }

    public void runOnTdlibThread (Runnable after, double timeout) {
      client.send(new TdApi.SetAlarm(timeout), ignored -> after.run());
    }

    private final AtomicBoolean databaseOpened = new AtomicBoolean(false);
    private Runnable openTakesTooLong;

    private void scheduleOptimizationCheck () {
      synchronized (databaseOpened) {
        if (!databaseOpened.get() && openTakesTooLong == null) {
          openTakesTooLong = () -> {
            synchronized (databaseOpened) {
              if (!databaseOpened.get()) {
                tdlib.setIsOptimizing(true);
              }
            }
          };
          tdlib.ui().postDelayed(openTakesTooLong, 1000);
        }
      }
    }

    private void onDatabaseOpened () {
      synchronized (databaseOpened) {
        if (!databaseOpened.getAndSet(true)) {
          if (openTakesTooLong != null) {
            tdlib.ui().removeCallbacks(openTakesTooLong);
            openTakesTooLong = null;
          }
          tdlib.setIsOptimizing(false);
        }
      }
    }

    public void init () {
      if (initializationTime != 0)
        return;
      initializationTime = SystemClock.uptimeMillis();
      StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
      client.send(new TdApi.SetTdlibParameters(tdlib.parameters), result -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          Tracer.onTdlibFatalError(tdlib.id(), TdApi.SetTdlibParameters.class, (TdApi.Error) result, stackTrace);
        }
      });
      long time = SystemClock.uptimeMillis();
      client.send(new TdApi.CheckDatabaseEncryptionKey(), result -> {
        long elapsed = SystemClock.uptimeMillis() - time;
        TDLib.Tag.td_init("CheckDatabaseEncryptionKey response in %dms, accountId:%d", elapsed, tdlib.accountId);
        onDatabaseOpened();
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          Tracer.onTdlibFatalError(tdlib.id(), TdApi.CheckDatabaseEncryptionKey.class, (TdApi.Error) result, stackTrace);
        }
      });
      scheduleOptimizationCheck();
      final TdApi.Function startup;
      if (tdlib.accountId == 0 && Settings.instance().needProxyLegacyMigrateCheck()) {
        startup = new TdApi.GetProxies();
      } else {
        startup = new TdApi.SetAlarm(0);
      }
      client.send(startup, (result) -> {
        if (result.getConstructor() == TdApi.Proxies.CONSTRUCTOR) {
          TdApi.Proxy[] proxies = ((TdApi.Proxies) result).proxies;
          for (TdApi.Proxy proxy : proxies) {
            int proxyId = Settings.instance().addOrUpdateProxy(proxy.server, proxy.port, proxy.type, null, proxy.isEnabled);
            if (proxy.isEnabled) {
              tdlib.setProxy(proxyId, proxy.server, proxy.port, proxy.type);
            }
          }
        } else {
          int proxyId = Settings.instance().getEffectiveProxyId();
          Settings.Proxy proxy = Settings.instance().getProxyConfig(proxyId);
          if (proxy != null) {
            tdlib.setProxy(proxyId, proxy.server, proxy.port, proxy.type);
          } else {
            tdlib.setProxy(Settings.PROXY_ID_NONE, null, 0, null);
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
      client.send(new TdApi.Close(), tdlib.okHandler);
    }

    public void close () {
      Log.i(Log.TAG_ACCOUNTS, "Calling client.close(), accountId:%d", tdlib.accountId);
      long ms = SystemClock.uptimeMillis();
      client.close();
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
  private @ConnectionState int connectionState = STATE_UNKNOWN;

  private final Object clientLock = new Object();
  private final Object dataLock = new Object();
  private final HashMap<Long, TdApi.Chat> chats = new HashMap<>();
  private final HashMap<String, TdlibChatList> chatLists = new HashMap<>();
  private final StickerSet
    animatedEmoji = new StickerSet(AnimatedEmojiListener.TYPE_EMOJI, "animatedemojies", false),
    // animatedTgxEmoji = new StickerSet(AnimatedEmojiListener.TYPE_TGX, "AnimatedTgxEmojies", false),
    animatedDiceExplicit = new StickerSet(AnimatedEmojiListener.TYPE_DICE, "BetterDice", true);
  private final HashSet<Long> knownChatIds = new HashSet<>();
  private final HashMap<Long, Integer> chatOnlineMemberCount = new HashMap<>();
  private final TdlibCache cache;
  private final TdlibListeners listeners;
  private final TdlibFilesManager filesManager;
  private final TdlibStatusManager statusManager;
  private final TdlibContactManager contactManager;
  private final TdlibQuickAckManager quickAckManager;
  private final TdlibSettingsManager settingsManager;
  private final TdlibWallpaperManager wallpaperManager;
  private final TdlibNotificationManager notificationManager;
  private final TdlibFileGenerationManager fileGenerationManager;

  private final HashSet<Integer> channels = new HashSet<>();
  private final LongSparseLongArray accessibleChatTimers = new LongSparseLongArray();

  private long authorizationDate = 0;
  private int supergroupMaxSize = 100000;
  private boolean suggestOnlyApiStickers;
  private int maxGroupCallParticipantCount = 10000;
  private long roundVideoBitrate = 1000, roundAudioBitrate = 64, roundVideoMaxSize = 12582912, roundVideoDiameter = 384;
  private int forwardMaxCount = 100;
  private int groupMaxSize = 200;
  private int pinnedChatsMaxCount = 5, pinnedArchivedChatsMaxCount = 100;
  private double emojiesAnimatedZoom = .75f;
  private boolean youtubePipDisabled, qrLoginCamera, dialogFiltersTooltip, dialogFiltersEnabled;
  private String qrLoginCode;
  private String[] diceEmoji;
  private boolean callsEnabled = true, expectBlocking, isLocationVisible;
  private boolean canIgnoreSensitiveContentRestrictions, ignoreSensitiveContentRestrictions;
  private boolean canArchiveAndMuteNewChatsFromUnknownUsers, archiveAndMuteNewChatsFromUnknownUsers;
  private RtcServer[] rtcServers;

  private long unixTime;
  private long unixTimeReceived;

  private int disableTopChats = -1;
  private boolean disableSentScheduledMessageNotifications;
  private String animationSearchBotUsername = "gif";
  private String venueSearchBotUsername = "foursquare";
  private String photoSearchBotUsername = "pic";
  // private String animatedEmojiStickerSetName = "animatedemojies", animatedDiceStickerSetName;

  private String languagePackId;
  private String suggestedLanguagePackId;
  private TdApi.LanguagePackInfo suggestedLanguagePackInfo;

  private long connectionLossTime = SystemClock.uptimeMillis();

  private String tMeUrl;
  private String tdlibVersionSignature;

  private long callConnectTimeoutMs = 30000;
  private long callPacketTimeoutMs = 10000;

  private long repliesBotChatId = TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID;
  private long telegramServiceNotificationsChatId = TdConstants.TELEGRAM_ACCOUNT_ID;

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

  private int installedStickerSetCount;
  private int[] favoriteStickerIds;
  private int unreadTrendingStickerSetsCount;

  private boolean debugInstance;
  private boolean instancePaused;
  private final AtomicInteger referenceCount = new AtomicInteger(0);

  /*package*/ Tdlib (TdlibAccount account, boolean debug) {
    this.context = account.context;
    this.accountId = account.id;
    this.debugInstance = debug;
    this.hasUnprocessedPushes = account.hasUnprocessedPushes();
    this.isLoggingOut = account.isLoggingOut();

    this.parameters = new TdApi.TdlibParameters(
      false, null, null, // updateParameters
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
    if (BitwiseUtils.getFlag(newSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) != BitwiseUtils.getFlag(oldSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) && !isDebugInstance()) {
      if (BitwiseUtils.getFlag(newSettings, Settings.SETTING_FLAG_EXPLICIT_DICE) && !animatedDiceExplicit.isLoaded()) {
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
    final String deviceToken = context.getToken();
    if (StringUtils.isEmpty(deviceToken) || status != STATUS_READY)
      return;
    int myUserId = myUserId();
    if (myUserId == 0)
      return;
    int[] availableUserIds = context.availableUserIds(debugInstance);
    int[] otherUserIds = ArrayUtils.removeElement(availableUserIds, Arrays.binarySearch(availableUserIds, myUserId));
    if (TdlibSettingsManager.checkRegisteredDeviceToken(id(), myUserId, deviceToken, otherUserIds, false)) {
      Log.i(Log.TAG_FCM, "Device token already registered. accountId:%d", accountId);
      context.setDeviceRegistered(accountId, true);
      U.run(onDone);
      return;
    }
    Log.i(Log.TAG_FCM, "Registering device token... accountId:%d", accountId);
    context.setDeviceRegistered(accountId, false);
    TdApi.DeviceTokenFirebaseCloudMessaging token = new TdApi.DeviceTokenFirebaseCloudMessaging(deviceToken, true);
    incrementReferenceCount(REFERENCE_TYPE_JOB);
    client().send(new TdApi.RegisterDevice(token, otherUserIds), result -> {
      switch (result.getConstructor()) {
        case TdApi.PushReceiverId.CONSTRUCTOR:
          Log.i(Log.TAG_FCM, "Successfully registered device token:%s, accountId:%d, otherUserIdsCount:%d", deviceToken, accountId, otherUserIds.length);
          Settings.instance().putNotificationReceiverId(((TdApi.PushReceiverId) result).id, accountId);
          TdlibSettingsManager.setRegisteredDevice(accountId, myUserId, deviceToken, otherUserIds);
          context().setDeviceRegistered(accountId, true);
          context().unregisterDevices(debugInstance, accountId, availableUserIds);
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
    synchronized (clientLock) {
      int referenceCount = this.referenceCount.incrementAndGet();
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
  }

  private void decrementReferenceCount (int type) {
    synchronized (clientLock) {
      int referenceCount = this.referenceCount.decrementAndGet();
      if (referenceCount < 0) {
        RuntimeException e = new IllegalStateException("type == " + type);
        Tracer.onOtherError(e);
        throw e;
      }
      Log.v(Log.TAG_ACCOUNTS, "accountId:%d, referenceCount:%d, type:%d", accountId, referenceCount, type);
      schedulePause();
    }
  }

  private long restartTimeout, restartScheduledTime;

  // keep_alive

  private boolean keepAlive, hasUnprocessedPushes, isLoggingOut;

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

  private void setLoggingOut (boolean isLoggingOut) {
    if (this.isLoggingOut != isLoggingOut) {
      this.isLoggingOut = isLoggingOut;
      context().setLoggingOut(accountId, isLoggingOut);
      checkPauseTimeout();
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
    if (status == STATUS_UNKNOWN)
      return false; // TDLib takes too long to launch. Give it a chance to initialize
    if ((status == STATUS_UNAUTHORIZED && authorizationState != null && authorizationState.getConstructor() != TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR))
      return false; // User has started authorization process. Give them a chance to complete it.
    /*if (context().hasUi()) {
        // TODO limit couple most recent used accounts?
      }*/
    return !checkKeepAlive() && !account().keepAlive() && account().isDeviceRegistered() && referenceCount.get() == 0;
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
      Log.i(Log.TAG_ACCOUNTS, "Canceling TDLib restart, accountId:%d, referenceCount:%d, keepAlive:%b", accountId, referenceCount.get(), account().keepAlive());
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
      Log.i(Log.TAG_ACCOUNTS, "Cannot restart TDLib, because it is in use. referenceCount:%d, accountId:%d", referenceCount.get(), accountId);
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
      if (client != null && !instancePaused && !context.inRecoveryMode()) {
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

  private int status;
  private TdApi.AuthorizationState authorizationState;

  public TdApi.AuthorizationState authorizationState () {
    return authorizationState;
  }

  public void signOut () {
    if (context().preferredAccountId() == accountId) {
      int nextAccountId = context().findNextAccountId(accountId);
      if (nextAccountId != TdlibAccount.NO_ID) {
        context().changePreferredAccountId(nextAccountId, TdlibManager.SWITCH_REASON_UNAUTHORIZED);
      }
    }
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
    client().send(new TdApi.Destroy(), okHandler);
  }

  public boolean isCurrent () {
    return context().preferredAccountId() == accountId;
  }

  public boolean isAuthorized () {
    return authorizationState != null && authorizationState.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR;
  }

  public boolean isUnauthorized () {
    if (authorizationState != null) {
      switch (authorizationState.getConstructor()) {
        case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
        case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
          return true;
        case TdApi.AuthorizationStateReady.CONSTRUCTOR:
          return false;
        case TdApi.AuthorizationStateClosed.CONSTRUCTOR:
        case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
        case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
          break; // because we cannot know for sure
        default:
          throw new AssertionError(authorizationState);
      }
    }
    return false;
  }

  public int authorizationStatus () {
    return status;
  }

  private TdApi.AuthorizationState currentAuthState;

  @TdlibThread
  private void updateAuthState (ClientHolder context, TdApi.AuthorizationState newAuthState) {
    this.currentAuthState = newAuthState;
    closeListeners.trigger(true);

    int status;
    switch (newAuthState.getConstructor()) {
      case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR:
      case TdApi.AuthorizationStateClosing.CONSTRUCTOR: {
        status = STATUS_UNKNOWN;
        break;
      }
      case TdApi.AuthorizationStateClosed.CONSTRUCTOR: {
        status = STATUS_UNKNOWN;

        RunnableBool eraseActor;
        boolean eraseSuccess;

        boolean forceErase = false;
        if (isLoggingOut) { // FIXME TDLib: AuthorizationStateLoggedOut
          setLoggingOut(false);
          forceErase = true;
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
        break;
      }
      case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitRegistration.CONSTRUCTOR:
      case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
        status = STATUS_UNAUTHORIZED;
        synchronized (dataLock) {
          knownChatIds.clear();
          chats.clear();
        }
        break;
      }
      case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR: {
        status = STATUS_UNAUTHORIZED;
        setLoggingOut(true);
        break;
      }
      case TdApi.AuthorizationStateReady.CONSTRUCTOR: {
        status = STATUS_READY;
        setLoggingOut(false);
        break;
      }
      default:
        throw new AssertionError(newAuthState);
    }
    if (status != STATUS_UNKNOWN && Log.needMeasureLaunchSpeed() && !context.hasLogged()) {
      long timeSinceInitialization = context.timeSinceInitializationMs();
      long timeWasted = context.timeWasted();
      Log.v("INITIALIZATION: TDLIB FINISHED INITIALIZATION & SENT VALID AUTH STATE IN %dMS, WASTED: %dMS", timeSinceInitialization - timeWasted, timeWasted);
    }
    Log.i(Log.TAG_ACCOUNTS, "updateAuthState accountId:%d %s", accountId, newAuthState.getClass().getSimpleName());
    ui().sendMessage(ui().obtainMessage(MSG_ACTION_SET_STATUS, status, myUserId(), newAuthState));
    if (status == STATUS_READY) {
      setNeedPeriodicSync(true);
    } else if (status == STATUS_UNAUTHORIZED) {
      setNeedPeriodicSync(false);
    }

    if (status == STATUS_READY && stressTest > 0) {
      clientHolder().sendClose();
    }
  }

  private static TdApi.FormattedText makeUpdateText (String version, String changeLog) {
    String text = Lang.getStringSecure(R.string.ChangeLogText, version, changeLog);
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text, null);
    Td.parseMarkdown(formattedText);
    return formattedText;
  }

  private static void makeUpdateText (int major, int agesSinceBirthdate, int monthsSinceLastBirthday, int buildNo, String changeLogUrl, List<TdApi.Function> functions, List<TdApi.InputMessageContent> messages, boolean isLast) {
    /*if (isLast) {
      version = BuildConfig.OVERRIDEN_VERSION_NAME;
      int i = version.indexOf('-');
      if (i != -1) {
        version = version.substring(0, i);
      }
    }*/
    TdApi.FormattedText text = makeUpdateText(String.format(Locale.US, "%d.%d.%d.%d", major, agesSinceBirthdate, monthsSinceLastBirthday, buildNo), changeLogUrl);
    functions.add(new TdApi.GetWebPagePreview(text));
    functions.add(new TdApi.GetWebPageInstantView(changeLogUrl, false));
    messages.add(new TdApi.InputMessageText(text, false, false));
  }

  private boolean isOptimizing;

  public boolean isOptimizing () {
    return isOptimizing;
  }

  private void setIsOptimizing (boolean isOptimizing) {
    if (this.isOptimizing != isOptimizing) {
      this.isOptimizing = isOptimizing;
      context().global().notifyOptimizing(this, isOptimizing);
    }
  }

  private static boolean checkVersion (int version, int checkVersion, boolean isTest) {
    return version < checkVersion && (checkVersion <= BuildConfig.ORIGINAL_VERSION_CODE || isTest || BuildConfig.DEBUG);
  }

  private void processAuthState (int status, TdApi.AuthorizationState newAuthState, int userId) {
    int oldStatus = this.status;
    this.status = status;
    this.authorizationState = newAuthState;

    context.onAuthStateChanged(this, newAuthState, status, userId);
    listeners().updateAuthorizationState(newAuthState);
    if (this.status != STATUS_READY) {
      startupPerformed = false;
    }
    if (oldStatus == STATUS_UNKNOWN && status != oldStatus) {
      onInitialized();
    }
    if (oldStatus != STATUS_READY && status == STATUS_READY) {
      runStartupChecks();
    }
    if (oldStatus != STATUS_UNAUTHORIZED && status == STATUS_UNAUTHORIZED) {
      Log.i("Performing account cleanup for accountId:%d", accountId);
      listeners().performCleanup();
    } else if (status == STATUS_READY) {
      onPerformStartup();
    }
    if (status == STATUS_UNAUTHORIZED)
      checkChangeLogs(false, false);
  }

  public boolean checkChangeLogs (boolean alreadySent, boolean test) {
    if (status != STATUS_READY && status != STATUS_UNAUTHORIZED) {
      return false;
    }
    final String key = accountId + "_app_version";
    if (status == STATUS_UNAUTHORIZED || alreadySent) {
      Settings.instance().putInt(key, BuildConfig.ORIGINAL_VERSION_CODE);
      return alreadySent;
    }
    int prevVersion = test || Config.TEST_CHANGELOG ? 0 : Settings.instance().getInt(key, 0);
    if (prevVersion != BuildConfig.ORIGINAL_VERSION_CODE) {
      List<TdApi.InputMessageContent> updates = new ArrayList<>();
      List<TdApi.Function> functions = new ArrayList<>();
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
        makeUpdateText(0, 22, 5, APP_RELEASE_VERSION_2020_FEBRUARY, "https://telegra.ph/Telegram-X-02-29", functions, updates, true);
      }
      if (checkVersion(prevVersion, APP_RELEASE_VERSION_2020_SPRING, test)) {
        makeUpdateText(0, 22, 8, APP_RELEASE_VERSION_2020_SPRING, "https://telegra.ph/Telegram-X-04-23", functions, updates, true);
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
              client().send(new TdApi.AddLocalMessage(chatId, new TdApi.MessageSenderUser(TdConstants.TELEGRAM_ACCOUNT_ID) /*TODO: @tgx_android?*/, 0, true, content), localMessageHandler);
            }
          }
        };
        for (TdApi.Function function : functions) {
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

  private boolean isDebugInstance () {
    return debugInstance;
  }

  /*public void toggleDebug (boolean isDebug) {
    context.setIsDebug(account(), isDebug);
  }*/

  void setIsDebugInstance (boolean isDebug) {
    synchronized (clientLock) {
      if (this.debugInstance != isDebug) {
        this.debugInstance = isDebug;
        if (instancePaused)
          return;
        int referenceCount = this.referenceCount.get();
        if (referenceCount > 0)
          throw new IllegalStateException("referenceCount == " + referenceCount);
        client.sendClose();
      }
    }
  }

  public TdlibAccount account () {
    return context.account(accountId);
  }

  public void wakeUp () {
    synchronized (clientLock) {
      if (client == null || !instancePaused || Thread.currentThread() != client.client.getThread()) {
        clientHolderUnsafe();
        return;
      }
    }
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
        RuntimeException e = new RuntimeException("Long close detected. authState: " + currentAuthState + ", closeState: " + (client != null ? client.closeState : -1));
        Tracer.onOtherError(e);
        throw e;
      }
    } else {
      U.awaitLatch(latch);
    }
    return clientHolder();
  }

  public Client client () { // TODO migrate all tdlib.client().send(..) to tdlib.send(..)
    return clientHolder().client;
  }

  public void send (TdApi.Function function, Client.ResultHandler handler) {
    client().send(function, handler);
  }

  public void sendAll (TdApi.Function[] functions, Client.ResultHandler handler, @Nullable Runnable after) {
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
    for (TdApi.Function function : functions) {
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
    if (Thread.currentThread() == client.client.getThread())
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

  public void runOnUiThread (@NonNull Runnable runnable, long timeout) {
    incrementUiReferenceCount();
    ui().post(() -> {
      runnable.run();
      decrementUiReferenceCount();
    });
  }

  public void runOnTdlibThread (@NonNull Runnable runnable) {
    runOnTdlibThread(runnable, 0);
  }

  public void runOnTdlibThread (@NonNull Runnable runnable, double timeout) {
    incrementReferenceCount(REFERENCE_TYPE_JOB);
    clientHolder().runOnTdlibThread(() -> {
      runnable.run();
      decrementReferenceCount(REFERENCE_TYPE_JOB);
    }, timeout);
  }

  public void searchContacts (@Nullable String searchQuery, int limit, Client.ResultHandler handler) {
    client().send(new TdApi.SearchContacts(searchQuery, limit), handler);
  }

  public void loadMoreChats (@NonNull TdApi.ChatList chatList, int limit, Client.ResultHandler handler) {
    client().send(new TdApi.LoadChats(chatList, limit), handler);
  }

  public void readAllChats (@Nullable TdApi.ChatList chatList, @Nullable RunnableInt after) {
    AtomicInteger readChatsCount = new AtomicInteger(0);
    getAllChats(chatList, chat -> {
      boolean read = false;
      if (chat.unreadCount != 0 && chat.lastMessage != null) {
        readMessages(chat.id, 0, new long[] {chat.lastMessage.id});
        read = true;
      }
      if (chat.isMarkedAsUnread) {
        client().send(new TdApi.ToggleChatIsMarkedAsUnread(chat.id, false), okHandler);
        read = true;
      }
      if (chat.unreadMentionCount > 0) {
        client().send(new TdApi.ReadAllChatMentions(chat.id), okHandler);
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

    client().send(new TdApi.UploadFile(new TdApi.InputFileGenerated(null, id, 0), isSecret ? new TdApi.FileTypeSecret() : fileType, priority), object -> {
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
    if (inTdlibThread()) {
      return null;
    }
    TdApi.Object result = clientExecute(new TdApi.GetMessageLocally(chatId, messageId), 0);
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

  public boolean inTdlibThread () {
    return Thread.currentThread() == client().getThread();
  }

  @Nullable
  public TdApi.Object clientExecute (TdApi.Function function, long timeoutMs) {
    if (inTdlibThread())
      throw new IllegalStateException("Cannot call from TDLib thread: " + function);
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();
    awaitInitialization(() -> {
      incrementReferenceCount(REFERENCE_TYPE_REQUEST_EXECUTION);
      client().send(function, object -> {
        synchronized (response) {
          response.set(object);
          latch.countDown();
        }
        decrementReferenceCount(REFERENCE_TYPE_REQUEST_EXECUTION);
      });
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
    synchronized (response) {
      return response.get();
    }
  }

  public @Nullable TdApi.File getRemoteFile (String remoteId, TdApi.FileType fileType, long timeoutMs) {
    TdApi.Object result = clientExecute(new TdApi.GetRemoteFile(remoteId, fileType), timeoutMs);
    return result instanceof TdApi.File ? (TdApi.File) result : null;
  }

  public TdApi.TdlibParameters clientParameters () {
    return parameters;
  }

  public TdlibCache cache () {
    return cache;
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
    return okHandler;
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

  public Client.ResultHandler imageLoadHandler () {
    return imageLoadHandler;
  }

  public Comparator<TdApi.User> userComparator () {
    return userComparator;
  }

  public Comparator<UserProvider> userProviderComparator () {
    return userProviderComparator;
  }

  // Self User

  public int myUserId (boolean allowCached) {
    int myUserId = myUserId();
    if (myUserId != 0 || !allowCached)
      return myUserId;
    return account().getKnownUserId();
  }

  public int myUserId () {
    // TODO move myUserId management to TdlibContext
    return cache().myUserId();
  }

  public @Nullable TdApi.User myUser () {
    // TODO move myUser management to TdlibContext
    return cache().myUser();
  }

  public TdApi.MessageSender mySender () {
    int userId = myUserId();
    return new TdApi.MessageSenderUser(userId);
  }

  public @Nullable String myUserUsername () {
    TdApi.User user = myUser();
    return user != null && !StringUtils.isEmpty(user.username) ? user.username : null;
  }

  public @Nullable TdApi.UserFullInfo myUserFull () {
    int myUserId = myUserId();
    return myUserId != 0 ? cache().userFull(myUserId) : null;
  }

  // Chats

  public void loadChats (long[] chatIds, @Nullable Runnable after) {
    if (chatIds == null || chatIds.length == 0)
      return;
    if (after != null) {
      int[] counter = new int[]{chatIds.length};
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
        int supergroupId = ChatId.toSupergroupId(chatId);
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

  public void privateChat (int userId, RunnableData<TdApi.Chat> callback) {
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
    TdApi.Chat chat = chat(chatId);
    if (chat != null) {
      callback.runWithData(chat);
    } else {
      client().send(new TdApi.GetChat(chatId), result -> callback.runWithData(result.getConstructor() == TdApi.Chat.CONSTRUCTOR ? chat(chatId) : null));
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

  public @Nullable TdApi.ChatMemberStatus chatStatus (long chatId) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return null;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        int supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
        return supergroup != null ? supergroup.status : null;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        int basicGroupId = ChatId.toBasicGroupId(chatId);
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
        int supergroupId = ChatId.toSupergroupId(chatId);
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

  public boolean isSelfUserId (int userId) {
    return userId != 0 && userId == myUserId(true);
  }

  public long selfChatId () {
    return ChatId.fromUserId(myUserId());
  }

  public boolean canClearHistory (long chatId) {
    return chatId != 0 && canClearHistory(chat(chatId));
  }

  public boolean canClearHistory (TdApi.Chat chat) {
    if (chat == null || chat.lastMessage == null) {
      return false;
    }
    switch (ChatId.getType(chat.id)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return true;
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        TdApi.Supergroup supergroup = chatToSupergroup(chat.id);
        return supergroup != null && !supergroup.isChannel && StringUtils.isEmpty(supergroup.username);
      }
    }
    return false;
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

  public boolean hasWritePermission (long chatId) {
    return chatId != 0 && hasWritePermission(chat(chatId));
  }

  public boolean hasWritePermission (TdApi.Chat chat) {
    return getRestrictionStatus(chat, R.id.right_sendMessages) == null;
  }

  public @Nullable TdApi.SecretChat chatToSecretChat (long chatId) {
    int secretChatId = ChatId.toSecretChatId(chatId);
    return secretChatId != 0 ? cache().secretChat(secretChatId) : null;
  }

  public @Nullable TdApi.BasicGroup chatToBasicGroup (long chatId) {
    int basicGroupId = ChatId.toBasicGroupId(chatId);
    return basicGroupId != 0 ? cache().basicGroup(basicGroupId) : null;
  }

  public @Nullable TdApi.Supergroup chatToSupergroup (long chatId) {
    int supergroupId = ChatId.toSupergroupId(chatId);
    return supergroupId != 0 ? cache().supergroup(supergroupId) : null;
  }

  public @Nullable ImageFile chatAvatar (long chatId) {
    if (chatId == 0)
      return null;
    TdApi.Chat chat = chat(chatId);
    TdApi.ChatPhotoInfo photo = chat != null ? chat.photo : null;
    if (photo == null)
      return null;
    ImageFile avatarFile = new ImageFile(this, photo.small);
    avatarFile.setSize(ChatView.getDefaultAvatarCacheSize());
    return avatarFile;
  }

  public int chatAvatarColorId (long chatId) {
    if (chatId == 0) {
      return TD.getAvatarColorId(-1, myUserId());
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return TD.getAvatarColorId(-ChatId.toBasicGroupId(chatId), myUserId());
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        return TD.getAvatarColorId(-ChatId.toSupergroupId(chatId), myUserId());
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
        return cache().userAvatarColorId(ChatId.toUserId(chatId));
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int secretChatId = ChatId.toSecretChatId(chatId);
        TdApi.SecretChat secretChat = secretChatId != 0 ? cache().secretChat(secretChatId) : null;
        return cache().userAvatarColorId(secretChat != null ? secretChat.userId : 0);
      }
    }
    throw new RuntimeException();
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
    } else {
      return new AvatarPlaceholder.Metadata(chatAvatarColorId(chatId));
    }
  }

  public AvatarPlaceholder.Metadata chatPlaceholderMetadata (@Nullable TdApi.Chat chat, boolean allowSavedMessages) {
    if (chat == null) {
      return null;
    }
    Letters avatarLetters = null;
    int avatarColorId;
    int desiredDrawableRes = 0;
    int extraDrawableRes = 0;
    if (isUserChat(chat)) {
      int userId = chatUserId(chat);
      return cache().userPlaceholderMetadata(userId, cache().user(userId), allowSavedMessages);
    } else {
      avatarColorId = chatAvatarColorId(chat.id);
      avatarLetters = chatLetters(chat);
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
          extraDrawableRes = canChangeInfo(chat) ? R.drawable.ic_add_a_photo_black_56 : R.drawable.baseline_group_56;
          break;
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
          extraDrawableRes = canChangeInfo(chat) ? R.drawable.ic_add_a_photo_black_56 : isChannelChat(chat) ? R.drawable.baseline_bullhorn_56 : R.drawable.baseline_group_56;
          break;
      }
    }
    return new AvatarPlaceholder.Metadata(avatarColorId, avatarLetters != null ? avatarLetters.text : null, desiredDrawableRes, extraDrawableRes);
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

  public int chatAvatarColorId (TdApi.Chat chat) {
    if (chat != null) {
      switch (chat.type.getConstructor()) {
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          return TD.getAvatarColorId(-((TdApi.ChatTypeSupergroup) chat.type).supergroupId, myUserId());
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          return TD.getAvatarColorId(-((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId, myUserId());
        }
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          int userId = chatUserId(chat);
          if (isSelfUserId(userId)) {
            return R.id.theme_color_avatarSavedMessages;
          }
          return cache().userAvatarColorId(userId);
        }
      }
    }
    return TD.getAvatarColorId(-1, 0);
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
        case TdApi.MessageForwardOriginUser.CONSTRUCTOR: {
          int userId = ((TdApi.MessageForwardOriginUser) message.forwardInfo.origin).senderUserId;
          return shorten ? cache().userFirstName(userId) : cache().userName(userId);
        }
        case TdApi.MessageForwardOriginChannel.CONSTRUCTOR: {
          TdApi.MessageForwardOriginChannel info = (TdApi.MessageForwardOriginChannel) message.forwardInfo.origin;
          if (allowSignature && !StringUtils.isEmpty(info.authorSignature))
            return info.authorSignature;
          TdApi.Chat chat = chat(info.chatId);
          if (chat != null)
            return chat.title;
          break;
        }
        case TdApi.MessageForwardOriginChat.CONSTRUCTOR: {
          TdApi.MessageForwardOriginChat info = (TdApi.MessageForwardOriginChat) message.forwardInfo.origin;
          if (allowSignature && !StringUtils.isEmpty(info.authorSignature))
            return info.authorSignature;
          TdApi.Chat chat = chat(info.senderChatId);
          if (chat != null)
            return chat.title;
          break;
        }
        case TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR:
        case TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR:
          break;
      }
    }
    if (message.sender == null)
      return null;
    switch (message.sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        return chatTitle(((TdApi.MessageSenderChat) message.sender).chatId);
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        int senderUserId = ((TdApi.MessageSenderUser) message.sender).userId;
        return shorten ? cache().userFirstName(senderUserId) : cache().userName(senderUserId);
      }
    }
    throw new IllegalArgumentException(message.sender.toString());
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
    final int userId = chatUserId(chatId);
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
    final int userId = chatUserId(chat.id);
    return userId != 0 ? cache().userDisplayName(userId, allowSavedMessages, shorten) : chat.title;
  }

  public int chatUserId (TdApi.Chat chat) {
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
        return ((TdApi.ChatTypePrivate) chat.type).userId;
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return ((TdApi.ChatTypeSecret) chat.type).userId;
    }
    return 0;
  }

  public @Nullable TdApi.User chatUser (TdApi.Chat chat) {
    final int userId = chatUserId(chat);
    return userId != 0 ? cache().user(userId) : null;
  }

  public int chatUserId (long chatId) {
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

  public TdApi.MessageSender sender (long chatId) {
    int userId = chatUserId(chatId);
    return userId != 0 ? new TdApi.MessageSenderUser(userId) : new TdApi.MessageSenderChat(chatId);
  }

  public boolean isSelfSender (TdApi.MessageSender sender) {
    return sender != null && isSelfUserId(Td.getSenderUserId(sender));
  }

  public boolean isSelfSender (TdApi.Message message) {
    return message != null && (message.isOutgoing || isSelfChat(Td.getSenderId(message.sender)));
  }

  public @Nullable TdApi.User chatUser (long chatId) {
    int userId = chatUserId(chatId);
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

  public boolean chatBlocked (TdApi.Chat chat) {
    return chat != null && chatBlocked(chat.id);
  }

  public boolean chatBlocked (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null && chat.isBlocked;
  }

  public boolean userBlocked (int userId) {
    return chatBlocked(ChatId.fromUserId(userId));
  }

  public String chatUsername (TdApi.Chat chat) {
    if (chat == null) {
      return null;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return null;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int userId = chatUserId(chat);
        return cache().userUsername(userId);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        TdApi.Supergroup supergroup = cache().supergroup(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
        return supergroup != null && !StringUtils.isEmpty(supergroup.username) ? supergroup.username : null;
      }
    }
    throw new RuntimeException();
  }

  public String chatUsername (long chatId) {
    if (chatId == 0) {
      return null;
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return null;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        int userId = ChatId.toUserId(chatId);
        return cache().userUsername(userId);
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        int secretChatId = ChatId.toSecretChatId(chatId);
        TdApi.SecretChat secretChat = cache().secretChat(secretChatId);
        if (secretChat != null) {
          return cache().userUsername(secretChat.userId);
        }
        return null;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        int supergroupId = ChatId.toSupergroupId(chatId);
        TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
        return supergroup != null && !StringUtils.isEmpty(supergroup.username) ? supergroup.username : null;
      }
    }
    throw new RuntimeException();
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
    int basicGroupId = ChatId.toBasicGroupId(chatId);
    return basicGroupId != 0 && cache().basicGroupActive(basicGroupId);
  }

  public void withChannelBotUserId (RunnableInt runnable) {
    client().send(new TdApi.SearchPublicChat("Channel_Bot"), result -> ui().post(() -> {
      int userId = result.getConstructor() == TdApi.Chat.CONSTRUCTOR ? chatUserId((TdApi.Chat) result) : 0;
      runnable.runWithInt(userId != 0 ? userId : TdConstants.TELEGRAM_CHANNEL_BOT_ACCOUNT_ID);
    }));
  }

  public boolean canCopyMessageLinks (long chatId) {
    if (chatId == 0) {
      return false;
    }
    int supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0) {
      return false;
    }
    TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
    return supergroup != null && !StringUtils.isEmpty(supergroup.username);
  }

  public boolean chatPublic (long chatId) {
    if (chatId == 0) {
      return false;
    }
    int supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0) {
      return false;
    }
    TdApi.Supergroup supergroup = cache().supergroup(supergroupId);
    return supergroup != null && (!StringUtils.isEmpty(supergroup.username) || supergroup.hasLocation);
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

  public TdApi.ChatFilterInfo chatFilterInfo (int chatFilterId) {
    synchronized (dataLock) {
      if (chatFilters != null) {
        for (TdApi.ChatFilterInfo filter : chatFilters) {
          if (filter.id == chatFilterId)
            return filter;
        }
      }
    }
    return null;
  }

  public boolean canArchiveChat (TdApi.ChatList chatList, TdApi.Chat chat) {
    if (chat == null)
      return false;
    if (chatList != null) {
      switch (chatList.getConstructor()) {
        case TdApi.ChatListMain.CONSTRUCTOR:
        case TdApi.ChatListArchive.CONSTRUCTOR:
          break;
        case TdApi.ChatListFilter.CONSTRUCTOR:
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
          case TdApi.ChatListFilter.CONSTRUCTOR:
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
    return chat != null ? chat.messageTtlSetting : 0;
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

  public @Nullable String messageUsername (TdApi.Message msg) {
    if (msg != null) {
      final int userId = msg.viaBotUserId != 0 ? msg.viaBotUserId : Td.getSenderUserId(msg);
      if (userId != 0) {
        TdApi.User user = cache().user(userId);
        return user != null && !StringUtils.isEmpty(user.username) ? user.username :null;
      }
    }
    return null;
  }

  public boolean isSameSender (TdApi.Message a, TdApi.Message b) {
    String psa1 = a.forwardInfo != null ? a.forwardInfo.publicServiceAnnouncementType : null;
    String psa2 = b.forwardInfo != null ? b.forwardInfo.publicServiceAnnouncementType : null;
    return Td.equalsTo(a.sender, b.sender) && StringUtils.equalsOrBothEmpty(psa1, psa2);
  }

  public int senderUserId (TdApi.Message msg) {
    if (msg == null) {
      return 0;
    }
    if (isSelfChat(msg.chatId)) {
      if (msg.forwardInfo != null && msg.forwardInfo.origin.getConstructor() == TdApi.MessageForwardOriginUser.CONSTRUCTOR)
        return ((TdApi.MessageForwardOriginUser) msg.forwardInfo.origin).senderUserId;
    }
    return Td.getSenderUserId(msg);
  }

  public String senderName (TdApi.Message msg, boolean allowForward, boolean shorten) {
    long authorId = Td.getMessageAuthorId(msg, allowForward);
    if (ChatId.isUserChat(authorId)) {
      int userId = chatUserId(authorId);
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
        int userId = ((TdApi.MessageSenderUser) sender).userId;
        return shorten ? cache().userFirstName(userId) : cache().userName(userId);
      }
    }
    throw new RuntimeException(sender.toString());
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

  public void markChatAsRead (long chatId, long messageThreadId, @Nullable Runnable after) {
    TdApi.Chat chat = chat(chatId);
    if (chat != null) {
      if (messageThreadId == 0 && chat.isMarkedAsUnread) {
        client().send(new TdApi.ToggleChatIsMarkedAsUnread(chat.id, false), okHandler(after));
      }
      if (!hasPasscode(chat) && chat.lastMessage != null && (messageThreadId != 0 || chat.unreadCount > 0)) {
        client().send(new TdApi.ViewMessages(chatId, messageThreadId, new long[] {chat.lastMessage.id}, true), okHandler(after));
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
              int supergroupId = ChatId.toSupergroupId(message.chatId);
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
    int supergroupId = ChatId.toSupergroupId(chatId);
    return supergroupId != 0 && channels.contains(supergroupId);
  }

  public boolean isChannel (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && supergroup.isChannel;
  }

  public boolean isSupergroup (long chatId) {
    TdApi.Supergroup supergroup = chatToSupergroup(chatId);
    return supergroup != null && !supergroup.isChannel;
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
    return (repliesBotChatId != 0 && repliesBotChatId == chatId) || (chatId == ChatId.fromUserId(TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID));
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
    int userId = chatUserId(chatId);
    return cache().userBot(userId);
  }

  public boolean isBotChat (TdApi.Chat chat) {
    int userId = chatUserId(chat);
    return cache().userBot(userId);
  }

  public boolean isSupportChat (TdApi.Chat chat) {
    TdApi.User user = chatUser(chat);
    return user != null && user.isSupport;
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

  public boolean chatHasScheduled (long chatId) {
    TdApi.Chat chat = chat(chatId);
    return chat != null && chat.hasScheduledMessages;
  }

  public int calleeUserId (TdApi.Message message) {
    return message.isOutgoing ? chatUserId(message.chatId) : senderUserId(message);
  }

  @Nullable
  public String getDiceEmoji (TdApi.FormattedText text) {
    if (!Td.isEmpty(text) && (text.entities == null || text.entities.length == 0)) {
      String trimmed = text.text.trim();
      if (isDiceEmoji(trimmed)) {
        return trimmed;
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
        for (String emoji : diceEmoji) {
          if (text.equals(emoji))
            return true;
        }
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
        client().send(new TdApi.CloseChat(chatId), okHandler);
      }
    }
  }

  public void onScreenshotTaken (int timeSeconds) {
    synchronized (chatOpenMutex) {
      final int size = openedChatsTimes.size();
      for (int i = 0; i < size; i++) {
        final int openTime = openedChatsTimes.valueAt(i);
        if (timeSeconds >= openTime) {
          final long chatId = openedChatsTimes.keyAt(i);
          TdApi.Chat chat = chat(chatId);
          if (hasWritePermission(chat) && (ChatId.isSecret(chatId) || ui().shouldSendScreenshotHint(chat))) {
            sendScreenshotMessage(chatId);
          }
        }
      }
    }
  }

  public boolean hasOpenChats () {
    return openedChats != null && openedChats.size() > 0;
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
    return getColor(ChatId.isSecret(chatId) ? R.id.theme_color_notificationSecure : R.id.theme_color_notification);
  }

  public int accountColor () {
    return getColor(R.id.theme_color_notification);
  }

  public int accountPlayerColor () {
    return getColor(R.id.theme_color_notificationPlayer);
  }

  public int getColor (@ThemeColorId int colorId) {
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

  /*public String userDisplayName () {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
      return "";
    }
    return null;
  }*/

  // Actions

  public void sendScreenshotMessage (long chatId) {
    client().send(new TdApi.SendChatScreenshotTakenNotification(chatId), messageHandler());
  }

  public void saveGif (int fileId) {
    if (fileId == 0) {
      return;
    }
    client().send(new TdApi.AddSavedAnimation(new TdApi.InputFileId(fileId)), object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          UI.showToast(R.string.GifSaved, Toast.LENGTH_SHORT);
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(object);
          break;
      }
    });
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, boolean disableNotification, boolean fromBackground, TdApi.Animation animation) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageAnimation(new TdApi.InputFileId(animation.animation.id), null, null, animation.duration, animation.width, animation.height, null);
    sendMessage(chatId, messageThreadId, replyToMessageId, disableNotification, fromBackground, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, boolean disableNotification, boolean fromBackground, TdApi.Audio audio) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageAudio(new TdApi.InputFileId(audio.audio.id), null, audio.duration, audio.title, audio.performer, null);
    sendMessage(chatId, messageThreadId, replyToMessageId, disableNotification, fromBackground, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, boolean disableNotification, boolean fromBackground, TdApi.Sticker sticker, @Nullable String emoji) {
    TdApi.InputMessageContent inputMessageContent = new TdApi.InputMessageSticker(new TdApi.InputFileId(sticker.sticker.id), null, 0, 0, emoji);
    sendMessage(chatId, messageThreadId, replyToMessageId, disableNotification, fromBackground, inputMessageContent);
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, boolean disableNotification, boolean fromBackground, TdApi.InputMessageContent inputMessageContent) {
    sendMessage(chatId, messageThreadId, replyToMessageId, disableNotification, fromBackground, inputMessageContent, null);
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, boolean disableNotification, boolean fromBackground, TdApi.InputMessageContent inputMessageContent, @Nullable RunnableData<TdApi.Message> after) {
    sendMessage(chatId, messageThreadId, replyToMessageId, new TdApi.MessageSendOptions(disableNotification, fromBackground, null), inputMessageContent, after);
  }

  public void sendMessage (long chatId, long messageThreadId, long replyToMessageId, TdApi.MessageSendOptions options, TdApi.InputMessageContent inputMessageContent, @Nullable RunnableData<TdApi.Message> after) {
    client().send(new TdApi.SendMessage(chatId, messageThreadId, replyToMessageId, options, null, inputMessageContent), after != null ? result -> {
      messageHandler.onResult(result);
      after.runWithData(result instanceof TdApi.Message ? (TdApi.Message) result : null);
    } : messageHandler());
  }

  public void resendMessages (long chatId, long[] messageIds) {
    client().send(new TdApi.ResendMessages(chatId, messageIds), messageHandler());
  }

  private final HashMap<String, TdApi.MessageText> pendingMessageTexts = new HashMap<>();
  private final HashMap<String, TdApi.FormattedText> pendingMessageCaptions = new HashMap<>();

  public void editMessageText (long chatId, long messageId, TdApi.InputMessageText content, @Nullable TdApi.WebPage webPage) {
    if (content.disableWebPagePreview) {
      webPage = null;
    }
    TD.parseEntities(content.text);
    TdApi.MessageText messageText = new TdApi.MessageText(content.text, webPage);
    performEdit(chatId, messageId, messageText, new TdApi.EditMessageText(chatId, messageId, null, content), pendingMessageTexts);
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
    TdApi.MessageText messageText = getPendingMessageText(chatId, messageId);
    if (messageText != null)
      return messageText.text;
    return getPendingMessageCaption(chatId, messageId);
  }

  public TdApi.MessageText getPendingMessageText (long chatId, long messageId) {
    synchronized (pendingMessageTexts) {
      return pendingMessageTexts.get(chatId + "_" + messageId);
    }
  }

  public TdApi.FormattedText getPendingMessageCaption (long chatId, long messageId) {
    synchronized (pendingMessageCaptions) {
      return pendingMessageCaptions.get(chatId + "_" + messageId);
    }
  }

  private <T extends TdApi.Object> void performEdit (long chatId, long messageId, T pendingData, TdApi.Function function, Map<String, T> map) {
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
  // public static final int CEO_USER_ID = 36265675;

  public static final long ADMIN_CHAT_ID = ChatId.fromSupergroupId(1112283549); // TGX Alpha and Admins
  public static final long TESTER_CHAT_ID = ChatId.fromSupergroupId(1336679475); // Telegram X Android: t.me/tgandroidtests
  public static final long READER_CHAT_ID = ChatId.fromSupergroupId(1136101327); // Telegram X: t.me/tgx_android
  public static final long CLOUD_RESOURCES_CHAT_ID = ChatId.fromSupergroupId(1247387696); // Telegram X: Resources
  public static final long TRENDING_STICKERS_CHAT_ID = ChatId.fromSupergroupId(1140222267); // Trending Stickers: t.me/TrendingStickers

  public boolean isRedTeam (long chatId) {
    if (isDebugInstance()) {
      return false;
    }
    int supergroupId = ChatId.toSupergroupId(chatId);
    if (supergroupId == 0)
      return false;
    switch (supergroupId) {
      case 1084287520:
      case 1266791237:
      case 1492016544:
      case 1227585106:
      case 1116030833:
        return true;
    }
    return false;
  }

  public void getTesterLevel (@NonNull RunnableInt callback) {
    if (context.inRecoveryMode() || isDebugInstance()) {
      callback.runWithInt(TESTER_LEVEL_TESTER);
      return;
    }
    int myUserId = myUserId();
    switch (myUserId) {
      /*case CEO_USER_ID: {
        callback.run(TESTER_LEVEL_TESTER);
        break;
      }*/
      case TGX_CREATOR_USER_ID:
        callback.runWithInt(TESTER_LEVEL_CREATOR);
        break;
      case TDLIB_CREATOR_USER_ID:
        callback.runWithInt(TESTER_LEVEL_DEVELOPER);
        break;
      default: {
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
        break;
      }
    }
  }

  public void forwardMessage (long chatId, long fromChatId, long messageId, boolean disableNotification, boolean fromBackground) {
    client().send(new TdApi.ForwardMessages(chatId, fromChatId, new long[] {messageId}, new TdApi.MessageSendOptions(disableNotification, fromBackground, null), false, false, false), messageHandler());
  }

  public void sendInlineQueryResult (long chatId, long messageThreadId, long replyToMessageId, TdApi.MessageSendOptions options, long queryId, String resultId) {
    client().send(new TdApi.SendInlineQueryResultMessage(chatId, messageThreadId, replyToMessageId, options, queryId, resultId, false), messageHandler());
  }

  public void sendBotStartMessage (int botUserId, long chatId, String parameter) {
    client().send(new TdApi.SendBotStartMessage(botUserId, chatId, parameter), messageHandler());
  }

  public void setChatMessageTtlSetting (long chatId, int ttl) {
    client().send(new TdApi.SetChatMessageTtlSetting(chatId, ttl), okHandler());
  }

  public void getPrimaryChatInviteLink (long chatId, Client.ResultHandler handler) {
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
          case TdApi.Error.CONSTRUCTOR:
            handler.onResult(result);
            return;
          default:
            Log.unexpectedTdlibResponse(result, TdApi.ReplacePrimaryChatInviteLink.class, TdApi.ChatInviteLink.class);
            return;
        }
        if (inviteLink != null) {
          handler.onResult(inviteLink);
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
        handler.onResult(new TdApi.Error(-1, "Invalid chat type"));
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
      boolean isPublic = !StringUtils.isEmpty(supergroup.username) || supergroup.hasLinkedChat || supergroup.hasLocation;
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

  public void blockSender (TdApi.MessageSender sender, boolean block, Client.ResultHandler handler) {
    client().send(new TdApi.ToggleMessageSenderIsBlocked(sender, block), handler);
  }

  public void setScopeNotificationSettings (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    client().send(new TdApi.SetScopeNotificationSettings(scope, settings), okHandler);
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
    client().send(new TdApi.SetChatNotificationSettings(chatId, settings), okHandler);
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
        if (status == null || !TD.isMember(status, false)) {
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
    final boolean needBanAtFirst = !ChatId.isBasicGroup(chatId) && TD.isMember(currentStatus) && !TD.isMember(newStatus) && newStatus.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR;
    final boolean needForward = ChatId.isBasicGroup(chatId) && forwardLimit > 0 && !TD.isMember(currentStatus, false) && TD.isMember(newStatus, false) && sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR;
    final AtomicBoolean oneShot = needBanAtFirst || (needForward && TD.isAdmin(newStatus)) ? new AtomicBoolean(false) : null;

    TdApi.Function function;
    if (needBanAtFirst) {
      function = new TdApi.SetChatMemberStatus(chatId, sender, new TdApi.ChatMemberStatusBanned());
    } else if (needForward) {
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

  public void transferOwnership (final long chatId, final int toUserId, final String password, ChatMemberStatusChangeCallback callback) {
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
    client().send(new TdApi.DeleteMessages(chatId, messageIds, revoke), okHandler);
  }

  public void deleteMessagesIfOk (final long chatId, final long[] messageIds, boolean revoke) {
    client().send(new TdApi.DeleteMessages(chatId, messageIds, revoke), okHandler);
  }

  public void readMessages (long chatId, long messageThreadId, long[] messageIds) {
    if (Log.isEnabled(Log.TAG_FCM)) {
      Log.i(Log.TAG_FCM, "Reading messages chatId:%d messageIds:%s", Log.generateSingleLineException(2), chatId, Arrays.toString(messageIds));
    }
    client().send(new TdApi.ViewMessages(chatId, messageThreadId, messageIds, true), okHandler);
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
        client().send(new TdApi.SetOption("language_pack_id", new TdApi.OptionValueString(languagePackId)), okHandler);
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
    client().send(new TdApi.SynchronizeLanguagePack(languagePackId), result -> {
      boolean success;
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          Log.v("%s language is successfully synchronized", languagePackId);
          success = true;
          break;
        case TdApi.Error.CONSTRUCTOR:
          Log.e("Unable to synchronize languagePackId %s: %s", languagePackId, TD.toErrorString(result));
          success = languagePackId.equals(Lang.getBuiltinLanguagePackId());
          if (!success) {
            success = Config.NEED_LANGUAGE_WORKAROUND;
            UI.showError(result);
            /*if (success = Config.NEED_LANGUAGE_WORKAROUND) {
              UI.showToast("Warning: language not synced. It's temporary issue of current beta version. " + TD.makeErrorString(result), Toast.LENGTH_LONG);
            } else {
              UI.showError(result);
            }*/
          }
          break;
        default:
          Log.unexpectedTdlibResponse(result, TdApi.SynchronizeLanguagePack.class, TdApi.Ok.class, TdApi.Error.class);
          return;
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

  private void updateLanguageParameters (Client client) {
    this.languagePackId = Settings.instance().getLanguagePackInfo().id;

    client.send(new TdApi.SetOption("language_pack_database_path", new TdApi.OptionValueString(context.languageDatabasePath())), okHandler);
    client.send(new TdApi.SetOption("localization_target", new TdApi.OptionValueString(BuildConfig.LANGUAGE_PACK)), okHandler);
    client.send(new TdApi.SetOption("language_pack_id", new TdApi.OptionValueString(languagePackId)), okHandler);
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

  private static final String DEVICE_TOKEN_KEY = "device_token";
  private String lastReportedConnectionParams;

  public void checkConnectionParams () {
    checkConnectionParams(client(), false);
  }

  private String getRegisteredDeviceToken () {
    String deviceToken = context.getToken();
    return StringUtils.isEmpty(deviceToken) ? Settings.instance().getDeviceToken() : deviceToken;
  }

  private void checkConnectionParams (Client client, boolean force) {
    int state = context().getTokenState();
    final String deviceToken = getRegisteredDeviceToken();
    if (!StringUtils.isEmpty(deviceToken) && (state == TdlibManager.TOKEN_STATE_NONE || state == TdlibManager.TOKEN_STATE_INITIALIZING)) {
      state = TdlibManager.TOKEN_STATE_OK;
    }
    if (state == TdlibManager.TOKEN_STATE_NONE)
      return;
    String error = context().getTokenError();
    List<TdApi.JsonObjectMember> members = new ArrayList<>();
    switch (state) {
      case TdlibManager.TOKEN_STATE_ERROR: {
        members.add(new TdApi.JsonObjectMember(DEVICE_TOKEN_KEY, new TdApi.JsonValueString("FIREBASE_ERROR")));
        if (!StringUtils.isEmpty(error)) {
          members.add(new TdApi.JsonObjectMember("firebase_error", new TdApi.JsonValueString(error)));
        }
        break;
      }
      case TdlibManager.TOKEN_STATE_INITIALIZING: {
        members.add(new TdApi.JsonObjectMember(DEVICE_TOKEN_KEY, new TdApi.JsonValueString("FIREBASE_INITIALIZING")));
        break;
      }
      case TdlibManager.TOKEN_STATE_OK: {
        members.add(new TdApi.JsonObjectMember(DEVICE_TOKEN_KEY, new TdApi.JsonValueString(deviceToken)));
        break;
      }
      default: {
        members.add(new TdApi.JsonObjectMember(DEVICE_TOKEN_KEY, new TdApi.JsonValueString("UNKNOWN")));
        break;
      }
    }
    String connectionParams = JSON.stringify(members);
    if (connectionParams != null && (force || !StringUtils.equalsOrBothEmpty(lastReportedConnectionParams, connectionParams))) {
      this.lastReportedConnectionParams = connectionParams;
      client.send(new TdApi.SetOption("connection_parameters", new TdApi.OptionValueString(connectionParams)), okHandler);
    }
  }

  private void updateParameters (Client client) {
    updateLanguageParameters(client);
    client.send(new TdApi.SetOption("use_quick_ack", new TdApi.OptionValueBoolean(true)), okHandler);
    client.send(new TdApi.SetOption("use_pfs", new TdApi.OptionValueBoolean(true)), okHandler);
    client.send(new TdApi.SetOption("notification_group_count_max", new TdApi.OptionValueInteger(25)), okHandler);
    client.send(new TdApi.SetOption("notification_group_size_max", new TdApi.OptionValueInteger(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? 7 : 10)), okHandler);
    client.send(new TdApi.SetOption("is_emulator", new TdApi.OptionValueBoolean(isEmulator = Settings.instance().isEmulator())), okHandler);
    client.send(new TdApi.SetOption("storage_max_files_size", new TdApi.OptionValueInteger(Integer.MAX_VALUE)), okHandler);
    client.send(new TdApi.SetOption("ignore_default_disable_notification", new TdApi.OptionValueBoolean(true)), okHandler);
    client.send(new TdApi.SetOption("ignore_platform_restrictions", new TdApi.OptionValueBoolean(U.isAppSideLoaded())), okHandler);
    checkConnectionParams(client, true);

    if (needDropNotificationIdentifiers) {
      client.send(new TdApi.SetOption("drop_notification_ids", new TdApi.OptionValueBoolean(true)), result -> {
        notifications().onDropNotificationData(true);
      });
      needDropNotificationIdentifiers = false;
    }

    parameters.useTestDc = isDebugInstance();
    parameters.databaseDirectory = TdlibManager.getTdlibDirectory(accountId, false);
    parameters.filesDirectory = TdlibManager.getTdlibDirectory(accountId, true);
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
        case "youtube_pip":
          this.youtubePipDisabled = member.value instanceof TdApi.JsonValueString && "disabled".equals(((TdApi.JsonValueString) member.value).value);
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

  public long toTdlibTimeMillis (long millis) {
    return millis + (currentTimeMillis() - System.currentTimeMillis());
  }

  public long toSystemTimeMillis (long tdlibTime, TimeUnit unit) {
    return unit.toMillis(tdlibTime) + (System.currentTimeMillis() - currentTimeMillis());
  }

  public void setProxy (int proxyId, @Nullable String server, int port, @Nullable TdApi.ProxyType type) {
    // setProxyId(Settings.PROXY_ID_UNKNOWN);
    final TdApi.Function function;
    if (server != null)
      function = new TdApi.AddProxy(server, port, true, type);
    else
      function = new TdApi.DisableProxy();
    client().send(function, (result) -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          break;
        case TdApi.Proxy.CONSTRUCTOR:
          // removeProxies(((TdApi.Proxy) result).id);
          break;
        default:
          return;
      }
      // setProxyId(proxyId);
    });
  }

  public void cleanupProxies () {
    client().send(new TdApi.GetProxies(), result -> {
      switch (result.getConstructor()) {
        case TdApi.Proxies.CONSTRUCTOR: {
          TdApi.Proxies proxies = (TdApi.Proxies) result;
          for (TdApi.Proxy proxy : proxies.proxies) {
            if (!proxy.isEnabled) {
              client().send(new TdApi.RemoveProxy(proxy.id), okHandler);
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
              client().send(new TdApi.RemoveProxy(proxy.id), okHandler);
            }
          }
          break;
        }
      }
    });
  }

  public void getProxyLink (@NonNull Settings.Proxy proxy, RunnableData<String> callback) {
    client().send(new TdApi.AddProxy(proxy.server, proxy.port, false, proxy.type), object -> {
      switch (object.getConstructor()) {
        case TdApi.Proxy.CONSTRUCTOR: {
          int tdlibProxyId = ((TdApi.Proxy) object).id;
          client().send(new TdApi.GetProxyLink(tdlibProxyId), httpUrl -> {
            String url;
            switch (httpUrl.getConstructor()) {
              case TdApi.HttpUrl.CONSTRUCTOR:
                url = ((TdApi.HttpUrl) httpUrl).url;
                break;
              case TdApi.Error.CONSTRUCTOR:
                Log.e("Proxy link unavailable: %s", TD.toErrorString(httpUrl));
                url = null;
                break;
              default:
                Log.unexpectedTdlibResponse(httpUrl, TdApi.GetProxyLink.class, TdApi.HttpUrl.class, TdApi.Error.class);
                return;
            }
            ui().post(() -> callback.runWithData(url));
          });
          break;
        }
      }
    });
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
      client.send(new TdApi.SetNetworkType(networkType), okHandler), null);
    listeners().updateConnectionType(networkType);
  }

  public boolean isWaitingForNetwork () {
    return (networkType != null && networkType.getConstructor() == TdApi.NetworkTypeNone.CONSTRUCTOR) || context.watchDog().isWaitingForNetwork();
  }

  public void resendNetworkTypeIfNeeded (TdApi.NetworkType networkType) {
    if (connectionState != STATE_CONNECTED) {
      setNetworkType(networkType);
    }
  }

  // Options

  public boolean isStickerFavorite (int stickerId) {
    synchronized (dataLock) {
      return favoriteStickerIds != null && ArrayUtils.indexOf(favoriteStickerIds, stickerId) != -1;
    }
  }

  public boolean canFavoriteStickers () {
    synchronized (dataLock) {
      return installedStickerSetCount >= 5 || (favoriteStickerIds != null && favoriteStickerIds.length > 0);
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
        performOptional(client -> client.send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(isOnline)), okHandler), null);
      }
      // cache().setPauseStatusRefreshers(!isOnline);
    }
  }

  private boolean isEmulator;

  public void setIsEmulator (boolean isEmulator) {
    if (this.isEmulator != isEmulator) {
      this.isEmulator = isEmulator;
      performOptional(client -> client.send(new TdApi.SetOption("is_emulator", new TdApi.OptionValueBoolean(isEmulator)), okHandler), null);
    }
  }

  public boolean isEmulator () {
    return isEmulator;
  }

  public void sync (long pushId, @Nullable Runnable after, boolean needNotifications, boolean needNetworkRequest) {
    TDLib.Tag.notifications(pushId, accountId, "Performing sync needNotification: %b, needNetworkRequest: %b, hasAfter: %b. Awaiting connection. Connection state: %d, status: %d", needNotifications, needNetworkRequest, after != null, connectionState, status);
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
          TDLib.Tag.notifications(pushId, accountId, "Ensuring updateActiveNotifications was sent.");
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
      client().send(new TdApi.SetOption("disable_contact_registered_notifications", new TdApi.OptionValueBoolean(disable)), okHandler);
      listeners().updateContactRegisteredNotificationsDisabled(disable);
    }
  }

  private void setDisableContactRegisteredNotificationsImpl (boolean disable) {
    if (this.disableContactRegisteredNotifications != disable) {
      this.disableContactRegisteredNotifications = disable;
      listeners().updateContactRegisteredNotificationsDisabled(disable);
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
    client().send(new TdApi.SetOption("is_location_visible", new TdApi.OptionValueBoolean(isLocationVisible)), okHandler);
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
      client().send(new TdApi.SetOption("ignore_sensitive_content_restrictions", new TdApi.OptionValueBoolean(ignoreSensitiveContentRestrictions)), okHandler);
    }
  }

  public boolean autoArchiveEnabled () {
    return archiveAndMuteNewChatsFromUnknownUsers;
  }

  public void setAutoArchiveEnabled (boolean enabled) {
    if (this.archiveAndMuteNewChatsFromUnknownUsers != enabled) {
      this.archiveAndMuteNewChatsFromUnknownUsers = enabled;
      client().send(new TdApi.SetOption("archive_and_mute_new_chats_from_unknown_users", new TdApi.OptionValueBoolean(enabled)), okHandler);
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

  public double emojiesAnimatedZoom () {
    return emojiesAnimatedZoom;
  }

  public boolean youtubePipEnabled () {
    return !youtubePipDisabled || U.isAppSideLoaded();
  }

  public RtcServer[] rtcServers () {
    return rtcServers;
  }

  public boolean autoArchiveAvailable () {
    return canArchiveAndMuteNewChatsFromUnknownUsers;
  }

  public String tMeUrl () {
    return StringUtils.isEmpty(tMeUrl) ? "https://" + TD.getTelegramHost() + "/" : tMeUrl;
  }

  public String tMeMessageUrl (String username, long messageId) {
    return tMeUrl(username + "/" + messageId);
  }

  public String tMePrivateMessageUrl (int supergroupId, long messageId) {
    return tMeUrl("c/" + supergroupId + "/" + messageId);
  }

  public Uri.Builder tMeUrlBuilder () {
    return new Uri.Builder()
      .scheme("https")
      .authority(tMeAuthority());
  }

  public String tMeUrl (String path) {
    return tMeUrlBuilder()
      .path(path)
      .build()
      .toString();
  }

  public String tMeStartUrl (String botUsername, String parameter, boolean inGroup) {
    return tMeUrlBuilder()
      .path(botUsername)
      .appendQueryParameter(inGroup ? "startgroup" : "start", parameter)
      .build()
      .toString();
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

  public @Nullable String tdlibVersionSignature () {
    return tdlibVersionSignature;
  }

  public String tMeHost () {
    return StringUtils.urlWithoutProtocol(tMeUrl());
  }

  public String tMeAuthority () {
    return Uri.parse(tMeUrl).getHost();
  }

  public boolean areTopChatsDisabled () {
    return disableTopChats == 1;
  }

  public void setDisableTopChats (boolean disable) {
    this.disableTopChats = disable ? 1 : 0;
    client().send(new TdApi.SetOption("disable_top_chats", new TdApi.OptionValueBoolean(disable)), okHandler);
  }

  public boolean areSentScheduledMessageNotificationsDisabled () {
    return disableSentScheduledMessageNotifications;
  }

  public void setDisableSentScheduledMessageNotifications (boolean disable) {
    this.disableSentScheduledMessageNotifications = disable;
    client().send(new TdApi.SetOption("disable_sent_scheduled_message_notifications", new TdApi.OptionValueBoolean(disable)), okHandler);
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

  public boolean isKnownHost (String host, boolean allowTelegraph) {
    if (StringUtils.isEmpty(host)) {
      return false;
    }
    host = StringUtils.urlWithoutProtocol(host.toLowerCase());
    int i = host.indexOf('/');
    if (i != -1) {
      host = host.substring(0, i);
    }
    if (!StringUtils.isEmpty(tMeUrl)) {
      String tMeHost = StringUtils.urlWithoutProtocol(tMeUrl);
      if (StringUtils.equalsOrBothEmpty(host, tMeHost)) {
        return true;
      }
    }
    for (String knownHost : TdConstants.TELEGRAM_HOSTS) {
      if (StringUtils.equalsOrBothEmpty(host, knownHost)) {
        return true;
      }
    }
    if (allowTelegraph) {
      for (String knownHost : TdConstants.TELEGRAPH_HOSTS) {
        if (StringUtils.equalsOrBothEmpty(host, knownHost)) {
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
    return qrLoginCamera && BuildConfig.DEBUG;
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

  public @ConnectionState int connectionState () {
    return connectionState;
  }

  public @Nullable TdApi.NetworkType networkType () {
    return networkType;
  }

  public boolean isConnected () {
    return connectionState == STATE_CONNECTED;
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

  private static final int MSG_ACTION_SET_STATUS = 0;
  private static final int MSG_ACTION_UPDATE_USER_ACTION = 1;
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
      case MSG_ACTION_SET_STATUS:
        processAuthState(msg.arg1, (TdApi.AuthorizationState) msg.obj, msg.arg2);
        break;
      case MSG_ACTION_UPDATE_USER_ACTION:
        statusManager.onUpdateChatUserAction((TdApi.UpdateUserChatAction) msg.obj);
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
  public void dispatchCallStateChanged (final int callId, final int newState) {
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
  }

  private void resetContextualData () {
    chats.clear();
    chatLists.clear();
    knownChatIds.clear();
    accessibleChatTimers.clear();
    chatOnlineMemberCount.clear();
    myProfilePhoto = null;
    pendingMessageTexts.clear();
    pendingMessageCaptions.clear();
    animatedEmoji.clear();
    animatedDiceExplicit.clear();
    telegramServiceNotificationsChatId = TdConstants.TELEGRAM_ACCOUNT_ID;
    repliesBotChatId = TdConstants.TELEGRAM_REPLIES_BOT_ACCOUNT_ID;
    // animatedTgxEmoji.clear();
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

  @Nullable
  public TdApi.Sticker findAnimatedEmoji (String emoji) {
    return animatedEmoji.find(emoji);
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
          } else if (diceEmoji.equals(TD.EMOJI_DICE.textRepresentation)) {
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
    explicitDice = animatedDiceExplicit.find(TD.EMOJI_DICE.textRepresentation);
    return explicitDice;
  }

  @Nullable
  public TdApi.DiceStickers findDiceEmoji (String emoji, int value, TdApi.DiceStickers defaultValue) {
    if (TD.EMOJI_DICE.textRepresentation.equals(emoji)) {
      TdApi.Sticker explicitDice = findExplicitDiceEmoji(value);
      if (explicitDice != null)
        return new TdApi.DiceStickersRegular(explicitDice);
    }
    return defaultValue;
  }

  /*@Nullable
  public TdApi.Sticker findTgxEmoji (String emoji) {
    return animatedTgxEmoji.find(emoji);
  }*/

  @TdlibThread
  private void onUpdateHavePendingNotifications (TdApi.UpdateHavePendingNotifications update) {
    boolean havePendingNotifications = update.haveDelayedNotifications || update.haveUnreceivedNotifications;
    if (this.havePendingNotifications != havePendingNotifications) {
      this.havePendingNotifications = havePendingNotifications;
      checkKeepAlive();
    }
    if (!update.haveUnreceivedNotifications) {
      this.notificationConsistencyListeners.trigger(true);
    }
    incrementNotificationReferenceCount();
    // notificationManager.onUpdateHavePendingNotifications(update);
    notificationManager.releaseTdlibReference(havePendingNotifications ? null : this::onNotificationsReceived);
  }

  private long receivedActiveNotificationsTime;

  @TdlibThread
  private void onUpdateActiveNotifications (TdApi.UpdateActiveNotifications update) {
    TDLib.Tag.notifications(0, accountId, "Received updateActiveNotifications");
    receivedActiveNotificationsTime = SystemClock.uptimeMillis();
    notificationManager.onUpdateActiveNotifications(update, this::dispatchNotificationsInitialized);
  }

  @TdlibThread
  private void onUpdateNotificationGroup (TdApi.UpdateNotificationGroup update) {
    TDLib.Tag.notifications(0, accountId, "Received updateNotificationGroup, groupId: %d, elapsed: %d", update.notificationGroupId, SystemClock.uptimeMillis() - receivedActiveNotificationsTime);
    notificationManager.onUpdateNotificationGroup(update);
  }

  @TdlibThread
  private void onUpdateNotification (TdApi.UpdateNotification update) {
    notificationManager.onUpdateNotification(update);
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
      for (int i = delta - 1; i >= 0; i--) {
        decrementReferenceCount(REFERENCE_TYPE_MESSAGE);
      }
    }
  }

  private void updateNewMessage (TdApi.UpdateNewMessage update, boolean isUpdate) {
    if (update.message.sendingState instanceof TdApi.MessageSendingStatePending && update.message.content.getConstructor() != TdApi.MessageChatSetTtl.CONSTRUCTOR) {
      addRemoveSendingMessage(update.message.chatId, update.message.id, true);
      if (isUpdate)
        return;
    }

    listeners.updateNewMessage(update);

    notificationManager.onUpdateNewMessage(update);

    context.global().notifyUpdateNewMessage(this, update);
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
    UI.showError(new TdApi.Error(update.errorCode, update.errorMessage));
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
        int supergroupId = ((TdApi.ChatTypeSupergroup) update.chat.type).supergroupId;
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
    private static final int ORDER = 1;
    private static final int SOURCE = 1 << 1;
    private static final int PIN_STATE = 1 << 2;

    public final TdApi.ChatPosition position;
    private final int flags;

    public ChatChange (TdApi.ChatPosition position, int flags) {
      this.position = position;
      this.flags = flags;
    }

    public boolean orderChanged () {
      return BitwiseUtils.getFlag(flags, ORDER);
    }

    public boolean metadataChanged () {
      return BitwiseUtils.setFlag(flags, ORDER, false) != 0;
    }

    public boolean sourceChanged () {
      return BitwiseUtils.getFlag(flags, SOURCE);
    }

    public boolean pinStateChanged () {
      return BitwiseUtils.getFlag(flags, PIN_STATE);
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

  private TdApi.ChatFilterInfo[] chatFilters;

  @TdlibThread
  private void updateChatFilters (TdApi.UpdateChatFilters update) {
    synchronized (dataLock) {
      this.chatFilters = update.chatFilters;
    }
    listeners.updateChatFilters(update);
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
      int myUserId = myUserId();
      if (myUserId != 0) {
        TdlibNotificationChannelGroup.updateChat(this, myUserId, chat);
      }
    }
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
  private void updateChatMessageTtlSetting (TdApi.UpdateChatMessageTtlSetting update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.messageTtlSetting = update.messageTtlSetting;
    }
    listeners.updateChatMessageTtlSetting(update);
  }

  @TdlibThread
  private void updateChatVoiceChat (TdApi.UpdateChatVoiceChat update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.voiceChat = update.voiceChat;
    }
    listeners.updateChatVoiceChat(update);
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
  private void updateChatIsBlocked (TdApi.UpdateChatIsBlocked update) {
    synchronized (dataLock) {
      final TdApi.Chat chat = chats.get(update.chatId);
      if (TdlibUtils.assertChat(update.chatId, chat, update)) {
        return;
      }
      chat.isBlocked = update.isBlocked;
    }

    listeners.updateChatIsBlocked(update);
  }

  // Updates: CHAT STATUS

  @TdlibThread
  private void updateChatUserAction (final TdApi.UpdateUserChatAction update) {
    if (update.chatId != myUserId()) {
      ui().sendMessage(ui().obtainMessage(MSG_ACTION_UPDATE_USER_ACTION, 0, 0, update));
    }
  }

  // Updates: CALLS

  @TdlibThread
  private void updateCall (TdApi.UpdateCall update) {
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

  // Updates: PRIVACY

  @TdlibThread
  private void updatePrivacySettingRules (TdApi.UpdateUserPrivacySettingRules update) {
    listeners.updatePrivacySettingRules(update.setting, update.rules);
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
          int supergroupId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
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

  // Updates: CONFIG

  @TdlibThread
  private void updateConnectionState (TdApi.UpdateConnectionState update) {
    final int state;
    switch (update.state.getConstructor()) {
      case TdApi.ConnectionStateWaitingForNetwork.CONSTRUCTOR:
        state = STATE_WAITING;
        break;
      case TdApi.ConnectionStateConnectingToProxy.CONSTRUCTOR:
        state = STATE_CONNECTING_TO_PROXY;
        break;
      case TdApi.ConnectionStateConnecting.CONSTRUCTOR:
        state = STATE_CONNECTING;
        break;
      case TdApi.ConnectionStateUpdating.CONSTRUCTOR:
        state = STATE_UPDATING;
        break;
      case TdApi.ConnectionStateReady.CONSTRUCTOR:
        state = STATE_CONNECTED;
        break;
      default:
        throw new UnsupportedOperationException(update.toString());
    }

    if (this.connectionState != state) {
      int prevState = this.connectionState;
      this.connectionState = state;
      if (state == STATE_CONNECTED || state == STATE_WAITING) {
        connectionLossTime = 0;
      } else if (connectionLossTime == 0) {
        connectionLossTime = SystemClock.uptimeMillis();
      }
      listeners.updateConnectionState(state, prevState);
      context.onConnectionStateChanged(this, state);
      if (state == STATE_CONNECTED) {
        onConnected();
      } else if (prevState == STATE_CONNECTED) {
        context().watchDog().helpDogeIfInBackground();
      }
    }
  }

  public long calculateConnectionTimeoutMs (long timeoutMs) {
    long time = timeSinceFirstConnectionAttemptMs();
    return time != 0 ? Math.max(0, timeoutMs - time) : timeoutMs;
  }

  /**
   * @return Zero if connection is stablished or network is unavailable and amount of milliseconds otherwise.
   */
  public long timeSinceFirstConnectionAttemptMs () {
    long time = connectionLossTime;
    return time != 0 ? SystemClock.uptimeMillis() - time : 0;
  }

  @TdlibThread
  private void onUpdateMyUserId (int myUserId) {
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
  }

  @AnyThread
  public TdApi.SuggestedAction[] getSuggestedActions () {
    synchronized (dataLock) {
      return suggestedActions.toArray(new TdApi.SuggestedAction[0]);
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

    context.incrementBadgeCounters(chatList, unreadMessageCount - oldUnreadCount, unreadUnmutedCount - oldUnreadUnmutedCount, false);
    account().storeCounter(chatList, counter, false);
    listeners().notifyMessageCountersChanged(chatList, unreadMessageCount, unreadUnmutedCount);

    return true;
  }

  @UiThread
  private void dispatchUnreadCounters (@NonNull TdApi.ChatList chatList, int count, boolean isMuted) {
    context.global().notifyCountersChanged(this, chatList, count, isMuted);
  }

  @TdlibThread
  private boolean setUnreadChatCounters(@NonNull TdApi.ChatList chatList, int totalCount, int unreadChatCount, int unreadUnmutedCount, int markedAsUnreadCount, int markedAsUnreadUnmutedCount) {
    TdlibCounter counter = getCounter(chatList);

    int oldUnreadCount = Math.max(counter.chatCount, 0);
    int oldUnreadUnmutedCount = Math.max(counter.chatUnmutedCount, 0);
    int oldTotalChatCount = counter.totalChatCount;

    if (counter.setChatCounters(totalCount, unreadChatCount, unreadUnmutedCount, markedAsUnreadCount, markedAsUnreadUnmutedCount)) {
      context.incrementBadgeCounters(chatList, unreadChatCount - oldUnreadCount, unreadUnmutedCount - oldUnreadUnmutedCount, true);
      account().storeCounter(chatList, counter, true);
      listeners().notifyChatCountersChanged(chatList, (totalCount > 0) != (oldTotalChatCount > 0), totalCount, unreadChatCount, unreadUnmutedCount);
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
  private void updateOption (ClientHolder context, TdApi.UpdateOption update) {
    final String name = update.name;

    if (!name.isEmpty() && name.charAt(0) == 'x') {
      // TGSettingsManager.instance().onUpdateOption(name, update.value);
      return;
    }

    switch (update.value.getConstructor()) {
      case TdApi.OptionValueInteger.CONSTRUCTOR: {
        long longValue = ((TdApi.OptionValueInteger) update.value).value;

        if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
          Log.v(Log.TAG_TDLIB_OPTIONS,"optionInteger %s -> %d", name, longValue);
        }

        switch (name) {
          case "my_id":
            onUpdateMyUserId((int) longValue);
            break;
          case "unix_time": {
            final long receivedTime = SystemClock.elapsedRealtime();
            synchronized (dataLock) {
              this.unixTime = longValue;
              this.unixTimeReceived = receivedTime;
            }
            break;
          }
          case "supergroup_size_max":
            this.supergroupMaxSize = (int) longValue;
            break;
          case "forwarded_messages_count_max":
            this.forwardMaxCount = (int) longValue;
            break;
          case "basic_group_size_max":
            this.groupMaxSize = (int) longValue;
            break;
          case "authorization_date":
            this.authorizationDate = longValue;
            break;
          case "pinned_chat_count_max":
            this.pinnedChatsMaxCount = (int) longValue;
            break;
          case "pinned_archived_chat_count_max":
            this.pinnedArchivedChatsMaxCount = (int) longValue;
            break;
          case "call_connect_timeout_ms":
            this.callConnectTimeoutMs = longValue;
            break;
          case "call_packet_timeout_ms":
            this.callPacketTimeoutMs = longValue;
            break;
          case "message_text_length_max":
            this.maxMessageTextLength = (int) longValue;
            break;
          case "message_caption_length_max":
            this.maxMessageCaptionLength = (int) longValue;
            break;
          case "replies_bot_chat_id":
            this.repliesBotChatId = longValue;
            break;
          case "telegram_service_notifications_chat_id":
            this.telegramServiceNotificationsChatId = longValue;
            break;
        }

        break;
      }

      case TdApi.OptionValueBoolean.CONSTRUCTOR: {
        boolean boolValue = ((TdApi.OptionValueBoolean) update.value).value;

        if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
          Log.v(Log.TAG_TDLIB_OPTIONS,"optionBool %s -> %b", name, boolValue);
        }

        switch (name) {
          case "disable_contact_registered_notifications": {
            setDisableContactRegisteredNotificationsImpl(boolValue);
            break;
          }
          case "disable_top_chats": {
            if (disableTopChats == -1) {
              disableTopChats = boolValue ? 1 : 0;
            } else {
              int desiredValue = boolValue ? 1 : 0;
              if (disableTopChats != desiredValue) {
                this.disableTopChats = desiredValue;
                listeners().updateTopChatsDisabled(boolValue);
              }
            }
            break;
          }
          case "disable_sent_scheduled_message_notifications": {
            if (this.disableSentScheduledMessageNotifications != boolValue) {
              this.disableSentScheduledMessageNotifications = boolValue;
              listeners().updatedSentScheduledMessageNotificationsDisabled(boolValue);
            }
            break;
          }
          case "calls_enabled": {
            this.callsEnabled = boolValue;
            break;
          }
          case "is_location_visible": {
            this.isLocationVisible = boolValue;
            break;
          }
          case "expect_blocking": {
            this.expectBlocking = boolValue;
            break;
          }
          case "can_ignore_sensitive_content_restrictions": {
            this.canIgnoreSensitiveContentRestrictions = boolValue;
            break;
          }
          case "can_archive_and_mute_new_chats_from_unknown_users": {
            this.canArchiveAndMuteNewChatsFromUnknownUsers = boolValue;
            break;
          }
          case "archive_and_mute_new_chats_from_unknown_users": {
            if (this.archiveAndMuteNewChatsFromUnknownUsers != boolValue) {
              this.archiveAndMuteNewChatsFromUnknownUsers = boolValue;
              listeners().updateArchiveAndMuteChatsFromUnknownUsersEnabled(boolValue);
            }
            break;
          }
          case "ignore_sensitive_content_restrictions": {
            this.ignoreSensitiveContentRestrictions = boolValue;
            break;
          }
        }

        /*if ("network_unreachable".equals(name)) {
          WatchDog.instance().setTGUnreachable(boolValue);
        }*/

        break;
      }
      case TdApi.OptionValueEmpty.CONSTRUCTOR: {
        if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
          Log.v(Log.TAG_TDLIB_OPTIONS,"optionEmpty %s -> empty", name);
        }

        switch (name) {
          case "my_id":
            cache.onUpdateMyUserId(0);
            break;
        }

        break;
      }
      case TdApi.OptionValueString.CONSTRUCTOR: {
        String stringValue = ((TdApi.OptionValueString) update.value).value;

        if (Log.isEnabled(Log.TAG_TDLIB_OPTIONS)) {
          Log.v(Log.TAG_TDLIB_OPTIONS, "optionString %s -> %s", name, stringValue);
        }

        switch (name) {
          case "t_me_url":
            this.tMeUrl = stringValue;
            break;
          case "version":
            this.tdlibVersionSignature = stringValue;
            break;
          case "animation_search_bot_username":
            this.animationSearchBotUsername = stringValue;
            break;
          case "venue_search_bot_username":
            this.venueSearchBotUsername = stringValue;
            break;
          case "photo_search_bot_username":
            this.photoSearchBotUsername = stringValue;
            break;
          /*FIXME server
          case "animated_dice_sticker_set_name": {
            animatedDice.reload(this, stringValue);
            break;
          }*/
          case "animated_emoji_sticker_set_name": {
            animatedEmoji.reload(this, stringValue);
            break;
          }
          case "language_pack_id":
            setLanguagePackIdImpl(stringValue, false);
            break;
          case "suggested_language_pack_id": {
            if (this.suggestedLanguagePackId == null || !this.suggestedLanguagePackId.equals(stringValue)) {
              this.suggestedLanguagePackId = stringValue;
              this.suggestedLanguagePackInfo = null;
              listeners().updateSuggestedLanguageChanged(stringValue, null);
              context.client.send(new TdApi.GetLanguagePackInfo(stringValue), result -> {
                switch (result.getConstructor()) {
                  case TdApi.LanguagePackInfo.CONSTRUCTOR:
                    setSuggestedLanguagePackInfo(stringValue, (TdApi.LanguagePackInfo) result);
                    break;
                  case TdApi.Error.CONSTRUCTOR:
                    Log.e("Failed to fetch suggested language, code: %s %s", stringValue, TD.toErrorString(result));
                    setSuggestedLanguagePackInfo(stringValue, null);
                    break;
                  default:
                    Log.unexpectedTdlibResponse(result, TdApi.GetLanguagePackInfo.class, TdApi.LanguagePackInfo.class, TdApi.Error.class);
                    break;
                }
              });
            }
            break;
          }
        }
        break;
      }
    }
  }

  // Updates: MEDIA

  private void updateAnimationSearchParameters (TdApi.UpdateAnimationSearchParameters update) {
    // TODO
  }

  private void updateInstalledStickerSets (TdApi.UpdateInstalledStickerSets update) {
    if (!update.isMasks) {
      synchronized (dataLock) {
        installedStickerSetCount = update.stickerSetIds.length;
      }
    }
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

  private void updateStickerSet (TdApi.StickerSet stickerSet) {
    animatedEmoji.update(this, stickerSet);
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
      int senderUserId = Td.getSenderUserId(chat.lastMessage);
      if (senderUserId != 0) {
        sendFakeUpdate(new TdApi.UpdateUserChatAction(chat.id, 0, senderUserId, action), false);
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

      // Voice chats
      case TdApi.UpdateChatVoiceChat.CONSTRUCTOR: {
        updateChatVoiceChat((TdApi.UpdateChatVoiceChat) update);
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
      case TdApi.UpdateChatMessageTtlSetting.CONSTRUCTOR: {
        updateChatMessageTtlSetting((TdApi.UpdateChatMessageTtlSetting) update);
        break;
      }
      case TdApi.UpdateChatFilters.CONSTRUCTOR: {
        updateChatFilters((TdApi.UpdateChatFilters) update);
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
      case TdApi.UpdateChatIsBlocked.CONSTRUCTOR: {
        updateChatIsBlocked((TdApi.UpdateChatIsBlocked) update);
        break;
      }
      case TdApi.UpdateChatDefaultDisableNotification.CONSTRUCTOR: {
        updateChatDefaultDisableNotifications((TdApi.UpdateChatDefaultDisableNotification) update);
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
      case TdApi.UpdateChatActionBar.CONSTRUCTOR: {
        updateChatActionBar((TdApi.UpdateChatActionBar) update);
        break;
      }
      case TdApi.UpdateChatHasScheduledMessages.CONSTRUCTOR: {
        updateChatHasScheduledMessages((TdApi.UpdateChatHasScheduledMessages) update);
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
      case TdApi.UpdateUserChatAction.CONSTRUCTOR: {
        updateChatUserAction((TdApi.UpdateUserChatAction) update);
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
        cache.onUpdateBasicGroupFull((TdApi.UpdateBasicGroupFullInfo) update);
        break;
      }

      // Channels
      case TdApi.UpdateSupergroup.CONSTRUCTOR: {
        updateSupergroup((TdApi.UpdateSupergroup) update);
        break;
      }
      case TdApi.UpdateSupergroupFullInfo.CONSTRUCTOR: {
        cache.onUpdateSupergroupFull((TdApi.UpdateSupergroupFullInfo) update);
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

      // Files
      case TdApi.UpdateFile.CONSTRUCTOR: {
        TdApi.UpdateFile updateFile = (TdApi.UpdateFile) update;
        if (Log.isEnabled(Log.TAG_TDLIB_FILES)) {
          Log.i(Log.TAG_TDLIB_FILES, "updateFile id=%d size=%d expectedSize=%d remote=%s local=%s", updateFile.file.id, updateFile.file.size, updateFile.file.expectedSize, updateFile.file.remote.toString(), updateFile.file.local.toString());
        }
        updateFile(updateFile);
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
      case TdApi.UpdateSelectedBackground.CONSTRUCTOR: {
        // TODO?
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
      case TdApi.UpdateSuggestedActions.CONSTRUCTOR: {
        updateSuggestedActions((TdApi.UpdateSuggestedActions) update);
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

      // Bots
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
      case TdApi.UpdateChatMember.CONSTRUCTOR: {
        Log.unexpectedTdlibResponse(update, null, TdApi.Update.class);
        break;
      }
    }
  }

  private TdApi.ProfilePhoto myProfilePhoto;

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

  @TdlibThread
  void downloadMyUser (@Nullable TdApi.User user) {
    account().storeUserInformation(user);

    TdApi.ProfilePhoto newProfilePhoto = user != null ? user.profilePhoto : null;
    if (newProfilePhoto == null && myProfilePhoto == null)
      return;
    if (newProfilePhoto != null && myProfilePhoto != null && (newProfilePhoto.id == myProfilePhoto.id && newProfilePhoto.small.id == myProfilePhoto.small.id && newProfilePhoto.big.id == myProfilePhoto.big.id))
      return;
    myProfilePhoto = newProfilePhoto;
    if (newProfilePhoto != null) {
      client().send(new TdApi.DownloadFile(newProfilePhoto.small.id, TdlibFilesManager.CLOUD_PRIORITY + 1, 0, 0, true), profilePhotoHandler(false));
      client().send(new TdApi.DownloadFile(newProfilePhoto.big.id, TdlibFilesManager.CLOUD_PRIORITY, 0, 0, true), profilePhotoHandler(true));
    }
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

  private void onJobAdded () {
    incrementReferenceCount(REFERENCE_TYPE_JOB);
  }

  private void onJobRemoved () {
    decrementReferenceCount(REFERENCE_TYPE_JOB);
  }

  private final JobList
    initializationListeners = new JobList(() -> status != STATUS_UNKNOWN).onAddRemove(this::onJobAdded, this::onJobRemoved), // Executed once received authorization state
    connectionListeners = new JobList(() -> status != STATUS_UNKNOWN && connectionState == STATE_CONNECTED).onAddRemove(this::onJobAdded, this::onJobRemoved), // Executed once connected
    notificationInitListeners = new JobList(() -> status != STATUS_UNKNOWN && (status == STATUS_UNAUTHORIZED || haveInitializedNotifications)).onAddRemove(this::onJobAdded, this::onJobRemoved),
    notificationListeners = new JobList(() -> !havePendingNotifications).onAddRemove(this::onJobAdded, this::onJobRemoved),
    notificationConsistencyListeners = new JobList(() -> false),
    closeListeners = new JobList(() -> currentAuthState != null && currentAuthState.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR); // Executed once no pending notifications remaining

  public void awaitInitialization (@NonNull Runnable after) {
    initializationListeners.add(after);
  }

  public void awaitNotificationInitialization (@NonNull Runnable after) {
    notificationInitListeners.add(after);
  }

  public void awaitClose (@NonNull Runnable after, boolean force) {
    if (force || (currentAuthState != null && currentAuthState.getConstructor() == TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR)) {
      closeListeners.add(after);
    } else {
      after.run();
    }
  }

  public void awaitNotifications (@NonNull Runnable after) {
    notificationListeners.add(after);
  }

  public void awaitConnection (@NonNull Runnable after) {
    connectionListeners.add(after);
  }

  // Events

  private void onInitialized () {
    initializationListeners.trigger();
    notificationInitListeners.trigger();
    connectionListeners.trigger();
  }

  private void runStartupChecks () {
    checkDeviceToken();
    // animatedTgxEmoji.load(this);
    animatedEmoji.load(this);
    if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE) && !isDebugInstance()) {
      animatedDiceExplicit.load(this);
    }
  }

  private void onConnected () {
    setHasUnprocessedPushes(false);
    connectionListeners.trigger();
  }

  private void onNotificationsInitialized () {
    notificationInitListeners.trigger();
  }

  public void dispatchNotificationsInitialized () {
    runOnTdlibThread(() -> {
      if (!haveInitializedNotifications) {
        haveInitializedNotifications = true;
        onNotificationsInitialized();
      }
    });
  }

  private void onNotificationsReceived () {
    notificationListeners.trigger();
  }

  // Emoji

  void fetchAllMessages (long chatId, @Nullable String query, @Nullable TdApi.SearchMessagesFilter filter,  @NonNull RunnableData<List<TdApi.Message>> callback) {
    List<TdApi.Message> messages = new ArrayList<>();
    boolean needFilter = !StringUtils.isEmpty(query) || filter != null;
    TdApi.Function function;
    if (needFilter) {
      function = new TdApi.SearchChatMessages(chatId, query, null, 0, 0, 100, filter, 0);
    } else {
      function = new TdApi.GetChatHistory(chatId, 0, 0, 0, false);
    }
    client().send(function, new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object result) {
        switch (result.getConstructor()) {
          case TdApi.Messages.CONSTRUCTOR: {
            TdApi.Messages fetchedMessages = (TdApi.Messages) result;
            if (fetchedMessages.messages.length == 0) {
              callback.runWithData(messages);
            } else {
              Collections.addAll(messages, fetchedMessages.messages);
              long fromMessageId = messages.get(messages.size() - 1).id;
              if (needFilter) {
                ((TdApi.SearchChatMessages) function).fromMessageId = fromMessageId;
              } else {
                ((TdApi.GetChatHistory) function).fromMessageId = fromMessageId;
              }
              client().send(function, this);
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            if (messages.isEmpty())
              UI.showError(result);
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
    public final String version;

    public UpdateFileInfo (TdApi.Document document, int buildNo, String version) {
      this.document = document;
      this.buildNo = buildNo;
      this.version = version;
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
      if (message != null && message.content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR) {
        TdApi.Document document = ((TdApi.MessageDocument) message.content).document;
        boolean ok = false;
        int buildNo = 0;
        String version = null;
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
        onDone.runWithData(ok ? new UpdateFileInfo(document, buildNo, version) : null);
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
        T currentSetting = currentSettingProvider.get();
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
          settings.add(0, builtinItemProvider.get());
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
          if (((TdApi.ChatMemberStatusAdministrator) status).canInviteUsers) {
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
          if (((TdApi.ChatMemberStatusAdministrator) status).canChangeInfo) {
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
          if (((TdApi.ChatMemberStatusAdministrator) status).canRestrictMembers) {
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

  public boolean canToggleAllHistory (TdApi.Chat chat) {
    return ((isSupergroupChat(chat) && canChangeInfo(chat, false)) || canUpgradeChat(chat.id)) && StringUtils.isEmpty(chatUsername(chat.id));
  }

  public boolean canUpgradeChat (long chatId) {
    return ChatId.isBasicGroup(chatId) && TD.isCreator(chatStatus(chatId));
  }

  public boolean canPinMessages (TdApi.Chat chat) {
    if (chat == null || chat.id == 0 || !hasWritePermission(chat) || ChatId.isSecret(chat.id))
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
            return ((TdApi.ChatMemberStatusAdministrator) status).canEditMessages;
        }
      }
      return false;
    } else {
      if (status != null) {
        switch (status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            return true;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            if (((TdApi.ChatMemberStatusAdministrator) status).canPinMessages)
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
        TdApi.User user = cache().user(ChatId.toUserId(chat.id));
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
        }
        return new RestrictionStatus(chat.id, RESTRICTION_STATUS_UNAVAILABLE, 0);
      }
    }
    if (!TD.checkRight(chat.permissions, rightId))
      return new RestrictionStatus(chat.id, isNotSpecificallyRestricted ? RESTRICTION_STATUS_EVERYONE : RESTRICTION_STATUS_RESTRICTED, 0);
    return null;
  }

  public CharSequence getRestrictionText (TdApi.Chat chat, TdApi.Message message) {
    if (message != null) {
      switch (message.content.getConstructor()) {
        case TdApi.MessageAnimation.CONSTRUCTOR:
          return getGifRestrictionText(chat);
        case TdApi.MessageSticker.CONSTRUCTOR:
          return getStickerRestrictionText(chat);
        case TdApi.MessagePoll.CONSTRUCTOR:
          return getPollRestrictionText(chat);
        case TdApi.MessageAudio.CONSTRUCTOR:
        case TdApi.MessageDocument.CONSTRUCTOR:
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageVideo.CONSTRUCTOR:
        case TdApi.MessageVideoNote.CONSTRUCTOR:
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
          return getMediaRestrictionText(chat);
      }
    }
    return getMessageRestrictionText(chat);
  }

  public CharSequence getRestrictionText (TdApi.Chat chat, TdApi.InputMessageContent message) {
    if (message != null) {
      switch (message.getConstructor()) {
        case TdApi.InputMessageAnimation.CONSTRUCTOR:
          return getGifRestrictionText(chat);
        case TdApi.InputMessageSticker.CONSTRUCTOR:
          return getStickerRestrictionText(chat);
        case TdApi.InputMessageAudio.CONSTRUCTOR:
        case TdApi.InputMessageDocument.CONSTRUCTOR:
        case TdApi.InputMessagePhoto.CONSTRUCTOR:
        case TdApi.InputMessageVideo.CONSTRUCTOR:
        case TdApi.InputMessageVideoNote.CONSTRUCTOR:
        case TdApi.InputMessageVoiceNote.CONSTRUCTOR:
          return getMediaRestrictionText(chat);
      }
    }
    return getMessageRestrictionText(chat);
  }

  public CharSequence getMessageRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendMessages, R.string.ChatDisabledMessages, R.string.ChatRestrictedMessages, R.string.ChatRestrictedMessagesUntil);
  }

  public CharSequence getMediaRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendMedia, R.string.ChatDisabledMedia, R.string.ChatRestrictedMedia, R.string.ChatRestrictedMediaUntil);
  }

  public CharSequence getGifRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendStickersAndGifs, R.string.ChatDisabledGifs, R.string.ChatRestrictedGifs, R.string.ChatRestrictedGifsUntil);
  }

  public CharSequence getStickerRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendStickersAndGifs, R.string.ChatDisabledStickers, R.string.ChatRestrictedStickers, R.string.ChatRestrictedStickersUntil);
  }

  public CharSequence getInlineRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendStickersAndGifs, R.string.ChatDisabledBots, R.string.ChatRestrictedBots, R.string.ChatRestrictedBotsUntil);
  }

  public CharSequence getPollRestrictionText (TdApi.Chat chat) {
    return getRestrictionText(chat, R.id.right_sendPolls, R.string.ChatDisabledPolls, R.string.ChatRestrictedPolls, R.string.ChatRestrictedPollsUntil);
  }

  public CharSequence getRestrictionText (TdApi.Chat chat, @RightId int rightId, @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes) {
    RestrictionStatus status = getRestrictionStatus(chat, rightId);
    if (status != null) {
      switch (rightId) {
        case R.id.right_sendStickersAndGifs: {
          CharSequence restriction = getMediaRestrictionText(chat);
          if (restriction != null)
            return restriction;
          break;
        }
        case R.id.right_sendMedia:
        case R.id.right_sendPolls: {
          CharSequence restriction = getMessageRestrictionText(chat);
          if (restriction != null)
            return restriction;
          break;
        }
      }
      switch (status.status) {
        case RESTRICTION_STATUS_BANNED:
          return status.untilDate != 0 ? Lang.getString(R.string.ChatBannedUntil, Lang.getUntilDate(status.untilDate, TimeUnit.SECONDS)) : Lang.getString(R.string.ChatBanned);
        case RESTRICTION_STATUS_RESTRICTED:
          return status.untilDate != 0 ? Lang.getString(specificUntilRes, Lang.getUntilDate(status.untilDate, TimeUnit.SECONDS)) : Lang.getString(specificRes);
        case RESTRICTION_STATUS_UNAVAILABLE:
        case RESTRICTION_STATUS_EVERYONE:
          return Lang.getString(status.isUserChat() ? R.string.UserDisabledMessages : defaultRes);
      }

      throw new UnsupportedOperationException();
    }
    return null;
  }

  public boolean showRestriction (TdApi.Chat chat, @RightId int rightId, @StringRes int defaultRes, @StringRes int specificRes, @StringRes int specificUntilRes) {
    CharSequence res = getRestrictionText(chat, rightId, defaultRes, specificRes, specificUntilRes);
    if (res != null) {
      UI.showToast(res, Toast.LENGTH_SHORT);
      return true;
    }
    return false;
  }

  public boolean canAddWebPagePreviews (TdApi.Chat chat) {
    return getRestrictionStatus(chat, R.id.right_embedLinks) == null;
  }

  public boolean canSendOtherMessages (TdApi.Chat chat) {
    return getRestrictionStatus(chat, R.id.right_sendStickersAndGifs) == null;
  }

  public boolean canSendMessages (TdApi.Chat chat) {
    return getRestrictionStatus(chat, R.id.right_sendMessages) == null;
  }

  public boolean canSendMedia (TdApi.Chat chat) {
    return getRestrictionStatus(chat, R.id.right_sendMedia) == null;
  }
}
