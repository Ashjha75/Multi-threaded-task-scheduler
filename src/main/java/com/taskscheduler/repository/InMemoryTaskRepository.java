package com.taskscheduler.repository;

import com.taskscheduler.model.Task;
import com.taskscheduler.model.TaskPriority;
import com.taskscheduler.model.TaskStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTaskRepository implements TaskRepository {

	/**
	 * The backing store.
	 *
	 * WHY ConcurrentHashMap and not Collections.synchronizedMap(new HashMap<>())?
	 *
	 * Collections.synchronizedMap locks the ENTIRE map for every read/write.
	 * With 3 worker threads all trying to read different tasks, they'd queue up
	 * behind a single lock — terrible throughput.
	 *
	 * ConcurrentHashMap uses "lock striping" — internally, it's divided into
	 * buckets, each with its own lock. Thread A can read bucket 1 while Thread B
	 * writes to bucket 5; they don't block each other.
	 *
	 * For this project: with ~100-1000 tasks and 3 workers, ConcurrentHashMap
	 * will be dramatically faster under contention.
	 *
	 * Trade-off: ConcurrentHashMap's iteration is weakly consistent (you might
	 * see tasks added/removed mid-iteration), but that's fine for our use case.
	 */

	private final ConcurrentHashMap<UUID, Task> store =
		new ConcurrentHashMap<>();

	@Override
	public void save(Task task) {
		if (task == null) {
			throw new IllegalArgumentException("Task cannot be null");
		}
		store.putIfAbsent(task.getId(), task);
		System.out.println("[Repo] Saved task: " + task.getId());
	}

	@Override
	public Optional<Task> findById(UUID id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(store.get(id));
	}

	@Override
	public List<Task> findAll() {
		return new ArrayList<>(store.values());
	}

	@Override
	public List<Task> findByStatus(TaskStatus status) {
		if (status == null) {
			return Collections.emptyList();
		}
		return store
			.values()
			.stream()
			.filter(task -> task.getStatus().equals(status))
			.toList();
	}

	@Override
	public List<Task> findByPriority(TaskPriority priority) {
		if (priority == null) {
			return Collections.emptyList();
		}
		return store
			.values()
			.stream()
			.filter(task -> task.getPriority().equals(priority))
			.toList();
	}

	@Override
	public void update(Task task) {
		if (task == null) {
			throw new IllegalArgumentException("Task cannot be null");
		}
		if (!store.containsKey(task.getId())) {
			throw new IllegalStateException("Task not found: " + task.getId());
		}
		store.putIfAbsent(task.getId(), task);
		System.out.println("[Repo] Updated task: " + task.getId());
	}

	@Override
	public boolean delete(UUID id) {
		if (id == null) {
			throw new IllegalArgumentException("Task  id cannot be null");
		}

		Task removed = store.remove(id);
		if (removed != null) {
			System.out.println("[Repo] Deleted task: " + id);
			return true;
		}
		return false;
	}

	@Override
	public int count() {
		return store.size();
	}

	public void printAll() {
		System.out.println("=== All Tasks ===");
		findAll().forEach(task ->
			System.out.println(
				"  " +
					task.getId() +
					" | " +
					task.getName() +
					" | Status: " +
					task.getStatus() +
					" | Retries: " +
					task.getRetryCount()
			)
		);
	}
}
