/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.test.session.common;

/** Constants for calling MediaBrowserServiceCompat methods. */
public class MediaBrowserServiceCompatConstants {

  public static final String TEST_CONNECT_REJECTED = "testConnect_rejected";
  public static final String TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE =
      "testOnChildrenChanged_subscribeAndUnsubscribe";
  public static final String TEST_GET_LIBRARY_ROOT = "getLibraryRoot_correctExtraKeyAndValue";
  public static final String TEST_GET_CHILDREN = "getChildren_correctMetadataExtras";
  public static final String TEST_GET_CHILDREN_WITH_NULL_LIST =
      "onChildrenChanged_withNullChildrenListInLegacyService_convertedToSessionError";
  public static final String TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL =
      "onChildrenChanged_cacheChildrenOfSubscribeCall_serviceCalledOnceOnly";
  public static final String TEST_GET_CHILDREN_FATAL_AUTHENTICATION_ERROR =
      "getLibraryRoot_fatalAuthenticationError_receivesPlaybackException";
  public static final String TEST_GET_CHILDREN_NON_FATAL_AUTHENTICATION_ERROR =
      "getLibraryRoot_nonFatalAuthenticationError_receivesPlaybackException";
  public static final String TEST_SEND_CUSTOM_COMMAND = "sendCustomCommand";
  public static final String TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS =
      "getLibraryRoot_withBrowseActions";
  public static final String TEST_SUBSCRIBE_THEN_REJECT_ON_LOAD_CHILDREN =
      "subscribe_thenRejectOnLoadChildren";

  private MediaBrowserServiceCompatConstants() {}
}
