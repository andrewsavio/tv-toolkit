# Builds build\app.apk with the Android SDK's own tools (no Gradle).
$ErrorActionPreference = 'Stop'
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\34.0.0"
$jar  = "$sdk\platforms\android-34\android.jar"
$here = $PSScriptRoot
$out  = "$here\build"

Remove-Item "$out\classes", "$out\classes.dex", "$out\unsigned.apk", "$out\app.apk" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes" | Out-Null

function Run { & $args[0] $args[1..($args.Length - 1)]; if ($LASTEXITCODE) { throw "failed: $($args[0])" } }

Run "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
Run "$bt\aapt2.exe" link -I $jar --manifest "$here\AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 -o "$out\unsigned.apk" "$out\res.zip"
$srcs = @(Get-ChildItem "$here\src" -Recurse -Filter *.java | ForEach-Object FullName)
Run javac --release 8 -classpath $jar -d "$out\classes" @srcs
$classes = @(Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName)
Run java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib $jar --output $out @classes
Run jar uf "$out\unsigned.apk" -C $out classes.dex
Remove-Item "$out\aligned.apk" -Force -ErrorAction SilentlyContinue
Run "$bt\zipalign.exe" -f 4 "$out\unsigned.apk" "$out\aligned.apk"   # Android 11+ needs resources.arsc aligned

if (-not (Test-Path "$out\debug.keystore")) {
    Run keytool -genkeypair -keystore "$out\debug.keystore" -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Aurora Home"
}
Run java -jar "$bt\lib\apksigner.jar" sign --ks "$out\debug.keystore" --ks-pass pass:android `
    --key-pass pass:android --ks-key-alias androiddebugkey --out "$out\app.apk" "$out\aligned.apk"
"Built: $out\app.apk ($((Get-Item "$out\app.apk").Length) bytes)"
