package com.gomesdev.sortifyteams.config;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    // ───────── Config ─────────
    private static final String START_TIME = "lg_start";
    private static final String REQ_ID = "lg_reqid";
    private static final SimpleDateFormat DF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");

    // ANSI colors (desliga com ANSI_OFF se seu console não suportar)
    private static final String RESET = "\u001B[0m";
    private static final String DIM = "\u001B[2m";
    private static final String BOLD = "\u001B[1m";

    private static final String FG_CYAN = "\u001B[36m";
    private static final String FG_GREEN = "\u001B[32m";
    private static final String FG_YELLOW = "\u001B[33m";
    private static final String FG_RED = "\u001B[31m";
    private static final String FG_BLUE = "\u001B[34m";
    private static final String FG_MAGENTA = "\u001B[35m";
    private static final String FG_GRAY = "\u001B[90m";

    // Bordas
    private static final String TOP = "┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓";
    private static final String SEP = "┠──────────────────────────────────────────────────────────────────────────────┨";
    private static final String BOTTOM = "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛";

    // ───────── Helpers ─────────
    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String getReqId(HttpServletRequest r) {
        String h = r.getHeader("X-Request-Id");
        return (h != null && !h.isBlank()) ? h : shortUuid();
    }

    private static String pad(String s, int len) {
        if (s == null)
            s = "";
        if (s.length() >= len)
            return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < len)
            b.append(' ');
        return b.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String colorByStatus(int code) {
        if (code >= 200 && code < 300)
            return FG_GREEN;
        if (code >= 300 && code < 400)
            return FG_BLUE;
        if (code >= 400 && code < 500)
            return FG_YELLOW;
        return FG_RED; // 5xx
    }

    private static String joinParams(Map<String, String[]> map) {
        if (map == null || map.isEmpty())
            return "(sem parâmetros)";
        StringJoiner j = new StringJoiner(FG_GRAY + " | " + RESET);
        map.forEach((k, v) -> j.add(k + "=" + String.join(",", v)));
        return j.toString();
    }

    private static String header(HttpServletRequest r, String reqId) {
        String method = pad(r.getMethod(), 6);
        String uri = truncate(r.getRequestURI(), 58);
        String thread = Thread.currentThread().getName();
        return String.format("%sRID=%s%s  %s%s%s  %s  %s%s%s", FG_MAGENTA, reqId, RESET, BOLD, method, RESET, uri, DIM,
                thread, RESET);
    }

    // ───────── Interceptor ─────────
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        request.setAttribute(START_TIME, System.currentTimeMillis());
        String reqId = getReqId(request);
        request.setAttribute(REQ_ID, reqId);
        response.setHeader("X-Request-Id", reqId);

        // Linha única e limpa no início (facilita seguir no console em alto volume)
        String head = header(request, reqId);
        System.err.println(TOP);
        System.out.println("┃ " + FG_CYAN + "🚀 REQ IN " + RESET + " " + head);
        System.err.println(SEP);

        // Bloco leve de contexto
        String when = DF.format(new Date());
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For")).map(s -> s.split(",")[0].trim())
                .orElseGet(() -> request.getRemoteAddr());
        String ua = truncate(request.getHeader("User-Agent"), 80);
        String qs = truncate(request.getQueryString(), 100);

        System.out.println("┃ " + FG_CYAN + "📅 Início:      " + RESET + when);
        System.out.println("┃ " + FG_CYAN + "🌐 Cliente:     " + RESET + ip);
        if (qs != null && !qs.isBlank()) {
            System.out.println("┃ " + FG_CYAN + "❓ QueryString: " + RESET + qs);
        }
        Map<String, String[]> params = request.getParameterMap();
        if (params != null && !params.isEmpty()) {
            System.out.println("┃ " + FG_CYAN + "🔖 Parâmetros:  " + RESET + truncate(joinParams(params), 120));
        } else {
            System.out.println("┃ " + FG_CYAN + "🔖 Parâmetros:  " + RESET + "(sem parâmetros)");
        }
        if (ua != null && !ua.isBlank()) {
            System.out.println("┃ " + FG_CYAN + "🧭 User-Agent:  " + RESET + ua);
        }

        System.err.println(SEP);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        // opcional
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {

        Long start = (Long) request.getAttribute(START_TIME);
        long took = (start != null) ? (System.currentTimeMillis() - start) : -1;
        String reqId = (String) request.getAttribute(REQ_ID);
        if (reqId == null)
            reqId = getReqId(request);

        int status = response.getStatus();
        String statusColor = colorByStatus(status);
        String head = header(request, reqId);

        // Resultado + métricas
        System.out.println("┃ " + statusColor + "🏁 REQ OUT" + RESET + " " + head);
        System.out.println("┃ " + FG_CYAN + "🔢 Status:      " + RESET + statusColor + status + RESET);
        if (took >= 0) {
            System.out.println("┃ " + FG_CYAN + "⏱  Duração:     " + RESET + took + " ms");
        }
        System.out.println("┃ " + FG_CYAN + "📅 Fim:         " + RESET + DF.format(new Date()));

        if (ex != null) {
            String msg = truncate(ex.getMessage(), 160);
            System.out.println("┃ " + FG_RED + "❌ Erro:        " + RESET + msg);
        }

        System.err.println(BOTTOM + "\n");
    }
}
