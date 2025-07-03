#include <jni.h>
#include <string>           // ✅ C++ header — stays outside
#include <android/log.h>    // ✅ C++ header — stays outside
#include <unistd.h>         // ✅ POSIX header — stays outside
#include <thread>
// ✅ Only C headers go inside this block
extern "C" {
#include "opener_api.h"
#include "doublylinkedlist.h"
#include "cipconnectionobject.h"
#include "cipethernetlink.h"
#include "ciptcpipinterface.h"
#include "trace.h"
#include "nvdata.h"
#include "generic_networkhandler.h"

}
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define BringupNetwork(if_name, method, if_cfg, hostname)  ((EipStatus)kEipStatusOk)
#define ShutdownNetwork(if_name)  ((EipStatus)kEipStatusOk)
extern volatile int g_end_stack;


#define LOG_TAG "EtherNetIP-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


extern "C"
JNIEXPORT jstring JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_getVersionFromJNI(
        JNIEnv *env,
        jobject ) {
    //LOGI("EtherNet/IP Library - Native code initialized");
    std::string version = "EtherNet/IP Library v1.0.0 - Native Ready";
    return env->NewStringUTF(version.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_startOpENerStack(
        JNIEnv *env,
        jobject /* this */,
        jstring interfaceNameJ) {

    const char *interfaceName = env->GetStringUTFChars(interfaceNameJ, 0);
    uint8_t iface_mac[6];
    EipStatus eip_status;
    std::string logStr;
    auto log = [&](const std::string &msg) {
        logStr += msg + "\n";
        __android_log_print(ANDROID_LOG_INFO, "EtherNetIP", "%s", msg.c_str());
    };
    log("OpENer: Starting initialization...");


    // Step 1: Initialize connection list
    DoublyLinkedListInitialize(&connection_list,
                               CipConnectionObjectListArrayAllocator,
                               CipConnectionObjectListArrayFree);

    // Step 2: Get MAC address
    if (kEipStatusError == IfaceGetMacAddress(interfaceName, iface_mac)) {
        log("Error: Network interface " + std::string(interfaceName) + " not found!");
        env->ReleaseStringUTFChars(interfaceNameJ, interfaceName);
        return env->NewStringUTF(logStr.c_str());
    }

    // Step 3: Set static device serial number
    SetDeviceSerialNumber(123456789);

    // Step 4: Generate a random connection ID
    srand(time(NULL));
    EipUint16 unique_connection_id = (EipUint16) rand();

    // Step 5: Initialize CIP stack
    eip_status = CipStackInit(unique_connection_id);
    if (eip_status != kEipStatusOk) {
        log("Error: CIP stack initialization failed!");
        env->ReleaseStringUTFChars(interfaceNameJ, interfaceName);
        return env->NewStringUTF(logStr.c_str());
    }

    // Step 6: Assign MAC to Ethernet Link Object
    CipEthernetLinkSetMac(iface_mac);

    // Step 7: Load stored configuration (NVData)
    if (kEipStatusError == NvdataLoad()) {
        log("Loading of some NV data failed. Maybe the first start?");
    }

    // Step 8: Bring up network interface
    eip_status = BringupNetwork(interfaceName,
                                g_tcpip.config_control,
                                &g_tcpip.interface_configuration,
                                &g_tcpip.hostname);
    if (eip_status < 0) {
        log("Error: BringUpNetwork() failed");
        env->ReleaseStringUTFChars(interfaceNameJ, interfaceName);
        return env->NewStringUTF(logStr.c_str());
    }

    // Step 9: Check if DHCP is used
    CipDword network_config_method = g_tcpip.config_control & kTcpipCfgCtrlMethodMask;

    if (kTcpipCfgCtrlStaticIp == network_config_method) {
        log("Info: Static IP configuration done");
    } else if (kTcpipCfgCtrlDhcp == network_config_method) {
        log("Info: DHCP network configuration started");

        // Wait until IP is assigned or stack is stopped
        eip_status = IfaceWaitForIp(interfaceName, -1, &g_end_stack);
        if (kEipStatusOk == eip_status && 0 == g_end_stack) {
            eip_status = IfaceGetConfiguration(interfaceName, &g_tcpip.interface_configuration);
            if (eip_status < 0) {
                log("Warning: Problems getting interface configuration");
            } else {
                log("Info: DHCP configuration retrieved");
            }
        }else {
            log("Error: DHCP interface wait failed or aborted");
        }
    }

    // Step 10: Initialize the network handler
    if (!g_end_stack && kEipStatusOk == NetworkHandlerInitialize()) {
        log("Info:  Starting OpENer event loop in background thread");
        std::string ifaceName(interfaceName);
        std::thread([] {
            while (!g_end_stack) {
                if (kEipStatusOk != NetworkHandlerProcessCyclic()) {
                    LOGE("Error in NetworkHandler loop! Exiting OpENer.");
                    break;
                }
                usleep(1000); // Sleep for 1ms to avoid CPU overuse
            }

            // Clean up the network handler
            NetworkHandlerFinish();
            ShutdownCipStack(); // Step 11: Clean CIP
            ShutdownNetwork(ifaceName.c_str());
        }).detach();
        log("Info: Background thread started successfully");
    }else {
        log("Error: Failed to initialize NetworkHandler or stack was stopped");
    }
    env->ReleaseStringUTFChars(interfaceNameJ, interfaceName);
    return env->NewStringUTF(logStr.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_stopOpENerStack(
        JNIEnv *env,
        jobject /* this */) {
    g_end_stack = SIGINT;
    LOGI("Signal sent to stop OpENer stack.");
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_isOpENerRunning(JNIEnv *env, jobject) {
    return g_end_stack == 0 ? JNI_TRUE : JNI_FALSE;
}



