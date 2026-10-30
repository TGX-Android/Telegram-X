/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.telegram;

import android.os.Bundle;

import org.thunderdog.challegram.data.ForumTabsState;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * One list lease shared only by successive histories in one navigation slot. No second topic cache.
 */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumTabsHost implements CleanupStartupDelegate {
  public final Tdlib tdlib;
  public final long chatId;
  public ForumTabsState state;
  public Bundle panelState;
  private ForumTopicStore.ListSession session;
  private ForumTopicStore.Snapshot snapshot;
  private final LinkedHashSet<Runnable> observers = new LinkedHashSet<>();
  private final long persistenceEpoch;
  private volatile boolean disposed, dataCleared, restartPending;

  public ForumTabsHost (Tdlib tdlib, long chatId) {
    this.tdlib = tdlib;
    this.chatId = chatId;
    synchronized (tdlib.settings()) {
      state = tdlib.settings().getForumTabsState(chatId);
      persistenceEpoch = tdlib.settings().getForumTabsStateEpoch();
    }
    snapshot = tdlib.topics().cachedList(chatId, "");
    tdlib.listeners().addCleanupListener(this);
  }

  public void attach (Runnable observer) {
    if (disposed || dataCleared) {
      throw new IllegalStateException("Closed forum tabs host");
    }
    observers.add(observer);
    if (session == null) {
      open();
    }
  }

  private void open () {
    session = tdlib.topics()
      .openList(
        chatId,
        "",
        value -> {
        if (disposed || dataCleared) {
          return;
        }
        snapshot = value;
        for (Runnable observer : new ArrayList<>(observers)) {
          observer.run();
        }
      }
      );
  }

  public void detach (Runnable observer) {
    observers.remove(observer);
    if (observers.isEmpty()) {
      disposed = true;
      if (session != null) {
        session.close();
      }
      session = null;
      panelState = null;
      tdlib.listeners().removeCleanupListener(this);
    }
  }

  public ForumTopicStore.Snapshot snapshot () {
    return snapshot;
  }

  public void loadMore () {
    if (session != null) {
      session.loadMore();
    }
  }

  public void retry () {
    if (session != null) {
      session.retry();
    }
  }

  public void remember (ForumTabsState value) {
    if (disposed || dataCleared) {
      return;
    }
    if (tdlib.settings().putForumTabsState(chatId, value, persistenceEpoch)) {
      state = value;
    }
  }

  @Override public void onPerformUserCleanup () {
    dataCleared = true;
    tdlib.ui()
      .post(
        () -> {
        if (session != null) {
          session.close();
        }
        session = null;
        panelState = null;
        state = ForumTabsState.defaultState();
      }
      );
  }

  @Override public void onPerformRestart () {
    restartPending = true;
  }

  @Override public void onPerformStartup (boolean afterRestart) {
    if (afterRestart || restartPending) {
      tdlib.ui()
        .post(
          () -> {
          restartPending = false;
          if (!disposed && !dataCleared) {
            if (session != null) {
              session.close();
            }
            open();
          }
        }
        );
    }
  }
}