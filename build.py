#!/usr/bin/env python3
"""Build the standalone, non-debuggable Fiji Pocket APK with the local SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

APP = Path(__file__).resolve().parent
ROOT = APP.parent
DATA = ROOT / ".mobile"
SDK = Path(os.environ.get("ANDROID_HOME", str(DATA / "android-sdk"))).expanduser()
JAVA = Path(os.environ.get("JAVA_HOME", str(DATA / "jdk-17" / "Contents" / "Home"))).expanduser()
TOOLS = SDK / "build-tools" / "36.0.0"
ANDROID_JAR = SDK / "platforms" / "android-36" / "android.jar"
OUTPUT = APP / "dist" / "fiji-pocket.apk"
COMPONENT = "org.fijipocket.android/org.fijipocket.MainActivity"


def demo_assets():
    """Require the offline gallery's full data, previews, and attribution in every build."""
    directory = APP / "assets" / "demos"
    provenance = json.loads((directory / "provenance.json").read_text())
    required = {name for demo in ("cells", "brain", "embryos", "leaf")
                for name in (f"{demo}.tif", f"{demo}-thumb.jpg")}
    files = {}
    for name in sorted(required | {"CREDITS.txt", "provenance.json"}):
        data = (directory / name).read_bytes()
        if not data:
            raise SystemExit(f"Empty bundled demo asset: {name}")
        if name in required:
            expected = provenance["bundled_files"][name]
            if len(data) != expected["bytes"] or hashlib.sha256(data).hexdigest() != expected["sha256"]:
                raise SystemExit(f"Bundled demo does not match provenance: {name}")
        files[f"assets/demos/{name}"] = data
    return files


def build():
    demos = demo_assets()
    for required in [JAVA / "bin" / "javac", TOOLS / "aapt2", ANDROID_JAR]:
        if not required.exists():
            raise SystemExit(f"Missing {required}. Set JAVA_HOME to JDK 17 and ANDROID_HOME to an SDK with platform 36 and Build Tools 36.0.0.")
    env = dict(os.environ, JAVA_HOME=str(JAVA), JAVA_OPTS="-Xmx512m")
    env["PATH"] = str(JAVA / "bin") + os.pathsep + env.get("PATH", "")

    def run(*args):
        subprocess.run([str(arg) for arg in args], env=env, check=True, cwd=APP)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    run(JAVA / "bin" / "java", "-version")
    with tempfile.TemporaryDirectory(prefix="compile-", dir=OUTPUT.parent) as temp:
        work = Path(temp)
        generated, classes, dex = (work / name for name in ("generated", "classes", "dex"))
        for directory in (generated, classes, dex):
            directory.mkdir()
        resources = work / "resources.zip"
        unsigned = work / "unsigned.apk"
        aligned = work / "aligned.apk"
        signed = work / "fiji-pocket.apk"

        print("Compiling resources and Java…", flush=True)
        run(TOOLS / "aapt2", "compile", "--dir", APP / "res", "-o", resources)
        run(TOOLS / "aapt2", "link", "-o", unsigned, "--manifest", APP / "AndroidManifest.xml",
            "-I", ANDROID_JAR, "--java", generated, "-A", APP / "assets", resources)
        sources = sorted((APP / "src").rglob("*.java")) + sorted(generated.rglob("*.java"))
        run(JAVA / "bin" / "javac", "-encoding", "UTF-8", "--release", "8",
            "-classpath", ANDROID_JAR, "-d", classes, *sources)
        run(TOOLS / "d8", "--release", "--min-api", "26", "--lib", ANDROID_JAR, "--output", dex,
            *sorted(classes.rglob("*.class")))
        with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
            for bytecode in sorted(dex.glob("*.dex")):
                apk.write(bytecode, bytecode.name)
        run(TOOLS / "zipalign", "-f", "4", unsigned, aligned)

        # Persistent local distribution key. Keep it to sign compatible updates.
        # This is for direct APK distribution, not a Play signing configuration.
        key = DATA / "fiji-pocket.keystore"
        key.parent.mkdir(parents=True, exist_ok=True)
        if not key.exists():
            run(JAVA / "bin" / "keytool", "-genkeypair", "-noprompt", "-keystore", key,
                "-storepass", "android", "-keypass", "android", "-alias", "fijipocket",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                "-dname", "CN=Fiji Pocket Local Distribution")
        print("Signing and verifying APK…", flush=True)
        run(TOOLS / "apksigner", "sign", "--ks", key, "--ks-key-alias", "fijipocket",
            "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", signed, aligned)
        run(TOOLS / "apksigner", "verify", "--verbose", signed)
        run(TOOLS / "zipalign", "-c", "4", signed)
        with zipfile.ZipFile(signed) as apk:
            for name, data in demos.items():
                if apk.read(name) != data:
                    raise SystemExit(f"APK is missing the complete bundled demo: {name}")
        print("Verified all four offline demos, thumbnails, and credits inside APK.", flush=True)
        signed.replace(OUTPUT)
    print(f"Built {OUTPUT} ({OUTPUT.stat().st_size / 1024:.1f} KB)", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", choices=("phone", "tablet"),
                        help="Install and open the app on an already running emulator")
    args = parser.parse_args()
    build()
    if args.install:
        subprocess.run([str(ROOT / "android"), args.install, "install", str(OUTPUT)], check=True)
        subprocess.run([str(ROOT / "android"), args.install, "adb", "shell", "am", "start",
                        "-W", "-n", COMPONENT], check=True)
