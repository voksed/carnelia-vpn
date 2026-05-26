#pragma once
#include <QObject>
#include <QVector>
#include <QString>
#include <QSettings>
#include "ServerProfile.h"

class PrefsManager : public QObject
{
    Q_OBJECT
public:
    explicit PrefsManager(QObject* parent = nullptr);

    // Servers
    QVector<ServerProfile> loadServers() const;
    void saveServers(const QVector<ServerProfile>& servers);

    // Selected server index
    int  selectedServerIndex() const;
    void setSelectedServerIndex(int index);

    // Settings
    int  socksPort()     const;
    void setSocksPort(int port);

    int  httpPort()      const;
    void setHttpPort(int port);

    bool bypassRussia()  const;
    void setBypassRussia(bool value);

    bool isMuxEnabled() const;
    void setMuxEnabled(bool enabled);

    int  muxConcurrency() const;
    void setMuxConcurrency(int value);

    bool isFragmentationEnabled() const;
    void setFragmentationEnabled(bool enabled);

    QString fragmentationMode() const;
    void setFragmentationMode(const QString& mode);

    bool startWithOs()   const;
    void setStartWithOs(bool value);

    QString logLevel()   const;
    void setLogLevel(const QString& level);

private:
    QSettings m_s;
};
