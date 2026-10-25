# Chapter notes and term collections

Reader → TT quick settings → Notes or Extract. The existing UI now uses real data; opening these screens never generates AI text.

## Notes

Notes belong to one source, novel and chapter. Tap +, enter a title and Markdown, then Save. Tap a saved title to edit it.
The database acknowledges Save before the editor returns to the list. A failed save retains the editor text and displays an error.
Manual notes work without provider credentials or connectivity. Unsaved editor text survives Activity recreation; use Save before
closing the sheet or leaving the chapter. Closing the sheet deliberately releases editor data and database observers.

The Android text-selection toolbar includes Bold, Italic, Strike, Code and Link while retaining clipboard/select-all actions.
The visible B/I/heading/list/quote controls format the current selection/cursor, rather than appending markup after the note.
Raw Markdown is stored losslessly. Preview uses Markwon 4.6.2 core with tables, task lists and strikethrough: CommonMark/GFM,
including paragraphs, emphasis, headings, quotes, nested lists, links, code and thematic breaks. Arbitrary dialects such as
LaTeX/Mermaid are not promised. Images are not fetched and executable HTML is not enabled.

Lists query only 240-character previews; the full note loads on selection. Limits: 160-character titles, 64,000-character
bodies and 200 notes per chapter. Device selection-menu appearance depends on Android/OEM behavior.

## Extract

1. Settings → AI Reading Tools → Extract Terms Route Ordering: choose the ordered provider/model routes. Existing API keys,
   sharing consent and cost policy apply. No paid route or separate client is introduced.
2. Reader → Extract → +: name what to find, for example `Cultivation terms`. Save creates a collection for this source/novel.
3. Send beside a collection extracts from the **original current chapter**, even while viewing an AI rewrite. Topic and chapter
   text are shared only with the configured, consented routes that are actually attempted; manual notes are never included.
4. Open the collection to see discoveries, their original chapter/paragraph and an exact evidence quote. Send in another chapter
   adds discoveries. Each term has one entry; expand its references to read earlier chapter contributions. Prior meanings/evidence are not overwritten.

EXTRACT has a dedicated terms-only prompt/schema and strict local paragraph/evidence validation. Names must appear verbatim
in the referenced source paragraph. An empty result is valid and preserves existing discoveries. Structural/evidence validation
cannot prove that an AI explanation is semantically correct.

Requests share the existing one-request gate, chunk/call/time budgets, eligibility checks, cooldowns, cancellation and fallback
policy. Extraction combines segment results locally; there is no extra model call to merge them. At most 40 terms per segment
and 320 per chapter are accepted. Rapid Send taps are ignored while busy. Backgrounding, source refresh or a chapter change
cancels outgoing work. A provider request already accepted can still consume allowance.

Extraction uses an independent input planner instead of rewrite-sized segments. Normal chapters fit one request;
Gemini/OpenRouter use up to 32,000 source bytes per segment for longer chapters. Groq preflights the whole chapter,
topic, prompt/schema/framing and full 3,072-token output reservation against the local 8,000-token budget. If it cannot fit,
an enabled extraction fallback is tried before spending on partial output. No source paragraphs are sampled or omitted.
Long local quota waits are declined: extraction waits at most ten seconds, then uses an eligible fallback or reports
an actionable size/quota error. Actual reported usage settles reservations; cancelled/ambiguous usage stays reserved.
Output remains bounded to 40 terms per request; supplying the complete source does not guarantee exhaustive AI extraction.
The prompt asks for distinct terms, concise meanings and short exact evidence to reduce repeated output.

The disposable AI cache key includes the topic as well as source, novel, chapter, source fingerprint and prompt version. Sending
the same topic/chapter again normally reuses its validated cached result. SQLite commits are atomic, append-only and idempotent
for identical evidence/meaning. Case/Unicode/whitespace normalize term identities without guessing aliases. Changed meanings,
source revisions or chapter references become additional contributions. Deleting a collection cancels its active extraction;
a late result cannot recreate it. Deletion requires confirmation and cascades only that collection's discoveries.

Limits: 100 collections per novel, topic/name up to 160 characters, 10,000 discoveries per collection. At capacity a whole append
fails with a visible message; previous data is kept. Counts are distinct term names; details retain their separate contributions.
Collections are indexed and only the selected collection's discoveries are observed. Lists are lazy; Markdown parsing and
term presentation preparation run off the main thread. No timer, background generation, new service or permission is added.

## Persistence and verification

Room schema 4 adds `chapter_notes`, `term_collections` and `term_discoveries` through migration 3→4, preserving the existing
library/progress. These are user-owned tables, independent of cached novels and bookmarks. Clearing text/audio/AI caches does
not erase them. Unknown database downgrades fail rather than silently destroying user data. Older app binaries may still have
their own destructive downgrade behavior; do not downgrade a production database without a backup. Uninstall/Clear app data
removes local notes and collections. Credentials remain excluded from Android backup, while the normal database backup policy applies.

Offline unit tests cover extraction prompts/validation, chunk merging, route fallback/consent, cache separation, duplicate/cancel
races, deterministic append identities and selection formatting. Instrumentation tests cover Room CRUD, scope isolation,
duplicate/delete races and migration 1/3→4. Device testing is still needed for OEM selection controls, keyboard sizing, rotation,
all reader themes and long-list scrolling, plus real provider extraction quality/quotas. No live credentials or inference are
used by automated tests.

Markwon documentation: https://noties.io/Markwon/docs/v4/install.html

Verified on 2026-10-24: `assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline --warning-mode fail --max-workers=1`
succeeded. Unit results: 224 total, 222 passed, 2 skipped, no failures/errors; includes 16 new extraction/merge/formatting
regressions. The device-test APK compiled, but its Room migration/persistence tests have not executed because no Android
device was connected. No live provider generation was performed.
