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
class GlobalReadOnlyDynamicBufferTest extends AbstractGlobalDynamicalBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalReadOnlyDynamicBuffer createBuffer(int capacity) {
        return new GlobalReadOnlyDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalReadOnlyDynamicBuffer createBuffer(
            String name, int capacity) {
        return new GlobalReadOnlyDynamicBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity);
    }

    @Override
    protected boolean supportsHostRead()  { return true;  }
    @Override
    protected boolean supportsHostWrite() { return false; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Writes {@code data} into a temporary ReadWrite buffer and copies it
     * into {@code dst}, waits for completion, then destroys the temp buffer.
     */
    private void plant(GlobalReadOnlyDynamicBuffer dst, int[] data) {
        GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, dst.getCapacity());
        tmp.writeSync(data);
        dst.copyFrom(tmp, 0, 0, data.length).waitForComplete();
        tmp.destroy();
    }

    private GlobalReadOnlyDynamicBuffer roDyn(int capacity) {
        return createBuffer(capacity);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. HOST WRITE IS FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1300)
    @DisplayName("WritableGlobal interface is absent on a read-only dynamic buffer")
    void readOnly_writableNotAvailable() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        assertFalse(buf instanceof WritableGlobal,
                "GlobalReadOnlyDynamicBuffer must not implement WritableGlobal");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. READ SYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1400)
    @DisplayName("readSync(len) returns a non-null int[] of the requested length")
    void readSync_returnsCorrectLength() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        Object result = buf.readSync(cap);
        assertNotNull(result);
        assertInstanceOf(int[].class, result);
        assertEquals(cap, ((int[]) result).length);
        buf.destroy();
    }

    @Test @Order(1401)
    @DisplayName("readSync returns the correct data planted via copyFrom")
    void readSync_correctData() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] expected = new int[cap];
        for (int i = 0; i < cap; i++) expected[i] = i * 10;
        plant(buf, expected);

        assertArrayEquals(expected, (int[]) buf.readSync(cap));
        buf.destroy();
    }

    @Test @Order(1402)
    @DisplayName("readSync(offset, len) reads the correct sub-range")
    void readSync_offsetSlice() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int[] got = (int[]) buf.readSync(2, 4);
        assertArrayEquals(new int[]{2, 3, 4, 5}, got);
        buf.destroy();
    }

    @Test @Order(1403)
    @DisplayName("readSync with targetArray overload fills the provided array")
    void readSync_targetArray() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] expected = new int[cap];
        for (int i = 0; i < cap; i++) expected[i] = i + 1;
        plant(buf, expected);

        int[] dest = new int[cap];
        buf.readSync(dest);
        assertArrayEquals(expected, dest);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. READ ASYNC
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1500)
    @DisplayName("readAsync returns a non-null ClEvent and fills target array correctly")
    void readAsync_returnsAndFills() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] expected = new int[cap];
        for (int i = 0; i < cap; i++) expected[i] = i;
        plant(buf, expected);

        int[] dest = new int[cap];
        ClEvent ev = buf.readAsync(dest);
        assertNotNull(ev);
        ev.waitForComplete();
        assertArrayEquals(expected, dest);
        buf.destroy();
    }

    @Test @Order(1501)
    @DisplayName("readAsync with offset reads the correct sub-range")
    void readAsync_withOffset() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int[] dest = new int[3];
        buf.readAsync(2, dest).waitForComplete();
        assertArrayEquals(new int[]{2, 3, 4}, dest);
        buf.destroy();
    }

    @Test @Order(1502)
    @DisplayName("readAsync chained with copyFrom event reads correct data")
    void readAsync_chainedWithCopyFrom() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] expected = new int[cap];
        for (int i = 0; i < cap; i++) expected[i] = i + 100;

        GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, cap);
        tmp.writeSync(expected);
        ClEvent copyEv = buf.copyFrom(tmp, 0, 0, cap);

        int[] dest = new int[cap];
        buf.readAsync(new ClEventList(copyEv), dest).waitForComplete();
        assertArrayEquals(expected, dest);
        tmp.destroy();
        buf.destroy();
    }

    @Test @Order(1503)
    @DisplayName("readAsync with null target throws NullPointerException")
    void readAsync_nullTargetThrows() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        assertThrows(NullPointerException.class, () -> buf.readAsync(null));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. READ NEXT – pointer-advancing
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1600)
    @DisplayName("readNextSync(len) advances pointer by len")
    void readNext_syncAdvancesPointer() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        plant(buf, new int[cap]);

        buf.readNextSync(3);
        assertEquals(3, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1601)
    @DisplayName("readNextSync reads sequential chunks in order")
    void readNext_syncSequentialChunks() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i * 10;
        plant(buf, data);

        int[] a = (int[]) buf.readNextSync(2);
        int[] b = (int[]) buf.readNextSync(2);
        assertArrayEquals(new int[]{0, 10}, a);
        assertArrayEquals(new int[]{20, 30}, b);
        buf.destroy();
    }

    @Test @Order(1602)
    @DisplayName("readNextSync() no-arg reads exactly 1 element and advances by 1")
    void readNext_syncNoArg() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        data[0] = 55;
        plant(buf, data);

        int[] got = (int[]) buf.readNextSync();
        assertEquals(1, buf.getPointer());
        assertEquals(55, got[0]);
        buf.destroy();
    }

    @Test @Order(1603)
    @DisplayName("readNextSync beyond actual capacity throws (reading garbage is a bug)")
    void readNext_syncBeyondCapacityThrows() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        plant(buf, new int[cap]);

        buf.setPointer(cap - 1);
        assertThrows(Exception.class, () -> buf.readNextSync(2),
                "Reading beyond buffer capacity must throw regardless of buffer being dynamic");
        buf.destroy();
    }

    @Test @Order(1604)
    @DisplayName("readNextAsync advances pointer and completes with correct data")
    void readNext_asyncAdvancesAndReturns() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int[] dest = new int[2];
        ClEvent ev = buf.readNextAsync(dest);
        assertEquals(2, buf.getPointer());
        ev.waitForComplete();
        assertArrayEquals(new int[]{0, 1}, dest);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. READ SYNC BYTE
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1700)
    @DisplayName("readSyncByte returns a byte array of the correct length")
    void readSyncByte_correctLength() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        plant(buf, new int[cap]);

        byte[] bytes = buf.readSyncByte(0, cap);
        assertEquals(cap * Integer.BYTES, bytes.length);
        buf.destroy();
    }

    @Test @Order(1701)
    @DisplayName("readSyncByte with targetArray overload fills the array with non-zero bytes")
    void readSyncByte_targetArray() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i + 1;
        plant(buf, data);

        byte[] dest = new byte[cap * Integer.BYTES];
        buf.readSyncByte(dest);
        boolean anyNonZero = false;
        for (byte b : dest) if (b != 0) { anyNonZero = true; break; }
        assertTrue(anyNonZero);
        buf.destroy();
    }

    @Test @Order(1702)
    @DisplayName("readNextSyncByte advances pointer correctly")
    void readSyncByte_nextAdvancesPointer() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        plant(buf, new int[cap]);

        buf.readNextSyncByte(2);
        assertEquals(2, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1703)
    @DisplayName("readNextSyncByte() no-arg reads 1 element and advances by 1")
    void readSyncByte_nextNoArg() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        plant(buf, new int[cap]);

        byte[] bytes = buf.readNextSyncByte();
        assertEquals(1, buf.getPointer());
        assertEquals(Integer.BYTES, bytes.length);
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 18. DATA PRESERVATION during resize
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1800)
    @DisplayName("all data is preserved after a grow resize")
    void preserve_fullDataAfterGrow() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        buf.resize(cap + 100);

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "All original data must survive a grow resize");
        buf.destroy();
    }

    @Test @Order(1801)
    @DisplayName("prefix data is preserved after a shrink resize")
    void preserve_prefixAfterShrink() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

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
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i * 2;
        plant(buf, data);

        buf.increase(cap + 50);

        assertArrayEquals(data, (int[]) buf.readSync(cap),
                "Data must survive increase()");
        buf.destroy();
    }

    @Test @Order(1803)
    @DisplayName("prefix data is preserved after shrink via decrease()")
    void preserve_prefixAfterDecrease() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int shrinkTarget = cap / 4;
        buf.decrease(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), (int[]) buf.readSync(newCap),
                "Prefix data must survive decrease()");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 19. ROUND-TRIP: plant → GPU kernel → read
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1900)
    @DisplayName("Round-trip: plant data, kernel doubles each element, read back")
    void roundTrip_kernelDoubles() {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        clKernel = buildKernel("double_rod", "", "buf[get_global_id(0)] *= 2;");
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
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);

        clKernel = buildKernel("add5_rod", "", "buf[get_global_id(0)] += 5;");
        buf.bindToKernel(clKernel, 0);

        buf.resize(buf.getCapacity() + 30);
        int newCap = buf.getCapacity();
        int[] data = new int[newCap];
        for (int i = 0; i < newCap; i++) data[i] = i;
        plant(buf, data);
        enqueueKernel(clKernel, newCap);

        int[] result = (int[]) buf.readSync(newCap);
        for (int i = 0; i < newCap; i++) {
            assertEquals(i + 5, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1902)
    @DisplayName("Round-trip: buffer used as input-only arg in two-buffer kernel")
    void roundTrip_twoBufferKernel() {
        int n = 15;
        GlobalReadOnlyDynamicBuffer inputBuf = roDyn(10);
        int cap = inputBuf.getCapacity();
        int[] input = new int[cap];
        for (int i = 0; i < cap; i++) input[i] = i;
        plant(inputBuf, input);

        GlobalReadWriteDynamicBuffer outputBuf = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, cap);

        String src =
                "__kernel void copy_add_rod(__global int* in, __global int* out) {" +
                        "    int gid = get_global_id(0);" +
                        "    out[gid] = in[gid] + 200;" +
                        "}";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), src, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            assertEquals(CL_SUCCESS, clBuildProgram(program, device.getDeviceID(), "", null, 0));
            clKernel = clCreateKernel(program, "copy_add_rod", errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            clProgram = program;
        }

        inputBuf.bindToKernel(clKernel, 0);
        outputBuf.bindToKernel(clKernel, 1);
        enqueueKernel(clKernel, cap);

        int[] result = (int[]) outputBuf.readSync(cap);
        for (int i = 0; i < cap; i++) {
            assertEquals(i + 200, result[i], "Mismatch at index " + i);
        }

        inputBuf.destroy();
        outputBuf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 20. COPY
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(2000)
    @DisplayName("copyFrom a ReadWrite buffer preserves data correctly")
    void copy_fromReadWrite() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 5;
        src.writeSync(payload);

        GlobalReadOnlyDynamicBuffer dst = roDyn(10);
        dst.copyFrom(src, 0, 0, cap).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(cap));
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

        GlobalReadOnlyDynamicBuffer dst = roDyn(10); // cap = 15
        dst.copyFrom(src).waitForComplete();

        assertTrue(dst.getCapacity() >= srcCap,
                "dst must auto-grow to accommodate the full source buffer");
        assertArrayEquals(payload, (int[]) dst.readSync(srcCap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(2002)
    @DisplayName("copyTo a ReadWrite buffer preserves data correctly")
    void copy_toReadWrite() {
        GlobalReadOnlyDynamicBuffer src = roDyn(10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = -i;
        plant(src, payload);

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

        GlobalReadOnlyDynamicBuffer dst = roDyn(10);
        dst.copyFrom(src, 0, 2, 4).waitForComplete(); // src[0..3] → dst[2..5]

        int[] result = (int[]) dst.readSync(cap);
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
    @DisplayName("Concurrent readSync calls do not throw")
    void thread_concurrentReadSync() throws InterruptedException {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int n = 8;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(exec.submit(() -> buf.readSync(cap)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
        buf.destroy();
    }

    @Test @Order(2101)
    @DisplayName("Concurrent resize calls do not corrupt the buffer (usable after)")
    void thread_concurrentResizes() throws InterruptedException {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
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
    @DisplayName("Concurrent readAsync calls complete without error")
    void thread_concurrentReadAsync() throws InterruptedException {
        GlobalReadOnlyDynamicBuffer buf = roDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int n = 6;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(exec.submit(() -> {
                int[] dest = new int[cap];
                buf.readAsync(dest).waitForComplete();
            }));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
        buf.destroy();
    }
}
