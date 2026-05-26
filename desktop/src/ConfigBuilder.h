#pragma once
#include <string>
#include "ServerProfile.h"

class ConfigBuilder
{
public:
    // Build a full xray-core config JSON string.
    // SOCKS5 inbound on socksPort, HTTP proxy on httpPort.
    static std::string build(
        const ServerProfile& server,
        int  socksPort,
        int  httpPort,
        bool bypassRussia,
        const std::string& logLevel = "warning",
        bool muxEnabled = false,
        int muxConcurrency = 4,
        bool fragEnabled = false,
        const std::string& fragMode = "balanced"
    );
};
