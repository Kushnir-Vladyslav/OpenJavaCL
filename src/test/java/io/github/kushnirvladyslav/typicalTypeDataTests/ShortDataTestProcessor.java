package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.ShortDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class ShortDataTestProcessor {
    private ShortDataProcessor shortData;

    @BeforeEach
    void setUp() {
        shortData = new ShortDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Short.BYTES, shortData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        short[] primitiveArray = new short[5];
        assertEquals(5, shortData.getSizeArray(primitiveArray));

        // Test boxed array
        Short[] boxedArray = new Short[3];
        assertEquals(3, shortData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, shortData.getSizeArray((short)1));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = shortData.createArr(10);
        assertTrue(arr instanceof short[]);
        assertEquals(10, ((short[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        short[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Short.BYTES);

        shortData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (short value : source) {
            assertEquals(value, buffer.getShort());
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Short[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Short.BYTES);

        shortData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Short value : source) {
            assertEquals(value, buffer.getShort());
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Short value = 1;
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);

        shortData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getShort());
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        short[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Short.BYTES);

        shortData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Short.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        short[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Short.BYTES);
        for (short value : expected) {
            buffer.putShort(value);
        }
        buffer.rewind();

        short[] target = new short[expected.length];
        shortData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Short[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Short.BYTES);
        for (Short value : expected) {
            buffer.putShort(value);
        }
        buffer.rewind();

        Short[] target = new Short[expected.length];
        shortData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleShortTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.putShort((short)1);
        buffer.rewind();

        Short target = 0;
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertFromByteBuffer(buffer, new Float(1.0f))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> shortData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertToByteBuffer(ByteBuffer.allocate(2), null));
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertFromByteBuffer(ByteBuffer.allocate(2), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Short[] array = {1, null, 3};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Short.BYTES);

        assertThrows(NullPointerException.class, () ->
                shortData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> shortData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> shortData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertToByteBuffer(ByteBuffer.allocate(2), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                shortData.convertFromByteBuffer(ByteBuffer.allocate(2), new float[1]));
    }
}