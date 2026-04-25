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

package io.github.kushnirvladyslav;

import io.github.kushnirvladyslav.memory.buffer.BufferManager;
import io.github.kushnirvladyslav.exceptions.ResourceAllocationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.StatusCL;
import org.lwjgl.opencl.CL10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an OpenCL context with associated command queues and device resources.
 * This class encapsulates the OpenCL context and provides high-level management
 * of OpenCL resources and execution capabilities.
 *
 * <p>The context manages:
 * <ul>
 *     <li>Platform and device associations</li>
 *     <li>Command queue operations (including out-of-order execution)</li>
 *     <li>Device-specific command queues for OpenCL 2.0+ devices</li>
 *     <li>Memory buffer management through BufferManager</li>
 *     <li>Memory kernel management through KernelManager</li>
 *     <li>Resource lifecycle and cleanup</li>
 * </ul>
 *
 * <p>Current implementation focuses on single-device contexts. Future versions will support:
 * <ul>
 *     <li>Multi-device context management</li>
 *     <li>Shared virtual memory (SVM) for OpenCL 2.0+ devices</li>
 *     <li>Advanced synchronization primitives</li>
 *     <li>Cross-device memory management</li>
 *     <li>Event-based profiling and monitoring</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ClContext context = OpenCL.createContext()
 *     .withDevice(device)
 *     .create();
 *
 * }</pre>
 *
 * @author Vladyslav Kushnir
 * @version 1.0.0
 * @since 2025-08-13
 *
 * @see ContextBuilder
 * @see BufferManager
 * @see Platform
 * @see Device
 */
public class ClContext {
    private static final Logger logger = LoggerFactory.getLogger(ClContext.class);

    private final Platform platform;
    private final Device device;
    private final boolean isOutOfOrder;
    private long context = 0;
    private long commandQueue = 0;
    private long deviceCommandQueue = 0;
    private long sizeDeviceCommandQueue = 0;

    private BufferManager bufferManager = new BufferManager();

    private volatile StatusCL status = StatusCL.READY;

    /**
     * Creates a new OpenCL context with the specified configuration.
     * This constructor is package-private and should be used only by {@link ContextBuilder}.
     *
     * @param platform the OpenCL platform
     * @param device the OpenCL device
     * @param isOutOfOrder whether out-of-order execution is enabled
     * @param context the native OpenCL context handle
     * @param commandQueue the native command queue handle
     * @param deviceCommandQueue the native device-specific queue handle (OpenCL 2.0+)
     * @param sizeDeviceCommandQueue the size of device queue
     * @throws ResourceAllocationException if device queue size is invalid
     */
    ClContext(Platform platform, Device device, boolean isOutOfOrder,
              long context, long commandQueue, long deviceCommandQueue,
              long sizeDeviceCommandQueue) {
        logger.debug("Creating new OpenCL context for device: {}", device.getName());

        this.platform = platform;
        this.device = device;
        this.isOutOfOrder = isOutOfOrder;
        this.context = context;
        this.commandQueue = commandQueue;
        this.deviceCommandQueue = deviceCommandQueue;
        if (deviceCommandQueue != 0 && sizeDeviceCommandQueue <= 0) {
            destroy();
            logger.error("Invalid device queue size: {}", sizeDeviceCommandQueue);
            throw new ResourceAllocationException("Invalid device queue size");
        }
        this.sizeDeviceCommandQueue = sizeDeviceCommandQueue;

        status = StatusCL.RUNNING;

        logger.info("Created OpenCL context successfully: {}", this);
    }

    /**
     * Gets the platform associated with this context.
     *
     * @return the OpenCL platform
     * @throws IllegalStateException if context has been closed
     */
    public Platform getPlatform() {
        checkNotClosed();
        return platform;
    }

    /**
     * Gets the device associated with this context.
     *
     * @return the OpenCL device
     * @throws IllegalStateException if context has been closed
     */
    public Device getDevice() {
        checkNotClosed();
        return device;
    }

    /**
     * Checks whether out-of-order execution is enabled for this context.
     * Out-of-order execution allows commands to be executed in a different order
     * than they were enqueued, potentially improving performance.
     *
     * @return true if out-of-order execution is enabled, false otherwise
     * @throws IllegalStateException if context has been closed
     */
    public boolean isOutOfOrder() {
        checkNotClosed();
        return isOutOfOrder;
    }

    /**
     * Gets the native OpenCL context handle.
     * This handle can be used for direct OpenCL API calls.
     *
     * @return the native OpenCL context handle
     * @throws IllegalStateException if context has been closed
     */
    public long getContext() {
        checkNotClosed();
        return context;
    }

    /**
     * Gets the native command queue handle.
     * This handle represents the primary command queue for this context.
     *
     * @return the native command queue handle
     * @throws IllegalStateException if context has been closed
     */
    public long getCommandQueue() {
        checkNotClosed();
        return commandQueue;
    }

    /**
     * Checks whether this context has a device-specific command queue.
     * Device command queues are available on OpenCL 2.0+ devices and provide
     * additional performance optimizations for specific device architectures.
     *
     * @return true if device command queue is available, false otherwise
     * @throws IllegalStateException if context has been closed
     */
    public boolean hasDeviceCommandQueue() {
        checkNotClosed();
        return deviceCommandQueue != 0;
    }

    /**
     * Gets the size of the device-specific command queue.
     * This represents the maximum number of commands that can be queued
     * in the device command queue simultaneously.
     *
     * @return the size of the device command queue
     * @throws IllegalStateException if context has been closed
     */
    public long getSizeDeviceCommandQueue() {
        checkNotClosed();
        return sizeDeviceCommandQueue;
    }

    /**
     * Gets the buffer manager associated with this context.
     * The buffer manager handles OpenCL memory buffer allocation, deallocation,
     * and lifecycle management within this context.
     *
     * @return the buffer manager instance
     * @throws IllegalStateException if context has been closed
     */
    public BufferManager getBufferManager() {
        checkNotClosed();
        return bufferManager;
    }

    /**
     * Checks whether the context is currently running and available for use.
     * A running context can execute OpenCL operations and manage resources.
     *
     * @return true if the context is running, false otherwise
     */
    public boolean isRunning() {
        return status == StatusCL.RUNNING;
    }

    /**
     * Checks whether the context has been closed and is no longer usable.
     * A closed context cannot execute operations or manage resources.
     *
     * @return true if the context is closed, false otherwise
     */
    public boolean isClosed() {
        return status == StatusCL.CLOSED;
    }

    /**
     * Validates that the context has not been closed.
     * This method is used internally to ensure context operations are only
     * performed on active contexts.
     *
     * @throws IllegalStateException if the context has been closed
     */
    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("OpenCL context has been closed");
        }
    }

    /**
     * Destroys this context and releases all associated OpenCL resources.
     * This operation is irreversible and the context cannot be used after destruction.
     */
    void destroy () {
        synchronized (this) {
            if (status != StatusCL.RUNNING) {
                logger.debug("Context already closed or in process of closing");
                return;
            }
            logger.info("Starting context destruction");
            status = StatusCL.CLOSED;
        }

        int result;

        if (bufferManager != null) {
            try {
                bufferManager.releaseAll();
            } catch (Exception e) {
                logger.error("Error releasing buffers during context destruction", e);
            }
            bufferManager = null;
        }

        if (deviceCommandQueue != 0) {
            result = CL10.clReleaseCommandQueue(deviceCommandQueue);
            if (!OpenCLErrorUtils.isSuccess(result)) {
                logger.error("Failed to release device command queue: {}",
                        OpenCLErrorUtils.getCLErrorString(result));
            }
            commandQueue = 0;
        }

        if (commandQueue != 0) {
            result = CL10.clReleaseCommandQueue(commandQueue);
            if (!OpenCLErrorUtils.isSuccess(result)) {
                logger.error("Failed to release command queue: {}",
                        OpenCLErrorUtils.getCLErrorString(result));
            }
            commandQueue = 0;
        }

        if (context != 0) {
            result = CL10.clReleaseContext(context);
            if (!OpenCLErrorUtils.isSuccess(result)) {
                logger.error("Failed to release context: {}",
                        OpenCLErrorUtils.getCLErrorString(result));
            }
            context = 0;
        }

        logger.info("Context destroyed successfully");
    }

    @Override
    public String toString() {
        return String.format("ClContext{platform=%s, device=%s, status=%s, outOfOrder=%s, hasDeviceQueue=%s}",
                platform != null ? platform.getName() : "null",
                device != null ? device.getName() : "null",
                status.name(),
                isOutOfOrder,
                hasDeviceCommandQueue());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ClContext other = (ClContext) obj;

        return  this.isOutOfOrder == other.isOutOfOrder &&
                this.context == other.context &&
                this.commandQueue == other.commandQueue &&
                this.deviceCommandQueue == other.deviceCommandQueue &&
                this.sizeDeviceCommandQueue == other.sizeDeviceCommandQueue &&
                this.bufferManager.equals(other.bufferManager) &&
                this.status == other.status;
    }
}
