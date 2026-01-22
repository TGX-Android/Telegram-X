/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.annotation.Nullable;
import androidx.media3.datasource.ResolvingDataSource.Resolver;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.FakeDataSet;
import androidx.media3.test.utils.FakeDataSource;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link ResolvingDataSourceContractTest}. */
@RunWith(AndroidJUnit4.class)
public class ResolvingDataSourceContractTest extends DataSourceContractTest {

  private static final String REQUESTED_URI = "test://simple.test";
  private static final String RESOLVED_URI = "resolved://simple.resolved";

  private static final String REQUESTED_URI_WITH_DIFFERENT_REPORTED =
      "test://different.report.test";
  private static final String RESOLVED_URI_WITH_DIFFERENT_REPORTED =
      "resolved://different.report.test";
  private static final String REPORTED_URI = "reported://reported.test";

  private byte[] simpleData;
  private byte[] differentReportedData;
  private FakeDataSet fakeDataSet;
  private FakeDataSource fakeDataSource;

  @Before
  public void setUp() {
    simpleData = TestUtil.buildTestData(/* length= */ 20);
    differentReportedData = TestUtil.buildTestData(/* length= */ 15);
    fakeDataSet =
        new FakeDataSet()
            .setData(RESOLVED_URI, simpleData)
            .setData(RESOLVED_URI_WITH_DIFFERENT_REPORTED, differentReportedData);
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(REQUESTED_URI)
            .setResolvedUri(RESOLVED_URI)
            .setExpectedBytes(simpleData)
            .build(),
        new TestResource.Builder()
            .setName("different-reported")
            .setUri(REQUESTED_URI_WITH_DIFFERENT_REPORTED)
            .setResolvedUri(REPORTED_URI)
            .setExpectedBytes(differentReportedData)
            .build());
  }

  @Override
  protected Uri getNotFoundUri() {
    return Uri.parse("test://not-found.test");
  }

  @Override
  protected DataSource createDataSource() {
    fakeDataSource = new FakeDataSource(fakeDataSet);
    return new ResolvingDataSource(
        fakeDataSource,
        new Resolver() {
          @Override
          public DataSpec resolveDataSpec(DataSpec dataSpec) throws IOException {
            switch (dataSpec.uri.normalizeScheme().toString()) {
              case REQUESTED_URI:
                return dataSpec.buildUpon().setUri(RESOLVED_URI).build();
              case REQUESTED_URI_WITH_DIFFERENT_REPORTED:
                return dataSpec.buildUpon().setUri(RESOLVED_URI_WITH_DIFFERENT_REPORTED).build();
              default:
                return dataSpec;
            }
          }

          @Override
          public Uri resolveReportedUri(Uri uri) {
            return uri.normalizeScheme().toString().equals(RESOLVED_URI_WITH_DIFFERENT_REPORTED)
                ? Uri.parse(REPORTED_URI)
                : uri;
          }
        });
  }

  @Override
  @Nullable
  protected DataSource getTransferListenerDataSource() {
    return fakeDataSource;
  }
}
