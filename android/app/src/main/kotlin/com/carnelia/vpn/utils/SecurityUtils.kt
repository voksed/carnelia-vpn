package com.carnelia.vpn.utils

import android.content.Context
import com.carnelia.vpn.R
import com.carnelia.vpn.core.VpnServerConfig
import com.carnelia.vpn.core.VpnProtocol

object SecurityUtils {

    fun analyzeSecurity(context: Context, config: VpnServerConfig): Pair<String, String> {
        val score: String
        val report = StringBuilder()
        var safetyPoints = 0

        // 1. Protocol Check
        when (config.protocol) {
            VpnProtocol.VLESS -> {
                safetyPoints += 3
                report.append(context.getString(R.string.sec_proto_vless))
                
                val flow = config.config["flow"]
                if (flow == "xtls-rprx-vision") {
                    safetyPoints += 2
                    report.append(context.getString(R.string.sec_flow_vision))
                }

                val security = config.config["security"]
                if (security == "reality") {
                    safetyPoints += 5
                    report.append(context.getString(R.string.sec_security_reality))
                } else if (security == "tls") {
                    safetyPoints += 3
                    report.append(context.getString(R.string.sec_security_tls))
                } else {
                    safetyPoints -= 2
                    report.append(context.getString(R.string.sec_security_none))
                }
            }
            VpnProtocol.VMESS -> {
                safetyPoints += 2
                report.append(context.getString(R.string.sec_proto_vmess))
                if (config.config["tls"] == "tls" || config.config["net"] == "ws") {
                    safetyPoints += 2
                    report.append(context.getString(R.string.sec_obfu_tls))
                }
            }
            VpnProtocol.SHADOWSOCKS -> {
                if (config.config["method"]?.contains("2022") == true) {
                    safetyPoints += 3
                    report.append(context.getString(R.string.sec_proto_ss2022))
                } else {
                    safetyPoints += 1
                    report.append(context.getString(R.string.sec_proto_ss))
                }
            }
            VpnProtocol.WIREGUARD -> {
                safetyPoints += 4
                report.append(context.getString(R.string.sec_proto_wg))
            }
            VpnProtocol.AMNEZIA_WG -> {
                safetyPoints += 5  // WireGuard + obfuscation layer
                report.append("AmneziaWG — WireGuard с анти-DPI обфускацией.\n")
            }
            VpnProtocol.OUTLINE -> {
                safetyPoints += 2
                report.append(context.getString(R.string.sec_proto_outline))
            }
             VpnProtocol.OPENVPN -> {
                safetyPoints += 3
                report.append(context.getString(R.string.sec_proto_openvpn))
            }
            else -> {
                report.append(context.getString(R.string.sec_proto_other, config.protocol.name))
            }
        }

        // 2. Port Check
        if (config.port == 443) {
            safetyPoints += 2
            report.append(context.getString(R.string.sec_port_443))
        } else if (config.port == 80) {
            report.append(context.getString(R.string.sec_port_80))
        } else {
            report.append(context.getString(R.string.sec_port_other, config.port))
        }

        // 3. Conclusion
        if (safetyPoints >= 8) {
            score = context.getString(R.string.security_high)
            report.insert(0, context.getString(R.string.sec_report_high_intro))
        } else if (safetyPoints >= 5) {
            score = context.getString(R.string.security_medium)
            report.insert(0, context.getString(R.string.sec_report_med_intro))
        } else {
            score = context.getString(R.string.security_low)
            report.insert(0, context.getString(R.string.sec_report_low_intro))
        }

        return Pair(score, report.toString().trim())
    }
}
