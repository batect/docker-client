#include <stdlib.h>
#include "types.h"

PingResponse* AllocPingResponse() {
    PingResponse* value = malloc(sizeof(PingResponse));
    value->APIVersion = NULL;
    value->OSType = NULL;
    value->BuilderVersion = NULL;

    return value;
}

void FreePingResponse(PingResponse* value) {
    free(value->APIVersion);
    free(value->OSType);
    free(value->BuilderVersion);
    free(value);
}

Error* AllocError() {
    Error* value = malloc(sizeof(Error));
    value->Type = NULL;
    value->Message = NULL;

    return value;
}

void FreeError(Error* value) {
    free(value->Message);
    free(value);
}

PingReturn* AllocPingReturn() {
    PingReturn* value = malloc(sizeof(PingReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void FreePingReturn(PingReturn* value) {
    if (value->Response != NULL) {
        FreePingResponse(value->Response);
    }

    if (value->Error != NULL) {
        FreeError(value->Error);
    }

    free(value);
}

CreateClientReturn* AllocCreateClientReturn() {
    CreateClientReturn* value = malloc(sizeof(CreateClientReturn));
    value->Error = NULL;

    return value;
}

void FreeCreateClientReturn(CreateClientReturn* value) {
    if (value->Error != NULL) {
        FreeError(value->Error);
    }

    free(value);
}
