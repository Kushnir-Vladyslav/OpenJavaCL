package io.github.kushnirvladyslav.typicalTypeDataTests;

import io.github.kushnirvladyslav.memory.data.typical.CharDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class CharDataTestProcessor {
    private CharDataProcessor charData;

    @BeforeEach
    void setUp() {
        charData = new CharDataProcessor();
    }

    @Test
    void shouldReturnCorrectStructSize() {
        assertEquals(Character.BYTES, charData.getSizeStruct());
    }

    @Test
    void shouldReturnCorrectArraySize() {
        // Test primitive array
        char[] primitiveArray = new char[5];
        assertEquals(5, charData.getSizeArray(primitiveArray));

        // Test boxed array
        Character[] boxedArray = new Character[3];
        assertEquals(3, charData.getSizeArray(boxedArray));

        // Test single value
        assertEquals(1, charData.getSizeArray('a'));
    }

    @Test
    void shouldCreateArrayWithCorrectSize() {
        Object arr = charData.createArr(10);
        assertTrue(arr instanceof char[]);
        assertEquals(10, ((char[]) arr).length);
    }

    @Test
    void shouldConvertPrimitiveArrayToByteBuffer() {
        char[] source = {'a', 'b', 'c'};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Character.BYTES);

        charData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (char value : source) {
            assertEquals(value, buffer.getChar());
        }
    }

    @Test
    void shouldConvertBoxedArrayToByteBuffer() {
        Character[] source = {'x', 'y', 'z'};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Character.BYTES);

        charData.convertToByteBuffer(buffer, source);
        buffer.rewind();

        for (Character value : source) {
            assertEquals(value, buffer.getChar());
        }
    }

    @Test
    void shouldConvertSingleValueToByteBuffer() {
        Character value = 'q';
        ByteBuffer buffer = ByteBuffer.allocate(Character.BYTES);

        charData.convertToByteBuffer(buffer, value);
        buffer.rewind();

        assertEquals(value, buffer.getChar());
    }

    @Test
    void shouldUpdateBufferPositionAfterWrite() {
        char[] source = {'a', 'b', 'c'};
        ByteBuffer buffer = ByteBuffer.allocate(source.length * Character.BYTES);

        charData.convertToByteBuffer(buffer, source);
        assertEquals(source.length * Character.BYTES, buffer.position());
    }

    @Test
    void shouldConvertFromByteBufferToPrimitiveArray() {
        char[] expected = {'a', 'b', 'c'};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Character.BYTES);
        for (char value : expected) {
            buffer.putChar(value);
        }
        buffer.rewind();

        char[] target = new char[expected.length];
        charData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldConvertFromByteBufferToBoxedArray() {
        Character[] expected = {'x', 'y', 'z'};
        ByteBuffer buffer = ByteBuffer.allocate(expected.length * Character.BYTES);
        for (Character value : expected) {
            buffer.putChar(value);
        }
        buffer.rewind();

        Character[] target = new Character[expected.length];
        charData.convertFromByteBuffer(buffer, target);

        assertArrayEquals(expected, target);
    }

    @Test
    void shouldThrowExceptionForSingleCharacterTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Character.BYTES);
        buffer.putChar('a');
        buffer.rewind();

        Character target = 'x';
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertFromByteBuffer(buffer, target)
        );
    }

    @Test
    void shouldThrowExceptionForNullTarget() {
        ByteBuffer buffer = ByteBuffer.allocate(Character.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertFromByteBuffer(buffer, null)
        );
    }

    @Test
    void shouldThrowExceptionForUnsupportedTargetType() {
        ByteBuffer buffer = ByteBuffer.allocate(Character.BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertFromByteBuffer(buffer, new Integer(1))
        );
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        assertThrows(IllegalArgumentException.class, () -> charData.getSizeArray(null));
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertToByteBuffer(ByteBuffer.allocate(4), null));
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertFromByteBuffer(ByteBuffer.allocate(4), null));
    }

    @Test
    void shouldThrowExceptionForNullElementsInBoxedArray() {
        Character[] array = {'a', null, 'c'};
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Character.BYTES);

        assertThrows(NullPointerException.class, () ->
                charData.convertToByteBuffer(buffer, array));
    }

    @Test
    void shouldThrowExceptionForNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> charData.createArr(-1));
    }

    @Test
    void shouldThrowExceptionForUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> charData.getSizeArray("unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertToByteBuffer(ByteBuffer.allocate(4), "unsupported"));
        assertThrows(IllegalArgumentException.class, () ->
                charData.convertFromByteBuffer(ByteBuffer.allocate(4), new float[1]));
    }
}