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
import io.github.kushnirvladyslav.exceptions.OpenCLInitializationException;
import io.github.kushnirvladyslav.util.OpenCLErrorUtils;
import io.github.kushnirvladyslav.util.StatusCL;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for managing OpenCL resources and contexts.
 * This class provides functionality for:
 * <ul>
 *     <li>Initializing OpenCL runtime</li>
 *     <li>Managing OpenCL platforms and devices</li>
 *     <li>Creating and managing OpenCL contexts</li>
 *     <li>Handling default context for simple use cases</li>
 * </ul>
 *
 * <p>The class follows a singleton pattern for OpenCL runtime management and
 * provides thread-safe access to OpenCL resources.
 *
 * @author Vladyslav Kushnir
 * @version 1.0
 */
public class OpenCL {
    private static final Logger logger = LoggerFactory.getLogger(OpenCL.class);
    private static final List<Platform> platforms;
    private static final Object lock = new Object();

    private static ClContext defaultContext = null;
    private static final AtomicInteger numDefaultContext = new AtomicInteger(0);
    private static final List<ClContext> contextList = new CopyOnWriteArrayList<>();

    private static volatile StatusCL status = StatusCL.CLOSED;

    static {
        logger.debug("Initializing OpenCL runtime");
        org.lwjgl.system.Configuration.OPENCL_EXPLICIT_INIT.set(true);

        start();

        List<Platform> platformsList;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer numberPlatform = stack.mallocInt(1);
            int result = CL10.clGetPlatformIDs(null, numberPlatform);

            OpenCLErrorUtils.checkError(result, "Getting number of platforms");


            PointerBuffer platformsBuffer = stack.mallocPointer(numberPlatform.get(0));
            result = CL10.clGetPlatformIDs(platformsBuffer, (IntBuffer) null);
            OpenCLErrorUtils.checkError(result, "Getting platform IDs");

            platformsList = new ArrayList<>();
            for (int i = 0; i < numberPlatform.get(0); i++) {
                Platform platform = new Platform(platformsBuffer.get(i));
                platformsList.add(platform);
                logger.info("Found OpenCL platform: {}", platform.getName());
            }
        } catch (Exception e) {
            logger.error("Failed to initialize OpenCL", e);
            throw new OpenCLInitializationException("Failed to initialize OpenCL", e);
        } finally {
            destroy();
        }

        platforms = platformsList;
        logger.info("Successfully initialized OpenCL with {} platforms", platforms.size());
    }

    /**
     * Gets or creates the default OpenCL context.
     * <p>
     * This method provides a shared context for simple use cases where a custom context
     * is not required. The context is created lazily on first access and is shared
     * between all callers.
     * 
     *
     * @return The default OpenCL context
     * @throws DeviceNotFoundException if no suitable OpenCL device is found
     * @throws ContextCreationException if context creation fails
     */
    static public ClContext getDefaultContext() {
        synchronized (lock) {
            if (numDefaultContext.get() == 0) {
                logger.debug("Creating new default OpenCL context");
                defaultContext = createDefaultContext();
            }
        }
        numDefaultContext.incrementAndGet();
        logger.debug("Returning default context (reference count: {})", numDefaultContext.get());
        return defaultContext;
    }

    /**
     * Creates a new default OpenCL context using the best available device.
     *
     * @return A new OpenCL context
     * @throws DeviceNotFoundException if no suitable OpenCL device is found
     * @throws ContextCreationException if context creation fails
     */
    static public ClContext createDefaultContext() {
        if (platforms.isEmpty()) {
            logger.error("No OpenCL platforms available");
            throw new DeviceNotFoundException("No OpenCL platforms available");
        }

        logger.debug("Searching for best available OpenCL device");
        for (Platform platform : platforms) {
            Device device = platform.getBestDevice();
            if (device != null && device.isAvailable()) {
                logger.info("Selected device for default context: {} on platform {}",
                        device.getName(), platform.getName());

                ContextBuilder contextBuilder = new ContextBuilder();
                contextBuilder.withDevice(device);
                return contextBuilder.create();
            }
        }

        logger.error("No suitable OpenCL devices found");
        throw new DeviceNotFoundException("No OpenCL devices available");
    }

    /**
     * Creates a new context builder for custom OpenCL context configuration.
     *
     * @return A new context builder instance
     */
    static public ContextBuilder createContext() {
        logger.debug("Creating new context builder");
        return new ContextBuilder();
    }

    /**
     * Registers a new OpenCL context with the runtime.
     *
     * @param context The context to register
     * @throws IllegalArgumentException if the context is null
     */
    static void registrationContext(ClContext context) {
        if (context == null) {
            logger.error("Attempted to register null context");
            throw new IllegalArgumentException("Context cannot be null");
        }
        contextList.add(context);
        logger.info("Registered new OpenCL context: {}", context);
    }

    /**
     * Destroys an OpenCL context and releases its resources.
     *
     * @param context The context to destroy
     * @throws IllegalArgumentException if the context is null or not registered
     */
    public static void destroyContext(ClContext context) {
        if (context == null) {
            logger.error("Attempted to destroy null context");
            throw new IllegalArgumentException("Context cannot be null");
        }

        if (!contextList.contains(context)) {
            logger.error("Attempted to destroy unregistered context: {}", context);
            throw new IllegalArgumentException("Context is not registered");
        }

        if(defaultContext != null && context == defaultContext) {
            if (numDefaultContext.decrementAndGet() == 0) {
                if (contextList.remove(context)) {
                    defaultContext.destroy();
                    defaultContext = null;
                    logger.info("Destroyed default context");
                }
                destroy();
            }  else {
                logger.debug("Decreased default context reference count to {}",
                        numDefaultContext.get());
            }
        } else {
            if(contextList.remove(context)) {
                String contextInfo = context.toString();
                context.destroy();
                logger.info("Destroyed context: {}", contextInfo);
            }
            destroy();
        }
    }

    /**
     * Starts the OpenCL runtime if it's not already running.
     *
     * @throws OpenCLInitializationException if startup fails
     */
    static void start() {
        synchronized (lock) {
            if (!isRunning()) {
                logger.debug("Starting OpenCL runtime");
                try {
                    CL.create();
                    setStatus(StatusCL.RUNNING);
                    logger.info("OpenCL runtime started successfully");
                } catch (Exception e) {
                    logger.error("Failed to start OpenCL runtime", e);
                    throw new OpenCLInitializationException("Failed to start OpenCL runtime", e);
                }
            }
        }
    }

    /**
     * Destroys the OpenCL runtime if no contexts are active.
     */
    static void destroy() {
        synchronized (lock) {
            if (contextList.isEmpty() && isRunning()) {
                logger.debug("Destroying OpenCL runtime");
                setStatus(StatusCL.CLOSED);
                CL.destroy();
                logger.info("OpenCL runtime destroyed");
            }
        }
    }

    /**
     * Shuts down all contexts and the OpenCL runtime.
     */
    public void shutdown() {
        synchronized (lock) {
            logger.info("Initiating OpenCL shutdown");
            for (ClContext context : contextList) {
                try {
                    context.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying context during shutdown: {}", context, e);
                }
            }

            contextList.clear();

            defaultContext = null;
            numDefaultContext.set(0);
            destroy();
            logger.info("OpenCL shutdown completed");
        }
    }

    /**
     * Gets a list of available OpenCL platforms.
     *
     * @return List of available platforms
     */
    public static List<Platform> getPlatforms() {
        return new ArrayList<>(platforms);
    }

    /**
     * Determines whether the CL.OpenCL is currently running.
     *
     * @return true if the OpenCL is in RUNNING status, false otherwise
     */
    private static boolean isRunning () {
        return status == StatusCL.RUNNING;
    }

    /**
     * Sets the status of this OpenCL.
     *
     * @param newStatus the new status to set, must not be null
     * @throws IllegalArgumentException if newStatus is null
     */
    private static void setStatus(StatusCL newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        status = newStatus;
    }
}
