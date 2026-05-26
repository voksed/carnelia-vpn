#include "PrefsManager.h"
#include <QJsonDocument>
#include <QJsonArray>
#include <QJsonObject>

PrefsManager::PrefsManager(QObject* parent)
    : QObject(parent)
    , m_s("Carnelia", "CarneliaVPN")
{}

QVector<ServerProfile> PrefsManager::loadServers() const
{
    QVector<ServerProfile> result;
    QByteArray raw = m_s.value("servers/data").toByteArray();
    if (raw.isEmpty()) return result;

    QJsonArray arr = QJsonDocument::fromJson(raw).array();
    for (const QJsonValue& v : arr) {
        QJsonObject o = v.toObject();
        ServerProfile p;
        p.name        = o["name"].toString().toStdString();
        p.address     = o["address"].toString().toStdString();
        p.port        = o["port"].toInt(443);
        p.protocol    = static_cast<Protocol>(o["protocol"].toInt());
        p.uuid        = o["uuid"].toString().toStdString();
        p.method      = o["method"].toString().toStdString();
        p.password    = o["password"].toString().toStdString();
        p.network     = o["network"].toString("tcp").toStdString();
        p.security    = o["security"].toString("none").toStdString();
        p.path        = o["path"].toString("/").toStdString();
        p.host        = o["host"].toString().toStdString();
        p.serverName  = o["serverName"].toString().toStdString();
        p.publicKey   = o["publicKey"].toString().toStdString();
        p.shortId     = o["shortId"].toString().toStdString();
        p.fingerprint = o["fingerprint"].toString().toStdString();
        p.flow        = o["flow"].toString().toStdString();
        result.append(p);
    }
    return result;
}

void PrefsManager::saveServers(const QVector<ServerProfile>& servers)
{
    QJsonArray arr;
    for (const ServerProfile& p : servers) {
        QJsonObject o;
        o["name"]        = QString::fromStdString(p.name);
        o["address"]     = QString::fromStdString(p.address);
        o["port"]        = p.port;
        o["protocol"]    = static_cast<int>(p.protocol);
        o["uuid"]        = QString::fromStdString(p.uuid);
        o["method"]      = QString::fromStdString(p.method);
        o["password"]    = QString::fromStdString(p.password);
        o["network"]     = QString::fromStdString(p.network);
        o["security"]    = QString::fromStdString(p.security);
        o["path"]        = QString::fromStdString(p.path);
        o["host"]        = QString::fromStdString(p.host);
        o["serverName"]  = QString::fromStdString(p.serverName);
        o["publicKey"]   = QString::fromStdString(p.publicKey);
        o["shortId"]     = QString::fromStdString(p.shortId);
        o["fingerprint"] = QString::fromStdString(p.fingerprint);
        o["flow"]        = QString::fromStdString(p.flow);
        arr.append(o);
    }
    m_s.setValue("servers/data", QJsonDocument(arr).toJson(QJsonDocument::Compact));
    m_s.sync();
}

int PrefsManager::selectedServerIndex() const
{ return m_s.value("servers/selected", -1).toInt(); }

void PrefsManager::setSelectedServerIndex(int index)
{ m_s.setValue("servers/selected", index); m_s.sync(); }

int PrefsManager::socksPort() const
{ return m_s.value("proxy/socksPort", 10808).toInt(); }

void PrefsManager::setSocksPort(int port)
{ m_s.setValue("proxy/socksPort", port); m_s.sync(); }

int PrefsManager::httpPort() const
{ return m_s.value("proxy/httpPort", 10809).toInt(); }

void PrefsManager::setHttpPort(int port)
{ m_s.setValue("proxy/httpPort", port); m_s.sync(); }

bool PrefsManager::bypassRussia() const
{ return m_s.value("routing/bypassRussia", false).toBool(); }

void PrefsManager::setBypassRussia(bool value)
{ m_s.setValue("routing/bypassRussia", value); m_s.sync(); }

bool PrefsManager::isMuxEnabled() const
{ return m_s.value("xray/muxEnabled", false).toBool(); }

void PrefsManager::setMuxEnabled(bool enabled)
{ m_s.setValue("xray/muxEnabled", enabled); m_s.sync(); }

int PrefsManager::muxConcurrency() const
{ return m_s.value("xray/muxConcurrency", 4).toInt(); }

void PrefsManager::setMuxConcurrency(int value)
{ m_s.setValue("xray/muxConcurrency", value); m_s.sync(); }

bool PrefsManager::isFragmentationEnabled() const
{ return m_s.value("xray/fragmentationEnabled", false).toBool(); }

void PrefsManager::setFragmentationEnabled(bool enabled)
{ m_s.setValue("xray/fragmentationEnabled", enabled); m_s.sync(); }

QString PrefsManager::fragmentationMode() const
{ return m_s.value("xray/fragmentationMode", "balanced").toString(); }

void PrefsManager::setFragmentationMode(const QString& mode)
{ m_s.setValue("xray/fragmentationMode", mode); m_s.sync(); }

bool PrefsManager::startWithOs() const
{ return m_s.value("system/startWithOs", false).toBool(); }

void PrefsManager::setStartWithOs(bool value)
{ m_s.setValue("system/startWithOs", value); m_s.sync(); }

QString PrefsManager::logLevel() const
{ return m_s.value("xray/logLevel", "warning").toString(); }

void PrefsManager::setLogLevel(const QString& level)
{ m_s.setValue("xray/logLevel", level); m_s.sync(); }
