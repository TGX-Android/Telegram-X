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

import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;

/** A memory allocator for {@link ByteBuffer}. */
@UnstableApi
public interface ByteBufferAllocator {
  /** Default implementation. */
  ByteBufferAllocator DEFAULT = ByteBuffer::allocateDirect;

  /**
   * Allocates and returns a new {@link ByteBuffer}.
   *
   * @param capacity The new buffer's capacity, in bytes.
   * @throws IllegalArgumentException If the {@code capacity} is a negative integer.
   */
  ByteBuffer allocate(int capacity);
}
