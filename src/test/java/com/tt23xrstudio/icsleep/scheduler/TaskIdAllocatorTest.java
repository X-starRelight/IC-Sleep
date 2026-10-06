package com.tt23xrstudio.icsleep.scheduler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务 ID 分配器单元测试。
 */
class TaskIdAllocatorTest {

    @Test
    void testStartsAtTwo() {
        TaskIdAllocator allocator = new TaskIdAllocator(2);
        Set<Integer> inUse = new HashSet<>();
        assertEquals(2, allocator.allocate(inUse));
        assertEquals(3, allocator.allocate(inUse));
    }

    @Test
    void testNoZeroOrOne() {
        TaskIdAllocator allocator = new TaskIdAllocator(2);
        Set<Integer> inUse = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            int id = allocator.allocate(inUse);
            assertTrue(id >= 2);
            inUse.add(id);
        }
    }

    @Test
    void testSkipsOccupied() {
        TaskIdAllocator allocator = new TaskIdAllocator(2);
        Set<Integer> inUse = new HashSet<>();
        inUse.add(2);
        assertEquals(3, allocator.allocate(inUse));
    }

    @Test
    void testWrapAround() {
        // 从很大的 ID 开始，验证回绕到起始值并跳过占用
        TaskIdAllocator allocator = new TaskIdAllocator(2);
        allocator.setNextTaskId(TaskIdAllocator.MAX_ID);
        Set<Integer> inUse = new HashSet<>();
        inUse.add(TaskIdAllocator.MAX_ID);
        assertEquals(2, allocator.allocate(inUse));
    }

    @Test
    void testSetNextTaskIdClamps() {
        TaskIdAllocator allocator = new TaskIdAllocator(5);
        allocator.setNextTaskId(1);
        assertEquals(5, allocator.peek());
        allocator.setNextTaskId(10);
        assertEquals(10, allocator.peek());
    }

    @Test
    void testSequentialNoDuplicate() {
        TaskIdAllocator allocator = new TaskIdAllocator(2);
        Set<Integer> inUse = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int id = allocator.allocate(inUse);
            assertTrue(inUse.add(id), "ID 重复: " + id);
        }
    }
}
