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

#include <stdint.h>
#include <stdbool.h>

#ifndef TYPES_H
#define TYPES_H

typedef uint64_t DockerClient;

typedef struct {
    char* APIVersion;
    char* OSType;
    bool Experimental;
    char* BuilderVersion;
} PingResponse;

PingResponse* AllocPingResponse();
void FreePingResponse(PingResponse* value);

typedef struct {
    char* Type;
    char* Message;
} Error;

Error* AllocError();
void FreeError(Error* value);

typedef struct {
    PingResponse* Response;
    Error* Error;
} PingReturn;

PingReturn* AllocPingReturn();
void FreePingReturn(PingReturn* value);

typedef struct {
    DockerClient Client;
    Error* Error;
} CreateClientReturn;

CreateClientReturn* AllocCreateClientReturn();
void FreeCreateClientReturn(CreateClientReturn* value);

#endif
