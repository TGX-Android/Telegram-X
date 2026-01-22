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
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkArgument;

/**
 * Represents a rectangle by the coordinates of its 4 edges (left, bottom, right, top).
 *
 * <p>Note that the right and top coordinates are exclusive.
 *
 * <p>This class represents coordinates in the OpenGL coordinate convention: {@code left <= right}
 * and {@code bottom <= top}.
 */
@UnstableApi
public final class GlRect {
  public int left;
  public int bottom;
  public int right;
  public int top;

  /** Creates an instance from (0, 0) to the specified width and height. */
  public GlRect(int width, int height) {
    this(/* left= */ 0, /* bottom= */ 0, width, height);
  }

  /** Creates an instance. */
  public GlRect(int left, int bottom, int right, int top) {
    checkArgument(left <= right && bottom <= top);
    this.left = left;
    this.bottom = bottom;
    this.right = right;
    this.top = top;
  }
}
