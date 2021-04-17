package org.nut.dynamatrix;

import java.util.ArrayList;
import java.util.Arrays.*;
import java.util.regex.*;

import hudson.model.Node;

class NodeData {
    /* This class encapsulates interesting data about a single Jenkins Node.
     * A lot of these data points are stored in a NodeCaps object below.
     */

    // Info about the build agent (node) detailed here.
    public final hudson.model.Node node
    public final String name

    // Original full-string set of labels received from Jenkins Node:
    public final String labelString
    // Labels split into an array (or rather sorted set) by whitespaces:
    public final TreeSet<String> labelSet
    // The array of labels split further into a Map: those with an equality
    // sign are converted into key=[valueSet] (since our use-case can have
    // many values with same key, like compiler versions); those without
    // the sign become key=null; also the original unique key=value strings
    // are saved as keys similarly mapped to null:
    public final Map<String, Object> labelMap

    public NodeData(Object nodeOrig) throws Exception {
        // System.out.println("NodeData: got arg: (" + nodeOrig.getClass() + ") : " + nodeOrig.toString())

        hudson.model.Node node = null
        if (nodeOrig.getClass() in [hudson.model.Node] || nodeOrig in hudson.model.Node) {
            node = nodeOrig
        } else if (nodeOrig.getClass() in [String, GString]) {
            def jenkins = Jenkins.getInstanceOrNull()
            if (jenkins != null) {
                node = jenkins.getNode(nodeOrig)
            }
            if (node == null) {
                throw new Exception("NodeData: bad argument to constructor: node was not found by name: (" + nodeOrig.getClass() + ") : " + nodeOrig.toString())
            }
        }

        if (node == null) {
            throw new Exception("NodeData: bad argument to constructor: invalid value or class: (" + nodeOrig.getClass() + ") : " + nodeOrig.toString())
        }

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

    hudson.model.Node getNode() {
        return this.node;
    }

    // Other fields are readable directly for now
}

