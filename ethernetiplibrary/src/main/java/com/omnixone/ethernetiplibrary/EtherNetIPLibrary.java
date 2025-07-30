package com.omnixone.ethernetiplibrary;


public class EtherNetIPLibrary {

    private static CppDataListener dataListener;
    // Load the native library
    static {
        System.loadLibrary("opener-lib");
        Class<?> ensureLoaded = com.omnixone.ethernetiplibrary.EtherNetIPLibrary.class;
    }


    public native String getVersionFromJNI();
    public native String startOpENerStack(String interfaceName); // ✅ Add this
    public native void stopOpENerStack();
    public String getVersion() {
        return getVersionFromJNI();
    }

    public native OpenerIdentity getIdentity();
    public static native void setInputValues(byte[] values);


    public String startStack(String interfaceName) {
       String outputString = startOpENerStack(interfaceName);
       return outputString;
    }
    public void stopStack() {
        stopOpENerStack();
    }


    public static void onDataFromCpp(byte[] data) {
//        System.out.println("Data received from C++:");
//        for (byte b : data) {
//            System.out.printf("%02X ", b & 0xFF);
//        }
//        System.out.println();

        if (dataListener != null) {
            dataListener.onCppDataReceived(data); // Notify MainActivity
        }
    }

    public static void setCppDataListener(CppDataListener listener) {
        dataListener = listener;
    }

}