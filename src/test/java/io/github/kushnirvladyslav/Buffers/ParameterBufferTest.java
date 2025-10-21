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
import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBuffer;
import io.github.kushnirvladyslav.memory.newBuffer.typedBuffer.ParameterBufferBuilder;
import org.junit.jupiter.api.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;


import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.*;

public class ParameterBufferTest {
    private ClContext context;
    private Device device = OpenCL.getPlatforms().get(0).getBestDevice();

    private ParameterBufferBuilder builder;
    private long clProgram;
    private long clKernel;

    @BeforeEach
    void setUp() {
        context = new ContextBuilder()
                .withDevice(device)
                .create();

        builder = new ParameterBufferBuilder();
    }

    @AfterEach
    void tearDown() {
        if (context != null && !context.isClosed()) {
            OpenCL.destroyContext(context);
        }
    }

    private long createSimpleKernel(String kernelName) {
        String source =
                "__kernel void " + kernelName + "(const int value) {\n" +
                        "    // Dummy kernel\n" +
                        "}\n";
        return createKernelFromSource(source, kernelName);
    }

    private long createKernelWithMultipleArgs(String kernelName) {
        String source =
                "__kernel void " + kernelName + "(const int a, const int b, const float c) {\n" +
                        "    // Dummy kernel\n" +
                        "}\n";
        return createKernelFromSource(source, kernelName);
    }

    private long createKernelFromSource(String source, String kernelName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errBuf = stack.mallocInt(1);


            long program = clCreateProgramWithSource(
                    context.getContext(),
                    source,
                    errBuf
            );

            if (errBuf.get(0) != CL_SUCCESS) {
                throw new RuntimeException("Failed to create program: " + errBuf.get(0));
            }

            int buildStatus = clBuildProgram(
                    program,
                    device.getDeviceID(),
                    "",
                    null,
                    0
            );

            if (buildStatus != CL_SUCCESS) {
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

            if (errBuf.get(0) != CL_SUCCESS) {
                clReleaseProgram(program);
                throw new RuntimeException("Failed to create kernel: " + errBuf.get(0));
            }

            clProgram = program;
            return kernel;

        } catch (Exception e) {
            throw new RuntimeException("Error creating kernel", e);
        }
    }

    // ========== Constructor and Setup Tests ==========

    @Test
    @DisplayName("Should create ParameterBuffer with valid parameters")
    void testCreateParameterBuffer() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertNotNull(buffer);
        assertFalse(buffer.isClosed());
        assertEquals(IntDataProcessor.class, buffer.getDataClass());
        assertTrue(buffer.inSameContext(context));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should create ParameterBuffer with custom name")
    void testCreateParameterBufferWithName() {
        String bufferName = "TestBuffer";
        ParameterBuffer buffer = builder.setup(bufferName, IntDataProcessor.class, context);

        assertNotNull(buffer);
        assertEquals(bufferName, buffer.getName());

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when DataProcessor class doesn't implement ToByteBuffer")
    void testCreateBufferWithInvalidDataClass() {
        class InvalidDataProcessor implements DataProcessor {
            @Override
            public int getSizeStruct() { return 4; }
            @Override
            public int getSizeArray(Object arr) { return 1; }
        }

        assertThrows(BufferInitializationException.class, () -> {
            builder.setup(InvalidDataProcessor.class, context);
        });
    }

    @Test
    @DisplayName("Should throw exception when context is null")
    void testCreateBufferWithNullContext() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntDataProcessor.class, null);
        });
    }

    @Test
    @DisplayName("Should throw exception when context is closed")
    void testCreateBufferWithClosedContext() {
        OpenCL.destroyContext(context);

        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(IntDataProcessor.class, context);
        });
    }

    @Test
    @DisplayName("Should throw exception when data class is null")
    void testCreateBufferWithNullDataClass() {
        assertThrows(IllegalArgumentException.class, () -> {
            builder.setup(null, context);
        });
    }

    // ========== Write Tests ==========

    @Test
    @DisplayName("Should write integer parameter successfully")
    void testWriteIntParameter() {
        ParameterBuffer buffer = builder.setup("IntBuffer", IntDataProcessor.class, context);

        assertDoesNotThrow(() -> buffer.write(42));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should write integer parameter successfully")
    void testWriteIntegerParameter() {
        ParameterBuffer buffer = builder.setup("IntBuffer", IntDataProcessor.class, context);

        Integer value = 100;
        assertDoesNotThrow(() -> buffer.write(value));

        buffer.destroy();
    }

    @Test
    void testWriteIntOneElementArrayParameter() {
        ParameterBuffer buffer = builder.setup("IntArrayBuffer", IntDataProcessor.class, context);

        int[] array = {3};
        assertDoesNotThrow(() -> buffer.write(array));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should write Integer boxed array parameter successfully")
    void testWriteIntegerOneElementBoxedArrayParameter() {
        ParameterBuffer buffer = builder.setup("IntegerArrayBuffer", IntDataProcessor.class, context);

        Integer[] array = {20};
        assertDoesNotThrow(() -> buffer.write(array));

        buffer.destroy();
    }

    @Test
    void testWriteIntArrayParameter() {
        ParameterBuffer buffer = builder.setup("IntArrayBuffer", IntDataProcessor.class, context);

        int[] array = {1, 2, 3, 4, 5};
        assertThrows(IllegalStateException.class, () -> buffer.write(array));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should write Integer boxed array parameter successfully")
    void testWriteIntegerBoxedArrayParameter() {
        ParameterBuffer buffer = builder.setup("IntegerArrayBuffer", IntDataProcessor.class, context);

        Integer[] array = {10, 20, 30};
        assertThrows(IllegalStateException.class, () -> buffer.write(array));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when writing null parameter")
    void testWriteNullParameter() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> buffer.write(null)
        );
        assertTrue(exception.getMessage().contains("Parameter cannot be null"));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when writing to closed buffer")
    void testWriteToClosedBuffer() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, () -> {
            buffer.write(42);
        });
    }

    @Test
    @DisplayName("Should write multiple times to same buffer")
    void testMultipleWrites() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertDoesNotThrow(() -> {
            buffer.write(10);
            buffer.write(20);
            buffer.write(30);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle writing different scalar values")
    void testWriteDifferentScalarValues() {
        ParameterBuffer buffer = builder.setup("ScalarBuffer", IntDataProcessor.class, context);

        assertDoesNotThrow(() -> {
            buffer.write(0);
            buffer.write(Integer.MAX_VALUE);
            buffer.write(Integer.MIN_VALUE);
            buffer.write(-42);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when writing array with null elements")
    void testWriteArrayWithNullElements() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        Integer[] arrayWithNull = {null};
        assertThrows(IllegalStateException.class, () -> {
            buffer.write(arrayWithNull);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when writing unsupported type")
    void testWriteUnsupportedType() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertThrows(Exception.class, () -> {
            buffer.write("not an integer");
        });

        buffer.destroy();
    }

    // ========== Kernel Binding Tests ==========

    @Test
    @DisplayName("Should bind buffer to kernel successfully")
    void testBindToKernel() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createSimpleKernel("test_kernel");

        assertDoesNotThrow(() -> buffer.bindToKernel(clKernel, 0));
        assertTrue(buffer.isBoundToKernel(clKernel));
        assertEquals(0, buffer.getKernelArgIndex(clKernel));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should bind buffer to multiple kernels")
    void testBindToMultipleKernels() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(100);

        long kernel1 = createSimpleKernel("kernel_one");
        long kernel2 = createSimpleKernel("kernel_two");

        assertDoesNotThrow(() -> {
            buffer.bindToKernel(kernel1, 0);
            buffer.bindToKernel(kernel2, 0);
        });

        assertTrue(buffer.isBoundToKernel(kernel1));
        assertTrue(buffer.isBoundToKernel(kernel2));

        clReleaseKernel(kernel2);
        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when binding to zero kernel")
    void testBindToZeroKernel() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertThrows(IllegalArgumentException.class, () -> {
            buffer.bindToKernel(0L, 0);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when binding with negative argument index")
    void testBindToKernelWithNegativeIndex() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        clKernel = createSimpleKernel("test_kernel");

        assertThrows(IllegalArgumentException.class, () -> {
            buffer.bindToKernel(clKernel, -1);
        });

        buffer.destroy();
    }

    @Test
    @DisplayName("Should warn when binding buffer to same kernel and index")
    void testRebindToSameKernelAndIndex() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createSimpleKernel("test_kernel");

        buffer.bindToKernel(clKernel, 0);

        assertDoesNotThrow(() -> buffer.bindToKernel(clKernel, 0));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should throw exception when binding to same kernel with different index")
    void testBindToSameKernelDifferentIndex() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createKernelWithMultipleArgs("test_kernel");

        buffer.bindToKernel(clKernel, 0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> buffer.bindToKernel(clKernel, 1)
        );

        assertTrue(exception.getMessage().contains("already bound"));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should unbind kernel successfully")
    void testUnbindKernel() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createSimpleKernel("test_kernel");

        buffer.bindToKernel(clKernel, 0);
        assertTrue(buffer.isBoundToKernel(clKernel));

        boolean result = buffer.unbindKernel(clKernel);
        assertTrue(result);
        assertFalse(buffer.isBoundToKernel(clKernel));
        assertEquals(-1, buffer.getKernelArgIndex(clKernel));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should return false when unbinding non-bound kernel")
    void testUnbindNonBoundKernel() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        clKernel = createSimpleKernel("test_kernel");

        boolean result = buffer.unbindKernel(clKernel);
        assertFalse(result);

        buffer.destroy();
    }

    @Test
    @DisplayName("Should return false when unbinding zero kernel")
    void testUnbindZeroKernel() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        boolean result = buffer.unbindKernel(0L);
        assertFalse(result);

        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle unbind after rebind to same kernel")
    void testUnbindAfterRebind() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createSimpleKernel("test_kernel");

        buffer.bindToKernel(clKernel, 0);
        buffer.bindToKernel(clKernel, 0); // Rebind

        boolean result = buffer.unbindKernel(clKernel);
        assertTrue(result);
        assertFalse(buffer.isBoundToKernel(clKernel));

        buffer.destroy();
    }

    // ========== Destruction Tests ==========

    @Test
    @DisplayName("Should destroy buffer successfully")
    void testDestroyBuffer() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertDoesNotThrow(() -> buffer.destroy());
        assertTrue(buffer.isClosed());
    }

    @Test
    @DisplayName("Should throw exception when destroying already closed buffer")
    void testDestroyAlreadyClosedBuffer() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, () -> {
            buffer.destroy();
        });
    }

    @Test
    @DisplayName("Should clean up kernel bindings on destroy")
    void testDestroyWithKernelBindings() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        long kernel1 = createSimpleKernel("kernel_one");
        long kernel2 = createSimpleKernel("kernel_two");

        buffer.bindToKernel(kernel1, 0);
        buffer.bindToKernel(kernel2, 0);

        buffer.destroy();

        assertTrue(buffer.isClosed());

        clReleaseKernel(kernel1);
        clReleaseKernel(kernel2);
    }

    @Test
    @DisplayName("Should not allow operations after destroy")
    void testOperationsAfterDestroy() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, () -> buffer.write(42));
        assertThrows(BufferDestructionException.class, () -> buffer.getName());
        assertThrows(BufferDestructionException.class, () -> buffer.getContext());
        assertThrows(BufferDestructionException.class, () -> buffer.getDataClass());

        clKernel = createSimpleKernel("test_kernel");
        assertThrows(BufferDestructionException.class,
                () -> buffer.bindToKernel(clKernel, 0));
    }

    // ========== Thread Safety Tests ==========

    @Test
    @DisplayName("Should handle concurrent writes safely")
    void testConcurrentWrites() throws InterruptedException {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int value = i;
            threads[i] = new Thread(() -> {
                buffer.write(value);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertFalse(buffer.isClosed());
        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle concurrent bindings safely")
    void testConcurrentBindings() throws InterruptedException {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        int kernelCount = 5;
        long[] kernels = new long[kernelCount];
        Thread[] threads = new Thread[kernelCount];

        for (int i = 0; i < kernelCount; i++) {
            kernels[i] = createSimpleKernel("kernel_" + i);
            final long kernel = kernels[i];
            threads[i] = new Thread(() -> {
                buffer.bindToKernel(kernel, 0);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        for (long kernel : kernels) {
            assertTrue(buffer.isBoundToKernel(kernel));
            clReleaseKernel(kernel);
        }

        buffer.destroy();
    }

    // ========== State Tests ==========

    @Test
    @DisplayName("Should throw exception when accessing closed buffer name")
    void testGetNameAfterDestroy() {
        ParameterBuffer buffer = builder.setup("TestBuffer", IntDataProcessor.class, context);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, buffer::getName);
    }

    @Test
    @DisplayName("Should throw exception when accessing closed buffer context")
    void testGetContextAfterDestroy() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.destroy();

        assertThrows(BufferDestructionException.class, buffer::getContext);
    }

    @Test
    @DisplayName("Should return correct toString representation")
    void testToString() {
        ParameterBuffer buffer = builder.setup("MyBuffer", IntDataProcessor.class, context);

        String result = buffer.toString();
        assertTrue(result.contains("ParameterBuffer"));
        assertTrue(result.contains("MyBuffer"));
        assertTrue(result.contains("IntDataProcessor"));
        assertTrue(result.contains("RUNNING"));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should correctly report context association")
    void testContextAssociation() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertTrue(buffer.inSameContext(context));
        assertSame(context, buffer.getContext());

        buffer.destroy();
    }

    @Test
    @DisplayName("Should return -1 for kernel arg index when not bound")
    void testGetKernelArgIndexNotBound() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        clKernel = createSimpleKernel("test_kernel");

        assertEquals(-1, buffer.getKernelArgIndex(clKernel));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should return correct kernel arg index after binding")
    void testGetKernelArgIndexAfterBinding() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);
        buffer.write(42);

        clKernel = createKernelWithMultipleArgs("test_kernel");

        buffer.bindToKernel(clKernel, 2);

        assertEquals(2, buffer.getKernelArgIndex(clKernel));

        buffer.destroy();
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle empty buffer name generation")
    void testEmptyNameGeneration() {
        ParameterBuffer buffer1 = builder.setup(IntDataProcessor.class, context);
        ParameterBuffer buffer2 = builder.setup(IntDataProcessor.class, context);

        assertNotNull(buffer1.getName());
        assertNotNull(buffer2.getName());
        assertNotEquals(buffer1.getName(), buffer2.getName());

        buffer1.destroy();
        buffer2.destroy();
    }

    @Test
    @DisplayName("Should handle write before and after kernel binding")
    void testWriteBeforeAndAfterBinding() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        buffer.write(10);

        clKernel = createSimpleKernel("test_kernel");
        buffer.bindToKernel(clKernel, 0);

        assertDoesNotThrow(() -> buffer.write(20));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle zero value writes")
    void testWriteZeroValue() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertDoesNotThrow(() -> buffer.write(0));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle negative value writes")
    void testWriteNegativeValue() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertDoesNotThrow(() -> buffer.write(-42));

        buffer.destroy();
    }

    @Test
    @DisplayName("Should handle maximum integer value")
    void testWriteMaxIntValue() {
        ParameterBuffer buffer = builder.setup(IntDataProcessor.class, context);

        assertDoesNotThrow(() -> buffer.write(Integer.MAX_VALUE));
        assertDoesNotThrow(() -> buffer.write(Integer.MIN_VALUE));

        buffer.destroy();
    }
}
