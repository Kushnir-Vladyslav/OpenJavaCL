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

import io.github.kushnirvladyslav.*;
import io.github.kushnirvladyslav.memory.data.typical.FloatData;
import io.github.kushnirvladyslav.memory.data.typical.IntData;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBuffer;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBufferBuilder;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
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

public class ParameterBufferKernelTest {
    private OpenClContext context;
    private Device device = OpenCL.getPlatforms().get(0).getBestDevice();;
    private ParameterBufferBuilder builder;

    private long clProgram;
    private long clKernel;
    private long clMemory;

    @BeforeEach
    void setUp() {
        context = new ContextBuilder()
                .withDevice(device)
                .create();

        builder = new ParameterBufferBuilder();
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
                throw new RuntimeException("Failed to create program: " + OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
            }

            int buildStatus = clBuildProgram(
                    program,
                    device.getDeviceID(),
                    "",
                    null,
                    0
            );

            if (!OpenCLErrorUtils.isSuccess(CL_SUCCESS)) {
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

            if (!OpenCLErrorUtils.isSuccess(CL_SUCCESS)) {
                clReleaseProgram(program);
                throw new RuntimeException("Failed to create kernel: " + OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
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
                throw new RuntimeException("Failed to create buffer: " + OpenCLErrorUtils.getCLErrorString(errBuf.get(0)));
            }

            return buffer;
        }
    }

    private void executeKernel(long kernel, long globalWorkSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long queue = context.getCommandQueue();

            PointerBuffer globalSize = stack.mallocPointer(1);
            globalSize.put(0, globalWorkSize);

            int err = clEnqueueNDRangeKernel(
                    queue,
                    kernel,
                    1,
                    null,
                    globalSize,
                    null,
                    null,
                    null
            );

            if (err != CL_SUCCESS) {
                throw new RuntimeException("Failed to enqueue kernel: " + err);
            }

            clFinish(queue);
        }
    }

    // ========== Тести з додаванням ==========

    @Test
    @DisplayName("Should add integer parameter to all array elements")
    void testAddParameterToArray() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void add_value(__global int* data, const int value) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] += value;\n" +
                            "}\n";

            int arraySize = 100;
            int addValue = 42;
            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = i;
            }

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer inputBuffer = BufferUtils.createIntBuffer(arraySize);
            inputBuffer.put(inputData).flip();

            int err = clEnqueueWriteBuffer(
                    context.getCommandQueue(),
                    clMemory,
                    true,
                    0,
                    inputBuffer,
                    null,
                    null
            );
            assertEquals(CL_SUCCESS, err, "Failed to write buffer to device");

            clKernel = createKernelFromSource(kernelSource, "add_value");
            ParameterBuffer paramBuffer = builder.setup("addValue", IntData.class, context);
            paramBuffer.write(addValue);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            err = clEnqueueReadBuffer(
                    context.getCommandQueue(),
                    clMemory,
                    true,
                    0,
                    outputBuffer,
                    null,
                    null
            );
            assertEquals(CL_SUCCESS, err, "Failed to read buffer from device");

            for (int i = 0; i < arraySize; i++) {
                int expected = i + addValue;
                int actual = outputBuffer.get(i);
                assertEquals(expected, actual,
                        String.format("Mismatch at index %d: expected %d, got %d", i, expected, actual));
            }

            paramBuffer.destroy();
        }
    }

    @Test
    @DisplayName("Should multiply array elements by float parameter")
    void testMultiplyByFloatParameter() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void multiply_value(__global float* data, const float multiplier) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] *= multiplier;\n" +
                            "}\n";

            int arraySize = 50;
            float multiplier = 2.5f;
            float[] inputData = new float[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = (float) i;
            }

            clMemory = createBuffer(arraySize * Float.BYTES, CL_MEM_READ_WRITE);

            FloatBuffer inputBuffer = BufferUtils.createFloatBuffer(arraySize);
            inputBuffer.put(inputData).flip();

            clEnqueueWriteBuffer(
                    context.getCommandQueue(),
                    clMemory,
                    true,
                    0,
                    inputBuffer,
                    null,
                    null
            );

            clKernel = createKernelFromSource(kernelSource, "multiply_value");
            ParameterBuffer paramBuffer = builder.setup("multiplier", FloatData.class, context);


            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            paramBuffer.write(multiplier);

            executeKernel(clKernel, arraySize);

            FloatBuffer outputBuffer = BufferUtils.createFloatBuffer(arraySize);
            clEnqueueReadBuffer(
                    context.getCommandQueue(),
                    clMemory,
                    true,
                    0,
                    outputBuffer,
                    null,
                    null
            );

            for (int i = 0; i < arraySize; i++) {
                float expected = i * multiplier;
                float actual = outputBuffer.get(i);
                assertEquals(expected, actual, 0.0001f,
                        String.format("Mismatch at index %d: expected %.4f, got %.4f", i, expected, actual));
            }

            paramBuffer.destroy();
        }
    }

    // ========== Тести зміни параметра ==========

    @Test
    @DisplayName("Should handle parameter value change between kernel executions")
    void testChangeParameterBetweenExecutions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void add_value(__global int* data, const int value) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] += value;\n" +
                            "}\n";

            int arraySize = 10;
            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = 0;
            }

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer buffer = BufferUtils.createIntBuffer(arraySize);
            buffer.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, buffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "add_value");
            ParameterBuffer paramBuffer = builder.setup("value", IntData.class, context);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            paramBuffer.write(10);
            executeKernel(clKernel, arraySize);

            paramBuffer.write(5);
            executeKernel(clKernel, arraySize);

            paramBuffer.write(3);
            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < arraySize; i++) {
                assertEquals(18, outputBuffer.get(i),
                        String.format("Expected 18 at index %d, got %d", i, outputBuffer.get(i)));
            }

            paramBuffer.destroy();
        }
    }

    // ========== Тести з множинними параметрами ==========

    @Test
    @DisplayName("Should handle multiple parameter buffers in one kernel")
    void testMultipleParameters() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void compute(__global int* data, const int add, const int multiply) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] = (data[gid] + add) * multiply;\n" +
                            "}\n";

            int arraySize = 20;
            int addValue = 5;
            int multiplyValue = 3;

            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = i;
            }

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer buffer = BufferUtils.createIntBuffer(arraySize);
            buffer.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, buffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "compute");

            ParameterBuffer addBuffer = builder.setup("add", IntData.class, context);
            addBuffer.write(addValue);

            ParameterBuffer multiplyBuffer = builder.setup("multiply", IntData.class, context);
            multiplyBuffer.write(multiplyValue);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            addBuffer.bindToKernel(clKernel, 1);
            multiplyBuffer.bindToKernel(clKernel, 2);

            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < arraySize; i++) {
                int expected = (i + addValue) * multiplyValue;
                int actual = outputBuffer.get(i);
                assertEquals(expected, actual,
                        String.format("Mismatch at index %d: expected %d, got %d", i, expected, actual));
            }

            addBuffer.destroy();
            multiplyBuffer.destroy();
        }
    }

    // ========== Тести з різними типами даних ==========

    @Test
    @DisplayName("Should work with negative integer values")
    void testNegativeIntegerParameter() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void add_value(__global int* data, const int value) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] += value;\n" +
                            "}\n";

            int arraySize = 15;
            int addValue = -10;

            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = 50;
            }

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer buffer = BufferUtils.createIntBuffer(arraySize);
            buffer.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, buffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "add_value");
            ParameterBuffer paramBuffer = builder.setup("value", IntData.class, context);
            paramBuffer.write(addValue);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < arraySize; i++) {
                assertEquals(40, outputBuffer.get(i),
                        String.format("Expected 40 at index %d, got %d", i, outputBuffer.get(i)));
            }

            paramBuffer.destroy();
        }
    }

    @Test
    @DisplayName("Should work with zero parameter value")
    void testZeroParameter() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void multiply_value(__global int* data, const int multiplier) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] *= multiplier;\n" +
                            "}\n";

            int arraySize = 10;
            int[] inputData = new int[arraySize];
            for (int i = 0; i < arraySize; i++) {
                inputData[i] = i + 1;
            }

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer buffer = BufferUtils.createIntBuffer(arraySize);
            buffer.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, buffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "multiply_value");
            ParameterBuffer paramBuffer = builder.setup("multiplier", IntData.class, context);
            paramBuffer.write(0);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);

            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < arraySize; i++) {
                assertEquals(0, outputBuffer.get(i),
                        String.format("Expected 0 at index %d, got %d", i, outputBuffer.get(i)));
            }

            paramBuffer.destroy();
        }
    }

    // ========== Тести з великими масивами ==========

    @Test
    @DisplayName("Should handle large arrays with parameter")
    void testLargeArray() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void add_value(__global int* data, const int value) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] += value;\n" +
                            "}\n";

            int arraySize = 1_000_000;
            int addValue = 7;

            IntBuffer inputBuffer = BufferUtils.createIntBuffer(arraySize);
            for (int i = 0; i < arraySize; i++) {
                inputBuffer.put(i % 100);
            }
            inputBuffer.flip();

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, inputBuffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "add_value");
            ParameterBuffer paramBuffer = builder.setup("value", IntData.class, context);
            paramBuffer.write(addValue);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());
            paramBuffer.bindToKernel(clKernel, 1);

            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < 100; i++) {
                int expected = (i % 100) + addValue;
                assertEquals(expected, outputBuffer.get(i),
                        String.format("Mismatch at start index %d", i));
            }

            for (int i = arraySize - 100; i < arraySize; i++) {
                int expected = (i % 100) + addValue;
                assertEquals(expected, outputBuffer.get(i),
                        String.format("Mismatch at end index %d", i));
            }

            paramBuffer.destroy();
        }
    }

    // ========== Тест unbind/rebind ==========

    @Test
    @DisplayName("Should handle unbind and rebind parameter buffer")
    void testUnbindRebindParameter() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String kernelSource =
                    "__kernel void add_value(__global int* data, const int value) {\n" +
                            "    int gid = get_global_id(0);\n" +
                            "    data[gid] += value;\n" +
                            "}\n";

            int arraySize = 10;
            int[] inputData = new int[arraySize];

            clMemory = createBuffer(arraySize * Integer.BYTES, CL_MEM_READ_WRITE);

            IntBuffer buffer = BufferUtils.createIntBuffer(arraySize);
            buffer.put(inputData).flip();
            clEnqueueWriteBuffer(context.getCommandQueue(), clMemory, true, 0, buffer, null, null);

            clKernel = createKernelFromSource(kernelSource, "add_value");
            ParameterBuffer paramBuffer = builder.setup("value", IntData.class, context);

            PointerBuffer clMemoryPointer = stack.mallocPointer(1);
            clMemoryPointer.put(clMemory);
            clSetKernelArg(clKernel, 0, clMemoryPointer.rewind());

            paramBuffer.write(5);
            paramBuffer.bindToKernel(clKernel, 1);
            executeKernel(clKernel, arraySize);

            assertTrue(paramBuffer.unbindKernel(clKernel));
            assertFalse(paramBuffer.isBoundToKernel(clKernel));

            paramBuffer.write(10);
            paramBuffer.bindToKernel(clKernel, 1);
            executeKernel(clKernel, arraySize);

            IntBuffer outputBuffer = BufferUtils.createIntBuffer(arraySize);
            clEnqueueReadBuffer(context.getCommandQueue(), clMemory, true, 0, outputBuffer, null, null);

            for (int i = 0; i < arraySize; i++) {
                assertEquals(15, outputBuffer.get(i));
            }

            paramBuffer.destroy();
        }
    }
}
