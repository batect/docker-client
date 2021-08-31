#include <stdlib.h>
#include "types.h"

PingResponse* allocPingResponse() {
    PingResponse* value = malloc(sizeof(PingResponse));
    value->APIVersion = NULL;
    value->OSType = NULL;
    value->BuilderVersion = NULL;

    return value;
}

void freePingResponse(PingResponse* value) {
    free(value->APIVersion);
    free(value->OSType);
    free(value->BuilderVersion);
    free(value);
}

Error* allocError() {
    Error* value = malloc(sizeof(Error));
    value->Type = NULL;
    value->Message = NULL;

    return value;
}

void freeError(Error* value) {
    free(value->Message);
    free(value);
}

PingReturn* allocPingReturn() {
    PingReturn* value = malloc(sizeof(PingReturn));
    value->Response = NULL;
    value->Error = NULL;

    return value;
}

void freePingReturn(PingReturn* value) {
    if (value->Response != NULL) {
        freePingResponse(value->Response);
    }

    if (value->Error != NULL) {
        freeError(value->Error);
    }

    free(value);
}

CreateClientReturn* allocCreateClientReturn() {
    CreateClientReturn* value = malloc(sizeof(CreateClientReturn));
    value->Error = NULL;

    return value;
}

void freeCreateClientReturn(CreateClientReturn* value) {
    if (value->Error != NULL) {
        freeError(value->Error);
    }

    free(value);
}
