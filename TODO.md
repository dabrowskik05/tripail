# Tripail — Backlog

Known issues and planned work. **Do not treat this list as a request to fix them now.**

1. Language picker appears on every launch (should only show on first run, or from Settings).
2. Top bar: spacing between Settings and Community icons is too tight. Community button does nothing.
3. Continent screen: dragging the map with a finger causes jitter. Map touch should be fully locked; only the bottom slider should respond.
4. H3 LOD: zooming changes the size/shape of hexes from the GPS trail (they were meant to stay fixed). The trail looks like a puzzle of jagged hexagons instead of a solid corridor.
5. UI state leak: selecting regions in Poland, then opening Czechia, still shows Poland’s region outlines in Czechia.
6. Stability: jumping between countries eventually crashes and stops continent taps from working.
7. Geocoder: full Polish names such as “Norwegia” or “Niemcy” do not resolve correctly in the Polish locale.
8. Radius logic: cities unlocked by staying in place (Auto) get a different radius than manually unlocked ones, which conflicts. Radius should follow city population / size.
9. Android back gesture: ugly screen collapse (not wired cleanly to Compose Navigation).
10. Animations: no smooth transitions between the Continents view and Country details.
11. Cleanup: thoroughly remove junk files, dead code, unused resources and leftover test artefacts from the repo.
12. README: add real UI screenshots (continent menu, fog reveal, trail, search).
