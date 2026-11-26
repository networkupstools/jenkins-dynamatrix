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

        parent.countStagesIncrement('STARTED')
        clone.countStagesIncrement('FAILURE')
        clone.setWorstResult('FAILURE')

        Map<String, Integer> counts = parent.getCountStages(true)
        assertEquals(1, counts.STARTED)
        assertEquals(1, counts.FAILURE)
        assertEquals(1, counts.FINISHED)
        assertEquals(Result.FAILURE, parent.getWorstResult(true))
    }

    private static String objectID(Dynamatrix dynamatrix) {
        Field objectIDField = Dynamatrix.getDeclaredField('objectID')
        objectIDField.accessible = true
        return (String) objectIDField.get(dynamatrix)
    }
}