# Triggers the Yahoo price import on a running backend.
#
# Usage (from the backend folder, with the backend running):
#   powershell -ExecutionPolicy Bypass -File scripts\import-prices.ps1
#
# On first use it creates a random ADMIN_TOKEN in backend/.env. Restart the backend
# after that so it picks up the token, then run this script again.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env")
)

if (-not (Test-Path $EnvFile)) {
    Write-Error "$EnvFile not found. Create it first (see scripts\neon-to-env.ps1)."
    exit 1
}

$line = Get-Content $EnvFile | Where-Object { $_ -match '^ADMIN_TOKEN=.+' } | Select-Object -First 1
if (-not $line) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $token = ([Convert]::ToBase64String($bytes)) -replace '[+/=]', ''
    # Ensure the file ends with a newline before appending.
    $raw = [System.IO.File]::ReadAllText((Resolve-Path $EnvFile))
    $prefix = if ($raw.Length -gt 0 -and -not $raw.EndsWith("`n")) { "`n" } else { "" }
    [System.IO.File]::AppendAllText((Resolve-Path $EnvFile), "${prefix}ADMIN_TOKEN=$token`n", (New-Object System.Text.UTF8Encoding $false))
    Write-Host "Created ADMIN_TOKEN in $EnvFile."
    Write-Host "Restart the backend so it reads the token, then run this script again."
    exit 0
}
$token = $line.Substring("ADMIN_TOKEN=".Length).Trim()

Write-Host "Importing prices from Yahoo via $BaseUrl ... (first run backfills ~10 years; can take a few minutes)"
try {
    $result = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/import/prices" `
        -Headers @{ "X-Admin-Token" = $token } -TimeoutSec 1800
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 403) {
        Write-Error "Backend rejected the token. Restart the backend so it reads ADMIN_TOKEN from .env."
    } else {
        Write-Error "Import request failed: $($_.Exception.Message)"
    }
    exit 1
}

if ($result.alreadyRunning) {
    Write-Host "An import is already running. Try again when it finishes."
    exit 0
}
Write-Host ("Done: {0}/{1} stocks OK, {2} failed, {3} rows written, checked through {4}." -f `
    $result.stocksOk, $result.stocks, $result.stocksFailed, $result.rowsWritten, $result.upToDate)
foreach ($failure in $result.failures) { Write-Warning $failure }
