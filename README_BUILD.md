Local build instructions

Prerequisites:
- JDK 17 installed and `JAVA_HOME` set to the JDK installation directory.
- Android SDK installed with platform 36 and build-tools 36.1.0
- Recommended: use Android Studio or install command-line SDK tools and run sdkmanager to install packages.

Quick local build steps:

1) Ensure `ANDROID_SDK_ROOT` points to your Android SDK. Example (Windows PowerShell):

   $env:ANDROID_SDK_ROOT = 'C:\Users\<you>\AppData\Local\Android\Sdk'

2) Generate Gradle wrapper (if missing):

   gradle wrapper

3) Build debug APK:

   ./gradlew assembleDebug

4) Find the APK at:

   app/build/outputs/apk/debug/app-debug.apk

CI option (recommended if local machine runs out of memory):
- Push this repository to GitHub and the `Android CI Build` workflow will run and upload the APK artifact.
- After the workflow completes, download the artifact from the Actions run.
