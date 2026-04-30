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

import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.*;
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.junit.jupiter.api.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalWriteOnlyDynamicBufferTest extends AbstractGlobalDynamicalBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalWriteOnlyDynamicBuffer createBuffer(int capacity) {
        return new GlobalWriteOnlyDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalWriteOnlyDynamicBuffer createBuffer(
            String name, int capacity, boolean stagingBuffer) {
        return new GlobalWriteOnlyDynamicBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity, stagingBuffer);
    }

    @Override
    protected boolean supportsHostRead()  { return false; }
    @Override
    protected boolean supportsHostWrite() { return true;  }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Copies {@code src} into a temporary ReadWrite buffer, reads the data,
     * destroys the temporary buffer, and returns the result.
     */
    private int[] harvest(GlobalWriteOnlyDynamicBuffer src, int len) {
        GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, len);
        src.copyTo(tmp, 0, 0, len).waitForComplete();
        int[] result = (int[]) tmp.readSync(len);
        tmp.destroy();
        return result;
    }

    private int[] harvest(GlobalWriteOnlyDynamicBuffer src) {
        return harvest(src, src.getCapacity());
    }

    private GlobalWriteOnlyDynamicBuffer woDyn(int capacity) {
        return createBuffer(capacity);
    }

    private GlobalWriteOnlyDynamicBuffer woDynStaged(int capacity) {
        return createBuffer("staged-wod-" + capacity, capacity, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. HOST READ IS FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1300)
    @DisplayName("ReadableGlobal interface is absent on a write-only dynamic buffer")
    void writeOnly_readableNotAvailable() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        assertFalse(buf instanceof Readable,
                "GlobalWriteOnlyDynamicBuffer must not implement ReadableGlobal");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. WRITE SYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1400)
    @DisplayName("writeSync with full-capacity array succeeds")
    void writeSync_fullCapacity() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        assertDoesNotThrow(() -> buf.writeSync(data));
        buf.destroy();
    }

    @Test @Order(1401)
    @DisplayName("writeSync(offset, array) respects the given offset")
    void writeSync_withOffset() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(cap / 2, new int[]{99, 88}));
        buf.destroy();
    }

    @Test @Order(1402)
    @DisplayName("writeSync with events=null overload completes without error")
    void writeSync_eventsNull() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(null, new int[cap]));
        buf.destroy();
    }

    @Test @Order(1403)
    @DisplayName("Multiple sequential writeSync calls succeed")
    void writeSync_multipleCalls() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        assertDoesNotThrow(() -> {
            buf.writeSync(data);
            buf.writeSync(data);
        });
        buf.destroy();
    }

    @Test @Order(1404)
    @DisplayName("writeSync via staging buffer succeeds")
    void writeSync_stagingPath() {
        GlobalWriteOnlyDynamicBuffer buf = woDynStaged(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(new int[cap]));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. WRITE ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1500)
    @DisplayName("writeAsync returns a non-null ClEvent that completes")
    void writeAsync_returnsAndCompletes() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        ClEvent ev = buf.writeAsync(new int[cap]);
        assertNotNull(ev);
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test @Order(1501)
    @DisplayName("writeAsync with offset within bounds completes without error")
    void writeAsync_withOffsetSucceeds() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        ClEvent ev = buf.writeAsync(cap / 2, new int[]{7, 8, 9});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test @Order(1502)
    @DisplayName("writeAsync chained: second write depends on first via ClEventList")
    void writeAsync_chainedWrites() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        ClEvent first = buf.writeAsync(new int[cap]);
        ClEvent second = buf.writeAsync(new ClEventList(first), new int[cap]);
        assertDoesNotThrow(second::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. WRITE NEXT – pointer-advancing and auto-grow
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1600)
    @DisplayName("writeNextSync advances pointer by the array length")
    void writeNext_syncAdvancesPointer() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        buf.writeNextSync(new int[]{1, 2, 3});
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1601)
    @DisplayName("Two consecutive writeNextSync calls advance pointer additively")
    void writeNext_syncTwoCallsAccumulate() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        buf.writeNextSync(new int[]{1, 2});
        buf.writeNextSync(new int[]{3, 4});
        assertEquals(4, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1602)
    @DisplayName("writeNextSync beyond initial capacity triggers auto-grow")
    void writeNext_syncAutoGrows() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] bigChunk = new int[cap + 10];
        assertDoesNotThrow(() -> buf.writeNextSync(bigChunk));
        assertTrue(buf.getCapacity() >= bigChunk.length,
                "Capacity must grow to accommodate writeNextSync beyond original capacity");
        buf.destroy();
    }

    @Test @Order(1603)
    @DisplayName("writeNextAsync advances pointer and returns a valid ClEvent")
    void writeNext_asyncAdvancesPointer() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        ClEvent ev = buf.writeNextAsync(new int[]{10, 20});
        assertNotNull(ev);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    @Test @Order(1604)
    @DisplayName("writeNextSyncByte advances pointer correctly")
    void writeNext_syncByteAdvancesPointer() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        buf.writeNextSyncByte(new byte[2 * Integer.BYTES]);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. WRITE SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1700)
    @DisplayName("writeSyncByte writes raw bytes without error")
    void writeSyncByte_basic() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSyncByte(new byte[cap * Integer.BYTES]));
        buf.destroy();
    }

    @Test @Order(1701)
    @DisplayName("writeSyncByte with offset writes correctly within bounds")
    void writeSyncByte_withOffset() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSyncByte(cap / 2, new byte[4 * Integer.BYTES]));
        buf.destroy();
    }

    @Test @Order(1702)
    @DisplayName("writeSyncByte with null array throws")
    void writeSyncByte_nullThrows() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        assertThrows(Exception.class, () -> buf.writeSyncByte(null));
        buf.destroy();
    }

    @Test @Order(1703)
    @DisplayName("writeNextSyncByte advances pointer correctly")
    void writeSyncByte_nextAdvancesPointer() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        buf.writeNextSyncByte(new byte[2 * Integer.BYTES]);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1704)
    @DisplayName("writeAsyncByte returns a non-null ClEvent and completes")
    void writeAsyncByte_returnsAndCompletes() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        ClEvent ev = buf.writeAsyncByte(new byte[cap * Integer.BYTES]);
        assertNotNull(ev);
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test @Order(1705)
    @DisplayName("writeNextAsyncByte advances pointer and event completes")
    void writeAsyncByte_nextAdvancesPointer() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        ClEvent ev = buf.writeNextAsyncByte(new byte[3 * Integer.BYTES]);
        assertNotNull(ev);
        assertEquals(3, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 18. DATA PRESERVATION during resize
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1800)
    @DisplayName("all data is preserved after a grow resize")
    void preserve_fullDataAfterGrow() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        buf.resize(cap + 100);

        assertArrayEquals(data, harvest(buf, cap),
                "All original data must survive a grow resize");
        buf.destroy();
    }

    @Test @Order(1801)
    @DisplayName("prefix data is preserved after a shrink resize")
    void preserve_prefixAfterShrink() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive a shrink resize");
        buf.destroy();
    }

    @Test @Order(1802)
    @DisplayName("data is preserved after grow via increase()")
    void preserve_dataAfterIncrease() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i * 2;
        buf.writeSync(data);

        buf.increase(cap + 50);

        assertArrayEquals(data, harvest(buf, cap),
                "Data must survive increase()");
        buf.destroy();
    }

    @Test @Order(1803)
    @DisplayName("prefix data is preserved after shrink via decrease()")
    void preserve_prefixAfterDecrease() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.decrease(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive decrease()");
        buf.destroy();
    }

    @Test @Order(1804)
    @DisplayName("data is preserved after auto-grow triggered by writeNextSync")
    void preserve_dataAfterAutoGrow() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int[] initial = new int[cap];
        for (int i = 0; i < cap; i++) initial[i] = i + 1;
        buf.writeSync(initial);

        // Push pointer to end then write one more element → auto-grow
        buf.setPointer(cap);
        buf.writeNextSync(new int[]{999});

        assertArrayEquals(initial, harvest(buf, cap),
                "Original data must survive auto-grow via writeNextSync");
        buf.destroy();
    }

    @Test @Order(1805)
    @DisplayName("staging buffer: data is preserved after grow resize")
    void preserve_stagingDataAfterGrow() {
        GlobalWriteOnlyDynamicBuffer buf = woDynStaged(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        buf.resize(cap + 50);

        assertArrayEquals(data, harvest(buf, cap),
                "Data must survive grow resize with staging buffer");
        buf.destroy();
    }

    @Test @Order(1806)
    @DisplayName("staging buffer: prefix data is preserved after shrink resize")
    void preserve_stagingDataAfterShrink() {
        GlobalWriteOnlyDynamicBuffer buf = woDynStaged(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive shrink with staging buffer");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 19. ROUND-TRIP: write → GPU kernel → harvest
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1900)
    @DisplayName("Round-trip: writeSync data, kernel doubles each element, harvest")
    void roundTrip_kernelDoubles() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        buf.writeSync(input);

        clKernel = buildKernel("double_wod", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf, n);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1901)
    @DisplayName("Round-trip: kernel bound before grow resize executes correctly after resize")
    void roundTrip_kernelBoundBeforeResizeExecutesAfter() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);

        clKernel = buildKernel("add10_wod", "", "buf[get_global_id(0)] += 10;");
        buf.bindToKernel(clKernel, 0);

        buf.resize(buf.getCapacity() + 30);
        int newCap = buf.getCapacity();
        int[] data = new int[newCap];
        for (int i = 0; i < newCap; i++) data[i] = i;
        buf.writeSync(data);
        enqueueKernel(clKernel, newCap);

        int[] result = harvest(buf, newCap);
        for (int i = 0; i < newCap; i++) {
            assertEquals(i + 10, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1902)
    @DisplayName("Round-trip: write-only buffer used as output arg in two-buffer kernel")
    void roundTrip_twoBufferKernel() {
        int n = 10;
        GlobalReadWriteDynamicBuffer inputBuf = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, n);
        int cap = inputBuf.getCapacity();
        int[] input = new int[cap];
        for (int i = 0; i < cap; i++) input[i] = i;
        inputBuf.writeSync(input);

        GlobalWriteOnlyDynamicBuffer outputBuf = woDyn(n);

        String src =
                "__kernel void copy_add_wod(__global int* in, __global int* out) {" +
                        "    int gid = get_global_id(0);" +
                        "    out[gid] = in[gid] + 300;" +
                        "}";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), src, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            assertEquals(CL_SUCCESS, clBuildProgram(program, device.getDeviceID(), "", null, 0));
            clKernel = clCreateKernel(program, "copy_add_wod", errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            clProgram = program;
        }

        inputBuf.bindToKernel(clKernel, 0);
        outputBuf.bindToKernel(clKernel, 1);
        enqueueKernel(clKernel, cap);

        int[] result = harvest(outputBuf, cap);
        for (int i = 0; i < cap; i++) {
            assertEquals(i + 300, result[i], "Mismatch at index " + i);
        }

        inputBuf.destroy();
        outputBuf.destroy();
    }

    @Test @Order(1903)
    @DisplayName("Round-trip with staging buffer: writeSync via staging, kernel, harvest")
    void roundTrip_stagingBuffer() {
        GlobalWriteOnlyDynamicBuffer buf = woDynStaged(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = n - i;
        buf.writeSync(input);

        clKernel = buildKernel("negate_staged_wod",
                "", "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf, n);
        for (int i = 0; i < n; i++) {
            assertEquals(-(n - i), result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 20. COPY
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2000)
    @DisplayName("copyFrom a ReadWrite buffer fills the write-only buffer without error")
    void copy_fromReadWrite() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 5;
        src.writeSync(payload);

        GlobalWriteOnlyDynamicBuffer dst = woDyn(10);
        assertDoesNotThrow(() -> dst.copyFrom(src, 0, 0, cap).waitForComplete());

        assertArrayEquals(payload, harvest(dst, cap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2001)
    @DisplayName("copyFrom auto-grows destination when source is larger")
    void copy_autoGrowsDstWhenSmallerThanSrc() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 100); // cap ≈ 150
        int srcCap = src.getCapacity();
        int[] payload = new int[srcCap];
        for (int i = 0; i < srcCap; i++) payload[i] = i;
        src.writeSync(payload);

        GlobalWriteOnlyDynamicBuffer dst = woDyn(10); // cap = 15
        dst.copyFrom(src).waitForComplete();

        assertTrue(dst.getCapacity() >= srcCap,
                "dst must auto-grow to accommodate the full source buffer");
        assertArrayEquals(payload, harvest(dst, srcCap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2002)
    @DisplayName("copyTo a ReadWrite buffer preserves data correctly")
    void copy_toReadWrite() {
        GlobalWriteOnlyDynamicBuffer src = woDyn(10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = -i;
        src.writeSync(payload);

        GlobalReadWriteDynamicBuffer dst = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, cap);
        src.copyTo(dst, 0, 0, cap).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(cap));
        dst.destroy();
        src.destroy();
    }

    @Test @Order(2003)
    @DisplayName("Partial copyFrom places data at the correct destination offset")
    void copy_partialOffsets() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        int cap = src.getCapacity();
        int[] srcData = new int[cap];
        for (int i = 0; i < cap; i++) srcData[i] = i + 1;
        src.writeSync(srcData);

        GlobalWriteOnlyDynamicBuffer dst = woDyn(10);
        dst.writeSync(new int[cap]);                       // initialise to zeros
        dst.copyFrom(src, 0, 2, 4).waitForComplete();     // src[0..3] → dst[2..5]

        int[] result = harvest(dst, cap);
        assertEquals(1, result[2]);
        assertEquals(2, result[3]);
        assertEquals(3, result[4]);
        assertEquals(4, result[5]);
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 21. THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2100)
    @DisplayName("Concurrent writeSync calls do not throw and leave buffer usable")
    void thread_concurrentWriteSync() throws InterruptedException {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        int n = 8;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] data = new int[cap];
            Arrays.fill(data, i);
            futures.add(exec.submit(() -> buf.writeSync(data)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
        assertDoesNotThrow(() -> buf.writeSync(new int[cap]));
        buf.destroy();
    }

    @Test @Order(2101)
    @DisplayName("Concurrent resize calls do not corrupt the buffer (usable after)")
    void thread_concurrentResizes() throws InterruptedException {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int base = buf.getCapacity();
        int n = 6;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int target = base + (i + 1) * 10;
            futures.add(exec.submit(() -> buf.resize(target)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        assertFalse(buf.isClosed());
        assertTrue(buf.getCapacity() >= base);
        buf.destroy();
    }

    @Test @Order(2102)
    @DisplayName("Interleaved async writes with event chaining complete without deadlock")
    void thread_asyncWriteChain() {
        GlobalWriteOnlyDynamicBuffer buf = woDyn(10);
        int cap = buf.getCapacity();
        ClEvent prev = buf.writeAsync(new int[cap]);
        for (int i = 1; i <= 5; i++) {
            int[] data = new int[cap];
            Arrays.fill(data, i);
            prev = buf.writeAsync(new ClEventList(prev), data);
        }
        final ClEvent last = prev;
        assertDoesNotThrow(last::waitForComplete);
        buf.destroy();
    }
}
