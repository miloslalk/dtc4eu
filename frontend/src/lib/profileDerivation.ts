/**
 * A field filled from another field of the same row (GAS-Set L/M: disciplinary
 * area from role). Source values listed in `none` have no value at all (M:
 * Volunteers have no disciplinary area).
 */
export interface DerivedFrom {
  field: string;
  map: Record<string, string>;
  none?: string[];
}

interface DerivableField {
  key: string;
  derivedFrom?: DerivedFrom;
}

/** True when the row's source value says this derived field does not apply. */
export function isNotApplicable(field: DerivableField, row: Record<string, unknown>): boolean {
  const source = field.derivedFrom ? row[field.derivedFrom.field] : undefined;
  return typeof source === 'string' && (field.derivedFrom?.none ?? []).includes(source);
}

/**
 * Sets one field of a row and re-derives the fields that follow from it:
 * choosing a role assigns its disciplinary area (Tables 1-2); a role without a
 * single area clears it so it is chosen by hand. A derived field can still be
 * edited afterwards, unless it does not apply to the row.
 */
export function setRowField(fields: DerivableField[], row: Record<string, unknown>, key: string,
                            value: unknown): Record<string, unknown> {
  const next = { ...row };
  if (value === undefined) delete next[key];
  else next[key] = value;
  for (const field of fields) {
    if (field.derivedFrom?.field !== key) continue;
    const derived = typeof value === 'string' ? field.derivedFrom.map[value] : undefined;
    if (derived !== undefined) next[field.key] = derived;
    else delete next[field.key];
  }
  return next;
}
