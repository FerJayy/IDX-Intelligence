# Converts a Neon connection string into backend/.env.
#
# Usage (from the backend folder):
#   powershell -ExecutionPolicy Bypass -File scripts\neon-to-env.ps1
# Then paste the string Neon shows under "Connect", e.g.
#   postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require&channel_binding=require
param(
    [string]$ConnectionString,
    [string]$OutFile = (Join-Path $PSScriptRoot "..\.env")
)

if (-not $ConnectionString) {
    $ConnectionString = Read-Host "Paste the Neon connection string"
}
$ConnectionString = $ConnectionString.Trim().Trim("'", '"')

if ($ConnectionString -notmatch '^postgres(ql)?://') {
    Write-Error "That doesn't look like a Neon connection string (it should start with postgresql://)."
    exit 1
}

$uri = [System.Uri]$ConnectionString
$userInfo = $uri.UserInfo -split ':', 2
if ($userInfo.Count -ne 2 -or -not $userInfo[1]) {
    Write-Error "No password found. In Neon, reveal or copy the password before copying the string."
    exit 1
}
$username = [System.Uri]::UnescapeDataString($userInfo[0])
$password = [System.Uri]::UnescapeDataString($userInfo[1])
$database = $uri.AbsolutePath.TrimStart('/')
$hostPart = if ($uri.IsDefaultPort -or $uri.Port -lt 0) { $uri.Host } else { "$($uri.Host):$($uri.Port)" }

if ($uri.Host -like '*-pooler*') {
    Write-Warning "This is a pooled host (-pooler). Turn off 'Connection pooling' in Neon's Connect dialog and copy again."
}

if ((Test-Path $OutFile) -and (Get-Content $OutFile -Raw)) {
    $answer = Read-Host "$OutFile already has content. Overwrite? (y/N)"
    if ($answer -ne 'y') { Write-Host "Nothing changed."; exit 0 }
}

$content = @"
DB_URL=jdbc:postgresql://$hostPart/$database`?sslmode=require
DB_USERNAME=$username
DB_PASSWORD=$password
"@
# UTF-8 without BOM, so Spring reads the first key correctly.
[System.IO.File]::WriteAllText((Resolve-Path -LiteralPath (Split-Path $OutFile)).Path + "\" + (Split-Path $OutFile -Leaf), $content + "`n", (New-Object System.Text.UTF8Encoding $false))

Write-Host "Wrote $OutFile"
Write-Host "  DB_URL      = jdbc:postgresql://$hostPart/$database`?sslmode=require"
Write-Host "  DB_USERNAME = $username"
Write-Host "  DB_PASSWORD = (hidden, $($password.Length) characters)"
