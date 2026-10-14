package org.thunderdog.challegram.data;

/** Profile-only link state. Request identity rejects late replies after retry, retarget, or cleanup. */
public final class ForumTopicProfileLink {
  public enum State { EMPTY, LOADING, READY, UNAVAILABLE }

  public static final class Request {
    public final long chatId;
    public final int forumTopicId;

    private Request (long chatId, int forumTopicId) {
      this.chatId = chatId; this.forumTopicId = forumTopicId;
    }
  }

  private Request request;
  private State state = State.EMPTY;
  private String url;
  private boolean isPublic;

  public State state () { return state; }
  public String url () { return url; }
  public boolean isPublic () { return isPublic; }
  public boolean matches (long chatId, int forumTopicId) {
    return request != null && request.chatId == chatId && request.forumTopicId == forumTopicId;
  }

  /** READY is cached; only an explicit retry or a different target starts another lookup. */
  public Request begin (long chatId, int forumTopicId) {
    if (chatId == 0 || forumTopicId <= 0) throw new IllegalArgumentException("A forum topic is required");
    if (matches(chatId, forumTopicId) && (state == State.LOADING || state == State.READY)) return null;
    request = new Request(chatId, forumTopicId);
    state = State.LOADING; url = null; isPublic = false;
    return request;
  }

  /** A null/empty result also covers failures and the bounded UI timeout. */
  public boolean complete (Request expected, String value, boolean publicLink) {
    if (expected == null || expected != request || state != State.LOADING) return false;
    if (value == null || value.trim().isEmpty()) {
      state = State.UNAVAILABLE; url = null; isPublic = false;
    } else {
      state = State.READY; url = value; isPublic = publicLink;
    }
    return true;
  }

  public String displayUrl () {
    return url != null && url.startsWith("https://") ? url.substring(8) : url;
  }

  public void invalidate () {
    request = null; state = State.EMPTY; url = null; isPublic = false;
  }
}
