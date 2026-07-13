'use client';

import { useCallback, useEffect, useState } from 'react';
import { linksApi } from '@/lib/api-client';
import type { Link, LinkStatus } from '@/lib/types';

export function useLinks() {
  const [links, setLinks] = useState<Link[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [updatingId, setUpdatingId] = useState<string | undefined>();

  const fetchLinks = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    const response = await linksApi.getAll();
    if (response.error) {
      setError(response.error);
    } else if (response.data) {
      setLinks(response.data);
    }

    setIsLoading(false);
  }, []);

  useEffect(() => {
    fetchLinks();
  }, [fetchLinks]);

  const addLink = useCallback((newLink: Link) => {
    setLinks((prev) => [newLink, ...prev]);
  }, []);

  const updateStatus = useCallback(async (id: string, status: LinkStatus) => {
    setUpdatingId(id);
    setError(null);

    const response = await linksApi.updateStatus(id, status);
    if (response.error) {
      setError(response.error);
    } else if (response.data) {
      setLinks((prev) =>
        prev.map((link) => (link.id === id ? response.data! : link))
      );
    }

    setUpdatingId(undefined);
  }, []);

  return {
    links,
    isLoading,
    error,
    updatingId,
    fetchLinks,
    addLink,
    updateStatus,
  };
}
