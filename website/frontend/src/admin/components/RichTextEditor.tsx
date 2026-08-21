import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import { useEffect } from 'react';

/**
 * TipTap-based rich text control. Emits HTML; the CMS backend sanitizes it
 * again on write (the server policy is the source of truth).
 */
export function RichTextEditor({ value, onChange }: { value: string; onChange: (html: string) => void }) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3] } }),
      Link.configure({ openOnClick: false }),
    ],
    content: value,
    onUpdate: ({ editor: e }) => onChange(e.isEmpty ? '' : e.getHTML()),
  });

  // keep the editor in sync when the parent swaps content (e.g. switching blocks)
  useEffect(() => {
    if (editor && value !== editor.getHTML() && !(editor.isEmpty && value === '')) {
      editor.commands.setContent(value, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, editor]);

  if (!editor) return null;

  const toolButton = (label: string, active: boolean, run: () => void, title: string) => (
    <button
      type="button"
      title={title}
      onMouseDown={(e) => { e.preventDefault(); run(); }}
      className={`rounded px-2 py-1 text-xs font-bold ${
        active ? 'bg-blue-600 text-white' : 'text-neutral-600 hover:bg-neutral-100'
      }`}
    >
      {label}
    </button>
  );

  const setLink = () => {
    const previous = editor.getAttributes('link').href as string | undefined;
    const url = window.prompt('Link URL', previous ?? 'https://');
    if (url === null) return;
    if (url === '') {
      editor.chain().focus().unsetLink().run();
    } else {
      editor.chain().focus().setLink({ href: url }).run();
    }
  };

  return (
    <div className="rounded-lg border border-neutral-300 bg-white">
      <div className="flex flex-wrap gap-0.5 border-b border-neutral-200 px-1.5 py-1">
        {toolButton('B', editor.isActive('bold'), () => editor.chain().focus().toggleBold().run(), 'Bold')}
        {toolButton('I', editor.isActive('italic'), () => editor.chain().focus().toggleItalic().run(), 'Italic')}
        {toolButton('H2', editor.isActive('heading', { level: 2 }), () => editor.chain().focus().toggleHeading({ level: 2 }).run(), 'Heading 2')}
        {toolButton('H3', editor.isActive('heading', { level: 3 }), () => editor.chain().focus().toggleHeading({ level: 3 }).run(), 'Heading 3')}
        {toolButton('••', editor.isActive('bulletList'), () => editor.chain().focus().toggleBulletList().run(), 'Bullet list')}
        {toolButton('1.', editor.isActive('orderedList'), () => editor.chain().focus().toggleOrderedList().run(), 'Numbered list')}
        {toolButton('❝', editor.isActive('blockquote'), () => editor.chain().focus().toggleBlockquote().run(), 'Quote')}
        {toolButton('🔗', editor.isActive('link'), setLink, 'Link')}
      </div>
      <EditorContent editor={editor} className="cms-prose min-h-32 px-3 py-2 text-sm" />
    </div>
  );
}
