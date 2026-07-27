$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$sdkCandidates = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    'C:\Workspace\tools\android-sdk',
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$sdkRoot = $sdkCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw 'Android SDK not found. Install it or set ANDROID_SDK_ROOT.'
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1 -ExpandProperty FullName
$androidJar = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'platforms') -Filter 'android.jar' -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if ([string]::IsNullOrWhiteSpace($buildTools) -or [string]::IsNullOrWhiteSpace($androidJar)) {
    throw 'Android SDK platform or build tools not found.'
}

$javacCommand = Get-Command 'javac.exe' -ErrorAction SilentlyContinue
if ($null -eq $javacCommand) {
    throw 'JDK not found. Install JDK 17 or newer and expose javac on PATH.'
}
$jdkRoot = Split-Path -Parent (Split-Path -Parent $javacCommand.Source)
$env:JAVA_HOME = $jdkRoot
$keystore = Join-Path $projectRoot 'keystore\sms-relay-debug.keystore'
$buildRoot = Join-Path $projectRoot 'build'
$classesRoot = Join-Path $buildRoot 'classes'
$dexRoot = Join-Path $buildRoot 'dex'
$classesJar = Join-Path $buildRoot 'classes.jar'
$unsignedApk = Join-Path $buildRoot 'sms-relay-unsigned.apk'
$alignedApk = Join-Path $buildRoot 'sms-relay-aligned.apk'
$signedApk = Join-Path $buildRoot 'sms-relay-debug.apk'

New-Item -ItemType Directory -Force -Path $buildRoot, $classesRoot, $dexRoot | Out-Null

$aapt2 = Join-Path $buildTools 'aapt2.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$javac = Join-Path $jdkRoot 'bin\javac.exe'
$java = Join-Path $jdkRoot 'bin\java.exe'
$jar = Join-Path $jdkRoot 'bin\jar.exe'
$keytool = Join-Path $jdkRoot 'bin\keytool.exe'
$resourceZip = Join-Path $buildRoot 'resources.zip'

if (-not (Test-Path -LiteralPath $keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystore) | Out-Null
    & $keytool -genkeypair -keystore $keystore -storepass android -keypass android `
        -alias smsrelay -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=M02SmsRelay, OU=Local, O=Local, L=Local, ST=Local, C=XX'
    if ($LASTEXITCODE -ne 0) { throw 'Debug keystore creation failed' }
}

& $aapt2 compile --dir (Join-Path $projectRoot 'res') -o $resourceZip
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
& $aapt2 link -o $unsignedApk -I $androidJar --manifest (Join-Path $projectRoot 'AndroidManifest.xml') $resourceZip --java $buildRoot --min-sdk-version 23 --target-sdk-version 25
if ($LASTEXITCODE -ne 0) { throw 'Resource linking failed' }

$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Filter '*.java' -Recurse |
    Select-Object -ExpandProperty FullName
$generatedFiles = Get-ChildItem -LiteralPath $buildRoot -Filter 'R.java' -Recurse |
    Select-Object -ExpandProperty FullName
$javacArguments = @(
    '-encoding', 'UTF-8',
    '-source', '8',
    '-target', '8',
    '-bootclasspath', $androidJar,
    '-d', $classesRoot
) + $sourceFiles + $generatedFiles
& $javac $javacArguments
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }

Push-Location $classesRoot
try {
    & $jar cf $classesJar '.'
    if ($LASTEXITCODE -ne 0) { throw 'Class archive creation failed' }
} finally {
    Pop-Location
}
& $d8 --min-api 23 --lib $androidJar --output $dexRoot $classesJar
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed' }

Push-Location $dexRoot
try {
    & $jar uf $unsignedApk 'classes.dex'
    if ($LASTEXITCODE -ne 0) { throw 'DEX packaging failed' }
} finally {
    Pop-Location
}

& $zipalign -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $signedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& $apksigner verify --min-sdk-version 23 --verbose $signedApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }

Get-Item -LiteralPath $signedApk
