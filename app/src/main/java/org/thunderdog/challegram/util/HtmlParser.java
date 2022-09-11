/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 11/09/2022, 00:52.
 */

package org.thunderdog.challegram.util;

import android.os.Build;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.util.ArrayDeque;

public class HtmlParser {
  public interface TagHandler {
    boolean handleTag (boolean opening, String tag,
                    Editable output, XMLReader xmlReader,
                    Attributes attributes);
  }

  private static final String INTERNAL_ROOT_TAG_NAME = "tg-unsupported";

  public static CharSequence fromHtml (String htmlText, @Nullable Html.ImageGetter imageGetter, @Nullable TagHandler handler) {
    Html.TagHandler tagHandler;
    if (handler != null) {
      tagHandler = new HtmlTagHandler(INTERNAL_ROOT_TAG_NAME, handler);
      // Wrap with unknown tag to force handleTag to be called
      htmlText = "<" + INTERNAL_ROOT_TAG_NAME + ">" + htmlText + "</" + INTERNAL_ROOT_TAG_NAME + ">";
    } else {
      tagHandler = null;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return Html.fromHtml(
        htmlText,
        Html.FROM_HTML_MODE_COMPACT,
        imageGetter,
        tagHandler
      );
    } else {
      //noinspection deprecation
      return Html.fromHtml(
        htmlText,
        imageGetter,
        tagHandler
      );
    }
  }

  private static class HtmlTagHandler implements Html.TagHandler {
    private final String rootTagName;
    @NonNull
    private final TagHandler tagHandler;

    public HtmlTagHandler (String rootTagName, @NonNull TagHandler tagHandler) {
      this.rootTagName = rootTagName;
      this.tagHandler = tagHandler;
    }

    private ContentHandlerWrapper wrapped;

    @Override
    public void handleTag (boolean opening, String tag, Editable output, XMLReader xmlReader) {
      if (wrapped == null) {
        if (!tag.equalsIgnoreCase(rootTagName))
          throw new IllegalArgumentException(tag);
        ArrayDeque<Boolean> tagStatus = new ArrayDeque<>();
        wrapped = new ContentHandlerWrapper(xmlReader.getContentHandler()) {
          @Override
          public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {
            boolean isHandled = tagHandler.handleTag(true, localName, output, xmlReader, attributes);
            tagStatus.addLast(isHandled);
            if (!isHandled) {
              super.startElement(uri, localName, qName, attributes);
            }
          }

          @Override
          public void endElement (String uri, String localName, String qName) throws SAXException {
            if (!tagStatus.removeLast()) {
              super.endElement(uri, localName, qName);
            }
            if (!tagStatus.isEmpty()) { // empty when processing rootTagName
              tagHandler.handleTag(false, localName, output, xmlReader, null);
            } else if (!localName.equals(rootTagName)) {
              throw new IllegalArgumentException(localName);
            }
          }
        };
        xmlReader.setContentHandler(wrapped);
        tagStatus.addLast(Boolean.FALSE);
      }
    }
  }

  private static class ContentHandlerWrapper implements ContentHandler {
    private final ContentHandler wrapped;

    public ContentHandlerWrapper (ContentHandler wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void setDocumentLocator (Locator locator) {
      wrapped.setDocumentLocator(locator);
    }

    @Override
    public void startDocument () throws SAXException {
      wrapped.startDocument();
    }

    @Override
    public void endDocument () throws SAXException {
      wrapped.endDocument();
    }

    @Override
    public void startPrefixMapping (String prefix, String uri) throws SAXException {
      wrapped.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping (String prefix) throws SAXException {
      wrapped.endPrefixMapping(prefix);
    }

    @Override
    public void startElement (String uri, String localName, String qName, Attributes atts) throws SAXException {
      wrapped.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement (String uri, String localName, String qName) throws SAXException {
      wrapped.endElement(uri, localName, qName);
    }

    @Override
    public void characters (char[] ch, int start, int length) throws SAXException {
      wrapped.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace (char[] ch, int start, int length) throws SAXException {
      wrapped.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction (String target, String data) throws SAXException {
      wrapped.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity (String name) throws SAXException {
      wrapped.skippedEntity(name);
    }
  }

  public interface Replacer<T> {
    void onReplaceSpanMark (Spannable text, int start, int end, T mark);
  }

  public static <T> void end (Editable text, Class<T> markKind, Replacer<T> replacer) {
    T mark = getLast(text, markKind);
    if (mark != null) {
      int where = text.getSpanStart(mark);
      text.removeSpan(mark);
      int len = text.length();
      if (where != len) {
        replacer.onReplaceSpanMark(text, where, len, mark);
      }
    }
  }

  // Copy of:
  // https://android.googlesource.com/platform/frameworks/base/+/f63f20af/core/java/android/text/Html.java#1074

  public static <T> void start (Editable text, T mark) {
    int len = text.length();
    text.setSpan(mark, len, len, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
  }

  private static <T> T getLast (Spanned text, Class<T> kind) {
    T[] objs = text.getSpans(0, text.length(), kind);
    if (objs == null || objs.length == 0) {
      return null;
    } else {
      return objs[objs.length - 1];
    }
  }
}
