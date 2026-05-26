#include "SystemProxy.h"
#include <QProcess>
#include <QStringList>
#include <QFile>

static bool runGSettings(const QStringList& args)
{
    QProcess proc;
    proc.start("gsettings", args);
    if (!proc.waitForFinished(3000))
        return false;
    return proc.exitStatus() == QProcess::NormalExit && proc.exitCode() == 0;
}

bool SystemProxy::enable(int socksPort, int httpPort)
{
    // Prefer GNOME-compatible proxy settings.
    bool ok = true;
    ok &= runGSettings({"set", "org.gnome.system.proxy", "mode", "manual"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.http", "host", "127.0.0.1"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.http", "port", QString::number(httpPort)});
    ok &= runGSettings({"set", "org.gnome.system.proxy.https", "host", "127.0.0.1"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.https", "port", QString::number(httpPort)});
    ok &= runGSettings({"set", "org.gnome.system.proxy.socks", "host", "127.0.0.1"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.socks", "port", QString::number(socksPort)});
    ok &= runGSettings({"set", "org.gnome.system.proxy", "use-same-proxy", "true"});
    return ok;
}

bool SystemProxy::disable()
{
    bool ok = true;
    ok &= runGSettings({"set", "org.gnome.system.proxy", "mode", "none"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.http", "host", ""});
    ok &= runGSettings({"set", "org.gnome.system.proxy.http", "port", "0"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.https", "host", ""});
    ok &= runGSettings({"set", "org.gnome.system.proxy.https", "port", "0"});
    ok &= runGSettings({"set", "org.gnome.system.proxy.socks", "host", ""});
    ok &= runGSettings({"set", "org.gnome.system.proxy.socks", "port", "0"});
    return ok;
}
