package org.thunderdog.challegram.data;

import java.io.Serializable;

/** Android-free editor state. Never stores a message draft or retries a mutation. */
public final class ForumTopicEditorState {
  public enum Phase { READY, PENDING, UNKNOWN, SUCCEEDED }
  public enum Error { NONE, UNAVAILABLE, EMPTY_NAME, LONG_NAME, INVALID_COLOR, GENERAL_ICON, ICON_LOADING, PREMIUM }

  public final boolean creating;
  public final boolean general;
  public final String originalName;
  public final int originalColor;
  public final long originalEmoji;
  private String name;
  private int color;
  private long emoji;
  private Phase phase = Phase.READY;
  private String serverError = "";
  private int completedTopicId;
  private boolean completionConsumed;

  private ForumTopicEditorState (boolean creating, boolean general, String name, int color, long emoji) {
    this.creating = creating;
    this.general = general;
    this.originalName = name;
    this.originalColor = color;
    this.originalEmoji = emoji;
    this.name = name;
    this.color = color;
    this.emoji = emoji;
  }

  public static ForumTopicEditorState create () {
    return new ForumTopicEditorState(true, false, "", ForumTopicPolicy.ICON_COLORS[0], 0);
  }

  public static ForumTopicEditorState edit (String name, int color, long emoji, boolean general) {
    if (name == null) throw new IllegalArgumentException("Missing topic name");
    return new ForumTopicEditorState(false, general, name, color, emoji);
  }

  public String name () { return name; }
  public String submittedName () { return name.trim(); }
  public int color () { return color; }
  public long emoji () { return emoji; }
  public Phase phase () { return phase; }
  public String serverError () { return serverError; }
  public int completedTopicId () { return completedTopicId; }
  public boolean editable () { return phase == Phase.READY; }
  public boolean changesEmoji () { return !general && emoji != originalEmoji; }

  public boolean setName (String value) {
    if (!editable() || value == null) return false;
    name = value;
    serverError = "";
    return true;
  }

  public boolean setColor (int value) {
    if (!editable() || !creating || !ForumTopicPolicy.validColor(value)) return false;
    color = value;
    serverError = "";
    return true;
  }

  public boolean setEmoji (long value) {
    if (!editable() || general) return false;
    emoji = value;
    serverError = "";
    return true;
  }

  public boolean hasChanges () {
    return !submittedName().equals(originalName.trim()) || color != originalColor || emoji != originalEmoji;
  }

  /** The caller uses ForumTopicPolicy.allowedIcon against the current account/default set. */
  public Error validate (boolean available, boolean iconAllowed, boolean defaultsLoaded) {
    if (!available) return Error.UNAVAILABLE;
    if (submittedName().isEmpty()) return Error.EMPTY_NAME;
    if (!ForumTopicPolicy.validName(submittedName())) return Error.LONG_NAME;
    if (creating ? !ForumTopicPolicy.validColor(color) : color != originalColor) return Error.INVALID_COLOR;
    if (general && emoji != originalEmoji) return Error.GENERAL_ICON;
    // Renaming a topic must remain possible after Premium expires, without resending its icon.
    if ((creating || changesEmoji()) && !iconAllowed) return defaultsLoaded ? Error.PREMIUM : Error.ICON_LOADING;
    return Error.NONE;
  }

  public boolean canSubmit (boolean available, boolean iconAllowed, boolean defaultsLoaded) {
    return editable() && hasChanges() && validate(available, iconAllowed, defaultsLoaded) == Error.NONE;
  }

  public boolean beginSubmit (boolean available, boolean iconAllowed, boolean defaultsLoaded) {
    if (!canSubmit(available, iconAllowed, defaultsLoaded)) return false;
    phase = Phase.PENDING;
    serverError = "";
    return true;
  }

  public static boolean uncertainError (int code) {
    return code <= 0 || code == 408 || code >= 500;
  }

  public void failed (int code, String message) {
    if (phase != Phase.PENDING) return;
    phase = creating && uncertainError(code) ? Phase.UNKNOWN : Phase.READY;
    serverError = message != null ? message : "";
  }

  public void succeeded (int topicId) {
    if (phase != Phase.PENDING || topicId <= 0) return;
    completedTopicId = topicId;
    phase = Phase.SUCCEEDED;
    serverError = "";
  }

  /** Unknown Create can only be resolved by an explicit user choice of an existing topic. */
  public boolean resolveToExisting (int topicId) {
    if (phase != Phase.UNKNOWN || topicId <= 0) return false;
    completedTopicId = topicId;
    phase = Phase.SUCCEEDED;
    serverError = "";
    return true;
  }

  public boolean claimCompletion () {
    if (phase != Phase.SUCCEEDED || completionConsumed) return false;
    completionConsumed = true;
    return true;
  }

  public boolean matchesCandidate (String name, int color, long emoji, boolean outgoing) {
    return phase == Phase.UNKNOWN && outgoing && submittedName().equals(name) && this.color == color && this.emoji == emoji;
  }

  public Snapshot snapshot () { return new Snapshot(this); }

  public static ForumTopicEditorState restore (Snapshot saved) {
    ForumTopicEditorState state = new ForumTopicEditorState(saved.creating, saved.general, saved.originalName, saved.originalColor, saved.originalEmoji);
    state.name = saved.name;
    state.color = saved.color;
    state.emoji = saved.emoji;
    // A callback belongs to the old controller. Create must be reconciled, never replayed.
    state.phase = saved.phase == Phase.PENDING ? (saved.creating ? Phase.UNKNOWN : Phase.READY) : saved.phase;
    state.serverError = saved.serverError;
    state.completedTopicId = saved.completedTopicId;
    state.completionConsumed = saved.completionConsumed;
    return state;
  }

  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    public final boolean creating, general, completionConsumed;
    public final String originalName, name, serverError;
    public final int originalColor, color, completedTopicId;
    public final long originalEmoji, emoji;
    public final Phase phase;

    private Snapshot (ForumTopicEditorState state) {
      creating = state.creating; general = state.general;
      originalName = state.originalName; originalColor = state.originalColor; originalEmoji = state.originalEmoji;
      name = state.name; color = state.color; emoji = state.emoji; phase = state.phase;
      serverError = state.serverError; completedTopicId = state.completedTopicId; completionConsumed = state.completionConsumed;
    }
  }
}
