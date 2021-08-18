package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2019-12-16
 * Author: default
 */
public class EditLinkedChatController extends RecyclerViewController<EditLinkedChatController.Args> implements View.OnClickListener {
  public static class Args {
    private final long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public EditLinkedChatController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_linkChat;
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this, this, this);
    List<ListItem> items = new ArrayList<>();

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.chat: {
        break;
      }
    }
  }
}
