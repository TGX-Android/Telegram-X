package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.EmptySmartView;

import java.util.ArrayList;

/**
 * Date: 2019-09-19
 * Author: default
 */
public class SharedRestrictionController extends SharedBaseController<InlineResult<?>> {
  public SharedRestrictionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private String restrictionReason;

  public SharedRestrictionController setRestrictionReason (String restrictionReason) {
    this.restrictionReason = restrictionReason;
    return this;
  }

  @Override
  protected boolean supportsMessageContent () {
    return false;
  }

  @Override
  protected int getEmptySmartMode () {
    return EmptySmartView.MODE_EMPTY_RESTRICTED;
  }

  @Override
  protected String getEmptySmartArgument () {
    return restrictionReason;
  }

  @Override
  protected boolean isAlwaysEmpty () {
    return true;
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<InlineResult<?>> data) {
    return null;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TabMedia);
  }

  @Override
  protected InlineResult<?> parseObject (TdApi.Object object) {
    return null;
  }

  @Override
  protected TdApi.Function buildRequest (long chatId, long messageThreadId, String query, long offset, String secretOffset, int limit) {
    return null;
  }

  @Override
  protected int provideViewType () {
    return 0;
  }

  @Override
  public void onClick (View v) {

  }
}
