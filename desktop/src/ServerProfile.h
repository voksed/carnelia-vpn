#pragma once
#include <string>
#include <vector>
#include <map>

enum class Protocol { VLESS, VMESS, SHADOWSOCKS, TROJAN, UNKNOWN };

struct ServerProfile {
    std::string name;
    std::string address;
    int         port     = 443;
    Protocol    protocol = Protocol::UNKNOWN;

    // VLESS / VMESS
    std::string uuid;

    // Shadowsocks / Trojan
    std::string method;
    std::string password;

    // Transport
    std::string network  = "tcp";   // tcp, ws, grpc, h2
    std::string security = "none";  // none, tls, reality
    std::string path     = "/";
    std::string host;
    std::string serverName;

    // REALITY
    std::string publicKey;
    std::string shortId;
    std::string fingerprint;

    // XTLS flow (VLESS)
    std::string flow;

    bool isValid() const { return !address.empty() && port > 0 && protocol != Protocol::UNKNOWN; }

    static ServerProfile fromUrl(const std::string& url);
    std::string displayName() const { return name.empty() ? address + ":" + std::to_string(port) : name; }
    std::string protocolName() const;
};
