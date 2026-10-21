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


def review_group_nodes():
    """Resolve literal D-299 document fixtures, not application history grouping."""
    fixture = json.loads((UI / 'group-icon-order.json').read_text())

    def representative(events):
        anchors = [e for e in events if e['role'] == 'anchor']
        pool = anchors or events
        ranks = fixture['anchorRanks' if anchors else 'companionRanks']
        return min(pool, key=lambda e: ranks[e['kind']])  # min preserves first tie.

    nodes = fixture['nodes']
    for node in nodes:
        if node['events']:
            winner = representative(node['events'])
            assert winner['id'] == node['representative'], node['id']
            node.update(icon=winner['icon'], name=winner['name'])
    by_id = {node['id']: node for node in nodes}
    for check in fixture['prefixChecks']:
        events = by_id[check['node']]['events']
        actual = [representative(events[:n])['id'] for n in range(1, len(events) + 1)]
        assert actual == check['expected'], (check, actual)
    for case in fixture['cases']:
        events = case['events']
        actual = [representative(events[:n])['id'] for n in range(1, len(events) + 1)]
        assert actual == case['expected'], (case['name'], actual)
    return nodes


async def compose_table_review(page, screen, orientation):
    """Materialize inert repeated design markup, never application interactions."""
    await page.evaluate('''({id, orientation, groupNodes}) => {
        const active=['G01','G02','G05','G07','G08','G08b','G11','G12','H01','H02','H06'].includes(id);
        const history=['H01','H02','H04','H05','H06'].includes(id);
        for (const pane of document.querySelectorAll('.zone-person')) {
            const own=pane.classList.contains('own'), name=own?'alex':'riley';
            const life=pane.querySelector('.life-square');
            const button=document.createElement('button');
            button.className=life.className;
            button.textContent=life.textContent;
            button.setAttribute('aria-label',`${name}, life 20; open life and player counter actions`);
            life.replaceWith(button);
            if (own && id==='G08') {
                button.classList.add('is-dragging');
                button.insertAdjacentHTML('beforeend','<span class="delta-preview" aria-label="Uncommitted life delta, plus 3">+3</span>');
            }
            for (const zone of pane.querySelectorAll('.zones button')) {
                const label=zone.getAttribute('aria-label');
                const library=label.includes('Library'), exile=label.includes('Exile');
                const count=id==='H04'?(library?60:0):Number(zone.querySelector('b').textContent);
                if (id==='H04') zone.setAttribute('aria-label',label.replace(/[0-9]+ cards/,`${count} cards`));
                zone.replaceChildren(document.querySelector('#pile-display-template').content.cloneNode(true));
                zone.classList.add('pile-control');
                zone.dataset.zone=library?'library':exile?'exile':'graveyard';
                zone.querySelector('.pile-total').textContent=count;
                const pieces=[...zone.querySelectorAll('.piece')];
                const keep=Math.min(3,count);
                pieces.slice(0,3-keep).forEach(e=>e.remove());
                pieces.slice(3-keep).forEach(e=>{
                    e.classList.add(own?'owner-own':'owner-opponent');
                    if (library) e.classList.add('covered');
                    else e.innerHTML='<strong>Guide</strong><span class="art"><span class="icon">shield</span></span>';
                });
                if (!count) zone.querySelector('.pile-shell').insertAdjacentHTML('beforeend','<span class="pile-empty icon">block</span>');
            }
            pane.append(document.querySelector('#player-counter-row-template').content.cloneNode(true));
            const row=pane.querySelector('.counter-row');
            if (pane.closest('.portrait-dock')) {
                row.classList.add('portrait-counter-row'); row.dataset.player=own?'own':'opponent';
                document.querySelector('.middle').append(row);
            }
            row.querySelectorAll('.counter-chip').forEach(e=>e.setAttribute('aria-label',`${name}, ${e.getAttribute('aria-label')}`));
            if (own && active) {
                row.dataset.nonzero='true';
                const blue=row.querySelector('[data-color="blue"]');
                blue.textContent='3'; blue.setAttribute('aria-label',`${name}, blue player counter, 3`);
                if (id==='G08b') {
                    blue.classList.add('is-dragging');
                    blue.insertAdjacentHTML('beforeend','<span class="delta-preview" aria-label="Uncommitted blue counter delta, minus 2">−2</span>');
                }
            }
        }
        if (orientation==='landscape') {
            const side=document.querySelector('.sidebar');
            side.insertBefore(document.querySelector('.stack'), side.querySelector('.zone-person.own'));
            document.querySelectorAll('.reveal-strip').forEach(e=>document.querySelector('.workspace').append(e));
            document.querySelector('.top .title').after(document.querySelector('.opponent-hand'));
            document.querySelector(history?'.bottom.historical-only':'.bottom.live-only')
                .insertBefore(document.querySelector('.hand'), document.querySelector(history?'.transport':'.quick'));
        }
        if (history) {
            document.querySelectorAll('.zones button,button.life-square,.counter-chip').forEach(e=>{
                const span=document.createElement('span'); span.className=e.className+' zone-readout';
                if (e.closest('.zones')) span.classList.add('zone-label');
                span.innerHTML=e.innerHTML; span.setAttribute('aria-label',e.getAttribute('aria-label'));
                if (e.dataset.zone) span.dataset.zone=e.dataset.zone;
                e.replaceWith(span);
            });
        }
        // Empty insertion affordances are not empty persisted combat pairs.
        const combat=document.querySelector('.combat');
        for (const slot of [...combat.children]) {
            for (const [i,footprint] of [...slot.children].entries()) {
                const side=document.createElement('div'); side.className='combat-side';
                side.dataset.side=i===0?'blocker':'attacker';
                footprint.replaceWith(side); side.append(footprint);
                if (!footprint.children.length) footprint.remove();
            }
            combat.insertBefore(document.querySelector('#combat-insertion-template').content.cloneNode(true),slot);
        }
        combat.append(document.querySelector('#combat-insertion-template').content.cloneNode(true));
        combat.setAttribute('aria-label','Open combat, two active paired slots and three new-pair drop targets; blockers above attackers');
        // Show adaptive width with two blockers sharing the first pair.
        const firstSide=combat.querySelector('.combat-slot:not(.combat-insert) .combat-side');
        firstSide.append(firstSide.firstElementChild.cloneNode(true));
        firstSide.lastElementChild.querySelector('strong').textContent='Ward';
        if (id==='G02') {
            const sentinel=document.querySelector('.participant.own .piece');
            sentinel.classList.add('relation-highlight'); sentinel.dataset.reviewSource='battlefield-sentinel';
            const declared=document.querySelector('.combat-slot:not(.combat-insert) [data-side="attacker"] .piece');
            declared.classList.add('relation-highlight'); declared.dataset.reviewSource='battlefield-sentinel';
            const river=document.querySelectorAll('.combat-slot:not(.combat-insert)')[1].querySelector('.piece');
            river.classList.add('tapped','relation-highlight'); river.dataset.reviewSource='battlefield-river';
            const main=document.querySelector('.participant.own .card-row > .card-footprint:nth-child(2) .piece');
            main.classList.add('relation-highlight'); main.dataset.reviewSource='battlefield-river';
            const effect=document.querySelector('.stack-pile .piece:last-child');
            effect.classList.add('tapped','relation-highlight'); effect.dataset.reviewSource='battlefield-river';
            effect.querySelector('strong').textContent='River'; effect.querySelector('.art .icon').textContent='water_drop';
        }
        // Static direct controls for each card-counter color, not app handlers.
        for (const color of ['red','blue','green','yellow','purple','gray']) {
            const fragment=document.querySelector('#card-counter-row-template').content.cloneNode(true);
            const row=fragment.firstElementChild; row.classList.add(color);
            row.setAttribute('aria-label',color+' card counter');
            for (const [i,button] of [...row.querySelectorAll('button')].entries())
                button.setAttribute('aria-label',[`Set ${color} card counter`,
                    `Increase ${color} card counter by one`,`Decrease ${color} card counter by one`][i]);
            if (id==='G06' && color==='blue') row.querySelector('.fit-value').textContent='3';
            const player=row.cloneNode(true); player.setAttribute('aria-label',color+' player counter');
            player.querySelector('.fit-value').textContent='';
            for (const button of player.querySelectorAll('button')) button.setAttribute('aria-label',button.getAttribute('aria-label').replace('card counter','player counter'));
            if (color==='blue') player.querySelector('.fit-value').textContent='3';
            if (color==='gray') player.querySelector('.fit-value').textContent='0';
            document.querySelector('.player-counter-controls').append(player);
            document.querySelector('.card-counter-rows').append(fragment);
        }
        if (id==='G06') {
            const preview=document.querySelector('.card-preview');
            preview.classList.replace('owner-own','owner-opponent');
            preview.setAttribute('aria-label','Sentinel, face up, untapped, owner riley, corner text 2/4, blue counter 3');
            preview.querySelector('.pt').textContent='2/4';
            document.querySelector('.card-tools .choose-own').setAttribute('aria-pressed','false');
            document.querySelector('.card-tools .choose-opponent').setAttribute('aria-pressed','true');
            document.querySelector('.corner-set .fit-value').textContent='2/4';
            document.querySelector('.participant.own .piece').classList.replace('owner-own','owner-opponent');
            document.querySelector('.participant.own .piece .pt').textContent='2/4';
        }
        document.querySelector('.card-preview').insertAdjacentHTML('beforeend','<span class="scope" aria-label="One captured target">1</span>');
        if (id==='G10') {
            const cards=document.querySelector('.hand-cards');
            const examples=[...cards.children];
            for (let i=3;i<24;i++) cards.append(examples[i%3].cloneNode(true));
            document.querySelector('.hand').setAttribute('aria-label',"alex's hand, 24 cards, horizontally pannable");
            document.querySelector('.hand .hand-count').textContent='24';
            const opponent=document.querySelector('.opponent-hand');
            for (let i=3;i<24;i++) opponent.insertBefore(opponent.querySelector('.back').cloneNode(true),opponent.querySelector('.hand-count'));
            opponent.setAttribute('aria-label','riley, covered hand, 24 cards, horizontally pannable');
            opponent.querySelector('.hand-count').textContent='24';
        }
        if (id==='G09') document.querySelector('.chat-button').setAttribute('aria-label','Game chat, no unread messages');
        if (id==='H04') {
            document.querySelectorAll('.participant .card-row,.hand-cards').forEach(e=>e.replaceChildren());
            document.querySelectorAll('.opponent-hand .back').forEach(e=>e.remove());
            document.querySelectorAll('.hand-count').forEach(e=>e.textContent='0');
            document.querySelector('.hand').setAttribute('aria-label',"alex's hand, 0 cards");
            document.querySelector('.opponent-hand').setAttribute('aria-label','riley, covered hand, 0 cards');
            document.querySelector('.stack-content').remove();
            document.querySelector('.stack-empty').style.display='flex';
        }
        const opponent=document.querySelector('.opponent-hand'), backs=document.createElement('div');
        backs.className='opponent-cards';
        opponent.insertBefore(backs,opponent.firstChild);
        opponent.querySelectorAll('.back').forEach(e=>backs.append(e));
        const phaseGroups=[
            ['beginning',[['Untap','rotate_90_degrees_ccw'],['Upkeep','hourglass_top'],['Draw','download']]],
            ['precombat',[['Precombat main','looks_one']]],
            ['combat',[['Beginning of combat','flag'],['Declare attackers','swords'],['Declare blockers','shield'],['Combat damage','local_fire_department'],['End of combat','flag_circle']]],
            ['postcombat',[['Postcombat main','looks_two']]],
            ['ending',[['End step','nightlight'],['Cleanup','cleaning_services']]]
        ];
        const turnPlayer=id==='G11'?'opponent':'own', turn=id==='H04'?1:4;
        document.body.dataset.turnPlayer=turnPlayer;
        const exact=id==='G02'?'Declare attackers':id==='G11'?'Draw':id==='H04'?'Untap':'Precombat main';
        for (const [phase,steps] of phaseGroups) {
            const row=document.createElement('div'); row.className='phase-choice-row'; row.dataset.phase=phase;
            row.setAttribute('role','group'); row.setAttribute('aria-label',phase+' phase');
            for (const [label,icon] of steps) {
                const button=document.createElement('button'); button.dataset.icon=icon;
                button.setAttribute('aria-label',`Set ${label}, preserve current player and turn number`);
                button.setAttribute('aria-pressed',String(label===exact)); row.append(button);
                if (label===exact) document.querySelectorAll('.top .phase').forEach(e=>{
                    e.dataset.phase=phase; e.replaceChildren();
                    e.insertAdjacentHTML('beforeend',`<span class="icon" aria-hidden="true">${icon}</span>`);
                    e.setAttribute('aria-label',`Turn ${turn}, ${turnPlayer==='own'?'alex':'riley'}, ${label}${history?', read-only':', choose phase and step'}`);
                });
            }
            document.querySelector('.phase-rows').append(row);
        }
        document.querySelector('.top .title').insertAdjacentHTML('beforeend',`<span class="turn-number" aria-label="Turn ${turn}">${turn}</span>`);
        const context=document.querySelector('.contextual-quick');
        if (id==='G02') context.innerHTML='<button class="primary combined-icon" data-icon="swords" aria-label="Declare attackers and tap"><span class="icon" aria-hidden="true">rotate_90_degrees_cw</span></button><button data-icon="swords" aria-label="Declare attackers without tapping"></button><span class="quick-slot" aria-hidden="true"></span>';
        else if (id==='G11') context.innerHTML='<button data-icon="download" aria-label="Draw one for riley"></button><button class="combined-icon" data-icon="download" aria-label="Draw one for riley and advance"><span class="icon" aria-hidden="true">skip_next</span></button><span class="quick-slot" aria-hidden="true"></span>';
        else context.innerHTML='<span class="quick-slot" aria-hidden="true"></span><span class="quick-slot" aria-hidden="true"></span><span class="quick-slot" aria-hidden="true"></span>';
        if (turnPlayer==='opponent') document.querySelector('.progression-quick').setAttribute('aria-label',"Next step; hold to end opponent's turn");
        document.querySelector('.progression-quick').classList.add('end-turn-control');
        document.querySelector('.undo').dataset.holdAction='redo';
        document.querySelector('.phase-progression button:last-child').classList.add('end-turn-control');
        const actions=[['flag','Choose phase / step'],['skip_next','Next step'],['last_page','End turn'],
            ['rotate_90_degrees_ccw','Untap all · turn player'],['download','Draw one · turn player'],
            ['add_box','Create combat area'],['layers_clear','Close combat area'],['add_card','Create catalog token'],
            ['crop_portrait','Create Blank token'],['casino','Roll die…'],['monetization_on','Flip coin'],
            ['emoji_events','Suggest my win'],['outlined_flag','Suggest my loss'],['handshake','Suggest draw']];
        for (const [icon,label] of actions) {
            const button=document.createElement('button'); button.dataset.icon=icon; button.textContent=label;
            if (label==='End turn') button.classList.add('end-turn-control');
            document.querySelector('.table-action-list').append(button);
        }
        for (const node of groupNodes) {
            const button=document.createElement('button');
            button.className='tree-node'; button.dataset.group=node.id;
            button.dataset.author=node.author; button.dataset.icon=node.icon;
            button.dataset.target=node.target; button.dataset.tooltipName='Review '+node.name;
            button.style.left=node.x+'px'; button.style.top=node.y+'px';
            button.setAttribute('aria-label',`Review ${node.name} group, ${node.events.length} events; group end ${node.target}`);
            if (node.id==='g5') {button.classList.add('selected-node'); button.setAttribute('aria-pressed','true');}
            if (node.id==='g4') {button.classList.add('current-node'); button.setAttribute('aria-current','step');}
            document.querySelector('.tree-world').append(button);
        }
        const tree=document.querySelector('.tree-canvas'); tree.scrollLeft=220; tree.scrollTop=26;
        if (id==='H06') {
            document.querySelector('.timeline-label .paused-label').textContent='Paused · C2 · Comment';
            document.querySelector('.cursor-x').textContent='chat_bubble';
            document.querySelector('.cursor-x').dataset.eventType='comment';
            document.querySelector('.cursor-x').dataset.author='opponent';
            document.querySelector('.cursor-x').setAttribute('aria-label','riley, C2, Comment, selected personal review');
        }
        // Long values fit the static square; native text fitting remains future work.
        for (const value of document.querySelectorAll('.fit-value')) {
            let size=parseFloat(getComputedStyle(value).fontSize);
            const limit=value.closest('.counter-set')?28:44;
            while ((value.scrollWidth>limit || value.scrollHeight>limit) && size>8) value.style.fontSize=`${--size}px`;
        }
        // A fixed center reticle; only the path's static capture offset changes.
        const track=document.querySelector('.event-track'), viewport=document.createElement('div');
        viewport.className='event-viewport'; track.replaceWith(viewport); viewport.append(track);
        viewport.insertAdjacentHTML('beforeend','<span class="selection-square" aria-hidden="true"></span>');
        const nodes=[...track.querySelectorAll('.event-node')];
        const selected=id==='H04'?nodes[0]:id==='H05'?nodes.at(-1):track.querySelector(id==='H02'?'.cursor-y':'.cursor-x');
        selected.setAttribute('aria-current','step');
        track.style.transform=`translateX(-${selected.offsetLeft+selected.offsetWidth/2}px)`;
        if (id==='H04' || id==='H05') {
            document.querySelector('.timeline-label .paused-label').textContent=id==='H04'?'Paused · A · Setup':'Paused · Last · Reveal';
            track.setAttribute('aria-label',id==='H04'?'Start of event path centered, left half empty':'End of event path centered, right half empty');
        }
        // Static name annotations only; no native long-press handler is installed.
        const known={zoom_in:'Inspect',visibility:'Inspect other side',flip:'Turn face down',
            vertical_align_top:'Library top',vertical_align_bottom:'Library bottom',inventory_2:'Graveyard',
            block:'Exile',control_point_duplicate:'Clone',link:'Link',link_off:'Unlink',ads_click:'Ping',
            layers:'Stack effect',swords:'Declare attacker',settings_backup_restore:'Reset corner',
            sticky_note_2:'Edit note',published_with_changes:'Make current',account_tree:'Game tree',
            chat_bubble_outline:'Game chat',close:'Close',more_vert:'Table menu',call_split:'Continue here',live_tv:'Return to live'};
        for (const button of document.querySelectorAll('button')) {
            if (button.dataset.holdAction) {button.dataset.tooltipPolicy='hold-action'; continue;}
            if (button.closest('.review-navigation') && button.classList.contains('navigation-game')) {
                button.dataset.holdAction='game-context'; button.dataset.tooltipPolicy='hold-action'; continue;
            }
            const compact=button.matches('[data-icon]:empty,.icon-button,.phase,.history-button,.review-hamburger,.mode,.counter-chip,.counter-set,.corner-set,.pair-action,.life-square,.value-set,.pile-control,.previous-group,.next-group,.tree-node,.combined-icon,.declare-tap,.choose-own,.choose-opponent');
            if (!compact) continue;
            let name=button.dataset.tooltipName || known[button.dataset.icon];
            const label=button.getAttribute('aria-label')||'';
            if (button.matches('.history-button')) name='History';
            if (button.matches('.review-hamburger')) name='Menu';
            if (button.matches('.phase')) name='Choose step';
            if (button.closest('.phase-choice-row')) name=label.split(',')[0];
            if (button.matches('.mode')) name='Choose mode';
            if (button.matches('.declare-tap')) name='Declare and tap';
            if (button.matches('.life-square')) name='Life and counters';
            if (button.matches('.counter-chip')) name='Counter actions';
            if (button.matches('.pile-control')) name=button.closest('.stack')?'Stack contents':`${button.dataset.zone[0].toUpperCase()+button.dataset.zone.slice(1)} actions`;
            if (button.closest('.contextual-quick') && button.dataset.icon==='swords') name=button.querySelector('.icon')?'Declare and tap':'Declare attackers';
            if (button.closest('.contextual-quick') && button.dataset.icon==='download') name=button.querySelector('.icon')?'Draw and next':'Draw one';
            if (button.dataset.icon==='send') name='Send';
            if (button.matches('.corner-set')) name='Set corner text';
            if (button.matches('.value-set')) name='Set life';
            if (button.closest('.life-controls') && button.dataset.icon==='add') name='Increase life';
            if (button.closest('.life-controls') && button.dataset.icon==='remove') name='Decrease life';
            if (button.matches('.pair-action')) name=label.replace(' corner numbers by one','').replace(' corner number by one','');
            if (button.matches('.counter-set') || button.closest('.counter-triplet')) name=label.replace(' by one','').replace('card counter','counter').replace('player counter','counter');
            if (button.matches('.choose-own')) name='Owner alex';
            if (button.matches('.choose-opponent')) name='Owner riley';
            if (button.matches('.previous-group')) name='Previous group';
            if (button.matches('.next-group')) name='Next group';
            if (!name) name=label.split(';')[0].split(',')[0];
            button.dataset.tooltipPolicy='name-only'; button.dataset.tooltipName=name;
        }
        if (id==='G13') {
            const button=document.querySelector('.card-tools [data-icon="zoom_in"]');
            const tooltip=document.createElement('span'); tooltip.className='review-tooltip';
            tooltip.id='name-tooltip'; tooltip.setAttribute('role','tooltip'); tooltip.textContent=button.dataset.tooltipName;
            document.body.append(tooltip); button.setAttribute('aria-describedby',tooltip.id);
            const a=button.getBoundingClientRect(), b=tooltip.getBoundingClientRect();
            tooltip.style.left=Math.max(8,Math.min(innerWidth-b.width-8,a.left+(a.width-b.width)/2))+'px';
            tooltip.style.top=Math.max(8,a.top-b.height-6)+'px';
        }
    }''', {'id': screen['id'], 'orientation': orientation, 'groupNodes': review_group_nodes()})


async def validate_owner_revision(page, screen, orientation):
    """Regression checks for cumulative D-292–D-299 static presentation."""
    issues = await page.evaluate(r'''({id, orientation}) => {
        const issues = [];
        const visible = e => !!(e && e.getBoundingClientRect().width && e.getBoundingClientRect().height);
        const bars = '.status-bar,.status-bar-inset,.status,.safe-inset-bottom,.bottom-safe-inset,' +
            '.safe-area-bottom,.bottom-safe-area,.safe-area,.safe-inset,.safe,.bottom-inset,.safe-inset-bar';
        if ([...document.querySelectorAll(bars)].some(visible)) issues.push('Visible system-bar allowance');
        if (/Draft [A-Z]0[0-9]/.test(document.body.innerText)) issues.push('Visible mock title-bar badge');
        if ([...document.querySelectorAll('button')].some(e=>visible(e) &&
            (/arrow_back/.test(e.innerText) || ['arrow_back','home'].includes(e.dataset.icon))))
            issues.push('App Back/home arrow returned');
        const title = [...document.querySelectorAll('.screen-controls h1,.top-bar h1,.top-app-bar h1,.app-bar h1,.top .title,.readonly-title')].find(visible);
        const hamburger = document.querySelector('.review-hamburger');
        if (title && visible(hamburger)) {
            const a=title.getBoundingClientRect(), b=hamburger.getBoundingClientRect();
            if (Math.abs(a.y+a.height/2-b.y-b.height/2)>12) issues.push('Hamburger and title on different rows');
        }
        const nav = document.querySelector('.review-navigation');
        if (document.body.classList.contains('review-authenticated')) {
            if (!nav) issues.push('Missing shared navigation');
            else {
                const categories = [...nav.querySelectorAll('.navigation-category')].map(e=>e.textContent.trim());
                if (categories.join('|') !== 'Menu|Games') issues.push('Wrong top-level categories');
                if ([...nav.querySelectorAll('.navigation-category')].some(e=>
                    !['none','normal'].includes(getComputedStyle(e,'::before').content)))
                    issues.push('Icons added to text-only top-level navigation');
                for (const e of nav.querySelectorAll('.navigation-child,.navigation-footer button')) {
                    const icon = getComputedStyle(e,'::before');
                    if (['none','normal','""'].includes(icon.content) || !icon.fontFamily.includes('Review Symbols'))
                        issues.push('Missing matching navigation icon');
                }
                if ([...nav.querySelectorAll('.navigation-game')].some(e=>/^(With|Game with)\b/i.test(e.textContent.trim())))
                    issues.push('Redundant game-name prefix');
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
            const logo=document.querySelector('.form-panel .atw-logo');
            if (!visible(logo) || !logo.complete || !logo.naturalWidth) issues.push('Missing login ATW logo');
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
        if (id.startsWith('A') && [...document.querySelectorAll('.top-bar,.top-app-bar')].some(visible))
            issues.push('Authentication top Back/chrome returned');
        if (id === 'N01') {
            const content = document.querySelector('main').innerText;
            if (/Logout|notification|two.player|Import and organise|Find players by username/i.test(content))
                issues.push('Main-menu utility/subtitle clutter returned');
            const logo = document.querySelector('.atw-logo');
            if (!visible(logo) || !logo.complete || !logo.naturalWidth) issues.push('Missing ATW logo');
        }
        if (id === 'P01') {
            const sections = [...document.querySelectorAll('main .section-title')].map(e=>e.textContent.trim());
            if (sections.join('|') !== 'Games|Invitations') issues.push('Browser split/order regressed');
            if (document.querySelector('main').innerText.includes('Reinvite')) issues.push('Reinvite outside lobby');
            if (document.querySelectorAll('.open-chevron').length !== 2) issues.push('Missing right-chevron game Open');
            if (document.querySelectorAll('.game-state-details time').length !== 3)
                issues.push('Game/invitation creation dates missing beside state');
        }
        if (['D02','D04','D04b','L04','L04b'].includes(id)) {
            for (const row of document.querySelectorAll('.list-row')) {
                if (!row.querySelector('.qty + .art-button')) issues.push('Art not immediately after quantity');
                if (['L04','L04b'].includes(id) && row.querySelectorAll('button.transfer-control').length !== 2)
                    issues.push('Missing distinct one/all drag handles');
            }
            if (['L04','L04b'].includes(id) && /Save preparation/.test(document.querySelector('main').innerText))
                issues.push('Preparation Send step returned');
        }
        if (id==='P02' && document.querySelector('#username-search-input').closest('.section-card') !==
            document.querySelector('#search-results-heading').closest('.section-card'))
            issues.push('Search results separated from search field card');
        if (id==='P02') {
            const label=document.querySelector('#search-results-heading');
            if (/^H[1-6]$/.test(label.tagName) || parseFloat(getComputedStyle(label).fontSize)>13)
                issues.push('Search results look like another title');
        }
        if (id === 'D07') {
            const selected = document.querySelector('.segmented [aria-pressed="true"]');
            if (!selected || selected.textContent !== 'Overwrite') issues.push('Import default is not Overwrite');
        }
        if (['D04','D04b'].includes(id) && /Replace saved deck|Save as new/.test(document.querySelector('main').innerText))
            issues.push('Generic replacement selector returned');
        if (id === 'P04' && !/both players/.test(document.querySelector('[role="dialog"]').innerText))
            issues.push('Missing shared-deletion scope warning');
        if (['D02','D03','D04','D04b','D07'].includes(id)) {
            const exit=document.querySelector('[aria-label="Exit without saving"]');
            if (!visible(title) || !visible(exit)) issues.push('Missing import/editor title or exit');
            else {
                const a=title.getBoundingClientRect(), b=exit.getBoundingClientRect();
                if (Math.abs(a.y+a.height/2-b.y-b.height/2)>12 || b.right<innerWidth-24)
                    issues.push('Exit not at title-row right corner');
            }
        }
        if (['L01','L02','L05','L06'].includes(id)) {
            const statuses=[...document.querySelectorAll('main .readiness')];
            if (statuses.length!==2) issues.push('Missing per-player readiness');
            for (const status of statuses) {
                const icon=getComputedStyle(status,'::before');
                if (!icon.fontFamily.includes('Review Symbols') ||
                    status.dataset.ready==='true' && (!icon.content.includes('check_circle') || icon.color!=='rgb(35, 121, 99)'))
                    issues.push('Missing colored readiness icon/green Ready check');
            }
            if (!title || title.textContent.trim()!=='Crackling Mist') issues.push('Lobby game-name title missing');
            if (!document.querySelector('.game-details time')) issues.push('Lobby creation date missing');
            const cancel=document.querySelector('.screen-controls [aria-label="Cancel game"]');
            if (!visible(cancel) || cancel.getBoundingClientRect().right<innerWidth-24)
                issues.push('Lobby Cancel not top-right');
            if (/Leaving this screen|Ready stays set|when you leave/.test(document.body.innerText))
                issues.push('Leaving-lobby helper returned');
            const reinvite=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='Reinvite');
            if (reinvite && !reinvite.closest('.opponent-slot')) issues.push('Reinvite outside opponent slot');
        }
        if (id==='L03' && (document.querySelector('.select-btn') || !document.querySelector('.fixed-bottom .primary')))
            issues.push('Deck selection missing bottom confirmation or per-row Select returned');
        if (['L04','L04b'].includes(id) && [...document.querySelectorAll('.drag-handle')].some(e=>e.getBoundingClientRect().width!==24))
            issues.push('Preparation handles not half-width');
        if (['L04','L04b'].includes(id)) {
            const feedback=document.querySelector('.transfer-feedback');
            if (!feedback || feedback.getBoundingClientRect().height!==40)
                issues.push('Missing fixed-height transfer-feedback slot');
            if (feedback && (getComputedStyle(feedback).opacity==='0') !== (id==='L04b'))
                issues.push('Wrong visible/faded feedback snapshot');
        }
        if (id==='D05' && [...document.querySelectorAll('.selection-indicator')].some(visible))
            issues.push('Cover checkboxes returned');
        if (id==='D05' && orientation==='landscape' && visible(document.querySelector('.bottom-bar.portrait-only')))
            issues.push('Duplicate portrait cover footer in landscape');
        if (id==='D06' && /Copies already selected|trash folder/.test(document.querySelector('.dialog-body').innerText))
            issues.push('Long deck-deletion explanation returned');
        if (id==='P05' && (!document.querySelector('#game-name[required]') || !document.querySelector('[aria-label="Suggest another game name"]')))
            issues.push('Required name/suggestion control missing');
        if (id==='P05' && (!document.querySelector('[role="dialog"] #game-name[data-select-all-on-focus="true"]')))
            issues.push('Naming is not a dialog with specified select-all behavior');
        if (id==='D06' && document.querySelector('.dialog-eyebrow')) issues.push('Duplicate deletion title');
        if (id==='U01') {
            const dialog=document.querySelector('.reconnect-context [role="dialog"]');
            if (!dialog || dialog.querySelectorAll('button').length!==1 || !/Back to games list/.test(dialog.innerText))
                issues.push('Reconnect escape is not games-list only');
        }
        if (document.body.dataset.reviewState) {
            if (document.querySelector('.ghost,.band-label')) issues.push('Visible slot/area labels returned');
            const widths=[...document.querySelectorAll('.participant .card-footprint')].filter(visible).map(e=>e.getBoundingClientRect().width);
            if (Math.max(...widths)-Math.min(...widths)>0.1) issues.push('Unequal battlefield footprints');
            for (const row of document.querySelectorAll('.participant .card-row')) {
                if (getComputedStyle(row).borderBottomWidth!=='0px') issues.push('Battlefield separators returned');
            }
            for (const card of document.querySelectorAll('.participant .piece')) {
                const a=card.getBoundingClientRect(), b=card.parentElement.getBoundingClientRect();
                if (a.left<b.left-.1 || a.right>b.right+.1 || a.top<b.top-.1 || a.bottom>b.bottom+.1)
                    issues.push('Card/pile exceeds uniform footprint');
            }
            if (/riley|alex|Life\s*\d/.test(document.querySelector('.workspace').innerText))
                issues.push('Gameplay player-name/Life clutter returned');
            if (id==='G02') {
                if (document.querySelectorAll('.stack-pile .piece').length!==3) issues.push('Stack is not top plus two');
                if (orientation==='portrait') {
                    const stack=document.querySelector('.stack').getBoundingClientRect();
                    const library=document.querySelector('.portrait-dock .zone-person').getBoundingClientRect();
                    if (stack.left<library.right) issues.push('Portrait Stack is not right of libraries');
                }
            }
            const pieces=[...document.querySelectorAll('.piece,.back')].filter(visible);
            const sizes=pieces.map(e=>{const s=getComputedStyle(e);return [parseFloat(s.width),parseFloat(s.height)];});
            if (sizes.some(s=>Math.abs(s[0]-40*.7143)>.1 || Math.abs(s[1]-40)>.1))
                issues.push('Cards do not share one 40px review zoom across all areas/orientations');
            for (const e of document.querySelectorAll('.zones [data-zone],.stack .pile-control')) {
                if (!e.querySelector('.pile-total')) {issues.push('Pile total badge missing'); continue;}
                const badge=e.querySelector('.pile-total'), shell=e.querySelector('.pile-shell')||e;
                const a=badge.getBoundingClientRect(), b=shell.getBoundingClientRect();
                if (visible(e) && (Math.abs(a.left-b.left)>1 || Math.abs(a.top-b.top)>1 ||
                    getComputedStyle(badge).borderRadius!=='50%')) issues.push('Pile count is not a top-left circle');
            }
            if (orientation==='landscape') {
                const side=document.querySelector('.sidebar'), stack=document.querySelector('.stack');
                const a=side.querySelector('.opponent').getBoundingClientRect(), b=stack.getBoundingClientRect(),
                    c=side.querySelector('.own').getBoundingClientRect();
                if (stack.parentElement!==side || b.top<a.bottom-.1 || b.bottom>c.top+.1)
                    issues.push('Landscape Stack not between player info blocks');
                const hand=document.querySelector('.hand'), bottom=hand.parentElement.getBoundingClientRect(), h=hand.getBoundingClientRect();
                if (!hand.parentElement.matches('.bottom') || h.top<bottom.top-.1 || h.bottom>bottom.bottom+.1)
                    issues.push('Landscape own Hand not in bottom action row');
                if (document.querySelector('.opponent-hand').parentElement!==document.querySelector('.top'))
                    issues.push('Landscape opponent Hand still occupies battlefield');
            } else {
                for (const row of document.querySelectorAll('.portrait-counter-row'))
                    if (visible(row)!==(row.dataset.nonzero==='true')) issues.push('Portrait counter-row conditional visibility');
            }
            for (const row of document.querySelectorAll('.counter-row')) {
                if (!visible(row)) continue;
                const chips=[...row.querySelectorAll('.counter-chip')], bounds=row.getBoundingClientRect();
                const first=chips[0].getBoundingClientRect(), last=chips.at(-1).getBoundingClientRect();
                if (Math.abs(first.left-bounds.left)>1 || Math.abs(last.right-bounds.right)>1 ||
                    chips.some(e=>getComputedStyle(e,'::before').width!=='28px'))
                    issues.push('Counters not circular/spread across entire row');
                if (row.classList.contains('portrait-counter-row')) {
                    const middle=document.querySelector('.middle').getBoundingClientRect();
                    if (Math.abs(bounds.left-middle.left-4)>1 || Math.abs(middle.right-4-bounds.right)>1)
                        issues.push('Portrait counters only span library column, not full middle strip');
                }
                for (const chip of chips) if (/absent/.test(chip.getAttribute('aria-label')) && chip.textContent.trim())
                    issues.push('Absent player counter still displays a dash/value');
            }
            if (document.querySelector('.player-counter-controls .red .fit-value').textContent!=='' ||
                document.querySelector('.player-counter-controls .gray .fit-value').textContent!=='0')
                issues.push('Player menu conflates blank/zero values');
            if (parseFloat(getComputedStyle(document.querySelector('.hand-cards')).gap)>2)
                issues.push('Hand card gap not compact');
            for (const hand of document.querySelectorAll('.hand-cards,.opponent-cards')) {
                if (hand.scrollWidth<=hand.clientWidth && hand.children.length) {
                    const a=hand.firstElementChild.getBoundingClientRect(), b=hand.lastElementChild.getBoundingClientRect(), r=hand.getBoundingClientRect();
                    if (Math.abs((a.left+b.right)/2-(r.left+r.right)/2)>1) issues.push('Fitting Hand not centered');
                }
            }
            if (id==='G10') for (const hand of document.querySelectorAll('.hand-cards,.opponent-cards')) {
                if (hand.scrollWidth<=hand.clientWidth || getComputedStyle(hand).overflowX!=='auto')
                    issues.push('Overflowing Hand not horizontally pannable');
                hand.scrollLeft=hand.scrollWidth;
                if (hand.scrollLeft<=0) issues.push('Hand cannot reach rightmost cards');
                hand.scrollLeft=0;
                if (hand.scrollLeft!==0) issues.push('Hand cannot return to leftmost cards');
                hand.scrollLeft=48;
            }
            if (id==='G02') {
                const inserts=[...document.querySelectorAll('.combat-insert')], slots=[...document.querySelectorAll('.combat-slot')];
                if (inserts.length!==3 || slots.some((e,i)=>e.classList.contains('combat-insert')!==(i%2===0)))
                    issues.push('Combat insertion targets missing before/between/after pairs');
                for (const slot of slots) {
                    const sides=[...slot.children].map(e=>e.getBoundingClientRect().width);
                    if (Math.abs(slot.getBoundingClientRect().width-Math.max(...sides))>1.1)
                        issues.push('Combat slot stretched beyond card contents');
                }
                if (inserts.some(e=>Math.abs(e.getBoundingClientRect().width-(40*.7143+7))>1.1))
                    issues.push('Empty combat target not one-card width plus tiny margin');
                if ([...document.querySelectorAll('.combat-insert .combat-side')].some(e=>getComputedStyle(e).borderWidth!=='0px') ||
                    [...document.querySelectorAll('.combat-insert .icon')].some(visible)) issues.push('Drawn empty combat boxes/plus markers returned');
                if (document.querySelectorAll('.relation-highlight[data-review-source="battlefield-river"]').length!==3 ||
                    document.querySelectorAll('.relation-highlight[data-review-source="battlefield-sentinel"]').length!==2 ||
                    !document.querySelector('.combat .tapped')) issues.push('Tapped combat/source relationship example missing');
            }
            if (['G04','G06','G13'].includes(id)) {
                const dialog=document.querySelector('.card-action-dialog'), preview=dialog.querySelector('.card-preview');
                if (!visible(preview) || dialog.querySelector('h2,.dialog-heading,[data-icon="close"]'))
                    issues.push('Card details missing or title/close X returned');
                if (dialog.querySelectorAll('.card-counter-rows .counter-triplet').length!==6 ||
                    dialog.querySelectorAll('.card-values button').length!==25 ||
                    dialog.querySelectorAll('.card-tools .choose-own,.card-tools .choose-opponent').length!==2)
                    issues.push('Card +/-/Set or inline owner choices hidden in submenu');
                const pt=[...dialog.querySelectorAll('.pt-controls button')].map(e=>e.getBoundingClientRect());
                if (pt.length!==7 || pt.some(e=>e.top!==pt[0].top)) issues.push('P/T controls not seven squares on one row');
                const triplets=[...dialog.querySelectorAll('.counter-triplet')].map(e=>e.getBoundingClientRect());
                if (new Set(triplets.map(e=>e.top)).size!==3) issues.push('Card counters not paired triplets on three rows');
                for (const button of dialog.querySelectorAll('button')) {
                    const b=button.getBoundingClientRect();
                    if (b.width!==48 || b.height!==48 || !button.getAttribute('aria-label'))
                        issues.push('Card action not square icon-only/named');
                }
                if (id==='G06' && (!preview.classList.contains('owner-opponent') || preview.querySelector('.pt').textContent!=='2/4'))
                    issues.push('Retained card preview not updated');
            }
            if (visible(document.querySelector('.bottom.live-only'))) {
                const quick=document.querySelector('.quick');
                if (quick.getBoundingClientRect().width!==204 || quick.querySelectorAll(':scope > button').length!==1 ||
                    quick.querySelectorAll('.contextual-quick > *').length!==3 ||
                    quick.querySelector('.progression-quick').dataset.holdAction!=='end-turn')
                    issues.push('Quick allocation not three contextual plus one tap/hold progression position');
            }
            for (const phase of [...document.querySelectorAll('.top .phase')].filter(visible)) {
                if (!phase.dataset.phase || !phase.querySelector('.icon') || phase.childNodes.length!==1 ||
                    getComputedStyle(phase).borderTopWidth!=='3px') issues.push('Phase indicator not exact icon/phase color/player border');
            }
            if ([...document.querySelectorAll('.top .phase')].filter(visible).length!==1) issues.push('Duplicate/missing phase indicator');
            if (id==='G11' && (document.querySelectorAll('.phase-choice-row').length!==5 ||
                document.querySelectorAll('.phase-choice-row button').length!==12 ||
                !/End opponent/.test(document.querySelector('.phase-progression').innerText))) issues.push('Phase picker incomplete');
            if (id==='G11') {
                const rows=[...document.querySelectorAll('.phase-choice-row')];
                if (new Set(rows.map(e=>getComputedStyle(e).backgroundColor)).size!==5 ||
                    new Set([...document.querySelectorAll('.phase-choice-row button')].map(e=>e.dataset.icon)).size!==12)
                    issues.push('Phase colors/exact-step icons not distinct');
                if (getComputedStyle(document.querySelector('.phase-dialog')).borderTopColor!==
                    getComputedStyle(document.querySelector('.phase.live-only')).borderTopColor)
                    issues.push('Phase picker/indicator disagree on turn-player border');
            }
            for (const end of document.querySelectorAll('.end-turn-control')) {
                const style=getComputedStyle(end), phase=getComputedStyle(document.querySelector('.phase.live-only'));
                if (style.backgroundColor!==phase.borderTopColor || style.color!=='rgb(255, 255, 255)')
                    issues.push('End-turn control not filled with tracked player color');
            }
            for (const button of document.querySelectorAll('button[data-tooltip-policy]')) {
                if (button.dataset.holdAction && (button.dataset.tooltipName || button.dataset.tooltipPolicy!=='hold-action'))
                    issues.push('Name tooltip steals an assigned hold action');
                if (button.dataset.tooltipPolicy==='name-only' && (!button.dataset.tooltipName ||
                    button.dataset.tooltipName.length>40 || /hold|preserve|logged| by one/i.test(button.dataset.tooltipName)))
                    issues.push('Tooltip is explanatory rather than a short action name');
            }
            if (id==='G13') {
                const tip=document.querySelector('[role="tooltip"]'), r=tip.getBoundingClientRect();
                if (tip.textContent!=='Inspect' || r.left<0 || r.right>innerWidth || r.top<0 || r.bottom>innerHeight)
                    issues.push('Short-name tooltip missing/out of bounds');
            }
            if (id==='H06') {
                const dialog=document.querySelector('.tree-dialog'), tree=document.querySelector('.tree-canvas');
                if (!visible(dialog) || dialog.getBoundingClientRect().width>360 || dialog.getBoundingClientRect().height>300 ||
                    document.querySelectorAll('.tree-node').length!==7 || dialog.querySelector('.tree-filters,.tree-review-actions,.archive-dialog') ||
                    dialog.querySelectorAll('.make-current').length!==1) issues.push('Group tree still oversized/button-heavy');
                if (tree.scrollWidth<=tree.clientWidth || tree.scrollHeight<=tree.clientHeight ||
                    [...dialog.querySelectorAll('.tree-node')].some(e=>e.textContent.trim() || !e.dataset.group || !e.dataset.icon))
                    issues.push('Tree not pannable group icons');
                const chosen=dialog.querySelector('.selected-node'), current=dialog.querySelector('.current-node');
                if (chosen.dataset.target!=='C2' || chosen.dataset.icon!=='visibility' || current.dataset.target!=='Y2' ||
                    !dialog.querySelector('.current-edge')) issues.push('Representative icon confused with exact group-end target/current path');
                const x=tree.scrollLeft,y=tree.scrollTop;
                tree.scrollLeft=0; tree.scrollTop=0;
                if (tree.scrollLeft!==0 || tree.scrollTop!==0) issues.push('Cannot pan tree to start');
                tree.scrollLeft=tree.scrollWidth; tree.scrollTop=tree.scrollHeight;
                if (tree.scrollLeft<=0 || tree.scrollTop<=0) issues.push('Cannot pan tree to far ends');
                tree.scrollLeft=x; tree.scrollTop=y;
            }
            if (!visible(document.querySelector('.chat-button'))) issues.push('Missing direct game chat button');
            if (id==='G09' && (!visible(document.querySelector('.chat-panel')) || visible(document.querySelector('.unread-bubble'))))
                issues.push('Chat panel/read sample not shown');
            if (id==='H03' && (document.querySelector('.intent') || /Continuing from/.test(document.body.innerText)))
                issues.push('Continuation banner returned');
            if (id==='G05' && orientation==='landscape') {
                const side=document.querySelector('.sidebar').getBoundingClientRect(),
                    field=document.querySelector('.board').getBoundingClientRect();
                for (const tray of document.querySelectorAll('.reveal-strip')) {
                    const r=tray.getBoundingClientRect();
                    if (r.left<side.right-.1 || r.right>field.left+.1)
                        issues.push('Landscape reveal not between libraries and field');
                }
            }
            if (['H01','H02','H04','H05'].includes(id)) {
                const viewport=document.querySelector('.event-viewport').getBoundingClientRect(),
                    selected=document.querySelector('.event-node[aria-current="step"]').getBoundingClientRect(),
                    square=document.querySelector('.selection-square').getBoundingClientRect();
                const center=viewport.left+viewport.width/2;
                if (Math.abs(selected.left+selected.width/2-center)>.1 || Math.abs(square.left+square.width/2-center)>.1)
                    issues.push('Timeline selected event/reticle not exactly centered');
                if (id==='H04' && document.querySelector('[aria-current="step"]')!==document.querySelector('.event-node') ||
                    id==='H05' && document.querySelector('[aria-current="step"]')!==[...document.querySelectorAll('.event-node')].at(-1))
                    issues.push('Wrong centered endpoint sample');
                const icons=new Map(), reverse=new Map();
                for (const node of document.querySelectorAll('.event-node')) {
                    const type=node.dataset.eventType, icon=node.textContent;
                    if (icons.has(type)&&icons.get(type)!==icon || reverse.has(icon)&&reverse.get(icon)!==type)
                        issues.push('Event-type icon collision/inconsistency');
                    icons.set(type,icon); reverse.set(icon,type);
                }
                if (icons.size<12) issues.push('Insufficient dense event-type examples');
                const nodes=[...document.querySelectorAll('.event-node')];
                for (const [i,node] of nodes.entries()) {
                    if (!['own','opponent'].includes(node.dataset.author)) issues.push('Missing event author color');
                    if ((node.dataset.groupStart==='true')!==(i>0 && node.dataset.group!==nodes[i-1].dataset.group))
                        issues.push('Group divider differs from actual group transition');
                }
                const main=[...document.querySelectorAll('.playback-main')].find(visible);
                if (!main || getComputedStyle(main).fontSize==='0px' || !/Play|Pause/.test(main.innerText))
                    issues.push('History Play/Pause missing visible text');
                if (!document.querySelector('.paused-label[data-hold-action="continue-from-selected-boundary"]') ||
                    !visible(document.querySelector('.top [aria-label="Continue playing from here"]')))
                    issues.push('Missing hold or top-corner continuation route');
                if (orientation==='landscape') {
                    const prev=document.querySelector('.previous-group').getBoundingClientRect(),
                        next=document.querySelector('.next-group').getBoundingClientRect(), center=main.getBoundingClientRect();
                    if (Math.abs(prev.right-center.left)>1 || Math.abs(center.right-next.left)>1)
                        issues.push('Landscape transport not attached');
                }
            }
        }
        if (id==='U01' && (!document.querySelector('.board .piece') || /Empty gameplay placeholder/.test(document.body.innerText)))
            issues.push('Reconnect still has an empty board');
        for (const e of document.querySelectorAll('.dialog-heading .icon-only,.dialog-close-btn,.inspector-dialog > button')) {
            const dialog=e.closest('.dialog,.modal-dialog');
            if (visible(e) && dialog) {
                const a=e.getBoundingClientRect(), b=dialog.getBoundingClientRect();
                if (a.right<b.right-36 || a.top>b.top+40) issues.push('Dialog X not upper-right');
            }
        }
        for (const pair of document.querySelectorAll('.action-pair,.dialog-actions,.actions,.landscape-actions,.bottom-bar')) {
            const buttons=[...pair.children].filter(e=>e.tagName==='BUTTON' && visible(e));
            if (visible(pair) && buttons.length===2) {
                const a=buttons[0].getBoundingClientRect(), b=buttons[1].getBoundingClientRect();
                if (Math.abs(a.width-b.width)>2 || Math.abs(a.top-b.top)>2)
                    issues.push('Paired actions not equal-width in one row');
            }
        }
        return issues;
    }''', {'id': screen['id'], 'orientation': orientation})
    if issues:
        raise ValueError(f"{screen['id']} {orientation}: {issues}")


async def main(screen_ids):
    manifest = json.loads((UI / 'manifest.json').read_text())
    known_ids = {screen['id'] for screen in manifest['screens']}
    if known_ids & {'G00', 'H07'}:
        raise ValueError('Retired G00/H07 must stay outside the current review')
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
                if screen.get('captureVariant'):
                    await page.evaluate('(variant)=>document.body.dataset.reviewState=variant', screen['captureVariant'])
                    await compose_table_review(page, screen, orientation)
                    if screen['id'] == 'G02':
                        await page.evaluate('''() => {
                            document.querySelector('.mode').setAttribute('aria-label','Select mode, 2 selected; choose mode');
                        }''')
                await page.evaluate('document.fonts.ready')
                if screen.get('captureVariant') and orientation == 'landscape':
                    # A static camera framing, not smaller area-specific cards.
                    await page.evaluate('''() => {
                        const board=document.querySelector('.board');
                        board.scrollTop=(board.scrollHeight-board.clientHeight)/2;
                    }''')
                if screen.get('captureFeedback') == 'faded':
                    await page.evaluate('''() => {
                        const anchors=[...document.querySelectorAll('main,.screen-controls,.transfer-feedback,.list-row')];
                        const bounds=()=>anchors.map(e=>JSON.stringify(e.getBoundingClientRect().toJSON())).join('|');
                        const before=bounds();
                        document.querySelector('.transfer-feedback').style.opacity='0';
                        if (bounds()!==before) throw new Error('Fading feedback shifted layout');
                    }''')
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
                            if(['auto','scroll'].includes(c.overflowY)&&p.scrollHeight>p.clientHeight ||
                               ['auto','scroll'].includes(c.overflowX)&&p.scrollWidth>p.clientWidth)scrollAncestor=true;
                        }
                        return {text:(e.innerText||e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.tagName).trim().slice(0,120),
                            x:Math.round(r.x),y:Math.round(r.y),width:Math.round(r.width),height:Math.round(r.height),
                            minimumWidth:e.matches('button.transfer-control')?24:48,
                            scrollAncestor,inViewport:r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth};
                    })
                })''')
                screen.setdefault('audit', {})[orientation] = audit
                bad = [c for c in audit['controls'] if c['width'] < c['minimumWidth'] or c['height'] < 48
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
