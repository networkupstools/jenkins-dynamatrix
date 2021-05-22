import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;

/*
 * Return the label (expression) string for a worker that handles
 * git checkouts and stashing of the source for other build agents.
 * Unlike the other agents, this worker should have appropriate
 * internet access, possibly reference git repository cache, etc.
 */
def labelDefaultWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelDefaultWorker)) {
        return dynamatrixGlobalState.labelDefaultWorker
    }
    return "master-worker"
}

def labelCheckoutWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelCheckoutWorker)) {
        return dynamatrixGlobalState.labelCheckoutWorker
    }
    return labelDefaultWorker()
}

def labelDocumentationWorker() {
    // Global/modifiable config point:
    if (Utils.isStringNotEmpty(dynamatrixGlobalState.labelDocumentationWorker)) {
        return dynamatrixGlobalState.labelDocumentationWorker
    }
    return labelDefaultWorker()
}

/* Helpers for optional use of withEnv - if a value is not already in the env
 * map, use one defined by the current build agent, or the provided default.
 */
def withEnvOptional(String VAR, String DEFVAL, Closure body) {
    if (env.containsKey(VAR)) {
        return body()
    }

    def hits = 0
    def VAL = null
    if (Utils.isStringNotEmpty(env['NODE_NAME'])) {
        NodeData.getNodeLabelsByName(env.NODE_NAME).each() { label ->
            if (label.startsWith("${VAR}=")) {
                String[] keyValue = label.split("=", 2)
                if (VAL == null) VAL=keyValue[1]
                hits ++
            }
        }

        if (hits > 0 && VAL != null) {
            if (hits > 1) println "[WARNING] Numerous assignments of label '${VAR}' in node '${env.NODE_NAME}': using envvar value '${VAL}'"
            //return withEnv(["${VAR}": "${VAL}"], body)
            return withEnv(["${VAR}=${VAL}"], body)
        }
    }

    if (DEFVAL != null) {
        //return withEnv(["${VAR}": "${DEFVAL}"], body)
        return withEnv(["${VAR}=${DEFVAL}"], body)
    }

    return body()
}

def withEnvOptional(Map VARVAL, Closure body) {
    if (VARVAL.size() == 0) {
        return body()
    }

    def envmap = [:]
    def arrLabels = NodeData.getNodeLabelsByName(env.NODE_NAME)

    VARVAL.keySet().each() {VAR ->
        def DEFVAL = VARVAL[VAR]
        if (Utils.isStringNotEmpty(env[VAR]) || envmap.containsKey(VAR)) {
            return // continue
        }

        def hits = 0
        def VAL = null
        if (arrLabels.size() > 0) {
            arrLabels.each() {label ->
                if (label.startsWith("${VAR}=")) {
                    String[] keyValue = label.split("=", 2)
                    if (VAL == null) VAL=keyValue[1]
                    hits ++
                }
            }

            if (hits > 0 && VAL != null) {
                if (hits > 1 && dynamatrixGlobalState.enableDebugTrace) println "[WARNING] Numerous assignments of label '${VAR}' in node '${env.NODE_NAME}': using envvar value '${VAL}'"
                envmap[VAR] = "${VAL}"
            }
        }

        if (DEFVAL != null) {
            envmap[VAR] = "${DEFVAL}"
        }
    }

    if (envmap.size() > 0) {
        def envarr = []
        envmap.keySet().each() {k ->
            envarr << "${k}=${envmap[k]}"
        }
        //return withEnv(envmap, body)
        return withEnv(envarr, body)
    }

    return body()
}

