import org.nut.dynamatrix.dynamatrixGlobalState;
import org.nut.dynamatrix.NodeData;
import org.nut.dynamatrix.Utils;

/* withEnvOptional: Helpers for optional use of withEnv - if a value is
 * not already in the env map, use one defined by the current build agent,
 * or the provided default.
 */
def call(String VAR, String DEFVAL, Closure body) {
    if (VAR == null || env.containsKey(VAR)) {
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

def call(Map VARVAL, Closure body) {
    if (VARVAL == null || VARVAL.size() == 0) {
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

