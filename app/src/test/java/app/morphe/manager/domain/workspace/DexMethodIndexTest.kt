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
}
