package com.bclaw.app.remote

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

data class SunshineStreamStats(
    val target: String,
    val stage: String,
    val codec: String,
    val optionsStatus: Int? = null,
    val describeStatus: Int? = null,
    val announceStatus: Int? = null,
    val playStatus: Int? = null,
    val sessionId: String? = null,
    val audioPort: Int? = null,
    val videoPort: Int? = null,
    val controlPort: Int? = null,
    val controlConnected: Boolean = false,
    val controlEncrypted: Boolean = false,
    val packets: Long = 0,
    val dataPackets: Long = 0,
    val frames: Long = 0,
    val decodedFrames: Long = 0,
    val droppedPackets: Long = 0,
    val droppedFrames: Long = 0,
    val decoderDrops: Long = 0,
    val lastFrameIndex: Long? = null,
    val message: String? = null,
)

class SunshineVideoStream private constructor(
    private val base: String,
    private val plan: SunshineLaunchPlan,
    private val surface: Surface,
    private val onStats: (SunshineStreamStats) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val worker = Thread(::runStream, "BclawSunshineVideo")
    private var socket: DatagramSocket? = null
    @Volatile
    private var controlSession: SunshineControlSession? = null
    private var renderer: MediaCodecVideoRenderer? = null

    private fun start(): SunshineVideoStream {
        worker.isDaemon = true
        worker.start()
        return this
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            socket?.close()
            controlSession?.close()
            renderer?.close()
            worker.interrupt()
        }
    }

    fun sendMousePosition(x: Int, y: Int, reliable: Boolean = false) {
        controlSession?.sendMousePosition(x, y, plan.width, plan.height, reliable)
    }

    fun sendMouseMove(deltaX: Int, deltaY: Int) {
        controlSession?.sendMouseMove(deltaX, deltaY)
    }

    fun sendMouseScroll(scrollAmount: Int) {
        controlSession?.sendMouseScroll(scrollAmount)
    }

    fun sendMouseHScroll(scrollAmount: Int) {
        controlSession?.sendMouseHScroll(scrollAmount)
    }

    fun sendMouseButton(pressed: Boolean, button: Int = SunshineMouseButton.LEFT) {
        controlSession?.sendMouseButton(pressed, button)
    }

    fun sendKeyboardKey(pressed: Boolean, keyCode: Int, modifiers: Int = SunshineModifier.NONE) {
        controlSession?.sendKeyboardKey(pressed, keyCode, modifiers)
    }

    fun sendKeyboardShortcut(modifierKeyCode: Int, keyCode: Int) {
        controlSession?.sendKeyboardShortcut(modifierKeyCode, keyCode)
    }

    fun sendUtf8Text(text: String) {
        controlSession?.sendUtf8Text(text)
    }

    fun sendTouchEvent(
        eventType: Int,
        pointerId: Int,
        x: Float,
        y: Float,
        pressureOrDistance: Float = 0f,
        contactAreaMajor: Float = 0f,
        contactAreaMinor: Float = 0f,
        rotation: Int = 0xFFFF,
    ): Boolean =
        controlSession?.sendTouchEvent(
            eventType = eventType,
            pointerId = pointerId,
            x = x,
            y = y,
            pressureOrDistance = pressureOrDistance,
            contactAreaMajor = contactAreaMajor,
            contactAreaMinor = contactAreaMinor,
            rotation = rotation,
        ) ?: false

    private fun runStream() {
        var stats = SunshineStreamStats(
            target = "",
            stage = "connecting",
            codec = SunshineVideoCodec.fromRequest(plan.codec).summary,
            controlEncrypted = isEncryptedControlAppVersion(plan.appVersion),
        )
        fun publish(next: SunshineStreamStats) {
            stats = next
            onStats(next)
        }

        try {
            publish(stats.copy(stage = "rtsp"))
            val rtsp = openStreamRtsp(base, plan)
            publish(
                stats.copy(
                    target = rtsp.target,
                    stage = "play",
                    codec = rtsp.codecSummary,
                    optionsStatus = rtsp.options.statusCode,
                    describeStatus = rtsp.describe.statusCode,
                    announceStatus = rtsp.announce.statusCode,
                    playStatus = rtsp.play.statusCode,
                    sessionId = rtsp.sessionId,
                    audioPort = rtsp.audioPort,
                    videoPort = rtsp.videoPort,
                    controlPort = rtsp.controlPort,
                    controlEncrypted = rtsp.controlEncrypted,
                ),
            )
            requireRtspOk(rtsp)

            val control = openControlSession(rtsp)
            controlSession = control
            val controlThread = startControlLoop(control)
            publish(stats.copy(stage = "decoder", controlConnected = true))

            val videoRenderer = MediaCodecVideoRenderer(surface, plan.width, plan.height, plan.fps, rtsp.codec)
            renderer = videoRenderer
            publish(stats.copy(stage = "streaming", controlConnected = true))

            receiveVideo(rtsp, videoRenderer) { counters ->
                publish(
                    stats.copy(
                        stage = "streaming",
                        controlConnected = true,
                        packets = counters.packets,
                        dataPackets = counters.dataPackets,
                        frames = counters.frames,
                        decodedFrames = counters.decodedFrames,
                        droppedPackets = counters.droppedPackets,
                        droppedFrames = counters.droppedFrames,
                        decoderDrops = counters.decoderDrops,
                        lastFrameIndex = counters.lastFrameIndex,
                    ),
                )
            }

            controlThread.interrupt()
            runCatching { controlThread.join(350) }
        } catch (error: Throwable) {
            if (!closed.get()) {
                Log.w(TAG, "native Sunshine stream failed", error)
                publish(stats.copy(stage = "failed", message = error.message ?: error.javaClass.simpleName))
                onError(error)
            }
        } finally {
            close()
        }
    }

    private fun openControlSession(rtsp: StreamRtspSession): SunshineControlSession {
        val port = rtsp.controlPort ?: throw IOException("missing control port")
        val connectData = rtsp.controlConnectData ?: throw IOException("missing control connect data")
        val riKey = plan.riKey ?: throw IOException("missing Sunshine RI key")
        return SunshineControlSession
            .connect(
                host = rtsp.host,
                port = port,
                connectData = connectData,
                riKey = riKey,
                encrypted = rtsp.controlEncrypted,
                hostFeatureFlags = rtsp.hostFeatureFlags,
            )
            .also { it.start() }
    }

    private fun startControlLoop(control: SunshineControlSession): Thread {
        return Thread(
            {
                var nextPingAt = System.nanoTime()
                while (!closed.get() && !Thread.currentThread().isInterrupted) {
                    runCatching {
                        if (System.nanoTime() >= nextPingAt) {
                            control.sendPeriodicPing()
                            nextPingAt = System.nanoTime() + CONTROL_PING_INTERVAL_NANOS
                        }
                        control.service(10)
                    }.onFailure {
                        if (!closed.get()) {
                            Log.w(TAG, "control loop failed", it)
                        }
                        close()
                    }
                }
            },
            "BclawSunshineControlLoop",
        ).also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun receiveVideo(
        rtsp: StreamRtspSession,
        videoRenderer: MediaCodecVideoRenderer,
        publishCounters: (VideoCounters) -> Unit,
    ) {
        val assembler = VideoFrameAssembler(rtsp.codec, plan.appVersion)
        val counters = VideoCounters()
        var nextPingAt = 0L
        var pingSequence = 1
        var nextPublishAt = 0L
        val videoTarget = InetSocketAddress(rtsp.host, rtsp.videoPort)
        val audioTarget = rtsp.audioPort?.let { InetSocketAddress(rtsp.host, it) }
        val receiveBuffer = ByteArray(4096)
        val streamStartedAt = System.nanoTime()
        var lastDecodedFrameAt = streamStartedAt

        DatagramSocket(null).use { datagramSocket ->
            socket = datagramSocket
            datagramSocket.soTimeout = 25
            runCatching { datagramSocket.receiveBufferSize = 2 * 1024 * 1024 }
            datagramSocket.bind(InetSocketAddress(0))

            while (!closed.get()) {
                val now = System.nanoTime()
                if (now >= nextPingAt) {
                    val ping = buildVideoPing(rtsp.videoPingPayload, pingSequence++)
                    datagramSocket.send(DatagramPacket(ping, ping.size, videoTarget))
                    if (audioTarget != null) {
                        datagramSocket.send(DatagramPacket(ping, ping.size, audioTarget))
                    }
                    nextPingAt = now + VIDEO_PING_INTERVAL_NANOS
                }

                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    datagramSocket.receive(packet)
                    if (packet.port != rtsp.videoPort) {
                        continue
                    }
                    counters.packets += 1
                    val parsed = parseSunshineVideoPacket(packet.data, packet.length)
                    if (parsed == null) {
                        counters.droppedPackets += 1
                        continue
                    }
                    if (parsed.isParity) {
                        continue
                    }
                    counters.dataPackets += 1
                    val frame = assembler.accept(parsed)
                    if (frame != null) {
                        counters.frames += 1
                        counters.lastFrameIndex = frame.frameIndex
                        if (videoRenderer.submit(frame)) {
                            counters.decodedFrames += 1
                            lastDecodedFrameAt = System.nanoTime()
                        } else {
                            counters.decoderDrops += 1
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep the ping cadence tight even when video is momentarily quiet.
                } catch (error: SocketException) {
                    if (!closed.get()) throw error
                }

                if (System.nanoTime() >= nextPublishAt) {
                    publishCounters(counters.copy())
                    nextPublishAt = System.nanoTime() + STATS_INTERVAL_NANOS
                }
                throwIfDecodeStalled(counters, lastDecodedFrameAt)
            }
        }
    }

    companion object {
        fun start(
            base: String,
            plan: SunshineLaunchPlan,
            surface: Surface,
            onStats: (SunshineStreamStats) -> Unit,
            onError: (Throwable) -> Unit,
        ): SunshineVideoStream = SunshineVideoStream(base, plan, surface, onStats, onError).start()
    }
}

private fun throwIfDecodeStalled(counters: VideoCounters, lastDecodedFrameAt: Long) {
    val now = System.nanoTime()
    val stallNanos = now - lastDecodedFrameAt
    if (counters.decodedFrames == 0L && stallNanos >= VIDEO_STARTUP_DECODE_TIMEOUT_NANOS) {
        throw SocketTimeoutException(
            "No decodable video frames after ${stallNanos.toSecondsString()} " +
                "(packets=${counters.packets}, frames=${counters.frames}, droppedPackets=${counters.droppedPackets})",
        )
    }
    if (counters.decodedFrames > 0L && stallNanos >= VIDEO_DECODE_STALL_TIMEOUT_NANOS) {
        throw SocketTimeoutException(
            "Video decode stalled for ${stallNanos.toSecondsString()} " +
                "(packets=${counters.packets}, frames=${counters.frames}, decoded=${counters.decodedFrames})",
        )
    }
}

private fun Long.toSecondsString(): String =
    "%.1fs".format(this / 1_000_000_000.0)

private data class StreamRtspSession(
    val host: String,
    val target: String,
    val options: RtspResponse,
    val describe: RtspResponse,
    val setupAudio: RtspResponse,
    val setupVideo: RtspResponse,
    val setupControl: RtspResponse,
    val announce: RtspResponse,
    val play: RtspResponse,
    val sessionId: String?,
    val audioPort: Int?,
    val videoPort: Int,
    val controlPort: Int?,
    val videoPingPayload: String?,
    val controlConnectData: String?,
    val controlEncrypted: Boolean,
    val hostFeatureFlags: Int,
    val codec: SunshineVideoCodec,
    val codecSummary: String,
)

private enum class SunshineVideoCodec(
    val requestName: String,
    val summary: String,
    val mimeType: String,
) {
    H264("h264", "H264", MIME_AVC),
    AV1("av1", "AV1", MIME_AV1);

    companion object {
        fun fromRequest(value: String?): SunshineVideoCodec =
            when {
                value?.equals("av1", ignoreCase = true) == true -> AV1
                else -> H264
            }
    }
}

private fun openStreamRtsp(base: String, plan: SunshineLaunchPlan): StreamRtspSession {
    val targetHosts = resolveSunshineTargetHosts(base, plan)
    var lastError: Throwable? = null
    for (targetHost in targetHosts) {
        runCatching {
            return openStreamRtspAtHost(targetHost, plan)
        }.onFailure { error ->
            lastError = error
        }
    }
    throw IOException(
        "Sunshine stream is unreachable at ${targetHosts.joinToString()}",
        lastError,
    )
}

private fun openStreamRtspAtHost(targetHost: String, plan: SunshineLaunchPlan): StreamRtspSession {
    val target = "rtsp://$targetHost:${plan.rtspPort}"
    val options = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "OPTIONS",
            target = target,
            host = targetHost,
            cseq = 1,
        ),
    )
    val describe = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "DESCRIBE",
            target = target,
            host = targetHost,
            cseq = 2,
            extraHeaders = listOf(
                "Accept" to "application/sdp",
                "If-Modified-Since" to "Thu, 01 Jan 1970 00:00:00 GMT",
            ),
        ),
    )
    val videoCodec = selectSunshineVideoCodec(plan, describe.body)
    val setupAudio = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "SETUP",
            target = "streamid=audio/0/0",
            host = targetHost,
            cseq = 3,
            extraHeaders = listOf(
                "Transport" to "unicast;X-GS-ClientPort=$AUDIO_CLIENT_PORT-${AUDIO_CLIENT_PORT + 1}",
                "If-Modified-Since" to "Thu, 01 Jan 1970 00:00:00 GMT",
            ),
        ),
    )
    val sessionId = setupAudio.sessionId()
    val setupVideo = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "SETUP",
            target = "streamid=video/0/0",
            host = targetHost,
            cseq = 4,
            extraHeaders = listOf(
                "Session" to (sessionId ?: ""),
                "Transport" to "unicast;X-GS-ClientPort=$VIDEO_CLIENT_PORT-${VIDEO_CLIENT_PORT + 1}",
                "If-Modified-Since" to "Thu, 01 Jan 1970 00:00:00 GMT",
            ),
        ),
    )
    val setupControl = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "SETUP",
            target = "streamid=control/13/0",
            host = targetHost,
            cseq = 5,
            extraHeaders = listOf(
                "Session" to (sessionId ?: ""),
                "Transport" to "unicast;X-GS-ClientPort=$CONTROL_CLIENT_PORT-${CONTROL_CLIENT_PORT + 1}",
                "If-Modified-Since" to "Thu, 01 Jan 1970 00:00:00 GMT",
            ),
        ),
    )
    val videoPort = setupVideo.serverPort() ?: throw IOException("missing video server port")
    val controlEncrypted = isEncryptedControlAppVersion(plan.appVersion)
    val announceSdp = buildSunshineAnnounceSdp(
        targetHost = targetHost,
        plan = plan,
        videoPort = videoPort,
        encryptionEnabled = if (controlEncrypted) 1 else 0,
        videoCodec = videoCodec.requestName,
    )
    val announce = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "ANNOUNCE",
            target = if (controlEncrypted) "streamid=control/13/0" else "streamid=video",
            host = targetHost,
            cseq = 6,
            extraHeaders = listOf(
                "Session" to (sessionId ?: ""),
                "Content-type" to "application/sdp",
                "Content-length" to announceSdp.toByteArray(Charsets.UTF_8).size.toString(),
            ),
            body = announceSdp,
        ),
    )
    val play = transactRtsp(
        host = targetHost,
        port = plan.rtspPort,
        request = buildRtspRequest(
            method = "PLAY",
            target = "/",
            host = targetHost,
            cseq = 7,
            extraHeaders = listOf("Session" to (sessionId ?: "")),
        ),
    )

    return StreamRtspSession(
        host = targetHost,
        target = target,
        options = options,
        describe = describe,
        setupAudio = setupAudio,
        setupVideo = setupVideo,
        setupControl = setupControl,
        announce = announce,
        play = play,
        sessionId = sessionId,
        audioPort = setupAudio.serverPort(),
        videoPort = videoPort,
        controlPort = setupControl.serverPort(),
        videoPingPayload = setupVideo.headers["x-ss-ping-payload"],
        controlConnectData = setupControl.headers["x-ss-connect-data"],
        controlEncrypted = controlEncrypted,
        hostFeatureFlags = parseSdpAttributeUInt(describe.body, "x-ss-general.featureFlags") ?: 0,
        codec = videoCodec,
        codecSummary = videoCodec.summary,
    )
}

private fun selectSunshineVideoCodec(plan: SunshineLaunchPlan, sdp: String): SunshineVideoCodec {
    val requested = SunshineVideoCodec.fromRequest(plan.codec)
    if (requested == SunshineVideoCodec.AV1) {
        if (sdp.contains("AV1/90000", ignoreCase = true)) {
            return SunshineVideoCodec.AV1
        }
        Log.i(TAG, "AV1 requested but not advertised by Sunshine; falling back to H264")
    }
    return SunshineVideoCodec.H264
}

private fun requireRtspOk(rtsp: StreamRtspSession) {
    val failures = listOf(
        "OPTIONS" to rtsp.options.statusCode,
        "DESCRIBE" to rtsp.describe.statusCode,
        "SETUP audio" to rtsp.setupAudio.statusCode,
        "SETUP video" to rtsp.setupVideo.statusCode,
        "SETUP control" to rtsp.setupControl.statusCode,
        "ANNOUNCE" to rtsp.announce.statusCode,
        "PLAY" to rtsp.play.statusCode,
    ).filter { (_, status) -> status !in 200..299 }
    if (failures.isNotEmpty()) {
        throw IOException(failures.joinToString { (name, status) -> "$name $status" })
    }
}

private class MediaCodecVideoRenderer(
    surface: Surface,
    width: Int,
    height: Int,
    private val fps: Int,
    private val videoCodec: SunshineVideoCodec,
) : Closeable {
    private val mediaCodec = MediaCodec.createDecoderByType(videoCodec.mimeType)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sawCodecConfig = false
    private var closed = false

    init {
        val format = MediaFormat.createVideoFormat(videoCodec.mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceAtLeast(1))
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, (width * height * 3 / 2).coerceAtLeast(512 * 1024))
        }
        mediaCodec.configure(format, surface, null, 0)
        mediaCodec.start()
    }

    fun submit(frame: AssembledVideoFrame): Boolean {
        if (closed) return false
        if (videoCodec == SunshineVideoCodec.H264 && !sawCodecConfig) {
            if (!frame.hasCodecConfig) {
                return false
            }
            sawCodecConfig = true
        }

        return runCatching {
            val inputIndex = mediaCodec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex < 0) {
                drain()
                return false
            }
            val input = mediaCodec.getInputBuffer(inputIndex) ?: return false
            if (frame.data.size > input.capacity()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs(frame), 0)
                return false
            }
            input.clear()
            input.put(frame.data)
            mediaCodec.queueInputBuffer(
                inputIndex,
                0,
                frame.data.size,
                presentationTimeUs(frame),
                if (frame.isIdr) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
            )
            drain()
            true
        }.getOrElse {
            Log.w(TAG, "decoder submit failed", it)
            false
        }
    }

    private fun drain() {
        while (!closed) {
            when (val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                -> Unit
                else -> {
                    if (outputIndex >= 0) {
                        mediaCodec.releaseOutputBuffer(outputIndex, bufferInfo.size > 0)
                    } else {
                        return
                    }
                }
            }
        }
    }

    private fun presentationTimeUs(frame: AssembledVideoFrame): Long =
        frame.frameIndex * 1_000_000L / fps.coerceAtLeast(1)

    override fun close() {
        if (!closed) {
            closed = true
            runCatching { mediaCodec.stop() }
            runCatching { mediaCodec.release() }
        }
    }
}

private data class ParsedVideoPacket(
    val sequenceNumber: Int,
    val streamPacketIndex: Int,
    val frameIndex: Long,
    val flags: Int,
    val fecCurrentBlock: Int,
    val fecLastBlock: Int,
    val isParity: Boolean,
    val payload: ByteArray,
) {
    val firstPacket: Boolean
        get() {
            val withoutPicData = flags and FLAG_CONTAINS_PIC_DATA.inv()
            return (withoutPicData == FLAG_SOF || withoutPicData == (FLAG_SOF or FLAG_EOF)) &&
                fecCurrentBlock == 0
        }
    val lastPacket: Boolean
        get() = (flags and FLAG_EOF) != 0 && fecCurrentBlock == fecLastBlock
}

private data class AssembledVideoFrame(
    val frameIndex: Long,
    val data: ByteArray,
    val frameType: Int,
    val hasCodecConfig: Boolean,
    val isIdr: Boolean,
)

private class VideoFrameAssembler(
    private val codec: SunshineVideoCodec,
    private val appVersion: String?,
) {
    private var frameIndex = -1L
    private var frameType = 0
    private var lastPacketPayloadLength = 0
    private var seenFirst = false
    private var seenLast = false
    private val fragments = TreeMap<Int, ByteArray>()

    fun accept(packet: ParsedVideoPacket): AssembledVideoFrame? {
        if ((packet.flags and FLAG_CONTAINS_PIC_DATA) == 0) {
            return null
        }
        if (frameIndex != packet.frameIndex) {
            reset(packet.frameIndex)
            if (!packet.firstPacket) {
                return null
            }
        }
        if (!seenFirst && !packet.firstPacket) {
            return null
        }

        val stripped = if (packet.firstPacket) {
            val firstPayload = stripSunshineFrameHeader(packet.payload, codec, appVersion)
            frameType = firstPayload.frameType
            lastPacketPayloadLength = firstPayload.lastPacketPayloadLength ?: 0
            seenFirst = true
            firstPayload
        } else {
            null
        }
        var payload = stripped?.payload ?: packet.payload
        if (codec == SunshineVideoCodec.AV1 && packet.lastPacket) {
            val headerSize = stripped?.headerSize ?: 0
            val trimmed = trimLastPacketPayload(payload, lastPacketPayloadLength, headerSize, frameIndex)
            if (trimmed == null) {
                val lostFrame = frameIndex
                reset(-1L)
                Log.d(TAG, "dropping frame $lostFrame with invalid AV1 payload length")
                return null
            }
            payload = trimmed
        }
        if (payload.isNotEmpty()) {
            fragments[packet.streamPacketIndex] = payload
        }
        if (packet.lastPacket) {
            seenLast = true
        }
        if (!seenFirst || !seenLast || fragments.isEmpty()) {
            return null
        }
        if (!hasContiguousFragmentKeys(fragments)) {
            val lostFrame = frameIndex
            reset(-1L)
            Log.d(TAG, "dropping non-contiguous frame $lostFrame")
            return null
        }

        val out = ByteArrayOutputStream(fragments.values.sumOf { it.size })
        fragments.values.forEach { out.write(it) }
        val data = out.toByteArray()
        val completedFrame = frameIndex
        val completedFrameType = frameType
        reset(-1L)

        if (codec == SunshineVideoCodec.H264) {
            if (!hasAnnexBStart(data)) {
                return null
            }
            val hasConfig = containsH264Nal(data, H264_NAL_SPS) && containsH264Nal(data, H264_NAL_PPS)
            val idr = completedFrameType == FRAME_TYPE_IDR || containsH264Nal(data, H264_NAL_IDR)
            return AssembledVideoFrame(
                frameIndex = completedFrame,
                data = data,
                frameType = completedFrameType,
                hasCodecConfig = hasConfig,
                isIdr = idr,
            )
        }

        if (data.isEmpty()) {
            return null
        }
        return AssembledVideoFrame(
            frameIndex = completedFrame,
            data = data,
            frameType = completedFrameType,
            hasCodecConfig = true,
            isIdr = completedFrameType == FRAME_TYPE_IDR,
        )
    }

    private fun reset(nextFrameIndex: Long) {
        frameIndex = nextFrameIndex
        frameType = 0
        lastPacketPayloadLength = 0
        seenFirst = false
        seenLast = false
        fragments.clear()
    }
}

private data class FirstPayload(
    val payload: ByteArray,
    val frameType: Int,
    val headerSize: Int,
    val lastPacketPayloadLength: Int?,
)

private fun parseSunshineVideoPacket(data: ByteArray, length: Int): ParsedVideoPacket? {
    if (length < FIXED_RTP_HEADER_SIZE) return null
    val rtpHeader = data[0].toInt() and 0xff
    var offset = FIXED_RTP_HEADER_SIZE
    if ((rtpHeader and RTP_FLAG_EXTENSION) != 0) {
        offset += RTP_EXTENSION_SIZE
    }
    if (length < offset + NV_VIDEO_PACKET_SIZE) return null

    val sequenceNumber = readU16be(data, 2)
    val rawStreamPacketIndex = readU32le(data, offset)
    val streamPacketIndex = ((rawStreamPacketIndex ushr 8) and 0x00ff_ffff).toInt()
    val frameIndex = readU32le(data, offset + 4)
    val flags = data[offset + 8].toInt() and 0xff
    val multiFecBlocks = data[offset + 11].toInt() and 0xff
    val fecInfo = readU32le(data, offset + 12)
    val fecIndex = ((fecInfo and 0x003f_f000L) ushr 12).toInt()
    val dataPacketCount = ((fecInfo and 0xffc0_0000L) ushr 22).toInt()
    val fecPercentage = ((fecInfo and 0x0000_0ff0L) ushr 4).toInt()
    val parityPacketCount = ((dataPacketCount * fecPercentage) + 99) / 100
    val lowestSequenceNumber = (sequenceNumber - fecIndex) and 0xffff
    val firstParitySequenceNumber = (lowestSequenceNumber + dataPacketCount) and 0xffff
    val isParity = dataPacketCount > 0 &&
        parityPacketCount > 0 &&
        !isBefore16(sequenceNumber, firstParitySequenceNumber)
    val payloadOffset = offset + NV_VIDEO_PACKET_SIZE
    return ParsedVideoPacket(
        sequenceNumber = sequenceNumber,
        streamPacketIndex = streamPacketIndex,
        frameIndex = frameIndex,
        flags = flags,
        fecCurrentBlock = (multiFecBlocks ushr 4) and 0x3,
        fecLastBlock = (multiFecBlocks ushr 6) and 0x3,
        isParity = isParity,
        payload = data.copyOfRange(payloadOffset, length),
    )
}

private fun stripSunshineFrameHeader(
    payload: ByteArray,
    codec: SunshineVideoCodec,
    appVersion: String?,
): FirstPayload {
    if (payload.isEmpty()) {
        return FirstPayload(payload, 0, 0, null)
    }
    val frameType = payload.getOrNull(3)?.toInt()?.and(0xff) ?: 0
    val headerSize = sunshineFrameHeaderSize(payload, appVersion).coerceAtMost(payload.size)
    val lastPacketPayloadLength = if (codec == SunshineVideoCodec.AV1) readU16leOrNull(payload, 4) else null
    if (codec == SunshineVideoCodec.AV1) {
        return FirstPayload(
            payload = if (headerSize < payload.size) payload.copyOfRange(headerSize, payload.size) else ByteArray(0),
            frameType = frameType,
            headerSize = headerSize,
            lastPacketPayloadLength = lastPacketPayloadLength,
        )
    }

    val annexBOffset = findAnnexBStart(payload, headerSize) ?: findAnnexBStart(payload, 0) ?: payload.size
    val payloadOffset = skipLeadingAudAndSei(payload, annexBOffset)
    return FirstPayload(
        payload = if (payloadOffset < payload.size) payload.copyOfRange(payloadOffset, payload.size) else ByteArray(0),
        frameType = frameType,
        headerSize = headerSize,
        lastPacketPayloadLength = null,
    )
}

private fun trimLastPacketPayload(
    payload: ByteArray,
    lastPacketPayloadLength: Int,
    headerSize: Int,
    frameIndex: Long,
): ByteArray? {
    if (lastPacketPayloadLength <= headerSize) {
        Log.d(TAG, "invalid AV1 last payload length on frame $frameIndex: $lastPacketPayloadLength <= $headerSize")
        return null
    }
    val expectedLength = lastPacketPayloadLength - headerSize
    if (expectedLength > payload.size) {
        Log.d(TAG, "invalid AV1 last payload length on frame $frameIndex: $expectedLength > ${payload.size}")
        return null
    }
    return if (expectedLength == payload.size) payload else payload.copyOf(expectedLength)
}

private fun sunshineFrameHeaderSize(payload: ByteArray, appVersion: String?): Int {
    val first = payload.firstOrNull()?.toInt()?.and(0xff) ?: return 0
    return when {
        versionAtLeast(appVersion, 7, 1, 450) -> if (first == 0x01) 8 else 44
        versionAtLeast(appVersion, 7, 1, 446) -> if (first == 0x01) 8 else 41
        versionAtLeast(appVersion, 7, 1, 415) -> if (first == 0x01) 8 else 24
        versionAtLeast(appVersion, 7, 1, 350) -> 8
        versionAtLeast(appVersion, 7, 1, 320) -> 12
        versionAtLeast(appVersion, 5, 0, 0) -> 8
        else -> 0
    }
}

private fun skipLeadingAudAndSei(data: ByteArray, startOffset: Int): Int {
    var offset = startOffset
    while (offset < data.size) {
        val start = findAnnexBStart(data, offset) ?: return offset
        val startLength = annexBStartLength(data, start)
        val nalOffset = start + startLength
        if (nalOffset >= data.size) return start
        val nalType = data[nalOffset].toInt() and 0x1f
        if (nalType != H264_NAL_AUD && nalType != H264_NAL_SEI) {
            return start
        }
        offset = findAnnexBStart(data, nalOffset + 1) ?: return data.size
    }
    return data.size
}

private fun hasAnnexBStart(data: ByteArray): Boolean = findAnnexBStart(data, 0) != null

private fun containsH264Nal(data: ByteArray, type: Int): Boolean {
    var offset = 0
    while (offset < data.size) {
        val start = findAnnexBStart(data, offset) ?: return false
        val nalOffset = start + annexBStartLength(data, start)
        if (nalOffset < data.size && (data[nalOffset].toInt() and 0x1f) == type) {
            return true
        }
        offset = nalOffset + 1
    }
    return false
}

private fun findAnnexBStart(data: ByteArray, offset: Int): Int? {
    var index = offset.coerceAtLeast(0)
    while (index + 3 < data.size) {
        if (data[index] == 0.toByte() && data[index + 1] == 0.toByte()) {
            if (data[index + 2] == 1.toByte()) {
                return index
            }
            if (index + 4 < data.size && data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()) {
                return index
            }
        }
        index += 1
    }
    return null
}

private fun annexBStartLength(data: ByteArray, offset: Int): Int =
    if (offset + 3 < data.size && data[offset + 2] == 1.toByte()) 3 else 4

private fun hasContiguousFragmentKeys(fragments: TreeMap<Int, ByteArray>): Boolean {
    var expected: Int? = null
    for (key in fragments.keys) {
        val next = expected
        if (next != null && key != next) {
            return false
        }
        expected = (key + 1) and 0x00ff_ffff
    }
    return true
}

private data class VideoCounters(
    var packets: Long = 0,
    var dataPackets: Long = 0,
    var frames: Long = 0,
    var decodedFrames: Long = 0,
    var droppedPackets: Long = 0,
    var droppedFrames: Long = 0,
    var decoderDrops: Long = 0,
    var lastFrameIndex: Long? = null,
)

private fun readU16be(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

private fun readU16leOrNull(data: ByteArray, offset: Int): Int? =
    if (offset + 1 < data.size) {
        (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
    } else {
        null
    }

private fun readU32le(data: ByteArray, offset: Int): Long =
    (data[offset].toLong() and 0xffL) or
        ((data[offset + 1].toLong() and 0xffL) shl 8) or
        ((data[offset + 2].toLong() and 0xffL) shl 16) or
        ((data[offset + 3].toLong() and 0xffL) shl 24)

private fun isBefore16(a: Int, b: Int): Boolean =
    (((a - b) and 0xffff) > 0x8000)

private fun versionAtLeast(version: String?, major: Int, minor: Int, patch: Int): Boolean {
    val parts = version
        ?.split('.', '-')
        ?.mapNotNull { it.toIntOrNull() }
        .orEmpty()
    val actualMajor = parts.getOrNull(0) ?: return true
    val actualMinor = parts.getOrNull(1) ?: 0
    val actualPatch = parts.getOrNull(2) ?: 0
    return when {
        actualMajor != major -> actualMajor > major
        actualMinor != minor -> actualMinor > minor
        else -> actualPatch >= patch
    }
}

private const val TAG = "BclawSunshineStream"
private const val MIME_AVC = "video/avc"
private const val MIME_AV1 = "video/av01"
private const val FIXED_RTP_HEADER_SIZE = 12
private const val RTP_EXTENSION_SIZE = 4
private const val RTP_FLAG_EXTENSION = 0x10
private const val NV_VIDEO_PACKET_SIZE = 16
private const val FLAG_CONTAINS_PIC_DATA = 0x1
private const val FLAG_EOF = 0x2
private const val FLAG_SOF = 0x4
private const val FRAME_TYPE_IDR = 2
private const val H264_NAL_IDR = 5
private const val H264_NAL_SEI = 6
private const val H264_NAL_SPS = 7
private const val H264_NAL_PPS = 8
private const val H264_NAL_AUD = 9
private const val INPUT_TIMEOUT_US = 10_000L
private const val CONTROL_PING_INTERVAL_NANOS = 100_000_000L
private const val VIDEO_PING_INTERVAL_NANOS = 500_000_000L
private const val STATS_INTERVAL_NANOS = 500_000_000L
private const val VIDEO_STARTUP_DECODE_TIMEOUT_NANOS = 8_000_000_000L
private const val VIDEO_DECODE_STALL_TIMEOUT_NANOS = 12_000_000_000L
