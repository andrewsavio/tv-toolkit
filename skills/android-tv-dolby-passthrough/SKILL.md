---
name: android-tv-dolby-passthrough
description: Get Dolby Digital Plus, Dolby Atmos or DTS audio passed straight to an Android TV's own decoder, check which formats the TV accepts, and show a format badge in a Media3 player. Use when the user wants Dolby or Atmos sound from their own player, asks why a file is decoded instead of passed through, or asks what formats the TV accepts.
---

# Dolby pass-through on Android TV

## What the TV accepts

```bash
adb shell dumpsys media.audio_policy | grep -E "format:|flags:"
```

Look for `AUDIO_FORMAT_E_AC3`, `AUDIO_FORMAT_E_AC3_JOC` (Atmos in Dolby Digital Plus) and `AUDIO_FORMAT_AC4`, with `AUDIO_OUTPUT_FLAG_DIRECT` on the output that goes to HDMI. On the tested TV, only E-AC3, E-AC3-JOC and AC-4 were listed for direct output. Plain AC3 (Dolby Digital) was not listed, so it is decoded to PCM.

Also check the TV's own *Digital audio output* and HDMI audio settings, if any. A setting that forces PCM overrides everything below.

## Player side (Media3)

- Build the renderers with a custom `AudioSink` (`DefaultAudioSink`). Pass-through works when the sink is allowed to send encoded audio. A `DefaultAudioSink.Builder` with `setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)` restricts it to PCM, which the player uses as its fallback.
- Detect the outcome with `AnalyticsListener.onAudioTrackInitialized`. Its `AudioSink.AudioTrackConfig.encoding` is `AudioFormat.ENCODING_PCM_*` when decoded, and a compressed encoding when passed through.
- If the audio track fails to initialise (`ERROR_CODE_AUDIO_TRACK_INIT_FAILED` or `ERROR_CODE_AUDIO_TRACK_WRITE_FAILED`), rebuild the player in PCM mode and remember the choice.

## Format badge

Read the format from the track: MIME type `audio/eac3` is Dolby Digital Plus, `audio/eac3-joc` or the codec string `ec+3` is Atmos, `audio/ac3` is Dolby Digital, `audio/ac4` is AC-4. Show it in a small black rounded box in the top-left, only while the audio is passed through. Draw it with a Canvas, not with bitmaps, to keep memory low.

## Limits to tell the user

- Only the formats the TV lists can pass through. Other formats are decoded, and the Dolby logo is not shown for them.
- Verified: Dolby Digital Plus 5.1 pass-through on the tested TV. Not verified: Dolby Digital (AC3) pass-through, Atmos files, DTS.
- Sound that the TV decodes itself is only as good as the TV's own speakers or receiver. Check the receiver's display shows the Dolby format.
