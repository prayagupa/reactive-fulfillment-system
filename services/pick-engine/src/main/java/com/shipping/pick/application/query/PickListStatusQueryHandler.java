package com.shipping.pick.application.query;

import com.shipping.cqrs.QueryHandler;
import com.shipping.pick.domain.model.PickTask;
import com.shipping.pick.infrastructure.persistence.PickTaskRepository;
import org.springframework.stereotype.Service;

/**
 * CQRS read side: handles {@link PickListStatusQuery}.
 * <p>
 * Returns {@code true} when every task in the pick list has reached
 * the {@code PICKED} status, {@code false} otherwise.
 */
@Service
public class PickListStatusQueryHandler implements QueryHandler<PickListStatusQuery, Boolean> {

    private final PickTaskRepository repository;

    public PickListStatusQueryHandler(PickTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public Boolean handle(PickListStatusQuery query) {
        return repository.findByPickList(query.pickListId())
            .stream()
            .allMatch(t -> t.status() == PickTask.Status.PICKED);
    }
}
