package com.tt23xrstudio.icsleep.time;

/**
 * 时间解析结果。
 *
 * @param ticks    换算后的游戏刻
 * @param original 原始输入文本
 */
public record ParsedTime(long ticks, String original) {
}
