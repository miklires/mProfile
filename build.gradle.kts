plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "io.github.miklires"
version = "1.1.0"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("com.h2database:h2:2.3.232")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.3.232")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    jar { archiveClassifier.set("plain") }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mProfile-${project.version}.jar")
        relocate("org.bstats", "io.github.miklires.mprofile.libs.bstats")
    }
    build { dependsOn(shadowJar) }
    processResources { filesMatching("plugin.yml") { expand("version" to project.version) } }
    test { useJUnitPlatform() }
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
    versionNumber.set(project.version.toString())
    versionName.set("mProfile ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.add("26.2")
    loaders.addAll("paper", "purpur", "folia")
    changelog.set(provider { file("CHANGELOG.md").readText() })
    syncBodyFrom.set(file("README.md").readText())
}
