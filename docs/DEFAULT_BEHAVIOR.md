# Default behavior

This document records Taki's user-visible defaults and automatic behavior. It is intended to make implicit behavior explicit, especially when Taki communicates with a Navidrome or OpenSubsonic-compatible server without requiring a separate setting.

The guiding principle is simple: **prefer sensible defaults over configuration, while avoiding surprising or costly behavior.**

## Server playback reporting

When Taki is online, playback activity is reported to the connected server using the Subsonic/OpenSubsonic scrobble API.

- Starting a track reports **Now Playing**.
- A completed qualifying playback is reported as a **scrobble/play submission**.
- Taki does not directly configure or manage external scrobbling services such as Last.fm. If the server forwards playback activity to another service, that is controlled by the server administrator.
- Offline playback cannot be reported to the server while no connection is available.

This behavior keeps server-side play counts, recently played information, and compatible recommendation features useful without exposing a separate "scrobbling" concept in the everyday Taki interface.

## Streaming quality

| Connection | Default |
| --- | --- |
| Mobile data | Maximum 256 kbps |
| Wi-Fi | No client bitrate limit |

A server may still transcode or otherwise alter a stream according to its own configuration and capabilities.

## Downloads

| Setting | Default |
| --- | --- |
| Download bitrate | No client bitrate limit / original quality when the server provides it |
| Require Wi-Fi for downloads | Off |

Explicit downloads are intended for offline listening. The temporary streaming cache and explicit downloads are separate concepts.

> **Data usage note:** because "Require Wi-Fi for downloads" is currently off by default, starting a download while on mobile data may use the mobile connection.

## Temporary music cache

| Setting | Default |
| --- | --- |
| Music cache size shown in Settings | 500 MB |

Taki cleans temporary playback-cache files as needed. Explicit user downloads are not treated as disposable streaming-cache files.

## Audio

| Setting | Default |
| --- | --- |
| ReplayGain | Disabled |
| Hardware audio offload | Disabled |

These options are available under Advanced settings for users who need them; normal playback does not require configuration.

## Library metadata

| Setting | Default |
| --- | --- |
| Use ID3 tags | On |
| Use ID3 tags offline | On |

Taki works best with a well-curated library. Metadata organization and library complexity are expected to live primarily on the server/library side so the player can remain simple.

## Bluetooth audio behavior

For Bluetooth A2DP audio devices:

- connecting an audio device requests playback/resume;
- disconnecting an audio device requests pause.

This is automatic behavior rather than a user-facing preference in the current beta.

## Language and diagnostics

| Setting | Default |
| --- | --- |
| Language override | System language |
| Debug logging to file | Off |
| Confirmation dialog | Off |

## Phone orientation

The current phone interface is **portrait-only**. Android Auto is not affected because the car interface is rendered by Android Auto rather than by Taki's phone activity.

## What belongs here

When adding or changing a feature, update this document if the change introduces any of the following:

- an action Taki performs automatically;
- a network request or server-side state update caused by normal listening;
- a default that can affect mobile data, storage, battery, or playback behavior;
- a setting whose default materially changes the listening experience.

The purpose of this document is not to list every feature. It is to make Taki's **implicit behavior predictable**.
