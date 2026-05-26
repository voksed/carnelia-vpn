import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

// Main VPN connection page
Rectangle {
    color: "#0e0e16"

    // Timer to refresh connection duration display
    Timer {
        interval: 1000
        running: appCtrl.connected
        repeat: true
        onTriggered: durationLabel.text = appCtrl.connectedDuration
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        // ── Header ─────────────────────────────────────────────────────
        Rectangle {
            Layout.fillWidth: true
            height: 56
            color: "#181825"

            RowLayout {
                anchors { fill: parent; leftMargin: 24; rightMargin: 24 }
                Text {
                    text: "CarneliaVPN"
                    font.pixelSize: 18
                    font.bold: true
                    color: "#e8e8f4"
                }
                Item { Layout.fillWidth: true }
                Rectangle {
                    width: 10; height: 10; radius: 5
                    color: appCtrl.connected ? "#00b894" : "#555566"
                    Behavior on color { ColorAnimation { duration: 400 } }
                }
                Text {
                    text: appCtrl.statusText
                    font.pixelSize: 13
                    color: appCtrl.connected ? "#00b894" : "#8888aa"
                    leftPadding: 6
                    Behavior on color { ColorAnimation { duration: 400 } }
                }
            }
        }

        // ── Center content ─────────────────────────────────────────────
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true

            ColumnLayout {
                anchors.centerIn: parent
                spacing: 20

                // Selected server
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: serverLabel.implicitWidth + 32
                    height: 34
                    radius: 17
                    color: "#22223a"

                    Text {
                        id: serverLabel
                        anchors.centerIn: parent
                        text: appCtrl.selectedServerIndex >= 0
                            ? appCtrl.serverNames[appCtrl.selectedServerIndex]
                            : "Сервер не выбран"
                        font.pixelSize: 13
                        color: appCtrl.selectedServerIndex >= 0 ? "#e8e8f4" : "#8888aa"
                    }
                }

                // Big connect button
                ConnectButton {
                    Layout.alignment: Qt.AlignHCenter
                    connectionState: appCtrl.connectionState
                    onConnectClicked:    appCtrl.connectVpn()
                    onDisconnectClicked: appCtrl.disconnectVpn()
                }

                // Duration
                Text {
                    id: durationLabel
                    Layout.alignment: Qt.AlignHCenter
                    text: appCtrl.connected ? appCtrl.connectedDuration : "──"
                    font.pixelSize: 22
                    font.family: "monospace"
                    color: appCtrl.connected ? "#e8e8f4" : "#444455"
                    font.letterSpacing: 2
                }

                // Traffic widget (only visible when connected)
                TrafficWidget {
                    Layout.alignment: Qt.AlignHCenter
                    visible: appCtrl.connected
                    opacity: appCtrl.connected ? 1 : 0
                    Behavior on opacity { NumberAnimation { duration: 400 } }

                    uploadSpeed:   appCtrl.uploadSpeed
                    downloadSpeed: appCtrl.downloadSpeed
                    totalUp:       appCtrl.totalUploaded
                    totalDown:     appCtrl.totalDownloaded
                }
            }
        }

        // ── Proxy hint bar ─────────────────────────────────────────────
        Rectangle {
            Layout.fillWidth: true
            height: appCtrl.connected ? 38 : 0
            color: "#181825"
            clip: true
            Behavior on height { NumberAnimation { duration: 300 } }

            Text {
                anchors.centerIn: parent
                text: "Системный прокси активен · SOCKS5 127.0.0.1:" + appCtrl.socksPort
                      + "  ·  HTTP 127.0.0.1:" + appCtrl.httpPort
                font.pixelSize: 11
                color: "#8888aa"
            }
        }
    }
}
