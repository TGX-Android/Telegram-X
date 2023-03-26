package org.thunderdog.challegram.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.tool.Intents;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.FutureBoolWithArg;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableInt;

public class Permissions {
  @IntDef({
    ReadType.ALL,
    ReadType.IMAGES,
    ReadType.VIDEOS,
    ReadType.IMAGES_AND_VIDEOS,
    ReadType.AUDIO,
    ReadType.EXTERNAL_FILE,
    ReadType.EXTERNAL_IMAGES,
    ReadType.EXTERNAL_AUDIO,
    ReadType.EXTERNAL_CAMERA_PHOTO,
    ReadType.EXTERNAL_CAMERA_VIDEO
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface ReadType {
    int
      ALL = 0,
      IMAGES = 1,
      VIDEOS = 2,
      IMAGES_AND_VIDEOS = 3,
      AUDIO = 4,
      EXTERNAL_FILE = 5,
      EXTERNAL_IMAGES = 6,
      EXTERNAL_AUDIO = 7,
      EXTERNAL_CAMERA_PHOTO = 8,
      EXTERNAL_CAMERA_VIDEO = 9;
  }

  @IntDef({
    GrantResult.ALL,
    GrantResult.PARTIAL,
    GrantResult.NONE
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface GrantResult {
    int ALL = 0, PARTIAL = 1, NONE = 2;
  }

  @IntDef({
    WriteType.DOWNLOADS,
    WriteType.GALLERY
  })
  public @interface WriteType {
    int DOWNLOADS = 0, GALLERY = 1;
  }

  private final BaseActivity context;

  public Permissions (BaseActivity context) {
    this.context = context;
  }

  public boolean checkPermission (String permission) {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
  }

  @GrantResult
  public int checkPermissions (String... permissions) {
    int grantCount = 0;
    for (String permission : permissions) {
      if (checkPermission(permission)) {
        grantCount++;
      }
    }
    if (grantCount == permissions.length) {
      return GrantResult.ALL;
    } else if (grantCount > 0) {
      return GrantResult.PARTIAL;
    } else {
      return GrantResult.NONE;
    }
  }

  public boolean requestPermissions (@Nullable RunnableBool after, String... permissions) {
    if (after != null) {
      return requestPermissions((RunnableInt) grantResult -> after.runWithBool(grantResult == GrantResult.ALL), permissions);
    } else {
      return requestPermissions((RunnableInt) null, permissions);
    }
  }

  public boolean requestPermissions (@Nullable RunnableInt after, String... permissions) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return false;
    }
    List<String> missingPermissions = null;
    for (String permission : permissions) {
      if (!checkPermission(permission)) {
        if (missingPermissions == null) {
          missingPermissions = new ArrayList<>();
        }
        missingPermissions.add(permission);
      }
    }
    if (missingPermissions == null || missingPermissions.isEmpty()) {
      return false;
    }
    context.requestCustomPermissions(missingPermissions.toArray(new String[0]), (code, permissions1, grantResults, grantCount) -> {
      if (after != null) {
        after.runWithInt(
          grantCount == permissions1.length ? GrantResult.ALL :
          grantCount > 0 ? GrantResult.PARTIAL :
          GrantResult.NONE
        );
      }
    });
    return true;
  }

  public boolean requestReadExternalStorage (File file, @Nullable RunnableBool after) {
    return requestReadExternalStorage(ReadType.EXTERNAL_FILE, grantResult -> {
      if (after != null) {
        after.runWithBool(grantResult == GrantResult.ALL);
      }
    });
  }

  public boolean requestReadExternalStorage (@ReadType int type, @Nullable RunnableInt after) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      // No permission management before Marshmallow
      return false;
    }
    List<String> permissions = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      switch (type) {
        case ReadType.ALL:
          permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
          permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
          permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
          break;
        case ReadType.IMAGES:
          permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
          break;
        case ReadType.VIDEOS:
          permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
          break;
        case ReadType.IMAGES_AND_VIDEOS:
          permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
          permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
          break;
        case ReadType.AUDIO:
          permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
          break;
        case ReadType.EXTERNAL_FILE:
        case ReadType.EXTERNAL_IMAGES:
        case ReadType.EXTERNAL_AUDIO:
        case ReadType.EXTERNAL_CAMERA_PHOTO:
        case ReadType.EXTERNAL_CAMERA_VIDEO:
          // No permissions required?
          break;
        default:
          throw new IllegalArgumentException(Integer.toString(type));
      }
    } else {
      permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
    }
    if (permissions.isEmpty()) {
      return false;
    }
    List<String> missingPermissions = new ArrayList<>();
    for (String permission : permissions) {
      if (!checkPermission(permission)) {
        missingPermissions.add(permission);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !missingPermissions.isEmpty()) {
      if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
        return false;
      }
    }
    if (missingPermissions.isEmpty()) {
      return false;
    }
    RunnableBool onResult = granted -> {
      if (after != null) {
        if (granted) {
          after.runWithInt(GrantResult.ALL);
        } else {
          @GrantResult int grantResult = checkPermissions(permissions.toArray(new String[0]));
          after.runWithInt(grantResult);
        }
      }
    };
    context.requestCustomPermissions(missingPermissions.toArray(new String[0]), (code, requestedPermissions, grantResults, grantCount) -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && grantCount != requestedPermissions.length) {
        context.requestCustomPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, (code1, requestedPermissions1, grantResults1, grantCount1) -> {
          onResult.runWithBool(grantCount1 == requestedPermissions1.length);
        });
      } else {
        onResult.runWithBool(requestedPermissions.length == grantCount);
      }
    });
    return true;
  }

  public boolean requestWriteExternalStorage (@WriteType int type, @Nullable RunnableBool after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // WRITE_EXTERNAL_STORAGE no longer gives any effect
      return false;
    } else {
      return requestPermissions(after, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
  }

  public boolean requestPostNotifications (@Nullable RunnableBool after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return requestPermissions(after, Manifest.permission.POST_NOTIFICATIONS);
    } else {
      return false;
    }
  }

  public boolean requestReadContacts (@Nullable RunnableBool after) {
    return requestPermissions(after, Manifest.permission.READ_CONTACTS);
  }

  public boolean requestForegroundService (@NonNull RunnableBool after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return requestPermissions(after, Manifest.permission.FOREGROUND_SERVICE);
    } else {
      return false;
    }
  }

  public boolean canManageStorage () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return
        Environment.isExternalStorageLegacy() ||
        Environment.isExternalStorageManager() ||
        checkPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Q allows for requestExternalStorage ?
    }
    return true;
  }

  public boolean requestManageStorage (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !canManageStorage()) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      intent.setData(Uri.parse("package:" + context.getPackageName()));
      ((Activity) context).startActivityForResult(intent, Intents.ACTIVITY_RESULT_MANAGE_STORAGE);
      return true;
    }
    return false;
  }

  private boolean shouldShowPermissionRationale (String permission) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return context.shouldShowRequestPermissionRationale(permission);
    } else {
      return false;
    }
  }

  public boolean requestRecordAudioPermissions (@Nullable RunnableBool after) {
    return requestPermissions(after, Manifest.permission.RECORD_AUDIO);
  }

  public boolean requestRecordVideoPermissions (@Nullable RunnableBool after) {
    return requestPermissions(after, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
  }

  public boolean requestAccessCameraPermission (@Nullable RunnableBool after) {
    return requestPermissions(after, Manifest.permission.CAMERA);
  }

  private boolean requestExternalStoragePermission (@ReadType int readType, @NonNull FutureBoolWithArg<RunnableBool> mainPermissionRequest, @Nullable RunnableBool after) {
    if (mainPermissionRequest.getBoolValue((granted) -> {
      if (granted) {
        requestExternalStoragePermission(readType, mainPermissionRequest, after);
      } else if (after != null) {
        after.runWithBool(false);
      }
    })) {
      return true;
    }
    return requestReadExternalStorage(readType, (grantType) -> {
      if (after != null) {
        after.runWithBool(grantType == GrantResult.ALL);
      }
    });
  }

  public boolean requestExternalRecordVideoPermissions (@Nullable RunnableBool after) {
    return requestExternalStoragePermission(ReadType.EXTERNAL_CAMERA_VIDEO, this::requestRecordVideoPermissions, after);
  }

  public boolean requestExternalAccessCameraPermission (@Nullable RunnableBool after) {
    return requestExternalStoragePermission(ReadType.EXTERNAL_CAMERA_PHOTO, this::requestAccessCameraPermission, after);
  }

  public boolean shouldShowReadContactsRationale () {
    return shouldShowPermissionRationale(Manifest.permission.READ_CONTACTS);
  }

  public boolean shouldShowAccessLocationRationale () {
    return
      shouldShowPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ||
      shouldShowPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
  }

  public boolean shouldShowBackgroundLocationRationale () {
    // Build.VERSION_CODES.Q: permission introduced, but automatically granted after request
    // Build.VERSION_CODES.R+: proper permission request prompt required
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && shouldShowPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
  }

  // can

  public boolean canReadContacts () {
    return checkPermission(Manifest.permission.READ_CONTACTS);
  }

  private boolean canReadExternalStorage () {
    return checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
  }

  public boolean canRequestDownloadsAccess () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return canManageStorage();
    } else {
      // Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> downloads can be queried through content provider
      // pre-Build.VERSION_CODES.R -> direct access to downloads folder
      return true;
    }
  }

  public boolean canAccessLocation () {
    return checkPermissions(
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) != GrantResult.NONE;
  }

  public boolean canAccessLocationInBackground () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    } else {
      return true;
    }
  }

  public boolean canAccessCamera () {
    return checkPermission(Manifest.permission.CAMERA);
  }

  public boolean canRecordAudio () {
    return checkPermission(Manifest.permission.RECORD_AUDIO);
  }

  public boolean canRecordVideo () {
    return canAccessCamera() && canRecordAudio();
  }
}
