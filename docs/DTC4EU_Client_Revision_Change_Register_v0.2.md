DTC4EU DIGITAL PLATFORM - CLIENT REVISION CHANGE REGISTER (for Spec v0.2)

Document version: 0.2-CR
Date: 24.07.2026.
Project: DTC4EU - Democratic Therapeutic Communities for Social Inclusion (Erasmus+ KA220-ADU)
Purpose: Authoritative register of every change introduced by the client's corrected document set (July 2026), mapped to the sections of the Technical Specification, the UI/Screen Specification, the V1 baseline schema and the Build Spec. This is the changelog for spec v0.2.

Source documents analysed:
- DTCRO-U / -F / -O / -M / -Del / -Com-LF - 2nd Ed. EN "Correct" (six questionnaire forms, Excel)
- Terna_Example1 - Quantitative Analysis of DTCRO Questionnaire (Excel, 3 facility sheets with live formulas)
- BRUSCHETTA_GAS-SET-DTC-2020-DEF_EN_Correct (Word, full GAS-Set form, Sections A-N)
- BRUSCHETTA_C_N-C_Quest_En_Correct (Word, QCNC standards catalogue)
- Bruschetta_Data_Analysis_2026 (PDF, methodology deck)
- DTC4EU_Platform_Phase_1_Prototype_correct (Word, 18 pages of prototype screenshots with the client's screen-by-screen annotations)

---

1. HEADLINE DECISION REVERSAL - QUALITY DOCUMENT NAMING

The corrected client documents standardise the quality document types as:

| Code | Meaning |
|------|---------|
| GPP | Good Practice Procedure |
| CGS | Community Group Setting |
| EEN | Enabling Environment Setting |

This REVERSES the previously confirmed convention (PGP/DGC/NEE per the earlier Excel data dictionary). The annotated prototype uses GPP/CGS/EEN consistently, including real document codes (GPP CT4-LC, GPP CT7-S, CGS CT2-T, EEN CT3-LC). Spec v0.2, the schema enum, and all seed data must adopt GPP/CGS/EEN. Observed code pattern for individual documents: {TYPE} CT{n}-{suffix} with suffixes LC, S, T seen in the example data - the suffix taxonomy should be confirmed with Simone (open question 12.1).

Spec impact: Tech Spec section 6 (quality document tables and enums), Module 6-8 descriptions; Build Spec naming conventions section; V1 schema CHECK/enum values.

---

2. DTCRO/MHSRO QUESTIONNAIRE CONTENT - CRITICAL PATH NOW UNBLOCKED

The DTCRO item content, previously the open blocker for screen S-32, is now final. The structure differs from the earlier working assumption of a single 45-item list administered to everyone.

2.1 Areas (unchanged in count, canonical English names fixed)

R1 Renewing hope and commitment; R2 Being supported by others; R3 Finding one's place in the local community; R4 Redefining oneself; R5 Assimilating the illness; R6 Managing symptoms; R7 Taking control; R8 Fighting stigma; R9 Developing empowerment.

Note: form headers occasionally vary the phrasing of R3/R4/R5 ("Finding your place...", "Coming to terms with the illness"). The R1-R9 list above is the canonical set per the client's annotation on the prototype and should be the stored area names; per-form phrasing lives with the form rendering, not the area entity.

2.2 Six forms, per-category item subsets, per-category wording

The master item grid is 9 areas x letters a-e (45 coordinates). Each form uses a subset of coordinates, and the item TEXT for the same coordinate differs per form (perspective-adapted wording). Extracted counts, verified programmatically from the corrected Excels (full text in the companion file dtcro_item_bank.csv, 216 rows):

| Form code | Client name | Respondent / administration | Items | Coordinates used |
|-----------|-------------|------------------------------|-------|------------------|
| USER (U) | User Form | Users of the DTC, individual | 36 | 4 per area, letters vary per area (e.g. A1: a,b,d,e; A2: a,b,c,d) |
| FAMILY (F) | Family Form | Family members of users, individual | 36 | 4 per area, letters vary per area |
| STAFF (O) | Staff Form | Practitioners of the DTC, individual | 45 | a-e in all areas |
| MANAGER (M) | Manager Form | Managers of the DTC, individual | 27 | a-c in all areas |
| LONG_FORM (Com-LF) | Long Form | Community self-assessment, collective, administered by the Internal Facilitator | 45 | a-e in all areas |
| DELEGATION (Del) | Delegation Form | Administered by the Terna Coordinator or Audit Coordinator (visits/audits) | 27 | a-c in all areas |

The Delegation Form wording equals the Long Form wording for coordinates a-c (verified by text comparison), i.e. Del is the a-c projection of Com-LF.

Schema impact: questionnaire items must be modelled per form with a shared coordinate key (area_no, item_letter) used for cross-form aggregation. The earlier single-item-list model with six respondent categories is replaced by six form definitions; the enum SELFEVAL_COLLECTIVE maps to LONG_FORM and HETEROEVAL_DELEGATION maps to DELEGATION.

2.3 Likert anchors

1 = Not at all; 2 = A little; 3 = A lot; 4 = Very much. (The methodology deck's older "recovery-oriented statement" phrasing is superseded by the anchors printed on the corrected forms.)

2.4 Per-form anagraphic/profile header - NEW requirement

Every form opens with a structured profile section that must be captured with the response (pseudonymised: SUBJECT ID / working-group ID, never names). Field sets differ per form:

- USER: age; sex (NB/M/F); education level; sheltered / competitive work experience; profession; years in community; hours per week in community; single room Y/N and sharing count; monthly therapy sessions by type (individual, small group, community group, family, multi-family, personalised therapeutic planning activity group, other); self-help groups (existence, participants, leader, frequency); key worker (existence, freely chosen Y/N, reference operator ID, meeting frequency); residence proximity (municipality/province/region); involvement in community planning/development/evaluation work; sheltered and unsheltered work activity.
- FAMILY: age; sex; relationship type and ID of related subject; years of relationship; weekly hours in community activities and in the relative's therapeutic project; other family participants; participation in five listed activity types (Y/N + n); multi-family community meetings (Y/N, participants, frequency); knowledge of the relative's key worker and meeting frequency; residence proximity; cohabitation with the relative.
- STAFF (O): age; sex; professional role (7 listed roles + other); years and weekly hours worked; staff composition by role with per-role counts and M/F split; directorate team size and M/F; full staff meetings (Y/N, participants, frequency); administrative-staff delegate in reflective coordination; administrative meetings with representatives of operators/users/family (Y/N each); clinical supervision (Y/N, five target groups Y/N each, frequency); participation in peer accreditation programmes; membership of scientific-professional associations; funding type (public/private); user counts (total, forensic, non-EU) with M/F splits.
- MANAGER (M): age; gender; managerial role (6 listed roles + other); years and weekly hours; administrative staff composition by role with counts; delegate participation; administrative meetings with representation; supervision provision and targets; inter-agency planning meetings; accreditation programmes; association membership; funding type; totals for clinical-social staff, directorate, users, forensic users, non-EU users with M/F splits; clinical-social staff composition by role with M/F splits.
- LONG_FORM and DELEGATION (group forms): working-group ID; community name; provider/managing body; evaluator group size and composition by gender (M/F/NB) and by role (coordinators, staff, users, family members, managers, other stakeholders) each with gender split; duration of the visit/audit or preliminary evaluation process (hours); members of the group belonging (Del) / not belonging (Com-LF) to the community, by role and gender; preliminary activities carried out (six Y/N: meetings with administrative staff, with clinical-social staff, community meeting, multi-family community meeting, documentation review, separate category interviews with staff/users/family/managers); rating procedure used (six listed modes); community statistics (staff, coordination team, users total/forensic/non-EU, each with M/F/NB split).

Spec impact: Tech Spec section 6 (new per-form profile tables or a structured profile JSON with per-form validation), UI spec S-32 (profile step precedes the Likert areas), Build Spec seed data.

---

3. SCORING ENGINE - CALCULATION MODEL NOW FULLY SPECIFIED (Terna_Example1)

The example workbook is the executable reference for Module 4 aggregation, per facility per annual cycle. The client's annotation on the scores screens: "This type of automated calculation is also necessary. See Example Excel Document."

3.1 Item-level pipeline (Quant-SPWP)

For each coordinate (area.letter):
1. Category means over individual administrations: Quant-SPWP-U-I, -F-I, -S-I, -M-I (simple unweighted means; a category is absent if the coordinate is not on that form or no responses exist).
2. Individual-administration mean: Quant-SPWP-Aver-I = AVERAGE of the available category means (missing categories are skipped, not zero-filled - the workbook uses Excel AVERAGE semantics).
3. Community score: the single LONG_FORM value for the coordinate; Delegation score: the single DELEGATION value (absent for coordinates d, e).
4. Final item score: Quant-SPWP = AVERAGE(Aver-I, Com, Del), again skipping absent components.

3.2 Area-level pipeline (Quant-SAWA)

Identical structure at area level (means taken over the items of the area within each source), producing Quant-SAWA-U/F/S/M-I, Quant-SAWA-Aver-I, -Aver-Com, -Aver-Del and the final Quant-SAWA per area. Facility overall score = AVERAGE of the nine area finals.

3.3 Effectiveness classification (TAB.5) - required per item AND per area

| Score M | Operational assessment | Level |
|---------|------------------------|-------|
| 1 <= M <= 2 | Unacceptable | CRITICAL LEVEL |
| 2 < M < 3 | Partially Acceptable | INTERMEDIATE LEVEL |
| 3 <= M < 4 | More than Acceptable | EFFICACY LEVEL |

Boundary semantics matter: exactly 2.0 is CRITICAL; exactly 3.0 is EFFICACY. The client requires the classification badge displayed next to every item score and every area score on the scores screens.

3.4 Strengths, weaknesses, benchmarks, excellence points

- Quantitative Strength Points (Quant-SP) = items whose final score is at EFFICACY level (>= 3); Quantitative Weakness Points (Quant-WP) = items at CRITICAL level (<= 2). Same definitions at area level (Quant-SA / Quant-WA).
- Benchmark Strength Areas of the Terna: for each of the nine areas, compare the final area scores of the three facilities of the terna and record the best facility (score + facility code), as in the workbook's right-hand table.
- Excellence Points (EP): per questionnaire (VIVACOM and DTCRO/MHSRO), the subset of strength points that qualifies for the accreditation record; EP-Bench = EP falling within the terna's Benchmark Areas. Client note: "Calculation of DTCRO/MHSRO Excellence Points can also be carried out without presence of the Qualitative Strengths and Weaknesses Points" - the EP computation must not depend on QCNC qualitative data being present.
- QCNC meta-areas: the platform must compute the "meta-area of strengths" and "meta-area of weaknesses" - areas that are strengths (or weaknesses) across all three QCNC administrations of a facility.

3.5 Summary Table - NEW required output (per facility per cycle)

"Summary Table of Qualitative and Quantitative Community Data for Annual Cycle of a DTC of a Terna", columns VIVACOM and DTCRO/MHSRO, rows: Qualitative Weakness Areas (C-NC); Qualitative Weakness Points; Quantitative Weakness Areas; Quantitative Weakness Points; Qualitative Strength Areas (C-NC); Qualitative Strength Points; Quantitative Strength Areas; Quantitative Strength Points; Benchmark Areas; EP; EP-Bench. Values are lists of area numbers / item coordinates (e.g. "8.c; 9.b; 9.c"). Automated generation is explicitly required.

3.6 Terna population table (TAB.2, Terna-Com-Pop-Tab)

Per terna: collected questionnaires vs participating subjects, per community and per category (Users, Familiars, Staff, Managers) with totals - a coverage/response-rate table the platform must produce.

Spec impact: Tech Spec sections 6 and 7 (scoring tables, computed-score endpoints), UI spec S-33 and the facility analytics screens, Build Spec T-plan (scoring service acceptance criteria should replicate the example workbook's numbers as a regression fixture).

---

4. QCNC - CONTENT AND ADMINISTRATION MODEL DELIVERED

- Five Quality Standards (clusters, Haigh 1999): Q1 Attachment-Belonging; Q2 Containment-Permissiveness; Q3 Communication-Communalism; Q4 Involvement-Reality Confrontation; Q5 Agency-Democratization.
- Standards are letter-coded within each cluster (1A...1R, 2B...2R, 3A...3L, 4A...4J, 5A...5N) and typed as Organizational-Structural vs Community-Functional basic quality standards.
- Some structural standards are parameterised by setting type (e.g. 1A open days/hours and 1B user counts differ for RTC / STC / Day TC).
- Administration: three administrations per facility per cycle - (1) Internal Facilitator self-assessment, (2) Terna Coordinator for their own facility, (3) Terna Coordinator for the visiting delegations. Each records Conformity / Non-Conformity per standard.
- Mandatory standards: within Q1, standards 1D, 1E, 1K, 1O, 1P, 1Q, 1R are required ("requested") - non-conformity there is blocking for accreditation logic and must be flagged.
- Automated C/NC aggregation (Com-Grup-Qual-C/NC, TAB.14) and the qualitative strength/weakness areas feeding the Summary Table (3.5) are required.

Spec impact: QCNC was Phase 2 in the v0.1 phasing; the client's annotations treat the QCNC screens as part of the reviewed prototype. Whether QCNC data entry moves into Phase 1 scope must be decided (open question 12.2) - the schema should carry the standards catalogue either way.

---

5. GAS-SET (SETTING ANALYSIS GRID) - FULL FORM AVAILABLE

Administered collectively by the Internal Facilitator (client annotation on S-15: "Setting Analysis Greed - Internal Facilitator"). Sections:

A structural data (managing body, location, architecture, rooms, transport, devices, staff count, user counts by age band, discharges, average length of stay); B collaborations with local social agencies (16 checkboxes + specify); C care data (C.1-C.8 Y/N: PTP-R, CNT-R, GOPTP, community consent, community regulation); D areas of intervention (D1-D6); E psychopharmacotherapy management (E.1-E.4); F community regulations (F.1-F.9, Y/N + description); G activities carried out (Individual AI.1-AI.5, Group AG.1+, Community AC - each Y/N + daily hours + weekday pattern + frequency); H other information; I quality documents; L per-user services-delivered table; M staff data table; N per-user data table (user code, sex, age, marital status 1-5, education 1-7, occupation 1-6, months/years current community, total residential months/years, ICD-10 diagnosis group F10-F90) plus appendix rows for clinical history and family history free text.

Spec impact: GAS-Set becomes a concrete structured module (repeating child tables for G activities, L services, M staff rows, N user rows). Pseudonymisation note: Section N carries clinical data at per-user granularity - keep user codes pseudonymous, treat clinical/family history as sensitive free text with restricted role access.

---

6. QUALITY DOCUMENT APPLICATIONS AND ACCREDITATION HISTORY - NEW SCREEN-LEVEL REQUIREMENTS

Per the annotated prototype (facility participation screens: "These additional information and 2 tables with measured data resulting from coordinated evaluations are necessary"):

- Every quality document applied by a facility is assessed EVERY annual cycle with an application level from 0% to 100%.
- The Terna Coordinator administers an Assessment Form for every document applied by a facility of the terna; the Audit Coordinator administers one for every document applied by a facility in audit.
- Facility accreditation history view must show: number of annual cycles in the accreditation programme; per cycle, the Excellence Points of the evaluating questionnaires (VIVACOM EP and DTCRO/MHSRO EP, as lists of item coordinates); per cycle, the applied quality documents with their measured application level (e.g. 1st 2023-24: GPP CT4-LC = 40%; 4th 2026-27: EEN CT3-LC = 23%).
- Cycle labels follow the pattern "1st 2023-24", "2nd 2024-25", etc.

Spec impact: new tables (or completion of Module 6-8 tables) for quality_document, facility_document_application (per cycle, with application_level_pct and assessor role), and the two history tables on the facility participation screen; API endpoints for the history view.

---

7. FACILITY TAXONOMY - REVISED (3 SETTINGS, 8 TYPES)

| Setting | Types |
|---------|-------|
| 1. DTC-TC | 1.a Residential TC (RTC); 1.b Spread TC (STC); 1.c Day TC |
| 2. DTC-GA | 2.a Double GA; 2.b Simple GA; 2.c Couple GA |
| 3. SH | 3.a Supported Apartments SH; 3.b Home Care SH |

This replaces the earlier flat facility-type list (TC/GA/SH without subtypes). QCNC structural standards reference the subtype (see 4). Facility records need setting + type; filters and the facility detail screen show both.

---

8. ANNUAL CYCLE - DEFINITION FIXED, CYCLES OVERLAP

"The Annual Cycle runs from September 1st of the first year to December 31st of the second year. Each Annual Cycle includes months of activity during which it overlaps with the preceding and succeeding annual cycles."

A cycle therefore spans 16 months and ADJACENT CYCLES OVERLAP (Sep-Dec of year 2 belongs to both cycle n and cycle n+1's opening months). Any v0.1 assumption of disjoint 12-month cycles must be removed: no uniqueness constraint on "the active cycle", cycle pickers must allow two concurrent open cycles, and all data remains keyed to an explicitly chosen cycle rather than resolved from the current date.

---

9. ROLES AND SCOPES - CORRECTIONS FROM THE USERS/ROLES SCREEN

- Role set referenced by the client: Upper Coordinator, Terna Coordinator, Audit Coordinator, Internal Facilitator, Research Team.
- Scope note: Upper Coordinator, Internal Facilitator (in their remit) and Research Team see "All Cycles, all Countries, all Ternas" - the Research Team's read scope is global.
- "Should be me!" / "Should be the name!" annotations against Terna Coordinator and Audit Coordinator: role assignments must display the actual named user account holding the role (per terna / per audit), not a generic role placeholder.

---

10. SCREEN-BY-SCREEN VERDICTS FROM THE ANNOTATED PROTOTYPE

Approved as-is (marked OK): Login (S-00); Dashboard (S-01); Facilities list (S-10); Facility members and codes (S-13); Terna detail (S-21); My questionnaires (S-30); Fill questionnaire layout (S-32, content now supplied); Annual cycles admin (S-40).

Approved with content corrections: Facility detail (S-11) - adopt the 3-settings/8-types taxonomy; Fill questionnaire (S-32) - six forms, R1-R9 areas, profile header step; Computed scores (S-33) - add per-item and per-area effectiveness badges and the full aggregation columns of the example workbook.

Requires additions ("These additional information is necessary" / "This type of automated calculation is also necessary"): Facility participation / accreditation history (cycle count, EP tables, quality-document application tables - section 6); scores/analytics (Summary Table, benchmark areas, EP, EP-Bench, QCNC meta-areas - section 3); QCNC screens (three administrations per facility, mandatory Q1 standards flagging - section 4); terna schedule (Foundation Meeting and Final Forum event types alongside visit dates; several schedule elements are marked OK/NO on pages 16-17 of the annotated document - the exact mapping of each OK/NO mark should be confirmed with the client, open question 12.3).

---

11. SCHEMA AND BUILD IMPACT CHECKLIST (V1 baseline + Build Spec)

1. Rename quality-document enums to GPP / CGS / EEN; add document code field ({TYPE} CT{n}-{suffix}).
2. Replace single DTCRO item list with per-form item definitions keyed by (form_code, area_no, item_letter); seed from dtcro_item_bank.csv (216 rows).
3. Rename respondent-category enums: SELFEVAL_COLLECTIVE -> LONG_FORM, HETEROEVAL_DELEGATION -> DELEGATION; individual categories USER / FAMILY / STAFF / MANAGER unchanged.
4. Add per-form profile structures (2.4) attached to questionnaire administrations.
5. Scoring tables/views implementing 3.1-3.6, including classification thresholds with exact boundary semantics, benchmark areas, EP, EP-Bench, summary table, terna population table; example workbook becomes the regression fixture.
6. QCNC standards catalogue (cluster, letter code, structural/functional type, per-setting parameters, mandatory flag) and the three-administration C/NC response model.
7. GAS-Set structured tables including repeating child rows for activities, services, staff and users; restricted access class for Section N clinical fields.
8. facility: setting + subtype columns per section 7 taxonomy.
9. annual_cycle: allow overlapping cycles; remove any single-active-cycle assumption; label format "1st 2023-24".
10. Role scope adjustments per section 9; named assignment display.

---

12. OPEN QUESTIONS FOR THE CLIENT

12.1 Quality-document code suffixes (LC, S, T) - confirm the full suffix list and meaning.
12.2 Does QCNC data entry (three administrations) move into Phase 1 (September) scope, or does Phase 1 carry only the catalogue and schema? Same question for GAS-Set data entry and the quality-document application tracking (sections 4, 5, 6) - the annotations request them but the September scope was Modules 1, 2, 4.
12.3 Terna schedule: confirm which schedule elements the OK/NO marks on the two schedule pages refer to, and the definitive event-type list (Foundation Meeting, visit dates, Final Forum, others?).
12.4 VIVACOM EP appears in the accreditation history for past cycles - confirm VIVACOM remains Phase 2 and historical EP values will be entered manually for pre-platform cycles.
12.5 Area 5 naming: "Assimilating the illness" (canonical R-list) vs "Coming to terms with the illness" (U/O form headers) - confirm the display name policy per form.
