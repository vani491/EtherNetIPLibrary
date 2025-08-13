package com.omnixone.modbuslibrary.android;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.omnixone.modbuslibrary.serial.SerialPortIo;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Usb-serial I/O with buffered reads (avoids FTDI "Read buffer too small"). */
public class AndroidUsbSerialPortIo implements SerialPortIo {

    private static final String TAG = "UsbPortIo";

    public static final int DATABITS_5 = 5;
    public static final int DATABITS_6 = 6;
    public static final int DATABITS_7 = 7;
    public static final int DATABITS_8 = 8;

    public static final int STOPBITS_1   = 1;
    public static final int STOPBITS_2   = 2;
    public static final int STOPBITS_1_5 = 3;

    public static final int PARITY_NONE = 0;
    public static final int PARITY_ODD  = 1;
    public static final int PARITY_EVEN = 2;
    public static final int PARITY_MARK = 3;
    public static final int PARITY_SPACE= 4;

    private final UsbSerialPort port;
    private final UsbDeviceConnection connection;
    private int readTimeoutMs = 1000, writeTimeoutMs = 1000;

    private InputStream in;
    private OutputStream out;

    // RX cache so we can satisfy small reads from a bigger driver read
    private final byte[] rxCache = new byte[4096];
    private int rxHead = 0;   // next index to read
    private int rxSize = 0;   // number of valid bytes in cache

    public AndroidUsbSerialPortIo(UsbSerialPort port, UsbDeviceConnection connection) {
        this.port = port;
        this.connection = connection;
    }

    private int cacheGet(byte[] dst, int off, int len) {
        int n = Math.min(len, rxSize);
        for (int i = 0; i < n; i++) {
            dst[off + i] = rxCache[(rxHead + i) % rxCache.length];
        }
        rxHead = (rxHead + n) % rxCache.length;
        rxSize -= n;
        return n;
    }

    private void cachePut(byte[] src, int n) {
        int tail = (rxHead + rxSize) % rxCache.length;
        for (int i = 0; i < n; i++) {
            rxCache[(tail + i) % rxCache.length] = src[i];
        }
        rxSize = Math.min(rxSize + n, rxCache.length);
    }

    @Override
    public void open() throws Exception {
        port.open(connection);

        in = new InputStream() {
            private final byte[] one = new byte[1];

            @Override public int read() throws IOException {
                int n = read(one, 0, 1);
                return (n == -1) ? -1 : (one[0] & 0xFF);
            }

            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (b == null) throw new NullPointerException();
                if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
                if (len == 0) return 0;

                int total = 0;
                long start = System.currentTimeMillis();

                while (total < len) {
                    // serve from cache first
                    if (rxSize > 0) {
                        total += cacheGet(b, off + total, len - total);
                        continue;
                    }
                    // cache empty: read a bigger chunk from driver (FTDI needs >=3)
                    byte[] tmp = new byte[512];
                    int n = port.read(tmp, readTimeoutMs);
                    if (n > 0) {
                        cachePut(tmp, n);
                        total += cacheGet(b, off + total, len - total);
                    } else {
                        // timeout / no data
                        if (total == 0) return -1;
                        break;
                    }
                    if (System.currentTimeMillis() - start >= readTimeoutMs) break;
                }
                return (total == 0) ? -1 : total;
            }
        };

        out = new OutputStream() {
            @Override public void write(int oneByte) throws IOException {
                byte[] buf = new byte[]{(byte) oneByte};
                Log.d(TAG, "port.write(1): " + String.format("%02X", buf[0]));
                port.write(buf, writeTimeoutMs);
            }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                byte[] tmp;
                if (off == 0 && len == b.length) {
                    tmp = b;
                } else {
                    tmp = new byte[len];
                    System.arraycopy(b, off, tmp, 0, len);
                }
                // log first up to 32 bytes for brevity
                StringBuilder sb = new StringBuilder();
                int show = Math.min(len, 32);
                for (int i = 0; i < show; i++) sb.append(String.format("%02X ", tmp[i]));
                Log.d(TAG, "port.write(" + len + "): " + sb.toString().trim() + (len>show?" ...":""));
                port.write(tmp, writeTimeoutMs);
            }
            @Override public void flush() {}
        };

        // IMPORTANT: do NOT force DTR/RTS high here (auto-direction adapters handle DE/RE).
        try { port.setDTR(false); } catch (Exception ignored) {}
        try { port.setRTS(false); } catch (Exception ignored) {}
    }

    @Override public void close() {
        try { port.close(); } catch (Exception ignored) {}
        try { connection.close(); } catch (Exception ignored) {}
    }

    @Override public void setParameters(int baud, int dataBits, int stopBits, int parity) throws Exception {
        port.setParameters(baud, dataBits, stopBits, parity);
        // Leave DTR/RTS low for auto-direction RS-485 dongles
        try { port.setDTR(false); } catch (Exception ignored) {}
        try { port.setRTS(false); } catch (Exception ignored) {}
    }

    @Override public void setDTR(boolean v) throws Exception { port.setDTR(v); }
    @Override public void setRTS(boolean v) throws Exception { port.setRTS(v); }

    @Override public void setTimeouts(int readMillis, int writeMillis) {
        this.readTimeoutMs = readMillis;
        this.writeTimeoutMs = writeMillis;
    }

    @Override public InputStream getInputStream() { return in; }
    @Override public OutputStream getOutputStream() { return out; }
    @Override public String getPortId() { return "usb-serial-for-android:" + port.getClass().getSimpleName(); }
}
