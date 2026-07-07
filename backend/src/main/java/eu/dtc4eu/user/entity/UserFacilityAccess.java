package eu.dtc4eu.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Data-scope assignment: which facilities an INTERNAL_FACILITATOR may see (spec §6). */
@Entity
@Table(name = "user_facility_access")
@IdClass(UserFacilityAccess.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserFacilityAccess {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "facility_id")
    private Long facilityId;

    public record Key(Long userId, Long facilityId) {
    }
}
