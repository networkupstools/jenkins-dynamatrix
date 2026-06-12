// Query on JENKINS_URL/script console about queue pressure on different node labels
// (C) 2026 by Jim Klimov <jimklimov+nut@gmail.com>

import jenkins.model.Jenkins

def queue = Jenkins.instance.queue

println "TOTAL: ${ queue.items.size()}"

/////////////////////////////////////

def countsByLabel = [:].withDefault { 0 }
def oldestByLabel = [:]

queue.items.each { item ->
    def label = item.assignedLabel?.name ?: "any"
    countsByLabel[label]++

    Integer ageSec = (System.currentTimeMillis() - item.inQueueSince) / 1000

    oldestByLabel[label] = Math.max(oldestByLabel.get(label, 0), ageSec)
}

def extractField = { label, field ->
    def m = (label =~ /${field}=([^&\)]+)/)
    m.find() ? m.group(1) : ""
}

/////////////////////////////////////

def summary = [:].withDefault { 0 }

countsByLabel.each { label, count ->
    def key = [
        extractField(label, "OS_FAMILY") ?: "-",
        extractField(label, "OS_DISTRO") ?: "-",
        extractField(label, "ARCH_BITS") ?: "-",
        extractField(label, "ARCH64") ?: (extractField(label, "ARCH32") ?: "-")
    ].join(" | ")

    summary[key] += count
}

println "=== Output 1 - summary by HW/OS aspect"
println String.format("%4s  %s", "QLEN", "OS_FAMILY | OS_DISTRO | BITS | ARCH")
summary
    .sort { a, b -> b.value <=> a.value }
    .each { k, v ->
        println String.format("%4d  %s", v, k)
    }

/////////////////////////////////////

def rows = countsByLabel.collect { label, count ->
    [
        osFamily : extractField(label, "OS_FAMILY") ?: "-",
        osDistro : extractField(label, "OS_DISTRO") ?: "-",
        archBits : extractField(label, "ARCH_BITS") ?: "-",
        arch     : extractField(label, "ARCH64") ?: (extractField(label, "ARCH32") ?: "-"),
        count    : count,
        label    : label
    ]
}

rows.sort { a, b ->
    a.osFamily <=> b.osFamily ?:
    a.osDistro <=> b.osDistro ?:
    a.archBits <=> b.archBits ?:
    a.arch     <=> b.arch     ?:
    b.count    <=> a.count    // descending queue length
}

println "=== Output 2 - detailed by requested node labels"
println String.format("%-8s %-20s %-3s %-12s %4s  %8s  %s",
    "OS_FAM", "OS_DISTRO", "BIT", "ARCH", "QLEN", "OLDEST", "LABEL")
rows.each { r ->
    println String.format("%-8s %-20s %-3s %-12s %4d  %8ds  %s",
        r.osFamily,
        r.osDistro,
        r.archBits,
        r.arch,
        r.count,
        oldestByLabel[r.label],
        r.label)
}

/////////////////////////////////////

return  queue.items.size()

