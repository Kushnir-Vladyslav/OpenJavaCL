package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.ByteDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class ByteDataTestProcessor {
    private ByteDataProcessor byteData;

    @BeforeEach
    void setUp() {
        byteData = new ByteDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Byte.BYTES, byteData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        byte[] primitiveArray = new byte[5];
        assertEquals(5, byteData.getSizeArray(primitiveArray));

        // Test boxed array
        Byte[] boxedArray = new Byte[3];
        assertEquals(3, byteData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, byteData.getSizeArray((byte)1));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = byteData.createArr(10);
        assertTrue(arr instanceof byte[]);
        assertEquals(10, ((byte[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        byte[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Byte.BYTES);

        byteData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (byte value : source) {
            assertEquals(value, buffer.get());
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Byte[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Byte.BYTES);

        byteData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Byte value : source) {
            assertEquals(value, buffer.get());
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Byte value = 1;
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);

        byteData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.get());
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        byte[] source = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Byte.BYTES);

        byteData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Byte.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        byte[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Byte.BYTES);
        for (byte value : expected) {
            buffer.put(value);
        }
        buffer.rewind();

        byte[] target = new byte[expected.length];
        byteData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Byte[] expected = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Byte.BYTES);
        for (Byte value : expected) {
            buffer.put(value);
        }
        buffer.rewind();

        Byte[] target = new Byte[expected.length];
        byteData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleByteTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        buffer.put((byte)1);
        buffer.rewind();

        Byte target = 0;
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Byte.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertFromByteBuffer(buffer, new Float(1.0f))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> byteData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertToByteBuffer(ByteBuffer.allocate(1), null));
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertFromByteBuffer(ByteBuffer.allocate(1), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Byte[] array = {1, null, 3};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Byte.BYTES);

        assertThrows(NullPointerException.class, () ->
                byteData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> byteData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> byteData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertToByteBuffer(ByteBuffer.allocate(1), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                byteData.convertFromByteBuffer(ByteBuffer.allocate(1), new float[1]));
    }
}