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

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.buffer.WritableGlobal;
import io.github.kushnirvladyslav.memory.buffer.typedBuffer.globalBuffers.*;
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import org.junit.jupiter.api.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalNoAccessDynamicBufferTest extends AbstractGlobalDynamicalBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalNoAccessDynamicBuffer createBuffer(int capacity) {
        return new GlobalNoAccessDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalNoAccessDynamicBuffer createBuffer(
            String name, int capacity, boolean stagingBuffer) {
        return new GlobalNoAccessDynamicBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity, stagingBuffer);
    }

    @Override
    protected boolean supportsHostRead()  { return false; }
    @Override
    protected boolean supportsHostWrite() { return false; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Writes {@code data} into a temporary ReadWrite buffer and copies it
     * into {@code dst}, waits for completion, then destroys the temp buffer.
     */
    private void plant(GlobalNoAccessDynamicBuffer dst, int[] data) {
        GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, dst.getCapacity());
        tmp.writeSync(data);
        dst.copyFrom(tmp, 0, 0, data.length).waitForComplete();
        tmp.destroy();
    }

    /**
     * Copies {@code len} elements from {@code src} into a temporary ReadWrite buffer,
     * reads the data, destroys the temporary buffer, and returns the result.
     */
    private int[] harvest(GlobalNoAccessDynamicBuffer src, int len) {
        GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, len);
        src.copyTo(tmp, 0, 0, len).waitForComplete();
        int[] result = (int[]) tmp.readSync(len);
        tmp.destroy();
        return result;
    }

    private int[] harvest(GlobalNoAccessDynamicBuffer src) {
        return harvest(src, src.getCapacity());
    }

    private GlobalNoAccessDynamicBuffer naDyn(int capacity) {
        return createBuffer(capacity);
    }

    private GlobalNoAccessDynamicBuffer naDynStaged(int capacity) {
        return createBuffer("staged-nad-" + capacity, capacity, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. HOST READ AND WRITE ARE FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1300)
    @DisplayName("ReadableGlobal interface is absent on a no-access dynamic buffer")
    void noAccess_readableNotAvailable() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        assertFalse(buf instanceof Readable,
                "GlobalNoAccessDynamicBuffer must not implement ReadableGlobal");
        buf.destroy();
    }

    @Test @Order(1301)
    @DisplayName("WritableGlobal interface is absent on a no-access dynamic buffer")
    void noAccess_writableNotAvailable() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        assertFalse(buf instanceof WritableGlobal,
                "GlobalNoAccessDynamicBuffer must not implement WritableGlobal");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. COPY – filling and draining the buffer
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1400)
    @DisplayName("copyFrom a ReadWrite buffer fills the no-access buffer without error")
    void copy_fromReadWrite() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        int cap = src.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 5;
        src.writeSync(payload);

        GlobalNoAccessDynamicBuffer dst = naDyn(10);
        assertDoesNotThrow(() -> dst.copyFrom(src, 0, 0, cap).waitForComplete());

        assertArrayEquals(payload, harvest(dst, cap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(1401)
    @DisplayName("copyFrom preserves data that can be harvested via a ReadWrite buffer")
    void copy_fromReadWriteDataPreserved() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int cap = buf.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 10;
        plant(buf, payload);

        assertArrayEquals(payload, harvest(buf));
        buf.destroy();
    }

    @Test @Order(1402)
    @DisplayName("copyTo a ReadWrite buffer preserves data correctly")
    void copy_toReadWrite() {
        GlobalNoAccessDynamicBuffer src = naDyn(10);
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

    @Test @Order(1403)
    @DisplayName("copyFrom auto-grows destination when source is larger")
    void copy_autoGrowsDstWhenSmallerThanSrc() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 100); // cap ≈ 150
        int srcCap = src.getCapacity();
        int[] payload = new int[srcCap];
        for (int i = 0; i < srcCap; i++) payload[i] = i;
        src.writeSync(payload);

        GlobalNoAccessDynamicBuffer dst = naDyn(10); // cap = 15
        dst.copyFrom(src).waitForComplete();

        assertTrue(dst.getCapacity() >= srcCap,
                "dst must auto-grow to accommodate the full source buffer");
        assertArrayEquals(payload, harvest(dst, srcCap));
        src.destroy();
        dst.destroy();
    }

    @Test @Order(1404)
    @DisplayName("copyTo is symmetric with copyFrom – same data arrives at destination")
    void copy_symmetry() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int cap = buf.getCapacity();
        int[] payload = new int[cap];
        for (int i = 0; i < cap; i++) payload[i] = i * 7;
        plant(buf, payload);

        GlobalReadWriteDynamicBuffer dst = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, cap);
        buf.copyTo(dst, 0, 0, cap).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(cap));
        dst.destroy();
        buf.destroy();
    }

    @Test @Order(1405)
    @DisplayName("Partial copyFrom places data at the correct destination offset")
    void copy_partialOffsets() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        int cap = src.getCapacity();
        int[] srcData = new int[cap];
        for (int i = 0; i < cap; i++) srcData[i] = i + 1;
        src.writeSync(srcData);

        GlobalNoAccessDynamicBuffer dst = naDyn(10);
        plant(dst, new int[cap]);                          // initialise to zeros
        dst.copyFrom(src, 0, 2, 4).waitForComplete();     // src[0..3] → dst[2..5]

        int[] result = harvest(dst, cap);
        assertEquals(1, result[2]);
        assertEquals(2, result[3]);
        assertEquals(3, result[4]);
        assertEquals(4, result[5]);
        src.destroy();
        dst.destroy();
    }

    @Test @Order(1406)
    @DisplayName("copyFrom with a closed source throws BufferOperationException")
    void copy_fromClosedSourceThrows() {
        GlobalReadWriteDynamicBuffer src = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, 10);
        GlobalNoAccessDynamicBuffer dst = naDyn(10);
        src.destroy();
        assertThrows(BufferOperationException.class, () -> dst.copyFrom(src));
        dst.destroy();
    }

    @Test @Order(1407)
    @DisplayName("copyFrom with null source throws BufferOperationException")
    void copy_fromNullSourceThrows() {
        GlobalNoAccessDynamicBuffer dst = naDyn(10);
        assertThrows(BufferOperationException.class, () -> dst.copyFrom(null));
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. DATA PRESERVATION during resize
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1500)
    @DisplayName("all data is preserved after a grow resize")
    void preserve_fullDataAfterGrow() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        buf.resize(cap + 100);

        assertArrayEquals(data, harvest(buf, cap),
                "All original data must survive a grow resize");
        buf.destroy();
    }

    @Test @Order(1501)
    @DisplayName("prefix data is preserved after a shrink resize")
    void preserve_prefixAfterShrink() {
        GlobalNoAccessDynamicBuffer buf = naDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive a shrink resize");
        buf.destroy();
    }

    @Test @Order(1502)
    @DisplayName("data is preserved after grow via increase()")
    void preserve_dataAfterIncrease() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i * 3;
        plant(buf, data);

        buf.increase(cap + 50);

        assertArrayEquals(data, harvest(buf, cap),
                "Data must survive increase()");
        buf.destroy();
    }

    @Test @Order(1503)
    @DisplayName("prefix data is preserved after shrink via decrease()")
    void preserve_prefixAfterDecrease() {
        GlobalNoAccessDynamicBuffer buf = naDyn(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int shrinkTarget = cap / 4;
        buf.decrease(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive decrease()");
        buf.destroy();
    }

    @Test @Order(1504)
    @DisplayName("staging buffer: data is preserved after grow resize")
    void preserve_stagingDataAfterGrow() {
        GlobalNoAccessDynamicBuffer buf = naDynStaged(10);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        buf.resize(cap + 50);

        assertArrayEquals(data, harvest(buf, cap),
                "Data must survive grow resize with staging buffer");
        buf.destroy();
    }

    @Test @Order(1505)
    @DisplayName("staging buffer: prefix data is preserved after shrink resize")
    void preserve_stagingDataAfterShrink() {
        GlobalNoAccessDynamicBuffer buf = naDynStaged(100);
        int cap = buf.getCapacity();
        int[] data = new int[cap];
        for (int i = 0; i < cap; i++) data[i] = i;
        plant(buf, data);

        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();

        assertArrayEquals(Arrays.copyOf(data, newCap), harvest(buf),
                "Prefix data must survive shrink with staging buffer");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 16. ROUND-TRIP: plant → GPU kernel → harvest
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1600)
    @DisplayName("Round-trip: plant data, kernel doubles each element, harvest")
    void roundTrip_kernelDoubles() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        clKernel = buildKernel("double_nad", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf, n);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1601)
    @DisplayName("Round-trip: kernel bound before grow resize executes correctly after resize")
    void roundTrip_kernelBoundBeforeResizeExecutesAfter() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);

        clKernel = buildKernel("triple_nad", "", "buf[get_global_id(0)] *= 3;");
        buf.bindToKernel(clKernel, 0);

        buf.resize(buf.getCapacity() + 30);
        int newCap = buf.getCapacity();
        int[] data = new int[newCap];
        for (int i = 0; i < newCap; i++) data[i] = i + 1;
        plant(buf, data);
        enqueueKernel(clKernel, newCap);

        int[] result = harvest(buf, newCap);
        for (int i = 0; i < newCap; i++) {
            assertEquals((i + 1) * 3, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1602)
    @DisplayName("Round-trip: no-access buffer used as intermediate stage in a pipeline")
    void roundTrip_intermediateStage() {
        int n = 10;
        GlobalReadWriteDynamicBuffer rw = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, n);
        int cap = rw.getCapacity();

        GlobalNoAccessDynamicBuffer na = naDyn(n);

        int[] input = new int[cap];
        for (int i = 0; i < cap; i++) input[i] = i + 1;
        rw.writeSync(input);
        na.copyFrom(rw, 0, 0, cap).waitForComplete();

        clKernel = buildKernel("quadruple_nad", "", "buf[get_global_id(0)] *= 4;");
        na.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, cap);

        na.copyTo(rw, 0, 0, cap).waitForComplete();
        int[] result = (int[]) rw.readSync(cap);

        for (int i = 0; i < cap; i++) {
            assertEquals((i + 1) * 4, result[i], "Mismatch at index " + i);
        }

        rw.destroy();
        na.destroy();
    }

    @Test @Order(1603)
    @DisplayName("Round-trip: two-buffer kernel reads from ReadWrite, writes to NoAccess, harvest verifies")
    void roundTrip_twoBufferKernel() {
        int n = 10;
        GlobalReadWriteDynamicBuffer inputBuf = new GlobalReadWriteDynamicBufferBuilder()
                .setup(IntDataProcessor.class, context, n);
        int cap = inputBuf.getCapacity();
        int[] input = new int[cap];
        for (int i = 0; i < cap; i++) input[i] = i;
        inputBuf.writeSync(input);

        GlobalNoAccessDynamicBuffer outputBuf = naDyn(n);

        String src =
                "__kernel void copy_add_nad(__global int* in, __global int* out) {" +
                        "    int gid = get_global_id(0);" +
                        "    out[gid] = in[gid] + 400;" +
                        "}";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), src, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            assertEquals(CL_SUCCESS, clBuildProgram(program, device.getDeviceID(), "", null, 0));
            clKernel = clCreateKernel(program, "copy_add_nad", errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            clProgram = program;
        }

        inputBuf.bindToKernel(clKernel, 0);
        outputBuf.bindToKernel(clKernel, 1);
        enqueueKernel(clKernel, cap);

        int[] result = harvest(outputBuf, cap);
        for (int i = 0; i < cap; i++) {
            assertEquals(i + 400, result[i], "Mismatch at index " + i);
        }

        inputBuf.destroy();
        outputBuf.destroy();
    }

    @Test @Order(1604)
    @DisplayName("Round-trip with staging buffer: plant, kernel, harvest")
    void roundTrip_stagingBuffer() {
        GlobalNoAccessDynamicBuffer buf = naDynStaged(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = n - i;
        plant(buf, input);

        clKernel = buildKernel("negate_staged_nad",
                "", "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf, n);
        for (int i = 0; i < n; i++) {
            assertEquals(-(n - i), result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test @Order(1605)
    @DisplayName("Round-trip: two sequential kernel passes produce correct cumulative result")
    void roundTrip_twoKernelPasses() {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int n = buf.getCapacity();
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        // Pass 1: multiply by 2
        clKernel = buildKernel("pass1_nad", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);
        buf.unbindKernel(clKernel);

        // Pass 2: add 10
        long kernel2;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String src2 =
                    "__kernel void pass2_nad(__global int* buf) {" +
                            "    buf[get_global_id(0)] += 10;" +
                            "}";
            IntBuffer errBuf = stack.mallocInt(1);
            long prog2 = clCreateProgramWithSource(context.getContext(), src2, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            assertEquals(CL_SUCCESS, clBuildProgram(prog2, device.getDeviceID(), "", null, 0));
            kernel2 = clCreateKernel(prog2, "pass2_nad", errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            long prevProgram = clProgram;
            clProgram = prog2;
            clReleaseProgram(prevProgram);
        }
        buf.bindToKernel(kernel2, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer gws = stack.mallocPointer(1).put(0, n);
            clEnqueueNDRangeKernel(context.getCommandQueue(), kernel2, 1, null, gws, null, null, null);
            clFinish(context.getCommandQueue());
        }
        clReleaseKernel(kernel2);

        int[] result = harvest(buf, n);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2 + 10, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 17. THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1700)
    @DisplayName("Concurrent copyFrom calls do not throw and leave buffer usable")
    void thread_concurrentCopyFrom() throws InterruptedException {
        GlobalNoAccessDynamicBuffer dst = naDyn(10);
        int cap = dst.getCapacity();
        int n = 4;
        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int val = i;
            futures.add(exec.submit(() -> {
                GlobalReadWriteDynamicBuffer tmp = new GlobalReadWriteDynamicBufferBuilder()
                        .setup(IntDataProcessor.class, context, cap);
                int[] data = new int[cap];
                Arrays.fill(data, val);
                tmp.writeSync(data);
                dst.copyFrom(tmp, 0, 0, cap).waitForComplete();
                tmp.destroy();
            }));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent copyFrom");
        }
        assertFalse(dst.isClosed());
        dst.destroy();
    }

    @Test @Order(1701)
    @DisplayName("Concurrent resize calls do not corrupt the buffer (usable after)")
    void thread_concurrentResizes() throws InterruptedException {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
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
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent resize");
        }
        assertFalse(buf.isClosed());
        assertTrue(buf.getCapacity() >= base);
        buf.destroy();
    }

    @Test @Order(1702)
    @DisplayName("Concurrent kernel bindings do not throw")
    void thread_concurrentKernelBindings() throws InterruptedException {
        GlobalNoAccessDynamicBuffer buf = naDyn(10);
        int n = 6;
        long[] kernels = new long[n];
        for (int i = 0; i < n; i++) {
            kernels[i] = buildKernel("knad_" + i, "", "buf[get_global_id(0)] += " + i + ";");
            clProgram = 0;
        }

        ExecutorService exec = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final long k = kernels[i];
            futures.add(exec.submit(() -> buf.bindToKernel(k, 0)));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent binding");
        }

        for (long k : kernels) clReleaseKernel(k);
        buf.destroy();
    }
}
