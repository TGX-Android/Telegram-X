package tgx.test

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import tgx.gradle.fatal
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

// Saved by the tool MessageTypesImages of UI tests, e.g.
// MessageTypesImages.images.blue-001-MessageText-in.png
private const val IMAGES_PREFIX = "MessageTypesImages.images."

private fun imagesIn(directory: File): Map<String, File> = directory.walk()
  .filter { it.name.startsWith(IMAGES_PREFIX) && it.name.endsWith(".png") }
  .filter { "failure" !in it.name }
  .associateBy { it.name.removePrefix(IMAGES_PREFIX).removeSuffix(".png") }

/** Keeps images of types of messages of the last run as the base that later runs compare with */
@DisableCachingByDefault(because = "Images are copied")
abstract class SaveMessageTypesTask : DefaultTask() {
  @get:Internal
  abstract val results: DirectoryProperty

  @get:Internal
  abstract val base: DirectoryProperty

  @TaskAction
  fun save() {
    val images = imagesIn(results.get().asFile)
      .ifEmpty { fatal("No images of types of messages, the tool MessageTypesImages makes them") }
    val directory = base.get().asFile
    directory.deleteRecursively()
    directory.mkdirs()
    for (image in images.values) {
      image.copyTo(File(directory, image.name))
    }
    logger.quiet("Base: ${images.size} images in $directory")
  }
}

/**
 * Compares images of types of messages of the last run with the base pixel by pixel and lists the
 * types whose images changed, with their images before and after
 */
@DisableCachingByDefault(because = "Images are compared")
abstract class CompareMessageTypesTask : DefaultTask() {
  @get:Internal
  abstract val rootDirectory: DirectoryProperty

  @get:Internal
  abstract val results: DirectoryProperty

  @get:Internal
  abstract val base: DirectoryProperty

  @TaskAction
  fun compare() {
    val root = rootDirectory.get().asFile
    val before = imagesIn(base.get().asFile)
      .ifEmpty { fatal("No base, saveMessageTypes saves images of a run as the base") }
    val after = imagesIn(results.get().asFile)
      .ifEmpty { fatal("No images of types of messages, the tool MessageTypesImages makes them") }
    val changed = before.keys.intersect(after.keys).sorted()
      .filter { !isSame(before.getValue(it), after.getValue(it)) }
    if (changed.isNotEmpty()) {
      logger.quiet("Changed types")
    }
    for (name in changed) {
      logger.quiet(name)
      logger.quiet("  before: ${before.getValue(name).relativeTo(root).invariantSeparatorsPath}")
      logger.quiet("  after: ${after.getValue(name).relativeTo(root).invariantSeparatorsPath}")
    }
    val same = before.keys.intersect(after.keys).size - changed.size
    val new = (after.keys - before.keys).sorted()
    val missing = (before.keys - after.keys).sorted()
    logger.quiet(
      "Images: $same same, ${changed.size} changed, ${new.size} new, ${missing.size} missing"
    )
    if (new.isNotEmpty()) {
      logger.quiet("New: ${new.joinToString()}")
    }
    // A run of a part of the types by messageTypes.filter misses most of them
    if (missing.isNotEmpty() && missing.size <= 20) {
      logger.quiet("Missing: ${missing.joinToString()}")
    }
  }

  private fun isSame(before: File, after: File): Boolean {
    // Channels that differ by 1 are the same: the wallpaper behind messages is drawn with such
    // differences in single pixels from one run to the next
    val a = ImageIO.read(before)
    val b = ImageIO.read(after)
    if (a.width != b.width || a.height != b.height) {
      return false
    }
    val pixelsA = a.getRGB(0, 0, a.width, a.height, null, 0, a.width)
    val pixelsB = b.getRGB(0, 0, b.width, b.height, null, 0, b.width)
    return pixelsA.indices.all { i ->
      (0 until 32 step 8).all { shift ->
        abs((pixelsA[i] ushr shift and 0xFF) - (pixelsB[i] ushr shift and 0xFF)) <= 1
      }
    }
  }
}
