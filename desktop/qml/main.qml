import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ApplicationWindow {
    id: root
    visible: true
    width: 920
    height: 600
    minimumWidth: 720
    minimumHeight: 520
    title: "CarneliaVPN"
    color: "#0e0e16"

    // ── Global color palette ───────────────────────────────────────────────
    readonly property color bgColor:       "#0e0e16"
    readonly property color surfaceColor:  "#181825"
    readonly property color surface2Color: "#22223a"
    readonly property color primaryColor:  "#d63031"
    readonly property color textColor:     "#e8e8f4"
    readonly property color textSecColor:  "#8888aa"
    readonly property color successColor:  "#00b894"
    readonly property color borderColor:   "#ffffff14"

    // ── Error toast ────────────────────────────────────────────────────────
    Connections {
        target: appCtrl
        function onErrorOccurred(message) {
            toast.show(message)
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ── Sidebar ──────────────────────────────────────────────────────
        Rectangle {
            width: 76
            Layout.fillHeight: true
            color: "#0a0a12"

            ColumnLayout {
                anchors.fill: parent
                spacing: 0

                // Logo
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: 44; height: 44
                    radius: 12
                    color: root.primaryColor
                    Layout.topMargin: 18
                    Layout.bottomMargin: 14
                    Layout.leftMargin: 16

                    Text {
                        anchors.centerIn: parent
                        text: "C"
                        font.pixelSize: 22
                        font.bold: true
                        color: "white"
                    }
                }

                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: 40; height: 1
                    color: root.borderColor
                    Layout.bottomMargin: 8
                }

                NavButton {
                    iconText: "⊙"
                    label: "Главная"
                    selected: stack.currentIndex === 0
                    onClicked: stack.currentIndex = 0
                }
                NavButton {
                    iconText: "☰"
                    label: "Серверы"
                    selected: stack.currentIndex === 1
                    onClicked: stack.currentIndex = 1
                }
                NavButton {
                    iconText: "⚙"
                    label: "Настройки"
                    selected: stack.currentIndex === 2
                    onClicked: stack.currentIndex = 2
                }

                Item { Layout.fillHeight: true }

                // Connection indicator dot
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: 8; height: 8
                    radius: 4
                    color: appCtrl.connected ? root.successColor : "#555566"
                    Layout.bottomMargin: 6

                    SequentialAnimation on opacity {
                        running: appCtrl.connectionState === 1
                        loops: Animation.Infinite
                        NumberAnimation { to: 0.2; duration: 600 }
                        NumberAnimation { to: 1.0; duration: 600 }
                    }
                }

                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: "v2.4"
                    font.pixelSize: 10
                    color: "#55556a"
                    Layout.bottomMargin: 14
                }
            }
        }

        // ── Content area ─────────────────────────────────────────────────
        StackLayout {
            id: stack
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: 0

            HomePage {}
            ServersPage {}
            SettingsPage {}
        }
    }

    // ── Toast notification ────────────────────────────────────────────────
    Rectangle {
        id: toast
        anchors { bottom: parent.bottom; horizontalCenter: parent.horizontalCenter; bottomMargin: 32 }
        width: toastText.implicitWidth + 40
        height: 42
        radius: 10
        color: "#c0392b"
        opacity: 0
        visible: opacity > 0

        Text {
            id: toastText
            anchors.centerIn: parent
            color: "white"
            font.pixelSize: 13
        }

        function show(msg) {
            toastText.text = msg
            toast.opacity = 1
            hideTimer.restart()
        }

        Timer { id: hideTimer; interval: 3500; onTriggered: toast.opacity = 0 }
        Behavior on opacity { NumberAnimation { duration: 280 } }
    }
}
