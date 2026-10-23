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

1. The launcher starts the existing MainActivity with Theme.NovelNest.Starting.
   Android 12+ shows its native static logo splash; API 26–30 use a matching
   starting-window drawable. Neither is held for a timer, network, or logo animation.
2. MainActivity restores the normal theme before onCreate and immediately composes
   the application, preserving active-task state, reading progress, and notification navigation.
3. Android dismisses its splash when the first application frame is ready. There
   is no second Compose launch overlay, fixed delay, or wait for network content.
   Home can show its normal loading state while data arrives.

Previously, MainActivity also mounted NovelNestLaunchReveal after the system
splash. That independent timed logo animation caused the visible two-stage launch
and extra composition/animation work during startup. It is no longer mounted.
The standalone artwork component remains available but is not part of startup.
Notification opens and Activity recreation follow their normal navigation paths;
resuming an existing Activity does not replay an app-owned launch animation.

Fresh launcher entry opens Home on the saved source tab, including after removal
from Recents. An old persisted reader checkpoint never triggers navigation. Continue
Reading opens the last chapter from the independent reading-progress store. Existing
task navigation and reader SavedStateHandle restoration are retained for background
resume and Activity recreation.

Platform reference: [adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
and [system splash screens](https://developer.android.com/develop/ui/views/launch/splash-screen).
See also [Android splash migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)
for avoiding a second splash after the system splash.
