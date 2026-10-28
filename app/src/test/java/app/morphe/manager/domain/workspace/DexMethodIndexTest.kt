package app.morphe.manager.domain.workspace

import kotlin.test.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexMethodIndexTest {
    @Test fun rejectsTruncatedAndInvalidHeaders() {
        assertFailsWith<IllegalArgumentException> { DexMethodIndex.read(ByteArray(20)) }
        assertFailsWith<IllegalArgumentException> { DexMethodIndex.read(ByteArray(112)) }
    }
    @Test fun emptyDexHasNoMethods() {
        val dex = ByteArray(112)
        byteArrayOf(100, 101, 120, 10).copyInto(dex)
        assertTrue(DexMethodIndex.read(dex).isEmpty())
    }
    @Test fun malformedClassTableCannotReadOutsideDex() {
        val dex = ByteArray(112)
        byteArrayOf(100, 101, 120, 10).copyInto(dex)
        ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN).putInt(96, 1).putInt(100, 112)
        assertFailsWith<IllegalArgumentException> { DexMethodIndex.read(dex) }
    }

    private fun fixture(opcode: Byte): ByteArray {
        val bytes = ByteArray(280)
        byteArrayOf(100, 101, 120, 10).copyInto(bytes)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(56, 3).putInt(60, 112).putInt(64, 2).putInt(68, 124)
        b.putInt(72, 1).putInt(76, 132).putInt(88, 1).putInt(92, 144)
        b.putInt(96, 1).putInt(100, 152)
        b.putInt(112, 240).putInt(116, 253).putInt(120, 261)
        b.putInt(124, 0).putInt(128, 2)
        b.putInt(132, 2).putInt(136, 1).putInt(140, 0)
        b.putShort(144, 0).putShort(146, 0).putInt(148, 1)
        b.putInt(176, 184)
        byteArrayOf(0, 0, 1, 0, 0, 1, 0xc8.toByte(), 1).copyInto(bytes, 184)
        b.putInt(212, 1)
        bytes[216] = opcode
        bytes[240] = 10; "Ltest/App;".toByteArray().copyInto(bytes, 241)
        bytes[253] = 3; "run".toByteArray().copyInto(bytes, 254)
        bytes[261] = 1; bytes[262] = 'V'.code.toByte()
        return bytes
    }
    @Test fun indexesDescriptorsAndDetectsInstructionChanges() {
        val original = DexMethodIndex.read(fixture(0x0e))
        val changed = DexMethodIndex.read(fixture(0x00))
        assertEquals(setOf("Ltest/App;->run()V"), original.keys)
        assertNotEquals(original.values.single(), changed.values.single())
        assertTrue(DexMethodIndex.read(fixture(0x0e), limit = 0).isEmpty())
    }
}
