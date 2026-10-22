# NovelPhoenix Fixture Refresh Script
# Respects the site by sending realistic User-Agent headers, spacing requests >= 1.5 seconds, and caching files locally.

$ErrorActionPreference = "Stop"
$ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$fixDir = Join-Path $scriptDir "fixtures"

if (!(Test-Path $fixDir)) {
    New-Item -ItemType Directory -Path $fixDir | Out-Null
}

function Download-Polite {
    param (
        [string]$Url,
        [string]$OutputFile,
        [hashtable]$Headers = @{}
    )

    Write-Host "Fetching: $Url -> $(Split-Path $OutputFile -Leaf)"
    $args = @("-s", "-A", $ua, "$Url", "-o", "$OutputFile")
    foreach ($k in $Headers.Keys) {
        $args += @("-H", "$k`: $($Headers[$k])")
    }

    & curl.exe @args
    Start-Sleep -Milliseconds 1500
}

Write-Host "--- Refreshing NovelPhoenix Test Fixtures ---"

# 1. Detail pages
Download-Polite "https://novelphoenix.com/novel/genetic-ascension" "$fixDir\detail_genetic_ascension.html"
Download-Polite "https://novelphoenix.com/novel/shadow-slave" "$fixDir\detail_shadow_slave.html"
Download-Polite "https://novelphoenix.com/novel/lord-of-the-mysteries" "$fixDir\detail_lord_of_the_mysteries_completed.html"
Download-Polite "https://novelphoenix.com/novel/kill-the-sun" "$fixDir\detail_kill_the_sun_no_tags.html"

# 2. Chapter list pages
Download-Polite "https://novelphoenix.com/novel/genetic-ascension/chapters?page=1" "$fixDir\chapters_genetic_ascension.html"
Download-Polite "https://novelphoenix.com/novel/shadow-slave/chapters?page=1" "$fixDir\chapters_shadow_slave.html"
Download-Polite "https://novelphoenix.com/novel/shadow-slave/chapters?page=17" "$fixDir\chapters_shadow_slave_page_17.html"
Download-Polite "https://novelphoenix.com/novel/shadow-slave/chapters?page=33" "$fixDir\chapters_shadow_slave_page_33.html"

# 3. Chapter reading pages
Download-Polite "https://novelphoenix.com/novel/genetic-ascension/chapter-1" "$fixDir\chapter_1_genetic_ascension.html"
Download-Polite "https://novelphoenix.com/novel/shadow-slave/chapter-1" "$fixDir\chapter_1_shadow_slave.html"
Download-Polite "https://novelphoenix.com/novel/genetic-ascension/chapter-2451" "$fixDir\chapter_2451_bonus_genetic_ascension.html"
Download-Polite "https://novelphoenix.com/novel/lord-of-the-mysteries/chapter-1432" "$fixDir\chapter_1432_last_lotm.html"

# 4. Search endpoints
Download-Polite "https://novelphoenix.com/ajax/searchLive?keyword=shadow&type=title" "$fixDir\search_live_shadow.json" @{ "X-Requested-With" = "XMLHttpRequest" }
Download-Polite "https://novelphoenix.com/ajax/searchLive?keyword=ab&type=title" "$fixDir\search_live_short_keyword.json" @{ "X-Requested-With" = "XMLHttpRequest" }
Download-Polite "https://novelphoenix.com/ajax/searchLive?keyword=xyznonexistentnoveltitlesupercalifragilistic&type=title" "$fixDir\search_live_zero_results.json" @{ "X-Requested-With" = "XMLHttpRequest" }
Download-Polite "https://novelphoenix.com/search?keyword=shadow&type=title" "$fixDir\search_page_shadow.html"

# 5. Feeds & browse
Download-Polite "https://novelphoenix.com/ranking" "$fixDir\ranking.html"
Download-Polite "https://novelphoenix.com/genre-all/sort-new/status-all/all-novel" "$fixDir\browse_new.html"

Write-Host "--- Successfully Refreshed All Fixtures ---"
