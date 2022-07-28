package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.TGCountry;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class PaymentAddShippingInfoController extends EditBaseController<PaymentAddShippingInfoController.Args> implements SettingsAdapter.TextChangeListener, View.OnClickListener {
  public static class Args {
    private final TdApi.Invoice invoice;
    private final TdApi.InputInvoice inputInvoice;
    @Nullable private final TdApi.OrderInfo predefinedOrderInfo;
    private final PaymentFormController.NewShippingInfoCallback callback;

    public Args (TdApi.Invoice invoice, TdApi.InputInvoice inputInvoice, @Nullable TdApi.OrderInfo predefinedOrderInfo, PaymentFormController.NewShippingInfoCallback callback) {
      this.invoice = invoice;
      this.inputInvoice = inputInvoice;
      this.predefinedOrderInfo = predefinedOrderInfo;
      this.callback = callback;
    }
  }

  private TdApi.Invoice invoice;
  private TdApi.InputInvoice inputInvoice;
  private SettingsAdapter adapter;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.invoice = args.invoice;
    this.inputInvoice = args.inputInvoice;

    if (args.predefinedOrderInfo != null) {
      if (args.predefinedOrderInfo.shippingAddress != null) {
        i_shipAddressOne = args.predefinedOrderInfo.shippingAddress.streetLine1;
        i_shipAddressTwo = args.predefinedOrderInfo.shippingAddress.streetLine2;
        i_shipState = args.predefinedOrderInfo.shippingAddress.state;
        i_shipCity = args.predefinedOrderInfo.shippingAddress.city;
        i_shipPostcode = args.predefinedOrderInfo.shippingAddress.postalCode;
        i_shipCountry = args.predefinedOrderInfo.shippingAddress.countryCode;
        if (!i_shipCountry.isEmpty()) {
          i_shipCountryUI = TGCountry.instance().find(i_shipCountry)[2];
        }
      }

      i_shipName = args.predefinedOrderInfo.name;
      i_shipPhone = args.predefinedOrderInfo.phoneNumber;
      i_shipEmail = args.predefinedOrderInfo.emailAddress;
    }
  }

  public PaymentAddShippingInfoController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_paymentFormNewShipInfo;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.PaymentFormShipInfoHeader);
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    setDoneIcon(R.drawable.baseline_check_24);
    setInstantDoneVisible(false);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        parent.setBackgroundColor(Theme.fillingColor());
        Views.setSingleLine(editText.getEditText(), true);

        switch (item.getId()) {
          case R.id.btn_inputShipPhone:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints(View.AUTOFILL_HINT_PHONE);
            }
            break;
          case R.id.btn_inputShipEmail:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
            }
            break;
          case R.id.btn_inputShipName:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints(View.AUTOFILL_HINT_NAME);
            }
            break;
          case R.id.btn_inputShipPostCode:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints(View.AUTOFILL_HINT_POSTAL_CODE);
            }
            break;
          case R.id.btn_inputShipAddressOne:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints(View.AUTOFILL_HINT_POSTAL_ADDRESS);
            }
            break;
          default:
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              editText.getEditText().setAutofillHints();
            }
            break;
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_inputShipCountry) {
          view.setData(i_shipCountryUI.isEmpty() ? Lang.getString(R.string.PaymentFormNotSet) : i_shipCountryUI);
        }
      }
    };

    adapter.setTextChangeListener(this);

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    if (invoice.needShippingAddress) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentFormShipInfoAddressSection));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipAddressOne, 0, R.string.PaymentFormShipInfoAddressOne).setStringValue(i_shipAddressOne));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipAddressTwo, 0, R.string.PaymentFormShipInfoAddressTwo).setStringValue(i_shipAddressTwo));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipCity, 0, R.string.PaymentFormShipInfoCity).setStringValue(i_shipCity));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipState, 0, R.string.PaymentFormShipInfoState).setStringValue(i_shipState));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inputShipCountry, 0, R.string.PaymentFormShipInfoCountry));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipPostCode, 0, R.string.PaymentFormShipInfoPostcode).setStringValue(i_shipPostcode));

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (invoice.needName || invoice.needPhoneNumber || invoice.needEmailAddress) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentFormShipInfoReceiverSection));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      if (invoice.needName) {
        items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipName, 0, R.string.PaymentFormShipInfoReceiverName).setStringValue(i_shipName));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      if (invoice.needPhoneNumber) {
        items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipPhone, 0, R.string.PaymentFormShipInfoReceiverPhone).setStringValue(i_shipPhone));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      if (invoice.needEmailAddress) {
        items.add(new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.btn_inputShipEmail, 0, R.string.PaymentFormShipInfoReceiverEmail).setStringValue(i_shipEmail));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      items.remove(items.size() - 1);

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_inputShipSaveInfo, 0, R.string.PaymentFormShipInfoSave, i_saveInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PaymentFormShipInfoSaveInfo));

    adapter.setItems(items, false);

    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    checkDoneButton();
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_inputShipCountry) {
      SelectCountryController c = new SelectCountryController(context, tdlib);
      c.setArguments(new SelectCountryController.Args((country) -> {
        i_shipCountry = country.countryCode;
        i_shipCountryUI = country.countryName;
        adapter.updateValuedSettingById(R.id.btn_inputShipCountry);
        checkDoneButton();
      }, false));
      navigateTo(c);
    } else if (v.getId() == R.id.btn_inputShipSaveInfo) {
      i_saveInfo = adapter.toggleView(v);
    }
  }

  private String i_shipAddressOne = "";
  private String i_shipAddressTwo = "";
  private String i_shipCity = "";
  private String i_shipState = "";
  private String i_shipPostcode = "";
  private String i_shipName = "";
  private String i_shipPhone = "";
  private String i_shipEmail = "";

  private String i_shipCountry = "";
  private String i_shipCountryUI = "";

  private boolean i_saveInfo = true;

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    v.setInErrorState(false);

    switch (id) {
      case R.id.btn_inputShipAddressOne:
        i_shipAddressOne = text;
        break;
      case R.id.btn_inputShipAddressTwo:
        i_shipAddressTwo = text;
        break;
      case R.id.btn_inputShipCity:
        i_shipCity = text;
        break;
      case R.id.btn_inputShipState:
        i_shipState = text;
        break;
      case R.id.btn_inputShipPostCode:
        i_shipPostcode = text;
        break;
      case R.id.btn_inputShipName:
        i_shipName = text;
        break;
      case R.id.btn_inputShipPhone:
        i_shipPhone = text;
        break;
      case R.id.btn_inputShipEmail:
        i_shipEmail = text;
        break;
    }

    checkDoneButton();
  }

  @Override
  protected boolean onDoneClick () {
    setDoneInProgress(true);

    try {
      verifyOrderInfo();
    } catch (Exception e) {
      Log.e(e);
      showAlert(new AlertDialog.Builder(context).setTitle(R.string.Error).setMessage(e.getMessage()));
      setDoneInProgress(false);
    }

    return true;
  }

  private void checkDoneButton () {
    boolean isValid = true;
    if (invoice.needShippingAddress && (i_shipAddressOne.isEmpty() || i_shipCity.isEmpty() || i_shipState.isEmpty() || i_shipPostcode.isEmpty() || i_shipCountry.isEmpty())) isValid = false;
    if (invoice.needEmailAddress && i_shipEmail.isEmpty()) isValid = false;
    if (invoice.needPhoneNumber && i_shipPhone.isEmpty()) isValid = false;
    if (invoice.needName && i_shipName.isEmpty()) isValid = false;
    setDoneVisible(isValid);
  }

  private void vibrateError (int target) {
    setDoneInProgress(false);

    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View view = recyclerView.getChildAt(i);
      if (view.getId() == target) {
        MaterialEditTextGroup metg = ((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0));
        metg.setInErrorState(true);
        UI.hapticVibrate(metg, true);
        Keyboard.show(metg.getEditText());
        break;
      }
    }
  }

  private void verifyOrderInfo () {
    TdApi.OrderInfo orderInfo = new TdApi.OrderInfo(i_shipName, i_shipPhone, i_shipEmail, new TdApi.Address(i_shipCountry, i_shipState, i_shipCity, i_shipAddressOne, i_shipAddressTwo, i_shipPostcode));

    tdlib.client().send(new TdApi.ValidateOrderInfo(
            inputInvoice,
            orderInfo,
            i_saveInfo
    ), (result) -> runOnUiThreadOptional(() -> {
      setDoneInProgress(false);

      if (result.getConstructor() == TdApi.ValidatedOrderInfo.CONSTRUCTOR) {
        getArgumentsStrict().callback.onShippingInfoValidated(orderInfo, (TdApi.ValidatedOrderInfo) result);
        navigateBack();
      } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        String errorMessage = ((TdApi.Error) result).message;

        switch (errorMessage) {
          case "ADDRESS_STREET_LINE1_INVALID":
            vibrateError(R.id.btn_inputShipAddressOne);
            break;
          case "ADDRESS_STREET_LINE2_INVALID":
            vibrateError(R.id.btn_inputShipAddressTwo);
            break;
          case "ADDRESS_STATE_INVALID":
            vibrateError(R.id.btn_inputShipState);
            break;
          case "ADDRESS_POSTCODE_INVALID":
            vibrateError(R.id.btn_inputShipPostCode);
            break;
          case "ADDRESS_CITY_INVALID":
            vibrateError(R.id.btn_inputShipCity);
            break;
          case "REQ_INFO_NAME_INVALID":
            vibrateError(R.id.btn_inputShipName);
            break;
          case "REQ_INFO_PHONE_INVALID":
            vibrateError(R.id.btn_inputShipPhone);
            break;
          case "REQ_INFO_EMAIL_INVALID":
            vibrateError(R.id.btn_inputShipEmail);
            break;
          default:
            showAlert(new AlertDialog.Builder(context).setTitle(R.string.Error).setMessage(errorMessage).setPositiveButton(Lang.getString(R.string.OK), (a, b) -> {}));
            break;
        }
      }
    }));
  }
}
