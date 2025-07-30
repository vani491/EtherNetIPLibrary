//
// Created by admin on 19-07-2025.
//

#ifndef ETHERNETIPLIBRARY_JNI_BRIDGE_H
#define ETHERNETIPLIBRARY_JNI_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int sendDataToJavaFromCPPWrapper(const uint8_t* data, int length);

#ifdef __cplusplus
}
#endif


#endif //ETHERNETIPLIBRARY_JNI_BRIDGE_H
