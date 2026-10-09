# Makes this PC trust Exhale's own signing certificate (the "CN=ozyern" one build-installer.ps1 signs with),
# for the current user only. Windows asks once to confirm adding it to Trusted Root Certification Authorities.
#
#   powershell -ExecutionPolicy Bypass -File desktop\scripts\trust-signing-cert.ps1
#
# This is for your own machine: a self-signed certificate is not something other PCs will trust.

$ErrorActionPreference = 'Stop'
$subject = if ($env:EXHALE_SIGN_SUBJECT) { $env:EXHALE_SIGN_SUBJECT } else { 'CN=ozyern' }
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object { $_.Subject -eq $subject } |
    Sort-Object NotAfter -Descending | Select-Object -First 1
if (-not $cert) { throw "No code-signing certificate with subject $subject in the current user's store." }

$cer = Join-Path $env:TEMP 'exhale-signing.cer'
Export-Certificate -Cert $cert -FilePath $cer -Force | Out-Null
Import-Certificate -FilePath $cer -CertStoreLocation Cert:\CurrentUser\TrustedPublisher | Out-Null
Import-Certificate -FilePath $cer -CertStoreLocation Cert:\CurrentUser\Root | Out-Null
Write-Host "Trusted $($cert.Subject) ($($cert.Thumbprint))."
