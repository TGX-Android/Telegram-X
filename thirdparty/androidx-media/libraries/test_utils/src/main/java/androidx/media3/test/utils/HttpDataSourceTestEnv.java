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

package androidx.media3.test.utils;

import static androidx.media3.test.utils.WebServerDispatcher.getRequestPath;

import android.net.Uri;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.HttpDataSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

/** A JUnit {@link Rule} that creates test resources for {@link HttpDataSource} contract tests. */
@UnstableApi
public class HttpDataSourceTestEnv extends ExternalResource {

  public static final ImmutableListMultimap<String, String> EXTRA_HEADERS =
      ImmutableListMultimap.<String, String>builder()
          .putAll("X-Test-Header", "test value1", "test value2")
          .build();

  private static int seed = 0;
  public static final WebServerDispatcher.Resource RANGE_SUPPORTED =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/supports/range-requests")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .supportsRangeRequests(true)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource RANGE_SUPPORTED_LENGTH_UNKNOWN =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/supports/range-requests-length-unknown")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .supportsRangeRequests(true)
          .resolvesToUnknownLength(true)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource RANGE_NOT_SUPPORTED =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/doesnt/support/range-requests")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .supportsRangeRequests(false)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource RANGE_NOT_SUPPORTED_LENGTH_UNKNOWN =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/doesnt/support/range-requests-length-unknown")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .supportsRangeRequests(false)
          .resolvesToUnknownLength(true)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource GZIP_ENABLED =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/gzip/enabled")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .setGzipSupport(WebServerDispatcher.Resource.GZIP_SUPPORT_ENABLED)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource GZIP_FORCED =
      new WebServerDispatcher.Resource.Builder()
          .setPath("/gzip/forced")
          .setData(TestUtil.buildTestData(/* length= */ 20, seed++))
          .setGzipSupport(WebServerDispatcher.Resource.GZIP_SUPPORT_FORCED)
          .setExtraResponseHeaders(EXTRA_HEADERS)
          .build();

  public static final WebServerDispatcher.Resource REDIRECTS_TO_RANGE_SUPPORTED =
      RANGE_SUPPORTED.buildUpon().setPath("/redirects/to/range/supported").build();

  private final MockWebServer originServer = new MockWebServer();
  private final MockWebServer redirectionServer = new MockWebServer();

  public ImmutableList<DataSourceContractTest.TestResource> getServedResources() {
    return ImmutableList.of(
        createTestResource("range supported", RANGE_SUPPORTED),
        createTestResource("range supported, length unknown", RANGE_SUPPORTED_LENGTH_UNKNOWN),
        createTestResource("range not supported", RANGE_NOT_SUPPORTED),
        createTestResource(
            "range not supported, length unknown", RANGE_NOT_SUPPORTED_LENGTH_UNKNOWN),
        createTestResource("gzip enabled", GZIP_ENABLED),
        createTestResource("gzip forced", GZIP_FORCED),
        new DataSourceContractTest.TestResource.Builder()
            .setName("302 redirect")
            .setUri(
                Uri.parse(redirectionServer.url(REDIRECTS_TO_RANGE_SUPPORTED.getPath()).toString()))
            .setResolvedUri(originServer.url(RANGE_SUPPORTED.getPath()).toString())
            .setExpectedBytes(REDIRECTS_TO_RANGE_SUPPORTED.getData())
            .build());
  }

  public ImmutableList<DataSourceContractTest.TestResource> getNotFoundResources() {
    return ImmutableList.of(
        new DataSourceContractTest.TestResource.Builder()
            .setName("404")
            .setUri(Uri.parse(originServer.url("/not/a/real/path").toString()))
            .setResponseHeaders(
                ImmutableMap.of(
                    HttpHeaders.CONTENT_LENGTH,
                    ImmutableList.of(String.valueOf(WebServerDispatcher.NOT_FOUND_BODY.length()))))
            .setExpectedBytes(Util.getUtf8Bytes(WebServerDispatcher.NOT_FOUND_BODY))
            .build(),
        new DataSourceContractTest.TestResource.Builder()
            .setName("no-connection")
            .setUri(Uri.parse("http://not-a-real-server.test/path"))
            .setUnexpectedResponseHeaderKeys(ImmutableSet.of(HttpHeaders.CONTENT_LENGTH))
            .build());
  }

  /**
   * @deprecated Use {@link #getNotFoundResources()} instead.
   */
  @Deprecated
  public String getNonexistentUrl() {
    return originServer.url("/not/a/real/path").toString();
  }

  @Override
  protected void before() throws Throwable {
    originServer.start();
    originServer.setDispatcher(
        WebServerDispatcher.forResources(
            ImmutableList.of(
                RANGE_SUPPORTED,
                RANGE_SUPPORTED_LENGTH_UNKNOWN,
                RANGE_NOT_SUPPORTED,
                RANGE_NOT_SUPPORTED_LENGTH_UNKNOWN,
                GZIP_ENABLED,
                GZIP_FORCED)));

    redirectionServer.start();
    redirectionServer.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            if (getRequestPath(request).equals(REDIRECTS_TO_RANGE_SUPPORTED.getPath())) {
              return new MockResponse()
                  .setResponseCode(302)
                  .setHeader(
                      HttpHeaders.LOCATION, originServer.url(RANGE_SUPPORTED.getPath()).toString());
            } else {
              return new MockResponse().setResponseCode(404);
            }
          }
        });
  }

  @Override
  protected void after() {
    try {
      originServer.shutdown();
      redirectionServer.shutdown();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private DataSourceContractTest.TestResource createTestResource(
      String name, WebServerDispatcher.Resource resource) {
    DataSourceContractTest.TestResource.Builder testResource =
        new DataSourceContractTest.TestResource.Builder()
            .setName(name)
            .setUri(Uri.parse(originServer.url(resource.getPath()).toString()))
            .setResponseHeaders(Maps.transformValues(EXTRA_HEADERS.asMap(), v -> (List<String>) v))
            .setExpectedBytes(resource.getData());
    if (resource.resolvesToUnknownLength()) {
      testResource.setUnexpectedResponseHeaderKeys(ImmutableSet.of(HttpHeaders.CONTENT_LENGTH));
    }
    return testResource.build();
  }
}
