package com.omnixone.modbuslibrary.modbuscontroller;


import com.fazecast.jSerialComm.SerialPort;
import com.omnixone.modbuslibrary.ModbusException;
import com.omnixone.modbuslibrary.slave.ModbusSlave;
import com.omnixone.modbuslibrary.slave.ModbusSlaveFactory;
import com.omnixone.modbuslibrary.util.SerialParameters;

public class ModbusSerialSlaveManager {

    private ModbusSlave serialSlave;

    public void startSerialSlave(String portName) {
        try {
            SerialParameters params = new SerialParameters();
            params.setPortName(portName);
            params.setBaudRate(9600);         // Adjust as needed
            params.setDatabits(8);
            params.setStopbits(1);
            params.setParity("None");
            params.setEncoding("rtu");        // or "ascii"

            serialSlave = ModbusSlaveFactory.createSerialSlave(params);
            serialSlave.open();
        } catch (ModbusException e) {
            e.printStackTrace();
        }
    }

    public void stopSerialSlave() {
        if (serialSlave != null) {
            serialSlave.close();
        }
    }
}
