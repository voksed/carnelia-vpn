package com.carnelia.vpn.utils

import com.carnelia.vpn.core.VpnProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigParserVlessTest {

    @Test
    fun `parse grpc vless keeps serviceName authority and allowInsecure`() {
        val config = ConfigParser.parseOrThrow(
            "VLESS://11111111-1111-1111-1111-111111111111@example.com:443?type=gRpc&security=tLs&serviceName=myGrpc&authority=grpc.example.com&allowInsecure=true#Grpc"
        )

        assertEquals(VpnProtocol.VLESS, config.protocol)
        assertEquals("grpc", config.config["type"])
        assertEquals("tls", config.config["security"])
        assertEquals("myGrpc", config.config["serviceName"])
        assertEquals("grpc.example.com", config.config["authority"])
        assertEquals("1", config.config["allowInsecure"])
    }

    @Test
    fun `parse split http vless normalizes to xhttp`() {
        val config = ConfigParser.parseOrThrow(
            "vless://11111111-1111-1111-1111-111111111111@example.com:443?type=splithttp&host=cdn.example.com&path=%2Fsudoku&mode=stream-one#Xhttp"
        )

        assertEquals(VpnProtocol.VLESS, config.protocol)
        assertEquals("xhttp", config.config["type"])
        assertEquals("cdn.example.com", config.config["host_header"])
        assertEquals("/sudoku", config.config["path"])
        assertEquals("stream-one", config.config["mode"])
    }
}