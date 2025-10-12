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

import io.github.kushnirvladyslav.memory.newBuffer.KernelAwareBuffer;
import org.lwjgl.opencl.CL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalBuffer
        extends KernelAwareBuffer {
    private static final Logger logger = LoggerFactory.getLogger(LocalBuffer.class);

    private int size;

    public LocalBuffer(LocalBufferBuilder builder){
        super(builder);

        this.size = builder.getSize();
    }

    public int getSize(){
        checkNotClosed();

        return size;
    }

    public void resize(int newSize){
        checkNotClosed();

        if(newSize < 1) {
            String message = String.format(
                    "Size must be positive for LocalBuffer. Got: %d", size);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        this.size = newSize;
        rewriteAllKernels();
    }

    @Override
    protected void setKernelArg(long targetKernel, int argIndex) {
        logger.trace("Setting kernel argument for LocalBuffer '{}' at index {}", getName(), argIndex);
        int errorCode = CL10.clSetKernelArg(
                targetKernel,
                argIndex,
                (long) size * dataObject.getSizeStruct());

        if (errorCode != CL10.CL_SUCCESS) {
            String message = String.format(
                    "Failed to set kernel argument for LocalBuffer '%s' at index %d: error code %d",
                    getName(), argIndex, errorCode);
            logger.error(message);
            throw new IllegalStateException(message);
        }
    }
}
