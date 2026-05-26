import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

// Settings page
Rectangle {
    color: "#0e0e16"

    // ── Helpers ────────────────────────────────────────────────────────────
    component SectionLabel: Text {
        font.pixelSize: 11
        font.bold: true
        color: "#d63031"
        topPadding: 8
        bottomPadding: 4
        leftPadding: 4
        text: ""
    }

    component SettingRow: Rectangle {
        property alias label: lbl.text
        property alias sublabel: sub.text
        default property alias children2: content.data

        Layout.fillWidth: true
        height: 56
        color: "#181825"
        radius: 10

        RowLayout {
            anchors { fill: parent; leftMargin: 16; rightMargin: 16 }
            spacing: 12

            ColumnLayout {
                spacing: 2
                Text { id: lbl; font.pixelSize: 13; color: "#e8e8f4" }
                Text { id: sub; font.pixelSize: 11; color: "#8888aa"; visible: text !== "" }
            }
            Item { Layout.fillWidth: true }
            Item { id: content }
        }
    }

    component ToggleSwitch: Rectangle {
        property bool checked: false
        signal toggled()
        width: 46; height: 24; radius: 12
        color: checked ? "#d63031" : "#333348"
        Behavior on color { ColorAnimation { duration: 200 } }
        Rectangle {
            x: parent.checked ? 24 : 2
            anchors.verticalCenter: parent.verticalCenter
            width: 20; height: 20; radius: 10
            color: "white"
            Behavior on x { NumberAnimation { duration: 200 } }
        }
        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: parent.toggled() }
    }

    component PortField: Rectangle {
        property alias value: tf.text
        signal editingFinished()
        width: 80; height: 32; radius: 6
        color: "#22223a"
        border.color: tf.activeFocus ? "#d63031" : "#ffffff20"
        border.width: 1
        TextField {
            id: tf
            anchors { fill: parent; leftMargin: 8; rightMargin: 8 }
            background: Item {}
            color: "#e8e8f4"
            font.pixelSize: 13
            inputMethodHints: Qt.ImhDigitsOnly
            validator: IntValidator { bottom: 1024; top: 65535 }
            selectByMouse: true
            onEditingFinished: parent.editingFinished()
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────────
    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.fillWidth: true
            height: 56
            color: "#181825"
            Text { anchors { left: parent.left; leftMargin: 24; verticalCenter: parent.verticalCenter }
                   text: "Настройки"; font.pixelSize: 18; font.bold: true; color: "#e8e8f4" }
        }

        Flickable {
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentHeight: col.implicitHeight + 32
            clip: true
            ScrollBar.vertical: ScrollBar {}

            ColumnLayout {
                id: col
                width: parent.width
                anchors { left: parent.left; right: parent.right; margins: 20 }
                spacing: 6

                SectionLabel { text: "ПРОКСИ" }

                // SOCKS port
                SettingRow {
                    label: "SOCKS5 порт"
                    sublabel: "Порт локального SOCKS5 прокси"
                    PortField {
                        value: appCtrl.socksPort.toString()
                        onEditingFinished: appCtrl.setSocksPort(parseInt(value))
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                // HTTP port
                SettingRow {
                    label: "HTTP порт"
                    sublabel: "Порт локального HTTP прокси"
                    PortField {
                        value: appCtrl.httpPort.toString()
                        onEditingFinished: appCtrl.setHttpPort(parseInt(value))
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                SectionLabel { text: "МАРШРУТИЗАЦИЯ" }

                // Bypass Russia
                SettingRow {
                    label: "Обход российских сайтов"
                    sublabel: "geoip:ru + geosite:ru идут напрямую"
                    ToggleSwitch {
                        checked: appCtrl.bypassRussia
                        onToggled: appCtrl.bypassRussia = !appCtrl.bypassRussia
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                // MUX
                SettingRow {
                    label: "MUX"
                    sublabel: "Сокращает количество TCP-соединений"
                    ToggleSwitch {
                        checked: appCtrl.muxEnabled
                        onToggled: appCtrl.muxEnabled = !appCtrl.muxEnabled
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                SettingRow {
                    label: "Конкурентность MUX"
                    sublabel: "Количество потоков MUX"
                    PortField {
                        value: appCtrl.muxConcurrency.toString()
                        onEditingFinished: appCtrl.setMuxConcurrency(parseInt(value))
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                // Fragmentation
                SettingRow {
                    label: "Фрагментация"
                    sublabel: "Делает пакеты меньше для обхода фильтров"
                    ToggleSwitch {
                        checked: appCtrl.fragmentationEnabled
                        onToggled: appCtrl.fragmentationEnabled = !appCtrl.fragmentationEnabled
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                    }
                }

                SettingRow {
                    label: "Режим фрагментации"
                    sublabel: "light / balanced / aggressive"
                    ComboBox {
                        model: ["light", "balanced", "aggressive"]
                        currentIndex: {
                            let mode = appCtrl.fragmentationMode
                            let idx = model.indexOf(mode)
                            return idx >= 0 ? idx : 1
                        }
                        onActivated: appCtrl.fragmentationMode = currentText
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                        background: Rectangle { color: "#22223a"; radius: 6; border.color: "#ffffff18" }
                        contentItem: Text { text: parent.currentText; color: "#e8e8f4"; leftPadding: 10; font.pixelSize: 13; verticalAlignment: Text.AlignVCenter }
                    }
                }

                SectionLabel { text: "XRAY" }

                // Log level
                SettingRow {
                    label: "Уровень логов"
                    sublabel: ""
                    ComboBox {
                        model: ["debug", "info", "warning", "error", "none"]
                        currentIndex: {
                            let lvl = appCtrl.logLevel
                            let idx = model.indexOf(lvl)
                            return idx >= 0 ? idx : 2
                        }
                        onActivated: appCtrl.logLevel = currentText
                        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
                        background: Rectangle { color: "#22223a"; radius: 6; border.color: "#ffffff18" }
                        contentItem: Text { text: parent.currentText; color: "#e8e8f4"; leftPadding: 10; font.pixelSize: 13; verticalAlignment: Text.AlignVCenter }
                    }
                }

                SectionLabel { text: "О ПРОГРАММЕ" }

                Rectangle {
                    Layout.fillWidth: true
                    height: 70
                    color: "#181825"
                    radius: 10
                    ColumnLayout {
                        anchors { left: parent.left; leftMargin: 16; verticalCenter: parent.verticalCenter }
                        spacing: 4
                        Text { text: "CarneliaVPN Desktop"; font.pixelSize: 14; font.bold: true; color: "#e8e8f4" }
                        Text { text: "Версия 2.4.0 · Xray-core · Qt6"; font.pixelSize: 12; color: "#8888aa" }
                    }
                }

                Item { height: 20 }
            }
        }
    }
}
