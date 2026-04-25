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

package io.github.kushnirvladyslav.memory.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class for OpenCL buffer objects.
 * Provides centralized management of buffer lifecycle and operations.
 *
 * <p>This class implements a registry pattern for OpenCL buffers, providing:
 * <ul>
 *   <li>Buffer registration and tracking</li>
 *   <li>Buffer lookup by name</li>
 *   <li>Resource cleanup and management</li>
 *   <li>Buffer removal and destruction</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * BufferManager manager = new BufferManager();
 * BaseBuffer buffer = new GlobalDynamicBuffer()
 *     .withBufferName("MyBuffer")
 *     .withDataClass(FloatDataProcessor.class)
 *     .init();
 * manager.registerBuffer(buffer);
 * </pre>
 *
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public class BufferManager {
    private static final Logger logger = LoggerFactory.getLogger(BufferManager.class);
    private final Map<String, BaseBuffer> buffers = new LinkedHashMap<>();

    /**
     * Registers a buffer with this manager.
     * The buffer will be tracked and managed by this manager instance.
     *
     * @param buffer the buffer to register
     * @throws IllegalArgumentException if the buffer is null
     */
    void registerBuffer(BaseBuffer buffer) {
        if (buffer == null) {
            String message = "Cannot register null buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        if (buffers.containsKey(buffer.getName())){
            String message = String.format(
                    "Buffer with name '%s' is already registered in this context",
                    buffer.getName());
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Registering buffer '{}'", buffer.getName());
        buffers.put(buffer.getName(), buffer);
    }

    /**
     * Releases all buffers managed by this instance.
     * Each buffer is destroyed and removed from management.
     */
    public void releaseAll() {
        logger.debug("Releasing all buffers (count: {})", buffers.size());

        List<BaseBuffer> snapshot = new ArrayList<>(buffers.values());
        buffers.clear();

        for (BaseBuffer buffer : snapshot) {
            logger.trace("Destroying buffer '{}'", buffer.getName());
            buffer.destroy();
        }
        logger.debug("All buffers released and cleared");
    }

    /**
     * Retrieves a buffer by its name.
     *
     * @param bufferName the name of the buffer to retrieve
     * @return the buffer with the specified name, or null if not found
     * @throws IllegalArgumentException if bufferName is null or empty
     */
    public BaseBuffer getBuffer(String bufferName) {
        if (bufferName == null || bufferName.trim().isEmpty()) {
            String message = "Buffer name cannot be null or empty";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        logger.trace("Looking up buffer '{}'", bufferName);
        BaseBuffer buffer = buffers.get(bufferName);
        if(buffer != null){
            logger.debug("Found buffer '{}'", bufferName);
            return buffer;
        }
        logger.debug("Buffer '{}' not found", bufferName);
        return null;
    }

    /**
     * Removes a buffer from management without destroying it.
     *
     * @param buffer the buffer to remove
     * @throws IllegalArgumentException if the buffer is null
     */
    public void remove (BaseBuffer buffer) {
        if (buffer == null) {
            String message = "Cannot remove null buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Removing buffer '{}'", buffer.getName());
        buffers.remove(buffer.getName());
    }

    /**
     * Releases and removes a buffer from management.
     * The buffer is destroyed before removal.
     *
     * @param buffer the buffer to release
     * @throws IllegalArgumentException if the buffer is null
     */
    public void release(BaseBuffer buffer) {
        if (buffer == null) {
            String message = "Cannot release null buffer";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        logger.debug("Releasing buffer '{}'", buffer.getName());
        buffers.remove(buffer.getName());
        buffer.destroy();
    }

    /**
     * Releases and removes a buffer from management by its name.
     * The buffer is destroyed before removal if found.
     *
     * @param bufferName the name of the buffer to release
     * @throws IllegalArgumentException if bufferName is null or empty
     */
    public void release (String bufferName) {
        if (bufferName == null || bufferName.trim().isEmpty()) {
            String message = "Buffer name cannot be null or empty";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        BaseBuffer buffer = buffers.get(bufferName);
        if (buffer != null) {
            logger.debug("Releasing buffer '{}'", bufferName);
            buffers.remove(bufferName);
            buffer.destroy();
        } else {
            logger.warn("Cannot release buffer '{}': not found in buffer manager", bufferName);
        }
    }
}
