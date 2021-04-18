package org.nut.dynamatrix;

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

class Utils {
    public static final def classesStrings = [String, GString, org.codehaus.groovy.runtime.GStringImpl, java.lang.String]
    public static final def classesRegex = [java.util.regex.Pattern]
    public static final def classesStringOrRegex = classesStrings + classesRegex
    public static final def classesMaps = [Map, LinkedHashMap, HashMap]
    public static final def classesLists = [ArrayList, List, Set, TreeSet, LinkedHashSet, Object[]]

    public static Boolean isString(obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesStrings) return true;
        if (obj instanceof String) return true;
        if (obj instanceof GString) return true;
        if (obj instanceof org.codehaus.groovy.runtime.GStringImpl) return true;
        return false;
    }

    public static Boolean isRegex(obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesRegex) return true;
        if (obj instanceof java.util.regex.Pattern) return true;
        return false;
    }

    public static Boolean isStringOrRegex(obj) {
        if (obj == null) return false;
        if (isString(obj) || isRegex(obj)) return true;
        return false;
    }

    public static Boolean isStringNotEmpty(obj) {
        if (!isString(obj)) return false;
        if ("".equals(obj)) return false;
        return true;
    }

    public static Boolean isStringOrRegexNotEmpty(obj) {
        if (isString(obj)) {
            if ("".equals(obj)) return false;
            return true;
        }
        // No idea how to check for empty regex,
        // or more - if that makes sense :)
        return isRegex(obj)
    }

    public static Boolean isMap(obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesMaps) return true;
        if (obj instanceof Map) return true;
        return false;
    }

    public static Boolean isList(obj) {
        if (obj == null) return false;
        if (obj.getClass() in classesLists) return true;
        if (obj instanceof Set) return true;
        if (obj instanceof List) return true;
        if (obj instanceof ArrayList) return true;
        if (obj instanceof Object[]) return true;
        return false;
    }

    @NonCPS
    public static Boolean isNode(obj) {
        if (obj == null) return false;
        if (obj.getClass() in [hudson.model.Node] || obj in hudson.model.Node) return true;
        if (obj instanceof hudson.model.Node) return true;
        return false;
    }

    public static String castString(obj) {
        return "<${obj?.getClass()}>(${obj?.toString()})"
    }

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

    static def mergeMapSet(orig, addon, Boolean debug = false) {
        // Note: debug println() below might not go anywhere

        /* For objects that are like an Array, List or Set, this routine
         * simply appends contents of "addon" to "orig". For Maps it recurses
         * so it can process the object which is value in a Map for same key.
         * For other types, replace orig with addon.
         * Returns the result of merge (or whatever did happen there).
         */

        if (isList(orig)) {
            if (isList(addon)) {
                // Concatenate
                if (debug) println "Both orig and addon are arrays, concatenate:\n  ${orig}\n+ ${addon}\n"
                return (orig + addon)
            }
            // For other types, append as a single new array item
            if (debug) println "The orig is an array, addon is not; append it:\n  ${orig}\n+ ${addon}\n"
            return (orig << addon)
        }

        if (isMap(orig)) {
            if (isMap(addon)) {
                if (debug) println "Both orig and addon are Maps, concatenate recursively:\n  ${orig}\n+ ${addon}\n"
                for (k in addon.keySet()) {
                    if (orig.containsKey(k)) {
                        if (debug) println "+ Merging orig[${k}]=${orig[k]} with addon[${k}]=${addon[k]}"
                        orig[k] = mergeMapSet(orig[k], addon[k])
                    } else {
                        if (debug) println "+ Adding new orig[${k}] from addon[${k}]=${addon[k]}\n"
                        orig[k] = addon[k]
                    }
                }
                return orig
            }
            throw new Exception("Can not mergeMapSet() a non-Map: ${castString(addon)} into a Map: ${castString(orig)}")
        }

        // For other types, replace with new value
        if (debug) println "Both orig and addon are neither arrays nor maps, replace:\n${orig}\n${addon}\n"
        return addon
    }
}
