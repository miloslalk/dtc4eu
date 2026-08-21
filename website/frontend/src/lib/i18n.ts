import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from '../locales/en.json';

/**
 * UI-chrome strings only — all content comes from the CMS. English-only for
 * now; additional locales slot in here and in the CMS locale columns later.
 */
export const LANGUAGES = [{ code: 'en', label: 'English' }];

i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

export default i18n;
