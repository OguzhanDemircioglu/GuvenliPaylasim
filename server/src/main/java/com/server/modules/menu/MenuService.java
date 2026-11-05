package com.server.modules.menu;

import com.server.security.Role;

import java.util.List;

public interface MenuService {

    /** Rolün (hiyerarşiyle kapsadığı rollerle birlikte) görebildiği menüler; SUPERADMIN hepsini görür. */
    List<MenuNode> getMenuTree(Role role);
}
