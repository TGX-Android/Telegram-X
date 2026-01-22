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

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/**
 * {@link DataSource} contract tests for {@link FileDescriptorDataSource} using {@link
 * FileDescriptor}.
 */
@RunWith(AndroidJUnit4.class)
public class FileDescriptorDataSourceUsingFileDescriptorContractTest
    extends DataSourceContractTest {

  private static final byte[] DATA = TestUtil.buildTestData(20);
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private FileInputStream inputStream;

  @After
  public void cleanUp() throws IOException {
    if (inputStream != null) {
      inputStream.close();
    }
  }

  @Override
  protected List<DataSource> createDataSources() throws Exception {
    File file = tempFolder.newFile();
    Files.write(DATA, file);
    inputStream = new FileInputStream(file);
    return ImmutableList.of(
        new FileDescriptorDataSource(inputStream.getFD(), /* offset= */ 0, DATA.length),
        new FileDescriptorDataSource(inputStream.getFD(), /* offset= */ 0, C.LENGTH_UNSET));
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
