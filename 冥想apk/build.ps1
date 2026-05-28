$tools = "C:\apk工具"
$env:JAVA_HOME = "$tools\jdk"
$env:ANDROID_SDK_ROOT = "$tools\android-sdk"
$env:PATH = "$tools\jdk\bin;$env:PATH"

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradle = "$tools\gradle-8.14.3\bin\gradle.bat"

Write-Host "Building..."
& $gradle -p "$project\android" assembleDebug *>&1 | Tee-Object -FilePath "$project\build.log"

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem "$project\android\app\build\outputs\apk\debug\app-debug.apk"
    Copy-Item $apk.FullName "$project\冥想.apk" -Force
    Write-Host "DONE - $($apk.LastWriteTime)"
} else {
    Write-Host "FAILED - check build.log"
}
