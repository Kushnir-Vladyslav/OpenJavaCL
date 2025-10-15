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


import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


interface Dynamical
        <T extends CopyableGlobalBuffer & Dynamical<T>>{
    Logger logger = LoggerFactory.getLogger(Dynamical.class);

    default double getCapacityMultiplier() {
        return 1.5;
    }

    default int getMinCapacity() {
        return 10;
    }

    default double getShrinkFactor() {
        return 4.0;
    }

    default void resize(int newCapacity) {
        resize(newCapacity, null);
    }

    @SuppressWarnings("unchecked")
    default void resize(int newCapacity, long[] events){
        if (newCapacity < 0) {
            String message = String.format(
                    "Required capacity cannot be negative: %d",
                    newCapacity
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        T buffer = (T) this;

        buffer.checkNotClosed();

        if (buffer.capacity < newCapacity) {
            increase(newCapacity, events);
        } else {
            decrease(newCapacity, events);
        }
    }

    default void increase(int newCapacity){
        increase(newCapacity, null);
    }

    @SuppressWarnings("unchecked")
    default void increase(int newCapacity, long[] events){
        T buffer = (T) this;
        buffer.checkNotClosed();

        int currentCapacity = buffer.getCapacity();

        if (newCapacity > currentCapacity) {
            long oldClBuffer = buffer.clMem;

            try (MemoryStack stack = MemoryStack.stackPush()){
                PointerBuffer eventList = events != null && events.length != 0 ?
                        stack.mallocPointer(events.length).put(events).rewind() : null;
                PointerBuffer thisEvent = stack.mallocPointer(1);

                int dataSize = buffer.dataObject.getSizeStruct();

                buffer.capacity = (int) (newCapacity * getCapacityMultiplier());
                buffer.clMem = buffer.createClMem();

                int errorCode = CL10.clEnqueueCopyBuffer(
                        buffer.context.getCommandQueue(),
                        oldClBuffer,
                        buffer.clMem,
                        0,
                        0,
                        (long) currentCapacity * dataSize,
                        eventList,
                        thisEvent.rewind()
                );


                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    String message = String.format(
                            "Copying from old to new clBuffer, when increasing size of buffer '%s', ended with an error: %s",
                            buffer.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                    logger.error(message);
                    throw new BufferOperationException(message);
                }

                errorCode = CL10.clWaitForEvents(thisEvent);
                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    String message = String.format(
                            "Copying from old to new clBuffer, when increasing size of buffer '%s', ended with an error: %s",
                            buffer.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                    logger.error(message);
                    throw new BufferOperationException(message);
                }

                errorCode = CL10.clReleaseMemObject(oldClBuffer);
                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    logger.warn("Failed to release old buffer memory object: OpenCL error - {}",
                            OpenCLErrorUtils.getCLErrorString(errorCode));
                }

                buffer.rebindAllKernels();
            } catch (Exception e) {
                CL10.clReleaseMemObject(buffer.clMem);
                buffer.clMem = oldClBuffer;
                buffer.capacity = currentCapacity;
                throw new BufferOperationException("Failed to increase buffer size", e);
            }
        }
    }

    default void decrease(int newCapacity){
        decrease(newCapacity, null);
    }

    @SuppressWarnings("unchecked")
    default void decrease(int newCapacity, long[] events){
        T buffer = (T) this;
        buffer.checkNotClosed();

        int currentCapacity = buffer.getCapacity();

        int actualNewCapacity = Math.max ((int) (newCapacity * getCapacityMultiplier()), getMinCapacity());

        if (actualNewCapacity < currentCapacity) {
            long oldClBuffer = buffer.clMem;

            try (MemoryStack stack = MemoryStack.stackPush()){
                PointerBuffer eventList = events != null && events.length != 0 ?
                        stack.mallocPointer(events.length).put(events).rewind() : null;
                PointerBuffer thisEvent = stack.mallocPointer(1);

                int dataSize = buffer.dataObject.getSizeStruct();

                buffer.capacity = actualNewCapacity;
                buffer.clMem = buffer.createClMem();

                int errorCode = CL10.clEnqueueCopyBuffer(
                        buffer.context.getCommandQueue(),
                        oldClBuffer,
                        buffer.clMem,
                        0,
                        0,
                        (long) actualNewCapacity * dataSize,
                        eventList,
                        thisEvent.rewind()
                );


                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    String message = String.format(
                            "Copying from old to new clBuffer, when decreasing size of buffer '%s', ended with an error: %s",
                            buffer.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                    logger.error(message);
                    throw new BufferOperationException(message);
                }

                errorCode = CL10.clWaitForEvents(thisEvent);
                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    String message = String.format(
                            "Copying from old to new clBuffer, when decreasing size of buffer '%s', ended with an error: %s",
                            buffer.name, OpenCLErrorUtils.getCLErrorString(errorCode));
                    logger.error(message);
                    throw new BufferOperationException(message);
                }

                errorCode = CL10.clReleaseMemObject(oldClBuffer);
                if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                    logger.warn("Failed to release old buffer memory object: OpenCL error - {}",
                            OpenCLErrorUtils.getCLErrorString(errorCode));
                }

                buffer.rebindAllKernels();
            } catch (Exception e) {
                CL10.clReleaseMemObject(buffer.clMem);
                buffer.clMem = oldClBuffer;
                buffer.capacity = currentCapacity;
                throw new BufferOperationException("Failed to decreasing buffer size", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    default void compact() {
        T buffer = (T) this;

        if (buffer.size > 0 && (double) buffer.capacity / buffer.size >= getShrinkFactor()) {

            logger.debug("Compacting buffer: capacity={}, size={}",
                    buffer.capacity, buffer.size);
            decrease(buffer.size);
        }
    }

}
