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

package io.github.kushnirvladyslav.memory.newBuffer.typedBuffer;

import io.github.kushnirvladyslav.memory.data.ConvertToByteBuffer;

import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.newBuffer.KernelAwareBuffer;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ParameterBuffer
        extends KernelAwareBuffer {
    private static final Logger logger = LoggerFactory.getLogger(ParameterBuffer.class);

    private final ByteBuffer nativeBuffer;

    protected ParameterBuffer(ParameterBufferBuilder builder){
        super(builder);
        if (!(dataObject instanceof ConvertToByteBuffer)){
            String message = String.format(
                    "Data object for buffer '%s' must implement ConvertToByteBuffer interface, got %s",
                    name, dataObject.getClass().getSimpleName());
            logger.error(message);
            throw new BufferInitializationException(message);
        }
        nativeBuffer = MemoryUtil.memAlloc(dataObject.getSizeStruct());
    }

    public synchronized void write(Object parameter) {
        checkNotClosed();

        if (parameter == null) {
            String message = String.format("Parameter cannot be null for ParameterBuffer '%s'",
                    getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        try {
            nativeBuffer.clear();
            ((ConvertToByteBuffer)dataObject).convertToByteBuffer(nativeBuffer, parameter);
            logger.debug("Parameter set for ParameterBuffer '{}'", getName());
        } catch (Exception e) {
            String message = String.format("Failed to convert parameter for ParameterBuffer '%s'",
                    getName());
            logger.error(message, e);
            throw new IllegalStateException(message, e);
        }

        rebindAllKernels();
    }

    @Override
    protected void setKernelArg(long targetKernel, int argIndex) {
        logger.trace("Setting kernel argument for ParameterBuffer '{}' at index {}",
                getName(), argIndex);

        int errorCode = CL10.clSetKernelArg(
                targetKernel,
                argIndex,
                (ByteBuffer) nativeBuffer.rewind()
        );

        if (!OpenCLErrorUtils.isSuccess(errorCode)) {
            String message = String.format(
                    "OpenCL error \"'%s'\" when setting kernel arg for buffer '%s' at index %d",
                    OpenCLErrorUtils.getCLErrorString(errorCode), name, argIndex);
            logger.error(message);
            throw new BufferOperationException(message);
        }
    }

    @Override
    protected void performDestroy(){
        if(nativeBuffer != null) {
            MemoryUtil.memFree(nativeBuffer);
        }

        super.performDestroy();
    }
}
