rootProject.name = "pkl-k8s"

val javaVersion: JavaVersion = JavaVersion.current()

require(javaVersion.isJava11Compatible) {
  "This project requires Java 11 or higher, but found ${javaVersion.majorVersion}."
}

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}
