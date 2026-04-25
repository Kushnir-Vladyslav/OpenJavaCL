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

import io.github.kushnirvladyslav.exceptions.OpenCLException;
import io.github.kushnirvladyslav.exceptions.PlatformInitializationException;
import io.github.kushnirvladyslav.util.CLVersion;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.BufferUtils;

import java.nio.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opencl.CL10.*;
import static org.lwjgl.opencl.CL21.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an OpenCL platform with its devices and capabilities.
 * This class provides access to platform information and manages device discovery.
 * All platform information is cached during initialization to improve performance.
 *
 * <p>Platform properties that are cached include:
 * <ul>
 *     <li>Basic information (name, vendor, version, profile)</li>
 *     <li>OpenCL version and supported extensions</li>
 *     <li>Available devices (CPU, GPU, Accelerator)</li>
 *     <li>Host timer resolution (for OpenCL 2.1+)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Platform platform = OpenCL.getPlatforms().get(0);
 * System.out.println("Platform name: " + platform.getName());
 *
 * if (platform.hasGPUDevices()) {
 *     Device gpu = platform.getBestGPUDevice();
 *     // Use GPU device
 * }
 * }</pre>
 *
 * @author Vladyslav Kushnir
 * @version 1.0.0
 * @since 2025-08-13
 *
 * @see Device
 * @see CLVersion
 */
public class Platform {
    private static final Logger logger = LoggerFactory.getLogger(Platform.class);

    private final long platformID;
    private final CLVersion versionCL;

    // Cached device lists
    private final List<Device> allDevices;
    private final List<Device> cpuDevices;
    private final List<Device> gpuDevices;
    private final List<Device> acceleratorDevices;

    // Cached platform info
    private final String name;
    private final String vendor;
    private final String platformVersion;
    private final String profile;
    private final String[] extensions;
    private final long hostTimerResolution;

    // Device counts (cached)
    private final int totalDeviceCount;
    private final int cpuDeviceCount;
    private final int gpuDeviceCount;
    private final int acceleratorDeviceCount;

    /**
     * Creates a new Platform instance and initializes all platform information.
     *
     * @param id the OpenCL platform ID
     * @throws PlatformInitializationException if platform information cannot be retrieved
     */
    Platform(long id) {
        logger.debug("Initializing OpenCL platform with ID: {}", id);
        this.platformID = id;

        try {
            // Cache all platform information immediately
            this.name = getString(CL_PLATFORM_NAME);
            this.vendor = getString(CL_PLATFORM_VENDOR);
            this.platformVersion = getString(CL_PLATFORM_VERSION);
            this.profile = getString(CL_PLATFORM_PROFILE);

            logger.info("Initialized platform: {} (Vendor: {})", name, vendor);

            // Parse OpenCL version
            if (this.platformVersion == null) {
                logger.warn("Platform version string is null, defaulting to UNKNOWN");
                versionCL = CLVersion.UNKNOWN;
            } else {
                versionCL = CLVersion.getOpenCLVersion(this.platformVersion);
                logger.debug("Platform OpenCL version: {}", versionCL);
            }

            // Cache extensions
            String extString = getString(CL_PLATFORM_EXTENSIONS);
            if (extString == null || extString.trim().isEmpty()) {
                logger.debug("No platform extensions found");
                this.extensions = new String[0];
            } else {
                this.extensions = extString.trim().split("\\s+");
                logger.debug("Found {} platform extensions", extensions.length);
            }

            // Cache host timer resolution
            long timerResolution;
            if (versionCL.isAtLeast(CLVersion.OPENCL_2_1)) {
                try {
                    timerResolution = getLong(CL_PLATFORM_HOST_TIMER_RESOLUTION);
                    logger.debug("Host timer resolution: {} ns", timerResolution);
                } catch (Exception e) {
                    logger.warn("Failed to get host timer resolution", e);
                    timerResolution = -1;
                }
            } else {
                logger.debug("Host timer resolution not supported (OpenCL < 2.1)");
                timerResolution = -1;
            }

            // Cache device counts
            logger.debug("Starting device discovery for platform: {}", name);
            this.hostTimerResolution = timerResolution;
            this.totalDeviceCount = getDeviceCountDirect(CL_DEVICE_TYPE_ALL);
            this.cpuDeviceCount = getDeviceCountDirect(CL_DEVICE_TYPE_CPU);
            this.gpuDeviceCount = getDeviceCountDirect(CL_DEVICE_TYPE_GPU);
            this.acceleratorDeviceCount = getDeviceCountDirect(CL_DEVICE_TYPE_ACCELERATOR);

            logger.info("Found devices - Total: {}, CPU: {}, GPU: {}, Accelerator: {}",
                    totalDeviceCount, cpuDeviceCount, gpuDeviceCount, acceleratorDeviceCount);


            // Cache device lists
            this.allDevices = getDeviceDirect(getDeviceIDs(CL_DEVICE_TYPE_ALL));
            this.cpuDevices = getDeviceDirect(getDeviceIDs(CL_DEVICE_TYPE_CPU));
            this.gpuDevices = getDeviceDirect(getDeviceIDs(CL_DEVICE_TYPE_GPU));
            this.acceleratorDevices = getDeviceDirect(getDeviceIDs(CL_DEVICE_TYPE_ACCELERATOR));

        } catch (Exception e) {
            logger.error("Failed to initialize platform with ID: {}", id, e);
            throw new PlatformInitializationException("Failed to initialize OpenCL platform", e);
        }
    }

    // ---------- PRIVATE HELPER METHODS ----------

    /**
     * Gets a string value for the specified platform info parameter.
     *
     * @param param the parameter to query
     * @return the string value, or null if the query fails
     * @throws OpenCLException if the platform query fails
     */
    private String getString(int param) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer sizeBuf = stack.mallocPointer(1);
            int result = clGetPlatformInfo(platformID, param, (ByteBuffer) null, sizeBuf);
            OpenCLErrorUtils.checkError(result, "Failed to get platform info size");

            int size = (int) sizeBuf.get(0);
            ByteBuffer buf = stack.malloc(size);
            result = clGetPlatformInfo(platformID, param, buf, null);
            OpenCLErrorUtils.checkError(result, "Failed to get platform info value");

            return memUTF8(buf, size - 1);
        }
    }

    /**
     * Gets a long value for the specified platform info parameter.
     *
     * @param param the parameter to query
     * @return the long value, or -1 if the query fails
     * @throws OpenCLException if the platform query fails
     */
    private long getLong(int param) {
        logger.debug("Querying long parameter: {}", param);
        try (MemoryStack stack = stackPush()) {
            LongBuffer buf = stack.mallocLong(1);
            int result = clGetPlatformInfo(platformID, param, buf, null);
            if (result != CL_SUCCESS) {
                logger.warn("Failed to get long parameter {}: {} ({})", param,
                        OpenCLErrorUtils.getCLErrorString(result), result);
                return -1;
            }
            long value = buf.get(0);
            logger.debug("Retrieved long parameter {}: {}", param, value);
            return value;
        } catch (Exception e) {
            logger.error("Exception while querying long parameter {}", param, e);
            throw new OpenCLException("Failed to retrieve long platform parameter", e);
        }
    }

    /**
     * Creates Device instances from a list of device IDs.
     *
     * @param devicesID list of device IDs to convert
     * @return unmodifiable list of Device instances
     * @throws OpenCLException if device creation fails
     */
    private List<Device> getDeviceDirect(List<Long> devicesID) {
        logger.debug("Creating {} device instances", devicesID.size());
        List<Device> deviceList = new ArrayList<>();
        try {
            for (Long deviceID : devicesID) {
                logger.trace("Creating device instance for ID: {}", deviceID);
                deviceList.add(new Device(this, deviceID));
            }
            logger.debug("Successfully created {} device instances", deviceList.size());
            return Collections.unmodifiableList(deviceList);
        } catch (Exception e) {
            logger.error("Failed to create device instances", e);
            throw new OpenCLException("Failed to create device instances", e);
        }
    }

    /**
     * Retrieves device IDs for the specified device type.
     *
     * @param deviceType the OpenCL device type constant
     * @return list of device IDs, empty if none found or query fails
     * @throws OpenCLException if the device query encounters an unexpected error
     */
    private List<Long> getDeviceIDs(int deviceType) {
        logger.debug("Querying device IDs for type: {}", deviceType);

        try {
            IntBuffer numDevices = BufferUtils.createIntBuffer(1);
            int result = clGetDeviceIDs(platformID, deviceType, null, numDevices);

            if (result == CL_DEVICE_NOT_FOUND) {
                logger.debug("No devices found for type: {}", deviceType);
                return new ArrayList<>();
            }

            if (result != CL_SUCCESS || numDevices.get(0) == 0) {
                logger.warn("Device query failed for type {}: {} ({})", deviceType,
                        OpenCLErrorUtils.getCLErrorString(result), result);
                return new ArrayList<>();
            }

            int deviceCount = numDevices.get(0);
            logger.debug("Found {} devices for type: {}", deviceCount, deviceType);

            PointerBuffer devices = BufferUtils.createPointerBuffer(deviceCount);
            result = clGetDeviceIDs(platformID, deviceType, devices, (IntBuffer) null);

            if (result != CL_SUCCESS) {
                logger.warn("Failed to retrieve device IDs for type {}: {} ({})", deviceType,
                        OpenCLErrorUtils.getCLErrorString(result), result);
                return new ArrayList<>();
            }

            List<Long> deviceList = new ArrayList<>();
            for (int i = 0; i < devices.capacity(); i++) {
                long deviceID = devices.get(i);
                deviceList.add(deviceID);
                logger.trace("Found device ID: {}", deviceID);
            }

            logger.debug("Successfully retrieved {} device IDs for type: {}", deviceList.size(), deviceType);
            return deviceList;
        } catch (Exception e) {
            logger.error("Exception while querying device IDs for type: {}", deviceType, e);
            throw new OpenCLException("Failed to query device IDs", e);
        }
    }

    /**
     * Gets the count of devices for the specified device type.
     *
     * @param deviceType the OpenCL device type constant
     * @return number of devices, 0 if none found or query fails
     */
    private int getDeviceCountDirect(int deviceType) {
        logger.debug("Counting devices for type: {}", deviceType);
        try {
            IntBuffer numDevices = BufferUtils.createIntBuffer(1);
            int result = clGetDeviceIDs(platformID, deviceType, null, numDevices);

            if (result == CL_DEVICE_NOT_FOUND) {
                logger.debug("No devices found for type: {}", deviceType);
                return 0;
            }

            if (result != CL_SUCCESS) {
                logger.warn("Device count query failed for type {}: {} ({})", deviceType,
                        OpenCLErrorUtils.getCLErrorString(result), result);
                return 0;
            }

            int count = numDevices.get(0);
            logger.debug("Device count for type {}: {}", deviceType, count);
            return count;
        }  catch (Exception e) {
            logger.error("Exception while counting devices for type: {}", deviceType, e);
            return 0;
        }
    }

    // ========== PUBLIC GETTERS (now return cached values) ==========

    /**
     * Returns the platform name.
     *
     * @return the platform name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the platform vendor.
     *
     * @return the platform vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * Returns the platform version string.
     *
     * @return the platform version string
     */
    public String getPlatformVersion() {
        return platformVersion;
    }

    /**
     * Returns the parsed OpenCL version for this platform.
     *
     * @return the OpenCL version
     */
    public CLVersion getOpenCLVersion() {
        return versionCL;
    }

    /**
     * Returns the platform profile (FULL_PROFILE or EMBEDDED_PROFILE).
     *
     * @return the platform profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * Returns a copy of the platform extensions array.
     *
     * @return array of extension names, empty array if no extensions
     */
    public String[] getExtensions() {
        return extensions.clone(); // Return copy to prevent modification
    }

    /**
     * Returns the host timer resolution in nanoseconds.
     * Only available for OpenCL 2.1 and later.
     *
     * @return host timer resolution in nanoseconds, or -1 if not available
     */
    public long getHostTimerResolution() {
        return hostTimerResolution;
    }

    /**
     * Returns the OpenCL platform ID.
     *
     * @return the platform ID
     */
    public long getPlatformID() {
        return platformID;
    }

    // ========== DEVICE METHODS (now return cached values) ==========

    /**
     * Returns a list of all available devices on this platform.
     *
     * @return list of all devices
     */
    public List<Device> getAllDevices() {
        return new ArrayList<>(allDevices);
    }

    /**
     * Returns a list of CPU devices available on this platform.
     *
     * @return list of CPU devices
     */
    public List<Device> getCPUDevices() {
        return new ArrayList<>(cpuDevices);
    }

    /**
     * Returns a list of GPU devices available on this platform.
     *
     * @return list of GPU devices
     */
    public List<Device> getGPUDevices() {
        return new ArrayList<>(gpuDevices);
    }

    /**
     * Returns a list of accelerator devices available on this platform.
     *
     * @return list of accelerator devices
     */
    public List<Device> getAcceleratorDevices() {
        return new ArrayList<>(acceleratorDevices);
    }

    /**
     * Returns the total number of devices on this platform.
     *
     * @return total device count
     */
    public int getTotalDeviceCount() {
        return totalDeviceCount;
    }

    /**
     * Returns the number of CPU devices on this platform.
     *
     * @return CPU device count
     */
    public int getCPUDeviceCount() {
        return cpuDeviceCount;
    }

    /**
     * Returns the number of GPU devices on this platform.
     *
     * @return GPU device count
     */
    public int getGPUDeviceCount() {
        return gpuDeviceCount;
    }

    /**
     * Returns the number of accelerator devices on this platform.
     *
     * @return accelerator device count
     */
    public int getAcceleratorDeviceCount() {
        return acceleratorDeviceCount;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Checks if this platform has any GPU devices.
     *
     * @return true if GPU devices are available, false otherwise
     */
    public boolean hasGPUDevices() {
        return gpuDeviceCount > 0;
    }

    /**
     * Checks if this platform has any CPU devices.
     *
     * @return true if CPU devices are available, false otherwise
     */
    public boolean hasCPUDevices() {
        return cpuDeviceCount > 0;
    }

    /**
     * Checks if this platform has any accelerator devices.
     *
     * @return true if accelerator devices are available, false otherwise
     */
    public boolean hasAcceleratorDevices() {
        return acceleratorDeviceCount > 0;
    }

    /**
     * Checks if this platform supports OpenCL 2.1 or later.
     *
     * @return true if OpenCL 2.1+ is supported, false otherwise
     */
    public boolean supportsOpenCL21() {
        return versionCL.isAtLeast(versionCL.OPENCL_2_1);
    }

    /**
     * Checks if the platform supports a specific extension.
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
     * Returns the first (typically best) GPU device on this platform.
     *
     * @return the best GPU device, or null if no GPU devices are available
     */
    public Device getBestGPUDevice() {
        if (gpuDevices.isEmpty()) {
            logger.debug("No GPU devices available");
            return null;
        }
        Device bestGPU = gpuDevices.get(0);
        logger.debug("Best GPU device: {}", bestGPU.getName());
        return bestGPU;
    }

    /**
     * Returns the best available device on this platform.
     * Priority order: GPU > CPU > Accelerator.
     *
     * @return the best device, or null if no devices are available
     */
    public Device getBestDevice() {
        // Priority: GPU > CPU > Accelerator
        if (!gpuDevices.isEmpty()) {
            Device best = gpuDevices.get(0);
            logger.debug("Best device selected: GPU - {}", best.getName());
            return best;
        }
        if (!cpuDevices.isEmpty()) {
            Device best = cpuDevices.get(0);
            logger.debug("Best device selected: CPU - {}", best.getName());
            return best;
        }
        if (!acceleratorDevices.isEmpty()) {
            Device best = acceleratorDevices.get(0);
            logger.debug("Best device selected: Accelerator - {}", best.getName());
            return best;
        }

        logger.warn("No devices available on platform");
        return null;
    }

    /**
     * Checks if the specified device belongs to this platform.
     *
     * @param device the device to check
     * @return true if the device belongs to this platform, false otherwise
     * @throws IllegalArgumentException if device is null
     */
    public boolean owns(Device device) {
        if (device == null) {
            throw new IllegalArgumentException("Device cannot be null");
        }

        boolean owns = allDevices.contains(device);
        logger.trace("Platform {} device: {}", owns ? "owns" : "does not own", device.getName());
        return owns;
    }

    // ========== INFORMATION DISPLAY ==========

    /**
     * Returns a formatted summary of devices on this platform.
     *
     * @return device summary string
     */
    public String getDeviceSummary() {
        logger.trace("Generating device summary");

        StringBuilder sb = new StringBuilder();
        sb.append("Devices on platform:\n");

        if (cpuDeviceCount > 0) sb.append("  CPU: ").append(cpuDeviceCount).append(" device(s)\n");
        if (gpuDeviceCount > 0) sb.append("  GPU: ").append(gpuDeviceCount).append(" device(s)\n");
        if (acceleratorDeviceCount > 0) sb.append("  Accelerator: ").append(acceleratorDeviceCount).append(" device(s)\n");

        if (totalDeviceCount == 0) {
            sb.append("  No available devices\n");
        }

        return sb.toString();
    }

    /**
     * Returns a formatted summary of platform extensions.
     *
     * @return extension summary string
     */
    public String getExtensionSummary() {
        logger.trace("Generating extension summary");

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
     * Returns a comprehensive string representation of this platform.
     *
     * @return formatted platform information
     */
    @Override
    public String toString() {
        logger.trace("Generating platform string representation");

        StringBuilder sb = new StringBuilder();
        sb.append("=== OpenCL Platform ===\n");
        sb.append("Name: ").append(getName()).append("\n");
        sb.append("Vendor: ").append(getVendor()).append("\n");
        sb.append("Version: ").append(getPlatformVersion()).append("\n");
        sb.append("Profile: ").append(getProfile()).append("\n");

        if (hostTimerResolution != -1) {
            sb.append("Host timer resolution: ").append(hostTimerResolution).append(" ns\n");
        }

        sb.append("\n").append(getDeviceSummary());
        sb.append("\n").append(getExtensionSummary());

        return sb.toString();
    }
}
