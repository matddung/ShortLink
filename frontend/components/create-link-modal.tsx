'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { Field, FieldLabel, FieldGroup } from '@/components/ui/field';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { AlertCircle } from 'lucide-react';
import { linksApi } from '@/lib/api-client';
import type { Link } from '@/lib/types';

interface CreateLinkModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: (link: Link) => void;
}

export function CreateLinkModal({ open, onOpenChange, onSuccess }: CreateLinkModalProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [formData, setFormData] = useState({
    originalUrl: '',
    customCode: '',
  });

  const isValidUrl = (string: string) => {
    try {
      new URL(string);
      return true;
    } catch {
      return false;
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!formData.originalUrl.trim()) {
      setError('Please enter a URL');
      return;
    }

    if (!isValidUrl(formData.originalUrl)) {
      setError('Please enter a valid URL');
      return;
    }

    setIsLoading(true);
    const response = await linksApi.create({
      originalUrl: formData.originalUrl,
      customCode: formData.customCode || undefined,
    });
    setIsLoading(false);

    if (response.error) {
      setError(response.error);
      return;
    }

    if (response.data) {
      onSuccess(response.data);
      setFormData({ originalUrl: '', customCode: '' });
      onOpenChange(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData((prev) => ({
      ...prev,
      [e.target.name]: e.target.value,
    }));
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create new link</DialogTitle>
          <DialogDescription>
            Enter the URL you want to shorten. Optionally, provide a custom short code.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <div className="flex items-center gap-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="originalUrl">Destination URL</FieldLabel>
              <Input
                id="originalUrl"
                name="originalUrl"
                type="text"
                placeholder="https://example.com/very-long-url"
                value={formData.originalUrl}
                onChange={handleChange}
                className="bg-secondary"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="customCode">
                Custom code <span className="text-muted-foreground">(optional)</span>
              </FieldLabel>
              <div className="flex items-center gap-2">
                <span className="text-sm text-muted-foreground">shrt.link/</span>
                <Input
                  id="customCode"
                  name="customCode"
                  type="text"
                  placeholder="my-link"
                  value={formData.customCode}
                  onChange={handleChange}
                  className="flex-1 bg-secondary"
                  pattern="[a-zA-Z0-9-]+"
                />
              </div>
            </Field>

            <div className="flex justify-end gap-2 pt-2">
              <Button 
                type="button" 
                variant="outline" 
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isLoading}>
                {isLoading ? <Spinner className="h-4 w-4" /> : 'Create link'}
              </Button>
            </div>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  );
}
