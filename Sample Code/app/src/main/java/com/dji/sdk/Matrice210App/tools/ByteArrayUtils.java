package com.dji.sdk.Matrice210App.tools;

import java.nio.ByteBuffer;

public class ByteArrayUtils {
    public static byte[] intToByteArray(int x) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(0, x);
        return buffer.array();
    }

    public static int byteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes, 0, bytes.length);
        buffer.flip();//need flip
        return buffer.getInt();
    }

    public static byte [] float2ByteArray (float value) {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }

    public static float byteArrayToFloat(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getFloat();
    }

    public static void reverseEndianness(byte[] array) {
        if (null == array)
            return;

        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    public static String byteArrayToString(byte[] bytes) {
        StringBuilder buffer = new StringBuilder();
        for (byte b : bytes) {
            buffer.append((char) b);
        }
        return buffer.toString();
    }
}