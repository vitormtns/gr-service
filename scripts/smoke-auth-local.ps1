[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

function Get-HttpStatus {
    param(
        [Parameter(Mandatory)]
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Method = "Get",
        [string]$Body = $null
    )

    try {
        $arguments = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true }
        $supportsRequestBody = $Method.ToUpperInvariant() -in @("POST", "PUT", "PATCH")
        if ($supportsRequestBody -and $PSBoundParameters.ContainsKey("Body") -and $null -ne $Body) {
            $arguments.ContentType = "application/json"
            $arguments.Body = $Body
        }
        $response = Invoke-WebRequest @arguments
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function Get-SafeHttpFailure {
    param(
        [Parameter(Mandatory)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    $response = $ErrorRecord.Exception.Response
    $status = if ($response) { [int]$response.StatusCode } else { $null }
    $requestId = if ($response) { $response.Headers["X-Request-Id"] } else { $null }
    $cacheControl = if ($response) { $response.Headers["Cache-Control"] } else { $null }
    $body = $ErrorRecord.ErrorDetails.Message
    if (-not $body -and $response -and $response.GetResponseStream) {
        $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
        try {
            $body = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    }
    $code = $null
    if ($body) {
        try {
            $code = ($body | ConvertFrom-Json).code
        } catch {
            $code = $null
        }
    }

    return [pscustomobject]@{
        Status = $status
        RequestId = $requestId
        CacheControl = $cacheControl
        Code = $code
        Body = $body
    }
}

function Invoke-HttpCheck {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Method = "Get",
        [string]$Body = $null
    )
    try {
        $arguments = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true }
        $supportsRequestBody = $Method.ToUpperInvariant() -in @("POST", "PUT", "PATCH")
        if ($supportsRequestBody -and $PSBoundParameters.ContainsKey("Body") -and $null -ne $Body) {
            $arguments.ContentType = "application/json"
            $arguments.Body = $Body
        }
        $response = Invoke-WebRequest @arguments
        return [pscustomobject]@{ Status = [int]$response.StatusCode; CacheControl = $response.Headers["Cache-Control"]; Code = $null; RequestId = $response.Headers["X-Request-Id"]; Content = $response.Content }
    } catch {
        $failure = Get-SafeHttpFailure -ErrorRecord $_
        return [pscustomobject]@{ Status = $failure.Status; CacheControl = $failure.CacheControl; Code = $failure.Code; RequestId = $failure.RequestId; Content = $failure.Body }
    }
}

function Assert-HttpStatus {
    param(
        [Parameter(Mandatory)][string]$Step,
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][int]$ExpectedStatus,
        [Parameter(Mandatory)]$Result
    )

    if ($Result.Status -ne $ExpectedStatus) {
        $publicCode = if ($Result.Code) { $Result.Code } else { "ausente" }
        $requestId = if ($Result.RequestId) { $Result.RequestId } else { "ausente" }
        throw "Etapa '$Step' falhou: método=$Method; status esperado=$ExpectedStatus; status recebido=$($Result.Status); code=$publicCode; requestId=$requestId."
    }
}

function Protect-DiagnosticFile {
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [string[]]$SensitiveValues = @()
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $content = Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) {
        $content = ""
    }
    $content = Protect-DiagnosticText -Content $content -SensitiveValues $SensitiveValues
    Set-Content -LiteralPath $Path -Value $content -NoNewline
}

function Protect-DiagnosticText {
    param(
        [AllowNull()]
        [string]$Content,
        [string[]]$SensitiveValues = @()
    )

    if ($null -eq $Content) {
        return ""
    }
    foreach ($value in $SensitiveValues) {
        if ($value) {
            $Content = $Content.Replace([string]$value, "[PROTEGIDO]")
        }
    }
    return $Content
}

function Get-JavaExecutable {
    $javaCommand = (Get-Command "java.exe" -ErrorAction Stop).Source
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $javaSettings = (& $javaCommand -XshowSettings:properties -version 2>&1) | Out-String
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $javaHomeMatch = [regex]::Match(
            $javaSettings,
            "(?m)^\s*java\.home\s*=\s*(.+?)\s*$")
    if (-not $javaHomeMatch.Success) {
        throw "Não foi possível identificar o executável real do Java."
    }

    $javaExecutable = Join-Path $javaHomeMatch.Groups[1].Value "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
        throw "O executável real do Java não foi encontrado."
    }
    return $javaExecutable
}

$stdoutLog = New-TemporaryFile
$stderrLog = New-TemporaryFile
$apiProcess = $null
$databaseContainer = $null
$supabaseHealthy = $false
$runtimeRoleCreated = $false
$preserveDiagnostics = $false
$primaryFailure = $null
$cleanupFailures = [System.Collections.Generic.List[string]]::new()
$runtimeUsername = "app_smoke_runtime"
$runtimePassword = "smoke_$([guid]::NewGuid().ToString('N'))"
$databaseEnvironment = @("DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD")
$authenticationEnvironment = @(
    "SUPABASE_AUTH_MODE",
    "SUPABASE_AUTH_ALGORITHM",
    "SUPABASE_AUTH_ISSUER",
    "SUPABASE_AUTH_JWKS_URI",
    "SUPABASE_AUTH_JWT_SECRET"
)
$managedEnvironment = $databaseEnvironment + $authenticationEnvironment
$previousEnvironment = @{}
foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}
$email = $null
$password = $null
$accessToken = $null
$supabase = $null

try {
    $ErrorActionPreference = "Continue"
    $rawStatus = (& .\node_modules\.bin\supabase.cmd status --output json 2>&1) | Out-String
    $supabaseStatusExitCode = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($supabaseStatusExitCode -ne 0) {
        throw "A stack local do Supabase não está saudável."
    }
    $supabaseHealthy = $true
    $jsonStart = $rawStatus.IndexOf("{")
    $jsonEnd = $rawStatus.LastIndexOf("}")
    if ($jsonStart -lt 0 -or $jsonEnd -le $jsonStart) {
        throw "O status estruturado do Supabase local está indisponível."
    }
    $supabase = $rawStatus.Substring($jsonStart, $jsonEnd - $jsonStart + 1) | ConvertFrom-Json

    $databaseContainers = @(& docker ps --filter "name=supabase_db_gr-service" --format "{{.ID}}")
    if ($LASTEXITCODE -ne 0 -or $databaseContainers.Count -ne 1) {
        throw "Não foi possível identificar com segurança o container PostgreSQL local."
    }
    $databaseContainer = $databaseContainers[0].Trim()
    $roleSql = @"
drop role if exists $runtimeUsername;
create role $runtimeUsername login noinherit nosuperuser nocreatedb nocreaterole noreplication nobypassrls password '$runtimePassword';
grant app_api to $runtimeUsername;
"@
    $roleSql | & docker exec -i $databaseContainer psql -v ON_ERROR_STOP=1 -U postgres -d postgres | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível criar o login runtime efêmero."
    }
    $runtimeRoleCreated = $true

    [Environment]::SetEnvironmentVariable(
            "DATABASE_URL", "jdbc:postgresql://127.0.0.1:54322/postgres", "Process")
    [Environment]::SetEnvironmentVariable("DATABASE_USERNAME", $runtimeUsername, "Process")
    [Environment]::SetEnvironmentVariable("DATABASE_PASSWORD", $runtimePassword, "Process")
    [Environment]::SetEnvironmentVariable("SUPABASE_AUTH_MODE", "JWKS", "Process")
    [Environment]::SetEnvironmentVariable("SUPABASE_AUTH_ALGORITHM", "ES256", "Process")
    [Environment]::SetEnvironmentVariable(
            "SUPABASE_AUTH_ISSUER", "$($supabase.API_URL)/auth/v1", "Process")
    [Environment]::SetEnvironmentVariable(
            "SUPABASE_AUTH_JWKS_URI", "$($supabase.API_URL)/auth/v1/.well-known/jwks.json", "Process")
    [Environment]::SetEnvironmentVariable("SUPABASE_AUTH_JWT_SECRET", $null, "Process")

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
    $javaExecutable = Get-JavaExecutable
    $apiProcess = Start-Process -FilePath $javaExecutable `
        -ArgumentList @("-jar", $jarArgument, "--spring.profiles.active=local", "--server.port=$port") `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog.FullName `
        -RedirectStandardError $stderrLog.FullName `
        -PassThru
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }

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
        $safeLogTail = Protect-DiagnosticText -Content $safeLogTail -SensitiveValues @(
            $runtimePassword,
            $supabase.PUBLISHABLE_KEY, $supabase.ANON_KEY, $supabase.SECRET_KEY,
            $supabase.SERVICE_ROLE_KEY, $supabase.JWT_SECRET,
            $supabase.S3_PROTOCOL_ACCESS_KEY_ID, $supabase.S3_PROTOCOL_ACCESS_KEY_SECRET
        )
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
    $meAgain = Invoke-RestMethod -Uri $meUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $organizationId = [guid]::NewGuid().ToString()
    $membershipId = [guid]::NewGuid().ToString()
    $otherUserId = [guid]::NewGuid().ToString()
    $otherOrganizationId = [guid]::NewGuid().ToString()
    $otherMembershipId = [guid]::NewGuid().ToString()
    $otherFarmId = [guid]::NewGuid().ToString()
    $activeFarmId = [guid]::NewGuid().ToString()
    $secondActiveFarmId = [guid]::NewGuid().ToString()
    $inactiveFarmId = [guid]::NewGuid().ToString()
    $archivedFarmId = [guid]::NewGuid().ToString()
    $suspendedMembershipOrganizationId = [guid]::NewGuid().ToString()
    $revokedMembershipOrganizationId = [guid]::NewGuid().ToString()
    $suspendedOrganizationId = [guid]::NewGuid().ToString()
    $archivedOrganizationId = [guid]::NewGuid().ToString()
    $organizationSql = @"
insert into app.organizations (id, name, status)
values ('$organizationId'::uuid, 'Organização smoke', 'ACTIVE');
insert into app.organization_memberships
    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
values
    ('$membershipId'::uuid, '$organizationId'::uuid, '$($signup.user.id)'::uuid, 'OWNER', 'ACTIVE', 'ALL_FARMS');
insert into app.farms (id, tenant_id, name, status) values
    ('$activeFarmId'::uuid, '$organizationId'::uuid, 'Fazenda ativa A', 'ACTIVE'),
    ('$secondActiveFarmId'::uuid, '$organizationId'::uuid, 'Fazenda ativa B', 'ACTIVE'),
    ('$inactiveFarmId'::uuid, '$organizationId'::uuid, 'Fazenda inativa', 'INACTIVE'),
    ('$archivedFarmId'::uuid, '$organizationId'::uuid, 'Fazenda arquivada', 'ARCHIVED');
insert into app.users (id, status)
values ('$otherUserId'::uuid, 'ACTIVE');
insert into app.organizations (id, name, status)
values ('$otherOrganizationId'::uuid, 'Organização de outro usuário', 'ACTIVE');
insert into app.organization_memberships
    (id, tenant_id, user_id, role_key, status, farm_scope_mode)
values
    ('$otherMembershipId'::uuid, '$otherOrganizationId'::uuid, '$otherUserId'::uuid, 'VIEWER', 'ACTIVE', 'SELECTED_FARMS');
insert into app.farms (id, tenant_id, name, status)
values ('$otherFarmId'::uuid, '$otherOrganizationId'::uuid, 'Fazenda de outro usuário', 'ACTIVE');
insert into app.organizations (id, name, status) values
    ('$suspendedMembershipOrganizationId'::uuid, 'Membership suspenso', 'ACTIVE'),
    ('$revokedMembershipOrganizationId'::uuid, 'Membership revogado', 'ACTIVE'),
    ('$suspendedOrganizationId'::uuid, 'Organização suspensa', 'SUSPENDED'),
    ('$archivedOrganizationId'::uuid, 'Organização arquivada', 'ARCHIVED');
insert into app.organization_memberships (id, tenant_id, user_id, role_key, status, farm_scope_mode) values
    ('$([guid]::NewGuid())'::uuid, '$suspendedMembershipOrganizationId'::uuid, '$($signup.user.id)'::uuid, 'VIEWER', 'SUSPENDED', 'ALL_FARMS'),
    ('$([guid]::NewGuid())'::uuid, '$revokedMembershipOrganizationId'::uuid, '$($signup.user.id)'::uuid, 'VIEWER', 'REVOKED', 'ALL_FARMS'),
    ('$([guid]::NewGuid())'::uuid, '$suspendedOrganizationId'::uuid, '$($signup.user.id)'::uuid, 'VIEWER', 'ACTIVE', 'ALL_FARMS'),
    ('$([guid]::NewGuid())'::uuid, '$archivedOrganizationId'::uuid, '$($signup.user.id)'::uuid, 'VIEWER', 'ACTIVE', 'ALL_FARMS');
insert into app.farms (id, tenant_id, name, status) values
    ('$([guid]::NewGuid())'::uuid, '$suspendedMembershipOrganizationId'::uuid, 'Fazenda bloqueada', 'ACTIVE'),
    ('$([guid]::NewGuid())'::uuid, '$revokedMembershipOrganizationId'::uuid, 'Fazenda bloqueada', 'ACTIVE'),
    ('$([guid]::NewGuid())'::uuid, '$suspendedOrganizationId'::uuid, 'Fazenda bloqueada', 'ACTIVE'),
    ('$([guid]::NewGuid())'::uuid, '$archivedOrganizationId'::uuid, 'Fazenda bloqueada', 'ACTIVE');
"@
    $organizationSql | & docker exec -i $databaseContainer psql -v ON_ERROR_STOP=1 -U postgres -d postgres | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível preparar os dados temporários de organizações."
    }
    $organizationsUri = "http://127.0.0.1:$port/api/v1/me/organizations"
    $organizations = Invoke-RestMethod -Uri $organizationsUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $farmsUri = "http://127.0.0.1:$port/api/v1/me/organizations/$organizationId/farms"
    $allFarms = Invoke-RestMethod -Uri $farmsUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $contextUri = "http://127.0.0.1:$port/api/v1/context"
    try {
        $allFarmsContext = Invoke-WebRequest -Uri $contextUri -Method Get -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $activeFarmId } -UseBasicParsing
    } catch {
        $failure = Get-SafeHttpFailure -ErrorRecord $_
        $preserveDiagnostics = $true
        throw "GET /api/v1/context falhou: status=$($failure.Status); requestId=$($failure.RequestId); code=$($failure.Code). Logs da API foram preservados para diagnóstico."
    }
    $allFarmsContextBody = $allFarmsContext.Content | ConvertFrom-Json
    $farmProfileUri = "http://127.0.0.1:$port/api/v1/farms/current"
    $farmProfile = Invoke-HttpCheck -Uri $farmProfileUri -Method Get -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $activeFarmId }
    Assert-HttpStatus -Step "GET inicial do perfil da fazenda" -Method "GET" -ExpectedStatus 200 -Result $farmProfile
    $farmProfileBody = $farmProfile.Content | ConvertFrom-Json
    $farmProfileHeaders = @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $activeFarmId }
    $initialFarmVersion = [long]$farmProfileBody.version
    $patchBody = @{ name = "  Fazenda  Smoke Atualizada  "; expectedVersion = $initialFarmVersion } | ConvertTo-Json -Compress
    $farmProfilePatch = Invoke-HttpCheck -Uri $farmProfileUri -Method Patch -Headers $farmProfileHeaders -Body $patchBody
    Assert-HttpStatus -Step "PATCH válido do perfil da fazenda" -Method "PATCH" -ExpectedStatus 200 -Result $farmProfilePatch
    $farmProfilePatchBody = $farmProfilePatch.Content | ConvertFrom-Json
    $farmProfileAfterPatch = Invoke-HttpCheck -Uri $farmProfileUri -Method Get -Headers $farmProfileHeaders
    Assert-HttpStatus -Step "GET após PATCH válido do perfil da fazenda" -Method "GET" -ExpectedStatus 200 -Result $farmProfileAfterPatch
    $farmProfileAfterPatchBody = $farmProfileAfterPatch.Content | ConvertFrom-Json
    $stalePatch = Invoke-HttpCheck -Uri $farmProfileUri -Method Patch -Headers $farmProfileHeaders -Body (@{ name = "Tentativa conflitante"; expectedVersion = $initialFarmVersion } | ConvertTo-Json -Compress)
    $forbiddenField = Invoke-HttpCheck -Uri $farmProfileUri -Method Patch -Headers $farmProfileHeaders -Body (@{ name = "Campo proibido"; expectedVersion = ($initialFarmVersion + 1); farmId = $otherFarmId } | ConvertTo-Json -Compress)
    $queryParameter = Invoke-HttpCheck -Uri "${farmProfileUri}?farmId=$otherFarmId" -Method Patch -Headers $farmProfileHeaders -Body (@{ name = "Query proibida"; expectedVersion = ($initialFarmVersion + 1) } | ConvertTo-Json -Compress)
    $otherTenantPatch = Invoke-HttpCheck -Uri $farmProfileUri -Method Patch -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $otherOrganizationId; "X-Farm-Id" = $otherFarmId } -Body (@{ name = "Tentativa cross-tenant"; expectedVersion = 0 } | ConvertTo-Json -Compress)
    Assert-HttpStatus -Step "PATCH de perfil de outra organização" -Method "PATCH" -ExpectedStatus 404 -Result $otherTenantPatch
    $otherFarmProfile = Invoke-HttpCheck -Uri $farmProfileUri -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $otherOrganizationId; "X-Farm-Id" = $otherFarmId }
    Assert-HttpStatus -Step "GET de perfil de outra organização" -Method "GET" -ExpectedStatus 404 -Result $otherFarmProfile
    $farmProfileAfterRejected = Invoke-HttpCheck -Uri $farmProfileUri -Method Get -Headers $farmProfileHeaders
    Assert-HttpStatus -Step "GET após PATCH rejeitado do perfil da fazenda" -Method "GET" -ExpectedStatus 200 -Result $farmProfileAfterRejected
    $farmProfileAfterRejectedBody = $farmProfileAfterRejected.Content | ConvertFrom-Json
    $otherFarmProfileStatus = Get-HttpStatus -Uri $farmProfileUri -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $otherOrganizationId; "X-Farm-Id" = $otherFarmId }
    $crossTenantFarmProfileStatus = Get-HttpStatus -Uri $farmProfileUri -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $otherFarmId }
    $inactiveFarmProfileStatus = Get-HttpStatus -Uri $farmProfileUri -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $inactiveFarmId }
    @"
update app.organization_memberships set farm_scope_mode = 'SELECTED_FARMS' where id = '$membershipId'::uuid;
insert into app.membership_farm_scopes (tenant_id, membership_id, farm_id)
values ('$organizationId'::uuid, '$membershipId'::uuid, '$secondActiveFarmId'::uuid);
"@ | & docker exec -i $databaseContainer psql -v ON_ERROR_STOP=1 -U postgres -d postgres | Out-Null
    $selectedFarms = Invoke-RestMethod -Uri $farmsUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $selectedContext = Invoke-RestMethod -Uri $contextUri -Method Get -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $secondActiveFarmId }
    $unselectedContextStatus = Get-HttpStatus -Uri $contextUri -Headers @{ Authorization = "Bearer $accessToken"; "X-Organization-Id" = $organizationId; "X-Farm-Id" = $activeFarmId }
    $otherFarmsUri = "http://127.0.0.1:$port/api/v1/me/organizations/$otherOrganizationId/farms"
    $otherFarms = Invoke-RestMethod -Uri $otherFarmsUri -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $suspendedMembershipFarms = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/v1/me/organizations/$suspendedMembershipOrganizationId/farms" -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $revokedMembershipFarms = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/v1/me/organizations/$revokedMembershipOrganizationId/farms" -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $suspendedOrganizationFarms = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/v1/me/organizations/$suspendedOrganizationId/farms" -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $archivedOrganizationFarms = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/v1/me/organizations/$archivedOrganizationId/farms" -Method Get -Headers @{ Authorization = "Bearer $accessToken" }
    $storedVersion = (& docker exec $databaseContainer psql -At -U postgres -d postgres `
            -c "select version from app.users where id = '$($signup.user.id)'::uuid").Trim()
    $sensitiveColumnCount = (& docker exec $databaseContainer psql -At -U postgres -d postgres `
            -c "select count(*) from information_schema.columns where table_schema = 'app' and table_name = 'users' and column_name in ('password', 'token', 'access_token', 'refresh_token')").Trim()
    $missingTokenStatus = Get-HttpStatus -Uri $meUri
    $signature = $segments[2]
    $replacement = if ($signature[0] -eq "A") { "B" } else { "A" }
    $alteredToken = "$($segments[0]).$($segments[1]).$replacement$($signature.Substring(1))"
    $alteredTokenStatus = Get-HttpStatus -Uri $meUri -Headers @{ Authorization = "Bearer $alteredToken" }

    $apiLogs = (Get-Content -LiteralPath $stdoutLog.FullName -Raw -ErrorAction SilentlyContinue) `
            + (Get-Content -LiteralPath $stderrLog.FullName -Raw -ErrorAction SilentlyContinue)
    $jwks = Invoke-RestMethod -Uri "$($supabase.API_URL)/auth/v1/.well-known/jwks.json" -Method Get
    $signingKey = @($jwks.keys | Where-Object {
                $_.kid -eq $jwtHeader.kid -and $_.kty -eq "EC" -and $_.crv -eq "P-256"
            })
    $requiredClaims = @("sub", "iss", "exp", "iat")
    $presentClaims = @($requiredClaims | Where-Object { $null -ne $jwtPayload.$_ })

    [pscustomobject]@{
        SupabaseAuth = "local"
        Algorithm = $jwtHeader.alg
        Issuer = $jwtPayload.iss
        LocalEs256Jwt = $jwtHeader.alg -eq "ES256" `
                -and $jwtPayload.iss -eq "$($supabase.API_URL)/auth/v1" `
                -and $signingKey.Count -eq 1
        RequiredClaimsPresent = $presentClaims.Count -eq $requiredClaims.Count
        JwksKeyCount = @($jwks.keys).Count
        ValidTokenStatus = 200
        SubjectMatches = $me.userId -eq $signup.user.id -and $me.userId -eq $jwtPayload.sub
        UserPersisted = $storedVersion -eq "0"
        SecondCallIdempotent = $meAgain.version -eq $me.version `
                -and $meAgain.updatedAt -eq $me.updatedAt
        OrganizationsEndpointStatus = 200
        AccessibleOrganizationReturned = @($organizations.items).Count -eq 1 `
                -and $organizations.items[0].organizationId -eq $organizationId `
                -and $organizations.items[0].membershipId -eq $membershipId `
                -and $organizations.items[0].role -eq "OWNER" `
                -and $organizations.items[0].farmScopeMode -eq "ALL_FARMS"
        OtherUserOrganizationIsolated = @($organizations.items).organizationId -notcontains $otherOrganizationId
        FarmsOrPermissionsReturned = $null -ne $organizations.items[0].farmId `
                -or $null -ne $organizations.items[0].permissions
        AllFarmsReturned = @($allFarms.items).Count -eq 2 `
                -and @($allFarms.items).farmId -contains $activeFarmId `
                -and @($allFarms.items).farmId -contains $secondActiveFarmId `
                -and @($allFarms.items).farmId -notcontains $inactiveFarmId `
                -and @($allFarms.items).farmId -notcontains $archivedFarmId
        SelectedFarmsReturned = @($selectedFarms.items).Count -eq 1 `
                -and $selectedFarms.items[0].farmId -eq $secondActiveFarmId
        OtherOrganizationFarmsIsolated = @($otherFarms.items).Count -eq 0
        TenantContextAllFarms = $allFarmsContext.StatusCode -eq 200 -and $allFarmsContext.Headers["Cache-Control"] -match "no-store" -and $allFarmsContextBody.userId -eq $signup.user.id -and $allFarmsContextBody.organization.id -eq $organizationId -and $allFarmsContextBody.farm.id -eq $activeFarmId -and $allFarmsContextBody.membership.id -eq $membershipId -and $allFarmsContextBody.membership.farmScopeMode -eq "ALL_FARMS"
        TenantContextSelectedFarms = $selectedContext.farm.id -eq $secondActiveFarmId -and $selectedContext.membership.farmScopeMode -eq "SELECTED_FARMS"
        TenantContextUnselectedFarmRejected = $unselectedContextStatus -eq 404
        FarmProfileCurrent = $farmProfile.Status -eq 200 -and $farmProfile.CacheControl -match "no-store" -and $farmProfileBody.id -eq $activeFarmId -and $farmProfileBody.organizationId -eq $organizationId -and $farmProfileBody.name -eq "Fazenda ativa A" -and $farmProfileBody.status -eq "ACTIVE" -and $farmProfileBody.version -eq 0 -and @($farmProfileBody.PSObject.Properties.Name).Count -eq 5 -and -not ($farmProfile.Content -match "membership|farmScopeMode|role|token|email|claims")
        FarmProfilePatchUpdated = $farmProfilePatch.Status -eq 200 -and $farmProfilePatch.CacheControl -match "no-store" -and $farmProfilePatchBody.name -eq "Fazenda  Smoke Atualizada" -and $farmProfilePatchBody.version -eq ($initialFarmVersion + 1)
        FarmProfilePatchReadBack = $farmProfileAfterPatch.Status -eq 200 -and $farmProfileAfterPatch.CacheControl -match "no-store" -and $farmProfileAfterPatchBody.name -eq "Fazenda  Smoke Atualizada" -and $farmProfileAfterPatchBody.version -eq ($initialFarmVersion + 1)
        FarmProfilePatchConflictRejected = $stalePatch.Status -eq 409 -and $stalePatch.Code -eq "FARM_PROFILE_VERSION_CONFLICT" -and $stalePatch.CacheControl -match "no-store"
        FarmProfilePatchForbiddenFieldRejected = $forbiddenField.Status -eq 400 -and $forbiddenField.Code -eq "FARM_PROFILE_UPDATE_INVALID" -and $forbiddenField.CacheControl -match "no-store"
        FarmProfilePatchQueryRejected = $queryParameter.Status -eq 400 -and $queryParameter.Code -eq "FARM_PROFILE_UPDATE_INVALID" -and $queryParameter.CacheControl -match "no-store"
        FarmProfilePatchOtherTenantRejected = $otherTenantPatch.Status -eq 404 -and $otherTenantPatch.Code -eq "FARM_PROFILE_NOT_AVAILABLE" -and $otherTenantPatch.CacheControl -match "no-store"
        FarmProfilePatchRejectedUnchanged = $farmProfileAfterRejected.Status -eq 200 -and $farmProfileAfterRejected.CacheControl -match "no-store" -and $farmProfileAfterRejectedBody.name -eq "Fazenda  Smoke Atualizada" -and $farmProfileAfterRejectedBody.version -eq ($initialFarmVersion + 1)
        FarmProfileOtherOrganizationRejected = $otherFarmProfileStatus -eq 404
        FarmProfileCrossTenantRejected = $crossTenantFarmProfileStatus -eq 404
        FarmProfileInactiveRejected = $inactiveFarmProfileStatus -eq 404
        SuspendedMembershipFarmsIsolated = @($suspendedMembershipFarms.items).Count -eq 0
        RevokedMembershipFarmsIsolated = @($revokedMembershipFarms.items).Count -eq 0
        SuspendedOrganizationFarmsIsolated = @($suspendedOrganizationFarms.items).Count -eq 0
        ArchivedOrganizationFarmsIsolated = @($archivedOrganizationFarms.items).Count -eq 0
        SensitiveColumnsFound = [int]$sensitiveColumnCount
        MissingTokenStatus = $missingTokenStatus
        AlteredTokenStatus = $alteredTokenStatus
        RawTokenFoundInApiLog = $apiLogs.Contains($accessToken)
        EmailFoundInApiLog = $apiLogs.Contains($email)
    } | Tee-Object -Variable smokeResult

    $requiredChecks = @(
        $smokeResult.LocalEs256Jwt, $smokeResult.RequiredClaimsPresent,
        $smokeResult.SubjectMatches, $smokeResult.UserPersisted,
        $smokeResult.SecondCallIdempotent, $smokeResult.AccessibleOrganizationReturned,
        $smokeResult.OtherUserOrganizationIsolated, $smokeResult.AllFarmsReturned,
        $smokeResult.TenantContextAllFarms, $smokeResult.TenantContextSelectedFarms, $smokeResult.TenantContextUnselectedFarmRejected,
        $smokeResult.FarmProfileCurrent, $smokeResult.FarmProfileOtherOrganizationRejected, $smokeResult.FarmProfileCrossTenantRejected, $smokeResult.FarmProfileInactiveRejected,
        $smokeResult.FarmProfilePatchUpdated, $smokeResult.FarmProfilePatchReadBack, $smokeResult.FarmProfilePatchConflictRejected,
        $smokeResult.FarmProfilePatchForbiddenFieldRejected, $smokeResult.FarmProfilePatchQueryRejected, $smokeResult.FarmProfilePatchOtherTenantRejected, $smokeResult.FarmProfilePatchRejectedUnchanged,
        $smokeResult.SelectedFarmsReturned, $smokeResult.OtherOrganizationFarmsIsolated,
        $smokeResult.SuspendedMembershipFarmsIsolated, $smokeResult.RevokedMembershipFarmsIsolated,
        $smokeResult.SuspendedOrganizationFarmsIsolated, $smokeResult.ArchivedOrganizationFarmsIsolated,
        ($smokeResult.MissingTokenStatus -eq 401), ($smokeResult.AlteredTokenStatus -eq 401),
        (-not $smokeResult.FarmsOrPermissionsReturned), ($smokeResult.SensitiveColumnsFound -eq 0),
        (-not $smokeResult.RawTokenFoundInApiLog), (-not $smokeResult.EmailFoundInApiLog)
    )
    if ($requiredChecks -contains $false) {
        throw "O smoke local detectou um cenário de segurança ou isolamento inválido."
    }
} catch {
    $primaryFailure = $_
    $preserveDiagnostics = $true
} finally {
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
    try {
        if ($apiProcess -and -not $apiProcess.HasExited) {
            Stop-Process -Id $apiProcess.Id -Force
            Wait-Process -Id $apiProcess.Id -Timeout 10 -ErrorAction SilentlyContinue
        }
        if ($apiProcess) {
            $apiProcess.Refresh()
            if (-not $apiProcess.HasExited) {
                throw "A API permaneceu em execução após a tentativa de encerramento."
            }
        }
    } catch {
        $cleanupFailures.Add("Não foi possível encerrar a API iniciada pelo smoke.")
    }
    try {
        if ($runtimeRoleCreated -and $databaseContainer) {
            "drop role if exists $runtimeUsername;" |
                    & docker exec -i $databaseContainer psql -v ON_ERROR_STOP=1 -U postgres -d postgres | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "drop role falhou"
            }
        }
    } catch {
        $cleanupFailures.Add("Não foi possível remover o login runtime efêmero.")
    }
    try {
        if ($supabaseHealthy) {
            & .\node_modules\.bin\supabase.cmd db reset | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "reset falhou"
            }
        }
    } catch {
        $cleanupFailures.Add("O reset final do Supabase local falhou.")
    }
    $preserveDiagnostics = $preserveDiagnostics -or $cleanupFailures.Count -gt 0
    $sensitiveValues = @(
        $runtimePassword, $email, $password, $accessToken,
        $supabase.PUBLISHABLE_KEY, $supabase.ANON_KEY, $supabase.SECRET_KEY,
        $supabase.SERVICE_ROLE_KEY, $supabase.JWT_SECRET,
        $supabase.S3_PROTOCOL_ACCESS_KEY_ID, $supabase.S3_PROTOCOL_ACCESS_KEY_SECRET
    )
    if ($preserveDiagnostics) {
        Protect-DiagnosticFile -Path $stdoutLog.FullName -SensitiveValues $sensitiveValues
        Protect-DiagnosticFile -Path $stderrLog.FullName -SensitiveValues $sensitiveValues
        Write-Host "Logs temporários da API preservados: $($stdoutLog.FullName) e $($stderrLog.FullName)"
    } else {
        Remove-Item -LiteralPath $stdoutLog.FullName -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stderrLog.FullName -Force -ErrorAction SilentlyContinue
    }
}

if ($primaryFailure) {
    $message = Protect-DiagnosticText -Content $primaryFailure.Exception.Message -SensitiveValues $sensitiveValues
    if ($cleanupFailures.Count -gt 0) {
        $message += " Falhas de limpeza: $($cleanupFailures -join ' ')"
    }
    Write-Error $message
    exit 1
}
if ($cleanupFailures.Count -gt 0) {
    Write-Error ($cleanupFailures -join " ")
    exit 1
}

exit 0
