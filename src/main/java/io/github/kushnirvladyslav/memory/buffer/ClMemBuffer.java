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

import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import io.github.kushnirvladyslav.memory.util.HostMemoryAccess;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ClMemBuffer
        extends KernelAwareBuffer {
    private static final Logger logger = LoggerFactory.getLogger(ClMemBuffer.class);

    protected long flags = 0L;
    protected long clMem;

    protected ClMemBuffer(ClMemBufferBuilder<?, ?> builder) {
        super(builder);

        flags |= builder.getHostMemoryAccess().getFlag();
        flags |= builder.getDeviceMemoryAccess().getFlag();

        clMem = 0;

        HostMemoryAccess access = builder.getHostMemoryAccess();
        if (access == HostMemoryAccess.READ_ONLY || access == HostMemoryAccess.READ_WRITE) {
            if (!(dataProcessor instanceof FromByteBuffer)) {
                String message = String.format(
                        "For buffer '%s' with read access, DataProcessor must implement FromByteBuffer.",
                        name);
                logger.error(message);
                throw new BufferInitializationException(message);
            }
        }
        if (access == HostMemoryAccess.WRITE_ONLY || access == HostMemoryAccess.READ_WRITE) {
            if (!(dataProcessor instanceof ToByteBuffer)) {
                String message = String.format(
                        "For buffer '%s' with write access, DataProcessor must implement ToByteBuffer.",
                        name);
                logger.error(message);
                throw new BufferInitializationException(message);
            }
        }
    }

    protected abstract long createClMem();

    @Override
    protected void setKernelArg (long targetKernel, int argIndex){
        if(clMem == 0){
            String message = String.format("cl_mem not initialized for buffer '%s'",
                    getName());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer transmitter = stack.mallocPointer(1);

            int errorCode = CL10.clSetKernelArg(
                    targetKernel,
                    argIndex,
                    transmitter.put(0, clMem).rewind()
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "OpenCL error \"'%s'\" when setting kernel arg for buffer '%s' at index %d",
                        OpenCLErrorUtils.getCLErrorString(errorCode), name, argIndex);
                logger.error(message);
                throw new BufferOperationException(message);
            }
        }
    }

    protected void releaseClMem(){
        if (clMem == 0){
            logger.warn("clMem has already been released in buffer {}", getName());
            return;
        }

        int errorCode = CL10.clReleaseMemObject(clMem);

        if(!OpenCLErrorUtils.isSuccess(errorCode)){
            String message = String.format(
                    "OpenCL error \"'%s'\" when release clMem in buffer '%s'.",
                    OpenCLErrorUtils.getCLErrorString(errorCode), name);
            logger.error(message);
            throw new BufferOperationException(message);
        }
    }

    @Override
    protected void performDestroy(){
        releaseClMem();
        super.performDestroy();
    }
}
