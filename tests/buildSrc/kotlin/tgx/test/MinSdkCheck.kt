package tgx.test

import Sdk
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import tgx.gradle.fatal
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

fun Project.configureMinSdkCheck() {
  val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
  val checkDebugVariants = tasks.register("checkMinSdk")
  androidComponents.onVariants { variant ->
    // Newer NDK raises minSdk of variants, e.g. of legacy flavor built without -PuseLegacyNdk=true
    val variantMinSdk = variant.productFlavors.firstNotNullOfOrNull { (_, flavor) ->
      Sdk.VARIANTS.values
        .firstOrNull {
          it.flavor == flavor
        }
        ?.minSdk
    } ?: variant.minSdk.apiLevel
    val variantSources = listOfNotNull(variant.sources.java?.all, variant.sources.kotlin?.all)
    val check = tasks.register<CheckMinSdkTask>("check${variant.name.uppercaseFirstChar()}MinSdk") {
      minSdk.set(variantMinSdk)
      sources.from(variantSources)
      apiLevels.fileProvider(
        androidComponents.sdkComponents.bootClasspath.map { files ->
          files
            .map { it.asFile }
            .first {
              it.name == "android.jar"
            }
            .resolveSibling("data/api-versions.xml")
        }
      )
      report.set(layout.buildDirectory.file("reports/minSdk/${variant.name}.txt"))
    }
    variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
      .use(check)
      .toGet(ScopedArtifact.CLASSES, CheckMinSdkTask::jars, CheckMinSdkTask::directories)
    if (variant.buildType == "debug") {
      checkDebugVariants.configure {
        dependsOn(check)
      }
    }
  }
}

/**
 * Lists classes of a variant that use classes added after its minSdk in declarations,
 * which version checks can't guard: supertypes, types of fields, parameters and results of methods,
 * and caught exceptions, and imports of such classes, see [findNewImports].
 * Fails for variants for Android 4.x: Dalvik fails to verify such classes,
 * e.g. with NoClassDefFoundError or VerifyError, even when the code that needs new classes
 * never runs there, while ART of Android 5.0+ resolves classes only when they are used.
 * Classes and methods annotated with `@RequiresApi` or `@TargetApi`,
 * as well as anonymous and local classes, are used only after version checks,
 * which are verified by NewApi check of lint, together with calls inside methods.
 */
@CacheableTask
abstract class CheckMinSdkTask : DefaultTask() {
  @get:Input
  abstract val minSdk: Property<Int>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val apiLevels: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val jars: ListProperty<RegularFile>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val directories: ListProperty<Directory>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  @get:OutputFile
  abstract val report: RegularFileProperty

  @TaskAction
  fun check() {
    val classes = directories.get().asSequence().flatMap { directory ->
      directory.asFile.walk()
        .filter {
          it.extension == "class"
        }
        .map { it.readBytes() }
    } + jars.get().asSequence().flatMap { jar ->
      ZipFile(jar.asFile).use { zip ->
        zip.entries().asSequence()
          .filter { it.name.endsWith(".class") }
          .map { entry ->
            zip.getInputStream(entry).use { it.readBytes() }
          }
          .toList()
      }
    }
    val levels = readApiLevels(apiLevels.get().asFile)
    // Imports name nested classes with dots
    val importLevels = levels.mapKeys { (name, _) ->
      name.replace('/', '.').replace('$', '.')
    }
    val imports = mutableListOf<String>()
    val sourceFiles = sources.asFileTree.matching {
      include("**/*.java", "**/*.kt")
    }
    sourceFiles.visit {
      if (!isDirectory) {
        imports += findNewImports(path, file.readText(), minSdk.get(), importLevels)
      }
    }
    val violations = findMinSdkViolations(classes, minSdk.get(), levels) + imports.sorted()
    val reportFile = report.get().asFile
    reportFile.writeText(violations.joinToString("\n"))
    if (violations.isEmpty()) {
      return
    }
    val message = "Classes added after minSdk ${minSdk.get()} are used in declarations or imported"
    if (minSdk.get() < ART_SDK) {
      fatal("$message:\n${violations.joinToString("\n")}")
    }
    logger.info("$message, which Android 5.0+ allows: $reportFile")
  }
}

/** API levels by internal names of classes */
internal fun readApiLevels(file: File): Map<String, Int> {
  val apiLevels = HashMap<String, Int>()
  SAXParserFactory.newInstance().newSAXParser().parse(file, object : DefaultHandler() {
    override fun startElement(
      uri: String?,
      localName: String?,
      qName: String,
      attributes: Attributes
    ) {
      val since = attributes.getValue("since")
      if (qName == "class" && since != null) {
        // Minor versions, e.g. 36.1, are added after major ones
        apiLevels[attributes.getValue("name")] = since.substringBefore('.').toInt()
      }
    }
  })
  return apiLevels
}

// Android 5.0 replaced Dalvik with ART
private const val ART_SDK = 21

// Core library desugaring adds many of their classes to older Android,
// NewApi check of lint knows which
private val DESUGARED_PACKAGES = listOf("java/time/", "java/util/")

private val VERSION_ANNOTATIONS = setOf(
  "Landroidx/annotation/RequiresApi;",
  "Landroid/annotation/TargetApi;"
)

internal fun findMinSdkViolations(
  classes: Sequence<ByteArray>,
  minSdk: Int,
  apiLevels: Map<String, Int>
): List<String> {
  val violations = mutableListOf<Pair<String, String>>()
  val annotatedClasses = mutableSetOf<String>()

  fun describe(type: Type): String? {
    val element = if (type.sort == Type.ARRAY) type.elementType else type
    if (element.sort != Type.OBJECT) {
      return null
    }
    if (DESUGARED_PACKAGES.any { element.internalName.startsWith(it) }) {
      return null
    }
    val apiLevel = apiLevels[element.internalName]?.takeIf {
      it > minSdk
    } ?: return null
    return "${element.className} (API $apiLevel)"
  }

  for (bytes in classes) {
    ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
      private lateinit var className: String
      private var isLocal = false
      private val found = mutableListOf<String>()

      override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<String>?
      ) {
        className = name
        val supertypes = listOfNotNull(superName) + interfaces.orEmpty()
        supertypes
          .mapNotNull { describe(Type.getObjectType(it)) }
          .forEach {
            found += "extends $it"
          }
      }

      override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
        // Anonymous and local classes are created only where they are declared
        isLocal = true
      }

      override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        if (descriptor in VERSION_ANNOTATIONS) {
          annotatedClasses += className
        }
        return null
      }

      override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
      ): FieldVisitor? {
        val type = describe(Type.getType(descriptor)) ?: return null
        return object : FieldVisitor(Opcodes.ASM9) {
          private var isAnnotated = false

          override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            isAnnotated = isAnnotated || descriptor in VERSION_ANNOTATIONS
            return null
          }

          override fun visitEnd() {
            if (!isAnnotated) {
              found += "field $name: $type"
            }
          }
        }
      }

      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
      ): MethodVisitor? {
        if (access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) != 0) {
          return null
        }
        val types = Type.getArgumentTypes(descriptor).toList() + Type.getReturnType(descriptor)
        val methodFound = types
          .mapNotNull(::describe)
          .map { "method $name: $it" }
          .toMutableList()
        return object : MethodVisitor(Opcodes.ASM9) {
          private var isAnnotated = false

          override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            isAnnotated = isAnnotated || descriptor in VERSION_ANNOTATIONS
            return null
          }

          override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
            val caught = type?.let { describe(Type.getObjectType(it)) } ?: return
            methodFound += "method $name catches $caught"
          }

          override fun visitEnd() {
            if (!isAnnotated) {
              found += methodFound
            }
          }
        }
      }

      override fun visitEnd() {
        if (!isLocal) {
          found.forEach {
            violations += className to it
          }
        }
      }
    }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
  }

  // Annotation of a class applies to classes nested in it
  fun isAnnotated(className: String): Boolean = className in annotatedClasses ||
    className.contains('$') && isAnnotated(className.substringBeforeLast('$'))

  return violations
    .filter { (className, _) ->
      !isAnnotated(className)
    }
    .map { (className, violation) ->
      "${className.replace('/', '.')}: $violation"
    }
    .distinct()
    .sorted()
}

private val IMPORT = Regex("""^import\s+(?:static\s+)?([\w.]+)""", RegexOption.MULTILINE)

private val VERSION_ANNOTATED_CLASS = Regex(
  """^@(?:RequiresApi|TargetApi)\b.*\R(?:@.*\R)*(?:\w+ )*(?:class|interface|object) """,
  RegexOption.MULTILINE
)

/**
 * Classes added after minSdk are referenced by their full name only inside version checks:
 * Dalvik on Android 4.x fails to verify whole classes that use them elsewhere,
 * and imports make such uses easy to miss.
 * Files of classes annotated with `@RequiresApi` or `@TargetApi` may import them.
 */
internal fun findNewImports(
  path: String,
  source: String,
  minSdk: Int,
  importLevels: Map<String, Int>
): List<String> {
  if (VERSION_ANNOTATED_CLASS.containsMatchIn(source)) {
    return emptyList()
  }
  return IMPORT.findAll(source)
    .mapNotNull { match ->
      // Imports of nested classes and static members start with name of the class
      val names = generateSequence(match.groupValues[1]) {
        it.substringBeforeLast('.', "").ifEmpty { null }
      }
      val className = names.firstOrNull {
        it in importLevels
      } ?: return@mapNotNull null
      val apiLevel = importLevels.getValue(className).takeIf { level ->
        level > minSdk && DESUGARED_PACKAGES.none {
          className.startsWith(it.replace('/', '.'))
        }
      } ?: return@mapNotNull null
      "$path: import $className (API $apiLevel)"
    }
    .distinct()
    .toList()
}
