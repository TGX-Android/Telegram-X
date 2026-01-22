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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BackgroundThreadStateHandler.StateChangeListener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Unit test for {@link BackgroundThreadStateHandler}. */
@SuppressWarnings("unchecked") // Mocks of listeners are all unchecked.
@RunWith(AndroidJUnit4.class)
public class BackgroundThreadStateHandlerTest {

  private HandlerThread backgroundThread;

  @Before
  public void setUp() {
    backgroundThread = new HandlerThread("BackgroundThreadStateHandlerTest");
    backgroundThread.start();
  }

  @After
  public void tearDown() {
    backgroundThread.quit();
  }

  @Test
  public void get_afterConstructor_returnsInitialState() throws Exception {
    TestState initialState = new TestState(2);
    // Create handler from another thread to test promise it can be created on any thread.
    AtomicReference<BackgroundThreadStateHandler<TestState>> handler = new AtomicReference<>();
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    Thread testThread =
        new Thread("otherThread") {
          @Override
          public void run() {
            handler.set(
                new BackgroundThreadStateHandler<>(
                    initialState,
                    backgroundThread.getLooper(),
                    Looper.getMainLooper(),
                    Clock.DEFAULT,
                    mockListener));
          }
        };
    testThread.start();
    testThread.join();

    TestState foregroundState = handler.get().get();
    AtomicReference<TestState> backgroundState = new AtomicReference<>();
    CountDownLatch waitForBackgroundState = new CountDownLatch(1);
    new Handler(backgroundThread.getLooper())
        .post(
            () -> {
              backgroundState.set(handler.get().get());
              waitForBackgroundState.countDown();
            });
    waitForBackgroundState.await();

    assertThat(foregroundState).isEqualTo(initialState);
    assertThat(backgroundState.get()).isEqualTo(initialState);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void get_immediatelyAfterUpdateStateAsync_returnsPlaceholderStateAndInformsListener() {
    TestState initialState = new TestState(2);
    TestState placeholderState = new TestState(3);
    TestState finalState = new TestState(4);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    AtomicReference<TestState> placeholderArgument = new AtomicReference<>();
    handler.updateStateAsync(
        /* placeholderState= */ state -> {
          placeholderArgument.set(state);
          return placeholderState;
        },
        /* backgroundStateUpdate= */ state -> finalState);
    TestState stateAfterUpdate = handler.get();

    assertThat(stateAfterUpdate).isEqualTo(placeholderState);
    assertThat(placeholderArgument.get()).isEqualTo(initialState);
    verify(mockListener).onStateChanged(initialState, placeholderState);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void get_afterUpdateStateAsyncWithIdenticalFinalValue_onlyCallsListenerOnce() {
    TestState initialState = new TestState(2);
    TestState updatedState = new TestState(3);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    handler.updateStateAsync(
        /* placeholderState= */ state -> updatedState,
        /* backgroundStateUpdate= */ state -> updatedState);
    waitForPendingTasks();
    TestState stateAfterUpdate = handler.get();

    assertThat(stateAfterUpdate).isEqualTo(updatedState);
    verify(mockListener).onStateChanged(initialState, updatedState);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void get_afterUpdateStateAsyncWithDifferentFinalValue_callsListenerAgain() {
    TestState initialState = new TestState(2);
    TestState placeholderState = new TestState(3);
    TestState finalState = new TestState(4);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    AtomicReference<TestState> backgroundUpdateArgument = new AtomicReference<>();
    handler.updateStateAsync(
        /* placeholderState= */ state -> placeholderState,
        /* backgroundStateUpdate= */ state -> {
          backgroundUpdateArgument.set(state);
          return finalState;
        });
    waitForPendingTasks();
    TestState stateAfterUpdate = handler.get();

    assertThat(stateAfterUpdate).isEqualTo(finalState);
    verify(mockListener).onStateChanged(initialState, placeholderState);
    verify(mockListener).onStateChanged(placeholderState, finalState);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void get_afterMultipleOverlappingUpdateStateAsync_onlyReportsFinalState() {
    TestState initialState = new TestState(2);
    TestState placeholderState1 = new TestState(3);
    TestState finalState1 = new TestState(4);
    TestState placeholderState2 = new TestState(5);
    TestState finalState2 = new TestState(6);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    AtomicReference<TestState> placeholderArgument1 = new AtomicReference<>();
    AtomicReference<TestState> placeholderArgument2 = new AtomicReference<>();
    AtomicReference<TestState> backgroundUpdateArgument1 = new AtomicReference<>();
    AtomicReference<TestState> backgroundUpdateArgument2 = new AtomicReference<>();
    handler.updateStateAsync(
        /* placeholderState= */ state -> {
          placeholderArgument1.set(state);
          return placeholderState1;
        },
        /* backgroundStateUpdate= */ state -> {
          backgroundUpdateArgument1.set(state);
          return finalState1;
        });
    handler.updateStateAsync(
        /* placeholderState= */ state -> {
          placeholderArgument2.set(state);
          return placeholderState2;
        },
        /* backgroundStateUpdate= */ state -> {
          backgroundUpdateArgument2.set(state);
          return finalState2;
        });
    TestState stateImmediatelyAfterUpdate = handler.get();
    waitForPendingTasks();
    TestState stateAfterFinalUpdate = handler.get();

    assertThat(stateImmediatelyAfterUpdate).isEqualTo(placeholderState2);
    assertThat(stateAfterFinalUpdate).isEqualTo(finalState2);
    assertThat(placeholderArgument1.get()).isEqualTo(initialState);
    assertThat(placeholderArgument2.get()).isEqualTo(placeholderState1);
    assertThat(backgroundUpdateArgument1.get()).isEqualTo(initialState);
    assertThat(backgroundUpdateArgument2.get()).isEqualTo(finalState1);
    verify(mockListener).onStateChanged(initialState, placeholderState1);
    verify(mockListener).onStateChanged(placeholderState1, placeholderState2);
    verify(mockListener).onStateChanged(placeholderState2, finalState2);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void runInBackground_executesInBackground() throws Exception {
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            new TestState(2),
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            (oldState, newState) -> {});

    // Test that calling this from another third thread is possible.
    AtomicReference<Looper> taskLooper = new AtomicReference<>();
    Thread testThread =
        new Thread("otherThread") {
          @Override
          public void run() {
            handler.runInBackground(() -> taskLooper.set(Looper.myLooper()));
          }
        };
    testThread.start();
    testThread.join();
    waitForPendingTasks();

    assertThat(taskLooper.get()).isEqualTo(backgroundThread.getLooper());
  }

  @Test
  public void setStateInBackground_updatesStateAndCallsListener() {
    TestState initialState = new TestState(2);
    TestState updatedState = new TestState(3);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    handler.runInBackground(() -> handler.setStateInBackground(updatedState));
    waitForPendingTasks();
    TestState stateAfterUpdate = handler.get();

    assertThat(stateAfterUpdate).isEqualTo(updatedState);
    verify(mockListener).onStateChanged(initialState, updatedState);
    verifyNoMoreInteractions(mockListener);
  }

  @Test
  public void setStateInBackground_whileAsyncUpdateInProgress_onlyUpdatesFinalState()
      throws Exception {
    TestState initialState = new TestState(2);
    TestState updatedBackgroundState = new TestState(3);
    TestState finalState = new TestState(4);
    StateChangeListener<TestState> mockListener = mock(StateChangeListener.class);
    BackgroundThreadStateHandler<TestState> handler =
        new BackgroundThreadStateHandler<>(
            initialState,
            backgroundThread.getLooper(),
            Looper.getMainLooper(),
            Clock.DEFAULT,
            mockListener);

    handler.runInBackground(() -> handler.setStateInBackground(updatedBackgroundState));
    AtomicReference<TestState> backgroundUpdateArgument = new AtomicReference<>();
    handler.updateStateAsync(
        /* placeholderState= */ state -> state,
        /* backgroundStateUpdate= */ state -> {
          backgroundUpdateArgument.set(state);
          return finalState;
        });
    waitForPendingTasks();
    TestState stateAfterUpdate = handler.get();

    assertThat(stateAfterUpdate).isEqualTo(finalState);
    assertThat(backgroundUpdateArgument.get()).isEqualTo(updatedBackgroundState);
    verify(mockListener).onStateChanged(initialState, finalState);
    verifyNoMoreInteractions(mockListener);
  }

  private void waitForPendingTasks() {
    shadowOf(backgroundThread.getLooper()).idle();
    ShadowLooper.idleMainLooper();
  }

  public static final class TestState {

    public final int value;

    public TestState(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof TestState)) {
        return false;
      }
      TestState testState = (TestState) o;
      return value == testState.value;
    }

    @Override
    public int hashCode() {
      return value;
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }
  }
}
