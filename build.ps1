$ErrorActionPreference = "Stop"

$root    = Split-Path -Parent $MyInvocation.MyCommand.Path
$build   = Join-Path $root "build"
$classes = Join-Path $build "classes"
$input   = Join-Path $build "input"
$dist    = Join-Path $root "dist"
$jar     = Join-Path $dist "VoxelPort.jar"
$lib     = Join-Path $root "lib"
$zipPath = Join-Path $dist "VoxelPort-1.1.0-portable.zip"

foreach ($path in @($build, $dist)) {
    if (Test-Path $path) {
        Get-ChildItem -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue |
            ForEach-Object { $_.Attributes = 'Normal' }
        Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force -ErrorAction Stop
    } else {
        New-Item -ItemType Directory -Force $path | Out-Null
    }
}
New-Item -ItemType Directory -Force $classes | Out-Null
New-Item -ItemType Directory -Force $input   | Out-Null
New-Item -ItemType Directory -Force $dist    | Out-Null

$libJars = (Get-ChildItem -Path $lib -Filter "*.jar" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty FullName) -join ";"

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java |
           Select-Object -ExpandProperty FullName

$addMods = "java.net.http,java.management"
if ($libJars) {
    javac --release 17 -encoding UTF-8 --add-modules $addMods -cp $libJars -d $classes $sources
} else {
    javac --release 17 -encoding UTF-8 --add-modules $addMods -d $classes $sources
}
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# Build a portable fat JAR: unpack third-party jars first, then add app classes.
if ($libJars) {
    $libJarList = Get-ChildItem -Path $lib -Filter "*.jar" -ErrorAction SilentlyContinue
    foreach ($lj in $libJarList) {
        $extractDir = Join-Path $build ("extract_" + $lj.BaseName)
        New-Item -ItemType Directory -Force $extractDir | Out-Null
        Push-Location $extractDir
        jar xf $lj.FullName
        if ($LASTEXITCODE -ne 0) { throw "jar extraction failed for $($lj.FullName)" }
        Pop-Location
        Get-ChildItem -Path $extractDir -Recurse -File |
            Where-Object { $_.FullName -notmatch "META-INF[/\\]MANIFEST" -and $_.Name -ne "module-info.class" } |
            ForEach-Object {
                $rel = $_.FullName.Substring($extractDir.Length + 1)
                $dest = Join-Path $classes $rel
                New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
                Copy-Item $_.FullName $dest -Force
            }
    }
}

jar --create --file $jar --main-class org.localm.LocalMJava -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar creation failed with exit code $LASTEXITCODE" }

if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path $jar -DestinationPath $zipPath -CompressionLevel Optimal -Force

Write-Host "Built portable JAR:"
Write-Host $jar
Write-Host ""
Write-Host "Run with:"
Write-Host "java -jar `"$jar`""
Write-Host ""
Write-Host "Built release zip:"
Write-Host $zipPath
