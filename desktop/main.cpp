#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QIcon>
#include <QQuickStyle>
#include "AppController.h"

int main(int argc, char* argv[])
{
    QGuiApplication app(argc, argv);
    app.setApplicationName("CarneliaVPN");
    app.setApplicationVersion("2.4.0");
    app.setOrganizationName("Carnelia");
    app.setOrganizationDomain("carnelia.vpn");

#if defined(Q_OS_UNIX) && !defined(Q_OS_MACOS)
    QQuickStyle::setStyle("gtk");
#endif

    AppController controller;

    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty("appCtrl", &controller);

    const QUrl url(u"qrc:/carnelia/vpn/qml/main.qml"_qs);
    QObject::connect(
        &engine, &QQmlApplicationEngine::objectCreationFailed,
        &app, []() { QCoreApplication::exit(-1); },
        Qt::QueuedConnection
    );
    engine.load(url);

    if (engine.rootObjects().isEmpty())
        return -1;

    return app.exec();
}
