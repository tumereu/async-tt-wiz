#!/usr/bin/env python3
"""Capture the inert UI design sources offline, without contacting Stitch."""

import argparse
import asyncio
import json
import os
from pathlib import Path
import shutil

from playwright.async_api import async_playwright


ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'docs/design/02-experience/ui'
VIEWPORTS = {'portrait': (412, 892), 'landscape': (892, 412)}


async def validate_owner_revision(page, screen, orientation):
    """Regression checks for the confirmed D-292 static review presentation."""
    issues = await page.evaluate('''({id, orientation}) => {
        const issues = [];
        const visible = e => !!(e && e.getBoundingClientRect().width && e.getBoundingClientRect().height);
        const bars = '.status-bar,.status-bar-inset,.status,.safe-inset-bottom,.bottom-safe-inset,' +
            '.safe-area-bottom,.bottom-safe-area,.safe-area,.safe-inset,.safe,.bottom-inset,.safe-inset-bar';
        if ([...document.querySelectorAll(bars)].some(visible)) issues.push('Visible system-bar allowance');
        if (/Draft [A-Z]0[0-9]/.test(document.body.innerText)) issues.push('Visible mock title-bar badge');
        const nav = document.querySelector('.review-navigation');
        if (document.body.classList.contains('review-authenticated')) {
            if (!nav) issues.push('Missing shared navigation');
            else {
                const categories = [...nav.querySelectorAll('.navigation-category')].map(e=>e.textContent.trim());
                if (categories.join('|') !== 'Menu|Games') issues.push('Wrong top-level categories');
                const menuChildren = [...nav.querySelectorAll('.navigation-child:not(.navigation-game)')].map(e=>e.textContent.trim());
                if (menuChildren.join('|') !== 'Manage decks|Contacts') issues.push('Wrong Menu subitems');
                const persistent = orientation === 'landscape' && !document.body.classList.contains('review-gameplay');
                if (visible(nav) !== (persistent || document.body.classList.contains('review-drawer-open')))
                    issues.push('Wrong drawer/persistent presentation');
                if (visible(nav)) {
                    const footer = nav.querySelector('.navigation-footer');
                    const scroll = nav.querySelector('.navigation-scroll');
                    if (!footer || scroll.contains(footer)) issues.push('Logout inside scrolling list');
                    else {
                        const before = footer.getBoundingClientRect();
                        const filler = document.createElement('div'); filler.style.height='2000px';
                        scroll.append(filler); scroll.scrollTop=scroll.scrollHeight;
                        const after = footer.getBoundingClientRect();
                        if (before.y !== after.y || after.bottom > innerHeight) issues.push('Logout footer moved/clipped');
                        filler.remove(); scroll.scrollTop=0;
                    }
                }
            }
        }
        if (id === 'A01') {
            const card = document.querySelector('.form-panel').getBoundingClientRect();
            if (Math.abs(card.width - 380) > 1 || Math.abs(card.x + card.width/2 - innerWidth/2) > 1)
                issues.push('Login card changed size or is not centered');
            if (getComputedStyle(document.querySelector('.screen-layout')).flexDirection !== 'column')
                issues.push('Split sign-in composition');
            for (const text of ['Async Tabletop Wizards', 'Specialized tabletop simulator for asynchronous wizardry'])
                if (!document.querySelector('.form-panel').innerText.includes(text)) issues.push('Missing exact branding: '+text);
        }
        if (['A03','A04'].includes(id) && /sessions|signed in|sign in again/i.test(document.body.innerText))
            issues.push('Reset-session explanatory copy returned');
        if (id === 'N01') {
            const content = document.querySelector('main').innerText;
            if (/Logout|notification|two.player|Import and organise|Find players by username/i.test(content))
                issues.push('Main-menu utility/subtitle clutter returned');
        }
        if (id === 'P01') {
            const sections = [...document.querySelectorAll('main .section-title')].map(e=>e.textContent.trim());
            if (sections.join('|') !== 'Games|Invitations') issues.push('Browser split/order regressed');
        }
        return issues;
    }''', {'id': screen['id'], 'orientation': orientation})
    if issues:
        raise ValueError(f"{screen['id']} {orientation}: {issues}")


async def main(screen_ids):
    manifest = json.loads((UI / 'manifest.json').read_text())
    known_ids = {screen['id'] for screen in manifest['screens']}
    if set(screen_ids) - known_ids:
        raise ValueError(f'Unknown screen IDs: {sorted(set(screen_ids) - known_ids)}')
    chrome = os.environ.get('CHROME_PATH') or shutil.which('google-chrome') or shutil.which('chromium')
    if not chrome:
        raise SystemExit('Install Google Chrome/Chromium first.')
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(executable_path=chrome)
        for screen in manifest['screens']:
            if screen_ids and screen['id'] not in screen_ids:
                continue
            context = await browser.new_context()
            blocked = []

            async def block(route):
                blocked.append(route.request.url)
                await route.abort()

            await context.route('http://**/*', block)
            await context.route('https://**/*', block)
            for orientation, (width, height) in VIEWPORTS.items():
                page = await context.new_page()
                await page.set_viewport_size({'width': width, 'height': height})
                await page.goto((UI / screen['source']).as_uri(), wait_until='networkidle')
                await page.evaluate('document.fonts.ready')
                if screen.get('captureScroll') == 'bottom':
                    await page.evaluate('''() => {
                        document.querySelectorAll('*').forEach(e=>{
                            if(['auto','scroll'].includes(getComputedStyle(e).overflowY)) {
                                e.scrollTop=e.scrollHeight;
                            }
                        });
                    }''')
                state = await page.evaluate('''() => ({
                    inert: document.body.inert,
                    scripts: document.scripts.length,
                    width: document.documentElement.scrollWidth,
                    fonts: [...document.fonts].filter(f=>f.status==='error').map(f=>f.family)
                })''')
                if not state['inert'] or state['scripts'] or state['width'] > width or state['fonts']:
                    raise ValueError(f"{screen['id']} {orientation}: {state}")
                await validate_owner_revision(page, screen, orientation)
                audit = await page.evaluate('''() => ({
                    documentWidth:document.documentElement.scrollWidth,viewportWidth:innerWidth,
                    fonts:[...document.fonts].filter(f=>f.status==='loaded').map(f=>({family:f.family,status:f.status})),
                    controls:[...document.querySelectorAll('button,input:not([type="hidden"]),a[href],[role="button"]')]
                    .filter(e=>{const c=getComputedStyle(e),r=e.getBoundingClientRect();return c.display!=='none'&&c.visibility!=='hidden'&&r.width&&r.height;})
                    .map(e=>{
                        const r=e.getBoundingClientRect();let scrollAncestor=false;
                        for(let p=e.parentElement;p;p=p.parentElement){
                            const c=getComputedStyle(p);
                            if(['auto','scroll'].includes(c.overflowY)&&p.scrollHeight>p.clientHeight)scrollAncestor=true;
                        }
                        return {text:(e.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.tagName).trim().slice(0,120),
                            x:Math.round(r.x),y:Math.round(r.y),width:Math.round(r.width),height:Math.round(r.height),
                            scrollAncestor,inViewport:r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth};
                    })
                })''')
                screen.setdefault('audit', {})[orientation] = audit
                bad = [c for c in audit['controls'] if c['width'] < 48 or c['height'] < 48
                       or (not c['inViewport'] and not c['scrollAncestor'])]
                if bad:
                    raise ValueError(f"{screen['id']} {orientation}: unreachable/undersized controls: {bad}")
                await page.screenshot(path=str(UI / f"images/{screen['slug']}-{orientation}.png"))
                await page.close()
            if blocked:
                raise ValueError(f"{screen['id']}: unexpected remote assets: {blocked}")
            await context.close()
            print(f"Captured {screen['id']} in both orientations.")
        await browser.close()
    (UI / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('screen_ids', nargs='*', help='Capture only these IDs; default: all')
    asyncio.run(main(parser.parse_args().screen_ids))
