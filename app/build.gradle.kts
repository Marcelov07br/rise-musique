import java.util.Properties
plugins {
  id("com.android.application"); id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose"); id("app.cash.paparazzi")
}
// Local secrets (gitignored secrets.properties or env vars).
val secrets = Properties().also { p -> rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { p.load(it) } }
android {
  namespace = "com.rise.musique"; compileSdk = 35
  defaultConfig { applicationId = "com.rise.musique.gpt"; minSdk = 26; targetSdk = 35; versionCode = 19; versionName = "0.16.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Local secrets (gitignored secrets.properties or env vars); empty = feature shows "não configurado".
    fun secret(k: String) = (secrets.getProperty(k) ?: System.getenv(k) ?: "").replace("\\", "").replace("\"", "")
    buildConfigField("String", "LASTFM_API_KEY", "\"${secret("LASTFM_API_KEY")}\"")
    buildConfigField("String", "LASTFM_API_SECRET", "\"${secret("LASTFM_API_SECRET")}\"") }
  signingConfigs {
    create("stable") {
      System.getenv("RISE_KEYSTORE")?.let { storeFile = file(it) }
      storePassword = System.getenv("RISE_STORE_PASSWORD")
      keyAlias = System.getenv("RISE_KEY_ALIAS")
      keyPassword = System.getenv("RISE_KEY_PASSWORD")
    }
  }
  buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("stable") } }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
  packaging { jniLibs { useLegacyPackaging = true } }
  defaultConfig { ndk { abiFilters += if (project.hasProperty("rise.emulator")) listOf("x86_64") else listOf("arm64-v8a") } }
}
dependencies {
  val bom = platform("androidx.compose:compose-bom:2024.12.01")
  implementation(bom)
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
  implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.documentfile:documentfile:1.0.1")
  implementation("io.coil-kt:coil-compose:2.7.0")
  implementation("androidx.profileinstaller:profileinstaller:1.4.1")
  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-session:1.5.1")
  implementation("androidx.media3:media3-ui:1.5.1")
  implementation("androidx.media3:media3-database:1.5.1")
  implementation("androidx.palette:palette-ktx:1.0.0")
  implementation("androidx.glance:glance-appwidget:1.1.1")
  implementation("androidx.work:work-runtime-ktx:2.10.0")
  implementation("androidx.core:core-splashscreen:1.0.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("androidx.test.ext:junit:1.2.1")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

