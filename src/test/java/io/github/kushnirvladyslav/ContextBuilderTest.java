package io.github.kushnirvladyslav;

import io.github.kushnirvladyslav.exceptions.DeviceNotFoundException;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the ContextBuilder class functionality.
 */
class ContextBuilderTest {
    private ContextBuilder builder;
    private Platform platform;
    private Device device;

    @BeforeEach
    void setUp() {
        List<Platform> platforms = OpenCL.getPlatforms();
        assumeTrue(!platforms.isEmpty(), "No OpenCL platforms available for testing");

        platform = platforms.get(0);
        device = platform.getBestDevice();
        assumeTrue(device != null, "No OpenCL devices available for testing");

        builder = new ContextBuilder();
    }

    @Test
    void createContext_WithDefaultSettings() {
        ClContext context = builder
                .withDevice(device)
                .create();

        assertNotNull(context, "Created context should not be null");
        assertEquals(device, context.getDevice(), "Context should use specified device");
        assertFalse(context.isOutOfOrder(), "Out of order should be false by default");
    }

    @Test
    void createContext_WithOutOfOrder() {
        ClContext context = builder
                .withDevice(device)
                .withOutOfOrderQueue(true)
                .create();

        assertTrue(context.isOutOfOrder(), "Context should be out of order");
    }

    @Test
    void createContext_WithProfiling() {
        ClContext context = builder
                .withDevice(device)
                .withProfiling(true)
                .create();

        assertNotNull(context, "Created context should not be null");
    }

    @Test
    void createContext_WithBestDevice() {
        ClContext context = builder
                .withBestDevice(platform)
                .create();

        assertNotNull(context, "Created context should not be null");
        assertEquals(platform.getBestDevice(), context.getDevice(),
                "Should use best available device");
    }

    @Test
    void createContext_WithBestCPUDevice() {
        assumeTrue(platform.hasCPUDevices(), "No CPU devices available for testing");

        ClContext context = builder
                .withBestCPUDevice(platform)
                .create();

        assertTrue(context.getDevice().isCPU(),
                "Should use CPU device");
    }

    @Test
    void createContext_WithBestGPUDevice() {
        assumeTrue(platform.hasGPUDevices(), "No GPU devices available for testing");

        ClContext context = builder
                .withBestGPUDevice(platform)
                .create();

        assertTrue(context.getDevice().isGPU(),
                "Should use GPU device");
    }

    @Test
    void createContext_ShouldFail_WithoutDevice() {
        assertThrows(DeviceNotFoundException.class,
                () -> builder.create(),
                "Should throw exception when no device specified");
    }

    @Test
    void createContext_ShouldFail_WithUnavailableDevice() {
        assumeTrue(!device.isAvailable(), "Test requires unavailable device");

        assertThrows(DeviceNotFoundException.class,
                () -> builder.withDevice(device).create(),
                "Should throw exception when device is unavailable");
    }
}
