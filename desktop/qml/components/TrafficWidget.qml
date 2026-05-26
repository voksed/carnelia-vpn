import QtQuick
import QtQuick.Layouts

// Traffic speed + totals display widget
Item {
    id: root

    property double uploadSpeed:   0  // bytes/s
    property double downloadSpeed: 0  // bytes/s
    property qint64 totalUp:       0  // bytes
    property qint64 totalDown:     0  // bytes

    function formatSpeed(bps) {
        if (bps < 1024)        return bps.toFixed(0) + " B/s"
        if (bps < 1048576)     return (bps / 1024).toFixed(1) + " KB/s"
        return (bps / 1048576).toFixed(2) + " MB/s"
    }
    function formatBytes(b) {
        if (b < 1024)       return b + " B"
        if (b < 1048576)    return (b / 1024).toFixed(1) + " KB"
        if (b < 1073741824) return (b / 1048576).toFixed(2) + " MB"
        return (b / 1073741824).toFixed(2) + " GB"
    }

    width: 340; height: 64

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // Upload
        Rectangle {
            Layout.fillWidth: true
            height: 56
            color: "#ffffff08"
            radius: 10

            ColumnLayout {
                anchors.centerIn: parent
                spacing: 2
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: "↑  " + root.formatSpeed(root.uploadSpeed)
                    font.pixelSize: 15
                    font.bold: true
                    color: "#e8e8f4"
                }
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: root.formatBytes(root.totalUp)
                    font.pixelSize: 11
                    color: "#8888aa"
                }
            }
        }

        Item { width: 12 }

        // Download
        Rectangle {
            Layout.fillWidth: true
            height: 56
            color: "#ffffff08"
            radius: 10

            ColumnLayout {
                anchors.centerIn: parent
                spacing: 2
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: "↓  " + root.formatSpeed(root.downloadSpeed)
                    font.pixelSize: 15
                    font.bold: true
                    color: "#e8e8f4"
                }
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: root.formatBytes(root.totalDown)
                    font.pixelSize: 11
                    color: "#8888aa"
                }
            }
        }
    }
}
