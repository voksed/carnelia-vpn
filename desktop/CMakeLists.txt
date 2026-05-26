cmake_minimum_required(VERSION 3.22)
project(CarneliaVPN VERSION 2.4.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

set(MSYS2_PREFIX "C:/msys64/mingw64")

set(CMAKE_PREFIX_PATH "${MSYS2_PREFIX}")

find_package(PkgConfig REQUIRED)
pkg_check_modules(GTK3 REQUIRED gtk+-3.0 json-glib-1.0 gio-2.0)
find_package(Qt6 REQUIRED COMPONENTS Core)

set(SOURCES
    src/ServerProfile.cpp
    src/PrefsManager.cpp
    src/ConfigBuilder.cpp
    src/XrayManager.cpp
    src/TrafficMonitor.cpp
    src/GtkMain.cpp
    ui/AppController.cpp
)

if(WIN32)
    list(APPEND SOURCES src/SystemProxy_win.cpp)
else()
    list(APPEND SOURCES src/SystemProxy_linux.cpp)
endif()

add_executable(CarneliaVPN ${SOURCES})

target_include_directories(CarneliaVPN PRIVATE src ui ${GTK3_INCLUDE_DIRS})

target_compile_options(CarneliaVPN PRIVATE ${GTK3_CFLAGS_OTHER})

target_link_libraries(CarneliaVPN PRIVATE ${GTK3_LIBRARIES} Qt6::Core)

install(TARGETS CarneliaVPN DESTINATION .)

add_custom_command(TARGET CarneliaVPN POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_SOURCE_DIR}/../xray-extracted/xray"
        "$<TARGET_FILE_DIR:CarneliaVPN>/xray"
    COMMENT "Copying xray"
)

if(WIN32)
    target_link_libraries(CarneliaVPN PRIVATE wininet ws2_32 iphlpapi)
    # No console window in release builds
    if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
        set_target_properties(CarneliaVPN PROPERTIES WIN32_EXECUTABLE TRUE)
    endif()
endif()

install(TARGETS CarneliaVPN DESTINATION .)

# Copy xray binary next to the executable
if(WIN32)
    set(XRAY_SOURCE "${CMAKE_SOURCE_DIR}/../xray-extracted/xray.exe")
    if(NOT EXISTS "${XRAY_SOURCE}")
        set(XRAY_SOURCE "${CMAKE_SOURCE_DIR}/../xray-extracted/xray")
    endif()
    add_custom_command(TARGET CarneliaVPN POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "${XRAY_SOURCE}"
            "$<TARGET_FILE_DIR:CarneliaVPN>/xray.exe"
        COMMENT "Copying xray.exe"
    )
else()
    add_custom_command(TARGET CarneliaVPN POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
            "${CMAKE_SOURCE_DIR}/../xray-extracted/xray"
            "$<TARGET_FILE_DIR:CarneliaVPN>/xray"
        COMMENT "Copying xray"
    )
endif()
