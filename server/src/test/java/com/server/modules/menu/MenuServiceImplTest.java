package com.server.modules.menu;

import com.server.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock
    private MenuRepository repository;

    @InjectMocks
    private MenuServiceImpl service;

    @Test
    void adminGetsAdminAndUserMenusInSortOrder() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(sampleMenus());

        List<MenuNode> tree = service.getMenuTree(Role.ADMIN);

        assertThat(tree).extracting(MenuNode::titleKey)
                .containsExactly("menu_home", "menu_definitions", "menu_administration");
        assertThat(tree.get(1).children()).extracting(MenuNode::titleKey)
                .containsExactly("menu_categories", "menu_products");
        assertThat(tree.get(2).children()).extracting(MenuNode::titleKey)
                .containsExactly("menu_users", "menu_orphan_of_admin_group");
    }

    @Test
    void superadminSeesEverythingIncludingMenusWithoutRoles() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(sampleMenus());

        List<MenuNode> tree = service.getMenuTree(Role.SUPERADMIN);

        assertThat(tree).extracting(MenuNode::titleKey)
                .containsExactly("menu_home", "menu_definitions", "menu_administration", "menu_system");
    }

    @Test
    void ownerDoesNotInheritUserMenus() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(sampleMenus());

        List<MenuNode> tree = service.getMenuTree(Role.OWNER);

        assertThat(tree).extracting(MenuNode::titleKey).containsExactly("menu_home");
    }

    @Test
    void childOfInvisibleParentIsNotShown() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(sampleMenus());

        List<MenuNode> tree = service.getMenuTree(Role.USER);

        // menu_orphan_of_admin_group USER'a açık ama üst menüsü (Yönetim) görünmediği için o da görünmez.
        assertThat(tree).extracting(MenuNode::titleKey).containsExactly("menu_home", "menu_definitions");
        assertThat(tree.get(1).children()).extracting(MenuNode::titleKey)
                .containsExactly("menu_categories", "menu_products");
    }

    private static List<Menu> sampleMenus() {
        return List.of(
                menu(7L, null, "menu_home", 0, Role.OWNER, Role.USER),
                menu(1L, null, "menu_definitions", 1, Role.USER),
                menu(3L, 1L, "menu_categories", 1, Role.USER),
                menu(4L, 1L, "menu_products", 2, Role.USER),
                menu(2L, null, "menu_administration", 99, Role.ADMIN),
                menu(5L, 2L, "menu_users", 1, Role.ADMIN),
                menu(6L, 2L, "menu_orphan_of_admin_group", 2, Role.USER),
                menu(8L, null, "menu_system", 100));
    }

    private static Menu menu(Long id, Long parentId, String titleKey, int sortOrder, Role... roles) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setTitleKey(titleKey);
        menu.setSortOrder(sortOrder);
        menu.setRoles(roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(Arrays.asList(roles)));
        return menu;
    }
}
