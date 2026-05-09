package com.shipping.pick.infrastructure.persistence;

import com.shipping.pick.domain.model.PickList;
import com.shipping.pick.domain.model.PickTask;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the {@link PickList} aggregate root.
 * <p>
 * Internally delegates to {@link PickTaskRepository} (ScyllaDB wide-row
 * {@code pick_tasks} table).  This layer owns the mapping between the
 * aggregate's in-memory shape and the flattened persistence model.
 */
@Repository
public class PickListRepository {

    private final PickTaskRepository taskRepository;

    public PickListRepository(PickTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Loads all tasks for the given pick list from ScyllaDB and reconstitutes
     * the {@link PickList} aggregate.  Returns {@code null} if no tasks are found.
     */
    public PickList findByPickListId(String pickListId) {
        List<PickTask> tasks = taskRepository.findByPickList(pickListId);
        if (tasks.isEmpty()) return null;
        // orderId and fcId are denormalised on every task row
        String orderId = tasks.get(0).orderId();
        String fcId    = tasks.get(0).fcId();
        return PickList.reconstitute(pickListId, orderId, fcId, tasks);
    }

    /**
     * Persists all tasks in the pick list (used when materialising a new list
     * from a {@code WaveReleased} event).
     */
    public void saveAll(PickList pickList) {
        pickList.tasks().forEach(taskRepository::save);
    }

    /**
     * Persists only the tasks whose status has changed (used after a scan
     * confirmation or short).
     */
    public void updateTask(PickTask task) {
        taskRepository.update(task);
    }
}
