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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for OpenCL data type representations.
 * Defines core functionality for size calculations and data structure management.
 *
 * <p>This interface provides a foundation for all data types that can be used with OpenCL buffers.
 * It includes default implementations for common operations and validations, while allowing
 * implementations to focus on type-specific logic.
 *
 * <p>Example implementation:
 * <pre>
 * public class FloatDataProcessor implements DataProcessor {
 *     {@literal @}Override
 *     public int getSizeStruct() {
 *         return Float.BYTES;
 *     }
 *
 *     {@literal @}Override
 *     public int getSizeArray(Object arr) {
 *         validateArray(arr, float[].class);
 *         return ((float[]) arr).length;
 *     }
 * }
 * </pre>
 *
 * @author Vladyslav Kushnir
 * @since 1.0
 */
public interface DataProcessor {
    Logger logger = LoggerFactory.getLogger(DataProcessor.class);

    /**
     * Gets the size in bytes of a single element of this data type.
     * This is used for memory allocation and pointer arithmetic in native buffers.
     *
     * @return the size in bytes of a single element
     */
    int getSizeStruct();

    /**
     * Gets the number of elements in the provided array.
     * This method is used to determine the total size needed for buffer allocation.
     *
     * @param arr the array to measure
     * @return the number of elements in the array
     * @throws IllegalArgumentException if the array is null or of an unsupported type
     */
    int getSizeArray(Object arr);

    /**
     * Validates that the size value is positive.
     *
     * @param size the size value to validate
     * @param context additional context for error messages
     * @throws IllegalArgumentException if the size is not positive
     */
    default void validateSize(int size, String context) {
        if (size <= 0) {
            String message = String.format(
                    "Size must be positive. Got %d for %s",
                    size,
                    context
            );
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

}
