/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.max;

import java.nio.ByteBuffer;

/** A simple linear allocator for {@link ByteBuffer} instances. */
/* package */ final class LinearByteBufferAllocator implements ByteBufferAllocator {

  private ByteBuffer memoryPool;

  /**
   * Creates a new instance.
   *
   * @param initialCapacity The initial capacity reserved by the linear allocator.
   */
  public LinearByteBufferAllocator(int initialCapacity) {
    checkArgument(initialCapacity >= 0);
    memoryPool = ByteBuffer.allocateDirect(initialCapacity);
  }

  @Override
  public ByteBuffer allocate(int capacity) {
    checkArgument(capacity >= 0);
    if (memoryPool.remaining() < capacity) {
      int newCapacity = max(capacity, memoryPool.capacity() * 2);
      memoryPool = ByteBuffer.allocateDirect(newCapacity);
    }
    ByteBuffer outputBuffer = memoryPool.slice();
    memoryPool.position(memoryPool.position() + capacity);
    outputBuffer.limit(capacity);

    return outputBuffer;
  }

  /** Frees all previously allocated memory and returns it to the allocator. */
  public void reset() {
    memoryPool.clear();
  }
}
