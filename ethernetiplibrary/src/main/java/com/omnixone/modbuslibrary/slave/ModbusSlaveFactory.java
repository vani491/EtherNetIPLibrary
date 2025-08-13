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
package com.omnixone.modbuslibrary.slave;

import com.omnixone.modbuslibrary.ModbusException;
import com.omnixone.modbuslibrary.net.AbstractModbusListener;
import com.omnixone.modbuslibrary.util.ModbusUtil;
import com.omnixone.modbuslibrary.util.SerialParameters;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.omnixone.modbuslibrary.net.AbstractSerialConnection;
import com.omnixone.modbuslibrary.net.ModbusSerialListener;
import java.lang.reflect.Field;

/**
 * This is a factory class that allows users to easily create and manage slaves.<br>
 * Each slave is uniquely identified by the port it is listening on, irrespective of if
 * the socket type (TCP, UDP or Serial)
 *
 * @author Steve O'Hara (4NG)
 * @version 2.0 (March 2016)
 */
public class ModbusSlaveFactory {

    private static final Map<String, ModbusSlave> slaves = new HashMap<>();

    /**
     * Prevent instantiation
     */
    private ModbusSlaveFactory() {}


    // File: com/omnixone/modbuslibrary/slave/ModbusSlaveFactory.java
// Add imports:


    public static synchronized ModbusSlave createAndroidSerialSlave(AbstractSerialConnection serialCon) throws ModbusException {
        if (serialCon == null) throw new ModbusException("Serial connection is null");

        String key = ModbusSlaveType.SERIAL.getKey(serialCon.getPortName());
        ModbusSlave existing = slaves.get(key);
        if (existing != null) return existing;

        // Create a normal serial slave (uses protected ctor from same package)
        // It will build a listener internally that we will replace.
        ModbusSlave slave = new ModbusSlave(new com.omnixone.modbuslibrary.util.SerialParameters()) { };

        // Replace its listener with one bound to *your* open connection
        ModbusSerialListener custom = new ModbusSerialListener(serialCon);
        try {
            Field f = ModbusSlave.class.getDeclaredField("listener");
            f.setAccessible(true);
            f.set(slave, custom);
        } catch (Exception e) {
            throw new ModbusException("Failed to inject serial listener: " + e.getMessage());
        }

        // Register in the map using your connection's port name
        slaves.put(key, slave);
        return slave;
    }



    // NEW: Create a SERIAL slave from an already-openable Android connection
/*    public static synchronized ModbusSlave createAndroidSerialSlave(
            com.omnixone.modbuslibrary.net.AbstractSerialConnection connection)
            throws com.omnixone.modbuslibrary.ModbusException {

        if (connection == null) {
            throw new com.omnixone.modbuslibrary.ModbusException("Serial connection is null");
        }

        // Use the connection's port id as the key
        String key = ModbusSlaveType.SERIAL.getKey(connection.getPortName());

        // If one exists for this "port", close & replace (params may differ)
        ModbusSlave existing = slaves.get(key);
        if (existing != null) {
            close(existing);
        }

        ModbusSlave slave = new ModbusSlave(connection);
        slaves.put(key, slave);
        return slave;
    }*/


    /**
     * Creates a TCP modbus slave or returns the one already allocated to this port
     *
     * @param port     Port to listen on
     * @param poolSize Pool size of listener threads
     * @return new or existing TCP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createTCPSlave(int port, int poolSize) throws ModbusException {
        return createTCPSlave(port, poolSize, false);
    }

    /**
     * Creates a TCP modbus slave or returns the one already allocated to this port
     *
     * @param port          Port to listen on
     * @param poolSize      Pool size of listener threads
     * @param useRtuOverTcp True if the RTU protocol should be used over TCP
     * @return new or existing TCP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createTCPSlave(int port, int poolSize, boolean useRtuOverTcp) throws ModbusException {
        return ModbusSlaveFactory.createTCPSlave(null, port, poolSize, useRtuOverTcp);
    }

    /**
     * Creates a TCP modbus slave or returns the one already allocated to this port
     *
     * @param address       IP address to listen on
     * @param port          Port to listen on
     * @param poolSize      Pool size of listener threads
     * @param useRtuOverTcp True if the RTU protocol should be used over TCP
     * @return new or existing TCP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createTCPSlave(InetAddress address, int port, int poolSize, boolean useRtuOverTcp) throws ModbusException {
        return ModbusSlaveFactory.createTCPSlave(address, port, poolSize, useRtuOverTcp, 0);
    }

    /**
     * Creates a TCP modbus slave or returns the one already allocated to this port
     *
     * @param address        IP address to listen on
     * @param port           Port to listen on
     * @param poolSize       Pool size of listener threads
     * @param useRtuOverTcp  True if the RTU protocol should be used over TCP
     * @param maxIdleSeconds Maximum idle seconds for TCP connection
     * @return new or existing TCP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createTCPSlave(InetAddress address, int port, int poolSize, boolean useRtuOverTcp, int maxIdleSeconds) throws ModbusException {
        String key = ModbusSlaveType.TCP.getKey(port);
        if (slaves.containsKey(key)) {
            return slaves.get(key);
        }
        else {
            ModbusSlave slave = new ModbusSlave(address, port, poolSize, useRtuOverTcp, maxIdleSeconds);
            slaves.put(key, slave);
            return slave;
        }
    }

    /**
     * Creates a UDP modbus slave or returns the one already allocated to this port
     *
     * @param port Port to listen on
     * @return new or existing UDP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createUDPSlave(int port) throws ModbusException {
        return createUDPSlave(null, port);
    }

    /**
     * Creates a UDP modbus slave or returns the one already allocated to this port
     *
     * @param address IP address to listen on
     * @param port    Port to listen on
     * @return new or existing UDP modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createUDPSlave(InetAddress address, int port) throws ModbusException {
        String key = ModbusSlaveType.UDP.getKey(port);
        if (slaves.containsKey(key)) {
            return slaves.get(key);
        }
        else {
            ModbusSlave slave = new ModbusSlave(address, port, false);
            slaves.put(key, slave);
            return slave;
        }
    }

    /**
     * Creates a serial modbus slave or returns the one already allocated to this port
     *
     * @param serialParams Serial parameters for serial type slaves
     * @return new or existing Serial modbus slave associated with the port
     * @throws ModbusException If a problem occurs e.g. port already in use
     */
    public static synchronized ModbusSlave createSerialSlave(SerialParameters serialParams) throws ModbusException {
        ModbusSlave slave;
        if (serialParams == null) {
            throw new ModbusException("Serial parameters are null");
        }
        else if (ModbusUtil.isBlank(serialParams.getPortName())) {
            throw new ModbusException("Serial port name is empty");
        }

        // If we have a slave already assigned to this port
        slave = getSlave(ModbusSlaveType.SERIAL, serialParams.getPortName());
        if (slave != null) {
            // Check if any of the parameters have changed
            if (!serialParams.toString().equals(slave.getSerialParams().toString())) {
                close(slave);
                slave = null;
            }
        }

        // If we don;t have a slave, create one
        if (slave == null) {
            slave = new ModbusSlave(serialParams);
            slaves.put(ModbusSlaveType.SERIAL.getKey(serialParams.getPortName()), slave);
        }
        return slave;
    }

    /**
     * Closes this slave and removes it from the running list
     *
     * @param slave Slave to remove
     */
/*    public static synchronized void close(ModbusSlave slave) {
        if (slave != null) {
            slave.closeListener();
            if (slave.getType().is(ModbusSlaveType.SERIAL)) {
                slaves.remove(slave.getType().getKey(slave.getSerialParams().getPortName()));
            } else {
                slaves.remove(slave.getType().getKey(slave.getPort()));
            }
        }
    }*/


    public static synchronized void close(ModbusSlave slave) {
        if (slave == null) return;

        slave.closeListener();

        try {
            if (slave.getType().is(ModbusSlaveType.SERIAL)) {
                String key = null;

                // Preferred: use serialParams if present
                if (slave.getSerialParams() != null
                        && !ModbusUtil.isBlank(slave.getSerialParams().getPortName())) {
                    key = slave.getType().getKey(slave.getSerialParams().getPortName());
                } else {
                    // Fallback: find and remove by instance
                    for (Map.Entry<String, ModbusSlave> e : new ArrayList<>(slaves.entrySet())) {
                        if (e.getValue() == slave) { key = e.getKey(); break; }
                    }
                }

                if (key != null) {
                    slaves.remove(key);
                }
                // else: nothing to remove, but we did stop the listener above
            } else {
                slaves.remove(slave.getType().getKey(slave.getPort()));
            }
        } catch (Exception ignored) {
            // swallow any cleanup issues; we've already stopped the listener
        }
    }


    /**
     * Closes all slaves and removes them from the running list
     */
    public static synchronized void close() {
        for (ModbusSlave slave : new ArrayList<>(slaves.values())) {
            slave.close();
        }
    }

    /**
     * Returns the running slave listening on the given port
     *
     * @param port Port to check for running slave
     * @return Null or ModbusSlave
     */
    public static synchronized ModbusSlave getSlave(ModbusSlaveType type, int port) {
        return type == null ? null : slaves.get(type.getKey(port));
    }

    /**
     * Returns the running slave listening on the given port
     *
     * @param port Port to check for running slave
     * @return Null or ModbusSlave
     */
    public static synchronized ModbusSlave getSlave(ModbusSlaveType type, String port) {
        return type == null || ModbusUtil.isBlank(port) ? null : slaves.get(type.getKey(port));
    }

    /**
     * Returns the running slave that utilises the give listener
     *
     * @param listener Listener used for this slave
     * @return Null or ModbusSlave
     */
    public static synchronized ModbusSlave getSlave(AbstractModbusListener listener) {
        for (ModbusSlave slave : slaves.values()) {
            if (slave.getListener().equals(listener)) {
                return slave;
            }
        }
        return null;
    }
}
