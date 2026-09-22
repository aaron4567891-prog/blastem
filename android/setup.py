"""Prepare SDL, generated CPU cores and real asset files on Windows or Linux."""
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SDL = ROOT / "android/app/jni/SDL"
SDL_COMMIT = "8236e01a9f758d15927624925c6043f84d8a261f"


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    if not SDL.exists():
        run("git", "clone", "--depth", "1", "--branch", "release-2.30.12",
            "https://github.com/libsdl-org/SDL.git", str(SDL))
    revision = subprocess.check_output(
        ["git", "-C", str(SDL), "rev-parse", "HEAD"], text=True).strip()
    if revision != SDL_COMMIT:
        raise SystemExit("The SDL folder must contain SDL release-2.30.12; existing files were left unchanged.")

    for core in ("m68k", "z80", "sh2", "upd78k2"):
        print("Generating", core, flush=True)
        result = run(sys.executable, "cpu_dsl.py", "-d", "call", core + ".cpu",
                     stdout=subprocess.PIPE)
        (ROOT / (core + ".c")).write_bytes(result.stdout)

    assets = ROOT / "android/app/build/generated/blastemAssets"
    assets.mkdir(parents=True, exist_ok=True)
    for name in ("default.cfg", "rom.db", "systems.cfg"):
        shutil.copy2(ROOT / name, assets / name)
    for name in ("images", "shaders"):
        shutil.copytree(ROOT / name, assets / name, dirs_exist_ok=True)
    print("Android setup complete. Build from android with gradlew.bat assembleDebug.")


if __name__ == "__main__":
    main()
