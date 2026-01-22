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

import static org.junit.Assert.assertThrows;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link FileDescriptorDataSource}. */
@RunWith(AndroidJUnit4.class)
public final class FileDescriptorDataSourceTest {

  private static final byte[] DATA = TestUtil.buildTestData(20);
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String ASSET_PATH = "media/mp3/1024_incrementing_bytes.mp3";

  @Test
  public void testReadViaFileDescriptor() throws Exception {
    File file = tempFolder.newFile();
    Files.write(DATA, file);

    try (FileInputStream inputStream = new FileInputStream(file)) {
      DataSource dataSource =
          new FileDescriptorDataSource(inputStream.getFD(), /* offset= */ 0, DATA.length);

      TestUtil.assertDataSourceContent(
          dataSource, new DataSpec(Uri.EMPTY), DATA, /* expectKnownLength= */ true);
    }
  }

  @Test
  public void testReadViaFileDescriptorWithUnsetLength() throws Exception {
    File file = tempFolder.newFile();
    Files.write(DATA, file);

    try (FileInputStream inputStream = new FileInputStream(file)) {
      DataSource dataSource =
          new FileDescriptorDataSource(inputStream.getFD(), /* offset= */ 0, C.LENGTH_UNSET);

      TestUtil.assertDataSourceContent(
          dataSource, new DataSpec(Uri.EMPTY), DATA, /* expectKnownLength= */ true);
    }
  }

  @Test
  public void testReadViaFileDescriptorWithOffset() throws Exception {
    File file = tempFolder.newFile();
    Files.write(DATA, file);

    try (FileInputStream inputStream = new FileInputStream(file)) {
      DataSource dataSource =
          new FileDescriptorDataSource(inputStream.getFD(), /* offset= */ 0, DATA.length);
      DataSpec dataSpec = new DataSpec(Uri.EMPTY, /* position= */ 10, C.LENGTH_UNSET);
      byte[] expectedData = Arrays.copyOfRange(DATA, /* position= */ 10, DATA.length);

      TestUtil.assertDataSourceContent(
          dataSource, dataSpec, expectedData, /* expectKnownLength= */ true);
    }
  }

  @Test
  public void testReadViaAssetFileDescriptor() throws Exception {
    try (AssetFileDescriptor afd =
        ApplicationProvider.getApplicationContext().getAssets().openFd(ASSET_PATH)) {
      DataSource dataSource =
          new FileDescriptorDataSource(
              afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
      byte[] expectedData =
          TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), ASSET_PATH);

      TestUtil.assertDataSourceContent(
          dataSource, new DataSpec(Uri.EMPTY), expectedData, /* expectKnownLength= */ true);
    }
  }

  @Test
  public void testReadViaAssetFileDescriptorWithOffset() throws Exception {
    try (AssetFileDescriptor afd =
        ApplicationProvider.getApplicationContext().getAssets().openFd(ASSET_PATH)) {
      DataSource dataSource =
          new FileDescriptorDataSource(
              afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
      DataSpec dataSpec = new DataSpec(Uri.EMPTY, /* position= */ 100, C.LENGTH_UNSET);
      byte[] data = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), ASSET_PATH);
      byte[] expectedData = Arrays.copyOfRange(data, /* position= */ 100, data.length);

      TestUtil.assertDataSourceContent(
          dataSource, dataSpec, expectedData, /* expectKnownLength= */ true);
    }
  }

  @Test
  public void testConcurrentUseOfSameFileDescriptorFails() throws Exception {
    try (AssetFileDescriptor afd =
        ApplicationProvider.getApplicationContext().getAssets().openFd(ASSET_PATH)) {
      DataSpec dataSpec = new DataSpec(Uri.EMPTY, /* position= */ 100, C.LENGTH_UNSET);
      DataSource dataSource1 =
          new FileDescriptorDataSource(
              afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
      dataSource1.open(dataSpec);
      DataSource dataSource2 =
          new FileDescriptorDataSource(
              afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());

      // Opening a data source with the same file descriptor is expected to fail.
      assertThrows(DataSourceException.class, () -> dataSource2.open(dataSpec));

      if (dataSource1 != null) {
        dataSource1.close();
      }
      if (dataSource2 != null) {
        dataSource2.close();
      }
    }
  }
}
