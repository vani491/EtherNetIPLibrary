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

    public static native void setAssemblyData(int  _input_assembly_num,
                                              int _output_assembly_num,
                                              int _config_assembly_num,
                                              int _input_size,
                                              int _output_size,
                                              int _config_size );




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


    public static void setEthernetAssemblyData(int  input_assembly_num,
                                       int output_assembly_num,
                                       int config_assembly_num,
                                       int input_size,
                                       int output_size,
                                       int config_size){

        setAssemblyData(input_assembly_num,
                output_assembly_num,
                config_assembly_num,
                input_size,
                output_size,
                config_size);

    }

}