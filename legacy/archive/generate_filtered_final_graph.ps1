# --- Configuration ---
$currentRepoPath = $PSScriptRoot
$remoteRepoUrl = "https://github.com/tpov/SchoolQuiz.git"
$tempRepoFolderName = "temp_schoolquiz_repo_for_stats_v5"
$tempRepoPath = Join-Path $currentRepoPath $tempRepoFolderName
$outputHtmlFile = "productivity_graph.html"
$wmaWindowSize = 7 

# --- Function to get daily stats from a repo (Unchanged) ---
function Get-DailyGitStats {
    param([string]$RepoPath)
    Push-Location $RepoPath
    try {
        $gitLogOutput = git log --all --numstat --pretty="format:---%n%ad" --date=iso -- . ':(exclude)gradle' ':(exclude)build-logic' ':(exclude)functions' ':(exclude)*/schemas' ':(exclude)*.json' ':(exclude)app/src/main/assets'
        $commitData = @()
        $currentCommit = $null
        $gitLogOutput | ForEach-Object {
            $line = $_
            if ($line.StartsWith('---')) {
                if ($currentCommit -and $currentCommit.DateString) { $commitData += $currentCommit }
                $currentCommit = [pscustomobject]@{ DateString = ''; AddedLines = 0 }
            }
            elseif ($currentCommit -ne $null -and !$currentCommit.DateString) { $currentCommit.DateString = $line }
            elseif ($currentCommit -ne $null) {
                $stats = $line -split '\s+'; if ($stats.Length -ge 1 -and $stats[0] -ne '-') { try { $currentCommit.AddedLines += [int]$stats[0] } catch {} }
            }
        }
        if ($currentCommit -and $currentCommit.DateString) { $commitData += $currentCommit }
        return $commitData | ForEach-Object { try { $datePart = $_.DateString.Split(' ')[0]; [pscustomobject]@{ Date = [datetime]::ParseExact($datePart, 'yyyy-MM-dd', $null); AddedLines = $_.AddedLines } } catch {} } | Where-Object { $_ -ne $null } | Group-Object { $_.Date.Date } | Select-Object @{ Name = "Date"; Expression = { [datetime]$_.Name } }, @{ Name = "TotalLines"; Expression = { ($_.Group | Measure-Object -Property AddedLines -Sum).Sum } } | Sort-Object Date
    } finally { Pop-Location }
}

# --- Main Logic ---
try {
    # 1. Get and combine all stats
    Write-Host "Cloning remote repository..."
    if (Test-Path $tempRepoPath -PathType Container) { Remove-Item -Recurse -Force $tempRepoPath }
    git clone --quiet --depth 2000 $remoteRepoUrl $tempRepoPath
    
    $statsCurrent = Get-DailyGitStats -RepoPath $currentRepoPath
    $statsRemote = Get-DailyGitStats -RepoPath $tempRepoPath
    $mergedDailyStats = ($statsCurrent + $statsRemote) | Group-Object { $_.Date.Date } | Select-Object @{ Name = "Date"; Expression = { [datetime]$_.Name } }, @{ Name = "TotalLines"; Expression = { ($_.Group | Measure-Object -Property TotalLines -Sum).Sum } } | Sort-Object Date
    
    # 2. Calculate daily rate
    $rateStats = @()
    if ($mergedDailyStats.Count -gt 1) {
        for ($i = 1; $i -lt $mergedDailyStats.Count; $i++) {
            $currentDay = $mergedDailyStats[$i]; $previousDay = $mergedDailyStats[$i-1]
            $daysBetween = ($currentDay.Date - $previousDay.Date).Days; if ($daysBetween -le 0) { $daysBetween = 1 }
            $rate = $currentDay.TotalLines / $daysBetween
            $rateStats += [pscustomobject]@{ Date = $currentDay.Date; Rate = $rate; Volume = $currentDay.TotalLines }
        }
    }

    # 3. Calculate Weighted Moving Average for the trend line
    $trendPoints = @()
    for ($i = 0; $i -lt $rateStats.Count; $i++) {
        $window = if ($i -ge ($wmaWindowSize - 1)) { $rateStats[($i - $wmaWindowSize + 1)..$i] } else { $rateStats[0..$i] }
        $weightedSum = 0; $weightSum = 0
        for ($j = 0; $j -lt $window.Count; $j++) {
            $weight = $j + 1; $weightedSum += $window[$j].Rate * $weight; $weightSum += $weight
        }
        $wma = if ($weightSum -gt 0) { $weightedSum / $weightSum } else { 0 }
        $trendPoints += [pscustomobject]@{ Date = $rateStats[$i].Date; Trend = $wma; Volume = $rateStats[$i].Volume }
    }

    # 4. FILTER OUTLIERS to make the graph readable
    $averageTrend = ($trendPoints.Trend | Measure-Object -Average).Average
    $threshold = $averageTrend * 15 # A threshold to cut off extreme initial commits
    $filteredTrendPoints = $trendPoints | Where-Object { $_.Trend -lt $threshold }

    # 5. Format data for JavaScript
    $maxVolume = ($filteredTrendPoints.Volume | Measure-Object -Maximum).Maximum
    $minRadius = 3; $maxRadius = 15

    $lineData = $filteredTrendPoints | ForEach-Object { @{x = $_.Date.ToString("yyyy-MM-dd"); y = [math]::Round($_.Trend, 2)} } | ConvertTo-Json -Compress
    $radiiData = $filteredTrendPoints | ForEach-Object { if ($maxVolume -gt 0) { $minRadius + ($_.Volume / $maxVolume) * ($maxRadius - $minRadius) } else { $minRadius } } | ConvertTo-Json -Compress

    # 6. Generate final HTML
    $htmlContent = @"
<!DOCTYPE html><html><head><title>Productivity Trend & Volume (Filtered)</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@3.7.1/dist/chart.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@2.0.0/dist/chartjs-adapter-date-fns.bundle.min.js"></script>
    <style>body{font-family:sans-serif;padding:20px}.chart-container{position:relative;height:60vh;width:90vw}</style>
</head><body><h1>Productivity Analysis: Trend and Volume (Filtered)</h1><div class="chart-container"><canvas id="myChart"></canvas></div>
<script>
const ctx = document.getElementById('myChart').getContext('2d');
new Chart(ctx, {
    type: 'line',
    data: {
        datasets: [{
            label: 'Productivity Trend',
            data: $lineData,
            borderColor: 'rgba(75, 192, 192, 0.8)',
            backgroundColor: 'rgba(75, 192, 192, 0.5)',
            tension: 0.4,
            pointRadius: $radiiData,
            pointHoverRadius: $radiiData,
        }]
    },
    options: {
        scales: {
            x: { type: 'time', time: { unit: 'month' }, title: { display: true, text: 'Date' }},
            y: { beginAtZero: true, title: { display: true, text: 'Productivity Trend (Weighted Avg Lines/Day)' }}
        },
        maintainAspectRatio: false
    }
});
</script></body></html>
"@
    $htmlContent | Out-File -FilePath $outputHtmlFile -Encoding utf8
    Write-Output "Generated final, filtered 'Trend and Volume' analysis. Please open '$outputHtmlFile'."
}
catch { Write-Error "An error occurred: $_" }
finally {
    # 7. Clean up
    if (Test-Path $tempRepoPath -PathType Container) { Remove-Item -Recurse -Force $tempRepoPath }
} 