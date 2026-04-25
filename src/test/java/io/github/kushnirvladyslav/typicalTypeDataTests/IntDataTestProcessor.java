package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.IntDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class IntDataTestProcessor {
    private IntDataProcessor intData;

    @BeforeEach
    void setUp() {
        intData = new IntDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Integer.BYTES, intData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        int[] primitiveArray = new int[5];
        assertEquals(5, intData.getSizeArray(primitiveArray));

        // Test boxed array
        Integer[] boxedArray = new Integer[3];
        assertEquals(3, intData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, intData.getSizeArray(1));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = intData.createArr(10);
        assertTrue(arr instanceof int[]);
        assertEquals(10, ((int[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        int[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Integer.BYTES);

        intData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (int value : source) {
            assertEquals(value, buffer.getInt());
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Integer[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Integer.BYTES);

        intData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Integer value : source) {
            assertEquals(value, buffer.getInt());
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Integer value = 1;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);

        intData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getInt());
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        int[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Integer.BYTES);

        intData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Integer.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        int[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Integer.BYTES);
        for (int value : expected) {
            buffer.putInt(value);
        }
        buffer.rewind();

        int[] target = new int[expected.length];
        intData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Integer[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Integer.BYTES);
        for (Integer value : expected) {
            buffer.putInt(value);
        }
        buffer.rewind();

        Integer[] target = new Integer[expected.length];
        intData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleIntegerTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(1);
        buffer.rewind();

        Integer target = 0;
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertFromByteBuffer(buffer, new Float(1.0f))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> intData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertToByteBuffer(ByteBuffer.allocate(4), null));
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertFromByteBuffer(ByteBuffer.allocate(4), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Integer[] array = {1, null, 3};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Integer.BYTES);

        assertThrows(NullPointerException.class, () ->
                intData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> intData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> intData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertToByteBuffer(ByteBuffer.allocate(4), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                intData.convertFromByteBuffer(ByteBuffer.allocate(4), new float[1]));
    }
}