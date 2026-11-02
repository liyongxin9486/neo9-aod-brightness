$ErrorActionPreference='Stop'
$w='C:/Users/7v/Documents/Codex/2026-11-02/nih/work/aod-build'
New-Item -ItemType Directory -Force -Path "$w/classes-guard", "$w/dex-guard" | Out-Null
$sources=@(Get-ChildItem "$w/src" -Recurse -Filter '*.java' | ForEach-Object FullName)
& 'D:/software/anndroid studio/jbr/bin/javac.exe' --release 11 -encoding UTF-8 -cp "$w/xposed-api-82.jar;D:/software/software_data/AndroidSDK/platforms/android-36.1/android.jar" -d "$w/classes-guard" $sources
if($LASTEXITCODE -ne 0){throw 'javac'}
$files=@(Get-ChildItem "$w/classes-guard" -Recurse -Filter '*.class' | ForEach-Object FullName)
$env:JAVA_HOME='D:/software/anndroid studio/jbr'
& 'D:/software/software_data/AndroidSDK/build-tools/36.0.0/d8.bat' --lib 'D:/software/software_data/AndroidSDK/platforms/android-36.1/android.jar' --classpath "$w/xposed-api-82.jar" --output "$w/dex-guard" $files
if($LASTEXITCODE -ne 0){throw 'd8'}
& 'D:/software/software_data/AndroidSDK/build-tools/36.0.0/aapt.exe' package -f -M "$w/AndroidManifest.xml" -I 'D:/software/software_data/AndroidSDK/platforms/android-36.1/android.jar' -A "$w/assets" -S "$w/res" -F "$w/unsigned.apk"
& 'D:/software/anndroid studio/jbr/bin/jar.exe' uf "$w/unsigned.apk" -C "$w/dex-guard" classes.dex
& 'D:/software/software_data/AndroidSDK/build-tools/36.0.0/zipalign.exe' -f 4 "$w/unsigned.apk" "$w/aligned.apk"
& 'D:/software/software_data/AndroidSDK/build-tools/36.0.0/apksigner.bat' sign --ks "$w/aod-test.p12" --ks-pass pass:aod-local-build --out 'C:/Users/7v/Documents/Codex/2026-11-02/nih/outputs/Neo9-AOD-typeguard.apk' "$w/aligned.apk"
& 'D:/software/software_data/AndroidSDK/build-tools/36.0.0/apksigner.bat' verify 'C:/Users/7v/Documents/Codex/2026-11-02/nih/outputs/Neo9-AOD-typeguard.apk'
