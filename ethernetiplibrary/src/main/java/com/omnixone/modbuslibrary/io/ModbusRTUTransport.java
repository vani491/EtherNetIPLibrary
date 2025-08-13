/*
 * Copyright 2002-2016 jamod & j2mod development teams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.omnixone.modbuslibrary.io;

import com.omnixone.modbuslibrary.Modbus;
import com.omnixone.modbuslibrary.ModbusIOException;
import com.omnixone.modbuslibrary.msg.ModbusMessage;
import com.omnixone.modbuslibrary.msg.ModbusRequest;
import com.omnixone.modbuslibrary.msg.ModbusResponse;
import com.omnixone.modbuslibrary.net.AbstractModbusListener;
import com.omnixone.modbuslibrary.util.ModbusUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * Class that implements the ModbusRTU transport flavor.
 *
 * @author John Charlton
 * @author Dieter Wimberger
 * @author Julie Haugh
 * @author Steve O'Hara (4NG)
 * @version 2.0 (March 2016)
 */
public class ModbusRTUTransport extends ModbusSerialTransport {

    private static final Logger logger = LoggerFactory.getLogger(ModbusRTUTransport.class);

    private final byte[] inBuffer = new byte[Modbus.MAX_MESSAGE_LENGTH];
    private final BytesInputStream byteInputStream = new BytesInputStream(inBuffer); // to read message from
    private final BytesOutputStream byteInputOutputStream = new BytesOutputStream(inBuffer); // to buffer message to
    private final BytesOutputStream byteOutputStream = new BytesOutputStream(Modbus.MAX_MESSAGE_LENGTH); // write frames
    private byte[] lastRequest = null;

    /**
     * Read the data for a request of a given fixed size
     *
     * @param byteCount Byte count excluding the 2 byte CRC
     * @param out       Output buffer to populate
     * @throws IOException If data cannot be read from the port
     */
    private void readRequestData(int byteCount, BytesOutputStream out) throws IOException {
        byteCount += 2;
        byte[] inpBuf = new byte[byteCount];
        readBytes(inpBuf, byteCount);
        out.write(inpBuf, 0, byteCount);
    }

    /**
     * getRequest - Read a request, after the unit and function code
     *
     * @param function - Modbus function code
     * @param out      - Byte stream buffer to hold actual message
     */
    private void getRequest(int function, BytesOutputStream out) throws IOException {
        int byteCount;
        byte[] inpBuf = new byte[256];
        try {
            if ((function & 0x80) == 0) {
                switch (function) {
                    case Modbus.READ_EXCEPTION_STATUS:
                    case Modbus.READ_COMM_EVENT_COUNTER:
                    case Modbus.READ_COMM_EVENT_LOG:
                    case Modbus.REPORT_SLAVE_ID:
                        readRequestData(0, out);
                        break;

                    case Modbus.READ_FIFO_QUEUE:
                        readRequestData(2, out);
                        break;

                    case Modbus.READ_MEI:
                        readRequestData(3, out);
                        break;

                    case Modbus.READ_COILS:
                    case Modbus.READ_INPUT_DISCRETES:
                    case Modbus.READ_MULTIPLE_REGISTERS:
                    case Modbus.READ_INPUT_REGISTERS:
                    case Modbus.WRITE_COIL:
                    case Modbus.WRITE_SINGLE_REGISTER:
                        readRequestData(4, out);
                        break;
                    case Modbus.MASK_WRITE_REGISTER:
                        readRequestData(6, out);
                        break;

                    case Modbus.READ_FILE_RECORD:
                    case Modbus.WRITE_FILE_RECORD:
                        byteCount = readByte();
                        out.write(byteCount);
                        readRequestData(byteCount, out);
                        break;

                    case Modbus.WRITE_MULTIPLE_COILS:
                    case Modbus.WRITE_MULTIPLE_REGISTERS:
                        readBytes(inpBuf, 4);
                        out.write(inpBuf, 0, 4);
                        byteCount = readByte();
                        out.write(byteCount);
                        readRequestData(byteCount, out);
                        break;

                    case Modbus.READ_WRITE_MULTIPLE:
                        readRequestData(8, out);
                        byteCount = readByte();
                        out.write(byteCount);
                        readRequestData(byteCount, out);
                        break;

                    default:
                        throw new IOException(String.format("getResponse unrecognised function code [%s]", function));
                }
            }
        }
        catch (IOException e) {
            throw new IOException("getResponse serial port exception");
        }
    }

    /**
     * getResponse - Read a <tt>ModbusResponse</tt> from a slave.
     *
     * @param function The function code of the request
     * @param out      The output buffer to put the result
     * @throws IOException If data cannot be read from the port
     */
    private void getResponse(int function, BytesOutputStream out) throws IOException {
        byte[] inpBuf = new byte[256];
        try {
            if ((function & 0x80) == 0) {
                switch (function) {
                    case Modbus.READ_COILS:
                    case Modbus.READ_INPUT_DISCRETES:
                    case Modbus.READ_MULTIPLE_REGISTERS:
                    case Modbus.READ_INPUT_REGISTERS:
                    case Modbus.READ_COMM_EVENT_LOG:
                    case Modbus.REPORT_SLAVE_ID:
                    case Modbus.READ_FILE_RECORD:
                    case Modbus.WRITE_FILE_RECORD:
                    case Modbus.READ_WRITE_MULTIPLE:
                        // Read the data payload byte count. There will be two
                        // additional CRC bytes afterwards.
                        int cnt = readByte();
                        out.write(cnt);
                        readRequestData(cnt, out);
                        break;

                    case Modbus.WRITE_COIL:
                    case Modbus.WRITE_SINGLE_REGISTER:
                    case Modbus.READ_COMM_EVENT_COUNTER:
                    case Modbus.WRITE_MULTIPLE_COILS:
                    case Modbus.WRITE_MULTIPLE_REGISTERS:
                    case Modbus.READ_SERIAL_DIAGNOSTICS:
                        // read status: only the CRC remains after the two data
                        // words.
                        readRequestData(4, out);
                        break;

                    case Modbus.READ_EXCEPTION_STATUS:
                        // read status: only the CRC remains after exception status
                        // byte.
                        readRequestData(1, out);
                        break;

                    case Modbus.MASK_WRITE_REGISTER:
                        // eight bytes in addition to the address and function codes
                        readRequestData(6, out);
                        break;

                    case Modbus.READ_FIFO_QUEUE:
                        int b1;
                        int b2;
                        b1 = (byte) (readByte() & 0xFF);
                        out.write(b1);
                        b2 = (byte) (readByte() & 0xFF);
                        out.write(b2);
                        int byteCount = ModbusUtil.makeWord(b1, b2);
                        readRequestData(byteCount, out);
                        break;

                    case Modbus.READ_MEI:
                        // read the subcode. We only support 0x0e.
                        int sc = readByte();
                        if (sc != 0x0e) {
                            throw new IOException("Invalid subfunction code");
                        }
                        out.write(sc);
                        // next few bytes are just copied.
                        int id;
                        int fieldCount;
                        readBytes(inpBuf, 5);
                        out.write(inpBuf, 0, 5);
                        fieldCount = (int) inpBuf[4];
                        for (int i = 0; i < fieldCount; i++) {
                            id = readByte();
                            out.write(id);
                            int len = readByte();
                            out.write(len);
                            readBytes(inpBuf, len);
                            out.write(inpBuf, 0, len);
                        }
                        if (fieldCount == 0) {
                            int err = readByte();
                            out.write(err);
                        }
                        // now get the 2 CRC bytes
                        readRequestData(0, out);
                        break;

                    default:
                        throw new IOException(String.format("getResponse unrecognised function code [%s]", function));

                }
            }
            else {
                // read the exception code, plus two CRC bytes.
                readRequestData(1, out);

            }
        }
        catch (IOException e) {
            throw new IOException(String.format("getResponse serial port exception - %s", e.getMessage()));
        }
    }

    /**
     * Writes the Modbus message to the comms port
     *
     * @param msg a <code>ModbusMessage</code> value
     * @throws ModbusIOException If an error occurred bundling the message
     */
/*    @Override
    protected void writeMessageOut(ModbusMessage msg) throws ModbusIOException {
        // Build full RTU frame: unitId + function + data + CRC
        byte[] pdu = msg.getMessage(); // unitId+fc+data or sometimes just data depending on j2mod build
        byte[] frame;

        if (pdu != null && pdu.length >= 2) {
            // pdu already includes unit + function (most j2mod builds)
            int[] crc = ModbusUtil.calculateCRC(pdu, 0, pdu.length);
            frame = Arrays.copyOf(pdu, pdu.length + 2);
            frame[frame.length - 2] = (byte) crc[0]; // CRC Lo
            frame[frame.length - 1] = (byte) crc[1]; // CRC Hi
        } else {
            // fallback: use hex message
            String hex = msg.getHexMessage().replace(" ", "");
            byte[] tmp = new byte[hex.length() / 2];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
            }
            int[] crc = ModbusUtil.calculateCRC(tmp, 0, tmp.length);
            frame = Arrays.copyOf(tmp, tmp.length + 2);
            frame[frame.length - 2] = (byte) crc[0];
            frame[frame.length - 1] = (byte) crc[1];
        }

        logger.info("[RTU] TX frame ({} bytes): {}", frame.length, ModbusUtil.toHex(frame));

        // Robust write: loop until all bytes written, log progress
        int toWrite = frame.length;
        int offset = 0;
        int attempts = 0;
        while (toWrite > 0) {
            attempts++;
            int n = getCommPort().writeBytes(frame, toWrite);
            logger.info("[RTU] writeBytes attempt {} -> {}", attempts, n);
            if (n < 0) {
                throw new ModbusIOException("I/O failed to write (returned " + n + ")");
            }
            offset += n;
            toWrite -= n;

            // Safety: avoid tight loop if driver ever returns 0
            if (n == 0) {
                ModbusUtil.sleep(2);
            }
        }
        logger.info("[RTU] write complete ({} attempts)", attempts);

        // If you use RS-485 echo mode (usually false), read echo here:
        // if (isEcho()) { readEcho(frame.length); }
    }*/



    @Override
    protected void writeMessageOut(ModbusMessage msg) throws ModbusIOException {
        // Build PDU = [unitId][function][data...]
        if (!(msg instanceof ModbusResponse)) {
            throw new ModbusIOException("Expected ModbusResponse");
        }
        ModbusResponse res = (ModbusResponse) msg;

        byte[] data = msg.getMessage(); // may be just byteCount+payload
        int dataLen = (data == null) ? 0 : data.length;

        byte[] pdu = new byte[2 + dataLen];
        pdu[0] = (byte) res.getUnitID();
        pdu[1] = (byte) res.getFunctionCode();
        if (dataLen > 0) System.arraycopy(data, 0, pdu, 2, dataLen);

        // CRC over full PDU
        int[] crc = ModbusUtil.calculateCRC(pdu, 0, pdu.length); // [lo, hi]

        // Final RTU frame = PDU + CRC
        byte[] frame = Arrays.copyOf(pdu, pdu.length + 2);
        frame[frame.length - 2] = (byte) crc[0]; // CRC lo
        frame[frame.length - 1] = (byte) crc[1]; // CRC hi

        // Write all bytes (handle short writes)
        int remaining = frame.length;
        int offset = 0;
        while (remaining > 0) {
            int n = getCommPort().writeBytes(frame, remaining);
            if (n <= 0) throw new ModbusIOException("I/O failed to write");
            offset += n;
            remaining -= n;
            // small yield if a driver ever returns tiny chunks
            if (n < 16) ModbusUtil.sleep(1);
        }

        // If you use RS-485 echo mode, uncomment:
        // if (isEcho()) readEcho(frame.length);
    }


    @Override
    protected ModbusRequest readRequestIn(AbstractModbusListener listener) throws ModbusIOException {
        ModbusRequest request = null;

        try {
            while (request == null) {
                synchronized (byteInputStream) {
                    int uid = readByte();

                    byteInputOutputStream.reset();
                    byteInputOutputStream.writeByte(uid);

                    if (listener.getProcessImage(uid) != null) {
                        // Read a proper request

                        int fc = readByte();
                        byteInputOutputStream.writeByte(fc);

                        // create request to acquire length of message
                        request = ModbusRequest.createModbusRequest(fc);
                        request.setHeadless();

                        /*
                         * With Modbus RTU, there is no end frame. Either we
                         * assume the message is complete as is or we must do
                         * function specific processing to know the correct
                         * length. To avoid moving frame timing to the serial
                         * input functions, we set the timeout and to message
                         * specific parsing to read a response.
                         */
                        getRequest(fc, byteInputOutputStream);
                        int dlength = byteInputOutputStream.size() - 2; // less the crc
                        if (logger.isDebugEnabled()) {
                            logger.debug("Request: {}", ModbusUtil.toHex(byteInputOutputStream.getBuffer(), 0, dlength + 2));
                        }

                        byteInputStream.reset(inBuffer, dlength);

                        // check CRC
                        int[] crc = ModbusUtil.calculateCRC(inBuffer, 0, dlength); // does not include CRC
                        if (ModbusUtil.unsignedByteToInt(inBuffer[dlength]) != crc[0] || ModbusUtil.unsignedByteToInt(inBuffer[dlength + 1]) != crc[1]) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("CRC should be {}, {}", Integer.toHexString(crc[0]), Integer.toHexString(crc[1]));
                            }

                            // Drain the input in case the frame was misread and more
                            // was to follow.
                            clearInput();
                            throw new IOException("CRC Error in received frame: " + dlength + " bytes: " + ModbusUtil.toHex(byteInputStream.getBuffer(), 0, dlength));
                        }

                        // read request
                        byteInputStream.reset(inBuffer, dlength);
                        request.readFrom(byteInputStream);

                        return request;

                    }
                    else {
                        // This message is not for us, read and wait for the 3.5t delay

                        // Wait for max 1.5t for data to be available
                        while (true) {
                            boolean bytesAvailable = availableBytes() > 0;
                            if (!bytesAvailable) {
                                // Sleep the 1.5t to see if there will be more data
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Waiting for {} microsec", getMaxCharDelay());
                                }
                                bytesAvailable = spinUntilBytesAvailable(getMaxCharDelay());
                            }

                            if (bytesAvailable) {
                                // Read the available data
                                while (availableBytes() > 0) {
                                    byteInputOutputStream.writeByte(readByte());
                                }
                            }
                            else {
                                // Transition to wait for the 3.5t interval
                                break;
                            }
                        }

                        // Wait for 2t to complete the 3.5t wait
                        // Is there is data available the interval was not respected, we should discard the message
                        if (logger.isDebugEnabled()) {
                            logger.debug("Waiting for {} microsec", getCharIntervalMicro(2));
                        }
                        if (spinUntilBytesAvailable(getCharIntervalMicro(2))) {
                            // Discard the message
                            if (logger.isDebugEnabled()) {
                                logger.debug("Discarding message (More than 1.5t between characters!) - {}", ModbusUtil.toHex(byteInputOutputStream.getBuffer(), 0, byteInputOutputStream.size()));
                            }
                        }
                        else {
                            // This message is complete
                            if (logger.isDebugEnabled()) {
                                logger.debug("Read message not meant for us: {}", ModbusUtil.toHex(byteInputOutputStream.getBuffer(), 0, byteInputOutputStream.size()));
                            }
                        }
                    }
                }
            }

            // We will never get here
            return null;
        }
        catch (IOException ex) {
            // An exception mostly means there is no request. The master should
            // retry the request.

            if (logger.isDebugEnabled()) {
                logger.debug("Failed to read response! {}", ex.getMessage());
            }

            return null;
        }
    }

    /**
     * readResponse - Read the bytes for the response from the slave.
     *
     * @return a <tt>ModbusRespose</tt>
     *
     * @throws ModbusIOException If the response cannot be read from the socket/port
     */
    @Override
    protected ModbusResponse readResponseIn() throws ModbusIOException {
        boolean done;
        ModbusResponse response;
        int dlength;

        try {
            do {
                // 1. read to function code, create request and read function
                // specific bytes
                synchronized (byteInputStream) {
                    int uid = readByte();

                    if (uid != -1) {
                        int fc = readByte();
                        byteInputOutputStream.reset();
                        byteInputOutputStream.writeByte(uid);
                        byteInputOutputStream.writeByte(fc);

                        // create response to acquire length of message
                        response = ModbusResponse.createModbusResponse(fc);
                        response.setHeadless();

                        /*
                         * With Modbus RTU, there is no end frame. Either we
                         * assume the message is complete as is or we must do
                         * function specific processing to know the correct
                         * length. To avoid moving frame timing to the serial
                         * input functions, we set the timeout and to message
                         * specific parsing to read a response.
                         */
                        getResponse(fc, byteInputOutputStream);
                        dlength = byteInputOutputStream.size() - 2; // less the crc
                        if (logger.isDebugEnabled()) {
                            logger.debug("Response: {}", ModbusUtil.toHex(byteInputOutputStream.getBuffer(), 0, dlength + 2));
                        }
                        byteInputStream.reset(inBuffer, dlength);

                        // check CRC
                        int[] crc = ModbusUtil.calculateCRC(inBuffer, 0, dlength); // does not include CRC
                        if (ModbusUtil.unsignedByteToInt(inBuffer[dlength]) != crc[0] || ModbusUtil.unsignedByteToInt(inBuffer[dlength + 1]) != crc[1]) {
                            logger.debug("CRC should be {}, {}", crc[0], crc[1]);
                            throw new IOException("CRC Error in received frame: " + dlength + " bytes: " + ModbusUtil.toHex(byteInputStream.getBuffer(), 0, dlength));
                        }
                    }
                    else {
                        throw new IOException("Error reading response");
                    }

                    // read response
                    byteInputStream.reset(inBuffer, dlength);
                    response.readFrom(byteInputStream);
                    done = true;
                }
            } while (!done);
            return response;
        }
        catch (IOException ex) {
            // FIXME: This printout is wrong when reading response from other slave
            throw new ModbusIOException("I/O exception - failed to read response for request [%s] - %s", ModbusUtil.toHex(lastRequest), ex.getMessage());
        }
    }
}
