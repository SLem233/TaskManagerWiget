"""Render public demo images with synthetic tasks; no device or Vault access."""

from pathlib import Path
from math import cos, sin, pi
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts"
SCALE = 2
WIDTH, HEIGHT = 576, 1280
FONT_DIR = Path("C:/Windows/Fonts")


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "segoeuib.ttf" if bold else "segoeui.ttf"
    return ImageFont.truetype(str(FONT_DIR / name), size * SCALE)


def xy(value: int) -> int:
    return value * SCALE


def box(*values: int) -> tuple[int, ...]:
    return tuple(map(xy, values))


def text(draw: ImageDraw.ImageDraw, position: tuple[int, int], value: str,
         size: int, color: tuple[int, int, int], bold: bool = False) -> None:
    draw.text(tuple(map(xy, position)), value, font=font(size, bold), fill=color,
              anchor="lt")


def truncate(draw: ImageDraw.ImageDraw, value: str, size: int, max_width: int) -> str:
    face = font(size)
    if draw.textlength(value, font=face) <= xy(max_width):
        return value
    while value and draw.textlength(value + "…", font=face) > xy(max_width):
        value = value[:-1]
    return value + "…"


def make_image(compact: bool) -> None:
    image = Image.new("RGB", (xy(WIDTH), xy(HEIGHT)))
    draw = ImageDraw.Draw(image)
    for y in range(image.height):
        amount = y / image.height
        color = (int(14 + 8 * amount), int(22 + 10 * amount), int(34 + 18 * amount))
        draw.line((0, y, image.width, y), fill=color)
    for offset in (-200, 40, 250, 470, 700):
        draw.line(box(offset, 0, offset + 280, HEIGHT), fill=(29, 39, 54), width=xy(2))

    text(draw, (38, 27), "11:56", 19, (238, 242, 247), True)
    text(draw, (459, 29), "4G  94%", 15, (218, 225, 234))
    text(draw, (38, 86), "ДЕМО • СИНТЕТИЧЕСКИЕ ЗАДАЧИ", 12, (139, 163, 189))

    bottom = 420 if compact else 805
    draw.rounded_rectangle(box(28, 124, 548, bottom), radius=xy(20),
                           fill=(8, 13, 22), outline=(53, 70, 89), width=xy(1))
    text(draw, (48, 153), "ЗАДАЧИ", 23, (255, 255, 255), True)
    draw.arc(box(423, 150, 454, 181), 40, 325,
             fill=(255, 255, 255), width=xy(3))
    draw.polygon([box(448, 149)[:2], box(458, 150)[:2], box(453, 160)[:2]],
                 fill=(255, 255, 255))
    cx, cy = 504, 166
    draw.ellipse(box(cx - 12, cy - 12, cx + 12, cy + 12),
                 outline=(255, 255, 255), width=xy(3))
    draw.ellipse(box(cx - 4, cy - 4, cx + 4, cy + 4),
                 outline=(255, 255, 255), width=xy(2))
    for i in range(8):
        angle = i * pi / 4
        start = (xy(cx + int(13 * cos(angle))), xy(cy + int(13 * sin(angle))))
        end = (xy(cx + int(18 * cos(angle))), xy(cy + int(18 * sin(angle))))
        draw.line((start, end), fill=(255, 255, 255), width=xy(3))

    def header(label: str, y: int) -> None:
        text(draw, (109, y), label, 18, (159, 170, 183))

    def task(title: str, detail: str, y: int, color: tuple[int, int, int]) -> None:
        draw.rectangle(box(65, y + 5, 91, y + 31), outline=(231, 237, 243), width=xy(2))
        text(draw, (111, y), truncate(draw, title, 21, 405), 21, color)
        text(draw, (111, y + 31), detail, 16, (170, 182, 195))

    header("Просрочено", 211)
    task("Проверить сроки", "до 30 сен.", 250, (255, 0, 0))
    header("Сегодня", 325)
    task("Отправить материалы", "до 1 окт.", 363, (255, 128, 64))
    if not compact:
        task("Подготовить обзор", "пора 1 окт.", 436, (255, 255, 128))
        task("Начать прототип", "старт 1 окт.", 509, (0, 128, 255))
        header("Завтра", 585)
        task("Проверить демо", "до 4 окт.", 623, (255, 255, 255))
        text(draw, (48, 752), "←  список прокручивается  →", 12, (120, 142, 166))

    text(draw, (37, 1160), "TaskManagerWiget · иллюстрация интерфейса", 13,
         (151, 168, 187))
    draw.ellipse(box(257, 1229, 266, 1238), fill=(102, 119, 139))
    draw.ellipse(box(280, 1229, 289, 1238), fill=(242, 247, 252))
    draw.ellipse(box(303, 1229, 312, 1238), fill=(102, 119, 139))

    name = "widget-demo-compact.png" if compact else "widget-demo-large.png"
    image.resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS).save(OUT / name)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    make_image(compact=False)
    make_image(compact=True)
