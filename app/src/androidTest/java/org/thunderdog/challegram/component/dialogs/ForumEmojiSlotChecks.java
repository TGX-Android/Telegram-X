package org.thunderdog.challegram.component.dialogs;

import android.content.Context;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation;
import org.thunderdog.challegram.stage8.SyntheticEnvironment;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextMedia;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Calls the actual javac-emitted layout callback, not a copied identity predicate.
 * Constructor-free Text/TextMedia have no attached parts, files, TDLib, or custom-emoji fetch.
 * Text.requestMedia still computes/assigns the real receiver key and clears that exact key.
 * The lambda signature is an explicit debug-only seam: refactoring it requires updating this
 * test; failure to find it is not silently skipped. Actual emoji raster/decoding is NOT tested.
 */
public final class ForumEmojiSlotChecks {
  public static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    cases.add(new Stage8SyntheticInstrumentation.Case("emoji_equal_texts_have_distinct_receiver_slots", env -> activeSlots()));
    cases.add(new Stage8SyntheticInstrumentation.Case("emoji_stale_equal_text_callback_cannot_replace_rebound_slot", env -> staleSlot()));
  }

  private static Text icon (String key) throws Exception {
    Text text = allocate(Text.class);
    set(text, "originalText", "*");
    TextMedia media = allocate(TextMedia.class);
    set(media, "keyId", key);
    set(media, "id", 1L);
    set(media, "displayMediaKeyOffset", -1L);
    set(media, "attachedToParts", new ArrayList<>());
    LinkedHashMap<String, TextMedia> entries = new LinkedHashMap<>();
    entries.put(key, media);
    set(text, "media", entries);
    return text;
  }

  private static Method callback () {
    Method result = null;
    for (Method method : ForumChatTopics.class.getDeclaredMethods()) {
      if (method.isSynthetic() && method.getName().startsWith("lambda$layout$") &&
          Arrays.equals(method.getParameterTypes(), new Class<?>[] {int.class, Text.class, TextMedia.class})) {
        require(result == null, "Ambiguous emoji callback; update test seam");
        result = method;
      }
    }
    require(result != null, "Missing (slot, Text, TextMedia) layout callback; inspect production identity seam");
    result.setAccessible(true);
    return result;
  }

  @SuppressWarnings("unchecked")
  private static void activeSlots () throws Exception {
    InvalidationView view = allocate(InvalidationView.class);
    ForumChatTopics topics = new ForumChatTopics(view, null, null);
    KeyReceiver receiver = new KeyReceiver();
    set(topics, "receiver", receiver);
    List<Text> icons = (List<Text>) get(topics, "icons");
    Text first = icon("synthetic-a"), second = icon("synthetic-b"), third = icon("synthetic-c");
    require(first != second && first.equals(second) && second.equals(third), "Fixture must reproduce Text.equals('*') collision");
    icons.add(first); icons.add(second); icons.add(third);
    Method callback = callback();
    callback.invoke(topics, 2, third, null);
    callback.invoke(topics, 0, first, null);
    callback.invoke(topics, 1, second, null);
    require(receiver.keys.equals(Arrays.asList(33L, 1L, 17L)), "Out-of-order callbacks must preserve three distinct receiver slots: " + receiver.keys);
    equal(3, view.invalidations, "Each live callback invalidates once");
    topics.detach();
  }

  @SuppressWarnings("unchecked")
  private static void staleSlot () throws Exception {
    InvalidationView view = allocate(InvalidationView.class);
    ForumChatTopics topics = new ForumChatTopics(view, null, null);
    KeyReceiver receiver = new KeyReceiver();
    set(topics, "receiver", receiver);
    List<Text> icons = (List<Text>) get(topics, "icons");
    Text old = icon("synthetic-old"), replacement = icon("synthetic-new");
    icons.add(icon("synthetic-first")); icons.add(replacement);
    Method callback = callback();
    callback.invoke(topics, 1, old, null);
    require(receiver.keys.isEmpty() && view.invalidations == 0, "Stale equal-text callback must not touch a rebound slot");
    callback.invoke(topics, 1, replacement, null);
    require(receiver.keys.equals(Arrays.asList(17L)), "Replacement retains its own nonzero slot");
    equal(1, view.invalidations, "Replacement invalidates once");
    icons.clear();
    callback.invoke(topics, 1, replacement, null);
    equal(1, receiver.keys.size(), "Shrunk/recycled icon list must reject delayed callback");
    equal(1, view.invalidations, "Delayed callback must not invalidate recycled row");
    topics.detach();
  }

  private static final class KeyReceiver extends ComplexReceiver {
    final List<Long> keys = new ArrayList<>();
    @Override public void clearReceivers (long key) { keys.add(key); }
  }

  /** Never constructed or passed to Android View APIs: callback uses only invalidate(). */
  private static final class InvalidationView extends ChatView {
    int invalidations;
    private InvalidationView (Context context) { super(context, null); }
    @Override public void invalidate () { invalidations++; }
  }
}
