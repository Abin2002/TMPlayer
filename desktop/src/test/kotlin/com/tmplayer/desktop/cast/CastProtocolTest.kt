package com.tmplayer.desktop.cast

import com.tmplayer.cast.BytesCastSource
import com.tmplayer.cast.CastResource
import com.tmplayer.cast.CastServer
import com.tmplayer.desktop.player.JoinedStreamBytes
import com.tmplayer.desktop.player.LocalFileBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** The Cast protocol's framing and protobuf, mDNS answers, and serving split pieces to a receiver. */
class CastProtocolTest {

    @Test
    fun `a message is framed with its length and reads back the same`() {
        val message = CastMessage("sender-0", "receiver-0", CastNamespaces.CONNECTION, """{"type":"CONNECT"}""")
        val out = ByteArrayOutputStream()
        CastMessage.write(out, message)
        val bytes = out.toByteArray()
        val length = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        assertEquals(bytes.size - 4, length)
        assertEquals(message, CastMessage.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `the protobuf fields are where cast_channel proto puts them`() {
        val encoded = CastMessage("a", "b", "ns", "{}").encode()
        // protocol_version = 0, source "a", destination "b", namespace "ns", STRING, payload "{}".
        val expected = byteArrayOf(
            0x08, 0x00,
            0x12, 0x01, 'a'.code.toByte(),
            0x1A, 0x01, 'b'.code.toByte(),
            0x22, 0x02, 'n'.code.toByte(), 's'.code.toByte(),
            0x28, 0x00,
            0x32, 0x02, '{'.code.toByte(), '}'.code.toByte(),
        )
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun `reads a message whose payload is longer than one varint byte`() {
        val payload = "x".repeat(300)
        val m = CastMessage("sender-1", "web-5", CastNamespaces.MEDIA, payload)
        assertEquals(m, CastMessage.decode(m.encode()))
    }

    @Test
    fun `binary payloads and unknown fields are tolerated`() {
        val m = CastMessage("s", "d", "urn:x-cast:test", binary = byteArrayOf(1, 2, 3))
        // A future field 9, a varint, after the known ones.
        val withExtra = m.encode() + byteArrayOf(0x48, 0x05)
        val back = CastMessage.decode(withExtra)
        assertArrayEquals(byteArrayOf(1, 2, 3), back.binary)
        assertNull(back.payload)
    }

    @Test
    fun `a clean end of stream reads as null`() {
        assertNull(CastMessage.read(ByteArrayInputStream(ByteArray(0))))
    }

    @Test(expected = java.io.IOException::class)
    fun `an absurd length is refused rather than allocated`() {
        CastMessage.read(ByteArrayInputStream(byteArrayOf(0x7F, 0, 0, 0)))
    }

    // ---- mDNS -------------------------------------------------------------------------------

    private fun name(out: ByteArrayOutputStream, n: String) = MdnsPacket.writeName(out, n)

    private fun u16(out: ByteArrayOutputStream, v: Int) {
        out.write(v shr 8); out.write(v and 0xFF)
    }

    private fun record(out: ByteArrayOutputStream, owner: ByteArray, type: Int, data: ByteArray) {
        out.write(owner)
        u16(out, type); u16(out, 0x8001); out.write(byteArrayOf(0, 0, 0x11, 0x94.toByte())); u16(out, data.size); out.write(data)
    }

    /** A Chromecast's answer, with the instance name compressed against its first use. */
    private fun answer(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 3))
        // PTR _googlecast._tcp.local -> Chromecast-abc._googlecast._tcp.local
        val serviceAt = out.size()
        name(out, "_googlecast._tcp.local")
        u16(out, MdnsPacket.PTR); u16(out, 1); out.write(byteArrayOf(0, 0, 0x11, 0x94.toByte()))
        val ptrData = ByteArrayOutputStream()
        val label = "Chromecast-abc".toByteArray()
        ptrData.write(label.size); ptrData.write(label)
        ptrData.write(0xC0 or (serviceAt shr 8)); ptrData.write(serviceAt and 0xFF)
        u16(out, ptrData.size()); val instanceAt = out.size(); out.write(ptrData.toByteArray())
        val instance = byteArrayOf((0xC0 or (instanceAt shr 8)).toByte(), (instanceAt and 0xFF).toByte())
        // TXT
        val txt = ByteArrayOutputStream()
        for (e in listOf("id=abc123", "md=Chromecast Ultra", "fn=Living room TV", "ca=4101", "rs=")) {
            val b = e.toByteArray(); txt.write(b.size); txt.write(b)
        }
        record(out, instance, MdnsPacket.TXT, txt.toByteArray())
        // SRV 0 0 8009 abc.local
        val srv = ByteArrayOutputStream()
        u16(srv, 0); u16(srv, 0); u16(srv, 8009); name(srv, "abc.local")
        record(out, instance, MdnsPacket.SRV, srv.toByteArray())
        // A abc.local -> 192.168.1.50
        val host = ByteArrayOutputStream().also { name(it, "abc.local") }.toByteArray()
        record(out, host, MdnsPacket.A, byteArrayOf(192.toByte(), 168.toByte(), 1, 50))
        return out.toByteArray()
    }

    @Test
    fun `an mdns answer becomes a device`() {
        val devices = MdnsPacket.devices(MdnsPacket.parse(answer()), null)
        assertEquals(1, devices.size)
        val d = devices[0]
        assertEquals("abc123", d.id)
        assertEquals("Living room TV", d.name)
        assertEquals("Chromecast Ultra", d.model)
        assertEquals(InetAddress.getByName("192.168.1.50"), d.address)
        assertEquals(8009, d.port)
        assertTrue(!d.audioOnly)
    }

    @Test
    fun `a speaker is audio only`() {
        assertTrue(CastDevice("x", "Kitchen", "Google Home", InetAddress.getLoopbackAddress(), capabilities = 2052).audioOnly)
    }

    @Test
    fun `the question asks for the googlecast pointer with a unicast reply`() {
        val q = MdnsPacket.query(CastDiscovery.SERVICE)
        assertEquals(1, q[5].toInt())
        val (name, end) = MdnsPacket.readName(q, 12)
        assertEquals("_googlecast._tcp.local", name)
        assertEquals(MdnsPacket.PTR, q[end + 1].toInt())
        assertEquals(0x80, q[end + 2].toInt() and 0xFF)
    }

    // ---- the server over the desktop's split pieces -------------------------------------------

    @Test
    fun `byte ranges across split pieces come off every piece`() {
        val dir = kotlin.io.path.createTempDirectory("cast-pieces").toFile()
        try {
            val whole = ByteArray(250_000) { (it % 251).toByte() }
            val cuts = listOf(0, 100_000, 100_003, 250_000)
            val files = (0 until 3).map { i ->
                File(dir, "video.mkv.00${i + 1}").apply { writeBytes(whole.copyOfRange(cuts[i], cuts[i + 1])) }
            }
            val joined = JoinedStreamBytes(files.map { it.length() }, open = { index, _ -> LocalFileBytes(files[index]) })
            val server = CastServer(InetAddress.getLoopbackAddress()).apply { start() }
            try {
                val url = server.publish("video.mkv", CastResource.Ranged(StreamBytesCastSource(joined), "video/x-matroska"))
                fun get(range: String): Pair<Int, ByteArray> {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.setRequestProperty("Range", range)
                    return c.responseCode to c.inputStream.use { it.readBytes() }
                }
                val (code, seam) = get("bytes=99990-100010")
                assertEquals(206, code)
                assertArrayEquals(whole.copyOfRange(99_990, 100_011), seam)
                assertArrayEquals(whole.copyOfRange(100_001, 100_004), get("bytes=100001-100003").second)
                assertArrayEquals(whole.copyOfRange(249_000, 250_000), get("bytes=-1000").second)
                assertArrayEquals(whole, get("bytes=0-").second)
            } finally {
                server.stop()
                joined.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a direct play source reads what it was given`() {
        val bytes = ByteArray(1000) { it.toByte() }
        val server = CastServer(InetAddress.getLoopbackAddress()).apply { start() }
        try {
            val url = server.publish("v.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
            val c = URL(url).openConnection() as HttpURLConnection
            assertEquals(200, c.responseCode)
            assertEquals("bytes", c.getHeaderField("Accept-Ranges"))
            assertArrayEquals(bytes, c.inputStream.use { it.readBytes() })
        } finally {
            server.stop()
        }
    }
}
