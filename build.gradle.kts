import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.Confidence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// This project is consumed by infrastructure bootstrap code. Therefore it does not use any
// C Thing Software Gradle plugins and is in the org.cthing domain so it can be consumed as
// a third party dependency.

plugins {
    `java-library`
    checkstyle
    jacoco
    `maven-publish`
    signing
    id("com.github.spotbugs") version "4.3.0"
}

val isCIServer = System.getenv("CTHING_CI") != null
val isSnapshot = property("cthing.build.type") == "snapshot"
val buildNumber = if (isCIServer) System.currentTimeMillis().toString() else "0"
val semver = property("cthing.version")
version = if (isSnapshot) "$semver-$buildNumber" else this.semver!!
group = property("cthing.group") as String
description = property("cthing.description") as String

dependencies {
    api("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.6.2")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testCompileOnly("org.apiguardian:apiguardian-api:1.0.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")

    spotbugsPlugins("com.mebigfatguy.sb-contrib:sb-contrib:7.4.7")
}

checkstyle {
    toolVersion = "8.33"
    isIgnoreFailures = false
    configFile = file("dev/checkstyle/checkstyle.xml")
    configDirectory.set(file("dev/checkstyle"))
    isShowViolations = true
}

spotbugs {
    toolVersion.set("4.0.3")
    ignoreFailures.set(false)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.MEDIUM)
    excludeFilter.set(file("dev/spotbugs/suppressions.xml"))
}

jacoco {
    toolVersion = "0.8.5"
}

tasks {
    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("--release", "11", "-Xlint:all", "-Xlint:-options", "-Werror"))
    }

    withType<Jar>().configureEach {
        manifest.attributes(mapOf("Implementation-Title" to project.name,
                                  "Implementation-Vendor" to project.property("cthing.organization.name"),
                                  "Implementation-Version" to project.version))
    }

    withType<Javadoc>().configureEach {
        with(options as StandardJavadocDocletOptions) {
            breakIterator(false)
            encoding("UTF-8")
            bottom("Copyright &copy; ${SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())} ${project.property("cthing.organization.name")}. All rights reserved.")
            memberLevel = JavadocMemberLevel.PUBLIC
            outputLevel = JavadocOutputLevel.QUIET
        }
    }

    spotbugsMain {
        reports.create("html").isEnabled = true
    }

    spotbugsTest {
        isEnabled = false
    }

    withType<JacocoReport>().configureEach {
        dependsOn("test")
        with(reports) {
            xml.isEnabled = false
            csv.isEnabled = false
            html.isEnabled = true
            html.destination = File(buildDir, "reports/jacoco")
        }
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    withType<GenerateModuleMetadata> {
        enabled = false
    }
}

val sourceJar by tasks.registering(Jar::class) {
    from(project.sourceSets["main"].allSource)
    archiveClassifier.set("sources")
}

val javadocJar by tasks.registering(Jar::class) {
    from("javadoc")
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        register("jar", MavenPublication::class) {
            from(components["java"])

            artifact(sourceJar.get())
            artifact(javadocJar.get())

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/baron1405/${project.name}")
                licenses {
                    license {
                        name.set(property("cthing.license.name") as String)
                        url.set(property("cthing.license.url") as String)
                    }
                }
                developers {
                    developer {
                        id.set(property("cthing.developer.id") as String)
                        name.set(property("cthing.developer.name") as String)
                        email.set("${property("cthing.developer.id")}@cthing.com")
                        organization.set(property("cthing.organization.name") as String)
                        organizationUrl.set(property("cthing.organization.url") as String)
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/baron1405/${project.name}.git")
                    developerConnection.set("scm:git:ssh://github.com:baron1405/${project.name}")
                    url.set("https://github.com/baron1405/${project.name}/src")
                }
            }
        }
    }

    val repoUrl = if (isSnapshot) property("cthing.nexus.snapshotsUrl") else property("cthing.nexus.candidatesUrl")
    if (repoUrl != null) {
        repositories {
            maven {
                name = "CThingMaven"
                setUrl(repoUrl)
                credentials {
                    username = property("cthing.nexus.user") as String
                    password = property("cthing.nexus.password") as String
                }
            }
        }
    }
}

if (hasProperty("signing.keyId") && hasProperty("signing.password") && hasProperty("signing.secretKeyRingFile")) {
    signing {
        sign(publishing.publications["jar"])
    }
}
