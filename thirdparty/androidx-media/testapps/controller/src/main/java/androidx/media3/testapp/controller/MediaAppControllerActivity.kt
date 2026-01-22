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
package androidx.media3.testapp.controller

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Log
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionToken
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaAppControllerActivity : AppCompatActivity() {
  private lateinit var mediaAppDetails: MediaAppDetails
  private lateinit var browserFuture: ListenableFuture<MediaBrowser>
  private val browser: MediaBrowser?
    get() = if (browserFuture.isDone && !browserFuture.isCancelled) browserFuture.get() else null

  private lateinit var viewPager: ViewPager
  private lateinit var ratingViewGroup: ViewGroup

  private lateinit var mediaInfoText: TextView
  private lateinit var mediaTitleView: TextView
  private lateinit var mediaArtistView: TextView
  private lateinit var mediaAlbumView: TextView
  private lateinit var mediaAlbumArtView: ImageView

  private lateinit var transportControlHelper: TransportControlHelper
  private lateinit var shuffleModeHelper: ShuffleModeHelper
  private lateinit var repeatModeHelper: RepeatModeHelper
  private lateinit var ratingHelper: RatingHelper
  private lateinit var customCommandsAdapter: CustomCommandsAdapter
  private lateinit var timelineAdapter: TimelineAdapter
  private lateinit var browseMediaItemsAdapter: BrowseMediaItemsAdapter
  private lateinit var searchMediaItemsAdapter: SearchMediaItemsAdapter

  private lateinit var bitmapLoader: BitmapLoader

  companion object {
    private const val TAG = "ControllerActivity"

    // Key name for Intent extras.
    private const val APP_DETAILS_EXTRA = "androidx.media3.testapp.controller.APP_DETAILS_EXTRA"

    /**
     * Builds an [Intent] to launch this Activity with a set of extras.
     *
     * @param activity The Activity building the Intent.
     * @param appDetails The app details about the media app to connect to.
     * @return An Intent that can be used to start the Activity.
     */
    fun buildIntent(activity: Activity, appDetails: MediaAppDetails): Intent {
      val intent = Intent(activity, MediaAppControllerActivity::class.java)
      intent.putExtra(APP_DETAILS_EXTRA, appDetails.toBundle())
      return intent
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_media_app_controller)

    val toolbar: Toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    toolbar.setNavigationOnClickListener { finish() }

    bitmapLoader = CacheBitmapLoader(DataSourceBitmapLoader(this))
    viewPager = findViewById(R.id.view_pager)
    ratingViewGroup = findViewById(R.id.rating)
    mediaInfoText = findViewById(R.id.media_info)
    mediaAlbumArtView = findViewById(R.id.media_art)
    mediaTitleView = findViewById(R.id.media_title)
    mediaArtistView = findViewById(R.id.media_artist)
    mediaAlbumView = findViewById(R.id.media_album)

    mediaAppDetails = parseIntent(intent)

    val pages: Array<Int> =
      arrayOf(
        R.id.prepare_play_page,
        R.id.controls_page,
        R.id.custom_commands_page,
        R.id.timeline_list_page,
        R.id.browse_tree_page,
        R.id.media_search_page,
      )

    viewPager.offscreenPageLimit = pages.size
    viewPager.adapter =
      object : PagerAdapter() {
        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = (view === obj)

        override fun instantiateItem(container: ViewGroup, position: Int): Any =
          findViewById(pages[position])
      }

    val pageIndicator: TabLayout = findViewById(R.id.page_indicator)
    pageIndicator.setupWithViewPager(viewPager)

    setupToolbar()
    setupMediaBrowser()
  }

  override fun onDestroy() {
    MediaBrowser.releaseFuture(browserFuture)
    super.onDestroy()
  }

  private fun parseIntent(intent: Intent): MediaAppDetails {
    val extras: Bundle? = intent.extras
    return MediaAppDetails.fromBundle(extras!!.getBundle(APP_DETAILS_EXTRA)!!)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBundle(APP_DETAILS_EXTRA, mediaAppDetails.toBundle())
  }

  private fun setupMediaBrowser() {
    browserFuture = getMediaBrowser(mediaAppDetails.sessionToken)
    val listener: Player.Listener =
      object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
          updateMediaInfoText()
          updateMediaMetadataView(mediaMetadata)
        }

        override fun onPlaybackStateChanged(state: Int) = updateMediaInfoText()

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
          updateMediaInfoText()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
          updateMediaInfoText()
      }

    Futures.addCallback(
      browserFuture,
      object : FutureCallback<MediaBrowser> {
        override fun onSuccess(browser: MediaBrowser) {
          browser.addListener(listener)
          PreparePlayHelper(this@MediaAppControllerActivity, browser)
          AudioFocusHelper(this@MediaAppControllerActivity)
          customCommandsAdapter =
            CustomCommandsAdapter(
              this@MediaAppControllerActivity,
              browser,
              mediaAppDetails.packageName,
            )
          transportControlHelper = TransportControlHelper(this@MediaAppControllerActivity, browser)
          shuffleModeHelper = ShuffleModeHelper(this@MediaAppControllerActivity, browser)
          repeatModeHelper = RepeatModeHelper(this@MediaAppControllerActivity, browser)
          ratingHelper = RatingHelper(ratingViewGroup, browser)
          timelineAdapter = TimelineAdapter(this@MediaAppControllerActivity, browser)
          browseMediaItemsAdapter =
            BrowseMediaItemsAdapter(this@MediaAppControllerActivity, browser)
          searchMediaItemsAdapter =
            SearchMediaItemsAdapter(this@MediaAppControllerActivity, browser)

          updateMediaInfoText()
          updateMediaMetadataView(browser.mediaMetadata)
        }

        override fun onFailure(t: Throwable) {
          mediaInfoText.text = getString(R.string.controller_connection_failed_msg, t.message)
        }
      },
      ContextCompat.getMainExecutor(this),
    )
  }

  private fun getMediaBrowser(token: SessionToken): ListenableFuture<MediaBrowser> {
    val listener =
      object : MediaBrowser.Listener {
        override fun onAvailableSessionCommandsChanged(
          controller: MediaController,
          commands: SessionCommands,
        ) = updateMediaInfoText()

        override fun onCustomLayoutChanged(
          controller: MediaController,
          layout: MutableList<CommandButton>,
        ) {
          customCommandsAdapter.setCommands(layout)
        }

        override fun onChildrenChanged(
          browser: MediaBrowser,
          parentId: String,
          itemCount: Int,
          params: MediaLibraryService.LibraryParams?,
        ) {
          if (
            itemCount >= 1 &&
              browser.isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
          ) {
            val future: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
              browser.getChildren(parentId, 0, itemCount, params)
            future.addListener(
              {
                val items: List<MediaItem> = future.get().value ?: emptyList()
                browseMediaItemsAdapter.updateItems(items)
              },
              ContextCompat.getMainExecutor(this@MediaAppControllerActivity),
            )
          }
        }

        override fun onDisconnected(controller: MediaController) {
          mediaInfoText.text = getString(R.string.controller_disconnected_msg)
          browseMediaItemsAdapter.updateItems(emptyList())
          searchMediaItemsAdapter.updateItems(emptyList())
        }
      }
    return MediaBrowser.Builder(this, token).setListener(listener).buildAsync()
  }

  private fun updateMediaInfoText() {
    val browser = this.browser ?: return

    val mediaInfos = HashMap<String, CharSequence>()

    mediaInfos[getString(R.string.info_state_string)] =
      MediaIntToString.playbackStateMap.getValue(browser.playbackState)

    val mediaMetadata: MediaMetadata = browser.mediaMetadata

    mediaInfos[getString(R.string.info_title_string)] =
      mediaMetadata.title ?: "Title metadata empty"
    mediaInfos[getString(R.string.info_artist_string)] =
      mediaMetadata.artist ?: "Artist metadata empty"
    mediaInfos[getString(R.string.info_album_string)] =
      mediaMetadata.albumTitle ?: "Album title metadata empty"
    mediaInfos[getString(R.string.info_play_when_ready)] = browser.playWhenReady.toString()

    var infoCharSequence: CharSequence = ""
    val keys: List<String> = mediaInfos.keys.sorted()

    for (key in keys) {
      infoCharSequence = TextUtils.concat(infoCharSequence, key, "=", mediaInfos[key], "\n")
    }

    infoCharSequence = TextUtils.concat(infoCharSequence, "\nSupported Commands=\n")

    val playerCommands: Player.Commands = browser.availableCommands
    MediaIntToString.playerCommandMap.forEach { (command, string) ->
      if (playerCommands.contains(command)) {
        infoCharSequence = TextUtils.concat(infoCharSequence, string, "\n")
      }
    }

    val sessionCommands: SessionCommands = browser.availableSessionCommands
    MediaIntToString.sessionCommandMap.forEach { (command, string) ->
      if (sessionCommands.contains(command)) {
        infoCharSequence = TextUtils.concat(infoCharSequence, string, "\n")
      }
    }

    mediaInfoText.text = infoCharSequence
  }

  private fun updateMediaMetadataView(mediaMetadata: MediaMetadata) {
    mediaTitleView.text = mediaMetadata.title ?: "Title metadata empty"
    mediaArtistView.text = mediaMetadata.artist ?: "Artist metadata empty"
    mediaAlbumView.text = mediaMetadata.albumTitle ?: "Album title metadata empty"

    bitmapLoader.loadBitmapFromMetadata(mediaMetadata)?.let {
      Futures.addCallback(
        it,
        object : FutureCallback<Bitmap> {
          override fun onSuccess(result: Bitmap?) {
            mediaAlbumArtView.setImageBitmap(result)
          }

          override fun onFailure(t: Throwable) {
            mediaAlbumArtView.setImageResource(R.drawable.ic_album_black_24dp)
            t.message?.let { msg -> Log.e("BitmapLoader", msg, t) }
          }
        },
        ContextCompat.getMainExecutor(this),
      )
    } ?: mediaAlbumArtView.setImageResource(R.drawable.ic_album_black_24dp)
  }

  private fun setupToolbar() {
    val actionBar: ActionBar? = supportActionBar
    if (actionBar != null) {
      val toolbarIcon = BitmapUtils.createToolbarIcon(resources, mediaAppDetails.icon)
      with(actionBar) {
        setIcon(BitmapDrawable(resources, toolbarIcon))
        title = mediaAppDetails.appName
      }
    }
  }
}
