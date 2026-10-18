"""Genera manual-usuario.md e img/ a partir de manual-usuario.html (la fuente).

Uso, desde la raíz del repositorio:
    python docs/manual/generar-markdown.py docs/manual/manual-usuario.html docs/manual/manual-usuario.md docs/manual/img

Solo usa la biblioteca estándar. Si cambian la fecha de revisión o las secciones del HTML,
actualice también la cabecera y el índice que este script escribe al final.
"""
import base64, re, sys, unicodedata
from html.parser import HTMLParser
from pathlib import Path

src = Path(sys.argv[1]); out_md = Path(sys.argv[2]); img_dir = Path(sys.argv[3])
img_dir.mkdir(parents=True, exist_ok=True)
html = src.read_text(encoding="utf-8")
html = html[html.index('<main'):html.index('</main>')]


def slug(t, n=40):
    t = unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", t).strip("-")[:n].strip("-") or "img"


class P(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.out = []; self.buf = ""; self.lists = []; self.img_n = 0
        self.table = None; self.cell = None; self.in_pre = False
        self.caption = None; self.note = False; self.skip = 0; self.href = None

    def w(self, s):
        if self.cell is not None: self.cell += s
        elif self.caption is not None: self.caption += s
        else: self.buf += s

    def flush(self, sep="\n\n"):
        t = re.sub(r"[ \t]*\n[ \t]*", " ", self.buf).strip() if not self.in_pre else self.buf
        m = re.match(r"^( +)(?:\d+\. |- )", self.buf)
        if t and m and not self.in_pre: t = m.group(1) + t
        t = re.sub(r"^(#+)\s+", r"\1 ", t)
        if t:
            if self.note: t = "> " + t
            self.out.append(t + sep)
        self.buf = ""

    def handle_starttag(self, tag, a):
        a = dict(a)
        cls = a.get("class", "") or ""
        if tag == "span" and "n" == cls: self.skip += 1; return
        if tag in ("h2", "h3", "h4"):
            self.flush(); self.buf = "#" * (int(tag[1])) + " "
            if a.get("id"): self.anchor = a["id"]
        elif tag == "p": self.flush()
        elif tag in ("ul", "ol"):
            self.flush("\n"); self.lists.append([tag, 0])
        elif tag == "li":
            self.flush("\n")
            l = self.lists[-1]; l[1] += 1
            ind = "   " * (len(self.lists) - 1)
            self.buf = ind + (f"{l[1]}. " if l[0] == "ol" else "- ")
        elif tag in ("strong", "b"): self.w("**")
        elif tag in ("em", "i"): self.w("*")
        elif tag == "code": self.w("`")
        elif tag == "br": self.w("<br>")
        elif tag == "a": self.href = a.get("href"); self.w("[")
        elif tag == "div" and "note" in cls: self.flush(); self.note = True
        elif tag == "table": self.flush(); self.table = []
        elif tag == "tr": self.table.append([])
        elif tag in ("td", "th"): self.cell = ""
        elif tag == "figcaption": self.caption = ""
        elif tag == "img":
            m = re.match(r"data:image/(\w+);base64,(.*)", a.get("src", ""), re.S)
            alt = a.get("alt", "")
            if m:
                self.img_n += 1
                ext = "jpg" if m.group(1) == "jpeg" else m.group(1)
                name = f"{self.img_n:02d}-{slug(alt)}.{ext}"
                (img_dir / name).write_bytes(base64.b64decode(m.group(2)))
                self.pending_img = (alt, f"{img_dir.name}/{name}", a.get("width")); self.h = a.get("height")

    def handle_endtag(self, tag):
        if tag == "span" and self.skip: self.skip -= 1; return
        if tag in ("h2", "h3", "h4", "p"): self.flush()
        elif tag in ("ul", "ol"):
            self.flush("\n"); self.lists.pop()
            if not self.lists: self.out.append("\n")
        elif tag == "li": self.flush("\n")
        elif tag in ("strong", "b"): self.w("**")
        elif tag in ("em", "i"): self.w("*")
        elif tag == "code": self.w("`")
        elif tag == "a":
            h = self.href or ""
            self.w(f"]({h})"); self.href = None
        elif tag == "div" and self.note: self.flush(); self.note = False
        elif tag in ("td", "th"):
            self.table[-1].append(re.sub(r"\s+", " ", self.cell).strip().replace("|", "\\|")); self.cell = None
        elif tag == "table":
            rows = [r for r in self.table if r]
            lines = ["| " + " | ".join(rows[0]) + " |", "|" + " :--- |" * len(rows[0])]
            lines += ["| " + " | ".join(r) + " |" for r in rows[1:]]
            self.out.append("\n".join(lines) + "\n\n"); self.table = None
        elif tag == "figure":
            alt, path, width = getattr(self, "pending_img", ("", "", None))
            cap = re.sub(r"\s+", " ", self.caption or "").strip()
            wattr = f' width="{300 if width and int(width) <= int(self.h or 0) else 640}"' if width else ""
            self.flush()
            self.out.append(f'<p align="center"><img src="{path}" alt="{alt}"{wattr}><br><em>{cap}</em></p>\n\n')
            self.caption = None
        elif tag == "figcaption": pass

    def handle_data(self, d):
        if self.skip: return
        if self.table is not None and self.cell is None: return
        self.w(d)


p = P(); p.feed(html); p.flush()
body = "".join(p.out)
body = re.sub(r"\n{3,}", "\n\n", body)
body = re.sub(r"\*\*\s+\*\*", " ", body)
# Anclas del HTML -> anclas que GitHub genera a partir de los títulos.
for old, new in {"#admin": "#6-administrador-role_admin", "#turista": "#3-turista-role_tourist",
                 "#t-cuenta": "#31-crear-cuenta-e-ingresar"}.items():
    body = body.replace(f"]({old})", f"]({new})")
header = (
    "# Manual de usuario · Intégrate\n\n"
    "Cómo usar la aplicación según tu rol: Turista, Gestor de Enlace, Anfitrión comunitario y Administrador.\n"
    "Cada paso se verificó recorriendo el flujo real en la aplicación.\n\n"
    "> **Versión:** Fase 1 · MVP transaccional · Revisado el 16 de octubre de 2026. "
    "Versión navegable con estilos: [manual-usuario.html](manual-usuario.html) (descárguela y ábrala en el navegador).\n\n"
    "## Contenido\n\n"
    "1. [Antes de empezar](#1-antes-de-empezar)\n"
    "2. [Roles y permisos](#2-roles-y-permisos)\n"
    "3. [Turista](#3-turista-role_tourist)\n"
    "4. [Gestor de Enlace](#4-gestor-de-enlace-role_link_manager)\n"
    "5. [Anfitrión comunitario](#5-anfitrión-comunitario-role_host)\n"
    "6. [Administrador](#6-administrador-role_admin)\n"
    "7. [Mensajes y problemas frecuentes](#7-mensajes-y-problemas-frecuentes)\n"
    "8. [Registro de la prueba del flujo](#8-registro-de-la-prueba-del-flujo)\n\n"
)
out_md.write_text(header + body.strip() + "\n", encoding="utf-8")
print("images:", p.img_n)
