#!/system/bin/sh

MODDIR=${0%/*}

LOG_FILE="$MODDIR/sui.log"
TAG="SuiDaemon"

print_log() {
    echo "[$(date)] $1" >> "$LOG_FILE"
    log -p i -t "$TAG" "$1"
}

get_sui_pids() {
    if command -v pidof >/dev/null 2>&1; then
        pidof sui 2>/dev/null
        return
    fi

    ps -A 2>/dev/null | awk '$NF == "sui" { print $2 }'
}

is_sui_running() {
    [ -n "$(get_sui_pids)" ]
}

start_sui() {
    chmod 700 "$MODDIR/bin/sui" 2>/dev/null
    nohup "$MODDIR/bin/sui" "$MODDIR" 0 >> "$LOG_FILE" 2>&1 &
}

LOCK_DIR="/data/adb/sui/watchdog.lock"
LOCK_PID="$LOCK_DIR/pid"

(
    mkdir -p "/data/adb/sui" 2>/dev/null

    if mkdir "$LOCK_DIR" 2>/dev/null; then
        echo "$$" > "$LOCK_PID" 2>/dev/null
    else
        if [ -f "$LOCK_PID" ]; then
            old_pid="$(cat "$LOCK_PID" 2>/dev/null)"
            if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
                exit 0
            fi
        fi
        rm -rf "$LOCK_DIR" 2>/dev/null
        mkdir "$LOCK_DIR" 2>/dev/null && echo "$$" > "$LOCK_PID" 2>/dev/null
    fi

    backoff=1
    backoff_max=60
    interval=5

    while true; do
        if is_sui_running; then
            backoff=1
            sleep "$interval"
            continue
        fi

        print_log "Sui daemon is not running, restarting..."
        start_sui
        sleep 2

        if is_sui_running; then
            pids="$(get_sui_pids)"
            print_log "Sui daemon is running (pid: $pids)"
            backoff=1
            sleep "$interval"
            continue
        fi

        print_log "Sui daemon still not running, retry in ${backoff}s"
        sleep "$backoff"
        if [ "$backoff" -lt "$backoff_max" ]; then
            backoff=$((backoff * 2))
            if [ "$backoff" -gt "$backoff_max" ]; then
                backoff="$backoff_max"
            fi
        fi
    done
) >/dev/null 2>&1 &

exit 0
