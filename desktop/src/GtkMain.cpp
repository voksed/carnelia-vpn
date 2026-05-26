#include <gtk/gtk.h>
#include <QCoreApplication>
#include <QStringList>
#include "AppController.h"

static AppController* g_controller = nullptr;
static GtkWidget* g_statusLabel = nullptr;
static GtkWidget* g_connectButton = nullptr;
static GtkWidget* g_serverCombo = nullptr;
static GtkWidget* g_bypassSwitch = nullptr;
static GtkWidget* g_muxSwitch = nullptr;
static GtkWidget* g_fragSwitch = nullptr;
static GtkWidget* g_socksPortSpin = nullptr;
static GtkWidget* g_httpPortSpin = nullptr;
static GtkWidget* g_muxConcurrencySpin = nullptr;
static GtkWidget* g_fragModeCombo = nullptr;
static GtkWidget* g_logLevelCombo = nullptr;

static gboolean processQtEvents(gpointer user_data)
{
    QCoreApplication::processEvents();
    return G_SOURCE_CONTINUE;
}

static void refreshUi()
{
    if (!g_controller) return;

    bool connected = g_controller->connected();
    gtk_button_set_label(GTK_BUTTON(g_connectButton), connected ? "Отключиться" : "Подключиться");
    gtk_label_set_text(GTK_LABEL(g_statusLabel), g_controller->statusText().toUtf8().constData());

    gtk_widget_set_sensitive(g_serverCombo, !connected);
    gtk_widget_set_sensitive(g_bypassSwitch, !connected);
    gtk_widget_set_sensitive(g_muxSwitch, !connected);
    gtk_widget_set_sensitive(g_fragSwitch, !connected);
    gtk_widget_set_sensitive(g_socksPortSpin, !connected);
    gtk_widget_set_sensitive(g_httpPortSpin, !connected);
    gtk_widget_set_sensitive(g_muxConcurrencySpin, !connected);
    gtk_widget_set_sensitive(g_fragModeCombo, !connected);
    gtk_widget_set_sensitive(g_logLevelCombo, !connected);
}

static void updateStatusLabel(const QString& text)
{
    gtk_label_set_text(GTK_LABEL(g_statusLabel), text.toUtf8().constData());
}

static void onConnectClicked(GtkButton* button, gpointer)
{
    if (!g_controller) return;
    if (g_controller->connected()) {
        g_controller->disconnectVpn();
    } else {
        g_controller->connectVpn();
    }
}

static void onServerChanged(GtkComboBoxText* combo)
{
    if (!g_controller) return;
    int index = gtk_combo_box_get_active(GTK_COMBO_BOX(combo));
    if (index >= 0)
        g_controller->setSelectedServerIndex(index);
}

static void onToggleBypass(GtkToggleButton* toggle)
{
    if (!g_controller) return;
    g_controller->setBypassRussia(gtk_toggle_button_get_active(toggle));
}

static void onToggleMux(GtkToggleButton* toggle)
{
    if (!g_controller) return;
    g_controller->setMuxEnabled(gtk_toggle_button_get_active(toggle));
}

static void onToggleFragmentation(GtkToggleButton* toggle)
{
    if (!g_controller) return;
    g_controller->setFragmentationEnabled(gtk_toggle_button_get_active(toggle));
}

static void onSocksPortChanged(GtkSpinButton* spin)
{
    if (!g_controller) return;
    g_controller->setSocksPort(gtk_spin_button_get_value_as_int(spin));
}

static void onHttpPortChanged(GtkSpinButton* spin)
{
    if (!g_controller) return;
    g_controller->setHttpPort(gtk_spin_button_get_value_as_int(spin));
}

static void onMuxConcurrencyChanged(GtkSpinButton* spin)
{
    if (!g_controller) return;
    g_controller->setMuxConcurrency(gtk_spin_button_get_value_as_int(spin));
}

static void onFragModeChanged(GtkComboBoxText* combo)
{
    if (!g_controller) return;
    gchar* text = gtk_combo_box_text_get_active_text(combo);
    if (text) {
        g_controller->setFragmentationMode(QString::fromUtf8(text));
        g_free(text);
    }
}

static void onLogLevelChanged(GtkComboBoxText* combo)
{
    if (!g_controller) return;
    gchar* text = gtk_combo_box_text_get_active_text(combo);
    if (text) {
        g_controller->setLogLevel(QString::fromUtf8(text));
        g_free(text);
    }
}

static GtkWidget* createLabeledWidget(const char* label, GtkWidget* widget)
{
    GtkWidget* box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 8);
    GtkWidget* lbl = gtk_label_new(label);
    gtk_widget_set_hexpand(lbl, FALSE);
    gtk_widget_set_halign(lbl, GTK_ALIGN_START);
    gtk_box_pack_start(GTK_BOX(box), lbl, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(box), widget, TRUE, TRUE, 0);
    return box;
}

int main(int argc, char* argv[])
{
    gtk_init(&argc, &argv);
    QCoreApplication app(argc, argv);

    AppController controller;
    g_controller = &controller;

    GtkWidget* window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_default_size(GTK_WINDOW(window), 600, 520);
    gtk_window_set_title(GTK_WINDOW(window), "CarneliaVPN GTK");
    g_signal_connect(window, "destroy", G_CALLBACK(gtk_main_quit), nullptr);

    GtkWidget* root = gtk_box_new(GTK_ORIENTATION_VERTICAL, 12);
    gtk_container_set_border_width(GTK_CONTAINER(root), 16);
    gtk_container_add(GTK_CONTAINER(window), root);

    g_statusLabel = gtk_label_new("Отключено");
    gtk_box_pack_start(GTK_BOX(root), g_statusLabel, FALSE, FALSE, 0);

    g_connectButton = gtk_button_new_with_label("Подключиться");
    g_signal_connect(g_connectButton, "clicked", G_CALLBACK(onConnectClicked), nullptr);
    gtk_box_pack_start(GTK_BOX(root), g_connectButton, FALSE, FALSE, 0);

    g_serverCombo = gtk_combo_box_text_new();
    const QStringList servers = controller.serverNames();
    for (const QString& item : servers)
        gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_serverCombo), item.toUtf8().constData());
    gtk_combo_box_set_active(GTK_COMBO_BOX(g_serverCombo), controller.selectedServerIndex());
    g_signal_connect(g_serverCombo, "changed", G_CALLBACK(onServerChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("Сервер", g_serverCombo), FALSE, FALSE, 0);

    g_bypassSwitch = gtk_check_button_new_with_label("Обход российских сайтов");
    gtk_toggle_button_set_active(GTK_TOGGLE_BUTTON(g_bypassSwitch), controller.bypassRussia());
    g_signal_connect(g_bypassSwitch, "toggled", G_CALLBACK(onToggleBypass), nullptr);
    gtk_box_pack_start(GTK_BOX(root), g_bypassSwitch, FALSE, FALSE, 0);

    g_socksPortSpin = gtk_spin_button_new_with_range(1024, 65535, 1);
    gtk_spin_button_set_value(GTK_SPIN_BUTTON(g_socksPortSpin), controller.socksPort());
    g_signal_connect(g_socksPortSpin, "value-changed", G_CALLBACK(onSocksPortChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("SOCKS5 порт", g_socksPortSpin), FALSE, FALSE, 0);

    g_httpPortSpin = gtk_spin_button_new_with_range(1024, 65535, 1);
    gtk_spin_button_set_value(GTK_SPIN_BUTTON(g_httpPortSpin), controller.httpPort());
    g_signal_connect(g_httpPortSpin, "value-changed", G_CALLBACK(onHttpPortChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("HTTP порт", g_httpPortSpin), FALSE, FALSE, 0);

    g_muxSwitch = gtk_check_button_new_with_label("MUX");
    gtk_toggle_button_set_active(GTK_TOGGLE_BUTTON(g_muxSwitch), controller.muxEnabled());
    g_signal_connect(g_muxSwitch, "toggled", G_CALLBACK(onToggleMux), nullptr);
    gtk_box_pack_start(GTK_BOX(root), g_muxSwitch, FALSE, FALSE, 0);

    g_muxConcurrencySpin = gtk_spin_button_new_with_range(1, 16, 1);
    gtk_spin_button_set_value(GTK_SPIN_BUTTON(g_muxConcurrencySpin), controller.muxConcurrency());
    g_signal_connect(g_muxConcurrencySpin, "value-changed", G_CALLBACK(onMuxConcurrencyChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("Конкурентность MUX", g_muxConcurrencySpin), FALSE, FALSE, 0);

    g_fragSwitch = gtk_check_button_new_with_label("Фрагментация");
    gtk_toggle_button_set_active(GTK_TOGGLE_BUTTON(g_fragSwitch), controller.fragmentationEnabled());
    g_signal_connect(g_fragSwitch, "toggled", G_CALLBACK(onToggleFragmentation), nullptr);
    gtk_box_pack_start(GTK_BOX(root), g_fragSwitch, FALSE, FALSE, 0);

    g_fragModeCombo = gtk_combo_box_text_new();
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_fragModeCombo), "light");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_fragModeCombo), "balanced");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_fragModeCombo), "aggressive");
    gtk_combo_box_set_active(GTK_COMBO_BOX(g_fragModeCombo), 1);
    g_signal_connect(g_fragModeCombo, "changed", G_CALLBACK(onFragModeChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("Режим фрагментации", g_fragModeCombo), FALSE, FALSE, 0);

    g_logLevelCombo = gtk_combo_box_text_new();
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_logLevelCombo), "debug");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_logLevelCombo), "info");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_logLevelCombo), "warning");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_logLevelCombo), "error");
    gtk_combo_box_text_append_text(GTK_COMBO_BOX_TEXT(g_logLevelCombo), "none");
    gtk_combo_box_set_active(GTK_COMBO_BOX(g_logLevelCombo), 2);
    g_signal_connect(g_logLevelCombo, "changed", G_CALLBACK(onLogLevelChanged), nullptr);
    gtk_box_pack_start(GTK_BOX(root), createLabeledWidget("Уровень логов", g_logLevelCombo), FALSE, FALSE, 0);

    QObject::connect(&controller, &AppController::connectionStateChanged, [](){ refreshUi(); });
    QObject::connect(&controller, &AppController::settingsChanged, [](){ refreshUi(); });
    QObject::connect(&controller, &AppController::errorOccurred, [](const QString& message){ updateStatusLabel(message); });

    refreshUi();

    g_timeout_add(16, processQtEvents, nullptr);
    gtk_widget_show_all(window);
    gtk_main();

    return 0;
}
