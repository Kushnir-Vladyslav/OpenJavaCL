package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.BooleanDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class BooleanDataTestProcessor {
    private BooleanDataProcessor booleanData;

    @BeforeEach
    void setUp() {
        booleanData = new BooleanDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(1, booleanData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        boolean[] primitiveArray = new boolean[5];
        assertEquals(5, booleanData.getSizeArray(primitiveArray));

        // Test boxed array
        Boolean[] boxedArray = new Boolean[3];
        assertEquals(3, booleanData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, booleanData.getSizeArray(true));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = booleanData.createArr(10);
        assertTrue(arr instanceof boolean[]);
        assertEquals(10, ((boolean[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        boolean[] source = {true, false, true};
        ByteBuffer buffer = ByteBuffer.allocate(source.length);

        booleanData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (boolean value : source) {
            assertEquals(value, buffer.get() == 1);
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Boolean[] source = {true, false, true};
        ByteBuffer buffer = ByteBuffer.allocate(source.length);

        booleanData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Boolean value : source) {
            assertEquals(value, buffer.get() == 1);
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Boolean value = true;
        ByteBuffer buffer = ByteBuffer.allocate(1);

        booleanData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.get() == 1);
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        boolean[] source = {true, false, true};
        ByteBuffer buffer = ByteBuffer.allocate(source.length);

        booleanData.convertToByteBuffer(buffer, source);
        assertEquals(source.length, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        boolean[] expected = {true, false, true};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length);
        for (boolean value : expected) {
            buffer.put(value ? (byte)1 : (byte)0);
        }
        buffer.rewind();

        boolean[] target = new boolean[expected.length];
        booleanData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Boolean[] expected = {true, false, true};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length);
        for (Boolean value : expected) {
            buffer.put(value ? (byte)1 : (byte)0);
        }
        buffer.rewind();

        Boolean[] target = new Boolean[expected.length];
        booleanData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleBooleanTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.put((byte)1);
        buffer.rewind();

        Boolean target = false;
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertFromByteBuffer(buffer, new Integer(1))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> booleanData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertToByteBuffer(ByteBuffer.allocate(1), null));
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertFromByteBuffer(ByteBuffer.allocate(1), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Boolean[] array = {true, null, false};
        ByteBuffer buffer = ByteBuffer.allocate(array.length);

        assertThrows(NullPointerException.class, () ->
                booleanData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> booleanData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> booleanData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertToByteBuffer(ByteBuffer.allocate(1), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                booleanData.convertFromByteBuffer(ByteBuffer.allocate(1), new float[1]));
    }
}