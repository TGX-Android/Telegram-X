package org.thunderdog.challegram.sync;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Date: 25/01/2019
 * Author: default
 */
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
