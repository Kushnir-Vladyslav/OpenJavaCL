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

import io.github.kushnirvladyslav.memory.buffer.AbstractBuffer;
import io.github.kushnirvladyslav.memory.buffer.AbstractGlobalBuffer;
import io.github.kushnirvladyslav.memory.buffer.Dynamical;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a dynamic global OpenCL buffer.
 * This buffer type provides automatic resizing capabilities for global memory buffers.
 *
 * <p>Dynamic global buffers:
 * <ul>
 *   <li>Support automatic resizing when needed</li>
 *   <li>Maintain a minimum capacity</li>
 *   <li>Increase capacity by 50% when resizing</li>
 *   <li>Ensure thread-safe resizing operations</li>
 * </ul>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class GlobalDynamicBuffer
        extends AbstractGlobalBuffer
        implements Dynamical<GlobalDynamicBuffer> {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDynamicBuffer.class);

    /**
     * Creates a new GlobalDynamicBuffer with minimum capacity.
     */
    public GlobalDynamicBuffer () {
        logger.debug("Creating new GlobalDynamicBuffer instance with minimum capacity");
        withInitSize(getMinCapacity());
    }

    @Override
    public AbstractBuffer withInitSize(int newSize) {
//        initCheck();
        if (newSize < getMinCapacity()) {
            String message = String.format("Buffer size must be positive, got %d for buffer '%s'", newSize, getBufferName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Setting initial size to {} for buffer '{}'", newSize, getBufferName());
        this.capacity = newSize;
        return this;
    }

    @Override
    public String toString() {
        return String.format("GlobalDynamicBuffer{name='%s', capacity=%d, dataClass=%s}",
                getBufferName(), capacity, dataProcessorObject.getClass().getSimpleName());
    }
}
