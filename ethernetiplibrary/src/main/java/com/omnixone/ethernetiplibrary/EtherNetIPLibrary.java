package com.omnixone.ethernetiplibrary;

/**
 * EtherNet/IP Library for Android
 * Provides CIP (Common Industrial Protocol) communication capabilities
 */
public class EtherNetIPLibrary {

    // Load the native library
    static {
        System.loadLibrary("opener-lib");
    }


    public native String getVersionFromJNI();
    public native String startOpENerStack(String interfaceName); // ✅ Add this
    public native void stopOpENerStack();
    public String getVersion() {
        return getVersionFromJNI();
    }

    public native OpenerIdentity getIdentity();
    public static native void setInputValue(int index, byte value);


    public String startStack(String interfaceName) {
       String outputString = startOpENerStack(interfaceName);
       return outputString;
    }
    public void stopStack() {
        stopOpENerStack();
    }
}