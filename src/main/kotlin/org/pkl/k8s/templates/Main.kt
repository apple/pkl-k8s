/**
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("MemberVisibilityCanBePrivate")

package org.pkl.k8s.templates

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Year
import kotlin.io.path.*
import org.pkl.core.Version
import org.pkl.formatter.Formatter
import org.pkl.formatter.GrammarVersion
import org.pkl.parser.Lexer
import org.pkl.parser.Token
import okio.buffer
import okio.source

private const val minPklVersion = "0.25.0"

// properties that are common for all k8s resources and are defined in resourceBaseModule
private val resourceBaseProperties = setOf("apiVersion", "kind")

private val licenseHeader = """
  //===----------------------------------------------------------------------===//
  // Copyright © 2024-${Year.now().value} Apple Inc. and the Pkl project authors. All rights reserved.
  //
  // Licensed under the Apache License, Version 2.0 (the "License");
  // you may not use this file except in compliance with the License.
  // You may obtain a copy of the License at
  //
  //     https://www.apache.org/licenses/LICENSE-2.0
  //
  // Unless required by applicable law or agreed to in writing, software
  // distributed under the License is distributed on an "AS IS" BASIS,
  // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  // See the License for the specific language governing permissions and
  // limitations under the License.
  //===----------------------------------------------------------------------===//
  
  """.trimIndent()

fun main(args: Array<String>) {
  require(args.size > 2) {
    throw ConversionException("Usage: org.pkl.k8s.templates.MainKt outputPath buildDir openApiSpecPath...")
  }

  val outputPath = Path.of(args[0])
  val downloadsDir = Path.of(args[1])
  val versions = args.drop(2).map { Version.parse(it.replace("v", "")) }

  val openApiSpecs = versions
    .associateWith { OASpec.fromJson(Path.of("$downloadsDir/swagger-v$it.json")) }

  val specs = openApiSpecs.mapValues { (version, spec) -> spec.toK8sApiSpec(version) }

  val k8sSpec = specs.merge()

  outputPath.toFile().deleteRecursively()
  outputPath.createDirectories()

  copyResource("K8sObject", outputPath)
  copyResource("K8sResource", outputPath)
  copyPklProject(outputPath)

  generateSchemaModule(k8sSpec, outputPath)

  val formatter = Formatter()

  for (obj in k8sSpec.objects.values) {
    if (obj.isPklModule) {
      val moduleText = buildString { obj.renderPklModule(this) }
      val formatted = formatter.format(moduleText, GrammarVersion.V1)
      outputPath.resolveModuleName(obj.name).createParentDirectories().writeText(formatted)
    }
  }

  if (propertyTypeOverrides.isNotEmpty()) {
    println("Some property type overrides didn't match any property:")
    for (override in propertyTypeOverrides) println(override.key)
  }
  if (forceModule.isNotEmpty()) {
    println("Some force-module resources didn't match any resource:")
    for (module in forceModule) println(module)
  }
}

private fun copyPklProject(outputPath: Path) {
  val path = Path.of(OASpec::class.java.getResource("PklProject.template")!!.toURI())
  Files.copy(path, outputPath.resolve("PklProject"))
}

private fun copyResource(moduleName: String, outputPath: Path) {
  OASpec::class.java.getResourceAsStream("$moduleName.pkl")!!.reader().use { reader ->
    createWriter(moduleName, outputPath).use { writer ->
      reader.copyTo(writer)
    }
  }
}

fun Map<Version, K8sApiSpec>.merge(): K8sApiSpec {
  var first = true
  val objects = mutableMapOf<String, K8sObject>()
  for ((k8sVersion, spec) in entries.sortedBy { it.key }) {
    // add introducedIn, removedIn for classes
    val newObjectNames = spec.objects.keys - objects.keys
    val removedObjectNames = objects.keys - spec.objects.keys
    val existingObjectNames = objects.keys - removedObjectNames
    if (!first) {
      for (name in newObjectNames) {
        spec.objects[name]!!.introducedIn = k8sVersion
      }
    }
    for (name in removedObjectNames) {
      if (objects[name]!!.removedIn == null) {
        objects[name]!!.removedIn = k8sVersion
      }
    }
    // persist introducedIn for objects that did not change
    for (name in existingObjectNames) {
      if (objects[name]!!.introducedIn != null) {
        spec.objects[name]!!.introducedIn = objects[name]!!.introducedIn
      }
    }
    // add introducedIn, removedIn for properties
    for ((name, obj) in spec.objects) {
      objects[name]?.let { oldObj ->
        if (!first) {
          for ((propertyName, property) in obj.properties) {
            val oldProperty = oldObj.properties[propertyName]
            when {
              oldProperty != null ->
                property.introducedIn = oldProperty.introducedIn

              else ->
                property.introducedIn = k8sVersion
            }
          }
        }
        val removedPropertyNames = oldObj.properties.keys - obj.properties.keys
        for (n in removedPropertyNames) {
          val oldProperty = oldObj.properties[n]!!
          obj.properties[n] = oldProperty
          if (oldProperty.removedIn == null) {
            oldProperty.removedIn = k8sVersion
          }
        }
      }
    }
    objects.putAll(spec.objects)
    first = false
  }
  return K8sApiSpec(objects)
}

val Version.k8sVersionString get() = "${major}.${minor}"

fun generateSchemaModule(k8sSpec: K8sApiSpec, outputPath: Path) {
  val builder = StringBuilder()
  builder.appendLine(licenseHeader)
  builder.appendLine(
    """
    @ModuleInfo { minPklVersion = "$minPklVersion" }
    module k8s.k8sSchema
    
    /// Resource modules keyed by `kind` and `apiVersion`.
    ///
    /// Note: Declared template type is [unknown] instead of `K8sResource`
    /// to delay loading a template until it is accessed.
    resourceTemplates: Mapping<String, Mapping<String, unknown>> = new {
    """.trimIndent()
  )

  val resourcesGroupedByKind = k8sSpec.objects.values
    .filter { it.isResource }
    .sortedBy { it.kind }
    .groupBy { it.kind }

  for ((kind, resources) in resourcesGroupedByKind) {
    builder.appendLine("""  ["${kind!!}"] {""")
    for (obj in resources) {
      builder.appendLine("""    ["${obj.apiVersion!!}"] = import("${obj.dirName}/${obj.shortName}.pkl")""")
    }
    builder.appendLine("  }")
  }

  builder.appendLine("}")

  createWriter("k8sSchema", outputPath).use { writer ->
    writer.write(builder.toString())
  }
}

class OASpec(
  private val definitions: Map<String, OADefinition>,
) {
  companion object {
    fun fromJson(json: Path): OASpec {
      val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

      return moshi.adapter(OASpec::class.java).fromJson(json.source().buffer())!!
    }
  }

  fun toK8sApiSpec(version: Version) = K8sApiSpec(
    definitions
      .entries.associate { (name, def) ->
        val strippedName = name.drop(7) /* io.k8s. */
        strippedName to def.toK8sObject(strippedName, version)
      })
}

// `properties`, `required`, and `groupVersionKind`
// are nullable rather than @Transient to work around https://github.com/square/moshi/issues/775
class OADefinition(
  val description: String?,
  private val properties: Map<String, OAProperty>?,
  val required: Set<String>?,
  @Suppress("unused")
  val type: String?,
  // note that this list can have multiple elements (not sure what the meaning is)
  @param:Json(name = "x-kubernetes-group-version-kind")
  val groupVersionKind: List<OAGroupVersionKind>?
) {
  fun toK8sObject(name: String, version: Version): K8sObject {
    val gvk = groupVersionKind?.first()
    return K8sObject(
      name,
      version,
      if (gvk == null) null else if (gvk.group.isEmpty()) gvk.version else "${gvk.group}/${gvk.version}",
      gvk?.kind,
      description,
      (properties ?: emptyMap()).mapValuesNotNull { (name, prop) -> prop.toK8sProperty(name, this) }.toMutableMap()
    )
  }

  @Suppress("NullableBooleanElvis")
  fun isRequiredProperty(prop: OAProperty, name: String) =
    required?.contains(name)
      ?: prop.description?.endsWith("Required.")
      ?: false

  // defined in resourceBaseModule
  fun isResourceBaseProperty(name: String) = groupVersionKind != null && name in resourceBaseProperties
}

class OAProperty(
  val description: String?,
  val type: String?,
  val format: String?,
  private val additionalProperties: OAProperty?, // never has nested additionalProperties/items
  val items: OAProperty?, // never has nested additionalProperties/items
  val enum: List<String>?,
  @Json(name = "\$ref")
  val ref: String?
) {
  companion object {
    const val REF_PREFIX = "#/definitions/io.k8s."
  }

  fun toK8sProperty(name: String, definition: OADefinition): K8sProperty? =
    if (definition.isResourceBaseProperty(name)) null
    else K8sProperty(name, description, definition.isRequiredProperty(this, name), toK8sType())

  private fun toK8sType(): K8sType {
    return when (type) {
      null -> {
        ref!!
        assert(ref.startsWith(REF_PREFIX))
        K8sType.Ref(ref.drop(REF_PREFIX.length))
      }

      "boolean" -> K8sType.Boolean
      "string" -> when {
        enum != null -> K8sType.Enum(enum)
        format == null -> K8sType.String
        format == "byte" -> K8sType.Byte
        else -> throw ConversionException("Unknown $type format: $format")
      }

      "integer" -> when (format) {
        "int32" -> K8sType.Int32
        "int64" -> K8sType.Int64
        else -> throw ConversionException("Unknown $type format: $format")
      }

      "number" -> when (format) {
        "double" -> K8sType.Double
        else -> throw ConversionException("Unknown $type format: $format")
      }

      "array" -> K8sType.Array(items!!.toK8sType())
      "object" -> K8sType.Object(additionalProperties?.toK8sType() ?: K8sType.String)
      else -> throw ConversionException("Unknown type: $type")
    }
  }
}

class OAGroupVersionKind(
  val group: String,
  val version: String,
  val kind: String
)

class K8sApiSpec(val objects: Map<String, K8sObject>) {

  init {
    for (obj in objects.values) {
      for (prop in obj.properties.values)
        when (val type = prop.type) {
          is K8sType.Ref -> {
            val target = resolveRef(type)
            if (target != obj) {
              obj.outRefs += target
              target.inRefs += obj
            }
          }

          is K8sType.Array -> if (type.elementType is K8sType.Ref) {
            val target = resolveRef(type.elementType)
            if (target != obj) {
              obj.outRefs += target
              target.inRefs += obj
            }
          }

          is K8sType.Object -> if (type.valueType is K8sType.Ref) {
            val target = resolveRef(type.valueType)
            if (target != obj) {
              obj.outRefs += target
              target.inRefs += obj
            }
          }

          else -> { /* noop */
          }
        }
    }
  }

  private fun resolveRef(ref: K8sType.Ref): K8sObject = objects[ref.target]
    ?: throw ConversionException("Cannot resolve reference `${ref.target}`.")
}

class ModuleImport(
  val isMoved: Boolean = false,
  val k8sObject: K8sObject,
) {
  val aliasedImportName = "${k8sObject.shortName}Module"
  val importStatement = buildString {
    append("import \".../")
    append(k8sObject.dirName)
    append("/")
    append(k8sObject.shortName)
    append(".pkl\"")
    if (isMoved) {
      append(" as ")
      append(aliasedImportName)
    }
    appendLine()
  }
}

interface Versioned {
  var removedIn: Version?
  var introducedIn: Version?
}

val Versioned.k8sVersionAnnotations: String?
  get() =
    (removedIn ?: introducedIn)?.let {
      buildString {
        append("@K8sVersion { ")
        if (introducedIn != null) {
          append("""introducedIn = "${introducedIn!!.k8sVersionString}"""")
        }
        if (removedIn != null) {
          if (introducedIn != null) {
            append("; ")
          }
          append("""removedIn = "${removedIn!!.k8sVersionString}"""")
        }
        append(" }")
      }
    }

data class K8sObject(
  val name: String, // has leading "k8s." stripped
  val version: Version, // The k8s version that this object was parsed from
  val apiVersion: String?,
  val kind: String?,
  val description: String? = null,
  val properties: MutableMap<String, K8sProperty> = mutableMapOf(),
  override var removedIn: Version? = null, // The version this resource was removed in
  override var introducedIn: Version? = null, // The version this resource was added in
) : Versioned {

  val shortName = name.substringAfterLast('.')

  val dirName = name.substringBeforeLast('.', "").replace('.', '/')

  val isResource = apiVersion != null // implies kind != null

  private val isReplaced = name in replacedTypes

  // objects directly referenced by this object; never contains this object itself
  val outRefs = mutableSetOf<K8sObject>()

  // objects directly referencing this object; never contains this object itself
  val inRefs = mutableSetOf<K8sObject>()

  private val pklModule: K8sObject? by lazy {
    if (isReplaced) null
    else if (isResource) this
    else if (forceModule.remove(name)) this
    else if (inRefs.isNotEmpty()) {
      val first = inRefs.first()
      if (dirName == first.dirName && inRefs.all { it.pklModule == first.pklModule }) first.pklModule else this
    } else this
  }

  val isPklModule: Boolean by lazy { pklModule == this }

  private val annotations: String by lazy {
    buildString {
      if (description.hasDeprecation()) {
        appendLine("@Deprecated")
      }
      k8sVersionAnnotations?.let(this::appendLine)
      appendLine("""@ModuleInfo { minPklVersion = "$minPklVersion" }""")
    }
  }

  private val pklDocComment: String? get() = description?.toPklDocComment()

  fun getImports(refs: Collection<K8sObject>, visited: Set<K8sObject> = emptySet()): List<ModuleImport> = refs
    .toList()
    .filterNot { visited.contains(it) || it.isReplaced }
    .flatMap { ref ->
      if (ref.pklModule == this) {
        getImports(ref.outRefs, visited + ref)
      } else {
        listOf(
          ModuleImport(
            isMoved = movedTypes.find {
              val (from, destModule) = it
              val (moduleName) = from
              destModule == ref.name && moduleName == this.name
            } != null,
            k8sObject = ref
          )
        )
      }
    }
    .distinctBy { it.k8sObject }

  fun renderPklModule(out: Appendable) {
    out.appendLine(licenseHeader)
    if (pklDocComment != null) {
      out.appendLine(pklDocComment)
    }
    out.append(annotations)

    val imports = getImports(outRefs)

    if (isResource) {
      // allow for consumers to add their own hidden properties
      out.append("open ")
    }

    out
      .append("module k8s.")
      .appendLine(name.split('.').joinToString(".") { Lexer.maybeQuoteIdentifier(it) })
      .appendLine()
      .append("extends \"")
      .append(if (isResource) ".../K8sResource.pkl" else ".../K8sObject.pkl")
      .appendLine('"')
      .appendLine()

    for (obj in imports) {
      if (obj.k8sObject.isReplaced) continue
      out.append(obj.importStatement)
    }
    if (imports.isNotEmpty()) {
      out.appendLine()
    }

    if (isResource) {
      // use string literal types for `apiVersion` and `kind` so that they can't be overridden
      out
        .append("fixed apiVersion: \"")
        .append(apiVersion)
        .appendLine('"')
        .appendLine()
        .append("fixed kind: \"")
        .append(kind)
        .appendLine('"')
        .appendLine()
    }

    for (prop in properties.values) {
      if (prop.pklDocComment != null) {
        out.appendLine(prop.pklDocComment)
      }
      if (prop.annotations != null) {
        out.appendLine(prop.annotations)
      }
      val propType = renderDeclaredType(Triple(name, null, prop.name), prop)
      out
        .append(Lexer.maybeQuoteIdentifier(prop.name))
        .append(": ")
        .appendLine(propType)
        .appendLine()
    }

    renderPklClasses(this, outRefs, out)

    renderTypeAliases(imports, out)
  }

  /**
   * Render typealiases for backwards compatibility purposes.
   *
   * E.g.
   * ```
   * @Deprecated { message = "`ContainerPort` has been moved into its own module."; replaceWith = "ContainerPortModule" }
   * typealias ContainerPort = ContainerPortModule
   * ```
   */
  private fun renderTypeAliases(
    objects: List<ModuleImport>,
    out: Appendable
  ) {
    for (obj in objects) {
      if (obj.isMoved) {
        out
          .append("@Deprecated { message = \"`")
          .append(obj.k8sObject.shortName)
          .append("` has been moved into its own module.\"; replaceWith = \"")
          .append(obj.aliasedImportName)
          .appendLine("\" }")
          .append("typealias ")
          .append(obj.k8sObject.shortName)
          .append(" = ")
          .appendLine(obj.aliasedImportName)
          .appendLine()
      }
    }
    renamedTypes[name]?.let { (oldName, newName) ->
      out
        .append("@Deprecated { message = \"`")
        .append(oldName)
        .append("` has been renamed to [")
        .append(newName)
        .append("].\"; replaceWith = \"")
        .append(newName)
        .appendLine("\" }")
        .append("typealias ")
        .append(oldName)
        .append(" = ")
        .appendLine(newName)
        .appendLine()
    }
  }

  private fun renderPklClasses(
    module: K8sObject,
    objects: Collection<K8sObject>,
    out: Appendable,
    visited: MutableSet<K8sObject> = mutableSetOf()
  ) {

    for (obj in objects) {
      if (obj.pklModule != this || !visited.add(obj) || obj.isReplaced) continue
      obj.renderPklClass(module, out)
      out.appendLine()
      renderPklClasses(module, obj.outRefs, out, visited)
    }
  }

  private fun renderDeclaredType(override: Triple<String, String?, String>, prop: K8sProperty): String {
    val overriddenType = propertyTypeOverrides.remove(override)
    val convertedType = overriddenType ?: prop.pklType
    if (prop.pklType == overriddenType) {
      println("redundant override: $override")
    }
    val imports = (pklModule?.getImports(pklModule!!.outRefs) ?: getImports(outRefs)).filter { it.isMoved }
    val ref = imports.find { convertedType.contains(it.k8sObject.shortName) }
    return if (ref != null) {
      convertedType.replaceIdentifier(ref.k8sObject.shortName, ref.aliasedImportName)
    } else {
      convertedType
    }
  }

  private fun String.replaceIdentifier(old: String, new: String): String {
    val lexer = Lexer(this)
    while (true) {
      val nextToken = lexer.next()
      if (nextToken == Token.IDENTIFIER && lexer.text() == old) {
        val span = lexer.span()
        return replaceRange(span.charIndex, span.stopIndex() + 1, new)
      }
      if (nextToken == Token.EOF) {
        return this
      }
    }
  }

  private fun renderPklClass(module: K8sObject, out: Appendable) {
    if (pklDocComment != null) {
      out.appendLine(pklDocComment)
    }
    if (pklDocComment.hasDeprecation()) {
      out.appendLine("@Deprecated")
    }
    out
      .append("class ")
      .append(shortName)
      .appendLine(" {")

    var first = true
    for (prop in properties.values) {
      if (first) first = false else out.appendLine()

      val docComment = prop.pklDocComment
      if (docComment != null) {
        out.appendLine(docComment.prependIndent("  "))
      }
      prop.annotations?.let { out.appendLine(it.prependIndent("  ")) }
      out
        .append("  ")
        .append(Lexer.maybeQuoteIdentifier(prop.name))
        .append(": ")
        .appendLine(renderDeclaredType(Triple(module.name, shortName, prop.name), prop))
    }

    out.appendLine("}")
  }

  override fun equals(other: Any?) = this === other || other is K8sObject && name == other.name

  override fun hashCode() = name.hashCode()
}

class K8sProperty(
  val name: String,
  val description: String?,
  private val isRequired: Boolean,
  val type: K8sType,
  override var removedIn: Version? = null, // The version this resource was removed in
  override var introducedIn: Version? = null, // The version this resource was added in
) : Versioned {
  val pklDocComment: String? get() = description?.toPklDocComment()

  val annotations: String?
    get() {
      // If the property name is "deprecated", the property itself is not deprecated.
      val deprecated = !name.equals("deprecated", false)
          && description.hasDeprecation()
      val res = listOfNotNull(
        if (deprecated) "@Deprecated" else null,
        k8sVersionAnnotations
      )
        .joinToString("\n")
      return if (res == "") null else res
    }

  val pklType: String get() = type.toPklType(isRequired)
}

sealed class K8sType {
  data object Boolean : K8sType()
  data object String : K8sType()
  data object Byte : K8sType()
  data object Int32 : K8sType()
  data object Int64 : K8sType()
  data object Double : K8sType()
  class Enum(val enum: List<kotlin.String>) : K8sType()
  class Array(val elementType: K8sType) : K8sType()
  class Object(val valueType: K8sType) : K8sType()
  class Ref(val target: kotlin.String) : K8sType()

  val patternGenericType = Regex("[a-zA-Z\$_][a-zA-Z0-9\$_]*<.+>")

  fun toPklType(isRequired: kotlin.Boolean, isInnerType: kotlin.Boolean = false): kotlin.String {
    val baseType = when (this) {
      is Boolean -> "Boolean"
      is String -> "String"
      is Byte -> "String" // good enough?
      is Int32 -> "Int32"
      is Int64 -> "Int"
      is Double -> "Float"
      is Enum -> enum.joinToString("|") { "\"$it\"" }
      is Array -> "Listing<${elementType.toPklType(isRequired = true, isInnerType = true)}>"
      is Object -> "Mapping<String, ${valueType.toPklType(isRequired = true, isInnerType = true)}>"
      is Ref -> {
        when (target) {
          "apimachinery.pkg.util.intstr.IntOrString" -> "Int|String"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrArray" -> "*JSONSchemaProps|Listing<JSONSchemaProps>"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrBool" -> "*JSONSchemaProps|Boolean"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSONSchemaPropsOrStringArray" -> "*JSONSchemaProps|Listing<String>"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1beta1.JSON" -> "Any"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray" -> "*JSONSchemaProps|Listing<JSONSchemaProps>"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool" -> "*JSONSchemaProps|Boolean"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray" -> "*JSONSchemaProps|Listing<String>"
          "apiextensions-apiserver.pkg.apis.apiextensions.v1.JSON" -> "Any"
          "apimachinery.pkg.apis.meta.v1.Patch" ->
            throw Exception("Found reference to `apimachinery.pkg.apis.meta.v1.Patch`, which is unexpected.")

          in replacedTypes -> target.substringAfterLast('.')
          else -> target.substringAfterLast('.')
        }
      }
    }
    return when {
      baseType.contains('|') && !baseType.matches(patternGenericType) ->
        if (isRequired && isInnerType) baseType
        else if (isRequired) "$baseType = Undefined()"
        else "($baseType)?"

      isRequired -> baseType
      else -> "$baseType?"
    }
  }
}

private fun String.toPklDocComment(): String {
  return "/// " +
      trim()
        .replace("\n", "\n/// ")
        // ordered list
        .replace(Regex("(1\\. .+) 2\\. "), "$1\n/// 2. ")
        .replace(Regex("(2\\. .+) 3\\. "), "$1\n/// 3. ")
        .replace(Regex("(3\\. .+) 4\\. "), "$1\n/// 4. ")
        .replace(Regex("(4\\. .+) 5\\. "), "$1\n/// 5. ")
        // fix indentation of unordered list items
        .replace(Regex("///  +\\* "), "/// * ")
        // unordered list
        .replace(Regex("(?<!///|Single|or|\\d) \\* "), "\n/// * ")
        // dot-terminated sentence
        .replace(Regex("(?<!Ex|i\\.e|e\\.g| vs)\\. +(?!\n)"), ".\n/// ")
        // turn http(s) urls into links (commonmark doesn't auto-link)
        .replace(Regex("\\bhttps?://\\S*"), "<$0>")
        // first sentence is overview -> needs its own paragraph
        .replaceFirst("\n///", "\n///\n///")
}

private fun String?.hasDeprecation(): Boolean =
  if (this == null) false
  else
    contains(Regex("\\bdeprecated\\b", RegexOption.IGNORE_CASE)) &&
        // weed out false positives
        !contains("Recycle (deprecated)") &&
        !contains("Flocker should be considered as deprecated") &&
        !contains("deprecated indicates this version of the custom resource") &&
        !contains("deprecationWarning overrides the default warning returned") &&
        !contains("This replaces the deprecated") &&
        !contains("However, even though the annotation is officially deprecated")

private fun createWriter(moduleName: String, outputPath: Path): Writer =
  outputPath.resolveModuleName(moduleName).apply { createParentDirectories() }.bufferedWriter()

private fun Path.resolveModuleName(moduleName: String): Path {
  val index = moduleName.lastIndexOf('.')
  val moduleDir = if (index == -1) {
    this
  } else {
    resolve(moduleName.take(index).replace('.', '/'))
  }
  val moduleFileName = moduleName.substring(index + 1) + ".pkl"
  return moduleDir.resolve(moduleFileName)
}

class ConversionException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)
