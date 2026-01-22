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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.castNonNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * A collection of contract tests for {@link DataSource} implementations.
 *
 * <p>Subclasses should only include the logic necessary to construct the DataSource (overriding
 * either {@link #createDataSource()} or {@link #createDataSources()}) and allow it to successfully
 * read data (overriding {@link #getTestResources()}. They shouldn't include any new {@link
 * Test @Test} methods - implementation-specific tests should be in a separate class.
 *
 * <p>Most implementations should pass all these tests. If necessary, subclasses can disable tests
 * by overriding the {@link Test @Test} method with a no-op implementation. It's recommended (but
 * not required) to also annotate this {@link Ignore @Ignore} so that JUnit correctly reports the
 * test as skipped/ignored instead of passing.
 */
@UnstableApi
public abstract class DataSourceContractTest {

  @Rule public final AdditionalFailureInfo additionalFailureInfo = new AdditionalFailureInfo();

  /**
   * Creates and returns an instance of the {@link DataSource}.
   *
   * <p>Only one of {@link #createDataSource()} and {@link #createDataSources()} should be
   * implemented.
   */
  @ForOverride
  protected DataSource createDataSource() throws Exception {
    throw new UnsupportedOperationException(
        "Either createDataSource or createDataSources must be implemented.");
  }

  /**
   * Creates and returns a list of instances of the {@link DataSource}.
   *
   * <p>Only one of {@link #createDataSource()} and {@link #createDataSources()} should be
   * implemented.
   */
  @ForOverride
  protected List<DataSource> createDataSources() throws Exception {
    throw new UnsupportedOperationException(
        "Either createDataSource or createDataSources must be implemented.");
  }

  /**
   * Returns the {@link DataSource} that will be included in the {@link TransferListener} callbacks
   * for the {@link DataSource} most recently created by {@link #createDataSource()}. If it's the
   * same {@link DataSource} then {@code null} can be returned.
   */
  @Nullable
  @ForOverride
  protected DataSource getTransferListenerDataSource() {
    return null;
  }

  /**
   * Returns whether the {@link DataSource} will continue reading indefinitely for unbounded {@link
   * DataSpec DataSpecs}.
   */
  @ForOverride
  protected boolean unboundedReadsAreIndefinite() {
    return false;
  }

  /**
   * Returns {@link TestResource} instances.
   *
   * <p>Each resource will be used to exercise the {@link DataSource} instance, allowing different
   * behaviours to be tested. Every {@link TestResource#getExpectedBytes()} must be at least 5
   * bytes.
   *
   * <p>If multiple resources are returned, it's recommended to disambiguate them using {@link
   * TestResource.Builder#setName(String)}.
   */
  @ForOverride
  protected abstract ImmutableList<TestResource> getTestResources() throws Exception;

  /**
   * Returns a {@link Uri} that doesn't resolve.
   *
   * <p>This is used to test how a {@link DataSource} handles nonexistent data.
   *
   * <p>Only one of {@link #getNotFoundUri()} and {@link #getNotFoundResources()} should be
   * implemented.
   */
  @ForOverride
  protected Uri getNotFoundUri() {
    throw new UnsupportedOperationException(
        "Either getNotFoundUri or getNotFoundUris must be implemented.");
  }

  /**
   * Returns a non-empty list of {@link TestResource} that don't resolve.
   *
   * <p>This is used to test how a {@link DataSource} handles nonexistent data. Multiple entries and
   * the rest of the {@link TestResource} fields can be helpful for situations where the data can be
   * "not found" for different reasons. For example in HTTP, 'server not found' generally results in
   * a failed HTTP connection while 'file not found' generally results in a successful connection
   * with a 404 HTTP error code and some response headers, and the handling code for these two cases
   * may be different (and therefore worth testing separately).
   *
   * <p>Only one of {@link #getNotFoundUri()} and {@link #getNotFoundResources()} should be
   * implemented.
   */
  @ForOverride
  protected List<TestResource> getNotFoundResources() {
    throw new UnsupportedOperationException(
        "Either getNotFoundUri or getNotFoundUris must be implemented.");
  }

  @Test
  public void unboundedDataSpec_readUntilEnd() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            long length = dataSource.open(new DataSpec(resource.getUri()));
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? DataSourceUtil.readExactly(dataSource, resource.getExpectedBytes().length)
                    : DataSourceUtil.readToEnd(dataSource);

            if (length != C.LENGTH_UNSET) {
              assertThat(length).isEqualTo(resource.getExpectedBytes().length);
            }
            assertThat(data).isEqualTo(resource.getExpectedBytes());
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void unboundedDataSpec_readExpectedBytesWithOffset() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            dataSource.open(new DataSpec.Builder().setUri(resource.getUri()).build());
            int offset = 2;
            byte[] data = new byte[resource.getExpectedBytes().length + offset];
            // Initialize the first two bytes with non-zero values.
            data[0] = (byte) 0xA5;
            data[1] = (byte) 0x5A;

            while (offset != data.length) {
              int bytesRead = dataSource.read(data, offset, resource.getExpectedBytes().length);

              if (bytesRead == C.RESULT_END_OF_INPUT) {
                break;
              }

              offset += bytesRead;
            }

            // Assert that the first two bytes have not been modified.
            assertThat(data[0]).isEqualTo((byte) 0xA5);
            assertThat(data[1]).isEqualTo((byte) 0x5A);

            assertThat(offset).isEqualTo(data.length);
            byte[] actualData = Arrays.copyOfRange(data, 2, data.length);
            assertThat(actualData).isEqualTo(resource.getExpectedBytes());
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithPosition_readUntilEnd() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            long length =
                dataSource.open(
                    new DataSpec.Builder().setUri(resource.getUri()).setPosition(3).build());
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? DataSourceUtil.readExactly(dataSource, resource.getExpectedBytes().length - 3)
                    : DataSourceUtil.readToEnd(dataSource);

            if (length != C.LENGTH_UNSET) {
              assertThat(length).isEqualTo(resource.getExpectedBytes().length - 3);
            }
            byte[] expectedData =
                Arrays.copyOfRange(
                    resource.getExpectedBytes(), 3, resource.getExpectedBytes().length);
            assertThat(data).isEqualTo(expectedData);
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithLength_readExpectedRange() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            long length =
                dataSource.open(
                    new DataSpec.Builder().setUri(resource.getUri()).setLength(4).build());
            byte[] data = DataSourceUtil.readToEnd(dataSource);

            assertThat(length).isEqualTo(4);
            byte[] expectedData = Arrays.copyOf(resource.getExpectedBytes(), 4);
            assertThat(data).isEqualTo(expectedData);
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithLength_readUntilEndInTwoParts() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            int length = resource.getExpectedBytes().length;
            dataSource.open(
                new DataSpec.Builder().setUri(resource.getUri()).setLength(length).build());

            byte[] firstPartData = DataSourceUtil.readExactly(dataSource, length / 2);
            byte[] secondPartData = DataSourceUtil.readToEnd(dataSource);

            byte[] expectedFirstPartData = Arrays.copyOf(resource.getExpectedBytes(), length / 2);
            byte[] expectedSecondPartData =
                Arrays.copyOfRange(resource.getExpectedBytes(), length / 2, length);
            assertThat(firstPartData).isEqualTo(expectedFirstPartData);
            assertThat(secondPartData).isEqualTo(expectedSecondPartData);
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithPositionAndLength_readExpectedRange() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            long length =
                dataSource.open(
                    new DataSpec.Builder()
                        .setUri(resource.getUri())
                        .setPosition(2)
                        .setLength(2)
                        .build());
            byte[] data = DataSourceUtil.readToEnd(dataSource);

            assertThat(length).isEqualTo(2);
            byte[] expectedData = Arrays.copyOfRange(resource.getExpectedBytes(), 2, 4);
            assertThat(data).isEqualTo(expectedData);
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithPositionAtEnd_readsZeroBytes() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          int resourceLength = resource.getExpectedBytes().length;
          DataSpec dataSpec =
              new DataSpec.Builder().setUri(resource.getUri()).setPosition(resourceLength).build();
          try {
            long length = dataSource.open(dataSpec);
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? Util.EMPTY_BYTE_ARRAY
                    : DataSourceUtil.readToEnd(dataSource);

            // The DataSource.open() contract requires the returned length to equal the length in
            // the DataSpec if set. This is true even though the DataSource implementation may know
            // that fewer bytes will be read in this case.
            if (length != C.LENGTH_UNSET) {
              assertThat(length).isEqualTo(0);
            }
            assertThat(data).isEmpty();
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithPositionAtEndAndLength_readsZeroBytes() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          int resourceLength = resource.getExpectedBytes().length;
          DataSpec dataSpec =
              new DataSpec.Builder()
                  .setUri(resource.getUri())
                  .setPosition(resourceLength)
                  .setLength(1)
                  .build();
          try {
            long length = dataSource.open(dataSpec);
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? Util.EMPTY_BYTE_ARRAY
                    : DataSourceUtil.readToEnd(dataSource);

            // The DataSource.open() contract requires the returned length to equal the length in
            // the DataSpec if set. This is true even though the DataSource implementation may know
            // that fewer bytes will be read in this case.
            assertThat(length).isEqualTo(1);
            assertThat(data).isEmpty();
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithPositionOutOfRange_throwsPositionOutOfRangeException() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          int resourceLength = resource.getExpectedBytes().length;
          DataSpec dataSpec =
              new DataSpec.Builder()
                  .setUri(resource.getUri())
                  .setPosition(resourceLength + 1)
                  .build();
          try {
            IOException exception =
                assertThrows(IOException.class, () -> dataSource.open(dataSpec));
            assertThat(DataSourceException.isCausedByPositionOutOfRange(exception)).isTrue();
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void dataSpecWithEndPositionOutOfRange_readsToEnd() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          int resourceLength = resource.getExpectedBytes().length;
          DataSpec dataSpec =
              new DataSpec.Builder()
                  .setUri(resource.getUri())
                  .setPosition(resourceLength - 1)
                  .setLength(2)
                  .build();
          try {
            long length = dataSource.open(dataSpec);
            byte[] data = DataSourceUtil.readExactly(dataSource, /* length= */ 1);
            // TODO: Decide what the allowed behavior should be for the next read, and assert it.

            // The DataSource.open() contract requires the returned length to equal the length in
            // the DataSpec if set. This is true even though the DataSource implementation may know
            // that fewer bytes will be read in this case.
            assertThat(length).isEqualTo(2);
            byte[] expectedData =
                Arrays.copyOfRange(resource.getExpectedBytes(), resourceLength - 1, resourceLength);
            assertThat(data).isEqualTo(expectedData);
          } finally {
            dataSource.close();
          }
        });
  }

  /**
   * {@link DataSpec#FLAG_ALLOW_GZIP} should either be ignored by {@link DataSource}
   * implementations, or correctly handled (i.e. the data is decompressed before being returned from
   * {@link DataSource#read(byte[], int, int)}).
   */
  @Test
  public void unboundedDataSpecWithGzipFlag_readUntilEnd() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            long length =
                dataSource.open(
                    new DataSpec.Builder()
                        .setUri(resource.getUri())
                        .setFlags(DataSpec.FLAG_ALLOW_GZIP)
                        .build());
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? DataSourceUtil.readExactly(dataSource, resource.getExpectedBytes().length)
                    : DataSourceUtil.readToEnd(dataSource);

            if (length != C.LENGTH_UNSET) {
              assertThat(length).isEqualTo(resource.getExpectedBytes().length);
            }
            assertThat(data).isEqualTo(resource.getExpectedBytes());
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void uriSchemeIsCaseInsensitive() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          @Nullable String scheme = resource.getUri().getScheme();
          if (scheme == null) {
            // No scheme for which to check case-insensitivity.
            return;
          }
          Uri uri =
              resource
                  .getUri()
                  .buildUpon()
                  .scheme(invertAsciiCaseOfEveryOtherCharacter(scheme))
                  .build();
          try {
            long length = dataSource.open(new DataSpec.Builder().setUri(uri).build());
            byte[] data =
                unboundedReadsAreIndefinite()
                    ? DataSourceUtil.readExactly(dataSource, resource.getExpectedBytes().length)
                    : DataSourceUtil.readToEnd(dataSource);

            if (length != C.LENGTH_UNSET) {
              assertThat(length).isEqualTo(resource.getExpectedBytes().length);
            }
            assertThat(data).isEqualTo(resource.getExpectedBytes());
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void resourceNotFound() throws Exception {
    forAllDataSourcesAndNotFoundResources(
        (resource, dataSource) -> {
          assertThrows(IOException.class, () -> dataSource.open(new DataSpec(resource.uri)));

          dataSource.close();
        });
  }

  @Test
  public void transferListenerCallbacks() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          FakeTransferListener listener = spy(new FakeTransferListener());
          dataSource.addTransferListener(listener);
          InOrder inOrder = Mockito.inOrder(listener);
          @Nullable DataSource callbackSource = getTransferListenerDataSource();
          if (callbackSource == null) {
            callbackSource = dataSource;
          }
          DataSpec reportedDataSpec = null;
          boolean reportedNetwork = false;

          DataSpec dataSpec = new DataSpec.Builder().setUri(resource.getUri()).build();
          try {
            dataSource.open(dataSpec);

            // Verify onTransferInitializing() and onTransferStart() have been called exactly from
            // DataSource.open().
            ArgumentCaptor<DataSpec> dataSpecArgumentCaptor =
                ArgumentCaptor.forClass(DataSpec.class);
            ArgumentCaptor<Boolean> isNetworkArgumentCaptor =
                ArgumentCaptor.forClass(Boolean.class);
            inOrder
                .verify(listener)
                .onTransferInitializing(
                    eq(callbackSource),
                    dataSpecArgumentCaptor.capture(),
                    isNetworkArgumentCaptor.capture());
            reportedDataSpec = dataSpecArgumentCaptor.getValue();
            reportedNetwork = isNetworkArgumentCaptor.getValue();
            inOrder
                .verify(listener)
                .onTransferStart(callbackSource, castNonNull(reportedDataSpec), reportedNetwork);
            inOrder.verifyNoMoreInteractions();

            if (unboundedReadsAreIndefinite()) {
              DataSourceUtil.readExactly(dataSource, resource.getExpectedBytes().length);
            } else {
              DataSourceUtil.readToEnd(dataSource);
            }
            // Verify sufficient onBytesTransferred() callbacks have been triggered before closing
            // the DataSource.
            assertThat(listener.bytesTransferred).isAtLeast(resource.getExpectedBytes().length);

          } finally {
            dataSource.close();
            inOrder
                .verify(listener)
                .onTransferEnd(callbackSource, castNonNull(reportedDataSpec), reportedNetwork);
            inOrder.verifyNoMoreInteractions();
          }
        });
  }

  @Test
  public void resourceNotFound_transferListenerCallbacks() throws Exception {
    forAllDataSourcesAndNotFoundResources(
        (resource, dataSource) -> {
          TransferListener listener = mock(TransferListener.class);
          dataSource.addTransferListener(listener);
          @Nullable DataSource callbackSource = getTransferListenerDataSource();
          if (callbackSource == null) {
            callbackSource = dataSource;
          }

          assertThrows(IOException.class, () -> dataSource.open(new DataSpec(resource.uri)));

          // Verify onTransferInitializing() has been called exactly from DataSource.open().
          verify(listener).onTransferInitializing(eq(callbackSource), any(), anyBoolean());
          verifyNoMoreInteractions(listener);

          dataSource.close();
          verifyNoMoreInteractions(listener);
        });
  }

  @Test
  public void getUri_returnsExpectedValueOnlyWhileOpen() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            assertThat(dataSource.getUri()).isNull();

            dataSource.open(new DataSpec(resource.getUri()));

            assertThat(dataSource.getUri()).isEqualTo(resource.getResolvedUri());
          } finally {
            dataSource.close();
          }
          assertThat(dataSource.getUri()).isNull();
        });
  }

  @Test
  public void getUri_resourceNotFound_returnsNullIfNotOpened() throws Exception {
    forAllDataSourcesAndNotFoundResources(
        (resource, dataSource) -> {
          assertThat(dataSource.getUri()).isNull();
          assertThrows(IOException.class, () -> dataSource.open(new DataSpec(resource.uri)));
          assertThat(dataSource.getUri()).isEqualTo(resource.uri);
          dataSource.close();
          assertThat(dataSource.getUri()).isNull();
        });
  }

  @Test
  public void getResponseHeaders_returnsExpectedValues() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            dataSource.open(new DataSpec(resource.getUri()));
            // Iterate over the expected headers (instead of using Truth's batch
            // containsAtLeastEntriesIn() method) in order to leverage the case-insensitivity of
            // the DataSource.getResponseHeaders() implementation.
            Map<String, List<String>> actualHeaders = dataSource.getResponseHeaders();
            for (Map.Entry<String, List<String>> expectedHeaders :
                resource.getResponseHeaders().entrySet()) {
              assertWithMessage("Header values for key=%s", expectedHeaders.getKey())
                  .that(actualHeaders.get(expectedHeaders.getKey()))
                  .isEqualTo(expectedHeaders.getValue());
            }
            for (String unexpectedKey : resource.getUnexpectedResponseHeaderKeys()) {
              assertThat(actualHeaders).doesNotContainKey(unexpectedKey);
            }
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void getResponseHeaders_noNullKeysOrValues() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            dataSource.open(new DataSpec(resource.getUri()));

            Map<String, List<String>> responseHeaders = dataSource.getResponseHeaders();
            assertThat(responseHeaders).doesNotContainKey(null);
            assertThat(responseHeaders.values()).doesNotContain(null);
            for (List<String> value : responseHeaders.values()) {
              assertThat(value).doesNotContain(null);
            }
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void getResponseHeaders_caseInsensitive() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            dataSource.open(new DataSpec(resource.getUri()));

            Map<String, List<String>> responseHeaders = dataSource.getResponseHeaders();
            for (String key : responseHeaders.keySet()) {
              String caseFlippedKey = invertAsciiCaseOfEveryOtherCharacter(key);
              assertWithMessage("key='%s', caseFlippedKey='%s'", key, caseFlippedKey)
                  .that(responseHeaders.get(caseFlippedKey))
                  .isEqualTo(responseHeaders.get(key));
            }
          } finally {
            dataSource.close();
          }
        });
  }

  @Test
  public void getResponseHeaders_isEmptyWhileNotOpen() throws Exception {
    forAllTestResourcesAndDataSources(
        (resource, dataSource) -> {
          try {
            assertThat(dataSource.getResponseHeaders()).isEmpty();

            dataSource.open(new DataSpec(resource.getUri()));
          } finally {
            dataSource.close();
          }
          assertThat(dataSource.getResponseHeaders()).isEmpty();
        });
  }

  @Test
  public void getResponseHeaders_resourceNotFound_isEmptyWhileNotOpen() throws Exception {
    forAllDataSourcesAndNotFoundResources(
        (resource, dataSource) -> {
          assertThat(dataSource.getResponseHeaders()).isEmpty();

          assertThrows(IOException.class, () -> dataSource.open(new DataSpec(resource.uri)));

          Map<String, List<String>> actualHeaders = dataSource.getResponseHeaders();
          for (Map.Entry<String, List<String>> expectedHeaders :
              resource.getResponseHeaders().entrySet()) {
            assertWithMessage("Header values for key=%s", expectedHeaders.getKey())
                .that(actualHeaders.get(expectedHeaders.getKey()))
                .isEqualTo(expectedHeaders.getValue());
          }
          for (String unexpectedKey : resource.getUnexpectedResponseHeaderKeys()) {
            assertThat(actualHeaders).doesNotContainKey(unexpectedKey);
          }

          dataSource.close();

          assertThat(dataSource.getResponseHeaders()).isEmpty();
        });
  }

  private interface TestResourceAndDataSourceTest {
    void run(TestResource resource, DataSource dataSource) throws Exception;
  }

  private void forAllTestResourcesAndDataSources(TestResourceAndDataSourceTest test)
      throws Exception {
    ImmutableList<TestResource> resources = getTestResources();
    Assertions.checkArgument(!resources.isEmpty(), "Must provide at least one test resource.");
    for (int i = 0; i < resources.size(); i++) {
      checkState(
          resources.get(i).expectedBytes.length >= 5,
          "TestResource.expectedBytes must be at least 5 bytes");
      List<DataSource> dataSources = createDataSourcesInternal();
      for (int j = 0; j < dataSources.size(); j++) {
        additionalFailureInfo.setInfo(getFailureLabel(resources, i, dataSources, j));
        test.run(resources.get(i), dataSources.get(j));
        additionalFailureInfo.setInfo(null);
      }
    }
  }

  private void forAllDataSourcesAndNotFoundResources(TestResourceAndDataSourceTest test)
      throws Exception {
    List<TestResource> notFoundResources = getNotFoundResourcesInternal();
    for (int i = 0; i < notFoundResources.size(); i++) {
      List<DataSource> dataSources = createDataSourcesInternal();
      for (int j = 0; j < dataSources.size(); j++) {
        additionalFailureInfo.setInfo(
            getNotFoundResourceLabel(notFoundResources, i, dataSources, j));
        test.run(notFoundResources.get(i), dataSources.get(j));
        additionalFailureInfo.setInfo(null);
      }
    }
  }

  private List<DataSource> createDataSourcesInternal() throws Exception {
    try {
      List<DataSource> dataSources = createDataSources();
      checkState(!dataSources.isEmpty(), "Must provide at least one DataSource");
      assertThrows(UnsupportedOperationException.class, this::createDataSource);
      return dataSources;
    } catch (UnsupportedOperationException e) {
      // Expected if createDataSources is not implemented.
      return ImmutableList.of(createDataSource());
    }
  }

  private List<TestResource> getNotFoundResourcesInternal() {
    try {
      List<TestResource> notFoundResources = getNotFoundResources();
      checkState(!notFoundResources.isEmpty(), "Must provide at least one 'not found' resource");
      assertThrows(UnsupportedOperationException.class, this::getNotFoundUri);
      return notFoundResources;
    } catch (UnsupportedOperationException e) {
      // Expected if createDataSources is not implemented.
      return ImmutableList.of(
          new TestResource.Builder()
              .setUri(getNotFoundUri())
              .setExpectedBytes(Util.EMPTY_BYTE_ARRAY)
              .build());
    }
  }

  /**
   * Build a label to make it clear which not-found resource and data source caused a given test
   * failure.
   */
  private static String getNotFoundResourceLabel(
      List<TestResource> resources,
      int resourceIndex,
      List<DataSource> dataSources,
      int dataSourceIndex) {
    return getFailureLabel("not-found", resources, resourceIndex, dataSources, dataSourceIndex);
  }

  /** Build a label to make it clear which resource and data source caused a given test failure. */
  private static String getFailureLabel(
      List<TestResource> resources,
      int resourceIndex,
      List<DataSource> dataSources,
      int dataSourceIndex) {
    return getFailureLabel("resources", resources, resourceIndex, dataSources, dataSourceIndex);
  }

  private static String getFailureLabel(
      String resourcesType,
      List<TestResource> resources,
      int resourceIndex,
      List<DataSource> dataSources,
      int dataSourceIndex) {
    String resourceLabel = getResourceLabel(resourcesType, resources, resourceIndex);
    String dataSourceLabel = getDataSourceLabel(dataSources, dataSourceIndex);
    if (resourceLabel.isEmpty()) {
      return dataSourceLabel;
    } else if (dataSourceLabel.isEmpty()) {
      return resourceLabel;
    } else {
      return dataSourceLabel + ", " + resourceLabel;
    }
  }

  private static String getResourceLabel(
      String resourcesType, List<TestResource> resources, int resourceIndex) {
    if (resources.size() == 1) {
      return "";
    } else if (resources.get(resourceIndex).getName() != null) {
      return "resource name: " + resources.get(resourceIndex).getName();
    } else {
      return String.format("%s[%s]", resourcesType, resourceIndex);
    }
  }

  private static String getDataSourceLabel(List<DataSource> dataSources, int dataSourceIndex) {
    if (dataSources.size() == 1) {
      return "";
    }
    return String.format("dataSource[%s]", dataSourceIndex);
  }

  private static String invertAsciiCaseOfEveryOtherCharacter(String input) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      result.append(i % 2 == 0 ? invertAsciiCase(input.charAt(i)) : input.charAt(i));
    }
    return result.toString();
  }

  /**
   * Returns {@code c} in the opposite case if it's an ASCII character, otherwise returns {@code c}
   * unchanged.
   */
  private static char invertAsciiCase(char c) {
    if (Ascii.isUpperCase(c)) {
      return Ascii.toLowerCase(c);
    } else if (Ascii.isLowerCase(c)) {
      return Ascii.toUpperCase(c);
    } else {
      return c;
    }
  }

  /** Information about a resource that can be used to test the {@link DataSource} instance. */
  public static final class TestResource {

    @Nullable private final String name;
    private final Uri uri;
    private final Uri resolvedUri;
    private final Map<String, List<String>> responseHeaders;
    private final Set<String> unexpectedResponseHeaderKeys;
    private final byte[] expectedBytes;

    private TestResource(
        @Nullable String name,
        Uri uri,
        Uri resolvedUri,
        Map<String, List<String>> responseHeaders,
        Set<String> unexpectedResponseHeaderKeys,
        byte[] expectedBytes) {
      this.name = name;
      this.uri = uri;
      this.resolvedUri = resolvedUri;
      this.responseHeaders = responseHeaders;
      this.unexpectedResponseHeaderKeys = unexpectedResponseHeaderKeys;
      this.expectedBytes = expectedBytes;
    }

    /** Returns a human-readable name for the resource, for use in test failure messages. */
    @Nullable
    public String getName() {
      return name;
    }

    /** Returns the URI where the resource should be requested from. */
    public Uri getUri() {
      return uri;
    }

    /**
     * Returns the URI where the resource is served from. This is equal to {@link #getUri()} unless
     * redirection occurred when opening the resource.
     */
    public Uri getResolvedUri() {
      return resolvedUri;
    }

    /**
     * Returns the headers associated with this resource that are expected to be present in {@link
     * DataSource#getResponseHeaders()}.
     *
     * <p>This doesn't have to be an exhaustive list, extra headers in {@link
     * DataSource#getResponseHeaders()} are ignored.
     */
    public Map<String, List<String>> getResponseHeaders() {
      return responseHeaders;
    }

    /**
     * Returns the keys that must <b>not</b> be present in {@link DataSource#getResponseHeaders()}
     * when reading this resource.
     */
    public Set<String> getUnexpectedResponseHeaderKeys() {
      return unexpectedResponseHeaderKeys;
    }

    /** Returns the expected contents of this resource. */
    public byte[] getExpectedBytes() {
      return expectedBytes;
    }

    /** Builder for {@link TestResource} instances. */
    public static final class Builder {
      private @MonotonicNonNull String name;
      private @MonotonicNonNull Uri uri;
      private @MonotonicNonNull Uri resolvedUri;
      private Map<String, List<String>> responseHeaders;
      private Set<String> unexpectedResponseHeaderKeys;
      private byte[] expectedBytes;

      public Builder() {
        responseHeaders = ImmutableMap.of();
        unexpectedResponseHeaderKeys = ImmutableSet.of();
        expectedBytes = Util.EMPTY_BYTE_ARRAY;
      }

      /**
       * Sets a human-readable name for this resource which will be shown in test failure messages.
       */
      @CanIgnoreReturnValue
      public Builder setName(String name) {
        this.name = name;
        return this;
      }

      /** Sets the URI where this resource should be requested from. */
      @CanIgnoreReturnValue
      public Builder setUri(String uri) {
        return setUri(Uri.parse(uri));
      }

      /** Sets the URI where this resource should be requested from. */
      @CanIgnoreReturnValue
      public Builder setUri(Uri uri) {
        this.uri = uri;
        return this;
      }

      /**
       * Sets the URI where this resource is served from. This only needs to be explicitly set if
       * it's different to {@link #setUri(Uri)}. See {@link #getResolvedUri()}.
       */
      @CanIgnoreReturnValue
      public Builder setResolvedUri(String uri) {
        return setResolvedUri(Uri.parse(uri));
      }

      /**
       * Sets the URI where this resource is served from. This only needs to be explicitly set if
       * it's different to {@link #setUri(Uri)}. See {@link #getResolvedUri()}.
       */
      @CanIgnoreReturnValue
      public Builder setResolvedUri(Uri uri) {
        this.resolvedUri = uri;
        return this;
      }

      /**
       * Sets the headers associated with this resource that are expected to be present in {@link
       * DataSource#getResponseHeaders()}.
       *
       * <p>This doesn't have to be an exhaustive list, extra headers in {@link
       * DataSource#getResponseHeaders()} are ignored. Defaults to an empty map.
       */
      @CanIgnoreReturnValue
      public Builder setResponseHeaders(Map<String, List<String>> responseHeaders) {
        this.responseHeaders = responseHeaders;
        return this;
      }

      /**
       * Sets the keys that must <b>not</b> be present in {@link DataSource#getResponseHeaders()}
       * when reading this resource. Defaults to an empty set.
       */
      @CanIgnoreReturnValue
      public Builder setUnexpectedResponseHeaderKeys(Set<String> unexpectedResponseHeaderKeys) {
        this.unexpectedResponseHeaderKeys = unexpectedResponseHeaderKeys;
        return this;
      }

      /** Sets the expected contents of this resource. Defaults to an empty byte array. */
      @CanIgnoreReturnValue
      public Builder setExpectedBytes(byte[] expectedBytes) {
        this.expectedBytes = checkNotNull(expectedBytes);
        return this;
      }

      public TestResource build() {
        return new TestResource(
            name,
            checkNotNull(uri),
            resolvedUri != null ? resolvedUri : uri,
            ImmutableMap.copyOf(responseHeaders),
            ImmutableSet.copyOf(unexpectedResponseHeaderKeys),
            expectedBytes);
      }
    }
  }

  /** A {@link TransferListener} that only keeps track of the transferred bytes. */
  public static class FakeTransferListener implements TransferListener {
    private int bytesTransferred;

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {}

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {}

    @Override
    public void onBytesTransferred(
        DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
      this.bytesTransferred += bytesTransferred;
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
  }
}
