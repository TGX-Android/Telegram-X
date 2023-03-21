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
 *
 * File created on 20/03/2023
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.CancellationSignal;

import org.thunderdog.challegram.unsorted.Settings;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import me.vkryl.core.collection.IntSet;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableData;

public class TdlibRouteSelector implements Comparator<Settings.Proxy> {
  private static final int CONCURRENT_PING_LIMIT = 5;
  private final Tdlib tdlib;
  private final List<Settings.Proxy> routes;
  private final CancellationSignal cancellationSignal;
  private final IntSet failedProxyIds = new IntSet();
  private int pendingProxyId = Settings.PROXY_ID_UNKNOWN;
  public TdlibRouteSelector (Tdlib tdlib, boolean allowDirect, int failedProxyId) {
    this(tdlib);
    if (!allowDirect) {
      markAsFailed(Settings.PROXY_ID_NONE);
    }
    if (failedProxyId != Settings.PROXY_ID_UNKNOWN) {
      markAsFailed(failedProxyId);
    }
  }

  public TdlibRouteSelector (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.routes = Settings.instance().getAvailableProxies();
    this.routes.add(0, Settings.Proxy.noProxy(true));
    this.cancellationSignal = new CancellationSignal();
    routes.sort(this);
  }

  @Override
  public int compare (Settings.Proxy a, Settings.Proxy b) {
    boolean aPong = a.hasPong();
    boolean bPong = b.hasPong();
    boolean aConnected = a.successfulConnectionsCount > 0;
    boolean bConnected = b.successfulConnectionsCount > 0;
    boolean aGood = aPong && aConnected;
    boolean bGood = bPong && bConnected;
    if (aGood != bGood) {
      return aGood ? -1 : 1;
    }
    if (aPong != bPong) {
      return aPong ? -1 : 1;
    }
    int aOrder = a.defaultOrder();
    int bOrder = b.defaultOrder();
    if (aOrder != bOrder) {
      return Integer.compare(aOrder, bOrder);
    }
    if (aPong && a.pingMs != b.pingMs) {
      return Long.compare(a.pingMs, b.pingMs);
    }
    if (aConnected != bConnected) {
      return aConnected ? -1 : 1;
    }
    if (aConnected && a.lastConnectionTime != 0 && b.lastConnectionTime != 0 && a.lastConnectionTime != b.lastConnectionTime) {
      return Long.compare(b.lastConnectionTime, a.lastConnectionTime);
    }
    return a.compareTo(b);
  }
  public void cancel () {
    cancellationSignal.cancel();
  }

  public void markAsFailed (int proxyId) {
    if (pendingProxyId == proxyId) {
      pendingProxyId = Settings.PROXY_ID_UNKNOWN;
    }
    failedProxyIds.add(proxyId);
  }

  public void markAsPending (int proxyId) {
    this.pendingProxyId = proxyId;
  }

  public boolean isPending (int proxyId) {
    return proxyId != Settings.PROXY_ID_UNKNOWN && this.pendingProxyId == proxyId;
  }
  public void markAsSuccessful (int proxyId) {
    failedProxyIds.remove(proxyId);
    if (pendingProxyId == proxyId) {
      pendingProxyId = Settings.PROXY_ID_UNKNOWN;
    }
  }

  public boolean isEmpty () {
    return routes.isEmpty();
  }

  public boolean isCanceled () {
    return cancellationSignal.isCanceled();
  }

  private final Queue<RunnableData<Settings.Proxy>> pendingCallbacks = new LinkedBlockingQueue<>();

  private int pongsRemaining = -1;
  private int pingsPending;

  public boolean isLookingUpForRoute () {
    return pongsRemaining != -1;
  }
  public void findBestRoute (@NonNull RunnableData<Settings.Proxy> after) {
    if (isCanceled()) {
      return;
    }
    if (isEmpty()) {
      after.runWithData(null);
      return;
    }
    pendingCallbacks.add(after);
    if (pongsRemaining == -1) {
      pongsRemaining = routes.size();
      performPings();
    }
  }
  private void performPings () {
    if (pongsRemaining == 0) {
      sortAndSelectRoute(true);
      return;
    }
    int index = routes.size() - pongsRemaining;
    for (int i = index; i < routes.size() && pingsPending < CONCURRENT_PING_LIMIT; i++) {
      Settings.Proxy route = routes.get(i);
      if (failedProxyIds.has(route.id)) {
        onPong(route, Settings.PROXY_TIME_UNSET);
      } else {
        performPing(route);
      }
    }
  }

  private void performPing (Settings.Proxy proxy) {
    if (pingsPending >= CONCURRENT_PING_LIMIT) {
      // Waiting for onPong
      return;
    }
    pingsPending++;
    tdlib.pingProxy(proxy, pingResult -> {
      onPong(proxy, pingResult);
      performPings();
    });
  }

  private CancellableRunnable onTimeout;
  private void onPong (Settings.Proxy proxy, long time) {
    boolean isLast = --pongsRemaining == 0;
    if (isLast) {
      sortAndSelectRoute(true);
    } else if (time >= 0 && maySuggestRoute(proxy)) {
      // Give at least 1.5 seconds after we receive the first pong
      if (onTimeout == null) {
        onTimeout = new CancellableRunnable() {
          @Override
          public void act () {
            sortAndSelectRoute(false);
          }
        };
        tdlib.runOnTdlibThread(onTimeout, Math.min(5.0, Math.max(1.5, (double) time / 1000.0)), false);
      }
    }
  }

  private void sortAndSelectRoute (boolean force) {
    routes.sort(this);
    Settings.Proxy proxy = selectRoute();
    if (proxy != null || force) {
      suggestRoute(proxy);
    }
  }

  private boolean maySuggestRoute (Settings.Proxy route) {
    return !failedProxyIds.has(route.id);
  }

  private Settings.Proxy selectRoute () {
    for (Settings.Proxy route : routes) {
      if (maySuggestRoute(route)) {
        return route;
      }
    }
    return null;
  }

  private void suggestRoute (@Nullable Settings.Proxy route) {
    if (isLookingUpForRoute() && !isCanceled()) {
      RunnableData<Settings.Proxy> callback;
      while ((callback = pendingCallbacks.poll()) != null) {
        callback.runWithData(route);
      }
    }
    pongsRemaining = -1;
  }
}
