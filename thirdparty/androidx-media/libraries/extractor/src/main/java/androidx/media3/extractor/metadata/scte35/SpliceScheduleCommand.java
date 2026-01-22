/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.extractor.metadata.scte35;

import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents a splice schedule command as defined in SCTE35, Section 9.3.2. */
@UnstableApi
public final class SpliceScheduleCommand extends SpliceCommand {

  /** Represents a splice event as contained in a {@link SpliceScheduleCommand}. */
  public static final class Event {

    /** The splice event id. */
    public final long spliceEventId;

    /** True if the event with id {@link #spliceEventId} has been canceled. */
    public final boolean spliceEventCancelIndicator;

    /**
     * If true, the splice event is an opportunity to exit from the network feed. If false,
     * indicates an opportunity to return to the network feed.
     */
    public final boolean outOfNetworkIndicator;

    /**
     * Whether the splice mode is program splice mode, whereby all PIDs/components are to be
     * spliced. If false, splicing is done per PID/component.
     */
    public final boolean programSpliceFlag;

    /**
     * Represents the time of the signaled splice event as the number of seconds since 00 hours UTC,
     * January 6th, 1980, with the count of intervening leap seconds included.
     */
    public final long utcSpliceTime;

    /**
     * If {@link #programSpliceFlag} is false, a non-empty list containing the {@link
     * ComponentSplice}s. Otherwise, an empty list.
     */
    public final List<ComponentSplice> componentSpliceList;

    /**
     * If {@link #breakDurationUs} is not {@link C#TIME_UNSET}, defines whether {@link
     * #breakDurationUs} should be used to know when to return to the network feed. If {@link
     * #breakDurationUs} is {@link C#TIME_UNSET}, the value is undefined.
     */
    public final boolean autoReturn;

    /**
     * The duration of the splice in microseconds, or {@link C#TIME_UNSET} if no duration is
     * present.
     */
    public final long breakDurationUs;

    /** The unique program id as defined in SCTE35, Section 9.3.2. */
    public final int uniqueProgramId;

    /** Holds the value of {@code avail_num} as defined in SCTE35, Section 9.3.2. */
    public final int availNum;

    /** Holds the value of {@code avails_expected} as defined in SCTE35, Section 9.3.2. */
    public final int availsExpected;

    private Event(
        long spliceEventId,
        boolean spliceEventCancelIndicator,
        boolean outOfNetworkIndicator,
        boolean programSpliceFlag,
        List<ComponentSplice> componentSpliceList,
        long utcSpliceTime,
        boolean autoReturn,
        long breakDurationUs,
        int uniqueProgramId,
        int availNum,
        int availsExpected) {
      this.spliceEventId = spliceEventId;
      this.spliceEventCancelIndicator = spliceEventCancelIndicator;
      this.outOfNetworkIndicator = outOfNetworkIndicator;
      this.programSpliceFlag = programSpliceFlag;
      this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
      this.utcSpliceTime = utcSpliceTime;
      this.autoReturn = autoReturn;
      this.breakDurationUs = breakDurationUs;
      this.uniqueProgramId = uniqueProgramId;
      this.availNum = availNum;
      this.availsExpected = availsExpected;
    }

    private static Event parseFromSection(ParsableByteArray sectionData) {
      long spliceEventId = sectionData.readUnsignedInt();
      // splice_event_cancel_indicator(1), reserved(7).
      boolean spliceEventCancelIndicator = (sectionData.readUnsignedByte() & 0x80) != 0;
      boolean outOfNetworkIndicator = false;
      boolean programSpliceFlag = false;
      long utcSpliceTime = C.TIME_UNSET;
      ArrayList<ComponentSplice> componentSplices = new ArrayList<>();
      int uniqueProgramId = 0;
      int availNum = 0;
      int availsExpected = 0;
      boolean autoReturn = false;
      long breakDurationUs = C.TIME_UNSET;
      if (!spliceEventCancelIndicator) {
        int headerByte = sectionData.readUnsignedByte();
        outOfNetworkIndicator = (headerByte & 0x80) != 0;
        programSpliceFlag = (headerByte & 0x40) != 0;
        boolean durationFlag = (headerByte & 0x20) != 0;
        if (programSpliceFlag) {
          utcSpliceTime = sectionData.readUnsignedInt();
        }
        if (!programSpliceFlag) {
          int componentCount = sectionData.readUnsignedByte();
          componentSplices = new ArrayList<>(componentCount);
          for (int i = 0; i < componentCount; i++) {
            int componentTag = sectionData.readUnsignedByte();
            long componentUtcSpliceTime = sectionData.readUnsignedInt();
            componentSplices.add(new ComponentSplice(componentTag, componentUtcSpliceTime));
          }
        }
        if (durationFlag) {
          long firstByte = sectionData.readUnsignedByte();
          autoReturn = (firstByte & 0x80) != 0;
          long breakDuration90khz = ((firstByte & 0x01) << 32) | sectionData.readUnsignedInt();
          breakDurationUs = breakDuration90khz * 1000 / 90;
        }
        uniqueProgramId = sectionData.readUnsignedShort();
        availNum = sectionData.readUnsignedByte();
        availsExpected = sectionData.readUnsignedByte();
      }
      return new Event(
          spliceEventId,
          spliceEventCancelIndicator,
          outOfNetworkIndicator,
          programSpliceFlag,
          componentSplices,
          utcSpliceTime,
          autoReturn,
          breakDurationUs,
          uniqueProgramId,
          availNum,
          availsExpected);
    }
  }

  /** Holds splicing information for specific splice schedule command components. */
  public static final class ComponentSplice {

    public final int componentTag;
    public final long utcSpliceTime;

    private ComponentSplice(int componentTag, long utcSpliceTime) {
      this.componentTag = componentTag;
      this.utcSpliceTime = utcSpliceTime;
    }
  }

  /** The list of scheduled events. */
  public final List<Event> events;

  private SpliceScheduleCommand(List<Event> events) {
    this.events = Collections.unmodifiableList(events);
  }

  /* package */ static SpliceScheduleCommand parseFromSection(ParsableByteArray sectionData) {
    int spliceCount = sectionData.readUnsignedByte();
    ArrayList<Event> events = new ArrayList<>(spliceCount);
    for (int i = 0; i < spliceCount; i++) {
      events.add(Event.parseFromSection(sectionData));
    }
    return new SpliceScheduleCommand(events);
  }
}
