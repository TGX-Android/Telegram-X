package tgx.test

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import tgx.gradle.fatal
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

private const val TOP_METHODS = 20

/**
 * Prints methods that take the most time in method traces,
 * which benchmark library records once for each microbenchmark after measuring it
 */
@DisableCachingByDefault(because = "Profiles are printed")
abstract class DescribeProfilesTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val results: DirectoryProperty

  /** Part of names of benchmarks, e.g. `layout_Text[text=short]` */
  @get:Input
  @get:Optional
  abstract val benchmark: Property<String>

  @TaskAction
  fun describe() {
    val name = benchmark.orNull
    val traced = loadResults(results.get().asFile)
      .filter {
        it.methodTrace != null && (name == null || name in it.key)
      }
      .sortedBy { it.key }
    if (traced.isEmpty()) {
      val of = if (name != null) " of $name" else ""
      fatal("No method traces$of")
    }
    logger.quiet(
      "Methods by own time, without methods they call. " +
        "Tracing slows down Java code much more than native code, e.g. of Android graphics, " +
        "so only numbers of calls are exact"
    )
    for (result in traced) {
      val profile = readMethodTrace(result.methodTrace!!)
      val time = profile.methods
        .sumOf { it.ownTime }
        .coerceAtLeast(1)
      logger.quiet("")
      logger.quiet("#### ${result.key}: ${profile.calls} calls in thread ${profile.thread}")
      logger.quiet("")
      logger.quiet("| Own time | With calls | Calls | Method |")
      logger.quiet("|---|---|---|---|")
      val topMethods = profile.methods
        .sortedByDescending { it.ownTime }
        .take(TOP_METHODS)
      for (method in topMethods) {
        logger.quiet(
          "| %.1f%% | %.1f%% | %d | %s |".format(
            Locale.ROOT,
            method.ownTime * 100.0 / time,
            method.totalTime * 100.0 / time,
            method.calls,
            method.name
          )
        )
      }
    }
  }
}

private class MethodStats(val name: String) {
  var ownTime = 0L
  var totalTime = 0L
  var calls = 0
  // Number of calls on stack, for recursion
  var depth = 0
}

private class Frame(val method: MethodStats, val start: Long) {
  var callsTime = 0L
}

private class Profile(val thread: String, val calls: Int, val methods: Collection<MethodStats>)

// Format of Debug.startMethodTracing, see dmtrace.h in Android Runtime
private const val TRACE_MAGIC = 0x574f4c53

/** Stats of the thread with the most method calls */
private fun readMethodTrace(file: File): Profile {
  val bytes = file.readBytes()
  // Text header is followed by binary records, ISO-8859-1 keeps offsets of bytes
  val text = String(bytes, Charsets.ISO_8859_1)
  val end = text.indexOf("\n*end\n")
  if (!text.startsWith("*version\n") || end < 0) {
    fatal("$file isn't a method trace written by Debug.startMethodTracing")
  }
  val headerEnd = end + "\n*end\n".length
  val threads = mutableMapOf<Int, String>()
  val methods = mutableMapOf<Long, String>()
  val options = mutableMapOf<String, String>()
  var section = ""
  for (line in String(bytes, 0, headerEnd, Charsets.UTF_8).lines().filter { it.isNotEmpty() }) {
    when {
      line.startsWith("*") -> section = line
      section == "*version" && '=' in line -> {
        options[line.substringBefore('=')] = line.substringAfter('=')
      }
      section == "*threads" -> {
        threads[line.substringBefore('\t').toInt()] = line.substringAfter('\t')
      }
      section == "*methods" -> {
        val (id, className, methodName) = line.split('\t')
        methods[java.lang.Long.decode(id)] = "$className.$methodName"
      }
    }
  }

  val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  buffer.position(headerEnd)
  if (buffer.int != TRACE_MAGIC) {
    fatal("$file has no records of method trace")
  }
  val version = buffer.short.toInt()
  val recordsStart = headerEnd + buffer.short
  buffer.long // Start time
  val recordSize = when (version) {
    1 -> 9
    2 -> 10
    3 -> buffer.short.toInt()
    else -> fatal("$file has method trace of unsupported version $version")
  }
  // Wall time, which benchmarks measure, follows thread time when clock is dual
  val timeOffset = if (options["clock"] == "dual") 4 else 0
  val threadIdSize = if (version == 1) 1 else 2
  fun forEachRecord(action: (thread: Int, method: Long, time: Long) -> Unit) {
    var position = recordsStart
    while (position + recordSize <= bytes.size) {
      val thread = if (threadIdSize == 1) {
        bytes[position].toInt() and 0xff
      } else {
        buffer.getShort(position).toInt() and 0xffff
      }
      val method = buffer.getInt(position + threadIdSize).toLong() and 0xffffffffL
      val time = buffer.getInt(position + threadIdSize + 4 + timeOffset).toLong() and 0xffffffffL
      action(thread, method, time)
      position += recordSize
    }
  }

  val records = mutableMapOf<Int, Int>()
  forEachRecord { thread, _, _ ->
    records[thread] = (records[thread] ?: 0) + 1
  }
  val thread = records
    .maxByOrNull { it.value }
    ?.key
    ?: fatal("$file has no records of method trace")
  val stats = mutableMapOf<String, MethodStats>()
  val stack = mutableListOf<Frame>()
  var calls = 0
  forEachRecord { recordThread, value, time ->
    if (recordThread != thread) {
      return@forEachRecord
    }
    // Lowest 2 bits are action: enter, exit or exit by exception
    val id = value and 3L.inv()
    val name = methods[id] ?: "0x%x".format(id)
    val method = stats.getOrPut(name) { MethodStats(name) }
    if (value and 3L == 0L) {
      calls++
      method.calls++
      method.depth++
      stack += Frame(method, time)
      return@forEachRecord
    }
    // Methods called before tracing started exit without entering
    val index = stack.indexOfLast {
      it.method === method
    }
    if (index < 0) {
      return@forEachRecord
    }
    while (stack.size > index) {
      val frame = stack.removeAt(stack.lastIndex)
      val duration = time - frame.start
      frame.method.ownTime += duration - frame.callsTime
      frame.method.depth--
      if (frame.method.depth == 0) {
        frame.method.totalTime += duration
      }
      stack.lastOrNull()?.let {
        it.callsTime += duration
      }
    }
  }
  val calledMethods = stats.values.filter {
    it.calls > 0
  }
  return Profile(threads[thread] ?: thread.toString(), calls, calledMethods)
}
