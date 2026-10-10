import os
import sys
import shutil
import zipfile
import urllib.request
import tomllib
import hashlib
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PACK_DIR = os.path.join(ROOT, "pack")
DIST_DIR = os.path.join(ROOT, "dist")
WORK_DIR = os.path.join(DIST_DIR, "tmp_prism_build")
INST_DIR = os.path.join(WORK_DIR, "Skycraft")
MC_DIR = os.path.join(INST_DIR, ".minecraft")
OUT_ZIP = os.path.join(DIST_DIR, "Skycraft-client-instance.zip")

MC_VERSION = "1.20.1"
FORGE_VERSION = "47.4.10"

HEADERS = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}

CACHE_DIR = os.path.join(DIST_DIR, ".cache")

def download_file(target_path, url, expected_hash=None, hash_format=None):
    os.makedirs(CACHE_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(target_path), exist_ok=True)
    cache_key = hashlib.sha256(url.encode()).hexdigest()[:16] + "_" + os.path.basename(target_path)
    cache_path = os.path.join(CACHE_DIR, cache_key)
    
    if os.path.exists(cache_path):
        shutil.copyfile(cache_path, target_path)
        return target_path, True

    temp_path = cache_path + ".tmp"
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req) as resp, open(temp_path, "wb") as out:
        shutil.copyfileobj(resp, out)
    
    if expected_hash and hash_format:
        with open(temp_path, "rb") as fp:
            data = fp.read()
            if hash_format == "sha512":
                calc = hashlib.sha512(data).hexdigest()
            elif hash_format == "sha1":
                calc = hashlib.sha1(data).hexdigest()
            elif hash_format == "sha256":
                calc = hashlib.sha256(data).hexdigest()
            else:
                calc = None
            if calc and calc.lower() != expected_hash.lower():
                os.remove(temp_path)
                raise ValueError(f"Hash mismatch for {target_path}: expected {expected_hash}, got {calc}")
                
    os.replace(temp_path, cache_path)
    shutil.copyfile(cache_path, target_path)
    return target_path, False

def build_prism_package():
    print("=== Building Full Skycraft Modpack for Prism Launcher ===")
    
    # 1. Clean and prepare directories
    if os.path.exists(WORK_DIR):
        shutil.rmtree(WORK_DIR)
    os.makedirs(MC_DIR, exist_ok=True)
    os.makedirs(DIST_DIR, exist_ok=True)
    
    # 2. Copy configs, shaderpacks, and local mods from pack/
    print("\n[1/5] Copying local overrides (configs, options, scripts)...")
    pack_config = os.path.join(PACK_DIR, "config")
    if os.path.exists(pack_config):
        shutil.copytree(pack_config, os.path.join(MC_DIR, "config"))
    
    # 3. Copy Skycraft Core mod
    print("\n[2/5] Installing Skycraft Core...")
    mc_mods_dir = os.path.join(MC_DIR, "mods")
    os.makedirs(mc_mods_dir, exist_ok=True)
    
    skycraft_core_source = os.path.join(ROOT, "mod", "build", "libs", "skycraft-0.1.0.jar")
    if not os.path.exists(skycraft_core_source):
        skycraft_core_source = os.path.join(PACK_DIR, "mods", "skycraft-core.jar")
    
    dest_core = os.path.join(mc_mods_dir, "skycraft-core.jar")
    shutil.copyfile(skycraft_core_source, dest_core)
    print(f"  Installed {dest_core} ({os.path.getsize(dest_core)/(1024*1024):.1f} MB)")
    
    # 4. Parse all .pw.toml files
    print("\n[3/5] Parsing pack metadata (.pw.toml)...")
    download_tasks = []
    
    for root, dirs, files in os.walk(PACK_DIR):
        for f in files:
            if not f.endswith(".pw.toml"):
                continue
            pw_path = os.path.join(root, f)
            rel_dir = os.path.relpath(root, PACK_DIR)  # e.g. "mods" or "shaderpacks"
            
            with open(pw_path, "rb") as fp:
                d = tomllib.load(fp)
            
            side = d.get("side", "both")
            if side == "server":
                continue  # Skip server-only mods for client instance
                
            fn = d.get("filename")
            download_info = d.get("download", {})
            url = download_info.get("url")
            h = download_info.get("hash")
            h_format = download_info.get("hash-format")
            
            # If no direct download url, check CurseForge
            if not url:
                cf = d.get("update", {}).get("curseforge", {})
                pid = cf.get("project-id")
                fid = cf.get("file-id")
                if pid and fid:
                    fid_s = str(fid)
                    url = f"https://edge.forgecdn.net/files/{fid_s[:4]}/{fid_s[4:]}/{fn}"
                    
            if not url:
                print(f"WARNING: No download URL found for {f}")
                continue
                
            target_path = os.path.join(MC_DIR, rel_dir, fn)
            download_tasks.append((d.get("name", fn), target_path, url, h, h_format))
            
    print(f"  Found {len(download_tasks)} files to download.")
    
    # 5. Download mods and shaderpacks in parallel
    print("\n[4/5] Downloading mods and assets...")
    success = 0
    failed = []
    
    def worker(task):
        name, target_path, url, h, h_fmt = task
        try:
            path, cached = download_file(target_path, url, h, h_fmt)
            size = os.path.getsize(path) / (1024 * 1024)
            print(f"  [OK] {name} ({size:.2f} MB)")
            return True, name
        except Exception as e:
            print(f"  [FAIL] {name}: {e}")
            return False, f"{name}: {e}"
            
    with ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(worker, download_tasks))
        
    for ok, res in results:
        if ok:
            success += 1
        else:
            failed.append(res)
            
    if failed:
        print(f"\nDownload Errors ({len(failed)}):")
        for err in failed:
            print("  -", err)
        raise RuntimeError("One or more files failed to download.")
        
    print(f"\nAll {success} dependencies successfully downloaded.")
    
    # 6. Generate Prism / MultiMC instance descriptors
    print("\n[5/5] Generating Prism Launcher instance descriptor...")
    instance_cfg = f"""InstanceType=OneSix
name=Skycraft
iconKey=default
notes=Skycraft: The Elder Scrolls V Skyrim Total Conversion for Minecraft 1.20.1 Forge 47.4.10
JvmArgs=-Xms4G -Xmx8G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M
OverrideMemory=true
MinMemAlloc=4096
MaxMemAlloc=8192
"""
    with open(os.path.join(INST_DIR, "instance.cfg"), "w", encoding="utf-8") as f:
        f.write(instance_cfg)

    mmc_pack = f"""{{
  "components": [
    {{"uid": "net.minecraft", "version": "{MC_VERSION}", "important": true}},
    {{"uid": "net.minecraftforge", "version": "{FORGE_VERSION}"}}
  ],
  "formatVersion": 1
}}
"""
    with open(os.path.join(INST_DIR, "mmc-pack.json"), "w", encoding="utf-8") as f:
        f.write(mmc_pack)

    # 7. Package instance zip
    print(f"\nCreating Prism Launcher instance zip: {OUT_ZIP} ...")
    if os.path.exists(OUT_ZIP):
        os.remove(OUT_ZIP)
        
    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(INST_DIR):
            for f in files:
                full_p = os.path.join(root, f)
                rel_p = os.path.relpath(full_p, WORK_DIR)
                zf.write(full_p, rel_p)

    final_size_mb = os.path.getsize(OUT_ZIP) / (1024 * 1024)
    print(f"\n=======================================================")
    print(f"SUCCESS! Modpack for Prism Launcher ready:")
    print(f"File: {OUT_ZIP}")
    print(f"Size: {final_size_mb:.2f} MB")
    print(f"To play: In Prism Launcher, click 'Add Instance' -> 'Import' -> select this zip!")
    print(f"=======================================================")
    
    # Cleanup work directory
    shutil.rmtree(WORK_DIR, ignore_errors=True)

if __name__ == "__main__":
    build_prism_package()

