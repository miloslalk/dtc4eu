/** A field filled from another field of the same row (GAS-Set L/M: disciplinary area from role). */
export interface DerivedFrom {
  field: string;
  map: Record<string, string>;
}

interface DerivableField {
  key: string;
  derivedFrom?: DerivedFrom;
}

/**
 * Sets one field of a row and re-derives the fields that follow from it:
 * choosing a role assigns its disciplinary area (Tables 1-2); a role without a
 * single area clears it so it is chosen by hand. A derived field can still be
 * edited afterwards.
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
