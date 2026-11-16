-- Expert feedback, prototype annotations (tools/dtc4euexpertfeedbackontheplatform (1),
-- "a score for each item is also required, indicating whether the level of effectiveness
-- achieved is >= 3 or the level of criticality is <= 2"): each administration's C/NC mark
-- also carries a 1-4 score. The board reports the per-standard mean across the
-- administrations that scored it, classified per TAB.5 (the intermediate band 2<M<3 is
-- only reachable through that mean).
ALTER TABLE qcnc_standard_results
    ADD COLUMN score integer
        CONSTRAINT qcnc_standard_results_score_check CHECK (score BETWEEN 1 AND 4);
