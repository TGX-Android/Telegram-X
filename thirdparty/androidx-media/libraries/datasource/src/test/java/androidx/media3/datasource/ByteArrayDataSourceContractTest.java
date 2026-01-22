/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.net.Uri;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link ByteArrayDataSource}. */
@RunWith(AndroidJUnit4.class)
public class ByteArrayDataSourceContractTest extends DataSourceContractTest {

  private static final Uri URI_1 = Uri.parse("uri1");
  private static final byte[] DATA_1 = TestUtil.buildTestData(20);
  private static final Uri URI_2 = Uri.parse("uri2");
  private static final byte[] DATA_2 = TestUtil.buildTestData(10);

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder().setName("data-1").setUri(URI_1).setExpectedBytes(DATA_1).build(),
        new TestResource.Builder()
            .setName("data-2")
            .setUri(URI_2)
            .setExpectedBytes(DATA_2)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("not-found");
  }

  @Override
  protected DataSource createDataSource() {
    return new ByteArrayDataSource(
        uri -> {
          if (uri.equals(URI_1)) {
            return DATA_1;
          } else if (uri.equals(URI_2)) {
            return DATA_2;
          } else {
            throw new IOException("Unrecognized URI: " + uri);
          }
        });
  }
}
