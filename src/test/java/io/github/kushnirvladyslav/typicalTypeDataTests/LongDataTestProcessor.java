package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.LongDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class LongDataTestProcessor {
    private LongDataProcessor longData;

    @BeforeEach
    void setUp() {
        longData = new LongDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Long.BYTES, longData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        long[] primitiveArray = new long[5];
        assertEquals(5, longData.getSizeArray(primitiveArray));

        // Test boxed array
        Long[] boxedArray = new Long[3];
        assertEquals(3, longData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, longData.getSizeArray(1L));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = longData.createArr(10);
        assertTrue(arr instanceof long[]);
        assertEquals(10, ((long[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        long[] source = {1L, 2L, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Long.BYTES);

        longData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (long value : source) {
            assertEquals(value, buffer.getLong());
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Long[] source = {1L, 2L, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Long.BYTES);

        longData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Long value : source) {
            assertEquals(value, buffer.getLong());
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Long value = 1L;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);

        longData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getLong());
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        long[] source = {1L, 2L, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Long.BYTES);

        longData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Long.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        long[] expected = {1L, 2L, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Long.BYTES);
        for (long value : expected) {
            buffer.putLong(value);
        }
        buffer.rewind();

        long[] target = new long[expected.length];
        longData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Long[] expected = {1L, 2L, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Long.BYTES);
        for (Long value : expected) {
            buffer.putLong(value);
        }
        buffer.rewind();

        Long[] target = new Long[expected.length];
        longData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleLongTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(1L);
        buffer.rewind();

        Long target = 0L;
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertFromByteBuffer(buffer, new Float(1.0f))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> longData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertToByteBuffer(ByteBuffer.allocate(8), null));
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertFromByteBuffer(ByteBuffer.allocate(8), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Long[] array = {1L, null, 3L};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Long.BYTES);

        assertThrows(NullPointerException.class, () ->
                longData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> longData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> longData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertToByteBuffer(ByteBuffer.allocate(8), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                longData.convertFromByteBuffer(ByteBuffer.allocate(8), new float[1]));
    }
}