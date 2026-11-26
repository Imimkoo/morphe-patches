/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/4851
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.shared.spoof;

import android.os.SystemClock;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.SharedYouTubeSettings;

/**
 * Limits how much media SABR streams buffer ahead of the playback position.
 * <p>
 * The next request policy of each media response tells the player how far ahead to buffer. For the
 * spoofed clients the server keeps raising this target while the video plays, up to about as much
 * media as was already watched. The app keeps all the buffered media in the Java heap, so videos of
 * high quality or at a high speed filled the heap after a few minutes and the app ran out of memory.
 * The target is lowered so the media buffered ahead fits in a part of the heap, using the bitrate of
 * the media that is received, and is never more than the readahead of the player config of the app.
 */
final class SabrReadaheadLimit {
    private static final int PART_MEDIA_HEADER = 20;
    private static final int PART_NEXT_REQUEST_POLICY = 35;
    private static final int PART_MEDIA_END = 22;
    private static final int PART_FORMAT_INITIALIZATION_METADATA = 42;

    private static final int MEDIA_HEADER_ID_FIELD = 1;
    private static final int MEDIA_HEADER_ITAG_FIELD = 3;
    private static final int MEDIA_HEADER_START_MS_FIELD = 11;
    private static final int MEDIA_HEADER_TIME_RANGE_FIELD = 15;
    private static final int MEDIA_HEADER_DURATION_MS_FIELD = 12;
    private static final int MEDIA_HEADER_CONTENT_LENGTH_FIELD = 14;

    private static final int FORMAT_INIT_FORMAT_ID_FIELD = 2;
    private static final int FORMAT_INIT_MIME_TYPE_FIELD = 5;
    private static final int FORMAT_ID_ITAG_FIELD = 1;
    private static final int TIME_RANGE_START_FIELD = 1;
    private static final int TIME_RANGE_DURATION_FIELD = 2;
    private static final int TIME_RANGE_TIMESCALE_FIELD = 3;

    private static final int POLICY_TARGET_AUDIO_READAHEAD_MS_FIELD = 1;
    private static final int POLICY_TARGET_VIDEO_READAHEAD_MS_FIELD = 2;

    /**
     * Memory that the media buffered ahead can use: a part of the maximum heap size, up to 100 MB,
     * which is about 30 seconds of 4K video at 60 fps.
     * The media behind the playback position and the rest of the app need the remaining heap.
     */
    private static final long MAX_BUFFERED_BYTES = Math.min(100L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5);
    private static final long MIN_READAHEAD_MS = 10_000;
    /**
     * Max readahead of the player config of the app (exoPlayerConfig maxReadAheadMediaTimeMs).
     * The player configs of the SABR clients have no readahead, so the server target was not limited by time.
     */
    private static final long MAX_READAHEAD_MS = 120_000;

    /**
     * Formats that were not received for this long are no longer counted in the bitrate.
     */
    private static final long FORMAT_EXPIRATION_MS = 30_000;

    /**
     * The bitrate of a format follows the largest segments, and lowers slowly after them.
     */
    private static final double BITRATE_DECAY = 0.9;

    /**
     * Readers of the responses, by the native callbacks of their request.
     */
    private static final Map<Object, SabrResponseReader> responseReaders =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Bytes per millisecond of media of the recently received formats, by itag.
     * The formats of all streams are counted together, which can only lower the limit.
     */
    private static final Map<Integer, FormatBitrate> bitrates = new HashMap<>();
    /** MIME type classification supplied by SABR format initialization metadata. */
    private static final Map<Integer, String> videoFormats = new HashMap<>();
    /** Total SABR response bytes observed since the current video started (includes UMP headers). */
    private static final AtomicLong receivedBytes = new AtomicLong();
    private static final Map<Object, Map<Integer, PendingVideoSegment>> pendingVideoSegments =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile long playbackGeneration;
    private static final Object videoBufferLock = new Object();
    private static int activeVideoItag = -1;
    private static long furthestBufferedVideoEndMs = -1;
    private static boolean holdingVideoRequests;

    private static final class PendingVideoSegment {
        final int itag;
        final long endTimeMs;
        final long generation;

        PendingVideoSegment(int itag, long endTimeMs, long generation) {
            this.itag = itag;
            this.endTimeMs = endTimeMs;
            this.generation = generation;
        }
    }

    private static long lastLoggedLimitMs;

    /** Playback clock observations used to detect seeking and rapid timeline navigation. */
    private static volatile long lastPlaybackPositionMs = -1;
    private static volatile long lastPlaybackUpdateMs = -1;
    private static volatile long stableSinceMs = SystemClock.elapsedRealtime();

    static void onPlaybackStarted() {
        lastPlaybackPositionMs = -1;
        lastPlaybackUpdateMs = -1;
        stableSinceMs = SystemClock.elapsedRealtime();
        playbackGeneration++;
        receivedBytes.set(0L);
        responseReaders.clear();
        synchronized (pendingVideoSegments) {
            pendingVideoSegments.clear();
        }
        synchronized (videoBufferLock) {
            activeVideoItag = -1;
            furthestBufferedVideoEndMs = -1;
            holdingVideoRequests = false;
        }
    }

    static void onPlaybackTime(long positionMs) {
        final long now = SystemClock.elapsedRealtime();
        final long previousPosition = lastPlaybackPositionMs;
        final long previousUpdate = lastPlaybackUpdateMs;

        if (previousPosition < 0 || previousUpdate < 0) {
            stableSinceMs = now;
        } else {
            final long elapsedMs = Math.max(1L, now - previousUpdate);
            final long deltaMs = positionMs - previousPosition;
            final long jumpThresholdMs = Math.max(2500L, elapsedMs * 3L + 500L);
            if (elapsedMs > 2500L || deltaMs <= 0L || deltaMs > jumpThresholdMs) {
                stableSinceMs = now;
                Logger.printDebug(() -> "SABR low video readahead: timeline movement detected (delta="
                        + deltaMs + "ms)");
            }
        }

        lastPlaybackPositionMs = positionMs;
        lastPlaybackUpdateMs = now;
        // Playback position and received media headers together provide a buffered-ahead estimate.
        getVideoReadaheadLimitMs(getReadaheadLimitMs());
    }

    static long getConfiguredVideoTargetMs() {
        final long stableDurationMs = SharedYouTubeSettings.SABR_VIDEO_READAHEAD_STABLE_SECONDS.get() * 1000L;
        final boolean stable = SystemClock.elapsedRealtime() - stableSinceMs >= stableDurationMs;
        final int targetTenths = stable
                ? SharedYouTubeSettings.SABR_VIDEO_READAHEAD_TENTHS.get()
                : SharedYouTubeSettings.SABR_VIDEO_READAHEAD_LOW_TENTHS.get();
        return Math.max(500L, targetTenths * 100L);
    }

    static long getConfiguredVideoResumeMs(long targetMs) {
        final long configuredResumeMs = SharedYouTubeSettings.SABR_VIDEO_READAHEAD_RESUME_TENTHS.get() * 100L;
        return Math.max(100L, Math.min(configuredResumeMs, targetMs - 100L));
    }

    static long getReceivedBytesThisPlayback() {
        return receivedBytes.get();
    }

    @Nullable
    static String getActiveVideoMimeType() {
        final int itag;
        synchronized (videoBufferLock) {
            itag = activeVideoItag;
        }
        synchronized (videoFormats) {
            return videoFormats.get(itag);
        }
    }

    private static long getVideoReadaheadLimitMs(long memoryLimitMs) {
        final long targetMs = Math.max(500L, Math.min(memoryLimitMs, getConfiguredVideoTargetMs()));
        final long resumeMs = getConfiguredVideoResumeMs(targetMs);

        synchronized (videoBufferLock) {
            if (lastPlaybackPositionMs >= 0 && furthestBufferedVideoEndMs >= 0) {
                final long bufferedAheadMs = furthestBufferedVideoEndMs - lastPlaybackPositionMs;
                if (holdingVideoRequests) {
                    if (bufferedAheadMs <= resumeMs) {
                        holdingVideoRequests = false;
                        Logger.printDebug(() -> "SABR video readahead resumed at " + bufferedAheadMs + "ms ahead");
                    }
                } else if (bufferedAheadMs >= targetMs) {
                    holdingVideoRequests = true;
                    Logger.printDebug(() -> "SABR video readahead held at " + bufferedAheadMs + "ms ahead");
                }
            }
            return Math.max(100L, Math.min(memoryLimitMs, holdingVideoRequests ? resumeMs : targetMs));
        }
    }

    private static final class FormatBitrate {
        double bytesPerMs;
        long receivedTime;
    }

    private SabrReadaheadLimit() {
    }

    /**
     * @param callbacks Native callbacks of a SABR media request, which are also given the response.
     */
    static void onRequest(Object callbacks) {
        final long requestGeneration = playbackGeneration;
        responseReaders.put(callbacks, new SabrResponseReader(
                SabrReadaheadLimit::limitReadahead, PART_NEXT_REQUEST_POLICY,
                (partType, part) -> {
                    if (partType == PART_MEDIA_HEADER) {
                        onMediaHeader(callbacks, requestGeneration, part);
                    } else if (partType == PART_FORMAT_INITIALIZATION_METADATA) {
                        onFormatInitializationMetadata(part);
                    } else if (partType == PART_MEDIA_END) {
                        onMediaEnd(callbacks, requestGeneration, part);
                    }
                }, PART_MEDIA_HEADER, PART_FORMAT_INITIALIZATION_METADATA, PART_MEDIA_END));
    }

    static void onResponseChunk(Object callbacks, ByteBuffer chunk) {
        SabrResponseReader reader = responseReaders.get(callbacks);
        if (reader == null) {
            return;
        }
        try {
            receivedBytes.addAndGet(chunk.remaining());
            reader.read(chunk);
        } catch (Exception ex) {
            responseReaders.remove(callbacks);
            Logger.printInfo(() -> "Could not read media response", ex);
        }
    }

    private static void onMediaHeader(Object callbacks, long generation, byte[] part) {
        if (generation != playbackGeneration) return;
        try {
            CodedInputStream input = CodedInputStream.newInstance(part);
            int headerId = -1;
            int itag = -1;
            long startTimeMs = -1;
            long durationMs = 0;
            long contentLength = 0;
            byte[] timeRange = null;
            int tag;
            while ((tag = input.readTag()) != 0) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case MEDIA_HEADER_ID_FIELD -> headerId = input.readInt32();
                    case MEDIA_HEADER_ITAG_FIELD -> itag = input.readInt32();
                    case MEDIA_HEADER_START_MS_FIELD -> startTimeMs = input.readInt64();
                    case MEDIA_HEADER_DURATION_MS_FIELD -> durationMs = input.readInt64();
                    case MEDIA_HEADER_CONTENT_LENGTH_FIELD -> contentLength = input.readInt64();
                    case MEDIA_HEADER_TIME_RANGE_FIELD -> timeRange = input.readByteArray();
                    default -> input.skipField(tag);
                }
            }
            if (timeRange != null && (startTimeMs < 0 || durationMs <= 0)) {
                long startTicks = 0;
                long durationTicks = 0;
                long timescale = 0;
                CodedInputStream range = CodedInputStream.newInstance(timeRange);
                while (!range.isAtEnd()) {
                    int rangeTag = range.readTag();
                    if (rangeTag == 0) break;
                    switch (WireFormat.getTagFieldNumber(rangeTag)) {
                        case TIME_RANGE_START_FIELD -> startTicks = range.readInt64();
                        case TIME_RANGE_DURATION_FIELD -> durationTicks = range.readInt64();
                        case TIME_RANGE_TIMESCALE_FIELD -> timescale = range.readInt64();
                        default -> range.skipField(rangeTag);
                    }
                }
                if (timescale > 0) {
                    if (startTimeMs < 0 && startTicks > 0) startTimeMs = startTicks * 1000L / timescale;
                    if (durationMs <= 0 && durationTicks > 0) durationMs = durationTicks * 1000L / timescale;
                }
            }

            // Init segments have no duration or start time.
            if (itag < 0 || durationMs <= 0 || contentLength <= 0) {
                return;
            }

            final double bytesPerMs = (double) contentLength / durationMs;
            synchronized (bitrates) {
                FormatBitrate bitrate = bitrates.get(itag);
                if (bitrate == null) {
                    bitrate = new FormatBitrate();
                    bitrates.put(itag, bitrate);
                }
                bitrate.bytesPerMs = Math.max(bytesPerMs, bitrate.bytesPerMs * BITRATE_DECAY);
                bitrate.receivedTime = SystemClock.elapsedRealtime();
            }

            final String mimeType;
            synchronized (videoFormats) {
                mimeType = videoFormats.get(itag);
            }
            final boolean isVideo = mimeType != null
                    && mimeType.regionMatches(true, 0, "video/", 0, 6);
            if (isVideo && headerId >= 0 && startTimeMs >= 0) {
                synchronized (pendingVideoSegments) {
                    Map<Integer, PendingVideoSegment> requestSegments =
                            pendingVideoSegments.computeIfAbsent(callbacks, ignored -> new HashMap<>());
                    requestSegments.put(headerId,
                            new PendingVideoSegment(itag, startTimeMs + durationMs, generation));
                }
            }
        } catch (IOException ex) {
            Logger.printInfo(() -> "Could not read media header", ex);
        }
    }

    /** A media segment is counted only after its UMP MEDIA_END marker arrives. */
    private static void onMediaEnd(Object callbacks, long generation, byte[] part) {
        if (part.length == 0 || generation != playbackGeneration) {
            return;
        }
        final int headerId = part[0] & 0xFF;
        final PendingVideoSegment segment;
        synchronized (pendingVideoSegments) {
            Map<Integer, PendingVideoSegment> requestSegments = pendingVideoSegments.get(callbacks);
            if (requestSegments == null) {
                return;
            }
            segment = requestSegments.remove(headerId);
            if (requestSegments.isEmpty()) {
                pendingVideoSegments.remove(callbacks);
            }
        }
        if (segment == null || segment.generation != playbackGeneration) {
            return;
        }
        synchronized (videoBufferLock) {
            if (activeVideoItag != segment.itag) {
                activeVideoItag = segment.itag;
                furthestBufferedVideoEndMs = segment.endTimeMs;
            } else {
                furthestBufferedVideoEndMs = Math.max(furthestBufferedVideoEndMs, segment.endTimeMs);
            }
        }
    }

    /** Records which SABR itags are video tracks so audio segments do not affect video hysteresis. */
    private static void onFormatInitializationMetadata(byte[] part) {
        try {
            CodedInputStream input = CodedInputStream.newInstance(part);
            int itag = -1;
            String mimeType = null;
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) break;
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case FORMAT_INIT_FORMAT_ID_FIELD -> {
                        CodedInputStream formatId = CodedInputStream.newInstance(input.readByteArray());
                        while (!formatId.isAtEnd()) {
                            int formatTag = formatId.readTag();
                            if (formatTag == 0) break;
                            if (WireFormat.getTagFieldNumber(formatTag) == FORMAT_ID_ITAG_FIELD) {
                                itag = formatId.readInt32();
                            } else {
                                formatId.skipField(formatTag);
                            }
                        }
                    }
                    case FORMAT_INIT_MIME_TYPE_FIELD -> mimeType = input.readString();
                    default -> input.skipField(tag);
                }
            }
            if (itag >= 0 && mimeType != null) {
                synchronized (videoFormats) {
                    videoFormats.put(itag, mimeType);
                }
            }
        } catch (IOException ex) {
            Logger.printInfo(() -> "Could not read SABR format initialization metadata", ex);
        }
    }

    /**
     * @return The largest readahead that fits in the heap, or the max readahead if the bitrate is not known yet.
     */
    private static long getReadaheadLimitMs() {
        final long now = SystemClock.elapsedRealtime();
        double bytesPerMs = 0;
        synchronized (bitrates) {
            Iterator<FormatBitrate> iterator = bitrates.values().iterator();
            while (iterator.hasNext()) {
                FormatBitrate bitrate = iterator.next();
                if (now - bitrate.receivedTime > FORMAT_EXPIRATION_MS) {
                    iterator.remove();
                } else {
                    bytesPerMs += bitrate.bytesPerMs;
                }
            }
        }
        if (bytesPerMs <= 0) {
            return MAX_READAHEAD_MS;
        }
        return Math.max(MIN_READAHEAD_MS, Math.min(MAX_READAHEAD_MS, (long) (MAX_BUFFERED_BYTES / bytesPerMs)));
    }

    /**
     * Lowers the readahead targets of a NextRequestPolicy message in place.
     * A target is written with the same number of bytes, so the size of the part does not change.
     * Protobuf varints can have extra empty groups, and parsers accept them.
     */
    private static void limitReadahead(ByteBuffer chunk, int offset, int length) {
        final long memoryLimitMs = getReadaheadLimitMs();
        // Keep audio readahead governed by the existing heap safety limit. The user's
        // video target is independent and may be lower, without deleting already buffered
        // media behind the playback position.
        final long videoLimitMs = getVideoReadaheadLimitMs(memoryLimitMs);
        final int end = offset + length;
        int position = offset;
        while (position < end) {
            long tag = 0;
            for (int shift = 0; ; shift += 7) {
                if (position >= end || shift > 28) {
                    return;
                }
                final byte b = chunk.get(position++);
                tag |= (long) (b & 0x7F) << shift;
                if (b >= 0) {
                    break;
                }
            }

            final int fieldNumber = (int) (tag >>> 3);
            switch ((int) (tag & 7)) {
                case WireFormat.WIRETYPE_VARINT -> {
                    final int valueStart = position;
                    long value = 0;
                    for (int shift = 0; ; shift += 7) {
                        if (position >= end || shift > 63) {
                            return;
                        }
                        final byte b = chunk.get(position++);
                        value |= (long) (b & 0x7F) << shift;
                        if (b >= 0) {
                            break;
                        }
                    }
                    final long fieldLimitMs = fieldNumber == POLICY_TARGET_VIDEO_READAHEAD_MS_FIELD
                            ? videoLimitMs
                            : fieldNumber == POLICY_TARGET_AUDIO_READAHEAD_MS_FIELD ? memoryLimitMs : Long.MAX_VALUE;
                    if (fieldLimitMs != Long.MAX_VALUE && value > fieldLimitMs) {
                        final int valueSize = position - valueStart;
                        long written = fieldLimitMs;
                        for (int i = 0; i < valueSize; i++) {
                            final int group = (int) (written & 0x7F);
                            written >>>= 7;
                            chunk.put(valueStart + i, (byte) (i < valueSize - 1 ? group | 0x80 : group));
                        }
                        // The limit follows the bitrate, so it is logged only when it changes much.
                        if (Math.abs(fieldLimitMs - lastLoggedLimitMs) > lastLoggedLimitMs / 4) {
                            lastLoggedLimitMs = fieldLimitMs;
                            final long serverMs = value;
                            Logger.printDebug(() -> "Limiting SABR "
                                    + (fieldNumber == POLICY_TARGET_VIDEO_READAHEAD_MS_FIELD ? "video" : "audio")
                                    + " readahead from " + serverMs + "ms to " + fieldLimitMs + "ms");
                        }
                    }
                }
                case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                    long size = 0;
                    for (int shift = 0; ; shift += 7) {
                        if (position >= end || shift > 28) {
                            return;
                        }
                        final byte b = chunk.get(position++);
                        size |= (long) (b & 0x7F) << shift;
                        if (b >= 0) {
                            break;
                        }
                    }
                    position += (int) size;
                }
                case WireFormat.WIRETYPE_FIXED64 -> position += 8;
                case WireFormat.WIRETYPE_FIXED32 -> position += 4;
                default -> {
                    return;
                }
            }
        }
    }
}
