# replayGain

Taki supports replayGain for volume normalization across songs and albums. A deep dive on
replayGain is available at the [Hydrogenaudio Wiki](https://wiki.hydrogenaudio.org/index.php?title=ReplayGain_1.0_specification).

## Usage

replayGain can be enabled in the Taki settings. The following modes are supported:
 * **Disabled**
   * replayGain is turned off.
 * **Prefer Track**
   * Always uses track gain but falls back to album gain if track gain is missing.
 * **Prefer Album**
     * Always uses album gain but falls back to track gain if album gain is missing.
 * **Track Only**
     * Always uses track gain, will not fallback to album gain.
 * **Album Only**
     * Always uses album gain, will not fallback to track gain.
 * **Track Unless Playing an Album**
    * If all the songs in the current playlist are from the same album then album gain is used,
      otherwise track gain is used. If album or track gain is missing it will attempt to fallback to
      the other gain tag.

## Adding replayGain Metadata

An external tool is needed to analyze a media library and tag files with replayGain metadata. A
number of tools are listed at the
[Hydrogenaudio Wiki](https://wiki.hydrogenaudio.org/index.php?title=ReplayGain#Implementations)

## Implementation Details

Taki is able to pull replayGain metadata from tags embedded in id3 tags and vorbis comments of
media. Currently it supports the following tags (case insensitive):
 * REPLAYGAIN_TRACK_GAIN
 * REPLAYGAIN_ALBUM_GAIN
 * R128_TRACK_GAIN
 * R128_ALBUM_GAIN

If these tags are not present for a given song Taki will play the song at full volume.
This can result in significant volume differences when mixing tagged and untagged songs, it's
recommended to tag all songs with replayGain metadata or disable replayGain.

'**Track Unless Playing an Album**' mode only looks 30 songs ahead in a playlist to decide if it
should use album gain or track gain. This means choosing album or track gain will be inconsistent if
a playlist has multiple albums but at least 30 consecutive songs are from the same album.

'**Track Unless Playing an Album**' mode only considers the album tag when examining a playlist to
determine if album or track gain should be used. Other potentially relevant tags such as album id
are ignored.

## Unsupported Features

Currently pre-amplification and clipping prevention are unimplemented.
