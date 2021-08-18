package me.vkryl.core.solar;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Public interface for getting the various types of sunrise/sunset.
 */
public class SunriseSunsetCalculator {

  private Location location;

  private SolarEventCalculator calculator;

  /**
   * Constructs a new <code>SunriseSunsetCalculator</code> with the given <code>Location</code>
   *
   * @param location
   *            <code>Location</code> object containing the Latitude/Longitude of the location to compute
   *            the sunrise/sunset for.
   * @param timeZoneIdentifier
   *            String identifier for the timezone to compute the sunrise/sunset times in. In the form
   *            "America/New_York". Please see the zi directory under the JDK installation for supported
   *            time zones.
   */
  public SunriseSunsetCalculator(Location location, String timeZoneIdentifier) {
    this.location = location;
    this.calculator = new SolarEventCalculator(location, timeZoneIdentifier);
  }

  /**
   * Constructs a new <code>SunriseSunsetCalculator</code> with the given <code>Location</code>
   *
   * @param location
   *            <code>Location</code> object containing the Latitude/Longitude of the location to compute
   *            the sunrise/sunset for.
   * @param timeZone
   *            timezone to compute the sunrise/sunset times in.
   */
  public SunriseSunsetCalculator(Location location, TimeZone timeZone) {
    this.location = location;
    this.calculator = new SolarEventCalculator(location, timeZone);
  }

  /**
   * Returns the astronomical (108deg) sunrise for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the astronomical sunrise for.
   * @return the astronomical sunrise time in HH:MM (24-hour clock) form.
   */
  public String getAstronomicalSunriseForDate(Calendar date) {
    return calculator.computeSunriseTime(Zenith.ASTRONOMICAL, date);
  }

  /**
   * Returns the astronomical (108deg) sunrise for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the astronomical sunrise for.
   * @return the astronomical sunrise time as a Calendar
   */
  public Calendar getAstronomicalSunriseCalendarForDate(Calendar date) {
    return calculator.computeSunriseCalendar(Zenith.ASTRONOMICAL, date);
  }

  /**
   * Returns the astronomical (108deg) sunset for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the astronomical sunset for.
   * @return the astronomical sunset time in HH:MM (24-hour clock) form.
   */
  public String getAstronomicalSunsetForDate(Calendar date) {
    return calculator.computeSunsetTime(Zenith.ASTRONOMICAL, date);
  }

  /**
   * Returns the astronomical (108deg) sunset for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the astronomical sunset for.
   * @return the astronomical sunset time as a Calendar
   */
  public Calendar getAstronomicalSunsetCalendarForDate(Calendar date) {
    return calculator.computeSunsetCalendar(Zenith.ASTRONOMICAL, date);
  }

  /**
   * Returns the nautical (102deg) sunrise for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the nautical sunrise for.
   * @return the nautical sunrise time in HH:MM (24-hour clock) form.
   */
  public String getNauticalSunriseForDate(Calendar date) {
    return calculator.computeSunriseTime(Zenith.NAUTICAL, date);
  }

  /**
   * Returns the nautical (102deg) sunrise for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the nautical sunrise for.
   * @return the nautical sunrise time as a Calendar
   */
  public Calendar getNauticalSunriseCalendarForDate(Calendar date) {
    return calculator.computeSunriseCalendar(Zenith.NAUTICAL, date);
  }

  /**
   * Returns the nautical (102deg) sunset for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the nautical sunset for.
   * @return the nautical sunset time in HH:MM (24-hour clock) form.
   */
  public String getNauticalSunsetForDate(Calendar date) {
    return calculator.computeSunsetTime(Zenith.NAUTICAL, date);
  }

  /**
   * Returns the nautical (102deg) sunset for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the nautical sunset for.
   * @return the nautical sunset time as a Calendar
   */
  public Calendar getNauticalSunsetCalendarForDate(Calendar date) {
    return calculator.computeSunsetCalendar(Zenith.NAUTICAL, date);
  }

  /**
   * Returns the civil sunrise (twilight, 96deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the civil sunrise for.
   * @return the civil sunrise time in HH:MM (24-hour clock) form.
   */
  public String getCivilSunriseForDate(Calendar date) {
    return calculator.computeSunriseTime(Zenith.CIVIL, date);
  }

  /**
   * Returns the civil sunrise (twilight, 96deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the civil sunrise for.
   * @return the civil sunrise time as a Calendar
   */
  public Calendar getCivilSunriseCalendarForDate(Calendar date) {
    return calculator.computeSunriseCalendar(Zenith.CIVIL, date);
  }

  /**
   * Returns the civil sunset (twilight, 96deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the civil sunset for.
   * @return the civil sunset time in HH:MM (24-hour clock) form.
   */
  public String getCivilSunsetForDate(Calendar date) {
    return calculator.computeSunsetTime(Zenith.CIVIL, date);
  }

  /**
   * Returns the civil sunset (twilight, 96deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the civil sunset for.
   * @return the civil sunset time as a Calendar
   */
  public Calendar getCivilSunsetCalendarForDate(Calendar date) {
    return calculator.computeSunsetCalendar(Zenith.CIVIL, date);
  }

  /**
   * Returns the official sunrise (90deg 50', 90.8333deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunrise for.
   * @return the official sunrise time in HH:MM (24-hour clock) form.
   */
  public String getOfficialSunriseForDate(Calendar date) {
    return calculator.computeSunriseTime(Zenith.OFFICIAL, date);
  }

  /**
   * Returns the official sunrise (90deg 50', 90.8333deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunrise for.
   * @return the official sunrise time as a Calendar
   */
  public Calendar getOfficialSunriseCalendarForDate(Calendar date) {
    return calculator.computeSunriseCalendar(Zenith.OFFICIAL, date);
  }

  /**
   * Returns the official sunrise (90deg 50', 90.8333deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunset for.
   * @return the official sunset time in HH:MM (24-hour clock) form.
   */
  public String getOfficialSunsetForDate(Calendar date) {
    return calculator.computeSunsetTime(Zenith.OFFICIAL, date);
  }

  /**
   * Returns the official sunrise (90deg 50', 90.8333deg) for the given date.
   *
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunset for.
   * @return the official sunset time as a Calendar
   */
  public Calendar getOfficialSunsetCalendarForDate(Calendar date) {
    return calculator.computeSunsetCalendar(Zenith.OFFICIAL, date);
  }

  public static Calendar[] getSunriseSunset (double latitude, double longitude) {
    Calendar now = Calendar.getInstance();
    now.setTimeInMillis(System.currentTimeMillis());
    SolarEventCalculator calculator = new SolarEventCalculator(new Location(latitude, longitude), now.getTimeZone());
    Calendar sunrise = calculator.computeSunriseCalendar(Zenith.OFFICIAL, now);
    Calendar sunset = calculator.computeSunsetCalendar(Zenith.OFFICIAL, now);
    return new Calendar[] {sunrise, sunset};
  }

  /**
   * Computes the sunrise for an arbitrary declination.
   *
   * @param latitude
   * @param longitude
   *            Coordinates for the location to compute the sunrise/sunset for.
   * @param timeZone
   *            timezone to compute the sunrise/sunset times in.
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunset for.
   * @param degrees
   *            Angle under the horizon for which to compute sunrise. For example, "civil sunrise"
   *            corresponds to 6 degrees.
   * @return the requested sunset time as a <code>Calendar</code> object.
   */

  public static Calendar getSunrise(double latitude, double longitude, TimeZone timeZone, Calendar date, double degrees) {
    SolarEventCalculator solarEventCalculator = new SolarEventCalculator(new Location(latitude, longitude), timeZone);
    return solarEventCalculator.computeSunriseCalendar(new Zenith(90 - degrees), date);
  }

  /**
   * Computes the sunset for an arbitrary declination.
   *
   * @param latitude
   * @param longitude
   *            Coordinates for the location to compute the sunrise/sunset for.
   * @param timeZone
   *            timezone to compute the sunrise/sunset times in.
   * @param date
   *            <code>Calendar</code> object containing the date to compute the official sunset for.
   * @param degrees
   *            Angle under the horizon for which to compute sunrise. For example, "civil sunset"
   *            corresponds to 6 degrees.
   * @return the requested sunset time as a <code>Calendar</code> object.
   */

  public static Calendar getSunset(double latitude, double longitude, TimeZone timeZone, Calendar date, double degrees) {
    SolarEventCalculator solarEventCalculator = new SolarEventCalculator(new Location(latitude, longitude), timeZone);
    return solarEventCalculator.computeSunsetCalendar(new Zenith(90 - degrees), date);
  }

  /**
   * Returns the location where the sunrise/sunset is calculated for.
   *
   * @return <code>Location</code> object representing the location of the computed sunrise/sunset.
   */
  public Location getLocation() {
    return location;
  }
}
