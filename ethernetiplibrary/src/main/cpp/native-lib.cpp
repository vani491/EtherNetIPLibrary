#include <jni.h>
#include <string>           // ✅ C++ header — stays outside
#include <android/log.h>    // ✅ C++ header — stays outside
#include <unistd.h>         // ✅ POSIX header — stays outside
#include <thread>
#include "cipidentity.h"
#include <cstring>
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
//extern volatile int g_end_stack;
static volatile int g_end_stack1 = false;



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
/*

    // Step 9: Check if DHCP is used
    CipDword network_config_method = g_tcpip.config_control & kTcpipCfgCtrlMethodMask;

    if (kTcpipCfgCtrlStaticIp == network_config_method) {
        log("Info: Static IP configuration done");
    } else if (kTcpipCfgCtrlDhcp == network_config_method) {
        log("Info: DHCP network configuration started");

        // Wait until IP is assigned or stack is stopped
        eip_status = IfaceWaitForIp(interfaceName, -1, &g_end_stack1);
        if (kEipStatusOk == eip_status && 0 == g_end_stack1) {
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
*/

    // Step 10: Initialize the network handler
    if (!g_end_stack1 && kEipStatusOk == NetworkHandlerInitialize()) {
        log("Info:  Starting OpENer event loop in background thread");
        std::string ifaceName(interfaceName);
        std::thread([] {
            while (!g_end_stack1) {
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
//    g_end_stack1 = SIGINT;
    g_end_stack1 = true;
    LOGI("Signal sent to stop OpENer stack.");
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_isOpENerRunning(JNIEnv *env, jobject) {
    return g_end_stack1 == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_getIdentity(JNIEnv* env, jobject /* thisObj */) {
    // 1. Find Java class OpenerIdentity
    jclass identityClass = env->FindClass("com/omnixone/ethernetiplibrary/OpenerIdentity");
    if (!identityClass) return nullptr;

    // 2. Call default constructor
    jmethodID constructor = env->GetMethodID(identityClass, "<init>", "()V");
    if (!constructor) return nullptr;

    jobject identityObj = env->NewObject(identityClass, constructor);
    if (!identityObj) return nullptr;

    // 3. Set each field
    env->SetIntField(identityObj, env->GetFieldID(identityClass, "vendorId", "I"), g_identity.vendor_id);
    env->SetIntField(identityObj, env->GetFieldID(identityClass, "deviceType", "I"), g_identity.device_type);
    env->SetIntField(identityObj, env->GetFieldID(identityClass, "productCode", "I"), g_identity.product_code);
    env->SetIntField(identityObj, env->GetFieldID(identityClass, "majorRevision", "I"), g_identity.revision.major_revision);
    env->SetIntField(identityObj, env->GetFieldID(identityClass, "minorRevision", "I"), g_identity.revision.minor_revision);
    env->SetIntField(identityObj,env->GetFieldID(identityClass, "serialNumber", "I"),static_cast<jint>(g_identity.serial_number));

    // 4. Set product name
    char name_buffer[256];
    size_t len = g_identity.product_name.length;
    if (len >= sizeof(name_buffer)) {
        len = sizeof(name_buffer) - 1;
    }
    std::memcpy(name_buffer, g_identity.product_name.string, len);
    name_buffer[len] = '\0';

    jstring jProductName = env->NewStringUTF(name_buffer);
    env->SetObjectField(identityObj, env->GetFieldID(identityClass, "productName", "Ljava/lang/String;"), jProductName);

    return identityObj;
}



extern EipUint8 g_assembly_data064[32];

extern "C" JNIEXPORT void JNICALL
Java_com_omnixone_ethernetiplibrary_EtherNetIPLibrary_setInputValue(JNIEnv *env, jobject thiz, jint index, jbyte value) {
    if (index >= 0 && index < 32) {
        g_assembly_data064[index] = (EipUint8)value;
    }
}





