param(
    [double[]]$Zooms = @(0.6, 1, 2, 3.5, 6, 7, 8),
    [int]$FocusX = 540,
    [int]$FocusY = 1080,
    [string]$DeviceSerial = $env:NATURAL_CAM_ADB_SERIAL
)
$ErrorActionPreference = 'Stop'
$codexRoot = Split-Path -Parent $PSScriptRoot
$adbExe = Join-Path $codexRoot '.android-sdk\platform-tools\adb.exe'
$renoSerial = $DeviceSerial
if (!$renoSerial) {
    $attached = @(& $adbExe devices | ForEach-Object { if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] } })
    if ($LASTEXITCODE -ne 0 -or $attached.Count -ne 1) {
        throw 'Informe -DeviceSerial ou NATURAL_CAM_ADB_SERIAL quando não houver exatamente um aparelho conectado.'
    }
    $renoSerial = $attached[0]
}
$model = (& $adbExe -s $renoSerial shell getprop ro.product.model) -join ''
if ($LASTEXITCODE -ne 0 -or $model.Trim() -ne 'CPH2863') { throw 'O aparelho selecionado não é o Reno esperado.' }
$output = Join-Path $codexRoot 'diagnostico_codex\comparacao_lentes'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$size = & $adbExe -s $renoSerial shell wm size
if ($size -notmatch '1080x2344') { throw 'Screen layout changed; inspect before using coordinates.' }
$positions = @{'0.6'=86; '1'=246; '2'=392; '3.5'=555; '6'=718; '7'=872; '8'=1015}
$summary = @()
function Assert-CodexForeground {
    $callState = (& $adbExe -s $renoSerial shell dumpsys telephony.registry | Select-String -Pattern '\bmCallState=') -join ' '
    if ($LASTEXITCODE -ne 0 -or !$callState -or $callState -match '\bmCallState=[12]\b') {
        throw 'Ligação ativa ou estado indisponível. Teste de câmera interrompido.'
    }
    # On this Android build the focus summary belongs to dumpsys window, not its windows subcommand.
    $focus = (& $adbExe -s $renoSerial shell dumpsys window | Select-String -Pattern 'mCurrentFocus=') -join ' '
    if ($focus -notmatch 'br\.com\.duvier\.thenaturalcam\.codex/') {
        throw 'Codex não está recebendo os toques. Teste interrompido para preservar a atividade atual do celular.'
    }
}
function Send-CodexTap([int]$X, [int]$Y) {
    Assert-CodexForeground
    & $adbExe -s $renoSerial shell input tap $X $Y
    if ($LASTEXITCODE -ne 0) { throw 'Falha no comando de toque.' }
}
foreach ($zoom in $Zooms) {
    $key = $zoom.ToString('0.0############', [Globalization.CultureInfo]::InvariantCulture).TrimEnd('0').TrimEnd('.')
    if (!$positions.ContainsKey($key)) { throw "Unknown zoom: $zoom" }
    $before = @(& $adbExe -s $renoSerial shell ls /sdcard/Documents/TheNaturalCamCodex)
    Send-CodexTap $positions[$key] 1720
    Start-Sleep -Milliseconds 1000
    Send-CodexTap $FocusX $FocusY
    Start-Sleep -Milliseconds 2200
    Assert-CodexForeground
    & $adbExe -s $renoSerial shell screencap -p /data/local/tmp/codex-lens-preview.png
    & $adbExe -s $renoSerial pull /data/local/tmp/codex-lens-preview.png (Join-Path $output ($key + 'x_previa.png')) | Out-Null
    Send-CodexTap 540 1930
    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        Start-Sleep -Milliseconds 1000
        $after = @(& $adbExe -s $renoSerial shell ls /sdcard/Documents/TheNaturalCamCodex)
        $new = @($after | Where-Object { $_ -notin $before -and $_ -like '*_ficha.json' })
    } while ($new.Count -eq 0 -and $timer.Elapsed.TotalSeconds -lt 40)
    if ($new.Count -eq 0) { throw "No completed photo at $zoom x in 40 s; inspect the app." }
    foreach ($name in $new) {
        $json = (& $adbExe -s $renoSerial shell cat ('/sdcard/Documents/TheNaturalCamCodex/' + $name)) -join "`n"
        $sheet = $json | ConvertFrom-Json
        [IO.File]::WriteAllText((Join-Path $output ($key + 'x_' + $name)), $json, [Text.UTF8Encoding]::new($false))
        $photo = $name.Replace('_ficha.json', '.jpg')
        & $adbExe -s $renoSerial pull ('/sdcard/DCIM/TheNaturalCamCodex/' + $photo) (Join-Path $output ($key + 'x_' + $photo)) | Out-Null
        $row = [ordered]@{requested=$zoom; actual_zoom=$sheet.zoom; camera=$sheet.camera_id; physical=$sheet.lente_fisica_ativa; af=$sheet.estado_af; preview_focus=$sheet.foco_previa_confirmado; iso=$sheet.iso; exposure_ns=$sheet.exposicao_ns; width=$sheet.largura_px; height=$sheet.altura_px; bytes=$sheet.arquivo_jpeg_bytes; fusion_ms=$sheet.tempo_fusao_ms; quality=$sheet.verificador_qualidade; photo=$photo; app=$sheet.app}
        $summary += $row
        $row | ConvertTo-Json -Depth 5 -Compress | Write-Output
    }
    Start-Sleep -Milliseconds 800
}
[IO.File]::WriteAllText((Join-Path $output 'comparacao.json'), ($summary | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
