#pragma once
#include <QString>

// Platform-specific system proxy management.
// Implementation is in SystemProxy_win.cpp / SystemProxy_linux.cpp.
class SystemProxy
{
public:
    // Set system-wide HTTP+SOCKS proxy to 127.0.0.1:httpPort / socksPort.
    static bool enable(int socksPort, int httpPort);

    // Remove system proxy settings.
    static bool disable();
};
