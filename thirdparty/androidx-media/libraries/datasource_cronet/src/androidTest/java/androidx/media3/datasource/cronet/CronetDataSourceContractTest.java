/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.datasource.cronet;

import androidx.media3.datasource.DataSource;
import androidx.media3.test.utils.DataSourceContractTest;
import androidx.media3.test.utils.HttpDataSourceTestEnv;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;
import org.junit.After;
import org.junit.Rule;
import org.junit.runner.RunWith;

/** {@link DataSource} contract tests for {@link CronetDataSource}. */
@RunWith(AndroidJUnit4.class)
public class CronetDataSourceContractTest extends DataSourceContractTest {

  @Rule public HttpDataSourceTestEnv httpDataSourceTestEnv = new HttpDataSourceTestEnv();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @After
  public void tearDown() {
    executorService.shutdown();
  }

  @Override
  protected List<DataSource> createDataSources() {
    List<CronetProvider> cronetProviders =
        CronetProvider.getAllProviders(ApplicationProvider.getApplicationContext());
    ImmutableList.Builder<DataSource> dataSources = ImmutableList.builder();
    for (int i = 0; i < cronetProviders.size(); i++) {
      CronetProvider provider = cronetProviders.get(i);
      if (!provider.isEnabled()) {
        continue;
      }
      CronetEngine cronetEngine = provider.createBuilder().setUserAgent("test-agent").build();
      dataSources.add(
          new CronetDataSource.Factory(cronetEngine, executorService).createDataSource());
    }
    return dataSources.build();
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
