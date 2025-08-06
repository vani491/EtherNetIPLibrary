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
package com.omnixone.modbuslibrary.procimg;

import com.omnixone.modbuslibrary.util.Observable;

/**
 * Class implementing an observable register.
 *
 * @author Dieter Wimberger
 * @author Steve O'Hara (4NG)
 * @version 2.0 (March 2016)
 */
public class ObservableRegister extends Observable implements Register {

    private static final String VALUE = "value";

    /**
     * The word holding the content of this register.
     */
    protected short register;

    @Override
    public synchronized int getValue() {
        return register & 0xFFFF;
    }

    @Override
    public int toUnsignedShort() {
        return register & 0xFFFF;
    }

    @Override
    public short toShort() {
        return register;
    }

    @Override
    public synchronized byte[] toBytes() {
        return new byte[]{(byte)(register >> 8), (byte)(register & 0xFF)};
    }

    @Override
    public synchronized void setValue(short s) {
        register = s;
        notifyObservers(VALUE);
    }

    @Override
    public synchronized void setValue(byte[] bytes) {
        if (bytes.length < 2) {
            throw new IllegalArgumentException();
        }
        else {
            register = (short)(((short)(bytes[0] << 8)) | (((short)(bytes[1])) & 0xFF));
            notifyObservers(VALUE);
        }
    }

    @Override
    public synchronized void setValue(int v) {
        register = (short)v;
        notifyObservers(VALUE);
    }
}