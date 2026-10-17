# Taki project notice

Taki is a free and open-source Android music player for personal and shared music collections. The project is maintained at:

https://github.com/churipakinti/taki-android

## Project status

Taki is independently maintained as a personal open-source project. It is not offered as a commercial service and is not a company-backed product. This statement describes the current stewardship of the project and does not add restrictions beyond the GNU GPLv3 license.

## License

Taki is distributed under the GNU General Public License, version 3. The full, unaltered license text is available in `LICENSE`.

Copyright notices belonging to individual contributors remain with those contributors. Contributions made specifically to Taki are distributed under the same GNU GPLv3 terms.

## Origin and attribution

Taki is a modified work based on Ultrasonic, an Android client for servers compatible with the Subsonic API:

https://gitlab.com/ultrasonic/ultrasonic

Ultrasonic and its contributors are credited as the original project. Taki has changed the application identity, visual design, navigation, exposed feature set, and parts of the playback and library experience. Taki is independently maintained; the original Ultrasonic project and its contributors are not responsible for Taki-specific modifications, releases, support, or defects.

Legacy module names, package identifiers, source paths, and compatibility code may still contain the name `ultrasonic`. Those internal identifiers are retained where renaming them would disrupt upgrades, integrations, or upstream-derived code. The user-facing application represented by this repository is Taki.

## Third-party components: FFmpeg-based hi-res FLAC decoding

Taki decodes high-resolution FLAC (24-bit, 88.2 kHz and above) with an FFmpeg software decoder to avoid a silent stall in Android's platform FLAC decoder (issue #17). This comes from the NextLib Media3 extension, pinned in `gradle/libs.versions.toml`:

- Artifact: `io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0` (Maven Central), tag `v1.10.1-0.13.0`, https://github.com/anilbeesetti/nextlib. The wrapper code is GPL-3.0.
- Bundled native libraries (`libavcodec`, `libavutil`, `libswresample`, `libswscale`, plus NextLib's `libmedia3ext`) are **FFmpeg 6.0** (https://ffmpeg.org, source: https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz). Its embedded build configuration, read from the shipped binary, includes `--enable-version3` and does **not** include `--enable-gpl` or `--enable-nonfree`, so these FFmpeg libraries are licensed under the **GNU Lesser General Public License, version 3 or later**. FFmpeg is a trademark of Fabrice Bellard; the FFmpeg developers are credited at https://ffmpeg.org/about.html.
- FFmpeg is statically linked with **libvpx 1.13.0** (BSD-3-Clause, Google/The WebM Project) and **mbed TLS 3.4.1** (Apache-2.0 OR GPL-2.0-or-later); NextLib's build script for this tag is `ffmpeg/setup.sh` in the repository above.
- The FFmpeg libraries are shipped as separate shared objects (`lib/<abi>/*.so`) in the APK and are loaded dynamically. Taki is GPL-3.0 and its complete source, including the build files that select this dependency, is in this repository, so the libraries can be replaced by building Taki against a modified NextLib/FFmpeg.
- Only the FLAC decoder is used by Taki. Taki does not use FFmpeg's network protocols, video decoders, or encoders.

The LGPL-3.0 and GPL-3.0 license texts are available at https://www.gnu.org/licenses/lgpl-3.0.html and in `LICENSE`.
