/*
 * Copyright 2024 The Android Open Source Project
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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/** Provides information about a session error. */
@UnstableApi
public final class SessionError {

  /**
   * Info and error result codes.
   *
   * <ul>
   *   <li>Info code: Positive integer
   *   <li>Error code: Negative integer
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    ERROR_UNKNOWN,
    ERROR_INVALID_STATE,
    ERROR_BAD_VALUE,
    ERROR_PERMISSION_DENIED,
    ERROR_IO,
    ERROR_NOT_SUPPORTED,
    ERROR_SESSION_DISCONNECTED,
    ERROR_SESSION_AUTHENTICATION_EXPIRED,
    ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED,
    ERROR_SESSION_CONCURRENT_STREAM_LIMIT,
    ERROR_SESSION_CONTENT_ALREADY_PLAYING,
    ERROR_SESSION_END_OF_PLAYLIST,
    ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED,
    ERROR_SESSION_NOT_AVAILABLE_IN_REGION,
    ERROR_SESSION_SKIP_LIMIT_REACHED,
    ERROR_SESSION_SETUP_REQUIRED,
    INFO_CANCELLED
  })
  public @interface Code {}

  // Info codes (> 0).

  /** Info code representing that the command was cancelled. */
  public static final int INFO_CANCELLED = 1;

  // Error codes (< 0).

  /** Error code representing that the command is ended with an unknown error. */
  public static final int ERROR_UNKNOWN = -1;

  /**
   * Error code representing that the command cannot be completed because the current state is not
   * valid for the command.
   */
  public static final int ERROR_INVALID_STATE = PlaybackException.ERROR_CODE_INVALID_STATE;

  /** Error code representing that an argument is illegal. */
  public static final int ERROR_BAD_VALUE = PlaybackException.ERROR_CODE_BAD_VALUE;

  /** Error code representing that the command is not allowed. */
  public static final int ERROR_PERMISSION_DENIED = PlaybackException.ERROR_CODE_PERMISSION_DENIED;

  /** Error code representing that a file or network related error happened. */
  public static final int ERROR_IO = -5;

  /** Error code representing that the command is not supported. */
  public static final int ERROR_NOT_SUPPORTED = PlaybackException.ERROR_CODE_NOT_SUPPORTED;

  /** Error code representing that the session and controller were disconnected. */
  public static final int ERROR_SESSION_DISCONNECTED = PlaybackException.ERROR_CODE_DISCONNECTED;

  /** Error code representing that the authentication has expired. */
  public static final int ERROR_SESSION_AUTHENTICATION_EXPIRED =
      PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED;

  /** Error code representing that a premium account is required. */
  public static final int ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED =
      PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED;

  /** Error code representing that too many concurrent streams are detected. */
  public static final int ERROR_SESSION_CONCURRENT_STREAM_LIMIT =
      PlaybackException.ERROR_CODE_CONCURRENT_STREAM_LIMIT;

  /** Error code representing that the content is blocked due to parental controls. */
  public static final int ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED =
      PlaybackException.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED;

  /** Error code representing that the content is blocked due to being regionally unavailable. */
  public static final int ERROR_SESSION_NOT_AVAILABLE_IN_REGION =
      PlaybackException.ERROR_CODE_NOT_AVAILABLE_IN_REGION;

  /**
   * Error code representing that the application cannot skip any more because the skip limit is
   * reached.
   */
  public static final int ERROR_SESSION_SKIP_LIMIT_REACHED =
      PlaybackException.ERROR_CODE_SKIP_LIMIT_REACHED;

  /** Error code representing that the session needs user's manual intervention. */
  public static final int ERROR_SESSION_SETUP_REQUIRED =
      PlaybackException.ERROR_CODE_SETUP_REQUIRED;

  /** Error code representing that navigation failed because the the playlist was exhausted. */
  public static final int ERROR_SESSION_END_OF_PLAYLIST =
      PlaybackException.ERROR_CODE_END_OF_PLAYLIST;

  /** Error code representing that the requested content is already playing. */
  public static final int ERROR_SESSION_CONTENT_ALREADY_PLAYING =
      PlaybackException.ERROR_CODE_CONTENT_ALREADY_PLAYING;

  /** Default error message. Only used by deprecated methods and for backwards compatibility. */
  /* package */ static final String DEFAULT_ERROR_MESSAGE = "no error message provided";

  /** Returns the name of a given error code. */
  public static String getErrorCodeName(@Code int errorCode) {
    switch (errorCode) {
      case ERROR_UNKNOWN:
        return "ERROR_UNKNOWN";
      case ERROR_INVALID_STATE:
        return "ERROR_INVALID_STATE";
      case ERROR_BAD_VALUE:
        return "ERROR_BAD_VALUE";
      case ERROR_PERMISSION_DENIED:
        return "ERROR_PERMISSION_DENIED";
      case ERROR_IO:
        return "ERROR_IO";
      case ERROR_NOT_SUPPORTED:
        return "ERROR_NOT_SUPPORTED";
      case ERROR_SESSION_DISCONNECTED:
        return "ERROR_SESSION_DISCONNECTED";
      case ERROR_SESSION_AUTHENTICATION_EXPIRED:
        return "ERROR_SESSION_AUTHENTICATION_EXPIRED";
      case ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED:
        return "ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED";
      case ERROR_SESSION_CONCURRENT_STREAM_LIMIT:
        return "ERROR_SESSION_CONCURRENT_STREAM_LIMIT";
      case ERROR_SESSION_CONTENT_ALREADY_PLAYING:
        return "ERROR_SESSION_CONTENT_ALREADY_PLAYING";
      case ERROR_SESSION_END_OF_PLAYLIST:
        return "ERROR_SESSION_END_OF_PLAYLIST";
      case ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED:
        return "ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED";
      case ERROR_SESSION_NOT_AVAILABLE_IN_REGION:
        return "ERROR_SESSION_NOT_AVAILABLE_IN_REGION";
      case ERROR_SESSION_SKIP_LIMIT_REACHED:
        return "ERROR_SESSION_SKIP_LIMIT_REACHED";
      case ERROR_SESSION_SETUP_REQUIRED:
        return "ERROR_SESSION_SETUP_REQUIRED";
      case INFO_CANCELLED:
        return "INFO_CANCELLED";
      default:
        return "invalid error code";
    }
  }

  public @SessionError.Code int code;
  public String message;
  public Bundle extras;

  /**
   * Creates an instance with {@linkplain Bundle#EMPTY an empty extras bundle}.
   *
   * @param code The error result code.
   * @param message The error message.
   * @throws IllegalArgumentException if the result code is not an error result code.
   */
  public SessionError(@SessionError.Code int code, String message) {
    this(code, message, Bundle.EMPTY);
  }

  /**
   * Creates an instance.
   *
   * @param code The error result code.
   * @param message The error message.
   * @param extras The error extras.
   * @throws IllegalArgumentException if the result code is not an error result code.
   */
  public SessionError(@SessionError.Code int code, String message, Bundle extras) {
    Assertions.checkArgument(code < 0 || code == INFO_CANCELLED);
    this.code = code;
    this.message = message;
    this.extras = extras;
  }

  /** Checks the given error for equality while ignoring {@link #extras}. */
  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SessionError)) {
      return false;
    }
    SessionError that = (SessionError) o;
    return code == that.code && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message);
  }

  // Bundleable implementation.

  private static final String FIELD_CODE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MESSAGE = Util.intToStringMaxRadix(1);
  private static final String FIELD_EXTRAS = Util.intToStringMaxRadix(2);

  /** Returns a {@link Bundle} representing the information stored in this object. */
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(FIELD_CODE, code);
    bundle.putString(FIELD_MESSAGE, message);
    if (!extras.isEmpty()) {
      bundle.putBundle(FIELD_EXTRAS, extras);
    }
    return bundle;
  }

  /** Restores a {@code SessionError} from a {@link Bundle}. */
  public static SessionError fromBundle(Bundle bundle) {
    int code =
        bundle.getInt(FIELD_CODE, /* defaultValue= */ PlaybackException.ERROR_CODE_UNSPECIFIED);
    String message = bundle.getString(FIELD_MESSAGE, /* defaultValue= */ "");
    @Nullable Bundle extras = bundle.getBundle(FIELD_EXTRAS);
    return new SessionError(code, message, extras == null ? Bundle.EMPTY : extras);
  }
}
