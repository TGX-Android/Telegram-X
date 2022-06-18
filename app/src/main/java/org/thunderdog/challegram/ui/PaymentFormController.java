package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

public class PaymentFormController extends RecyclerViewController<PaymentFormController.Args> implements View.OnClickListener {
  public static class Args {
    private final TdApi.PaymentForm paymentForm;

    public Args (TdApi.PaymentForm paymentForm) {
      this.paymentForm = paymentForm;
    }
  }

  private TdApi.PaymentForm paymentForm;
  private SettingsAdapter adapter;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    paymentForm = args.paymentForm;
  }

  public PaymentFormController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_paymentForm;
  }

  @Override
  public void onClick (View v) {

  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {

    };

    recyclerView.setAdapter(adapter);
    bindItems();
  }

  private void bindItems () {
    ArrayList<ListItem> items = new ArrayList<>();


    adapter.setItems(items, false);
  }
}
