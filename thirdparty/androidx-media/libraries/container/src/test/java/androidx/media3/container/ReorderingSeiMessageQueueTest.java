/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.container;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import androidx.annotation.Nullable;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ReorderingSeiMessageQueue}. */
@RunWith(AndroidJUnit4.class)
public final class ReorderingSeiMessageQueueTest {

  @Test
  public void noMaxSize_queueOnlyEmitsOnExplicitFlushCall() {
    ArrayList<SeiMessage> emittedMessages = new ArrayList<>();
    ReorderingSeiMessageQueue reorderingQueue =
        new ReorderingSeiMessageQueue(
            (presentationTimeUs, seiBuffer) ->
                emittedMessages.add(new SeiMessage(presentationTimeUs, seiBuffer)));

    // Deliberately re-use a single ParsableByteArray instance to ensure the implementation is
    // making copies as required.
    ParsableByteArray scratchData = new ParsableByteArray();
    byte[] data1 = TestUtil.buildTestData(5);
    scratchData.reset(data1);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);
    byte[] data2 = TestUtil.buildTestData(10);
    scratchData.reset(data2);
    reorderingQueue.add(/* presentationTimeUs= */ 123, scratchData);

    assertThat(emittedMessages).isEmpty();

    reorderingQueue.flush();

    assertThat(emittedMessages)
        .containsExactly(
            new SeiMessage(/* presentationTimeUs= */ 123, data2),
            new SeiMessage(/* presentationTimeUs= */ 345, data1))
        .inOrder();
  }

  @Test
  public void setMaxSize_emitsImmediatelyIfQueueIsOversized() {
    ArrayList<SeiMessage> emittedMessages = new ArrayList<>();
    ReorderingSeiMessageQueue reorderingQueue =
        new ReorderingSeiMessageQueue(
            (presentationTimeUs, seiBuffer) ->
                emittedMessages.add(new SeiMessage(presentationTimeUs, seiBuffer)));
    ParsableByteArray scratchData = new ParsableByteArray();
    byte[] data1 = TestUtil.buildTestData(5);
    scratchData.reset(data1);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);
    byte[] data2 = TestUtil.buildTestData(10);
    scratchData.reset(data2);
    reorderingQueue.add(/* presentationTimeUs= */ 123, scratchData);

    assertThat(emittedMessages).isEmpty();

    reorderingQueue.setMaxSize(1);

    assertThat(emittedMessages)
        .containsExactly(new SeiMessage(/* presentationTimeUs= */ 123, data2));
  }

  @Test
  public void withMaxSize_addEmitsWhenQueueIsFull() {
    ArrayList<SeiMessage> emittedMessages = new ArrayList<>();
    ReorderingSeiMessageQueue reorderingQueue =
        new ReorderingSeiMessageQueue(
            (presentationTimeUs, seiBuffer) ->
                emittedMessages.add(new SeiMessage(presentationTimeUs, seiBuffer)));
    reorderingQueue.setMaxSize(1);

    // Deliberately re-use a single ParsableByteArray instance to ensure the implementation is
    // copying as required.
    ParsableByteArray scratchData = new ParsableByteArray();
    byte[] data1 = TestUtil.buildTestData(5);
    scratchData.reset(data1);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);

    assertThat(emittedMessages).isEmpty();

    byte[] data2 = TestUtil.buildTestData(10);
    scratchData.reset(data2);
    reorderingQueue.add(/* presentationTimeUs= */ -123, scratchData);

    assertThat(emittedMessages)
        .containsExactly(new SeiMessage(/* presentationTimeUs= */ -123, data2));
  }

  @Test
  public void withMaxSize_addEmitsWhenQueueIsFull_handlesDuplicateTimestamps() {
    ArrayList<SeiMessage> emittedMessages = new ArrayList<>();
    ReorderingSeiMessageQueue reorderingQueue =
        new ReorderingSeiMessageQueue(
            (presentationTimeUs, seiBuffer) ->
                emittedMessages.add(new SeiMessage(presentationTimeUs, seiBuffer)));
    reorderingQueue.setMaxSize(1);

    // Deliberately re-use a single ParsableByteArray instance to ensure the implementation is
    // copying as required.
    ParsableByteArray scratchData = new ParsableByteArray();
    byte[] data1 = TestUtil.buildTestData(20);
    scratchData.reset(data1);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);
    // Add a message with a repeated timestamp which should not trigger the max size.
    byte[] data2 = TestUtil.buildTestData(15);
    scratchData.reset(data2);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);
    byte[] data3 = TestUtil.buildTestData(10);
    scratchData.reset(data3);
    reorderingQueue.add(/* presentationTimeUs= */ -123, scratchData);
    // Add another message to flush out the two t=345 messages.
    byte[] data4 = TestUtil.buildTestData(5);
    scratchData.reset(data4);
    reorderingQueue.add(/* presentationTimeUs= */ 456, scratchData);

    assertThat(emittedMessages)
        .containsExactly(
            new SeiMessage(/* presentationTimeUs= */ -123, data3),
            new SeiMessage(/* presentationTimeUs= */ 345, data1),
            new SeiMessage(/* presentationTimeUs= */ 345, data2))
        .inOrder();
  }

  /**
   * Tests that if a message smaller than all current queue items is added when the queue is full,
   * the same {@link ParsableByteArray} instance is passed straight to the output to avoid
   * unnecessary array copies or allocations.
   */
  @Test
  public void withMaxSize_addEmitsWhenQueueIsFull_skippingQueueReusesPbaInstance() {
    ReorderingSeiMessageQueue.SeiConsumer mockSeiConsumer =
        mock(ReorderingSeiMessageQueue.SeiConsumer.class);
    ReorderingSeiMessageQueue reorderingQueue = new ReorderingSeiMessageQueue(mockSeiConsumer);
    reorderingQueue.setMaxSize(1);

    ParsableByteArray scratchData = new ParsableByteArray();
    byte[] data1 = TestUtil.buildTestData(5);
    scratchData.reset(data1);
    reorderingQueue.add(/* presentationTimeUs= */ 345, scratchData);

    verifyNoInteractions(mockSeiConsumer);

    byte[] data2 = TestUtil.buildTestData(10);
    scratchData.reset(data2);
    reorderingQueue.add(/* presentationTimeUs= */ 123, scratchData);

    verify(mockSeiConsumer).consume(eq(123L), same(scratchData));
  }

  private static final class SeiMessage {
    public final long presentationTimeUs;
    public final byte[] data;

    public SeiMessage(long presentationTimeUs, ParsableByteArray seiBuffer) {
      this(
          presentationTimeUs,
          Arrays.copyOfRange(seiBuffer.getData(), seiBuffer.getPosition(), seiBuffer.limit()));
    }

    public SeiMessage(long presentationTimeUs, byte[] seiBuffer) {
      this.presentationTimeUs = presentationTimeUs;
      this.data = seiBuffer;
    }

    @Override
    public int hashCode() {
      return Objects.hash(presentationTimeUs, Arrays.hashCode(data));
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof SeiMessage)) {
        return false;
      }
      SeiMessage that = (SeiMessage) obj;
      return this.presentationTimeUs == that.presentationTimeUs
          && Arrays.equals(this.data, that.data);
    }

    @Override
    public String toString() {
      return "SeiMessage { ts=" + presentationTimeUs + ",data=0x" + Util.toHexString(data) + " }";
    }
  }
}
