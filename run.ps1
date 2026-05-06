$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (!(Test-Path (Join-Path $root "build\classes"))) {
  New-Item -ItemType Directory -Force (Join-Path $root "build\classes") | Out-Null
}
$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
javac --release 17 -encoding UTF-8 -d (Join-Path $root "build\classes") $sources
java -cp (Join-Path $root "build\classes") org.localm.LocalMJava
