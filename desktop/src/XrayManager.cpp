#include "XrayManager.h"
#include <QCoreApplication>
#include <QStandardPaths>
#include <QDir>
#include <QFile>
#include <QTextStream>
#include <QDateTime>

XrayManager::XrayManager(QObject* parent)
    : QObject(parent)
    , m_proc(new QProcess(this))
{
    connect(m_proc, &QProcess::started,                   this, &XrayManager::onProcessStarted);
    connect(m_proc, &QProcess::finished,                  this, &XrayManager::onProcessFinished);
    connect(m_proc, &QProcess::errorOccurred,             this, &XrayManager::onProcessError);
    connect(m_proc, &QProcess::readyReadStandardError,    this, &XrayManager::onReadStderr);
    connect(m_proc, &QProcess::readyReadStandardOutput,   this, &XrayManager::onReadStdout);
}

XrayManager::~XrayManager()
{
    stop();
}

bool XrayManager::start(const QString& configJson)
{
    if (m_proc->state() != QProcess::NotRunning) {
        m_proc->kill();
        m_proc->waitForFinished(3000);
    }

    // Write config to temp file
    QString cfgPath = configFilePath();
    QFile f(cfgPath);
    if (!f.open(QIODevice::WriteOnly | QIODevice::Text)) {
        m_lastError = "Cannot write xray config: " + cfgPath;
        return false;
    }
    QTextStream(&f) << configJson;
    f.close();

    QString binary = xrayBinaryPath();
    if (!QFile::exists(binary)) {
        m_lastError = "xray binary not found: " + binary;
        return false;
    }

    m_proc->start(binary, {"-config", cfgPath});
    return true;
}

void XrayManager::stop()
{
    if (m_proc->state() == QProcess::Running) {
        m_proc->terminate();
        if (!m_proc->waitForFinished(4000))
            m_proc->kill();
    }
}

bool XrayManager::isRunning() const
{
    return m_proc->state() == QProcess::Running;
}

void XrayManager::onProcessStarted()
{
    emit started();
}

void XrayManager::onProcessFinished(int exitCode, QProcess::ExitStatus)
{
    emit stopped(exitCode);
}

void XrayManager::onProcessError(QProcess::ProcessError err)
{
    switch (err) {
    case QProcess::FailedToStart:
        m_lastError = "Не удалось запустить xray. Проверьте наличие бинарника.";
        break;
    case QProcess::Crashed:
        m_lastError = "xray завершился с ошибкой.";
        break;
    default:
        m_lastError = "Ошибка процесса xray: " + QString::number(err);
        break;
    }
    emit errorOccurred(m_lastError);
}

void XrayManager::onReadStderr()
{
    QString line = QString::fromUtf8(m_proc->readAllStandardError()).trimmed();
    if (!line.isEmpty()) emit logLine("[xray] " + line);
    // Append to log file with timestamp
    QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir + "/logs");
    QString logPath = dir + "/logs/xray.log";
    QFile lf(logPath);
    if (lf.open(QIODevice::Append | QIODevice::Text)) {
        QTextStream ts(&lf);
        ts << QDateTime::currentDateTime().toString(Qt::ISODate) << " [stderr] " << line << "\n";
        lf.close();
    }
}

void XrayManager::onReadStdout()
{
    QString line = QString::fromUtf8(m_proc->readAllStandardOutput()).trimmed();
    if (!line.isEmpty()) emit logLine("[xray] " + line);
    // Append to log file with timestamp
    QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir + "/logs");
    QString logPath = dir + "/logs/xray.log";
    QFile lf(logPath);
    if (lf.open(QIODevice::Append | QIODevice::Text)) {
        QTextStream ts(&lf);
        ts << QDateTime::currentDateTime().toString(Qt::ISODate) << " [stdout] " << line << "\n";
        lf.close();
    }
}

QString XrayManager::xrayBinaryPath() const
{
    QString dir = QCoreApplication::applicationDirPath();
#ifdef Q_OS_WIN
    return dir + "/xray.exe";
#else
    return dir + "/xray";
#endif
}

QString XrayManager::configFilePath() const
{
    QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir);
    return dir + "/xray_config.json";
}
