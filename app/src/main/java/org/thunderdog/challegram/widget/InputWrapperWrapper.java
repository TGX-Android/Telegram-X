/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/07/2018
 */
package org.thunderdog.challegram.widget;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;

import org.thunderdog.challegram.Log;

import me.vkryl.core.StringUtils;

public class InputWrapperWrapper {
  private static final String CONTENT_MIME_TYPES = "android.support.v13.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES";

  private static final String COMMIT_CONTENT_ACTION =
    "android.support.v13.view.inputmethod.InputConnectionCompat.COMMIT_CONTENT";
  private static final String COMMIT_CONTENT_CONTENT_URI_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_URI";
  private static final String COMMIT_CONTENT_DESCRIPTION_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_DESCRIPTION";
  private static final String COMMIT_CONTENT_LINK_URI_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_LINK_URI";
  private static final String COMMIT_CONTENT_OPTS_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_OPTS";
  private static final String COMMIT_CONTENT_FLAGS_KEY =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_FLAGS";
  private static final String COMMIT_CONTENT_RESULT_RECEIVER =
    "android.support.v13.view.inputmethod.InputConnectionCompat.CONTENT_RESULT_RECEIVER";

  private static final String NEW_COMMIT_CONTENT_ACTION =
    "androidx.core.view.inputmethod.InputConnectionCompat.COMMIT_CONTENT";
  private static final String NEW_COMMIT_CONTENT_CONTENT_URI_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_URI";
  private static final String NEW_COMMIT_CONTENT_DESCRIPTION_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_DESCRIPTION";
  private static final String NEW_COMMIT_CONTENT_LINK_URI_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_LINK_URI";
  private static final String NEW_COMMIT_CONTENT_OPTS_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_OPTS";
  private static final String NEW_COMMIT_CONTENT_FLAGS_KEY =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_FLAGS";
  private static final String NEW_COMMIT_CONTENT_RESULT_RECEIVER =
    "androidx.core.view.inputmethod.InputConnectionCompat.CONTENT_RESULT_RECEIVER";

  public static void setContentMimeTypes (EditorInfo editorInfo, String[] mimeTypes) {
    EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
      if (editorInfo.extras == null) {
        editorInfo.extras = new Bundle();
      }
      editorInfo.extras.putStringArray(CONTENT_MIME_TYPES, mimeTypes);
    }
  }

  public static InputConnection createWrapper (InputConnection ic, EditorInfo editorInfo, InputConnectionCompat.OnCommitContentListener callback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
    } else {
      InputConnection wrapper = InputConnectionCompat.createWrapper(ic, editorInfo, callback);
      return new InputConnectionWrapper(ic, false) {
        @Override
        public boolean performPrivateCommand (String action, Bundle data) {
          if (StringUtils.equalsOrBothEmpty(action, COMMIT_CONTENT_ACTION) && data != null) {
            try {
              ResultReceiver resultReceiver = data.getParcelable(COMMIT_CONTENT_RESULT_RECEIVER);
              final Uri contentUri = data.getParcelable(COMMIT_CONTENT_CONTENT_URI_KEY);
              final ClipDescription description = data.getParcelable(
                COMMIT_CONTENT_DESCRIPTION_KEY);
              final Uri linkUri = data.getParcelable(COMMIT_CONTENT_LINK_URI_KEY);
              final int flags = data.getInt(COMMIT_CONTENT_FLAGS_KEY);
              final Bundle opts = data.getParcelable(COMMIT_CONTENT_OPTS_KEY);

              data.putParcelable(NEW_COMMIT_CONTENT_RESULT_RECEIVER, resultReceiver);
              data.putParcelable(NEW_COMMIT_CONTENT_CONTENT_URI_KEY, contentUri);
              data.putParcelable(NEW_COMMIT_CONTENT_DESCRIPTION_KEY, description);
              data.putParcelable(NEW_COMMIT_CONTENT_LINK_URI_KEY, linkUri);
              data.putInt(NEW_COMMIT_CONTENT_FLAGS_KEY, flags);
              data.putParcelable(NEW_COMMIT_CONTENT_OPTS_KEY, opts);

              action = NEW_COMMIT_CONTENT_ACTION;
            } catch (Throwable t) {
              Log.w("Cannot patch event", t);
            }
          }
          return wrapper.performPrivateCommand(action, data);
        }
      };
    }
  }
}
