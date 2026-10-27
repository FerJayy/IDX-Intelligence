# Triggers an import on a running backend.
#
# Usage (from the backend folder, with the backend running):
#   powershell -ExecutionPolicy Bypass -File scripts\import.ps1 stocks    # refresh the IDX stock list (~20 requests)
#   powershell -ExecutionPolicy Bypass -File scripts\import.ps1 prices    # Yahoo daily prices
#   powershell -ExecutionPolicy Bypass -File scripts\import.ps1 screener  # IDX Edge daily screener picks (1 request)
#   powershell -ExecutionPolicy Bypass -File scripts\import.ps1 brokers   # IDX Edge broker summaries
#   powershell -ExecutionPolicy Bypass -File scripts\import.ps1 stats     # row counts and storage used
#
# On first use it creates a random ADMIN_TOKEN in backend/.env. Restart the backend
# after that so it picks up the token, then run this script again.
param(
    [Parameter(Position = 0)][ValidateSet("stocks", "prices", "screener", "brokers", "stats")][string]$Job = "prices",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$EnvFile
)
# Set here, not in param(): Windows PowerShell 5.1 leaves $PSScriptRoot empty in param defaults of advanced scripts.
if (-not $EnvFile) { $EnvFile = Join-Path $PSScriptRoot "..\.env" }

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

$headers = @{ "X-Admin-Token" = $token }
try {
    switch ($Job) {
        "stocks" {
            Write-Host "Refreshing the IDX stock list from IDX Edge via $BaseUrl ..."
            $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/import/stocks" -Headers $headers -TimeoutSec 600
            if ($r.skipped) { Write-Host "Skipped: $($r.note)"; exit 0 }
            Write-Host ("Done: {0}" -f $r.note)
        }
        "prices" {
            Write-Host "Importing prices from Yahoo via $BaseUrl ... (new stocks get 5 years of history: the first full run can take up to an hour)"
            $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/import/prices" -Headers $headers -TimeoutSec 7200
            if ($r.alreadyRunning) { Write-Host "An import is already running. Try again when it finishes."; exit 0 }
            Write-Host ("Done: {0}/{1} stocks OK, {2} failed, {3} rows written, checked through {4}." -f `
                $r.stocksOk, $r.stocks, $r.stocksFailed, $r.rowsWritten, $r.upToDate)
            if ($r.stocksNotFound -or $r.stocksSkipped) {
                Write-Host ("Yahoo has no data for {0} stock(s) (retried weekly); {1} skipped today for that reason." -f `
                    $r.stocksNotFound, $r.stocksSkipped)
            }
            foreach ($failure in $r.failures) { Write-Warning $failure }
        }
        "screener" {
            $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/import/screener" -Headers $headers -TimeoutSec 120
            if ($r.skipped) { Write-Host "Skipped: $($r.note)"; exit 0 }
            Write-Host ("Done: {0}" -f $r.note)
        }
        "brokers" {
            Write-Host "Importing broker summaries from IDX Edge via $BaseUrl ... (up to ~800 requests; can take several minutes)"
            $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/import/brokers" -Headers $headers -TimeoutSec 3600
            if ($r.skipped) { Write-Host "Skipped: $($r.note)"; exit 0 }
            Write-Host ("Done: {0} requests ({1} failed), {2} rows written, days {3} to {4}." -f `
                $r.calls, $r.failed, $r.rowsWritten, $r.oldestDay, $r.newestDay)
            Write-Host ("Stopped because: {0}. Quota left today: {1}. Still to fetch: {2} stock-days." -f `
                $r.note, $r.remainingQuota, $r.stillPending)
        }
        "stats" {
            $r = Invoke-RestMethod -Uri "$BaseUrl/api/admin/stats" -Headers $headers
            $r.tables | Format-Table -AutoSize
            Write-Host ("Database size: {0} MB of Neon's 512 MB free tier." -f $r.databaseMegabytes)
        }
    }
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 403) {
        Write-Error "Backend rejected the token. Restart the backend so it reads ADMIN_TOKEN from .env."
    } else {
        Write-Error "Request failed: $($_.Exception.Message)"
    }
    exit 1
}
