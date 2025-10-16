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


import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.memory.buffer.KernelAwareBuffer;
import io.github.kushnirvladyslav.memory.data.ConvertToByteBuffer;
import io.github.kushnirvladyslav.memory.data.Data;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Buffer implementation for passing parameters to OpenCL kernels.
 * This buffer type is designed only for parameter passing and does not support
 * reading or writing operations.
 *
 * <p>Parameter buffers:
 * <ul>
 *   <li>Only pass data as kernel parameters</li>
 *   <li>Do not support read or write operations</li>
 *   <li>Do not allocate OpenCL memory</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class ParameterBuffer
        extends KernelAwareBuffer {

    private static final Logger logger = LoggerFactory.getLogger(LocalBuffer.class);
    private ByteBuffer nativeBuffer;


    /**
     * Creates a new empty ParameterBuffer.
     */
    public ParameterBuffer () {
        super();
        logger.debug("Creating new ParameterBuffer instance");
    }

    /**
     * Quick setup method for buffer configuration.
     *
     * @param dataClass class of data to be passed
     * @param context OpenCL context
     * @throws IllegalArgumentException if parameter is null
     */
    public void setup (Class<Data> dataClass, ClContext context) {
        logger.debug("Setting up ParameterBuffer '{}' with parameter of type {}",
                getBufferName(), dataClass.getSimpleName());

        withDataClass(dataClass);
        withOpenClContext(context);
        init();
    }

    /**
     * Quick setup method for buffer configuration.
     *
     * @param bufferName name of the buffer
     * @param dataClass class of data to be passed
     * @param context OpenCL context
     * @throws IllegalArgumentException if parameter is null
     */
    public void setup (String bufferName, Class<Data> dataClass, ClContext context) {
        logger.debug("Setting up ParameterBuffer '{}' with parameter of type {}",
                bufferName, dataClass.getSimpleName());

        withBufferName(bufferName);
        withDataClass(dataClass);
        withOpenClContext(context);
        init();
    }

    @Override
    public void additionalInit() {
        try {
            nativeBuffer = MemoryUtil.memAlloc(dataObject.getSizeStruct());
            if (nativeBuffer == null) {
                String message = String.format(
                        "Failed to allocate native buffer for ParameterBuffer '%s'",
                        getBufferName());
                logger.error(message);
                throw new IllegalStateException(message);
            }
            logger.debug("Allocated native buffer for ParameterBuffer '{}'", getBufferName());
        } catch (Exception e) {
            String message = String.format("Failed to initialize ParameterBuffer '%s'",
                    getBufferName());
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Sets the parameter value by converting it to native buffer.
     *
     * @param parameter the parameter value to set
     * @throws IllegalArgumentException if parameter is null
     * @throws IllegalStateException if conversion fails
     */
    public void setParameter (Object parameter) {
        if (parameter == null) {
            String message = String.format("Parameter cannot be null for ParameterBuffer '%s'",
                    getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try {
            nativeBuffer.clear();
            ((ConvertToByteBuffer)dataObject).convertToByteBuffer(nativeBuffer, parameter);
            logger.debug("Parameter set for ParameterBuffer '{}'", getBufferName());
        } catch (Exception e) {
            String message = String.format("Failed to convert parameter for ParameterBuffer '%s'",
                    getBufferName());
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        }
    }


    @Override
    protected void setKernelArg(long targetKernel, int argIndex) {
        logger.trace("Setting kernel argument for ParameterBuffer '{}' at index {}",
                getBufferName(), argIndex);

        int errorCode = CL10.clSetKernelArg(
                targetKernel,
                argIndex,
                nativeBuffer
        );

        if (errorCode != CL10.CL_SUCCESS) {
            String message = String.format(
                    "Failed to set kernel argument for ParameterBuffer '%s' at index %d: error code %d",
                    getBufferName(), argIndex, errorCode);
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }

    @Override
    public void destroy() {
        logger.debug("Destroying ParameterBuffer '{}'", getBufferName());

        if (nativeBuffer != null) {
            try {
                MemoryUtil.memFree(nativeBuffer);
                logger.trace("Freed native buffer for ParameterBuffer '{}'", getBufferName());
            } catch (Exception e) {
                logger.warn("Error freeing native buffer for ParameterBuffer '{}'",
                        getBufferName(), e);
            }
            nativeBuffer = null;
        }

        super.destroy();
    }

    /**
     * Returns a string representation of this buffer.
     *
     * @return a string containing buffer details
     */
    @Override
    public String toString() {
        return String.format("ParameterBuffer{name='%s', parameterType=%s}",
                getBufferName(), dataObject.getClass().getSimpleName());
    }
}
