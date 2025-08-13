package com.omnixone.modbuslibrary.android;

import android.util.Log;

import com.omnixone.modbuslibrary.io.AbstractModbusTransport;
import com.omnixone.modbuslibrary.io.ModbusSerialTransport;
import com.omnixone.modbuslibrary.net.AbstractSerialConnection;
import com.omnixone.modbuslibrary.util.ModbusUtil;
import com.omnixone.modbuslibrary.util.SerialParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

/**
 * Android implementation of AbstractSerialConnection using usb-serial-for-android.
 * Wraps an AndroidUsbSerialPortIo to provide the j2mod expected API.
 */
public class AndroidUsbSerialConnection extends AbstractSerialConnection {

    private static final Logger logger = LoggerFactory.getLogger(AndroidUsbSerialConnection.class);
    private static final String TAG = "UsbConn";
    private static final boolean DEBUG = true;

    private final AndroidUsbSerialPortIo io;
    private final SerialParameters params;
    private final AbstractModbusTransport transport;

    private InputStream in;
    private OutputStream out;

    private boolean open = false;
    private int readTimeoutMs = 1000;
    private int writeTimeoutMs = 1000;

    // RS-485 controls (used only when rs485Mode == true)
    private final boolean rs485Mode;
    private final boolean txActiveHigh;
    private final int beforeTxUs;
    private final int afterTxUs;

    private static void sleepMicros(int us) {
        if (us <= 0) return;
        try { Thread.sleep(us / 1000, (us % 1000) * 1000); } catch (InterruptedException ignored) {}
    }

    public AndroidUsbSerialConnection(AndroidUsbSerialPortIo io,
                                      SerialParameters params,
                                      AbstractModbusTransport transport) throws IOException {
        this.io = io;
        this.params = params;
        this.transport = transport;

        this.rs485Mode   = params.getRs485Mode();
        this.txActiveHigh = params.getRs485TxEnableActiveHigh();
        this.beforeTxUs   = params.getRs485DelayBeforeTxMicroseconds();
        this.afterTxUs    = params.getRs485DelayAfterTxMicroseconds();

        if (transport instanceof ModbusSerialTransport) {
            ModbusSerialTransport t = (ModbusSerialTransport) transport;
            t.setCommPort(this);
            t.setEcho(params.isEcho());
        }
    }

    // -------- lifecycle --------

    @Override
    public void open() throws IOException {
        try {
            io.setTimeouts(readTimeoutMs, writeTimeoutMs);
            try {
                io.open();
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                if (m.contains("claim interface") || m.contains("Could not claim interface")) {
                    try { io.close(); } catch (Exception ignored) {}
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    io.open(); // retry once
                } else {
                    throw e;
                }
            }
            io.setParameters(params.getBaudRate(), params.getDatabits(), params.getStopbits(), params.getParity());
            in = io.getInputStream();
            out = io.getOutputStream();
            open = true;
        } catch (Exception e) {
            throw new IOException("Failed to open USB serial: " + e.getMessage(), e);
        }

    }

    @Override
    public void close() {
        if (DEBUG) Log.d(TAG, "close()");
        open = false;
        try { io.close(); } catch (Exception ignored) {}
    }

    @Override
    public boolean isOpen() { return open; }

    // -------- transport --------

    @Override
    public AbstractModbusTransport getModbusTransport() { return transport; }

    // -------- I/O --------

    @Override
    public int readBytes(byte[] buffer, int bytesToRead) {
        if (!open) return -1;
        int total = 0;
        long start = System.currentTimeMillis();
        try {
            while (total < bytesToRead) {
                int n = in.read(buffer, total, bytesToRead - total);
                if (n > 0) {
                    total += n;
                } else {
                    if (System.currentTimeMillis() - start >= readTimeoutMs) break;
                }
            }
            if (DEBUG && total > 0) {
                Log.d(TAG, "READ  (" + total + "): " + ModbusUtil.toHex(buffer, 0, total));
            }
        } catch (IOException e) {
            if (DEBUG) Log.e(TAG, "read error: " + e);
            return -1;
        }
        return total;
    }

    @Override
    public int writeBytes(byte[] buffer, int bytesToWrite) {
        logger.info("[USB] writeBytes() called — len={} : {}", bytesToWrite, ModbusUtil.toHex(buffer));
        if (!open) {
            logger.warn("[USB] Port not open — abort write");
            return -1;
        }
        int total = 0;
        try {
            if (rs485Mode) {
                logger.info("[USB] RS485 mode — enabling TX");
                try { io.setRTS(txActiveHigh); } catch (Exception e) { logger.error("[USB] setRTS failed", e); }
                try { io.setDTR(txActiveHigh); } catch (Exception e) { logger.error("[USB] setDTR failed", e); }
                sleepMicros(beforeTxUs);
            }

            while (total < bytesToWrite) {
                int len = Math.min(256, bytesToWrite - total);
                logger.debug("[USB] Writing chunk: {}", ModbusUtil.toHex(buffer, total, len));
                out.write(buffer, total, len);
                total += len;
            }
            out.flush();
            logger.info("[USB] All bytes written OK");

            if (rs485Mode) {
                sleepMicros(afterTxUs);
                logger.info("[USB] RS485 mode — switching back to RX");
                try { io.setRTS(!txActiveHigh); } catch (Exception e) { logger.error("[USB] setRTS failed", e); }
                try { io.setDTR(!txActiveHigh); } catch (Exception e) { logger.error("[USB] setDTR failed", e); }
            }
        } catch (IOException e) {
            logger.error("[USB] writeBytes() IOException: {}", e.getMessage(), e);
            return -1;
        }
        return total;
    }


    public void debugRawWrite(byte[] frame) {
        try {
            if (!open) { Log.w(TAG, "debugRawWrite: port not open"); return; }
            Log.i(TAG, "RAW WRITE (" + frame.length + "): " + ModbusUtil.toHex(frame, 0, frame.length));
            out.write(frame);
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "debugRawWrite error: " + e);
        }
    }

    private static String bytesToHex(byte[] buf, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(String.format("%02X ", buf[i]));
        return sb.toString().trim();
    }

    @Override
    public int bytesAvailable() {
        // usb-serial InputStream.available() is unreliable.
        // Return 1 so transport attempts a blocking read with our timeout.
        return open ? 1 : 0;
    }

    // -------- params / meta --------

    @Override public int getBaudRate()    { return params.getBaudRate(); }
    @Override public int getNumDataBits() { return params.getDatabits(); }
    @Override public int getNumStopBits() { return params.getStopbits(); }
    @Override public int getParity()      { return params.getParity(); }

    @Override public String getPortName()            { return io.getPortId(); }
    @Override public String getDescriptivePortName() { return io.getPortId(); }

    @Override
    public void setComPortTimeouts(int newTimeoutMode, int newReadTimeout, int newWriteTimeout) {
        this.readTimeoutMs = Math.max(0, newReadTimeout);
        this.writeTimeoutMs = Math.max(0, newWriteTimeout);
        io.setTimeouts(readTimeoutMs, writeTimeoutMs);
    }

    @Override public int getTimeout() { return readTimeoutMs; }

    @Override
    public void setTimeout(int timeout) {
        this.readTimeoutMs = timeout;
        this.writeTimeoutMs = timeout;
        io.setTimeouts(readTimeoutMs, writeTimeoutMs);
    }

    @Override
    public Set<String> getCommPorts() { return Collections.singleton(io.getPortId()); }
}
