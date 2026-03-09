'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { Link2, AlertCircle } from 'lucide-react';
import { Field, FieldLabel, FieldGroup } from '@/components/ui/field';

interface AuthFormProps {
  mode: 'login' | 'signup';
}

export function AuthForm({ mode }: AuthFormProps) {
  const { login, signup } = useAuth();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    const result = mode === 'login' 
      ? await login({ email: formData.email, password: formData.password })
      : await signup(formData);

    if (result.error) {
      setError(result.error);
      setIsLoading(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({
      ...prev,
      [e.target.name]: e.target.value,
    }));
  };

  return (
    <div className="min-h-screen bg-background">
      <div className="flex min-h-screen flex-col items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          {/* Logo */}
          <Link href="/" className="mb-8 flex items-center justify-center gap-2">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary">
              <Link2 className="h-5 w-5 text-primary-foreground" />
            </div>
            <span className="text-xl font-semibold text-foreground">ShortLink</span>
          </Link>

          {/* Form Card */}
          <div className="rounded-lg border border-border bg-card p-6">
            <div className="mb-6 text-center">
              <h1 className="text-xl font-semibold text-foreground">
                {mode === 'login' ? 'Welcome back' : 'Create your account'}
              </h1>
              <p className="mt-1 text-sm text-muted-foreground">
                {mode === 'login' 
                  ? 'Sign in to manage your links' 
                  : 'Start shortening URLs for free'}
              </p>
            </div>

            {error && (
              <div className="mb-4 flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit}>
              <FieldGroup>
                {mode === 'signup' && (
                  <Field>
                    <FieldLabel htmlFor="name">Name</FieldLabel>
                    <Input
                      id="name"
                      name="name"
                      type="text"
                      placeholder="Enter your name"
                      value={formData.name}
                      onChange={handleChange}
                      required
                      className="bg-secondary"
                    />
                  </Field>
                )}

                <Field>
                  <FieldLabel htmlFor="email">Email</FieldLabel>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    placeholder="Enter your email"
                    value={formData.email}
                    onChange={handleChange}
                    required
                    className="bg-secondary"
                  />
                </Field>

                <Field>
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <Input
                    id="password"
                    name="password"
                    type="password"
                    placeholder="Enter your password"
                    value={formData.password}
                    onChange={handleChange}
                    required
                    minLength={6}
                    className="bg-secondary"
                  />
                </Field>

                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading ? (
                    <Spinner className="h-4 w-4" />
                  ) : mode === 'login' ? (
                    'Sign in'
                  ) : (
                    'Create account'
                  )}
                </Button>
              </FieldGroup>
            </form>
          </div>

          {/* Footer */}
          <p className="mt-6 text-center text-sm text-muted-foreground">
            {mode === 'login' ? (
              <>
                {"Don't have an account? "}
                <Link href="/signup" className="text-primary hover:underline">
                  Sign up
                </Link>
              </>
            ) : (
              <>
                Already have an account?{' '}
                <Link href="/login" className="text-primary hover:underline">
                  Sign in
                </Link>
              </>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
