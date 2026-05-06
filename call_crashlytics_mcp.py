import subprocess
import json
import sys

def call_mcp_tool(tool_name, arguments):
    process = subprocess.Popen(
        ['npx', '-y', 'firebase-tools@latest', 'mcp'],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd='android'
    )
    
    # ... (rest of initialization)
    init_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "manual-client", "version": "1.0.0"}
        }
    }
    
    process.stdin.write(json.dumps(init_request) + "\n")
    process.stdin.flush()
    
    while True:
        line = process.stdout.readline()
        if not line: break
        try:
            resp = json.loads(line)
            if resp.get('id') == 1:
                break
        except: continue

    # List tools
    list_request = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
        "params": {}
    }
    
    process.stdin.write(json.dumps(list_request) + "\n")
    process.stdin.flush()

    # ... (rest of the script)
    while True:
        line = process.stdout.readline()
        if not line: break
        try:
            resp = json.loads(line)
            if resp.get('id') == 2:
                print(json.dumps(resp, indent=2))
                break
        except: 
            print(f"DEBUG: {line.strip()}")
            continue

    # Also check stderr
    stderr_out = process.stderr.read()
    if stderr_out:
        print(f"STDERR: {stderr_out}")

    process.terminate()

if __name__ == "__main__":
    app_id = "1:1066388535658:android:4a69035d8571d91d5a0d13"
    call_mcp_tool("crashlytics-list-top-issues", {"appId": app_id})
