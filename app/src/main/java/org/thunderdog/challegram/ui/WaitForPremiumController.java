package org.thunderdog.challegram.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.billing.BillingConfig;
import org.thunderdog.challegram.billing.BillingManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

public class WaitForPremiumController extends RecyclerViewController<TdApi.AuthorizationStateWaitPremiumPurchase> implements View.OnClickListener {
  public WaitForPremiumController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_waitForPremium;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.login_PremiumRequiredTitle);
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      oneShot = true;
      destroyStackItemById(R.id.controller_code);
      destroyStackItemById(R.id.controller_name);
      destroyStackItemById(R.id.controller_password);
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this);
    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_premium_star_96, Lang.getMarkdownString(this, R.string.login_PremiumRequired)),
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_BUTTON, R.id.btn_buyPremium, 0, R.string.login_PremiumRequiredBtn),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
    }, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_buyPremium) {
      launchPremiumPurchase();
    }
  }

  private void launchPremiumPurchase () {
    BillingManager billingManager = BillingManager.getInstance();

    if (!billingManager.isBillingAvailable()) {
      // Fall back to invoice billing if Google Play is unavailable
      if (BillingConfig.USE_INVOICE_FALLBACK) {
        UI.openUrl("https://telegram.org/");
      }
      return;
    }

    Context ctx = context();
    if (!(ctx instanceof Activity)) {
      if (BillingConfig.USE_INVOICE_FALLBACK) {
        UI.openUrl("https://telegram.org/");
      }
      return;
    }

    Activity activity = (Activity) ctx;
    billingManager.launchPremiumPurchase(activity, tdlib, () -> {
      // Purchase was canceled, do nothing - user can try again
    });
  }
}
