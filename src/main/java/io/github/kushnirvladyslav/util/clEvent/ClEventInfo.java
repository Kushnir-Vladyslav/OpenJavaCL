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

package io.github.kushnirvladyslav.util.clEvent;

import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

public class ClEventInfo implements AutoCloseable{
    private static final Logger logger = LoggerFactory.getLogger(ClEventInfo.class);

    private long eventPointer = 0;

    public ClEventInfo(ClEvent event){
        if(event == null || event.isDone()){
            throw new IllegalArgumentException("ClEvent cannot be null or closed.");
        }
        eventPointer = event.getEventPointer();
    }

    public long getQueuedTime() {
        return getProfilingInfo(CL10.CL_PROFILING_COMMAND_QUEUED);
    }

    public long getSubmitTime() {
        return getProfilingInfo(CL10.CL_PROFILING_COMMAND_SUBMIT);
    }

    public long getStartTime() {
        return getProfilingInfo(CL10.CL_PROFILING_COMMAND_START);
    }

    public long getEndTime() {
        return getProfilingInfo(CL10.CL_PROFILING_COMMAND_END);
    }

    public long getExecutionTime() {
        return getEndTime() - getStartTime();
    }

    public double getExecutionTimeMs() {
        return getExecutionTime() / 1_000_000.0;
    }

    public long getTotalTime() {
        return getEndTime() - getQueuedTime();
    }

    public long getQueueWaitTime() {
        return getStartTime() - getQueuedTime();
    }

    public int getCommandType() {
        if(eventPointer == 0){
            throw new IllegalStateException("Handled event has been released.");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer commandType = stack.mallocInt(1);

            int errorCode = CL10.clGetEventInfo(
                    eventPointer,
                    CL10.CL_EVENT_COMMAND_TYPE,
                    commandType,
                    null
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                throw new BufferOperationException(
                        "Failed to get command type: " +
                                OpenCLErrorUtils.getCLErrorString(errorCode)
                );
            }

            return commandType.get(0);
        }
    }

    public String getCommandTypeName() {
        int type = getCommandType();
        switch (type) {
            case CL10.CL_COMMAND_NDRANGE_KERNEL: return "NDRANGE_KERNEL";
            case CL10.CL_COMMAND_TASK: return "TASK";
            case CL10.CL_COMMAND_NATIVE_KERNEL: return "NATIVE_KERNEL";
            case CL10.CL_COMMAND_READ_BUFFER: return "READ_BUFFER";
            case CL10.CL_COMMAND_WRITE_BUFFER: return "WRITE_BUFFER";
            case CL10.CL_COMMAND_COPY_BUFFER: return "COPY_BUFFER";
            case CL10.CL_COMMAND_READ_IMAGE: return "READ_IMAGE";
            case CL10.CL_COMMAND_WRITE_IMAGE: return "WRITE_IMAGE";
            case CL10.CL_COMMAND_COPY_IMAGE: return "COPY_IMAGE";
            case CL10.CL_COMMAND_COPY_IMAGE_TO_BUFFER: return "COPY_IMAGE_TO_BUFFER";
            case CL10.CL_COMMAND_COPY_BUFFER_TO_IMAGE: return "COPY_BUFFER_TO_IMAGE";
            case CL10.CL_COMMAND_MAP_BUFFER: return "MAP_BUFFER";
            case CL10.CL_COMMAND_MAP_IMAGE: return "MAP_IMAGE";
            case CL10.CL_COMMAND_UNMAP_MEM_OBJECT: return "UNMAP_MEM_OBJECT";
            case CL10.CL_COMMAND_MARKER: return "MARKER";
            case CL10.CL_COMMAND_ACQUIRE_GL_OBJECTS: return "ACQUIRE_GL_OBJECTS";
            case CL10.CL_COMMAND_RELEASE_GL_OBJECTS: return "RELEASE_GL_OBJECTS";
            default: return "UNKNOWN(" + type + ")";
        }
    }

    public String getProfilingReport() {
        if(eventPointer == 0){
            throw new IllegalStateException("Handled event has been released.");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer value = stack.mallocPointer(1).put(eventPointer).rewind();

            int errorCode = CL10.clWaitForEvents(value);

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Failed to wait for events: %s",
                        OpenCLErrorUtils.getCLErrorString(errorCode)
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }
        }

        return String.format(
                "Event Profiling Report:\n" +
                        "  Command Type: %s\n" +
                        "  Queue Wait Time: %.3f ms\n" +
                        "  Execution Time: %.3f ms\n" +
                        "  Total Time: %.3f ms\n" +
                        "  Queued:  %d ns\n" +
                        "  Submit:  %d ns\n" +
                        "  Start:   %d ns\n" +
                        "  End:     %d ns",
                getCommandTypeName(),
                getQueueWaitTime() / 1_000_000.0,
                getExecutionTimeMs(),
                getTotalTime() / 1_000_000.0,
                getQueuedTime(),
                getSubmitTime(),
                getStartTime(),
                getEndTime()
        );
    }

    private long getProfilingInfo(int paramName) {
        if(eventPointer == 0){
            throw new IllegalStateException("Handled event has been released.");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer value = stack.mallocLong(1);

            int errorCode = CL10.clGetEventProfilingInfo(
                    eventPointer,
                    paramName,
                    value,
                    null
            );

            if (!OpenCLErrorUtils.isSuccess(errorCode)) {
                String message = String.format(
                        "Failed to get profiling info: %s. " +
                                "Make sure command queue was created with CL_QUEUE_PROFILING_ENABLE flag.",
                        OpenCLErrorUtils.getCLErrorString(errorCode)
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }

            return value.get(0);
        }
    }

    public void release(){
        if(eventPointer != 0){
            CL10.clReleaseEvent(eventPointer);
            eventPointer = 0;
        }
    }

    @Override
    public void close() throws Exception {
        release();
    }
}
