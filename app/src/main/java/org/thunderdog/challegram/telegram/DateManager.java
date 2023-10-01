package org.thunderdog.challegram.telegram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

import java.util.Calendar;

import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;

public class DateManager {
  private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      String action = intent.getAction();
      if (!StringUtils.isEmpty(action)) {
        DateUtils.clearCache();
        switch (action) {
          case Intent.ACTION_TIMEZONE_CHANGED:
            notifyTimeZoneChanged();
            UI.execute(DateManager.this::checkCurrentDate);
            break;
          case Intent.ACTION_DATE_CHANGED:
            UI.execute(DateManager.this::checkCurrentDate);
            break;
          case Intent.ACTION_TIME_CHANGED:
            notifyTimeChanged();
            UI.execute(DateManager.this::checkCurrentDate);
            break;
        }
      }
    }
  };

  private final TdlibManager context;
  private final ReferenceList<DateChangeListener> dateListeners;

  public DateManager (TdlibManager context) {
    this.context = context;
    this.dateListeners = new ReferenceList<>(true, true, (list, isFull) -> {
      UI.post(() ->
        setNeedDateWatcher(isFull)
      );
    });
  }

  // Date change

  public void addListener (@NonNull DateChangeListener listener) {
    this.dateListeners.add(listener);
  }

  public void removeListener (@NonNull DateChangeListener listener) {
    this.dateListeners.remove(listener);
  }

  private void notifyListeners (RunnableData<DateChangeListener> act) {
    UI.execute(() -> {
      for (DateChangeListener listener : dateListeners) {
        act.runWithData(listener);
      }
    });
  }

  private void notifyDateChanged () {
    notifyListeners(DateChangeListener::onDateChanged);
  }

  private void notifyTimeZoneChanged () {
    notifyListeners(DateChangeListener::onTimeZoneChanged);
  }

  private void notifyTimeChanged () {
    notifyListeners(DateChangeListener::onTimeChanged);
  }

  private int currentDayOfYear, currentYear;

  private boolean setCurrentDate () {
    Calendar c = Calendar.getInstance();
    int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
    int year = c.get(Calendar.YEAR);
    if (this.currentDayOfYear != dayOfYear || this.currentYear != year) {
      this.currentDayOfYear = dayOfYear;
      this.currentYear = year;
      return true;
    }
    return false;
  }

  public void checkCurrentDate () {
    if (setCurrentDate()) {
      notifyDateChanged();
    }
  }

  private boolean dateWatcherRegistered;

  private void setNeedDateWatcher (boolean needDateWatcher) {
    if (this.dateWatcherRegistered != needDateWatcher) {
      if (needDateWatcher) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        try {
          UI.getAppContext().registerReceiver(dateChangeReceiver, filter);
          this.dateWatcherRegistered = true;
        } catch (Throwable t) {
          Log.w("Unable to register date change receiver", t);
        }
      } else {
        try {
          UI.getAppContext().unregisterReceiver(dateChangeReceiver);
          this.dateWatcherRegistered = false;
        } catch (Throwable t) {
          Log.i("Unable to unregister date change receiver", t);
        }
      }
    }
  }
}
