package org.thunderdog.challegram.util.text;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;

import java.util.ArrayList;

public class FormattedText {
  public final String text;
  public final TextEntity[] entities;

  public FormattedText (String text, TextEntity[] entities) {
    this.text = text;
    this.entities = entities;
  }

  public int getIconCount () {
    if (entities != null) {
      int iconCount = 0;
      for (TextEntity entity : entities) {
        if (entity.isIcon()) {
          iconCount++;
        }
      }
      return iconCount;
    }
    return 0;
  }

  public static int requestIcons (TextEntity[] entities, ComplexReceiver receiver, int keyOffset) {
    boolean clear = keyOffset == -1;
    if (clear) {
      keyOffset = 0;
    }
    int iconIndex = 0;
    if (entities != null) {
      for (TextEntity entity : entities) {
        if (entity.isIcon()) {
          entity.getIcon().requestFiles(keyOffset + iconIndex, receiver);
          iconIndex++;
        }
      }
    }
    if (clear) {
      if (iconIndex > 0) {
        receiver.clearReceiversWithHigherKey(keyOffset + iconIndex);
      } else {
        receiver.clear();
      }
    }
    return iconIndex;
  }
  public static FormattedText parseRichText (ViewController<?> context, @Nullable TdApi.RichText richText, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    if (richText == null)
      return null;
    StringBuilder out = new StringBuilder();
    ArrayList<TextEntityCustom> entities = new ArrayList<>();
    parseRichText(context, richText, out, entities, new int[1], 0, 0, null, TextEntityCustom.LINK_TYPE_NONE, null, false, null, null, openParameters);

    TextEntityCustom[] parsed;
    if (entities.isEmpty()) {
      parsed = null;
    } else {
      parsed = new TextEntityCustom[entities.size()];
      entities.toArray(parsed);

      int offset = 0;

      for (TextEntityCustom custom : parsed) {
        final int entityOffset = custom.getStart();
        final int entityEnd = custom.getEnd();

        if (entityOffset < offset || entityEnd < entityOffset) {
          throw new RuntimeException("Bug in parser");
        }

        offset = entityEnd;
      }
    }

    return new FormattedText(out.toString(), parsed);
  }
  private static void parseRichText (ViewController<?> context, TdApi.RichText in, StringBuilder out, ArrayList<TextEntityCustom> entities, int[] offset, int flags, int linkOffset, int[] linkLength, int linkType, String link, boolean linkCached, @Nullable String referenceAnchorName, String copyLink, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    switch (in.getConstructor()) {
      case TdApi.RichTextPlain.CONSTRUCTOR: {
        final String text = ((TdApi.RichTextPlain) in).text;
        out.append(text);
        if (flags != 0) {
          TextEntityCustom custom = new TextEntityCustom(context, context.tdlib(), text, offset[0], offset[0] + text.length(), flags, linkCached ? new TdlibUi.UrlOpenParameters(openParameters).forceInstantView() : openParameters)
            .setReferenceAnchorName(referenceAnchorName).setCopyLink(copyLink);
          if (linkType != TextEntityCustom.LINK_TYPE_NONE) {
            custom.setLink(linkOffset, linkLength, linkType, link, linkCached);
            linkLength[0] += text.length();
          }
          entities.add(custom);
        }
        offset[0] += text.length();
        break;
      }
      case TdApi.RichTextIcon.CONSTRUCTOR: {
        TdApi.RichTextIcon icon = (TdApi.RichTextIcon) in;
        TextEntityCustom custom = new TextEntityCustom(context, context.tdlib(), "", offset[0], offset[0], flags, linkCached ? new TdlibUi.UrlOpenParameters(openParameters).forceInstantView() : openParameters)
          .setReferenceAnchorName(referenceAnchorName).setCopyLink(copyLink)
          .setIcon(icon);
        if (linkType != TextEntityCustom.LINK_TYPE_NONE) {
          custom.setLink(linkOffset, linkLength, linkType, link, linkCached);
        }
        entities.add(custom);
        break;
      }
      case TdApi.RichTextAnchor.CONSTRUCTOR: {
        TdApi.RichTextAnchor anchor = (TdApi.RichTextAnchor) in;
        TextEntityCustom custom = new TextEntityCustom(context, context.tdlib(), "", offset[0], offset[0], TextEntityCustom.FLAG_ANCHOR, null).setAnchorName(anchor.name);
        entities.add(custom);
        break;
      }
      case TdApi.RichTextBold.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextBold) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_BOLD, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextItalic.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextItalic) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_ITALIC, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextUnderline.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextUnderline) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_UNDERLINE, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextFixed.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextFixed) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_MONOSPACE, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextStrikethrough.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextStrikethrough) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_STRIKETHROUGH, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextSubscript.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextSubscript) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_SUBSCRIPT, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextSuperscript.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextSuperscript) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_SUPERSCRIPT, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextMarked.CONSTRUCTOR: {
        parseRichText(context, ((TdApi.RichTextMarked) in).text, out, entities, offset, flags | TextEntityCustom.FLAG_MARKED, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        break;
      }
      case TdApi.RichTextPhoneNumber.CONSTRUCTOR: {
        TdApi.RichTextPhoneNumber phoneNumber = (TdApi.RichTextPhoneNumber) in;
        parseRichText(context, phoneNumber.text, out, entities, offset, flags | TextEntityCustom.FLAG_CLICKABLE, linkOffset, new int[1], TextEntityCustom.LINK_TYPE_PHONE_NUMBER, phoneNumber.phoneNumber, linkCached, referenceAnchorName, null, openParameters);
        break;
      }
      case TdApi.RichTextEmailAddress.CONSTRUCTOR: {
        TdApi.RichTextEmailAddress email = (TdApi.RichTextEmailAddress) in;
        parseRichText(context, email.text, out, entities, offset, flags | TextEntityCustom.FLAG_CLICKABLE, linkOffset, new int[1], TextEntityCustom.LINK_TYPE_EMAIL, email.emailAddress, linkCached, referenceAnchorName, null, openParameters);
        break;
      }
      case TdApi.RichTextUrl.CONSTRUCTOR: {
        TdApi.RichTextUrl url = (TdApi.RichTextUrl) in;
        parseRichText(context, url.text, out, entities, offset, flags | TextEntityCustom.FLAG_CLICKABLE, linkOffset, new int[1], TextEntityCustom.LINK_TYPE_URL, url.url, url.isCached, referenceAnchorName, null, openParameters);
        break;
      }
      case TdApi.RichTextAnchorLink.CONSTRUCTOR: {
        TdApi.RichTextAnchorLink anchorLink = (TdApi.RichTextAnchorLink) in;
        parseRichText(context, anchorLink.text, out, entities, offset, flags | TextEntityCustom.FLAG_CLICKABLE, linkOffset, new int[1], TextEntityCustom.LINK_TYPE_ANCHOR, anchorLink.anchorName, false, referenceAnchorName, anchorLink.url, openParameters);
        break;
      }
      case TdApi.RichTextReference.CONSTRUCTOR: {
        TdApi.RichTextReference reference = (TdApi.RichTextReference) in;
        parseRichText(context, reference.text, out, entities, offset, flags | TextEntityCustom.FLAG_CLICKABLE, linkOffset, new int[1], TextEntityCustom.LINK_TYPE_REFERENCE, null, false, reference.anchorName, reference.url, openParameters);
        break;
      }
      case TdApi.RichTexts.CONSTRUCTOR: {
        TdApi.RichTexts texts = (TdApi.RichTexts) in;
        for (TdApi.RichText concatenatedText : texts.texts) {
          parseRichText(context, concatenatedText, out, entities, offset, flags, linkOffset, linkLength, linkType, link, linkCached, referenceAnchorName, copyLink, openParameters);
        }
        break;
      }
    }
  }
}
