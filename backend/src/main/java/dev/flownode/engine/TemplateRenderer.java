package dev.flownode.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills in {{placeholders}}:
 * {{input}} all upstream outputs, {{iteration}}, {{previous}}, or {{nodeId}} for one upstream node.
 * Unknown placeholders are left as written. Single pass, so an output that itself contains
 * "{{input}}" is not expanded a second time.
 */
public final class TemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    private TemplateRenderer() { }

    public static String render(String template, NodeContext ctx) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = switch (key) {
                case "input" -> ctx.joinedInput();
                case "iteration" -> String.valueOf(ctx.iteration());
                case "previous" -> ctx.previousOutput() == null ? "" : ctx.previousOutput();
                default -> ctx.inputs().get(key);
            };
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
