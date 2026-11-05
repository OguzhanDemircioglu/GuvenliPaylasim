package com.server.common.service;

import com.server.common.entity.BaseEntity;
import com.server.common.exception.ConflictException;
import com.server.common.exception.NotFoundException;
import com.server.common.mapper.BaseMapper;
import com.server.common.message.GlobalMessages;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * BaseService'in genel davranışı. Modül iş kurallarını aşağıdaki kancaları override ederek ekler;
 * kancalar varsayılan olarak hiçbir şey yapmaz.
 */
@Transactional(readOnly = true)
public abstract class BaseServiceImpl<E extends BaseEntity, CREATE, UPDATE extends VersionedRequest, RESPONSE>
        implements BaseService<CREATE, UPDATE, RESPONSE> {

    protected final JpaRepository<E, Long> repository;
    protected final BaseMapper<E, CREATE, UPDATE, RESPONSE> mapper;

    protected BaseServiceImpl(JpaRepository<E, Long> repository, BaseMapper<E, CREATE, UPDATE, RESPONSE> mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<RESPONSE> getAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    public RESPONSE getById(Long id) {
        return mapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public RESPONSE create(CREATE request) {
        validateCreate(request);
        E entity = mapper.toEntity(request);
        prepareCreate(entity, request);
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public RESPONSE update(Long id, UPDATE request) {
        E entity = findEntity(id);
        if (!Objects.equals(entity.getVersion(), request.version())) {
            throw new ConflictException(GlobalMessages.RECORD_MODIFIED);
        }
        validateUpdate(entity, request);
        mapper.update(request, entity);
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        E entity = findEntity(id);
        validateDelete(entity);
        repository.delete(entity);
    }

    protected E findEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(GlobalMessages.RECORD_NOT_FOUND));
    }

    /** Kayıt eklenmeden önceki iş kuralları (ör. aynı isimde kayıt var mı). */
    protected void validateCreate(CREATE request) {
    }

    /** Mapping'den sonra, kaydetmeden önce entity'yi tamamlamak için (ör. şifreyi hash'leyip atamak). */
    protected void prepareCreate(E entity, CREATE request) {
    }

    /** Güncellemeden önceki iş kuralları. {@code entity} henüz eski değerlerini taşır. */
    protected void validateUpdate(E entity, UPDATE request) {
    }

    /** Silmeden önceki iş kuralları (ör. kayıt başka kayıtlar tarafından kullanılıyor mu). */
    protected void validateDelete(E entity) {
    }
}
