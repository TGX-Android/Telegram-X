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
package androidx.media3.datasource;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import androidx.media3.test.utils.FakeDataSet;
import androidx.media3.test.utils.FakeDataSource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StatsDataSourceTest {

  @Test
  public void getLastOpenedUri_openSucceeds_returnsRedirectedUriAfterClosure() throws Exception {
    Uri redirectedUri = Uri.parse("bar");
    FakeDataSet fakeDataSet = new FakeDataSet();
    fakeDataSet.setRandomData(redirectedUri, /* length= */ 10);
    StatsDataSource statsDataSource =
        new StatsDataSource(
            new ResolvingDataSource(
                new FakeDataSource(fakeDataSet),
                dataSpec -> dataSpec.buildUpon().setUri(redirectedUri).build()));

    statsDataSource.open(new DataSpec(Uri.parse("foo")));
    statsDataSource.close();

    assertThat(statsDataSource.getLastOpenedUri()).isEqualTo(redirectedUri);
  }

  @Test
  public void getLastOpenedUri_openFails_returnsRedirectedUriAfterClosure() throws Exception {
    Uri redirectedUri = Uri.parse("bar");
    StatsDataSource statsDataSource =
        new StatsDataSource(
            new ResolvingDataSource(
                new FakeDataSource(),
                dataSpec -> dataSpec.buildUpon().setUri(redirectedUri).build()));

    assertThrows(IOException.class, () -> statsDataSource.open(new DataSpec(Uri.parse("foo"))));
    statsDataSource.close();

    assertThat(statsDataSource.getLastOpenedUri()).isEqualTo(redirectedUri);
  }
}
