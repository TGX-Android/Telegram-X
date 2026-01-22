/*
 * Copyright (C) 2016 The Android Open Source Project
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

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Function;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Helper utility to dump field values. */
@UnstableApi
public final class Dumper {

  /** Provides custom dump method. */
  public interface Dumpable {
    /**
     * Dumps the fields of the object using the {@code dumper}.
     *
     * @param dumper The {@link Dumper} to be used to dump fields.
     */
    void dump(Dumper dumper);
  }

  private static final int INDENT_SIZE_IN_SPACES = 2;

  private final StringBuilder sb;
  private int indent;

  public Dumper() {
    sb = new StringBuilder();
  }

  @CanIgnoreReturnValue
  public Dumper add(String field, Object value) {
    if (value instanceof byte[]) {
      return add(field, (byte[]) value);
    }
    String[] lines = Util.split(value.toString(), "\n");
    addLine(field + " = " + lines[0]);
    int fieldValueAdditionalIndent = field.length() + 3;
    indent += fieldValueAdditionalIndent;
    for (int i = 1; i < lines.length; i++) {
      addLine(lines[i]);
    }
    indent -= fieldValueAdditionalIndent;
    return this;
  }

  @CanIgnoreReturnValue
  public Dumper add(Dumpable object) {
    object.dump(this);
    return this;
  }

  @CanIgnoreReturnValue
  public Dumper add(String field, byte[] value) {
    String string =
        String.format(
            Locale.US, "%s = length %d, hash %X", field, value.length, Arrays.hashCode(value));
    return addLine(string);
  }

  /**
   * Calls {@link #add(String, Object)} if {@code value} is not equal to {@code defaultValue}.
   *
   * <p>It is not permitted to pass a null value to {@link #add}, so null is only permitted here as
   * a default value. Passing {@code value == null && defaultValue != null} will result in a {@link
   * NullPointerException}.
   */
  @CanIgnoreReturnValue
  public Dumper addIfNonDefault(
      String field, @Nullable Object value, @Nullable Object defaultValue) {
    if (!Objects.equals(value, defaultValue)) {
      checkNotNull(value);
      add(field, value);
    }
    return this;
  }

  /**
   * Applies {@code valueTransformFunction} to {@code value} and {@code defaultValue} and passes the
   * results to {@link #addIfNonDefault(String, Object, Object)}.
   *
   * <p>See {@link #addIfNonDefault(String, Object, Object)} for limitations around when null
   * results from {@code valueTransformFunction} are permitted.
   */
  @CanIgnoreReturnValue
  public <T> Dumper addIfNonDefault(
      String field,
      T value,
      T defaultValue,
      Function<T, @NullableType Object> valueTransformFunction) {
    return addIfNonDefault(
        field, valueTransformFunction.apply(value), valueTransformFunction.apply(defaultValue));
  }

  @CanIgnoreReturnValue
  public Dumper addTime(String field, long time) {
    return add(field, time == C.TIME_UNSET ? "UNSET TIME" : time);
  }

  @CanIgnoreReturnValue
  public Dumper startBlock(String name) {
    addLine(name + ":");
    indent += INDENT_SIZE_IN_SPACES;
    return this;
  }

  @CanIgnoreReturnValue
  public Dumper endBlock() {
    indent -= INDENT_SIZE_IN_SPACES;
    return this;
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  @CanIgnoreReturnValue
  private Dumper addLine(String string) {
    for (int i = 0; i < indent; i++) {
      sb.append(' ');
    }
    sb.append(string);
    sb.append('\n');
    return this;
  }
}
