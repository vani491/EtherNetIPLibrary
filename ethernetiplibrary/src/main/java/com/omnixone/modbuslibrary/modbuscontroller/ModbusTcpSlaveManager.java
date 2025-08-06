package com.omnixone.modbuslibrary.modbuscontroller;

import com.omnixone.modbuslibrary.ModbusException;
import com.omnixone.modbuslibrary.slave.ModbusSlave;
import com.omnixone.modbuslibrary.slave.ModbusSlaveFactory;

public class ModbusTcpSlaveManager {

    private ModbusSlave tcpSlave;

    public void startTcpSlave(int port, int poolSize) {
        try {
            tcpSlave = ModbusSlaveFactory.createTCPSlave(port, poolSize, false);
            tcpSlave.open();  // This starts the listener
        } catch (ModbusException e) {
            e.printStackTrace();
        }
    }

    public void stopTcpSlave() {
        if (tcpSlave != null) {
            tcpSlave.close();
        }
    }
}
