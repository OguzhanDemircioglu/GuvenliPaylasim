package com.server.modules.menu;

import java.util.List;

/**
 * Login/refresh cevabındaki menü ağacının bir düğümü. titleKey'i frontend çevirir.
 */
public record MenuNode(Long id, String titleKey, String route, String icon, List<MenuNode> children) {
}
