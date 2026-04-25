package io.github.kushnirvladyslav.typicalTypeDataTests;


import io.github.kushnirvladyslav.memory.data.typical.FloatDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class FloatDataTestProcessor {
    private FloatDataProcessor floatData;

    @BeforeEach
    void setUp() {
        floatData = new FloatDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Float.BYTES, floatData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        float[] primitiveArray = new float[5];
        assertEquals(5, floatData.getSizeArray(primitiveArray));

        // Test boxed array
        Float[] boxedArray = new Float[3];
        assertEquals(3, floatData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, floatData.getSizeArray(1.0f));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = floatData.createArr(10);
        assertTrue(arr instanceof float[]);
        assertEquals(10, ((float[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        float[] source = {1.0f, 2.0f, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Float.BYTES);

        floatData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (float value : source) {
            assertEquals(value, buffer.getFloat(), 0.0001f);
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Float[] source = {1.0f, 2.0f, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Float.BYTES);

        floatData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Float value : source) {
            assertEquals(value, buffer.getFloat(), 0.0001f);
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Float value = 1.0f;
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);

        floatData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getFloat(), 0.0001f);
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        float[] source = {1.0f, 2.0f, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Float.BYTES);

        floatData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Float.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        float[] expected = {1.0f, 2.0f, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Float.BYTES);
        for (float value : expected) {
            buffer.putFloat(value);
        }
        buffer.rewind();

        float[] target = new float[expected.length];
        floatData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target, 0.0001f);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Float[] expected = {1.0f, 2.0f, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Float.BYTES);
        for (Float value : expected) {
            buffer.putFloat(value);
        }
        buffer.rewind();

        Float[] target = new Float[expected.length];
        floatData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleFloatTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        buffer.putFloat(1.0f);
        buffer.rewind();

        Float target = 0.0f;
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertFromByteBuffer(buffer, new Integer(1))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> floatData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertToByteBuffer(ByteBuffer.allocate(4), null));
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertFromByteBuffer(ByteBuffer.allocate(4), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Float[] array = {1.0f, null, 3.0f};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Float.BYTES);

        assertThrows(NullPointerException.class, () ->
                floatData.convertToByteBuffer(buffer, array));
    }


    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> floatData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> floatData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertToByteBuffer(ByteBuffer.allocate(4), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                floatData.convertFromByteBuffer(ByteBuffer.allocate(4), new int[1]));
    }
}
