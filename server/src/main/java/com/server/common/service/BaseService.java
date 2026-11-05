package com.server.common.service;

import java.util.List;

/**
 * CRUD modüllerinin ortak servis sözleşmesi. Modüle özel metotlar modülün kendi servis interface'ine eklenir.
 */
public interface BaseService<CREATE, UPDATE extends VersionedRequest, RESPONSE> {

    List<RESPONSE> getAll();

    RESPONSE getById(Long id);

    RESPONSE create(CREATE request);

    RESPONSE update(Long id, UPDATE request);

    void delete(Long id);
}
