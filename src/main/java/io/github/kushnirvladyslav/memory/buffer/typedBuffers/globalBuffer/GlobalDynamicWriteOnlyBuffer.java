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

package io.github.kushnirvladyslav.memory.buffer.typedBuffers.globalBuffer;

import io.github.kushnirvladyslav.memory.buffer.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a dynamic write-only global OpenCL buffer.
 * This buffer type combines dynamic resizing capabilities with write-only access,
 * optimized for data that needs to be written from the kernel but never read from it.
 *
 * <p>Dynamic write-only global buffers:
 * <ul>
 *   <li>Support writing data to the device</li>
 *   <li>Prevent reading from the device</li>
 *   <li>Use CL_MEM_READ_WRITE flag for full access</li>
 *   <li>Support automatic resizing</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class GlobalDynamicWriteOnlyBuffer
        extends GlobalDynamicBuffer
        implements Writable<GlobalDynamicWriteOnlyBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDynamicWriteOnlyBuffer.class);

    /**
     * Creates a new GlobalDynamicWriteOnlyBuffer with default configuration.
     * Sets OpenCL flags for write-only access.
     */
    public GlobalDynamicWriteOnlyBuffer() {
        logger.debug("Creating new GlobalDynamicWriteOnlyBuffer instance");
    }

    @Override
    public String toString() {
        return String.format("GlobalDynamicWriteOnlyBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
