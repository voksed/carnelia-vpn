import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

// Server list management page
Rectangle {
    color: "#0e0e16"

    // Server info dialog
    Dialog {
        id: infoDialog
        property string info: ""
        title: "Информация о сервере"
        anchors.centerIn: parent
        width: 360

        background: Rectangle { color: "#22223a"; radius: 12 }
        header: Rectangle {
            height: 48; color: "transparent"
            Text { anchors.centerIn: parent; text: infoDialog.title; font.pixelSize: 15; font.bold: true; color: "#e8e8f4" }
        }

        Text {
            text: infoDialog.info
            color: "#c0c0d8"
            font.pixelSize: 13
            lineHeight: 1.6
            wrapMode: Text.WordWrap
            width: 320
        }
        standardButtons: Dialog.Close
    }

    // Add server dialog
    Dialog {
        id: addDialog
        title: "Добавить сервер"
        anchors.centerIn: parent
        width: 440
        modal: true

        background: Rectangle { color: "#181825"; border.color: "#ffffff14"; border.width: 1; radius: 12 }
        header: Rectangle {
            height: 50; color: "transparent"
            Text { anchors { left: parent.left; leftMargin: 20; verticalCenter: parent.verticalCenter }
                   text: "Добавить сервер"; font.pixelSize: 16; font.bold: true; color: "#e8e8f4" }
        }

        ColumnLayout {
            width: 400
            spacing: 12

            Text {
                text: "Вставьте ссылку (vless://, vmess://, ss://, trojan://)"
                font.pixelSize: 12
                color: "#8888aa"
                wrapMode: Text.WordWrap
                Layout.fillWidth: true
            }

            Rectangle {
                Layout.fillWidth: true
                height: 42
                radius: 8
                color: "#22223a"
                border.color: urlField.activeFocus ? "#d63031" : "#ffffff18"
                border.width: 1

                TextField {
                    id: urlField
                    anchors { fill: parent; leftMargin: 12; rightMargin: 12 }
                    background: Item {}
                    color: "#e8e8f4"
                    placeholderText: "vless://uuid@host:port?..."
                    placeholderTextColor: "#55556a"
                    font.pixelSize: 12
                    selectByMouse: true
                    onAccepted: addBtn.clicked()
                }
            }

            Text {
                id: errorLabel
                text: "Неверный формат ссылки"
                color: "#d63031"
                font.pixelSize: 12
                visible: false
            }

            RowLayout {
                Layout.topMargin: 4
                Item { Layout.fillWidth: true }

                Rectangle {
                    width: 90; height: 36; radius: 8
                    color: "#22223a"
                    border.color: "#ffffff18"
                    Text { anchors.centerIn: parent; text: "Отмена"; color: "#8888aa"; font.pixelSize: 13 }
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: { errorLabel.visible = false; urlField.clear(); addDialog.close() }
                    }
                }

                Rectangle {
                    id: addBtn
                    width: 100; height: 36; radius: 8
                    color: "#d63031"
                    Text { anchors.centerIn: parent; text: "Добавить"; color: "white"; font.pixelSize: 13 }
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            if (appCtrl.addServer(urlField.text)) {
                                errorLabel.visible = false
                                urlField.clear()
                                addDialog.close()
                            } else {
                                errorLabel.visible = true
                            }
                        }
                    }
                }
            }
        }
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
                anchors { fill: parent; leftMargin: 24; rightMargin: 20 }
                Text { text: "Серверы"; font.pixelSize: 18; font.bold: true; color: "#e8e8f4" }
                Item { Layout.fillWidth: true }
                Rectangle {
                    width: 110; height: 34; radius: 8
                    color: "#d63031"
                    Text { anchors.centerIn: parent; text: "+ Добавить"; color: "white"; font.pixelSize: 13 }
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: { urlField.clear(); addDialog.open() }
                    }
                }
            }
        }

        // ── List ───────────────────────────────────────────────────────
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true

            // Empty state
            Text {
                anchors.centerIn: parent
                visible: appCtrl.serverNames.length === 0
                text: "Нет серверов\nНажмите + Добавить"
                horizontalAlignment: Text.AlignHCenter
                font.pixelSize: 15
                color: "#444455"
                lineHeight: 1.7
            }

            ListView {
                anchors { fill: parent; topMargin: 12; bottomMargin: 12 }
                model: appCtrl.serverNames
                spacing: 6
                clip: true

                ScrollBar.vertical: ScrollBar {}

                delegate: ServerCard {
                    serverName: modelData
                    selected: appCtrl.selectedServerIndex === index
                    isActive: appCtrl.connected && appCtrl.selectedServerIndex === index

                    onSelectClicked: appCtrl.selectedServerIndex = index
                    onRemoveClicked: appCtrl.removeServer(index)
                    onInfoClicked: {
                        infoDialog.info = appCtrl.serverInfo(index)
                        infoDialog.open()
                    }
                }
            }
        }
    }
}
