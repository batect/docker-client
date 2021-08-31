#include <stdint.h>
#include <stdbool.h>

#ifndef TYPES_H
#define TYPES_H

typedef uintptr_t DockerClient;

typedef struct {
    char* APIVersion;
    char* OSType;
    bool Experimental;
    char* BuilderVersion;
} PingResponse;

PingResponse* allocPingResponse();
void freePingResponse(PingResponse* value);

typedef struct {
    char* Type;
    char* Message;
} Error;

Error* allocError();
void freeError(Error* value);

typedef struct {
    PingResponse* Response;
    Error* Error;
} PingReturn;

PingReturn* allocPingReturn();
void freePingReturn(PingReturn* value);

typedef struct {
    DockerClient Client;
    Error* Error;
} CreateClientReturn;

CreateClientReturn* allocCreateClientReturn();
void freeCreateClientReturn(CreateClientReturn* value);

#endif
