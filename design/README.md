# iTube · Liquid Glass refresh

The interface uses pearl glass, rose and blue background light, large editorial titles, inset thumbnail cards, a full-width search capsule and a floating navigation lens. Light and dark modes share the same layout and retain the system theme setting. Shared glass changes also update player controls, sheets, settings and library surfaces. Background color is drawn behind content, never over video playback.

## Shorts behavior

- Standard paged feeds (Home charts and For you, search, signed-in subscriptions/account lists, channels and playlists) exclude identified Shorts on every page.
- Local subscriptions, recent/history lists, saved playlists and regular playback queues use the same policy. Filtering does not delete stored videos or history.
- Channel Shorts tabs are removed; Shorts discovery and playback use the main Shorts section.
- Explicit YouTube `/shorts/` links open that section with the requested Short first, even if feed loading fails. Normal videos remain in the normal player.
- Shorts are recognized through extractor flags, explicit Shorts URLs and Innertube Shorts endpoints/overlays. Duration alone is not used: a normal 45-second video remains a normal video. Sources that omit all Shorts metadata cannot be classified reliably.
- The dedicated Shorts feed still collects subscription Shorts and searches for Shorts. Ordinary autoplay/next queues exclude Shorts.
- Downloads remain a file manager: existing downloaded files stay accessible, playable, shareable and removable. No files, subscriptions, account data or signing settings are changed.

## Figma

File created: https://www.figma.com/design/wmiqmju6W5V3ZsNjL6RqUs

The account's Figma MCP Collab-seat limit blocks library reads and canvas edits. **These screens have not been written to the Figma canvas.**

To import them manually, open that file in Figma, run the Scripter plugin, paste all of `figma-liquid-glass.js`, then run once. It creates a new `iTube · Liquid Glass` page containing four editable mobile references (Home light, Home dark, Shorts and Library), 14 shared components and eight color variables with Light/Dark modes. Text, vector icons, cards and hierarchy stay editable; no flattened UI screenshots are imported. Repeat imports create additional pages.

`liquid-glass-reference.png` and `.svg` are design references rendered from the same scene data, not Android screenshots. Thumbnails use illustrative vector artwork; the app continues loading the actual YouTube thumbnails. Native text uses the Android system font; the import uses Roboto when available, with an Inter fallback reported by the import result. Icons are Google's rounded Material SVG assets, matching the app's icon family. Google Material Design icons are available under Apache 2.0: https://github.com/google/material-design-icons/blob/master/LICENSE

Regenerate references with `node design/build-reference.cjs`. PNG conversion uses Sharp. Run `node design/check-import.cjs` for the local mock structural audit; this does not replace a real Figma import or screenshot check.

## Checks

`python3 scripts/check-shorts-policy.py` compiles the production policy and extracted production model/metadata helpers using the Kotlin compiler included with a downloaded Gradle distribution. It checks 29 classification, URL, JSON metadata, channel/playlist retention, non-destructive filtering and paged continuation cases. The harness omits unrelated Android/network dependencies; it is not an instrumented app test.

Android Kotlin compilation passed with `./gradlew :app:clean :app:compileDebugKotlin -Pkotlin.incremental=false`. A prior incremental retry reported duplicate cached declarations; the clean non-incremental compile passed. No build configuration was changed.

Publishing status is recorded in the package's `APPLY.md`. Device testing remains necessary for streaming, scrolling/pagination, deep links, Shorts swipes, theme switching, player controls, background play, PiP, pop-up playback and downloads.
