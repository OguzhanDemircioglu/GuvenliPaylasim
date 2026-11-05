package com.server.modules.menu;

import com.server.security.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rol kümesi ↔ "USER,OWNER" gibi bir metin. Ayrı tablo ya da enum kolonu yerine düz metin seçildi: Hibernate enum
 * kolonlarına check constraint koyar ve yeni bir rol eklendiğinde o constraint'in migration'la yenilenmesi gerekir.
 */
@Converter
public class RoleSetConverter implements AttributeConverter<Set<Role>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Role> roles) {
        return roles == null || roles.isEmpty() ? null
                : roles.stream().map(Role::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<Role> convertToEntityAttribute(String value) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (value == null || value.isBlank()) {
            return roles;
        }
        // Bilinmeyen bir rol adı migration hatasıdır; sessizce yok sayılmaz.
        Arrays.stream(value.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .map(Role::valueOf).forEach(roles::add);
        return roles;
    }
}
