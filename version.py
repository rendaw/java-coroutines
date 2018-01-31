#!/usr/bin/env python3
import argparse
import subprocess
import re
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('version')
args = parser.parse_args()
if not re.match('\\d+\\.\\d+\\.\\d+', args.version):
    args.error('version must be in the format N.N.N')
subprocess.check_call([
    'mvn',
    'versions:set',
    '-DnewVersion={}'.format(args.version),
    '-DgenerateBackupPoms=false',
])
with open('readme.md', 'r') as source:
    text = source.read()
    text = re.sub(
        '<artifactId>coroutines</artifactId>([^<]*)<version>[^<]+</version>',  # noqa
        '<artifactId>coroutines</artifactId>\\1<version>{}</version>'.format(args.version),  # noqa
        text,
        flags=re.S | re.M,
    )
with open('readme.md.1', 'w') as dest:
    dest.write(text)
shutil.move('readme.md.1', 'readme.md')