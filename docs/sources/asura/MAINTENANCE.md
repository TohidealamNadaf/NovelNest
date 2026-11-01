# Maintaining the Asura source

Read [SPEC.md](SPEC.md) before changing selectors or endpoints. Production code is under
`app/src/main/java/com/novelnest/app/data/remote/asura`; image storage and reader state remain separate.

## Diagnose a change

1. Reproduce with the public browser page and the opt-in `AsuraLiveTest`. Record HTTP status,
   whether it is an access/locked/challenge response, and which operation failed. Never treat
   a challenge/login HTML response as an empty chapter or search result.
2. Inspect the source's HTML props and the site's own browser request. Current contracts are
   Astro `props`, `ComicSeries` JSON-LD, and the public series/chapter JSON API. Do not hardcode
   hashed `/_astro/*.js` bundle filenames or scrape all images on a page.
3. Update the smallest owner: domain/path policy in `AsuraUrls`, semantic component/metadata
   hooks in `AsuraSelectors`, a payload transformation in `AsuraParser`, transport behavior in
   `AsuraHttp`. Keep URL/image allowlists explicit. Do not remove access/size/cancellation checks.
4. Refresh affected fixtures, inspect the diff, adjust snapshot expectations if the actual site
   data changed, and run source/reader regressions. A fixture refresh alone does not prove live access.
5. For image problems, distinguish manifest order/missing URLs from HTTP failures or decoder/UI
   failures. Use the supplied chapter Referer; preserve image query parameters and source order.
   The backend downloads original files; the UI owns tile decoding and per-image error presentation.

## Fixture refresh

`refresh_fixtures.ps1` performs public anonymous GET requests sequentially. It uses known, verified
series paths and does not unlock chapters or submit account actions. Responses are trimmed of
scripts/styles/SVG/comments while retaining Astro props, SSR DOM and JSON-LD. Images are not committed.
The script retains the recorded locked fixture unless the live response is still explicitly locked.

Chapter totals, rank/rating, featured/latest items and unlock status change over time. Review test
assertions after refreshing; do not change a valid failure into a fake success to keep tests green.

## Important regression checks

- Same stable ID and series URL from every card, search, detail, bookmark and cold-link route.
- No old sample sections or invented ratings/posters after network/parse errors.
- Full metadata synopsis, chronological chapter list, decimal/zero chapters and complete counts.
- Exact API/HTML page order, repeated page positions, query strings, original dimensions and correct Referer.
- Locked/account responses expose no preview image list as a complete chapter.
- 403/429/5xx do not cause automatic retry storms; only API 404 uses the known HTML fallback.
- Cancellation closes network bodies and partial files, releases locks and does not overwrite a newer selection.
- Image leases protect decoder files during eviction/Settings clear; release/dispose keeps memory/disk bounded.
- Existing NovelPhoenix/FreeWebNovel/reading-history/bookmark tests continue to pass.

## Limits and handoff

See [UI_AGENT_PROMPT.md](UI_AGENT_PROMPT.md) for the unimplemented dedicated screen and route wiring.
Source availability and access restrictions are external. Offline permanent downloads, image OCR/AI,
manhwa narration, account login and chapter unlocks are not part of this backend.
