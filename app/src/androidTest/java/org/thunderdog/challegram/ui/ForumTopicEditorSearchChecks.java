package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation;
import org.thunderdog.challegram.stage8.SyntheticEnvironment;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/** Real search-row views only: no Activity, account, TDLib, keyboard service or search requests. */
public final class ForumTopicEditorSearchChecks {
  public static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    cases.add(new Stage8SyntheticInstrumentation.Case("editor_search_clear_ltr_touch_and_focus",
      environment -> layoutAndClear(environment, false)));
    cases.add(new Stage8SyntheticInstrumentation.Case("editor_search_clear_rtl_touch_and_focus",
      environment -> layoutAndClear(environment, true)));
    cases.add(new Stage8SyntheticInstrumentation.Case("editor_search_clear_restored_programmatic_and_disabled",
      ForumTopicEditorSearchChecks::restoredAndDisabled));
  }

  private static void layoutAndClear (SyntheticEnvironment environment, boolean rtl) throws Exception {
    Fixture fixture = new Fixture(environment.configure(1f, rtl, ThemeId.BLUE), "");
    equal(View.GONE, fixture.clear.getVisibility(), "Empty query has no clear control");
    fixture.input.setText("Synthetic query");
    measure(fixture.row, Screen.dp(320), Screen.dp(52));
    equal(View.VISIBLE, fixture.clear.getVisibility(), "Nonempty query exposes clear control");
    equal(Screen.dp(48), fixture.clear.getWidth(), "Clear target width");
    equal(Screen.dp(48), fixture.clear.getHeight(), "Clear target height");
    equal(Screen.dp(16), fixture.input.getPaddingStart(), "Modest leading text inset");
    equal(Screen.dp(8), fixture.input.getPaddingEnd(), "Text stays separate from the clear target");
    require(rtl ? fixture.clear.getRight() <= fixture.input.getLeft() : fixture.clear.getLeft() >= fixture.input.getRight(),
      "Clear target must be at the trailing edge without covering input text");
    require(fixture.input.getContext().getString(R.string.Clear).contentEquals(fixture.clear.getContentDescription()), "Accessible clear label is preserved");
    require(fixture.clear.isFocusable() && fixture.clear.isClickable(), "Clear is keyboard-accessible and clickable");

    final int[] emptyChanges = {0};
    fixture.input.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { }
      @Override public void afterTextChanged (Editable s) { if (s.length() == 0) emptyChanges[0]++; }
    });
    require(fixture.input.requestFocus(), "Search input accepts focus");
    require(fixture.clear.performClick(), "Clear click is handled");
    equal(0, fixture.input.length(), "Clear empties the actual query field");
    equal(1, emptyChanges[0], "Clear emits one empty-query event for the picker reset");
    require(fixture.input.hasFocus(), "Clear retains input focus");
    equal(View.GONE, fixture.clear.getVisibility(), "Clear disappears after activation");
    fixture.input.setText(" ");
    equal(View.VISIBLE, fixture.clear.getVisibility(), "Whitespace is still clearable input");
  }

  private static void restoredAndDisabled (SyntheticEnvironment environment) throws Exception {
    Fixture fixture = new Fixture(environment.configure(1.3f, false, ThemeId.BLUE), "Restored query");
    equal(View.VISIBLE, fixture.clear.getVisibility(), "Restored query immediately exposes clear");
    fixture.input.setEnabled(false);
    fixture.refresh();
    require(!fixture.clear.isEnabled(), "Pending/readonly input disables clear");
    fixture.clear.performClick();
    require("Restored query".contentEquals(fixture.input.getText()), "Disabled clear cannot mutate the query");
    fixture.input.setEnabled(true);
    fixture.refresh();
    require(fixture.clear.isEnabled(), "Editable input re-enables clear");
    set(fixture.editor, "binding", true);
    fixture.input.setText("");
    equal(View.GONE, fixture.clear.getVisibility(), "Programmatic category reset also hides clear");
  }

  private static final class Fixture {
    final ForumTopicEditController editor;
    final EditText input;
    final LinearLayout row;
    final ImageButton clear;

    Fixture (Context context, String initialQuery) throws Exception {
      // Only the search-row factory/theme-listener list is used on this constructor-free shell.
      // No controller lifecycle, TDLib field, context Activity or navigation is initialized.
      editor = allocate(ForumTopicEditController.class);
      input = new EditText(context);
      input.setSingleLine(true);
      input.setMinimumHeight(Screen.dp(52));
      input.setBackground(null);
      input.setText(initialQuery);
      set(editor, "searchInput", input);
      // Bypass cloud-language lookup, which would initialize persistent Settings.
      row = (LinearLayout) invoke(editor, "createSearchRow", new Class<?>[] {CharSequence.class}, context.getString(R.string.Clear));
      clear = (ImageButton) get(editor, "clearSearchButton");
    }

    void refresh () throws Exception {
      invoke(editor, "updateSearchClear", new Class<?>[0]);
    }
  }
}
