-- Client request: drop the "Research & Policy" page from the site.
-- page_blocks cascade with the page.
DELETE FROM pages WHERE slug = 'research-policy' AND locale = 'en';
