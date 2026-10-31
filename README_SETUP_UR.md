# EssSal Accounting ERP — Android Studio project

یہ Android Studio کا ابتدائی project ہے۔ یہ WebView کے اندر accounting app چلاتا ہے، اس لیے vouchers اور accounts موبائل کے local WebView storage میں رہتے ہیں۔ اوپر Gmail Login، Backup اور Restore کے native controls موجود ہیں۔

## APK بنانے سے پہلے لازمی Google setup
1. Google Cloud Console میں project بنائیں۔
2. Google Drive API enable کریں۔
3. OAuth consent screen configure کریں۔
4. Android OAuth client بنائیں: package name `com.esssal.accounting` اور اپنی app signing key کا SHA-1 درج کریں۔
5. OAuth consent میں Drive `drive.appdata` scope استعمال ہوگا۔ Testing کے لیے اپنے Gmail کو test user شامل کریں۔
6. Android Studio میں یہ project کھولیں، Gradle sync کریں، پھر Build > Build APK(s) کریں۔

## اہم
- اس workspace میں Android SDK/Gradle build environment موجود نہیں، اس لیے یہاں سے compiled APK نہیں بنایا گیا؛ یہ source project ہے۔
- Gmail sign-in اور Drive API کی پہلی authorization کے لیے internet ضروری ہے۔
- Backup Google Drive کے hidden `appDataFolder` میں اسی signed-in Google account کے تحت محفوظ ہوتا ہے۔
- Restore موجودہ local accounts/vouchers کو backup سے replace کرتا ہے؛ restore سے پہلے الگ backup بنا لیں۔
- یہ ابتدائی build ہے، production استعمال سے پہلے Google OAuth configuration، device testing، backup versioning/encryption اور error handling کی مکمل جانچ ضروری ہے۔
- Data local app storage میں رہتا ہے؛ app uninstall یا app data clear کرنے سے local data حذف ہو سکتا ہے، اس لیے باقاعدہ Drive backup کریں۔

## صرف موبائل سے APK build کرنے کا طریقہ
1. اس ZIP کو GitHub پر ایک نئے repository میں upload کریں (Chrome میں GitHub کی Desktop site استعمال کر سکتے ہیں)۔
2. Repository میں Actions tab کھولیں۔
3. `Build EssSal Android APK` workflow منتخب کریں اور `Run workflow` دبائیں۔
4. Build مکمل ہونے پر workflow کے Artifacts میں `EssSal-Accounting-Android-Debug` download کریں؛ ZIP کے اندر `app-debug.apk` ہوگا۔
5. پہلی بار Google Drive backup استعمال کرنے سے پہلے Android OAuth client بنانا ہوگا۔ Build logs میں signing report سے SHA-1 fingerprint حاصل کریں، پھر Google Cloud Console میں Android OAuth client کے package `com.esssal.accounting` اور اسی SHA-1 کے ساتھ بنائیں۔ OAuth setup مکمل ہونے کے بعد اسی app کو دوبارہ build کریں۔

یہ workflow APK خودکار طور پر build کرتا ہے، لیکن Google OAuth client ID کی configuration صارف کے اپنے Google Cloud project میں مکمل کرنا ضروری ہے۔ 
