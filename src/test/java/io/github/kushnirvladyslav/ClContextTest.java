package io.github.kushnirvladyslav;

import io.github.kushnirvladyslav.util.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the ClContext class functionality.
 */
class ClContextTest {
    private ClContext context;
    private Platform platform;
    private Device device;

    @BeforeEach
    void setUp() {
        List<Platform> platforms = OpenCL.getPlatforms();
        assumeTrue(!platforms.isEmpty(), "No OpenCL platforms available for testing");

        platform = platforms.get(0);
        device = platform.getBestDevice();
        assumeTrue(device != null, "No OpenCL devices available for testing");

        context = new ContextBuilder()
                .withDevice(device)
                .create();
    }

    @AfterEach
    void tearDown() {
        if (context != null && !context.isClosed()) {
            OpenCL.destroyContext(context);
        }
    }

    @Test
    void contextCreation_ShouldInitializeCorrectly() {
        assertEquals(platform, context.getPlatform(),
                "Context should have correct platform");
        assertEquals(device, context.getDevice(),
                "Context should have correct device");
        assertFalse(context.isOutOfOrder(),
                "Context should not be out of order by default");
        assertFalse(context.hasDeviceCommandQueue(),
                "Context should not have device queue by default");
    }

    @Test
    void destroy_ShouldCleanupResources() {
        OpenCL.destroyContext(context);
        assertTrue(context.isClosed(), "Context should be marked as closed");

        assertThrows(IllegalStateException.class,
                () -> context.getPlatform(),
                "Should throw exception when accessing destroyed context");
    }

    @Test
    void destroy_ShouldBeIdempotent() {
        OpenCL.destroyContext(context);
        assertThrows(IllegalArgumentException.class, () -> OpenCL.destroyContext(context));
    }

    @Test
    void toString_ShouldIncludeRelevantInfo() {
        String contextString = context.toString();

        assertTrue(contextString.contains(platform.getName()),
                "ToString should include platform name");
        assertTrue(contextString.contains(device.getName()),
                "ToString should include device name");
        assertTrue(contextString.contains(StatusCL.RUNNING.name()),
                "ToString should include status");
    }
}
