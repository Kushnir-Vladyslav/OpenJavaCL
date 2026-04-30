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

import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBufferBuilder;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalWriteOnlyStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalWriteOnlyStaticBufferBuilder;
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalWriteOnlyStaticBufferTest extends AbstractGlobalStaticBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalWriteOnlyStaticBuffer createBuffer(int capacity) {
        return new GlobalWriteOnlyStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalWriteOnlyStaticBuffer createBuffer(
            String name, int capacity, boolean stagingBuffer) {
        return new GlobalWriteOnlyStaticBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity, stagingBuffer);
    }

    @Override
    protected boolean supportsHostRead()  { return false; }
    @Override
    protected boolean supportsHostWrite() { return true;  }

    // ── Helper: harvest data via a ReadWrite buffer ──────────────────────────

    /**
     * Copies {@code src} into a temporary ReadWrite buffer, reads the data out,
     * destroys the temporary buffer, and returns the result array.
     */
    private int[] harvest(GlobalWriteOnlyStaticBuffer src) {
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, src.getCapacity());
        ClEvent ev = tmp.copyFrom(src);
        ev.waitForComplete();
        int[] result = (int[]) tmp.readSync(src.getCapacity());
        tmp.destroy();
        return result;
    }

    /**
     * Copies a slice of {@code src} into a temporary ReadWrite buffer and returns the result.
     */
    private int[] harvestSlice(GlobalWriteOnlyStaticBuffer src, int offset, int len) {
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, len);
        ClEvent ev = tmp.copyFrom(src, offset, 0, len);
        ev.waitForComplete();
        int[] result = (int[]) tmp.readSync(len);
        tmp.destroy();
        return result;
    }

    // ── Override: source buffer must be readable by copy engine (GPU-side) ──

    @Override
    protected void assumeBufferCanBeUsedAsSource() {
        // WriteOnly buffers have CL_MEM_HOST_WRITE_ONLY which does NOT prevent
        // device-to-device copies – the restriction is only on host reads.
        // No assumption needed; leave as no-op.
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. HOST READ IS FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1100)
    @DisplayName("readSync is not available on a write-only buffer (no ReadableGlobal interface)")
    void writeOnly_readSyncNotAvailable() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        assertFalse(buf instanceof Readable,
                "GlobalWriteOnlyStaticBuffer must not implement ReadableGlobal");
        buf.destroy();
    }

    @Test
    @Order(1101)
    @DisplayName("readAsync is not available on a write-only buffer")
    void writeOnly_readAsyncNotAvailable() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        assertFalse(buf instanceof Readable);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. WRITE SYNC – basic correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1200)
    @DisplayName("writeSync(array) fills the buffer without error")
    void writeSync_singleArrayNoError() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        int[] data = {10, 20, 30, 40};
        assertDoesNotThrow(() -> buf.writeSync(data));
        buf.destroy();
    }

    @Test
    @Order(1201)
    @DisplayName("writeSync(offset, array) respects the given offset")
    void writeSync_withOffset() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        int[] patch = {99, 88};
        assertDoesNotThrow(() -> buf.writeSync(4, patch));
        buf.destroy();
    }

    @Test
    @Order(1202)
    @DisplayName("writeSync with a single-element array writes without error")
    void writeSync_singleElement() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(1);
        int[] single = {42};
        assertDoesNotThrow(() -> buf.writeSync(single));
        buf.destroy();
    }

    @Test
    @Order(1203)
    @DisplayName("writeSync with full-capacity array exactly at boundary succeeds")
    void writeSync_exactCapacityBoundary() {
        int cap = 6;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(cap);
        int[] arr = new int[cap];
        for (int i = 0; i < cap; i++) arr[i] = i;
        assertDoesNotThrow(() -> buf.writeSync(arr));
        buf.destroy();
    }

    @Test
    @Order(1204)
    @DisplayName("writeSync with events=null overload completes without error")
    void writeSync_withEventsNull() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        int[] data = {1, 2, 3, 4};
        assertDoesNotThrow(() -> buf.writeSync(null, data));
        buf.destroy();
    }

    @Test
    @Order(1205)
    @DisplayName("Multiple sequential writeSync calls succeed")
    void writeSync_multipleCallsSucceed() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        assertDoesNotThrow(() -> {
            buf.writeSync(new int[]{1, 2, 3, 4});
            buf.writeSync(new int[]{5, 6, 7, 8});
        });
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. WRITE SYNC – staging buffer path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1300)
    @DisplayName("writeSync via staging buffer succeeds")
    void writeSync_stagingBufferPath() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer("staged", 8, true);
        assertDoesNotThrow(() -> buf.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8}));
        buf.destroy();
    }

    @Test
    @Order(1301)
    @DisplayName("Repeated writeSync via staging buffer does not corrupt state")
    void writeSync_stagingBufferRepeated() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer("staged-rep", 4, true);
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                buf.writeSync(new int[]{i, i + 1, i + 2, i + 3});
            }
        });
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. WRITE ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1400)
    @DisplayName("writeAsync returns a non-null ClEvent")
    void writeAsync_returnsEvent() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        ClEvent ev = buf.writeAsync(new int[]{1, 2, 3, 4});
        assertNotNull(ev);
        ev.waitForComplete();
        buf.destroy();
    }

    @Test
    @Order(1401)
    @DisplayName("writeAsync completes successfully when waited on")
    void writeAsync_completesOnWait() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        ClEvent ev = buf.writeAsync(new int[]{10, 20, 30, 40});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1402)
    @DisplayName("writeAsync with offset within bounds completes without error")
    void writeAsync_withOffsetSucceeds() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        ClEvent ev = buf.writeAsync(4, new int[]{7, 8, 9, 10});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1403)
    @DisplayName("writeAsync with events=null succeeds (no dependency chain)")
    void writeAsync_nullEventList() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        ClEvent ev = buf.writeAsync((ClEventList) null, new int[]{1, 2, 3, 4});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1404)
    @DisplayName("writeAsync chained: second write depends on first via ClEventList")
    void writeAsync_chainedWrites() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        ClEvent first = buf.writeAsync(new int[]{1, 2, 3, 4});
        ClEvent second = buf.writeAsync(new ClEventList(first), new int[]{5, 6, 7, 8});
        assertDoesNotThrow(second::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. WRITE NEXT (pointer-advancing helpers)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1500)
    @DisplayName("writeNextSync advances pointer by the array length")
    void writeNext_syncAdvancesPointer() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        buf.writeNextSync(new int[]{1, 2, 3});
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(1501)
    @DisplayName("Two consecutive writeNextSync calls advance pointer additively")
    void writeNext_syncTwoCallsAccumulate() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        buf.writeNextSync(new int[]{1, 2});
        buf.writeNextSync(new int[]{3, 4});
        assertEquals(4, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(1502)
    @DisplayName("writeNextSync beyond capacity throws (static – no auto-grow)")
    void writeNext_syncBeyondCapacityThrows() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        buf.writeNextSync(new int[]{1, 2, 3, 4}); // pointer = 4
        assertThrows(Exception.class, () -> buf.writeNextSync(new int[]{5}));
        buf.destroy();
    }

    @Test
    @Order(1503)
    @DisplayName("writeNextAsync advances pointer and returns a valid ClEvent")
    void writeNext_asyncAdvancesPointer() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(6);
        ClEvent ev = buf.writeNextAsync(new int[]{10, 20});
        assertNotNull(ev);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. WRITE SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1600)
    @DisplayName("writeSyncByte writes raw bytes without error")
    void writeSyncByte_basic() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        byte[] bytes = new byte[4 * Integer.BYTES];
        assertDoesNotThrow(() -> buf.writeSyncByte(bytes));
        buf.destroy();
    }

    @Test
    @Order(1601)
    @DisplayName("writeSyncByte with offset writes correctly within bounds")
    void writeSyncByte_withOffset() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        byte[] bytes = new byte[4 * Integer.BYTES];
        assertDoesNotThrow(() -> buf.writeSyncByte(2, bytes));
        buf.destroy();
    }

    @Test
    @Order(1602)
    @DisplayName("writeSyncByte with null array throws NullPointerException or IllegalArgumentException")
    void writeSyncByte_nullArrayThrows() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        assertThrows(Exception.class, () -> buf.writeSyncByte(null));
        buf.destroy();
    }

    @Test
    @Order(1603)
    @DisplayName("writeSyncByte exceeding capacity throws (static – no grow)")
    void writeSyncByte_exceedCapacityThrows() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(2);
        byte[] tooBig = new byte[100 * Integer.BYTES];
        assertThrows(Exception.class, () -> buf.writeSyncByte(tooBig));
        buf.destroy();
    }

    @Test
    @Order(1604)
    @DisplayName("writeNextSyncByte advances pointer correctly")
    void writeSyncByte_nextAdvancesPointer() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        byte[] chunk = new byte[2 * Integer.BYTES];
        buf.writeNextSyncByte(chunk);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. WRITE ASYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1700)
    @DisplayName("writeAsyncByte returns a non-null ClEvent and completes")
    void writeAsyncByte_returnsAndCompletes() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(4);
        byte[] bytes = new byte[4 * Integer.BYTES];
        ClEvent ev = buf.writeAsyncByte(bytes);
        assertNotNull(ev);
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1701)
    @DisplayName("writeNextAsyncByte advances pointer and event completes")
    void writeAsyncByte_nextAdvancesPointer() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        byte[] chunk = new byte[3 * Integer.BYTES];
        ClEvent ev = buf.writeNextAsyncByte(chunk);
        assertNotNull(ev);
        assertEquals(3, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 18. ROUND-TRIP: write → GPU kernel → read via helper ReadWrite buffer
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1800)
    @DisplayName("Round-trip: writeSync data is readable via copyFrom to a ReadWrite buffer")
    void roundTrip_writeSyncDataPreserved() {
        int n = 8;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        int[] payload = {10, 20, 30, 40, 50, 60, 70, 80};
        buf.writeSync(payload);

        assertArrayEquals(payload, harvest(buf));
        buf.destroy();
    }

    @Test
    @Order(1801)
    @DisplayName("Round-trip: writeAsync data is readable after event completes")
    void roundTrip_writeAsyncDataPreserved() {
        int n = 4;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        int[] payload = {1, 2, 3, 4};
        ClEvent ev = buf.writeAsync(payload);
        ev.waitForComplete();

        assertArrayEquals(payload, harvest(buf));
        buf.destroy();
    }

    @Test
    @Order(1802)
    @DisplayName("Round-trip: write data, run GPU kernel to double elements, verify via harvest")
    void roundTrip_kernelDoubles() {
        int n = 32;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        buf.writeSync(input);

        clKernel = buildKernel("double_wo", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(1803)
    @DisplayName("Round-trip: writeAsync chained with kernel, verify via harvest")
    void roundTrip_asyncWriteKernelHarvest() {
        int n = 16;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i;

        ClEvent wEv = buf.writeAsync(input);
        new ClEventList(wEv).waitForComplete();

        clKernel = buildKernel("add_five_wo", "", "buf[get_global_id(0)] += 5;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf);
        for (int i = 0; i < n; i++) {
            assertEquals(i + 5, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(1804)
    @DisplayName("Round-trip with staging buffer: writeSync via staging, verify via harvest")
    void roundTrip_stagingBuffer() {
        int n = 8;
        GlobalWriteOnlyStaticBuffer buf = createBuffer("wo-staged", n, true);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = n - i;
        buf.writeSync(input);

        assertArrayEquals(input, harvest(buf));
        buf.destroy();
    }

    @Test
    @Order(1805)
    @DisplayName("Round-trip byte: writeSyncByte then harvest preserves raw data")
    void roundTrip_byteLevel() {
        int n = 4;
        // Use ReadWrite buffer to convert typed data to bytes
        GlobalReadWriteStaticBuffer helper = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, n);
        int[] typed = {0x0102_0304, 0xDEAD_BEEF, 0xCAFE_BABE, 0xFFFF_FFFF};
        helper.writeSync(typed);
        byte[] bytes = helper.readSyncByte(0, n);
        helper.destroy();

        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        buf.writeSyncByte(bytes);

        assertArrayEquals(typed, harvest(buf));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 19. WRITE NEXT + POINTER interaction
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1900)
    @DisplayName("setPointer + writeNextSync writes into correct positions verified via harvest")
    void pointerWrite_setThenWriteNext() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(8);
        buf.writeSync(new int[]{0, 0, 0, 0, 0, 0, 0, 0});
        buf.setPointer(4);
        buf.writeNextSync(new int[]{77, 88});
        assertEquals(6, buf.getPointer());

        int[] all = harvest(buf);
        assertEquals(77, all[4]);
        assertEquals(88, all[5]);
        buf.destroy();
    }

    @Test
    @Order(1901)
    @DisplayName("Two writeNextSync chunks land at expected offsets in the device buffer")
    void pointerWrite_twoChunksAtCorrectOffsets() {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(6);
        buf.writeNextSync(new int[]{11, 22});
        buf.writeNextSync(new int[]{33, 44});
        assertEquals(4, buf.getPointer());

        int[] slice1 = harvestSlice(buf, 0, 2);
        int[] slice2 = harvestSlice(buf, 2, 2);
        assertArrayEquals(new int[]{11, 22}, slice1);
        assertArrayEquals(new int[]{33, 44}, slice2);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 20. COPY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2000)
    @DisplayName("copyTo a ReadWrite buffer preserves written data correctly")
    void copy_copyToReadWrite() {
        GlobalWriteOnlyStaticBuffer src = createBuffer(4);
        int[] payload = {-1, -2, -3, -4};
        src.writeSync(payload);

        GlobalReadWriteStaticBuffer dst = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        src.copyTo(dst).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(4));
        dst.destroy();
        src.destroy();
    }

    @Test
    @Order(2001)
    @DisplayName("copyFrom a ReadWrite buffer fills write-only buffer without error")
    void copy_copyFromReadWrite() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 8);
        int[] payload = {10, 20, 30, 40, 50, 60, 70, 80};
        src.writeSync(payload);

        GlobalWriteOnlyStaticBuffer dst = createBuffer(8);
        ClEvent ev = dst.copyFrom(src);
        ev.waitForComplete();

        assertArrayEquals(payload, harvest(dst));
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(2002)
    @DisplayName("Partial copyFrom places data at the correct destination offset")
    void copy_partialCopyOffsets() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 8);
        src.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        GlobalWriteOnlyStaticBuffer dst = createBuffer(8);
        dst.writeSync(new int[]{0, 0, 0, 0, 0, 0, 0, 0});

        dst.copyFrom(src, 2, 4, 4).waitForComplete(); // src[2..5] → dst[4..7]

        int[] result = harvest(dst);
        assertArrayEquals(new int[]{0, 0, 0, 0, 3, 4, 5, 6}, result);
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 21. THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2100)
    @DisplayName("Concurrent writeSync calls do not throw and leave buffer usable")
    void thread_concurrentWriteSync() throws InterruptedException {
        GlobalWriteOnlyStaticBuffer buf = createBuffer(16);
        int n = 8;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] data = new int[16];
            Arrays.fill(data, i);
            futures.add(exec.submit(() -> buf.writeSync(data)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent writeSync");
        }
        // Buffer must still accept operations
        assertDoesNotThrow(() -> buf.writeSync(new int[16]));
        buf.destroy();
    }

    @Test
    @Order(2101)
    @DisplayName("Interleaved async writes with event chaining complete without deadlock")
    void thread_asyncWriteChain() {
        int n = 8;
        GlobalWriteOnlyStaticBuffer buf = createBuffer(n);
        ClEvent prev = buf.writeAsync(new int[n]);
        for (int i = 1; i <= 5; i++) {
            int[] data = new int[n];
            Arrays.fill(data, i);
            prev = buf.writeAsync(new ClEventList(prev), data);
        }
        final ClEvent last = prev;
        assertDoesNotThrow(last::waitForComplete);
        buf.destroy();
    }
}