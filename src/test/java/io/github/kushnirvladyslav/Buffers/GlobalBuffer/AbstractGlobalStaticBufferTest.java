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
import io.github.kushnirvladyslav.exceptions.BufferOperationException;
import io.github.kushnirvladyslav.memory.buffer.CopyableGlobalBuffer;
import io.github.kushnirvladyslav.memory.buffer.GlobalBuffer;
import io.github.kushnirvladyslav.memory.buffer.Writable;
import io.github.kushnirvladyslav.memory.buffer.Readable;
import org.junit.jupiter.api.*;


import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractGlobalStaticBufferTest extends AbstractGlobalBufferTest {

    @Test @Order(506)
    @DisplayName("copyFrom with srcOffset >= capacity throws — static buffers do not over-allocate")
    void static_copy_srcOffsetOutOfBoundsThrows() {
        GlobalBuffer src = createBuffer(4); // actual capacity = exactly 4
        GlobalBuffer dst = createBuffer(8);
        // srcOffset=5 > capacity=4 → must throw
        assertThrows(BufferOperationException.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, 5, 0, 1),
                "srcOffset+size=6 exceeds static capacity 4 — exception required"
        );
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. STATIC CAPACITY CONTRACT
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(700)
    @DisplayName("Capacity remains unchanged after construction – static buffers never grow")
    void static_capacityIsImmutable() {
        GlobalBuffer buf = createBuffer(10);
        int before = buf.getCapacity();
        // No explicit resize call – just verify it stays put over time
        assertEquals(before, buf.getCapacity());
        buf.destroy();
    }

    @Test
    @Order(701)
    @DisplayName("setPointer at exactly the capacity boundary throws BufferIndexOutOfBoundsException")
    void static_setPointerAtCapacityThrows() {
        GlobalBuffer buf = createBuffer(8);
        // pointer == capacity is out-of-bounds for static buffers (no reallocation)
        assertThrows(BufferIndexOutOfBoundsException.class,
                () -> buf.setPointer(buf.getCapacity()));
        buf.destroy();
    }

    @Test
    @Order(702)
    @DisplayName("setPointer beyond capacity throws BufferIndexOutOfBoundsException")
    void static_setPointerBeyondCapacityThrows() {
        GlobalBuffer buf = createBuffer(8);
        assertThrows(BufferIndexOutOfBoundsException.class,
                () -> buf.setPointer(buf.getCapacity() + 5));
        buf.destroy();
    }

    @Test
    @Order(703)
    @DisplayName("setPointer beyond capacity does NOT change the pointer value")
    void static_failedSetPointerLeavesPointerUnchanged() {
        GlobalBuffer buf = createBuffer(8);
        buf.setPointer(3);
        try { buf.setPointer(buf.getCapacity() + 1); } catch (Exception ignored) {}
        assertEquals(3, buf.getPointer(),
                "Pointer must not change when setPointer throws");
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. WRITE / READ BOUNDS CHECKING (static – no auto-grow)
    //    These tests delegate to concrete subclass knowledge of whether
    //    writes / reads are supported.  They are "conditional" tests – they
    //    only assert the "throw" behaviour when the operation is supported.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(800)
    @DisplayName("readSync with offset + len == capacity succeeds (exact boundary)")
    void static_readSyncExactBoundarySucceeds() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        assertDoesNotThrow(() -> {
            Readable<?> r = (Readable<?>) buf;
            r.readSync(0, 4);
        });
        buf.destroy();
    }

    @Test
    @Order(801)
    @DisplayName("readSync with offset + len > capacity throws IllegalArgumentException")
    void static_readSyncOutOfBoundsThrows() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        assertThrows(IllegalArgumentException.class, () -> {
            Readable<?> r = (Readable<?>) buf;
            r.readSync(0, 5);          // 5 > capacity 4
        });
        buf.destroy();
    }

    @Test
    @Order(802)
    @DisplayName("readSync with negative offset throws IllegalArgumentException")
    void static_readSyncNegativeOffsetThrows() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        assertThrows(IllegalArgumentException.class, () -> {
            Readable<?> r = (Readable<?>) buf;
            r.readSync(-1, 1);
        });
        buf.destroy();
    }

    @Test
    @Order(803)
    @DisplayName("readSync with zero length throws IllegalArgumentException")
    void static_readSyncZeroLengthThrows() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        assertThrows(IllegalArgumentException.class, () -> {
            Readable<?> r = (Readable<?>) buf;
            r.readSync(0, 0);
        });
        buf.destroy();
    }

    @Test
    @Order(804)
    @DisplayName("writeSync beyond capacity throws and does NOT change capacity")
    void static_writeSyncBeyondCapacityThrowsAndCapacityUnchanged() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        int capBefore = buf.getCapacity();
        int[] bigArray = new int[10]; // 10 > capacity 4

        assertThrows(Exception.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeSync(bigArray);
        });
        assertEquals(capBefore, buf.getCapacity(),
                "Static buffer capacity must not change after a failed write");
        buf.destroy();
    }

    @Test
    @Order(805)
    @DisplayName("writeAsync beyond capacity throws and does NOT change capacity")
    void static_writeAsyncBeyondCapacityThrowsAndCapacityUnchanged() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        int capBefore = buf.getCapacity();
        int[] bigArray = new int[10];

        assertThrows(Exception.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeAsync(bigArray);
        });
        assertEquals(capBefore, buf.getCapacity());
        buf.destroy();
    }

    @Test
    @Order(806)
    @DisplayName("writeSync with negative offset throws IllegalArgumentException")
    void static_writeSyncNegativeOffsetThrows() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        int[] arr = {1};
        assertThrows(IllegalArgumentException.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeSync(-1, arr);
        });
        buf.destroy();
    }

    @Test
    @Order(807)
    @DisplayName("writeAsync with null array throws")
    void static_writeAsyncNullArrayThrows() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        assertThrows(Exception.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeAsync(null);
        });
        buf.destroy();
    }

    @Test
    @Order(808)
    @DisplayName("writeSync with null array throws")
    void static_writeSyncNullArrayThrows() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        assertThrows(Exception.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeSync(null);
        });
        buf.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. COPY BOUNDARY – static buffers cannot grow their destination
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(900)
    @DisplayName("copyFrom with size > dst capacity throws for static buffer (no auto-grow)")
    void static_copyFromOversizedSrcThrows() {
        GlobalBuffer src = createBuffer(16);
        GlobalBuffer dst = createBuffer(4);   // smaller destination
        assertThrows(Exception.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, 0, 0, 8));
        src.destroy();
        dst.destroy();
    }

    @Test
    @Order(901)
    @DisplayName("copyFrom with dstOffset + size > dst capacity throws for static buffer")
    void static_copyFromDstOffsetOutOfBoundsThrows() {
        GlobalBuffer src = createBuffer(8);
        GlobalBuffer dst = createBuffer(8);
        assertThrows(Exception.class,
                () -> ((CopyableGlobalBuffer) dst).copyFrom(src, 0, 5, 5)); // 5+5 > 8
        src.destroy();
        dst.destroy();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. POST-DESTROY WRITE/READ
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1000)
    @DisplayName("writeSync after destroy throws BufferDestructionException")
    void static_writeSyncAfterDestroyThrows() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        buf.destroy();
        int[] arr = {1};
        assertThrows(BufferDestructionException.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeSync(arr);
        });
    }

    @Test
    @Order(1001)
    @DisplayName("writeAsync after destroy throws BufferDestructionException")
    void static_writeAsyncAfterDestroyThrows() {
        if (!supportsHostWrite()) return;
        GlobalBuffer buf = createBuffer(4);
        buf.destroy();
        int[] arr = {1};
        assertThrows(BufferDestructionException.class, () -> {
            Writable<?> w = (Writable<?>) buf;
            w.writeAsync(arr);
        });
    }

    @Test
    @Order(1002)
    @DisplayName("readSync after destroy throws BufferDestructionException")
    void static_readSyncAfterDestroyThrows() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        buf.destroy();
        assertThrows(BufferDestructionException.class, () -> {
            Readable<?> r = (Readable<?>) buf;
            r.readSync(1);
        });
    }

    @Test
    @Order(1003)
    @DisplayName("readAsync after destroy throws BufferDestructionException")
    void static_readAsyncAfterDestroyThrows() {
        if (!supportsHostRead()) return;
        GlobalBuffer buf = createBuffer(4);
        buf.destroy();
        int[] target = new int[1];
        assertThrows(BufferDestructionException.class, () -> {
            Readable<?> r = (Readable<?>) buf;
            r.readAsync(target);
        });
    }
}
