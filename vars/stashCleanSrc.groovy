import org.nut.dynamatrix.DynamatrixStash

void call(String stashName, Closure scmbody = null) {
    // Optional closure can fully detail how the code is checked out
    DynamatrixStash.stashCleanSrc(this, stashName, scmbody)
} // stashCleanSrc()
