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
 * File created on 14/01/2017
 */
package org.thunderdog.challegram.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.helper.LocationHelper;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.DrawableModifier;
import org.thunderdog.challegram.util.EmojiModifier;
import org.thunderdog.challegram.util.ReactionModifier;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.RadioView;
import org.thunderdog.challegram.widget.SliderWrapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class SettingsThemeController extends RecyclerViewController<SettingsThemeController.Args> implements View.OnClickListener, ViewController.SettingsIntDelegate, SliderWrapView.RealTimeChangeListener, View.OnLongClickListener, TGLegacyManager.EmojiLoadListener, AppUpdater.Listener {
  public SettingsThemeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_themeSettings;
  }

  public static final int MODE_THEMES = 0;
  public static final int MODE_INTERFACE_OPTIONS = 1;
  public static final int MODE_CAMERA_SETTINGS = 2;

  public static class Args {
    private final int mode;

    public Args (int mode) {
      this.mode = mode;
    }
  }

  private int mode = MODE_THEMES;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(mode == MODE_CAMERA_SETTINGS ? R.string.CameraSettings : mode == MODE_INTERFACE_OPTIONS ? R.string.Tweaks : R.string.Themes);
  }

  private SettingsAdapter adapter;

  private int currentNightMode = Settings.NIGHT_MODE_NONE;
  private int currentNightPercentage;
  private boolean lightSensorAvailable;
  private float maxSensorValue;
  private LocationHelper locationHelper;

  private List<ThemeInfo> builtinThemes;
  private List<ThemeInfo> installedThemes;
  private List<ThemeInfo> myThemes;

  private final Comparator<ThemeInfo> themeComparator = (a, b) -> a.isInstalled() != b.isInstalled() ? (a.isInstalled() ? -1 : 1) : Integer.compare(a.getId(), b.getId());

  @Override
  public void destroy () {
    super.destroy();
    cancelLocationRequest();
    TGLegacyManager.instance().removeEmojiListener(this);
    context().appUpdater().removeListener(this);
  }

  private void cancelLocationRequest () {
    if (locationHelper != null) {
      locationHelper.cancel();
      locationHelper = null;
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        v.setDrawModifier(item.getDrawModifier());
        switch (item.getId()) {
          case R.id.btn_theme: {
            v.setName(item.getString()); // FIXME in {@link SettingsAdapter#updateValuedItemByPosition}?
            RadioView view = v.findRadioView();
            if (view != null)
              view.setChecked(item.isSelected(), isUpdate);
            break;
          }
          case R.id.btn_emoji: {
            Settings.EmojiPack emojiPack = Settings.instance().getEmojiPack();
            if (emojiPack.identifier.equals(BuildConfig.EMOJI_BUILTIN_ID)) {
              v.setData(R.string.EmojiBuiltIn);
            } else {
              v.setData(emojiPack.displayName);
            }
            break;
          }
          case R.id.btn_quick_reaction: {
            final String[] reactions = Settings.instance().getQuickReactions(tdlib);
            tdlib.ensureReactionsAvailable(reactions, reactionsUpdated -> {
              if (reactionsUpdated) {
                runOnUiThreadOptional(() -> {
                  updateQuickReaction();
                });
              }
            });
            StringBuilder stringBuilder = new StringBuilder();
            if (reactions.length > 0) {
              final List<TGReaction> tgReactions = new ArrayList<>(reactions.length);
              for (String reactionKey : reactions) {
                TdApi.ReactionType reactionType = TD.toReactionType(reactionKey);
                final TGReaction tgReaction = tdlib.getReaction(reactionType, false);
                if (tgReaction != null) {
                  tgReactions.add(tgReaction);
                  if (stringBuilder.length() > 0) {
                    stringBuilder.append(Lang.getConcatSeparator());
                  }
                  stringBuilder.append(tgReaction.getTitle());
                }
              }
              v.setDrawModifier(new ReactionModifier(v.getComplexReceiver(), tgReactions.toArray(new TGReaction[0])));
              v.setData(stringBuilder.toString());
            } else {
              v.setDrawModifier(null);
              v.setData(R.string.QuickReactionDisabled);
            }
            break;
          }
          case R.id.btn_icon: {
            v.setData(R.string.IconsBuiltIn);
            break;
          }
          case R.id.btn_reduceMotion: {
            v.getToggler().setRadioEnabled(Settings.instance().needReduceMotion(), isUpdate);
            break;
          }
          case R.id.btn_autoplayGIFs: {
            v.getToggler().setRadioEnabled(Settings.instance().needAutoplayGIFs(), isUpdate);
            break;
          }
          case R.id.btn_saveToGallery: {
            v.getToggler().setRadioEnabled(Settings.instance().needSaveEditedMediaToGallery(), isUpdate);
            break;
          }
          case R.id.btn_mosaic: {
            v.getToggler().setRadioEnabled(Settings.instance().rememberAlbumSetting(), isUpdate);
            break;
          }
          case R.id.btn_cameraSetting: {
            v.getToggler().setRadioEnabled(Settings.instance().getNewSetting(item.getLongId()) != item.getBoolValue(), isUpdate);
            break;
          }
          case R.id.btn_cameraRatio: {
            int ratioMode = Settings.instance().getCameraAspectRatioMode();
            switch (ratioMode) {
              case Settings.CAMERA_RATIO_1_1:
                v.setData("1:1");
                break;
              case Settings.CAMERA_RATIO_4_3:
                v.setData("4:3");
                break;
              case Settings.CAMERA_RATIO_FULL_SCREEN:
                v.setData(R.string.CameraRatioFull);
                break;
              case Settings.CAMERA_RATIO_16_9:
              default:
                v.setData("16:9");
                break;
            }
            break;
          }
          case R.id.btn_cameraVolume: {
            switch (Settings.instance().getCameraVolumeControl()) {
              case Settings.CAMERA_VOLUME_CONTROL_SHOOT:
                v.setData(R.string.CameraVolumeShoot);
                break;
              case Settings.CAMERA_VOLUME_CONTROL_ZOOM:
                v.setData(R.string.CameraVolumeZoom);
                break;
              case Settings.CAMERA_VOLUME_CONTROL_NONE:
                v.setData(R.string.CameraVolumeNone);
                break;
            }
            break;
          }
          case R.id.btn_cameraType: {
            if (Config.CAMERA_X_AVAILABLE) {
              int type = Settings.instance().getCameraType();
              switch (type) {
                case Settings.CAMERA_TYPE_LEGACY:
                  v.setData(R.string.CameraTypeLegacy);
                  break;
                case Settings.CAMERA_TYPE_SYSTEM:
                  v.setData(R.string.CameraTypeSystem);
                  break;
                case Settings.CAMERA_TYPE_X:
                  v.setData(R.string.CameraTypeXBeta);
                  break;
              }
            } else {
              v.getToggler().setRadioEnabled(Settings.instance().getCameraType() == Settings.CAMERA_TYPE_SYSTEM, isUpdate);
            }
            break;
          }
          case R.id.btn_systemFonts: {
            v.getToggler().setRadioEnabled(Settings.instance().useSystemFonts(), isUpdate);
            break;
          }
          case R.id.btn_secret_batmanTransitions: {
            v.getToggler().setRadioEnabled(Settings.instance().getNewSetting(Settings.SETTING_FLAG_BATMAN_POLL_TRANSITIONS), isUpdate);
            break;
          }
          case R.id.btn_chatListStyle: {
            switch (Settings.instance().getChatListMode()) {
              case Settings.CHAT_MODE_3LINE_BIG:
                v.setData(R.string.ChatListStyle3);
                break;
              case Settings.CHAT_MODE_3LINE:
                v.setData(R.string.ChatListStyle2);
                break;
              case Settings.CHAT_MODE_2LINE:
              default:
                v.setData(R.string.ChatListStyle1);
                break;
            }
            break;
          }
          case R.id.btn_stickerSuggestions: {
            switch (Settings.instance().getStickerMode()) {
              case Settings.STICKER_MODE_ALL:
                v.setData(R.string.SuggestStickersAll);
                break;
              case Settings.STICKER_MODE_ONLY_INSTALLED:
                v.setData(R.string.SuggestStickersInstalled);
                break;
              case Settings.STICKER_MODE_NONE:
                v.setData(R.string.SuggestStickersNone);
                break;
            }
            break;
          }
          case R.id.btn_autoNightModeScheduled_location: {
            if (isUpdate) {
              v.setEnabledAnimated(locationHelper == null);
            } else {
              v.setEnabled(locationHelper == null);
            }
            v.setName(locationHelper == null ? R.string.AutoNightModeScheduledByLocation : R.string.AutoNightModeScheduledByLocationProgress);
            v.invalidate();
            break;
          }
          case R.id.btn_earpieceMode:
          case R.id.btn_earpieceModeVideo: {
            switch (Settings.instance().getEarpieceMode(item.getId() == R.id.btn_earpieceModeVideo)) {
              case Settings.EARPIECE_MODE_ALWAYS:
                v.setData(R.string.EarpieceModeAlways);
                break;
              case Settings.EARPIECE_MODE_PROXIMITY:
                v.setData(R.string.EarpieceModeProximity);
                break;
              case Settings.EARPIECE_MODE_NEVER:
                v.setData(R.string.EarpieceModeNever);
                break;
            }
            break;
          }
          case R.id.btn_separateMedia:
            v.getToggler().setRadioEnabled(Settings.instance().needSeparateMediaTab(), isUpdate);
            break;
          case R.id.btn_restrictSensitiveContent:
            v.getToggler().setRadioEnabled(tdlib.ignoreSensitiveContentRestrictions(), isUpdate);
            break;
          case R.id.btn_ignoreContentRestrictions:
            v.getToggler().setRadioEnabled(!Settings.instance().needRestrictContent(), isUpdate);
            break;
          case R.id.btn_useBigEmoji:
            v.getToggler().setRadioEnabled(Settings.instance().useBigEmoji(), isUpdate);
            break;
          case R.id.btn_markdown: {
            v.getToggler().setRadioEnabled(Settings.instance().getNewSetting(Settings.SETTING_FLAG_EDIT_MARKDOWN), isUpdate);
            break;
          }
          case R.id.btn_forceExoPlayerExtensions: {
            v.getToggler().setRadioEnabled(Settings.instance().getNewSetting(Settings.SETTING_FLAG_FORCE_EXO_PLAYER_EXTENSIONS), isUpdate);
            break;
          }
          case R.id.btn_audioCompression: {
            v.getToggler().setRadioEnabled(!Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_AUDIO_COMPRESSION), isUpdate);
            break;
          }
          case R.id.btn_sizeUnit: {
            v.setData(Settings.instance().getNewSetting(Settings.SETTING_FLAG_USE_METRIC_FILE_SIZE_UNITS) ? R.string.SizeUnitMetric : R.string.SizeUnitBinary);
            break;
          }
          case R.id.btn_instantViewMode: {
            switch (Settings.instance().getInstantViewMode()) {
              case Settings.INSTANT_VIEW_MODE_ALL:
                v.setData(R.string.AutoInstantViewAll);
                break;
              case Settings.INSTANT_VIEW_MODE_INTERNAL:
                v.setData(R.string.AutoInstantViewTelegram);
                break;
              case Settings.INSTANT_VIEW_MODE_NONE:
                v.setData(R.string.AutoInstantViewNone);
                break;
            }
            break;
          }
          case R.id.btn_hqRounds: {
            v.getToggler().setRadioEnabled(Settings.instance().needHqRoundVideos(), isUpdate);
            break;
          }
          case R.id.btn_rearRounds: {
            v.getToggler().setRadioEnabled(Settings.instance().startRoundWithRear(), isUpdate);
            break;
          }
          case R.id.btn_autoNightModeScheduled_timeOff:
          case R.id.btn_autoNightModeScheduled_timeOn: {
            int time = v.getId() == R.id.btn_autoNightModeScheduled_timeOn ? Settings.instance().getNightModeScheduleOn() : Settings.instance().getNightModeScheduleOff();
            v.setData(U.timeToString(time));
            break;
          }
          case R.id.btn_big_reactions: {
            StringBuilder b = new StringBuilder();
            if (Settings.instance().getBigReactionsInChats()) {
              b.append(Lang.getString(R.string.BigReactionsChats));
            }
            if (Settings.instance().getBigReactionsInChannels()) {
              if (b.length() > 0) {
                b.append(Lang.getConcatSeparator());
              }
              b.append(Lang.getString(R.string.BigReactionsChannels));
            }
            if (b.length() == 0) {
              b.append(Lang.getString(R.string.BigReactionsNone));
            }
            v.setData(b.toString());
            break;
          }
          case R.id.btn_chatSwipes: {
            StringBuilder b = new StringBuilder();
            if (Settings.instance().needChatQuickShare()) {
              b.append(Lang.getString(R.string.QuickActionSettingShare));
            }
            if (Settings.instance().needChatQuickReply()) {
              if (b.length() > 0) {
                b.append(Lang.getConcatSeparator());
              }
              b.append(Lang.getString(R.string.QuickActionSettingReply));
            }
            if (b.length() == 0) {
              b.append(Lang.getString(R.string.QuickActionSettingNone));
            }
            v.setData(b.toString());
            break;
          }
          case R.id.btn_systemEmoji: {
            v.getToggler().setRadioEnabled(Settings.instance().useSystemEmoji(), isUpdate);
            break;
          }
          case R.id.btn_customVibrations: {
            v.getToggler().setRadioEnabled(Settings.instance().useCustomVibrations(), isUpdate);
            break;
          }
          case R.id.btn_confirmCalls: {
            v.getToggler().setRadioEnabled(Settings.instance().needOutboundCallsPrompt(), isUpdate);
            break;
          }
          case R.id.btn_hideChatKeyboard: {
            v.getToggler().setRadioEnabled(Settings.instance().needHideChatKeyboardOnScroll(), isUpdate);
            break;
          }
          case R.id.btn_useInAppBrowser: {
            v.getToggler().setRadioEnabled(Settings.instance().useInAppBrowser(), isUpdate);
            break;
          }
          case R.id.btn_switchRtl: {
            v.getToggler().setRadioEnabled(Lang.rtl(), isUpdate);
            break;
          }
          case R.id.btn_useHoldToPreview: {
            v.getToggler().setRadioEnabled(Settings.instance().needPreviewChatOnHold(), isUpdate);
            break;
          }
          case R.id.btn_sendByEnter: {
            v.getToggler().setRadioEnabled(Settings.instance().needSendByEnter(), isUpdate);
            break;
          }
          case R.id.btn_toggleNewSetting: {
            boolean value = Settings.instance().getNewSetting(item.getLongId());
            if (item.getBoolValue())
              value = !value;
            v.getToggler().setRadioEnabled(value, isUpdate);
            break;
          }
          case R.id.btn_updateAutomatically: {
            int mode = Settings.instance().getAutoUpdateMode();
            v.getToggler().setRadioEnabled(mode != Settings.AUTO_UPDATE_MODE_NEVER, isUpdate);
            switch (mode) {
              case Settings.AUTO_UPDATE_MODE_NEVER:
                v.setData(R.string.AutoUpdateNever);
                break;
              case Settings.AUTO_UPDATE_MODE_ALWAYS:
                v.setData(R.string.AutoUpdateAlways);
                break;
              case Settings.AUTO_UPDATE_MODE_WIFI_ONLY:
                v.setData(R.string.AutoUpdateWiFi);
                break;
              case Settings.AUTO_UPDATE_MODE_PROMPT:
                v.setData(R.string.AutoUpdatePrompt);
                break;
            }
            break;
          }
          case R.id.btn_checkUpdates: {
            switch (context().appUpdater().state()) {
              case AppUpdater.State.NONE: {
                v.setEnabledAnimated(true, isUpdate);
                v.setName(R.string.CheckForUpdates);
                break;
              }
              case AppUpdater.State.CHECKING: {
                v.setEnabledAnimated(false, isUpdate);
                v.setName(R.string.CheckingForUpdates);
                break;
              }
              case AppUpdater.State.AVAILABLE: {
                v.setEnabledAnimated(true, isUpdate);
                long bytesToDownload = context().appUpdater().totalBytesToDownload() - context().appUpdater().bytesDownloaded();
                if (bytesToDownload > 0) {
                  v.setName(Lang.getStringBold(R.string.DownloadUpdateSize, Strings.buildSize(bytesToDownload)));
                } else {
                  v.setName(R.string.DownloadUpdate);
                }
                break;
              }
              case AppUpdater.State.DOWNLOADING: {
                v.setEnabledAnimated(false, isUpdate);
                v.setName(Lang.getDownloadProgress(context().appUpdater().bytesDownloaded(), context().appUpdater().totalBytesToDownload(), true));
                break;
              }
              case AppUpdater.State.READY_TO_INSTALL: {
                v.setEnabledAnimated(true, isUpdate);
                v.setName(R.string.InstallUpdate);
                break;
              }
            }
            break;
          }
        }
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();

    int chatStyle = tdlib.settings().chatStyle();

    if (mode == MODE_THEMES) {
      adapter.setOnLongClickListener(this);

      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatMode));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.theme_chat_classic, 0, R.string.ChatStyleBubbles, R.id.theme_chat, chatStyle == ThemeManager.CHAT_STYLE_BUBBLES));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_forcePlainChannels, 0, R.string.ChatStyleBubblesChannel, R.id.btn_forcePlainChannels, !tdlib.settings().forcePlainModeInChannels()));

      if (!tdlib.account().isDebug()) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_emoji, 0, R.string.Emoji).setDrawModifier(new EmojiModifier(Lang.getString(R.string.EmojiPreview), Paints.emojiPaint())));
        TGLegacyManager.instance().addEmojiListener(this);

        if (BuildConfig.DEBUG) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_icon, 0, R.string.Icons).setDrawModifier(new DrawableModifier(R.drawable.baseline_star_20, R.drawable.baseline_account_balance_wallet_20, R.drawable.baseline_location_on_20, R.drawable.baseline_favorite_20)));
        }
      }
      if (!tdlib.account().isDebug() || BuildConfig.DEBUG) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_quick_reaction, 0, R.string.QuickReaction));
      }

      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatListStyle, 0, R.string.ChatListStyle));

      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatBackground, 0, R.string.Wallpaper));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatFontSize, 0, R.string.TextSize));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ColorTheme));

      this.builtinThemes = ThemeManager.instance().getBuiltinThemes();
      addThemeGroup(items, builtinThemes, false);

      List<ThemeInfo> customThemes = Settings.instance().getCustomThemes();
      Collections.sort(customThemes, themeComparator);
      this.installedThemes = new ArrayList<>();
      this.myThemes = new ArrayList<>();
      for (ThemeInfo theme : customThemes) {
        if (theme.isInstalled()) {
          installedThemes.add(theme);
        } else {
          myThemes.add(theme);
        }
      }

      addThemeGroup(items, installedThemes, false);
      addThemeGroup(items, myThemes, true);

      lightSensorAvailable = false;
      maxSensorValue = SensorManager.LIGHT_CLOUDY / 5;
      try {
        SensorManager sensorManager = (SensorManager) UI.getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
          Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
          lightSensorAvailable = sensor != null;
          if (lightSensorAvailable) {
            lightSensorAvailable = (maxSensorValue = Math.min(maxSensorValue, sensor.getMaximumRange())) > 0f;
            if (lightSensorAvailable) {
              maxSensorValue = Math.max(maxSensorValue, Settings.MAX_NIGHT_LUX_DEFAULT * 1.5f);
            }
          }
        }
      } catch (Throwable t) {
        Log.i("Cannot access light sensor", t);
      }

      currentNightMode = Settings.instance().getNightMode();
      float value = maxSensorValue != 0f ? MathUtils.clamp(Settings.instance().getMaxNightLux() / maxSensorValue) : 0f;
      currentNightPercentage = (int) (100f * value);

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AutoNightMode));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_autoNightModeNone, 0, R.string.AutoNightDisabled, R.id.btn_autoNightMode, currentNightMode == Settings.NIGHT_MODE_NONE));
      if (Settings.NIGHT_MODE_DEFAULT == Settings.NIGHT_MODE_SYSTEM || (currentNightMode == Settings.NIGHT_MODE_SYSTEM || context().hasSystemNightMode())) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_autoNightModeSystem, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? R.string.AutoNightSystemQ : R.string.AutoNightSystem, R.id.btn_autoNightMode, currentNightMode == Settings.NIGHT_MODE_SYSTEM));
      }

      if (lightSensorAvailable) {
        adapter.setSliderChangeListener(this);
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_autoNightModeAuto, 0, R.string.AutoNightAutomatic, R.id.btn_autoNightMode, currentNightMode == Settings.NIGHT_MODE_AUTO));
      }

      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_autoNightModeScheduled, 0, R.string.AutoNightScheduled, R.id.btn_autoNightMode, currentNightMode == Settings.NIGHT_MODE_SCHEDULED));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      switch (currentNightMode) {
        case Settings.NIGHT_MODE_NONE: {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_autoNightMode_description, 0, lightSensorAvailable ? R.string.AutoNightModeDescription : R.string.AutoNightModeDescriptionScheduled));
          break;
        }
        case Settings.NIGHT_MODE_SYSTEM: {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_autoNightMode_description, 0, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? R.string.AutoNightModeDescriptionSystemQ : R.string.AutoNightModeDescriptionSystem));
          break;
        }
        case Settings.NIGHT_MODE_AUTO: {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(newBrightnessItem());
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_autoNightMode_description, 0, Lang.getString(R.string.AutoNightModeDescriptionAuto, currentNightPercentage), false));
          break;
        }
        case Settings.NIGHT_MODE_SCHEDULED: {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(newScheduleItem(true));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(newScheduleItem(false));
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(newScheduleLocationItem());
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_autoNightMode_description, 0, Lang.getString(R.string.AutoNightModeDescriptionScheduled), false));
          break;
        }
      }

      RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
        @Override
        public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
          ListItem item = (ListItem) viewHolder.itemView.getTag();
          return item != null && item.getId() == R.id.btn_theme && ((ThemeInfo) item.getData()).isCustom();
        }

        @Override
        public void onRemove (RecyclerView.ViewHolder viewHolder) {
          ThemeInfo theme = (ThemeInfo) ((ListItem) viewHolder.itemView.getTag()).getData();
          tdlib.ui().showDeleteThemeConfirm(SettingsThemeController.this, theme, () -> deleteTheme(theme, true));
        }
      });
    } else if (mode == MODE_INTERFACE_OPTIONS) {
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_autoplayGIFs, 0, R.string.AutoplayGifs));
      if (Config.IN_APP_BROWSER_AVAILABLE) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useInAppBrowser, 0, R.string.UseInAppBrowser, Settings.instance().useInAppBrowser()));
      }
      if (Config.FORCE_TOUCH_ENABLED && Config.HOLD_TO_PREVIEW_AVAILABLE) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useHoldToPreview, 0, R.string.HoldToPreview, Settings.instance().needPreviewChatOnHold()));
      }
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_customVibrations, 0, R.string.CustomVibrations));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_reduceMotion, 0, R.string.ReduceMotion, Settings.instance().needReduceMotion()));
      if (Lang.rtl() || Lang.getLanguageDirection() != Lang.LANGUAGE_DIRECTION_LTR) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_switchRtl, 0, R.string.RtlLayout));
      }
      /*if (Config.ALLOW_SYSTEM_EMOJI) {
        items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
        items.add(new SettingItem(SettingItem.TYPE_RADIO_SETTING, R.id.btn_systemEmoji, 0, R.string.AndroidEmoji));
      }*/
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      if (U.isAppSideLoaded()) {
        items.addAll(Arrays.asList(
          new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InAppUpdates),
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_updateAutomatically, 0, R.string.AutoUpdate)
        ));
        if (Settings.instance().getAutoUpdateMode() != Settings.AUTO_UPDATE_MODE_NEVER) {
          items.addAll(newAutoUpdateConfigurationItems());
        }
      } else {
        items.addAll(Arrays.asList(
          new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AppUpdates),
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_SETTING, R.id.btn_checkUpdates, 0, R.string.CheckForUpdates),
          new ListItem(ListItem.TYPE_SEPARATOR_FULL),
          new ListItem(ListItem.TYPE_SETTING, R.id.btn_subscribeToBeta, 0, R.string.SubscribeToBeta)
        ));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      context().appUpdater().addListener(this);

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.AnimatedEmoji).setLongId(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI).setBoolValue(true));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_useBigEmoji, 0, R.string.BigEmoji));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_big_reactions, 0, R.string.BigReactions));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.LoopAnimatedStickers).setLongId(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP).setBoolValue(true));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_stickerSuggestions, 0, R.string.SuggestStickers));
      boolean sideLoaded = U.isAppSideLoaded();
      if (tdlib.canIgnoreSensitiveContentRestriction() && (sideLoaded || tdlib.ignoreSensitiveContentRestrictions())) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_restrictSensitiveContent, 0, R.string.DisplaySensitiveContent));
      }
      if (sideLoaded || !Settings.instance().needRestrictContent()) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_ignoreContentRestrictions, 0, R.string.IgnoreRestrictions));
      }
      if (Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.UseExplicitDice).setLongId(Settings.SETTING_FLAG_EXPLICIT_DICE));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatSwipes, 0, R.string.ChatQuickActions));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_sendByEnter, 0, R.string.SendByEnter));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideChatKeyboard, 0, R.string.HideChatKeyboard));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_saveToGallery, 0, R.string.SaveOutgoingPhotos));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_instantViewMode, 0, R.string.AutoInstantView));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.OpenEmbed).setLongId(Settings.SETTING_FLAG_NO_EMBEDS).setBoolValue(true));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.OpenEmbedDesc), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_sizeUnit, 0, R.string.SizeUnit));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_mosaic, 0, R.string.RememberAlbumSetting));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_separateMedia, 0, R.string.SeparateMediaTab));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_markdown, 0, R.string.EditMarkdown));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.EditMarkdownHint2), false));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.VoiceMessages));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_earpieceMode, 0, R.string.EarpieceMode));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.VideoMessages));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_earpieceModeVideo, 0, R.string.EarpieceMode));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_rearRounds, 0, R.string.UseRearRoundVideos));
      if (!Device.NEED_HQ_ROUND_VIDEOS && Config.ROUND_VIDEOS_RECORD_SUPPORTED) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hqRounds, 0, R.string.UseHqRoundVideos));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Calls));

      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_confirmCalls, 0, R.string.ConfirmCalls));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ConfirmCallsDesc));

      if (Config.CUSTOM_CAMERA_AVAILABLE) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Camera));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        if (Config.CAMERA_X_AVAILABLE) {
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_cameraType, 0, R.string.CameraType));
        } else {
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_cameraType, 0, R.string.CameraUseSystem));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_cameraSetting, 0, R.string.CameraKeepMedia).setLongId(Settings.SETTING_FLAG_CAMERA_KEEP_DISCARDED_MEDIA));
        boolean hasSettings = Settings.instance().getCameraType() != Settings.CAMERA_TYPE_SYSTEM;
        if (hasSettings) {
          List<ListItem> cameraSettings = getCameraSettings();
          items.addAll(cameraSettings);
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        if (hasSettings) {
          items.add(newCameraFlipInfoItem());
        }
      }

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Other));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_systemFonts, 0, R.string.UseSystemFonts));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_forceExoPlayerExtensions, 0, R.string.ForceBuiltinDecoding));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_audioCompression, 0, R.string.CompressAudio));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      tdlib.getTesterLevel(testerLevel -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          boolean needBatman = testerLevel >= Tdlib.TESTER_LEVEL_READER || Settings.instance().getNewSetting(Settings.SETTING_FLAG_BATMAN_POLL_TRANSITIONS);
          int index = adapter.indexOfViewById(R.id.btn_secret_batmanTransitions);
          boolean hasBatman = index != -1;
          if (needBatman != hasBatman) {
            if (needBatman) {
              index = adapter.indexOfViewByIdReverse(R.id.btn_systemFonts);
              if (index != -1) {
                adapter.getItems().addAll(index, Arrays.asList(
                  new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secret_batmanTransitions, 0, R.string.BatmanTransitions),
                  new ListItem(ListItem.TYPE_SEPARATOR_FULL)
                ));
                adapter.notifyItemRangeInserted(index, 2);
              }
            } else {
              adapter.removeRange(index, 2);
            }
          }
        }
      }));
    } else if (mode == MODE_CAMERA_SETTINGS) {
      // TODO
    } else {
      throw new IllegalArgumentException("mode == " + mode);
    }

    adapter.setItems(items, true);
    recyclerView.setAdapter(adapter);

    tdlib.wallpaper().getBackgrounds(null, Theme.isDark());
  }

  private static final int CAMERA_SETTING_ITEM_COUNT = 8;

  private static List<ListItem> getCameraSettings () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_cameraSetting, 0, R.string.CameraGrid).setLongId(Settings.SETTING_FLAG_CAMERA_SHOW_GRID));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_cameraVolume, 0, R.string.CameraVolume));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_cameraRatio, 0, R.string.CameraRatio));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_cameraSetting, 0, R.string.CameraFlip).setLongId(Settings.SETTING_FLAG_CAMERA_NO_FLIP).setBoolValue(true));
    return items;
  }

  @Override
  public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) {
    if (oldState == AppUpdater.State.CHECKING && state == AppUpdater.State.NONE) {
      // Slight delay
      runOnUiThread(() ->
        adapter.updateValuedSettingById(R.id.btn_checkUpdates),
        250
      );
    } else {
      adapter.updateValuedSettingById(R.id.btn_checkUpdates);
    }
  }

  @Override
  public void onAppUpdateDownloadProgress (long bytesDownloaded, long totalBytesToDownload) {
    adapter.updateValuedSettingById(R.id.btn_checkUpdates);
  }

  private static ListItem newCameraFlipInfoItem () {
    return new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CameraFlipInfo);
  }

  public void updateSelectedEmoji () {
    if (adapter != null)
      adapter.updateValuedSettingById(R.id.btn_emoji);
  }

  public void updateQuickReaction () {
    if (adapter != null)
      adapter.updateValuedSettingById(R.id.btn_quick_reaction);
  }

  public void updateSelectedIconPack () {
    if (adapter != null)
      adapter.updateValuedSettingById(R.id.btn_icon);
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    updateQuickReaction();
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (isPackSwitch) {
      updateSelectedEmoji();
    } else {
      invalidateById(R.id.btn_emoji);
    }
  }

  private void invalidateById (int id) {
    if (adapter != null) {
      int index = adapter.indexOfViewById(id);
      View view = getRecyclerView().getLayoutManager().findViewByPosition(index);
      if (view != null)
        view.invalidate();
    }
  }

  private static ListItem newItem (ThemeInfo theme) {
    int themeId = theme.getId();
    ListItem item;
    boolean isCustom = ThemeManager.isCustomTheme(themeId);
    ThemeDelegate currentTheme = ThemeManager.instance().currentTheme();
    boolean isCurrent = currentTheme.getId() == theme.getId();
    if (isCustom) {
      item = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_theme, 0, theme.getName(), false);
    } else {
      item = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_theme, 0, ThemeManager.getBuiltinThemeName(themeId));
    }
    if (isCurrent && isCustom && !theme.hasLoadedTheme()) {
      theme.setLoadedTheme((ThemeCustom) currentTheme);
    }
    item.setData(theme);
    item.setIntValue(themeId);
    item.setSelected(isCurrent);
    item.setRadioColorId(Theme.getCircleColorId(isCustom ? theme.parentThemeId() : themeId));
    return item;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putInt(keyPrefix + "mode", mode);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    this.mode = in.getInt(keyPrefix + "mode", MODE_THEMES);
    return true;
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  private void setThemeSelected (@ThemeId int themeId, boolean selected) {
    int index = findThemeCell(themeId);
    if (index != -1) {
      ListItem item = adapter.getItems().get(index);
      if (item.isSelected() != selected) {
        item.setSelected(selected);
        adapter.updateValuedSettingByPosition(index);
      }
    }
  }

  @Override
  public void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) {
    setThemeSelected(fromTheme.getId(), false);
    setThemeSelected(toTheme.getId(), true);
    ThemeInfo newTheme = findThemeInfo(toTheme);
    if (newTheme != null && currentTheme != newTheme) {
      currentTheme = newTheme;
      if (createThemeItem != null && createThemeItem.setStringIfChanged(Lang.getStringBold(R.string.ThemeCreateInfo, currentTheme.getName()))) {
        adapter.updateValuedSettingByPosition(adapter.indexOfView(createThemeItem));
      }
    }
  }

  private ListItem newBrightnessItem () {
    return new ListItem(ListItem.TYPE_SLIDER_BRIGHTNESS).setIntValue(Float.floatToIntBits(maxSensorValue)).setSliderInfo(null, Float.floatToIntBits(Settings.instance().getMaxNightLux()));
  }

  private static ListItem newScheduleItem (boolean isTurnOn) {
    if (isTurnOn) {
      return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_autoNightModeScheduled_timeOn, 0, R.string.AutoNightModeScheduledTurnOn);
    } else {
      return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_autoNightModeScheduled_timeOff, 0, R.string.AutoNightModeScheduledTurnOff);
    }
  }

  private static ListItem newScheduleLocationItem () {
    return new ListItem(ListItem.TYPE_SETTING, R.id.btn_autoNightModeScheduled_location);
  }

  private static List<ListItem> newAutoUpdateConfigurationItems () {
    return Arrays.asList(
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.InstallBetas).setLongId(Settings.SETTING_FLAG_DOWNLOAD_BETAS),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_SETTING, R.id.btn_checkUpdates, 0, R.string.CheckForUpdates)
    );
  }

  @Override
  public void onThemeAutoNightModeChanged (int autoNightMode) {
    setCurrentNightMode(autoNightMode, false);
  }

  public void setCurrentNightMode (int autoNightMode, boolean byUserRequest) {
    if (this.currentNightMode != autoNightMode) {
      int oldNightMode = this.currentNightMode;
      this.currentNightMode = autoNightMode;
      int newResultId;
      switch (autoNightMode) {
        case Settings.NIGHT_MODE_NONE:
          newResultId = R.id.btn_autoNightModeNone;
          break;
        case Settings.NIGHT_MODE_AUTO:
          newResultId = R.id.btn_autoNightModeAuto;
          break;
        case Settings.NIGHT_MODE_SCHEDULED:
          newResultId = R.id.btn_autoNightModeScheduled;
          break;
        case Settings.NIGHT_MODE_SYSTEM:
          newResultId = R.id.btn_autoNightModeSystem;
          break;
        default:
          throw new IllegalArgumentException("autoNightMode == " + autoNightMode);
      }
      adapter.setIntResult(R.id.btn_autoNightMode, newResultId);

      List<ListItem> items = adapter.getItems();
      int descriptionPosition = items.size() - 1;
      ListItem descriptionItem = items.get(descriptionPosition);
      boolean descriptionChanged = false;
      switch (autoNightMode) {
        case Settings.NIGHT_MODE_NONE:
          descriptionChanged = lightSensorAvailable || oldNightMode != Settings.NIGHT_MODE_SCHEDULED;
          if (descriptionChanged) {
            descriptionChanged = descriptionItem.setStringIfChanged(lightSensorAvailable ? R.string.AutoNightModeDescription : R.string.AutoNightModeDescriptionScheduled);
          }
          break;
        case Settings.NIGHT_MODE_SYSTEM:
          descriptionChanged = descriptionItem.setStringIfChanged(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? R.string.AutoNightModeDescriptionSystemQ : R.string.AutoNightModeDescriptionSystem);
          break;
        case Settings.NIGHT_MODE_AUTO:
          descriptionChanged = descriptionItem.setStringIfChanged(Lang.getString(R.string.AutoNightModeDescriptionAuto, currentNightPercentage));
          break;
        case Settings.NIGHT_MODE_SCHEDULED:
          descriptionChanged = lightSensorAvailable || oldNightMode != Settings.NIGHT_MODE_NONE;
          if (descriptionChanged) {
            descriptionChanged = descriptionItem.setStringIfChanged(R.string.AutoNightModeDescriptionScheduled);
          }
          break;
      }
      if (descriptionChanged) {
        adapter.notifyItemChanged(descriptionPosition);
      }
      if (autoNightMode == Settings.NIGHT_MODE_NONE || autoNightMode == Settings.NIGHT_MODE_SYSTEM) {
        int itemCount = oldNightMode == Settings.NIGHT_MODE_NONE || oldNightMode == Settings.NIGHT_MODE_SYSTEM ? 0 : oldNightMode == Settings.NIGHT_MODE_SCHEDULED ? 5 + 2 : 1 + 2;
        if (itemCount > 0) {
          adapter.removeRange(descriptionPosition - itemCount, itemCount);
        }
      } else if (oldNightMode == Settings.NIGHT_MODE_NONE || oldNightMode == Settings.NIGHT_MODE_SYSTEM) {
        int index = descriptionPosition;
        items.add(index++, new ListItem(ListItem.TYPE_SHADOW_TOP));
        switch (autoNightMode) {
          case Settings.NIGHT_MODE_AUTO:
            items.add(index++, newBrightnessItem());
            break;
          case Settings.NIGHT_MODE_SCHEDULED:
            items.add(index++, newScheduleItem(true));
            items.add(index++, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(index++, newScheduleItem(false));
            items.add(index++, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            items.add(index++, newScheduleLocationItem());
            break;
        }
        items.add(index++, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.notifyItemRangeInserted(descriptionPosition, index - descriptionPosition);
      } else if (oldNightMode == Settings.NIGHT_MODE_SCHEDULED) {
        int centerPosition = descriptionPosition - 5;
        items.set(centerPosition, newBrightnessItem());
        adapter.notifyItemChanged(centerPosition);
        adapter.removeRange(centerPosition + 1, 3);
        adapter.removeItem(centerPosition - 1);
      } else if (oldNightMode == Settings.NIGHT_MODE_AUTO) {
        int adjustPosition = descriptionPosition - 2;
        items.set(adjustPosition, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.notifyItemChanged(adjustPosition);
        items.add(adjustPosition, newScheduleItem(true));
        adapter.notifyItemInserted(adjustPosition);
        adjustPosition += 2;
        items.add(adjustPosition, newScheduleLocationItem());
        items.add(adjustPosition, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(adjustPosition, newScheduleItem(false));
        adapter.notifyItemRangeInserted(adjustPosition, 3);
      }
      if ((autoNightMode != Settings.NIGHT_MODE_NONE && autoNightMode != Settings.NIGHT_MODE_SCHEDULED) && byUserRequest) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(items.size() - 1, 0);
      }
    }
  }

  /*private boolean canChooseWallpaper;

  private void setCanChooseWallpaper (boolean canChooseWallpaper) {
    if (this.canChooseWallpaper != canChooseWallpaper) {
      this.canChooseWallpaper = canChooseWallpaper;
      if (canChooseWallpaper) {
        int i = adapter.indexOfViewById(R.id.btn_forcePlainChannels);
        if (i != -1) {
          adapter.getItems().add(i + 1, new SettingItem(SettingItem.TYPE_SETTING, R.id.btn_chatBackground, 0, R.string.Wallpaper));
          adapter.getItems().add(i + 1, new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
          adapter.notifyItemRangeInserted(i + 1, 2);
        }
      } else {
        int i = adapter.indexOfViewById(R.id.btn_chatBackground);
        if (i != -1) {
          adapter.removeRange(i - 1, 2);
        }
      }
    }
  }*/

  @Override
  public void onNewValue (SliderWrapView wrapView, float value, float valueMax, int valueIndex, boolean isFinished) {
    if (Settings.instance().setNightModeMaxLux(value * valueMax, isFinished)) {
      int newPercentage = (int) (value * 100);
      if (currentNightPercentage != newPercentage) {
        currentNightPercentage = newPercentage;
        int i = adapter.indexOfViewByIdReverse(R.id.btn_autoNightMode_description);
        if (i != -1) {
          adapter.getItems().get(i).setString(Lang.getString(R.string.AutoNightModeDescriptionAuto, currentNightPercentage));
          adapter.updateValuedSettingByPosition(i);
        }
      }
      context().checkNightMode();
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_reduceMotion: {
        Settings.instance().toggleReduceMotion();
        adapter.updateValuedSettingById(R.id.btn_reduceMotion);
        break;
      }
      case R.id.btn_quick_reaction: {
        EditEnabledReactionsController c = new EditEnabledReactionsController(context, tdlib);
        c.setArguments(new EditEnabledReactionsController.Args(null, EditEnabledReactionsController.TYPE_QUICK_REACTION));
        navigateTo(c);
        break;
      }
      case R.id.btn_emoji: {
        SettingsCloudEmojiController c = new SettingsCloudEmojiController(context, tdlib);
        c.setArguments(new SettingsCloudController.Args<>(this));
        navigateTo(c);
        break;
      }
      case R.id.btn_icon: {
        SettingsCloudIconController c = new SettingsCloudIconController(context, tdlib);
        c.setArguments(new SettingsCloudController.Args<>(this));
        navigateTo(c);
        break;
      }
      case R.id.btn_earpieceMode: {
        showEarpieceOptions(false);
        break;
      }
      case R.id.btn_earpieceModeVideo: {
        showEarpieceOptions(true);
        break;
      }
      case R.id.btn_autoplayGIFs: {
        Settings.instance().setAutoplayGIFs(adapter.toggleView(v));
        break;
      }
      case R.id.btn_cameraSetting: {
        ListItem item = ((ListItem) v.getTag());
        Settings.instance().setNewSetting(item.getLongId(), item.getBoolValue() != adapter.toggleView(v));
        break;
      }
      case R.id.btn_cameraRatio: {
        showOptions(Lang.boldify(Lang.getString(R.string.CameraRatio)), new int[] {
          R.id.btn_cameraRatio_16_9,
          R.id.btn_cameraRatio_4_3,
          // R.id.btn_cameraRatio_1_1,
          R.id.btn_cameraRatio_fullScreen
        }, new String[] {
          "16:9",
          "4:3",
          // "1:1",
          Lang.getString(R.string.CameraRatioFull)
        }, null, new int[] {
          R.drawable.baseline_crop_16_9_24,
          R.drawable.baseline_crop_3_2_24,
          // R.drawable.baseline_crop_square_24,
          R.drawable.baseline_crop_free_24
        }, (optionView, optionId) -> {
          int cameraRatio;
          switch (optionId) {
            case R.id.btn_cameraRatio_1_1:
              cameraRatio = Settings.CAMERA_RATIO_1_1;
              break;
            case R.id.btn_cameraRatio_4_3:
              cameraRatio = Settings.CAMERA_RATIO_4_3;
              break;
            case R.id.btn_cameraRatio_fullScreen:
              cameraRatio = Settings.CAMERA_RATIO_FULL_SCREEN;
              break;
            case R.id.btn_cameraRatio_16_9:
            default:
              cameraRatio = Settings.CAMERA_RATIO_16_9;
              break;
          }
          Settings.instance().setCameraAspectRatioMode(cameraRatio);
          adapter.updateValuedSettingById(R.id.btn_cameraRatio);
          return true;
        });
        break;
      }
      case R.id.btn_cameraVolume: {
        showOptions(Lang.boldify(Lang.getString(R.string.CameraVolume)), new int[] {
          R.id.btn_cameraVolumeShoot,
          R.id.btn_cameraVolumeZoom,
          R.id.btn_cameraVolumeNone
        }, new String[] {
          Lang.getString(R.string.CameraVolumeShoot),
          Lang.getString(R.string.CameraVolumeZoom),
          Lang.getString(R.string.CameraVolumeNone),
        }, null, new int[] {
          R.drawable.baseline_camera_enhance_24,
          R.drawable.baseline_zoom_in_24,
          R.drawable.baseline_volume_up_24
        }, (optionView, optionId) -> {
          int cameraControlType;
          switch (optionId) {
            case R.id.btn_cameraVolumeShoot:
              cameraControlType = Settings.CAMERA_VOLUME_CONTROL_SHOOT;
              break;
            case R.id.btn_cameraVolumeZoom:
              cameraControlType = Settings.CAMERA_VOLUME_CONTROL_ZOOM;
              break;
            case R.id.btn_cameraVolumeNone:
            default:
              cameraControlType = Settings.CAMERA_VOLUME_CONTROL_NONE;
              break;
          }
          Settings.instance().setCameraVolumeControl(cameraControlType);
          adapter.updateValuedSettingById(R.id.btn_cameraVolume);
          return true;
        });
        break;
      }
      case R.id.btn_cameraType: {
        if (Config.CAMERA_X_AVAILABLE) {
          int type = Settings.instance().getCameraType();
          showSettings(R.id.btn_cameraType, new ListItem[] {
            new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_cameraTypeX, 0, R.string.CameraTypeXBeta, R.id.btn_cameraType, type == Settings.CAMERA_TYPE_X),
            new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_cameraTypeLegacy, 0, R.string.CameraTypeLegacy, R.id.btn_cameraType, type == Settings.CAMERA_TYPE_LEGACY),
            new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_cameraTypeSystem, 0, R.string.CameraTypeSystem, R.id.btn_cameraType, type == Settings.CAMERA_TYPE_SYSTEM),
          }, (id, result) -> {
            int cameraType = result.get(R.id.btn_cameraType);
            switch (cameraType) {
              case R.id.btn_cameraTypeX:
                cameraType = Settings.CAMERA_TYPE_X;
                break;
              case R.id.btn_cameraTypeLegacy:
                cameraType = Settings.CAMERA_TYPE_LEGACY;
                break;
              case R.id.btn_cameraTypeSystem:
                cameraType = Settings.CAMERA_TYPE_SYSTEM;
                break;
              default:
                cameraType = Settings.CAMERA_TYPE_DEFAULT;
                break;
            }
            int prevCameraType = Settings.instance().getCameraType();
            Settings.instance().setCameraType(cameraType);
            if (cameraType != Settings.CAMERA_TYPE_SYSTEM) {
              context.checkCameraApi();
            }
            if (prevCameraType != cameraType && (prevCameraType == Settings.CAMERA_TYPE_SYSTEM || cameraType == Settings.CAMERA_TYPE_SYSTEM)) {
              int index = adapter.indexOfViewById(R.id.btn_cameraType);
              if (index != -1) {
                index += 2; // + separator + keep discarded media
                if (prevCameraType == Settings.CAMERA_TYPE_SYSTEM) {
                  List<ListItem> items = getCameraSettings();
                  adapter.getItems().addAll(index + 1, items);
                  adapter.notifyItemRangeInserted(index + 1, items.size());
                  adapter.addItem(index + 1 + items.size() + 1, newCameraFlipInfoItem());
                } else {
                  adapter.removeRange(index + 1, CAMERA_SETTING_ITEM_COUNT);
                  adapter.removeItem(index + 2);
                }
              }
            }
            adapter.updateValuedSettingById(R.id.btn_cameraType);
          });
        } else {
          Settings.instance().setCameraType(adapter.toggleView(v) ? Settings.CAMERA_TYPE_SYSTEM : Settings.CAMERA_TYPE_LEGACY);
        }
        break;
      }
      case R.id.btn_systemFonts: {
        Boolean value = Fonts.areUsingSystemFonts();
        if (value != null && value != Settings.instance().useSystemFonts()) {
          Settings.instance().setUseSystemFonts(value);
          adapter.updateValuedSettingById(R.id.btn_systemFonts);
          break;
        }

        if (Settings.instance().useSystemFonts()) {
          showWarning(Lang.getString(R.string.RestartEffect), success -> {
            Settings.instance().setUseSystemFonts(false);
            adapter.updateValuedSettingById(R.id.btn_systemFonts);
          });
        } else {
          showWarning(TextUtils.concat(Lang.getMarkdownString(this, R.string.UseSystemFontsHint), "\n\n", Lang.getString(R.string.RestartEffect)), success -> {
            if (success) {
              Settings.instance().setUseSystemFonts(true);
              adapter.updateValuedSettingById(R.id.btn_systemFonts);
            }
          });
        }
        break;
      }
      case R.id.btn_hqRounds: {
        Settings.instance().setNeedHqRoundVideos(adapter.toggleView(v));
        break;
      }
      case R.id.btn_rearRounds: {
        Settings.instance().setStartRoundWithRear(adapter.toggleView(v));
        break;
      }
      case R.id.btn_autoNightModeScheduled_location: {
        if (locationHelper == null) {
          locationHelper = LocationHelper.requestLocation(context, 10000, true, true, (errorCode, location) -> {
            if (locationHelper == null) {
              return;
            }
            locationHelper = null;
            adapter.updateValuedSettingById(R.id.btn_autoNightModeScheduled_location);
            if (Settings.instance().getNightMode() != Settings.NIGHT_MODE_SCHEDULED) {
              return;
            }
            if (errorCode != LocationHelper.ERROR_CODE_NONE) {
              UI.showToast(R.string.DetectLocationError, Toast.LENGTH_SHORT);
            } else {
              Calendar sunrise = SunriseSunsetCalculator.getSunrise(location.getLatitude(), location.getLongitude(), TimeZone.getDefault(), DateUtils.getNowCalendar(), 0);
              Calendar sunset = SunriseSunsetCalculator.getSunset(location.getLatitude(), location.getLongitude(), TimeZone.getDefault(), DateUtils.getNowCalendar(), 0);
              /*if (result == null || result[0] == -1 || result[1] == -1) {
                UI.showToast(R.string.AutoNightModeScheduledByLocationError, Toast.LENGTH_SHORT);
                return;
              }*/
              int startHour = sunset.get(Calendar.HOUR_OF_DAY);
              int startMinute = sunset.get(Calendar.MINUTE);

              int endHour = sunrise.get(Calendar.HOUR_OF_DAY);
              int endMinute = sunrise.get(Calendar.MINUTE);

              UI.showToast(R.string.Done, Toast.LENGTH_SHORT);

              if (Settings.instance().setNightModeSchedule(BitwiseUtils.mergeLong(BitwiseUtils.mergeTimeToInt(startHour, startMinute, 0), BitwiseUtils.mergeTimeToInt(endHour, endMinute, 0)))) {
                adapter.updateValuedSettingById(R.id.btn_autoNightModeScheduled_timeOff);
                adapter.updateValuedSettingById(R.id.btn_autoNightModeScheduled_timeOn);
              }
            }
          });
          adapter.updateValuedSettingById(R.id.btn_autoNightModeScheduled_location);
        }
        break;
      }
      case R.id.btn_autoNightModeNone:
      case R.id.btn_autoNightModeAuto:
      case R.id.btn_autoNightModeScheduled:
      case R.id.btn_autoNightModeSystem: {
        if (adapter.processToggle(v)) {
          int value = adapter.getCheckIntResults().get(R.id.btn_autoNightMode);
          int newMode;
          switch (value) {
            case R.id.btn_autoNightModeNone:
              newMode = Settings.NIGHT_MODE_NONE;
              break;
            case R.id.btn_autoNightModeAuto:
              newMode = Settings.NIGHT_MODE_AUTO;
              break;
            case R.id.btn_autoNightModeScheduled:
              newMode = Settings.NIGHT_MODE_SCHEDULED;
              break;
            case R.id.btn_autoNightModeSystem:
              newMode = Settings.NIGHT_MODE_SYSTEM;
              break;
            default:
              return;
          }
          setCurrentNightMode(newMode, true);
          Settings.instance().setAutoNightMode(newMode);
        }
        break;
      }
      case R.id.btn_autoNightModeScheduled_timeOn:
      case R.id.btn_autoNightModeScheduled_timeOff: {
        final int id = v.getId();
        final boolean isOn = id == R.id.btn_autoNightModeScheduled_timeOn;
        final int time = isOn ? Settings.instance().getNightModeScheduleOn() : Settings.instance().getNightModeScheduleOff();

        TimePickerDialog timePickerDialog = new TimePickerDialog(context(), Theme.dialogTheme(), (view, hourOfDay, minute) -> {
          final int newTime = BitwiseUtils.mergeTimeToInt(hourOfDay, minute, 0);
          if (time != newTime) {
            Settings.instance().setNightModeSchedule(newTime, isOn);
            adapter.updateValuedSettingById(id);
          }
        }, BitwiseUtils.splitIntToHour(time), BitwiseUtils.splitIntToMinute(time), !UI.needAmPm());
        ViewSupport.showTimePicker(timePickerDialog);

        break;
      }
      case R.id.btn_markdown: {
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_EDIT_MARKDOWN, adapter.toggleView(v));
        break;
      }
      case R.id.btn_forceExoPlayerExtensions: {
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_FORCE_EXO_PLAYER_EXTENSIONS, adapter.toggleView(v));
        break;
      }
      case R.id.btn_audioCompression: {
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_NO_AUDIO_COMPRESSION, !adapter.toggleView(v));
        break;
      }
      case R.id.btn_sizeUnit: {
        boolean isMetric = Settings.instance().getNewSetting(Settings.SETTING_FLAG_USE_METRIC_FILE_SIZE_UNITS);
        showSettings(new SettingsWrapBuilder(R.id.btn_sizeUnit).setRawItems(new ListItem[]{
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_sizeUnitMetric, 0, R.string.SizeUnitMetric, R.id.btn_sizeUnit, isMetric),
          new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_sizeUnitBinary, 0, R.string.SizeUnitBinary, R.id.btn_sizeUnit, !isMetric),
        }).setIntDelegate((id, result) -> {
          boolean nowMetric = result.get(R.id.btn_sizeUnit) == R.id.btn_sizeUnitMetric;
          Settings.instance().setNewSetting(Settings.SETTING_FLAG_USE_METRIC_FILE_SIZE_UNITS, nowMetric);
          adapter.updateValuedSettingById(R.id.btn_sizeUnit);
        }).setAllowResize(false)); //.setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, 0, 0, UI.getString(R.string.MarkdownHint), false))
        break;
      }
      case R.id.btn_separateMedia: {
        Settings.instance().setNeedSeparateMediaTab(adapter.toggleView(v));
        break;
      }
      case R.id.btn_restrictSensitiveContent: {
        tdlib.setIgnoreSensitiveContentRestrictions(adapter.toggleView(v));
        break;
      }
      case R.id.btn_ignoreContentRestrictions: {
        Settings.instance().setRestrictContent(!adapter.toggleView(v));
        break;
      }
      case R.id.btn_useBigEmoji: {
        Settings.instance().setUseBigEmoji(adapter.toggleView(v));
        break;
      }
      case R.id.btn_secret_batmanTransitions: {
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_BATMAN_POLL_TRANSITIONS, adapter.toggleView(v));
        break;
      }
      case R.id.btn_stickerSuggestions: {
        showStickerOptions();
        break;
      }
      case R.id.btn_chatListStyle: {
        showChatListOptions();
        break;
      }
      case R.id.btn_instantViewMode: {
        showInstantViewOptions();
        break;
      }
      case R.id.btn_chatSwipes: {
        showSettings(R.id.btn_chatSwipes, new ListItem[]{
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_messageShare, 0, R.string.Share, R.id.btn_messageShare, Settings.instance().needChatQuickShare()),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_messageReply, 0, R.string.Reply, R.id.btn_messageReply, Settings.instance().needChatQuickReply())
        }, (id, result) -> {
          Settings.instance().setDisableChatQuickActions(result.get(R.id.btn_messageShare) != R.id.btn_messageShare, result.get(R.id.btn_messageReply) != R.id.btn_messageReply);
          adapter.updateValuedSettingById(R.id.btn_chatSwipes);
        });
        break;
      }
      case R.id.btn_big_reactions: {
        showSettings(R.id.btn_big_reactions, new ListItem[]{
          new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.BigReactionsInfo),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_bigReactionsChats, 0, R.string.BigReactionsChats, R.id.btn_bigReactionsChats, Settings.instance().getBigReactionsInChats()),
          new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_bigReactionsChannels, 0, R.string.BigReactionsChannels, R.id.btn_bigReactionsChannels, Settings.instance().getBigReactionsInChannels())
        }, (id, result) -> {
          Settings.instance().setBigReactionsInChannels(result.get(R.id.btn_bigReactionsChannels) == R.id.btn_bigReactionsChannels);
          Settings.instance().setBigReactionsInChats(result.get(R.id.btn_bigReactionsChats) == R.id.btn_bigReactionsChats);
          adapter.updateValuedSettingById(R.id.btn_big_reactions);
        });
        break;
      }
      case R.id.btn_systemEmoji: {
        Settings.instance().setUseSystemEmoji(adapter.toggleView(v));
        break;
      }
      case R.id.btn_customVibrations: {
        Settings.instance().setUseCustomVibrations(adapter.toggleView(v));
        break;
      }
      case R.id.btn_confirmCalls: {
        Settings.instance().setNeedOutboundCallsPrompt(adapter.toggleView(v));
        break;
      }
      case R.id.btn_useInAppBrowser: {
        Settings.instance().setUseInAppBrowser(adapter.toggleView(v));
        break;
      }
      case R.id.btn_switchRtl: {
        Settings.instance().setNeedRtl(Lang.packId(), adapter.toggleView(v));
        break;
      }
      case R.id.btn_useHoldToPreview: {
        Settings.instance().setNeedPreviewChatsOnHold(adapter.toggleView(v));
        break;
      }
      case R.id.btn_hideChatKeyboard: {
        Settings.instance().setNeedHideChatKeyboardOnScroll(adapter.toggleView(v));
        break;
      }
      case R.id.btn_sendByEnter: {
        Settings.instance().setNeedSendByEnter(adapter.toggleView(v));
        break;
      }
      case R.id.btn_toggleNewSetting: {
        ListItem item = (ListItem) v.getTag();
        boolean value = adapter.toggleView(v);
        if (item.getBoolValue())
          value = !value;
        Settings.instance().setNewSetting(item.getLongId(), value);
        if (value && item.getLongId() == Settings.SETTING_FLAG_DOWNLOAD_BETAS) {
          context().appUpdater().checkForUpdates();
        }
        break;
      }
      case R.id.btn_subscribeToBeta: {
        tdlib.ui().subscribeToBeta(this);
        break;
      }
      case R.id.btn_checkUpdates: {
        switch (context().appUpdater().state()) {
          case AppUpdater.State.NONE: {
            context().appUpdater().checkForUpdates();
            break;
          }
          case AppUpdater.State.CHECKING:
          case AppUpdater.State.DOWNLOADING: {
            // Do nothing.
            break;
          }
          case AppUpdater.State.AVAILABLE: {
            context().appUpdater().downloadUpdate();
            break;
          }
          case AppUpdater.State.READY_TO_INSTALL: {
            context().appUpdater().installUpdate();
            break;
          }
        }
        break;
      }
      case R.id.btn_updateAutomatically: {
        showUpdateOptions();
        break;
      }
      case R.id.btn_saveToGallery: {
        Settings.instance().setSaveEditedMediaToGallery(adapter.toggleView(v));
        break;
      }
      case R.id.btn_mosaic: {
        Settings.instance().setRememberAlbumSetting(adapter.toggleView(v));
        break;
      }
      case R.id.btn_chatFontSize: {
        MessagesController controller = new MessagesController(context, tdlib);
        controller.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_FONT_SIZE, null, null));
        navigateTo(controller);
        break;
      }
      case R.id.btn_chatBackground: {
        if (!context().permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES, grantType ->
          openWallpaperSetup()
        )) {
          openWallpaperSetup();
        }
        break;
      }
      case R.id.btn_previewChat: {
        MessagesController controller = new MessagesController(context, tdlib);
        controller.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_NONE, null, null));
        navigateTo(controller);
        break;
      }
      case R.id.btn_theme: {
        ListItem item = (ListItem) v.getTag();
        int themeId = item.getIntValue();
        if (ThemeManager.instance().isCurrentTheme(themeId)) {
          ThemeInfo theme = (ThemeInfo) item.getData();
          if (!theme.isInstalled() && theme.isCustom())
            editTheme(theme, false);
          else if (theme.isCustom())
            showThemeOptions(item);
        } else {
          ThemeManager.instance().changeGlobalTheme(tdlib, getTheme(item), false, null);
        }
        break;
      }
      case R.id.btn_themeCreate: {
        createNewTheme(currentTheme);
        break;
      }
      default: {
        ListItem item = (ListItem) v.getTag();
        switch (item.getCheckId()) {
          case R.id.btn_forcePlainChannels:
            if (adapter.processToggle(v)) {
              SparseIntArray array = adapter.getCheckIntResults();
              boolean value = array.get(R.id.btn_forcePlainChannels) != R.id.btn_forcePlainChannels;
              tdlib.settings().setForcePlainModeInChannels(value);
            }
            break;
          case R.id.theme_chat: {
            if (adapter.processToggle(v)) {
              SparseIntArray array = adapter.getCheckIntResults();
              int result = array.get(R.id.theme_chat);
              int chatStyle = result == R.id.theme_chat_classic ? ThemeManager.CHAT_STYLE_BUBBLES : ThemeManager.CHAT_STYLE_MODERN;
              tdlib.settings().setChatStyle(chatStyle);
            }
            /*if (!adapter.processToggle(v)) {
              break;
            }
            SparseIntArray array = adapter.getCheckIntResults();
            int result = array.get(R.id.theme_chat);
            switch (result) {
              case R.id.theme_chat_classic: {
                tdlib.settings().setChatStyle(ThemeManager.CHAT_STYLE_BUBBLES);
                setCanChooseWallpaper(tdlib.settings().canUseWallpaper());
                break;
              }
              case R.id.theme_chat_modern: {
                tdlib.settings().setChatStyle(ThemeManager.CHAT_STYLE_MODERN);
                setCanChooseWallpaper(tdlib.settings().canUseWallpaper());
                break;
              }
            }*/
            break;
          }
        }
        break;
      }
    }
  }

  private String newThemeName (String name, boolean copy) {
    int attempt = 2;
    if (copy) {
      name = Lang.getString(R.string.FileNameCopy, name);
      attempt = 1;
    }
    do {
      String nameAttempt = attempt == 1 ? name : Lang.getString(R.string.FileNameDuplicate, name, attempt);
      boolean found = false;
      for (ThemeInfo theme : installedThemes) {
        if (theme.getName().equals(nameAttempt)) {
          found = true;
          attempt++;
          break;
        }
      }
      if (found)
        continue;
      for (ThemeInfo theme : myThemes) {
        if (theme.getName().equals(nameAttempt)) {
          found = true;
          attempt++;
          break;
        }
      }
      if (!found) {
        return nameAttempt;
      }
    } while (true);
  }

  private void showUpdateOptions () {
    int autoUpdateMode = Settings.instance().getAutoUpdateMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_updateAutomatically).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_updateAutomaticallyPrompt, 0, R.string.AutoUpdatePrompt, R.id.btn_updateAutomatically, autoUpdateMode == Settings.AUTO_UPDATE_MODE_PROMPT),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_updateAutomaticallyAlways, 0, R.string.AutoUpdateAlways, R.id.btn_updateAutomatically, autoUpdateMode == Settings.AUTO_UPDATE_MODE_ALWAYS),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_updateAutomaticallyWiFi, 0, R.string.AutoUpdateWiFi, R.id.btn_updateAutomatically, autoUpdateMode == Settings.AUTO_UPDATE_MODE_WIFI_ONLY),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_updateAutomaticallyNever, 0, R.string.AutoUpdateNever, R.id.btn_updateAutomatically, autoUpdateMode == Settings.AUTO_UPDATE_MODE_NEVER),
    }).setAllowResize(false).setIntDelegate((id, result) -> {
      int autoUpdateMode1 = Settings.instance().getAutoUpdateMode();
      int autoUpdateResult = result.get(R.id.btn_updateAutomatically);
      boolean shouldChangeUi = (autoUpdateMode1 == Settings.AUTO_UPDATE_MODE_NEVER && autoUpdateResult != R.id.btn_updateAutomaticallyNever) || (autoUpdateMode1 != Settings.AUTO_UPDATE_MODE_NEVER && autoUpdateResult == R.id.btn_updateAutomaticallyNever);
      switch (autoUpdateResult) {
        case R.id.btn_updateAutomaticallyAlways:
          autoUpdateMode1 = Settings.AUTO_UPDATE_MODE_ALWAYS;
          break;
        case R.id.btn_updateAutomaticallyWiFi:
          autoUpdateMode1 = Settings.AUTO_UPDATE_MODE_WIFI_ONLY;
          break;
        case R.id.btn_updateAutomaticallyPrompt:
          autoUpdateMode1 = Settings.AUTO_UPDATE_MODE_PROMPT;
          break;
        case R.id.btn_updateAutomaticallyNever:
          autoUpdateMode1 = Settings.AUTO_UPDATE_MODE_NEVER;
          break;
      }
      Settings.instance().setAutoUpdateMode(autoUpdateMode1);
      adapter.updateValuedSettingById(R.id.btn_updateAutomatically);

      int index = adapter.indexOfViewById(R.id.btn_updateAutomatically);
      if (shouldChangeUi && index != -1) {
        if (autoUpdateMode1 == Settings.AUTO_UPDATE_MODE_NEVER) {
          adapter.removeRange(index + 1, 4);
        } else {
          adapter.addItems(index + 1, newAutoUpdateConfigurationItems().toArray(new ListItem[0]));
        }
      }
    }));
  }

  private void showThemeOptions (ListItem item) {
    int themeId = item.getIntValue();

    boolean isCustom = ThemeManager.isCustomTheme(themeId);
    int customThemeId = ThemeManager.resolveCustomThemeId(themeId);
    boolean canEdit = isCustom && Settings.instance().hasThemeOwnership(customThemeId);
    boolean isCurrent = ThemeManager.instance().isCurrentTheme(themeId);
    int size = isCustom ? (isCurrent ? 3 : 4): 1;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    StringList strings = new StringList(size);
    IntList colors = new IntList(size);
    CharSequence info = null;

    if (isCustom) {
      if (canEdit) {
        info = Lang.getStringBold(R.string.ThemeEditInfo, item.getString());

        ids.append(R.id.btn_edit);
        icons.append(R.drawable.baseline_edit_24);
        strings.append(R.string.ThemeEdit);
        colors.append(OPTION_COLOR_NORMAL);

        ids.append(R.id.btn_share);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(Settings.instance().canEditAuthor(customThemeId) ? R.string.ThemeExport : R.string.Share);
        colors.append(OPTION_COLOR_NORMAL);

        if (!isCurrent) {
          ids.append(R.id.btn_new);
          icons.append(R.drawable.baseline_content_copy_24);
          strings.append(R.string.ThemeCopy);
          colors.append(OPTION_COLOR_NORMAL);
        }
      } else {
        info = Lang.getStringBold(R.string.ThemeCreateInfo, item.getString());

        ids.append(R.id.btn_new);
        icons.append(R.drawable.baseline_edit_24);
        strings.append(R.string.ThemeCreate);
        colors.append(OPTION_COLOR_NORMAL);

        ids.append(R.id.btn_share);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(R.string.Share);
        colors.append(OPTION_COLOR_NORMAL);
      }

      ids.append(R.id.btn_delete);
      icons.append(R.drawable.baseline_delete_forever_24);
      strings.append(R.string.ThemeRemove);
      colors.append(OPTION_COLOR_RED);
    } else {
      info = Lang.getStringBold(R.string.ThemeCreateInfo, item.getString());
      ids.append(R.id.btn_new);
      icons.append(R.drawable.baseline_create_24);
      strings.append(R.string.ThemeCreate);
      colors.append(OPTION_COLOR_NORMAL);

      if (BuildConfig.DEBUG) {
        ids.append(R.id.btn_share);
        icons.append(R.drawable.baseline_forward_24);
        strings.append(R.string.Share);
        colors.append(OPTION_COLOR_NORMAL);
      }
    }

    showOptions(info, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_edit: {
          editTheme((ThemeInfo) item.getData(), false);
          break;
        }
        case R.id.btn_share: {
          ThemeInfo theme = (ThemeInfo) item.getData();
          tdlib.ui().exportTheme(this, theme, !theme.hasParent(), false);
          break;
        }
        case R.id.btn_delete: {
          ThemeInfo theme = (ThemeInfo) item.getData();
          tdlib.ui().showDeleteThemeConfirm(this, theme, () -> deleteTheme(theme, true));
          break;
        }
        case R.id.btn_new: {
          ThemeInfo theme = (ThemeInfo) item.getData();
          createNewTheme(theme);
          break;
        }
      }
      return true;
    });
  }

  private void createNewTheme (ThemeInfo theme) {
    if (theme == null)
      return;
    boolean isCustom = theme.isCustom();
    openInputAlert(Lang.getString(R.string.ThemeCreateTitle), Lang.getString(R.string.ThemeName), R.string.ThemeCreateConfirm, R.string.Cancel, newThemeName(theme.getName(), isCustom), (inputView, themeName) -> {
      themeName = themeName.trim();
      if (StringUtils.isEmpty(themeName))
        return false;
      int parentThemeId = isCustom ? theme.parentThemeId() : theme.getId();
      int newThemeId = ThemeManager.instance().newCustomTheme(themeName, parentThemeId, isCustom ? theme.getId() : 0);
      if (newThemeId == ThemeId.NONE)
        return false;
      ThemeInfo newTheme = new ThemeInfo(newThemeId, themeName, theme.getWallpaper(), parentThemeId, isCustom ? theme.getFlags() | Settings.THEME_FLAG_COPY : 0);
      if (theme.isCustom()) {
        newTheme.copyTheme((ThemeCustom) theme.getTheme());
      }
      tdlib.ui().postDelayed(() -> {
        if (!isDestroyed()) {
          editTheme(newTheme, true);
        }
      }, 150);
      return true;
    }, true);
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_theme: {
        ListItem item = (ListItem) v.getTag();
        showThemeOptions(item);
        return true;
      }
    }
    return false;
  }

  private ThemeDelegate getTheme (ListItem item) {
    ThemeInfo theme = (ThemeInfo) item.getData();
    return theme.getTheme();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    context().checkNightMode();
  }

  private void editTheme (ThemeInfo theme, boolean add) {
    if (isDestroyed())
      return;
    if (!ThemeManager.instance().isCurrentTheme(theme.getId())) {
      ThemeManager.instance().changeGlobalTheme(tdlib, theme.getTheme(), true, () -> editTheme(theme, add));
      return;
    }
    ThemeController c = new ThemeController(context, tdlib);
    c.setArguments(new ThemeController.Args(theme, add ? () -> addTheme(theme) : null, this));
    navigateTo(c);
  }

  public void updateTheme (ThemeInfo theme) {
    if (!isDestroyed() && adapter != null) {
      int i = adapter.indexOfViewByData(theme);
      if (i != -1) {
        adapter.getItems().get(i).setString(theme.getName());
        adapter.updateValuedSettingByPosition(i);
      }
    }
  }

  private ThemeInfo currentTheme;
  private ListItem createThemeItem;

  private ThemeInfo findThemeInfo (ThemeDelegate delegate) {
    int id = delegate.getId();
    if (ThemeManager.isCustomTheme(id)) {
      if (installedThemes != null) {
        for (ThemeInfo theme : installedThemes) {
          if (id == theme.getId())
            return theme;
        }
      }
      if (myThemes != null) {
        for (ThemeInfo theme : myThemes) {
          if (id == theme.getId())
            return theme;
        }
      }
    } else {
      if (builtinThemes != null) {
        for (ThemeInfo theme : builtinThemes) {
          if (id == theme.getId())
            return theme;
        }
      }
    }
    return null;
  }

  private void addThemeGroup (List<ListItem> items, List<ThemeInfo> themes, boolean areCustom) {
    if (themes.isEmpty() && !areCustom)
      return;
    boolean first = true;
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, 0));
    boolean addedAccent = false;
    for (ThemeInfo theme : themes) {
      boolean isAccent = theme.isAccent();
      if (isAccent && addedAccent)
        continue;
      if (ThemeManager.instance().isCurrentTheme(theme.getId()))
        currentTheme = theme;
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      if (isAccent) {
        // TODO special accent switcher
        items.add(newItem(theme));
        // addedAccent = true;
      } else {
        items.add(newItem(theme));
      }
    }
    if (areCustom) {
      if (!first)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_themeCreate, 0, R.string.ThemeCreate));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (areCustom) {
      items.add(createThemeItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_themeCreateInfo));
      if (currentTheme != null) {
        createThemeItem.setString(Lang.getStringBold(R.string.ThemeCreateInfo, currentTheme.getName()));
      }
    }

  }

  private int findThemeCell (@ThemeId int themeId) {
    int i = adapter.indexOfViewById(R.id.btn_theme);
    if (i != -1) {
      int maxCount = adapter.getItemCount();
      for (; i < maxCount; i++) {
        ListItem item = adapter.getItems().get(i);
        if (item.getId() == R.id.btn_theme && item.getIntValue() == themeId) {
          return i;
        }
      }
    }
    return -1;
  }

  private void addTheme (ThemeInfo theme) {
    if (theme == null)
      throw new IllegalArgumentException();
    int index = adapter.indexOfViewById(R.id.btn_themeCreate);
    if (index == -1)
      throw new IllegalArgumentException();
    index -= myThemes.size() * 2;
    if (theme.isInstalled()) {
      installedThemes.add(0, theme);
      index--; // top shadow of my themes
      if (installedThemes.isEmpty()) {
        adapter.getItems().add(index, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.getItems().add(index, newItem(theme));
        adapter.getItems().add(index, new ListItem(ListItem.TYPE_SHADOW_TOP));
        adapter.notifyItemRangeInserted(index, 3);
      } else {
        index -= installedThemes.size() * 2;
        adapter.getItems().add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(index, newItem(theme));
        adapter.notifyItemRangeInserted(index, 2);
      }
    } else {
      myThemes.add(0, theme);
      adapter.getItems().add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      adapter.getItems().add(index, newItem(theme));
      adapter.notifyItemRangeInserted(index, 2);
    }
  }

  public void deleteTheme (ThemeInfo theme, boolean animated) {
    if (!isDestroyed()) {
      RecyclerView.ItemAnimator animator = animated ? null : getRecyclerView().getItemAnimator();
      if (!animated && animator != null) {
        getRecyclerView().setItemAnimator(null);
      }
      if (theme.isInstalled()) {
        int dataIndex = installedThemes.indexOf(theme);
        if (dataIndex == -1)
          return;
        int index = adapter.indexOfViewByData(theme);
        installedThemes.remove(dataIndex);
        if (installedThemes.isEmpty()) {
          adapter.removeRange(index - 1, 3);
        } else if (dataIndex == 0) {
          adapter.removeRange(index, 2);
        } else {
          adapter.removeRange(index - 1, 2);
        }
      } else {
        int dataIndex = myThemes.indexOf(theme);
        if (dataIndex == -1)
          return;
        myThemes.remove(dataIndex);
        int index = adapter.indexOfViewByData(theme);
        if (index != -1)
          adapter.removeRange(index, 2);
      }

      if (!animated && animator != null) {
        tdlib.ui().postDelayed(() -> getRecyclerView().setItemAnimator(animator), 100);
      }
    }
  }

  private void showChatListOptions () {
    int chatListStyle = Settings.instance().getChatListMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_chatListStyle).setRawItems(new ListItem[]{
      /*new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION, R.id.btn_chatListStyleOptIcons, 0, R.string.ChatListStyleOptIcons, R.id.btn_chatListStyleOptIcons, Settings.instance().),
      new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION, R.id.btn_chatListStyleOptFormatting, 0, R.string.ChatListStyleOptFormatting, R.id.btn_chatListStyleOptFormatting, Settings.instance().),*/
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle1, 0, R.string.ChatListStyle1, R.id.btn_chatListStyle, chatListStyle == Settings.CHAT_MODE_2LINE),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle2, 0, R.string.ChatListStyle2, R.id.btn_chatListStyle, chatListStyle == Settings.CHAT_MODE_3LINE),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle3, 0, R.string.ChatListStyle3, R.id.btn_chatListStyle, chatListStyle == Settings.CHAT_MODE_3LINE_BIG),
    }).setIntDelegate((id, result) -> {
      int chatListStyle1 = Settings.instance().getChatListMode();
      int chatListStyleResult = result.get(R.id.btn_chatListStyle);
      switch (chatListStyleResult) {
        case R.id.btn_chatListStyle1:
          chatListStyle1 = Settings.CHAT_MODE_2LINE;
          break;
        case R.id.btn_chatListStyle2:
          chatListStyle1 = Settings.CHAT_MODE_3LINE;
          break;
        case R.id.btn_chatListStyle3:
          chatListStyle1 = Settings.CHAT_MODE_3LINE_BIG;
          break;
      }
      Settings.instance().setChatListMode(chatListStyle1);
      adapter.updateValuedSettingById(R.id.btn_chatListStyle);
    }).setAllowResize(false)); //.setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, 0, 0, UI.getString(R.string.MarkdownHint), false))
  }

  private void showStickerOptions () {
    int stickerOption = Settings.instance().getStickerMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_stickerSuggestions).setRawItems(new ListItem[]{
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerSuggestionsAll, 0, R.string.SuggestStickersAll, R.id.btn_stickerSuggestions, stickerOption == Settings.STICKER_MODE_ALL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerSuggestionsInstalled, 0, R.string.SuggestStickersInstalled, R.id.btn_stickerSuggestions, stickerOption == Settings.STICKER_MODE_ONLY_INSTALLED),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_stickerSuggestionsNone, 0, R.string.SuggestStickersNone, R.id.btn_stickerSuggestions, stickerOption == Settings.STICKER_MODE_NONE),
    }).setIntDelegate((id, result) -> {
      int stickerOption1 = Settings.instance().getStickerMode();
      int stickerResult = result.get(R.id.btn_stickerSuggestions);
      switch (stickerResult) {
        case R.id.btn_stickerSuggestionsAll:
          stickerOption1 = Settings.STICKER_MODE_ALL;
          break;
        case R.id.btn_stickerSuggestionsInstalled:
          stickerOption1 = Settings.STICKER_MODE_ONLY_INSTALLED;
          break;
        case R.id.btn_stickerSuggestionsNone:
          stickerOption1 = Settings.STICKER_MODE_NONE;
          break;
      }
      Settings.instance().setStickerMode(stickerOption1);
      adapter.updateValuedSettingById(R.id.btn_stickerSuggestions);
    }).setAllowResize(false)); //.setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, 0, 0, UI.getString(R.string.MarkdownHint), false))
  }

  private void showEarpieceOptions (boolean isVideo) {
    int earpieceMode = Settings.instance().getEarpieceMode(isVideo);
    int settingId = isVideo ? R.id.btn_earpieceModeVideo : R.id.btn_earpieceMode;
    ListItem item1 = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_earpieceMode_never, 0, R.string.EarpieceModeNever, settingId, earpieceMode == Settings.EARPIECE_MODE_NEVER);
    ListItem item2 = new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_earpieceMode_proximity, 0, R.string.EarpieceModeProximity, settingId, earpieceMode == Settings.EARPIECE_MODE_PROXIMITY);
    ListItem[] items = isVideo ? new ListItem[] {item1, item2} : new ListItem[] {
      item1, item2, new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_earpieceMode_always, 0, R.string.EarpieceModeAlways, settingId, earpieceMode == Settings.EARPIECE_MODE_ALWAYS)
    };
    showSettings(new SettingsWrapBuilder(settingId).setRawItems(items).setAllowResize(false).setIntDelegate((id, result) -> {
      int newMode = earpieceMode;
      switch (result.get(settingId)) {
        case R.id.btn_earpieceMode_never:
          newMode = Settings.EARPIECE_MODE_NEVER;
          break;
        case R.id.btn_earpieceMode_proximity:
          newMode = Settings.EARPIECE_MODE_PROXIMITY;
          break;
        case R.id.btn_earpieceMode_always:
          newMode = Settings.EARPIECE_MODE_ALWAYS;
          break;
      }
      Settings.instance().setEarpieceMode(isVideo, newMode);
      adapter.updateValuedSettingById(settingId);
    }));
  }

  private void showInstantViewOptions () {
    int instantViewOption = Settings.instance().getInstantViewMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_instantViewMode).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_instantViewModeAll, 0, R.string.AutoInstantViewAll, R.id.btn_instantViewMode, instantViewOption == Settings.INSTANT_VIEW_MODE_ALL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_instantViewModeTelegram, 0, R.string.AutoInstantViewTelegram, R.id.btn_instantViewMode, instantViewOption == Settings.INSTANT_VIEW_MODE_INTERNAL),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_instantViewModeNone, 0, R.string.AutoInstantViewNone, R.id.btn_instantViewMode, instantViewOption == Settings.INSTANT_VIEW_MODE_NONE),
    }).setAllowResize(false).addHeaderItem(Lang.getString(R.string.AutoInstantViewDesc)).setIntDelegate((id, result) -> {
      int instantViewOption1 = Settings.instance().getInstantViewMode();
      int instantViewResult = result.get(R.id.btn_instantViewMode);
      switch (instantViewResult) {
        case R.id.btn_instantViewModeNone:
          instantViewOption1 = Settings.INSTANT_VIEW_MODE_NONE;
          break;
        case R.id.btn_instantViewModeTelegram:
          instantViewOption1 = Settings.INSTANT_VIEW_MODE_INTERNAL;
          break;
        case R.id.btn_instantViewModeAll:
          instantViewOption1 = Settings.INSTANT_VIEW_MODE_ALL;
          break;
      }
      Settings.instance().setInstantViewMode(instantViewOption1);
      adapter.updateValuedSettingById(R.id.btn_instantViewMode);
    }));
  }

  /*private void showMarkdownOptions () {
    int markdownOption = Settings.instance().getMarkdownMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_markdown).setRawItems(new SettingItem[]{
      new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_markdownEnabled, 0, R.string.MarkdownEnabled, R.id.btn_markdown, markdownOption == Settings.MARKDOWN_MODE_ENABLED),
      new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_markdownTextOnly, 0, R.string.MarkdownTextOnly, R.id.btn_markdown, markdownOption == Settings.MARKDOWN_MODE_TEXT_ONLY),
      new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_markdownDisabled, 0, R.string.MarkdownDisabled, R.id.btn_markdown, markdownOption == Settings.MARKDOWN_MODE_DISABLED),
    }).setIntDelegate((id, result) -> {
      int markdownOption1 = Settings.instance().getMarkdownMode();
      int markdownResult = result.get(R.id.btn_markdown);
      switch (markdownResult) {
        case R.id.btn_markdownEnabled:
          markdownOption1 = Settings.MARKDOWN_MODE_ENABLED;
          break;
        case R.id.btn_markdownTextOnly:
          markdownOption1 = Settings.MARKDOWN_MODE_TEXT_ONLY;
          break;
        case R.id.btn_markdownDisabled:
          markdownOption1 = Settings.MARKDOWN_MODE_DISABLED;
          break;
      }
      Settings.instance().setMarkdownMode(markdownOption1);
      adapter.updateValuedSettingById(R.id.btn_markdown);
    }).setAllowResize(false).setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, 0, 0, Lang.getString(R.string.MarkdownHint), false)));
  }*/

  /*private void showLuxCalibration () {
    String[] maxLuxValues = new String[TGSettingsManager.MAX_NIGHT_LUX.length];
    int i = 0;
    for (float maxNightLux : TGSettingsManager.MAX_NIGHT_LUX) {
      maxLuxValues[i] = Strings.toString(maxNightLux);
      i++;
    }
    String[] luxMultiplyValues = new String[TGSettingsManager.MAX_NIGHT_LUX_MULTIPLIES.length];
    i = 0;
    for (float luxMultiply : TGSettingsManager.MAX_NIGHT_LUX_MULTIPLIES) {
      luxMultiplyValues[i] = Strings.toString(luxMultiply);
      i++;
    }

    int maxLuxIndex = U.indexOf(TGSettingsManager.MAX_NIGHT_LUX, TGSettingsManager.instance().getMaxNightLuxRaw());
    int luxMultiplyIndex = U.indexOf(TGSettingsManager.MAX_NIGHT_LUX_MULTIPLIES, TGSettingsManager.instance().getMaxNightLuxMultiply());
    currentSettings = showSettings(new SettingsWrapBuilder(R.id.btn_maxNightLux)
      .setHeaderItem(new SettingItem(SettingItem.TYPE_INFO, R.id.btn_currentLux, 0, getCurrentLuxStr(), false))
      .setRawItems(new SettingItem[]{
        new SettingItem(SettingItem.TYPE_SLIDER, R.id.btn_lux, 0, R.string.MaxLux, true).setSliderInfo(maxLuxValues, maxLuxIndex),
        new SettingItem(SettingItem.TYPE_SLIDER, R.id.btn_luxMultiply, 0, R.string.Multiply, true).setSliderInfo(luxMultiplyValues, luxMultiplyIndex)
      }).setCancelStr(R.string.Reset).setCancelColorId(R.id.theme_color_textNegativeAction).setSaveStr(R.string.Done).setDismissListener(new PopupLayout.PopupDismissListener() {
        @Override
        public void onPopupDismiss (PopupLayout popup) {
          UI.getContext(getContext()).removeLuxListener(SettingsThemeController.this);
        }
      }).setOnActionButtonClick(new SettingsWrap.OnActionButtonClick() {
        @Override
        public boolean onActionButtonClick (SettingsWrap wrap, View view, boolean isCancel) {
          if (isCancel) {
            setMaxNightLux(TGSettingsManager.MAX_NIGHT_LUX_DEFAULT, TGSettingsManager.MAX_NIGHT_LUX_MULTIPLY_DEFAULT);
          }
          return false;
        }
      }));
    if (currentSettings != null) {
      currentSettings.adapter.setSliderChangeListener(new SliderWrapView.RealTimeChangeListener() {
        @Override
        public void onNewValue (SliderWrapView wrapView, int newValue) {
          SettingItem item = (SettingItem) wrapView.getTag();
          switch (item.getId()) {
            case R.id.btn_lux: {
              setMaxNightLux(TGSettingsManager.MAX_NIGHT_LUX[newValue], TGSettingsManager.instance().getMaxNightLuxMultiply());
              break;
            }
            case R.id.btn_luxMultiply: {
              setMaxNightLux(TGSettingsManager.instance().getMaxNightLuxRaw(), TGSettingsManager.MAX_NIGHT_LUX_MULTIPLIES[newValue]);
              break;
            }
          }
        }
      });
      UI.getContext(getContext()).addLuxListener(this);
    }
  }

  private void setMaxNightLux (float lux, float multiply) {
    if (TGSettingsManager.instance().setMaxNightLux(lux, multiply)) {
      UI.getContext(getContext()).checkNightMode();
      adapter.updateValuedSettingById(R.id.btn_maxNightLux);
      updateCurrentLux();
    }
  }

  private String getCurrentLuxStr () {
    float currentLux = UI.getContext(getContext()).getLastLuxValue();
    float maxLux = TGSettingsManager.instance().getMaxNightLux();
    StringBuilder b = new StringBuilder();
    b.append(Strings.toString(maxLux));
    b.append(' ');
    b.append(maxLux < currentLux ? '<' : currentLux == maxLux ? '=' : '>');
    b.append(' ');
    b.append(Strings.toString(currentLux));
    return UI.getString(R.string.AdjustNightLuxDesc, b.toString(), UI.getString(currentLux <= maxLux ? R.string.LuxDark : R.string.LuxBright).toLowerCase());
  }*/

  private void openWallpaperSetup () {
    MessagesController controller = new MessagesController(context, tdlib);
    controller.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_WALLPAPER, null, null));
    navigateTo(controller);
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    switch (id) {
      case R.id.theme_chat: {
        final int chatStyleId = result.get(R.id.theme_chat, R.id.theme_chat_modern);
        final int chatStyle;
        switch (chatStyleId) {
          case R.id.theme_chat_modern: {
            chatStyle = ThemeManager.CHAT_STYLE_MODERN;
            break;
          }
          case R.id.theme_chat_classic: {
            chatStyle = ThemeManager.CHAT_STYLE_BUBBLES;
            break;
          }
          default: {
            return;
          }
        }
        tdlib.settings().setChatStyle(chatStyle);
        adapter.updateValuedSettingById(R.id.theme_chat);
        break;
      }
    }
  }
}
