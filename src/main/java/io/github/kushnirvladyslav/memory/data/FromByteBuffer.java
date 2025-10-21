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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Interface for converting ByteBuffer data from OpenCL operations back to Java data structures.
 * This interface works in conjunction with the DataProcessor interface to provide
 * deserialization capabilities for OpenCL buffer operations.
 *
 * <p>Implementations of this interface handle the conversion of ByteBuffer data
 * (typically received from OpenCL devices) back into appropriate Java data structures.
 * The conversion must preserve data integrity and handle proper byte ordering.
 *
 * <p>Example implementation for float arrays:
 * <pre>
 * public class FloatDataProcessor implements DataProcessor, FromByteBuffer {
 *     {@literal @}Override
 *     public Object createArr(int size) {
 *         return new float[size];
 *     }
 *
 *     {@literal @}Override
 *     public void convertFromByteBuffer(ByteBuffer buffer, Object target) {
 *         if (!(target instanceof float[])) {
 *             throw new IllegalArgumentException("Target must be float[]");
 *         }
 *         float[] data = (float[]) target;
 *         buffer.asFloatBuffer().get(data);
 *     }
 * }
 * </pre>
 *
 * <p>Important considerations for implementations:
 * <ul>
 *   <li>The ByteBuffer's position should be properly handled during reading</li>
 *   <li>Implementation should verify buffer has sufficient remaining data</li>
 *   <li>Byte ordering should match the OpenCL device's format</li>
 *   <li>Array bounds must be checked to prevent buffer overflow</li>
 *   <li>Type safety should be enforced through runtime checks</li>
 * </ul>
 *
 * @see java.nio.ByteBuffer
 * @see DataProcessor
 * @see ToByteBuffer
 * @since 1.0
 * @author Vladyslav Kushnir
 */
public interface FromByteBuffer {
    /**
     * Converts data from the ByteBuffer into the provided target array.
     * This method is called during OpenCL buffer read operations to convert
     * device data back into Java format.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>Validate the target object type</li>
     *   <li>Ensure the buffer has sufficient remaining data</li>
     *   <li>Handle the conversion efficiently</li>
     *   <li>Maintain proper byte ordering</li>
     *   <li>Update the buffer's position appropriately</li>
     * </ul>
     *
     * @param buffer the source ByteBuffer containing the data to convert
     * @param target the target object (typically an array) to store the converted data
     * @throws IllegalArgumentException if the target is null or of invalid type
     * @throws BufferUnderflowException if the buffer has insufficient remaining data
     */
    void convertFromByteBuffer (ByteBuffer buffer, Object target);

    /**
     * Creates a new array of the appropriate type with the specified size.
     * This method is called before reading data from OpenCL buffers to
     * prepare the target array.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>Create an array of the correct type</li>
     *   <li>Ensure the size is non-negative</li>
     *   <li>Handle memory allocation efficiently</li>
     * </ul>
     *
     * @param size the number of elements in the array
     * @return a new array of the appropriate type
     * @throws IllegalArgumentException if size is negative
     * @throws OutOfMemoryError if insufficient memory is available
     */
    Object createArr (int size);
}
