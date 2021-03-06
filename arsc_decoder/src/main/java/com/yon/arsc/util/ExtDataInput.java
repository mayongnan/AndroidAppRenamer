package com.yon.arsc.util;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ExtDataInput extends DataInputDelegate {
    public ExtDataInput(InputStream in) {
        this((DataInput) new DataInputStream(in));
    }

    public ExtDataInput(DataInput delegate) {
        super(delegate);
    }

    public int[] readIntArray(int length) throws IOException {
        int[] array = new int[length];
        for(int i = 0; i < length; i++) {
            array[i] = readInt();
        }
        return array;
    }

    public void skipInt() throws IOException {
        skipBytes(4);
    }

    public void skipCheckInt(int expected1, int expected2) throws IOException {
        int got = readInt();
        if (got != expected1 && got != expected2) {
            throw new IOException(String.format(
                    "Expected: 0x%08x or 0x%08x, got: 0x%08x", expected1, expected2, got));
        }
    }

    public void skipCheckShort(short expected) throws IOException {
        short got = readShort();
        if (got != expected) {
            throw new IOException(String.format(
                    "Expected: 0x%08x, got: 0x%08x", expected, got));
        }
    }

    public void skipCheckByte(byte expected) throws IOException {
        byte got = readByte();
        if (got != expected) {
            throw new IOException(String.format(
                    "Expected: 0x%08x, got: 0x%08x", expected, got));
        }
    }

    public void skipCheckChunkTypeInt(int expected, int possible) throws IOException {
        int got = readInt();

        if (got == possible || got < expected) {
            skipCheckChunkTypeInt(expected, -1);
        } else if (got != expected) {
            throw new IOException(String.format("Expected: 0x%08x, got: 0x%08x", expected, got));
        }
    }

    /**
     * The general contract of DataInput doesn't guarantee all the bytes requested will be skipped
     * and failure can occur for many reasons. We override this to try harder to skip all the bytes
     * requested (this is similar to DataInputStream's wrapper).
     */
    public final int skipBytes(int n) throws IOException {
        int total = 0;
        int cur;

        while ((total < n) && ((cur = super.skipBytes(n - total)) > 0)) {
            total += cur;
        }

        return total;
    }

    public String readNullEndedString(int length, boolean fixed)
            throws IOException {
        StringBuilder string = new StringBuilder(16);
        while(length-- != 0) {
            short ch = readShort();
            if (ch == 0) {
                break;
            }
            string.append((char) ch);
        }
        if (fixed) {
            skipBytes(length * 2);
        }

        return string.toString();
    }
}