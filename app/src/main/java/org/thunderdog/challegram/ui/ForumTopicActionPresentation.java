package org.thunderdog.challegram.ui;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ForumTopicSelection.Action;
import org.thunderdog.challegram.telegram.ForumTopicActions;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Presentation-only decisions; never changes selection, permissions or batch execution. */
final class ForumTopicActionPresentation {
  private ForumTopicActionPresentation () { }

  static EnumSet<Action> overflowActions (EnumSet<Action> common, EnumSet<Action> header) {
    EnumSet<Action> overflow = common.clone();
    overflow.removeAll(header);
    return overflow;
  }

  static final class BatchFailure {
    final @Nullable TdApi.Error error;
    final boolean uncertain;
    final List<ForumTopicActions.BatchTarget> retryTargets;

    private BatchFailure (@Nullable TdApi.Error error, boolean uncertain, List<ForumTopicActions.BatchTarget> retryTargets) {
      this.error = error;
      this.uncertain = uncertain;
      this.retryTargets = retryTargets;
    }
  }

  /** Null means silent success. Report a shared cause, not a per-topic result transcript. */
  static @Nullable BatchFailure failure (ForumTopicActions.BatchResult result) {
    boolean failed = result.outcomes.isEmpty(), uncertain = false, differentErrors = false;
    TdApi.Error cause = null;
    for (ForumTopicActions.BatchOutcome outcome : result.outcomes) {
      if (outcome.getSuccessful()) continue;
      failed = true;
      if (outcome.uncertain) { uncertain = true; continue; }
      TdApi.Error error = outcome.error;
      // Unsent siblings are not the reason preflight failed; prefer the actual error.
      if (error.code == ForumTopicActions.BATCH_PREFLIGHT_ABORTED) continue;
      if (cause == null) cause = error;
      else if (cause.code != error.code || !Objects.equals(cause.message, error.message)) differentErrors = true;
    }
    return failed ? new BatchFailure(differentErrors ? null : cause, uncertain, result.failedTargets()) : null;
  }

  /** TD's raw fallback is diagnostic text, not a suitable user-facing batch error. */
  static String readableError (TdApi.Error error, @Nullable String translated, String fallback) {
    return translated == null || translated.trim().isEmpty() || translated.equals("#" + error.code + ": " + error.message) ? fallback : translated;
  }
}
