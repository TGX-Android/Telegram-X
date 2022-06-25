package org.thunderdog.challegram.ui;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import me.vkryl.android.widget.FrameLayoutFix;

public class PaymentAddNewCardController extends EditBaseController<PaymentAddNewCardController.Args> implements SettingsAdapter.TextChangeListener {
  @Override
  public int getId () {
    return 0;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {

  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {

  }

  public static class Args {

  }
}
