import json
from pathlib import Path
from statistics import median

from PIL import Image, ImageChops


directory = Path(__file__).resolve().parent.parent / "examples" / "vergil-cross-gate-reference"


def bright_pixels(path):
    image = Image.open(path).convert("RGB")
    pixels = image.load()
    points = []
    for vertical in range(int(image.height * 0.10), int(image.height * 0.85)):
        for horizontal in range(int(image.width * 0.15), int(image.width * 0.85)):
            color = pixels[horizontal, vertical]
            if max(color) > 225 and min(color) > 150:
                points.append((horizontal, vertical))
    assert points, f"No emissive pixels in {path}"
    return image, points


def bounds(points):
    return [min(point[0] for point in points), min(point[1] for point in points),
            max(point[0] for point in points), max(point[1] for point in points)]


frame, cross_points = bright_pixels(directory / "frames" / "020.png")
center_horizontal = frame.width / 2
cross_horizontal = [point for point in cross_points if abs(point[0] - center_horizontal) > frame.width * 0.04]
center_vertical = median(point[1] for point in cross_horizontal)
vertical_early = bounds(bright_pixels(directory / "frames" / "004.png")[1])
vertical_late = bounds(bright_pixels(directory / "frames" / "006.png")[1])
assert abs(vertical_early[1] - vertical_late[1]) <= 4, "Vertical cut must keep its upper endpoint fixed"
assert vertical_late[3] - vertical_early[3] > 15, "Vertical cut must advance downwards"
assert vertical_early[3] < center_vertical - 5, "Early vertical cut must start above the center"


def horizontal_bounds(tick):
    points = bright_pixels(directory / "frames" / f"{tick:03d}.png")[1]
    return bounds([point for point in points if abs(point[1] - center_vertical) < 8
                   and abs(point[0] - center_horizontal) > frame.width * 0.025])


horizontal_early = horizontal_bounds(14)
horizontal_late = horizontal_bounds(16)
assert abs(horizontal_early[0] - horizontal_late[0]) <= 4, "Horizontal cut must keep its left endpoint fixed"
assert horizontal_late[2] - horizontal_early[2] > 15, "Horizontal cut must advance rightwards"
assert horizontal_early[2] < center_horizontal - 5, "Early horizontal cut must start left of the center"

opening_early = bounds(bright_pixels(directory / "frames" / "028.png")[1])
opening_late = bounds(bright_pixels(directory / "frames" / "032.png")[1])
assert center_vertical - opening_early[1] > 8 and opening_early[3] - center_vertical > 8, "Both halves must open in the same frame"
assert opening_early[1] - opening_late[1] > 8 and opening_late[3] - opening_early[3] > 8, "Both halves must continue expanding together"

open_image, open_points = bright_pixels(directory / "proof-90.png")
open_bounds = bounds(open_points)
aspect = (open_bounds[2] - open_bounds[0]) / (open_bounds[3] - open_bounds[1])
assert aspect > 1.35, f"Portal must be wider than tall: {aspect}"
disabled = Image.open(directory / "warp-off.png").convert("RGB")
enabled = Image.open(directory / "warp-on.png").convert("RGB")
changed_refraction = sum(1 for color in ImageChops.difference(enabled, disabled).getdata() if max(color) > 8)
assert changed_refraction > 500, "Scene refraction must visibly affect the background"
moving_image, moving_points = bright_pixels(directory / "proof-135.png")
assert moving_image.size == open_image.size
changed_edge = len(set(open_points).symmetric_difference(moving_points))
assert changed_edge > 100, "The emissive border must keep animating after opening"
report = {
    "vertical_early": vertical_early,
    "vertical_late": vertical_late,
    "horizontal_early": horizontal_early,
    "horizontal_late": horizontal_late,
    "opening_early": opening_early,
    "opening_late": opening_late,
    "open_aspect_ratio": round(aspect, 3),
    "refraction_changed_pixels": changed_refraction,
    "animated_edge_changed_pixels": changed_edge,
    "captured_frames": len(list((directory / "frames").glob("*.png"))),
}
(directory / "visual-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print("PASS: directional slashes, simultaneous opening, wide portal, moving border and scene refraction")
print(json.dumps(report, indent=2))
