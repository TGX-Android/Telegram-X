package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.payments.PaymentFormBottomBarView;
import org.thunderdog.challegram.component.payments.PaymentPricePartView;
import org.thunderdog.challegram.component.payments.PaymentTipView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.payments.PaymentsSubtitleFormatter;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.td.Td;

public class PaymentFormController extends ViewController<PaymentFormController.Args> implements View.OnClickListener {
  public static class Args {
    private final TdApi.PaymentForm paymentForm;
    private final TdApi.InputInvoice paymentInvoice;

    public Args (TdApi.PaymentForm paymentForm, TdApi.InputInvoice paymentInvoice) {
      this.paymentForm = paymentForm;
      this.paymentInvoice = paymentInvoice;
    }
  }

  private TdApi.PaymentForm paymentForm;
  private TdApi.InputInvoice paymentInvoice;
  private TdApi.OrderInfo currentOrderInfo;

  private long paymentFormTotalAmount;
  private long paymentFormTipAmount;

  private TdApi.InputCredentials inputCredentials;
  private String inputCredentialsTitle;

  private String validatedOrderInfoId;
  private TdApi.ShippingOption[] availableShippingOptions;
  private TdApi.ShippingOption selectedShippingOption;

  private SettingsAdapter adapter;
  private ComplexRecyclerView contentView;

  private ComplexHeaderView complexHeader;
  private DoubleHeaderView doubleHeader;

  public PaymentFormController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    paymentForm = args.paymentForm;
    paymentInvoice = args.paymentInvoice;

    if (paymentForm.savedCredentials != null) {
      this.inputCredentials = new TdApi.InputCredentialsSaved(paymentForm.savedCredentials.id);
      this.inputCredentialsTitle = paymentForm.savedCredentials.title;
    }

    if (paymentForm.savedOrderInfo != null) {
      this.currentOrderInfo = paymentForm.savedOrderInfo;
    }

    updateTotalAmount();
  }

  @Override
  protected View onCreateView (Context context) {
    if (isHeaderFullscreen()) {
      complexHeader = new ComplexHeaderView(context, tdlib, this);

      complexHeader.setAvatarExpandListener((headerView1, expandFactor, byCollapse, allowanceFactor, collapseFactor) -> {
        if (byCollapse && bottomBar != null) {
          scrollToBottomVisibleFactor = expandFactor;
          updateBottomBarStyle();
        }

        updateButtonsColor();
      });

      complexHeader.setAllowEmptyClick();
      complexHeader.initWithController(this, true);
      complexHeader.setInnerMargins(Screen.dp(56f), 0);
    } else {
      doubleHeader = new DoubleHeaderView(context());
      doubleHeader.setThemedTextColor(this);
      doubleHeader.initWithMargin(Screen.dp(49f), true);
    }

    updateHeader();

    this.contentView = new ComplexRecyclerView(context, this);
    this.contentView.setHasFixedSize(true);
    this.contentView.setHeaderView(complexHeader, this);
    // this.contentView.setItemAnimator(null);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_background, this);
    this.contentView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    this.contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_paymentFormDescription:
            view.setText(new TextWrapper(tdlib, paymentForm.productDescription, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null));
            break;
          case R.id.btn_paymentFormProvider:
            view.setName(getPaymentProcessorName());
            view.setData(R.string.PaymentFormProvider);
            break;
          case R.id.btn_paymentFormShipmentAddress:
            view.setName(formatShippingAddressSubtitle());
            view.setData(R.string.PaymentFormShipAddress);
            break;
          case R.id.btn_paymentFormShipmentMethod:
            view.setName(selectedShippingOption != null ? selectedShippingOption.title : Lang.getString(R.string.PaymentFormNotSet));
            view.setData(R.string.PaymentFormShipMethod);
            break;
          case R.id.btn_paymentFormMethod:
            view.setName(inputCredentials != null ? inputCredentialsTitle : Lang.getString(R.string.PaymentFormNotSet));
            view.setData(R.string.PaymentFormMethod);
            break;
        }
      }

      @Override
      protected void modifyPaymentTip (ListItem item, PaymentTipView tipView) {
        tipView.setData(paymentForm.invoice, (tip) -> {
          paymentFormTipAmount = tip;
          updateTotalAmountWithUI();
        });
      }

      @Override
      protected void modifyPaymentPricePart (ListItem item, PaymentPricePartView partView) {
        if (item.getId() == R.id.btn_paymentFormTotal) {
          partView.setData(new PaymentPricePartView.PartData(Lang.getString(R.string.PaymentFormTotal), formatCurrency(paymentFormTotalAmount), true));
        } else {
          partView.setData((PaymentPricePartView.PartData) item.getData());
        }
      }
    };

    bindItems();
    this.contentView.setAdapter(adapter);

    RelativeLayout wrapper = new RelativeLayout(context);
    createBottomBar();
    wrapper.addView(bottomBar);
    wrapper.addView(contentView, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    return wrapper;
  }

  private String formatShippingAddressSubtitle () {
    return PaymentsSubtitleFormatter.format(currentOrderInfo, paymentForm.invoice);
  }

  private void openNewCardController () {
    switch (paymentForm.paymentProvider.getConstructor()) {
      case TdApi.PaymentProviderStripe.CONSTRUCTOR:
      case TdApi.PaymentProviderSmartGlocal.CONSTRUCTOR:
        PaymentAddNewCardController c = new PaymentAddNewCardController(context, tdlib);
        c.setArguments(new PaymentAddNewCardController.Args(this::onPaymentMethodSelected, paymentForm.paymentProvider, paymentForm.invoice.isTest, paymentForm.canSaveCredentials, paymentForm.needPassword));
        navigateTo(c);
        break;
      case TdApi.PaymentProviderOther.CONSTRUCTOR:
        WebPaymentMethodController wc = new WebPaymentMethodController(context, tdlib);
        wc.setArguments(new WebPaymentMethodController.Args(this::onPaymentMethodSelected, getPaymentProcessorName(), ((TdApi.PaymentProviderOther) paymentForm.paymentProvider).url, paymentForm.canSaveCredentials));
        navigateTo(wc);
        break;
    }
  }

  private void openShipmentAddressController () {
    PaymentAddShippingInfoController c = new PaymentAddShippingInfoController(context, tdlib);
    c.setArguments(new PaymentAddShippingInfoController.Args(paymentForm.invoice, paymentInvoice, currentOrderInfo, this::onShippingInfoValidated));
    navigateTo(c);
  }

  private void openShipmentMethodAlert () {
    validateAndRequestShipping(() -> {
      ArrayList<OptionItem> options = new ArrayList<>();

      for (int i = 0; i < availableShippingOptions.length; i++) {
        TdApi.ShippingOption so = availableShippingOptions[i];

        SpannableStringBuilder ssb = new SpannableStringBuilder();

        ssb.append(so.title);

        long totalCost = 0;

        for (TdApi.LabeledPricePart lpp : so.priceParts) {
          totalCost += lpp.amount;
        }

        int initialStart = ssb.length();

        ssb.append(" ").append(formatCurrency(totalCost));
        ssb.setSpan(new ForegroundColorSpan(Theme.getColor(R.id.theme_color_textLight)), initialStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        options.add(
          new OptionItem.Builder()
            .id(i)
            .name(ssb)
            .build()
        );
      }

      showOptions(new Options(Lang.getString(R.string.ChooseShipmentMethod), options.toArray(new OptionItem[0])), (optionItemView, id) -> {
        setShipmentOption(availableShippingOptions[id]);
        return true;
      }, null);
    });
  }

  private int getCredentialsDescription () {
    if (inputCredentials.getConstructor() == TdApi.InputCredentialsSaved.CONSTRUCTOR) {
      return R.string.PaymentMethodSaved;
    } else {
      return R.string.PaymentMethodNew;
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_paymentFormMethod:
        if (inputCredentials != null) {
          showOptions(
                  Lang.getMarkdownString(this, R.string.format_paymentMethod, inputCredentialsTitle, Lang.getString(getCredentialsDescription())),
                  new int[]{R.id.btn_paymentFormMethod, R.id.btn_cancel},
                  new String[]{Lang.getString(R.string.PaymentMethodActionChange), Lang.getString(R.string.Cancel)},
                  new int[]{ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_NORMAL},
                  new int[]{R.drawable.baseline_credit_card_24, R.drawable.baseline_cancel_24},
                  (optionItemView, id) -> {
                    if (id == R.id.btn_paymentFormMethod) {
                      openNewCardController();
                    }

                    return true;
                  }
          );
        } else {
          openNewCardController();
        }
        break;
      case R.id.btn_paymentFormShipmentAddress:
        openShipmentAddressController();
        break;
      case R.id.btn_paymentFormShipmentMethod:
        openShipmentMethodAlert();
        break;
    }
  }

  private void onShippingInfoValidated (TdApi.OrderInfo newOrderInfo, TdApi.ValidatedOrderInfo validatedOrderInfo) {
    this.currentOrderInfo = newOrderInfo;
    this.validatedOrderInfoId = validatedOrderInfo.orderInfoId;
    this.availableShippingOptions = validatedOrderInfo.shippingOptions;
    adapter.updateValuedSettingById(R.id.btn_paymentFormShipmentAddress);
    updateShippingInterface();
  }

  private void validateAndRequestShipping (Runnable after) {
    if (validatedOrderInfoId != null) {
      after.run();
      return;
    }

    tdlib.client().send(new TdApi.ValidateOrderInfo(paymentInvoice, currentOrderInfo, true), (obj) -> {
      if (obj.getConstructor() == TdApi.ValidatedOrderInfo.CONSTRUCTOR) {
        TdApi.ValidatedOrderInfo validatedOrderInfo = (TdApi.ValidatedOrderInfo) obj;
        this.validatedOrderInfoId = validatedOrderInfo.orderInfoId;
        this.availableShippingOptions = validatedOrderInfo.shippingOptions;
        runOnUiThreadOptional(after);
      } else {
        UI.showError(obj);
      }
    });
  }

  private void setShipmentOption (TdApi.ShippingOption newShippingOption) {
    if (selectedShippingOption != null) {
      if (selectedShippingOption.id.equals(newShippingOption.id)) return;

      int removeIndex = adapter.indexOfViewById(R.id.btn_paymentFormTotal) - 1 - (paymentForm.invoice.maxTipAmount > 0 ? 2 : 0);
      int count = selectedShippingOption.priceParts.length;
      adapter.removeRange(removeIndex - count, count);
    }

    selectedShippingOption = newShippingOption;

    if (selectedShippingOption != null) {
      int additionalIndex = adapter.indexOfViewById(R.id.btn_paymentFormTotal) - 1 - (paymentForm.invoice.maxTipAmount > 0 ? 2 : 0);

      ArrayList<ListItem> newItems = new ArrayList<>();

      for (TdApi.LabeledPricePart pricePart: newShippingOption.priceParts) {
        newItems.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(pricePart.label, formatCurrency(pricePart.amount), false)));
      }

      adapter.addItems(additionalIndex, newItems.toArray(new ListItem[0]));
    }

    adapter.updateValuedSettingById(R.id.btn_paymentFormShipmentMethod);
    updateTotalAmountWithUI();
  }

  private void updateShippingInterface () {
    int indexOfShippingAddress = adapter.indexOfViewById(R.id.btn_paymentFormShipmentAddress);
    int indexOfShipmentMethod = adapter.indexOfViewById(R.id.btn_paymentFormShipmentMethod);

    if (availableShippingOptions != null && availableShippingOptions.length > 0 && indexOfShipmentMethod == -1) {
      adapter.addItems(
        indexOfShippingAddress + 1,
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false)
      );
    } else if (indexOfShipmentMethod != -1) {
      adapter.removeRange(indexOfShipmentMethod - 1, 2);
    }
  }

  private String getPaymentProcessorName () {
    return tdlib.cache().userDisplayName(paymentForm.paymentProviderUserId, false, false);
  }

  private boolean isHeaderFullscreen () {
    return paymentForm.productPhoto != null;
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, complexHeader != null ? complexHeader.getAvatarExpandFactor() : 0f));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  private void updateHeader () {
    if (complexHeader != null) {
      TdApi.PhotoSize size = Td.findBiggest(paymentForm.productPhoto);
      ImageFile sizeFile = size != null ? new ImageFile(tdlib, size.photo) : null;
      if (sizeFile != null) sizeFile.setScaleType(ImageFile.CENTER_CROP);
      complexHeader.setAvatar(size != null ? new ImageFile(tdlib, size.photo) : null, sizeFile);
      complexHeader.setText(paymentForm.productTitle, tdlib.cache().userDisplayName(paymentForm.sellerBotUserId, false, false));
      complexHeader.invalidate();
    } else if (doubleHeader != null) {
      doubleHeader.setTitle(paymentForm.productTitle);
      doubleHeader.setSubtitle(tdlib.cache().userDisplayName(paymentForm.sellerBotUserId, false, false));
    }
  }

  private void bindItems () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (isHeaderFullscreen()) items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_paymentFormDescription, R.drawable.baseline_info_24, R.string.Description, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormMethod, R.drawable.baseline_credit_card_24, 0, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormProvider, R.drawable.themanuz_cash_register_24, 0, false));

    if (isShipmentInfoRequired()) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentAddress, R.drawable.baseline_location_on_24, 0, false));

      if (currentOrderInfo != null) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false));
      }
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentFormSummary));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    for (TdApi.LabeledPricePart pricePart : paymentForm.invoice.priceParts) {
      items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(pricePart.label, formatCurrency(pricePart.amount), false)));
    }

    if (paymentForm.invoice.maxTipAmount > 0) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_PAYMENT_TIP));
    }

    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART, R.id.btn_paymentFormTotal));
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(48f)));

    adapter.setItems(items, false);
    tryPredictingHeaderHeight();
  }

  private void tryPredictingHeaderHeight () {
    int adapterHeight = adapter.measureHeight(-1);
    boolean shouldExpand = adapterHeight >= (Screen.currentActualHeight());
    scrollToBottomVisibleFactor = shouldExpand ? 1f : 0f;
    updateBottomBarStyle();
  }

  private void updateTotalAmount () {
    paymentFormTotalAmount = paymentFormTipAmount;
    for (TdApi.LabeledPricePart pricePart : paymentForm.invoice.priceParts) paymentFormTotalAmount += pricePart.amount;
    if (selectedShippingOption != null) {
      for (TdApi.LabeledPricePart pricePart : selectedShippingOption.priceParts) paymentFormTotalAmount += pricePart.amount;
    }
  }

  private void updateTotalAmountWithUI() {
    updateTotalAmount();
    adapter.updateValuedSettingById(R.id.btn_paymentFormTotal);
    bottomBar.setAction(0, Lang.getString(R.string.PaymentFormPay, CurrencyUtils.buildAmount(paymentForm.invoice.currency, paymentFormTotalAmount)), R.drawable.baseline_arrow_downward_24, false);
  }

  private String formatCurrency (long amount) {
    if (amount < 0) {
      return "-" + CurrencyUtils.buildAmount(paymentForm.invoice.currency, Math.abs(amount));
    } else {
      return CurrencyUtils.buildAmount(paymentForm.invoice.currency, amount);
    }
  }

  // Animations - Scroll to Bottom + Pay superbutton

  private PaymentFormBottomBarView bottomBar;
  private float scrollToBottomVisibleFactor;

  private void createBottomBar () {
    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

    bottomBar = new PaymentFormBottomBarView(context, tdlib) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateBottomBarStyle();
      }
    };

    bottomBar.setOnClickListener(this);
    bottomBar.setLayoutParams(params);
    bottomBar.setAction(0, Lang.getString(R.string.PaymentFormPay, CurrencyUtils.buildAmount(paymentForm.invoice.currency, paymentFormTotalAmount)), R.drawable.baseline_arrow_downward_24, false);
    bottomBar.setOnClickListener(view -> {
      if (scrollToBottomVisibleFactor == 0f) {
        onPayButtonPressed();
      } else {
        contentView.smoothScrollToPosition(adapter.getItemCount() - 1);
      }
    });

    addThemeInvalidateListener(bottomBar);
    updateBottomBarStyle();
  }

  private void onPayButtonPressed () {
    if (inputCredentials == null) {
      openNewCardController();
    } else if (isShipmentInfoRequired() && (currentOrderInfo == null || currentOrderInfo.shippingAddress == null)) {
      openShipmentAddressController();
    } else if (currentOrderInfo != null && paymentForm.invoice.needShippingAddress && selectedShippingOption == null) {
      openShipmentMethodAlert();
    } else {
      initPaymentProcess();
    }
  }

  private void initPaymentProcess () {
    // TODO: check if 2fa is needed + disclaimer + payment confirmation

    if (isCredsSavable()) {
      tdlib.client().send(new TdApi.GetTemporaryPasswordState(), (state) -> {

      });
    }

  }

  private boolean isCredsSavable() {
    return inputCredentials.getConstructor() == TdApi.InputCredentialsSaved.CONSTRUCTOR || (inputCredentials.getConstructor() == TdApi.InputCredentialsNew.CONSTRUCTOR && ((TdApi.InputCredentialsNew) inputCredentials).allowSave);
  }

  private boolean isShipmentInfoRequired () {
    return paymentForm.invoice.needShippingAddress || paymentForm.invoice.needEmailAddress || paymentForm.invoice.needName || paymentForm.invoice.needPhoneNumber;
  }

  private void updateBottomBarStyle () {
    if (bottomBar == null) return;

    float toY = -Screen.dp(16f);
    int barHeight = Screen.dp(48f);
    int dx = (int) ((bottomBar.getMeasuredWidth() / 2f - Screen.dp(16f) - barHeight / 2) * scrollToBottomVisibleFactor);
    bottomBar.setCollapseFactor(scrollToBottomVisibleFactor);
    bottomBar.setTranslationY(scrollToBottomVisibleFactor == 0f ? 0 : toY * scrollToBottomVisibleFactor);
    bottomBar.setTranslationX(dx);
  }

  // Internal - Bridge methods

  public void onPaymentMethodSelected (TdApi.InputCredentials newMethod, String name) {
    this.inputCredentials = newMethod;
    this.inputCredentialsTitle = name;
    adapter.updateValuedSettingById(R.id.btn_paymentFormMethod);
  }

  // Internal methods

  @Override
  public int getId () {
    return R.id.controller_paymentForm;
  }

  @Override
  public void destroy () {
    super.destroy();

    if (complexHeader != null) {
      complexHeader.performDestroy();
    } else if (doubleHeader != null) {
      doubleHeader.performDestroy();
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return isHeaderFullscreen() ? complexHeader : doubleHeader;
  }

  @Override
  protected int getHeaderHeight () {
    if (isHeaderFullscreen()) {
      return (int) (Size.getHeaderPortraitSize() + Size.getHeaderSizeDifference(true) * contentView.getScrollFactor());
    } else {
      return super.getHeaderHeight();
    }
  }

  @Override
  protected int getMaximumHeaderHeight () {
    if (isHeaderFullscreen()) {
      return Size.getHeaderBigPortraitSize(true);
    } else {
      return super.getMaximumHeaderHeight();
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderIconColorId () {
    return complexHeader != null && !complexHeader.isCollapsed() ? R.id.theme_color_white : R.id.theme_color_headerIcon;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    contentView.setFactorLocked(false);
  }

  @Override
  public void onBlur () {
    super.onBlur();
    contentView.setFactorLocked(true);
  }

  // Bridges

  interface NewPaymentMethodCallback {
    void onNewMethodCreated (TdApi.InputCredentialsNew credentials, String methodName);
  }

  interface NewShippingInfoCallback {
    void onShippingInfoValidated (TdApi.OrderInfo newOrderInfo, TdApi.ValidatedOrderInfo validatedOrderInfo);
  }
}
