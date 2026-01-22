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

import static androidx.media3.session.SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED;
import static androidx.media3.session.SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT;
import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SessionResult}. */
@RunWith(AndroidJUnit4.class)
public class SessionResultTest {

  @Test
  public void constructor_errorCodeOnly_createsDefaultSessionError() {
    SessionResult sessionResult = new SessionResult(ERROR_SESSION_AUTHENTICATION_EXPIRED);

    assertThat(sessionResult.resultCode).isEqualTo(ERROR_SESSION_AUTHENTICATION_EXPIRED);
    assertThat(sessionResult.extras.size()).isEqualTo(0);
    assertThat(sessionResult.sessionError.code).isEqualTo(ERROR_SESSION_AUTHENTICATION_EXPIRED);
    assertThat(sessionResult.sessionError.message).isEqualTo(SessionError.DEFAULT_ERROR_MESSAGE);
    assertThat(sessionResult.sessionError.extras.size()).isEqualTo(0);
  }

  @Test
  public void constructor_errorCodeAndBundleOnly_createsDefaultSessionError() {
    Bundle bundle = new Bundle();
    bundle.putString("key", "value");

    SessionResult sessionResult = new SessionResult(ERROR_SESSION_CONCURRENT_STREAM_LIMIT, bundle);

    assertThat(sessionResult.resultCode).isEqualTo(ERROR_SESSION_CONCURRENT_STREAM_LIMIT);
    assertThat(sessionResult.extras.size()).isEqualTo(1);
    assertThat(sessionResult.extras.getString("key")).isEqualTo("value");
    assertThat(sessionResult.sessionError.code).isEqualTo(ERROR_SESSION_CONCURRENT_STREAM_LIMIT);
    assertThat(sessionResult.sessionError.message).isEqualTo(SessionError.DEFAULT_ERROR_MESSAGE);
    assertThat(sessionResult.sessionError.extras.size()).isEqualTo(0);
  }

  @Test
  public void toBundle_roundTrip_resultsInEqualObjectWithSameBundle() {
    Bundle errorExtras = new Bundle();
    errorExtras.putString("errorKey", "errorValue");
    SessionResult sessionResult =
        new SessionResult(
            new SessionError(SessionError.ERROR_NOT_SUPPORTED, "error message", errorExtras));

    SessionResult resultFromBundle = SessionResult.fromBundle(sessionResult.toBundle());

    assertThat(resultFromBundle.resultCode).isEqualTo(sessionResult.resultCode);
    assertThat(resultFromBundle.completionTimeMs).isEqualTo(sessionResult.completionTimeMs);
    assertThat(resultFromBundle.sessionError.code).isEqualTo(sessionResult.sessionError.code);
    assertThat(resultFromBundle.sessionError.message).isEqualTo(sessionResult.sessionError.message);
    assertThat(resultFromBundle.sessionError.extras.size()).isEqualTo(1);
    assertThat(resultFromBundle.sessionError.extras.getString("errorKey")).isEqualTo("errorValue");
    assertThat(resultFromBundle.extras.size()).isEqualTo(0);
  }
}
