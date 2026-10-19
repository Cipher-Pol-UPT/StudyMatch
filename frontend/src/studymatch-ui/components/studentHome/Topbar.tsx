import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, User, X } from 'lucide-react';

const SIDE_GROUPS = [
  ['My Subjects', 'StudyRooms', 'My Groups', 'My Assignments'],
  ['Student Information', 'My Profile', 'Notifications'],
];
const PROFILE_ITEMS = ['Student Information', 'My Profile', 'Notifications', 'Log Out'];

export function TopBar({ onNavigate = (l: string) => console.log('navigate:', l) }: { onNavigate?: (label: string) => void }) {
  const [side, setSide] = useState(false);
  const [menu, setMenu] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && (setSide(false), setMenu(false));
    const onDown = (e: MouseEvent) => menuRef.current && !menuRef.current.contains(e.target as Node) && setMenu(false);
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousedown', onDown);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mousedown', onDown);
    };
  }, []);

  const pick = (label: string) => {
    onNavigate(label);
    setSide(false);
    setMenu(false);
  };

  return (
    <>
      <header className="sh-topbar">
        <button className="sh-icon-btn" aria-label="Open menu" aria-expanded={side} aria-controls="sh-sidebar" onClick={() => setSide(true)}>
          <Menu size={26} />
        </button>

        <div className="sh-profile" ref={menuRef}>
          <button className="sh-icon-btn" aria-label="Profile menu" aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu((v) => !v)}>
            <User size={26} fill="currentColor" />
          </button>
          <AnimatePresence>
            {menu && (
              <motion.ul
                role="menu"
                className="sh-dropdown"
                initial={{ opacity: 0, y: -8, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -8, scale: 0.96 }}
                transition={{ duration: 0.18 }}
              >
                {PROFILE_ITEMS.map((l) => (
                  <li key={l} role="none">
                    <button role="menuitem" onClick={() => pick(l)}>{l}</button>
                  </li>
                ))}
              </motion.ul>
            )}
          </AnimatePresence>
        </div>
      </header>

      <AnimatePresence>
        {side && (
          <>
            <motion.div className="sh-scrim" onClick={() => setSide(false)} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />
            <motion.nav
              id="sh-sidebar"
              aria-label="Main"
              className="sh-sidebar"
              initial={{ x: '-105%' }}
              animate={{ x: 0 }}
              exit={{ x: '-105%' }}
              transition={{ type: 'spring', stiffness: 260, damping: 30 }}
            >
              <button className="sh-icon-btn sh-sidebar__close" aria-label="Close menu" onClick={() => setSide(false)}>
                <X size={26} />
              </button>
              {SIDE_GROUPS.map((g, i) => (
                <ul key={i} className="sh-sidebar__group">
                  {g.map((l) => (
                    <li key={l}><button onClick={() => pick(l)}>{l}</button></li>
                  ))}
                </ul>
              ))}
              <button className="sh-sidebar__logout" onClick={() => pick('Log Out')}>Log Out</button>
            </motion.nav>
          </>
        )}
      </AnimatePresence>
    </>
  );
}