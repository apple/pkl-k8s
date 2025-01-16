import java.net.URI
import java.net.URL
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
  "v1.19.6",
  "v1.20.15",
  "v1.21.5",
  "v1.22.2",
  "v1.23.4",
  "v1.24.17",
  "v1.25.16",
  "v1.26.12",
  "v1.27.9",
  "v1.28.5",
  "v1.29.0",
  "v1.30.0"
)

val isCiBuild = System.getenv("CI") != null

configurations {
  all {
    resolutionStrategy {
      // make sure Kotlin reflect is the same version as core Kotlin (moshi-kotlin might bring in a different version)
      force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    }
  }
}

dependencies {
  implementation(libs.moshiKotlin)
  // used to quote identifiers
  implementation(libs.pklCore)
  // used for lexing
  implementation(libs.antlr)
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
  kotlinOptions {
    freeCompilerArgs = freeCompilerArgs +
        listOf("-Xjsr305=strict", "-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
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
  val inputUrl: URL,
  val outputFile: File
)

val downloadsDir = "${layout.buildDirectory.get()}/downloads"

val downloadOpenApiSpec by tasks.registering {
  val versions = k8sVersions.map { version ->
    K8sVersion(
      inputUrl = URI("https://raw.githubusercontent.com/kubernetes/kubernetes/$version/api/openapi-spec/swagger.json").toURL(),
      outputFile = file("$downloadsDir/swagger-$version.json")
    )
  }
  inputs.property("k8sInputUrls", versions.map { it.inputUrl.toString() })
  outputs.files(versions.map { it.outputFile })

  onlyIf { versions.any { !it.outputFile.exists() } }

  doLast {
    for (version in versions) {
      val text = version.inputUrl.readText()
      // retain top-level properties `definitions` and `info`, remove the rest
      val index = text.indexOf("\"title\": \"Kubernetes\"")
      if (index == -1) throw GradleException("Failed to truncate Open API Spec.")
      val index2 = text.indexOf('}', index)
      if (index2 == -1) throw GradleException("Failed to truncate Open API Spec.")
      val truncated = text.substring(0, index2 + 1) + "\n}"
      version.outputFile.writeText(truncated)
    }
  }
}

fun cyan(text: String) = "\u001B[36m$text\u001B[0m"

val generateTemplates by tasks.registering(JavaExec::class) {
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
    listOf(outputDir.path, downloadsDir) + k8sVersions
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
    licenseHeader(
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
