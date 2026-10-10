param([switch]$Test)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot'
$env:ANDROID_HOME = 'C:\Android\android-sdk'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\nonexistent-rise-sockets'
$credentialsPath = Join-Path $PSScriptRoot 'signing\credentials.json'
if (Test-Path -LiteralPath $credentialsPath) {
    $signingConfig = Get-Content -Raw -LiteralPath $credentialsPath | ConvertFrom-Json
    foreach ($setting in $signingConfig.PSObject.Properties) { [Environment]::SetEnvironmentVariable($setting.Name, $setting.Value, 'Process') }
}
Push-Location $PSScriptRoot
try {
    $tasks = @(':app:assembleRelease', ':app:lintRelease')
    if ($Test) { $tasks += @(':app:testDebugUnitTest', '--tests', 'com.rise.musique.UpdateReleaseTest', '--tests', 'com.rise.musique.BackupMergeTest') }
    & .\gradlew.bat @tasks '-Pandroid.overridePathCheck=true' --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
    python scripts\prepare-release.py
    if ($LASTEXITCODE -ne 0) { throw 'Preparing release failed' }
} finally { Pop-Location }
