package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGCountry;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public class SelectCountryController extends RecyclerViewController<SelectCountryController.Args> implements Menu, View.OnClickListener {
  public static class Args {
    private final RunnableData<Country> callback;
    private final boolean showPhoneCode;

    public Args (RunnableData<Country> callback, boolean showPhoneCode) {
      this.callback = callback;
      this.showPhoneCode = showPhoneCode;
    }
  }

  public static class Country {
    public final String callingCode;
    public final String countryCode;
    public final String countryName;

    public Country (String callingCode, String countryCode, String countryName) {
      this.callingCode = callingCode;
      this.countryCode = countryCode;
      this.countryName = countryName;
    }
  }

  private SettingsAdapter adapter;

  public SelectCountryController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_selectCountry;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {};

    recyclerView.setItemAnimator(null);
    recyclerView.setAdapter(adapter);

    bindCountryList(null);
  }

  private void bindCountryList (@Nullable String query) {
    ArrayList<ListItem> items = new ArrayList<>();
    boolean phoneMode = getArgumentsStrict().showPhoneCode;

    if (query != null && !query.isEmpty()) {
      search(query, phoneMode);
      return;
    }

    for (String[] country : TGCountry.instance().getAll()) {
      items.add(new ListItem(ListItem.TYPE_COUNTRY, R.id.result, 0, country[2], false).setData(phoneMode ? "+" + country[0] : "").setStringValue(country[1]));
    }

    items.remove(items.size() - 1);
    adapter.setItems(items, false);
  }

  private void search (String query, boolean phoneMode) {
    Background.instance().post(() -> {
      String[][] countries = TGCountry.instance().getAll();
      String number = Strings.getNumber(query);
      int[] level = new int[1];
      final ArrayList<ListItem> results = new ArrayList<>(countries.length + 1);
      final Comparator<ListItem> comparator = (o1, o2) -> {
        int level1 = o1.getSliderValue();
        int level2 = o2.getSliderValue();

        if (level1 != level2) {
          return level1 < level2 ? -1 : 1;
        }

        String c1 = o1.getString().toString();
        String c2 = o2.getString().toString();

        int cmp = c1.compareTo(c2);

        return cmp != 0 ? cmp : ((String) o1.getData()).compareTo((String) o2.getData());
      };

      for (String[] country : countries) {
        String name = country[2].toLowerCase();
        if (!number.isEmpty() && country[0].startsWith(number)) {
          level[0] = -1;
        } else if (!Strings.anyWordStartsWith(name, query, level)) {
          String clean = Strings.clean(name);
          if (StringUtils.equalsOrBothEmpty(name, clean) || !Strings.anyWordStartsWith(clean, query, level)) {
            continue;
          }
        }
        ListItem item = new ListItem(ListItem.TYPE_COUNTRY, R.id.result, 0, country[2], false).setData(phoneMode ? "+" + country[0] : "").setStringValue(country[1]).setSliderInfo(null, level[0]);
        int i = Collections.binarySearch(results, item, comparator);
        if (i < 0) {
          results.add(-(++i), item);
        }
      }

      if (results.isEmpty()) {
        results.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.RegionNotFound));
      }

      tdlib.ui().post(() -> {
        if (!isDestroyed() && query.equals(getLastSearchInput().toLowerCase())) {
          adapter.setItems(results, true);
        }
      });
    });
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.result) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() != null) {
        String data = ((String) item.getData());
        navigateBack();
        getArgumentsStrict().callback.runWithData(
                new Country(
                        data.isEmpty() ? "" : data.substring(1),
                        item.getStringValue(),
                        item.getString().toString()
                )
        );
      }
    }
  }

  //

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search: {
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        openSearchMode();
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    bindCountryList(null);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ChooseCountry);
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    runOnUiThread(() -> setName(Lang.getString(R.string.ChooseCountry)), 100);
  }

  @Override
  protected void onSearchInputChanged (String input) {
    super.onSearchInputChanged(input);
    bindCountryList(Strings.clean(input.trim().toLowerCase()));
  }
}
