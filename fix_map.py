import os

file_path = "D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Replace Satellite Source
old_satellite = """        MapLayer.SATELLITE -> object : OnlineTileSourceBase(
            "EsriWorldImagery",
            0,
            19,
            256,
            ".jpg",
            arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/" +
                        org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + "/" +
                        org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
            }
        }"""

new_satellite = """        MapLayer.SATELLITE -> object : OnlineTileSourceBase(
            "GoogleSatellite",
            0,
            19,
            256,
            "",
            arrayOf("https://mt1.google.com/vt/lyrs=y&")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return baseUrl + "x=" + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) +
                        "&y=" + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) +
                        "&z=" + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
            }
        }"""

if old_satellite in content:
    content = content.replace(old_satellite, new_satellite)
    print("Replaced Satellite with Google Hybrid")
else:
    print("Could not find satellite block")

# 2. Add Lifecycle onResume / onPause to OSMTileMap

old_disposable = """    DisposableEffect(mapView, marker) {
        val dragListener = object : Marker.OnMarkerDragListener {"""

new_disposable = """    DisposableEffect(mapView, marker) {
        mapView.onResume()
        
        val dragListener = object : Marker.OnMarkerDragListener {"""

if old_disposable in content:
    content = content.replace(old_disposable, new_disposable)
    print("Added onResume")
else:
    print("Could not find DisposableEffect start")

old_dispose = """        onDispose {
            mapView.overlays.remove(marker)
            mapView.overlays.remove(tapOverlay)
            mapView.onDetach()
        }"""

new_dispose = """        onDispose {
            mapView.onPause()
            mapView.overlays.remove(marker)
            mapView.overlays.remove(tapOverlay)
            mapView.onDetach()
        }"""

if old_dispose in content:
    content = content.replace(old_dispose, new_dispose)
    print("Added onPause")
else:
    print("Could not find onDispose block")
    
# 3. Wait, one more thing. MapView is created as MapView(context). In Compose, if the AndroidView is detached and a new one is created but it uses the exact same `mapView` instance via `remember`, it might be an issue?
# Yes. `val mapView = remember { MapView(context) }` persists across recompositions. This is correct.
# However, sometimes we need to invalidate or resume when the composable itself is drawn.

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("File updated")