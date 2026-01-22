/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.demo.transformer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_VIDEO;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.transformer.Composition;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;

/**
 * An {@link Activity} that sets the configuration to use for exporting and playing media, using
 * {@link TransformerActivity}.
 */
public final class ConfigurationActivity extends AppCompatActivity {
  public static final String SHOULD_REMOVE_AUDIO = "should_remove_audio";
  public static final String SHOULD_REMOVE_VIDEO = "should_remove_video";
  public static final String SHOULD_FLATTEN_FOR_SLOW_MOTION = "should_flatten_for_slow_motion";
  public static final String FORCE_AUDIO_TRACK = "force_audio_track";
  public static final String AUDIO_MIME_TYPE = "audio_mime_type";
  public static final String VIDEO_MIME_TYPE = "video_mime_type";
  public static final String RESOLUTION_HEIGHT = "resolution_height";
  public static final String SCALE_X = "scale_x";
  public static final String SCALE_Y = "scale_y";
  public static final String ROTATE_DEGREES = "rotate_degrees";
  public static final String TRIM_START_MS = "trim_start_ms";
  public static final String TRIM_END_MS = "trim_end_ms";
  public static final String ENABLE_FALLBACK = "enable_fallback";
  public static final String ENABLE_ANALYZER_MODE = "enable_analyzer_mode";
  public static final String ENABLE_DEBUG_PREVIEW = "enable_debug_preview";
  public static final String ABORT_SLOW_EXPORT = "abort_slow_export";
  public static final String USE_MEDIA3_MP4_MUXER = "use_media3_mp4_muxer";
  public static final String USE_MEDIA3_FRAGMENTED_MP4_MUXER = "use_media3_fragmented_mp4_muxer";
  public static final String HDR_MODE = "hdr_mode";
  public static final String AUDIO_EFFECTS_SELECTIONS = "audio_effects_selections";
  public static final String VIDEO_EFFECTS_SELECTIONS = "video_effects_selections";
  public static final String PERIODIC_VIGNETTE_CENTER_X = "periodic_vignette_center_x";
  public static final String PERIODIC_VIGNETTE_CENTER_Y = "periodic_vignette_center_y";
  public static final String PERIODIC_VIGNETTE_INNER_RADIUS = "periodic_vignette_inner_radius";
  public static final String PERIODIC_VIGNETTE_OUTER_RADIUS = "periodic_vignette_outer_radius";
  public static final String COLOR_FILTER_SELECTION = "color_filter_selection";
  public static final String CONTRAST_VALUE = "contrast_value";
  public static final String RGB_ADJUSTMENT_RED_SCALE = "rgb_adjustment_red_scale";
  public static final String RGB_ADJUSTMENT_GREEN_SCALE = "rgb_adjustment_green_scale";
  public static final String RGB_ADJUSTMENT_BLUE_SCALE = "rgb_adjustment_blue_scale";
  public static final String HSL_ADJUSTMENTS_HUE = "hsl_adjustments_hue";
  public static final String HSL_ADJUSTMENTS_SATURATION = "hsl_adjustments_saturation";
  public static final String HSL_ADJUSTMENTS_LIGHTNESS = "hsl_adjustments_lightness";
  public static final String BITMAP_OVERLAY_URI = "bitmap_overlay_uri";
  public static final String BITMAP_OVERLAY_ALPHA = "bitmap_overlay_alpha";
  public static final String TEXT_OVERLAY_TEXT = "text_overlay_text";
  public static final String TEXT_OVERLAY_TEXT_COLOR = "text_overlay_text_color";
  public static final String TEXT_OVERLAY_ALPHA = "text_overlay_alpha";

  // Video effect selections.
  public static final int DIZZY_CROP_INDEX = 0;
  public static final int EDGE_DETECTOR_INDEX = 1;
  public static final int COLOR_FILTERS_INDEX = 2;
  public static final int MAP_WHITE_TO_GREEN_LUT_INDEX = 3;
  public static final int RGB_ADJUSTMENTS_INDEX = 4;
  public static final int HSL_ADJUSTMENT_INDEX = 5;
  public static final int CONTRAST_INDEX = 6;
  public static final int PERIODIC_VIGNETTE_INDEX = 7;
  public static final int SPIN_3D_INDEX = 8;
  public static final int ZOOM_IN_INDEX = 9;
  public static final int OVERLAY_LOGO_AND_TIMER_INDEX = 10;
  public static final int BITMAP_OVERLAY_INDEX = 11;
  public static final int TEXT_OVERLAY_INDEX = 12;
  public static final int CLOCK_OVERLAY_INDEX = 13;
  public static final int CONFETTI_OVERLAY_INDEX = 14;
  public static final int ANIMATING_LOGO_OVERLAY = 15;

  // Audio effect selections.
  public static final int HIGH_PITCHED_INDEX = 0;
  public static final int SAMPLE_RATE_48K_INDEX = 1;
  public static final int SAMPLE_RATE_96K_INDEX = 2;
  public static final int SKIP_SILENCE_INDEX = 3;
  public static final int CHANNEL_MIXING_INDEX = 4;
  public static final int VOLUME_SCALING_INDEX = 5;

  // Color filter options.
  public static final int COLOR_FILTER_GRAYSCALE = 0;
  public static final int COLOR_FILTER_INVERTED = 1;
  public static final int COLOR_FILTER_SEPIA = 2;

  public static final int FILE_PERMISSION_REQUEST_CODE = 1;
  private static final ImmutableMap<String, @Composition.HdrMode Integer> HDR_MODE_DESCRIPTIONS =
      new ImmutableMap.Builder<String, @Composition.HdrMode Integer>()
          .put("Keep HDR", HDR_MODE_KEEP_HDR)
          .put("MediaCodec tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)
          .put("OpenGL tone-map HDR to SDR", HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
          .put("Force Interpret HDR as SDR", HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR)
          .build();
  private static final ImmutableMap<String, Integer> OVERLAY_COLORS =
      new ImmutableMap.Builder<String, Integer>()
          .put("BLACK", Color.BLACK)
          .put("BLUE", Color.BLUE)
          .put("CYAN", Color.CYAN)
          .put("DKGRAY", Color.DKGRAY)
          .put("GRAY", Color.GRAY)
          .put("GREEN", Color.GREEN)
          .put("LTGRAY", Color.LTGRAY)
          .put("MAGENTA", Color.MAGENTA)
          .put("RED", Color.RED)
          .put("WHITE", Color.WHITE)
          .put("YELLOW", Color.YELLOW)
          .build();
  private static final String SAME_AS_INPUT_OPTION = "same as input";
  private static final float HALF_DIAGONAL = 1f / (float) Math.sqrt(2);

  private Runnable onPermissionsGranted;
  private ActivityResultLauncher<Intent> videoLocalFilePickerLauncher;
  private ActivityResultLauncher<Intent> overlayLocalFilePickerLauncher;
  private Button selectPresetButton;
  private Button selectLocalFileButton;
  private TextView selectedFileTextView;
  private CheckBox removeAudioCheckbox;
  private CheckBox removeVideoCheckbox;
  private CheckBox flattenForSlowMotionCheckbox;
  private CheckBox forceAudioTrackCheckbox;
  private Spinner audioMimeSpinner;
  private Spinner videoMimeSpinner;
  private Spinner resolutionHeightSpinner;
  private Spinner scaleSpinner;
  private Spinner rotateSpinner;
  private CheckBox trimCheckBox;
  private CheckBox enableFallbackCheckBox;
  private CheckBox enableAnalyzerModeCheckBox;
  private CheckBox enableDebugPreviewCheckBox;
  private CheckBox enableDebugTracingCheckBox;
  private CheckBox abortSlowExportCheckBox;
  private CheckBox useMedia3Mp4Muxer;
  private CheckBox useMedia3FragmentedMp4Muxer;
  private Spinner hdrModeSpinner;
  private Button selectAudioEffectsButton;
  private Button selectVideoEffectsButton;
  private boolean[] audioEffectsSelections;
  private boolean[] videoEffectsSelections;
  private String[] presetDescriptions;
  private Uri localFileUri;
  private int inputUriPosition;
  private long trimStartMs;
  private long trimEndMs;
  private int colorFilterSelection;
  private float rgbAdjustmentRedScale;
  private float rgbAdjustmentGreenScale;
  private float rgbAdjustmentBlueScale;
  private float contrastValue;
  private float hueAdjustment;
  private float saturationAdjustment;
  private float lightnessAdjustment;
  private float periodicVignetteCenterX;
  private float periodicVignetteCenterY;
  private float periodicVignetteInnerRadius;
  private float periodicVignetteOuterRadius;
  private String bitmapOverlayUri;
  private float bitmapOverlayAlpha;
  private String textOverlayText;
  private int textOverlayTextColor;
  private float textOverlayAlpha;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.configuration_activity);

    findViewById(R.id.export_button).setOnClickListener(view -> startExport());

    videoLocalFilePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::videoLocalFilePickerLauncherResult);
    overlayLocalFilePickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::overlayLocalFilePickerLauncherResult);

    selectPresetButton = findViewById(R.id.select_preset_button);
    selectPresetButton.setOnClickListener(view -> selectPreset());

    selectLocalFileButton = findViewById(R.id.select_local_file_button);
    selectLocalFileButton.setOnClickListener(
        view ->
            selectLocalFile(
                videoLocalFilePickerLauncher,
                /* mimeTypes= */ new String[] {"image/*", "video/*", "audio/*"}));

    selectedFileTextView = findViewById(R.id.selected_file_text_view);
    presetDescriptions = getResources().getStringArray(R.array.preset_descriptions);
    selectedFileTextView.setText(presetDescriptions[inputUriPosition]);

    removeAudioCheckbox = findViewById(R.id.remove_audio_checkbox);
    removeAudioCheckbox.setOnClickListener(this::onRemoveAudio);

    removeVideoCheckbox = findViewById(R.id.remove_video_checkbox);
    removeVideoCheckbox.setOnClickListener(this::onRemoveVideo);

    flattenForSlowMotionCheckbox = findViewById(R.id.flatten_for_slow_motion_checkbox);

    forceAudioTrackCheckbox = findViewById(R.id.force_audio_track_checkbox);

    ArrayAdapter<String> audioMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    audioMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    audioMimeSpinner = findViewById(R.id.audio_mime_spinner);
    audioMimeSpinner.setAdapter(audioMimeAdapter);
    audioMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB);

    ArrayAdapter<String> videoMimeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    videoMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    videoMimeSpinner = findViewById(R.id.video_mime_spinner);
    videoMimeSpinner.setAdapter(videoMimeAdapter);
    videoMimeAdapter.addAll(
        SAME_AS_INPUT_OPTION,
        MimeTypes.VIDEO_H263,
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_MP4V,
        MimeTypes.VIDEO_AV1,
        MimeTypes.VIDEO_DOLBY_VISION);

    ArrayAdapter<String> resolutionHeightAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    resolutionHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    resolutionHeightSpinner = findViewById(R.id.resolution_height_spinner);
    resolutionHeightSpinner.setAdapter(resolutionHeightAdapter);
    resolutionHeightAdapter.addAll(
        SAME_AS_INPUT_OPTION, "144", "240", "360", "480", "720", "1080", "1440", "2160");

    ArrayAdapter<String> scaleAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    scaleSpinner = findViewById(R.id.scale_spinner);
    scaleSpinner.setAdapter(scaleAdapter);
    scaleAdapter.addAll(SAME_AS_INPUT_OPTION, "-1, -1", "-1, 1", "1, 1", ".5, 1", ".5, .5", "2, 2");

    ArrayAdapter<String> rotateAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    rotateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    rotateSpinner = findViewById(R.id.rotate_spinner);
    rotateSpinner.setAdapter(rotateAdapter);
    rotateAdapter.addAll(SAME_AS_INPUT_OPTION, "0", "10", "45", "60", "90", "180");

    trimCheckBox = findViewById(R.id.trim_checkbox);
    trimCheckBox.setOnCheckedChangeListener((view, isChecked) -> selectTrimBounds(isChecked));
    trimStartMs = C.TIME_UNSET;
    trimEndMs = C.TIME_UNSET;

    enableFallbackCheckBox = findViewById(R.id.enable_fallback_checkbox);
    enableAnalyzerModeCheckBox = findViewById(R.id.enable_analyzer_mode_checkbox);
    enableDebugPreviewCheckBox = findViewById(R.id.enable_debug_preview_checkbox);
    enableDebugTracingCheckBox = findViewById(R.id.enable_debug_tracing_checkbox);
    enableDebugTracingCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> DebugTraceUtil.enableTracing = isChecked);

    abortSlowExportCheckBox = findViewById(R.id.abort_slow_export_checkbox);
    useMedia3Mp4Muxer = findViewById(R.id.use_media3_mp4_muxer_checkbox);
    useMedia3FragmentedMp4Muxer = findViewById(R.id.use_media3_fragmented_mp4_muxer_checkbox);
    useMedia3Mp4Muxer.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked) {
            useMedia3FragmentedMp4Muxer.setChecked(false);
          }
        });
    useMedia3FragmentedMp4Muxer.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isChecked) {
            useMedia3Mp4Muxer.setChecked(false);
          }
        });

    ArrayAdapter<String> hdrModeAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    hdrModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    hdrModeSpinner = findViewById(R.id.hdr_mode_spinner);
    hdrModeSpinner.setAdapter(hdrModeAdapter);
    hdrModeAdapter.addAll(HDR_MODE_DESCRIPTIONS.keySet());

    String[] audioEffectsNames = getResources().getStringArray(R.array.audio_effects_names);
    audioEffectsSelections = new boolean[audioEffectsNames.length];
    selectAudioEffectsButton = findViewById(R.id.select_audio_effects_button);
    selectAudioEffectsButton.setOnClickListener(view -> selectAudioEffects(audioEffectsNames));

    String[] videoEffectsNames = getResources().getStringArray(R.array.video_effects_names);
    videoEffectsSelections = new boolean[videoEffectsNames.length];
    selectVideoEffectsButton = findViewById(R.id.select_video_effects_button);
    selectVideoEffectsButton.setOnClickListener(view -> selectVideoEffects(videoEffectsNames));
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == FILE_PERMISSION_REQUEST_CODE
        && grantResults.length == 1
        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      onPermissionsGranted.run();
    } else {
      Toast.makeText(
              getApplicationContext(), getString(R.string.permission_denied), Toast.LENGTH_LONG)
          .show();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    @Nullable Uri intentUri = getIntent().getData();
    if (intentUri != null) {
      selectPresetButton.setEnabled(false);
      selectLocalFileButton.setEnabled(false);
      selectedFileTextView.setText(intentUri.toString());
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
  }

  private void startExport() {
    Intent transformerIntent = new Intent(/* packageContext= */ this, TransformerActivity.class);
    Bundle bundle = new Bundle();
    bundle.putBoolean(SHOULD_REMOVE_AUDIO, removeAudioCheckbox.isChecked());
    bundle.putBoolean(SHOULD_REMOVE_VIDEO, removeVideoCheckbox.isChecked());
    bundle.putBoolean(SHOULD_FLATTEN_FOR_SLOW_MOTION, flattenForSlowMotionCheckbox.isChecked());
    bundle.putBoolean(FORCE_AUDIO_TRACK, forceAudioTrackCheckbox.isChecked());
    String selectedAudioMimeType = String.valueOf(audioMimeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedAudioMimeType)) {
      bundle.putString(AUDIO_MIME_TYPE, selectedAudioMimeType);
    }
    String selectedVideoMimeType = String.valueOf(videoMimeSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedVideoMimeType)) {
      bundle.putString(VIDEO_MIME_TYPE, selectedVideoMimeType);
    }
    String selectedResolutionHeight = String.valueOf(resolutionHeightSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedResolutionHeight)) {
      bundle.putInt(RESOLUTION_HEIGHT, Integer.parseInt(selectedResolutionHeight));
    }
    String selectedScale = String.valueOf(scaleSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedScale)) {
      List<String> scaleXY = Arrays.asList(selectedScale.split(", "));
      checkState(scaleXY.size() == 2);
      bundle.putFloat(SCALE_X, Float.parseFloat(scaleXY.get(0)));
      bundle.putFloat(SCALE_Y, Float.parseFloat(scaleXY.get(1)));
    }
    String selectedRotate = String.valueOf(rotateSpinner.getSelectedItem());
    if (!SAME_AS_INPUT_OPTION.equals(selectedRotate)) {
      bundle.putFloat(ROTATE_DEGREES, Float.parseFloat(selectedRotate));
    }
    if (trimCheckBox.isChecked()) {
      bundle.putLong(TRIM_START_MS, trimStartMs);
      bundle.putLong(TRIM_END_MS, trimEndMs);
    }
    bundle.putBoolean(ENABLE_FALLBACK, enableFallbackCheckBox.isChecked());
    bundle.putBoolean(ENABLE_ANALYZER_MODE, enableAnalyzerModeCheckBox.isChecked());
    bundle.putBoolean(ENABLE_DEBUG_PREVIEW, enableDebugPreviewCheckBox.isChecked());
    bundle.putBoolean(ABORT_SLOW_EXPORT, abortSlowExportCheckBox.isChecked());
    bundle.putBoolean(USE_MEDIA3_MP4_MUXER, useMedia3Mp4Muxer.isChecked());
    bundle.putBoolean(USE_MEDIA3_FRAGMENTED_MP4_MUXER, useMedia3FragmentedMp4Muxer.isChecked());
    String selectedHdrMode = String.valueOf(hdrModeSpinner.getSelectedItem());
    bundle.putInt(HDR_MODE, HDR_MODE_DESCRIPTIONS.get(selectedHdrMode));
    bundle.putBooleanArray(AUDIO_EFFECTS_SELECTIONS, audioEffectsSelections);
    bundle.putBooleanArray(VIDEO_EFFECTS_SELECTIONS, videoEffectsSelections);
    bundle.putInt(COLOR_FILTER_SELECTION, colorFilterSelection);
    bundle.putFloat(CONTRAST_VALUE, contrastValue);
    bundle.putFloat(RGB_ADJUSTMENT_RED_SCALE, rgbAdjustmentRedScale);
    bundle.putFloat(RGB_ADJUSTMENT_GREEN_SCALE, rgbAdjustmentGreenScale);
    bundle.putFloat(RGB_ADJUSTMENT_BLUE_SCALE, rgbAdjustmentBlueScale);
    bundle.putFloat(HSL_ADJUSTMENTS_HUE, hueAdjustment);
    bundle.putFloat(HSL_ADJUSTMENTS_SATURATION, saturationAdjustment);
    bundle.putFloat(HSL_ADJUSTMENTS_LIGHTNESS, lightnessAdjustment);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_X, periodicVignetteCenterX);
    bundle.putFloat(PERIODIC_VIGNETTE_CENTER_Y, periodicVignetteCenterY);
    bundle.putFloat(PERIODIC_VIGNETTE_INNER_RADIUS, periodicVignetteInnerRadius);
    bundle.putFloat(PERIODIC_VIGNETTE_OUTER_RADIUS, periodicVignetteOuterRadius);
    bundle.putString(BITMAP_OVERLAY_URI, bitmapOverlayUri);
    bundle.putFloat(BITMAP_OVERLAY_ALPHA, bitmapOverlayAlpha);
    bundle.putString(TEXT_OVERLAY_TEXT, textOverlayText);
    bundle.putInt(TEXT_OVERLAY_TEXT_COLOR, textOverlayTextColor);
    bundle.putFloat(TEXT_OVERLAY_ALPHA, textOverlayAlpha);
    transformerIntent.putExtras(bundle);

    @Nullable Uri intentUri;
    if (getIntent().getData() != null) {
      intentUri = getIntent().getData();
    } else if (localFileUri != null) {
      intentUri = localFileUri;
    } else {
      String[] presetUris = getResources().getStringArray(R.array.preset_uris);
      intentUri = Uri.parse(presetUris[inputUriPosition]);
    }
    transformerIntent.setData(intentUri);

    startActivity(transformerIntent);
  }

  private void selectPreset() {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_preset_title)
        .setSingleChoiceItems(presetDescriptions, inputUriPosition, this::selectPresetInDialog)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectPresetInDialog(DialogInterface dialog, int which) {
    inputUriPosition = which;
    localFileUri = null;
    selectedFileTextView.setText(presetDescriptions[inputUriPosition]);
  }

  private void selectLocalFile(
      ActivityResultLauncher<Intent> localFilePickerLauncher, String[] mimeTypes) {
    String permission = SDK_INT >= 33 ? READ_MEDIA_VIDEO : READ_EXTERNAL_STORAGE;
    if (ActivityCompat.checkSelfPermission(/* context= */ this, permission)
        != PackageManager.PERMISSION_GRANTED) {
      onPermissionsGranted = () -> launchLocalFilePicker(localFilePickerLauncher, mimeTypes);
      ActivityCompat.requestPermissions(
          /* activity= */ this, new String[] {permission}, FILE_PERMISSION_REQUEST_CODE);
    } else {
      launchLocalFilePicker(localFilePickerLauncher, mimeTypes);
    }
  }

  private void launchLocalFilePicker(
      ActivityResultLauncher<Intent> localFilePickerLauncher, String[] mimeTypes) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("*/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    localFilePickerLauncher.launch(intent);
  }

  private void videoLocalFilePickerLauncherResult(ActivityResult result) {
    Intent data = result.getData();
    if (data != null) {
      localFileUri = data.getData();
      selectedFileTextView.setText(localFileUri.toString());
    } else {
      Toast.makeText(
              getApplicationContext(),
              getString(R.string.local_file_picker_failed),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void overlayLocalFilePickerLauncherResult(ActivityResult result) {
    Intent data = result.getData();
    if (data != null) {
      bitmapOverlayUri = data.getData().toString();
    } else {
      Toast.makeText(
              getApplicationContext(),
              getString(R.string.local_file_picker_failed),
              Toast.LENGTH_SHORT)
          .show();
    }
  }

  private void selectAudioEffects(String[] audioEffectsNames) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_audio_effects)
        .setMultiChoiceItems(audioEffectsNames, audioEffectsSelections, this::selectAudioEffect)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectVideoEffects(String[] videoEffectsNames) {
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.select_video_effects)
        .setMultiChoiceItems(videoEffectsNames, videoEffectsSelections, this::selectVideoEffect)
        .setPositiveButton(android.R.string.ok, /* listener= */ null)
        .create()
        .show();
  }

  private void selectTrimBounds(boolean isChecked) {
    if (!isChecked) {
      return;
    }
    View dialogView = getLayoutInflater().inflate(R.layout.trim_options, /* root= */ null);
    RangeSlider trimRangeSlider = dialogView.findViewById(R.id.trim_bounds_range_slider);
    trimRangeSlider.setValues(0f, 1f); // seconds
    new AlertDialog.Builder(/* context= */ this)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              List<Float> trimRange = trimRangeSlider.getValues();
              trimStartMs = Math.round(1000 * trimRange.get(0));
              trimEndMs = Math.round(1000 * trimRange.get(1));
            })
        .create()
        .show();
  }

  private void selectAudioEffect(DialogInterface dialog, int which, boolean isChecked) {
    audioEffectsSelections[which] = isChecked;
  }

  private void selectVideoEffect(DialogInterface dialog, int which, boolean isChecked) {
    videoEffectsSelections[which] = isChecked;
    if (!isChecked) {
      return;
    }

    switch (which) {
      case COLOR_FILTERS_INDEX:
        controlColorFiltersSettings();
        break;
      case RGB_ADJUSTMENTS_INDEX:
        controlRgbAdjustmentsScale();
        break;
      case CONTRAST_INDEX:
        controlContrastSettings();
        break;
      case HSL_ADJUSTMENT_INDEX:
        controlHslAdjustmentSettings();
        break;
      case PERIODIC_VIGNETTE_INDEX:
        controlPeriodicVignetteSettings();
        break;
      case BITMAP_OVERLAY_INDEX:
        controlBitmapOverlaySettings();
        break;
      case TEXT_OVERLAY_INDEX:
        controlTextOverlaySettings();
        break;
    }
  }

  private void controlColorFiltersSettings() {
    new AlertDialog.Builder(/* context= */ this)
        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss())
        .setSingleChoiceItems(
            this.getResources().getStringArray(R.array.color_filter_options),
            colorFilterSelection,
            (DialogInterface dialogInterface, int i) -> {
              checkState(
                  i == COLOR_FILTER_GRAYSCALE
                      || i == COLOR_FILTER_INVERTED
                      || i == COLOR_FILTER_SEPIA);
              colorFilterSelection = i;
              dialogInterface.dismiss();
            })
        .create()
        .show();
  }

  private void controlRgbAdjustmentsScale() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.rgb_adjustment_options, /* root= */ null);
    Slider redScaleSlider = dialogView.findViewById(R.id.rgb_adjustment_red_scale);
    Slider greenScaleSlider = dialogView.findViewById(R.id.rgb_adjustment_green_scale);
    Slider blueScaleSlider = dialogView.findViewById(R.id.rgb_adjustment_blue_scale);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.rgb_adjustment_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              rgbAdjustmentRedScale = redScaleSlider.getValue();
              rgbAdjustmentGreenScale = greenScaleSlider.getValue();
              rgbAdjustmentBlueScale = blueScaleSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlContrastSettings() {
    View dialogView = getLayoutInflater().inflate(R.layout.contrast_options, /* root= */ null);
    Slider contrastSlider = dialogView.findViewById(R.id.contrast_slider);
    new AlertDialog.Builder(/* context= */ this)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> contrastValue = contrastSlider.getValue())
        .create()
        .show();
  }

  private void controlHslAdjustmentSettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.hsl_adjustment_options, /* root= */ null);
    Slider hueAdjustmentSlider = dialogView.findViewById(R.id.hsl_adjustments_hue);
    Slider saturationAdjustmentSlider = dialogView.findViewById(R.id.hsl_adjustments_saturation);
    Slider lightnessAdjustmentSlider = dialogView.findViewById(R.id.hsl_adjustment_lightness);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.hsl_adjustment_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              hueAdjustment = hueAdjustmentSlider.getValue();
              saturationAdjustment = saturationAdjustmentSlider.getValue();
              lightnessAdjustment = lightnessAdjustmentSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlPeriodicVignetteSettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.periodic_vignette_options, /* root= */ null);
    Slider centerXSlider = dialogView.findViewById(R.id.periodic_vignette_center_x_slider);
    Slider centerYSlider = dialogView.findViewById(R.id.periodic_vignette_center_y_slider);
    RangeSlider radiusRangeSlider =
        dialogView.findViewById(R.id.periodic_vignette_radius_range_slider);
    radiusRangeSlider.setValues(0f, HALF_DIAGONAL);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.periodic_vignette_options)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              periodicVignetteCenterX = centerXSlider.getValue();
              periodicVignetteCenterY = centerYSlider.getValue();
              List<Float> radiusRange = radiusRangeSlider.getValues();
              periodicVignetteInnerRadius = radiusRange.get(0);
              periodicVignetteOuterRadius = radiusRange.get(1);
            })
        .create()
        .show();
  }

  private void controlBitmapOverlaySettings() {
    View dialogView =
        getLayoutInflater().inflate(R.layout.bitmap_overlay_options, /* root= */ null);
    Button uriButton = dialogView.findViewById(R.id.bitmap_overlay_uri);
    uriButton.setOnClickListener(
        (view ->
            selectLocalFile(
                overlayLocalFilePickerLauncher, /* mimeTypes= */ new String[] {"image/*"})));
    Slider alphaSlider = dialogView.findViewById(R.id.bitmap_overlay_alpha_slider);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.bitmap_overlay_settings)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              bitmapOverlayAlpha = alphaSlider.getValue();
            })
        .create()
        .show();
  }

  private void controlTextOverlaySettings() {
    View dialogView = getLayoutInflater().inflate(R.layout.text_overlay_options, /* root= */ null);
    EditText textEditText = dialogView.findViewById(R.id.text_overlay_text);

    ArrayAdapter<String> textColorAdapter =
        new ArrayAdapter<>(/* context= */ this, R.layout.spinner_item);
    textColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    Spinner textColorSpinner = dialogView.findViewById(R.id.text_overlay_text_color);
    textColorSpinner.setAdapter(textColorAdapter);
    textColorAdapter.addAll(OVERLAY_COLORS.keySet());

    Slider alphaSlider = dialogView.findViewById(R.id.text_overlay_alpha_slider);
    new AlertDialog.Builder(/* context= */ this)
        .setTitle(R.string.bitmap_overlay_settings)
        .setView(dialogView)
        .setPositiveButton(
            android.R.string.ok,
            (DialogInterface dialogInterface, int i) -> {
              textOverlayText = textEditText.getText().toString();
              String selectedTextColor = String.valueOf(textColorSpinner.getSelectedItem());
              textOverlayTextColor = OVERLAY_COLORS.get(selectedTextColor);
              textOverlayAlpha = alphaSlider.getValue();
            })
        .create()
        .show();
  }

  private void onRemoveAudio(View view) {
    if (((CheckBox) view).isChecked()) {
      removeVideoCheckbox.setChecked(false);
      enableTrackSpecificOptions(/* isAudioEnabled= */ false, /* isVideoEnabled= */ true);
    } else {
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ true);
    }
  }

  private void onRemoveVideo(View view) {
    if (((CheckBox) view).isChecked()) {
      removeAudioCheckbox.setChecked(false);
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ false);
    } else {
      enableTrackSpecificOptions(/* isAudioEnabled= */ true, /* isVideoEnabled= */ true);
    }
  }

  private void enableTrackSpecificOptions(boolean isAudioEnabled, boolean isVideoEnabled) {
    forceAudioTrackCheckbox.setEnabled(isVideoEnabled);
    audioMimeSpinner.setEnabled(isAudioEnabled);
    videoMimeSpinner.setEnabled(isVideoEnabled);
    resolutionHeightSpinner.setEnabled(isVideoEnabled);
    scaleSpinner.setEnabled(isVideoEnabled);
    rotateSpinner.setEnabled(isVideoEnabled);
    enableDebugPreviewCheckBox.setEnabled(isVideoEnabled);
    hdrModeSpinner.setEnabled(isVideoEnabled);
    selectAudioEffectsButton.setEnabled(isAudioEnabled);
    selectVideoEffectsButton.setEnabled(isVideoEnabled);

    findViewById(R.id.audio_mime_text_view).setEnabled(isAudioEnabled);
    findViewById(R.id.video_mime_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.resolution_height_text_view).setEnabled(isVideoEnabled);
    findViewById(R.id.scale).setEnabled(isVideoEnabled);
    findViewById(R.id.rotate).setEnabled(isVideoEnabled);
    findViewById(R.id.hdr_mode).setEnabled(isVideoEnabled);
  }
}
