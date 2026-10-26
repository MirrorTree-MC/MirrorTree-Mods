"""Check water-effect candidate/distance order without starting Minecraft.

This verifies the loop arithmetic, not fluid-update cost or gameplay throughput.
"""

import math


def positions(position, height, optimized):
    x, y, z = position
    bx, by, bz = map(math.floor, position)
    eye = x, y + height, z
    for dx in range(-3, 4):
        ex = bx + dx + 0.5 - eye[0]
        for dy in range(-3, 4):
            ey = by + dy + 0.5 - eye[1]
            xy = ex * ex + ey * ey
            if optimized and xy > 9:
                continue
            for dz in range(-3, 4):
                ez = bz + dz + 0.5 - eye[2]
                squared = xy + ez * ez
                if squared <= 9:
                    yield bx + dx, by + dy, bz + dz, squared


def main():
    cases = 0
    for origin in (0, -30000000, 29999996):
        for x in (0, 0.1, 0.5, 0.9999999):
            for y in (-63, 0.1, 200.9999999):
                for z in (0, 0.25, 0.5, 0.9999999):
                    for eye_height in (0, 0.4, 1.27, 1.62, 2.55):
                        position = origin + x, y, origin + z
                        assert list(positions(position, eye_height, False)) == list(
                            positions(position, eye_height, True)
                        ), (position, eye_height)
                        cases += 1
    print(f"PASS: identical water candidate, distance and update order for {cases} positions/poses")


if __name__ == "__main__":
    main()
