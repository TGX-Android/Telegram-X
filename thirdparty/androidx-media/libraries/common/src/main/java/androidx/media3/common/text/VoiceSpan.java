/*
 * Copyright (C) 2024 The Android Open Source Project
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
 *
 */
package androidx.media3.common.text;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.os.Bundle;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * A span representing the speaker of the spanned text.
 *
 * <p>For example a <a href="https://www.w3.org/TR/webvtt1/#webvtt-cue-voice-span">WebVTT voice
 * span</a>.
 */
@UnstableApi
public final class VoiceSpan {

  /** The voice name. */
  public final String name;

  private static final String FIELD_NAME = Util.intToStringMaxRadix(0);

  public VoiceSpan(String name) {
    this.name = name;
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(FIELD_NAME, name);
    return bundle;
  }

  public static VoiceSpan fromBundle(Bundle bundle) {
    return new VoiceSpan(checkNotNull(bundle.getString(FIELD_NAME)));
  }
}
