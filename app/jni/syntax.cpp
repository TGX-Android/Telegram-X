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
 *
 * File created on 26/10/2026
 */

#include <jni.h>
#include <jni_utils.h>
#include <SyntaxHighlighter.h>
#include <iterator>
#include <string>
#include <vector>

#include "bridge.h"

// Grammars of libprisma, included into the library as they are
__asm__(
  "  .pushsection .rodata\n"
  "  .global prisma_grammars\n"
  "  .hidden prisma_grammars\n"
  "  .global prisma_grammars_end\n"
  "  .hidden prisma_grammars_end\n"
  "prisma_grammars:\n"
  "  .incbin \"" PRISMA_GRAMMARS "\"\n"
  "prisma_grammars_end:\n"
  "  .popsection\n"
);
extern "C" const char prisma_grammars[];
extern "C" const char prisma_grammars_end[];

// Same as in CodeHighlighter.java
#define STYLE_COLOR_MASK 0xff
#define STYLE_BOLD (1 << 8)
#define STYLE_ITALIC (1 << 9)

// A color is the index in this list + 1, as in CodeHighlighter.java
static const char *const COLORED_TYPES[] = {
  "comment",
  "block-comment",
  "prolog",
  "doctype",
  "cdata",
  "punctuation",
  "property",
  "tag",
  "boolean",
  "number",
  "constant",
  "symbol",
  "deleted",
  "selector",
  "attr-name",
  "string",
  "char",
  "builtin",
  "inserted",
  "operator",
  "entity",
  "url",
  "atrule",
  "attr-value",
  "keyword",
  "function",
  "class-name",
  "annotation"
};

static int getColor (const std::string &type) {
  for (size_t i = 0; i < std::size(COLORED_TYPES); i++) {
    if (type == COLORED_TYPES[i]) {
      return (int) i + 1;
    }
  }
  return 0;
}

static jint getStyle (const Syntax &token, jint parentStyle) {
  jint style = parentStyle;
  int color = getColor(token.type());
  if (color == 0) {
    color = getColor(token.alias());
  }
  if (color != 0) {
    style = (style & ~STYLE_COLOR_MASK) | color;
  }
  if (token.type() == "bold" || token.alias() == "bold") {
    style |= STYLE_BOLD;
  }
  if (token.type() == "italic" || token.alias() == "italic") {
    style |= STYLE_ITALIC;
  }
  return style;
}

static jint getUtf16Length (std::string_view text) {
  jint length = 0;
  for (unsigned char c : text) {
    if ((c & 0xc0) != 0x80) {
      length += c >= 0xf0 ? 2 : 1;
    }
  }
  return length;
}

static void addRanges (
  const TokenList &tokens,
  jint parentStyle,
  jint &offset,
  std::vector<jint> &ranges
) {
  for (const auto &node : tokens) {
    if (node.isSyntax()) {
      const auto &token = static_cast<const Syntax &>(node);
      addRanges(token.children(), getStyle(token, parentStyle), offset, ranges);
    } else {
      const auto &text = static_cast<const Text &>(node);
      jint start = offset;
      offset += getUtf16Length(text.value());
      if (parentStyle == 0 || start == offset) {
        continue;
      }
      size_t count = ranges.size();
      if (count != 0 && ranges[count - 2] == start && ranges[count - 1] == parentStyle) {
        ranges[count - 2] = offset;
      } else {
        ranges.push_back(start);
        ranges.push_back(offset);
        ranges.push_back(parentStyle);
      }
    }
  }
}

static const SyntaxHighlighter &getHighlighter () {
  // Grammars are parsed once, by the first thread that needs them
  static const SyntaxHighlighter highlighter {
    std::string(prisma_grammars, prisma_grammars_end - prisma_grammars)
  };
  return highlighter;
}

static std::string languageOf (JNIEnv *env, jstring jlanguage) {
  // Names of languages in grammars are in lower case, and entities may have any case
  std::string language = jni::from_jstring(env, jlanguage);
  for (char &c : language) {
    if (c >= 'A' && c <= 'Z') {
      c = (char) (c - 'A' + 'a');
    }
  }
  return language;
}

JNI_FUNC(jstring, getLanguageName, jstring jlanguage) {
  const std::string language = languageOf(env, jlanguage);
  if (language.empty()) {
    return nullptr;
  }
  const std::string name = getHighlighter().languageName(language);
  return name == language ? nullptr : jni::to_jstring(env, name);
}

JNI_FUNC(jintArray, highlightCode, jstring jcode, jstring jlanguage) {
  const std::string code = jni::from_jstring(env, jcode);
  const std::string language = languageOf(env, jlanguage);
  if (code.empty() || language.empty()) {
    return nullptr;
  }

  std::vector<jint> ranges;
  jint offset = 0;
  addRanges(getHighlighter().tokenize(code, language), 0, offset, ranges);
  if (ranges.empty()) {
    return nullptr;
  }

  jintArray result = env->NewIntArray((jsize) ranges.size());
  if (result != nullptr) {
    env->SetIntArrayRegion(result, 0, (jsize) ranges.size(), ranges.data());
  }
  return result;
}
