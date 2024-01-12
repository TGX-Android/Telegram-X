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

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.Locale;

import me.vkryl.core.StringUtils;

@SuppressWarnings(value = "SpellCheckingInspection")
public class TGCountry {
  private static TGCountry instance;

  public static TGCountry instance () {
    if (instance == null) {
      instance = new TGCountry();
    }
    return instance;
  }

  public static void destroy () {
    if (instance != null) {
      instance.clear();
      instance = null;
    }
  }

  private final String[] USA = {"1","US","USA"};
  private final String[] ANONYMOUS = {"888", "XXX", "\uD83C\uDFF4\u200D\u2620 Anonymous Numbers"};

  private final String[][] countryList = {ANONYMOUS, {"93","AF","Afghanistan"},{"355","AL","Albania"},{"213","DZ","Algeria"},{"21","DZ","Algeria"},{"1684","AS","American Samoa"},{"376","AD","Andorra"},{"244","AO","Angola"},{"1264","AI","Anguilla"},{"1268","AG","Antigua and Barbuda"},{"54","AR","Argentina"},{"374","AM","Armenia"},{"297","AW","Aruba"},{"61","AU","Australia"},{"672","AU","Australia"},{"43","AT","Austria"},{"994","AZ","Azerbaijan"},{"1242","BS","Bahamas"},{"973","BH","Bahrain"},{"880","BD","Bangladesh"},{"1246","BB","Barbados"},{"375","BY","Belarus"},{"32","BE","Belgium"},{"501","BZ","Belize"},{"229","BJ","Benin"},{"1441","BM","Bermuda"},{"975","BT","Bhutan"},{"591","BO","Bolivia"},{"1721","SX","Bonaire, Sint Eustatius and Saba"},{"387","BA","Bosnia and Herzegovina"},{"267","BW","Botswana"},{"55","BR","Brazil"},{"1284","VG","British Virgin Islands"},{"673","BN","Brunei Darussalam"},{"359","BG","Bulgaria"},{"226","BF","Burkina Faso"},{"257","BI","Burundi"},{"855","KH","Cambodia"},{"237","CM","Cameroon"},{"1", "CAN", "Canada"},{"238","CV","Cape Verde"},{"1345","KY","Cayman Islands"},{"236","CF","Central African Republic"},{"235","TD","Chad"},{"56","CL","Chile"},{"86","CN","China"},{"57","CO","Colombia"},{"269","KM","Comoros"},{"242","CG","Congo"},{"243","CD","Congo, Democratic Republic"},{"682","CK","Cook Islands"},{"506","CR","Costa Rica"},{"385","HR","Croatia"},{"53","CU","Cuba"},{"5999","CW","Curaçao"},{"357","CY","Cyprus"},{"420","CZ","Czech Republic"},{"225","CI","Côte d`Ivoire"},{"45","DK","Denmark"},{"253","DJ","Djibouti"},{"1767","DM","Dominica"},{"1849","DO","Dominican Republic"},{"1809","DO","Dominican Republic"},{"1829","DO","Dominican Republic"},{"670","TL","East Timor"},{"593","EC","Ecuador"},{"20","EG","Egypt"},{"503","SV","El Salvador"},{"240","GQ","Equatorial Guinea"},{"291","ER","Eritrea"},{"372","EE","Estonia"},{"251","ET","Ethiopia"},{"500","FK","Falkland Islands"},{"298","FO","Faroe Islands"},{"679","FJ","Fiji"},{"358","FI","Finland"},{"33","FR","France"},{"594","GF","French Guiana"},{"689","PF","French Polynesia"},{"241","GA","Gabon"},{"220","GM","Gambia"},{"995","GE","Georgia"},{"49","DE","Germany"},{"233","GH","Ghana"},{"350","GI","Gibraltar"},{"30","GR","Greece"},{"299","GL","Greenland"},{"1473","GD","Grenada"},{"590","GP","Guadeloupe"},{"1671","GU","Guam"},{"502","GT","Guatemala"},{"224","GN","Guinea"},{"245","GW","Guinea-Bissau"},{"592","GY","Guyana"},{"509","HT","Haiti"},{"504","HN","Honduras"},{"852","HK","Hong Kong"},{"36","HU","Hungary"},{"354","IS","Iceland"},{"91","IN","India"},{"62","ID","Indonesia"},{"98","IR","Iran"},{"964","IQ","Iraq"},{"353","IE","Ireland"},{"972","IL","Israel"},{"39","IT","Italy"},{"1876","JM","Jamaica"},{"81","JP","Japan"},{"962","JO","Jordan"},{"77","KZ","Kazakhstan"},{"254","KE","Kenya"},{"686","KI","Kiribati"},{"965","KW","Kuwait"},{"996","KG","Kyrgyzstan"},{"856","LA","Laos"},{"371","LV","Latvia"},{"961","LB","Lebanon"},{"266","LS","Lesotho"},{"231","LR","Liberia"},{"218","LY","Libya"},{"423","LI","Liechtenstein"},{"370","LT","Lithuania"},{"352","LU","Luxembourg"},{"853","MO","Macau"},{"389","MK","Macedonia"},{"261","MG","Madagascar"},{"265","MW","Malawi"},{"60","MY","Malaysia"},{"960","MV","Maldives"},{"223","ML","Mali"},{"356","MT","Malta"},{"692","MH","Marshall Islands"},{"596","MQ","Martinique"},{"222","MR","Mauritania"},{"230","MU","Mauritius"},{"52","MX","Mexico"},{"691","FM","Micronesia"},{"373","MD","Moldova"},{"377","MC","Monaco"},{"976","MN","Mongolia"},{"382","ME","Montenegro"},{"1664","MS","Montserrat"},{"212","MA","Morocco"},{"258","MZ","Mozambique"},{"95","MM","Myanmar"},{"264","NA","Namibia"},{"674","NR","Nauru"},{"977","NP","Nepal"},{"31","NL","Netherlands"},{"687","NC","New Caledonia"},{"64","NZ","New Zealand"},{"505","NI","Nicaragua"},{"227","NE","Niger"},{"234","NG","Nigeria"},{"683","NU","Niue"},{"6723","NF","Norfolk Island"},{"850","KP","North Korea"},{"1670","MP","Northern Mariana Islands"},{"47","NO","Norway"},{"968","OM","Oman"},{"92","PK","Pakistan"},{"680","PW","Palau"},{"970","PS","Palestine"},{"507","PA","Panama"},{"675","PG","Papua New Guinea"},{"595","PY","Paraguay"},{"51","PE","Peru"},{"63","PH","Philippines"},{"48","PL","Poland"},{"351","PT","Portugal"},{"1939","PR","Puerto Rico"},{"1787","PR","Puerto Rico"},{"974","QA","Qatar"},{"40","RO","Romania"},{"7","RU","Russia"},{"250","RW","Rwanda"},{"262","RE","Réunion"},{"247","SH","Saint Helena"},{"290","SH","Saint Helena"},{"1869","KN","Saint Kitts and Nevis"},{"1758","LC","Saint Lucia"},{"508","PM","Saint Pierre and Miquelon"},{"1784","VC","Saint Vincent and the Grenadines"},{"685","WS","Samoa"},{"378","SM","San Marino"},{"966","SA","Saudi Arabia"},{"221","SN","Senegal"},{"381","RS","Serbia"},{"248","SC","Seychelles"},{"232","SL","Sierra Leone"},{"65","SG","Singapore"},{"599","BQ","Sint Maarten"},{"421","SK","Slovakia"},{"386","SI","Slovenia"},{"677","SB","Solomon Islands"},{"252","SO","Somalia"},{"27","ZA","South Africa"},{"82","KR","South Korea"},{"211","SS","South Sudan"},{"34","ES","Spain"},{"94","LK","Sri Lanka"},{"249","SD","Sudan"},{"597","SR","Suriname"},{"268","SZ","Swaziland"},{"46","SE","Sweden"},{"41","CH","Switzerland"},{"963","SY","Syria"},{"239","ST","São Tomé and Príncipe"},{"886","TW","Taiwan"},{"992","TJ","Tajikistan"},{"255","TZ","Tanzania"},{"66","TH","Thailand"},{"228","TG","Togo"},{"690","TK","Tokelau"},{"676","TO","Tonga"},{"1868","TT","Trinidad and Tobago"},{"216","TN","Tunisia"},{"90","TR","Turkey"},{"993","TM","Turkmenistan"},{"1649","TC","Turks and Caicos Islands"},{"688","TV","Tuvalu"},{"1340","VI","US Virgin Islands"},USA,{"256","UG","Uganda"},{"380","UA","Ukraine"},{"971","AE","United Arab Emirates"},{"44","GB","United Kingdom"},{"246","IO","United Kingdom"},{"598","UY","Uruguay"},{"998","UZ","Uzbekistan"},{"678","VU","Vanuatu"},{"58","VE","Venezuela"},{"84","VN","Vietnam"},{"681","WF","Wallis and Futuna"},{"967","YE","Yemen"},{"260","ZM","Zambia"},{"263","ZW","Zimbabwe"}};

  private TGCountry () {
    ANONYMOUS[ANONYMOUS.length - 1] = Lang.getString(R.string.AnonymousNumbers);
  }

  private void clear () {
    if (countryList != null) {
      int i = 0;
      for (String[] country : countryList) {
        country[0] = null;
        country[1] = null;
        country[2] = null;
        countryList[i] = null;
        i++;
      }
    }
  }

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
      String[] country = find(countryISO);

      if (country == null) {
        return null;
      }

      return new String[] { country[0], number.substring(country[0].length())};
    } catch (Throwable t) {
      Log.w("Cannot getLine1Number", t);
    }
    return null;
  }

  public String[] find (String countryCode) {
    for (String[] country : countryList) {
      if (country[1].equals(countryCode)) {
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

  public String[] getCurrent () {
    String code = getCurrentCode();
    if (code == null)
      return null;
    return find(code);
  }

  public String[] get (String phoneCode) {
    if (StringUtils.isEmpty(phoneCode)) {
      return null;
    }

    if (USA[0].equals(phoneCode)) {
      return USA;
    }

    int guess = -1;
    int i = 0;

    for (String[] country : countryList) {
      if (country[0].equals(phoneCode)) {
        return country;
      }
      if (country[0].indexOf(phoneCode) == 0) {
        guess = i;
      }
      i++;
    }

    if (guess != -1) {
      return countryList[guess];
    }

    return null;
  }

  public String[][] getAll () {
    return countryList;
  }
}
