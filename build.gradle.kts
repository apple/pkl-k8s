import java.net.URI
import java.net.URL
import java.security.MessageDigest
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel

plugins {
  kotlin("jvm").version(libs.versions.kotlin)
  alias(libs.plugins.pkl)
  alias(libs.plugins.spotless)
  idea
}

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(17)
}

val pklPackageVersion = file("VERSION").readText().trim()

val k8sVersions = listOf(
  "v1.19.6" to "657c689d4014c512baae14e02badb2b53743282b8ea163f0c499a4b622a07097",
  "v1.20.15" to "c7354e6ab4034a89873229a9b7c1fa31fa6242b2e5f5a9b67c3be4fc67cb903c",
  "v1.21.5" to "128a984dbb5a4e5ceceef9dea0db575267678d333f53ed606300a2132d2539cc",
  "v1.22.2" to "d6718670e062681e4dc9e2b9dadf2b311c147cfdabcac628b9018011165d8773",
  "v1.23.4" to "4a744e0c28180c3fe90256d69ebdf57d31b8579d9d5dfcdff141cd4e2d7a3b4a",
  "v1.24.17" to "389079bbbe9242f725d2bf7f3e685c340e870d973438fa3e7ce86e890054a3d6",
  "v1.25.16" to "d4450a999e2fde133faa3bae137c3090bec4dc9fbaadacfa385b0307bf551b72",
  "v1.26.12" to "90ad850630542abe8ced2000669ed5472bbcb7e16d4c8cf803a922453b24565f",
  "v1.27.9" to "2ff17583954f409b1c1ed316144aa6666a2a75f9de0742df05bb66723b0d1b91",
  "v1.28.5" to "cf6e61a06b9603b0332a5228209d708a474399ad4bbd56ff3397f4a0fd61fb92",
  "v1.29.0" to "84c20b1e2bcedce1d9d09c03e0ccb02500cd96e62e4fa54651b0e97b3a211046",
  "v1.30.0" to "94ca7544416a4f27d8cb3f8a332630d41325c5edeb2408a5aefe37ba3fd6af6d",
  "v1.31.7" to "ddcb3d5c3d85849f1fadb56387d3a7bf44712da291e5bd08b34df4e8a4247f8d",
  "v1.32.3" to "2335b4e7f79df85b20066164b14d8f02dd077dee7cd8c7b7fb425009c9837e85",
  "v1.33.6" to "2af5abf98554af1599e516f31f7f1698085e6faa12b816401f569c097b9f7ef6",
  "v1.34.2" to "d3b0cdc2fda15c753206d25ab459dc7c12df64e2fd652b6809687471ea751c37",
  "v1.35.0" to "483500149ee52ce5753d75f5639101d985bb4f5e902cc05b1ba7627465d62446",
  "v1.36.0" to "dcede2063da1d7ad62ecb5af8adb6d7fabd0b52385a7fa0048afb491dac90450",
)

configurations {
  all {
    resolutionStrategy {
      // Prevent transitive deps from resolving to dynamic versions (makes sure that builds are reproducible).
      failOnDynamicVersions()
      // make sure Kotlin reflect is the same version as core Kotlin (moshi-kotlin might bring in a different version)
      force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    }
  }
}

dependencies {
  implementation(libs.moshiKotlin)
  implementation(libs.pklParser)
  implementation(libs.pklCore)
  implementation(libs.pklFormatter)
}

idea {
  project {
    languageLevel = IdeaLanguageLevel("17")
    jdkName = "17"
  }
}

tasks.idea {
  doFirst {
    throw GradleException(
      "To open this project in IntelliJ, go to File->Open and select the project's root directory. Do *not* run `./gradlew idea`."
    )
  }
}

tasks.compileKotlin {
  compilerOptions {
    freeCompilerArgs.addAll(listOf("-Xjsr305=strict", "-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn"))
  }
}

tasks.processResources {
  eachFile {
    if (name == "PklProject.template") {
      expand("pklPackageVersion" to pklPackageVersion)
    }
  }
}

private data class K8sVersion(
  val version: String,
  val inputUrl: URL,
  val outputFile: File,
  val sha256: String,
)

val downloadsDir = "${layout.buildDirectory.get()}/downloads"

val downloadOpenApiSpec = tasks.register("downloadOpenApiSpec") {
  val versions = k8sVersions.map { v ->
    K8sVersion(
      version = v.first,
      inputUrl = URI("https://raw.githubusercontent.com/kubernetes/kubernetes/${v.first}/api/openapi-spec/swagger.json").toURL(),
      outputFile = file("$downloadsDir/swagger-${v.first}.json"),
      sha256 = v.second,
    )
  }
  inputs.property("k8sInputUrls", versions.map { it.inputUrl.toString() })
  outputs.files(versions.map { it.outputFile })

  onlyIf { versions.any { !it.outputFile.exists() } }

  doLast {
    for (v in versions) {
      val bytes = v.inputUrl.readBytes()
      val sha256 = bytes.toSha256()
      if (sha256 != v.sha256) {
        throw GradleException("Checksum mismatch for k8s version ${v.version}:\nExpected: ${v.sha256}\n   Found: $sha256")
      }
      val text = bytes.toString(Charsets.UTF_8)
      // retain top-level properties `definitions` and `info`, remove the rest
      val index = text.indexOf("\"title\": \"Kubernetes\"")
      if (index == -1) throw GradleException("Failed to truncate Open API Spec.")
      val index2 = text.indexOf('}', index)
      if (index2 == -1) throw GradleException("Failed to truncate Open API Spec.")
      val truncated = text.substring(0, index2 + 1) + "\n}"
      v.outputFile.writeText(truncated)
    }
  }
}

fun ByteArray.toSha256(): String {
  val bytes = MessageDigest.getInstance("SHA-256").digest(this)
  return bytes.joinToString("") { "%02x".format(it) }
}

fun cyan(text: String) = "\u001B[36m$text\u001B[0m"

val generateTemplates = tasks.register<JavaExec>("generateTemplates") {
  dependsOn(downloadOpenApiSpec)
  outputs.upToDateWhen { false }

  val outputDir = file("generated-package")
  outputs.dir(outputDir)

  inputs.file("VERSION")
  inputs.files(downloadOpenApiSpec.map { it.outputs.files })
  inputs.files(layout.projectDirectory.dir("src/main/resources/"))

  doFirst {
    outputDir.deleteRecursively()
  }

  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("org.pkl.k8s.templates.MainKt")
  argumentProviders.add(CommandLineArgumentProvider {
    listOf(outputDir.path, downloadsDir) + k8sVersions.map { it.first }
  })
  doLast {
    println("Wrote packages to ${cyan(outputDir.path)}")
  }
}

pkl {
  project {
    packagers {
      register("packageTemplates") {
        projectDirectories.from("generated-package")
        outputPath.set(file("${layout.buildDirectory.get()}/distributions/k8s"))
      }
    }
  }
}

tasks.named("packageTemplates") {
  dependsOn(generateTemplates)
  group = "build"
}

val originalRemoteName = System.getenv("PKL_ORIGINAL_REMOTE_NAME") ?: "origin"

spotless {
  ratchetFrom = "$originalRemoteName/main"
  kotlin {
     (
      """
      /**
       * Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
    """.trimIndent()
    )
  }
  format("pkl") {
    target("*.pkl", "PklProject.template")
    licenseHeader(
      """
      //===----------------------------------------------------------------------===//
      // Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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


    """.trimIndent(), "///"
    )
  }
}
