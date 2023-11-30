/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.MessageListManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.MessageId;

public class PinnedMessagesBar extends ViewGroup implements Destroyable, MessageListManager.ChangeListener, View.OnClickListener {
  private CustomRecyclerView recyclerView;
  private SettingsAdapter messagesAdapter;
  private TdlibMessageViewer.Viewport messageViewport;
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

  private final RecyclerView.ItemAnimator itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private final boolean reverseLayout;

  public PinnedMessagesBar (@NonNull Context context, boolean reverseLayout) {
    super(context);

    this.reverseLayout = reverseLayout;

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
    recyclerView.setItemAnimator(itemAnimator);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS : RecyclerView.OVER_SCROLL_NEVER);
    recyclerView.setVerticalScrollBarEnabled(false);
    Views.setScrollBarPosition(recyclerView);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, reverseLayout));
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (messagesAdapter.getItems().isEmpty()) {
          setOverScrollDisabled(true);
          return;
        }

        int viewportHeight = getRecyclerHeight();

        int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
        int scrollItemCount = messageList != null ? messageList.getTotalCount() : messagesAdapter.getItems().size();
        if (scrollItemCount <= 0) {
          setOverScrollDisabled(true);
          return;
        }

        final float alpha = 1f;

        //

        final int defaultFillColor = ColorUtils.alphaColor(alpha, Theme.chatVerticalLineColor());
        final int defaultBackgroundColor = ColorUtils.alphaColor(.3f * alpha, defaultFillColor);

        //

        final float visibleItemCount = Math.min(scrollItemCount, (float) viewportHeight / (float) itemHeight);

        //

        float focusPosition = getFocusPosition();
        if (!reverseLayout) {
          focusPosition = scrollItemCount - 1 - focusPosition;
        }

        //

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

        setOverScrollDisabled(fromPosition == 0 && toPosition == scrollItemCount);

        RectF rectF = Paints.getRectF();

        float lineScrollY = Math.max(0, lineHeight * scrollItemCount + spacingBetweenItems * (scrollItemCount - 1) + outerSpacing * 2 - viewportHeight);

        float lineBeginY = viewportHeight - outerSpacing + lineScrollY * scrollFactor;

        for (int position = fromPosition; position < toPosition; position++) {
          float lineEndY = lineBeginY - (float) Math.ceil((lineHeight + spacingBetweenItems) * position);
          float lineStartY = lineEndY - lineHeight;

          int dataPosition;
          if (reverseLayout) {
            dataPosition = position;
          } else {
            dataPosition = scrollItemCount - 1 - position;
          }
          Entry entry = null;
          if (messagesAdapter != null && dataPosition >= 0 && dataPosition < messagesAdapter.getItemCount()) {
            entry = (Entry) messagesAdapter.getItems().get(dataPosition).getData();
          }
          final int fillColor, backgroundColor;
          if (entry != null && entry.accentColor != null) {
            fillColor = ColorUtils.alphaColor(alpha, entry.accentColor.getVerticalLineColor());
            backgroundColor = ColorUtils.alphaColor(.3f * alpha, fillColor);
          } else {
            fillColor = defaultFillColor;
            backgroundColor = defaultBackgroundColor;
          }

          boolean isFull = focusPosition == position || (position > (int) focusPosition && position < (int) (focusPosition + visibleItemCount));

          rectF.set(lineStartX, lineStartY, lineEndX, lineEndY);
          c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(isFull ? fillColor : backgroundColor));

          if (!isFull) {
            if (position == (int) focusPosition && focusPosition > position) {
              rectF.set(lineStartX, lineStartY, lineEndX, lineEndY + (lineStartY - lineEndY) * (focusPosition - (float) position));
              c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(fillColor));
            } else {
              float remain = focusPosition + visibleItemCount - position;
              if (remain > 0f && remain < 1f) {
                rectF.set(lineStartX, lineEndY + (lineStartY - lineEndY) * remain, lineEndX, lineEndY);
                c.drawRoundRect(rectF, strokeWidth, strokeWidth, Paints.fillingPaint(fillColor));
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

    ViewSupport.setThemedBackground(this, ColorId.filling, null);
    ViewSupport.setThemedBackground(recyclerView, ColorId.filling, null);
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setWillNotDraw(false);
  }

  private boolean ignoreAlbums;

  public void setIgnoreAlbums (boolean ignoreAlbums) {
    this.ignoreAlbums = ignoreAlbums;
  }

  public void setCollapseButtonVisible (boolean isVisible) {
    collapseButton.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    updateContentInsets();
  }

  public void setAnimationsDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      recyclerView.setItemAnimator(animationsDisabled ? null : itemAnimator);
    }
  }

  public boolean areAnimationsDisabled () {
    return animationsDisabled;
  }

  @Override
  protected void onDraw (Canvas c) {
    c.drawRect(0, getRecyclerHeight(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
  }

  private int getContentInset () {
    return collapseButton.getVisibility() == View.VISIBLE ? Screen.dp(28f) : 0;
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
          int scrollY;
          if (reverseLayout) {
            scrollY = view.getBottom() - viewportHeight;
          } else {
            scrollY = -view.getTop();
          }
          return adapterPosition + (float) scrollY / (float) itemHeight;
        }
      }
    }
    return 0f;
  }

  private void updateContentInsets () {
    float focusPosition = getFocusPosition();
    final float collapse = 1f - getExpandFactor();
    final int contentInset = getContentInset();
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View view = recyclerView.getChildAt(i);
      if (view instanceof MessagePreviewView) {
        int adapterPosition = recyclerView.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          updateContentInset((MessagePreviewView) view, adapterPosition, focusPosition, contentInset, collapse);
        }
      }
    }
  }

  private boolean overScrollDisabled;

  private void setOverScrollDisabled (boolean isDisabled) {
    if (this.overScrollDisabled != isDisabled) {
      this.overScrollDisabled = isDisabled;
      recyclerView.setOverScrollMode(isDisabled ? RecyclerView.OVER_SCROLL_NEVER : RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
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
    int horizontalPadding = getPaddingLeft() + getPaddingRight();
    if (horizontalPadding != 0) {
      int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec) - horizontalPadding;
      widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
    }
    recyclerView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getRecyclerHeight(), MeasureSpec.EXACTLY));
    showAllButton.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(36f), MeasureSpec.EXACTLY));

    collapseButton.measure(MeasureSpec.makeMeasureSpec(Screen.dp(40f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MathUtils.fromTo(SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW), Screen.dp(36f), getExpandFactor()), MeasureSpec.EXACTLY));
    if (newHeight != lastKnownViewHeight) {
      lastKnownViewHeight = newHeight;
      onViewportChanged();
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    l += getPaddingLeft();
    r -= getPaddingRight();
    showAllButton.layout(l, b - Screen.dp(36f), r, b);
    recyclerView.layout(l, t, r, getRecyclerHeight());
    collapseButton.layout(r - collapseButton.getMeasuredWidth(), b - collapseButton.getMeasuredHeight(), r, b);
  }

  private void updateContentInset (MessagePreviewView view, int position) {
    updateContentInset(view, position, getFocusPosition(), getContentInset(), 1f - getExpandFactor());
  }

  private void updateContentInset (MessagePreviewView view, int position, float focusPosition, int contentInset, float collapse) {
    if (contentInset == 0) {
      view.setContentInset(0);
      return;
    }
    int inset = collapse == 1f ?
      contentInset :
      collapse == 0f ? 0 :
        Math.round((float) contentInset * (1f - MathUtils.clamp(Math.abs((float) position - focusPosition))) * collapse);
    view.setContentInset(inset);
  }

  public void initialize (@NonNull ViewController<?> viewController) {
    messageViewport = viewController.tdlib().messageViewer().createViewport(new TdApi.MessageSourceSearch(), viewController);
    messagesAdapter = new SettingsAdapter(viewController, this, viewController) {
      @Nullable
      @Override
      protected View createModifiedView (ViewGroup parent, int viewType, View view) {
        if (viewType == ListItem.TYPE_MESSAGE_PREVIEW && messageListener != null) {
          messageListener.onCreateMessagePreview(PinnedMessagesBar.this, (MessagePreviewView) view);
        }
        return null;
      }

      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        Entry data = (Entry) item.getData();
        if (data.isLinkPreview()) {
          LinkPreview linkPreview = data.linkPreviewContext.getLinkPreview(data.linkPreviewUrl);
          previewView.setLinkPreview(linkPreview, (v, currentLinkPreview) -> {
            if (messageListener != null && messageListener.onToggleLargeMedia(PinnedMessagesBar.this, v, data.linkPreviewContext, currentLinkPreview)) {
              v.updateShowSmallMedia(true);
            }
          });
        } else if (data.isMessage()) {
          TdApi.Message message = data.message;
          TdApi.FormattedText quote = data.quote;
          previewView.setMessage(message, quote, new TdApi.SearchMessagesFilterPinned(), item.getStringValue(), ignoreAlbums ? MessagePreviewView.Options.IGNORE_ALBUM_REFRESHERS : MessagePreviewView.Options.NONE);
          if (messageList == null) {
            // override message preview
            MessageId highlightMessageId;
            //noinspection ConstantConditions
            if (data.tdlib.isChannelAutoForward(message) && message.forwardInfo.fromChatId == contextChatId) {
              highlightMessageId = new MessageId(message.forwardInfo.fromChatId, message.forwardInfo.fromMessageId);
            } else {
              highlightMessageId = new MessageId(message.chatId, message.id);
            }
            previewView.setPreviewChatId(null, highlightMessageId.getChatId(), null, highlightMessageId, null);
          }
          if (messageListener != null) {
            messageListener.onMessageDisplayed(PinnedMessagesBar.this, previewView, message);
          }
        } else {
          throw new UnsupportedOperationException();
        }
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
              if (reverseLayout) {
                int offsetY = Math.max(0, view.getBottom() - recyclerView.getMeasuredHeight());
                if (offsetY != 0) {
                  if (offsetY > view.getMeasuredHeight() / 2) {
                    recyclerView.smoothScrollBy(0, offsetY - view.getMeasuredHeight());
                  } else {
                    recyclerView.smoothScrollBy(0, offsetY);
                  }
                }
              } else {
                int offsetY = Math.min(0, view.getTop());
                if (offsetY != 0) {
                  if (-offsetY > view.getMeasuredHeight() / 2) {
                    recyclerView.smoothScrollBy(0, view.getMeasuredHeight() + offsetY);
                  } else {
                    recyclerView.smoothScrollBy(0, offsetY);
                  }
                }
              }
            }
          }
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager != null) {
          int lastVisiblePosition = manager.findLastVisibleItemPosition();
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (messageList != null && dy != 0) {
            if (lastVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition + 15 >= messageList.getCount()) {
              messageList.loadItems(false, null);
            } else if (firstVisiblePosition != RecyclerView.NO_POSITION && firstVisiblePosition - 5 <= 0) {
              messageList.loadItems(true, null);
            }
          }

          int focusIndex = firstVisiblePosition;
          View view = manager.findViewByPosition(firstVisiblePosition);
          if (view != null) {
            if (reverseLayout) {
              int offsetY = Math.max(0, view.getBottom() - recyclerView.getMeasuredHeight());
              if (offsetY > view.getMeasuredHeight() / 2) {
                focusIndex++;
              }
            } else {
              int offsetY = Math.min(0, view.getTop());
              if (-offsetY > view.getMeasuredHeight() / 2) {
                focusIndex++;
              }
            }
          }
          setFocusIndex(focusIndex);
        }
        float expand = getExpandFactor();
        if (expand > 0f && expand < 1f) {
          updateContentInsets();
        }
      }
    });
    viewController.tdlib().ui().attachViewportToRecyclerView(messageViewport, recyclerView);
    viewController.addThemeInvalidateListener(this);
    viewController.addThemeInvalidateListener(recyclerView);
    viewController.addThemeInvalidateListener(collapseButton);
    showAllButton.addThemeListeners(viewController);
  }

  private int focusIndex = RecyclerView.NO_POSITION;

  private void setFocusIndex (int focusIndex) {
    if (this.focusIndex != focusIndex) {
      this.focusIndex = focusIndex;
      ListItem item = messagesAdapter.getItem(focusIndex);
      if (item != null) {
        Entry entry = (Entry) item.getData();
        if (entry.isLinkPreview()) {
          messageListener.onSelectLinkPreviewUrl(this, entry.linkPreviewContext, entry.linkPreviewUrl);
        }
      }
    }
  }

  public interface MessageListener {
    void onMessageClick (PinnedMessagesBar view, TdApi.Message message, @Nullable TdApi.FormattedText quote);
    default void onSelectLinkPreviewUrl (PinnedMessagesBar view, MessagesController.MessageInputContext messageContext, String url) { }
    default boolean onToggleLargeMedia (PinnedMessagesBar view, MessagePreviewView previewView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview) {
      return false;
    }
    default void onDismissRequest (PinnedMessagesBar view) { }
    default void onShowAllRequest (PinnedMessagesBar view) { }
    default void onCreateMessagePreview (PinnedMessagesBar view, MessagePreviewView previewView) { }
    default void onMessageDisplayed (PinnedMessagesBar view, MessagePreviewView previewView, TdApi.Message message) { }
  }

  private MessageListener messageListener;

  public void setMessageListener (MessageListener messageListener) {
    this.messageListener = messageListener;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.message) {
      ListItem item = ((ListItem) v.getTag());
      Entry entry = (Entry) item.getData();
      if (messageListener != null) {
        if (entry.isLinkPreview()) {
          messageListener.onSelectLinkPreviewUrl(this, entry.linkPreviewContext, entry.linkPreviewUrl);
        } else if (entry.isMessage()) {
          messageListener.onMessageClick(this, entry.message, entry.quote);
        } else {
          // TODO
        }
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

  public static class Entry implements Destroyable {
    public final Tdlib tdlib;

    public final TdApi.Message message;
    public final @Nullable TdApi.FormattedText quote;
    public final TdlibAccentColor accentColor;

    public final MessagesController.MessageInputContext linkPreviewContext;
    public final String linkPreviewUrl;

    public Entry (Tdlib tdlib, TdApi.Message message, @Nullable TdApi.FormattedText quote) {
      this.tdlib = tdlib;
      this.message = message;
      this.linkPreviewContext = null;
      this.linkPreviewUrl = null;
      this.quote = quote;
      this.accentColor = tdlib.messageAccentColor(message);
    }

    public Entry (Tdlib tdlib, @NonNull MessagesController.MessageInputContext linkPreviewContext, @NonNull String linkPreviewUrl) {
      this.tdlib = tdlib;
      this.linkPreviewContext = linkPreviewContext;
      this.linkPreviewUrl = linkPreviewUrl;
      this.message = null;
      this.accentColor = null;
      this.quote = null;
    }

    public boolean isLinkPreview () {
      return linkPreviewUrl != null;
    }

    public boolean isMessage () {
      return message != null;
    }

    public void subscribeToUpdates () {
      // TODO subscribe to updates
    }

    @Override
    public void performDestroy () {
      // TODO
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Entry)) {
        return false;
      }
      Entry b = (Entry) obj;
      if (isMessage() != b.isMessage() || isLinkPreview() != b.isLinkPreview()) {
        return false;
      }
      if (isLinkPreview()) {
        return StringUtils.equalsTo(this.linkPreviewUrl, b.linkPreviewUrl) && (this.linkPreviewContext == b.linkPreviewContext);
      } else if (isMessage()) {
        return this.message.chatId == b.message.chatId && this.message.id == b.message.id;
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  private long contextChatId;
  private @Nullable List<Entry> staticList;
  private @Nullable MessageListManager messageList;

  public void collapse (boolean animated) {
    this.isExpanded.setValue(false, animated);
  }

  private void clearMessageList () {
    if (this.staticList != null) {
      for (Entry entry : staticList) {
        entry.performDestroy();
      }
      this.staticList = null;
    }
    if (this.messageList != null) {
      this.messageList.removeChangeListener(this);
      this.messageList = null;
    }
    this.focusIndex = RecyclerView.NO_POSITION;
  }

  public void setMessageList (@Nullable MessageListManager messageList) {
    if (this.messageList == messageList)
      return;
    clearMessageList();
    this.messageList = messageList;
    collapse(false);
    canExpand.setValue(messageList != null && messageList.getTotalCount() > 1, false);
    countAnimator.forceFactor(Math.max(0f, Math.min(3f, messageList != null ? messageList.getTotalCount() - 1 : 0)));
    if (messageList != null) {
      messageList.addChangeListener(this);
      List<ListItem> items = new ArrayList<>(messageList.getCount());
      for (TdApi.Message message : messageList) {
        items.add(itemOf(messageList.tdlib(), message));
      }
      messagesAdapter.setItems(items, false);
      messageList.loadInitialChunk(null);
    } else {
      messagesAdapter.setItems(new ListItem[0], false);
    }
  }

  public void setMessage (@Nullable Tdlib tdlib, @Nullable TdApi.Message message) {
    setMessage(tdlib, message, null);
  }

  public void setStaticMessageList (@Nullable List<Entry> entries, int scrollToPosition) {
    if (entries == null || entries.isEmpty()) {
      setMessageList(null);
      return;
    }
    if (this.staticList != null && this.staticList.size() == entries.size()) {
      boolean contentEquals = true;
      for (int i = 0; i < entries.size(); i++) {
        Entry oldEntry = this.staticList.get(i);
        Entry newEntry = entries.get(i);
        // TODO better check
        if (!oldEntry.equals(newEntry)) {
          contentEquals = false;
          break;
        }
      }
      if (contentEquals) {
        if (scrollToPosition != RecyclerView.NO_POSITION) {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          // TODO smooth?
          manager.scrollToPositionWithOffset(scrollToPosition, 0);
        }
        return;
      }
    }
    clearMessageList();
    collapse(false);
    canExpand.setValue(false, false);
    countAnimator.forceFactor(0);
    List<ListItem> items = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      entry.subscribeToUpdates();
      items.add(itemOf(entry));
    }
    messagesAdapter.setItems(items, false);
    if (scrollToPosition != RecyclerView.NO_POSITION) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      manager.scrollToPositionWithOffset(scrollToPosition, 0);
    }
  }

  public void setMessage (@Nullable Tdlib tdlib, @Nullable TdApi.Message message, @Nullable TdApi.FormattedText quote) {
    if (tdlib != null && message != null) {
      setStaticMessageList(Collections.singletonList(new Entry(tdlib, message, quote)), RecyclerView.NO_POSITION);
    } else {
      setMessageList(null);
    }
  }

  public void setContextChatId (long contextChatId) {
    this.contextChatId = contextChatId;
  }

  private long maxFocusMessageId;

  public void setMaxFocusMessageId (long maxFocusMessageId) {
    if (this.maxFocusMessageId != maxFocusMessageId) {
      this.maxFocusMessageId = maxFocusMessageId;
      // TODO animate focus bar on the left
      // TODO ensure availability of pinned messages
    }
  }

  private static ListItem itemOf (@NonNull Entry entry) {
    return new ListItem(ListItem.TYPE_MESSAGE_PREVIEW, R.id.message).setData(entry);
  }

  private static ListItem itemOf (Tdlib tdlib, TdApi.Message message) {
    return new ListItem(ListItem.TYPE_MESSAGE_PREVIEW, R.id.message).setData(new Entry(tdlib, message, null));
  }

  @Override
  public void performDestroy () {
    setMessageList(null);
    messageViewport.clear();
  }

  public void completeDestroy () {
    messageViewport.performDestroy();
  }

  @Override
  public void onItemsAdded (ListManager<TdApi.Message> list, List<TdApi.Message> items, int startIndex, boolean isInitialChunk) {
    ListItem[] messageList = new ListItem[items.size()];
    int index = 0;
    for (TdApi.Message message : items) {
      messageList[index] = itemOf(list.tdlib(), message);
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
        itemOf(list.tdlib(), item)
      }, false);
    } else {
      boolean scrollBy = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition() == 0;
      messagesAdapter.addItem(toIndex, itemOf(list.tdlib(), item));
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
