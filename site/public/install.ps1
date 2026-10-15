# TMPlayer for Windows, installed from PowerShell:
#
#     irm https://tmplayer.org/install.ps1 | iex
#
# Reads the same update feed the app reads (tmplayer.org/latest.json), downloads that release's
# Windows installer from GitHub, checks its SHA-256 against the feed, and runs it for the current
# user, so no administrator rights are asked for. A file fetched this way carries no "downloaded
# from the internet" mark, so Windows does not stop the unsigned installer with SmartScreen.
#
# Everything sits in one function that is called on the last line: if the download of this script
# is cut short, nothing in it has run.

function Install-TMPlayer {
    $ErrorActionPreference = 'Stop'
    # The progress bar slows Invoke-WebRequest down many times over on Windows PowerShell 5.
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    if (-not [Environment]::Is64BitOperatingSystem) {
        throw 'TMPlayer needs 64-bit Windows 10 or 11.'
    }

    $feed = Invoke-RestMethod -Uri 'https://tmplayer.org/latest.json' -UseBasicParsing
    $msi = $feed.assets.'windows-x64-msi'
    if (-not $msi) {
        throw "The latest release has no Windows installer. See $($feed.releaseUrl)"
    }

    $file = Join-Path ([IO.Path]::GetTempPath()) "TMPlayer-$($feed.version)-windows-x64.msi"
    $megabytes = [math]::Round($msi.size / 1MB)
    Write-Host "Downloading TMPlayer $($feed.version) ($megabytes MB)..."
    Invoke-WebRequest -Uri $msi.url -OutFile $file -UseBasicParsing

    $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $msi.sha256) {
        Remove-Item -Path $file -ErrorAction SilentlyContinue
        throw "The download does not match the checksum TMPlayer published, so it was deleted. Try again, or download from $($feed.releaseUrl)"
    }

    Write-Host 'Installing...'
    $run = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', "`"$file`"", '/passive', '/norestart') -Wait -PassThru
    Remove-Item -Path $file -ErrorAction SilentlyContinue
    # 3010 is "installed, a restart would finish it", which TMPlayer never needs.
    if ($run.ExitCode -ne 0 -and $run.ExitCode -ne 3010) {
        throw "The installer stopped with code $($run.ExitCode). Try again, or download from $($feed.releaseUrl)"
    }
    Write-Host "TMPlayer $($feed.version) is installed. Open it from the Start menu."
}

Install-TMPlayer
