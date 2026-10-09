package org.thunderdog.challegram.telegram;

import android.os.Message;

import java.util.ArrayList;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;

/** Handler-only seam: captures post/postDelayed without putting work on any Android queue. */
public final class Stage8DeferredUi extends TdlibUi {
  private final List<Runnable> pending = new ArrayList<>();

  public Stage8DeferredUi () { super(null); }

  @Override public boolean sendMessageAtTime (Message message, long uptimeMillis) {
    require(message.getCallback() != null, "Only synthetic Runnable callbacks are allowed");
    require(pending.isEmpty(), "Editor cleanup must not accumulate deferred retries");
    pending.add(message.getCallback());
    return true;
  }

  public int pendingCount () { return pending.size(); }

  public void runNext () {
    require(!pending.isEmpty(), "Expected a deferred editor cleanup");
    pending.remove(0).run();
  }

  public void clear () { pending.clear(); }
}
