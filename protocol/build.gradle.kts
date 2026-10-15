plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pl.bbtv.protocol"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 21; testInstrumentationRunner = "android.test.InstrumentationTestRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
