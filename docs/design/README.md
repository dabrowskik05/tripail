# Tripail design sources

Editable / master artwork for branding. **The app does not read this folder at
runtime** — Compose and the launcher use the density PNGs under `:ui` / `:app`
`res/drawable-*` and `res/mipmap-*`. After changing a source here, regenerate
those resources before building.

## Files

| File | Role |
|---|---|
| `tripail-wordmark.svg` | Editable splash wordmark (gradients / filters — not for VectorDrawable) |
| `tripail-wordmark@4x.png` | Raster master for splash densities |
| `ic_launcher_foreground@4x.png` | Adaptive-icon foreground master (432×432 = 108dp @ xxxhdpi) |
| `ic_launcher_background.svg` | Solid `#BCE3F7` — mirrored as `@color/ic_launcher_background` |

## Regenerating runtime assets

Splash logo is shown at **144 dp** height (3:1). From the `@4x` wordmark master:

| density | size (px) | output |
|---|---|---|
| mdpi | 432×144 | `ui/src/main/res/drawable-mdpi/logo_wordmark.png` |
| hdpi | 648×216 | `ui/src/main/res/drawable-hdpi/logo_wordmark.png` |
| xhdpi | 864×288 | `ui/src/main/res/drawable-xhdpi/logo_wordmark.png` |
| xxhdpi | 1296×432 | `ui/src/main/res/drawable-xxhdpi/logo_wordmark.png` |
| xxxhdpi | 1728×576 | `ui/src/main/res/drawable-xxxhdpi/logo_wordmark.png` |

Launcher foreground: scale `ic_launcher_foreground@4x.png` to 108×scale for
each density into `app/src/main/res/drawable-*/ic_launcher_foreground.png`, then
composite onto `#BCE3F7` and downscale to 48×scale for
`app/src/main/res/mipmap-*/ic_launcher{,_round}.png`.
