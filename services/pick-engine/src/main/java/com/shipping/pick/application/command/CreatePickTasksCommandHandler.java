package com.shipping.pick.application.command;

import com.shipping.events.PickList;
import com.shipping.cqrs.CommandHandler;
import com.shipping.pick.domain.model.PickTask;
import com.shipping.pick.infrastructure.persistence.PickTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CQRS write side: handles {@link CreatePickTasksCommand}.
 * <p>
 * Materialises one {@link PickTask} row in ScyllaDB for each line item
 * in the PickList event received from the wave planner.
 */
@Service
public class CreatePickTasksCommandHandler
        implements CommandHandler<CreatePickTasksCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(CreatePickTasksCommandHandler.class);

    private final PickTaskRepository repository;
    private final MeterRegistry meterRegistry;

    public CreatePickTasksCommandHandler(PickTaskRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Void handle(CreatePickTasksCommand cmd) {
        PickList pickList = cmd.pickList();
        for (var item : pickList.getItems()) {
            PickTask task = new PickTask(
                pickList.getPickListId().toString(),
                pickList.getOrderId().toString(),
                item.getItemSeq(),
                item.getSku().toString(),
                0,   // quantityRequired resolved from wave data in full implementation
                item.getBinLocation().toString(),
                null,
                PickTask.Status.PENDING);
            repository.save(task);
        }
        log.info("Created {} pick tasks for pickListId={}", pickList.getItems().size(), pickList.getPickListId());
        meterRegistry.counter("pick.tasks.created").increment(pickList.getItems().size());
        return null;
    }
}
