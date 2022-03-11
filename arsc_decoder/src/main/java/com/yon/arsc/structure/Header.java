package com.yon.arsc.structure;

import com.yon.arsc.util.ExtDataInput;

import com.yon.arsc.util.ExtDataOutput;
import org.apache.commons.io.input.CountingInputStream;

import java.io.EOFException;
import java.io.IOException;

public class Header {
    public final short type;
    public final int headerSize;
    public final int chunkSize;
    public final int startPosition;
    public final int endPosition;

    public Header(short type, int headerSize, int chunkSize, int headerStart) {
        this.type = type;
        this.headerSize = headerSize;
        this.chunkSize = chunkSize;
        this.startPosition = headerStart;
        this.endPosition = headerStart + chunkSize;
    }

    public static Header read(ExtDataInput in, CountingInputStream countIn) throws IOException {
        short type;
        int start = countIn.getCount();
        try {
            type = in.readShort();
        } catch (EOFException ex) {
            return new Header(TYPE_NONE, 0, 0, countIn.getCount());
        }
        return new Header(type, in.readShort(), in.readInt(), start);
    }

    public static Header readAndWrite(ExtDataInput in, CountingInputStream countIn, ExtDataOutput out) throws IOException {
        short type;
        int start = countIn.getCount();
        try {
            type = in.readShort();
        } catch (EOFException ex) {
            return new Header(TYPE_NONE, 0, 0, countIn.getCount());
        }
        int headerSize = in.readShort();
        int chunkSize = in.readInt();
        out.writeShort(type);
        out.writeShort(headerSize);
        out.writeInt(chunkSize);
        return new Header(type, headerSize, chunkSize, start);
    }

    public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002,
            TYPE_PACKAGE = 0x0200, TYPE_TYPE = 0x0201, TYPE_SPEC_TYPE = 0x0202, TYPE_LIBRARY = 0x0203;
}