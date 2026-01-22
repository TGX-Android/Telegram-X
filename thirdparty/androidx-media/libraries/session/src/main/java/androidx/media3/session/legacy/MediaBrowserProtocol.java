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

import android.os.Bundle;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;

/** Defines the communication protocol for media browsers and media browser services. */
@UnstableApi
@RestrictTo(LIBRARY)
public class MediaBrowserProtocol {

  public static final String DATA_CALLBACK_TOKEN = "data_callback_token";
  public static final String DATA_CALLING_UID = "data_calling_uid";
  public static final String DATA_CALLING_PID = "data_calling_pid";
  public static final String DATA_MEDIA_ITEM_ID = "data_media_item_id";
  public static final String DATA_MEDIA_ITEM_LIST = "data_media_item_list";
  public static final String DATA_MEDIA_SESSION_TOKEN = "data_media_session_token";
  public static final String DATA_OPTIONS = "data_options";
  public static final String DATA_NOTIFY_CHILDREN_CHANGED_OPTIONS =
      "data_notify_children_changed_options";
  public static final String DATA_PACKAGE_NAME = "data_package_name";
  public static final String DATA_RESULT_RECEIVER = "data_result_receiver";
  public static final String DATA_ROOT_HINTS = "data_root_hints";
  public static final String DATA_SEARCH_EXTRAS = "data_search_extras";
  public static final String DATA_SEARCH_QUERY = "data_search_query";
  public static final String DATA_CUSTOM_ACTION = "data_custom_action";
  public static final String DATA_CUSTOM_ACTION_EXTRAS = "data_custom_action_extras";

  public static final String EXTRA_CLIENT_VERSION = "extra_client_version";
  public static final String EXTRA_CALLING_PID = "extra_calling_pid";
  public static final String EXTRA_SERVICE_VERSION = "extra_service_version";
  public static final String EXTRA_MESSENGER_BINDER = "extra_messenger";
  public static final String EXTRA_SESSION_BINDER = "extra_session_binder";

  /**
   * MediaBrowserCompat will check the version of the connected MediaBrowserServiceCompat, and it
   * will not send messages if they are introduced in the higher version of the
   * MediaBrowserServiceCompat.
   */
  public static final int SERVICE_VERSION_1 = 1;

  /**
   * To prevent ClassNotFoundException from Parcelables, MediaBrowser(Service)Compat tries to avoid
   * using framework code as much as possible (b/62648808). For backward compatibility, service v2
   * is introduced so that the browser can distinguish whether the service supports subscribing
   * through compat code.
   */
  public static final int SERVICE_VERSION_2 = 2;

  public static final int SERVICE_VERSION_CURRENT = SERVICE_VERSION_2;

  /*
   * Messages sent from the media browser service compat to the media browser compat.
   * (Compat implementation for IMediaBrowserServiceCallbacks)
   * DO NOT RENUMBER THESE!
   */

  /**
   * (service v1) Sent after {@link MediaBrowserCompat#connect()} when the request has successfully
   * completed. - arg1 : The service version - data DATA_MEDIA_ITEM_ID : A string for the root media
   * item id DATA_MEDIA_SESSION_TOKEN : Media session token DATA_ROOT_HINTS : An optional root hints
   * bundle of service-specific arguments
   */
  public static final int SERVICE_MSG_ON_CONNECT = 1;

  /**
   * (service v1) Sent after {@link MediaBrowserCompat#connect()} when the connection to the media
   * browser failed. - arg1 : service version
   */
  public static final int SERVICE_MSG_ON_CONNECT_FAILED = 2;

  /**
   * (service v1) Sent when the list of children is loaded or updated. - arg1 : The service version
   * - data DATA_MEDIA_ITEM_ID : A string for the parent media item id DATA_MEDIA_ITEM_LIST : An
   * array list for the media item children DATA_OPTIONS : A bundle of service-specific arguments
   * sent from the media browse to the media browser service DATA_NOTIFY_CHILDREN_CHANGED_OPTIONS :
   * A bundle of service-specific arguments sent from the media browser service to the media browser
   * by calling {@link MediaBrowserServiceCompat#notifyChildrenChanged(String, Bundle)}
   */
  public static final int SERVICE_MSG_ON_LOAD_CHILDREN = 3;

  /**
   * MediaBrowserServiceCompat will check the version of the MediaBrowserCompat, and it will not
   * send messages if they are introduced in the higher version of the MediaBrowserCompat.
   */
  public static final int CLIENT_VERSION_1 = 1;

  public static final int CLIENT_VERSION_CURRENT = CLIENT_VERSION_1;

  /*
   * Messages sent from the media browser compat to the media browser service compat.
   * (Compat implementation for IMediaBrowserService)
   * DO NOT RENUMBER THESE!
   */

  /**
   * (client v1) Sent to connect to the media browse service compat. - arg1 : The client version -
   * data DATA_PACKAGE_NAME : A string for the package name of MediaBrowserCompat DATA_ROOT_HINTS :
   * An optional root hints bundle of service-specific arguments - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_CONNECT = 1;

  /**
   * (client v1) Sent to disconnect from the media browse service compat. - arg1 : The client
   * version - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_DISCONNECT = 2;

  /**
   * (client v1) Sent to subscribe for changes to the children of the specified media id. - arg1 :
   * The client version - data DATA_MEDIA_ITEM_ID : A string for a media item id DATA_OPTIONS : A
   * bundle of service-specific arguments sent from the media browser to the media browser service
   * DATA_CALLBACK_TOKEN : An IBinder of service-specific arguments sent from the media browser to
   * the media browser service - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_ADD_SUBSCRIPTION = 3;

  /**
   * (client v1) Sent to unsubscribe for changes to the children of the specified media id. - arg1 :
   * The client version - data DATA_MEDIA_ITEM_ID : A string for a media item id DATA_CALLBACK_TOKEN
   * : An IBinder of service-specific arguments sent from the media browser to the media browser
   * service - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_REMOVE_SUBSCRIPTION = 4;

  /**
   * (client v1) Sent to retrieve a specific media item from the connected service. - arg1 : The
   * client version - data DATA_MEDIA_ITEM_ID : A string for a media item id DATA_RESULT_RECEIVER :
   * Result receiver to get the result - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_GET_MEDIA_ITEM = 5;

  /**
   * (client v1) Sent to register the client messenger - arg1 : The client version - data
   * DATA_ROOT_HINTS : An optional root hints bundle of service-specific arguments - replyTo :
   * Callback messenger
   */
  public static final int CLIENT_MSG_REGISTER_CALLBACK_MESSENGER = 6;

  /**
   * (client v1) Sent to unregister the client messenger - arg1 : The client version - replyTo :
   * Callback messenger
   */
  public static final int CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER = 7;

  /**
   * (client v1) Sent to retrieve a specific media item from the connected service. - arg1 : The
   * client version - data DATA_SEARCH_QUERY : A string for search query that contains keywords
   * separated by space. DATA_SEARCH_EXTRAS : A bundle of service-specific arguments to send to the
   * media browser service. DATA_RESULT_RECEIVER : Result receiver to get the result. - replyTo :
   * Callback messenger
   */
  public static final int CLIENT_MSG_SEARCH = 8;

  /**
   * (client v1) Sent to request a custom action from the media browser. - arg1 : The client version
   * - data DATA_CUSTOM_ACTION : A string for the custom action. DATA_CUSTOM_ACTION_EXTRAS : A
   * bundle of service-specific arguments to send to the media browser service. DATA_RESULT_RECEIVER
   * : Result receiver to get the result. - replyTo : Callback messenger
   */
  public static final int CLIENT_MSG_SEND_CUSTOM_ACTION = 9;

  private MediaBrowserProtocol() {}
}
