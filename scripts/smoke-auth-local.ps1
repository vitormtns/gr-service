[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

function Get-HttpStatus {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [hashtable]$Headers = @{}
    )

    try {
        Invoke-WebRequest -Uri $Uri -Method Get -Headers $Headers -UseBasicParsing | Out-Null
        return 200
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

$stdoutLog = New-TemporaryFile
$stderrLog = New-TemporaryFile
$apiProcess = $null
$workspacePath = (Resolve-Path ".").Path
$initialApiProcessIds = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object {
            $_.CommandLine -and $_.CommandLine.Contains($workspacePath) `
                    -and $_.CommandLine -match "gr-service-.*\.jar"
        } |
        Select-Object -ExpandProperty ProcessId)

try {
    $ErrorActionPreference = "Continue"
    $rawStatus = (& .\node_modules\.bin\supabase.cmd status --output json 2>&1) | Out-String
    $supabaseStatusExitCode = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($supabaseStatusExitCode -ne 0) {
        throw "A stack local do Supabase não está saudável."
    }
    $jsonStart = $rawStatus.IndexOf("{")
    $jsonEnd = $rawStatus.LastIndexOf("}")
    if ($jsonStart -lt 0 -or $jsonEnd -le $jsonStart) {
        throw "O status estruturado do Supabase local está indisponível."
    }
    $supabase = $rawStatus.Substring($jsonStart, $jsonEnd - $jsonStart + 1) | ConvertFrom-Json

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    $listener.Stop()

    $jarFiles = @(Get-ChildItem -Path ".\target" -Filter "gr-service-*.jar" -File |
            Where-Object { $_.Name -notlike "*.original" })
    if ($jarFiles.Count -ne 1) {
        throw "Execute mvnw verify antes do smoke test para gerar um único JAR da aplicação."
    }
    $jar = $jarFiles[0].FullName
    $jarArgument = '"' + $jar + '"'
    $apiProcess = Start-Process -FilePath "java.exe" `
        -ArgumentList @("-jar", $jarArgument, "--spring.profiles.active=local", "--server.port=$port") `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog.FullName `
        -RedirectStandardError $stderrLog.FullName `
        -PassThru

    $healthUri = "http://127.0.0.1:$port/actuator/health"
    $deadline = (Get-Date).AddSeconds(45)
    $healthy = $false
    do {
        if ($apiProcess.HasExited) {
            break
        }
        try {
            $health = Invoke-RestMethod -Uri $healthUri -Method Get
            if ($health.status -eq "UP") {
                $healthy = $true
                break
            }
        } catch {
            # A API ainda está inicializando.
        }
        Start-Sleep -Milliseconds 800
    } while ((Get-Date) -lt $deadline)

    if (-not $healthy) {
        $safeLogTail = @(
            Get-Content -LiteralPath $stdoutLog.FullName -Tail 30 -ErrorAction SilentlyContinue
            Get-Content -LiteralPath $stderrLog.FullName -Tail 30 -ErrorAction SilentlyContinue
        ) -join [Environment]::NewLine
        throw "A API local não iniciou para o smoke test. Logs: $safeLogTail"
    }

    $email = "smoke-$([guid]::NewGuid().ToString('N'))@example.test"
    $password = "$([guid]::NewGuid().ToString('N'))Aa1#"
    $signupBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $signup = Invoke-RestMethod `
        -Uri "$($supabase.API_URL)/auth/v1/signup" `
        -Method Post `
        -Headers @{ apikey = $supabase.PUBLISHABLE_KEY } `
        -ContentType "application/json" `
        -Body $signupBody

    $accessToken = $signup.access_token
    if (-not $accessToken) {
        throw "O Auth local não retornou access token para o usuário temporário."
    }

    $segments = $accessToken.Split(".")
    $headerSegment = $segments[0].Replace("-", "+").Replace("_", "/")
    while ($headerSegment.Length % 4) { $headerSegment += "=" }
    $payloadSegment = $segments[1].Replace("-", "+").Replace("_", "/")
    while ($payloadSegment.Length % 4) { $payloadSegment += "=" }
    $jwtHeader = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($headerSegment)) | ConvertFrom-Json
    $jwtPayload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payloadSegment)) | ConvertFrom-Json

    $meUri = "http://127.0.0.1:$port/api/v1/me"
    $me = Invoke-RestMethod -Uri $meUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $missingTokenStatus = Get-HttpStatus -Uri $meUri
    $lastCharacter = $accessToken[$accessToken.Length - 1]
    $replacement = if ($lastCharacter -eq "A") { "B" } else { "A" }
    $alteredToken = $accessToken.Substring(0, $accessToken.Length - 1) + $replacement
    $alteredTokenStatus = Get-HttpStatus -Uri $meUri -Headers @{ Authorization = "Bearer $alteredToken" }

    $apiLogs = (Get-Content -LiteralPath $stdoutLog.FullName -Raw -ErrorAction SilentlyContinue) `
            + (Get-Content -LiteralPath $stderrLog.FullName -Raw -ErrorAction SilentlyContinue)
    $jwks = Invoke-RestMethod -Uri "$($supabase.API_URL)/auth/v1/.well-known/jwks.json" -Method Get
    $requiredClaims = @("sub", "iss", "exp", "iat")
    $presentClaims = @($requiredClaims | Where-Object { $null -ne $jwtPayload.$_ })

    [pscustomobject]@{
        SupabaseAuth = "local"
        Algorithm = $jwtHeader.alg
        Issuer = $jwtPayload.iss
        RequiredClaimsPresent = $presentClaims.Count -eq $requiredClaims.Count
        JwksKeyCount = @($jwks.keys).Count
        ValidTokenStatus = 200
        SubjectMatches = $me.userId -eq $signup.user.id -and $me.userId -eq $jwtPayload.sub
        MissingTokenStatus = $missingTokenStatus
        AlteredTokenStatus = $alteredTokenStatus
        RawTokenFoundInApiLog = $apiLogs.Contains($accessToken)
        EmailFoundInApiLog = $apiLogs.Contains($email)
    }
} finally {
    if ($apiProcess -and -not $apiProcess.HasExited) {
        Stop-Process -Id $apiProcess.Id -Force
        Wait-Process -Id $apiProcess.Id -Timeout 10 -ErrorAction SilentlyContinue
    }
    $newApiProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
            Where-Object {
                $_.CommandLine -and $_.CommandLine.Contains($workspacePath) `
                        -and $_.CommandLine -match "gr-service-.*\.jar" `
                        -and $_.ProcessId -notin $initialApiProcessIds
            })
    foreach ($process in $newApiProcesses) {
        Stop-Process -Id $process.ProcessId -Force
        Wait-Process -Id $process.ProcessId -Timeout 10 -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $stdoutLog.FullName -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stderrLog.FullName -Force -ErrorAction SilentlyContinue
}
