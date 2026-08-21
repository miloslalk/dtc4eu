import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../lib/api';
import type { Block, BlockType } from '../lib/types';

/* ------------------------------- types ------------------------------- */

export type Role = 'ADMIN' | 'EDITOR';
export type ContentStatus = 'DRAFT' | 'PUBLISHED';

export interface Me {
  id: number;
  username: string;
  fullName: string;
  role: Role;
}

export interface PageSummary {
  id: number;
  slug: string;
  title: string;
  status: ContentStatus;
  showInNav: boolean;
  navLabel: string | null;
  navOrder: number;
  updatedAt: string;
}

export interface PageDetail extends PageSummary {
  seoTitle: string | null;
  seoDescription: string | null;
  blocks: Block[];
}

export interface PageMeta {
  slug: string;
  title: string;
  showInNav: boolean;
  navLabel: string | null;
  navOrder: number;
  seoTitle: string | null;
  seoDescription: string | null;
}

export interface AdminNews {
  id: number;
  slug: string;
  title: string;
  excerpt: string | null;
  bodyHtml: string;
  coverMediaId: number | null;
  status: ContentStatus;
  publishedAt: string | null;
  updatedAt: string;
}

export interface Media {
  id: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  altText: string | null;
  url: string;
}

export interface AdminGalleryImage {
  id: number;
  mediaId: number;
  caption: string | null;
  url: string;
  altText: string;
}

export interface AdminGallery {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  images: AdminGalleryImage[];
}

export interface Submission {
  id: number;
  name: string;
  email: string;
  subject: string | null;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface AdminUserRow {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export type Settings = Record<string, Record<string, unknown>>;

/* ------------------------------- hooks ------------------------------- */

const invalidate = (queryClient: ReturnType<typeof useQueryClient>, key: string) => () =>
  queryClient.invalidateQueries({ queryKey: ['admin', key] });

export function useMe(enabled: boolean) {
  return useQuery({
    queryKey: ['admin', 'me'],
    queryFn: async () => (await adminApi.get<Me>('/auth/me')).data,
    enabled,
    retry: false,
  });
}

export function usePages() {
  return useQuery({
    queryKey: ['admin', 'pages'],
    queryFn: async () => (await adminApi.get<PageSummary[]>('/pages')).data,
  });
}

export function usePageDetail(id: number) {
  return useQuery({
    queryKey: ['admin', 'pages', id],
    queryFn: async () => (await adminApi.get<PageDetail>(`/pages/${id}`)).data,
  });
}

export function usePageMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'pages');
  return {
    create: useMutation({
      mutationFn: async (meta: PageMeta) => (await adminApi.post<PageDetail>('/pages', meta)).data,
      onSuccess: done,
    }),
    updateMeta: useMutation({
      mutationFn: async ({ id, meta }: { id: number; meta: PageMeta }) =>
        (await adminApi.put<PageDetail>(`/pages/${id}`, meta)).data,
      onSuccess: done,
    }),
    saveBlocks: useMutation({
      mutationFn: async ({ id, blocks }: { id: number; blocks: { blockType: BlockType; payload: Record<string, unknown> }[] }) =>
        (await adminApi.put<PageDetail>(`/pages/${id}/blocks`, { blocks })).data,
      onSuccess: done,
    }),
    setPublished: useMutation({
      mutationFn: async ({ id, publish }: { id: number; publish: boolean }) =>
        (await adminApi.post<PageDetail>(`/pages/${id}/${publish ? 'publish' : 'unpublish'}`)).data,
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => adminApi.delete(`/pages/${id}`),
      onSuccess: done,
    }),
  };
}

export function useAdminNews() {
  return useQuery({
    queryKey: ['admin', 'news'],
    queryFn: async () => (await adminApi.get<AdminNews[]>('/news')).data,
  });
}

export interface SaveNews {
  slug: string;
  title: string;
  excerpt: string | null;
  bodyHtml: string;
  coverMediaId: number | null;
}

export function useNewsMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'news');
  return {
    create: useMutation({
      mutationFn: async (body: SaveNews) => (await adminApi.post<AdminNews>('/news', body)).data,
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: async ({ id, body }: { id: number; body: SaveNews }) =>
        (await adminApi.put<AdminNews>(`/news/${id}`, body)).data,
      onSuccess: done,
    }),
    setPublished: useMutation({
      mutationFn: async ({ id, publish }: { id: number; publish: boolean }) =>
        (await adminApi.post<AdminNews>(`/news/${id}/${publish ? 'publish' : 'unpublish'}`)).data,
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => adminApi.delete(`/news/${id}`),
      onSuccess: done,
    }),
  };
}

export function useMedia() {
  return useQuery({
    queryKey: ['admin', 'media'],
    queryFn: async () => (await adminApi.get<Media[]>('/media')).data,
  });
}

export function useMediaMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'media');
  return {
    upload: useMutation({
      mutationFn: async ({ file, altText }: { file: File; altText?: string }) => {
        const form = new FormData();
        form.append('file', file);
        if (altText) form.append('altText', altText);
        return (await adminApi.post<Media>('/media', form)).data;
      },
      onSuccess: done,
    }),
    updateAlt: useMutation({
      mutationFn: async ({ id, altText }: { id: number; altText: string }) =>
        adminApi.put(`/media/${id}`, { altText }),
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => adminApi.delete(`/media/${id}`),
      onSuccess: done,
    }),
  };
}

export function useAdminGalleries() {
  return useQuery({
    queryKey: ['admin', 'galleries'],
    queryFn: async () => (await adminApi.get<AdminGallery[]>('/galleries')).data,
  });
}

export function useGalleryMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'galleries');
  return {
    create: useMutation({
      mutationFn: async (body: { slug: string; name: string; description: string | null }) =>
        (await adminApi.post<AdminGallery>('/galleries', body)).data,
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: async ({ id, body }: { id: number; body: { slug: string; name: string; description: string | null } }) =>
        (await adminApi.put<AdminGallery>(`/galleries/${id}`, body)).data,
      onSuccess: done,
    }),
    saveImages: useMutation({
      mutationFn: async ({ id, images }: { id: number; images: { mediaId: number; caption: string | null }[] }) =>
        (await adminApi.put<AdminGallery>(`/galleries/${id}/images`, { images })).data,
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => adminApi.delete(`/galleries/${id}`),
      onSuccess: done,
    }),
  };
}

export function useSubmissions() {
  return useQuery({
    queryKey: ['admin', 'inbox'],
    queryFn: async () => (await adminApi.get<Submission[]>('/contact-submissions')).data,
  });
}

export function useSubmissionMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'inbox');
  return {
    setRead: useMutation({
      mutationFn: async ({ id, read }: { id: number; read: boolean }) =>
        adminApi.post(`/contact-submissions/${id}/read`, { read }),
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: async (id: number) => adminApi.delete(`/contact-submissions/${id}`),
      onSuccess: done,
    }),
  };
}

export function useSettings() {
  return useQuery({
    queryKey: ['admin', 'settings'],
    queryFn: async () => (await adminApi.get<Settings>('/settings')).data,
  });
}

export function useSaveSetting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ key, value }: { key: string; value: Record<string, unknown> }) =>
      adminApi.put(`/settings/${key}`, value),
    onSuccess: invalidate(queryClient, 'settings'),
  });
}

export function useAdminUsers() {
  return useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () => (await adminApi.get<AdminUserRow[]>('/users')).data,
  });
}

export function useUserMutations() {
  const queryClient = useQueryClient();
  const done = invalidate(queryClient, 'users');
  return {
    create: useMutation({
      mutationFn: async (body: { username: string; password: string; fullName: string; role: Role }) =>
        (await adminApi.post<AdminUserRow>('/users', body)).data,
      onSuccess: done,
    }),
    update: useMutation({
      mutationFn: async ({ id, body }: { id: number; body: { fullName: string; role: Role; active: boolean; password?: string } }) =>
        (await adminApi.put<AdminUserRow>(`/users/${id}`, body)).data,
      onSuccess: done,
    }),
  };
}
