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
package androidx.media3.common;

import static androidx.media3.common.util.Assertions.checkState;
import static com.google.common.math.DoubleMath.fuzzyEquals;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a media format.
 *
 * <p>When building formats, populate all fields whose values are known and relevant to the type of
 * format being constructed. For information about different types of format, see ExoPlayer's <a
 * href="https://developer.android.com/media/media3/exoplayer/supported-formats">Supported formats
 * page</a>.
 *
 * <h2>Fields commonly relevant to all formats</h2>
 *
 * <ul>
 *   <li>{@link #id}
 *   <li>{@link #label}
 *   <li>{@link #labels}
 *   <li>{@link #language}
 *   <li>{@link #selectionFlags}
 *   <li>{@link #roleFlags}
 *   <li>{@link #averageBitrate}
 *   <li>{@link #peakBitrate}
 *   <li>{@link #codecs}
 *   <li>{@link #metadata}
 * </ul>
 *
 * <h2 id="container-formats">Fields relevant to container formats</h2>
 *
 * <ul>
 *   <li>{@link #containerMimeType}
 *   <li>If the container only contains a single media track, <a href="#sample-formats">fields
 *       relevant to sample formats</a> can are also be relevant and can be set to describe the
 *       sample format of that track.
 *   <li>If the container only contains one track of a given type (possibly alongside tracks of
 *       other types), then fields relevant to that track type can be set to describe the properties
 *       of the track. See the sections below for <a href="#video-formats">video</a>, <a
 *       href="#audio-formats">audio</a> and <a href="#text-formats">text</a> formats.
 * </ul>
 *
 * <h2 id="sample-formats">Fields relevant to sample formats</h2>
 *
 * <ul>
 *   <li>{@link #sampleMimeType}
 *   <li>{@link #maxInputSize}
 *   <li>{@link #initializationData}
 *   <li>{@link #drmInitData}
 *   <li>{@link #subsampleOffsetUs}
 *   <li>Fields relevant to the sample format's track type are also relevant. See the sections below
 *       for <a href="#video-formats">video</a>, <a href="#audio-formats">audio</a> and <a
 *       href="#text-formats">text</a> formats.
 * </ul>
 *
 * <h2 id="video-formats">Fields relevant to video formats</h2>
 *
 * <ul>
 *   <li>{@link #width}
 *   <li>{@link #height}
 *   <li>{@link #frameRate}
 *   <li>{@link #rotationDegrees}
 *   <li>{@link #pixelWidthHeightRatio}
 *   <li>{@link #projectionData}
 *   <li>{@link #stereoMode}
 *   <li>{@link #colorInfo}
 *   <li>{@link #maxSubLayers}
 * </ul>
 *
 * <h2 id="audio-formats">Fields relevant to audio formats</h2>
 *
 * <ul>
 *   <li>{@link #channelCount}
 *   <li>{@link #sampleRate}
 *   <li>{@link #pcmEncoding}
 *   <li>{@link #encoderDelay}
 *   <li>{@link #encoderPadding}
 * </ul>
 *
 * <h2 id="text-formats">Fields relevant to text formats</h2>
 *
 * <ul>
 *   <li>{@link #accessibilityChannel}
 * </ul>
 *
 * <h2 id="image-formats">Fields relevant to image formats</h2>
 *
 * <ul>
 *   <li>{@link #tileCountHorizontal}
 *   <li>{@link #tileCountVertical}
 * </ul>
 */
public final class Format {

  /**
   * Builds {@link Format} instances.
   *
   * <p>Use Format#buildUpon() to obtain a builder representing an existing {@link Format}.
   *
   * <p>When building formats, populate all fields whose values are known and relevant to the type
   * of format being constructed. See the {@link Format} Javadoc for information about which fields
   * should be set for different types of format.
   */
  @UnstableApi
  public static final class Builder {

    @Nullable private String id;
    @Nullable private String label;
    private List<Label> labels;
    @Nullable private String language;
    private @C.SelectionFlags int selectionFlags;
    private @C.RoleFlags int roleFlags;
    private @C.AuxiliaryTrackType int auxiliaryTrackType;
    private int averageBitrate;
    private int peakBitrate;
    @Nullable private String codecs;
    @Nullable private Metadata metadata;
    @Nullable private Object customData;

    // Container specific.

    @Nullable private String containerMimeType;

    // Sample specific.

    @Nullable private String sampleMimeType;
    private int maxInputSize;
    private int maxNumReorderSamples;
    @Nullable private List<byte[]> initializationData;
    @Nullable private DrmInitData drmInitData;
    private long subsampleOffsetUs;
    private boolean hasPrerollSamples;

    // Video specific.

    private int width;
    private int height;
    private float frameRate;
    private int rotationDegrees;
    private float pixelWidthHeightRatio;
    @Nullable private byte[] projectionData;
    private @C.StereoMode int stereoMode;
    @Nullable private ColorInfo colorInfo;
    private int maxSubLayers;

    // Audio specific.

    private int channelCount;
    private int sampleRate;
    private @C.PcmEncoding int pcmEncoding;
    private int encoderDelay;
    private int encoderPadding;

    // Text specific.

    private int accessibilityChannel;
    @UnstableApi private @CueReplacementBehavior int cueReplacementBehavior;

    // Image specific

    private int tileCountHorizontal;
    private int tileCountVertical;

    // Provided by the source.

    private @C.CryptoType int cryptoType;

    /** Creates a new instance with default values. */
    public Builder() {
      labels = ImmutableList.of();
      averageBitrate = NO_VALUE;
      peakBitrate = NO_VALUE;
      // Sample specific.
      maxInputSize = NO_VALUE;
      maxNumReorderSamples = NO_VALUE;
      subsampleOffsetUs = OFFSET_SAMPLE_RELATIVE;
      // Video specific.
      width = NO_VALUE;
      height = NO_VALUE;
      frameRate = NO_VALUE;
      pixelWidthHeightRatio = 1.0f;
      stereoMode = NO_VALUE;
      maxSubLayers = NO_VALUE;
      // Audio specific.
      channelCount = NO_VALUE;
      sampleRate = NO_VALUE;
      pcmEncoding = NO_VALUE;
      // Text specific.
      accessibilityChannel = NO_VALUE;
      cueReplacementBehavior = CUE_REPLACEMENT_BEHAVIOR_MERGE;
      // Image specific.
      tileCountHorizontal = NO_VALUE;
      tileCountVertical = NO_VALUE;
      // Provided by the source.
      cryptoType = C.CRYPTO_TYPE_NONE;
      auxiliaryTrackType = C.AUXILIARY_TRACK_TYPE_UNDEFINED;
    }

    /**
     * Creates a new instance to build upon the provided {@link Format}.
     *
     * @param format The {@link Format} to build upon.
     */
    private Builder(Format format) {
      this.id = format.id;
      this.label = format.label;
      this.labels = format.labels;
      this.language = format.language;
      this.selectionFlags = format.selectionFlags;
      this.roleFlags = format.roleFlags;
      this.averageBitrate = format.averageBitrate;
      this.peakBitrate = format.peakBitrate;
      this.codecs = format.codecs;
      this.metadata = format.metadata;
      this.customData = format.customData;
      // Container specific.
      this.containerMimeType = format.containerMimeType;
      // Sample specific.
      this.sampleMimeType = format.sampleMimeType;
      this.maxInputSize = format.maxInputSize;
      this.maxNumReorderSamples = format.maxNumReorderSamples;
      this.initializationData = format.initializationData;
      this.drmInitData = format.drmInitData;
      this.subsampleOffsetUs = format.subsampleOffsetUs;
      this.hasPrerollSamples = format.hasPrerollSamples;
      // Video specific.
      this.width = format.width;
      this.height = format.height;
      this.frameRate = format.frameRate;
      this.rotationDegrees = format.rotationDegrees;
      this.pixelWidthHeightRatio = format.pixelWidthHeightRatio;
      this.projectionData = format.projectionData;
      this.stereoMode = format.stereoMode;
      this.colorInfo = format.colorInfo;
      this.maxSubLayers = format.maxSubLayers;
      // Audio specific.
      this.channelCount = format.channelCount;
      this.sampleRate = format.sampleRate;
      this.pcmEncoding = format.pcmEncoding;
      this.encoderDelay = format.encoderDelay;
      this.encoderPadding = format.encoderPadding;
      // Text specific.
      this.accessibilityChannel = format.accessibilityChannel;
      this.cueReplacementBehavior = format.cueReplacementBehavior;
      // Image specific.
      this.tileCountHorizontal = format.tileCountHorizontal;
      this.tileCountVertical = format.tileCountVertical;
      // Provided by the source.
      this.cryptoType = format.cryptoType;
    }

    /**
     * Sets {@link Format#id}. The default value is {@code null}.
     *
     * @param id The {@link Format#id}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setId(@Nullable String id) {
      this.id = id;
      return this;
    }

    /**
     * Sets {@link Format#id} to {@link Integer#toString() Integer.toString(id)}. The default value
     * is {@code null}.
     *
     * @param id The {@link Format#id}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setId(int id) {
      this.id = Integer.toString(id);
      return this;
    }

    /**
     * Sets {@link Format#label}. The default value is {@code null}.
     *
     * <p>If both this default label and a list of {@link #setLabels labels} are set, this default
     * label must be part of label list.
     *
     * @param label The {@link Format#label}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setLabel(@Nullable String label) {
      this.label = label;
      return this;
    }

    /**
     * Sets {@link Format#labels}. The default value is an empty list.
     *
     * <p>If both the default {@linkplain #setLabel label} and this list are set, the default label
     * must be part of this list of labels.
     *
     * @param labels The {@link Format#labels}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setLabels(List<Label> labels) {
      this.labels = ImmutableList.copyOf(labels);
      return this;
    }

    /**
     * Sets {@link Format#language}. The default value is {@code null}.
     *
     * @param language The {@link Format#language}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setLanguage(@Nullable String language) {
      this.language = language;
      return this;
    }

    /**
     * Sets {@link Format#selectionFlags}. The default value is 0.
     *
     * @param selectionFlags The {@link Format#selectionFlags}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setSelectionFlags(@C.SelectionFlags int selectionFlags) {
      this.selectionFlags = selectionFlags;
      return this;
    }

    /**
     * Sets {@link Format#roleFlags}. The default value is 0.
     *
     * <p>When {@code roleFlags} includes {@link C#ROLE_FLAG_AUXILIARY}, then the specific {@link
     * C.AuxiliaryTrackType} can also be {@linkplain #setAuxiliaryTrackType(int) set}.
     *
     * @param roleFlags The {@link Format#roleFlags}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setRoleFlags(@C.RoleFlags int roleFlags) {
      this.roleFlags = roleFlags;
      return this;
    }

    /**
     * Sets {@link Format#auxiliaryTrackType}. The default value is {@link
     * C#AUXILIARY_TRACK_TYPE_UNDEFINED}.
     *
     * <p>This must be set to a value other than {@link C#AUXILIARY_TRACK_TYPE_UNDEFINED} only when
     * {@linkplain #setRoleFlags(int) role flags} contains {@link C#ROLE_FLAG_AUXILIARY}.
     *
     * @param auxiliaryTrackType The {@link Format#auxiliaryTrackType}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setAuxiliaryTrackType(@C.AuxiliaryTrackType int auxiliaryTrackType) {
      this.auxiliaryTrackType = auxiliaryTrackType;
      return this;
    }

    /**
     * Sets {@link Format#averageBitrate}. The default value is {@link #NO_VALUE}.
     *
     * @param averageBitrate The {@link Format#averageBitrate}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setAverageBitrate(int averageBitrate) {
      this.averageBitrate = averageBitrate;
      return this;
    }

    /**
     * Sets {@link Format#peakBitrate}. The default value is {@link #NO_VALUE}.
     *
     * @param peakBitrate The {@link Format#peakBitrate}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setPeakBitrate(int peakBitrate) {
      this.peakBitrate = peakBitrate;
      return this;
    }

    /**
     * Sets {@link Format#codecs}. The default value is {@code null}.
     *
     * @param codecs The {@link Format#codecs}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setCodecs(@Nullable String codecs) {
      this.codecs = codecs;
      return this;
    }

    /**
     * Sets {@link Format#metadata}. The default value is {@code null}.
     *
     * @param metadata The {@link Format#metadata}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setMetadata(@Nullable Metadata metadata) {
      this.metadata = metadata;
      return this;
    }

    /**
     * Sets the opaque object {@link Format#customData}. The default value is null.
     *
     * <p>This value is not included in serialized {@link Bundle} instances of this class that are
     * used to transfer data to other processes.
     *
     * @param customData The {@link Format#customData}.
     * @return The builder.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setCustomData(@Nullable Object customData) {
      this.customData = customData;
      return this;
    }

    // Container specific.

    /**
     * Sets {@link Format#containerMimeType}. The default value is {@code null}.
     *
     * @param containerMimeType The {@link Format#containerMimeType}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setContainerMimeType(@Nullable String containerMimeType) {
      this.containerMimeType = MimeTypes.normalizeMimeType(containerMimeType);
      return this;
    }

    // Sample specific.

    /**
     * Sets {@link Format#sampleMimeType}. The default value is {@code null}.
     *
     * @param sampleMimeType {@link Format#sampleMimeType}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setSampleMimeType(@Nullable String sampleMimeType) {
      this.sampleMimeType = MimeTypes.normalizeMimeType(sampleMimeType);
      return this;
    }

    /**
     * Sets {@link Format#maxInputSize}. The default value is {@link #NO_VALUE}.
     *
     * @param maxInputSize The {@link Format#maxInputSize}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxInputSize(int maxInputSize) {
      this.maxInputSize = maxInputSize;
      return this;
    }

    /**
     * Sets {@link Format#maxNumReorderSamples}. The default value is {@link #NO_VALUE}.
     *
     * @param maxNumReorderSamples {@link Format#maxNumReorderSamples}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxNumReorderSamples(int maxNumReorderSamples) {
      this.maxNumReorderSamples = maxNumReorderSamples;
      return this;
    }

    /**
     * Sets {@link Format#initializationData}. The default value is {@code null}.
     *
     * @param initializationData The {@link Format#initializationData}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setInitializationData(@Nullable List<byte[]> initializationData) {
      this.initializationData = initializationData;
      return this;
    }

    /**
     * Sets {@link Format#drmInitData}. The default value is {@code null}.
     *
     * @param drmInitData The {@link Format#drmInitData}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setDrmInitData(@Nullable DrmInitData drmInitData) {
      this.drmInitData = drmInitData;
      return this;
    }

    /**
     * Sets {@link Format#subsampleOffsetUs}. The default value is {@link #OFFSET_SAMPLE_RELATIVE}.
     *
     * @param subsampleOffsetUs The {@link Format#subsampleOffsetUs}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setSubsampleOffsetUs(long subsampleOffsetUs) {
      this.subsampleOffsetUs = subsampleOffsetUs;
      return this;
    }

    /**
     * Sets {@link Format#hasPrerollSamples}. The default value is {@code false}.
     *
     * @param hasPrerollSamples The {@link Format#hasPrerollSamples}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setHasPrerollSamples(boolean hasPrerollSamples) {
      this.hasPrerollSamples = hasPrerollSamples;
      return this;
    }

    // Video specific.

    /**
     * Sets {@link Format#width}. The default value is {@link #NO_VALUE}.
     *
     * @param width The {@link Format#width}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setWidth(int width) {
      this.width = width;
      return this;
    }

    /**
     * Sets {@link Format#height}. The default value is {@link #NO_VALUE}.
     *
     * @param height The {@link Format#height}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setHeight(int height) {
      this.height = height;
      return this;
    }

    /**
     * Sets {@link Format#frameRate}. The default value is {@link #NO_VALUE}.
     *
     * @param frameRate The {@link Format#frameRate}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setFrameRate(float frameRate) {
      this.frameRate = frameRate;
      return this;
    }

    /**
     * Sets {@link Format#rotationDegrees}. The default value is 0.
     *
     * @param rotationDegrees The {@link Format#rotationDegrees}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setRotationDegrees(int rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    /**
     * Sets {@link Format#pixelWidthHeightRatio}. The default value is 1.0f.
     *
     * @param pixelWidthHeightRatio The {@link Format#pixelWidthHeightRatio}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setPixelWidthHeightRatio(float pixelWidthHeightRatio) {
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      return this;
    }

    /**
     * Sets {@link Format#projectionData}. The default value is {@code null}.
     *
     * @param projectionData The {@link Format#projectionData}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setProjectionData(@Nullable byte[] projectionData) {
      this.projectionData = projectionData;
      return this;
    }

    /**
     * Sets {@link Format#stereoMode}. The default value is {@link #NO_VALUE}.
     *
     * @param stereoMode The {@link Format#stereoMode}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setStereoMode(@C.StereoMode int stereoMode) {
      this.stereoMode = stereoMode;
      return this;
    }

    /**
     * Sets {@link Format#colorInfo}. The default value is {@code null}.
     *
     * @param colorInfo The {@link Format#colorInfo}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setColorInfo(@Nullable ColorInfo colorInfo) {
      this.colorInfo = colorInfo;
      return this;
    }

    /**
     * Sets {@link Format#maxSubLayers}. The default value is {@link #NO_VALUE}.
     *
     * @param maxSubLayers The {@link Format#maxSubLayers}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxSubLayers(int maxSubLayers) {
      this.maxSubLayers = maxSubLayers;
      return this;
    }

    // Audio specific.

    /**
     * Sets {@link Format#channelCount}. The default value is {@link #NO_VALUE}.
     *
     * @param channelCount The {@link Format#channelCount}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setChannelCount(int channelCount) {
      this.channelCount = channelCount;
      return this;
    }

    /**
     * Sets {@link Format#sampleRate}. The default value is {@link #NO_VALUE}.
     *
     * @param sampleRate The {@link Format#sampleRate}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setSampleRate(int sampleRate) {
      this.sampleRate = sampleRate;
      return this;
    }

    /**
     * Sets {@link Format#pcmEncoding}. The default value is {@link #NO_VALUE}.
     *
     * @param pcmEncoding The {@link Format#pcmEncoding}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setPcmEncoding(@C.PcmEncoding int pcmEncoding) {
      this.pcmEncoding = pcmEncoding;
      return this;
    }

    /**
     * Sets {@link Format#encoderDelay}. The default value is 0.
     *
     * @param encoderDelay The {@link Format#encoderDelay}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setEncoderDelay(int encoderDelay) {
      this.encoderDelay = encoderDelay;
      return this;
    }

    /**
     * Sets {@link Format#encoderPadding}. The default value is 0.
     *
     * @param encoderPadding The {@link Format#encoderPadding}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setEncoderPadding(int encoderPadding) {
      this.encoderPadding = encoderPadding;
      return this;
    }

    // Text specific.

    /**
     * Sets {@link Format#accessibilityChannel}. The default value is {@link #NO_VALUE}.
     *
     * @param accessibilityChannel The {@link Format#accessibilityChannel}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setAccessibilityChannel(int accessibilityChannel) {
      this.accessibilityChannel = accessibilityChannel;
      return this;
    }

    /**
     * Sets {@link Format#cueReplacementBehavior}. The default value is {@link
     * #CUE_REPLACEMENT_BEHAVIOR_MERGE}.
     *
     * @param cueReplacementBehavior The {@link Format.CueReplacementBehavior}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setCueReplacementBehavior(@CueReplacementBehavior int cueReplacementBehavior) {
      this.cueReplacementBehavior = cueReplacementBehavior;
      return this;
    }

    // Image specific.

    /**
     * Sets {@link Format#tileCountHorizontal}. The default value is {@link #NO_VALUE}.
     *
     * @param tileCountHorizontal The {@link Format#tileCountHorizontal}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setTileCountHorizontal(int tileCountHorizontal) {
      this.tileCountHorizontal = tileCountHorizontal;
      return this;
    }

    /**
     * Sets {@link Format#tileCountVertical}. The default value is {@link #NO_VALUE}.
     *
     * @param tileCountVertical The {@link Format#tileCountVertical}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setTileCountVertical(int tileCountVertical) {
      this.tileCountVertical = tileCountVertical;
      return this;
    }

    // Provided by source.

    /**
     * Sets {@link Format#cryptoType}. The default value is {@link C#CRYPTO_TYPE_NONE}.
     *
     * @param cryptoType The {@link C.CryptoType}.
     * @return The builder.
     */
    @CanIgnoreReturnValue
    public Builder setCryptoType(@C.CryptoType int cryptoType) {
      this.cryptoType = cryptoType;
      return this;
    }

    // Build.

    public Format build() {
      return new Format(/* builder= */ this);
    }
  }

  /**
   * The replacement behaviors for consecutive samples in a {@linkplain C#TRACK_TYPE_TEXT text
   * track} of type {@link MimeTypes#APPLICATION_MEDIA3_CUES}.
   */
  @UnstableApi
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    CUE_REPLACEMENT_BEHAVIOR_MERGE,
    CUE_REPLACEMENT_BEHAVIOR_REPLACE,
  })
  public @interface CueReplacementBehavior {}

  /**
   * Subsequent cues should be merged with any previous cues that should still be shown on screen.
   *
   * <p>Tracks with this behavior must not contain samples with an {@linkplain C#TIME_UNSET unset}
   * duration.
   */
  @UnstableApi public static final int CUE_REPLACEMENT_BEHAVIOR_MERGE = 1;

  /**
   * Subsequent cues should replace all previous cues.
   *
   * <p>Tracks with this behavior may contain samples with an {@linkplain C#TIME_UNSET unset}
   * duration (but the duration may also be set to a 'real' value).
   */
  @UnstableApi public static final int CUE_REPLACEMENT_BEHAVIOR_REPLACE = 2;

  /** A value for various fields to indicate that the field's value is unknown or not applicable. */
  public static final int NO_VALUE = -1;

  /**
   * A value for {@link #subsampleOffsetUs} to indicate that subsample timestamps are relative to
   * the timestamps of their parent samples.
   */
  @UnstableApi public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

  private static final Format DEFAULT = new Builder().build();

  /** An identifier for the format, or null if unknown or not applicable. */
  @Nullable public final String id;

  /**
   * The default human readable label, or null if unknown or not applicable.
   *
   * <p>If non-null, the same label will be part of {@link #labels} too. If null, {@link #labels}
   * will be empty.
   */
  @Nullable public final String label;

  /**
   * The human readable list of labels, or an empty list if unknown or not applicable.
   *
   * <p>If non-empty, the default {@link #label} will be part of this list. If empty, the default
   * {@link #label} will be null.
   */
  @UnstableApi public final List<Label> labels;

  /** The language as an IETF BCP 47 conformant tag, or null if unknown or not applicable. */
  @Nullable public final String language;

  /** Track selection flags. */
  public final @C.SelectionFlags int selectionFlags;

  /** Track role flags. */
  public final @C.RoleFlags int roleFlags;

  /** The auxiliary track type. */
  @UnstableApi public final @C.AuxiliaryTrackType int auxiliaryTrackType;

  /**
   * The average bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The
   * way in which this field is populated depends on the type of media to which the format
   * corresponds:
   *
   * <ul>
   *   <li>DASH representations: Always {@link Format#NO_VALUE}.
   *   <li>HLS variants: The {@code AVERAGE-BANDWIDTH} attribute defined on the corresponding {@code
   *       EXT-X-STREAM-INF} tag in the multivariant playlist, or {@link Format#NO_VALUE} if not
   *       present.
   *   <li>SmoothStreaming track elements: The {@code Bitrate} attribute defined on the
   *       corresponding {@code TrackElement} in the manifest, or {@link Format#NO_VALUE} if not
   *       present.
   *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
   *       the average bitrate of the container if known.
   *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the average
   *       bitrate of the stream of samples with type {@link #sampleMimeType} if known. Note that if
   *       {@link #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then
   *       this bitrate is for the stream of still compressed samples.
   * </ul>
   */
  @UnstableApi public final int averageBitrate;

  /**
   * The peak bitrate in bits per second, or {@link #NO_VALUE} if unknown or not applicable. The way
   * in which this field is populated depends on the type of media to which the format corresponds:
   *
   * <ul>
   *   <li>DASH representations: The {@code @bandwidth} attribute of the corresponding {@code
   *       Representation} element in the manifest.
   *   <li>HLS variants: The {@code BANDWIDTH} attribute defined on the corresponding {@code
   *       EXT-X-STREAM-INF} tag.
   *   <li>SmoothStreaming track elements: Always {@link Format#NO_VALUE}.
   *   <li>Progressive container formats: Often {@link Format#NO_VALUE}, but may be populated with
   *       the peak bitrate of the container if known.
   *   <li>Sample formats: Often {@link Format#NO_VALUE}, but may be populated with the peak bitrate
   *       of the stream of samples with type {@link #sampleMimeType} if known. Note that if {@link
   *       #sampleMimeType} is a compressed format (e.g., {@link MimeTypes#AUDIO_AAC}), then this
   *       bitrate is for the stream of still compressed samples.
   * </ul>
   */
  @UnstableApi public final int peakBitrate;

  /**
   * The bitrate in bits per second. This is the peak bitrate if known, or else the average bitrate
   * if known, or else {@link Format#NO_VALUE}. Equivalent to: {@code peakBitrate != NO_VALUE ?
   * peakBitrate : averageBitrate}.
   */
  @UnstableApi public final int bitrate;

  /** Codecs of the format as described in RFC 6381, or null if unknown or not applicable. */
  @Nullable public final String codecs;

  /** Metadata, or null if unknown or not applicable. */
  @UnstableApi @Nullable public final Metadata metadata;

  /**
   * An extra opaque object that can be added to the {@link Format} to provide additional
   * information that can be passed through the player.
   *
   * <p>This value is not included in serialized {@link Bundle} instances of this class that are
   * used to transfer data to other processes.
   */
  @UnstableApi @Nullable public final Object customData;

  // Container specific.

  /** The MIME type of the container, or null if unknown or not applicable. */
  @Nullable public final String containerMimeType;

  // Sample specific.

  /** The sample MIME type, or null if unknown or not applicable. */
  @Nullable public final String sampleMimeType;

  /**
   * The maximum size of a buffer of data (typically one sample), or {@link #NO_VALUE} if unknown or
   * not applicable.
   */
  @UnstableApi public final int maxInputSize;

  /**
   * The maximum number of samples that must be stored to correctly re-order samples from decode
   * order to presentation order.
   */
  @UnstableApi public final int maxNumReorderSamples;

  /**
   * Initialization data that must be provided to the decoder. Will not be null, but may be empty if
   * initialization data is not required.
   */
  @UnstableApi public final List<byte[]> initializationData;

  /** DRM initialization data if the stream is protected, or null otherwise. */
  @UnstableApi @Nullable public final DrmInitData drmInitData;

  /**
   * For samples that contain subsamples, this is an offset that should be added to subsample
   * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
   * relative to the timestamps of their parent samples.
   */
  @UnstableApi public final long subsampleOffsetUs;

  /**
   * Indicates whether the stream contains preroll samples.
   *
   * <p>When this field is set to {@code true}, it means that the stream includes decode-only
   * samples that occur before the intended playback start position. These samples are necessary for
   * decoding but are not meant to be rendered and should be skipped after decoding.
   */
  @UnstableApi public final boolean hasPrerollSamples;

  // Video specific.

  /** The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable. */
  public final int width;

  /** The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable. */
  public final int height;

  /** The frame rate in frames per second, or {@link #NO_VALUE} if unknown or not applicable. */
  public final float frameRate;

  /**
   * The clockwise rotation that should be applied to the video for it to be rendered in the correct
   * orientation, or 0 if unknown or not applicable. Only 0, 90, 180 and 270 are supported.
   */
  @UnstableApi public final int rotationDegrees;

  /** The width to height ratio of pixels in the video, or 1.0 if unknown or not applicable. */
  public final float pixelWidthHeightRatio;

  /** The projection data for 360/VR video, or null if not applicable. */
  @UnstableApi @Nullable public final byte[] projectionData;

  /**
   * The stereo layout for 360/3D/VR video, or {@link #NO_VALUE} if not applicable. Valid stereo
   * modes are {@link C#STEREO_MODE_MONO}, {@link C#STEREO_MODE_TOP_BOTTOM}, {@link
   * C#STEREO_MODE_LEFT_RIGHT}, {@link C#STEREO_MODE_STEREO_MESH}.
   */
  @UnstableApi public final @C.StereoMode int stereoMode;

  /** The color metadata associated with the video, or null if not applicable. */
  @UnstableApi @Nullable public final ColorInfo colorInfo;

  /**
   * The maximum number of temporal scalable sub-layers in the video bitstream, or {@link #NO_VALUE}
   * if not applicable.
   */
  @UnstableApi public final int maxSubLayers;

  // Audio specific.

  /** The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable. */
  public final int channelCount;

  /** The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable. */
  public final int sampleRate;

  /**
   * The {@link C.PcmEncoding} for PCM or losslessly compressed audio. Set to {@link #NO_VALUE} for
   * other media types.
   */
  @UnstableApi public final @C.PcmEncoding int pcmEncoding;

  /**
   * The number of frames to trim from the start of the decoded audio stream, or 0 if not
   * applicable.
   */
  @UnstableApi public final int encoderDelay;

  /**
   * The number of frames to trim from the end of the decoded audio stream, or 0 if not applicable.
   */
  @UnstableApi public final int encoderPadding;

  // Text specific.

  /** The Accessibility channel, or {@link #NO_VALUE} if not known or applicable. */
  @UnstableApi public final int accessibilityChannel;

  /**
   * The replacement behavior that should be followed when handling consecutive samples in a
   * {@linkplain C#TRACK_TYPE_TEXT text track} of type {@link MimeTypes#APPLICATION_MEDIA3_CUES}.
   */
  @UnstableApi public final @CueReplacementBehavior int cueReplacementBehavior;

  // Image specific.

  /**
   * The number of horizontal tiles in an image, or {@link #NO_VALUE} if not known or applicable.
   */
  @UnstableApi public final int tileCountHorizontal;

  /** The number of vertical tiles in an image, or {@link #NO_VALUE} if not known or applicable. */
  @UnstableApi public final int tileCountVertical;

  // Provided by source.

  /**
   * The type of crypto that must be used to decode samples associated with this format, or {@link
   * C#CRYPTO_TYPE_NONE} if the content is not encrypted. Cannot be {@link C#CRYPTO_TYPE_NONE} if
   * {@link #drmInitData} is non-null, but may be {@link C#CRYPTO_TYPE_UNSUPPORTED} to indicate that
   * the samples are encrypted using an unsupported crypto type.
   */
  @UnstableApi public final @C.CryptoType int cryptoType;

  // Lazily initialized hashcode.
  private int hashCode;

  private static boolean isLabelPartOfLabels(Builder builder) {
    if (builder.labels.isEmpty() && builder.label == null) {
      return true;
    }
    for (int i = 0; i < builder.labels.size(); i++) {
      if (builder.labels.get(i).value.equals(builder.label)) {
        return true;
      }
    }
    return false;
  }

  private Format(Builder builder) {
    id = builder.id;
    language = Util.normalizeLanguageCode(builder.language);
    if (builder.labels.isEmpty() && builder.label != null) {
      labels = ImmutableList.of(new Label(language, builder.label));
      label = builder.label;
    } else if (!builder.labels.isEmpty() && builder.label == null) {
      labels = builder.labels;
      label = getDefaultLabel(builder.labels, language);
    } else {
      checkState(isLabelPartOfLabels(builder));
      labels = builder.labels;
      label = builder.label;
    }
    selectionFlags = builder.selectionFlags;

    checkState(
        builder.auxiliaryTrackType == C.AUXILIARY_TRACK_TYPE_UNDEFINED
            || (builder.roleFlags & C.ROLE_FLAG_AUXILIARY) != 0,
        "Auxiliary track type must only be set to a value other than AUXILIARY_TRACK_TYPE_UNDEFINED"
            + " only when ROLE_FLAG_AUXILIARY is set");
    roleFlags = builder.roleFlags;
    auxiliaryTrackType = builder.auxiliaryTrackType;
    averageBitrate = builder.averageBitrate;
    peakBitrate = builder.peakBitrate;
    bitrate = peakBitrate != NO_VALUE ? peakBitrate : averageBitrate;
    codecs = builder.codecs;
    metadata = builder.metadata;
    customData = builder.customData;
    // Container specific.
    containerMimeType = builder.containerMimeType;
    // Sample specific.
    sampleMimeType = builder.sampleMimeType;
    maxInputSize = builder.maxInputSize;
    maxNumReorderSamples = builder.maxNumReorderSamples;
    initializationData =
        builder.initializationData == null ? Collections.emptyList() : builder.initializationData;
    drmInitData = builder.drmInitData;
    subsampleOffsetUs = builder.subsampleOffsetUs;
    hasPrerollSamples = builder.hasPrerollSamples;
    // Video specific.
    width = builder.width;
    height = builder.height;
    frameRate = builder.frameRate;
    rotationDegrees = builder.rotationDegrees == NO_VALUE ? 0 : builder.rotationDegrees;
    pixelWidthHeightRatio =
        builder.pixelWidthHeightRatio == NO_VALUE ? 1 : builder.pixelWidthHeightRatio;
    projectionData = builder.projectionData;
    stereoMode = builder.stereoMode;
    colorInfo = builder.colorInfo;
    maxSubLayers = builder.maxSubLayers;
    // Audio specific.
    channelCount = builder.channelCount;
    sampleRate = builder.sampleRate;
    pcmEncoding = builder.pcmEncoding;
    encoderDelay = builder.encoderDelay == NO_VALUE ? 0 : builder.encoderDelay;
    encoderPadding = builder.encoderPadding == NO_VALUE ? 0 : builder.encoderPadding;
    // Text specific.
    accessibilityChannel = builder.accessibilityChannel;
    cueReplacementBehavior = builder.cueReplacementBehavior;
    // Image specific.
    tileCountHorizontal = builder.tileCountHorizontal;
    tileCountVertical = builder.tileCountVertical;
    // Provided by source.
    if (builder.cryptoType == C.CRYPTO_TYPE_NONE && drmInitData != null) {
      // Encrypted content cannot use CRYPTO_TYPE_NONE.
      cryptoType = C.CRYPTO_TYPE_UNSUPPORTED;
    } else {
      cryptoType = builder.cryptoType;
    }
  }

  /** Returns a {@link Format.Builder} initialized with the values of this instance. */
  @UnstableApi
  public Builder buildUpon() {
    return new Builder(this);
  }

  @UnstableApi
  @SuppressWarnings("ReferenceEquality")
  public Format withManifestFormatInfo(Format manifestFormat) {
    if (this == manifestFormat) {
      // No need to copy from ourselves.
      return this;
    }

    @C.TrackType int trackType = MimeTypes.getTrackType(sampleMimeType);

    // Use manifest value only.
    @Nullable String id = manifestFormat.id;
    int tileCountHorizontal = manifestFormat.tileCountHorizontal;
    int tileCountVertical = manifestFormat.tileCountVertical;

    // Prefer manifest values, but fill in from sample format if missing.
    @Nullable String label = manifestFormat.label != null ? manifestFormat.label : this.label;
    List<Label> labels = !manifestFormat.labels.isEmpty() ? manifestFormat.labels : this.labels;
    @Nullable String language = this.language;
    if ((trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO)
        && manifestFormat.language != null) {
      language = manifestFormat.language;
    }

    // Prefer sample format values, but fill in from manifest if missing.
    int averageBitrate =
        this.averageBitrate == NO_VALUE ? manifestFormat.averageBitrate : this.averageBitrate;
    int peakBitrate = this.peakBitrate == NO_VALUE ? manifestFormat.peakBitrate : this.peakBitrate;
    @Nullable String codecs = this.codecs;
    if (codecs == null) {
      // The manifest format may be muxed, so filter only codecs of this format's type. If we still
      // have more than one codec then we're unable to uniquely identify which codec to fill in.
      @Nullable String codecsOfType = Util.getCodecsOfType(manifestFormat.codecs, trackType);
      if (Util.splitCodecs(codecsOfType).length == 1) {
        codecs = codecsOfType;
      }
    }

    @Nullable
    Metadata metadata =
        this.metadata == null
            ? manifestFormat.metadata
            : this.metadata.copyWithAppendedEntriesFrom(manifestFormat.metadata);

    float frameRate = this.frameRate;
    if (frameRate == NO_VALUE && trackType == C.TRACK_TYPE_VIDEO) {
      frameRate = manifestFormat.frameRate;
    }

    // Merge manifest and sample format values.
    @C.SelectionFlags int selectionFlags = this.selectionFlags | manifestFormat.selectionFlags;
    @C.RoleFlags int roleFlags = this.roleFlags | manifestFormat.roleFlags;
    @Nullable
    DrmInitData drmInitData =
        DrmInitData.createSessionCreationData(manifestFormat.drmInitData, this.drmInitData);

    return buildUpon()
        .setId(id)
        .setLabel(label)
        .setLabels(labels)
        .setLanguage(language)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setAverageBitrate(averageBitrate)
        .setPeakBitrate(peakBitrate)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setDrmInitData(drmInitData)
        .setFrameRate(frameRate)
        .setTileCountHorizontal(tileCountHorizontal)
        .setTileCountVertical(tileCountVertical)
        .build();
  }

  /** Returns a copy of this format with the specified {@link #cryptoType}. */
  @UnstableApi
  public Format copyWithCryptoType(@C.CryptoType int cryptoType) {
    return buildUpon().setCryptoType(cryptoType).build();
  }

  /**
   * Returns the number of pixels if this is a video format whose {@link #width} and {@link #height}
   * are known, or {@link #NO_VALUE} otherwise
   */
  @UnstableApi
  public int getPixelCount() {
    return width == NO_VALUE || height == NO_VALUE ? NO_VALUE : (width * height);
  }

  @Override
  public String toString() {
    return "Format("
        + id
        + ", "
        + label
        + ", "
        + containerMimeType
        + ", "
        + sampleMimeType
        + ", "
        + codecs
        + ", "
        + bitrate
        + ", "
        + language
        + ", ["
        + width
        + ", "
        + height
        + ", "
        + frameRate
        + ", "
        + colorInfo
        + "]"
        + ", ["
        + channelCount
        + ", "
        + sampleRate
        + "])";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      // Some fields for which hashing is expensive are deliberately omitted.
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (label == null ? 0 : label.hashCode());
      result = 31 * result + labels.hashCode();
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + selectionFlags;
      result = 31 * result + roleFlags;
      result = 31 * result + auxiliaryTrackType;
      result = 31 * result + averageBitrate;
      result = 31 * result + peakBitrate;
      result = 31 * result + (codecs == null ? 0 : codecs.hashCode());
      result = 31 * result + (metadata == null ? 0 : metadata.hashCode());
      result = 31 * result + (customData == null ? 0 : customData.hashCode());
      // Container specific.
      result = 31 * result + (containerMimeType == null ? 0 : containerMimeType.hashCode());
      // Sample specific.
      result = 31 * result + (sampleMimeType == null ? 0 : sampleMimeType.hashCode());
      result = 31 * result + maxInputSize;
      // [Omitted] initializationData.
      // [Omitted] drmInitData.
      result = 31 * result + (int) subsampleOffsetUs;
      // Video specific.
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + Float.floatToIntBits(frameRate);
      result = 31 * result + rotationDegrees;
      result = 31 * result + Float.floatToIntBits(pixelWidthHeightRatio);
      // [Omitted] projectionData.
      result = 31 * result + stereoMode;
      // [Omitted] colorInfo.
      result = 31 * result + maxSubLayers;
      // Audio specific.
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + pcmEncoding;
      result = 31 * result + encoderDelay;
      result = 31 * result + encoderPadding;
      // Text specific.
      result = 31 * result + accessibilityChannel;
      // Image specific.
      result = 31 * result + tileCountHorizontal;
      result = 31 * result + tileCountVertical;
      // Provided by the source.
      result = 31 * result + cryptoType;
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    if (hashCode != 0 && other.hashCode != 0 && hashCode != other.hashCode) {
      return false;
    }
    // Field equality checks ordered by type, with the cheapest checks first.
    return selectionFlags == other.selectionFlags
        && roleFlags == other.roleFlags
        && auxiliaryTrackType == other.auxiliaryTrackType
        && averageBitrate == other.averageBitrate
        && peakBitrate == other.peakBitrate
        && maxInputSize == other.maxInputSize
        && subsampleOffsetUs == other.subsampleOffsetUs
        && width == other.width
        && height == other.height
        && rotationDegrees == other.rotationDegrees
        && stereoMode == other.stereoMode
        && maxSubLayers == other.maxSubLayers
        && channelCount == other.channelCount
        && sampleRate == other.sampleRate
        && pcmEncoding == other.pcmEncoding
        && encoderDelay == other.encoderDelay
        && encoderPadding == other.encoderPadding
        && accessibilityChannel == other.accessibilityChannel
        && tileCountHorizontal == other.tileCountHorizontal
        && tileCountVertical == other.tileCountVertical
        && cryptoType == other.cryptoType
        && Float.compare(frameRate, other.frameRate) == 0
        && Float.compare(pixelWidthHeightRatio, other.pixelWidthHeightRatio) == 0
        && Objects.equals(id, other.id)
        && Objects.equals(label, other.label)
        && labels.equals(other.labels)
        && Objects.equals(codecs, other.codecs)
        && Objects.equals(containerMimeType, other.containerMimeType)
        && Objects.equals(sampleMimeType, other.sampleMimeType)
        && Objects.equals(language, other.language)
        && Arrays.equals(projectionData, other.projectionData)
        && Objects.equals(metadata, other.metadata)
        && Objects.equals(colorInfo, other.colorInfo)
        && Objects.equals(drmInitData, other.drmInitData)
        && initializationDataEquals(other)
        && Objects.equals(customData, other.customData);
  }

  /**
   * Returns whether the {@link #initializationData}s belonging to this format and {@code other} are
   * equal.
   *
   * @param other The other format whose {@link #initializationData} is being compared.
   * @return Whether the {@link #initializationData}s belonging to this format and {@code other} are
   *     equal.
   */
  @UnstableApi
  public boolean initializationDataEquals(Format other) {
    if (initializationData.size() != other.initializationData.size()) {
      return false;
    }
    for (int i = 0; i < initializationData.size(); i++) {
      if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
        return false;
      }
    }
    return true;
  }

  // Utility methods

  /** Returns a prettier {@link String} than {@link #toString()}, intended for logging. */
  @UnstableApi
  public static String toLogString(@Nullable Format format) {
    if (format == null) {
      return "null";
    }
    Joiner commaJoiner = Joiner.on(',');
    StringBuilder builder = new StringBuilder();
    builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType);
    if (format.containerMimeType != null) {
      builder.append(", container=").append(format.containerMimeType);
    }
    if (format.bitrate != NO_VALUE) {
      builder.append(", bitrate=").append(format.bitrate);
    }
    if (format.codecs != null) {
      builder.append(", codecs=").append(format.codecs);
    }
    if (format.drmInitData != null) {
      Set<String> schemes = new LinkedHashSet<>();
      for (int i = 0; i < format.drmInitData.schemeDataCount; i++) {
        UUID schemeUuid = format.drmInitData.get(i).uuid;
        if (schemeUuid.equals(C.COMMON_PSSH_UUID)) {
          schemes.add("cenc");
        } else if (schemeUuid.equals(C.CLEARKEY_UUID)) {
          schemes.add("clearkey");
        } else if (schemeUuid.equals(C.PLAYREADY_UUID)) {
          schemes.add("playready");
        } else if (schemeUuid.equals(C.WIDEVINE_UUID)) {
          schemes.add("widevine");
        } else if (schemeUuid.equals(C.UUID_NIL)) {
          schemes.add("universal");
        } else {
          schemes.add("unknown (" + schemeUuid + ")");
        }
      }
      builder.append(", drm=[");
      commaJoiner.appendTo(builder, schemes);
      builder.append(']');
    }
    if (format.width != NO_VALUE && format.height != NO_VALUE) {
      builder.append(", res=").append(format.width).append("x").append(format.height);
    }
    if (!fuzzyEquals(format.pixelWidthHeightRatio, 1, 0.001)) {
      builder.append(", par=").append(Util.formatInvariant("%.3f", format.pixelWidthHeightRatio));
    }
    if (format.colorInfo != null && format.colorInfo.isValid()) {
      builder.append(", color=").append(format.colorInfo.toLogString());
    }
    if (format.frameRate != NO_VALUE) {
      builder.append(", fps=").append(format.frameRate);
    }
    if (format.maxSubLayers != NO_VALUE) {
      builder.append(", maxSubLayers=").append(format.maxSubLayers);
    }
    if (format.channelCount != NO_VALUE) {
      builder.append(", channels=").append(format.channelCount);
    }
    if (format.sampleRate != NO_VALUE) {
      builder.append(", sample_rate=").append(format.sampleRate);
    }
    if (format.language != null) {
      builder.append(", language=").append(format.language);
    }
    if (!format.labels.isEmpty()) {
      builder.append(", labels=[");
      commaJoiner.appendTo(
          builder, Lists.transform(format.labels, l -> l.language + ": " + l.value));
      builder.append("]");
    }
    if (format.selectionFlags != 0) {
      builder.append(", selectionFlags=[");
      commaJoiner.appendTo(builder, Util.getSelectionFlagStrings(format.selectionFlags));
      builder.append("]");
    }
    if (format.roleFlags != 0) {
      builder.append(", roleFlags=[");
      commaJoiner.appendTo(builder, Util.getRoleFlagStrings(format.roleFlags));
      builder.append("]");
    }
    if (format.customData != null) {
      builder.append(", customData=").append(format.customData);
    }
    if ((format.roleFlags & C.ROLE_FLAG_AUXILIARY) != 0) {
      builder
          .append(", auxiliaryTrackType=")
          .append(Util.getAuxiliaryTrackTypeString(format.auxiliaryTrackType));
    }
    return builder.toString();
  }

  private static final String FIELD_ID = Util.intToStringMaxRadix(0);
  private static final String FIELD_LABEL = Util.intToStringMaxRadix(1);
  private static final String FIELD_LANGUAGE = Util.intToStringMaxRadix(2);
  private static final String FIELD_SELECTION_FLAGS = Util.intToStringMaxRadix(3);
  private static final String FIELD_ROLE_FLAGS = Util.intToStringMaxRadix(4);
  private static final String FIELD_AVERAGE_BITRATE = Util.intToStringMaxRadix(5);
  private static final String FIELD_PEAK_BITRATE = Util.intToStringMaxRadix(6);
  private static final String FIELD_CODECS = Util.intToStringMaxRadix(7);
  // Do not reuse this key.
  private static final String UNUSED_FIELD_METADATA = Util.intToStringMaxRadix(8);
  private static final String FIELD_CONTAINER_MIME_TYPE = Util.intToStringMaxRadix(9);
  private static final String FIELD_SAMPLE_MIME_TYPE = Util.intToStringMaxRadix(10);
  private static final String FIELD_MAX_INPUT_SIZE = Util.intToStringMaxRadix(11);
  private static final String FIELD_INITIALIZATION_DATA = Util.intToStringMaxRadix(12);
  private static final String FIELD_DRM_INIT_DATA = Util.intToStringMaxRadix(13);
  private static final String FIELD_SUBSAMPLE_OFFSET_US = Util.intToStringMaxRadix(14);
  private static final String FIELD_WIDTH = Util.intToStringMaxRadix(15);
  private static final String FIELD_HEIGHT = Util.intToStringMaxRadix(16);
  private static final String FIELD_FRAME_RATE = Util.intToStringMaxRadix(17);
  private static final String FIELD_ROTATION_DEGREES = Util.intToStringMaxRadix(18);
  private static final String FIELD_PIXEL_WIDTH_HEIGHT_RATIO = Util.intToStringMaxRadix(19);
  private static final String FIELD_PROJECTION_DATA = Util.intToStringMaxRadix(20);
  private static final String FIELD_STEREO_MODE = Util.intToStringMaxRadix(21);
  private static final String FIELD_COLOR_INFO = Util.intToStringMaxRadix(22);
  private static final String FIELD_CHANNEL_COUNT = Util.intToStringMaxRadix(23);
  private static final String FIELD_SAMPLE_RATE = Util.intToStringMaxRadix(24);
  private static final String FIELD_PCM_ENCODING = Util.intToStringMaxRadix(25);
  private static final String FIELD_ENCODER_DELAY = Util.intToStringMaxRadix(26);
  private static final String FIELD_ENCODER_PADDING = Util.intToStringMaxRadix(27);
  private static final String FIELD_ACCESSIBILITY_CHANNEL = Util.intToStringMaxRadix(28);
  private static final String FIELD_CRYPTO_TYPE = Util.intToStringMaxRadix(29);
  private static final String FIELD_TILE_COUNT_HORIZONTAL = Util.intToStringMaxRadix(30);
  private static final String FIELD_TILE_COUNT_VERTICAL = Util.intToStringMaxRadix(31);
  private static final String FIELD_LABELS = Util.intToStringMaxRadix(32);
  private static final String FIELD_AUXILIARY_TRACK_TYPE = Util.intToStringMaxRadix(33);
  private static final String FIELD_MAX_SUB_LAYERS = Util.intToStringMaxRadix(34);

  /**
   * Returns a {@link Bundle} representing the information stored in this object. If {@code
   * excludeMetadata} is true, {@linkplain Format#metadata metadata} is excluded.
   */
  @UnstableApi
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putString(FIELD_ID, id);
    bundle.putString(FIELD_LABEL, label);
    bundle.putParcelableArrayList(
        FIELD_LABELS, BundleCollectionUtil.toBundleArrayList(labels, Label::toBundle));
    bundle.putString(FIELD_LANGUAGE, language);
    bundle.putInt(FIELD_SELECTION_FLAGS, selectionFlags);
    bundle.putInt(FIELD_ROLE_FLAGS, roleFlags);
    if (auxiliaryTrackType != DEFAULT.auxiliaryTrackType) {
      bundle.putInt(FIELD_AUXILIARY_TRACK_TYPE, auxiliaryTrackType);
    }
    bundle.putInt(FIELD_AVERAGE_BITRATE, averageBitrate);
    bundle.putInt(FIELD_PEAK_BITRATE, peakBitrate);
    bundle.putString(FIELD_CODECS, codecs);
    // The metadata does not implement toBundle() method, hence can not be added.
    // Container specific.
    bundle.putString(FIELD_CONTAINER_MIME_TYPE, containerMimeType);
    // Sample specific.
    bundle.putString(FIELD_SAMPLE_MIME_TYPE, sampleMimeType);
    bundle.putInt(FIELD_MAX_INPUT_SIZE, maxInputSize);
    for (int i = 0; i < initializationData.size(); i++) {
      bundle.putByteArray(keyForInitializationData(i), initializationData.get(i));
    }
    // DrmInitData doesn't need to be put into Bundle as it's only used in the playing process to
    // initialize the decoder.
    bundle.putParcelable(FIELD_DRM_INIT_DATA, drmInitData);
    bundle.putLong(FIELD_SUBSAMPLE_OFFSET_US, subsampleOffsetUs);
    // Video specific.
    bundle.putInt(FIELD_WIDTH, width);
    bundle.putInt(FIELD_HEIGHT, height);
    bundle.putFloat(FIELD_FRAME_RATE, frameRate);
    bundle.putInt(FIELD_ROTATION_DEGREES, rotationDegrees);
    bundle.putFloat(FIELD_PIXEL_WIDTH_HEIGHT_RATIO, pixelWidthHeightRatio);
    bundle.putByteArray(FIELD_PROJECTION_DATA, projectionData);
    bundle.putInt(FIELD_STEREO_MODE, stereoMode);
    if (colorInfo != null) {
      bundle.putBundle(FIELD_COLOR_INFO, colorInfo.toBundle());
    }
    bundle.putInt(FIELD_MAX_SUB_LAYERS, maxSubLayers);
    // Audio specific.
    bundle.putInt(FIELD_CHANNEL_COUNT, channelCount);
    bundle.putInt(FIELD_SAMPLE_RATE, sampleRate);
    bundle.putInt(FIELD_PCM_ENCODING, pcmEncoding);
    bundle.putInt(FIELD_ENCODER_DELAY, encoderDelay);
    bundle.putInt(FIELD_ENCODER_PADDING, encoderPadding);
    // Text specific.
    bundle.putInt(FIELD_ACCESSIBILITY_CHANNEL, accessibilityChannel);
    // Image specific.
    bundle.putInt(FIELD_TILE_COUNT_HORIZONTAL, tileCountHorizontal);
    bundle.putInt(FIELD_TILE_COUNT_VERTICAL, tileCountVertical);
    // Source specific.
    bundle.putInt(FIELD_CRYPTO_TYPE, cryptoType);
    return bundle;
  }

  /** Restores a {@code Format} from a {@link Bundle}. */
  @UnstableApi
  public static Format fromBundle(Bundle bundle) {
    Builder builder = new Builder();
    BundleCollectionUtil.ensureClassLoader(bundle);
    builder
        .setId(defaultIfNull(bundle.getString(FIELD_ID), DEFAULT.id))
        .setLabel(defaultIfNull(bundle.getString(FIELD_LABEL), DEFAULT.label));
    @Nullable List<Bundle> labelsBundles = bundle.getParcelableArrayList(FIELD_LABELS);
    List<Label> labels =
        labelsBundles == null
            ? ImmutableList.of()
            : BundleCollectionUtil.fromBundleList(Label::fromBundle, labelsBundles);
    builder
        .setLabels(labels)
        .setLanguage(defaultIfNull(bundle.getString(FIELD_LANGUAGE), DEFAULT.language))
        .setSelectionFlags(bundle.getInt(FIELD_SELECTION_FLAGS, DEFAULT.selectionFlags))
        .setRoleFlags(bundle.getInt(FIELD_ROLE_FLAGS, DEFAULT.roleFlags))
        .setAuxiliaryTrackType(
            bundle.getInt(FIELD_AUXILIARY_TRACK_TYPE, DEFAULT.auxiliaryTrackType))
        .setAverageBitrate(bundle.getInt(FIELD_AVERAGE_BITRATE, DEFAULT.averageBitrate))
        .setPeakBitrate(bundle.getInt(FIELD_PEAK_BITRATE, DEFAULT.peakBitrate))
        .setCodecs(defaultIfNull(bundle.getString(FIELD_CODECS), DEFAULT.codecs))
        // Container specific.
        .setContainerMimeType(
            defaultIfNull(bundle.getString(FIELD_CONTAINER_MIME_TYPE), DEFAULT.containerMimeType))
        // Sample specific.
        .setSampleMimeType(
            defaultIfNull(bundle.getString(FIELD_SAMPLE_MIME_TYPE), DEFAULT.sampleMimeType))
        .setMaxInputSize(bundle.getInt(FIELD_MAX_INPUT_SIZE, DEFAULT.maxInputSize));

    List<byte[]> initializationData = new ArrayList<>();
    for (int i = 0; ; i++) {
      @Nullable byte[] data = bundle.getByteArray(keyForInitializationData(i));
      if (data == null) {
        break;
      }
      initializationData.add(data);
    }
    builder
        .setInitializationData(initializationData)
        .setDrmInitData(bundle.getParcelable(FIELD_DRM_INIT_DATA))
        .setSubsampleOffsetUs(bundle.getLong(FIELD_SUBSAMPLE_OFFSET_US, DEFAULT.subsampleOffsetUs))
        // Video specific.
        .setWidth(bundle.getInt(FIELD_WIDTH, DEFAULT.width))
        .setHeight(bundle.getInt(FIELD_HEIGHT, DEFAULT.height))
        .setFrameRate(bundle.getFloat(FIELD_FRAME_RATE, DEFAULT.frameRate))
        .setRotationDegrees(bundle.getInt(FIELD_ROTATION_DEGREES, DEFAULT.rotationDegrees))
        .setPixelWidthHeightRatio(
            bundle.getFloat(FIELD_PIXEL_WIDTH_HEIGHT_RATIO, DEFAULT.pixelWidthHeightRatio))
        .setProjectionData(bundle.getByteArray(FIELD_PROJECTION_DATA))
        .setStereoMode(bundle.getInt(FIELD_STEREO_MODE, DEFAULT.stereoMode))
        .setMaxSubLayers(bundle.getInt(FIELD_MAX_SUB_LAYERS, DEFAULT.maxSubLayers));
    Bundle colorInfoBundle = bundle.getBundle(FIELD_COLOR_INFO);
    if (colorInfoBundle != null) {
      builder.setColorInfo(ColorInfo.fromBundle(colorInfoBundle));
    }
    // Audio specific.
    builder
        .setChannelCount(bundle.getInt(FIELD_CHANNEL_COUNT, DEFAULT.channelCount))
        .setSampleRate(bundle.getInt(FIELD_SAMPLE_RATE, DEFAULT.sampleRate))
        .setPcmEncoding(bundle.getInt(FIELD_PCM_ENCODING, DEFAULT.pcmEncoding))
        .setEncoderDelay(bundle.getInt(FIELD_ENCODER_DELAY, DEFAULT.encoderDelay))
        .setEncoderPadding(bundle.getInt(FIELD_ENCODER_PADDING, DEFAULT.encoderPadding))
        // Text specific.
        .setAccessibilityChannel(
            bundle.getInt(FIELD_ACCESSIBILITY_CHANNEL, DEFAULT.accessibilityChannel))
        // Image specific.
        .setTileCountHorizontal(
            bundle.getInt(FIELD_TILE_COUNT_HORIZONTAL, DEFAULT.tileCountHorizontal))
        .setTileCountVertical(bundle.getInt(FIELD_TILE_COUNT_VERTICAL, DEFAULT.tileCountVertical))
        // Source specific.
        .setCryptoType(bundle.getInt(FIELD_CRYPTO_TYPE, DEFAULT.cryptoType));

    return builder.build();
  }

  private static String keyForInitializationData(int initialisationDataIndex) {
    return FIELD_INITIALIZATION_DATA
        + "_"
        + Integer.toString(initialisationDataIndex, Character.MAX_RADIX);
  }

  /**
   * Utility method to get {@code defaultValue} if {@code value} is {@code null}. {@code
   * defaultValue} can be {@code null}.
   *
   * <p>Note: Current implementations of getters in {@link Bundle}, for example {@link
   * Bundle#getString(String, String)} does not allow the defaultValue to be {@code null}, hence the
   * need for this method.
   */
  @Nullable
  private static <T> T defaultIfNull(@Nullable T value, @Nullable T defaultValue) {
    return value != null ? value : defaultValue;
  }

  private static String getDefaultLabel(List<Label> labels, @Nullable String language) {
    for (Label l : labels) {
      if (TextUtils.equals(l.language, language)) {
        return l.value;
      }
    }
    return labels.get(0).value;
  }
}
