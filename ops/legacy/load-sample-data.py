#!/usr/bin/env python3
"""Load sample owners, pets, visits and photos through the web application (Fase 1.7 of the lab).

Goes through the HTTP forms on purpose, so the data follows the same path as a real user.
Usage: load-sample-data.py [base_url]   (default http://localhost:8080)
Needs curl, and psql access as the postgres user to look up the generated pet ids.
"""
import random
import re
import struct
import subprocess
import sys
import tempfile
import zlib
from datetime import date, timedelta
from pathlib import Path

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8080"
random.seed(20260915)

OWNERS = [
    ("Lucía", "Fernández", "Av. Colón 1234", "Córdoba"),
    ("Martín", "Gómez", "San Martín 455", "Rosario"),
    ("Valentina", "Rodríguez", "Belgrano 78", "Mendoza"),
    ("Joaquín", "López", "Rivadavia 2100", "Buenos Aires"),
    ("Camila", "Martínez", "Mitre 902", "La Plata"),
    ("Tomás", "Sánchez", "Sarmiento 315", "Salta"),
    ("Sofía", "Pérez", "Independencia 640", "Tucumán"),
    ("Mateo", "Díaz", "Urquiza 27", "Paraná"),
    ("Julieta", "Romero", "9 de Julio 1500", "Neuquén"),
    ("Benjamín", "Álvarez", "Laprida 88", "Bahía Blanca"),
]
PET_NAMES = ["Luna", "Simón", "Mora", "Rocco", "Nina", "Toby", "Olivia", "Milo", "Kira", "Bruno",
             "Frida", "Coco", "Lola", "Pancho", "Maya", "Tito", "Chispa", "Pipa"]
PET_TYPES = ["cat", "dog", "dog", "cat", "bird", "hamster", "lizard", "snake"]
VISIT_REASONS = ["vacunación anual", "control general", "desparasitación", "limpieza dental",
                 "control de peso", "herida leve en pata", "revisión post operatoria", "alergia en piel"]


def curl(*args):
    out = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code} %{redirect_url}", *args],
                         check=True, capture_output=True, text=True).stdout
    code, _, location = out.partition(" ")
    # Every call is a form POST: success redirects (302); 200 means the form came back with validation errors.
    if code != "302":
        raise RuntimeError(f"HTTP {code} (expected 302) for {args}")
    return code, location


def psql(sql):
    return subprocess.run(["sudo", "-u", "postgres", "psql", "-d", "petclinic", "-At", "-c", sql],
                          check=True, capture_output=True, text=True).stdout.strip()


def png(path, rgb, size=96):
    """Solid-colour square with a lighter diagonal band, so each pet gets a distinct image."""
    light = tuple(min(255, c + 90) for c in rgb)
    rows = b"".join(
        b"\x00" + b"".join(bytes(light if abs(x - y) < size // 6 else rgb) for x in range(size))
        for y in range(size))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def main():
    work = Path(tempfile.mkdtemp(prefix="petclinic-load-"))
    names = iter(random.sample(PET_NAMES, len(PET_NAMES)))
    for first, last, address, city in OWNERS:
        phone = str(random.randint(3510000000, 3519999999))
        _, location = curl("--data-urlencode", f"firstName={first}", "--data-urlencode", f"lastName={last}",
                           "--data-urlencode", f"address={address}", "--data-urlencode", f"city={city}",
                           "--data-urlencode", f"telephone={phone}", f"{BASE}/owners/new")
        match = re.search(r"/owners/(\d+)$", location)
        if not match:
            raise RuntimeError(f"Owner {first} {last} not created (validation error?)")
        owner_id = int(match.group(1))

        for _ in range(random.randint(1, 2)):
            pet = next(names)
            birth = date(2016, 1, 1) + timedelta(days=random.randint(0, 3000))
            curl("--data-urlencode", f"name={pet}", "--data-urlencode", f"birthDate={birth}",
                 "--data-urlencode", f"type={random.choice(PET_TYPES)}", f"{BASE}/owners/{owner_id}/pets/new")
            pet_id = int(psql(f"SELECT id FROM pets WHERE owner_id = {owner_id} AND name = '{pet}'"))

            photo = work / f"{pet_id}.png"
            png(photo, (random.randint(40, 160), random.randint(40, 160), random.randint(40, 160)))
            curl("-F", f"photo=@{photo};type=image/png", f"{BASE}/owners/{owner_id}/pets/{pet_id}/photo")

            for _ in range(random.randint(1, 3)):
                visit = date(2026, 1, 1) + timedelta(days=random.randint(0, 250))
                curl("--data-urlencode", f"date={visit}", "--data-urlencode",
                     f"description={random.choice(VISIT_REASONS)}",
                     f"{BASE}/owners/{owner_id}/pets/{pet_id}/visits/new")
        print(f"owner {owner_id}: {first} {last}")

    print(psql("SELECT 'owners=' || (SELECT count(*) FROM owners) || ' pets=' || (SELECT count(*) FROM pets)"
               " || ' visits=' || (SELECT count(*) FROM visits)"))


if __name__ == "__main__":
    main()
