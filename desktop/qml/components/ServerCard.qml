import QtQuick
import QtQuick.Layouts

// Server list item card
Item {
    id: root

    property string serverName: ""
    property bool selected: false
    property bool isActive: false   // currently in use for VPN

    signal selectClicked()
    signal removeClicked()
    signal infoClicked()

    width: parent ? parent.width : 300
    height: 58

    Rectangle {
        anchors { fill: parent; leftMargin: 16; rightMargin: 16 }
        radius: 10
        color: root.selected ? "#2a1e3a" : "#181825"
        border.color: root.selected ? "#d6303180" : "#ffffff10"
        border.width: 1

        RowLayout {
            anchors { fill: parent; leftMargin: 14; rightMargin: 10; topMargin: 0; bottomMargin: 0 }
            spacing: 10

            // Active indicator dot
            Rectangle {
                width: 8; height: 8
                radius: 4
                color: root.isActive ? "#00b894" : (root.selected ? "#d6303180" : "#444455")
            }

            // Server name
            Text {
                Layout.fillWidth: true
                text: root.serverName
                font.pixelSize: 13
                color: root.selected ? "#e8e8f4" : "#aaaacc"
                elide: Text.ElideRight
            }

            // Info button
            Rectangle {
                width: 28; height: 28; radius: 6
                color: infoHover.containsMouse ? "#ffffff18" : "transparent"
                Text { anchors.centerIn: parent; text: "ℹ"; font.pixelSize: 14; color: "#8888aa" }
                HoverHandler { id: infoHover }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.infoClicked() }
            }

            // Remove button
            Rectangle {
                width: 28; height: 28; radius: 6
                color: delHover.containsMouse ? "#3a1a1a" : "transparent"
                Text { anchors.centerIn: parent; text: "✕"; font.pixelSize: 12; color: "#cc4444" }
                HoverHandler { id: delHover }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.removeClicked() }
            }
        }

        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: root.selectClicked()
        }
    }
}
