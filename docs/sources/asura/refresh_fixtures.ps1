param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'fixtures'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$requests = [ordered]@{
    'home.html' = 'https://asurascans.com/'
    'browse.html' = 'https://asurascans.com/browse'
    'browse-page-2.html' = 'https://asurascans.com/browse?page=2'
    'fantasy.html' = 'https://asurascans.com/browse?genres=fantasy'
    'detail.html' = 'https://asurascans.com/comics/childhood-friend-of-the-zenith'
    'completed.html' = 'https://asurascans.com/comics/the-greatest-estate-developer'
    'chapter.html' = 'https://asurascans.com/comics/childhood-friend-of-the-zenith/chapter/1'
    'chapter.json' = 'https://api.asurascans.com/api/series/childhood-friend-of-the-zenith/chapters/1'
    'search.json' = 'https://api.asurascans.com/api/series?search=zenith&sort=latest&order=desc&limit=20&offset=0'
    'search-empty.json' = 'https://api.asurascans.com/api/series?search=novelnest-absent-fixture-9xyz&sort=latest&order=desc&limit=50&offset=0'
    'locked.json' = 'https://api.asurascans.com/api/series/childhood-friend-of-the-zenith/chapters/112'
}
foreach ($entry in $requests.GetEnumerator()) {
    $response = Invoke-WebRequest -Uri $entry.Value -UseBasicParsing -TimeoutSec 30
    $content = $response.Content
    if ($entry.Key -eq 'locked.json' -and !(($content | ConvertFrom-Json).data.is_locked)) {
        Write-Warning 'The recorded chapter is now public; retaining the existing locked-state fixture.'
        continue
    }
    if ($entry.Key.EndsWith('.html')) {
        $content = [regex]::Replace($content, '<script\b(?![^>]*type="application/ld\+json")[^>]*>[\s\S]*?</script>', '', 'IgnoreCase')
        $content = [regex]::Replace($content, '<style\b[^>]*>[\s\S]*?</style>|<svg\b[^>]*>[\s\S]*?</svg>|<!--[\s\S]*?-->', '', 'IgnoreCase')
    }
    [System.IO.File]::WriteAllText((Join-Path $OutputDirectory $entry.Key), $content, [System.Text.UTF8Encoding]::new($false))
    Write-Output "Captured $($entry.Key)"
    Start-Sleep -Milliseconds 600
}
