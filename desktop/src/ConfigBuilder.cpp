#include "ConfigBuilder.h"
#include <glib.h>
#include <json-glib/json-glib.h>
#include <sstream>
#include <string>

static JsonNode* buildServerOutbound(const ServerProfile& server,
                                    bool muxEnabled,
                                    int muxConcurrency,
                                    bool fragEnabled,
                                    const std::string& fragMode)
{
    JsonBuilder* builder = json_builder_new();
    json_builder_begin_object(builder);

    json_builder_set_member_name(builder, "tag");
    json_builder_add_string_value(builder, "proxy");

    json_builder_set_member_name(builder, "streamSettings");
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "network");
    json_builder_add_string_value(builder, server.network.c_str());

    if (server.security == "tls") {
        json_builder_set_member_name(builder, "security");
        json_builder_add_string_value(builder, "tls");
        json_builder_set_member_name(builder, "tlsSettings");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "serverName");
        json_builder_add_string_value(builder,
            server.serverName.empty() ? (server.host.empty() ? server.address.c_str() : server.host.c_str()) : server.serverName.c_str());
        json_builder_set_member_name(builder, "allowInsecure");
        json_builder_add_boolean_value(builder, FALSE);
        if (!server.fingerprint.empty()) {
            json_builder_set_member_name(builder, "fingerprint");
            json_builder_add_string_value(builder, server.fingerprint.c_str());
        }
        json_builder_end_object(builder);
    } else if (server.security == "reality") {
        json_builder_set_member_name(builder, "security");
        json_builder_add_string_value(builder, "reality");
        json_builder_set_member_name(builder, "realitySettings");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "serverName");
        json_builder_add_string_value(builder, server.serverName.empty() ? server.address.c_str() : server.serverName.c_str());
        json_builder_set_member_name(builder, "fingerprint");
        json_builder_add_string_value(builder, server.fingerprint.empty() ? "chrome" : server.fingerprint.c_str());
        json_builder_set_member_name(builder, "publicKey");
        json_builder_add_string_value(builder, server.publicKey.c_str());
        json_builder_set_member_name(builder, "shortId");
        json_builder_add_string_value(builder, server.shortId.c_str());
        json_builder_end_object(builder);
    } else {
        json_builder_set_member_name(builder, "security");
        json_builder_add_string_value(builder, "none");
    }

    if (server.network == "ws") {
        json_builder_set_member_name(builder, "wsSettings");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "path");
        json_builder_add_string_value(builder, server.path.empty() ? "/" : server.path.c_str());
        if (!server.host.empty()) {
            json_builder_set_member_name(builder, "headers");
            json_builder_begin_object(builder);
            json_builder_set_member_name(builder, "Host");
            json_builder_add_string_value(builder, server.host.c_str());
            json_builder_end_object(builder);
        }
        json_builder_end_object(builder);
    } else if (server.network == "grpc") {
        json_builder_set_member_name(builder, "grpcSettings");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "serviceName");
        json_builder_add_string_value(builder, server.path.c_str());
        json_builder_end_object(builder);
    } else if (server.network == "h2") {
        json_builder_set_member_name(builder, "httpSettings");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "path");
        json_builder_add_string_value(builder, server.path.empty() ? "/" : server.path.c_str());
        if (!server.host.empty()) {
            json_builder_set_member_name(builder, "host");
            json_builder_begin_array(builder);
            json_builder_add_string_value(builder, server.host.c_str());
            json_builder_end_array(builder);
        }
        json_builder_end_object(builder);
    }

    if (muxEnabled) {
        json_builder_set_member_name(builder, "mux");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "enabled");
        json_builder_add_boolean_value(builder, TRUE);
        json_builder_set_member_name(builder, "concurrency");
        json_builder_add_int_value(builder, muxConcurrency);
        json_builder_end_object(builder);
    }

    if (fragEnabled) {
        json_builder_set_member_name(builder, "sockopt");
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "fragment");
        json_builder_begin_object(builder);
        if (fragMode == "light") {
            json_builder_set_member_name(builder, "packets"); json_builder_add_string_value(builder, "1-1");
            json_builder_set_member_name(builder, "length"); json_builder_add_string_value(builder, "500-1000");
            json_builder_set_member_name(builder, "interval"); json_builder_add_string_value(builder, "1-2");
        } else if (fragMode == "aggressive") {
            json_builder_set_member_name(builder, "packets"); json_builder_add_string_value(builder, "2-5");
            json_builder_set_member_name(builder, "length"); json_builder_add_string_value(builder, "40-80");
            json_builder_set_member_name(builder, "interval"); json_builder_add_string_value(builder, "30-50");
        } else {
            json_builder_set_member_name(builder, "packets"); json_builder_add_string_value(builder, "1-2");
            json_builder_set_member_name(builder, "length"); json_builder_add_string_value(builder, "100-200");
            json_builder_set_member_name(builder, "interval"); json_builder_add_string_value(builder, "10-20");
        }
        json_builder_end_object(builder);
        json_builder_end_object(builder);
    }

    json_builder_end_object(builder);

    json_builder_set_member_name(builder, "protocol");
    if (server.protocol == Protocol::VLESS) json_builder_add_string_value(builder, "vless");
    else if (server.protocol == Protocol::VMESS) json_builder_add_string_value(builder, "vmess");
    else if (server.protocol == Protocol::SHADOWSOCKS) json_builder_add_string_value(builder, "shadowsocks");
    else if (server.protocol == Protocol::TROJAN) json_builder_add_string_value(builder, "trojan");
    else json_builder_add_string_value(builder, "unknown");

    json_builder_set_member_name(builder, "settings");
    json_builder_begin_object(builder);
    if (server.protocol == Protocol::VLESS || server.protocol == Protocol::VMESS) {
        json_builder_set_member_name(builder, "vnext");
        json_builder_begin_array(builder);
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "address");
        json_builder_add_string_value(builder, server.address.c_str());
        json_builder_set_member_name(builder, "port");
        json_builder_add_int_value(builder, server.port);
        json_builder_set_member_name(builder, "users");
        json_builder_begin_array(builder);
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "id");
        json_builder_add_string_value(builder, server.uuid.c_str());
        if (server.protocol == Protocol::VLESS) {
            json_builder_set_member_name(builder, "encryption");
            json_builder_add_string_value(builder, "none");
            if (!server.flow.empty()) {
                json_builder_set_member_name(builder, "flow");
                json_builder_add_string_value(builder, server.flow.c_str());
            }
        } else {
            json_builder_set_member_name(builder, "alterId");
            json_builder_add_int_value(builder, 0);
            json_builder_set_member_name(builder, "security");
            json_builder_add_string_value(builder, "auto");
        }
        json_builder_end_object(builder);
        json_builder_end_array(builder);
        json_builder_end_object(builder);
        json_builder_end_array(builder);
    } else if (server.protocol == Protocol::SHADOWSOCKS) {
        json_builder_set_member_name(builder, "servers");
        json_builder_begin_array(builder);
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "address");
        json_builder_add_string_value(builder, server.address.c_str());
        json_builder_set_member_name(builder, "port");
        json_builder_add_int_value(builder, server.port);
        json_builder_set_member_name(builder, "method");
        json_builder_add_string_value(builder, server.method.c_str());
        json_builder_set_member_name(builder, "password");
        json_builder_add_string_value(builder, server.password.c_str());
        json_builder_end_object(builder);
        json_builder_end_array(builder);
    } else if (server.protocol == Protocol::TROJAN) {
        json_builder_set_member_name(builder, "servers");
        json_builder_begin_array(builder);
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "address");
        json_builder_add_string_value(builder, server.address.c_str());
        json_builder_set_member_name(builder, "port");
        json_builder_add_int_value(builder, server.port);
        json_builder_set_member_name(builder, "password");
        json_builder_add_string_value(builder, server.password.c_str());
        json_builder_end_object(builder);
        json_builder_end_array(builder);
    }
    json_builder_end_object(builder);

    JsonNode* root = json_builder_get_root(builder);
    JsonNode* result = json_node_copy(root);
    g_object_unref(builder);
    return result;
}

std::string ConfigBuilder::build(
    const ServerProfile& server,
    int socksPort,
    int httpPort,
    bool bypassRussia,
    const std::string& logLevel,
    bool muxEnabled,
    int muxConcurrency,
    bool fragEnabled,
    const std::string& fragMode)
{
    JsonBuilder* builder = json_builder_new();
    json_builder_begin_object(builder);

    json_builder_set_member_name(builder, "log");
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "loglevel");
    json_builder_add_string_value(builder, logLevel.c_str());
    json_builder_set_member_name(builder, "access");
    json_builder_add_string_value(builder, "");
    json_builder_set_member_name(builder, "error");
    json_builder_add_string_value(builder, "");
    json_builder_end_object(builder);

    json_builder_set_member_name(builder, "inbounds");
    json_builder_begin_array(builder);
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "tag");
    json_builder_add_string_value(builder, "socks");
    json_builder_set_member_name(builder, "port");
    json_builder_add_int_value(builder, socksPort);
    json_builder_set_member_name(builder, "listen");
    json_builder_add_string_value(builder, "127.0.0.1");
    json_builder_set_member_name(builder, "protocol");
    json_builder_add_string_value(builder, "socks");
    json_builder_set_member_name(builder, "settings");
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "auth");
    json_builder_add_string_value(builder, "noauth");
    json_builder_set_member_name(builder, "udp");
    json_builder_add_boolean_value(builder, TRUE);
    json_builder_end_object(builder);
    json_builder_end_object(builder);

    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "tag");
    json_builder_add_string_value(builder, "http");
    json_builder_set_member_name(builder, "port");
    json_builder_add_int_value(builder, httpPort);
    json_builder_set_member_name(builder, "listen");
    json_builder_add_string_value(builder, "127.0.0.1");
    json_builder_set_member_name(builder, "protocol");
    json_builder_add_string_value(builder, "http");
    json_builder_set_member_name(builder, "settings");
    json_builder_begin_object(builder);
    json_builder_end_object(builder);
    json_builder_end_object(builder);
    json_builder_end_array(builder);

    json_builder_set_member_name(builder, "outbounds");
    json_builder_begin_array(builder);
    JsonNode* outbound = buildServerOutbound(server, muxEnabled, muxConcurrency, fragEnabled, fragMode);
    json_builder_add_value(builder, outbound);
    json_node_free(outbound);

    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "tag");
    json_builder_add_string_value(builder, "direct");
    json_builder_set_member_name(builder, "protocol");
    json_builder_add_string_value(builder, "freedom");
    json_builder_end_object(builder);

    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "tag");
    json_builder_add_string_value(builder, "block");
    json_builder_set_member_name(builder, "protocol");
    json_builder_add_string_value(builder, "blackhole");
    json_builder_end_object(builder);
    json_builder_end_array(builder);

    json_builder_set_member_name(builder, "routing");
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "domainStrategy");
    json_builder_add_string_value(builder, "IPIfNonMatch");
    json_builder_set_member_name(builder, "rules");
    json_builder_begin_array(builder);
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "type");
    json_builder_add_string_value(builder, "field");
    json_builder_set_member_name(builder, "outboundTag");
    json_builder_add_string_value(builder, "direct");
    json_builder_set_member_name(builder, "ip");
    json_builder_begin_array(builder);
    json_builder_add_string_value(builder, "geoip:private");
    json_builder_end_array(builder);
    json_builder_end_object(builder);
    if (bypassRussia) {
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "type");
        json_builder_add_string_value(builder, "field");
        json_builder_set_member_name(builder, "outboundTag");
        json_builder_add_string_value(builder, "direct");
        json_builder_set_member_name(builder, "ip");
        json_builder_begin_array(builder);
        json_builder_add_string_value(builder, "geoip:ru");
        json_builder_end_array(builder);
        json_builder_end_object(builder);
        json_builder_begin_object(builder);
        json_builder_set_member_name(builder, "type");
        json_builder_add_string_value(builder, "field");
        json_builder_set_member_name(builder, "outboundTag");
        json_builder_add_string_value(builder, "direct");
        json_builder_set_member_name(builder, "domain");
        json_builder_begin_array(builder);
        json_builder_add_string_value(builder, "geosite:ru");
        json_builder_end_array(builder);
        json_builder_end_object(builder);
    }
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "type");
    json_builder_add_string_value(builder, "field");
    json_builder_set_member_name(builder, "outboundTag");
    json_builder_add_string_value(builder, "proxy");
    json_builder_set_member_name(builder, "network");
    json_builder_add_string_value(builder, "tcp,udp");
    json_builder_end_object(builder);
    json_builder_end_array(builder);
    json_builder_end_object(builder);

    json_builder_set_member_name(builder, "dns");
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "servers");
    json_builder_begin_array(builder);
    json_builder_add_string_value(builder, "8.8.8.8");
    json_builder_add_string_value(builder, "1.1.1.1");
    json_builder_end_array(builder);
    json_builder_end_object(builder);

    json_builder_end_object(builder);

    JsonNode* root = json_builder_get_root(builder);
    JsonGenerator* gen = json_generator_new();
    json_generator_set_root(gen, root);
    gsize length = 0;
    gchar* text = json_generator_to_data(gen, &length);
    std::string result;
    if (text) {
        result.assign(text, length);
        g_free(text);
    }
    g_object_unref(gen);
    json_node_free(root);
    g_object_unref(builder);
    return result;
}
