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

package androidx.media3.session.legacy;

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import android.os.Bundle;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaDescriptionCompat}. */
@RunWith(AndroidJUnit4.class)
public class MediaDescriptionCompatTest {

  @SdkSuppress(minSdkVersion = 21)
  @Test
  public void roundTripViaFrameworkObject_returnsEqualMediaUriAndExtras() {
    Uri mediaUri = Uri.parse("androidx://media/uri");
    MediaDescriptionCompat originalDescription =
        new MediaDescriptionCompat.Builder()
            .setMediaUri(mediaUri)
            .setExtras(createExtras())
            .build();

    MediaDescriptionCompat restoredDescription =
        MediaDescriptionCompat.fromMediaDescription(originalDescription.getMediaDescription());

    // Test second round-trip as MediaDescriptionCompat keeps an internal reference to a previously
    // restored platform instance.
    MediaDescriptionCompat restoredDescription2 =
        MediaDescriptionCompat.fromMediaDescription(restoredDescription.getMediaDescription());

    assertEquals(mediaUri, restoredDescription.getMediaUri());
    TestUtils.equals(createExtras(), restoredDescription.getExtras());
    assertEquals(mediaUri, restoredDescription2.getMediaUri());
    TestUtils.equals(createExtras(), restoredDescription2.getExtras());
  }

  @SdkSuppress(minSdkVersion = 21)
  @Test
  public void getMediaDescription_withMediaUri_doesNotTouchExtras() {
    MediaDescriptionCompat originalDescription =
        new MediaDescriptionCompat.Builder()
            .setMediaUri(Uri.EMPTY)
            .setExtras(createExtras())
            .build();
    originalDescription.getMediaDescription();
    TestUtils.equals(createExtras(), originalDescription.getExtras());
  }

  private static Bundle createExtras() {
    Bundle extras = new Bundle();
    extras.putString("key1", "value1");
    extras.putString("key2", "value2");
    return extras;
  }
}
