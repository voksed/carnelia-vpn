#include "AppController.h"
#include "XrayManager.h"
#include "TrafficMonitor.h"
#include "PrefsManager.h"
#include "SystemProxy.h"
#include "ConfigBuilder.h"

#include <QDateTime>
#include <QTimer>

AppController::AppController(QObject* parent)
    : QObject(parent)
    , m_xray(new XrayManager(this))
    , m_traffic(new TrafficMonitor(this))
    , m_prefs(new PrefsManager(this))
{
    // Restore saved state
    m_servers       = m_prefs->loadServers();
    m_selectedIndex = m_prefs->selectedServerIndex();
    if (m_selectedIndex >= m_servers.size()) m_selectedIndex = -1;

    connect(m_xray, &XrayManager::started,        this, &AppController::onXrayStarted);
    connect(m_xray, &XrayManager::stopped,        this, &AppController::onXrayStopped);
    connect(m_xray, &XrayManager::errorOccurred,  this, &AppController::onXrayError);
    connect(m_traffic, &TrafficMonitor::updated,  this, &AppController::onTrafficUpdated);
}

AppController::~AppController()
{
    SystemProxy::disable();
    m_xray->stop();
}

bool AppController::connected() const
{
    return m_state == Connected;
}

QStringList AppController::serverNames() const
{
    QStringList names;
    names.reserve(m_servers.size());
    for (const auto& s : m_servers)
        names << QString::fromStdString(s.displayName());
    return names;
}

QString AppController::connectedDuration() const
{
    if (m_state != Connected || !m_connectedAt.isValid()) return "00:00:00";
    qint64 secs = m_connectedAt.secsTo(QDateTime::currentDateTime());
    int h = static_cast<int>(secs / 3600);
    int m = static_cast<int>((secs % 3600) / 60);
    int s = static_cast<int>(secs % 60);
    return QString("%1:%2:%3")
        .arg(h, 2, 10, QChar('0'))
        .arg(m, 2, 10, QChar('0'))
        .arg(s, 2, 10, QChar('0'));
}

int AppController::socksPort() const { return m_prefs->socksPort(); }
int AppController::httpPort()  const { return m_prefs->httpPort();  }
bool AppController::bypassRussia() const { return m_prefs->bypassRussia(); }
QString AppController::logLevel()  const { return m_prefs->logLevel(); }

void AppController::setSelectedServerIndex(int index)
{
    if (m_selectedIndex == index) return;
    m_selectedIndex = index;
    m_prefs->setSelectedServerIndex(index);
    emit selectedServerIndexChanged();
}

void AppController::setBypassRussia(bool value)
{
    m_prefs->setBypassRussia(value);
    emit settingsChanged();
}

bool AppController::muxEnabled() const
{
    return m_prefs->isMuxEnabled();
}

void AppController::setMuxEnabled(bool enabled)
{
    m_prefs->setMuxEnabled(enabled);
    emit settingsChanged();
}

int AppController::muxConcurrency() const
{
    return m_prefs->muxConcurrency();
}

void AppController::setMuxConcurrency(int value)
{
    m_prefs->setMuxConcurrency(value);
    emit settingsChanged();
}

bool AppController::fragmentationEnabled() const
{
    return m_prefs->isFragmentationEnabled();
}

void AppController::setFragmentationEnabled(bool enabled)
{
    m_prefs->setFragmentationEnabled(enabled);
    emit settingsChanged();
}

QString AppController::fragmentationMode() const
{
    return m_prefs->fragmentationMode();
}

void AppController::setFragmentationMode(const QString& mode)
{
    m_prefs->setFragmentationMode(mode);
    emit settingsChanged();
}

void AppController::setLogLevel(const QString& level)
{
    m_prefs->setLogLevel(level);
    emit settingsChanged();
}

void AppController::setSocksPort(int port)
{
    m_prefs->setSocksPort(port);
    emit settingsChanged();
}

void AppController::setHttpPort(int port)
{
    m_prefs->setHttpPort(port);
    emit settingsChanged();
}

void AppController::connectVpn()
{
    if (m_state == Connecting || m_state == Connected) return;
    if (m_selectedIndex < 0 || m_selectedIndex >= m_servers.size()) {
        emit errorOccurred("Сервер не выбран");
        return;
    }

    setState(Connecting, "Подключение...");

    const ServerProfile& srv = m_servers.at(m_selectedIndex);
    QString cfg = QString::fromStdString(ConfigBuilder::build(
        srv,
        m_prefs->socksPort(),
        m_prefs->httpPort(),
        m_prefs->bypassRussia(),
        m_prefs->logLevel().toStdString(),
        m_prefs->isMuxEnabled(),
        m_prefs->muxConcurrency(),
        m_prefs->isFragmentationEnabled(),
        m_prefs->fragmentationMode().toStdString()
    ));

    if (!m_xray->start(cfg)) {
        setState(Disconnected, "Ошибка запуска");
        emit errorOccurred(m_xray->lastError());
    }
}

void AppController::disconnectVpn()
{
    if (m_state == Disconnected || m_state == Disconnecting) return;
    setState(Disconnecting, "Отключение...");
    m_traffic->stop();
    SystemProxy::disable();
    m_xray->stop();
}

bool AppController::addServer(const QString& url)
{
    ServerProfile p = ServerProfile::fromUrl(url.trimmed().toStdString());
    if (!p.isValid()) return false;
    m_servers.append(p);
    m_prefs->saveServers(m_servers);
    if (m_selectedIndex < 0) {
        m_selectedIndex = 0;
        m_prefs->setSelectedServerIndex(0);
        emit selectedServerIndexChanged();
    }
    emit serversChanged();
    return true;
}

void AppController::removeServer(int index)
{
    if (index < 0 || index >= m_servers.size()) return;
    m_servers.removeAt(index);
    if (m_selectedIndex >= m_servers.size())
        m_selectedIndex = m_servers.isEmpty() ? -1 : m_servers.size() - 1;
    m_prefs->saveServers(m_servers);
    m_prefs->setSelectedServerIndex(m_selectedIndex);
    emit serversChanged();
    emit selectedServerIndexChanged();
}

QString AppController::serverInfo(int index) const
{
    if (index < 0 || index >= m_servers.size()) return {};
    const ServerProfile& p = m_servers.at(index);
    return QString("Протокол: %1\nАдрес: %2:%3\nТранспорт: %4\nБезопасность: %5")
        .arg(p.protocolName())
        .arg(p.address)
        .arg(p.port)
        .arg(p.network)
        .arg(p.security);
}

// ── Slots ──────────────────────────────────────────────────────────────────

void AppController::onXrayStarted()
{
    // Give xray ~500ms to bind ports, then set proxy
    QTimer::singleShot(600, this, [this]{
        SystemProxy::enable(m_prefs->socksPort(), m_prefs->httpPort());
        m_traffic->reset();
        m_traffic->start();
        m_connectedAt = QDateTime::currentDateTime();
        setState(Connected, "Подключено");
    });
}

void AppController::onXrayStopped(int exitCode)
{
    Q_UNUSED(exitCode)
    m_traffic->stop();
    SystemProxy::disable();
    setState(Disconnected, "Отключено");
}

void AppController::onXrayError(const QString& error)
{
    m_traffic->stop();
    SystemProxy::disable();
    setState(Disconnected, "Ошибка");
    emit errorOccurred(error);
}

void AppController::onTrafficUpdated(double up, double down, qint64 totalUp, qint64 totalDown)
{
    m_uploadSpeed     = up;
    m_downloadSpeed   = down;
    m_totalUploaded   = totalUp;
    m_totalDownloaded = totalDown;
    emit trafficUpdated();
}

void AppController::setState(ConnectionState state, const QString& text)
{
    m_state      = state;
    m_statusText = text;
    emit connectionStateChanged();
}
