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

import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.memory.data.Data;
import io.github.kushnirvladyslav.OpenClContext;
import io.github.kushnirvladyslav.util.StatusCL;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for OpenCL buffer management.
 * Provides common functionality for buffer initialization, configuration and resource management.
 *
 * <p>This class implements a builder pattern for buffer configuration and ensures proper
 * resource cleanup. All buffer implementations should extend this class and implement
 * specific buffer operations.
 *
 * <p>Example usage:
 * <pre>
 * AbstractBuffer buffer = new ConcreteBuffer()
 *     .withBufferName("MyBuffer")
 *     .withDataClass(FloatData.class)
 *     .withInitSize(1024)
 *     .withOpenClContext(context)
 *     .init();
 * </pre>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public abstract class AbstractBuffer {
    private static final Logger logger = LoggerFactory.getLogger(AbstractBuffer.class);
    private static final AtomicInteger counter = new AtomicInteger(0);

    private StatusCL status = StatusCL.READY;

    private String bufferName;

    private Class<?> clazz = null;
    protected OpenClContext context;

    protected Data dataObject;

    protected ByteBuffer nativeBuffer = null;

    protected int capacity = -1;

    /**
     * Creates a new AbstractBuffer instance with a default name.
     */
    protected AbstractBuffer () {
        this.bufferName  = "UnnamedBuffer " + counter.getAndIncrement();
    }

    /**
     * Checks if the buffer has already been initiated.
     *
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws BufferDestructionException if the buffer has been closed
     */
    protected void readyForInit () {
        checkIsNotDestroy();
        if (!isReady()) {
            String message = String.format("Buffer '%s' has already been initiated", bufferName);
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Sets the name of the buffer.
     *
     * @param name the name to set for the buffer
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the name is null or empty
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractBuffer withBufferName(String name) {
        readyForInit();
        if (name == null || name.trim().isEmpty()) {
            String message = "Buffer name cannot be null or empty";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Setting buffer name from '{}' to '{}'", this.bufferName, name);
        bufferName = name.trim();
        return this;
    }

    /**
     * Sets the OpenCL context for this buffer.
     *
     * @param clContext the OpenCL context to use
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the context is null
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractBuffer withOpenClContext(OpenClContext clContext) {
        readyForInit();
        if (clContext == null) {
            String message = String.format("OpenCL context cannot be null for buffer '%s'", bufferName);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Setting OpenCL context for buffer '{}'", bufferName);
        this.context = clContext;
        return this;
    }

    /**
     * Sets the initial size of the buffer.
     *
     * @param newSize the size to initialize the buffer with
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the size is not positive
     * @throws BufferDestructionException if the buffer has been closed
     */
    public AbstractBuffer withInitSize(int newSize) {
        readyForInit();
        if (newSize <= 0) {
            String message = String.format("Buffer size must be positive, got %d for buffer '%s'", newSize, bufferName);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Setting initial size to {} for buffer '{}'", newSize, bufferName);
        this.capacity = newSize;
        return this;
    }

    /**
     * Sets the data class for the buffer.
     *
     * @param <T> the type of data
     * @param newClass the class of data to be stored in the buffer
     * @return this buffer instance for method chaining
     * @throws BufferInitializationException if the buffer has already been initiated
     * @throws IllegalArgumentException if the class is null
     * @throws BufferDestructionException if the buffer has been closed
     */
    public <T extends Data> AbstractBuffer withDataClass(Class<T> newClass) {
        readyForInit();
        if (newClass == null) {
            String message = String.format("Data class cannot be null for buffer '%s'", bufferName);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Setting data class to {} for buffer '{}'", newClass.getSimpleName(), bufferName);
        this.clazz = newClass;
        return this;
    }

    /**
     * Initializes the buffer with the configured settings.
     *
     * @throws BufferInitializationException if any required settings are missing or invalid
     * @throws BufferDestructionException if the buffer has been closed
     */
    public final void init() {
        readyForInit();
        logger.info("Initializing buffer '{}'", bufferName);

        validateInitialization();

        try {
            initializeDataObject();
            registerWithContext();

            if (this instanceof AdditionalLifecycle) {
                AdditionalLifecycle additionalLifecycle = (AdditionalLifecycle) this;
                try {
                    additionalLifecycle.additionalInit();
                } catch (Exception e) {
                    throw new IllegalStateException(
                            String.format("Additional initialization failed for buffer '%s'", bufferName), e
                    );
                }
            }

            setStatus(StatusCL.RUNNING);
            logger.info("Successfully initialized buffer '{}'", bufferName);
        } catch (Exception e) {
            String message = String.format("Failed to initialize buffer '%s'", bufferName);
            logger.error(message, e);
            cleanup();
            throw new IllegalStateException(message, e);
        }
    }

    private void validateInitialization() {
        if (bufferName == null) {
            throwInitError("Buffer name cannot be null");
        }
        if (clazz == null) {
            throwInitError("Data class must be set");
        }
        if (capacity < 1) {
            throwInitError("Buffer size must be positive");
        }
        if (context == null) {
            throwInitError("OpenCL context must be set");
        }
    }

    private void initializeDataObject() {
        try {
            dataObject = (Data) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throwInitError("Failed to instantiate data class: " + e.getMessage());
        }
    }

    private void registerWithContext() {
        try {
//            context.getBufferManager().registerBuffer(this);
        } catch (Exception e) {
            throwInitError("Failed to register buffer with context: " + e.getMessage());
        }
    }

    protected void throwInitError(String message) {
        String fullMessage = String.format("Initialization error for buffer '%s': %s", bufferName, message);
        logger.error(fullMessage);
        throw new BufferInitializationException(fullMessage);
    }

    /**
     * Sets kernel arguments for this buffer.
     *
     * @param targetKernel the kernel to set arguments for
     * @param argIndex the index of the argument to set
     */
    protected abstract void setKernelArg (long targetKernel, int argIndex);

    /**
     * Gets the name of the buffer.
     *
     * @return the buffer name
     * @throws BufferDestructionException if the buffer has been closed
     */
    public String getBufferName () {
        checkIsNotDestroy();
        return bufferName;
    }

    /**
     * Gets the capacity of the buffer.
     *
     * @return the buffer capacity
     * @throws BufferDestructionException if the buffer has been closed
     */
    public int getCapacity() {
        checkIsNotDestroy();
        return capacity;
    }

    /**
     * Gets the data class associated with this buffer.
     *
     * @return the data class
     */
    public Class<?> getDataClass () {
        return clazz;
    }

    /**
     * Releases all resources associated with this buffer.
     * This method is idempotent and can be called multiple times safely.
     *
     * @throws BufferDestructionException if the buffer has been closed
     */
    public void destroy () {
        checkIsNotDestroy();

        logger.info("Destroying buffer '{}'", bufferName);

        if (this instanceof AdditionalLifecycle) {
            AdditionalLifecycle additionalLifecycle = (AdditionalLifecycle) this;
            try {
                additionalLifecycle.additionalCleanup();
            } catch (Exception e) {
                logger.error("Error during additional cleanup for buffer '{}'", bufferName, e);
            }
        }

        cleanup();
        setStatus(StatusCL.CLOSED);
        logger.info("Buffer '{}' destroyed", bufferName);

    }

    /**
     * Checks whether the buffer is currently running and available for use.
     * A running buffer can be initiated.
     *
     * @return true if the buffer is just created, false otherwise
     */
    public boolean isReady() {
        return status == StatusCL.READY;
    }

    /**
     * Checks whether the buffer is currently running and available for use.
     * A running buffer can execute OpenCL operations and manage resources.
     *
     * @return true if the buffer is running, false otherwise
     */
    public boolean isRunning() {
        return status == StatusCL.RUNNING;
    }

    /**
     * Checks whether the buffer has been closed and is no longer usable.
     * A closed buffer cannot execute operations or manage resources.
     *
     * @return true if the buffer is closed, false otherwise
     */
    public boolean isClosed() {
        return status == StatusCL.CLOSED;
    }

    /**
     * Sets the status of this OpenCL.
     *
     * @param newStatus the new status to set, must not be null
     * @throws IllegalArgumentException if newStatus is null
     */
    private void setStatus(StatusCL newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        status = newStatus;
    }

    /**
     * Validates that the buffer has not been closed.
     * This method is used internally to ensure buffer operations are only
     * performed on active state.
     *
     * @throws BufferDestructionException if the buffer has been closed
     */
    protected void checkIsNotDestroy () {
        if (isClosed()) {
            throw new BufferDestructionException("Buffer \"" + bufferName + "\" has been closed.");
        }
    }

    private void cleanup() {
        if (nativeBuffer != null) {
            try {
                MemoryUtil.memFree(nativeBuffer);
                nativeBuffer = null;
            } catch (Exception e) {
                logger.error("Error freeing native buffer for '{}'", bufferName, e);
            }
        }

        if (context != null && context.getBufferManager() != null) {
            try {
//                context.getBufferManager().remove(this);
            } catch (Exception e) {
                logger.error("Error removing buffer '{}' from context", bufferName, e);
            }
        }

        capacity = -1;
    }
}

