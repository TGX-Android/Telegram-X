/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotEmpty;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.app.BundleCompat;
import androidx.media3.common.BundleListRetriever;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A result to be used with {@link ListenableFuture} for asynchronous calls between {@link
 * MediaLibraryService.MediaLibrarySession} and {@link MediaBrowser}.
 */
public final class LibraryResult<V> {

  /** Result codes. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    RESULT_SUCCESS,
    SessionError.INFO_CANCELLED,
    SessionError.ERROR_UNKNOWN,
    SessionError.ERROR_INVALID_STATE,
    SessionError.ERROR_BAD_VALUE,
    SessionError.ERROR_PERMISSION_DENIED,
    SessionError.ERROR_IO,
    SessionError.ERROR_SESSION_DISCONNECTED,
    SessionError.ERROR_NOT_SUPPORTED,
    SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
    SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
    SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT,
    SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED,
    SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION,
    SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED,
    SessionError.ERROR_SESSION_SETUP_REQUIRED
  })
  public @interface Code {}

  /**
   * Result code representing that the command is successfully completed.
   *
   * <p>Interoperability: This code is also used to tell that the command was successfully sent, but
   * the result is unknown when connected with {@code
   * android.support.v4.media.session.MediaSessionCompat} or {@code
   * android.support.v4.media.session.MediaControllerCompat}.
   */
  public static final int RESULT_SUCCESS = 0;

  /** Result code representing that the command is skipped. */
  public static final int RESULT_INFO_SKIPPED = SessionError.INFO_CANCELLED;

  /** Result code representing that the command is ended with an unknown error. */
  public static final int RESULT_ERROR_UNKNOWN = SessionError.ERROR_UNKNOWN;

  /**
   * Result code representing that the command cannot be completed because the current state is not
   * valid for the command.
   */
  public static final int RESULT_ERROR_INVALID_STATE = SessionError.ERROR_INVALID_STATE;

  /** Result code representing that an argument is illegal. */
  public static final int RESULT_ERROR_BAD_VALUE = SessionError.ERROR_BAD_VALUE;

  /** Result code representing that the command is not allowed. */
  public static final int RESULT_ERROR_PERMISSION_DENIED = SessionError.ERROR_PERMISSION_DENIED;

  /** Result code representing that a file or network related error happened. */
  public static final int RESULT_ERROR_IO = SessionError.ERROR_IO;

  /** Result code representing that the command is not supported. */
  public static final int RESULT_ERROR_NOT_SUPPORTED = SessionError.ERROR_NOT_SUPPORTED;

  /** Result code representing that the session and controller were disconnected. */
  public static final int RESULT_ERROR_SESSION_DISCONNECTED =
      SessionError.ERROR_SESSION_DISCONNECTED;

  /** Result code representing that the authentication has expired. */
  public static final int RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED =
      SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED;

  /** Result code representing that a premium account is required. */
  public static final int RESULT_ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED =
      SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED;

  /** Result code representing that too many concurrent streams are detected. */
  public static final int RESULT_ERROR_SESSION_CONCURRENT_STREAM_LIMIT =
      SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT;

  /** Result code representing that the content is blocked due to parental controls. */
  public static final int RESULT_ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED =
      SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED;

  /** Result code representing that the content is blocked due to being regionally unavailable. */
  public static final int RESULT_ERROR_SESSION_NOT_AVAILABLE_IN_REGION =
      SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION;

  /**
   * Result code representing that the application cannot skip any more because the skip limit is
   * reached.
   */
  public static final int RESULT_ERROR_SESSION_SKIP_LIMIT_REACHED =
      SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED;

  /** Result code representing that the session needs user's manual intervention. */
  public static final int RESULT_ERROR_SESSION_SETUP_REQUIRED =
      SessionError.ERROR_SESSION_SETUP_REQUIRED;

  /** The {@link Code} of this result. */
  public final @Code int resultCode;

  /**
   * The completion time of the command in milliseconds. It's the same as {@link
   * SystemClock#elapsedRealtime()} when the command is completed.
   */
  public final long completionTimeMs;

  /**
   * The value of this result. Will be {@code null} if {@link #resultCode} is not {@link
   * #RESULT_SUCCESS}.
   */
  @Nullable public final V value;

  private final @ValueType int valueType;

  /** The optional parameters. */
  @Nullable public final MediaLibraryService.LibraryParams params;

  /** The optional session error. */
  @UnstableApi @Nullable public final SessionError sessionError;

  /** Creates an instance with {@link #resultCode}{@code ==}{@link #RESULT_SUCCESS}. */
  public static LibraryResult<Void> ofVoid() {
    return new LibraryResult<>(
        RESULT_SUCCESS,
        SystemClock.elapsedRealtime(),
        /* params= */ null,
        /* sessionError= */ null,
        /* value= */ null,
        VALUE_TYPE_VOID);
  }

  /**
   * Creates an instance with {@link #resultCode}{@code ==}{@link #RESULT_SUCCESS} and optional
   * {@link LibraryParams params}.
   */
  public static LibraryResult<Void> ofVoid(@Nullable LibraryParams params) {
    return new LibraryResult<>(
        RESULT_SUCCESS,
        SystemClock.elapsedRealtime(),
        params,
        /* sessionError= */ null,
        /* value= */ null,
        VALUE_TYPE_VOID);
  }

  /**
   * Creates an instance with a media item and {@link #resultCode}{@code ==}{@link #RESULT_SUCCESS}.
   *
   * <p>The {@link MediaItem#mediaMetadata} must specify {@link MediaMetadata#isBrowsable} and
   * {@link MediaMetadata#isPlayable} fields.
   *
   * @param item The media item.
   * @param params The optional parameters to describe the media item.
   */
  public static LibraryResult<MediaItem> ofItem(MediaItem item, @Nullable LibraryParams params) {
    verifyMediaItem(item);
    return new LibraryResult<>(
        RESULT_SUCCESS,
        SystemClock.elapsedRealtime(),
        params,
        /* sessionError= */ null,
        item,
        VALUE_TYPE_ITEM);
  }

  /**
   * Creates an instance with a list of media items and {@link #resultCode}{@code ==}{@link
   * #RESULT_SUCCESS}.
   *
   * <p>The {@link MediaItem#mediaMetadata} of each item in the list must specify {@link
   * MediaMetadata#isBrowsable} and {@link MediaMetadata#isPlayable} fields.
   *
   * @param items The list of media items.
   * @param params The optional parameters to describe the list of media items.
   */
  public static LibraryResult<ImmutableList<MediaItem>> ofItemList(
      List<MediaItem> items, @Nullable LibraryParams params) {
    for (MediaItem item : items) {
      verifyMediaItem(item);
    }
    return new LibraryResult<>(
        RESULT_SUCCESS,
        SystemClock.elapsedRealtime(),
        params,
        /* sessionError= */ null,
        ImmutableList.copyOf(items),
        VALUE_TYPE_ITEM_LIST);
  }

  /**
   * Creates an instance with an unsuccessful {@link Code result code}.
   *
   * <p>{@code errorCode} must not be {@link #RESULT_SUCCESS}.
   *
   * <p>Note: This method will be deprecated when {@link #ofError(SessionError)} is promoted to
   * stable API status.
   *
   * @param errorCode The error code.
   */
  public static <V> LibraryResult<V> ofError(@Code int errorCode) {
    return ofError(new SessionError(errorCode, SessionError.DEFAULT_ERROR_MESSAGE, Bundle.EMPTY));
  }

  /**
   * Creates an instance with an unsuccessful {@link Code result code} and {@link LibraryParams} to
   * describe the error.
   *
   * <p>{@code errorCode} must not be {@link #RESULT_SUCCESS}.
   *
   * <p>Note: This method will be deprecated when {@link #ofError(SessionError, LibraryParams)} is
   * promoted to stable API status.
   *
   * @param errorCode The error code.
   * @param params The optional parameters to describe the error.
   */
  public static <V> LibraryResult<V> ofError(@Code int errorCode, @Nullable LibraryParams params) {
    return new LibraryResult<>(
        /* resultCode= */ errorCode,
        SystemClock.elapsedRealtime(),
        /* params= */ params,
        new SessionError(errorCode, SessionError.DEFAULT_ERROR_MESSAGE, Bundle.EMPTY),
        /* value= */ null,
        VALUE_TYPE_ERROR);
  }

  /**
   * Creates an instance with a {@link SessionError} to describe the error. The {@link #resultCode}
   * is taken from {@link SessionError#code}.
   *
   * @param sessionError The {@link SessionError}.
   */
  @UnstableApi
  public static <V> LibraryResult<V> ofError(SessionError sessionError) {
    return new LibraryResult<>(
        /* resultCode= */ sessionError.code,
        SystemClock.elapsedRealtime(),
        /* params= */ null,
        sessionError,
        /* value= */ null,
        VALUE_TYPE_ERROR);
  }

  /**
   * Creates an instance with a {@link SessionError} to describe the error, and the {@linkplain
   * LibraryParams parameters sent by the browser}. The {@link #resultCode} is taken from {@link
   * SessionError#code}.
   *
   * @param sessionError The {@link SessionError}.
   * @param params The {@link LibraryParams} sent by the browser.
   */
  @UnstableApi
  public static <V> LibraryResult<V> ofError(SessionError sessionError, LibraryParams params) {
    return new LibraryResult<>(
        /* resultCode= */ sessionError.code,
        SystemClock.elapsedRealtime(),
        /* params= */ params,
        sessionError,
        /* value= */ null,
        VALUE_TYPE_ERROR);
  }

  private LibraryResult(
      @Code int resultCode,
      long completionTimeMs,
      @Nullable LibraryParams params,
      @Nullable SessionError sessionError,
      @Nullable V value,
      @ValueType int valueType) {
    this.resultCode = resultCode;
    this.completionTimeMs = completionTimeMs;
    this.params = params;
    this.sessionError = sessionError;
    this.value = value;
    this.valueType = valueType;
  }

  private static void verifyMediaItem(MediaItem item) {
    checkNotEmpty(item.mediaId, "mediaId must not be empty");
    checkArgument(item.mediaMetadata.isBrowsable != null, "mediaMetadata must specify isBrowsable");
    checkArgument(item.mediaMetadata.isPlayable != null, "mediaMetadata must specify isPlayable");
  }

  private static final String FIELD_RESULT_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_COMPLETION_TIME_MS = Util.intToStringMaxRadix(1);
  private static final String FIELD_PARAMS = Util.intToStringMaxRadix(2);
  private static final String FIELD_VALUE = Util.intToStringMaxRadix(3);
  private static final String FIELD_VALUE_TYPE = Util.intToStringMaxRadix(4);
  private static final String FIELD_SESSION_ERROR = Util.intToStringMaxRadix(5);

  // Casting V to ImmutableList<MediaItem> is safe if valueType == VALUE_TYPE_ITEM_LIST.
  @SuppressWarnings("unchecked")
  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RESULT_CODE, resultCode);
    bundle.putLong(FIELD_COMPLETION_TIME_MS, completionTimeMs);
    if (params != null) {
      bundle.putBundle(FIELD_PARAMS, params.toBundle());
    }
    if (sessionError != null) {
      bundle.putBundle(FIELD_SESSION_ERROR, sessionError.toBundle());
    }
    bundle.putInt(FIELD_VALUE_TYPE, valueType);

    if (value == null) {
      return bundle;
    }
    switch (valueType) {
      case VALUE_TYPE_ITEM:
        bundle.putBundle(FIELD_VALUE, ((MediaItem) value).toBundle());
        break;
      case VALUE_TYPE_ITEM_LIST:
        BundleCompat.putBinder(
            bundle,
            FIELD_VALUE,
            new BundleListRetriever(
                BundleCollectionUtil.toBundleList(
                    (ImmutableList<MediaItem>) value, MediaItem::toBundle)));
        break;
      case VALUE_TYPE_VOID:
      case VALUE_TYPE_ERROR:
        // value must be null for both these types, so we should have returned above.
        throw new IllegalStateException();
    }
    return bundle;
  }

  /** Restores a {@code LibraryResult<Void>} from a {@link Bundle}. */
  // fromBundle will throw if the bundle doesn't have the right value type.
  @UnstableApi
  @SuppressWarnings("unchecked")
  public static LibraryResult<Void> fromVoidBundle(Bundle bundle) {
    return (LibraryResult<Void>) fromUnknownBundle(bundle);
  }

  /** Restores a {@code LibraryResult<MediaItem>} from a {@link Bundle}. */
  // fromBundle will throw if the bundle doesn't have the right value type.
  @UnstableApi
  @SuppressWarnings("unchecked")
  public static LibraryResult<MediaItem> fromItemBundle(Bundle bundle) {
    return (LibraryResult<MediaItem>) fromBundle(bundle, VALUE_TYPE_ITEM);
  }

  /** Restores a {@code LibraryResult<ImmutableList<MediaItem>} from a {@link Bundle}. */
  // fromBundle will throw if the bundle doesn't have the right value type.
  @UnstableApi
  @SuppressWarnings("unchecked")
  public static LibraryResult<ImmutableList<MediaItem>> fromItemListBundle(Bundle bundle) {
    return (LibraryResult<ImmutableList<MediaItem>>) fromBundle(bundle, VALUE_TYPE_ITEM_LIST);
  }

  /** Restores a {@code LibraryResult} with unknown value type from a {@link Bundle}. */
  @UnstableApi
  public static LibraryResult<?> fromUnknownBundle(Bundle bundle) {
    return fromBundle(bundle, /* expectedType= */ null);
  }

  /**
   * Constructs a new instance from {@code bundle}.
   *
   * @throws IllegalStateException if {@code expectedType} is non-null and doesn't match the value
   *     type read from {@code bundle}.
   */
  private static LibraryResult<?> fromBundle(
      Bundle bundle, @Nullable @ValueType Integer expectedType) {
    int resultCode = bundle.getInt(FIELD_RESULT_CODE, /* defaultValue= */ RESULT_SUCCESS);
    long completionTimeMs =
        bundle.getLong(FIELD_COMPLETION_TIME_MS, /* defaultValue= */ SystemClock.elapsedRealtime());
    @Nullable Bundle paramsBundle = bundle.getBundle(FIELD_PARAMS);
    @Nullable
    MediaLibraryService.LibraryParams params =
        paramsBundle == null ? null : LibraryParams.fromBundle(paramsBundle);
    @Nullable SessionError sessionError = null;
    @Nullable Bundle sessionErrorBundle = bundle.getBundle(FIELD_SESSION_ERROR);
    if (sessionErrorBundle != null) {
      sessionError = SessionError.fromBundle(sessionErrorBundle);
    } else if (resultCode != RESULT_SUCCESS) {
      // Result from a session with a library version that doesn't have the SessionError.
      sessionError = new SessionError(resultCode, SessionError.DEFAULT_ERROR_MESSAGE);
    }
    @ValueType int valueType = bundle.getInt(FIELD_VALUE_TYPE);
    @Nullable Object value;
    switch (valueType) {
      case VALUE_TYPE_ITEM:
        checkState(expectedType == null || expectedType == VALUE_TYPE_ITEM);
        @Nullable Bundle valueBundle = bundle.getBundle(FIELD_VALUE);
        value = valueBundle == null ? null : MediaItem.fromBundle(valueBundle);
        break;
      case VALUE_TYPE_ITEM_LIST:
        checkState(expectedType == null || expectedType == VALUE_TYPE_ITEM_LIST);
        @Nullable IBinder valueRetriever = BundleCompat.getBinder(bundle, FIELD_VALUE);
        value =
            valueRetriever == null
                ? null
                : BundleCollectionUtil.fromBundleList(
                    MediaItem::fromBundle, BundleListRetriever.getList(valueRetriever));
        break;
      case VALUE_TYPE_VOID:
      case VALUE_TYPE_ERROR:
        value = null;
        break;
      default:
        throw new IllegalStateException();
    }

    return new LibraryResult<>(
        resultCode, completionTimeMs, params, sessionError, value, valueType);
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({VALUE_TYPE_VOID, VALUE_TYPE_ITEM, VALUE_TYPE_ITEM_LIST, VALUE_TYPE_ERROR})
  private @interface ValueType {}

  private static final int VALUE_TYPE_VOID = 1;
  private static final int VALUE_TYPE_ITEM = 2;
  private static final int VALUE_TYPE_ITEM_LIST = 3;

  /** The value type isn't known because the result is carrying an error. */
  private static final int VALUE_TYPE_ERROR = 4;
}
