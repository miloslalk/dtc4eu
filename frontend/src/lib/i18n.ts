import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from '../locales/en.json';

// English is bundled. The other interface languages are static JSON under
// public/locales/, fetched only when chosen, so they never weigh on the
// bundle. TR and PL are machine drafts awaiting partner review (spec §9) —
// questionnaire item text always stays in the client's verbatim wording.
export const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'tr', label: 'Türkçe (draft)' },
  { code: 'pl', label: 'Polski (draft)' },
];

const LAZY_LANGUAGES = new Set(['tr', 'pl']);

async function ensureLoaded(lng: string) {
  if (!LAZY_LANGUAGES.has(lng) || i18n.hasResourceBundle(lng, 'translation')) return;
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}locales/${lng}.json`);
    if (!response.ok) return;
    i18n.addResourceBundle(lng, 'translation', await response.json());
  } catch {
    // offline or missing file: i18next falls back to English
  }
}

function storedLanguage(): string {
  try {
    return localStorage.getItem('dtc4eu.lang') ?? 'en';
  } catch {
    return 'en';
  }
}

const initial = storedLanguage();

i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
});

// load the stored language's strings first, then switch, so the UI never shows raw keys
if (initial !== 'en') {
  void ensureLoaded(initial).then(() => i18n.changeLanguage(initial));
}

/** Switches the interface language, fetching its strings on first use. */
export async function changeLanguage(lng: string) {
  await ensureLoaded(lng);
  await i18n.changeLanguage(lng);
}

i18n.on('languageChanged', (lng) => {
  try {
    localStorage.setItem('dtc4eu.lang', lng);
  } catch {
    // storage unavailable: the choice lasts for this page only
  }
  document.documentElement.lang = lng;
});

export default i18n;
