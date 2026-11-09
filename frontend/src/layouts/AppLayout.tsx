import { BookOpen, FileText, FolderOpen, GraduationCap, LayoutDashboard, Loader2, LogOut, Menu, MessageSquare, Moon, Settings, Shield, Sun, X, type LucideIcon } from 'lucide-react';
import { Suspense, useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Logo } from '@/components/common';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { cn } from '@/utils/cn';
import { initials } from '@/utils/format';

const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/courses', label: 'My Courses', icon: BookOpen },
  { to: '/documents', label: 'All Documents', icon: FileText },
  { to: '/chat', label: 'AI Chats', icon: MessageSquare },
  { to: '/quizzes', label: 'Quiz Center', icon: GraduationCap },
  { to: '/folders', label: 'Shared Folders', icon: FolderOpen },
  { to: '/settings', label: 'Settings', icon: Settings },
];

function NavItems({ rail, onNavigate, admin }: { rail: boolean; onNavigate?: () => void; admin: boolean }) {
  const items = admin ? [...NAV, { to: '/admin', label: 'Admin', icon: Shield }] : NAV;
  return (
    <nav className="flex-1 space-y-0.5 overflow-y-auto px-2" aria-label="Main">
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          title={rail ? label : undefined}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              'flex h-9 items-center gap-2.5 rounded-md px-2.5 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground',
              isActive && 'bg-primary-soft font-medium text-primary hover:bg-primary-soft hover:text-primary',
              rail && 'justify-center px-0',
            )
          }
        >
          <Icon className="size-4 shrink-0" />
          {!rail && label}
          {rail && <span className="sr-only">{label}</span>}
        </NavLink>
      ))}
    </nav>
  );
}

function UserMenu({ rail }: { rail: boolean }) {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const navigate = useNavigate();
  if (!user) return null;
  const isDark = document.documentElement.classList.contains('dark');
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button aria-label="Account menu" className={cn('flex w-full items-center gap-2.5 rounded-md p-2 text-left hover:bg-accent', rail && 'justify-center')}>
          <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">{initials(user.name)}</span>
          {!rail && (
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{user.name}</span>
              <span className="block truncate text-xs text-muted-foreground">{user.email}</span>
            </span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent side="top" align="start" className="w-56">
        <DropdownMenuLabel>{user.role.replace('_', ' ')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => setTheme(isDark ? 'light' : 'dark')} data-theme-state={theme}>
          {isDark ? <Sun /> : <Moon />} {isDark ? 'Light mode' : 'Dark mode'}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => navigate('/settings')}>
          <Settings /> Settings
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => logout().then(() => navigate('/login'))}>
          <LogOut /> Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function AppLayout() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  // The chat screen has its own panels, so the main navigation shrinks to an icon rail there.
  const rail = pathname.startsWith('/chat');
  const fullBleed = rail;
  const admin = user?.role === 'ADMIN';

  useEffect(() => setMobileOpen(false), [pathname]);

  return (
    <div className="flex h-dvh overflow-hidden">
      <aside className={cn('hidden shrink-0 flex-col border-r bg-sidebar transition-[width] duration-200 md:flex', rail ? 'w-14' : 'w-60')}>
        <div className={cn('flex h-14 items-center', rail ? 'justify-center' : 'px-4')}>
          <Logo compact={rail} />
        </div>
        <NavItems rail={rail} admin={admin} />
        <div className="p-2">
          <UserMenu rail={rail} />
        </div>
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <button aria-label="Close menu" className="absolute inset-0 bg-black/40" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-sidebar shadow-pop">
            <div className="flex h-14 items-center justify-between px-4">
              <Logo />
              <Button variant="ghost" size="icon-sm" aria-label="Close menu" onClick={() => setMobileOpen(false)}>
                <X />
              </Button>
            </div>
            <NavItems rail={false} admin={admin} onNavigate={() => setMobileOpen(false)} />
            <div className="p-2">
              <UserMenu rail={false} />
            </div>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-12 shrink-0 items-center gap-2 border-b px-3 md:hidden">
          <Button variant="ghost" size="icon-sm" aria-label="Open menu" onClick={() => setMobileOpen(true)}>
            <Menu />
          </Button>
          <Logo />
        </header>
        <main className={cn('min-h-0 flex-1', fullBleed ? 'overflow-hidden' : 'scroll-thin overflow-y-auto')}>
          <Suspense fallback={<div className="grid h-full place-items-center" aria-label="Loading"><Loader2 className="size-5 animate-spin text-primary" /></div>}>
            {fullBleed ? (
              <Outlet />
            ) : (
              <div className="mx-auto max-w-6xl px-4 py-6 md:px-8 md:py-8">
                <Outlet />
              </div>
            )}
          </Suspense>
        </main>
      </div>
    </div>
  );
}
