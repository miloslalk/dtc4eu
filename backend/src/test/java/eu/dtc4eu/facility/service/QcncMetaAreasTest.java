package eu.dtc4eu.facility.service;

import eu.dtc4eu.facility.dto.QcncDtos.AdministrationDto;
import eu.dtc4eu.facility.dto.QcncDtos.MetaAreaDto;
import eu.dtc4eu.facility.dto.QcncDtos.MetaAreaStatus;
import eu.dtc4eu.facility.dto.QcncDtos.StandardDto;
import eu.dtc4eu.facility.entity.QcncAssessment.AdministrationType;
import eu.dtc4eu.facility.entity.QcncStandard.StandardType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §6.5 meta-areas over a catalogue shaped like the V11 seed: Q1 has 10
 * standards of which 7 are ★ mandatory (1A/1B/1C are recorded only), Q2–Q5
 * have 7 standards each and no flags.
 */
class QcncMetaAreasTest {

    private static final List<StandardDto> CATALOGUE = catalogue();

    private static List<StandardDto> catalogue() {
        List<StandardDto> standards = new ArrayList<>();
        long id = 1;
        for (String letter : List.of("A", "B", "C", "D", "E", "K", "O", "P", "Q", "R")) {
            boolean mandatory = !List.of("A", "B", "C").contains(letter);
            standards.add(new StandardDto(id++, "Q1", "1" + letter, StandardType.ORGANIZATIONAL_STRUCTURAL,
                    mandatory, Map.of(), "", (int) id));
        }
        for (int q = 2; q <= 5; q++) {
            for (int n = 0; n < 7; n++) {
                standards.add(new StandardDto(id++, "Q" + q, q + "" + (char) ('A' + n),
                        StandardType.COMMUNITY_FUNCTIONAL, false, Map.of(), "", (int) id));
            }
        }
        return standards;
    }

    private static List<Long> idsOf(String... clusters) {
        return CATALOGUE.stream().filter(s -> List.of(clusters).contains(s.clusterCode()))
                .map(StandardDto::id).toList();
    }

    /** One administration marking the given standards C and every other standard NC. */
    private static AdministrationDto administration(AdministrationType type, List<Long> conforming) {
        Map<Long, String> marks = new HashMap<>();
        CATALOGUE.forEach(s -> marks.put(s.id(), conforming.contains(s.id()) ? "C" : "NC"));
        return new AdministrationDto(type, null, null, null, marks);
    }

    private static Map<AdministrationType, AdministrationDto> allThree(List<Long> conforming) {
        Map<AdministrationType, AdministrationDto> administrations = new EnumMap<>(AdministrationType.class);
        for (AdministrationType type : AdministrationType.values()) {
            administrations.put(type, administration(type, conforming));
        }
        return administrations;
    }

    private static MetaAreaDto meta(List<MetaAreaDto> metaAreas, String code) {
        return metaAreas.stream().filter(m -> m.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void requiredStandardsExcludeTheRecordedOnlyQ1Standards() {
        List<MetaAreaDto> metaAreas = QcncStandardsService.metaAreas(CATALOGUE, allThree(List.of()));

        assertThat(meta(metaAreas, "RO.1").requiredStandards()).isEqualTo(14);   // 7 ★ in Q1 + 7 in Q2
        assertThat(meta(metaAreas, "RO.2").requiredStandards()).isEqualTo(7);
        assertThat(meta(metaAreas, "RO.3").requiredStandards()).isEqualTo(14);
        assertThat(meta(metaAreas, "RO.1").dtcroAreas()).containsExactly(1, 2, 3);
        assertThat(meta(metaAreas, "RO.3").dtcroAreas()).containsExactly(7, 8, 9);
    }

    /** V18 (Simone, 27 Nov 2026): 1A–1C are flagged too, so every Q1 standard counts. */
    @Test
    void withEveryQ1StandardFlaggedAllOfThemCount() {
        List<StandardDto> v18 = CATALOGUE.stream()
                .map(s -> s.clusterCode().equals("Q1") ? new StandardDto(s.id(), s.clusterCode(), s.letterCode(),
                        s.standardType(), true, s.settingParameters(), s.description(), s.sortOrder()) : s)
                .toList();
        List<Long> allButOneA = new ArrayList<>(idsOf("Q1", "Q2"));
        allButOneA.remove(Long.valueOf(1L));
        Map<AdministrationType, AdministrationDto> administrations = new EnumMap<>(AdministrationType.class);
        for (AdministrationType type : AdministrationType.values()) {
            Map<Long, String> marks = new HashMap<>();
            v18.forEach(s -> marks.put(s.id(), allButOneA.contains(s.id()) ? "C" : "NC"));
            administrations.put(type, new AdministrationDto(type, null, null, null, marks));
        }

        MetaAreaDto ro1 = meta(QcncStandardsService.metaAreas(v18, administrations), "RO.1");
        assertThat(ro1.requiredStandards()).isEqualTo(17);
        // 1A non-conforming now spoils the strength
        assertThat(ro1.status()).isEqualTo(MetaAreaStatus.NEITHER);
    }

    @Test
    void allRequiredConformingInAllThreeIsAStrength() {
        // 1A–1C non-conforming must not spoil RO.1: they are not required
        List<Long> conforming = new ArrayList<>(idsOf("Q1", "Q2", "Q3"));
        conforming.removeAll(List.of(1L, 2L, 3L));
        List<MetaAreaDto> metaAreas = QcncStandardsService.metaAreas(CATALOGUE, allThree(conforming));

        assertThat(meta(metaAreas, "RO.1").status()).isEqualTo(MetaAreaStatus.STRENGTH);
        assertThat(meta(metaAreas, "RO.2").status()).isEqualTo(MetaAreaStatus.STRENGTH);
        assertThat(meta(metaAreas, "RO.3").status()).isEqualTo(MetaAreaStatus.WEAKNESS);   // 0 of 14
        assertThat(meta(metaAreas, "RO.1").conformingByAdministration())
                .containsEntry(AdministrationType.INTERNAL_FACILITATOR, 14);
    }

    @Test
    void weaknessIsFewerThanHalfAndExactlyHalfIsNeither() {
        List<Long> q3 = idsOf("Q3");
        List<Long> q4q5 = idsOf("Q4", "Q5");
        // RO.2: 3 of 7 (< 3.5) → weak; RO.3: 7 of 14 (= half) → neither
        List<Long> conforming = new ArrayList<>(q3.subList(0, 3));
        conforming.addAll(q4q5.subList(0, 7));
        List<MetaAreaDto> metaAreas = QcncStandardsService.metaAreas(CATALOGUE, allThree(conforming));

        assertThat(meta(metaAreas, "RO.2").status()).isEqualTo(MetaAreaStatus.WEAKNESS);
        assertThat(meta(metaAreas, "RO.3").status()).isEqualTo(MetaAreaStatus.NEITHER);
    }

    @Test
    void verdictMustHoldInAllThreeAdministrations() {
        Map<AdministrationType, AdministrationDto> administrations = allThree(idsOf("Q3"));
        administrations.put(AdministrationType.TERNA_COORDINATOR_OWN,
                administration(AdministrationType.TERNA_COORDINATOR_OWN, idsOf("Q3").subList(0, 6)));

        assertThat(meta(QcncStandardsService.metaAreas(CATALOGUE, administrations), "RO.2").status())
                .isEqualTo(MetaAreaStatus.NEITHER);
    }

    @Test
    void aMissingAdministrationIsIncomplete() {
        Map<AdministrationType, AdministrationDto> administrations = allThree(idsOf("Q3"));
        administrations.remove(AdministrationType.TERNA_COORDINATOR_DELEGATION);

        MetaAreaDto ro2 = meta(QcncStandardsService.metaAreas(CATALOGUE, administrations), "RO.2");
        assertThat(ro2.status()).isEqualTo(MetaAreaStatus.INCOMPLETE);
        assertThat(ro2.conformingByAdministration()).hasSize(2);
    }
}
