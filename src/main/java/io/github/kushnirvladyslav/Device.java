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

package io.github.kushnirvladyslav;

import io.github.kushnirvladyslav.exceptions.DeviceInitializationException;
import io.github.kushnirvladyslav.exceptions.OpenCLException;
import io.github.kushnirvladyslav.util.CLVersion;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an OpenCL device and provides access to its capabilities and properties.
 * All device information is cached during initialization to improve performance.
 *
 * <p>Device properties that are cached include:
 * <ul>
 *     <li>Basic information (name, vendor, driver version)</li>
 *     <li>OpenCL version and supported extensions</li>
 *     <li>Compute capabilities (compute units, work group sizes)</li>
 *     <li>Memory information (global, local, constant memory sizes)</li>
 *     <li>Device type and available features</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Device device = platform.getBestDevice();
 * if (device.isAvailable() && device.isGPU()) {
 *     System.out.println("GPU Name: " + device.getName());
 *     System.out.println("Compute Units: " + device.getMaxComputeUnits());
 *     System.out.println("Global Memory: " + device.getFormattedGlobalMemSize());
 * }
 * }</pre>
 *
 * @author Vladyslav Kushnir
 * @version 1.0.0
 * @since 2025-08-13
 *
 * @see io.github.kushnirvladyslav.Platform
 * @see CLVersion
 */
public class Device {
    private static final Logger logger = LoggerFactory.getLogger(Device.class);

    private final long deviceID;
    private final io.github.kushnirvladyslav.Platform platform;
    private final CLVersion versionCL;

    // Cached basic info
    private final String name;
    private final String vendor;
    private final String driverVersion;
    private final String deviceVersion;
    private final String profile;
    private final String[] extensions;
    private final String deviceType;

    // Cached capabilities
    private final boolean isAvailable;
    private final boolean isCompilerAvailable;
    private final boolean isImageSupport;
    private final int vendorID;

    // Cached compute info
    private final int maxComputeUnits;
    private final int maxClockFrequency;
    private final int maxWorkItemDimensions;
    private final long[] maxWorkItemSizes;
    private final long maxWorkGroupSize;

    // Cached memory info
    private final long maxGlobalBufferSize;
    private final long maxMemAllocSize;
    private final long maxLocalMemSize;
    private final long maxConstantBufferSize;

    /**
     * Creates a new Device instance and initializes all device information.
     *
     * @param platform the platform this device belongs to
     * @param id the OpenCL device ID
     * @throws DeviceInitializationException if device information cannot be retrieved
     */
    public Device(io.github.kushnirvladyslav.Platform platform, long id) {
        logger.debug("Initializing OpenCL device with ID: {} on platform: {}",
                id, platform.getName());

        this.deviceID = id;
        this.platform = platform;

        try {
            // Cache basic info
            this.name = getString(CL_DEVICE_NAME);
            this.vendor = getString(CL_DEVICE_VENDOR);
            this.driverVersion = getString(CL_DRIVER_VERSION);
            this.deviceVersion = getString(CL_DEVICE_VERSION);
            this.profile = getString(CL_DEVICE_PROFILE);

            logger.info("Initialized device: {} (Vendor: {}, Driver: {})",
                    name, vendor, driverVersion);

            // Parse OpenCL version
            if (this.deviceVersion == null) {
                logger.warn("Device version string is null, defaulting to UNKNOWN");
                versionCL = CLVersion.UNKNOWN;
            } else {
                versionCL = CLVersion.getOpenCLVersion(this.deviceVersion);
                logger.debug("Device OpenCL version: {}", versionCL);
            }

            // Cache extensions
            String extString = getString(CL_DEVICE_EXTENSIONS);
            if (extString == null || extString.trim().isEmpty()) {
                logger.debug("No device extensions found");
                this.extensions = new String[0];
            } else {
                this.extensions = extString.trim().split("\\s+");
                logger.debug("Found {} device extensions", extensions.length);
            }

            // Cache device type
            long type = getLong(CL_DEVICE_TYPE);
            List<String> types = new ArrayList<>();
            if ((type & CL_DEVICE_TYPE_CPU) != 0) types.add("CPU");
            if ((type & CL_DEVICE_TYPE_GPU) != 0) types.add("GPU");
            if ((type & CL_DEVICE_TYPE_ACCELERATOR) != 0) types.add("Accelerator");
            this.deviceType = String.join(", ", types);
            logger.debug("Device type: {}", deviceType);

            // Cache capabilities
            this.isAvailable = getBool(CL_DEVICE_AVAILABLE);
            this.isCompilerAvailable = getBool(CL_DEVICE_COMPILER_AVAILABLE);
            this.isImageSupport = getBool(CL_DEVICE_IMAGE_SUPPORT);
            this.vendorID = getInt(CL_DEVICE_VENDOR_ID);

            logger.debug("Device capabilities - Available: {}, Compiler: {}, Image Support: {}",
                    isAvailable, isCompilerAvailable, isImageSupport);

            // Cache compute info
            this.maxComputeUnits = getInt(CL_DEVICE_MAX_COMPUTE_UNITS);
            this.maxClockFrequency = getInt(CL_DEVICE_MAX_CLOCK_FREQUENCY);
            this.maxWorkItemDimensions = getInt(CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
            this.maxWorkItemSizes = getSizeArray(CL_DEVICE_MAX_WORK_ITEM_SIZES, this.maxWorkItemDimensions);
            this.maxWorkGroupSize = getLong(CL_DEVICE_MAX_WORK_GROUP_SIZE);

            logger.debug("Device compute units: {}, Clock: {} MHz",
                    maxComputeUnits, maxClockFrequency);

            // Cache memory info
            this.maxGlobalBufferSize = getLong(CL_DEVICE_GLOBAL_MEM_SIZE);
            this.maxMemAllocSize = getLong(CL_DEVICE_MAX_MEM_ALLOC_SIZE);
            this.maxLocalMemSize = getLong(CL_DEVICE_LOCAL_MEM_SIZE);
            this.maxConstantBufferSize = getLong(CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);

            logger.debug("Device memory - Global: {}, Local: {}, Constant: {}",
                    formatBytes(maxGlobalBufferSize),
                    formatBytes(maxLocalMemSize),
                    formatBytes(maxConstantBufferSize));
        } catch (Exception e) {
            logger.error("Failed to initialize device with ID: {}", id, e);
            throw new DeviceInitializationException("Failed to initialize OpenCL device", e);
        }
    }

    // ---------- PRIVATE HELPER METHODS ----------

    /**
     * Gets an integer value for the specified device info parameter.
     *
     * @param param the parameter to query
     * @return the integer value
     * @throws OpenCLException if the device query fails
     */
    private int getInt(int param) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer buf = stack.mallocInt(1);
            int result = clGetDeviceInfo(deviceID, param, buf, null);
            OpenCLErrorUtils.checkError(result, "Failed to get device integer info");
            return buf.get(0);
        }
    }

    /**
     * Gets a long value for the specified device info parameter.
     *
     * @param param the parameter to query
     * @return the long value
     * @throws OpenCLException if the device query fails
     */
    private long getLong(int param) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer buf = stack.mallocLong(1);
            int result = clGetDeviceInfo(deviceID, param, buf, null);
            OpenCLErrorUtils.checkError(result, "Failed to get device long info");
            return buf.get(0);
        }
    }

    /**
     * Gets a boolean value for the specified device info parameter.
     *
     * @param param the parameter to query
     * @return the boolean value
     * @throws OpenCLException if the device query fails
     */
    private boolean getBool(int param) {
        return getInt(param) == CL_TRUE;
    }

    /**
     * Gets a string value for the specified device info parameter.
     *
     * @param param the parameter to query
     * @return the string value, or null if the query fails
     * @throws OpenCLException if the device query fails
     */
    private String getString(int param) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer sizeBuf = stack.mallocPointer(1);
            int result = clGetDeviceInfo(deviceID, param, (ByteBuffer) null, sizeBuf);
            OpenCLErrorUtils.checkError(result, "Failed to get device info size");

            int size = (int) sizeBuf.get(0);
            ByteBuffer buf = stack.malloc(size);
            result = clGetDeviceInfo(deviceID, param, buf, null);
            OpenCLErrorUtils.checkError(result, "Failed to get device info value");
            return memUTF8(buf, size - 1);
        }
    }

    /**
     * Gets an array of size_t values for the specified device info parameter.
     *
     * @param param the parameter to query
     * @param dim the expected dimension of the array
     * @return array of long values, empty array if query fails or dim <= 0
     * @throws OpenCLException if the device query encounters an unexpected error
     */
    private long[] getSizeArray(int param, int dim) {
        logger.trace("Querying size array parameter: {} with dimension: {}", param, dim);

        if (dim <= 0) {
            logger.debug("Invalid dimension {} for parameter {}, returning empty array", dim, param);
            return new long[0];
        }

        try (MemoryStack stack = stackPush()) {
            PointerBuffer buf = stack.mallocPointer(dim);
            int result = clGetDeviceInfo(deviceID, param, buf, null);
            if (result != CL_SUCCESS) {
                logger.warn("Failed to get size array parameter {}: {} ({})", param,
                        OpenCLErrorUtils.getCLErrorString(result), result);
                return new long[0];
            }

            long[] results = new long[dim];
            for (int i = 0; i < dim; i++) {
                results[i] = buf.get(i);
            }

            logger.trace("Retrieved size array parameter {}: {}", param, java.util.Arrays.toString(results));
            return results;
        } catch (Exception e) {
            logger.error("Exception while querying size array parameter {}", param, e);
            throw new OpenCLException("Failed to retrieve size array device parameter", e);
        }
    }

    // ========== PUBLIC GETTERS (now return cached values) ==========

    /**
     * Returns the device name.
     *
     * @return the device name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the device vendor.
     *
     * @return the device vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * Returns the driver version string.
     *
     * @return the driver version
     */
    public String getDriverVersion() {
        return driverVersion;
    }

    /**
     * Returns the device version string.
     *
     * @return the device version
     */
    public String getDeviceVersion() {
        return deviceVersion;
    }

    /**
     * Returns the parsed OpenCL version for this device.
     *
     * @return the OpenCL version
     */
    public CLVersion getOpenCLVersion() {
        return versionCL;
    }

    /**
     * Returns the device profile (FULL_PROFILE or EMBEDDED_PROFILE).
     *
     * @return the device profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * Returns a copy of the device extensions array.
     *
     * @return array of extension names, empty array if no extensions
     */
    public String[] getExtensions() {
        return extensions.clone(); // Return copy to prevent modification
    }

    /**
     * Returns the device type as a string (CPU, GPU, Accelerator or combination).
     *
     * @return the device type string
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * Returns whether the device is currently available for use.
     *
     * @return true if device is available, false otherwise
     */
    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Returns whether the device has an OpenCL compiler available.
     *
     * @return true if compiler is available, false otherwise
     */
    public boolean isCompilerAvailable() {
        return isCompilerAvailable;
    }

    /**
     * Returns whether the device supports OpenCL images.
     *
     * @return true if image support is available, false otherwise
     */
    public boolean isImageSupport() {
        return isImageSupport;
    }

    /**
     * Returns the vendor ID for this device.
     *
     * @return the vendor ID
     */
    public int getVendorID() {
        return vendorID;
    }

    /**
     * Returns the maximum number of compute units on the device.
     *
     * @return the maximum compute units
     */
    public int getMaxComputeUnits() {
        return maxComputeUnits;
    }

    /**
     * Returns the maximum clock frequency of the device in MHz.
     *
     * @return the maximum clock frequency in MHz
     */
    public int getMaxClockFrequency() {
        return maxClockFrequency;
    }

    /**
     * Returns the maximum dimensions for work items.
     *
     * @return the maximum work item dimensions
     */
    public int getMaxWorkItemDimensions() {
        return maxWorkItemDimensions;
    }

    /**
     * Returns a copy of the maximum work item sizes for each dimension.
     *
     * @return array of maximum work item sizes
     */
    public long[] getMaxWorkItemSizes() {
        return maxWorkItemSizes.clone(); // Return copy to prevent modification
    }

    /**
     * Returns the maximum work group size.
     *
     * @return the maximum work group size
     */
    public long getMaxWorkGroupSize() {
        return maxWorkGroupSize;
    }

    /**
     * Returns the maximum global buffer size in bytes.
     *
     * @return the maximum global buffer size
     */
    public long getMaxGlobalBufferSize() {
        return maxGlobalBufferSize;
    }

    /**
     * Returns the maximum memory allocation size in bytes.
     *
     * @return the maximum memory allocation size
     */
    public long getMaxMemAllocSize() {
        return maxMemAllocSize;
    }

    /**
     * Returns the maximum local memory size in bytes.
     *
     * @return the maximum local memory size
     */
    public long getMaxLocalMemSize() {
        return maxLocalMemSize;
    }

    /**
     * Returns the maximum constant buffer size in bytes.
     *
     * @return the maximum constant buffer size
     */
    public long getMaxConstantBufferSize() {
        return maxConstantBufferSize;
    }

    /**
     * Returns the OpenCL device ID.
     *
     * @return the device ID
     */
    public long getDeviceID() {
        return deviceID;
    }

    /**
     * Returns the platform this device belongs to.
     *
     * @return the platform instance
     */
    public io.github.kushnirvladyslav.Platform getPlatform() {
        return platform;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Checks if the device supports a specific extension.
     *
     * @param extensionName the name of the extension to check
     * @return true if the extension is supported, false otherwise
     * @throws IllegalArgumentException if extensionName is null or empty
     */
    public boolean supportsExtension(String extensionName) {
        if (extensionName == null || extensionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Extension name cannot be null or empty");
        }

        logger.trace("Checking support for extension: {}", extensionName);
        for (String ext : extensions) {
            if (ext.equals(extensionName)) {
                logger.trace("Extension {} is supported", extensionName);
                return true;
            }
        }
        logger.trace("Extension {} is not supported", extensionName);
        return false;
    }

    /**
     * Checks if this device is a CPU device.
     *
     * @return true if device is CPU type, false otherwise
     */
    public boolean isCPU() {
        boolean isCpu = deviceType.contains("CPU");
        logger.trace("Device {} is CPU: {}", name, isCpu);
        return isCpu;
    }

    /**
     * Checks if this device is a GPU device.
     *
     * @return true if device is GPU type, false otherwise
     */
    public boolean isGPU() {
        boolean isGpu = deviceType.contains("GPU");
        logger.trace("Device {} is GPU: {}", name, isGpu);
        return isGpu;
    }

    /**
     * Checks if this device is an accelerator device.
     *
     * @return true if device is accelerator type, false otherwise
     */
    public boolean isAccelerator() {
        boolean isAccel = deviceType.contains("Accelerator");
        logger.trace("Device {} is Accelerator: {}", name, isAccel);
        return isAccel;
    }

    /**
     * Checks if this device belongs to the specified platform.
     *
     * @param platform the platform to check against
     * @return true if the device belongs to the platform, false otherwise
     * @throws IllegalArgumentException if platform is null
     */
    public boolean belongTo(Platform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("Platform cannot be null");
        }

        boolean belongs = this.platform.equals(platform);
        logger.trace("Device {} belongs to platform {}: {}", name, platform.getName(), belongs);
        return belongs;
    }

    // ========== INFORMATION DISPLAY ==========

    /**
     * Returns a formatted summary of device memory information.
     *
     * @return memory information summary string
     */
    public String getMemorySummary() {
        logger.trace("Generating memory summary for device: {}", name);

        StringBuilder sb = new StringBuilder();
        sb.append("Memory Information:\n");
        sb.append("  Global memory: ").append(getFormattedGlobalMemSize()).append("\n");
        sb.append("  Local memory: ").append(formatBytes(maxLocalMemSize)).append("\n");
        sb.append("  Max allocation: ").append(formatBytes(maxMemAllocSize)).append("\n");
        sb.append("  Constant buffer: ").append(formatBytes(maxConstantBufferSize)).append("\n");
        return sb.toString();
    }

    /**
     * Returns a formatted summary of device compute capabilities.
     *
     * @return compute information summary string
     */
    public String getComputeSummary() {
        logger.trace("Generating compute summary for device: {}", name);

        StringBuilder sb = new StringBuilder();
        sb.append("Compute Information:\n");
        sb.append("  Compute units: ").append(maxComputeUnits).append("\n");
        sb.append("  Clock frequency: ").append(maxClockFrequency).append(" MHz\n");
        sb.append("  Max work group size: ").append(maxWorkGroupSize).append("\n");

        if (maxWorkItemSizes.length > 0) {
            sb.append("  Max work item sizes: [");
            for (int i = 0; i < maxWorkItemSizes.length; i++) {
                sb.append(maxWorkItemSizes[i]);
                if (i < maxWorkItemSizes.length - 1) sb.append(", ");
            }
            sb.append("]\n");
        }

        return sb.toString();
    }

    /**
     * Returns a formatted summary of device capabilities.
     *
     * @return capabilities summary string
     */
    public String getCapabilitiesSummary() {
        logger.trace("Generating capabilities summary for device: {}", name);

        StringBuilder sb = new StringBuilder();
        sb.append("Capabilities:\n");
        sb.append("  Device type: ").append(deviceType).append("\n");
        sb.append("  Available: ").append(isAvailable ? "Yes" : "No").append("\n");
        sb.append("  Compiler available: ").append(isCompilerAvailable ? "Yes" : "No").append("\n");
        sb.append("  Image support: ").append(isImageSupport ? "Yes" : "No").append("\n");
        return sb.toString();
    }

    /**
     * Returns a formatted summary of device extensions.
     *
     * @return extension summary string
     */
    public String getExtensionSummary() {
        logger.trace("Generating extension summary for device: {}", name);
        if (extensions.length == 0) {
            return "Extensions: none";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Extensions (").append(extensions.length).append("):\n");
        for (String ext : extensions) {
            sb.append("  - ").append(ext).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns the formatted global memory size as a human-readable string.
     *
     * @return formatted global memory size
     */
    public String getFormattedGlobalMemSize() {
        return formatBytes(maxGlobalBufferSize);
    }

    /**
     * Formats a byte size into a human-readable string.
     *
     * @param bytes the number of bytes
     * @return a formatted string representation of the size
     */
    private String formatBytes(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Returns a comprehensive string representation of this device.
     *
     * @return formatted device information
     */
    @Override
    public String toString() {
        logger.trace("Generating string representation for device: {}", name);

        StringBuilder sb = new StringBuilder();
        sb.append("=== OpenCL Device ===\n");
        sb.append("Name: ").append(getName()).append("\n");
        sb.append("Vendor: ").append(getVendor()).append("\n");
        sb.append("Driver version: ").append(getDriverVersion()).append("\n");
        sb.append("OpenCL version: ").append(getOpenCLVersion()).append("\n");

        sb.append("\n").append(getCapabilitiesSummary());
        sb.append("\n").append(getComputeSummary());
        sb.append("\n").append(getMemorySummary());
        sb.append("\n").append(getExtensionSummary());

        return sb.toString();
    }

    /**
     * Compares this device with another object for equality.
     * Two devices are considered equal if they have the same device ID.
     *
     * @param obj the object to compare with
     * @return true if the devices are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            logger.trace("Device equals: same object reference");
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            logger.trace("Device equals: null or different class");
            return false;
        }
        Device device = (Device) obj;
        boolean isEqual = deviceID == device.deviceID;
        logger.trace("Device equals: comparing IDs {} and {} - result: {}",
                deviceID, device.deviceID, isEqual);
        return isEqual;
    }

    /**
     * Returns the hash code for this device based on its device ID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int hash = Long.hashCode(deviceID);
        logger.trace("Device hashCode for ID {}: {}", deviceID, hash);
        return hash;
    }
}
