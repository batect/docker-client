// Copyright 2017-2021 Charles Korn.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
