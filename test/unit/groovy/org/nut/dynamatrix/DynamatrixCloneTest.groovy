package org.nut.dynamatrix

import hudson.model.Result
import org.junit.jupiter.api.Test

import java.lang.reflect.Field

import static org.junit.jupiter.api.Assertions.*

class DynamatrixCloneTest {

    @Test
    void testRememberedCloneHasUniqueIdentityAndAggregatesOnce() {
        Dynamatrix parent = new Dynamatrix(null)
        Dynamatrix clone = parent.clone('remembered clone', true)

        assertNotEquals(objectID(parent), objectID(clone))
        assertEquals(progressID(parent), progressID(clone))

        parent.countStagesExpected = 374
        assertEquals(374, clone.countStagesExpected)

        parent.countStagesIncrement('STARTED')
        clone.countStagesIncrement('FAILURE')
        clone.setWorstResult('FAILURE')

        Map<String, Integer> counts = parent.getCountStages(true)
        assertEquals(1, counts.STARTED)
        assertEquals(1, counts.FAILURE)
        assertEquals(1, counts.FINISHED)
        assertEquals(Result.FAILURE, parent.getWorstResult(true))
    }

    @Test
    void testCloneUsesUpdatedMatrixExpectedCount() {
        Dynamatrix parent = new Dynamatrix(null)
        Dynamatrix clone = parent.clone('prepared clone', false)

        parent.countStagesExpected = 374

        assertEquals(374, clone.countStagesExpected)
        assertEquals(progressID(parent), progressID(clone))
        assertNotEquals(objectID(parent), objectID(clone))
    }

    @Test
    void testExpectedStageCountBackingFieldStaysInSyncWithSharedState() {
        Field countStagesExpectedField = Dynamatrix.getDeclaredField('countStagesExpected')
        countStagesExpectedField.accessible = true

        Dynamatrix parent = new Dynamatrix(null)
        Dynamatrix clone = parent.clone('prepared clone', false)

        parent.countStagesExpected = 374
        assertEquals(374, countStagesExpectedField.get(parent))
        assertEquals(374, clone.countStagesExpected)

        clone.countStagesExpected = 375
        assertEquals(375, countStagesExpectedField.get(clone))
        assertEquals(375, parent.countStagesExpected)
    }

    private static String objectID(Dynamatrix dynamatrix) {
        Field objectIDField = Dynamatrix.getDeclaredField('objectID')
        objectIDField.accessible = true
        return (String) objectIDField.get(dynamatrix)
    }

    private static String progressID(Dynamatrix dynamatrix) {
        Field progressOwnerIDField = Dynamatrix.getDeclaredField('progressOwnerID')
        progressOwnerIDField.accessible = true
        return (String) progressOwnerIDField.get(dynamatrix) ?: objectID(dynamatrix)
    }
}