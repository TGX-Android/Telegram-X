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
package androidx.media3.exoplayer.source;

import static androidx.media3.test.utils.TestUtil.assertForwardingClassForwardsAllMethodsExcept;
import static androidx.media3.test.utils.TestUtil.assertSubclassOverridesAllMethods;

import androidx.media3.common.Timeline;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ForwardingTimeline}. */
@RunWith(AndroidJUnit4.class)
public class ForwardingTimelineTest {

  @Test
  public void overridesAllMethods() throws Exception {
    assertSubclassOverridesAllMethods(Timeline.class, ForwardingTimeline.class);
  }

  @Test
  public void forwardsAllMethods() throws Exception {
    // ForwardingTimeline equals, hashCode, and getPeriodByUid implementations deliberately call
    // through to super rather than the delegate instance. This is because these methods are already
    // correctly implemented on Timeline in terms of the publicly visible parts of Timeline.
    assertForwardingClassForwardsAllMethodsExcept(
        Timeline.class,
        delegate -> new ForwardingTimeline(delegate) {},
        ImmutableSet.of("equals", "hashCode", "getPeriodByUid"));
  }
}
