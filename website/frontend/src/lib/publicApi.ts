import { useQuery } from '@tanstack/react-query';
import { publicApi } from './api';
import type { Gallery, NavItem, NewsDetail, NewsItem, PublicPage, SiteSettings } from './types';

export function useNav() {
  return useQuery({
    queryKey: ['public', 'nav'],
    queryFn: async () => (await publicApi.get<NavItem[]>('/nav')).data,
    staleTime: 60_000,
  });
}

export function useSiteSettings() {
  return useQuery({
    queryKey: ['public', 'settings'],
    queryFn: async () => (await publicApi.get<SiteSettings>('/settings')).data,
    staleTime: 60_000,
  });
}

export function usePage(slug: string) {
  return useQuery({
    queryKey: ['public', 'page', slug],
    queryFn: async () => (await publicApi.get<PublicPage>(`/pages/${slug}`)).data,
    retry: false,
  });
}

export function useNews(limit: number) {
  return useQuery({
    queryKey: ['public', 'news', limit],
    queryFn: async () => (await publicApi.get<NewsItem[]>('/news', { params: { limit } })).data,
    staleTime: 60_000,
  });
}

export function useNewsDetail(slug: string) {
  return useQuery({
    queryKey: ['public', 'news-detail', slug],
    queryFn: async () => (await publicApi.get<NewsDetail>(`/news/${slug}`)).data,
    retry: false,
  });
}

export function useGallery(slug: string | null) {
  return useQuery({
    queryKey: ['public', 'gallery', slug],
    queryFn: async () => (await publicApi.get<Gallery>(`/galleries/${slug}`)).data,
    enabled: slug !== null,
    retry: false,
  });
}

export function useGalleryById(id: number | null) {
  return useQuery({
    queryKey: ['public', 'gallery-id', id],
    queryFn: async () => (await publicApi.get<Gallery>(`/galleries/id/${id}`)).data,
    enabled: id !== null,
    retry: false,
  });
}
