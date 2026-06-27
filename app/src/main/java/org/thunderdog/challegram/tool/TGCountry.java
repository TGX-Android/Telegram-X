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
 * File created on 29/04/2015 at 15:21
 */
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Locale;

import me.vkryl.core.StringUtils;

@SuppressWarnings(value = "SpellCheckingInspection")
public class TGCountry {
  public static class Country {
    public final String number, code, name;

    public Country (String number, String code, String name) {
      this.number = number;
      this.code = code;
      this.name = name;
    }
  }
  
  private static TGCountry instance;

  public static TGCountry instance () {
    if (instance == null) {
      instance = new TGCountry();
    }
    return instance;
  }

  private final Country USA = new Country("1","US","USA");
  private final Country ANONYMOUS = new Country("888", "XXX", Lang.getString(R.string.AnonymousNumbers));

  private final Country[] countryList = {
    ANONYMOUS,
    new Country("93", "AF", "Afghanistan"),
    new Country("355", "AL", "Albania"),
    new Country("213", "DZ", "Algeria"),
    new Country("21", "DZ", "Algeria"),
    new Country("1684", "AS", "American Samoa"),
    new Country("376", "AD", "Andorra"),
    new Country("244", "AO", "Angola"),
    new Country("1264", "AI", "Anguilla"),
    new Country("1268", "AG", "Antigua and Barbuda"),
    new Country("54", "AR", "Argentina"),
    new Country("374", "AM", "Armenia"),
    new Country("297", "AW", "Aruba"),
    new Country("61", "AU", "Australia"),
    new Country("672", "AU", "Australia"),
    new Country("43", "AT", "Austria"),
    new Country("994", "AZ", "Azerbaijan"),
    new Country("1242", "BS", "Bahamas"),
    new Country("973", "BH", "Bahrain"),
    new Country("880", "BD", "Bangladesh"),
    new Country("1246", "BB", "Barbados"),
    new Country("375", "BY", "Belarus"),
    new Country("32", "BE", "Belgium"),
    new Country("501", "BZ", "Belize"),
    new Country("229", "BJ", "Benin"),
    new Country("1441", "BM", "Bermuda"),
    new Country("975", "BT", "Bhutan"),
    new Country("591", "BO", "Bolivia"),
    new Country("1721", "SX", "Bonaire, Sint Eustatius and Saba"),
    new Country("387", "BA", "Bosnia and Herzegovina"),
    new Country("267", "BW", "Botswana"),
    new Country("55", "BR", "Brazil"),
    new Country("1284", "VG", "British Virgin Islands"),
    new Country("673", "BN", "Brunei Darussalam"),
    new Country("359", "BG", "Bulgaria"),
    new Country("226", "BF", "Burkina Faso"),
    new Country("257", "BI", "Burundi"),
    new Country("855", "KH", "Cambodia"),
    new Country("237", "CM", "Cameroon"),
    new Country("1", "CAN", "Canada"),
    new Country("238", "CV", "Cape Verde"),
    new Country("1345", "KY", "Cayman Islands"),
    new Country("236", "CF", "Central African Republic"),
    new Country("235", "TD", "Chad"),
    new Country("56", "CL", "Chile"),
    new Country("86", "CN", "China"),
    new Country("57", "CO", "Colombia"),
    new Country("269", "KM", "Comoros"),
    new Country("242", "CG", "Congo"),
    new Country("243", "CD", "Congo, Democratic Republic"),
    new Country("682", "CK", "Cook Islands"),
    new Country("506", "CR", "Costa Rica"),
    new Country("385", "HR", "Croatia"),
    new Country("53", "CU", "Cuba"),
    new Country("5999", "CW", "Curaçao"),
    new Country("357", "CY", "Cyprus"),
    new Country("420", "CZ", "Czech Republic"),
    new Country("225", "CI", "Côte d`Ivoire"),
    new Country("45", "DK", "Denmark"),
    new Country("253", "DJ", "Djibouti"),
    new Country("1767", "DM", "Dominica"),
    new Country("1849", "DO", "Dominican Republic"),
    new Country("1809", "DO", "Dominican Republic"),
    new Country("1829", "DO", "Dominican Republic"),
    new Country("670", "TL", "East Timor"),
    new Country("593", "EC", "Ecuador"),
    new Country("20", "EG", "Egypt"),
    new Country("503", "SV", "El Salvador"),
    new Country("240", "GQ", "Equatorial Guinea"),
    new Country("291", "ER", "Eritrea"),
    new Country("372", "EE", "Estonia"),
    new Country("251", "ET", "Ethiopia"),
    new Country("500", "FK", "Falkland Islands"),
    new Country("298", "FO", "Faroe Islands"),
    new Country("679", "FJ", "Fiji"),
    new Country("358", "FI", "Finland"),
    new Country("33", "FR", "France"),
    new Country("594", "GF", "French Guiana"),
    new Country("689", "PF", "French Polynesia"),
    new Country("241", "GA", "Gabon"),
    new Country("220", "GM", "Gambia"),
    new Country("995", "GE", "Georgia"),
    new Country("49", "DE", "Germany"),
    new Country("233", "GH", "Ghana"),
    new Country("350", "GI", "Gibraltar"),
    new Country("30", "GR", "Greece"),
    new Country("299", "GL", "Greenland"),
    new Country("1473", "GD", "Grenada"),
    new Country("590", "GP", "Guadeloupe"),
    new Country("1671", "GU", "Guam"),
    new Country("502", "GT", "Guatemala"),
    new Country("224", "GN", "Guinea"),
    new Country("245", "GW", "Guinea-Bissau"),
    new Country("592", "GY", "Guyana"),
    new Country("509", "HT", "Haiti"),
    new Country("504", "HN", "Honduras"),
    new Country("852", "HK", "Hong Kong"),
    new Country("36", "HU", "Hungary"),
    new Country("354", "IS", "Iceland"),
    new Country("91", "IN", "India"),
    new Country("62", "ID", "Indonesia"),
    new Country("98", "IR", "Iran"),
    new Country("964", "IQ", "Iraq"),
    new Country("353", "IE", "Ireland"),
    new Country("972", "IL", "Israel"),
    new Country("39", "IT", "Italy"),
    new Country("1876", "JM", "Jamaica"),
    new Country("81", "JP", "Japan"),
    new Country("962", "JO", "Jordan"),
    new Country("77", "KZ", "Kazakhstan"),
    new Country("254", "KE", "Kenya"),
    new Country("686", "KI", "Kiribati"),
    new Country("965", "KW", "Kuwait"),
    new Country("996", "KG", "Kyrgyzstan"),
    new Country("856", "LA", "Laos"),
    new Country("371", "LV", "Latvia"),
    new Country("961", "LB", "Lebanon"),
    new Country("266", "LS", "Lesotho"),
    new Country("231", "LR", "Liberia"),
    new Country("218", "LY", "Libya"),
    new Country("423", "LI", "Liechtenstein"),
    new Country("370", "LT", "Lithuania"),
    new Country("352", "LU", "Luxembourg"),
    new Country("853", "MO", "Macau"),
    new Country("389", "MK", "Macedonia"),
    new Country("261", "MG", "Madagascar"),
    new Country("265", "MW", "Malawi"),
    new Country("60", "MY", "Malaysia"),
    new Country("960", "MV", "Maldives"),
    new Country("223", "ML", "Mali"),
    new Country("356", "MT", "Malta"),
    new Country("692", "MH", "Marshall Islands"),
    new Country("596", "MQ", "Martinique"),
    new Country("222", "MR", "Mauritania"),
    new Country("230", "MU", "Mauritius"),
    new Country("52", "MX", "Mexico"),
    new Country("691", "FM", "Micronesia"),
    new Country("373", "MD", "Moldova"),
    new Country("377", "MC", "Monaco"),
    new Country("976", "MN", "Mongolia"),
    new Country("382", "ME", "Montenegro"),
    new Country("1664", "MS", "Montserrat"),
    new Country("212", "MA", "Morocco"),
    new Country("258", "MZ", "Mozambique"),
    new Country("95", "MM", "Myanmar"),
    new Country("264", "NA", "Namibia"),
    new Country("674", "NR", "Nauru"),
    new Country("977", "NP", "Nepal"),
    new Country("31", "NL", "Netherlands"),
    new Country("687", "NC", "New Caledonia"),
    new Country("64", "NZ", "New Zealand"),
    new Country("505", "NI", "Nicaragua"),
    new Country("227", "NE", "Niger"),
    new Country("234", "NG", "Nigeria"),
    new Country("683", "NU", "Niue"),
    new Country("6723", "NF", "Norfolk Island"),
    new Country("850", "KP", "North Korea"),
    new Country("1670", "MP", "Northern Mariana Islands"),
    new Country("47", "NO", "Norway"),
    new Country("968", "OM", "Oman"),
    new Country("92", "PK", "Pakistan"),
    new Country("680", "PW", "Palau"),
    new Country("970", "PS", "Palestine"),
    new Country("507", "PA", "Panama"),
    new Country("675", "PG", "Papua New Guinea"),
    new Country("595", "PY", "Paraguay"),
    new Country("51", "PE", "Peru"),
    new Country("63", "PH", "Philippines"),
    new Country("48", "PL", "Poland"),
    new Country("351", "PT", "Portugal"),
    new Country("1939", "PR", "Puerto Rico"),
    new Country("1787", "PR", "Puerto Rico"),
    new Country("974", "QA", "Qatar"),
    new Country("40", "RO", "Romania"),
    new Country("7", "RU", "Russia"),
    new Country("250", "RW", "Rwanda"),
    new Country("262", "RE", "Réunion"),
    new Country("247", "SH", "Saint Helena"),
    new Country("290", "SH", "Saint Helena"),
    new Country("1869", "KN", "Saint Kitts and Nevis"),
    new Country("1758", "LC", "Saint Lucia"),
    new Country("508", "PM", "Saint Pierre and Miquelon"),
    new Country("1784", "VC", "Saint Vincent and the Grenadines"),
    new Country("685", "WS", "Samoa"),
    new Country("378", "SM", "San Marino"),
    new Country("966", "SA", "Saudi Arabia"),
    new Country("221", "SN", "Senegal"),
    new Country("381", "RS", "Serbia"),
    new Country("248", "SC", "Seychelles"),
    new Country("232", "SL", "Sierra Leone"),
    new Country("65", "SG", "Singapore"),
    new Country("599", "BQ", "Sint Maarten"),
    new Country("421", "SK", "Slovakia"),
    new Country("386", "SI", "Slovenia"),
    new Country("677", "SB", "Solomon Islands"),
    new Country("252", "SO", "Somalia"),
    new Country("27", "ZA", "South Africa"),
    new Country("82", "KR", "South Korea"),
    new Country("211", "SS", "South Sudan"),
    new Country("34", "ES", "Spain"),
    new Country("94", "LK", "Sri Lanka"),
    new Country("249", "SD", "Sudan"),
    new Country("597", "SR", "Suriname"),
    new Country("268", "SZ", "Swaziland"),
    new Country("46", "SE", "Sweden"),
    new Country("41", "CH", "Switzerland"),
    new Country("963", "SY", "Syria"),
    new Country("239", "ST", "São Tomé and Príncipe"),
    new Country("886", "TW", "Taiwan"),
    new Country("992", "TJ", "Tajikistan"),
    new Country("255", "TZ", "Tanzania"),
    new Country("66", "TH", "Thailand"),
    new Country("228", "TG", "Togo"),
    new Country("690", "TK", "Tokelau"),
    new Country("676", "TO", "Tonga"),
    new Country("1868", "TT", "Trinidad and Tobago"),
    new Country("216", "TN", "Tunisia"),
    new Country("90", "TR", "Turkey"),
    new Country("993", "TM", "Turkmenistan"),
    new Country("1649", "TC", "Turks and Caicos Islands"),
    new Country("688", "TV", "Tuvalu"),
    new Country("1340", "VI", "US Virgin Islands"),
    USA,
    new Country("256", "UG", "Uganda"),
    new Country("380", "UA", "Ukraine"),
    new Country("971", "AE", "United Arab Emirates"),
    new Country("44", "GB", "United Kingdom"),
    new Country("246", "IO", "United Kingdom"),
    new Country("598", "UY", "Uruguay"),
    new Country("998", "UZ", "Uzbekistan"),
    new Country("678", "VU", "Vanuatu"),
    new Country("58", "VE", "Venezuela"),
    new Country("84", "VN", "Vietnam"),
    new Country("681", "WF", "Wallis and Futuna"),
    new Country("967", "YE", "Yemen"),
    new Country("260", "ZM", "Zambia"),
    new Country("263", "ZW", "Zimbabwe")
  };

  private TGCountry () { }

  public String[] getNumber (Tdlib tdlib) {
    try {
      if (tdlib.hasAuthPhoneNumber()) {
        String code = tdlib.authPhoneCode();
        String number = tdlib.authPhoneNumber();
        String formatted = Strings.formatPhone("+" + code + number);
        return new String[] {code, formatted.substring(code.length() + 1)};
      }
      TelephonyManager manager = (TelephonyManager) UI.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);
      if (manager == null) {
        return null;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // FIXME
        return null;
      }
      String rawNumber = manager.getLine1Number();
      if (rawNumber == null || rawNumber.isEmpty()) {
        return null;
      }
      String number = Strings.getNumber(rawNumber);
      if (number.isEmpty()) {
        return null;
      }

      String countryISO = manager.getSimCountryIso();
      Country country = find(countryISO);

      if (country == null) {
        return null;
      }

      return new String[] { country.number, number.substring(country.number.length())};
    } catch (Throwable t) {
      Log.w("Cannot getLine1Number", t);
    }
    return null;
  }

  public Country find (String countryCode) {
    for (Country country : countryList) {
      if (country.code.equals(countryCode)) {
        return country;
      }
    }
    return null;
  }

  public String getCurrentCode () {
    TelephonyManager manager = (TelephonyManager) UI.getContext().getSystemService(Context.TELEPHONY_SERVICE);
    if (manager == null)
      return getLocaleCode();

    String code = manager.getSimCountryIso();

    if (StringUtils.isEmpty(code))
      return getLocaleCode();

    return code.toUpperCase();
  }

  public String getLocaleCode () {
    String locale = Locale.getDefault().getCountry();
    if (locale == null || locale.length() == 0) {
      locale = Locale.getDefault().getISO3Country();
    }
    if (locale != null) {
      if (locale.length() > 2) {
        locale = locale.substring(0, 2);
      }
      return locale.toUpperCase();
    }
    return null;
  }

  public Country getCurrent () {
    String code = getCurrentCode();
    if (code == null)
      return null;
    return find(code);
  }

  public Country get (String phoneCode) {
    if (StringUtils.isEmpty(phoneCode)) {
      return null;
    }

    if (USA.number.equals(phoneCode)) {
      return USA;
    }

    int guess = -1;
    int i = 0;

    for (Country country : countryList) {
      if (country.number.equals(phoneCode)) {
        return country;
      }
      if (country.number.indexOf(phoneCode) == 0) {
        guess = i;
      }
      i++;
    }

    if (guess != -1) {
      return countryList[guess];
    }

    return null;
  }

  public Country[] getAll () {
    return countryList;
  }
}
