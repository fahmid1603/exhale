# Builds Exhale-Setup-<version>.exe: Exhale's own installer (desktop/installer/ExhaleSetup.cs) with the
# app image packed inside it. Needs nothing beyond Windows itself — the C# compiler and WPF ship with
# .NET Framework 4.8 — plus the JBR SDK the Gradle packaging uses (see desktop/build.gradle.kts).
#
#   powershell -ExecutionPolicy Bypass -File desktop\scripts\build-installer.ps1
#   ... -SkipApp    reuse the app image already in desktop\build\compose\binaries\main\app

param([switch]$SkipApp)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$desktop = Join-Path $root 'desktop'
$out = Join-Path $desktop 'build\installer'
$version = (Select-String -Path "$desktop\build.gradle.kts" -Pattern 'packageVersion = "([^"]+)"').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force $out | Out-Null

# Code signing, when there's something to sign with. Without it every build is unsigned: SmartScreen
# warns, and Windows 11's Smart App Control refuses to run it at all.
#   $env:EXHALE_SIGN_PFX       a .pfx file (and $env:EXHALE_SIGN_PASSWORD, if it has one), signed with signtool
#   otherwise                  a code-signing certificate in the current user's store whose subject matches
#                              $env:EXHALE_SIGN_SUBJECT (default "CN=ozyern"), signed with PowerShell
# A self-signed certificate only helps the PC that trusts it: see desktop\scripts\trust-signing-cert.ps1.
function Get-SigningCert {
    $subject = if ($env:EXHALE_SIGN_SUBJECT) { $env:EXHALE_SIGN_SUBJECT } else { 'CN=ozyern' }
    Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert -ErrorAction SilentlyContinue |
        Where-Object { $_.Subject -eq $subject -and $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } |
        Sort-Object NotAfter -Descending | Select-Object -First 1
}

function Invoke-Sign {
    param([string[]]$Paths)
    $pfx = $env:EXHALE_SIGN_PFX
    if ($pfx) {
        if (-not (Test-Path $pfx)) { Write-Host "EXHALE_SIGN_PFX is set but $pfx isn't there; leaving the build unsigned."; return }
        $tool = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if (-not $tool) { Write-Host "No signtool.exe (install the Windows SDK); leaving the build unsigned."; return }
        foreach ($path in $Paths) {
            $signArgs = @('sign', '/fd', 'SHA256', '/f', $pfx)
            if ($env:EXHALE_SIGN_PASSWORD) { $signArgs += @('/p', $env:EXHALE_SIGN_PASSWORD) }
            # A timestamp keeps the signature valid after the certificate itself expires.
            $signArgs += @('/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', $path)
            & $tool.FullName @signArgs
            if ($LASTEXITCODE -ne 0) { throw "Signing failed for $path" }
        }
        return
    }
    $cert = Get-SigningCert
    if (-not $cert) { Write-Host "No signing certificate; leaving the build unsigned."; return }
    foreach ($path in $Paths) {
        # jpackage leaves its launcher read-only.
        Set-ItemProperty -LiteralPath $path -Name IsReadOnly -Value $false
        $result = Set-AuthenticodeSignature -FilePath $path -Certificate $cert -HashAlgorithm SHA256 -TimestampServer 'http://timestamp.digicert.com' -ErrorAction SilentlyContinue
        # No network for the timestamp is no reason to ship unsigned.
        if (-not $result -or -not $result.SignerCertificate) {
            $result = Set-AuthenticodeSignature -FilePath $path -Certificate $cert -HashAlgorithm SHA256
        }
        if (-not $result.SignerCertificate) { throw "Signing failed for $path : $($result.StatusMessage)" }
    }
}

# Every native file the app loads. Smart App Control judges each executable and library, not just the
# installer, so one unsigned runtime DLL is enough to stop the app starting.
function Invoke-SignTree {
    param([string]$Root)
    $todo = Get-ChildItem $Root -Recurse -File | Where-Object { $_.Extension -in '.exe', '.dll' } |
        Where-Object { (Get-AuthenticodeSignature $_.FullName).Status -eq 'NotSigned' } | ForEach-Object FullName
    if ($todo) {
        Write-Host ("Signing {0} native files..." -f @($todo).Count)
        Invoke-Sign @($todo)
    }
}

if (-not $SkipApp) {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
    & "$root\gradlew.bat" -p $root :desktop:createDistributable --console=plain
    if ($LASTEXITCODE -ne 0) { throw "createDistributable failed" }
}
$image = Join-Path $desktop 'build\compose\binaries\main\app\Exhale'
if (-not (Test-Path "$image\Exhale.exe")) { throw "No app image at $image" }
# A class-data archive left by a test launch belongs to that machine; don't ship it.
Remove-Item "$image\app\exhale.jsa" -ErrorAction SilentlyContinue

# Signed before they go into the payload, so the installed app is signed too.
Invoke-SignTree $image

Write-Host "Packing the app image..."
$zip = Join-Path $out 'payload.zip'
Remove-Item $zip -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($image, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)

Set-Content -Encoding UTF8 "$out\Version.cs" "namespace ExhaleSetup { static partial class Product { public const string Version = `"$version`"; } }"

$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
$csc = "$fw\csc.exe"
$refs = @(
    "$fw\WPF\PresentationFramework.dll", "$fw\WPF\PresentationCore.dll", "$fw\WPF\WindowsBase.dll",
    "$fw\System.Xaml.dll", "$fw\System.IO.Compression.dll", "$fw\System.IO.Compression.FileSystem.dll",
    "$fw\System.Windows.Forms.dll", "$fw\Microsoft.CSharp.dll", "$fw\System.Core.dll"
) | ForEach-Object { "/r:$_" }
$common = @(
    '/nologo', '/target:winexe', '/optimize+', '/platform:anycpu', '/utf8output',
    "/win32icon:$desktop\icons\exhale.ico",
    "/win32manifest:$desktop\installer\app.manifest",
    "/resource:$desktop\src\main\resources\exhale.png,exhale.png",
    "/resource:$desktop\src\main\resources\font\linotte.ttf,linotte.ttf"
)
$sources = @("$desktop\installer\ExhaleSetup.cs", "$out\Version.cs")

Write-Host "Compiling the uninstaller..."
$uninstaller = Join-Path $out 'Uninstall Exhale.exe'
& $csc @common @refs '/define:UNINSTALLER' "/out:$uninstaller" @sources
if ($LASTEXITCODE -ne 0) { throw "Uninstaller didn't compile" }
Invoke-Sign @($uninstaller)

Write-Host "Compiling setup..."
$setup = Join-Path $out "Exhale-Setup-$version.exe"
& $csc @common @refs "/resource:$uninstaller,uninstaller.exe" "/resource:$zip,payload.zip" "/out:$setup" @sources
if ($LASTEXITCODE -ne 0) { throw "Setup didn't compile" }
Invoke-Sign @($setup)

Write-Host ("Built {0} ({1:N0} MB)" -f $setup, ((Get-Item $setup).Length / 1MB))
