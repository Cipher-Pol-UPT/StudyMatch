import { ChevronRight,Menu as MenuIcon } from 'lucide-react';
import { Fragment,useEffect,useState,type ElementType,type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../../../i18n';
import { cx } from '../../utils/cx';
import type { IconComponent } from '../../utils/shared';
import { Badge } from '../badge/Badge';
import '../button/button.css';
import './navigation.css';

/**
 * Router-agnostic links. Pass your router's Link through `linkAs`;
 * StudyMatch passes `href` and also `to` (React Router), so both work.
 */
function NavLinkEl({ linkAs, href, className, children, current, onClick, label }: { linkAs?: ElementType; href?: string; className: string; children: ReactNode; current?: boolean; onClick?: () => void; label?: string }) {
  useTranslation();
  if (!href) {
    return (
      <button type="button" className={className} aria-current={current ? 'page' : undefined} onClick={onClick} aria-label={label}>
        {children}
      </button>
    );
  }
  const As = linkAs ?? 'a';
  return (
    <As className={className} href={href} to={linkAs ? href : undefined} aria-current={current ? 'page' : undefined} onClick={onClick} aria-label={label}>
      {children}
    </As>
  );
}

// ─── Tab bar ───────────────────────────────────────────────
export interface TabBarItem {
  value: string;
  label: string;
  icon: IconComponent;
  href?: string;
  /** The raised, gradient centre action. */
  fab?: boolean;
  badge?: number;
}

export interface TabBarProps {
  items: TabBarItem[];
  value: string;
  onValueChange?: (value: string) => void;
  linkAs?: ElementType;
  /** Pin to the bottom of the viewport. */
  fixed?: boolean;
  className?: string;
}

/** Mobile bottom navigation with an optional central action. */
export function TabBar({ items, value, onValueChange, linkAs, fixed, className }: TabBarProps) {
  useTranslation();
  return (
    <nav className={cx('sm-tabbar', fixed && 'sm-tabbar--fixed', className)} aria-label={i18n.t('ui.main')}>
      {items.map((it) => {
        const active = it.value === value;
        const Icon = it.icon;
        return (
          <NavLinkEl
            key={it.value}
            linkAs={linkAs}
            href={it.href}
            current={active}
            label={it.fab ? it.label : undefined}
            className={cx('sm-tabbar__item', it.fab && 'sm-tabbar__fab')}
            onClick={() => onValueChange?.(it.value)}
          >
            <Icon size={24} strokeWidth={active || it.fab ? 2.5 : 2} aria-hidden />
            {!it.fab && it.label}
            {it.badge ? <Badge className="sm-tabbar__badge">{it.badge}</Badge> : null}
          </NavLinkEl>
        );
      })}
    </nav>
  );
}

// ─── Breadcrumbs ───────────────────────────────────────────
export interface BreadcrumbsProps {
  items: { label: ReactNode; href?: string }[];
  linkAs?: ElementType;
  className?: string;
}

export function Breadcrumbs({ items, linkAs, className }: BreadcrumbsProps) {
  useTranslation();
  const As = linkAs ?? 'a';
  return (
    <nav className={cx('sm-crumbs', className)} aria-label={i18n.t('ui.breadcrumb')}>
      <ol>
        {items.map((it, i) => {
          const last = i === items.length - 1;
          return (
            <li key={i}>
              {last || !it.href ? (
                <span aria-current={last ? 'page' : undefined}>{it.label}</span>
              ) : (
                <As href={it.href} to={linkAs ? it.href : undefined}>
                  {it.label}
                </As>
              )}
              {!last && <ChevronRight size={14} strokeWidth={2.5} aria-hidden />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

// ─── App shell ─────────────────────────────────────────────
export interface NavItem {
  label: string;
  icon: IconComponent;
  href?: string;
  onClick?: () => void;
  badge?: number;
  /** Mark as current. Usually computed from the router location. */
  active?: boolean;
}

export interface NavSection {
  title?: string;
  items: NavItem[];
}

export interface AppShellProps {
  /** Logo area at the top of the sidebar. */
  brand: ReactNode;
  nav: NavSection[];
  /** Page title in the top bar. */
  title?: ReactNode;
  /** Right side of the top bar: search, notifications, user menu. */
  actions?: ReactNode;
  /** Bottom of the sidebar (e.g. the user chip). */
  sidebarFooter?: ReactNode;
  /** Your router's Link component. */
  linkAs?: ElementType;
  /** Changes on navigation; closes the mobile drawer. Pass `location.pathname`. */
  routeKey?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Sidebar + top bar + content. Collapses to a drawer under 960px.
 * Role-specific navigation (Student, Teacher, Staff, Admin) is just a different `nav`.
 */
export function AppShell({ brand, nav, title, actions, sidebarFooter, linkAs, routeKey, children, className }: AppShellProps) {
  useTranslation();
  const [openRoute, setOpenRoute] = useState<string | null>(null);
  const open = openRoute === (routeKey ?? '');
  const setOpen = (value: boolean) => setOpenRoute(value ? routeKey ?? '' : null);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpenRoute(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <div className={cx('sm-shell', open && 'sm-shell--open', className)}>
      <a className="sm-visually-hidden" href="#sm-main">
         {i18n.t('ui.skip_to_content')} </a>
      <aside className="sm-shell__side" aria-label={i18n.t('ui.main_navigation')}>
        <div className="sm-shell__brand">{brand}</div>
        <nav>
          {nav.map((section, si) => (
            <Fragment key={si}>
              {section.title && <div className="sm-shell__section">{section.title}</div>}
              {section.items.map((it) => {
                const Icon = it.icon;
                return (
                  <NavLinkEl
                    key={it.label}
                    linkAs={linkAs}
                    href={it.href}
                    current={it.active}
                    className="sm-nav-item"
                    onClick={() => {
                      it.onClick?.();
                      setOpen(false);
                    }}
                  >
                    <Icon size={19} strokeWidth={2.1} aria-hidden />
                    {it.label}
                    {it.badge ? <Badge>{it.badge}</Badge> : null}
                  </NavLinkEl>
                );
              })}
            </Fragment>
          ))}
        </nav>
        {sidebarFooter && <div className="sm-shell__foot">{sidebarFooter}</div>}
      </aside>
      <div className="sm-shell__scrim" onClick={() => setOpen(false)} aria-hidden />
      <div className="sm-shell__main">
        <header className="sm-shell__top">
          <button type="button" className="sm-icon-btn sm-icon-btn--sm sm-icon-btn--secondary sm-shell__menu" onClick={() => setOpen(true)} aria-label={i18n.t('ui.open_navigation')} aria-expanded={open}>
            <MenuIcon size={18} strokeWidth={2.25} />
          </button>
          <div className="sm-shell__title">{title}</div>
          {actions && <div className="sm-shell__actions">{actions}</div>}
        </header>
        <main id="sm-main" className="sm-shell__content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
