# Novel Nest icon and launch

## Artwork

The mark combines an open book, two nest curves, and a reading spark. Charcoal
`#0F1216` matches the app background; amber `#E2A765`, paper amber `#F1C68F`, and
deep amber `#C78C4B` keep it readable without lettering at small launcher sizes.
No gradients, shadows, or raster decoding are needed in the app.

- Editable source: `artwork/novelnest/mark.svg` (108-unit adaptive foreground).
- Full icon SVG: `artwork/novelnest/icon.svg`.
- Store asset: `artwork/novelnest/icon-512.png` (512 x 512, opaque square).
- Circular preview: `artwork/novelnest/icon-round-preview.png`.
- Android artwork: `res/drawable/ic_novelnest_mark.xml` and its monochrome sibling.
- Default, API 26 adaptive, and API 33 themed resources are in the corresponding
  `mipmap-anydpi*` folders. Android applies the launcher mask; the foreground does
  not contain a baked-in circular or rounded-square background.

The meaningful mark fits inside the central 66-unit safe circle in the 108-unit
foreground. The 512px store icon uses the central 72-unit launcher crop.

Regenerate all icon resources and exports from the source SVG on Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Export-AppIcon.ps1
```

This uses built-in .NET drawing, requires no Python or image-generation service,
and changes no persistent execution-policy setting. The SVG exporter supports the
absolute M/L/C/Z path commands used by this mark.

## Launch sequence

1. The launcher starts MainActivity with `Theme.NovelNest.Starting` (inheriting from `Theme.SplashScreen`).
   Android shows the circular emblem (`ic_novelnest_splash_logo`) with its amber-gold border and shining sunrays natively.
2. In `MainActivity.onCreate`, `installSplashScreen()` is invoked before `super.onCreate()`, adhering to official Android guidance.
3. The custom launch animation is applied directly to the Android system splash screen using `splashScreen.setOnExitAnimationListener`:
   - `splashScreenViewProvider.iconView` smoothly scales up (1.0f -> 1.16f) with an `AccelerateDecelerateInterpolator`.
   - `splashScreenViewProvider.view` fades out gracefully (1.0f -> 0f), seamlessly dissolving the system splash into the application UI.
   - When the exit animation completes, `splashScreenViewProvider.remove()` dismisses the splash.
4. There is no second Compose splash screen or in-app delay—the single Android system splash handles both the instantaneous cold-start display and the fluid reveal animation.

Fresh launcher entry opens Home on the saved source tab, including after removal
from Recents. An old persisted reader checkpoint never triggers navigation. Continue
Reading opens the last chapter from the independent reading-progress store. Existing
task navigation and reader SavedStateHandle restoration are retained for background
resume and Activity recreation.

Platform reference: [adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
and [system splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen).
See also [Android splash migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)
for avoiding a second splash after the system splash.
