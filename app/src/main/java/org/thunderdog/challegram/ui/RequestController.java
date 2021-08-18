package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.RunnableBool;

/**
 * Date: 12/8/17
 * Author: default
 */

public class RequestController extends EditBaseController<RequestController.Delegate> implements SettingsAdapter.TextChangeListener {
  public interface Delegate {
    CharSequence getName ();
    int getPlaceholder ();
    void performRequest (String input, RunnableBool callback);
  }

  public RequestController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_request;
  }

  @Override
  public CharSequence getName () {
    return getArgumentsStrict().getName();
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), false);
      }
    };
    adapter.setTextChangeListener(this);
    adapter.setLockFocusOn(this, true);
    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_EDITTEXT, R.id.input, 0, getArgumentsStrict().getPlaceholder())
    }, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    setDoneVisible(true);
  }

  private String currentInput;

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    currentInput = text;
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    adapter.updateLockEditTextById(R.id.input, inProgress ? currentInput : null);
  }

  @Override
  protected final boolean onDoneClick () {
    if (!isInProgress()) {
      setInProgress(true);
      getArgumentsStrict().performRequest(currentInput, result -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setInProgress(false);
          if (result) {
            onSaveCompleted();
          }
        }
      }));
    }
    return true;
  }
}
