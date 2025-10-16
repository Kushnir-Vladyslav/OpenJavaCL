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

import io.github.kushnirvladyslav.exceptions.ContextCreationException;
import io.github.kushnirvladyslav.exceptions.DeviceNotFoundException;
import io.github.kushnirvladyslav.exceptions.OpenCLException;
import io.github.kushnirvladyslav.util.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL20;
import org.lwjgl.opencl.KHRPriorityHints;
import org.lwjgl.opencl.KHRThrottleHints;
import org.lwjgl.opencl.KHRInitializeMemory;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import static com.jopencl.util.OpenCLErrorUtils.*;

/**
 * Builder class for creating OpenCL contexts with specific configurations.
 * Provides a fluent interface for setting various context and command queue properties.
 *
 * <p>This implementation currently supports single-device contexts with basic OpenCL functionality.
 * Future versions will include:
 * <ul>
 *     <li>Multi-device context support for parallel computation across multiple devices</li>
 *     <li>Interoperability with other APIs (OpenGL, DirectX, Vulkan)</li>
 *     <li>Other basic functionality/li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ClContext context = OpenCL.createContext()
 *     .withDevice(device)
 *     .withOutOfOrderQueue(true)
 *     .withProfiling(true)
 *     .create();
 * }</pre>
 *
 * @author Vladyslav Kushnir
 * @version 1.0.0
 * @since 2025-08-13
 *
 * @see ClContext
 * @see Platform
 * @see Device
 */
public class ContextBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ContextBuilder.class);

    //context
    private Platform platform = null;
    private Device device = null;

    private boolean isInitialize = false;

    //queue
    private boolean outOfOrder = false;
    private boolean profiling = false;

    private long priority = KHRPriorityHints.CL_QUEUE_PRIORITY_MED_KHR;
    private long energyConsumption = KHRThrottleHints.CL_QUEUE_THROTTLE_MED_KHR;

    private boolean deviceQueue = false;
    private long deviceQueueSize = 0L;

    /**
     * Sets memory initialization flag for the context.
     *
     * @param isInitialize whether to initialize memory on context creation
     * @return this builder instance
     */
    public ContextBuilder withMemoryInitialization (boolean isInitialize) {
        this.isInitialize = isInitialize;
        logger.debug("Setting memory initialization to: {}", isInitialize);
        return this;
    }

    /**
     * Selects the best available device from the given platform.
     *
     * @param platform the platform to select device from
     * @return this builder instance
     * @throws DeviceNotFoundException if no suitable device is found
     */
    public ContextBuilder withBestDevice (Platform platform) {
        logger.debug("Selecting best device from platform: {}", platform.getName());
        this.platform = platform;
        this.device = platform.getBestDevice();
        if (device == null) {
            throw new DeviceNotFoundException("No suitable device found on platform: " + platform.getName());
        }
        logger.debug("Selected device: {}", device.getName());
        return this;
    }

    /**
     * Selects the best CPU device from the given platform.
     *
     * @param platform the platform to select CPU device from
     * @return this builder instance
     * @throws DeviceNotFoundException if no CPU device is found
     */
    public ContextBuilder withBestCPUDevice (Platform platform) {
        logger.debug("Selecting best CPU device from platform: {}", platform.getName());
        this.platform = platform;
        List<Device> devices = platform.getCPUDevices();
        if (devices == null || devices.isEmpty()) {
            throw new DeviceNotFoundException("No CPU devices found on platform: " + platform.getName());
        }
        this.device = devices.get(0);
        logger.debug("Selected CPU device: {}", device.getName());
        return this;
    }

    /**
     * Selects the best GPU device from the given platform.
     *
     * @param platform the platform to select GPU device from
     * @return this builder instance
     * @throws DeviceNotFoundException if no GPU device is found
     */
    public ContextBuilder withBestGPUDevice (Platform platform) {
        logger.debug("Selecting best GPU device from platform: {}", platform.getName());
        this.platform = platform;
        List<Device> devices = platform.getGPUDevices();
        if (devices == null || devices.isEmpty()) {
            throw new DeviceNotFoundException("No GPU devices found on platform: " + platform.getName());
        }
        this.device = devices.get(0);
        logger.debug("Selected GPU device: {}", device.getName());
        return this;
    }

    /**
     * Selects the best Accelerator device from the given platform.
     *
     * @param platform the platform to select Accelerator device from
     * @return this builder instance
     * @throws DeviceNotFoundException if no Accelerator device is found
     */
    public ContextBuilder withBestAcceleratorDevice (Platform platform) {
        logger.debug("Selecting best Accelerator device from platform: {}", platform.getName());
        this.platform = platform;
        List<Device> devices = platform.getAcceleratorDevices();
        if (devices == null || devices.isEmpty()) {
            throw new DeviceNotFoundException("No Accelerator devices found on platform: " + platform.getName());
        }
        this.device = devices.get(0);
        logger.debug("Selected Accelerator device: {}", device.getName());
        return this;
    }

    /**
     * Sets the specified device for context creation.
     *
     * @param device the device to use
     * @return this builder instance
     * @throws IllegalArgumentException if device is null
     */
    public ContextBuilder withDevice (Device device) {
        if (device == null) {
            throw new IllegalArgumentException("Device cannot be null");
        }
        this.device = device;
        this.platform = device.getPlatform();
        logger.debug("Set device: {} from platform: {}", device.getName(), platform.getName());
        return this;
    }

    /**
     * Enables or disables out-of-order execution for command queues.
     *
     * @param outOfOrder true to enable out-of-order execution
     * @return this builder instance
     */
    public ContextBuilder withOutOfOrderQueue (boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
        return this;
    }

    /**
     * Enables or disables profiling for command queues.
     *
     * @param profiling true to enable profiling
     * @return this builder instance
     */
    public ContextBuilder withProfiling (boolean profiling) {
        this.profiling = profiling;
        logger.debug("Setting out-of-order execution to: {}", outOfOrder);
        return this;
    }

    /**
     * Setting the task execution priority
     *
     * @param level task priority
     * @return this builder instance
     */
    public ContextBuilder withPriority(Level level) {
        switch (level) {
            case LOW:
                this.priority = KHRPriorityHints.CL_QUEUE_PRIORITY_LOW_KHR;
                break;
            case MED:
                this.priority = KHRPriorityHints.CL_QUEUE_PRIORITY_MED_KHR;
                break;
            case HIGH:
                this.priority = KHRPriorityHints.CL_QUEUE_PRIORITY_HIGH_KHR;
                break;
        }
        return this;
    }

    /**
     * Setting the power consumption when performing a task
     *
     * @param level power consumption
     * @return this builder instance
     */
    public ContextBuilder withEnergyConsumption(Level level) {
        switch (level) {
            case LOW:
                this.energyConsumption = KHRThrottleHints.CL_QUEUE_THROTTLE_LOW_KHR;
                break;
            case MED:
                this.energyConsumption = KHRThrottleHints.CL_QUEUE_THROTTLE_MED_KHR;
                break;
            case HIGH:
                this.energyConsumption = KHRThrottleHints.CL_QUEUE_THROTTLE_HIGH_KHR;
                break;
        }
        return this;
    }

    /**
     * Enables or disables device command queues, and set its size.
     *
     * @param additionalDeviceQueue true to enable device command queue
     * @param deviceQueueSize size of the device command queue
     * @throws IllegalArgumentException if deviceQueueSize is less than or equal to 0
     * @return this builder instance
     */
    public ContextBuilder withAdditionalDeviceQueue (boolean additionalDeviceQueue, long deviceQueueSize) {
        if(deviceQueueSize <= 0) {
            logger.error("The device queue size has an invalid value: {}", deviceQueueSize);
            throw new IllegalArgumentException("The device queue size must be greater than 0.");
        }

        this.deviceQueue = additionalDeviceQueue;
        this.deviceQueueSize = deviceQueueSize;
        return this;
    }

    /**
     * Creates an OpenCL context with the configured properties.
     *
     * @return newly created OpenCL context
     * @throws ContextCreationException if context creation fails
     * @throws DeviceNotFoundException if no device was specified or device is unavailable
     */
    public ClContext create () {
        logger.debug("Starting context creation");
        validate();

        OpenCL.start();

        long context = 0L;
        long commandQueue = 0L;
        long deviceCommandQueue = 0L;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            context = createContext(stack);
            logger.debug("Created OpenCL context");

            commandQueue = createCommandQueue(stack, context, device);
            logger.debug("Created command queue");

            if(deviceQueue && device.getOpenCLVersion().isAtLeast(CLVersion.OPENCL_2_0)){
                deviceCommandQueue = createCommandQueueCL20(stack, context, device, true);
                logger.debug("Created device queue");
            }

            ClContext contextCL = new ClContext(
                    platform,
                    device,
                    outOfOrder,
                    context,
                    commandQueue,
                    deviceCommandQueue,
                    deviceQueueSize
            );

            OpenCL.registrationContext(contextCL);
            logger.info("Successfully created and registered OpenCL context");
            return contextCL;

        } catch (Exception e) {
            cleanup(context, Arrays.asList(commandQueue, deviceCommandQueue));
            throw new ContextCreationException("Failed to create OpenCL context", e);
        }
    }

    /**
     * Checking resources and whether the device supports the necessary extension.
     *
     * @throws DeviceNotFoundException if no device was specified or device is unavailable
     */
    private void validate() {
        if (device == null || !device.isAvailable()) {
            throw new DeviceNotFoundException("No OpenCL device specified for context creation.");
        }
        logger.debug("Configuration validation passed");
    }

    /**
     * Creating CL.OpenCL context.
     *
     * @return newly created CL.OpenCL context
     * @param stack MemoryStack object
     * @throws OpenCLException if context creating failed
     */
    private long createContext(MemoryStack stack) {
        PointerBuffer contextProperties = createContextProperties(stack);
        IntBuffer errorCode = stack.mallocInt(1);

        long context = CL10.clCreateContext(
                contextProperties,
                device.getDeviceID(),
                null,
                0L,
                errorCode
        );

        OpenCLErrorUtils.checkError(errorCode.get(0), "Context creation");
        return context;
    }

    /**
     * Checks which version of OpenCL the device supports and chooses the appropriate method for creating a command queue.
     *
     * @return newly created CL.OpenCL context
     * @param stack MemoryStack object
     * @param context the context in which the queue is created
     * @param device device for which the queue is created
     * @throws OpenCLException if context creating failed
     */
    private long createCommandQueue(MemoryStack stack, long context, Device device) {
        if (device.getOpenCLVersion().isAtLeast(CLVersion.OPENCL_2_0)) {
            return createCommandQueueCL20(stack, context, device, false);
        } else {
            return createCommandQueueCL10(stack, context, device);
        }
    }

    /**
     * Creating a command queue for devices that support OpenCL versions up to 2.0.
     *
     * @return newly created CL.OpenCL command queue
     * @param stack MemoryStack object
     * @param context the context in which the queue is created
     * @param device device for which the queue is created
     * @throws OpenCLException if command queue creating failed
     */
    private long createCommandQueueCL10(MemoryStack stack, long context, Device device) {
        long queueProperties = 0L;
        if (outOfOrder) {
            queueProperties |= CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        if (profiling) {
            queueProperties |= CL10.CL_QUEUE_PROFILING_ENABLE;
        }
        IntBuffer errorCode = stack.mallocInt(1);

        long commandQueue = CL10.clCreateCommandQueue(
                context,
                device.getDeviceID(),
                queueProperties,
                errorCode
        );

        OpenCLErrorUtils.checkError(errorCode.get(0), "Command queue creation");
        return commandQueue;
    }

    /**
     * Creating a command queue for devices that support OpenCL version 2.0 and higher.
     *
     * @return newly created CL.OpenCL command queue
     * @param stack MemoryStack object
     * @param context the context in which the queue is created
     * @param device device for which the queue is created
     * @param onDevice queue for host or for device
     * @throws OpenCLException if command queue creating failed
     */
    private long createCommandQueueCL20(MemoryStack stack, long context, Device device, boolean onDevice) {
        LongBuffer queueProperties = createQueueProperties(stack, onDevice);
        IntBuffer errorCode = stack.mallocInt(1);

        long commandQueue = CL20.clCreateCommandQueueWithProperties(
                context,
                device.getDeviceID(),
                queueProperties,
                errorCode
        );

        OpenCLErrorUtils.checkError(errorCode.get(0), "Command queue creation");
        return commandQueue;
    }

    /**
     * Completing context properties according to user-specified ones.
     *
     * @return buffer with properties
     * @param stack MemoryStack object
     */
    private PointerBuffer createContextProperties (MemoryStack stack) {
        List<Long> properties = new ArrayList<>();

        properties.add((long) CL10.CL_CONTEXT_PLATFORM);
        properties.add(platform.getPlatformID());

        if(isInitialize) {
            properties.add((long) KHRInitializeMemory.CL_CONTEXT_MEMORY_INITIALIZE_KHR);
            properties.add((long) CL10.CL_TRUE);
        }

        properties.add(0L);

        PointerBuffer propertiesBuffer = stack.mallocPointer(properties.size());
        for (long property : properties) {
            propertiesBuffer.put(property);
        }

        return propertiesBuffer.flip();
    }

    /**
     * Completing context properties according to user-specified ones.
     *
     * @return buffer with properties
     * @param stack MemoryStack object
     * @param onDevice queue for host or for device
     */
    private LongBuffer createQueueProperties (MemoryStack stack, boolean onDevice) {
        List<Long> properties = new ArrayList<>();

        long queueProperties = 0L;
        if (onDevice) {
            queueProperties |= CL20.CL_QUEUE_ON_DEVICE;
        }
        if (outOfOrder) {
            queueProperties |= CL10.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
        }
        if (profiling) {
            queueProperties |= CL10.CL_QUEUE_PROFILING_ENABLE;
        }

        if (queueProperties != 0L) {
            properties.add((long) CL20.CL_QUEUE_PROPERTIES);
            properties.add(queueProperties);
        }

        if (device.supportsExtension("cl_khr_priority_hints")) {
            properties.add((long) KHRPriorityHints.CL_QUEUE_PRIORITY_KHR);
            properties.add(priority);
        }

        if (device.supportsExtension("cl_khr_throttle_hints")) {
            properties.add((long) KHRThrottleHints.CL_QUEUE_THROTTLE_KHR);
            properties.add(energyConsumption);
        }

        properties.add(0L);

        LongBuffer propertiesBuffer = stack.mallocLong(properties.size());
        for (long property : properties) {
            propertiesBuffer.put(property);
        }

        return (LongBuffer) propertiesBuffer.flip();
    }

    /**
     * Release of allocated resources in case of errors.
     *
     * @param context the context in which the queue is created
     * @param commandQueues list of created command queues
     */
    private void cleanup(long context, List<Long> commandQueues) {
        logger.debug("Cleaning up resources after failed context creation");

        for (Long commandQueue : commandQueues) {
            if (commandQueue != 0) {
                CL10.clReleaseCommandQueue(commandQueue);
            }
        }
        if (context != 0) {
            CL10.clReleaseContext(context);
        }
        OpenCL.destroy();
    }
}
