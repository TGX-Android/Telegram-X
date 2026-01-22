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

import static java.lang.Math.min;

import android.media.MediaDataSource;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link MediaDataSourceAdapter}. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 23)
public class MediaDataSourceAdapterContractTest extends DataSourceContractTest {

  private static final byte[] DATA = TestUtil.buildTestData(20);

  @Override
  protected DataSource createDataSource() {
    MediaDataSource mediaDataSource =
        new MediaDataSource() {
          @Override
          public int readAt(long position, byte[] buffer, int offset, int size) {
            if (size == 0) {
              return 0;
            }

            if (position > getSize()) {
              return C.RESULT_END_OF_INPUT;
            }

            size = min(size, (int) (getSize() - position));

            System.arraycopy(DATA, (int) position, buffer, offset, size);
            return size;
          }

          @Override
          public long getSize() {
            return DATA.length;
          }

          @Override
          public void close() {}
        };
    return new MediaDataSourceAdapter(mediaDataSource, /* isNetwork= */ false);
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(Uri.EMPTY)
            .setExpectedBytes(DATA)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Test
  @Ignore
  public void resourceNotFound() {}

  @Override
  @Test
  @Ignore
  public void resourceNotFound_transferListenerCallbacks() {}

  @Override
  @Test
  @Ignore
  public void getUri_resourceNotFound_returnsNullIfNotOpened() {}

  @Override
  @Test
  @Ignore
  public void getResponseHeaders_resourceNotFound_isEmptyWhileNotOpen() {}
}
