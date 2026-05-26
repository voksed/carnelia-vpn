#include "ServerProfile.h"
#include <glib.h>
#include <json-glib/json-glib.h>
#include <algorithm>
#include <cctype>
#include <sstream>

static std::string trim(const std::string& value)
{
    auto start = value.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return {};
    auto end = value.find_last_not_of(" \t\r\n");
    return value.substr(start, end - start + 1);
}

static std::string uriUnescape(const std::string& value)
{
    gchar* decoded = g_uri_unescape_string(value.c_str(), nullptr);
    std::string result;
    if (decoded) {
        result = decoded;
        g_free(decoded);
    }
    return result;
}

static std::string base64Decode(const std::string& value)
{
    gsize outLen = 0;
    gchar* decoded = reinterpret_cast<gchar*>(g_base64_decode(value.c_str(), &outLen));
    std::string result;
    if (decoded) {
        result.assign(decoded, outLen);
        g_free(decoded);
    }
    return result;
}

static std::map<std::string, std::string> parseQuery(const std::string& query)
{
    std::map<std::string, std::string> result;
    size_t pos = 0;
    while (pos < query.size()) {
        size_t amp = query.find('&', pos);
        size_t eq = query.find('=', pos);
        if (eq != std::string::npos && eq < amp) {
            std::string key = uriUnescape(query.substr(pos, eq - pos));
            std::string value = uriUnescape(query.substr(eq + 1, amp - eq - 1));
            result[key] = value;
        }
        if (amp == std::string::npos) break;
        pos = amp + 1;
    }
    return result;
}

static bool startsWith(const std::string& value, const std::string& prefix)
{
    return value.size() >= prefix.size() && value.compare(0, prefix.size(), prefix) == 0;
}

ServerProfile ServerProfile::fromUrl(const std::string& rawUrl)
{
    ServerProfile p;
    std::string url = trim(rawUrl);

    if (startsWith(url, "vless://") || startsWith(url, "trojan://")) {
        GError* error = nullptr;
        GUri* uri = g_uri_parse(url.c_str(), G_URI_FLAGS_NONE, &error);
        if (!uri) {
            if (error) g_error_free(error);
            return p;
        }

        if (startsWith(url, "vless://")) {
            p.protocol = Protocol::VLESS;
        } else {
            p.protocol = Protocol::TROJAN;
            p.security = "tls";
        }

        if (const gchar* userInfo = g_uri_get_userinfo(uri))
            p.uuid = userInfo;
        if (p.protocol == Protocol::TROJAN)
            p.password = p.uuid;

        if (const gchar* host = g_uri_get_host(uri))
            p.address = host;
        int port = g_uri_get_port(uri);
        p.port = port == 0 ? 443 : port;
        if (const gchar* fragment = g_uri_get_fragment(uri))
            p.name = uriUnescape(fragment);
        if (const gchar* query = g_uri_get_query(uri)) {
            auto params = parseQuery(query);
            p.network = params.count("type") ? params["type"] : "tcp";
            if (p.protocol == Protocol::VLESS) {
                p.security    = params.count("security") ? params["security"] : "none";
                p.path        = params.count("path") ? uriUnescape(params["path"]) : "/";
                p.host        = params.count("host") ? params["host"] : "";
                p.serverName  = params.count("sni") ? params["sni"] : "";
                p.flow        = params.count("flow") ? params["flow"] : "";
                p.publicKey   = params.count("pbk") ? params["pbk"] : "";
                p.shortId     = params.count("sid") ? params["sid"] : "";
                p.fingerprint = params.count("fp") ? params["fp"] : "";
            } else {
                p.serverName = params.count("sni") ? params["sni"] : "";
                p.path       = params.count("path") ? params["path"] : "/";
            }
        }

        g_uri_unref(uri);
        return p;
    }

    if (startsWith(url, "vmess://")) {
        std::string decoded = base64Decode(url.substr(8));
        if (!decoded.empty()) {
            GError* error = nullptr;
            JsonParser* parser = json_parser_new();
            if (json_parser_load_from_data(parser, decoded.c_str(), decoded.size(), &error)) {
                JsonNode* root = json_parser_get_root(parser);
                if (JSON_NODE_HOLDS_OBJECT(root)) {
                    JsonObject* obj = json_node_get_object(root);
                    p.protocol   = Protocol::VMESS;
                    p.name       = json_object_get_string_member(obj, "ps");
                    p.address    = json_object_get_string_member(obj, "add");
                    p.port       = json_object_get_int_member(obj, "port");
                    p.uuid       = json_object_get_string_member(obj, "id");
                    p.network    = json_object_get_string_member(obj, "net");
                    if (p.network.empty()) p.network = "tcp";
                    p.path       = json_object_get_string_member(obj, "path");
                    if (p.path.empty()) p.path = "/";
                    p.host       = json_object_get_string_member(obj, "host");
                    p.serverName = json_object_get_string_member(obj, "sni");
                    p.security   = json_object_get_string_member(obj, "tls");
                    if (p.security != "tls") p.security = "none";
                }
                json_node_free(root);
            }
            if (error) g_error_free(error);
            g_object_unref(parser);
        }
        return p;
    }

    if (startsWith(url, "ss://")) {
        p.protocol = Protocol::SHADOWSOCKS;
        std::string body = url.substr(5);
        size_t hashPos = body.rfind('#');
        if (hashPos != std::string::npos) {
            p.name = uriUnescape(body.substr(hashPos + 1));
            body = body.substr(0, hashPos);
        }

        size_t atPos = body.rfind('@');
        if (atPos != std::string::npos) {
            std::string auth = base64Decode(body.substr(0, atPos));
            size_t colonPos = auth.find(':');
            if (colonPos != std::string::npos) {
                p.method = auth.substr(0, colonPos);
                p.password = auth.substr(colonPos + 1);
            }
            std::string hostPort = body.substr(atPos + 1);
            size_t lastColon = hostPort.rfind(':');
            if (lastColon != std::string::npos) {
                p.address = hostPort.substr(0, lastColon);
                p.port = std::stoi(hostPort.substr(lastColon + 1));
            }
        } else {
            std::string decoded = base64Decode(body);
            size_t colonPos = decoded.find(':');
            if (colonPos != std::string::npos) {
                p.method = decoded.substr(0, colonPos);
                std::string rest = decoded.substr(colonPos + 1);
                size_t at = rest.rfind('@');
                if (at != std::string::npos) {
                    p.password = rest.substr(0, at);
                    std::string hostPort = rest.substr(at + 1);
                    size_t lastColon = hostPort.rfind(':');
                    if (lastColon != std::string::npos) {
                        p.address = hostPort.substr(0, lastColon);
                        p.port = std::stoi(hostPort.substr(lastColon + 1));
                    }
                }
            }
        }
        return p;
    }

    return p;
}

std::string ServerProfile::protocolName() const
{
    switch (protocol) {
    case Protocol::VLESS:       return "VLESS";
    case Protocol::VMESS:       return "VMess";
    case Protocol::SHADOWSOCKS: return "Shadowsocks";
    case Protocol::TROJAN:      return "Trojan";
    default:                    return "Unknown";
    }
}
