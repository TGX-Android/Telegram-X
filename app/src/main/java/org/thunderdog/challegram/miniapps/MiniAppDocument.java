/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

/** UI-thread document lifetime. Each callback belongs to this owner and one navigation. */
public final class MiniAppDocument {
  // DeviceStorage permits 5 MiB. postEvent JSON-encodes its JSON payload again,
  // so escaped values require up to twice that size plus the envelope.
  public static final int MAX_MESSAGE_LENGTH = 11 * 1024 * 1024;
  public static final int MAX_ACTION_LENGTH = 1024 * 1024;

  public static final class Request {
    private final MiniAppDocument owner;
    private final long generation;
    private final String sourceOrigin;

    private Request (MiniAppDocument owner, long generation, String sourceOrigin) {
      this.owner = owner;
      this.generation = generation;
      this.sourceOrigin = sourceOrigin;
    }
  }

  private final String launchOrigin;
  private final boolean requireSameOrigin;
  private long generation;
  private boolean closed;

  public MiniAppDocument (String launchUrl, boolean requireSameOrigin) {
    launchOrigin = MiniAppOrigin.of(launchUrl);
    if (launchOrigin == null) throw new IllegalArgumentException("Invalid Mini App origin");
    this.requireSameOrigin = requireSameOrigin;
  }

  public void navigate () {
    generation++;
  }

  public void close () {
    closed = true;
    generation++;
  }

  public Request receive (String sourceUrl, String documentUrl, int messageLength) {
    String sourceOrigin = MiniAppOrigin.of(sourceUrl);
    String documentOrigin = MiniAppOrigin.of(documentUrl);
    if (closed || messageLength < 0 || messageLength > MAX_MESSAGE_LENGTH || sourceOrigin == null || documentOrigin == null) return null;
    if (requireSameOrigin && (!launchOrigin.equals(sourceOrigin) || !launchOrigin.equals(documentOrigin))) return null;
    return new Request(this, generation, sourceOrigin);
  }

  public boolean isCurrent (Request request, String documentUrl) {
    if (closed || request == null || request.owner != this || request.generation != generation) return false;
    String documentOrigin = MiniAppOrigin.of(documentUrl);
    return documentOrigin != null && (!requireSameOrigin || (launchOrigin.equals(documentOrigin) && launchOrigin.equals(request.sourceOrigin)));
  }

  public boolean canSend (String documentUrl) {
    String documentOrigin = MiniAppOrigin.of(documentUrl);
    return !closed && documentOrigin != null && (!requireSameOrigin || launchOrigin.equals(documentOrigin));
  }
}
