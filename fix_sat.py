import os

file_path = "D:/carneliavpn/carnelia-vpn-fork/android/app/src/main/kotlin/com/carnelia/vpn/GeoSpoofActivity.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the MapLayer.SATELLITE block
old_satellite_block = """        MapLayer.SATELLITE -> XYTileSource(
            "EsriWorldImagery",
            0,
            19,
            256,
            ".jpg",
            arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
        )"""

new_satellite_block = """        MapLayer.SATELLITE -> object : OnlineTileSourceBase(
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

if old_satellite_block in content:
    content = content.replace(old_satellite_block, new_satellite_block)
    print("Replaced SATELLITE block")
else:
    print("Could not find old SATELLITE block!")

# Add imports
old_import = "import org.osmdroid.tileprovider.tilesource.XYTileSource"
new_import = "import org.osmdroid.tileprovider.tilesource.XYTileSource\nimport org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase\nimport org.osmdroid.util.MapTileIndex"

if old_import in content and "OnlineTileSourceBase" not in content:
    content = content.replace(old_import, new_import)
    print("Added imports")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")