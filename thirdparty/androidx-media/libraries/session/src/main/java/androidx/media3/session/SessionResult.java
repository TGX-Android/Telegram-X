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
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A result to be used with {@link ListenableFuture} for asynchronous calls between {@link
 * MediaSession} and {@link MediaController}.
 */
public final class SessionResult {

  /**
   * Result codes.
   *
   * <ul>
   *   <li>Error code: Negative integer
   *   <li>Success code: 0
   *   <li>Info code: Positive integer
   * </ul>
   *
   * <ul>
   *   <li>{@code 0 < |code| < 100} : Reserved for Player specific code.
   *   <li>{@code 100 <= |code| < 500} : Session/Controller specific code.
   *   <li>{@code 500 <= |code| < 1000} : Browser/Library session specific code.
   *   <li>{@code 1000 <= |code|} : Reserved for Player custom code.
   * </ul>
   */
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

  /** The extra {@link Bundle} for the result. */
  public final Bundle extras;

  /**
   * The completion time of the command. It's the same as {@link SystemClock#elapsedRealtime()} when
   * the command is completed.
   */
  public final long completionTimeMs;

  /** The optional session error. */
  @UnstableApi @Nullable public final SessionError sessionError;

  /**
   * Creates an instance with a result code.
   *
   * <p>Note: Use {@link SessionResult#SessionResult(SessionError)} for errors to provide a
   * localized error message for your users.
   *
   * @param resultCode The result code.
   */
  public SessionResult(@Code int resultCode) {
    this(resultCode, /* extras= */ Bundle.EMPTY);
  }

  /**
   * Creates an instance with a result code and an extra {@link Bundle}.
   *
   * <p>Note: Use {@link SessionResult#SessionResult(SessionError, Bundle)} for errors to provide a
   * localized error message for your users.
   *
   * @param resultCode The result code.
   * @param extras The extra {@link Bundle}.
   */
  public SessionResult(@Code int resultCode, Bundle extras) {
    this(
        resultCode,
        extras,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        /* sessionError= */ null);
  }

  /**
   * Creates an instance from a {@link SessionError}. The {@link #resultCode} is taken from {@link
   * SessionError#code} and the session result extras {@link Bundle} is empty.
   *
   * @param sessionError The {@linkplain SessionError session error}.
   */
  @UnstableApi
  public SessionResult(SessionError sessionError) {
    this(
        sessionError.code,
        Bundle.EMPTY,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        sessionError);
  }

  /**
   * Creates an instance from a {@link SessionError} and an extras {@link Bundle}. The {@link
   * #resultCode} is taken from the {@link SessionError}.
   *
   * @param sessionError The {@link SessionError}.
   * @param extras The extra {@link Bundle}.
   */
  @UnstableApi
  public SessionResult(SessionError sessionError, Bundle extras) {
    this(
        sessionError.code,
        extras,
        /* completionTimeMs= */ SystemClock.elapsedRealtime(),
        sessionError);
  }

  private SessionResult(
      @Code int resultCode,
      Bundle extras,
      long completionTimeMs,
      @Nullable SessionError sessionError) {
    checkArgument(sessionError == null || resultCode < 0);
    this.resultCode = resultCode;
    this.extras = new Bundle(extras);
    this.completionTimeMs = completionTimeMs;
    this.sessionError =
        sessionError == null && resultCode < 0
            ? new SessionError(resultCode, SessionError.DEFAULT_ERROR_MESSAGE)
            : sessionError;
  }

  private static final String FIELD_RESULT_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(1);
  private static final String FIELD_COMPLETION_TIME_MS = Util.intToStringMaxRadix(2);
  private static final String FIELD_SESSION_ERROR = Util.intToStringMaxRadix(3);

  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_RESULT_CODE, resultCode);
    bundle.putBundle(FIELD_EXTRAS, extras);
    bundle.putLong(FIELD_COMPLETION_TIME_MS, completionTimeMs);
    if (sessionError != null) {
      bundle.putBundle(FIELD_SESSION_ERROR, sessionError.toBundle());
    }
    return bundle;
  }

  /** Restores a {@code SessionResult} from a {@link Bundle}. */
  @UnstableApi
  public static SessionResult fromBundle(Bundle bundle) {
    int resultCode =
        bundle.getInt(FIELD_RESULT_CODE, /* defaultValue= */ SessionError.ERROR_UNKNOWN);
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    long completionTimeMs =
        bundle.getLong(FIELD_COMPLETION_TIME_MS, /* defaultValue= */ SystemClock.elapsedRealtime());
    @Nullable SessionError sessionError = null;
    @Nullable Bundle sessionErrorBundle = bundle.getBundle(FIELD_SESSION_ERROR);
    if (sessionErrorBundle != null) {
      sessionError = SessionError.fromBundle(sessionErrorBundle);
    } else if (resultCode != RESULT_SUCCESS) {
      // Populate the session error if the session is of a library version that doesn't have the
      // SessionError yet.
      sessionError = new SessionError(resultCode, SessionError.DEFAULT_ERROR_MESSAGE);
    }
    return new SessionResult(
        resultCode, extras == null ? Bundle.EMPTY : extras, completionTimeMs, sessionError);
  }
}
