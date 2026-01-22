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
package androidx.media3.demo.compose

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.demo.compose.buttons.ExtraControls
import androidx.media3.demo.compose.buttons.MinimalControls
import androidx.media3.demo.compose.data.videos
import androidx.media3.demo.compose.layout.CONTENT_SCALES
import androidx.media3.demo.compose.layout.noRippleClickable
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { ComposeDemoApp() }
  }
}

@Composable
fun ComposeDemoApp(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var player by remember { mutableStateOf<Player?>(null) }

  // See the following resources
  // https://developer.android.com/topic/libraries/architecture/lifecycle#onStop-and-savedState
  // https://developer.android.com/develop/ui/views/layout/support-multi-window-mode#multi-window_mode_configuration
  // https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode#android_9

  if (Build.VERSION.SDK_INT > 23) {
    // Initialize/release in onStart()/onStop() only because in a multi-window environment multiple
    // apps can be visible at the same time. The apps that are out-of-focus are paused, but video
    // playback should continue.
    LifecycleStartEffect(Unit) {
      player = initializePlayer(context)
      onStopOrDispose {
        player?.apply { release() }
        player = null
      }
    }
  } else {
    // Call to onStop() is not guaranteed, hence we release the Player in onPause() instead
    LifecycleResumeEffect(Unit) {
      player = initializePlayer(context)
      onPauseOrDispose {
        player?.apply { release() }
        player = null
      }
    }
  }

  player?.let { MediaPlayerScreen(player = it, modifier = modifier.fillMaxSize()) }
}

private fun initializePlayer(context: Context): Player =
  ExoPlayer.Builder(context).build().apply {
    setMediaItems(videos.map(MediaItem::fromUri))
    prepare()
  }

@Composable
private fun MediaPlayerScreen(player: Player, modifier: Modifier = Modifier) {
  var showControls by remember { mutableStateOf(true) }
  var currentContentScaleIndex by remember { mutableIntStateOf(0) }
  val contentScale = CONTENT_SCALES[currentContentScaleIndex].second

  val presentationState = rememberPresentationState(player)
  val scaledModifier = Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)

  // Only use MediaPlayerScreen's modifier once for the top level Composable
  Box(modifier) {
    // Always leave PlayerSurface to be part of the Compose tree because it will be initialised in
    // the process. If this composable is guarded by some condition, it might never become visible
    // because the Player will not emit the relevant event, e.g. the first frame being ready.
    PlayerSurface(
      player = player,
      surfaceType = SURFACE_TYPE_SURFACE_VIEW,
      modifier = scaledModifier.noRippleClickable { showControls = !showControls },
    )

    if (presentationState.coverSurface) {
      // Cover the surface that is being prepared with a shutter
      // Do not use scaledModifier here, makes the Box be measured at 0x0
      Box(Modifier.matchParentSize().background(Color.Black))
    }

    if (showControls) {
      // drawn on top of a potential shutter
      MinimalControls(player, Modifier.align(Alignment.Center))
      ExtraControls(
        player,
        Modifier.fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(Color.Gray.copy(alpha = 0.4f))
          .navigationBarsPadding(),
      )
    }

    Button(
      onClick = { currentContentScaleIndex = currentContentScaleIndex.inc() % CONTENT_SCALES.size },
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
    ) {
      Text("ContentScale is ${CONTENT_SCALES[currentContentScaleIndex].first}")
    }
  }
}
