param(
    [switch]$SupabaseStatus,
    [switch]$SupabaseReset
)

$ErrorActionPreference = "Stop"

& "$PSScriptRoot\..\mvnw.cmd" verify
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if ($SupabaseStatus) {
    & npm --prefix "$PSScriptRoot\.." run supabase:status
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($SupabaseReset) {
    & npm --prefix "$PSScriptRoot\.." run supabase:reset
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
