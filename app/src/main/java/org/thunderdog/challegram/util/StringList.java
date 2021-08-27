/**
 * File created on 11/02/16 at 23:56
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.StringUtils;

public class StringList {
  private final List<String> list;

  public StringList (int initialCapacity) {
    this.list = new ArrayList<>(initialCapacity);
  }

  public int indexOf (String string) {
    return list.indexOf(string);
  }

  public StringList (String[] copy) {
    this.list = Arrays.asList(copy);
  }

  public void append (@StringRes int x) {
    append(Lang.getString(x));
  }

  public void append (String string) {
    list.add(string);
  }

  public static String[] asArray (@StringRes int... stringResources) {
    String[] strings = new String[stringResources.length];
    int i = 0;
    for (int stringRes : stringResources) {
      strings[i++] = Lang.getString(stringRes);
    }
    return strings;
  }

  public String join (@NonNull String separator, @NonNull String separatorForLastItem) {
    return StringUtils.join(separator, separatorForLastItem, list);
  }

  public String[] get () {
    return list.toArray(new String[0]);
  }

  public boolean isEmpty () {
    return list.isEmpty();
  }
}
