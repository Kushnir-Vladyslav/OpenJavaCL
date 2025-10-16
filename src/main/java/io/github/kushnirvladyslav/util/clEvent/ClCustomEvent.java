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

import io.github.kushnirvladyslav.ClContext;
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

public class ClCustomEvent  extends ClEvent{
    private static final Logger logger = LoggerFactory.getLogger(ClCustomEvent.class);

    public ClCustomEvent(ClContext context){
        super(createUserEvent(context));
    }

    private static long createUserEvent(ClContext context) {
        if(context.isClosed()){
            throw new IllegalStateException("OpenCL context has been closed");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errorCode = stack.mallocInt(1);

            long eventHandle = CL11.clCreateUserEvent(context.getContext(), errorCode);

            if (!OpenCLErrorUtils.isSuccess(errorCode.get(0))) {
                String message = String.format(
                        "Failed to create user event: %s",
                        OpenCLErrorUtils.getCLErrorString(errorCode.get(0))
                );
                logger.error(message);
                throw new BufferOperationException(message);
            }

            logger.trace("User event created: {}", eventHandle);
            return eventHandle;
        }
    }

    public void setComplete(){
        setStatus(CL10.CL_COMPLETE);
    }

    public void setError(int errorCode) {
        if (errorCode >= 0) {
            throw new IllegalArgumentException(
                    "Error code must be negative, got: " + errorCode
            );
        }
        setStatus(errorCode);
    }

    public void setStatus(int executionStatus) {

        int errorCode = CL11.clSetUserEventStatus(eventPointer, executionStatus);

        if (!OpenCLErrorUtils.isSuccess(errorCode)) {
            String message = String.format(
                    "Failed to set user event status: %s",
                    OpenCLErrorUtils.getCLErrorString(errorCode)
            );
            logger.error(message);
            throw new BufferOperationException(message);
        }

        logger.trace("User event status set to: {}", executionStatus);
    }
}
