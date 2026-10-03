package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

/** Pure topic-material contract. Counts are hints, never proof that a category is empty. */
public final class ForumTopicMedia {
  private ForumTopicMedia () { }

  public static final int CATEGORY_COUNT = 7;
  public static TdApi.SearchMessagesFilter filter (int index) {
    switch (index) {
      case 0: return new TdApi.SearchMessagesFilterPhotoAndVideo();
      case 1: return new TdApi.SearchMessagesFilterDocument();
      case 2: return new TdApi.SearchMessagesFilterUrl();
      case 3: return new TdApi.SearchMessagesFilterAudio();
      case 4: return new TdApi.SearchMessagesFilterPoll();
      case 5: return new TdApi.SearchMessagesFilterAnimation();
      case 6: return new TdApi.SearchMessagesFilterVoiceNote();
      default: throw new IllegalArgumentException("Unknown material category");
    }
  }

  /** Also safe for ordinary chats, Saved Messages, and generic threads. */
  public static boolean matches (long chatId, TdApi.MessageTopic topic, TdApi.Message message) {
    return message != null && message.chatId == chatId && Td.matchesTopic(message.topicId, topic);
  }

  public enum Availability { UNKNOWN, LOADING, PRESENT, EMPTY, ERROR }

  public static final class Category {
    public Availability availability = Availability.UNKNOWN;
    public int approximateCount = -1;
    public TdApi.Error countError;
    public TdApi.Error error;
    private int revision;

    public int begin () {
      // Keep an existing page reachable during a refresh or a transient failure.
      if (availability != Availability.PRESENT) availability = Availability.LOADING;
      error = null;
      return ++revision;
    }

    public boolean count (int ticket, int count, TdApi.Error failure) {
      if (ticket != revision) return false;
      approximateCount = failure == null ? count : -1;
      countError = failure;
      return true;
    }

    public boolean confirm (int ticket, boolean hasMessage, TdApi.Error failure) {
      if (ticket != revision) return false;
      error = failure;
      if (failure == null) availability = hasMessage ? Availability.PRESENT : Availability.EMPTY;
      else if (availability != Availability.PRESENT) availability = Availability.ERROR;
      return true;
    }

    public boolean unknown (int ticket) {
      if (ticket != revision) return false;
      if (availability != Availability.PRESENT) availability = Availability.UNKNOWN;
      return true;
    }

    public boolean isCurrent (int ticket) { return ticket == revision; }

    public void arrived () { ++revision; error = null; availability = Availability.PRESENT; }
    public void invalidate () { ++revision; }
  }

  public static List<Integer> visible (Category[] categories) {
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < categories.length; i++) if (categories[i].availability == Availability.PRESENT) result.add(i);
    return result;
  }

  public static int nearest (List<Integer> visible, int previous) {
    if (visible.contains(previous)) return previous;
    for (int value : visible) if (value >= previous) return value;
    return visible.isEmpty() ? -1 : visible.get(visible.size() - 1);
  }
}
