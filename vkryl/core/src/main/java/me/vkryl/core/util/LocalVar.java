package me.vkryl.core.util;

import android.os.Looper;

import java.util.HashMap;

/**
 * Date: 12/19/17
 * Author: default
 */

public class LocalVar<T> {
  private T mainVar;
  private HashMap<Looper, T> looperMap;
  private HashMap<Thread, T> threadMap;

  public LocalVar () { }

  public T getMain () {
    return mainVar;
  }

  public void setMain (T var) {
    this.mainVar = var;
  }

  public T get () {
    Looper looper = Looper.myLooper();
    if (looper == Looper.getMainLooper()) {
      return mainVar;
    } else if (looper != null) {
      T result;
      synchronized (this) {
        result = looperMap != null ? looperMap.get(looper) : null;
      }
      return result;
    } else {
      Thread thread = Thread.currentThread();
      T result;
      synchronized (this) {
        result = threadMap != null ? threadMap.get(thread) : null;
      }
      return result;
    }
  }

  public void set (T value) {
    Looper looper = Looper.myLooper();
    if (looper == Looper.getMainLooper()) {
      mainVar = value;
    } else if (value == null) {
      if (looper != null) {
        synchronized (this) {
          if (looperMap != null) {
            looperMap.remove(looper);
          }
        }
      } else {
        Thread thread = Thread.currentThread();
        synchronized (this) {
          if (threadMap != null) {
            threadMap.remove(thread);
          }
        }
      }
    } else {
      if (looper != null) {
        synchronized (this) {
          if (looperMap == null) {
            looperMap = new HashMap<>();
          }
          looperMap.put(looper, value);
        }
      } else {
        Thread thread = Thread.currentThread();
        synchronized (this) {
          if (threadMap == null) {
            threadMap = new HashMap<>();
          }
          threadMap.put(thread, value);
        }
      }
    }
  }
}
