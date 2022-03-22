package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;

public class MessageReactorsController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private final long chatId;
  private final long msgId;
  private final int reactionCount;

  public static CharSequence getViewString (int count) {
    return Lang.pluralBold(R.string.MessageXReacted, count);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  public MessageReactorsController (MediaLayout context, long chatId, long msgId, int reactionCount) {
    super(context, getViewString(reactionCount).toString());
    this.reactionCount = reactionCount;
    this.chatId = chatId;
    this.msgId = msgId;
  }

  private boolean allowExpand;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    // Build ViewPager's

    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();
    load();

    return contentView;
  }

  private void load () {
    //tdlib.client().send(new TdApi.GetMessageAddedReactions());
  }

  @Override
  protected int getInitialContentHeight () {
    int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_USER) * reactionCount;
    initialContentHeight += Screen.dp(24f);
    return Math.min(super.getInitialContentHeight(), initialContentHeight);
  }

  @Override
  protected boolean canExpandHeight () {
    return allowExpand;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  @Override
  public int getId () {
    return R.id.controller_messageReacted;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      mediaLayout.hide(false);
      tdlib.ui().openPrivateProfile(this, ((ListItem) v.getTag()).getLongId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }
}