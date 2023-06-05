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
 * File created on 13/01/2018
 */
package org.thunderdog.challegram.unsorted;

import android.os.SystemClock;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.StringList;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.util.Blob;
import me.vkryl.leveldb.LevelDB;
import me.vkryl.td.Td;

public class Test {
  private static void testFormat (String format, Object... args) {
    assertEquals(Lang.formatString(format, null, args).toString(), String.format(Lang.locale(), format, args));
  }

  public static void executeBeforeAppInit () {

  }

  public static void executeAfterAppInit () {
    // Settings.instance().removeByPrefix("settings_emoji_installed", null);
    // Log.i("%s", Lang.getString(R.string.Groups));
    /*if (Config.NEED_TESTS) {
      try {
        testLevelDB();
      } catch (AssertionError | RuntimeException e) {
        throw e;
      } catch (Throwable ignored) { }

      // Log.i("decimal: %s %s %s", Lang.formatDecimal(12.03), Lang.formatDecimal(1024.5134), Lang.formatDecimal(10241024.5134));
      // testFormat("%4$d%% %3$02d %2$s %1$s", "World", "Hello", 0, 100);
      // testFormat("%1$d:%2$02d", 1, 1);
      // assertEquals("HELLO \\\"WORLD\\\"".replaceAll("\\\\\"", "\""), "HELLO \"WORLD\"");
      // assertEquals(Lang.patternWithoutYear("d MMMM، y"), "d MMMM");
    }*/
  }

  public static void execute () {
    // Log.i("OpusVersion: %s FfmpegVersion: %s", OpusLibrary.getVersion(), FfmpegLibrary.getVersion());
  }

  // public static boolean HIDE_PHONE_NUMBER = false;

  public static final boolean NEED_CLICK = false;
  public static final String CLICK_NAME = "Run test";

  public static TdApi.ChatAction testAction;

  public static void onClick (final BaseActivity context) {
    ViewController<?> c = context.navigation().getCurrentStackItem();
    if (c == null) {
      return;
    }
    String info = "When selected, tapping a chat will generate a fake event.\n\nTo open a chat from the chat list use Chat Preview.";
    IntList ids = new IntList(5);
    StringList strings = new StringList(5);

    final TdApi.ChatAction[] actions = new TdApi.ChatAction[] {
      null,
      new TdApi.ChatActionCancel(),
      new TdApi.ChatActionTyping(),
      new TdApi.ChatActionRecordingVoiceNote(),
      new TdApi.ChatActionRecordingVideoNote(),
      new TdApi.ChatActionStartPlayingGame(),
      new TdApi.ChatActionUploadingDocument(0),
      new TdApi.ChatActionUploadingDocument(35),
      new TdApi.ChatActionUploadingDocument(100),
    };
    ids.append(1); strings.append("Disable");
    ids.append(2); strings.append("Typing...");
    ids.append(3); strings.append("Recording voice...");
    ids.append(4); strings.append("Recording video...");
    ids.append(5); strings.append("Playing game... (there's no \"selecting sticker\" action now)");
    ids.append(6); strings.append("Sending file... (0% or unknown)");
    ids.append(7); strings.append("Sending file... (35%)");
    ids.append(8); strings.append("Sending file... (100%)");
    c.showOptions(info, ids.get(), strings.get(), null, null, new OptionDelegate() {
      @Override
      public boolean onOptionItemPressed (View optionItemView, int id) {
        TdApi.ChatAction action = actions[id];
        testAction = action.getConstructor() == TdApi.ChatActionCancel.CONSTRUCTOR ? null : action;
        context.currentTdlib().__sendFakeAction(action);
        return true;
      }
    });
  }

  public static boolean onChatClick (Tdlib tdlib, TdApi.Chat chat) {
    if (BuildConfig.DEBUG) {
      /*if (true) {
        if (!tdlib.isChannelChat(chat) || !TD.isAdmin(tdlib.chatStatus(chat.id)))
          return false;
        CreateChannelLinkController c = new CreateChannelLinkController(UI.getUiContext(), tdlib);
        c.setArguments(new CreateChannelLinkController.Args(chat, null));
        UI.getNavigation().navigateTo(c);
        return true;
      }*/
      // Testing typings
      long userId = tdlib.chatUserId(chat);
      if (userId == 0) {
        userId = Td.getSenderUserId(chat.lastMessage);
      }
      if (chat.lastMessage != null) {
        tdlib.sendFakeUpdate(new TdApi.UpdateChatAction(chat.id, 0, chat.lastMessage.senderId, tdlib.status().hasStatus(chat.id, 0) ? new TdApi.ChatActionCancel() : testAction != null ? testAction : new TdApi.ChatActionTyping()), false);
      }
      return true;
    }
    return false;
  }

  // Tests

  public static void assertEquals (int a, int b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (byte a, byte b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (boolean a, boolean b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (long a, long b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (double a, double b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (float a, float b) { if (a != b) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (String a, String b) { if (!StringUtils.equalsOrBothEmpty(a, b)) throw new AssertionError(a + " vs " + b); }
  public static void assertEquals (int[] a, int[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }
  public static void assertEquals (byte[] a, byte[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }
  public static void assertEquals (byte[] x, byte[] y, int z) { assertEquals(y.length, z); for (int i = 0; i < z; i++) assertEquals(x[i], y[i]); }
  public static void assertEquals (long[] a, long[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }
  public static void assertEquals (double[] a, double[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }
  public static void assertEquals (float[] a, float[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }
  public static void assertEquals (String[] a, String[] b) { if (!Arrays.equals(a, b)) throw new AssertionError(); }

  public static void testIntList () {
    IntList list = new IntList(10);

    for (int i = 0; i < 10; i++) {
      list.append(i);
    }

    IntList other = new IntList(0);
    for (int i = 10; i < 20; i++) {
      other.append(i);
    }

    list.appendAll(other);

    assertEquals(list.size(), 20);
    for (int i = 0; i < list.size(); i++) {
      assertEquals(list.get(i), i);
    }
  }

  public static void testBlob () {
    Random random;
    Blob input;
    Blob output;

    random = new Random();
    input = new Blob();
    output = new Blob();

    byte x;
    byte[] xs;
    int y;
    int[] ys;
    long z;
    long[] zs;
    double d;
    int[] vs;

    xs = new byte[1];
    random.nextBytes(xs);

    x = xs[0];
    y = Math.max(10, random.nextInt());
    z = Math.max(10, random.nextLong());
    d = Math.max(10, random.nextDouble());

    xs = new byte[(int) Math.max(Math.random() * 10d, 2d)];
    random.nextBytes(xs);

    ys = new int[(int) Math.max(Math.random() * 10d, 1d)];
    vs = new int[]{0xfd, 0x7f, 0x7d, 100, 265, 10, 2048};

    for (int i = 0; i < ys.length; i++) {
      ys[i] = random.nextInt();
    }

    zs = new long[(int) Math.max(Math.random() * 10d, 1d)];

    for (int i = 0; i < zs.length; i++) {
      zs[i] = random.nextLong();
    }

    input.writeByte(x);
    input.writeInt(y);
    input.writeLong(z);
    input.writeDouble(d);
    input.writeRaw(xs);
    input.writeRaw(xs, xs.length - 1);
    input.writeByteArray(xs);
    input.writeIntArray(ys);
    input.writeLongArray(zs);

    for (int v : vs) {
      Log.i("writing varint %d, size: %d", v, Blob.sizeOf(v));
      input.writeVarint(v);
    }
    for (long v : zs) {
      Log.i("writing varlong %d, size: %d", v, Blob.sizeOf(v));
      input.writeVarLong(v);
    }

    input.writeRaw(xs);

    output.reset(input);

    assertEquals(x, output.readByte());
    assertEquals(y, output.readInt());
    assertEquals(z, output.readLong());
    assertEquals(d, output.readDouble());

    assertEquals(xs, output.readRaw(xs.length));
    assertEquals(xs, output.readRaw(xs.length - 1), xs.length - 1);
    assertEquals(xs, output.readByteArray());
    assertEquals(ys, output.readIntArray());
    assertEquals(zs, output.readLongArray());
    for (int v : vs) {
      assertEquals(v, output.readVarint());
    }
    for (long v : zs) {
      assertEquals(v, output.readVarLong());
    }
    assertEquals(xs, output.readRaw());
  }

  public static File getTestDBDir () {
    File pmcDir = new File(UI.getAppContext().getFilesDir(), "pmc");
    if (!FileUtils.createDirectory(pmcDir)) {
      throw new IllegalStateException("Unable to create working directory");
    }
    return new File(pmcDir, "test");
  }

  public static void testLevelDB () throws FileNotFoundException {
    Log.load(null);

    File testDb = getTestDBDir();
    LevelDB db = new LevelDB(testDb.getPath(), true, null);
    try {
      db.clear();

      Random r = new Random();

      byte[] randomByteArray = new byte[1024];
      r.nextBytes(randomByteArray);
      byte randomByte = randomByteArray[0];
      boolean randomBoolean = r.nextBoolean();
      int randomInt = r.nextInt();
      long randomLong = r.nextLong();
      float randomFloat = r.nextFloat();
      double randomDouble = r.nextDouble();
      String randomString = UUID.randomUUID().toString();
      int[] randomIntArray = new int[randomByteArray.length / 4];
      for (int i = 0, j = 0; i < randomByteArray.length; i += 4, j++) {
        randomIntArray[j] = Blob.readInt(randomByteArray, i);
      }
      float[] randomFloatArray = new float[randomByteArray.length / 4];
      for (int i = 0, j = 0; i < randomByteArray.length; i += 4, j++) {
        randomFloatArray[j] = Blob.readFloat(randomByteArray, i);
      }
      long[] randomLongArray = new long[randomByteArray.length / 8];
      for (int i = 0, j = 0; i < randomByteArray.length; i += 8, j++) {
        randomLongArray[j] = Blob.readLong(randomByteArray, i);
      }
      double[] randomDoubleArray = new double[randomByteArray.length / 8];
      for (int i = 0, j = 0; i < randomByteArray.length; i += 8, j++) {
        randomDoubleArray[j] = Blob.readDouble(randomByteArray, i);
      }
      String[] randomStringArray = new String[32];
      for (int i = 0; i < randomStringArray.length; i++) {
        randomStringArray[i] = UUID.randomUUID().toString();
      }

      // empty read

      int testCount = 100;
      int exceptionCount = 0;
      for (int i = 0; i < testCount; i++) {
        String suffix = "asdfkjhaskjfdh_" + System.currentTimeMillis();
        try {
          db.tryGetInt(suffix);
        } catch (FileNotFoundException e) {
          exceptionCount++;
        }
      }
      assertEquals(testCount, exceptionCount);

      // write

      db.putString("string", "");
      db.putIntArray("intArray", new int[0]);
      db.putByteArray("byteArray", new byte[0]);
      db.putLongArray("longArray", new long[0]);
      db.putFloatArray("floatArray", new float[0]);
      db.putDoubleArray("doubleArray", new double[0]);
      db.putStringArray("stringArray", new String[0]);
      db.putStringArray("stringArray2", new String[] {""});

      long ms = SystemClock.uptimeMillis();
      String suffix = null;
      for (int j = 0; j < 2; j++) {
        boolean batch = j == 1;
        if (batch) {
          db.edit();
        }

        for (int i = 0; i < 10; i++) {
          suffix = "_" + System.currentTimeMillis();

          db.putVoid("void" + suffix);
          db.putInt("int" + suffix, randomInt);
          db.putLong("long" + suffix, randomLong);
          db.putBoolean("boolean" + suffix, randomBoolean);
          db.putByte("byte" + suffix, randomByte);
          db.putFloat("float" + suffix, randomFloat);
          db.putDouble("double" + suffix, randomDouble);
          db.putString("string" + suffix, randomString);

          db.putIntArray("intArray" + suffix, randomIntArray);
          db.putByteArray("byteArray" + suffix, randomByteArray);
          db.putLongArray("longArray" + suffix, randomLongArray);
          db.putFloatArray("floatArray" + suffix, randomFloatArray);
          db.putDoubleArray("doubleArray" + suffix, randomDoubleArray);
          db.putStringArray("stringArray" + suffix, randomStringArray);
        }
        if (batch) {
          db.apply();
        }
        long elapsedMs = SystemClock.uptimeMillis() - ms;
        Log.i("Done db test in %dms", elapsedMs);
        ms = SystemClock.uptimeMillis();
      }

      db.edit();
      final int testNum = 10;
      final String prefixInt = "prefixInt" + suffix;
      final String prefixString = "prefixString" + suffix;
      final String prefixByteArray = "prefixByteArray" + suffix;
      byte[][] testItems = new byte[testNum][];
      for (int i = 0; i < testNum; i++) {
        db.putInt(prefixInt + "_" + i, randomIntArray[i]);
        db.putString(prefixString + "_" + i, randomStringArray[i]);
        r.nextBytes(testItems[i] = new byte[1024]);
        db.putByteArray(prefixByteArray + "_" + i, testItems[i]);
      }
      db.apply();

      // reset

      db.repair(new AssertionError("Corruption: not an sstable (bad magic number)"));
      db.flush();

      // read

      assertEquals(db.tryGetString("string"), "");
      assertEquals(db.getIntArray("intArray"), new int[0]);
      assertEquals(db.getByteArray("byteArray"), new byte[0]);
      assertEquals(db.getLongArray("longArray"), new long[0]);
      assertEquals(db.getFloatArray("floatArray"), new float[0]);
      assertEquals(db.getDoubleArray("doubleArray"), new double[0]);
      assertEquals(db.getStringArray("stringArray"), new String[0]);
      assertEquals(db.getStringArray("stringArray2"), new String[] {""});

      ms = SystemClock.uptimeMillis();
      for (int i = 0; i < 1000; i++) {
        assertEquals(db.contains("void" + suffix), true);
        assertEquals(db.tryGetInt("int" + suffix), randomInt);
        assertEquals(db.tryGetLong("long" + suffix), randomLong);
        assertEquals(db.tryGetBoolean("boolean" + suffix), randomBoolean);
        assertEquals(db.tryGetByte("byte" + suffix), randomByte);
        assertEquals(db.tryGetFloat("float" + suffix), randomFloat);
        assertEquals(db.tryGetDouble("double" + suffix), randomDouble);
        assertEquals(db.tryGetString("string" + suffix), randomString);

        assertEquals(db.getIntArray("intArray" + suffix), randomIntArray);
        assertEquals(db.getByteArray("byteArray" + suffix), randomByteArray);
        assertEquals(db.getLongArray("longArray" + suffix), randomLongArray);
        assertEquals(db.getFloatArray("floatArray" + suffix), randomFloatArray);
        assertEquals(db.getDoubleArray("doubleArray" + suffix), randomDoubleArray);
        assertEquals(db.getStringArray("stringArray" + suffix), randomStringArray);
      }

      /*"intArray" + suffix, randomIntArray);
          db.putByteArray("byteArray" + suffix, randomByteArray);
          db.putLongArray("longArray" + suffix, randomLongArray);
          db.putFloatArray("floatArray" + suffix, randomFloatArray);
          db.putDoubleArray("doubleArray" + suffix, randomDoubleArray);
          db.putStringArray("stringArray" */
      for (LevelDB.Entry entry : db.find("int"))
        if (!entry.key().startsWith("intArray"))
          assertEquals(entry.asInt(), randomInt);
      for (LevelDB.Entry entry : db.find("long"))
        if (!entry.key().startsWith("longArray"))
          assertEquals(entry.asLong(), randomLong);
      for (LevelDB.Entry entry : db.find("boolean"))
        assertEquals(entry.asBoolean(), randomBoolean);
      for (LevelDB.Entry entry : db.find("float"))
        if (!entry.key().startsWith("floatArray"))
          assertEquals(entry.asFloat(), randomFloat);
      for (LevelDB.Entry entry : db.find("double"))
        if (!entry.key().startsWith("doubleArray"))
          assertEquals(entry.asDouble(), randomDouble);
      for (LevelDB.Entry entry : db.find("string"))
        if (!entry.key().equals("string") && !entry.key().startsWith("stringArray"))
          assertEquals(entry.asString(), randomString);
      for (LevelDB.Entry entry : db.find("byteArray"))
        if (!entry.key().equals("byteArray"))
          assertEquals(entry.asByteArray(), randomByteArray);

      assertEquals(db.findByValue("byteArray" + suffix, randomByteArray), "byteArray" + suffix);

      assertEquals(db.getSizeByPrefix(prefixInt), testNum);
      assertEquals(db.getSizeByPrefix(prefixString), testNum);

      int iterationCount = 0;
      for (LevelDB.Entry entry : db.find(prefixInt)) {
        assertEquals(entry.key().substring(0, entry.key().lastIndexOf('_')), prefixInt);
        assertEquals(entry.asInt(), randomIntArray[iterationCount]);
        iterationCount++;
      }
      assertEquals(iterationCount, testNum);
      iterationCount = 0;
      for (LevelDB.Entry entry : db.find(prefixString)) {
        assertEquals(entry.key().substring(0, entry.key().lastIndexOf('_')), prefixString);
        assertEquals(entry.asString(), randomStringArray[iterationCount]);
        iterationCount++;
      }
      assertEquals(iterationCount, testNum);

      iterationCount = 0;
      byte[][] allItems = db.findAll(prefixByteArray);
      for (byte[] item : allItems) {
        assertEquals(item, testItems[iterationCount]);
        iterationCount++;
      }
      assertEquals(iterationCount, testNum);

      assertEquals(db.findFirst(prefixInt), prefixInt + "_0");
      assertEquals(db.findFirst(prefixString), prefixString + "_0");
      assertEquals(db.findFirst(prefixByteArray), prefixByteArray + "_0");

      long elapsedMs = SystemClock.uptimeMillis() - ms;
      Log.i("Done db test in %dms", elapsedMs);

      ms = SystemClock.uptimeMillis();
      String prevStats = db.getProperty("leveldb.stats");
      long prevSize = db.getSize();
      int removedCount = db.removeByAnyPrefix("void", "int", "long", "boolean", "byte", "float", "double", "string", prefixInt, prefixString, prefixByteArray);
      elapsedMs = SystemClock.uptimeMillis() - ms;
      String stats = db.getProperty("leveldb.stats");
      String memoryUsage = db.getProperty("leveldb.approximate-memory-usage");
      long newSize = db.getSize();
      assertEquals(newSize, 0);
      Log.i("Done db test in %dms count:%d size:%d->%d\n%s\n%s\n%s", elapsedMs, removedCount, prevSize, newSize, prevStats, stats, memoryUsage);
    } finally {
      db.close();
    }
    FileUtils.delete(testDb, true);
  }
}
