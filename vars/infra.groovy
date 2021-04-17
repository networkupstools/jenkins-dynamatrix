/*
 * Return the label (expression) string for a worker that handles
 * git checkouts and stashing of the source for other build agents.
 * Unlike the other agents, this worker should have appropriate
 * internet access, possibly reference git repository cache, etc.
 */
def labelDefaultWorker() {
    // TODO: Global/modifiable config point?
    return "master-worker"
}

/*
 * Strangely, the Set or list classes I needed in dynamatrix did not include
 * a cartesian multiplication seen in many examples and complained about a
 *    groovy.lang.MissingMethodException: No signature of method:
 *      java.util.ArrayList.multiply() is applicable for argument
 *      types: (java.util.ArrayList) values: ...
 * Injecting this method into Collection base class is suggested by the
 * articles linked below (using "Iterable.metaClass.mixin newClassName" or
 * "java.util.Collection.metaClass.newFuncName", but I am not sure how to
 * do that via Jenkins shared library once and for all its use-cases.
 * So the next best thing is to call a function to do stuff.
 */
static Iterable cartesianProduct(Iterable a, Iterable b) {
    // Inspired by https://rosettacode.org/wiki/Cartesian_product_of_two_or_more_lists#Groovy
    // and https://coviello.blog/2013/05/19/adding-a-method-for-computing-cartesian-product-to-groovys-collections/
    assert [a,b].every { it != null }
    def (m,n) = [a.size(),b.size()]
    return ( (0..<(m*n)).inject([]) { prod, i -> prod << [a[i.intdiv(n)], b[i%n]].flatten().sort() } )
}

static Iterable cartesianSquared(Iterable arr) {
    /* Return a cartesian product of items stored in a single set.
     * This likely can be made more efficient, but this codepath
     * is not too hot in practice anyway.
     */
    Iterable res = []
    for (a in arr) {
        if (res.size() == 0) {
            res = a
        } else {
            res = cartesianProduct(res, a)
        }
    }
    return res
}
