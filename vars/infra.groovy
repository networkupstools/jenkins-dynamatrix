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
 * Collect all info about useful build agents in one collection:
 * Returns a Map with names of currently known agent which matched the
 * builderLabel (or all agents if builderLabel content is trivial),
 * mapped to the Map of nodes' selected metadata including capabilities
 * they declared with further labels mapped as KEY=VALUE entries.
 */
def detectCapabilityLabelsForBuilders(String builderLabel = null) {
    def nodeCaps = [:]
    // Track the original expression, matched or not, in the returned value
    // TODO: Maybe track something else like timestamps?
    nodeCaps.labelExpression = builderLabel
    nodeCaps.nodeData = [:]

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

        nodeCaps.nodeData[node] = [:]
        nodeCaps.nodeData[node].name = node.getNodeName()
        nodeCaps.nodeData[node].labelString = node.labelString
        nodeCaps.nodeData[node].labelArray = node.labelString.split('[ \r\n\t]+')

        // Finally, collect the map of labels which declare certain capabilities
        // There may be several hits e.g. "GCCVER=8 GCCVER=10" so we save arrays
        nodeCaps.nodeData[node].labelMap = [:]
        for (String label : nodeCaps.nodeData[node].labelArray) {
            if (label.contains("=")) {
                // Save custom mapping
                String[] keyValue = label.split("=", 2)
                try {
                    nodeCaps.nodeData[node].labelMap[keyValue[0]] << keyValue[1]
                } catch (Throwable e) {
                    nodeCaps.nodeData[node].labelMap[keyValue[0]] = []
                    nodeCaps.nodeData[node].labelMap[keyValue[0]] << keyValue[1]
                }
                // Also save the original string "as is", as if a single-value token
                nodeCaps.nodeData[node].labelMap[label] = null
            } else {
                // single-value token, no equality sign in contents
                nodeCaps.nodeData[node].labelMap[label] = null
            }
        }
    }

    return nodeCaps
}
