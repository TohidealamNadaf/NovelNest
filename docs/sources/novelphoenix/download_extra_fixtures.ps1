$ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
$fix = "e:\novel-nest\docs\sources\novelphoenix\fixtures"

Write-Host "1. detail_lord_of_the_mysteries_completed.html"
curl.exe -s -A $ua "https://novelphoenix.com/novel/lord-of-the-mysteries" -o "$fix\detail_lord_of_the_mysteries_completed.html"
Start-Sleep -Seconds 1

Write-Host "2. chapters_shadow_slave_page_17.html"
curl.exe -s -A $ua "https://novelphoenix.com/novel/shadow-slave/chapters?page=17" -o "$fix\chapters_shadow_slave_page_17.html"
Start-Sleep -Seconds 1

Write-Host "3. chapters_shadow_slave_page_33.html"
curl.exe -s -A $ua "https://novelphoenix.com/novel/shadow-slave/chapters?page=33" -o "$fix\chapters_shadow_slave_page_33.html"
Start-Sleep -Seconds 1

Write-Host "4. chapter_2452_bonus_genetic_ascension.html"
curl.exe -s -A $ua "https://novelphoenix.com/novel/genetic-ascension/chapter-2452" -o "$fix\chapter_2452_bonus_genetic_ascension.html"
Start-Sleep -Seconds 1

Write-Host "5. chapter_1432_last_lotm.html"
curl.exe -s -A $ua "https://novelphoenix.com/novel/lord-of-the-mysteries/chapter-1432" -o "$fix\chapter_1432_last_lotm.html"
Start-Sleep -Seconds 1

Write-Host "6. search_live_zero_results.json"
curl.exe -s -A $ua "https://novelphoenix.com/ajax/searchLive?keyword=xyznonexistentnoveltitlesupercalifragilistic&type=title" -H "X-Requested-With: XMLHttpRequest" -o "$fix\search_live_zero_results.json"
Start-Sleep -Seconds 1

Write-Host "7. search_live_short_keyword.json"
curl.exe -s -A $ua "https://novelphoenix.com/ajax/searchLive?keyword=ab&type=title" -H "X-Requested-With: XMLHttpRequest" -o "$fix\search_live_short_keyword.json"
Start-Sleep -Seconds 1

Write-Host "8. response_404.html"
curl.exe -s -i -A $ua "https://novelphoenix.com/novel/this-novel-absolutely-does-not-exist-404" -o "$fix\response_404.html"
Start-Sleep -Seconds 1

Write-Host "9. response_403_cloudflare.html"
curl.exe -s -i -A $ua "https://novelphoenix.com/sitemap.xml" -o "$fix\response_403_cloudflare.html"

Write-Host "DONE ALL"
