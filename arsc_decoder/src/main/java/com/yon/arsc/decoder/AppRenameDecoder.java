
package com.yon.arsc.decoder;


import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;
import com.yon.arsc.exception.InvalidArscException;
import com.yon.arsc.structure.Header;
import com.yon.arsc.structure.ResConfigFlags;
import com.yon.arsc.structure.ResPackage;
import com.yon.arsc.structure.StringBlock;
import com.yon.arsc.util.ExtDataInput;

import com.yon.arsc.util.ExtDataOutput;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.math.BigInteger;

public class AppRenameDecoder {
    private final static short ENTRY_FLAG_COMPLEX = 0x0001;
    private final ExtDataInput mIn;
    private final CountingInputStream mCountIn;
    private static final int KNOWN_CONFIG_BYTES = 56;
    private Header mHeader;
    private StringBlock mTableStrings;
    private StringBlock mTypeNames;
    private StringBlock mSpecNames;
    private int mResId;
    private int mTypeIdOffset = 0;

    private String destStringName;
    private int appNameValueStart = -1;
    private int targetStringValueId = -1;

    public static void main(String[] args) {
        if (args == null || args.length < 3) {
            throw new RuntimeException("must provide originFilePath&outFilePath&destStringResName !");
        }
//         String originFilePath = "D:\\resources.arsc";
//         String outFilePath = "D:\\resources-out.arsc";
//         String destStringResName = "app_nam";
        String originFilePath = args[0];
        String outFilePath = args[1];
        String destStringResName = args[2];
        if (destStringResName == null || destStringResName.length() == 0) {
            throw new RuntimeException("must provide a special string resource name!");
        }
        File destFile = new File(originFilePath);
        FileInputStream in = null;
        ByteArrayInputStream fileStream = null;
        try {
            in = new FileInputStream(destFile);
            byte[] data = IOUtils.toByteArray(in);
            fileStream = new ByteArrayInputStream(data);
            AppRenameDecoder.decode(originFilePath, fileStream, destStringResName, outFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fileStream);
            IOUtils.closeQuietly(in);
        }
    }

    public AppRenameDecoder(InputStream arscStream) {
        arscStream = mCountIn = new CountingInputStream(arscStream);
        // We need to explicitly cast to DataInput as otherwise the constructor is ambiguous.
        // We choose DataInput instead of InputStream as ExtDataInput wraps an InputStream in
        // a DataInputStream which is big-endian and ignores the little-endian behavior.
        //noinspection UnstableApiUsage
        mIn = new ExtDataInput((DataInput) new LittleEndianDataInputStream(arscStream));
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void decode(String originFilePath, InputStream arscStream, String destStringName, String outPutFilePath)
            throws InvalidArscException {
        try {
            AppRenameDecoder decoder = new AppRenameDecoder(arscStream);
            decoder.destStringName = destStringName;
            decoder.readTableHeader();

            writeNewFile(originFilePath, outPutFilePath, decoder);
        } catch (IOException ex) {
            throw new InvalidArscException("Could not decode arsc file", ex);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void writeNewFile(String originFilePath, String outPutFilePath, AppRenameDecoder decoder) {
        if (decoder.targetStringValueId == -1) {
            return;
        }
        File destFile = new File(originFilePath);
        File outFile = new File(outPutFilePath);
        if (outFile.exists()) {
            outFile.delete();
        }
        FileInputStream in = null;
        ByteArrayInputStream fileStream = null;
        LittleEndianDataOutputStream output = null;
        ExtDataInput input = null;
        try {
            output = new LittleEndianDataOutputStream(new FileOutputStream(outPutFilePath));
            ExtDataOutput extOutput = new ExtDataOutput(output);
            in = new FileInputStream(destFile);
            byte[] data = IOUtils.toByteArray(in);
            fileStream = new ByteArrayInputStream(data);
            input = new ExtDataInput((DataInput) new LittleEndianDataInputStream(fileStream));
            System.out.println("data.length-> " + data.length + " , decoder.appNameValueStart->" + decoder.appNameValueStart);
            extOutput.writeBytes(input, decoder.appNameValueStart);
            extOutput.writeInt(decoder.targetStringValueId);
            input.skipInt();
            extOutput.writeBytes(input, data.length - decoder.appNameValueStart - 4);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fileStream);
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(output);
        }
    }

    private void readTableHeader() throws IOException, InvalidArscException {

        nextChunkCheckType(Header.TYPE_TABLE);
        int packageCount = mIn.readInt();
        mTableStrings = StringBlock.read(mIn);
        ResPackage[] packages = new ResPackage[packageCount];

        nextChunk();
        for (int i = 0; i < packageCount; i++) {
            mTypeIdOffset = 0;
            packages[i] = readTablePackage();
        }
    }

    private ResPackage readTablePackage() throws IOException, InvalidArscException {
        checkChunkType(Header.TYPE_PACKAGE);
        // If this is a base package, its ID.  Package IDs start
        // at 1 (corresponding to the value of the package bits in a
        // resource identifier).  0 means this is not a base package.
        int id = mIn.readInt();

        String name = mIn.readNullEndedString(128, true);
        System.out.printf("Decoding Shared Library (%s), pkgId: %d\n", name, id);
        /* typeStrings */
        mIn.skipInt();
        /* lastPublicType */
        mIn.skipInt();
        /* keyStrings */
        mIn.skipInt();
        /* lastPublicKey */
        mIn.skipInt();
        // TypeIdOffset was added platform_frameworks_base/@f90f2f8dc36e7243b85e0b6a7fd5a590893c827e
        // which is only in split/new applications.
        int splitHeaderSize = (2 + 2 + 4 + 4 + (2 * 128) + (4 * 5)); // short, short, int, int, char[128], int * 4
        if (mHeader.headerSize == splitHeaderSize) {
            mTypeIdOffset = mIn.readInt();
        }

        if (mTypeIdOffset > 0) {
            throw new InvalidArscException("typeIdOffset is not zero ");
        }
        mTypeNames = StringBlock.read(mIn);
        mSpecNames = StringBlock.read(mIn);

        mResId = id << 24;
        nextChunk();

        boolean flag = true;
        while (flag) {
            switch (mHeader.type) {
                case Header.TYPE_LIBRARY:
                    readLibraryType();
                    break;
                case Header.TYPE_SPEC_TYPE:
                    readTableTypeSpec();
                    break;
                default:
                    flag = false;
                    break;
            }
        }

        return new ResPackage();
    }

    private void readTableTypeSpec() throws InvalidArscException, IOException {
        readSingleTableTypeSpec();
        int type = nextChunk().type;
        while (type == Header.TYPE_SPEC_TYPE) {
            readSingleTableTypeSpec();
            type = nextChunk().type;
        }
        while (type == Header.TYPE_TYPE) {
            readTableType();

            // skip "TYPE 8 chunks" and/or padding data at the end of this chunk
            if (mCountIn.getCount() < mHeader.endPosition) {
                mCountIn.skip(mHeader.endPosition - mCountIn.getCount());
            }

            type = nextChunk().type;
        }
    }

    private void readTableType() throws IOException, InvalidArscException {
        checkChunkType(Header.TYPE_TYPE);
        int typeId = mIn.readUnsignedByte() - mTypeIdOffset;
        int typeFlags = mIn.readByte();
        /* reserved */
        mIn.skipBytes(2);
        int entryCount = mIn.readInt();
        int entriesStart = mIn.readInt();


        ResConfigFlags flags = readConfigFlags();
        int position = (mHeader.startPosition + entriesStart) - (entryCount * 4);

        // For some APKs there is a disconnect between the reported size of Configs
        // If we find a mismatch skip those bytes.
        if (position != mCountIn.getCount()) {
            mIn.skipBytes(position - mCountIn.getCount());
        }

        if (typeFlags == 1) {
//            System.out.println("Sparse type flags detected: " + mTypeSpec.getName());
        }
        int[] entryOffsets = mIn.readIntArray(entryCount);

        if (flags.isInvalid) {
//            String resName = mTypeSpec.getName() + flags.getQualifiers();
//            if (mKeepBroken) {
//                LOGGER.warning("Invalid config flags detected: " + resName);
//            } else {
//                LOGGER.warning("Invalid config flags detected. Dropping resources: " + resName);
//            }
        }


        for (int i = 0; i < entryOffsets.length; i++) {
            if (entryOffsets[i] != -1) {
                mResId = (mResId & 0xffff0000) | i;
                readEntry();
            }
        }
    }


    private void readEntry() throws IOException, InvalidArscException {
        short size = mIn.readShort();
        if (size < 0) {
            throw new InvalidArscException("Entry size is under 0 bytes.");
        }
        short flags = mIn.readShort();
        int specNamesId = mIn.readInt();//Reference into ResTable_package::keyStrings identifying this entry.
        String entryName = mSpecNames.getString(specNamesId);
        boolean isAppName = isStringValue && entryName.equals("app_name");
        if (isAppName && appNameValueStart == -1) {
            appNameValueStart = mCountIn.getCount() + 4;
        }
        boolean isDestString = isStringValue && entryName.equals(destStringName);
        if ((flags & ENTRY_FLAG_COMPLEX) == 0) {
            if (isDestString && targetStringValueId == -1) {
                targetStringValueId = readValue();
                System.out.println("find target key " + destStringName + ", string id " + targetStringValueId + ", value " + mTableStrings.getString(targetStringValueId));
            } else {
                readValue();
            }
        } else {
            readComplexEntry();
        }
    }

    private void readComplexEntry() throws IOException {
        int parent = mIn.readInt();
        int count = mIn.readInt();

        int resId;

        for (int i = 0; i < count; i++) {
            resId = mIn.readInt();
            readValue();
        }
    }


    private int readValue() throws IOException {
        /* size */
        mIn.skipCheckShort((short) 8);
        /* zero */
        mIn.skipCheckByte((byte) 0);
        byte type = mIn.readByte();
        /* data */
        return mIn.readInt();
    }

    private ResConfigFlags readConfigFlags() throws IOException, InvalidArscException {
        int size = mIn.readInt();
        int read = 28;

        if (size < 28) {
            throw new InvalidArscException("Config size < 28");
        }

        boolean isInvalid = false;

        short mcc = mIn.readShort();
        short mnc = mIn.readShort();

        char[] language = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), 'a');
        char[] country = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), '0');

        byte orientation = mIn.readByte();
        byte touchscreen = mIn.readByte();

        int density = mIn.readUnsignedShort();

        byte keyboard = mIn.readByte();
        byte navigation = mIn.readByte();
        byte inputFlags = mIn.readByte();
        /* inputPad0 */
        mIn.skipBytes(1);

        short screenWidth = mIn.readShort();
        short screenHeight = mIn.readShort();

        short sdkVersion = mIn.readShort();
        /* minorVersion, now must always be 0 */
        mIn.skipBytes(2);

        byte screenLayout = 0;
        byte uiMode = 0;
        short smallestScreenWidthDp = 0;
        if (size >= 32) {
            screenLayout = mIn.readByte();
            uiMode = mIn.readByte();
            smallestScreenWidthDp = mIn.readShort();
            read = 32;
        }

        short screenWidthDp = 0;
        short screenHeightDp = 0;
        if (size >= 36) {
            screenWidthDp = mIn.readShort();
            screenHeightDp = mIn.readShort();
            read = 36;
        }

        char[] localeScript = null;
        char[] localeVariant = null;
        if (size >= 48) {
            localeScript = readScriptOrVariantChar(4).toCharArray();
            localeVariant = readScriptOrVariantChar(8).toCharArray();
            read = 48;
        }

        byte screenLayout2 = 0;
        byte colorMode = 0;
        if (size >= 52) {
            screenLayout2 = mIn.readByte();
            colorMode = mIn.readByte();
            mIn.skipBytes(2); // reserved padding
            read = 52;
        }

        if (size >= 56) {
            mIn.skipBytes(4);
            read = 56;
        }

        int exceedingSize = size - KNOWN_CONFIG_BYTES;
        if (exceedingSize > 0) {
            byte[] buf = new byte[exceedingSize];
            read += exceedingSize;
            mIn.readFully(buf);
            BigInteger exceedingBI = new BigInteger(1, buf);

            if (exceedingBI.equals(BigInteger.ZERO)) {
//                System.out.printf("Config flags size > %d, but exceeding bytes are all zero, so it should be ok.%n",
//                        KNOWN_CONFIG_BYTES);
            } else {
//                System.out.printf("Config flags size > %d. Size = %d. Exceeding bytes: 0x%X.%n",
//                        KNOWN_CONFIG_BYTES, size, exceedingBI);
                isInvalid = true;
            }
        }

        int remainingSize = size - read;
        if (remainingSize > 0) {
            mIn.skipBytes(remainingSize);
        }

        return new ResConfigFlags(mcc, mnc, language, country,
                orientation, touchscreen, density, keyboard, navigation,
                inputFlags, screenWidth, screenHeight, sdkVersion,
                screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp,
                screenHeightDp, localeScript, localeVariant, screenLayout2,
                colorMode, isInvalid, size);
    }

    private char[] unpackLanguageOrRegion(byte in0, byte in1, char base) {
        // check high bit, if so we have a packed 3 letter code
        if (((in0 >> 7) & 1) == 1) {
            int first = in1 & 0x1F;
            int second = ((in1 & 0xE0) >> 5) + ((in0 & 0x03) << 3);
            int third = (in0 & 0x7C) >> 2;

            // since this function handles languages & regions, we add the value(s) to the base char
            // which is usually 'a' or '0' depending on language or region.
            return new char[]{(char) (first + base), (char) (second + base), (char) (third + base)};
        }
        return new char[]{(char) in0, (char) in1};
    }

    private String readScriptOrVariantChar(int length) throws IOException {
        StringBuilder string = new StringBuilder(16);

        while (length-- != 0) {
            short ch = mIn.readByte();
            if (ch == 0) {
                break;
            }
            string.append((char) ch);
        }
        mIn.skipBytes(length);

        return string.toString();
    }

    private void readLibraryType() throws InvalidArscException, IOException {
        checkChunkType(Header.TYPE_LIBRARY);
        int libraryCount = mIn.readInt();

        int packageId;
        String packageName;

        for (int i = 0; i < libraryCount; i++) {
            packageId = mIn.readInt();
            packageName = mIn.readNullEndedString(128, true);
            System.out.printf("Decoding Shared Library (%s), pkgId: %d%n", packageName, packageId);
        }

        while (nextChunk().type == Header.TYPE_TYPE) {
            readTableTypeSpec();
        }
    }

    private boolean isStringValue = false;

    private void readSingleTableTypeSpec() throws InvalidArscException, IOException {
        checkChunkType(Header.TYPE_SPEC_TYPE);
        // The type identifier this chunk is holding.  Type IDs start
        // at 1 (corresponding to the value of the type bits in a
        // resource identifier).  0 is invalid.
        // 资源的类型有 animator、anim、color、drawable、layout、menu、raw、string 和 xml 等等若干种，每一种都会被赋予一个 ID
        int id = mIn.readUnsignedByte();
        String tableName = mTypeNames.getString(id - 1);
        isStringValue = tableName.equals("string");
        System.out.println("read table type " + tableName);
        mIn.skipBytes(3);
        int entryCount = mIn.readInt();

        /* flags */
        //紧接着后面的是 entryCount 个 uint_32 数组，数组每个元素都是用来描述资源项的配置差异性的
        mIn.skipBytes(entryCount * 4);
    }

    private Header nextChunk() throws IOException {
        return mHeader = Header.read(mIn, mCountIn);
    }

    private void checkChunkType(int expectedType) throws InvalidArscException {
        if (mHeader.type != expectedType) {
            throw new InvalidArscException(String.format("Invalid chunk type: expected=0x%08x, got=0x%08x",
                    expectedType, mHeader.type));
        }
    }

    private void nextChunkCheckType(int expectedType) throws IOException, InvalidArscException {
        nextChunk();
        checkChunkType(expectedType);
    }

}
