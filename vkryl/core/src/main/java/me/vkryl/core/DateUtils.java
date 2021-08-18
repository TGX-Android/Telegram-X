/**
 * File created on 28/04/15 at 18:18
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.core;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.util.LocalVar;

public final class DateUtils {
  private DateUtils () { }

  private static @Nullable LocalVar<Date> now, date;
  private static @Nullable LocalVar<Calendar> nowCalendar, calendar;

  public static Date getNow () {
    if (now == null) {
      synchronized (Date.class) {
        if (now == null) {
          now = new LocalVar<>();
        }
      }
    }
    Date date = now.get();
    if (date == null) {
      now.set(date = new Date());
    } else {
      date.setTime(System.currentTimeMillis());
    }
    return date;
  }

  public static Calendar getNowCalendar () {
    if (nowCalendar == null) {
      synchronized (Date.class) {
        if (nowCalendar == null) {
          nowCalendar = new LocalVar<>();
        }
      }
    }
    Calendar c = nowCalendar.get();
    if (c == null) {
      nowCalendar.set(c = Calendar.getInstance());
    }
    c.setTime(getNow());
    return c;
  }

  public static Calendar calendarInstance (long mills) {
    if (calendar == null) {
      synchronized (Date.class) {
        if (calendar == null) {
          calendar = new LocalVar<>();
        }
      }
    }
    Calendar c = calendar.get();
    if (c == null) {
      calendar.set(c = GregorianCalendar.getInstance());
    }
    c.setTime(dateInstance(mills));
    return c;
  }

  public static Date dateInstance (long millis) {
    if (date == null) {
      synchronized (Date.class) {
        if (date == null) {
          date = new LocalVar<>();
        }
      }
    }
    Date d = date.get();
    if (d == null) {
      // date.set(d = new Date(millis));
      d = new Date(millis);
    } else {
      d.setTime(millis);
    }
    return d;
  }

  public static boolean isSameTimeIgnoringDate (long date1, long date2, TimeUnit unit) {
    Calendar c = calendarInstance(unit.toMillis(date1));
    int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
    int minute = c.get(Calendar.MINUTE);
    int second = c.get(Calendar.SECOND);
    int ms = c.get(Calendar.MILLISECOND);
    c = calendarInstance(unit.toMillis(date2));
    return c.get(Calendar.HOUR_OF_DAY) == hourOfDay && c.get(Calendar.MINUTE) == minute && c.get(Calendar.SECOND) == second && c.get(Calendar.MILLISECOND) == ms;
  }

  public static void resetToStartOfDay (Calendar c) {
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
  }

  public static long getStartOfDay (Calendar c) {
    int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
    int minute = c.get(Calendar.MINUTE);
    int second = c.get(Calendar.SECOND);
    int millisecond = c.get(Calendar.MILLISECOND);
    resetToStartOfDay(c);
    long result = c.getTimeInMillis();
    c.set(Calendar.HOUR_OF_DAY, hourOfDay);
    c.set(Calendar.MINUTE, minute);
    c.set(Calendar.SECOND, second);
    c.set(Calendar.MILLISECOND, millisecond);
    return result;
  }

  public static long getStartOfToday () {
    Calendar c = calendarInstance(System.currentTimeMillis());
    resetToStartOfDay(c);
    return c.getTimeInMillis();
  }

  public static boolean isSameDay (int date1, int date2) {
    Calendar target = calendarInstance(date1 * 1000l);
    int y1 = target.get(Calendar.YEAR);
    int d1 = target.get(Calendar.DAY_OF_YEAR);
    target = calendarInstance(date2 * 1000l);
    return d1 == target.get(Calendar.DAY_OF_YEAR) && y1 == target.get(Calendar.YEAR);
  }

  public static boolean isSameHour (int date1, int date2) {
    Calendar target = calendarInstance(date1 * 1000l);
    int y1 = target.get(Calendar.YEAR);
    int d1 = target.get(Calendar.DAY_OF_YEAR);
    int h1 = target.get(Calendar.HOUR_OF_DAY);
    int m1 = target.get(Calendar.MINUTE);
    target = calendarInstance(date2 * 1000l);
    return m1 / 15 == target.get(Calendar.MINUTE) / 20 && h1 == target.get(Calendar.HOUR_OF_DAY) && d1 == target.get(Calendar.DAY_OF_YEAR) && y1 == target.get(Calendar.YEAR);
  }

  public static boolean isSameMonth (int date1, int date2) {
    Calendar target = calendarInstance(date1 * 1000l);
    int y1 = target.get(Calendar.YEAR);
    int m1 = target.get(Calendar.MONTH);
    target = calendarInstance(date2 * 1000l);
    return m1 == target.get(Calendar.MONTH) && y1 == target.get(Calendar.YEAR);
  }

  public static boolean isThisMonth (int unixTime) {
    Calendar current = getNowCalendar();

    int months = current.get(Calendar.MONTH);
    int years = current.get(Calendar.YEAR);

    Calendar target = calendarInstance(unixTime * 1000l);

    months -= target.get(Calendar.MONTH);
    years -= target.get(Calendar.YEAR);

    return months == 0 && years == 0;
  }

  public static boolean isPastWeek (int unixTime) {
    Calendar current = getNowCalendar();

    int weeks = current.get(Calendar.WEEK_OF_YEAR);
    int years = current.get(Calendar.YEAR);
    int currentDay = current.get(Calendar.DAY_OF_WEEK);

    Calendar target = calendarInstance(unixTime * 1000l);

    weeks -= target.get(Calendar.WEEK_OF_YEAR);
    years -= target.get(Calendar.YEAR);

    return (weeks == 1 || (weeks == 0 && currentDay != Calendar.SUNDAY && target.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)) && years == 0;
  }

  public static boolean isWithinWeek (int unixTime) {
    long nowMs = getStartOfToday();
    Calendar c = calendarInstance((long) unixTime * 1000l);
    resetToStartOfDay(c);
    long unixTimeMs = c.getTimeInMillis();
    long days = TimeUnit.MILLISECONDS.toDays(nowMs - unixTimeMs);
    return days < 7 && days >= 0;
  }

  public static int getDayOfWeek (int unixTime) {
    Calendar target = calendarInstance(unixTime * 1000l);
    return target.get(Calendar.DAY_OF_WEEK);
  }

  public static boolean isToday (long unixTime, TimeUnit unit) {
    Calendar current = getNowCalendar();

    int days = current.get(Calendar.DAY_OF_YEAR);
    int years = current.get(Calendar.YEAR);

    Calendar target = calendarInstance(unit.toMillis(unixTime));

    days -= target.get(Calendar.DAY_OF_YEAR);
    years -= target.get(Calendar.YEAR);

    return days == 0 && years == 0;
  }

  public static boolean isYesterday (long unixTime, TimeUnit unit) {
    Calendar current = getNowCalendar();

    int days = current.get(Calendar.DAY_OF_YEAR);
    int years = current.get(Calendar.YEAR);

    Calendar target = calendarInstance(unit.toMillis(unixTime));

    days -= target.get(Calendar.DAY_OF_YEAR);
    years -= target.get(Calendar.YEAR);

    return days == 1 && years == 0;
  }

  public static boolean isTomorrow (long unixTime, TimeUnit unit) {
    Calendar current = getNowCalendar();

    int days = current.get(Calendar.DAY_OF_YEAR);
    int years = current.get(Calendar.YEAR);

    Calendar target = calendarInstance(unit.toMillis(unixTime));

    days -= target.get(Calendar.DAY_OF_YEAR);
    years -= target.get(Calendar.YEAR);

    return days == -1 && years == 0;
  }

  public static boolean isThisYear (long unixTime, TimeUnit unit) {
    Calendar current = getNowCalendar();

    int year = current.get(Calendar.YEAR);

    Calendar target = calendarInstance(unit.toMillis(unixTime));

    return year == target.get(Calendar.YEAR);
  }

}
