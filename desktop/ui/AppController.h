#pragma once
#include <QObject>
#include <QVector>
#include <QDateTime>
#include <QString>
#include "ServerProfile.h"

class XrayManager;
class TrafficMonitor;
class PrefsManager;

class AppController : public QObject
{
    Q_OBJECT

    Q_PROPERTY(bool        connected           READ connected            NOTIFY connectionStateChanged)
    Q_PROPERTY(int         connectionState     READ connectionState      NOTIFY connectionStateChanged)
    Q_PROPERTY(QString     statusText          READ statusText           NOTIFY connectionStateChanged)
    Q_PROPERTY(int         selectedServerIndex READ selectedServerIndex  WRITE setSelectedServerIndex  NOTIFY selectedServerIndexChanged)
    Q_PROPERTY(QStringList serverNames         READ serverNames          NOTIFY serversChanged)
    Q_PROPERTY(double      uploadSpeed         READ uploadSpeed          NOTIFY trafficUpdated)
    Q_PROPERTY(double      downloadSpeed       READ downloadSpeed        NOTIFY trafficUpdated)
    Q_PROPERTY(qint64      totalUploaded       READ totalUploaded        NOTIFY trafficUpdated)
    Q_PROPERTY(qint64      totalDownloaded     READ totalDownloaded      NOTIFY trafficUpdated)
    Q_PROPERTY(QString     connectedDuration   READ connectedDuration    NOTIFY trafficUpdated)
    Q_PROPERTY(int         socksPort           READ socksPort            NOTIFY settingsChanged)
    Q_PROPERTY(int         httpPort            READ httpPort             NOTIFY settingsChanged)
    Q_PROPERTY(bool        bypassRussia        READ bypassRussia         WRITE setBypassRussia         NOTIFY settingsChanged)
    Q_PROPERTY(bool        muxEnabled          READ muxEnabled           WRITE setMuxEnabled          NOTIFY settingsChanged)
    Q_PROPERTY(int         muxConcurrency      READ muxConcurrency       WRITE setMuxConcurrency      NOTIFY settingsChanged)
    Q_PROPERTY(bool        fragmentationEnabled READ fragmentationEnabled WRITE setFragmentationEnabled NOTIFY settingsChanged)
    Q_PROPERTY(QString     fragmentationMode   READ fragmentationMode    WRITE setFragmentationMode   NOTIFY settingsChanged)
    Q_PROPERTY(QString     logLevel            READ logLevel             WRITE setLogLevel             NOTIFY settingsChanged)

public:
    enum ConnectionState { Disconnected = 0, Connecting = 1, Connected = 2, Disconnecting = 3 };
    Q_ENUM(ConnectionState)

    explicit AppController(QObject* parent = nullptr);
    ~AppController();

    bool           connected()            const;
    int            connectionState()      const { return static_cast<int>(m_state); }
    QString        statusText()           const { return m_statusText; }
    int            selectedServerIndex()  const { return m_selectedIndex; }
    QStringList    serverNames()          const;
    double         uploadSpeed()          const { return m_uploadSpeed; }
    double         downloadSpeed()        const { return m_downloadSpeed; }
    qint64         totalUploaded()        const { return m_totalUploaded; }
    qint64         totalDownloaded()      const { return m_totalDownloaded; }
    QString        connectedDuration()    const;
    int            socksPort()            const;
    int            httpPort()             const;
    bool           bypassRussia()         const;
    bool           muxEnabled()           const;
    int            muxConcurrency()       const;
    bool           fragmentationEnabled() const;
    QString        fragmentationMode()    const;
    QString        logLevel()             const;

    void setSelectedServerIndex(int index);
    void setBypassRussia(bool value);
    void setMuxEnabled(bool enabled);
    void setMuxConcurrency(int value);
    void setFragmentationEnabled(bool enabled);
    void setFragmentationMode(const QString& mode);
    void setLogLevel(const QString& level);

public slots:
    void connectVpn();
    void disconnectVpn();

    // Returns true on success, false if URL is invalid.
    bool addServer(const QString& url);
    void removeServer(int index);

    // Returns a multi-line info string for display in dialog.
    Q_INVOKABLE QString serverInfo(int index) const;

    // Update port settings (triggers disconnect if connected).
    Q_INVOKABLE void setSocksPort(int port);
    Q_INVOKABLE void setHttpPort(int port);

signals:
    void connectionStateChanged();
    void selectedServerIndexChanged();
    void serversChanged();
    void trafficUpdated();
    void settingsChanged();
    void errorOccurred(const QString& message);

private slots:
    void onXrayStarted();
    void onXrayStopped(int exitCode);
    void onXrayError(const QString& error);
    void onTrafficUpdated(double up, double down, qint64 totalUp, qint64 totalDown);

private:
    ConnectionState m_state        = Disconnected;
    QString         m_statusText   = "Отключено";
    int             m_selectedIndex = -1;

    double   m_uploadSpeed    = 0;
    double   m_downloadSpeed  = 0;
    qint64   m_totalUploaded  = 0;
    qint64   m_totalDownloaded = 0;
    QDateTime m_connectedAt;

    QVector<ServerProfile> m_servers;

    XrayManager*   m_xray    = nullptr;
    TrafficMonitor* m_traffic = nullptr;
    PrefsManager*  m_prefs   = nullptr;

    void setState(ConnectionState state, const QString& text);
};
