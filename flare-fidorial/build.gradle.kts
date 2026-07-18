import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.regex.Pattern

repositories {
    maven("https://repo.euphyllia.moe/repository/maven-public/")
}

dependencies {
    implementation(projects.flareCommon)
    implementation(libs.oshi.core)
    compileOnly(libs.fidorial.api)
}

tasks {
    jar {
        archiveClassifier.set("dev")
    }
    shadowJar {
        archiveClassifier.set("")
        configureRelocation()
    }
}

fun ShadowJar.configureRelocation() {
    val prefix = "co.technove.flareplatform.libs"
    mapOf(
        "oshi" to "oshi",
        "co.technove.flare." to "flare",
    ).forEach { pack ->
        relocate(pack.key, "$prefix.${pack.value}")
    }
    rename(Pattern.compile("^oshi.*"), $$"$$prefix.$0")
}
