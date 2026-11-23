package lt.ehu.unwalked.core.ui.map

object MapStyle {
    const val TILES = "https://tiles.openfreemap.org/planet"

    val dark: String = """
    {
      "version": 8,
      "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
      "sources": { "omt": { "type": "vector", "url": "$TILES" } },
      "layers": [
        { "id": "background", "type": "background", "paint": { "background-color": "#0B0F18" } },
        { "id": "park", "type": "fill", "source": "omt", "source-layer": "park", "paint": { "fill-color": "#0F1A1A" } },
        { "id": "landcover-wood", "type": "fill", "source": "omt", "source-layer": "landcover", "filter": ["in", "class", "wood", "grass"], "paint": { "fill-color": "#0E1718", "fill-opacity": 0.8 } },
        { "id": "water", "type": "fill", "source": "omt", "source-layer": "water", "paint": { "fill-color": "#0A1626" } },
        { "id": "waterway", "type": "line", "source": "omt", "source-layer": "waterway", "paint": { "line-color": "#0A1626", "line-width": 1.5 } },
        { "id": "building", "type": "fill", "source": "omt", "source-layer": "building", "minzoom": 14, "paint": { "fill-color": "#121826", "fill-outline-color": "#161E2D" } },
        { "id": "road-path", "type": "line", "source": "omt", "source-layer": "transportation", "filter": ["in", "class", "path", "track"], "paint": { "line-color": "#1C2433", "line-width": ["interpolate", ["linear"], ["zoom"], 13, 0.5, 18, 2], "line-dasharray": [2, 1] } },
        { "id": "road-minor", "type": "line", "source": "omt", "source-layer": "transportation", "filter": ["in", "class", "minor", "service"], "paint": { "line-color": "#1F2838", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 0.5, 18, 6] } },
        { "id": "road-major", "type": "line", "source": "omt", "source-layer": "transportation", "filter": ["in", "class", "primary", "secondary", "tertiary", "trunk", "motorway"], "paint": { "line-color": "#2A3446", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.8, 18, 10] } },
        { "id": "rail", "type": "line", "source": "omt", "source-layer": "transportation", "filter": ["==", "class", "rail"], "paint": { "line-color": "#202838", "line-width": 1, "line-dasharray": [3, 2] } },
        { "id": "water-name", "type": "symbol", "source": "omt", "source-layer": "water_name", "layout": { "text-field": "{name}", "text-font": ["Noto Sans Italic"], "text-size": 11 }, "paint": { "text-color": "#3E5878" } },
        { "id": "road-name", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 15, "layout": { "symbol-placement": "line", "text-field": "{name}", "text-font": ["Noto Sans Regular"], "text-size": 11 }, "paint": { "text-color": "#5D6B82", "text-halo-color": "#0B0F18", "text-halo-width": 1.2 } },
        { "id": "place-name", "type": "symbol", "source": "omt", "source-layer": "place", "filter": ["in", "class", "suburb", "neighbourhood", "quarter", "city", "town"], "layout": { "text-field": "{name}", "text-font": ["Noto Sans Regular"], "text-size": ["interpolate", ["linear"], ["zoom"], 11, 11, 15, 14], "text-transform": "uppercase", "text-letter-spacing": 0.08 }, "paint": { "text-color": "#7D8AA0", "text-halo-color": "#0B0F18", "text-halo-width": 1.5 } }
      ]
    }
    """.trimIndent()
}
