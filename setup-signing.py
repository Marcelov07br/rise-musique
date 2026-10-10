"""Create the app's stable signing key locally (already done once on Marce's PC; never rerun to replace it). Never commit signing/."""
import json
import secrets
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent
folder = root / "signing"
folder.mkdir(exist_ok=True)
key = folder / "rise-musique-gpt.jks"
settings = folder / "credentials.json"
if key.exists() or settings.exists():
    raise SystemExit("Signing files already exist; refusing to replace the key.")
password = secrets.token_urlsafe(32)
config = {"RISE_KEYSTORE": str(key), "RISE_STORE_PASSWORD": password,
          "RISE_KEY_ALIAS": "rise-musique-gpt", "RISE_KEY_PASSWORD": password}
import os
env = os.environ.copy()
env["RISE_KEYGEN_PASSWORD"] = password
tool = Path(r"C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot\bin\keytool.exe")
subprocess.run([str(tool), "-genkeypair", "-keystore", str(key), "-storetype", "JKS",
                "-storepass:env", "RISE_KEYGEN_PASSWORD", "-keypass:env", "RISE_KEYGEN_PASSWORD",
                "-alias", config["RISE_KEY_ALIAS"], "-keyalg", "RSA", "-keysize", "3072",
                "-validity", "10000", "-dname", "CN=Rise Musique GPT"], env=env, check=True)
settings.write_text(json.dumps(config, indent=2), encoding="utf-8")
print("Stable key created in signing/. Keep a private backup of both files.")
