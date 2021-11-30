package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.data.MessageListManager;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class PinnedMessagesBar extends ViewGroup implements Destroyable, MessageListManager.ChangeListener, View.OnClickListener {
  private CustomRecyclerView recyclerView;
  private SettingsAdapter messagesAdapter;
  private CollapseButton collapseButton;
  private TopBarView showAllButton;

  private boolean animationsDisabled;

  private class CollapseButton extends View {
    public CollapseButton (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      DrawAlgorithms.drawCollapse(c, getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2f, getMeasuredHeight() / 2f, Theme.iconColor(), isExpanded.getFloatValue(), 1f - canExpand.getFloatValue());
    }
  }

  private int lastKnownViewHeight;

  public PinnedMessagesBar (@NonNull Context context) {
    super(context);

    showAllButton = new TopBarView(context);
    showAllButton.setAlpha(0f);
    showAllButton.setCanDismiss(true);
    showAllButton.setDismissListener(barView -> {
      if (messageListener != null) {
        messageListener.onDismissRequest(this);
      }
    });
    showAllButton.setItems(new TopBarView.Item(R.id.btn_showPinnedMessage, R.string.ShowPinnedList, v -> {
      if (messageListener != null) {
        messageListener.onShowAllRequest(this);
      }
    }));
    showAllButton.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, Screen.dp(36f)));
    addView(showAllButton);

    recyclerView = (CustomRecyclerView) Views.inflate(context, R.layout.recycler_custom, null);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
    recyclerView.setVerticalScrollBarEnabled(false);
    Views.setScrollBarPosition(recyclerView);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (messageList == null)
          return;

        int viewportHeight = getRecyclerHeight();

        int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
        int scrollItemCount = messageList.getTotalCount();
        if (scrollItemCount <= 0)
          return;

        float alpha = 1f;
        float focusPosition = getFocusPosition();

        //

        final int color = ColorUtils.alphaColor(alpha, Theme.chatVerticalLineColor());
        final int backgroundColor = ColorUtils.alphaColor(.3f * alpha, color);

        //

        final float visibleItemCount = Math.min(scrollItemCount, (float) viewportHeight / (float) itemHeight);

        final int lineX = Screen.dp(10f);
        final float strokeWidth = Screen.dp(1.5f);
        final int outerSpacing = Screen.dp(6f); // spacing before the first and the last item
        final int minSpacingBetweenItems = Screen.dp(3f);
        final float lineStartX = lineX - strokeWidth;
        final float lineEndX = lineX + strokeWidth;

        final int maxLineHeight = itemHeight - outerSpacing * 2;
        final int minLineHeight = (maxLineHeight - minSpacingBetweenItems * 3) / 4;

        final int lineHeight = Math.min(maxLineHeight, Math.max(minLineHeight, ((viewportHeight - outerSpacing * 2 - minSpacingBetweenItems * (scrollItemCount - 1))) / scrollItemCount));
        final int spacingBetweenItems = scrollItemCount <= 1 ? 0 : MathUtils.fromTo(minSpacingBetweenItems, outerSpacing * 2, (float) (visibleItemCount - 1) / (float) (scrollItemCount - 1));

        final int visibleLineCount = Math.min(scrollItemCount, viewportHeight / lineHeight + 1);

        final float scrollFactor = scrollItemCount == visibleItemCount ? 1f : focusPosition / (float) (scrollItemCount - visibleItemCount);

        final int fromPosition = Math.max(0, (int) (focusPosition - Math.ceil(visibleLineCount * scrollFactor)));
        final int toPosition = Math.min(scrollItemCount, (int) Math.ceil(focusPosition) + visibleLineCount + 1);

        RectF rectF = Paints.getRectF();

        float lineScrollY = Math.max(0, lineHeight * scrollItemCount + spacingBetweenItems * (scrollItemCount - 1) + outerSpacing * 2 - viewportHeight);

        float lineBeginY = viewportHeight - outerSpacing + lineScrollY * scrollFactor;

        for (int position = fromPosition; position < toPosition; position++) {
          float lineEndY = lineBeginY - (float) Math.ceil((lineHeight + spacingBetweenItems) * position);
          float lineStartY = lineEndY - lineHeight;

          boolean isFull = focusPosition == position || (position > (int) focusPosition && position < (int) (focusPosition + visibleItemCount));

          rectF.set(lineStartX, lineStartY, lineEndX, lineEndY);
          c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(isFull ? color : backgroundColor));

          if (!isFull) {
            if (position == (int) focusPosition && focusPosition > position) {
              rectF.set(lineStartX, lineStartY, lineEndX, lineEndY + (lineStartY - lineEndY) * (focusPosition - (float) position));
              c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(color));
            } else {
              float remain = focusPosition + visibleItemCount - position;
              if (remain > 0f && remain < 1f) {
                rectF.set(lineStartX, lineEndY + (lineStartY - lineEndY) * remain, lineEndX, lineEndY);
                c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(color));
              }
            }
          }
        }

        c.drawRect(0, parent.getMeasuredHeight() - Screen.separatorSize(), parent.getMeasuredWidth(), parent.getMeasuredHeight(), Paints.fillingPaint(ColorUtils.alphaColor(getExpandFactor() * MathUtils.clamp(focusPosition), Theme.separatorColor())));
      }
    });
    addView(recyclerView);

    collapseButton = new CollapseButton(context);
    collapseButton.setOnClickListener(v -> {
      if (canExpand.getValue() || isExpanded.getValue()) {
        isExpanded.toggleValue(true);
      } else {
        if (messageListener != null) {
          messageListener.onDismissRequest(this);
        }
      }
    });
    collapseButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(collapseButton);
    addView(collapseButton);

    ViewSupport.setThemedBackground(this, R.id.theme_color_filling, null);
    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_filling, null);
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setWillNotDraw(false);
  }

  public void setAnimationsDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      recyclerView.setItemAnimator(animationsDisabled ? null : new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    c.drawRect(0, getRecyclerHeight(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
  }

  private float getFocusPosition () {
    int viewportHeight = recyclerView.getMeasuredHeight();
    int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
    int childCount = recyclerView.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = recyclerView.getChildAt(i);
      if (view instanceof MessagePreviewView) {
        int adapterPosition = recyclerView.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          int scrollY = view.getBottom() - viewportHeight;
          return adapterPosition + (float) scrollY / (float) itemHeight;
        }
      }
    }
    return 0f;
  }

  private void updateContentInsets () {
    float focusPosition = getFocusPosition();
    final float collapse = 1f - getExpandFactor();
    final int contentInset = Screen.dp(28f);
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View view = recyclerView.getChildAt(i);
      if (view instanceof MessagePreviewView) {
        int adapterPosition = recyclerView.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          int inset = collapse == 1f ?
            contentInset :
            collapse == 0f ? 0 :
            Math.round((float) contentInset * (1f - MathUtils.clamp(Math.abs((float) adapterPosition - focusPosition))) * collapse);
          ((MessagePreviewView) view).setContentInset(inset);
        }
      }
    }
  }

  @CallSuper
  protected void onViewportChanged () {
    updateContentInsets();
  }

  private final FactorAnimator.Target animationTarget = new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      checkHeight();
      recyclerView.invalidate();
      collapseButton.invalidate();
      showAllButton.setAlpha(getExpandFactor());
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == 1 && finalFactor == 0f) {
        isExpanded.setValue(false, false);
      }
    }
  };
  private final BoolAnimator canExpand = new BoolAnimator(1, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator isExpanded = new BoolAnimator(0, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private void checkHeight () {
    int newHeight = getTotalHeight();
    if (newHeight != lastKnownViewHeight) {
      lastKnownViewHeight = newHeight;
      requestLayout();
      onViewportChanged();

      float expand = getExpandFactor();
      if (expand == 1f || expand == 0f) {
        updateContentInsets();
        /*LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastCompletelyVisibleItemPosition();
        if (first > 0) {
          messagesAdapter.notifyItemRangeChanged(0, first);
        }
        if (last < messagesAdapter.getItemCount()) {
          messagesAdapter.notifyItemRangeChanged(last + 1, messagesAdapter.getItemCount() - (last + 1));
        }*/
      }
    }
  }

  private float getExpandFactor () {
    return isExpanded.getFloatValue() * canExpand.getFloatValue();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int newHeight = getTotalHeight();
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));
    recyclerView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getRecyclerHeight(), MeasureSpec.EXACTLY));
    collapseButton.measure(MeasureSpec.makeMeasureSpec( Screen.dp(40f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MathUtils.fromTo(SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW), Screen.dp(36f), getExpandFactor()), MeasureSpec.EXACTLY));
    showAllButton.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(36f), MeasureSpec.EXACTLY));
    if (newHeight != lastKnownViewHeight) {
      lastKnownViewHeight = newHeight;
      onViewportChanged();
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    showAllButton.layout(l, b - Screen.dp(36f), r, b);
    recyclerView.layout(l, t, r, getRecyclerHeight());
    collapseButton.layout(r - collapseButton.getMeasuredWidth(), b - collapseButton.getMeasuredHeight(), r, b);
  }

  private void updateContentInset (MessagePreviewView view, int position) {
    float collapse = 1f - getExpandFactor();
    int contentInset = Screen.dp(28f);
    int inset = collapse == 1f ?
      contentInset :
      collapse == 0f ? 0 :
        Math.round((float) contentInset * (1f - MathUtils.clamp(Math.abs((float) position - getFocusPosition()))) * collapse);
    view.setContentInset(inset);
  }

  public void initialize (@NonNull ViewController<?> viewController) {
    messagesAdapter = new SettingsAdapter(viewController, this, viewController) {
      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        TdApi.Message message = (TdApi.Message) item.getData();
        previewView.setMessage(message, new TdApi.SearchMessagesFilterPinned(), item.getStringValue(), false);
        updateContentInset(previewView, position);
      }

      @Override
      public void onViewAttachedToWindow (SettingHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.itemView instanceof MessagePreviewView) {
          int position = holder.getAdapterPosition();
          updateContentInset((MessagePreviewView) holder.itemView, position);
        }
      }
    };
    recyclerView.setAdapter(messagesAdapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            View view = manager.findViewByPosition(firstVisiblePosition);
            if (view != null) {
              int offsetY = Math.max(0, view.getBottom() - recyclerView.getMeasuredHeight());
              if (offsetY != 0) {
                if (offsetY > view.getMeasuredHeight() / 2) {
                  recyclerView.smoothScrollBy(0, offsetY - view.getMeasuredHeight());
                } else {
                  recyclerView.smoothScrollBy(0, offsetY);
                }
              }
            }
          }
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (messageList != null && dy != 0 && manager != null) {
          int lastVisiblePosition = manager.findLastVisibleItemPosition();
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (lastVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition + 15 >= messageList.getCount()) {
            messageList.loadItems(false, null);
          } else if (firstVisiblePosition != RecyclerView.NO_POSITION && firstVisiblePosition - 5 <= 0) {
            messageList.loadItems(true, null);
          }
        }
        float expand = getExpandFactor();
        if (expand > 0f && expand < 1f) {
          updateContentInsets();
        }
      }
    });
    viewController.addThemeInvalidateListener(this);
    viewController.addThemeInvalidateListener(recyclerView);
    viewController.addThemeInvalidateListener(collapseButton);
    showAllButton.addThemeListeners(viewController);
  }

  public interface MessageListener {
    void onMessageClick (PinnedMessagesBar view, TdApi.Message message);
    void onDismissRequest (PinnedMessagesBar view);
    void onShowAllRequest (PinnedMessagesBar view);
  }

  private MessageListener messageListener;

  public void setMessageListener (MessageListener messageListener) {
    this.messageListener = messageListener;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.message) {
      ListItem item = ((ListItem) v.getTag());
      TdApi.Message message = (TdApi.Message) item.getData();
      if (messageListener != null) {
        messageListener.onMessageClick(this, message);
      }
    }
  }

  private final FactorAnimator countAnimator = new FactorAnimator(2, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public void onTotalCountChanged (ListManager<TdApi.Message> list, int totalCount) {
    if (totalCount != 0 || isExpanded.getFloatValue() == 0f) {
      canExpand.setValue(totalCount > 1, true);
    }
    float toCount = Math.max(0f, Math.min(3f, totalCount - 1));
    if (getExpandFactor() > 0f) {
      countAnimator.animateTo(toCount);
    } else {
      countAnimator.forceFactor(toCount);
    }
  }

  public int getTotalHeight () {
    return getRecyclerHeight() + getBottomBarHeight();
  }

  private int getBottomBarHeight () {
    return (int) (Screen.dp(36f) * getExpandFactor());
  }

  private int getRecyclerHeight () {
    int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
    return itemHeight + Math.round((float) itemHeight * countAnimator.getFactor() * getExpandFactor());
  }

  private MessageListManager messageList;

  public void collapse (boolean animated) {
    this.isExpanded.setValue(false, animated);
  }

  public void setMessageList (@Nullable MessageListManager messageList) {
    if (this.messageList == messageList)
      return;
    if (this.messageList != null) {
      this.messageList.removeChangeListener(this);
      this.messageList = null;
    }
    this.messageList = messageList;
    collapse(false);
    canExpand.setValue(messageList != null && messageList.getTotalCount() > 1, false);
    countAnimator.forceFactor(Math.max(0f, Math.min(3f, messageList != null ? messageList.getTotalCount() - 1 : 0)));
    if (messageList != null) {
      messageList.addChangeListener(this);
      List<ListItem> items = new ArrayList<>(messageList.getCount());
      for (TdApi.Message message : messageList) {
        items.add(itemOf(message));
      }
      messagesAdapter.setItems(items, false);
      messageList.loadInitialChunk(null);
    } else {
      messagesAdapter.setItems(new ListItem[0], false);
    }
  }

  private long maxFocusMessageId;

  public void setMaxFocusMessageId (long maxFocusMessageId) {
    if (this.maxFocusMessageId != maxFocusMessageId) {
      this.maxFocusMessageId = maxFocusMessageId;
      // TODO animate focus bar on the left
      // TODO ensure availability of pinned messages
    }
  }

  private static ListItem itemOf (TdApi.Message message) {
    return new ListItem(ListItem.TYPE_MESSAGE_PREVIEW, R.id.message).setData(message);
  }

  @Override
  public void performDestroy () {
    setMessageList(null);
  }

  @Override
  public void onItemsAdded (ListManager<TdApi.Message> list, List<TdApi.Message> items, int startIndex, boolean isInitialChunk) {
    ListItem[] messageList = new ListItem[items.size()];
    int index = 0;
    for (TdApi.Message message : items) {
      messageList[index] = itemOf(message);
      index++;
    }
    if (needsClear || messagesAdapter.getItems().isEmpty()) {
      needsClear = false;
      messagesAdapter.setItems(messageList, false);
    } else {
      messagesAdapter.addItems(startIndex, messageList);
    }
  }

  @Override
  public void onItemAdded (ListManager<TdApi.Message> list, TdApi.Message item, int toIndex) {
    if (needsClear || messagesAdapter.getItems().isEmpty()) {
      needsClear = false;
      messagesAdapter.setItems(new ListItem[] {
        itemOf(item)
      }, false);
    } else {
      boolean scrollBy = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition() == 0;
      messagesAdapter.addItem(toIndex, itemOf(item));
      if (scrollBy) {
        ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
      }
    }
  }

  @Override
  public void onItemMoved (ListManager<TdApi.Message> list, TdApi.Message item, int fromIndex, int toIndex) {
    messagesAdapter.moveItem(fromIndex, toIndex);
  }

  private boolean needsClear;

  @Override
  public void onItemRemoved (ListManager<TdApi.Message> list, TdApi.Message removedItem, int fromIndex) {
    if (fromIndex == 0 && messagesAdapter.getItems().size() == 1) {
      needsClear = true;
    } else {
      messagesAdapter.removeItem(fromIndex);
    }
  }

  @Override
  public void onItemChanged (ListManager<TdApi.Message> list, TdApi.Message item, int index, int cause) {
    if (cause == MessageListManager.CAUSE_CONTENT_CHANGED) {
      messagesAdapter.notifyItemChanged(index);
    }
  }
}
