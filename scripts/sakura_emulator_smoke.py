"""Release gate: both package identities install and the independent UI survives relaunch/font scaling."""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

out = Path('smoke-results'); out.mkdir(exist_ok=True)
def adb(*args): return subprocess.check_output(['adb', *args], text=True, stderr=subprocess.STDOUT)
def capture(name):
    subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/window.xml'], check=True, capture_output=True)
    xml = adb('shell', 'cat', '/sdcard/window.xml'); (out / f'{name}.xml').write_text(xml)
    with (out / f'{name}.png').open('wb') as file: subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=file, check=True)
    return ET.fromstring(xml)
def tap_text(root, text):
    import re
    node = next((node for node in root.iter('node') if node.get('text') == text or node.get('content-desc') == text), None)
    if node is None: return False
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2)//2), str((y1 + y2)//2)); time.sleep(1)
    return True
adb('logcat', '-c')
for file in ['SakuraPatcher-release.apk', 'SakuraPatcher-legacy-migration.apk']:
    result = adb('install', '-r', file); assert 'Success' in result, result
for pkg in ['app.sakura.patcher', 'app.morphe.manager']:
    adb('shell', 'pm', 'grant', pkg, 'android.permission.POST_NOTIFICATIONS')
    adb('shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1')
    time.sleep(5)
    assert adb('shell', 'pidof', pkg).strip(), f'{pkg} exited after launch'
    capture(pkg)
# The two applications must coexist, and the independent package must relaunch after process death.
adb('shell', 'am', 'force-stop', 'app.sakura.patcher')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
adb('shell', 'monkey', '-p', 'app.sakura.patcher', '-c', 'android.intent.category.LAUNCHER', '1')
time.sleep(4)
root = capture('large-font-home')
for label in ['Skip', 'Not now', 'Later', 'Close']:
    if tap_text(root, label): root = capture('after-onboarding')
assert tap_text(root, 'Settings'), 'Settings navigation is not accessible at font scale 1.5'
if True:
    root = capture('settings')
    workspace_opened = False
    for _ in range(8):
        if tap_text(root, 'Build workspace'):
            screen = capture('build-workspace')
            assert any('Your next patch' in node.get('text', '') or node.get('text') == 'Import recipe' for node in screen.iter('node')), 'Build workspace content did not open'
            workspace_opened = True
            break
        adb('shell', 'input', 'swipe', '500', '1500', '500', '500', '400'); time.sleep(0.5)
        root = capture('settings-scroll')
assert workspace_opened, 'Build workspace navigation failed'
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
log = adb('logcat', '-d'); (out / 'logcat.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log, 'App crashed; inspect smoke logcat'
assert adb('shell', 'pidof', 'app.sakura.patcher').strip()
(out / 'summary.txt').write_text('Both package identities installed and launched. Independent package survived force-stop/relaunch and font scale 1.5. UI captures included. No fatal exception observed.\n')
