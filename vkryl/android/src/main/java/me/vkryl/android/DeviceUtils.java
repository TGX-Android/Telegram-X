package me.vkryl.android;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;

import java.io.File;

public final class DeviceUtils {
  private DeviceUtils () { }

  /**
   * Detects if app is currently running on emulator, or real device.
   * @return true for emulator, false for real devices
   *
   * See https://github.com/gingo/android-emulator-detector
   */
  public static boolean detectEmulator (Activity context) {
    if (isTestLabDevice(context))
      return true;

    // TODO improve emulation detection

    int rating = 0;

    if (Build.PRODUCT.contains("sdk") ||
      Build.PRODUCT.contains("Andy") ||
      Build.PRODUCT.contains("ttVM_Hdragon") ||
      Build.PRODUCT.contains("google_sdk") ||
      Build.PRODUCT.contains("Droid4X") ||
      Build.PRODUCT.contains("nox") ||
      Build.PRODUCT.contains("sdk_x86") ||
      Build.PRODUCT.contains("sdk_google") ||
      Build.PRODUCT.contains("vbox86p")) {
      rating++;
    }

    if (Build.MANUFACTURER.equals("unknown") ||
      Build.MANUFACTURER.equals("Genymotion") ||
      Build.MANUFACTURER.contains("Andy") ||
      Build.MANUFACTURER.contains("MIT") ||
      Build.MANUFACTURER.contains("nox") ||
      Build.MANUFACTURER.contains("TiantianVM")){
      rating++;
    }

    if (Build.BRAND.equals("generic") ||
      Build.BRAND.equals("generic_x86") ||
      Build.BRAND.equals("TTVM") ||
      Build.BRAND.contains("Andy")) {
      rating++;
    }

    if (Build.DEVICE.contains("generic") ||
      Build.DEVICE.contains("generic_x86") ||
      Build.DEVICE.contains("Andy") ||
      Build.DEVICE.contains("ttVM_Hdragon") ||
      Build.DEVICE.contains("Droid4X") ||
      Build.DEVICE.contains("nox") ||
      Build.DEVICE.contains("generic_x86_64") ||
      Build.DEVICE.contains("vbox86p")) {
      rating++;
    }

    if (Build.MODEL.equals("sdk") ||
      Build.MODEL.equals("google_sdk") ||
      Build.MODEL.contains("Droid4X") ||
      Build.MODEL.contains("TiantianVM") ||
      Build.MODEL.contains("Andy") ||
      Build.MODEL.equals("Android SDK built for x86_64") ||
      Build.MODEL.equals("Android SDK built for x86")) {
      rating++;
    }

    if (Build.HARDWARE.equals("goldfish") ||
      Build.HARDWARE.equals("vbox86") ||
      Build.HARDWARE.contains("nox") ||
      Build.HARDWARE.contains("ttVM_x86")) {
      rating++;
    }

    if (Build.FINGERPRINT.contains("generic/sdk/generic") ||
      Build.FINGERPRINT.contains("generic_x86/sdk_x86/generic_x86") ||
      Build.FINGERPRINT.contains("Andy") ||
      Build.FINGERPRINT.contains("ttVM_Hdragon") ||
      Build.FINGERPRINT.contains("generic_x86_64") ||
      Build.FINGERPRINT.contains("generic/google_sdk/generic") ||
      Build.FINGERPRINT.contains("vbox86p") ||
      Build.FINGERPRINT.contains("generic/vbox86p/vbox86p")) {
      rating++;
    }

    if (rating > 3) {
      return true;
    }

    /*FIXME?
    try {
      String opengl = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
      if (opengl != null && (opengl.contains("Bluestacks") || opengl.contains("Translator"))) {
        return true;
      }
    } catch (Throwable t) {
      Log.i(t);
    }*/

    try {
      File sharedFolder = new File(Environment
        .getExternalStorageDirectory().toString()
        + File.separatorChar
        + "windows"
        + File.separatorChar
        + "BstSharedFolder");

      if (sharedFolder.exists()) {
        return true;
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }

    return false;
  }

  public static boolean isTestLabDevice (Context context) {
    try {
      String testLabSetting = android.provider.Settings.System.getString(context.getContentResolver(), "firebase.test.lab");
      return "true".equals(testLabSetting);
    } catch (Throwable ignored) { }
    return false;
  }

  public static boolean isAppInstalled (Context context, String packageName) {
    try {
      ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
      return info != null;
    } catch (Throwable t) {
      return false;
    }
  }
}
