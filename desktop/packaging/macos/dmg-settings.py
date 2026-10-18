# dmgbuild settings for the macOS DMG: the window it opens to, laid out like every Mac app's
# installer. The background (scripts/dmg-background.html, rendered by
# scripts/render-dmg-background.sh, joined into a Retina TIFF by the release job) draws the words
# and the arrow; Finder draws the two icons on it at icon_locations, which must stay at the arrow's
# two ends in the HTML. Run as:
#   dmgbuild -s desktop/packaging/macos/dmg-settings.py -D app=<TMPlayer.app> \
#       -D background=<tiff> -D icon=<icns> "TMPlayer <version>" <out.dmg>
import os.path

app = defines["app"]  # noqa: F821, dmgbuild supplies defines
appname = os.path.basename(app)

format = "ULMO"
filesystem = "APFS"
files = [app]
symlinks = {"Applications": "/Applications"}
icon = defines.get("icon")  # noqa: F821
background = defines["background"]  # noqa: F821

# The content area is the background's size, 660 by 400 points; the window adds its title bar.
window_rect = ((200, 140), (660, 400))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
include_icon_view_settings = True
arrange_by = None
icon_size = 112
text_size = 13
icon_locations = {
    appname: (170, 220),
    "Applications": (490, 220),
}
