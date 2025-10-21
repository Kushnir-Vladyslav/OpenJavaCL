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

package io.github.kushnirvladyslav.memory.buffer.typedBuffers;

import io.github.kushnirvladyslav.memory.buffer.KernelAwareBuffer;
import io.github.kushnirvladyslav.memory.data.DataProcessor;
import io.github.kushnirvladyslav.ClContext;
import org.lwjgl.opencl.CL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of an OpenCL local memory buffer.
 * Local memory is shared by all work-items within a work-group but not between work-groups.
 * This buffer type is useful for work-group level data sharing and improving performance
 * by reducing global memory access.
 *
 * <p>Local buffers:
 * <ul>
 *   <li>Are allocated per work-group</li>
 *   <li>Have faster access than global memory</li>
 *   <li>Don't support host access</li>
 *   <li>Are temporary (exist only during kernel execution)</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class LocalBuffer
        extends KernelAwareBuffer {

    private static final Logger logger = LoggerFactory.getLogger(LocalBuffer.class);

    /**
     * Creates a new empty LocalBuffer.
     */
    public LocalBuffer() {
        logger.debug("Creating new LocalBuffer instance");
    }

    /**
     * Quick setup method for buffer configuration.
     *
     * @param dataClass class of data to be stored
     * @param context OpenCL context
     * @param initSize initial size of the buffer
     */
    public void setup (Class<DataProcessor> dataClass, ClContext context, int initSize) {
        logger.debug("Setting up LocalBuffer '{}' with size {}", this.getBufferName(), initSize);
        withDataClass(dataClass);
        withInitSize(initSize);
        withOpenClContext(context);
        init();
    }

    /**
     * Quick setup method for buffer configuration.
     *
     * @param bufferName name of the buffer
     * @param dataClass class of data to be stored
     * @param context OpenCL context
     * @param initSize initial size of the buffer
     */
    public void setup (String bufferName, Class<DataProcessor> dataClass, ClContext context, int initSize) {
        logger.debug("Setting up LocalBuffer '{}' with size {}", bufferName, initSize);
        withBufferName(bufferName);
        withDataClass(dataClass);
        withInitSize(initSize);
        withOpenClContext(context);
        init();
    }

    @Override
    public void additionalInit() {
        if (capacity < 1) {
            String message = String.format("Buffer capacity must be positive, got %d for LocalBuffer '%s'",
                    capacity, getBufferName());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        logger.debug("LocalBuffer '{}' initialized with capacity {}", getBufferName(), capacity);
    }

    @Override
    protected void setKernelArg(long targetKernel, int argIndex) {
        logger.trace("Setting kernel argument for LocalBuffer '{}' at index {}", getBufferName(), argIndex);
        int errorCode = CL10.clSetKernelArg(
                targetKernel,
                argIndex,
                (long) capacity * dataProcessorObject.getSizeStruct());

        if (errorCode != CL10.CL_SUCCESS) {
            String message = String.format(
                    "Failed to set kernel argument for LocalBuffer '%s' at index %d: error code %d",
                    getBufferName(), argIndex, errorCode);
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    @Override
    public void destroy() {
        logger.debug("Destroying LocalBuffer '{}'", getBufferName());
        super.destroy();
    }

    /**
     * Returns a string representation of this buffer.
     *
     * @return a string containing buffer details
     */
    @Override
    public String toString() {
        return String.format("LocalBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
