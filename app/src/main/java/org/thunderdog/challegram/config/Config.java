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
 * File created on 08/02/2017
 */
package org.thunderdog.challegram.config;

import android.os.Build;
import android.view.WindowManager;

import androidx.annotation.Dimension;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.theme.ColorId;

public class Config {
  public static final boolean SUPPORT_SYSTEM_UNDERLINE_SPAN = true;
  public static final boolean FOREGROUND_SYNC_ALWAYS_ENABLED = true; // Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
  public static final @Dimension(unit = Dimension.DP) int COMMENTS_BUBBLE_BUTTON_MIN_WIDTH = 200;
  public static final boolean SHOW_CHANNEL_POST_REPLY_INFO_IN_COMMENTS = true;
  public static final boolean CHAT_FOLDERS_SMART_CHAT_DELETION_ENABLED = true;
  public static final boolean CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL = true;
  public static final boolean CHAT_FOLDERS_APPEARANCE_IS_GLOBAL = true;
  public static final boolean RESTRICT_HIDING_MAIN_LIST = true;
  public static final boolean SEARCH_MESSAGES_ONLY_IN_SELECTED_FOLDER = BuildConfig.EXPERIMENTAL;
  public static final boolean CHAT_FOLDERS_UNSET_DEFAULT_ICONS = false; // Until there's a fix on server

  public static final boolean TEST_NEW_FEATURES_PROMPTS = false;

  public static final boolean NEED_SILENT_BROADCAST = false;

  public static final boolean CAN_CHANGE_SELF_ADMIN_CUSTOM_TITLE = false;

  public static final boolean SHOW_EMOJI_TONE_PICKER_ALWAYS = true;

  public static final String ICONS_BUILTIN_ID = "material-baseline";

  public static final boolean MODERN_IMAGE_DECODER_ENABLED = true;
  public static final boolean FORCE_SOFTWARE_IMAGE_DECODER = true;

  public static final boolean WAIT_ANIMATIONS_BEFORE_START_VIDEO = true;

  // Allow stretch bounce in places where the glow looks ugly
  public static final boolean HAS_NICE_OVER_SCROLL_EFFECT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

  private static Boolean hasWebpSupport;
  public static boolean useBundledWebp () {
    if (BuildConfig.WEBP_ENABLED) {
      if (hasWebpSupport == null) {
        hasWebpSupport = N.hasBuiltInWebpSupport();
      }
      return hasWebpSupport;
    }
    return false;
  }

  public static final boolean TEST_NOTIFICATION_PROBLEM_RESOLUTION = false; // BuildConfig.DEBUG;

  public static final boolean NEED_TDLIB_CLEANUP = false;

  public static final boolean FAKE_BACKGROUND_CONNECTION_STATE = true;

  public static final boolean NEED_SYSTEM_SYNC = true;
  public static final boolean TEST_CONFETTI = false;

  public static final boolean NOTIFICATION_AUTO_CANCEL = true;
  public static final boolean NOTIFICATION_AUTO_CANCEL_SPECIFIC = true;
  public static final int MAX_RUNNING_TDLIBS = 5;

  public static final boolean NEED_LANGUAGE_WORKAROUND = false;

  public static final boolean RTL_BETA = true;

  public static final boolean ALLOW_SEEK_ANYTIME = true;

  public static final boolean NEED_NETWORK_SYNC_REQUEST = false;

  public static final boolean AWAKE_ALL_TDLIB_INSTANCES = !BuildConfig.DEBUG;

  // Fields from default config.

  public static final int STATUS_BAR_COLOR_ID = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? ColorId.statusBar : ColorId.statusBarLegacy;
  public static final int STATUS_BAR_TEXT_COLOR_ID = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? ColorId.statusBarContent : ColorId.statusBarLegacyContent;

  public static final boolean DISABLE_SENDING_MEDIA_CACHE = false; // BuildConfig.DEBUG; // FIXME: TDLib
  public static final boolean WORKAROUND_NEED_MODIFY = true; // FIXME TDLib

  public static final boolean USE_FULLSCREEN_NAVIGATION = true;
  public static final boolean USE_FULLSCREEN_NAVIGATION_CONTENT = false; // BuildConfig.DEBUG;
  public static final boolean USE_TRANSLUCENT_NAVIGATION = false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

  public static final boolean HARDWARE_MESSAGE_LAYER = false;
  public static final boolean HARDWARE_CLIP_PATH_FIX = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
  public static final boolean HARDWARE_MESSAGES_LIST = false; // HARDWARE_CLIP_PATH_FIX; // HARDWARE_CLIP_PATH_FIX;
  public static final boolean HARDWARE_MEDIA_VIEWER = HARDWARE_CLIP_PATH_FIX;

  public static final boolean ALLOW_MORE_CACHED_MESSAGES = false;
  public static final boolean NEED_MEDIA_GROUP_MERGE_REQUESTS = false;

  public static final int CHANNEL_MEMBER_STRING = R.string.xSubscribers;

  public static final boolean USE_SCALED_ROUNDINGS = false;

  public static final boolean TEST_CHANGELOG = false;

  public static final boolean SERVICES_ENABLED = true; // !BuildConfig.DEBUG;

  public static final boolean PIN_BITMAP_ENABLED = Build.VERSION.SDK_INT < Build.VERSION_CODES.M && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
  public static final boolean GCM_ENABLED = true;

  public static final boolean ALLOW_SYSTEM_EMOJI = BuildConfig.DEBUG;

  public static final boolean USE_VIDEO_COMPRESSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
  public static final boolean MUTE_VIDEO_AVAILABLE = USE_VIDEO_COMPRESSION;

  public static final boolean MASKS_TEXTS_AVAILABLE = false;

  public static final boolean DEBUG_GALAXY_TAB_2 = false;

  public static final boolean USE_SECRET_SEARCH = true;

  public static final boolean SECRET_PREVIEWS_AVAILABLE = BuildConfig.DEBUG;

  public static final boolean USE_NONSTRICT_TEXT_ALWAYS = false;

  public static final boolean BUBBLE_USE_SEPARATE_BACKGROUND_FOR_SERVICE_MESSAGES = false;
  public static final boolean MOVE_BUBBLE_TIME_RTL_TO_LEFT = true;

  public static final boolean USE_STICKER_VIBRATE = true;


  public static final boolean CUSTOM_CAMERA_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
  public static final boolean CAMERA_ALLOW_FAKE_ROTATION = true;
  public static final boolean CAMERA_AUTO_FLASH_ENABLED = true;
  public static final boolean CAMERA_ALLOW_SNAPSHOTS = false; // true;
  public static final boolean CAMERA_X_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public static final boolean CROP_USE_REGION_READER = true;
  public static final boolean CROP_ENABLED = true;
  public static final boolean MODERN_VIDEO_TRANSCODING_ENABLED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public static final boolean IN_APP_BROWSER_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;

  public static final boolean VIDEO_PLAYER_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

  public static final boolean FORCE_TOUCH_ENABLED = true;
  public static final boolean HOLD_TO_PREVIEW_AVAILABLE = true;
  public static final boolean USE_CUSTOM_INPUT_STYLING = true;

  public static final int ICON_MARK_AS_READ = R.drawable.baseline_done_all_24;
  public static final int ICON_MARK_AS_UNREAD = R.drawable.baseline_unsubscribe_24;

  public static final boolean SLOW_VIDEO_SWITCH = Device.IS_SAMSUNG; // TODO make

  public static final boolean ROUND_VIDEOS_PLAYBACK_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
  public static final boolean ROUND_VIDEOS_RECORD_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;

  public static final boolean DEBUG_CLIPPING = false;
  public static final boolean USE_CRASHLYTICS = false;

  public static final boolean CALL_FROM_PREVIEW = false;

  public static final boolean USE_GROUP_NAMES = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

  public static final boolean FORCE_SHOW_RECENTS_STICKERS_TITLE = false;
  public static final int DEFAULT_SHOW_RECENT_STICKERS_COUNT = 10;

  public static final boolean USE_TEXT_ADVANCE = true; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;

  public static final boolean SMOOTH_SCROLL_TO_BOTTOM_ENABLED = false; // Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

  public static final boolean ALLOW_DEBUG_DC = BuildConfig.DEBUG || BuildConfig.EXPERIMENTAL;

  public static final boolean DEBUG_DISABLE_DOWNLOAD = false; // BuildConfig.DEBUG;
  public static final boolean DEBUG_DISABLE_IMAGES = false; // BuildConfig.DEBUG;

  public static final boolean USE_NEW_PLAYER = true;

  public static final boolean USE_CLOUD_PLAYER = USE_NEW_PLAYER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
  public static final float DEFAULT_ICON_SWITCH_SCALE = .4f;
  public static final boolean CUTOUT_ENABLED = true; // Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
  public static final boolean EXPLICIT_DICE_AVAILABLE = false;

  public static boolean useCloudPlayback (TdApi.Message playPauseFile) {
    if (USE_CLOUD_PLAYER && playPauseFile != null) {
      //noinspection SwitchIntDef
      switch (playPauseFile.content.getConstructor()) {
        case TdApi.MessageAudio.CONSTRUCTOR:
          TdApi.Audio audio = ((TdApi.MessageAudio) playPauseFile.content).audio;
          return (audio.audio.remote == null || !audio.audio.remote.isUploadingActive);
        case TdApi.MessageVoiceNote.CONSTRUCTOR:
          TdApi.VoiceNote voice = ((TdApi.MessageVoiceNote) playPauseFile.content).voiceNote;
          return voice.voice.remote == null || !voice.voice.remote.isUploadingActive;
        case TdApi.MessageDocument.CONSTRUCTOR:
          return TD.isSupportedMusic(((TdApi.MessageDocument) playPauseFile.content).document);
      }
    }
    return false;
  }

  public static final boolean REQUEST_BACKGROUND_LOCATION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

  public static final boolean DISABLE_VIEWER_ELEVATION = false;

  public static final int COVER_OVERLAY = 0x44000000;
  public static final int COVER_OVERLAY_QUEUE = 0x80000000;

  public static final int VIDEO_RESOLUTION = 640;
  public static final int VIDEO_BITRATE = 800 * 1000;

  public static final int DEFAULT_WINDOW_PARAMS = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

  public static final int MAX_ROBOT_ID = 9;
  public static final int ROBOT_ID_PREFIX = 50;
  public static final int ROBOT_DC_ID = 1;
  public static final String ROBOT_PASSWORD = "objection";

  public static boolean VIBRATE_ONLY_IF_SILENT_AVAILABLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.O;

  public static boolean USE_OLD_COVER = false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;

  public static boolean ALLOW_BOT_COVERS = true;

  public static final boolean FAVORITE_STICKERS_WITHOUT_SETS = false;
  public static final boolean DEBUG_NAV_ANIM = false; // BuildConfig.DEBUG;

  public static final boolean NEED_NOTIFICATION_CONTENT_PREVIEW = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public static final boolean SHOW_COPY_REPORT_DETAILS_IN_SETTINGS = BuildConfig.EXPERIMENTAL;

  public static final boolean FORCE_DISABLE_NOTIFICATIONS = BuildConfig.EXPERIMENTAL && !BuildConfig.DEBUG;

  public static final int MINIMUM_CALL_CONTACTS_SUGGESTIONS = 3;

  public static final boolean USE_CUSTOM_NAVIGATION_COLOR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

  public static final boolean PREFER_RENDER_EXTENSIONS = true;

  public static final boolean NEED_ONLINE = true; // !BuildConfig.DEBUG;

  /* TODO: Missing Android API
   * TextUtils.CHAR_SEQUENCE_CREATOR doesn't support ImageSpan (or whatever alternative),
   * therefore it's impossible to display even static custom emoji in notifications
   * as of Android 12L (SDK 32).
   */
  public static final boolean SYSTEM_SUPPORTS_CUSTOM_IMAGE_SPANS = false;

  public static final boolean HIDE_EMPTY_TABS = true;

  public static final boolean CRASH_CHAT_NOT_FOUND = true;

  public static final boolean ALLOW_DATE_MODIFIED_RESOLVING = false; // disabling, because android sucks

  public static final boolean REVEAL_ANIMATION_AVAILABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

  public static final boolean REMOVE_INTRO = true;

  public static final boolean TEST_CHAT_COUNTERS = false;

  public static boolean isThemeDoc (TdApi.Document doc) {
    return doc != null && doc.fileName != null && doc.fileName.toLowerCase().endsWith("." + BuildConfig.THEME_FILE_EXTENSION);
  }

  public static final boolean DISABLE_PASSWORD_INVISIBILITY = true;

  public static final boolean DEBUG_STICKER_OUTLINES = false; // BuildConfig.DEBUG;
  public static final boolean DEBUG_GIF_OPTIMIZATION_MODE = false;

  public static final int SUPPORTED_INSTANT_VIEW_VERSION = 2;
  public static final boolean INSTANT_VIEW_WRONG_LAYOUT = false;

  public static final boolean QR_AVAILABLE = true;
  public static final boolean QR_FORCE_ZXING = false;
  public static final boolean MANAGE_STORAGE_PERMISSION_AVAILABLE = false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

  public static final boolean VIDEO_CLOUD_PLAYBACK_AVAILABLE = true;

  public static final float MAX_ANIMATED_EMOJI_REFRESH_RATE = 30.0f;

  public static final String FILE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".provider";

  public static final boolean PROFILE_DEADLOCKS = true;

  public static final boolean DEBUG_REACTIONS_ANIMATIONS = false;
  public static final boolean TEST_STATIC_REACTIONS = false;
  public static final boolean TEST_GENERIC_REACTION_EFFECTS = false;

  public static final boolean REORDER_INSTALLED_STICKER_SETS = false;

  public static final boolean NEED_TEMPORARY_TOPICS_WORKAROUND = true;

  public static final boolean USE_HARDWARE_PHOTO_VIEWER_CONFIG = false;

  public static final boolean REQUIRE_FIREBASE_SERVICES_FOR_SAFETYNET = false;

  public static final boolean USE_INPUT_VIEW_CLIPPING_FIX = false;

  public static final int VOIP_CONNECTION_MIN_LAYER = 65;
  public static final boolean FORCE_DIRECT_TGVOIP = false;

  public static final boolean ALLOW_SPONSORED_MESSAGE_LINK_COPY = true;
  public static final boolean PROTECT_ANONYMOUS_VOTING = false;
  public static final boolean PROTECT_ANONYMOUS_REACTIONS = false;
  public static final boolean DISABLE_ANONYMOUS_NON_OWNER_REACTIONS = true;

  public static final boolean KEEP_ORIGINAL_EMOJI_WHEN_INPUT_CUSTOM_EMOJI = true;
  public static final boolean FORCE_REPLY_WHEN_FORWARDING_WITH_COMMENT = false;
  public static final boolean DEBUG_VIEW_MESSAGES = false;
  public static final boolean ENABLE_TEXT_ANIMATIONS = false;

  public static final boolean COMPILE_CHECK = false /*never set to true*/;
}
