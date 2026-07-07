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

/** Data-scope assignment: which ternas a COORDINATOR may see (spec §6). */
@Entity
@Table(name = "user_terna_access")
@IdClass(UserTernaAccess.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserTernaAccess {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "terna_id")
    private Long ternaId;

    public record Key(Long userId, Long ternaId) {
    }
}
