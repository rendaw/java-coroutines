#!/usr/bin/env python3
import argparse
import re
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('version')
args = parser.parse_args()
if not re.match('\\d+\\.\\d+\\.\\d+', args.version):
    args.error('version must be in the format N.N.N')


def replace(path, *replacements):
    with open(path, 'r') as source:
        text = source.read()
        for a, b in replacements:
            text = re.sub(a, b, text, flags=re.S | re.M)
    temp = '{}.1'.format(path)
    with open(temp, 'w') as dest:
        dest.write(text)
    shutil.move(temp, path)


mvnver = [
    '<artifactId>coroutines</artifactId>([^<]*)<version>[^<]+</version>',  # noqa
    '<artifactId>coroutines</artifactId>\\1<version>{}</version>'.format(args.version),  # noqa
]
replace('readme.md', mvnver)
replace('pom.xml', mvnver)