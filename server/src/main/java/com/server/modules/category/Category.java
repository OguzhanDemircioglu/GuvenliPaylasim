package com.server.modules.category;

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
@Table(name = "categories")
public class Category extends BaseEntity {

    // Benzersizlik @Column(unique) ile değil, silinmemiş kayıtlara özel index ile sağlanır (V004_CategoryModule).
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
