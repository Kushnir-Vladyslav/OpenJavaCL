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

package io.github.kushnirvladyslav.Buffers.GlobalBuffer;

import io.github.kushnirvladyslav.exceptions.BufferDestructionException;
import io.github.kushnirvladyslav.exceptions.BufferIndexOutOfBoundsException;
import io.github.kushnirvladyslav.memory.buffer.*;
import io.github.kushnirvladyslav.memory.buffer.Readable;
import org.junit.jupiter.api.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opencl.CL10.clReleaseKernel;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractGlobalDynamicalBufferTest extends AbstractGlobalBufferTest {

    // ── Constants (mirrors GlobalDynamicalBuffer; shrinkFactor is private there) ──
    protected static final double CAPACITY_MULTIPLIER = 1.5;  // GlobalDynamicalBuffer.capacityMultiplier
    protected static final int    MIN_CAPACITY        = 10;   // GlobalDynamicalBuffer.minCapacity
    protected static final double SHRINK_FACTOR       = 2.0;  // GlobalDynamicalBuffer.shrinkFactor

    // ── Capacity helpers ─────────────────────────────────────────────────────

    /** Expected actual capacity after construction with a given requested value. */
    protected static int initialCapacity(int requested) {
        int dyn = (int)(requested * CAPACITY_MULTIPLIER);
        return Math.max(dyn, MIN_CAPACITY);
    }

    /** Expected capacity after a successful grow resize to the given target. */
    protected static int growCapacity(int target) {
        return (int)(target * CAPACITY_MULTIPLIER);
    }

    // ── Convenience cast ─────────────────────────────────────────────────────

    protected GlobalDynamicalBuffer dynBuf(int capacity) {
        return (GlobalDynamicalBuffer) createBuffer(capacity);
    }

    protected GlobalDynamicalBuffer dynBufStaged(String name, int capacity) {
        return (GlobalDynamicalBuffer) createBuffer(name, capacity, true);
    }

    // ── Override: dynamic buffers are always device READ_WRITE ───────────────
    @Override
    protected void assumeBufferCanBeUsedAsSource() { /* no-op */ }

    @Test @Order(506)
    @DisplayName("copyFrom with srcOffset within ACTUAL capacity does not throw (dynamic over-allocates)")
    void copy_srcOffsetOutOfBoundsDoesNotThrow() {
        GlobalBuffer src = createBuffer(4); // actual capacity = max(4*1.5, 10) = 10
        GlobalBuffer dst = createBuffer(8);
        assertDoesNotThrow(
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, 5, 0, 1),
                "srcOffset+size=6 fits within actual capacity 10 — no exception expected"
        );
        assertThrows(Exception.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, src.getCapacity(), 0, 1),
                "srcOffset == actualCapacity must still throw"
        );
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. CAPACITY CONTRACT (dynamic-specific)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(700)
    @DisplayName("Actual capacity is always >= the requested value")
    void dynamic_capacityAtLeastRequested() {
        GlobalDynamicalBuffer buf = dynBuf(50);
        assertTrue(buf.getCapacity() >= 50);
        buf.destroy();
    }

    @Test @Order(701)
    @DisplayName("Actual capacity is never below MIN_CAPACITY regardless of request")
    void dynamic_capacityNeverBelowMin() {
        GlobalDynamicalBuffer buf = dynBuf(1);
        assertTrue(buf.getCapacity() >= MIN_CAPACITY);
        buf.destroy();
    }

    @Test @Order(702)
    @DisplayName("Actual capacity matches expected multiplied value for a large request")
    void dynamic_capacityMatchesExpectedForLargeRequest() {
        int requested = 100;
        GlobalDynamicalBuffer buf = dynBuf(requested);
        assertEquals(initialCapacity(requested), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(703)
    @DisplayName("Two buffers with the same requested capacity have identical actual capacity")
    void dynamic_sameRequestSameCapacity() {
        GlobalDynamicalBuffer b1 = dynBuf(50);
        GlobalDynamicalBuffer b2 = dynBuf(50);
        assertEquals(b1.getCapacity(), b2.getCapacity());
        b1.destroy();
        b2.destroy();
    }

    @Test @Order(704)
    @DisplayName("setPointer at capacity does NOT throw – dynamic buffer auto-grows")
    void dynamic_setPointerAtCapacityDoesNotThrow() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.setPointer(cap),
                "Dynamic buffer must auto-grow instead of throwing when pointer == capacity");
        buf.destroy();
    }

    @Test @Order(705)
    @DisplayName("setPointer beyond capacity does NOT throw – dynamic buffer auto-grows")
    void dynamic_setPointerBeyondCapacityDoesNotThrow() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.setPointer(cap + 10));
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. RESIZE
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(800)
    @DisplayName("resize to a larger value increases capacity")
    void resize_growthIncreasesCapacity() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int before = buf.getCapacity();
        buf.resize(before + 50);
        assertTrue(buf.getCapacity() > before);
        buf.destroy();
    }

    @Test @Order(801)
    @DisplayName("resize to a larger value: new capacity = target * CAPACITY_MULTIPLIER")
    void resize_growthCapacityIncludesMultiplier() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int target = buf.getCapacity() + 50;
        buf.resize(target);
        assertEquals(growCapacity(target), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(802)
    @DisplayName("resize to current capacity is a no-op")
    void resize_sameCapacityIsNoOp() {
        GlobalDynamicalBuffer buf = dynBuf(50);
        int before = buf.getCapacity();
        buf.resize(before);
        assertEquals(before, buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(803)
    @DisplayName("resize to slightly smaller (ratio <= SHRINK_FACTOR) is a no-op")
    void resize_slightlySmallerWithinThresholdIsNoOp() {
        // cap = 150; ratio = 150 / 76 ≈ 1.97 < 2 → no-op
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();             // 150
        int safeTarget = cap / 2 + 1;           // 76: just inside threshold
        buf.resize(safeTarget);
        assertEquals(cap, buf.getCapacity(), "resize within shrink threshold must be a no-op");
        buf.destroy();
    }

    @Test @Order(804)
    @DisplayName("resize to much smaller (ratio > SHRINK_FACTOR) decreases capacity")
    void resize_muchSmallerShrinks() {
        // cap = 150; ratio = 150 / 37 ≈ 4.05 > 2 → shrinks
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();            // 150
        int shrinkTarget = cap / 4;             // 37
        buf.resize(shrinkTarget);
        assertTrue(buf.getCapacity() < cap, "Capacity must decrease after a shrink resize");
        buf.destroy();
    }

    @Test @Order(805)
    @DisplayName("after shrink resize, capacity equals max(target, MIN_CAPACITY)")
    void resize_shrinkCapacityEqualsTarget() {
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();
        int shrinkTarget = cap / 4;
        buf.resize(shrinkTarget);
        assertEquals(Math.max(shrinkTarget, MIN_CAPACITY), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(806)
    @DisplayName("resize to 0 shrinks to MIN_CAPACITY (not an error)")
    void resize_zeroShrinksToMin() {
        // cap = 150; minReq = max(0, 10) = 10; ratio = 150/10 = 15 > 2 → shrinks to 10
        GlobalDynamicalBuffer buf = dynBuf(100);
        buf.resize(0);
        assertEquals(MIN_CAPACITY, buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(807)
    @DisplayName("resize with negative value throws IllegalArgumentException")
    void resize_negativeThrows() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        assertThrows(IllegalArgumentException.class, () -> buf.resize(-1));
        buf.destroy();
    }

    @Test @Order(808)
    @DisplayName("resize on a closed buffer throws BufferDestructionException")
    void resize_closedBufferThrows() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        buf.destroy();
        assertThrows(BufferDestructionException.class, () -> buf.resize(100));
    }

    @Test @Order(809)
    @DisplayName("after shrink resize, pointer is clamped to new capacity")
    void resize_pointerClampedAfterShrink() {
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();        // 150
        buf.setPointer(cap - 1);            // near the end
        int shrinkTarget = cap / 4;         // 37
        buf.resize(shrinkTarget);
        int newCap = buf.getCapacity();
        assertTrue(buf.getPointer() <= newCap,
                "Pointer must not exceed new capacity after shrink");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. INCREASE / DECREASE
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(900)
    @DisplayName("increase() grows capacity when target exceeds current capacity")
    void increase_growsWhenTargetLarger() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int before = buf.getCapacity();
        buf.increase(before + 50);
        assertTrue(buf.getCapacity() > before);
        buf.destroy();
    }

    @Test @Order(901)
    @DisplayName("increase() new capacity includes CAPACITY_MULTIPLIER")
    void increase_newCapacityIncludesMultiplier() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int target = buf.getCapacity() + 50;
        buf.increase(target);
        assertEquals(growCapacity(target), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(902)
    @DisplayName("increase() is a no-op when target equals current capacity")
    void increase_noOpWhenEqual() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int before = buf.getCapacity();
        buf.increase(before);
        assertEquals(before, buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(903)
    @DisplayName("increase() is a no-op when target is smaller than current capacity")
    void increase_noOpWhenSmaller() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int before = buf.getCapacity();
        buf.increase(before - 1);
        assertEquals(before, buf.getCapacity(), "increase() must never shrink capacity");
        buf.destroy();
    }

    @Test @Order(904)
    @DisplayName("decrease() shrinks capacity when ratio > SHRINK_FACTOR")
    void decrease_shrinksWhenRatioExceedsFactor() {
        // cap = 150; target = 37; 37 < 150/2 = 75 → triggers shrink
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();
        int target = cap / 4;               // 37
        buf.decrease(target);
        assertTrue(buf.getCapacity() < cap, "decrease() must shrink when ratio > SHRINK_FACTOR");
        buf.destroy();
    }

    @Test @Order(905)
    @DisplayName("after decrease(), capacity equals max(target, MIN_CAPACITY)")
    void decrease_capacityEqualsTarget() {
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();
        int target = cap / 4;
        buf.decrease(target);
        assertEquals(Math.max(target, MIN_CAPACITY), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(906)
    @DisplayName("decrease() is a no-op when ratio <= SHRINK_FACTOR")
    void decrease_noOpWithinThreshold() {
        // cap = 150; target = 76; 76 < 75 is false → no-op
        GlobalDynamicalBuffer buf = dynBuf(100);
        int cap = buf.getCapacity();
        int safeTarget = cap / 2 + 1;       // 76: just outside shrink zone
        buf.decrease(safeTarget);
        assertEquals(cap, buf.getCapacity(), "decrease() must not shrink within threshold");
        buf.destroy();
    }

    @Test @Order(907)
    @DisplayName("decrease() is a no-op when target is larger than current capacity")
    void decrease_noOpWhenTargetLarger() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int before = buf.getCapacity();
        buf.decrease(before + 100);
        assertEquals(before, buf.getCapacity(), "decrease() must not grow capacity");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. AUTO-GROW via setPointer
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1000)
    @DisplayName("setPointer one beyond actual capacity triggers auto-grow")
    void autoGrow_atCapacity() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int cap = buf.getCapacity();        // e.g. 15
        buf.setPointer(cap + 1);            // 16 > 15 → changeCapacity(16) → grows
        assertTrue(buf.getCapacity() > cap,
                "Buffer must auto-grow when pointer exceeds actual capacity");
        buf.destroy();
    }

    @Test @Order(1001)
    @DisplayName("setPointer beyond capacity triggers auto-grow")
    void autoGrow_beyondCapacity() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int cap = buf.getCapacity();
        buf.setPointer(cap + 20);
        assertTrue(buf.getCapacity() > cap);
        buf.destroy();
    }

    @Test @Order(1002)
    @DisplayName("after auto-grow, pointer is set to the requested value")
    void autoGrow_pointerSetCorrectly() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int target = buf.getCapacity() + 5;
        buf.setPointer(target);
        assertEquals(target, buf.getPointer());
        buf.destroy();
    }

    @Test @Order(1003)
    @DisplayName("after auto-grow, new capacity strictly exceeds the pointer value")
    void autoGrow_capacityStrictlyExceedsPointer() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int target = buf.getCapacity() + 1;
        buf.setPointer(target);
        assertTrue(buf.getCapacity() > target,
                "New capacity should exceed pointer so there is room to grow further");
        buf.destroy();
    }

    @Test @Order(1004)
    @DisplayName("after auto-grow, new capacity = target * CAPACITY_MULTIPLIER")
    void autoGrow_capacityIncludesMultiplier() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        int target = buf.getCapacity() + 10;
        buf.setPointer(target);
        assertEquals(growCapacity(target), buf.getCapacity());
        buf.destroy();
    }

    @Test @Order(1005)
    @DisplayName("Multiple sequential auto-grows each increase capacity")
    void autoGrow_sequentialGrowsEachSucceed() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        for (int i = 0; i < 5; i++) {
            int cap = buf.getCapacity();
            buf.setPointer(cap + 1);        // always one beyond actual → forces grow
            assertTrue(buf.getCapacity() > cap,
                    "Iteration " + i + ": setPointer beyond actual capacity must grow");
        }
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. KERNEL REBINDING after resize
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1100)
    @DisplayName("after grow resize, buffer remains bound to the kernel")
    void rebind_persistsAfterGrow() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        clKernel = buildKernel("k_grow_rebind", "", "buf[get_global_id(0)] = 1;");
        buf.bindToKernel(clKernel, 0);

        buf.resize(buf.getCapacity() + 50);

        assertTrue(buf.isBoundToKernel(clKernel),
                "Binding must persist after a grow resize");
        buf.destroy();
    }

    @Test @Order(1101)
    @DisplayName("after shrink resize, buffer remains bound to the kernel")
    void rebind_persistsAfterShrink() {
        GlobalDynamicalBuffer buf = dynBuf(100);
        clKernel = buildKernel("k_shrink_rebind", "", "buf[get_global_id(0)] = 0;");
        buf.bindToKernel(clKernel, 0);
        int cap = buf.getCapacity();

        buf.resize(cap / 4);

        assertTrue(buf.isBoundToKernel(clKernel),
                "Binding must persist after a shrink resize");
        buf.destroy();
    }

    @Test @Order(1102)
    @DisplayName("all multiple kernel bindings persist after resize")
    void rebind_multipleBindingsPersistAfterResize() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        long k1 = buildKernel("k_multi_r1", "", "buf[get_global_id(0)] = 1;");
        long k2 = buildKernel("k_multi_r2", "", "buf[get_global_id(0)] = 2;");
        buf.bindToKernel(k1, 0);
        buf.bindToKernel(k2, 0);

        buf.resize(buf.getCapacity() + 50);

        assertTrue(buf.isBoundToKernel(k1));
        assertTrue(buf.isBoundToKernel(k2));
        clReleaseKernel(k1);
        clReleaseKernel(k2);
        buf.destroy();
    }

    @Test @Order(1103)
    @DisplayName("after grow resize, kernel can execute against the buffer without error")
    void rebind_kernelExecutesAfterGrow() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        clKernel = buildKernel("k_exec_grow",
                "", "buf[get_global_id(0)] = get_global_id(0);");
        buf.bindToKernel(clKernel, 0);

        buf.resize(buf.getCapacity() + 30);
        int newCap = buf.getCapacity();

        assertDoesNotThrow(() -> enqueueKernel(clKernel, Math.min(newCap, 64)));
        buf.destroy();
    }

    @Test @Order(1104)
    @DisplayName("after auto-grow via setPointer, kernel binding persists")
    void rebind_persistsAfterAutoGrow() {
        GlobalDynamicalBuffer buf = dynBuf(10);
        clKernel = buildKernel("k_autogrow_rebind", "", "buf[get_global_id(0)] = 1;");
        buf.bindToKernel(clKernel, 0);
        int cap = buf.getCapacity();

        buf.setPointer(cap);          // trigger auto-grow

        assertTrue(buf.isBoundToKernel(clKernel),
                "Binding must persist after auto-grow via setPointer");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. STAGING BUFFER resize behaviour
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1200)
    @DisplayName("staging buffer: grow resize completes without error")
    void staging_growSucceeds() {
        GlobalDynamicalBuffer buf = dynBufStaged("staged-grow", 10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.resize(cap + 50));
        buf.destroy();
    }

    @Test @Order(1201)
    @DisplayName("staging buffer: shrink resize completes without error")
    void staging_shrinkSucceeds() {
        GlobalDynamicalBuffer buf = dynBufStaged("staged-shrink", 100);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.resize(cap / 4));
        buf.destroy();
    }

    @Test @Order(1202)
    @DisplayName("staging buffer: alternating grow/shrink cycles all succeed")
    void staging_alternateCycles() {
        GlobalDynamicalBuffer buf = dynBufStaged("staged-cycle", 10);
        assertDoesNotThrow(() -> {
            buf.resize(buf.getCapacity() + 50);   // grow
            buf.resize(buf.getCapacity() + 50);   // grow again
            buf.resize(buf.getCapacity() / 4);    // shrink
            buf.resize(buf.getCapacity() + 100);  // grow back
        });
        buf.destroy();
    }

    @Test @Order(1203)
    @DisplayName("staging buffer: auto-grow via setPointer completes without error")
    void staging_autoGrowViaPointer() {
        GlobalDynamicalBuffer buf = dynBufStaged("staged-autogrow", 10);
        int cap = buf.getCapacity();
        assertDoesNotThrow(() -> buf.setPointer(cap + 20));
        buf.destroy();
    }
}
