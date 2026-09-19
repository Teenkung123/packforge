package com.teenkung.packforge.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class PreparationBudgetTest {
    @Test void errorsAndRepeatedFailureObjectsCannotAbandonLaterOwners() {
        PreparationBudget budget = new PreparationBudget(100);
        var scope = budget.openScope();
        var lease = scope.tryReserve(100);
        AssertionError failure = new AssertionError("cleanup");
        scope.onRetire(() -> { throw failure; });
        scope.onRetire(() -> { throw failure; });
        scope.onRetire(lease::close);
        assertSame(failure, assertThrows(AssertionError.class, scope::retire));
        assertEquals(0, budget.used());
    }

    @Test void reducingRetiredWorkspacePreservesRetainedOwnership() {
        PreparationBudget budget = new PreparationBudget(100);
        var retired = budget.openScope();
        var allocation = retired.tryReserve(100);
        retired.retire();
        allocation.reduceTo(25);
        assertEquals(25, allocation.bytes());
        assertEquals(25, retired.used());
        assertEquals(25, budget.used());
        assertEquals(100, budget.peak());
        var next = budget.openScope();
        assertNull(next.tryReserve(76));
        var remaining = next.tryReserve(75);
        assertNotNull(remaining);
        allocation.close();
        allocation.close();
        allocation.reduceTo(0);
        assertEquals(75, budget.used());
        remaining.close();
        assertEquals(0, budget.used());
    }

    @Test void reductionRejectsGrowthAndNegativeSizesWithoutChangingUsage() {
        PreparationBudget budget = new PreparationBudget(100);
        var allocation = budget.openScope().tryReserve(60);
        assertThrows(IllegalArgumentException.class, () -> allocation.reduceTo(-1));
        assertThrows(IllegalArgumentException.class, () -> allocation.reduceTo(61));
        allocation.reduceTo(20);
        assertThrows(IllegalArgumentException.class, () -> allocation.reduceTo(21));
        assertEquals(20, budget.used());
        allocation.reduceTo(0);
        allocation.close();
        assertEquals(0, budget.used());
    }

    @Test void reductionRacingCloseCannotReleaseAnotherOwnersBytes() throws Exception {
        PreparationBudget budget = new PreparationBudget(100);
        var scope = budget.openScope();
        var retained = scope.tryReserve(10);
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 1000; i++) {
                var allocation = scope.tryReserve(90);
                Future<?> reduce = pool.submit(() -> allocation.reduceTo(30));
                Future<?> close = pool.submit(allocation::close);
                reduce.get();
                close.get();
                assertEquals(10, budget.used());
            }
        } finally { pool.shutdownNow(); retained.close(); }
        assertEquals(0, budget.used());
    }

    @Test void retirementCallbacksRunOnceOutsideAccountingLock() {
        PreparationBudget budget = new PreparationBudget(100);
        var scope = budget.openScope();
        List<String> calls = new ArrayList<>();
        scope.onRetire(() -> {
            assertFalse(Thread.holdsLock(budget));
            assertTrue(scope.isRetired());
            calls.add("first");
        });
        scope.retire();
        scope.retire();
        scope.onRetire(() -> calls.add("late"));
        assertEquals(List.of("first", "late"), calls);
    }

    @Test void cleanupFailureDoesNotSkipOtherOwners() {
        PreparationBudget budget = new PreparationBudget(100);
        var scope = budget.openScope();
        var lease = scope.tryReserve(100);
        scope.onRetire(() -> { throw new IllegalStateException("test cleanup failure"); });
        scope.onRetire(lease::close);
        assertThrows(IllegalStateException.class, scope::retire);
        assertEquals(0, budget.used());
    }

    @Test void overlappingScopesKeepRetiredLeasesCharged() {
        PreparationBudget budget = new PreparationBudget(100);
        var old = budget.openScope(80);
        var lease = old.tryReserve(80);
        assertNotNull(lease);
        old.retire();
        assertNull(old.tryReserve(0));
        var next = budget.openScope();
        assertNull(next.tryReserve(21));
        var remaining = next.tryReserve(20);
        assertNotNull(remaining);
        assertEquals(100, budget.peak());
        lease.close();
        lease.close();
        assertEquals(20, budget.used());
        remaining.close();
        assertEquals(0, budget.used());
    }

    @Test void validatesBoundsWithoutOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new PreparationBudget(-1));
        PreparationBudget budget = new PreparationBudget(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> budget.openScope(-1));
        var scope = budget.openScope();
        assertThrows(IllegalArgumentException.class, () -> scope.tryReserve(-1));
        var lease = scope.tryReserve(Long.MAX_VALUE);
        assertNotNull(lease);
        assertNull(scope.tryReserve(1));
        lease.close();
        assertEquals(0, budget.used());
        assertThrows(IllegalArgumentException.class, () -> new PreparationBudget(10).openScope(11));
    }

    @Test void concurrentReservationsNeverExceedLimit() throws Exception {
        PreparationBudget budget = new PreparationBudget(17);
        var scope = budget.openScope();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                for (int j = 0; j < 1000; j++) {
                    var lease = scope.tryReserve(5);
                    if (lease != null) {
                        assertTrue(budget.used() <= 17);
                        lease.close();
                    }
                }
            }));
            for (var result : results) result.get();
        } finally { pool.shutdownNow(); }
        assertEquals(0, budget.used());
        assertTrue(budget.peak() <= 17);
    }
}
