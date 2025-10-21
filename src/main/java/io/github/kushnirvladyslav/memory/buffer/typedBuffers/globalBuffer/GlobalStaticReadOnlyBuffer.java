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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a static read-only global OpenCL buffer.
 * This buffer type combines fixed size with read-only access,
 * optimized for constant data that needs to be read from the kernel but never written to.
 *
 * <p>Static read-only global buffers:
 * <ul>
 *   <li>Support reading data from the device</li>
 *   <li>Prevent writing from the device</li>
 *   <li>Use CL_MEM_READ_WRITE flag for full access</li>
 *   <li>Have fixed size after initialization</li>
 * </ul>
 *
 * @since 1.0
 * @author Kushnir-Vladyslav
 */
public class GlobalStaticReadOnlyBuffer
        extends GlobalStaticBuffer
        implements Readable<GlobalStaticReadOnlyBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalStaticReadOnlyBuffer.class);

    /**
     * Creates a new GlobalStaticReadOnlyBuffer with default configuration.
     * Sets OpenCL flags for read-only access.
     */
    public GlobalStaticReadOnlyBuffer() {
        logger.debug("Creating new GlobalStaticReadOnlyBuffer instance");
    }

    @Override
    public String toString() {
        return String.format("GlobalStaticReadOnlyBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
