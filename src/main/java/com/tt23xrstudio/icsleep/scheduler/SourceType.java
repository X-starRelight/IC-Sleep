package com.tt23xrstudio.icsleep.scheduler;

/**
 * 任务来源类型。
 */
public enum SourceType {
    /** 玩家发起 */
    PLAYER("player"),
    /** 服务端控制台发起 */
    SERVER("server"),
    /** 命令方块发起 */
    COMMAND_BLOCK("command_block"),
    /** 数据包函数发起 */
    FUNCTION("function");

    private final String id;

    SourceType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 根据持久化字符串还原来源类型，未知时回退到 SERVER */
    public static SourceType fromId(String id) {
        if (id != null) {
            for (SourceType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
        }
        return SERVER;
    }
}
