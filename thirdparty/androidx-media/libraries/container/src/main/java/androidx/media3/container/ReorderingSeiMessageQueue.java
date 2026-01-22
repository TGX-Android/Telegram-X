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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.castNonNull;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/** A queue of SEI messages, ordered by presentation timestamp. */
@UnstableApi
@RestrictTo(LIBRARY_GROUP)
public final class ReorderingSeiMessageQueue {

  /** Functional interface to handle an SEI message that is being removed from the queue. */
  public interface SeiConsumer {
    /** Handles an SEI message that is being removed from the queue. */
    void consume(long presentationTimeUs, ParsableByteArray seiBuffer);
  }

  private final SeiConsumer seiConsumer;

  /** Pool of re-usable {@link ParsableByteArray} objects to avoid repeated allocations. */
  private final ArrayDeque<ParsableByteArray> unusedParsableByteArrays;

  /** Pool of re-usable {@link SampleSeiMessages} objects to avoid repeated allocations. */
  private final ArrayDeque<SampleSeiMessages> unusedSampleSeiMessages;

  private final PriorityQueue<SampleSeiMessages> pendingSeiMessages;

  private int reorderingQueueSize;
  @Nullable private SampleSeiMessages lastQueuedMessage;

  /**
   * Creates an instance, initially with no max size.
   *
   * @param seiConsumer Callback to invoke when SEI messages are removed from the head of queue,
   *     either due to exceeding the {@linkplain #setMaxSize(int) max queue size} during a call to
   *     {@link #add(long, ParsableByteArray)}, or due to {@link #flush()}.
   */
  public ReorderingSeiMessageQueue(SeiConsumer seiConsumer) {
    this.seiConsumer = seiConsumer;
    unusedParsableByteArrays = new ArrayDeque<>();
    unusedSampleSeiMessages = new ArrayDeque<>();
    pendingSeiMessages = new PriorityQueue<>();
    reorderingQueueSize = C.LENGTH_UNSET;
  }

  /**
   * Sets the max size of the re-ordering queue.
   *
   * <p>The size is defined in terms of the number of unique presentation timestamps, rather than
   * the number of messages. This ensures that properties like H.264's {@code
   * max_number_reorder_frames} can be used to set this max size in the case of multiple SEI
   * messages per sample (where multiple SEI messages therefore have the same presentation
   * timestamp).
   *
   * <p>When the queue exceeds this size during a call to {@link #add(long, ParsableByteArray)}, the
   * messages associated with the least timestamp are passed to the {@link SeiConsumer} provided
   * during construction.
   *
   * <p>If the new size is larger than the number of elements currently in the queue, items are
   * removed from the head of the queue (least first) and passed to the {@link SeiConsumer} provided
   * during construction.
   */
  public void setMaxSize(int reorderingQueueSize) {
    checkState(reorderingQueueSize >= 0);
    this.reorderingQueueSize = reorderingQueueSize;
    flushQueueDownToSize(reorderingQueueSize);
  }

  /**
   * Returns the maximum size of this queue, or {@link C#LENGTH_UNSET} if it is unbounded.
   *
   * <p>See {@link #setMaxSize(int)} for details on how size is defined.
   */
  public int getMaxSize() {
    return reorderingQueueSize;
  }

  /**
   * Adds a message to the queue.
   *
   * <p>If this causes the queue to exceed its {@linkplain #setMaxSize(int) max size}, messages
   * associated with the least timestamp (which may be the message passed to this method) are passed
   * to the {@link SeiConsumer} provided during construction.
   *
   * <p>Messages with matching timestamps must be added consecutively (this will naturally happen
   * when parsing messages from a container).
   *
   * @param presentationTimeUs The presentation time of the SEI message.
   * @param seiBuffer The SEI data. The data will be copied, so the provided object can be re-used
   *     after this method returns.
   */
  public void add(long presentationTimeUs, ParsableByteArray seiBuffer) {
    if (reorderingQueueSize == 0
        || (reorderingQueueSize != C.LENGTH_UNSET
            && pendingSeiMessages.size() >= reorderingQueueSize
            && presentationTimeUs < castNonNull(pendingSeiMessages.peek()).presentationTimeUs)) {
      seiConsumer.consume(presentationTimeUs, seiBuffer);
      return;
    }
    // Make a local copy of the SEI data so we can store it in the queue and allow the seiBuffer
    // parameter to be safely re-used after this add() method returns.
    ParsableByteArray seiBufferCopy = copy(seiBuffer);
    if (lastQueuedMessage != null && presentationTimeUs == lastQueuedMessage.presentationTimeUs) {
      lastQueuedMessage.nalBuffers.add(seiBufferCopy);
      return;
    }
    SampleSeiMessages sampleSeiMessages =
        unusedSampleSeiMessages.isEmpty() ? new SampleSeiMessages() : unusedSampleSeiMessages.pop();
    sampleSeiMessages.init(presentationTimeUs, seiBufferCopy);
    pendingSeiMessages.add(sampleSeiMessages);
    lastQueuedMessage = sampleSeiMessages;
    if (reorderingQueueSize != C.LENGTH_UNSET) {
      flushQueueDownToSize(reorderingQueueSize);
    }
  }

  /**
   * Copies {@code input} into a {@link ParsableByteArray} instance from {@link
   * #unusedParsableByteArrays}, or a new instance if that is empty.
   */
  private ParsableByteArray copy(ParsableByteArray input) {
    ParsableByteArray result =
        unusedParsableByteArrays.isEmpty()
            ? new ParsableByteArray()
            : unusedParsableByteArrays.pop();
    result.reset(input.bytesLeft());
    System.arraycopy(
        /* src= */ input.getData(),
        /* srcPos= */ input.getPosition(),
        /* dest= */ result.getData(),
        /* destPos= */ 0,
        /* length= */ result.bytesLeft());
    return result;
  }

  /** Empties the queue, discarding all previously {@linkplain #add added} messages. */
  public void clear() {
    pendingSeiMessages.clear();
  }

  /**
   * Empties the queue, passing all messages (least first) to the {@link SeiConsumer} provided
   * during construction.
   */
  public void flush() {
    flushQueueDownToSize(0);
  }

  private void flushQueueDownToSize(int targetSize) {
    while (pendingSeiMessages.size() > targetSize) {
      SampleSeiMessages sampleSeiMessages = castNonNull(pendingSeiMessages.poll());
      for (int i = 0; i < sampleSeiMessages.nalBuffers.size(); i++) {
        seiConsumer.consume(
            sampleSeiMessages.presentationTimeUs, sampleSeiMessages.nalBuffers.get(i));
        unusedParsableByteArrays.push(sampleSeiMessages.nalBuffers.get(i));
      }
      sampleSeiMessages.nalBuffers.clear();
      if (lastQueuedMessage != null
          && lastQueuedMessage.presentationTimeUs == sampleSeiMessages.presentationTimeUs) {
        lastQueuedMessage = null;
      }
      unusedSampleSeiMessages.push(sampleSeiMessages);
    }
  }

  /** Holds the presentation timestamp of a sample and the data from associated SEI messages. */
  private static final class SampleSeiMessages implements Comparable<SampleSeiMessages> {

    public final List<ParsableByteArray> nalBuffers;
    public long presentationTimeUs;

    public SampleSeiMessages() {
      presentationTimeUs = C.TIME_UNSET;
      nalBuffers = new ArrayList<>();
    }

    public void init(long presentationTimeUs, ParsableByteArray nalBuffer) {
      checkArgument(presentationTimeUs != C.TIME_UNSET);
      checkState(this.nalBuffers.isEmpty());
      this.presentationTimeUs = presentationTimeUs;
      this.nalBuffers.add(nalBuffer);
    }

    @Override
    public int compareTo(SampleSeiMessages other) {
      return Long.compare(this.presentationTimeUs, other.presentationTimeUs);
    }
  }
}
