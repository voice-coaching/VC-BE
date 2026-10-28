"""Stop only the isolated test resources when the host loses its safety margin."""
import json
from pathlib import Path
import shutil
import subprocess

def stop_needed(memory_available_mib, disk_free_bytes, backend_failed):
    return memory_available_mib < 128 or disk_free_bytes < 512 * 1024 * 1024 or backend_failed

def run():
    memory = next(int(row.split()[1]) // 1024 for row in Path('/proc/meminfo').read_text().splitlines() if row.startswith('MemAvailable:'))
    failed = subprocess.run(['systemctl', 'is-failed', '--quiet', 'alpha-payment-test.service'], capture_output=True).returncode == 0
    if stop_needed(memory, shutil.disk_usage('/').free, failed):
        subprocess.run(['systemctl', 'stop', 'alpha-payment-test.service'], capture_output=True, timeout=45)
        subprocess.run(['docker', 'stop', 'alpha-payment-test-db'], capture_output=True, timeout=45)
        print(json.dumps({'test_resources_stopped': True, 'available_memory_mib': memory}))

if __name__ == '__main__':
    run()
