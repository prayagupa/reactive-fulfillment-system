package com.shipping.pick.application.query;

import com.shipping.cqrs.QueryHandler;
import com.shipping.pick.domain.model.PickTask;
import com.shipping.pick.infrastructure.persistence.PickTaskRepository;
import org.springframework.stereotype.Service;

/**
 * CQRS read side: handles {@link NextTaskQuery}.
 * <p>
 * Returns the next PENDING pick task for a warehouse associate,
 * or {@code null} if no work is currently available.
 */
@Service
public class NextTaskQueryHandler implements QueryHandler<NextTaskQuery, PickTask> {

    private final PickTaskRepository repository;

    public NextTaskQueryHandler(PickTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public PickTask handle(NextTaskQuery query) {
        return repository.findNextPending(query.associateId());
    }
}
