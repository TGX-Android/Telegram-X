/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.formatInvariant;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.JsonWriter;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** A debugging tracing utility. Debug logging is disabled at compile time by default. */
@UnstableApi
public final class DebugTraceUtil {

  /**
   * Whether to store tracing events for debug logging. Should be set to {@code true} for testing
   * and debugging purposes only.
   */
  @SuppressWarnings("NonFinalStaticField") // Only for debugging/testing.
  public static boolean enableTracing = false;

  /** Events logged by {@link #logEvent}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    EVENT_START,
    EVENT_INPUT_FORMAT,
    EVENT_OUTPUT_FORMAT,
    EVENT_ACCEPTED_INPUT,
    EVENT_PRODUCED_OUTPUT,
    EVENT_INPUT_ENDED,
    EVENT_OUTPUT_ENDED,
    EVENT_REGISTER_NEW_INPUT_STREAM,
    EVENT_SURFACE_TEXTURE_INPUT,
    EVENT_SURFACE_TEXTURE_TRANSFORM_FIX,
    EVENT_QUEUE_FRAME,
    EVENT_QUEUE_BITMAP,
    EVENT_QUEUE_TEXTURE,
    EVENT_OUTPUT_TEXTURE_RENDERED,
    EVENT_RENDERED_TO_OUTPUT_SURFACE,
    EVENT_RECEIVE_END_OF_ALL_INPUT,
    EVENT_RECEIVE_EOS,
    EVENT_SIGNAL_EOS,
    EVENT_SIGNAL_ENDED,
    EVENT_CAN_WRITE_SAMPLE
  })
  @Target(TYPE_USE)
  public @interface Event {}

  public static final String EVENT_START = "Start";
  public static final String EVENT_INPUT_FORMAT = "InputFormat";
  public static final String EVENT_OUTPUT_FORMAT = "OutputFormat";
  public static final String EVENT_ACCEPTED_INPUT = "AcceptedInput";
  public static final String EVENT_PRODUCED_OUTPUT = "ProducedOutput";
  public static final String EVENT_INPUT_ENDED = "InputEnded";
  public static final String EVENT_OUTPUT_ENDED = "OutputEnded";
  public static final String EVENT_REGISTER_NEW_INPUT_STREAM = "RegisterNewInputStream";
  public static final String EVENT_SURFACE_TEXTURE_INPUT = "SurfaceTextureInput";
  public static final String EVENT_SURFACE_TEXTURE_TRANSFORM_FIX = "SurfaceTextureTransformFix";
  public static final String EVENT_QUEUE_FRAME = "QueueFrame";
  public static final String EVENT_QUEUE_BITMAP = "QueueBitmap";
  public static final String EVENT_QUEUE_TEXTURE = "QueueTexture";
  public static final String EVENT_OUTPUT_TEXTURE_RENDERED = "OutputTextureRendered";
  public static final String EVENT_RENDERED_TO_OUTPUT_SURFACE = "RenderedToOutputSurface";
  public static final String EVENT_RECEIVE_END_OF_ALL_INPUT = "ReceiveEndOfAllInput";
  public static final String EVENT_RECEIVE_EOS = "ReceiveEOS";
  public static final String EVENT_SIGNAL_EOS = "SignalEOS";
  public static final String EVENT_SIGNAL_ENDED = "SignalEnded";
  public static final String EVENT_CAN_WRITE_SAMPLE = "CanWriteSample";

  /** Components logged by {@link #logEvent}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    COMPONENT_TRANSFORMER_INTERNAL,
    COMPONENT_ASSET_LOADER,
    COMPONENT_AUDIO_DECODER,
    COMPONENT_AUDIO_GRAPH,
    COMPONENT_AUDIO_MIXER,
    COMPONENT_AUDIO_ENCODER,
    COMPONENT_VIDEO_DECODER,
    COMPONENT_VFP,
    COMPONENT_BITMAP_TEXTURE_MANAGER,
    COMPONENT_EXTERNAL_TEXTURE_MANAGER,
    COMPONENT_TEX_ID_TEXTURE_MANAGER,
    COMPONENT_COMPOSITOR,
    COMPONENT_VIDEO_ENCODER,
    COMPONENT_MUXER
  })
  @Target(TYPE_USE)
  public @interface Component {}

  public static final String COMPONENT_TRANSFORMER_INTERNAL = "TransformerInternal";
  public static final String COMPONENT_ASSET_LOADER = "AssetLoader";
  public static final String COMPONENT_AUDIO_DECODER = "AudioDecoder";
  public static final String COMPONENT_AUDIO_GRAPH = "AudioGraph";
  public static final String COMPONENT_AUDIO_MIXER = "AudioMixer";
  public static final String COMPONENT_AUDIO_ENCODER = "AudioEncoder";
  public static final String COMPONENT_VIDEO_DECODER = "VideoDecoder";
  public static final String COMPONENT_VFP = "VideoFrameProcessor";
  public static final String COMPONENT_EXTERNAL_TEXTURE_MANAGER = "ExternalTextureManager";
  public static final String COMPONENT_BITMAP_TEXTURE_MANAGER = "BitmapTextureManager";
  public static final String COMPONENT_TEX_ID_TEXTURE_MANAGER = "TexIdTextureManager";
  public static final String COMPONENT_COMPOSITOR = "Compositor";
  public static final String COMPONENT_VIDEO_ENCODER = "VideoEncoder";
  public static final String COMPONENT_MUXER = "Muxer";

  // For a given component, events are in the rough expected order that they occur.
  private static final ImmutableMap<@Component String, List<@Event String>> COMPONENTS_TO_EVENTS =
      ImmutableMap.<String, List<String>>builder()
          .put(COMPONENT_TRANSFORMER_INTERNAL, ImmutableList.of(EVENT_START))
          .put(COMPONENT_ASSET_LOADER, ImmutableList.of(EVENT_INPUT_FORMAT, EVENT_OUTPUT_FORMAT))
          .put(
              COMPONENT_AUDIO_DECODER,
              ImmutableList.of(
                  EVENT_INPUT_FORMAT,
                  EVENT_OUTPUT_FORMAT,
                  EVENT_ACCEPTED_INPUT,
                  EVENT_PRODUCED_OUTPUT,
                  EVENT_INPUT_ENDED,
                  EVENT_OUTPUT_ENDED))
          .put(
              COMPONENT_AUDIO_GRAPH,
              ImmutableList.of(EVENT_REGISTER_NEW_INPUT_STREAM, EVENT_OUTPUT_ENDED))
          .put(
              COMPONENT_AUDIO_MIXER,
              ImmutableList.of(
                  EVENT_REGISTER_NEW_INPUT_STREAM, EVENT_OUTPUT_FORMAT, EVENT_PRODUCED_OUTPUT))
          .put(
              COMPONENT_AUDIO_ENCODER,
              ImmutableList.of(
                  EVENT_INPUT_FORMAT,
                  EVENT_OUTPUT_FORMAT,
                  EVENT_ACCEPTED_INPUT,
                  EVENT_PRODUCED_OUTPUT,
                  EVENT_INPUT_ENDED,
                  EVENT_OUTPUT_ENDED))
          .put(
              COMPONENT_VIDEO_DECODER,
              ImmutableList.of(
                  EVENT_INPUT_FORMAT,
                  EVENT_OUTPUT_FORMAT,
                  EVENT_ACCEPTED_INPUT,
                  EVENT_PRODUCED_OUTPUT,
                  EVENT_INPUT_ENDED,
                  EVENT_OUTPUT_ENDED))
          .put(
              COMPONENT_VFP,
              ImmutableList.of(
                  EVENT_REGISTER_NEW_INPUT_STREAM,
                  EVENT_SURFACE_TEXTURE_INPUT,
                  EVENT_QUEUE_FRAME,
                  EVENT_QUEUE_BITMAP,
                  EVENT_QUEUE_TEXTURE,
                  EVENT_RENDERED_TO_OUTPUT_SURFACE,
                  EVENT_OUTPUT_TEXTURE_RENDERED,
                  EVENT_RECEIVE_END_OF_ALL_INPUT,
                  EVENT_SIGNAL_ENDED))
          .put(
              COMPONENT_EXTERNAL_TEXTURE_MANAGER,
              ImmutableList.of(EVENT_SIGNAL_EOS, EVENT_SURFACE_TEXTURE_TRANSFORM_FIX))
          .put(COMPONENT_BITMAP_TEXTURE_MANAGER, ImmutableList.of(EVENT_SIGNAL_EOS))
          .put(COMPONENT_TEX_ID_TEXTURE_MANAGER, ImmutableList.of(EVENT_SIGNAL_EOS))
          .put(COMPONENT_COMPOSITOR, ImmutableList.of(EVENT_OUTPUT_TEXTURE_RENDERED))
          .put(
              COMPONENT_VIDEO_ENCODER,
              ImmutableList.of(
                  EVENT_INPUT_FORMAT,
                  EVENT_OUTPUT_FORMAT,
                  EVENT_ACCEPTED_INPUT,
                  EVENT_PRODUCED_OUTPUT,
                  EVENT_INPUT_ENDED,
                  EVENT_OUTPUT_ENDED))
          .put(
              COMPONENT_MUXER,
              ImmutableList.of(
                  EVENT_INPUT_FORMAT,
                  EVENT_CAN_WRITE_SAMPLE,
                  EVENT_ACCEPTED_INPUT,
                  EVENT_INPUT_ENDED,
                  EVENT_OUTPUT_ENDED))
          .buildOrThrow();

  /**
   * Whether to {@linkplain Log#d(String, String) log} tracing events to the logcat as they occur.
   * Should be set to {@code true} for testing and debugging purposes only.
   *
   * <p>Note that enabling this can add a large amount of logcat lines.
   *
   * <p>Requires {@link #enableTracing} to be true.
   */
  private static final boolean ENABLE_TRACES_IN_LOGCAT = false;

  private static final int MAX_FIRST_LAST_LOGS = 10;

  @GuardedBy("DebugTraceUtil.class")
  private static final Map<@Component String, Map<@Event String, EventLogger>>
      componentsToEventsToLogs = new LinkedHashMap<>();

  @GuardedBy("DebugTraceUtil.class")
  private static long startTimeMs = SystemClock.DEFAULT.elapsedRealtime();

  public static synchronized void reset() {
    componentsToEventsToLogs.clear();
    startTimeMs = SystemClock.DEFAULT.elapsedRealtime();
  }

  /**
   * Logs a new event, if debug logging is enabled.
   *
   * @param component The {@link Component} to log.
   * @param event The {@link Event} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   * @param extraFormat Format string for optional extra information. See {@link
   *     Util#formatInvariant(String, Object...)}.
   * @param extraArgs Arguments for optional extra information.
   */
  @SuppressWarnings("ComputeIfAbsentContainsKey") // Avoid Java8 for visibility
  public static synchronized void logEvent(
      @Component String component,
      @Event String event,
      long presentationTimeUs,
      String extraFormat,
      Object... extraArgs) {
    if (!enableTracing) {
      return;
    }
    long eventTimeMs = SystemClock.DEFAULT.elapsedRealtime() - startTimeMs;

    if (!componentsToEventsToLogs.containsKey(component)) {
      componentsToEventsToLogs.put(component, new LinkedHashMap<>());
    }
    Map<@Event String, EventLogger> events = componentsToEventsToLogs.get(component);
    if (!events.containsKey(event)) {
      events.put(event, new EventLogger());
    }
    EventLogger logger = events.get(event);
    String extra = Util.formatInvariant(extraFormat, extraArgs);
    EventLog eventLog = new EventLog(presentationTimeUs, eventTimeMs, extra);
    logger.addLog(eventLog);
    if (ENABLE_TRACES_IN_LOGCAT) {
      Log.d("DebugTrace-" + component, event + ": " + eventLog);
    }
  }

  /**
   * Logs a new event, if debug logging is enabled.
   *
   * @param component The {@link Component} to log.
   * @param event The {@link Event} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   */
  public static synchronized void logEvent(
      @Component String component, @Event String event, long presentationTimeUs) {
    logEvent(component, event, presentationTimeUs, /* extraFormat= */ "");
  }

  /**
   * Logs an {@link Event} for a codec, if debug logging is enabled.
   *
   * @param isDecoder Whether the codec is a decoder.
   * @param isVideo Whether the codec is for video.
   * @param eventName The {@link Event} to log.
   * @param presentationTimeUs The current presentation time of the media. Use {@link C#TIME_UNSET}
   *     if unknown, {@link C#TIME_END_OF_SOURCE} if EOS.
   * @param extraFormat Format string for optional extra information. See {@link
   *     Util#formatInvariant(String, Object...)}.
   * @param extraArgs Arguments for optional extra information.
   */
  public static synchronized void logCodecEvent(
      boolean isDecoder,
      boolean isVideo,
      @Event String eventName,
      long presentationTimeUs,
      String extraFormat,
      Object... extraArgs) {
    logEvent(
        getCodecComponent(isDecoder, isVideo),
        eventName,
        presentationTimeUs,
        extraFormat,
        extraArgs);
  }

  /**
   * Generate a summary of the logged events, containing the total number of times an event happened
   * and the detailed log of a window of the oldest and newest events.
   */
  public static synchronized String generateTraceSummary() {
    if (!enableTracing) {
      return "\"Tracing disabled\"";
    }
    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    try {
      jsonWriter.beginObject();
      for (Map.Entry<@Component String, List<@Event String>> componentToEvents :
          COMPONENTS_TO_EVENTS.entrySet()) {
        @Component String component = componentToEvents.getKey();
        List<@Event String> componentEvents = componentToEvents.getValue();

        jsonWriter.name(component);
        @Nullable
        Map<@Event String, EventLogger> eventsToLogs = componentsToEventsToLogs.get(component);
        jsonWriter.beginObject();
        for (@Event String event : componentEvents) {
          jsonWriter.name(event);
          if (eventsToLogs != null && eventsToLogs.containsKey(event)) {
            checkNotNull(eventsToLogs.get(event)).toJson(jsonWriter);
          } else {
            jsonWriter.value("No events");
          }
        }
        jsonWriter.endObject();
      }
      jsonWriter.endObject();
      return stringWriter.toString();
    } catch (IOException e) {
      return "\"Error generating trace summary\"";
    } finally {
      Util.closeQuietly(jsonWriter);
    }
  }

  /** Dumps all the logged events to the {@link Writer} as tab separated values (tsv). */
  public static synchronized void dumpTsv(Writer writer) throws IOException {
    if (!enableTracing) {
      writer.write("Tracing disabled");
      return;
    }
    writer.write("component\tevent\ttimestamp\tpresentation\textra\n");
    for (Map.Entry<@Component String, Map<@Event String, EventLogger>> componentToEventsToLogs :
        componentsToEventsToLogs.entrySet()) {
      @Component String component = componentToEventsToLogs.getKey();
      Map<@Event String, EventLogger> eventsToLogs = componentToEventsToLogs.getValue();
      for (Map.Entry<@Event String, EventLogger> eventToLogs : eventsToLogs.entrySet()) {
        @Event String componentEvent = eventToLogs.getKey();
        ImmutableList<EventLog> eventLogs = eventToLogs.getValue().getLogs();
        for (EventLog eventLog : eventLogs) {
          writer.write(
              formatInvariant(
                  "%s\t%s\t%dms\t%s\t%s\n",
                  component,
                  componentEvent,
                  eventLog.eventTimeMs,
                  presentationTimeToString(eventLog.presentationTimeUs),
                  eventLog.extra));
        }
      }
    }
  }

  private static String presentationTimeToString(long presentationTimeUs) {
    if (presentationTimeUs == C.TIME_UNSET) {
      return "UNSET";
    } else if (presentationTimeUs == C.TIME_END_OF_SOURCE) {
      return "EOS";
    } else {
      return presentationTimeUs + "us";
    }
  }

  private static @Component String getCodecComponent(boolean isDecoder, boolean isVideo) {
    if (isDecoder) {
      if (isVideo) {
        return COMPONENT_VIDEO_DECODER;
      } else {
        return COMPONENT_AUDIO_DECODER;
      }
    } else {
      if (isVideo) {
        return COMPONENT_VIDEO_ENCODER;
      } else {
        return COMPONENT_AUDIO_ENCODER;
      }
    }
  }

  private static final class EventLog {
    public final long presentationTimeUs;
    public final long eventTimeMs;
    public final String extra;

    private EventLog(long presentationTimeUs, long eventTimeMs, String extra) {
      this.presentationTimeUs = presentationTimeUs;
      this.eventTimeMs = eventTimeMs;
      this.extra = extra;
    }

    @Override
    public String toString() {
      return formatInvariant("%s@%dms", presentationTimeToString(presentationTimeUs), eventTimeMs)
          + (extra.isEmpty() ? "" : formatInvariant("(%s)", extra));
    }
  }

  private static final class EventLogger {
    private final List<EventLog> firstLogs;
    private final Queue<EventLog> lastLogs;
    private int totalCount;

    public EventLogger() {
      firstLogs = new ArrayList<>(MAX_FIRST_LAST_LOGS);
      lastLogs = new ArrayDeque<>(MAX_FIRST_LAST_LOGS);
      totalCount = 0;
    }

    public void addLog(EventLog log) {
      if (firstLogs.size() < MAX_FIRST_LAST_LOGS) {
        firstLogs.add(log);
      } else {
        lastLogs.add(log);
        if (lastLogs.size() > MAX_FIRST_LAST_LOGS) {
          lastLogs.remove();
        }
      }
      totalCount++;
    }

    public ImmutableList<EventLog> getLogs() {
      return new ImmutableList.Builder<EventLog>().addAll(firstLogs).addAll(lastLogs).build();
    }

    public void toJson(JsonWriter jsonWriter) throws IOException {
      jsonWriter.beginObject().name("count").value(totalCount).name("first").beginArray();
      for (EventLog eventLog : firstLogs) {
        jsonWriter.value(eventLog.toString());
      }
      jsonWriter.endArray();
      if (!lastLogs.isEmpty()) {
        jsonWriter.name("last").beginArray();
        for (EventLog eventLog : lastLogs) {
          jsonWriter.value(eventLog.toString());
        }
        jsonWriter.endArray();
      }
      jsonWriter.endObject();
    }
  }
}
