package org.nut.dynamatrix;

import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import hudson.model.Node;

class NodeData {
    /* This class encapsulates interesting data about a single Jenkins Node.
     * A lot of these data points are stored in a NodeCaps object below.
     */

    // Info about the build agent (node) detailed here. The "node" field
    // also serves to skip subsequent attempts to "set" the final fields
    // after they were populated once.
    private hudson.model.Node node = null
    public final String name

    // Original full-string set of labels received from Jenkins Node:
    public final String labelString
    // Labels split into an array by whitespaces:
    public final SortedSet<String> labelSet
    // The array of labels split further into a Map: those with an equality
    // sign are converted into key=[valueSet] (since our use-case can have
    // many values with same key, like compiler versions); those without
    // the sign become key=null; also the original unique key=value strings
    // are saved as keys similarly mapped to null:
    public final Map<String, Object> labelMap

    // Keep not initialized for now
    public NodeData() {
    }

    public NodeData(hudson.model.Node node) {
        this.setNode(node)
    }

    public NodeData(String nodeName) {
        def jenkins = Jenkins.getInstanceOrNull()
        if (jenkins != null) {
            def node = jenkins.getNode(nodeName)
            // that does check for null input
            this.setNode(node)
        }
    }

    @NonCPS
    void setNode(hudson.model.Node node) {
        if (node != null && this.node == null) {
            this.node = node

            // Other fields are final, so can not be re-set by us or external code:
            this.name = node.getNodeName()
            this.labelString = node.labelString
            this.labelSet = node.labelString.split('[ \r\n\t]+')

            // Finally, collect the map of labels which declare certain capabilities
            // There may be several hits e.g. "GCCVER=8 GCCVER=10" so we save Sets
            def labelMap = [:]
            for (String label : labelSet) {
                if (label == null) continue
                label = label.trim()
                if (label.equals("")) continue

                if (label.contains("=")) {
                    // Save custom mapping:
                    String[] keyValue = label.split("=", 2)
                    String k = keyValue[0].trim()
                    String v = keyValue[1].trim()
                    if (!labelMap.containsKey(k)) {
                        labelMap[k] = new TreeSet()
                    }
                    labelMap[k] << v
                }

                // Also save the original string "as is", as if a single-value
                // token... or it just was a single-value token, no equality
                // sign in contents:
                labelMap[label] = null
            }
            this.labelMap = labelMap
        }
    }

    hudson.model.Node getNode() {
        return this.node;
    }

    // Other fields are readable directly for now
}

class NodeCaps {
    /* This class encapsulates retrieval, storage and queries to information
     * about labels declared by Jenkins build agents which is of interest to
     * the dynamatrix effort, to know the agents' capabilities.
     */

    private Boolean isInitialized = false
    public Boolean enableDebugTrace = false
    public Boolean enableDebugErrors = true

    // What we looked for (null means all known nodes):
    public final String labelExpression

    // Collected data:
    // TODO: Is a Map needed now that we have a copy of "node" in NodeData?
    // Maybe a Set is okay? On the other hand, being a Map key guarantees
    // uniqueness...
    public final Map<hudson.model.Node, NodeData> nodeData

    public NodeCaps() {
        // Initialize later
    }

    public NodeCaps(String builderLabel) {
        // Initialize now - explicit arg is needed, null or not
        this.detectCapabilityLabelsForBuilders(builderLabel)
    }

    /*
     * Collect all info about useful build agents in one collection:
     * Returns a Map with names of currently known agent which matched the
     * builderLabel (or all agents if builderLabel content is trivial),
     * mapped to the Map of nodes' selected metadata including capabilities
     * they declared with further labels mapped as KEY=VALUE entries.
     */
    @NonCPS
    void detectCapabilityLabelsForBuilders(String builderLabel = null) {
        if (this.isInitialized) return
        
        // Track the original expression, matched or not, in the returned value
        // TODO: Maybe track something else like timestamps?
        this.labelExpression = builderLabel
        nodeData = [:]

        Set<hudson.model.Node> builders = null
        if (builderLabel == null || "".equals(builderLabel.trim())) {
            def jenkins = Jenkins.getInstanceOrNull()
            if (jenkins != null) {
                builders = jenkins.getNodes()
            }
        } else {
            Label le = Label.get(builderLabel)
            builders = le.getNodes()
        }
        if (builders == null) {
            // Let the caller know we found no (suitable) workers
            return nodeCaps
        }

        for (hudson.model.Node node : builders) {
            if (node == null) continue
            nodeData << new NodeData(node)
        }

        this.nodeData = nodeData
        this.isInitialized = true
    }

    void printDebug() {
        if (!this.isInitialized) {
            println "[DEBUG] nodeCaps: not initialized yet"
            return
        }

        try {
            //println "[DEBUG] raw nodeCaps: " + this
            println "[DEBUG] nodeCaps.labelExpression: " + this.labelExpression
            println "[DEBUG] nodeCaps.nodeData.size(): " + this.nodeData.size()
            for (node in this.nodeData.keySet()) {
                if (node == null) continue
                println "[DEBUG] nodeCaps.nodeData[${node}].labelMap.size()\t: " + this.nodeData[node].labelMap.size()
                for (String label : nodeCaps.nodeData[node].labelMap.keySet()) {
                    println "[DEBUG] nodeCaps.nodeData[${node}].labelMap['${label}']\t: " + this.nodeData[node].labelMap[label].toString()
                }
            }
        } catch (Throwable t) {
            println "[DEBUG] FAILED to print nodeCaps, possibly unexpected structure"
        }
    }

    void optionalPrintDebug() {
        if (this.enableDebugTrace || this.enableDebugErrors) {
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

        if (!this.isInitialized) {
            if (this.enableDebugErrors) println "[DEBUG] resolveAxisName(): this NodeCaps object is not populated yet"
            return res
        }

        // If caller has a Set to check, they should iterate it on their own
        // TODO: or maybe provide a helper wrapper?..
        if (axis == null || (!axis.getClass() in [String, java.lang.String, GString, java.util.regex.Pattern]) || axis.equals("")) {
            if (this.enableDebugErrors) println "[DEBUG] resolveAxisName(): invalid input value or class: " + axis.toString()
            return res
        }

        if (this.enableDebugTrace) println "[DEBUG] resolveAxisName(): " + axis.getClass() + " : " + axis.toString()

        if (axis in String || axis in GString) {
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
                    if (expandedAxisName == null || (!expandedAxisName in String && !expandedAxisName in GString) || expandedAxisName.equals("")) continue;

                    // This layer of recursion gets us fixed-string name
                    // variants of the variable axis (like 'GCC' and
                    // 'CLANG' for variable '${COMPILER}' in originally
                    // requested axis name '${COMPILER}VAR').
                    // Pattern looks into nodeCaps.
                    for (expandedAxisValue in this.resolveAxisValues(expandedAxisName)) {
                        if (expandedAxisValue == null || (!expandedAxisValue in String && !expandedAxisValue in GString) || expandedAxisValue.equals("")) continue;

                        // In the original axis like '${COMPILER}VER' apply current item
                        // from expandedAxisValue like 'GCC' (or 'CLANG' in next loop)
                        // and yield 'GCCVER' as the axis name for original request:
                        String tmpAxis = axis.replaceFirst(/\$\{${varAxis}\}/, expandedAxisValue)

                        // Now resolve the value of "axis" with one substituted
                        // expansion variant - if it is a fixed string by now,
                        // this will end quickly:
                        res << this.resolveAxisName(tmpAxis)
                    }
                }
                return res.flatten()
            } else {
                res = [axis]
                return res
            }
        }

        if (axis.getClass() in java.util.regex.Pattern) {
            // Return label keys which match the expression
            for (node in this.nodeData.keySet()) {
                if (node == null) continue

                for (String label : this.nodeData[node].labelMap.keySet()) {
                    if (label == null) continue
                    if (this.enableDebugTrace) {
                        println "[DEBUG] resolveAxisName(): label: " + label.getClass() + " : " + label.toString()
                        println "[DEBUG] resolveAxisName(): value: " + this.nodeData[node].labelMap[label]?.getClass() + " : " + this.nodeData[node].labelMap[label]?.toString()
                    }
                    if (label.trim() =~ axis && !label.contains("=")) {
                        if (this.enableDebugTrace) println "[DEBUG] resolveAxisName(): label matched axis as regex"
                        res << label
                    }
                }
            }
        }

        return res.flatten()
    }

    def resolveAxisValues(Object axis) {
        // For a fixed-string name or regex pattern, return a flattened Set of
        // values which have it as a key in nodeCaps.nodeData[].labelMap[]

        Set res = []

        if (!this.isInitialized) {
            if (this.enableDebugErrors) println "[DEBUG] resolveAxisName(): this NodeCaps object is not populated yet"
            return res
        }

        if (axis == null || (!axis.getClass() in [String, java.lang.String, GString, java.util.regex.Pattern]) || axis.equals("")) {
            if (this.enableDebugErrors) println "[DEBUG] resolveAxisValues(): invalid input value or class: " + axis.toString()
            return res;
        }

        if (this.enableDebugTrace) println "[DEBUG] resolveAxisValues(): " + axis.getClass() + " : " + axis.toString()

        for (node in this.nodeData.keySet()) {
            if (node == null) continue

            for (String label : this.nodeData[node].labelMap.keySet()) {
                if (label == null) continue
                if (this.enableDebugTrace) {
                    println "[DEBUG] resolveAxisValues(): label: " + label.getClass() + " : " + label.toString()
                    println "[DEBUG] resolveAxisValues(): value: " + this.nodeData[node].labelMap[label]?.getClass() + " : " + this.nodeData[node].labelMap[label]?.toString()
                }
                if (axis.getClass() in [String, GString, java.lang.String]) {
                    if (axis.trim().equals(label.trim())) {
                        if (this.enableDebugTrace) println "[DEBUG] resolveAxisValues(): label matched axis as string"
                        res << this.nodeData[node].labelMap[label]
                    } else {
                        if (this.enableDebugTrace) println "[DEBUG] resolveAxisValues(): label did not match axis as string"
                    }
                }
                if (axis.getClass() in java.util.regex.Pattern) {
                    if (label.trim() =~ axis && !label.contains("=")) {
                        if (this.enableDebugTrace) println "[DEBUG] resolveAxisValues(): label matched axis as regex"
                        res << this.nodeData[node].labelMap[label]
                    } else {
                        if (this.enableDebugTrace) println "[DEBUG] resolveAxisValues(): label did not match axis as regex"
                    }
                }
            }
        }

        return res.flatten()
    }
}

