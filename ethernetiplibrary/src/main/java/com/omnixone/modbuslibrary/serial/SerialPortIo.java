package com.omnixone.modbuslibrary.serial;

import java.io.InputStream;
import java.io.OutputStream;

public interface SerialPortIo {
    void open() throws Exception;
    void close();
    void setParameters(int baud, int dataBits, int stopBits, int parity) throws Exception;
    void setDTR(boolean v) throws Exception;
    void setRTS(boolean v) throws Exception;
    void setTimeouts(int readMillis, int writeMillis);
    InputStream getInputStream();
    OutputStream getOutputStream();
    String getPortId();
}
