import { useMemo, useState } from 'react'
import { Link } from '../router.jsx'
import { MAINTAINER, RELEASES, REPO, TELEGRAM, TELEGRAM_CHANNEL } from '../content.js'
import { ALL_RELEASES, LATEST_RELEASE as RELEASE, releasePath } from '../releases.js'
import { DownloadIcon, GithubIcon, Mark, TelegramIcon } from '../icons.jsx'

export const RELEASE_PATH = `/release/${RELEASE.version}`

/*
 * The parts every page wears.
 *
 * These lived inside App while there was only one page. There are two now, and
 * a footer that is right on one of them and stale on the other is worse than no
 * footer, so they moved here — one definition, both pages.
 */

/**
 * The announcement strip.
 *
 * Full width, one sentence, and the only thing on the page above the nav. A
 * release is news exactly once; this is where news goes, and it is a link
 * rather than a banner you have to dismiss, because nothing here interrupts
 * anything.
 */
export function AnnounceBar() {
  const [shut, setShut] = useState(false)
  if (shut) return null
  // OPPO's promo strip: one centred sentence, an underlined call, and an ×.
  return (
    <div className="announce">
      <span className="announce-text">Exhale {RELEASE.version} is here. Ultra fluid. Knows your music.</span>
      <Link className="announce-go" to={RELEASE_PATH}>
        See what’s new
      </Link>
      <button type="button" className="announce-close" aria-label="Close" onClick={() => setShut(true)}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
      </button>
    </div>
  )
}

/**
 * The global bar, as OPPO's: white, the mark and the name on the left with the
 * main links beside them, the quieter links and the icons on the right. It
 * scrolls away with the page. Hovering Releases opens a panel down the full
 * width listing every release page, the way OPPO's product menus open.
 */
export function TopBar() {
  const [menu, setMenu] = useState(false)
  return (
    <div className="ohdr" onMouseLeave={() => setMenu(false)}>
      <nav className="ohdr-row" aria-label="Project">
        <Link className="ohdr-brand" to="/" aria-label="Exhale, home">
          <Mark />
          <span>Exhale</span>
        </Link>
        <div className="ohdr-main">
          <a href={RELEASES} target="_blank" rel="noreferrer">
            Download
          </a>
          <button
            type="button"
            className="ohdr-menu"
            data-open={menu}
            aria-expanded={menu}
            onMouseEnter={() => setMenu(true)}
            onClick={() => setMenu((m) => !m)}
          >
            Releases
          </button>
          <a className="hide-sm" href={REPO} target="_blank" rel="noreferrer">
            Source
          </a>
          <Link className="hide-sm" to="/support">
            Support
          </Link>
        </div>
        <div className="ohdr-side">
          <a className="hide-sm" href={`${REPO}/blob/master/CONTRIBUTING.md`} target="_blank" rel="noreferrer">
            Contributing
          </a>
          <a className="hide-sm" href={`${REPO}/blob/master/LICENSE`} target="_blank" rel="noreferrer">
            GPL-3.0
          </a>
          <a className="ohdr-icon" href={REPO} target="_blank" rel="noreferrer" aria-label="Source on GitHub">
            <GithubIcon />
          </a>
          <a className="ohdr-icon" href={TELEGRAM_CHANNEL} target="_blank" rel="noreferrer" aria-label="Exhale on Telegram">
            <TelegramIcon />
          </a>
          <a className="ohdr-icon" href={RELEASES} target="_blank" rel="noreferrer" aria-label="Download the APK">
            <DownloadIcon />
          </a>
        </div>
      </nav>
      <div className="ohdr-panel" data-open={menu} aria-hidden={!menu}>
        <div className="ohdr-panel-row">
          {ALL_RELEASES.map((r, i) => (
            <Link key={r.version} className="ohdr-rel" to={releasePath(r)} onClick={() => setMenu(false)} tabIndex={menu ? 0 : -1}>
              <b>Exhale {r.version}</b>
              <span>{i === 0 ? 'Latest' : r.date}</span>
            </Link>
          ))}
          <a className="ohdr-rel" href={`${REPO}/releases`} target="_blank" rel="noreferrer" tabIndex={menu ? 0 : -1}>
            <b>All releases</b>
            <span>On GitHub</span>
          </a>
        </div>
      </div>
    </div>
  )
}

/**
 * @param onNotes opens the short release-notes sheet. Only the home page has
 *   one mounted, so the two entries that open it are omitted without it rather
 *   than rendered dead.
 */
export function SiteFooter({ onNotes }) {
  const year = useMemo(() => new Date().getFullYear(), [])

  return (
    <footer className="footer">
      <div className="shell">
        <div className="footer-mark">
          <img src="/logo.png" alt="" width="30" height="30" />
          Exhale
        </div>

        <div className="footer-grid">
          <div>
            <h4>Exhale</h4>
            <ul>
              <li>
                <Link to="/">Overview</Link>
              </li>
              <li>
                <a href="/#lyrics">Lyrics</a>
              </li>
              <li>
                <a href="/#design">Design</a>
              </li>
              <li>
                <a href="/#features">Features</a>
              </li>
              <li>
                <a href="/#download">Download</a>
              </li>
            </ul>
          </div>
          <div>
            <h4>This release</h4>
            <ul>
              <li>
                <Link to={RELEASE_PATH}>Exhale {RELEASE.version}</Link>
              </li>
              {onNotes ? (
                <li>
                  <button type="button" onClick={onNotes}>
                    What&rsquo;s new, in short
                  </button>
                </li>
              ) : null}
              <li>
                <a href={`${REPO}/blob/master/CHANGELOG.md`} target="_blank" rel="noreferrer">
                  Full changelog
                </a>
              </li>
              <li>
                <a href={RELEASES} target="_blank" rel="noreferrer">
                  Download the APK
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Source</h4>
            <ul>
              <li>
                <a href={REPO} target="_blank" rel="noreferrer">
                  Repository
                </a>
              </li>
              <li>
                <a href={`${REPO}/releases`} target="_blank" rel="noreferrer">
                  Releases
                </a>
              </li>
              <li>
                <Link to="/support">Support</Link>
              </li>
              <li>
                <a href={TELEGRAM_CHANNEL} target="_blank" rel="noreferrer">
                  Telegram channel
                </a>
              </li>
              <li>
                <a href={`${REPO}/issues`} target="_blank" rel="noreferrer">
                  Open issues
                </a>
              </li>
              <li>
                <a
                  href={`${REPO}/blob/master/CONTRIBUTING.md`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Contributing
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Built on</h4>
            <ul>
              <li>
                <a href="https://github.com/Arturo254/OpenTune" target="_blank" rel="noreferrer">
                  OpenTune
                </a>
              </li>
              <li>
                <a href="https://lrclib.net" target="_blank" rel="noreferrer">
                  LRCLIB
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/Kyant0/AndroidLiquidGlass"
                  target="_blank"
                  rel="noreferrer"
                >
                  AndroidLiquidGlass
                </a>
              </li>
              <li>
                <a href="https://github.com/chrisbanes/haze" target="_blank" rel="noreferrer">
                  Haze
                </a>
              </li>
            </ul>
          </div>
          <div>
            <h4>Maintainer</h4>
            <ul>
              <li>
                {MAINTAINER.name} — {MAINTAINER.role}
              </li>
              <li>
                <a href={REPO.replace('/Exhale', '')} target="_blank" rel="noreferrer">
                  GitHub {MAINTAINER.handle}
                </a>
              </li>
              <li>
                <a href={TELEGRAM} target="_blank" rel="noreferrer">
                  Telegram {MAINTAINER.handle}
                </a>
              </li>
              <li>
                <a href={`${REPO}/blob/master/CODE_OF_CONDUCT.md`} target="_blank" rel="noreferrer">
                  Code of conduct
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="footer-legal">
          <p>
            © {year} ozyern. Exhale is free software under the GNU GPL v3, built on
            the work of OpenTune.
          </p>
          <p>
            Not affiliated with, endorsed by, or connected to Apple, Google or
            YouTube. Album titles, artists and lyrics shown on this site are
            placeholders written for the demo.
          </p>
        </div>
      </div>
    </footer>
  )
}
