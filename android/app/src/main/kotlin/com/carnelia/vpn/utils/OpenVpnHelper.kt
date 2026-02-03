package com.carnelia.vpn.utils

import android.content.Context
import com.carnelia.vpn.core.VpnServerConfig
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ProfileManager
import java.io.StringReader
import java.util.UUID

object OpenVpnHelper {

    fun startVpn(context: Context, config: VpnServerConfig) {
        val profile = createProfile(context, config)
        if (profile != null) {
            // Using LaunchVPN Activity intent interface
            val intent = android.content.Intent(context, de.blinkt.openvpn.LaunchVPN::class.java)
            intent.putExtra(de.blinkt.openvpn.LaunchVPN.EXTRA_KEY, profile.uuid.toString())
            intent.action = android.content.Intent.ACTION_MAIN
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            throw Exception("Failed to parse OpenVPN Configuration")
        }
    }

    private fun createProfile(context: Context, config: VpnServerConfig): VpnProfile? {
        val ovpnData = config.config["ovpn_data"] ?: return null
        
        try {
            val cp = ConfigParser()
            cp.parseConfig(StringReader(ovpnData))
            val profile = cp.convertProfile()
            
            // Set Name/ID (VpnProfile fields access check)
            profile.mName = config.name

            // Set Username and Password if available
            if (!config.username.isNullOrEmpty()) {
                profile.mUsername = config.username
            }
            if (!config.password.isNullOrEmpty()) {
                profile.mPassword = config.password
            }

            // Ensure Auth Type includes User/Pass if credentials are provided
            if (!config.username.isNullOrEmpty() || !config.password.isNullOrEmpty()) {
                when (profile.mAuthenticationType) {
                    VpnProfile.TYPE_CERTIFICATES -> profile.mAuthenticationType = VpnProfile.TYPE_USERPASS_CERTIFICATES
                    VpnProfile.TYPE_PKCS12 -> profile.mAuthenticationType = VpnProfile.TYPE_USERPASS_PKCS12
                    VpnProfile.TYPE_KEYSTORE -> profile.mAuthenticationType = VpnProfile.TYPE_USERPASS_KEYSTORE
                    VpnProfile.TYPE_STATICKEYS -> profile.mAuthenticationType = VpnProfile.TYPE_USERPASS // Fallback or mixed?
                    else -> if (profile.mAuthenticationType != VpnProfile.TYPE_USERPASS_CERTIFICATES 
                                && profile.mAuthenticationType != VpnProfile.TYPE_USERPASS_PKCS12
                                && profile.mAuthenticationType != VpnProfile.TYPE_USERPASS_KEYSTORE) {
                        profile.mAuthenticationType = VpnProfile.TYPE_USERPASS
                    }
                }
            }
            
            // ProfileManager usage
            val pm = ProfileManager.getInstance(context) // Warning: deprecated/singleton access might differ in recent versions
            pm.addProfile(profile)
            ProfileManager.saveProfile(context, profile)
            pm.saveProfileList(context)
            
            return profile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}