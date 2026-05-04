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
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;



@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalReadWriteStaticBufferTest extends AbstractGlobalStaticBufferTest {

    // ── Factory methods ───────────────────────────────────────────────────────

    @Override
    protected GlobalReadWriteStaticBuffer createBuffer(int capacity) {
        return new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalReadWriteStaticBuffer createBuffer(
            String name, int capacity) {
        return new GlobalReadWriteStaticBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity);
    }

    @Override
    protected boolean supportsHostRead()  { return true; }
    @Override
    protected boolean supportsHostWrite() { return true; }

    // ── Convenience casts ─────────────────────────────────────────────────────

    private GlobalReadWriteStaticBuffer rwBuf(int cap) {
        return createBuffer(cap);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. WRITE SYNC – basic correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1100)
    @DisplayName("writeSync(array) fills the buffer without error")
    void writeSync_singleArrayNoError() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] data = {10, 20, 30, 40};
        assertDoesNotThrow(() -> buf.writeSync(data));
        buf.destroy();
    }

    @Test
    @Order(1101)
    @DisplayName("writeSync(offset, array) respects the given offset")
    void writeSync_withOffset() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        int[] patch = {99, 88};
        assertDoesNotThrow(() -> buf.writeSync(4, patch)); // offset 4, within 8
        buf.destroy();
    }

    @Test
    @Order(1102)
    @DisplayName("writeSync with a single-element array writes without error")
    void writeSync_singleElement() {
        GlobalReadWriteStaticBuffer buf = rwBuf(1);
        int[] single = {42};
        assertDoesNotThrow(() -> buf.writeSync(single));
        buf.destroy();
    }

    @Test
    @Order(1103)
    @DisplayName("writeSync with full-capacity array exactly at boundary succeeds")
    void writeSync_exactCapacityBoundary() {
        int cap = 6;
        GlobalReadWriteStaticBuffer buf = rwBuf(cap);
        int[] arr = new int[cap];
        for (int i = 0; i < cap; i++) arr[i] = i;
        assertDoesNotThrow(() -> buf.writeSync(arr));
        buf.destroy();
    }

    @Test
    @Order(1104)
    @DisplayName("writeSync with events parameter overload completes without error")
    void writeSync_withEventsNull() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] data = {1, 2, 3, 4};
        assertDoesNotThrow(() -> buf.writeSync(null, data));
        buf.destroy();
    }

    @Test
    @Order(1105)
    @DisplayName("Multiple sequential writeSync calls succeed")
    void writeSync_multipleCallsSucceed() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] a = {1, 2, 3, 4};
        int[] b = {5, 6, 7, 8};
        assertDoesNotThrow(() -> {
            buf.writeSync(a);
            buf.writeSync(b);
        });
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. WRITE ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1300)
    @DisplayName("writeAsync returns a non-null ClEvent")
    void writeAsync_returnsEvent() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] data = {1, 2, 3, 4};
        ClEvent ev = buf.writeAsync(data);
        assertNotNull(ev);
        ev.waitForComplete();
        buf.destroy();
    }

    @Test
    @Order(1301)
    @DisplayName("writeAsync completes successfully when waited on")
    void writeAsync_completesOnWait() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        ClEvent ev = buf.writeAsync(new int[]{10, 20, 30, 40});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1302)
    @DisplayName("writeAsync with offset within bounds completes without error")
    void writeAsync_withOffsetSucceeds() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        ClEvent ev = buf.writeAsync(4, new int[]{7, 8, 9, 10});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1303)
    @DisplayName("writeAsync with events=null succeeds (no dependency chain)")
    void writeAsync_nullEventList() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        ClEvent ev = buf.writeAsync((ClEventList) null, new int[]{1, 2, 3, 4});
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1304)
    @DisplayName("writeAsync chained: second write depends on first via ClEventList")
    void writeAsync_chainedWrites() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        ClEvent first = buf.writeAsync(new int[]{1, 2, 3, 4});
        ClEventList list = new ClEventList();
        list.addEvent(first);
        ClEvent second = buf.writeAsync(list, new int[]{5, 6, 7, 8});
        assertDoesNotThrow(second::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. WRITE NEXT (pointer-advancing helpers)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1400)
    @DisplayName("writeNextSync advances pointer by the array length")
    void writeNext_syncAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        int[] chunk = {1, 2, 3};
        buf.writeNextSync(chunk);
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(1401)
    @DisplayName("Two consecutive writeNextSync calls advance pointer additively")
    void writeNext_syncTwoCallsAccumulate() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        buf.writeNextSync(new int[]{1, 2});
        buf.writeNextSync(new int[]{3, 4});
        assertEquals(4, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(1402)
    @DisplayName("writeNextSync beyond capacity throws (static – no auto-grow)")
    void writeNext_syncBeyondCapacityThrows() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] fits   = {1, 2, 3, 4};
        int[] tooLarge = {5};       // pointer would become 5 > capacity 4
        buf.writeNextSync(fits);    // consumes all 4 elements; pointer = 4
        assertThrows(Exception.class, () -> buf.writeNextSync(tooLarge));
        buf.destroy();
    }

    @Test
    @Order(1403)
    @DisplayName("writeNextAsync advances pointer and returns a valid ClEvent")
    void writeNext_asyncAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(6);
        ClEvent ev = buf.writeNextAsync(new int[]{10, 20});
        assertNotNull(ev);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. WRITE SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1500)
    @DisplayName("writeSyncByte writes raw bytes without error")
    void writeSyncByte_basic() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        byte[] bytes = new byte[4 * Integer.BYTES]; // 16 bytes = 4 ints
        assertDoesNotThrow(() -> buf.writeSyncByte(bytes));
        buf.destroy();
    }

    @Test
    @Order(1501)
    @DisplayName("writeSyncByte with offset writes correctly within bounds")
    void writeSyncByte_withOffset() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        byte[] bytes = new byte[4 * Integer.BYTES]; // fills 4 ints starting at offset 2
        assertDoesNotThrow(() -> buf.writeSyncByte(2, bytes));
        buf.destroy();
    }

    @Test
    @Order(1502)
    @DisplayName("writeSyncByte with null array throws NullPointerException or IllegalArgumentException")
    void writeSyncByte_nullArrayThrows() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        assertThrows(Exception.class, () -> buf.writeSyncByte(null));
        buf.destroy();
    }

    @Test
    @Order(1503)
    @DisplayName("writeSyncByte exceeding capacity throws (static – no grow)")
    void writeSyncByte_exceedCapacityThrows() {
        GlobalReadWriteStaticBuffer buf = rwBuf(2);
        byte[] tooBig = new byte[100 * Integer.BYTES];
        assertThrows(Exception.class, () -> buf.writeSyncByte(tooBig));
        buf.destroy();
    }

    @Test
    @Order(1504)
    @DisplayName("writeNextSyncByte advances pointer correctly")
    void writeSyncByte_nextAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        byte[] chunk = new byte[2 * Integer.BYTES]; // 2 int-elements
        buf.writeNextSyncByte(chunk);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. WRITE ASYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1600)
    @DisplayName("writeAsyncByte returns a non-null ClEvent and completes")
    void writeAsyncByte_returnsAndCompletes() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        byte[] bytes = new byte[4 * Integer.BYTES];
        ClEvent ev = buf.writeAsyncByte(bytes);
        assertNotNull(ev);
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    @Test
    @Order(1601)
    @DisplayName("writeNextAsyncByte advances pointer and event completes")
    void writeAsyncByte_nextAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        byte[] chunk = new byte[3 * Integer.BYTES];
        ClEvent ev = buf.writeNextAsyncByte(chunk);
        assertNotNull(ev);
        assertEquals(3, buf.getPointer());
        ev.waitForComplete();
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. READ SYNC – basic correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1700)
    @DisplayName("readSync(len) returns a non-null array of the requested length")
    void readSync_returnsCorrectLength() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        buf.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        Object result = buf.readSync(8);
        assertNotNull(result);
        assertTrue(result instanceof int[]);
        assertEquals(8, ((int[]) result).length);
        buf.destroy();
    }

    @Test
    @Order(1701)
    @DisplayName("readSync(offset, len) reads the correct slice")
    void readSync_offsetSlice() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        int[] written = {0, 1, 2, 3, 4, 5, 6, 7};
        buf.writeSync(written);
        int[] read = (int[]) buf.readSync(2, 4);  // elements [2..5]
        assertArrayEquals(new int[]{2, 3, 4, 5}, read);
        buf.destroy();
    }

    @Test
    @Order(1702)
    @DisplayName("readSync with exact capacity length succeeds")
    void readSync_fullCapacity() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{10, 20, 30, 40});
        int[] read = (int[]) buf.readSync(4);
        assertArrayEquals(new int[]{10, 20, 30, 40}, read);
        buf.destroy();
    }

    @Test
    @Order(1703)
    @DisplayName("readSync(1) reads the single first element")
    void readSync_singleElement() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{99, 0, 0, 0});
        int[] result = (int[]) buf.readSync(1);
        assertEquals(99, result[0]);
        buf.destroy();
    }

    @Test
    @Order(1704)
    @DisplayName("readSync with targetArray overload fills the provided array")
    void readSync_targetArrayOverload() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] src = {5, 6, 7, 8};
        buf.writeSync(src);
        int[] dest = new int[4];
        buf.readSync(dest);
        assertArrayEquals(src, dest);
        buf.destroy();
    }

    @Test
    @Order(1705)
    @DisplayName("readSync with events=null parameter overload completes without error")
    void readSync_eventsNullOverload() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        int[] dest = new int[4];
        assertDoesNotThrow(() -> buf.readSync((ClEventList) null, dest));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 19. READ ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1900)
    @DisplayName("readAsync returns a non-null ClEvent")
    void readAsync_returnsEvent() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        int[] dest = new int[4];
        ClEvent ev = buf.readAsync(dest);
        assertNotNull(ev);
        ev.waitForComplete();
        buf.destroy();
    }

    @Test
    @Order(1901)
    @DisplayName("readAsync fills the target array with correct values on completion")
    void readAsync_correctDataOnCompletion() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] written = {100, 200, 300, 400};
        buf.writeSync(written);
        int[] dest = new int[4];
        ClEvent ev = buf.readAsync(dest);
        ev.waitForComplete();
        assertArrayEquals(written, dest);
        buf.destroy();
    }

    @Test
    @Order(1902)
    @DisplayName("readAsync with offset reads the correct sub-range")
    void readAsync_withOffsetCorrectData() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(6);
        buf.writeSync(new int[]{0, 1, 2, 3, 4, 5});
        int[] dest = new int[3];
        ClEvent ev = buf.readAsync(2, dest); // read elements [2..4]
        ev.waitForComplete();
        assertArrayEquals(new int[]{2, 3, 4}, dest);
        buf.destroy();
    }

    @Test
    @Order(1903)
    @DisplayName("readAsync with null target throws NullPointerException")
    void readAsync_nullTargetThrows() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        assertThrows(NullPointerException.class, () -> buf.readAsync(null));
        buf.destroy();
    }

    @Test
    @Order(1904)
    @DisplayName("readAsync chained: read waits for a prior write via ClEventList")
    void readAsync_chainedWithWriteEvent() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] written = {7, 8, 9, 10};
        ClEvent writeEv = buf.writeAsync(written);
        ClEventList deps = new ClEventList();
        deps.addEvent(writeEv);
        int[] dest = new int[4];
        ClEvent readEv = buf.readAsync(deps, dest);
        readEv.waitForComplete();
        assertArrayEquals(written, dest);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 20. READ NEXT (pointer-advancing helpers)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2000)
    @DisplayName("readNextSync(len) advances pointer by len")
    void readNext_syncAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        buf.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        buf.readNextSync(3);
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(2001)
    @DisplayName("readNextSync reads chunks in order")
    void readNext_syncSequentialChunks() {
        GlobalReadWriteStaticBuffer buf = rwBuf(6);
        buf.writeSync(new int[]{10, 20, 30, 40, 50, 60});
        int[] a = (int[]) buf.readNextSync(2);
        int[] b = (int[]) buf.readNextSync(2);
        int[] c = (int[]) buf.readNextSync(2);
        assertArrayEquals(new int[]{10, 20}, a);
        assertArrayEquals(new int[]{30, 40}, b);
        assertArrayEquals(new int[]{50, 60}, c);
        buf.destroy();
    }

    @Test
    @Order(2002)
    @DisplayName("readNextSync beyond capacity throws (static – no auto-grow)")
    void readNext_syncBeyondCapacityThrows() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        buf.readNextSync(4);             // advance to end
        assertThrows(Exception.class, () -> buf.readNextSync(1)); // one past end
        buf.destroy();
    }

    @Test
    @Order(2003)
    @DisplayName("readNextAsync advances pointer and completes with correct data")
    void readNext_asyncAdvancesAndReturns() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        int[] written = {3, 6, 9, 12};
        buf.writeSync(written);
        int[] dest = new int[2];
        ClEvent ev = buf.readNextAsync(dest);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        assertArrayEquals(new int[]{3, 6}, dest);
        buf.destroy();
    }

    @Test
    @Order(2004)
    @DisplayName("readNextSync() (no args) reads exactly 1 element and advances by 1")
    void readNext_syncNoArgsReadsOne() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{55, 66, 77, 88});
        int[] got = (int[]) buf.readNextSync();
        assertEquals(1, buf.getPointer());
        assertEquals(55, got[0]);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 21. READ SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2100)
    @DisplayName("readSyncByte returns a byte array with the correct length")
    void readSyncByte_correctLength() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        byte[] bytes = buf.readSyncByte(0, 4);
        assertNotNull(bytes);
        assertEquals(4 * Integer.BYTES, bytes.length);
        buf.destroy();
    }

    @Test
    @Order(2101)
    @DisplayName("readSyncByte encodes the written integer values correctly")
    void readSyncByte_correctEncoding() {
        GlobalReadWriteStaticBuffer buf = rwBuf(1);
        buf.writeSync(new int[]{0x01020304});
        byte[] bytes = buf.readSyncByte(0, 1);
        // Verify all 4 bytes are present (exact byte order is implementation-defined)
        assertEquals(4, bytes.length);
        buf.destroy();
    }

    @Test
    @Order(2102)
    @DisplayName("readSyncByte with offset returns correct sub-range of bytes")
    void readSyncByte_withOffset() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        byte[] bytes = buf.readSyncByte(2, 2); // 2 ints from offset 2
        assertEquals(2 * Integer.BYTES, bytes.length);
        buf.destroy();
    }

    @Test
    @Order(2103)
    @DisplayName("readSyncByte with targetArray overload fills the provided array")
    void readSyncByte_targetArrayOverload() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{10, 20, 30, 40});
        byte[] dest = new byte[4 * Integer.BYTES];
        buf.readSyncByte(dest);
        // bytes must not all be zero
        boolean anyNonZero = false;
        for (byte b : dest) if (b != 0) { anyNonZero = true; break; }
        assertTrue(anyNonZero, "readSyncByte should write non-zero bytes from {10,20,30,40}");
        buf.destroy();
    }

    @Test
    @Order(2104)
    @DisplayName("readNextSyncByte advances pointer and returns correct bytes")
    void readSyncByte_nextAdvancesPointer() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        byte[] bytes = buf.readNextSyncByte(2); // reads 2 ints
        assertEquals(2, buf.getPointer());
        assertEquals(2 * Integer.BYTES, bytes.length);
        buf.destroy();
    }

    @Test
    @Order(2105)
    @DisplayName("readNextSyncByte() (no args) reads 1 element and advances by 1")
    void readSyncByte_nextNoArgs() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{7, 8, 9, 10});
        byte[] bytes = buf.readNextSyncByte();
        assertEquals(1, buf.getPointer());
        assertEquals(Integer.BYTES, bytes.length);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 22. READ ASYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2200)
    @DisplayName("readAsyncByte returns a non-null ClEvent and fills target array")
    void readAsyncByte_returnsAndFills() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        byte[] dest = new byte[4 * Integer.BYTES];
        ClEvent ev = buf.readAsyncByte(0, dest);
        assertNotNull(ev);
        ev.waitForComplete();
        boolean anyNonZero = false;
        for (byte b : dest) if (b != 0) { anyNonZero = true; break; }
        assertTrue(anyNonZero);
        buf.destroy();
    }

    @Test
    @Order(2201)
    @DisplayName("readNextAsyncByte advances pointer and event completes")
    void readAsyncByte_nextAdvancesPointer() throws Exception {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{10, 20, 30, 40});
        byte[] dest = new byte[2 * Integer.BYTES];
        ClEvent ev = buf.readNextAsyncByte(dest);
        assertEquals(2, buf.getPointer());
        assertDoesNotThrow(ev::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 23. ROUND-TRIP: write → GPU kernel → read
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2300)
    @DisplayName("Round-trip: write data, run GPU kernel to double each element, read back")
    void roundTrip_kernelDoubles() throws Exception {
        int n = 64;
        GlobalReadWriteStaticBuffer buf = rwBuf(n);

        // Write: [0, 1, 2, ..., n-1]
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i;
        buf.writeSync(input);

        // Bind to kernel that doubles every element: data[gid] *= 2
        clKernel = buildKernel("double_it", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        // Read back
        int[] result = (int[]) buf.readSync(n);
        for (int i = 0; i < n; i++) {
            assertEquals(i * 2, result[i],
                    "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(2301)
    @DisplayName("Round-trip async: writeAsync → kernel → readAsync")
    void roundTrip_asyncWriteKernelAsyncRead() throws Exception {
        int n = 32;
        GlobalReadWriteStaticBuffer buf = rwBuf(n);

        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;

        // Async write
        ClEvent wEv = buf.writeAsync(input);
        ClEventList dep = new ClEventList(wEv);

        // Bind kernel: data[gid] += 10
        clKernel = buildKernel("add_ten", "", "buf[get_global_id(0)] += 10;");
        buf.bindToKernel(clKernel, 0);
        dep.waitForComplete();
        enqueueKernel(clKernel, n);

        // Async read
        int[] dest = new int[n];
        ClEvent rEv = buf.readAsync(dest);
        rEv.waitForComplete();

        for (int i = 0; i < n; i++) {
            assertEquals(i + 11, dest[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(2303)
    @DisplayName("Round-trip byte: writeSyncByte → readSyncByte preserves raw bytes")
    void roundTrip_byteLevel() {
        int n = 4;
        GlobalReadWriteStaticBuffer buf = rwBuf(n);

        // Write via typed path, read back as bytes
        int[] typed = {0xDEAD_BEEF, 0xCAFE_BABE, 0x0102_0304, 0xFFFF_FFFF};
        buf.writeSync(typed);
        byte[] bytesOut = buf.readSyncByte(0, n);
        assertEquals(n * Integer.BYTES, bytesOut.length);

        // Now write those bytes back and compare typed read
        buf.writeSyncByte(bytesOut);
        int[] roundTripped = (int[]) buf.readSync(n);
        assertArrayEquals(typed, roundTripped);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 24. POINTER + WRITE/READ INTERACTION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2400)
    @DisplayName("setPointer + writeNextSync writes into correct positions")
    void pointerWrite_setThenWriteNext() {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        // Initialise all to 0
        buf.writeSync(new int[]{0, 0, 0, 0, 0, 0, 0, 0});
        // Seek to position 4 and write 2 elements
        buf.setPointer(4);
        buf.writeNextSync(new int[]{77, 88});
        assertEquals(6, buf.getPointer());

        int[] all = (int[]) buf.readSync(8);
        assertEquals(77, all[4]);
        assertEquals(88, all[5]);
        buf.destroy();
    }

    @Test
    @Order(2401)
    @DisplayName("setPointer resets between operations without corrupting data")
    void pointerWrite_resetAndReread() {
        GlobalReadWriteStaticBuffer buf = rwBuf(4);
        buf.writeSync(new int[]{1, 2, 3, 4});
        buf.setPointer(0);
        int[] a = (int[]) buf.readNextSync(2);
        int[] b = (int[]) buf.readNextSync(2);
        assertArrayEquals(new int[]{1, 2}, a);
        assertArrayEquals(new int[]{3, 4}, b);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 25. THREAD SAFETY – interleaved writes and reads
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2500)
    @DisplayName("Concurrent writeSync calls do not throw and leave buffer usable")
    void thread_concurrentWriteSync() throws InterruptedException {
        GlobalReadWriteStaticBuffer buf = rwBuf(16);
        int n = 8;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] data = {i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i}; // 16 ints
            // each thread overwrites the whole buffer
            futures.add(exec.submit(() -> buf.writeSync(data)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent writeSync");
        }
        // Buffer should still be usable
        assertDoesNotThrow(() -> buf.readSync(16));
        buf.destroy();
    }

    @Test
    @Order(2501)
    @DisplayName("Concurrent readSync calls do not throw")
    void thread_concurrentReadSync() throws InterruptedException {
        GlobalReadWriteStaticBuffer buf = rwBuf(8);
        buf.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        int n = 8;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(exec.submit(() -> buf.readSync(8)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
        buf.destroy();
    }

    @Test
    @Order(2502)
    @DisplayName("Interleaved async writes with event chaining complete without deadlock")
    void thread_asyncWriteChain() throws Exception {
        int n = 8;
        GlobalReadWriteStaticBuffer buf = rwBuf(n);
        ClEvent prev = buf.writeAsync(new int[]{0, 0, 0, 0, 0, 0, 0, 0});
        for (int i = 1; i <= 5; i++) {
            int[] data = new int[n];
            for (int j = 0; j < n; j++) data[j] = i;
            ClEvent cur = buf.writeAsync(new ClEventList(prev), data);
            prev = cur;
        }
        assertDoesNotThrow(prev::waitForComplete);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 26. COPY – correctness with write then copy then read
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2600)
    @DisplayName("Copy from src to dst preserves data correctly")
    void copy_dataPreservedAfterCopy() throws Exception {
        GlobalReadWriteStaticBuffer src = rwBuf(8);
        GlobalReadWriteStaticBuffer dst = rwBuf(8);
        int[] payload = {10, 20, 30, 40, 50, 60, 70, 80};
        src.writeSync(payload);

        ClEvent ev = dst.copyFrom(src);
        ev.waitForComplete();

        int[] result = (int[]) dst.readSync(8);
        assertArrayEquals(payload, result);
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(2601)
    @DisplayName("Partial copy (offset + size) places data at the correct destination offset")
    void copy_partialCopyOffsets() throws Exception {
        GlobalReadWriteStaticBuffer src = rwBuf(8);
        GlobalReadWriteStaticBuffer dst = rwBuf(8);
        src.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        dst.writeSync(new int[]{0, 0, 0, 0, 0, 0, 0, 0});

        // Copy elements [2..5] of src into elements [4..7] of dst
        ClEvent ev = dst.copyFrom(src, 2, 4, 4);
        ev.waitForComplete();

        int[] result = (int[]) dst.readSync(8);
        assertArrayEquals(new int[]{0, 0, 0, 0, 3, 4, 5, 6}, result);
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(2602)
    @DisplayName("copyTo is symmetric with copyFrom – same data arrives at destination")
    void copy_copyToSymmetry() throws Exception {
        GlobalReadWriteStaticBuffer src = rwBuf(4);
        GlobalReadWriteStaticBuffer dst = rwBuf(4);
        int[] data = {-1, -2, -3, -4};
        src.writeSync(data);

        ClEvent ev = src.copyTo(dst);
        ev.waitForComplete();

        assertArrayEquals(data, (int[]) dst.readSync(4));
        src.destroy();
        dst.destroy();
    }
}
