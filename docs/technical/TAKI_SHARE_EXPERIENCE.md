# Taki share experience: the Now Playing story card (issue #20)

## 1. Product objective

Let someone share the music they are listening to as one beautiful, ready-made image, without
knowing anything about servers or streaming. One tap on Now Playing, a preview, the Android
Sharesheet. The card is rendered on the device: no account, no service, no template picker.

## 2. Entry point

A **Share** icon in the quiet utility row under the transport controls of Now Playing
(`NowPlayingUtilityRow`), between Lyrics and Sleep timer. It is icon-only like its neighbours, keeps
a 48 dp target, has the content description "Share" (localized, English and Spanish) and is disabled
when there is no playable track. It is the only Share action (the overflow menu was not touched), so
there is no duplicate, and artwork prominence and every existing control are unchanged. This
intentional visible change re-recorded exactly four Roborazzi goldens (`now_playing_standard`,
`now_playing_buffering`, `now_playing_paused`, `s320_now_playing`).

## 3. Interaction flow

```
Now Playing -> tap Share -> card generated from a snapshot of the current track
  -> preview sheet (real 9:16 card + one Share button) -> tap Share -> Android Sharesheet
  -> receiving app gets a PNG
```

- Back, the scrim or the host dismisses the sheet; nothing is launched by dismissing.
- No "shared!" message: Taki cannot know whether the user finished sharing in the other app.
- Playback is never touched: no seek, pause, prepare, queue or controller call (the share code has no
  player dependency; it reads a `PlayerUiState` snapshot).

## 4. Card visual specification (1080 x 1920 px, PNG)

Everything is in `ShareCardSpec` (`ui/share/ShareCardRenderer.kt`).

- **Atmosphere**: a portrait crop of the cover reduced by repeated halving, box-blurred (3 passes) and
  upscaled in smooth steps, saturation 1.3, under a vertical black scrim (alpha 0.22 -> 0.42 -> 0.70)
  that keeps text readable on light covers without flattening the colours.
- **Cover**: 780 x 780 square, centre-cropped (never stretched), 24 px corners, a 2 px 10 % ivory
  hairline, drawn on an opaque base so transparent covers cannot show the atmosphere through.
- **Text** (left-aligned with the cover edge, system sans-serif like the app): title Medium 68 px, up to
  2 lines, shrinking to 46 px, then ellipsized; artist Regular 46 px, up to 2 lines, 86 % ivory; album
  Regular 38 px, 1 line, 66 % ivory, only when it adds information (omitted when it repeats the
  title). Balanced breaking, no hyphenation, long unbroken words break by character.
- **Taki mark**: the existing primary logo (`docs/graphic resources/Taki_Logo_Primary.svg`) redrawn at
  0.26 scale, centred, 92 % opacity. The wordmark sits slightly lower and right of the SVG so the "k"
  clears the arm of the "t" at this size; the three symbol shapes are unioned into one path so the
  translucent colour does not double-blend.
- **Layout**: the cover + text block is centred in the Stories-safe zone (top 250 px reserved,
  everything above y = 1470, the mark at y = 1520..1582, the bottom 320 px left empty). A guideline,
  not a platform guarantee.
- **Missing artwork**: Taki's own palette (olive-to-black gradient, dark tile with the accent "t"), same
  layout, still a finished card.
- **Never on the card**: server, URL, account, ids, paths, bitrate/codec, "Navidrome", "Subsonic".

## 5. Rendering implementation

Pure `android.graphics.Canvas`, chosen over capturing Compose because it is deterministic, density
independent, needs no on-screen composition, and is unit-testable without a device. `ShareCardRenderer`
exposes `layout()` (the geometry, asserted by tests) and `render()` (a 1080 x 1920 ARGB_8888 bitmap, the
only large allocation; the rest are <= 432 x 768 working bitmaps that are recycled). The rendered
bitmap is **both the preview and the exported PNG**, so the two cannot diverge.

## 6. Artwork handling

`loadShareArtwork` runs the same `CoverArtRequest` Now Playing already displays through the app's
Coil image loader (normally a cache hit, no extra request), software bitmap, max 1000 px on a side
(the cover is drawn at 780 px, so it is never upscaled). It is bounded by a 6 s timeout and any
failure or timeout simply selects the fallback tile; sharing is never blocked on artwork.

## 7. Metadata and typography rules

`ShareCardContent.from` cleans server text: HTML entities and tags decoded, control characters and
repeated whitespace removed; title required (no title, no share), blank artist/album omitted, album
omitted when equal to the title. Unicode, accents and non-Latin scripts are passed through unchanged.

## 8. Android Sharesheet integration

`ACTION_SEND`, MIME `image/png`, the content URI as `EXTRA_STREAM` and as `ClipData`,
`FLAG_GRANT_READ_URI_PERMISSION` on both intents, wrapped in `Intent.createChooser`. No `file://`, no
storage permission. A missing receiver is caught and logged, never crashes. A second tap within 1.5 s
is the same tap (no second chooser).

## 9. FileProvider and temporary cache

A new `androidx.core.content.FileProvider` (`${applicationId}.shareprovider`, not exported,
`grantUriPermissions`), configured by `res/xml/share_file_paths.xml` with exactly one root:
`<cache-path path="share/">`. Files are `cache/share/taki-share-<timestamp>-<n>.png`. Cleanup is
bounded, not immediate (the receiver may still be reading): each write keeps at most **3** files and
drops anything older than 24 h; the view model also prunes when cleared.

## 10. Security and privacy

The image contains only title, artist, album and the cover. The provider exposes one cache
subdirectory only (asserted by test). File names carry no track data. Nothing is sent anywhere by
Taki: the user chooses the receiving app.

## 11. Error handling

Artwork missing, failing or slow -> fallback tile. Render/encode failure (including out of memory) ->
the sheet shows a short message and **Try again**; Now Playing and audio are unaffected. Share is
disabled until a card is ready. A stale result from an earlier open is dropped (generation counter).

## 12. Accessibility

Share icon with a content description and 48 dp target; the preview is an image with the description
"Preview of the share card: Title - Artist"; the error message is a polite live region; the sheet
scrolls when a large system font pushes content down; Back works; the primary button is a real
button with an enabled state. On the Pixel at font scale 2.0 and 1.3 the sheet and button stayed
usable (the exported card is a fixed bitmap, by design).

## 13. Pixel 7 validation matrix (OBSERVED unless stated)

| Case | Result |
|---|---|
| Normal square artwork (Stromae "Cheese", Héroes del Silencio) | OBSERVED, exports inspected |
| Dark artwork (Greta Van Fleet) | OBSERVED, real 1080x1920 export inspected |
| Bright / saturated (Tom Misch yellow, purple Senda'91, near-white Cheese) | OBSERVED |
| Long album name / accents / apostrophe ("Senda'91 Remastered + Bonus Tracks", "Héroes") | OBSERVED, fits one line |
| Very long title / artist, ellipsis | **Renderer-level only**: 1080x1920 exports from the unit renderer inspected; no such real track on the device |
| Missing artwork | **Renderer-level only** (export inspected, unit and view-model tests); not exercised on the device |
| Track changes while the preview is open | OBSERVED: the preview kept the chosen track; reopening showed the new one |
| Rapid open/close x8, tapping Share repeatedly | OBSERVED: no crash, cache stayed at 3 files, one chooser |
| Share while playing / while paused | OBSERVED: position advanced continuously (146.7 s -> 187.2 s) while preview and Sharesheet were open; paused stayed paused at 0:33; no seek/prepare/controller log lines |
| Return from the Sharesheet | OBSERVED: back in Taki, same queue and position |
| Rotation | The app stays portrait even with rotation forced; a font-scale change did not recreate the activity and the sheet survived. A true activity recreation was not exercised (the state lives in a ViewModel by design) |
| Export size | `taki-share-*.png` decoded as 1080 x 1920 |

## 14. External sharing compatibility

The standard Sharesheet opened with the card as "Sharing image" and listed WhatsApp, Messages,
Gmail, Instagram and others. **Instagram** (installed) offered Messages / Feed / **Stories**; choosing
Stories opened Instagram's story composer with the card full-screen and correctly composed; I
discarded it and **published nothing**. WhatsApp and Messages were present in the chooser but I did not
send to a contact, so image transfer to them was not exercised. No Instagram SDK or account
integration is used.

## 15. Automated tests

53 new tests: content cleaning and metadata rules; renderer (dimensions, opacity, sharp artwork, crop not
stretch, transparent artwork, fallback, long/unbroken/non-Latin text bounded in the safe zone, no
overlap, brand placement, determinism, input bitmap untouched); store (PNG decode, safe names,
bounded and age pruning, intent shape, provider config exposes only `cache/share`, provider registered
non-exported); view model (snapshot stability, duplicate open, artwork null/throw/timeout, render
failure then retry, out of memory, dismiss/late-result, stale-result drop, launch guard); Compose (all
sheet states, 9:16 preview, scrim and Back dismissal, Share/Retry callbacks); Now Playing (Share
fires its action, disabled without a track, 48 dp target); the sheet joined `SheetInteractionAuditTest`.
Gates: 1680 tests, 0 failures, 0 skipped; detekt 42 / 1.

## 16. Known limitations

- Very long titles/artists and the missing-artwork fallback were not seen on the device (renderer exports
  and tests only).
- WhatsApp/Messages transfer not exercised; Instagram verified up to the composer, deliberately not
  posted.
- The `FileProvider` URI mapping itself cannot be unit-tested on a Windows host (androidx path matching),
  so it is validated on the device (the share worked) and its configuration by test.
- One fixed design; the atmosphere samples the centre of the cover, so a cover with strong left/right
  contrast produces a banded atmosphere (as with the Tom Misch cover) rather than an average tint.

## 17. Future improvements (not started)

Sharing from other screens, an optional "Listen on Taki" call to action, a lyric line, and a user-chosen
accent. None were implemented and none are needed for the feature to be complete.
