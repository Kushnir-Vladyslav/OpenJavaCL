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
 * Implementation of a dynamic read-only global OpenCL buffer.
 * This buffer type combines dynamic resizing capabilities with read-only access,
 * optimized for data that needs to be read from the kernel but never written to.
 *
 * <p>Dynamic read-only global buffers:
 * <ul>
 *   <li>Support reading data from the device</li>
 *   <li>Prevent writing from the device</li>
 *   <li>Use CL_MEM_READ_WRITE flag for full access</li>
 *   <li>Support automatic resizing</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class GlobalDynamicReadOnlyBuffer
        extends GlobalDynamicBuffer
        implements Readable<GlobalDynamicReadOnlyBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDynamicReadOnlyBuffer.class);

    /**
     * Creates a new GlobalDynamicReadOnlyBuffer with default configuration.
     * Sets OpenCL flags for read-only access.
     */
    public GlobalDynamicReadOnlyBuffer () {
        logger.debug("Creating new GlobalDynamicReadOnlyBuffer instance");
    }

    @Override
    public String toString() {
        return String.format("GlobalDynamicReadOnlyBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
