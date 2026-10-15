plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pl.bbtv.tv"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { applicationId = "pl.bbtv.tv"; minSdk = 21; targetSdk = 28; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // Private sideloaded TV app; retaining API 28 behavior is intentional, not a Play Store release.
    lint { disable += "ExpiredTargetSdkVersion" }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation(project(":protocol"))
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
}
