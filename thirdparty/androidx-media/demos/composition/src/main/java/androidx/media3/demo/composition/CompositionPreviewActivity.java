/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.demo.composition;

import static android.content.pm.ActivityInfo.COLOR_MODE_HDR;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.effect.LanczosResample;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.CompositionPlayer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.InAppFragmentedMp4Muxer;
import androidx.media3.transformer.InAppMp4Muxer;
import androidx.media3.transformer.JsonUtil;
import androidx.media3.transformer.Transformer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An {@link Activity} that previews compositions, using {@link
 * androidx.media3.transformer.CompositionPlayer}.
 */
public final class CompositionPreviewActivity extends AppCompatActivity {
  private static final String TAG = "CompPreviewActivity";
  private static final String AUDIO_URI =
      "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3";
  private static final String SAME_AS_INPUT_OPTION = "same as input";
  private static final ImmutableMap<String, @Composition.HdrMode Integer> HDR_MODE_DESCRIPTIONS =
      new ImmutableMap.Builder<String, @Composition.HdrMode Integer>()
          .put("Keep HDR", HDR_MODE_KEEP_HDR)
          .put("MediaCodec tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
          .put("OpenGL tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
          .put("Force Interpret HDR as SDR", HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR)
          .build();
  private static final ImmutableList<String> RESOLUTION_HEIGHTS =
      ImmutableList.of(
          SAME_AS_INPUT_OPTION, "144", "240", "360", "480", "720", "1080", "1440", "2160");

  private ArrayList<String> sequenceAssetTitles;
  private boolean[] selectedMediaItems;
  private String[] presetDescriptions;
  private AssetItemAdapter assetItemAdapter;
  @Nullable private CompositionPlayer compositionPlayer;
  @Nullable private Transformer transformer;
  @Nullable private File outputFile;
  private PlayerView playerView;
  private AppCompatButton exportButton;
  private AppCompatTextView exportInformationTextView;
  private Stopwatch exportStopwatch;
  private boolean includeBackgroundAudioTrack;
  private boolean appliesVideoEffects;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (SDK_INT >= 26) {
      getWindow().setColorMode(COLOR_MODE_HDR);
    }
    setContentView(R.layout.composition_preview_activity);
    playerView = findViewById(R.id.composition_player_view);

    findViewById(R.id.preview_button).setOnClickListener(view -> previewComposition());
    findViewById(R.id.edit_sequence_button).setOnClickListener(view -> selectPreset());
    RecyclerView presetList = findViewById(R.id.composition_preset_list);
    presetList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    LinearLayoutManager layoutManager =
        new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, /* reverseLayout= */ false);
    presetList.setLayoutManager(layoutManager);

    exportInformationTextView = findViewById(R.id.export_information_text);
    exportButton = findViewById(R.id.composition_export_button);
    exportButton.setOnClickListener(view -> showExportSettings());

    AppCompatCheckBox backgroundAudioCheckBox = findViewById(R.id.background_audio_checkbox);
    backgroundAudioCheckBox.setOnCheckedChangeListener(
        (compoundButton, checked) -> includeBackgroundAudioTrack = checked);

    ArrayAdapter<String> resolutionHeightAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    resolutionHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner resolutionHeightSpinner = findViewById(R.id.resolution_height_spinner);
    resolutionHeightSpinner.setAdapter(resolutionHeightAdapter);
    resolutionHeightAdapter.addAll(RESOLUTION_HEIGHTS);

    ArrayAdapter<String> hdrModeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item);
    hdrModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner hdrModeSpinner = findViewById(R.id.hdr_mode_spinner);
    hdrModeSpinner.setAdapter(hdrModeAdapter);
    hdrModeAdapter.addAll(HDR_MODE_DESCRIPTIONS.keySet());

    AppCompatCheckBox applyVideoEffectsCheckBox = findViewById(R.id.apply_video_effects_checkbox);
    applyVideoEffectsCheckBox.setOnCheckedChangeListener(
        ((compoundButton, checked) -> appliesVideoEffects = checked));

    presetDescriptions = getResources().getStringArray(R.array.preset_descriptions);
    // Select two media items by default.
    selectedMediaItems = new boolean[presetDescriptions.length];
    selectedMediaItems[0] = true;
    selectedMediaItems[2] = true;
    sequenceAssetTitles = new ArrayList<>();
    for (int i = 0; i < selectedMediaItems.length; i++) {
      if (selectedMediaItems[i]) {
        sequenceAssetTitles.add(presetDescriptions[i]);
      }
    }
    assetItemAdapter = new AssetItemAdapter(sequenceAssetTitles);
    presetList.setAdapter(assetItemAdapter);

    exportStopwatch =
        Stopwatch.createUnstarted(
            new Ticker() {
              @Override
              public long read() {
                return android.os.SystemClock.elapsedRealtimeNanos();
              }
            });
  }

  @Override
  protected void onStart() {
    super.onStart();
    playerView.onResume();
  }

  @Override
  protected void onStop() {
    super.onStop();
    playerView.onPause();
    releasePlayer();
    cancelExport();
    exportStopwatch.reset();
  }

  @SuppressWarnings("MissingSuperCall")
  @Override
  public void onBackPressed() {
    if (compositionPlayer != null) {
      compositionPlayer.pause();
    }
    if (exportStopwatch.isRunning()) {
      cancelExport();
      exportStopwatch.reset();
    }
  }

  private Composition prepareComposition() {
    String[] presetUris = getResources().getStringArray(/* id= */ R.array.preset_uris);
    int[] presetDurationsUs = getResources().getIntArray(/* id= */ R.array.preset_durations);
    List<EditedMediaItem> mediaItems = new ArrayList<>();
    ImmutableList.Builder<Effect> videoEffectsBuilder = new ImmutableList.Builder<>();
    if (appliesVideoEffects) {
      videoEffectsBuilder.add(MatrixTransformationFactory.createDizzyCropEffect());
      videoEffectsBuilder.add(RgbFilter.createGrayscaleFilter());
    }
    Spinner resolutionHeightSpinner = findViewById(R.id.resolution_height_spinner);
    String selectedResolutionHeight = String.valueOf(resolutionHeightSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedResolutionHeight)) {
      int resolutionHeight = Integer.parseInt(selectedResolutionHeight);
      videoEffectsBuilder.add(LanczosResample.scaleToFit(10000, resolutionHeight));
      videoEffectsBuilder.add(Presentation.createForHeight(resolutionHeight));
    }
    ImmutableList<Effect> videoEffects = videoEffectsBuilder.build();
    // Preview requires all sequences to be the same duration, so calculate main sequence duration
    // and limit background sequence duration to match.
    long videoSequenceDurationUs = 0;
    for (int i = 0; i < selectedMediaItems.length; i++) {
      if (selectedMediaItems[i]) {
        SonicAudioProcessor pitchChanger = new SonicAudioProcessor();
        pitchChanger.setPitch(mediaItems.size() % 2 == 0 ? 2f : 0.2f);
        MediaItem mediaItem =
            new MediaItem.Builder()
                .setUri(presetUris[i])
                .setImageDurationMs(Util.usToMs(presetDurationsUs[i])) // Ignored for audio/video
                .build();
        EditedMediaItem.Builder itemBuilder =
            new EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    new Effects(
                        /* audioProcessors= */ ImmutableList.of(pitchChanger),
                        /* videoEffects= */ videoEffects))
                .setDurationUs(presetDurationsUs[i]);
        videoSequenceDurationUs += presetDurationsUs[i];
        mediaItems.add(itemBuilder.build());
      }
    }
    EditedMediaItemSequence videoSequence = new EditedMediaItemSequence.Builder(mediaItems).build();
    List<EditedMediaItemSequence> compositionSequences = new ArrayList<>();
    compositionSequences.add(videoSequence);
    if (includeBackgroundAudioTrack) {
      compositionSequences.add(getAudioBackgroundSequence(Util.usToMs(videoSequenceDurationUs)));
    }
    SonicAudioProcessor sampleRateChanger = new SonicAudioProcessor();
    sampleRateChanger.setOutputSampleRateHz(8_000);
    Spinner hdrModeSpinner = findViewById(R.id.hdr_mode_spinner);
    int selectedHdrMode =
        HDR_MODE_DESCRIPTIONS.get(String.valueOf(hdrModeSpinner.getSelectedItem()));
    return new Composition.Builder(compositionSequences)
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(sampleRateChanger),
                /* videoEffects= */ ImmutableList.of()))
        .setHdrMode(selectedHdrMode)
        .build();
  }

  private EditedMediaItemSequence getAudioBackgroundSequence(long durationMs) {
    MediaItem audioMediaItem =
        new MediaItem.Builder()
            .setUri(AUDIO_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(durationMs)
                    .build())
            .build();
    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(audioMediaItem).setDurationUs(59_000_000).build();
    return new EditedMediaItemSequence.Builder(audioItem).build();
  }

  private void previewComposition() {
    releasePlayer();
    Composition composition = prepareComposition();
    playerView.setPlayer(null);

    CompositionPlayer player = new CompositionPlayer.Builder(getApplicationContext()).build();
    this.compositionPlayer = player;
    playerView.setPlayer(compositionPlayer);
    playerView.setControllerAutoShow(false);
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlayerError(PlaybackException error) {
            Toast.makeText(getApplicationContext(), "Preview error: " + error, Toast.LENGTH_LONG)
                .show();
            Log.e(TAG, "Preview error", error);
          }
        });
    player.setComposition(composition);
    player.prepare();
    player.play();
  }

  private void selectPreset() {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_preset_title)
        .setMultiChoiceItems(presetDescriptions, selectedMediaItems, this::selectPresetInDialog)
        .setPositiveButton(R.string.ok, /* listener= */ null)
        .setCancelable(false)
        .create()
        .show();
  }

  private void selectPresetInDialog(DialogInterface dialog, int which, boolean isChecked) {
    selectedMediaItems[which] = isChecked;
    // The items will be added to a the sequence in the order they were selected.
    if (isChecked) {
      sequenceAssetTitles.add(presetDescriptions[which]);
      assetItemAdapter.notifyItemInserted(sequenceAssetTitles.size() - 1);
    } else {
      int index = sequenceAssetTitles.indexOf(presetDescriptions[which]);
      sequenceAssetTitles.remove(presetDescriptions[which]);
      assetItemAdapter.notifyItemRemoved(index);
    }
  }

  private void showExportSettings() {
    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    LayoutInflater inflater = this.getLayoutInflater();
    View exportSettingsDialogView = inflater.inflate(R.layout.export_settings, null);

    alertDialogBuilder
        .setView(exportSettingsDialogView)
        .setTitle(R.string.export_settings)
        .setPositiveButton(
            R.string.export, (dialog, id) -> exportComposition(exportSettingsDialogView))
        .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.dismiss());

    ArrayAdapter<String> audioMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    audioMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner audioMimeSpinner = exportSettingsDialogView.findViewById(R.id.audio_mime_spinner);
    audioMimeSpinner.setAdapter(audioMimeAdapter);
    audioMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB);

    ArrayAdapter<String> videoMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    videoMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner videoMimeSpinner = exportSettingsDialogView.findViewById(R.id.video_mime_spinner);
    videoMimeSpinner.setAdapter(videoMimeAdapter);
    videoMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION,
        MimeTypes.VIDEO_H263,
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_MP4V,
        MimeTypes.VIDEO_AV1);

    CheckBox enableDebugTracingCheckBox =
        exportSettingsDialogView.findViewById(R.id.enable_debug_tracing_checkbox);
    enableDebugTracingCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> DebugTraceUtil.enableTracing = isChecked);

    CheckBox useMedia3Mp4MuxerCheckBox =
        exportSettingsDialogView.findViewById(R.id.use_media3_mp4_muxer_checkbox);
    CheckBox useMedia3FragmentedMp4MuxerCheckBox =
        exportSettingsDialogView.findViewById(R.id.use_media3_fragmented_mp4_muxer_checkbox);
    useMedia3Mp4MuxerCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked) {
            useMedia3FragmentedMp4MuxerCheckBox.setChecked(false);
          }
        });
    useMedia3FragmentedMp4MuxerCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked) {
            useMedia3Mp4MuxerCheckBox.setChecked(false);
          }
        });

    AlertDialog dialog = alertDialogBuilder.create();
    dialog.show();
  }

  private void exportComposition(View exportSettingsDialogView) {
    // Cancel and clean up files from any ongoing export.
    cancelExport();

    Composition composition = prepareComposition();

    try {
      outputFile =
          createExternalCacheFile(
              "composition-preview-" + Clock.DEFAULT.elapsedRealtime() + ".mp4");
    } catch (IOException e) {
      Toast.makeText(
              getApplicationContext(),
              "Aborting export! Unable to create output file: " + e,
              Toast.LENGTH_LONG)
          .show();
      Log.e(TAG, "Aborting export! Unable to create output file: ", e);
      return;
    }
    String filePath = outputFile.getAbsolutePath();

    Transformer.Builder transformerBuilder = new Transformer.Builder(/* context= */ this);

    Spinner audioMimeTypeSpinner = exportSettingsDialogView.findViewById(R.id.audio_mime_spinner);
    String selectedAudioMimeType = String.valueOf(audioMimeTypeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedAudioMimeType)) {
      transformerBuilder.setAudioMimeType(selectedAudioMimeType);
    }

    Spinner videoMimeTypeSpinner = exportSettingsDialogView.findViewById(R.id.video_mime_spinner);
    String selectedVideoMimeType = String.valueOf(videoMimeTypeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedVideoMimeType)) {
      transformerBuilder.setVideoMimeType(selectedVideoMimeType);
    }

    CheckBox useMedia3Mp4MuxerCheckBox =
        exportSettingsDialogView.findViewById(R.id.use_media3_mp4_muxer_checkbox);
    CheckBox useMedia3FragmentedMp4MuxerCheckBox =
        exportSettingsDialogView.findViewById(R.id.use_media3_fragmented_mp4_muxer_checkbox);
    if (useMedia3Mp4MuxerCheckBox.isChecked()) {
      transformerBuilder.setMuxerFactory(new InAppMp4Muxer.Factory());
    }
    if (useMedia3FragmentedMp4MuxerCheckBox.isChecked()) {
      transformerBuilder.setMuxerFactory(new InAppFragmentedMp4Muxer.Factory());
    }

    transformer =
        transformerBuilder
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onCompleted(Composition composition, ExportResult exportResult) {
                    exportStopwatch.stop();
                    long elapsedTimeMs = exportStopwatch.elapsed(TimeUnit.MILLISECONDS);
                    String details =
                        getString(R.string.export_completed, elapsedTimeMs / 1000.f, filePath);
                    Log.d(TAG, DebugTraceUtil.generateTraceSummary());
                    Log.i(TAG, details);
                    exportInformationTextView.setText(details);

                    try {
                      JSONObject resultJson =
                          JsonUtil.exportResultAsJsonObject(exportResult)
                              .put("elapsedTimeMs", elapsedTimeMs)
                              .put("device", JsonUtil.getDeviceDetailsAsJsonObject());
                      for (String line : Util.split(resultJson.toString(2), "\n")) {
                        Log.i(TAG, line);
                      }
                    } catch (JSONException e) {
                      Log.w(TAG, "Unable to convert exportResult to JSON", e);
                    }
                  }

                  @Override
                  public void onError(
                      Composition composition,
                      ExportResult exportResult,
                      ExportException exportException) {
                    exportStopwatch.stop();
                    Toast.makeText(
                            getApplicationContext(),
                            "Export error: " + exportException,
                            Toast.LENGTH_LONG)
                        .show();
                    Log.e(TAG, "Export error", exportException);
                    Log.d(TAG, DebugTraceUtil.generateTraceSummary());
                    exportInformationTextView.setText(R.string.export_error);
                  }
                })
            .build();

    exportInformationTextView.setText(R.string.export_started);
    exportStopwatch.reset();
    exportStopwatch.start();
    transformer.start(composition, filePath);
    Log.i(TAG, "Export started");
  }

  private void releasePlayer() {
    if (compositionPlayer != null) {
      compositionPlayer.release();
      compositionPlayer = null;
    }
  }

  /** Cancels any ongoing export operation, and deletes output file contents. */
  private void cancelExport() {
    if (transformer != null) {
      transformer.cancel();
      transformer = null;
    }
    if (outputFile != null) {
      outputFile.delete();
      outputFile = null;
    }
    exportInformationTextView.setText("");
  }

  /**
   * Creates a {@link File} of the {@code fileName} in the application cache directory.
   *
   * <p>If a file of that name already exists, it is overwritten.
   */
  // TODO: b/320636291 - Refactor duplicate createExternalCacheFile functions.
  private File createExternalCacheFile(String fileName) throws IOException {
    File file = new File(getExternalCacheDir(), fileName);
    if (file.exists() && !file.delete()) {
      throw new IOException("Could not delete file: " + file.getAbsolutePath());
    }
    if (!file.createNewFile()) {
      throw new IOException("Could not create file: " + file.getAbsolutePath());
    }
    return file;
  }
}
