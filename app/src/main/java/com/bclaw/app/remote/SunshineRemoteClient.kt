package com.bclaw.app.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit

private val RemoteHttpClient = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .build()

private val RemoteAiInputHttpClient = RemoteHttpClient.newBuilder()
    .readTimeout(70, TimeUnit.SECONDS)
    .callTimeout(75, TimeUnit.SECONDS)
    .build()

private fun Request.Builder.hostAgentAuth(token: String?): Request.Builder = apply {
    val trimmed = token?.trim().orEmpty()
    if (trimmed.isNotEmpty()) {
        header("Authorization", "Bearer $trimmed")
    }
}

data class SunshineStatus(
    val installed: Boolean,
    val appPath: String?,
    val binaryPath: String?,
    val running: Boolean,
    val hostPlatform: String?,
    val apiUrl: String,
    val apiAvailable: Boolean,
    val apiError: String?,
    val streamHost: String,
    val streamHosts: List<String>,
    val streamTransport: String,
    val streamClient: String,
    val pairStatus: String,
    val appVersion: String?,
    val streamState: String?,
    val selectedDisplayId: String?,
    val selectedDisplayName: String?,
    val displays: List<SunshineDisplay>,
    val wake: SunshineWakeState,
) {
    val ready: Boolean get() = installed && running && apiAvailable
    val paired: Boolean get() = pairStatus == "1"
}

data class SunshineDisplay(
    val id: String,
    val name: String,
    val connected: Boolean,
    val selected: Boolean,
)

data class SunshineWakeTarget(
    val interfaceName: String,
    val address: String,
    val broadcast: String,
    val macAddress: String,
    val privateLan: Boolean,
    val tailnet: Boolean,
)

data class SunshineWakeState(
    val supported: Boolean,
    val targets: List<SunshineWakeTarget>,
    val acWakeOnMagicPacket: Boolean,
    val batteryWakeOnMagicPacket: Boolean,
    val acSystemSleepDisabled: Boolean,
)

data class SunshineApp(
    val index: Int,
    val name: String,
    val gameStreamAppId: Int,
    val desktop: Boolean,
)

data class SunshineCatalog(
    val status: SunshineStatus,
    val apps: List<SunshineApp>,
)

data class SunshineLaunchPlan(
    val appName: String,
    val state: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val codec: String,
    val pairStatus: String,
    val rtspPort: Int,
    val rtspSessionUrl: String?,
    val streamHosts: List<String>,
    val displayId: String?,
    val displayName: String?,
    val riKey: String?,
    val riKeyId: Int?,
    val appVersion: String?,
)

data class RemoteAiInputResult(
    val text: String,
    val model: String?,
    val reasoningEffort: String?,
)

data class RtspProbeResult(
    val target: String,
    val optionsStatus: Int,
    val describeStatus: Int,
    val setupAudioStatus: Int,
    val setupVideoStatus: Int,
    val setupControlStatus: Int,
    val announceStatus: Int,
    val playStatus: Int,
    val sessionId: String?,
    val audioPort: Int?,
    val videoPort: Int?,
    val controlPort: Int?,
    val videoPingPayload: String?,
    val controlConnectData: String?,
    val controlConnected: Boolean,
    val controlEncrypted: Boolean,
    val controlError: String?,
    val videoPackets: Int,
    val sdpBytes: Int,
    val codecSummary: String,
)

data class SunshineStreamRequest(
    val appIndex: Int,
    val displayId: String? = null,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 60,
    val bitrateKbps: Int = 18_000,
    val codec: String = "h264",
)

suspend fun fetchSunshineCatalog(base: String, token: String? = null): SunshineCatalog = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$base/v1/sunshine/catalog")
        .hostAgentAuth(token)
        .get()
        .build()
    RemoteHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(raw.ifBlank { "HTTP ${response.code}" })
        }
        parseSunshineCatalog(raw)
    }
}

suspend fun invokeSunshineAction(base: String, path: String, token: String? = null) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$base$path")
        .hostAgentAuth(token)
        .get()
        .build()
    RemoteHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(raw.ifBlank { "HTTP ${response.code}" })
        }
    }
}

suspend fun selectSunshineDisplay(base: String, displayId: String, token: String? = null) = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("displayId", displayId)
        .toString()
    val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url("$base/v1/sunshine/display/select")
        .hostAgentAuth(token)
        .post(body)
        .build()
    RemoteHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(raw.ifBlank { "HTTP ${response.code}" })
        }
    }
}

suspend fun sendWakeOnLan(targets: List<SunshineWakeTarget>): Int = withContext(Dispatchers.IO) {
    if (targets.isEmpty()) return@withContext 0
    var sent = 0
    DatagramSocket().use { socket ->
        socket.broadcast = true
        targets.forEach { target ->
            val packet = buildWakeOnLanPacket(target.macAddress) ?: return@forEach
            val destinations = listOf(
                target.broadcast,
                "255.255.255.255",
            ).filter { it.isNotBlank() }.distinct()
            destinations.forEach { destination ->
                listOf(9, 7).forEach { port ->
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                packet,
                                packet.size,
                                InetAddress.getByName(destination),
                                port,
                            ),
                        )
                        sent += 1
                    }
                }
            }
        }
    }
    sent
}

suspend fun startSunshineSession(
    base: String,
    request: SunshineStreamRequest,
    token: String? = null,
): SunshineLaunchPlan = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("appIndex", request.appIndex)
        .put("displayId", request.displayId ?: JSONObject.NULL)
        .put("width", request.width)
        .put("height", request.height)
        .put("fps", request.fps)
        .put("bitrateKbps", request.bitrateKbps)
        .put("codec", request.codec)
        .toString()
    val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
    val httpRequest = Request.Builder()
        .url("$base/v1/sunshine/session/start")
        .hostAgentAuth(token)
        .post(body)
        .build()
    RemoteHttpClient.newCall(httpRequest).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(raw.ifBlank { "HTTP ${response.code}" })
        }
        parseSunshineLaunchPlan(raw)
    }
}

suspend fun startSunshineSession(
    base: String,
    app: SunshineApp?,
    displayId: String? = null,
    token: String? = null,
): SunshineLaunchPlan = startSunshineSession(
    base = base,
    request = SunshineStreamRequest(appIndex = app?.index ?: 0, displayId = displayId),
    token = token,
)

suspend fun sendRemoteMacosPinch(base: String, amount: Int, token: String? = null) {
    if (amount == 0) return
    withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("amount", amount)
            .toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$base/v1/input/macos/pinch")
            .hostAgentAuth(token)
            .post(body)
            .build()
        RemoteHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                throw IOException(raw.ifBlank { "HTTP ${response.code}" })
            }
        }
    }
}

suspend fun generateRemoteAiInputText(
    base: String,
    instruction: String,
    model: String = "gpt-5.4-mini",
    reasoningEffort: String = "none",
    token: String? = null,
): RemoteAiInputResult = withContext(Dispatchers.IO) {
    val payload = JSONObject()
        .put("instruction", instruction)
        .put("model", model)
        .put("reasoningEffort", reasoningEffort)
        .toString()
    val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url("$base/v1/input/ai/text")
        .hostAgentAuth(token)
        .post(body)
        .build()
    RemoteAiInputHttpClient.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IOException(raw.ifBlank { "HTTP ${response.code}" })
        }
        val json = JSONObject(raw)
        if (!json.optBoolean("ok", false)) {
            throw IOException(json.optString("error", "AI input failed"))
        }
        RemoteAiInputResult(
            text = json.optString("text"),
            model = json.optString("model").takeIf { it.isNotBlank() },
            reasoningEffort = json.optString("reasoningEffort").takeIf { it.isNotBlank() },
        )
    }
}

suspend fun probeSunshineRtsp(
    base: String,
    plan: SunshineLaunchPlan,
): RtspProbeResult = withContext(Dispatchers.IO) {
    val targetHost = resolveSunshineTargetHost(base, plan)
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
    val videoPort = setupVideo.serverPort()
    val videoPingPayload = setupVideo.headers["x-ss-ping-payload"]
    val controlPort = setupControl.serverPort()
    val controlConnectData = setupControl.headers["x-ss-connect-data"]
    val controlEncrypted = isEncryptedControlAppVersion(plan.appVersion)
    val announceSdp = buildSunshineAnnounceSdp(
        targetHost = targetHost,
        plan = plan,
        videoPort = videoPort,
        encryptionEnabled = if (controlEncrypted) 1 else 0,
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
    val (controlProbe, videoPackets) = if (videoPort != null) {
        runSunshineControlProbe(
            host = targetHost,
            port = controlPort,
            connectData = controlConnectData,
            riKey = plan.riKey,
            encrypted = controlEncrypted,
        ) {
            countVideoPackets(
                host = targetHost,
                audioPort = setupAudio.serverPort(),
                videoPort = videoPort,
                pingPayload = videoPingPayload,
            )
        }
    } else {
        SunshineControlProbe(false, controlEncrypted, "missing video port") to 0
    }

    RtspProbeResult(
        target = target,
        optionsStatus = options.statusCode,
        describeStatus = describe.statusCode,
        setupAudioStatus = setupAudio.statusCode,
        setupVideoStatus = setupVideo.statusCode,
        setupControlStatus = setupControl.statusCode,
        announceStatus = announce.statusCode,
        playStatus = play.statusCode,
        sessionId = sessionId,
        audioPort = setupAudio.serverPort(),
        videoPort = videoPort,
        controlPort = controlPort,
        videoPingPayload = videoPingPayload,
        controlConnectData = controlConnectData,
        controlConnected = controlProbe.connected,
        controlEncrypted = controlProbe.encrypted,
        controlError = controlProbe.error,
        videoPackets = videoPackets,
        sdpBytes = describe.body.length,
        codecSummary = summarizeSdpCodecs(describe.body),
    )
}

internal data class RtspResponse(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
)

internal fun resolveSunshineTargetHost(base: String, plan: SunshineLaunchPlan): String {
    return resolveSunshineTargetHosts(base, plan).first()
}

internal fun resolveSunshineTargetHosts(base: String, plan: SunshineLaunchPlan): List<String> {
    val hostAgentHost = URI(base).host ?: throw IOException("missing host-agent host")
    val sessionHost = plan.rtspSessionUrl
        ?.let { runCatching { URI(it).host }.getOrNull() }
    return listOf(hostAgentHost, sessionHost)
        .plus(plan.streamHosts)
        .mapNotNull(::normalizeSunshineTargetHost)
        .distinct()
        .ifEmpty { listOf(hostAgentHost) }
}

private fun normalizeSunshineTargetHost(host: String?): String? {
    val normalized = host
        ?.trim()
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    if (normalized == "0.0.0.0" || normalized == "::") return null
    if (normalized == "127.0.0.1" || normalized == "::1") return null
    if (normalized.equals("localhost", ignoreCase = true)) return null
    return normalized
}

internal fun buildRtspRequest(
    method: String,
    target: String,
    host: String,
    cseq: Int,
    extraHeaders: List<Pair<String, String>> = emptyList(),
    body: String? = null,
): String = buildString {
    append(method).append(' ').append(target).append(" RTSP/1.0\r\n")
    append("CSeq: ").append(cseq).append("\r\n")
    append("X-GS-ClientVersion: 14\r\n")
    append("Host: ").append(host).append("\r\n")
    extraHeaders.forEach { (name, value) ->
        append(name).append(": ").append(value).append("\r\n")
    }
    append("\r\n")
    if (body != null) {
        append(body)
    }
}

internal fun buildSunshineAnnounceSdp(
    targetHost: String,
    plan: SunshineLaunchPlan,
    videoPort: Int?,
    encryptionEnabled: Int,
    videoCodec: String = plan.codec,
): String {
    val packetSize = 1024
    val adjustedBitrate = (plan.bitrateKbps * 0.80).toInt().coerceAtMost(100_000)
    val refreshRateX100 = plan.fps * 100
    return buildString {
        append("v=0\r\n")
        append("o=android 0 14 IN IPv4 ").append(targetHost).append("\r\n")
        append("s=NVIDIA Streaming Client\r\n")
        appendSdpAttribute("x-ml-general.featureFlags", "3")
        appendSdpAttribute("x-ss-general.encryptionEnabled", encryptionEnabled.toString())
        appendSdpAttribute("x-ss-video[0].chromaSamplingType", "0")
        appendSdpAttribute("x-nv-video[0].clientViewportWd", plan.width.toString())
        appendSdpAttribute("x-nv-video[0].clientViewportHt", plan.height.toString())
        appendSdpAttribute("x-nv-video[0].maxFPS", plan.fps.toString())
        appendSdpAttribute("x-nv-video[0].packetSize", packetSize.toString())
        appendSdpAttribute("x-nv-video[0].rateControlMode", "4")
        appendSdpAttribute("x-nv-video[0].timeoutLengthMs", "7000")
        appendSdpAttribute("x-nv-video[0].framesWithInvalidRefThreshold", "0")
        appendSdpAttribute("x-nv-video[0].initialBitrateKbps", adjustedBitrate.toString())
        appendSdpAttribute("x-nv-video[0].initialPeakBitrateKbps", adjustedBitrate.toString())
        appendSdpAttribute("x-nv-vqos[0].bw.minimumBitrateKbps", adjustedBitrate.toString())
        appendSdpAttribute("x-nv-vqos[0].bw.maximumBitrateKbps", adjustedBitrate.toString())
        appendSdpAttribute("x-ml-video.configuredBitrateKbps", plan.bitrateKbps.toString())
        appendSdpAttribute("x-nv-vqos[0].fec.enable", "1")
        appendSdpAttribute("x-nv-vqos[0].fec.minRequiredFecPackets", "2")
        appendSdpAttribute("x-nv-vqos[0].bllFec.enable", "0")
        appendSdpAttribute("x-nv-vqos[0].videoQualityScoreUpdateTime", "5000")
        appendSdpAttribute("x-nv-vqos[0].qosTrafficType", "5")
        appendSdpAttribute("x-nv-aqos.qosTrafficType", "4")
        appendSdpAttribute("x-nv-general.featureFlags", "135")
        appendSdpAttribute("x-nv-general.useReliableUdp", "13")
        appendSdpAttribute("x-nv-vqos[0].drc.enable", "0")
        appendSdpAttribute("x-nv-general.enableRecoveryMode", "0")
        appendSdpAttribute("x-nv-video[0].videoEncoderSlicesPerFrame", "1")
        if (videoCodec.equals("av1", ignoreCase = true)) {
            appendSdpAttribute("x-nv-vqos[0].bitStreamFormat", "2")
        } else {
            appendSdpAttribute("x-nv-clientSupportHevc", "0")
            appendSdpAttribute("x-nv-vqos[0].bitStreamFormat", "0")
        }
        appendSdpAttribute("x-nv-video[0].dynamicRangeMode", "0")
        appendSdpAttribute("x-nv-video[0].maxNumReferenceFrames", "1")
        appendSdpAttribute("x-nv-video[0].clientRefreshRateX100", refreshRateX100.toString())
        appendSdpAttribute("x-nv-audio.surround.numChannels", "2")
        appendSdpAttribute("x-nv-audio.surround.channelMask", "3")
        appendSdpAttribute("x-nv-audio.surround.enable", "0")
        appendSdpAttribute("x-nv-audio.surround.AudioQuality", "0")
        appendSdpAttribute("x-nv-aqos.packetDuration", "5")
        appendSdpAttribute("x-nv-video[0].encoderCscMode", "0")
        append("t=0 0\r\n")
        append("m=video ").append(videoPort ?: 47998).append("  \r\n")
    }
}

private fun StringBuilder.appendSdpAttribute(name: String, value: String) {
    append("a=").append(name).append(":").append(value).append(" \r\n")
}

internal fun transactRtsp(host: String, port: Int, request: String): RtspResponse {
    Socket().use { socket ->
        socket.tcpNoDelay = true
        socket.soTimeout = 4_000
        socket.connect(InetSocketAddress(host, port), 4_000)
        val output = socket.getOutputStream()
        output.write(request.toByteArray(Charsets.UTF_8))
        output.flush()
        return readRtspResponse(BufferedInputStream(socket.getInputStream()))
    }
}

private fun readRtspResponse(input: BufferedInputStream): RtspResponse {
    val headerBytes = ArrayList<Byte>(2048)
    var matched = 0
    val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
    while (matched < marker.size) {
        val value = input.read()
        if (value < 0) throw IOException("RTSP socket closed")
        val byte = value.toByte()
        headerBytes.add(byte)
        matched = if (byte == marker[matched]) matched + 1 else if (byte == marker[0]) 1 else 0
        if (headerBytes.size > 64 * 1024) throw IOException("RTSP header too large")
    }

    val headerText = headerBytes.toByteArray().toString(Charsets.UTF_8)
    val lines = headerText.trimEnd().split("\r\n")
    val statusCode = lines.firstOrNull()
        ?.split(" ")
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: throw IOException("invalid RTSP response")
    val headers = lines.drop(1)
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).lowercase() to line.substring(separator + 1).trim()
        }
        .toMap()
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    val bodyBytes = if (contentLength > 0) {
        val fixed = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val count = input.read(fixed, read, contentLength - read)
            if (count < 0) throw IOException("RTSP body closed")
            read += count
        }
        fixed
    } else {
        val body = ArrayList<Byte>(4096)
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            for (i in 0 until count) body.add(buffer[i])
            if (body.size > 1024 * 1024) throw IOException("RTSP body too large")
        }
        body.toByteArray()
    }
    return RtspResponse(statusCode, headers, bodyBytes.toString(Charsets.UTF_8))
}

internal fun RtspResponse.sessionId(): String? =
    headers["session"]
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal fun RtspResponse.serverPort(): Int? =
    headers["transport"]
        ?.substringAfter("server_port=", missingDelimiterValue = "")
        ?.takeWhile { it.isDigit() }
        ?.toIntOrNull()

private fun countVideoPackets(
    host: String,
    audioPort: Int?,
    videoPort: Int,
    pingPayload: String?,
): Int {
    DatagramSocket(null).use { socket ->
        socket.soTimeout = 120
        socket.bind(InetSocketAddress(0))
        val videoTarget = InetSocketAddress(host, videoPort)
        val audioTarget = audioPort?.let { InetSocketAddress(host, it) }
        var packets = 0
        var sequence = 1
        val buffer = ByteArray(1600)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(3_200)
        while (System.nanoTime() < deadline) {
            val ping = buildVideoPing(pingPayload, sequence++)
            socket.send(DatagramPacket(ping, ping.size, videoTarget))
            if (audioTarget != null) {
                socket.send(DatagramPacket(ping, ping.size, audioTarget))
            }
            while (true) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    packets += 1
                } catch (_: IOException) {
                    break
                }
            }
            Thread.sleep(120)
        }
        return packets
    }
}

internal fun buildVideoPing(payload: String?, sequence: Int): ByteArray {
    if (payload != null && payload.length == 16) {
        val out = ByteArray(20)
        payload.toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        out[16] = ((sequence ushr 24) and 0xff).toByte()
        out[17] = ((sequence ushr 16) and 0xff).toByte()
        out[18] = ((sequence ushr 8) and 0xff).toByte()
        out[19] = (sequence and 0xff).toByte()
        return out
    }
    return "PING".toByteArray(Charsets.US_ASCII)
}

internal const val AUDIO_CLIENT_PORT = 50000
internal const val VIDEO_CLIENT_PORT = 50000
internal const val CONTROL_CLIENT_PORT = 50000

internal fun summarizeSdpCodecs(sdp: String): String {
    val codecs = buildList {
        if (sdp.contains("AV1/90000", ignoreCase = true)) add("AV1")
        if (sdp.contains("sprop-parameter-sets=AAAAAU", ignoreCase = true)) add("HEVC")
        if (sdp.contains("H264/90000", ignoreCase = true)) add("H264")
        if (sdp.contains("opus", ignoreCase = true)) add("Opus")
    }
    return codecs.distinct().joinToString("+").ifBlank { "SDP" }
}

internal fun parseSdpAttributeUInt(sdp: String, name: String): Int? {
    val prefix = "a=$name:"
    return sdp
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.trim()
        ?.substringBefore(' ')
        ?.let { raw ->
            if (raw.startsWith("0x", ignoreCase = true)) {
                raw.substring(2).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
        }
}

private fun parseSunshineCatalog(raw: String): SunshineCatalog {
    val json = JSONObject(raw)
    val appsJson = json.optJSONArray("apps")
    val apps = buildList {
        if (appsJson != null) {
            for (index in 0 until appsJson.length()) {
                val app = appsJson.optJSONObject(index) ?: continue
                add(
                    SunshineApp(
                        index = app.optInt("index", index),
                        name = app.optString("name", "App ${index + 1}"),
                        gameStreamAppId = app.optInt("gameStreamAppId", index),
                        desktop = app.optBoolean("desktop"),
                    ),
                )
            }
        }
    }
    return SunshineCatalog(
        status = parseSunshineStatus(json.optJSONObject("status") ?: json),
        apps = apps,
    )
}

private fun parseSunshineStatus(json: JSONObject): SunshineStatus {
    val installed = json.optJSONObject("installed")
    val api = json.optJSONObject("api")
    val stream = json.optJSONObject("stream")
    val display = json.optJSONObject("display")
    val wake = json.optJSONObject("wake")
    val displays = parseSunshineDisplays(display)
    val reportedSelectedDisplayId = display?.optString("selectedId")?.takeIf { it.isNotBlank() }
        ?: stream?.optString("displayId")?.takeIf { it.isNotBlank() }
    val reportedSelectedDisplayName = display?.optString("selectedName")?.takeIf { it.isNotBlank() }
        ?: stream?.optString("displayName")?.takeIf { it.isNotBlank() }
    val selectedDisplay = displays.firstOrNull { it.id == reportedSelectedDisplayId && it.connected }
        ?: displays.firstOrNull { it.id == reportedSelectedDisplayId }
        ?: displays.firstOrNull { it.connected }
        ?: displays.firstOrNull()
    return SunshineStatus(
        installed = installed?.optBoolean("available") ?: false,
        appPath = installed?.optString("appPath")?.takeIf { it.isNotBlank() },
        binaryPath = installed?.optString("binaryPath")?.takeIf { it.isNotBlank() },
        running = json.optBoolean("running"),
        hostPlatform = json.optString("hostPlatform").takeIf { it.isNotBlank() },
        apiUrl = api?.optString("url")?.takeIf { it.isNotBlank() } ?: "unknown",
        apiAvailable = api?.optBoolean("available") ?: false,
        apiError = api?.optString("error")?.takeIf { it.isNotBlank() },
        streamHost = stream?.optString("host")?.takeIf { it.isNotBlank() } ?: "unknown",
        streamHosts = stream.optStringArray("hosts"),
        streamTransport = stream?.optString("transport")?.takeIf { it.isNotBlank() } ?: "moonlight-compatible",
        streamClient = stream?.optString("client")?.takeIf { it.isNotBlank() } ?: "bclaw-native-pending",
        pairStatus = stream?.optString("pairStatus")?.takeIf { it.isNotBlank() } ?: "0",
        appVersion = stream?.optString("appVersion")?.takeIf { it.isNotBlank() },
        streamState = stream?.optString("state")?.takeIf { it.isNotBlank() },
        selectedDisplayId = selectedDisplay?.id ?: reportedSelectedDisplayId,
        selectedDisplayName = selectedDisplay?.name ?: reportedSelectedDisplayName,
        displays = displays,
        wake = parseSunshineWakeState(wake),
    )
}

private fun parseSunshineLaunchPlan(raw: String): SunshineLaunchPlan {
    val json = JSONObject(raw)
    val stream = json.optJSONObject("stream")
    val app = json.optJSONObject("app")
    val gameStream = json.optJSONObject("gameStream")
    val serverInfo = gameStream?.optJSONObject("serverInfo")
    val parsed = serverInfo?.optJSONObject("parsed")
    val launch = gameStream?.optJSONObject("launch")
    val launchParsed = launch?.optJSONObject("parsed")
    return SunshineLaunchPlan(
        appName = app?.optString("name")?.takeIf { it.isNotBlank() } ?: "Desktop",
        state = json.optString("state", "ready_for_native_transport"),
        width = stream?.optInt("width", 1280) ?: 1280,
        height = stream?.optInt("height", 720) ?: 720,
        fps = stream?.optInt("fps", 60) ?: 60,
        bitrateKbps = stream?.optInt("bitrateKbps", 18_000) ?: 18_000,
        codec = stream?.optString("codec")?.takeIf { it.isNotBlank() } ?: "h264",
        pairStatus = parsed?.optString("PairStatus")?.takeIf { it.isNotBlank() } ?: "unknown",
        rtspPort = gameStream?.optInt("rtspPort", 48010) ?: 48010,
        rtspSessionUrl = launch?.optString("sessionUrl")?.takeIf { it.isNotBlank() }
            ?: launchParsed?.optString("sessionUrl0")?.takeIf { it.isNotBlank() },
        streamHosts = stream.optStringArray("hosts"),
        displayId = stream?.optString("displayId")?.takeIf { it.isNotBlank() }
            ?: json.optJSONObject("display")?.optString("selectedId")?.takeIf { it.isNotBlank() },
        displayName = stream?.optString("displayName")?.takeIf { it.isNotBlank() }
            ?: json.optJSONObject("display")?.optString("selectedName")?.takeIf { it.isNotBlank() },
        riKey = gameStream?.optString("riKey")?.takeIf { it.isNotBlank() }
            ?: gameStream?.optJSONObject("launchQuery")?.optString("rikey")?.takeIf { it.isNotBlank() },
        riKeyId = if (gameStream?.has("riKeyId") == true) {
            gameStream.optInt("riKeyId")
        } else {
            gameStream?.optJSONObject("launchQuery")?.optInt("rikeyid")
        },
        appVersion = parsed?.optString("appversion")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("AppVersion")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("GfeVersion")?.takeIf { it.isNotBlank() },
    )
}

private fun parseSunshineDisplays(json: JSONObject?): List<SunshineDisplay> {
    val array = json?.optJSONArray("displays") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val display = array.optJSONObject(index) ?: continue
            val id = display.optString("id").takeIf { it.isNotBlank() } ?: continue
            add(
                SunshineDisplay(
                    id = id,
                    name = display.optString("name").takeIf { it.isNotBlank() } ?: "Display $id",
                    connected = display.optBoolean("connected", true),
                    selected = display.optBoolean("selected"),
                ),
            )
        }
    }
}

private fun parseSunshineWakeState(json: JSONObject?): SunshineWakeState {
    val power = json?.optJSONObject("power")
    val targets = buildList {
        val array = json?.optJSONArray("targets")
        if (array != null) {
            for (index in 0 until array.length()) {
                val target = array.optJSONObject(index) ?: continue
                val mac = target.optString("macAddress").takeIf { it.isNotBlank() } ?: continue
                add(
                    SunshineWakeTarget(
                        interfaceName = target.optString("interface").takeIf { it.isNotBlank() } ?: "network",
                        address = target.optString("address"),
                        broadcast = target.optString("broadcast").takeIf { it.isNotBlank() } ?: "255.255.255.255",
                        macAddress = mac,
                        privateLan = target.optBoolean("privateLan"),
                        tailnet = target.optBoolean("tailnet"),
                    ),
                )
            }
        }
    }
    return SunshineWakeState(
        supported = json?.optBoolean("supported") == true && targets.isNotEmpty(),
        targets = targets,
        acWakeOnMagicPacket = power?.optBoolean("acWakeOnMagicPacket") ?: false,
        batteryWakeOnMagicPacket = power?.optBoolean("batteryWakeOnMagicPacket") ?: false,
        acSystemSleepDisabled = power?.optBoolean("acSystemSleepDisabled") ?: false,
    )
}

private fun JSONObject?.optStringArray(name: String): List<String> {
    val array = this?.optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun buildWakeOnLanPacket(macAddress: String): ByteArray? {
    val mac = macAddress
        .split(':', '-')
        .map { it.toIntOrNull(16) }
        .takeIf { it.size == 6 && it.all { part -> part != null && part in 0..255 } }
        ?.map { it!!.toByte() }
        ?.toByteArray()
        ?: return null
    return ByteArray(6 + 16 * mac.size).also { packet ->
        for (index in 0 until 6) packet[index] = 0xff.toByte()
        for (repeat in 0 until 16) {
            mac.copyInto(packet, destinationOffset = 6 + repeat * mac.size)
        }
    }
}
