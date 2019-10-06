package core.module.jpa

/**
 * 基于 SnowFlake 的id 生成策略
 * <p>
 * 1位标识部分:     在java中由于long的最高位是符号位，正数是0，负数是1，一般生成的ID为正数，所以为0；
 * 41位时间戳部分:  这个是毫秒级的时间，一般实现上不会存储当前的时间戳，而是时间戳的差值（当前时间-固定的开始时间），这样可以使产生的ID从更小值开始；41位的时间戳可以使用69年，(1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69年；
 * 10位节点部分:    Twitter实现中使用前5位作为数据中心标识，后5位作为机器标识，可以部署1024个节点；
 * 12位序列号部分:  支持同一毫秒内同一个节点可以生成4096个ID;
 */
class SnowFlakeIdGenerator {
    // 起始的时间戳 2018-10-18 10:26:00
    private final static long START_STMP         = 1538706388336L;
    // 每一部分占用的位数，就三个
    private final static long SEQUENCE_BIT       = 12;// 序列号占用的位数
    private final static long MACHINE_BIT        = 5; // 机器标识占用的位数
    private final static long DATACENTER_BIT     = 5;// 数据中心占用的位数
    // 每一部分最大值
    private final static long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
    private final static long MAX_MACHINE_NUM    = -1L ^ (-1L << MACHINE_BIT);
    private final static long MAX_SEQUENCE       = -1L ^ (-1L << SEQUENCE_BIT);
    // 每一部分向左的位移
    private final static long MACHINE_LEFT       = SEQUENCE_BIT;
    private final static long DATACENTER_LEFT    = SEQUENCE_BIT + MACHINE_BIT;
    private final static long TIMESTMP_LEFT      = DATACENTER_LEFT + DATACENTER_BIT;
    private              long datacenterId; // 数据中心
    private              long machineId; // 机器标识
    private              long sequence           = 0L; // 序列号
    private              long lastStmp           = -1L;// 上一次时间戳


    SnowFlakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than " + MAX_DATACENTER_NUM + " or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than " + MAX_MACHINE_NUM + " or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }


    //产生下一个ID
    synchronized long nextId() {
        long currStmp = System.currentTimeMillis()
        if (currStmp < lastStmp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id");
        }

        if (currStmp == lastStmp) {
            //if条件里表示当前调用和上一次调用落在了相同毫秒内，只能通过第三部分，序列号自增来判断为唯一，所以+1.
            sequence = (sequence + 1) & MAX_SEQUENCE;
            //同一毫秒的序列数已经达到最大，只能等待下一个毫秒
            if (sequence == 0L) {
                currStmp = getNextMill();
            }
        } else {
            //不同毫秒内，序列号置为0
            //执行到这个分支的前提是currTimestamp > lastTimestamp，说明本次调用跟上次调用对比，已经不再同一个毫秒内了，这个时候序号可以重新回置0了。
            sequence = 0L;
        }

        lastStmp = currStmp;
        //就是用相对毫秒数、机器ID和自增序号拼接
        //时间戳部分 | 数据中心部分 | 机器标识部分 | 序列号部分
        return (currStmp - START_STMP) << TIMESTMP_LEFT | datacenterId << DATACENTER_LEFT  | machineId << MACHINE_LEFT | sequence
    }


    private long getNextMill() {
        long mill = System.currentTimeMillis()
        while (mill <= lastStmp) {
            mill = System.currentTimeMillis()
        }
        return mill;
    }



    static void main(String[] args) {
        // System.out.println(System.currentTimeMillis());
        SnowFlakeIdGenerator idGenerator = new SnowFlakeIdGenerator(1, 1);
        for (int i = 0; i < 10; i++) {
            System.out.println(idGenerator.nextId())
        }
    }
}
