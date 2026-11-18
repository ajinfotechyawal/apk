# Billing & Ledger - Android App (Kotlin project)

Aapki billing app ab ek asli Android app project hai (Android Studio mein khulta hai).
App ka poora code `app/src/main/assets/index.html` mein hai, aur Android ka hissa
`app/src/main/java/com/ajinfotech/billing/MainActivity.kt` + `assets/bridge.js` mein.

## APK kaise banayein
1. Android Studio (Ladybug ya naya) install karo: https://developer.android.com/studio
2. Studio mein **File > Open** > is folder (`AJ_Billing_Android`) ko chuno. Gradle sync apne aap chalega
   (pehli baar internet chahiye, kuch minute lagte hain).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   APK yahan milegi: `app/build/outputs/apk/debug/app-debug.apk` - ise phone mein copy karke install kar lo.

Command line se: `./gradlew assembleDebug` (Windows par `gradlew.bat assembleDebug`).

## Play Store / apni signed APK
1. Studio mein **Build > Generate Signed App Bundle / APK** se key (keystore) banao, aur use SAMBHAL kar rakho.
2. Ya folder mein `keystore.properties` file banao:
   ```
   storeFile=/path/to/my-release.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
   phir `./gradlew assembleRelease` (APK) ya `./gradlew bundleRelease` (Play Store ke liye AAB).
3. Har nayi release se pehle `app/build.gradle.kts` mein `versionCode` +1 karo.

## App mein badlav karna ho to
Nayi HTML file ko `app/src/main/assets/index.html` ke upar copy kar do, `versionCode` badhao, dobara build karo.
Phone par company ka data (localStorage) update par bhi bacha rehta hai.

## Android mein kya alag se kaam karta hai
- PDF / JPG / Word / Excel / Backup file -> `Downloads/AJ Billing/` mein save hoti hai.
- WhatsApp / Share -> Android ka share sheet (file + text ke saath).
- Print Bill -> Android ka print screen (PDF mein save ya printer).
- Restore, logo, signature ke liye file chunna -> Android file picker.
- Back button: pehle page wapas, phir 2 baar dabane par app band.
- Status bar app ke light/dark mode ke saath badalta hai.

## Dhyan dein
- Pehli baar app **ek baar internet par** kholo, taaki design/icons/fonts phone mein save ho jayein. Uske baad offline chalta hai.
- Android 10 (API 29) ya naya phone chahiye.
- Auto backup (file / Google Drive) tabhi chalta hai jab app khuli ho.
- Folder chunne wala auto-backup option (showDirectoryPicker) Android WebView mein nahi chalta;
  baaki sab (in-app saved backups, Downloads file, Google Drive) chalta hai.

## Android Studio ke bina APK (GitHub se)
1. github.com par naya (private) repository banao aur is folder ki saari files upload karo (`.github` folder samet).
2. Repository mein **Actions > Build APK > Run workflow** dabao (ya upload par apne aap chalega).
3. 3-5 minute baad us run ke andar **Artifacts > AJ-Billing-APK** se `app-debug.apk` download karo aur phone mein install karo.
