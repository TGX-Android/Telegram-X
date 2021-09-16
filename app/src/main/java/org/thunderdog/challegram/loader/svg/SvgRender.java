package org.thunderdog.challegram.loader.svg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParserFactory;

import me.vkryl.core.StringUtils;

/**
 * A slightly rewritten version of https://github.com/japgolly/svg-android library.
 */
public class SvgRender {
  private static final Pattern TRANSFORM_SEP = Pattern.compile("[\\s,]*");
  private static final RectF arcRectf = new RectF();
  private static final Matrix arcMatrix = new Matrix();
  private static final Matrix arcMatrix2 = new Matrix();

  public static Bitmap fromCompressed (int imageSize, boolean cropSquare, String filePath) {
    return svgAsBitmap(imageSize, cropSquare, U.gzipFileToString(filePath));
  }

  @Nullable
  private static Bitmap svgAsBitmap (int imageSize, boolean cropSquare, String rawData) {
    try {
      SVGHandler handler = new SVGHandler(imageSize, cropSquare);
      XMLReader xr = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
      xr.setContentHandler(handler);
      xr.parse(new InputSource(new StringReader(rawData)));
      return handler.outBitmap;
    } catch (Exception e) {
      Log.e(e);
      return null;
    }
  }

  private static class SVGHandler extends DefaultHandler {
    public Bitmap outBitmap;
    private Canvas canvas;

    private StringBuilder styleSheetInProcess;
    private boolean boundsMode, pushed;

    private final HashMap<String, StyleSet> styleSheet = new HashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int imageSizeLimit;
    private final boolean cropSquare;

    public SVGHandler (int imageSizeLimit, boolean cropSquare) {
      this.imageSizeLimit = imageSizeLimit;
      this.cropSquare = cropSquare;
    }

    private void pushTransform (Attributes attributes) {
      final String transform = getStringAttr("transform", attributes);
      if (pushed = transform != null) {
        canvas.save();
        canvas.concat(parseTransform(transform));
      }
    }

    private void popTransform () {
      if (pushed) {
        canvas.restore();
      }
    }

    public static class SvgException extends IllegalArgumentException { }

    @Override
    public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (boundsMode && !localName.equals("style")) return;

      switch (localName) {
        case "svg": {
          String rawViewBox = getStringAttr("viewBox", attributes);
          if (StringUtils.isEmpty(rawViewBox))
            throw new SvgException();
          String[] viewBox = rawViewBox.split(" ");
          if (viewBox.length != 4)
            throw new SvgException();

          // 0 0 1125 2436
          final float viewBoxWidth = Float.parseFloat(viewBox[2]);
          final float viewBoxHeight = Float.parseFloat(viewBox[3]);

          float scale = Math.min(1f, Math.min((float) imageSizeLimit / viewBoxWidth, (float) imageSizeLimit / viewBoxHeight));

          int outputWidth = (int) (viewBoxWidth * scale);
          int outputHeight = (int) (viewBoxHeight * scale);
          boolean needCrop = cropSquare && outputWidth != outputHeight;
          if (needCrop) {
            int minSize = Math.min(outputWidth, outputHeight);
            outBitmap = Bitmap.createBitmap(minSize, minSize, Bitmap.Config.ARGB_8888);
          } else {
            outBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
          }
          outBitmap.eraseColor(Color.TRANSPARENT);
          canvas = new Canvas(outBitmap);
          if (needCrop) {
            if (outputWidth > outputHeight) {
              canvas.translate(-(outputWidth - outputHeight) / 2f, 0);
            } else {
              canvas.translate(0, -(outputHeight - outputWidth) / 2f);
            }
          }
          canvas.scale(scale, scale, 0, 0);
          break;
        }

        case "defs":
        case "clipPath":
          boundsMode = true;
          break;
        case "style":
          styleSheetInProcess = new StringBuilder();
          break;
        case "g":
          if ("bounds".equalsIgnoreCase(getStringAttr("id", attributes))) {
            boundsMode = true;
          }
          break;
        case "rect": {
          Float x = getFloatAttr("x", attributes);
          if (x == null) {
            x = 0f;
          }
          Float y = getFloatAttr("y", attributes);
          if (y == null) {
            y = 0f;
          }
          Float width = getFloatAttr("width", attributes);
          Float height = getFloatAttr("height", attributes);
          Float rx = getFloatAttr("rx", attributes, null);
          pushTransform(attributes);
          Properties props = new Properties(attributes, styleSheet);
          if (doFill(props)) {
            if (rx != null) {
              rect.set(x, y, x + width, y + height);
              canvas.drawRoundRect(rect, rx, rx, paint);
            } else {
              canvas.drawRect(x, y, x + width, y + height, paint);
            }
          }
          if (doStroke(props)) {
            if (rx != null) {
              rect.set(x, y, x + width, y + height);
              canvas.drawRoundRect(rect, rx, rx, paint);
            } else {
              canvas.drawRect(x, y, x + width, y + height, paint);
            }
          }
          popTransform();
          break;
        }
        case "line": {
          Float x1 = getFloatAttr("x1", attributes);
          Float x2 = getFloatAttr("x2", attributes);
          Float y1 = getFloatAttr("y1", attributes);
          Float y2 = getFloatAttr("y2", attributes);
          Properties props = new Properties(attributes, styleSheet);
          if (doStroke(props)) {
            pushTransform(attributes);
            canvas.drawLine(x1, y1, x2, y2, paint);
            popTransform();
          }
          break;
        }
        case "circle": {
          Float centerX = getFloatAttr("cx", attributes);
          Float centerY = getFloatAttr("cy", attributes);
          Float radius = getFloatAttr("r", attributes);
          if (centerX != null && centerY != null && radius != null) {
            pushTransform(attributes);
            Properties props = new Properties(attributes, styleSheet);
            if (doFill(props)) {
              canvas.drawCircle(centerX, centerY, radius, paint);
            }
            if (doStroke(props)) {
              canvas.drawCircle(centerX, centerY, radius, paint);
            }
            popTransform();
          }
          break;
        }
        case "ellipse": {
          Float centerX = getFloatAttr("cx", attributes);
          Float centerY = getFloatAttr("cy", attributes);
          Float radiusX = getFloatAttr("rx", attributes);
          Float radiusY = getFloatAttr("ry", attributes);
          if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
            pushTransform(attributes);
            Properties props = new Properties(attributes, styleSheet);
            rect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY);
            if (doFill(props)) {
              canvas.drawOval(rect, paint);
            }
            if (doStroke(props)) {
              canvas.drawOval(rect, paint);
            }
            popTransform();
          }
          break;
        }
        case "polygon":
        case "polyline":
          NumberParse numbers = parseNumbers(attributes.getValue("points"));
          if (numbers != null) {
            Path p = new Path();
            ArrayList<Float> points = numbers.numbers;
            if (points.size() > 1) {
              pushTransform(attributes);
              Properties props = new Properties(attributes, styleSheet);
              p.moveTo(points.get(0), points.get(1));
              for (int i = 2; i < points.size(); i += 2) {
                float x = points.get(i);
                float y = points.get(i + 1);
                p.lineTo(x, y);
              }
              if (localName.equals("polygon")) {
                p.close();
              }
              if (doFill(props)) {
                canvas.drawPath(p, paint);
              }
              if (doStroke(props)) {
                canvas.drawPath(p, paint);
              }
              popTransform();
            }
          }
          break;
        case "path": {
          Path p = doPath(getStringAttr("d", attributes));
          pushTransform(attributes);
          Properties props = new Properties(attributes, styleSheet);
          if (doFill(props)) {
            canvas.drawPath(p, paint);
          }
          if (doStroke(props)) {
            canvas.drawPath(p, paint);
          }
          popTransform();
          break;
        }
      }
    }

    @Override
    public void characters (char[] ch, int start, int length) throws SAXException {
      if (styleSheetInProcess != null) {
        styleSheetInProcess.append(ch, start, length);
      }
    }

    @Override
    public void endElement (String uri, String localName, String qName) throws SAXException {
      switch (localName) {
        case "style":
          if (styleSheetInProcess != null) {
            String[] args = styleSheetInProcess.toString().split("\\}");

            for (int a = 0; a < args.length; a++) {
              args[a] = args[a].trim().replace("\t", "").replace("\n", "");
              if (args[a].length() == 0 || args[a].charAt(0) != '.') {
                continue;
              }
              int idx1 = args[a].indexOf('{');
              if (idx1 < 0) {
                continue;
              }
              String name = args[a].substring(1, idx1).trim();
              String style = args[a].substring(idx1 + 1);
              styleSheet.put(name, new StyleSet(style));
            }

            styleSheetInProcess = null;
          }
          break;
        case "g":
        case "defs":
        case "clipPath":
          boundsMode = false;
          break;
      }
    }

    private boolean doFill (Properties attributes) {
      if ("none".equals(attributes.getString("display"))) return false;
      String fillString = attributes.getString("fill");
      if (fillString != null && (fillString.startsWith("url(#") || fillString.equals("none"))) {
        return false;
      } else {
        Integer color = attributes.getHex("fill");
        if (color != null) {
          doColor(attributes, color, true);
          paint.setStyle(Paint.Style.STROKE);
          return true;
        } else if (attributes.getString("fill") == null && attributes.getString("stroke") == null) {
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(0xff000000);
          return true;
        }
      }
      return false;
    }

    private boolean doStroke (Properties attributes) {
      if ("none".equals(attributes.getString("display"))) return false;
      Integer color = attributes.getHex("stroke");
      if (color != null) {
        doColor(attributes, color, false);
        Float width = attributes.getFloat("stroke-width");
        if (width != null) paint.setStrokeWidth(width);
        String linecap = attributes.getString("stroke-linecap");
        if ("round".equals(linecap)) {
          paint.setStrokeCap(Paint.Cap.ROUND);
        } else if ("square".equals(linecap)) {
          paint.setStrokeCap(Paint.Cap.SQUARE);
        } else if ("butt".equals(linecap)) {
          paint.setStrokeCap(Paint.Cap.BUTT);
        }
        String linejoin = attributes.getString("stroke-linejoin");
        if ("miter".equals(linejoin)) {
          paint.setStrokeJoin(Paint.Join.MITER);
        } else if ("round".equals(linejoin)) {
          paint.setStrokeJoin(Paint.Join.ROUND);
        } else if ("bevel".equals(linejoin)) {
          paint.setStrokeJoin(Paint.Join.BEVEL);
        }
        paint.setStyle(Paint.Style.STROKE);
        return true;
      }
      return false;
    }

    private void doColor (Properties attributes, Integer color, boolean fillMode) {
      int c = (0xFFFFFF & color) | 0xFF000000;
      paint.setColor(c);
      Float opacity = attributes.getFloat("opacity");
      if (opacity == null) {
        opacity = attributes.getFloat(fillMode ? "fill-opacity" : "stroke-opacity");
      }
      if (opacity == null) {
        paint.setAlpha(255);
      } else {
        paint.setAlpha((int) (255 * opacity));
      }
    }
  }

  /**
   * Parse a list of transforms such as: foo(n,n,n...) bar(n,n,n..._ ...) Delimiters are whitespaces or commas
   */
  private static Matrix parseTransform (String s) {
    Matrix matrix = new Matrix();
    while (true) {
      parseTransformItem(s, matrix);
      // Log.i(TAG, "Transformed: (" + s + ") " + matrix);
      final int rparen = s.indexOf(")");
      if (rparen > 0 && s.length() > rparen + 1) {
        s = TRANSFORM_SEP.matcher(s.substring(rparen + 1)).replaceFirst("");
      } else {
        break;
      }
    }
    return matrix;
  }

  private static Matrix parseTransformItem (String s, Matrix matrix) {
    if (s.startsWith("matrix(")) {
      NumberParse np = parseNumbers(s.substring("matrix(".length()));
      if (np.numbers.size() == 6) {
        Matrix mat = new Matrix();
        mat.setValues(new float[]{
          // Row 1
          np.numbers.get(0), np.numbers.get(2), np.numbers.get(4),
          // Row 2
          np.numbers.get(1), np.numbers.get(3), np.numbers.get(5),
          // Row 3
          0, 0, 1,});
        matrix.preConcat(mat);
      }
    } else if (s.startsWith("translate(")) {
      NumberParse np = parseNumbers(s.substring("translate(".length()));
      if (np.numbers.size() > 0) {
        float tx = np.numbers.get(0);
        float ty = 0;
        if (np.numbers.size() > 1) {
          ty = np.numbers.get(1);
        }
        matrix.preTranslate(tx, ty);
      }
    } else if (s.startsWith("scale(")) {
      NumberParse np = parseNumbers(s.substring("scale(".length()));
      if (np.numbers.size() > 0) {
        float sx = np.numbers.get(0);
        float sy = sx;
        if (np.numbers.size() > 1) {
          sy = np.numbers.get(1);
        }
        matrix.preScale(sx, sy);
      }
    } else if (s.startsWith("skewX(")) {
      NumberParse np = parseNumbers(s.substring("skewX(".length()));
      if (np.numbers.size() > 0) {
        float angle = np.numbers.get(0);
        matrix.preSkew((float) Math.tan(angle), 0);
      }
    } else if (s.startsWith("skewY(")) {
      NumberParse np = parseNumbers(s.substring("skewY(".length()));
      if (np.numbers.size() > 0) {
        float angle = np.numbers.get(0);
        matrix.preSkew(0, (float) Math.tan(angle));
      }
    } else if (s.startsWith("rotate(")) {
      NumberParse np = parseNumbers(s.substring("rotate(".length()));
      if (np.numbers.size() > 0) {
        float angle = np.numbers.get(0);
        float cx = 0;
        float cy = 0;
        if (np.numbers.size() > 2) {
          cx = np.numbers.get(1);
          cy = np.numbers.get(2);
        }
        matrix.preTranslate(-cx, -cy);
        matrix.preRotate(angle);
        matrix.preTranslate(cx, cy);
      }
    } else {
      Log.w("SVG render: invalid transform (" + s + ")");
    }
    return matrix;
  }

  private static NumberParse parseNumbers (String s) {
    if (s == null) return null;
    int n = s.length();
    int p = 0;
    ArrayList<Float> numbers = new ArrayList<>();
    boolean skipChar = false;
    boolean prevWasE = false;
    for (int i = 1; i < n; i++) {
      if (skipChar) {
        skipChar = false;
        continue;
      }
      char c = s.charAt(i);
      switch (c) {
        // This ends the parsing, as we are on the next element
        case 'M':
        case 'm':
        case 'Z':
        case 'z':
        case 'L':
        case 'l':
        case 'H':
        case 'h':
        case 'V':
        case 'v':
        case 'C':
        case 'c':
        case 'S':
        case 's':
        case 'Q':
        case 'q':
        case 'T':
        case 't':
        case 'a':
        case 'A':
        case ')': {
          String str = s.substring(p, i);
          if (str.trim().length() > 0) {
            // Util.debug("  Last: " + str);
            Float f = Float.parseFloat(str);
            numbers.add(f);
          }
          p = i;
          return new NumberParse(numbers, p);
        }
        case '-':
          // Allow numbers with negative exp such as 7.23e-4
          if (prevWasE) {
            prevWasE = false;
            break;
          }
          // fall-through
        case '\n':
        case '\t':
        case ' ':
        case ',': {
          String str = s.substring(p, i);
          // Just keep moving if multiple whitespace
          if (str.trim().length() > 0) {
            // Util.debug("  Next: " + str);
            Float f = Float.parseFloat(str);
            numbers.add(f);
            if (c == '-') {
              p = i;
            } else {
              p = i + 1;
              skipChar = true;
            }
          } else {
            p++;
          }
          prevWasE = false;
          break;
        }
        case 'e':
          prevWasE = true;
          break;
        default:
          prevWasE = false;
      }
    }

    String last = s.substring(p);
    if (last.length() > 0) {
      // Util.debug("  Last: " + last);
      try {
        numbers.add(Float.parseFloat(last));
      } catch (NumberFormatException nfe) {
        // Just white-space, forget it
      }
      p = s.length();
    }
    return new NumberParse(numbers, p);
  }

  /**
   * This is where the hard-to-parse paths are handled. Uppercase rules are absolute positions, lowercase are
   * relative. Types of path rules:
   * <p/>
   * <ol>
   * <li>M/m - (x y)+ - Move to (without drawing)
   * <li>Z/z - (no params) - Close path (back to starting point)
   * <li>L/l - (x y)+ - Line to
   * <li>H/h - x+ - Horizontal ine to
   * <li>V/v - y+ - Vertical line to
   * <li>C/c - (x1 y1 x2 y2 x y)+ - Cubic bezier to
   * <li>S/s - (x2 y2 x y)+ - Smooth cubic bezier to (shorthand that assumes the x2, y2 from previous C/S is the x1,
   * y1 of this bezier)
   * <li>Q/q - (x1 y1 x y)+ - Quadratic bezier to
   * <li>T/t - (x y)+ - Smooth quadratic bezier to (assumes previous control point is "reflection" of last one w.r.t.
   * to current point)
   * </ol>
   * <p/>
   * Numbers are separate by whitespace, comma or nothing at all (!) if they are self-delimiting, (ie. begin with a -
   * sign)
   *
   * @param s the path string from the XML
   */
  private static Path doPath (String s) {
    int n = s.length();
    ParserHelper ph = new ParserHelper(s, 0);
    ph.skipWhitespace();
    Path p = new Path();
    float lastX = 0;
    float lastY = 0;
    float lastX1 = 0;
    float lastY1 = 0;
    float subPathStartX = 0;
    float subPathStartY = 0;
    char prevCmd = 0;
    while (ph.pos < n) {
      char cmd = s.charAt(ph.pos);
      switch (cmd) {
        case '-':
        case '+':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          if (prevCmd == 'm' || prevCmd == 'M') {
            cmd = (char) (((int) prevCmd) - 1);
            break;
          } else if (prevCmd == 'c' || prevCmd == 'C') {
            cmd = prevCmd;
            break;
          } else if (prevCmd == 'l' || prevCmd == 'L') {
            cmd = prevCmd;
            break;
          } else if (prevCmd == 's' || prevCmd == 'S') {
            cmd = prevCmd;
            break;
          } else if (prevCmd == 'h' || prevCmd == 'H') {
            cmd = prevCmd;
            break;
          } else if (prevCmd == 'v' || prevCmd == 'V') {
            cmd = prevCmd;
            break;
          }
        default: {
          ph.advance();
          prevCmd = cmd;
        }
      }

      boolean wasCurve = false;
      switch (cmd) {
        case 'M':
        case 'm': {
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'm') {
            subPathStartX += x;
            subPathStartY += y;
            p.rMoveTo(x, y);
            lastX += x;
            lastY += y;
          } else {
            subPathStartX = x;
            subPathStartY = y;
            p.moveTo(x, y);
            lastX = x;
            lastY = y;
          }
          break;
        }
        case 'Z':
        case 'z': {
          p.close();
          p.moveTo(subPathStartX, subPathStartY);
          lastX = subPathStartX;
          lastY = subPathStartY;
          lastX1 = subPathStartX;
          lastY1 = subPathStartY;
          wasCurve = true;
          break;
        }
        case 'L':
        case 'l': {
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'l') {
            p.rLineTo(x, y);
            lastX += x;
            lastY += y;
          } else {
            p.lineTo(x, y);
            lastX = x;
            lastY = y;
          }
          break;
        }
        case 'H':
        case 'h': {
          float x = ph.nextFloat();
          if (cmd == 'h') {
            p.rLineTo(x, 0);
            lastX += x;
          } else {
            p.lineTo(x, lastY);
            lastX = x;
          }
          break;
        }
        case 'V':
        case 'v': {
          float y = ph.nextFloat();
          if (cmd == 'v') {
            p.rLineTo(0, y);
            lastY += y;
          } else {
            p.lineTo(lastX, y);
            lastY = y;
          }
          break;
        }
        case 'C':
        case 'c': {
          wasCurve = true;
          float x1 = ph.nextFloat();
          float y1 = ph.nextFloat();
          float x2 = ph.nextFloat();
          float y2 = ph.nextFloat();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 'c') {
            x1 += lastX;
            x2 += lastX;
            x += lastX;
            y1 += lastY;
            y2 += lastY;
            y += lastY;
          }
          p.cubicTo(x1, y1, x2, y2, x, y);
          lastX1 = x2;
          lastY1 = y2;
          lastX = x;
          lastY = y;
          break;
        }
        case 'S':
        case 's': {
          wasCurve = true;
          float x2 = ph.nextFloat();
          float y2 = ph.nextFloat();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          if (cmd == 's') {
            x2 += lastX;
            x += lastX;
            y2 += lastY;
            y += lastY;
          }
          float x1 = 2 * lastX - lastX1;
          float y1 = 2 * lastY - lastY1;
          p.cubicTo(x1, y1, x2, y2, x, y);
          lastX1 = x2;
          lastY1 = y2;
          lastX = x;
          lastY = y;
          break;
        }
        case 'A':
        case 'a': {
          float rx = ph.nextFloat();
          float ry = ph.nextFloat();
          float theta = ph.nextFloat();
          int largeArc = (int) ph.nextFloat();
          int sweepArc = (int) ph.nextFloat();
          float x = ph.nextFloat();
          float y = ph.nextFloat();
          drawArc(p, lastX, lastY, x, y, rx, ry, theta, largeArc, sweepArc);
          lastX = x;
          lastY = y;
          break;
        }
      }
      if (!wasCurve) {
        lastX1 = lastX;
        lastY1 = lastY;
      }
      ph.skipWhitespace();
    }
    return p;
  }

  private static void drawArc (Path p, float lastX, float lastY, float x, float y, float rx, float ry, float theta,
                               int largeArc, int sweepArc) {
    // Log.d("drawArc", "from (" + lastX + "," + lastY + ") to (" + x + ","+ y + ") r=(" + rx + "," + ry +
    // ") theta=" + theta + " flags="+ largeArc + "," + sweepArc);

    // http://www.w3.org/TR/SVG/implnote.html#ArcImplementationNotes

    if (rx == 0 || ry == 0) {
      p.lineTo(x, y);
      return;
    }

    if (x == lastX && y == lastY) {
      return; // nothing to draw
    }

    rx = Math.abs(rx);
    ry = Math.abs(ry);

    final float thrad = theta * (float) Math.PI / 180;
    final float st = (float) Math.sin(thrad);
    final float ct = (float) Math.cos(thrad);

    final float xc = (lastX - x) / 2;
    final float yc = (lastY - y) / 2;
    final float x1t = ct * xc + st * yc;
    final float y1t = -st * xc + ct * yc;

    final float x1ts = x1t * x1t;
    final float y1ts = y1t * y1t;
    float rxs = rx * rx;
    float rys = ry * ry;

    float lambda = (x1ts / rxs + y1ts / rys) * 1.001f; // add 0.1% to be sure that no out of range occurs due to
    // limited precision
    if (lambda > 1) {
      float lambdasr = (float) Math.sqrt(lambda);
      rx *= lambdasr;
      ry *= lambdasr;
      rxs = rx * rx;
      rys = ry * ry;
    }

    final float R =
      (float) (Math.sqrt((rxs * rys - rxs * y1ts - rys * x1ts) / (rxs * y1ts + rys * x1ts))
        * ((largeArc == sweepArc) ? -1 : 1));
    final float cxt = R * rx * y1t / ry;
    final float cyt = -R * ry * x1t / rx;
    final float cx = ct * cxt - st * cyt + (lastX + x) / 2;
    final float cy = st * cxt + ct * cyt + (lastY + y) / 2;

    final float th1 = angle(1, 0, (x1t - cxt) / rx, (y1t - cyt) / ry);
    float dth = angle((x1t - cxt) / rx, (y1t - cyt) / ry, (-x1t - cxt) / rx, (-y1t - cyt) / ry);

    if (sweepArc == 0 && dth > 0) {
      dth -= 360;
    } else if (sweepArc != 0 && dth < 0) {
      dth += 360;
    }

    // draw
    if ((theta % 360) == 0) {
      // no rotate and translate need
      arcRectf.set(cx - rx, cy - ry, cx + rx, cy + ry);
      p.arcTo(arcRectf, th1, dth);
    } else {
      // this is the hard and slow part :-)
      arcRectf.set(-rx, -ry, rx, ry);
      arcMatrix.reset();
      arcMatrix.postRotate(theta);
      arcMatrix.postTranslate(cx, cy);
      arcMatrix.invert(arcMatrix2);
      p.transform(arcMatrix2);
      p.arcTo(arcRectf, th1, dth);
      p.transform(arcMatrix);
    }
  }

  private static float angle (float x1, float y1, float x2, float y2) {
    return (float) Math.toDegrees(Math.atan2(x1, y1) - Math.atan2(x2, y2)) % 360;
  }

  // CSS stuff
  private static class StyleSet {
    HashMap<String, String> styleMap = new HashMap<String, String>();

    private StyleSet (String string) {
      String[] styles = string.split(";");
      for (String s : styles) {
        String[] style = s.split(":");
        if (style.length == 2) {
          styleMap.put(style[0], style[1]);
        }
      }
    }

    public String getStyle (String name) {
      return styleMap.get(name);
    }
  }

  private static class Properties {
    Attributes atts;
    ArrayList<StyleSet> appliedStyles = new ArrayList<>();

    private Properties (Attributes atts, HashMap<String, StyleSet> styleSheet) {
      this.atts = atts;

      String styleAttr = getStringAttr("style", atts);
      if (styleAttr != null) {
        appliedStyles.add(new StyleSet(styleAttr));
      }

      String classes = getStringAttr("class", atts);
      if (classes != null) {
        for (String className : classes.split(" ")) {
          if (styleSheet.containsKey(className)) {
            appliedStyles.add(styleSheet.get(className));
          }
        }
      }
    }

    public String getAttr (String name) {
      String v = null;

      for (int i = 0; i < appliedStyles.size(); i++) {
        v = appliedStyles.get(i).getStyle(name);
        if (v != null) break;
      }

      if (v == null) {
        v = getStringAttr(name, atts);
      }

      return v;
    }

    public String getString (String name) {
      return getAttr(name);
    }

    private Integer rgb (int r, int g, int b) {
      return ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
    }

    private int parseNum (String v) throws NumberFormatException {
      if (v.endsWith("%")) {
        v = v.substring(0, v.length() - 1);
        return Math.round(Float.parseFloat(v) / 100 * 255);
      }
      return Integer.parseInt(v);
    }

    public Integer getColor (String name) {
      if (name == null) {
        return null;
      } else if (name.startsWith("#")) {
        try { // #RRGGBB or #AARRGGBB
          return Color.parseColor(name);
        } catch (IllegalArgumentException iae) {
          return null;
        }
      } else if (name.startsWith("rgb(") && name.endsWith(")")) {
        String[] values = name.substring(4, name.length() - 1).split(",");
        try {
          return rgb(parseNum(values[0]), parseNum(values[1]), parseNum(values[2]));
        } catch (NumberFormatException nfe) {
          return null;
        } catch (ArrayIndexOutOfBoundsException e) {
          return null;
        }
      } else {
        return Color.WHITE;
      }
    }

    public float getFloat (String name, float defaultValue) {
      String v = getAttr(name);
      if (v == null) {
        return defaultValue;
      } else {
        try {
          return Float.parseFloat(v);
        } catch (NumberFormatException nfe) {
          return defaultValue;
        }
      }
    }

    public Float getFloat (String name, Float defaultValue) {
      String v = getAttr(name);
      if (v == null) {
        return defaultValue;
      } else {
        try {
          return Float.parseFloat(v);
        } catch (NumberFormatException nfe) {
          return defaultValue;
        }
      }
    }

    public Float getFloat (String name) {
      return getFloat(name, null);
    }

    public Integer getHex (String name) {
      String v = getAttr(name);
      if (v == null) {
        return null;
      } else {
        try {
          return Integer.parseInt(v.substring(1), 16);
        } catch (NumberFormatException nfe) {
          return 0;
        }
      }
    }
  }

  // Various Attributes stuff
  private static Float getFloatAttr (String name, Attributes attributes) {
    return getFloatAttr(name, attributes, null);
  }

  private static Float getFloatAttr (String name, Attributes attributes, Float defaultValue) {
    return parseFloatValue(getStringAttr(name, attributes), defaultValue);
  }

  private static float getFloatAttr (String name, Attributes attributes, float defaultValue) {
    return parseFloatValue(getStringAttr(name, attributes), defaultValue);
  }

  private static Float parseFloatValue (String str, Float defaultValue) {
    if (str == null) {
      return defaultValue;
    } else if (str.endsWith("px")) {
      str = str.substring(0, str.length() - 2);
    } else if (str.endsWith("%")) {
      str = str.substring(0, str.length() - 1);
      return Float.parseFloat(str) / 100;
    }

    return Float.parseFloat(str);
  }

  private static String getStringAttr (String name, Attributes attributes) {
    int n = attributes.getLength();

    for (int i = 0; i < n; i++) {
      if (attributes.getLocalName(i).equals(name)) {
        return attributes.getValue(i);
      }
    }

    return null;
  }

  private static class NumberParse {
    private ArrayList<Float> numbers;
    private int nextCmd;

    public NumberParse (ArrayList<Float> numbers, int nextCmd) {
      this.numbers = numbers;
      this.nextCmd = nextCmd;
    }

    public int getNextCmd () {
      return nextCmd;
    }

    public float getNumber (int index) {
      return numbers.get(index);
    }

  }
}
