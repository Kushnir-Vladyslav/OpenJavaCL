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
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.kernel.Kernel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for OpenCL buffers that can be bound to kernels.
 * Provides functionality for managing kernel arguments and buffer bindings.
 *
 * <p>This class extends {@link AbstractBuffer} and adds the ability to:
 * <ul>
 *   <li>Bind buffer to kernel arguments</li>
 *   <li>Track kernel bindings</li>
 *   <li>Manage kernel argument indices</li>
 * </ul>
 * 
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public abstract class KernelAwareBuffer
        extends AbstractBuffer
        implements AdditionalLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(KernelAwareBuffer.class);

    /**
     * Map to store kernel bindings and their argument indices
     * Key: Kernel ID, Value: Argument Index
     */
    private final Map<Long, Integer> kernelBindings;

    /**
     * Creates a new KernelAwareBuffer instance.
     */
    protected KernelAwareBuffer() {
        super();
        this.kernelBindings = new ConcurrentHashMap<>();
        logger.debug("Created new KernelAwareBuffer instance");
    }

    /**
     * Binds this buffer to a kernel at the specified argument index.
     *
     * @param kernel the OpenCL kernel instance
     * @param argIndex the index of the kernel argument
     * @throws IllegalArgumentException if the kernel is null or argIndex is negative
     * @throws BufferInitializationException if the buffer is not initialized or binding fails
     * @throws BufferDestructionException if the buffer has been closed
     */
    public void bindKernel(Kernel kernel, int argIndex) {
        checkIsNotDestroy();
        if (kernel == null) {
            String message = String.format("Kernel cannot be null for buffer '%s'", getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        bindKernel(kernel.getClKernel(), argIndex);
    }

    /**
     * Binds this buffer to a kernel at the specified argument index.
     *
     * @param kernel the OpenCL kernel to bind to
     * @param argIndex the index of the kernel argument
     * @throws IllegalArgumentException if the kernel is invalid or argIndex is negative
     * @throws BufferOperationException if the buffer is not initialized or binding fails
     */
    public void bindKernel(long kernel, int argIndex) {
        if (isRunning()) {
            String message = String.format("Cannot bind uninitialized buffer '%s' to kernel", getBufferName());
            logger.error(message);
            throw new BufferOperationException(message);
        }

        if (kernel == 0) {
            String message = String.format("Invalid kernel (0) for buffer '%s'", getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        if (argIndex < 0) {
            String message = String.format("Invalid argument index (%d) for buffer '%s'", argIndex, getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try {
            setKernelArg(kernel, argIndex);
            kernelBindings.put(kernel, argIndex);
            logger.debug("Buffer '{}' bound to kernel {} at index {}", getBufferName(), kernel, argIndex);
        } catch (Exception e) {
            String message = String.format("Failed to bind buffer '%s' to kernel", getBufferName());
            logger.error(message, e);
            throw new BufferOperationException(message, e);
        }
    }

    /**
     * Unbinds this buffer from a specific kernel.
     *
     * @param kernel the Kernel instance to unbind from
     * @return true if the buffer was bound and is now unbound, false if it wasn't bound
     */
    public boolean unbindKernel(Kernel kernel) {
        if (kernel == null) {
            logger.warn("Attempted to unbind from null kernel for buffer '{}'", getBufferName());
            return false;
        }
        return unbindKernel(kernel.getClKernel());
    }

    /**
     * Unbinds this buffer from a specific kernel.
     *
     * @param kernel the OpenCL kernel to unbind from
     * @return true if the buffer was bound and is now unbound, false if it wasn't bound
     */
    public boolean unbindKernel(long kernel) {
        if (kernel == 0) {
            logger.warn("Attempted to unbind from invalid kernel (0) for buffer '{}'", getBufferName());
            return false;
        }

        Integer removedIndex = kernelBindings.remove(kernel);
        if (removedIndex != null) {
            logger.debug("Buffer '{}' unbound from kernel {} (was at index {})",
                    getBufferName(), kernel, removedIndex);
            return true;
        }
        return false;
    }

    /**
     * Gets the argument index for this buffer in the specified kernel.
     *
     * @param kernel the Kernel instance to check
     * @return the argument index, or -1 if the buffer is not bound to this kernel
     */
    public int getKernelArgIndex(Kernel kernel) {
        if (kernel == null) {
            return -1;
        }
        return getKernelArgIndex(kernel.getClKernel());
    }

    /**
     * Gets the argument index for this buffer in the specified kernel.
     *
     * @param kernel the OpenCL kernel to check
     * @return the argument index, or -1 if the buffer is not bound to this kernel
     */
    public int getKernelArgIndex(long kernel) {
        return kernelBindings.getOrDefault(kernel, -1);
    }

    /**
     * Checks if this buffer is bound to a specific kernel.
     *
     * @param kernel the Kernel instance to check
     * @return true if the buffer is bound to the kernel, false otherwise
     */
    public boolean isBoundToKernel(Kernel kernel) {
        if (kernel == null) {
            return false;
        }
        return isBoundToKernel(kernel.getClKernel());
    }

    /**
     * Checks if this buffer is bound to a specific kernel.
     *
     * @param kernel the OpenCL kernel to check
     * @return true if the buffer is bound to the kernel, false otherwise
     */
    public boolean isBoundToKernel(long kernel) {
        return kernelBindings.containsKey(kernel);
    }

    @Override
    public void additionalInit() {
        logger.debug("Performing additional initialization for KernelAwareBuffer '{}'", getBufferName());
    }

    @Override
    public void additionalCleanup() {
        logger.debug("Performing additional cleanup for KernelAwareBuffer '{}'", getBufferName());

        if (!kernelBindings.isEmpty()) {
            logger.debug("Cleaning up {} kernel bindings for buffer '{}'",
                    kernelBindings.size(), getBufferName());
            kernelBindings.clear();
        }
    }

    /**
     * Updates the kernel argument for this buffer in all bound kernels.
     * This method automatically updates all kernels where this buffer is bound
     * with their previously assigned argument indices.
     *
     * @throws BufferDestructionException if the buffer has been closed
     * @throws BufferOperationException if failed to pass arguments
     */
    public void setAllKernelArgs() {
        checkIsNotDestroy();

        for (Map.Entry<Long, Integer> binding : kernelBindings.entrySet()) {
            try {
                setKernelArg(binding.getKey(), binding.getValue());
            } catch (Exception e) {
                logger.error("Failed to update kernel argument for kernel {} at index {}",
                        binding.getKey(), binding.getValue(), e);
            }
        }
    }
}
