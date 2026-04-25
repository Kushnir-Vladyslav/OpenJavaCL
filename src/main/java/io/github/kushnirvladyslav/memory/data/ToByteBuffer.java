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

package io.github.kushnirvladyslav.memory.data;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * Interface for converting data structures to ByteBuffer format for OpenCL operations.
 * This interface is used in conjunction with the DataProcessor interface to provide
 * serialization capabilities for OpenCL buffer operations.
 *
 * <p>Implementations of this interface should handle the conversion of their specific
 * data type to a ByteBuffer format that can be used directly with OpenCL. The conversion
 * should maintain data integrity and handle proper byte ordering.
 *
 * <p>Example implementation for float arrays:
 * <pre>
 * public class FloatDataProcessor implements DataProcessor, ToByteBuffer {
 *     {@literal @}Override
 *     public void convertToByteBuffer(ByteBuffer buffer, Object source) {
 *         if (!(source instanceof float[])) {
 *             throw new IllegalArgumentException("Source must be float[]");
 *         }
 *         float[] data = (float[]) source;
 *         buffer.asFloatBuffer().put(data);
 *     }
 * }
 * </pre>
 *
 * <p>Important considerations for implementations:
 * <ul>
 *   <li>The ByteBuffer should be properly positioned before writing</li>
 *   <li>The implementation should handle boundary checks</li>
 *   <li>The byte order should match the OpenCL device requirements</li>
 *   <li>The conversion should be efficient for large data sets</li>
 * </ul>
 *
 * @see java.nio.ByteBuffer
 * @see DataProcessor
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public interface ToByteBuffer {

    /**
     * Converts the source data structure to bytes and writes them to the provided ByteBuffer.
     * This method is called during OpenCL buffer write operations to prepare data
     * for transfer to the device.
     *
     * <p>The implementation should:
     * <ul>
     *   <li>Validate the source object type</li>
     *   <li>Ensure the buffer has sufficient remaining capacity</li>
     *   <li>Handle the conversion efficiently</li>
     *   <li>Maintain proper byte ordering</li>
     * </ul>
     *
     * @param buffer the destination ByteBuffer to write the converted data into
     * @param source the source object to convert (typically an array)
     * @throws IllegalArgumentException if the source object is null or of invalid type
     * @throws BufferOverflowException if the buffer's remaining capacity is insufficient
     */
    void convertToByteBuffer(ByteBuffer buffer, Object source);
}
