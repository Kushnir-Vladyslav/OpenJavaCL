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

import io.github.kushnirvladyslav.exceptions.BufferIndexOutOfBoundsException;
import io.github.kushnirvladyslav.exceptions.BufferInitializationException;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.data.FromByteBuffer;
import io.github.kushnirvladyslav.memory.data.ToByteBuffer;
import io.github.kushnirvladyslav.memory.util.HostMemoryAccess;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.clEvent.ClEventList;
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

    protected int pointer = 0;
    protected int capacity;

    protected GlobalBuffer(GlobalBufferBuilder<?, ?> builder) {
        super(builder);

        this.capacity = builder.getCapacity();

        if(builder.getHostMemoryAccess() == HostMemoryAccess.READ_ONLY){
            if(!(dataProcessor instanceof FromByteBuffer)){
                String message =  String.format(
                        "For buffer '%s' with read from host access, must implements \"FromByteBuffer\" interface.",
                        name);
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        } else if(builder.getHostMemoryAccess() == HostMemoryAccess.WRITE_ONLY){
            if(!(dataProcessor instanceof ToByteBuffer)){
                String message =  String.format(
                        "For buffer '%s' with write from host access, must implements \"ToByteBuffer\" interface.",
                        name);
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        } else if(builder.getHostMemoryAccess() == HostMemoryAccess.READ_WRITE){
            if(!(dataProcessor instanceof ToByteBuffer) || !(dataProcessor instanceof FromByteBuffer)){
                String message =  String.format(
                        "For buffer '%s' with read/write from host access, must implements \"FromByteBuffer\" and \"ToByteBuffer\" interfaces.",
                        name);
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        }

        if(builder.getStagingBuffer()){
            stagingBuffer = MemoryUtil.memAlloc(capacity * dataProcessor.getSizeStruct());
        }

       this.clMem = createClMem();
    }

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
                    (long) capacity * dataProcessor.getSizeStruct(),
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

    protected synchronized void changeCapacity(int newSize, ClEventList events){
        String message = String.format(
                "Buffer '%s' must be dynamical for change capacity",
                getName());
        logger.error(message);
        throw new BufferIndexOutOfBoundsException(message);
    }

    public int getCapacity(){
        return capacity;
    }

    public int getPointer(){
        return pointer;
    }

    public void setPointer(int newPointer){
        setPointer(newPointer, null);
    }

    public void setPointer(int newPointer, ClEventList events){
        if (newPointer < 0){
            String message = String.format("Buffer pointer must be positive, got %d for '%s'",
                    newPointer, getName());
            logger.error(message);
            throw new BufferIndexOutOfBoundsException(message);
        }

        if(newPointer >= capacity){
            try{
                changeCapacity(newPointer,events);
            }
            catch (BufferOperationException e) {
                String message = String.format(
                        "Buffer pointer must be lower than capacity in static buffer, got %d for '%s' with capacity %d",
                        newPointer, getName(), capacity);
                logger.error(message);
                throw new BufferIndexOutOfBoundsException(message);
            }
        }

        pointer = newPointer;
    }

    @Override
    protected void performDestroy(){
        if(stagingBuffer != null) {
            MemoryUtil.memFree(stagingBuffer);
        }

        super.performDestroy();
    }
}
