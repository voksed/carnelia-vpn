#include "SystemProxy.h"
#ifndef UNICODE
#define UNICODE
#endif
#include <windows.h>
#include <wininet.h>

bool SystemProxy::enable(int socksPort, int httpPort)
{
    // Build proxy string: http=127.0.0.1:httpPort;socks=127.0.0.1:socksPort
    QString proxyStr = QString("http=127.0.0.1:%1;socks=127.0.0.1:%2")
                           .arg(httpPort).arg(socksPort);
    std::wstring proxyW  = proxyStr.toStdWString();
    std::wstring bypassW = L"localhost;127.*;::1;<local>";

    INTERNET_PER_CONN_OPTION options[3];
    options[0].dwOption        = INTERNET_PER_CONN_FLAGS;
    options[0].Value.dwValue   = PROXY_TYPE_PROXY;

    options[1].dwOption        = INTERNET_PER_CONN_PROXY_SERVER;
    options[1].Value.pszValue  = const_cast<LPWSTR>(proxyW.c_str());

    options[2].dwOption        = INTERNET_PER_CONN_PROXY_BYPASS;
    options[2].Value.pszValue  = const_cast<LPWSTR>(bypassW.c_str());

    INTERNET_PER_CONN_OPTION_LIST list;
    list.dwSize        = sizeof(list);
    list.pszConnection = nullptr;   // default connection
    list.dwOptionCount = 3;
    list.dwOptionError = 0;
    list.pOptions      = options;

    bool ok = InternetSetOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION,
                                &list, sizeof(list));
    InternetSetOption(nullptr, INTERNET_OPTION_SETTINGS_CHANGED, nullptr, 0);
    InternetSetOption(nullptr, INTERNET_OPTION_REFRESH,          nullptr, 0);
    return ok;
}

bool SystemProxy::disable()
{
    INTERNET_PER_CONN_OPTION options[1];
    options[0].dwOption      = INTERNET_PER_CONN_FLAGS;
    options[0].Value.dwValue = PROXY_TYPE_DIRECT;

    INTERNET_PER_CONN_OPTION_LIST list;
    list.dwSize        = sizeof(list);
    list.pszConnection = nullptr;
    list.dwOptionCount = 1;
    list.dwOptionError = 0;
    list.pOptions      = options;

    bool ok = InternetSetOption(nullptr, INTERNET_OPTION_PER_CONNECTION_OPTION,
                                &list, sizeof(list));
    InternetSetOption(nullptr, INTERNET_OPTION_SETTINGS_CHANGED, nullptr, 0);
    InternetSetOption(nullptr, INTERNET_OPTION_REFRESH,          nullptr, 0);
    return ok;
}
