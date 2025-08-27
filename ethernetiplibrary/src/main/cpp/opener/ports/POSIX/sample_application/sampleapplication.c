/*******************************************************************************
 * Copyright (c) 2012, Rockwell Automation, Inc.
 * All rights reserved.
 *
 ******************************************************************************/

#include <string.h>
#include <stdlib.h>
#include <stdbool.h>

#include "opener_api.h"
#include "appcontype.h"
#include "trace.h"
#include "cipidentity.h"
#include "ciptcpipinterface.h"
#include "cipqos.h"
#include "nvdata.h"

#include "jni_bridge.h"
#include <android/log.h>    // ✅ C++ header — stays outside


#if defined(OPENER_ETHLINK_CNTRS_ENABLE) && 0 != OPENER_ETHLINK_CNTRS_ENABLE
  #include "cipethernetlink.h"
  #include "ethlinkcbs.h"
#endif

#define DEMO_APP_INPUT_ASSEMBLY_NUM                102 //0x064
#define DEMO_APP_OUTPUT_ASSEMBLY_NUM               101 //0x096
#define DEMO_APP_CONFIG_ASSEMBLY_NUM               103 //0x097
#define DEMO_APP_HEARTBEAT_INPUT_ONLY_ASSEMBLY_NUM  152 //0x098
#define DEMO_APP_HEARTBEAT_LISTEN_ONLY_ASSEMBLY_NUM 153 //0x099
#define DEMO_APP_EXPLICT_ASSEMBLY_NUM              154 //0x09A

/* global variables for demo application (4 assembly data fields)  ************/
#define  ASSEMBLY_SIZE 128
EipUint8 g_assembly_data064[ASSEMBLY_SIZE]; /* Input */
EipUint8 g_assembly_data096[ASSEMBLY_SIZE+4]; /* Output */
EipUint8 g_assembly_data097[0]; /* Config */
EipUint8 g_assembly_data09A[32]; /* Explicit */

/* local functions */

// sampleapplication_dynamic.c (or wherever you keep app utilities)
#include "opener_api.h"
#include <stdlib.h>
#include <string.h>


typedef struct {
    CipInstanceNum inst;
    EipByte *buf;
} DynAsm;

static DynAsm g_dyn_asms[16];  // track a few for cleanup; size as you like
static size_t g_dyn_count = 0;

CipInstance *CreateDynamicAssembly(CipInstanceNum inst, size_t size_bytes) {
    // 1) allocate the payload buffer you own
    EipByte *buf = (EipByte *)malloc(size_bytes);
    if (!buf) return NULL;
    memset(buf, 0, size_bytes);

    // 2) create the Assembly instance pointed at that buffer
    CipInstance *inst_ptr = CreateAssemblyObject(inst, buf, (EipUint16)size_bytes);
    if (!inst_ptr) { free(buf); return NULL; }  // creation failed

    // 3) remember buffer for later cleanup
    if (g_dyn_count < (sizeof g_dyn_asms / sizeof g_dyn_asms[0])) {
        g_dyn_asms[g_dyn_count++] = (DynAsm){inst, buf};
    }
    return inst_ptr;
}

// Call this from your device shutdown path (before ShutdownCipStack())
void FreeDynamicAssemblies(void) {
    for (size_t i = 0; i < g_dyn_count; ++i) {
        free(g_dyn_asms[i].buf);  // you free only the payload buffers you allocated
    }
    g_dyn_count = 0;
}


/*uint8_t input_assembly_num = 102;
uint8_t output_assembly_num = 101;
uint8_t config_assembly_num = 103;

uint8_t input_size = 128;
uint8_t output_size = 132;
uint8_t config_size = 0;*/


int input_assembly_num ;
int output_assembly_num ;
int config_assembly_num ;

int input_size ;
int output_size ;
int config_size ;

/* global functions called by the stack */
EipStatus ApplicationInitialization(void) {
  /* create 3 assembly object instances*/


    __android_log_print(ANDROID_LOG_INFO, "SURAJ",
                        "++ Assembly Data\n"
                        "__ input_assembly_num: %d\n"
                        "--output_assembly_num: %d\n"
                        "--config_assembly_num: %d\n"
                        "--input_size: %d\n"
                        "output_size: %d\n"
                        "config_size: %d",
                        input_assembly_num,
                        output_assembly_num,
                        config_assembly_num,
                        input_size,
                        output_size,
                        config_size);

/*  *//*INPUT*//*
  CreateAssemblyObject( DEMO_APP_INPUT_ASSEMBLY_NUM, g_assembly_data064,
                        sizeof(g_assembly_data064) );

  *//*OUTPUT*//*
  CreateAssemblyObject( DEMO_APP_OUTPUT_ASSEMBLY_NUM, g_assembly_data096,
                        sizeof(g_assembly_data096) );

  *//*CONFIG*//*
  CreateAssemblyObject( DEMO_APP_CONFIG_ASSEMBLY_NUM, g_assembly_data097,
                        sizeof(g_assembly_data097) );*/


    CreateDynamicAssembly(input_assembly_num,input_size);
    CreateDynamicAssembly(output_assembly_num, output_size);
    CreateDynamicAssembly(config_assembly_num, config_size);



  /*Heart-beat output assembly for Input only connections */
  CreateAssemblyObject(DEMO_APP_HEARTBEAT_INPUT_ONLY_ASSEMBLY_NUM, NULL, 0);

  /*Heart-beat output assembly for Listen only connections */
  CreateAssemblyObject(DEMO_APP_HEARTBEAT_LISTEN_ONLY_ASSEMBLY_NUM, NULL, 0);

  /* assembly for explicit messaging */
  CreateAssemblyObject( DEMO_APP_EXPLICT_ASSEMBLY_NUM, g_assembly_data09A,
                        sizeof(g_assembly_data09A) );



/*

  ConfigureExclusiveOwnerConnectionPoint(0, DEMO_APP_OUTPUT_ASSEMBLY_NUM,
                                         DEMO_APP_INPUT_ASSEMBLY_NUM,
                                         DEMO_APP_CONFIG_ASSEMBLY_NUM);
  ConfigureInputOnlyConnectionPoint(0,
                                    DEMO_APP_HEARTBEAT_INPUT_ONLY_ASSEMBLY_NUM,
                                    DEMO_APP_INPUT_ASSEMBLY_NUM,
                                    DEMO_APP_CONFIG_ASSEMBLY_NUM);
  ConfigureListenOnlyConnectionPoint(0,
                                     DEMO_APP_HEARTBEAT_LISTEN_ONLY_ASSEMBLY_NUM,
                                     DEMO_APP_INPUT_ASSEMBLY_NUM,
                                     DEMO_APP_CONFIG_ASSEMBLY_NUM);


*/


    ConfigureExclusiveOwnerConnectionPoint(0, output_assembly_num,
                                           input_assembly_num,
                                           config_assembly_num);
    ConfigureInputOnlyConnectionPoint(0,
                                      DEMO_APP_HEARTBEAT_INPUT_ONLY_ASSEMBLY_NUM,
                                      input_assembly_num,
                                      config_assembly_num);
    ConfigureListenOnlyConnectionPoint(0,
                                       DEMO_APP_HEARTBEAT_LISTEN_ONLY_ASSEMBLY_NUM,
                                       input_assembly_num,
                                       config_assembly_num);


    /* For NV data support connect callback functions for each object class with
     *  NV data.
     */
  InsertGetSetCallback(GetCipClass(kCipQoSClassCode), NvQosSetCallback,
                       kNvDataFunc);
  InsertGetSetCallback(GetCipClass(kCipTcpIpInterfaceClassCode),
                       NvTcpipSetCallback,
                       kNvDataFunc);

#if defined(OPENER_ETHLINK_CNTRS_ENABLE) && 0 != OPENER_ETHLINK_CNTRS_ENABLE
  /* For the Ethernet Interface & Media Counters connect a PreGetCallback and
   *  a PostGetCallback.
   * The PreGetCallback is used to fetch the counters from the hardware.
   * The PostGetCallback is utilized by the GetAndClear service to clear
   *  the hardware counters after the current data have been transmitted.
   */
  {
    CipClass *p_eth_link_class = GetCipClass(kCipEthernetLinkClassCode);
    InsertGetSetCallback(p_eth_link_class,
                         EthLnkPreGetCallback,
                         kPreGetFunc);
    InsertGetSetCallback(p_eth_link_class,
                         EthLnkPostGetCallback,
                         kPostGetFunc);
    /* Specify the attributes for which the callback should be executed. */
    for (int idx = 0; idx < OPENER_ETHLINK_INSTANCE_CNT; ++idx)
    {
      CipAttributeStruct *p_eth_link_attr;
      CipInstance *p_eth_link_inst =
        GetCipInstance(p_eth_link_class, idx + 1);
      OPENER_ASSERT(p_eth_link_inst);

      /* Interface counters attribute */
      p_eth_link_attr = GetCipAttribute(p_eth_link_inst, 4);
      p_eth_link_attr->attribute_flags |= (kPreGetFunc | kPostGetFunc);
      /* Media counters attribute */
      p_eth_link_attr = GetCipAttribute(p_eth_link_inst, 5);
      p_eth_link_attr->attribute_flags |= (kPreGetFunc | kPostGetFunc);
    }
  }
#endif

  return kEipStatusOk;
}

void HandleApplication(void) {
  /* check if application needs to trigger an connection */
}

void CheckIoConnectionEvent(unsigned int output_assembly_id,
                            unsigned int input_assembly_id,
                            IoConnectionEvent io_connection_event) {
  /* maintain a correct output state according to the connection state*/

  (void) output_assembly_id; /* suppress compiler warning */
  (void) input_assembly_id; /* suppress compiler warning */
  (void) io_connection_event; /* suppress compiler warning */
}

EipStatus AfterAssemblyDataReceived(CipInstance *instance) {
  EipStatus status = kEipStatusOk;

  /*handle the data received e.g., update outputs of the device */
  switch (instance->instance_number) {
    case DEMO_APP_OUTPUT_ASSEMBLY_NUM:
      /* Data for the output assembly has been received.
       * Mirror it to the inputs */
//      memcpy( &g_assembly_data064[0], &g_assembly_data096[0],
//              sizeof(g_assembly_data064) );
      break;
    case DEMO_APP_EXPLICT_ASSEMBLY_NUM:
      /* do something interesting with the new data from
       * the explicit set-data-attribute message */
      break;
    case DEMO_APP_CONFIG_ASSEMBLY_NUM:
      /* Add here code to handle configuration data and check if it is ok
       * The demo application does not handle config data.
       * However in order to pass the test we accept any data given.
       * EIP_ERROR
       */
      status = kEipStatusOk;
      break;
    default:
      OPENER_TRACE_INFO(
        "Unknown assembly instance ind AfterAssemblyDataReceived");
      break;
  }
  return status;
}




EipBool8 BeforeAssemblyDataSend1(CipInstance *pa_pstInstance) {
  /*update data to be sent e.g., read inputs of the device */
  /*In this sample app we mirror the data from out to inputs on data receive
   * therefore we need nothing to do here. Just return true to inform that
   * the data is new.
   */

    sendDataToJavaFromCPPWrapper(g_assembly_data096, ASSEMBLY_SIZE+4);



    if (pa_pstInstance->instance_number == DEMO_APP_EXPLICT_ASSEMBLY_NUM) {
    /* do something interesting with the existing data
     * for the explicit get-data-attribute message */
  }
  return true;
}


#include "cipassembly.h"
#include "opener_api.h"

static inline CipByteArray *AsmByteArray(CipInstance *inst) {
    // Attribute 3 is the data buffer for assemblies
    // (kAssemblyObjectInstanceAttributeIdData == 3)
    CipAttributeStruct *a = GetCipAttribute(inst, kAssemblyObjectInstanceAttributeIdData);
    return a ? (CipByteArray *)a->data : NULL;
}



EipBool8 BeforeAssemblyDataSend(CipInstance *inst) {
    CipByteArray *ba = AsmByteArray(inst);
    if (ba && ba->data) {
        // Use the actual size of Attribute 3 (no hard-coded ASSEMBLY_SIZE)
        sendDataToJavaFromCPPWrapper(ba->data, (int)ba->length);
    }
    if (inst->instance_number == DEMO_APP_EXPLICT_ASSEMBLY_NUM) {
        // optional per-instance handling
    }
    return true;
}

EipStatus ResetDevice(void) {
  /* add reset code here*/
  CloseAllConnections();
  CipQosUpdateUsedSetQosValues();
  return kEipStatusOk;
}

EipStatus ResetDeviceToInitialConfiguration(void) {
  /*rest the parameters */
  g_tcpip.encapsulation_inactivity_timeout = 120;
  CipQosResetAttributesToDefaultValues();
  /*than perform device reset*/
  ResetDevice();
  return kEipStatusOk;
}

void *
CipCalloc(size_t number_of_elements,
          size_t size_of_element) {
  return calloc(number_of_elements, size_of_element);
}

void CipFree(void *data) {
  free(data);
}

void RunIdleChanged(EipUint32 run_idle_value) {
  OPENER_TRACE_INFO("Run/Idle handler triggered\n");
  if( (0x0001 & run_idle_value) == 1 ) {
    CipIdentitySetExtendedDeviceStatus(kAtLeastOneIoConnectionInRunMode);
  } else {
    CipIdentitySetExtendedDeviceStatus(
      kAtLeastOneIoConnectionEstablishedAllInIdleMode);
  }
  (void) run_idle_value;
}

