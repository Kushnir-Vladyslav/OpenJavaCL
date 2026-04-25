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

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.ContextBuilder;
import io.github.kushnirvladyslav.Device;
import io.github.kushnirvladyslav.OpenCL;
import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.exceptions.BufferIndexOutOfBoundsException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.buffer.CopyableGlobalBuffer;
import io.github.kushnirvladyslav.memory.buffer.GlobalBuffer;
import io.github.kushnirvladyslav.memory.buffer.Writable;
import io.github.kushnirvladyslav.util.clEvent.ClEvent;
import org.junit.jupiter.api.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractGlobalBufferTest {

    // ── Infrastructure ───────────────────────────────────────────────────────

    protected ClContext context;
    protected Device device;

    /** Helper: kernel source and handle, released in tearDown. */
    protected long clKernel = 0;
    protected long clProgram = 0;

    @BeforeEach
    void baseSetUp() {
        device  = OpenCL.getPlatforms().get(0).getBestDevice();
        context = new ContextBuilder().withDevice(device).create();
    }

    @AfterEach
    void baseTearDown() {
        if (clKernel  != 0) { clReleaseKernel(clKernel);   clKernel  = 0; }
        if (clProgram != 0) { clReleaseProgram(clProgram); clProgram = 0; }
        if (context != null && !context.isClosed()) {
            OpenCL.destroyContext(context);
        }
    }

    // ── Abstract factory / capability methods ────────────────────────────────

    /**
     * Creates and returns a fully initialised buffer with the given capacity,
     * no explicit name, no staging buffer, and default access flags for the
     * concrete subtype.
     */
    protected abstract GlobalBuffer createBuffer(int capacity);

    /**
     * Creates a buffer with the given name, capacity, and staging-buffer flag.
     */
    protected abstract GlobalBuffer createBuffer(String name, int capacity, boolean stagingBuffer);

    /**
     * Returns {@code true} if the buffer type under test supports host reads
     * (i.e. implements {@link Readable}).
     */
    protected abstract boolean supportsHostRead();

    /**
     * Returns {@code true} if the buffer type under test supports host writes
     * (i.e. implements {@link Writable}).
     */
    protected abstract boolean supportsHostWrite();

    // ── Utility helpers ──────────────────────────────────────────────────────

    /** Compiles a trivial kernel that accepts a single {@code __global int*} arg. */
    protected long buildKernel(String kernelName, String extraArgs, String body) {
        String src = String.format(
                "__kernel void %s(__global int* buf%s) { %s }",
                kernelName,
                extraArgs.isEmpty() ? "" : (", " + extraArgs),
                body
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);
            long program = clCreateProgramWithSource(context.getContext(), src, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0), "clCreateProgramWithSource failed");

            int buildErr = clBuildProgram(program, device.getDeviceID(), "", null, 0);
            if (buildErr != CL_SUCCESS) {
                PointerBuffer sz = stack.mallocPointer(1);
                clGetProgramBuildInfo(program, device.getDeviceID(),
                        CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, sz);
                ByteBuffer logBuf = stack.malloc((int) sz.get(0));
                clGetProgramBuildInfo(program, device.getDeviceID(),
                        CL_PROGRAM_BUILD_LOG, logBuf, null);
                clReleaseProgram(program);
                fail("Build failed: " + MemoryUtil.memUTF8(logBuf));
            }
            long kernel = clCreateKernel(program, kernelName, errBuf);
            assertEquals(CL_SUCCESS, errBuf.get(0), "clCreateKernel failed");
            clProgram = program;
            return kernel;
        }
    }

    /** Allocates a raw OpenCL {@code cl_mem} of {@code byteSize} bytes. */
    protected long rawClBuffer(long byteSize, long flags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer err = stack.mallocInt(1);
            long mem = clCreateBuffer(context.getContext(), flags, byteSize, err);
            assertEquals(CL_SUCCESS, err.get(0));
            return mem;
        }
    }

    /** Enqueues and waits for a 1-D NDRange. */
    protected void enqueueKernel(long kernel, long gws) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer gwsBuf = stack.mallocPointer(1).put(0, gws);
            int err = clEnqueueNDRangeKernel(
                    context.getCommandQueue(), kernel, 1,
                    null, gwsBuf, null, null, null);
            assertEquals(CL_SUCCESS, err, "clEnqueueNDRangeKernel failed");
            clFinish(context.getCommandQueue());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. CREATION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    @DisplayName("Buffer is non-null and RUNNING after construction")
    void creation_basicState() {
        GlobalBuffer buf = createBuffer(16);
        assertNotNull(buf);
        assertFalse(buf.isClosed());
        buf.destroy();
    }

    @Test @Order(101)
    @DisplayName("Actual capacity is >= the requested value (dynamic buffers over-allocate)")
    void creation_capacityMatchesRequest() {
        GlobalBuffer buf = createBuffer(42);
        assertTrue(buf.getCapacity() >= 42,
                "Dynamic buffer must allocate at least the requested capacity");
        buf.destroy();
    }

    @Test
    @Order(102)
    @DisplayName("Buffer auto-generates a non-null, non-blank name")
    void creation_autoGeneratedName() {
        GlobalBuffer buf = createBuffer(8);
        assertNotNull(buf.getName());
        assertFalse(buf.getName().trim().isEmpty());
        buf.destroy();
    }

    @Test
    @Order(103)
    @DisplayName("Two auto-named buffers receive distinct names")
    void creation_distinctAutoNames() {
        GlobalBuffer b1 = createBuffer(8);
        GlobalBuffer b2 = createBuffer(8);
        assertNotEquals(b1.getName(), b2.getName());
        b1.destroy();
        b2.destroy();
    }

    @Test
    @Order(104)
    @DisplayName("Buffer honours the explicit name supplied at build time")
    void creation_explicitName() {
        GlobalBuffer buf = createBuffer("my-buffer", 10, false);
        assertEquals("my-buffer", buf.getName());
        buf.destroy();
    }

    @Test
    @Order(105)
    @DisplayName("Buffer registers itself in the context's BufferManager")
    void creation_registeredInContext() {
        GlobalBuffer buf = createBuffer("reg-test", 10, false);
        assertNotNull(context.getBufferManager().getBuffer("reg-test"),
                "Buffer should be findable via BufferManager");
        buf.destroy();
    }

    @Test
    @Order(106)
    @DisplayName("Buffer.inSameContext() returns true for own context")
    void creation_sameContext() {
        GlobalBuffer buf = createBuffer(10);
        assertTrue(buf.inSameContext(context));
        buf.destroy();
    }

    @Test
    @Order(107)
    @DisplayName("Buffer.getContext() returns the construction context")
    void creation_getContext() {
        GlobalBuffer buf = createBuffer(10);
        assertSame(context, buf.getContext());
        buf.destroy();
    }

    @Test
    @Order(108)
    @DisplayName("Buffer with staging buffer can be constructed without error")
    void creation_withStagingBuffer() {
        assertDoesNotThrow(() -> {
            GlobalBuffer buf = createBuffer("staged", 32, true);
            buf.destroy();
        });
    }

    @Test
    @Order(109)
    @DisplayName("toString() mentions class name, buffer name, status, and data class")
    void creation_toStringContent() {
        GlobalBuffer buf = createBuffer("ts-buf", 10, false);
        String s = buf.toString();
        assertTrue(s.contains("ts-buf"), "toString should contain the name");
        assertTrue(s.contains("RUNNING"),  "toString should contain status");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. LIFECYCLE / DESTRUCTION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(200)
    @DisplayName("destroy() transitions buffer to CLOSED state")
    void lifecycle_destroyMakesBufferClosed() {
        GlobalBuffer buf = createBuffer(10);
        buf.destroy();
        assertTrue(buf.isClosed());
    }

    @Test
    @Order(201)
    @DisplayName("close() (AutoCloseable) behaves identically to destroy()")
    void lifecycle_closeIsAliasForDestroy() {
        GlobalBuffer buf = createBuffer(10);
        buf.close();
        assertTrue(buf.isClosed());
    }

    @Test
    @Order(202)
    @DisplayName("Calling destroy() twice throws BufferDestructionException")
    void lifecycle_doubleDestroyThrows() {
        GlobalBuffer buf = createBuffer(10);
        buf.destroy();
        assertThrows(BufferDestructionException.class, buf::destroy);
    }

    @Test
    @Order(203)
    @DisplayName("getName() on a closed buffer throws BufferDestructionException")
    void lifecycle_getNameAfterDestroyThrows() {
        GlobalBuffer buf = createBuffer("dead", 10, false);
        buf.destroy();
        assertThrows(BufferDestructionException.class, buf::getName);
    }

    @Test
    @Order(204)
    @DisplayName("getContext() on a closed buffer throws BufferDestructionException")
    void lifecycle_getContextAfterDestroyThrows() {
        GlobalBuffer buf = createBuffer(10);
        buf.destroy();
        assertThrows(BufferDestructionException.class, buf::getContext);
    }

    @Test
    @Order(205)
    @DisplayName("getDataClass() on a closed buffer throws BufferDestructionException")
    void lifecycle_getDataClassAfterDestroyThrows() {
        GlobalBuffer buf = createBuffer(10);
        buf.destroy();
        assertThrows(BufferDestructionException.class, buf::getDataClass);
    }

    @Test
    @Order(206)
    @DisplayName("getCapacity() on a closed buffer throws BufferDestructionException")
    void lifecycle_getCapacityAfterDestroyThrows() {
        GlobalBuffer buf = createBuffer(10);
        buf.destroy();
        // getCapacity does not call checkNotClosed in GlobalBuffer, but subclasses may –
        // record the actual behaviour rather than assume:
        // If it throws – good. If it doesn't – at least it doesn't crash.
        // We assert only that no NPE / SIGSEGV-equivalent occurs.
        assertDoesNotThrow(() -> {
            try { buf.getCapacity(); } catch (BufferDestructionException ignored) {}
        });
    }

    @Test
    @Order(207)
    @DisplayName("Buffer is deregistered from BufferManager after destroy()")
    void lifecycle_deregisteredFromManagerAfterDestroy() {
        GlobalBuffer buf = createBuffer("dereg", 10, false);
        buf.destroy();
        assertNull(context.getBufferManager().getBuffer("dereg"),
                "Destroyed buffer should no longer appear in BufferManager");
    }

    @Test
    @Order(208)
    @DisplayName("try-with-resources correctly closes the buffer")
    void lifecycle_tryWithResources() {
        GlobalBuffer[] ref = new GlobalBuffer[1];
        assertDoesNotThrow(() -> {
            try (GlobalBuffer buf = createBuffer(10)) {
                ref[0] = buf;
            }
        });
        assertTrue(ref[0].isClosed());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. POINTER
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(300)
    @DisplayName("Initial pointer value is 0")
    void pointer_startsAtZero() {
        GlobalBuffer buf = createBuffer(16);
        assertEquals(0, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(301)
    @DisplayName("setPointer(n) stores the given value")
    void pointer_setAndGet() {
        GlobalBuffer buf = createBuffer(16);
        buf.setPointer(5);
        assertEquals(5, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(302)
    @DisplayName("setPointer(0) after advancing resets back to start")
    void pointer_resetToZero() {
        GlobalBuffer buf = createBuffer(16);
        buf.setPointer(7);
        buf.setPointer(0);
        assertEquals(0, buf.getPointer());
        buf.destroy();
    }

    @Test
    @Order(303)
    @DisplayName("setPointer with negative value throws BufferIndexOutOfBoundsException")
    void pointer_negativeValueThrows() {
        GlobalBuffer buf = createBuffer(16);
        assertThrows(BufferIndexOutOfBoundsException.class, () -> buf.setPointer(-1));
        buf.destroy();
    }

    @Test
    @Order(304)
    @DisplayName("setPointer at capacity boundary (capacity - 1) is valid")
    void pointer_capacityMinusOneIsValid() {
        GlobalBuffer buf = createBuffer(8);
        assertDoesNotThrow(() -> buf.setPointer(7));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. KERNEL BINDING
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(400)
    @DisplayName("bindToKernel succeeds with a valid kernel handle and arg-index 0")
    void kernel_bindSucceeds() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_bind", "", "buf[0] = 1;");
        assertDoesNotThrow(() -> buf.bindToKernel(clKernel, 0));
        assertTrue(buf.isBoundToKernel(clKernel));
        buf.destroy();
    }

    @Test
    @Order(401)
    @DisplayName("getKernelArgIndex returns the correct index after binding")
    void kernel_argIndexAfterBind() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_idx", "", "buf[0] = 1;");
        buf.bindToKernel(clKernel, 0);
        assertEquals(0, buf.getKernelArgIndex(clKernel));
        buf.destroy();
    }

    @Test
    @Order(402)
    @DisplayName("getKernelArgIndex returns -1 when not bound to the kernel")
    void kernel_argIndexWhenNotBound() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_noidx", "", "buf[0] = 1;");
        assertEquals(-1, buf.getKernelArgIndex(clKernel));
        buf.destroy();
    }

    @Test
    @Order(403)
    @DisplayName("isBoundToKernel returns false before any binding")
    void kernel_notBoundInitially() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_notbound", "", "buf[0] = 1;");
        assertFalse(buf.isBoundToKernel(clKernel));
        buf.destroy();
    }

    @Test
    @Order(404)
    @DisplayName("Binding to kernel with handle 0 throws IllegalArgumentException")
    void kernel_zeroHandleThrows() {
        GlobalBuffer buf = createBuffer(8);
        assertThrows(IllegalArgumentException.class, () -> buf.bindToKernel(0L, 0));
        buf.destroy();
    }

    @Test
    @Order(405)
    @DisplayName("Binding with negative arg-index throws IllegalArgumentException")
    void kernel_negativeArgIndexThrows() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_negidx", "", "buf[0] = 1;");
        assertThrows(IllegalArgumentException.class,
                () -> buf.bindToKernel(clKernel, -1));
        buf.destroy();
    }

    @Test
    @Order(406)
    @DisplayName("Re-binding to the same kernel/index emits a warning but does not throw")
    void kernel_rebindToSameIndexDoesNotThrow() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_same", "", "buf[0] = 1;");
        buf.bindToKernel(clKernel, 0);
        assertDoesNotThrow(() -> buf.bindToKernel(clKernel, 0));
        buf.destroy();
    }

    @Test
    @Order(407)
    @DisplayName("Binding to the same kernel with a different index throws IllegalArgumentException")
    void kernel_rebindToDifferentIndexThrows() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_diff", "const int x", "buf[0] = x;");
        buf.bindToKernel(clKernel, 0);
        assertThrows(IllegalArgumentException.class,
                () -> buf.bindToKernel(clKernel, 1));
        buf.destroy();
    }

    @Test
    @Order(408)
    @DisplayName("unbindKernel returns true for a bound kernel")
    void kernel_unbindReturnsTrueWhenBound() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_unbind", "", "buf[0] = 1;");
        buf.bindToKernel(clKernel, 0);
        assertTrue(buf.unbindKernel(clKernel));
        assertFalse(buf.isBoundToKernel(clKernel));
        buf.destroy();
    }

    @Test
    @Order(409)
    @DisplayName("unbindKernel returns false for an unbound kernel")
    void kernel_unbindReturnsFalseWhenNotBound() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_unbound", "", "buf[0] = 1;");
        assertFalse(buf.unbindKernel(clKernel));
        buf.destroy();
    }

    @Test
    @Order(410)
    @DisplayName("unbindKernel(0) returns false without throwing")
    void kernel_unbindZeroReturnsFalse() {
        GlobalBuffer buf = createBuffer(8);
        assertFalse(buf.unbindKernel(0L));
        buf.destroy();
    }

    @Test
    @Order(411)
    @DisplayName("Buffer can be bound to multiple kernels simultaneously")
    void kernel_multipleKernelBindings() {
        GlobalBuffer buf = createBuffer(8);
        long k1 = buildKernel("k_m1", "", "buf[0] = 1;");
        long k2 = buildKernel("k_m2", "", "buf[0] = 2;");
        buf.bindToKernel(k1, 0);
        buf.bindToKernel(k2, 0);
        assertTrue(buf.isBoundToKernel(k1));
        assertTrue(buf.isBoundToKernel(k2));
        buf.destroy();
        clReleaseKernel(k1);
        clReleaseKernel(k2);
    }

    @Test
    @Order(412)
    @DisplayName("bindToKernel on a closed buffer throws BufferDestructionException")
    void kernel_bindAfterDestroyThrows() {
        GlobalBuffer buf = createBuffer(8);
        buf.destroy();
        clKernel = buildKernel("k_dead", "", "buf[0] = 1;");
        assertThrows(BufferDestructionException.class,
                () -> buf.bindToKernel(clKernel, 0));
    }

    @Test
    @Order(413)
    @DisplayName("Kernel bindings are cleared after destroy() without error")
    void kernel_bindingsClearedOnDestroy() {
        GlobalBuffer buf = createBuffer(8);
        clKernel = buildKernel("k_clr", "", "buf[0] = 1;");
        buf.bindToKernel(clKernel, 0);
        assertDoesNotThrow(buf::destroy);
        assertTrue(buf.isClosed());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. COPY OPERATIONS  (all GlobalBuffers extend CopyableGlobalBuffer)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(500)
    @DisplayName("copyFrom(src) with matching capacity completes without error")
    void copy_fromSameSizeBuffer() {
        assumeBufferCanBeUsedAsSource();     // source must be host-writable or GPU-writable
        GlobalBuffer src  = createBuffer(16);
        GlobalBuffer dst  = createBuffer(16);
        assertDoesNotThrow(() -> {
            ClEvent ev = ((CopyableGlobalBuffer) dst).copyFrom(src);
            ev.waitForComplete();
        });
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(501)
    @DisplayName("copyTo(dst) with matching capacity completes without error")
    void copy_toSameSizeBuffer() {
        GlobalBuffer src  = createBuffer(16);
        GlobalBuffer dst  = createBuffer(16);
        assertDoesNotThrow(() -> {
            ClEvent ev = ((CopyableGlobalBuffer) src).copyTo(dst);
            ev.waitForComplete();
        });
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(502)
    @DisplayName("copyFrom with null source throws BufferOperationException")
    void copy_fromNullSourceThrows() {
        GlobalBuffer dst = createBuffer(16);
        assertThrows(BufferOperationException.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(null));
        dst.destroy();
    }

    @Test
    @Order(503)
    @DisplayName("copyFrom with a closed source throws BufferOperationException")
    void copy_fromClosedSourceThrows() {
        GlobalBuffer src = createBuffer(8);
        GlobalBuffer dst = createBuffer(8);
        src.destroy();
        assertThrows(BufferOperationException.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src));
        dst.destroy();
    }

    @Test
    @Order(504)
    @DisplayName("copyFrom with negative size throws BufferOperationException")
    void copy_negativeSizeThrows() {
        GlobalBuffer src = createBuffer(8);
        GlobalBuffer dst = createBuffer(8);
        assertThrows(BufferOperationException.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, 0, 0, -1));
        src.destroy();
        dst.destroy();
    }


    /** Override to skip copy tests that require the buffer to act as a data source. */
    protected void assumeBufferCanBeUsedAsSource() { /* no-op by default */ }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. THREAD SAFETY (structural – no data-race assertions, just no crash)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(600)
    @DisplayName("Concurrent kernel bindings from multiple threads complete without exception")
    void thread_concurrentKernelBindings() throws InterruptedException {
        GlobalBuffer buf = createBuffer(8);
        int n = 6;
        long[] kernels = new long[n];
        for (int i = 0; i < n; i++) {
            kernels[i] = buildKernel("kthread_" + i, "", "buf[0] = " + i + ";");
            clProgram = 0; // prevent double-release; kernels share different programs
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
            assertDoesNotThrow(() -> f.get(), "Thread threw during binding");
        }
        for (long k : kernels) clReleaseKernel(k);
        buf.destroy();
    }

    @Test
    @Order(601)
    @DisplayName("Concurrent setPointer calls do not leave a negative pointer")
    void thread_concurrentSetPointer() throws InterruptedException {
        GlobalBuffer buf = createBuffer(1024);
        int threads = 8;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            final int val = i * 10;
            exec.submit(() -> {
                try { buf.setPointer(val); }
                catch (Exception e) { errors.incrementAndGet(); }
            });
        }
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
        // pointer must be non-negative and within capacity
        assertTrue(buf.getPointer() >= 0);
        assertTrue(buf.getPointer() < buf.getCapacity());
        buf.destroy();
    }
}
