/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assume.assumeTrue;

import android.net.http.HttpEngine;
import android.os.Build;
import android.os.ext.SdkExtensions;
import androidx.annotation.RequiresExtension;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.HttpDataSourceTestEnv;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link HttpEngineDataSource}. */
// @SdkSuppress doesn't support extensions but lint doesn't understand that, so have to use
// @RequiresExtension: https://issuetracker.google.com/382043552
@SuppressWarnings("UseSdkSuppress")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
@RunWith(AndroidJUnit4.class)
public class HttpEngineDataSourceContractTest extends DataSourceContractTest {

  @Rule public HttpDataSourceTestEnv httpDataSourceTestEnv = new HttpDataSourceTestEnv();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Before
  public void before() {
    assumeTrue(SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7);
  }

  @After
  public void tearDown() {
    executorService.shutdown();
  }

  @Override
  protected DataSource createDataSource() {
    HttpEngine httpEngine =
        new HttpEngine.Builder(ApplicationProvider.getApplicationContext()).build();
    return new HttpEngineDataSource.Factory(httpEngine, executorService).createDataSource();
  }

  @Override
  protected ImmutableList<TestResource> getTestResources() {
    return httpDataSourceTestEnv.getServedResources();
  }

  @Override
  protected List<TestResource> getNotFoundResources() {
    return httpDataSourceTestEnv.getNotFoundResources();
  }
}
