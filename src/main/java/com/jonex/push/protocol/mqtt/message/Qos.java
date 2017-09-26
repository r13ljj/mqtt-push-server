package com.jonex.push.protocol.mqtt.message;

/**
 * QoS 服务质量等级
 * 位置：第1个字节，第2-1位。
 * ----------------------------------------------
 * |QoS值|   bit2    |   bit1    |       描述    |
 * ----------------------------------------------
 * | 0   |      0    |      0    |  最多分发一次  |
 * ----------------------------------------------
 * | 1   |      0    |      1    |  至少分发一次  |
 * ----------------------------------------------
 * | 2   |      1    |      0    |  只分发一次    |
 * ----------------------------------------------
 * | -   |      1    |      1    |  保留位       |
 * ----------------------------------------------
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 14:09
 */
public enum Qos {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2),
    RESERVE(3);

    private final int val;

    private Qos(int value){
        this.val = value;
    }

    /**
     * 获取类型对应的值
     *
     * @return  int
     */
    public int value() {
        return val;
    }

    /**
     * 通过读取到的整型来获取对应的QoS类型
     *
     * @param i
     * @return Qos
     */
    public static Qos valueOf(int i) {
        for(Qos q: Qos.values()) {
            if (q.val == i)
                return q;
        }
        throw new IllegalArgumentException("Qos值无效: " + i);
    }
}
