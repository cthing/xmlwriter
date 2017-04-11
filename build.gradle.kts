import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.FindBugsExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.external.javadoc.JavadocOutputLevel
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.dom.*
import kotlinx.dom.build.*

// This project is consumed by infrastructure bootstrap code. Therefore it does not use any
// C Thing Software Gradle plugins and is in the org.cthing domain so it can be consumed as
// a third party dependency.

fun isOnCIServer(): Boolean = System.getenv("CTHING_CI") != null

fun isSnapshot(): Boolean = property("buildType") == "snapshot"


buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx.dom:0.0.10")
    }
}

apply {
    plugin("java")
    plugin("checkstyle")
    plugin("findbugs")
    plugin("jacoco")
    plugin("maven-publish")
    plugin("signing")
}

val buildNumber = if (isOnCIServer()) System.currentTimeMillis().toString() else "0"
val semver = property("semanticVersion")
version = if (isSnapshot()) "$semver-$buildNumber" else semver
group = "org.cthing"
description = "A simple yet highly configurable XML writing library."

dependencies {
    testCompile("junit:junit:4.12")
    testCompile("org.assertj:assertj-core:3.6.2")
}

tasks.withType(JavaCompile::class.java) {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

tasks.withType(Jar::class.java) {
    manifest.attributes(mapOf("Implementation-Title" to project.name,
                              "Implementation-Vendor" to "C Thing Software",
                              "Implementation-Version" to project.version))
}

tasks.withType(Javadoc::class.java) {
    val opts = options as StandardJavadocDocletOptions
    opts.breakIterator(false)
    opts.encoding("UTF-8")
    opts.bottom("Copyright &copy; ${SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())} C Thing Software. All rights reserved.")
    opts.memberLevel = JavadocMemberLevel.PUBLIC
    opts.outputLevel = JavadocOutputLevel.QUIET
}

configure<CheckstyleExtension> {
    toolVersion = "7.6.1"
    isIgnoreFailures = false
    configFile = project.file("dev/checkstyle/checkstyle.xml")
    configProperties.put("config_loc", project.file("dev/checkstyle"))
    isShowViolations = true
}

configure<FindBugsExtension> {
    toolVersion = "3.0.1"
    isIgnoreFailures = false
    effort = "max"
    reportLevel = "medium"
    excludeFilter = project.file("dev/findbugs/suppressions.xml")
    sourceSets = listOf(convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main"))
}

configure<JacocoPluginExtension> {
    toolVersion = "0.7.9"
}

(tasks["jacocoTestReport"] as JacocoReport).apply {
    dependsOn("test")
    with (reports) {
        xml.isEnabled = false
        csv.isEnabled = false
        html.isEnabled = true
        html.setDestination(File(buildDir, "reports/jacoco"))
    }
}

tasks["test"].extensions.getByType(JacocoTaskExtension::class.java).isAppend = false

task<Jar>("sourceJar") {
    from(project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main").allJava)
    classifier = "sources"
}

task<Jar>("javadocJar") {
    from("javadoc")
    classifier = "javadoc"
}

fun canSign(): Boolean {
    return project.hasProperty("signing.keyId")
            && project.hasProperty("signing.password")
            && project.hasProperty("signing.secretKeyRingFile")
}

if (canSign()) {
    configure<SigningExtension> {
        sign(tasks["jar"],
             tasks["sourceJar"],
             tasks["javadocJar"])
    }

    task<Sign>("signPom")

    tasks.withType(AbstractPublishToMaven::class.java) {
        dependsOn("signJar", "signSourceJar", "signJavadocJar", "signPom")
    }

    extra["pomFile"] = File(buildDir, "${project.name}-$version.pom")
    extra["pomSigFile"] = File(buildDir, "${project.name}-$version.pom.asc")


    class PomSigner : RuleSource() {
        @Mutate
        fun genPomRule(@Path("tasks.generatePomFileForMavenJavaPublication") genPomTask: GenerateMavenPom): Unit {
            genPomTask.setDestination(genPomTask.project.extra["pomFile"])
        }

        @Mutate
        fun signPomRule(@Path("tasks.signPom") signPomTask: Sign,
                           @Path("tasks.generatePomFileForMavenJavaPublication") genPomTask: GenerateMavenPom): Unit {
            val pomFile = signPomTask.project.extra["pomFile"] as File
            val pomSigFile = signPomTask.project.extra["pomSigFile"] as File
            signPomTask.dependsOn(genPomTask)
            signPomTask.inputs.file(pomFile)
            signPomTask.outputs.file(pomSigFile)
            signPomTask.sign(pomFile)
        }
    }

    pluginManager.apply(PomSigner::class.java)
}

configure<PublishingExtension> {
    publications.create<MavenPublication>("mavenJava") {
        from(components.getByName("java"))

        artifact(project.tasks["sourceJar"])
        artifact(project.tasks["javadocJar"])

        if (canSign()) {
            data class SignedArtifact(val files: Set<File>, val classifier: String?, val extension: String)

            val pomSigFile = project.extra["pomSigFile"] as File
            listOf(SignedArtifact((tasks["signJar"] as Sign).signatureFiles.files, null, "jar.asc"),
                   SignedArtifact((tasks["signSourceJar"] as Sign).signatureFiles.files, "sources", "jar.asc"),
                   SignedArtifact((tasks["signJavadocJar"] as Sign).signatureFiles.files, "javadoc", "jar.asc"),
                   SignedArtifact(setOf(pomSigFile), null, "pom.asc")).forEach { (files, clazzifier, ext) ->
                files.forEach { file ->
                    artifact(file) {
                        classifier = clazzifier
                        extension = ext
                    }
                }
            }
        }

        pom.withXml {
            val rootElem = asElement()

            rootElem.addElement("name") { appendText(project.name) }
            rootElem.addElement("description") { appendText(project.description) }
            rootElem.addElement("url") { appendText("https://bitbucket.org/cthing/xmlwriter") }
            rootElem.addElement("licenses") {
                addElement("license") {
                    addElement("name") { appendText("Apache License, Version 2.0") }
                    addElement("url") { appendText("http://www.apache.org/licenses/LICENSE-2.0") }
                }
            }
            rootElem.addElement("developers") {
                addElement("developer") {
                    addElement("id") { appendText("baron") }
                    addElement("name") { appendText("Baron Roberts") }
                    addElement("email") { appendText("baron@cthing.com") }
                    addElement("organization") { appendText("C Thing Software") }
                    addElement("organizationUrl") { appendText("http://www.cthing.com") }
                }
            }
            rootElem.addElement("scm") {
                addElement("connection") { appendText("scm:git:git://bitbucket.org/cthing/xmwriter.git") }
                addElement("developerConnection") { appendText("scm:git:ssh://bitbucket.org:cthing/xmlwriter") }
                addElement("url") { appendText("https://bitbucket.org/cthing/xmlwriter/src") }
            }
        }
    }

    repositories.maven {
        setUrl(if (isSnapshot()) project.property("nexusSnapshotsUrl") else project.property("nexusCandidatesUrl"))
        credentials {
            username = project.properties["nexusUser"].toString()
            password = project.properties["nexusPassword"].toString()
        }
    }
}
