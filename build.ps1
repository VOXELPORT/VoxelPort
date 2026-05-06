$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = Join-Path $root "build"
$classes = Join-Path $build "classes"
$input = Join-Path $build "input"
$dist = Join-Path $root "dist"
$jar = Join-Path $input "VoxelPort.jar"

Remove-Item -LiteralPath $build,$dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $classes | Out-Null
New-Item -ItemType Directory -Force $input | Out-Null
New-Item -ItemType Directory -Force $dist | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
javac --release 17 -encoding UTF-8 -d $classes $sources

jar --create --file $jar --main-class org.localm.LocalMJava -C $classes .

$runtimeImage = Join-Path $build "runtime"
jlink --add-modules java.desktop,java.net.http,java.logging,jdk.crypto.ec,jdk.zipfs --output $runtimeImage --strip-debug --no-header-files --no-man-pages

jpackage `
  --type app-image `
  --name "VoxelPort" `
  --input $input `
  --main-jar "VoxelPort.jar" `
  --main-class "org.localm.LocalMJava" `
  --runtime-image $runtimeImage `
  --dest $dist `
  --app-version "0.1.0"

Copy-Item -LiteralPath (Join-Path $root "bin") -Destination (Join-Path $dist "VoxelPort\bin") -Recurse -Force

Write-Host "Built app image:"
Write-Host (Join-Path $dist "VoxelPort\VoxelPort.exe")
