param(
    [ValidateSet("app-image", "exe", "msi", "all")]
    [string]$Type = "msi",
    [string]$AppVersion = "",
    [string]$OutputDir = "dist",
    [string]$IconPath = "",
    [switch]$SkipTests,
    [switch]$JPackageVerbose
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Test-IsWindowsHost {
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

if (-not (Test-IsWindowsHost)) {
    throw "This script supports Windows 10/11 only."
}

function Resolve-JPackagePath {
    $fromPath = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    if ($env:JAVA_HOME) {
        $javaHomeJpackage = Join-Path $env:JAVA_HOME "bin\\jpackage.exe"
        if (Test-Path $javaHomeJpackage) {
            return $javaHomeJpackage
        }
    }

    $jdkRoot = "C:\Program Files\Java"
    if (Test-Path $jdkRoot) {
        $jdkMatch = Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk*" } |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($jdkMatch) {
            $jdkJpackage = Join-Path $jdkMatch.FullName "bin\\jpackage.exe"
            if (Test-Path $jdkJpackage) {
                return $jdkJpackage
            }
        }
    }

    return $null
}

$jpackagePath = Resolve-JPackagePath
if (-not $jpackagePath) {
    throw "jpackage is not available. Install JDK 17+ and ensure jpackage is accessible via PATH or JAVA_HOME."
}

function Get-VersionFromPom {
    param([string]$PomPath)
    [xml]$pom = Get-Content $PomPath
    $version = $pom.project.version
    if (-not $version) {
        throw "Unable to resolve project version from $PomPath"
    }
    return [string]$version
}

function Test-WixAvailable {
    $wix4 = Get-Command wix -ErrorAction SilentlyContinue
    $candle = Get-Command candle -ErrorAction SilentlyContinue
    $light = Get-Command light -ErrorAction SilentlyContinue
    return ($wix4 -or ($candle -and $light))
}

function Add-KnownWixPathsToSession {
    if (Test-WixAvailable) {
        return
    }

    $roots = @("C:\Program Files", "C:\Program Files (x86)")
    $candidatePaths = @()
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) {
            continue
        }
        $dirs = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "WiX*" }
        foreach ($dir in $dirs) {
            $bin = Join-Path $dir.FullName "bin"
            if (Test-Path $bin) {
                $candidatePaths += $bin
            }
        }
    }

    foreach ($pathEntry in $candidatePaths) {
        if (-not ($env:Path.Split(";") -contains $pathEntry)) {
            $env:Path = "$pathEntry;$env:Path"
        }
    }
}

Push-Location $ProjectRoot
try {
    $logDir = Join-Path $ProjectRoot "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $logPath = Join-Path $logDir "package-windows-$timestamp.log"
    Start-Transcript -Path $logPath -Force | Out-Null

    $effectiveVersion = $AppVersion
    if ([string]::IsNullOrWhiteSpace($effectiveVersion)) {
        $effectiveVersion = Get-VersionFromPom -PomPath (Join-Path $ProjectRoot "pom.xml")
    }

    Add-KnownWixPathsToSession
    if ($Type -in @("msi", "exe", "all")) {
        if (-not (Test-WixAvailable)) {
            throw "WiX Toolset was not found. Install WiX and ensure `wix` (v4) or `candle`+`light` (v3) are on PATH."
        }
    }

    if ($SkipTests) {
        & .\mvnw.cmd clean package -DskipTests
    } else {
        & .\mvnw.cmd clean test package
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE. See log: $logPath"
    }

    $targetDir = Join-Path $ProjectRoot "target"
    $mainJar = "TextConverter.jar"
    $jarPath = Join-Path $targetDir $mainJar
    if (-not (Test-Path $jarPath)) {
        throw "Expected packaged jar not found at $jarPath"
    }

    $distDir = Join-Path $ProjectRoot $OutputDir
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null

    $resolvedIconPath = $IconPath
    if ([string]::IsNullOrWhiteSpace($resolvedIconPath)) {
        $resolvedIconPath = Join-Path $ProjectRoot "docs\\textcon.ico"
    }

    $typesToBuild = if ($Type -eq "all") { @("app-image", "exe", "msi") } else { @($Type) }

    foreach ($packageType in $typesToBuild) {
        $args = @(
            "--type", $packageType,
            "--name", "TextCon",
            "--input", $targetDir,
            "--main-jar", $mainJar,
            "--main-class", "com.textcon.Main",
            "--dest", $distDir,
            "--app-version", $effectiveVersion,
            "--vendor", "TextCon",
            "--description", "Markdown text converter desktop app",
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-per-user-install",
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )

        if (Test-Path $resolvedIconPath) {
            $args += @("--icon", $resolvedIconPath)
        }
        if ($JPackageVerbose) {
            $args += "--verbose"
        }

        Write-Host ""
        Write-Host "Building package type: $packageType"
        & $jpackagePath @args
        if ($LASTEXITCODE -ne 0) {
            throw "jpackage failed for type '$packageType' with exit code $LASTEXITCODE. See log: $logPath"
        }
    }

    Write-Host ""
    Write-Host "Packaging complete"
    Write-Host "Version : $effectiveVersion"
    Write-Host "Output  : $distDir"
    Write-Host "Log     : $logPath"
} finally {
    try {
        Stop-Transcript | Out-Null
    } catch {
        # Ignore when transcript isn't active.
    }
    Pop-Location
}
