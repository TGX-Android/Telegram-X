package org.thunderdog.challegram.component.payments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NonMaterialButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.lambda.RunnableData;

public class PaymentTipView extends LinearLayout implements ThemeInvalidateListener {
  private final TextView title;

  private final TextView tipCurrency;
  private final EditText tipAmount;

  private final LinearLayout tipEntryWrapper;
  private final LinearLayout suggestionWrapper;

  private PaymentTipPartView[] suggestions;
  private long[] suggestionsValues;

  private String currency;
  private long currencyExp;

  private long maxAmount;
  private long currentAmount;
  private RunnableData<Long> onTipSelected;

  public PaymentTipView (Context context) {
    super(context);

    FrameLayout headerWrap = new FrameLayout(context);

    title = new TextView(context);
    title.setTextSize(15);
    title.setText(R.string.PaymentFormTip);
    headerWrap.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

    tipAmount = new EditText(context);
    tipAmount.setHint("0");
    tipAmount.setBackground(null);
    tipAmount.setTextSize(13);
    tipAmount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    tipAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
    tipAmount.setPadding(Lang.rtl() ? 0 : Screen.dp(8f), 0, Lang.rtl() ? Screen.dp(8f) : 0, 0); // enlarge the click zone
    Views.setSingleLine(tipAmount, true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      tipAmount.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    }

    tipCurrency = new TextView(context);
    tipCurrency.setTextSize(13);
    tipCurrency.setTextColor(Theme.textDecentColor());

    tipEntryWrapper = new LinearLayout(context);
    tipEntryWrapper.setGravity(LinearLayout.HORIZONTAL);
    tipEntryWrapper.addView(tipAmount);

    headerWrap.addView(tipEntryWrapper, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

    ScrollView suggestionWrapperRoot = new ScrollView(context);
    suggestionWrapper = new LinearLayout(context);
    suggestionWrapperRoot.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), 0);
    suggestionWrapperRoot.addView(suggestionWrapper, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setOrientation(LinearLayout.VERTICAL);
    addView(headerWrap);
    addView(suggestionWrapperRoot);

    tipAmount.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged (CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged (CharSequence s, int start, int before, int count) {

      }

      @Override
      public void afterTextChanged (Editable s) {
        if (s.length() > 0) {
          currentAmount = Long.parseLong(s.toString());
          if (!String.valueOf(currentAmount).equals(s.toString())) {
            s.replace(0, s.length(), String.valueOf(currentAmount)); // normalize edittext value
          }
        } else {
          currentAmount = 0;
        }

        if (s.length() > 0 && currentAmount > maxAmount) {
          s.replace(0, s.length(), String.valueOf(maxAmount));
          return;
        }

        tipCurrency.setTextColor(s.length() == 0 ? Theme.textDecentColor() : Theme.textAccentColor());
        onTipSelected.runWithData(currentAmount * currencyExp);
        recalculateTipSuggestions();
      }
    });

    recolor();
  }

  public void setData (TdApi.Invoice invoice, RunnableData<Long> onTipSelected) {
    CurrencyUtils.Currency ccurrency = CurrencyUtils.getCurrency(invoice.currency);

    this.currency = invoice.currency;
    this.currencyExp = (long) Math.pow(10, ccurrency.exp);
    this.maxAmount = invoice.maxTipAmount / currencyExp;
    this.onTipSelected = onTipSelected;

    if (tipEntryWrapper.getChildCount() == 1) {
      tipCurrency.setText(((ccurrency.flags & CurrencyUtils.FLAG_SPACE_BETWEEN) != 0 ? " " : "") + CurrencyUtils.getCurrencyChar(invoice.currency));
      tipEntryWrapper.addView(tipCurrency, (ccurrency.flags & CurrencyUtils.FLAG_SYMBOL_LEFT) != 0 ? 0 : 1);
    }

    if (suggestionWrapper.getChildCount() == 0) {
      suggestionsValues = invoice.suggestedTipAmounts;
      suggestions = new PaymentTipPartView[suggestionsValues.length];
      for (int i = 0; i < invoice.suggestedTipAmounts.length; i++) {
        suggestionWrapper.addView(suggestions[i] = createTipPart(invoice.suggestedTipAmounts[i]), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, NonMaterialButton.defaultHeight()));
        if (i != invoice.suggestedTipAmounts.length - 1) {
          suggestionWrapper.addView(new Space(getContext()), new ViewGroup.LayoutParams(Screen.dp(8f), NonMaterialButton.defaultHeight()));
        }
      }
    }
  }

  private void recalculateTipSuggestions () {
    for (int i = 0; i < suggestionsValues.length; i++) {
      suggestions[i].setToggled((suggestionsValues[i] / currencyExp) == currentAmount);
    }
  }

  private PaymentTipPartView createTipPart (long amount) {
    PaymentTipPartView bt = new PaymentTipPartView(getContext());
    bt.setBackground(Theme.fillingSelector());
    bt.setText(CurrencyUtils.buildAmount(currency, amount));
    bt.setOnClickListener((v) -> {
      if (currentAmount == amount / currencyExp) {
        currentAmount = 0;
        tipAmount.setText("");
        tipAmount.setSelection(0);
      } else {
        currentAmount = amount / currencyExp;
        tipAmount.setText(String.valueOf(currentAmount));
        tipAmount.setSelection(tipAmount.length());
      }

      recalculateTipSuggestions();
      onTipSelected.runWithData(currentAmount * currencyExp);
    });
    return bt;
  }

  private void recolor () {
    title.setTextColor(Theme.textDecentColor());
    tipAmount.setTextColor(Theme.textAccentColor());
    tipAmount.setHintTextColor(Theme.textDecentColor());
    tipCurrency.setTextColor(tipAmount.getText().length() == 0 ? Theme.textDecentColor() : Theme.textAccentColor());
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
    invalidate();
  }
}
