#pragma once
#include <QObject>

// Samples network interface bytes every second and emits traffic stats.
class TrafficMonitor : public QObject
{
    Q_OBJECT
public:
    explicit TrafficMonitor(QObject* parent = nullptr);

    void start();
    void stop();
    void reset();

signals:
    // uploadSpeed / downloadSpeed in bytes/s; totalUp / totalDown cumulative since reset()
    void updated(double uploadSpeed, double downloadSpeed,
                 qint64 totalUp,    qint64 totalDown);

private slots:
    void sample();

private:
    int     m_timerId = 0;
    qint64  m_prevRx  = 0;
    qint64  m_prevTx  = 0;
    qint64  m_totalRx = 0;
    qint64  m_totalTx = 0;

    void timerEvent(QTimerEvent* event) override;

    static qint64 readRxBytes();
    static qint64 readTxBytes();
};
