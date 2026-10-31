package tgx.test

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import tgx.gradle.fatal
import java.io.File

// Saved by recordNativeHeap of UI tests
private const val HEAP_PROFILE_SUFFIX = ".heapprofd.perfetto-trace"

// Frames of allocators, called by the code that allocates
private val ALLOCATOR_FRAMES =
  listOf("*malloc*", "*calloc*", "*realloc*", "*memalign*", "operator new*", "*scudo*", "je_*")

private const val TOP_CALL_SITES = 15

// Frames of each call site, from the code that allocates to its callers
private const val FRAMES_OF_CALL_SITE = 10

private const val TOTALS_QUERY = """
SELECT
  printf('%.1f', SUM(MAX(size, 0)) / 1048576.0) AS allocated_mb,
  printf('%.1f', -SUM(MIN(size, 0)) / 1048576.0) AS freed_mb,
  printf('%.1f', SUM(size) / 1048576.0) AS unreleased_mb
FROM heap_profile_allocation
"""

/**
 * Prints native memory that the app allocated and didn't free by call sites, from heap profiles
 * that `recordNativeHeap` of UI tests saves, with trace processor of Perfetto
 */
@DisableCachingByDefault(because = "Profiles are printed")
abstract class DescribeHeapProfilesTask : DefaultTask() {
  @get:Internal
  abstract val results: DirectoryProperty

  /** Path of `trace_processor_shell`, see "Profiles of native memory" in tests/README.md */
  @get:Input
  @get:Optional
  abstract val traceProcessor: Property<String>

  @TaskAction
  fun describe() {
    val profiles = results.get().asFile.walk()
      .filter { it.name.endsWith(HEAP_PROFILE_SUFFIX) }
      .sortedBy { it.name }
      .toList()
      .ifEmpty { fatal("No heap profiles, recordNativeHeap of UI tests records them") }
    val processor = traceProcessor.orNull
      ?: System.getenv("PATH").orEmpty().split(File.pathSeparator)
        .map { File(it, "trace_processor_shell") }
        .firstOrNull { it.canExecute() }
        ?.path
      ?: fatal("No trace_processor_shell in PATH, -PtraceProcessor sets its path")
    val notAllocator = ALLOCATOR_FRAMES.joinToString(" AND ") { "name NOT GLOB '$it'" }
    // Callers of a call site are its parents. Frames without names, e.g. of stripped libraries, are
    // named by their libraries
    val callSitesQuery = """
      WITH RECURSIVE
      sites AS (
        SELECT callsite_id, SUM(MAX(size, 0)) AS allocated, SUM(size) AS unreleased
        FROM heap_profile_allocation
        GROUP BY callsite_id
        ORDER BY allocated DESC
        LIMIT $TOP_CALL_SITES
      ),
      chain(site, parent_id, depth, frame_id) AS (
        SELECT s.callsite_id, c.parent_id, c.depth, c.frame_id
        FROM sites s JOIN stack_profile_callsite c ON c.id = s.callsite_id
        UNION ALL
        SELECT chain.site, c.parent_id, c.depth, c.frame_id
        FROM chain JOIN stack_profile_callsite c ON c.id = chain.parent_id
      ),
      frames AS (
        SELECT chain.site, chain.depth, COALESCE(
          NULLIF(COALESCE(f.deobfuscated_name, DEMANGLE(f.name), f.name), ''),
          SUBSTR(m.name, INSTR(m.name, '!') + 1)
        ) AS name
        FROM chain
        JOIN stack_profile_frame f ON f.id = chain.frame_id
        JOIN stack_profile_mapping m ON m.id = f.mapping
      )
      SELECT
        printf('%.1f', allocated / 1048576.0) AS allocated_mb,
        printf('%.1f', unreleased / 1048576.0) AS unreleased_mb,
        (
          SELECT GROUP_CONCAT(name, ' < ') FROM (
            SELECT name FROM frames
            WHERE site = sites.callsite_id AND $notAllocator
            ORDER BY depth DESC
            LIMIT $FRAMES_OF_CALL_SITE
          )
        ) AS call_site
      FROM sites
      ORDER BY allocated DESC
    """.trimIndent()
    logger.quiet(
      "Native memory by call sites, from the code that allocates to its callers, " +
        "sampled every 4 KB: allocated during the profile, and unreleased at its end"
    )
    for (profile in profiles) {
      logger.quiet("")
      logger.quiet("#### ${profile.name.removeSuffix(HEAP_PROFILE_SUFFIX)}")
      for (query in listOf(TOTALS_QUERY, callSitesQuery)) {
        logger.quiet("")
        logger.quiet(query(processor, profile, query))
      }
    }
  }

  private fun query(processor: String, profile: File, query: String): String {
    val queryFile = File(temporaryDir, "query.sql")
    queryFile.writeText(query)
    val process = ProcessBuilder(processor, "--query-file", queryFile.path, profile.path)
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
      fatal("trace_processor_shell failed:\n$output")
    }
    return output.trim()
  }
}
