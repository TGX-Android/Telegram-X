package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.payments.PaymentFormBottomBarView;
import org.thunderdog.challegram.component.payments.PaymentPricePartView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.ComplexRecyclerView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.payments.PaymentsSubtitleFormatter;
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

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.Td;

public class PaymentReceiptController extends ViewController<PaymentReceiptController.Args> {
  public static class Args {
    private final TdApi.PaymentReceipt paymentReceipt;

    public Args (TdApi.PaymentReceipt paymentReceipt) {
      this.paymentReceipt = paymentReceipt;
    }
  }

  private TdApi.PaymentReceipt paymentReceipt;
  private long paymentFormTotalAmount;

  private SettingsAdapter adapter;
  private ComplexHeaderView headerCell;
  private ComplexRecyclerView contentView;

  public PaymentReceiptController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    paymentReceipt = args.paymentReceipt;
    updateTotalAmount();
  }

  @Override
  protected View onCreateView (Context context) {
    this.headerCell = new ComplexHeaderView(context, tdlib, this);
    this.headerCell.setAvatarExpandListener((headerView1, expandFactor, byCollapse, allowanceFactor, collapseFactor) -> updateButtonsColor());

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
            view.setText(new TextWrapper(tdlib, paymentReceipt.description, TGMessage.simpleTextStyleProvider(), TextColorSets.Regular.NORMAL, null));
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
            view.setName(paymentReceipt.shippingOption != null ? paymentReceipt.shippingOption.title : Lang.getString(R.string.PaymentFormNotSet));
            view.setData(R.string.PaymentFormShipMethod);
            break;
          case R.id.btn_paymentFormMethod:
            view.setName(paymentReceipt.credentialsTitle);
            view.setData(R.string.PaymentFormMethod);
            break;
        }
      }

      @Override
      protected void modifyPaymentPricePart (ListItem item, PaymentPricePartView partView) {
        partView.setData((PaymentPricePartView.PartData) item.getData());
      }
    };

    bindItems();
    this.contentView.setAdapter(adapter);

    return contentView;
  }

  private String formatShippingAddressSubtitle () {
    return PaymentsSubtitleFormatter.format(paymentReceipt.orderInfo, paymentReceipt.invoice);
  }

  private String getPaymentProcessorName () {
    return tdlib.cache().userDisplayName(paymentReceipt.paymentProviderUserId, false, false);
  }

  private boolean isHeaderFullscreen () {
    return paymentReceipt.photo != null;
  }

  private void updateButtonsColor () {
    if (headerView != null) {
      headerView.getBackButton().setColor(ColorUtils.fromToArgb(Theme.headerBackColor(), Color.WHITE, headerCell != null ? headerCell.getAvatarExpandFactor() : 0f));
      headerView.updateButtonsTransform(getMenuId(), this, getTransformFactor());
    }
  }

  private void updateHeader () {
    if (headerCell != null) {
      TdApi.PhotoSize size = Td.findBiggest(paymentReceipt.photo);
      ImageFile sizeFile = size != null ? new ImageFile(tdlib, size.photo) : null;
      if (sizeFile != null) sizeFile.setScaleType(ImageFile.CENTER_CROP);
      headerCell.setAvatar(size != null ? new ImageFile(tdlib, size.photo) : null, sizeFile);
      headerCell.setText(paymentReceipt.title, tdlib.cache().userDisplayName(paymentReceipt.sellerBotUserId, false, false));
      headerCell.invalidate();
    }
  }

  private void bindItems () {
    Log.d(paymentReceipt.toString());

    ArrayList<ListItem> items = new ArrayList<>();

    if (isHeaderFullscreen()) items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET));
    items.add(new ListItem(ListItem.TYPE_INFO_MULTILINE, R.id.btn_paymentFormDescription, R.drawable.baseline_info_24, R.string.Description, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormMethod, R.drawable.baseline_credit_card_24, 0, false));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormProvider, R.drawable.themanuz_cash_register_24, 0, false));

    if (paymentReceipt.orderInfo != null) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentAddress, R.drawable.baseline_location_on_24, 0, false));
    }

    if (paymentReceipt.shippingOption != null) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_paymentFormShipmentMethod, R.drawable.baseline_local_shipping_24, 0, false));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PaymentFormSummary));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    for (TdApi.LabeledPricePart pricePart : paymentReceipt.invoice.priceParts) {
      items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(pricePart.label, formatCurrency(pricePart.amount), false)));
    }

    if (paymentReceipt.shippingOption != null) {
      for (TdApi.LabeledPricePart pricePart : paymentReceipt.shippingOption.priceParts) {
        items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(pricePart.label, formatCurrency(pricePart.amount), false)));
      }
    }

    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_PAYMENT_PRICE_PART).setData(new PaymentPricePartView.PartData(Lang.getString(R.string.PaymentFormTotal), formatCurrency(paymentFormTotalAmount), true)));

    adapter.setItems(items, false);
  }

  private void updateTotalAmount () {
    paymentFormTotalAmount = 0;
    for (TdApi.LabeledPricePart pricePart : paymentReceipt.invoice.priceParts) paymentFormTotalAmount += pricePart.amount;
    if (paymentReceipt.shippingOption != null) for (TdApi.LabeledPricePart pricePart : paymentReceipt.shippingOption.priceParts) paymentFormTotalAmount += pricePart.amount;
  }

  private String formatCurrency (long amount) {
    if (amount < 0) {
      return "-" + CurrencyUtils.buildAmount(paymentReceipt.invoice.currency, Math.abs(amount));
    } else {
      return CurrencyUtils.buildAmount(paymentReceipt.invoice.currency, amount);
    }
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
