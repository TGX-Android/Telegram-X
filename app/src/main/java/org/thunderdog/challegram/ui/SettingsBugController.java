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
 * File created on 06/03/2017
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.GlobalTokenStateListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.AppInstallationUtil;
import org.thunderdog.challegram.util.Crash;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPController;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.td.ChatPosition;
import me.vkryl.td.Td;

public class SettingsBugController extends RecyclerViewController<SettingsBugController.Args> implements
  View.OnClickListener,
  ViewController.SettingsIntDelegate,
  View.OnLongClickListener,
  Log.OutputListener,
  Settings.PushStatsListener,
  GlobalTokenStateListener {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Section.MAIN,
    Section.UTILITIES,
    Section.TDLIB,
    Section.ERROR,
    Section.PUSH,
    Section.EXPERIMENTS
  })
  public @interface Section {
    int
      MAIN = 0,
      UTILITIES = 1,
      TDLIB = 2,
      ERROR = 3,
      PUSH = 4,
      EXPERIMENTS = 5;
  }

  public static class Args {
    public final @Section int section;
    public final Crash crash;
    private int testerLevel = Tdlib.TESTER_LEVEL_NONE;
    private boolean mainCrash;

    public Args (@Section int section) {
      this(section, null);
    }

    public Args (Crash crash) {
      this.crash = crash;
      this.mainCrash = true;
      if (crash.getType() == Crash.Type.TDLIB) {
        this.section = Section.TDLIB;
      } else {
        this.section = Section.ERROR;
      }
    }

    public Args (@Section int section, Crash crash) {
      this.section = section;
      this.crash = crash;
    }

    public Args setTesterLevel (int level) {
      this.testerLevel = level;
      return this;
    }
  }

  public SettingsBugController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private @Section int section = Section.MAIN;
  private int testerLevel = Tdlib.TESTER_LEVEL_NONE;
  private Crash crash;
  private boolean isMainCrash;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.section = args != null ? args.section : Section.MAIN;
    this.testerLevel = args != null ? args.testerLevel : Tdlib.TESTER_LEVEL_NONE;
    this.crash = args != null ? args.crash : null;
    this.isMainCrash = args != null && args.mainCrash;
  }

  @Override
  public int getId () {
    return R.id.controller_bug_killer;
  }

  @Override
  public CharSequence getName () {
    if (isMainCrash) {
      return Lang.getString(R.string.LaunchTitle);
    }
    switch (section) {
      case Section.MAIN:
        return BuildConfig.VERSION_NAME;
      case Section.UTILITIES:
        return Lang.getString(R.string.TestMode);
      case Section.PUSH:
        return Lang.getString(R.string.PushServices);
      case Section.TDLIB:
        return "TDLib " + getTdlibVersionSignature(false);
      case Section.ERROR:
        return Lang.getString(R.string.LaunchTitle);
      case Section.EXPERIMENTS:
        return Lang.getString(R.string.ExperimentalSettings);
    }
    throw new AssertionError(section);
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    Args args = getArguments();
    if (args == null || args.section != Section.EXPERIMENTS) {
      return false;
    }
    outState.putInt(keyPrefix + "section", args.section);
    outState.putInt(keyPrefix + "level", args.testerLevel);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    int section = in.getInt(keyPrefix + "section", Section.MAIN);
    int testerLevel = in.getInt(keyPrefix + "level", Tdlib.TESTER_LEVEL_NONE);
    if (section != Section.EXPERIMENTS) {
      return false;
    }
    setArguments(new Args(section).setTesterLevel(testerLevel));
    return true;
  }

  private String getTdlibVersionSignature (boolean needBuildNo) {
    final String signature = TdlibUi.getTdlibVersionSignature().toString();
    return signature + (needBuildNo ? " (" + BuildConfig.ORIGINAL_VERSION_CODE + ")" : "");
  }

  private SettingsAdapter adapter;

  private static String getLogVerbosity (int level) {
    switch (level) {
      case Settings.TDLIB_LOG_VERBOSITY_UNKNOWN: return "-1";
      case 0: return "ASSERT";
      case 1: return "ERROR";
      case 2: return "WARNING";
      case 3: return "INFO";
      case 4: return "DEBUG";
      case 5: return "VERBOSE";
    }
    return "MORE:" + level;
  }

  private final long[] logSize = new long[2];

  private Runnable scheduledCheck;

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    if (section == Section.TDLIB) {
      if (isFocused()) {
        UI.post(new Runnable() {
          @Override
          public void run () {
            if (isFocused()) {
              checkLogSize(false);
              checkLogSize(true);
              UI.post(this, 1500);
            }
          }
        }, 1500);
      } else if (scheduledCheck != null) {
        UI.removePendingRunnable(scheduledCheck);
        scheduledCheck = null;
      }
    }
  }

  private void setLogSize (long size, boolean old) {
    final int i = old ? 1 : 0;
    if (this.logSize[i] != size) {
      this.logSize[i] = size;
      if (adapter != null) {
        adapter.updateValuedSettingById(old ? R.id.btn_tdlib_viewLogsOld : R.id.btn_tdlib_viewLogs);
      }
    }
  }

  private void checkLogSize (boolean old) {
    try {
      setLogSize(TdlibManager.getLogFileSize(old), old);
    } catch (Throwable ignored) { }
  }

  private Log.LogFiles logFiles;
  private boolean filesLoaded;

  private void setLogFiles (Log.LogFiles files) {
    this.logFiles = files;
    this.filesLoaded = true;
    adapter.updateValuedSettingById(R.id.btn_log_files);
  }

  private long lastLoadLog;

  private boolean needsAppLogs () {
    return lastLoadLog == 0 || (filesLoaded && (logFiles == null || logFiles.isEmpty() || SystemClock.elapsedRealtime() - lastLoadLog >= 1000l));
  }

  private void getLogFiles () {
    if (needsAppLogs()) {
      lastLoadLog = SystemClock.elapsedRealtime();
      Log.getLogFiles(result -> UI.post(() -> {
        if (!isDestroyed()) {
          setLogFiles(result);
        }
      }));
    }
  }

  private boolean isDeleting;

  private void setIsDeleting (boolean isDeleting) {
    if (this.isDeleting != isDeleting) {
      this.isDeleting = isDeleting;
      adapter.updateValuedSettingById(R.id.btn_log_files);
    }
  }

  private void deleteAllFiles () {
    if (filesLoaded && logFiles != null && !logFiles.isEmpty() && !isDeleting) {
      setIsDeleting(true);
      Log.deleteAll(logFiles, result -> {
        if (!isDestroyed()) {
          UI.post(() -> {
            if (!isDestroyed()) {
              setLogFiles(logFiles);
              setIsDeleting(false);
            }
          });
        }
      }, null);
    }
  }

  @Override
  public void onLogOutput (int tag, int level, String message, @Nullable Throwable t) {
    if (level <= Log.LEVEL_WARNING || needsAppLogs()) {
      UI.post(() -> {
        if (!isDestroyed()) {
          getLogFiles();
        }
      });
    }
  }

  @Override
  public void onLogFilesAltered () {
    UI.post(() -> {
      if (!isDestroyed()) {
        getLogFiles();
      }
    });
  }

  private String getDiskAvailableInfo () {
    return Strings.buildSize(U.getAvailableInternalMemorySize());
  }

  private String getCrashName () {
    switch (crash.getType()) {
      case Crash.Type.TDLIB_EXTERNAL_ERROR:
        return Lang.getString(R.string.LaunchSubtitleExternalError);
      case Crash.Type.DISK_FULL:
        return Lang.getString(R.string.LaunchSubtitleDiskFull);
      case Crash.Type.TDLIB:
      case Crash.Type.TDLIB_INITIALIZATION_FAILURE:
        return Lang.getString(R.string.LaunchSubtitleTdlibIssue, getTdlibVersionSignature(true));
      case Crash.Type.TDLIB_DATABASE_BROKEN:
        return Lang.getString(R.string.LaunchSubtitleDatabaseBroken);
      case Crash.Type.UNCAUGHT_EXCEPTION:
        return Lang.getString(R.string.LaunchSubtitleFatalError);
      case Crash.Type.UNKNOWN:
      default:
        return null;
    }
  }

  private CharSequence getCrashGuide () {
    int resId;
    switch (crash.getType()) {
      case Crash.Type.TDLIB_EXTERNAL_ERROR:
        resId = R.string.LaunchAppGuideExternalError;
        break;
      case Crash.Type.DISK_FULL:
        resId = R.string.LaunchAppGuideDiskFull;
        break;
      case Crash.Type.TDLIB:
      case Crash.Type.TDLIB_INITIALIZATION_FAILURE:
        resId = R.string.LaunchAppGuideTdlibIssue;
        break;
      case Crash.Type.TDLIB_DATABASE_BROKEN:
        resId = R.string.LaunchAppGuideDatabaseBroken;
        break;
      case Crash.Type.UNCAUGHT_EXCEPTION:
        resId = R.string.LaunchAppGuideFatalError;
        break;
      case Crash.Type.UNKNOWN:
      default:
        return null;
    }
    return Lang.getMarkdownStringSecure(this, resId, getDiskAvailableInfo(), AppInstallationUtil.getDownloadUrl(null).url);
  }

  @Override
  protected int getBackButton () {
    return isMainCrash ? BackHeaderButton.TYPE_CLOSE : super.getBackButton();
  }

  private DoubleHeaderView customHeaderCell;

  @Override
  public View getCustomHeaderCell () {
    return customHeaderCell;
  }

  private static String toHumanRepresentation (@Nullable TdApi.DeviceToken deviceToken) {
    if (deviceToken == null) {
      return "null";
    }
    switch (deviceToken.getConstructor()) {
      case TdApi.DeviceTokenFirebaseCloudMessaging.CONSTRUCTOR: {
        TdApi.DeviceTokenFirebaseCloudMessaging fcm = (TdApi.DeviceTokenFirebaseCloudMessaging) deviceToken;
        return "Firebase: " + fcm.token + ", encrypt: " + fcm.encrypt;
      }
      case TdApi.DeviceTokenHuaweiPush.CONSTRUCTOR: {
        TdApi.DeviceTokenHuaweiPush huaweiPush = (TdApi.DeviceTokenHuaweiPush) deviceToken;
        return "Huawei: " + huaweiPush.token + ", encrypt: " + huaweiPush.encrypt;
      }
      case TdApi.DeviceTokenSimplePush.CONSTRUCTOR: {
        TdApi.DeviceTokenSimplePush simplePush = (TdApi.DeviceTokenSimplePush) deviceToken;
        return "Simple Push: " + simplePush.endpoint;
      }
      default: {
        Td.assertDeviceToken_de4a4f61();
        throw Td.unsupported(deviceToken);
      }
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    checkLogSize(false);
    checkLogSize(true);

    if (isMainCrash) {
      customHeaderCell = new DoubleHeaderView(context);
      customHeaderCell.setThemedTextColor(this);
      customHeaderCell.initWithMargin(Screen.dp(18f), true);
      customHeaderCell.setTitle(getName());
      customHeaderCell.setSubtitle(getCrashName());
    }

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setEnabled(false);
        TdlibAccount account = (TdlibAccount) item.getData();
        chatView.setTitle(account.getName());
        chatView.setAvatar(account.getAvatarFile(false), account.getAvatarPlaceholderMetadata());
        chatView.setSubtitle(Lang.getString(R.string.LaunchAppUserSubtitle));
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (isMainCrash) {
          @PorterDuffColorId int colorId;
          if (itemId == R.id.btn_launchApp) {
            colorId = ColorId.iconActive;
              /*case R.id.btn_shareError:
            case R.id.btn_showError:*/
          } else if (itemId == R.id.btn_eraseDatabase) {
            colorId = ColorId.iconNegative;
          } else {
            colorId = ColorId.NONE;
          }
          view.setIconColorId(colorId);
        }
        if (itemId == R.id.btn_log_verbosity) {
          final boolean isCapturing = Log.isCapturing();
          if (isUpdate) {
            view.setEnabledAnimated(!isCapturing);
          } else {
            view.setEnabled(!isCapturing);
          }
          view.setData(getLogVerbosity(isCapturing ? Log.LEVEL_VERBOSE : Log.getLogLevel()));
        } else if (itemId == R.id.btn_secret_replacePhoneNumber) {
          view.getToggler().setRadioEnabled(Settings.instance().needHidePhoneNumber(), isUpdate);
        } else if (itemId == R.id.btn_secret_disableQrProcess) {
          view.getToggler().setRadioEnabled(Settings.instance().needDisableQrProcessing(), isUpdate);
        } else if (itemId == R.id.btn_secret_forceQrZxing) {
          view.getToggler().setRadioEnabled(Settings.instance().needForceZxingQrProcessing(), isUpdate);
        } else if (itemId == R.id.btn_secret_debugQrRegions) {
          view.getToggler().setRadioEnabled(Settings.instance().needShowQrRegions(), isUpdate);
        } else if (itemId == R.id.btn_secret_disableNetwork) {
          view.getToggler().setRadioEnabled(Settings.instance().forceDisableNetwork(), isUpdate);
        } else if (itemId == R.id.btn_secret_forceTcpInCalls) {
          view.getToggler().setRadioEnabled(Settings.instance().forceTcpInCalls(), isUpdate);
        } else if (itemId == R.id.btn_secret_forceTdlibRestarts) {
          view.getToggler().setRadioEnabled(Settings.instance().forceTdlibRestart(), isUpdate);
        } else if (itemId == R.id.btn_switchRtl) {
          view.getToggler().setRadioEnabled(Lang.rtl(), isUpdate);
        } else if (itemId == R.id.btn_experiment) {
          view.getToggler().setRadioEnabled(Settings.instance().isExperimentEnabled(item.getLongValue()), isUpdate);
        } else if (itemId == R.id.btn_secret_pushToken) {
          switch (tdlib.context().getTokenState()) {
            case TdlibManager.TokenState.ERROR:
              view.setData("Error: " + tdlib.context().getTokenError());
              break;
            case TdlibManager.TokenState.INITIALIZING:
              view.setData("Initializing...");
              break;
            case TdlibManager.TokenState.OK: {
              TdApi.DeviceToken deviceToken = tdlib.context().getToken();
              view.setData(toHumanRepresentation(deviceToken));
              break;
            }
            case TdlibManager.TokenState.NONE:
            default:
              view.setData("Unknown");
              break;
          }
        } else if (itemId == R.id.btn_secret_pushConfig) {
          String configuration = TdlibNotificationUtils.getTokenRetriever().getConfiguration();
          view.setData(!StringUtils.isEmpty(configuration) ? configuration : "Unavailable");
        } else if (itemId == R.id.btn_secret_appFingerprint) {
          view.setData(U.getApkFingerprint("SHA1"));
        } else if (itemId == R.id.btn_secret_pushStats) {
          view.setData(Settings.instance().getPushMessageStats());
        } else if (itemId == R.id.btn_secret_pushDate) {
          long time = Settings.instance().getLastReceivedPushMessageReceivedTime();
          if (time != 0) {
            view.setData(Lang.getTimestamp(time, TimeUnit.MILLISECONDS));
          } else {
            view.setData("No data");
          }
        } else if (itemId == R.id.btn_secret_pushDuration) {
          long durationMs = Settings.instance().getLastReceivedPushMessageReceivedTime() - Settings.instance().getLastReceivedPushMessageSentTime();
          if (durationMs != 0) {
            view.setData(Lang.getDuration((int) (durationMs / 1000)));
          } else {
            view.setData("No data");
          }
        } else if (itemId == R.id.btn_secret_pushTtl) {
          int ttl = Settings.instance().getLastReceivedPushMessageTtl();
          if (ttl != 0) {
            view.setData(Integer.toString(ttl));
          } else {
            view.setData("No data");
          }
        } else if (itemId == R.id.btn_secret_dontReadMessages) {
          view.getToggler().setRadioEnabled(Settings.instance().dontReadMessages(), isUpdate);
        } else if (itemId == R.id.btn_log_files) {
          final boolean isEnabled = !Log.isCapturing() && filesLoaded && !isDeleting && logFiles != null && !logFiles.isEmpty();
          if (isUpdate) {
            view.setEnabledAnimated(isEnabled);
          } else {
            view.setEnabled(isEnabled);
          }
          if (filesLoaded) {
            if (logFiles == null || logFiles.isEmpty()) {
              view.setData(Lang.plural(R.string.xFiles, 0));
            } else {
              StringBuilder b = new StringBuilder();
              b.append(Strings.buildSize(logFiles.totalSize));
              if (logFiles.logsCount > 0) {
                if (b.length() > 0) {
                  b.append(", ");
                }
                b.append(logFiles.logsCount);
                b.append(" log");
                if (logFiles.logsCount != 1) {
                  b.append('s');
                }
              }
              if (logFiles.crashesCount > 0) {
                if (b.length() > 0) {
                  b.append(", ");
                }
                b.append(logFiles.crashesCount);
                b.append(" crash");
                if (logFiles.crashesCount != 1) {
                  b.append("es");
                }
              }
              view.setData(b.toString());
            }
          } else {
            view.setData(R.string.LoadingInformation);
          }
        } else if (itemId == R.id.btn_log_tags) {
          final boolean isCapturing = Log.isCapturing();
          if (isUpdate) {
            view.setEnabledAnimated(!isCapturing);
          } else {
            view.setEnabled(!isCapturing);
          }
          StringBuilder b = new StringBuilder();
          for (int tag : Log.TAGS) {
            if (Log.isEnabled(tag)) {
              if (b.length() > 0) {
                b.append(", ");
              }
              b.append(Log.getLogTag(tag));
            }
          }
          if (b.length() == 0) {
            b.append("None");
          }
          view.setData(b.toString());
        } else if (itemId == R.id.btn_log_android) {
          view.getToggler().setRadioEnabled(Log.checkSetting(Log.SETTING_ANDROID_LOG), false);
        } else if (itemId == R.id.btn_tdlib_verbosity) {
          String module = (String) item.getData();
          Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
          int verbosity = settings.getVerbosity(module);
          if (module != null && verbosity == settings.getDefaultVerbosity(module)) {
            view.setData("Default");
            view.getToggler().setRadioEnabled(verbosity <= settings.getVerbosity(null), isUpdate);
          } else {
            view.setData(getLogVerbosity(verbosity));
            view.getToggler().setRadioEnabled(module != null ? verbosity <= settings.getVerbosity(null) : verbosity > 0, isUpdate);
          }
        } else if (itemId == R.id.btn_tdlib_logSize) {
          view.setData(Strings.buildSize(Settings.instance().getLogSettings().getLogMaxFileSize()));
        } else if (itemId == R.id.btn_tdlib_viewLogs) {
          view.setData(Strings.buildSize(logSize[0]));
        } else if (itemId == R.id.btn_tdlib_viewLogsOld) {
          view.setData(Strings.buildSize(logSize[1]));
        } else if (itemId == R.id.btn_tdlib_androidLogs) {
          view.getToggler().setRadioEnabled(Settings.instance().getLogSettings().needAndroidLog(), isUpdate);
        }
      }
    };
    adapter.setOnLongClickListener(this);

    ArrayList<ListItem> items = new ArrayList<>();

    if (isMainCrash) {
      if (crash.accountId != TdlibAccount.NO_ID) {
        TdlibAccount account = TdlibManager.instanceForAccountId(crash.accountId).account(crash.accountId);
        if (account.getDisplayInformation() != null) {
          items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(account));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
      }

      if (crash.getType() == Crash.Type.TDLIB && !StringUtils.isEmpty(crash.message)) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, crash.message, false));
      }

      if (!items.isEmpty())
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_launchApp, R.drawable.baseline_warning_24, R.string.LaunchApp).setTextColorId(ColorId.textNeutral));
      if (!(BuildConfig.DEBUG || BuildConfig.EXPERIMENTAL)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_update, R.drawable.baseline_system_update_24, R.string.LaunchAppCheckUpdate));
      }
      if (section != Section.TDLIB) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        if (crash.getType() == Crash.Type.DISK_FULL) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showError, R.drawable.baseline_info_24, R.string.LaunchAppViewError)/*.setTextColorId(ColorId.textNegative)*/);
        } else {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_shareError, R.drawable.baseline_share_24, R.string.LaunchAppShareError)/*.setTextColorId(ColorId.textNegative)*/);
        }
      }
      switch (crash.getType()) {
        case Crash.Type.TDLIB_INITIALIZATION_FAILURE:
        case Crash.Type.TDLIB_DATABASE_BROKEN:
        case Crash.Type.TDLIB: {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_eraseDatabase, R.drawable.baseline_delete_forever_24, R.string.LaunchAppEraseDatabase).setTextColorId(ColorId.textNegative));
          break;
        }
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, getCrashGuide(), false));
    }

    switch (section) {
      case Section.ERROR: {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib, 0, R.string.TdlibLogs, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_appLogs, 0, R.string.AppLogs, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case Section.MAIN: {
        if (items.isEmpty())
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AppLogs, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_verbosity, 0, R.string.DebugVerbosity, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_tags, 0, R.string.DebugLogTags, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_log_files, 0, R.string.DebugLogFiles, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_log_android, 0, R.string.DebugLogcat, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownStringSecure(this, R.string.DebugAppLogsInfo), false));

        if (crash == null) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Other));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib, 0, R.string.TdlibLogs, false));
          if (tdlib != null) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_pushService, 0, R.string.PushServices, false));
          }
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
        break;
      }
      case Section.PUSH: {
        if (tdlib == null)
          throw new IllegalStateException();
        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushToken, 0, "Token", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushStats, 0, "Packages received", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushDate, 0, "Last received on", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushDuration, 0, "Time from being sent", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushTtl, 0, "TTL", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushConfig, 0, "Configuration", false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_appFingerprint, 0, "App fingerprint", false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case Section.EXPERIMENTS: {
        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experiment, 0, R.string.Experiment_ChatFolders).setLongValue(Settings.EXPERIMENT_FLAG_ENABLE_FOLDERS));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownStringSecure(this, R.string.Experiment_ChatFoldersInfo)));

        if (testerLevel >= Tdlib.TESTER_LEVEL_TESTER || Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_SHOW_PEER_IDS)) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_experiment, 0, R.string.Experiment_PeerIds).setLongValue(Settings.EXPERIMENT_FLAG_SHOW_PEER_IDS));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Experiment_PeerIdsInfo));
        }

        break;
      }
      case Section.UTILITIES: {
        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
        int initialSize = items.size();
        if (tdlib != null && !tdlib.context().inRecoveryMode()) {
          // items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_readAllChats, 0, R.string.ReadAllChats, false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_tgcalls, 0, "tgcalls versions (not persistent)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_tgcallsOptions, 0, "tgcalls options (not persistent)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_tdlibDatabaseStats, 0, "TDLib database statistics", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_databaseStats, 0, "Other internal statistics", false));

          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_stressTest, 0, "Stress test TDLib restarts", false));
          }
          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().forceTdlibRestart()) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_forceTdlibRestarts, 0, "Force TDLib restarts", Settings.instance().forceTdlibRestart()));
          }

          if (testerLevel >= Tdlib.TESTER_LEVEL_DEVELOPER) {
            if (tdlib.isAuthorized()) {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
              items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_sendAllChangeLogs, 0, "Send all change logs", false));
            }
          }

          if (testerLevel >= Tdlib.TESTER_LEVEL_CREATOR) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_copyLanguageCodes, 0, "Copy language codes list", false));
          }

          TdApi.User user = tdlib.myUser();
          if (user != null && user.profilePhoto != null) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_deleteProfilePhoto, 0, "Delete profile photo from cache", false));
          }
        }

        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_dropSavedScrollPositions, 0, "Drop saved scroll positions", false));

        if (testerLevel >= Tdlib.TESTER_LEVEL_CREATOR || Settings.instance().dontReadMessages()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_dontReadMessages, 0, "Don't read messages", false));
        }

        if (items.size() > initialSize)
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_resetTutorials, 0, "Reset tutorials", false));

        if (tdlib != null) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_resetLocalNotificationSettings, 0, "Reset local notification settings", false));
        }

        if (tdlib != null) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_dropHidden, 0, "Drop hidden notification identifiers", false));
        }

        if (testerLevel >= Tdlib.TESTER_LEVEL_READER || Settings.instance().needHidePhoneNumber()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_replacePhoneNumber, 0, "Hide phone number in drawer", Settings.instance().needHidePhoneNumber()));
        }
        if (testerLevel >= Tdlib.TESTER_LEVEL_READER || Settings.instance().forceTcpInCalls()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_forceTcpInCalls, 0, "Force TCP in calls", Settings.instance().forceTcpInCalls()));
        }
        if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().forceDisableNetwork()) {
          if (items.size() > initialSize)
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_disableNetwork, 0, "Force disable network", Settings.instance().forceDisableNetwork()));
        }
        if (Config.QR_AVAILABLE) {
          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().needDisableQrProcessing()) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_disableQrProcess, 0, "Disable QR processing", Settings.instance().needDisableQrProcessing()));
          }
          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().needForceZxingQrProcessing()) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_forceQrZxing, 0, "Force ZXing in QR scanner", Settings.instance().needForceZxingQrProcessing()));
          }
          if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN || Settings.instance().needShowQrRegions()) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_debugQrRegions, 0, "Show QR scanner UI regions", Settings.instance().needForceZxingQrProcessing()));
          }
          if (testerLevel >= Tdlib.TESTER_LEVEL_TESTER) {
            if (items.size() > initialSize)
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_qrTest, 0, "Test QR scanner", false));
          }
        }

        /*if (Config.RTL_BETA) {
          items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new SettingItem(SettingItem.TYPE_RADIO_SETTING, R.id.btn_switchRtl, 0, R.string.RtlLayout, false));
          items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          items.add(new SettingItem(SettingItem.TYPE_SETTING, R.id.btn_debugSwitchRtl, 0, "Add / Remove RTL switcher", false));
        }*/

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Tests (crash when failed)", false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_database, 0, "Test database", false));
        if (testerLevel >= Tdlib.TESTER_LEVEL_ADMIN) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery, 0, "Crash & enter recovery (uncaught exception)", false).setData(new Crash.Builder("Test error", Thread.currentThread(), Log.generateException())));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery, 0, "Crash & save server log event (tdlib error)", false).setData(
            new Crash.Builder("test tdlib fatal error message").flags(Crash.Flags.SOURCE_TDLIB | Crash.Flags.SAVE_APPLICATION_LOG_EVENT)
          ));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (TDLib error)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (disk full)", false).setStringValue("database or disk is full"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (database broken)", false).setStringValue("Wrong key or database is corrupted"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_recovery_tdlib, 0, "Crash & enter recovery mode (other external error)", false).setStringValue("I/O error"));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash1, 0, "Crash app (method 1, indirect)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash2, 0, "Crash app (method 2, direct)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash3, 0, "Crash app (method 3, native indirect)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crash4, 0, "Crash app (method 4, native direct)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crashDirect, 0, "Crash app (default)", false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_test_crashDirectNative, 0, "Crash app (native)", false));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        break;
      }
      case Section.TDLIB: {
        if (items.isEmpty())
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

        if (isMainCrash) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_shareError, R.drawable.baseline_share_24, R.string.LaunchAppShareError).setTextColorId(ColorId.textNegative));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, /*section == SECTION_TON ? R.string.TonLogs :*/ R.string.TdlibLogs, false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tdlib_verbosity, 0, R.string.DebugVerbosity, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_logSize, 0, R.string.DebugLogSize, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_viewLogs, 0, TdlibManager.getLogFile(false).getName(), false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tdlib_viewLogsOld, 0, TdlibManager.getLogFile(true).getName(), false));
        Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
        if (testerLevel >= Tdlib.TESTER_LEVEL_DEVELOPER || settings.needAndroidLog()) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tdlib_androidLogs, 0, R.string.DebugLogcatOnly, false));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tdlib_resetLogSettings, 0, R.string.DebugReset, false));

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        List<String> modules = settings.getModules();
        if (modules != null && !modules.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.DebugModules, false));
          boolean first = true;
          for (String module : modules) {
            if (first) {
              items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
              first = false;
            } else {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            }
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_tdlib_verbosity, 0, module, false).setData(module));
          }
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownStringSecure(this, R.string.DebugModulesInfo), false));
        }

        if (isMainCrash) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_appLogs, 0, R.string.AppLogs, false));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        break;
      }
    }

    adapter.setItems(items, false);

    switch (section) {
      case Section.MAIN: {
        if (tdlib != null) {
          getLogFiles();
          Log.addOutputListener(this);

          if (crash == null) {
            tdlib.getTesterLevel(level -> {
              this.testerLevel = level;
              tdlib.uiExecute(() -> {
                if (!isDestroyed()) {
                  int i = adapter.indexOfViewById(R.id.btn_pushService);
                  if (i == -1) {
                    i = adapter.indexOfViewById(R.id.btn_tdlib);
                  }
                  if (i == -1)
                    return;
                  if (level > Tdlib.TESTER_LEVEL_NONE) {
                    adapter.getItems().add(i + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                    adapter.getItems().add(i + 2, new ListItem(ListItem.TYPE_SETTING, R.id.btn_testingUtils, 0, R.string.TestMode, false));
                    adapter.notifyItemRangeInserted(i + 1, 2);
                    i += 2;
                    if (level == Tdlib.TESTER_LEVEL_READER) {
                      adapter.addItem(i + 2, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Strings.buildMarkdown(this, "To unlock more Testing Utilities you have to be a member of @tgandroidtests.", null), false));
                    }
                  } else {
                    adapter.addItem(i + 2, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Strings.buildMarkdown(this, "To unlock Testing Utilities you have to be subscribed to @tgx_android or be a member of @tgandroidtests.", null), false));
                  }
                }
              });
            });
          }
        }
        break;
      }
      case Section.PUSH: {
        tdlib.context().global().addTokenStateListener(this);
        Settings.instance().addPushStatsListener(this);
        break;
      }
    }

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onTokenStateChanged (int newState, @Nullable String error, @Nullable Throwable fullError) {
    runOnUiThreadOptional(() -> {
      adapter.updateValuedSettingById(R.id.btn_secret_pushToken);
    });
  }

  @Override
  public void onNewPushReceived () {
    runOnUiThreadOptional(() -> {
      adapter.updateValuedSettingById(R.id.btn_secret_pushStats);
      adapter.updateValuedSettingById(R.id.btn_secret_pushDate);
      adapter.updateValuedSettingById(R.id.btn_secret_pushDuration);
      adapter.updateValuedSettingById(R.id.btn_secret_pushTtl);
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    Log.removeOutputListener(this);
    if (section == Section.PUSH) {
      tdlib.context().global().removeTokenStateListener(this);
      Settings.instance().removePushStatsListener(this);
    }
  }


  private static final int TEST_DATABASE = 1;
  private int runningTest;
  private void runTest (int test, boolean needPrompt) {
    if (runningTest != 0)
      return;
    if (needPrompt) {
      showWarning("Test may take some time. Don't be scared if it crashes.\n\nWarning: don't do anything in the app while test is running.", confirm -> {
        if (confirm) {
          runTest(test, false);
        }
      });
      return;
    }
    setStackLocked(true);
    Runnable after = () -> {
      if (!isDestroyed()) {
        setStackLocked(false);
      }
      UI.showToast("Test completed successfully", Toast.LENGTH_SHORT);
    };
    runningTest = test;
    switch (test) {
      case TEST_DATABASE:
        runDbTests(after);
        break;
      default:
        runningTest = 0;
        break;
    }
  }

  private void runDbTests (Runnable after) {
    UI.showToast("Running tests, please do nothing and wait...", Toast.LENGTH_SHORT);
    Background.instance().post(() -> {
      try {
        Test.testLevelDB();
      } catch (Error | RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new AssertionError(e);
      }
      UI.post(after);
    });
  }

  private boolean isErasingData;

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_launchApp) {
      ((MainActivity) context).proceedFromRecovery();
    } else if (viewId == R.id.btn_update) {
      Intents.openSelfGooglePlay();
    } else if (viewId == R.id.btn_shareError) {
      Intents.shareText(U.getUsefulMetadata(null) + "\n" + crash.message);
    } else if (viewId == R.id.btn_eraseDatabase) {
      if (isErasingData)
        return;
      if (crash.accountId != TdlibAccount.NO_ID) {
        tdlib.context().tdlib(crash.accountId).ui().eraseLocalData(this, false, new TdlibUi.EraseCallback() {
          @Override
          public void onPrepareEraseData () {
            isErasingData = true;
          }

          @Override
          public void onEraseDataCompleted () {
            isErasingData = false;
            ((MainActivity) context).proceedFromRecovery();
          }
        });
      } else {
        ((MainActivity) context()).batchPerformFor(Lang.getMarkdownString(this, R.string.EraseDatabaseWarn), Lang.getString(R.string.EraseConfirm), accounts -> {
          showWarning(Lang.getMarkdownString(this, R.string.EraseDatabaseWarn2), success -> {
            if (success && !isDestroyed() && isFocused() && navigationController() != null) {
              UI.showToast(R.string.EraseDatabaseProgress, Toast.LENGTH_SHORT);
              navigationController().getStack().setIsLocked(true);

              isErasingData = true;
              AtomicBoolean error = new AtomicBoolean();
              accounts.remove(accounts.size() - 1).tdlib().eraseTdlibDatabase(new RunnableBool() {
                @Override
                public void runWithBool (boolean arg) {
                  if (!arg) {
                    error.set(true);
                  }
                  if (!accounts.isEmpty()) {
                    accounts.remove(accounts.size() - 1).tdlib().eraseTdlibDatabase(this);
                    return;
                  }
                  isErasingData = false;
                  if (!isDestroyed() && navigationController() != null) {
                    navigationController().getStack().setIsLocked(false);
                    if (!error.get()) {
                      ((MainActivity) context).proceedFromRecovery();
                      UI.showToast(R.string.EraseDatabaseDone, Toast.LENGTH_SHORT);
                    } else {
                      UI.showToast(R.string.EraseDatabaseError, Toast.LENGTH_SHORT);
                    }
                  }
                }
              });
            }
          });
        });
      }
    } else if (viewId == R.id.btn_showError) {
      TextController c = new TextController(context, tdlib);
      c.setArguments(TextController.Arguments.fromRawText(getCrashName(), crash.message, "text/plain"));
      navigateTo(c);
    } else if (viewId == R.id.btn_switchRtl) {
      Settings.instance().setNeedRtl(Lang.packId(), adapter.toggleView(v));
    } else if (viewId == R.id.btn_experiment) {
      ListItem item = (ListItem) v.getTag();
      if (Settings.instance().setExperimentEnabled(item.getLongValue(), adapter.toggleView(v))) {
        scheduleActivityRestart();
      }
    } else if (viewId == R.id.btn_secret_pushToken) {
      if (tdlib.context().getTokenState() == TdlibManager.TokenState.OK) {
        UI.copyText(toHumanRepresentation(tdlib.context().getToken()), R.string.CopiedText);
      }
    } else if (viewId == R.id.btn_secret_pushConfig) {
      String configuration = TdlibNotificationUtils.getTokenRetriever().getConfiguration();
      if (!StringUtils.isEmpty(configuration)) {
        UI.copyText(configuration, R.string.CopiedText);
      }
    } else if (viewId == R.id.btn_secret_appFingerprint) {
      UI.copyText(U.getApkFingerprint("SHA1"), R.string.CopiedText);
    } else if (viewId == R.id.btn_secret_pushStats) {
      UI.copyText(Settings.instance().getPushMessageStats(), R.string.CopiedText);
    } else if (viewId == R.id.btn_secret_tgcalls || viewId == R.id.btn_secret_tgcallsOptions) {
      SettingsWrapBuilder builder = new SettingsWrapBuilder(viewId);
      List<ListItem> items = new ArrayList<>();

      if (viewId == R.id.btn_secret_tgcalls) {
        String[] versions = VoIP.getAvailableVersions(false);
        Arrays.sort(versions, (a, b) -> {
          VoIP.Version aVersion = new VoIP.Version(a);
          VoIP.Version bVersion = new VoIP.Version(b);
          return bVersion.compareTo(aVersion);
        });
        for (String version : versions) {
          items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, viewId, 0, version, !VoIP.isForceDisabled(version)).setStringValue(version));
        }
        builder.addHeaderItem("Disabling all tgcalls versions enables libtgvoip " + VoIPController.getVersion() + " without tgcalls wrapper.");
      } else {
        int index = 0;
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, viewId, 0, "Acoustic Echo Cancellation", !VoIP.needDisableAcousticEchoCancellation()).setIntValue(index++));
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, viewId, 0, "Noise Suppression", !VoIP.needDisableNoiseSuppressor()).setIntValue(index++));
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, viewId, 0, "Automatic Gain Control", !VoIP.needDisableAutomaticGainControl()).setIntValue(index++));
      }

      builder.setRawItems(items);
      builder.setDisableToggles(true);
      builder.setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
        if (item.getViewType() == ListItem.TYPE_CHECKBOX_OPTION && item.getId() == viewId) {
          final boolean isSelect = settingsAdapter.toggleView(view);
          item.setSelected(isSelect);
        }
      });
      builder.setOnActionButtonClick((wrap, view, isCancel) -> {
        if (isCancel) {
          return false;
        }

        for (ListItem item : wrap.adapter.getItems()) {
          if (item.getViewType() == ListItem.TYPE_CHECKBOX_OPTION && item.getId() == viewId) {
            boolean isEnabled = item.isSelected();
            if (viewId == R.id.btn_secret_tgcalls) {
              String version = item.getStringValue();
              VoIP.setForceDisableVersion(version, !isEnabled);
            } else if (viewId == R.id.btn_secret_tgcallsOptions) {
              switch (item.getIntValue()) {
                case 0: VoIP.setForceDisableAcousticEchoCancellation(!isEnabled); break;
                case 1: VoIP.setForceDisableNoiseSuppressor(!isEnabled); break;
                case 2: VoIP.setForceDisableAutomaticGainControl(!isEnabled); break;
              }
            }
          }
        }

        return false;
      });
      builder.setSaveStr(R.string.Save);

      showSettings(builder);
    } else if (viewId == R.id.btn_debugSwitchRtl) {
      context.addRemoveRtlSwitch();
    } else if (viewId == R.id.btn_secret_replacePhoneNumber) {
      Settings.instance().setHidePhoneNumber(adapter.toggleView(v));
    } else if (viewId == R.id.btn_secret_disableQrProcess) {
      Settings.instance().setDisableQrProcessing(adapter.toggleView(v));
    } else if (viewId == R.id.btn_secret_forceQrZxing) {
      Settings.instance().setForceZxingQrProcessing(adapter.toggleView(v));
    } else if (viewId == R.id.btn_secret_debugQrRegions) {
      Settings.instance().setShowQrRegions(adapter.toggleView(v));
    } else if (viewId == R.id.btn_secret_qrTest) {
      openInAppCamera(new CameraOpenOptions().ignoreAnchor(true).noTrace(true).allowSystem(false).optionalMicrophone(true).qrModeDebug(true).mode(CameraController.MODE_QR).qrCodeListener((qrCode) -> UI.showToast(qrCode, Toast.LENGTH_LONG)));
    } else if (viewId == R.id.btn_secret_disableNetwork) {
      Settings.instance().setDisableNetwork(adapter.toggleView(v));
      TdlibManager.instance().watchDog().letsHelpDoge();
    } else if (viewId == R.id.btn_secret_forceTdlibRestarts) {
      TdlibManager.instance().setForceTdlibRestarts(adapter.toggleView(v));
    } else if (viewId == R.id.btn_secret_forceTcpInCalls) {
      Settings.instance().setForceTcpInCalls(adapter.toggleView(v));
    } else if (viewId == R.id.btn_test_database) {
      runTest(TEST_DATABASE, true);
    } else if (viewId == R.id.btn_test_recovery) {
      Crash.Builder crash = (Crash.Builder) ((ListItem) v.getTag()).getData();
      Settings.instance().storeTestCrash(crash);
      System.exit(0);
    } else if (viewId == R.id.btn_test_recovery_tdlib) {
      String text = ((ListItem) v.getTag()).getStringValue();
      if (StringUtils.isEmpty(text)) {
        text = "[ 0][t 7][1663524892.910522937][StickersManager.cpp:327][#3][!Td]  Check `Unreacheable` failed";
      }
      Settings.instance().storeTestCrash(new Crash.Builder(tdlib.id(), text)
        .flags(Crash.Flags.SOURCE_TDLIB)
      );
      System.exit(0);
    } else if (viewId == R.id.btn_test_crash1) {
      Tracer.test1("[SUCCESS] INDIRECT " + MathUtils.random(0, 10000));
    } else if (viewId == R.id.btn_test_crash2) {
      Tracer.test2("[SUCCESS] DIRECT " + -MathUtils.random(0, 10000));
    } else if (viewId == R.id.btn_test_crash3) {
      Tracer.test3("[SUCCESS] INDIRECT NATIVE " + MathUtils.random(0, 10000));
    } else if (viewId == R.id.btn_test_crash4) {
      Tracer.test4("[SUCCESS] DIRECT NATIVE " + -MathUtils.random(0, 10000));
    } else if (viewId == R.id.btn_test_crashDirectNative) {
      Tracer.test5("[SUCCESS] DIRECT THROW " + -MathUtils.random(0, 10000));
    } else if (viewId == R.id.btn_test_crashDirect) {
      throw new RuntimeException("This is a default test");
    } else if (viewId == R.id.btn_secret_dropHidden) {
      tdlib.notifications().onDropNotificationData(false);
      // case R.id.btn_ton:
    } else if (viewId == R.id.btn_tdlib) {
      openTdlibLogs(testerLevel, crash);
    } else if (viewId == R.id.btn_pushService) {
      SettingsBugController c = new SettingsBugController(context, tdlib);
      c.setArguments(new Args(Section.PUSH, crash).setTesterLevel(testerLevel));
      navigateTo(c);
    } else if (viewId == R.id.btn_appLogs) {
      SettingsBugController c = new SettingsBugController(context, tdlib);
      c.setArguments(new Args(Section.MAIN, crash).setTesterLevel(testerLevel));
      navigateTo(c);
    } else if (viewId == R.id.btn_testingUtils) {
      RunnableBool callback = proceed -> {
        if (proceed) {
          SettingsBugController c = new SettingsBugController(context, tdlib);
          c.setArguments(new Args(Section.UTILITIES, crash).setTesterLevel(testerLevel));
          navigateTo(c);
        }
      };
      if (crash != null) {
        callback.runWithBool(true);
      } else {
        showWarning(Lang.getMarkdownString(this, R.string.TestModeWarn), callback);
      }
    } else if (viewId == R.id.btn_secret_resetTutorials) {
      Settings.instance().resetTutorials();
      UI.showToast("Hints reset completed", Toast.LENGTH_SHORT);
    } else if (viewId == R.id.btn_log_verbosity) {
      ListItem[] items = new ListItem[Log.LEVEL_VERBOSE + 1];
      final int logLevel = Log.isCapturing() ? Log.LEVEL_VERBOSE : Log.getLogLevel();
      for (int level = 0; level < items.length; level++) {
        items[level] = new ListItem(ListItem.TYPE_RADIO_OPTION, level + 1, 0, getLogVerbosity(level), R.id.btn_log_verbosity, level == logLevel);
      }
      showSettings(R.id.btn_log_verbosity, items, this, false);
    } else if (viewId == R.id.btn_log_files) {
      SettingsLogFilesController c = new SettingsLogFilesController(context, tdlib);
      c.setArguments(new SettingsLogFilesController.Arguments(logFiles));
      navigateTo(c);
    } else if (viewId == R.id.btn_log_android) {
      Log.setSetting(Log.SETTING_ANDROID_LOG, ((SettingView) v).getToggler().toggle(true));
    } else if (viewId == R.id.btn_log_tags) {
      ListItem[] items = new ListItem[Log.TAGS.length];
      for (int i = 0; i < items.length; i++) {
        int tag = Log.TAGS[i];
        items[i] = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, tag, 0, "[" + Log.getLogTag(tag) + "]: " + Log.getLogTagDescription(tag), Log.isEnabled(tag));
      }
      showSettings(R.id.btn_log_tags, items, this, true);
    } else if (viewId == R.id.btn_secret_dontReadMessages) {
      int i = adapter.indexOfViewById(R.id.btn_secret_dontReadMessages);
      if (i != -1) {
        boolean newValue = !Settings.instance().dontReadMessages();
        Settings.instance().setDontReadMessages(newValue);
        if (newValue != Settings.instance().dontReadMessages()) {
          UI.showToast("You can't enable that", Toast.LENGTH_SHORT);
        } else {
          adapter.updateValuedSettingById(R.id.btn_secret_dontReadMessages);
        }
      }
    } else if (viewId == R.id.btn_secret_stressTest) {
      openInputAlert("Stress test", "Restart count", R.string.Done, R.string.Cancel, "50", new InputAlertCallback() {
        @Override
        public boolean onAcceptInput (MaterialEditTextGroup inputView, String result) {
          if (!StringUtils.isNumeric(result))
            return false;
          int count = StringUtils.parseInt(result);
          if (count <= 0)
            return false;
          if (!isDestroyed()) {
            if (navigationController != null)
              navigationController.getStack().destroyAllExceptLast();
            tdlib.stressTest(count);
            return true;
          }
          return false;
        }
      }, true);
    } else if (viewId == R.id.btn_secret_copyLanguageCodes) {
      tdlib.client().send(new TdApi.GetLocalizationTargetInfo(false), result -> {
        if (result instanceof TdApi.LocalizationTargetInfo) {
          TdApi.LocalizationTargetInfo info = (TdApi.LocalizationTargetInfo) result;
          StringBuilder codes = new StringBuilder();
          for (TdApi.LanguagePackInfo languagePackInfo : info.languagePacks) {
            if (!languagePackInfo.isBeta && languagePackInfo.isOfficial) {
              if (codes.length() > 0)
                codes.append(", ");
              codes.append("'");
              codes.append(languagePackInfo.id);
              codes.append("'");
            }
          }
          UI.copyText(codes.toString(), R.string.CopiedText);
        }
      });
    } else if (viewId == R.id.btn_secret_deleteContacts) {
      tdlib.contacts().reset(true, () -> {
        UI.showToast("Contacts reset done", Toast.LENGTH_SHORT);
        tdlib.ui().post(() -> tdlib.contacts().startSyncIfNeeded(context(), false, null));
      });
    } else if (viewId == R.id.btn_secret_resetLocalNotificationSettings) {
      tdlib.notifications().resetNotificationSettings(true);
    } else if (viewId == R.id.btn_secret_databaseStats) {
      String stats = Settings.instance().pmc().getProperty("leveldb.stats") + "\n\n" + "Memory usage: " + Settings.instance().pmc().getProperty("leveldb.approximate-memory-usage");
      TextController c = new TextController(context, tdlib);
      c.setArguments(TextController.Arguments.fromRawText("App Database Stats", stats, "text/plain"));
      navigateTo(c);
    } else if (viewId == R.id.btn_secret_tdlibDatabaseStats) {
      UI.showToast("Calculating. Please wait...", Toast.LENGTH_SHORT);
      tdlib.client().send(new TdApi.GetDatabaseStatistics(), result -> {
        switch (result.getConstructor()) {
          case TdApi.DatabaseStatistics.CONSTRUCTOR:
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                TextController c = new TextController(context, tdlib);
                c.setArguments(TextController.Arguments.fromRawText("TDLib Database Stats", ((TdApi.DatabaseStatistics) result).statistics, "text/plain"));
                navigateTo(c);
              }
            });
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(result);
            break;
        }
      });
    } else if (viewId == R.id.btn_secret_deleteProfilePhoto) {
      TdApi.User user = tdlib.myUser();
      if (user != null && user.profilePhoto != null) {
        tdlib.client().send(new TdApi.DeleteFile(user.profilePhoto.small.id), tdlib.okHandler());
        tdlib.client().send(new TdApi.DeleteFile(user.profilePhoto.big.id), tdlib.okHandler());
      }
    } else if (viewId == R.id.btn_secret_dropSavedScrollPositions) {
      Settings.instance().removeScrollPositions(tdlib.accountId(), null);
    } else if (viewId == R.id.btn_secret_sendAllChangeLogs) {
      tdlib.checkChangeLogs(false, true);
    } else if (viewId == R.id.btn_secret_readAllChats) {
      showConfirm(Lang.getString(R.string.ReadAllChatsInfo), null, () ->
        tdlib.readAllChats(ChatPosition.CHAT_LIST_MAIN, readCount ->
          tdlib.readAllChats(ChatPosition.CHAT_LIST_ARCHIVE, archiveReadCount ->
            UI.showToast(Lang.plural(R.string.ReadAllChatsDone, readCount + archiveReadCount), Toast.LENGTH_SHORT)
          )
        )
      );
    } else if (viewId == R.id.btn_tdlib_verbosity) {
      showTdlibVerbositySettings((String) ((ListItem) v.getTag()).getData());
    } else if (viewId == R.id.btn_tdlib_resetLogSettings) {
      Settings.instance().getLogSettings().reset();
      adapter.updateAllValuedSettings();
      UI.showToast("Done. Restart is required for some changes to apply.", Toast.LENGTH_SHORT);
    } else if (viewId == R.id.btn_tdlib_logSize) {
      openInputAlert("Maximum Log Size", "Amount of bytes", R.string.Done, R.string.Cancel, String.valueOf(Settings.instance().getLogSettings().getLogMaxFileSize()), (view, value) -> {
        if (!StringUtils.isNumeric(value)) {
          return false;
        }
        long result = StringUtils.parseLong(value);
        if (result < ByteUnit.KIB.toBytes(1))
          return false;
        Settings.instance().getLogSettings().setMaxFileSize(result);
        adapter.updateValuedSettingById(R.id.btn_tdlib_logSize);
        return true;
      }, true);
    } else if (viewId == R.id.btn_tdlib_viewLogs) {
      viewTdlibLog(v, false);
    } else if (viewId == R.id.btn_tdlib_viewLogsOld) {
      viewTdlibLog(v, true);
    } else if (viewId == R.id.btn_tdlib_androidLogs) {
      Settings.instance().getLogSettings().setNeedAndroidLog(adapter.toggleView(v));
    }
  }

  @SuppressLint("ResourceType")
  private void showTdlibVerbositySettings (@Nullable String module) {
    List<ListItem> items = new ArrayList<>(7);
    Settings.TdlibLogSettings settings = Settings.instance().getLogSettings();
    int currentVerbosity = settings.getVerbosity(module);
    int defaultVerbosity = module != null ? settings.getDefaultVerbosity(module) : Settings.TDLIB_LOG_VERBOSITY_UNKNOWN;
    int maxVerbosity = 7;
    if (module != null && (defaultVerbosity <= 0 || defaultVerbosity >= maxVerbosity - 1)) {
      int id = defaultVerbosity + 1;
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, getLogVerbosity(defaultVerbosity) + " (Default)", R.id.btn_tdlib_verbosity, defaultVerbosity == currentVerbosity));
    }
    for (int verbosity = module != null ? 1 : 0; verbosity < 7; verbosity++) {
      boolean isMore = verbosity == 6;
      String name;
      if (isMore) {
        name = "MORE";
      } else {
        name = getLogVerbosity(verbosity);
      }
      if (module != null && verbosity == defaultVerbosity) {
        name = name + " (Default)";
      }
      int id = verbosity + 1;
      items.add(new ListItem(isMore ? ListItem.TYPE_SETTING : ListItem.TYPE_RADIO_OPTION, id, 0, name, R.id.btn_tdlib_verbosity, !isMore && verbosity == currentVerbosity));
    }
    ListItem[] array = new ListItem[items.size()];
    items.toArray(array);
    SettingsWrap[] wrap = new SettingsWrap[1];
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_tdlib_verbosity)
      .setRawItems(array)
      .setIntDelegate((id, result) -> {
      int verbosity = result.get(R.id.btn_tdlib_verbosity, 1) - 1;
      settings.setVerbosity(module, verbosity);
      if (StringUtils.isEmpty(module)) {
        adapter.updateAllValuedSettingsById(R.id.btn_tdlib_verbosity);
      } else {
        adapter.updateValuedSettingByData(module);
      }
    })
      .setAllowResize(false);
    b.setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
      //noinspection ResourceType
      if (item.getId() == 7 && wrap[0] != null && wrap[0].window != null && !wrap[0].window.isWindowHidden()) {
        wrap[0].window.hideWindow(true);
        UI.post(() -> openInputAlert(module != null ? module : "Verbosity Level", "Integer "  + (module != null ? 1 : 0) + ".." + Integer.MAX_VALUE, R.string.Save, R.string.Cancel, Integer.toString(currentVerbosity != Settings.TDLIB_LOG_VERBOSITY_UNKNOWN ? currentVerbosity : 0), (inputView, result) -> {
          if (!StringUtils.isNumeric(result)) {
            return false;
          }
          int verbosity = StringUtils.parseInt(result, Settings.TDLIB_LOG_VERBOSITY_UNKNOWN);
          if (verbosity < 0)
            return false;
          if (module != null && verbosity < 1)
            return false;
          settings.setVerbosity(module, verbosity);
          if (StringUtils.isEmpty(module)) {
            adapter.updateValuedSettingById(R.id.btn_tdlib_verbosity);
          } else {
            adapter.updateValuedSettingByData(module);
          }
          return true;
        }, true), 200);
      }
    });
    wrap[0] = showSettings(b);
  }

  private void viewTdlibLog (View view, final boolean old) {
    File tdlibLogFile = TdlibManager.getLogFile(old);
    if (tdlibLogFile == null || !tdlibLogFile.exists() || !tdlibLogFile.isFile()) {
      UI.showToast("Log does not exists", Toast.LENGTH_SHORT);
      return;
    }
    setLogSize(tdlibLogFile.length(), old);
    int i = old ? 1 : 0;
    if (logSize[i] == 0) {
      UI.showToast("Log is empty", Toast.LENGTH_SHORT);
      return;
    }

    final int size = 4;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    IntList colors = new IntList(size);
    StringList strings = new StringList(size);

    ids.append(R.id.btn_tdlib_viewLogs);
    icons.append(R.drawable.baseline_visibility_24);
    colors.append(OptionColor.NORMAL);
    strings.append(R.string.Open);

    ids.append(R.id.btn_tdlib_shareLogs);
    icons.append(tdlib == null || tdlib.context().inRecoveryMode() ? R.drawable.baseline_share_24 : R.drawable.baseline_forward_24);
    colors.append(OptionColor.NORMAL);
    strings.append(R.string.Share);

    ids.append(R.id.btn_saveFile);
    icons.append(R.drawable.baseline_file_download_24);
    colors.append(OptionColor.NORMAL);
    strings.append(R.string.SaveToDownloads);

    ids.append(R.id.btn_tdlib_clearLogs);
    icons.append(R.drawable.baseline_delete_24);
    colors.append(OptionColor.RED);
    strings.append(R.string.Delete);

    showOptions(tdlibLogFile.getName() + " (" + Strings.buildSize(logSize[i]) + ")", ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      if (id == R.id.btn_tdlib_viewLogs) {
        TextController textController = new TextController(context, tdlib);
        textController.setArguments(TextController.Arguments.fromFile("TDLib Log", tdlibLogFile.getPath(), "text/plain"));
        navigateTo(textController);
      } else if (id == R.id.btn_saveFile) {
        TD.saveToDownloads(tdlibLogFile, "text/plain");
      } else if (id == R.id.btn_tdlib_shareLogs) {
        int verbosity = Settings.instance().getTdlibLogSettings().getVerbosity(null);
        if (verbosity == 0) {
          TdlibUi.sendTdlibLogs(SettingsBugController.this, old, tdlib == null || tdlib.context().inRecoveryMode());
        } else {
          context().tooltipManager().builder(view).show(this, tdlib, R.drawable.baseline_warning_24, Lang.getMarkdownString(this, R.string.DebugShareError));
        }
      } else if (id == R.id.btn_tdlib_clearLogs) {
        TdlibUi.clearLogs(old, arg1 -> setLogSize(arg1, old));
      }
      return true;
    });
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_log_files) {
      if (filesLoaded) {
        if (logFiles == null || logFiles.isEmpty()) {
          setLogFiles(Log.getLogFiles());
        }
        if (logFiles != null && !logFiles.isEmpty()) {
          showOptions("Clear " + Strings.buildSize(logFiles.totalSize) + "?", new int[] {R.id.btn_deleteAll, R.id.btn_cancel}, new String[] {"Delete all logs", "Cancel"}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteAll) {
              deleteAllFiles();
            }
            return true;
          });
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    if (id == R.id.btn_log_verbosity) {
      int level = result.get(R.id.btn_log_verbosity, Log.LEVEL_ERROR) - 1;
      Log.setLogLevel(level);
      adapter.updateValuedSettingById(R.id.btn_log_verbosity);
    } else if (id == R.id.btn_log_tags) {
      long tags = 0;
      final int count = result.size();
      for (int i = 0; i < count; i++) {
        tags |= result.keyAt(i);
      }
      Log.setEnabledTags(tags);
      adapter.updateValuedSettingById(R.id.btn_log_tags);
    }
  }
}
