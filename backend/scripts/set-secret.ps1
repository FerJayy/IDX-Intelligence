# Stores a secret in backend/.env without showing it on screen.
#
# Usage (from the backend folder):
#   powershell -ExecutionPolicy Bypass -File scripts\set-secret.ps1 IDX_EDGE_API_KEY
# Then paste the value when asked (it stays hidden) and press Enter.
# If the name already exists in .env, its value is replaced.
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Z][A-Z0-9_]*$')][string]$Name,
    [string]$EnvFile
)
# Set here, not in param(): Windows PowerShell 5.1 leaves $PSScriptRoot empty in param defaults of advanced scripts.
if (-not $EnvFile) { $EnvFile = Join-Path $PSScriptRoot "..\.env" }

$secure = Read-Host "Paste the value for $Name (input is hidden)" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim()
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if (-not $value) {
    Write-Error "Nothing was pasted; .env not changed."
    exit 1
}

$lines = @()
if (Test-Path $EnvFile) { $lines = @(Get-Content $EnvFile) }
$replaced = $false
# @(...) keeps this an array even when .env has a single line.
$lines = @($lines | ForEach-Object {
    if ($_ -match "^$Name=") { $replaced = $true; "$Name=$value" } else { $_ }
})
if (-not $replaced) { $lines += "$Name=$value" }

$path = if (Test-Path $EnvFile) { (Resolve-Path $EnvFile).Path } else { [IO.Path]::GetFullPath($EnvFile) }
[IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
Write-Host ("{0} {1} in {2} ({3} characters)." -f ($(if ($replaced) { "Updated" } else { "Added" })), $Name, $path, $value.Length)
