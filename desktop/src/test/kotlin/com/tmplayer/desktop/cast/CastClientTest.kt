package com.tmplayer.desktop.cast

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * The Cast protocol client against a pretend receiver on loopback: TLS with its own self-signed
 * certificate, as a Chromecast has, and the platform and Default Media Receiver answering the
 * way the protocol describes. Checks the order of things (CONNECT, LAUNCH, CONNECT to the app,
 * LOAD) and that statuses come back as positions and states.
 */
class CastClientTest {

    private lateinit var dir: File
    private lateinit var server: SSLServerSocket
    private val seen = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var playerState = "IDLE"
    private var currentTime = 0.0
    private var contentId = ""

    @Before
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("cast-receiver").toFile()
        val store = File(dir, "receiver.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val made = ProcessBuilder(
            keytool, "-genkeypair", "-alias", "receiver", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=receiver", "-keystore", store.path, "-storetype", "PKCS12", "-storepass", "secret", "-keypass", "secret",
        ).redirectErrorStream(true).start()
        made.inputStream.readBytes()
        check(made.waitFor() == 0) { "keytool failed" }
        val keys = KeyStore.getInstance("PKCS12").apply { FileInputStream(store).use { load(it, "secret".toCharArray()) } }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, "secret".toCharArray()) }
        val context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        server = context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        Thread({ serve() }, "fake-receiver").apply { isDaemon = true; start() }
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
        dir.deleteRecursively()
    }

    private fun serve() {
        val socket = runCatching { server.accept() as SSLSocket }.getOrNull() ?: return
        val input = socket.inputStream
        val output = socket.outputStream
        fun reply(to: CastMessage, body: JSONObject) {
            synchronized(output) { CastMessage.write(output, CastMessage(to.destinationId, to.sourceId, to.namespace, body.toString())) }
        }
        fun receiverStatus(request: Int, app: Boolean) = JSONObject()
            .put("type", "RECEIVER_STATUS").put("requestId", request)
            .put(
                "status",
                JSONObject().put("volume", JSONObject().put("level", 0.4).put("muted", false)).apply {
                    if (app) {
                        put(
                            "applications",
                            JSONArray().put(
                                JSONObject().put("appId", CastNamespaces.DEFAULT_MEDIA_RECEIVER).put("sessionId", "session-1").put("transportId", "web-7"),
                            ),
                        )
                    }
                },
            )
        fun mediaStatus(request: Int) = JSONObject()
            .put("type", "MEDIA_STATUS").put("requestId", request)
            .put(
                "status",
                JSONArray().put(
                    JSONObject().put("mediaSessionId", 3).put("playerState", playerState).put("currentTime", currentTime)
                        .put("media", JSONObject().put("contentId", contentId).put("duration", 600.0)),
                ),
            )
        var launched = false
        try {
            while (true) {
                val m = CastMessage.read(input) ?: break
                val json = JSONObject(m.payload ?: "{}")
                seen += m.destinationId to json
                val id = json.optInt("requestId")
                when (json.optString("type")) {
                    "PING" -> reply(m, JSONObject().put("type", "PONG"))
                    "GET_STATUS" -> if (m.namespace == CastNamespaces.RECEIVER) reply(m, receiverStatus(id, launched)) else reply(m, mediaStatus(id))
                    "LAUNCH" -> {
                        launched = true
                        reply(m, receiverStatus(id, true))
                    }
                    "LOAD" -> {
                        contentId = json.getJSONObject("media").getString("contentId")
                        currentTime = json.optDouble("currentTime", 0.0)
                        playerState = "PLAYING"
                        reply(m, mediaStatus(id))
                    }
                    "PAUSE" -> {
                        playerState = "PAUSED"
                        reply(m, mediaStatus(id))
                    }
                    "SEEK" -> {
                        currentTime = json.getDouble("currentTime")
                        reply(m, mediaStatus(id))
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun device() = CastDevice("fake", "Test TV", "Chromecast Ultra", InetAddress.getLoopbackAddress(), server.localPort)

    @Test
    fun `connects, launches the default media receiver and loads a video`() = runBlocking {
        val client = CastClient(device())
        try {
            withTimeout(15_000) { client.connect() }
            assertEquals("web-7", client.transportId)
            assertEquals(0.4, client.volume.value.level, 0.001)
            val order = seen.map { it.second.optString("type") to it.first }
            assertEquals("CONNECT" to CastNamespaces.PLATFORM, order[0])
            assertTrue(order.contains("LAUNCH" to CastNamespaces.PLATFORM))
            assertTrue(order.contains("CONNECT" to "web-7"))

            val answer = client.load(JSONObject().put("contentId", "http://x/video.mp4").put("contentType", "video/mp4").put("streamType", "BUFFERED"), startMs = 90_000)
            assertEquals("MEDIA_STATUS", answer.optString("type"))
            val status = client.media.value
            assertEquals(3, status.mediaSessionId)
            assertTrue(status.playing)
            assertTrue(status.currentTimeMs >= 90_000)
            assertEquals(600_000L, status.durationMs)
            assertEquals("http://x/video.mp4", status.contentId)
            val load = seen.first { it.second.optString("type") == "LOAD" }
            assertEquals("web-7", load.first)
            assertEquals("session-1", load.second.optString("sessionId"))

            client.pause()
            withTimeout(5_000) { client.media.first { it.playerState == "PAUSED" } }
            client.seek(30_000)
            withTimeout(5_000) { client.media.first { it.currentTimeMs == 30_000L } }
            val seek = seen.first { it.second.optString("type") == "SEEK" }.second
            assertEquals(3, seek.getInt("mediaSessionId"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `a playing position runs on between statuses`() {
        val s = RemoteMediaStatus(playerState = "PLAYING", currentTimeMs = 10_000, atNanos = 0)
        assertEquals(12_000L, s.positionAt(2_000_000_000L))
        val paused = s.copy(playerState = "PAUSED")
        assertEquals(10_000L, paused.positionAt(2_000_000_000L))
    }
}
