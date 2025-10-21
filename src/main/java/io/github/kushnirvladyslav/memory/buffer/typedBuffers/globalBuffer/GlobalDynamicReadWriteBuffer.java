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

import io.github.kushnirvladyslav.memory.buffer.Readable;
import io.github.kushnirvladyslav.memory.buffer.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a dynamic read-write global OpenCL buffer.
 * This buffer type supports both reading and writing operations while maintaining
 * dynamic resizing capabilities.
 *
 * <p>Dynamic read-write global buffers:
 * <ul>
 *   <li>Support both reading and writing operations</li>
 *   <li>Provide dynamic resizing when needed</li>
 *   <li>Use CL_MEM_READ_WRITE flag for full access</li>
 *   <li>Ensure thread-safe operations</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class GlobalDynamicReadWriteBuffer
        extends GlobalDynamicBuffer
        implements Readable<GlobalDynamicReadWriteBuffer>,
        Writable<GlobalDynamicReadWriteBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDynamicReadWriteBuffer.class);

    /**
     * Creates a new GlobalDynamicReadWriteBuffer with default configuration.
     * Sets OpenCL flags for read-write access.
     */
    public GlobalDynamicReadWriteBuffer() {
        logger.debug("Creating new GlobalDynamicReadWriteBuffer instance");
    }

    @Override
    public String toString() {
        return String.format("GlobalDynamicReadWriteBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
