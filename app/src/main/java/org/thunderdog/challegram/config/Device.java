package org.thunderdog.challegram.config;

import android.os.Build;

import me.vkryl.core.StringUtils;

/**
 * Date: 9/24/17
 * Author: default
 */

@SuppressWarnings("SpellCheckingInspection")
public class Device {
  public static final int UNKNOWN = 0;

  public static final int MANUFACTURER;
  public static final int SAMSUNG = 0x01;
  public static final int GOOGLE = 0x02;
  public static final int AMAZON = 0x03;
  public static final int ASUS = 0x04;
  public static final int HTC = 0x05;
  public static final int HUAWEI = 0x06;
  public static final int LG = 0x07;
  public static final int MOTOROLA = 0x08;
  public static final int NVIDIA = 0x09;
  public static final int SONY = 0x10;
  public static final int ONEPLUS = 0x11;
  public static final int XIAOMI = 0x12;
  public static final int ZTE = 0x13;
  public static final int BLACKBERRY = 0x14;

  public static final int PRODUCT;
  public static final int SAMSUNG_GALAXY_S8 = 1;
  public static final int SAMSUNG_GALAXY_S8_PLUS = 2;
  public static final int SAMSUNG_GALAXY_S9 = 3;
  public static final int SAMSUNG_GALAXY_S9_PLUS = 4;
  public static final int SAMSUNG_GALAXY_S10 = 5;
  public static final int SAMSUNG_GALAXY_S10_PLUS = 6;
  public static final int LGE_G3 = 10;
  public static final int ASUS_ZENFONE_3 = 20;

  static {
    MANUFACTURER = parseManufacturer(Build.MANUFACTURER, Build.BRAND);
    PRODUCT = parseProduct(MANUFACTURER, Build.PRODUCT);
  }

  private static int parseManufacturer (String manufacturer, String brand) {
    if (StringUtils.isEmpty(manufacturer)) {
      return UNKNOWN;
    }
    manufacturer = manufacturer.toLowerCase();
    brand = StringUtils.isEmpty(brand) ? null : brand.toLowerCase();

    switch (manufacturer) {
      case "google":
        return GOOGLE;
      case "samsung":
        return SAMSUNG;
      case "amazon":
        return AMAZON;
      case "asus":
        return ASUS;
      case "lge":
        return LG;
      case "huawei":
        return HUAWEI;
      case "htc":
        return HTC;
      case "motorola":
        return MOTOROLA;
      case "sony":
      case "sony ericsson":
        return SONY;
      case "oneplus":
        return ONEPLUS;
      case "nvidia":
        return NVIDIA;
      case "xiaomi":
        return XIAOMI;
      case "zte":
        return ZTE;
      case "rim":
        return "blackberry".equals(brand) ? BLACKBERRY : UNKNOWN;
    }

    return UNKNOWN;
  }

  private static int parseProduct (int manufacturer, String product) {
    if (manufacturer == UNKNOWN || StringUtils.isEmpty(product)) {
      return UNKNOWN;
    }
    product = product.toLowerCase();

    switch (manufacturer) {
      case SAMSUNG:
        switch (product) {
          case "dreamltexx":
            return SAMSUNG_GALAXY_S8;
          case "dreamlteks":
            return SAMSUNG_GALAXY_S8_PLUS;
          case "star2ltekx":
          case "starltexx":
          case "starltekx":
            return SAMSUNG_GALAXY_S9;
          case "star2ltexx":
          case "star2qlteue":
            return SAMSUNG_GALAXY_S9_PLUS;
          case "beyond1ltexx":
            return SAMSUNG_GALAXY_S9;
          case "beyond2ltexx":
          case "beyond2lteks":
            return SAMSUNG_GALAXY_S10_PLUS;
        }
        break;
      case GOOGLE:
        break;
      case AMAZON:
        break;
      case ASUS:
        switch (product) {
          case "ww_phone":
          case "asus_z010dd":
            return ASUS_ZENFONE_3;
        }
        break;
      case HTC:
        break;
      case HUAWEI:
        break;
      case LG:
        break;
      case MOTOROLA:
        break;
      case NVIDIA:
        break;
      case SONY:
        break;
      case ONEPLUS:
        break;
      case XIAOMI:
        break;
    }

    return UNKNOWN;
  }

  // Specific

  public static final boolean IS_SAMSUNG = MANUFACTURER == SAMSUNG;

  public static final boolean IS_XIAOMI = MANUFACTURER == XIAOMI;

  public static final boolean IS_SAMSUNG_SGS_FAMILY = PRODUCT == SAMSUNG_GALAXY_S8 || PRODUCT == SAMSUNG_GALAXY_S8_PLUS || PRODUCT == SAMSUNG_GALAXY_S9 || PRODUCT == SAMSUNG_GALAXY_S9_PLUS || PRODUCT == SAMSUNG_GALAXY_S10 || PRODUCT == SAMSUNG_GALAXY_S10_PLUS;
  public static final boolean IS_SAMSUNG_SGS8 = MANUFACTURER == SAMSUNG && (PRODUCT == SAMSUNG_GALAXY_S8);

  public static final long FORCE_VIBRATE_DURATION = 7;
  public static final long FORCE_VIBRATE_OPEN_DURATION = 10;

  // Feature flags

  public static final boolean NEED_BIGGER_BUBBLE_OFFSETS = IS_SAMSUNG_SGS8;
  public static final boolean NEED_FORCE_TOUCH_ROOT_INSETS = IS_SAMSUNG_SGS8;
  public static final boolean NEED_LIGHT_NAVIGATION_COLOR = IS_SAMSUNG_SGS8;
  public static final boolean NEED_REDUCE_BOUNCE = IS_SAMSUNG;

  public static final boolean NEED_LESS_PREVIEW_MARGINS = IS_SAMSUNG_SGS8;
  public static final boolean DISABLE_FULLSCREEN_PREVIEW = IS_SAMSUNG_SGS_FAMILY;

  public static final boolean HIDE_POPUP_KEYBOARD = MANUFACTURER == BLACKBERRY && Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP;

  public static final boolean NEED_HQ_ROUND_VIDEOS = IS_SAMSUNG_SGS_FAMILY || StringUtils.equalsOrBothEmpty(Build.DEVICE, "zeroflte") || StringUtils.equalsOrBothEmpty(Build.DEVICE, "zenlte");

  public static final boolean HAS_BUGGY_REGION_DECODER = MANUFACTURER == ASUS && (PRODUCT == ASUS_ZENFONE_3 || (Build.MODEL != null && StringUtils.equalsOrBothEmpty(Build.MODEL, "ASUS_Z010DD")));

  public static final boolean NEED_ADD_KEYBOARD_SIZE = IS_SAMSUNG_SGS_FAMILY;

  public static final boolean ROUND_NOTIFICAITON_IMAGE = true; //MANUFACTURER != XIAOMI;

  public static final boolean FLYME = !StringUtils.isEmpty(Build.DISPLAY) && Build.DISPLAY.toLowerCase().contains("flyme");
}
