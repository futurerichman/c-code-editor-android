# 非 Gradle 构建：aapt2 + javac + d8 + zipalign + apksigner
# 用法: powershell -File build.ps1 -SrcDir <源码目录> -OutApk <输出.apk> [-VersionName x] [-VersionCode n]
param(
    [Parameter(Mandatory = $true)][string]$SrcDir,
    [Parameter(Mandatory = $true)][string]$OutApk,
    [string]$VersionName = "",
    [string]$VersionCode = ""
)
$ErrorActionPreference = 'Continue'
$BT  = "C:\Users\星梦\AppData\Local\Android\Sdk\build-tools\36.0.0"
$AJ  = "C:\Users\星梦\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
$JH  = "C:\Android\jdk17"
$localKs = Join-Path $SrcDir "ccode.keystore"
if (Test-Path -LiteralPath $localKs) { $KS = $localKs }
else { $KS = "C:\Users\星梦\Doubao\chats\2026-10-29\new-chat\work\toolchain\ccode.keystore" }
$env:JAVA_HOME = $JH
$env:PATH = "$JH\bin;$env:PATH"

$manifest = Join-Path $SrcDir "src\AndroidManifest.xml"
if ($VersionName -ne "" -or $VersionCode -ne "") {
    $content = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8
    if ($VersionName -ne "") {
        $content = $content -replace 'android:versionName="[^"]*"', "android:versionName=`"$VersionName`""
    }
    if ($VersionCode -ne "") {
        $content = $content -replace 'android:versionCode="[^"]*"', "android:versionCode=`"$VersionCode`""
    }
    [System.IO.File]::WriteAllText($manifest, $content, (New-Object System.Text.UTF8Encoding $false))
}

$parent = Split-Path -Parent $SrcDir
$leaf   = Split-Path -Leaf $SrcDir
$buildDir = [System.IO.Path]::Combine($parent, ".build_" + $leaf)
if (Test-Path -LiteralPath $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $buildDir "gen"), (Join-Path $buildDir "classes"), (Join-Path $buildDir "dex") | Out-Null

Push-Location $SrcDir
try {
    Write-Host "[1/6] aapt2 compile 编译资源"
    & "$BT\aapt2.exe" compile --dir res -o (Join-Path $buildDir "res.zip")
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败 (exit=$LASTEXITCODE)" }

    Write-Host "[2/6] aapt2 link 链接资源+生成 R.java"
    $assetsArg = @()
    if (Test-Path -LiteralPath (Join-Path $SrcDir "assets")) { $assetsArg = @("-A", "assets") }
    & "$BT\aapt2.exe" link -o (Join-Path $buildDir "out.apk") -I $AJ `
        --manifest "src\AndroidManifest.xml" `
        --java (Join-Path $buildDir "gen") `
        --min-sdk-version 26 --target-sdk-version 34 `
        @assetsArg (Join-Path $buildDir "res.zip")
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败 (exit=$LASTEXITCODE)" }

    Write-Host "[3/6] javac 编译 Java 源码"
    $javaFiles = Get-ChildItem -LiteralPath (Join-Path $SrcDir "src\com\ccode\mintest") -Filter *.java | ForEach-Object { $_.FullName }
    & "$JH\bin\javac.exe" --release 8 -nowarn -classpath $AJ -encoding UTF-8 `
        -d (Join-Path $buildDir "classes") (Join-Path $buildDir "gen\com\ccode\mintest\R.java") @javaFiles
    if ($LASTEXITCODE -ne 0) { throw "javac 失败 (exit=$LASTEXITCODE)" }

    Write-Host "[4/6] d8 生成 classes.dex"
    $dexIn = Join-Path $buildDir "classes\com\ccode\mintest"
    Push-Location $dexIn
    try {
        $argfile = Join-Path $dexIn "d8args.txt"
        $clsNames = Get-ChildItem -Filter *.class | ForEach-Object { $_.Name }
        $argLines = @("--release", "--lib", $AJ, "--output", (Join-Path $buildDir "dex")) + $clsNames
        [System.IO.File]::WriteAllLines($argfile, $argLines, (New-Object System.Text.UTF8Encoding $false))
        $d8jar = Join-Path $BT "lib\d8.jar"
        & "$JH\bin\java.exe" -cp $d8jar com.android.tools.r8.D8 "@$argfile"
        if ($LASTEXITCODE -ne 0) { throw "d8 失败 (exit=$LASTEXITCODE)" }
    } finally { Pop-Location }

    Write-Host "[5/6] 把 classes.dex 写入 APK"
    & python -c "import zipfile,sys; a=zipfile.ZipFile(r'$(Join-Path $buildDir "out.apk")','a'); a.write(r'$(Join-Path $buildDir "dex\classes.dex")','classes.dex'); a.close()"
    if ($LASTEXITCODE -ne 0) { throw "写入 dex 失败 (exit=$LASTEXITCODE)" }

    Write-Host "[6/6] zipalign + apksigner 签名"
    & "$BT\zipalign.exe" -f 4 (Join-Path $buildDir "out.apk") (Join-Path $buildDir "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "zipalign 失败 (exit=$LASTEXITCODE)" }
    & "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:ccode123 --out $OutApk (Join-Path $buildDir "aligned.apk")
    if ($LASTEXITCODE -ne 0) { throw "apksigner 失败 (exit=$LASTEXITCODE)" }
    & "$BT\apksigner.bat" verify --print-certs $OutApk | Select-Object -First 2
    Write-Host "构建完成: $OutApk"
    Get-Item -LiteralPath $OutApk | Select-Object Name, Length
} catch {
    Write-Host "构建失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
