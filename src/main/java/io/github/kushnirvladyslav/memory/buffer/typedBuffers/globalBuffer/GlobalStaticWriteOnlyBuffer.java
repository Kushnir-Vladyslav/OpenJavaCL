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
 * Implementation of a static write-only global OpenCL buffer.
 * This buffer type provides write-only access with a fixed size,
 * optimized for scenarios where data only needs to be written to the device.
 *
 * <p>Static write-only global buffers:
 * <ul>
 *   <li>Support only writing operations</li>
 *   <li>Have fixed size after initialization</li>
 *   <li>Use CL_MEM_READ_WRITE flag for full access</li>
 *   <li>Prevent reading operations for better performance</li>
 * </ul>
 *
 * @since 1.0
 * @author Kushnir-Vladyslav
 */
public class GlobalStaticWriteOnlyBuffer
        extends GlobalStaticBuffer implements Writable<GlobalStaticWriteOnlyBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalStaticWriteOnlyBuffer.class);

    /**
     * Creates a new GlobalStaticWriteOnlyBuffer with default configuration.
     * Sets OpenCL flags for write-only access.
     */
    public GlobalStaticWriteOnlyBuffer() {
        logger.debug("Creating new GlobalStaticWriteOnlyBuffer instance");
    }

    @Override
    public String toString() {
        return String.format("GlobalStaticWriteOnlyBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
