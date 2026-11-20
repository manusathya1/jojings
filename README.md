# Jojings — Ringtone Maker (Android)

Native Kotlin + Jetpack Compose app. Android 9+ (minSdk 28).

## Build & install

```bash
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk (~2 MB)
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it installs for testing; sign with your own key before publishing.

## How it works

| Piece | File |
|---|---|
| Extract audio from any audio/video file (MediaExtractor + MediaCodec → 44.1 kHz stereo PCM, resampled) | `audio/AudioDecoder.kt` |
| Encode a cut to AAC `.m4a` with optional fades | `audio/AacEncoder.kt` |
| Preview playback of the selection | `audio/PcmPreviewPlayer.kt` |
| Join saved tunes (gaps, click-free seams, 2:00 cap) | `audio/Joiner.kt` |
| Tune storage (`files/tunes/` in internal phone memory), set as ringtone/notification/alarm, share | `data/TuneRepository.kt` |
| Pop UI kit: tap bursts on every touch, squishy buttons, confetti, dialogs | `ui/Pop.kt` |

Formats: whatever the phone's built-in decoders support — MP3, AAC/M4A, OGG/Vorbis, Opus, FLAC, WAV, AMR, and the audio track of MP4, MKV, WEBM, 3GP, TS. Files the phone can't decode (e.g. some AVI/WMV/AC3/DTS tracks) show a friendly error.
