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

package io.github.kushnirvladyslav.memory.newBuffer;

import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public abstract class GlobalBuffer
        extends ClMemBuffer{
    private static final Logger logger = LoggerFactory.getLogger(GlobalBuffer.class);

    protected ByteBuffer stagingBuffer;

    protected long flags = 0;
    protected int size = 0;
    protected int capacity;

    protected GlobalBuffer(GlobalBufferBuilder<?, ?> builder) {
        super(builder);

        this.capacity = builder.getCapacity();
        this.capacity |= builder.getDeviceMemoryAccess().getFlag();

        setup();

        if(builder.getStagingBuffer()){
            stagingBuffer = MemoryUtil.memAlloc(capacity * dataObject.getSizeStruct());
        }

        createClMem();
    }

    protected abstract void setup();

    @Override
    protected long createClMem() {
        if (capacity < 1) {
            String message = String.format("Buffer capacity must be positive, got %d for '%s'",
                    capacity, getName());
            logger.error(message);
            throw new IllegalStateException(message);
        }

        long newClMem;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errorCode = stack.mallocInt(1);
            newClMem = CL10.clCreateBuffer(
                    context.getContext(),
                    flags,
                    (long) capacity * dataObject.getSizeStruct(),
                    errorCode
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                String message = String.format(
                        "Failed to create OpenCL buffer '%s' with error: '%s'",
                        getName(), OpenCLErrorUtils.getCLErrorString(errorCode.get(0)));
                logger.error(message);
                throw new BufferInitializationException(message);
            }

            if (newClMem == 0) {
                String message = String.format("Failed to create OpenCL buffer for '%s'", getName());
                logger.error(message);
                throw new BufferInitializationException(message);
            }
        }

        return newClMem;
    }

    public int getCapacity(){
        return capacity;
    }

    /**
     * Returns the buffer size based on buffer operations from the host.
     * If more data has been written to the device, within capacity, than from the host, the "size" will not reflect this.
     *
     * @return size
     */
    public int getSize(){
        return size;
    }

    @Override
    protected void performDestroy(){
        if(stagingBuffer != null) {
            MemoryUtil.memFree(stagingBuffer);
        }

        super.performDestroy();
    }
}
