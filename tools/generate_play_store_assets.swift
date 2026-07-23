#!/usr/bin/env swift

import AppKit
import Foundation

struct LocaleCopy: Decodable {
    let tagline: String
    let screens: [[String]]
}

struct DeviceProfile {
    let folder: String
    let width: Int
    let height: Int
}

let profiles = [
    DeviceProfile(folder: "phone", width: 1080, height: 1920),
    DeviceProfile(folder: "tablet-7-inch", width: 1537, height: 2732),
    DeviceProfile(folder: "tablet-10-inch", width: 1800, height: 3200),
]

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let assetsRoot = root.appendingPathComponent("docs/play-store-assets")
let sourceRoot = assetsRoot.appendingPathComponent("screenshots/captioned")
let outputRoot = assetsRoot.appendingPathComponent("localized")
let copyURL = root.appendingPathComponent("tools/play_store_asset_captions.json")
let copies = try JSONDecoder().decode(
    [String: LocaleCopy].self,
    from: Data(contentsOf: copyURL)
)
let sourceNames = [
    "01-home-captioned.png",
    "02-photo-templates-captioned.png",
    "03-video-merge-captioned.png",
    "04-collage-editor-captioned.png",
    "05-multi-panel-layouts-captioned.png",
    "06-adaptive-grids-captioned.png",
    "07-grid-editor-captioned.png",
]
let sourceImages = try sourceNames.map { name -> NSImage in
    let url = sourceRoot.appendingPathComponent(name)
    guard let image = NSImage(contentsOf: url) else {
        throw NSError(domain: "PlayStoreAssets", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cannot load \(url.path)"])
    }
    return image
}
guard let icon = NSImage(contentsOf: assetsRoot.appendingPathComponent("app-icon-512.png")),
      let featureArtwork = NSImage(contentsOf: assetsRoot.appendingPathComponent("feature-art-background.png")) else {
    throw NSError(domain: "PlayStoreAssets", code: 2, userInfo: [NSLocalizedDescriptionKey: "Cannot load icon or feature graphic"])
}

let ink = NSColor(calibratedRed: 0.025, green: 0.18, blue: 0.18, alpha: 1)
let secondaryInk = NSColor(calibratedRed: 0.20, green: 0.34, blue: 0.34, alpha: 1)
let mint = NSColor(calibratedRed: 0.91, green: 0.97, blue: 0.96, alpha: 1)
let blush = NSColor(calibratedRed: 1.0, green: 0.93, blue: 0.94, alpha: 1)
let blue = NSColor(calibratedRed: 0.91, green: 0.95, blue: 1.0, alpha: 1)
let accent = NSColor(calibratedRed: 0.02, green: 0.55, blue: 0.50, alpha: 1)
let appCrop = NSRect(x: 0, y: 0, width: 1080, height: 1585)

func isRTL(_ locale: String) -> Bool {
    locale == "ar" || locale == "ur"
}

func canvas(width: Int, height: Int, draw: () -> Void) -> NSImage {
    let image = NSImage(size: NSSize(width: width, height: height))
    image.lockFocus()
    NSGraphicsContext.current?.imageInterpolation = .high
    fill(NSRect(x: 0, y: 0, width: width, height: height), color: .white)
    draw()
    image.unlockFocus()
    return image
}

func fill(_ rect: NSRect, color: NSColor) {
    color.setFill()
    NSBezierPath(rect: rect).fill()
}

func savePNG(_ image: NSImage, to url: URL) throws {
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    var rect = NSRect(origin: .zero, size: image.size)
    guard let cgImage = image.cgImage(forProposedRect: &rect, context: nil, hints: nil) else {
        throw NSError(domain: "PlayStoreAssets", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot rasterize \(url.path)"])
    }
    let bitmap = NSBitmapImageRep(cgImage: cgImage)
    guard let data = bitmap.representation(using: .png, properties: [.compressionFactor: 1.0]) else {
        throw NSError(domain: "PlayStoreAssets", code: 4, userInfo: [NSLocalizedDescriptionKey: "Cannot encode \(url.path)"])
    }
    try data.write(to: url, options: .atomic)
}

func paragraph(rtl: Bool, alignment: NSTextAlignment? = nil) -> NSMutableParagraphStyle {
    let style = NSMutableParagraphStyle()
    style.alignment = alignment ?? (rtl ? .right : .left)
    style.baseWritingDirection = rtl ? .rightToLeft : .leftToRight
    style.lineBreakMode = .byWordWrapping
    return style
}

func fittedFont(text: String, rect: NSRect, maximum: CGFloat, minimum: CGFloat, weight: NSFont.Weight, rtl: Bool) -> NSFont {
    var size = maximum
    while size > minimum {
        let font = NSFont.systemFont(ofSize: size, weight: weight)
        let bounds = (text as NSString).boundingRect(
            with: rect.size,
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: font, .paragraphStyle: paragraph(rtl: rtl)]
        )
        if bounds.width <= rect.width && bounds.height <= rect.height {
            return font
        }
        size -= 1
    }
    return NSFont.systemFont(ofSize: minimum, weight: weight)
}

func drawText(
    _ text: String,
    in rect: NSRect,
    maximumSize: CGFloat,
    minimumSize: CGFloat,
    weight: NSFont.Weight,
    color: NSColor,
    rtl: Bool,
    alignment: NSTextAlignment? = nil
) {
    let font = fittedFont(
        text: text,
        rect: rect,
        maximum: maximumSize,
        minimum: minimumSize,
        weight: weight,
        rtl: rtl
    )
    (text as NSString).draw(
        with: rect,
        options: [.usesLineFragmentOrigin, .usesFontLeading],
        attributes: [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraph(rtl: rtl, alignment: alignment),
        ]
    )
}

func roundedFill(_ rect: NSRect, radius: CGFloat, color: NSColor) {
    color.setFill()
    NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius).fill()
}

func drawScreenshot(
    source: NSImage,
    copy: [String],
    locale: String,
    profile: DeviceProfile
) -> NSImage {
    let w = CGFloat(profile.width)
    let h = CGFloat(profile.height)
    let rtl = isRTL(locale)
    let headerHeight: CGFloat = profile.folder == "phone" ? 335 : (profile.folder == "tablet-7-inch" ? 470 : 540)
    return canvas(width: profile.width, height: profile.height) {
        roundedFill(NSRect(x: w * 0.66, y: h - headerHeight, width: w * 0.34, height: headerHeight), radius: w * 0.06, color: mint)
        fill(NSRect(x: 0, y: h - headerHeight - 55, width: w * 0.55, height: 80), color: blush)
        fill(NSRect(x: w * 0.88, y: 0, width: w * 0.12, height: h * 0.18), color: blue)

        let iconSize = profile.folder == "phone" ? 92.0 : (profile.folder == "tablet-7-inch" ? 124.0 : 150.0)
        let margin = profile.folder == "phone" ? 72.0 : (profile.folder == "tablet-7-inch" ? 130.0 : 165.0)
        icon.draw(in: NSRect(x: margin, y: h - iconSize - 60, width: iconSize, height: iconSize))
        drawText(
            "SplitFrame",
            in: NSRect(x: margin + iconSize + 22, y: h - iconSize - 48, width: w * 0.55, height: iconSize),
            maximumSize: profile.folder == "phone" ? 28 : 42,
            minimumSize: 22,
            weight: .bold,
            color: accent,
            rtl: false
        )
        let headlineRect = NSRect(x: margin, y: h - headerHeight + 92, width: w - margin * 2, height: headerHeight * 0.30)
        let subRect = NSRect(x: margin, y: h - headerHeight + 28, width: w - margin * 2, height: headerHeight * 0.22)
        drawText(copy[0], in: headlineRect, maximumSize: profile.folder == "phone" ? 48 : 68, minimumSize: 30, weight: .heavy, color: ink, rtl: rtl)
        drawText(copy[1], in: subRect, maximumSize: profile.folder == "phone" ? 28 : 40, minimumSize: 20, weight: .regular, color: secondaryInk, rtl: rtl)

        let availableHeight = h - headerHeight + 24
        let scale = min((w - margin * 0.9) / appCrop.width, availableHeight / appCrop.height)
        let destination = NSRect(
            x: (w - appCrop.width * scale) / 2,
            y: 0,
            width: appCrop.width * scale,
            height: appCrop.height * scale
        )
        source.draw(in: destination, from: appCrop, operation: .sourceOver, fraction: 1)
    }
}

func drawFeatureGraphic(copy: LocaleCopy, locale: String) -> NSImage {
    let rtl = isRTL(locale)
    return canvas(width: 1024, height: 500) {
        featureArtwork.draw(in: NSRect(x: 0, y: 0, width: 1024, height: 500))
        roundedFill(
            NSRect(x: 34, y: 62, width: 470, height: 376),
            radius: 26,
            color: NSColor(calibratedRed: 0.95, green: 0.99, blue: 0.98, alpha: 0.91)
        )
        drawText("SplitFrame", in: NSRect(x: 68, y: 257, width: 402, height: 96), maximumSize: 58, minimumSize: 44, weight: .heavy, color: ink, rtl: false)
        drawText(copy.tagline, in: NSRect(x: 68, y: 145, width: 402, height: 108), maximumSize: 31, minimumSize: 20, weight: .semibold, color: secondaryInk, rtl: rtl)
    }
}

func drawContentSheet(images: [NSImage], copy: LocaleCopy, locale: String, profile: DeviceProfile) -> NSImage {
    let width = 3200
    let height = 2400
    let rtl = isRTL(locale)
    return canvas(width: width, height: height) {
        fill(NSRect(x: 0, y: 0, width: width, height: height), color: mint)
        drawText("SplitFrame", in: NSRect(x: 120, y: 2240, width: 900, height: 100), maximumSize: 72, minimumSize: 56, weight: .heavy, color: ink, rtl: false)
        drawText(copy.tagline, in: NSRect(x: 1080, y: 2248, width: 2000, height: 88), maximumSize: 48, minimumSize: 28, weight: .semibold, color: secondaryInk, rtl: rtl)

        let columns = 4
        let gap: CGFloat = 34
        let cardWidth: CGFloat = (CGFloat(width) - 240 - gap * 3) / 4
        let cardHeight: CGFloat = 1000
        for index in images.indices {
            let row = index / columns
            let column = index % columns
            let x = 120 + CGFloat(column) * (cardWidth + gap)
            let y = 1160 - CGFloat(row) * (cardHeight + gap)
            roundedFill(NSRect(x: x, y: y, width: cardWidth, height: cardHeight), radius: 26, color: .white)
            let image = images[index]
            let imageHeight: CGFloat = 840
            let scale = min((cardWidth - 34) / image.size.width, imageHeight / image.size.height)
            let imageRect = NSRect(
                x: x + (cardWidth - image.size.width * scale) / 2,
                y: y + 145,
                width: image.size.width * scale,
                height: image.size.height * scale
            )
            image.draw(in: imageRect)
            drawText(
                copy.screens[index][0],
                in: NSRect(x: x + 24, y: y + 30, width: cardWidth - 48, height: 90),
                maximumSize: 31,
                minimumSize: 19,
                weight: .bold,
                color: ink,
                rtl: rtl,
                alignment: .center
            )
        }
    }
}

let commandArguments = Array(CommandLine.arguments.dropFirst())
let sheetsOnly = commandArguments.contains("--sheets-only")
let featuresOnly = commandArguments.contains("--features-only")
let screensOnly = commandArguments.contains("--screens-only")
let requestedLocales = Set(commandArguments.filter { !$0.hasPrefix("--") })
let locales = copies.keys.sorted().filter { requestedLocales.isEmpty || requestedLocales.contains($0) }
try FileManager.default.createDirectory(at: outputRoot, withIntermediateDirectories: true)
for locale in locales {
    guard let copy = copies[locale], copy.screens.count == sourceImages.count else {
        throw NSError(domain: "PlayStoreAssets", code: 5, userInfo: [NSLocalizedDescriptionKey: "Invalid copy for \(locale)"])
    }
    let localeRoot = outputRoot.appendingPathComponent(locale)
    if !sheetsOnly && !screensOnly {
        try savePNG(
            drawFeatureGraphic(copy: copy, locale: locale),
            to: localeRoot.appendingPathComponent("feature-graphic-1024x500.png")
        )
    }
    if featuresOnly {
        print("Generated \(locale)")
        continue
    }
    for profile in profiles {
        let deviceRoot = localeRoot.appendingPathComponent(profile.folder)
        var rendered: [NSImage] = []
        for index in sourceImages.indices {
            let screenshotURL = deviceRoot.appendingPathComponent(sourceNames[index])
            let image: NSImage
            if sheetsOnly {
                guard let existing = NSImage(contentsOf: screenshotURL) else {
                    throw NSError(
                        domain: "PlayStoreAssets",
                        code: 6,
                        userInfo: [NSLocalizedDescriptionKey: "Cannot load \(screenshotURL.path)"]
                    )
                }
                image = existing
            } else {
                image = drawScreenshot(
                    source: sourceImages[index],
                    copy: copy.screens[index],
                    locale: locale,
                    profile: profile
                )
            }
            rendered.append(image)
            if !sheetsOnly {
                try savePNG(image, to: screenshotURL)
            }
        }
        let sheet = drawContentSheet(images: rendered, copy: copy, locale: locale, profile: profile)
        try savePNG(sheet, to: deviceRoot.appendingPathComponent("content-sheet-all-features.png"))
    }
    print("Generated \(locale)")
}
