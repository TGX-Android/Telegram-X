package org.thunderdog.challegram.util;

import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ForceTouchEntityList {
  private final List<ForceTouchView.ForceTouchEntity> list;

  public ForceTouchEntityList (int initialCapacity) {
    this.list = new ArrayList<>(initialCapacity);
  }

  public int indexOf (ForceTouchView.ForceTouchEntity string) {
    return list.indexOf(string);
  }

  public ForceTouchEntityList (ForceTouchView.ForceTouchEntity[] copy) {
    this.list = Arrays.asList(copy);
  }

  public void append (ForceTouchView.ForceTouchEntity entity) {
    list.add(entity);
  }

  public ForceTouchView.ForceTouchEntity[] get () {
    return list.toArray(new ForceTouchView.ForceTouchEntity[0]);
  }

  public boolean isEmpty () {
    return list.isEmpty();
  }
}
