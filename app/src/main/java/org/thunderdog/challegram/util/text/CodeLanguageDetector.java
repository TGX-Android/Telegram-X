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
 * File created on 27/10/2026
 */
package org.thunderdog.challegram.util.text;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A wrong language is worse than none, so text that isn't clearly code of one language gets none
 */
public final class CodeLanguageDetector {
  private CodeLanguageDetector () { }

  // Only the start of long code is read, so that detection takes about a millisecond
  static final int MAX_LENGTH = 4096;

  private static final int MIN_SCORE = 6;
  private static final int MIN_LEAD = 5;

  // So that one repeated construction doesn't decide
  private static final int MAX_MATCHES = 3;

  private static final String INDENT = findIndent();

  private static String findIndent () {
    // ^\s* would run through blank lines: whitespace of the line, without the line breaks of ICU,
    // which \h matches as fast as \s, where ICU has it
    try {
      Pattern.compile("\\h");
      return "^\\h*";
    } catch (PatternSyntaxException e) {
      return "^[^\\S\\n\\u000B\\u000C\\r\\u0085\\u2028\\u2029]*";
    }
  }

  private static Pattern compileLines (String regex) {
    return Pattern.compile(regex.replace("^\\s*", INDENT), Pattern.MULTILINE);
  }

  private static final class Feature {
    final Pattern pattern;
    final int weight;

    Feature (String regex, int weight) {
      this.pattern = compileLines(regex);
      this.weight = weight;
    }
  }

  private static final class Language {
    final String name;
    final Feature[] features;

    Language (String name, Feature... features) {
      this.name = name;
      this.features = features;
    }

    int score (String code) {
      int score = 0;
      for (Feature feature : features) {
        score += count(feature.pattern, code, MAX_MATCHES) * feature.weight;
      }
      return score;
    }
  }

  private static final String SEMICOLON = ";[ \\t]*$";

  private static final Language JAVASCRIPT = new Language("javascript",
    // Swift
    new Feature("\\bguard\\s+let\\b|\\bif\\s+let\\b|\\bfunc\\s+\\w+\\s*\\(", -4),
    new Feature("\\bfunction\\s*\\w*\\s*\\(", 2),
    new Feature("\\b(const|let)\\s+\\w+\\s*=", 2),
    new Feature("=>", 1),
    new Feature("\\bconsole\\.(log|error|warn|info)\\(", 3),
    new Feature("\\b(document|window)\\.\\w+", 2),
    new Feature(
      "\\brequire\\(['\"]|\\bmodule\\.exports\\b|^\\s*export\\s+default\\b|" +
        "^\\s*import\\s+.+\\s+from\\s+['\"]",
      3
    ),
    new Feature("[\\w)\\]]\\s*(===|!==)\\s*[\\w'\"(]", 2),
    new Feature("\\bundefined\\b|\\bnull\\b", 1)
  );
  private static final Language TYPESCRIPT = new Language("typescript",
    new Feature(":\\s*(string|number|boolean|any|void|unknown|never)(\\[\\])?\\b", 3),
    new Feature(
      "^\\s*(export\\s+)?(interface\\s+\\w+(<[^>]+>)?\\s*\\{|type\\s+\\w+(<[^>]+>)?\\s*=)",
      4
    ),
    new Feature("\\b(private|public|readonly)\\s+\\w+\\s*:", 3),
    new Feature("[(,]\\s*\\w+\\??\\s*:\\s*[A-Za-z]\\w*(<[^>]*>)?(\\[\\])?\\s*[,)=]", 3)
  );

  private static final Language[] LANGUAGES = {
    new Language("kotlin",
      new Feature("\\bfun\\s+(<[^>]+>\\s*)?[\\w.]+\\s*\\(", 4),
      new Feature("\\b(val|var)\\s+\\w+\\s*(:\\s*[\\w<>?, ]+)?\\s*=", 2),
      new Feature(
        "\\bdata\\s+class\\b|\\bcompanion\\s+object\\b|\\bsuspend\\s+fun\\b|\\blateinit\\b|" +
          "\\bby\\s+lazy\\b|\\binline\\s+fun\\b",
        4
      ),
      new Feature("\\bwhen\\s*(\\(|\\{)|\\?\\.|\\?:|!!", 2),
      new Feature("\\bprintln\\(|\\blistOf\\(|\\bmapOf\\(|\\bit\\.\\w+", 2),
      new Feature(SEMICOLON, -1)
    ),
    new Language("java",
      new Feature(
        "\\b(public|private|protected)\\s+(static\\s+)?(final\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(",
        3
      ),
      new Feature(
        "\\b(public\\s+)?(abstract\\s+|final\\s+)?class\\s+\\w+" +
          "(\\s+(extends|implements)\\s+\\w+)?\\s*\\{",
        2
      ),
      new Feature("\\bSystem\\.(out|err)\\.print", 4),
      new Feature("^\\s*import\\s+(java|javax|android|androidx)\\.[\\w.*]+;", 4),
      new Feature("@Override\\b", 3),
      new Feature("\\bString\\[\\]\\s+\\w+|\\bnew\\s+\\w+(<[^>]*>)?\\s*\\(", 2),
      new Feature(SEMICOLON, 1),
      // C# and Dart
      new Feature(
        "\\b(string|bool)\\s+\\w+|\\bforeach\\s*\\(|^\\s*namespace\\s|^\\s*using\\s+[\\w.]+;|" +
          "=>|@override\\b|\\blate\\s+\\w|\\brequired\\s+this\\.",
        -3
      )
    ),
    new Language("csharp",
      new Feature("\\b(string|bool)\\s+\\w+\\s*[=;,)]|\\bforeach\\s*\\(", 2),
      new Feature("^\\s*using\\s+System(\\.\\w+)*\\s*;", 5),
      new Feature("^\\s*namespace\\s+[\\w.]+", 3),
      new Feature("\\bConsole\\.(WriteLine|Write|ReadLine)\\(", 4),
      new Feature("\\{\\s*get;\\s*(private\\s+)?(set;)?\\s*\\}", 5),
      new Feature(
        "\\b(public|private|internal|protected)\\s+(static\\s+)?(async\\s+)?" +
          "(void|string|int|bool|Task(<[^>]+>)?)\\s+\\w+\\s*\\(",
        2
      ),
      new Feature("\\bvar\\s+\\w+\\s*=\\s*new\\b|\\$\"", 2),
      new Feature(SEMICOLON, 1)
    ),
    new Language("python",
      new Feature("^\\s*def\\s+\\w+\\s*\\([^)]*\\)\\s*(->\\s*[\\w\\[\\], .]+)?:\\s*$", 5),
      new Feature("^\\s*class\\s+\\w+(\\([^)]*\\))?\\s*:\\s*$", 4),
      new Feature(
        "^\\s*from\\s+[\\w.]+\\s+import\\s+|^\\s*import\\s+\\w+(\\s+as\\s+\\w+)?\\s*$",
        3
      ),
      new Feature("^\\s*(if|elif|for|while|with|try|except|else|finally)\\b[^\\n;{]*:\\s*$", 2),
      new Feature("\\bself\\.\\w+|\\belif\\b", 2),
      new Feature("\\b(None|True|False)\\b", 1),
      new Feature("\\bprint\\(|\\bf\"[^\"]*\\{|\\brange\\(|\\blen\\(", 1),
      new Feature("[;{][ \\t]*$", -3)
    ),
    JAVASCRIPT,
    new Language("html",
      new Feature(
        "<!DOCTYPE\\s+html|<html\\b|" +
          "</(html|head|body|div|span|p|a|ul|li|table|script|style|form|button)>",
        5
      ),
      new Feature("<\\w+(\\s+[\\w-]+(=\"[^\"]*\")?)*\\s*/?>", 1),
      new Feature("</\\w+>", 1)
    ),
    new Language("xml",
      new Feature("^\\s*<\\?xml\\b", 8),
      new Feature("\\bxmlns(:\\w+)?=\"", 4),
      new Feature("<\\w+:\\w+|\\b\\w+:\\w+=\"", 2),
      new Feature("<\\w+(\\s+[\\w:-]+=\"[^\"]*\")*\\s*/?>", 1),
      new Feature("</[\\w:]+>", 1),
      new Feature("</(html|head|body|div|span|p|a|ul|li|table|script|style)>", -4)
    ),
    new Language("css",
      // Possessive, so that ICU doesn't try every split of a line of words without a brace
      new Feature("^\\s*[.#]?[\\w-]++(\\s*+[,>+~]?\\s*+[.#:]?[\\w-]++)*+\\s*\\{\\s*$", 2),
      new Feature("^\\s*[\\w-]+\\s*:\\s*[^;{}]+;\\s*$", 2),
      new Feature("@(media|import|keyframes|font-face|supports)\\b", 4),
      new Feature("\\b\\d+(\\.\\d+)?(px|em|rem|vh|vw|pt)\\b|#[0-9a-fA-F]{3,6}\\b", 1),
      new Feature("\\bfunction\\b|=>|\\b(var|let|const)\\s+\\w+\\s*=|\\(\\)\\s*;", -4)
    ),
    new Language("c",
      new Feature("^\\s*#include\\s*<(stdio|stdlib|string|math|unistd|stdint|stdbool)\\.h>", 5),
      new Feature("^\\s*#(include|define|ifdef|ifndef|endif|pragma)\\b", 3),
      new Feature("\\bint\\s+main\\s*\\(", 3),
      new Feature("\\b(printf|fprintf|malloc|free|sizeof|scanf)\\s*\\(", 2),
      new Feature("\\b(struct|typedef|unsigned|const\\s+char)\\b", 2),
      new Feature(SEMICOLON, 1),
      new Feature(
        "std::|\\bclass\\s+\\w+|\\btemplate\\s*<|\\bnamespace\\b|cout\\s*<<|\\bnullptr\\b|" +
          "\\bauto\\s+\\w+\\s*=|^\\s*#include\\s*<\\w+>",
        -4
      )
    ),
    new Language("cpp",
      new Feature(
        "^\\s*use\\s+\\w+::|\\bfn\\s+\\w+|\\blet\\s+(mut\\s+)?\\w+|\\bimpl\\b|\\w+!\\(|" +
          "\\bmatch\\s+\\w+\\s*\\{",
        -4
      ),
      new Feature(
        "\\bnullptr\\b|\\bauto\\s+\\w+\\s*=|^\\s*(public|private|protected):|\\bvirtual\\b|" +
          "^\\s*#include\\s*<\\w+>",
        3
      ),
      new Feature("std::|cout\\s*<<|cin\\s*>>|\\btemplate\\s*<|\\bnamespace\\s+\\w+", 4),
      new Feature("^\\s*#include\\s*<(iostream|vector|string|map|memory|algorithm)>", 5),
      new Feature("^\\s*#(include|define|ifdef|ifndef|endif|pragma)\\b", 2),
      new Feature("\\bclass\\s+\\w+\\s*(:\\s*(public|private)\\s+\\w+)?\\s*\\{|\\w+::\\w+", 2),
      new Feature("\\bint\\s+main\\s*\\(", 2),
      new Feature(SEMICOLON, 1)
    ),
    new Language("go",
      new Feature("^\\s*package\\s+\\w+\\s*$", 3),
      new Feature("\\bfunc\\s+(\\(\\w+\\s+\\*?\\w+\\)\\s*)?\\w+\\s*\\(", 3),
      new Feature(":=", 2),
      new Feature("\\bfmt\\.\\w+\\(|\\berr\\s*!=\\s*nil\\b", 4),
      new Feature("\\bgo\\s+func\\b|\\bchan\\b|\\bdefer\\b|^\\s*import\\s*\\(", 3),
      new Feature(SEMICOLON, -2)
    ),
    new Language("rust",
      new Feature("\\bfn\\s+\\w+\\s*(<[^>]*>)?\\s*\\(", 3),
      new Feature("\\blet\\s+mut\\b|&mut\\b|&self\\b|\\bimpl\\b|\\bpub\\s+(fn|struct|enum)\\b", 4),
      new Feature("^\\s*use\\s+\\w+(::[\\w{}*, ]+)+;", 4),
      new Feature("\\b\\w+!\\(", 2),
      new Feature(
        "\\blet\\s+\\w+(\\s*:\\s*[\\w<>&]+)?\\s*=|\\bmatch\\s+\\w+\\s*\\{|" +
          "\\bSome\\(|\\bOk\\(|\\bErr\\(",
        2
      ),
      new Feature("->\\s*[\\w<>&]+\\s*\\{", 1)
    ),
    new Language("swift",
      new Feature("^\\s*import\\s+(UIKit|SwiftUI|Foundation|Combine)\\s*$", 6),
      new Feature(
        "\\bguard\\s+let\\b|\\bif\\s+let\\b|@(IBOutlet|IBAction|State|Published|Binding|objc)\\b",
        6
      ),
      new Feature("\\bfunc\\s+\\w+\\s*(<[^>]+>)?\\s*\\(", 2),
      new Feature("\\b(let|var)\\s+\\w+\\s*(:\\s*[\\w<>?\\[\\]]+)?\\s*=", 1),
      new Feature("->\\s*[\\w<>?\\[\\]]+\\s*\\{|\\bstruct\\s+\\w+\\s*:\\s*View\\b", 2),
      new Feature(SEMICOLON, -2)
    ),
    new Language("php",
      new Feature("\\$\\w+\\s*=[^=]", 2),
      new Feature("\\$\\w+->\\w+|\\bfunction\\s+\\w+\\s*\\(\\s*\\$", 5),
      new Feature("\\becho\\s+[\"'$]", 1),
      // PowerShell
      new Feature("\\b(Write-Host|Get-\\w+|Set-\\w+|New-Object)\\b|\\s-(eq|ne|gt|lt|and|or)\\s", -6)
    ),
    new Language("ruby",
      new Feature("^\\s*def\\s+\\w+[!?]?(\\([^)]*\\))?\\s*$", 3),
      new Feature("^\\s*end\\s*$", 2),
      new Feature("\\bputs\\s|\\brequire\\s+['\"]|\\battr_(reader|accessor)\\b", 3),
      new Feature("\\bdo\\s*\\|\\w+(,\\s*\\w+)*\\||\\.each\\b|:\\w+\\s*=>", 3),
      new Feature("[{;][ \\t]*$", -2)
    ),
    new Language("bash",
      new Feature("^#!\\s*/(usr/)?bin/(env\\s+)?(ba|z)?sh\\b", 8),
      new Feature(
        "^\\s*(sudo\\s+)?(apt|apt-get|brew|npm|yarn|pip3?|git|docker|cd|mkdir|rm|cp|mv|chmod|" +
          "chown|curl|wget|export|source|ssh|scp|tar|systemctl|adb|\\./[\\w.-]+)\\s+\\S",
        2
      ),
      new Feature("\\bif\\s+\\[\\[?\\s|^\\s*(then|fi|done|esac|elif)\\b|;\\s*(then|do)\\b", 3),
      new Feature("\\$\\{\\w+\\}|\\$\\(\\w|\\$\\w+", 1),
      new Feature("\\|\\s*(grep|sed|awk|xargs|sort|head|tail|wc)\\b|&&", 1)
    ),
    new Language("sql",
      new Feature("^\\s*(def|import|from|class)\\s|\\bself\\.|\\b(cursor|execute|query)\\(", -6),
      new Feature("\\bSELECT\\s+[\\w*.,\\s()]+\\s+FROM\\s+\\w+", 5),
      new Feature(
        "\\b(INSERT\\s+INTO|CREATE\\s+(TABLE|INDEX|VIEW)|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|" +
          "ALTER\\s+TABLE|DROP\\s+TABLE)\\b",
        5
      ),
      new Feature("\\b(WHERE|JOIN|GROUP\\s+BY|ORDER\\s+BY|VALUES|PRIMARY\\s+KEY|NOT\\s+NULL)\\b", 1)
    ),
    new Language("dart",
      new Feature("^\\s*import\\s+'(package|dart):", 6),
      new Feature(
        "\\bWidget\\s+build\\(|\\bsetState\\(|\\bStatelessWidget\\b|\\bStatefulWidget\\b",
        5
      ),
      new Feature("@override\\b", 3),
      new Feature("\\bvoid\\s+main\\s*\\(\\s*\\)|\\bfinal\\s+\\w+\\s*=", 1),
      new Feature(
        "\\blate\\s+\\w|\\brequired\\s+this\\.|\\bFuture<[\\w<>?]+>|\\basync\\s*\\{|" +
          "\\bfinal\\s+\\w+\\??\\s+\\w+\\s*;",
        3
      ),
      new Feature(SEMICOLON, 1)
    ),
    new Language("diff",
      new Feature("^diff --git\\b", 8),
      new Feature("^(---|\\+\\+\\+) [ab]/", 4)
    )
  };

  private static final Pattern LOG_LIKE_LINE = compileLines(
    "^\\s*(\\[?\\d{2,4}[-./]\\d{1,2}[-./]\\d{1,4}|\\[?\\d{1,2}:\\d{2}(:\\d{2})?|" +
      "[VDIWEF]/[\\w.-]+|\\[?(INFO|WARN|WARNING|ERROR|DEBUG|TRACE|FATAL)\\]?\\b|" +
      "at\\s+[\\w$.]+\\([\\w.]*(:\\d+)?\\)|" +
      "Traceback \\(most recent call last\\)|File \"[^\"]+\", line \\d+|[\\w.-]+\\s+\\|\\s)"
  );
  // Only logs have these lines, while LOG_LIKE_LINE also matches tables and lists: lines of logcat,
  // frames of glibc ("app(+0x15a1)[0x5565cfe2d652]"), of tombstones and gdb ("#00 pc 0004b2c8",
  // "#1 0x00007f in main ()"), of lldb ("frame #") and of exceptions of Java
  private static final Pattern LOG_LINE = compileLines(
    "^\\s*(\\[?\\d{2,4}[-./]\\d{1,2}[-./]\\d{1,4}[ T]\\d{1,2}:\\d{2}|\\[?\\d{1,2}:\\d{2}:\\d{2}|" +
      "[VDIWEF]/[\\w.-]+.*:|\\[?(INFO|WARN|WARNING|ERROR|DEBUG|TRACE|FATAL)\\]?\\b|" +
      "at\\s+[\\w$.<>]+\\([\\w.]*(:\\d+)?\\)|Traceback \\(most recent call last\\)|" +
      "File \"[^\"]+\", line \\d+|" +
      "\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s|" +
      "[^\\s(]+\\([^)\\n]*\\)\\s*\\[0x[\\da-fA-F]+\\]|" +
      "#\\d+\\s+(pc\\s+[\\da-fA-F]+|0x[\\da-fA-F]+)\\b|frame #\\d+: 0x|" +
      "Caused by: |Exception in thread )"
  );
  private static final Pattern LIST_LINE =
    compileLines("^\\s*([-*•]|\\d+[.)])\\s+\\S");
  private static final Pattern YAML_KEY = Pattern.compile(
    "^([ \\t]*)(- )?[a-z_][\\w.-]*:([ \\t]+[^\\s{}\\[\\];][^;{}\\n]*)?[ \\t]*$",
    Pattern.MULTILINE
  );
  private static final Pattern YAML_ITEM =
    Pattern.compile("^[ \\t]*- \\S[^;{}\\n]*$", Pattern.MULTILINE);
  private static final Pattern DIFF_HUNK =
    Pattern.compile("^(diff --git |@@ -\\d+(,\\d+)? \\+\\d+(,\\d+)? @@)", Pattern.MULTILINE);

  @Nullable
  public static String detect (String code) {
    final String text = code.length() > MAX_LENGTH ? code.substring(0, MAX_LENGTH) : code;
    final int lineCount = countNonBlankLines(text);
    if (isLog(text, lineCount)) {
      return "log";
    }
    if (!looksLikeCode(text, lineCount)) {
      return null;
    }
    if (isJson(text)) {
      return "json";
    }
    if (DIFF_HUNK.matcher(text).find()) {
      return "diff";
    }
    if (text.contains("<?php")) {
      return "php";
    }
    if (isYaml(text, lineCount)) {
      return "yaml";
    }
    String best = null;
    int bestScore = 0, secondScore = 0;
    for (Language language : LANGUAGES) {
      int score = language.score(text);
      String name = language.name;
      if (language == JAVASCRIPT) {
        final int typescriptScore = TYPESCRIPT.score(text);
        if (typescriptScore >= MIN_LEAD) {
          score += typescriptScore;
          name = TYPESCRIPT.name;
        }
      }
      if (score > bestScore) {
        secondScore = bestScore;
        bestScore = score;
        best = name;
      } else if (score > secondScore) {
        secondScore = score;
      }
    }
    return bestScore >= MIN_SCORE && bestScore - secondScore >= MIN_LEAD ? best : null;
  }

  private static boolean isLog (String text, int lineCount) {
    // Two fifths, e.g. a backtrace between lines of a message and separators of dashes
    final int logLines = count(LOG_LINE, text);
    return logLines >= 2 && logLines * 5 >= lineCount * 2;
  }

  private static boolean looksLikeCode (String text, int lineCount) {
    int nonSpace = 0, symbols = 0, boxDrawing = 0;
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        continue;
      }
      nonSpace++;
      if ("{}()[];=<>:\"'`#$@&|*+/\\".indexOf(c) != -1) {
        symbols++;
      } else if (c >= '─' && c <= '▟') {
        boxDrawing++;
      }
    }
    if (nonSpace < 12 || boxDrawing * 20 > nonSpace || symbols * 40 < nonSpace) {
      return false;
    }
    return count(LOG_LIKE_LINE, text) * 5 < lineCount * 2 &&
      count(LIST_LINE, text) * 2 <= lineCount &&
      countSentences(text) * 2 <= lineCount;
  }

  private static final String[] JSON_LITERALS = {"true", "false", "null"};

  private static boolean isJson (String text) {
    final String trimmed = text.trim();
    final boolean isObject = trimmed.startsWith("{") && trimmed.endsWith("}");
    final boolean isArray = trimmed.startsWith("[") && trimmed.endsWith("]");
    if (!isObject && !isArray) {
      return false;
    }
    final int end = parseJsonValue(trimmed, 0, 0);
    return end != -1 && skipSpaces(trimmed, end) == trimmed.length() && trimmed.indexOf(':') != -1;
  }

  private static int parseJsonValue (String s, int index, int depth) {
    if (index >= s.length() || depth > 64) {
      return -1;
    }
    final char c = s.charAt(index);
    if (c == '{' || c == '[') {
      final char close = c == '{' ? '}' : ']';
      index = skipSpaces(s, index + 1);
      if (index < s.length() && s.charAt(index) == close) {
        return index + 1;
      }
      while (true) {
        if (c == '{') {
          if (index >= s.length() || s.charAt(index) != '"') {
            return -1;
          }
          index = parseJsonString(s, index);
          if (index == -1) {
            return -1;
          }
          index = skipSpaces(s, index);
          if (index >= s.length() || s.charAt(index) != ':') {
            return -1;
          }
          index = skipSpaces(s, index + 1);
        }
        index = parseJsonValue(s, index, depth + 1);
        if (index == -1) {
          return -1;
        }
        index = skipSpaces(s, index);
        if (index >= s.length()) {
          return -1;
        }
        if (s.charAt(index) == close) {
          return index + 1;
        }
        if (s.charAt(index) != ',') {
          return -1;
        }
        index = skipSpaces(s, index + 1);
      }
    }
    if (c == '"') {
      return parseJsonString(s, index);
    }
    for (String literal : JSON_LITERALS) {
      if (s.startsWith(literal, index)) {
        return index + literal.length();
      }
    }
    int end = index;
    while (end < s.length() && "-+.eE0123456789".indexOf(s.charAt(end)) != -1) {
      end++;
    }
    return end > index && Character.isDigit(s.charAt(end - 1)) ? end : -1;
  }

  private static int parseJsonString (String s, int index) {
    for (int i = index + 1; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '"') {
        return i + 1;
      } else if (c == '\n') {
        return -1;
      }
    }
    return -1;
  }

  private static int skipSpaces (String s, int index) {
    while (index < s.length() && Character.isWhitespace(s.charAt(index))) {
      index++;
    }
    return index;
  }

  private static boolean isYaml (String text, int lineCount) {
    final Matcher matcher = YAML_KEY.matcher(text);
    int keys = 0;
    boolean isNested = false;
    while (matcher.find()) {
      keys++;
      isNested = isNested || !matcher.group(1).isEmpty() || matcher.group(2) != null;
    }
    final int items = count(YAML_ITEM, text);
    return keys >= 3 && isNested && (keys + items) * 4 >= lineCount * 3 && text.indexOf(';') == -1;
  }

  private static int countSentences (String text) {
    int count = 0;
    for (String line : text.split("\n")) {
      int words = 0, letters = 0, lowerCase = 0, nonSpace = 0;
      boolean inWord = false, hasSymbols = false;
      for (int i = 0; i < line.length(); i++) {
        final char c = line.charAt(i);
        if (Character.isWhitespace(c)) {
          inWord = false;
          continue;
        }
        if (!inWord) {
          words++;
          inWord = true;
        }
        nonSpace++;
        if (Character.isLetter(c)) {
          letters++;
          if (Character.isLowerCase(c)) {
            lowerCase++;
          }
        } else if ("{}[];=<>$#@|\\".indexOf(c) != -1) {
          hasSymbols = true;
        }
      }
      if (words >= 4 && !hasSymbols && letters * 4 >= nonSpace * 3 && lowerCase * 2 >= letters) {
        count++;
      }
    }
    return count;
  }

  private static int count (Pattern pattern, String text) {
    return count(pattern, text, Integer.MAX_VALUE);
  }

  private static int count (Pattern pattern, String text, int maxCount) {
    final Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (count < maxCount && matcher.find()) {
      count++;
    }
    return count;
  }

  private static int countNonBlankLines (String text) {
    int count = 0;
    for (String line : text.split("\n")) {
      if (!line.trim().isEmpty()) {
        count++;
      }
    }
    return Math.max(count, 1);
  }
}
