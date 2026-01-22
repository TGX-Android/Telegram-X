/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import java.io.IOException;

/** An overridable {@link Renderer} implementation forwarding all methods to another renderer. */
@UnstableApi
public class ForwardingRenderer implements Renderer {

  private final Renderer renderer;

  /** Creates a new instance that forwards all operations to {@code renderer}. */
  public ForwardingRenderer(Renderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void handleMessage(@MessageType int messageType, @Nullable Object message)
      throws ExoPlaybackException {
    renderer.handleMessage(messageType, message);
  }

  @Override
  public String getName() {
    return renderer.getName();
  }

  @Override
  public @C.TrackType int getTrackType() {
    return renderer.getTrackType();
  }

  @Override
  public RendererCapabilities getCapabilities() {
    return renderer.getCapabilities();
  }

  @Override
  public void init(int index, PlayerId playerId, Clock clock) {
    renderer.init(index, playerId, clock);
  }

  @Nullable
  @Override
  public MediaClock getMediaClock() {
    return renderer.getMediaClock();
  }

  @Override
  public @State int getState() {
    return renderer.getState();
  }

  @Override
  public void enable(
      RendererConfiguration configuration,
      Format[] formats,
      SampleStream stream,
      long positionUs,
      boolean joining,
      boolean mayRenderStartOfStream,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    renderer.enable(
        configuration,
        formats,
        stream,
        positionUs,
        joining,
        mayRenderStartOfStream,
        startPositionUs,
        offsetUs,
        mediaPeriodId);
  }

  @Override
  public void start() throws ExoPlaybackException {
    renderer.start();
  }

  @Override
  public void replaceStream(
      Format[] formats,
      SampleStream stream,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId)
      throws ExoPlaybackException {
    renderer.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId);
  }

  @Nullable
  @Override
  public SampleStream getStream() {
    return renderer.getStream();
  }

  @Override
  public boolean hasReadStreamToEnd() {
    return renderer.hasReadStreamToEnd();
  }

  @Override
  public long getReadingPositionUs() {
    return renderer.getReadingPositionUs();
  }

  @Override
  public long getDurationToProgressUs(long positionUs, long elapsedRealtimeUs) {
    return renderer.getDurationToProgressUs(positionUs, elapsedRealtimeUs);
  }

  @Override
  public void setCurrentStreamFinal() {
    renderer.setCurrentStreamFinal();
  }

  @Override
  public boolean isCurrentStreamFinal() {
    return renderer.isCurrentStreamFinal();
  }

  @Override
  public void maybeThrowStreamError() throws IOException {
    renderer.maybeThrowStreamError();
  }

  @Override
  public void resetPosition(long positionUs) throws ExoPlaybackException {
    renderer.resetPosition(positionUs);
  }

  @Override
  public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {
    renderer.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed);
  }

  @Override
  public void enableMayRenderStartOfStream() {
    renderer.enableMayRenderStartOfStream();
  }

  @Override
  public void setTimeline(Timeline timeline) {
    renderer.setTimeline(timeline);
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    renderer.render(positionUs, elapsedRealtimeUs);
  }

  @Override
  public boolean isReady() {
    return renderer.isReady();
  }

  @Override
  public boolean isEnded() {
    return renderer.isEnded();
  }

  @Override
  public void stop() {
    renderer.stop();
  }

  @Override
  public void disable() {
    renderer.disable();
  }

  @Override
  public void reset() {
    renderer.reset();
  }

  @Override
  public void release() {
    renderer.release();
  }
}
