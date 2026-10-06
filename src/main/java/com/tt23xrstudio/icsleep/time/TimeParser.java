package com.tt23xrstudio.icsleep.time;

/**
 * 时间参数解析器。
 *
 * 支持格式：20、20t、1s、1m、1h、1d。
 * 默认单位为 tick，不支持小数与负数，最小 0，最大由配置决定。
 */
public final class TimeParser {

    private TimeParser() {
    }

    /** 1 秒 = 20 tick */
    public static final long TICKS_PER_SECOND = 20L;
    /** 1 分钟 = 1200 tick */
    public static final long TICKS_PER_MINUTE = 1200L;
    /** 1 小时 = 72000 tick */
    public static final long TICKS_PER_HOUR = 72000L;
    /** 1 游戏日 = 24000 tick */
    public static final long TICKS_PER_DAY = 24000L;

    /**
     * 解析时间文本。
     *
     * @param input        原始输入，例如 "20"、"1s"
     * @param defaultUnit  无后缀时使用的默认单位（tick/second/minute/hour/day）
     * @param maxDelayTicks 允许的最大 tick
     * @return 解析结果
     * @throws TimeParseException 格式无效或超出范围
     */
    public static ParsedTime parse(String input, String defaultUnit, long maxDelayTicks)
            throws TimeParseException {
        if (input == null || input.isBlank()) {
            throw new TimeParseException("无效时间：" + input + "。示例：20、20t、1s、1m。");
        }
        String text = input.trim().toLowerCase();
        char suffix = text.charAt(text.length() - 1);
        String numberPart;
        long multiplier;
        if (Character.isDigit(suffix)) {
            // 无后缀，使用默认单位
            numberPart = text;
            multiplier = unitMultiplier(defaultUnit);
        } else {
            numberPart = text.substring(0, text.length() - 1);
            multiplier = switch (suffix) {
                case 't' -> 1L;
                case 's' -> TICKS_PER_SECOND;
                case 'm' -> TICKS_PER_MINUTE;
                case 'h' -> TICKS_PER_HOUR;
                case 'd' -> TICKS_PER_DAY;
                default -> throw new TimeParseException(
                        "无效时间：" + input + "。示例：20、20t、1s、1m。");
            };
        }
        if (numberPart.isEmpty()) {
            throw new TimeParseException("无效时间：" + input + "。示例：20、20t、1s、1m。");
        }
        long value;
        try {
            value = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            throw new TimeParseException("无效时间：" + input + "。示例：20、20t、1s、1m。");
        }
        if (value < 0) {
            throw new TimeParseException("无效时间：" + input + "。不支持负数。");
        }
        long ticks = value * multiplier;
        if (ticks < 0L || ticks > maxDelayTicks) {
            throw new TimeParseException("时间超出范围：允许 0 到 " + maxDelayTicks + " tick。");
        }
        return new ParsedTime(ticks, input);
    }

    /** 将单位名转换为 tick 倍数，无法识别时按 tick 处理 */
    private static long unitMultiplier(String unit) {
        if (unit == null) {
            return 1L;
        }
        return switch (unit.toLowerCase()) {
            case "second", "seconds", "s" -> TICKS_PER_SECOND;
            case "minute", "minutes", "m" -> TICKS_PER_MINUTE;
            case "hour", "hours", "h" -> TICKS_PER_HOUR;
            case "day", "days", "d" -> TICKS_PER_DAY;
            default -> 1L;
        };
    }
}
