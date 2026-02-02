package com.carnelia.vpn.core.xray

import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class XrayConfigBuilderTest {

    @Test
    fun `test build vless config`() {
        val config = VpnServerConfig(
            id = "1",
            name = "Test Server",
            protocol = VpnProtocol.VLESS,
            host = "example.com",
            port = 443,
            config = mapOf(
                "uuid" to UUID.randomUUID().toString(),
                "flow" to "xtls-rprx-vision"
            )
        )

        val json = XrayConfigBuilder.build(config)
        assertNotNull(json)
        assertTrue(json.contains("inbounds"))
        assertTrue(json.contains("outbounds"))
        assertTrue(json.contains("vless"))
        assertTrue(json.contains("example.com"))
    }

    @Test
    fun `test build vmess config`() {
        val config = VpnServerConfig(
            id = "2",
            name = "VMess Server",
            protocol = VpnProtocol.VMESS,
            host = "vmess.com",
            port = 80,
            config = mapOf(
                "uuid" to UUID.randomUUID().toString(),
                "alterId" to "0"
            )
        )

        val json = XrayConfigBuilder.build(config)
        assertNotNull(json)
        assertTrue(json.contains("vmess"))
        assertTrue(json.contains("vmess.com"))
    }
}
