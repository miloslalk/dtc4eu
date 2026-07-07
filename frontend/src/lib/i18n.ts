import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from '../locales/en.json';

// Interface languages are still being confirmed (spec §9); English ships now
// and additional locales drop into src/locales/ as they are supplied.
export const LANGUAGES = [{ code: 'en', label: 'English' }];

i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: localStorage.getItem('dtc4eu.lang') ?? 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', (lng) => localStorage.setItem('dtc4eu.lang', lng));

export default i18n;
