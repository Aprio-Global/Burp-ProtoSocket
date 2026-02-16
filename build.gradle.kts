plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.10")

    // Protobuf Java for dynamic decoding (bundled in JAR)
    implementation("com.google.protobuf:protobuf-java:3.25.1")

    // JSON library for formatting (bundled in JAR)
    implementation("org.json:json:20231013")

    // RSyntaxTextArea for JSON syntax highlighting (bundled in JAR)
    implementation("com.fifesoft:rsyntaxtextarea:3.3.4")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("com.google.protobuf:protobuf-java:3.25.1")
    testImplementation("org.json:json:20231013")
    testImplementation("net.portswigger.burp.extensions:montoya-api:2025.10")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().filter { it.isDirectory })
    from(configurations.runtimeClasspath.get().filterNot { it.isDirectory }.map { zipTree(it) })

    // Exclude signature files to avoid conflicts
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}