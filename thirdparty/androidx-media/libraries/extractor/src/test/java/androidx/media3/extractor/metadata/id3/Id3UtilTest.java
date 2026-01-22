/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.extractor.metadata.id3;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class Id3UtilTest {

  @Test
  public void expectedNumberOfV1Genres() {
    for (int i = 0; i < 192; i++) {
      assertThat(Id3Util.resolveV1Genre(i)).isNotNull();
    }
  }

  @Test
  public void unrecognizedV1Genre_returnsNull() {
    assertThat(Id3Util.resolveV1Genre(-1)).isNull();
    assertThat(Id3Util.resolveV1Genre(200)).isNull();
  }
}
