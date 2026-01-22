/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.drm;

import static androidx.media3.exoplayer.drm.DrmUtil.executePost;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.drm.ExoMediaDrm.KeyRequest;
import androidx.media3.exoplayer.drm.ExoMediaDrm.ProvisionRequest;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A {@link MediaDrmCallback} that makes requests using {@link DataSource} instances. */
@UnstableApi
public final class HttpMediaDrmCallback implements MediaDrmCallback {
  private final DataSource.Factory dataSourceFactory;
  @Nullable private final String defaultLicenseUrl;
  private final boolean forceDefaultLicenseUrl;
  private final Map<String, String> keyRequestProperties;

  /**
   * Constructs an instance.
   *
   * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
   *     their own license URL. May be {@code null} if it's known that all key requests will specify
   *     their own URLs.
   * @param dataSourceFactory A factory from which to obtain {@link DataSource} instances. This will
   *     usually be an HTTP-based {@link DataSource}.
   */
  public HttpMediaDrmCallback(
      @Nullable String defaultLicenseUrl, DataSource.Factory dataSourceFactory) {
    this(defaultLicenseUrl, /* forceDefaultLicenseUrl= */ false, dataSourceFactory);
  }

  /**
   * Constructs an instance.
   *
   * @param defaultLicenseUrl The default license URL. Used for key requests that do not specify
   *     their own license URL, or for all key requests if {@code forceDefaultLicenseUrl} is set to
   *     true. May be {@code null} if {@code forceDefaultLicenseUrl} is {@code false} and if it's
   *     known that all key requests will specify their own URLs.
   * @param forceDefaultLicenseUrl Whether to force use of {@code defaultLicenseUrl} for key
   *     requests that include their own license URL.
   * @param dataSourceFactory A factory from which to obtain {@link DataSource} instances. This will
   *     * usually be an HTTP-based {@link DataSource}.
   */
  public HttpMediaDrmCallback(
      @Nullable String defaultLicenseUrl,
      boolean forceDefaultLicenseUrl,
      DataSource.Factory dataSourceFactory) {
    Assertions.checkArgument(!(forceDefaultLicenseUrl && TextUtils.isEmpty(defaultLicenseUrl)));
    this.dataSourceFactory = dataSourceFactory;
    this.defaultLicenseUrl = defaultLicenseUrl;
    this.forceDefaultLicenseUrl = forceDefaultLicenseUrl;
    this.keyRequestProperties = new HashMap<>();
  }

  /**
   * Sets a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  public void setKeyRequestProperty(String name, String value) {
    Assertions.checkNotNull(name);
    Assertions.checkNotNull(value);
    synchronized (keyRequestProperties) {
      keyRequestProperties.put(name, value);
    }
  }

  /**
   * Clears a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   */
  public void clearKeyRequestProperty(String name) {
    Assertions.checkNotNull(name);
    synchronized (keyRequestProperties) {
      keyRequestProperties.remove(name);
    }
  }

  /** Clears all headers for key requests made by the callback. */
  public void clearAllKeyRequestProperties() {
    synchronized (keyRequestProperties) {
      keyRequestProperties.clear();
    }
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request)
      throws MediaDrmCallbackException {
    String url =
        request.getDefaultUrl() + "&signedRequest=" + Util.fromUtf8Bytes(request.getData());
    return executePost(
        dataSourceFactory.createDataSource(),
        url,
        /* httpBody= */ null,
        /* requestProperties= */ Collections.emptyMap());
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws MediaDrmCallbackException {
    String url = request.getLicenseServerUrl();
    if (forceDefaultLicenseUrl || TextUtils.isEmpty(url)) {
      url = defaultLicenseUrl;
    }
    if (TextUtils.isEmpty(url)) {
      throw new MediaDrmCallbackException(
          new DataSpec.Builder().setUri(Uri.EMPTY).build(),
          Uri.EMPTY,
          /* responseHeaders= */ ImmutableMap.of(),
          /* bytesLoaded= */ 0,
          /* cause= */ new IllegalStateException("No license URL"));
    }
    Map<String, String> requestProperties = new HashMap<>();
    // Add standard request properties for supported schemes.
    String contentType =
        C.PLAYREADY_UUID.equals(uuid)
            ? "text/xml"
            : (C.CLEARKEY_UUID.equals(uuid) ? "application/json" : "application/octet-stream");
    requestProperties.put("Content-Type", contentType);
    if (C.PLAYREADY_UUID.equals(uuid)) {
      requestProperties.put(
          "SOAPAction", "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
    }
    // Add additional request properties.
    synchronized (keyRequestProperties) {
      requestProperties.putAll(keyRequestProperties);
    }
    return executePost(
        dataSourceFactory.createDataSource(),
        url,
        /* httpBody= */ request.getData(),
        requestProperties);
  }
}
