import urllib.request, sys
url = "https://mirrors.aliyun.com/mysql/MySQL-8.0/mysql-8.0.28-winx64.zip"
dst = r"C:\Users\Administrator\Downloads\mysql-8.0.28-winx64.zip"
print("downloading...", flush=True)
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req, timeout=120) as r, open(dst, "wb") as f:
    total = 0
    while True:
        chunk = r.read(1024*256)
        if not chunk: break
        f.write(chunk)
        total += len(chunk)
        print(f"\r{total/1024/1024:.1f} MB", end="", flush=True)
print("\nDONE", dst)
