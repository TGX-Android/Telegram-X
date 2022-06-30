package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.ChatBottomBarView;
import org.thunderdog.challegram.component.payments.PaymentFormBottomBarView;
import org.thunderdog.challegram.component.payments.PaymentPricePartView;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.Locale;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.collection.IntList;
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

  private TdApi.InputCredentials inputCredentials;
  private String inputCredentialsTitle;

  private String validatedOrderInfoId;
  private TdApi.ShippingOption[] availableShippingOptions;
  private TdApi.ShippingOption selectedShippingOption;

  private SettingsAdapter adapter;
  private ComplexHeaderView headerCell;
  private ComplexRecyclerView contentView;

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
    this.headerCell = new ComplexHeaderView(context, tdlib, this);
    this.headerCell.setAvatarExpandListener((headerView1, expandFactor, byCollapse, allowanceFactor, collapseFactor) -> {
      if (byCollapse && bottomBar != null) {
        scrollToBottomVisibleFactor = expandFactor;
        updateBottomBarStyle();
      }

      updateButtonsColor();
    });

    this.headerCell.setAllowEmptyClick();
    this.headerCell.initWithController(this, true);
    this.headerCell.setInnerMargins(Screen.dp(isHeaderFullscreen() ? 56f : 12f), 0);
    updateHeader();

    this.contentView = new ComplexRecyclerView(context, this);
    this.contentView.setHasFixedSize(true);
    this.contentView.setHeaderView(headerCell, this);
    // this.contentView.setItemAnimator(null);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_background, this);
    this.contentView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    this.contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_paymentFormDescription:
            view.setText(new TextWrapper(paymentForm.productDescription, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null));
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
    if (currentOrderInfo == null || currentOrderInfo.shippingAddress == null) return Lang.getString(R.string.PaymentFormNotSet);
    if (currentOrderInfo.shippingAddress.streetLine2.isEmpty()) {
      return Lang.getString(R.string.format_paymentFormAddress, currentOrderInfo.shippingAddress.streetLine1, currentOrderInfo.shippingAddress.city, currentOrderInfo.shippingAddress.state, currentOrderInfo.shippingAddress.countryCode, currentOrderInfo.shippingAddress.postalCode);
    } else {
      return Lang.getString(R.string.format_paymentFormAddressWithSecondLine, currentOrderInfo.shippingAddress.streetLine1, currentOrderInfo.shippingAddress.streetLine2, currentOrderInfo.shippingAddress.city, currentOrderInfo.shippingAddress.state, currentOrderInfo.shippingAddress.countryCode, currentOrderInfo.shippingAddress.postalCode);
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_paymentFormMethod:
        if (paymentForm.paymentsProvider != null) {
          PaymentAddNewCardController c = new PaymentAddNewCardController(context, tdlib);
          c.setArguments(new PaymentAddNewCardController.Args(this, paymentForm.paymentsProvider));
          navigateTo(c);
        } else {
          WebPaymentMethodController c = new WebPaymentMethodController(context, tdlib);
          c.setArguments(new WebPaymentMethodController.Args(getPaymentProcessorName(), paymentForm.url, this));
          navigateTo(c);
        }
        break;
      case R.id.btn_paymentFormShipmentMethod:
        validateAndRequestShipping(() -> {
          IntList ids = new IntList(0);
          StringList titles = new StringList(0);

          for (TdApi.ShippingOption so : availableShippingOptions) {
            ids.append(so.id.hashCode());
            titles.append(so.id + " - " + so.title);
          }

          showOptions("Choose", ids.get(), titles.get());
        });
        break;
    }
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

  private void updateShippingInterface () {
    int indexOfShippingAddress = adapter.indexOfViewById(R.id.btn_paymentFormShipmentAddress);

    if (availableShippingOptions != null && availableShippingOptions.length > 0) {
      adapter.addItems(
        indexOfShippingAddress + 1,
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false)
      );
    } else {
      adapter.removeRange(indexOfShippingAddress + 1, 2);
    }
  }

  private String getPaymentProcessorName () {
    return tdlib.cache().userDisplayName(paymentForm.paymentsProviderUserId, false, false);
  }

  private boolean isHeaderFullscreen () {
    return paymentForm.productPhoto != null;
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  private void updateHeader () {
    if (headerCell != null) {
      TdApi.PhotoSize size = Td.findBiggest(paymentForm.productPhoto);
      ImageFile sizeFile = size != null ? new ImageFile(tdlib, size.photo) : null;
      if (sizeFile != null) sizeFile.setScaleType(ImageFile.CENTER_CROP);
      headerCell.setAvatar(size != null ? new ImageFile(tdlib, size.photo) : null, sizeFile);
      headerCell.setText(paymentForm.productTitle, tdlib.cache().userDisplayName(paymentForm.sellerBotUserId, false, false));
      headerCell.invalidate();
    }
  }

  private void bindItems () {
    Log.d(paymentForm.toString());

    ArrayList<ListItem> items = new ArrayList<>();

    if (isHeaderFullscreen()) items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_paymentFormDescription, R.drawable.baseline_info_24, R.string.Description, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormMethod, R.drawable.baseline_credit_card_24, 0, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormProvider, R.drawable.themanuz_cash_register_24, 0, false));

    if (paymentForm.invoice.needShippingAddress) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentAddress, R.drawable.baseline_location_on_24, 0, false));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false));
    }

    if (availableShippingOptions != null && availableShippingOptions.length > 0) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentFormSummary));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    for (TdApi.LabeledPricePart pricePart : paymentForm.invoice.priceParts) {
      items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(pricePart.label, formatCurrency(pricePart.amount), false)));
    }

    if (paymentForm.invoice.maxTipAmount > 0) {
      // items.add(new ListItem(ListItem.TYPE_PAYMENT_TIP));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(Lang.getString(R.string.PaymentFormTotal), formatCurrency(paymentFormTotalAmount), true)));
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
    paymentFormTotalAmount = 0;
    for (TdApi.LabeledPricePart pricePart : paymentForm.invoice.priceParts) paymentFormTotalAmount += pricePart.amount;
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
      // TODO check if expanded
      contentView.smoothScrollToPosition(adapter.getItemCount() - 1);
    });

    addThemeInvalidateListener(bottomBar);
    updateBottomBarStyle();
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
    headerCell.performDestroy();
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getHeaderHeight () {
    if (isHeaderFullscreen()) {
      return (int) (Size.getHeaderPortraitSize() + Size.getHeaderSizeDifference(true) * contentView.getScrollFactor());
    } else {
      return Size.getHeaderPortraitSize();
    }
  }

  @Override
  protected int getMaximumHeaderHeight () {
    if (isHeaderFullscreen()) {
      return Size.getHeaderBigPortraitSize(true);
    } else {
      return Size.getHeaderPortraitSize();
    }
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderIconColorId () {
    return headerCell != null && !headerCell.isCollapsed() ? R.id.theme_color_white : R.id.theme_color_headerIcon;
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
}
