/*
 * Copyright 2025 Kushnir Vladyslav
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.kushnirvladyslav.Buffers.GlobalBuffer;

import io.github.kushnirvladyslav.memory.buffer.WritableGlobal;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadOnlyStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadOnlyStaticBufferBuilder;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBufferBuilder;
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.junit.jupiter.api.*;



import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalReadOnlyStaticBufferTest extends AbstractGlobalStaticBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalReadOnlyStaticBuffer createBuffer(int capacity) {
        return new GlobalReadOnlyStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalReadOnlyStaticBuffer createBuffer(
            String name, int capacity) {
        return new GlobalReadOnlyStaticBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity);
    }

    @Override
    protected boolean supportsHostRead()  { return true;  }
    @Override
    protected boolean supportsHostWrite() { return false; }

    // ── Helper: plant data via a ReadWrite buffer ────────────────────────────

    /**
     * Creates a ReadWrite buffer of the same capacity, fills it with
     * {@code data}, copies it to {@code dst}, waits for completion,
     * and destroys the temporary buffer.
     */
    private void plant(GlobalReadOnlyStaticBuffer dst, int[] data) {
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, dst.getCapacity());
        tmp.writeSync(data);
        ClEvent ev = dst.copyFrom(tmp);
        ev.waitForComplete();
        tmp.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. HOST WRITE IS FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1100)
    @DisplayName("writeSync is not available on a read-only buffer (no WritableGlobal interface)")
    void readOnly_writeSyncNotAvailable() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        // The buffer must NOT implement WritableGlobal
        assertFalse(buf instanceof WritableGlobal,
                "GlobalReadOnlyStaticBuffer must not implement WritableGlobal");
        buf.destroy();
    }

    @Test
    @Order(1101)
    @DisplayName("writeAsync is not available on a read-only buffer")
    void readOnly_writeAsyncNotAvailable() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        assertFalse(buf instanceof WritableGlobal);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. HOST READ – basic correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1200)
    @DisplayName("readSync(len) returns a non-null array of the requested length")
    void readOnly_readSyncReturnsCorrectLength() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(8);
        plant(buf, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        Object result = buf.readSync(8);
        assertNotNull(result);
        assertTrue(result instanceof int[]);
        assertEquals(8, ((int[]) result).length);
        buf.destroy();
    }

    @Test
    @Order(1201)
    @DisplayName("readSync returns the correct data planted via copyFrom")
    void readOnly_readSyncCorrectData() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        int[] expected = {10, 20, 30, 40};
        plant(buf, expected);
        int[] got = (int[]) buf.readSync(4);
        assertArrayEquals(expected, got);
        buf.destroy();
    }

    @Test
    @Order(1202)
    @DisplayName("readSync(offset, len) reads the correct sub-range")
    void readOnly_readSyncOffsetSlice() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(8);
        plant(buf, new int[]{0, 1, 2, 3, 4, 5, 6, 7});
        int[] got = (int[]) buf.readSync(2, 4);
        assertArrayEquals(new int[]{2, 3, 4, 5}, got);
        buf.destroy();
    }

    @Test
    @Order(1203)
    @DisplayName("readSync with targetArray overload fills the provided array")
    void readOnly_readSyncTargetArray() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        int[] expected = {5, 6, 7, 8};
        plant(buf, expected);
        int[] dest = new int[4];
        buf.readSync(dest);
        assertArrayEquals(expected, dest);
        buf.destroy();
    }


    // ══════════════════════════════════════════════════════════════════════════
    // 13. HOST READ ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1300)
    @DisplayName("readAsync returns a non-null ClEvent")
    void readOnly_readAsyncReturnsEvent() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{1, 2, 3, 4});
        int[] dest = new int[4];
        ClEvent ev = buf.readAsync(dest);
        assertNotNull(ev);
        ev.waitForComplete();
        buf.destroy();
    }

    @Test
    @Order(1301)
    @DisplayName("readAsync fills the target array with correct values on completion")
    void readOnly_readAsyncCorrectData() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        int[] expected = {100, 200, 300, 400};
        plant(buf, expected);
        int[] dest = new int[4];
        buf.readAsync(dest).waitForComplete();
        assertArrayEquals(expected, dest);
        buf.destroy();
    }

    @Test
    @Order(1302)
    @DisplayName("readAsync with offset reads the correct sub-range")
    void readOnly_readAsyncWithOffset() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(6);
        plant(buf, new int[]{0, 1, 2, 3, 4, 5});
        int[] dest = new int[3];
        buf.readAsync(2, dest).waitForComplete();
        assertArrayEquals(new int[]{2, 3, 4}, dest);
        buf.destroy();
    }

    @Test
    @Order(1303)
    @DisplayName("readAsync chained with copyFrom event reads correct data")
    void readOnly_readAsyncChained() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        // Use copyFrom with event chaining
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        tmp.writeSync(new int[]{7, 8, 9, 10});
        ClEvent copyEv = buf.copyFrom(tmp);
        int[] dest = new int[4];
        ClEvent readEv = buf.readAsync(new ClEventList(copyEv), dest);
        readEv.waitForComplete();
        assertArrayEquals(new int[]{7, 8, 9, 10}, dest);
        tmp.destroy();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. READ NEXT (pointer-advancing)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1400)
    @DisplayName("readNextSync advances pointer by len")
    void readOnly_readNextSyncAdvancesPointer() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(8);
        plant(buf, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        buf.readNextSync(3);
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(1401)
    @DisplayName("readNextSync reads sequential chunks in order")
    void readOnly_readNextSyncSequential() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(6);
        plant(buf, new int[]{10, 20, 30, 40, 50, 60});
        int[] a = (int[]) buf.readNextSync(2);
        int[] b = (int[]) buf.readNextSync(2);
        int[] c = (int[]) buf.readNextSync(2);
        assertArrayEquals(new int[]{10, 20}, a);
        assertArrayEquals(new int[]{30, 40}, b);
        assertArrayEquals(new int[]{50, 60}, c);
        buf.destroy();
    }

    @Test
    @Order(1402)
    @DisplayName("readNextSync() no-arg reads exactly 1 element")
    void readOnly_readNextSyncNoArg() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{55, 66, 77, 88});
        int[] got = (int[]) buf.readNextSync();
        assertEquals(1, buf.getPointer());
        assertEquals(55, got[0]);
        buf.destroy();
    }

    @Test
    @Order(1403)
    @DisplayName("readNextSync beyond capacity throws (static – no auto-grow)")
    void readOnly_readNextSyncBeyondCapacityThrows() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{1, 2, 3, 4});
        buf.readNextSync(4);
        assertThrows(Exception.class, () -> buf.readNextSync(1));
        buf.destroy();
    }

    @Test
    @Order(1404)
    @DisplayName("readNextAsync advances pointer and completes with correct data")
    void readOnly_readNextAsyncAdvances() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        int[] expected = {3, 6, 9, 12};
        plant(buf, expected);
        int[] dest = new int[2];
        ClEvent ev = buf.readNextAsync(dest);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        assertArrayEquals(new int[]{3, 6}, dest);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. READ SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1500)
    @DisplayName("readSyncByte returns a byte array of the correct length")
    void readOnly_readSyncByteLength() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{1, 2, 3, 4});
        byte[] bytes = buf.readSyncByte(0, 4);
        assertEquals(4 * Integer.BYTES, bytes.length);
        buf.destroy();
    }

    @Test
    @Order(1501)
    @DisplayName("readSyncByte with targetArray overload fills the array with non-zero bytes")
    void readOnly_readSyncByteTargetArray() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{10, 20, 30, 40});
        byte[] dest = new byte[4 * Integer.BYTES];
        buf.readSyncByte(dest);
        boolean anyNonZero = false;
        for (byte b : dest) if (b != 0) { anyNonZero = true; break; }
        assertTrue(anyNonZero);
        buf.destroy();
    }

    @Test
    @Order(1502)
    @DisplayName("readNextSyncByte advances pointer correctly")
    void readOnly_readNextSyncByteAdvancesPointer() {
        GlobalReadOnlyStaticBuffer buf = createBuffer(4);
        plant(buf, new int[]{1, 2, 3, 4});
        buf.readNextSyncByte(2);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. ROUND-TRIP: plant via ReadWrite → kernel → read via ReadOnly
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1600)
    @DisplayName("Round-trip: plant data, run kernel to negate, read back via ReadOnly")
    void readOnly_roundTripKernelNegate() {
        int n = 16;
        GlobalReadOnlyStaticBuffer buf = createBuffer(n);
        // Plant initial data
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        // Kernel: buf[gid] = -buf[gid]
        // NOTE: kernel must use __global int* (read/write from GPU side)
        clKernel = buildKernel("negate_ro", "",
                "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = (int[]) buf.readSync(n);
        for (int i = 0; i < n; i++) {
            assertEquals(-(i + 1), result[i]);
        }
        buf.destroy();
    }


    // ══════════════════════════════════════════════════════════════════════════
    // 17. COPY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1700)
    @DisplayName("copyFrom a ReadWrite buffer preserves data correctly")
    void readOnly_copyFromReadWrite() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 8);
        int[] payload = {10, 20, 30, 40, 50, 60, 70, 80};
        src.writeSync(payload);

        GlobalReadOnlyStaticBuffer dst = createBuffer(8);
        dst.copyFrom(src).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(8));
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(1701)
    @DisplayName("copyTo a ReadWrite buffer preserves data correctly")
    void readOnly_copyToReadWrite() {
        GlobalReadOnlyStaticBuffer src = createBuffer(4);
        int[] expected = {-1, -2, -3, -4};
        plant(src, expected);

        GlobalReadWriteStaticBuffer dst = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        src.copyTo(dst).waitForComplete();

        assertArrayEquals(expected, (int[]) dst.readSync(4));
        dst.destroy();
        src.destroy();
    }

    @Test
    @Order(1702)
    @DisplayName("copyFrom beyond capacity throws for static ReadOnly buffer")
    void readOnly_copyFromOversizedThrows() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 16);
        GlobalReadOnlyStaticBuffer dst = createBuffer(4);
        assertThrows(Exception.class,
                () -> dst.copyFrom(src, 0, 0, 8));
        src.destroy();
        dst.destroy();
    }
}
