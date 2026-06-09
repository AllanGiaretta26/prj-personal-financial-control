#Requires -Version 7
<#
.SYNOPSIS
    Smoke test de segurança da API PFC via Postman CLI, em estado determinístico.

.DESCRIPTION
    Executa a collection "PFC v2 — Validação de Segurança" (JWT, ownership/IDOR,
    rate limit, headers) contra a aplicação rodando localmente, garantindo as
    pré-condições que tornam a rodada repetível:

      1. (opcional) build do jar
      2. para qualquer instância da app em execução
      3. reseta o volume do Postgres (docker compose down -v && up -d) — assim os
         usuários A/B da collection ainda não existem e o register sai 201
      4. sobe a app e espera ficar pronta — reiniciar zera os buckets de rate
         limit (estado em memória), evitando 429 espúrio na pasta 00
      5. roda a collection pela Postman CLI (exit code != 0 se algum teste falhar)
      6. (por padrão) encerra a app ao final

    Rode SEMPRE uma vez por reset: re-executar a collection sem reiniciar a app
    drena o bucket de login (compartilhado entre /auth/login e /auth/register) e
    a própria pasta 00 passa a falhar com 429.

.PARAMETER CollectionId
    ID (nuvem) ou caminho do .json da collection. Padrão: a collection PFC v2.

.PARAMETER EnvironmentId
    UID (nuvem) ou caminho do .json do environment. Padrão: "PFC - Local".

.PARAMETER Reporters
    Reporters da Postman CLI (ex.: "cli", "cli,junit"). Padrão: "cli".

.PARAMETER SkipBuild
    Pula o build do jar (usa o target/ existente).

.PARAMETER SkipReset
    Não reseta o banco nem reinicia a app (usa o que já estiver no ar). Útil só
    para iterar em uma pasta isolada — NÃO use para rodar a collection inteira.

.PARAMETER KeepAppRunning
    Mantém a app rodando após os testes (padrão: encerra).

.EXAMPLE
    ./run-postman.ps1

.EXAMPLE
    ./run-postman.ps1 -Reporters cli,junit -KeepAppRunning
#>
[CmdletBinding()]
param(
    [string]$CollectionId   = 'b917bfad-8452-497d-b873-e6cae3313c50',
    [string]$EnvironmentId  = '49447275-e0e4bdf4-cb99-41a8-96a9-260ae1d8861c',
    [string]$Reporters      = 'cli',
    [switch]$SkipBuild,
    [switch]$SkipReset,
    [switch]$KeepAppRunning
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot   # diretório pfc/

$Jar     = 'target/pfc-0.0.1-SNAPSHOT.jar'
$LogOut  = Join-Path $PSScriptRoot 'app.log'
$LogErr  = Join-Path $PSScriptRoot 'app.err.log'
$HealthUrl = 'http://localhost:8080/v3/api-docs'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

function Stop-App {
    Get-CimInstance Win32_Process |
        Where-Object { $_.CommandLine -like '*pfc-0.0.1-SNAPSHOT.jar*' } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force
            Write-Host "  app encerrada (PID $($_.ProcessId))"
        }
}

function Wait-Healthy([int]$TimeoutSec = 90) {
    for ($i = 0; $i -lt $TimeoutSec; $i++) {
        try {
            $r = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 1 }
    }
    return $false
}

# 1. Build (opcional)
if (-not $SkipBuild) {
    Write-Step 'Build do jar (mvnw -DskipTests)'
    & "$PSScriptRoot\mvnw.cmd" -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Build falhou (exit $LASTEXITCODE)" }
}
if (-not (Test-Path $Jar)) { throw "Jar não encontrado: $Jar (rode sem -SkipBuild)" }

if (-not $SkipReset) {
    # 2. Para a app
    Write-Step 'Encerrando instância anterior da app (se houver)'
    Stop-App

    # 3. Reseta o banco
    Write-Step 'Resetando volume do Postgres (down -v && up -d)'
    docker compose down -v   | Out-Host
    docker compose up -d      | Out-Host
    Write-Host '  aguardando Postgres aceitar conexões...'
    for ($i = 0; $i -lt 30; $i++) {
        docker exec pfc-postgres pg_isready -U pfc *> $null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    }

    # 4. Sobe a app e espera ficar pronta
    Write-Step 'Subindo a app (perfil local)'
    Remove-Item $LogOut, $LogErr -ErrorAction SilentlyContinue
    Start-Process -FilePath 'java' `
        -ArgumentList '-jar', $Jar, '--spring.profiles.active=local' `
        -RedirectStandardOutput $LogOut -RedirectStandardError $LogErr | Out-Null

    if (-not (Wait-Healthy)) {
        Write-Host 'A app não respondeu a tempo. Últimas linhas do log:' -ForegroundColor Red
        Get-Content $LogOut, $LogErr -Tail 30 -ErrorAction SilentlyContinue
        Stop-App
        exit 1
    }
    Write-Host '  app pronta.' -ForegroundColor Green
} else {
    Write-Step 'SkipReset: usando a app já em execução'
    if (-not (Wait-Healthy 5)) { throw "App não está respondendo em $HealthUrl" }
}

# 5. Roda a collection pela Postman CLI
Write-Step 'Executando a collection pela Postman CLI'
& postman collection run $CollectionId -e $EnvironmentId --reporters $Reporters
$runExit = $LASTEXITCODE

# 6. Encerra a app (a menos que peçam para manter)
if (-not $KeepAppRunning) {
    Write-Step 'Encerrando a app'
    Stop-App
} else {
    Write-Host "`nApp mantida em execução (use -KeepAppRunning). Encerre com:" -ForegroundColor Yellow
    Write-Host "  Get-CimInstance Win32_Process | ? { `$_.CommandLine -like '*pfc-0.0.1-SNAPSHOT.jar*' } | % { Stop-Process `$_.ProcessId -Force }"
}

if ($runExit -eq 0) {
    Write-Host "`nValidação concluída: todos os testes passaram." -ForegroundColor Green
} else {
    Write-Host "`nValidação falhou (Postman CLI exit $runExit)." -ForegroundColor Red
}
exit $runExit
