/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/01/2019
 */
package org.thunderdog.challegram.sync;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class StubProvider extends ContentProvider {
  /*
   * Always return true, indicating that the
   * provider loaded correctly.
   */
  @Override
  public boolean onCreate() {
    return true;
  }
  /*
   * Return no type for MIME type
   */
  @Override
  public String getType(Uri uri) {
    return null;
  }
  /*
   * query() always returns no results
   *
   */
  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    return null;
  }
  /*
   * insert() always returns null (no URI)
   */
  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }
  /*
   * delete() always returns "no rows affected" (0)
   */
  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }
  /*
   * update() always returns "no rows affected" (0)
   */
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }
}
