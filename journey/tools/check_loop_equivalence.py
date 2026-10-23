"""Finite equivalence checks for the cleanup's candidate and worldgen loops.

This compares the original and simplified arithmetic; it is not a runtime
Minecraft test or a benchmark. Run with Python 3 from any directory.
"""

import math


def original_candidates():
    for radius in range(5):
        for y in range(4):
            for x in range(-radius, radius + 1):
                for z in range(-radius, radius + 1):
                    for dy in ((0,) if y == 0 else (y, -y)):
                        yield x, dy, z


def current_candidates():
    for radius in range(5):
        for y in range(4):
            for x in range(-radius, radius + 1):
                for z in range(-radius, radius + 1):
                    if radius > 0 and abs(x) < radius and abs(z) < radius:
                        continue
                    yield x, y, z
                    if y > 0:
                        yield x, -y, z


def radii(radius):
    inner = radius + 2
    width = max(1, min(2, math.floor(radius * 0.1 + 0.5)))
    return inner, inner + width


def placements(radius, squared):
    inner, outer = radii(radius)
    thickness = radius / 4
    for x in range(-outer, outer + 1):
        for y in range(-outer, outer + 1):
            for z in range(-outer, outer + 1):
                # Each yielded event consumes one RNG value and performs one
                # placement. Keeping the plane label catches overlap changes.
                for plane, distance_squared, height in (
                    ("xy", x * x + y * y, z),
                    ("xz", x * x + z * z, y),
                    ("yz", y * y + z * z, x),
                ):
                    inside = (
                        inner * inner <= distance_squared <= outer * outer
                        if squared else inner <= math.sqrt(distance_squared) <= outer
                    )
                    if inside and abs(height) < thickness:
                        yield x, y, z, plane


def main():
    before = list(original_candidates())
    after = list(current_candidates())
    assert list(dict.fromkeys(before)) == after, "first safe candidate order changed"
    assert len(before) == 1155 and len(after) == 567
    print("Safe search: identical first-encounter order; 1155 -> 567 world checks.")

    # The three planes use the same radial predicate. Check every integer pair
    # across a wider finite radius range, including boundary points.
    for radius in range(2, 129):
        inner, outer = radii(radius)
        for a in range(-outer, outer + 1):
            for b in range(-outer, outer + 1):
                distance_squared = a * a + b * b
                assert (inner <= math.sqrt(distance_squared) <= outer) == (
                    inner * inner <= distance_squared <= outer * outer
                ), (radius, a, b)
    print("Ring predicate: all integer coordinate pairs match for radii 2..128.")

    events = 0
    for radius in range(2, 25):
        before = list(placements(radius, squared=False))
        after = list(placements(radius, squared=True))
        assert before == after, ("placement/RNG order changed", radius)
        events += len(after)
    print(f"Rings: identical XYZ/plane placement and RNG event order for radii 2..24 ({events} events).")


if __name__ == "__main__":
    main()
