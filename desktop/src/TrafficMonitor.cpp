#include "TrafficMonitor.h"

#ifdef Q_OS_WIN
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#  endif
#  include <windows.h>
#  include <iphlpapi.h>
#elif defined(Q_OS_LINUX)
#  include <QFile>
#  include <QTextStream>
#endif

#include <QTimerEvent>

TrafficMonitor::TrafficMonitor(QObject* parent) : QObject(parent) {}

void TrafficMonitor::start()
{
    if (m_timerId != 0) return;
    // Prime baseline
    m_prevRx = readRxBytes();
    m_prevTx = readTxBytes();
    m_timerId = startTimer(1000);
}

void TrafficMonitor::stop()
{
    if (m_timerId != 0) { killTimer(m_timerId); m_timerId = 0; }
}

void TrafficMonitor::reset()
{
    m_totalRx = 0;
    m_totalTx = 0;
    m_prevRx  = readRxBytes();
    m_prevTx  = readTxBytes();
}

void TrafficMonitor::timerEvent(QTimerEvent* event)
{
    if (event->timerId() != m_timerId) return;
    sample();
}

void TrafficMonitor::sample()
{
    qint64 rx = readRxBytes();
    qint64 tx = readTxBytes();

    double down = static_cast<double>(rx - m_prevRx);
    double up   = static_cast<double>(tx - m_prevTx);
    if (down < 0) down = 0;
    if (up   < 0) up   = 0;

    m_totalRx += static_cast<qint64>(down);
    m_totalTx += static_cast<qint64>(up);
    m_prevRx   = rx;
    m_prevTx   = tx;

    emit updated(up, down, m_totalTx, m_totalRx);
}

// ── Platform implementations ──────────────────────────────────────────────

#ifdef Q_OS_WIN
static std::pair<qint64,qint64> winIfaceBytes()
{
    PMIB_IFTABLE table = nullptr;
    qint64 totalIn = 0, totalOut = 0;
    ULONG tableSize = 0;
    
    // First call to get the required size
    if (GetIfTable(table, &tableSize, FALSE) == ERROR_INSUFFICIENT_BUFFER && tableSize > 0) {
        table = (PMIB_IFTABLE)malloc(tableSize);
        if (table && GetIfTable(table, &tableSize, FALSE) == NO_ERROR) {
            for (DWORD i = 0; i < table->dwNumEntries; i++) {
                const auto& row = table->table[i];
                if (row.dwOperStatus == MIB_IF_OPER_STATUS_OPERATIONAL &&
                    row.dwType       != IF_TYPE_SOFTWARE_LOOPBACK)
                {
                    totalIn  += static_cast<qint64>(row.dwInOctets);
                    totalOut += static_cast<qint64>(row.dwOutOctets);
                }
            }
            free(table);
        }
    }
    return {totalIn, totalOut};
}

qint64 TrafficMonitor::readRxBytes() { return winIfaceBytes().first;  }
qint64 TrafficMonitor::readTxBytes() { return winIfaceBytes().second; }

#elif defined(Q_OS_LINUX)
// Parse /proc/net/dev — sum all non-loopback interfaces
static std::pair<qint64,qint64> linuxIfaceBytes()
{
    QFile f("/proc/net/dev");
    if (!f.open(QIODevice::ReadOnly | QIODevice::Text))
        return {0, 0};

    qint64 rx = 0, tx = 0;
    QTextStream in(&f);
    in.readLine(); in.readLine();    // skip 2 header lines
    while (!in.atEnd()) {
        QString line = in.readLine().trimmed();
        if (line.startsWith("lo:")) continue;
        int colon = line.indexOf(':');
        if (colon < 0) continue;
        QStringList fields = line.mid(colon + 1).simplified().split(' ');
        if (fields.size() >= 9) {
            rx += fields[0].toLongLong();
            tx += fields[8].toLongLong();
        }
    }
    return {rx, tx};
}

qint64 TrafficMonitor::readRxBytes() { return linuxIfaceBytes().first;  }
qint64 TrafficMonitor::readTxBytes() { return linuxIfaceBytes().second; }

#else
qint64 TrafficMonitor::readRxBytes() { return 0; }
qint64 TrafficMonitor::readTxBytes() { return 0; }
#endif
