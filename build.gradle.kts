plugins {
    id("java")
    id("application")
    id("org.javamodularity.moduleplugin").version("1.8.12")
    id("org.openjfx.javafxplugin").version("0.0.14")
}

group = "com.hedera.consim"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjfx:javafx-controls:23.0.1")
    implementation("org.openjfx:javafx-fxml:23.0.1")
    implementation("eu.hansolo:tilesfx:21.0.9") {
        exclude(group = "org.openjfx")
    }
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

javafx {
    version = "23.0.1"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

application {
    mainClass.set("com.hashgraph.dynamicthrottles.ui.DynamicThrottlesUiApp")
}