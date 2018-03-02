import com.github.spotbugs.SpotBugsTask
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

plugins {
    id("java")
    id("checkstyle")
    id("jacoco")
    id("maven-publish")
    id("signing")
    id("com.github.spotbugs").version("1.6.1")
}

val buildNumber = if (isOnCIServer()) System.currentTimeMillis().toString() else "0"
val semver = property("semanticVersion")
version = if (isSnapshot()) "$semver-$buildNumber" else this.semver!!
group = "org.cthing"
description = "A simple yet highly configurable XML writing library."

dependencies {
    testCompile("org.junit.jupiter:junit-jupiter-api:5.1.0")
    testCompile("org.junit.jupiter:junit-jupiter-params:5.1.0")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.1.0")
    testCompileOnly("org.apiguardian:apiguardian-api:1.0.0")
    testCompile("org.assertj:assertj-core:3.9.1")

    spotbugsPlugins("com.mebigfatguy.fb-contrib:fb-contrib:7.2.0.sb")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
}

tasks.withType<Jar> {
    manifest.attributes(mapOf("Implementation-Title" to project.name,
                              "Implementation-Vendor" to "C Thing Software",
                              "Implementation-Version" to project.version))
}

tasks.withType<Javadoc> {
    with (options as StandardJavadocDocletOptions) {
        breakIterator(false)
        encoding("UTF-8")
        bottom("Copyright &copy; ${SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())} C Thing Software. All rights reserved.")
        memberLevel = JavadocMemberLevel.PUBLIC
        outputLevel = JavadocOutputLevel.QUIET
    }
}

checkstyle {
    toolVersion = "8.8"
    isIgnoreFailures = false
    configFile = project.file("dev/checkstyle/checkstyle.xml")
    configDir = project.file("dev/checkstyle")
    isShowViolations = true
}

spotbugs {
    toolVersion = "3.1.2"
    isIgnoreFailures = false
    effort = "max"
    reportLevel = "medium"
    excludeFilter = project.file("dev/spotbugs/suppressions.xml")
    sourceSets = listOf(convention.getPlugin<JavaPluginConvention>().sourceSets["main"])
}

tasks.withType<SpotBugsTask> {
    with (reports) {
        xml.isEnabled = false
        html.isEnabled = true
    }
}

jacoco {
    toolVersion = "0.8.0"
}

(tasks["jacocoTestReport"] as JacocoReport).apply {
    dependsOn("test")
    with (reports) {
        xml.isEnabled = false
        csv.isEnabled = false
        html.isEnabled = true
        html.destination = File(buildDir, "reports/jacoco")
    }
}

(tasks["test"] as Test).apply {
    useJUnitPlatform()

    extensions.getByType(JacocoTaskExtension::class.java).isAppend = false
}

val sourceJar by tasks.creating(Jar::class) {
    from(project.convention.getPlugin<JavaPluginConvention>().sourceSets["main"].allJava)
    classifier = "sources"
}

val javadocJar by tasks.creating(Jar::class) {
    from("javadoc")
    classifier = "javadoc"
}


class PomSigner : RuleSource() {
    @Mutate
    fun genPomRule(@Path("tasks.generatePomFileForMavenJavaPublication") genPomTask: GenerateMavenPom) {
        genPomTask.setDestination(genPomTask.project.extra["pomFile"])
    }

    @Mutate
    fun signPomRule(@Path("tasks.signPom") signPomTask: Sign,
                    @Path("tasks.generatePomFileForMavenJavaPublication") genPomTask: GenerateMavenPom) {
        val pomFile = signPomTask.project.extra["pomFile"] as File
        val pomSigFile = signPomTask.project.extra["pomSigFile"] as File
        signPomTask.dependsOn(genPomTask)
        signPomTask.inputs.file(pomFile)
        signPomTask.outputs.file(pomSigFile)
        signPomTask.sign(pomFile)
    }
}

data class SignedArtifact(val files: Set<File>, val classifier: String?, val extension: String)


fun canSign(): Boolean {
    return project.hasProperty("signing.keyId")
            && project.hasProperty("signing.password")
            && project.hasProperty("signing.secretKeyRingFile")
}

if (canSign()) {
    signing {
        sign(tasks["jar"], sourceJar, javadocJar)
    }

    task<Sign>("signPom")

    tasks.withType<AbstractPublishToMaven> {
        dependsOn("signJar", "signSourceJar", "signJavadocJar", "signPom")
    }

    extra["pomFile"] = File(buildDir, "${project.name}-$version.pom")
    extra["pomSigFile"] = File(buildDir, "${project.name}-$version.pom.asc")

    pluginManager.apply(PomSigner::class.java)
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])

        artifact(sourceJar)
        artifact(javadocJar)

        if (canSign()) {
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
            rootElem.addElement("description") { appendText(project.description!!) }
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

    val repoUrl = if (isSnapshot()) project.property("nexusSnapshotsUrl") else project.property("nexusCandidatesUrl")
    if (repoUrl != null) {
        repositories.maven {
            setUrl(repoUrl)
            credentials {
                username = project.properties["nexusUser"].toString()
                password = project.properties["nexusPassword"].toString()
            }
        }
    }
}
