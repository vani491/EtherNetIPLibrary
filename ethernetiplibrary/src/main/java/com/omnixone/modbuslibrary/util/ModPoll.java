/*
 *
 * Copyright (c) 2020, 4NG and/or its affiliates. All rights reserved.
 * 4NG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
package com.omnixone.modbuslibrary.util;

import com.omnixone.modbuslibrary.Modbus;
import com.omnixone.modbuslibrary.ModbusException;
import com.omnixone.modbuslibrary.facade.AbstractModbusMaster;
import com.omnixone.modbuslibrary.facade.ModbusSerialMaster;
import com.omnixone.modbuslibrary.facade.ModbusTCPMaster;
import com.omnixone.modbuslibrary.facade.ModbusUDPMaster;
import com.omnixone.modbuslibrary.net.AbstractSerialConnection;
import com.omnixone.modbuslibrary.procimg.InputRegister;
import com.omnixone.modbuslibrary.procimg.Register;

import java.util.HashSet;
import java.util.Set;

/**
 * This class mimics ModPoll for serial connections
 * see https://www.modbusdriver.com/modpoll.html
 */
public class ModPoll {

    /**
     * Main entry point for the ModPoll applicaion
     *
     * @param args Arguments
     */
    public static void main(String[] args) {

        try {
            // Get all the command line parameters
            CommandLineParams params = new CommandLineParams(args);

            // Select the master to use
            if (CommandLineParams.MODE_ASCII.equalsIgnoreCase(params.mode) || CommandLineParams.MODE_RTU.equalsIgnoreCase(params.mode)) {
                mainSerial(params);
            }
            else if (CommandLineParams.MODE_TCP.equalsIgnoreCase(params.mode) || CommandLineParams.MODE_ENC.equalsIgnoreCase(params.mode)) {
                mainTCP(params);
            }
            else if (CommandLineParams.MODE_UDP.equalsIgnoreCase(params.mode)) {
                mainUDP(params);
            }
        }
        catch (ModbusException e) {
            System.out.println(e.getMessage());
            showHelp();
        }
    }

    /**
     * Executes RTU serial transactions
     *
     * @param params Arguments
     */
    private static void mainSerial(CommandLineParams params) {

        ModbusSerialMaster master;
        SerialParameters parameters = new SerialParameters();
        parameters.setPortName(params.portname);
        parameters.setOpenDelay(1000);
        parameters.setEncoding(CommandLineParams.MODE_ASCII.equalsIgnoreCase(params.mode) ? Modbus.SERIAL_ENCODING_ASCII : Modbus.SERIAL_ENCODING_RTU);
        parameters.setStopbits(params.stopBits);
        parameters.setParity(params.parity);
        parameters.setBaudRate(params.baudRate);
        parameters.setDatabits(params.dataBits);
        parameters.setFlowControlOut(AbstractSerialConnection.FLOW_CONTROL_DISABLED);
        parameters.setFlowControlIn(AbstractSerialConnection.FLOW_CONTROL_DISABLED);
        parameters.setEcho(false);
        parameters.setOpenDelay(0);
        master = new ModbusSerialMaster(parameters);

        execute(params, master);
    }

    /**
     * Executes TCP transactions
     *
     * @param params Arguments
     */
    private static void mainTCP(CommandLineParams params) {

        ModbusTCPMaster master = new ModbusTCPMaster(params.portname, params.port, CommandLineParams.MODE_ENC.equalsIgnoreCase(params.mode));
        execute(params, master);
    }

    /**
     * Executes UDP transactions
     *
     * @param params Arguments
     */
    private static void mainUDP(CommandLineParams params) throws ModbusException {

        // Check for some mode specific issues
        if (params.portname == null) {
            throw new ModbusException("ERROR - you must specify a host name or IP address");
        }
        ModbusUDPMaster master = new ModbusUDPMaster(params.portname, params.port);
        execute(params, master);
    }

    /**
     * Executes the modbus transaction using the specified master
     *
     * @param params The command line parameters
     * @param master Master conection to use
     */
    private static void execute(CommandLineParams params, AbstractModbusMaster master) {
        boolean first = true;
        while (params.continuous || first) {
            long startTime = System.currentTimeMillis();
            first = false;
            try {
                master.connect();

                System.out.println("-- Polling slave... (Ctrl-C to stop)");
                if (params.type == 1) {
                    BitVector vector = master.readInputDiscretes(params.unit, params.reference, params.count);
                    System.out.println(vector.toString());
                }
                else if (params.type == 3) {
                    InputRegister[] regs = master.readInputRegisters(params.unit, params.reference, params.count);
                    int cnt = 0;
                    for (InputRegister reg : regs) {
                        System.out.printf("[%04d]: %s (%d)%n", params.reference + cnt, ModbusUtil.toHex(reg.toBytes()), reg.toUnsignedShort());
                        cnt++;
                    }
                }
                else if (params.type == 4) {
                    Register[] regs = master.readMultipleRegisters(params.unit, params.reference, params.count);
                    int cnt = 0;
                    for (Register reg : regs) {
                        System.out.printf("[%04d]: %s (%d)%n", params.reference + cnt, ModbusUtil.toHex(reg.toBytes()), reg.toUnsignedShort());
                        cnt++;
                    }
                }

            }
            catch (Exception e) {
                System.out.printf("ERROR - %s%n", e.getMessage());
            }
            finally {
                master.disconnect();
            }
            if (params.continuous) {
                long sleepTime = params.rate - (System.currentTimeMillis() - startTime);
                if (sleepTime > 0) {
                    ModbusUtil.sleep(sleepTime);
                }
            }
        }
    }

    /**
     * Shows the available options
     */
    private static void showHelp() {
        System.out.println("Usage: modpoll [OPTIONS] SERIALPORT|HOST");
        System.out.println("Arguments:");
        System.out.println("SERIALPORT    Serial port when using Modbus ASCII or Modbus RTU protocol");
        System.out.println("              COM1, COM2 ...                on Windows");
        System.out.println("              /dev/ttyS0, /dev/ttyS1 ...    on Linux");
        System.out.println("HOST          Host name or dotted IP address when using MODBUS/TCP protocol");
        System.out.println();
        System.out.println("General options:");
        System.out.println("-m ascii      Modbus ASCII protocol");
        System.out.println("-m rtu        Modbus RTU protocol (default if SERIALPORT contains /, \\ or COM)");
        System.out.println("-m tcp        MODBUS/TCP protocol (default otherwise)");
        System.out.println("-m udp        MODBUS UDP");
        System.out.println("-m enc        Encapsulated Modbus RTU over TCP");
        System.out.println("-a #          Slave address (1-255 for serial, 0-255 for TCP, 1 is default)\n");
        System.out.println("-r #          Start reference (1-65536, 1 is default)");
        System.out.println("-c #          Number of values to poll (1-125, 1 is default)");
        System.out.println("-t 1          Discrete input data type");
        System.out.println("-t 3          16-bit input register data type");
        System.out.println("-t 3:hex      16-bit input register data type with hex display");
        System.out.println("-t 3:int      32-bit integer data type in input register table");
        System.out.println("-t 3:mod      32-bit module 10000 data type in input register table");
        System.out.println("-t 3:float    32-bit float data type in input register table");
        System.out.println("-t 4          16-bit output (holding) register data type (default)");
        System.out.println("-t 4:hex      16-bit output (holding) register data type with hex display");
        System.out.println("-t 4:int      32-bit integer data type in output (holding) register table");
        System.out.println("-t 4:mod      32-bit module 10000 type in output (holding) register table");
        System.out.println("-t 4:float    32-bit float data type in output (holding) register table");
        System.out.println("-i            Slave operates on big-endian 32-bit integers");
        System.out.println("-f            Slave operates on big-endian 32-bit floats");
        System.out.println("-1            Poll only once only, otherwise every poll rate interval");
        System.out.println("-l            Poll rate in ms, (1000 is default)");
        System.out.println("-o #          Time-out in seconds (0.01 - 10.0, 1.0 s is default)");
        System.out.println();
        System.out.println("Options for MODBUS/TCP, UDP and RTU over TCP:");
        System.out.println("-p #          IP protocol port number (502 is default)");
        System.out.println();
        System.out.println("Options for Modbus ASCII and Modbus RTU:");
        System.out.println("-b #          Baudrate (e.g. 9600, 19200, ...) (19200 is default)");
        System.out.println("-d #          Databits (7 or 8 for ASCII protocol, 8 for RTU)");
        System.out.println("-s #          Stopbits (1 or 2, 1 is default)");
        System.out.println("-p none       No parity");
        System.out.println("-p even       Even parity (default)");
        System.out.println("-p odd        Odd parity");
    }

    /**
     * Convenient holder for all comman line arguments
     */
    private static class CommandLineParams {

        static final String MODE_ASCII = "ascii";
        static final String MODE_RTU = "rtu";
        static final String MODE_TCP = "tcp";
        static final String MODE_UDP = "udp";
        static final String MODE_ENC = "enc";
        private static final Set<String> ACCEPTABLE_MODES;
        private static final String ACCEPTABLE_MODES_DISPLAY;

        static {
            ACCEPTABLE_MODES = new HashSet<String>();
            ACCEPTABLE_MODES.add(MODE_ASCII);
            ACCEPTABLE_MODES.add(MODE_RTU);
            ACCEPTABLE_MODES.add(MODE_TCP);
            ACCEPTABLE_MODES.add(MODE_UDP);
            ACCEPTABLE_MODES.add(MODE_ENC);
            ACCEPTABLE_MODES_DISPLAY = String.format("%s,%s,%s,%s,%s", MODE_ASCII, MODE_RTU, MODE_TCP, MODE_UDP, MODE_ENC);
        }

        String portname = null;
        int parity = AbstractSerialConnection.NO_PARITY;
        int rate = 1000, unit = -1, reference = 0, count = 1, type = 3, baudRate = 9600, dataBits = 8, stopBits = 1, port = 502;
        boolean continuous = true;
        String mode = MODE_TCP, displayType = null;
        private String portOrParity;

        /**
         * Initialises the command line parameters and checks for common issues
         *
         * @param args Array of args from the command line
         * @throws ModbusException If an argument is out of range
         */
        public CommandLineParams(String[] args) throws ModbusException {
            for (int arg = 0; arg < args.length; arg++) {
                if (args[arg].equals("-m")) {
                    mode = args[++arg].toLowerCase();
                }
                else if (args[arg].equals("-a")) {
                    unit = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-r")) {
                    reference = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-c")) {
                    count = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-t")) {
                    String tmp = args[++arg];
                    if (tmp.contains(":")) {
                        type = Integer.parseInt(tmp.split(" *: *")[0]);
                        tmp = tmp.split(" *: *")[1];
                        if (!tmp.isEmpty()) {
                            displayType = tmp;
                        }
                    }
                    else {
                        type = Integer.parseInt(tmp);
                    }
                }
                else if (args[arg].equals("-1")) {
                    continuous = false;
                }
                else if (args[arg].equals("-l")) {
                    rate = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-b")) {
                    baudRate = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-d")) {
                    dataBits = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-s")) {
                    stopBits = Integer.parseInt(args[++arg]);
                }
                else if (args[arg].equals("-p")) {
                    portOrParity = args[++arg];
                }
                else {
                    portname = args[arg];
                }
            }

            // Work out if this is serial
            boolean isSerialMode = MODE_ASCII.equalsIgnoreCase(mode) || MODE_RTU.equalsIgnoreCase(mode);
            if (isSerialMode) {
                if ("none".equalsIgnoreCase(portOrParity)) {
                    parity = AbstractSerialConnection.NO_PARITY;
                }
                else if ("even".equalsIgnoreCase(portOrParity)) {
                    parity = AbstractSerialConnection.EVEN_PARITY;
                }
                else if ("odd".equalsIgnoreCase(portOrParity)) {
                    parity = AbstractSerialConnection.ODD_PARITY;
                }
            }
            else if (portOrParity != null) {
                port = Integer.parseInt(portOrParity);
            }

            // Check for some common stuff
            if (portname == null) {
                if (isSerialMode) {
                    throw new ModbusException("ERROR - you must specify a port name");
                }
                throw new ModbusException("ERROR - you must specify a host name or IP address");
            }
            if (unit < 0 || unit > 255) {
                throw new ModbusException("ERROR - you must specify a valid unit ID (1-255) e.g. -a 49");
            }
            else if (mode == null || !ACCEPTABLE_MODES.contains(mode)) {
                throw new ModbusException("ERROR - mode must be one of [%s]", ACCEPTABLE_MODES_DISPLAY);
            }
        }
    }
}
