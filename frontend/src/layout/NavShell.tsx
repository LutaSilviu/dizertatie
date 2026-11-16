import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import styles from './NavShell.module.css'

const navLinks = [
  { to: '/analyzer', label: 'Analyzer' },
  { to: '/benchmark', label: 'Benchmark' },
  { to: '/history', label: 'History' },
  { to: '/settings', label: 'Settings' },
]

function NavShell() {
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()

  // Close the mobile menu on navigation and on Escape.
  useEffect(() => setMenuOpen(false), [location.pathname])
  useEffect(() => {
    if (!menuOpen) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [menuOpen])

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <nav className={styles.nav} aria-label="Main navigation">
          <span className={styles.brand}>A11y Analyzer</span>
          <ul
            id="main-nav-list"
            className={[styles.navList, menuOpen ? styles.navListOpen : ''].join(' ').trim()}
          >
            {navLinks.map(({ to, label }) => (
              <li key={to} className={styles.navItem}>
                <NavLink
                  to={to}
                  className={({ isActive }) =>
                    [styles.navLink, isActive ? styles.navLinkActive : ''].join(' ').trim()
                  }
                  onClick={() => setMenuOpen(false)}
                >
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
          <button
            className={styles.menuToggle}
            aria-label="Toggle navigation menu"
            aria-expanded={menuOpen}
            aria-controls="main-nav-list"
            onClick={() => setMenuOpen((prev) => !prev)}
          >
            {menuOpen ? '✕' : '☰'}
          </button>
        </nav>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}

export default NavShell
