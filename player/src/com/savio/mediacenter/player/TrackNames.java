package com.savio.mediacenter.player;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import java.util.Locale;

/** Turns a track's metadata (name, language, codec, channels) into the text shown in the menus. */
final class TrackNames {
    private TrackNames() {}

    static String language(String code) {
        if (code == null || code.isEmpty() || code.equals("und") || code.equals("mis")) return null;
        if (code.equals("mul")) return "Multiple";
        String n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
        return (n == null || n.isEmpty() || n.equalsIgnoreCase(code)) ? code.toUpperCase(Locale.ROOT) : n;
    }

    static String codec(Format f) {
        String m = f.sampleMimeType == null ? "" : f.sampleMimeType;
        String c = f.codecs == null ? "" : f.codecs;
        if (m.equals("application/x-media3-cues") && c.contains("/")) m = c;   // text tracks are converted to cues; the real type is kept in codecs
        switch (m) {
            case MimeTypes.AUDIO_AC3: return "Dolby Digital";
            case MimeTypes.AUDIO_E_AC3: return "Dolby Digital Plus";
            case MimeTypes.AUDIO_E_AC3_JOC: return "Dolby Atmos";
            case MimeTypes.AUDIO_AC4: return "Dolby AC-4";
            case MimeTypes.AUDIO_TRUEHD: return "Dolby TrueHD";
            case MimeTypes.AUDIO_DTS: return "DTS";
            case MimeTypes.AUDIO_DTS_HD: return "DTS-HD";
            case MimeTypes.AUDIO_DTS_EXPRESS: return "DTS Express";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_MPEG: return "MP3";
            case MimeTypes.AUDIO_MPEG_L2: return "MP2";
            case MimeTypes.AUDIO_FLAC: return "FLAC";
            case MimeTypes.AUDIO_OPUS: return "Opus";
            case MimeTypes.AUDIO_VORBIS: return "Vorbis";
            case MimeTypes.AUDIO_RAW: return "PCM";
            case MimeTypes.AUDIO_ALAC: return "ALAC";
            case MimeTypes.APPLICATION_SUBRIP: return "SRT";
            case MimeTypes.TEXT_SSA: return "ASS";
            case MimeTypes.TEXT_VTT: return "WebVTT";
            case MimeTypes.APPLICATION_PGS: return "PGS";
            case MimeTypes.APPLICATION_DVBSUBS: return "DVB";
            case MimeTypes.APPLICATION_VOBSUB: return "VobSub";
            case MimeTypes.APPLICATION_TX3G: return "Text";
            case MimeTypes.APPLICATION_CEA608: return "CEA-608";
            case MimeTypes.APPLICATION_CEA708: return "CEA-708";
            case MimeTypes.VIDEO_H264: return "H.264";
            case MimeTypes.VIDEO_H265: return "HEVC";
            case MimeTypes.VIDEO_VP9: return "VP9";
            case MimeTypes.VIDEO_VP8: return "VP8";
            case MimeTypes.VIDEO_AV1: return "AV1";
            case MimeTypes.VIDEO_DOLBY_VISION: return "Dolby Vision";
            case MimeTypes.VIDEO_MPEG2: return "MPEG-2";
            case MimeTypes.VIDEO_MP4V: return "MPEG-4";
            default:
                if (c.startsWith("ec+3")) return "Dolby Atmos";
                return m.isEmpty() ? "" : m.substring(m.indexOf('/') + 1);
        }
    }

    static String channels(int n) {
        switch (n) {
            case Format.NO_VALUE: return "";
            case 1: return "Mono";
            case 2: return "Stereo";
            case 3: return "2.1";
            case 6: return "5.1";
            case 7: return "6.1";
            case 8: return "7.1";
            default: return n + " ch";
        }
    }

    private static String kbps(Format f) {
        int b = f.averageBitrate != Format.NO_VALUE ? f.averageBitrate : f.bitrate;
        return b > 0 ? (b / 1000) + " kbps" : "";
    }

    /** "Tamil - Dolby Digital Plus 5.1 - 640 kbps" (file's own track name wins over the guessed one). */
    static String audio(Format f, int number) {
        String lang = language(f.language);
        String label = f.label == null ? "" : f.label.trim();
        String head;
        if (!label.isEmpty()) {
            head = (lang != null && !label.toLowerCase(Locale.ROOT).contains(lang.toLowerCase(Locale.ROOT))) ? lang + " (" + label + ")" : label;
        } else {
            head = lang != null ? lang : "Audio " + number;
        }
        StringBuilder sb = new StringBuilder(head);
        String tail = (codec(f) + " " + channels(f.channelCount)).trim();
        if (!tail.isEmpty()) sb.append("  -  ").append(tail);
        String k = kbps(f);
        if (!k.isEmpty()) sb.append("  -  ").append(k);
        if ((f.roleFlags & C.ROLE_FLAG_DESCRIBES_VIDEO) != 0) sb.append("  [Audio description]");
        return sb.toString();
    }

    /** "English - SRT [Captions]" / "Forced" / "SDH". */
    static String text(Format f, int number) {
        String lang = language(f.language);
        String label = f.label == null ? "" : f.label.trim();
        String head;
        if (!label.isEmpty()) {
            head = (lang != null && !label.toLowerCase(Locale.ROOT).contains(lang.toLowerCase(Locale.ROOT))) ? lang + " (" + label + ")" : label;
        } else {
            head = lang != null ? lang : "Subtitle " + number;
        }
        StringBuilder sb = new StringBuilder(head);
        String kind = codec(f);
        if (!kind.isEmpty()) sb.append("  -  ").append(kind);
        if ((f.roleFlags & (C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)) != 0) sb.append("  [Captions]");
        else if (label.toLowerCase(Locale.ROOT).contains("sdh")) sb.append("  [SDH]");
        if ((f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0) sb.append("  [Forced]");
        return sb.toString();
    }

    /** Stable text key for remembering a choice (name, language, codec, channels), independent of track order. */
    static String key(Format f) {
        return (f.language == null ? "" : f.language) + "|" + (f.label == null ? "" : f.label) + "|"
                + (f.sampleMimeType == null ? "" : f.sampleMimeType) + "|" + f.channelCount;
    }
}
