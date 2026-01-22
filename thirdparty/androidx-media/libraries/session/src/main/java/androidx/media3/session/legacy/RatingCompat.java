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
package androidx.media3.session.legacy;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.annotation.SuppressLint;
import android.media.Rating;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A class to encapsulate rating information used as content metadata. A rating is defined by its
 * rating style (see {@link #RATING_HEART}, {@link #RATING_THUMB_UP_DOWN}, {@link #RATING_3_STARS},
 * {@link #RATING_4_STARS}, {@link #RATING_5_STARS} or {@link #RATING_PERCENTAGE}) and the actual
 * rating value (which may be defined as "unrated"), both of which are defined when the rating
 * instance is constructed through one of the factory methods.
 */
@UnstableApi
@RestrictTo(LIBRARY)
@SuppressLint("BanParcelableUsage")
public final class RatingCompat implements Parcelable {
  private static final String TAG = "Rating";

  /** */
  @IntDef({
    RATING_NONE,
    RATING_HEART,
    RATING_THUMB_UP_DOWN,
    RATING_3_STARS,
    RATING_4_STARS,
    RATING_5_STARS,
    RATING_PERCENTAGE
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Style {}

  /** */
  @IntDef({RATING_3_STARS, RATING_4_STARS, RATING_5_STARS})
  @Retention(RetentionPolicy.SOURCE)
  public @interface StarStyle {}

  /**
   * Indicates a rating style is not supported. A Rating will never have this type, but can be used
   * by other classes to indicate they do not support Rating.
   */
  public static final int RATING_NONE = 0;

  /**
   * A rating style with a single degree of rating, "heart" vs "no heart". Can be used to indicate
   * the content referred to is a favorite (or not).
   */
  public static final int RATING_HEART = 1;

  /** A rating style for "thumb up" vs "thumb down". */
  public static final int RATING_THUMB_UP_DOWN = 2;

  /** A rating style with 0 to 3 stars. */
  public static final int RATING_3_STARS = 3;

  /** A rating style with 0 to 4 stars. */
  public static final int RATING_4_STARS = 4;

  /** A rating style with 0 to 5 stars. */
  public static final int RATING_5_STARS = 5;

  /** A rating style expressed as a percentage. */
  public static final int RATING_PERCENTAGE = 6;

  private static final float RATING_NOT_RATED = -1.0f;

  private final int mRatingStyle;
  private final float mRatingValue;

  @Nullable private Object mRatingObj; // framework Rating object

  RatingCompat(@Style int ratingStyle, float rating) {
    mRatingStyle = ratingStyle;
    mRatingValue = rating;
  }

  @Override
  public String toString() {
    return "Rating:style="
        + mRatingStyle
        + " rating="
        + (mRatingValue < 0.0f ? "unrated" : String.valueOf(mRatingValue));
  }

  @Override
  public int describeContents() {
    return mRatingStyle;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(mRatingStyle);
    dest.writeFloat(mRatingValue);
  }

  public static final Parcelable.Creator<RatingCompat> CREATOR =
      new Parcelable.Creator<RatingCompat>() {
        /**
         * Rebuilds a Rating previously stored with writeToParcel().
         *
         * @param p Parcel object to read the Rating from
         * @return a new Rating created from the data in the parcel
         */
        @Override
        public RatingCompat createFromParcel(Parcel p) {
          return new RatingCompat(p.readInt(), p.readFloat());
        }

        @Override
        public RatingCompat[] newArray(int size) {
          return new RatingCompat[size];
        }
      };

  /**
   * Return a Rating instance with no rating. Create and return a new Rating instance with no rating
   * known for the given rating style.
   *
   * @param ratingStyle one of {@link #RATING_HEART}, {@link #RATING_THUMB_UP_DOWN}, {@link
   *     #RATING_3_STARS}, {@link #RATING_4_STARS}, {@link #RATING_5_STARS}, or {@link
   *     #RATING_PERCENTAGE}.
   * @return null if an invalid rating style is passed, a new Rating instance otherwise.
   */
  @Nullable
  public static RatingCompat newUnratedRating(@Style int ratingStyle) {
    switch (ratingStyle) {
      case RATING_HEART:
      case RATING_THUMB_UP_DOWN:
      case RATING_3_STARS:
      case RATING_4_STARS:
      case RATING_5_STARS:
      case RATING_PERCENTAGE:
        return new RatingCompat(ratingStyle, RATING_NOT_RATED);
      default:
        return null;
    }
  }

  /**
   * Return a Rating instance with a heart-based rating. Create and return a new Rating instance
   * with a rating style of {@link #RATING_HEART}, and a heart-based rating.
   *
   * @param hasHeart true for a "heart selected" rating, false for "heart unselected".
   * @return a new Rating instance.
   */
  public static RatingCompat newHeartRating(boolean hasHeart) {
    return new RatingCompat(RATING_HEART, hasHeart ? 1.0f : 0.0f);
  }

  /**
   * Return a Rating instance with a thumb-based rating. Create and return a new Rating instance
   * with a {@link #RATING_THUMB_UP_DOWN} rating style, and a "thumb up" or "thumb down" rating.
   *
   * @param thumbIsUp true for a "thumb up" rating, false for "thumb down".
   * @return a new Rating instance.
   */
  public static RatingCompat newThumbRating(boolean thumbIsUp) {
    return new RatingCompat(RATING_THUMB_UP_DOWN, thumbIsUp ? 1.0f : 0.0f);
  }

  /**
   * Return a Rating instance with a star-based rating. Create and return a new Rating instance with
   * one of the star-base rating styles and the given integer or fractional number of stars. Non
   * integer values can for instance be used to represent an average rating value, which might not
   * be an integer number of stars.
   *
   * @param starRatingStyle one of {@link #RATING_3_STARS}, {@link #RATING_4_STARS}, {@link
   *     #RATING_5_STARS}.
   * @param starRating a number ranging from 0.0f to 3.0f, 4.0f or 5.0f according to the rating
   *     style.
   * @return null if the rating style is invalid, or the rating is out of range, a new Rating
   *     instance otherwise.
   */
  @Nullable
  public static RatingCompat newStarRating(@StarStyle int starRatingStyle, float starRating) {
    float maxRating = -1.0f;
    switch (starRatingStyle) {
      case RATING_3_STARS:
        maxRating = 3.0f;
        break;
      case RATING_4_STARS:
        maxRating = 4.0f;
        break;
      case RATING_5_STARS:
        maxRating = 5.0f;
        break;
      default:
        Log.e(TAG, "Invalid rating style (" + starRatingStyle + ") for a star rating");
        return null;
    }
    if ((starRating < 0.0f) || (starRating > maxRating)) {
      Log.e(TAG, "Trying to set out of range star-based rating");
      return null;
    }
    return new RatingCompat(starRatingStyle, starRating);
  }

  /**
   * Return a Rating instance with a percentage-based rating. Create and return a new Rating
   * instance with a {@link #RATING_PERCENTAGE} rating style, and a rating of the given percentage.
   *
   * @param percent the value of the rating
   * @return null if the rating is out of range, a new Rating instance otherwise.
   */
  @Nullable
  public static RatingCompat newPercentageRating(float percent) {
    if ((percent < 0.0f) || (percent > 100.0f)) {
      Log.e(TAG, "Invalid percentage-based rating value");
      return null;
    } else {
      return new RatingCompat(RATING_PERCENTAGE, percent);
    }
  }

  /**
   * Return whether there is a rating value available.
   *
   * @return true if the instance was not created with {@link #newUnratedRating(int)}.
   */
  public boolean isRated() {
    return mRatingValue >= 0.0f;
  }

  /**
   * Return the rating style.
   *
   * @return one of {@link #RATING_HEART}, {@link #RATING_THUMB_UP_DOWN}, {@link #RATING_3_STARS},
   *     {@link #RATING_4_STARS}, {@link #RATING_5_STARS}, or {@link #RATING_PERCENTAGE}.
   */
  @Style
  public int getRatingStyle() {
    return mRatingStyle;
  }

  /**
   * Return whether the rating is "heart selected".
   *
   * @return true if the rating is "heart selected", false if the rating is "heart unselected", if
   *     the rating style is not {@link #RATING_HEART} or if it is unrated.
   */
  public boolean hasHeart() {
    if (mRatingStyle != RATING_HEART) {
      return false;
    } else {
      return (mRatingValue == 1.0f);
    }
  }

  /**
   * Return whether the rating is "thumb up".
   *
   * @return true if the rating is "thumb up", false if the rating is "thumb down", if the rating
   *     style is not {@link #RATING_THUMB_UP_DOWN} or if it is unrated.
   */
  public boolean isThumbUp() {
    if (mRatingStyle != RATING_THUMB_UP_DOWN) {
      return false;
    } else {
      return (mRatingValue == 1.0f);
    }
  }

  /**
   * Return the star-based rating value.
   *
   * @return a rating value greater or equal to 0.0f, or a negative value if the rating style is not
   *     star-based, or if it is unrated.
   */
  public float getStarRating() {
    switch (mRatingStyle) {
      case RATING_3_STARS:
      case RATING_4_STARS:
      case RATING_5_STARS:
        if (isRated()) {
          return mRatingValue;
        }
      // fall through
      default:
        return -1.0f;
    }
  }

  /**
   * Return the percentage-based rating value.
   *
   * @return a rating value greater or equal to 0.0f, or a negative value if the rating style is not
   *     percentage-based, or if it is unrated.
   */
  public float getPercentRating() {
    if ((mRatingStyle != RATING_PERCENTAGE) || !isRated()) {
      return -1.0f;
    } else {
      return mRatingValue;
    }
  }

  /**
   * Creates an instance from a framework {@link android.media.Rating} object.
   *
   * <p>This method is only supported on API 19+.
   *
   * @param ratingObj A {@link android.media.Rating} object, or null if none.
   * @return An equivalent {@link RatingCompat} object, or null if none.
   */
  @Nullable
  @SuppressLint("WrongConstant")
  public static RatingCompat fromRating(@Nullable Object ratingObj) {
    if (ratingObj != null) {
      final int ratingStyle = ((Rating) ratingObj).getRatingStyle();
      final RatingCompat rating;
      if (((Rating) ratingObj).isRated()) {
        switch (ratingStyle) {
          case Rating.RATING_HEART:
            rating = newHeartRating(((Rating) ratingObj).hasHeart());
            break;
          case Rating.RATING_THUMB_UP_DOWN:
            rating = newThumbRating(((Rating) ratingObj).isThumbUp());
            break;
          case Rating.RATING_3_STARS:
          case Rating.RATING_4_STARS:
          case Rating.RATING_5_STARS:
            rating = newStarRating(ratingStyle, ((Rating) ratingObj).getStarRating());
            break;
          case Rating.RATING_PERCENTAGE:
            rating = newPercentageRating(((Rating) ratingObj).getPercentRating());
            break;
          default:
            return null;
        }
      } else {
        rating = newUnratedRating(ratingStyle);
      }
      checkNotNull(rating).mRatingObj = ratingObj;
      return rating;
    } else {
      return null;
    }
  }

  /**
   * Gets the underlying framework {@link android.media.Rating} object.
   *
   * <p>This method is only supported on API 19+.
   *
   * @return An equivalent {@link android.media.Rating} object, or null if none.
   */
  @Nullable
  public Object getRating() {
    if (mRatingObj == null) {
      if (isRated()) {
        switch (mRatingStyle) {
          case RATING_HEART:
            mRatingObj = Rating.newHeartRating(hasHeart());
            break;
          case RATING_THUMB_UP_DOWN:
            mRatingObj = Rating.newThumbRating(isThumbUp());
            break;
          case RATING_3_STARS:
          case RATING_4_STARS:
          case RATING_5_STARS:
            mRatingObj = Rating.newStarRating(mRatingStyle, getStarRating());
            break;
          case RATING_PERCENTAGE:
            mRatingObj = Rating.newPercentageRating(getPercentRating());
            break;
          default:
            return null;
        }
      } else {
        mRatingObj = Rating.newUnratedRating(mRatingStyle);
      }
    }
    return mRatingObj;
  }
}
