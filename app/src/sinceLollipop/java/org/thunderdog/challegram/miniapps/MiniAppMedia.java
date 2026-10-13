/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import me.vkryl.core.lambda.RunnableInt;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Origin-bound HTML media prompts and file picker callbacks. Unknown resources are denied. */
final class MiniAppMedia {
  private final MiniAppController host;
  private PermissionRequest media;
  private GeolocationPermissions.Callback geolocation;
  private String geolocationOrigin;
  private ValueCallback<Uri[]> files;
  private int activityCode;
  private static int nextCode = 0x6200;
  private final List<File> captures = new ArrayList<>();

  MiniAppMedia (MiniAppController host) { this.host = host; }
  void permission (PermissionRequest request) {
    MiniAppDocument.Request origin = host.webRequest(request.getOrigin().toString());
    if (origin == null || media != null) { request.deny(); return; }
    ArrayList<String> resources = new ArrayList<>(), permissions = new ArrayList<>();
    for (String resource : request.getResources()) {
      if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) { resources.add(resource); permissions.add(Manifest.permission.CAMERA); }
      else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) { resources.add(resource); permissions.add(Manifest.permission.RECORD_AUDIO); }
    }
    if (resources.isEmpty()) { request.deny(); return; }
    // The native verification introduction already obtained camera consent, as in Telegram Android.
    // Keep this exception limited to the current verification document and the camera permission.
    if (host.isVerifyingAge() && request.getResources().length == 1 && resources.size() == 1 &&
        PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resources.get(0)) &&
        androidx.core.content.ContextCompat.checkSelfPermission(host.context(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      request.grant(new String[] {PermissionRequest.RESOURCE_VIDEO_CAPTURE});
      return;
    }
    media = request;
    String description = Lang.getString(resources.size() == 2 ? R.string.MiniAppCameraMicrophone :
      resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ? R.string.MiniAppCamera : R.string.MiniAppMicrophone,
      host.botName(), request.getOrigin().toString());
    host.nativePrompt(origin, host.botName(), description, R.string.Allow, null, () -> {
      RunnableInt done = ignored -> {
        if (media != request) return;
        media = null;
        ArrayList<String> granted = new ArrayList<>();
        if (host.current(origin) && host.isShowing()) for (int i = 0; i < resources.size(); i++) {
          if (androidx.core.content.ContextCompat.checkSelfPermission(host.context(), permissions.get(i)) == PackageManager.PERMISSION_GRANTED) granted.add(resources.get(i));
        }
        if (granted.isEmpty()) request.deny(); else request.grant(granted.toArray(new String[0]));
      };
      if (!host.context().permissions().requestPermissions(done, permissions.toArray(new String[0]))) done.runWithInt(0);
    }, () -> { if (media == request) { media = null; request.deny(); } });
  }
  void cancelled (PermissionRequest request) { if (media == request) media = null; }
  void geolocation (String source, GeolocationPermissions.Callback callback) {
    MiniAppDocument.Request origin = host.webRequest(source);
    if (origin == null || geolocation != null) { callback.invoke(source, false, false); return; }
    geolocation = callback; geolocationOrigin = source;
    host.location().consent(origin, source, allowed -> {
      if (geolocation != callback) return;
      geolocation = null; geolocationOrigin = null;
      callback.invoke(source, allowed && host.current(origin), false);
    });
  }
  void hideGeolocation () {
    if (geolocation != null) { GeolocationPermissions.Callback callback = geolocation; geolocation = null; callback.invoke(geolocationOrigin, false, false); geolocationOrigin = null; }
  }

  boolean choose (String url, ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
    cancelFiles();
    MiniAppDocument.Request origin = host.webRequest(url);
    if (origin == null) { callback.onReceiveValue(null); return true; }
    files = callback;
    final boolean multiple = params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
    List<String> types = new ArrayList<>();
    for (String accept : params.getAcceptTypes()) for (String part : accept.split(",")) {
      String type = part.trim();
      if (type.startsWith(".")) type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1).toLowerCase(java.util.Locale.US));
      if (type != null && type.matches("[a-zA-Z0-9!#$&^_.+*-]+/[a-zA-Z0-9!#$&^_.+*-]+")) types.add(type);
    }
    if (types.isEmpty()) types.add("*/*");
    boolean capture = params.isCaptureEnabled() && !multiple && types.size() == 1 && (types.get(0).startsWith("image/") || types.get(0).startsWith("video/"));
    Runnable launch = () -> {
      if (files != callback || !host.current(origin)) { cancelFiles(); return; }
      activityCode = ++nextCode;
      Uri output = null;
      try {
        Intent intent;
        if (capture) {
          boolean video = types.get(0).startsWith("video/");
          File directory = new File(host.context().getCacheDir(), "miniapps-capture");
          if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException();
          File file = File.createTempFile("capture-", video ? ".mp4" : ".jpg", directory); captures.add(file);
          output = org.thunderdog.challegram.FileProvider.getUriForFile(host.context(), host.context().getPackageName() + ".provider", file);
          intent = new Intent(video ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
          intent.setClipData(ClipData.newRawUri("capture", output));
        } else {
          intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(types.size() == 1 ? types.get(0) : "*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
          if (types.size() > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        }
        final Uri captured = output;
        host.context().putActivityResultHandler(activityCode, (code, result, data) -> {
          if (files != callback) return;
          files = null;
          host.context().putActivityResultHandler(activityCode, null);
          Uri[] chosen = result == Activity.RESULT_OK && host.current(origin) ?
            captured != null ? new Uri[] { captured } : pickerUris(data, multiple) : null;
          // Only picker-issued content URIs. Never let a forged result upload private file:// paths.
          if (chosen != null) for (Uri uri : chosen) if (uri == null || !"content".equals(uri.getScheme()) ||
            host.context().getPackageName().concat(".provider").equals(uri.getAuthority()) && !uri.equals(captured)) { chosen = null; break; }
          if (chosen != null && (chosen.length > 100 || !multiple && chosen.length > 1)) chosen = null;
          callback.onReceiveValue(chosen);
        });
        host.context().startActivityForResult(intent, activityCode);
      } catch (Exception error) { cancelFiles(); }
    };
    if (capture) {
      if (!host.context().permissions().requestAccessCameraPermission(allowed -> { if (allowed) launch.run(); else cancelFiles(); })) launch.run();
    } else launch.run();
    return true;
  }
  static Uri[] pickerUris (Intent data, boolean multiple) {
    if (data == null) return null;
    ClipData clip = data.getClipData();
    if (clip != null) {
      if (clip.getItemCount() == 0 || clip.getItemCount() > 100 || !multiple && clip.getItemCount() > 1) return null;
      Uri[] result = new Uri[clip.getItemCount()];
      for (int i = 0; i < result.length; i++) result[i] = clip.getItemAt(i).getUri();
      return result;
    }
    return data.getData() != null ? new Uri[] {data.getData()} : null;
  }
  private void cancelFiles () {
    if (files != null) { ValueCallback<Uri[]> callback = files; files = null; host.context().putActivityResultHandler(activityCode, null); callback.onReceiveValue(null); }
  }
  void cancel () {
    cancelFiles(); hideGeolocation();
    if (media != null) { PermissionRequest request = media; media = null; request.deny(); }
  }
  void destroy () {
    cancel();
    for (File file : captures) {
      try { Uri uri = org.thunderdog.challegram.FileProvider.getUriForFile(host.context(), host.context().getPackageName() + ".provider", file); host.context().revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (RuntimeException ignored) { }
      file.delete();
    }
    captures.clear();
  }
}
