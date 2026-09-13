#!/usr/bin/env python3
"""
SilentService Notification Client
Mirrors Android notifications to your PC terminal.

Usage:
    python notify_client.py <PHONE_IP> [PORT]

Commands:
    l              - list / refresh active notifications
    t              - send test notification to phone
    s              - check listener status on phone
    r <#> <text>   - reply to notification #
    a <#> <n>      - fire action n on notification #
    d <#>          - dismiss notification #
    q              - quit
"""
import socket, json, threading, sys, time
from datetime import datetime

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
CYAN = "\033[96m"
GREEN = "\033[92m"
YELLOW = "\033[93m"
RED = "\033[91m"
MAGENTA = "\033[95m"

PORT = 5556
IP = "127.0.0.1"

notifications = {}
key_to_index = {}
next_index = [1]
lock = threading.Lock()
sock = [None]
running = [True]

def ts():
    return datetime.now().strftime("%H:%M:%S")

def print_notif(data):
    with lock:
        key = data["key"]
        if key in key_to_index:
            idx = key_to_index[key]
        else:
            idx = next_index[0]
            next_index[0] += 1
            key_to_index[key] = idx
        notifications[idx] = data

    app = data.get("app", data.get("package", "?"))
    title = data.get("title", "")
    text = data.get("text", "")
    actions = data.get("actions", [])

    print(f"\n{BOLD}{CYAN}[#{idx}] {app}{RESET}  {DIM}{ts()}{RESET}")
    if title:
        print(f"  {BOLD}{title}{RESET}")
    if text:
        print(f"  {text}")
    if actions:
        print(f"  {DIM}Actions:{RESET}")
        for a in actions:
            t = f"{YELLOW}[reply]{RESET}" if a["type"] == "reply" else f"{GREEN}[action]{RESET}"
            print(f"    {a['index']}: {t} {a['label']}")
    print()

def list_notifs():
    with lock:
        if not notifications:
            print(f"\n{DIM}No active notifications recorded yet.{RESET}")
            print(f"{DIM}Tip: Type 't' to send a test notification or 's' to check status.{RESET}\n")
            return
        print(f"\n{BOLD}Active notifications ({len(notifications)}):{RESET}")
        for idx, data in sorted(notifications.items()):
            app = data.get("app", "?")
            title = data.get("title", "")
            text = data.get("text", "")[:70]
            print(f"  {CYAN}#{idx}{RESET} {BOLD}{app}{RESET} — {title}: {DIM}{text}{RESET}")
        print()

def send_json(obj):
    try:
        if sock[0]:
            sock[0].sendall((json.dumps(obj) + "\n").encode())
    except Exception as e:
        print(f"{RED}Send error: {e}{RESET}")

def handle_status(data):
    listener_active = data.get("listener_active", False)
    active_count = data.get("active_notifications", 0)
    if listener_active:
        print(f"{GREEN}✓ Phone notification listener is ACTIVE ({active_count} active notifications){RESET}")
    else:
        print(f"\n{RED}{BOLD}⚠️  WARNING: Notification Access is NOT active on the phone!{RESET}")
        print(f"{YELLOW}Android is not sending notifications to SilentService.{RESET}")
        print(f"{YELLOW}Fix via ADB:{RESET}")
        print(f"  adb shell cmd notification allow_listener com.jp1828.utils/com.jp1828.utils.NotificationListener")
        print(f"{YELLOW}Or in BlueStacks / Phone UI:{RESET}")
        print(f"  Open SilentService -> Tap 'Grant Notification Access' -> Turn ON\n")

def recv_loop():
    buf = ""
    while running[0]:
        try:
            chunk = sock[0].recv(4096).decode(errors="replace")
            if not chunk:
                break
            buf += chunk
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                except Exception as ex:
                    print(f"{DIM}[Debug] Raw unparsed: {line[:100]} ({ex}){RESET}")
                    continue

                t = data.get("type")
                if t == "notification":
                    print_notif(data)
                elif t == "status":
                    handle_status(data)
                elif t == "notification_removed":
                    key = data.get("key", "")
                    with lock:
                        idx = key_to_index.pop(key, None)
                        if idx:
                            notifications.pop(idx, None)
                            print(f"{DIM}[{ts()}] Notification #{idx} dismissed{RESET}")
                elif t == "ping":
                    send_json({"type": "pong"})
        except Exception as e:
            if running[0]:
                print(f"{RED}Connection lost: {e}{RESET}")
            break

def input_loop():
    print(f"{DIM}Commands: l=list  t=test  s=status  r <#> <text>=reply  a <#> <n>=action  d <#>=dismiss  q=quit{RESET}\n")
    while running[0]:
        try:
            line = input()
        except EOFError:
            break
        parts = line.strip().split(None, 2)
        if not parts:
            continue
        cmd = parts[0].lower()
        if cmd == "q":
            running[0] = False
            try:
                sock[0].close()
            except:
                pass
            sys.exit(0)
        elif cmd == "l":
            send_json({"type": "refresh"})
            time.sleep(0.3)
            list_notifs()
        elif cmd == "s":
            send_json({"type": "status"})
        elif cmd == "t":
            print(f"{YELLOW}Requesting test notification from phone...{RESET}")
            send_json({"type": "test"})
        elif cmd == "r" and len(parts) >= 3:
            try:
                idx = int(parts[1])
                text = parts[2]
                with lock:
                    data = notifications.get(idx)
                if not data:
                    print(f"{RED}Notification #{idx} not found{RESET}")
                    continue
                ra = next((a for a in data.get("actions", []) if a["type"] == "reply"), None)
                if not ra:
                    print(f"{RED}No reply action available on #{idx}{RESET}")
                    continue
                send_json({
                    "type": "action",
                    "key": data["key"],
                    "action_index": ra["index"],
                    "reply_text": text
                })
                print(f"{GREEN}✓ Reply sent: '{text}'{RESET}")
            except ValueError:
                print(f"{RED}Usage: r <#> <text>{RESET}")
        elif cmd == "a" and len(parts) >= 3:
            try:
                idx = int(parts[1])
                ai = int(parts[2])
                with lock:
                    data = notifications.get(idx)
                if not data:
                    print(f"{RED}Notification #{idx} not found{RESET}")
                    continue
                send_json({
                    "type": "action",
                    "key": data["key"],
                    "action_index": ai
                })
                print(f"{GREEN}✓ Action {ai} sent{RESET}")
            except ValueError:
                print(f"{RED}Usage: a <#> <action_index>{RESET}")
        elif cmd == "d" and len(parts) >= 2:
            try:
                idx = int(parts[1])
                with lock:
                    data = notifications.get(idx)
                if not data:
                    print(f"{RED}Notification #{idx} not found{RESET}")
                    continue
                send_json({"type": "dismiss", "key": data["key"]})
                with lock:
                    key_to_index.pop(data["key"], None)
                    notifications.pop(idx, None)
                print(f"{GREEN}✓ Dismissed #{idx}{RESET}")
            except ValueError:
                print(f"{RED}Usage: d <#>{RESET}")
        else:
            print(f"{DIM}Commands: l (list), t (test), s (status), r <#> <text>, a <#> <n>, d <#>, q (quit){RESET}")

def connect():
    while running[0]:
        try:
            print(f"{YELLOW}Connecting to {IP}:{PORT}...{RESET}")
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect((IP, PORT))
            sock[0] = s
            print(f"{GREEN}✓ Connected to SilentService! Waiting for notifications...{RESET}\n")
            # Request initial status and sync
            s.sendall(b'{"type":"status"}\n{"type":"refresh"}\n')
            t = threading.Thread(target=recv_loop, daemon=True)
            t.start()
            t.join()
            if running[0]:
                print(f"{YELLOW}Reconnecting in 5s...{RESET}")
                time.sleep(5)
        except Exception as e:
            if running[0]:
                print(f"{RED}Connection failed: {e}. Retrying in 5s...{RESET}")
                time.sleep(5)

if __name__ == "__main__":
    if len(sys.argv) >= 2:
        IP = sys.argv[1]
    if len(sys.argv) >= 3:
        PORT = int(sys.argv[2])
    print(f"{BOLD}{MAGENTA}SilentService Notification Client{RESET}")
    print(f"Target: {IP}:{PORT}")
    print(f"{DIM}──────────────────────────────────────────{RESET}")
    threading.Thread(target=connect, daemon=True).start()
    time.sleep(1.0)
    input_loop()
