#!/bin/sh
# Export the approved glass artwork. Requires ImageMagick 7 (magick).
set -eu
BBTV_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$BBTV_ROOT"

command -v magick >/dev/null 2>&1 || { echo 'Install ImageMagick 7 to export the brand assets.' >&2; exit 1; }
mkdir -p tv/src/main/res/drawable-xxxhdpi tv/src/main/res/drawable-xhdpi \
  phone/src/main/res/drawable-xxxhdpi web/public

# Keep web installation artwork square and opaque; the platform applies its mask.
magick design/brand/icon-master.png -colorspace sRGB -resize 512x512 -strip web/public/icon-512.png
magick design/brand/icon-master.png -colorspace sRGB -resize 192x192 -strip web/public/icon-192.png
magick design/brand/icon-master.png -colorspace sRGB -resize 180x180 -strip web/public/apple-touch-icon.png

# Legacy Android launcher icons need their own silhouette.
magick design/brand/icon-master.png -colorspace sRGB -resize 192x192 \
  \( +clone -alpha extract -fill black -colorize 100 -fill white \
    -draw 'roundrectangle 0,0 191,191 43,43' \) \
  -alpha off -compose CopyOpacity -composite -strip tv/src/main/res/drawable-xxxhdpi/app_icon.png
cp tv/src/main/res/drawable-xxxhdpi/app_icon.png phone/src/main/res/drawable-xxxhdpi/app_icon.png

# An embedded image works in SVG favicons, where external image URLs are blocked.
BBTV_ICON_DATA=$(base64 < web/public/icon-192.png | tr -d '\n')
cat > web/public/icon.svg <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <defs><clipPath id="mask"><rect width="512" height="512" rx="114"/></clipPath></defs>
  <image width="512" height="512" clip-path="url(#mask)" href="data:image/png;base64,$BBTV_ICON_DATA"/>
</svg>
EOF

# Android TV uses a 320x180 dp banner at xhdpi: 640x360 physical pixels.
magick design/brand/banner-master.png -colorspace sRGB -resize 640x360! -strip tv/src/main/res/drawable-xhdpi/banner.png
magick design/brand/banner-master.png -colorspace sRGB -resize 1280x720! -strip web/public/banner.png
