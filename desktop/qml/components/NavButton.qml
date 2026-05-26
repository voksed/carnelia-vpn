import QtQuick
import QtQuick.Layouts

// Sidebar navigation button
Item {
    id: root

    property string iconText: "○"
    property string label: ""
    property bool selected: false

    signal clicked()

    width: 76
    height: 64

    Rectangle {
        anchors.fill: parent
        color: root.selected ? "#ffffff12" : "transparent"
        radius: 0

        // Left accent bar
        Rectangle {
            visible: root.selected
            width: 3; height: 32
            anchors { left: parent.left; verticalCenter: parent.verticalCenter }
            radius: 2
            color: "#d63031"
        }

        ColumnLayout {
            anchors.centerIn: parent
            spacing: 4

            Text {
                Layout.alignment: Qt.AlignHCenter
                text: root.iconText
                font.pixelSize: 20
                color: root.selected ? "#d63031" : "#8888aa"
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                text: root.label
                font.pixelSize: 9
                color: root.selected ? "#e8e8f4" : "#8888aa"
            }
        }

        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: root.clicked()
        }

        // Hover glow
        Rectangle {
            anchors.fill: parent
            color: "#ffffff08"
            visible: hoverArea.containsMouse && !root.selected
            HoverHandler { id: hoverArea }
        }
    }
}
