package app.morphe.manager.domain.workspace

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Bounded DEX inspection. Hashes instruction bytes, not semantics: index renumbering may change hashes. */
object DexMethodIndex {
    fun read(bytes: ByteArray, limit: Int = 50000): Map<String, String> {
        require(bytes.size >= 112 && bytes.size <= 64 * 1024 * 1024)
        require(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) { "Unsupported DEX header" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun int(at: Int): Int { require(at >= 0 && at <= bytes.size - 4); return b.getInt(at) }
        fun short(at: Int): Int { require(at >= 0 && at <= bytes.size - 2); return b.getShort(at).toInt() and 0xffff }
        fun uleb(cursor: IntArray): Int {
            var value = 0
            for (i in 0..4) { val pos = cursor[0]++; require(pos in bytes.indices); val x = bytes[pos].toInt() and 255
                if (i == 4) require(x <= 15)
                value = value or ((x and 127) shl (i * 7)); if (x and 128 == 0) return value }
            error("Invalid DEX integer")
        }
        val strings = int(60); val types = int(68); val protos = int(76); val methods = int(92)
        val stringCount = int(56); val typeCount = int(64); val protoCount = int(72); val methodCount = int(88)
        fun string(index: Int): String {
            require(index in 0 until stringCount)
            val cursor = intArrayOf(int(strings + index * 4)); uleb(cursor)
            val start = cursor[0]; var end = start
            while (end < bytes.size && bytes[end] != 0.toByte() && end - start < 4096) end++
            require(end < bytes.size && end - start < 4096)
            return String(bytes, start, end - start, Charsets.UTF_8)
        }
        fun type(index: Int): String { require(index in 0 until typeCount); return string(int(types + index * 4)) }
        fun name(index: Int): String {
            require(index in 0 until methodCount)
            val at = methods + index * 8; val proto = short(at + 2); require(proto < protoCount)
            val p = protos + proto * 12; val args = int(p + 8)
            val params = if (args == 0) "" else {
                val size = int(args); require(size in 0..255)
                (0 until size).joinToString("") { type(short(args + 4 + it * 2)) }
            }
            return type(short(at)) + "->" + string(int(at + 4)) + "(" + params + ")" + type(int(p + 4))
        }
        val result = linkedMapOf<String, String>(); val count = int(96); val classes = int(100)
        require(count in 0..200000)
        for (i in 0 until count) {
            if (result.size >= limit) break
            val data = int(classes + i * 32 + 24); if (data == 0) continue
            val cursor = intArrayOf(data)
            val static = uleb(cursor); val instance = uleb(cursor); val direct = uleb(cursor); val virtual = uleb(cursor)
            require(listOf(static, instance, direct, virtual).all { it in 0..200000 })
            repeat(static + instance) { uleb(cursor); uleb(cursor) }
            for (n in listOf(direct, virtual)) {
                var index = 0
                repeat(n) {
                    index += uleb(cursor); uleb(cursor); val code = uleb(cursor)
                    if (result.size < limit) {
                        val hash = if (code == 0) "no-code" else {
                            val units = int(code + 12); require(units in 0..(bytes.size / 2))
                            val start = code + 16; val end = start.toLong() + units.toLong() * 2
                            require(start >= 0 && end <= bytes.size)
                            MessageDigest.getInstance("SHA-256").apply { update(bytes, start, units * 2) }.digest().joinToString("") { "%02x".format(it) }
                        }
                        result[name(index)] = hash
                    }
                }
            }
        }
        return result
    }
}
