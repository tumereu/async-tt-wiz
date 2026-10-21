#!/usr/bin/env python3
"""Compile the documentation-only UI review. No app or cloud generation."""

import asyncio
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess

from playwright.async_api import async_playwright
from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'docs/design/02-experience/ui'


def validate_images():
    manifest = json.loads((UI / 'manifest.json').read_text())
    markdown = (UI / 'review.md').read_text()
    for screen in manifest['screens']:
        for orientation, expected in [('portrait', (412, 892)),
                                      ('landscape', (892, 412))]:
            relative = f"images/{screen['slug']}-{orientation}.png"
            path = UI / relative
            data = path.read_bytes()
            if data[:8] != b'\x89PNG\r\n\x1a\n':
                raise ValueError(f'Not a PNG: {relative}')
            dimensions = struct.unpack('>II', data[16:24])
            if dimensions != expected:
                raise ValueError(f'{relative}: {dimensions} != {expected}')
            if relative not in markdown:
                raise ValueError(f'Image not embedded in Markdown: {relative}')
        if not (UI / screen['source']).is_file():
            raise ValueError(f"Missing source for {screen['id']}")
    return len(manifest['screens'])


async def compile_pdf(chrome):
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(executable_path=chrome)
        page = await browser.new_page()
        failures = []
        page.on('pageerror', lambda error: failures.append(str(error)))
        manifest = json.loads((UI / 'manifest.json').read_text())
        base = os.environ.get('UI_REVIEW_BASE_URL') or manifest.get('reviewBaseUrl')
        if base:
            # Fulfill from disk at the publication URL: PDF links must not point
            # at local file:///home/... paths that a phone cannot open.
            target = base.rstrip('/') + '/review.html'

            async def document(route):
                await route.fulfill(content_type='text/html', body=(UI / 'review.html').read_text())

            await page.route(target, document)
            await page.goto(target, wait_until='networkidle')
        else:
            await page.goto((UI / 'review.html').as_uri(), wait_until='networkidle')
        await page.evaluate('document.fonts.ready')
        await page.evaluate('Promise.all([...document.images].map(i => i.decode()))')
        missing = await page.locator('img').evaluate_all(
            '(images) => images.filter(i => !i.complete || !i.naturalWidth).map(i => i.alt)'
        )
        if failures or missing:
            raise ValueError(f'HTML errors: {failures}; broken images: {missing}')
        await page.pdf(path=str(UI / 'review.pdf'), format='A4',
                       print_background=True, prefer_css_page_size=True,
                       display_header_footer=True,
                       header_template='<span></span>',
                       footer_template='<div style="font-size:8px;width:100%;text-align:center;color:#666">'
                                        'Async Tabletop Wizards · UI review · '
                                       '<span class="pageNumber"></span> / '
                                       '<span class="totalPages"></span></div>')
        await page.set_viewport_size({'width': 412, 'height': 892})
        await page.emulate_media(media='screen')
        overflow = await page.evaluate(
            'document.documentElement.scrollWidth > window.innerWidth'
        )
        if overflow:
            raise ValueError('Review HTML overflows the phone viewport horizontally')
        await browser.close()


def main():
    count = validate_images()
    pandoc = shutil.which('pandoc')
    chrome = os.environ.get('CHROME_PATH') or shutil.which('google-chrome') or shutil.which('chromium')
    if not pandoc or not chrome:
        raise SystemExit('Install Pandoc and Google Chrome/Chromium first.')
    subprocess.run([
        pandoc, 'review.md', '--from=markdown+fenced_divs+implicit_figures',
        '--to=html5', '--standalone', '--embed-resources', '--toc', '--toc-depth=2',
        '--metadata=title:Async Tabletop Wizards — UI review including gameplay',
        '--css=review.css', '--output=review.html',
    ], cwd=UI, check=True)
    asyncio.run(compile_pdf(chrome))
    reader = PdfReader(UI / 'review.pdf')
    image_count = sum(len(page.images) for page in reader.pages)
    if image_count < count * 2:
        raise ValueError(f'PDF contains only {image_count} images; expected at least {count * 2}')
    text = '\n'.join(page.extract_text() or '' for page in reader.pages)
    manifest = json.loads((UI / 'manifest.json').read_text())
    for screen in manifest['screens']:
        if screen['id'] not in text:
            raise ValueError(f"PDF missing screen ID: {screen['id']}")
    print(f'Validated {count} screens / {count * 2} images, '
          f'phone HTML and {len(reader.pages)} PDF pages.')
    print(UI / 'review.html')
    print(UI / 'review.pdf')


if __name__ == '__main__':
    main()
