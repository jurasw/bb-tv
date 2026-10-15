# BB TV — glass identity

The icon and banner use a blue television and play mark built from nested, filled color layers on a near-white background. Depth comes from subtle edge highlights and soft shadows between layers. Match the restrained Apple Home and Podcasts references supplied by the user; keep surfaces calm and nearly planar.

Design reference: [Apple HIG — App icons](https://developer.apple.com/design/human-interface-guidelines/app-icons), read on 2026-10-15. Apple supplies dynamic lighting through Icon Composer on its platforms. These Android and web assets are static renders of that material treatment.

`references/` contains the user-supplied Apple reference screenshots for visual comparison only. These images are not included in production exports.

- `icon-master.png`: opaque, square, unmasked artwork. Keep the mark free of lettering.
- `banner-master.png`: wide artwork with a charcoal `BB TV` wordmark and safe margins for the TV launcher; exported at 16:9.
- `prompts.md`: exact generation prompts and reference relationship, using the built-in image generation tool.

Run `sh scripts/export-brand.sh` from the repository to regenerate the Android TV icon/banner, phone icon, web icon, Apple touch icon, SVG favicon, and web banner. Requires ImageMagick 7. The script only packages approved artwork; it does not call an image API.

Web installation PNGs are full bleed so the platform can apply its own mask. The SVG favicon and legacy Android launcher PNGs have rounded corners because those consumers need a standalone silhouette. The SVG embeds its image so it works when favicon external-resource loading is disabled.
