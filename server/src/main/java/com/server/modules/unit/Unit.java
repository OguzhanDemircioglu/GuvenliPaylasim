package com.server.modules.unit;

import com.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

@Getter
@Setter
@Entity
@SoftDelete
@Table(name = "units")
public class Unit extends BaseEntity {

    // Benzersizlik @Column(unique) ile değil, silinmemiş kayıtlara özel index ile sağlanır (V004_UnitModule).
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
