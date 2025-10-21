package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.DoubleDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class DoubleDataTestProcessor {
    private DoubleDataProcessor doubleData;

    @BeforeEach
    void setUp() {
        doubleData = new DoubleDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Double.BYTES, doubleData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        double[] primitiveArray = new double[5];
        assertEquals(5, doubleData.getSizeArray(primitiveArray));

        // Test boxed array
        Double[] boxedArray = new Double[3];
        assertEquals(3, doubleData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, doubleData.getSizeArray(1.0));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = doubleData.createArr(10);
        assertTrue(arr instanceof double[]);
        assertEquals(10, ((double[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        double[] source = {1.0, 2.0, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Double.BYTES);

        doubleData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (double value : source) {
            assertEquals(value, buffer.getDouble(), 0.0001);
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Double[] source = {1.0, 2.0, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Double.BYTES);

        doubleData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Double value : source) {
            assertEquals(value, buffer.getDouble(), 0.0001);
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Double value = 1.0;
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);

        doubleData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getDouble(), 0.0001);
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        double[] source = {1.0, 2.0, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Double.BYTES);

        doubleData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Double.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        double[] expected = {1.0, 2.0, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Double.BYTES);
        for (double value : expected) {
            buffer.putDouble(value);
        }
        buffer.rewind();

        double[] target = new double[expected.length];
        doubleData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target, 0.0001);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Double[] expected = {1.0, 2.0, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Double.BYTES);
        for (Double value : expected) {
            buffer.putDouble(value);
        }
        buffer.rewind();

        Double[] target = new Double[expected.length];
        doubleData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleDoubleTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.putDouble(1.0);
        buffer.rewind();

        Double target = 0.0;
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertFromByteBuffer(buffer, new Float(1.0f))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> doubleData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertToByteBuffer(ByteBuffer.allocate(8), null));
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertFromByteBuffer(ByteBuffer.allocate(8), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Double[] array = {1.0, null, 3.0};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Double.BYTES);

        assertThrows(NullPointerException.class, () ->
                doubleData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> doubleData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> doubleData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertToByteBuffer(ByteBuffer.allocate(8), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                doubleData.convertFromByteBuffer(ByteBuffer.allocate(8), new float[1]));
    }
}