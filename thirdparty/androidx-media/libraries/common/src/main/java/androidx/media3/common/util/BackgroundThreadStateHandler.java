/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkState;

import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.common.base.Function;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Helper class to handle state updates on a background thread while maintaining a placeholder state
 * on the foreground thread.
 *
 * @param <T> An immutable object representing the entire state. Must implement {@link
 *     Object#equals(Object)}.
 */
@UnstableApi
public final class BackgroundThreadStateHandler<T extends @NonNull Object> {

  /**
   * An interface to handle changes to the state on the foreground thread.
   *
   * @param <T> An immutable object representing the entire state. Must implement {@link
   *     Object#equals(Object)}.
   */
  public interface StateChangeListener<T extends @NonNull Object> {

    /**
     * The state has changed.
     *
     * <p>A typical usage of this method is to inform external listeners.
     *
     * <p>This method will be called on the foreground thread.
     *
     * @param oldState The old state.
     * @param newState The new state.
     */
    void onStateChanged(T oldState, T newState);
  }

  private final HandlerWrapper backgroundHandler;
  private final HandlerWrapper foregroundHandler;
  private final StateChangeListener<T> onStateChanged;

  private T foregroundState;
  private T backgroundState;
  private int pendingOperations;

  /**
   * Creates the helper for background thread state updates.
   *
   * <p>This constructor may be called on any thread.
   *
   * @param initialState The initial state value.
   * @param backgroundLooper The {@link Looper} to run background operations on.
   * @param foregroundLooper The {@link Looper} to run foreground operations on.
   * @param clock The {@link Clock} to control the handler messages.
   * @param onStateChanged The {@link StateChangeListener} to listen to state changes.
   */
  public BackgroundThreadStateHandler(
      T initialState,
      Looper backgroundLooper,
      Looper foregroundLooper,
      Clock clock,
      StateChangeListener<T> onStateChanged) {
    backgroundHandler = clock.createHandler(backgroundLooper, /* callback= */ null);
    foregroundHandler = clock.createHandler(foregroundLooper, /* callback= */ null);
    foregroundState = initialState;
    backgroundState = initialState;
    this.onStateChanged = onStateChanged;
  }

  /**
   * Returns the current state.
   *
   * <p>Can be called on either the foreground or background thread, returning the respective
   * current state of this thread.
   */
  public T get() {
    @Nullable Looper myLooper = Looper.myLooper();
    if (myLooper == foregroundHandler.getLooper()) {
      return foregroundState;
    }
    checkState(myLooper == backgroundHandler.getLooper());
    return backgroundState;
  }

  /**
   * Starts an asynchronous state update.
   *
   * <p>Must only be called on the foreground thread.
   *
   * @param placeholderState A function to create a placeholder state from the current state while
   *     the operation is pending. Will be called on the foreground thread.
   * @param backgroundStateUpdate A function to handle the background state update, taking in the
   *     current background state and returning the updated state. Will be called on the background
   *     thread.
   */
  public void updateStateAsync(
      Function<T, T> placeholderState, Function<T, T> backgroundStateUpdate) {
    checkState(Looper.myLooper() == foregroundHandler.getLooper());
    pendingOperations++;
    backgroundHandler.post(
        () -> {
          backgroundState = backgroundStateUpdate.apply(backgroundState);
          T newState = backgroundState;
          foregroundHandler.post(
              () -> {
                if (--pendingOperations == 0) {
                  updateStateInForeground(newState);
                }
              });
        });
    updateStateInForeground(placeholderState.apply(foregroundState));
  }

  /**
   * Updates the background state directly, independent to any operation started from the foreground
   * thread.
   *
   * <p>Must only be called on the background thread.
   *
   * @param newState The new state.
   */
  public void setStateInBackground(T newState) {
    backgroundState = newState;
    foregroundHandler.post(
        () -> {
          if (pendingOperations == 0) {
            updateStateInForeground(newState);
          }
        });
  }

  /**
   * Runs the provided {@link Runnable} on the background thread.
   *
   * <p>Can be called from any thread.
   *
   * <p>Note: This method is useful to update the state on the background using {@link
   * #setStateInBackground} for events arriving from external sources. Use {@link #updateStateAsync}
   * if the intention is update the state in response the a foreground thread method call.
   *
   * @param runnable The {@link Runnable} to be called on the background thread.
   */
  public void runInBackground(Runnable runnable) {
    backgroundHandler.post(runnable);
  }

  private void updateStateInForeground(T newState) {
    T oldState = foregroundState;
    foregroundState = newState;
    if (!oldState.equals(newState)) {
      onStateChanged.onStateChanged(oldState, newState);
    }
  }
}
