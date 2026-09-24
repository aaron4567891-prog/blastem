param(
    [Parameter(Mandatory = $true)][string]$Ndk,
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../..").Path
$output = Join-Path $repo 'android/build/native-tests/segacd_interrupt_test'
$remote = '/data/local/tmp/blastem-segacd-interrupt-test'
New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null
Push-Location $repo
try {
    & "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe" `
        --target=aarch64-linux-android26 -DNEW_CORE -DNEW_Z80 -DIS_LIB `
        -ffunction-sections -fdata-sections -Wno-unused-value -Wno-pointer-sign `
        -I . android/tests/segacd_interrupt_test.c lc8951.c '-Wl,--gc-sections' -o $output
    if ($LASTEXITCODE) { throw 'Test compilation failed' }
    & $Adb -s $Serial push $output $remote
    if ($LASTEXITCODE) { throw 'Test upload failed' }
    & $Adb -s $Serial shell chmod 755 $remote
    if ($LASTEXITCODE) { throw 'Cannot make test executable' }
    & $Adb -s $Serial shell $remote
    if ($LASTEXITCODE) { throw 'Sega CD interrupt regression test failed' }
} finally {
    & $Adb -s $Serial shell rm -f $remote
    Pop-Location
}
