package com.server.modules.menu;

import com.server.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    private final MenuRepository repository;

    @Override
    public List<MenuNode> getMenuTree(Role role) {
        Set<Role> reachable = role.reachable();
        // Menü sayısı az olduğu için süzme Java'da yapılır. SUPERADMIN rolü boş menüleri de görür.
        List<Menu> menus = repository.findAllByOrderBySortOrderAsc().stream()
                .filter(menu -> role == Role.SUPERADMIN || !Collections.disjoint(menu.getRoles(), reachable))
                .toList();

        Map<Long, List<Menu>> childrenByParent = new HashMap<>();
        for (Menu menu : menus) {
            if (menu.getParentId() != null) {
                childrenByParent.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
            }
        }
        // Ağaç görünür kök menülerden kurulur; görünmeyen bir menünün alt menüleri de görünmez.
        return menus.stream()
                .filter(menu -> menu.getParentId() == null)
                .map(menu -> toNode(menu, childrenByParent))
                .toList();
    }

    private static MenuNode toNode(Menu menu, Map<Long, List<Menu>> childrenByParent) {
        List<MenuNode> children = childrenByParent.getOrDefault(menu.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenByParent))
                .toList();
        return new MenuNode(menu.getId(), menu.getTitleKey(), menu.getRoute(), menu.getIcon(), children);
    }
}
