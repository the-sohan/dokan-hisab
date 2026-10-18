"""End-to-end smoke check on a dedicated, disposable Android emulator."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path(os.environ['ANDROID_SDK_ROOT']) / 'platform-tools/adb')
PACKAGE = 'bd.dokanhisab.app'

def adb(*args):
    return subprocess.check_output([ADB, *args], stderr=subprocess.STDOUT)

def nodes():
    for attempt in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/window.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(.5)
    raise AssertionError('Could not inspect UI')

def matching(label=None, cls=None):
    return [n for n in nodes() if (label is None or n.get('text') == label) and (cls is None or n.get('class') == cls)]

def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.25)

def tap(label):
    found=matching(label)
    assert found, f'UI text not found: {label}'
    tap_node(found[0])

def fill(value,index=0):
    tap_node(matching(cls='android.widget.EditText')[index])
    adb('shell','input','keyevent','123',*(['67']*25))
    if value:
        adb('shell','input','text',value)

def expect(label):
    assert matching(label), f'Expected UI text: {label}'

def launch():
    adb('shell','am','start','-n',PACKAGE+'/.MainActivity')
    time.sleep(.7)

launch()
tap('＋  নতুন কাস্টমার')
tap('সেভ করুন')
expect('কাস্টমারের নাম লিখুন।')
fill('UI-Test-Rahim')
fill('01712345678',1)
tap('সেভ করুন')
expect('UI-Test-Rahim')
tap('＋ বাকি')
fill('1000.50')
tap('সেভ করুন')
expect('৳ ১,০০০.৫০')
tap('✓ পরিশোধ')
fill('300.25')
tap('সেভ করুন')
expect('৳ ৭০০.২৫')
tap('পরিশোধ করেছেন')
tap('সংশোধন করুন')
fill('500')
tap('সেভ করুন')
expect('৳ ৫০০.৫০')
tap('পরিশোধ করেছেন')
tap('মুছে ফেলুন')
tap('মুছে ফেলুন')
expect('৳ ১,০০০.৫০')
adb('shell','am','force-stop',PACKAGE)
launch()
tap('UI-Test-Rahim')
expect('৳ ১,০০০.৫০')
tap('সম্পাদনা')
fill('UI-Test-Karim')
tap('সেভ করুন')
expect('UI-Test-Karim')
tap('সম্পাদনা')
tap('কাস্টমার মুছুন')
tap('মুছে ফেলুন')
expect('আপনার প্রথম কাস্টমার যোগ করুন')
print('PASS: UI validation, customer creation/edit/delete, credit, payment, payment edit/delete, and persistence after process restart')
