package com.ccode.mintest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CDebugInstrumenter {

    public static final String DEBUG_SHIM =
            "#include <stdio.h>\n"
            + "#include <stdlib.h>\n"
            + "#include <string.h>\n"
            + "#include <stdarg.h>\n"
            + "#include <unistd.h>\n"
            + "#include <sys/socket.h>\n"
            + "#include <sys/un.h>\n"
            + "#include <stddef.h>\n"
            + "\n"
            + "static int  g_sock = -1;\n"
            + "static int  g_bp[1024];\n"
            + "static int  g_nbp = 0;\n"
            + "static int  g_step = 1;\n"
            + "\n"
            + "static void dbg_connect(void) {\n"
            + "    const char *name = getenv(\"CCODE_DBG_SOCK\");\n"
            + "    if (!name) return;\n"
            + "    int fd = socket(AF_UNIX, SOCK_STREAM, 0);\n"
            + "    if (fd < 0) return;\n"
            + "    struct sockaddr_un a;\n"
            + "    memset(&a, 0, sizeof(a));\n"
            + "    a.sun_family = AF_UNIX;\n"
            + "    size_t n = strlen(name);\n"
            + "    if (n > sizeof(a.sun_path) - 2) n = sizeof(a.sun_path) - 2;\n"
            + "    a.sun_path[0] = 0;\n"
            + "    memcpy(a.sun_path + 1, name, n);\n"
            + "    socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + n);\n"
            + "    if (connect(fd, (struct sockaddr *)&a, len) == 0) {\n"
            + "        g_sock = fd;\n"
            + "    } else {\n"
            + "        close(fd);\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "void DEBUG_HOOK(int line, const char *fmt, ...) {\n"
            + "    if (g_sock < 0) dbg_connect();\n"
            + "    if (g_sock < 0) return;\n"
            + "    int hit = g_step;\n"
            + "    for (int i = 0; i < g_nbp; i++) if (g_bp[i] == line) hit = 1;\n"
            + "    if (!hit) return;\n"
            + "    g_step = 0;\n"
            + "    char buf[64];\n"
            + "    int n = snprintf(buf, sizeof(buf), \"LINE %d\\n\", line);\n"
            + "    if (send(g_sock, buf, n, 0) != n) { close(g_sock); g_sock = -1; return; }\n"
            + "    if (fmt != NULL) {\n"
            + "        char vbuf[512];\n"
            + "        va_list ap;\n"
            + "        va_start(ap, fmt);\n"
            + "        int vn = vsnprintf(vbuf, sizeof(vbuf), fmt, ap);\n"
            + "        va_end(ap);\n"
            + "        if (vn < 0) vn = 0;\n"
            + "        if (vn >= (int)sizeof(vbuf)) vn = (int)sizeof(vbuf) - 1;\n"
            + "        char msg[576];\n"
            + "        int mn = snprintf(msg, sizeof(msg), \"VARS %s\\n\", vbuf);\n"
            + "        if (mn > 0 && send(g_sock, msg, mn, 0) != mn) {\n"
            + "            close(g_sock); g_sock = -1; return;\n"
            + "        }\n"
            + "    }\n"
            + "    char cmd[32];\n"
            + "    int r = recv(g_sock, cmd, sizeof(cmd) - 1, 0);\n"
            + "    if (r <= 0) { close(g_sock); g_sock = -1; return; }\n"
            + "    cmd[r] = 0;\n"
            + "    if (strncmp(cmd, \"STEP\", 4) == 0)      { g_step = 1; }\n"
            + "    else if (strncmp(cmd, \"CONT\", 4) == 0) { g_step = 0; }\n"
            + "    else if (strncmp(cmd, \"BREAK\", 5) == 0) {\n"
            + "        int ln = atoi(cmd + 5);\n"
            + "        if (g_nbp < 1024) g_bp[g_nbp++] = ln;\n"
            + "        g_step = 0;\n"
            + "    }\n"
            + "}\n";

    private static final Pattern TYPE_PREFIX = Pattern.compile(
            "^(?:const\\s+)?(?:(?:unsigned|signed|short|long|char|int|float|double|void|bool)\\b\\s*)+");

    private static final Pattern SIMPLE_DECL = Pattern.compile(
            "^(?:const\\s+)?(?:(?:unsigned|signed|short|long|char|int|float|double|void|bool)\\b\\s*)+"
                    + "(\\*{0,2})\\s*([A-Za-z_]\\w*)\\s*(\\[[^\\]]*\\])?");

    private static final Pattern HEADER_DECL = Pattern.compile(
            "^(?:for|if|while|switch)\\s*\\(\\s*"
                    + "(?:const\\s+)?(?:(?:unsigned|signed|short|long|char|int|float|double|void|bool)\\b\\s*)+"
                    + "(\\*{0,2})\\s*([A-Za-z_]\\w*)\\s*(\\[[^\\]]*\\])?");

    public static String instrument(String src) {
        StringBuilder out = new StringBuilder();
        out.append(DEBUG_SHIM).append('\n');

        List<Map<String, String>> scopes = new ArrayList<Map<String, String>>();
        scopes.add(new LinkedHashMap<String, String>());

        String[] lines = src.split("\n", -1);
        int depth = 0;
        boolean stmtOpen = false;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();

            boolean isBlank = trimmed.isEmpty();
            boolean isPre = trimmed.startsWith("#");
            boolean isLineComment = trimmed.startsWith("//");
            boolean isBracesOnly = trimmed.equals("{") || trimmed.equals("}")
                    || trimmed.equals("};") || trimmed.equals("){");
            boolean isLabel = trimmed.endsWith(":") && !trimmed.contains("?");
            boolean isElseBranch = trimmed.startsWith("}") || trimmed.startsWith("else");

            boolean instrumentable = !isBlank && !isPre && !isLineComment
                    && !isBracesOnly && !isLabel && !isElseBranch
                    && depth >= 1 && !stmtOpen;

            if (instrumentable) {
                String indent = raw.substring(0, raw.length() - trimmed.length());
                out.append(indent).append(buildHook(i + 1, scopes)).append('\n');
            }
            out.append(raw).append('\n');

            if (!isLineComment && !isPre) {
                String code = stripLineComment(trimmed);
                processBraces(code, scopes);
                collectDecls(code, scopes);
                depth += netBraces(trimmed);
                if (depth < 0) depth = 0;
                char last = code.isEmpty() ? ' ' : code.charAt(code.length() - 1);
                stmtOpen = !(last == ';' || last == '{' || last == '}');
            }
        }
        return out.toString();
    }

    private static String buildHook(int line, List<Map<String, String>> scopes) {
        List<Map.Entry<String, String>> vars = visibleVars(scopes);
        if (vars.isEmpty()) {
            return "DEBUG_HOOK(" + line + ", 0);";
        }
        StringBuilder fmt = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (Map.Entry<String, String> e : vars) {
            if (fmt.length() > 0) fmt.append(", ");
            fmt.append(e.getKey()).append('=').append(e.getValue());
            if (args.length() > 0) args.append(", ");
            args.append(e.getKey());
        }
        return "DEBUG_HOOK(" + line + ", \"" + fmt + "\", " + args + ");";
    }

    private static List<Map.Entry<String, String>> visibleVars(
            List<Map<String, String>> scopes) {
        Map<String, String> merged = new LinkedHashMap<String, String>();
        for (int i = 0; i < scopes.size(); i++) {
            Map<String, String> scope = scopes.get(i);
            for (Map.Entry<String, String> e : scope.entrySet()) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return new ArrayList<Map.Entry<String, String>>(merged.entrySet());
    }

    private static void processBraces(String code, List<Map<String, String>> scopes) {
        boolean inStr = false, inChr = false, inCmt = false;
        for (int k = 0; k < code.length(); k++) {
            char c = code.charAt(k);
            if (inCmt) continue;
            if (inStr) { if (c == '\\') k++; else if (c == '"') inStr = false; continue; }
            if (inChr) { if (c == '\\') k++; else if (c == '\'') inChr = false; continue; }
            if (c == '/' && k + 1 < code.length() && code.charAt(k + 1) == '/') { inCmt = true; continue; }
            if (c == '"') { inStr = true; continue; }
            if (c == '\'') { inChr = true; continue; }
            if (c == '{') {
                scopes.add(new LinkedHashMap<String, String>());
            } else if (c == '}') {
                if (scopes.size() > 1) scopes.remove(scopes.size() - 1);
            }
        }
    }

    private static void collectDecls(String code, List<Map<String, String>> scopes) {
        String line = code.trim();
        if (line.isEmpty()) return;
        Matcher m = SIMPLE_DECL.matcher(line);
        if (m.find() && m.start() == 0) {
            collectFromDeclLine(line, scopes.get(scopes.size() - 1));
            return;
        }
        Matcher hm = HEADER_DECL.matcher(line);
        if (hm.find() && hm.start() == 0) {
            collectFromDeclLine(line.substring(line.indexOf('(') + 1),
                    scopes.get(scopes.size() - 1));
        }
    }

    private static void collectFromDeclLine(String line, Map<String, String> scope) {
        String s = line.trim();
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open >= 0 && close > open && close == s.length() - 1) {
            s = s.substring(open + 1, close);
        }
        int semi = s.lastIndexOf(';');
        if (semi >= 0) s = s.substring(0, semi);

        Matcher first = SIMPLE_DECL.matcher(s);
        if (!first.find() || first.start() != 0) return;
        String typePrefix = first.group(0);
        boolean isPtr = first.group(1) != null && !first.group(1).isEmpty();
        String firstName = first.group(2);
        boolean isArray = first.group(3) != null;

        String rest = firstName
                + (first.group(3) == null ? "" : first.group(3))
                + s.substring(first.end());
        String[] parts = splitTopLevel(rest);
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String name = p;
            boolean arr = isArray;
            String init = null;
            int eq = indexOfTopLevel(p, '=');
            if (eq >= 0) {
                name = p.substring(0, eq);
                init = p.substring(eq + 1).trim();
            }
            int br = name.indexOf('[');
            if (br >= 0) {
                arr = true;
                name = name.substring(0, br).trim();
            }
            if (!isValidName(name)) continue;

            String fmt = fmtFor(typePrefix, isPtr, arr, init);
            if (fmt != null) {
                scope.put(name, fmt);
            }
        }
    }

    private static String fmtFor(String type, boolean isPtr, boolean isArray, String init) {
        boolean isFloating = type.contains("float") || type.contains("double");
        boolean isChar = type.contains("char");
        if (isPtr) {
            return isChar ? "%s" : "%p";
        }
        if (isArray) {
            if (isChar && init != null && init.startsWith("\"")) return "%s";
            return "%p";
        }
        if (isFloating) return "%g";
        return "%d";
    }

    private static boolean isValidName(String name) {
        if (name == null || name.isEmpty()) return false;
        char c = name.charAt(0);
        if (!(c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        for (int i = 1; i < name.length(); i++) {
            char x = name.charAt(i);
            if (!(x == '_' || (x >= 'a' && x <= 'z') || (x >= 'A' && x <= 'Z')
                    || (x >= '0' && x <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static String[] splitTopLevel(String s) {
        List<String> parts = new ArrayList<String>();
        int depthBrace = 0;
        StringBuilder cur = new StringBuilder();
        boolean inStr = false, inChr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) { cur.append(c); if (c == '\\') i++; else if (c == '"') inStr = false; continue; }
            if (inChr) { cur.append(c); if (c == '\\') i++; else if (c == '\'') inChr = false; continue; }
            if (c == '"') { inStr = true; cur.append(c); continue; }
            if (c == '\'') { inChr = true; cur.append(c); continue; }
            if (c == '{') { depthBrace++; cur.append(c); continue; }
            if (c == '}') { depthBrace--; cur.append(c); continue; }
            if (c == ',' && depthBrace == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static int indexOfTopLevel(String s, char target) {
        boolean inStr = false, inChr = false;
        int paren = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) { if (c == '\\') i++; else if (c == '"') inStr = false; continue; }
            if (inChr) { if (c == '\\') i++; else if (c == '\'') inChr = false; continue; }
            if (c == '"') { inStr = true; continue; }
            if (c == '\'') { inChr = true; continue; }
            if (c == '(') { paren++; continue; }
            if (c == ')') { paren--; continue; }
            if (c == target && paren == 0) return i;
        }
        return -1;
    }

    private static int netBraces(String s) {
        int net = 0;
        boolean inStr = false, inChr = false, inCmt = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (inCmt) continue;
            if (inStr) { if (c == '\\') k++; else if (c == '"') inStr = false; continue; }
            if (inChr) { if (c == '\\') k++; else if (c == '\'') inChr = false; continue; }
            if (c == '/' && k + 1 < s.length() && s.charAt(k + 1) == '/') { inCmt = true; continue; }
            if (c == '"') { inStr = true; continue; }
            if (c == '\'') { inChr = true; continue; }
            if (c == '{') net++;
            else if (c == '}') net--;
        }
        return net;
    }

    private static String stripLineComment(String s) {
        int idx = -1;
        boolean inStr = false, inChr = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (inStr) { if (c == '\\') k++; else if (c == '"') inStr = false; continue; }
            if (inChr) { if (c == '\\') k++; else if (c == '\'') inChr = false; continue; }
            if (c == '"') { inStr = true; continue; }
            if (c == '\'') { inChr = true; continue; }
            if (c == '/' && k + 1 < s.length() && s.charAt(k + 1) == '/') { idx = k; break; }
        }
        return idx < 0 ? s : s.substring(0, idx);
    }

    private CDebugInstrumenter() {
    }
}
