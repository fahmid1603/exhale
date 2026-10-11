import { useEffect, useRef, useState } from 'react'
import { SiteFooter, TopBar } from '../components/Chrome.jsx'
import { Link } from '../router.jsx'
import { ALL_RELEASES, LATEST_RELEASE, releasePath, tagUrl } from '../releases.js'
import { RELEASES, REPO } from '../content.js'
import { useReveals } from '../hooks.js'
import Segments from '../components/Segments.jsx'
import '../coloros.css'

/*
 * A release page in the shape of OPPO's ColorOS 17 page.
 *
 * What that page does, and so what this one does:
 *  - the global bar scrolls away and a row of chapters sticks to the top, the
 *    one being read set heavier;
 *  - the first screen is the name, lit on black, with a two-part line under it;
 *  - every chapter opens on one enormous centred line that brightens as it
 *    reaches the middle of the screen;
 *  - the highlights are tall rounded cards that slide in sideways while the
 *    page holds still;
 *  - the rest are tiles: a capture, a single number, or a few words;
 *  - it ends on the plan — what it runs on and where to get it.
 *
 * Everything scroll-linked writes custom properties from one rAF-throttled
 * handler, so scrolling never re-renders React. With reduced motion the rail is
 * an ordinary horizontal scroller and nothing is pinned.
 */

const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** One scroll listener for the page; each subscriber reads layout and writes its own properties. */
function useScrollFrame(callback, deps) {
  useEffect(() => {
    let frame = 0
    const run = () => {
      frame = 0
      callback()
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(run)
    }
    run()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
}

/* ------------------------------------------------------------- chapter row */

function ChapterNav({ chapters }) {
  const [active, setActive] = useState(chapters[0]?.id)

  useEffect(() => {
    const nodes = chapters.map((c) => document.getElementById(c.id)).filter(Boolean)
    if (!nodes.length) return undefined
    // The chapter whose top has most recently passed a line a third of the way down.
    const pick = () => {
      const line = window.innerHeight * 0.34
      let current = chapters[0].id
      for (const node of nodes) {
        if (node.getBoundingClientRect().top <= line) current = node.id
      }
      setActive((prev) => (prev === current ? prev : current))
    }
    let frame = 0
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(() => { frame = 0; pick() })
    }
    pick()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [chapters])

  // The site's own floating capsule, the same one the home page wears.
  return (
    <div className="segbar">
      <Segments
        items={chapters}
        active={Math.max(0, chapters.findIndex((c) => c.id === active))}
        onSelect={(i) => setActive(chapters[i].id)}
      />
    </div>
  )
}

/* -------------------------------------------------------------------- hero */

function Hero({ release }) {
  const box = useRef(null)

  // The phones rise and the name recedes as the first screen scrolls away.
  useScrollFrame(() => {
    const node = box.current
    if (!node || reducedMotion()) return
    const rect = node.getBoundingClientRect()
    const p = Math.min(1, Math.max(0, -rect.top / Math.max(1, rect.height * 0.7)))
    node.style.setProperty('--hp', p.toFixed(4))
  }, [])

  const href = release === LATEST_RELEASE ? RELEASES : tagUrl(REPO, release)
  const [first, second] = release.tagline

  return (
    <header className="c17-hero" ref={box}>
      <div className="c17-hero-light" aria-hidden="true">
        <span className="c17-blob c17-blob-a" />
        <span className="c17-blob c17-blob-b" />
        <span className="c17-blob c17-blob-c" />
        <span className="c17-horizon" />
      </div>

      <div className="c17-hero-name">
        <p className="c17-hero-brand">Exhale</p>
        <h1 className="c17-hero-ver">
          <span className="c17-sr">Exhale </span>
          {release.version}
        </h1>
        <p className="c17-hero-line">
          <span>{first}</span> <span className="c17-grad">{second}</span>
        </p>
        <p className="c17-hero-dek">{release.dek}</p>
        <div className="c17-hero-actions">
          <a className="c17-btn" href={href} target="_blank" rel="noreferrer">
            Download {release.version}
          </a>
          <a className="c17-btn c17-btn-ghost" href="#everything">
            Everything new
          </a>
        </div>
        <p className="c17-hero-meta">
          Android 13 or later · Universal APK · {release.date}
        </p>
      </div>

      <div className="c17-hero-phones" aria-hidden="true">
        {release.heroShots.map((s, i) => (
          <div className="c17-phone" data-i={i} key={s.src}>
            <img src={s.src} alt="" width="720" height="1280" loading={i === 1 ? 'eager' : 'lazy'} decoding="async" />
          </div>
        ))}
      </div>
    </header>
  )
}

/* ------------------------------------------------------------ chapter title */

/** The enormous centred line a chapter opens on, brightening as it reaches the middle. */
function BigTitle({ eyebrow, title, lede }) {
  const ref = useRef(null)
  useScrollFrame(() => {
    const node = ref.current
    if (!node) return
    if (reducedMotion()) {
      node.style.setProperty('--lit', '1')
      return
    }
    const rect = node.getBoundingClientRect()
    const mid = rect.top + rect.height / 2
    const vh = window.innerHeight
    // 0 at the bottom edge, 1 once it is two fifths of the way up.
    const lit = Math.min(1, Math.max(0, (vh - mid) / (vh * 0.6)))
    node.style.setProperty('--lit', lit.toFixed(4))
  }, [])

  return (
    <div className="c17-title" ref={ref}>
      {eyebrow && <p className="c17-title-eyebrow">{eyebrow}</p>}
      <h2 className="c17-title-h">{title}</h2>
      {lede && <p className="c17-title-lede">{lede}</p>}
    </div>
  )
}

/* -------------------------------------------------------------- highlights */

/**
 * The cards that slide across while the page holds still.
 *
 * The section is as tall as the sideways travel needs; its inner frame sticks
 * under the chapter row and the track is moved by how far through the section
 * the reader is. One transform, written straight to the element.
 */
function HighlightRail({ cards }) {
  const section = useRef(null)
  const track = useRef(null)
  const [pinned, setPinned] = useState(true)

  useEffect(() => setPinned(!reducedMotion()), [])

  useScrollFrame(() => {
    const s = section.current
    const t = track.current
    if (!s || !t || reducedMotion()) return
    const rect = s.getBoundingClientRect()
    const travel = Math.max(0, t.scrollWidth - t.parentElement.clientWidth)
    const span = Math.max(1, rect.height - window.innerHeight)
    const p = Math.min(1, Math.max(0, -rect.top / span))
    t.style.transform = `translate3d(${(-travel * p).toFixed(1)}px, 0, 0)`
    s.style.setProperty('--rail', p.toFixed(4))
  }, [cards.length])

  return (
    <section
      className="c17-rail"
      ref={section}
      data-pinned={pinned}
      style={{ '--cards': cards.length }}
      aria-label="Highlights"
    >
      <div className="c17-rail-frame">
        <div className="c17-rail-track" ref={track}>
          {cards.map((card, i) => (
            <article className="c17-card" key={card.title} style={{ '--tint': card.tint }}>
              <div className="c17-card-say">
                <p className="c17-card-kick">{card.kicker}</p>
                <h3 className="c17-card-h">{card.title}</h3>
                <p className="c17-card-p">{card.body}</p>
              </div>
              <div className="c17-card-shot">
                <img src={card.shot.src} alt={card.shot.alt} width="720" height="1280" loading={i < 2 ? 'eager' : 'lazy'} decoding="async" />
              </div>
            </article>
          ))}
        </div>
        <div className="c17-rail-dots" aria-hidden="true">
          {cards.map((card, i) => (
            <span key={card.title} style={{ '--n': i, '--of': cards.length - 1 }} />
          ))}
        </div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ tiles */

function Tile({ tile, index }) {
  const kind = tile.stat ? 'stat' : tile.shot ? 'shot' : 'words'
  return (
    <article
      className="c17-tile reveal"
      data-size={tile.size ?? 'one'}
      data-kind={kind}
      data-landscape={!!tile.landscape}
      style={{ '--d': `${(index % 3) * 70}ms` }}
    >
      {tile.stat && (
        <p className="c17-stat">
          <span className="c17-grad">{tile.stat}</span>
          <small>{tile.unit}</small>
        </p>
      )}
      <div className="c17-tile-say">
        <h3 className="c17-tile-h">{tile.title}</h3>
        {tile.body && <p className="c17-tile-p">{tile.body}</p>}
      </div>
      {tile.shot && (
        <div className="c17-tile-shot">
          <img src={tile.shot.src} alt={tile.shot.alt} width={tile.landscape ? 1280 : 720} height={tile.landscape ? 720 : 1280} loading="lazy" decoding="async" />
        </div>
      )}
    </article>
  )
}

function Chapter({ section }) {
  return (
    <section className="c17-chapter" id={section.id}>
      <BigTitle eyebrow={section.eyebrow} title={section.title} lede={section.lede} />
      <div className="c17-grid">
        {section.tiles.map((tile, i) => (
          <Tile tile={tile} index={i} key={tile.title} />
        ))}
      </div>
    </section>
  )
}

/* -------------------------------------------------------------- the plan */

function GetIt({ release }) {
  const latest = release === LATEST_RELEASE
  const href = latest ? RELEASES : tagUrl(REPO, release)
  return (
    <section className="c17-chapter" id="get">
      <BigTitle eyebrow="Get 1.0.405" title="One file. Every Android phone." lede="No store, no account, no telemetry. After the first install it updates itself." />
      <div className="c17-plan reveal">
        <dl className="c17-plan-facts">
          {release.facts.map((fact) => (
            <div key={fact.label}>
              <dt>{fact.label}</dt>
              <dd>{fact.value}</dd>
            </div>
          ))}
        </dl>
        <div className="c17-plan-actions">
          <a className="c17-btn" href={href} target="_blank" rel="noreferrer">
            Download {release.version}
          </a>
          <a className="c17-btn c17-btn-ghost" href={REPO} target="_blank" rel="noreferrer">
            Read the source
          </a>
        </div>
      </div>

      <div className="c17-all reveal" id="everything">
        <h3 className="c17-all-h">Everything in {release.version}</h3>
        <div className="c17-all-grid">
          {release.everything.map((group) => (
            <div key={group.group}>
              <p className="c17-all-tag">{group.group}</p>
              <ul>
                {group.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>

      <div className="c17-more">
        {ALL_RELEASES.filter((r) => r !== release).map((other) => (
          <Link className="c17-more-card reveal" to={releasePath(other)} key={other.version}>
            <span>Earlier release</span>
            <b>Exhale {other.version}</b>
            <i>{other.date}</i>
          </Link>
        ))}
        <a className="c17-more-card reveal" href={`${REPO}/blob/master/CHANGELOG.md`} target="_blank" rel="noreferrer">
          <span>Changelog</span>
          <b>Every fix, with the reason it broke</b>
          <i>On GitHub</i>
        </a>
      </div>
    </section>
  )
}

/* --------------------------------------------------------------------- page */

export default function ReleaseColorOS({ release }) {
  useReveals()

  useEffect(() => {
    const hash = window.location.hash.slice(1)
    const target = hash && document.getElementById(hash)
    if (target) target.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [])

  return (
    <div id="top" className="c17">
      <TopBar />
      <ChapterNav chapters={release.chapters} />
      <main>
        <Hero release={release} />

        <section className="c17-chapter c17-chapter-hl" id="highlights">
          <BigTitle title="Highlights" />
          <HighlightRail cards={release.highlights} />
        </section>

        {release.sections.map((section) => (
          <Chapter section={section} key={section.id} />
        ))}

        <GetIt release={release} />
      </main>
      <SiteFooter />
    </div>
  )
}
