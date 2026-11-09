import { useMutation } from '@tanstack/react-query';
import { Monitor, Moon, Sun } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { PageHeader } from '@/components/common';
import { PasswordInput } from '@/pages/LoginPage';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { FieldError, Input, Label } from '@/components/ui/input';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme, type Theme } from '@/contexts/ThemeContext';
import { errorMessage } from '@/services/api';
import { authApi } from '@/services/endpoints';
import { cn } from '@/utils/cn';

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
];

export function SettingsPage() {
  const { user, setUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const [name, setName] = useState(user?.name ?? '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string>();

  const saveProfile = useMutation({
    mutationFn: () => authApi.updateProfile({ name: name.trim() }),
    onSuccess: (updated) => {
      setUser(updated);
      toast.success('Profile updated');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const savePassword = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      setCurrentPassword('');
      setNewPassword('');
      setPasswordError(undefined);
      toast.success('Password changed');
    },
    onError: (error) => setPasswordError(errorMessage(error)),
  });

  const passwordValid = newPassword.length >= 8 && /[A-Za-z]/.test(newPassword) && /\d/.test(newPassword);

  return (
    <div className="max-w-2xl">
      <PageHeader title="Settings" description="Manage your profile and preferences." />
      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle>Profile</CardTitle></CardHeader>
          <CardContent>
            <form
              className="space-y-4"
              onSubmit={(event) => {
                event.preventDefault();
                saveProfile.mutate();
              }}
            >
              <div className="space-y-1.5">
                <Label htmlFor="settings-name">Full name</Label>
                <Input id="settings-name" value={name} maxLength={120} onChange={(event) => setName(event.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="settings-email">Email</Label>
                <Input id="settings-email" value={user?.email ?? ''} disabled />
              </div>
              <Button type="submit" loading={saveProfile.isPending} disabled={!name.trim()}>Save</Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Appearance</CardTitle></CardHeader>
          <CardContent>
            <div className="grid grid-cols-3 gap-2">
              {THEMES.map(({ value, label, icon: Icon }) => (
                <button
                  key={value}
                  onClick={() => setTheme(value)}
                  aria-pressed={theme === value}
                  className={cn('flex flex-col items-center gap-1.5 rounded-lg border p-3 text-[13px] transition-colors hover:border-primary/50', theme === value && 'border-primary bg-primary-soft font-medium text-primary')}
                >
                  <Icon className="size-4" /> {label}
                </button>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Change password</CardTitle></CardHeader>
          <CardContent>
            <form
              className="space-y-4"
              onSubmit={(event) => {
                event.preventDefault();
                savePassword.mutate();
              }}
            >
              <div className="space-y-1.5">
                <Label htmlFor="settings-current">Current password</Label>
                <PasswordInput id="settings-current" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="settings-new">New password</Label>
                <PasswordInput id="settings-new" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
                <p className="text-[13px] text-muted-foreground">At least 8 characters, with a letter and a number.</p>
              </div>
              <FieldError message={passwordError} />
              <Button type="submit" loading={savePassword.isPending} disabled={!currentPassword || !passwordValid}>Update password</Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
