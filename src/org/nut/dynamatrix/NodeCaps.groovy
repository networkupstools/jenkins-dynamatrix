package org.nut.dynamatrix;

import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import hudson.model.Node;

import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;
import org.nut.dynamatrix.dynamatrixGlobalState;

class NodeCaps {
    /* This class encapsulates retrieval, storage and queries to information
     * about labels declared by Jenkins build agents which is of interest to
     * the dynamatrix effort, to know the agents' capabilities.
     */

    def script

    private Boolean isInitialized = false
    public Boolean enableDebugTrace = dynamatrixGlobalState.enableDebugTrace
    public Boolean enableDebugErrors = dynamatrixGlobalState.enableDebugErrors

    // What we looked for (null means all known nodes):
    public final String labelExpression

    // Collected data:
    // TODO: Is a Map needed now that we have a copy of "node" in NodeData?
    // Maybe a Set is okay? On the other hand, being a Map key guarantees
    // uniqueness...
    public final Map<String, NodeData> nodeData

    public NodeCaps(script, String builderLabel = null, Boolean debugTrace = null, Boolean debugErrors = null) {
        /*
         * Collect all info about useful build agents in one collection:
         * Returns a Map with names of currently known agent which matched the
         * builderLabel (or all agents if builderLabel content is trivial),
         * mapped to the Map of nodes' selected metadata including capabilities
         * they declared with further labels mapped as KEY=VALUE entries.
         */

        this.script = script
        if (debugTrace != null) {
            this.enableDebugTrace = debugTrace
        }
        if (debugErrors != null) {
            this.enableDebugErrors = debugErrors
        }

        // Track the original expression, matched or not, in the returned value
        // TODO: Maybe track something else like timestamps?
        this.labelExpression = builderLabel
        def nodeData = [:]

        // Have it sorted just in case:
        def builders = null
        if (builderLabel == null || "".equals(builderLabel.trim())) {
            def jenkins = Jenkins.getInstanceOrNull()
            if (jenkins != null) {
                builders = jenkins.getNodes()
                if (this.enableDebugTrace) this.script.println("NodeCaps: got all defined agents from Jenkins as builders: ${Utils.castString(builders)}")
            }
        } else {
            Label le = Label.get(builderLabel)
            builders = le.getNodes()
            if (this.enableDebugTrace) this.script.println("NodeCaps: got builders by label expression '${builderLabel}': ${Utils.castString(builders)}")
        }

        if (builders != null) {
            for (hudson.model.Node node : builders) {
                if (node == null) continue
                if (this.enableDebugTrace) this.script.println("NodeCaps: looking for node data: ${Utils.castString(node)}")
                nodeData[node.getNodeName()] = new NodeData(node)
            }
        }

        this.nodeData = nodeData
        this.isInitialized = true
    }

    @NonCPS
    public Boolean shouldDebugTrace() {
        return ( this.enableDebugTrace && this.script != null)
    }

    @NonCPS
    public Boolean shouldDebugErrors() {
        return ( (this.enableDebugTrace || this.enableDebugErrors) && this.script != null)
    }

    void printDebug() {
        if (!this.isInitialized) {
            this.script.println "[DEBUG] nodeCaps: not initialized yet"
            return
        }

        try {
            //this.script.println "[DEBUG] raw nodeCaps: " + this
            this.script.println "[DEBUG] nodeCaps.labelExpression: " + this.labelExpression
            this.script.println "[DEBUG] nodeCaps.nodeData.size(): " + this.nodeData.size()
            for (nodeName in this.nodeData.keySet()) {
                if (nodeName == null) continue
                this.script.println "[DEBUG] nodeCaps.nodeData[${nodeName}].labelMap.size()\t: " + this.nodeData[nodeName].labelMap.size()
                for (String label : this.nodeData[nodeName].labelMap.keySet()) {
                    this.script.println "[DEBUG] nodeCaps.nodeData[${nodeName}].labelMap['${label}']\t: ${Utils.castString(this.nodeData[nodeName].labelMap[label])}"
                }
            }
        } catch (Throwable t) {
            this.script.println "[DEBUG] FAILED to print nodeCaps, possibly unexpected structure: ${Utils.castString(t)}"
        }
    }

    void optionalPrintDebug() {
        if (this.enableDebugTrace
//         || this.enableDebugErrors
        ) {
            this.printDebug()
        }
    }

    def resolveAxisName(Object axis) {
        // The "axis" may be a fixed string to return quickly, or
        // a regex to match among nodeCaps.nodeData[].labelMap[] keys
        // or a groovy expansion to look up value(s) of another axis
        // which is not directly used in the resulting build set.
        // Recurse until (a flattened Set of) fixed strings with
        // axis names (keys of labelMap[] above) can be returned.
        Set res = []
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        if (!this.isInitialized) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisName(): this NodeCaps object is not populated yet"
            return res
        }

        if (Utils.isString(axis)) {
            axis = axis.trim()
        }

        // If caller has a Set to check, they should iterate it on their own
        // TODO: or maybe provide a helper wrapper?..
        if (!Utils.isStringOrRegexNotEmpty(axis)) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisName(): invalid input value or class: ${Utils.castString(axis)}"
            return res
        }

        if (debugTrace) this.script.println "[DEBUG] resolveAxisName(): ${Utils.castString(axis)}"

        if (Utils.isString(axis)) {
            // NOTE: No support for nested request like '${COMPILER${VENDOR}}VER'
            def matcher = axis =~ /\$\{([^\}]+)\}/
            if (matcher.find()) {
                // Substitute values of one expansion and recurse -
                // if there are more dollar-braces, they will be
                // expanded in the nested layer(s)
                def varAxis = matcher[0][1]

                // This layer of recursion gets us fixed-string name
                // of the variable axis itself (like fixed 'COMPILER'
                // string for variable part '${COMPILER}' in originally
                // requested axis name '${COMPILER}VAR').
                for (expandedAxisName in this.resolveAxisName(varAxis)) {
                    if (!Utils.isStringNotEmpty(expandedAxisName)) continue;

                    // This layer of recursion gets us fixed-string name
                    // variants of the variable axis (like 'GCC' and
                    // 'CLANG' for variable '${COMPILER}' in originally
                    // requested axis name '${COMPILER}VAR').
                    // Pattern looks into nodeCaps.
                    for (expandedAxisValue in this.resolveAxisValues(expandedAxisName)) {
                        if (!Utils.isStringNotEmpty(expandedAxisValue)) continue;

                        // In the original axis like '${COMPILER}VER' apply current item
                        // from expandedAxisValue like 'GCC' (or 'CLANG' in next loop)
                        // and yield 'GCCVER' as the axis name for original request:
                        String tmpAxis = axis.replaceFirst(/\$\{${varAxis}\}/, expandedAxisValue)

                        // Now resolve the value of "axis" with one substituted
                        // expansion variant - if it is a fixed string by now,
                        // this will end quickly. Keep it as a composite value
                        // so that the fixed resolved key=value part is remembered:
                        res << this.resolveAxisName("${expandedAxisName}=${expandedAxisValue} ${tmpAxis}")

                        // NOTE for the above: it may be useful to constrain
                        // the ultimately returned axis values (in the map)
                        // to remember e.g. the "COMPILER=GCC" part  always
                        // for "GCCVER=1.2.3" after resolving the original
                        // axis request from "${COMPILER}VER" string.
                        // Put another way, for this simpler example with one
                        // substitution, the "COMPILER=GCC GCCVER" string
                        // might as well *be* the axis (supported elsewhere).
                        // NOTE: This is expected to work (thanks to recursion
                        // above) for resolvable axes with more than one
                        // variable part, but this was not tested so far.
                    }
                }
                return res.flatten()
            } else {
                res << axis
                return res
            }
        }

        if (Utils.isRegex(axis)) {
            // Return label keys which match the expression
            for (nodeName in this.nodeData.keySet()) {
                if (nodeName == null) continue

                for (String label : this.nodeData[nodeName].labelMap.keySet()) {
                    if (debugTrace) {
                        this.script.println "[DEBUG] resolveAxisName(): label: ${Utils.castString(label)}"
                        this.script.println "[DEBUG] resolveAxisName(): value: ${Utils.castString(this.nodeData[nodeName].labelMap[label])}"
                    }
                    if (label == null) continue
                    label = label.trim()
                    if (label.equals("")) continue
                    if (label =~ axis && !label.contains("=")) {
                        if (debugTrace) this.script.println "[DEBUG] resolveAxisName(): label matched axis as regex"
                        res << label
                    }
                }
            }
        }

        return res.flatten()
    }

    def resolveAxisValues(Object axis, node, Boolean returnAssignments = false) {
        /* Look into a single node */

        /* For a fixed-string name or regex pattern, return a flattened Set of
         * values which have it as a key in nodeCaps.nodeData[].labelMap[]
         * (so not including labels that were not originally a KEY=VALUE).
         * The returnAssignments flag controls whether the set would contain
         * individual value labels like ['8', '9'] or strings with assignments
         * that can be quickly put into label matching expression strings like
         * ['GCCVER=8', 'GCCVER=9'].
         * NOTE: For axis passed as a regex Pattern, a wording too strict
         * (like including dollars for end-of-pattern) can preclude matching
         * of the entries we want. This is not handled now - to e.g. trawl
         * through the arrays of earlier parsed values per labels in labelMap.
         * NOTE: Currently there is no error-checking whether the axis param
         * itself contains an equality sign, unsure how to do it for Pattern.
         */

        Set res = []
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        if (node == null)
            return res

        if (!this.isInitialized) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisName(): this NodeCaps object is not populated yet"
            return res
        }

        def labelFixed = ""
        if (Utils.isString(axis)) {
            axis = axis.trim()
            // We support complex labels like "COMPILER=GCC GCCVER" which
            // would originate from original "${COMPILER}VER" axis, out of
            // which we want to resolve the "GCCVER=1.2.3" for the matrix
            // and keep the fixed part as is.
            def matcher = axis =~ /^(.* )([^ ]*)$/
            if (matcher.find()) {
                labelFixed = matcher[0][1]
                axis = matcher[0][2]
                if (debugTrace) {
                    this.script.println "[DEBUG] resolveAxisValues(): axis split into" +
                        " fixed label part: '${labelFixed}' and the" +
                        " part whose values we would look for: '${axis}'" +
                        ( returnAssignments ? "" : " (note that fixed part is dropped when returnAssignments=${returnAssignments})")
                }
            }
        }

        if (!Utils.isStringOrRegexNotEmpty(axis)) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisValues(): invalid input value or class: ${Utils.castString(axis)}"
            return res;
        }

        if (debugTrace) this.script.println "[DEBUG] resolveAxisValues(${node}, ${returnAssignments}): looking for: ${Utils.castString(axis)}"

        for (String label : this.nodeData[node].labelMap.keySet()) {
            if (debugTrace) {
                this.script.println "[DEBUG] resolveAxisValues(): label: ${Utils.castString(label)}"
                this.script.println "[DEBUG] resolveAxisValues(): value: ${Utils.castString(this.nodeData[node].labelMap[label])}"
            }
            if (label == null) continue
            label = label.trim()
            if (label.equals("")) continue

            def val = this.nodeData[node].labelMap[label]
            if (Utils.isString(val)) {
                val = val.trim()
                if (val.equals("") && debugErrors) {
                    this.script.println "[WARNING] resolveAxisValues(): got a value which is an empty string"
                    // TODO: Should it be turned into NULL?..
                }
            }

            // TODO: If labelFixed is not empty, check if this node contains
            // all the labels and values listed in that part of our query

            Boolean hit = false
            if (debugTrace) {
                this.script.println "[DEBUG]   resolveAxisValues(${node}, ${returnAssignments}): " +
                    "looking for: ${Utils.castString(axis)}    " +
                    "GOTNEXT label: ${Utils.castString(label)} // " +
                    "value: ${Utils.castString(val)}"
            }
            if (Utils.isString(axis)) {
                if ( (!returnAssignments && val != null && axis.equals(label))
                ||   ( returnAssignments && val == null && label.startsWith("${axis}="))
                ) {
                    if (debugTrace) this.script.println "[DEBUG] resolveAxisValues(): label matched axis as string"
                    hit = true
                } else {
                    if (debugTrace) {
                        this.script.println "[DEBUG] resolveAxisValues(): label did not match axis as string :" +
                            " returnAssignments=${returnAssignments}" +
                            " (val==null)=" + (val==null) +
                            " (val!=null)=" + (val!=null) +
                            " (axis.equals(label))=" + (axis.equals(label)) +
                            " (axis.startsWith('${label}='))=" + (axis.startsWith("${label}=")) +
                            " (label.startsWith('${axis}='))=" + (label.startsWith("${axis}="))
                    }
                }
            }
            if (Utils.isRegex(axis)) {
                if ( (!returnAssignments && val != null && label =~ axis && !label.contains("="))
                ||   ( returnAssignments && val == null && label =~ ~/(${axis}|${axis}.*=)/ && label.contains("="))
                ) {
                    if (debugTrace) this.script.println "[DEBUG] resolveAxisValues(): label matched axis as regex"
                    hit = true
                } else {
                    if (debugTrace) this.script.println "[DEBUG] resolveAxisValues(): label did not match axis as regex"
                }
            }

            if (hit) {
                if (val == null) {
                    // returnAssignments tells us to return the label like 'GCCVER=9'
                    res << labelFixed + label
                } else {
                    // !returnAssignments tells us to return the value like '9'
                    // and ignore the labelFixed part
                    res << val
                }
            }

        }

        return res.flatten()
    }

    def resolveAxisValues(Object axis, Boolean returnAssignments = false) {
        /* See comments in the per-node variant above - this method calls
         * it for all selected and cached nodes, and aggregates the results
         */

        Set res = []
        def debugErrors = this.shouldDebugErrors()
        def debugTrace = this.shouldDebugTrace()

        if (!this.isInitialized) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisName(): this NodeCaps object is not populated yet"
            return res
        }

        if (Utils.isString(axis)) {
            axis = axis.trim()
        }

        if (!Utils.isStringOrRegexNotEmpty(axis)) {
            if (debugErrors) this.script.println "[DEBUG] resolveAxisValues(): invalid input value or class: ${Utils.castString(axis)}"
            return res;
        }

        if (debugTrace) this.script.println "[DEBUG] resolveAxisValues(${returnAssignments}): looking for: ${Utils.castString(axis)}"

        for (nodeName in this.nodeData.keySet()) {
            if (nodeName == null) continue
            def nres = resolveAxisValues(axis, nodeName, returnAssignments)
            if (nres != null && nres.size() > 0)
                res << nres
        }

        return res.flatten()
    }
}

