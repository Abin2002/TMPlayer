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
# is cut short, nothing in it has run. It never calls `exit`, which would close the window of the
# person who pasted the line: a failure prints what went wrong and what to try, sets
# $LASTEXITCODE to 1 for scripts that check it, and returns.
#
# The file is plain ASCII on purpose. Windows PowerShell 5.1 may read a fetched script in the
# wrong code page, so the few non-ASCII characters the progress bar draws are made at run time
# from their code points, and only where the terminal can show them (Windows Terminal).
#
# NO_COLOR turns the colours off. When the output is not a console (redirected to a file, CI, the
# ISE), every step is printed as a plain line and nothing is redrawn.

function Install-TMPlayer {
    $ErrorActionPreference = 'Stop'
    # PowerShell's own progress bar slows web requests down many times over on Windows
    # PowerShell 5, so it stays off; the download draws its own, lighter one below.
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    $fallbackUrl = 'https://tmplayer.org/download/'
    $useColor = -not $env:NO_COLOR
    $live = $false
    try {
        $live = ($Host.Name -eq 'ConsoleHost') -and -not [Console]::IsOutputRedirected
    } catch { }
    # Block characters and a braille spinner where the font has them, plain ASCII elsewhere
    # (the old console host often uses a font without them).
    $fancy = $live -and ($env:WT_SESSION -or $env:TERM_PROGRAM)
    if ($fancy) {
        $barFull = [string][char]0x2588
        $barEmpty = [string][char]0x2591
        $spinner = @(0x280B, 0x2819, 0x2839, 0x2838, 0x283C, 0x2834, 0x2826, 0x2827, 0x2807, 0x280F) | ForEach-Object { [string][char]$_ }
    } else {
        $barFull = '#'
        $barEmpty = '-'
        $spinner = @('|', '/', '-', '\')
    }
    $state = @{ width = 0 }

    function Get-Width {
        try { return [Math]::Max(40, [Console]::WindowWidth) } catch { return 80 }
    }

    function Write-Part([string]$Text, [string]$Color, [switch]$NoNewline) {
        if ($useColor -and $Color) {
            Write-Host $Text -ForegroundColor $Color -NoNewline:$NoNewline
        } else {
            Write-Host $Text -NoNewline:$NoNewline
        }
    }

    # The line that is redrawn in place: a mark, a label and the rest, padded so a shorter line
    # wipes out a longer one before it, and cut so it never wraps.
    function Write-Live([string]$Mark, [string]$MarkColor, [string]$Text) {
        $max = (Get-Width) - 1
        $line = "$Mark $Text"
        if ($line.Length -gt $max) { $Text = $Text.Substring(0, [Math]::Max(0, $max - $Mark.Length - 1)) }
        $len = $Mark.Length + 1 + $Text.Length
        $pad = ' ' * [Math]::Max(0, $state.width - $len)
        Write-Host "`r" -NoNewline
        Write-Part $Mark $MarkColor -NoNewline
        Write-Host " $Text$pad" -NoNewline
        $state.width = $len
    }

    # A finished step: replaces the live line, if any, and moves on to the next line.
    function Write-Step([string]$Mark, [string]$MarkColor, [string]$Text, [string]$TextColor) {
        if ($live) {
            $len = $Mark.Length + 1 + $Text.Length
            $pad = ' ' * [Math]::Max(0, $state.width - $len)
            Write-Host "`r" -NoNewline
            Write-Part $Mark $MarkColor -NoNewline
            Write-Host ' ' -NoNewline
            Write-Part "$Text$pad" $TextColor
            $state.width = 0
        } else {
            Write-Part $Mark $MarkColor -NoNewline
            Write-Host ' ' -NoNewline
            Write-Part $Text $TextColor
        }
    }

    function Write-Ok([string]$Text) { Write-Step '[ ok ]' 'Green' $Text }

    function Start-Step([string]$Text) {
        if ($live) { Write-Live '[ .. ]' 'Cyan' $Text } else { Write-Step '[ .. ]' 'Cyan' $Text }
    }

    function Format-MB([double]$Bytes) { '{0:0.0}' -f ($Bytes / 1MB) }

    function Format-Time([double]$Seconds) {
        if ($Seconds -lt 0 -or [double]::IsInfinity($Seconds) -or [double]::IsNaN($Seconds)) { return '--' }
        $s = [int][Math]::Ceiling($Seconds)
        if ($s -lt 60) { return "${s}s" }
        if ($s -lt 3600) { return '{0}m{1:00}s' -f [int][Math]::Floor($s / 60), ($s % 60) }
        return '{0}h{1:00}m' -f [int][Math]::Floor($s / 3600), [int][Math]::Floor(($s % 3600) / 60)
    }

    # The message of the innermost exception, which is the one that says what really happened.
    function Get-Reason($Err) {
        $e = $Err.Exception
        while ($e.InnerException) { $e = $e.InnerException }
        $text = "$($e.Message)".Trim()
        if (-not $text) { $text = "$($Err.Exception.Message)".Trim() }
        return $text
    }

    $stage = 'start'
    $msiUrl = $null
    $file = $null
    $version = $null

    try {
        Write-Host ''
        Write-Host 'TMPlayer installer'
        Write-Host ''

        if (-not [Environment]::Is64BitOperatingSystem) {
            $stage = 'system'
            throw 'TMPlayer needs 64-bit Windows 10 or 11, and this Windows is 32-bit.'
        }

        # 1. Release info
        $stage = 'feed'
        Start-Step 'Fetching release info from tmplayer.org'
        $feed = Invoke-RestMethod -Uri 'https://tmplayer.org/latest.json' -UseBasicParsing
        $version = $feed.version
        if ($feed.releaseUrl) { $fallbackUrl = $feed.releaseUrl }
        $msi = $feed.assets.'windows-x64-msi'
        if (-not $msi -or -not $msi.url) {
            throw 'The latest release has no Windows installer yet.'
        }
        $msiUrl = $msi.url
        $expectedHash = "$($msi.sha256)".Trim().ToLowerInvariant()
        if ($expectedHash -notmatch '^[0-9a-f]{64}$') {
            throw 'The release info has no SHA-256 for the Windows installer, so the download could not be checked.'
        }
        $sizeText = if ($msi.size) { " ($(Format-MB $msi.size) MB)" } else { '' }
        Write-Ok "Release info: TMPlayer $version$sizeText"

        # 2. Download, read in chunks so the bar can be drawn without slowing it down.
        $stage = 'download'
        $file = Join-Path ([IO.Path]::GetTempPath()) "TMPlayer-$version-windows-x64.msi"
        $label = "Downloading TMPlayer $version"
        if (-not $live) { Write-Step '[ .. ]' 'Cyan' $label }

        if (-not ('System.Net.Http.HttpClient' -as [type])) {
            Add-Type -AssemblyName System.Net.Http
        }
        $handler = New-Object System.Net.Http.HttpClientHandler
        $client = New-Object System.Net.Http.HttpClient($handler)
        $client.Timeout = [TimeSpan]::FromHours(2)
        $client.DefaultRequestHeaders.UserAgent.ParseAdd('TMPlayer-install.ps1')
        $response = $null
        $in = $null
        $out = $null
        $done = [long]0
        $finished = $false
        $watch = [Diagnostics.Stopwatch]::StartNew()
        try {
            $response = $client.GetAsync($msiUrl, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) {
                throw "GitHub answered $([int]$response.StatusCode) $($response.ReasonPhrase)."
            }
            $total = [long]0
            if ($response.Content.Headers.ContentLength) { $total = [long]$response.Content.Headers.ContentLength }
            elseif ($msi.size) { $total = [long]$msi.size }

            $in = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
            try { if ($in.CanTimeout) { $in.ReadTimeout = 60000 } } catch { }
            $out = New-Object IO.FileStream($file, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None, 1048576)
            $buffer = New-Object byte[] 262144

            # Redrawn at most five times a second (plain output: every 10 per cent).
            $nextDraw = 0
            $nextPercent = 10
            $lastBytes = [long]0
            $lastTime = 0.0
            $speed = 0.0
            while (($n = $in.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $out.Write($buffer, 0, $n)
                $done += $n
                $ms = $watch.ElapsedMilliseconds
                if ($ms -lt $nextDraw) { continue }
                $nextDraw = $ms + 200

                $now = $ms / 1000.0
                if ($now - $lastTime -ge 0.5) {
                    $rate = ($done - $lastBytes) / ($now - $lastTime)
                    $speed = if ($speed -le 0) { $rate } else { 0.7 * $speed + 0.3 * $rate }
                    $lastBytes = $done
                    $lastTime = $now
                }
                $speedText = if ($speed -gt 0) { "$(Format-MB $speed) MB/s" } else { '-- MB/s' }

                if ($total -gt 0) {
                    $fraction = [Math]::Min(1.0, $done / $total)
                    $percent = [int][Math]::Floor($fraction * 100)
                    $eta = if ($speed -gt 0) { Format-Time (($total - $done) / $speed) } else { '--' }
                    if ($live) {
                        $tail = '{0,3}%  {1}/{2} MB  {3}  {4}' -f $percent, (Format-MB $done), (Format-MB $total), $speedText, $eta
                        $room = (Get-Width) - 1 - 7 - $label.Length - 2 - 2 - $tail.Length
                        $barWidth = [Math]::Min(30, $room)
                        if ($barWidth -ge 10) {
                            $filled = [int][Math]::Floor($fraction * $barWidth)
                            $bar = ($barFull * $filled) + ($barEmpty * ($barWidth - $filled))
                            Write-Live '[ .. ]' 'Cyan' "$label  $bar  $tail"
                        } else {
                            Write-Live '[ .. ]' 'Cyan' "$label  $tail"
                        }
                    } elseif ($percent -ge $nextPercent -and $percent -lt 100) {
                        Write-Host ('       {0,3}%  {1}/{2} MB  {3}' -f $percent, (Format-MB $done), (Format-MB $total), $speedText)
                        $nextPercent = ([int][Math]::Floor($percent / 10) + 1) * 10
                    }
                } elseif ($live) {
                    Write-Live '[ .. ]' 'Cyan' "$label  $(Format-MB $done) MB  $speedText"
                }
            }
            $out.Flush()
            $finished = $true
        } finally {
            if ($out) { $out.Dispose() }
            if ($in) { $in.Dispose() }
            if ($response) { $response.Dispose() }
            $client.Dispose()
            # Also runs on Ctrl+C, which skips the catch below: no half file is left in Temp.
            if (-not $finished) {
                Remove-Item -Path $file -ErrorAction SilentlyContinue
                if ($live) { Write-Host ''; $state.width = 0 }
            }
        }
        if ($total -gt 0 -and $done -ne $total) {
            throw "The download stopped early, at $(Format-MB $done) of $(Format-MB $total) MB."
        }
        $seconds = [Math]::Max(0.001, $watch.Elapsed.TotalSeconds)
        Write-Ok ('Downloaded {0} MB in {1} ({2} MB/s)' -f (Format-MB $done), (Format-Time $seconds), (Format-MB ($done / $seconds)))

        # 3. Verify the SHA-256 against the one tmplayer.org published. A mismatch stops here.
        $stage = 'verify'
        Start-Step 'Checking the SHA-256 against tmplayer.org/latest.json'
        $hash = (Get-FileHash -Path $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $expectedHash) {
            Remove-Item -Path $file -ErrorAction SilentlyContinue
            throw "The download does not match the SHA-256 TMPlayer published (expected $expectedHash, got $hash), so it was deleted."
        }
        Write-Ok "Verified SHA-256 $($hash.Substring(0, 12))...$($hash.Substring(52)) matches latest.json"

        # 4. Install for the current user. msiexec shows its own small progress window too.
        $stage = 'install'
        $log = Join-Path ([IO.Path]::GetTempPath()) "TMPlayer-$version-install.log"
        $installLabel = "Installing TMPlayer $version"
        if (-not $live) { Write-Step '[ .. ]' 'Cyan' $installLabel }
        # A running copy holds its files open, and the upgrade would stop half way.
        if (Get-Process -Name 'TMPlayer' -ErrorAction SilentlyContinue) {
            throw 'TMPlayer is running. Close it (also from the notification area) and run the command again.'
        }
        # Up to 1.26.0 the MSI installed into %LOCALAPPDATA%\TMPlayer, the folder the app keeps its
        # sign in, settings and cache in, and its uninstall, which an upgrade runs first, deletes
        # that whole folder. So when such an install is there, the data steps aside for the install
        # and comes back after it. Later versions install to %LOCALAPPDATA%\Programs\TMPlayer.
        $dataDir = Join-Path $env:LOCALAPPDATA 'TMPlayer'
        $aside = Join-Path $env:LOCALAPPDATA 'TMPlayer-kept'
        $setAside = New-Object System.Collections.Generic.List[string]
        function Restore-Aside {
            foreach ($name in $setAside) {
                $to = Join-Path $dataDir $name
                if (Test-Path -LiteralPath $to) { continue }
                New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
                Move-Item -LiteralPath (Join-Path $aside $name) -Destination $to -ErrorAction SilentlyContinue
            }
            if ((Test-Path -LiteralPath $aside) -and -not @(Get-ChildItem -LiteralPath $aside -Force)) {
                Remove-Item -LiteralPath $aside -Force -ErrorAction SilentlyContinue
            }
        }
        try {
            if (Test-Path -LiteralPath (Join-Path $dataDir 'TMPlayer.exe')) {
                New-Item -ItemType Directory -Force -Path $aside | Out-Null
                foreach ($item in @(Get-ChildItem -LiteralPath $dataDir -Force)) {
                    # What the old MSI installed stays for its uninstall; everything else is data.
                    if ($item.PSIsContainer -and @('app', 'runtime') -contains $item.Name) { continue }
                    if (-not $item.PSIsContainer -and @('.exe', '.ico') -contains $item.Extension) { continue }
                    try {
                        Move-Item -LiteralPath $item.FullName -Destination (Join-Path $aside $item.Name) -ErrorAction Stop
                    } catch {
                        throw "Your settings could not be kept safe for the upgrade ($($item.Name) is in use). Close TMPlayer and run the command again."
                    }
                    $setAside.Add($item.Name)
                }
            }
            $run = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', "`"$file`"", '/passive', '/norestart', '/l*', "`"$log`"") -PassThru
            # Without this, Windows PowerShell 5.1 can report no exit code for a process it did not -Wait on.
            $null = $run.Handle
            $tick = 0
            $installWatch = [Diagnostics.Stopwatch]::StartNew()
            while (-not $run.WaitForExit(100)) {
                if ($live) {
                    $mark = '[ ' + $spinner[$tick % $spinner.Count] + '  ]'
                    Write-Live $mark 'Cyan' "$installLabel  $(Format-Time $installWatch.Elapsed.TotalSeconds)"
                    $tick++
                }
            }
            $run.WaitForExit()
            $code = $run.ExitCode
        } finally {
            # Also on a failed install or Ctrl+C: the data goes home whatever happened.
            Restore-Aside
        }
        Remove-Item -Path $file -ErrorAction SilentlyContinue
        # 3010 is "installed, a restart would finish it", which TMPlayer never needs.
        if ($code -ne 0 -and $code -ne 3010) {
            $why = switch ($code) {
                1602 { 'The installation was cancelled.' }
                1618 { 'Another installation is already running. Wait for it to finish.' }
                1625 { 'A Windows policy does not allow this installation.' }
                default { "The installer stopped with code $code." }
            }
            throw "$why The installer's log is $log"
        }
        Remove-Item -Path $log -ErrorAction SilentlyContinue
        Write-Ok "Installed TMPlayer $version for $env:USERNAME in $(Format-Time $installWatch.Elapsed.TotalSeconds)"

        Write-Host ''
        Write-Part "TMPlayer $version is ready." 'Green'
        Write-Host 'Open it from the Start menu or the desktop shortcut (search for "TMPlayer"), and sign in with Telegram.'
        Write-Host ''
        $global:LASTEXITCODE = 0
    } catch {
        $reason = Get-Reason $_
        $hint = switch ($stage) {
            'feed' { 'Check your internet connection and run the command again.' }
            'download' { 'Check your internet connection and run the command again.' }
            'verify' { 'Run the command again. If it keeps failing, tell us at https://github.com/dracu-lah/TMPlayer/issues' }
            'install' { 'Run the command again, or install the MSI yourself.' }
            default { '' }
        }
        if ($file -and $stage -eq 'download') { Remove-Item -Path $file -ErrorAction SilentlyContinue }

        $what = switch ($stage) {
            'feed' { 'Could not read the release info.' }
            'download' { 'The download failed.' }
            'verify' { 'The download did not pass the checksum.' }
            'install' { 'The installation did not finish.' }
            default { 'TMPlayer cannot be installed here.' }
        }
        Write-Step '[FAIL]' 'Red' $what 'Red'
        Write-Host ''
        Write-Part "TMPlayer was not installed. $reason" 'Red'
        if ($hint) { Write-Part $hint 'Red' }
        if ($msiUrl) {
            Write-Part "Or download the installer yourself: $msiUrl" 'Red'
        } elseif ($stage -ne 'system') {
            Write-Part "Or download the installer yourself: $fallbackUrl" 'Red'
        }
        Write-Host ''
        $global:LASTEXITCODE = 1
    }
}

Install-TMPlayer
