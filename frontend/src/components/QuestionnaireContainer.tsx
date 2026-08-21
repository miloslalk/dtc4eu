import { useMemo } from 'react';
import { LikertScale } from './LikertScale';
import type { QuestionnaireDetail } from '../features/questionnaires/api';

export interface Answer {
  numericValue: number | null;
  textValue: string | null;
}

/**
 * The shared questionnaire container (spec T6: built first): renders a scale
 * definition grouped by area, with a Likert control per LIKERT item and a
 * textarea per FREE_TEXT item. Purely controlled — autosave lives in the page.
 *
 * v0.2: when `formId` is set, only that form's items render, area headers use
 * the form-specific phrasing (register §2.2 / open question 12.5), items show
 * their coordinate ("1.a"), and the Likert buttons carry the anchor labels.
 */
export function QuestionnaireContainer({ questionnaire, formId, answers, onAnswer, readOnly }: {
  questionnaire: QuestionnaireDetail;
  formId?: number | null;
  answers: Map<number, Answer>;
  onAnswer: (itemId: number, answer: Answer) => void;
  readOnly: boolean;
}) {
  const visibleItems = useMemo(
    () => (formId == null ? questionnaire.items : questionnaire.items.filter((i) => i.formId === formId)),
    [questionnaire.items, formId],
  );

  const itemsByArea = useMemo(() => {
    const groups = new Map<number | null, typeof questionnaire.items>();
    for (const item of visibleItems) {
      const key = item.areaId;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(item);
    }
    return groups;
  }, [visibleItems]);

  const sortedAreas = useMemo(
    () => [...questionnaire.areas].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
    [questionnaire.areas],
  );
  const orphanItems = itemsByArea.get(null) ?? [];

  const areaLabelOverrides = useMemo(() => {
    const map = new Map<number, string>();
    if (formId != null) {
      for (const label of questionnaire.formAreaLabels ?? []) {
        if (label.formId === formId) map.set(label.areaId, label.displayName);
      }
    }
    return map;
  }, [questionnaire.formAreaLabels, formId]);

  const anchors = useMemo(() => {
    const record: Record<number, string> = {};
    for (const anchor of questionnaire.anchors ?? []) {
      record[anchor.value] = anchor.label;
    }
    return record;
  }, [questionnaire.anchors]);

  const itemLabel = (item: (typeof questionnaire.items)[number], areaNo: number | null) =>
    item.itemLetter && areaNo != null ? `${areaNo}.${item.itemLetter}` : item.itemCode;

  return (
    <div className="space-y-6">
      {sortedAreas.map((area) => {
        const items = itemsByArea.get(area.id) ?? [];
        if (items.length === 0) return null;
        return (
          <section key={area.id} className="overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm">
            <header className="border-b border-neutral-200 bg-blue-50 px-5 py-3">
              <h2 className="text-sm font-bold text-blue-700">
                <span className="mr-2 font-mono text-xs">{area.areaCode}</span>
                {areaLabelOverrides.get(area.id) ?? area.name}
              </h2>
            </header>
            <ul className="divide-y divide-neutral-100">
              {items.map((item) => {
                const answer = answers.get(item.id) ?? { numericValue: null, textValue: null };
                return (
                  <li key={item.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                    <span className="w-10 shrink-0 font-mono text-xs font-bold text-neutral-400">
                      {itemLabel(item, area.sortOrder)}
                    </span>
                    <p className="min-w-[220px] flex-1 text-sm text-neutral-800">{item.itemText}</p>
                    {item.responseType === 'LIKERT' ? (
                      <LikertScale
                        min={questionnaire.scaleMin}
                        max={questionnaire.scaleMax}
                        value={answer.numericValue}
                        disabled={readOnly}
                        anchors={anchors}
                        onChange={(value) => onAnswer(item.id, { numericValue: value, textValue: null })}
                      />
                    ) : (
                      <textarea
                        rows={2}
                        disabled={readOnly}
                        value={answer.textValue ?? ''}
                        onChange={(e) => onAnswer(item.id, { numericValue: null, textValue: e.target.value })}
                        className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm
                          focus:outline-2 focus:outline-offset-1 focus:outline-gold-500 disabled:bg-neutral-100"
                      />
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}

      {orphanItems.length > 0 && (
        <section className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
          {orphanItems.map((item) => {
            const answer = answers.get(item.id) ?? { numericValue: null, textValue: null };
            return (
              <div key={item.id} className="flex items-center gap-3 py-2">
                <span className="w-10 font-mono text-xs font-bold text-neutral-400">{item.itemCode}</span>
                <p className="flex-1 text-sm">{item.itemText}</p>
                {item.responseType === 'LIKERT' ? (
                  <LikertScale min={questionnaire.scaleMin} max={questionnaire.scaleMax}
                    value={answer.numericValue} disabled={readOnly} anchors={anchors}
                    onChange={(value) => onAnswer(item.id, { numericValue: value, textValue: null })} />
                ) : (
                  <textarea rows={2} disabled={readOnly} value={answer.textValue ?? ''}
                    onChange={(e) => onAnswer(item.id, { numericValue: null, textValue: e.target.value })}
                    className="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm" />
                )}
              </div>
            );
          })}
        </section>
      )}
    </div>
  );
}
