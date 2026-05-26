import QtQuick
import QtQuick.Layouts

// Big animated connect/disconnect button
Item {
    id: root

    property int connectionState: 0   // 0=Disconnected 1=Connecting 2=Connected 3=Disconnecting
    property bool interactive: connectionState === 0 || connectionState === 2

    signal connectClicked()
    signal disconnectClicked()

    width: 160; height: 160

    // Outer pulse ring (visible only when connecting)
    Rectangle {
        id: pulseRing
        anchors.centerIn: parent
        width: root.width; height: root.height
        radius: width / 2
        color: "transparent"
        border.color: "#d6303150"
        border.width: 2
        visible: root.connectionState === 1 || root.connectionState === 3
        opacity: 0

        SequentialAnimation on opacity {
            running: pulseRing.visible
            loops: Animation.Infinite
            NumberAnimation { to: 0.9; duration: 700; easing.type: Easing.InOutQuad }
            NumberAnimation { to: 0.0; duration: 700; easing.type: Easing.InOutQuad }
        }
        SequentialAnimation on scale {
            running: pulseRing.visible
            loops: Animation.Infinite
            NumberAnimation { to: 1.25; duration: 1400 }
            NumberAnimation { to: 1.00; duration: 0 }
        }
    }

    // Main circle
    Rectangle {
        id: circle
        anchors.centerIn: parent
        width: 132; height: 132
        radius: 66
        color: {
            switch (root.connectionState) {
            case 2: return "#1e4d3a"
            case 1:
            case 3: return "#2a2a40"
            default: return "#1e1e2e"
            }
        }
        border.color: {
            switch (root.connectionState) {
            case 2: return "#00b894"
            case 1:
            case 3: return "#d6303170"
            default: return "#d6303180"
            }
        }
        border.width: 2

        Behavior on color       { ColorAnimation { duration: 400 } }
        Behavior on border.color { ColorAnimation { duration: 400 } }

        // Power icon (⏻)
        Text {
            anchors.centerIn: parent
            text: root.connectionState === 2 ? "⏻" : "⏻"
            font.pixelSize: 44
            color: {
                switch (root.connectionState) {
                case 2: return "#00b894"
                case 1:
                case 3: return "#8888aa"
                default: return "#d63031"
                }
            }
            Behavior on color { ColorAnimation { duration: 400 } }
        }

        MouseArea {
            anchors.fill: parent
            enabled: root.interactive
            cursorShape: Qt.PointingHandCursor
            onPressed:  circle.scale = 0.94
            onReleased: circle.scale = 1.0
            onClicked: {
                if (root.connectionState === 0) root.connectClicked()
                else if (root.connectionState === 2) root.disconnectClicked()
            }
            Behavior on scale { NumberAnimation { duration: 120 } }
        }
    }
}
