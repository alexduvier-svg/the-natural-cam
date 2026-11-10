param(
    [string]$ApkPath = '',
    [string]$DeviceSerial = $env:NATURAL_CAM_ADB_SERIAL
)
$ErrorActionPreference = 'Stop'
$codexRoot = Split-Path -Parent $PSScriptRoot
if (!$ApkPath) { $ApkPath = Join-Path $codexRoot 'ClaraCam\app\build\outputs\apk\debug\app-debug.apk' }
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$adbExe = Join-Path $codexRoot '.android-sdk\platform-tools\adb.exe'
$aaptExe = Join-Path $codexRoot '.android-sdk\build-tools\36.0.0\aapt2.exe'
$renoSerial = $DeviceSerial
if (!$renoSerial) {
    $attached = @(& $adbExe devices | ForEach-Object { if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] } })
    if ($LASTEXITCODE -ne 0 -or $attached.Count -ne 1) {
        throw 'Informe -DeviceSerial ou NATURAL_CAM_ADB_SERIAL quando não houver exatamente um aparelho conectado.'
    }
    $renoSerial = $attached[0]
}
$expectedPackage = 'br.com.duvier.thenaturalcam.codex'
$badging = & $aaptExe dump badging $ApkPath
if ($LASTEXITCODE -ne 0 -or !($badging | Select-String -SimpleMatch "package: name='$expectedPackage'")) {
    throw 'APK não pertence ao aplicativo Codex; instalação recusada.'
}
$model = (& $adbExe -s $renoSerial shell getprop ro.product.model) -join ''
if ($model.Trim() -ne 'CPH2863') { throw 'Reno 16 Pro não está conectado pelo ADB esperado.' }
Write-Output ('Instalando Codex no Reno · SHA256 ' + (Get-FileHash -LiteralPath $ApkPath).Hash)
# Push + pm install avoids an external APK-opening/installer UI; never uninstalls or erases app data.
$remoteApk = '/data/local/tmp/the-natural-cam-codex-update.apk'
& $adbExe -s $renoSerial push $ApkPath $remoteApk
if ($LASTEXITCODE -ne 0) { throw 'Falha na transferência do APK.' }
$install = & $adbExe -s $renoSerial shell pm install -r -g --user 0 $remoteApk
$install | Write-Output
if ($LASTEXITCODE -ne 0 -or !($install -match '^Success')) { throw 'Android não confirmou a instalação; dados existentes preservados.' }
& $adbExe -s $renoSerial shell dumpsys package $expectedPackage | Select-String -Pattern 'versionCode=|versionName=' | Select-Object -First 2
