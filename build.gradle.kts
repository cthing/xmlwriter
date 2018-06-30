import com.github.spotbugs.SpotBugsTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// This project is consumed by infrastructure bootstrap code. Therefore it does not use any
// C Thing Software Gradle plugins and is in the org.cthing domain so it can be consumed as
// a third party dependency.

plugins {
    id("java")
    id("checkstyle")
    id("jacoco")
    id("maven-publish")
    id("signing")
    id("com.github.spotbugs").version("1.6.2")
}

val isCIServer = System.getenv("CTHING_CI") != null
val isSnapshot = property("org.cthing.build.type") == "snapshot"
val buildNumber = if (isCIServer) System.currentTimeMillis().toString() else "0"
val semver = property("org.cthing.version")
version = if (isSnapshot) "$semver-$buildNumber" else this.semver!!
group = property("org.cthing.group") as String
description = property("org.cthing.description") as String

dependencies {
    testCompile("org.junit.jupiter:junit-jupiter-api:5.2.0")
    testCompile("org.junit.jupiter:junit-jupiter-params:5.2.0")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:5.2.0")
    testCompileOnly("org.apiguardian:apiguardian-api:1.0.0")
    testCompile("org.assertj:assertj-core:3.10.0")

    spotbugsPlugins("com.mebigfatguy.fb-contrib:fb-contrib:7.4.2.sb")
}

checkstyle {
    toolVersion = "8.10.1"
    isIgnoreFailures = false
    configFile = project.file("dev/checkstyle/checkstyle.xml")
    configDir = project.file("dev/checkstyle")
    isShowViolations = true
}

spotbugs {
    toolVersion = "3.1.5"
    isIgnoreFailures = false
    effort = "max"
    reportLevel = "medium"
    excludeFilter = project.file("dev/spotbugs/suppressions.xml")
    sourceSets = listOf(java.sourceSets["main"])
}

jacoco {
    toolVersion = "0.8.1"
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.compilerArgs = listOf("-Xlint:all", "-Xlint:-options", "-Werror")
    }

    withType<Jar> {
        manifest.attributes(mapOf("Implementation-Title" to project.name,
                                  "Implementation-Vendor" to project.property("org.cthing.organization.name"),
                                  "Implementation-Version" to project.version))
    }

    withType<Javadoc> {
        with(options as StandardJavadocDocletOptions) {
            breakIterator(false)
            encoding("UTF-8")
            bottom("Copyright &copy; ${SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())} ${project.property("org.cthing.organization.name")}. All rights reserved.")
            memberLevel = JavadocMemberLevel.PUBLIC
            outputLevel = JavadocOutputLevel.QUIET
        }
    }

    withType<SpotBugsTask> {
        with(reports) {
            xml.isEnabled = false
            html.isEnabled = true
        }
    }

    withType<JacocoReport> {
        dependsOn("test")
        with(reports) {
            xml.isEnabled = false
            csv.isEnabled = false
            html.isEnabled = true
            html.destination = File(buildDir, "reports/jacoco")
        }
    }

    withType<Test> {
        useJUnitPlatform()

        configure<JacocoTaskExtension> {
            isAppend = false
        }
    }
}

val sourceJar by tasks.creating(Jar::class) {
    from(java.sourceSets["main"].allSource)
    classifier = "sources"
}

val javadocJar by tasks.creating(Jar::class) {
    from("javadoc")
    classifier = "javadoc"
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])

        artifact(sourceJar)
        artifact(javadocJar)

        pom {
            name.set(project.name)
            description.set(project.description)
            url.set("https://bitbucket.org/cthing/${project.name}")
            licenses {
                license {
                    name.set(property("org.cthing.license.name") as String)
                    url.set(property("org.cthing.license.url") as String)
                }
            }
            developers {
                developer {
                    id.set(property("org.cthing.developer.id") as String)
                    name.set(property("org.cthing.developer.name") as String)
                    email.set("${property("org.cthing.developer.id")}@cthing.com")
                    organization.set(property("org.cthing.organization.name") as String)
                    organizationUrl.set(property("org.cthing.organization.url") as String)
                }
            }
            scm {
                connection.set("scm:git:git://bitbucket.org/cthing/${project.name}.git")
                developerConnection.set("scm:git:ssh://bitbucket.org:cthing/${project.name}")
                url.set("https://bitbucket.org/cthing/${project.name}/src")
            }
        }
    }

    val repoUrl = if (isSnapshot) property("nexusSnapshotsUrl") else property("nexusCandidatesUrl")
    if (repoUrl != null) {
        repositories.maven {
            setUrl(repoUrl)
            credentials {
                username = property("nexusUser") as String
                password = property("nexusPassword") as String
            }
        }
    }
}

if (hasProperty("signing.keyId") && hasProperty("signing.password") && hasProperty("signing.secretKeyRingFile")) {
    signing {
        sign(publishing.publications["mavenJava"])
    }
}
