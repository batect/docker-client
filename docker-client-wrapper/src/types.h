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
