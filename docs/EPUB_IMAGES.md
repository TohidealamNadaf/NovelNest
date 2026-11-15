# EPUB illustration viewing

## Using the controls

- Tap an illustration to open the viewer. Alternatively use the reader's controls -> Image Alignment -> Open illustrations in this section.
- Choose Fit screen, Fit width, Fit height or Original size. Original size maps one intrinsic image pixel to one device pixel; it does not upscale the image to fill the screen.
- Pinch to zoom, double-tap to zoom/reset, and drag to pan. Zoom in/out buttons provide an alternative to gesture input. Selecting a fit mode resets pan/zoom.
- Use Previous/Next to inspect other illustrations in the current EPUB section, in document order. An SVG wrapper and its embedded image count as one illustration.
- Full-screen hides controls and system bars. Tap the image or the floating restore button to show controls. Close or Android Back returns to the unchanged reader position.
- Background choices: Dark, Black, White and Parchment. Fit/background choices are remembered independently of chapter layout.

## Several images on one page

The former illustration CSS assigned every image a full viewport height, including pages containing several images. Center alignment then created large apparent blank gaps between images.

Image Alignment now has a multi-image layout choice:

- **Compact flow** (default): image-only sections with several illustrations use their natural aspect ratios and an 8-CSS-pixel gap, without giving every image a viewport-sized box. Images fill the available width and follow document order.
- **One image per page**: retains the deliberate viewport-sized presentation for each illustration.

Single illustrations retain existing vertical/horizontal alignment. Mixed text/illustration sections retain their normal document flow. The static sanitizer and live settings update use the same top-level image/SVG counting rule; nested SVG image elements are not counted twice.

## Ownership, performance and lifecycle

- EpubReaderViewModel owns the section catalog, a single current asset, cancellation and generation guards. Repeated image changes cancel older work; a mutex prevents overlapping local resource reads. Late results cannot replace a newly selected image or reopen a closed viewer.
- EPUB parsing, archive reads, image bounds checks and SVG preparation run on the IO dispatcher. Opening the viewer does not open a second Publication or decode a publication-wide gallery.
- A separate platform WebView renders a trusted static viewer document. Its main HTML document and current image are served from memory at two exact URLs on Android's reserved in-app HTTPS origin. Sanitized SVG wrappers may also load bounded embedded raster data URLs. It does not render publication chapter HTML or use a new image library.
- Pan/zoom updates use transforms with one scheduled animation frame only when input changes. There is no continuously running frame loop or Compose progress polling.
- Closing the viewer cancels pending reads, clears asset/catalog state, removes the JavaScript interface and destroys the viewer WebView. It does not clear global WebView caches or pause global WebView timers, which could affect Readium.
- Rotation can reuse ViewModel-owned metadata/current bytes; the new viewer starts with a fresh fit transform. Process recreation restores the reader normally, with the gallery closed. Backgrounding pauses this WebView's rendering; no background image prefetch is scheduled.
- Reader checkpoints and chapter navigation remain owned by the existing Readium navigator. Viewer image navigation never updates the book locator. Publication cleanup waits for an active image resource read to release its handle.

## Boundaries and errors

- Raster formats: JPEG, PNG, WebP, GIF and BMP, subject to platform decoder support. Raw image data is capped at 12 MiB and 16 megapixels. Bounds are inspected before rendering, without decoding a bitmap on the app's main thread.
- SVG input is capped at 4 MiB. Static vectors and local raster image wrappers are supported. Up to 16 nested raster references may be embedded, with a 6-MiB combined source-data limit and 12-MiB final output limit. SVG dimensions are bounded to 16 megapixels.
- Publisher scripts, event handlers, foreign objects, animation elements and external style sheets are removed from viewer SVGs. Common case-sensitive SVG names, including viewBox and gradient names, are restored after HTML parsing. Unsupported nested SVG references or resources requiring external CSS/fonts can render differently or show an explicit error.
- Only declared local publication resources and bounded raster data URLs may be loaded. Remote, file/content URL and undeclared path access is rejected. The viewer blocks network/file/content access and denies all resource requests except the fixed viewer document, current intercepted image, and bounded raster data URLs inside sanitized SVG images. Original EPUB files are not modified.
- A section gallery supports up to 256 top-level illustrations. Larger sections show an explicit limit message rather than silently omitting entries. Normal chapter rendering retains the existing markup/archive limits.
- Missing, unsupported or oversized resources show an error with Retry. Empty sections show an empty state. Native image loading errors, renderer failure and a 15-second foreground local rendering timeout show a render error; Previous/Next and Close remain available. There is no automatic retry loop.

## Verification

Debug Kotlin compilation and debug assembly are checked during implementation. No automated tests, physical-device gesture exercise or thermal profiling were requested for this change. Check illustrated EPUBs on the target phone before release: ordinary images, SVG wrappers, several images in one section, every fit/background mode, pinch/double-tap/pan, rapid image switching, close during load, rotation, background/restore and missing/oversized resources. Complex SVG/WebView behavior can vary with the device's installed Android System WebView.


## Local viewer document loading fix

The previous interceptor returned HTTP 403 for everything except the current image.
The main document was supplied through loadDataWithBaseURL, whose internal data:
request can also reach shouldInterceptRequest. Rejecting that request produces the
WebView error page seen in the report (ERR_HTTP_RESPONSE_CODE_FAILURE). This is a
viewer routing problem, independent of reader notes and the book's network source.

The viewer now loads an explicit viewer.html URL on the reserved
https://appassets.androidplatform.net/epub-image/ origin. Only its main-frame GET
and the current image's subresource GET are answered from memory. No server, DNS,
file access, or additional dependency is required; network loads remain blocked.
Embedded raster data URLs are allowed only as subresources for a validated SVG,
with a URL length bound matching the sanitizer's 6-MiB embedded-data limit.

Main-document/image load errors and HTTP errors now switch immediately to the
existing render-error card instead of leaving the native error page under a
spinner until the 15-second watchdog expires. The watchdog still covers missing
JavaScript/image readiness events. Renderer Retry creates a fresh viewer using the
already validated asset; archive read failures retain their separate reload path.

Implementation checks: offline debug assembly and diff review. No automated tests
or physical-phone reproduction were run for this fix; confirm the reported EPUB,
ordinary raster images, SVG wrappers, next/previous, fit/zoom controls, backgrounding,
and Retry on the device's installed Android System WebView.

Android references: [loading in-app content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content),
[WebViewClient interception](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)).
