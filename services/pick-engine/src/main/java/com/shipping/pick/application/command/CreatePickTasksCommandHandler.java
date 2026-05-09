package com.shipping.pick.application.command;

import com.shipping.events.PickList;
import com.shipping.cqrs.CommandHandler;
import com.shipping.pick.infrastructure.persistence.PickListRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CQRS write side: handles {@link CreatePickTasksCommand}.
 * <p>
 * Thin coordinator:
 * <ol>
 *   <li>Delegates aggregate construction to
 *       {@link com.shipping.pick.domain.model.PickList#from(PickList)}.</li>
 *   <li>Persists all tasks via {@link PickListRepository#saveAll}.</li>
 * </ol>
 * No business logic here — {@link com.shipping.pick.domain.model.PickList}
 * owns the construction invariants (e.g. valid item sequences, non-null SKUs).
 */
@Service
public class CreatePickTasksCommandHandler
        implements CommandHandler<CreatePickTasksCommand, Void> {

    private static final Logger log = LoggerFactory.getLogger(CreatePickTasksCommandHandler.class);

    private final PickListRepository pickListRepository;
    private final MeterRegistry meterRegistry;

    public CreatePickTasksCommandHandler(PickListRepository pickListRepository,
                                         MeterRegistry meterRegistry) {
        this.pickListRepository = pickListRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Void handle(CreatePickTasksCommand cmd) {
        // ── Step 1: build aggregate via factory ───────────────────────────────
        com.shipping.pick.domain.model.PickList pickList =
            com.shipping.pick.domain.model.PickList.from(cmd.pickList());

        // ── Step 2: persist all tasks ─────────────────────────────────────────
        pickListRepository.saveAll(pickList);

        log.info("Created {} pick tasks for pickListId={}",
                 pickList.tasks().size(), pickList.pickListId());
        meterRegistry.counter("pick.tasks.created").increment(pickList.tasks().size());
        return null;
    }
}
