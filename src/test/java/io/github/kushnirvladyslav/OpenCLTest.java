package io.github.kushnirvladyslav;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the OpenCL class functionality.
 */
class OpenCLTest {
    private Platform defaultPlatform;
    private Device defaultDevice;

    @BeforeEach
    void setUp() {
        List<Platform> platforms = OpenCL.getPlatforms();
        assumeTrue(!platforms.isEmpty(), "No OpenCL platforms available for testing");
        defaultPlatform = platforms.get(0);
        defaultDevice = defaultPlatform.getBestDevice();
        assumeTrue(defaultDevice != null, "No OpenCL devices available for testing");
    }

    @Test
    void getDefaultContext_ShouldCreateAndReturnSameContext() {
        ClContext context1 = OpenCL.getDefaultContext();
        ClContext context2 = OpenCL.getDefaultContext();

        assertNotNull(context1, "Default context should not be null");
        assertSame(context1, context2, "Should return same default context instance");

        OpenCL.destroyContext(context1);
        OpenCL.destroyContext(context2);
    }

    @Test
    void createDefaultContext_ShouldSelectBestDevice() {
        ClContext context = OpenCL.createDefaultContext();

        assertNotNull(context, "Created context should not be null");
        Device device = context.getDevice();

        if (defaultPlatform.hasGPUDevices()) {
            assertTrue(device.isGPU(), "Should prefer GPU device");
        } else if (defaultPlatform.hasCPUDevices()) {
            assertTrue(device.isCPU(), "Should use CPU device if no GPU available");
        }

        OpenCL.destroyContext(context);
    }

    @Test
    void registrationContext_ShouldAddContext() {
        ClContext context = new ContextBuilder()
                .withDevice(defaultDevice)
                .create();

        assertDoesNotThrow(() -> OpenCL.destroyContext(context),
                "Should successfully destroy registered context");
    }

    @Test
    void destroyContext_ShouldHandleDefaultContext() {
        ClContext context = OpenCL.getDefaultContext();
        OpenCL.destroyContext(context);

        ClContext newContext = OpenCL.getDefaultContext();
        assertNotSame(context, newContext,
                "Should create new default context after destroying old one");

        OpenCL.destroyContext(newContext);
    }
}
