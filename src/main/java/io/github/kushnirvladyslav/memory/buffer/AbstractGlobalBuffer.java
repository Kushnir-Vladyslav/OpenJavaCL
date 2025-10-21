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

package io.github.kushnirvladyslav.memory.buffer;

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import io.github.kushnirvladyslav.memory.util.DeviceMemoryAccess;
import io.github.kushnirvladyslav.memory.util.HostMemoryAccess;
import io.github.kushnirvladyslav.util.CLVersion;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

/**
 * Abstract base class for OpenCL global memory buffers.
 * Provides implementation for buffer management and kernel argument binding.
 *
 * <p>This class extends {@link KernelAwareBuffer} and adds support for:
 * <ul>
 *   <li>Read/Write operations</li>
 *   <li>Dynamic buffer resizing</li>
 *   <li>Host buffer copying</li>
 *   <li>OpenCL memory flags management</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public abstract class AbstractGlobalBuffer
        extends KernelAwareBuffer {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGlobalBuffer.class);

    protected boolean copyHostBuffer = false;
    protected boolean copyNativeBuffer = false;

    protected DeviceMemoryAccess deviceMemoryAccess = DeviceMemoryAccess.READ_WRIGHT;
    protected HostMemoryAccess hostMemoryAccess = HostMemoryAccess.READ_WRIGHT;
    protected long clBuffer = 0;

    protected Object hostBuffer = null;
    protected int size = -1;

    private PointerBuffer transmitter;

    /**
     * Creates a new AbstractGlobalBuffer with default read-write access.
     */
    public AbstractGlobalBuffer() {
        super();
    }

    /**
     * Sets the OpenCL device memory access flags for this buffer.
     *
     * @param deviceMemoryAccess the OpenCL device memory access flags to set
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the DeviceMemoryAccess is null
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractGlobalBuffer withDeviceMemoryAccess(DeviceMemoryAccess deviceMemoryAccess) {
        readyForInit();

        if (deviceMemoryAccess == null) {
            String message = String.format("Device memory access cannot be null for buffer '%s'", getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.debug("Setting OpenCL device memory access to {} for buffer '{}'", deviceMemoryAccess.name(), getBufferName());
        this.deviceMemoryAccess = deviceMemoryAccess;

        return this;
    }

    /**
     * Sets the OpenCL host memory access flags for this buffer.
     *
     * @param hostMemoryAccess the OpenCL host memory access flags to set
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the DeviceMemoryAccess is null
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractGlobalBuffer withHostMemoryAccess(HostMemoryAccess hostMemoryAccess) {
        readyForInit();

        if (hostMemoryAccess == null) {
            String message = String.format("Host memory access cannot be null for buffer '%s'", getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }



        logger.debug("Setting OpenCL host memory access to {} for buffer '{}'", deviceMemoryAccess.name(), getBufferName());
        this.hostMemoryAccess = hostMemoryAccess;

        return this;
    }

    /**
     * Sets whether this buffer should maintain a copy of host data.
     *
     * @param isProjection true to maintain a host copy, false otherwise
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractGlobalBuffer withCopyHostBuffer(boolean isProjection) {
        readyForInit();
        logger.debug("Setting copyHostBuffer to {} for buffer '{}'", isProjection, getBufferName());
        copyHostBuffer = isProjection;

        return this;
    }

    /**
     * Configures whether the buffer should maintain a native copy.
     *
     * @param copyNativeBuffer true to maintain a native copy, false otherwise
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractGlobalBuffer withCopyNativeBuffer(boolean copyNativeBuffer) {
        readyForInit();
        logger.debug("Setting copyNativeBuffer to {} for buffer '{}'", copyNativeBuffer, getBufferName());
        this.copyNativeBuffer = copyNativeBuffer;
        return this;
    }

    @Override
    public void additionalInit() {
        super.additionalInit();
        logger.debug("Performing additional initialization for buffer '{}'", getBufferName());

        try {
            transmitter = MemoryUtil.memAllocPointer(1);
            if (transmitter == null) {
                String message = String.format("Failed to allocate pointer buffer for '%s'", getBufferName());
                logger.error(message);
                throw new IllegalStateException(message);
            }

            size = 0;

            validateInterfaces();
            initializeBuffer();

            logger.debug("Additional initialization completed for buffer '{}'", getBufferName());
        } catch (Exception e) {
            String message = String.format("Additional initialization failed for buffer '%s'", getBufferName());
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }

    private void validateInterfaces() {
        if (this instanceof Readable) {
            if (!(dataProcessorObject instanceof FromByteBuffer)) {
                throwInitError("DataProcessor class must implement FromByteBuffer interface for readable buffers");
            }
        }

        if (this instanceof Writable) {
            if (!(dataProcessorObject instanceof ToByteBuffer)) {
                throwInitError("DataProcessor class must implement ToByteBuffer interface for writable buffers");
            }
        }

        if (hostMemoryAccess != HostMemoryAccess.READ_WRIGHT &&
                !context.getDevice().getOpenCLVersion().isAtLeast(CLVersion.OPENCL_1_2)) {
            hostMemoryAccess = HostMemoryAccess.READ_WRIGHT;
        }
    }

    private void initializeBuffer() {
        if (this instanceof Dynamical) {
            Dynamical dynamical = (Dynamical) this;
            capacity = (int)(capacity * dynamical.getCapacityMultiplier());
            if (capacity < dynamical.getMinCapacity()) {
                capacity = dynamical.getMinCapacity();
            }
            logger.debug("Adjusted capacity for dynamic buffer '{}' to {}", getBufferName(), capacity);
        }

        if (copyHostBuffer) {
            if (dataProcessorObject instanceof FromByteBuffer) {
                FromByteBuffer converter = (FromByteBuffer) this;
                hostBuffer = converter.createArr(capacity);
                logger.debug("Created host buffer copy for buffer '{}' with capacity {}",
                        getBufferName(), capacity);
            } else {
                throwInitError("DataProcessor class must implement FromByteBuffer interface when copyHostBuffer is true");
            }
        }

        if (copyNativeBuffer) {
            try {
                nativeBuffer = MemoryUtil.memAlloc(capacity);
                if (nativeBuffer == null) {
                    throwInitError("Failed to allocate native buffer");
                }
            } catch (Exception e) {
                throwInitError("Failed to allocate native buffer: " + e.getMessage());
            }
        }

        if (clBuffer == 0) {
            clBuffer = createClBuffer();
            logger.debug("Created OpenCL buffer for '{}' with handle {}", getBufferName(), clBuffer);
        }
    }

    /**
     * Creates an OpenCL buffer with the current configuration.
     *
     * @return the handle to the created OpenCL buffer
     * @throws BufferInitializationException if buffer creation fails
     */
    protected long createClBuffer() {
        if (capacity < 1) {
            String message = String.format("Buffer capacity must be positive, got %d for '%s'",
                    capacity, getBufferName());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        long newClBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            IntBuffer errorCode = stack.mallocInt(1);
            newClBuffer = CL10.clCreateBuffer(
                    context.getContext(),
                    deviceMemoryAccess.getFlag() | hostMemoryAccess.getFlag(),
                    capacity * dataProcessorObject.getSizeStruct(),
                    errorCode
            );

            if (errorCode.get(0) != CL10.CL_SUCCESS) {
                String message = String.format(
                        "Failed to create OpenCL buffer '%s' with error: '%s'",
                        getBufferName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferInitializationException(message, errorCode.get(0));
            }

            if (newClBuffer == 0) {
                String message = String.format("Failed to create OpenCL buffer for '%s'", getBufferName());
                logger.error(message);
                throw new BufferInitializationException(message);
            }
        }

        return newClBuffer;
    }

    /**
     * Quick setup method for buffer configuration.
     *
     * @param <T> the type of data to be stored
     * @param clazz the class of data to be stored
     * @param context the OpenCL context
     * @param copyNativeBuffer whether to maintain a native buffer copy
     * @param copyHostBuffer whether to maintain a host buffer copy
     * @param initSize initial buffer size
     * @throws BufferInitializationException if any required settings are missing or invalid
     * @throws BufferDestructionException if the buffer has been closed
     */
    public <T extends DataProcessor> void setup (Class<T> clazz,
                                                 ClContext context,
                                                 boolean copyNativeBuffer,
                                                 boolean copyHostBuffer,
                                                 int initSize) {
        logger.debug("Setting up buffer '{}' with size {}", getBufferName(), initSize);
        withDataClass(clazz);
        withOpenClContext(context);
        withCopyNativeBuffer(copyNativeBuffer);
        withCopyHostBuffer(copyHostBuffer);
        withInitSize(initSize);
        init();
    }

    /**
     * Returns the OpenCL buffer handle.
     *
     * @return the OpenCL buffer handle
     */
    public long getClBuffer() {
        return clBuffer;
    }


    /**
     * Quick setup method for buffer configuration with name.
     *
     * @param <T> the type of data to be stored
     * @param bufferName the name for the buffer
     * @param clazz the class of data to be stored
     * @param context the OpenCL context
     * @param copyNativeBuffer whether to maintain a native buffer copy
     * @param copyHostBuffer whether to maintain a host buffer copy
     * @param initSize initial buffer size
     * @throws BufferInitializationException if any required settings are missing or invalid
     * @throws BufferDestructionException if the buffer has been closed
     */
    public <T extends DataProcessor> void setup (String bufferName,
                                                 Class<T> clazz,
                                                 ClContext context,
                                                 boolean copyNativeBuffer,
                                                 boolean copyHostBuffer,
                                                 int initSize) {
        logger.debug("Setting up buffer with name '{}' and size {}", bufferName, initSize);
        withBufferName(bufferName);
        withDataClass(clazz);
        withOpenClContext(context);
        withCopyNativeBuffer(copyNativeBuffer);
        withCopyHostBuffer(copyHostBuffer);
        withInitSize(initSize);
        init();
    }

    @Override
    protected void setKernelArg (long targetKernel, int argIndex) {
        logger.trace("Setting kernel argument for buffer '{}' at index {}", getBufferName(), argIndex);
        int errorCode = CL10.clSetKernelArg(
                targetKernel,
                argIndex,
                transmitter.put(0, clBuffer).rewind()
        );

        if (errorCode != CL10.CL_SUCCESS) {
            String message = String.format(
                    "Failed to set kernel argument for buffer '%s' at index %d: error  %s",
                    getBufferName(), argIndex, OpenCLErrorUtils.getCLErrorString(errorCode));
            logger.error(message);
            throw new BufferOperationException(message, errorCode);
        }
    }

    /**
     * Gets the current size of the buffer.
     *
     * @return the buffer size
     */
    public int getSize() {
        return size;
    }

    @Override
    public void destroy () {
        logger.debug("Destroying buffer '{}'", getBufferName());

        if (hostBuffer != null) {
            hostBuffer = null;
        }

        size = -1;

        if (clBuffer != 0) {
            int errorCode = CL10.clReleaseMemObject(clBuffer);
            if (errorCode != CL10.CL_SUCCESS) {
                logger.warn("Failed to release OpenCL buffer '{}': error  {}",
                        getBufferName(), OpenCLErrorUtils.getCLErrorString(errorCode));
            }
            clBuffer = 0;
        }

        if (transmitter != null) {
            try {
                MemoryUtil.memFree(transmitter);
            } catch (Exception e) {
                logger.warn("Error freeing transmitter buffer for '{}'", getBufferName(), e);
            }
            transmitter = null;
        }

        super.destroy();
        logger.debug("Buffer '{}' destroyed", getBufferName());
    }
}
