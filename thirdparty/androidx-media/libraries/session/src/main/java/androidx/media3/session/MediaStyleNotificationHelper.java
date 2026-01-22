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
package androidx.media3.session;

import static android.Manifest.permission.MEDIA_CONTENT_CONTROL;
import static androidx.core.app.NotificationCompat.COLOR_DEFAULT;
import static androidx.media3.common.util.Assertions.checkArgument;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationBuilderWithBuilderAccessor;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Class containing media specfic {@link androidx.core.app.NotificationCompat.Style styles} that you
 * can use with {@link androidx.core.app.NotificationCompat.Builder#setStyle}.
 */
@UnstableApi
public class MediaStyleNotificationHelper {

  public static final String EXTRA_MEDIA3_SESSION = "androidx.media3.session";

  private MediaStyleNotificationHelper() {}

  /**
   * Notification style for media playback notifications.
   *
   * <p>In the expanded form, up to 5 {@link androidx.core.app.NotificationCompat.Action actions}
   * specified with {@link androidx.core.app.NotificationCompat.Builder #addAction(int,
   * CharSequence, PendingIntent) addAction} will be shown as icon-only pushbuttons, suitable for
   * transport controls. The Bitmap given to {@link androidx.core.app.NotificationCompat.Builder
   * #setLargeIcon(android.graphics.Bitmap) setLargeIcon()} will be treated as album artwork.
   *
   * <p>Unlike the other styles provided here, MediaStyle can also modify the standard-size content
   * view; by providing action indices to {@link #setShowActionsInCompactView(int...)} you can
   * promote up to 3 actions to be displayed in the standard view alongside the usual content.
   *
   * <p>Notifications created with MediaStyle will have their category set to {@link
   * androidx.core.app.NotificationCompat#CATEGORY_TRANSPORT CATEGORY_TRANSPORT} unless you set a
   * different category using {@link
   * androidx.core.app.NotificationCompat.Builder#setCategory(String) setCategory()}.
   *
   * <p>Finally, the System UI can identify this as a notification representing an active media
   * session and respond accordingly (by showing album artwork in the lockscreen, for example).
   *
   * <p>To use this style with your Notification, feed it to {@link
   * androidx.core.app.NotificationCompat.Builder#setStyle} like so:
   *
   * <pre class="prettyprint">
   * Notification noti = new NotificationCompat.Builder()
   *     .setSmallIcon(androidx.media3.R.drawable.media3_notification_small_icon)
   *     .setContentTitle(&quot;Track title&quot;)
   *     .setContentText(&quot;Artist - Album&quot;)
   *     .setLargeIcon(albumArtBitmap)
   *     .setStyle(<b>new MediaStyleNotificationHelper.MediaStyle(mySession)</b>)
   *     .build();
   * </pre>
   *
   * @see Notification#bigContentView
   */
  public static class MediaStyle extends androidx.core.app.NotificationCompat.Style {

    /**
     * Extracts a {@link SessionToken} from the extra values in the {@link MediaStyle} {@link
     * Notification notification}.
     *
     * @param notification The notification to extract a {@link MediaSession} from.
     * @return The {@link SessionToken} in the {@code notification} if it contains, null otherwise.
     */
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public static SessionToken getSessionToken(Notification notification) {
      Bundle extras = androidx.core.app.NotificationCompat.getExtras(notification);
      if (extras == null) {
        return null;
      }
      Bundle sessionTokenBundle = extras.getBundle(EXTRA_MEDIA3_SESSION);
      return sessionTokenBundle == null ? null : SessionToken.fromBundle(sessionTokenBundle);
    }

    private static final int MAX_MEDIA_BUTTONS_IN_COMPACT = 3;
    private static final int MAX_MEDIA_BUTTONS = 5;

    /* package */ final MediaSession session;

    /* package */ int @NullableType [] actionsToShowInCompact;
    /* package */ @MonotonicNonNull CharSequence remoteDeviceName;
    /* package */ int remoteDeviceIconRes;
    @Nullable /* package */ PendingIntent remoteDeviceIntent;

    /**
     * Creates a new instance with a {@link MediaSession} to this Notification to provide additional
     * playback information and control to the SystemUI.
     */
    public MediaStyle(MediaSession session) {
      this.session = session;
    }

    /**
     * Requests up to 3 actions (by index in the order of addition) to be shown in the compact
     * notification view.
     *
     * @param actions the indices of the actions to show in the compact notification view
     */
    @CanIgnoreReturnValue
    public MediaStyle setShowActionsInCompactView(int... actions) {
      actionsToShowInCompact = actions;
      return this;
    }

    /**
     * @deprecated This method is a no-op and usages can be safely removed. There is no recommended
     *     alternative (it was previously only operational on API &lt; 21).
     */
    @CanIgnoreReturnValue
    @Deprecated
    @SuppressWarnings("unused")
    public MediaStyle setShowCancelButton(boolean show) {
      return this;
    }

    /**
     * @deprecated This method is a no-op and usages can be safely removed. There is no recommended
     *     alternative (it was previously only operational on API &lt; 21).
     */
    @CanIgnoreReturnValue
    @Deprecated
    @SuppressWarnings("unused")
    public MediaStyle setCancelButtonIntent(PendingIntent pendingIntent) {
      return this;
    }

    /**
     * For media notifications associated with playback on a remote device, provide device
     * information that will replace the default values for the output switcher chip on the media
     * control, as well as an intent to use when the output switcher chip is tapped, on devices
     * where this is supported.
     *
     * <p>Most apps should integrate with {@link android.media.MediaRouter2} instead. This method is
     * only intended for system applications to provide information and/or functionality that would
     * otherwise be unavailable to the default output switcher because the media originated on a
     * remote device.
     *
     * <p>Also note that this method is a no-op when running on API 33 or lower.
     *
     * @param deviceName The name of the remote device to display.
     * @param iconResource Icon resource, of size 12, representing the device.
     * @param chipIntent PendingIntent to send when the output switcher is tapped. May be {@code
     *     null}, in which case the output switcher will be disabled. This intent should open an
     *     {@link android.app.Activity} or it will be ignored.
     */
    @CanIgnoreReturnValue
    @RequiresPermission(MEDIA_CONTENT_CONTROL)
    public MediaStyle setRemotePlaybackInfo(
        CharSequence deviceName,
        @DrawableRes int iconResource,
        @Nullable PendingIntent chipIntent) {
      checkArgument(deviceName != null);
      this.remoteDeviceName = deviceName;
      this.remoteDeviceIconRes = iconResource;
      this.remoteDeviceIntent = chipIntent;
      return this;
    }

    @Override
    public void apply(NotificationBuilderWithBuilderAccessor builder) {
      // Avoid ambiguity with androidx.media3.session.Session.Token
      @SuppressWarnings("UnnecessarilyFullyQualified")
      Notification.MediaStyle style =
          new Notification.MediaStyle()
              .setMediaSession(
                  (android.media.session.MediaSession.Token)
                      session.getSessionCompat().getSessionToken().getToken());
      if (actionsToShowInCompact != null) {
        style.setShowActionsInCompactView(actionsToShowInCompact);
      }
      if (Util.SDK_INT >= 34 && remoteDeviceName != null) {
        Api34Impl.setRemotePlaybackInfo(
            style, remoteDeviceName, remoteDeviceIconRes, remoteDeviceIntent);
        builder.getBuilder().setStyle(style);
      } else {
        builder.getBuilder().setStyle(style);
        Bundle bundle = new Bundle();
        bundle.putBundle(EXTRA_MEDIA3_SESSION, session.getToken().toBundle());
        builder.getBuilder().addExtras(bundle);
      }
    }

    /* package */ RemoteViews generateContentView() {
      RemoteViews view =
          applyStandardTemplate(
              /* showSmallIcon= */ false, getContentViewLayoutResource(), /* fitIn1U= */ true);

      final int numActions = mBuilder.mActions.size();
      if (actionsToShowInCompact != null) {
        int[] actions = actionsToShowInCompact;
        final int numActionsInCompact = Math.min(actions.length, MAX_MEDIA_BUTTONS_IN_COMPACT);
        view.removeAllViews(androidx.media3.session.R.id.media_actions);
        if (numActionsInCompact > 0) {
          for (int i = 0; i < numActionsInCompact; i++) {
            if (i >= numActions) {
              throw new IllegalArgumentException(
                  String.format(
                      "setShowActionsInCompactView: action %d out of bounds (max %d)",
                      i, numActions - 1));
            }

            final androidx.core.app.NotificationCompat.Action action =
                mBuilder.mActions.get(actions[i]);
            final RemoteViews button = generateMediaActionButton(action);
            view.addView(androidx.media3.session.R.id.media_actions, button);
          }
        }
      }
      view.setViewVisibility(androidx.media3.session.R.id.end_padder, View.VISIBLE);
      return view;
    }

    private RemoteViews generateMediaActionButton(
        androidx.core.app.NotificationCompat.Action action) {
      final boolean tombstone = (action.getActionIntent() == null);
      RemoteViews button =
          new RemoteViews(
              mBuilder.mContext.getPackageName(),
              androidx.media3.session.R.layout.media3_notification_media_action);
      IconCompat iconCompat = action.getIconCompat();
      if (iconCompat != null) {
        button.setImageViewResource(androidx.media3.session.R.id.action0, iconCompat.getResId());
      }
      if (!tombstone) {
        button.setOnClickPendingIntent(
            androidx.media3.session.R.id.action0, action.getActionIntent());
      }
      button.setContentDescription(androidx.media3.session.R.id.action0, action.getTitle());
      return button;
    }

    /* package */ int getContentViewLayoutResource() {
      return androidx.media3.session.R.layout.media3_notification_template_media;
    }

    /* package */ RemoteViews generateBigContentView() {
      final int actionCount = Math.min(mBuilder.mActions.size(), MAX_MEDIA_BUTTONS);
      RemoteViews big =
          applyStandardTemplate(
              /* showSmallIcon= */ false,
              getBigContentViewLayoutResource(actionCount),
              /* fitIn1U= */ false);

      big.removeAllViews(androidx.media3.session.R.id.media_actions);
      if (actionCount > 0) {
        for (int i = 0; i < actionCount; i++) {
          final RemoteViews button = generateMediaActionButton(mBuilder.mActions.get(i));
          big.addView(androidx.media3.session.R.id.media_actions, button);
        }
      }
      return big;
    }

    /* package */ int getBigContentViewLayoutResource(int actionCount) {
      return actionCount <= 3
          ? androidx.media3.session.R.layout.media3_notification_template_big_media_narrow
          : androidx.media3.session.R.layout.media3_notification_template_big_media;
    }
  }

  /**
   * Notification style for media custom views that are decorated by the system.
   *
   * <p>Instead of providing a media notification that is completely custom, a developer can set
   * this style and still obtain system decorations like the notification header with the expand
   * affordance and actions.
   *
   * <p>Use {@link androidx.core.app.NotificationCompat.Builder #setCustomContentView(RemoteViews)},
   * {@link androidx.core.app.NotificationCompat.Builder #setCustomBigContentView(RemoteViews)} and
   * {@link androidx.core.app.NotificationCompat.Builder #setCustomHeadsUpContentView(RemoteViews)}
   * to set the corresponding custom views to display.
   *
   * <p>To use this style with your Notification, feed it to {@link
   * androidx.core.app.NotificationCompat.Builder
   * #setStyle(androidx.core.app.NotificationCompat.Style)} like so:
   *
   * <pre class="prettyprint">
   * Notification noti = new NotificationCompat.Builder()
   *     .setSmallIcon(androidx.media3.R.drawable.media3_notification_small_icon)
   *     .setLargeIcon(albumArtBitmap))
   *     .setCustomContentView(contentView)
   *     .setStyle(<b>new NotificationCompat.DecoratedMediaCustomViewStyle()</b>
   *          .setMediaSession(mySession))
   *     .build();
   * </pre>
   *
   * <p>If you are using this style, consider using the corresponding styles like {@link
   * androidx.media3.session.R.style#TextAppearance_Compat_Notification_Media} or {@link
   * androidx.media3.session.R.style#TextAppearance_Compat_Notification_Title_Media} in your custom
   * views in order to get the correct styling on each platform version.
   *
   * @see androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
   * @see MediaStyle
   */
  public static class DecoratedMediaCustomViewStyle extends MediaStyle {

    public DecoratedMediaCustomViewStyle(MediaSession session) {
      super(session);
    }

    @Override
    public void apply(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT < 24) {
        super.apply(builder);
        return;
      }
      Notification.DecoratedMediaCustomViewStyle style =
          Api24Impl.createDecoratedMediaCustomViewStyle();
      if (actionsToShowInCompact != null) {
        style.setShowActionsInCompactView(actionsToShowInCompact);
      }
      if (Util.SDK_INT >= 34 && remoteDeviceName != null) {
        Api34Impl.setRemotePlaybackInfo(
            style, remoteDeviceName, remoteDeviceIconRes, remoteDeviceIntent);
        builder.getBuilder().setStyle(style);
      } else {
        builder.getBuilder().setStyle(style);
        Bundle bundle = new Bundle();
        bundle.putBundle(EXTRA_MEDIA3_SESSION, session.getToken().toBundle());
        builder.getBuilder().addExtras(bundle);
      }
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom content view required
        return null;
      }
      boolean hasContentView = mBuilder.getContentView() != null;
      boolean createCustomContent = hasContentView || mBuilder.getBigContentView() != null;
      if (createCustomContent) {
        RemoteViews contentView = generateContentView();
        if (hasContentView) {
          buildIntoRemoteViews(contentView, mBuilder.getContentView());
        }
        setBackgroundColor(contentView);
        return contentView;
      }
      return null;
    }

    @Override
    /* package */ int getContentViewLayoutResource() {
      return mBuilder.getContentView() != null
          ? androidx.media3.session.R.layout.media3_notification_template_media_custom
          : super.getContentViewLayoutResource();
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeBigContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom big content view required
        return null;
      }
      RemoteViews innerView =
          mBuilder.getBigContentView() != null
              ? mBuilder.getBigContentView()
              : mBuilder.getContentView();
      if (innerView == null) {
        // No expandable notification
        return null;
      }
      RemoteViews bigContentView = generateBigContentView();
      buildIntoRemoteViews(bigContentView, innerView);
      setBackgroundColor(bigContentView);
      return bigContentView;
    }

    @Override
    /* package */ int getBigContentViewLayoutResource(int actionCount) {
      return actionCount <= 3
          ? androidx.media3.session.R.layout.media3_notification_template_big_media_narrow_custom
          : androidx.media3.session.R.layout.media3_notification_template_big_media_custom;
    }

    @Override
    @Nullable
    @SuppressWarnings("nullness:override.return") // NotificationCompat doesn't annotate @Nullable
    public RemoteViews makeHeadsUpContentView(NotificationBuilderWithBuilderAccessor builder) {
      if (Util.SDK_INT >= 24) {
        // No custom heads up content view required
        return null;
      }
      RemoteViews innerView =
          mBuilder.getHeadsUpContentView() != null
              ? mBuilder.getHeadsUpContentView()
              : mBuilder.getContentView();
      if (innerView == null) {
        // No expandable notification
        return null;
      }
      RemoteViews headsUpContentView = generateBigContentView();
      buildIntoRemoteViews(headsUpContentView, innerView);
      setBackgroundColor(headsUpContentView);
      return headsUpContentView;
    }

    private void setBackgroundColor(RemoteViews views) {
      int color =
          mBuilder.getColor() != COLOR_DEFAULT
              ? mBuilder.getColor()
              : mBuilder
                  .mContext
                  .getResources()
                  .getColor(
                      androidx.media3.session.R.color
                          .notification_material_background_media_default_color);
      views.setInt(
          androidx.media3.session.R.id.status_bar_latest_event_content,
          "setBackgroundColor",
          color);
    }
  }

  @RequiresApi(24)
  private static class Api24Impl {
    private Api24Impl() {}

    public static Notification.DecoratedMediaCustomViewStyle createDecoratedMediaCustomViewStyle() {
      return new Notification.DecoratedMediaCustomViewStyle();
    }
  }

  @RequiresApi(34)
  private static class Api34Impl {

    private Api34Impl() {}

    // MEDIA_CONTENT_CONTROL permission is required by setRemotePlaybackInfo
    @CanIgnoreReturnValue
    @SuppressLint({"MissingPermission"})
    public static Notification.MediaStyle setRemotePlaybackInfo(
        Notification.MediaStyle style,
        CharSequence remoteDeviceName,
        @DrawableRes int remoteDeviceIconRes,
        @Nullable PendingIntent remoteDeviceIntent) {
      style.setRemotePlaybackInfo(remoteDeviceName, remoteDeviceIconRes, remoteDeviceIntent);
      return style;
    }
  }
}
