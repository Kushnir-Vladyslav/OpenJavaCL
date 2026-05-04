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
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
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
class GlobalNoAccessStaticBufferTest extends AbstractGlobalStaticBufferTest {

    // ── Factory / capability ─────────────────────────────────────────────────

    @Override
    protected GlobalNoAccessStaticBuffer createBuffer(int capacity) {
        return new GlobalNoAccessStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, capacity);
    }

    @Override
    protected GlobalNoAccessStaticBuffer createBuffer(
            String name, int capacity) {
        return new GlobalNoAccessStaticBufferBuilder()
                .setup(name, IntDataProcessor.class, context, capacity);
    }

    @Override
    protected boolean supportsHostRead()  { return false; }
    @Override
    protected boolean supportsHostWrite() { return false; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Writes {@code data} into a temporary ReadWrite buffer and copies it
     * into {@code dst}, then destroys the temporary buffer.
     */
    private void plant(GlobalNoAccessStaticBuffer dst, int[] data) {
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, dst.getCapacity());
        tmp.writeSync(data);
        dst.copyFrom(tmp).waitForComplete();
        tmp.destroy();
    }

    /**
     * Copies {@code src} into a temporary ReadWrite buffer, reads the data,
     * destroys the temporary buffer, and returns the result.
     */
    private int[] harvest(GlobalNoAccessStaticBuffer src) {
        GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, src.getCapacity());
        tmp.copyFrom(src).waitForComplete();
        int[] result = (int[]) tmp.readSync(src.getCapacity());
        tmp.destroy();
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. HOST READ AND WRITE ARE FORBIDDEN
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1100)
    @DisplayName("ReadableGlobal interface is absent on a no-access buffer")
    void noAccess_readableNotAvailable() {
        GlobalNoAccessStaticBuffer buf = createBuffer(4);
        assertFalse(buf instanceof Readable,
                "GlobalNoAccessStaticBuffer must not implement ReadableGlobal");
        buf.destroy();
    }

    @Test
    @Order(1101)
    @DisplayName("WritableGlobal interface is absent on a no-access buffer")
    void noAccess_writableNotAvailable() {
        GlobalNoAccessStaticBuffer buf = createBuffer(4);
        assertFalse(buf instanceof WritableGlobal,
                "GlobalNoAccessStaticBuffer must not implement WritableGlobal");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. COPY – filling the buffer from a host-accessible source
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1200)
    @DisplayName("copyFrom a ReadWrite buffer fills the no-access buffer without error")
    void copy_fromReadWrite() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 8);
        int[] payload = {1, 2, 3, 4, 5, 6, 7, 8};
        src.writeSync(payload);

        GlobalNoAccessStaticBuffer dst = createBuffer(8);
        assertDoesNotThrow(() -> dst.copyFrom(src).waitForComplete());

        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(1201)
    @DisplayName("copyFrom preserves data that can be harvested via a ReadWrite buffer")
    void copy_fromReadWriteDataPreserved() {
        GlobalNoAccessStaticBuffer buf = createBuffer(8);
        int[] payload = {10, 20, 30, 40, 50, 60, 70, 80};
        plant(buf, payload);

        assertArrayEquals(payload, harvest(buf));
        buf.destroy();
    }

    @Test
    @Order(1202)
    @DisplayName("copyTo a ReadWrite buffer preserves data correctly")
    void copy_toReadWrite() {
        GlobalNoAccessStaticBuffer src = createBuffer(4);
        int[] payload = {-1, -2, -3, -4};
        plant(src, payload);

        GlobalReadWriteStaticBuffer dst = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        src.copyTo(dst).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(4));
        dst.destroy();
        src.destroy();
    }

    @Test
    @Order(1203)
    @DisplayName("Partial copyFrom places data at the correct destination offset")
    void copy_partialFromReadWrite() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 8);
        src.writeSync(new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        GlobalNoAccessStaticBuffer dst = createBuffer(8);
        plant(dst, new int[]{0, 0, 0, 0, 0, 0, 0, 0});

        dst.copyFrom(src, 2, 4, 4).waitForComplete(); // src[2..5] → dst[4..7]

        int[] result = harvest(dst);
        assertArrayEquals(new int[]{0, 0, 0, 0, 3, 4, 5, 6}, result);
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(1204)
    @DisplayName("copyFrom beyond capacity throws for static no-access buffer")
    void copy_fromOversizedThrows() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 16);
        GlobalNoAccessStaticBuffer dst = createBuffer(4);
        assertThrows(Exception.class,
                () -> dst.copyFrom(src, 0, 0, 8));
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(1205)
    @DisplayName("copyTo is symmetric with copyFrom – same data arrives at destination")
    void copy_symmetry() {
        GlobalNoAccessStaticBuffer buf = createBuffer(4);
        int[] payload = {7, 14, 21, 28};
        plant(buf, payload);

        GlobalReadWriteStaticBuffer dst = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        buf.copyTo(dst).waitForComplete();

        assertArrayEquals(payload, (int[]) dst.readSync(4));
        dst.destroy();
        buf.destroy();
    }

    @Test
    @Order(1206)
    @DisplayName("copyFrom with a closed source throws BufferOperationException")
    void copy_fromClosedSourceThrows() {
        GlobalReadWriteStaticBuffer src = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, 4);
        GlobalNoAccessStaticBuffer dst = createBuffer(4);
        src.destroy();
        assertThrows(BufferOperationException.class, () -> dst.copyFrom(src));
        dst.destroy();
    }

    @Test
    @Order(1207)
    @DisplayName("copyFrom with null source throws BufferOperationException")
    void copy_fromNullSourceThrows() {
        GlobalNoAccessStaticBuffer dst = createBuffer(4);
        assertThrows(BufferOperationException.class, () -> dst.copyFrom(null));
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. ROUND-TRIP: plant → GPU kernel → harvest
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1300)
    @DisplayName("Round-trip: plant data, run kernel to double each element, harvest")
    void roundTrip_kernelDoubles() {
        int n = 32;
        GlobalNoAccessStaticBuffer buf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        clKernel = buildKernel("double_na", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(1301)
    @DisplayName("Round-trip: plant data, run kernel to negate each element, harvest")
    void roundTrip_kernelNegate() {
        int n = 16;
        GlobalNoAccessStaticBuffer buf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        clKernel = buildKernel("negate_na", "", "buf[get_global_id(0)] = -buf[get_global_id(0)];");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        int[] result = harvest(buf);
        for (int i = 0; i < n; i++) {
            assertEquals(-(i + 1), result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    @Test
    @Order(1302)
    @DisplayName("Round-trip: buffer used as input-only arg in two-buffer kernel")
    void roundTrip_twoBufferKernel() {
        int n = 16;
        GlobalNoAccessStaticBuffer inputBuf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i;
        plant(inputBuf, input);

        GlobalReadWriteStaticBuffer outputBuf = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, n);

        String src =
                "__kernel void copy_add(__global int* in, __global int* out) {" +
                        "    int gid = get_global_id(0);" +
                        "    out[gid] = in[gid] + 100;" +
                        "}";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), src, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            assertEquals(CL_SUCCESS, clBuildProgram(program, device.getDeviceID(), "", null, 0));
            clKernel = clCreateKernel(program, "copy_add", errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0));
            clProgram = program;
        }

        inputBuf.bindToKernel(clKernel, 0);
        outputBuf.bindToKernel(clKernel, 1);
        enqueueKernel(clKernel, n);

        int[] result = (int[]) outputBuf.readSync(n);
        for (int i = 0; i < n; i++) {
            assertEquals(i + 100, result[i], "Mismatch at index " + i);
        }

        inputBuf.destroy();
        outputBuf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 14. NO-ACCESS BUFFER AS INTERMEDIATE STAGE IN A PIPELINE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1400)
    @DisplayName("Pipeline: ReadWrite → copy → NoAccess → kernel → copy → ReadWrite → read")
    void pipeline_noAccessAsIntermediateStage() {
        int n = 16;

        GlobalReadWriteStaticBuffer rw = new GlobalReadWriteStaticBufferBuilder()
                .setup(IntDataProcessor.class, context, n);
        GlobalNoAccessStaticBuffer na = createBuffer(n);

        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        rw.writeSync(input);
        na.copyFrom(rw).waitForComplete();

        clKernel = buildKernel("triple_na", "", "buf[get_global_id(0)] *= 3;");
        na.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);

        na.copyTo(rw).waitForComplete();
        int[] result = (int[]) rw.readSync(n);

        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 3, result[i], "Mismatch at index " + i);
        }

        rw.destroy();
        na.destroy();
    }

    @Test
    @Order(1401)
    @DisplayName("Two sequential kernel passes on a no-access buffer produce correct cumulative result")
    void pipeline_twoKernelPasses() {
        int n = 8;
        GlobalNoAccessStaticBuffer buf = createBuffer(n);
        int[] input = new int[n];
        for (int i = 0; i < n; i++) input[i] = i + 1;
        plant(buf, input);

        // Pass 1: multiply by 2
        clKernel = buildKernel("pass1_na", "", "buf[get_global_id(0)] *= 2;");
        buf.bindToKernel(clKernel, 0);
        enqueueKernel(clKernel, n);
        buf.unbindKernel(clKernel);

        // Pass 2: add 10
        long kernel2;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String src2 = "__kernel void pass2_na(__global int* buf) { buf[get_global_id(0)] += 10; }";
            IntBuffer errBuf = stack.mallocInt(1);
            long prog2 = clCreateProgramWithSource(context.getContext(), src2, errBuf);
            clBuildProgram(prog2, device.getDeviceID(), "", null, 0);
            kernel2 = clCreateKernel(prog2, "pass2_na", errBuf);
            // prog2 intentionally leaked to clProgram for teardown
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

        int[] result = harvest(buf);
        for (int i = 0; i < n; i++) {
            assertEquals((i + 1) * 2 + 10, result[i], "Mismatch at index " + i);
        }
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 15. THREAD SAFETY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1500)
    @DisplayName("Concurrent copyFrom calls to a no-access buffer do not throw")
    void thread_concurrentCopyFrom() throws InterruptedException {
        int n = 8;
        GlobalNoAccessStaticBuffer dst = createBuffer(n);

        int threadCount = 4;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int val = i;
            futures.add(exec.submit(() -> {
                GlobalReadWriteStaticBuffer tmp = new GlobalReadWriteStaticBufferBuilder()
                        .setup(IntDataProcessor.class, context, n);
                int[] data = new int[n];
                Arrays.fill(data, val);
                tmp.writeSync(data);
                dst.copyFrom(tmp).waitForComplete();
                tmp.destroy();
            }));
        }

        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(), "A thread threw during concurrent copyFrom");
        }
        dst.destroy();
    }

    @Test
    @Order(1501)
    @DisplayName("Concurrent kernel bindings to a no-access buffer do not throw")
    void thread_concurrentKernelBindings() throws InterruptedException {
        GlobalNoAccessStaticBuffer buf = createBuffer(8);
        int n = 6;
        long[] kernels = new long[n];
        for (int i = 0; i < n; i++) {
            kernels[i] = buildKernel("kna_" + i, "", "buf[get_global_id(0)] += " + i + ";");
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
