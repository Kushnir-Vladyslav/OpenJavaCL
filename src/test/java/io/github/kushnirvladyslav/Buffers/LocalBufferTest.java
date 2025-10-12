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

package io.github.kushnirvladyslav.Buffers;

import io.github.kushnirvladyslav.memory.data.typical.FloatData;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.LocalBuffer;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.LocalBufferBuilder;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.*;
import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.memory.data.typical.IntData;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBuffer;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBufferBuilder;
import org.junit.jupiter.api.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.*;


public class LocalBufferTest {
    private OpenClContext context;
    private Device device = OpenCL.getPlatforms().get(0).getBestDevice();
    private LocalBufferBuilder builder;

    private long clProgram;
    private long clKernel;
    private long clMemory;

    @BeforeEach
    void setUp() {
        context = new ContextBuilder()
                .withDevice(device)
                .create();

        builder = new LocalBufferBuilder();
        clProgram = 0;
        clKernel = 0;
        clMemory = 0;
    }

    @AfterEach
    void tearDown() {
        if (clMemory != 0) {
            clReleaseMemObject(clMemory);
        }
        if (clKernel != 0) {
            clReleaseKernel(clKernel);
        }
        if (clProgram != 0) {
            clReleaseProgram(clProgram);
        }
        if (context != null && !context.isClosed()) {
            OpenCL.destroyContext(context);
        }
    }

    // ========== Допоміжні методи ==========

    private long createKernelFromSource(String source, String kernelName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);

            long program = clCreateProgramWithSource(
                    context.getContext(),
                    source,
                    errBuf
            );

            if (!OpenCLErrorUtils.isSuccess(errBuf.get(0))) {
                throw new RuntimeException("Failed to create program: " +
                        OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
            }

            int buildStatus = clBuildProgram(
                    program,
                    device.getDeviceID(),
                    "",
                    null,
                    0
            );

            if (!OpenCLErrorUtils.isSuccess(buildStatus)) {
                PointerBuffer sizeRet = stack.mallocPointer(1);
                clGetProgramBuildInfo(program, device.getDeviceID(),
                        CL_PROGRAM_BUILD_LOG, (ByteBuffer) null, sizeRet);

                ByteBuffer buffer = stack.malloc((int) sizeRet.get(0));
                clGetProgramBuildInfo(program, device.getDeviceID(),
                        CL_PROGRAM_BUILD_LOG, buffer, null);

                String log = MemoryUtil.memUTF8(buffer);
                clReleaseProgram(program);
                throw new RuntimeException("Failed to build program: " + log);
            }

            long kernel = clCreateKernel(program, kernelName, errBuf);

            if (!OpenCLErrorUtils.isSuccess(errBuf.get(0))) {
                clReleaseProgram(program);
                throw new RuntimeException("Failed to create kernel: " +
                        OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
            }

            clProgram = program;
            return kernel;

        } catch (Exception e) {
            throw new RuntimeException("Error creating kernel", e);
        }
    }

    private long createBuffer(int size, long flags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);

            long buffer = clCreateBuffer(
                    context.getContext(),
                    flags,
                    size,
                    errBuf
            );

            if (!OpenCLErrorUtils.isSuccess(errBuf.get(0))) {
                throw new RuntimeException("Failed to create buffer: " +
                        OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
            }

            return buffer;
        }
    }

    private void executeKernel(long kernel, long globalWorkSize, long localWorkSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long queue = context.getCommandQueue();

            PointerBuffer globalSize = stack.mallocPointer(1);
            globalSize.put(0, globalWorkSize);

            PointerBuffer localSize = null;
            if (localWorkSize > 0) {
                localSize = stack.mallocPointer(1);
                localSize.put(0, localWorkSize);
            }

            int err = clEnqueueNDRangeKernel(
                    queue,
                    kernel,
                    1,
                    null,
                    globalSize,
                    localSize,
                    null,
                    null
            );

            if (err != CL_SUCCESS) {
                throw new RuntimeException("Failed to enqueue kernel: " +
                        OpenCLErrorUtils.getCLErrorString(err));
            }

            clFinish(queue);
        }
    }

    // ========== Constructor and Setup Tests ==========

    @Test
    @DisplayName("Should create LocalBuffer with valid parameters")
    void testCreateLocalBuffer() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);

        assertNotNull(buffer);
        assertFalse(buffer.isClosed());
        assertEquals(IntData.class, buffer.getDataClass());
        assertTrue(buffer.inSameContext(context));
        assertEquals(256, buffer.getSize());

        buffer.destroy();
    }

    @Test
    @DisplayName("Should create LocalBuffer with custom name")
    void testCreateLocalBufferWithName() {
        String bufferName = "LocalCache";
        LocalBuffer buffer = builder.setup(bufferName, IntData.class, context, 128);

        assertNotNull(buffer);
        assertEquals(bufferName, buffer.getName());
        assertEquals(128, buffer.getSize());

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when size is zero")
    void testCreateBufferWithZeroSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntData.class, context, 0);
        });
    }

    @Test
    @DisplayName("Should throw exception when size is negative")
    void testCreateBufferWithNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntData.class, context, -10);
        });
    }

    @Test
    @DisplayName("Should throw exception when context is null")
    void testCreateBufferWithNullContext() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntData.class, null, 256);
        });
    }

    @Test
    @DisplayName("Should throw exception when data class is null")
    void testCreateBufferWithNullDataClass() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(null, context, 256);
        });
    }

    @Test
    @DisplayName("Should throw exception when context is closed")
    void testCreateBufferWithClosedContext() {
        OpenCL.destroyContext(context);

        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntData.class, context, 256);
        });
    }

    // ========== Resize Tests ==========

    @Test
    @DisplayName("Should resize buffer successfully")
    void testResizeBuffer() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 128);

        assertEquals(128, buffer.getSize());

        buffer.resize(256);
        assertEquals(256, buffer.getSize());

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when resizing to zero")
    void testResizeToZero() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 128);

        assertThrows(IllegalArgumentException.class, () -> {
            buffer.resize(0);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when resizing to negative")
    void testResizeToNegative() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 128);

        assertThrows(IllegalArgumentException.class, () -> {
            buffer.resize(-5);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when resizing closed buffer")
    void testResizeClosedBuffer() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 128);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, () -> {
            buffer.resize(256);
        });
    }

    // ========== Kernel Binding Tests ==========

    @Test
    @DisplayName("Should bind buffer to kernel successfully")
    void testBindToKernel() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);

        String kernelSource =
                "__kernel void test_kernel(__local int* shared) {\n" +
                        "    // Dummy kernel\n" +
                        "}\n";

        clKernel = createKernelFromSource(kernelSource, "test_kernel");

        assertDoesNotThrow(() -> buffer.bindToKernel(clKernel, 0));
        assertTrue(buffer.isBoundToKernel(clKernel));
        assertEquals(0, buffer.getKernelArgIndex(clKernel));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should bind buffer to multiple kernels")
    void testBindToMultipleKernels() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);

        String kernelSource1 =
                "__kernel void kernel_one(__local int* shared) {}\n";
        String kernelSource2 =
                "__kernel void kernel_two(__local int* shared) {}\n";

        long kernel1 = createKernelFromSource(kernelSource1, "kernel_one");
        long kernel2 = createKernelFromSource(kernelSource2, "kernel_two");

        assertDoesNotThrow(() -> {
            buffer.bindToKernel(kernel1, 0);
            buffer.bindToKernel(kernel2, 0);
        });

        assertTrue(buffer.isBoundToKernel(kernel1));
        assertTrue(buffer.isBoundToKernel(kernel2));

        clReleaseKernel(kernel1);
        clReleaseKernel(kernel2);
        buffer.destroy();
    }

    @Test
    @DisplayName("Should unbind kernel successfully")
    void testUnbindKernel() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);

        String kernelSource =
                "__kernel void test_kernel(__local int* shared) {}\n";

        clKernel = createKernelFromSource(kernelSource, "test_kernel");

        buffer.bindToKernel(clKernel, 0);
        assertTrue(buffer.isBoundToKernel(clKernel));

        boolean result = buffer.unbindKernel(clKernel);
        assertTrue(result);
        assertFalse(buffer.isBoundToKernel(clKernel));

        buffer.destroy();
    }

    // ========== Integration Tests with Kernels ==========

    @Test
    @DisplayName("Should use local memory for prefix sum calculation")
    void testLocalMemoryPrefixSum() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void prefix_sum(__global int* input, __global int* output, __local int* temp) {\n" +
                            "    int lid = get_local_id(0);\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    \n" +
                            "    temp[lid] = input[gid];\n" +
                            "    barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    \n" +
                            "    for (int stride = 1; stride < get_local_size(0); stride *= 2) {\n" +
                            "        int val = 0;\n" +
                            "        if (lid >= stride) {\n" +
                            "            val = temp[lid - stride];\n" +
                            "        }\n" +
                            "        barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "        if (lid >= stride) {\n" +
                            "            temp[lid] += val;\n" +
                            "        }\n" +
                            "        barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    }\n" +
                            "    \n" +
                            "    output[gid] = temp[lid];\n" +
                            "}\n";

            int localSize = 64;
            int arraySize = 64;

            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = 1; // Усі одиниці, prefix sum дасть 1,2,3,4,...
            }

            long inputBuffer = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_ONLY);
            long outputBuffer = createBuffer(arraySize * Integer.BYTES, CL_MEM_WRITE_ONLY);

            IntBuffer hostInput = BufferUtils.createIntBuffer(arraySize);
            hostInput.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), inputBuffer, true, 0, hostInput, null, null);

            clKernel = createKernelFromSource(kernelSource, "prefix_sum");
            LocalBuffer localBuf = builder.setup("temp", IntData.class, context, localSize);

            PointerBuffer inputPtr = stack.mallocPointer(1);
            inputPtr.put(inputBuffer);
            PointerBuffer outputPtr = stack.mallocPointer(1);
            outputPtr.put(outputBuffer);

            clSetKernelArg(clKernel, 0, inputPtr.rewind());
            clSetKernelArg(clKernel, 1, outputPtr.rewind());
            localBuf.bindToKernel(clKernel, 2);

            executeKernel(clKernel, arraySize, localSize);

            IntBuffer hostOutput = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), outputBuffer, true, 0, hostOutput, null, null);

            for (int i = 0; i < arraySize; i++) {
                int expected = i + 1;
                int actual = hostOutput.get(i);
                assertEquals(expected, actual,
                        String.format("Mismatch at index %d: expected %d, got %d", i, expected, actual));
            }

            clReleaseMemObject(inputBuffer);
            clReleaseMemObject(outputBuffer);
            localBuf.destroy();
        }
    }

    @Test
    @DisplayName("Should use local memory as cache for array reduction")
    void testLocalMemoryReduction() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void reduce_sum(__global int* input, __global int* output, __local int* cache, const int n) {\n" +
                            "    int lid = get_local_id(0);\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    int localSize = get_local_size(0);\n" +
                            "    \n" +
                            "    cache[lid] = (gid < n) ? input[gid] : 0;\n" +
                            "    barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    \n" +
                            "    for (int stride = localSize / 2; stride > 0; stride /= 2) {\n" +
                            "        if (lid < stride) {\n" +
                            "            cache[lid] += cache[lid + stride];\n" +
                            "        }\n" +
                            "        barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    }\n" +
                            "    \n" +
                            "    if (lid == 0) {\n" +
                            "        output[get_group_id(0)] = cache[0];\n" +
                            "    }\n" +
                            "}\n";

            int localSize = 64;
            int arraySize = 256;
            int numGroups = (arraySize + localSize - 1) / localSize;

            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = 1; // Сума має бути 256
            }

            long inputBuffer = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_ONLY);
            long outputBuffer = createBuffer(numGroups * Integer.BYTES, CL_MEM_WRITE_ONLY);

            IntBuffer hostInput = BufferUtils.createIntBuffer(arraySize);
            hostInput.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), inputBuffer, true, 0, hostInput, null, null);

            clKernel = createKernelFromSource(kernelSource, "reduce_sum");
            LocalBuffer localBuf = builder.setup("cache", IntData.class, context, localSize);
            ParameterBuffer paramBuf = new ParameterBufferBuilder().setup("n", IntData.class, context);
            paramBuf.write(arraySize);

            PointerBuffer inputPtr = stack.mallocPointer(1);
            inputPtr.put(inputBuffer);
            PointerBuffer outputPtr = stack.mallocPointer(1);
            outputPtr.put(outputBuffer);

            clSetKernelArg(clKernel, 0, inputPtr.rewind());
            clSetKernelArg(clKernel, 1, outputPtr.rewind());
            localBuf.bindToKernel(clKernel, 2);
            paramBuf.bindToKernel(clKernel, 3);

            executeKernel(clKernel, arraySize, localSize);

            IntBuffer hostOutput = BufferUtils.createIntBuffer(numGroups);
            clEnqueueReadBuffer(context.getCommandQueue(), outputBuffer, true, 0, hostOutput, null, null);

            int totalSum = 0;
            for (int i = 0; i < numGroups; i++) {
                totalSum += hostOutput.get(i);
            }

            assertEquals(arraySize, totalSum, "Total sum should equal array size");

            clReleaseMemObject(inputBuffer);
            clReleaseMemObject(outputBuffer);
            localBuf.destroy();
            paramBuf.destroy();
        }
    }

    @Test
    @DisplayName("Should handle resize and update kernel binding")
    void testResizeWithKernelBinding() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void fill_local(__global int* output, __local int* temp) {\n" +
                            "    int lid = get_local_id(0);\n" +
                            "    temp[lid] = lid;\n" +
                            "    barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    output[get_global_id(0)] = temp[lid];\n" +
                            "}\n";

            int initialSize = 32;
            int resizedSize = 64;

            LocalBuffer localBuf = builder.setup("temp", IntData.class, context, initialSize);

            long outputBuffer = createBuffer(resizedSize * Integer.BYTES, CL_MEM_WRITE_ONLY);

            clKernel = createKernelFromSource(kernelSource, "fill_local");

            PointerBuffer outputPtr = stack.mallocPointer(1);
            outputPtr.put(outputBuffer);
            clSetKernelArg(clKernel, 0, outputPtr.rewind());
            localBuf.bindToKernel(clKernel, 1);

            // Resize і перевірка що kernel автоматично оновився
            localBuf.resize(resizedSize);
            assertEquals(resizedSize, localBuf.getSize());

            executeKernel(clKernel, resizedSize, resizedSize);

            IntBuffer hostOutput = BufferUtils.createIntBuffer(resizedSize);
            clEnqueueReadBuffer(context.getCommandQueue(), outputBuffer, true, 0, hostOutput, null, null);

            for (int i = 0; i < resizedSize; i++) {
                assertEquals(i, hostOutput.get(i),
                        String.format("Expected %d at index %d, got %d", i, i, hostOutput.get(i)));
            }

            clReleaseMemObject(outputBuffer);
            localBuf.destroy();
        }
    }

    @Test
    @DisplayName("Should work with float local memory")
    void testFloatLocalMemory() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void avg_filter(__global float* input, __global float* output, __local float* cache) {\n" +
                            "    int lid = get_local_id(0);\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    \n" +
                            "    cache[lid] = input[gid];\n" +
                            "    barrier(CLK_LOCAL_MEM_FENCE);\n" +
                            "    \n" +
                            "    float sum = cache[lid];\n" +
                            "    if (lid > 0) sum += cache[lid - 1];\n" +
                            "    if (lid < get_local_size(0) - 1) sum += cache[lid + 1];\n" +
                            "    \n" +
                            "    output[gid] = sum / 3.0f;\n" +
                            "}\n";

            int localSize = 64;
            int arraySize = 64;

            float[] inputData = new float[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = (float) i;
            }

            long inputBuffer = createBuffer(arraySize * Float.BYTES, CL_MEM_READ_ONLY);
            long outputBuffer = createBuffer(arraySize * Float.BYTES, CL_MEM_WRITE_ONLY);

            FloatBuffer hostInput = BufferUtils.createFloatBuffer(arraySize);
            hostInput.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), inputBuffer, true, 0, hostInput, null, null);

            clKernel = createKernelFromSource(kernelSource, "avg_filter");
            LocalBuffer localBuf = builder.setup("cache", FloatData.class, context, localSize);

            PointerBuffer inputPtr = stack.mallocPointer(1);
            inputPtr.put(inputBuffer);
            PointerBuffer outputPtr = stack.mallocPointer(1);
            outputPtr.put(outputBuffer);

            clSetKernelArg(clKernel, 0, inputPtr.rewind());
            clSetKernelArg(clKernel, 1, outputPtr.rewind());
            localBuf.bindToKernel(clKernel, 2);

            executeKernel(clKernel, arraySize, localSize);

            FloatBuffer hostOutput = BufferUtils.createFloatBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), outputBuffer, true, 0, hostOutput, null, null);

            // Перевіряємо середні елементи (не краї)
            for (int i = 1; i < arraySize - 1; i++) {
                float expected = (inputData[i - 1] + inputData[i] + inputData[i + 1]) / 3.0f;
                float actual = hostOutput.get(i);
                assertEquals(expected, actual, 0.001f,
                        String.format("Mismatch at index %d", i));
            }

            clReleaseMemObject(inputBuffer);
            clReleaseMemObject(outputBuffer);
            localBuf.destroy();
        }
    }

    // ========== Destruction Tests ==========

    @Test
    @DisplayName("Should destroy buffer successfully")
    void testDestroyBuffer() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);

        assertDoesNotThrow(() -> buffer.destroy());
        assertTrue(buffer.isClosed());
    }

    @Test
    @DisplayName("Should throw exception when destroying already closed buffer")
    void testDestroyAlreadyClosedBuffer() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, buffer::destroy);
    }

    @Test
    @DisplayName("Should not allow operations after destroy")
    void testOperationsAfterDestroy() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 256);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, buffer::getSize);
        assertThrows(BufferDestructionException.class, () -> buffer.resize(512));
        assertThrows(BufferDestructionException.class, buffer::getName);
    }

    // ========== State Tests ==========

    @Test
    @DisplayName("Should return correct toString representation")
    void testToString() {
        LocalBuffer buffer = builder.setup("MyLocalBuffer", IntData.class, context, 256);

        String result = buffer.toString();
        assertTrue(result.contains("LocalBuffer"));
        assertTrue(result.contains("MyLocalBuffer"));
        assertTrue(result.contains("IntData"));

        buffer.destroy();
    }


    @Test
    @DisplayName("Should handle large local memory allocation")
    void testLargeLocalMemoryAllocation() {
        LocalBuffer buffer = builder.setup(IntData.class, context, 8192);

        assertEquals(8192, buffer.getSize());

        buffer.destroy();
    }
}
