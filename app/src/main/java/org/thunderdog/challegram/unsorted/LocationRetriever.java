package org.thunderdog.challegram.unsorted;

import android.location.Location;

import org.thunderdog.challegram.BaseActivity;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableBool;

public abstract class LocationRetriever implements Destroyable {
  public interface LocationCallback {
    void onSuccess (Location location);
    void onFailure ();
  }

  private final Queue<LocationCallback> pendingCallbacks = new LinkedBlockingDeque<>();

  protected final BaseActivity activity;

  public LocationRetriever (BaseActivity activity) {
    this.activity = activity;
  }

  public final void requestLocation (LocationCallback callback) {
    pendingCallbacks.offer(callback);
    retrieveLocation();
  }

  public abstract void checkPermissions (RunnableBool runnable);
  protected abstract void retrieveLocation ();

  protected final void onLocationRetrieved (Location location) {
    LocationCallback callback;
    while ((callback = pendingCallbacks.poll()) != null) {
      callback.onSuccess(location);
    }
  }

  protected final void onLocationFetchFailed () {
    LocationCallback callback;
    while ((callback = pendingCallbacks.poll()) != null) {
      callback.onFailure();
    }
  }
}
