package org.thunderdog.challegram.data;

import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextPart;

/**
 * Date: 29/03/2019
 * Author: default
 */
public class TGSourceHidden extends TGSource {
  private final String name;
  private final boolean isImported;

  public TGSourceHidden (TGMessage msg, TdApi.MessageForwardOriginHiddenUser forward) {
    super(msg);
    this.name = forward.senderName;
    this.isImported = false;
  }

  public TGSourceHidden (TGMessage msg, TdApi.MessageForwardOriginMessageImport messageImport) {
    super(msg);
    this.name = messageImport.senderName;
    this.isImported = true;
  }

  @Override
  public boolean open (View view, Text text, TextPart part, TdlibUi.UrlOpenParameters parameters, Receiver receiver) {
    msg.context()
      .tooltipManager()
      .builder(view, msg.currentViews)
      .locate(text != null ? (targetView, outRect) -> text.locatePart(outRect, part) : receiver != null ? (targetView, outRect) -> receiver.toRect(outRect) : null)
      .controller(msg.controller())
      .show(msg.tdlib(), isImported ? R.string.ForwardAuthorImported : R.string.ForwardAuthorHidden)
      .hideDelayed();
    return true;
  }

  @Override
  public void load () { }

  @Override
  public String getAuthorName () {
    return name;
  }

  @Override
  public ImageFile getAvatar () {
    return null;
  }

  @Override
  public void destroy () { }

  @Override
  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return new AvatarPlaceholder.Metadata(TD.getColorIdForName(name), isImported ? null : TD.getLetters(name), isImported ? R.drawable.baseline_phone_24 : 0, 0);
  }
}
