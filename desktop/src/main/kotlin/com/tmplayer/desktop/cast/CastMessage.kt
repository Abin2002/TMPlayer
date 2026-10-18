package com.tmplayer.desktop.cast

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One message of the Cast protocol (CastV2): the protobuf `CastMessage` from Chromium's
 * cast_channel.proto, sent over TLS to port 8009 with a four byte big endian length in front.
 *
 *     message CastMessage {
 *       required ProtocolVersion protocol_version = 1;  // CASTV2_1_0 = 0
 *       required string source_id = 2;
 *       required string destination_id = 3;
 *       required string namespace = 4;
 *       required PayloadType payload_type = 5;          // STRING = 0, BINARY = 1
 *       optional string payload_utf8 = 6;
 *       optional bytes payload_binary = 7;
 *     }
 *
 * Seven fields of two wire types are all it has, so it is written and read by hand here rather
 * than through a protobuf library. Every namespace TMPlayer speaks carries JSON in payload_utf8.
 */
data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: String? = null,
    val binary: ByteArray? = null,
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        varintField(out, 1, 0)
        stringField(out, 2, sourceId.toByteArray(Charsets.UTF_8))
        stringField(out, 3, destinationId.toByteArray(Charsets.UTF_8))
        stringField(out, 4, namespace.toByteArray(Charsets.UTF_8))
        varintField(out, 5, if (binary != null) 1 else 0)
        if (binary != null) stringField(out, 7, binary) else stringField(out, 6, (payload ?: "").toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    override fun equals(other: Any?): Boolean =
        other is CastMessage && sourceId == other.sourceId && destinationId == other.destinationId &&
            namespace == other.namespace && payload == other.payload && (binary?.contentEquals(other.binary) ?: (other.binary == null))

    override fun hashCode(): Int = listOf(sourceId, destinationId, namespace, payload).hashCode()

    companion object {
        /** Bigger than any message a receiver sends; a length past it means the stream is out of step. */
        const val MAX_MESSAGE = 64 * 1024

        fun decode(bytes: ByteArray): CastMessage {
            var p = 0
            var source = ""
            var destination = ""
            var namespace = ""
            var payload: String? = null
            var binary: ByteArray? = null
            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (p >= bytes.size) throw IOException("Truncated varint")
                    val b = bytes[p++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IOException("Varint too long")
                }
            }
            while (p < bytes.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> varint()
                    2 -> {
                        val length = varint().toInt()
                        if (length < 0 || p + length > bytes.size) throw IOException("Truncated field $field")
                        val value = bytes.copyOfRange(p, p + length)
                        p += length
                        when (field) {
                            2 -> source = String(value, Charsets.UTF_8)
                            3 -> destination = String(value, Charsets.UTF_8)
                            4 -> namespace = String(value, Charsets.UTF_8)
                            6 -> payload = String(value, Charsets.UTF_8)
                            7 -> binary = value
                        }
                    }
                    1 -> p += 8
                    5 -> p += 4
                    else -> throw IOException("Unknown wire type in field $field")
                }
            }
            return CastMessage(source, destination, namespace, payload, binary)
        }

        /** Writes [message] with its length in front. */
        fun write(out: OutputStream, message: CastMessage) {
            val body = message.encode()
            val n = body.size
            out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()) + body)
            out.flush()
        }

        /** The next message on [input], or null at a clean end of the stream. */
        fun read(input: InputStream): CastMessage? {
            val data = DataInputStream(input)
            val first = data.read()
            if (first < 0) return null
            val rest = ByteArray(3)
            try {
                data.readFully(rest)
            } catch (e: EOFException) {
                return null
            }
            val n = (first shl 24) or ((rest[0].toInt() and 0xFF) shl 16) or ((rest[1].toInt() and 0xFF) shl 8) or (rest[2].toInt() and 0xFF)
            if (n < 0 || n > MAX_MESSAGE) throw IOException("Message length $n out of range")
            val body = ByteArray(n)
            data.readFully(body)
            return decode(body)
        }

        private fun varintField(out: ByteArrayOutputStream, field: Int, value: Long) {
            writeVarint(out, (field.toLong() shl 3) or 0)
            writeVarint(out, value)
        }

        private fun stringField(out: ByteArrayOutputStream, field: Int, value: ByteArray) {
            writeVarint(out, (field.toLong() shl 3) or 2)
            writeVarint(out, value.size.toLong())
            out.write(value)
        }

        private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }
}

/** The namespaces of the Cast protocol TMPlayer speaks, and the receiver it launches. */
object CastNamespaces {
    const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val RECEIVER = "urn:x-cast:com.google.cast.receiver"
    const val MEDIA = "urn:x-cast:com.google.cast.media"

    /** Google's Default Media Receiver: no receiver app of TMPlayer's own to register or host. */
    const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"

    const val PLATFORM = "receiver-0"
}
