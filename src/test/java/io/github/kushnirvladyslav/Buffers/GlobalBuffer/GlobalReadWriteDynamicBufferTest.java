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

import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteDynamicBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteDynamicBufferBuilder;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBuffer;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.GlobalReadWriteStaticBufferBuilder;
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
class GlobalReadWriteDynamicBufferTest extends AbstractGlobalDynamicalBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalReadWriteDynamicBuffer createBuffer(int capacity) {
        return new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalReadWriteDynamicBuffer createBuffer(
            String name, int capacity, boolean stagingBuffer) {
        return new GlobalReadWriteDynamicBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity, stagingBuffer);
    }

    @Override
    protected boolean supportsHostRead()  { return true; }
    @Override
    protected boolean supportsHostWrite() { return true; }

    // ── Convenience helpers ───────────────────────────────────────────────────

    private GlobalReadWriteDynamicBuffer rwDyn(int capacity) {
        return createBuffer(capacity);
    }

    private GlobalReadWriteDynamicBuffer rwDynStaged(int capacity) {
        return createBuffer("staged-rwd-" + capacity, capacity, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. WRITE SYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1300)
    @DisplayName("writeSync with full-capacity array succeeds")
    void writeSync_fullCapacity() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        assertDoesNotThrow(() -> buf.writeSync(data));
        buf.destroy();
    }

    @Test @Order(1301)
    @DisplayName("writeSync(offset, array) respects the given offset")
    void writeSync_withOffset() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(cap / 2, new int[]{99, 88}));
        buf.destroy();
    }

    @Test @Order(1302)
    @DisplayName("writeSync with events=null overload completes without error")
    void writeSync_eventsNull() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(null, new int[cap]));
        buf.destroy();
    }

    @Test @Order(1303)
    @DisplayName("Multiple sequential writeSync calls succeed")
    void writeSync_multipleCalls() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        assertDoesNotThrow(() -> {
            buf.writeSync(data);
            buf.writeSync(data);
        });
        buf.destroy();
    }

    @Test @Order(1304)
    @DisplayName("writeSync via staging buffer succeeds")
    void writeSync_stagingPath() {
        GlobalReadWriteDynamicBuffer buf = rwDynStaged(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.writeSync(new int[cap]));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. WRITE ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1400)
    @DisplayName("writeAsync returns a non-null ClEvent that completes")
    void writeAsync_returnsAndCompletes() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        ClEvent ev = buf.writeAsync(new int[cap]);
        assertNotNull(ev);
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test @Order(1401)
    @DisplayName("writeAsync with offset within bounds completes without error")
    void writeAsync_withOffsetSucceeds() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        ClEvent ev = buf.writeAsync(cap / 2, new int[]{7, 8, 9});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test @Order(1402)
    @DisplayName("writeAsync chained: second write depends on first via ClEventList")
    void writeAsync_chainedWrites() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        ClEvent first = buf.writeAsync(new int[cap]);
        ClEvent second = buf.writeAsync(new ClEventList(first), new int[cap]);
        assertDoesNotThrow(second::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. WRITE NEXT – pointer-advancing and auto-grow
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1500)
    @DisplayName("writeNextSync advances pointer by the array length")
    void writeNext_syncAdvancesPointer() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        buf.writeNextSync(new int[]{1, 2, 3});
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1501)
    @DisplayName("Two consecutive writeNextSync calls advance pointer additively")
    void writeNext_syncTwoCallsAccumulate() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        buf.writeNextSync(new int[]{1, 2});
        buf.writeNextSync(new int[]{3, 4});
        assertEquals(4, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1502)
    @DisplayName("writeNextSync beyond initial capacity triggers auto-grow")
    void writeNext_syncAutoGrows() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        // Writing cap+10 elements will push the pointer past capacity → auto-grow
        int[] bigChunk = new int[cap + 10];
        assertDoesNotThrow(() -> buf.writeNextSync(bigChunk));
        assertTrue(buf.getCapacity() >= bigChunk.length,
                "Capacity must grow to accommodate writeNextSync beyond original capacity");
        buf.destroy();
    }

    @Test @Order(1503)
    @DisplayName("writeNextAsync advances pointer and returns a valid ClEvent")
    void writeNext_asyncAdvancesPointer() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        ClEvent ev = buf.writeNextAsync(new int[]{10, 20});
        assertNotNull(ev);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    @Test @Order(1504)
    @DisplayName("writeNextSyncByte advances pointer correctly")
    void writeNext_syncByteAdvancesPointer() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        buf.writeNextSyncByte(new byte[2 * Integer.BYTES]);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. READ SYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1600)
    @DisplayName("readSync returns a non-null int[] of the requested length")
    void readSync_returnsCorrectLength() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        buf.writeSync(new int[cap]);
        Object result = buf.readSync(cap);
        assertNotNull(result);
        assertInstanceOf(int[].class, result);
        assertEquals(cap, ((int[]) result).length);
        buf.destroy();
    }

    @Test @Order(1601)
    @DisplayName("readSync(offset, len) reads the correct sub-range")
    void readSync_offsetSlice() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] written = new int[cap];
        for (int i = 0; i < cap; i++) written[i] = i;
        buf.writeSync(written);
        int[] read = (int[]) buf.readSync(2, 4);
        assertArrayEquals(new int[]{2, 3, 4, 5}, read);
        buf.destroy();
    }

    @Test @Order(1602)
    @DisplayName("readSync with targetArray overload fills the provided array")
    void readSync_targetArray() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] written = new int[cap];
        for (int i = 0; i < cap; i++) written[i] = i * 10;
        buf.writeSync(written);
        int[] dest = new int[cap];
        buf.readSync(dest);
        assertArrayEquals(written, dest);
        buf.destroy();
    }

    @Test @Order(1603)
    @DisplayName("readSync via staging buffer returns correct data")
    void readSync_stagingBuffer() {
        GlobalReadWriteDynamicBuffer buf = rwDynStaged(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i + 1;
        buf.writeSync(data);
        assertArrayEquals(data, (int[]) buf.readSync(cap));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. READ ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1700)
    @DisplayName("readAsync returns a non-null ClEvent and fills target array correctly")
    void readAsync_returnsAndFills() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] written = new int[cap];
        for (int i = 0; i < cap; i++) written[i] = i;
        buf.writeSync(written);
        int[] dest = new int[cap];
        ClEvent ev = buf.readAsync(dest);
        assertNotNull(ev);
        ev.waitForComplete();
        assertArrayEquals(written, dest);
        buf.destroy();
    }

    @Test @Order(1701)
    @DisplayName("readAsync chained with write event reads correct data")
    void readAsync_chainedWithWrite() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] written = new int[cap];
        for (int i = 0; i < cap; i++) written[i] = i + 100;
        ClEvent wEv = buf.writeAsync(written);
        int[] dest = new int[cap];
        buf.readAsync(new ClEventList(wEv), dest).waitForComplete();
        assertArrayEquals(written, dest);
        buf.destroy();
    }

    @Test @Order(1702)
    @DisplayName("readAsync with offset reads the correct sub-range")
    void readAsync_withOffset() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] written = new int[cap];
        for (int i = 0; i < cap; i++) written[i] = i;
        buf.writeSync(written);
        int[] dest = new int[3];
        buf.readAsync(2, dest).waitForComplete();
        assertArrayEquals(new int[]{2, 3, 4}, dest);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 18. DATA PRESERVATION during resize
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1800)
    @DisplayName("all data is preserved after a grow resize")
    void preserve_fullDataAfterGrow() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        buf.resize(cap + 100);

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "All original data must survive a grow resize");
        buf.destroy();
    }

    @Test @Order(1801)
    @DisplayName("prefix data is preserved after a shrink resize")
    void preserve_prefixAfterShrink() {
        // cap = 150; shrinkTarget = 37; new capacity = 37
        GlobalReadWriteDynamicBuffer buf = rwDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), (int[]) buf.readSync(newCap),
                "Prefix data must survive a shrink resize");
        buf.destroy();
    }

    @Test @Order(1802)
    @DisplayName("data is preserved after grow via increase()")
    void preserve_dataAfterIncrease() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i * 2;
        buf.writeSync(data);

        buf.increase(cap + 50);

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "Data must survive increase()");
        buf.destroy();
    }

    @Test @Order(1803)
    @DisplayName("prefix data is preserved after shrink via decrease()")
    void preserve_prefixAfterDecrease() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.decrease(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), (int[]) buf.readSync(newCap),
                "Prefix data must survive decrease()");
        buf.destroy();
    }

    @Test @Order(1804)
    @DisplayName("data is preserved after auto-grow triggered by setPointer")
    void preserve_dataAfterAutoGrow() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i + 1;
        buf.writeSync(data);

        buf.setPointer(cap + 5);          // triggers auto-grow

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "Data must survive auto-grow via setPointer");
        buf.destroy();
    }

    @Test @Order(1805)
    @DisplayName("staging buffer: data is preserved after grow resize")
    void preserve_stagingDataAfterGrow() {
        GlobalReadWriteDynamicBuffer buf = rwDynStaged(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        buf.resize(cap + 50);

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "Data must survive grow resize with staging buffer");
        buf.destroy();
    }

    @Test @Order(1806)
    @DisplayName("staging buffer: data is preserved after shrink resize")
    void preserve_stagingDataAfterShrink() {
        GlobalReadWriteDynamicBuffer buf = rwDynStaged(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        buf.writeSync(data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), (int[]) buf.readSync(newCap),
                "Prefix data must survive shrink resize with staging buffer");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 19. ROUND-TRIP: write → GPU kernel → read
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1900)
    @DisplayName("Round-trip: write, kernel doubles each element, read back")
    void roundTrip_kernelDoubles() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        buf.writeSync(input);

        clKernel = buildKernel("double_rwd", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = (int[]) buf.readSync(n);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1901)
    @DisplayName("Round-trip: kernel bound before grow resize executes correctly after resize")
    void roundTrip_kernelBoundBeforeResizeExecutesAfter() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);

        // Bind kernel before resize
        clKernel = buildKernel("add10_rwd", "", "buf[get_global_id(0)] += 10;");
        buf.bindToKernel(clKernel, 0);

        // Grow, write fresh data to full buffer, run kernel
        buf.resize(buf.getCapacity() + 30);
        int newCap = buf.getCapacity();
        int[] data = new int[newCap];
        for (int i = 0; i < newCap; i++) data[i] = i;
        buf.writeSync(data);
        enqueueKernel(clKernel, newCap);

        int[] result = (int[]) buf.readSync(newCap);
        for (int i = 0; i < newCap; i++) {
            assertEquals(i + 10, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1902)
    @DisplayName("Round-trip async: writeAsync → grow → kernel → readAsync")
    void roundTrip_asyncAfterGrow() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
        int initialCap = buf.getCapacity();
        int[] initialData = new int[initialCap];
        for (int i = 0; i < initialCap; i++) initialData[i] = i;
        buf.writeAsync(initialData).waitForComplete();

        // Grow and fill the whole new area
        buf.resize(initialCap + 30);
        int newCap = buf.getCapacity();
        int[] fullData = new int[newCap];
        for (int i = 0; i < newCap; i++) fullData[i] = i;
        buf.writeSync(fullData);

        clKernel = buildKernel("negate_rwd",
                "", "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, newCap);

        int[] dest = new int[newCap];
        buf.readAsync(dest).waitForComplete();
        for (int i = 0; i < newCap; i++) {
            assertEquals(-i, dest[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1903)
    @DisplayName("Round-trip with staging buffer: write, kernel, read")
    void roundTrip_stagingBuffer() {
        GlobalReadWriteDynamicBuffer buf = rwDynStaged(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = n - i;
        buf.writeSync(input);

        clKernel = buildKernel("negate_staged_rwd",
                "", "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = (int[]) buf.readSync(n);
        for (int i = 0; i < n; i++) {
            assertEquals(-(n - i), result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 20. COPY – dynamic-specific behaviour
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2000)
    @DisplayName("copyFrom between two dynamic buffers of equal size preserves data")
    void copy_dynToDynSameSize() {
        GlobalReadWriteDynamicBuffer src = rwDyn(10);
        GlobalReadWriteDynamicBuffer dst = rwDyn(10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 10;
        src.writeSync(payload);

        dst.copyFrom(src).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(cap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2001)
    @DisplayName("copyFrom: destination dynamic buffer auto-grows when smaller than source")
    void copy_autoGrowsDstWhenSmallerThanSrc() {
        // copyFrom(src) → copyFromBufferToBuffer(src, this=dst, ...) →
        // changeCapacity called on dst → auto-grows correctly.
        // copyTo(dst) would call changeCapacity on src (wrong buffer) — not tested here.
        GlobalReadWriteDynamicBuffer src = rwDyn(100); // actual cap ≈ 150
        GlobalReadWriteDynamicBuffer dst = rwDyn(10);  // actual cap = 15
        int srcCap = src.getCapacity();
        int[] payload = new int[srcCap];
        for (int i = 0; i < srcCap; i++) payload[i] = i;
        src.writeSync(payload);

        dst.copyFrom(src).waitForComplete();  // dst auto-grows to fit srcCap

        assertTrue(dst.getCapacity() >= srcCap,
                "dst must auto-grow to accommodate the full source buffer");
        assertArrayEquals(payload, (int[]) dst.readSync(srcCap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2002)
    @DisplayName("partial copyFrom places data at the correct destination offset")
    void copy_partialOffsets() {
        GlobalReadWriteDynamicBuffer src = rwDyn(10);
        GlobalReadWriteDynamicBuffer dst = rwDyn(10);
        int cap = src.getCapacity();
        int[] srcData = new int[cap];
        for (int i = 0; i < cap; i++) srcData[i] = i + 1;
        src.writeSync(srcData);
        dst.writeSync(new int[cap]);        // initialise dst to zeros

        dst.copyFrom(src, 0, 2, 4).waitForComplete(); // src[0..3] → dst[2..5]

        int[] result = (int[]) dst.readSync(cap);
        assertEquals(1, result[2]);
        assertEquals(2, result[3]);
        assertEquals(3, result[4]);
        assertEquals(4, result[5]);
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2003)
    @DisplayName("copyTo is symmetric with copyFrom – same data arrives at destination")
    void copy_copyToSymmetry() {
        GlobalReadWriteDynamicBuffer src = rwDyn(10);
        GlobalReadWriteDynamicBuffer dst = rwDyn(10);
        int cap = src.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = -i;
        src.writeSync(data);

        src.copyTo(dst).waitForComplete();

        assertArrayEquals(data, (int[]) dst.readSync(cap));
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 21. THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2100)
    @DisplayName("Concurrent resize calls do not corrupt the buffer (no crash, usable after)")
    void thread_concurrentResizes() throws InterruptedException {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
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
        // Buffer must still be usable after concurrent resizes
        assertFalse(buf.isClosed());
        assertTrue(buf.getCapacity() >= base);
        buf.destroy();
    }

    @Test @Order(2101)
    @DisplayName("Concurrent writeSync calls do not throw and leave buffer usable")
    void thread_concurrentWriteSync() throws InterruptedException {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
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
        assertDoesNotThrow(() -> buf.readSync(cap));
        buf.destroy();
    }

    @Test @Order(2102)
    @DisplayName("Interleaved async writes with event chaining complete without deadlock")
    void thread_asyncWriteChain() {
        GlobalReadWriteDynamicBuffer buf = rwDyn(10);
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
