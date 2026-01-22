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

import android.text.Editable
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PercentageRating
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.StarRating
import androidx.media3.common.ThumbRating
import androidx.media3.session.MediaController

/** Helper class to manage displaying and setting different kinds of media ratings. */
class RatingHelper(private val rootView: ViewGroup, private val mediaController: MediaController) {
  private var ratingUiHelper: RatingUiHelper?
  init {
    ratingUiHelper = ratingUiHelperFor(rootView, mediaController.mediaMetadata)

    val listener: Player.Listener =
      object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) =
          updateRating(mediaMetadata)
      }
    mediaController.addListener(listener)
    updateRating(mediaController.mediaMetadata)
  }

  fun updateRating(mediaMetadata: MediaMetadata) {
    val rating: Rating? = mediaMetadata.userRating ?: mediaMetadata.overallRating
    if (rating != null) {
      if (ratingUiHelper == null) {
        ratingUiHelper = ratingUiHelperFor(rootView, mediaMetadata)
      }
      ratingUiHelper?.setRating(rating)
    } else {
      ratingUiHelper?.let { it.setRating(it.unrated()) }
    }
  }

  private fun ratingUiHelperFor(
    viewGroup: ViewGroup,
    mediaMetadata: MediaMetadata
  ): RatingUiHelper? {
    val rating: Rating? = mediaMetadata.userRating ?: mediaMetadata.overallRating
    viewGroup.visibility = View.VISIBLE
    return when (rating) {
      is ThumbRating -> RatingUiHelper.Thumbs(viewGroup, mediaController)
      is HeartRating -> RatingUiHelper.Heart(viewGroup, mediaController)
      is PercentageRating -> RatingUiHelper.Percentage(viewGroup, mediaController)
      is StarRating ->
        when (rating.maxStars) {
          3 -> RatingUiHelper.Stars3(viewGroup, mediaController)
          4 -> RatingUiHelper.Stars4(viewGroup, mediaController)
          5 -> RatingUiHelper.Stars5(viewGroup, mediaController)
          else -> {
            viewGroup.visibility = View.GONE
            null
          }
        }
      else -> {
        viewGroup.visibility = View.GONE
        null
      }
    }
  }
}

@SuppressWarnings("FutureReturnValueIgnored")
private abstract class RatingUiHelper(
  private val rootView: ViewGroup,
  mediaController: MediaController
) {
  private var currentRating: Rating = unrated()

  init {
    for (i in 0 until rootView.childCount) {
      val ratingView: View = rootView.getChildAt(i)
      ratingView.visibility = if (visible(ratingView.id)) View.VISIBLE else View.GONE

      if (ratingView !is Editable) {
        ratingView.setOnClickListener { view ->
          val newRating: Rating = ratingFor(view.id, currentRating)
          val mediaItem: MediaItem? = mediaController.currentMediaItem
          if (mediaItem != null && !TextUtils.isEmpty(mediaItem.mediaId)) {
            mediaController.setRating(mediaItem.mediaId, newRating)
          } else {
            mediaController.setRating(newRating)
          }
        }
      }
    }
  }

  /** Returns whether the given view is enabled with the current rating */
  abstract fun enabled(@IdRes viewId: Int, rating: Rating): Boolean

  /**
   * Returns whether the given view is visible for the type of rating. For example, a thumbs up/down
   * rating will not display stars or heart. And a 4-star rating will not display the fifth star.
   */
  abstract fun visible(@IdRes viewId: Int): Boolean

  /** Returns the rating that should be set when the given view is tapped. */
  abstract fun ratingFor(@IdRes viewId: Int, currentRating: Rating): Rating

  /** Returns unrated rating of the current rating type. */
  abstract fun unrated(): Rating

  fun setRating(rating: Rating) {
    for (i in 0 until rootView.childCount) {
      val view: View = rootView.getChildAt(i)
      if (view is ImageView) {
        val tint: Int =
          if (enabled(view.id, rating)) R.color.colorPrimary else R.color.colorInactive
        DrawableCompat.setTint(view.drawable, ContextCompat.getColor(rootView.context, tint))
      } else {
        view.isEnabled = enabled(view.id, rating)
      }
    }
    currentRating = rating
  }

  open class Stars3(viewGroup: ViewGroup, controller: MediaController) :
    RatingUiHelper(viewGroup, controller) {
    override fun enabled(viewId: Int, rating: Rating): Boolean {
      if (rating is StarRating) {
        val starRating: Float = rating.starRating
        return when (viewId) {
          R.id.rating_star_1 -> starRating >= 1.0f
          R.id.rating_star_2 -> starRating >= 2.0f
          R.id.rating_star_3 -> starRating >= 3.0f
          else -> false
        }
      }
      return false
    }

    override fun visible(viewId: Int): Boolean =
      viewId == R.id.rating_star_1 || viewId == R.id.rating_star_2 || viewId == R.id.rating_star_3

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating =
      when (viewId) {
        R.id.rating_star_1 -> StarRating(3, 1.0f)
        R.id.rating_star_2 -> StarRating(3, 2.0f)
        R.id.rating_star_3 -> StarRating(3, 3.0f)
        else -> StarRating(3)
      }

    override fun unrated(): Rating = StarRating(3)
  }

  open class Stars4(viewGroup: ViewGroup, controller: MediaController) :
    Stars3(viewGroup, controller) {
    override fun enabled(viewId: Int, rating: Rating): Boolean {
      if (rating is StarRating && viewId == R.id.rating_star_4) {
        return rating.starRating >= 4.0f
      }
      return super.enabled(viewId, rating)
    }

    override fun visible(viewId: Int): Boolean =
      viewId == R.id.rating_star_4 || super.visible(viewId)

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating =
      when (viewId) {
        R.id.rating_star_1 -> StarRating(4, 1.0f)
        R.id.rating_star_2 -> StarRating(4, 2.0f)
        R.id.rating_star_3 -> StarRating(4, 3.0f)
        R.id.rating_star_4 -> StarRating(4, 4.0f)
        else -> StarRating(4)
      }

    override fun unrated(): Rating = StarRating(4)
  }

  class Stars5(viewGroup: ViewGroup, controller: MediaController) : Stars4(viewGroup, controller) {
    override fun enabled(viewId: Int, rating: Rating): Boolean {
      if (rating is StarRating && viewId == R.id.rating_star_5) {
        return rating.starRating >= 5.0f
      }
      return super.enabled(viewId, rating)
    }

    override fun visible(viewId: Int): Boolean =
      viewId == R.id.rating_star_5 || super.visible(viewId)

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating =
      when (viewId) {
        R.id.rating_star_1 -> StarRating(5, 1.0f)
        R.id.rating_star_2 -> StarRating(5, 2.0f)
        R.id.rating_star_3 -> StarRating(5, 3.0f)
        R.id.rating_star_4 -> StarRating(5, 4.0f)
        R.id.rating_star_5 -> StarRating(5, 5.0f)
        else -> StarRating(5)
      }

    override fun unrated(): Rating = StarRating(5)
  }

  class Thumbs(viewGroup: ViewGroup, controller: MediaController) :
    RatingUiHelper(viewGroup, controller) {
    override fun enabled(viewId: Int, rating: Rating): Boolean {
      if (rating is ThumbRating) {
        if (rating.isThumbsUp && viewId == R.id.rating_thumb_up) return true
        if (isThumbDown(rating) && viewId == R.id.rating_thumb_down) return true
      }
      return false
    }

    override fun visible(viewId: Int): Boolean =
      viewId == R.id.rating_thumb_up || viewId == R.id.rating_thumb_down

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating {
      // User tapped on current thumb rating, so reset the rating.
      if (enabled(viewId, currentRating)) return ThumbRating()
      return when (viewId) {
        R.id.rating_thumb_up -> ThumbRating(true)
        R.id.rating_thumb_down -> ThumbRating(false)
        else -> ThumbRating()
      }
    }

    override fun unrated(): Rating = ThumbRating()

    private fun isThumbDown(rating: ThumbRating): Boolean = rating.isRated && !rating.isThumbsUp
  }

  class Heart(viewGroup: ViewGroup, controller: MediaController) :
    RatingUiHelper(viewGroup, controller) {
    override fun enabled(viewId: Int, rating: Rating): Boolean =
      rating is HeartRating && rating.isHeart

    override fun visible(viewId: Int): Boolean = viewId == R.id.rating_heart

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating =
      if (currentRating is HeartRating) HeartRating(!currentRating.isHeart) else HeartRating()

    override fun unrated(): Rating = HeartRating()
  }

  class Percentage(viewGroup: ViewGroup, controller: MediaController) :
    RatingUiHelper(viewGroup, controller) {
    private val percentageEditText: EditText = viewGroup.findViewById(R.id.rating_percentage)

    override fun enabled(viewId: Int, rating: Rating): Boolean = true

    override fun visible(viewId: Int): Boolean =
      viewId == R.id.rating_percentage || viewId == R.id.rating_percentage_set

    override fun ratingFor(viewId: Int, currentRating: Rating): Rating {
      val percentage: Int = Integer.parseInt(percentageEditText.text.toString(), 10)
      return PercentageRating(percentage / 100.0f)
    }

    override fun unrated(): Rating = PercentageRating()
  }
}
