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

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link DataSource} contract tests for {@link FileDescriptorDataSource} using {@link
 * AssetFileDescriptor}.
 */
@RunWith(AndroidJUnit4.class)
public class FileDescriptorDataSourceUsingAssetFileDescriptorContractTest
    extends DataSourceContractTest {

  private static final String ASSET_PATH = "media/mp3/1024_incrementing_bytes.mp3";

  @Override
  protected DataSource createDataSource() throws Exception {
    AssetFileDescriptor afd =
        ApplicationProvider.getApplicationContext().getAssets().openFd(ASSET_PATH);
    return new FileDescriptorDataSource(
        afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() throws Exception {
    return ImmutableList.of(
        new TestResource.Builder()
            .setName("simple")
            .setUri(Uri.EMPTY)
            .setExpectedBytes(
                TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), ASSET_PATH))
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
