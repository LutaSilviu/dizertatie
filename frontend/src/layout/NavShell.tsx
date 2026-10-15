import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import styles from './NavShell.module.css'

const navLinks = [
  { to: '/analyzer', label: 'Analyzer' },
  { to: '/benchmark', label: 'Benchmark' },
  { to: '/settings', label: 'Settings' },
]

function NavShell() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <nav className={styles.nav} aria-label="Main navigation">
          <span className={styles.brand}>A11y Analyzer</span>
          <ul
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
            onClick={() => setMenuOpen((prev) => !prev)}
          >
            &#9776;
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
