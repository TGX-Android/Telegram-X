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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;

import me.vkryl.android.widget.FrameLayoutFix;

public class MessageReactorsController extends MediaBottomBaseController<Void> {
  private final long chatId;
  private final long msgId;
  private final int reactionCount;
  private final TdApi.MessageReaction[] reactions;

  private ViewPagerHeaderViewCompact headerCell;

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

  public MessageReactorsController (MediaLayout context, long chatId, long msgId, int reactionCount, TdApi.MessageReaction[] reactions) {
    super(context, getViewString(reactionCount).toString());
    this.reactions = reactions;
    this.reactionCount = reactionCount;
    this.chatId = chatId;
    this.msgId = msgId;
  }

  private boolean allowExpand;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    // Build ViewPagers + header
    headerCell = new ViewPagerHeaderViewCompact(context);
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) ((ViewPagerHeaderViewCompact) headerCell).getRecyclerView().getLayoutParams();
    if (getBackButton() != BackHeaderButton.TYPE_NONE) {
      if (Lang.rtl()) {
        params.rightMargin = Screen.dp(56f);
      } else {
        params.leftMargin = Screen.dp(56f);
      }
    }
    addThemeInvalidateListener(headerCell.getTopView());
    headerCell.getTopView().checkRtl();
    headerCell.getTopView().setItems(new String[0]);
    for (TdApi.MessageReaction r: reactions) {
      headerCell.getTopView().addItem(r.reaction + "   " + r.totalCount);
    }
    headerCell.getTopView().setSelectionFactor(0f);

    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();

    return contentView;
  }

  @Override
  protected void onCompleteShow (boolean isPopup) {
    //mediaLayout.getHeaderView().getFilling().setColor(Theme.getColor(R.id.theme_color_background));
    //mediaLayout.getHeaderView().setVisibility(View.GONE);
  }

  @Override
  public View getCustomHeaderCell () {
    return (View) headerCell;
  }

  @Override
  public boolean anchorHeaderToContent () {
    return true;
  }

  @Override
  public float getContentTranslationY () {
    return recyclerView.getTranslationY();
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
}