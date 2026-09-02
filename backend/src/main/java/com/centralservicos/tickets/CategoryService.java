package com.centralservicos.tickets;

import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final AuditService audit;

    CategoryService(CategoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> active() {
        return repository.findAllByActiveTrueOrderByNameAsc().stream().map(Category::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> all() {
        return repository.findAllByOrderByNameAsc().stream().map(Category::toView).toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> names(Iterable<UUID> ids) {
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::id, Category::name));
    }

    @Transactional
    public CategoryView create(String name, UUID actorId) {
        validateName(name);
        if (repository.existsByNameIgnoreCase(name.trim())) {
            throw DomainException.conflict("Já existe uma categoria com este nome.");
        }
        var category = repository.save(new Category(name));
        audit.record(actorId, "CATEGORY_CREATED", "Category", category.id(), null);
        return category.toView();
    }

    @Transactional
    public CategoryView update(UUID id, String name, boolean active, UUID actorId) {
        validateName(name);
        var category = required(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(name.trim(), id)) {
            throw DomainException.conflict("Já existe uma categoria com este nome.");
        }
        category.update(name, active);
        audit.record(actorId, "CATEGORY_UPDATED", "Category", id, "{\"active\":" + active + "}");
        return category.toView();
    }

    @Transactional(readOnly = true)
    public CategoryView requiredActive(UUID id) {
        return repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> DomainException.unprocessable("Categoria inválida ou inativa."))
                .toView();
    }

    private Category required(UUID id) {
        return repository.findById(id).orElseThrow(() -> DomainException.notFound("Categoria não encontrada."));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw DomainException.unprocessable("Informe uma categoria com até 100 caracteres.");
        }
    }
}
