#pragma once
#include <QObject>
#include <QProcess>

class XrayManager : public QObject
{
    Q_OBJECT
public:
    explicit XrayManager(QObject* parent = nullptr);
    ~XrayManager();

    bool start(const QString& configJson);
    void stop();

    bool isRunning() const;
    QString lastError() const { return m_lastError; }

signals:
    void started();
    void stopped(int exitCode);
    void errorOccurred(const QString& message);
    void logLine(const QString& line);

private slots:
    void onProcessStarted();
    void onProcessFinished(int exitCode, QProcess::ExitStatus status);
    void onProcessError(QProcess::ProcessError error);
    void onReadStderr();
    void onReadStdout();

private:
    QProcess* m_proc;
    QString   m_lastError;
    QString   m_configPath;

    QString xrayBinaryPath() const;
    QString configFilePath() const;
};
