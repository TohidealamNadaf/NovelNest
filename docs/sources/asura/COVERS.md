# Asura poster transport follow-up

Verified **15 November 2026** using live public Asura pages and Android Studio's
connected emulator. This is a poster fix; it does not revise the older chapter,
reader-state or metadata test results in VERIFICATION.md.

## Confirmed cause

The homepage still exposes supported HeroCarouselEmbla, TrendingSection,
LatestUpdates and PopularSidebar Astro data. Covers retain their HTTPS
cdn.asurascans.com/asura-images/covers/... URLs. Sampled public covers returned
HTTP 200 and genuine WebP data from the development host. The public feed also
contains an original GIF for A Dragonslayer's Peerless Regression; this alone
cannot explain the widespread missing posters.

The failure was reproduced inside the emulator. Metadata and ratings loaded, while
Coil raised HttpException: HTTP 403 for many valid cover URLs, including Cyberpunk
Chivalry, Damn Reincarnation, Murim Login and Logging Out Is Too Hard. The image
cache initially contained only its journal. This was an HTTP rejection before
image decoding, not evidence of missing HTML selectors or invalid chapter routing.
The earlier generic image loader provided a browser User-Agent but no Asura
Referer or image Accept context. The source's chapter-image downloader already
provided its own source headers, but the independent Coil poster path did not.

## Fix and ownership

- AsuraCoverInterceptor adds Referer https://asurascans.com/, the existing source
  User-Agent and an image Accept header to known Asura poster/banner URLs.
- Matching is limited to HTTPS, normal TLS port, no URL credentials, existing
  Asura image hosts and /asura-images/covers/ or /asura-images/banners/ paths.
  Other direct image requests pass through unchanged.
- NovelNestApplication installs this adapter once on Coil's image client. Its
  newBuilder shares the existing connection pool, dispatcher and cache owner.
  Discover cards, the hero carousel, detail poster/backdrop, Browse, Search and
  bookmarked covers all use that shared loader rather than separate per-screen fixes.
- Metadata parsers, URL identities, bookmarks, reading progress, chapter order,
  region-decoded chapter images and the novel-source transports are unchanged.
- Existing Coil memory/disk caches and URL keys are retained. No cache clearing,
  cache-busting query, extra metadata request, second image request, or HTTP retry
  cascade is added by this fix.
- Debug builds report Asura image failures and HTTP response classification under
  the AsuraCover log tag. Release builds do not emit these diagnostics. No API
  credential, cookie injection or challenge solver is introduced.

A continuing 403/429 from the provider can still prevent loading; source headers
cannot guarantee access against future access-policy or network changes. Do not
replace blocked images with unrelated covers or repeatedly request the same URL
in a retry loop. An access-policy change and an HTML contract change require
separate evidence.

## Actual verification

- Offline assembleDebug succeeded after the fix.
- Installed as an emulator update with app data preserved; no cache was cleared.
- Before: repeated Coil HTTP 403 failures and blank poster areas.
- After: visual confirmation of hero, Trending, Popular and Recently Released
  images, including cards which had previously failed.
- Opened Damn Reincarnation from Discover: its detail poster and full header
  background both rendered alongside the real 83-chapter list.
- Coil's image cache populated after the header change. The fixed app process
  produced no AsuraCover errors during these checks.
- No new automated tests were added or run. No physical Samsung-device result,
  permanent provider access or exhaustive every-title result is claimed.

Local evidence (ignored build diagnostics, not production fixtures):
- build/diagnostics/asura-covers/home.html and sections.json
- build/diagnostics/asura-covers/probes.json, comic-probes.json and app-header-probes.json
- build/diagnostics/asura-covers/before.log and after.log
- build/diagnostics/asura-covers/emulator.png (before), after.png (Discover),
  detail-after.png and sections-after.png
- build/diagnostics/asura-covers/build.log

Primary live page: https://asurascans.com/

## Maintaining this path

Change cover request headers or scope in AsuraCoverInterceptor, and origin policy
in AsuraUrls, after checking the current source. Do not patch headers independently
in NovelCard, HeroCarousel or NovelDetailScreen. Request/decoder diagnostics should
be consulted before changing selectors or adding image-format dependencies.

Final packaging check: offline assembleDebug completed successfully in 8 seconds
(all tasks up-to-date). AAPT inspection confirmed the final APK is debuggable
and has no testOnly restriction. Whitespace/diff validation passed.
